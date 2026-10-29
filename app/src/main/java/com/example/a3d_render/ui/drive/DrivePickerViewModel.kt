package com.example.a3d_render.ui.drive

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.a3d_render.data.drive.GoogleDriveManager
import com.example.a3d_render.data.project.ProjectLibrary
import com.example.a3d_render.domain.model.DriveFileItem
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.ui.common.ImportController
import com.example.a3d_render.ui.common.ImportState
import com.example.a3d_render.util.NetworkMonitor
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DriveFolderRef(val id: String, val name: String)

data class DrivePickerUiState(
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val folderPath: List<DriveFolderRef> = listOf(ROOT_FOLDER),
    /** Sub-folders first, then GLB files; other file types are hidden. */
    val entries: List<DriveFileItem> = emptyList(),
    val hiddenFileCount: Int = 0,
    val isLoading: Boolean = false,
    val listError: String? = null,
    val signInError: String? = null
) {
    val currentFolder: DriveFolderRef get() = folderPath.last()
    val isAtRoot: Boolean get() = folderPath.size == 1
}

val ROOT_FOLDER = DriveFolderRef(GoogleDriveManager.ROOT_FOLDER_ID, "My Drive")

class DrivePickerViewModel(
    application: Application,
    private val driveManager: GoogleDriveManager,
    private val projectLibrary: ProjectLibrary
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DrivePickerUiState())
    val uiState: StateFlow<DrivePickerUiState> = _uiState.asStateFlow()

    private val importController = ImportController(projectLibrary, viewModelScope)
    val importState: StateFlow<ImportState> = importController.state

    /** Intent to show when listing needs the user to re-approve Drive access. */
    private val _authRecoveryIntent = MutableStateFlow<Intent?>(null)
    val authRecoveryIntent: StateFlow<Intent?> = _authRecoveryIntent.asStateFlow()

    private var listJob: Job? = null

    init {
        if (driveManager.isSignedIn()) {
            _uiState.update { it.copy(isSignedIn = true, accountEmail = driveManager.signedInEmail()) }
            loadCurrentFolder()
        }
    }

    fun getSignInIntent(): Intent = driveManager.getSignInIntent()

    fun handleSignInResult(intent: Intent?) {
        try {
            val account = driveManager.handleSignInResult(intent)
            _uiState.update { it.copy(isSignedIn = true, accountEmail = account.email, signInError = null) }
            loadCurrentFolder()
        } catch (e: ApiException) {
            android.util.Log.e("DrivePickerViewModel", "Google Sign-In failed with status code: ${e.statusCode}", e)
            _uiState.update { it.copy(isSignedIn = false, signInError = signInErrorMessage(e.statusCode)) }
        }
    }

    fun signOut() {
        listJob?.cancel()
        driveManager.signOut()
        _uiState.value = DrivePickerUiState()
    }

    fun openFolder(folder: DriveFileItem) {
        if (!folder.isFolder) return
        _uiState.update { it.copy(folderPath = it.folderPath + DriveFolderRef(folder.id, folder.name)) }
        loadCurrentFolder()
    }

    /** Goes to the parent folder; returns false at My Drive so the caller can leave the screen. */
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
            _uiState.update {
                it.copy(isLoading = false, entries = emptyList(), listError = OFFLINE_MESSAGE)
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, listError = null) }
        listJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { driveManager.listFolder(folder.id) }
                val visible = items.filter { it.isFolder || it.isGlb }
                    .sortedWith(compareBy<DriveFileItem>({ !it.isFolder }, { it.name.lowercase() }))
                _uiState.update {
                    it.copy(isLoading = false, entries = visible, hiddenFileCount = items.size - visible.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserRecoverableAuthIOException) {
                _uiState.update { it.copy(isLoading = false) }
                _authRecoveryIntent.value = e.intent
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = emptyList(),
                        listError = "Couldn't load this Google Drive folder. Check your connection and try again."
                    )
                }
            }
        }
    }

    fun consumeAuthRecoveryIntent() {
        _authRecoveryIntent.value = null
        importController.consumeDriveAuthIntent()
    }

    fun openCurrentFolderAsProject(onReady: (ProjectItem) -> Unit) {
        val folder = _uiState.value.currentFolder
        importController.importNew(
            scanMessage = "Scanning \"${folder.name}\" for a GLB model…",
            prepare = { projectLibrary.prepareDriveFolder(folder.id, folder.name) },
            onReady = onReady
        )
    }

    fun openGlbFile(file: DriveFileItem, onReady: (ProjectItem) -> Unit) {
        val folder = _uiState.value.currentFolder
        importController.importNew(
            scanMessage = "Checking \"${file.name}\"…",
            prepare = { projectLibrary.prepareDriveFile(folder.id, folder.name, file) },
            onReady = onReady
        )
    }

    fun confirmLargeFile() = importController.confirmLargeFile()

    fun dismissLargeFile() = importController.dismissLargeFile()

    fun clearImportError() = importController.clearError()

    private fun signInErrorMessage(statusCode: Int): String? = when (statusCode) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null
        CommonStatusCodes.NETWORK_ERROR -> OFFLINE_MESSAGE
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google Sign-In is not configured for this app build (OAuth client / SHA-1 missing in Google Cloud)."
        else -> "Google sign-in failed (code $statusCode). Please try again."
    }

    class Factory(
        private val application: Application,
        private val driveManager: GoogleDriveManager,
        private val projectLibrary: ProjectLibrary
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DrivePickerViewModel(application, driveManager, projectLibrary) as T
        }
    }

    private companion object {
        const val OFFLINE_MESSAGE = "No internet connection. Google Drive needs internet access."
    }
}
