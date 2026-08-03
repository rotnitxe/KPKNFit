package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class CookingFactorsTest {

    @Test
    fun `crudo is baseline`() {
        val factor = COOKING_FACTORS[CookingMethod.CRUDO]!!
        assertEquals(1.00, factor.kcal, 0.01)
        assertEquals(1.00, factor.fats, 0.01)
        assertEquals(1.00, factor.carbs, 0.01)
        assertEquals(1.00, factor.protein, 0.01)
    }

    @Test
    fun `cocido reduces kcal and protein from water dilution`() {
        val cocido = COOKING_FACTORS[CookingMethod.COCIDO]!!
        assertEquals(0.90, cocido.kcal, 0.01)
        assertEquals(0.95, cocido.protein, 0.01)
        assert(cocido.waterChange > 0) { "cocido should gain water" }
    }

    @Test
    fun `vapor slight kcal drop`() {
        assertEquals(0.95, COOKING_FACTORS[CookingMethod.VAPOR]!!.kcal, 0.01)
        assert(COOKING_FACTORS[CookingMethod.VAPOR]!!.waterChange > 0)
    }

    @Test
    fun `plancha fat loss from rendering`() {
        val f = COOKING_FACTORS[CookingMethod.PLANCHA]!!
        assertEquals(1.00, f.kcal, 0.01)
        assertEquals(0.95, f.fats, 0.01)
        assertEquals(1.05, f.protein, 0.01)
    }

    @Test
    fun `frito water loss concentrates`() {
        val f = COOKING_FACTORS[CookingMethod.FRITO]!!
        assertEquals(1.10, f.kcal, 0.01)
        assertEquals(1.00, f.fats, 0.01)
        assertEquals(1.10, f.protein, 0.01)
        assertEquals(-0.20, f.waterChange, 0.01)
    }

    @Test
    fun `empanizado frito applies water loss and breading carbs`() {
        val f = COOKING_FACTORS[CookingMethod.EMPANIZADO_FRITO]!!
        assertEquals(1.20, f.kcal, 0.01)
        assertEquals(1.00, f.fats, 0.01)
        assertEquals(1.20, f.carbs, 0.01)
        assertEquals(1.10, f.protein, 0.01)
    }

    @Test
    fun `asado parrilla fat drips off`() {
        val f = COOKING_FACTORS[CookingMethod.ASADO_PARRILLA]!!
        assertEquals(1.05, f.kcal, 0.01)
        assertEquals(0.90, f.fats, 0.01)
        assertEquals(1.10, f.protein, 0.01)
    }

    @Test
    fun `horno concentrates macros`() {
        val f = COOKING_FACTORS[CookingMethod.HORNO]!!
        assertEquals(1.15, f.kcal, 0.01)
        assertEquals(0.95, f.fats, 0.01)
        assertEquals(1.10, f.protein, 0.01)
    }

    @Test
    fun `guisado adds fat`() {
        val f = COOKING_FACTORS[CookingMethod.GUISADO]!!
        assertEquals(1.30, f.kcal, 0.01)
        assertEquals(1.20, f.fats, 0.01)
    }

    @Test
    fun `applyCookingToMacros adjusts correctly`() {
        val result = applyCookingToMacros(165.0, 31.0, 0.0, 3.6, CookingMethod.FRITO)
        assertEquals(181.5, result.calories, 0.1)
        assertEquals(34.1, result.protein, 0.1)
        assertEquals(3.6, result.fats, 0.1)
    }

    @Test
    fun `applyCookingToMacros with null returns original`() {
        val result = applyCookingToMacros(100.0, 20.0, 50.0, 3.0, null)
        assertEquals(100.0, result.calories, 0.01)
        assertEquals(20.0, result.protein, 0.01)
        assertEquals(50.0, result.carbs, 0.01)
    }

    @Test
    fun `all 11 methods have factors`() {
        assertEquals(11, COOKING_FACTORS.size)
        for (method in CookingMethod.entries) {
            assert(COOKING_FACTORS.containsKey(method)) { "Missing factor for $method" }
        }
    }

    @Test
    fun `waterChange negative for dry methods`() {
        assert(COOKING_FACTORS[CookingMethod.PLANCHA]!!.waterChange < 0)
        assert(COOKING_FACTORS[CookingMethod.HORNO]!!.waterChange < 0)
        assert(COOKING_FACTORS[CookingMethod.FRITO]!!.waterChange < 0)
    }

    @Test
    fun `waterChange positive for wet methods`() {
        assert(COOKING_FACTORS[CookingMethod.COCIDO]!!.waterChange > 0)
        assert(COOKING_FACTORS[CookingMethod.VAPOR]!!.waterChange > 0)
        assert(COOKING_FACTORS[CookingMethod.OLLA]!!.waterChange > 0)
    }

    @Test
    fun `isLikelyLiquid detects liquids`() {
        assert(isLikelyLiquid("agua"))
        assert(isLikelyLiquid("leche entera"))
        assert(isLikelyLiquid("bebida energética"))
        assert(isLikelyLiquid("jugo de naranja"))
        assert(isLikelyLiquid("café con leche"))
        assert(isLikelyLiquid("aceite de oliva"))
        assert(isLikelyLiquid("refresco"))
        assert(!isLikelyLiquid("pollo"))
        assert(!isLikelyLiquid("arroz"))
        assert(!isLikelyLiquid("manzana"))
    }

    @Test
    fun `B10 isLikelyLiquid no false positives por substring te`() {
        assert(!isLikelyLiquid("tomate"))
        assert(!isLikelyLiquid("lentejas"))
        assert(!isLikelyLiquid("filete de pollo"))
        assert(!isLikelyLiquid("mantequilla"))
        assert(!isLikelyLiquid("palta"))
        assert(isLikelyLiquid("te"))
        assert(isLikelyLiquid("té"))
        assert(isLikelyLiquid("leche descremada"))
    }

    // ─── IT3: absorción de aceite y factor de cocción por categoría ─────────

    @Test
    fun `IT3 aceite medio por categoria de alimento`() {
        assertEquals(6.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("Pechuga de Pollo")), 0.001)
        assertEquals(6.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("huevo")), 0.001)
        assertEquals(4.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("Carne molida")), 0.001)
        assertEquals(12.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("papas fritas")), 0.001)
        assertEquals(12.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("Empanada de Pino")), 0.001)
        assertEquals(8.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("tomate")), 0.001)
        assertEquals(7.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("lentejas")), 0.001)
        assertEquals(8.0, oilGramsForLevelInCategory("medio", oilAbsorptionCategory("algo raro")), 0.001)
    }

    @Test
    fun `IT3 aceite poco y abundante escalan sobre la categoria`() {
        assertEquals(3.0, oilGramsForLevelInCategory("poco", oilAbsorptionCategory("papas fritas")), 0.001)
        val abundantePapa = oilGramsForLevelInCategory("abundante", oilAbsorptionCategory("papas fritas"))
        assertEquals(26.4, abundantePapa, 0.1)
        val abundantePollo = oilGramsForLevelInCategory("abundante", oilAbsorptionCategory("Pollo"))
        assertEquals(13.2, abundantePollo, 0.1)
    }

    @Test
    fun `IT3 factor de fritura mayor para masas y tuberculos`() {
        val papas = cookingFactorFor("papas", CookingMethod.FRITO)
        assertEquals(1.20, papas.kcal, 0.01)
        val pollo = cookingFactorFor("Pollo", CookingMethod.FRITO)
        assertEquals(1.10, pollo.kcal, 0.01)
        val hornoPapas = cookingFactorFor("papas", CookingMethod.HORNO)
        assertEquals(1.15, hornoPapas.kcal, 0.01)
        val sinMetodo = cookingFactorFor("papas", null)
        assertEquals(1.00, sinMetodo.kcal, 0.01)
    }

    @Test
    fun `IT3 adjust oil por nombre de alimento`() {
        val base = com.example.kpkn.data.models.LoggedFood(
            id = "1", foodName = "Papa (cruda)", amount = 100.0,
            calories = 100.0, protein = 2.0, carbs = 20.0, fats = 1.0,
            cookingMethod = com.example.kpkn.data.models.CookingMethod.FRITO,
        )
        val papaFrita = adjustLoggedFoodForOil(base, com.example.kpkn.data.models.CookingMethod.FRITO, "medio", foodName = "Papa")
        assertEquals(13.0, papaFrita.fats, 0.01) // 1 + 12
        assertEquals(208.0, papaFrita.calories, 0.01) // 100 + 108
        val huevoFrito = adjustLoggedFoodForOil(base, com.example.kpkn.data.models.CookingMethod.FRITO, "medio", foodName = "Huevo")
        assertEquals(7.0, huevoFrito.fats, 0.01) // 1 + 6
        val sinNombre = adjustLoggedFoodForOil(base, com.example.kpkn.data.models.CookingMethod.FRITO, "medio")
        assertEquals(9.0, sinNombre.fats, 0.01) // 1 + 8 (default histórico)
    }

    @Test
    fun `IT3 strip y reapply con categoria son consistentes`() {
        val logged = com.example.kpkn.data.models.LoggedFood(
            id = "1", foodName = "Papas", amount = 100.0,
            calories = 208.0, protein = 2.0, carbs = 20.0, fats = 13.0,
            cookingMethod = com.example.kpkn.data.models.CookingMethod.FRITO,
        )
        val stripped = stripOilFromLoggedFood(logged, com.example.kpkn.data.models.CookingMethod.FRITO, "medio", foodName = "Papas")
        assertEquals(1.0, stripped.fats, 0.01)
        assertEquals(100.0, stripped.calories, 0.01)
        val reapplied = adjustLoggedFoodForOil(stripped, com.example.kpkn.data.models.CookingMethod.FRITO, "medio", foodName = "Papas")
        assertEquals(13.0, reapplied.fats, 0.01)
        assertEquals(208.0, reapplied.calories, 0.01)
    }
}
