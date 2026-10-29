package com.example.a3d_render.ui.common

import android.content.Intent
import com.example.a3d_render.data.project.DriveAuthRequiredException
import com.example.a3d_render.data.project.PreparedImport
import com.example.a3d_render.data.project.ProjectLibrary
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.domain.project.ProjectImportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ImportProgress(val message: String, val fraction: Float?)

data class LargeFilePrompt(val projectName: String, val sizeBytes: Long)

data class ImportState(
    val progress: ImportProgress? = null,
    val largeFilePrompt: LargeFilePrompt? = null,
    val error: String? = null,
    /** Set when Google Drive needs the user to re-approve access. */
    val driveAuthIntent: Intent? = null
) {
    val isBusy: Boolean get() = progress != null
}

/**
 * Runs scan → (confirm if over the recommended size) → copy for a project, exposing progress
 * and user-facing errors. Shared by the dashboard and the Google Drive browser.
 */
class ImportController(
    private val library: ProjectLibrary,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var pending: Pair<PreparedImport, (ProjectItem) -> Unit>? = null
    private var job: Job? = null

    fun importNew(
        scanMessage: String,
        prepare: suspend () -> PreparedImport,
        onReady: (ProjectItem) -> Unit
    ) = launchGuarded(ImportProgress(scanMessage, null)) {
        val prepared = prepare()
        if (prepared.plan.exceedsRecommendedSize) {
            pending = prepared to onReady
            _state.update {
                it.copy(progress = null, largeFilePrompt = LargeFilePrompt(prepared.displayName, prepared.plan.glbSizeBytes))
            }
        } else {
            copy(prepared, onReady)
        }
    }

    fun open(project: ProjectItem, onReady: (ProjectItem) -> Unit) {
        if (library.isAvailableOffline(project)) {
            launchGuarded(ImportProgress("Opening project…", null)) {
                val opened = library.open(project) {}
                _state.update { it.copy(progress = null) }
                onReady(opened)
            }
            return
        }
        launchGuarded(ImportProgress(copyMessage(project.source, 0f), 0f)) {
            val opened = library.open(project) { fraction -> reportProgress(project.source, fraction) }
            _state.update { it.copy(progress = null) }
            onReady(opened)
        }
    }

    fun confirmLargeFile() {
        val (prepared, onReady) = pending ?: return
        pending = null
        _state.update { it.copy(largeFilePrompt = null) }
        launchGuarded(ImportProgress(copyMessage(prepared.source, 0f), 0f)) { copy(prepared, onReady) }
    }

    fun dismissLargeFile() {
        pending = null
        _state.update { it.copy(largeFilePrompt = null) }
    }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun clearError() = _state.update { it.copy(error = null) }

    fun consumeDriveAuthIntent() = _state.update { it.copy(driveAuthIntent = null) }

    private suspend fun copy(prepared: PreparedImport, onReady: (ProjectItem) -> Unit) {
        val project = library.import(prepared) { fraction -> reportProgress(prepared.source, fraction) }
        _state.update { it.copy(progress = null) }
        onReady(project)
    }

    private fun reportProgress(source: ProjectSource, fraction: Float) {
        val percent = (fraction * 100).roundToInt()
        val current = _state.value.progress
        if (current?.fraction != null && (current.fraction * 100).roundToInt() == percent) return
        _state.update { it.copy(progress = ImportProgress(copyMessage(source, fraction), fraction)) }
    }

    private fun copyMessage(source: ProjectSource, fraction: Float): String {
        val percent = (fraction * 100).roundToInt()
        return when {
            fraction > COPY_PHASE_END -> "Optimizing textures for this device… $percent%"
            source == ProjectSource.LOCAL -> "Copying project files… $percent%"
            source == ProjectSource.ONEDRIVE -> "Downloading from OneDrive… $percent%"
            else -> "Downloading from Google Drive… $percent%"
        }
    }

    private companion object {
        /** Matches ProjectLibrary's copy share; the rest of the bar is texture optimization. */
        const val COPY_PHASE_END = 0.8f
    }

    private fun launchGuarded(initial: ImportProgress, block: suspend () -> Unit) {
        if (job?.isActive == true) return
        _state.update { it.copy(progress = initial, error = null) }
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DriveAuthRequiredException) {
                _state.update { it.copy(progress = null, driveAuthIntent = e.intent) }
            } catch (e: ProjectImportException) {
                _state.update { it.copy(progress = null, error = e.message) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(progress = null, error = "Not enough memory to import this project.") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(progress = null, error = "Unable to load the project: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }
}
