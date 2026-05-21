package com.example.a3d_render.data.repository

import android.content.Context
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.domain.repository.ProjectRepository
import org.json.JSONArray
import org.json.JSONObject

class ProjectRepositoryImpl(context: Context) : ProjectRepository {
    private val sharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getRecentProjects(): List<ProjectItem> {
        val raw = sharedPreferences.getString(KEY_RECENT_PROJECTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val json = array.getJSONObject(i)
                    add(json.toProjectItem())
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun upsertProject(projectItem: ProjectItem) {
        val updated = getRecentProjects()
            .filterNot { it.id == projectItem.id }
            .toMutableList()
            .apply { add(0, projectItem) }
            .take(MAX_RECENT_PROJECTS)
        writeProjects(updated)
    }

    override suspend fun renameProject(projectId: String, newName: String) {
        val updated = getRecentProjects().map {
            if (it.id == projectId) it.copy(name = newName.trim()) else it
        }
        writeProjects(updated)
    }

    private fun writeProjects(projects: List<ProjectItem>) {
        val array = JSONArray()
        projects.forEach { array.put(it.toJson()) }
        sharedPreferences.edit().putString(KEY_RECENT_PROJECTS, array.toString()).apply()
    }

    private fun ProjectItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("folderUri", folderUri)
        put("glbUri", glbUri)
        put("source", source.name)
        put("glbSizeInBytes", glbSizeInBytes)
        put("lastOpenedAt", lastOpenedAt)
    }

    private fun JSONObject.toProjectItem(): ProjectItem = ProjectItem(
        id = getString("id"),
        name = getString("name"),
        folderUri = getString("folderUri"),
        glbUri = getString("glbUri"),
        source = ProjectSource.valueOf(getString("source")),
        glbSizeInBytes = getLong("glbSizeInBytes"),
        lastOpenedAt = getLong("lastOpenedAt")
    )

    companion object {
        private const val PREFS_NAME = "aims3d_projects"
        private const val KEY_RECENT_PROJECTS = "recent_projects"
        private const val MAX_RECENT_PROJECTS = 10
    }
}
