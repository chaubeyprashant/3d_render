package com.example.a3d_render.data.onedrive

import java.io.IOException

/** The saved OneDrive session is missing or was revoked; the user must sign in again. */
class OneDriveAuthRequiredException(message: String = "OneDrive sign-in is required.") : IOException(message)

/** Microsoft Graph answered with a non-success HTTP status. */
class OneDriveHttpException(val statusCode: Int) : IOException("OneDrive request failed with HTTP $statusCode")

/** Interactive sign-in did not complete. [cancelled] is true when the user backed out. */
class OneDriveSignInException(message: String, val cancelled: Boolean = false) : Exception(message)
