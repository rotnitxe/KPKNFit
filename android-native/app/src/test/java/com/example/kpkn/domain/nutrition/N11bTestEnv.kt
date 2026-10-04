package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.MealType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * The resolution of a description over the static catalog plus the supermarket cola of the blind probe (the tests of WP-N11b).
 * The cola is built like the rows the OFF Chile importer writes: per 100 ml as sold and no portion of its own, which is how a
 * drink that is not a beverage row of the catalog reaches the portions.
 */
internal object N11bTestEnv {
    private fun offCola(): GlobalFoodEntity {
        val name = "Coca-Cola Original 350 ml"
        val normalized = FoodIndex.normalizeSearch(name)
        val brand = FoodIndex.normalizeSearch("Coca-Cola")
        return GlobalFoodEntity(
            foodId = "off_coca_cola_original_350ml",
            name = name,
            brand = "Coca-Cola",
            normalizedName = normalized,
            normalizedBrand = brand,
            aliasesJson = JsonArray(listOf(normalized, brand).distinct().map { JsonPrimitive(it) }).toString(),
            calories = 42.0,
            protein = 0.0,
            carbs = 10.6,
            fats = 0.0,
            fiber = 0.0,
            sugar = 10.6,
            source = "OFF Chile",
            sourcePriority = 80,
            verifiedScore = 0.85,
            sourceRecordId = "coca_cola_original_350ml",
            nutritionBasis = "PER_100G_AS_SOLD",
            portionGrams = null,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao

    /** The port over the static catalog, the cola and the [extra] rows (which the resolver knows but the static lookup of a filling does not). */
    private fun buildPort(extra: List<FoodItem>): FoodResolutionPort {
        val staticFoods = buildFoodDatabase()
        val cola = offCola()
        val foods = (staticFoods + extra + cola.toFoodItem()).associateBy { it.id }
        val index = FoodIndex().apply { build(listOf(cola), staticFoods + extra, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        return object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods[id]
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    private val port: FoodResolutionPort by lazy { buildPort(emptyList()) }

    /** The tags of [description], resolved as the logger resolves them. */
    fun resolve(description: String): List<ResolvedTag> =
        runBlocking { TagResolver(port).resolveAll(parseMealDescription(description)).first }

    /** The tags of [description] for a meal of [mealType]: the meal context scales the portions that were not said (WP-N8b). */
    fun resolveAt(description: String, mealType: MealType): List<ResolvedTag> =
        runBlocking { TagResolver(port).resolveAll(parseMealDescription(description), mealType = mealType).first }

    /** The tags of [description] when the catalog of the resolver also holds the [extra] rows. */
    fun resolveWith(description: String, vararg extra: FoodItem): List<ResolvedTag> =
        runBlocking { TagResolver(buildPort(extra.toList())).resolveAll(parseMealDescription(description)).first }

    /** The kcal that the tags log. */
    fun kcal(tags: List<ResolvedTag>): Double = tags.filterNot { it.isExcluded }.sumOf { it.loggedFood?.calories ?: 0.0 }

    fun grams(tag: ResolvedTag): Double = tag.amountGrams ?: tag.loggedFood?.amount ?: 0.0
}
