package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11: a keyword profile names a food only as a whole word of its name, never as a piece of a longer word, plus the dessert
 * and hot dog profiles and the sourced range of the dessert. The profiles are named here by their calories per 100 g.
 */
class NutritionHeuristicEstimatorProfileTest {

    private val leanProtein = 165.0
    private val fattyProtein = 220.0
    private val fruit = 60.0
    private val vegetable = 28.0
    private val starchy = 85.0
    private val pasta = 131.0
    private val bread = 265.0
    private val nuts = 580.0
    private val oil = 780.0
    private val dessert = 310.0
    private val hotDog = 190.0
    private val mixedDish = 160.0

    private fun kcal(name: String): Double = NutritionHeuristicEstimator.estimatePer100g(name).calories

    private fun assertProfile(expected: Double, vararg names: String) {
        for (name in names) assertEquals(name, expected, kcal(name), 0.001)
    }

    @Test
    fun `a keyword inside a longer word does not name the food`() {
        // repollo holds pollo, fresa and fresco hold res, papaya holds papa: none of them is the food of its piece
        assertProfile(vegetable, "repollo", "repollo morado", "ensalada de repollo", "un repollo")
        assertProfile(fruit, "fresa", "fresas", "un plato de fresas", "papaya", "papayas", "una papaya")
        assertProfile(mixedDish, "fresco", "refresco", "jugo fresco", "nectares", "express")
        assertProfile(vegetable, "tres tomates")
        assertProfile(leanProtein, "pollo", "pollos", "pechuga de pollo")
    }

    @Test
    fun `the words that hold a keyword by themselves keep it`() {
        assertProfile(fattyProtein, "res", "carne de res", "carne", "cerdo")
        assertProfile(starchy, "papa", "papas", "batata")
        assertProfile(bread, "pan", "panes", "pan integral", "marraqueta")
        assertProfile(fruit, "frutilla", "manzana")
    }

    @Test
    fun `a plural or an accent does not hide a keyword`() {
        assertProfile(fruit, "manzanas", "plátano", "platano")
        assertProfile(vegetable, "verduras", "brócoli", "brocoli", "brocolí", "champiñón", "champiñones", "espinaca")
        assertProfile(leanProtein, "atún", "atun", "ATUN")
        assertProfile(starchy, "ñame", "papá")
        assertProfile(pasta, "pasta", "pastas", "lasaña", "lasana")
    }

    @Test
    fun `a nut paste is a nut, not pasta or butter`() {
        assertProfile(nuts, "pasta de maní", "pasta de mani", "crema de maní", "mantequilla de maní", "mantequilla de almendras")
        assertProfile(pasta, "pasta", "pasta cocida")
        assertProfile(oil, "mantequilla", "aceite")
    }

    @Test
    fun `tres leches and every other dessert are the dessert profile`() {
        assertProfile(
            dessert,
            "tres leches", "torta tres leches", "tres leches de chocolate", "un trozo de tres leches",
            "torta", "tortas", "torta de chocolate", "torta de zanahoria", "torta de manzana",
            "queque", "queques", "queque de vainilla", "queque de plátano",
            "kuchen", "kuchenes", "kuchen de manzana", "kuchen de nuez",
            "helado", "helados", "helado de vainilla", "helado de chocolate", "helado de frutilla",
            "pie", "pies", "pie de limón", "pie de manzana",
            "flan", "flanes", "flan de caramelo",
            "mil hojas", "milhojas", "cheesecake", "chesecake", "cheesecake de frambuesa",
            "brownie", "brownies", "alfajor", "alfajores", "alfajor de maicena",
        )
        // not three milks, not a beef cut ("tres" holds "res"), and not the sugar, vegetable or fruit of its flavour
        val tresLeches = NutritionHeuristicEstimator.estimatePer100g("tres leches")
        assertTrue(tresLeches.protein < 10.0)
        assertTrue(tresLeches.carbs > 30.0)
    }

    @Test
    fun `an iced drink is not a dessert`() {
        assertProfile(mixedDish, "té helado", "un te helado", "café helado", "jugo helado")
        assertProfile(dessert, "helado de té")
    }

    @Test
    fun `completo is the hot dog profile`() {
        assertProfile(
            hotDog,
            "completo", "un completo", "completos", "completo italiano", "completo americano", "completo con vienesa",
            "completo de pollo", "hot dog", "hotdog", "perro caliente",
        )
        val completo = NutritionHeuristicEstimator.estimatePer100g("completo italiano")
        assertEquals(6.0, completo.protein, 0.001)
        assertEquals(16.0, completo.carbs, 0.001)
        assertEquals(11.0, completo.fats, 0.001)
    }

    @Test
    fun `a specific profile still wins over the one of an ingredient word`() {
        assertProfile(dessert, "helado de chocolate", "torta de zanahoria", "kuchen de manzana")
        assertProfile(leanProtein, "pollo a la plancha")
        assertProfile(bread, "empanada", "empanadas de queso", "panqueque", "choripan")
        assertProfile(fattyProtein, "costillar", "huachalomo")
    }

    @Test
    fun `the dessert carries its sourced range as the evidence of the estimate`() {
        val estimate = NutritionHeuristicEstimator.estimateWithEvidence("tres leches")
        val evidence = estimate.evidence
        assertFalse(evidence.isUnmatchedFallback)
        // lowest and highest value of each nutrient over the 13 USDA/catalog rows cited in the profile table
        assertEquals(145.0, evidence.minPer100g.calories, 0.001)
        assertEquals(1.5, evidence.minPer100g.protein, 0.001)
        assertEquals(22.8, evidence.minPer100g.carbs, 0.001)
        assertEquals(2.7, evidence.minPer100g.fats, 0.001)
        assertEquals(466.0, evidence.maxPer100g.calories, 0.001)
        assertEquals(6.2, evidence.maxPer100g.protein, 0.001)
        assertEquals(63.9, evidence.maxPer100g.carbs, 0.001)
        assertEquals(29.1, evidence.maxPer100g.fats, 0.001)
        val central = estimate.profile
        assertTrue(central.calories in evidence.minPer100g.calories..evidence.maxPer100g.calories)
        assertTrue(central.protein in evidence.minPer100g.protein..evidence.maxPer100g.protein)
        assertTrue(central.carbs in evidence.minPer100g.carbs..evidence.maxPer100g.carbs)
        assertTrue(central.fats in evidence.minPer100g.fats..evidence.maxPer100g.fats)
        // the profile closes 4/4/9 within 5 %
        val atwater = 4.0 * central.protein + 4.0 * central.carbs + 9.0 * central.fats
        assertEquals(central.calories, atwater, central.calories * 0.05)
    }

    @Test
    fun `the other profiles and an unmatched name keep the generic bounds`() {
        val pollo = NutritionHeuristicEstimator.estimateWithEvidence("pollo").evidence
        assertFalse(pollo.isUnmatchedFallback)
        assertEquals(0.0, pollo.minPer100g.calories, 0.001)
        assertEquals(900.0, pollo.maxPer100g.calories, 0.001)
        val unknown = NutritionHeuristicEstimator.estimateWithEvidence("xyz plato").evidence
        assertTrue(unknown.isUnmatchedFallback)
        assertEquals(0.0, unknown.minPer100g.calories, 0.001)
        assertEquals(900.0, unknown.maxPer100g.calories, 0.001)
    }

    @Test
    fun `the evidence and the estimate agree on what a word matches`() {
        // a piece of a longer word is no match: the estimate is the generic dish and the evidence says so
        assertTrue(NutritionHeuristicEstimator.estimateWithEvidence("fresco").evidence.isUnmatchedFallback)
        assertTrue(NutritionHeuristicEstimator.estimateWithEvidence("refresco").evidence.isUnmatchedFallback)
        assertFalse(NutritionHeuristicEstimator.estimateWithEvidence("repollo").evidence.isUnmatchedFallback)
        assertFalse(NutritionHeuristicEstimator.estimateWithEvidence("fresa").evidence.isUnmatchedFallback)
    }

    @Test
    fun `the frying fat stays additive on the new profiles`() {
        val base = requireNotNull(estimateNutritionByKeyword("completo"))
        val frito = requireNotNull(estimateNutritionByKeyword("completo frito"))
        assertEquals(base.fats + 6.0, frito.fats, 0.001)
        assertEquals(base.calories + 9.0 * 6.0, frito.calories, 0.001)
    }
}
