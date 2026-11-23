package com.example.kpkn.screens.technique.importer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.hub.TechniqueExerciseItem
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

private val ImportCream = Color(0xFFF2EEE6)
private val ImportDarkInk = Color(0xFF111111)

/** Hoja sin estado `.tc-im`; bloquea el cierre mientras la copia se confirma. */
@Composable
fun VideoImportSheet(
    state: VideoImportUiState,
    recentExercises: List<TechniqueExerciseItem>,
    onPickVideo: () -> Unit,
    onChooseRecent: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onWeightChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss, dismissible = !state.isImporting) {
        VideoImportContent(
            state, recentExercises, onPickVideo, onChooseRecent, onOpenCatalog, onWeightChange, onAnalyze,
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/** Cuerpo `.tc-im`: tarjeta de miniatura `.tc-thumb`, chips `.tc-fch` y carga opcional + CTA crema. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoImportContent(
    state: VideoImportUiState,
    recentExercises: List<TechniqueExerciseItem>,
    onPickVideo: () -> Unit,
    onChooseRecent: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onWeightChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier.testTag("technique-import-sheet"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Analizar un vídeo", style = KpknType.title, color = WizardColors.text)

        if (state.uri == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
                    .clickable(enabled = !state.isImporting, role = Role.Button, onClick = onPickVideo)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x16FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KpknLineIcons.PlayFilled, contentDescription = null, tint = ImportCream, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Elegir vídeo", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text("De tu galería · hasta 10 minutos", style = KpknType.caption, color = WizardColors.textMuted)
                }
                Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(18.dp))
            }
        } else {
            // Tarjeta de miniatura `.tc-thumb`
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0D0E12))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)),
            ) {
                if (state.thumbnail != null) {
                    Image(
                        bitmap = state.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0x22000000), Color(0xB8050507))),
                        ),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(KpknLineIcons.Play, contentDescription = null, tint = Color(0x55F2EEE6), modifier = Modifier.size(34.dp))
                    }
                }

                // Insignia de duración y metadatos abajo a la izquierda
                val metaText = when {
                    state.isReadingMetadata -> "Leyendo el vídeo…"
                    state.metadata != null -> buildVideoMetaBadge(state.metadata)
                    else -> "No se pudo leer el vídeo"
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                ) {
                    val durText = state.metadata?.let {
                        val totalSec = (it.durationMs / 1000L).coerceAtLeast(0L)
                        "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
                    }
                    if (durText != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xB8000000))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = durText,
                                style = KpknType.chip.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                                color = ImportCream,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(text = metaText, style = KpknType.caption, color = Color(0xCCFFFFFF))
                }

                if (!state.isImporting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .defaultMinSize(minHeight = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x99121216))
                            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClick = onPickVideo)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cambiar vídeo", style = KpknType.chip.copy(fontSize = 11.5.sp), color = ImportCream)
                    }
                }
            }
        }

        state.metadata?.warning?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }

        // Selección de ejercicio con chips `.tc-fch`
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("¿Qué ejercicio es?", style = KpknType.bodyStrong, color = WizardColors.text)
                state.exercise?.let {
                    Text(it.name, style = KpknType.caption, color = ImportCream)
                }
            }
            if (!state.isImporting && !state.isSelectionLocked) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentExercises.take(4).forEach { recent ->
                        val isSelected = state.exercise?.name.equals(recent.name, ignoreCase = true) ||
                            state.exercise?.id == recent.exerciseKey ||
                            state.exercise?.catalogDefinitionId == recent.exerciseKey
                        ImportFilterChip(
                            label = recent.name,
                            selected = isSelected,
                            onClick = { onChooseRecent(recent.exerciseKey) },
                            modifier = Modifier.semantics { contentDescription = "Elegir ejercicio reciente ${recent.name}" },
                        )
                    }
                    ImportFilterChip(
                        label = "Buscar en el catálogo",
                        selected = false,
                        onClick = onOpenCatalog,
                        modifier = Modifier.testTag("technique-import-catalog"),
                    )
                }
            }
        }

        // Carga opcional
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Carga (opcional)", style = KpknType.bodyStrong, color = WizardColors.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknNumberCell(
                    value = state.weightText,
                    input = if (state.isImporting || state.isSelectionLocked) KpknCellInput.ReadOnly else KpknCellInput.Text(onCommit = onWeightChange),
                    contentDescription = "Carga opcional en kilogramos",
                    modifier = Modifier.width(112.dp).testTag("technique-import-load"),
                    placeholder = "Opcional",
                )
                Text("kg", style = KpknType.unit, color = WizardColors.textMuted)
            }
        }

        if (state.isSelectionLocked && !state.isImporting) {
            Text(
                "Esta solicitud mantiene el ejercicio y la carga. Reintenta el análisis o cambia el vídeo para preparar otra.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        state.error?.let {
            Text(it, style = KpknType.body, color = WizardColors.danger, modifier = Modifier.testTag("technique-import-error"))
        }

        KpknPrimaryPill(
            text = if (state.isImporting) "Guardando y preparando…" else "Analizar",
            onClick = {
                // La celda confirma su borrador al perder el foco antes de capturar la semilla.
                focusManager.clearFocus(force = true)
                onAnalyze()
            },
            enabled = state.canAnalyze,
            modifier = Modifier.fillMaxWidth(),
            tag = "technique-import-analyze",
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ImportFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) ImportCream else Color(0x12FFFFFF))
            .border(1.dp, if (selected) ImportCream else Color(0x24FFFFFF), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KpknType.chip.copy(fontSize = 12.5.sp),
            color = if (selected) ImportDarkInk else WizardColors.text,
        )
    }
}

private fun buildVideoMetaBadge(meta: ImportedVideoMetadata): String {
    val orientation = when {
        meta.width != null && meta.height != null -> if (meta.height >= meta.width) "Vertical" else "Horizontal"
        else -> "Vídeo seleccionado"
    }
    val fps = meta.fps?.takeIf { it.isFinite() && it > 0.0 }?.let { "${it.roundToInt()} fps" }
    return listOfNotNull(orientation, fps).joinToString(" · ")
}
