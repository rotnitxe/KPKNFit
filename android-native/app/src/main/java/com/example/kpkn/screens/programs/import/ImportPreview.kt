package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.onboarding.entreno.EntrenoTextAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Pausa antes de buscar en el catálogo mientras la persona escribe. */
private const val SEARCH_DEBOUNCE_MS = 250L

/** Cómo se ve un ejercicio importado: reconocido (con su configuración), propio o con dudas sin decidir. */
internal enum class ImportExerciseState { CHOSEN, OWN, UNDECIDED }

/** Estado de un ejercicio según sus decisiones: sin clave = con dudas; clave con null = ejercicio propio. */
internal fun importExerciseStateOf(rawName: String, resolutions: Map<String, String?>): ImportExerciseState = when {
    !resolutions.containsKey(rawName) -> ImportExerciseState.UNDECIDED
    resolutions[rawName] == null -> ImportExerciseState.OWN
    else -> ImportExerciseState.CHOSEN
}

/** Sesiones por semana del programa importado: la semana con más días de entreno. */
internal fun importSessionsPerWeek(imported: ImportedProgramDraft): Int = imported.weeks.maxOfOrNull { it.days.size } ?: 0

/** La prescripción en una línea: «4 × 6–8 · 120 kg · RPE 8». */
internal fun importPrescriptionOf(exercise: ImportedExercise): String {
    val reps = if (exercise.repsMin == exercise.repsMax) "${exercise.repsMin}" else "${exercise.repsMin}–${exercise.repsMax}"
    val parts = mutableListOf("${exercise.sets} × $reps")
    exercise.loadKg?.let { parts += "${decimalText(it)} kg" }
    exercise.percent1Rm?.let { parts += "${decimalText(it)} %" }
    exercise.rpe?.let { parts += "RPE ${decimalText(it)}" }
    exercise.rir?.let { parts += "$it RIR" }
    return parts.joinToString(" · ")
}

/** Material de un ejercicio del catálogo en español; vacío si no se conoce. */
internal fun importEquipmentLabelOf(equipmentId: String?): String = when (equipmentId) {
    "barbell" -> "Barra"
    "dumbbells" -> "Mancuernas"
    "cable" -> "Polea"
    "machine" -> "Máquina"
    "smith_machine" -> "Smith"
    "kettlebell" -> "Kettlebell"
    "band" -> "Banda"
    "bodyweight" -> "Peso corporal"
    "hex_bar" -> "Barra hexagonal"
    else -> ""
}

private fun decimalText(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString().replace('.', ',')

/**
 * La vista previa de un programa importado: semanas, días y ejercicios con su prescripción. Cada ejercicio muestra si
 * está reconocido (✓), es propio o tiene dudas («Elegir»). [openedName] es el ejercicio cuyo panel «Elegir» está abierto.
 * Lo usan el paso del alta y la pantalla de Programas: no escribe nada por sí misma, solo reenvía las decisiones.
 */
@Composable
internal fun ImportPreviewWeeks(
    imported: ImportedProgramDraft,
    resolutions: Map<String, String?>,
    openedName: String?,
    onOpen: (String?) -> Unit,
    onPick: (rawName: String, configId: String?) -> Unit,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    namesOf: suspend (Set<String>) -> Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val chosenIds = resolutions.values.filterNotNull().toSet()
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(chosenIds) {
        names = try {
            namesOf(chosenIds)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            emptyMap()
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        imported.weeks.sortedBy { it.index }.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Semana ${week.index}", style = WizardTypography.eyebrow, color = WizardColors.textMuted)
                week.days.forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(day.name, style = WizardTypography.controlLabel, color = WizardColors.text)
                        day.exercises.forEach { exercise ->
                            val open = openedName == exercise.rawName
                            val chosenId = resolutions[exercise.rawName]
                            ImportExerciseRow(
                                exercise = exercise,
                                state = importExerciseStateOf(exercise.rawName, resolutions),
                                shownName = chosenId?.let { names[it] } ?: exercise.rawName,
                                onToggle = { onOpen(if (open) null else exercise.rawName) },
                            )
                            if (open) {
                                ImportChoicePanel(
                                    rawName = exercise.rawName,
                                    suggest = suggest,
                                    search = search,
                                    onPick = { configId ->
                                        onPick(exercise.rawName, configId)
                                        onOpen(null)
                                    },
                                    onClose = { onOpen(null) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Los avisos del analizador («N filas no se entendieron») en una línea; al desplegarla, cada fila. Sin caja.
 */
@Composable
internal fun ImportWarningsNote(warnings: List<String>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (warnings.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (warnings.size == 1) "1 fila no se entendió" else "${warnings.size} filas no se entendieron",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
            EntrenoTextAction(
                label = if (expanded) "Ocultar las filas" else "Ver las filas",
                onClick = { expanded = !expanded },
            )
            if (expanded) {
                warnings.forEach { warning ->
                    Text(warning, style = WizardTypography.note, color = WizardColors.textMuted)
                }
            }
        }
    }
}

/** Una fila de ejercicio: el pictograma, el nombre (reconocido o tal como vino), la prescripción y su estado a la derecha. */
@Composable
private fun ImportExerciseRow(
    exercise: ImportedExercise,
    state: ImportExerciseState,
    shownName: String,
    onToggle: () -> Unit,
) {
    val pattern = remember(shownName) { exercisePatternOf(shownName) }
    val stateText = when (state) {
        ImportExerciseState.CHOSEN -> "Reconocido"
        ImportExerciseState.OWN -> "Propio"
        ImportExerciseState.UNDECIDED -> "Sin decidir"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Elegir ejercicio", onClick = onToggle)
            .semantics { stateDescription = stateText }
            .testTag("import-exercise-${exercise.rawName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PatternGlyph(pattern = pattern, accent = SymbolPalette.musculo, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(shownName, style = WizardTypography.controlValue, color = WizardColors.text)
            Text(importPrescriptionOf(exercise), style = WizardTypography.note, color = WizardColors.textMuted)
        }
        when (state) {
            ImportExerciseState.CHOSEN -> Text(
                "✓",
                style = WizardTypography.controlLabel,
                color = SymbolPalette.ok,
                modifier = Modifier.semantics { contentDescription = "Reconocido" },
            )
            ImportExerciseState.OWN -> Text("Propio", style = WizardTypography.note, color = WizardColors.textMuted)
            ImportExerciseState.UNDECIDED -> Text("Elegir", style = WizardTypography.controlLabel, color = SymbolPalette.energia)
        }
    }
}

/**
 * El panel de vidrio de «Elegir»: las tres sugerencias del emparejador, un campo para buscar en el catálogo y
 * «Dejarlo como está» (ejercicio propio). Vidrio neutro y tenue sobre la página; sin botones de relleno.
 */
@Composable
private fun ImportChoicePanel(
    rawName: String,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onPick: (configId: String?) -> Unit,
    onClose: () -> Unit,
) {
    var suggestions by remember(rawName) { mutableStateOf<List<ExerciseNameCandidate>>(emptyList()) }
    var query by rememberSaveable(rawName) { mutableStateOf("") }
    var results by remember(rawName) { mutableStateOf<List<ExerciseNameCandidate>>(emptyList()) }
    LaunchedEffect(rawName) {
        suggestions = try {
            suggest(rawName)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            emptyList()
        }
    }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.isEmpty()) {
            results = emptyList()
        } else {
            delay(SEARCH_DEBOUNCE_MS)
            results = try {
                search(text)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                emptyList()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WizardColors.glassFill)
            .border(1.dp, WizardColors.glassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("import-choice-panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Elige el ejercicio", style = WizardTypography.controlLabel, color = WizardColors.text)
        Text("Tu archivo dice «$rawName».", style = WizardTypography.note, color = WizardColors.textMuted)
        suggestions.forEach { candidate ->
            ImportCandidateRow(candidate = candidate, onClick = { onPick(candidate.configurationId) })
        }
        SetupFormTextField(
            value = query,
            onValueChange = { query = it },
            label = "Buscar en el catálogo",
            testTag = "import-choice-search",
        )
        results.forEach { candidate ->
            ImportCandidateRow(candidate = candidate, onClick = { onPick(candidate.configurationId) })
        }
        EntrenoTextAction(
            label = "Dejarlo como está (ejercicio propio)",
            onClick = { onPick(null) },
            modifier = Modifier.testTag("import-choice-own"),
        )
        EntrenoTextAction(label = "Cancelar", onClick = onClose)
    }
}

@Composable
private fun ImportCandidateRow(candidate: ExerciseNameCandidate, onClick: () -> Unit) {
    val equipment = importEquipmentLabelOf(candidate.equipmentId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("import-candidate-${candidate.configurationId}"),
    ) {
        Text(candidate.displayName, style = WizardTypography.controlValue, color = WizardColors.text)
        if (equipment.isNotEmpty()) {
            Text(equipment, style = WizardTypography.note, color = WizardColors.textMuted)
        }
    }
}
