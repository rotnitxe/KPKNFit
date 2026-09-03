package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.MealType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StapleOntologyTest {

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

    private suspend fun resolve(description: String): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = FOOD_ALIASES)
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val parsed = parseMealDescription(description)
        return TagResolver(RealPort(resolver, foods)).resolveAll(parsed).first
    }

    @Test
    fun `pollo sin corte pide aclaracion material`() = runBlocking {
        val tags = resolve("pollo")
        assertEquals(1, tags.size)
        assertTrue(tags.single().needsCutClarification)
        assertTrue(tags.single().stapleCutOptions.size >= 2)
    }

    @Test
    fun `trutro resuelve sin colapsar a pechuga`() = runBlocking {
        val tags = resolve("200g trutro de pollo")
        assertEquals("gen003t", tags.single().foodItem?.id)
        assertFalse(tags.single().needsCutClarification)
    }

    @Test
    fun `arroz con pollo mantiene pechuga por defecto sin chip`() = runBlocking {
        val tags = resolve("arroz con pollo")
        assertEquals(2, tags.size)
        val pollo = tags.single { it.tag.contains("pollo") }
        assertEquals("gen004", pollo.foodItem?.id)
        assertFalse(pollo.needsCutClarification)
    }

    @Test
    fun `papas fritas no usa 350g heuristico`() = runBlocking {
        val tags = resolve("papas fritas")
        assertEquals("gen021f", tags.single().foodItem?.id)
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue("papas fritas $grams g", grams in 80.0..180.0)
    }

    @Test
    fun `quesadilla usa lonja de queso no 100g`() = runBlocking {
        val tags = resolve("quesadilla")
        assertEquals("gen047", tags.single().foodItem?.id)
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue("quesadilla $grams g", grams in 25.0..40.0)
    }

    @Test
    fun `proteina sola no colapsa a whey`() = runBlocking {
        assertNull(FoodStapleOntology.resolveFoodId("proteína"))
        assertFalse(FOOD_ALIASES.containsKey("proteína"))
    }

    @Test
    fun `whey scoop resuelve suplemento`() = runBlocking {
        val tags = resolve("whey")
        assertEquals("gen105", tags.single().foodItem?.id)
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue(grams in 25.0..35.0)
    }

    @Test
    fun `dataset no pisa gramos anclados de staple`() {
        val hint = SemanticPortionRetriever.getGramsForFood("arroz", null)
        assertNull(hint)
    }

    @Test
    fun `bridge aplica corte elegido`() {
        val tag = ResolvedTag(
            tag = "pollo",
            foodItem = buildFoodDatabase().first { it.id == "gen004" },
            stapleCutOptions = listOf(
                FoodStapleOntology.StapleCutOption("Pechuga", "gen004", 166.0),
                FoodStapleOntology.StapleCutOption("Trutro", "gen003t", 177.0),
            ),
            needsCutClarification = true,
        )
        val updated = NutritionInterpretationBridge.applyCutOption(tag, "gen003t")
        assertEquals("gen003t", updated.foodItem?.id)
        assertFalse(updated.needsCutClarification)
        assertTrue(updated.isResolved)
        assertEquals(120.0, updated.amountGrams ?: 0.0, 0.01)
    }

    @Test
    fun `100g de arroz sin estado pregunta seco vs cocido`() = runBlocking {
        val tags = resolve("200 g arroz")
        assertEquals(1, tags.size)
        assertTrue(tags.single().needsCookingClarification)
        assertEquals(CookingStateResolver.ClarificationKind.DRY_VS_COOKED, tags.single().clarificationKind)
        assertTrue(tags.single().hasMaterialQuestion())
        assertFalse(tags.single().needsCutClarification)
    }

    @Test
    fun `arroz a secas asume cocido sin pregunta`() = runBlocking {
        val tags = resolve("arroz")
        assertEquals("gen005", tags.single().foodItem?.id)
        assertFalse(tags.single().needsCookingClarification)
        assertFalse(tags.single().hasMaterialQuestion())
    }

    @Test
    fun `carne pide corte y no colapsa a molida`() = runBlocking {
        val tags = resolve("carne")
        assertEquals("gen093", tags.single().foodItem?.id)
        assertTrue(tags.single().needsCutClarification)
        assertTrue(tags.single().stapleCutOptions.any { it.label.contains("Molida", ignoreCase = true) })
        assertTrue(tags.single().stapleCutOptions.any { it.label.contains("Bistec", ignoreCase = true) })
    }

    @Test
    fun `merluza choclo y pan integral son nodos de ontologia`() {
        assertEquals("gen044", FoodStapleOntology.resolveFoodId("merluza"))
        assertEquals("gen071", FoodStapleOntology.resolveFoodId("choclo"))
        assertEquals("gen133", FoodStapleOntology.resolveFoodId("pan integral"))
        assertEquals(50.0, FoodStapleOntology.householdDefaultGrams("pan integral")!!, 0.01)
    }

    @Test
    fun `pollo chips incluyen entero no solo ala`() {
        val cut = FoodStapleOntology.cutClarification("pollo")
        assertNotNull(cut)
        val labels = cut!!.options.map { it.label }
        assertTrue(labels.contains("Pechuga"))
        assertTrue(labels.contains("Trutro"))
        assertTrue(labels.contains("Entero"))
    }

    @Test
    fun `aprendizaje de corte suprime el chip`() = runBlocking {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = FOOD_ALIASES)
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        resolver.recordLearned("pollo", null, "gen003t", 120.0, null)
        val parsed = parseMealDescription("pollo")
        val tags = TagResolver(RealPort(resolver, foods)).resolveAll(parsed).first
        assertEquals("gen003t", tags.single().foodItem?.id)
        assertFalse(tags.single().needsCutClarification)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
}
