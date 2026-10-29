package com.example.a3d_render.domain.repository

import com.example.a3d_render.domain.model.LoginResult

interface AuthRepository {
    suspend fun login(email: String, password: String): LoginResult
    fun isLoggedIn(): Boolean
    fun logout()
}
