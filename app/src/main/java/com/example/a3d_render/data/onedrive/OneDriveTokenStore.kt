package com.example.a3d_render.data.onedrive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class OneDriveSession(
    val refreshToken: String,
    val accessToken: String,
    val expiresAtMillis: Long,
    val email: String?
)

/** Pending PKCE values kept between opening the browser and receiving the redirect. */
internal data class PendingAuthorization(val state: String, val codeVerifier: String)

/**
 * Persists the OneDrive session encrypted with an Android Keystore key, so refresh tokens are
 * never written to disk in plain text. Anything that fails to decrypt is treated as signed out.
 */
internal class OneDriveTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): OneDriveSession? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            gson.fromJson(decrypt(stored), OneDriveSession::class.java)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun save(session: OneDriveSession) {
        prefs.edit().putString(KEY_SESSION, encrypt(gson.toJson(session))).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    fun savePending(pending: PendingAuthorization) {
        prefs.edit()
            .putString(KEY_PENDING_STATE, pending.state)
            .putString(KEY_PENDING_VERIFIER, pending.codeVerifier)
            .apply()
    }

    fun takePending(): PendingAuthorization? {
        val state = prefs.getString(KEY_PENDING_STATE, null)
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)
        prefs.edit().remove(KEY_PENDING_STATE).remove(KEY_PENDING_VERIFIER).apply()
        return if (state != null && verifier != null) PendingAuthorization(state, verifier) else null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(':', limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
        )
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "onedrive_auth"
        const val KEY_SESSION = "session"
        const val KEY_PENDING_STATE = "pending_state"
        const val KEY_PENDING_VERIFIER = "pending_verifier"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "onedrive_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
