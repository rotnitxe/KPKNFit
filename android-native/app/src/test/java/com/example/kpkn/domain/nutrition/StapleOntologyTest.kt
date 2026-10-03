package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
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
    fun `arroz con pollo conserva pechuga estimada y pide corte material`() = runBlocking {
        val tags = resolve("arroz con pollo")
        assertEquals(2, tags.size)
        val pollo = tags.single { it.tag.contains("pollo") }
        assertEquals("gen004", pollo.foodItem?.id)
        assertTrue(pollo.needsCutClarification)
    }

    @Test
    fun `papas fritas no usa 350g heuristico`() = runBlocking {
        val tags = resolve("papas fritas")
        assertEquals("gen021f", tags.single().foodItem?.id)
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue("papas fritas $grams g", grams in 80.0..180.0)
    }

    @Test
    fun `quesadilla conserva plato y estimación pendiente`() = runBlocking {
        val tag = resolve("quesadilla").single()
        assertEquals(null, tag.foodItem)
        assertEquals(FoodResolutionStatus.NEEDS_REVIEW, tag.resolutionStatus)
        assertTrue(tag.hasMaterialQuestion())
        assertNotNull(tag.loggedFood)
        assertTrue(tag.loggedFood!!.foodName.contains("quesadilla", ignoreCase = true))
        assertTrue(tag.amountGrams!! in 150.0..350.0)
    }

    @Test
    fun `proteina sola no colapsa a whey`() = runBlocking {
        assertNull(FoodStapleOntology.resolveFoodId("proteína"))
        assertFalse(FOOD_ALIASES.containsKey("proteína"))
    }

    @Test
    fun `whey scoop resuelve suplemento`() = runBlocking {
        val tags = resolve("whey")
        assertEquals("unbranded whey must keep the generic supplement, not an arbitrary brand: ${tags.single()}", "gen105", tags.single().foodItem?.id)
        assertEquals("Genérico", tags.single().foodItem?.brand)
        assertTrue("whey already declares a supplement form", FoodIdentity.matchesDeclaredIdentity("whey", "Proteína en Polvo (Whey)", listOf("whey")))
        assertFalse("plain milk does not declare powder", FoodIdentity.matchesDeclaredIdentity("leche", "Leche en Polvo"))
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue(grams in 25.0..35.0)
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
    fun `carne mantiene corte y pregunta solo ante diferencia material`() = runBlocking {
        val tags = resolve("carne")
        assertEquals("gen093", tags.single().foodItem?.id)
        assertFalse(tags.single().needsCutClarification)
        val larger = resolve("carne grande").single()
        assertEquals("gen093", larger.foodItem?.id)
        assertTrue(larger.amountGrams!! > tags.single().amountGrams!!)
        assertTrue(larger.needsCutClarification)
        assertTrue(larger.stapleCutOptions.any { it.label.contains("Molida", ignoreCase = true) })
        assertTrue(larger.stapleCutOptions.any { it.label.contains("Bistec", ignoreCase = true) })
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

    // ─── WP-N11: the options of a cut question are all in the state the person declared ──────────────────────────

    @Test
    fun `a bare pollo offers cooked cuts only`() {
        val cut = requireNotNull(FoodStapleOntology.cutClarification("pollo"))
        assertEquals(listOf("gen004", "gen003tc", "gen003e"), cut.options.map { it.foodId })
        assertEquals(listOf("Pechuga", "Trutro", "Entero"), cut.options.map { it.label })
        assertEquals("gen004", cut.defaultFoodId)
        for (option in cut.options) {
            val state = FoodIdentity.stateFor(requireNotNull(findStaticFoodById(option.foodId)))
            assertEquals("${option.foodId} is a cooked row", FoodState.COOKED, state)
        }
    }

    @Test
    fun `pollo crudo offers the raw cuts, by the declared state or by the words typed`() {
        val byState = requireNotNull(FoodStapleOntology.cutClarification("pollo", "pollo", null, FoodState.RAW))
        assertEquals(listOf("gen003", "gen003t"), byState.options.map { it.foodId })
        assertEquals(listOf("Pechuga", "Trutro"), byState.options.map { it.label })
        val byWords = requireNotNull(FoodStapleOntology.cutClarification("pollo", "pollo crudo 200 g"))
        assertEquals(listOf("gen003", "gen003t"), byWords.options.map { it.foodId })
        for (option in byWords.options) {
            assertEquals("${option.foodId} is a raw row", FoodState.RAW, FoodIdentity.stateFor(requireNotNull(findStaticFoodById(option.foodId))))
        }
        // a cooked state, declared or typed, keeps the cooked cuts
        val declaredCooked = requireNotNull(FoodStapleOntology.cutClarification("pollo", "pollo", null, FoodState.COOKED))
        assertEquals(listOf("gen004", "gen003tc", "gen003e"), declaredCooked.options.map { it.foodId })
        val typedCooked = requireNotNull(FoodStapleOntology.cutClarification("pollo", "pollo asado"))
        assertEquals(listOf("gen004", "gen003tc", "gen003e"), typedCooked.options.map { it.foodId })
    }

    @Test
    fun `the declared state does not hide a learned cut or a named one`() {
        assertNull(FoodStapleOntology.cutClarification("pollo", "pollo", "gen003t", FoodState.RAW))
        assertNull(FoodStapleOntology.cutClarification("trutro de pollo", "trutro de pollo", null, FoodState.RAW))
        assertNull(FoodStapleOntology.cutClarification("pechuga", "pechuga", null, FoodState.RAW))
    }

    @Test
    fun `beef has cooked cuts only, so a raw beef mention offers none`() {
        assertEquals(listOf("gen093", "gen010"), requireNotNull(FoodStapleOntology.cutClarification("carne")).options.map { it.foodId })
        assertNull(FoodStapleOntology.cutClarification("carne", "carne", null, FoodState.RAW))
    }

    @Test
    fun `the question of the tag carries the cuts of its state`() = runBlocking {
        val bare = resolve("pollo").single()
        assertTrue(bare.needsCutClarification)
        assertEquals(listOf("gen004", "gen003tc", "gen003e"), bare.stapleCutOptions.map { it.foodId })
        val question = requireNotNull(bare.interpretationV2).pendingQuestions.filterIsInstance<ClarificationRequest.Identity>().single { it.requestId == "cut" }
        assertEquals(listOf("gen004", "gen003tc", "gen003e"), question.candidateIds)
        assertEquals(listOf("Pechuga", "Trutro", "Entero"), question.candidateLabels)

        val raw = resolve("pollo crudo").single()
        assertTrue(raw.needsCutClarification)
        assertEquals(listOf("gen003", "gen003t"), raw.stapleCutOptions.map { it.foodId })
        val rawQuestion = requireNotNull(raw.interpretationV2).pendingQuestions.filterIsInstance<ClarificationRequest.Identity>().single { it.requestId == "cut" }
        assertEquals(listOf("gen003", "gen003t"), rawQuestion.candidateIds)

        // an explicit mass answers the portion, not the cut: the declared state picks the row and nothing is asked
        val grams = resolve("pollo crudo 200 g").single()
        assertFalse(grams.needsCutClarification)
        assertEquals("gen003", grams.foodItem?.id)
    }

    @Test
    fun `every option of the question can be applied`() = runBlocking {
        for ((text, ids) in listOf(
            "pollo" to listOf("gen004", "gen003tc", "gen003e"),
            "pollo crudo" to listOf("gen003", "gen003t"),
        )) {
            val tag = resolve(text).single()
            for (id in ids) {
                val applied = NutritionInterpretationBridge.applyCutOption(tag, id)
                assertEquals("$text -> $id", id, applied.foodItem?.id)
                assertFalse("$text -> $id", applied.needsCutClarification)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
}
