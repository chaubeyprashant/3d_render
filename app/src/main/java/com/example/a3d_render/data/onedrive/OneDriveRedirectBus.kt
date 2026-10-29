package com.example.a3d_render.data.onedrive

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hands the OAuth redirect URI from [com.example.a3d_render.ui.onedrive.OneDriveRedirectActivity]
 * to whichever screen started the sign-in. A state flow keeps the value until it is consumed,
 * so it survives the screen being recreated while the browser was open.
 */
object OneDriveRedirectBus {
    val redirect = MutableStateFlow<Uri?>(null)
}
