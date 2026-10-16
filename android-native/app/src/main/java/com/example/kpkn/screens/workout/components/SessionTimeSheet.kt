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
    HeaderSheet(onDismiss) { close ->
        HeaderSheetFrame("Tiempo de sesión", footer = { HeaderSheetAction("Listo", close, Modifier.fillMaxWidth(), primary = true) }) {
            SessionTimeSheetContent(elapsedSeconds, startTimeMs, targetMinutes, accent, completedWorkingSets, totalWorkingSets,
                selectedMinutes, pendingDescription, onProposeMinutes, onAssignTime, onClear, onApplyPending,
                alertMode, onAlertMode, ultraFastApplied, ultraFastSavedSeconds, onUltraFastPreview, onRevertUltraFast)
        }
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
    var ultraExpanded by remember { mutableStateOf(false) }
    var technique by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.2f) 118.dp else 132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val pad = 6.dp.toPx()
                    val arc = Size(size.width - 2 * pad, size.height - 2 * pad)
                    val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(WizardColors.textFaint.copy(alpha = .2f), -90f, 360f, false, Offset(pad, pad), arc, style = stroke)
                    val fraction = targetMinutes?.takeIf { it > 0 }?.let { (elapsedSeconds / (it * 60f)).coerceIn(0f, 1f) } ?: 0f
                    if (fraction > 0f) drawArc(accent, -90f, 360f * fraction, false, Offset(pad, pad), arc, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(KpknSetFormat.rest(elapsedSeconds), style = KpknType.metricM, color = accent)
                    Text(targetMinutes?.let { "de $it min" } ?: "Sin objetivo", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("En marcha", style = KpknType.caption, color = WizardColors.textMuted)
                projectSessionPace(elapsedSeconds.toLong(), completedWorkingSets, totalWorkingSets, targetMinutes)?.let { PaceProjectionLines(it, startTimeMs) }
            }
        }
        Text("DURACIÓN OBJETIVO", style = KpknType.eyebrow, color = WizardColors.textMuted)
        val goals = listOf(30, 45, 60, 90)
        KpknSegmentedControl(goals.map { "$it min" }, goals.indexOf(selectedMinutes), { onProposeMinutes(goals[it]) }, size = KpknSegmentedSize.Compact)
        HeaderSheetAction("Asignar tiempo", onAssignTime, Modifier.fillMaxWidth())
        if (targetMinutes != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-5, 5, 15).forEach { delta ->
                    HeaderSheetAction("${if (delta > 0) "+" else "−"}${kotlin.math.abs(delta)} min",
                        { onProposeMinutes(((selectedMinutes ?: targetMinutes) + delta).coerceAtLeast(5)) }, Modifier.weight(1f))
                }
            }
            HeaderSheetAction("Quitar", onClear, Modifier.fillMaxWidth())
        }
        if (pendingDescription != null) {
            Text(pendingDescription, style = KpknType.caption, color = WizardColors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderSheetAction("Solo esta vez", { onApplyPending(false) }, Modifier.weight(1f))
                HeaderSheetAction("Permanente", { onApplyPending(true) }, Modifier.weight(1f), primary = true)
            }
        }
        Text("AVISOS", style = KpknType.eyebrow, color = WizardColors.textMuted)
        val modes = listOf(PacingAlertMode.OFF, PacingAlertMode.FINAL, PacingAlertMode.SOFT, PacingAlertMode.STRICT)
        KpknSegmentedControl(listOf("Nada", "15 y 5 min", "Ritmo", "Por ejercicio"), modes.indexOf(alertMode),
            { onAlertMode(modes[it]) }, size = KpknSegmentedSize.Compact)
        HeaderSheetAction(if (ultraExpanded) "Ultrarrápido · Ocultar" else "Ultrarrápido · Mostrar", { ultraExpanded = !ultraExpanded }, Modifier.fillMaxWidth())
        if (ultraExpanded) {
            Text("Comprime el plan con técnicas intensas. Aumenta la fatiga y la carga articular; cuida tu recuperación.", style = KpknType.caption, color = WizardColors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip("Drop-sets ⓘ", technique == "drop", { technique = if (technique == "drop") null else "drop" })
                KpknChip("Rest-pauses ⓘ", technique == "rest", { technique = if (technique == "rest") null else "rest" })
            }
            technique?.let {
                Text(if (it == "drop") "Tras la serie efectiva, baja la carga lo suficiente para unas 3 repeticiones más." else "Mismo peso: descansa unos 15 segundos y realiza unas 3 repeticiones más.", style = KpknType.caption, color = WizardColors.textMuted)
                HeaderSheetAction("Cerrar concepto", { technique = null }, Modifier.fillMaxWidth())
            }
            if (ultraFastApplied) {
                Text(if (ultraFastSavedSeconds > 0) "Activo · Ahorro ~${KpknSetFormat.rest(ultraFastSavedSeconds)}" else "Activo", style = KpknType.bodyStrong, color = WizardColors.done)
                onRevertUltraFast?.let { HeaderSheetAction("Deshacer", it, Modifier.fillMaxWidth()) }
            } else onUltraFastPreview?.let { HeaderSheetAction("Ver compresión del plan", it, Modifier.fillMaxWidth(), primary = true) }
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
