package com.example.a3d_render.domain.project

import java.io.InputStream

/** A file or folder inside a project source (device storage or Google Drive). */
data class SourceNode(
    val name: String,
    /** Opaque handle: a SAF document ID or a Drive file ID. */
    val ref: String,
    val isDirectory: Boolean,
    /** Size in bytes, or -1 when the provider does not report it. */
    val size: Long
)

/** Read-only, lazily listed view of a project folder. Calls block; use a background dispatcher. */
interface ProjectSourceTree {
    val root: SourceNode
    fun listChildren(directory: SourceNode): List<SourceNode>
    fun openStream(file: SourceNode): InputStream

    /** Opens just the first [byteCount] bytes; remote sources should fetch no more than that. */
    fun openHead(file: SourceNode, byteCount: Long): InputStream = openStream(file)
}
