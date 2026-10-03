package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11: a sandwich expands inside its OWN mention. The greedy tail of the old regex and the `clear()` of the whole tag list made
 * "un sandwich de jamón y queso y una coca cola" lose the cola (and "sandwich de jamón y queso y un jugo" the juice).
 */
class SandwichMentionBoundaryTest {

    private class RealPort(
        private val resolver: SmartFoodResolver,
        private val foods: List<FoodItem>,
    ) : FoodResolutionPort {
        override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
            resolver.resolve(tag, brandHint, contextHint, stateHint)
        override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
        override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
        override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
        override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao

    private suspend fun resolve(description: String): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = FOOD_ALIASES)
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        return TagResolver(RealPort(resolver, foods)).resolveAll(parseMealDescription(description)).first
    }

    private fun List<ResolvedTag>.names() = map { it.tag }

    // ─── The mention ────────────────────────────────────────────────────────────────

    @Test
    fun `a known sandwich ends where its dish name ends`() {
        assertEquals("sandwich de jamón y queso", FoodCombinationParser.sandwichMention("1 sandwich de jamón y queso y 1 coca cola"))
        assertEquals("sandwich de jamón y queso", FoodCombinationParser.sandwichMention("sandwich de jamón y queso y 1 jugo"))
        assertEquals("sandwich de jamón y queso", FoodCombinationParser.sandwichMention("tomé 1 té y 1 sandwich de jamón y queso"))
        assertEquals("sándwich de pollo con mayonesa", FoodCombinationParser.sandwichMention("sándwich de pollo con mayonesa y 1 coca cola"))
    }

    @Test
    fun `sandwich de A y B ends at punctuation, before a quantified mention or at the end of the text`() {
        assertEquals("sandwich de ave y palta", FoodCombinationParser.sandwichMention("sandwich de ave y palta y 1 manzana"))
        assertEquals("sandwich de ave y palta", FoodCombinationParser.sandwichMention("sandwich de ave y palta, 1 manzana"))
        assertEquals("sandwich de ave y palta", FoodCombinationParser.sandwichMention("sandwich de ave y palta. 1 manzana"))
        assertEquals("sandwich de ave y palta", FoodCombinationParser.sandwichMention("sandwich de ave y palta; 1 manzana"))
        assertEquals("sandwich de ave y palta", FoodCombinationParser.sandwichMention("sandwich de ave y palta con 2 galletas"))
        assertEquals("sandwich de ave y palta", FoodCombinationParser.sandwichMention("sandwich de ave y palta"))
        // a third filling with no quantity in front of it is still the sandwich
        assertEquals("sandwich de mortadela y palta y queso", FoodCombinationParser.sandwichMention("sandwich de mortadela y palta y queso"))
    }

    @Test
    fun `a y before a quantified mention is not a filling separator`() {
        // the single filling is the mention and the juice is not part of it (WP-N11b); the row of "ave mayo" is what keeps it from being taken apart
        assertEquals("sandwich de ave mayo", FoodCombinationParser.sandwichMention("1 sandwich de ave mayo y 1 jugo"))
        assertEquals("sandwich de ave mayo", FoodCombinationParser.sandwichMention("1 sandwich de ave mayo"))
        assertNull(FoodCombinationParser.sandwichMention("pan con palta y huevo"))
        assertNull(FoodCombinationParser.sandwichMention(""))
    }

    @Test
    fun `the generic regex no longer swallows what follows the sandwich`() {
        val whole = FoodCombinationParser.parse("sandwich de ave y palta y 1 manzana")
        assertEquals("pan", whole.baseFood)
        assertEquals(listOf("ave", "palta"), whole.accompaniments.map { it.food })
        val mention = FoodCombinationParser.parse(requireNotNull(FoodCombinationParser.sandwichMention("sandwich de ave y palta y 1 manzana")))
        assertEquals(whole.accompaniments.map { it.food }, mention.accompaniments.map { it.food })
    }

    // ─── The tags ───────────────────────────────────────────────────────────────────

    @Test
    fun `un sandwich de jamon y queso y una coca cola gives the components and the cola apart`() = runBlocking {
        val tags = resolve("un sandwich de jamón y queso y una coca cola")
        assertEquals(listOf("pan", "jamón", "queso", "coca cola"), tags.names())
        assertEquals(listOf("gen019", "gen094", "gen047"), tags.take(3).map { it.foodItem?.id })
        assertEquals("the cola is a food of its own: ${tags.last()}", "gen146", tags.last().foodItem?.id)
    }

    @Test
    fun `a juice after the sandwich keeps its tag and its own portion`() = runBlocking {
        val tags = resolve("sandwich de jamón y queso y un jugo")
        assertEquals(listOf("pan", "jamón", "queso", "jugo"), tags.names())
        // the portion of a juice said alone, not the 100 g of a filling
        assertEquals(resolve("un jugo").single().amountGrams ?: 0.0, tags.last().amountGrams ?: 0.0, 0.001)
    }

    @Test
    fun `what is said after the sandwich does not change the sandwich`() = runBlocking {
        val alone = resolve("un sandwich de jamón y queso")
        val withDrink = resolve("un sandwich de jamón y queso y una coca cola")
        assertEquals(alone.names(), withDrink.take(alone.size).names())
        assertEquals(alone.map { it.loggedFood?.calories }, withDrink.take(alone.size).map { it.loggedFood?.calories })
        assertEquals(alone.map { it.amountGrams }, withDrink.take(alone.size).map { it.amountGrams })
    }

    @Test
    fun `what is said before the sandwich keeps its place`() = runBlocking {
        val tags = resolve("tomé un té y un sandwich de jamón y queso")
        assertEquals(listOf("té", "pan", "jamón", "queso"), tags.names())
    }

    @Test
    fun `a generic sandwich de A y B expands inside its mention and the tags around it stay`() = runBlocking {
        val tags = resolve("sandwich de mortadela y queso y un café")
        assertEquals(listOf("pan", "mortadela", "queso", "café"), tags.names())
        assertEquals("gen059", tags.last().foodItem?.id)
    }

    @Test
    fun `a word that holds pan inside another tag no longer blocks the expansion`() = runBlocking {
        // "empanada" holds "pan": only the tags of the mention are asked whether the sandwich already has its bread
        val tags = resolve("sandwich de jamón y queso y una empanada")
        assertEquals(listOf("pan", "jamón", "queso", "empanada"), tags.names())
        assertEquals("cl001", tags.last().foodItem?.id)
    }

    @Test
    fun `un sandwich de ave mayo is one tag, alone or with a drink`() = runBlocking {
        val alone = resolve("un sándwich de ave mayo")
        assertEquals(1, alone.size)
        assertEquals("gen185", alone.single().foodItem?.id)
        val withDrink = resolve("un sandwich de ave mayo y un jugo")
        assertEquals(2, withDrink.size)
        assertEquals("gen185", withDrink.first().foodItem?.id)
        assertTrue("its bread is already in the row: ${withDrink.names()}", withDrink.none { it.tag == "pan" })
    }

    @Test
    fun `an excluded ingredient still keeps the sandwich from expanding`() = runBlocking {
        val tags = resolve("sandwich de jamón y queso sin queso")
        assertTrue(tags.names().toString(), tags.none { it.tag == "pan" })
    }
}
