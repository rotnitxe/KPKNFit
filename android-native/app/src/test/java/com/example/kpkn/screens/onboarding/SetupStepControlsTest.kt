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

    // ── Bolsa de orden (PRIORITIES): reductor puro de deltas ────────────────

    @Test
    fun `priorities minus takes two points to one instead of clearing the entry`() {
        assertEquals(mapOf("Pecho" to 1), prioritiesAfterDelta(mapOf("Pecho" to 2), "Pecho", -1, 5, 2))
        // Nunca baja de 0 ni deja claves a cero.
        assertEquals(emptyMap<String, Int>(), prioritiesAfterDelta(mapOf("Pecho" to 1), "Pecho", -1, 5, 2))
        assertEquals(emptyMap<String, Int>(), prioritiesAfterDelta(emptyMap(), "Pecho", -1, 5, 2))
    }

    @Test
    fun `priorities increments accumulate back to back without losing any`() {
        val accumulated = (1..3).fold(emptyMap<String, Int>()) { bag, _ ->
            prioritiesAfterDelta(bag, "Pecho", +1, budget = 5, maxPerItem = 2)
        }
        // Tres incrementos encadenados sobre la bolsa última: dos entran y el
        // tercero lo rechaza el tope por músculo (2) sin tocar la entrada.
        assertEquals(mapOf("Pecho" to 2), accumulated)

        // El presupuesto se valida también sobre la última bolsa.
        val overBudget = (1..3).fold(emptyMap<String, Int>()) { bag, index ->
            prioritiesAfterDelta(bag, "Músculo $index", +1, budget = 2, maxPerItem = 2)
        }
        assertEquals(mapOf("Músculo 1" to 1, "Músculo 2" to 1), overBudget)
    }
}
