package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ImportDraftEditor
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

/** Un número con coma decimal, sin cero final cuando es entero: «120», «2,5». */
internal fun decimalText(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString().replace('.', ',')

/** Un día al que se puede mover un ejercicio: su id, el texto de la opción («Día 2 — Pierna») y sus ejercicios. */
internal data class ImportMoveTarget(val dayId: String, val label: String, val exerciseCount: Int)

/**
 * El texto de una opción de «Mover a otro día»: el nombre del día, con «Día N —» delante solo si el nombre no lo trae (los
 * días leídos de un texto ya llevan «Día 2 — Pierna»).
 */
internal fun importMoveLabelOf(position: Int, dayName: String): String =
    if (dayName.startsWith("Día")) dayName else "Día ${position + 1} — $dayName"

/**
 * La vista previa de un programa importado: semanas, días y ejercicios con su prescripción. Cada ejercicio muestra si
 * está reconocido (✓), es propio o tiene dudas («Elegir»). Tocarlo abre su panel de ajuste ([openedExerciseId] es el
 * único abierto). Lo usan el paso del alta y la pantalla de Programas: no guarda nada por sí misma; reenvía cada edición
 * como [ImportEditOp] a quien la muestra.
 */
@Composable
internal fun ImportPreviewWeeks(
    imported: ImportedProgramDraft,
    resolutions: Map<String, String?>,
    openedExerciseId: String?,
    onOpen: (String?) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
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
    // Cuántas veces aparece cada nombre en todo el borrador: una decisión vale para todas.
    val nameCounts = remember(imported) {
        imported.weeks.flatMap { week -> week.days.flatMap { it.exercises } }.groupingBy { it.rawName }.eachCount()
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        imported.weeks.sortedBy { it.index }.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Semana ${week.index}", style = WizardTypography.eyebrow, color = WizardColors.textMuted)
                week.days.forEachIndexed { dayPosition, day ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(day.name, style = WizardTypography.controlLabel, color = WizardColors.text)
                        day.exercises.forEachIndexed { position, exercise ->
                            val open = openedExerciseId == exercise.id
                            ImportExerciseRow(
                                exercise = exercise,
                                state = importExerciseStateOf(exercise.rawName, resolutions),
                                shownName = resolutions[exercise.rawName]?.let { names[it] } ?: exercise.rawName,
                                expanded = open,
                                onToggle = { onOpen(if (open) null else exercise.id) },
                            )
                            if (open) {
                                ImportExerciseEditor(
                                    exercise = exercise,
                                    dayId = day.id,
                                    position = position,
                                    dayExerciseCount = day.exercises.size,
                                    state = importExerciseStateOf(exercise.rawName, resolutions),
                                    shownName = resolutions[exercise.rawName]?.let { names[it] } ?: exercise.rawName,
                                    moveTargets = week.days.mapIndexedNotNull { target, other ->
                                        if (target == dayPosition) {
                                            null
                                        } else {
                                            ImportMoveTarget(other.id, importMoveLabelOf(target, other.name), other.exercises.size)
                                        }
                                    },
                                    sameNameCount = nameCounts[exercise.rawName] ?: 1,
                                    otherWeeks = ImportDraftEditor.sameExerciseInOtherWeeks(imported, exercise.id),
                                    suggest = suggest,
                                    search = search,
                                    onEdit = onEdit,
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
 * Los avisos del analizador («N filas no se entendieron») en una línea; al desplegarla, cada fila. Sin caja. Quien lo usa
 * guarda si está desplegado, para poder abrirlo desde la revisión final.
 */
@Composable
internal fun ImportWarningsNote(
    warnings: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { BringIntoViewRequester() }
    // Al desplegarse desde la revisión final, la lista sube a la vista (queda arriba, lejos del final de la página).
    LaunchedEffect(expanded) { if (expanded) requester.bringIntoView() }
    if (warnings.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth().bringIntoViewRequester(requester),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (warnings.size == 1) "1 fila no se entendió" else "${warnings.size} filas no se entendieron",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
            EntrenoTextAction(
                label = if (expanded) "Ocultar las filas" else "Ver las filas",
                onClick = { onExpandedChange(!expanded) },
            )
            if (expanded) {
                warnings.forEach { warning ->
                    Text(warning, style = WizardTypography.note, color = WizardColors.textMuted)
                }
            }
        }
    }
}

/**
 * La línea de deshacer, bajo el título del programa: el texto del último cambio y «Deshacer». Cada toque deshace un cambio.
 */
@Composable
internal fun ImportUndoLine(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag("import-undo-line"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = WizardTypography.note, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        EntrenoTextAction(label = "Deshacer", onClick = onUndo, modifier = Modifier.testTag("import-undo"))
    }
}

/**
 * Una fila de ejercicio: el pictograma, el nombre (reconocido o tal como vino), la prescripción y su estado a la derecha.
 * Al abrirla, sube a la vista para que su panel quede debajo.
 */
@Composable
private fun ImportExerciseRow(
    exercise: ImportedExercise,
    state: ImportExerciseState,
    shownName: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val pattern = remember(shownName) { exercisePatternOf(shownName) }
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) { if (expanded) requester.bringIntoView() }
    val stateText = when (state) {
        ImportExerciseState.CHOSEN -> "Reconocido"
        ImportExerciseState.OWN -> "Propio"
        ImportExerciseState.UNDECIDED -> "Sin decidir"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Ajustar ejercicio", onClick = onToggle)
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
 * El bloque de «Elegir»: las sugerencias del emparejador, un campo para buscar en el catálogo y «Dejarlo como está»
 * (ejercicio propio). Lo usa el panel de ajuste, dentro de su vidrio: no pone caja propia. [onPick] recibe la configuración
 * (null = propio) y el nombre que se muestra; [onCancel] null oculta «Cancelar» (con dudas no hay a qué volver).
 */
@Composable
internal fun ImportChoicePanel(
    rawName: String,
    suggest: suspend (String) -> List<ExerciseNameCandidate>,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onPick: (configId: String?, name: String) -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth().testTag("import-choice-panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Elige el ejercicio", style = WizardTypography.controlLabel, color = WizardColors.text)
        suggestions.forEach { candidate ->
            ImportCandidateRow(candidate = candidate, onClick = { onPick(candidate.configurationId, candidate.displayName) })
        }
        SetupFormTextField(
            value = query,
            onValueChange = { query = it },
            label = "Buscar en el catálogo",
            testTag = "import-choice-search",
        )
        results.forEach { candidate ->
            ImportCandidateRow(candidate = candidate, onClick = { onPick(candidate.configurationId, candidate.displayName) })
        }
        EntrenoTextAction(
            label = "Dejarlo como está (ejercicio propio)",
            onClick = { onPick(null, rawName) },
            modifier = Modifier.testTag("import-choice-own"),
        )
        onCancel?.let { EntrenoTextAction(label = "Cancelar", onClick = it) }
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
