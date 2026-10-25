package com.example.kpkn.domain.nutrition.expenditure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieTargetControllerTest {
    @Test
    fun `el ritmo negativo propone deficit y el positivo superavit con densidad simetrica`() {
        val deficit = CalorieTargetController.propose(2500.0, -0.1, 2500, 1200.0)
        val surplus = CalorieTargetController.propose(2500.0, 0.1, 2500, 1200.0)
        assertEquals(-110, deficit.deltaKcal)
        assertEquals(110, surplus.deltaKcal)
        assertTrue(deficit.changed && surplus.changed)
    }

    @Test
    fun `la banda muerta evita propuestas diminutas`() {
        val result = CalorieTargetController.propose(2511.0, 0.0, 2500, 1200.0)
        assertEquals(2500, result.targetKcal)
        assertEquals(TargetClamp.DEAD_BAND, result.clampedBy)
        assertFalse(result.changed)
    }

    @Test
    fun `el tope limita subidas y bajadas por propuesta`() {
        for (direction in listOf(-1, 1)) {
            val result = CalorieTargetController.propose(2500.0 + direction * 600, 0.0, 2500, 1200.0)
            assertEquals(direction * 150, result.deltaKcal)
            assertEquals(TargetClamp.MAX_STEP, result.clampedBy)
        }
    }

    @Test
    fun `el suelo prevalece ante un deficit excesivo y se redondea hacia arriba`() {
        val result = CalorieTargetController.propose(1400.0, -0.8, 1300, 1203.5)
        assertEquals(1210, result.targetKcal)
        assertEquals(TargetClamp.FLOOR, result.clampedBy)
    }

    @Test
    fun `el redondeo respeta a la vez el tope aunque el objetivo no este alineado`() {
        val result = CalorieTargetController.propose(3000.0, 0.0, 2473, 1200.0, maxStepKcal = 145)
        assertEquals(2610, result.targetKcal)
        assertTrue(result.deltaKcal <= 145)
        assertEquals(0, result.targetKcal % 10)
    }

    @Test
    fun `un objetivo previo bajo el suelo se corrige con prioridad explicita`() {
        val result = CalorieTargetController.propose(900.0, 0.0, 700, 1200.0)
        assertEquals(1200, result.targetKcal)
        assertEquals(TargetClamp.FLOOR, result.clampedBy)
    }

    @Test
    fun `un objetivo que ya cumple el suelo conserva la banda muerta aunque no este alineado`() {
        val result = CalorieTargetController.propose(1000.0, 0.0, 1205, 1203.5)
        assertEquals(1205, result.targetKcal)
        assertEquals(TargetClamp.DEAD_BAND, result.clampedBy)
        assertFalse(result.changed)
    }

    @Test
    fun `el limite entero tampoco rompe la rejilla de redondeo`() {
        val result = CalorieTargetController.propose(3_000_000_000.0, 0.0, Int.MAX_VALUE - 100, 1200.0)
        assertEquals(0, result.targetKcal % 10)
        assertTrue(result.deltaKcal in 0..150)
    }

    @Test
    fun `redondea a la unidad solicitada y permite configurar la banda muerta`() {
        val result = CalorieTargetController.propose(2537.0, 0.0, 2500, 1200.0, roundToKcal = 25, deadBandKcal = 0)
        assertEquals(2525, result.targetKcal)
        assertEquals(25, result.deltaKcal)
    }

    @Test
    fun `un paso menor que la rejilla conserva un objetivo no alineado`() {
        val result = CalorieTargetController.propose(2600.0, 0.0, 2503, 1200.0, maxStepKcal = 1, deadBandKcal = 0)
        assertEquals(2503, result.targetKcal)
        assertFalse(result.changed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rechaza datos no finitos`() {
        CalorieTargetController.propose(Double.NaN, 0.0, 2500, 1200.0)
    }
}
