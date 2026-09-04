package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.NutritionCalibrationProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageBindingCorpusTest {

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

    private suspend fun resolve(
        description: String,
        profile: NutritionCalibrationProfile? = null,
    ): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = FOOD_ALIASES)
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val parsed = parseMealDescription(description)
        val (tags, _) = TagResolver(RealPort(resolver, foods), profile).resolveAll(parsed)
        return tags
    }

    @Test
    fun mixPolloArrozLaminas_keepsPlateGramsAndLockedCheese() = runBlocking {
        val tags = resolve("pollo a la plancha, arroz cocido y láminas de queso gouda")
        assertEquals(3, tags.size)
        val cheese = tags.single { FoodIdentity.normalize(it.tag + " " + it.foodQuery).contains("queso") }
        val rice = tags.single { it.tag.contains("arroz", ignoreCase = true) }
        val chicken = tags.single { it.tag.contains("pollo", ignoreCase = true) }
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, cheese.amountIntent)
        val cheeseG = cheese.amountGrams ?: 0.0
        assertTrue("queso locked $cheeseG", cheeseG in 35.0..50.0)
        val riceG = rice.amountGrams ?: 0.0
        val chickenG = chicken.amountGrams ?: 0.0
        assertTrue("arroz plato $riceG", riceG in 180.0..280.0)
        assertTrue("pollo plato $chickenG", chickenG in 120.0..180.0)
        assertFalse(cheese.foodQuery.contains("lamina"))
        assertTrue(cheese.reviewCandidates.isNotEmpty() || cheese.foodItem != null)
    }

    @Test
    fun twoTajadasDeJamonYQueso_cheeseDoesNotInheritSlices() = runBlocking {
        val parsed = parseMealDescription("2 tajadas de jamón y queso")
        assertTrue(parsed.items.size >= 2)
        val ham = parsed.items.first { FoodIdentity.normalize(it.tag).contains("jamon") }
        val cheese = parsed.items.first { FoodIdentity.normalize(it.effectiveFoodQuery()).contains("queso") }
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, ham.amountIntent)
        val hamG = ham.amountGrams ?: 0.0
        assertTrue("jamón locked $hamG", hamG in 28.0..45.0)
        assertNotEquals(AmountIntent.RESOLVED_SUBJECTIVE, cheese.amountIntent)
        val tags = resolve("2 tajadas de jamón y queso")
        val cheeseTag = tags.first { FoodIdentity.normalize(it.tag + " " + it.foodQuery).contains("queso") }
        val cheeseG = cheeseTag.amountGrams ?: 0.0
        assertTrue("queso casa $cheeseG not 350 and not 2 tajadas", cheeseG in 20.0..50.0)
        assertTrue(cheeseG < 150.0)
    }

    @Test
    fun galletasDeChocolate_oneItemNotADrink() = runBlocking {
        val parsed = parseMealDescription("galletas de chocolate")
        assertEquals(1, parsed.items.size)
        val tags = resolve("galletas de chocolate")
        assertEquals(1, tags.size)
        val food = tags.single().foodItem
        assertFalse(
            "must not be a beverage, was ${food?.id} ${food?.name} ${food?.unit}",
            food?.unit.equals("ml", ignoreCase = true) == true ||
                food?.name?.contains("jugo", ignoreCase = true) == true ||
                food?.category?.contains("bebida", ignoreCase = true) == true,
        )
        assertNotEquals(InferredMealContext.Shape.BEVERAGE, InferredMealContext.inferShape("galletas de chocolate"))
    }

    @Test
    fun platoDeArrozConPolloYChoclo_threeMentionsOneVessel() {
        val parsed = parseMealDescription("un plato de arroz con pollo y choclo")
        assertEquals(3, parsed.items.size)
        assertTrue(parsed.items.all { it.containerScope == "plato" })
        val coffee = parseMealDescription("café con leche")
        assertEquals(1, coffee.items.size)
        val riceMilk = parseMealDescription("arroz con leche")
        assertEquals(1, riceMilk.items.size)
    }

    @Test
    fun identityMapping_isReadOnResolve() = runBlocking {
        val profile = NutritionCalibrationProfile(
            identityMappings = mapOf("queso gouda" to "gen047"),
        )
        val tags = resolve("láminas de queso gouda", profile)
        assertEquals("gen047", tags.single().foodItem?.id)
    }

    @Test
    fun torrijaAndTorrada_areLexiconTerms() {
        val torrija = SubjectivePortionLexicon.resolve("una torrija de pan")
        assertTrue("torrija $torrija", torrija != null && torrija.grams in 20.0..80.0)
        val torrada = SubjectivePortionLexicon.resolve("2 torradas de pan")
        assertTrue("torrada $torrada", torrada != null && torrada.grams in 40.0..120.0)
    }

    @Test
    fun papasFritas_staysOneFriedPotato() = runBlocking {
        val parsed = parseMealDescription("papas fritas")
        assertEquals(
            "parser items=${parsed.items.map { "${it.tag}/${it.cookingMethod}/${it.foodQuery}" }}",
            1,
            parsed.items.size,
        )
        val tags = resolve("papas fritas")
        assertEquals(
            "resolved=${tags.map { "${it.tag}:${it.foodItem?.id}:${it.foodItem?.name}" }}",
            1,
            tags.size,
        )
        assertEquals("gen021f", tags.single().foodItem?.id)
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue("papas fritas grams $grams", grams in 80.0..180.0)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
}
