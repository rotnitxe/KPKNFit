package com.example.kpkn.screens.drag

import org.junit.Assert.assertEquals
import org.junit.Test

class DropInsertionGeometryTest {
    @Test
    fun usaCadaPuntoMedioComoLimiteDeInsercion() {
        val stops = listOf(
            DropInsertionStop(midpointY = 50f, targetIndex = 0, targetKey = "a"),
            DropInsertionStop(midpointY = 150f, targetIndex = 1, targetKey = "b"),
        )

        assertEquals(DropInsertionResult(0, "a"), resolveDropInsertion(49f, stops, appendIndex = 2))
        assertEquals(DropInsertionResult(1, "b"), resolveDropInsertion(50f, stops, appendIndex = 2))
        assertEquals(DropInsertionResult(1, "b"), resolveDropInsertion(149f, stops, appendIndex = 2))
        assertEquals(DropInsertionResult(2, null), resolveDropInsertion(150f, stops, appendIndex = 2))
    }

    @Test
    fun usaElIndiceDeFinCuandoNoHayFilasVisibles() {
        assertEquals(DropInsertionResult(3, null), resolveDropInsertion(40f, emptyList(), appendIndex = 3))
    }
}
