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
