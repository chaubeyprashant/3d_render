package com.example.a3d_render.util

import android.content.Context
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import java.io.File

object LargeModelLoader {
    suspend fun loadFromFile(
        context: Context,
        modelLoader: ModelLoader,
        modelFile: File
    ): ModelInstance? {
        return modelLoader.loadModelInstance(modelFile.absolutePath)
    }
}
