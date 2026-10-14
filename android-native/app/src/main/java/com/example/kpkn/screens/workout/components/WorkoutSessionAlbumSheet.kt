package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Álbum de la sesión (LS6): una hoja KPKN con la rejilla de fotos y vídeos; sin medios, un estado vacío. Cada medio se abre en
 * [WorkoutMediaPreviewDialog].
 */
@Composable
fun WorkoutSessionAlbumSheet(
    media: List<WorkoutMedia>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit = {},
    poseOverlayEnabled: Boolean = false,
) {
    var preview by remember { mutableStateOf<WorkoutMedia?>(null) }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Álbum de esta sesión",
                style = KpknType.title,
                color = WizardColors.text,
            )
            if (media.isEmpty()) {
                KpknEmptyState(
                    icon = KpknLineIcons.Layers,
                    title = "Todavía no hay fotos ni vídeos",
                    body = "Las fotos y los vídeos de esta sesión aparecen aquí.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
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
            val file by produceState<File?>(null, media.filePath, media.kind) {
                value = null
                value = withContext(Dispatchers.IO) { File(media.filePath).takeIf { it.isFile } }
            }
            val previewFile by produceState<File?>(null, media.filePath, media.thumbPath, media.kind) {
                value = null
                value = withContext(Dispatchers.IO) { mediaPreviewFile(media) }
            }
            val videoFile = file
            if (media.kind == WorkoutMediaKind.VIDEO && videoFile != null) {
                WorkoutVideoPlayer(
                    file = videoFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    poseOverlayEnabled = poseOverlayEnabled,
                    poseSidecarPath = media.poseTrackPath,
                )
            } else {
                LocalMediaImage(
                    source = rememberLocalMediaImageSource(file ?: previewFile),
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
