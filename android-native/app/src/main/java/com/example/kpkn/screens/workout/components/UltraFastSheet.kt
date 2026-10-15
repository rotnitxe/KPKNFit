package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.domain.sessionassistant.UltraFastReason
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Duración con espacio inseparable antes de la unidad («1 min», «2 m 05 s»), como el resto de la piel de la sesión (LR1c2). */
private fun formatSeconds(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (s == 0) "$m min" else "$m m ${s.toString().padStart(2, '0')} s"
}

/**
 * «Modo Ultrarrápido» (B11, LR1c2): la piel de LR1a dentro del `KpknSheet`. Las barras van en `KpknProgressBar`, las cifras en Syne, las
 * tarjetas por ejercicio son módulos, el ahorro va en `ok` y los botones son píldoras. Los cálculos y las acciones no cambian.
 */
@Composable
fun UltraFastPreviewSheet(
    preview: UltraFastPreview?,
    savedSeconds: Int,
    visibleExercises: List<Exercise>,
    ultraFastManualOverrides: Map<String, Boolean>,
    onToggleOverride: (String) -> Unit,
    onConfirm: (Map<String, Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (preview == null) {
        KpknSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Modo Ultrarrápido", style = KpknType.title, color = WizardColors.text)
                Text("Calculando compresión de sesión…", style = KpknType.caption, color = WizardColors.textMuted)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = WizardColors.text,
                    trackColor = Color.White.copy(alpha = 0.08f),
                )
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KpknTextAction(text = "Cerrar", onClick = onDismiss)
                }
            }
        }
        return
    }
    val totalSaved = preview.savedSeconds.takeIf { it > 0 } ?: savedSeconds
    val beforeSec = preview.beforeSeconds.coerceAtLeast(60)
    val afterSec = preview.afterSeconds.coerceAtLeast(60)

    // State for local adjustments to dropsets / rest-pauses per exercise
    var customSetCounts by remember { mutableStateOf(mapOf<String, Int>()) }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Título tipográfico, sin icono de IA
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Modo Ultrarrápido",
                    style = KpknType.title,
                    color = WizardColors.text,
                )
                Text(
                    "Optimiza descansos y densifica series para finalizar en menos tiempo.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }

            // Comparativa visual de duraciones
            val maxSec = maxOf(beforeSec, afterSec).toFloat()
            KpknModule(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Fila 1: duración estándar
                    UltraDurationRow(
                        label = "Duración estándar",
                        value = formatSeconds(beforeSec),
                        progress = (beforeSec / maxSec).coerceIn(0.1f, 1f),
                        barColor = WizardColors.textFaint,
                        labelColor = WizardColors.textMuted,
                        valueColor = WizardColors.text,
                    )

                    // Fila 2: duración con Modo Ultrarrápido
                    UltraDurationRow(
                        label = "Con Modo Ultrarrápido",
                        value = formatSeconds(afterSec),
                        progress = (afterSec / maxSec).coerceIn(0.1f, 1f),
                        barColor = WizardColors.done,
                        labelColor = WizardColors.done,
                        valueColor = WizardColors.done,
                    )

                    // Píldora de ahorro
                    if (totalSaved > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(KpknShape.pill)
                                .background(WizardColors.done.copy(alpha = 0.15f).compositeOver(KpknSurface.module), KpknShape.pill)
                                .border(1.dp, WizardColors.done.copy(alpha = 0.30f), KpknShape.pill)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                KpknLineIcons.Clock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = WizardColors.done,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Ahorras ~${formatSeconds(totalSaved)} (${((totalSaved.toFloat() / beforeSec) * 100).toInt()} % más rápida)",
                                style = KpknType.chip,
                                color = WizardColors.done,
                            )
                        }
                    }
                }
            }

            // Ajustes por ejercicio
            Text(
                "Ajustes por ejercicio".uppercase(),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                preview.perExercise.forEach { ch ->
                    val isOverridden = ultraFastManualOverrides[ch.exerciseId]
                    val count = customSetCounts[ch.exerciseId] ?: ch.afterSets
                    val reasonLabel = when (ch.reason) {
                        UltraFastReason.PROTECTED_BASIC -> "Básico protegido · ${ch.beforeSets} → $count series"
                        UltraFastReason.DANGEROUS_COMPLEX -> "Complejo · ${ch.beforeSets} → $count series"
                        UltraFastReason.ISOLATION_DENSIFIED -> "Aislado · ${ch.beforeSets} series → $count× ${ch.afterTechnique}"
                        UltraFastReason.MANUAL_OVERRIDE_ALLOWED -> "Manual: forzado a ${ch.afterTechnique}"
                        else -> "${ch.beforeSets} → $count series · ${ch.afterTechnique}"
                    }
                    // Reducido en azul (info), densificado en verde (ok) y sin cambio en gris.
                    val badgeTone = when {
                        ch.wasReduced -> WizardColors.info
                        ch.wasDensified -> WizardColors.done
                        else -> WizardColors.textFaint
                    }

                    KpknModule(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ch.exerciseName,
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(KpknShape.pill)
                                        .background(badgeTone.copy(alpha = 0.16f).compositeOver(KpknSurface.module), KpknShape.pill)
                                        .border(1.dp, badgeTone.copy(alpha = 0.35f), KpknShape.pill)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        if (ch.wasDensified) ch.afterTechnique else if (ch.wasReduced) "${ch.beforeSets}→$count" else "Sin cambio",
                                        style = KpknType.chip,
                                        color = badgeTone,
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    reasonLabel,
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                    modifier = Modifier.weight(1f),
                                )

                                // Mini stepper para configurar el número de series o de drops
                                if (ch.wasDensified || ch.wasReduced) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        KpknIconButton(
                                            icon = KpknLineIcons.Minus,
                                            contentDescription = "Menos",
                                            onClick = {
                                                if (count > 1) customSetCounts = customSetCounts + (ch.exerciseId to count - 1)
                                            },
                                        )
                                        Text(
                                            "$count",
                                            style = KpknType.metricM,
                                            color = WizardColors.text,
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        )
                                        KpknIconButton(
                                            icon = KpknLineIcons.Plus,
                                            contentDescription = "Más",
                                            onClick = {
                                                if (count < 6) customSetCounts = customSetCounts + (ch.exerciseId to count + 1)
                                            },
                                        )
                                    }
                                }
                            }

                            // Interruptor manual para los ejercicios protegidos o peligrosos
                            if (ch.reason == UltraFastReason.PROTECTED_BASIC || ch.reason == UltraFastReason.DANGEROUS_COMPLEX) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    KpknChip(
                                        text = if (isOverridden == true) "Permitir técnica intensa" else "Mantener protegido",
                                        selected = isOverridden == true,
                                        onClick = { onToggleOverride(ch.exerciseId) },
                                    )
                                    if (isOverridden == true) {
                                        Text(
                                            "Mayor riesgo",
                                            style = KpknType.chip,
                                            color = WizardColors.danger,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Resumen de supersets
            if (preview.supersets.isNotEmpty()) {
                Text(
                    "Supersets (misma máquina, antagónicos)".uppercase(),
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                preview.supersets.forEach { ss ->
                    KpknModule(
                        modifier = Modifier.fillMaxWidth(),
                        accent = WizardColors.done,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                KpknLineIcons.Swap,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = WizardColors.done,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${ss.nameA} + ${ss.nameB}",
                                    style = KpknType.bodyStrong,
                                    color = WizardColors.text,
                                )
                                Text(
                                    "Máquina: ${ss.machineKey} · antagónicos",
                                    style = KpknType.caption,
                                    color = WizardColors.textMuted,
                                )
                            }
                        }
                    }
                }
            }

            // Acciones
            // Con la letra al 120 % o más, «Cancelar» y «Aplicar» no caben en una fila: se apilan a ancho completo (LR1c2).
            if (LocalDensity.current.fontScale >= 1.2f) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KpknPrimaryPill(
                        text = "Aplicar",
                        onClick = { onConfirm(customSetCounts) },
                        icon = KpknLineIcons.Check,
                        enabled = totalSaved >= 0,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KpknSecondaryPill(
                        text = "Cancelar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f),
                    )
                    KpknPrimaryPill(
                        text = "Aplicar",
                        onClick = { onConfirm(customSetCounts) },
                        icon = KpknLineIcons.Check,
                        enabled = totalSaved >= 0,
                        modifier = Modifier.weight(1.2f),
                    )
                }
            }
            Text(
                "Reversible: podrás deshacer desde el banner o el menú de tiempo.",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Una fila de la comparativa: el rótulo y la cifra (en Syne, tabular) sobre una barra `KpknProgressBar`. */
@Composable
private fun UltraDurationRow(
    label: String,
    value: String,
    progress: Float,
    barColor: Color,
    labelColor: Color,
    valueColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = KpknType.caption, color = labelColor)
            Text(value, style = KpknType.metricM, color = valueColor)
        }
        KpknProgressBar(
            progress = progress,
            color = barColor,
            trackColor = Color.White.copy(alpha = 0.08f),
            height = 10.dp,
        )
    }
}
