package com.example.a3d_render.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3d_render.domain.model.ProjectItem
import com.example.a3d_render.domain.model.ProjectSource
import com.example.a3d_render.ui.theme.HeroDark
import com.example.a3d_render.ui.theme.HeroHighlight
import com.example.a3d_render.ui.theme.HeroLight
import com.example.a3d_render.ui.theme.HeroMid
import com.example.a3d_render.ui.theme.MonoAccentMid
import com.example.a3d_render.ui.theme.MonoAccentSoft
import com.example.a3d_render.ui.theme.MonoAccentStrong
import com.example.a3d_render.ui.theme.MonoOnSurfaceMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onLocalFolderPickerClick: () -> Unit,
    onOpenProject: (ProjectItem) -> Unit,
    onRenameProject: (projectId: String, newName: String) -> Unit,
    onDismissError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<ProjectItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onDismissError()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedMeshBackground()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { Spacer(Modifier.height(12.dp)) }
                item { DashboardHeader() }
                item {
                    PremiumHeroCard(
                        recentCount = uiState.recentProjects.size,
                        onPrimaryAction = onLocalFolderPickerClick
                    )
                }
                item {
                    SectionHeader(
                        title = "Quick actions",
                        subtitle = "Select a local folder containing a GLB"
                    )
                }
                item {
                    QuickActionLocalFolder(onClick = onLocalFolderPickerClick)
                }
                item {
                    SectionHeader(
                        title = "Recent projects",
                        subtitle = if (uiState.recentProjects.isEmpty()) {
                            "Your last 10 projects appear here"
                        } else {
                            "Tap to open • Long-press style menu for more"
                        }
                    )
                }
                if (uiState.recentProjects.isEmpty()) {
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(420)) + slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 3 }
                            )
                        ) {
                            EmptyRecentState()
                        }
                    }
                } else {
                    itemsIndexed(
                        items = uiState.recentProjects,
                        key = { _, item -> item.id }
                    ) { index, project ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 360,
                                    delayMillis = index * 45
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 360,
                                    delayMillis = index * 45,
                                    easing = FastOutSlowInEasing
                                ),
                                initialOffsetY = { it / 3 }
                            )
                        ) {
                            ModernRecentProjectCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onRename = {
                                    renameTarget = project
                                    renameText = project.name
                                }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }

            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                LoadingScrim()
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape = RoundedCornerShape(22.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = renameTarget ?: return@TextButton
                        onRenameProject(target.id, renameText)
                        renameTarget = null
                    }
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Rename project", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("Project name") }
                )
            }
        )
    }
}

// ───────────────────────────────────────────── Header

@Composable
private fun DashboardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "AIMS 3D",
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Visualization workspace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(42.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ───────────────────────────────────────────── Animated Mesh Background

@Composable
private fun AnimatedMeshBackground() {
    val transition = rememberInfiniteTransition(label = "mesh_bg")
    val angle1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb1_angle"
    )
    val angle2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb2_angle"
    )

    val orbColor1 = Color.White.copy(alpha = 0.06f)
    val orbColor2 = Color.White.copy(alpha = 0.04f)
    val orbColor3 = Color.White.copy(alpha = 0.03f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val cx1 = w * 0.5f + cos(Math.toRadians(angle1.toDouble())).toFloat() * w * 0.35f
        val cy1 = h * 0.25f + sin(Math.toRadians(angle1.toDouble())).toFloat() * h * 0.18f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbColor1, Color.Transparent),
                center = Offset(cx1, cy1),
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = Offset(cx1, cy1)
        )

        val cx2 = w * 0.2f + cos(Math.toRadians(angle2.toDouble() + 120)).toFloat() * w * 0.4f
        val cy2 = h * 0.7f + sin(Math.toRadians(angle2.toDouble() + 120)).toFloat() * h * 0.2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbColor2, Color.Transparent),
                center = Offset(cx2, cy2),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(cx2, cy2)
        )

        val cx3 = w * 0.8f + cos(Math.toRadians(angle1.toDouble() + 240)).toFloat() * w * 0.25f
        val cy3 = h * 0.85f + sin(Math.toRadians(angle1.toDouble() + 240)).toFloat() * h * 0.12f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(orbColor3, Color.Transparent),
                center = Offset(cx3, cy3),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(cx3, cy3)
        )
    }
}

// ───────────────────────────────────────────── Hero

@Composable
private fun PremiumHeroCard(recentCount: Int, onPrimaryAction: () -> Unit) {
    val animatedRecentCount by animateIntAsState(
        targetValue = recentCount,
        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        label = "recent_count_anim"
    )

    val transition = rememberInfiniteTransition(label = "hero_orb")
    val orbDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_orb_drift"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "hero_scale"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HeroDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPrimaryAction
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(HeroDark, HeroMid, HeroLight),
                        start = Offset.Zero,
                        end = Offset(1200f, 800f)
                    )
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val w = size.width
                val h = size.height
                val cx = w * (0.7f + orbDrift * 0.1f)
                val cy = h * (0.2f + orbDrift * 0.3f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = w * 0.6f
                    ),
                    radius = w * 0.6f,
                    center = Offset(cx, cy)
                )
                val cx2 = w * (0.15f - orbDrift * 0.05f)
                val cy2 = h * (0.85f - orbDrift * 0.1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(cx2, cy2),
                        radius = w * 0.45f
                    ),
                    radius = w * 0.45f,
                    center = Offset(cx2, cy2)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HeroHighlight)
                    )
                    Text(
                        text = "WELCOME BACK",
                        color = MonoOnSurfaceMuted,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "Open a 3D project",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp
                )

                Text(
                    text = "Load fast, navigate smoothly. Pick from local storage or Google Drive to begin immersive viewing.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = HeroHighlight,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "$animatedRecentCount in workspace",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Surface(
                        color = HeroHighlight,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                                tint = HeroDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Tap to load",
                                color = HeroDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────────────────────── Section header

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ───────────────────────────────────────────── Quick actions

@Composable
private fun QuickActionLocalFolder(onClick: () -> Unit) {
    QuickActionTile(
        modifier = Modifier.fillMaxWidth(),
        title = "Local folder",
        subtitle = "From device storage",
        icon = Icons.Outlined.Folder,
        tint = MonoAccentStrong,
        onClick = onClick
    )
}

@Composable
private fun QuickActionTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tile_scale"
    )

    Card(
        modifier = modifier
            .aspectRatio(1.05f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surface
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 600f)
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(tint, tint.copy(alpha = 0.78f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = HeroDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────── Recent project card

@Composable
private fun ModernRecentProjectCard(
    project: ProjectItem,
    onOpen: () -> Unit,
    onRename: () -> Unit
) {
    val accent = sourceAccent(project.source)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.6f))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent, accent.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = project.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(
                            text = sourceLabel(project.source),
                            tint = accent
                        )
                        Chip(
                            text = formatBytes(project.glbSizeInBytes),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = timeAgo(project.lastOpenedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontWeight = FontWeight.SemiBold,
            color = tint,
            fontSize = 11.sp
        )
    }
}

// ───────────────────────────────────────────── Empty state

@Composable
private fun EmptyRecentState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MonoAccentMid, MonoAccentSoft)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = HeroDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "No projects yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select a local folder above to load your first GLB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────── Loading scrim

@Composable
private fun LoadingScrim() {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "Importing project…",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
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

// ───────────────────────────────────────────── Helpers

private fun sourceAccent(source: ProjectSource): Color = when (source) {
    ProjectSource.LOCAL -> MonoAccentStrong
    ProjectSource.GOOGLE_DRIVE -> MonoAccentMid
}

private fun sourceLabel(source: ProjectSource): String = when (source) {
    ProjectSource.LOCAL -> "Local"
    ProjectSource.GOOGLE_DRIVE -> "Drive"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
}

private fun timeAgo(millis: Long): String {
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(millis))
    }
}
