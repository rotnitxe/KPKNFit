package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.kpkn.ui.design.motion.kpknAnimate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.domain.workout.FreeClock
import com.example.kpkn.domain.workout.FreeClockState
import com.example.kpkn.domain.workout.FreeTimerPhase
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.PacingAlertMode
import com.example.kpkn.screens.workout.SessionPaceProjection
import com.example.kpkn.screens.workout.projectSessionPace
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Hoja «Tiempo» (Pulido v1 · punto 16): Sesión · Cronómetro · Temporizador. «Sesión» conserva todo lo de la hoja de antes y gana la
 * esfera; el cronómetro y el temporizador son libres (no tocan la sesión ni el descanso). Escenas `lx-t-*`.
 */
@Composable
internal fun SessionTimeSheet(
    elapsedSeconds: Int, startTimeMs: Long, targetMinutes: Int?, accent: Color,
    completedWorkingSets: Int, totalWorkingSets: Int, selectedMinutes: Int?,
    pendingDescription: String?, onProposeMinutes: (Int) -> Unit, onAssignTime: () -> Unit,
    onClear: () -> Unit, onApplyPending: (Boolean) -> Unit,
    alertMode: PacingAlertMode, onAlertMode: (PacingAlertMode) -> Unit,
    ultraFastApplied: Boolean, ultraFastSavedSeconds: Int,
    onUltraFastPreview: (() -> Unit)?, onRevertUltraFast: (() -> Unit)?, onDismiss: () -> Unit,
    freeClock: FreeClockState = FreeClockState(),
    clockActions: FreeClockActions = FreeClockActions(),
    initialTab: Int = 0,
) {
    LiveSheetWindow {
        // La hoja del lienzo es `.pl-sheet`: #1B1B1D opaco (no el #141513 de las demás hojas de la sesión).
        HeaderSheet(onDismiss = onDismiss, showDragHandle = false, scrim = Color(0xA8000000), panelTint = Color(0xFF1B1B1D)) { _ ->
            var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
            val running = freeClock.stopwatch.running || freeClock.timer.phase == FreeTimerPhase.RUNNING
            val nowMs = rememberFrameNowMs(active = tab == 0 || running)
            TimeSheetBody(
                tab = tab, onTab = { tab = it }, nowMs = nowMs, freeClock = freeClock, clockActions = clockActions,
                session = {
                    SessionTimeSheetContent(elapsedSeconds, startTimeMs, targetMinutes, accent, completedWorkingSets, totalWorkingSets,
                        selectedMinutes, pendingDescription, onProposeMinutes, onAssignTime, onClear, onApplyPending,
                        alertMode, onAlertMode, ultraFastApplied, ultraFastSavedSeconds, onUltraFastPreview, onRevertUltraFast,
                        sessionMs = { if (startTimeMs > 0L) (nowMs.value - startTimeMs).coerceAtLeast(0L) else elapsedSeconds * 1_000L })
                },
            )
        }
    }
}

private val PendingEntry by lazy { com.example.kpkn.ui.design.motion.KpknFamilies.EntradaBaja.demo }

/** Alto de la hoja del lienzo: `clamp(540px, 84%, 700px)` del alto de la pantalla. */
@Composable
internal fun timeSheetHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.84f).dp.coerceIn(540.dp, 700.dp)

/** Marco y pestañas de la hoja (sin la ventana): asa de 40 × 4, carril de pestañas y el panel de la elegida. Lo dibujan también las hojas de render. */
@Composable
internal fun TimeSheetBody(
    tab: Int, onTab: (Int) -> Unit, nowMs: State<Long>, freeClock: FreeClockState, clockActions: FreeClockActions,
    session: @Composable () -> Unit, modifier: Modifier = Modifier,
) {
    val timer = freeClock.timer
    // Si la alarma del sistema tarda, la interfaz ve el fin en cuanto llega la hora (idempotente).
    LaunchedEffect(timer.phase, timer.endAtMs) {
        if (timer.phase == FreeTimerPhase.RUNNING) {
            snapshotFlow { nowMs.value >= timer.endAtMs }.first { it }
            clockActions.onFinishIfDue()
        }
    }
    Column(modifier.fillMaxWidth().height(timeSheetHeight()).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 24.dp).testTag("session-time-sheet")) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF)))
        Spacer(Modifier.height(6.dp))
        LxTabs(tab, onTab, freeClock.stopwatch.running, timer.phase == FreeTimerPhase.RUNNING)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> session()
                1 -> StopwatchPane(freeClock, nowMs, clockActions)
                else -> TimerPane(freeClock, nowMs, clockActions)
            }
        }
    }
}

/**
 * Panel «Sesión»: esfera con arco hacia el objetivo, tiempo transcurrido y proyección de ritmo, objetivo, avisos y acortar lo pendiente
 * (`lx-t-sesion`). Los avisos y los objetivos son los de siempre; solo cambia cómo se ve.
 */
@Composable
internal fun SessionTimeSheetContent(
    elapsedSeconds: Int, startTimeMs: Long, targetMinutes: Int?, accent: Color,
    completedWorkingSets: Int, totalWorkingSets: Int, selectedMinutes: Int?,
    pendingDescription: String?, onProposeMinutes: (Int) -> Unit, onAssignTime: () -> Unit,
    onClear: () -> Unit, onApplyPending: (Boolean) -> Unit,
    alertMode: PacingAlertMode, onAlertMode: (PacingAlertMode) -> Unit,
    ultraFastApplied: Boolean, ultraFastSavedSeconds: Int,
    onUltraFastPreview: (() -> Unit)?, onRevertUltraFast: (() -> Unit)?,
    sessionMs: () -> Long = { elapsedSeconds * 1_000L },
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ClockDial(sessionMs, goalMinutes = targetMinutes ?: 0, subMaxMinutes = 60, modifier = Modifier.size(128.dp).testTag("session-time-dial"))
            Column(Modifier.weight(1f)) {
                val seconds by remember(sessionMs) { derivedStateOf { sessionMs() / 1_000L } }
                Text(FreeClock.formatClock(seconds), style = Lx.num(42.sp, (-1.4).sp).copy(lineHeight = 42.sp), color = Lx.Ink, maxLines = 1, softWrap = false,
                    modifier = Modifier.testTag("session-time-readout"))
                Text(targetMinutes?.let { "Tiempo transcurrido · objetivo $it min" } ?: "Tiempo transcurrido",
                    Modifier.padding(top = 4.dp), style = Lx.inter(12.sp), color = Lx.Muted)
                projectSessionPace(elapsedSeconds.toLong(), completedWorkingSets, totalWorkingSets, targetMinutes)?.let {
                    Box(Modifier.padding(top = 10.dp)) { PaceLines(it, startTimeMs) }
                }
            }
        }
        LxSectionLabel("Objetivo", Modifier.padding(top = 4.dp))
        val goals = listOf(30, 45, 60, 90)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            goals.forEachIndexed { i, m ->
                LxChip("$m min", m == selectedMinutes, { onProposeMinutes(m) }, Modifier.weight("$m min".length + 2f), fill = true, tag = "session-time-goal-$i")
            }
            LxChip("Otro…", false, onAssignTime, Modifier.weight(7f), fill = true, tag = "session-time-goal-4")
        }
        if (targetMinutes != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(-5, 5, 15).forEachIndexed { i, delta ->
                    LxChip("${if (delta > 0) "+" else "−"}${kotlin.math.abs(delta)} min", false,
                        { onProposeMinutes(((selectedMinutes ?: targetMinutes) + delta).coerceAtLeast(5)) }, tag = "session-time-delta-$i")
                }
            }
            // «Quitar objetivo» va a la derecha, en su propia línea (como en el lienzo a 360 dp, donde los chips llenan la anterior).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.height(40.dp).clickable(role = Role.Button, onClick = onClear).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text("Quitar objetivo", style = Lx.inter(13.sp, FontWeight.Medium), color = Lx.Muted, maxLines = 1, softWrap = false)
                }
            }
        }
        if (pendingDescription != null) {
            // `.lx-pend` (`lx-A07`): entra con `lxrise` 240 ms.
            Column(Modifier.fillMaxWidth().kpknAnimate(PendingEntry).clip(RoundedCornerShape(16.dp)).background(Color(0x08FFFFFF)).padding(12.dp)) {
                Text(pendingDescription, style = Lx.inter(13.sp, FontWeight.Medium), color = Lx.Muted, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LxButton("Solo hoy", { onApplyPending(false) }, Modifier.weight(1f), ghost = true, height = 40.dp, fontSize = 13.5.sp)
                    LxButton("Guardar en programa", { onApplyPending(true) }, Modifier.weight(1f), height = 40.dp, fontSize = 13.5.sp)
                }
            }
        }
        LxSectionLabel("Avisos", Modifier.padding(top = 4.dp))
        val modes = listOf(PacingAlertMode.OFF, PacingAlertMode.FINAL, PacingAlertMode.SOFT, PacingAlertMode.STRICT)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.zip(listOf("Nada", "15 y 5 min", "Ritmo", "Por ejercicio")).forEachIndexed { i, (mode, label) ->
                LxChip(label, mode == alertMode, { onAlertMode(mode) }, Modifier.weight(label.length + 2f), fill = true, tag = "session-time-alert-$i")
            }
        }
        if (ultraFastApplied) {
            onRevertUltraFast?.let {
                ClockRow("Deshacer el recorte de la sesión", KpknLineIcons.Repeat, it, small = if (ultraFastSavedSeconds > 0) "−${ultraFastSavedSeconds / 60} min" else null)
            }
        } else {
            onUltraFastPreview?.let { ClockRow("Revisar cómo acortar lo pendiente", KpknLineIcons.Sliders, it, Modifier.testTag("session-time-ultra")) }
        }
    }
}

/** `.lx-row`: fila de 54 dp con filete superior, icono en círculo de 38 dp, texto en negrita y flecha. */
@Composable
private fun ClockRow(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, small: String? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 54.dp).drawBehind { drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = Lx.Muted)
        }
        Text(label, Modifier.weight(1f), style = Lx.inter(14.sp, FontWeight.SemiBold), color = Lx.Ink)
        small?.let { Text(it, style = Lx.inter(11.sp), color = Lx.Faint) }
        Icon(KpknLineIcons.ChevronRight, null, Modifier.size(15.dp), tint = Lx.Faint)
    }
}

/** La proyección de ritmo con el texto del lienzo: negrita de 13 sp y líneas de 12 sp (la de «sobre tu plan» en coral si se pasa). */
@Composable
private fun PaceLines(projection: SessionPaceProjection, startTimeMs: Long) {
    val end = if (startTimeMs > 0 && projection.totalSeconds < (Long.MAX_VALUE - startTimeMs) / 1_000) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startTimeMs + projection.totalSeconds * 1_000))
    } else null
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("A este ritmo terminas en ${ceil(projection.totalSeconds / 60.0).toLong()} min", style = Lx.inter(13.sp, FontWeight.SemiBold, line = 17.sp), color = Lx.Ink)
        end?.let { Text("Fin estimado $it", style = Lx.inter(12.sp), color = Lx.Faint) }
        projection.overTargetSeconds?.let { delta ->
            val minutes = ceil(kotlin.math.abs(delta) / 60.0).toLong()
            Text(when { delta > 0 -> "+$minutes min sobre tu plan"; delta < 0 -> "$minutes min antes de tu plan"; else -> "Dentro de tu plan" },
                style = Lx.inter(12.sp), color = if (delta > 0) Lx.Danger else Lx.Faint)
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
