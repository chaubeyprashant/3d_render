package com.example.a3d_render.domain.model

data class DriveFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val modifiedTime: Long,
    val isFolder: Boolean
) {
    val isGlb: Boolean get() = !isFolder && name.endsWith(".glb", ignoreCase = true)
}
