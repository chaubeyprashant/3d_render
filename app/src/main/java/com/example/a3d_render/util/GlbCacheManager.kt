package com.example.a3d_render.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object GlbCacheManager {
    fun prepareForLargeModelLoad(context: Context, keepAbsolutePath: String? = null) {
        val cacheDir = context.cacheDir
        val filesDir = context.filesDir
        listOf(cacheDir, filesDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("glb_cache_") || file.name.startsWith("active_")) {
                    if (keepAbsolutePath == null || file.absolutePath != keepAbsolutePath) {
                        file.delete()
                    }
                }
            }
        }
    }

    fun copyToCache(input: InputStream, destination: File) {
        input.copyTo(FileOutputStream(destination))
    }
}
