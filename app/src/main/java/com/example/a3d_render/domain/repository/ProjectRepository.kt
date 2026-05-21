package com.example.a3d_render.domain.repository

import com.example.a3d_render.domain.model.ProjectItem

interface ProjectRepository {
    suspend fun getRecentProjects(): List<ProjectItem>
    suspend fun upsertProject(projectItem: ProjectItem)
    suspend fun renameProject(projectId: String, newName: String)
}
