package com.example.a3d_render.data.project

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.a3d_render.domain.project.ProjectImportException
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.project.SourceNode
import java.io.InputStream

/**
 * A folder picked with the Storage Access Framework. Queries children directly instead of
 * DocumentFile, which issues one provider query per property per file.
 */
class DocumentSourceTree(
    private val context: Context,
    private val treeUri: Uri
) : ProjectSourceTree {

    override val root: SourceNode = run {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val name = context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        SourceNode(name?.ifBlank { null } ?: "Untitled Project", rootId, isDirectory = true, size = -1)
    }

    override fun listChildren(directory: SourceNode): List<SourceNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.ref)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        return context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SourceNode(
                            name = cursor.getString(1).orEmpty(),
                            ref = cursor.getString(0),
                            isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
                        )
                    )
                }
            }
        }.orEmpty()
    }

    override fun openStream(file: SourceNode): InputStream =
        context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, file.ref))
            ?: throw ProjectImportException("Cannot read \"${file.name}\" from device storage.")
}
