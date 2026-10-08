package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardPageMetricsTest {

    private val chip = 180
    private val gap = 36
    private val stride = chip + gap

    @Test
    fun `la primera pagina se ancla arriba y las demas dejan una fila-resumen encima`() {
        assertEquals(0, WizardPageMetrics.target(0, chip, gap))
        assertEquals(0, WizardPageMetrics.target(1, chip, gap))
        assertEquals(stride, WizardPageMetrics.target(2, chip, gap))
        assertEquals(4 * stride, WizardPageMetrics.target(5, chip, gap))
    }

    @Test
    fun `el inicio de cada pagina sale de la formula cerrada`() {
        assertEquals(200 + gap, WizardPageMetrics.top(0, 200, chip, gap))
        assertEquals(200 + gap + 3 * stride, WizardPageMetrics.top(3, 200, chip, gap))
    }

    @Test
    fun `la linea de foco queda bajo la cabecera y bajo la ultima fila-resumen`() {
        assertEquals(200 + gap, WizardPageMetrics.focusLine(0, 200, chip, gap))
        assertEquals(200 + gap + chip + gap, WizardPageMetrics.focusLine(1, 200, chip, gap))
        assertEquals(200 + gap + chip + gap, WizardPageMetrics.focusLine(7, 200, chip, gap))
    }

    @Test
    fun `el asomo llena lo que queda de pantalla y nunca baja de su minimo`() {
        assertEquals(1_400, WizardPageMetrics.peekWindow(viewportPx = 2_900, focusLinePx = 500, activeHeightPx = 1_000, minPeekPx = 450))
        assertEquals(450, WizardPageMetrics.peekWindow(viewportPx = 2_900, focusLinePx = 500, activeHeightPx = 2_800, minPeekPx = 450))
    }

    @Test
    fun `una pagina corta queda fija en su linea de foco`() {
        val limit = lockMax(index = 3, activeHeight = 1_200)
        assertEquals(WizardPageMetrics.target(3, chip, gap), limit)
    }

    @Test
    fun `una pagina mas alta que la pantalla deja bajar hasta ver su final y el asomo`() {
        val limit = lockMax(index = 3, activeHeight = 3_500)
        val expected = WizardPageMetrics.top(3, 200, chip, gap) + 3_500 + gap + 450 + 330 - 2_900
        assertEquals(expected, limit)
    }

    @Test
    fun `el limite nunca es negativo`() {
        assertEquals(0, lockMax(index = 0, activeHeight = 100))
    }

    @Test
    fun `el bloqueo corta el avance mas alla del limite y deja volver atras`() {
        val scroll = ScrollState(initial = 100)
        val lock = WizardScrollLock(scroll) { 150 }

        // Dentro del margen: no consume nada.
        assertEquals(Offset.Zero, lock.onPreScroll(Offset(0f, -30f), NestedScrollSource.UserInput))
        // Se pasa por 30 px: consume justo el exceso para que el contenido solo avance 50.
        assertEquals(Offset(0f, -30f), lock.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput))
        // Hacia atrás nunca se bloquea.
        assertEquals(Offset.Zero, lock.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput))
    }

    @Test
    fun `en el limite el fling hacia delante se descarta y hacia atras no`() = runBlocking {
        val atLimit = WizardScrollLock(ScrollState(initial = 150)) { 150 }
        assertEquals(Velocity(0f, -900f), atLimit.onPreFling(Velocity(0f, -900f)))
        assertEquals(Velocity.Zero, atLimit.onPreFling(Velocity(0f, 900f)))

        val inside = WizardScrollLock(ScrollState(initial = 10)) { 150 }
        assertEquals(Velocity.Zero, inside.onPreFling(Velocity(0f, -900f)))
    }

    @Test
    fun `la fila-resumen solo cambia de alto con la escala de fuente`() {
        assertEquals(WizardSpacing.summaryRowHeight, WizardSpacing.summaryRowHeightFor(1f))
        assertEquals(WizardSpacing.summaryRowHeightFor(1.4f), WizardSpacing.summaryRowHeightFor(1.4f))
        assertTrue(WizardSpacing.summaryRowHeightFor(2f) > WizardSpacing.summaryRowHeightFor(1.4f))
    }

    @Test
    fun `un paso cuyo final queda bajo el boton sube la pagina lo justo y nunca mas de una fila-resumen`() {
        // El final debe quedar sobre 2.900 − 330 = 2.570; la línea de foco (500) más un paso de 2.200 llega a 2.700: faltan 130.
        assertEquals(130, WizardPageMetrics.openExtra(focusLinePx = 500, activeHeightPx = 2_200, clearancePx = 330, viewportPx = 2_900, chipPx = chip))
        // Con un paso mucho más alto solo se sube una fila-resumen (la pregunta tiene que seguir a la vista).
        assertEquals(chip, WizardPageMetrics.openExtra(500, 6_000, 330, 2_900, chip))
    }

    @Test
    fun `un paso que ya cabe sobre el boton no sube nada`() {
        assertEquals(0, WizardPageMetrics.openExtra(500, 1_000, 330, 2_900, chip))
        // Justo en el límite (el final queda exactamente sobre el velo del botón): tampoco.
        assertEquals(0, WizardPageMetrics.openExtra(500, 2_070, 330, 2_900, chip))
        assertEquals(1, WizardPageMetrics.openExtra(500, 2_071, 330, 2_900, chip))
    }

    @Test
    fun `al abrirse el paso su final deja el margen del boton si cabe subiendo una fila-resumen`() {
        // Para cada alto de paso que se arregla subiendo una fila, el final queda a (al menos) la altura del botón del borde.
        for (active in 1_000..2_900 step 37) {
            val extra = WizardPageMetrics.openExtra(500, active, 330, 2_900, chip)
            val bottom = 500 + active - extra
            if (500 + active - 2_900 + 330 <= chip) {
                assertTrue("alto $active: el final queda en $bottom y el límite es ${2_900 - 330}", bottom <= 2_900 - 330)
            }
            assertTrue(extra in 0..chip)
        }
    }

    @Test
    fun `lo que sube la pagina al abrir nunca pasa de lo que el bloqueo deja ver`() {
        for (active in listOf(600, 1_200, 2_500, 3_500, 6_000)) {
            val index = 3
            val focusLine = WizardPageMetrics.focusLine(index, 200, chip, gap)
            val extra = WizardPageMetrics.openExtra(focusLine, active, 330, 2_900, chip)
            val limit = lockMax(index = index, activeHeight = active)
            assertTrue("alto $active: ${WizardPageMetrics.target(index, chip, gap)} + $extra no cabe en $limit", WizardPageMetrics.target(index, chip, gap) + extra <= limit)
        }
    }

    private fun lockMax(index: Int, activeHeight: Int): Int = WizardPageMetrics.lockMax(
        index = index,
        headerBottomPx = 200,
        chipPx = chip,
        gapPx = gap,
        peekPx = 450,
        clearancePx = 330,
        viewportPx = 2_900,
        activeHeightPx = activeHeight,
    )
}
