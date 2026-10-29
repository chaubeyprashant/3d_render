package com.example.a3d_render

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.a3d_render.ui.navigation.AppNavHost
import com.example.a3d_render.ui.theme._3d_renderTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as RpavApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            _3d_renderTheme {
                AppNavHost(appContainer = appContainer)
            }
        }
    }
}
