package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * El desplazamiento de la página larga, para quien necesite moverla mientras el dedo arrastra algo (el tablero de la semana
 * lleva una sesión hasta el borde visible y la página sube o baja sola). Solo lo ofrece el anfitrión del asistente: sin él
 * ([LocalWizardPageScroll] en null) quien arrastra no desplaza nada.
 *
 * Mueve la página con el mismo límite que el scroll de la persona ([limit]): nunca más allá de lo que el paso activo deja ver,
 * ni hacia lo que el botón de confirmar no ha generado. [top] y [bottom] son, en píxeles de la ventana, la franja visible de la
 * página: sin la cabecera de arriba ni el botón de confirmar de abajo.
 */
internal class WizardPageScroll(
    private val scroll: ScrollState,
    private val limit: () -> Int,
    private val top: () -> Float,
    private val bottom: () -> Float,
) {
    /** El borde superior de la franja visible, en píxeles de la ventana. */
    fun visibleTop(): Float = top()

    /** El borde inferior de la franja visible, en píxeles de la ventana. */
    fun visibleBottom(): Float = bottom()

    /** Desplaza la página [px] píxeles (positivo = hacia abajo) sin pasar del límite; devuelve lo que se desplazó de verdad. */
    suspend fun scrollBy(px: Float): Float {
        val allowed = pageScrollStep(scroll.value, limit(), px)
        return if (allowed == 0f) 0f else scroll.scrollBy(allowed)
    }
}

/**
 * Cuánto de [delta] se puede desplazar una página que está en [value] y no puede pasar de [limit] (ni bajar de 0): hacia abajo
 * (positivo) hasta el límite, hacia arriba (negativo) hasta el principio. Una página ya más allá del límite no avanza más.
 */
internal fun pageScrollStep(value: Int, limit: Int, delta: Float): Float = when {
    delta.isNaN() -> 0f
    delta > 0f -> minOf(delta, (limit - value).coerceAtLeast(0).toFloat())
    delta < 0f -> maxOf(delta, -value.coerceAtLeast(0).toFloat())
    else -> 0f
}

internal val LocalWizardPageScroll = staticCompositionLocalOf<WizardPageScroll?> { null }
