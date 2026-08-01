package com.example.kpkn.domain.nutrition

import org.junit.Assert.*
import org.junit.Test

class ContextDetectorTest {

    @Test
    fun `jamón no dispara desayuno por el am`() {
        val result = ContextDetector.detect("2 huevos y jamón")
        assertFalse(ContextDetector.MealContext.DESAYUNO in result.detectedContexts)
    }

    @Test
    fun `desayuno explícito se detecta`() {
        val result = ContextDetector.detect("desayuno con 2 huevos")
        assertTrue(ContextDetector.MealContext.DESAYUNO in result.detectedContexts)
    }

    @Test
    fun `casino se detecta por palabra completa`() {
        val result = ContextDetector.detect("comida del casino")
        assertTrue(ContextDetector.MealContext.CASINO in result.detectedContexts)
    }

    @Test
    fun `trabajo no matchea trabajosa`() {
        val result = ContextDetector.detect("sopa trabajosa")
        assertFalse(ContextDetector.MealContext.OFICINA in result.detectedContexts)
    }

    @Test
    fun `snack y oficina en colacion de oficina`() {
        val result = ContextDetector.detect("colación en la oficina")
        assertTrue(ContextDetector.MealContext.SNACK in result.detectedContexts)
        assertTrue(ContextDetector.MealContext.OFICINA in result.detectedContexts)
    }

    @Test
    fun `sin contexto devuelve GENERAL`() {
        val result = ContextDetector.detect("200g de pollo a la plancha")
        assertEquals(ContextDetector.MealContext.GENERAL, result.primaryContext)
    }
}
