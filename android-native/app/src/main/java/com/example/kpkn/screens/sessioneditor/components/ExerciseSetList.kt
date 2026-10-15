package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatMax2Decimals
import com.example.kpkn.screens.sessioneditor.withPlannedConfigOf
import com.example.kpkn.screens.sessioneditor.withSetDuplicated
import com.example.kpkn.screens.sessioneditor.withSideConfigOf
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Lista vertical de las series del ejercicio (ER1c1, R1 §4.3). Cada serie es una fila de 56 dp con su número, su dosis, su intensidad y
 * su técnica; al tocarla se despliega en sitio con todos los controles de la tarjeta original ([InlineSetRow] en
 * [SetRowPresentation.List]). Solo una fila abierta a la vez. La fila abierta tiene sus acciones: «Aplicar a todas», «Duplicar» (solo
 * si la pantalla da [onTransformExercise]), «Subir», «Bajar» y «Eliminar» (siempre queda una serie).
 *
 * En unilateral, cada serie son dos filas: I (izquierda) y D (derecha), con su letra y sin el azul y el rojo del original.
 */
@Composable
internal fun ExerciseSetList(
    exercise: Exercise,
    reference1RM: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    predictedMetrics: Map<String, Double?>,
    accentColor: Color,
    backgroundAccentColor: Color,
    modifier: Modifier = Modifier,
    onAddSet: (String?) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onTransformExercise: (((Exercise) -> Exercise) -> Unit)? = null,
) {
    if (exercise.sets.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("No hay series añadidas", style = KpknType.caption, color = WizardColors.textMuted)
            KpknSecondaryPill(text = "+ Añadir serie", onClick = { onAddSet(null) })
        }
        return
    }

    var openKey by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    var pendingApply by remember(exercise.id) { mutableStateOf<PendingApply?>(null) }
    val unilateral = exercise.isEffectivelyUnilateral()
    val orderedSides = if (exercise.unilateralSideOrder == UnilateralSideOrder.RIGHT_LEFT) listOf("R", "L") else listOf("L", "R")
    val sharedSides = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED
    val predictedWeights = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM, exercise.sets) {
        exercise.sets.associate { set -> set.id to calculateSuggestedLoad(exercise, set) }
    }

    /** Pide copiar la configuración de [set] (o de su lado [side]) al resto; si hay series distintas, antes pregunta. */
    fun requestApply(set: ExerciseSet, side: String?) {
        fun applyTo(current: ExerciseSet): ExerciseSet =
            if (side == null) current.withPlannedConfigOf(set) else current.withSideConfigOf(set, side, sharedSides)
        val changed = exercise.sets
            .filter { it.id != set.id && !it.isEmptySlot }
            .filter { applyTo(it) != it }
        if (changed.isEmpty()) return
        pendingApply = PendingApply(count = changed.size) {
            changed.forEach { target ->
                onUpdateSet(target.id) { current -> applyTo(current) }
            }
        }
    }

    /** Añade el lado que falta (I o D) con la carga del otro lado, como «Añadir L/R» del original. */
    fun addSide(set: ExerciseSet, side: String) {
        onUpdateSet(set.id) { current ->
            val source = if (side == "L") current.rightTarget else current.leftTarget
            val default = UnilateralTarget(
                weight = current.weight,
                targetReps = current.targetReps,
                targetRepsRange = current.targetRepsRange,
                targetDuration = current.targetDuration,
                targetValue = current.plannedTargetV2,
                targetRPE = current.targetRPE,
                targetRIR = current.targetRIR,
                intensityMode = current.intensityMode,
            )
            val seed = source ?: default
            if (side == "L") current.copy(leftTarget = current.leftTarget ?: seed) else current.copy(rightTarget = current.rightTarget ?: seed)
        }
    }

    @Composable
    fun SetItem(set: ExerciseSet, index: Int, side: String?) {
        val rowKey = if (side == null) set.id else "${set.id}:$side"
        val open = openKey == rowKey
        val label = if (side == null) "${index + 1}" else "${sideLetter(side)}${index + 1}"
        val predictedWeight = predictedWeights[set.id]
        val estimatedMetric = predictedMetrics[set.id]
        SetSummaryRow(
            label = label,
            summary = setRowSummary(
                set = set,
                side = side,
                mode = trainingMode,
                customUnit = customUnit,
                predictedWeight = predictedWeight,
                estimatedMetric = estimatedMetric,
            ),
            open = open,
            testTag = "set-row-$rowKey",
            onClick = { openKey = if (open) null else rowKey },
        )
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(KpknSurface.inset, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InlineSetRow(
                    set = set,
                    index = index,
                    reference1RM = reference1RM,
                    predictedWeight = predictedWeight,
                    estimatedMetric = estimatedMetric,
                    trainingMode = trainingMode,
                    customUnit = customUnit,
                    accentColor = accentColor,
                    backgroundAccentColor = backgroundAccentColor,
                    canMoveUp = index > 0,
                    canMoveDown = index < exercise.sets.lastIndex,
                    isUnilateral = unilateral,
                    fixedUnilateralSide = side,
                    showSetActions = false,
                    unilateralIntensityMode = exercise.unilateralIntensityMode,
                    onUpdate = { updater -> onUpdateSet(set.id, updater) },
                    onRemove = { onRemoveSet(set.id) },
                    onMoveUp = { onMoveSet(set.id, -1) },
                    onMoveDown = { onMoveSet(set.id, 1) },
                    fillHeight = false,
                    presentation = SetRowPresentation.List,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (exercise.sets.size > 1) {
                        KpknTextAction(text = "Aplicar a todas", onClick = { requestApply(set, side) })
                    }
                    if (onTransformExercise != null) {
                        KpknTextAction(
                            text = "Duplicar",
                            onClick = { onTransformExercise { current -> current.withSetDuplicated(set.id) } },
                        )
                    }
                    if (index > 0) {
                        KpknTextAction(text = "Subir", onClick = { onMoveSet(set.id, -1) })
                    }
                    if (index < exercise.sets.lastIndex) {
                        KpknTextAction(text = "Bajar", onClick = { onMoveSet(set.id, 1) })
                    }
                    if (exercise.sets.size > 1) {
                        KpknTextAction(
                            text = "Eliminar",
                            color = WizardColors.danger,
                            onClick = {
                                openKey = null
                                onRemoveSet(set.id)
                            },
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        exercise.sets.forEachIndexed { index, set ->
            key(set.id) {
                if (set.isEmptySlot) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "RONDA ${index + 1} · HUECO VACÍO",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                } else if (unilateral) {
                    orderedSides.forEach { side ->
                        if (sideTarget(set, side) != null) {
                            SetItem(set = set, index = index, side = side)
                        }
                    }
                    val missing = orderedSides.filter { side -> sideTarget(set, side) == null }
                    if (missing.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            missing.forEach { side ->
                                KpknSecondaryPill(
                                    text = "+ Añadir ${sideLetter(side)}",
                                    onClick = { addSide(set, side) },
                                )
                            }
                        }
                    }
                } else {
                    SetItem(set = set, index = index, side = null)
                }
            }
        }
        KpknTextAction(
            text = "+ Añadir serie",
            onClick = { onAddSet(null) },
            color = WizardColors.text,
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
private class PendingApply(val count: Int, val onApply: () -> Unit)

private fun sideTarget(set: ExerciseSet, side: String): UnilateralTarget? =
    if (side == "L") set.leftTarget else set.rightTarget

private fun sideLetter(side: String): String = if (side == "L") "I" else "D"

/** Fila colapsada de una serie (56 dp): número, dosis en Syne, tipo de carga si no es la externa, intensidad y técnica. */
@Composable
private fun SetSummaryRow(
    label: String,
    summary: SetRowSummary,
    open: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 36.dp)
                .height(32.dp)
                .background(KpknSurface.inset, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 1)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                summary.dose,
                style = KpknType.metricM,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            summary.load?.let { load ->
                Text(load, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            summary.intensity?.let { intensity ->
                Text(intensity, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
            }
            summary.technique?.let { technique ->
                Text(technique, style = KpknType.caption, color = WizardColors.text, maxLines = 1)
            }
            Icon(
                imageVector = if (open) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                contentDescription = if (open) "Plegar serie $label" else "Desplegar serie $label",
                tint = WizardColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Lo que se ve de una serie sin abrirla. */
private data class SetRowSummary(
    val dose: String,
    val load: String?,
    val intensity: String?,
    val technique: String?,
)

/**
 * Resume una serie (o su lado) con la misma lógica que la fila: la carga sugerida si la hay, las reps o el rango, el tiempo o la
 * distancia según el modo, y la intensidad y la técnica. Los decimales van con coma y las unidades con espacio inseparable.
 */
private fun setRowSummary(
    set: ExerciseSet,
    side: String?,
    mode: TrainingMode,
    customUnit: String?,
    predictedWeight: Double?,
    estimatedMetric: Double?,
): SetRowSummary {
    val target: UnilateralTarget? = if (side == null) null else sideTarget(set, side)
    fun <T> pick(fromSet: T?, fromTarget: T?): T? = if (target != null) fromTarget else fromSet
    val isAmrap = set.isAmrap || mode == TrainingMode.AMRAP
    val rpe = pick(set.targetRPE, target?.targetRPE)
    val rir = pick(set.targetRIR, target?.targetRIR)
    val weight = predictedWeight ?: pick(set.weight, target?.weight)
    val reps = pick(set.targetReps, target?.targetReps)
    val range = pick(set.targetRepsRange, target?.targetRepsRange)
    val duration = pick(set.targetDuration, target?.targetDuration)
    val value = pick(set.plannedTargetV2, target?.targetValue)
    val intensityMode = target?.intensityMode ?: set.intensityMode

    val kgText = weight?.let { "${formatSummaryNumber(it)} kg" }
    val dose = when (mode) {
        TrainingMode.RM -> {
            val estimated = estimatedMetric?.toInt()
            listOfNotNull(kgText, estimated?.let { "× $it" }).joinToString(" ").ifBlank {
                set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %" } ?: "—"
            }
        }
        TrainingMode.SOLO_RPE -> "Solo RPE"
        TrainingMode.TIME -> duration?.let { KpknSetFormat.doseTime(it) } ?: "—"
        TrainingMode.DISTANCE -> value?.let { "${formatSummaryNumber(it)} m" } ?: "—"
        TrainingMode.CUSTOM -> value?.let {
            "${formatSummaryNumber(it)} ${customUnit?.takeIf { unit -> unit.isNotBlank() } ?: "u"}"
        } ?: "—"
        else -> {
            val repsText = if (isAmrap) {
                (range?.min ?: reps)?.let { "$it+" }
            } else {
                range?.format() ?: reps?.toString()
            }
            when {
                kgText != null && repsText != null -> "$kgText × $repsText"
                kgText != null -> kgText
                repsText != null -> repsText
                else -> "—"
            }
        }
    }

    val intensity: String? = when {
        mode == TrainingMode.RM -> set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %" }
        mode == TrainingMode.SOLO_RPE -> rpe?.let { "RPE ${formatSummaryNumber(it)}" }
        isAmrap -> null
        else -> when (intensityMode ?: IntensityMode.RPE) {
            IntensityMode.RPE -> rpe?.let { "RPE ${formatSummaryNumber(it)}" }
            IntensityMode.RIR -> rir?.let { "RIR $it" }
            IntensityMode.FAILURE -> "Fallo"
            IntensityMode.SOLO_RM -> set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %" }
            IntensityMode.AMRAP, IntensityMode.LOAD -> null
        }
    }

    val load = when (set.loadModeV2 ?: LoadModeV2.LOAD) {
        LoadModeV2.LOAD -> null
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre"
        LoadModeV2.ASSISTED -> "Asistido"
    }

    return SetRowSummary(
        dose = dose,
        load = load,
        intensity = intensity,
        technique = techniqueSummary(set),
    )
}

/** «Drop × 2» o «RP × 3», con el número de drops o de mini-series que tiene la serie. */
private fun techniqueSummary(set: ExerciseSet): String? {
    val drop = set.plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
    if (drop != null) {
        val count = drop.params["count"]?.toIntOrNull()
            ?: drop.params["weightPcts"]?.split(",")?.size
            ?: DropSetPlanDefaults.DefaultDrops
        return "Drop × $count"
    }
    val restPause = set.plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE } ?: return null
    val count = restPause.params["count"]?.toIntOrNull() ?: RestPausePlanDefaults.DefaultCount
    return "RP × $count"
}

private fun formatSummaryNumber(value: Double): String = formatMax2Decimals(value).replace('.', ',')
