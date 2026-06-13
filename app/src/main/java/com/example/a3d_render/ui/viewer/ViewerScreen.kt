package com.example.a3d_render.ui.viewer

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.a3d_render.R
import com.example.a3d_render.util.DeviceCapabilities
import com.example.a3d_render.util.GlbCacheManager
import com.example.a3d_render.util.LargeModelLoader
import io.github.sceneview.SceneView
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.SurfaceType
import io.github.sceneview.createEnvironment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val useProceduralGrid = remember(context) { DeviceCapabilities.useProceduralGridBackground(context) }
    val useFillLight = remember(context) { DeviceCapabilities.useFillLight(context) }
    val fillLightNode = if (useFillLight) rememberFillLightNode(engine) else null
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    var loadAttempt by remember { mutableStateOf(0) }

    // IBL only; transparent skybox so the static Compose grid shows through empty pixels.
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (useProceduralGrid) {
            ViewerProceduralGridBackground(modifier = Modifier.fillMaxSize())
        } else {
            ViewerGridBackground(modifier = Modifier.fillMaxSize())
        }
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

        if (isModelLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = if (modelPathResult == null) {
                            "Preparing model source..."
                        } else {
                            "Loading 3D model..."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        ViewerHeader(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f),
            projectName = projectName,
            onBack = onBack
        )

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
                Button(onClick = { loadAttempt += 1 }) {
                    Text("Retry")
                }
            }
        }

        if (modelLoadState is ModelLoadState.Loaded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "One finger: rotate · Double-tap & hold: pan · Pinch: zoom",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ViewerHeader(
    projectName: String,
    onBack: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = projectName,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(48.dp))
        }
    }
}

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

/** Values below 1 shrink each grid tile (finer / closer lines). */
private const val GRID_TILE_SCALE = 0.62f

@Composable
private fun ViewerProceduralGridBackground(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val minorSpacing = with(density) { 28.dp.toPx() }
    val majorEvery = 4
    Box(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                drawRect(Color(0xFFF4F4F4))
                val majorColor = Color(0xFFB8B8B8)
                val minorColor = Color(0xFFD8D8D8)
                var x = 0f
                var column = 0
                while (x <= size.width) {
                    val color = if (column % majorEvery == 0) majorColor else minorColor
                    val stroke = if (column % majorEvery == 0) 1.5f else 1f
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    x += minorSpacing
                    column++
                }
                var y = 0f
                var row = 0
                while (y <= size.height) {
                    val color = if (row % majorEvery == 0) majorColor else minorColor
                    val stroke = if (row % majorEvery == 0) 1.5f else 1f
                    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                    y += minorSpacing
                    row++
                }
            }
        }
    )
}

@Composable
private fun ViewerGridBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sampleSize = remember(context) { DeviceCapabilities.gridBitmapSampleSize(context) }
    val gridImage = remember(context, sampleSize) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.viewer_grid_background, options)
            .asImageBitmap()
    }
    Box(
        modifier = modifier.drawWithCache {
            val shader = ImageShader(
                image = gridImage,
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated
            )
            shader.setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(GRID_TILE_SCALE, GRID_TILE_SCALE)
                }
            )
            val brush = ShaderBrush(shader)
            onDrawBehind {
                drawRect(brush = brush)
            }
        }
    )
}

private const val TAG = "ViewerScreen"
private const val FLAT_TERRAIN_THRESHOLD = 0.08f
private const val FLAT_TERRAIN_TARGET_RATIO = 0.18f
private const val MIN_HEIGHT_EXAGGERATION = 4f
private const val MAX_HEIGHT_EXAGGERATION = 24f
