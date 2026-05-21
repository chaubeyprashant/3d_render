package com.example.a3d_render.data.firebase

import android.content.Context
import com.example.a3d_render.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    fun initializeIfNeeded(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true

        val apiKey = BuildConfig.FIREBASE_API_KEY
        val appId = BuildConfig.FIREBASE_APP_ID
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val senderId = BuildConfig.FIREBASE_GCM_SENDER_ID

        if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank() || senderId.isBlank()) {
            return false
        }

        val options = FirebaseOptions.Builder()
            .setApiKey(apiKey)
            .setApplicationId(appId)
            .setProjectId(projectId)
            .setGcmSenderId(senderId)
            .build()

        FirebaseApp.initializeApp(context, options)
        return true
    }
}
