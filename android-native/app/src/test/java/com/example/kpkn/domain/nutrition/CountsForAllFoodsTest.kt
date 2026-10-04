package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import com.example.kpkn.data.models.PortionPreset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N8: a count typed before a food counts units of that food, for every food that has a unit weight (audit finding A-Q1,
 * blind-probe inputs #2, #22, #37, #55 and #59; "2 cervezas" of the N5 report).
 *
 * Before: "2 yogures" was one yogurt (200 g), "3 tomates" 80 g, "media palta" and "2 paltas" the same 80 g, "3 cervezas" one beer, and
 * "una manzana grande" lost its size. Only breads, eggs, empanadas, wraps and a short list of fruits counted.
 *
 * Pure JVM, same chain as the drawer: parseMealDescription -> TagResolver(port).resolveAll. Two ports: the static catalog, and
 * the catalog next to a branded drink an Open Food Facts import adds (Coca-Cola), so the count is shown for both kinds of row.
 */
class CountsForAllFoodsTest {

    // --- Environment ----------------------------------------------------------------------------------------------

    private fun noOpNutritionDao(): NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao

    private fun cocaCola(): GlobalFoodEntity {
        val name = "Coca-Cola Original 350 ml"
        val normalized = FoodIndex.normalizeSearch(name)
        return GlobalFoodEntity(
            foodId = "off_coca_cola_original_350ml",
            name = name,
            brand = "Coca-Cola",
            normalizedName = normalized,
            normalizedBrand = FoodIndex.normalizeSearch("Coca-Cola"),
            aliasesJson = JsonArray(listOf(normalized, "coca cola").map { JsonPrimitive(it) }).toString(),
            calories = 42.0,
            protein = 0.0,
            carbs = 10.6,
            fats = 0.0,
            sugar = 10.6,
            source = "OFF Chile",
            sourcePriority = 80,
            verifiedScore = 0.85,
            sourceRecordId = "coca_cola_original_350ml",
            nutritionBasis = "PER_100G_AS_SOLD",
        )
    }

    private fun portFor(globals: List<GlobalFoodEntity>): FoodResolutionPort {
        val staticFoods = buildFoodDatabase()
        val foods = staticFoods + globals.map { it.toFoodItem() }
        val index = FoodIndex().apply { build(globals, staticFoods, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        return object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    private val catalogPort: FoodResolutionPort by lazy { portFor(emptyList()) }
    private val brandedPort: FoodResolutionPort by lazy { portFor(listOf(cocaCola())) }

    private fun resolve(
        text: String,
        calibration: NutritionCalibrationProfile? = null,
        branded: Boolean = false,
    ): List<ResolvedTag> = runBlocking {
        TagResolver(if (branded) brandedPort else catalogPort, calibration).resolveAll(parseMealDescription(text)).first
            .filterNot { it.isExcluded }
    }

    private fun single(text: String, calibration: NutritionCalibrationProfile? = null, branded: Boolean = false): ResolvedTag {
        val tags = resolve(text, calibration, branded)
        assertEquals("$text -> ${tags.map { it.tag }}", 1, tags.size)
        return tags.single()
    }

    private fun grams(text: String, branded: Boolean = false): Double =
        single(text, branded = branded).amountGrams ?: Double.NaN

    // --- The counts of the audit ----------------------------------------------------------------------------------

    @Test
    fun `2 yogures are two 125 g cups, not one`() {
        val tag = single("2 yogures")
        assertEquals(250.0, tag.amountGrams ?: Double.NaN, 0.01)
        assertEquals(2.0, tag.quantity, 0.0)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        // A declared count is a declared amount: the same label the eggs and the breads get.
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, tag.amountIntent)
        assertEquals(HouseholdPortions.COUNT_UNIT_ID, tag.unitId)
        assertEquals("the unit is the cup, the nutrients follow the mass", 250.0, tag.loggedFood?.amount ?: Double.NaN, 0.01)
    }

    @Test
    fun `3 tomates are 360 g and one tomate is 120 g`() {
        assertEquals(360.0, grams("3 tomates"), 0.01)
        assertEquals(360.0, grams("tres tomates"), 0.01)
        assertEquals(120.0, grams("un tomate"), 0.01)
        // A bare name is still the 80 g serving: only a count changes it.
        assertEquals(80.0, grams("tomate"), 0.01)
    }

    @Test
    fun `half an avocado is 75 g, one is 150 g and two are 300 g`() {
        assertEquals(75.0, grams("media palta"), 0.01)
        assertEquals(150.0, grams("una palta"), 0.01)
        assertEquals(300.0, grams("2 paltas"), 0.01)
        assertEquals(80.0, grams("palta"), 0.01)
    }

    @Test
    fun `a yogurt is 125 g`() {
        assertEquals(125.0, grams("un yogurt"), 0.01)
        assertEquals(125.0, grams("un yogur"), 0.01)
        assertEquals(250.0, grams("2 yogurt"), 0.01)
    }

    @Test
    fun `half a piece of a countable fruit uses the same unit weight`() {
        assertEquals(91.0, grams("media manzana"), 0.01)
        assertEquals(182.0, grams("una manzana"), 0.01)
        assertEquals(120.0, grams("un plátano"), 0.01)
        assertEquals(130.0, grams("una naranja"), 0.01)
        assertEquals(75.0, grams("un kiwi"), 0.01)
        assertEquals(150.0, grams("un durazno"), 0.01)
        assertEquals(220.0, grams("un completo"), 0.01)
        assertEquals(300.0, grams("2 duraznos"), 0.01)
    }

    @Test
    fun `each food of a meal takes its own count`() {
        val tags = resolve("2 yogures y 3 tomates")
        assertEquals(listOf(250.0, 360.0), tags.map { it.amountGrams })
        assertTrue(tags.all { it.resolutionStatus == FoodResolutionStatus.AUTO })
    }

    // --- A size multiplies the count once -------------------------------------------------------------------------

    @Test
    fun `2 manzanas grandes are 455 g from a base of 364 g, and Grande Habitual Grande is exact`() {
        val tag = single("2 manzanas grandes")
        assertEquals("manzana", tag.tag)
        assertEquals(PortionPreset.LARGE, tag.portion)
        assertEquals(455.0, tag.amountGrams ?: Double.NaN, 0.01)
        assertEquals("the anchor is the count without the size", 364.0, tag.baseAmountGrams ?: Double.NaN, 0.01)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertEquals("gen001", tag.foodItem?.id)
        // The chips and the options read the immutable anchor, so every round trip lands on the same grams.
        val options = absolutePortionOptions(tag.baseAmountGrams).toMap()
        assertEquals(455.0, options.getValue("Grande"), 0.0)
        assertEquals(364.0, options.getValue("Habitual"), 0.0)
        assertEquals(273.0, options.getValue("Pequeña"), 0.0)
        assertEquals(tag.amountGrams, options["Grande"])
        assertEquals(227.5, grams("una manzana grande"), 0.01)
    }

    @Test
    fun `a size on a food that is not countable by default also multiplies once`() {
        val tag = single("2 yogures grandes")
        assertEquals(312.5, tag.amountGrams ?: Double.NaN, 0.01)
        assertEquals(250.0, tag.baseAmountGrams ?: Double.NaN, 0.01)
        assertEquals(2.0 * 125.0 * (PORTION_MULTIPLIERS[PortionPreset.LARGE] ?: Double.NaN), tag.amountGrams ?: Double.NaN, 0.01)
        assertEquals(187.5, grams("una palta grande"), 0.01)
        assertEquals(0.75 * grams("2 tomates"), grams("2 tomates chicos"), 0.01)
    }

    // --- What a count must not touch ------------------------------------------------------------------------------

    @Test
    fun `a bare food, eggs and a vessel keep their amounts`() {
        assertEquals(120.0, grams("arroz"), 0.01)
        assertEquals(100.0, grams("2 huevos"), 0.01)
        assertEquals(50.0, grams("un huevo"), 0.01)
        val glasses = single("dos vasos de leche descremada")
        assertEquals("the vessel wins over the count of units", "glass", glasses.unitId)
        assertEquals(515.0, glasses.amountGrams ?: Double.NaN, 0.01)
        assertEquals(2.0 * grams("un vaso de leche descremada"), glasses.amountGrams ?: Double.NaN, 0.01)
        assertEquals("an explicit mass is not a count", 200.0, grams("200 g de yogurt"), 0.01)
    }

    @Test
    fun `a food with no unit weight keeps its portion, a portion is not a piece`() {
        // 20 almonds are not 20 portions of almonds, nor 10 grapes 10 portions of grapes.
        assertEquals(grams("almendras"), grams("20 almendras"), 0.01)
        assertEquals(grams("uvas"), grams("diez uvas"), 0.01)
        assertEquals(grams("queso"), grams("4 quesos"), 0.01)
        assertEquals(grams("tomates cherry"), grams("10 tomates cherry"), 0.01)
        // "doble palta" doubles a topping; it does not count two avocados.
        assertEquals(grams("palta"), grams("doble palta"), 0.01)
        // A bare "un"/"una" of a food with no piece of its own is not a count either.
        assertEquals(grams("café"), grams("un café"), 0.01)
    }

    // --- Drinks and cuts -----------------------------------------------------------------------------------------

    @Test
    fun `drinks count their serving`() {
        assertEquals(660.0, grams("2 cervezas"), 0.01)
        assertEquals(990.0, grams("3 cervezas"), 0.01)
        assertEquals(330.0, grams("una cerveza"), 0.01)
        assertEquals(700.0, grams("2 sprites"), 0.01)
        assertEquals(440.0, grams("2 cafés"), 0.01)
        assertEquals(220.0, grams("un café"), 0.01)
        // A tea is the cup of the row the alias table gives "té" (Té Verde, or Té sin azúcar and its 240 ml): two are two lone ones.
        assertEquals(2.0 * grams("un té"), grams("2 tés"), 0.01)
        assertEquals(300.0, grams("2 vinos"), 0.01)
        assertEquals(700.0, grams("2 gaseosas"), 0.01)
        assertEquals(500.0, grams("2 aguas"), 0.01)
        assertEquals(FoodResolutionStatus.AUTO, single("2 cervezas").resolutionStatus)
    }

    @Test
    fun `a count of a plural of three letters counts the food of its singular`() {
        // "tés" is below the four-letter floor of the singularizer and of the catalog's plural rule: it named no food, and the
        // count was a 350 g dish estimate of 560 kcal asking which food it was.
        val cup = grams("un té")
        for ((text, count) in listOf("2 tés" to 2.0, "2 tes" to 2.0, "dos tés" to 2.0, "3 tés" to 3.0, "3 tes" to 3.0)) {
            val tag = single(text)
            assertNotNull("$text names a tea", tag.foodItem)
            assertEquals("$text status", FoodResolutionStatus.AUTO, tag.resolutionStatus)
            assertEquals("$text quantity", count, tag.quantity, 0.0)
            assertEquals("$text amount", count * cup, tag.amountGrams ?: Double.NaN, 0.01)
        }
        assertEquals("the singular is what it always was", grams("2 té"), grams("2 tés"), 0.01)
    }

    @Test
    fun `a branded drink counts the serving of the drink the person named`() {
        val tag = single("3 coca colas", branded = true)
        assertEquals("off_coca_cola_original_350ml", tag.foodItem?.id)
        assertEquals(1050.0, tag.amountGrams ?: Double.NaN, 0.01)
        assertEquals(FoodResolutionStatus.AUTO, tag.resolutionStatus)
    }

    @Test
    fun `a count of cuts of meat counts their household piece`() {
        assertEquals(300.0, grams("2 pechugas"), 0.01)
        assertEquals(240.0, grams("2 trutros de pollo"), 0.01)
        assertEquals(150.0, grams("una pechuga"), 0.01)
    }

    // --- The parser, the resolver API and the personal portion ---------------------------------------------------

    @Test
    fun `the parser flags a count of a food with a unit weight and leaves the amount to the resolver`() {
        fun flagged(text: String) = parseMealDescription(text).items.single().countExpressed
        assertTrue(flagged("2 yogures"))
        assertTrue(flagged("3 tomates"))
        assertTrue(flagged("una palta"))
        assertTrue(flagged("media palta"))
        assertTrue(flagged("2 cervezas"))
        assertTrue(flagged("2 pechugas"))
        assertFalse("a bare name", flagged("yogurt"))
        assertFalse("a bare name", flagged("arroz"))
        assertFalse("no unit weight", flagged("20 almendras"))
        assertFalse("no unit weight", flagged("un café"))
        assertFalse("a portion multiplier", flagged("doble palta"))
        assertFalse("eggs resolve in the parser", flagged("2 huevos"))
        assertFalse("an explicit mass", flagged("200 g de yogurt"))
        assertFalse("a vessel", flagged("dos vasos de leche descremada"))
        val yogurts = parseMealDescription("2 yogures").items.single()
        assertEquals(AmountIntent.UNSPECIFIED, yogurts.amountIntent)
        assertNull(yogurts.amountGrams)
    }

    @Test
    fun `resolveEatenGrams scales one unit by the count only when it is told the amount is a count`() {
        val yogurt = checkNotNull(HouseholdPortions.householdStaticFood("yogur"))
        fun resolved(count: Boolean, hint: Double? = null) = HouseholdPortions.resolveEatenGrams(
            intent = AmountIntent.UNSPECIFIED, quantity = 2.0, food = yogurt, parsedGrams = null,
            datasetHint = hint, query = "yogur", countExpressed = count,
        )
        assertEquals(250.0, resolved(count = true), 0.01)
        assertEquals("without the flag the quantity of a non-countable food is not read", 125.0, resolved(count = false), 0.01)
        assertEquals("a confirmed portion is the unit", 360.0, resolved(count = true, hint = 180.0), 0.01)
        // A mass or a resolved utensil is never rescaled by the count.
        assertEquals(
            90.0,
            HouseholdPortions.resolveEatenGrams(AmountIntent.EXPLICIT_MASS, 2.0, yogurt, 90.0, query = "yogur", countExpressed = true),
            0.01,
        )
        assertEquals(
            515.0,
            HouseholdPortions.resolveEatenGrams(AmountIntent.RESOLVED_SUBJECTIVE, 2.0, yogurt, 515.0, query = "yogur", unitId = "glass", countExpressed = true),
            0.01,
        )
    }

    @Test
    fun `the unit weight of a count is known for fruits, drinks and cuts and unknown for nuts and dishes`() {
        assertEquals(182.0, HouseholdPortions.unitWeightByToken("manzana verde") ?: Double.NaN, 0.0)
        assertEquals(125.0, HouseholdPortions.unitWeightByToken("yogures") ?: Double.NaN, 0.0)
        assertNull("the head noun decides: pan con palta", HouseholdPortions.unitWeightByToken("pan con palta"))
        assertNull("ensalada de tomate", HouseholdPortions.unitWeightByToken("ensalada de tomate"))
        assertNull("a cherry tomato is not 120 g", HouseholdPortions.unitWeightByToken("tomate cherry"))
        assertNull("a dried peach is not a peach", HouseholdPortions.unitWeightByToken("durazno seco"))
        assertNull(HouseholdPortions.countedUnitGrams(null, "almendras"))
        assertNull(HouseholdPortions.countedUnitGrams(null, "sandwich de pollo"))
        assertNotNull(HouseholdPortions.countedUnitGrams(findFoodExactByNormalized("cerveza"), "cerveza"))
        assertTrue(HouseholdPortions.countAppliesTo(null, "palta", 1.0))
        assertFalse(HouseholdPortions.countAppliesTo(null, "pechuga", 1.0))
        assertTrue(HouseholdPortions.countAppliesTo(null, "pechuga", 2.0))
        assertFalse(HouseholdPortions.countAppliesTo(null, "almendras", 20.0))
        // A custom food keeps the serving its owner typed.
        val custom = FoodItem(id = "custom_yogurt", name = "Yogurt mío", isCustom = true, servingSize = 170.0, unit = "g", calories = 90.0)
        assertNull(HouseholdPortions.unitWeightByToken("yogurt", custom))
    }

    @Test
    fun `a confirmed personal portion is the weight of one counted unit`() {
        val calibration = NutritionCalibrationProfile(maturePortionsGrams = mapOf("gen087" to 200.0))
        assertEquals(200.0, single("un yogur", calibration).amountGrams ?: Double.NaN, 0.01)
        assertEquals(400.0, single("2 yogures", calibration).amountGrams ?: Double.NaN, 0.01)
        assertEquals(250.0, single("2 yogures").amountGrams ?: Double.NaN, 0.01)
    }

    @Test
    fun `declaring another identity keeps the counted amount`() {
        val original = single("2 yogures")
        val declared = runBlocking { TagResolver(catalogPort).resolveDeclaredComposition(original, "yogurt griego") }
        assertEquals(250.0, declared.amountGrams ?: Double.NaN, 0.01)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, declared.amountIntent)
    }

    @Test
    fun `a counted amount does not turn the rest of the meal into a context guess`() {
        val tags = resolve("un yogur griego con granola")
        assertEquals(2, tags.size)
        assertEquals(125.0, tags[0].amountGrams ?: Double.NaN, 0.01)
        assertEquals(30.0, tags[1].amountGrams ?: Double.NaN, 0.01)
    }
}
