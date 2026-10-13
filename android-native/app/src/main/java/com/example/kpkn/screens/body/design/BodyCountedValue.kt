package com.example.kpkn.screens.body.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknEntranceMemory

/** Duración de la cuenta de una cifra la primera vez que se ve (KPKN_UI_V2 §7). */
private const val COUNT_MILLIS = 900

/**
 * Cifra que cuenta hacia arriba: la primera vez que el proceso la ve, va de 0 a [target] en 900 ms; después, o con movimiento reducido,
 * muestra el valor final sin animar. Devuelve el valor del cuadro actual para que quien llama lo formatee. [key] identifica la cifra.
 */
@Composable
internal fun bodyCountedValue(key: String, target: Double): Double {
    val reduced = wizardReducedMotion()
    val counting = remember(key) { KpknEntranceMemory.firstTime("body-cuenta/$key", 0) && !reduced }
    val value = remember(key) { Animatable(if (counting) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        if (counting) {
            value.animateTo(target.toFloat(), tween(durationMillis = COUNT_MILLIS, easing = FastOutSlowInEasing))
        } else {
            value.snapTo(target.toFloat())
        }
    }
    return value.value.toDouble()
}
