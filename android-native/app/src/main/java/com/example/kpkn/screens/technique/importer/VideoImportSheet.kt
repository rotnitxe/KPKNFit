package com.example.kpkn.screens.technique.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.hub.TechniqueExerciseItem
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Hoja sin estado; bloquea el cierre mientras la copia se confirma. */
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/** Cuerpo separado de la ventana para verificar la hoja al 100 % y al 130 %. */
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
    Column(modifier.testTag("technique-import-sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Analizar un vídeo", style = KpknType.title, color = WizardColors.text)
        if (state.uri == null) {
            Text("Elige un vídeo de tu galería de hasta 10 minutos.", style = KpknType.body, color = WizardColors.textMuted)
        } else {
            Text(
                if (state.isReadingMetadata) "Leyendo el vídeo…" else state.metadata?.let { "Vídeo seleccionado · ${it.durationMs / 60_000}:${((it.durationMs / 1_000) % 60).toString().padStart(2, '0')} min" } ?: "No se pudo leer el vídeo",
                style = KpknType.caption, color = WizardColors.textMuted,
            )
        }
        if (!state.isImporting) KpknTextAction(if (state.uri == null) "Elegir vídeo" else "Cambiar vídeo", onPickVideo)
        state.metadata?.warning?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
        Text("¿Qué ejercicio es?", style = KpknType.bodyStrong, color = WizardColors.text)
        state.exercise?.let { Text(it.name, style = KpknType.body, color = WizardColors.text) }
        if (!state.isImporting && !state.isSelectionLocked) {
            recentExercises.take(4).forEach { recent ->
                KpknTextAction(
                    text = recent.name,
                    onClick = { onChooseRecent(recent.exerciseKey) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Elegir ejercicio reciente ${recent.name}" },
                )
            }
            KpknTextAction("Buscar en el catálogo", onOpenCatalog, Modifier.testTag("technique-import-catalog"))
        }
        Spacer(Modifier.height(4.dp))
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
        if (state.isSelectionLocked && !state.isImporting) {
            Text("Esta solicitud mantiene el ejercicio y la carga. Reintenta el análisis o cambia el vídeo para preparar otra.", style = KpknType.caption, color = WizardColors.textMuted)
        }
        state.error?.let { Text(it, style = KpknType.body, color = WizardColors.danger, modifier = Modifier.testTag("technique-import-error")) }
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
        Spacer(Modifier.height(8.dp))
    }
}
