package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.ui.components.KpknRestPickerChain
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknSetTable
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

/** Hojas que se abren desde la fila de opciones. «Unilateral» y «Más» son menús o paneles, no hojas. */
private enum class SessionExerciseSheet { MODE, SMART_LOAD, TECHNIQUE, NOTE }

/** Descanso con el que abre el selector cuando el ejercicio no tiene uno (el mismo de la fila de hoy). */
private const val DEFAULT_REST_SECONDS = 90

/**
 * Ejercicio desplegado (SESSION_EDITOR_V1 §3, SE3b): la tabla de series con las columnas de su modo, la fila de intensidad y la fila
 * de opciones. Va debajo de su fila plegada, dentro del módulo de la parte. Cada cambio pasa por los callbacks de la tarjeta, que son
 * las mismas llamadas al VM de hoy.
 *
 * Los ejercicios unilaterales siguen con el carrusel de hoy: la tabla escribe la serie, y en un unilateral los datos viven en cada lado.
 * El cardio y la movilidad no pasan por aquí (son de SE5).
 *
 * @param inSuperset el ejercicio es miembro de una superserie: su descanso lo pone la superserie (sin columna de descanso).
 */
@Composable
internal fun SessionExerciseExpanded(
    exercise: Exercise,
    inSuperset: Boolean,
    suppressRest: Boolean,
    relationshipAnchorName: String?,
    accentColor: Color,
    coverColor: Color,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onAddSet: (String?) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onOpenReplacePicker: () -> Unit,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onUpdateRelationshipNotes: (String?) -> Unit,
    onOpenSuperset: () -> Unit,
    onOpenGoal: () -> Unit,
    onOpenCustomUnit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val unilateral = exercise.isEffectivelyUnilateral()
    val intensity = sessionSetIntensity(exercise)
    val predictedKg = remember(exercise) {
        exercise.sets.associate { set -> set.id to calculateSuggestedLoad(exercise, set) }
    }
    val predictedMetrics = remember(exercise) {
        exercise.sets.associate { set -> set.id to calculateEstimatedMetric(exercise, set) }
    }
    val resolved1RM = remember(exercise) { resolveReferenceCapacity(exercise) }
    val columns = remember(exercise, suppressRest) { sessionSetColumns(exercise, withRest = !suppressRest) }
    val workIds = remember(exercise) { exercise.sets.map { it.id }.toSet() }
    val isSuperset = inSuperset || exercise.supersetGroupRefOrLegacyId() != null

    // Estado de presentación: qué hoja o menú está abierto y qué serie tiene su menú. Los datos son los del ejercicio.
    var menuRowId by remember(exercise.id) { mutableStateOf<String?>(null) }
    val rowBounds = remember(exercise.id) { mutableStateMapOf<String, Rect>() }
    var openSheet by remember(exercise.id) { mutableStateOf<SessionExerciseSheet?>(null) }
    var unilateralOpen by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var restOpen by remember(exercise.id) { mutableStateOf(false) }
    var moreOpen by remember(exercise.id) { mutableStateOf(false) }
    var moreBounds by remember(exercise.id) { mutableStateOf<Rect?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (reduced) Modifier else Modifier.animateContentSize(animationSpec = tween(durationMillis = 220))),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SessionIntensityRow(
            selected = intensityIndex(exercise, intensity),
            onSelect = { index ->
                if (index != intensityIndex(exercise, intensity)) {
                    onUpdateExercise { current -> current.withIntensity(intensityChoice(index)) }
                }
            },
            onMore = { bounds ->
                moreBounds = bounds
                moreOpen = true
            },
        )

        if (!unilateral) {
            // Cada celda escribe con las mismas reglas de la fila de hoy. Solo si el texto cambia, se escribe.
            val cellInput: (ExerciseSet, String, String) -> KpknCellInput = { set, key, current ->
                when (key) {
                    "rest" -> KpknCellInput.Picker(onOpen = { restOpen = true })
                    "kg", "reps", "rir", "rpe", "percent", "time", "distance", "value" -> KpknCellInput.Text(
                        keyboardType = keyboardFor(key),
                        onCommit = { typed ->
                            if (typed.trim() != current.trim()) {
                                onUpdateSet(set.id) { it.withTypedValue(key, typed.trim()) }
                            }
                        },
                    )
                    else -> KpknCellInput.ReadOnly
                }
            }
            val rows = toKpknSetRows(
                exercise = exercise,
                columns = columns,
                predictedKg = predictedKg,
                input = cellInput,
            ).map { row ->
                // RPE, RIR y reps suman o restan 1 con el arrastre corto. Las aproximaciones no.
                if (row.id !in workIds) {
                    row
                } else {
                    row.copy(
                        cells = row.cells.mapValues { (key, cell) ->
                            if (key == "reps" || key == "rir" || key == "rpe") {
                                cell.copy(onStep = { delta -> onUpdateSet(row.id) { it.steppedValue(key, delta) } })
                            } else {
                                cell
                            }
                        },
                    )
                }
            }
            KpknSetTable(
                columns = columns,
                rows = rows,
                onAddSet = { onAddSet(null) },
                onRowMenu = { id -> if (id in workIds) menuRowId = id },
                onRowBounds = { id, bounds -> rowBounds[id] = bounds },
            )
        } else {
            ExerciseSetsCarousel(
                exercise = exercise,
                reference1RM = resolved1RM,
                trainingMode = exercise.trainingMode,
                customUnit = exercise.customUnit,
                predictedMetrics = predictedMetrics,
                accentColor = accentColor,
                backgroundAccentColor = coverColor,
                modifier = Modifier.fillMaxWidth(),
                onAddSet = onAddSet,
                onUpdateSet = onUpdateSet,
                onRemoveSet = onRemoveSet,
                onMoveSet = onMoveSet,
            )
        }

        SessionOptionChips(
            modeLabel = "Modo · " + sessionModeLabel(exercise.trainingMode),
            unilateral = unilateral,
            smartLoad = resolved1RM != null,
            technique = exercise.sets.any { it.hasTechnique() },
            note = !exercise.relationshipNotes.isNullOrBlank(),
            onMode = { openSheet = SessionExerciseSheet.MODE },
            onUnilateral = { unilateralOpen = !unilateralOpen },
            onSmartLoad = { openSheet = SessionExerciseSheet.SMART_LOAD },
            onTechnique = { openSheet = SessionExerciseSheet.TECHNIQUE },
            onNote = { openSheet = SessionExerciseSheet.NOTE },
            onReplace = onOpenReplacePicker,
        )

        AnimatedVisibility(visible = unilateralOpen) {
            SessionUnilateralPanel(
                choiceIndex = exercise.unilateralChoiceIndex(),
                unilateral = unilateral,
                sideOrderIndex = if (exercise.unilateralSideOrder == UnilateralSideOrder.RIGHT_LEFT) 1 else 0,
                onChoose = { index ->
                    if (index != exercise.unilateralChoiceIndex()) {
                        onUpdateExercise { current -> current.withUnilateralChoice(index) }
                    }
                },
                onSideOrder = { index -> onUpdateExercise { current -> current.withSideOrder(index) } },
            )
        }

        if (isSuperset && !suppressRest) {
            SessionCaption(text = supersetCaption(exercise))
        }
        if (exercise.relativeToCanonicalExerciseId != null) {
            SessionCaption(text = "Relacionado con ${relationshipAnchorName ?: exercise.relativeToCanonicalExerciseId}")
        }
    }

    // Menú de la serie: se ancla a su número. Solo las series de trabajo y drop tienen menú (las aproximaciones son de solo lectura).
    val menuSet = menuRowId?.let { id -> exercise.sets.firstOrNull { it.id == id } }
    val menuAnchor = menuRowId?.let { id -> rowBounds[id] }
    if (menuSet != null && menuAnchor != null) {
        val canDuplicate = !menuSet.isEmptySlot && !isSuperset
        KpknContextMenu(
            anchor = menuAnchor,
            rows = if (canDuplicate) 5 else 4,
            onDismiss = { menuRowId = null },
        ) {
            SessionSetMenuRows(
                canDuplicate = canDuplicate,
                onWork = {
                    menuRowId = null
                    if (menuSet.isDropSet) onUpdateSet(menuSet.id) { it.asWorkSet() }
                },
                onDrop = {
                    menuRowId = null
                    if (!menuSet.isDropSet) onUpdateSet(menuSet.id) { it.asDropSet(UUID.randomUUID().toString()) }
                },
                onDuplicate = {
                    menuRowId = null
                    onUpdateExercise { current -> current.withDuplicatedSet(menuSet.id, UUID.randomUUID().toString()) }
                },
                onRemove = {
                    menuRowId = null
                    onRemoveSet(menuSet.id)
                },
            )
        }
    }

    val moreAnchor = moreBounds
    if (moreOpen && moreAnchor != null) {
        KpknContextMenu(
            anchor = moreAnchor,
            rows = sessionExerciseMoreRowCount(exercise),
            onDismiss = { moreOpen = false },
        ) {
            SessionExerciseMoreRows(
                exercise = exercise,
                relationshipAnchorName = relationshipAnchorName,
                onToggleRom = {
                    moreOpen = false
                    onUpdateExercise { current -> current.copy(trackRom = !current.trackRom) }
                },
                onRelation = {
                    moreOpen = false
                    if (exercise.relativeToCanonicalExerciseId == null) onOpenRelationshipPicker() else onClearRelationship()
                },
                onSuperset = {
                    moreOpen = false
                    onOpenSuperset()
                },
                onGoal = {
                    moreOpen = false
                    onOpenGoal()
                },
                onCustomUnit = {
                    moreOpen = false
                    onOpenCustomUnit()
                },
            )
        }
    }

    if (restOpen) {
        KpknRestPickerChain(
            primaryTitle = "Descanso",
            primarySeconds = exercise.restTime ?: DEFAULT_REST_SECONDS,
            onConfirm = { seconds, _ ->
                restOpen = false
                onUpdateExercise { current -> current.copy(restTime = seconds, restBetweenSidesSeconds = null) }
            },
            onDismiss = { restOpen = false },
        )
    }

    when (openSheet) {
        SessionExerciseSheet.MODE -> KpknSheet(onDismissRequest = { openSheet = null }) {
            SessionModeSheetBody(
                current = exercise.trainingMode,
                onSelect = { mode ->
                    openSheet = null
                    onUpdateExercise { current -> current.copy(trainingMode = mode) }
                },
            )
        }
        SessionExerciseSheet.SMART_LOAD -> KpknSheet(onDismissRequest = { openSheet = null }) {
            SessionSmartLoadBody(
                exercise = exercise,
                onUpdateExercise = onUpdateExercise,
                onDone = { openSheet = null },
            )
        }
        SessionExerciseSheet.TECHNIQUE -> KpknSheet(onDismissRequest = { openSheet = null }) {
            SessionTechniqueBody(exercise = exercise, onUpdateSet = onUpdateSet)
        }
        SessionExerciseSheet.NOTE -> KpknSheet(onDismissRequest = { openSheet = null }) {
            SessionNoteBody(
                exercise = exercise,
                relationshipAnchorName = relationshipAnchorName,
                onUpdateRelationshipNotes = onUpdateRelationshipNotes,
                onUpdateRelationshipType = onUpdateRelationshipType,
                onClearRelationship = onClearRelationship,
                onDone = { openSheet = null },
            )
        }
        null -> Unit
    }
}

/** Teclado de cada celda: texto con guiones y dos puntos para reps y tiempo, cifra entera para RIR, decimal para el resto. */
private fun keyboardFor(key: String): KeyboardType = when (key) {
    "reps", "time" -> KeyboardType.Ascii
    "rir" -> KeyboardType.Number
    else -> KeyboardType.Decimal
}

/** Índice del segmentado de intensidad: 0 RIR, 1 RPE, 2 %; -1 si el modo no es de fuerza (tiempo, distancia o personalizado). */
private fun intensityIndex(exercise: Exercise, intensity: SessionSetIntensity): Int = when (exercise.trainingMode) {
    TrainingMode.RM -> 2
    TrainingMode.REPS, TrainingMode.AMRAP, TrainingMode.SOLO_RPE ->
        if (intensity == SessionSetIntensity.RIR) 0 else 1
    else -> -1
}

/** Intensidad de cada segmento del control (0 RIR, 1 RPE, 2 %). */
private fun intensityChoice(index: Int): SessionSetIntensity = when (index) {
    0 -> SessionSetIntensity.RIR
    2 -> SessionSetIntensity.PERCENT
    else -> SessionSetIntensity.RPE
}

/** Línea de la superserie: el descanso entre ejercicios y tras la ronda, con los valores por defecto de siempre. */
private fun supersetCaption(exercise: Exercise): String {
    val between = exercise.supersetRestBetween ?: 60
    val after = exercise.supersetRestAfter ?: 120
    val individual = exercise.restTime?.let { " El descanso individual ($it s) lo reemplaza el de la superserie." }.orEmpty()
    return "Superserie: $between s entre ejercicios y $after s tras la ronda.$individual"
}

/** Pie de texto del ejercicio (superserie, relación): caption en `textMuted`, sin caja. */
@Composable
internal fun SessionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = KpknType.caption,
        color = WizardColors.textMuted,
        modifier = modifier.fillMaxWidth(),
    )
}
