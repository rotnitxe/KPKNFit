package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.food.*
import com.example.kpkn.data.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Cross-layer regressions: correctness of identity must survive valid-looking macros. */
class FoodIdentityReliabilityTest {
    private fun dao(): NutritionDao = java.lang.reflect.Proxy.newProxyInstance(
        NutritionDao::class.java.classLoader, arrayOf(NutritionDao::class.java),
    ) { _, _, _ -> null } as NutritionDao

    @Test fun `nutrient denominator is independent of household portion`() {
        val wholegrain = findStaticFoodById("gen133")!!
        assertEquals(265.0, scaleFoodByPortion(wholegrain, amountGrams = 100.0).calories, 0.01)
        assertEquals(165.0, scaleFoodByPortion(findStaticFoodById("gen003e")!!, amountGrams = 100.0).calories, 0.01)
        assertEquals(154.0, scaleFoodByPortion(findStaticFoodById("gen007")!!, amountGrams = 100.0).calories, 0.01)
        val largerDefault = wholegrain.copy(servingSize = 200.0)
        assertEquals(scaleFoodByPortion(wholegrain, amountGrams = 100.0).calories,
            scaleFoodByPortion(largerDefault, amountGrams = 100.0).calories, 0.01)
        assertEquals(scaleFoodByPortion(findStaticFoodById("gen020")!!, amountGrams = 100.0).calories,
            scaleFoodByPortion(wholegrain, amountGrams = 100.0).calories, 0.01)
    }

    @Test fun `legacy custom JSON keeps the original per serving meaning`() {
        val legacy = Json.decodeFromString<FoodItem>("""{"id":"my-bar","name":"Mi barrita","isCustom":true,"servingSize":40.0,"calories":180.0,"protein":8.0,"carbs":16.0,"fats":9.0,"nutritionBasis":"PER_100G_AS_SOLD"}""")
        assertEquals(180.0, scaleFoodByPortion(legacy, amountGrams = 40.0).calories, 0.01)
        assertEquals(NutritionSourceKind.USER_PROVIDED, NutrientBasis.source(legacy))
        val imported = GlobalFoodEntity(foodId = "usda-reference", name = "Reference food",
            normalizedName = "reference food", calories = 180.0, protein = 8.0,
            carbs = 16.0, fats = 9.0, source = "USDA", nutritionBasis = "PER_100G_AS_SOLD")
        assertEquals(72.0, scaleFoodByPortion(imported.toFoodItem(), amountGrams = 40.0).calories, 0.01)
        val historical = Json.decodeFromString<LoggedFood>("""{"foodName":"Mi barrita","amount":40.0,"calories":180.0,"protein":8.0,"carbs":16.0,"fats":9.0}""")
        assertEquals(180.0, historical.calories, 0.0)
        val legacyMissingFlag = legacy.copy(isCustom = false).toEntity().toFoodItem()
        assertTrue(legacyMissingFlag.isCustom)
        assertEquals(180.0, scaleFoodByPortion(legacyMissingFlag, amountGrams = 40.0).calories, 0.01)
        val perServingImport = imported.copy(nutritionBasis = "PER_SERVING", portionGrams = 40.0)
        assertEquals(180.0, scaleFoodByPortion(perServingImport.toFoodItem(), amountGrams = 40.0).calories, 0.01)
    }

    @Test fun `volume nutrients and mass nutrients retain their declared denominator`() {
        val milkByVolume = FoodItem(name = "Leche entera", unit = "ml", servingSize = 100.0,
            nutritionBasis = "PER_SERVING", calories = 61.0, protein = 3.2, carbs = 4.8, fats = 3.3)
        val cupMass = SubjectivePortionEngine.massFromVolumeMl(240.0, milkByVolume.name)
        assertEquals(247.2, cupMass, 0.01)
        assertEquals(103.0, NutrientBasis.grams(milkByVolume), 0.01)
        val cup = scaleFoodByPortion(milkByVolume, amountGrams = cupMass)
        assertEquals(146.0, cup.calories, 0.01)
        assertEquals("g", cup.unit)
        val milkByMass = milkByVolume.copy(nutritionBasis = "PER_100G_AS_SOLD")
        assertEquals(100.0, NutrientBasis.grams(milkByMass), 0.0)
        assertEquals(151.0, scaleFoodByPortion(milkByMass, amountGrams = cupMass).calories, 0.01)
    }

    @Test fun `family is not identity and explicit constraints cannot be dropped`() {
        assertEquals("pavo", FoodIdentity.familyFor("pechuga de pavo"))
        assertNotEquals("pasta", FoodIdentity.familyFor("pasta de maní"))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("pechuga de pavo", "Pechuga de pollo"))
        for (attribute in listOf("sin lactosa", "sin azúcar", "sin gluten")) {
            assertFalse(attribute, FoodIdentity.matchesDeclaredIdentity("leche $attribute", "Leche entera"))
            assertTrue(attribute, FoodIdentity.matchesDeclaredIdentity("leche $attribute", "Leche $attribute"))
        }
        assertNotEquals(FoodIdentity.canonicalKey(FoodItem(name = "Leche entera")),
            FoodIdentity.canonicalKey(FoodItem(name = "Leche descremada")))
    }

    @Test fun `branded global exact match survives local family retrieval`() = runBlocking {
        val global = GlobalFoodEntity(foodId = "off-reference", name = "Hallulla Ideal",
            normalizedName = "hallulla ideal", brand = "Ideal", normalizedBrand = "ideal",
            calories = 260.0, protein = 8.0, carbs = 49.0, fats = 4.0, source = "OFF Chile")
        val index = FoodIndex().apply { build(listOf(global), buildFoodDatabase(), FOOD_ALIASES) }
        assertTrue(index.search("Hallulla Ideal").contains(global.foodId))
        assertEquals(global.foodId, SmartFoodResolver(dao(), index).resolve("hallulla", "Ideal").resolvedFoodId)
    }

    @Test fun `weak candidate never becomes AUTO just because macros look plausible`() = runBlocking {
        val f = FoodItem(id = "weak", name = "Preparación de prueba", calories = 180.0, protein = 8.0, carbs = 22.0, fats = 7.0)
        val candidate = SmartFoodResolver.ResolutionCandidate(f.id, f.name, null, 0.3,
            SmartFoodResolver.Confidence.LOW, "LOCAL", f.calories, f.protein, f.carbs, f.fats, 0.0, emptyList())
        val port = object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                SmartFoodResolver.ResolutionResult(tag, listOf(candidate), SmartFoodResolver.Decision.NEEDS_REVIEW, f.id)
            override suspend fun getFoodById(id: String) = f
            override suspend fun staticFood(tag: String): FoodItem? = null
            override fun staticIsExact(tag: String) = false
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
        val item = ParsedMealItem(tag = "preparación desconocida", amountGrams = 100.0, amountIntent = AmountIntent.EXPLICIT_MASS)
        val tag = TagResolver(port).resolveAll(ParsedMealDescription(items = listOf(item), rawDescription = "preparación desconocida")).first.single()
        assertNotNull(tag.loggedFood)
        assertNotEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertTrue(tag.hasMaterialQuestion())
        assertFalse(tag.isResolved)
    }

    @Test fun `unavailable cooked variant cannot claim that the raw profile is cooked`() {
        val raw = findStaticFoodById("gen009")!!
        assertNull(CookingStateResolver.resolveAssumedVariant("salmón", raw, FoodState.COOKED))
        val converted = scaleFoodByPortion(raw, amountGrams = 150.0, cookingMethod = CookingMethod.COCIDO)
        assertTrue(converted.calories > scaleFoodByPortion(raw, amountGrams = 150.0).calories)
        val english = raw.copy(name = "Salmon, cooked", foodState = "COOKED")
        assertTrue(CookingStateResolver.isDbFoodCooked(english))
        assertFalse(CookingStateResolver.isDbFoodRaw(english))
    }

    @Test fun `resolved utensils stay locked instead of becoming whole foods`() {
        val bread = findStaticFoodById("gen019")!!
        assertEquals(56.0, HouseholdPortions.resolveEatenGrams(AmountIntent.RESOLVED_SUBJECTIVE,
            2.0, bread, 56.0, query = "pan", unitId = "slice"), 0.0)
        val yogurt = findStaticFoodById("gen017")!!
        assertEquals(360.0, HouseholdPortions.resolveEatenGrams(AmountIntent.RESOLVED_SUBJECTIVE,
            2.0, yogurt, 360.0, query = "yogurt", unitId = "cup"), 0.0)
    }

    @Test fun `manual corrections preserve distinct cooking mentions without reviving removed foods`() {
        val fried = ResolvedTag(tag = "pollo", cookingMethod = CookingMethod.FRITO, hasManualEdits = true)
        val cooked = ResolvedTag(tag = "pollo", cookingMethod = CookingMethod.COCIDO, hasManualEdits = true)
        val removed = ResolvedTag(tag = "arroz", hasManualEdits = true)
        val merged = mergeTagsPreservingManualEdits(listOf(fried, cooked, removed),
            listOf(cooked.copy(id = "new-cooked", hasManualEdits = false), fried.copy(id = "new-fried", hasManualEdits = false)))
        assertEquals(listOf(cooked.id, fried.id), merged.map { it.id })
        assertEquals(1, mergeTagsPreservingManualEdits(listOf(fried, fried.copy(id = "repeat")), listOf(fried)).size)
    }

    @Test fun `global portions normalize before comparing nutrient differences`() {
        val serving = GlobalFoodEntity(foodId = "reference-serving", name = "Reference snack",
            normalizedName = "reference snack", source = "USDA", nutritionBasis = "PER_SERVING",
            portionGrams = 40.0, calories = 180.0, protein = 8.0, carbs = 16.0, fats = 9.0)
        val index = FoodIndex().apply { build(listOf(serving), emptyList()) }
        assertEquals(450.0, index.getFood(serving.foodId)!!.calories, 0.01)
    }

    @Test fun `confirmed USDA mass profiles do not inherit a visual ml denominator`() {
        for ((id, source, kcal) in listOf(Triple("gen015", "171413", 884.0), Triple("gen016", "171265", 61.0), Triple("gen046", "171269", 34.0))) {
            val food = findStaticFoodById(id)!!
            assertEquals(source, food.sourceRecordId)
            assertEquals(100.0, NutrientBasis.grams(food), 0.0)
            assertEquals(kcal, scaleFoodByPortion(food, amountGrams = 100.0).calories, 0.01)
        }
    }

    @Test fun `neighboring bread names and mixed dishes cannot replace a declared ingredient`() {
        assertFalse(FoodIdentity.matchesDeclaredIdentity("hallulla", findStaticFoodById("cl010")!!))
        assertFalse(FoodIdentity.matchesDeclaredIdentity("palta", findStaticFoodById("cl037")!!))
        assertTrue(FoodIdentity.matchesDeclaredIdentity("palta", findStaticFoodById("gen014")!!))
    }
    @Test fun `curated origin stays eligible when nutrient provenance becomes explicit`() = runBlocking {
        val oil = findStaticFoodById("gen015")!!
        val legacy = oil.copy(source = null, sourceRecordId = null)
        val realFoods = buildFoodDatabase()
        fun indexWith(record: FoodItem) = FoodIndex().apply {
            build(emptyList(), realFoods.map { if (it.id == oil.id) record else it }, FOOD_ALIASES)
        }
        val documented = indexWith(oil)
        val before = SmartFoodResolver(dao(), indexWith(legacy)).resolve("aceite")
        val after = SmartFoodResolver(dao(), documented).resolve("aceite")
        assertTrue("USDA provenance must not erase curated inclusion", documented.getFood(oil.id)!!.isCuratedCatalog)
        assertEquals("source evidence remains truthful", oil.source, documented.getFood(oil.id)!!.source)
        assertEquals("identity cannot change when adding provenance", oil.id, after.resolvedFoodId)
        assertEquals("same curated identity remains automatic", SmartFoodResolver.Decision.AUTO_SELECT, after.decision)
        assertEquals(before.candidates.first().score, after.candidates.first().score, 0.0)
        val custom = oil.copy(id = "user-oil", isCustom = true)
        val unknown = oil.copy(id = "unverified-oil", qualityFlags = listOf("UNVERIFIED_NUTRIENT_BASIS"))
        val index = FoodIndex().apply { build(emptyList(), listOf(custom, unknown)) }
        assertFalse("user data is not catalog curation", index.getFood(custom.id)!!.isCuratedCatalog)
        assertFalse("unverified data is not catalog curation", index.getFood(unknown.id)!!.isCuratedCatalog)
    }

}
