package com.example.a3d_render.ui.viewer

import android.view.MotionEvent
import io.github.sceneview.gesture.CameraGestureDetector
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One-finger double-tap (second tap) + hold/drag pans the camera.
 * Two-finger parallel pan is suppressed; pinch zoom still passes through.
 */
class ViewerDoubleTapPanHandler(
    private val manipulator: () -> CameraGestureDetector.CameraManipulator?,
) {
    private var isPanning = false
    private var lastUpTimeMs = 0L
    private var lastUpX = 0f
    private var lastUpY = 0f
    private var twoFingerSeparation = 0f

    fun onTouchEvent(event: MotionEvent, viewHeight: Int): Boolean {
        val manip = manipulator() ?: return false

        if (isPanning) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        manip.grabUpdate(touchX(event), touchY(event, viewHeight))
                        return true
                    }
                    stopPan(manip)
                }
                MotionEvent.ACTION_POINTER_DOWN -> stopPan(manip)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopPan(manip)
                    return true
                }
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount != 1) return false
                if (isSecondTap(event)) {
                    isPanning = true
                    manip.grabBegin(touchX(event), touchY(event, viewHeight), strafe = true)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (event.pointerCount == 1 && !isPanning) {
                    lastUpTimeMs = event.eventTime
                    lastUpX = event.x
                    lastUpY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    twoFingerSeparation = fingerSeparation(event, viewHeight)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    return blockTwoFingerPan(event, viewHeight)
                }
            }
        }

        return false
    }

    private fun isSecondTap(event: MotionEvent): Boolean {
        if (event.eventTime - lastUpTimeMs > DOUBLE_TAP_TIMEOUT_MS) return false
        val slop = DOUBLE_TAP_SLOP_PX
        return hypot(event.x - lastUpX, event.y - lastUpY) <= slop
    }

    /** Consume two-finger translation unless the gesture looks like pinch zoom. */
    private fun blockTwoFingerPan(event: MotionEvent, viewHeight: Int): Boolean {
        val separation = fingerSeparation(event, viewHeight)
        val separationDelta = abs(separation - twoFingerSeparation)
        twoFingerSeparation = separation
        return separationDelta <= PINCH_SEPARATION_THRESHOLD_PX
    }

    private fun stopPan(manip: CameraGestureDetector.CameraManipulator) {
        if (!isPanning) return
        manip.grabEnd()
        isPanning = false
    }

    private fun touchX(event: MotionEvent): Int = event.getX(0).toInt()

    private fun touchY(event: MotionEvent, viewHeight: Int): Int =
        (viewHeight - event.getY(0)).toInt()

    private fun fingerSeparation(event: MotionEvent, viewHeight: Int): Float {
        val x0 = event.getX(0)
        val y0 = viewHeight - event.getY(0)
        val x1 = event.getX(1)
        val y1 = viewHeight - event.getY(1)
        return hypot(x1 - x0, y1 - y0)
    }

    private companion object {
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val DOUBLE_TAP_SLOP_PX = 48f
        const val PINCH_SEPARATION_THRESHOLD_PX = 8f
    }
}
