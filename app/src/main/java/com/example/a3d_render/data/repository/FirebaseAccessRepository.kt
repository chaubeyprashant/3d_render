package com.example.a3d_render.data.repository

import android.content.Context
import com.example.a3d_render.data.firebase.FirebaseInitializer
import com.example.a3d_render.domain.repository.AccessRepository
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

class FirebaseAccessRepository(
    private val context: Context
) : AccessRepository {

    override suspend fun isAppAccessEnabled(): Boolean {
        if (!FirebaseInitializer.initializeIfNeeded(context)) return false

        return runCatching {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            remoteConfig.setConfigSettingsAsync(settings).await()
            remoteConfig.setDefaultsAsync(
                mapOf(ACCESS_FLAG_KEY to false)
            ).await()
            remoteConfig.fetchAndActivate().await()
            remoteConfig.getBoolean(ACCESS_FLAG_KEY)
        }.getOrDefault(false)
    }

    companion object {
        const val ACCESS_FLAG_KEY = "app_access_enabled"
    }
}
