package com.example.kpkn.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C3 · Concordancia de número en español: solo el uno va en singular; el cero y
 * cualquier otra cantidad van en plural. El ayudante es dominio puro (sin
 * `android.*`), así que estas pruebas corren en la JVM sin Robolectric.
 */
class SpanishPluralsTest {

    @Test
    fun onlyTheNumberOneIsSingular() {
        assertTrue(SpanishPlurals.isSingular(1))
        assertTrue("−1 también concuerda en singular", SpanishPlurals.isSingular(-1))
        assertFalse("el cero va en plural: «0 días»", SpanishPlurals.isSingular(0))
        assertFalse(SpanishPlurals.isSingular(2))
        assertFalse(SpanishPlurals.isSingular(11))
        assertFalse(SpanishPlurals.isSingular(100))
    }

    @Test
    fun chooseReturnsTheSingularOnlyForOne() {
        assertEquals("uno", SpanishPlurals.choose(1, "uno", "varios"))
        assertEquals("varios", SpanishPlurals.choose(0, "uno", "varios"))
        assertEquals("varios", SpanishPlurals.choose(3, "uno", "varios"))
    }

    @Test
    fun withNounJoinsTheNumberAndTheMatchingForm() {
        assertEquals("1 día", SpanishPlurals.withNoun(1, "día", "días"))
        assertEquals("0 días", SpanishPlurals.withNoun(0, "día", "días"))
        assertEquals("3 días", SpanishPlurals.withNoun(3, "día", "días"))
        assertEquals("1 plan publicado", SpanishPlurals.withNoun(1, "plan publicado", "planes publicados"))
        assertEquals("2 planes publicados", SpanishPlurals.withNoun(2, "plan publicado", "planes publicados"))
    }

    @Test
    fun theNamedShortcutsAgreeForOneZeroAndMany() {
        assertEquals("1 día", SpanishPlurals.days(1))
        assertEquals("6 días", SpanishPlurals.days(6))
        assertEquals("1 semana", SpanishPlurals.weeks(1))
        assertEquals("12 semanas", SpanishPlurals.weeks(12))
        assertEquals("1 sesión", SpanishPlurals.sessions(1))
        assertEquals("4 sesiones", SpanishPlurals.sessions(4))
        assertEquals("1 ejercicio", SpanishPlurals.exercises(1))
        assertEquals("5 ejercicios", SpanishPlurals.exercises(5))
        assertEquals("1 serie", SpanishPlurals.sets(1))
        assertEquals("3 series", SpanishPlurals.sets(3))
        assertEquals("1 rep", SpanishPlurals.reps(1))
        assertEquals("8 reps", SpanishPlurals.reps(8))
        assertEquals("1 bloque", SpanishPlurals.blocks(1))
        assertEquals("3 bloques", SpanishPlurals.blocks(3))
        assertEquals("1 plan", SpanishPlurals.plans(1))
        assertEquals("0 planes", SpanishPlurals.plans(0))
        assertEquals("1 paso", SpanishPlurals.steps(1))
        assertEquals("4 pasos", SpanishPlurals.steps(4))
    }

    @Test
    fun noShortcutEverProducesThePluralFormWithOne() {
        val withOne = listOf(
            SpanishPlurals.days(1),
            SpanishPlurals.weeks(1),
            SpanishPlurals.sessions(1),
            SpanishPlurals.exercises(1),
            SpanishPlurals.sets(1),
            SpanishPlurals.reps(1),
            SpanishPlurals.blocks(1),
            SpanishPlurals.plans(1),
            SpanishPlurals.steps(1),
        )
        withOne.forEach { text ->
            assertTrue("«$text» debe ir en singular", text.startsWith("1 ") && !text.endsWith("s"))
        }
    }

    @Test
    fun aWholePhraseCanChangeShapeWithOne() {
        fun fit(days: Int) = SpanishPlurals.choose(
            days,
            "Encaja con tu semana de 1 día",
            "Encaja con tus $days días por semana",
        )
        assertEquals("Encaja con tu semana de 1 día", fit(1))
        assertEquals("Encaja con tus 3 días por semana", fit(3))
        assertEquals("Encaja con tus 6 días por semana", fit(6))
    }
}
