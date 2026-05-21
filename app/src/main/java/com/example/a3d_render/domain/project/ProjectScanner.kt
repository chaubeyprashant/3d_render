package com.example.a3d_render.domain.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import java.io.File
import java.io.FileOutputStream

object ProjectScanner {
    private const val MAX_GLB_SIZE_BYTES = 150L * 1024L * 1024L

    fun validateAndBuildProject(
        context: Context,
        folderUri: Uri,
        source: ProjectSource
    ): Result<ProjectItem> {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: return Result.failure(IllegalArgumentException("Cannot access selected folder."))

        val glbFile = findFirstGlb(folder)
            ?: return Result.failure(
                IllegalArgumentException("No GLB found. Select a folder containing a .glb file.")
            )

        if (glbFile.length() <= 0L) {
            return Result.failure(
                IllegalArgumentException("GLB file seems corrupted or empty.")
            )
        }

        if (glbFile.length() > MAX_GLB_SIZE_BYTES) {
            return Result.failure(
                IllegalArgumentException("GLB exceeds 150MB supported limit.")
            )
        }

        val folderName = folder.name?.ifBlank { null } ?: "Untitled Project"
        val projectName = folderName.substringBeforeLast(".")
        val localGlbPath = cacheGlbLocally(
            context = context,
            glbUri = glbFile.uri,
            projectId = folder.uri.toString()
        )

        return Result.success(
            ProjectItem(
                id = folder.uri.toString(),
                name = projectName,
                folderUri = folder.uri.toString(),
                glbUri = localGlbPath,
                source = source,
                glbSizeInBytes = glbFile.length(),
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    fun validateAndBuildProjectFromFile(
        context: Context,
        fileUri: Uri,
        source: ProjectSource
    ): Result<ProjectItem> {
        val file = DocumentFile.fromSingleUri(context, fileUri)
            ?: return Result.failure(IllegalArgumentException("Cannot access selected file."))

        if (!file.isFile) {
            return Result.failure(IllegalArgumentException("Please select a valid file."))
        }

        if (!isGlbFile(context, file.uri, file.name)) {
            return Result.failure(
                IllegalArgumentException("Selected file is not a valid GLB. Please choose a GLB file.")
            )
        }

        if (file.length() <= 0L) {
            return Result.failure(
                IllegalArgumentException("GLB file seems corrupted or empty.")
            )
        }

        if (file.length() > MAX_GLB_SIZE_BYTES) {
            return Result.failure(
                IllegalArgumentException("GLB exceeds 150MB supported limit.")
            )
        }

        val name = file.name.orEmpty()
        val projectName = name.substringBeforeLast(".").ifBlank { "Untitled Project" }
        val localGlbPath = cacheGlbLocally(
            context = context,
            glbUri = file.uri,
            projectId = file.uri.toString()
        )
        return Result.success(
            ProjectItem(
                id = file.uri.toString(),
                name = projectName,
                folderUri = file.uri.toString(),
                glbUri = localGlbPath,
                source = source,
                glbSizeInBytes = file.length(),
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    private fun isGlbFile(context: Context, fileUri: Uri, fileName: String?): Boolean {
        if (fileName?.endsWith(".glb", ignoreCase = true) == true) return true

        val mime = context.contentResolver.getType(fileUri).orEmpty()
        if (mime.equals("model/gltf-binary", ignoreCase = true)) return true

        // GLB binary header starts with ASCII "glTF".
        return runCatching {
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                val signature = ByteArray(4)
                val readCount = input.read(signature)
                readCount == 4 &&
                    signature[0] == 'g'.code.toByte() &&
                    signature[1] == 'l'.code.toByte() &&
                    signature[2] == 'T'.code.toByte() &&
                    signature[3] == 'F'.code.toByte()
            } == true
        }.getOrDefault(false)
    }

    private fun cacheGlbLocally(
        context: Context,
        glbUri: Uri,
        projectId: String
    ): String {
        val destination = File(context.filesDir, "glb_cache_${projectId.hashCode()}.glb")
        context.contentResolver.openInputStream(glbUri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Failed to read GLB data from selected source.")

        if (!destination.exists() || destination.length() <= 0L) {
            throw IllegalArgumentException("Imported GLB is empty after local copy.")
        }
        return destination.absolutePath
    }

    private fun findFirstGlb(root: DocumentFile): DocumentFile? {
        if (!root.isDirectory) return null
        root.listFiles().forEach { file ->
            if (file.isFile && file.name.orEmpty().endsWith(".glb", ignoreCase = true)) {
                return file
            }
            if (file.isDirectory) {
                val nested = findFirstGlb(file)
                if (nested != null) return nested
            }
        }
        return null
    }
}
