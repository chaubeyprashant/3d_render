package com.example.a3d_render.ui.navigation

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.ui.access.AccessGateViewModel
import com.example.a3d_render.ui.access.AccessLockedScreen
import com.example.a3d_render.ui.access.AccessSplashScreen
import com.example.a3d_render.ui.auth.LoginScreen
import com.example.a3d_render.ui.auth.LoginViewModel
import com.example.a3d_render.ui.dashboard.DashboardScreen
import com.example.a3d_render.ui.dashboard.DashboardViewModel
import com.example.a3d_render.ui.dashboard.rememberGlbFilePicker
import com.example.a3d_render.ui.dashboard.rememberProjectFolderPicker
import com.example.a3d_render.ui.drive.DrivePickerScreen
import com.example.a3d_render.ui.drive.DrivePickerViewModel
import com.example.a3d_render.ui.onedrive.OneDrivePickerViewModel
import com.example.a3d_render.ui.viewer.ViewerScreen

private const val SPLASH_ROUTE = "splash"
private const val LOCKED_ROUTE = "locked"
private const val LOGIN_ROUTE = "login"
private const val DASHBOARD_ROUTE = "dashboard"
private const val DRIVE_PICKER_ROUTE = "drive_picker"
private const val ONEDRIVE_PICKER_ROUTE = "onedrive_picker"
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
                    val destination = if (gateState.isAccessEnabled) {
                        if (appContainer.authRepository.isLoggedIn()) DASHBOARD_ROUTE else LOGIN_ROUTE
                    } else LOCKED_ROUTE
                    navController.navigate(destination) {
                        popUpTo(SPLASH_ROUTE) { inclusive = true }
                    }
                }
            }

            AccessSplashScreen()
        }

        composable(LOGIN_ROUTE) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.Factory(
                    authRepository = appContainer.authRepository
                )
            )
            val uiState by loginViewModel.uiState.collectAsState()

            LoginScreen(
                uiState = uiState,
                onEmailChange = loginViewModel::onEmailChange,
                onPasswordChange = loginViewModel::onPasswordChange,
                onLoginClick = {
                    loginViewModel.onLoginClick {
                        navController.navigate(DASHBOARD_ROUTE) {
                            popUpTo(LOGIN_ROUTE) { inclusive = true }
                        }
                    }
                }
            )
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
                    projectRepository = appContainer.projectRepository,
                    projectLibrary = appContainer.projectLibrary
                )
            )
            val uiState by dashboardViewModel.uiState.collectAsState()
            val importState by dashboardViewModel.importState.collectAsState()

            // Pick up projects added from the Drive screen and fresh last-opened times.
            LaunchedEffect(Unit) { dashboardViewModel.refreshRecentProjects() }

            val localPicker = rememberProjectFolderPicker { uri ->
                dashboardViewModel.onProjectFolderPicked(uri) { project ->
                    navController.navigate(project.viewerRoute())
                }
            }

            val filePicker = rememberGlbFilePicker { uri ->
                dashboardViewModel.onGlbFilePicked(uri) { project ->
                    navController.navigate(project.viewerRoute())
                }
            }

            DashboardScreen(
                uiState = uiState,
                importState = importState,
                onLocalFolderPickerClick = localPicker,
                onLocalFilePickerClick = filePicker,
                onDriveClick = { navController.navigate(DRIVE_PICKER_ROUTE) },
                onOneDriveClick = { navController.navigate(ONEDRIVE_PICKER_ROUTE) },
                onOpenProject = { project ->
                    dashboardViewModel.openProject(project) { opened ->
                        navController.navigate(opened.viewerRoute())
                    }
                },
                onRenameProject = dashboardViewModel::renameProject,
                onDeleteProject = dashboardViewModel::deleteProject,
                onConfirmLargeFile = dashboardViewModel::confirmLargeFile,
                onDismissLargeFile = dashboardViewModel::dismissLargeFile,
                onDismissError = dashboardViewModel::clearError,
                onLogoutClick = {
                    appContainer.authRepository.logout()
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(DRIVE_PICKER_ROUTE) {
            val drivePickerViewModel: DrivePickerViewModel = viewModel(
                factory = DrivePickerViewModel.Factory(
                    application = application,
                    driveManager = appContainer.driveManager,
                    projectLibrary = appContainer.projectLibrary
                )
            )
            val uiState by drivePickerViewModel.uiState.collectAsState()
            val importState by drivePickerViewModel.importState.collectAsState()
            val listAuthIntent by drivePickerViewModel.authRecoveryIntent.collectAsState()

            val signInLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                drivePickerViewModel.handleSignInResult(result.data)
            }
            val authRecoveryLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) drivePickerViewModel.loadCurrentFolder()
            }
            val pendingAuthIntent = listAuthIntent ?: importState.driveAuthIntent
            LaunchedEffect(pendingAuthIntent) {
                pendingAuthIntent?.let {
                    drivePickerViewModel.consumeAuthRecoveryIntent()
                    authRecoveryLauncher.launch(it)
                }
            }

            val openViewer: (ProjectItem) -> Unit = { project ->
                navController.navigate(project.viewerRoute()) {
                    popUpTo(DRIVE_PICKER_ROUTE) { inclusive = true }
                }
            }

            DrivePickerScreen(
                uiState = uiState,
                importState = importState,
                onSignInClick = { signInLauncher.launch(drivePickerViewModel.getSignInIntent()) },
                onSignOutClick = drivePickerViewModel::signOut,
                onFolderClick = drivePickerViewModel::openFolder,
                onGlbClick = { file -> drivePickerViewModel.openGlbFile(file, openViewer) },
                onBreadcrumbClick = drivePickerViewModel::navigateTo,
                onOpenFolderAsProject = { drivePickerViewModel.openCurrentFolderAsProject(openViewer) },
                onRetry = drivePickerViewModel::loadCurrentFolder,
                onNavigateUp = {
                    if (!drivePickerViewModel.navigateUp()) navController.popBackStack()
                },
                onConfirmLargeFile = drivePickerViewModel::confirmLargeFile,
                onDismissLargeFile = drivePickerViewModel::dismissLargeFile,
                onDismissImportError = drivePickerViewModel::clearImportError
            )
        }

        composable(ONEDRIVE_PICKER_ROUTE) {
            val context = LocalContext.current
            val oneDriveViewModel: OneDrivePickerViewModel = viewModel(
                factory = OneDrivePickerViewModel.Factory(
                    application = application,
                    oneDriveManager = appContainer.oneDriveManager,
                    projectLibrary = appContainer.projectLibrary
                )
            )
            val uiState by oneDriveViewModel.uiState.collectAsState()
            val importState by oneDriveViewModel.importState.collectAsState()

            val openViewer: (ProjectItem) -> Unit = { project ->
                navController.navigate(project.viewerRoute()) {
                    popUpTo(ONEDRIVE_PICKER_ROUTE) { inclusive = true }
                }
            }

            DrivePickerScreen(
                uiState = uiState,
                importState = importState,
                onSignInClick = {
                    oneDriveViewModel.startSignIn()?.let { uri ->
                        CustomTabsIntent.Builder().build().launchUrl(context, uri)
                    }
                },
                onSignOutClick = oneDriveViewModel::signOut,
                onFolderClick = oneDriveViewModel::openFolder,
                onGlbClick = { file -> oneDriveViewModel.openGlbFile(file, openViewer) },
                onBreadcrumbClick = oneDriveViewModel::navigateTo,
                onOpenFolderAsProject = { oneDriveViewModel.openCurrentFolderAsProject(openViewer) },
                onRetry = oneDriveViewModel::loadCurrentFolder,
                onNavigateUp = {
                    if (!oneDriveViewModel.navigateUp()) navController.popBackStack()
                },
                onConfirmLargeFile = oneDriveViewModel::confirmLargeFile,
                onDismissLargeFile = oneDriveViewModel::dismissLargeFile,
                onDismissImportError = oneDriveViewModel::clearImportError,
                providerName = "OneDrive"
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

private fun ProjectItem.viewerRoute(): String {
    return "$VIEWER_ROUTE?" +
        "$ARG_NAME=${Uri.encode(name)}&" +
        "$ARG_SOURCE=${Uri.encode(source.name)}&" +
        "$ARG_GLB_URI=${Uri.encode(glbUri)}"
}
