package com.example.kpkn.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay

/**
 * Entrada de un bloque de la app. La PRIMERA vez que el proceso compone el bloque [index] de [group], se funde de 0 a 1 y sube 12 dp
 * hasta 0, en 420 ms con `FastOutSlowInEasing` y con retardo `index × 50 ms`. Después no se repite, ni al volver a la pantalla. Con
 * movimiento reducido no hay entrada. Va con `graphicsLayer`, así que no recompone por cuadro.
 */
fun Modifier.kpknEntrance(group: String, index: Int): Modifier = composed {
    val reduced = wizardReducedMotion()
    // La plaza se consume aunque haya movimiento reducido: así la siguiente vez tampoco anima.
    val first = remember(group, index) { KpknEntranceMemory.firstTime(group, index) }
    val animate = first && !reduced
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val liftPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(animate) {
        if (animate) {
            delay(index * 50L)
            progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }
    Modifier.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * liftPx
    }
}

/** Bloques que ya han entrado en este proceso, por grupo y por índice: la entrada es una sola vez por proceso. */
internal object KpknEntranceMemory {
    private val entered = HashSet<String>()

    /** true solo la primera vez que se pide [index] de [group] en el proceso. */
    @Synchronized
    fun firstTime(group: String, index: Int): Boolean = entered.add("$group#$index")
}
