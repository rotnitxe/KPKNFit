package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.clickable
import com.example.kpkn.ui.components.icons.KpknLineIcons
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import com.example.kpkn.domain.training.importer.ImportLoadUnit
import com.example.kpkn.domain.training.importer.ImportedExercise
import com.example.kpkn.domain.training.importer.ImportedProgramBuilder
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
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

/** La unidad de las cargas en una palabra: «kg» o «lb». */
internal fun importLoadUnitLabel(unit: ImportLoadUnit): String = if (unit == ImportLoadUnit.LB) "lb" else "kg"

/** La prescripción en una línea: «4 × 6–8 · 120 kg · RPE 8», o «135 lb» si las cargas del archivo están en libras. */
internal fun importPrescriptionOf(exercise: ImportedExercise, unit: ImportLoadUnit): String {
    val reps = if (exercise.repsMin == exercise.repsMax) "${exercise.repsMin}" else "${exercise.repsMin}–${exercise.repsMax}"
    val parts = mutableListOf("${exercise.sets} × $reps")
    exercise.loadKg?.let { parts += "${decimalText(it)} ${importLoadUnitLabel(unit)}" }
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

/** El primer valor de carga en kilos (o libras) del programa, en el orden de la vista previa; null si no hay ninguno. */
internal fun importFirstLoadOf(imported: ImportedProgramDraft): Double? =
    imported.weeks.sortedBy { it.index }.firstNotNullOfOrNull { week ->
        week.days.firstNotNullOfOrNull { day -> day.exercises.firstNotNullOfOrNull { it.loadKg } }
    }

/**
 * El conmutador «kg | lb» de las cargas del archivo, encima de las semanas. Con lb, la nota dice cómo se guardan: con el primer
 * valor del programa, convertido como lo hace el constructor (135 lb = 61 kg).
 */
@Composable
internal fun ImportLoadUnitToggle(unit: ImportLoadUnit, example: Double, onChange: (ImportLoadUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Las cargas de tu archivo están en", style = WizardTypography.controlLabel, color = WizardColors.text)
        ImportSegmented(
            options = ImportLoadUnit.entries,
            selected = unit,
            label = { importLoadUnitLabel(it) },
            onSelected = onChange,
            testTag = "import-load-unit",
        )
        if (unit == ImportLoadUnit.LB) {
            val kilos = ImportedProgramBuilder.kilograms(example, ImportLoadUnit.LB)
            Text(
                "Se guardarán en kg (p. ej. ${decimalText(example)} lb = ${decimalText(kilos)} kg).",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
        }
    }
}

/**
 * La vista previa de un programa importado: semanas, días y ejercicios con su prescripción. Cada ejercicio muestra si
 * está reconocido (✓), es propio o tiene dudas («Elegir»). Tocarlo abre su panel de ajuste ([openedExerciseId] es el
 * único abierto). Cada semana y cada día tienen su «⋯», con el menú en línea (renombrar, mover, unir, quitar, añadir días
 * y ejercicios). Lo usan el paso del alta y la pantalla de Programas: no guarda nada por sí misma; reenvía cada edición
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
    // Un menú de semana o de día, o el panel «Añadir ejercicio», abierto (clave de ImportStructureMenus.kt). Solo uno a la vez y
    // nunca con un ejercicio abierto: si se abre un ejercicio desde fuera (la revisión final), el menú se cierra.
    var structurePanel by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openedExerciseId) { if (openedExerciseId != null) structurePanel = null }
    val activePanel = structurePanel.takeIf { openedExerciseId == null }
    fun togglePanel(key: String) {
        if (activePanel == key) {
            structurePanel = null
        } else {
            structurePanel = key
            onOpen(null)
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // La unidad de las cargas va encima de las semanas, y solo si el programa tiene alguna carga.
        importFirstLoadOf(imported)?.let { example ->
            ImportLoadUnitToggle(
                unit = imported.loadUnit,
                example = example,
                onChange = { unit -> onEdit(ImportEditOp.SetLoadUnit(unit)) },
            )
        }
        imported.weeks.sortedBy { it.index }.forEach { week ->
            val weekKey = importWeekMenuKey(week.index)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Semana ${week.index}",
                        style = WizardTypography.eyebrow,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    ImportMenuButton(
                        open = activePanel == weekKey,
                        description = "Opciones de la semana ${week.index}",
                        onClick = { togglePanel(weekKey) },
                        testTag = "import-week-menu-${week.index}",
                    )
                }
                if (activePanel == weekKey) {
                    ImportWeekMenu(week = week, onEdit = onEdit, onClose = { structurePanel = null })
                }
                week.days.forEachIndexed { dayPosition, day ->
                    val dayKey = importDayMenuKey(day.id)
                    val addKey = importAddExerciseKey(day.id)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                day.name,
                                style = WizardTypography.controlLabel,
                                color = WizardColors.text,
                                modifier = Modifier.weight(1f),
                            )
                            ImportMenuButton(
                                open = activePanel == dayKey,
                                description = "Opciones de «${day.name}»",
                                onClick = { togglePanel(dayKey) },
                                testTag = "import-day-menu-${day.id}",
                            )
                        }
                        if (activePanel == dayKey) {
                            ImportDayMenu(
                                day = day,
                                position = dayPosition,
                                dayCount = week.days.size,
                                onEdit = onEdit,
                                onAddExercise = { togglePanel(addKey) },
                                onClose = { structurePanel = null },
                            )
                        }
                        day.exercises.forEachIndexed { position, exercise ->
                            val open = openedExerciseId == exercise.id
                            ImportExerciseRow(
                                exercise = exercise,
                                state = importExerciseStateOf(exercise.rawName, resolutions),
                                shownName = resolutions[exercise.rawName]?.let { names[it] } ?: exercise.rawName,
                                loadUnit = imported.loadUnit,
                                expanded = open,
                                onToggle = {
                                    structurePanel = null
                                    onOpen(if (open) null else exercise.id)
                                },
                            )
                            if (open) {
                                ImportExerciseEditor(
                                    exercise = exercise,
                                    dayId = day.id,
                                    position = position,
                                    dayExerciseCount = day.exercises.size,
                                    state = importExerciseStateOf(exercise.rawName, resolutions),
                                    shownName = resolutions[exercise.rawName]?.let { names[it] } ?: exercise.rawName,
                                    loadUnit = imported.loadUnit,
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
                        // «Añadir ejercicio» sale bajo el último ejercicio del día, que es donde queda el nuevo.
                        if (activePanel == addKey) {
                            ImportAddExercisePanel(
                                key = day.id,
                                search = search,
                                onAdd = { name, configId, sets, repsMin, repsMax ->
                                    onEdit(
                                        ImportEditOp.AddExercise(day.id, name, sets, repsMin, repsMax, configId = configId, decided = true),
                                    )
                                },
                                onClose = { structurePanel = null },
                            )
                        }
                    }
                }
                ImportTextAction(
                    label = "Añadir día",
                    onClick = { onEdit(ImportEditOp.AddDay(week.index, "")) },
                    modifier = Modifier.testTag("import-add-day-${week.index}"),
                )
            }
        }
    }
}

/**
 * La línea de deshacer, justo bajo el título (G3): el texto del último cambio en una línea (13 sp) y «Deshacer», que mide 48 dp de
 * alto táctil. Cada toque deshace un cambio.
 */
@Composable
internal fun ImportUndoLine(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("import-undo-line"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("import-undo")
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Deshacer", style = WizardTypography.cardSubtitle.copy(fontWeight = FontWeight.SemiBold), color = WizardColors.text)
        }
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
    loadUnit: ImportLoadUnit,
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
        Box(
            modifier = Modifier.size(40.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            PatternGlyph(pattern = pattern, accent = SymbolPalette.musculo, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(shownName, style = WizardTypography.measure, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(importPrescriptionOf(exercise, loadUnit), style = WizardTypography.note, color = WizardColors.textMuted)
        }
        when (state) {
            ImportExerciseState.CHOSEN -> Row(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Reconocido" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(KpknLineIcons.Check, contentDescription = null, tint = SymbolPalette.ok, modifier = Modifier.size(14.dp))
                Text("Reconocido", style = WizardTypography.note.copy(fontSize = 12.sp), color = SymbolPalette.ok, maxLines = 1)
            }
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
    LaunchedEffect(rawName) {
        suggestions = try {
            suggest(rawName)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            emptyList()
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
        ImportCatalogSearch(key = rawName, search = search, onPick = { onPick(it.configurationId, it.displayName) })
        ImportTextAction(
            label = "Dejarlo como está (ejercicio propio)",
            onClick = { onPick(null, rawName) },
            modifier = Modifier.testTag("import-choice-own"),
        )
        onCancel?.let { ImportTextAction(label = "Cancelar", onClick = it) }
    }
}

/**
 * La búsqueda libre en el catálogo: el campo «Buscar en el catálogo» y sus resultados; al tocar uno, [onPick]. La usan «Elegir»
 * y «Añadir ejercicio». [key] reinicia la búsqueda cuando cambia lo que se elige (el nombre o el día).
 */
@Composable
internal fun ImportCatalogSearch(
    key: String,
    search: suspend (String) -> List<ExerciseNameCandidate>,
    onPick: (ExerciseNameCandidate) -> Unit,
) {
    var query by rememberSaveable(key) { mutableStateOf("") }
    var results by remember(key) { mutableStateOf<List<ExerciseNameCandidate>>(emptyList()) }
    var resultsQuery by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key, query) {
        val text = query.trim()
        results = emptyList()
        resultsQuery = null
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
            resultsQuery = text
        }
    }
    SetupFormTextField(
        value = query,
        onValueChange = { query = it },
        label = "Buscar en el catálogo",
        testTag = "import-choice-search",
    )
    results.takeIf { resultsQuery == query.trim() }.orEmpty().forEach { candidate ->
        ImportCandidateRow(candidate = candidate, onClick = { onPick(candidate) })
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
