package com.example.a3d_render.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.a3d_render.domain.model.LoginResult
import com.example.a3d_render.domain.repository.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepositoryImpl(
    context: Context
) : AuthRepository {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            // Serialized with Gson so quotes/backslashes in credentials produce valid JSON.
            val jsonBody = gson.toJson(mapOf("email" to email, "password" to password))

            val requestBody = jsonBody.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://api.aims3d.in/auth/login")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                // If there is a token returned, you could parse it here and save it.
                // val responseBody = response.body?.string()
                // val jsonObject = org.json.JSONObject(responseBody)
                // val token = jsonObject.getString("token")
                
                sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                LoginResult.Success
            } else {
                LoginResult.Error("Login failed. Status code: ${response.code}")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "An error occurred during login")
        }
    }

    override fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    override fun logout() {
        sharedPreferences.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}
