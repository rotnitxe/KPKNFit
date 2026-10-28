package com.example.kpkn.screens.workout

import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import kotlin.math.abs
import kotlin.math.min

/** Alto que el carrusel reserva bajo la tarjeta para la pila de las dos siguientes (iteración 04, variante B). */
internal val LiveCardStackRoom = 44.dp

/** Solo con este alto de pantalla o más sobra hueco bajo la tarjeta para la pila (el Flip mide 880). */
internal const val LiveCardStackMinScreenHeightDp = 800

/** Transformación de una página del carrusel según su distancia a la actual. */
internal data class LiveCardStackTransform(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val rotation: Float,
    val alpha: Float,
)

/**
 * Pila de tarjetas: las siguientes no se ven a la derecha sino debajo de la actual, centradas, escalonadas (26 dp y 14 dp más) y algo
 * más pequeñas. Al deslizar hacia la siguiente, la actual sale hacia la izquierda inclinándose y desvaneciéndose, y la siguiente sube de
 * la pila a su sitio. Al volver, la anterior entra desde la izquierda por encima. [offset] es la posición de la página respecto de la
 * actual (> 0 siguientes); [stride] es lo que el carrusel desplaza una página.
 */
internal fun liveCardStackTransform(offset: Float, stride: Float, density: Float, stacked: Boolean = true): LiveCardStackTransform {
    if (offset >= 0f && !stacked) {
        // Sin sitio para la pila: la siguiente espera oculta detrás y aparece al subir a su sitio.
        return LiveCardStackTransform(-offset * stride, 0f, 1f - .06f * offset.coerceAtMost(1f), 0f, (1f - offset).coerceIn(0f, 1f))
    }
    if (offset >= 0f) {
        val depth = min(offset, 2f)
        val firstStep = min(depth, 1f)
        val secondStep = (depth - 1f).coerceAtLeast(0f)
        return LiveCardStackTransform(
            // Anula el desplazamiento lateral del carrusel: la tarjeta queda centrada bajo la actual.
            translationX = -offset * stride,
            translationY = (26f * firstStep + 14f * secondStep) * density,
            scale = 1f - .06f * firstStep - .05f * secondStep,
            rotation = 0f,
            alpha = if (offset > 2.5f) 0f else 1f,
        )
    }
    val out = abs(offset).coerceIn(0f, 1f)
    return LiveCardStackTransform(
        translationX = 0f,
        translationY = -10f * out * density,
        scale = 1f - .04f * out,
        rotation = -6f * out,
        alpha = (1f - out * 1.15f).coerceIn(0f, 1f),
    )
}

/** Orden de dibujo: la actual encima; las de la pila, cada vez más abajo; la anterior que vuelve, por encima de la actual. */
internal fun liveCardStackZ(offset: Float): Float = if (offset < 0f) 20f - offset * 0.5f else 20f - offset

/** Rótulo del borde de una tarjeta en la pila: «Serie 4 · 100 × 8», «Descanso», «Aproximaciones»… */
internal fun liveCardStackLabel(page: WorkoutSetSwipePage, exercise: Exercise): String = when (page.type) {
    LivePageType.NORMAL -> {
        val set = exercise.sets.getOrNull(page.setIndex)
        val side = when (page.side) { "left" -> " · izquierda"; "right" -> " · derecha"; else -> "" }
        val dose = set?.let {
            val weight = it.weight?.takeIf { w -> w > 0.0 }?.let(::formatDecimal)
            val reps = it.targetReps?.toString()
            when {
                weight != null && reps != null -> " · $weight × $reps"
                reps != null -> " · $reps reps"
                else -> ""
            }
        }.orEmpty()
        "Serie ${page.setIndex + 1}$side$dose"
    }
    LivePageType.REST -> "Descanso"
    LivePageType.WARMUP -> "Aproximaciones"
    LivePageType.MOBILITY -> "Movilidad"
    LivePageType.CARDIO -> "Cardio"
}
