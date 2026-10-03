package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.ParsedMealDescription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N1: `TagResolver.resolveAll` ends with `NutritionInterpretationBridge.enrich` on every tag it
 * returns, so its callers (the drawer's `resolveTags`) must not enrich a second time. Pure JVM; the
 * resolver port follows `IndependentNutritionCorpusTest.resolve` (static catalog + `FoodIndex` +
 * `SmartFoodResolver` over a no-op DAO).
 */
class TagResolverEnrichmentTest {

    private val staticFoods: List<FoodItem> by lazy { buildFoodDatabase() }

    private val port: FoodResolutionPort by lazy {
        val index = FoodIndex().apply { build(emptyList(), staticFoods, FOOD_ALIASES) }
        val dao = java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader, arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao
        val resolver = SmartFoodResolver(dao, index)
        object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = staticFoods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    /** Varied on purpose: explicit mass and cooking, subjective amounts, an exclusion, an unknown dish, a recipe expansion. */
    private val inputs = listOf(
        "150 g de arroz cocido con pollo a la plancha",
        "dos huevos revueltos y una taza de cafe sin azucar",
        "un completo italiano sin mayonesa",
        "pad thai con camarones",
        "un sandwich de jamon y queso",
    )

    private fun resolveAll(parsed: ParsedMealDescription): List<ResolvedTag> =
        runBlocking { TagResolver(port).resolveAll(parsed).first }

    @Test
    fun `every tag returned by resolveAll already carries its interpretation`() {
        for (text in inputs) {
            val tags = resolveAll(parseMealDescription(text))
            assertTrue("no tags for: $text", tags.isNotEmpty())
            for (tag in tags) {
                assertNotNull(
                    "interpretationV2 missing for '${tag.tag}' (excluded=${tag.isExcluded}) in: $text",
                    tag.interpretationV2,
                )
            }
        }
    }

    @Test
    fun `enriching the resolved tags a second time changes nothing`() {
        for (text in inputs) {
            val parsed = parseMealDescription(text)
            for (tag in resolveAll(parsed)) {
                assertEquals(
                    "a second enrich changed '${tag.tag}' in: $text",
                    tag,
                    NutritionInterpretationBridge.enrich(tag, parsed),
                )
            }
        }
    }
}
