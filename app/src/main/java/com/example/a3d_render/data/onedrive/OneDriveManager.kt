package com.example.a3d_render.data.onedrive

import android.content.Context
import android.net.Uri
import com.example.a3d_render.domain.model.DriveFileItem
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.project.SourceNode
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Read-only OneDrive access through Microsoft Graph, mirroring [com.example.a3d_render.data.drive.GoogleDriveManager].
 * Requires an Azure app registration (client ID in `ONEDRIVE_CLIENT_ID`) with the delegated
 * `Files.Read`, `User.Read` and `offline_access` permissions. All calls block.
 */
class OneDriveManager(context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val auth = OneDriveAuth(context, http)

    val isConfigured: Boolean get() = auth.isConfigured

    fun isSignedIn(): Boolean = auth.isSignedIn()

    fun signedInEmail(): String? = auth.signedInEmail()

    /** URL to open in a browser to start sign-in. */
    fun createSignInUri(): Uri = auth.createAuthorizationUri()

    @Throws(OneDriveSignInException::class, IOException::class)
    fun completeSignIn(redirect: Uri) = auth.completeSignIn(redirect)

    fun signOut() = auth.signOut()

    /** Lists the direct children of [folderId] (`root` = the user's OneDrive root). */
    fun listFolder(folderId: String): List<DriveFileItem> {
        val items = mutableListOf<DriveFileItem>()
        val childrenPath = if (folderId == ROOT_FOLDER_ID) "root" else "items/${Uri.encode(folderId)}"
        var next: HttpUrl? = "$GRAPH_BASE/me/drive/$childrenPath/children".toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,name,size,folder,file,package,lastModifiedDateTime")
            .addQueryParameter("\$top", "200")
            .build()
        while (next != null) {
            val json = execute(next).use { response ->
                JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            }
            json.getAsJsonArray("value")?.forEach { element ->
                parseItem(element.asJsonObject)?.let(items::add)
            }
            next = json.stringOrNull("@odata.nextLink")?.toHttpUrl()
        }
        return items
    }

    /** A project tree rooted at a OneDrive folder, used to import GLBs and their related files. */
    fun sourceTree(folderId: String, folderName: String): ProjectSourceTree =
        OneDriveSourceTree(SourceNode(folderName, folderId, isDirectory = true, size = -1))

    private inner class OneDriveSourceTree(override val root: SourceNode) : ProjectSourceTree {
        override fun listChildren(directory: SourceNode): List<SourceNode> =
            listFolder(directory.ref)
                .map { SourceNode(it.name, it.id, it.isFolder, if (it.isFolder) -1L else it.size) }

        override fun openStream(file: SourceNode): InputStream = download(file, range = null)

        override fun openHead(file: SourceNode, byteCount: Long): InputStream =
            download(file, range = "bytes=0-${byteCount - 1}")

        private fun download(file: SourceNode, range: String?): InputStream {
            val url = "$GRAPH_BASE/me/drive/items/${Uri.encode(file.ref)}/content".toHttpUrl()
            // Graph redirects to a pre-authenticated CDN URL; OkHttp drops the bearer token on that hop.
            return requireNotNull(execute(url, range).body) { "Empty OneDrive response" }.byteStream()
        }
    }

    /** Runs an authorized GET, refreshing the token once if Graph reports it as expired. */
    private fun execute(url: HttpUrl, range: String? = null): Response {
        var forceRefresh = false
        while (true) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${auth.accessToken(forceRefresh)}")
                .header("Accept-Encoding", "identity")
                .apply { range?.let { header("Range", it) } }
                .build()
            val response = http.newCall(request).execute()
            if (response.code == 401 && !forceRefresh) {
                response.close()
                forceRefresh = true
                continue
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw OneDriveHttpException(code)
            }
            return response
        }
    }

    companion object {
        const val ROOT_FOLDER_ID = "root"
        private const val GRAPH_BASE = OneDriveAuth.GRAPH_BASE

        /** Folders and plain files only; OneNote notebooks and other packages are skipped. */
        private fun parseItem(item: JsonObject): DriveFileItem? {
            val isFolder = item.has("folder")
            if (!isFolder && !item.has("file")) return null
            val id = item.stringOrNull("id") ?: return null
            return DriveFileItem(
                id = id,
                name = item.stringOrNull("name").orEmpty(),
                size = item.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: -1L,
                mimeType = if (isFolder) FOLDER_MIME else item.getAsJsonObject("file")?.stringOrNull("mimeType").orEmpty(),
                modifiedTime = item.stringOrNull("lastModifiedDateTime")?.let(::parseInstantMillis) ?: 0L,
                isFolder = isFolder
            )
        }

        private fun parseInstantMillis(value: String): Long =
            try {
                Instant.parse(value).toEpochMilli()
            } catch (e: Exception) {
                0L
            }

        private fun JsonObject.stringOrNull(key: String): String? =
            get(key)?.takeIf { it: JsonElement -> !it.isJsonNull }?.asString

        private const val FOLDER_MIME = "application/vnd.microsoft.onedrive.folder"
    }
}
