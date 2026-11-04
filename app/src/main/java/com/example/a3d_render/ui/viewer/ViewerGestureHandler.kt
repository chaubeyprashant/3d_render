package com.example.a3d_render.ui.viewer

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln

/**
 * Google Earth-style touch navigation for [ViewerCameraController]. Consumes every event so
 * SceneView's built-in camera gestures stay out of the way.
 *
 * - One finger drag: orbit (3D) / pan (2D)
 * - Double-tap: zoom in · double-tap and hold, then drag: pan
 * - Pinch: zoom · two-finger twist: rotate heading · two fingers dragged up/down: tilt (3D)
 */
class ViewerGestureHandler(
    context: Context,
    private val controller: ViewerCameraController
) {
    private enum class Mode { NONE, DRAG, DOUBLE_TAP_DRAG, TWO_FINGER }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val doubleTapSlop = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragStarted = false
    private var lastTapUpTime = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f

    private var previousSpan = 0f
    private var previousAngle = 0f
    private var previousMidY = 0f
    private var accumulatedZoomLog = 0f
    private var accumulatedRotation = 0f
    private var accumulatedTiltPx = 0f
    private var zoomActive = false
    private var rotateActive = false
    private var tiltActive = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragStarted = false
                val isSecondTap = event.eventTime - lastTapUpTime <= doubleTapTimeout &&
                    hypot(event.x - lastTapUpX, event.y - lastTapUpY) <= doubleTapSlop
                mode = if (isSecondTap) Mode.DOUBLE_TAP_DRAG else Mode.DRAG
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) beginTwoFinger(event)

            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.DRAG, Mode.DOUBLE_TAP_DRAG -> if (event.pointerCount == 1) handleDrag(event)
                Mode.TWO_FINGER -> if (event.pointerCount >= 2) handleTwoFinger(event)
                Mode.NONE -> Unit
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Re-baseline on the remaining fingers; after two-finger input, wait for a fresh touch.
                if (event.pointerCount > 2) beginTwoFinger(event, excludeIndex = event.actionIndex) else mode = Mode.NONE
                lastTapUpTime = 0L
            }

            MotionEvent.ACTION_UP -> {
                when {
                    mode == Mode.DOUBLE_TAP_DRAG && !dragStarted -> {
                        controller.zoomIn()
                        lastTapUpTime = 0L
                    }
                    mode == Mode.DRAG && !dragStarted -> {
                        lastTapUpTime = event.eventTime
                        lastTapUpX = event.x
                        lastTapUpY = event.y
                    }
                    else -> lastTapUpTime = 0L
                }
                mode = Mode.NONE
            }

            MotionEvent.ACTION_CANCEL -> mode = Mode.NONE
        }
        return true
    }

    private fun handleDrag(event: MotionEvent) {
        if (!dragStarted) {
            if (hypot(event.x - downX, event.y - downY) < touchSlop) return
            // Start from here so crossing the slop doesn't cause a jump.
            dragStarted = true
            lastX = event.x
            lastY = event.y
            return
        }
        val dx = event.x - lastX
        val dy = event.y - lastY
        lastX = event.x
        lastY = event.y
        if (mode == Mode.DOUBLE_TAP_DRAG) controller.pan(dx, dy) else controller.orbit(dx, dy)
    }

    private fun beginTwoFinger(event: MotionEvent, excludeIndex: Int = -1) {
        val (first, second) = twoPointers(event, excludeIndex) ?: return
        mode = Mode.TWO_FINGER
        previousSpan = span(event, first, second)
        previousAngle = angle(event, first, second)
        previousMidY = (event.getY(first) + event.getY(second)) / 2f
        accumulatedZoomLog = 0f
        accumulatedRotation = 0f
        accumulatedTiltPx = 0f
        zoomActive = false
        rotateActive = false
        tiltActive = false
    }

    private fun handleTwoFinger(event: MotionEvent) {
        val (first, second) = twoPointers(event, -1) ?: return
        val span = span(event, first, second)
        val angle = angle(event, first, second)
        val midY = (event.getY(first) + event.getY(second)) / 2f

        val zoomFactor = if (span > 1f && previousSpan > 1f) previousSpan / span else 1f
        val rotation = ViewerCameraController.wrapAngle(angle - previousAngle)
        val tiltPx = midY - previousMidY
        previousSpan = span
        previousAngle = angle
        previousMidY = midY

        // Lock in gestures only after a clear intent so a pinch doesn't also spin or tilt.
        if (!tiltActive) {
            accumulatedZoomLog += ln(zoomFactor)
            accumulatedRotation += rotation
            if (!zoomActive && abs(accumulatedZoomLog) > ZOOM_LOCK_LOG) zoomActive = true
            if (!rotateActive && abs(accumulatedRotation) > ROTATE_LOCK_RADIANS) rotateActive = true
        }
        if (!zoomActive && !rotateActive && controller.mode == ViewMode.THREE_D) {
            accumulatedTiltPx += tiltPx
            if (abs(accumulatedTiltPx) > TILT_LOCK_PX) tiltActive = true
        }

        when {
            tiltActive -> controller.tilt(tiltPx * TILT_RADIANS_PER_PX)
            else -> {
                if (zoomActive) controller.zoomBy(zoomFactor)
                if (rotateActive) controller.rotateHeading(rotation)
            }
        }
    }

    private fun twoPointers(event: MotionEvent, excludeIndex: Int): Pair<Int, Int>? {
        val indices = (0 until event.pointerCount).filter { it != excludeIndex }
        return if (indices.size >= 2) indices[0] to indices[1] else null
    }

    private fun span(event: MotionEvent, a: Int, b: Int): Float =
        hypot(event.getX(b) - event.getX(a), event.getY(b) - event.getY(a))

    private fun angle(event: MotionEvent, a: Int, b: Int): Float =
        atan2(event.getY(b) - event.getY(a), event.getX(b) - event.getX(a))

    private companion object {
        val ZOOM_LOCK_LOG = ln(1.08f)
        const val ROTATE_LOCK_RADIANS = (12 * PI / 180).toFloat()
        const val TILT_LOCK_PX = 28f
        const val TILT_RADIANS_PER_PX = 0.004f
    }
}
