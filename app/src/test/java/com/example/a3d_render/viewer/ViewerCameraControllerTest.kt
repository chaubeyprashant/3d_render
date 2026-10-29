package com.example.a3d_render.viewer

import com.example.a3d_render.ui.viewer.NavDirection
import com.example.a3d_render.ui.viewer.ViewMode
import com.example.a3d_render.ui.viewer.ViewerCameraController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ViewerCameraControllerTest {

    private fun ViewerCameraController.settle() = repeat(200) { update(1f / 60f) }

    @Test
    fun zoomFractionRoundTrips() {
        listOf(0f, 0.25f, 0.5f, 1f).forEach { fraction ->
            val distance = ViewerCameraController.distanceFor(fraction)
            assertEquals(fraction, ViewerCameraController.fractionFor(distance), 1e-4f)
        }
    }

    @Test
    fun zoomButtonsMoveTheZoomBarAndClamp() {
        val camera = ViewerCameraController()
        val start = camera.zoomFraction
        camera.zoomIn()
        assertTrue(camera.zoomFraction > start)
        repeat(50) { camera.zoomIn() }
        assertEquals(1f, camera.zoomFraction, 1e-4f)
        repeat(100) { camera.zoomOut() }
        assertEquals(0f, camera.zoomFraction, 1e-4f)
    }

    @Test
    fun zoomBarDrivesCameraDistance() {
        val camera = ViewerCameraController()
        camera.zoomTo(1f)
        camera.settle()
        assertEquals(ViewerCameraController.distanceFor(1f), camera.renderedDistance, 1e-3f)
    }

    @Test
    fun recenterRestoresHomeView() {
        val camera = ViewerCameraController()
        camera.setViewport(1080, 1920)
        camera.settle()
        val home = camera.getTransform()
        camera.orbit(400f, 150f)
        camera.pan(200f, -80f)
        camera.zoomIn()
        camera.nudge(NavDirection.LEFT, 2f)
        camera.settle()
        camera.recenter()
        camera.settle()
        val after = camera.getTransform()
        for (column in 0..3) for (row in 0..3) {
            assertEquals(home[column][row], after[column][row], 1e-3f)
        }
    }

    @Test
    fun twoDModeBecomesOrthographicTopDown() {
        val camera = ViewerCameraController()
        camera.settle()
        assertFalse(camera.isOrthographic)
        camera.switchMode(ViewMode.TWO_D)
        camera.settle()
        assertTrue(camera.isOrthographic)
        // Looking straight down: camera forward (-Z column) points along -Y.
        val forwardY = -camera.getTransform().z.y
        assertEquals(-1f, forwardY, 1e-3f)
        camera.switchMode(ViewMode.THREE_D)
        camera.settle()
        assertFalse(camera.isOrthographic)
    }

    @Test
    fun recenterIn2DIsNorthUp() {
        val camera = ViewerCameraController()
        camera.switchMode(ViewMode.TWO_D)
        camera.rotateHeading(1.2f)
        camera.settle()
        camera.recenter()
        camera.settle()
        // Screen-up (camera +Y column) points to world -Z (north) when looking straight down.
        val up = camera.getTransform().y
        assertEquals(0f, up.x, 1e-3f)
        assertEquals(-1f, up.z, 1e-3f)
    }

    @Test
    fun cameraStaysAboveTerrainWhenZoomingAndTilting() {
        val camera = ViewerCameraController()
        camera.setViewport(1080, 1920)
        camera.setGroundClearance(0.4f)
        repeat(40) { camera.zoomIn() }
        camera.tilt(-2f)
        repeat(3) { camera.nudge(NavDirection.UP, 1f) }
        camera.settle()
        val eyeY = camera.getTransform().w.y
        assertTrue("eye y=$eyeY is below terrain top", eyeY >= 0.4f)
        // The zoom bar reflects the closest distance actually allowed.
        assertTrue(camera.zoomFraction < 1f)
    }

    @Test
    fun objectsCanStillBeOrbitedFreely() {
        val camera = ViewerCameraController()
        camera.setGroundClearance(null)
        repeat(40) { camera.zoomIn() }
        camera.settle()
        assertEquals(1f, camera.zoomFraction, 1e-4f)
    }

    @Test
    fun wrapAngleStaysInRange() {
        assertEquals(0f, ViewerCameraController.wrapAngle((4 * PI).toFloat()), 1e-4f)
        assertEquals((PI / 2).toFloat(), ViewerCameraController.wrapAngle((2.5 * PI).toFloat()), 1e-4f)
        assertEquals((-PI / 2).toFloat(), ViewerCameraController.wrapAngle((-2.5 * PI).toFloat()), 1e-4f)
    }
}
