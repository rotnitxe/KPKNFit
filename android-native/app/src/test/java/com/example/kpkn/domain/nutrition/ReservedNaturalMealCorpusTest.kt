package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer

/**
 * Eight original semantic oracles recorded before the first execution, independently of
 * parser output and dataset labels. These are targeted challenge cases, not an
 * estimate of population accuracy. Unsupported reference resolution may ask a
 * material question on that mention; it must never silently replace or lose it.
 */
class ReservedNaturalMealCorpusTest {
    private suspend fun resolve(text: String): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex().apply { build(emptyList(), foods, FOOD_ALIASES) }
        val dao = java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader, arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao
        val smart = SmartFoodResolver(dao, index)
        val port = object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                smart.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String) = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String) = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
        val parsed = parseMealDescription(text)
        return TagResolver(port).resolveAll(parsed).first.map { NutritionInterpretationBridge.enrich(it, parsed) }
    }

    private fun folded(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun query(tag: ResolvedTag) = folded(tag.foodQuery.ifBlank { tag.tag })
    private fun active(tags: List<ResolvedTag>) = tags.filterNot { it.isExcluded }
    private fun questions(tag: ResolvedTag) = tag.interpretationV2!!.pendingQuestions
    private fun trace(tags: List<ResolvedTag>) = tags.joinToString { "${it.tag}: id=${it.foodItem?.id}, g=${it.amountGrams}, excluded=${it.isExcluded}, questions=${it.interpretationV2?.pendingQuestions}" }

    private fun assertMaterialReview(tag: ResolvedTag) {
        assertTrue("Reference must be reviewed: ${trace(listOf(tag))}", questions(tag).any { it.material })
        assertFalse(tag.interpretationV2!!.canFinalize())
    }

    /** Follow-up regression from independent review; not part of the original eight. */
    @Test fun `lexicalized dessert never becomes automatically accepted milk`() = runBlocking {
        for (input in listOf("tres leches", "100 g tres leches")) {
            val tags = active(resolve(input))
            assertEquals(trace(tags), 1, tags.size)
            val dessert = tags.single()
            assertEquals("tres leches", query(dessert))
            assertNotEquals("gen016", dessert.foodItem?.id)
            if (dessert.foodItem == null) assertMaterialReview(dessert)
            else assertTrue(folded(dessert.foodItem!!.name).contains("tres leches"))
            if (input.startsWith("100")) assertEquals(100.0, dessert.amountGrams!!, 0.01)
        }
    }

    /** Follow-up regression for the household unit seen in on-device QA. */
    @Test fun `whey scoop keeps its measured mass through the resolved snapshot`() = runBlocking {
        for ((input, grams) in listOf("un scoop whey" to 30.0, "dos scoops whey" to 60.0, "medio scoop whey" to 15.0)) {
            val tag = active(resolve(input)).single()
            assertEquals(input, "gen105", tag.foodItem?.id)
            assertEquals(grams, tag.amountGrams!!, 0.1)
            assertEquals(grams, tag.interpretationV2!!.observedGrams!!, 0.1)
            assertEquals(grams, tag.loggedFood!!.amount, 0.1)
            assertEquals("scoop", tag.interpretationV2!!.declaredUnitId)
        }
    }

    @Test fun `quantity correction keeps the food and replaces its count`() = runBlocking {
        // "uno" refers to the preceding eggs; pan is a separate habitual portion.
        val tags = active(resolve("Dos huevos, perdón, uno, con pan"))
        assertEquals(trace(tags), 2, tags.size)
        val egg = tags.single { query(it).contains("huevo") }
        assertEquals("gen007", egg.foodItem?.id)
        assertEquals(1.0, egg.quantity, 0.01)
        assertTrue(trace(tags), egg.amountGrams!! in 40.0..70.0)
        assertTrue(tags.any { query(it) == "pan" })
    }

    @Test fun `narrative replacement does not add the discarded side dish`() = runBlocking {
        val tags = active(resolve("Almorcé pollo con arroz, mejor dicho con fideos"))
        assertEquals(trace(tags), 2, tags.size)
        assertTrue(tags.any { query(it).contains("pollo") })
        assertTrue(tags.any { query(it).contains("fideo") || query(it).contains("pasta") })
        assertFalse(tags.any { query(it).contains("arroz") || query(it).contains("mejor dicho") })
        assertTrue(tags.all { (it.amountGrams ?: 0.0) > 0.0 })
    }

    @Test fun `negative meal report stops at the next positive clause`() = runBlocking {
        val all = resolve("No comí pollo ni arroz; cené una tortilla")
        val tags = active(all)
        assertEquals(trace(all), 1, tags.size)
        assertTrue(query(tags.single()).contains("tortilla"))
        assertTrue(all.any { it.isExcluded && query(it).contains("pollo") })
        assertTrue(all.any { it.isExcluded && query(it).contains("arroz") })
        // A composition question for tortilla is valid; substituting bread is not.
        assertFalse(tags.single().foodItem?.id == "gen019")
    }

    @Test fun `repeated composite dishes keep different ingredient constraints`() = runBlocking {
        val tags = active(resolve("Un completo sin mayonesa y otro completo con mayonesa"))
        val completos = tags.filter { query(it).contains("completo") }
        assertEquals(trace(tags), 2, completos.size)
        assertTrue(completos[0].excludedIngredients.contains("mayonesa"))
        assertFalse(completos[1].excludedIngredients.contains("mayonesa"))
        // The modified recipe must not silently use an unmodified whole dish.
        if (completos[0].foodItem != null) {
            assertTrue(folded(completos[0].foodItem!!.name).contains("sin mayonesa"))
        } else assertMaterialReview(completos[0])
        val additions = tags.filterNot { it in completos }
        assertTrue(trace(tags), additions.all { query(it).contains("mayonesa") })
        // A separately counted topping may accompany an unresolved recipe, but
        // cannot also be included in a verified complete-dish profile.
        if (additions.isNotEmpty() && completos[1].foodItem != null) {
            assertTrue(folded(completos[1].foodItem!!.name).contains("sin mayonesa"))
        }
    }

    @Test fun `sugar free product can still have explicitly added sugar`() = runBlocking {
        val tags = active(resolve("Yogurt sin azúcar con 10 g de azúcar"))
        assertEquals(trace(tags), 2, tags.size)
        val yogurt = tags.single { query(it).contains("yogurt") || query(it).contains("yogur") }
        assertTrue(query(yogurt).contains("sin azucar"))
        val sugar = tags.single { query(it) == "azucar" }
        assertEquals(10.0, sugar.amountGrams!!, 0.01)
        assertFalse(sugar.isExcluded)
        assertTrue(sugar.excludedIngredients.isEmpty())
    }

    @Test fun `elliptical measure is resolved or questioned on its own mention`() = runBlocking {
        val tags = active(resolve("Medio vaso de jugo y medio de leche"))
        assertEquals(trace(tags), 2, tags.size)
        assertTrue(query(tags[0]).contains("jugo"))
        val milk = tags[1]
        assertTrue(query(milk).contains("leche"))
        // Vaso is a configured household vessel, independently of the 240 ml cup.
        val vesselMl = SubjectivePortionEngine.currentUtensilOverrides()["vaso"]
            ?: SubjectivePortionEngine.UTENSIL_DEFAULTS.getValue("vaso")
        val resolved = milk.unitId in setOf("glass", "vaso", "medio_vaso") &&
            kotlin.math.abs(milk.amountGrams!! - vesselMl * 0.5 * 1.03) <= 0.1
        if (!resolved) {
            assertMaterialReview(milk)
            assertTrue(questions(milk).any { it is ClarificationRequest.Portion || it is ClarificationRequest.Identity })
        }
    }

    @Test fun `anaphoric second helping is retained with its uncertainty`() = runBlocking {
        val tags = active(resolve("Un plato de lentejas; luego otra porción pequeña de las mismas"))
        assertEquals(trace(tags), 2, tags.size)
        assertTrue(query(tags[0]).contains("lenteja"))
        val second = tags[1]
        val bound = query(second).contains("lenteja") && second.foodItem?.id == tags[0].foodItem?.id
        if (bound) {
            assertTrue(second.amountGrams!! > 0.0 && second.amountGrams!! < tags[0].amountGrams!!)
        } else {
            assertTrue(query(second).contains("las mismas"))
            assertNull("Unbound reference must not silently become another food", second.foodItem)
            assertMaterialReview(second)
            assertTrue(questions(second).any { it is ClarificationRequest.Identity })
        }
    }

    @Test fun `excluded spread attaches to bread without changing the soup`() = runBlocking {
        val all = resolve("Una sopa de verduras y pan, sin mantequilla")
        val tags = active(all)
        assertEquals(trace(all), 2, tags.size)
        val soup = tags.single { query(it).contains("sopa") }
        val bread = tags.single { query(it) == "pan" }
        assertTrue(soup.excludedIngredients.isEmpty())
        assertEquals(setOf("mantequilla"), bread.excludedIngredients)
        assertTrue(all.any { it.isExcluded && query(it).contains("mantequilla") })
        assertFalse(tags.any { query(it) == "mantequilla" })
    }
}
