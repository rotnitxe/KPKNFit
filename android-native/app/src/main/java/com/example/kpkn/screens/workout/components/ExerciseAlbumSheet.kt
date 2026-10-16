package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.media.AlbumEntry
import com.example.kpkn.data.media.AlbumFilter
import com.example.kpkn.data.media.DeferredMediaDelete
import com.example.kpkn.data.media.ExerciseAlbumModel
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import kotlinx.coroutines.launch
import com.example.kpkn.ui.design.KpknType

/** Etiquetas de prueba del álbum (LV8). */
internal const val AlbumRowTag = "album_row"
internal const val AlbumDeleteTag = "album_delete"
internal const val AlbumUndoTag = "album_undo"
internal const val AlbumSelectTag = "album_select"

/** Rojo de borrar del prototipo (A4). */
private val AlbumDeleteRed = Color(0xFFEF6F64)

/**
 * Álbum de un ejercicio (LV8, A4), abierto desde «Ver álbum» y el icono del álbum de «Modo visual»: sus fotos y vídeos ENTRE sesiones,
 * agrupados por fecha, con filtros y «Seleccionar». Los datos salen de `listForExercise` por las claves con las que se guardó el medio.
 * Borrar es aplazado con «Deshacer» ([DeferredMediaDelete]): lo definitivo ocurre al caducar el aviso o al salir del álbum.
 */
@Composable
internal fun ExerciseAlbumSheet(
    exerciseName: String,
    keys: List<String>,
    mediaCapture: WorkoutMediaCaptureController,
    onDismiss: () -> Unit,
) {
    var items by remember { mutableStateOf<List<WorkoutMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var efforts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var reloadTick by remember { mutableStateOf(0) }
    LaunchedEffect(keys, reloadTick) {
        try {
            val loaded = mediaCapture.listForExercise(keys)
            efforts = mediaCapture.effortLabelsFor(loaded)
            items = loaded
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Se conserva la última lista si falla una lectura.
        }
        loading = false
    }
    val scope = rememberCoroutineScope()
    val deferred = remember(mediaCapture) {
        mediaCapture.newDeferredDelete(timerScope = scope, onCommitted = { reloadTick += 1 })
    }
    // Salir del álbum borra ya lo que seguía en espera: «Deshacer» solo vale mientras el álbum está abierto.
    DisposableEffect(deferred) { onDispose { deferred.flush() } }
    KpknSheet(
        onDismissRequest = onDismiss,
        maxHeightFraction = 0.93f,
        stableHeightFraction = 0.93f,
    ) {
        AlbumSheetContent(
            subject = exerciseName,
            items = items,
            loading = loading,
            effortFor = { media -> efforts[media.id] },
            deferred = deferred,
            poseOverlayEnabled = mediaCapture.isPoseTrajectoryEnabled(),
            emptyText = "Todavía no hay fotos ni vídeos de este ejercicio.",
            modifier = Modifier.weight(1f),
        )
    }
}

/** El mismo álbum de LV8 desde catálogo, editor o ficha general, sin crear una sesión de captura ficticia. */
@Composable
internal fun ExerciseAlbumSheet(exerciseName: String, keys: List<String>, onDismiss: () -> Unit) {
    val repository = remember { com.example.kpkn.data.repository.WorkoutMediaRepository.getInstance() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<WorkoutMedia>>(emptyList()) }
    var efforts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    lateinit var deferred: DeferredMediaDelete
    deferred = remember(repository) {
        DeferredMediaDelete(scope = scope, commit = { batch ->
            repository.deleteAlbumMediaAsync(batch).invokeOnCompletion { error ->
                if (error != null) deferred.restoreFailed(batch)
                scope.launch { reload += 1 }
            }
        })
    }
    LaunchedEffect(keys, reload) {
        try {
            val loaded = repository.listForExerciseKeys(keys)
            efforts = repository.effortLabelsForMedia(loaded)
            items = loaded
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Conserva la última lectura. */ }
        loading = false
    }
    DisposableEffect(deferred) { onDispose { deferred.flush() } }
    KpknSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.93f, stableHeightFraction = 0.93f) {
        AlbumSheetContent(
            subject = exerciseName, items = items, loading = loading, effortFor = { efforts[it.id] },
            deferred = deferred, poseOverlayEnabled = false,
            emptyText = "Todavía no hay fotos ni vídeos de este ejercicio.", modifier = Modifier.weight(1f),
        )
    }
}

/**
 * El álbum con el aspecto de A4: «Álbum» con su subtítulo («Sentadilla trasera · 9 registros»), «Seleccionar», los filtros «Todo · Fotos ·
 * Vídeos», los registros por fecha (la serie, kg × reps, el esfuerzo si se sabe, el tipo y la hora, «Marca» si es récord y la duración de los
 * vídeos), el borrado con «Deshacer» y la vista previa a pantalla completa. Lo comparten el álbum del ejercicio y el de la sesión.
 */
@Composable
internal fun AlbumSheetContent(
    subject: String,
    items: List<WorkoutMedia>,
    loading: Boolean,
    effortFor: (WorkoutMedia) -> String?,
    deferred: DeferredMediaDelete,
    poseOverlayEnabled: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(AlbumFilter.ALL) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var preview by remember { mutableStateOf<WorkoutMedia?>(null) }
    val pending by deferred.pending.collectAsState()
    val committedIds by deferred.committedIds.collectAsState()
    val hidden = committedIds + pending?.items?.map { it.id }.orEmpty()
    val days = remember(items, filter, hidden, effortFor) { ExerciseAlbumModel.days(items, filter, hidden, effortFor = effortFor) }
    val total = items.count { it.id !in hidden }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Álbum", style = KpknType.dialogTitle, color = WizardColors.text, maxLines = 1)
                    Text(
                        ExerciseAlbumModel.heading(subject, total),
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (total > 0) {
                    AlbumPill(
                        text = if (selecting) "Cancelar" else "Seleccionar",
                        onClick = {
                            selecting = !selecting
                            selected = emptySet()
                        },
                        modifier = Modifier.testTag(AlbumSelectTag),
                    )
                }
            }
            KpknSegmentedControl(
                options = AlbumFilter.values().map { it.label },
                selectedIndex = filter.ordinal,
                onSelect = { filter = AlbumFilter.values()[it] },
                size = KpknSegmentedSize.Compact,
            )
            if (days.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    Text(
                        text = when {
                            loading -> "Cargando…"
                            total == 0 -> emptyText
                            else -> "No hay ${if (filter == AlbumFilter.PHOTOS) "fotos" else "vídeos"} en este álbum."
                        },
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    days.forEach { day ->
                        item(key = "dia:${day.header}") {
                            Text(
                                day.header,
                                style = KpknType.eyebrow,
                                color = WizardColors.textFaint,
                                modifier = Modifier.padding(top = 6.dp),
                                maxLines = 1,
                            )
                        }
                        items(day.entries, key = { it.media.id }) { entry ->
                            AlbumRow(
                                entry = entry,
                                selecting = selecting,
                                selected = entry.media.id in selected,
                                onOpen = {
                                    if (selecting) {
                                        selected = if (entry.media.id in selected) selected - entry.media.id else selected + entry.media.id
                                    } else {
                                        preview = entry.media
                                    }
                                },
                                onDelete = { deferred.schedule(listOf(entry.media)) },
                            )
                        }
                    }
                }
            }
        }

        // Pie: con «Seleccionar», «Cancelar» y «Borrar (n)»; si no, el aviso de lo que se acaba de borrar con «Deshacer».
        if (selecting) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KpknSecondaryPill(
                    text = "Cancelar",
                    onClick = {
                        selecting = false
                        selected = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                )
                KpknDangerPill(
                    text = "Borrar (${selected.size})",
                    onClick = {
                        val chosen = items.filter { it.id in selected }
                        deferred.schedule(chosen)
                        selecting = false
                        selected = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selected.isNotEmpty(),
                )
            }
        } else {
            pending?.let { current ->
                UndoBar(
                    label = deletedLabel(current.items),
                    onUndo = { deferred.undo() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                )
            }
        }
    }

    preview?.let { item ->
        WorkoutMediaPreviewDialog(
            media = item,
            onDismiss = { preview = null },
            onDelete = {
                deferred.schedule(listOf(item))
                preview = null
            },
            poseOverlayEnabled = poseOverlayEnabled,
        )
    }
}

/** «Vídeo borrado», «Foto borrada» o «3 elementos borrados». */
internal fun deletedLabel(items: List<WorkoutMedia>): String = when {
    items.size != 1 -> "${items.size} elementos borrados"
    items.single().kind == WorkoutMediaKind.VIDEO -> "Vídeo borrado"
    else -> "Foto borrada"
}

@Composable
private fun AlbumPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .border(1.dp, Color(0xFFF2EEE6).copy(alpha = 0.12f), KpknShape.pill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = KpknType.chip, color = WizardColors.text, maxLines = 1)
        }
    }
}

/** Un registro (A4): miniatura de 64 dp, «Serie 3 · 95 kg × 6», «RIR 2 · Vídeo · 18:42» y, a la derecha, borrar o la casilla de selección. */
@Composable
private fun AlbumRow(
    entry: AlbumEntry,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AlbumRowTag)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkoutMediaThumb(
            media = entry.media,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp)),
            onClick = onOpen,
        )
        Column(modifier = Modifier.weight(1f)) {
            // Con la letra grande el título y el detalle pasan a dos líneas antes que cortarse: es una lista, no la tarjeta.
            Text(entry.title, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // El detalle y, si es un récord, «Marca» en el amarillo de la energía: un solo texto que se parte con naturalidad.
            val detail = buildAnnotatedString {
                append(entry.detail)
                if (entry.isMark) {
                    append(" · ")
                    withStyle(SpanStyle(color = SymbolPalette.energia)) { append("Marca") }
                }
            }
            Text(detail, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selecting) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) WizardColors.text else KpknSurface.inset, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                    .semantics { contentDescription = if (selected) "Elegido" else "Sin elegir" },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        KpknLineIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = WizardColors.ctaContent,
                    )
                }
            }
        } else {
            val kind = if (entry.media.kind == WorkoutMediaKind.VIDEO) "vídeo" else "foto"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag(AlbumDeleteTag)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDelete)
                    .semantics {
                        val target = entry.title.substringBefore(" ·")
                        contentDescription = if (target.startsWith("Serie")) "Borrar $kind de la ${target.lowercase()}" else "Borrar $kind"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161616), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        KpknLineIcons.Trash,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AlbumDeleteRed,
                    )
                }
            }
        }
    }
}

/** Aviso inferior del álbum (A4): «Vídeo borrado» y «Deshacer», en una barra de 48 dp sobre el fondo del álbum. */
@Composable
private fun UndoBar(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F1F1F), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFF2EEE6).copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = KpknType.action.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = WizardColors.text, maxLines = 1)
        KpknTextAction(text = "Deshacer", onClick = onUndo, modifier = Modifier.testTag(AlbumUndoTag))
    }
}
