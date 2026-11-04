package com.example.a3d_render.ui.onedrive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a3d_render.data.onedrive.OneDriveManager
import com.example.a3d_render.data.onedrive.OneDriveHttpException
import com.example.a3d_render.data.onedrive.OneDriveRedirectBus
import com.example.a3d_render.data.onedrive.OneDriveSignInException
import com.example.a3d_render.data.project.ProjectLibrary
import com.example.a3d_render.domain.model.DriveFileItem
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.ui.common.ImportController
import com.example.a3d_render.ui.common.ImportState
import com.example.a3d_render.ui.drive.DriveFolderRef
import com.example.a3d_render.ui.drive.DrivePickerUiState
import com.example.a3d_render.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

val ONEDRIVE_ROOT_FOLDER = DriveFolderRef(OneDriveManager.ROOT_FOLDER_ID, "OneDrive")

/** Browses OneDrive with the same UI state as the Google Drive picker. */
class OneDrivePickerViewModel(
    application: Application,
    private val oneDriveManager: OneDriveManager,
    private val projectLibrary: ProjectLibrary
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DrivePickerUiState(folderPath = listOf(ONEDRIVE_ROOT_FOLDER)))
    val uiState: StateFlow<DrivePickerUiState> = _uiState.asStateFlow()

    private val importController = ImportController(projectLibrary, viewModelScope)
    val importState: StateFlow<ImportState> = importController.state

    private var listJob: Job? = null

    init {
        if (oneDriveManager.isSignedIn()) {
            _uiState.update { it.copy(isSignedIn = true, accountEmail = oneDriveManager.signedInEmail()) }
            loadCurrentFolder()
        }
        viewModelScope.launch {
            OneDriveRedirectBus.redirect.filterNotNull().collect { uri ->
                OneDriveRedirectBus.redirect.value = null
                completeSignIn(uri)
            }
        }
    }

    /** The browser URL to open for sign-in, or null (with an error shown) when OneDrive isn't set up. */
    fun startSignIn(): Uri? {
        if (!oneDriveManager.isConfigured) {
            _uiState.update {
                it.copy(
                    signInError = "OneDrive is not configured for this app build " +
                        "(set ONEDRIVE_CLIENT_ID in gradle.properties)."
                )
            }
            return null
        }
        if (!NetworkMonitor.isOnline(getApplication())) {
            _uiState.update { it.copy(signInError = OFFLINE_MESSAGE) }
            return null
        }
        _uiState.update { it.copy(signInError = null) }
        return oneDriveManager.createSignInUri()
    }

    private suspend fun completeSignIn(redirect: Uri) {
        try {
            withContext(Dispatchers.IO) { oneDriveManager.completeSignIn(redirect) }
            _uiState.update {
                it.copy(isSignedIn = true, accountEmail = oneDriveManager.signedInEmail(), signInError = null)
            }
            loadCurrentFolder()
        } catch (e: CancellationException) {
            throw e
        } catch (e: OneDriveSignInException) {
            _uiState.update { it.copy(isSignedIn = false, signInError = if (e.cancelled) null else e.message) }
        } catch (e: IOException) {
            _uiState.update { it.copy(isSignedIn = false, signInError = OFFLINE_MESSAGE) }
        }
    }

    fun signOut() {
        listJob?.cancel()
        oneDriveManager.signOut()
        _uiState.value = DrivePickerUiState(folderPath = listOf(ONEDRIVE_ROOT_FOLDER))
    }

    fun openFolder(folder: DriveFileItem) {
        if (!folder.isFolder) return
        _uiState.update { it.copy(folderPath = it.folderPath + DriveFolderRef(folder.id, folder.name)) }
        loadCurrentFolder()
    }

    /** Goes to the parent folder; returns false at the OneDrive root so the caller can leave the screen. */
    fun navigateUp(): Boolean {
        val path = _uiState.value.folderPath
        if (path.size <= 1) return false
        navigateTo(path.size - 2)
        return true
    }

    fun navigateTo(index: Int) {
        val path = _uiState.value.folderPath
        if (index !in path.indices || index == path.lastIndex) return
        _uiState.update { it.copy(folderPath = path.take(index + 1)) }
        loadCurrentFolder()
    }

    fun loadCurrentFolder() {
        val folder = _uiState.value.currentFolder
        listJob?.cancel()
        if (!NetworkMonitor.isOnline(getApplication())) {
            _uiState.update { it.copy(isLoading = false, entries = emptyList(), listError = OFFLINE_MESSAGE) }
            return
        }
        _uiState.update { it.copy(isLoading = true, listError = null) }
        listJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { oneDriveManager.listFolder(folder.id) }
                val visible = items.filter { it.isFolder || it.isGlb }
                    .sortedWith(compareBy<DriveFileItem>({ !it.isFolder }, { it.name.lowercase() }))
                _uiState.update {
                    it.copy(isLoading = false, entries = visible, hiddenFileCount = items.size - visible.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("OneDrive", "Folder load failed", e)
                val sessionExpired = !oneDriveManager.isSignedIn()
                _uiState.update {
                    if (sessionExpired) {
                        DrivePickerUiState(
                            folderPath = listOf(ONEDRIVE_ROOT_FOLDER),
                            signInError = "Your OneDrive session expired. Please sign in again."
                        )
                    } else {
                        val noOneDrive = folder.id == OneDriveManager.ROOT_FOLDER_ID &&
                            (e as? OneDriveHttpException)?.statusCode == 404
                        it.copy(
                            isLoading = false,
                            entries = emptyList(),
                            listError = if (noOneDrive) {
                                NO_ONEDRIVE_MESSAGE
                            } else {
                                "Couldn't load this OneDrive folder. Check your connection and try again."
                            }
                        )
                    }
                }
            }
        }
    }

    fun openCurrentFolderAsProject(onReady: (ProjectItem) -> Unit) {
        val folder = _uiState.value.currentFolder
        importController.importNew(
            scanMessage = "Scanning \"${folder.name}\" for a GLB model…",
            prepare = { projectLibrary.prepareOneDriveFolder(folder.id, folder.name) },
            onReady = onReady
        )
    }

    fun openGlbFile(file: DriveFileItem, onReady: (ProjectItem) -> Unit) {
        val folder = _uiState.value.currentFolder
        importController.importNew(
            scanMessage = "Checking \"${file.name}\"…",
            prepare = { projectLibrary.prepareOneDriveFile(folder.id, folder.name, file) },
            onReady = onReady
        )
    }

    fun confirmLargeFile() = importController.confirmLargeFile()

    fun dismissLargeFile() = importController.dismissLargeFile()

    fun clearImportError() = importController.clearError()

    class Factory(
        private val application: Application,
        private val oneDriveManager: OneDriveManager,
        private val projectLibrary: ProjectLibrary
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OneDrivePickerViewModel(application, oneDriveManager, projectLibrary) as T
        }
    }

    private companion object {
        const val NO_ONEDRIVE_MESSAGE = "This Microsoft account doesn't have a OneDrive. " +
            "Open OneDrive in a browser with this account to set it up (work accounts need a OneDrive license), " +
            "or sign out and use a different account."
        const val OFFLINE_MESSAGE = "No internet connection. OneDrive needs internet access."
    }
}
