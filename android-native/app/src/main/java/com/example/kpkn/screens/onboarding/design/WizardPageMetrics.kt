package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Geometría de la página larga, en píxeles y sin Compose, para poder probarla en JVM.
 *
 * Disposición (de arriba abajo): hueco de cabecera, una fila-resumen por cada página ya
 * confirmada, la página activa, la que asoma y un hueco final del alto de la pantalla. Todas las
 * filas-resumen miden lo mismo ([chipPx]) y van separadas por [gapPx], así que el sitio de cada
 * página sale de una fórmula cerrada y no hace falta medir nada para saber adónde deslizar.
 */
internal object WizardPageMetrics {

    /** Inicio de la página [index] dentro del contenido, suponiendo que todas las anteriores están plegadas. */
    fun top(index: Int, headerBottomPx: Int, chipPx: Int, gapPx: Int): Int =
        headerBottomPx + gapPx + index * (chipPx + gapPx)

    /**
     * Scroll que deja la página activa [index] en su línea de foco: la primera, justo bajo la
     * cabecera; las demás, con la última fila-resumen visible encima como contexto.
     */
    fun target(index: Int, chipPx: Int, gapPx: Int): Int =
        if (index <= 0) 0 else (index - 1) * (chipPx + gapPx)

    /**
     * Hasta dónde puede llegar el scroll del usuario: lo que el check no ha generado no se
     * alcanza arrastrando. Una página corta queda fija en su línea de foco; una más alta que la
     * pantalla deja bajar hasta ver su final y el asomo del siguiente por encima del botón.
     */
    fun lockMax(
        index: Int,
        headerBottomPx: Int,
        chipPx: Int,
        gapPx: Int,
        peekPx: Int,
        clearancePx: Int,
        viewportPx: Int,
        activeHeightPx: Int,
    ): Int {
        val pinned = target(index, chipPx, gapPx)
        val reachBottom = top(index, headerBottomPx, chipPx, gapPx) + activeHeightPx + gapPx + peekPx + clearancePx - viewportPx
        return maxOf(pinned, reachBottom).coerceAtLeast(0)
    }
}

/**
 * Bloquea el avance del scroll del usuario más allá de [limit]. Solo mira gestos y fling: el
 * deslizado que lanza el check usa `animateScrollTo`, que no pasa por aquí. Volver atrás
 * (arrastrar hacia abajo) nunca se bloquea.
 */
internal class WizardScrollLock(
    private val scroll: ScrollState,
    private val limit: () -> Int,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // El dedo sube (delta negativo) = el contenido avanza hacia lo siguiente.
        if (available.y >= 0f) return Offset.Zero
        val room = (limit() - scroll.value).coerceAtLeast(0)
        val wanted = -available.y
        if (wanted <= room) return Offset.Zero
        return Offset(0f, -(wanted - room))
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y < 0f && scroll.value >= limit()) Velocity(0f, available.y) else Velocity.Zero
}
