package com.example.a3d_render.ui.dashboard

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.domain.project.ProjectScanner
import com.example.a3d_render.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val recentProjects: List<ProjectItem> = emptyList()
)

class DashboardViewModel(
    application: Application,
    private val projectRepository: ProjectRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshRecentProjects()
    }

    fun refreshRecentProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val projects = projectRepository.getRecentProjects()
            _uiState.update { it.copy(isLoading = false, recentProjects = projects) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun renameProject(projectId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Project name cannot be empty.") }
            return
        }
        viewModelScope.launch {
            projectRepository.renameProject(projectId, trimmed)
            refreshRecentProjects()
        }
    }

    fun onProjectFolderPicked(
        uri: Uri,
        source: ProjectSource,
        onProjectReady: (ProjectItem) -> Unit
    ) {
        processPickedProject(
            uri = uri,
            onProjectReady = onProjectReady
        ) {
            ProjectScanner.validateAndBuildProject(
                context = getApplication(),
                folderUri = uri,
                source = source
            )
        }
    }

    fun onProjectFilePicked(
        uri: Uri,
        source: ProjectSource,
        onProjectReady: (ProjectItem) -> Unit
    ) {
        processPickedProject(
            uri = uri,
            onProjectReady = onProjectReady
        ) {
            ProjectScanner.validateAndBuildProjectFromFile(
                context = getApplication(),
                fileUri = uri,
                source = source
            )
        }
    }

    private fun processPickedProject(
        uri: Uri,
        onProjectReady: (ProjectItem) -> Unit,
        validator: () -> Result<ProjectItem>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            val result = validator()

            result.onSuccess { project ->
                projectRepository.upsertProject(project)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recentProjects = listOf(project) + it.recentProjects.filterNot { item ->
                            item.id == project.id
                        }
                    )
                }
                onProjectReady(project)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to load selected project."
                    )
                }
            }
        }
    }

    class Factory(
        private val application: Application,
        private val projectRepository: ProjectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(application, projectRepository) as T
        }
    }
}
