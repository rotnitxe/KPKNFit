package com.example.kpkn.screens.workout.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/*
 * Transiciones de la sesión en vivo (pedido del usuario, 29-oct): cuando una acción hace aparecer, desaparecer o cambiar algo, nunca
 * salta de golpe. Todo dura entre 160 y 320 ms con FastOutSlowIn y, con el movimiento reducido del sistema (`wizardReducedMotion()`),
 * cada función devuelve el corte inmediato (snap / None), que es lo que ya hacen el resto de capas de la sesión.
 */

/** Animación de un valor (selecciones, color, alto): [ms] con FastOutSlowIn, o corte si [reduced]. */
internal fun <T> liveSpec(reduced: Boolean, ms: Int = 220): FiniteAnimationSpec<T> =
    if (reduced) snap() else tween(ms, easing = FastOutSlowInEasing)

/** Entrada de un panel que se despliega: se funde y se abre en vertical. */
internal fun liveEnter(reduced: Boolean): EnterTransition =
    if (reduced) EnterTransition.None
    else fadeIn(tween(220, delayMillis = 40, easing = FastOutSlowInEasing)) + expandVertically(tween(280, easing = FastOutSlowInEasing))

/** Salida de un panel que se pliega: se funde y se cierra en vertical. */
internal fun liveExit(reduced: Boolean): ExitTransition =
    if (reduced) ExitTransition.None
    else fadeOut(tween(160, easing = FastOutSlowInEasing)) + shrinkVertically(tween(240, easing = FastOutSlowInEasing))

/** Entrada que solo se funde (superposiciones y capas que no cambian el alto). */
internal fun liveFadeIn(reduced: Boolean): EnterTransition =
    if (reduced) EnterTransition.None else fadeIn(tween(220, easing = FastOutSlowInEasing))

/** Salida que solo se funde. */
internal fun liveFadeOut(reduced: Boolean): ExitTransition =
    if (reduced) ExitTransition.None else fadeOut(tween(160, easing = FastOutSlowInEasing))

/**
 * Cambio de contenido en un `AnimatedContent`: el nuevo se funde por encima del anterior. Con [animateSize] el alto del contenedor
 * también se interpola; sin él, el recipiente conserva su tamaño y solo cambia lo de dentro.
 */
internal fun liveContentSwap(reduced: Boolean, animateSize: Boolean = false): ContentTransform {
    val size = if (animateSize) SizeTransform(clip = false) else null
    return if (reduced) ContentTransform(EnterTransition.None, ExitTransition.None, 0f, size)
    else ContentTransform(
        fadeIn(tween(240, delayMillis = 60, easing = FastOutSlowInEasing)),
        fadeOut(tween(160, easing = FastOutSlowInEasing)),
        0f,
        size,
    )
}
