package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-D1b (blind corpus #39): "porotos granados" was 149 kcal per 100 g, half as much again as the plate of its recipe, and the dry legumes had no rows (the
 * importer of USDA could not take them safely). The static catalog now has the raw black beans and chickpeas next to the raw lentils, found by "seco".
 */
class DryLegumeAndPorotosGranadosTest {

    private fun row(id: String) = checkNotNull(findStaticFoodById(id)) { id }

    // ─── Porotos granados ───────────────────────────────────────────────────────────

    @Test
    fun `the plate of porotos granados is 99 kcal per 100 g, the sum of its recipe`() {
        val granados = row("cl007")
        // the recipe of the plate of 350 g: 150 g of cranberry beans (136 kcal per 100 g), 80 g of corn (96), 60 g of squash (37), 5 g of olive oil (884)
        assertEquals(150 * 1.36 + 80 * 0.96 + 60 * 0.37 + 5 * 8.84, granados.calories, 1.0)
        assertEquals(350.0, granados.servingSize, 0.0)
        assertEquals("g", granados.unit)
        val per100 = granados.calories / granados.servingSize * 100.0
        assertTrue("$per100 kcal per 100 g", per100 in 90.0..120.0) // the Chilean tables: 100 to 120
        // the macros close within the tolerance of the catalog
        assertEquals(granados.calories, 4.0 * granados.protein + 4.0 * granados.carbs + 9.0 * granados.fats, 25.0)
        val tag = N11bTestEnv.resolve("porotos granados").single()
        assertEquals("cl007", tag.foodItem?.id)
        assertEquals(350.0, N11bTestEnv.grams(tag), 0.001)
        assertEquals(347.0, tag.loggedFood?.calories ?: Double.NaN, 1.0)
    }

    // ─── The dry legumes ────────────────────────────────────────────────────────────

    @Test
    fun `the dry legumes are found by their spoken names and carry the USDA energy of the raw seed`() {
        // id, USDA record and kcal per 100 g, the names a person says
        val dry = listOf(
            Triple("gen031c", "173734" to 341.0, listOf("poroto negro seco", "porotos negros secos", "poroto negro crudo", "frijol negro seco")),
            Triple("gen013c", "173756" to 378.0, listOf("garbanzo seco", "garbanzos secos", "garbanzos crudos")),
            Triple("gen012c", "172420" to 352.0, listOf("lentejas secas", "lenteja seca", "lentejas crudas")),
        )
        for ((id, source, names) in dry) {
            val food = row(id)
            assertEquals(id, source.second, food.calories, 0.0)
            assertEquals(id, FoodState.RAW, FoodIdentity.stateFor(food))
            for (name in names) assertEquals(name, id, findFoodExactByNormalized(name)?.id)
            if (id != "gen012c") {
                assertEquals(id, "PER_100G_RAW", food.nutritionBasis)
                assertEquals(id, source.first, food.sourceRecordId)
                assertTrue(id, food.source.orEmpty().contains(source.first))
            }
            assertEquals(id, food.calories, 4.0 * food.protein + 4.0 * food.carbs + 9.0 * food.fats, 0.12 * food.calories)
        }
    }

    @Test
    fun `a dry legume is not the cooked plate and cooked ones stay cooked`() {
        val tag = N11bTestEnv.resolve("poroto negro seco").single()
        assertEquals("gen031c", tag.foodItem?.id)
        assertNotEquals(198.0, N11bTestEnv.grams(tag), 0.001) // the cup of a cooked legume is not a dry portion
        val measured = N11bTestEnv.resolve("100 g de poroto negro seco").single()
        assertEquals(100.0, N11bTestEnv.grams(measured), 0.001)
        assertEquals(341.0, measured.loggedFood?.calories ?: Double.NaN, 0.5)
        // cooked: the existing row, a plate of one cup
        assertEquals("gen031", findFoodExactByNormalized("porotos negros (cocidos)")?.id)
    }
}
