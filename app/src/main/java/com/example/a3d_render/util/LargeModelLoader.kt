package com.example.a3d_render.util

import android.util.Log
import com.example.a3d_render.domain.project.GlbInspector
import com.example.a3d_render.domain.project.ProjectImporter
import com.google.gson.JsonParser
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads GLBs of 150 MB+ without a heap-sized copy: the file is memory-mapped (off the Java
 * heap, pageable by the OS) and handed to Filament directly. `ModelLoader.loadModelInstance`
 * would read the whole file into a byte array, and treats bare file paths as asset names.
 */
object LargeModelLoader {

    suspend fun load(modelLoader: ModelLoader, glbFile: File): ModelInstance {
        val baseDir = glbFile.parentFile ?: throw IllegalArgumentException("Model has no parent directory")
        val (glbBuffer, manifest) = withContext(Dispatchers.IO) {
            mapFile(glbFile) to readManifest(baseDir)
        }
        return withContext(Dispatchers.Main) {
            modelLoader.createModelInstance(glbBuffer) { uri -> resolveResource(baseDir, manifest, uri) }
        }
    }

    private fun mapFile(file: File): ByteBuffer =
        RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }

    private fun readManifest(baseDir: File): Map<String, String> {
        val file = File(baseDir, ProjectImporter.RESOURCE_MANIFEST)
        if (!file.isFile) return emptyMap()
        return runCatching {
            JsonParser.parseString(file.readText()).asJsonObject.entrySet()
                .associate { (uri, path) -> uri to path.asString }
        }.getOrElse {
            Log.w(TAG, "Unreadable resource manifest", it)
            emptyMap()
        }
    }

    private fun resolveResource(baseDir: File, manifest: Map<String, String>, uri: String): Buffer? {
        val decoded = GlbInspector.percentDecode(uri)
        val relativePath = manifest[decoded] ?: manifest[uri] ?: decoded
        val file = File(baseDir, relativePath).canonicalFile
        val root = baseDir.canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile) {
            Log.w(TAG, "Missing model resource: $uri")
            return null
        }
        return mapFile(file)
    }

    private const val TAG = "LargeModelLoader"
}
