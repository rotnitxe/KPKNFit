package com.example.kpkn.domain.relator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Rotación de variantes (LR1h): con una, dos o tres frases, el mismo tema no repite la misma de inmediato. */
class RelatorVariantRotationTest {

    private fun planCandidate(set: Int, lines: List<String>) = RelatorCandidate(
        topic = RelatorTopic.PLAN,
        priority = 50,
        relevance = 1.0,
        lines = lines,
        fingerprintBase = "PLAN:$set",
    )

    /** El tema habla en cada serie (una clave nueva por serie) y devuelve la variante que dijo en cada una. */
    private fun variantsOverSets(lines: List<String>, sets: Int): List<Int> {
        var state = RelatorSelectorState()
        return (0 until sets).map { set ->
            val setKey = "ex_$set|0"
            val candidate = planCandidate(set, lines)
            val seed = RelatorSelector.deterministicSeed("s", setKey, 0)
            val idx = RelatorSelector.variantIndex(candidate, seed, state)
            state = state.record(
                fingerprint = RelatorSelector.fingerprint(candidate, idx),
                topic = RelatorTopic.PLAN,
                setKey = setKey,
                conceptId = null,
                exerciseId = "ex",
                variant = idx,
            )
            idx
        }
    }

    @Test
    fun una_sola_frase_es_siempre_la_variante_cero() {
        assertEquals(listOf(0, 0, 0, 0), variantsOverSets(listOf("Única {ex}."), 4))
    }

    @Test
    fun dos_frases_alternan_serie_a_serie() {
        val variants = variantsOverSets(listOf("Primera {ex}.", "Segunda {ex}."), 8)
        variants.zipWithNext().forEachIndexed { i, (a, b) ->
            assertNotEquals("la serie ${i + 2} repite la frase $a", a, b)
        }
    }

    @Test
    fun tres_frases_rotan_por_modulo_y_salen_todas() {
        val variants = variantsOverSets(listOf("A {ex}.", "B {ex}.", "C {ex}."), 6)
        variants.zipWithNext().forEachIndexed { i, (a, b) ->
            assertNotEquals("la serie ${i + 2} repite la frase $a", a, b)
        }
        assertEquals(3, variants.toSet().size)
    }

    @Test
    fun la_rotacion_recuerda_la_ultima_frase_del_tema_aunque_otro_tema_hable_entre_medias() {
        val lines = listOf("Primera {ex}.", "Segunda {ex}.")
        val first = planCandidate(0, lines)
        var state = RelatorSelectorState()
        val firstIdx = RelatorSelector.variantIndex(first, RelatorSelector.deterministicSeed("s", "ex_0|0", 0), state)
        state = state.record(
            fingerprint = RelatorSelector.fingerprint(first, firstIdx),
            topic = RelatorTopic.PLAN,
            setKey = "ex_0|0",
            conceptId = null,
            exerciseId = "ex",
            variant = firstIdx,
        )
        // Otro tema habla en la serie siguiente; el plan vuelve después.
        state = state.record("HISTORY:1:0:1", RelatorTopic.HISTORY, "ex_1|0", null, "ex")
        val again = planCandidate(2, lines)
        val againIdx = RelatorSelector.variantIndex(again, RelatorSelector.deterministicSeed("s", "ex_2|0", 0), state)
        assertNotEquals(firstIdx, againIdx)
    }

    @Test
    fun dentro_de_un_momento_la_variante_no_cambia() {
        val lines = listOf("Primera {ex}.", "Segunda {ex}.", "Tercera {ex}.")
        val state = RelatorSelectorState(lastVariantByTopic = mapOf(RelatorTopic.PLAN to 1))
        val candidate = planCandidate(3, lines)
        val seed = RelatorSelector.deterministicSeed("s", "ex_3|0", 0)
        assertEquals(
            RelatorSelector.variantIndex(candidate, seed, state),
            RelatorSelector.variantIndex(candidate, seed, state),
        )
    }
}
