package com.example.a3d_render.ui.viewer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.a3d_render.ui.theme.DarkCard
import com.example.a3d_render.ui.theme.DarkSurfaceVariant
import com.example.a3d_render.ui.theme.DarkToolbar
import com.example.a3d_render.ui.theme.DarkTopBar
import com.example.a3d_render.ui.theme.IndowingsGreen
import com.example.a3d_render.domain.project.GlbInspector
import com.example.a3d_render.util.ByteFormat
import com.example.a3d_render.util.DeviceCapabilities
import com.example.a3d_render.util.LargeModelLoader
import com.google.android.filament.Camera
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEnvironment
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    projectName: String,
    projectSource: String,
    glbUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeight = with(LocalDensity.current) { topBarHeightPx.toDp() }

    // The rotate button overrides the phone's auto-rotate only while the viewer is open.
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val engine = rememberEngine()
    val isLowRamDevice = remember(context) { DeviceCapabilities.isLowRamDevice(context) }
    val autoAnimateModel = remember(context) { DeviceCapabilities.shouldAutoAnimateModel(context) }
    val useFillLight = remember(context) { DeviceCapabilities.useFillLight(context) }
    val fillLightNode = if (useFillLight) rememberFillLightNode(engine) else null
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    var loadAttempt by remember { mutableIntStateOf(0) }

    val environment = rememberEnvironment(environmentLoader, isOpaque = false) {
        environmentLoader.createKTX1Environment(
            iblAssetFile = "environments/neutral/neutral_ibl.ktx",
            skyboxAssetFile = null
        ).takeIf { it.indirectLight != null }
            ?: createEnvironment(environmentLoader, isOpaque = false)
    }

    val modelFile = remember(glbUri) { File(glbUri) }
    val modelLoadState by produceState<ModelLoadState>(ModelLoadState.Loading, glbUri, loadAttempt) {
        value = ModelLoadState.Loading
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            Log.e(TAG, "Model file missing/empty at path: $glbUri")
            value = ModelLoadState.Failed(MISSING_MODEL_MESSAGE)
            return@produceState
        }
        Log.i(TAG, "Model load start path=$glbUri size=${modelFile.length()}")
        value = try {
            val extensions = withContext(Dispatchers.IO) {
                runCatching { modelFile.inputStream().use { GlbInspector.inspect(it) }.extensionsUsed }
                    .getOrDefault(emptyList())
            }
            val instance = LargeModelLoader.load(modelLoader, modelFile)
            val box = instance.model.boundingBox
            val framing = ModelFraming.compute(box.halfExtent, box.center, extensions)
            logModelBounds(instance)
            Log.i(TAG, "Model load success path=$glbUri zUp=${framing.isZUp} exaggeration=${framing.heightExaggeration}")
            ModelLoadState.Loaded(instance, framing)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Model load failed path=$glbUri", t)
            ModelLoadState.Failed(loadErrorMessage(t, modelFile.length()))
        }
    }
    val loadedState = modelLoadState as? ModelLoadState.Loaded

    // Textures finish decoding over the following frames; report it without blocking navigation.
    var textureProgress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(loadedState) {
        if (loadedState == null) return@LaunchedEffect
        val startedAt = System.nanoTime()
        while (true) {
            val progress = modelLoader.resourceLoader.asyncGetLoadProgress().takeUnless { it.isNaN() } ?: 1f
            textureProgress = progress
            if (progress >= 1f || System.nanoTime() - startedAt > TEXTURE_WAIT_TIMEOUT_NANOS) break
            withFrameNanos { }
        }
        textureProgress = 1f
    }

    val cameraController = remember { ViewerCameraController() }
    LaunchedEffect(loadedState) {
        cameraController.setGroundClearance(loadedState?.framing?.groundTopY)
    }
    val gestureHandler = remember(cameraController) { ViewerGestureHandler(context, cameraController) }
    val orthoApplied = remember { AtomicBoolean(false) }

    // UI state
    var showSettings by remember { mutableStateOf(false) }
    var isHdMode by remember { mutableStateOf(!isLowRamDevice) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Auto-dismiss toast
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3000)
            toastMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkSurfaceVariant)) {
        // ── Dark grid background ──
        ViewerDarkGridBackground(modifier = Modifier.fillMaxSize())

        // ── 3D Scene ──
        SceneView(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            renderQuality = if (isHdMode) RenderQuality.Default else RenderQuality.Performance,
            fillLightNode = fillLightNode,
            cameraNode = cameraNode,
            cameraManipulator = cameraController,
            onTouchEvent = { event, _ -> gestureHandler.onTouchEvent(event) },
            onFrame = {
                // 2D mode renders orthographically once the camera is straight down, sized to
                // match the perspective framing so the switch is seamless.
                if (cameraController.isOrthographic) {
                    val halfHeight = (cameraController.renderedDistance * ViewerCameraController.TAN_HALF_FOV).toDouble()
                    val halfWidth = halfHeight * cameraNode.getViewPortAspect()
                    cameraNode.setProjection(
                        Camera.Projection.ORTHO,
                        -halfWidth, halfWidth, -halfHeight, halfHeight,
                        ORTHO_NEAR, ORTHO_FAR
                    )
                    orthoApplied.set(true)
                } else if (orthoApplied.getAndSet(false)) {
                    cameraNode.updateProjection()
                }
            }
        ) {
            loadedState?.let { state ->
                ModelNode(
                    modelInstance = state.instance,
                    autoAnimate = autoAnimateModel,
                    position = state.framing.position,
                    rotation = state.framing.rotation,
                    scale = state.framing.scale
                )
            }
        }

        // ── Loading overlay ──
        if (modelLoadState is ModelLoadState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(
                            color = DarkCard.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    CircularProgressIndicator(color = IndowingsGreen)
                    Text(
                        text = "Loading 3D model…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (modelFile.length() > 0L) {
                        Text(
                            text = ByteFormat.format(modelFile.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // ── Top Bar ──
        RpavTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .onSizeChanged { topBarHeightPx = it.height },
            projectName = projectName,
            isLandscape = isLandscape,
            onBack = onBack,
            onRotateClick = {
                // SENSOR_LANDSCAPE works even when the phone's auto-rotate is off.
                activity?.requestedOrientation = if (isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            },
            onSettingsClick = { showSettings = true }
        )

        // ── Texture streaming indicator ──
        if (loadedState != null && textureProgress < 1f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topBarHeight + 8.dp)
                    .zIndex(1f),
                shape = RoundedCornerShape(16.dp),
                color = DarkCard.copy(alpha = 0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = IndowingsGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Loading textures… ${(textureProgress * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── Error state ──
        (modelLoadState as? ModelLoadState.Failed)?.let { failure ->
            ModelErrorCard(
                message = failure.message,
                onRetry = { loadAttempt += 1 },
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
            )
        }

        if (loadedState != null) Box(
            // Below the top bar and clear of the navigation bar / camera cutout in any orientation.
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarHeight)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .zIndex(1f)
        ) {
            // ── Zoom control bar (right edge) ──
            ZoomBar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                compact = isLandscape,
                zoomFraction = cameraController.zoomFraction,
                onZoomIn = cameraController::zoomIn,
                onZoomOut = cameraController::zoomOut,
                onZoomTo = cameraController::zoomTo
            )

            // ── Directional (trackball) navigation pad ──
            // Portrait stacks it above the toolbar; landscape has room beside it.
            NavigationPad(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = if (isLandscape) 16.dp else 112.dp),
                viewMode = cameraController.mode,
                onNudge = cameraController::nudge
            )

            // ── Bottom Toolbar ──
            BottomToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
                viewMode = cameraController.mode,
                isHdMode = isHdMode,
                onToggleViewMode = {
                    val next = if (cameraController.mode == ViewMode.THREE_D) ViewMode.TWO_D else ViewMode.THREE_D
                    cameraController.switchMode(next)
                    toastMessage = if (next == ViewMode.TWO_D) "2D top-down view" else "3D perspective view"
                },
                onToggleQuality = {
                    isHdMode = !isHdMode
                    toastMessage = if (isHdMode) "HD mode enabled" else "SD mode enabled (faster)"
                },
                onRecenter = {
                    cameraController.recenter()
                    toastMessage = "View recentered"
                },
                onScreenshot = {
                    val frame = findTextureView(rootView)?.bitmap
                    if (frame == null) {
                        toastMessage = "Screenshot unavailable — viewer not ready"
                    } else {
                        coroutineScope.launch {
                            val saved = runCatching { saveScreenshot(context, frame, projectName) }
                                .onFailure { Log.e(TAG, "Screenshot save failed", it) }
                                .getOrNull()
                            toastMessage = if (saved != null) "Screenshot saved to Pictures/RPAV" else "Couldn't save screenshot"
                        }
                    }
                }
            )
        }

        // ── Toast Notification ──
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topBarHeight + 8.dp, end = 12.dp)
                .zIndex(3f)
        ) {
            toastMessage?.let { message ->
                RpavToast(
                    message = message,
                    onDismiss = { toastMessage = null }
                )
            }
        }

        // ── Settings Dialog ──
        if (showSettings) {
            SettingsDialog(onDismiss = { showSettings = false })
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Top Bar — matches RPAV desktop
// ═══════════════════════════════════════════════════════════

@Composable
private fun RpavTopBar(
    projectName: String,
    isLandscape: Boolean,
    onBack: () -> Unit,
    onRotateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previousLightStatusBars != null) {
                controller?.isAppearanceLightStatusBars = previousLightStatusBars
            }
        }
    }
    val dateText = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date())
    val safeTop = Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )

    Column(modifier = modifier.fillMaxWidth().background(DarkTopBar)) {
        if (isLandscape) {
            // Single row keeps vertical space for the model.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(safeTop)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RPAV",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(16.dp))
                BackPill(onBack)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = projectName,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = dateText, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                TopBarActions(isLandscape, onRotateClick, onSettingsClick)
            }
            return@Column
        }

        // Top row: RPAV + date + actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(safeTop)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RPAV",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = dateText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TopBarActions(isLandscape, onRotateClick, onSettingsClick)
            }
        }
        // Back button row
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackPill(onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                text = projectName,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TopBarActions(isLandscape: Boolean, onRotateClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onRotateClick) {
            Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = if (isLandscape) "Switch to portrait" else "Switch to landscape",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun BackPill(onBack: () -> Unit) {
    Surface(
        onClick = onBack,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Back",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Bottom Toolbar — 2D/3D | HD/SD | Recenter | Screenshot
// ═══════════════════════════════════════════════════════════

@Composable
private fun BottomToolbar(
    modifier: Modifier = Modifier,
    viewMode: ViewMode,
    isHdMode: Boolean,
    onToggleViewMode: () -> Unit,
    onToggleQuality: () -> Unit,
    onRecenter: () -> Unit,
    onScreenshot: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DarkToolbar.copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(
                icon = if (viewMode == ViewMode.TWO_D) Icons.Default.Map else Icons.Default.ViewInAr,
                label = if (viewMode == ViewMode.TWO_D) "2D" else "3D",
                sublabel = "View",
                isActive = viewMode == ViewMode.TWO_D,
                onClick = onToggleViewMode
            )
            ToolbarButton(
                icon = if (isHdMode) Icons.Default.Hd else Icons.Default.Sd,
                label = if (isHdMode) "HD" else "SD",
                sublabel = "Quality",
                isActive = !isHdMode,
                onClick = onToggleQuality
            )
            ToolbarButton(
                icon = Icons.Default.CenterFocusStrong,
                label = "Recenter",
                sublabel = null,
                isActive = false,
                onClick = onRecenter
            )
            ToolbarButton(
                icon = Icons.Default.CameraAlt,
                label = "Screenshot",
                sublabel = null,
                isActive = false,
                onClick = onScreenshot
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String?,
    isActive: Boolean,
    activeColor: Color = IndowingsGreen,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) activeColor else Color.Transparent
    val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.85f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
            if (sublabel != null && sublabel != label) {
                Text(
                    text = sublabel,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Zoom control bar (vertical, right edge)
// ═══════════════════════════════════════════════════════════

@Composable
private fun ZoomBar(
    modifier: Modifier = Modifier,
    compact: Boolean,
    zoomFraction: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomTo: (Float) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = DarkToolbar.copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .width(44.dp)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onZoomIn, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = Color.White)
            }
            ZoomTrack(
                fraction = zoomFraction,
                onFractionChange = onZoomTo,
                modifier = Modifier
                    .width(44.dp)
                    .height(if (compact) 96.dp else 150.dp)
            )
            IconButton(onClick = onZoomOut, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = Color.White)
            }
        }
    }
}

/** Vertical slider; top = zoomed in. Tap or drag anywhere on the track. */
@Composable
private fun ZoomTrack(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnChange by rememberUpdatedState(onFractionChange)
    val thumbRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 7.dp.toPx() }
    fun fractionAt(y: Float, height: Int): Float {
        val usable = (height - 2 * thumbRadiusPx).coerceAtLeast(1f)
        return (1f - (y - thumbRadiusPx) / usable).coerceIn(0f, 1f)
    }
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnChange(fractionAt(offset.y, size.height)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> currentOnChange(fractionAt(offset.y, size.height)) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        currentOnChange(fractionAt(change.position.y, size.height))
                    }
                )
            }
            .semantics {
                contentDescription = "Zoom level"
                stateDescription = "${(fraction * 100).roundToInt()}%"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value ->
                    currentOnChange(value)
                    true
                }
            }
            .drawBehind {
                val centerX = size.width / 2f
                val top = thumbRadiusPx
                val bottom = size.height - thumbRadiusPx
                val thumbY = top + (1f - fraction) * (bottom - top)
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(centerX, top),
                    end = Offset(centerX, bottom),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = IndowingsGreen,
                    start = Offset(centerX, thumbY),
                    end = Offset(centerX, bottom),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color.White, radius = thumbRadiusPx, center = Offset(centerX, thumbY))
            }
    )
}

// ═══════════════════════════════════════════════════════════
// Directional navigation pad (trackball-style)
// ═══════════════════════════════════════════════════════════

@Composable
private fun NavigationPad(
    modifier: Modifier = Modifier,
    viewMode: ViewMode,
    onNudge: (NavDirection, Float) -> Unit
) {
    val is2D = viewMode == ViewMode.TWO_D
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = DarkToolbar.copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.size(128.dp)) {
            // Arrows map to on-screen directions, so they are intentionally not RTL-mirrored.
            NavArrow(
                icon = Icons.Default.KeyboardArrowUp,
                description = if (is2D) "Move north" else "Rotate up",
                direction = NavDirection.UP,
                onNudge = onNudge,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            NavArrow(
                icon = Icons.Default.KeyboardArrowDown,
                description = if (is2D) "Move south" else "Rotate down",
                direction = NavDirection.DOWN,
                onNudge = onNudge,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            NavArrow(
                icon = Icons.Default.KeyboardArrowUp,
                iconRotation = -90f,
                description = if (is2D) "Move west" else "Rotate left",
                direction = NavDirection.LEFT,
                onNudge = onNudge,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            NavArrow(
                icon = Icons.Default.KeyboardArrowUp,
                iconRotation = 90f,
                description = if (is2D) "Move east" else "Rotate right",
                direction = NavDirection.RIGHT,
                onNudge = onNudge,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            Icon(
                imageVector = if (is2D) Icons.Default.OpenWith else Icons.Default._3dRotation,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
            )
        }
    }
}

/** Moves once on press, then continuously while held. */
@Composable
private fun NavArrow(
    icon: ImageVector,
    description: String,
    direction: NavDirection,
    onNudge: (NavDirection, Float) -> Unit,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnNudge by rememberUpdatedState(onNudge)

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        currentOnNudge(direction, TAP_NUDGE_SECONDS)
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            currentOnNudge(direction, (now - last) / 1_000_000_000f)
            last = now
        }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (pressed) IndowingsGreen.copy(alpha = 0.85f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                })
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick {
                    currentOnNudge(direction, ACCESSIBILITY_NUDGE_SECONDS)
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .rotate(iconRotation)
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Model load error
// ═══════════════════════════════════════════════════════════

@Composable
private fun ModelErrorCard(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.zIndex(2f),
        shape = RoundedCornerShape(16.dp),
        color = DarkCard.copy(alpha = 0.97f),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Couldn't open the model",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Text("Back", color = Color.White)
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = IndowingsGreen)
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Green Toast Notification
// ═══════════════════════════════════════════════════════════

@Composable
private fun RpavToast(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = IndowingsGreen,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Settings Dialog
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    var isDarkTheme by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var languageExpanded by remember { mutableStateOf(false) }
    val languages = listOf("English", "Hindi", "German", "French", "Spanish")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = DarkCard,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Theme
                Text(
                    text = "Theme",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { isDarkTheme = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkTheme) DarkSurfaceVariant else Color.Transparent,
                        border = if (isDarkTheme) {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.3f)
                            )
                        } else {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.15f)
                            )
                        }
                    ) {
                        Text(
                            text = "Dark",
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        onClick = { isDarkTheme = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isDarkTheme) DarkSurfaceVariant else Color.Transparent,
                        border = if (!isDarkTheme) {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.3f)
                            )
                        } else {
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.15f)
                            )
                        }
                    ) {
                        Text(
                            text = "Light",
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Language
                Text(
                    text = "Language",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = !languageExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedBorderColor = IndowingsGreen,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTrailingIconColor = Color.White,
                            focusedTrailingIconColor = IndowingsGreen
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false },
                        containerColor = DarkCard
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang, color = Color.White) },
                                onClick = {
                                    selectedLanguage = lang
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = IndowingsGreen)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Dark Grid Background
// ═══════════════════════════════════════════════════════════

@Composable
private fun ViewerDarkGridBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                drawRect(DarkSurfaceVariant)
                val spacing = 28f
                val majorEvery = 4
                val majorColor = Color(0xFF3A3A3A)
                val minorColor = Color(0xFF333333)
                var x = 0f
                var column = 0
                while (x <= size.width) {
                    val color = if (column % majorEvery == 0) majorColor else minorColor
                    val stroke = if (column % majorEvery == 0) 1.2f else 0.6f
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    x += spacing
                    column++
                }
                var y = 0f
                var row = 0
                while (y <= size.height) {
                    val color = if (row % majorEvery == 0) majorColor else minorColor
                    val stroke = if (row % majorEvery == 0) 1.2f else 0.6f
                    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                    y += spacing
                    row++
                }
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// Internal types and helpers
// ═══════════════════════════════════════════════════════════

private sealed interface ModelLoadState {
    data object Loading : ModelLoadState
    data class Loaded(val instance: ModelInstance, val framing: ModelFraming) : ModelLoadState
    data class Failed(val message: String) : ModelLoadState
}

private fun loadErrorMessage(error: Throwable, sizeBytes: Long): String = when (error) {
    is OutOfMemoryError ->
        "Not enough memory to display this ${ByteFormat.format(sizeBytes)} model. Close other apps and " +
            "try again, or use a smaller export (up to 150 MB is supported on 4 GB devices)."
    is IOException -> "The model file could not be read from device storage. Go back and open the project again."
    is IllegalArgumentException ->
        "This GLB file could not be parsed. It may be corrupted or use unsupported glTF extensions."
    else -> "The model failed to load (${error.message ?: error.javaClass.simpleName})."
}

private fun logModelBounds(instance: ModelInstance) {
    val box = instance.model.boundingBox
    val half = box.halfExtent
    val center = box.center
    Log.i(
        TAG,
        "Model bounds halfExtents=(${half[0]}, ${half[1]}, ${half[2]}) " +
            "center=(${center[0]}, ${center[1]}, ${center[2]})"
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** SceneView renders into a TextureView, whose current frame can be read back as a bitmap. */
private fun findTextureView(view: View): TextureView? = when (view) {
    is TextureView -> view
    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findTextureView(view.getChildAt(it)) }
    else -> null
}

/** Saves the frame over the viewer background to Pictures/RPAV; returns its MediaStore URI. */
private suspend fun saveScreenshot(context: Context, frame: Bitmap, projectName: String): Uri =
    withContext(Dispatchers.IO) {
        val composed = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        Canvas(composed).apply {
            drawColor(DarkSurfaceVariant.toArgb())
            drawBitmap(frame, 0f, 0f, null)
        }
        val safeName = projectName.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "model" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "RPAV_${safeName}_$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RPAV")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { composed.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IOException("Cannot open screenshot output")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        } finally {
            composed.recycle()
            frame.recycle()
        }
        uri
    }

private const val TAG = "ViewerScreen"
private const val MISSING_MODEL_MESSAGE =
    "The model file is no longer on this device. Go back and open the project again to reload it."
private const val TEXTURE_WAIT_TIMEOUT_NANOS = 120_000_000_000L
private const val ORTHO_NEAR = 0.01
private const val ORTHO_FAR = 1000.0
private const val TAP_NUDGE_SECONDS = 0.12f
private const val ACCESSIBILITY_NUDGE_SECONDS = 0.25f
