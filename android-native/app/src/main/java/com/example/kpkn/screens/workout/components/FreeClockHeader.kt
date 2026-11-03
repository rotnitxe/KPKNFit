package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.domain.workout.FreeClock
import com.example.kpkn.domain.workout.FreeClockState
import com.example.kpkn.domain.workout.FreeTimerPhase
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.kpknAnimate
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/** ¿Hay un reloj libre en marcha que la píldora deba enseñar en su segunda línea? (Con los dos, manda el temporizador: es el que avisa.) */
internal fun FreeClockState.pillClock(): PillClock? = when {
    timer.phase == FreeTimerPhase.RUNNING -> PillClock.Timer
    stopwatch.running -> PillClock.Stopwatch
    else -> null
}

internal enum class PillClock { Stopwatch, Timer }

/** Una marca de hora que avanza cada [periodMs] mientras la pantalla está a la vista (para la segunda línea de la píldora). */
@Composable
internal fun rememberTickMs(active: Boolean, periodMs: Long): androidx.compose.runtime.State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner, periodMs) {
        now.longValue = System.currentTimeMillis()
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { now.longValue = System.currentTimeMillis(); delay(periodMs) }
        }
    }
    return now
}

/**
 * Segunda línea de la píldora de tiempo (`.lx-pill.lx-two .lx-r2`): icono de 12 dp y la cifra de 10,5 sp en gris. El cronómetro se
 * actualiza cada décima («1:12,4»); el temporizador, cada segundo («38:24»).
 */
@Composable
internal fun FreeClockPillLine(state: FreeClockState, clock: PillClock, scale: Float = 1f) {
    val now by rememberTickMs(active = true, periodMs = if (clock == PillClock.Stopwatch) 100L else 250L)
    val text = when (clock) {
        PillClock.Stopwatch -> FreeClock.formatLap(FreeClock.stopwatchElapsedMs(state.stopwatch, now))
        PillClock.Timer -> FreeClock.formatClock((FreeClock.timerRemainingMs(state.timer, now) + 999) / 1000)
    }
    Row(
        Modifier.testTag("pill-free-clock").semantics {
            contentDescription = (if (clock == PillClock.Stopwatch) "Cronómetro " else "Temporizador, quedan ") + text
        },
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (clock == PillClock.Stopwatch) FreeClockIcons.Stopwatch else FreeClockIcons.Hourglass, null, Modifier.size(12.dp * scale), tint = Lx.Muted)
        Text(text, style = Lx.num(10.5.sp * scale), color = Lx.Muted, maxLines = 1, softWrap = false)
    }
}

private val BannerEntry by lazy { KpknFamilies.EntradaBaja.demo.copy(duration = KpknDuration(320)) }

/**
 * Franja «Listo» del fin del temporizador con la hoja cerrada (`lx-t-coexiste`, `.lx-banner`): vidrio de isla con desenfoque, 24 dp de
 * radio, entra desde arriba en 320 ms y se queda hasta que se toca «Listo». Si el descanso termina a la vez, suena primero el descanso.
 */
@Composable
internal fun FreeClockBanner(hazeState: HazeState?, onDone: () -> Unit, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp)
            // `lx-S02`: isla-vidrio (0 8 28 #000a + realce interior); `lx-A03`: lxrise 320 ms con la curva estándar.
            .kpknAnimate(BannerEntry)
            .kpknShadow(KpknShadow.IslaVidrio, shape)
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape)
            .background(Color(0xC21B1B1D), shape)
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .heightIn(min = 64.dp)
            .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .testTag("free-clock-banner"),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x10FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(FreeClockIcons.Hourglass, null, Modifier.size(18.dp), tint = Lx.Ink)
        }
        Column(Modifier.weight(1f).then(if (onOpen != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpen) else Modifier)) {
            Text("TEMPORIZADOR", style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
            Text("Se acabó el tiempo", Modifier.padding(top = 2.dp), style = Lx.inter(14.5.sp, FontWeight.SemiBold), color = Lx.Ink)
        }
        LxButton("Listo", onDone, fontSize = 14.sp, horizontalPadding = 24.dp, tag = "free-clock-banner-done")
    }
}

/** El reloj libre del proceso (controlador único) visto desde la interfaz: su estado y las acciones que le pide. */
@Composable
internal fun rememberFreeClockBinding(): Pair<FreeClockState, FreeClockActions> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember(context) { com.example.kpkn.services.workout.FreeClockHub.controller(context) }
    val state by controller.state.collectAsState()
    val actions = remember(controller) {
        FreeClockActions(
            onStopwatchToggle = controller::stopwatchToggle, onStopwatchLapOrReset = controller::stopwatchLapOrReset,
            onKey = controller::timerKey, onPreset = controller::timerPreset, onSound = controller::timerSetSound,
            onVibrate = controller::timerSetVibrate, onStart = controller::timerStart, onPauseResume = controller::timerPauseOrResume,
            onAdd = controller::timerAdd, onBackToConfig = controller::timerBackToConfig, onRepeat = controller::timerRepeat,
            onFinishIfDue = { controller.finishIfDue() },
        )
    }
    return state to actions
}

/** «Listo»: el aviso del fin del temporizador se da por enterado. */
@Composable
internal fun rememberFreeClockDismiss(): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) { { com.example.kpkn.services.workout.FreeClockHub.controller(context).dismissBanner() } }
}
