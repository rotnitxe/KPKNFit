package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Hand-authored holdout examples. Expected identities are IDs, never the engine's families. */
class IndependentNutritionCorpusTest {
    private data class Example(
        val text: String,
        val ids: List<String?>,
        val grams: List<ClosedFloatingPointRange<Double>?> = ids.map { null },
        val kcal: List<ClosedFloatingPointRange<Double>?> = ids.map { null },
        val exactKcal: List<Double?> = ids.map { null },
        val clarification: Boolean = false,
        val clarifiedMentions: Set<Int> = if (clarification) ids.indices.toSet() else emptySet(),
        val unresolvedQueries: List<String?> = ids.map { null },
        val excludedIngredients: List<Set<String>> = ids.map { emptySet() },
    )

    private val examples = listOf(
        Example("media taza de leche", listOf("gen016"), listOf(120.0..127.0), listOf(70.0..77.0), exactKcal = listOf(123.6 * 61.0 / 100.0)),
        Example("una taza de leche", listOf("gen016"), listOf(240.0..255.0), listOf(140.0..152.0), exactKcal = listOf(247.2 * 61.0 / 100.0)),
        Example("dos tazas de leche", listOf("gen016"), listOf(480.0..510.0), listOf(280.0..304.0)),
        Example("una rebanada de pan", listOf("gen019"), listOf(20.0..40.0), listOf(50.0..110.0)),
        Example("dos rebanadas de pan", listOf("gen019"), listOf(40.0..80.0), listOf(100.0..220.0)),
        Example("dos rodajas de manzana", listOf("gen001"), listOf(20.0..70.0), listOf(10.0..37.0)),
        Example("100 g pan integral", listOf("gen133"), listOf(100.0..100.0), listOf(250.0..280.0), exactKcal = listOf(265.0)),
        Example("pechuga de pavo", listOf("gen045"), listOf(100.0..220.0)),
        Example("0,5 kg arroz cocido", listOf("gen005"), listOf(500.0..500.0), listOf(640.0..660.0), exactKcal = listOf(5.0 * 130.0)),
        Example("pollo frito y pollo cocido", listOf("gen003f", "gen004"), listOf(100.0..220.0, 100.0..220.0), clarification = true),
        Example("manzana y una taza de leche", listOf("gen001", "gen016"), listOf(100.0..220.0, 240.0..255.0)),
        Example("yogurt sin lactosa", listOf(null), listOf(100.0..250.0), clarification = true, unresolvedQueries = listOf("yogurt sin lactosa")),
        Example("pasta de maní", listOf("gen034"), listOf(8.0..40.0)),
        Example("completo italiano sin mayonesa", listOf(null), listOf(180.0..350.0), clarification = true, unresolvedQueries = listOf("completo italiano"), excludedIngredients = listOf(setOf("mayonesa"))),
        Example("sándwich de jamón y queso sin queso", listOf(null), listOf(180.0..350.0), clarification = true, unresolvedQueries = listOf("sándwich de jamón y queso"), excludedIngredients = listOf(setOf("queso"))),
        // Reserved descriptions combine ingredients and constraints independently of the audit examples.
        Example("150 g lentejas cocidas y 80 g zanahoria cruda", listOf("gen012", "gen024"),
            listOf(150.0..150.0, 80.0..80.0), listOf(170.0..180.0, 30.0..35.0)),
        Example("100 g tofu y 150 g quinoa cocida", listOf("gen041", "gen030"),
            listOf(100.0..100.0, 150.0..150.0), listOf(70.0..80.0, 175.0..185.0)),
        Example("100 g brócoli cocido y 30 g nueces", listOf("gen022", "gen039"),
            listOf(100.0..100.0, 30.0..30.0), listOf(30.0..40.0, 190.0..200.0)),
        Example("240 ml leche descremada", listOf("gen046"), listOf(240.0..255.0), listOf(78.0..86.0)),
        Example("100 g leche sin gluten", listOf(null), listOf(100.0..100.0), clarification = true, unresolvedQueries = listOf("leche sin gluten")),
        Example("100 g leche de marca inexistente", listOf(null), listOf(100.0..100.0), clarification = true, unresolvedQueries = listOf("leche de marca inexistente")),
        Example("100 g merluza cocida y 50 g palta", listOf("gen044", "gen014"),
            listOf(100.0..100.0, 50.0..50.0), listOf(90.0..98.0, 75.0..85.0)),
        Example("20 g crema de maní y una rebanada de pan", listOf("gen034", "gen019"),
            listOf(20.0..20.0, 20.0..40.0), listOf(110.0..125.0, 50.0..110.0)),
    )

    private suspend fun resolve(text: String, calibration: NutritionCalibrationProfile? = null, globals: List<GlobalFoodEntity> = emptyList()): List<ResolvedTag> {
        val staticFoods = buildFoodDatabase()
        val foods = staticFoods + globals.map { it.toFoodItem() }
        val index = FoodIndex().apply { build(globals, staticFoods, FOOD_ALIASES) }
        val dao = java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader, arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao
        val resolver = SmartFoodResolver(dao, index)
        val port = object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String) = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String) = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
        val parsed = parseMealDescription(text)
        return TagResolver(port, calibration).resolveAll(parsed).first
            .map { NutritionInterpretationBridge.enrich(it, parsed) }.filterNot { it.isExcluded }
    }

    @Test fun `independent corpus measures identity coverage quantity nutrition and clarification separately`() = runBlocking {
        val rows = examples.map { example ->
            val tags = resolve(example.text)
            val actualIds = tags.map { it.foodItem?.id }
            val matches = example.ids.indices.map { i ->
                val actual = tags.getOrNull(i)
                actual != null && if (example.ids[i] != null) actual.foodItem?.id == example.ids[i] else {
                    actual.foodItem == null && example.unresolvedQueries.getOrNull(i)?.let { expected ->
                        FoodIdentity.normalize(actual.foodQuery) == FoodIdentity.normalize(expected) &&
                            actual.excludedIngredients == example.excludedIngredients[i]
                    } == true
                }
            }
            val quantityOk = tags.size == example.ids.size && tags.indices.all { i -> example.grams[i]?.let { range -> tags[i].amountGrams?.let { it in range } ?: false } ?: true }
            val nutritionOk = tags.size == example.ids.size && tags.indices.all { i -> example.kcal[i]?.let { range -> tags[i].loggedFood?.calories?.let { it in range } ?: false } ?: true }
            val questionedMentions = tags.indices.filter { i -> tags[i].interpretationV2?.pendingQuestions?.isNotEmpty() == true }.toSet()
            buildJsonObject {
                put("input", example.text)
                put("expectedIds", JsonArray(example.ids.map { it?.let(::JsonPrimitive) ?: JsonNull }))
                put("expectedClarifiedMentions", JsonArray(example.clarifiedMentions.map(::JsonPrimitive)))
                put("identity", matches.all { it } && tags.size == example.ids.size)
                put("coverage", matches.all { it } && tags.size == example.ids.size)
                put("omissions", matches.count { !it })
                put("additions", matches.indices.count { !matches[it] && it < tags.size } + (tags.size - example.ids.size).coerceAtLeast(0))
                put("quantity", quantityOk)
                put("nutrition", if (example.kcal.any { it != null }) JsonPrimitive(nutritionOk) else JsonNull)
                put("clarification", questionedMentions == example.clarifiedMentions)
                put("actual", buildJsonArray { tags.forEachIndexed { i, tag -> add(buildJsonObject {
                    put("id", tag.foodItem?.id?.let(::JsonPrimitive) ?: JsonNull)
                    put("query", tag.foodQuery)
                    put("excluded", JsonArray(tag.excludedIngredients.map(::JsonPrimitive)))
                    put("grams", tag.amountGrams?.let(::JsonPrimitive) ?: JsonNull)
                    put("kcal", tag.loggedFood?.calories?.let(::JsonPrimitive) ?: JsonNull)
                    val exactKcal = example.exactKcal.getOrNull(i)
                    put("arithmeticReference", if (exactKcal == null) JsonNull else buildJsonObject {
                        put("expectedKcal", exactKcal)
                        put("basis", "Documented per-100-g profile scaled by independently specified mass; output rounds kcal to integers.")
                        val error = tag.loggedFood?.calories?.let { kotlin.math.abs(it - exactKcal) }
                        put("absoluteErrorKcal", error?.let(::JsonPrimitive) ?: JsonNull)
                        put("relativeError", error?.let { JsonPrimitive(it / exactKcal) } ?: JsonNull)
                    })
                    val reference = example.kcal.getOrNull(i)
                    put("nutritionReference", if (reference == null) JsonNull else buildJsonObject {
                        put("kind", if (reference.start == reference.endInclusive) "exact" else "interval")
                        put("kcalMin", reference.start)
                        put("kcalMax", reference.endInclusive)
                        val actual = tag.loggedFood?.calories
                        put("insideRange", actual != null && actual in reference)
                        put("distanceOutsideRangeKcal", actual?.let { maxOf(reference.start - it, 0.0, it - reference.endInclusive) }?.let(::JsonPrimitive) ?: JsonNull)
                        if (reference.start == reference.endInclusive && actual != null) {
                            val error = kotlin.math.abs(actual - reference.start)
                            put("absoluteErrorKcal", error)
                            put("relativeError", if (reference.start > 0.0) JsonPrimitive(error / reference.start) else JsonNull)
                        }
                    })
                    put("status", tag.resolutionStatus.name)
                    put("source", tag.nutritionSource.name)
                    put("questions", JsonArray(tag.interpretationV2?.pendingQuestions.orEmpty().map { JsonPrimitive(it.requestId) }))
                }) } })
            }
        }
        val dimensions = listOf("identity", "coverage", "quantity", "nutrition", "clarification")
        val report = buildJsonObject {
            put("corpus", "independent-nutrition-v2")
            put("evidence", "Hand-authored identities and declared catalog basis; quantity ranges and physical equivalences, never DatasetKnowledge labels.")
            put("count", examples.size)
            put("metrics", buildJsonObject { dimensions.forEach { key ->
                put(key, buildJsonObject {
                    put("passed", rows.count { it[key]?.jsonPrimitive?.booleanOrNull == true })
                    put("total", rows.count { it[key] != JsonNull })
                    put("notEvaluated", rows.count { it[key] == JsonNull })
                })
            } })
            put("nutrientError", buildJsonObject {
                val errors = rows.flatMap { it["actual"]!!.jsonArray }.mapNotNull { food ->
                    food.jsonObject["arithmeticReference"]?.takeUnless { it == JsonNull }?.jsonObject
                        ?.get("absoluteErrorKcal")?.jsonPrimitive?.doubleOrNull
                }
                put("exactReferenceCount", errors.size)
                put("meanAbsoluteErrorKcal", if (errors.isEmpty()) JsonNull else JsonPrimitive(errors.average()))
                put("maxAbsoluteErrorKcal", errors.maxOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                put("note", "Errors measure source scaling and output rounding for the four documented controls; they do not measure real-world portion error. Interval references remain separate containment checks.")
                assertEquals("four independently documented arithmetic references", 4, errors.size)
                assertTrue("arithmetic error must fit integer kcal rounding: $errors", errors.all { it <= 0.5 })
            })
            put("cases", JsonArray(rows))
        }
        File("build/reports/nutrition-reliability/independent-corpus.json").apply { parentFile.mkdirs(); writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report)) }
        val failures = rows.filter { row -> dimensions.any { row[it]?.jsonPrimitive?.booleanOrNull == false } }
        assertTrue("Independent corpus failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test fun `equivalent volume mentions retain identical mass and nutrients`() = runBlocking {
        val cup = resolve("una taza de leche").single()
        val ml = resolve("240 ml leche").single()
        val litres = resolve("0,24 litros leche").single()
        assertEquals("gen016", cup.foodItem?.id)
        for (equivalent in listOf(ml, litres)) {
            assertEquals(cup.foodItem?.id, equivalent.foodItem?.id)
            assertEquals(cup.amountGrams!!, equivalent.amountGrams!!, 0.01)
            assertEquals(cup.loggedFood!!.calories, equivalent.loggedFood!!.calories, 1.0)
            assertEquals("g", equivalent.loggedFood!!.unit)
        }
        val half = resolve("media taza de leche").single()
        val twice = resolve("dos tazas de leche").single()
        assertEquals(cup.amountGrams!! / 2.0, half.amountGrams!!, 0.01)
        assertEquals(cup.amountGrams!! * 2.0, twice.amountGrams!!, 0.01)
    }

    @Test fun `size belongs to one item and explicit mass takes precedence`() = runBlocking {
        val normal = resolve("ensalada y arroz cocido")
        val larger = resolve("ensalada grande y arroz cocido")
        assertEquals(2, normal.size)
        assertEquals(normal.map { it.foodItem?.id }, larger.map { it.foodItem?.id })
        assertTrue("large salad must exceed normal: normal=${normal[0]}, large=${larger[0]}", larger[0].amountGrams!! > normal[0].amountGrams!!)
        assertEquals("only the salad scales once", normal[0].amountGrams!! * PORTION_MULTIPLIERS[larger[0].portion]!!, larger[0].amountGrams!!, 0.01)
        assertEquals("salad estimate nutrients scale with the same mass", normal[0].loggedFood!!.calories * PORTION_MULTIPLIERS[larger[0].portion]!!, larger[0].loggedFood!!.calories, 0.01)
        assertEquals(normal[0].amountGrams!!, larger[0].baseAmountGrams!!, 0.01)
        assertEquals(normal[1].amountGrams!!, larger[1].amountGrams!!, 0.01)
        assertEquals(150.0, resolve("150 g ensalada grande").single().amountGrams!!, 0.01)
    }

    @Test fun `confirmed habits apply only to exact food and yield to explicit mass`() = runBlocking {
        val calibration = NutritionCalibrationProfile(maturePortionsGrams = mapOf("gen005" to 210.0))
        assertEquals(210.0, resolve("arroz cocido", calibration).single().amountGrams!!, 0.01)
        assertEquals(90.0, resolve("90 g arroz cocido", calibration).single().amountGrams!!, 0.01)
        assertNotEquals(210.0, resolve("arroz integral cocido", calibration).single().amountGrams)
        val stateCalibration = NutritionCalibrationProfile(statePreferences = mapOf("arroz" to "RAW"))
        assertEquals(FoodState.RAW, resolve("arroz", stateCalibration).single().foodState)
        assertEquals(FoodState.COOKED, resolve("arroz cocido", stateCalibration).single().foodState)
    }

    @Test fun `excluded oil stays in its mention and never reappears in a full recipe`() = runBlocking {
        assertEquals(0.0, oilGramsForLevel("sin aceite"), 0.0)
        val tags = resolve("pechuga de pollo frita sin aceite y papas fritas")
        assertEquals(2, tags.size)
        assertEquals("sin aceite", tags[0].oilLevel)
        assertNotEquals("sin aceite", tags[1].oilLevel)
        assertEquals(0.0, tags[0].appliedOilGrams ?: 0.0, 0.0)
        val incompleteRecipe = resolve("completo italiano sin mayonesa").single()
        assertNull(incompleteRecipe.foodItem)
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, incompleteRecipe.nutritionSource)
        assertTrue(incompleteRecipe.hasMaterialQuestion())
        assertTrue(incompleteRecipe.excludedIngredients.contains("mayonesa"))
        assertFalse(incompleteRecipe.reviewCandidates.any { !FoodIdentity.matchesExclusions(it, incompleteRecipe.excludedIngredients) })
    }

    @Test fun `habitual quantity evidence stays estimated while explicit quantity is declared`() = runBlocking {
        val habitual = resolve("arroz cocido").single()
        val explicit = resolve("150 g arroz cocido").single()
        assertTrue(habitual.interpretationV2!!.portionConfidence < 1.0)
        assertEquals("habitual_estimate", habitual.interpretationV2!!.stageEvidence.first { it.stage == InterpretationStage.PORTION }.status)
        assertEquals(1.0, explicit.interpretationV2!!.portionConfidence, 0.0)
    }

    @Test fun `explicit bulk mass preserves source nutrient totals without silent caps`() = runBlocking {
        val bulk = resolve("2 kg nueces").single()
        assertEquals("gen039", bulk.foodItem?.id)
        assertEquals(2000.0, bulk.amountGrams!!, 0.01)
        assertEquals(13080.0, bulk.loggedFood!!.calories, 0.01)
        assertEquals(1300.0, bulk.loggedFood!!.fats, 0.01)
    }

    @Test fun `explicit and assumed conversions expose the actual state and source separately`() = runBlocking {
        for (phrase in listOf("150 g espinaca cocida", "150 g salmón cocido", "salmón")) {
            val converted = resolve(phrase).single()
            assertEquals(FoodState.COOKED, converted.foodState)
            assertEquals(FoodState.RAW, FoodIdentity.stateFor(converted.foodItem!!))
            assertTrue(converted.stateConversion!!.contains("yield="))
            assertTrue(converted.loggedFood!!.foodName.contains("cocido", ignoreCase = true))
            val raw = scaleFoodByPortion(converted.foodItem!!, amountGrams = converted.amountGrams)
            assertTrue(converted.loggedFood!!.calories > raw.calories)
        }
    }

    @Test fun `branded trailing package mass asks portion without replacing explicit consumed mass`() = runBlocking {
        for (name in listOf("Hallulla Ideal", "Hallulla Ideal 1kg")) {
            val product = GlobalFoodEntity(foodId = "off-ideal-reference", name = name,
                normalizedName = FoodIdentity.normalize(name), brand = "Ideal", normalizedBrand = "ideal",
                calories = 260.0, protein = 8.0, carbs = 50.0, fats = 3.0, source = "OFF",
                nutritionBasis = "PER_100G_AS_SOLD", portionGrams = 1000.0)
            val trailing = resolve("Hallulla Ideal 1kg", globals = listOf(product)).single()
            assertEquals(product.foodId, trailing.foodItem?.id)
            assertEquals(1000.0, trailing.ambiguousPackageGrams!!, 0.01)
            assertTrue(trailing.amountGrams!! in 50.0..120.0)
            assertTrue(trailing.interpretationV2!!.pendingQuestions.any { it.requestId == "package_portion" })
            assertNotEquals(FoodResolutionStatus.AUTO, trailing.resolutionStatus)
            val consumed = resolve("100 g Hallulla Ideal", globals = listOf(product)).single()
            assertEquals(product.foodId, consumed.foodItem?.id)
            assertEquals(100.0, consumed.amountGrams!!, 0.01)
            assertNull(consumed.ambiguousPackageGrams)
            assertFalse(consumed.hasMaterialQuestion())
        }
        val unbranded = resolve("arroz 200g").single()
        assertEquals(200.0, unbranded.amountGrams!!, 0.01)
        assertNull(unbranded.ambiguousPackageGrams)
    }
}
