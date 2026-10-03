package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.max
import kotlin.math.round

/**
 * WP-U10 / C8: el tope del slider de gramos sale de un ancla estable, nunca del valor arrastrado,
 * y el campo numérico solo confirma cantidades válidas. Puro JVM.
 */
class GramsSliderSpecTest {

    private fun assertMax(expected: Double, anchor: Double) =
        assertEquals("ancla $anchor", expected, GramsSliderSpec.maxFor(anchor), 0.0)

    @Test
    fun `small anchors keep the 600 g floor`() {
        assertMax(600.0, 100.0)
        assertMax(600.0, 1.0)
        assertMax(600.0, 299.0)
        assertMax(600.0, 300.0)
    }

    @Test
    fun `larger anchors double and round up to the next 100 g`() {
        assertMax(700.0, 350.0)
        assertMax(700.0, 301.0)
        assertMax(900.0, 450.0)
        assertMax(2500.0, 1234.0)
        assertMax(3000.0, 1500.0)
    }

    @Test
    fun `an invalid anchor leaves the floor instead of throwing`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -50.0).forEach {
            assertMax(600.0, it)
        }
    }

    @Test
    fun `floating point noise does not add an extra 100 g step`() {
        assertMax(3000.0, 1500.0000000000002)
        assertMax(700.0, 350.00000000000006)
    }

    @Test
    fun `anchor is the base mass when present and the current grams otherwise`() {
        assertEquals(250.0, GramsSliderSpec.anchorFor(250.0, 900.0), 0.0)
        assertEquals(900.0, GramsSliderSpec.anchorFor(null, 900.0), 0.0)
        assertEquals(120.0, GramsSliderSpec.anchorFor(0.0, 120.0), 0.0)
        assertEquals(120.0, GramsSliderSpec.anchorFor(Double.NaN, 120.0), 0.0)
    }

    @Test
    fun `the range is the same whatever value is being dragged`() {
        // El defecto C8: con el tope pegado al valor (max(600, 2 * valor)) arrastrar al extremo
        // derecho y soltar cuatro veces seguidas llevaba 400 g a 6.400 g.
        var legacy = 400.0
        repeat(4) { legacy = max(600.0, round(legacy * 2.0)) }
        assertEquals(6400.0, legacy, 0.0)

        // Con ancla estable el extremo derecho es siempre el mismo, gesto tras gesto.
        val anchor = GramsSliderSpec.anchorFor(baseAmountGrams = 400.0, currentGrams = 400.0)
        val firstMax = GramsSliderSpec.maxFor(anchor)
        var shown = anchor
        repeat(8) { shown = GramsSliderSpec.maxFor(anchor) }
        assertEquals(800.0, firstMax, 0.0)
        assertEquals(firstMax, shown, 0.0)
        // ...y el ancla que lee la tarjeta no cambia aunque el valor actual sí.
        listOf(1.0, 150.0, 800.0, 5000.0).forEach { current ->
            assertEquals(400.0, GramsSliderSpec.anchorFor(400.0, current), 0.0)
        }
    }

    @Test
    fun `whole grams round half up and never drop below one gram`() {
        assertEquals(63.0, GramsSliderSpec.wholeGrams(62.5), 0.0)
        assertEquals(62.0, GramsSliderSpec.wholeGrams(62.4), 0.0)
        assertEquals(1.0, GramsSliderSpec.wholeGrams(0.2), 0.0)
        assertEquals(1.0, GramsSliderSpec.wholeGrams(Double.NaN), 0.0)
        assertEquals(150.0, GramsSliderSpec.wholeGrams(150.0), 0.0)
    }

    @Test
    fun `typed text keeps digits and a single decimal separator`() {
        assertEquals("250", GramsSliderSpec.sanitizeInput("250"))
        assertEquals("2,5", GramsSliderSpec.sanitizeInput("2,5"))
        assertEquals("2.57", GramsSliderSpec.sanitizeInput("2.5.7"))
        assertEquals("12", GramsSliderSpec.sanitizeInput(" 1a2g"))
        assertEquals("", GramsSliderSpec.sanitizeInput("-g"))
        assertEquals("1234567", GramsSliderSpec.sanitizeInput("123456789"))
    }

    @Test
    fun `parseGrams returns whole grams or null for an unusable text`() {
        assertEquals(250.0, GramsSliderSpec.parseGrams("250")!!, 0.0)
        assertEquals(250.0, GramsSliderSpec.parseGrams(" 250 g")!!, 0.0)
        assertEquals(3.0, GramsSliderSpec.parseGrams("2,5")!!, 0.0)
        assertEquals(2.0, GramsSliderSpec.parseGrams("2.4")!!, 0.0)
        assertEquals(1.0, GramsSliderSpec.parseGrams("0,5")!!, 0.0)
        assertNull(GramsSliderSpec.parseGrams(""))
        assertNull(GramsSliderSpec.parseGrams("   "))
        assertNull(GramsSliderSpec.parseGrams("g"))
        assertNull(GramsSliderSpec.parseGrams("."))
        assertNull(GramsSliderSpec.parseGrams("0"))
        assertNull(GramsSliderSpec.parseGrams("0,4"))
    }

    @Test
    fun `parseGrams clamps absurd amounts to the typed maximum`() {
        assertEquals(10_000.0, GramsSliderSpec.parseGrams("10000")!!, 0.0)
        assertEquals(10_000.0, GramsSliderSpec.parseGrams("99999")!!, 0.0)
        assertEquals(10_000.0, GramsSliderSpec.parseGrams("123456789")!!, 0.0)
    }
}
