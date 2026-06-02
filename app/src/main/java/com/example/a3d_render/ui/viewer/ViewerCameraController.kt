package com.example.a3d_render.ui.viewer

import com.google.android.filament.utils.Manipulator
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.gesture.orbitHomePosition
import io.github.sceneview.gesture.targetPosition
import io.github.sceneview.math.Position
import io.github.sceneview.math.Transform
import io.github.sceneview.math.slerp

/**
 * Builds a stable orbit camera for the viewer. The loaded model is normalized to [MODEL_UNITS]
 * via [io.github.sceneview.node.ModelNode]'s `scaleToUnits`, so the camera pose stays constant
 * regardless of source asset size.
 *
 * Gestures (handled by SceneView + Filament ORBIT manipulator):
 * - One finger drag → orbit (rotate around model)
 * - Double-tap and hold + drag → pan
 * - Two finger pinch → zoom
 */
object ViewerCameraController {
    const val MODEL_UNITS = 2.0f

    fun buildManipulator(): CameraGestureDetector.CameraManipulator {
        val baseManipulator = Manipulator.Builder()
            .targetPosition(FRAMED_TARGET)
            .orbitHomePosition(FRAMED_HOME)
            .orbitSpeed(ORBIT_SPEED_X, ORBIT_SPEED_Y)
            .zoomSpeed(ZOOM_SPEED)
            .panning(true)
            .build(Manipulator.Mode.ORBIT)

        val defaultManipulator = CameraGestureDetector.DefaultCameraManipulator(
            manipulator = baseManipulator,
            pinchZoomSpeed = PINCH_ZOOM_SPEED,
            pinchZoomDamping = PINCH_ZOOM_DAMPING
        )
        return SmoothCameraManipulator(
            inner = defaultManipulator,
            orbitDragScale = ORBIT_DRAG_SCALE,
            panDragScale = PAN_DRAG_SCALE,
            orbitTouchSmoothing = ORBIT_TOUCH_SMOOTHING,
            panTouchSmoothing = PAN_TOUCH_SMOOTHING,
            orbitActiveSmoothSpeed = ORBIT_ACTIVE_SMOOTH_SPEED,
            panActiveSmoothSpeed = PAN_ACTIVE_SMOOTH_SPEED,
            idleSmoothSpeed = IDLE_SMOOTH_SPEED
        )
    }

    val FRAMED_TARGET = Position(x = 0f, y = 0f, z = 0f)
    val FRAMED_HOME = Position(x = 1.1f, y = 0.85f, z = 2.6f)

    private const val ORBIT_SPEED_X = 0.00090f
    private const val ORBIT_SPEED_Y = 0.00070f
    private const val ZOOM_SPEED = 0.058f
    private const val PINCH_ZOOM_SPEED = 1f / 28f
    private const val PINCH_ZOOM_DAMPING = 0.86f

    /** One-finger orbit; raised slightly from 0.22 for a snappier rotate. */
    private const val ORBIT_DRAG_SCALE = 1.4f
    /** Two-finger pan — higher = less finger travel for the same shift. */
    private const val PAN_DRAG_SCALE = 1.48f

    /** EMA on prior touch delta — higher = smoother orbit/pan (zoom unchanged). */
    private const val ORBIT_TOUCH_SMOOTHING = 0.84f
    private const val PAN_TOUCH_SMOOTHING = 0.62f
    private const val ORBIT_ACTIVE_SMOOTH_SPEED = 5f
    private const val PAN_ACTIVE_SMOOTH_SPEED = 11f
    /** Soft glide after orbit/pan ends. */
    private const val IDLE_SMOOTH_SPEED = 3f
}

/**
 * Fractional touch deltas + EMA input filtering + slerp-smoothed orbit/pan.
 * Pinch zoom follows the inner manipulator directly (no extra lag).
 */
private class SmoothCameraManipulator(
    private val inner: CameraGestureDetector.CameraManipulator,
    private val orbitDragScale: Float,
    private val panDragScale: Float,
    private val orbitTouchSmoothing: Float,
    private val panTouchSmoothing: Float,
    private val orbitActiveSmoothSpeed: Float,
    private val panActiveSmoothSpeed: Float,
    private val idleSmoothSpeed: Float,
) : CameraGestureDetector.CameraManipulator {

    private var proxyX = 0f
    private var proxyY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var smoothedDeltaX = 0f
    private var smoothedDeltaY = 0f
    private var isPanning = false
    private var isGrabbing = false
    private var isScrolling = false
    private var smoothedTransform: Transform? = null
    private var lastDeltaSeconds: Float = 0f

    override fun setViewport(width: Int, height: Int) = inner.setViewport(width, height)

    override fun getTransform(): Transform =
        smoothedTransform ?: inner.getTransform()

    override fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        isPanning = strafe
        isGrabbing = true
        lastTouchX = x.toFloat()
        lastTouchY = y.toFloat()
        proxyX = lastTouchX
        proxyY = lastTouchY
        smoothedDeltaX = 0f
        smoothedDeltaY = 0f
        inner.grabBegin(x, y, strafe)
        smoothedTransform = inner.getTransform()
    }

    override fun grabUpdate(x: Int, y: Int) {
        val dragScale = if (isPanning) panDragScale else orbitDragScale
        val xf = x.toFloat()
        val yf = y.toFloat()
        val rawDeltaX = (xf - lastTouchX) * dragScale
        val rawDeltaY = (yf - lastTouchY) * dragScale
        lastTouchX = xf
        lastTouchY = yf

        val touchSmoothing = if (isPanning) panTouchSmoothing else orbitTouchSmoothing
        val blend = 1f - touchSmoothing
        smoothedDeltaX = smoothedDeltaX * touchSmoothing + rawDeltaX * blend
        smoothedDeltaY = smoothedDeltaY * touchSmoothing + rawDeltaY * blend

        proxyX += smoothedDeltaX
        proxyY += smoothedDeltaY
        inner.grabUpdate(proxyX.toInt(), proxyY.toInt())
    }

    override fun grabEnd() {
        isPanning = false
        isGrabbing = false
        smoothedDeltaX = 0f
        smoothedDeltaY = 0f
        inner.grabEnd()
    }

    override fun scrollBegin(x: Int, y: Int, separation: Float) {
        isScrolling = true
        inner.scrollBegin(x, y, separation)
    }

    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) {
        inner.scrollUpdate(x, y, prevSeparation, currSeparation)
    }

    override fun scrollEnd() {
        isScrolling = false
        inner.scrollEnd()
    }

    override fun update(deltaTime: Float) {
        lastDeltaSeconds = deltaTime.coerceAtMost(0.05f)
        inner.update(deltaTime)
        val target = inner.getTransform()
        val current = smoothedTransform
        smoothedTransform = when {
            current == null -> target
            // Pinch zoom: no extra slerp — keep current zoom feel.
            isScrolling -> target
            else -> {
                val speed = when {
                    isGrabbing && isPanning -> panActiveSmoothSpeed
                    isGrabbing -> orbitActiveSmoothSpeed
                    else -> idleSmoothSpeed
                }
                slerp(
                    start = current,
                    end = target,
                    deltaSeconds = lastDeltaSeconds.toDouble(),
                    speed = speed
                )
            }
        }
    }
}
