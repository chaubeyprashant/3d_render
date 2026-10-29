package com.example.a3d_render.ui.dashboard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a3d_render.data.project.ProjectLibrary
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.repository.ProjectRepository
import com.example.a3d_render.ui.common.ImportController
import com.example.a3d_render.ui.common.ImportState
import com.example.a3d_render.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val recentProjects: List<ProjectItem> = emptyList(),
    /** Projects whose files are on the device and open without internet. */
    val offlineProjectIds: Set<String> = emptySet(),
    val isOnline: Boolean = true
)

class DashboardViewModel(
    application: Application,
    private val projectRepository: ProjectRepository,
    private val projectLibrary: ProjectLibrary
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val importController = ImportController(projectLibrary, viewModelScope)
    val importState: StateFlow<ImportState> = importController.state

    init {
        refreshRecentProjects()
    }

    fun refreshRecentProjects() {
        viewModelScope.launch {
            val projects = projectRepository.getRecentProjects()
            _uiState.update {
                it.copy(
                    recentProjects = projects,
                    offlineProjectIds = projects.filter(projectLibrary::isAvailableOffline).map { p -> p.id }.toSet(),
                    isOnline = NetworkMonitor.isOnline(getApplication())
                )
            }
        }
    }

    fun onProjectFolderPicked(uri: Uri, onProjectReady: (ProjectItem) -> Unit) {
        importController.importNew(
            scanMessage = "Scanning project folder…",
            prepare = { projectLibrary.prepareLocalFolder(uri) },
            onReady = { project ->
                refreshRecentProjects()
                onProjectReady(project)
            }
        )
    }

    fun onGlbFilePicked(uri: Uri, onProjectReady: (ProjectItem) -> Unit) {
        importController.importNew(
            scanMessage = "Checking GLB file…",
            prepare = { projectLibrary.prepareLocalFile(uri) },
            onReady = { project ->
                refreshRecentProjects()
                onProjectReady(project)
            }
        )
    }

    fun openProject(project: ProjectItem, onProjectReady: (ProjectItem) -> Unit) {
        importController.open(project) { opened ->
            refreshRecentProjects()
            onProjectReady(opened)
        }
    }

    fun confirmLargeFile() = importController.confirmLargeFile()

    fun dismissLargeFile() = importController.dismissLargeFile()

    fun renameProject(projectId: String, newName: String) {
        val trimmed = newName.trim()
        if (validateProjectName(trimmed) != null) return
        viewModelScope.launch {
            projectRepository.renameProject(projectId, trimmed)
            refreshRecentProjects()
        }
    }

    fun deleteProject(project: ProjectItem) {
        viewModelScope.launch {
            projectLibrary.delete(project)
            refreshRecentProjects()
        }
    }

    fun clearError() = importController.clearError()

    class Factory(
        private val application: Application,
        private val projectRepository: ProjectRepository,
        private val projectLibrary: ProjectLibrary
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(application, projectRepository, projectLibrary) as T
        }
    }

    companion object {
        const val MAX_PROJECT_NAME_LENGTH = 60

        /** Returns an error message, or null when [name] is an acceptable project name. */
        fun validateProjectName(name: String): String? = when {
            name.isBlank() -> "Project name cannot be empty."
            name.trim().length > MAX_PROJECT_NAME_LENGTH -> "Keep the name under $MAX_PROJECT_NAME_LENGTH characters."
            else -> null
        }
    }
}
