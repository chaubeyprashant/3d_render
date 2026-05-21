package com.example.a3d_render.domain.repository

interface AccessRepository {
    suspend fun isAppAccessEnabled(): Boolean
}
