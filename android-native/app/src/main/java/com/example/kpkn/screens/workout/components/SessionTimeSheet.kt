package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.PacingAlertMode
import com.example.kpkn.screens.workout.SessionPaceProjection
import com.example.kpkn.screens.workout.projectSessionPace
import com.example.kpkn.ui.design.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

@Composable
internal fun SessionTimeSheet(
    elapsedSeconds: Int, startTimeMs: Long, targetMinutes: Int?, accent: Color,
    completedWorkingSets: Int, totalWorkingSets: Int, selectedMinutes: Int?,
    pendingDescription: String?, onProposeMinutes: (Int) -> Unit, onAssignTime: () -> Unit,
    onClear: () -> Unit, onApplyPending: (Boolean) -> Unit,
    alertMode: PacingAlertMode, onAlertMode: (PacingAlertMode) -> Unit,
    ultraFastApplied: Boolean, ultraFastSavedSeconds: Int,
    onUltraFastPreview: (() -> Unit)?, onRevertUltraFast: (() -> Unit)?, onDismiss: () -> Unit,
) {
    LiveSheet(title = "Tiempo de sesión", onDismiss = onDismiss, tag = "session-time-sheet") {
        SessionTimeSheetContent(elapsedSeconds, startTimeMs, targetMinutes, accent, completedWorkingSets, totalWorkingSets,
            selectedMinutes, pendingDescription, onProposeMinutes, onAssignTime, onClear, onApplyPending,
            alertMode, onAlertMode, ultraFastApplied, ultraFastSavedSeconds, onUltraFastPreview, onRevertUltraFast)
    }
}

/** Callback-only body: the chronometer remains the single owner of pending/persistent time changes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionTimeSheetContent(
    elapsedSeconds: Int, startTimeMs: Long, targetMinutes: Int?, accent: Color,
    completedWorkingSets: Int, totalWorkingSets: Int, selectedMinutes: Int?,
    pendingDescription: String?, onProposeMinutes: (Int) -> Unit, onAssignTime: () -> Unit,
    onClear: () -> Unit, onApplyPending: (Boolean) -> Unit,
    alertMode: PacingAlertMode, onAlertMode: (PacingAlertMode) -> Unit,
    ultraFastApplied: Boolean, ultraFastSavedSeconds: Int,
    onUltraFastPreview: (() -> Unit)?, onRevertUltraFast: (() -> Unit)?,
) {
    // Prototipo 02 (`time`): el tiempo grande en Syne, el objetivo y acortar lo pendiente; la app conserva su proyección de ritmo,
    // los objetivos rápidos, aplicar solo hoy o siempre y los avisos.
    Column(Modifier.fillMaxWidth()) {
        Text(KpknSetFormat.rest(elapsedSeconds), Modifier.fillMaxWidth().padding(vertical = 18.dp),
            style = KpknType.display.copy(fontSize = 52.sp, lineHeight = 58.sp, fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        LiveSheetSubtitle(targetMinutes?.let { "Tiempo transcurrido · objetivo $it min" } ?: "Tiempo transcurrido de la sesión")
        projectSessionPace(elapsedSeconds.toLong(), completedWorkingSets, totalWorkingSets, targetMinutes)?.let {
            Box(Modifier.padding(bottom = 6.dp)) { PaceProjectionLines(it, startTimeMs) }
        }
        LiveSheetSectionLabel("Objetivo")
        val goals = listOf(30, 45, 60, 90)
        LiveSheetChoiceRow(goals.map { it to "$it min" } + listOf(-1 to "Otro…"), selected = { it == selectedMinutes },
            onSelect = { if (it < 0) onAssignTime() else onProposeMinutes(it) }, tagPrefix = "session-time-goal")
        if (targetMinutes != null) {
            LiveSheetChoiceRow(listOf(-5, 5, 15).map { it to "${if (it > 0) "+" else "−"}${kotlin.math.abs(it)} min" }, selected = { false },
                onSelect = { delta -> onProposeMinutes(((selectedMinutes ?: targetMinutes) + delta).coerceAtLeast(5)) }, tagPrefix = "session-time-delta")
            LiveSheetTextAction("Quitar objetivo", onClear, wide = false)
        }
        if (pendingDescription != null) {
            LiveSheetInlineNote(pendingDescription)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveSheetSecondary("Solo esta vez", { onApplyPending(false) }, Modifier.weight(1f))
                LiveSheetPrimary("Siempre", { onApplyPending(true) }, Modifier.weight(1f))
            }
        }
        LiveSheetSectionLabel("Avisos")
        val modes = listOf(PacingAlertMode.OFF, PacingAlertMode.FINAL, PacingAlertMode.SOFT, PacingAlertMode.STRICT)
        LiveSheetChoiceRow(modes.zip(listOf("Nada", "15 y 5 min", "Ritmo", "Por ejercicio")), selected = { it == alertMode },
            onSelect = onAlertMode, tagPrefix = "session-time-alert")
        if (ultraFastApplied) {
            onRevertUltraFast?.let { LiveSheetMenuRow("Deshacer el recorte de la sesión", it, icon = KpknLineIcons.Repeat,
                small = if (ultraFastSavedSeconds > 0) "−${ultraFastSavedSeconds / 60} min" else null) }
        } else {
            onUltraFastPreview?.let { LiveSheetMenuRow("Revisar cómo acortar lo pendiente", it, icon = KpknLineIcons.Sliders,
                modifier = Modifier.testTag("session-time-ultra")) }
        }
    }
}

@Composable
internal fun PaceProjectionLines(projection: SessionPaceProjection, startTimeMs: Long) {
    val end = if (startTimeMs > 0 && projection.totalSeconds < (Long.MAX_VALUE - startTimeMs) / 1_000) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startTimeMs + projection.totalSeconds * 1_000))
    } else null
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("A este ritmo terminas en ${ceil(projection.totalSeconds / 60.0).toLong()} min", style = KpknType.bodyStrong, color = WizardColors.text)
        end?.let { Text("Fin estimado $it", style = KpknType.caption, color = WizardColors.textMuted) }
        projection.overTargetSeconds?.let { delta ->
            val minutes = ceil(kotlin.math.abs(delta) / 60.0).toLong()
            Text(when { delta > 0 -> "+$minutes min sobre tu plan"; delta < 0 -> "$minutes min antes de tu plan"; else -> "Dentro de tu plan" }, style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

/** Same pure estimator as the time sheet; clock is scoped to this visible cockpit section. */
@Composable
internal fun SessionPaceEstimate(startTimeMs: Long, completed: Int, total: Int, targetMinutes: Int?) {
    var now by remember(startTimeMs) { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    val visibility = rememberFeedbackAnimationVisibility()
    LaunchedEffect(owner, startTimeMs, completed, total, visibility.visible) {
        if (startTimeMs <= 0 || completed < 2 || total < completed || !visibility.visible) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { now = System.currentTimeMillis(); delay(1_000) }
        }
    }
    Box(Modifier.fillMaxWidth().then(visibility.modifier)) {
        if (startTimeMs > 0) projectSessionPace((now - startTimeMs) / 1_000, completed, total, targetMinutes)?.let { PaceProjectionLines(it, startTimeMs) }
    }
}
