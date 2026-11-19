package com.example.a3d_render.data.project

import com.example.a3d_render.domain.project.ProjectImportException
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.project.SourceNode
import java.io.File
import java.io.InputStream

/** A GLB already saved on disk, presented as a folder that contains only that file. */
class LocalFileSourceTree(
    private val file: File,
    displayName: String
) : ProjectSourceTree {

    val fileNode: SourceNode = SourceNode(
        name = displayName,
        ref = file.absolutePath,
        isDirectory = false,
        size = file.length()
    )

    override val root: SourceNode = SourceNode(displayName, "", isDirectory = true, size = -1)

    override fun listChildren(directory: SourceNode): List<SourceNode> =
        if (directory.ref == root.ref) listOf(fileNode) else emptyList()

    override fun openStream(file: SourceNode): InputStream {
        if (!this.file.isFile) throw ProjectImportException("The downloaded GLB is no longer available.")
        return this.file.inputStream()
    }
}
