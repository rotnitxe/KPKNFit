package com.example.kpkn.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de las opciones excluyentes del catálogo:
 * - NUTRITION_ELIGIBILITY: `none` y `unknown` no se combinan con nada.
 * - RINGS_DISCOMFORT: `none` (sin molestias) y `omit` (prefiero omitirlo) tampoco.
 *
 * Solo se prueba la función pura: la UI delega en ella al pulsar una tarjeta.
 */
class SetupStepControlsTest {

    private val eligibilityExclusive = setOf("none", "unknown")
    private val discomfortExclusive = setOf("none", "omit")

    @Test
    fun `selecting an exclusive value drops the rest`() {
        val result = setupToggleExclusive(setOf("pregnancy", "lactation"), "none", eligibilityExclusive)
        assertEquals(setOf("none"), result)
    }

    @Test
    fun `an exclusive value is not combined with unknown`() {
        val result = setupToggleExclusive(setOf("unknown"), "none", eligibilityExclusive)
        assertEquals(setOf("none"), result)
    }

    @Test
    fun `a normal value drops the selected exclusive one`() {
        val result = setupToggleExclusive(setOf("none"), "medical_restriction", eligibilityExclusive)
        assertEquals(setOf("medical_restriction"), result)
    }

    @Test
    fun `normal values accumulate`() {
        val result = setupToggleExclusive(setOf("pregnancy"), "lactation", eligibilityExclusive)
        assertEquals(setOf("pregnancy", "lactation"), result)
    }

    @Test
    fun `deselecting an exclusive value leaves the set empty`() {
        val result = setupToggleExclusive(setOf("omit"), "omit", discomfortExclusive)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deselecting a normal value keeps the others`() {
        val result = setupToggleExclusive(setOf("pregnancy", "lactation"), "lactation", eligibilityExclusive)
        assertEquals(setOf("pregnancy"), result)
    }

    @Test
    fun `discomfort none and omit never coexist`() {
        val selected = setupToggleExclusive(setOf("neck_pain"), "none", discomfortExclusive)
        val withOmit = setupToggleExclusive(selected, "omit", discomfortExclusive)
        assertEquals(setOf("omit"), withOmit)
    }

    @Test
    fun `two toggles chained from the latest selection both land`() {
        val first = setupToggleExclusive(emptySet(), "pregnancy", eligibilityExclusive)
        val second = setupToggleExclusive(first, "lactation", eligibilityExclusive)
        assertEquals(setOf("pregnancy", "lactation"), second)
        // El Set viejo (el que la tarjeta leyó al componer el primer toque)
        // perdería la primera selección: por eso el toggle se calcula en el VM.
        assertNotEquals(second, setupToggleExclusive(emptySet(), "lactation", eligibilityExclusive))
    }

    @Test
    fun `an exclusive displaces every value selected before`() {
        val chain = listOf("pregnancy", "lactation", "unknown").fold(emptySet<String>()) { acc, value ->
            setupToggleExclusive(acc, value, eligibilityExclusive)
        }
        assertEquals(setOf("unknown"), chain)
    }
}
