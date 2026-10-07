package com.example.kpkn.screens.onboarding.design.entreno

import com.example.kpkn.domain.onboarding.MuscleSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de la cuadrícula de músculos: el orden, el tope, el «no» y la etiqueta «Sugerido». */
class MuscleGridLogicTest {

    @Test
    fun `la cuadricula trae los doce musculos una sola vez y en filas de tres`() {
        val order = MuscleGridLogic.gridOrder
        assertEquals(12, order.size)
        assertEquals(MuscleSymbol.entries.toSet(), order.toSet())
        assertEquals(12, order.distinct().size)
        assertEquals(3, MuscleGridLogic.COLUMNS)
        assertEquals(4, order.chunked(MuscleGridLogic.COLUMNS).size)
    }

    @Test
    fun `con sitio un toque elige y un musculo elegido siempre se puede quitar`() {
        val selected = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK)
        assertEquals(MuscleGridLogic.Tap.TOGGLE, MuscleGridLogic.tap(selected, MuscleSymbol.ABS, 5))
        assertEquals(MuscleGridLogic.Tap.TOGGLE, MuscleGridLogic.tap(selected, MuscleSymbol.CHEST, 5))
    }

    @Test
    fun `con el tope alcanzado los nuevos se rechazan pero los elegidos se pueden quitar`() {
        val full = setOf(
            MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS,
        )
        assertTrue(MuscleGridLogic.isCapped(full, 5))
        assertEquals(MuscleGridLogic.Tap.REJECT, MuscleGridLogic.tap(full, MuscleSymbol.ABS, 5))
        assertEquals(MuscleGridLogic.Tap.TOGGLE, MuscleGridLogic.tap(full, MuscleSymbol.BACK, 5))
        assertFalse(MuscleGridLogic.isCapped(full - MuscleSymbol.BACK, 5))
    }

    @Test
    fun `un tope de cero o de uno se respeta`() {
        assertEquals(MuscleGridLogic.Tap.REJECT, MuscleGridLogic.tap(emptySet(), MuscleSymbol.CHEST, 0))
        assertEquals(MuscleGridLogic.Tap.TOGGLE, MuscleGridLogic.tap(emptySet(), MuscleSymbol.CHEST, 1))
        assertEquals(MuscleGridLogic.Tap.REJECT, MuscleGridLogic.tap(setOf(MuscleSymbol.CHEST), MuscleSymbol.BACK, 1))
        // Con más elegidos que el tope (un borrador viejo) solo se pueden quitar.
        val over = setOf(MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.ABS)
        assertEquals(MuscleGridLogic.Tap.REJECT, MuscleGridLogic.tap(over, MuscleSymbol.CALVES, 2))
        assertEquals(MuscleGridLogic.Tap.TOGGLE, MuscleGridLogic.tap(over, MuscleSymbol.ABS, 2))
    }

    @Test
    fun `sugerido se rotula hasta que la persona toca ese musculo`() {
        val suggested = setOf(MuscleSymbol.QUADS, MuscleSymbol.GLUTES)
        assertTrue(MuscleGridLogic.showSuggestedTag(MuscleSymbol.QUADS, suggested, emptySet()))
        assertFalse(MuscleGridLogic.showSuggestedTag(MuscleSymbol.QUADS, suggested, setOf(MuscleSymbol.QUADS)))
        assertTrue(MuscleGridLogic.showSuggestedTag(MuscleSymbol.GLUTES, suggested, setOf(MuscleSymbol.QUADS)))
        assertFalse(MuscleGridLogic.showSuggestedTag(MuscleSymbol.CHEST, suggested, emptySet()))
    }

    @Test
    fun `la nota del tope usa el texto del modulo`() {
        assertEquals("Máximo 5 músculos.", MuscleGridLogic.capNote(5))
        assertEquals("Máximo 1 músculo.", MuscleGridLogic.capNote(1))
        assertEquals("Máximo 3 músculos.", MuscleGridLogic.capNote(3))
    }
}
