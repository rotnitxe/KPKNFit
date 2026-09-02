package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.ParsedMealDescription
import com.example.kpkn.data.models.ParsedMealItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Product contract: a Chilean everyday description must resolve by itself.
 * Identity, grams, kcal, AUTO and a saveable loggedFood are required together.
 */
class EverydayMealCorpusTest {

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

    private fun offPack(
        id: String,
        name: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        portionGrams: Double = 1000.0,
    ) = GlobalFoodEntity(
        foodId = id,
        name = name,
        normalizedName = FoodIndex.normalizeSearch(name),
        aliasesJson = Json.encodeToString(listOf(FoodIndex.normalizeSearch(name))),
        calories = calories,
        protein = protein,
        carbs = carbs,
        fats = fats,
        source = "OFF Chile",
        sourcePriority = 80,
        portionGrams = portionGrams,
        nutritionBasis = "PER_100G_AS_SOLD",
    )

    private val packOff = listOf(
        offPack("off_hallulla_kg", "Hallulla Ideal 1kg", 400.0, 8.0, 70.0, 8.0),
        offPack("off_fideos_pack", "Fideos pack 400g", 360.0, 12.0, 72.0, 2.0, portionGrams = 400.0),
        offPack("off_marraqueta_pack", "Marraqueta pack x6", 390.0, 10.0, 74.0, 4.0),
    )

    private suspend fun resolve(description: String): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = packOff, staticFoods = foods, staticAliases = FOOD_ALIASES)
        val allFoods = foods + packOff.map { it.toFoodItem() }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val parsed = parseMealDescription(description)
        val (tags, _) = TagResolver(RealPort(resolver, allFoods)).resolveAll(parsed)
        return tags
    }

    private fun assertSaveableEveryday(
        tag: ResolvedTag,
        expectedId: String,
        grams: ClosedFloatingPointRange<Double>,
        kcal: ClosedFloatingPointRange<Double>,
        label: String,
    ) {
        assertEquals("$label id", expectedId, tag.foodItem?.id)
        assertFalse("$label must be local, was ${tag.foodItem?.id} ${tag.foodItem?.name}", tag.foodItem?.id?.startsWith("off_") == true)
        val g = tag.amountGrams ?: tag.loggedFood?.amount ?: 0.0
        assertTrue("$label grams $g not in $grams", g in grams)
        val calories = tag.loggedFood?.calories ?: 0.0
        assertTrue("$label kcal $calories not in $kcal", calories in kcal)
        assertEquals("$label AUTO", FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertTrue("$label resolved", tag.isResolved)
        assertFalse("$label material question", tag.hasMaterialQuestion())
        assertNotNull("$label loggedFood", tag.loggedFood)
        assertTrue("$label finite macros", tag.loggedFood!!.calories.isFinite() && tag.loggedFood!!.calories >= 0.0)
        if (!HouseholdPortions.isExplicitKilogram(label)) {
            assertTrue("$label kcal not pack-sized", (tag.loggedFood?.calories ?: 0.0) <= HouseholdPortions.MAX_ITEM_KCAL_WITHOUT_KG)
        }
    }

    @Test
    fun `conteos y typos resuelven unidades de hogar`() = runBlocking {
        assertSaveableEveryday(resolve("2 hallulas").single(), "cl013", 155.0..165.0, 400.0..450.0, "2 hallulas")
        assertSaveableEveryday(resolve("2 hallullas").single(), "cl013", 155.0..165.0, 400.0..450.0, "2 hallullas")
        assertSaveableEveryday(resolve("dos marraquetas").single(), "cl010", 190.0..210.0, 480.0..560.0, "dos marraquetas")
        assertSaveableEveryday(resolve("3 huevos").single(), "gen007", 140.0..160.0, 210.0..250.0, "3 huevos")
        assertSaveableEveryday(resolve("2 panes").single(), "gen019", 190.0..210.0, 500.0..560.0, "2 panes")
        assertSaveableEveryday(resolve("una empanada").single(), "cl001", 160.0..200.0, 400.0..500.0, "una empanada")
        assertSaveableEveryday(resolve("2 sopaipillas").single(), "cl006", 110.0..130.0, 280.0..320.0, "2 sopaipillas")
        assertSaveableEveryday(resolve("poyo").single(), "gen004", 140.0..160.0, 230.0..280.0, "poyo")
        assertSaveableEveryday(resolve("arros").single(), "gen005", 100.0..140.0, 130.0..190.0, "arros")
        assertSaveableEveryday(resolve("wevo").single(), "gen007", 40.0..70.0, 60.0..100.0, "wevo")
        assertSaveableEveryday(resolve("gauda").single(), "gen047", 25.0..40.0, 90.0..160.0, "gauda")
        assertSaveableEveryday(resolve("hallula").single(), "cl013", 70.0..90.0, 180.0..240.0, "hallula")
    }

    @Test
    fun `masa explicita gana al pack y al conteo`() = runBlocking {
        assertSaveableEveryday(resolve("100 g hallulla").single(), "cl013", 99.0..101.0, 250.0..280.0, "100 g hallulla")
        val kilo = resolve("1 kg de hallullas").single()
        assertEquals("cl013", kilo.foodItem?.id)
        assertEquals(1000.0, kilo.amountGrams ?: 0.0, 1.0)
        assertTrue(kilo.isResolved)
        assertEquals(FoodResolutionStatus.AUTO, kilo.resolutionStatus)
        assertSaveableEveryday(resolve("200 g pechuga a la plancha").single(), "gen003c", 199.0..201.0, 300.0..450.0, "200 g pechuga")
        val rice = resolve("medio kilo de arroz").single()
        assertEquals("gen005", rice.foodItem?.id)
        assertEquals(500.0, rice.amountGrams ?: 0.0, 1.0)
        assertTrue(rice.isResolved)
    }

    @Test
    fun `utensilios cotidianos`() = runBlocking {
        assertSaveableEveryday(resolve("una cucharada de aceite").single(), "gen015", 8.0..20.0, 70.0..180.0, "cucharada aceite")
        assertSaveableEveryday(resolve("un vaso de leche").single(), "gen016", 200.0..280.0, 100.0..180.0, "vaso leche")
        val butter = resolve("2 cucharadas de mantequilla").single()
        val butterGrams = butter.amountGrams ?: 0.0
        assertTrue("mantequilla $butterGrams", butterGrams in 25.0..40.0)
        assertTrue(butter.isResolved)
        assertNotNull(butter.loggedFood)
        val butterKcal = butter.loggedFood!!.calories
        assertTrue("mantequilla kcal $butterKcal", butterKcal in 180.0..280.0)
    }

    @Test
    fun `platos chilenos cotidianos se guardan solos`() = runBlocking {
        assertSaveableEveryday(resolve("completo").single(), "cl002", 150.0..250.0, 300.0..460.0, "completo")
        assertSaveableEveryday(resolve("cazuela").single(), "cl004", 300.0..450.0, 280.0..420.0, "cazuela")
        assertSaveableEveryday(resolve("once").single(), "gen019", 80.0..120.0, 210.0..320.0, "once")
        assertSaveableEveryday(resolve("café con leche").single(), "gen016", 180.0..260.0, 90.0..170.0, "café con leche")
        val mixed = resolve("arroz con pollo")
        assertEquals(2, mixed.size)
        val arroz = mixed.single { it.tag.contains("arroz", ignoreCase = true) }
        val pollo = mixed.single { it.tag.contains("pollo", ignoreCase = true) }
        assertSaveableEveryday(arroz, "gen005", 180.0..280.0, 230.0..380.0, "arroz con pollo / arroz")
        assertSaveableEveryday(pollo, "gen004", 120.0..180.0, 200.0..320.0, "arroz con pollo / pollo")
        assertTrue("pollo protein", (pollo.loggedFood?.protein ?: 0.0) in 35.0..60.0)
        val hallullaQueso = resolve("hallulla con queso")
        assertEquals(2, hallullaQueso.size)
        assertSaveableEveryday(
            hallullaQueso.single { it.foodItem?.id == "cl013" },
            "cl013",
            70.0..90.0,
            180.0..240.0,
            "hallulla con queso / pan",
        )
        assertSaveableEveryday(
            hallullaQueso.single { it.foodItem?.id == "gen047" },
            "gen047",
            25.0..40.0,
            90.0..160.0,
            "hallulla con queso / queso",
        )
    }

    @Test
    fun `estado silencioso no pregunta`() = runBlocking {
        val fideos = resolve("fideos").single()
        assertTrue("fideos id ${fideos.foodItem?.id}", fideos.foodItem?.id in setOf("gen040", "gen040h"))
        assertSaveableEveryday(fideos, fideos.foodItem!!.id, 140.0..180.0, 170.0..250.0, "fideos")
        assertSaveableEveryday(resolve("arroz").single(), "gen005", 100.0..140.0, 130.0..190.0, "arroz")
        val tuna = resolve("atún").single()
        assertSaveableEveryday(tuna, "gen029", 100.0..140.0, 110.0..180.0, "atún")
        assertTrue("atún protein", (tuna.loggedFood?.protein ?: 0.0) in 25.0..40.0)
        assertSaveableEveryday(resolve("media marraqueta").single(), "cl010", 45.0..55.0, 110.0..150.0, "media marraqueta")
    }

    @Test
    fun `pack OFF nunca gana a la ficha de hogar`() = runBlocking {
        val hallulla = resolve("hallulla").single()
        assertEquals("cl013", hallulla.foodItem?.id)
        assertFalse(hallulla.foodItem!!.name.contains("kg", ignoreCase = true))
        assertTrue((hallulla.amountGrams ?: 0.0) < 200.0)
        val fideos = resolve("fideos").single()
        assertTrue(fideos.foodItem?.id?.startsWith("gen") == true)
        assertFalse(fideos.foodItem!!.name.contains("pack", ignoreCase = true))
        assertFalse(HouseholdPortions.isGlobalSku(fideos.foodItem!!))
    }

    @Test
    fun `puerto live staticFood nunca devuelve OFF en query sin marca`() {
        listOf("hallulla", "fideos", "arroz", "queso", "atún", "tomate", "leche", "yogurt", "xyznoexiste123").forEach { q ->
            val food = HouseholdPortions.householdStaticFood(q)
            if (food != null) {
                assertFalse("$q leaked ${food.id} ${food.source}", HouseholdPortions.isGlobalSku(food))
                assertFalse(food.id.startsWith("off_"))
            }
        }
    }

    @Test
    fun `TagResolver ignora OFF del puerto leaky en query sin marca`() = runBlocking {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = packOff, staticFoods = foods, staticAliases = FOOD_ALIASES)
        val allFoods = foods + packOff.map { it.toFoodItem() }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val leaky = object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = allFoods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? =
                HouseholdPortions.householdStaticFood(tag) ?: packOff.first().toFoodItem()
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
        val unknown = TagResolver(leaky).resolveAll(
            ParsedMealDescription(
                items = listOf(ParsedMealItem(tag = "sku inventado xyz")),
                rawDescription = "sku inventado xyz",
            ),
        ).first
        assertFalse(
            "leaky OFF persisted as ${unknown.single().foodItem?.id} ${unknown.single().foodItem?.name}",
            unknown.single().foodItem?.id?.startsWith("off_") == true,
        )
        val hallulla = TagResolver(leaky).resolveAll(
            ParsedMealDescription(
                items = listOf(ParsedMealItem(tag = "hallulla")),
                rawDescription = "hallulla",
            ),
        ).first.single()
        assertEquals("cl013", hallulla.foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, hallulla.resolutionStatus)
        assertTrue((hallulla.amountGrams ?: 0.0) in 70.0..90.0)
    }

    @Test
    fun `pechuga cocida conserva la precision ya ganada`() = runBlocking {
        val tags = resolve("200 g pechuga cocida")
        assertEquals("gen004", tags.single().foodItem?.id)
        assertEquals(200.0, tags.single().amountGrams ?: 0.0, 0.01)
        assertEquals(64.2, tags.single().loggedFood!!.protein, 0.15)
        assertTrue(tags.single().isResolved)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
}
