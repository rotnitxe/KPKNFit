package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/**
 * «Ajuste por estado actual» (LR1c2): la piel de LR1a dentro del `KpknSheet`. Las tarjetas son módulos, el semáforo pasa a `ok` /
 * acento / `danger`, el deslizador va en tinta y los botones son píldoras. Los cálculos y el orden de los elementos no cambian.
 */
@Composable
internal fun SetAdjustmentOverlay(
    exercise: Exercise,
    currentSet: ExerciseSet,
    setIndex: Int,
    exerciseReadiness: ExerciseReadiness,
    weightSuggestion: WeightSuggestion?,
    averageErm: Double?,
    bodyWeight: Double? = null,
    loadMode: LoadModeV2,
    onDismiss: () -> Unit,
    onApply: (SetAdjustmentSuggestion) -> Unit,
) {
    var severitySlider by remember { mutableFloatStateOf(0.5f) }
    val plannedWeight = when (loadMode) {
        LoadModeV2.BODYWEIGHT -> 0.0
        else -> currentSet.weight
            ?: weightSuggestion?.suggestedWeight
            ?: (averageErm?.let { erm ->
                val plannedReps = currentSet.plannedRepAnchor()
                if (plannedReps != null && plannedReps > 0) {
                    StrengthModel.weightFor(erm, plannedReps, rir = currentSet.targetRIR, rpe = currentSet.targetRPE,
                        toFailure = currentSet.isFailure || currentSet.isAmrap || currentSet.intensityMode == IntensityMode.FAILURE ||
                            currentSet.intensityMode == IntensityMode.AMRAP || currentSet.intensityMode == IntensityMode.SOLO_RM)
                } else null
            })
            ?: 0.0
    }

    val adjustment = remember(severitySlider, plannedWeight, loadMode, bodyWeight) {
        ExerciseReadinessEngine.calculateSetAdjustment(
            plannedWeight = plannedWeight,
            exerciseReadiness = exerciseReadiness,
            severitySlider = severitySlider.toDouble(),
            averageErm = averageErm,
            loadMode = loadMode,
            bodyWeight = bodyWeight,
        )
    }

    // Semáforo: lo listo en `ok`, lo intermedio en el acento de la sesión y lo bajo en `danger`.
    val readinessColor = when {
        exerciseReadiness.overallScore >= 75 -> WizardColors.done
        exerciseReadiness.overallScore >= 50 -> SymbolPalette.musculo
        else -> WizardColors.danger
    }
    val readinessLabel = ExerciseReadinessEngine.readinessLabel(exerciseReadiness.overallScore)

    val plannedModeText = when (loadMode) {
        LoadModeV2.LOAD -> "Carga Normal"
        LoadModeV2.LASTRE -> "Con Lastre"
        LoadModeV2.BODYWEIGHT -> "Peso Corporal"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val plannedWeightText = when (loadMode) {
        LoadModeV2.BODYWEIGHT -> "BW"
        else -> "${plannedWeight.toTrimmedNumberString()} kg"
    }

    val suggestedModeText = when (adjustment.suggestedLoadMode) {
        LoadModeV2.LOAD -> "Carga Normal"
        LoadModeV2.LASTRE -> "Con Lastre"
        LoadModeV2.BODYWEIGHT -> "Peso Corporal"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val suggestedWeightText = when (adjustment.suggestedLoadMode) {
        LoadModeV2.BODYWEIGHT -> "BW"
        else -> "${adjustment.suggestedWeight.toTrimmedNumberString()} kg"
    }
    val applyText = when (adjustment.suggestedLoadMode) {
        LoadModeV2.BODYWEIGHT -> "Aplicar Peso Corporal"
        LoadModeV2.ASSISTED -> "Aplicar +${adjustment.suggestedWeight.toTrimmedNumberString()} kg Asist."
        else -> "Aplicar ${adjustment.suggestedWeight.toTrimmedNumberString()} kg"
    }
    // Atrás, tocar fuera o arrastrar hacia abajo cierran el ajuste igual que «Cancelar»: no aplica nada y la serie queda como estaba
    // (LV4, 15). Antes la hoja se tragaba Atrás en silencio y no había más salida que sus botones.
    KpknSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ajuste por estado actual",
                        style = KpknType.dialogTitle,
                        color = WizardColors.text,
                    )
                    Text(
                        "${exercise.displayNameWithSelectedChips()} · Serie ${setIndex + 1}/${exercise.sets.size}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                KpknIconButton(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Cerrar",
                    onClick = onDismiss,
                )
            }

            // ── Info Card ──
            KpknModule(contentPadding = PaddingValues(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 48 dp: «62 %» en Inter 13 sp, con la letra al 130 %, no se corta.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(KpknSurface.inset, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${exerciseReadiness.overallScore} %",
                            style = KpknType.chip,
                            color = readinessColor,
                            maxLines = 1,
                        )
                    }
                    Column {
                        Text(
                            "Preparación: $readinessLabel",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                        Text(
                            buildString {
                                append("Músc. ${exerciseReadiness.muscularComponent} %")
                                append(" · SNC ${exerciseReadiness.cnsComponent} %")
                                if (exerciseReadiness.spinalWeight > 0.01) {
                                    append(" · Col. ${exerciseReadiness.spinalComponent} %")
                                }
                            },
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }

            // ── Comparación de cargas ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknModule(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            plannedModeText.uppercase(),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            plannedWeightText,
                            style = KpknType.metricM,
                            color = WizardColors.text,
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        "→",
                        style = KpknType.bodyStrong,
                        color = readinessColor,
                    )
                }

                KpknModule(
                    modifier = Modifier.weight(1f),
                    accent = readinessColor,
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            suggestedModeText.uppercase(),
                            style = KpknType.eyebrow,
                            color = readinessColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            suggestedWeightText,
                            style = KpknType.metricM,
                            color = readinessColor,
                        )
                        Text(
                            if (loadMode == LoadModeV2.ASSISTED) {
                                "+${(adjustment.suggestedWeight - plannedWeight).roundToInt()} kg asist."
                            } else {
                                "−${(adjustment.reductionPercent * 100).roundToInt()} %"
                            },
                            style = KpknType.caption,
                            color = readinessColor,
                        )
                    }
                }
            }

            // ── Referencia eRM ──
            if (averageErm != null && averageErm > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        KpknLineIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = WizardColors.textFaint,
                    )
                    Text(
                        "eRM promedio: ~${averageErm.toTrimmedNumberString()} kg",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                }
            }

            // ── Slider de severidad ──
            Column {
                Text(
                    "Intensidad del ajuste".uppercase(),
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = severitySlider,
                    onValueChange = { severitySlider = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = WizardColors.text,
                        activeTrackColor = WizardColors.text,
                        inactiveTrackColor = Color.White.copy(alpha = 0.08f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Conservador",
                        style = KpknType.caption,
                        color = if (severitySlider <= 0.33f) readinessColor else WizardColors.textFaint,
                    )
                    Text(
                        "Equilibrado",
                        style = KpknType.caption,
                        color = if (severitySlider in 0.34f..0.66f) readinessColor else WizardColors.textFaint,
                    )
                    Text(
                        "Agresivo",
                        style = KpknType.caption,
                        color = if (severitySlider >= 0.67f) readinessColor else WizardColors.textFaint,
                    )
                }
            }

            // ── Disclaimer ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KpknSurface.inset, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    KpknLineIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = WizardColors.info,
                )
                Text(
                    "El ajuste aplica solo para esta serie y sesión. Tu plan no se modifica permanentemente.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }

            // ── Botones de acción ──
            // Con la letra al 120 % o más, «Aplicar 97,5 kg» no cabe junto a «Cancelar»: las dos píldoras se apilan a ancho completo.
            if (LocalDensity.current.fontScale >= 1.2f) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpknPrimaryPill(
                        text = applyText,
                        onClick = { onApply(adjustment) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KpknSecondaryPill(
                        text = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpknSecondaryPill(
                        text = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f),
                    )
                    KpknPrimaryPill(
                        text = applyText,
                        onClick = { onApply(adjustment) },
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
        }
    }
}
