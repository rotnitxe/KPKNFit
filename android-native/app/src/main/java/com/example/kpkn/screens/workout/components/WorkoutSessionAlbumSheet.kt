package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType
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
import androidx.compose.ui.platform.testTag
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
    captureActions: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val latestDelete by rememberUpdatedState(onDelete)
    val deferred = remember {
        DeferredMediaDelete(scope = scope, commit = { items -> items.forEach { latestDelete(it.id) } })
    }
    // Salir del álbum borra ya lo que seguía en espera: «Deshacer» solo vale mientras el álbum está abierto.
    DisposableEffect(deferred) { onDispose { deferred.flush() } }
    KpknSheet(
        modifier = Modifier.testTag("live-session-album"),
        onDismissRequest = onDismiss,
        maxHeightFraction = 0.93f,
        stableHeightFraction = 0.93f,
    ) {
        captureActions?.invoke()
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
    if (media.kind == WorkoutMediaKind.PHOTO) {
        WorkoutPhotoViewer(
            path = media.filePath,
            title = media.exerciseName?.takeIf { it.isNotBlank() } ?: "Foto de la sesión",
            caption = listOfNotNull(media.setIndex?.let { "Serie ${it + 1}" },
                ExerciseAlbumModel.loadLabel(media.weightKg, media.reps), "Marca".takeIf { media.isPr })
                .joinToString(" · "),
            createdAtMs = media.createdAtMs,
            onDismiss = onDismiss,
            onDelete = onDelete,
        )
        return
    }
    var entered by remember(media.id) { mutableStateOf(false) }
    var zoom by remember(media.id) { mutableStateOf(1f) }
    var pan by remember(media.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val reduced = wizardReducedMotion()
    val entry by animateFloatAsState(if (entered) 1f else .94f, tween(if (reduced) 0 else 300), label = "album-photo-expand")
    LaunchedEffect(media.id) { entered = true }
    val transform = rememberTransformableState { factor, delta, _ ->
        zoom = (zoom * factor).coerceIn(1f, 4f)
        pan = if (zoom <= 1f) androidx.compose.ui.geometry.Offset.Zero else pan + delta
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar visor", onClick = onDismiss)
                Column(Modifier.weight(1f)) {
                    media.exerciseName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    val caption = listOfNotNull(
                        media.setIndex?.let { "Serie " + (it + 1) },
                        ExerciseAlbumModel.loadLabel(media.weightKg, media.reps),
                        "Marca".takeIf { media.isPr },
                    ).joinToString(" · ")
                    if (caption.isNotBlank()) Text(caption, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            val file by produceState<File?>(null, media.filePath, media.kind) {
                value = null
                value = withContext(Dispatchers.IO) { File(media.filePath).takeIf { it.isFile } }
            }
            val previewFile by produceState<File?>(null, media.filePath, media.thumbPath, media.kind) {
                value = null
                value = withContext(Dispatchers.IO) { mediaPreviewFile(media) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF101010))
                .graphicsLayer { scaleX = entry; scaleY = entry; alpha = if (reduced) 1f else entry }, contentAlignment = Alignment.Center) {
                val videoFile = file
                if (media.kind == WorkoutMediaKind.VIDEO && videoFile != null) {
                    WorkoutVideoPlayer(file = videoFile, modifier = Modifier.fillMaxSize(), poseOverlayEnabled = poseOverlayEnabled, poseSidecarPath = media.poseTrackPath)
                } else {
                    LocalMediaImage(
                        source = rememberLocalMediaImageSource(file ?: previewFile), contentDescription = media.exerciseName,
                        modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                            scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                        }, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )

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

                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (media.kind == WorkoutMediaKind.PHOTO) {
                    HeaderSheetAction(if (zoom > 1f) "Ajustar" else "Ampliar", { zoom = if (zoom > 1f) 1f else 2f; pan = androidx.compose.ui.geometry.Offset.Zero }, Modifier.weight(1f))
                }
                onDelete?.let { HeaderSheetAction("Borrar", it, Modifier.weight(1f)) }
            }
        }
    }
}
