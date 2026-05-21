package com.example.a3d_render.ui.viewer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.filament.utils.Manipulator
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.createEnvironment
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.gesture.orbitHomePosition
import io.github.sceneview.gesture.targetPosition
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SkyTop = Color(0xFF5B8FD4)
private val SkyHorizon = Color(0xFFB8D8F0)
@Composable
fun ViewerScreen(
    projectName: String,
    projectSource: String,
    glbUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    var loadAttempt by remember { mutableStateOf(0) }

    val environment = rememberEnvironment(environmentLoader) {
        environmentLoader.createKTX1Environment(
            iblAssetFile = "environments/neutral/neutral_ibl.ktx",
            skyboxAssetFile = "environments/neutral/neutral_skybox.ktx"
        ) ?: createEnvironment(environmentLoader, isOpaque = true)
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
            withContext(Dispatchers.Main) {
                modelLoader.createModelInstance(modelFile)
            }
        }.onFailure {
            Log.e(TAG, "Model load threw exception path=$modelPath", it)
        }.getOrNull()

        if (instance == null) {
            val message = "Model parse/load failed."
            Log.e(TAG, "$message path=$modelPath")
            value = ModelLoadState.Failed(message)
        } else {
            Log.i(TAG, "Model load success path=$modelPath")
            value = ModelLoadState.Loaded(instance)
        }
    }

    val isPreparingSource = modelPathResult == null
    val isParsingModel = resolvedModelPath != null &&
        (modelLoadState is ModelLoadState.WaitingPath || modelLoadState is ModelLoadState.Loading)
    val isModelLoading = isPreparingSource || isParsingModel

    Box(modifier = Modifier.fillMaxSize()) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            environmentLoader = environmentLoader,
            environment = environment,
            renderQuality = RenderQuality.Default,
            cameraManipulator = rememberCameraManipulator(
                creator = { createSmoothEarthLikeManipulator() }
            )
        ) {
            val loaded = modelLoadState as? ModelLoadState.Loaded
            loaded?.let { state ->
                ModelNode(
                    modelInstance = state.instance,
                    scaleToUnits = 2.0f,
                    centerOrigin = Position(0f, 0f, 0f),
                    rotation = Rotation(x = -12f, y = 28f, z = 0f),
                    autoAnimate = true
                )
            }
        }

        if (isModelLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                SkyTop.copy(alpha = 0.55f),
                                SkyHorizon.copy(alpha = 0.45f)
                            )
                        )
                    ),
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when {
                        modelPathResult == null -> "Preparing model source..."
                        resolvedModelPath == null ->
                            modelPathResult?.exceptionOrNull()?.message
                                ?: "Unable to open GLB from the selected source."
                        modelLoadState is ModelLoadState.Failed ->
                            (modelLoadState as ModelLoadState.Failed).message
                        modelLoadState is ModelLoadState.Loading -> "Loading 3D model..."
                        else -> "Drag to orbit · Pinch to zoom · Two fingers to pan"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (modelPathResult?.isFailure == true || modelLoadState is ModelLoadState.Failed) {
                Button(onClick = { loadAttempt += 1 }) {
                    Text("Retry")
                }
            }
            if (modelLoadState is ModelLoadState.Loaded) {
                Text(
                    text = projectName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = projectSource,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private sealed interface ModelLoadState {
    data object WaitingPath : ModelLoadState
    data object Loading : ModelLoadState
    data class Loaded(val instance: ModelInstance) : ModelLoadState
    data class Failed(val message: String) : ModelLoadState
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
            val inputStream = context.contentResolver.openInputStream(parsed)
                ?: context.contentResolver.openAssetFileDescriptor(parsed, "r")?.createInputStream()
                ?: context.contentResolver.openFileDescriptor(parsed, "r")?.let { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                }
                ?: throw IllegalArgumentException(
                    "File provider did not return readable stream for: $sourceUri"
                )

            inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
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

private fun createSmoothEarthLikeManipulator(): CameraGestureDetector.CameraManipulator {
    val baseManipulator = Manipulator.Builder()
        .targetPosition(Position(0f, 0.6f, 0f))
        .orbitHomePosition(Position(0.8f, 1.8f, 4.2f))
        .orbitSpeed(0.0022f, 0.0022f)
        .zoomSpeed(0.065f)
        .build(Manipulator.Mode.ORBIT)

    return CameraGestureDetector.DefaultCameraManipulator(
        manipulator = baseManipulator,
        pinchZoomSpeed = 1f / 24f,
        pinchZoomDamping = 0.86f
    )
}
