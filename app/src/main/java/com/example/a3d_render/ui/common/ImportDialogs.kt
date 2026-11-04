package com.example.a3d_render.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a3d_render.domain.project.GlbSizePolicy
import com.example.a3d_render.util.ByteFormat

private val CardBg = Color(0xFF162230)
private val Accent = Color(0xFF00C853)
private val TextPrimary = Color(0xFFE8EDF2)
private val TextSecondary = Color(0xFF8A9BB0)
private val Warning = Color(0xFFFFB300)

/** Full-screen scrim blocking input while a project is scanned, copied or downloaded. */
@Composable
fun ImportProgressOverlay(progress: ImportProgress) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBg, shadowElevation = 16.dp) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val fraction = progress.fraction
                if (fraction == null) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent, strokeWidth = 3.dp)
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
                Text(
                    text = progress.message,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LargeFileDialog(prompt: LargeFilePrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = CardBg,
        title = { Text("Large model", fontWeight = FontWeight.Bold, color = Warning) },
        text = {
            Text(
                text = "\"${prompt.projectName}\" is ${ByteFormat.format(prompt.sizeBytes)}, above the supported " +
                    "limit of ${ByteFormat.format(GlbSizePolicy.RECOMMENDED_MAX_BYTES)}. It may load slowly or fail " +
                    "on devices with 4 GB RAM. Open it anyway?",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Open anyway", fontWeight = FontWeight.SemiBold, color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
