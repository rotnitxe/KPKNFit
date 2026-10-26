package com.example.kpkn.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralIntensityMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.RestBundleDialog
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.components.InlineSetRow
import com.example.kpkn.screens.sessioneditor.components.AmrapSetFocus
import com.example.kpkn.screens.sessioneditor.components.RmSetFocus
import com.example.kpkn.screens.sessioneditor.components.ExerciseModeSheet
import com.example.kpkn.screens.sessioneditor.components.SetRestControl
import com.example.kpkn.screens.sessioneditor.components.SetRowPresentation
import com.example.kpkn.screens.sessioneditor.components.SetRowSummary
import com.example.kpkn.screens.sessioneditor.components.setRowSummary
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatMax2Decimals
import com.example.kpkn.screens.sessioneditor.freshPlannedCopy
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.withPlannedConfigOf
import com.example.kpkn.screens.sessioneditor.withSetDuplicated
import com.example.kpkn.screens.sessioneditor.withSideConfigOf
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

/** Marcas de prueba de la hoja «Editar series» (G1). */
internal const val LIVE_EDIT_MODE_TAG = "live-edit-mode"
internal const val LIVE_EDIT_LATERALITY_TAG = "live-edit-laterality"
internal const val LIVE_EDIT_REST_TAG = "live-edit-rest"
internal const val LIVE_EDIT_SMART_LOAD_TAG = "live-edit-smart-load"
internal const val LIVE_EDIT_ADD_SET_TAG = "live-edit-add-set"
internal const val LIVE_EDIT_FOCUS_TAG = "live-edit-focus"
internal const val LIVE_EDIT_PREV_TAG = "live-edit-prev"
internal const val LIVE_EDIT_NEXT_TAG = "live-edit-next"
internal const val LIVE_EDIT_APPLY_ALL_TAG = "live-edit-apply-all"
internal const val LIVE_EDIT_DUPLICATE_TAG = "live-edit-duplicate"
internal const val LIVE_EDIT_UP_TAG = "live-edit-up"
internal const val LIVE_EDIT_DOWN_TAG = "live-edit-down"
internal const val LIVE_EDIT_DELETE_TAG = "live-edit-delete"

/** La marca de prueba de la ficha de la serie número [index] (desde 0). */
internal fun liveEditSetChipTag(index: Int): String = "live-edit-set-$index"

/** Los fondos del tablero G1: losetas #151515, elementos tonales #1A1A1A y la serie elegida con borde de tinta. */
private val TileFill = Color(0xFF151515)
private val ChipBorder = Color.White.copy(alpha = 0.08f)

/** El peligro de la paleta KPKN (#EF6F64). */
private val LiveEditDanger = Color(0xFFEF6F64)

/**
 * El cuerpo de fuerza de «Editar series» (G1): la zona «Ejercicio», que vale para todas las series (modo, lateralidad, descanso y
 * «Carga inteligente»), la lista compacta de series y el foco de la serie elegida, con sus flechas y sus acciones. Todo sale del
 * ejercicio en edición: la serie que se abre es la de turno de la sesión ([initialSetIndex]) y cada cambio vuelve a quien llama por
 * [onUpdateExercise]. «Confirmar cambios» es del cajón.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveEditSeriesBody(
    exercise: Exercise,
    reference1RM: Double?,
    accentColor: Color,
    initialSetIndex: Int,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onOpenSmartLoad: () -> Unit,
) {
    val unilateral = exercise.isEffectivelyUnilateral()
    val sets = exercise.sets
    val orderedSides = if (exercise.unilateralSideOrder == UnilateralSideOrder.RIGHT_LEFT) listOf("R", "L") else listOf("L", "R")
    val sharedSides = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED
    var selectedId by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    var selectedSide by rememberSaveable(exercise.id) { mutableStateOf(orderedSides.first()) }
    var showRest by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showLaterality by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<PendingApplyAll?>(null) }

    val selectedIndex = sets.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
        ?: initialSetIndex.coerceIn(0, (sets.size - 1).coerceAtLeast(0))
    val selected = sets.getOrNull(selectedIndex)
    val side = if (unilateral) selectedSide.takeIf { it in orderedSides } ?: orderedSides.first() else null
    val predicted = remember(exercise.trainingMode, exercise.reference1RM, exercise.referenceTrainingMax, exercise.prFor1RM, exercise.sets) {
        sets.associate { set -> set.id to calculateSuggestedLoad(exercise, set) }
    }
    fun summaryOf(set: ExerciseSet, ofSide: String?): SetRowSummary = setRowSummary(
        set = set,
        side = ofSide,
        mode = exercise.trainingMode,
        customUnit = exercise.customUnit,
        predictedWeight = predicted[set.id],
        estimatedMetric = null,
    )

    /** La dosis de una serie como la lee la sesión en vivo: «8 × 100 kg» (reps × carga) en Reps y AMRAP; en los demás modos, la del editor. */
    fun doseOf(set: ExerciseSet): String = liveSetDose(set, exercise.trainingMode, summaryOf(set, null).dose, predicted[set.id])

    fun addSet() {
        val lastSet = sets.lastOrNull()
        val seed = lastSet?.freshPlannedCopy(preserveEmptySlot = false) ?: ExerciseSet(id = UUID.randomUUID().toString(), targetReps = 8)
        val newSet = seed.copy(leftTarget = lastSet?.leftTarget, rightTarget = lastSet?.rightTarget)
        selectedId = newSet.id
        onUpdateExercise { current -> current.copy(sets = current.sets + newSet) }
    }

    fun moveSet(setId: String, direction: Int) {
        // Al mover, la selección se ancla a la serie movida: si el foco venía de la serie de turno
        // (sin id elegido), seguiría apuntando al índice de siempre y no acompañaría al movimiento.
        selectedId = setId
        onUpdateExercise { current ->
            val currentIndex = current.sets.indexOfFirst { it.id == setId }
            val targetIndex = (currentIndex + direction).coerceIn(0, current.sets.lastIndex)
            if (currentIndex >= 0 && currentIndex != targetIndex) {
                val mutable = current.sets.toMutableList()
                val moved = mutable.removeAt(currentIndex)
                mutable.add(targetIndex, moved)
                current.copy(sets = mutable)
            } else {
                current
            }
        }
    }

    fun requestApplyToAll(set: ExerciseSet, ofSide: String?) {
        fun applyTo(current: ExerciseSet): ExerciseSet =
            if (ofSide == null) current.withPlannedConfigOf(set) else current.withSideConfigOf(set, ofSide, sharedSides)
        val changed = sets.filter { it.id != set.id && !it.isEmptySlot }.filter { applyTo(it) != it }
        if (changed.isEmpty()) return
        pendingApply = PendingApplyAll(count = changed.size) {
            val ids = changed.map { it.id }.toSet()
            onUpdateExercise { current -> current.copy(sets = current.sets.map { if (it.id in ids) applyTo(it) else it }) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text("Los cambios ajustan el plan. Lo que ya registraste se conserva en el historial de esta sesión.",
            style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 12.dp))
        // ── Ejercicio: vale para todas las series ──
        LiveEditSectionLabel("EJERCICIO", "Vale para todas las series")
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveEditModePill(
                mode = exercise.trainingMode,
                onSelect = { mode -> onUpdateExercise { it.withTrainingMode(mode) } },
            )
            if (unilateral) Text("Unilateral", style = KpknType.caption, color = WizardColors.textMuted)
            KpknIconButton(icon = KpknLineIcons.More, contentDescription = "Más opciones del ejercicio",
                onClick = { showOptions = true }, modifier = Modifier.testTag(LIVE_EDIT_LATERALITY_TAG))
        }
        if (unilateral) {
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SideOrderChip(
                    sideOrder = exercise.unilateralSideOrder,
                    accentColor = accentColor,
                    onToggle = {
                        onUpdateExercise {
                            it.copy(
                                unilateralSideOrder = if (it.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                    UnilateralSideOrder.RIGHT_LEFT
                                } else {
                                    UnilateralSideOrder.LEFT_RIGHT
                                },
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val restText = buildString {
                append(KpknSetFormat.rest(exercise.restTime ?: 90))
                if (unilateral) append(" · Lados ").append(KpknSetFormat.rest(exercise.restBetweenSidesSeconds ?: 0))
            }
            LiveEditTile(
                modifier = Modifier.weight(1f).testTag(LIVE_EDIT_REST_TAG),
                description = "Descanso: $restText",
                onClick = { showRest = true },
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Descanso", style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                    Text(restText, style = KpknType.metricM, color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(14.dp))
            }
            LiveEditTile(
                modifier = Modifier.weight(1.25f).testTag(LIVE_EDIT_SMART_LOAD_TAG),
                description = "Carga inteligente",
                onClick = onOpenSmartLoad,
            ) {
                Icon(KpknLineIcons.Training, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("Carga inteligente", style = KpknType.chip.copy(fontSize = 12.sp), color = WizardColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = reference1RM?.takeIf { it > 0.0 }?.let { "1RM: ${formatEditableNumber(it).replace('.', ',')} kg" } ?: "Configurar",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                    )
                }
                Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(14.dp))
            }
        }

        // ── Series: lista compacta ──
        LiveEditSectionLabel("SERIES", "Toca una para editarla", modifier = Modifier.padding(top = 14.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val addWidth = 56.dp
            val gap = 6.dp
            val count = sets.size.coerceAtLeast(1)
            val chipWidth = ((maxWidth - addWidth - gap * count) / count).coerceAtLeast(60.dp)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                sets.forEachIndexed { index, set ->
                    val summary = summaryOf(set, null)
                    LiveEditSetChip(
                        number = index + 1,
                        dose = doseOf(set).replace(" kg", ""),
                        description = "Serie ${index + 1}: ${doseOf(set)}" + (summary.intensity?.let { ", $it" } ?: ""),
                        selected = index == selectedIndex,
                        onClick = { selectedId = set.id },
                        modifier = Modifier.width(chipWidth).testTag(liveEditSetChipTag(index)),
                    )
                }
                LiveEditAddSetChip(onClick = { addSet() }, modifier = Modifier.width(addWidth).testTag(LIVE_EDIT_ADD_SET_TAG))
            }
        }

        // ── Foco de la serie elegida ──
        if (selected != null && !selected.isEmptySlot) {
            val next = sets.getOrNull(selectedIndex + 1)
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .testTag(LIVE_EDIT_FOCUS_TAG)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TileFill)
                    .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(20.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Serie ${selectedIndex + 1} de ${sets.size}", style = KpknType.metricM, color = WizardColors.text, maxLines = 1)
                        Text(
                            text = next?.let { nextSet ->
                                val summary = summaryOf(nextSet, null)
                                "Siguiente: " + listOfNotNull(doseOf(nextSet), summary.intensity).joinToString(" · ")
                            } ?: "Última serie",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LiveEditArrow(
                        icon = KpknLineIcons.ChevronLeft,
                        description = "Serie anterior",
                        enabled = selectedIndex > 0,
                        onClick = { selectedId = sets[selectedIndex - 1].id },
                        modifier = Modifier.testTag(LIVE_EDIT_PREV_TAG),
                    )
                    LiveEditArrow(
                        icon = KpknLineIcons.ChevronRight,
                        description = "Serie siguiente",
                        enabled = selectedIndex < sets.lastIndex,
                        onClick = { selectedId = sets[selectedIndex + 1].id },
                        modifier = Modifier.testTag(LIVE_EDIT_NEXT_TAG),
                    )
                }
                Column(modifier = Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unilateral && side != null) {
                        KpknSegmentedControl(
                            options = orderedSides,
                            selectedIndex = orderedSides.indexOf(side),
                            onSelect = { index -> selectedSide = orderedSides[index] },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val sideTarget = side?.let { if (it == "L") selected.leftTarget else selected.rightTarget }
                    if (unilateral && side != null && sideTarget == null) {
                        KpknSecondaryPill(
                            text = "+ Añadir $side",
                            onClick = {
                                onUpdateExercise { current ->
                                    current.copy(
                                        sets = current.sets.map { set ->
                                            if (set.id != selected.id) return@map set
                                            val source = if (side == "L") set.rightTarget else set.leftTarget
                                            val seed = source ?: UnilateralTarget(
                                                weight = set.weight,
                                                targetReps = set.targetReps,
                                                targetRepsRange = set.targetRepsRange,
                                                targetDuration = set.targetDuration,
                                                targetValue = set.plannedTargetV2,
                                                targetRPE = set.targetRPE,
                                                targetRIR = set.targetRIR,
                                                intensityMode = set.intensityMode,
                                            )
                                            if (side == "L") set.copy(leftTarget = set.leftTarget ?: seed) else set.copy(rightTarget = set.rightTarget ?: seed)
                                        },
                                    )
                                }
                            },
                        )
                    } else {
                        val updateSelected: ((ExerciseSet) -> ExerciseSet) -> Unit = { updater ->
                            onUpdateExercise { current ->
                                current.copy(sets = current.sets.map { set -> if (set.id == selected.id) updater(set) else set })
                            }
                        }
                        if (selected.purpose.isAmrap) {
                            AmrapSetFocus(exercise, selected, selectedIndex, fixedUnilateralSide = side, onUpdate = updateSelected)
                        } else if (exercise.trainingMode == TrainingMode.RM) {
                            RmSetFocus(exercise, selected, updateSelected, onUpdateExercise, fixedUnilateralSide = side)
                        } else InlineSetRow(
                            set = selected,
                            index = selectedIndex,
                            reference1RM = reference1RM,
                            trainingMax = exercise.referenceTrainingMax,
                            predictedWeight = predicted[selected.id],
                            estimatedMetric = null,
                            trainingMode = exercise.trainingMode,
                            customUnit = exercise.customUnit,
                            accentColor = accentColor,
                            canMoveUp = selectedIndex > 0,
                            canMoveDown = selectedIndex < sets.lastIndex,
                            isUnilateral = unilateral,
                            fixedUnilateralSide = side,
                            showSetActions = false,
                            unilateralIntensityMode = exercise.unilateralIntensityMode,
                            onUpdate = updateSelected,
                            onRemove = {},
                            onMoveUp = {},
                            onMoveDown = {},
                            fillHeight = false,
                            presentation = SetRowPresentation.List,
                        )
                    }
                }
                SetRestControl(
                    seconds = selected.restAfterSeconds ?: exercise.restTime ?: 90,
                    inherited = selected.restAfterSeconds == null,
                    onChange = { seconds -> onUpdateExercise { current -> current.copy(
                        sets = current.sets.map { if (it.id == selected.id) it.copy(restAfterSeconds = seconds) else it },
                    ) } },
                    onReset = { onUpdateExercise { current -> current.copy(
                        sets = current.sets.map { if (it.id == selected.id) it.copy(restAfterSeconds = null) else it },
                    ) } },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveEditActionChip(
                        text = "Aplicar a todas",
                        enabled = sets.size > 1,
                        onClick = { requestApplyToAll(selected, side) },
                        modifier = Modifier.testTag(LIVE_EDIT_APPLY_ALL_TAG),
                    )
                    LiveEditActionChip(
                        text = "Duplicar",
                        onClick = {
                            val copyId = UUID.randomUUID().toString()
                            selectedId = copyId
                            // El primer id es el de la copia; los siguientes (los de sus técnicas) son nuevos.
                            var firstId = true
                            onUpdateExercise { current ->
                                firstId = true
                                current.withSetDuplicated(selected.id) {
                                    if (firstId) copyId.also { firstId = false } else UUID.randomUUID().toString()
                                }
                            }
                        },
                        modifier = Modifier.testTag(LIVE_EDIT_DUPLICATE_TAG),
                    )
                    LiveEditActionChip(
                        text = "Subir",
                        enabled = selectedIndex > 0,
                        onClick = { moveSet(selected.id, -1) },
                        modifier = Modifier.testTag(LIVE_EDIT_UP_TAG),
                    )
                    LiveEditActionChip(
                        text = "Bajar",
                        enabled = selectedIndex < sets.lastIndex,
                        onClick = { moveSet(selected.id, 1) },
                        modifier = Modifier.testTag(LIVE_EDIT_DOWN_TAG),
                    )
                    LiveEditActionChip(
                        text = "Quitar del plan",
                        danger = true,
                        enabled = sets.size > 1,
                        onClick = {
                            val neighbor = sets.getOrNull(selectedIndex + 1) ?: sets.getOrNull(selectedIndex - 1)
                            selectedId = neighbor?.id
                            onUpdateExercise { current -> current.copy(sets = current.sets.filterNot { it.id == selected.id }) }
                        },
                        modifier = Modifier.testTag(LIVE_EDIT_DELETE_TAG),
                    )
                }
            }
        }
    }

    // G1 sigue B1/B8: la lateralidad pertenece al ejercicio y se cambia desde sus opciones.
    if (showOptions) KpknSheet(onDismissRequest = { showOptions = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Opciones del ejercicio", style = KpknType.dialogTitle, color = WizardColors.text)
            KpknTextAction(text = "Lateralidad: ${if (unilateral) "Unilateral" else "Bilateral"}", onClick = {
                showOptions = false
                showLaterality = true
            })
        }
    }
    if (showLaterality) KpknSheet(onDismissRequest = { showLaterality = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lateralidad", style = KpknType.dialogTitle, color = WizardColors.text)
            listOf(false to "Bilateral", true to "Unilateral").forEach { (option, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = option == unilateral, role = Role.RadioButton, onClick = {
                        if (option != unilateral) onUpdateExercise { it.toggledBilateralUnilateral() }
                        showLaterality = false
                    }), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = KpknType.bodyStrong, color = WizardColors.text)
                }
            }
        }
    }

    if (showRest) {
        RestBundleDialog(
            primaryLabel = if (unilateral) "Series" else "Descanso",
            initialPrimarySeconds = exercise.restTime ?: 90,
            initialSideSeconds = if (unilateral) exercise.restBetweenSidesSeconds ?: 0 else null,
            accentColor = accentColor,
            onDismiss = { showRest = false },
            onConfirm = { primary, sideSeconds ->
                onUpdateExercise { draft ->
                    draft.copy(
                        restTime = primary,
                        restBetweenSidesSeconds = if (draft.isEffectivelyUnilateral()) sideSeconds ?: draft.restBetweenSidesSeconds else draft.restBetweenSidesSeconds,
                    )
                }
                showRest = false
            },
        )
    }

    pendingApply?.let { request ->
        KpknAlertDialog(
            onDismissRequest = { pendingApply = null },
            title = "Aplicar a todas",
            text = if (request.count == 1) {
                "Otra serie cambiará para quedar igual que esta. ¿Aplicar?"
            } else {
                "${request.count} series cambiarán para quedar iguales que esta. ¿Aplicar?"
            },
            confirmLabel = "Aplicar",
            onConfirm = {
                request.onApply()
                pendingApply = null
            },
            dismissLabel = "Cancelar",
            onDismiss = { pendingApply = null },
        )
    }
}

/** Confirmación pendiente de «Aplicar a todas»: cuántas series cambian y la copia que se hace al confirmar. */
private class PendingApplyAll(val count: Int, val onApply: () -> Unit)

/** La etiqueta de una zona («EJERCICIO») con su nota a la derecha («Vale para todas las series»). */
@Composable
private fun LiveEditSectionLabel(title: String, note: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = KpknType.eyebrow, color = WizardColors.textMuted, maxLines = 1)
        Text(note, style = KpknType.caption, color = WizardColors.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Píldora visual de 44 dp con objetivo táctil de 48 dp y confirmación común B4. */
@Composable
private fun LiveEditModePill(mode: TrainingMode, onSelect: (TrainingMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(LIVE_EDIT_MODE_TAG)
                .semantics { contentDescription = "Modo: ${trainingModeLabel(mode)}" }
                .clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(KpknSurface.inset)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(trainingModeLabel(mode), style = KpknType.chip, color = WizardColors.text, maxLines = 1)
                Icon(KpknLineIcons.ChevronDown, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(14.dp))
            }
        }
        if (open) ExerciseModeSheet(
            current = mode,
            onSelect = { selected ->
                open = false
                onSelect(selected)
            },
            onDismiss = { open = false },
        )
    }
}

/** Una loseta de la zona «Ejercicio»: 52 dp de alto, un solo objetivo táctil. */
@Composable
private fun LiveEditTile(
    modifier: Modifier,
    description: String,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TileFill)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** La ficha de una serie en la lista: su número y su dosis; la elegida lleva el borde de tinta. 56 dp de alto. */
@Composable
private fun LiveEditSetChip(
    number: Int,
    dose: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(if (selected) KpknSurface.inset else TileFill)
            .border(if (selected) BorderStroke(1.5.dp, WizardColors.text) else BorderStroke(1.dp, ChipBorder), shape)
            .semantics { contentDescription = if (selected) "$description, elegida" else description }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$number", style = KpknType.caption, color = if (selected) WizardColors.text else WizardColors.textMuted, maxLines = 1)
        Text(
            dose,
            style = KpknType.chip,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** «+ Serie»: la ficha con borde discontinuo que añade una serie. */
@Composable
private fun LiveEditAddSetChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .semantics { contentDescription = "Añadir serie" }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(KpknLineIcons.Plus, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(16.dp))
        Text("Serie", style = KpknType.navLabel, color = WizardColors.text, maxLines = 1)
    }
}

/** Una flecha del foco (serie anterior o siguiente): un círculo de 44 dp dentro de su objetivo de 48. */
@Composable
private fun LiveEditArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).background(KpknSurface.inset, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(16.dp))
        }
    }
}

/** Una acción de la serie elegida: píldora tonal de 34 dp dentro de un objetivo de 48. «Eliminar» va con el filete de peligro. */
@Composable
private fun LiveEditActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val ink = if (danger) LiveEditDanger else WizardColors.text
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .then(
                    if (danger) {
                        Modifier.border(1.dp, LiveEditDanger.copy(alpha = 0.35f), RoundedCornerShape(17.dp))
                    } else {
                        Modifier.background(KpknSurface.inset)
                    },
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = KpknType.chip, color = ink, maxLines = 1)
        }
    }
}

/**
 * La dosis de una serie para la lista y el foco (G1): en Reps y AMRAP, las repeticiones (o el rango, o «N+») por la carga, «8 × 100 kg» con
 * espacio inseparable, como la lee la sesión en vivo; sin carga, solo las repeticiones. En los demás modos, la dosis del editor
 * ([editorDose], con el tiempo, la distancia, el %RM…). [predictedWeight] solo cuenta si la serie no tiene carga guardada.
 */
internal fun liveSetDose(set: ExerciseSet, mode: TrainingMode, editorDose: String, predictedWeight: Double?): String {
    if (mode != TrainingMode.REPS && mode != TrainingMode.AMRAP) return editorDose
    val reps = if (set.purpose.isAmrap) {
        set.amrapMinimumReps?.let { "$it+" } ?: "AMRAP"
    } else {
        set.targetRepsRange?.format() ?: set.targetReps?.toString()
    }
    val kg = (set.weight?.takeIf { it > 0.0 } ?: predictedWeight)?.let { formatMax2Decimals(it).replace('.', ',') }
    return when {
        reps != null && kg != null -> "$reps × $kg kg"
        kg != null -> "$kg kg"
        reps != null -> reps
        else -> editorDose
    }
}
