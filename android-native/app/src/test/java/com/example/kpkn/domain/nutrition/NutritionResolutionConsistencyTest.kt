package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.MacroOverrides
import com.example.kpkn.data.models.ParsedMealDescription
import com.example.kpkn.data.models.ParsedMealItem
import com.example.kpkn.data.models.AmountIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionResolutionConsistencyTest {

    @Test
    fun `parser protects salsa de tomate but keeps salsa y tomate separate`() {
        val protectedPhrase = parseMealDescription("salsa de tomate")
        val explicitConnector = parseMealDescription("salsa y tomate")

        assertEquals(1, protectedPhrase.items.size)
        assertEquals("salsa de tomate", protectedPhrase.items.single().tag)
        assertEquals(2, explicitConnector.items.size)
        assertEquals(listOf("salsa", "tomate"), explicitConnector.items.map { it.tag })
    }

    @Test
    fun `meal description keeps champinones as its own component`() {
        val parsed = parseMealDescription("arroz con pollo, champiñones y salsa de yogurt")

        assertEquals(
            listOf("arroz", "pollo", "champiñones", "salsa de yogurt"),
            parsed.items.map { it.tag },
        )
    }

    @Test
    fun `parser separates fideos from salsa de tomate but protects the sauce`() {
        val result = parseMealDescription("200g fideos con 80g salsa de tomate")

        assertEquals(2, result.items.size)
        assertEquals("fideos", result.items[0].tag)
        assertEquals(200.0, result.items[0].amountGrams)
        assertEquals("salsa de tomate", result.items[1].tag)
        assertEquals(80.0, result.items[1].amountGrams)
    }

    @Test
    fun `local catalog owns salsa and pasta variants`() {
        val sauce = findFoodExactByNormalized("Salsa de Tomate")
        val dry = findFoodExactByNormalized("fideos secos")
        val cooked = findFoodExactByNormalized("fideos cocidos")

        assertEquals("gen064", sauce?.id)
        assertEquals(32.0, sauce?.calories)
        assertEquals("gen040c", dry?.id)
        assertEquals("gen040h", cooked?.id)
        assertNull(findFoodByNormalized("fideos"))
        assertEquals(FoodState.RAW, FoodIdentity.stateFor("fideos secos"))
        assertEquals(FoodState.COOKED, FoodIdentity.stateFor("fideos cocidos"))
    }

    @Test
    fun `food index includes static aliases without assigning plain fideos a state`() {
        val index = FoodIndex()
        index.build(emptyList(), buildFoodDatabase(), FOOD_ALIASES)

        assertEquals("gen064", index.exactMatches("salsa de tomate").first().foodId)
        assertTrue(index.search("fideos secos").contains("gen040c"))
        assertTrue(index.search("fideos cocidos").contains("gen040h"))
        assertTrue(index.getAllFoods().filter { it.canonicalFamily == "pasta" }.size >= 2)
    }

    @Test
    fun `dry and cooked pasta use their local macros`() {
        val dry = findFoodExactByNormalized("fideos secos")!!
        val cooked = findFoodExactByNormalized("fideos cocidos")!!

        val dryLogged = scaleFoodByPortion(dry, amountGrams = 200.0)
        val cookedLogged = scaleFoodByPortion(cooked, amountGrams = 200.0)

        assertEquals(742.0, dryLogged.calories, 0.01)
        assertEquals(26.0, dryLogged.protein, 0.01)
        assertEquals(262.0, cookedLogged.calories, 0.01)
        assertEquals(10.0, cookedLogged.protein, 0.01)
    }

    @Test
    fun `api salsa and tomate mentions reconcile to local sauce and discard api macros`() {
        val items = listOf(
            ParsedMealItem(
                tag = "Salsa",
                amountGrams = 50.0,
                analysisSource = AnalysisSource.EXTERNAL_API_ESTIMATE,
                macroOverrides = MacroOverrides(calories = 900.0, protein = 0.0, carbs = 0.0, fats = 100.0),
            ),
            ParsedMealItem(
                tag = "Tomate",
                amountGrams = 20.0,
                analysisSource = AnalysisSource.EXTERNAL_API_ESTIMATE,
                macroOverrides = MacroOverrides(calories = 500.0, protein = 0.0, carbs = 0.0, fats = 50.0),
            ),
        )

        val reconciled = reconcileParsedFoodItems(items)

        assertEquals(1, reconciled.size)
        assertEquals("salsa de tomate", reconciled.single().tag)
        assertEquals(70.0, reconciled.single().amountGrams)
        assertNull(reconciled.single().macroOverrides)
        assertEquals(AnalysisSource.EXTERNAL_API_ESTIMATE, reconciled.single().analysisSource)
    }

    @Test
    fun `automatic local resolution does not write learning`() = runBlocking {
        val sauce = findFoodExactByNormalized("salsa de tomate")!!
        val port = RecordingPort(staticFood = sauce, staticExact = true)
        val resolver = TagResolver(port)

        val (tags, _) = resolver.resolveAll(
            ParsedMealDescription(
                items = listOf(
                    ParsedMealItem(
                        tag = "salsa de tomate",
                        amountGrams = 100.0,
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                    ),
                ),
                rawDescription = "100g salsa de tomate",
            ),
        )

        assertEquals(0, port.learnedWrites)
        assertTrue(tags.single().isResolved)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertEquals(32.0, tags.single().loggedFood?.calories)
    }

    @Test
    fun `local exact food ignores unrelated dataset interpretation and macro range`() = runBlocking {
        val mushrooms = findFoodByNormalized("champiñones")!!
        val misleadingRetrieval = SemanticPortionRetriever.RetrievalResult(
            query = "champiñones",
            matches = listOf(
                SemanticPortionRetriever.DatasetMatch(
                    docId = 15549,
                    instruction = "Completo con champiñones salteados",
                    score = 0.482,
                    type = "GENERAL",
                ),
            ),
            contextDetected = emptyList(),
            portionPriors = emptyMap(),
            macroRange = SemanticPortionRetriever.MacroRangeEstimate(
                kcalMin = 93.0,
                kcalMax = 158.0,
                kcalMedian = 117.0,
                proteinMin = 3.0,
                proteinMax = 11.0,
                proteinMedian = 5.0,
                fatsMin = 0.0,
                fatsMax = 5.0,
                fatsMedian = 1.0,
                carbsMin = 15.0,
                carbsMax = 19.0,
                carbsMedian = 18.0,
                sampleCount = 4,
                sourceDocumentIds = listOf(15549),
            ),
            confidence = 0.9,
            elapsedMs = 0,
        )
        val port = RecordingPort(
            staticFood = mushrooms,
            staticExact = false,
            smart = SmartFoodResolver.ResolutionResult(
                query = "champiñones",
                candidates = listOf(
                    SmartFoodResolver.ResolutionCandidate(
                        foodId = mushrooms.id,
                        name = mushrooms.name,
                        brand = mushrooms.brand,
                        score = 1.0,
                        confidence = SmartFoodResolver.Confidence.HIGH,
                        source = "LOCAL",
                        calories = mushrooms.calories,
                        protein = mushrooms.protein,
                        carbs = mushrooms.carbs,
                        fats = mushrooms.fats,
                        fiber = 0.0,
                        trace = listOf("local"),
                    ),
                ),
                decision = SmartFoodResolver.Decision.AUTO_SELECT,
                resolvedFoodId = mushrooms.id,
                semanticRetrieval = misleadingRetrieval,
            ),
        )

        val (tags, _) = TagResolver(port).resolveAll(
            ParsedMealDescription(
                items = listOf(
                    ParsedMealItem(
                        tag = "champiñones",
                        amountGrams = 100.0,
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                    ),
                ),
                rawDescription = "100g champiñones",
            ),
        )

        val tag = tags.single()
        assertEquals("gen038", tag.foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertTrue(tag.isResolved)
        assertEquals(22.0, tag.loggedFood?.calories ?: -1.0, 0.01)
        assertNull(tag.interpretation)
        assertTrue(tag.statusText.isBlank())
        assertFalse(tag.statusText.contains("Entendí", ignoreCase = true))
        assertFalse(tag.statusText.contains("Completo", ignoreCase = true))
        assertFalse(tag.statusText.contains("fuera de rango", ignoreCase = true))
    }

    @Test
    fun `ambiguous fideos asume cocido y expone macros`() = runBlocking {
        val dry = findFoodExactByNormalized("fideos secos")!!
        val candidate = SmartFoodResolver.ResolutionCandidate(
            foodId = dry.id,
            name = dry.name,
            brand = dry.brand,
            score = 0.95,
            confidence = SmartFoodResolver.Confidence.HIGH,
            source = "LOCAL",
            calories = dry.calories,
            protein = dry.protein,
            carbs = dry.carbs,
            fats = dry.fats,
            fiber = 0.0,
            trace = listOf("local"),
            canonicalFamily = "pasta",
            state = FoodState.RAW,
        )
        val port = RecordingPort(
            staticFood = dry,
            staticExact = false,
            smart = SmartFoodResolver.ResolutionResult(
                query = "fideos",
                candidates = listOf(candidate),
                decision = SmartFoodResolver.Decision.AUTO_SELECT,
                resolvedFoodId = dry.id,
                canonicalFamily = "pasta",
                state = FoodState.RAW,
            ),
        )

        val (tags, _) = TagResolver(port).resolveAll(
            ParsedMealDescription(
                items = listOf(ParsedMealItem(tag = "fideos", amountGrams = 200.0)),
                rawDescription = "200g fideos",
            ),
        )

        assertTrue(tags.single().isResolved)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertFalse(tags.single().needsCookingClarification)
        assertNotNull(tags.single().loggedFood)
        assertTrue(tags.single().stateAssumed)
        assertEquals(0, port.learnedWrites)
    }

    @Test
    fun `unknown macro estimate is saveable`() = runBlocking {
        val candidate = SmartFoodResolver.ResolutionCandidate(
            foodId = "dataset_unknown_food",
            name = "Alimento desconocido",
            brand = "Dataset KPKN",
            score = 0.55,
            confidence = SmartFoodResolver.Confidence.MEDIUM,
            source = "DATASET_SEMANTIC",
            calories = 210.0,
            protein = 8.0,
            carbs = 30.0,
            fats = 5.0,
            fiber = 0.0,
            trace = listOf("dataset"),
        )
        val port = RecordingPort(
            staticFood = null,
            staticExact = false,
            smart = SmartFoodResolver.ResolutionResult(
                query = "alimento desconocido",
                candidates = listOf(candidate),
                decision = SmartFoodResolver.Decision.NEEDS_REVIEW,
                resolvedFoodId = candidate.foodId,
            ),
        )

        val (tags, _) = TagResolver(port).resolveAll(
            ParsedMealDescription(
                items = listOf(
                    ParsedMealItem(
                        tag = "alimento desconocido",
                        amountGrams = 100.0,
                        analysisSource = AnalysisSource.EXTERNAL_API_ESTIMATE,
                        macroOverrides = MacroOverrides(calories = 210.0, protein = 8.0, carbs = 30.0, fats = 5.0),
                    ),
                ),
                rawDescription = "100g alimento desconocido",
            ),
        )

        assertTrue(tags.single().isResolved)
        assertEquals(FoodResolutionStatus.NO_RESOLVED, tags.single().resolutionStatus)
        assertNotNull(tags.single().loggedFood)
        assertFalse(tags.single().hasMaterialQuestion())
    }

    @Test
    fun `smart resolver keeps local exact macros over global api-like rows`() = runBlocking {
        val index = FoodIndex()
        index.build(
            globalFoods = listOf(
                com.example.kpkn.data.db.GlobalFoodEntity(
                    foodId = "global-salsa",
                    name = "Salsa de tomate",
                    normalizedName = "salsa de tomate",
                    calories = 900.0,
                    protein = 0.0,
                    carbs = 0.0,
                    fats = 100.0,
                    source = "USDA",
                    sourcePriority = 90,
                ),
            ),
            staticFoods = buildFoodDatabase(),
            staticAliases = FOOD_ALIASES,
        )

        val result = SmartFoodResolver(noOpNutritionDao(), index).resolve("salsa de tomate")

        assertEquals(SmartFoodResolver.Decision.AUTO_SELECT, result.decision)
        assertEquals("gen064", result.resolvedFoodId)
        assertEquals(32.0, result.candidates.first().calories, 0.01)
    }

    @Test
    fun `plain fideos never exposes irrelevant global products`() = runBlocking {
        val index = FoodIndex()
        index.build(
            globalFoods = listOf(
                com.example.kpkn.data.db.GlobalFoodEntity(
                    foodId = "global-noodle-soup",
                    name = "Sopa de fideo chipotle",
                    normalizedName = "sopa de fideo chipotle",
                    calories = 364.0,
                    protein = 8.0,
                    carbs = 50.0,
                    fats = 12.0,
                    source = "USDA",
                ),
            ),
            staticFoods = buildFoodDatabase(),
            staticAliases = FOOD_ALIASES,
        )

        val result = SmartFoodResolver(noOpNutritionDao(), index).resolve("fideos")

        assertEquals(SmartFoodResolver.Decision.NEEDS_REVIEW, result.decision)
        assertTrue(result.candidates.isNotEmpty())
        assertTrue(result.candidates.all { it.source == "LOCAL" })
        assertTrue(result.candidates.any { it.state == FoodState.RAW })
        assertTrue(result.candidates.any { it.state == FoodState.COOKED || it.state == FoodState.HYDRATED })
    }

    @Test
    fun `global candidates with the same canonical name are deduplicated`() = runBlocking {
        val index = FoodIndex()
        index.build(
            globalFoods = listOf(
                com.example.kpkn.data.db.GlobalFoodEntity(
                    foodId = "global-rare-1",
                    name = "Alimento raro",
                    normalizedName = "alimento raro",
                    calories = 100.0,
                    protein = 10.0,
                    carbs = 10.0,
                    fats = 2.0,
                    source = "USDA",
                ),
                com.example.kpkn.data.db.GlobalFoodEntity(
                    foodId = "global-rare-2",
                    name = "Alimento raro",
                    normalizedName = "alimento raro",
                    calories = 105.0,
                    protein = 10.0,
                    carbs = 11.0,
                    fats = 2.0,
                    source = "OFF",
                ),
            ),
            staticFoods = emptyList(),
            staticAliases = emptyMap(),
        )

        val result = SmartFoodResolver(noOpNutritionDao(), index).resolve("alimento raro")

        assertEquals(1, result.candidates.size)
        assertEquals("alimento raro", FoodIdentity.normalize(result.candidates.single().name))
    }

    @Test
    fun `A1 approximation alias torta autoconfirms generic bread`() = runBlocking {
        val panBlanco = findFoodExactByNormalized("pan blanco")
        assertNotNull(panBlanco)
        val port = RecordingPort(staticFood = panBlanco, staticExact = true)

        val (tags, _) = TagResolver(port).resolveAll(
            ParsedMealDescription(
                items = listOf(
                    ParsedMealItem(
                        tag = "torta",
                        amountGrams = 100.0,
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                    ),
                ),
                rawDescription = "100g torta",
            ),
        )

        val tag = tags.single()
        assertTrue("la aproximación cotidiana se auto-guarda", tag.isResolved)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertNotNull(tag.foodItem)
        assertFalse(tag.hasMaterialQuestion())
    }

    @Test
    fun `A1 papas fritas resuelve a papa frita con alias exacto`() {
        val cookedPotato = findFoodExactByNormalized("papas fritas")
        val normalizedForm = findFoodExactByNormalized("papa fritas")
        assertNotNull("papas fritas debe resolver a Papa (frita)", cookedPotato)
        assertNotNull("la forma normalizada 'papa fritas' también debe resolver", normalizedForm)
        assertEquals("gen021f", cookedPotato?.id)
        assertEquals("gen021f", normalizedForm?.id)
    }

    @Test
    fun `A1 manjar resuelve a manjar dulce de leche y no a miel`() {
        val manjar = findFoodExactByNormalized("manjar")
        val dulceDeLeche = findFoodExactByNormalized("dulce de leche")
        assertNotNull(manjar)
        assertNotNull(dulceDeLeche)
        assertEquals("gen109", manjar?.id)
        assertEquals("gen109", dulceDeLeche?.id)
        assertTrue("no debe ser miel", manjar?.name?.contains("Manjar") == true)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao {
        return java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
    }
    private class RecordingPort(
        private val staticFood: FoodItem?,
        private val staticExact: Boolean,
        private val smart: SmartFoodResolver.ResolutionResult = SmartFoodResolver.ResolutionResult(
            query = "",
            candidates = emptyList(),
            decision = SmartFoodResolver.Decision.UNRESOLVED,
            resolvedFoodId = null,
        ),
    ) : FoodResolutionPort {
        var learnedWrites: Int = 0

        override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
            smart.copy(query = tag)

        override suspend fun getFoodById(id: String): FoodItem? =
            staticFood?.takeIf { it.id == id }

        override suspend fun staticFood(tag: String): FoodItem? = staticFood

        override fun staticIsExact(tag: String): Boolean = staticExact

        override fun recordLearned(
            query: String,
            brandHint: String?,
            foodId: String,
            portionGrams: Double?,
            cookingMethod: String?,
        ) {
            learnedWrites++
        }
    }
}
