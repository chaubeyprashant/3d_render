package com.example.a3d_render.domain.project

object GlbSizePolicy {
    /** Size the viewer is specified and tuned for (Android 10, 4 GB RAM). Larger files need confirmation. */
    const val RECOMMENDED_MAX_BYTES = 150L * 1024L * 1024L

    private const val MIN_HARD_LIMIT_BYTES = 300L * 1024L * 1024L
    private const val MAX_HARD_LIMIT_BYTES = 1536L * 1024L * 1024L

    /**
     * Largest GLB the device may attempt. A model needs several times its file size in native and
     * GPU memory while loading, so the ceiling scales with installed RAM (~380 MB on a 4 GB device).
     */
    fun hardLimitBytes(totalRamBytes: Long): Long =
        (totalRamBytes / 10).coerceIn(MIN_HARD_LIMIT_BYTES, MAX_HARD_LIMIT_BYTES)
}
