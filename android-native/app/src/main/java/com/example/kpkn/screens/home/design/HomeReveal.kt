package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/*
 * Revelado de las cifras y los trazos de Home: la PRIMERA vez que el proceso los muestra, cuentan o crecen desde cero; después
 * aparecen con su valor. Con movimiento reducido, siempre el valor final.
 */

/** Duración de la cuenta de las cifras y del trazo de las barras, en ms. */
private const val RevealMillis = 900

/** Cifras y trazos de Home ya revelados en este proceso: el revelado es una sola vez por proceso. */
internal object HomeRevealMemory {
    private val revealed = HashSet<String>()

    /** true solo la primera vez que se pide [key] en el proceso. */
    @Synchronized
    fun firstTime(key: String): Boolean = revealed.add(key)
}

/**
 * Cifra que cuenta de 0 a [target] en 900 ms con `FastOutSlowInEasing`, solo la primera vez que el proceso la muestra ([key] la
 * identifica). Después, o con movimiento reducido, devuelve el valor final sin animar.
 */
@Composable
internal fun rememberHomeCount(key: String, target: Int): Int {
    val reduced = wizardReducedMotion()
    // La plaza se consume aunque haya movimiento reducido: así la siguiente vez tampoco cuenta.
    val animate = remember(key) { HomeRevealMemory.firstTime(key) } && !reduced
    // Arranca en 0 y pasa al objetivo tras la primera composición, para que la cuenta se vea.
    var goal by remember(key) { mutableIntStateOf(if (animate) 0 else target) }
    LaunchedEffect(key, target) { goal = target }
    val spec: AnimationSpec<Int> = if (reduced) snap<Int>() else tween<Int>(RevealMillis, easing = FastOutSlowInEasing)
    return animateIntAsState(targetValue = goal, animationSpec = spec, label = "homeCount").value
}

/**
 * Trazo que crece de 0 a [target] (de 0 a 1) en 900 ms, solo la primera vez que el proceso lo muestra ([key] lo identifica).
 * Igual que [rememberHomeCount], pero para anchuras, posiciones y dibujos.
 */
@Composable
internal fun rememberHomeTrace(key: String, target: Float): Float {
    val reduced = wizardReducedMotion()
    val animate = remember(key) { HomeRevealMemory.firstTime(key) } && !reduced
    var goal by remember(key) { mutableFloatStateOf(if (animate) 0f else target) }
    LaunchedEffect(key, target) { goal = target }
    val spec: AnimationSpec<Float> = if (reduced) snap<Float>() else tween<Float>(RevealMillis, easing = FastOutSlowInEasing)
    return animateFloatAsState(targetValue = goal, animationSpec = spec, label = "homeTrace").value
}
