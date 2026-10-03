package com.example.kpkn.data.food

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.TextKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * WP-D1 + WP-S11: the rows of the static catalog this work package added or fixed (gen154-gen198, gen084, gen006) and the redirect
 * of the id it deleted (gen136). The audit's complaint was a catalog whose rows state no origin and contradict each other; these are
 * the invariants of the rows that now do: a declared source and record, a per-100 g basis, macros that add up, no name a row shares
 * with another, aliases that name ONE food, and recipes that are what their named profiles say.
 */
class StaticCatalogContentTest {

    private val all: List<FoodItem> = GENERIC_FOODS + CHILEAN_FOODS

    /** The rows WP-D1 added. */
    private val added: List<FoodItem> = all.filter { it.id.startsWith("gen") && it.id.length == 6 && it.id >= "gen154" }

    /** The rows whose content this work package touched. */
    private val touched: List<FoodItem> = added + listOfNotNull(findStaticFoodById("gen006"), findStaticFoodById("gen084"))

    private val alcoholIds = setOf("gen196", "gen197", "gen198")

    @Test
    fun `WP-D1 added the 45 rows gen154 to gen198`() {
        assertEquals((154..198).map { "gen$it" }, added.map { it.id })
    }

    // 1. Provenance

    @Test
    fun `every row declares its origin its basis and a serving`() {
        touched.forEach { food ->
            val label = "${food.id} ${food.name}"
            assertTrue("$label: source", !food.source.isNullOrBlank())
            assertTrue("$label: sourceRecordId", !food.sourceRecordId.isNullOrBlank())
            assertTrue("$label: PER_100G basis, was ${food.nutritionBasis}", food.nutritionBasis.startsWith("PER_100G"))
            assertTrue("$label: category", !food.category.isNullOrBlank())
            assertTrue("$label: serving ${food.servingSize}", food.servingSize.isFinite() && food.servingSize > 0.0)
            assertTrue("$label: unit ${food.unit}", food.unit in setOf("g", "ml", "u"))
            assertEquals("$label: brand", "Genérico", food.brand)
            assertTrue("$label: has a search alias", food.searchAliases.isNotEmpty())
        }
    }

    @Test
    fun `a cooked row declares the cooked state and a state in the name is the declared one`() {
        touched.forEach { food ->
            val named = FoodIdentity.stateFor(food.name)
            if (named != FoodState.UNKNOWN) assertEquals("${food.id} ${food.name}", named.name, food.foodState)
            if (food.nutritionBasis == "PER_100G_COOKED") assertEquals("${food.id} ${food.name}", "COOKED", food.foodState)
        }
        assertEquals("RAW", findStaticFoodById("gen166")?.foodState)
    }

    @Test
    fun `no source reads as a supermarket SKU or as another provenance`() {
        // HouseholdPortions.isGlobalSku and the FoodSource mapping read the TEXT of source, ignoring case: "coffeecake" holds "off".
        val forbidden = listOf("OFF", "USDA", "SR", "BRANDED", "FNDDS", "CUSTOM", "HEURISTIC", "DATASET")
        touched.forEach { food ->
            val source = food.source.orEmpty().uppercase()
            assertFalse("${food.id} source \"${food.source}\" is a supermarket SKU", HouseholdPortions.isGlobalSku(food))
            forbidden.forEach { assertFalse("${food.id} source \"${food.source}\" holds $it", it in source) }
        }
    }

    @Test
    fun `the recipe dishes say so and stay verified`() {
        val dishes = touched.filter { it.source == "RECIPE_ESTIMATE" }
        assertEquals(listOf("gen183", "gen184", "gen185", "gen186", "gen187", "gen188", "gen196"), dishes.map { it.id })
        dishes.forEach { dish ->
            assertTrue("${dish.id}: the recipe is the record id", dish.sourceRecordId.orEmpty().startsWith("receta: "))
            // A flag would hide the row from the search and from the identity gates: NutrientBasis.isVerified needs it empty.
            assertTrue("${dish.id}: no quality flag", dish.qualityFlags.isEmpty())
            assertTrue("${dish.id}: verified", NutrientBasis.isVerified(dish))
        }
    }

    // 2. Macros

    @Test
    fun `macros are plausible and add up to the energy`() {
        touched.forEach { food ->
            assertTrue("${food.id} ${food.name}: plausible", NutrientBasis.isVerified(food))
            val atwater = food.protein * 4.0 + food.carbs * 4.0 + food.fats * 9.0
            val gap = food.calories - atwater
            when {
                // Ethanol is 7 kcal/g and has no macro column: the gap is the alcohol, 0 to 35 g per 100 g (a 40 % spirit holds ~32).
                food.id in alcoholIds -> assertTrue("${food.id}: ethanol ${gap / 7.0} g", gap / 7.0 in 0.0..35.0)
                // Chili powder is 35 % fibre: its carbohydrate column counts fibre that gives no energy.
                food.id == "gen176" -> assertTrue("${food.id}: gap $gap", gap in -110.0..0.0)
                else -> assertTrue(
                    "${food.id} ${food.name}: kcal ${food.calories} vs 4P+4C+9G $atwater",
                    abs(gap) <= maxOf(16.0, 0.20 * food.calories),
                )
            }
        }
    }

    @Test
    fun `macros never exceed 100 g per 100 g`() {
        touched.forEach { food ->
            assertTrue(food.id, food.protein + food.carbs + food.fats <= 100.0 + 1e-9)
            listOf(food.calories, food.protein, food.carbs, food.fats).forEach { assertTrue(food.id, it.isFinite() && it >= 0.0) }
        }
    }

    // 3. Names and aliases

    @Test
    fun `no name repeats with the same state`() {
        val clash = all.groupBy { TextKeys.normalize(it.name) to FoodIdentity.stateFor(it) }.filterValues { it.size > 1 }
        // Pan Integral (gen020 and gen133) is an older twin that this work package did not add (kept on purpose, see
        // StaticCatalogProvenanceTest); WP-S10 merged the other one, Charquicán (cl015 and cl039).
        val older = setOf("pan integral")
        assertTrue(
            "duplicate (name, state): ${clash.mapValues { (_, rows) -> rows.map { it.id } }}",
            clash.keys.all { (name, _) -> name in older },
        )
        touched.forEach { food -> assertTrue(food.id, clash.none { (_, rows) -> food in rows }) }
    }

    @Test
    fun `every alias of a row resolves to that row alone`() {
        touched.forEach { food ->
            (food.searchAliases + food.name).forEach { alias ->
                assertEquals("${food.id} alias \"$alias\"", food.id, staticFoodForAlias(alias)?.id)
                assertEquals("${food.id} alias \"$alias\" by exact lookup", food.id, findFoodExactByNormalized(alias)?.id)
                // No other row lists the same key: the answer never depends on the order of the catalog.
                val key = foodAliasKey(alias)
                val owners = all.filter { other -> (listOf(other.name) + other.searchAliases).any { foodAliasKey(it) == key } }
                assertEquals("\"$alias\" is claimed by ${owners.map { it.id }}", listOf(food.id), owners.map { it.id })
            }
        }
    }

    @Test
    fun `an alias of the table that has a row of its own names that row`() {
        listOf("gauda", "gouda", "queso gouda").forEach { alias ->
            assertEquals(alias, "gen157", FOOD_ALIAS_IDS[foodAliasKey(alias)])
            assertNotEquals(alias, "gen047", staticFoodForAlias(alias)?.id)
        }
        assertEquals("gen188", FOOD_ALIAS_IDS[foodAliasKey("ceviche")])
        assertEquals("gen188", FOOD_ALIAS_IDS[foodAliasKey("cebiche")])
        assertEquals("gen179", FOOD_ALIAS_IDS[foodAliasKey("cereal")])
        // They were approximations (the nearest food); with a row of their own they are the food.
        listOf("ceviche", "cebiche", "cereal", "cereales").forEach { assertFalse(it, isApproximationAlias(it)) }
        // The others still are: nobody wrote "galletas" meaning bread.
        listOf("galletas", "torta", "ensalada").forEach { assertTrue(it, isApproximationAlias(it)) }
    }

    @Test
    fun `gouda is not cheddar and quesillo is not the Nicaraguan cheese`() {
        val gouda = checkNotNull(findStaticFoodById("gen157"))
        assertEquals("171241", gouda.sourceRecordId)
        assertTrue("gouda ${gouda.calories}", gouda.calories in 340.0..370.0)
        assertNotEquals(checkNotNull(findStaticFoodById("gen047")).protein, gouda.protein, 0.0)
        assertEquals("gen158", staticFoodForAlias("quesillo")?.id)
    }

    // 4. Fixes of existing rows

    @Test
    fun `queso fresco is no longer the 98 kcal cottage cheese`() {
        val fresco = checkNotNull(findStaticFoodById("gen084"))
        val cottage = checkNotNull(findStaticFoodById("gen018"))
        assertEquals(98.0, cottage.calories, 0.0)
        assertTrue("queso fresco ${fresco.calories} kcal", fresco.calories in 130.0..200.0)
        assertEquals("gen084", staticFoodForAlias("queso fresco")?.id)
        assertEquals("gen084", staticFoodForAlias("queso blanco")?.id)
    }

    @Test
    fun `there is one brown rice and the deleted id still answers`() {
        val rice = checkNotNull(findStaticFoodById("gen006"))
        assertEquals(123.0, rice.calories, 0.0)
        assertEquals(2.7, rice.protein, 0.0)
        assertEquals(26.0, rice.carbs, 0.0)
        assertEquals(0.9, rice.fats, 0.0)
        assertEquals("169704", rice.sourceRecordId)
        assertEquals(1, all.count { TextKeys.normalize(it.name) == "arroz integral cocido" })
        assertTrue(all.none { it.id == "gen136" })
        // The learned resolutions and the templates that saved "gen136" keep their food.
        assertEquals(mapOf("gen136" to "gen006", "cl039" to "cl015"), LEGACY_FOOD_ID_REDIRECTS)
        assertEquals("gen006", findStaticFoodById("gen136")?.id)
        assertEquals("gen006", resolveLegacyFoodId("gen136"))
        assertEquals("gen004", resolveLegacyFoodId("gen004"))
        assertNull(findStaticFoodById("gen999"))
        // A redirect points at a live row and never shadows one.
        LEGACY_FOOD_ID_REDIRECTS.forEach { (old, current) ->
            assertNotNull("$old -> $current", all.firstOrNull { it.id == current })
            assertTrue("$old is still a live id", all.none { it.id == old })
        }
        listOf("arroz integral", "arroz integral cocido").forEach { assertEquals(it, "gen006", staticFoodForAlias(it)?.id) }
    }

    @Test
    fun `champinon and the water brand are aliases of rows that already existed`() {
        assertEquals("gen038", staticFoodForAlias("champiñón")?.id)
        assertEquals("gen038", staticFoodForAlias("hongos")?.id)
        assertEquals("gen143", staticFoodForAlias("cachantún")?.id)
        assertEquals("gen151", staticFoodForAlias("cachantún con gas")?.id)
        assertEquals("gen138", staticFoodForAlias("galleta de agua")?.id)
        assertEquals("gen138", staticFoodForAlias("galletas de soda")?.id)
    }

    // 5. Recipes are what their named profiles say

    private data class Part(val profile: String, val percent: Double)

    /** Per 100 g (kcal, P, C, F) of the profiles that are not rows of the catalog: USDA FDC records, cited in the recipe. */
    private val fdcProfiles = mapOf(
        "FDC173713" to doubleArrayOf(90.0, 18.31, 0.0, 1.31), // Fish, whiting, mixed species, raw
        "FDC167747" to doubleArrayOf(22.0, 0.35, 6.9, 0.24), // Lemon juice, raw
        "FDC174815" to doubleArrayOf(231.0, 0.0, 0.0, 0.0), // Alcoholic beverage, distilled, 80 proof
        "agua" to doubleArrayOf(0.0, 0.0, 0.0, 0.0),
    )

    private val recipes: Map<String, List<Part>> = mapOf(
        "gen183" to listOf(Part("cl010", 37.0), Part("gen093", 31.5), Part("gen026", 18.5), Part("gen173", 11.0), Part("gen175", 2.0)),
        "gen184" to listOf(Part("cl010", 43.5), Part("gen093", 39.1), Part("gen157", 17.4)),
        "gen185" to listOf(Part("cl010", 44.4), Part("gen004", 38.9), Part("gen065", 16.7)),
        "gen186" to listOf(
            Part("agua", 45.0), Part("gen093", 12.0), Part("gen021", 20.0), Part("gen072", 6.0), Part("gen071", 5.0),
            Part("gen005", 5.0), Part("gen024", 3.0), Part("gen055", 3.0), Part("gen027", 1.0),
        ),
        "gen187" to listOf(
            Part("gen093", 25.0), Part("gen021", 40.0), Part("gen027", 12.0), Part("agua", 17.0), Part("gen099", 3.0), Part("gen036", 3.0),
        ),
        "gen188" to listOf(Part("FDC173713", 68.0), Part("gen027", 18.0), Part("FDC167747", 12.0), Part("agua", 2.0)),
        "gen196" to listOf(Part("FDC174815", 22.0), Part("gen146", 78.0)),
    )

    private fun profile(name: String): DoubleArray =
        fdcProfiles[name]
            ?: checkNotNull(findStaticFoodById(name)) { "recipe profile $name" }.let { doubleArrayOf(it.calories, it.protein, it.carbs, it.fats) }

    @Test
    fun `a recipe dish is the weighted sum of its named profiles`() {
        recipes.forEach { (id, parts) ->
            val dish = checkNotNull(findStaticFoodById(id))
            assertEquals("$id shares add to 100 %", 100.0, parts.sumOf { it.percent }, 0.11)
            val sum = DoubleArray(4)
            parts.forEach { part -> profile(part.profile).forEachIndexed { i, value -> sum[i] += value * part.percent / 100.0 } }
            assertEquals("$id kcal", sum[0], dish.calories, 1.0)
            assertEquals("$id protein", sum[1], dish.protein, 0.2)
            assertEquals("$id carbs", sum[2], dish.carbs, 0.2)
            assertEquals("$id fat", sum[3], dish.fats, 0.2)
            // The record id names every profile of the recipe, so the assumption is readable where the row is shown.
            val record = dish.sourceRecordId.orEmpty().replace("FDC ", "FDC")
            parts.filter { it.profile != "agua" }.forEach { part -> assertTrue("$id record names ${part.profile}", part.profile in record) }
        }
    }
}
