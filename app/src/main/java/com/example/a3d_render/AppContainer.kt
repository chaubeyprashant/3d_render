package com.example.a3d_render

import android.content.Context
import com.example.a3d_render.data.drive.GoogleDriveManager
import com.example.a3d_render.data.onedrive.OneDriveManager
import com.example.a3d_render.data.project.ProjectLibrary
import com.example.a3d_render.data.repository.AuthRepositoryImpl
import com.example.a3d_render.data.repository.FirebaseAccessRepository
import com.example.a3d_render.data.repository.ProjectRepositoryImpl
import com.example.a3d_render.domain.repository.AccessRepository
import com.example.a3d_render.domain.repository.AuthRepository
import com.example.a3d_render.domain.repository.ProjectRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val projectRepository: ProjectRepository = ProjectRepositoryImpl(appContext)
    val accessRepository: AccessRepository = FirebaseAccessRepository(appContext)
    val authRepository: AuthRepository = AuthRepositoryImpl(appContext)
    val driveManager: GoogleDriveManager = GoogleDriveManager(appContext)
    val oneDriveManager: OneDriveManager = OneDriveManager(appContext)
    val projectLibrary: ProjectLibrary = ProjectLibrary(appContext, projectRepository, driveManager, oneDriveManager)
}
