package com.example.a3d_render.data.drive

import android.content.Context
import android.content.Intent
import com.example.a3d_render.domain.model.DriveFileItem
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.project.SourceNode
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import java.io.InputStream

/**
 * Google Sign-In + read-only Drive access. Requires an Android OAuth client (package name +
 * signing SHA-1) registered in the Google Cloud project with the Drive API enabled.
 */
class GoogleDriveManager(private val context: Context) {

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent = signInClient.signInIntent

    /** @throws ApiException with the Google Sign-In status code when sign-in failed. */
    fun handleSignInResult(intent: Intent?): GoogleSignInAccount =
        GoogleSignIn.getSignedInAccountFromIntent(intent).getResult(ApiException::class.java)

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun signedInEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    fun signOut() {
        signInClient.signOut()
    }

    private fun driveService(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Not signed in to Google Drive")
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_READONLY))
            .apply { selectedAccount = account.account }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("RPAV")
            .build()
    }

    /** Lists the direct children of [folderId] (`root` = My Drive). Blocking. */
    fun listFolder(folderId: String): List<DriveFileItem> = listFolder(driveService(), folderId)

    /** A project tree rooted at a Drive folder, used to import GLBs and their related files. */
    fun sourceTree(folderId: String, folderName: String): ProjectSourceTree =
        DriveSourceTree(driveService(), SourceNode(folderName, folderId, isDirectory = true, size = -1))

    private class DriveSourceTree(
        private val drive: Drive,
        override val root: SourceNode
    ) : ProjectSourceTree {
        override fun listChildren(directory: SourceNode): List<SourceNode> =
            listFolder(drive, directory.ref)
                .filter { it.isFolder || !it.mimeType.startsWith(GOOGLE_APPS_MIME_PREFIX) }
                .map { SourceNode(it.name, it.id, it.isFolder, if (it.isFolder) -1L else it.size) }

        override fun openStream(file: SourceNode): InputStream = download(file, range = null)

        override fun openHead(file: SourceNode, byteCount: Long): InputStream =
            download(file, range = "bytes=0-${byteCount - 1}")

        /**
         * Raw stream without gzip: the client's default stream reads the rest of the response
         * when closed early, which would fetch a whole model just to read its header or cancel.
         */
        private fun download(file: SourceNode, range: String?): InputStream =
            drive.files().get(file.ref).apply {
                requestHeaders.acceptEncoding = "identity"
                range?.let { requestHeaders.range = it }
                setReturnRawInputStream(true)
            }.executeMediaAsInputStream()
    }

    companion object {
        const val ROOT_FOLDER_ID = "root"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val GOOGLE_APPS_MIME_PREFIX = "application/vnd.google-apps."

        private fun listFolder(drive: Drive, folderId: String): List<DriveFileItem> {
            val items = mutableListOf<DriveFileItem>()
            var pageToken: String? = null
            do {
                val result = drive.files().list()
                    .setQ("'${folderId.replace("'", "\\'")}' in parents and trashed = false")
                    .setSpaces("drive")
                    .setPageSize(1000)
                    .setFields("nextPageToken, files(id, name, size, mimeType, modifiedTime)")
                    .setPageToken(pageToken)
                    .execute()
                result.files.orEmpty().forEach { file ->
                    items += DriveFileItem(
                        id = file.id,
                        name = file.name.orEmpty(),
                        size = file.getSize() ?: -1L,
                        mimeType = file.mimeType.orEmpty(),
                        modifiedTime = file.modifiedTime?.value ?: 0L,
                        isFolder = file.mimeType == FOLDER_MIME
                    )
                }
                pageToken = result.nextPageToken
            } while (pageToken != null)
            return items
        }
    }
}
