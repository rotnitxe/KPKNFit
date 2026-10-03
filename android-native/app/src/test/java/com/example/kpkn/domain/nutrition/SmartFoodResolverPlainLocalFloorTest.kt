package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11: the branch of the decision that selects a local "plain" winner without asking used to need nothing beyond
 * MIN_THRESHOLD, so a match with a trace of evidence (a few shared words and an alias list the query does not use) was chosen
 * silently. It now needs [SmartFoodResolver.PLAIN_LOCAL_MIN_SCORE].
 *
 * The catalog is one synthetic curated row: the score of "zapallo asado" falls by 0.15 for every alias word the query does not
 * use, from 0.87 (none) to 0.27 (three), so each case sits well away from the 0.45 floor.
 */
class SmartFoodResolverPlainLocalFloorTest {

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao

    private fun zapallo(vararg unusedAliases: String) = FoodItem(
        id = "t_zapallo", name = "Zapallo", brand = "Genérico", servingSize = 100.0, unit = "g",
        calories = 26.0, protein = 1.0, carbs = 6.5, fats = 0.1,
        nutritionBasis = "PER_100G_AS_SOLD", source = "KPKN Curated (test)", sourceRecordId = "t_zapallo",
        searchAliases = listOf("zapallo") + unusedAliases,
    )

    private fun resolverFor(food: FoodItem): SmartFoodResolver {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = listOf(food), staticAliases = emptyMap())
        assertTrue("the row is a curated local one", index.getFood(food.id)?.isCuratedCatalog == true)
        return SmartFoodResolver(noOpNutritionDao(), index, null)
    }

    @Test
    fun `a plain local winner under the floor is a candidate, not a choice`() = runBlocking {
        val result = resolverFor(zapallo("calabaza", "pumpkin", "ayote")).resolve("zapallo asado")
        val winner = result.candidates.first()
        assertEquals("t_zapallo", winner.foodId)
        assertTrue("score ${winner.score}", winner.score >= SmartFoodResolver.MIN_THRESHOLD)
        assertTrue("score ${winner.score}", winner.score < SmartFoodResolver.PLAIN_LOCAL_MIN_SCORE)
        assertEquals(SmartFoodResolver.Decision.NEEDS_REVIEW, result.decision)
        // it is still the first thing the person is shown
        assertEquals("t_zapallo", result.resolvedFoodId)
    }

    @Test
    fun `the same food over the floor is still chosen without asking`() = runBlocking {
        val result = resolverFor(zapallo("calabaza")).resolve("zapallo asado")
        val winner = result.candidates.first()
        assertEquals("t_zapallo", winner.foodId)
        assertTrue("score ${winner.score}", winner.score >= SmartFoodResolver.PLAIN_LOCAL_MIN_SCORE)
        assertTrue("score ${winner.score}", winner.score < 0.70)
        assertEquals(SmartFoodResolver.Decision.AUTO_SELECT, result.decision)
    }

    @Test
    fun `a strong match is untouched`() = runBlocking {
        assertEquals(SmartFoodResolver.Decision.AUTO_SELECT, resolverFor(zapallo()).resolve("zapallo asado").decision)
        assertEquals(SmartFoodResolver.Decision.AUTO_SELECT, resolverFor(zapallo("calabaza", "pumpkin", "ayote")).resolve("zapallo").decision)
    }

    @Test
    fun `the floor is named, sits between the minimum and the medium threshold and nothing else moved`() {
        assertEquals(0.45, SmartFoodResolver.PLAIN_LOCAL_MIN_SCORE, 0.0)
        assertEquals(0.18, SmartFoodResolver.MIN_THRESHOLD, 0.0)
        assertTrue(SmartFoodResolver.PLAIN_LOCAL_MIN_SCORE > SmartFoodResolver.MIN_THRESHOLD)
        assertTrue(SmartFoodResolver.PLAIN_LOCAL_MIN_SCORE < SmartFoodResolver.MEDIUM_THRESHOLD)
        assertEquals(0.86, SmartFoodResolver.HIGH_THRESHOLD, 0.0)
        assertEquals(0.90, SmartFoodResolver.FUZZY_HIGH_THRESHOLD, 0.0)
        assertEquals(0.6, SmartFoodResolver.MEDIUM_THRESHOLD, 0.0)
        assertEquals(0.16, SmartFoodResolver.SAFE_GAP, 0.0)
        assertEquals(0.74, SmartFoodResolver.LEARNED_AUTO_THRESHOLD, 0.0)
    }
}
