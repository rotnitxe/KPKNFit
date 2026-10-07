package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlin.math.roundToInt

/*
 * Piezas compartidas por los componentes de semana y tiempo (día de más energía, calendario y dial): paleta, la política
 * de movimiento reducido y el reloj. Todo lleva el prefijo «Week» para no chocar con los kits de los otros paquetes del
 * mismo directorio.
 */

/** Colores de los componentes de semana y tiempo: la tinta de la marca y los acentos de módulo que usan. */
internal object WeekPalette {
    /** Tinta cálida de la marca (`#F2EEE6`). */
    val ink: Color = WizardColors.text
    val muted: Color = WizardColors.textMuted
    val faint: Color = WizardColors.textFaint

    /** Acento de columna (`#8FB2FF`): los días de entreno. */
    val columna = Color(0xFF8FB2FF)

    /** Acento de energía (`#F7CF73`): el día de más energía y la segundera. */
    val energia = Color(0xFFF7CF73)

    /** Tinta oscura sobre la tinta cálida (la inicial del día elegido). */
    val onInk = Color(0xFF0B0B0B)

    /** Tinta oscura sobre el azul de columna (la marca de «hecho»). */
    val onColumna = Color(0xFF0A1226)
}

/**
 * Anula «reducir movimiento» (la escala de animaciones del sistema) solo en la vista previa de depuración y en las pruebas.
 * Sin valor, manda el sistema.
 */
internal val LocalWeekReducedMotion = compositionLocalOf<Boolean?> { null }

/**
 * Congela el reloj de los bucles en ese instante (segundos) solo en la vista previa de depuración, para capturar un cuadro
 * concreto de la onda, el sol o la segundera. Sin valor, el reloj corre.
 */
internal val LocalWeekFrozenTime = compositionLocalOf<Float?> { null }

/**
 * Alarga las animaciones de duración fija (relleno, deslizado de la tira, cifra del dial) solo en la vista previa de depuración,
 * para capturar un cuadro a medias; 1 = normal. Los resortes no se alargan.
 */
internal val LocalWeekMotionScale = compositionLocalOf { 1f }

/** [base] milisegundos, alargados por [LocalWeekMotionScale]. */
@Composable
internal fun weekMillis(base: Int): Int = (base * LocalWeekMotionScale.current).roundToInt()

/** Verdadero con la escala de animaciones del sistema a 0 (o forzado desde la vista previa): cuadro final y sin bucles. */
@Composable
internal fun weekReducedMotion(): Boolean = LocalWeekReducedMotion.current ?: wizardReducedMotion()

/**
 * Reloj de un componente: UNO para todo lo que se mueve dentro de él. Los bucles (onda, sol, segundera) leen
 * [seconds] al dibujar, así que avanzarlo no recompone nada, solo vuelve a pintar.
 */
@Stable
internal class WeekClock {
    /** Segundos acumulados mientras el reloj ha corrido. Solo se lee al dibujar. */
    var seconds by mutableFloatStateOf(0f)
        internal set

    /** Lectura sin suscribirse: para anotar desde cuándo corre algo que se acaba de encender. */
    fun peek(): Float = Snapshot.withoutReadObservation { seconds }
}

/**
 * Crea el reloj y lo mueve mientras [active] y la app esté en primer plano. Al pararse conserva su valor y al reanudarse
 * sigue desde ahí. Usa `withInfiniteAnimationFrameNanos`: las pruebas de Compose cancelan este bucle en lugar de esperarlo.
 */
@Composable
internal fun rememberWeekClock(active: Boolean): WeekClock {
    val clock = remember { WeekClock() }
    val frozen = LocalWeekFrozenTime.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(running, frozen) {
        if (frozen != null) {
            clock.seconds = frozen
            return@LaunchedEffect
        }
        if (!running) return@LaunchedEffect
        val base = clock.peek()
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now -> clock.seconds = base + ((now - start) / 1_000_000_000.0).toFloat() }
        }
    }
    return clock
}
