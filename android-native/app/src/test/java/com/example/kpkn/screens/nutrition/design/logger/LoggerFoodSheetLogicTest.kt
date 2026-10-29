package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.data.models.PortionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Tamaño» de la hoja «Ajustar un alimento» (Arreglos v1): cuatro tramos con sus gramos reales. */
class LoggerFoodSheetLogicTest {

    @Test fun sizesCarryTheirRealGrams() {
        val sizes = loggerSizeOptions(200.0)
        assertEquals(PortionPreset.entries, sizes.map { it.preset })
        assertEquals(listOf(150.0, 200.0, 250.0, 300.0), sizes.map { it.grams })
        assertEquals(listOf("Pequeña", "Mediana", "Grande", "Extra"), sizes.map { loggerSizeLabel(it.preset) })
        assertTrue(loggerSizeOptions(0.0).isEmpty())
    }

    @Test fun onlyTheSizeThatMatchesTheAmountIsMarked() {
        val sizes = loggerSizeOptions(200.0)
        assertEquals(PortionPreset.MEDIUM, loggerSelectedSize(sizes, 200.0))
        assertEquals(PortionPreset.EXTRA, loggerSelectedSize(sizes, 300.2))
        // Otra cantidad escrita a mano: ninguno marcado.
        assertNull(loggerSelectedSize(sizes, 210.0))
    }
}
