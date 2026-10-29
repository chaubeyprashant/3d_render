package com.example.a3d_render.data.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.a3d_render.domain.project.ProjectImportException
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.project.SourceNode
import java.io.InputStream

/**
 * A single GLB picked with the document picker, presented as a folder holding just that file.
 * Files next to it are not accessible, so it only suits GLBs with embedded textures.
 */
class SingleDocumentSourceTree(
    private val context: Context,
    private val documentUri: Uri
) : ProjectSourceTree {

    val file: SourceNode = run {
        val (name, size) = context.contentResolver.query(
            documentUri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0) to if (cursor.isNull(1)) -1L else cursor.getLong(1)
            } else {
                null
            }
        } ?: throw ProjectImportException("The selected file is no longer available.")
        SourceNode(name?.ifBlank { null } ?: "model.glb", documentUri.toString(), isDirectory = false, size = size)
    }

    override val root: SourceNode = SourceNode(file.name, "", isDirectory = true, size = -1)

    override fun listChildren(directory: SourceNode): List<SourceNode> =
        if (directory.ref == root.ref) listOf(file) else emptyList()

    override fun openStream(file: SourceNode): InputStream =
        context.contentResolver.openInputStream(Uri.parse(file.ref))
            ?: throw ProjectImportException("Cannot read \"${file.name}\" from device storage.")
}
