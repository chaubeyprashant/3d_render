package com.example.a3d_render

import android.content.Context
import com.example.a3d_render.data.repository.FirebaseAccessRepository
import com.example.a3d_render.data.repository.ProjectRepositoryImpl
import com.example.a3d_render.domain.repository.AccessRepository
import com.example.a3d_render.domain.repository.ProjectRepository

class AppContainer(context: Context) {
    val projectRepository: ProjectRepository = ProjectRepositoryImpl(context)
    val accessRepository: AccessRepository = FirebaseAccessRepository(context)
}
