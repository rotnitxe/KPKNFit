package com.example.kpkn.screens.onboarding.design.entreno

import com.example.kpkn.domain.onboarding.CapabilityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ritmo y cantidad de repeticiones de cada nivel de «¿Qué ejercicios ya te salen?», y la lógica de tocar el símbolo.
 */
class CapabilityMotionTest {

    private val dt = 0.01f

    /** Cuenta las repeticiones (subidas por encima de [high] seguidas de una bajada por debajo de [low]) en un periodo. */
    private fun repsIn(level: CapabilityLevel?, period: Float, high: Float = 0.9f, low: Float = 0.1f): Int {
        var reps = 0
        var armed = true
        var t = 0f
        while (t < period) {
            val d = CapabilityMotion.depthAt(level, t)
            if (armed && d >= high) {
                reps++
                armed = false
            } else if (!armed && d <= low) {
                armed = true
            }
            t += dt
        }
        return reps
    }

    @Test
    fun `sin responder y aun no es la figura quieta con el ejercicio a medias`() {
        for (level in listOf(null, CapabilityLevel.NONE)) {
            var t = 0f
            while (t < 12f) {
                assertEquals(CapabilityMotion.ATTEMPT, CapabilityMotion.depthAt(level, t), 0f)
                t += 0.37f
            }
        }
        assertTrue(CapabilityMotion.ATTEMPT in 0.3f..0.6f)
    }

    @Test
    fun `algunas hace tres repeticiones y una pausa`() {
        assertEquals(3, repsIn(CapabilityLevel.SOME, CapabilityMotion.SOME_PERIOD))
        // La pausa: desde que acaba la tercera hasta el final del periodo la figura está en la posición de salida.
        val pauseStart = CapabilityMotion.SOME_REPS * CapabilityMotion.SOME_REP_SECONDS
        var t = pauseStart + 0.02f
        while (t < CapabilityMotion.SOME_PERIOD - 0.01f) {
            assertEquals("pausa t=$t", 0f, CapabilityMotion.depthAt(CapabilityLevel.SOME, t), 1e-6f)
            t += 0.05f
        }
        assertTrue("la pausa debe notarse", CapabilityMotion.SOME_PERIOD - pauseStart >= 1.2f)
    }

    @Test
    fun `varias son repeticiones fluidas y continuas`() {
        // En un minuto caben 48 repeticiones.
        assertEquals(4, repsIn(CapabilityLevel.MANY, CapabilityMotion.MANY_PERIOD * 4))
        // «Fluidas»: nunca se queda quieta abajo (sin pausa).
        var still = 0f
        var worst = 0f
        var t = 0f
        while (t < CapabilityMotion.MANY_PERIOD * 4) {
            if (CapabilityMotion.depthAt(CapabilityLevel.MANY, t) < 0.03f) still += dt else still = 0f
            worst = maxOf(worst, still)
            t += dt
        }
        assertTrue("se detiene $worst s", worst < 0.2f)
    }

    @Test
    fun `algunas es mas lento y varias mas rapido`() {
        val someRep = CapabilityMotion.SOME_REP_SECONDS
        assertTrue(someRep > CapabilityMotion.MANY_PERIOD * 0.8f)
        assertTrue(CapabilityMotion.SOME_PERIOD > CapabilityMotion.MANY_PERIOD * 3f)
    }

    @Test
    fun `la profundidad siempre esta entre 0 y 1 y es periodica`() {
        for (level in CapabilityLevel.entries + listOf(null)) {
            val period = when (level) {
                CapabilityLevel.SOME -> CapabilityMotion.SOME_PERIOD
                CapabilityLevel.MANY -> CapabilityMotion.MANY_PERIOD
                else -> 1f
            }
            var t = -3f
            while (t < 20f) {
                val d = CapabilityMotion.depthAt(level, t)
                assertTrue("$level t=$t d=$d", d in -1e-5f..1f + 1e-5f)
                assertEquals("$level periodo t=$t", d, CapabilityMotion.depthAt(level, t + period), 1e-3f)
                t += 0.113f
            }
        }
    }

    @Test
    fun `los periodos dividen al reloj para que el bucle no de un salto`() {
        assertEquals(0.0, FIG_CLOCK_WRAP % CapabilityMotion.SOME_PERIOD, 1e-9)
        assertEquals(0.0, FIG_CLOCK_WRAP % CapabilityMotion.MANY_PERIOD, 1e-6)
    }

    @Test
    fun `entradas invalidas no rompen el bucle`() {
        assertEquals(0f, CapabilityMotion.wrap(Float.NaN, 5f), 0f)
        assertEquals(0f, CapabilityMotion.wrap(3f, 0f), 0f)
        assertEquals(0f, CapabilityMotion.wrap(3f, -2f), 0f)
        assertEquals(0f, CapabilityMotion.wrap(Float.POSITIVE_INFINITY, 5f), 0f)
        assertEquals(2f, CapabilityMotion.wrap(-3f, 5f), 1e-6f)
        assertEquals(0f, CapabilityMotion.depthAt(CapabilityLevel.MANY, Float.NaN), 1e-6f)
    }

    @Test
    fun `con movimiento reducido cada nivel tiene su cuadro estatico`() {
        assertEquals(CapabilityMotion.ATTEMPT, CapabilityMotion.restDepth(null), 0f)
        assertEquals(CapabilityMotion.ATTEMPT, CapabilityMotion.restDepth(CapabilityLevel.NONE), 0f)
        assertTrue(CapabilityMotion.restDepth(CapabilityLevel.SOME) > CapabilityMotion.ATTEMPT)
        assertTrue(CapabilityMotion.restDepth(CapabilityLevel.MANY) >= CapabilityMotion.restDepth(CapabilityLevel.SOME))
        assertTrue(!CapabilityMotion.isMoving(null) && !CapabilityMotion.isMoving(CapabilityLevel.NONE))
        assertTrue(CapabilityMotion.isMoving(CapabilityLevel.SOME) && CapabilityMotion.isMoving(CapabilityLevel.MANY))
    }

    // ─── Tocar el símbolo y los segmentos ────────────────────────────────────

    @Test
    fun `tocar el simbolo avanza de nivel y vuelve al primero`() {
        assertEquals(CapabilityLevel.SOME, CapabilityLevels.next(CapabilityLevel.NONE))
        assertEquals(CapabilityLevel.MANY, CapabilityLevels.next(CapabilityLevel.SOME))
        assertEquals(CapabilityLevel.NONE, CapabilityLevels.next(CapabilityLevel.MANY))
        // Sin responder cuenta como «Aún no»: el primer toque elige «Algunas».
        assertEquals(CapabilityLevel.SOME, CapabilityLevels.next(null))
    }

    @Test
    fun `los segmentos encendidos son ninguno sin responder y de uno a tres segun el nivel`() {
        assertEquals(0, CapabilityLevels.litSegments(null))
        assertEquals(1, CapabilityLevels.litSegments(CapabilityLevel.NONE))
        assertEquals(2, CapabilityLevels.litSegments(CapabilityLevel.SOME))
        assertEquals(3, CapabilityLevels.litSegments(CapabilityLevel.MANY))
        assertNull(CapabilityLevel.entries.firstOrNull { it.ordinal > 2 })
    }
}
