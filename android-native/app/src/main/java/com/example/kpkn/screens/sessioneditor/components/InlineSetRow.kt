package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.SetPurpose
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.domain.training.RepRangeParser
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.withoutBeyondFailureTechniques
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.kpknEditorFieldColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatMax2Decimals
import com.example.kpkn.screens.sessioneditor.roundToMax2Decimals
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.formatEstimatedMetric
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Dónde se dibuja una serie. [Card] es la tarjeta del editor original (rondas de superserie y cajón en vivo, sin cambios de piel);
 * [List] es la fila del desplegado (ER1c1): sin tarjeta ni cabecera, con las piezas KPKN y la misma lógica.
 */
internal enum class SetRowPresentation { Card, List }

internal fun shouldShowPlannedIntensity(
    set: ExerciseSet,
    isUnilateral: Boolean,
    activeSideTarget: UnilateralTarget?,
    includeLoad: Boolean = true,
): Boolean {
    if (!includeLoad) {
        val modes = setOf(IntensityMode.RPE, IntensityMode.RIR, IntensityMode.FAILURE)
        return if (isUnilateral) activeSideTarget?.let {
            it.intensityMode in modes || it.targetRPE != null || it.targetRIR != null
        } == true else set.intensityMode in modes || set.targetRPE != null || set.targetRIR != null || set.isFailure
    }
    if (isUnilateral) {
        return activeSideTarget?.let { target ->
            target.intensityMode != null ||
                target.targetRPE != null ||
                target.targetRIR != null ||
                target.weight != null
        } == true
    }

    return set.intensityMode != null ||
        set.targetRPE != null ||
        set.targetRIR != null ||
        set.targetPercentageRM != null ||
        set.weight != null ||
        set.isFailure ||
        set.purpose.isAmrap
}

@Composable
internal fun InlineSetRow(
    set: ExerciseSet,
    index: Int,
    reference1RM: Double?,
    predictedWeight: Double?,
    estimatedMetric: Double?,
    /** TM declarado del ejercicio: referencia de las series `PERCENT_TM` (el 1RM no la sustituye). */
    trainingMax: Double? = null,
    trainingMode: TrainingMode,
    customUnit: String?,
    accentColor: Color,
    backgroundAccentColor: Color = accentColor,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isUnilateral: Boolean = false,
    fixedUnilateralSide: String? = null,
    showSetActions: Boolean = true,
    unilateralIntensityMode: UnilateralIntensityMode = UnilateralIntensityMode.SHARED,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTechniqueConfigExpandedChange: (Boolean) -> Unit = {},
    fillHeight: Boolean = true,
    presentation: SetRowPresentation = SetRowPresentation.Card,
    /** Abre la hoja de la carga inteligente (la cifra de carga de la fila es su botón). Sin callback, la cifra no es un botón. */
    onOpenSmartLoad: (() -> Unit)? = null,
) {
    if (set.isEmptySlot) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = accentColor.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("RONDA ${index + 1} · HUECO VACÍO", fontWeight = FontWeight.Black, color = accentColor)
                Text("No reindexa las rondas siguientes", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.58f))
            }
        }
        return
    }
    var showIntensityMenu by remember(set.id) { mutableStateOf(false) }
    var showLoadModeMenu by remember(set.id) { mutableStateOf(false) }
    // La intensidad es opcional: siempre se parte desde "Programar intensidad".
    var showPlannedIntensity by rememberSaveable(set.id) { mutableStateOf(false) }
    val listSkin = presentation == SetRowPresentation.List
    val isNarrowScreen = rememberSessionEditorBreakpoint() == SessionEditorBreakpoint.Compact ||
        com.example.kpkn.ui.adapt.LocalViewportAdapt.current.shouldReflow
    val setCardDensity = when (rememberSessionEditorBreakpoint()) {
        SessionEditorBreakpoint.Compact -> SetCardDensity.Compact
        SessionEditorBreakpoint.Normal -> SetCardDensity.Comfortable
        SessionEditorBreakpoint.Comfortable -> SetCardDensity.Comfortable
    }
    val isRmMode = trainingMode == TrainingMode.RM
    val usesExplicitTmPercent = isRmMode && set.loadBasis == LoadBasis.PERCENT_TM &&
        (set.loadModeV2 ?: LoadModeV2.LOAD) == LoadModeV2.LOAD
    val isSoloRpeMode = trainingMode == TrainingMode.SOLO_RPE
    val isAmrapMode = set.purpose.isAmrap
    var selectedUniSide by remember(set.id, fixedUnilateralSide) { mutableStateOf(fixedUnilateralSide ?: "L") }
    val activeUniSide = fixedUnilateralSide ?: selectedUniSide
    fun commitEditedField(updater: (ExerciseSet) -> ExerciseSet) {
        onUpdate(updater)
    }
    val setStateKeySuffix = if (isUnilateral) activeUniSide else "B"
    // El %RM de la serie se lee sobre la referencia de SU base: el TM en PERCENT_TM, el 1RM en el resto.
    val percentReference = if (usesExplicitTmPercent) trainingMax else reference1RM
    val sliderPercent = remember(set.targetPercentageRM, set.targetReps, set.targetRepsRange, set.intensityMode, predictedWeight, percentReference) {
        when {
            isRmMode && set.targetPercentageRM != null -> set.targetPercentageRM
            predictedWeight != null && percentReference != null && percentReference > 0 -> ((predictedWeight / percentReference) * 100.0).coerceIn(40.0, 100.0)
            set.plannedRepAnchor() != null -> estimatePercent1RM(set.plannedRepAnchor()!!)
            else -> 75.0
        }
    }
    var localSliderPercent by remember(set.id, sliderPercent) { mutableStateOf(sliderPercent.toFloat()) }
    LaunchedEffect(sliderPercent) {
        // Sync when external value changes and not dragging (approx by checking equality)
        if (kotlin.math.abs(localSliderPercent - sliderPercent.toFloat()) > 0.01f) {
            localSliderPercent = sliderPercent.toFloat()
        }
    }
    val metricLabel = when (trainingMode) {
        TrainingMode.RM -> if (usesExplicitTmPercent) "Reps" else "Reps est."
        TrainingMode.REPS -> if (isAmrapMode) "Reps mín." else "Reps / rango"
        TrainingMode.TIME -> if (isAmrapMode) "Tiempo mín." else "Tiempo"
        TrainingMode.DISTANCE -> if (isAmrapMode) "Dist. mín." else "Dist."
        TrainingMode.CUSTOM -> if (isAmrapMode) "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} mín." else (customUnit?.ifBlank { "Unidad" } ?: "Unidad")
        TrainingMode.SOLO_RPE -> "RPE obj."
        TrainingMode.AMRAP -> "AMRAP / mín."
    }
    val activeSideTarget = if (isUnilateral) {
        if (activeUniSide == "L") set.leftTarget else set.rightTarget
    } else null
    // ER2a: la línea de carga enseña la guardada (la de la serie o la de su lado); la calculada solo si no hay ninguna.
    val savedWeight = (if (isUnilateral && activeSideTarget != null) activeSideTarget.weight else set.weight)?.takeIf { it > 0.0 }
    val displayedWeight = savedWeight ?: predictedWeight
    val externalPlannedIntensity = shouldShowPlannedIntensity(
        set = set,
        isUnilateral = isUnilateral,
        activeSideTarget = activeSideTarget,
        includeLoad = !listSkin,
    )
    LaunchedEffect(
        set.id,
        isUnilateral,
        activeUniSide,
        set.intensityMode,
        set.targetRPE,
        set.targetRIR,
        set.targetPercentageRM,
        set.weight,
        set.isFailure,
        activeSideTarget?.intensityMode,
        activeSideTarget?.targetRPE,
        activeSideTarget?.targetRIR,
        activeSideTarget?.weight,
    ) {
        if (externalPlannedIntensity) {
            // Rules and other external edits must reveal the persisted selector.
            // Do not reset it when the user is editing a local field.
            showPlannedIntensity = true
        }
    }
    fun uniOrSetDbl(getSet: (ExerciseSet) -> Double?, getTarget: (UnilateralTarget?) -> Double?): Double? =
        if (isUnilateral && activeSideTarget != null) getTarget(activeSideTarget) else getSet(set)
    fun uniOrSetInt(getSet: (ExerciseSet) -> Int?, getTarget: (UnilateralTarget?) -> Int?): Int? =
        if (isUnilateral && activeSideTarget != null) getTarget(activeSideTarget) else getSet(set)
    val metricValue = when (trainingMode) {
        TrainingMode.RM -> if (usesExplicitTmPercent) {
            val range = if (isUnilateral) activeSideTarget?.targetRepsRange else set.targetRepsRange
            range?.format() ?: uniOrSetInt({ it.targetReps }, { it?.targetReps })?.toString().orEmpty()
        } else {
            formatEstimatedMetric(estimatedMetric, trainingMode, customUnit)
        }
        TrainingMode.TIME -> (
            if (isUnilateral && activeSideTarget != null) activeSideTarget.targetDuration else set.targetDuration
        )?.toString().orEmpty()
        TrainingMode.SOLO_RPE -> formatEditableNumber(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE }))
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> formatEditableNumber(
            if (isUnilateral && activeSideTarget != null) activeSideTarget.targetValue else set.plannedTargetV2
        ).ifBlank { (uniOrSetInt({ it.targetReps }, { it?.targetReps })?.toString()).orEmpty() }
        else -> {
            val range = if (isUnilateral) activeSideTarget?.targetRepsRange else set.targetRepsRange
            val plannedReps = uniOrSetInt({ it.targetReps }, { it?.targetReps })
            // AMRAP stores the optional range as a minimum plus a legacy max
            // mirror. The editor must expose the minimum here; showing 4–6 in
            // a field labelled “Reps mín.” incorrectly suggests a fixed target.
            if (isAmrapMode) {
                set.amrapMinimumReps?.toString().orEmpty()
            } else {
                range?.format() ?: plannedReps?.toString().orEmpty()
            }
        }
    }
    val intensityValue = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> formatEditableNumber(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE }))
        IntensityMode.RIR -> (uniOrSetInt({ it.targetRIR }, { it?.targetRIR })?.toString()).orEmpty()
        IntensityMode.FAILURE -> ""
        IntensityMode.SOLO_RM -> formatEditableNumber(set.targetPercentageRM ?: sliderPercent)
        IntensityMode.AMRAP -> ""
        IntensityMode.LOAD -> formatEditableNumber(uniOrSetDbl({ it.weight }, { it?.weight }))
    }

    fun updateUniSet(updater: (UnilateralTarget) -> UnilateralTarget): ((ExerciseSet) -> ExerciseSet) {
        val side = activeUniSide
        return { current ->
            val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
            val updated = updater(currentSide)
            if (unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                val mirrored = updated.copy()
                if (side == "L") current.copy(leftTarget = updated, rightTarget = mirrored)
                else current.copy(rightTarget = updated, leftTarget = mirrored)
            } else {
                if (side == "L") current.copy(leftTarget = updated)
                else current.copy(rightTarget = updated)
            }
        }
    }
    // ER2a: la serie RM con rango o RIR se edita con «Reps» (el rango) y «RIR», junto al %RM. Sin ninguno, queda como antes.
    val rmRange = if (isUnilateral) activeSideTarget?.targetRepsRange else set.targetRepsRange
    val rmRir = uniOrSetInt({ it.targetRIR }, { it?.targetRIR })
    val rmDetailed = isRmMode && !usesExplicitTmPercent && (rmRange != null || rmRir != null)
    val onRmRirCommit: (String) -> Unit = { input ->
        val rir = input.safeIntOrNull()
        commitEditedField(if (isUnilateral) updateUniSet { it.copy(targetRIR = rir) } else { current -> current.copy(targetRIR = rir) })
    }
    val intensityLabel = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> "RPE"
        IntensityMode.RIR -> "RIR"
        IntensityMode.FAILURE -> "Fallo"
        IntensityMode.SOLO_RM -> "Solo RM"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.LOAD -> "Carga"
    }
    val intensityValueLabel = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> "RPE"
        IntensityMode.RIR -> "RIR"
        IntensityMode.FAILURE -> "Fallo"
        IntensityMode.SOLO_RM -> "%RM"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.LOAD -> "Carga"
    }
    val loadModeLabel = when (set.loadModeV2 ?: LoadModeV2.LOAD) {
        LoadModeV2.LOAD -> "Carga externa"
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val estimatedSurface = Color.White.copy(alpha = 0.06f)
    val estimatedText = Color.White.copy(alpha = 0.90f)
    // Tono oscuro con el énfasis de la tarjeta para el bloque de intensidad.
    val intensitySurface = lerp(DarkEditorChip, accentColor, 0.08f)
    val intensityBorder = accentColor.copy(alpha = 0.18f)
    val intensityLabelColor = lerp(Color.White, accentColor, 0.30f)

    // La misma lógica en la tarjeta y en la fila: cada campo escribe igual, solo cambia la piel.
    val onPercentCommit: (String) -> Unit = { input ->
        commitEditedField { current ->
            current.copy(
                targetPercentageRM = input.safeDoubleOrNull()?.let {
                    if (usesExplicitTmPercent) it.takeIf { value -> value.isFinite() && value > 0.0 }
                    else roundToMax2Decimals(it)
                },
                intensityMode = null,
                targetRPE = null,
                isFailure = false,
            )
        }
    }
    val onSliderFinished: () -> Unit = {
        val v = roundToMax2Decimals(localSliderPercent.toDouble())
        if (v != set.targetPercentageRM) {
            commitEditedField { current -> current.copy(targetPercentageRM = v, intensityMode = null) }
        }
    }
    val onSoloRpeCommit: (String) -> Unit = { input ->
        commitEditedField(if (isUnilateral) updateUniSet { it.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE) } else { current ->
            current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE, targetRIR = null, targetPercentageRM = null, targetReps = null, targetDuration = null, isFailure = false, purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null)
        })
    }
    val onMetricCommit: (String) -> Unit = { input ->
        if (set.purpose.isAmrap) {
            commitEditedField { current -> current.copy(amrapMinimumReps = RepRangeParser.parse(input)?.min ?: input.safeIntOrNull()) }
        } else commitEditedField(
            if (isUnilateral) updateUniSet {
                when (trainingMode) {
                    TrainingMode.TIME -> it.copy(targetDuration = input.safeIntOrNull())
                    TrainingMode.DISTANCE,
                    TrainingMode.CUSTOM,
                    -> it.copy(targetValue = input.safeDoubleOrNull())
                    else -> {
                        val parsed = RepRangeParser.parse(input)
                        it.copy(
                            targetReps = parsed?.max ?: input.safeIntOrNull(),
                            targetRepsRange = parsed?.takeIf { range -> range.min != range.max },
                        )
                    }
                }
            } else { current ->
                when (trainingMode) {
                    TrainingMode.TIME -> current.copy(targetDuration = input.safeIntOrNull())
                    TrainingMode.DISTANCE,
                    TrainingMode.CUSTOM,
                    -> current.copy(plannedTargetV2 = input.safeDoubleOrNull())
                    else -> {
                        val parsed = RepRangeParser.parse(input)
                        current.copy(
                            targetReps = parsed?.max ?: input.safeIntOrNull(),
                            targetRepsRange = parsed?.takeIf { range -> range.min != range.max },
                            amrapMinimumReps = if (current.purpose.isAmrap) parsed?.min ?: input.safeIntOrNull() else current.amrapMinimumReps,
                        )
                    }
                }
            },
        )
    }
    val onIntensityValueCommit: (String) -> Unit = { input ->
        commitEditedField(
            if (isUnilateral) updateUniSet {
                when (set.intensityMode ?: IntensityMode.RPE) {
                    IntensityMode.RPE -> it.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                    IntensityMode.RIR -> it.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                    IntensityMode.LOAD -> it.copy(weight = input.safeDoubleOrNull())
                    else -> it
                }
            } else { current ->
                when (current.intensityMode ?: IntensityMode.RPE) {
                    IntensityMode.RPE -> current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                    IntensityMode.RIR -> current.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                    IntensityMode.LOAD -> current.copy(weight = input.safeDoubleOrNull(), intensityMode = IntensityMode.LOAD)
                    else -> current
                }
            },
        )
    }
    // Elegir RPE, RIR o Fallo (el menú de la tarjeta o el segmentado de la fila).
    fun applyIntensityMode(mode: IntensityMode) {
        fun commitMode(change: (ExerciseSet) -> ExerciseSet) = commitEditedField { current ->
            val updated = change(current)
            if (mode == IntensityMode.FAILURE) updated else updated.withoutBeyondFailureTechniques()
        }
        val updater: (ExerciseSet) -> ExerciseSet = {
            when (mode) {
                IntensityMode.RPE -> it.copy(intensityMode = IntensityMode.RPE, isFailure = false, purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null, targetRPE = it.targetRPE ?: 8.0, targetRIR = null, targetPercentageRM = null)
                IntensityMode.RIR -> it.copy(intensityMode = IntensityMode.RIR, isFailure = false, purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null, targetRIR = it.targetRIR ?: 2, targetRPE = null, targetPercentageRM = null)
                IntensityMode.FAILURE -> it.copy(intensityMode = IntensityMode.FAILURE, isFailure = true, purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null, targetRIR = null, targetRPE = null, targetPercentageRM = null)
                else -> it
            }
        }
        if (isUnilateral) {
            val side = activeUniSide
            commitMode { current ->
                val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
                val temp = ExerciseSet(id = "", targetRPE = currentSide.targetRPE, targetRIR = currentSide.targetRIR, intensityMode = currentSide.intensityMode ?: current.intensityMode, weight = currentSide.weight, targetPercentageRM = current.targetPercentageRM)
                val updated = updater(temp)
                val newSide = currentSide.copy(targetRPE = updated.targetRPE, targetRIR = updated.targetRIR, intensityMode = updated.intensityMode, weight = updated.weight)
                if (side == "L") current.copy(leftTarget = newSide, isFailure = updated.isFailure, purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null, intensityMode = updated.intensityMode, targetPercentageRM = updated.targetPercentageRM)
                else current.copy(rightTarget = newSide, isFailure = updated.isFailure, purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null, intensityMode = updated.intensityMode, targetPercentageRM = updated.targetPercentageRM)
            }
        } else {
            commitMode(updater)
        }
    }
    @Composable
    fun FieldsRow() {
        val metricWeight = if (listSkin) {
            if (isAmrapMode) 1.35f else 1f
        } else {
            if (isAmrapMode) if (isNarrowScreen) 1.2f else 1.35f else 0.55f
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (listSkin) 8.dp else if (isNarrowScreen) 6.dp else 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (isRmMode) {
                SetValueCell(
                    label = if (usesExplicitTmPercent) "% del TM" else "%RM",
                    value = formatEditableNumber(set.targetPercentageRM ?: sliderPercent),
                    stateKey = "percent-${set.id}",
                    listSkin = listSkin,
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Decimal,
                    onCommit = onPercentCommit,
                )
                SetValueCell(
                    label = metricLabel,
                    value = metricValue,
                    stateKey = "metric-${set.id}",
                    listSkin = listSkin,
                    modifier = Modifier.weight(1f),
                    enabled = false,
                    onCommit = {},
                )
            } else if (isSoloRpeMode) {
                SetValueCell(
                    label = metricLabel,
                    value = metricValue,
                    stateKey = "metric-${set.id}-${setStateKeySuffix}",
                    listSkin = listSkin,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Decimal,
                    onCommit = onSoloRpeCommit,
                )
            } else {
                SetValueCell(
                    label = metricLabel,
                    value = metricValue,
                    stateKey = "metric-${set.id}-${setStateKeySuffix}",
                    listSkin = listSkin,
                    modifier = Modifier.weight(metricWeight),
                    keyboardType = if (trainingMode == TrainingMode.REPS || trainingMode == TrainingMode.AMRAP) KeyboardType.Ascii else KeyboardType.Number,
                    onCommit = onMetricCommit,
                )
            }
            if (!isAmrapMode && !isRmMode && !isSoloRpeMode) {
                if (!showPlannedIntensity) {
                    // Botón "Intensidad" con el mismo componente y tamaño que el input de reps.
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Intensidad", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.94f),
                            ),
                            colors = kpknEditorFieldColors(),
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    showPlannedIntensity = true
                                    commitEditedField { current ->
                                        current.copy(
                                            intensityMode = IntensityMode.RPE,
                                            targetRPE = current.targetRPE ?: 8.0,
                                            targetRIR = null,
                                            isFailure = false,
                                            purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null,
                                        )
                                    }
                                },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(modifier = Modifier.weight(1.6f)) {
                            OutlinedTextField(
                                value = intensityLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Intensidad", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.94f),
                                ),
                                colors = kpknEditorFieldColors(),
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showIntensityMenu = true },
                            )
                            KpknDropdownMenu(
                                expanded = showIntensityMenu,
                                onDismissRequest = { showIntensityMenu = false },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                listOf(
                                    IntensityMode.RPE to "RPE",
                                    IntensityMode.RIR to "RIR",
                                    IntensityMode.FAILURE to "Fallo",
                                ).forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            showIntensityMenu = false
                                            applyIntensityMode(mode)
                                        },
                                    )
                                }
                            }
                        }
                        // En "Fallo" no hay valor que ingresar: solo queda el modo puesto.
                        if ((set.intensityMode ?: IntensityMode.RPE) != IntensityMode.FAILURE) {
                            AccentSetValueField(
                                label = intensityValueLabel,
                                value = intensityValue,
                                accentColor = accentColor,
                                stateKey = "intensity-${set.id}-${setStateKeySuffix}",
                                keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number,
                                modifier = Modifier.weight(1f),
                                onCommit = onIntensityValueCommit,
                            )
                        }
                    }
                }
            } else if (isAmrapMode) {
                if (listSkin) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Hueco del rótulo: la píldora AMRAP queda a la altura de la cifra de reps.
                        Text(" ", style = KpknType.caption, minLines = 2, maxLines = 2)
                        AmrapTag(calibrator = (set.purpose == SetPurpose.CALIBRATOR), modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                        color = accentColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = if ((set.purpose == SetPurpose.CALIBRATOR)) "AMRAP calibrador" else "AMRAP",
                            modifier = Modifier.padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 8.dp else 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
        }
    }

    /** Línea de carga: «82,5 kg · 75 % RM», o la pista de carga inteligente si aún no hay carga. La fila usa comas y espacios inseparables. */
    fun estimateLine(listSkin: Boolean): String {
        val space = if (listSkin) " " else " "
        val decimal: (Double) -> String = { value ->
            if (listSkin) formatMax2Decimals(value).replace('.', ',') else formatMax2Decimals(value)
        }
        return buildString {
            append(displayedWeight?.let { "${decimal(it)}${space}kg" } ?: "Usa carga inteligente para estimar la carga inicial")
            if (isRmMode && percentReference != null) {
                val basis = if (usesExplicitTmPercent) "del TM" else "RM"
                val percentSpace = if (listSkin) " " else ""
                append(" · ${decimal(sliderPercent.toDouble())}$percentSpace% $basis")
            }
        }
    }

    @Composable
    fun EstimateBox() {
        if (isSoloRpeMode) return
            Surface(
                shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                color = estimatedSurface,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 4.dp else 5.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 3.dp else 4.dp),
                ) {
                    Text(
                        text = estimateLine(listSkin = false),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = estimatedText,
                    )
                    if (isRmMode) {
                        Slider(
                            value = localSliderPercent,
                            onValueChange = { localSliderPercent = it },
                            onValueChangeFinished = onSliderFinished,
                            valueRange = 45f..100f,
                            enabled = percentReference != null,
                        )
                    }
                }
            }
    }

    LaunchedEffect(set.isFailure, set.intensityMode) {
        if (!set.isFailure && set.intensityMode != IntensityMode.FAILURE) onTechniqueConfigExpandedChange(false)
    }

    @Composable
    fun TechniqueSection() {
        if (!set.isFailure && set.intensityMode != IntensityMode.FAILURE) return
        // En la fila de la lista el rótulo «¿Vas más allá del fallo?» lo dibuja el propio bloque de técnicas (ed-tech).
        if (!listSkin) Text("¿Vas más allá del fallo?", style = KpknType.bodyStrong, color = WizardColors.text)
        InlineSetRowTechniqueChips(
            set = set,
            onUpdate = onUpdate,
            onConfigExpandedChange = onTechniqueConfigExpandedChange,
            presentation = presentation,
        )
    }

    if (listSkin) {
        // `ed-serie` (Pulido v1 · EDITOR): carga, dosis e intensidad como en el lienzo. El encabezado «Serie N de M», el descanso y las
        // acciones los pone quien contiene la serie (ExerciseSetList, el cajón en vivo, las rondas de superserie).
        val repRange = if (isUnilateral) activeSideTarget?.targetRepsRange else set.targetRepsRange
        val plannedReps = uniOrSetInt({ it.targetReps }, { it?.targetReps })
        var rangeMode by remember(set.id, activeUniSide) { mutableStateOf(repRange != null && repRange.min != repRange.max) }
        LaunchedEffect(repRange?.min, repRange?.max) {
            if (repRange != null && repRange.min != repRange.max) rangeMode = true
        }
        val baseReps = plannedReps ?: repRange?.max ?: 8
        val rMin = repRange?.min ?: (baseReps - 2).coerceAtLeast(1)
        val rMax = repRange?.max ?: baseReps
        fun commitRange(min: Int, max: Int) = commitEditedField(
            if (isUnilateral) updateUniSet { it.copy(targetReps = max, targetRepsRange = RepRange(min, max)) }
            else { current -> current.copy(targetReps = max, targetRepsRange = RepRange(min, max)) },
        )
        val intensityUnit = set.intensityMode ?: IntensityMode.RPE
        val intensityNumber: Double? = when (intensityUnit) {
            IntensityMode.RPE -> uniOrSetDbl({ it.targetRPE }, { it?.targetRPE })
            IntensityMode.RIR -> uniOrSetInt({ it.targetRIR }, { it?.targetRIR })?.toDouble()
            else -> null
        }
        val intensityIsRpe = intensityUnit == IntensityMode.RPE
        fun commitIntensity(raw: Double) {
            val bounded = if (intensityIsRpe) (kotlin.math.round(raw * 2.0) / 2.0).coerceIn(1.0, 10.0) else kotlin.math.round(raw).coerceIn(0.0, 5.0)
            onIntensityValueCommit(formatMax2Decimals(bounded))
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isSoloRpeMode) {
                EdLoadSection(
                    mode = set.loadModeV2 ?: LoadModeV2.LOAD,
                    weightKg = displayedWeight,
                    onChangeMode = { mode -> commitEditedField { current -> current.copy(loadModeV2 = mode) } },
                    onOpenSmartLoad = onOpenSmartLoad,
                )
            }
            when {
                isSoloRpeMode -> SoloRpeGoal(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE })) { onSoloRpeCommit(it.toString()) }
                trainingMode == TrainingMode.TIME -> DurationGoal(metricValue.safeIntOrNull()) { onMetricCommit(it.toString()) }
                trainingMode == TrainingMode.DISTANCE || trainingMode == TrainingMode.CUSTOM -> MetricGoal(
                    label = if (trainingMode == TrainingMode.DISTANCE) "Distancia (m)" else customUnit?.ifBlank { "Unidad" } ?: "Unidad",
                    hint = if (trainingMode == TrainingMode.DISTANCE) "metros por serie"
                        else if (customUnit.isNullOrBlank()) "unidad por configurar" else "valor por serie",
                    unit = if (trainingMode == TrainingMode.DISTANCE) "m" else null,
                    value = metricValue.safeDoubleOrNull(),
                    step = if (trainingMode == TrainingMode.DISTANCE) 5.0 else 1.0,
                ) { onMetricCommit(it.toString()) }
                else -> EdRepsSection(
                    rangeMode = rangeMode,
                    reps = plannedReps ?: repRange?.max,
                    rangeMin = rMin,
                    rangeMax = rMax,
                    onRangeModeChange = { toRange ->
                        rangeMode = toRange
                        if (toRange) commitRange((baseReps - 2).coerceAtLeast(1), baseReps) else onMetricCommit(rMax.toString())
                    },
                    onStepReps = { direction -> onMetricCommit((baseReps + direction).coerceIn(1, 100).toString()) },
                    onCommitReps = { input -> onMetricCommit(input) },
                    onStepMin = { direction -> commitRange((rMin + direction).coerceIn(1, rMax), rMax) },
                    onCommitMin = { input -> input.safeIntOrNull()?.let { commitRange(it.coerceIn(1, rMax), rMax) } },
                    onStepMax = { direction -> commitRange(rMin, (rMax + direction).coerceIn(rMin, 100)) },
                    onCommitMax = { input -> input.safeIntOrNull()?.let { commitRange(rMin, it.coerceIn(rMin, 100)) } },
                )
            }
            if (!isSoloRpeMode && !isAmrapMode && !isRmMode) {
                EdIntensitySection(
                    unit = if (showPlannedIntensity) intensityUnit else null,
                    valueText = intensityNumber?.let(::edNumber).orEmpty(),
                    canDecrement = intensityNumber == null || intensityNumber > (if (intensityIsRpe) 1.0 else 0.0),
                    canIncrement = intensityNumber == null || intensityNumber < (if (intensityIsRpe) 10.0 else 5.0),
                    onAdd = {
                        showPlannedIntensity = true
                        commitEditedField { current ->
                            current.copy(
                                intensityMode = IntensityMode.RPE,
                                targetRPE = current.targetRPE ?: 8.0,
                                targetRIR = null,
                                isFailure = false,
                                purpose = SetPurpose.NONE, amrapMinimumReps = null, calibrationScope = null,
                            )
                        }
                    },
                    onPickUnit = { mode -> applyIntensityMode(mode) },
                    onStep = { direction ->
                        val base = intensityNumber ?: if (intensityIsRpe) 8.0 else 2.0
                        commitIntensity(base + direction * if (intensityIsRpe) 0.5 else 1.0)
                    },
                    onCommitText = { input -> input.safeDoubleOrNull()?.let { commitIntensity(it) } },
                    beyondFailure = { TechniqueSection() },
                )
            }
        }
        return
    }

    // Chips externos: número de serie + tipo de carga + acciones, siguiendo a su tarjeta.
    Column(
        modifier = if (fillHeight) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = RoundedCornerShape(999.dp), color = accentColor.copy(alpha = 0.12f)) {
                Text(
                    text = "SERIE ${index + 1}${if (isUnilateral) " (${activeUniSide})" else ""}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (isUnilateral && fixedUnilateralSide == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("L" to Color(0xFF2196F3), "R" to Color(0xFFFF5252)).forEach { (label, sideColor) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedUniSide == label) sideColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                            border = BorderStroke(
                                1.dp,
                                if (selectedUniSide == label) sideColor.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            ),
                            modifier = Modifier.clickable { selectedUniSide = label },
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                fontWeight = if (selectedUniSide == label) FontWeight.Black else FontWeight.Medium,
                                color = if (selectedUniSide == label) sideColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (showSetActions) Box {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = DarkEditorChip,
                    modifier = Modifier.clickable { showLoadModeMenu = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            loadModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                    }
                }
                KpknDropdownMenu(
                    expanded = showLoadModeMenu,
                    onDismissRequest = { showLoadModeMenu = false },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    listOf(
                        LoadModeV2.LOAD to "Carga externa",
                        LoadModeV2.BODYWEIGHT to "Peso corporal",
                        LoadModeV2.LASTRE to "Lastre",
                        LoadModeV2.ASSISTED to "Asistido",
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                showLoadModeMenu = false
                                commitEditedField { current -> current.copy(loadModeV2 = mode) }
                            },
                        )
                    }
                }
            }
            if (showSetActions) {
                IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        SetEditorCard(
            accentColor = accentColor,
            backgroundAccentColor = backgroundAccentColor,
            density = setCardDensity,
            modifier = if (fillHeight) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = if (fillHeight) {
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
                verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 5.dp else 6.dp),
            ) {
                FieldsRow()
                EstimateBox()
                TechniqueSection()
            }
        }
    }
}

/**
 * Campo de una cifra de la serie. En la tarjeta es el [EditorMiniField] de siempre; en la fila, una [KpknNumberCell] con su rótulo
 * encima. Con [enabled] en falso (Reps est.) solo muestra el valor.
 */
@Composable
internal fun SetValueCell(
    label: String,
    value: String,
    stateKey: String,
    listSkin: Boolean,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    labelMinLines: Int = 1,
    labelMaxLines: Int = 1,
    labelOverflow: TextOverflow = TextOverflow.Ellipsis,
    onCommit: (String) -> Unit,
) {
    if (listSkin) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Rótulo de una línea justo encima de su celda, con el mismo borde izquierdo.
            Text(
                label,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                minLines = labelMinLines,
                maxLines = labelMaxLines,
                overflow = labelOverflow,
            )
            // Celda de 48 dp con filete blanco al 12 %: se lee como campo también sobre el panel.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                KpknNumberCell(
                    value = value.replace('.', ','),
                    input = if (enabled) KpknCellInput.Text(keyboardType = keyboardType, onCommit = onCommit) else KpknCellInput.ReadOnly,
                    contentDescription = label,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        EditorMiniField(
            label = label,
            value = value,
            stateKey = stateKey,
            keyboardType = keyboardType,
            enabled = enabled,
            modifier = modifier,
            onCommit = onCommit,
        )
    }
}

/** Píldora de AMRAP o AMRAP calibrador de la fila (solo lectura, como en la tarjeta). */
@Composable
private fun AmrapTag(calibrator: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(KpknSurface.inset, RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (calibrator) "AMRAP calibrador" else "AMRAP",
            style = KpknType.chip,
            color = WizardColors.text,
            maxLines = 1,
        )
    }
}

/** Deslizador del %RM con la tinta de la app (Material con tokens, como indica el brief común). */
@Composable
internal fun inkSliderColors() = SliderDefaults.colors(
    thumbColor = WizardColors.text,
    activeTrackColor = WizardColors.text,
    inactiveTrackColor = Color.White.copy(alpha = 0.12f),
    disabledThumbColor = WizardColors.textFaint,
    disabledActiveTrackColor = WizardColors.textFaint,
    disabledInactiveTrackColor = Color.White.copy(alpha = 0.08f),
)

/** Campo del valor de intensidad con el énfasis de la tarjeta en un tono oscuro. */
@Composable
private fun AccentSetValueField(
    label: String,
    value: String,
    accentColor: Color,
    stateKey: String,
    keyboardType: KeyboardType,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var localValue by rememberSaveable(stateKey) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    val latestLocal = rememberUpdatedState(localValue)
    val latestValue = rememberUpdatedState(value)
    val latestCommit = rememberUpdatedState(onCommit)
    val commitWhileTyping = keyboardType == KeyboardType.Number ||
        keyboardType == KeyboardType.Decimal ||
        keyboardType == KeyboardType.Ascii
    LaunchedEffect(stateKey, value, isFocused) {
        if (!isFocused && value != localValue) {
            localValue = value
        }
    }
    LaunchedEffect(localValue, isFocused, commitWhileTyping) {
        if (!isFocused || !commitWhileTyping) return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        if (latestLocal.value != latestValue.value &&
            com.example.kpkn.screens.sessioneditor.isCommitableEditorInput(latestLocal.value, keyboardType)
        ) {
            latestCommit.value(latestLocal.value)
        }
    }
    DisposableEffect(stateKey) {
        onDispose {
            if (latestLocal.value != latestValue.value) {
                latestCommit.value(latestLocal.value)
            }
        }
    }
    val surfaceColor = lerp(DarkEditorChip, accentColor, 0.16f)
    OutlinedTextField(
        value = localValue,
        onValueChange = {
            localValue = it
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (localValue != value) onCommit(localValue)
        }),
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            if (wasFocused && !focusState.isFocused && localValue != value) {
                onCommit(localValue)
            }
        },
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.94f),
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surfaceColor,
            unfocusedContainerColor = surfaceColor,
            disabledContainerColor = surfaceColor.copy(alpha = 0.6f),
            focusedTextColor = Color.White.copy(alpha = 0.96f),
            unfocusedTextColor = Color.White.copy(alpha = 0.92f),
            disabledTextColor = Color.White.copy(alpha = 0.45f),
            focusedLabelColor = lerp(Color.White, accentColor, 0.6f),
            unfocusedLabelColor = lerp(Color.White, accentColor, 0.4f),
            disabledLabelColor = Color.White.copy(alpha = 0.35f),
            cursorColor = accentColor,
            focusedBorderColor = accentColor.copy(alpha = 0.45f),
            unfocusedBorderColor = accentColor.copy(alpha = 0.22f),
            disabledBorderColor = accentColor.copy(alpha = 0.10f),
            focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
        ),
    )
}
