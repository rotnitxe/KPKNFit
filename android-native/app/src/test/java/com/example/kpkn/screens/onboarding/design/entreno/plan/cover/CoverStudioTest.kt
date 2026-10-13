package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las piezas puras del motor: el marco que cubre la caja, la ruta del topo, la carga de la semana y la semilla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoverStudioTest {

    @Test
    fun theFrameAlwaysCoversTheBoxWithTheDesignRatio() {
        val boxes = listOf(260f to 347f, 360f to 200f, 200f to 420f, 300f to 300f, 400f to 560f)
        val focuses = listOf(Offset(0f, 0f), Offset(0.5f, 0.5f), Offset(1f, 1f), Offset(0.62f, 0.32f))
        for ((bw, bh) in boxes) for (focus in focuses) {
            val f = coverFrameOf(bw, bh, focus)
            assertTrue("x de $bw×$bh", f.x <= 0.001f && f.x + f.w >= bw - 0.001f)
            assertTrue("y de $bw×$bh", f.y <= 0.001f && f.y + f.h >= bh - 0.001f)
            assertEquals(4f / 3f, f.h / f.w, 0.0001f)
        }
    }

    @Test
    fun aPosterBoxIsTheFrameItself() {
        val f = coverFrameOf(300f, 400f, Offset(0.5f, 0.5f))
        assertEquals(0f, f.x, 0.001f)
        assertEquals(0f, f.y, 0.001f)
        assertEquals(300f, f.w, 0.001f)
        assertEquals(400f, f.h, 0.001f)
    }

    @Test
    fun aBannerCropKeepsTheFocusInTheMiddle() {
        // Caja de 300 × 200: el marco es de 400 × 533 y el foco vertical cae en el centro de la banda.
        val focus = Offset(0.5f, 0.5f)
        val f = coverFrameOf(300f, 200f, focus)
        assertEquals(100f, f.y + focus.y * f.h, 0.5f)
        assertEquals(0f, f.x, 0.001f)
    }

    @Test
    fun theRouteGrowsToTheSummitAndThenErases() {
        assertEquals(0f, routeHeadOf(0f), 0f)
        assertEquals(1f, routeHeadOf(0.625f), 0.001f)
        assertEquals(0f, routeTailOf(0.5f), 0f)
        assertEquals(1f, routeTailOf(1f), 0.001f)
        // Dentro del ciclo, la cabeza sube y la cola no baja nunca.
        var lastHead = -1f
        var lastTail = -1f
        for (k in 0..100) {
            val u = k / 100f
            assertTrue(routeHeadOf(u) >= lastHead)
            assertTrue(routeTailOf(u) >= lastTail)
            lastHead = routeHeadOf(u)
            lastTail = routeTailOf(u)
        }
    }

    @Test
    fun theGridLoadRisesAndPeaksBeforeTheDeloadWeek() {
        assertEquals(1f, gridLoadOf(0, 1), 0f)
        assertEquals(0.25f, gridLoadOf(5, 6), 0f)
        assertEquals(1f, gridLoadOf(4, 6), 0.001f)
        val rising = (0..4).map { gridLoadOf(it, 6) }
        assertTrue("sube hasta el pico: $rising", rising.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun theSeedUnitIsStableAndInRange() {
        for (seed in listOf(0, 1, -7, 123456, Int.MAX_VALUE, Int.MIN_VALUE)) {
            for (index in 0..20) {
                val v = seedUnit(seed, index)
                assertTrue("rango $seed/$index: $v", v >= 0f && v < 1f)
                assertEquals(v, seedUnit(seed, index), 0f)
            }
        }
        assertNotEquals(seedUnit(5, 1), seedUnit(5, 2))
    }
}
