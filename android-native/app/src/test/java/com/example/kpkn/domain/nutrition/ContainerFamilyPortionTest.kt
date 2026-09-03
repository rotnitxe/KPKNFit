package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerFamilyPortionTest {

    private fun grams(expr: String): Double =
        SubjectivePortionEngine.resolve(expression = expr)?.grams ?: 0.0

    @Test
    fun `plato de avena es racion seca no 250g`() {
        val g = grams("un plato de avena")
        assertTrue("plato avena $g", g in 40.0..60.0)
    }

    @Test
    fun `bowl de avena es racion seca`() {
        val g = grams("un bowl de avena")
        assertTrue("bowl avena $g", g in 40.0..60.0)
        assertTrue(g <= 80.0)
    }

    @Test
    fun `taza de avena es 40g seco y escala con override`() {
        val original = SubjectivePortionEngine.currentUtensilOverrides()
        try {
            SubjectivePortionEngine.applyUtensilOverrides(emptyMap())
            assertEquals(40.0, grams("una taza de avena"), 0.01)
            SubjectivePortionEngine.applyUtensilOverrides(mapOf("taza" to 300.0))
            assertEquals(48.0, grams("una taza de avena"), 0.01)
        } finally {
            SubjectivePortionEngine.applyUtensilOverrides(original)
        }
    }

    @Test
    fun `plato de arroz se queda cocido 200-250g`() {
        val g = grams("un plato de arroz")
        assertTrue("plato arroz $g", g in 180.0..280.0)
    }

    @Test
    fun `bowl de yogurt es 150-200g no 300`() {
        val g = grams("un bowl de yogurt")
        assertTrue("bowl yogurt $g", g in 150.0..200.0)
    }

    @Test
    fun `un poco de palta es 30-40g`() {
        val g = grams("un poco de palta")
        assertTrue("poco palta $g", g in 30.0..40.0)
    }

    @Test
    fun `un poco de aceite no es una gota`() {
        val g = grams("un poco de aceite")
        assertTrue("poco aceite $g", g in 8.0..15.0)
    }

    @Test
    fun `assumedDefault plato de avena es seco como bowl`() {
        assertEquals(FoodState.RAW, CookingStateResolver.assumedDefault("avena", null))
        assertEquals(FoodState.RAW, CookingStateResolver.assumedDefault("plato de avena", null))
        assertEquals(FoodState.RAW, CookingStateResolver.assumedDefault("bowl de avena", null))
    }
}
