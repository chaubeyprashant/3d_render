package com.example.a3d_render.ui.viewer

import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import kotlin.math.max

/**
 * Node transform that centers a model at the origin, fits it to [ViewerCameraController.MODEL_UNITS],
 * turns Z-up survey exports upright, and stretches very flat terrain so relief is visible.
 *
 * Replaces SceneView's `scaleToUnits`/`centerOrigin`: its centerOrigin only offsets by a
 * fraction of the size and never subtracts the bounding-box center, so geo-referenced models
 * (hundreds of metres from their origin) render off-screen.
 */
data class ModelFraming(
    val position: Position,
    val rotation: Rotation,
    val scale: Scale,
    val isZUp: Boolean,
    val heightExaggeration: Float,
    /** Highest point in world Y for terrain-like models (the camera stays above it); null for objects. */
    val groundTopY: Float?
) {
    companion object {
        /**
         * @param halfExtent/center model-space bounding box.
         * @param extensionsUsed from the GLB; `CESIUM_RTC` marks geo-referenced, Z-up content.
         */
        fun compute(halfExtent: FloatArray, center: FloatArray, extensionsUsed: List<String>): ModelFraming {
            val (hx, hy, hz) = Triple(halfExtent[0], halfExtent[1], halfExtent[2])
            val isZUp = "CESIUM_RTC" in extensionsUsed || hz < Z_UP_THINNESS * minOf(hx, hy)

            val vertical = if (isZUp) hz else hy
            val horizontal = max(hx, if (isZUp) hy else hz).coerceAtLeast(MIN_EXTENT)
            val flatness = vertical / horizontal
            val exaggeration = if (flatness >= FLAT_TERRAIN_THRESHOLD) 1f else
                (FLAT_TERRAIN_TARGET_RATIO / flatness.coerceAtLeast(MIN_EXTENT))
                    .coerceIn(MIN_HEIGHT_EXAGGERATION, MAX_HEIGHT_EXAGGERATION)

            val maxHalf = maxOf(hx, hy, hz).coerceAtLeast(MIN_EXTENT)
            val s = ViewerCameraController.MODEL_UNITS / (2f * maxHalf)
            val sx = s
            val sy = if (isZUp) s else s * exaggeration
            val sz = if (isZUp) s * exaggeration else s

            // Node transform is T·R·S; choose T so the scaled, rotated center lands on the origin.
            // Z-up uses R = rotX(-90°): (x, y, z) → (x, z, -y), so +Z becomes up and +Y points north (-Z).
            val scaledCenter = floatArrayOf(center[0] * sx, center[1] * sy, center[2] * sz)
            val position = if (isZUp) {
                Position(-scaledCenter[0], -scaledCenter[2], scaledCenter[1])
            } else {
                Position(-scaledCenter[0], -scaledCenter[1], -scaledCenter[2])
            }
            val isTerrain = vertical < TERRAIN_RATIO * horizontal
            return ModelFraming(
                position = position,
                rotation = if (isZUp) Rotation(x = -90f) else Rotation(0f, 0f, 0f),
                scale = Scale(sx, sy, sz),
                isZUp = isZUp,
                heightExaggeration = exaggeration,
                groundTopY = if (isTerrain) vertical * s * exaggeration else null
            )
        }

        /** Z counts as the up axis when it is under half the smaller horizontal extent. */
        private const val Z_UP_THINNESS = 0.5f
        /** Models whose height is under half their width are treated as terrain. */
        private const val TERRAIN_RATIO = 0.5f
        private const val MIN_EXTENT = 0.001f
        private const val FLAT_TERRAIN_THRESHOLD = 0.08f
        private const val FLAT_TERRAIN_TARGET_RATIO = 0.18f
        private const val MIN_HEIGHT_EXAGGERATION = 4f
        private const val MAX_HEIGHT_EXAGGERATION = 24f
    }
}
