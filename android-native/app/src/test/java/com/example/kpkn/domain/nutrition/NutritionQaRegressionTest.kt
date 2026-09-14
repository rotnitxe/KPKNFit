package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.*
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.MealType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual APK findings, including the shipped OFF catalog rather than a curated-only index. */
class NutritionQaRegressionTest {
    private fun resolver(globals: List<GlobalFoodEntity> = emptyList()): TagResolver {
        val static = buildFoodDatabase()
        val foods = (static + globals.map { it.toFoodItem() }).associateBy { it.id }
        val index = FoodIndex().apply { build(globals, static, FOOD_ALIASES) }
        val dao = java.lang.reflect.Proxy.newProxyInstance(NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java)) { _, _, _ -> null } as NutritionDao
        val resolver = SmartFoodResolver(dao, index)
        val port = object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) = resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods[id]
            override suspend fun staticFood(tag: String) = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String) = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
        return TagResolver(port)
    }

    private suspend fun resolve(text: String, globals: List<GlobalFoodEntity> = emptyList()): List<ResolvedTag> =
        resolver(globals).resolveAll(parseMealDescription(text)).first

    private fun List<ResolvedTag>.active(query: String): ResolvedTag = single {
        !it.isExcluded && FoodIdentity.normalize(it.foodQuery.ifBlank { it.tag }).contains(FoodIdentity.normalize(query))
    }

    @Test fun `real lunch salad uses referenced vegetables and asks composition`() = runBlocking {
        val tags = resolve("Almorce arroz con pollo y ensalada").filterNot { it.isExcluded }
        assertEquals("all lunch mentions survive", 3, tags.size)
        val salad = tags.single { it.foodQuery == "ensalada" }
        val grams = salad.amountGrams!!
        val logged = salad.loggedFood!!
        // Explicitly assumed 50:50 lettuce/tomato: USDA 169249/170457, local rounded rows15/18kcal100g.
        assertEquals("salad density is a referenced mixture, not universal160", 16.5 * grams / 100.0, logged.calories, 0.01)
        assertEquals(1.15 * grams / 100.0, logged.protein, 0.01)
        assertEquals(setOf("169249", "170457"), salad.nutritionEstimate!!.referenceSourceRecordIds.toSet())
        assertTrue(logged.nutritionReferenceNote!!.contains("lechuga y tomate"))
        assertTrue("unknown composition remains a material question", salad.hasMaterialQuestion())
        assertTrue(salad.interpretationV2!!.pendingQuestions.any { it.requestId == "composition" })
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, salad.nutritionSource)
        assertNull("the assumption is not a verified salad recipe", salad.foodItem)
        val withoutTomato = resolve("100 g ensalada sin tomate").first { !it.isExcluded }
        assertFalse("the assumed mixture cannot reintroduce a declared exclusion", withoutTomato.nutritionEstimate!!.referenceFoodIds.contains("gen026"))
    }

    @Test fun `exact mass does not certify an unknown nutrient density`() = runBlocking {
        val unknown = resolve("100 g preparación desconocida").single()
        val result = unknown.interpretationV2!!
        assertEquals(100.0, unknown.amountGrams!!, 0.0)
        assertEquals(100.0, unknown.portionMinGrams!!, 0.0)
        assertEquals(100.0, unknown.portionMaxGrams!!, 0.0)
        assertTrue(unknown.nutritionEstimate!!.isUnmatchedFallback)
        assertTrue(unknown.loggedFood!!.nutritionReferenceNote!!.contains("Sin referencia nutricional", ignoreCase = true))
        assertEquals("conservative density lower bound", 0.0, result.caloriesMin, 0.0)
        assertEquals("conservative marginal bound, not measured precision", 900.0, result.caloriesMax, 0.0)
        assertTrue(result.proteinMaxGrams > result.proteinMinGrams)
        assertTrue(unknown.hasMaterialQuestion())
        assertFalse(result.canFinalize())
    }

    @Test fun `plain oats cannot become a product whose malformed brand is Avena`() = runBlocking {
        val offending = offFoods.single { it.foodId == "off_7800120162489" }
        assertEquals("Avena", offending.brand)
        assertEquals("Vivo PRO BIÓTICOS", offending.name)
        val tags = resolve("avena sin leche ni huevo", offFoods)
        val active = tags.filterNot { it.isExcluded }
        assertEquals(1, active.size)
        assertEquals("a brand column is not evidence of oat identity: $active", "gen011", active.single().foodItem?.id)
        assertTrue(tags.any { it.isExcluded && it.foodQuery.contains("leche") })
        assertTrue(tags.any { it.isExcluded && it.foodQuery.contains("huevo") })
        assertTrue("offending brand alias is rejected for identity", active.single().reviewCandidates.none { it.id == offending.foodId })
    }

    @Test fun `milk constraints apply to every actual catalog candidate not ingredient mentions`() = runBlocking {
        assertTrue(offFoods.any { it.foodId == "off_7808709504835" })
        val milk = resolve("leche sin lactosa", offFoods).single()
        val candidates = listOfNotNull(milk.foodItem) + milk.reviewCandidates
        assertTrue("at least one actual lactose-free milk remains", candidates.isNotEmpty())
        assertTrue("all candidates preserve requested identity: ${candidates.map { it.name }}", candidates.all {
            val name = FoodIdentity.normalize(it.name)
            name.contains("leche") && name.contains("sin lactosa") && !name.contains("queso") &&
                !name.contains("condensad") && !name.contains("sabor vainilla")
        })
        assertFalse(FoodIdentity.matchesDeclaredIdentity("leche sin lactosa", "Queso Mantecoso con leche sin lactosa"))
    }

    @Test fun `negated ingredients do not inflate context portions or later combination scaling`() = runBlocking {
        val plain = resolve("avena").active("avena")
        val firstOrder = resolve("avena sin leche ni huevo")
        val reversedOrder = resolve("avena sin huevo ni leche")

        assertEquals(40.0, plain.amountGrams!!, 0.0)
        assertEquals(40.0, firstOrder.active("avena").amountGrams!!, 0.0)
        assertEquals(40.0, reversedOrder.active("avena").amountGrams!!, 0.0)
        assertEquals(setOf("leche", "huevo"), firstOrder.filter { it.isExcluded }.map { it.foodQuery }.toSet())
        assertEquals(setOf("leche", "huevo"), reversedOrder.filter { it.isExcluded }.map { it.foodQuery }.toSet())
        assertEquals("only the consumed mention contributes to the total", 1,
            firstOrder.count { !it.isExcluded })
        val explicit = resolve("100 g avena sin leche ni huevo").active("avena")
        assertEquals(100.0, explicit.amountGrams!!, 0.0)
        assertEquals(AmountIntent.EXPLICIT_MASS, explicit.amountIntent)
    }

    @Test fun `positive breakfast keeps each component household prior`() = runBlocking {
        val tags = resolver().resolveAll(
            parseMealDescription("avena con leche y huevo"),
            mealType = MealType.BREAKFAST,
        ).first.filterNot { it.isExcluded }
        assertEquals(3, tags.size)
        assertEquals(40.0, tags.active("avena").amountGrams!!, 0.0)
        assertEquals(200.0, tags.active("leche").amountGrams!!, 0.0)
        assertEquals(50.0, tags.active("huevo").amountGrams!!, 0.0)
    }

    @Test fun `generic tortilla asks composition while declared wheat stays specific`() = runBlocking {
        val tortilla = resolve("tortilla").single()
        assertTrue("flatbread and egg-potato dishes cannot share an automatic identity", tortilla.hasMaterialQuestion())
        assertFalse(tortilla.interpretationV2!!.canFinalize())
        assertTrue("assuming wheat is not knowing the recipe density", tortilla.interpretationV2!!.caloriesMax > tortilla.loggedFood!!.calories * 2.0)
        assertTrue(tortilla.loggedFood!!.nutritionReferenceNote!!.contains("sin confirmar"))
        val wheat = resolve("tortilla de trigo").single()
        assertEquals("gen090", wheat.foodItem?.id)
        assertFalse("declared composition should not be asked again", wheat.hasMaterialQuestion())
    }

    @Test fun `declared tortilla composition replaces identity and only preserves declared amount`() = runBlocking {
        val resolver = resolver()
        val explicit = resolver.resolveAll(parseMealDescription("40 g tortilla")).first.single()
        val correctedExplicit = resolver.resolveDeclaredComposition(explicit, "tortilla de huevo")

        assertEquals(explicit.id, correctedExplicit.id)
        assertEquals("tortilla de huevo", correctedExplicit.foodQuery)
        assertEquals(AmountIntent.EXPLICIT_MASS, correctedExplicit.amountIntent)
        assertEquals(40.0, correctedExplicit.amountGrams!!, 0.0)
        assertFalse("the old wheat identity must never survive", correctedExplicit.foodItem?.id == "gen090")
        assertFalse(FoodIdentity.normalize(correctedExplicit.loggedFood!!.foodName).contains("trigo"))
        assertTrue("identity answer is persisted as a declaration", "identity" in correctedExplicit.confirmedDimensions)

        val inferred = resolver.resolveAll(parseMealDescription("tortilla")).first.single()
        val correctedInferred = resolver.resolveDeclaredComposition(inferred, "tortilla de papas")
        assertEquals(AmountIntent.UNSPECIFIED, correctedInferred.amountIntent)
        assertNotEquals("an inferred wrap mass is not a declared potato-tortilla mass", 40.0,
            correctedInferred.amountGrams!!, 0.0)
        assertFalse(correctedInferred.foodItem?.id == "gen090")
        assertFalse(FoodIdentity.normalize(correctedInferred.loggedFood!!.foodName).contains("trigo"))
        assertTrue(FoodIdentity.normalize(correctedInferred.loggedFood!!.foodName).contains("papas"))
        assertTrue(
            "the corrected heuristic remains saveable as uncertainty: ${correctedInferred.interpretationV2}",
            correctedInferred.interpretationV2!!.canFinalize(),
        )
        assertTrue(correctedInferred.isUncertain)
        assertTrue(
            "corrected heuristic should retain its evidence note",
            !correctedInferred.loggedFood!!.nutritionReferenceNote.isNullOrBlank(),
        )
    }

    @Test fun `unsure generic tortilla never restores wheat preview`() = runBlocking {
        val tortilla = resolver().resolveAll(parseMealDescription("40 g tortilla")).first.single()
        val unsure = NutritionInterpretationBridge.acceptEstimate(tortilla)

        assertEquals("tortilla", unsure.foodQuery)
        assertNull(unsure.foodItem)
        assertEquals("Tortilla", unsure.loggedFood?.foodName)
        assertFalse(unsure.loggedFood?.foodName.orEmpty().contains("Trigo", ignoreCase = true))
        assertTrue(unsure.interpretationV2!!.canFinalize())
        assertTrue(unsure.interpretationV2!!.isUncertain)
        assertTrue(unsure.interpretationV2!!.caloriesMax > unsure.interpretationV2!!.caloriesMin)
        assertTrue(unsure.loggedFood?.nutritionReferenceNote.orEmpty().contains("sin confirmar"))
        assertNull(unsure.confirmedLearning())
    }

    companion object {
        /** Import the shipped TSV through its real description parser. Do not replace its rows with invented fixtures. */
        private val offFoods: List<GlobalFoodEntity> by lazy {
            File("src/main/assets/food_data/off_chile.csv").useLines { lines -> lines.mapNotNull { line ->
                val p = line.split('\t')
                if (p.size <= 156 || p[0].isBlank() || p[10].isBlank()) return@mapNotNull null
                fun value(i: Int, max: Double = 100.0) = p[i].toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..max } ?: 0.0
                val kcal = value(89, 1000.0); val protein = value(150); val fat = value(92); val carbs = value(129)
                val energy = protein * 4.0 + fat * 9.0 + carbs * 4.0
                if (kcal <= 0.0 || energy <= 0.0 || kotlin.math.abs(kcal - energy) / energy > 0.5) return@mapNotNull null
                val parsed = FoodDescriptionParser.parse(p[10], p[18].takeIf { it.isNotBlank() }, kcal,
                    protein, fat, carbs, value(146), value(130), value(156, 5.0), allowDatabaseMatch = false)
                val name = FoodIndex.normalizeSearch(parsed.cleanedName)
                val brand = parsed.brandHint?.let { FoodIndex.normalizeSearch(it) }
                GlobalFoodEntity(foodId = "off_${p[0]}", name = parsed.cleanedName, brand = parsed.brandHint,
                    normalizedName = name, normalizedBrand = brand,
                    aliasesJson = JsonArray(listOfNotNull(name, brand).map(::JsonPrimitive)).toString(),
                    calories = parsed.calories, protein = parsed.protein, carbs = parsed.carbs, fats = parsed.fats,
                    source = "OFF Chile", sourcePriority = 80, sourceRecordId = p[0], nutritionBasis = "PER_100G_AS_SOLD",
                    qualityFlagsJson = FoodImporter.encodeQualityFlags(FoodImporter.offQualityFlags(kcal, protein, carbs, fat, parsed.confidence)))
            }.toList() }
        }
    }
}
