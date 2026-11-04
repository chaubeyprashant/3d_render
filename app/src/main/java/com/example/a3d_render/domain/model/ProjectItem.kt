package com.example.a3d_render.domain.model

data class ProjectItem(
    val id: String,
    /** Display name; renaming never touches the project's files. */
    val name: String,
    /** Project root: a SAF tree URI (local) or a Drive folder ID. */
    val folderUri: String,
    /** Absolute path of the cached GLB used by the viewer. */
    val glbUri: String,
    val source: ProjectSource,
    val glbSizeInBytes: Long,
    val lastOpenedAt: Long,
    /** GLB location inside [folderUri], used to re-fetch the project if its cache was cleared. */
    val glbRelativePath: String = "",
    /** External textures/buffers loaded alongside the GLB. */
    val relatedFileCount: Int = 0
)
