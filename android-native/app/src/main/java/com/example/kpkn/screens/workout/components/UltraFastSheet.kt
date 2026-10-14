package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.domain.sessionassistant.UltraFastReason
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

private fun formatSeconds(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (s == 0) "${m} min" else "${m}m ${s.toString().padStart(2, '0')}s"
}

/**
 * Hoja «Modo ultrarrápido» (LS7b): la duración estándar contra la comprimida, el ahorro en `metricM`, los ajustes por ejercicio
 * con su ± de series y los supersets. Antes de llegar la propuesta, muestra que se está calculando.
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
    // Ajustes locales de series por ejercicio (solo se aplican al confirmar).
    var customSetCounts by remember { mutableStateOf(mapOf<String, Int>()) }
    if (preview == null) {
        KpknSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Modo ultrarrápido", style = KpknType.title, color = WizardColors.text)
                Text(
                    text = "Calculando la compresión de la sesión…",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                KpknSecondaryPill(text = "Cerrar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }
    KpknSheet(onDismissRequest = onDismiss) {
        UltraFastPreviewBody(
            preview = preview,
            savedSeconds = savedSeconds,
            manualOverrides = ultraFastManualOverrides,
            setCounts = customSetCounts,
            onSetCountChange = { exerciseId, count ->
                customSetCounts = customSetCounts + (exerciseId to count)
            },
            onToggleOverride = onToggleOverride,
            onConfirm = { onConfirm(customSetCounts) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Cuerpo sin estado de «Modo ultrarrápido» (LS7b): duraciones, ahorro, ajustes por ejercicio, supersets y «Aplicar». Lo pinta la
 * hoja y lo montan las pruebas con datos y lambdas. [setCounts] son las series elegidas a mano por ejercicio.
 */
@Composable
internal fun UltraFastPreviewBody(
    preview: UltraFastPreview,
    savedSeconds: Int,
    manualOverrides: Map<String, Boolean>,
    setCounts: Map<String, Int>,
    onSetCountChange: (String, Int) -> Unit,
    onToggleOverride: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSaved = preview.savedSeconds.takeIf { it > 0 } ?: savedSeconds
    val beforeSec = preview.beforeSeconds.coerceAtLeast(60)
    val afterSec = preview.afterSeconds.coerceAtLeast(60)
    val maxSec = maxOf(beforeSec, afterSec).toFloat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Modo ultrarrápido",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Text(
                text = "Optimiza descansos y densifica series para finalizar en menos tiempo.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }

        KpknModule(modifier = Modifier.fillMaxWidth()) {
            DurationRow(
                label = "Duración estándar",
                value = formatSeconds(beforeSec),
                progress = beforeSec / maxSec,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            DurationRow(
                label = "Con modo ultrarrápido",
                value = formatSeconds(afterSec),
                progress = afterSec / maxSec,
                color = SymbolPalette.ok,
            )
            if (totalSaved > 0) {
                Spacer(Modifier.height(16.dp))
                KpknModuleHeader(label = "Ahorro estimado")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KpknMetric(
                        value = "≈ ${formatSeconds(totalSaved)}",
                        style = KpknType.metricM,
                        color = SymbolPalette.ok,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(${((totalSaved.toFloat() / beforeSec) * 100).toInt()}% más rápida)",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }

        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Ajustes por ejercicio")
            Spacer(Modifier.height(8.dp))
            preview.perExercise.forEachIndexed { index, change ->
                if (index > 0) ModuleDivider()
                val isOverridden = manualOverrides[change.exerciseId] == true
                val count = setCounts[change.exerciseId] ?: change.afterSets
                val reasonLabel = when (change.reason) {
                    UltraFastReason.PROTECTED_BASIC -> "Básico protegido · ${change.beforeSets} → $count series"
                    UltraFastReason.DANGEROUS_COMPLEX -> "Complejo · ${change.beforeSets} → $count series"
                    UltraFastReason.ISOLATION_DENSIFIED -> "Aislado · ${change.beforeSets} series → $count× ${change.afterTechnique}"
                    UltraFastReason.MANUAL_OVERRIDE_ALLOWED -> "Manual: forzado a ${change.afterTechnique}"
                    else -> "${change.beforeSets} → $count series · ${change.afterTechnique}"
                }
                val badgeTone = when {
                    change.wasReduced -> SymbolPalette.columna
                    change.wasDensified -> SymbolPalette.ok
                    else -> WizardColors.textFaint
                }
                val badgeText = when {
                    change.wasDensified -> change.afterTechnique
                    change.wasReduced -> "${change.beforeSets}→$count"
                    else -> "Sin cambio"
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = change.exerciseName,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = badgeText,
                            style = KpknType.caption,
                            color = badgeTone,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = reasonLabel,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    if (change.wasDensified || change.wasReduced) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Series",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            KpknIconButton(
                                icon = KpknLineIcons.Minus,
                                contentDescription = "Menos series de ${change.exerciseName}",
                                onClick = {
                                    if (count > 1) onSetCountChange(change.exerciseId, count - 1)
                                },
                            )
                            Text(
                                text = "$count",
                                style = KpknType.bodyStrong.copy(fontFeatureSettings = "tnum"),
                                color = WizardColors.text,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                            KpknIconButton(
                                icon = KpknLineIcons.Plus,
                                contentDescription = "Más series de ${change.exerciseName}",
                                onClick = {
                                    if (count < MaxUltraFastSets) onSetCountChange(change.exerciseId, count + 1)
                                },
                            )
                        }
                    }
                    if (change.reason == UltraFastReason.PROTECTED_BASIC || change.reason == UltraFastReason.DANGEROUS_COMPLEX) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KpknChip(
                                text = if (isOverridden) "Permitir técnica intensa" else "Mantener protegido",
                                selected = isOverridden,
                                onClick = { onToggleOverride(change.exerciseId) },
                            )
                            if (isOverridden) {
                                Text(
                                    text = "Mayor riesgo",
                                    style = KpknType.caption,
                                    color = WizardColors.danger,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (preview.supersets.isNotEmpty()) {
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(label = "Supersets")
                Spacer(Modifier.height(8.dp))
                preview.supersets.forEachIndexed { index, superset ->
                    if (index > 0) ModuleDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = KpknLineIcons.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = SymbolPalette.ok,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${superset.nameA} + ${superset.nameB}",
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                            Text(
                                text = "Máquina: ${superset.machineKey} · antagónicos",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknSecondaryPill(
                text = "Cancelar",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            KpknPrimaryPill(
                text = "Aplicar",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = totalSaved >= 0,
            )
        }
        Text(
            text = "Reversible: podrás deshacer desde el banner o el menú de tiempo.",
            style = KpknType.caption,
            color = WizardColors.textFaint,
        )
    }
}

/** Tope de series por ejercicio que el ajuste manual de la hoja permite. */
private const val MaxUltraFastSets = 6

/** Una duración: etiqueta y cifra en una fila, y debajo su barra proporcional al máximo. */
@Composable
private fun DurationRow(label: String, value: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
            Text(
                text = value,
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "tnum"),
                color = color,
            )
        }
        KpknProgressBar(
            progress = progress.coerceIn(0.1f, 1f),
            color = color,
            modifier = Modifier.fillMaxWidth(),
            height = 8.dp,
        )
    }
}

@Composable
private fun ModuleDivider() {
    Spacer(Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
    Spacer(Modifier.height(10.dp))
}
