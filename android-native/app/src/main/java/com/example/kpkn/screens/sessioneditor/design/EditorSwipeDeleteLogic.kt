package com.example.kpkn.screens.sessioneditor.design

import kotlin.math.abs
import kotlin.math.max

/*
 * Borrar un ejercicio deslizando (Pulido v1 · EDITOR · A2, escenas `ed-borrar`, `-arrastre`, `-umbral` y `-aviso`): la lógica del gesto,
 * pura y en dp (los px de CSS del lienzo son dp). Todo sale de `scenes-ed.js` (ed-J13 … ed-J27): umbral de 104, resistencia del 42 % más allá,
 * bloqueo al deslizar más de 8 hacia la izquierda con una razón de 1,3 sobre la vertical, y soltado con velocidad.
 */
internal object EdSwipe {
    /** Recorrido que enciende la píldora de peligro (`THR`). */
    const val ThresholdDp = 104f

    /** Más allá del umbral la fila avanza un 42 % de lo que se desliza el dedo (`rub`). */
    const val RubberFactor = 0.42f

    /** Hacia la izquierda más de 8 dp y 1,3 veces lo que se mueve en vertical: el gesto es del borrado. */
    const val LockDx = -8f
    const val LockRatio = 1.3f

    /** Más de 10 dp en vertical, y más que en horizontal: el gesto es del scroll. */
    const val CancelDy = 10f

    /** Soltar con más de 0,9 dp/ms pasados 60 dp también borra. */
    const val FlingVelocity = 0.9f
    const val FlingMinReveal = 60f

    /** La píldora mide lo que se ha deslizado menos 10 dp (`Math.max(0, a-10)`) y aparece pasados 4. */
    const val PillInsetDp = 10f
    const val PillVisibleAfterDp = 4f

    /** Duración de la salida de la fila y de la píldora al borrar, y espera hasta el aviso (ms). */
    const val OutMillis = 220
    const val NoticeDelayMillis = 230L

    /** Vuelta de la fila al no borrar o al deshacer (ms, con rebote). */
    const val SnapMillis = 340
}

/** El deslizamiento con resistencia: hasta el umbral sigue al dedo; después, el 42 % de lo que sobra. */
internal fun edRubber(rawDp: Float): Float =
    if (rawDp <= EdSwipe.ThresholdDp) rawDp else EdSwipe.ThresholdDp + (rawDp - EdSwipe.ThresholdDp) * EdSwipe.RubberFactor

/** Cuánto se ha desplazado la fila (dp, ≥ 0) para un movimiento horizontal [dxDp] del dedo (negativo = hacia la izquierda). */
internal fun edReveal(dxDp: Float): Float = edRubber(max(0f, -dxDp))

/** ¿La píldora está encendida (en rosa, con «Eliminar»)? */
internal fun edArmed(revealDp: Float): Boolean = revealDp >= EdSwipe.ThresholdDp

/** Ancho de la píldora para un desplazamiento [revealDp]. */
internal fun edPillWidth(revealDp: Float): Float = max(0f, revealDp - EdSwipe.PillInsetDp)

/** La píldora se ve pasados 4 dp de desplazamiento. */
internal fun edPillVisible(revealDp: Float): Boolean = revealDp > EdSwipe.PillVisibleAfterDp

/** Qué hace el gesto con el movimiento acumulado: sigue sin decidirse, es de este borrado o es del scroll. */
internal enum class EdSwipeDecision { Undecided, Locked, Scroll }

/** Decide un gesto a partir del movimiento [dxDp] y [dyDp] desde que se tocó (la regla del lienzo). */
internal fun edSwipeDecision(dxDp: Float, dyDp: Float): EdSwipeDecision = when {
    abs(dyDp) > EdSwipe.CancelDy && abs(dyDp) > abs(dxDp) -> EdSwipeDecision.Scroll
    dxDp < EdSwipe.LockDx && abs(dxDp) > abs(dyDp) * EdSwipe.LockRatio -> EdSwipeDecision.Locked
    else -> EdSwipeDecision.Undecided
}

/** ¿Al soltar se borra? Pasado el umbral, o con velocidad ([velocityDpPerMs]) y más de 60 dp desplazados. */
internal fun edShouldCommit(revealDp: Float, velocityDpPerMs: Float): Boolean =
    revealDp >= EdSwipe.ThresholdDp || (velocityDpPerMs > EdSwipe.FlingVelocity && revealDp > EdSwipe.FlingMinReveal)

/**
 * Autoscroll al arrastrar una fila (`dragLoop` del lienzo, ed-J31 y ed-J35): con el dedo a menos de 150 dp del borde de arriba de la lista o a menos
 * de 170 del de abajo, la lista se desplaza hasta 16 dp por cuadro, más rápido cuanto más cerca del borde. Devuelve los px a desplazar en este
 * cuadro (negativo = hacia arriba) para un dedo en [pointerY] con la lista entre [listTop] y [listBottom] (px de ventana).
 */
internal fun edAutoScrollDelta(pointerY: Float, listTop: Float, listBottom: Float, density: Float): Float {
    val zoneTop = listTop + 150f * density
    val zoneBottom = listBottom - 170f * density
    return when {
        pointerY < zoneTop -> -minOf(16f, (zoneTop - pointerY) / (150f * density) * 16f) * density
        pointerY > zoneBottom -> minOf(16f, (pointerY - zoneBottom) / (170f * density) * 16f) * density
        else -> 0f
    }
}
