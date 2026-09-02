package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.ParsedMealDescription
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Systematic mode contracts S1–S8. Failures must cite the pipeline rule, not a missing dish name.
 */
class SystematicModeContractTest {

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

    private val packOff = listOf(
        GlobalFoodEntity(
            foodId = "off_hallulla_kg",
            name = "Hallulla Ideal 1kg",
            normalizedName = FoodIndex.normalizeSearch("Hallulla Ideal 1kg"),
            aliasesJson = Json.encodeToString(listOf(FoodIndex.normalizeSearch("Hallulla Ideal 1kg"))),
            calories = 400.0,
            protein = 8.0,
            carbs = 70.0,
            fats = 8.0,
            source = "OFF Chile",
            sourcePriority = 80,
            portionGrams = 1000.0,
            nutritionBasis = "PER_100G_AS_SOLD",
        ),
    )

    private suspend fun resolve(description: String, mealType: MealType? = null): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = packOff, staticFoods = foods, staticAliases = FOOD_ALIASES)
        val allFoods = foods + packOff.map { it.toFoodItem() }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val parsed = parseMealDescription(description)
        val (tags, _) = TagResolver(RealPort(resolver, allFoods)).resolveAll(parsed, mealType = mealType)
        return tags
    }

    @Test
    fun `S1b taco de pollo no roba la ficha Pollo`() = runBlocking {
        val stolen = findFoodByNormalized("taco de pollo")
        assertFalse(
            "S1b: lookup fuzzy no puede elegir Pollo como hijo de taco de pollo, fue ${stolen?.id} ${stolen?.name}",
            stolen?.id == "gen004" || stolen?.name.equals("Pollo", ignoreCase = true) == true,
        )
        val tags = resolve("taco de pollo")
        assertTrue("S1: taco de pollo debe conservar wrap, tags=${tags.map { it.foodItem?.id to it.tag }}", tags.size >= 1)
        assertFalse(
            "S1b: resolver no deja solo Pollo gen004",
            tags.size == 1 && tags.single().foodItem?.id == "gen004",
        )
    }

    @Test
    fun `S1 frase de plato con con no se parte`() = runBlocking {
        val mote = resolve("mote con huesillo")
        assertEquals("S1: mote con huesillo es un plato, tags=${mote.map { it.tag }}", 1, mote.size)
        assertEquals("cl005", mote.single().foodItem?.id)
        val mixed = resolve("arroz con pollo y ensalada")
        assertTrue("S1 contraste: ingredientes listados pueden ser 3 tags, fue ${mixed.size}", mixed.size >= 2)
    }

    @Test
    fun `S2 gauda es lonja no 100g`() = runBlocking {
        val gauda = resolve("gauda").single()
        val g = gauda.amountGrams ?: 0.0
        assertTrue("S2: gauda $g g no es lonja 25-40", g in 25.0..40.0)
        assertTrue((gauda.loggedFood?.calories ?: 0.0) < 200.0)
    }

    @Test
    fun `S3 bowl de avena no es 120g de hojuelas a 389kcal`() = runBlocking {
        val tags = resolve("un bowl de avena")
        assertEquals(1, tags.size)
        val avena = tags.single()
        val kcal = avena.loggedFood?.calories ?: 0.0
        val g = avena.amountGrams ?: 0.0
        assertTrue("S3: bowl avena grams $g kcal $kcal parece hojuela seca de plato", kcal < 280.0)
        assertTrue("S3: bowl avena $g g demasiado seco", g <= 80.0)
    }

    @Test
    fun `S4 dos tacos multiplican unidad y punado es punado`() = runBlocking {
        val two = resolve("2 tacos")
        assertTrue(two.isNotEmpty())
        val tacoGrams = two.sumOf { it.amountGrams ?: it.loggedFood?.amount ?: 0.0 }
        val one = resolve("taco").sumOf { it.amountGrams ?: it.loggedFood?.amount ?: 0.0 }
        assertTrue("S4: 2 tacos ($tacoGrams) debe ser >= 2x unidad ($one)", tacoGrams >= one * 1.8 || tacoGrams >= 180.0)
        val handful = resolve("un punado de almendras").single()
        val hg = handful.amountGrams ?: 0.0
        assertTrue("S4: punado $hg g no es ~25-40 g", hg in 20.0..45.0)
        val nHandful = resolve("un puñado de almendras").single()
        assertTrue((nHandful.amountGrams ?: 0.0) in 20.0..45.0)
    }

    @Test
    fun `S5 porotos granados es cl007 y pad thai no es 100g MIXED`() = runBlocking {
        val granados = resolve("porotos granados")
        assertEquals("S5: tags=${granados.map { it.foodItem?.id to it.tag }}", 1, granados.size)
        assertEquals("cl007", granados.single().foodItem?.id)
        val pad = resolve("pad thai")
        val grams = pad.sumOf { it.amountGrams ?: it.loggedFood?.amount ?: 0.0 }
        assertTrue("S5: pad thai $grams g es plato no 100 g MIXED", grams in 250.0..450.0)
    }

    @Test
    fun `S6 drawer usa eatenGrams no servingSize por quantity y boton por fingerprint`() {
        assertEquals("REGISTRAR", FoodLoggerPrimaryAction.label(hasTags = false, descriptionEdited = false, isSearchMode = false))
        assertEquals("GUARDAR", FoodLoggerPrimaryAction.label(hasTags = true, descriptionEdited = false, isSearchMode = false))
        assertEquals("ACTUALIZAR Y BUSCAR", FoodLoggerPrimaryAction.label(hasTags = true, descriptionEdited = true, isSearchMode = false))
        assertFalse(
            FoodLoggerPrimaryAction.isDescriptionEdited("arroz  con  pollo", "arroz con pollo", hasTags = true, describeTab = true),
        )
        val cheese = HouseholdPortions.householdStaticFood("gauda")
        val eaten = HouseholdPortions.resolveEatenGrams(
            intent = AmountIntent.UNSPECIFIED,
            quantity = 1.0,
            food = cheese,
            parsedGrams = null,
            query = "gauda",
        )
        assertTrue("S6: eatenGrams gauda $eaten no puede ser servingSize 100", eaten in 25.0..40.0)
        assertNotEquals(cheese?.servingSize ?: 100.0, eaten, 0.1)
    }

    @Test
    fun `S7 un retrieve compartido no dispara retrieve por fragmento ni por tag`() = runBlocking {
        SemanticPortionRetriever.resetRetrieveCount()
        val snapshot = SemanticPortionRetriever.retrieve("arroz con pollo y ensalada")
        val parsed = parseMealDescription("arroz con pollo y ensalada", snapshot)
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = packOff, staticFoods = foods, staticAliases = FOOD_ALIASES)
        TagResolver(RealPort(SmartFoodResolver(noOpNutritionDao(), index, null), foods))
            .resolveAll(parsed)
        val calls = SemanticPortionRetriever.retrieveCount
        assertTrue("S7: retrieves=$calls (tope 2 para 3 ítems con 1 snapshot)", calls <= 2)
        assertTrue(parsed.items.size >= 2)
    }

    @Test
    fun `S8 arroz con huevo es comida no 100g mas 100g`() = runBlocking {
        val tags = resolve("arroz con huevo")
        assertEquals("S8: ${tags.map { it.tag }}", 2, tags.size)
        val rice = tags.single { it.tag.contains("arroz", ignoreCase = true) }
        val egg = tags.single { it.tag.contains("huevo", ignoreCase = true) }
        val riceG = rice.amountGrams ?: 0.0
        val eggG = egg.amountGrams ?: 0.0
        assertTrue("S8 comida: arroz $riceG debe ser plato ~200-250 no 100", riceG in 180.0..280.0)
        assertTrue("S8 comida: huevo $eggG de comida", eggG in 40.0..120.0)
        val ctx = ContextDetector.detect("arroz con huevo")
        assertEquals(InferredMealContext.Shape.MAIN_PLATE, ctx.shape)
        assertEquals("comida", ctx.assumedLabel)
    }

    @Test
    fun `S8 yogurt granola es desayuno y cafe leche es bebida`() = runBlocking {
        val yogurt = resolve("yogurt con granola")
        assertTrue(yogurt.size >= 2)
        val granola = yogurt.first { it.tag.contains("granola", ignoreCase = true) }
        val g = granola.amountGrams ?: 0.0
        assertTrue("S8 desayuno: granola topping $g no es 100 g de plato", g in 20.0..50.0)
        val breakfast = ContextDetector.detect("yogurt con granola")
        assertEquals(InferredMealContext.Shape.BREAKFAST_BOWL, breakfast.shape)
        val drink = ContextDetector.detect("café con leche")
        assertEquals(InferredMealContext.Shape.BEVERAGE, drink.shape)
        val coffee = resolve("café con leche").single()
        val cg = coffee.amountGrams ?: 0.0
        assertTrue("S8 bebida: $cg ml/g de vaso", cg in 180.0..280.0)
    }

    @Test
    fun `S8 queso suelto no es comida y slot no aplasta bebida`() = runBlocking {
        val cheese = resolve("queso").single()
        assertTrue((cheese.amountGrams ?: 0.0) in 25.0..40.0)
        val cheeseCtx = ContextDetector.detect("queso", mealType = MealType.LUNCH)
        assertTrue(
            "S8: slot LUNCH no convierte queso en almuerzo, shape=${cheeseCtx.shape} ctx=${cheeseCtx.primaryContext}",
            cheeseCtx.shape == InferredMealContext.Shape.SNACK_ITEM ||
                cheeseCtx.primaryContext == ContextDetector.MealContext.GENERAL,
        )
        val drinkLunch = ContextDetector.detect("café con leche", mealType = MealType.LUNCH)
        assertEquals(InferredMealContext.Shape.BEVERAGE, drinkLunch.shape)
        val snackPlate = resolve("arroz con huevo", mealType = MealType.SNACK)
        val lunchPlate = resolve("arroz con huevo", mealType = MealType.LUNCH)
        val snackG = snackPlate.sumOf { it.amountGrams ?: 0.0 }
        val lunchG = lunchPlate.sumOf { it.amountGrams ?: 0.0 }
        assertTrue("S8 slot: snack $snackG < lunch $lunchG", snackG < lunchG)
        val explicitPlate = parseMealDescription("un plato de arroz")
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, explicitPlate.items.single().amountIntent)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
}
