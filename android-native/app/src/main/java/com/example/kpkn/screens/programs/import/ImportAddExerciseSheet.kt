package com.example.kpkn.screens.programs.importer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/** Marcas de prueba de la hoja «Añadir ejercicio» de la isla (G3). */
internal const val IMPORT_ADD_SHEET_TAG = "import-add-sheet"
internal const val IMPORT_ADD_SCRIM_TAG = "import-add-scrim"

/** Un día al que puede ir el ejercicio nuevo: su id y su texto («Semana 2 · Día 1 — Pierna» si el programa tiene varias semanas). */
internal data class ImportAddTarget(val dayId: String, val label: String)

/** Todos los días del programa como destinos de «Añadir ejercicio»; con varias semanas, cada texto lleva la suya. */
internal fun importAddTargetsOf(imported: ImportedProgramDraft): List<ImportAddTarget> {
    val weeks = imported.weeks.sortedBy { it.index }
    return weeks.flatMap { week ->
        week.days.mapIndexed { position, day ->
            val label = importMoveLabelOf(position, day.name)
            ImportAddTarget(day.id, if (weeks.size > 1) "Semana ${week.index} · $label" else label)
        }
    }
}

/**
 * «Añadir ejercicio» de la isla de abajo (G3): el catálogo contextual del importador. Una hoja sobre la pantalla con el panel de añadir
 * (búsqueda en el catálogo o nombre propio, series y repeticiones) y «Añadir a:», que parte del día del ejercicio abierto o, sin
 * ninguno, del primero del programa. Al añadir se aplica `AddExercise` en ese día, deja deshacer y la hoja se cierra. Tocar el velo o
 * Atrás la cierra sin cambiar nada.
 */
@Composable
internal fun ImportAddExerciseSheet(
    imported: ImportedProgramDraft,
    contextDayId: String?,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onEdit: (ImportEditOp) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets = remember(imported) { importAddTargetsOf(imported) }
    var target by rememberSaveable { mutableStateOf(contextDayId?.takeIf { id -> targets.any { it.dayId == id } } ?: targets.firstOrNull()?.dayId) }
    // Si el día elegido desaparece del programa (se quitó con la hoja abierta), vuelve al primero.
    val chosen = target?.takeIf { id -> targets.any { it.dayId == id } } ?: targets.firstOrNull()?.dayId
    BackHandler(onBack = onClose)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(IMPORT_ADD_SCRIM_TAG)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 56.dp)
                .imePadding()
                .testTag(IMPORT_ADD_SHEET_TAG)
                .semantics { paneTitle = "Añadir ejercicio" }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF0E0E0E))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                // Lo de dentro no deja pasar el toque al velo, sin fundir la semántica de sus hijos.
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(2.dp)),
            )
            ImportAddExercisePanel(
                key = "island",
                search = search,
                onAdd = { name, configId, sets, repsMin, repsMax ->
                    chosen?.let { dayId ->
                        onEdit(ImportEditOp.AddExercise(dayId, name, sets, repsMin, repsMax, configId = configId, decided = true))
                    }
                },
                onClose = onClose,
                leading = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("import-add-targets")) {
                        Text("Añadir a:", style = WizardTypography.note, color = WizardColors.textMuted)
                        targets.forEach { option ->
                            val selected = option.dayId == chosen
                            ImportTextAction(
                                label = if (selected) "✓ ${option.label}" else option.label,
                                onClick = { target = option.dayId },
                                modifier = Modifier
                                    .testTag("import-add-sheet-day-${option.dayId}")
                                    .semantics { stateDescription = if (selected) "elegido" else "no elegido" },
                            )
                        }
                    }
                },
            )
        }
    }
}
