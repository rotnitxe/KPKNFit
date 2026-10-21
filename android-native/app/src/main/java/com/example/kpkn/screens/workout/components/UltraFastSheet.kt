package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.domain.sessionassistant.UltraFastExerciseChange
import com.example.kpkn.domain.sessionassistant.UltraFastReason
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType
import kotlin.math.sin

private fun formatSeconds(seconds: Int): String {
    val sec = seconds.coerceAtLeast(0)
    return if (sec % 60 == 0) "${sec / 60} min" else "${sec / 60} m ${sec % 60} s"
}

/** Compresses the existing plan; set counts are passed to the original apply callback. */
@Composable
fun UltraFastPreviewSheet(
    preview: UltraFastPreview?, savedSeconds: Int,
    @Suppress("UNUSED_PARAMETER") visibleExercises: List<Exercise>,
    ultraFastManualOverrides: Map<String, Boolean>, onToggleOverride: (String) -> Unit,
    onConfirm: (Map<String, Int>) -> Unit, onDismiss: () -> Unit,
) {
    var customSetCounts by remember { mutableStateOf(emptyMap<String, Int>()) }
    // A recalculated proposal may remove/reprotect an exercise. Never submit an obsolete manual count.
    val editable = preview?.perExercise?.filter { it.wasReduced && !it.wasDensified }?.map { it.exerciseId }?.toSet().orEmpty()
    val applicableCounts = customSetCounts.filterKeys { it in editable }
    HeaderSheet(onDismiss) { close ->
        HeaderSheetFrame("Ultrarrápido", footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderSheetAction("Cancelar", close, Modifier.weight(1f))
                HeaderSheetAction("Aplicar", { onConfirm(applicableCounts) }, Modifier.weight(1f), primary = true,
                    enabled = preview != null && (preview.savedSeconds.takeIf { it > 0 } ?: savedSeconds) >= 0)
            }
            if (preview != null) Text("Reversible: deshaz desde el aviso o el menú de tiempo.", style = KpknType.caption, color = WizardColors.textMuted)
        }) {
            if (preview == null) UltraFastCalculating()
            else UltraFastPreviewContent(preview, savedSeconds, ultraFastManualOverrides, applicableCounts,
                { id, count -> customSetCounts = customSetCounts + (id to count) }, onToggleOverride)
        }
    }
}

@Composable
internal fun UltraFastPreviewContent(
    preview: UltraFastPreview, savedSeconds: Int, overrides: Map<String, Boolean>, counts: Map<String, Int>,
    onCount: (String, Int) -> Unit, onToggleOverride: (String) -> Unit,
) {
    val before = preview.beforeSeconds.coerceAtLeast(60)
    val after = preview.afterSeconds.coerceAtLeast(60)
    val saved = preview.savedSeconds.takeIf { it > 0 } ?: savedSeconds
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Duración estándar → con Ultrarrápido", style = KpknType.caption, color = WizardColors.textMuted)
        Text("${formatSeconds(before)} → ${formatSeconds(after)}", style = KpknType.metricM, color = WizardColors.text)
        if (saved > 0) Text("Ahorras ~${formatSeconds(saved)} (${saved.toLong() * 100 / before} % más rápida)", style = KpknType.bodyStrong, color = WizardColors.done)
        if (counts.isNotEmpty()) Text("Estimación previa a tus ajustes de series.", style = KpknType.caption, color = WizardColors.textMuted)
        Text("AJUSTES POR EJERCICIO", style = KpknType.eyebrow, color = WizardColors.textMuted)
        val columns = if (LocalDensity.current.fontScale >= 1.2f) 1 else 2
        preview.perExercise.chunked(columns).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { change ->
                    UltraFastExerciseTile(change, counts[change.exerciseId] ?: change.afterSets, overrides[change.exerciseId],
                        { onCount(change.exerciseId, it) }, { onToggleOverride(change.exerciseId) }, Modifier.weight(1f))
                }
                if (columns == 2 && group.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (preview.supersets.isNotEmpty()) {
            Text("SUPERSERIES PROPUESTAS", style = KpknType.eyebrow, color = WizardColors.textMuted)
            preview.supersets.forEach { group ->
                KpknModule(Modifier.fillMaxWidth(), accent = WizardColors.done, contentPadding = PaddingValues(12.dp)) {
                    Text("${group.nameA} + ${group.nameB}", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text("Misma máquina · ${group.machineKey} · antagónicos", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun UltraFastExerciseTile(change: UltraFastExerciseChange, count: Int, override: Boolean?, onCount: (Int) -> Unit, onOverride: () -> Unit, modifier: Modifier) {
    val editable = change.wasReduced && !change.wasDensified
    val allowsOverride = override != null || change.reason in setOf(UltraFastReason.PROTECTED_BASIC, UltraFastReason.DANGEROUS_COMPLEX,
        UltraFastReason.MANUAL_OVERRIDE_ALLOWED, UltraFastReason.MANUAL_OVERRIDE_BLOCKED)
    KpknModule(modifier, accent = if (change.wasDensified) WizardColors.done else if (change.wasReduced) WizardColors.info else null,
        contentPadding = PaddingValues(10.dp)) {
        Text(change.exerciseName, style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(when {
            change.wasDensified -> when (change.afterTechnique.uppercase()) { "DROPSET", "DROP_SET" -> "Drop-set"; "REST_PAUSE" -> "Rest-pause"; else -> change.afterTechnique }
            change.wasReduced -> "Series reducidas"
            change.reason == UltraFastReason.DANGEROUS_COMPLEX -> "Complejo protegido"
            change.reason == UltraFastReason.PROTECTED_BASIC -> "Básico protegido"
            else -> "Sin cambio"
        }, style = KpknType.caption, color = WizardColors.textMuted)
        Text(if (change.wasDensified) "Mismas ${change.beforeSets} series con técnica" else "${change.beforeSets} → $count series", style = KpknType.caption, color = WizardColors.textMuted)
        if (editable) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HeaderSheetAction("−", { onCount((count - 1).coerceIn(1, 6)) }, Modifier.weight(1f).testTag("ultra-minus-${change.exerciseId}"), enabled = editable && count > 1)
            Text("$count", style = KpknType.metricM, color = WizardColors.text)
            HeaderSheetAction("+", { onCount((count + 1).coerceIn(1, 6)) }, Modifier.weight(1f).testTag("ultra-plus-${change.exerciseId}"), enabled = editable && count < 6)
        }
        }
        if (allowsOverride) {
            HeaderSheetAction(if (override == true) "Permitir técnica intensa" else "Mantener protegido", onOverride,
                Modifier.fillMaxWidth().semantics { selected = override == true }, primary = override == true, multiline = LocalDensity.current.fontScale < 1.2f)
            if (override == true) Text("Mayor riesgo", style = KpknType.caption, color = WizardColors.danger)
        }
    }
}

@Composable
private fun UltraFastCalculating() {
    val reduced = wizardReducedMotion()
    val visibility = rememberFeedbackAnimationVisibility()
    val clock = rememberRestClock(visibility.visible && !reduced)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val ink = WizardColors.text
        Canvas(Modifier.size(42.dp).then(visibility.modifier)) {
            repeat(4) { index ->
                val height = size.height * (.25f + .45f * if (reduced) .5f else (sin(clock.seconds * 2 + index) + 1f) / 2f)
                val x = size.width * (index + 1) / 5
                drawLine(ink, Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        Text("Calculando…", style = KpknType.bodyStrong, color = WizardColors.text)
    }
}
