package com.example.a3d_render.ui.viewer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.a3d_render.ui.theme.DarkBackground
import com.example.a3d_render.ui.theme.DarkCard
import com.example.a3d_render.ui.theme.DarkSurfaceVariant
import com.example.a3d_render.ui.theme.DarkToolbar
import com.example.a3d_render.ui.theme.DarkTopBar
import com.example.a3d_render.ui.theme.IndowingsBlue
import com.example.a3d_render.ui.theme.IndowingsGreen
import com.example.a3d_render.util.DeviceCapabilities
import com.example.a3d_render.util.GlbCacheManager
import com.example.a3d_render.util.LargeModelLoader
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun ViewerScreen(
    projectName: String,
    projectSource: String,
    glbUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val isLowRamDevice = remember(context) { DeviceCapabilities.isLowRamDevice(context) }
    val renderQuality = remember(context) { DeviceCapabilities.viewerRenderQuality(context) }
    val autoAnimateModel = remember(context) { DeviceCapabilities.shouldAutoAnimateModel(context) }
    val useFillLight = remember(context) { DeviceCapabilities.useFillLight(context) }
    val fillLightNode = if (useFillLight) rememberFillLightNode(engine) else null
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    var loadAttempt by remember { mutableStateOf(0) }

    val environment = rememberEnvironment(environmentLoader, isOpaque = false) {
        environmentLoader.createKTX1Environment(
            iblAssetFile = "environments/neutral/neutral_ibl.ktx",
            skyboxAssetFile = null
        ).takeIf { it.indirectLight != null }
            ?: createEnvironment(environmentLoader, isOpaque = false)
    }

    val modelPathResult by produceState<Result<String>?>(initialValue = null, glbUri, loadAttempt) {
        value = withContext(Dispatchers.IO) {
            resolveModelPath(context = context, sourceUri = glbUri)
        }
    }
    val resolvedModelPath = modelPathResult?.getOrNull()
    val modelLoadState by produceState<ModelLoadState>(
        initialValue = ModelLoadState.WaitingPath,
        resolvedModelPath,
        loadAttempt
    ) {
        val modelPath = resolvedModelPath
        if (modelPath.isNullOrBlank()) {
            value = ModelLoadState.WaitingPath
            return@produceState
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() <= 0L) {
            val message = "Model file missing/empty at path: $modelPath"
            Log.e(TAG, message)
            value = ModelLoadState.Failed(message)
            return@produceState
        }

        Log.i(TAG, "Model load start path=$modelPath size=${modelFile.length()}")
        value = ModelLoadState.Loading

        val instance = runCatching {
            LargeModelLoader.loadFromFile(
                context = context,
                modelLoader = modelLoader,
                modelFile = modelFile
            )
        }.onFailure {
            Log.e(TAG, "Model load threw exception path=$modelPath", it)
        }.getOrNull()

        if (instance == null) {
            val message = if (isLowRamDevice && modelFile.length() > 200L * 1024L * 1024L) {
                "Large model failed to load. Close other apps and try again."
            } else {
                "Model parse/load failed."
            }
            Log.e(TAG, "$message path=$modelPath")
            value = ModelLoadState.Failed(message)
        } else {
            logModelBounds(instance)
            Log.i(TAG, "Model load success path=$modelPath")
            value = ModelLoadState.Loaded(instance)
        }
    }

    val isPreparingSource = modelPathResult == null
    val isParsingModel = resolvedModelPath != null &&
        (modelLoadState is ModelLoadState.WaitingPath || modelLoadState is ModelLoadState.Loading)
    val isModelLoading = isPreparingSource || isParsingModel
    val loadedState = modelLoadState as? ModelLoadState.Loaded

    val cameraManipulator = remember { ViewerCameraController.buildManipulator() }
    val doubleTapPanHandler = remember(cameraManipulator) {
        ViewerDoubleTapPanHandler { cameraManipulator }
    }
    var sceneViewHeightPx by remember { mutableStateOf(0) }

    // UI state
    var showSettings by remember { mutableStateOf(false) }
    var isHdMode by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var zoomLevel by remember { mutableFloatStateOf(0.5f) }
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
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sceneViewHeightPx = it.height },
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            renderQuality = renderQuality,
            fillLightNode = fillLightNode,
            cameraManipulator = cameraManipulator,
            onTouchEvent = { event, _ ->
                if (sceneViewHeightPx > 0) {
                    doubleTapPanHandler.onTouchEvent(event, sceneViewHeightPx)
                } else {
                    false
                }
            }
        ) {
            loadedState?.let { state ->
                ModelNode(
                    modelInstance = state.instance,
                    scaleToUnits = ViewerCameraController.MODEL_UNITS,
                    centerOrigin = Position(0f, 0f, 0f),
                    autoAnimate = autoAnimateModel,
                    apply = { applyTerrainHeightExaggeration() }
                )
            }
        }

        // ── Loading overlay ──
        if (isModelLoading) {
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
                        text = if (modelPathResult == null) "Preparing model source..."
                        else "Loading 3D model...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        // ── Top Bar ──
        IndowingsTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f),
            projectName = projectName,
            onBack = onBack,
            onSettingsClick = { showSettings = true }
        )

        // ── Error state ──
        if (modelPathResult?.isFailure == true || modelLoadState is ModelLoadState.Failed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when {
                        modelPathResult?.isFailure == true ->
                            modelPathResult?.exceptionOrNull()?.message
                                ?: "Unable to open GLB from the selected source."
                        else -> (modelLoadState as ModelLoadState.Failed).message
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { loadAttempt += 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = IndowingsGreen)
                ) {
                    Text("Retry", color = Color.White)
                }
            }
        }

        // ── Zoom Slider (right edge) ──
        if (modelLoadState is ModelLoadState.Loaded) {
            ZoomSlider(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .zIndex(1f),
                zoomLevel = zoomLevel,
                onZoomChange = { zoomLevel = it }
            )
        }

        // ── Bottom Toolbar ──
        if (modelLoadState is ModelLoadState.Loaded) {
            BottomToolbar(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 12.dp, start = 12.dp)
                    .zIndex(1f),
                isHdMode = isHdMode,
                onToggleQuality = {
                    isHdMode = !isHdMode
                    toastMessage = if (isHdMode) "HD mode enabled" else "SD mode enabled"
                },
                onReset = {
                    loadAttempt += 1
                    toastMessage = "View reset"
                },
                onScreenshot = {
                    toastMessage = "Screenshot saved successfully!"
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
                .padding(top = 100.dp, end = 12.dp)
                .zIndex(3f)
        ) {
            toastMessage?.let { message ->
                IndowingsToast(
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
// Top Bar — matches INDOWINGS desktop
// ═══════════════════════════════════════════════════════════

@Composable
private fun IndowingsTopBar(
    projectName: String,
    onBack: () -> Unit,
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

    Column(modifier = modifier.fillMaxWidth().background(DarkTopBar)) {
        // Top row: INDOWINGS + date + settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INDOWINGS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date()),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
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
        // Back button row
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

// ═══════════════════════════════════════════════════════════
// Bottom Toolbar — 2D Orthophoto | HD/SD | Reset | Screenshot
// ═══════════════════════════════════════════════════════════

@Composable
private fun BottomToolbar(
    modifier: Modifier = Modifier,
    isHdMode: Boolean,
    onToggleQuality: () -> Unit,
    onReset: () -> Unit,
    onScreenshot: () -> Unit
) {
    var screenshotActive by remember { mutableStateOf(false) }

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
            // 2D Orthophoto
            ToolbarButton(
                icon = Icons.Default.Map,
                label = "2D",
                sublabel = "Orthophoto",
                isActive = false,
                onClick = { }
            )
            // HD/SD toggle
            ToolbarButton(
                icon = Icons.Default.Hd,
                label = if (isHdMode) "HD" else "SD",
                sublabel = if (isHdMode) "HD" else "SD",
                isActive = !isHdMode,
                activeColor = IndowingsGreen,
                onClick = onToggleQuality
            )
            // Reset
            ToolbarButton(
                icon = Icons.Default.Refresh,
                label = "Reset",
                sublabel = null,
                isActive = false,
                onClick = onReset
            )
            // Screenshot
            ToolbarButton(
                icon = Icons.Default.CameraAlt,
                label = "Screenshot",
                sublabel = null,
                isActive = screenshotActive,
                activeColor = IndowingsGreen,
                onClick = {
                    screenshotActive = true
                    onScreenshot()
                    // Reset active state after a moment
                    screenshotActive = false
                }
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
// Zoom Slider (vertical, right edge)
// ═══════════════════════════════════════════════════════════

@Composable
private fun ZoomSlider(
    modifier: Modifier = Modifier,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit
) {
    Column(
        modifier = modifier.width(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // + button
        Surface(
            onClick = { onZoomChange((zoomLevel + 0.1f).coerceAtMost(1f)) },
            shape = RoundedCornerShape(6.dp),
            color = IndowingsBlue,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom in",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Vertical slider track
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            // Slider thumb
            val thumbOffset = ((1f - zoomLevel) * 152f).dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = thumbOffset)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // - button
        Surface(
            onClick = { onZoomChange((zoomLevel - 0.1f).coerceAtLeast(0f)) },
            shape = RoundedCornerShape(6.dp),
            color = IndowingsBlue,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom out",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Green Toast Notification
// ═══════════════════════════════════════════════════════════

@Composable
private fun IndowingsToast(
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
    data object WaitingPath : ModelLoadState
    data object Loading : ModelLoadState
    data class Loaded(val instance: ModelInstance) : ModelLoadState
    data class Failed(val message: String) : ModelLoadState
}

/**
 * Flat terrain meshes collapse to paper-thin sheets after uniform [scaleToUnits] scaling.
 * Stretch Y just enough so relief is visible from the default oblique camera.
 */
private fun io.github.sceneview.node.ModelNode.applyTerrainHeightExaggeration() {
    val halfExtents = boundingBox.halfExtent
    val halfX = halfExtents[0]
    val halfY = halfExtents[1]
    val halfZ = halfExtents[2]
    val horizontalExtent = max(halfX, halfZ).coerceAtLeast(0.001f)
    val flatness = halfY / horizontalExtent
    if (flatness >= FLAT_TERRAIN_THRESHOLD) return

    val exaggeration = (FLAT_TERRAIN_TARGET_RATIO / flatness.coerceAtLeast(0.001f))
        .coerceIn(MIN_HEIGHT_EXAGGERATION, MAX_HEIGHT_EXAGGERATION)
    scale = Scale(scale.x, scale.y * exaggeration, scale.z)
    Log.i(
        TAG,
        "Applied terrain height exaggeration=${"%.1f".format(exaggeration)}x " +
            "(halfX=$halfX halfY=$halfY halfZ=$halfZ)"
    )
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

private fun resolveModelPath(context: Context, sourceUri: String): Result<String> = runCatching {
    val parsed = Uri.parse(sourceUri)
    when (parsed.scheme?.lowercase()) {
        null -> {
            val file = File(sourceUri)
            require(file.exists()) { "Model file path does not exist." }
            file.absolutePath
        }
        "file" -> {
            val file = File(parsed.path.orEmpty())
            require(file.exists()) { "Model file not found in local storage." }
            file.absolutePath
        }
        "content" -> {
            val destination = File(
                context.cacheDir,
                "active_${sourceUri.hashCode()}.glb"
            )
            if (destination.exists() && destination.length() > 0L) {
                return@runCatching destination.absolutePath
            }
            GlbCacheManager.prepareForLargeModelLoad(context, keepAbsolutePath = destination.absolutePath)
            val inputStream = context.contentResolver.openInputStream(parsed)
                ?: context.contentResolver.openAssetFileDescriptor(parsed, "r")?.createInputStream()
                ?: context.contentResolver.openFileDescriptor(parsed, "r")?.let { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                }
                ?: throw IllegalArgumentException(
                    "File provider did not return readable stream for: $sourceUri"
                )

            inputStream.use { input ->
                GlbCacheManager.copyToCache(input, destination)
            }
            require(destination.exists() && destination.length() > 0L) {
                "Copied GLB cache file is empty."
            }
            destination.absolutePath
        }
        else -> throw IllegalArgumentException("Unsupported source URI scheme: ${parsed.scheme}")
    }
}.onFailure { throwable ->
    Log.e(TAG, "resolveModelPath failed for uri=$sourceUri", throwable)
}.recoverCatching { throwable ->
    val reason = throwable.message ?: throwable.javaClass.simpleName
    throw IllegalStateException("Unable to read selected GLB: $reason", throwable)
}

private const val TAG = "ViewerScreen"
private const val FLAT_TERRAIN_THRESHOLD = 0.08f
private const val FLAT_TERRAIN_TARGET_RATIO = 0.18f
private const val MIN_HEIGHT_EXAGGERATION = 4f
private const val MAX_HEIGHT_EXAGGERATION = 24f
