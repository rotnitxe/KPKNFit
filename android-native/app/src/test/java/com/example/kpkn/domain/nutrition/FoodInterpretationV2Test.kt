package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The V2 interpretation of a resolved mention. WP-N7 retired the mini-pipeline of the engine that parsed a text, asked its own
 * questions and answered them (only these tests used it): the logger resolves the mentions and
 * [FoodInterpretationV2Engine.interpretResolved] describes each one, so the tests start from a resolved tag.
 */
class FoodInterpretationV2Test {
    private val engine = FoodInterpretationV2Engine()

    private val staticFoods = buildFoodDatabase()
    private val port: FoodResolutionPort by lazy {
        val dao = java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao
        val index = FoodIndex().apply { build(emptyList(), staticFoods, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(dao, index, null)
        object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = staticFoods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    /** The mentions of [text], resolved over the static catalog as the logger resolves them. */
    private fun resolve(text: String): List<ResolvedTag> =
        runBlocking { TagResolver(port).resolveAll(parseMealDescription(text)).first }

    @Test
    fun `explicit cooked chicken keeps its cooked row and is not converted again`() {
        val result = engine.interpretResolved(resolve("200 g pechuga de pollo cocida").single())

        assertEquals("gen004", result.selectedCandidateId)
        assertEquals(WeightBasis.COOKED, result.weightBasis)
        assertEquals(200.0, result.observedGrams ?: Double.NaN, 0.0)
        assertEquals(64.2, result.proteinGrams, 0.01)
        assertEquals(64.2, result.proteinMinGrams, 0.01)
        assertTrue(result.transformations.any { it.startsWith("preparation") })
        assertTrue(result.transformations.none { it.startsWith("weight_basis:") })
        assertTrue(result.canFinalize())
    }

    @Test
    fun `a vague portion keeps a range around the central value and stays an estimate`() {
        val result = engine.interpretResolved(resolve("arroz cocido").single())
        val grams = result.observedGrams ?: Double.NaN

        assertTrue(result.isUncertain)
        assertFalse(result.isConfirmedEstimate)
        assertEquals(0.55, result.portionConfidence, 0.0)
        assertTrue((result.portionMinGrams ?: Double.NaN) < grams)
        assertTrue(grams < (result.portionMaxGrams ?: Double.NaN))
        assertTrue(result.caloriesMin < result.calories)
        assertTrue(result.calories < result.caloriesMax)
        assertEquals("habitual_estimate", result.stageEvidence.single { it.stage == InterpretationStage.PORTION }.status)
    }

    @Test
    fun `an explicit mass has no range and the same tag always gives the same result`() {
        val tag = resolve("180 g pechuga de pollo cocida").single()
        val first = engine.interpretResolved(tag)

        assertEquals(first, engine.interpretResolved(tag))
        assertEquals(180.0, first.observedGrams ?: Double.NaN, 0.0)
        assertEquals(180.0, first.portionMinGrams ?: Double.NaN, 0.0)
        assertEquals(180.0, first.portionMaxGrams ?: Double.NaN, 0.0)
        assertEquals(1.0, first.portionConfidence, 0.0)
        assertEquals("declared", first.stageEvidence.single { it.stage == InterpretationStage.PORTION }.status)
    }

    @Test
    fun `the questions of a mention are carried and keep its result from finalizing`() {
        val tag = resolve("200 g arroz").single()
        val question = ClarificationRequest.WeightState("weight_state")

        val asked = engine.interpretResolved(tag, listOf(question))

        assertEquals(listOf<ClarificationRequest>(question), asked.pendingQuestions)
        assertTrue(asked.isUncertain)
        assertFalse(asked.canFinalize())
        assertTrue(engine.interpretResolved(tag).canFinalize())
    }

    // ─── WP-N10b: the name of a converted row keeps its dish ────────────────────────────────────────

    @Test
    fun `conversion stem strips state words only as qualifiers and never the first word`() {
        val cases = listOf(
            "Asado de Tira (crudo)" to "Asado de Tira",
            "Papas Fritas" to "Papas",
            "Huevo Entero (frito)" to "Huevo Entero",
            "Pechuga de Pollo (cruda)" to "Pechuga de Pollo",
            "Arroz (hidratado/cocido)" to "Arroz",
            "Pasta (hidratada/cocida)" to "Pasta",
            "Soya texturizada (seca)" to "Soya texturizada",
            "Posta Rosada (cocida)" to "Posta Rosada",
            "Pavo (pechuga cocida)" to "Pavo (pechuga)",
            "Jamón Cocido" to "Jamón",
            "Pollo al asado" to "Pollo",
            "Papas fritas (snack)" to "Papas (snack)",
            "Pechuga de pollo (cruda, sin piel)" to "Pechuga de pollo (sin piel)",
            "Lomo liso de vacuno magro crudo (FDC 334849)" to "Lomo liso de vacuno magro (FDC 334849)",
            // The first word is the dish: it stays even when it is a state word.
            "Asado alemán" to "Asado alemán",
            "Frito Lay Clásicas" to "Frito Lay Clásicas",
            "Asado" to "Asado",
            "Fritos" to "Fritos",
            // A word inside the name, or a stem inside a word, is not a qualifier.
            "Pan Amasado" to "Pan Amasado",
            "Pollo asado con papas" to "Pollo asado con papas",
        )
        for ((name, stem) in cases) assertEquals(name, stem, conversionStem(name))
    }

    private fun converted(food: FoodItem, to: FoodState, method: CookingMethod) = ResolvedTag(
        tag = food.name, foodItem = food, cookingMethod = method, foodState = to, amountGrams = 100.0,
        stateConversion = "weight_basis:test",
        loggedFood = LoggedFood(foodName = food.name, amount = 100.0, calories = 300.0, protein = 20.0, fats = 20.0),
    )

    @Test
    fun `the interpretation of a converted row is named after its dish`() {
        val cut = requireNotNull(findStaticFoodById("gen093c"))
        assertEquals("Asado de Tira (crudo)", cut.name)
        // The raw cut asked as a roast: "de Tira (cocido, estimado)" lost the first word of the name.
        val roast = engine.interpretResolved(converted(cut, FoodState.COOKED, CookingMethod.ASADO_PARRILLA))
        assertEquals("Asado de Tira (cocido, estimado)", roast.canonicalIdentity)
        assertTrue(roast.transformations.any { it.startsWith("weight_basis:RAW->COOKED;yield=0.7;source=gen093c") })
        // The other direction, and the names of the probe: a cooked row asked raw.
        for ((name, stem) in listOf("Papas Fritas" to "Papas", "Huevo Entero (frito)" to "Huevo Entero", "Arroz Blanco (cocido)" to "Arroz Blanco")) {
            val raw = engine.interpretResolved(converted(FoodItem(name = name), FoodState.RAW, CookingMethod.CRUDO))
            assertEquals(name, "$stem (crudo, estimado)", raw.canonicalIdentity)
        }
        val chicken = engine.interpretResolved(converted(FoodItem(name = "Pechuga de Pollo (cruda)"), FoodState.COOKED, CookingMethod.COCIDO))
        assertEquals("Pechuga de Pollo (cocido, estimado)", chicken.canonicalIdentity)
    }
}
