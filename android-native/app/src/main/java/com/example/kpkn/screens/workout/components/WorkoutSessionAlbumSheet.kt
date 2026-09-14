package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionAlbumSheet(
    media: List<WorkoutMedia>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit = {},
    poseOverlayEnabled: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var preview by remember { mutableStateOf<WorkoutMedia?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Álbum de esta sesión",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (media.isEmpty()) {
                Text(
                    "Todavía no hay fotos ni vídeos en esta sesión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(media, key = { it.id }) { item ->
                        WorkoutMediaThumb(
                            media = item,
                            modifier = Modifier.size(96.dp),
                            onClick = { preview = item },
                        )
                    }
                }
            }
        }
    }
    preview?.let { item ->
        WorkoutMediaPreviewDialog(
            media = item,
            onDismiss = { preview = null },
            onDelete = {
                onDelete(item.id)
                preview = null
            },
            poseOverlayEnabled = poseOverlayEnabled,
        )
    }
}

@Composable
internal fun WorkoutMediaPreviewDialog(
    media: WorkoutMedia,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    poseOverlayEnabled: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val file = File(media.filePath)
            if (media.kind == WorkoutMediaKind.VIDEO && file.isFile) {
                WorkoutVideoPlayer(
                    file = file,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    poseOverlayEnabled = poseOverlayEnabled,
                    poseSidecarPath = media.poseTrackPath,
                )
            } else {
                coil.compose.AsyncImage(
                    model = file.takeIf { it.isFile } ?: mediaPreviewFile(media),
                    contentDescription = media.exerciseName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            val caption = buildString {
                media.exerciseName?.let { append(it) }
                if (media.weightKg != null && media.reps != null) {
                    if (isNotEmpty()) append(" · ")
                    append("${media.weightKg} kg × ${media.reps}")
                }
                if (media.isPr) {
                    if (isNotEmpty()) append(" · ")
                    append("PR")
                }
            }
            if (caption.isNotBlank()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White)
            }
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
            if (onDelete != null) {
                androidx.compose.material3.TextButton(onClick = onDelete) {
                    Text("Borrar")
                }
            }
        }
    }
}
