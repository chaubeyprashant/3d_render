package com.example.a3d_render.domain.model

data class ProjectItem(
    val id: String,
    val name: String,
    val folderUri: String,
    val glbUri: String,
    val source: ProjectSource,
    val glbSizeInBytes: Long,
    val lastOpenedAt: Long
)
