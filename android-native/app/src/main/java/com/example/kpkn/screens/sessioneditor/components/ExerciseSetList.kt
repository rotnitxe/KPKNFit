package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Carrusel de fichas de serie de 85 × 76 dp y un único foco de edición (B1/B2). Al tocar una ficha se abre su foco con
 * el contenido propio de RM, AMRAP o [InlineSetRow] en [SetRowPresentation.List]. El foco tiene sus acciones: «Aplicar a todas», «Duplicar» (solo
 * si la pantalla da [onTransformExercise]), «Subir», «Bajar» y «Eliminar» (siempre queda una serie).
 *
 * En unilateral, cada serie son dos fichas: L (izquierda) y R (derecha), con su letra y sin el azul y el rojo del original (B5).
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
    val predictedWeights = remember(exercise.trainingMode, exercise.reference1RM, exercise.referenceTrainingMax, exercise.prFor1RM, exercise.sets) {
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

    /** Añade el lado que falta (L o R) con la carga del otro lado, como «Añadir L/R» del original. */
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
        val predictedWeight = predictedWeights[set.id]
        val estimatedMetric = predictedMetrics[set.id]
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("set-focus-$rowKey")
                    .padding(bottom = 10.dp)
                    .background(KpknSurface.module, KpknShape.module)
                    .border(1.dp, KpknSurface.moduleBorder, KpknShape.module)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Serie ${index + 1} de ${exercise.sets.size}", style = KpknType.bodyStrong,
                        color = WizardColors.text, modifier = Modifier.weight(1f))
                    if (index > 0) KpknIconButton(
                        icon = KpknLineIcons.ChevronLeft, contentDescription = "Serie anterior",
                        onClick = { openKey = if (side == null) exercise.sets[index - 1].id else "${exercise.sets[index - 1].id}:$side" },
                    )
                    if (index < exercise.sets.lastIndex) KpknIconButton(
                        icon = KpknLineIcons.ChevronRight, contentDescription = "Serie siguiente",
                        onClick = { openKey = if (side == null) exercise.sets[index + 1].id else "${exercise.sets[index + 1].id}:$side" },
                    )
                }
                if (set.purpose.isAmrap) {
                    AmrapSetFocus(exercise, set, index, fixedUnilateralSide = side) { updater -> onUpdateSet(set.id, updater) }
                } else if (trainingMode == TrainingMode.RM) {
                    RmSetFocus(exercise, set, { updater -> onUpdateSet(set.id, updater) }, onTransformExercise, fixedUnilateralSide = side)
                } else InlineSetRow(
                    set = set,
                    index = index,
                    reference1RM = reference1RM,
                    trainingMax = exercise.referenceTrainingMax,
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
                SetRestControl(
                    seconds = set.restAfterSeconds ?: exercise.restTime ?: 90,
                    inherited = set.restAfterSeconds == null,
                    onChange = { seconds -> onUpdateSet(set.id) { it.copy(restAfterSeconds = seconds) } },
                    onReset = { onUpdateSet(set.id) { it.copy(restAfterSeconds = null) } },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (exercise.sets.size > 1) {
                        SetRowAction(text = "Aplicar a todas", onClick = { requestApply(set, side) })
                    }
                    if (onTransformExercise != null) {
                        SetRowAction(
                            text = "Duplicar",
                            onClick = { onTransformExercise { current -> current.withSetDuplicated(set.id) } },
                        )
                    }
                    if (index > 0) {
                        SetRowAction(text = "Subir", onClick = { onMoveSet(set.id, -1) })
                    }
                    if (index < exercise.sets.lastIndex) {
                        SetRowAction(text = "Bajar", onClick = { onMoveSet(set.id, 1) })
                    }
                    if (exercise.sets.size > 1) {
                        SetRowAction(
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
        val slots = buildList<Triple<ExerciseSet, Int, String?>> {
            exercise.sets.forEachIndexed { index, set ->
                if (!unilateral || set.isEmptySlot) add(Triple(set, index, null))
                else orderedSides.filter { sideTarget(set, it) != null }.forEach { add(Triple(set, index, it)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(SessionEditorChipMetrics.gap)) {
            items(slots, key = { (set, _, side) -> if (side == null) set.id else "${set.id}:$side" }) { (set, index, side) ->
                val rowKey = if (side == null) set.id else "${set.id}:$side"
                val summary = setRowSummary(set, side, trainingMode, customUnit, predictedWeights[set.id], predictedMetrics[set.id])
                EffectiveSeriesChip(
                    label = if (side == null) "S${index + 1}" else "${sideLetter(side)}${index + 1}",
                    load = if (set.isEmptySlot) "Hueco" else seriesChipValue(set, side, trainingMode, customUnit, predictedWeights[set.id]),
                    dose = if (set.isEmptySlot) "vacío" else seriesChipDetail(set, side, trainingMode, customUnit, summary),
                    description = listOfNotNull("Serie ${index + 1}", summary.load, summary.dose, summary.intensity, summary.technique).joinToString(" · "),
                    enabled = !set.isEmptySlot,
                    selected = openKey == rowKey,
                    tag = "set-row-$rowKey",
                    onClick = { if (!set.isEmptySlot) openKey = if (openKey == rowKey) null else rowKey },
                )
            }
        }
        slots.firstOrNull { (set, _, side) -> openKey == (if (side == null) set.id else "${set.id}:$side") }
            ?.let { (set, index, side) -> SetItem(set, index, side) }
        if (unilateral) exercise.sets.filterNot { it.isEmptySlot }.forEach { set ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                orderedSides.filter { sideTarget(set, it) == null }.forEach { side ->
                    KpknSecondaryPill(text = "+ Añadir ${sideLetter(side)}", onClick = { addSide(set, side) })
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
        EditorConfirmationSheet(
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

private fun sideLetter(side: String): String = if (side == "L") "L" else "R"

/** En la ficha cabe la cifra; al abrir el foco se editan todos los detalles. La semántica conserva la dosis completa. */
private fun seriesChipValue(set: ExerciseSet, side: String?, mode: TrainingMode, customUnit: String?, predicted: Double?): String {
    val target = side?.let { sideTarget(set, it) }
    return when (mode) {
        TrainingMode.SOLO_RPE -> (target?.targetRPE ?: set.targetRPE)?.let { "RPE\u00a0${formatSummaryNumber(it)}" } ?: "—"
        TrainingMode.TIME -> (target?.targetDuration ?: set.targetDuration)?.let { KpknSetFormat.doseTime(it) } ?: "—"
        TrainingMode.DISTANCE, TrainingMode.CUSTOM -> (target?.targetValue ?: set.plannedTargetV2)?.let { formatSummaryNumber(it) } ?: "—"
        else -> if (set.loadModeV2 == LoadModeV2.BODYWEIGHT) "Corporal"
            else (target?.weight ?: set.weight ?: predicted)?.let { "${formatSummaryNumber(it)}\u00a0kg" } ?: "—"
    }
}

private fun seriesChipDetail(set: ExerciseSet, side: String?, mode: TrainingMode, customUnit: String?, summary: SetRowSummary): String {
    val target = side?.let { sideTarget(set, it) }
    val dose = when {
        set.purpose.isAmrap -> set.amrapMinimumReps?.let { "$it+" } ?: "AMRAP"
        mode == TrainingMode.RM -> set.targetPercentageRM?.let { "${formatSummaryNumber(it)} %${if (set.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM) "TM" else "RM"}" } ?: "—"
        mode == TrainingMode.SOLO_RPE -> "objetivo"
        mode == TrainingMode.TIME -> summary.intensity.orEmpty()
        mode == TrainingMode.DISTANCE -> "m"
        mode == TrainingMode.CUSTOM -> customUnit?.ifBlank { "Unidad" } ?: "Unidad"
        else -> {
            target?.targetRepsRange?.format()
                ?: target?.targetReps?.toString()
                ?: set.targetRepsRange?.format()
                ?: set.targetReps?.toString()
                ?: "—"
        }
    }
    return if (mode == TrainingMode.REPS && !set.purpose.isAmrap) listOfNotNull(dose, summary.intensity).joinToString(" · ") else dose
}

internal data class SetRowSummary(
    val dose: String,
    val load: String?,
    val intensity: String?,
    val technique: String?,
)

/**
 * Resume una serie (o su lado) con la misma lógica que la fila: la carga sugerida si la hay, las reps o el rango, el tiempo o la
 * distancia según el modo, y la intensidad y la técnica. Los decimales van con coma y las unidades con espacio inseparable.
 */
internal fun setRowSummary(
    set: ExerciseSet,
    side: String?,
    mode: TrainingMode,
    customUnit: String?,
    predictedWeight: Double?,
    estimatedMetric: Double?,
): SetRowSummary {
    val target: UnilateralTarget? = if (side == null) null else sideTarget(set, side)
    fun <T> pick(fromSet: T?, fromTarget: T?): T? = if (target != null) fromTarget else fromSet
    val isAmrap = set.purpose.isAmrap
    val rpe = pick(set.targetRPE, target?.targetRPE)
    val rir = pick(set.targetRIR, target?.targetRIR)
    // ER2a: la fila enseña la carga guardada; la calculada solo aparece si la serie no tiene ninguna.
    val weight = pick(set.weight, target?.weight)?.takeIf { it > 0.0 } ?: predictedWeight
    val reps = pick(set.targetReps, target?.targetReps)
    val range = pick(set.targetRepsRange, target?.targetRepsRange)
    val duration = pick(set.targetDuration, target?.targetDuration)
    val value = pick(set.plannedTargetV2, target?.targetValue)
    val intensityMode = target?.intensityMode ?: set.intensityMode

    val kgText = weight?.let { "${formatSummaryNumber(it)} kg" }
    val dose = when (mode) {
        // ER2a: una serie RM con rango o RIR enseña su prescripción («95 kg × 4–6 · RIR 2»); la pura, como antes («95 kg × 7»).
        TrainingMode.RM -> if (range != null || rir != null) {
            rmPrescriptionDose(kgText, range, reps, rir, estimatedMetric?.toInt())
        } else {
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
                set.amrapMinimumReps?.let { "$it+" } ?: "AMRAP"
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
        mode == TrainingMode.SOLO_RPE -> rpe?.let { "RPE\u00A0${formatSummaryNumber(it)}" }
        isAmrap -> null
        else -> when (intensityMode ?: IntensityMode.RPE) {
            IntensityMode.RPE -> rpe?.let { "RPE\u00A0${formatSummaryNumber(it)}" }
            IntensityMode.RIR -> rir?.let { "RIR\u00A0$it" }
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

/**
 * Dosis de una serie RM con rango o RIR (ER2a): la carga guardada, las reps del rango («× 4–6») y el RIR («· RIR 2»). Sin rango usa
 * las reps que salen al %RM, como la dosis pura.
 */
private fun rmPrescriptionDose(kgText: String?, range: RepRange?, reps: Int?, rir: Int?, estimated: Int?): String {
    val repsText = range?.format() ?: reps?.toString() ?: estimated?.toString()
    val head = if (kgText != null && repsText != null) "$kgText × $repsText" else kgText ?: repsText
    // «RIR 2» con espacio inseparable: al partir la dosis a dos líneas, la cifra no se queda sola en la segunda.
    return listOfNotNull(head, rir?.let { "RIR $it" }).joinToString(" · ").ifBlank { "—" }
}

/**
 * Acción de la fila abierta (ER1c1, pulido): texto de tinta de 48 dp de alto como objetivo táctil. Al 130 % de letra puede pasar a dos
 * líneas por palabra en vez de cortarse.
 */
@Composable
private fun SetRowAction(
    text: String,
    onClick: () -> Unit,
    color: Color = WizardColors.text,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = KpknType.action, color = color, maxLines = 2)
    }
}
