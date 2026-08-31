package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de fluidez: identidad exacta contra ruido OFF, estado asumible
 * saveable, y separación de ingredientes vs platos-ficha.
 */
class FluencyGoldenCorpusTest {

    private class RealPort(
        private val resolver: SmartFoodResolver,
        private val foods: List<FoodItem>,
    ) : FoodResolutionPort {
        override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
            resolver.resolve(tag, brandHint, contextHint, stateHint)
        override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
        override suspend fun staticFood(tag: String): FoodItem? = findFoodByNormalized(tag)
        override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
        override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
    }

    private fun offFood(
        id: String,
        name: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        aliases: List<String> = emptyList(),
    ) = GlobalFoodEntity(
        foodId = id,
        name = name,
        normalizedName = FoodIndex.normalizeSearch(name),
        aliasesJson = Json.encodeToString(aliases),
        calories = calories,
        protein = protein,
        carbs = carbs,
        fats = fats,
        source = "OFF",
        sourcePriority = 40,
    )

    private val adversarialOff = listOf(
        offFood("off_pizza_tomate", "Pizza de Tomate", 266.0, 11.0, 33.0, 10.0, listOf("tomato pizza")),
        offFood("off_salsa_tomate", "Salsa de Tomate Industrial", 80.0, 1.5, 18.0, 0.5),
        offFood("off_ketchup", "Ketchup de Tomate", 100.0, 1.0, 27.0, 0.1),
        offFood("off_banana_bread", "Banana Bread", 326.0, 4.0, 54.0, 10.0, listOf("pan de platano")),
        offFood("off_caldo_pollo", "Caldo de Pollo", 12.0, 1.0, 1.0, 0.4, listOf("chicken broth")),
    )

    private suspend fun resolve(description: String, withOff: Boolean = true): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(
            globalFoods = if (withOff) adversarialOff else emptyList(),
            staticFoods = foods,
            staticAliases = FOOD_ALIASES,
        )
        val allFoods = foods + adversarialOff.map { it.toFoodItem() }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val parsed = parseMealDescription(description)
        val (tags, _) = TagResolver(RealPort(resolver, allFoods)).resolveAll(parsed)
        return tags
    }

    @Test
    fun `tomate no se confunde con pizza salsa ketchup ni banana`() = runBlocking {
        val tags = resolve("tomate")
        assertEquals("gen026", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertTrue(tags.single().isResolved)
        assertFalse(tags.single().hasMaterialQuestion())
        assertFalse(tags.single().foodItem!!.name.contains("pizza", ignoreCase = true))
        assertFalse(tags.single().foodItem!!.name.contains("ketchup", ignoreCase = true))
        assertFalse(tags.single().foodItem!!.name.contains("banana", ignoreCase = true))
    }

    @Test
    fun `platano no se resuelve a banana bread`() = runBlocking {
        val tags = resolve("plátano")
        assertEquals("gen002", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertFalse(tags.single().foodItem!!.name.contains("bread", ignoreCase = true))
    }

    @Test
    fun `pollo no se resuelve a caldo de pollo`() = runBlocking {
        val tags = resolve("pollo")
        val id = tags.single().foodItem?.id
        assertTrue("pollo debe ser pechuga, fue $id ${tags.single().foodItem?.name}", id in setOf("gen003", "gen004"))
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertNotNull(tags.single().loggedFood)
        assertFalse(tags.single().foodItem!!.name.contains("caldo", ignoreCase = true))
        assertFalse(tags.single().hasMaterialQuestion())
    }

    @Test
    fun `arroz asume cocido y se puede guardar`() = runBlocking {
        val tags = resolve("arroz")
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertTrue(tags.single().isResolved)
        assertNotNull(tags.single().loggedFood)
        assertTrue(tags.single().statusText.contains("Asumí", ignoreCase = true))
        assertFalse(tags.single().hasMaterialQuestion())
        assertTrue(
            tags.single().foodItem?.name?.contains("cocid", ignoreCase = true) == true ||
                tags.single().stateAssumed,
        )
    }

    @Test
    fun `atun desnudo sigue pidiendo confirmacion`() = runBlocking {
        val tags = resolve("atún")
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tags.single().resolutionStatus)
        assertTrue(tags.single().hasMaterialQuestion())
    }

    @Test
    fun `torta sigue siendo alias de aproximacion`() = runBlocking {
        val tags = resolve("torta")
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tags.single().resolutionStatus)
        assertFalse(tags.single().isResolved)
    }

    @Test
    fun `pechuga cocida explicita no cambia macros de precision`() = runBlocking {
        val tags = resolve("200 g pechuga cocida")
        assertEquals("gen004", tags.single().foodItem?.id)
        assertEquals(200.0, tags.single().amountGrams ?: 0.0, 0.01)
        assertEquals(64.2, tags.single().loggedFood!!.protein, 0.15)
    }

    @Test
    fun `arroz con pollo se separa en dos alimentos`() = runBlocking {
        val parsed = parseMealDescription("arroz con pollo")
        assertEquals(listOf("arroz", "pollo"), parsed.items.map { it.tag })
        val tags = resolve("arroz con pollo")
        assertEquals(2, tags.size)
        assertTrue(tags.any { it.tag == "arroz" })
        assertTrue(tags.any { it.tag == "pollo" })
    }

    @Test
    fun `arroz con leche sigue siendo un plato`() {
        val parsed = parseMealDescription("arroz con leche")
        assertEquals(1, parsed.items.size)
        assertEquals("arroz con leche", parsed.items.single().tag)
    }

    @Test
    fun `tomate lechuga y pollo plancha se separan con coccion por fragmento`() = runBlocking {
        val parsed = parseMealDescription("tomate, lechuga y 150g pollo a la plancha")
        assertEquals(3, parsed.items.size)
        val pollo = parsed.items.single { it.tag.contains("pollo") }
        assertEquals(CookingMethod.PLANCHA, pollo.cookingMethod)
        assertEquals(150.0, pollo.amountGrams ?: 0.0, 0.01)
        assertTrue(parsed.items.any { it.tag == "tomate" })
        assertTrue(parsed.items.any { it.tag.contains("lechuga") })
        val tags = resolve("tomate, lechuga y 150g pollo a la plancha")
        assertEquals("gen026", tags.first { it.tag == "tomate" }.foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.first { it.tag == "tomate" }.resolutionStatus)
    }

    @Test
    fun `familia tomate no es salsa ni ketchup`() {
        assertEquals("tomate", FoodIdentity.familyFor("tomate"))
        assertEquals("salsa_de_tomate", FoodIdentity.familyFor("salsa de tomate"))
        assertEquals("ketchup", FoodIdentity.familyFor("ketchup"))
        assertTrue(FoodIdentity.isPlainSimpleFood("tomate", "Tomate"))
        assertFalse(FoodIdentity.isPlainSimpleFood("tomate", "Pizza de Tomate"))
        assertFalse(FoodIdentity.isCompoundProduct("Tomate"))
        assertTrue(FoodIdentity.isCompoundProduct("Pizza de Tomate"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
}
