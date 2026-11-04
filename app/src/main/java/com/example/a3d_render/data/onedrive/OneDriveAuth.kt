package com.example.a3d_render.data.onedrive

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.a3d_render.BuildConfig
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Microsoft identity platform sign-in for a public (native) client: authorization-code flow with
 * PKCE in a Custom Tab, then refresh-token grants. Blocking calls; use a background dispatcher.
 */
internal class OneDriveAuth(context: Context, private val http: OkHttpClient) {

    private val store = OneDriveTokenStore(context.applicationContext)

    val isConfigured: Boolean get() = BuildConfig.ONEDRIVE_CLIENT_ID.isNotBlank()

    fun isSignedIn(): Boolean = store.load() != null

    fun signedInEmail(): String? = store.load()?.email

    fun signOut() = store.clear()

    /** The browser URL that starts sign-in. The result arrives at [REDIRECT_URI]. */
    fun createAuthorizationUri(): Uri {
        val random = SecureRandom()
        val verifier = randomUrlSafe(random, 64)
        val state = randomUrlSafe(random, 16)
        store.savePending(PendingAuthorization(state, verifier))
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            URL_SAFE_FLAGS
        )
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()
    }

    /** Exchanges the redirect's authorization code for tokens and stores the session. */
    @Throws(OneDriveSignInException::class, IOException::class)
    fun completeSignIn(redirect: Uri) {
        val pending = store.takePending()
            ?: throw OneDriveSignInException("The sign-in session expired. Please try again.")
        val error = redirect.getQueryParameter("error")
        if (error != null) {
            throw OneDriveSignInException(
                redirect.getQueryParameter("error_description")?.substringBefore('\n') ?: "Sign-in failed ($error).",
                cancelled = error == "access_denied"
            )
        }
        if (redirect.getQueryParameter("state") != pending.state) {
            throw OneDriveSignInException("The sign-in response could not be verified. Please try again.")
        }
        val code = redirect.getQueryParameter("code")
            ?: throw OneDriveSignInException("Sign-in did not return an authorization code.")
        val tokens = requestTokens(
            FormBody.Builder()
                .add("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", pending.codeVerifier)
                .add("scope", SCOPES)
                .build()
        ) ?: throw OneDriveSignInException("Microsoft rejected the sign-in. Please try again.")
        val email = fetchEmail(tokens.accessToken)
        store.save(OneDriveSession(tokens.refreshToken, tokens.accessToken, tokens.expiresAtMillis, email))
    }

    /** A valid access token, refreshed when expired (or when [forceRefresh] is set). */
    @Synchronized
    @Throws(OneDriveAuthRequiredException::class, IOException::class)
    fun accessToken(forceRefresh: Boolean = false): String {
        val session = store.load() ?: throw OneDriveAuthRequiredException()
        if (!forceRefresh && System.currentTimeMillis() < session.expiresAtMillis - EXPIRY_SKEW_MILLIS) {
            return session.accessToken
        }
        val tokens = requestTokens(
            FormBody.Builder()
                .add("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", session.refreshToken)
                .add("scope", SCOPES)
                .build()
        )
        if (tokens == null) {
            store.clear()
            throw OneDriveAuthRequiredException("Your OneDrive session expired. Please sign in again.")
        }
        store.save(
            session.copy(
                refreshToken = tokens.refreshToken.ifBlank { session.refreshToken },
                accessToken = tokens.accessToken,
                expiresAtMillis = tokens.expiresAtMillis
            )
        )
        return tokens.accessToken
    }

    private class Tokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)

    /** Returns null when the identity platform rejects the grant (revoked/invalid); throws on I/O errors. */
    private fun requestTokens(form: FormBody): Tokens? {
        val request = Request.Builder().url(TOKEN_URL).post(form).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 400 || response.code == 401) return null
                throw IOException("Token endpoint returned HTTP ${response.code}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val accessToken = json.get("access_token")?.asString ?: return null
            val expiresIn = json.get("expires_in")?.asLong ?: DEFAULT_LIFETIME_SECONDS
            return Tokens(
                accessToken = accessToken,
                refreshToken = json.get("refresh_token")?.asString.orEmpty(),
                expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
            )
        }
    }

    private fun fetchEmail(accessToken: String): String? = try {
        val request = Request.Builder()
            .url("$GRAPH_BASE/me?\$select=mail,userPrincipalName")
            .header("Authorization", "Bearer $accessToken")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            listOf("mail", "userPrincipalName")
                .mapNotNull { key -> json.get(key)?.takeIf { !it.isJsonNull }?.asString }
                .firstOrNull { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }

    private fun randomUrlSafe(random: SecureRandom, byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(random::nextBytes)
        return Base64.encodeToString(bytes, URL_SAFE_FLAGS)
    }

    companion object {
        const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
        /** Register this exact value as a "Mobile and desktop applications" redirect URI in Azure. */
        const val REDIRECT_URI = "rpav-onedrive://auth"
        const val REDIRECT_SCHEME = "rpav-onedrive"

        private val AUTHORITY = "https://login.microsoftonline.com/${BuildConfig.ONEDRIVE_TENANT_ID}/oauth2/v2.0"
        private val AUTHORIZE_URL = "$AUTHORITY/authorize"
        private val TOKEN_URL = "$AUTHORITY/token"
        private const val SCOPES = "Files.Read User.Read offline_access"
        private const val URL_SAFE_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        private const val EXPIRY_SKEW_MILLIS = 60_000L
        private const val DEFAULT_LIFETIME_SECONDS = 3600L
    }
}
