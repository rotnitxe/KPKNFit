package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/*
 * Reloj de las escenas de la bienvenida.
 *
 * Cada página tiene su reloj `t` (segundos). Corre SOLO mientras esa página es la actual y la app está en
 * primer plano; arranca en 0 al llegar a la página y vuelve a 0 al cumplir el periodo de la escena. Todo lo que
 * decide «qué cuadro se ve» es aritmética pura (esta parte del archivo, con prueba unitaria); lo único con estado
 * es [WelcomeSceneClock], que el anfitrión (SetupWelcomeScreen) eleva y reparte a las páginas.
 *
 * Las escenas que no corren (páginas vecinas, app en segundo plano, movimiento reducido) muestran su cuadro
 * representativo: `periodo × 0,7`, donde cada escena ya tiene la información completa a la vista.
 */

/** Fracción del periodo donde vive el cuadro representativo de una escena. */
internal const val WelcomeRepresentativeFraction = 0.7f

/** Cada medio fundido del bucle (salida al final del ciclo y entrada al empezar el siguiente): 0,25 s + 0,25 s ≈ el corte de 0,5 s. */
internal const val WelcomeLoopFadeSeconds = 0.25f

/** Tiempo de despedida del cuadro estático cuando una página acaba de ser la actual, antes de que la escena arranque desde 0. */
internal const val WelcomeArrivalLeadSeconds = 0.25f

/** Cuadro que se muestra cuando la escena no corre. */
internal fun representativeSceneTime(period: Float): Float = period * WelcomeRepresentativeFraction

/**
 * Tiempo de escena para los segundos transcurridos desde que la página llegó: 0 ≤ t < [period], en bucle.
 * Cualquier entrada inválida (negativa, NaN, infinita o un periodo no positivo) da 0.
 */
internal fun sceneTimeAt(elapsedSeconds: Float, period: Float): Float {
    if (!(period > 0f) || !period.isFinite()) return 0f
    if (!elapsedSeconds.isFinite() || elapsedSeconds <= 0f) return 0f
    val t = elapsedSeconds % period
    return if (t in 0f..period && t < period) t else 0f
}

/**
 * Opacidad del bucle: la escena se funde a negro al acabar el ciclo y vuelve al empezar el siguiente, así el
 * salto de «cuadro final» a «cuadro inicial» nunca se ve. Vale 0 en t = 0 y en t = [period], y 1 entre los dos fundidos.
 */
internal fun loopAlpha(t: Float, period: Float): Float {
    if (!(period > 0f)) return 1f
    val fade = minOf(WelcomeLoopFadeSeconds, period / 4f)
    val fadeIn = smoothStep((t / fade).coerceIn(0f, 1f))
    val fadeOut = smoothStep(((period - t) / fade).coerceIn(0f, 1f))
    return minOf(fadeIn, fadeOut)
}

private fun smoothStep(x: Float) = x * x * (3f - 2f * x)

/** Qué ve la escena en un instante: el tiempo de escena y la opacidad con la que se muestra. */
internal data class WelcomeSceneFrame(val t: Float, val alpha: Float)

/**
 * Cuadro de una página.
 *
 * - [playing] = false: la escena no corre, así que muestra su cuadro representativo a plena opacidad.
 * - [elapsedSeconds] < 0: la página acaba de llegar; el cuadro representativo se apaga durante
 *   [WelcomeArrivalLeadSeconds] y la escena arranca desde 0.
 * - Si no, la escena corre en bucle. Con [fadeLoop] el marco la funde con [loopAlpha] entre ciclos; las escenas que
 *   cierran solas (con su propio velo y la barra de estado a la vista) lo desactivan para no oscurecer dos veces.
 */
internal fun welcomeSceneFrame(
    elapsedSeconds: Float,
    period: Float,
    playing: Boolean,
    fadeLoop: Boolean = true,
): WelcomeSceneFrame {
    if (!playing) return WelcomeSceneFrame(representativeSceneTime(period), 1f)
    if (elapsedSeconds < 0f) {
        val a = (-elapsedSeconds / WelcomeArrivalLeadSeconds).coerceIn(0f, 1f)
        return WelcomeSceneFrame(representativeSceneTime(period), smoothStep(a))
    }
    val t = sceneTimeAt(elapsedSeconds, period)
    return WelcomeSceneFrame(t, if (fadeLoop) loopAlpha(t, period) else 1f)
}

/**
 * Estado del reloj: qué página lo posee, cuántos segundos lleva y si de verdad está corriendo.
 * `playing` solo pasa a true cuando llega el primer cuadro: si el reloj no puede correr (pruebas de
 * instrumentación, que cancelan las animaciones infinitas) la escena queda en su cuadro representativo.
 */
@Stable
internal class WelcomeSceneClock {
    /** Página a la que pertenece el reloj (−1 = ninguna todavía). */
    var page by mutableIntStateOf(-1)
        internal set

    /** Segundos desde que la página llegó; negativos durante la despedida del cuadro estático. */
    var elapsed by mutableFloatStateOf(0f)
        internal set

    var playing by mutableStateOf(false)
        internal set

    /** Si alguna página ya corrió: la primera arranca sin despedida; las siguientes, con ella. */
    internal var hasPlayed = false

    /** Cuadro de la página [index] con su [period]: solo la página poseedora y en marcha corre; el resto, cuadro fijo. */
    fun frameOf(index: Int, period: Float, fadeLoop: Boolean = true): WelcomeSceneFrame =
        if (index == page && playing) welcomeSceneFrame(elapsed, period, playing = true, fadeLoop = fadeLoop)
        else welcomeSceneFrame(0f, period, playing = false)
}

/** True mientras la app está al menos en RESUMED (primer plano con foco de la actividad). */
@Composable
internal fun rememberAppInForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * Crea el reloj y lo mueve: corre para [currentPage] mientras [enabled]; al cambiar de página (o al volver al
 * primer plano) arranca de nuevo desde 0. La primera página arranca sin despedida (nada que apagar).
 */
@Composable
internal fun rememberWelcomeSceneClock(currentPage: Int, enabled: Boolean): WelcomeSceneClock {
    val clock = remember { WelcomeSceneClock() }
    LaunchedEffect(currentPage, enabled) {
        clock.playing = false
        clock.page = currentPage
        clock.elapsed = 0f
        if (!enabled) return@LaunchedEffect
        val lead = if (clock.hasPlayed) WelcomeArrivalLeadSeconds else 0f
        // `withInfiniteAnimationFrameNanos` (y no `withFrameNanos`): las pruebas de Compose cancelan este bucle
        // infinito en lugar de esperarlo para siempre, así `waitForIdle()` sigue volviendo.
        val start = withInfiniteAnimationFrameNanos { it }
        clock.elapsed = -lead
        clock.playing = true
        clock.hasPlayed = true
        while (true) {
            withInfiniteAnimationFrameNanos { now -> clock.elapsed = (now - start) / 1_000_000_000f - lead }
        }
    }
    return clock
}
