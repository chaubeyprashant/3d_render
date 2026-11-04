package com.example.a3d_render.ui.onedrive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.a3d_render.MainActivity
import com.example.a3d_render.data.onedrive.OneDriveRedirectBus

/**
 * Receives the `rpav-onedrive://auth` redirect after Microsoft sign-in,
 * passes it to the picker, and brings the app back in front of the browser tab.
 */
class OneDriveRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { OneDriveRedirectBus.redirect.value = it }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
