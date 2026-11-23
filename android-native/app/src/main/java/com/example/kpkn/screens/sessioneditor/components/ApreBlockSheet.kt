package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.domain.calculations.SmartLoadReferenceSource
import com.example.kpkn.domain.calculations.resolveSmartLoadReference
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

internal enum class ApreEffortPreset(
    val label: String,
    val intensityMode: IntensityMode?,
    val targetRir: Int?,
    val targetRpe: Double?,
) {
    FAILURE("Al fallo", IntensityMode.FAILURE, null, null),
    RIR_1("RIR 1", IntensityMode.RIR, 1, null),
    RIR_2("RIR 2", IntensityMode.RIR, 2, null),
    RPE_8("RPE 8", IntensityMode.RPE, null, 8.0),
    RPE_9("RPE 9", IntensityMode.RPE, null, 9.0),
}

private fun Exercise.resolveApreOneRmKg(): Double? {
    com.example.kpkn.domain.calculations.resolveReferenceCapacity(this)
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { return it }
    val smart = resolveSmartLoadReference(this)
    if (smart != null && smart.kilograms.isFinite() && smart.kilograms > 0.0) {
        return when (smart.source) {
            SmartLoadReferenceSource.EXPLICIT_TRAINING_MAX,
            SmartLoadReferenceSource.RECIPE_TRAINING_MAX,
            SmartLoadReferenceSource.DEFAULT_TRAINING_MAX -> smart.kilograms / 0.90
            SmartLoadReferenceSource.ONE_RM -> smart.kilograms
            else -> smart.kilograms
        }
    }
    return reference1RM?.takeIf { it.isFinite() && it > 0.0 }
}

/**
 * #14.1 · Si el ejercicio tiene un bloque APRE planificado (sin observación S4),
 * recalcula las cargas de S1–S4 automáticamente al editar 1RM/PR/TM en Carga Inteligente.
 */
internal fun Exercise.recomputePlannedApreLoadsIfLinked(): Exercise {
    if (apreBlock == null) return this
    if (com.example.kpkn.domain.training.observedApreXrmBlock(this) != null) return this
    val live = resolveLiveApreBlock(this) ?: return this
    val oneRm = resolveApreOneRmKg() ?: return this
    val generated = Apre.block(oneRm, live.variant)
    if (generated.size != 4) return this
    val updatedSets = sets.toMutableList()
    live.indices.forEachIndexed { pos, setIdx ->
        val current = updatedSets.getOrNull(setIdx) ?: return@forEachIndexed
        val nextLoad = generated[pos].loadKg
        updatedSets[setIdx] = current.copy(
            weight = nextLoad,
            leftTarget = current.leftTarget?.copy(weight = nextLoad),
            rightTarget = current.rightTarget?.copy(weight = nextLoad),
        )
    }
    return copy(sets = updatedSets)
}

/** La sustitución se confirma en la hoja y conserva la identidad y las referencias del ejercicio. */
internal fun Exercise.withApreBlock(
    variant: ApreVariant,
    effortPreset: ApreEffortPreset = ApreEffortPreset.FAILURE,
    newId: () -> String = { UUID.randomUUID().toString() },
): Exercise {
    val oneRm = resolveApreOneRmKg() ?: return this
    val block = Apre.block(oneRm, variant)
    if (block.isEmpty()) return this
    val prescribed = block.map { item ->
        val isAmrap = item.ordinal >= 3
        val side = if (isEffectivelyUnilateral()) {
            UnilateralTarget(
                weight = item.loadKg,
                targetReps = item.targetReps,
                targetRIR = if (isAmrap) effortPreset.targetRir else null,
                targetRPE = if (isAmrap) effortPreset.targetRpe else null,
                intensityMode = if (isAmrap) effortPreset.intensityMode else null,
            )
        } else null
        ExerciseSet(
            id = newId(),
            weight = item.loadKg,
            targetReps = item.targetReps,
            purpose = item.purpose,
            amrapMinimumReps = item.minimumReps,
            calibrationScope = null,
            loadModeV2 = LoadModeV2.LOAD,
            targetRIR = if (isAmrap) effortPreset.targetRir else null,
            targetRPE = if (isAmrap) effortPreset.targetRpe else null,
            intensityMode = if (isAmrap) effortPreset.intensityMode else null,
            leftTarget = side,
            rightTarget = side?.copy(),
        )
    }
    return copy(
        trainingMode = TrainingMode.REPS,
        sets = prescribed,
        apreBlock = ApreBlockReference(variant.xrmReps, prescribed.map { it.id }),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApreBlockSheet(
    exercise: Exercise,
    onApply: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
    onOpenSmartLoad: (() -> Unit)? = null,
) {
    var variant by remember(exercise.id, exercise.apreBlock) {
        mutableStateOf(
            resolveLiveApreBlock(exercise)?.variant
                ?: Apre.variantOfXrmReps(exercise.apreBlock?.xrmReps ?: -1)
                ?: ApreVariant.APRE_6,
        )
    }
    var effortPreset by remember(exercise.id, exercise.apreBlock) {
        val s3Idx = resolveLiveApreBlock(exercise)?.thirdIndex ?: 2
        val s3 = exercise.sets.getOrNull(s3Idx)
        mutableStateOf(
            when {
                s3?.targetRIR == 1 -> ApreEffortPreset.RIR_1
                s3?.targetRIR == 2 -> ApreEffortPreset.RIR_2
                s3?.targetRPE == 8.0 -> ApreEffortPreset.RPE_8
                s3?.targetRPE == 9.0 -> ApreEffortPreset.RPE_9
                else -> ApreEffortPreset.FAILURE
            },
        )
    }
    val markerNeedsRepair = exercise.apreBlock != null && resolveLiveApreBlock(exercise) == null
    val oneRm = exercise.resolveApreOneRmKg()
    val block = oneRm?.let { Apre.block(it, variant) }.orEmpty()
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bloque APRE", style = KpknType.dialogTitle, color = WizardColors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApreVariant.entries.forEach { option ->
                    KpknChip(text = option.label, selected = variant == option, onClick = { variant = option })
                }
            }
            if (block.isEmpty()) {
                Text(
                    "Falta el 1RM o PR. Indícalo en Carga inteligente antes de crear el bloque.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
                onOpenSmartLoad?.let { openSmartLoad ->
                    KpknTextAction(
                        text = "Configurar en Carga inteligente",
                        onClick = { onDismiss(); openSmartLoad() },
                    )
                }
            }
            if (markerNeedsRepair) {
                Text(
                    "El marcador guardado está incompleto. Aplicar una variante nueva reemplaza las series actuales y repara APRE.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
            if (block.isNotEmpty()) {
                KpknModule {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Serie", style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(0.6f))
                            Text("Rol", style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1.4f))
                            Text("Carga", style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                            Text("Meta", style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
                        }
                        block.forEach { item ->
                            val role = when (item.ordinal) {
                                1 -> "50 % XRM"
                                2 -> "75 % XRM"
                                3 -> "AMRAP · S4"
                                else -> "AMRAP · XRM"
                            }
                            val meta = item.minimumReps?.let { "$it+ · ${effortPreset.label}" } ?: "${item.targetReps} reps"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(KpknSurface.inset)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("S${item.ordinal}", style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(0.6f))
                                Text(role, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1.4f))
                                Text(
                                    "${formatEditableNumber(item.loadKg).replace('.', ',')}\u00a0kg",
                                    style = KpknType.bodyStrong,
                                    color = SymbolPalette.energia,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(meta, style = KpknType.caption.copy(fontWeight = FontWeight.Medium), color = WizardColors.text, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Text("Esfuerzo en S3 y S4 (AMRAP)", style = KpknType.bodyStrong, color = WizardColors.text)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ApreEffortPreset.entries.forEach { preset ->
                        KpknChip(
                            text = preset.label,
                            selected = effortPreset == preset,
                            onClick = { effortPreset = preset },
                        )
                    }
                }
            }
            KpknPrimaryPill(
                text = "Aplicar ${variant.label}",
                enabled = block.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onApply { it.withApreBlock(variant, effortPreset) }; onDismiss() },
            )
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
        }
    }
}
