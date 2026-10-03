package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N11: a flavoured mention that has no candidate of its own ("helado de vainilla y chocolate", "yogur de frutilla") is the food
 * it is a flavour of: its head noun's row, with that row's own serving. Read whole, the flavour word made a chocolate square of it
 * (25 g, an estimate to review) although the catalog has a generic "Helado".
 */
class FlavouredMentionHeadNounTest {

    private class Port(
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

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao

    private fun resolverOver(foods: List<FoodItem>, aliases: Map<String, String> = emptyMap()): SmartFoodResolver {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = aliases)
        return SmartFoodResolver(noOpNutritionDao(), index, null)
    }

    private fun catalogResolver() = resolverOver(buildFoodDatabase(), FOOD_ALIASES)

    private suspend fun resolveTags(description: String): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        return TagResolver(Port(resolverOver(foods, FOOD_ALIASES), foods)).resolveAll(parseMealDescription(description)).first
    }

    private fun curated(id: String, name: String, vararg aliases: String) = FoodItem(
        id = id, name = name, brand = "Genérico", servingSize = 100.0, unit = "g",
        calories = 206.0, protein = 4.0, carbs = 25.0, fats = 10.0,
        nutritionBasis = "PER_100G_AS_SOLD", source = "KPKN Curated (test)", sourceRecordId = id,
        searchAliases = aliases.toList(),
    )

    // ─── Recognition ────────────────────────────────────────────────────────────────

    @Test
    fun `a flavoured mention is a food that comes in flavours, de, and its flavours`() {
        val pair = requireNotNull(FoodIdentity.flavouredMention("helado de vainilla y chocolate"))
        assertEquals("helado", pair.head)
        assertEquals(listOf("vainilla", "chocolate"), pair.flavours)
        val accented = requireNotNull(FoodIdentity.flavouredMention("Helado de lúcuma"))
        assertEquals("helado", accented.head)
        assertEquals(listOf("lucuma"), accented.flavours)
        assertEquals("helados", requireNotNull(FoodIdentity.flavouredMention("helados de frutilla")).head)
        assertEquals("yogur", requireNotNull(FoodIdentity.flavouredMention("yogur de durazno")).head)
        assertEquals("torta", requireNotNull(FoodIdentity.flavouredMention("torta de chocolate")).head)
        assertEquals(listOf("platano"), requireNotNull(FoodIdentity.flavouredMention("queque de plátano")).flavours)
        // the flavours of a cake, and a flavour in the plural
        assertEquals(listOf("zanahoria"), requireNotNull(FoodIdentity.flavouredMention("queque de zanahoria")).flavours)
        assertEquals(listOf("nuez"), requireNotNull(FoodIdentity.flavouredMention("kuchen de nueces")).flavours)
        assertEquals(listOf("almendra", "chocolate"), requireNotNull(FoodIdentity.flavouredMention("helado de almendras y chocolate")).flavours)
    }

    @Test
    fun `what is not a flavour after de is not a flavoured mention`() {
        for (text in listOf(
            "crema de zapallo", "helado", "helado de", "helado de vainilla con salsa", "ensalada de repollo", "pan de chocolate",
            "jugo de naranja", "barra de chocolate", "paleta de helado", "helado vainilla", "de vainilla", "torta de jamon",
            "crema de zanahoria", "queque de zanahoria con crema",
        )) {
            assertNull(text, FoodIdentity.flavouredMention(text))
        }
    }

    // ─── The resolver ───────────────────────────────────────────────────────────────

    @Test
    fun `a flavoured mention with no row of its own resolves through its head noun`() = runBlocking {
        val resolver = catalogResolver()
        val pair = resolver.resolve("helado de vainilla y chocolate")
        assertEquals("helado", pair.headNoun)
        assertEquals("gen193", pair.candidates.first().foodId)
        assertEquals(SmartFoodResolver.Decision.AUTO_SELECT, pair.decision)
        assertEquals("helado de vainilla y chocolate", pair.query)
        assertEquals("gen193", resolver.resolve("helado de lúcuma").candidates.first().foodId)
        val plural = resolver.resolve("helados de frutilla")
        assertEquals("helado", plural.headNoun)
        assertEquals("gen193", plural.candidates.first().foodId)
        val yogur = resolver.resolve("yogur de frutilla")
        assertEquals("yogur", yogur.headNoun)
        assertTrue(yogur.candidates.first().name, yogur.candidates.first().name.startsWith("Yogurt"))
        assertEquals(SmartFoodResolver.Decision.AUTO_SELECT, yogur.decision)
        assertEquals("yogur", resolver.resolve("yogures de frutilla").headNoun)
        // a cake and a flavour in the plural
        val queque = resolver.resolve("queque de zanahoria")
        assertEquals("queque", queque.headNoun)
        assertEquals("gen152", queque.candidates.first().foodId)
        assertEquals("gen190", resolver.resolve("kuchen de nueces").candidates.first().foodId)
    }

    @Test
    fun `a head with no row of its own, or that is bread only by approximation, gives nothing`() = runBlocking {
        val resolver = catalogResolver()
        for (text in listOf("torta de chocolate", "brownie de chocolate", "crema de zapallo", "galletas de vainilla")) {
            val result = resolver.resolve(text)
            assertNull(text, result.headNoun)
            assertTrue(text, result.candidates.all { it.foodId.startsWith("heuristic_") })
        }
    }

    @Test
    fun `the whole phrase wins when it has a candidate of its own`() = runBlocking {
        val generic = curated("t_helado", "Helado", "helado", "helados")
        val vanilla = curated("t_helado_vainilla", "Helado de vainilla", "helado de vainilla")
        val resolver = resolverOver(listOf(generic, vanilla))
        val whole = resolver.resolve("helado de vainilla")
        assertNull(whole.headNoun)
        assertEquals("t_helado_vainilla", whole.candidates.first().foodId)
        // a flavour that has no row of its own is the generic one
        val other = resolver.resolve("helado de frutilla")
        assertEquals("helado", other.headNoun)
        assertEquals("t_helado", other.candidates.first().foodId)
    }

    @Test
    fun `a row of the head that names another flavour is no stand-in`() = runBlocking {
        val resolver = resolverOver(listOf(curated("t_alfajor_chocolate", "Alfajor de chocolate", "alfajor", "alfajores")))
        assertNull(resolver.resolve("alfajor de manjar").headNoun)
        assertNull(resolver.resolve("alfajor de chocolate").headNoun)
        assertEquals("t_alfajor_chocolate", resolver.resolve("alfajor de chocolate").candidates.first().foodId)
        // a mention that names that flavour among others is still its kind of alfajor
        val both = resolver.resolve("alfajor de chocolate y manjar")
        assertEquals("alfajor", both.headNoun)
        assertEquals("t_alfajor_chocolate", both.candidates.first().foodId)
    }

    @Test
    fun `a plain mention and a head of its own are untouched`() = runBlocking {
        val resolver = catalogResolver()
        assertNull(resolver.resolve("helado").headNoun)
        assertEquals("gen193", resolver.resolve("helado").candidates.first().foodId)
        assertNull(resolver.resolve("queque de vainilla").headNoun)
        assertNull(resolver.resolve("pastel de choclo").headNoun)
    }

    // ─── The tags ───────────────────────────────────────────────────────────────────

    @Test
    fun `helado de vainilla y chocolate is the Helado row with its own serving`() = runBlocking {
        val tag = resolveTags("helado de vainilla y chocolate").single()
        assertEquals("gen193", tag.foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertEquals(AmountIntent.UNSPECIFIED, tag.amountIntent)
        // the normal portion of the row (that of a plain "helado"), not the 25 g of a chocolate square
        val grams = tag.amountGrams ?: 0.0
        assertEquals(resolveTags("helado").single().amountGrams ?: 0.0, grams, 0.001)
        assertTrue("$grams g", grams > 25.0)
        val row = requireNotNull(tag.foodItem)
        assertEquals(row.calories * grams / row.servingSize, tag.loggedFood?.calories ?: 0.0, 1.0)
        assertFalse(tag.hasMaterialQuestion())
        assertTrue(tag.statusText, tag.statusText.contains("Helado"))
        // the person's words stay on the tag; the identity the card reads is the head
        assertEquals("helado de vainilla y chocolate", tag.tag)
        assertEquals("helado", tag.foodQuery)
        assertNotNull(tag.loggedFood)
    }

    @Test
    fun `other flavours of a food with a row are that row too`() = runBlocking {
        val lucuma = resolveTags("helado de lúcuma").single()
        assertEquals("gen193", lucuma.foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, lucuma.resolutionStatus)
        assertEquals(resolveTags("helado").single().amountGrams ?: 0.0, lucuma.amountGrams ?: 0.0, 0.001)
        val yogur = resolveTags("yogur de frutilla").single()
        assertTrue("${yogur.foodItem?.name}", yogur.foodItem?.name?.startsWith("Yogurt") == true)
        assertEquals(FoodResolutionStatus.AUTO, yogur.resolutionStatus)
        // the household unit of the row (a pot), as "un yogur" weighs; not the bowl a lone "yogur" is read as from its context
        assertEquals(resolveTags("un yogur").single().amountGrams ?: 0.0, yogur.amountGrams ?: 0.0, 0.001)
        // the same serving as the plain food
        assertEquals(resolveTags("helado").single().amountGrams, resolveTags("helado de chocolate").single().amountGrams)
        // a cake: the serving of its row (a piece of 70 g), not the 350 g of a plate
        val queque = resolveTags("queque de zanahoria").single()
        assertEquals("gen152", queque.foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, queque.resolutionStatus)
        assertEquals(resolveTags("queque").single().amountGrams ?: 0.0, queque.amountGrams ?: 0.0, 0.001)
        assertTrue("${queque.amountGrams} g", (queque.amountGrams ?: 0.0) < 200.0)
    }

    @Test
    fun `a cake with no row stays an estimate to review`() = runBlocking {
        val tag = resolveTags("torta de chocolate").single()
        assertNull(tag.foodItem)
        assertEquals(FoodResolutionStatus.NEEDS_REVIEW, tag.resolutionStatus)
        // the dessert profile (310 kcal per 100 g) over whatever portion the dish gets
        val grams = tag.amountGrams ?: 0.0
        assertTrue("$grams g", grams > 0.0)
        assertEquals(3.10 * grams, tag.loggedFood?.calories ?: 0.0, 1.0)
    }

    @Test
    fun `flavoured mentions keep their place among other mentions`() = runBlocking {
        val tags = resolveTags("un yogur de frutilla y un helado de vainilla y chocolate")
        assertEquals(listOf("yogur de frutilla", "helado de vainilla y chocolate"), tags.map { it.tag })
        assertEquals(listOf(FoodResolutionStatus.AUTO, FoodResolutionStatus.AUTO), tags.map { it.resolutionStatus })
        assertEquals("gen193", tags.last().foodItem?.id)
        assertEquals(resolveTags("helado").single().amountGrams ?: 0.0, tags.last().amountGrams ?: 0.0, 0.001)
    }
}
