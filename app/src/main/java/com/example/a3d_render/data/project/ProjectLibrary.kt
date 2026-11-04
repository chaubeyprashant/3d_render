package com.example.a3d_render.data.project

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.a3d_render.data.drive.GoogleDriveManager
import com.example.a3d_render.data.onedrive.OneDriveAuthRequiredException
import com.example.a3d_render.data.onedrive.OneDriveHttpException
import com.example.a3d_render.data.onedrive.OneDriveManager
import com.example.a3d_render.domain.model.DriveFileItem
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.domain.project.GlbSizePolicy
import com.example.a3d_render.domain.project.GlbTextureOptimizer
import com.example.a3d_render.domain.project.ImportPlan
import com.example.a3d_render.domain.project.MissingRelatedFilesException
import com.example.a3d_render.domain.project.ProjectImportException
import com.example.a3d_render.domain.project.ProjectImporter
import com.example.a3d_render.domain.project.ProjectSourceTree
import com.example.a3d_render.domain.repository.ProjectRepository
import com.example.a3d_render.util.ByteFormat
import com.example.a3d_render.util.DeviceCapabilities
import com.example.a3d_render.util.NetworkMonitor
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Google Drive needs the user to (re-)approve access; launch [intent] and retry. */
class DriveAuthRequiredException(val intent: Intent) : Exception("Google Drive access needs approval.")

/** A scanned and validated project, ready to be copied with [ProjectLibrary.import]. */
class PreparedImport internal constructor(
    val plan: ImportPlan,
    val displayName: String,
    internal val tree: ProjectSourceTree,
    internal val projectId: String,
    internal val source: ProjectSource,
    internal val rootRef: String
)

/**
 * Imports projects from device storage or Google Drive into the on-device cache, keeps the
 * recent-projects list in sync, and re-fetches evicted projects when they are reopened.
 */
class ProjectLibrary(
    private val context: Context,
    private val repository: ProjectRepository,
    private val driveManager: GoogleDriveManager,
    private val oneDriveManager: OneDriveManager
) {
    private val cache = ProjectCache(File(context.filesDir, "projects"))
    private val importer = ProjectImporter(
        hardLimitBytes = GlbSizePolicy.hardLimitBytes(DeviceCapabilities.totalRamBytes(context))
    )
    private val textureOptimizer = GlbTextureOptimizer(
        resampler = BitmapTextureResampler(),
        budgetBytes = GlbTextureOptimizer.budgetFor(DeviceCapabilities.totalRamBytes(context))
    )

    init {
        ProjectCache.purgeLegacyFiles(context.filesDir, context.cacheDir)
    }

    suspend fun prepareLocalFolder(treeUri: Uri): PreparedImport = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val tree = openLocalTree(treeUri)
        val plan = guardSourceErrors(ProjectSource.LOCAL) { importer.scan(tree) }
        PreparedImport(plan, tree.root.name, tree, treeUri.toString(), ProjectSource.LOCAL, treeUri.toString())
    }

    /** A single GLB picked with the file picker (e.g. one saved straight to Downloads). */
    suspend fun prepareLocalFile(documentUri: Uri): PreparedImport = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(documentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val tree = openLocalFile(documentUri)
        val plan = guardSourceErrors(ProjectSource.LOCAL) { scanSingleFile(tree) }
        PreparedImport(
            plan, tree.file.name.substringBeforeLast('.'), tree,
            "$FILE_PROJECT_PREFIX$documentUri", ProjectSource.LOCAL, documentUri.toString()
        )
    }

    suspend fun prepareDriveFolder(folderId: String, folderName: String): PreparedImport =
        withContext(Dispatchers.IO) {
            requireOnline(ProjectSource.GOOGLE_DRIVE)
            val tree = guardSourceErrors(ProjectSource.GOOGLE_DRIVE) { driveManager.sourceTree(folderId, folderName) }
            val plan = guardSourceErrors(ProjectSource.GOOGLE_DRIVE) { importer.scan(tree) }
            PreparedImport(plan, folderName, tree, "drive:folder:$folderId", ProjectSource.GOOGLE_DRIVE, folderId)
        }

    /** A GLB tapped directly in the Drive browser; its folder supplies any related files. */
    suspend fun prepareDriveFile(parentId: String, parentName: String, file: DriveFileItem): PreparedImport =
        withContext(Dispatchers.IO) {
            requireOnline(ProjectSource.GOOGLE_DRIVE)
            val tree = guardSourceErrors(ProjectSource.GOOGLE_DRIVE) { driveManager.sourceTree(parentId, parentName) }
            val plan = guardSourceErrors(ProjectSource.GOOGLE_DRIVE) { importer.scan(tree, file.name) }
            PreparedImport(
                plan, file.name.substringBeforeLast('.'), tree,
                "drive:file:${file.id}", ProjectSource.GOOGLE_DRIVE, parentId
            )
        }

    suspend fun prepareOneDriveFolder(folderId: String, folderName: String): PreparedImport =
        withContext(Dispatchers.IO) {
            requireOnline(ProjectSource.ONEDRIVE)
            val tree = guardSourceErrors(ProjectSource.ONEDRIVE) { oneDriveManager.sourceTree(folderId, folderName) }
            val plan = guardSourceErrors(ProjectSource.ONEDRIVE) { importer.scan(tree) }
            PreparedImport(plan, folderName, tree, "onedrive:folder:$folderId", ProjectSource.ONEDRIVE, folderId)
        }

    /** A GLB tapped directly in the OneDrive browser; its folder supplies any related files. */
    suspend fun prepareOneDriveFile(parentId: String, parentName: String, file: DriveFileItem): PreparedImport =
        withContext(Dispatchers.IO) {
            requireOnline(ProjectSource.ONEDRIVE)
            val tree = guardSourceErrors(ProjectSource.ONEDRIVE) { oneDriveManager.sourceTree(parentId, parentName) }
            val plan = guardSourceErrors(ProjectSource.ONEDRIVE) { importer.scan(tree, file.name) }
            PreparedImport(
                plan, file.name.substringBeforeLast('.'), tree,
                "onedrive:file:${file.id}", ProjectSource.ONEDRIVE, parentId
            )
        }

    suspend fun import(prepared: PreparedImport, onProgress: (Float) -> Unit): ProjectItem =
        withContext(Dispatchers.IO) {
            val plan = prepared.plan
            val required = plan.totalBytes + STORAGE_HEADROOM_BYTES
            val available = context.filesDir.usableSpace
            if (available < required) {
                throw ProjectImportException(
                    "Not enough free storage. This project needs ${ByteFormat.format(required)}; " +
                        "only ${ByteFormat.format(available)} is available."
                )
            }
            val job = coroutineContext[Job]
            val staging = cache.newStagingDirectory(prepared.projectId)
            val glbName = try {
                val glb = guardSourceErrors(prepared.source) {
                    importer.materialize(prepared.tree, plan, staging, { onProgress(it * COPY_PROGRESS_SHARE) }) {
                        job?.ensureActive()
                    }
                }
                optimizeTextures(glb, staging) { onProgress(COPY_PROGRESS_SHARE + it * (1f - COPY_PROGRESS_SHARE)) }
                File(staging, CACHE_MARKER).writeText(glb.length().toString())
                glb.name
            } catch (t: Throwable) {
                staging.deleteRecursively()
                throw t
            }
            val directory = cache.commit(prepared.projectId, staging)
            val existingName = repository.getRecentProjects().firstOrNull { it.id == prepared.projectId }?.name
            save(
                ProjectItem(
                    id = prepared.projectId,
                    name = existingName ?: prepared.displayName,
                    folderUri = prepared.rootRef,
                    glbUri = File(directory, glbName).absolutePath,
                    source = prepared.source,
                    glbSizeInBytes = plan.glbSizeBytes,
                    lastOpenedAt = System.currentTimeMillis(),
                    glbRelativePath = plan.glbRelativePath,
                    relatedFileCount = plan.resources.size
                )
            )
        }

    /** Returns the project ready to view, re-fetching it from its source if the cache was evicted. */
    suspend fun open(project: ProjectItem, onProgress: (Float) -> Unit): ProjectItem =
        withContext(Dispatchers.IO) {
            if (isAvailableOffline(project)) {
                cache.touch(project.id)
                return@withContext save(project.copy(lastOpenedAt = System.currentTimeMillis()))
            }
            if (project.id.startsWith(FILE_PROJECT_PREFIX)) {
                val tree = openLocalFile(Uri.parse(project.folderUri))
                val plan = guardSourceErrors(project.source) { scanSingleFile(tree) }
                return@withContext import(
                    PreparedImport(plan, project.name, tree, project.id, project.source, project.folderUri),
                    onProgress
                )
            }
            val tree = when (project.source) {
                ProjectSource.LOCAL -> openLocalTree(Uri.parse(project.folderUri))
                ProjectSource.GOOGLE_DRIVE -> {
                    if (!project.id.startsWith("drive:")) {
                        throw ProjectImportException("This project was added by an older version. Open it again from Google Drive.")
                    }
                    requireOnlineToRedownload(project, "Google Drive")
                    guardSourceErrors(project.source) { driveManager.sourceTree(project.folderUri, project.name) }
                }
                ProjectSource.ONEDRIVE -> {
                    if (!project.id.startsWith("onedrive:")) {
                        throw ProjectImportException("This project can't be re-downloaded. Open it again from OneDrive.")
                    }
                    requireOnlineToRedownload(project, "OneDrive")
                    guardSourceErrors(project.source) { oneDriveManager.sourceTree(project.folderUri, project.name) }
                }
            }
            val plan = guardSourceErrors(project.source) {
                importer.scan(tree, project.glbRelativePath.ifBlank { null })
            }
            import(
                PreparedImport(plan, project.name, tree, project.id, project.source, project.folderUri),
                onProgress
            )
        }

    suspend fun delete(project: ProjectItem) = withContext(Dispatchers.IO) {
        repository.deleteProject(project.id)
        cache.delete(project.id)
        releaseUnusedFolderPermissions(repository.getRecentProjects())
    }

    /** True when the prepared (texture-optimized) copy is complete on this device. */
    fun isAvailableOffline(project: ProjectItem): Boolean {
        val glb = File(project.glbUri)
        val directory = cache.directoryFor(project.id)
        if (!glb.isFile || glb.parentFile != directory) return false
        val expectedLength = runCatching { File(directory, CACHE_MARKER).readText().trim().toLong() }.getOrNull()
        return expectedLength == glb.length()
    }

    /**
     * Downscales oversized textures so the model fits in GPU memory. If this fails the full
     * resolution copy is kept and the viewer still tries to load it.
     */
    private fun optimizeTextures(glb: File, directory: File, onProgress: (Float) -> Unit) {
        val manifest = runCatching {
            JsonParser.parseString(File(directory, ProjectImporter.RESOURCE_MANIFEST).readText()).asJsonObject
                .entrySet().associate { (uri, path) -> uri to path.asString }
        }.getOrDefault(emptyMap())
        try {
            val result = textureOptimizer.optimize(glb, { uri -> manifest[uri]?.let { File(directory, it) } }, onProgress)
            if (result.texturesResized > 0) {
                Log.i(
                    TAG,
                    "Resized ${result.texturesResized} textures: ~${ByteFormat.format(result.estimatedBytesBefore)} → " +
                        "~${ByteFormat.format(result.estimatedBytesAfter)} GPU memory"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Texture optimization failed; keeping original textures", e)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Texture optimization ran out of memory; keeping original textures", e)
        }
    }

    private suspend fun save(project: ProjectItem): ProjectItem {
        repository.upsertProject(project)
        val recent = repository.getRecentProjects()
        cache.prune(keepProjectIds = recent.map { it.id }, pinnedProjectId = project.id)
        releaseUnusedFolderPermissions(recent)
        return project
    }

    /** Persisted folder grants are capped per app; drop those no recent project uses. */
    private fun releaseUnusedFolderPermissions(recent: List<ProjectItem>) {
        val inUse = recent.filter { it.source == ProjectSource.LOCAL }.map { it.folderUri }.toSet()
        context.contentResolver.persistedUriPermissions
            .filter { it.uri.toString() !in inUse }
            .forEach { permission ->
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        permission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
    }

    private fun openLocalTree(treeUri: Uri): ProjectSourceTree = try {
        DocumentSourceTree(context, treeUri)
    } catch (e: SecurityException) {
        throw ProjectImportException(FOLDER_ACCESS_LOST, e)
    } catch (e: IllegalArgumentException) {
        throw ProjectImportException("The saved location is not a folder. Select the project folder again.", e)
    }

    private fun openLocalFile(documentUri: Uri): SingleDocumentSourceTree = try {
        SingleDocumentSourceTree(context, documentUri)
    } catch (e: SecurityException) {
        throw ProjectImportException("Access to the file was lost. Select it again with \"Load from device\".", e)
    }

    /** Validated by its GLB header, so downloads renamed like `model.glb (1)` still open. */
    private fun scanSingleFile(tree: SingleDocumentSourceTree): ImportPlan = try {
        importer.scan(tree, tree.file.name)
    } catch (e: MissingRelatedFilesException) {
        throw ProjectImportException(
            "This GLB uses separate texture files (${e.missing.first()}${if (e.missing.size > 1) " and ${e.missing.size - 1} more" else ""}). " +
                "Open the folder that contains it instead of the single file.",
            e
        )
    }

    private fun requireOnline(source: ProjectSource) {
        if (!NetworkMonitor.isOnline(context)) {
            throw ProjectImportException(
                "No internet connection. ${providerName(source)} needs internet access — projects on this device still open offline."
            )
        }
    }

    private fun requireOnlineToRedownload(project: ProjectItem, provider: String) {
        if (!NetworkMonitor.isOnline(context)) {
            throw ProjectImportException(
                "\"${project.name}\" is not stored on this device. Connect to the internet to download it again from $provider."
            )
        }
    }

    private fun providerName(source: ProjectSource): String = when (source) {
        ProjectSource.GOOGLE_DRIVE -> "Google Drive"
        ProjectSource.ONEDRIVE -> "OneDrive"
        ProjectSource.LOCAL -> "The device"
    }

    private inline fun <T> guardSourceErrors(source: ProjectSource, block: () -> T): T = try {
        block()
    } catch (e: ProjectImportException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: UserRecoverableAuthIOException) {
        throw DriveAuthRequiredException(e.intent)
    } catch (e: GoogleJsonResponseException) {
        throw ProjectImportException(
            when (e.statusCode) {
                401, 403 -> "Google Drive denied access to this item. Check that your account can open it."
                404 -> "This Google Drive item no longer exists or is no longer shared with you."
                else -> "Google Drive error (${e.statusCode}). Please try again."
            },
            e
        )
    } catch (e: OneDriveAuthRequiredException) {
        throw ProjectImportException("Your OneDrive session expired. Open OneDrive and sign in again.", e)
    } catch (e: OneDriveHttpException) {
        throw ProjectImportException(
            when (e.statusCode) {
                401, 403 -> "OneDrive denied access to this item. Check that your account can open it."
                404 -> "This OneDrive item no longer exists or is no longer shared with you."
                429 -> "OneDrive is busy right now. Please try again in a moment."
                else -> "OneDrive error (${e.statusCode}). Please try again."
            },
            e
        )
    } catch (e: SecurityException) {
        throw ProjectImportException(FOLDER_ACCESS_LOST, e)
    } catch (e: IOException) {
        throw ProjectImportException(
            if (source == ProjectSource.LOCAL) {
                "Couldn't read the project files: ${e.message ?: "I/O error"}."
            } else {
                "Couldn't reach ${providerName(source)}. Check your internet connection and try again."
            },
            e
        )
    } catch (e: IllegalStateException) {
        throw ProjectImportException(e.message ?: "Unable to load the project.", e)
    }

    private companion object {
        const val STORAGE_HEADROOM_BYTES = 64L * 1024L * 1024L
        const val FILE_PROJECT_PREFIX = "file:"
        const val TAG = "ProjectLibrary"
        /** Written after a complete import; holds the prepared GLB's length. */
        const val CACHE_MARKER = "prepared.txt"
        const val COPY_PROGRESS_SHARE = 0.8f
        const val FOLDER_ACCESS_LOST =
            "Access to the project folder was lost or it was moved. Select the folder again with \"Load from device\"."
    }
}
