package com.example.kpkn.domain.biomechanics.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class AnglesTest {
    @Test
    fun vec2OperatorsAndProducts() {
        val a = Vec2(3.0, 4.0)
        val b = Vec2(1.0, -2.0)
        assertEquals(Vec2(4.0, 2.0), a + b)
        assertEquals(Vec2(2.0, 6.0), a - b)
        assertEquals(Vec2(6.0, 8.0), a * 2.0)
        assertEquals(5.0, a.norm(), 1e-12)
        assertEquals(3.0 - 8.0, a.dot(b), 1e-12)
        assertEquals(3.0 * -2.0 - 4.0 * 1.0, a.cross(b), 1e-12)
    }

    @Test
    fun includedAngleIsInDegreesBetweenZeroAndOneEighty() {
        val b = Vec2(0.0, 0.0)
        assertEquals(90.0, Angles.included(Vec2(1.0, 0.0), b, Vec2(0.0, 1.0)), 1e-9)
        assertEquals(180.0, Angles.included(Vec2(1.0, 0.0), b, Vec2(-2.0, 0.0)), 1e-9)
        assertEquals(0.0, Angles.included(Vec2(1.0, 0.0), b, Vec2(3.0, 0.0)), 1e-9)
        assertEquals(45.0, Angles.included(Vec2(1.0, 0.0), b, Vec2(1.0, 1.0)), 1e-9)
        assertEquals(135.0, Angles.included(Vec2(1.0, 0.0), b, Vec2(-1.0, 1.0)), 1e-9)
        // Simétrico en los extremos y en el sentido de giro (el ángulo incluido no tiene signo).
        val a = Vec2(0.3, 0.8)
        val c = Vec2(-0.5, 0.1)
        val p = Vec2(0.1, 0.2)
        assertEquals(Angles.included(a, p, c), Angles.included(c, p, a), 1e-12)
        assertEquals(Angles.included(a, p, c), Angles.included(Vec2(a.x, -a.y), Vec2(p.x, -p.y), Vec2(c.x, -c.y)), 1e-9)
    }

    @Test
    fun includedAngleOfADegenerateTripleIsNaN() {
        val p = Vec2(0.2, 0.2)
        assertTrue(Angles.included(p, p, Vec2(1.0, 0.0)).isNaN())
        assertTrue(Angles.included(Vec2(1.0, 0.0), p, p).isNaN())
    }

    @Test
    fun trunkLeanIsZeroUprightAndPositiveForwardWhateverTheFacing() {
        val hip = Vec2(0.5, 0.6)
        // De pie: el hombro justo encima de la cadera (Y crece hacia abajo).
        assertEquals(0.0, Angles.trunkLean(Vec2(0.5, 0.3), hip, 1), 1e-9)
        assertEquals(0.0, Angles.trunkLean(Vec2(0.5, 0.3), hip, -1), 1e-9)
        // Inclinado 30° hacia la derecha de la imagen: positivo si mira a la derecha, negativo si mira a la izquierda.
        val lean = Vec2(0.5 + 0.3 * tan(Math.toRadians(30.0)), 0.3)
        assertEquals(30.0, Angles.trunkLean(lean, hip, 1), 1e-9)
        assertEquals(-30.0, Angles.trunkLean(lean, hip, -1), 1e-9)
        // Tumbado con la cabeza hacia delante: 90°.
        assertEquals(90.0, Angles.trunkLean(Vec2(0.9, 0.6), hip, 1), 1e-9)
        assertEquals(90.0, Angles.trunkLean(Vec2(0.1, 0.6), hip, -1), 1e-9)
    }
}
