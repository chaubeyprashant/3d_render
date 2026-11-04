package com.example.a3d_render.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.ui.common.ImportProgressOverlay
import com.example.a3d_render.ui.common.ImportState
import com.example.a3d_render.ui.common.LargeFileDialog
import com.example.a3d_render.util.ByteFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Colors matching reference design ──
private val ScreenBg = Color(0xFF0F1923)
private val CardBg = Color(0xFF162230)
private val CardBorder = Color(0xFF243447)
private val AccentGreen = Color(0xFF00C853)
private val StatusRed = Color(0xFFFF4444)
private val StatusAmber = Color(0xFFFFB300)
private val StatusGreen = Color(0xFF00C853)
private val ChipBg = Color(0xFF1E2E3E)
private val ChipBorder = Color(0xFF2C3E50)
private val TextPrimary = Color(0xFFE8EDF2)
private val TextSecondary = Color(0xFF8A9BB0)
private val TextDim = Color(0xFF5A6F82)
private val ButtonDarkGreen = Color(0xFF1B3A2A)
private val ButtonLightBg = Color(0xFF1E2E3E)
private val AvatarGradientStart = Color(0xFFB0B0B0)
private val AvatarGradientEnd = Color(0xFF808080)
private val ProfileBg = Color(0xFF1A2D3D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    importState: ImportState,
    onLocalFolderPickerClick: () -> Unit,
    onLocalFilePickerClick: () -> Unit,
    onDriveClick: () -> Unit,
    onOneDriveClick: () -> Unit,
    onOpenProject: (ProjectItem) -> Unit,
    onRenameProject: (projectId: String, newName: String) -> Unit,
    onDeleteProject: (ProjectItem) -> Unit,
    onConfirmLargeFile: () -> Unit,
    onDismissLargeFile: () -> Unit,
    onDismissError: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<ProjectItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProjectItem?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(importState.error) {
        val message = importState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissError()
    }

    Scaffold(
        containerColor = ScreenBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                // Two cards on phones in portrait; more columns in landscape and on tablets.
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Top Bar (full width) ──
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(12.dp))
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TopBar(
                        isOnline = uiState.isOnline,
                        onLogoutClick = { showLogoutConfirm = true }
                    )
                }

                // ── Welcome Section (full width) ──
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WelcomeSection(
                        onLocalFolderPickerClick = onLocalFolderPickerClick,
                        onLocalFilePickerClick = onLocalFilePickerClick,
                        onDriveClick = onDriveClick,
                        onOneDriveClick = onOneDriveClick
                    )
                }

                // ── Recent Projects Header (full width) ──
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            text = "Recent projects",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Your last 10 projects · Tap a project to open",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }

                // ── Project Cards (2-column grid) ──
                if (uiState.recentProjects.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState()
                    }
                } else {
                    itemsIndexed(
                        items = uiState.recentProjects,
                        key = { _, item -> item.id }
                    ) { _, project ->
                        ProjectCard(
                            project = project,
                            isOffline = project.id in uiState.offlineProjectIds,
                            onOpen = { onOpenProject(project) },
                            onRename = {
                                renameTarget = project
                                renameText = project.name
                            },
                            onDelete = { deleteTarget = project }
                        )
                    }
                }
            }

            importState.progress?.let { ImportProgressOverlay(progress = it) }
        }
    }

    importState.largeFilePrompt?.let { prompt ->
        LargeFileDialog(prompt = prompt, onConfirm = onConfirmLargeFile, onDismiss = onDismissLargeFile)
    }

    // ── Rename Dialog ──
    renameTarget?.let { target ->
        val renameError = DashboardViewModel.validateProjectName(renameText)
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardBg,
            confirmButton = {
                TextButton(
                    enabled = renameError == null,
                    onClick = {
                        onRenameProject(target.id, renameText)
                        renameTarget = null
                    }
                ) { Text("Save", fontWeight = FontWeight.SemiBold, color = if (renameError == null) AccentGreen else TextDim) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            title = { Text("Rename project", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        isError = renameError != null,
                        shape = RoundedCornerShape(10.dp),
                        label = { Text("Project name", color = TextSecondary) },
                        supportingText = {
                            Text(
                                text = renameError ?: "Only the display name changes — project files stay untouched.",
                                color = if (renameError != null) StatusRed else TextDim,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = ChipBorder,
                            cursorColor = AccentGreen
                        )
                    )
                }
            }
        )
    }

    // ── Delete Dialog ──
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardBg,
            title = { Text("Remove project?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    text = "\"${target.name}\" will be removed from recent projects and its offline copy deleted. " +
                        "The original files on your device or Google Drive are not affected.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProject(target)
                    deleteTarget = null
                }) { Text("Remove", fontWeight = FontWeight.SemiBold, color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // ── Logout Dialog ──
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardBg,
            title = { Text("Sign out?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    text = "You'll need internet access to sign in again.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogoutClick()
                }) { Text("Sign out", fontWeight = FontWeight.SemiBold, color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

// ───────────────────────────────────────────── Top Bar

@Composable
private fun TopBar(isOnline: Boolean, onLogoutClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // RPAV logo
        Text(
            text = "RPAV",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif,
            color = TextPrimary,
            letterSpacing = 2.sp
        )

        // Profile / Logout chip
        Surface(
            onClick = onLogoutClick,
            shape = RoundedCornerShape(10.dp),
            color = ProfileBg,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "User",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = if (isOnline) "Online · Tap to sign out" else "Offline · Local projects only",
                    fontSize = 11.sp,
                    color = if (isOnline) TextSecondary else StatusAmber
                )
            }
        }
    }
}

// ───────────────────────────────────────────── Welcome Section

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeSection(
    onLocalFolderPickerClick: () -> Unit,
    onLocalFilePickerClick: () -> Unit,
    onDriveClick: () -> Unit,
    onOneDriveClick: () -> Unit
) {
    var showLocalOptions by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))

        // Welcome badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "WELCOME BACK",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // Heading
        Text(
            text = "Open a 3D project",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = TextPrimary
        )

        Spacer(Modifier.height(6.dp))

        // Subtitle
        Text(
            text = "Select a project folder from device storage, Google Drive or OneDrive. The GLB model and its related files are detected and loaded automatically.",
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(18.dp))

        // Action Buttons
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drive / URL button
            Button(
                onClick = onDriveClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkGreen
                ),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Google Drive",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // OneDrive
            Button(
                onClick = onOneDriveClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkGreen
                ),
                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "OneDrive",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Local storage: project folder or a single GLB file
            Box {
                Button(
                    onClick = { showLocalOptions = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonLightBg
                    ),
                    border = BorderStroke(1.dp, ChipBorder),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Load from device",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                DropdownMenu(
                    expanded = showLocalOptions,
                    onDismissRequest = { showLocalOptions = false },
                    containerColor = CardBg
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Project folder", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("GLB + related files", color = TextSecondary, fontSize = 12.sp)
                            }
                        },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = TextSecondary) },
                        onClick = {
                            showLocalOptions = false
                            onLocalFolderPickerClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Single GLB file", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("e.g. a file in Downloads", color = TextSecondary, fontSize = 12.sp)
                            }
                        },
                        leadingIcon = { Icon(Icons.Outlined.ViewInAr, contentDescription = null, tint = TextSecondary) },
                        onClick = {
                            showLocalOptions = false
                            onLocalFilePickerClick()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ───────────────────────────────────────────── Project Card

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectCard(
    project: ProjectItem,
    isOffline: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: avatar + name + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(AvatarGradientStart, AvatarGradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = project.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Rename",
                        tint = TextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove",
                        tint = TextDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Chips row: source, GLB size, related files
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip(text = sourceLabel(project.source))
                Chip(text = ByteFormat.format(project.glbSizeInBytes))
                if (project.relatedFileCount > 0) {
                    Chip(text = "+${project.relatedFileCount} related")
                }
            }

            Spacer(Modifier.height(10.dp))

            // Availability
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = if (isOffline) StatusGreen else StatusAmber
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = when {
                        isOffline -> "Available offline"
                        project.source == ProjectSource.GOOGLE_DRIVE || project.source == ProjectSource.ONEDRIVE -> "Download needed"
                        else -> "Reload needed"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Last opened ${formatLastOpened(project.lastOpenedAt)}",
                fontSize = 12.sp,
                color = TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ───────────────────────────────────────────── Chip

@Composable
private fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = ChipBg,
        border = BorderStroke(1.dp, ChipBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}

// ───────────────────────────────────────────── Empty State

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "No projects yet",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
            Text(
                text = "Load a project folder from your device or Google Drive to get started.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

// ───────────────────────────────────────────── Pickers

@Composable
fun rememberProjectFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(null) }
}

/** Any file type is offered: GLBs are often reported as application/octet-stream. */
@Composable
fun rememberGlbFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(arrayOf("*/*")) }
}

// ───────────────────────────────────────────── Helpers

private fun sourceLabel(source: ProjectSource): String = when (source) {
    ProjectSource.LOCAL -> "Local"
    ProjectSource.GOOGLE_DRIVE -> "Google Drive"
    ProjectSource.ONEDRIVE -> "OneDrive"
}

private fun formatLastOpened(millis: Long): String {
    val opened = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    val sameYear = opened.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val dayDiff = today.get(Calendar.DAY_OF_YEAR) - opened.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "today, $time"
        sameYear && dayDiff == 1 -> "yesterday, $time"
        sameYear -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}
