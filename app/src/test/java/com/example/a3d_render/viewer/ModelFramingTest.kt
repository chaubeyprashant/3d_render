package com.example.a3d_render.viewer

import com.example.a3d_render.ui.viewer.ModelFraming
import com.example.a3d_render.ui.viewer.ViewerCameraController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFramingTest {

    /** Applies the node transform T·R·S (R is identity or rotX(-90°)) to a model-space point. */
    private fun ModelFraming.apply(p: FloatArray): FloatArray {
        val sx = p[0] * scale.x; val sy = p[1] * scale.y; val sz = p[2] * scale.z
        val r = if (isZUp) floatArrayOf(sx, sz, -sy) else floatArrayOf(sx, sy, sz)
        return floatArrayOf(r[0] + position.x, r[1] + position.y, r[2] + position.z)
    }

    // Bounds of the geo-referenced photogrammetry sample (Z-up, CESIUM_RTC, ~445 m from origin).
    private val surveyHalf = floatArrayOf(157.90454f, 181.72397f, 72.33536f)
    private val surveyCenter = floatArrayOf(-11.665993f, -2.2966614f, 445.01486f)

    @Test
    fun geoReferencedModelIsCenteredUprightAndFitted() {
        val framing = ModelFraming.compute(surveyHalf, surveyCenter, listOf("CESIUM_RTC", "KHR_draco_mesh_compression"))
        assertTrue(framing.isZUp)
        assertEquals(1f, framing.heightExaggeration)

        val center = framing.apply(surveyCenter)
        center.forEach { assertEquals(0f, it, 1e-4f) }

        // Highest point (max Z) ends up above the center: Z became up.
        val top = framing.apply(floatArrayOf(surveyCenter[0], surveyCenter[1], surveyCenter[2] + surveyHalf[2]))
        assertTrue(top[1] > 0f)
        // North (+Y) points away from the default camera (-Z).
        val north = framing.apply(floatArrayOf(surveyCenter[0], surveyCenter[1] + surveyHalf[1], surveyCenter[2]))
        assertTrue(north[2] < 0f)

        // Terrain: the camera floor is the highest point (half the elevation span, scaled).
        assertEquals(surveyHalf[2] * framing.scale.z, framing.groundTopY!!, 1e-5f)

        // Largest extent fits the viewer's model size.
        assertEquals(ViewerCameraController.MODEL_UNITS / 2f, surveyHalf[1] * framing.scale.y, 1e-4f)
    }

    @Test
    fun zUpIsDetectedFromShapeWithoutExtension() {
        assertTrue(ModelFraming.compute(surveyHalf, surveyCenter, emptyList()).isZUp)
    }

    @Test
    fun flatYUpTerrainIsExaggeratedVertically() {
        val framing = ModelFraming.compute(floatArrayOf(50f, 1f, 50f), floatArrayOf(0f, 3f, 0f), emptyList())
        assertFalse(framing.isZUp)
        assertTrue(framing.heightExaggeration > 1f)
        assertEquals(framing.scale.x * framing.heightExaggeration, framing.scale.y, 1e-5f)
        framing.apply(floatArrayOf(0f, 3f, 0f)).forEach { assertEquals(0f, it, 1e-4f) }
    }

    @Test
    fun ordinaryObjectKeepsItsOrientation() {
        val framing = ModelFraming.compute(floatArrayOf(1f, 2f, 1f), floatArrayOf(5f, 5f, 5f), emptyList())
        assertFalse(framing.isZUp)
        assertEquals(1f, framing.heightExaggeration)
        assertEquals(null, framing.groundTopY)
    }
}
