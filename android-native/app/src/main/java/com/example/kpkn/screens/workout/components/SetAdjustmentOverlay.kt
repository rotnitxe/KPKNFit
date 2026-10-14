package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.*
import java.util.Locale
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPercentRule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/**
 * Ajuste de la serie por el estado actual (LS7b): preparación, comparación de cargas y la intensidad del ajuste con la regla KPKN.
 * La hoja calcula el ajuste con la intensidad elegida; «Aplicar» lo entrega tal cual a [onApply].
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
                    erm * (1.0278 - 0.0278 * plannedReps)
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

    // Tono de la preparación: verde con buena forma, ámbar a medias y peligro por debajo.
    val readinessTone = when {
        exerciseReadiness.overallScore >= 75 -> WizardColors.done
        exerciseReadiness.overallScore >= 50 -> SymbolPalette.energia
        else -> WizardColors.danger
    }
    val readinessLabel = ExerciseReadinessEngine.readinessLabel(exerciseReadiness.overallScore)

    val plannedModeText = modeText(loadMode)
    val suggestedModeText = modeText(adjustment.suggestedLoadMode)
    val plannedValue = if (loadMode == LoadModeV2.BODYWEIGHT) "BW" else plannedWeight.toTrimmedNumberString()
    val plannedUnit = if (loadMode == LoadModeV2.BODYWEIGHT) null else "kg"
    val suggestedValue = if (adjustment.suggestedLoadMode == LoadModeV2.BODYWEIGHT) {
        "BW"
    } else {
        adjustment.suggestedWeight.toTrimmedNumberString()
    }
    val suggestedUnit = if (adjustment.suggestedLoadMode == LoadModeV2.BODYWEIGHT) null else "kg"
    val suggestedDelta = if (loadMode == LoadModeV2.ASSISTED) {
        "+${(adjustment.suggestedWeight - plannedWeight).roundToInt()} kg asist."
    } else {
        "−${(adjustment.reductionPercent * 100).roundToInt()} %"
    }
    val applyLabel = when (adjustment.suggestedLoadMode) {
        LoadModeV2.BODYWEIGHT -> "Aplicar peso corporal"
        LoadModeV2.ASSISTED -> "Aplicar +${adjustment.suggestedWeight.toTrimmedNumberString()} kg asist."
        else -> "Aplicar ${adjustment.suggestedWeight.toTrimmedNumberString()} kg"
    }

    KpknSheet(
        onDismissRequest = {},
        dismissible = false,
    ) {
        SetAdjustmentBody(
            subtitle = "${exercise.displayNameWithSelectedChips()} · Serie ${setIndex + 1}/${exercise.sets.size}",
            readinessScore = exerciseReadiness.overallScore,
            readinessLabel = readinessLabel,
            readinessDetail = buildString {
                append("Músculos ${exerciseReadiness.muscularComponent} %")
                append(" · Energía ${exerciseReadiness.cnsComponent} %")
                if (exerciseReadiness.spinalWeight > 0.01) {
                    append(" · Columna ${exerciseReadiness.spinalComponent} %")
                }
            },
            readinessTone = readinessTone,
            // Con carga normal no hace falta decir el modo: solo se nombra cuando cambia (asistido, lastre, peso corporal).
            plannedCaption = if (loadMode == LoadModeV2.LOAD) null else plannedModeText,
            plannedValue = plannedValue,
            plannedUnit = plannedUnit,
            suggestedCaption = if (adjustment.suggestedLoadMode == LoadModeV2.LOAD) null else suggestedModeText,
            suggestedValue = suggestedValue,
            suggestedUnit = suggestedUnit,
            suggestedDelta = suggestedDelta,
            erm = averageErm?.takeIf { it > 0 }?.let { "1RM estimado ≈ ${it.toTrimmedNumberString()} kg" },
            severity = severitySlider,
            onSeverityChange = { severitySlider = it },
            applyLabel = applyLabel,
            onApply = { onApply(adjustment) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Cuerpo sin estado del ajuste de serie (LS7b): la preparación, la comparación de cargas, la intensidad con su regla KPKN y los
 * botones. [onSeverityChange] recibe la intensidad entre 0 y 1; [onApply] se llama al pulsar «Aplicar».
 */
@Composable
internal fun SetAdjustmentBody(
    subtitle: String,
    readinessScore: Int,
    readinessLabel: String,
    readinessDetail: String,
    readinessTone: Color,
    plannedCaption: String?,
    plannedValue: String,
    plannedUnit: String?,
    suggestedCaption: String?,
    suggestedValue: String,
    suggestedUnit: String?,
    suggestedDelta: String,
    erm: String?,
    severity: Float,
    onSeverityChange: (Float) -> Unit,
    applyLabel: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Ajuste por estado actual",
                    style = KpknType.title,
                    color = WizardColors.text,
                )
                Text(
                    text = subtitle,
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

        KpknModule(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KpknMetric(
                    value = "$readinessScore",
                    unit = "%",
                    style = KpknType.metricM,
                    color = readinessTone,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Preparación: $readinessLabel",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = readinessDetail,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }

        KpknModule(modifier = Modifier.fillMaxWidth()) {
            // Se lee por filas (cabeceras, cifras, aclaraciones y diferencia) para que las dos columnas compartan línea base.
            // «Planificada» va alineada a la derecha y «Hoy» a la izquierda, pegadas a la flecha: así la flecha queda centrada entre
            // las cifras. «Planificada» es más ancha: en 130 % «PLANIFICADA» no cabe en media fila y se partía dentro de la palabra.
            val arrowWidth = 40.dp
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // La misma cabecera que KpknModuleHeader (eyebrow, 40 dp de altura, centrada en vertical), pero pegada a la derecha
                    // para que quede encima de su cifra. KpknModuleHeader siempre ocupa el ancho entero y alinea a la izquierda.
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 40.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = "Planificada".uppercase(Locale("es")),
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(arrowWidth))
                    KpknModuleHeader(label = "Hoy", modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(modifier = Modifier.weight(1.4f), horizontalArrangement = Arrangement.End) {
                        KpknMetric(value = plannedValue, unit = plannedUnit, style = KpknType.metricL)
                    }
                    Box(
                        modifier = Modifier.width(arrowWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "→", style = KpknType.title, color = readinessTone)
                    }
                    KpknMetric(
                        value = suggestedValue,
                        unit = suggestedUnit,
                        style = KpknType.metricL,
                        color = readinessTone,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Solo si el modo no es el de carga normal; si una columna no tiene aclaración, la otra conserva su sitio.
                if (plannedCaption != null || suggestedCaption != null) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.weight(1.4f), horizontalArrangement = Arrangement.End) {
                            Text(text = plannedCaption.orEmpty(), style = KpknType.caption, color = WizardColors.textMuted)
                        }
                        Spacer(Modifier.width(arrowWidth))
                        Text(
                            text = suggestedCaption.orEmpty(),
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1.4f))
                    Spacer(Modifier.width(arrowWidth))
                    Text(
                        text = suggestedDelta,
                        style = KpknType.caption,
                        color = readinessTone,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (erm != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = KpknLineIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = WizardColors.textFaint,
                )
                Text(
                    text = erm,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }

        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Intensidad del ajuste")
            Spacer(Modifier.height(12.dp))
            KpknPercentRule(
                value = (severity * 100f).roundToInt(),
                onValueChange = { onSeverityChange(it / 100f) },
                color = readinessTone,
                contentDescription = "Intensidad del ajuste",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SeverityLabel(text = "Conservador", active = severity <= 0.33f, tone = readinessTone)
                SeverityLabel(text = "Equilibrado", active = severity in 0.34f..0.66f, tone = readinessTone)
                SeverityLabel(text = "Agresivo", active = severity >= 0.67f, tone = readinessTone)
            }
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = WizardColors.textMuted,
            )
            Text(
                text = "Solo cambia esta serie y esta sesión. Tu plan sigue igual.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }

        // «Aplicar» ocupa todo el ancho para que su etiqueta quepa al 130 %; «Cancelar» baja como acción de texto.
        KpknPrimaryPill(
            text = applyLabel,
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
        }
    }
}

/** Nombre del modo de carga en tipo frase, tal como lo muestra la hoja. */
private fun modeText(mode: LoadModeV2): String = when (mode) {
    LoadModeV2.LOAD -> "Carga normal"
    LoadModeV2.LASTRE -> "Con lastre"
    LoadModeV2.BODYWEIGHT -> "Peso corporal"
    LoadModeV2.ASSISTED -> "Asistido"
}

@Composable
private fun SeverityLabel(text: String, active: Boolean, tone: Color) {
    Text(
        text = text,
        style = KpknType.caption,
        color = if (active) tone else WizardColors.textFaint,
    )
}
