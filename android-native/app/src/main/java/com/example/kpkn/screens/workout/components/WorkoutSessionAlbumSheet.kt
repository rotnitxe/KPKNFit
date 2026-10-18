package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.data.media.DeferredMediaDelete
import com.example.kpkn.data.media.ExerciseAlbumModel
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Álbum de la sesión (el que abren el relator y el icono de álbum fuera de «Modo visual»): con el mismo aspecto que el del ejercicio
 * (LV8, A4), con los medios de la sesión agrupados por fecha, los filtros, «Seleccionar» y el borrado con «Deshacer». Borrar es aplazado:
 * [onDelete] solo se llama al caducar el aviso o al salir del álbum, nunca antes.
 */
@Composable
fun WorkoutSessionAlbumSheet(
    media: List<WorkoutMedia>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit = {},
    poseOverlayEnabled: Boolean = false,
    onOpenAnalysis: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val latestDelete by rememberUpdatedState(onDelete)
    val deferred = remember {
        DeferredMediaDelete(scope = scope, commit = { items -> items.forEach { latestDelete(it.id) } })
    }
    // Salir del álbum borra ya lo que seguía en espera: «Deshacer» solo vale mientras el álbum está abierto.
    DisposableEffect(deferred) { onDispose { deferred.flush() } }
    KpknSheet(
        onDismissRequest = onDismiss,
        maxHeightFraction = 0.93f,
        stableHeightFraction = 0.93f,
    ) {
        AlbumSheetContent(
            subject = "Esta sesión",
            items = media,
            loading = false,
            effortFor = { null },
            deferred = deferred,
            poseOverlayEnabled = poseOverlayEnabled,
            onOpenAnalysis = onOpenAnalysis,
            emptyText = "Todavía no hay fotos ni vídeos en esta sesión.",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun WorkoutMediaPreviewDialog(
    media: WorkoutMedia,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    poseOverlayEnabled: Boolean = false,
    onOpenAnalysis: ((String) -> Unit)? = null,
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
            if (media.kind == WorkoutMediaKind.VIDEO && onOpenAnalysis != null) {
                val repository = remember { com.example.kpkn.data.repository.VideoAnalysisRepository.getInstance() }
                val analysis by remember(media.id, repository) { repository.observeByMedia(media.id) }
                    .collectAsStateWithLifecycle(initialValue = null)
                if (analysis != null) {
                    com.example.kpkn.ui.design.KpknTextAction("Ver análisis", onClick = {
                        onDismiss()
                        onOpenAnalysis(media.id)
                    })
                }
            }
            // El pie de la vista previa: el ejercicio, la carga con coma decimal y «Marca» si es un récord.
            val caption = listOfNotNull(
                media.exerciseName?.takeIf { it.isNotBlank() },
                ExerciseAlbumModel.loadLabel(media.weightKg, media.reps),
                "Marca".takeIf { media.isPr },
            ).joinToString(" · ")
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
