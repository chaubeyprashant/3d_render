package com.example.a3d_render.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.github.sceneview.RenderQuality

/**
 * Runtime checks for low-end phones (Android 10 class, ~4 GB RAM).
 */
object DeviceCapabilities {

    /** Android 10 (API 29) — minimum supported release. */
    const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.Q

    private const val LOW_RAM_TOTAL_BYTES = 4_500L * 1024L * 1024L

    fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (activityManager.isLowRamDevice) return true

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem <= LOW_RAM_TOTAL_BYTES
    }

    fun viewerRenderQuality(context: Context): RenderQuality =
        if (isLowRamDevice(context)) RenderQuality.Performance else RenderQuality.Default

    /** Downsample grid bitmap on constrained devices to save heap. */
    fun gridBitmapSampleSize(context: Context): Int =
        if (isLowRamDevice(context)) 2 else 1

    fun shouldAutoAnimateModel(context: Context): Boolean = !isLowRamDevice(context)
}
