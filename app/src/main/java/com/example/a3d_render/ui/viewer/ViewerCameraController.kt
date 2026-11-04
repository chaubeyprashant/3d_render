package com.example.a3d_render.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.lookAt
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Transform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class ViewMode { THREE_D, TWO_D }

enum class NavDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Google Earth-style camera around the loaded model, which [io.github.sceneview.node.ModelNode]
 * normalizes to [MODEL_UNITS] so the framing is independent of source asset size.
 *
 * Gestures and buttons edit a target pose (heading, tilt, distance, pivot); every frame the
 * rendered pose eases toward it, which keeps touch input smooth and makes Recenter, zoom-bar and
 * mode changes animate.
 *
 * - 3D: orbit around the model, tilt between near-horizon and near-top-down.
 * - 2D: straight top-down view with an orthographic projection (see [isOrthographic]); drags pan.
 */
class ViewerCameraController : CameraGestureDetector.CameraManipulator {

    private var targetYaw = HOME_YAW
    private var targetPitch = HOME_PITCH
    private var targetDistance = HOME_DISTANCE
    private var targetPivot = Float3()

    private var yaw = targetYaw
    private var pitch = targetPitch
    private var distance = targetDistance
    private var pivot = Float3()

    private var viewportHeightPx = 1

    /** Lowest eye height (world Y) allowed in 3D; see [setGroundClearance]. */
    private var minEyeY = Float.NEGATIVE_INFINITY
    private var lastGrabX = 0
    private var lastGrabY = 0
    private var grabIsPan = false

    var mode by mutableStateOf(ViewMode.THREE_D)
        private set

    /** 0 = fully zoomed out, 1 = fully zoomed in (log scale), for the zoom bar. */
    var zoomFraction by mutableFloatStateOf(fractionFor(HOME_DISTANCE))
        private set

    /** Camera-to-pivot distance as currently rendered. */
    val renderedDistance: Float get() = distance

    /** True once the 2D transition has reached top-down; the viewer then switches to ORTHO. */
    val isOrthographic: Boolean
        get() = mode == ViewMode.TWO_D && abs(pitch - TOP_DOWN_PITCH) < ORTHO_SWITCH_RADIANS

    // ── Gesture input (screen pixels, y pointing down) ─────────────────────────────────────────

    /** One-finger drag: orbits in 3D, pans in 2D. */
    fun orbit(dxPx: Float, dyPx: Float) {
        if (mode == ViewMode.TWO_D) {
            pan(dxPx, dyPx)
            return
        }
        targetYaw -= dxPx * ORBIT_RADIANS_PER_PX_X
        targetPitch = (targetPitch + dyPx * ORBIT_RADIANS_PER_PX_Y).coerceIn(MIN_PITCH, MAX_PITCH)
        keepAboveGround()
    }

    /** Moves the pivot so the surface under the finger follows it. */
    fun pan(dxPx: Float, dyPx: Float) {
        val worldPerPx = 2f * targetDistance * TAN_HALF_FOV / viewportHeightPx
        // Ground-plane basis: screen-right and screen-up (away from the viewer).
        val rightX = cos(targetYaw)
        val rightZ = -sin(targetYaw)
        val forwardX = -sin(targetYaw)
        val forwardZ = -cos(targetYaw)
        var x = targetPivot.x + (-dxPx * rightX + dyPx * forwardX) * worldPerPx
        var z = targetPivot.z + (-dxPx * rightZ + dyPx * forwardZ) * worldPerPx
        val horizontal = sqrt(x * x + z * z)
        if (horizontal > MAX_PAN_RADIUS) {
            x = x / horizontal * MAX_PAN_RADIUS
            z = z / horizontal * MAX_PAN_RADIUS
        }
        targetPivot = Float3(x, targetPivot.y, z)
    }

    /** [factor] < 1 zooms in. */
    fun zoomBy(factor: Float) {
        if (factor.isNaN() || factor <= 0f) return
        setTargetDistance(targetDistance * factor)
    }

    /** Two-finger twist; positive = content rotates clockwise on screen. */
    fun rotateHeading(radians: Float) {
        targetYaw += radians
    }

    /** Two-finger vertical drag (3D only); negative tilts toward the horizon. */
    fun tilt(radians: Float) {
        if (mode != ViewMode.THREE_D) return
        targetPitch = (targetPitch + radians).coerceIn(MIN_PITCH, MAX_PITCH)
        keepAboveGround()
    }

    // ── On-screen controls ─────────────────────────────────────────────────────────────────────

    fun zoomIn() = zoomBy(ZOOM_STEP)

    fun zoomOut() = zoomBy(1f / ZOOM_STEP)

    fun zoomTo(fraction: Float) = setTargetDistance(distanceFor(fraction.coerceIn(0f, 1f)))

    /**
     * Directional buttons, applied continuously while held: trackball-style rotation in 3D,
     * map panning (up = away from the viewer) in 2D.
     */
    fun nudge(direction: NavDirection, seconds: Float) {
        if (mode == ViewMode.THREE_D) {
            val step = NUDGE_RADIANS_PER_SECOND * seconds
            when (direction) {
                NavDirection.LEFT -> targetYaw += step
                NavDirection.RIGHT -> targetYaw -= step
                NavDirection.UP -> targetPitch = (targetPitch - step).coerceIn(MIN_PITCH, MAX_PITCH)
                NavDirection.DOWN -> targetPitch = (targetPitch + step).coerceIn(MIN_PITCH, MAX_PITCH)
            }
            keepAboveGround()
        } else {
            val stepPx = viewportHeightPx * NUDGE_VIEWPORTS_PER_SECOND * seconds
            when (direction) {
                NavDirection.LEFT -> pan(stepPx, 0f)
                NavDirection.RIGHT -> pan(-stepPx, 0f)
                NavDirection.UP -> pan(0f, stepPx)
                NavDirection.DOWN -> pan(0f, -stepPx)
            }
        }
    }

    /** Animates back to the home view for the current mode (north-up, whole model framed). */
    fun recenter() {
        val homeYaw = if (mode == ViewMode.TWO_D) 0f else HOME_YAW
        yaw = homeYaw + wrapAngle(yaw - homeYaw)
        targetYaw = homeYaw
        targetPivot = Float3()
        if (mode == ViewMode.THREE_D) {
            targetPitch = HOME_PITCH
            setTargetDistance(HOME_DISTANCE)
            keepAboveGround()
        } else {
            targetPitch = TOP_DOWN_PITCH
            setTargetDistance(HOME_DISTANCE_2D)
        }
    }

    fun switchMode(newMode: ViewMode) {
        if (newMode == mode) return
        mode = newMode
        targetPitch = if (newMode == ViewMode.TWO_D) TOP_DOWN_PITCH else HOME_PITCH
        keepAboveGround()
    }

    /**
     * For terrain, keeps the camera above the model's highest point ([modelTopY], world units;
     * null = no limit). The orbit centre is the model's mid-height, which on hilly terrain lies
     * inside the hills, so without this, zooming in or tilting down puts the camera underground.
     */
    fun setGroundClearance(modelTopY: Float?) {
        minEyeY = modelTopY?.plus(GROUND_CLEARANCE) ?: Float.NEGATIVE_INFINITY
        keepAboveGround()
    }

    // ── CameraManipulator (SceneView frame loop) ───────────────────────────────────────────────

    override fun setViewport(width: Int, height: Int) {
        viewportHeightPx = height.coerceAtLeast(1)
    }

    override fun getTransform(): Transform {
        // In ORTHO the eye distance does not affect framing; keep it high so tall geometry is
        // never behind the near plane when zoomed in.
        val eyeDistance = if (isOrthographic) max(distance, ORTHO_EYE_DISTANCE) else distance
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        var eyeY = pivot.y + sinPitch * eyeDistance
        // Targets already respect the ground limit; this also covers the eased path toward them.
        if (mode == ViewMode.THREE_D && eyeY < minEyeY) eyeY = minEyeY
        val eye = Float3(
            pivot.x + cosPitch * sin(yaw) * eyeDistance,
            eyeY,
            pivot.z + cosPitch * cos(yaw) * eyeDistance
        )
        // up = right × forward, with right = (cos yaw, 0, -sin yaw) and forward pointing from
        // the eye to the pivot. Well-defined even when looking straight down.
        val up = Float3(-sinPitch * sin(yaw), cosPitch, -sinPitch * cos(yaw))
        return lookAt(eye = eye, target = pivot, up = up)
    }

    // SceneView's own gesture detector is bypassed by ViewerGestureHandler; these keep the
    // manipulator usable if it is ever driven directly. Grab y is bottom-up (GL convention).
    override fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        lastGrabX = x
        lastGrabY = y
        grabIsPan = strafe
    }

    override fun grabUpdate(x: Int, y: Int) {
        val dx = (x - lastGrabX).toFloat()
        val dy = -(y - lastGrabY).toFloat()
        lastGrabX = x
        lastGrabY = y
        if (grabIsPan) pan(dx, dy) else orbit(dx, dy)
    }

    override fun grabEnd() = Unit

    override fun scrollBegin(x: Int, y: Int, separation: Float) = Unit

    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) {
        if (prevSeparation > 0f && currSeparation > 0f) zoomBy(prevSeparation / currSeparation)
    }

    override fun scrollEnd() = Unit

    override fun update(deltaTime: Float) {
        val blend = 1f - exp(-SMOOTHING_PER_SECOND * deltaTime.coerceIn(0f, MAX_FRAME_SECONDS))
        yaw += (targetYaw - yaw) * blend
        pitch += (targetPitch - pitch) * blend
        distance += (targetDistance - distance) * blend
        pivot = Float3(
            pivot.x + (targetPivot.x - pivot.x) * blend,
            pivot.y + (targetPivot.y - pivot.y) * blend,
            pivot.z + (targetPivot.z - pivot.z) * blend
        )
    }

    private fun setTargetDistance(value: Float) {
        targetDistance = value.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
        zoomFraction = fractionFor(targetDistance)
        keepAboveGround()
    }

    /** Raises the target tilt (and, near the ground, the distance) so the eye stays above [minEyeY]. */
    private fun keepAboveGround() {
        if (mode != ViewMode.THREE_D || minEyeY == Float.NEGATIVE_INFINITY) return
        val needed = minEyeY - targetPivot.y
        if (needed <= 0f || targetDistance * sin(targetPitch) >= needed) return
        val maxSin = sin(MAX_PITCH)
        if (needed / targetDistance <= maxSin) {
            targetPitch = asin(needed / targetDistance)
        } else {
            targetPitch = MAX_PITCH
            targetDistance = (needed / maxSin).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
            zoomFraction = fractionFor(targetDistance)
        }
    }

    companion object {
        const val MODEL_UNITS = 2.0f

        /** tan(vfov / 2) of SceneView's default 28 mm lens on Filament's 24 mm sensor. */
        const val TAN_HALF_FOV = 12f / 28f

        private const val HOME_YAW = 0.40f // ≈ 23°
        private const val HOME_PITCH = 0.29f // ≈ 17°
        private const val HOME_DISTANCE = 2.95f
        private const val HOME_DISTANCE_2D = 3.2f
        private const val TOP_DOWN_PITCH = (PI / 2).toFloat()
        private const val MIN_PITCH = 0.09f // ≈ 5°, just above the horizon
        private const val MAX_PITCH = 1.55f // ≈ 89°
        private const val MIN_DISTANCE = 0.4f
        private const val MAX_DISTANCE = 12f
        private const val MAX_PAN_RADIUS = 3f
        private const val ORTHO_SWITCH_RADIANS = 0.02f
        private const val ORTHO_EYE_DISTANCE = 20f
        /** Gap kept between the camera and the terrain's highest point. */
        private const val GROUND_CLEARANCE = 0.04f

        private const val ORBIT_RADIANS_PER_PX_X = 0.00126f
        private const val ORBIT_RADIANS_PER_PX_Y = 0.00098f
        private const val ZOOM_STEP = 0.8f
        private const val NUDGE_RADIANS_PER_SECOND = 1.1f // ≈ 63°/s
        private const val NUDGE_VIEWPORTS_PER_SECOND = 0.5f
        private const val SMOOTHING_PER_SECOND = 10f
        private const val MAX_FRAME_SECONDS = 0.1f

        private val LN_MIN_DISTANCE = ln(MIN_DISTANCE)
        private val LN_MAX_DISTANCE = ln(MAX_DISTANCE)

        fun fractionFor(distance: Float): Float =
            ((LN_MAX_DISTANCE - ln(distance)) / (LN_MAX_DISTANCE - LN_MIN_DISTANCE)).coerceIn(0f, 1f)

        fun distanceFor(fraction: Float): Float =
            exp(LN_MAX_DISTANCE - fraction * (LN_MAX_DISTANCE - LN_MIN_DISTANCE))

        /** Wraps to (-π, π]. */
        fun wrapAngle(radians: Float): Float {
            val twoPi = (2 * PI).toFloat()
            var wrapped = radians % twoPi
            if (wrapped > PI) wrapped -= twoPi
            if (wrapped <= -PI) wrapped += twoPi
            return wrapped
        }
    }
}
