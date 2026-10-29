package com.example.a3d_render.util

import java.util.Locale

object ByteFormat {
    fun format(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024.0 -> String.format(Locale.US, "%.1f GB", mb / 1024.0)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
        }
    }
}
