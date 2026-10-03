package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import com.example.kpkn.data.models.PortionPreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N6: one size scale for the whole nutrition flow (audit finding A-Q6, blind-probe inputs #20, #21, #22 and #55).
 *
 * "Grande" used to be x2.0 in the parser, x1.5 in the chip and x1.25 in the V2 option, and a small size x0.6. Every size now reads
 * [PORTION_MULTIPLIERS]: small x0.75, large x1.25, extra x1.5. The tests ask for ratios between two inputs, not for literals, so
 * they hold for the meaning of the scale and not for one catalog row.
 */
class PortionSizeScaleTest {

    private val staticFoods = buildFoodDatabase()
    private val port: FoodResolutionPort by lazy {
        val dao = java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao
        val index = FoodIndex().apply { build(emptyList(), staticFoods, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(dao, index, null)
        object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = staticFoods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    private fun tag(text: String): ResolvedTag = runBlocking {
        TagResolver(port).resolveAll(parseMealDescription(text)).first.filterNot { it.isExcluded }.first()
    }

    private fun grams(text: String): Double = tag(text).amountGrams ?: Double.NaN

    private fun factor(size: PortionPreset): Double = PORTION_MULTIPLIERS[size] ?: Double.NaN

    @Test
    fun `the scale is small 0,75, medium 1, large 1,25 and extra 1,5`() {
        assertEquals(0.75, factor(PortionPreset.SMALL), 0.0)
        assertEquals(1.0, factor(PortionPreset.MEDIUM), 0.0)
        assertEquals(1.25, factor(PortionPreset.LARGE), 0.0)
        assertEquals(1.5, factor(PortionPreset.EXTRA), 0.0)
    }

    @Test
    fun `the absolute options of a 100 g portion are 75, 100 and 125 g`() {
        val options = absolutePortionOptions(100.0)
        assertEquals(listOf("Pequeña", "Habitual", "Grande"), options.map { it.first })
        assertEquals(listOf(75.0, 100.0, 125.0), options.map { it.second })
        assertEquals(emptyList<Pair<String, Double>>(), absolutePortionOptions(null))
    }

    @Test
    fun `a large plate of rice is 1,25 plates of rice`() {
        val plate = grams("un plato de arroz")
        val large = grams("un plato grande de arroz")
        assertEquals(1.25 * plate, large, 0.1)
        assertEquals(PortionPreset.LARGE, tag("un plato grande de arroz").portion)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, tag("un plato grande de arroz").amountIntent)
        // Said without the article it is the usual serving of rice, enlarged by the same factor.
        assertEquals(1.25 * grams("arroz"), grams("plato grande de arroz"), 0.01)
    }

    @Test
    fun `a small salad is 0,75 of a salad and a large one 1,25`() {
        val salad = grams("ensalada")
        assertEquals(0.75 * salad, grams("ensalada chica"), 0.01)
        assertEquals(0.75 * salad, grams("una ensalada chica"), 0.01)
        assertEquals(1.25 * salad, grams("ensalada grande"), 0.01)
        assertEquals(0.75 * grams("arroz"), grams("plato chico de arroz"), 0.01)
    }

    @Test
    fun `the parser reads the size words on the same scale, in the plural too`() {
        fun size(text: String) = parseMealDescription(text).items.first().portion
        assertEquals(PortionPreset.LARGE, size("ensalada grande"))
        assertEquals(PortionPreset.LARGE, size("un plato generoso de ensalada"))
        assertEquals(PortionPreset.LARGE, size("2 manzanas grandes"))
        assertEquals(PortionPreset.SMALL, size("ensalada chica"))
        assertEquals(PortionPreset.SMALL, size("2 papas chicas"))
        assertEquals(PortionPreset.MEDIUM, size("plato mediano de ensalada"))
        assertEquals("manzana", parseMealDescription("2 manzanas grandes").items.single().tag)
    }

    @Test
    fun `a juice of a large size is 1,25 of the same juice`() {
        assertEquals(1.25 * grams("jugo natural de naranja"), grams("jugo natural de naranja grande"), 0.01)
    }

    @Test
    fun `the engine sizes a plate on the scale`() {
        val plate = SubjectivePortionEngine.resolve("un plato de arroz")?.grams ?: Double.NaN
        val large = SubjectivePortionEngine.resolve("un plato grande de arroz")?.grams ?: Double.NaN
        assertEquals("a plato grande is a plate x1,25", 1.25 * plate, large, 0.01)
    }

    @Test
    fun `scaling a food to a size multiplies its nutrients by the scale`() {
        val food = FoodItem(id = "t", name = "Alimento de prueba", servingSize = 100.0, unit = "g", calories = 200.0, protein = 8.0, carbs = 20.0, fats = 4.0)
        val medium = scaleFoodByPortion(food, quantity = 1.0, portion = PortionPreset.MEDIUM)
        for (size in PortionPreset.entries) {
            val sized = scaleFoodByPortion(food, quantity = 1.0, portion = size)
            assertEquals("$size", medium.calories * factor(size), sized.calories, 0.5)
        }
        assertTrue(factor(PortionPreset.SMALL) < factor(PortionPreset.MEDIUM) && factor(PortionPreset.LARGE) < factor(PortionPreset.EXTRA))
    }
}
