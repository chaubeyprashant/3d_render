package com.example.a3d_render.ui.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3d_render.domain.model.DriveFileItem
import com.example.a3d_render.domain.project.GlbSizePolicy
import com.example.a3d_render.ui.common.ImportProgressOverlay
import com.example.a3d_render.ui.common.ImportState
import com.example.a3d_render.ui.common.LargeFileDialog
import com.example.a3d_render.util.ByteFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScreenBg = Color(0xFF0F1923)
private val CardBg = Color(0xFF162230)
private val CardBorder = Color(0xFF243447)
private val Accent = Color(0xFF00C853)
private val TextPrimary = Color(0xFFE8EDF2)
private val TextSecondary = Color(0xFF8A9BB0)
private val TextDim = Color(0xFF5A6F82)
private val ErrorRed = Color(0xFFFF6B6B)
private val WarningAmber = Color(0xFFFFB300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivePickerScreen(
    uiState: DrivePickerUiState,
    importState: ImportState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onFolderClick: (DriveFileItem) -> Unit,
    onGlbClick: (DriveFileItem) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onOpenFolderAsProject: () -> Unit,
    onRetry: () -> Unit,
    onNavigateUp: () -> Unit,
    onConfirmLargeFile: () -> Unit,
    onDismissLargeFile: () -> Unit,
    onDismissImportError: () -> Unit,
    providerName: String = "Google Drive"
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(importState.error) {
        val message = importState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissImportError()
    }

    BackHandler(enabled = !importState.isBusy, onBack = onNavigateUp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(providerName, fontWeight = FontWeight.Bold)
                        uiState.accountEmail?.let {
                            Text(it, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !importState.isBusy) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Accent
                        )
                    }
                },
                actions = {
                    if (uiState.isSignedIn) {
                        TextButton(onClick = onSignOutClick, enabled = !importState.isBusy) {
                            Text("Sign out", color = Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = ScreenBg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.isSignedIn) {
                SignInPrompt(providerName = providerName, error = uiState.signInError, onSignInClick = onSignInClick)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Breadcrumbs(path = uiState.folderPath, onClick = onBreadcrumbClick)
                    FolderActions(
                        isAtRoot = uiState.isAtRoot,
                        enabled = !uiState.isLoading && uiState.listError == null,
                        onOpenFolderAsProject = onOpenFolderAsProject
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            uiState.isLoading -> CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Accent
                            )
                            uiState.listError != null -> ListError(
                                message = uiState.listError,
                                onRetry = onRetry,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            uiState.entries.isEmpty() -> Text(
                                text = if (uiState.hiddenFileCount > 0) {
                                    "No folders or GLB files here (${uiState.hiddenFileCount} other files hidden)."
                                } else {
                                    "This folder is empty."
                                },
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            )
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.entries, key = { it.id }) { item ->
                                    DriveItemCard(
                                        item = item,
                                        onClick = { if (item.isFolder) onFolderClick(item) else onGlbClick(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            importState.progress?.let { ImportProgressOverlay(progress = it) }
        }
    }

    importState.largeFilePrompt?.let { prompt ->
        LargeFileDialog(prompt = prompt, onConfirm = onConfirmLargeFile, onDismiss = onDismissLargeFile)
    }
}

@Composable
private fun SignInPrompt(providerName: String, error: String?, onSignInClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign in to browse your ${if (providerName == "Google Drive") "Drive" else providerName}",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open an RPAV project folder or GLB file stored in $providerName. Access is read-only.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSignInClick,
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(
                if (providerName == "Google Drive") "Sign in with Google" else "Sign in with Microsoft",
                color = ScreenBg,
                fontWeight = FontWeight.Bold
            )
        }
        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            ErrorRow(message = it)
        }
    }
}

@Composable
private fun Breadcrumbs(path: List<DriveFolderRef>, onClick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        path.forEachIndexed { index, folder ->
            val isCurrent = index == path.lastIndex
            Text(
                text = folder.name,
                color = if (isCurrent) TextPrimary else Accent,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier
                    .clickable(enabled = !isCurrent) { onClick(index) }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
            if (!isCurrent) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderActions(isAtRoot: Boolean, enabled: Boolean, onOpenFolderAsProject: () -> Unit) {
    if (isAtRoot) {
        Text(
            text = "Open a project folder, or tap a GLB file to view it.",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        return
    }
    OutlinedButton(
        onClick = onOpenFolderAsProject,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open this folder as project", color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DriveItemCard(item: DriveFileItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isFolder) Icons.Filled.Folder else Icons.Filled.ViewInAr,
                contentDescription = null,
                tint = if (item.isFolder) TextSecondary else Accent,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isFolder) {
                    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(item.modifiedTime))
                    val tooLarge = item.size > GlbSizePolicy.RECOMMENDED_MAX_BYTES
                    Text(
                        text = "${ByteFormat.format(item.size.coerceAtLeast(0))} · $date" +
                            if (tooLarge) " · above 150 MB limit" else "",
                        color = if (tooLarge) WarningAmber else TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (item.isFolder) "Open folder" else "Open model",
                tint = TextDim
            )
        }
    }
}

@Composable
private fun ListError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorRow(message = message)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry, border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f))) {
            Text("Retry", color = Accent)
        }
    }
}

@Composable
private fun ErrorRow(message: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = ErrorRed,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = message, color = ErrorRed, fontSize = 13.sp)
    }
}
