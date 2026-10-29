package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import kotlin.math.abs
import kotlin.math.min

/** Etiqueta de la capa que tapa el contenido de una página del carrusel que no es la actual. */
internal const val LiveCardTouchBlockerTag = "live-card-touch-blocker"

/**
 * Sumidero de toques: la superficie entera queda «tocada» para la prueba de impacto de Compose, aunque en ese punto no haya ningún
 * control. Sin él, un toque en una zona vacía de la tarjeta actual pasaba a la hermana de debajo (la pila pone las tarjetas siguientes
 * justo bajo la actual) y enfocaba, por ejemplo, el campo de carga de la serie efectiva que asoma detrás. No consume nada: los controles
 * de dentro siguen recibiendo sus toques y el carrusel, que es un ancestro, sigue deslizando.
 */
internal fun Modifier.liveCardTouchSink(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/**
 * Página del carrusel de tarjetas con su protección de toques: la página absorbe los toques de toda su superficie (no atraviesan a la
 * tarjeta de debajo) y, si no es la actual, una capa por encima de su contenido impide que sus controles reciban toques. El deslizamiento
 * horizontal sigue funcionando sobre cualquier página porque nada se consume.
 */
@Composable
internal fun LiveCardTouchShield(
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liveCardTouchSink()) {
        content()
        if (!isCurrent) {
            Box(Modifier.matchParentSize().liveCardTouchSink().testTag(LiveCardTouchBlockerTag))
        }
    }
}

/** Alto que el carrusel reserva bajo la tarjeta para la pila de las dos siguientes (iteración 04, variante B). */
internal val LiveCardStackRoom = 44.dp

/** Contorno de la sombra de cada tarjeta del carrusel: el radio 25 de la tarjeta. */
internal val LiveCardShadowShape = androidx.compose.foundation.shape.RoundedCornerShape(25.dp)

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
 * más pequeñas. Al deslizar hacia la siguiente, la actual se desvanece mientras sale y la siguiente sube de la pila a su sitio; al
 * volver, la anterior entra por encima. [offset] es la posición de la página respecto de la
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
    // La que sale se desvanece (sin inclinarse ni quedar translúcida encima de la siguiente): a mitad del gesto ya no se ve,
    // así nunca se ven dos tarjetas superpuestas a medias (pedido del usuario, 29-oct).
    val out = abs(offset).coerceIn(0f, 1f)
    return LiveCardStackTransform(
        translationX = 0f,
        translationY = 0f,
        scale = 1f - .03f * out,
        rotation = 0f,
        alpha = (1f - out * 2.2f).coerceIn(0f, 1f),
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
