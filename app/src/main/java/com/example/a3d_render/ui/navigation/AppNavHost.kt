package com.example.a3d_render.ui.navigation

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.a3d_render.AppContainer
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.ui.access.AccessGateViewModel
import com.example.a3d_render.ui.access.AccessLockedScreen
import com.example.a3d_render.ui.access.AccessSplashScreen
import com.example.a3d_render.ui.dashboard.DashboardScreen
import com.example.a3d_render.ui.dashboard.DashboardViewModel
import com.example.a3d_render.ui.dashboard.rememberProjectFilePicker
import com.example.a3d_render.ui.dashboard.rememberProjectFolderPicker
import com.example.a3d_render.ui.viewer.ViewerScreen

private const val SPLASH_ROUTE = "splash"
private const val LOCKED_ROUTE = "locked"
private const val DASHBOARD_ROUTE = "dashboard"
private const val VIEWER_ROUTE = "viewer"
private const val ARG_NAME = "name"
private const val ARG_SOURCE = "source"
private const val ARG_GLB_URI = "glbUri"

@Composable
fun AppNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as Application

    NavHost(
        navController = navController,
        startDestination = SPLASH_ROUTE
    ) {
        composable(SPLASH_ROUTE) {
            val gateViewModel: AccessGateViewModel = viewModel(
                factory = AccessGateViewModel.Factory(
                    accessRepository = appContainer.accessRepository
                )
            )
            val gateState by gateViewModel.uiState.collectAsState()

            LaunchedEffect(gateState.isLoading, gateState.isAccessEnabled) {
                if (!gateState.isLoading) {
                    val destination = if (gateState.isAccessEnabled) DASHBOARD_ROUTE else LOCKED_ROUTE
                    navController.navigate(destination) {
                        popUpTo(SPLASH_ROUTE) { inclusive = true }
                    }
                }
            }

            AccessSplashScreen()
        }

        composable(LOCKED_ROUTE) {
            AccessLockedScreen(
                onRetry = {
                    navController.navigate(SPLASH_ROUTE) {
                        popUpTo(LOCKED_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        composable(DASHBOARD_ROUTE) {
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(
                    application = application,
                    projectRepository = appContainer.projectRepository
                )
            )
            val uiState by dashboardViewModel.uiState.collectAsState()

            val localPicker = rememberProjectFolderPicker { uri ->
                dashboardViewModel.onProjectFolderPicked(uri, ProjectSource.LOCAL) { project ->
                    navController.navigate(project.viewerRoute())
                }
            }
            val drivePicker = rememberProjectFolderPicker { uri ->
                dashboardViewModel.onProjectFolderPicked(uri, ProjectSource.GOOGLE_DRIVE) { project ->
                    navController.navigate(project.viewerRoute())
                }
            }
            val localFilePicker = rememberProjectFilePicker { uri ->
                dashboardViewModel.onProjectFilePicked(uri, ProjectSource.LOCAL) { project ->
                    navController.navigate(project.viewerRoute())
                }
            }
            val driveFilePicker = rememberProjectFilePicker { uri ->
                dashboardViewModel.onProjectFilePicked(uri, ProjectSource.GOOGLE_DRIVE) { project ->
                    navController.navigate(project.viewerRoute())
                }
            }

            DashboardScreen(
                uiState = uiState,
                onLocalFolderPickerClick = localPicker,
                onDriveFolderPickerClick = drivePicker,
                onLocalFilePickerClick = localFilePicker,
                onDriveFilePickerClick = driveFilePicker,
                onOpenProject = { navController.navigate(it.viewerRoute()) },
                onRenameProject = dashboardViewModel::renameProject,
                onDismissError = dashboardViewModel::clearError
            )
        }

        composable(
            route = "$VIEWER_ROUTE?$ARG_NAME={$ARG_NAME}&$ARG_SOURCE={$ARG_SOURCE}&$ARG_GLB_URI={$ARG_GLB_URI}",
            arguments = listOf(
                navArgument(ARG_NAME) { type = NavType.StringType },
                navArgument(ARG_SOURCE) { type = NavType.StringType },
                navArgument(ARG_GLB_URI) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString(ARG_NAME).orEmpty()
            val source = backStackEntry.arguments?.getString(ARG_SOURCE).orEmpty()
            val glbUri = backStackEntry.arguments?.getString(ARG_GLB_URI).orEmpty()

            ViewerScreen(
                projectName = Uri.decode(name),
                projectSource = Uri.decode(source),
                glbUri = Uri.decode(glbUri),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun com.example.a3d_render.domain.model.ProjectItem.viewerRoute(): String {
    return "$VIEWER_ROUTE?" +
        "$ARG_NAME=${Uri.encode(name)}&" +
        "$ARG_SOURCE=${Uri.encode(source.name)}&" +
        "$ARG_GLB_URI=${Uri.encode(glbUri)}"
}
