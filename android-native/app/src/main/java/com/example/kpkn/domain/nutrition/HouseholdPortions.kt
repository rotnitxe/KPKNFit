package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood

/**
 * Household portion authority: what a person actually ate, not a supermarket pack.
 *
 * Later stages (dataset priors, OFF portionGrams, contextual 200 g dairy defaults)
 * must not override a locked count, utensil, or explicit mass.
 */
object HouseholdPortions {

    const val PACK_GRAMS = 400.0
    const val MAX_ITEM_GRAMS_WITHOUT_KG = 600.0
    const val MAX_ITEM_KCAL_WITHOUT_KG = 1200.0

    private val COUNTABLE_FAMILIES = setOf(
        "pan_chileno", "pan", "huevo", "empanada", "wrap",
    )

    private val COUNTABLE_NAME_MARKERS = listOf(
        "hallulla", "hallula", "marraqueta", "sopaipilla", "empanada",
        "completo", "huevo", "galleta", "arepa", "pan amasado", "panecillo",
        "manzana", "platano", "naranja", "pera", "kiwi",
        "taco", "burrito", "sushi", "wrap", "hamburguesa",
    )

    private val CHEESE_MARKERS = listOf("queso", "gouda", "gauda", "cheddar", "mantecoso", "gauda")
    private val FAT_MARKERS = listOf("aceite", "mantequilla", "mayo", "mayonesa")
    private val BREAD_MARKERS = listOf(
        "hallulla", "hallula", "marraqueta", "pan", "sopaipilla", "amasado",
    )

    fun looksLikePackName(name: String): Boolean {
        val n = FoodIdentity.normalize(name)
        return Regex("""\b(?:\d+\s*kg|kilo|pack|packe|x\s*\d+|x\d+)\b""").containsMatchIn(n) ||
            n.contains(" 1kg") || n.endsWith("kg") || n.contains("gramos") && n.contains("pack")
    }

    fun isHouseholdHint(grams: Double, food: FoodItem? = null, query: String? = null): Boolean {
        if (!grams.isFinite() || grams <= 0.0) return false
        if (grams >= PACK_GRAMS) return false
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        val cap = when {
            family == "huevo" || query?.let { FoodIdentity.familyFor(it) } == "huevo" -> 80.0
            family == "pan_chileno" || family == "pan" -> 180.0
            isBreadQuery(food, query) -> 180.0
            else -> 350.0
        }
        return grams <= cap
    }

    fun isCountable(food: FoodItem?, query: String? = null): Boolean {
        if (food != null) {
            if (food.unit.equals("u", ignoreCase = true)) return true
            val family = FoodIdentity.familyFor(food)
            if (family in COUNTABLE_FAMILIES) return true
            val blob = FoodIdentity.normalize(food.name + " " + food.searchAliases.joinToString(" "))
            if (COUNTABLE_NAME_MARKERS.any { blob.contains(it) }) return true
        }
        val q = query?.let(FoodIdentity::normalize).orEmpty()
        if (q.isBlank()) return false
        if (FoodIdentity.familyFor(q) in COUNTABLE_FAMILIES) return true
        return COUNTABLE_NAME_MARKERS.any { q.contains(it) }
    }

    fun unitGrams(food: FoodItem?, query: String? = null): Double {
        val q = FoodIdentity.normalize(query ?: food?.name.orEmpty())
        if (q.contains("marraqueta")) return 100.0
        if (q.contains("hallulla") || q.contains("hallula")) return 80.0
        if (q.contains("sopaipilla")) return 60.0
        if (q.contains("empanada")) return food?.servingSize?.takeIf { it in 80.0..250.0 } ?: 180.0
        if (q.contains("taco") || q.contains("burrito") || q.contains("arepa") ||
            q.contains("sushi") || q.contains("wrap")
        ) {
            return food?.servingSize?.takeIf { it in 40.0..220.0 } ?: 120.0
        }
        if (q.contains("hamburguesa") || q.contains("burger")) {
            return food?.servingSize?.takeIf { it in 80.0..250.0 } ?: 150.0
        }
        if (FoodIdentity.familyFor(q) == "huevo" || q.contains("huevo")) {
            return food?.servingSize?.takeIf { it in 40.0..70.0 } ?: 50.0
        }
        if (food != null) {
            val serving = food.servingSize.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
            if (food.unit.equals("u", ignoreCase = true)) return serving
            val family = FoodIdentity.familyFor(food)
            return when (family) {
                "pan_chileno" -> serving.coerceIn(40.0, 150.0)
                "pan" -> serving.coerceIn(40.0, 120.0)
                "huevo" -> serving.coerceIn(40.0, 70.0)
                else -> serving.coerceAtMost(150.0)
            }
        }
        return when (FoodIdentity.familyFor(q)) {
            "pan_chileno" -> 80.0
            "pan" -> 50.0
            "huevo" -> 50.0
            else -> 100.0
        }
    }

    fun defaultGrams(food: FoodItem?, query: String? = null): Double {
        if (isCountable(food, query)) return unitGrams(food, query)
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name, food?.searchAliases?.joinToString(" ")).joinToString(" "),
        )
        when {
            FAT_MARKERS.any { blob.contains(it) } -> return 10.0
            CHEESE_MARKERS.any { blob.contains(it) } -> return 30.0
            blob.contains("granola") -> return 30.0
            blob.contains("avena") -> return 40.0
        }
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        return when (family) {
            "huevo" -> 50.0
            "pan", "pan_chileno" -> unitGrams(food, query)
            "leche" -> 200.0
            "yogurt" -> 125.0
            "arroz" -> 120.0
            "avena" -> 40.0
            "pasta" -> 160.0
            "pollo" -> 150.0
            "tomate" -> 80.0
            "palta" -> 80.0
            else -> {
                food?.let { getContextualDefaultServingSize(it) }
                    ?: 100.0
            }
        }
    }

    private fun hasClassDefault(food: FoodItem?, query: String?): Boolean {
        if (isCountable(food, query)) return true
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name, food?.searchAliases?.joinToString(" ")).joinToString(" "),
        )
        if (FAT_MARKERS.any { blob.contains(it) }) return true
        if (CHEESE_MARKERS.any { blob.contains(it) }) return true
        if (blob.contains("granola") || blob.contains("avena")) return true
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        return family in setOf("huevo", "pan", "pan_chileno", "leche", "yogurt", "arroz", "avena", "pasta", "pollo", "queso")
    }

    /**
     * Locked eaten grams. Dataset / pack sizes only apply as a last hint inside
     * household range, and never after an explicit mass, utensil, or count.
     */
    fun resolveEatenGrams(
        intent: AmountIntent,
        quantity: Double,
        food: FoodItem?,
        parsedGrams: Double?,
        datasetHint: Double? = null,
        query: String? = null,
        explicitKilogram: Boolean = false,
    ): Double {
        val qty = quantity.coerceAtLeast(0.01)
        val parsed = parsedGrams?.takeIf { it.isFinite() && it > 0.0 }
        when (intent) {
            AmountIntent.EXPLICIT_MASS -> {
                return parsed ?: (defaultGrams(food, query) * qty)
            }
            AmountIntent.RESOLVED_SUBJECTIVE -> {
                if (parsed != null) {
                    if (isCountable(food, query) || isCountable(null, query)) {
                        return plausibilityClamp(food, parsed, query, explicitKilogram, qty)
                    }
                    if (!explicitKilogram && (
                            looksLikePackName(food?.name.orEmpty()) ||
                                (food != null && isGlobalSku(food))
                            )
                    ) {
                        return defaultGrams(food, query)
                    }
                    return parsed
                }
            }
            AmountIntent.INFERRED_CONTEXT -> {
                if (parsed != null) return parsed
            }
            AmountIntent.UNSPECIFIED -> Unit
        }
        if (isCountable(food, query) || (qty != 1.0 && isCountable(null, query))) {
            return unitGrams(food, query) * qty
        }
        if (parsed != null && (explicitKilogram || isHouseholdHint(parsed, food, query))) {
            return parsed
        }
        val hint = datasetHint?.takeIf { isHouseholdHint(it, food, query) }
        if (hint != null && intent == AmountIntent.UNSPECIFIED && !hasClassDefault(food, query)) {
            return hint
        }
        return defaultGrams(food, query) * if (intent == AmountIntent.UNSPECIFIED || intent == AmountIntent.INFERRED_CONTEXT) 1.0 else qty
    }

    const val HEURISTIC_DISH_GRAMS = 350.0

    fun inferredItemGrams(
        food: FoodItem?,
        query: String,
        context: ContextDetector.ContextResult,
    ): Double {
        val factor = context.primaryContext.portionFactor.coerceIn(0.55, 1.45)
        val blob = FoodIdentity.normalize("$query ${food?.name.orEmpty()}")
        val role = inferredRole(blob)
        val grams = when (context.shape) {
            InferredMealContext.Shape.MAIN_PLATE -> when (role) {
                "starch" -> 220.0 * factor
                "protein" -> if (blob.contains("huevo")) 100.0 else 140.0 * factor
                else -> 90.0 * factor
            }
            InferredMealContext.Shape.BREAKFAST_BOWL -> when {
                blob.contains("avena") -> 40.0
                blob.contains("granola") || blob.contains("nuez") || blob.contains("almendra") -> 30.0
                blob.contains("yogurt") || blob.contains("leche") -> 200.0
                blob.contains("platano") || blob.contains("fruta") || blob.contains("banana") -> 120.0
                else -> defaultGrams(food, query)
            }
            InferredMealContext.Shape.SANDWICH -> when {
                isCountable(food, query) || blob.contains("pan") || blob.contains("hallulla") ||
                    blob.contains("marraqueta") -> unitGrams(food, query)
                CHEESE_MARKERS.any { blob.contains(it) } -> 30.0
                blob.contains("palta") -> 60.0
                else -> 40.0
            }
            InferredMealContext.Shape.BEVERAGE -> 220.0
            InferredMealContext.Shape.WRAP -> unitGrams(food, query)
            InferredMealContext.Shape.UNKNOWN -> heuristicDishGrams(query, context)
            else -> defaultGrams(food, query)
        }
        return grams.coerceIn(8.0, MAX_ITEM_GRAMS_WITHOUT_KG)
    }

    fun heuristicDishGrams(query: String, context: ContextDetector.ContextResult? = null): Double {
        return when (context?.shape) {
            InferredMealContext.Shape.WRAP -> 120.0
            InferredMealContext.Shape.BEVERAGE -> 220.0
            InferredMealContext.Shape.BREAKFAST_BOWL -> 250.0
            InferredMealContext.Shape.SNACK_ITEM -> defaultGrams(null, query)
            else -> HEURISTIC_DISH_GRAMS
        }
    }

    private fun inferredRole(blob: String): String = when {
        listOf("arroz", "pasta", "papa", "fideo", "quinoa", "couscous", "ramen", "noodle").any { blob.contains(it) } -> "starch"
        listOf("pollo", "huevo", "carne", "pescado", "atun", "salmon", "cerdo", "vacuno", "pechuga").any { blob.contains(it) } -> "protein"
        else -> "side"
    }

    fun looksLikeCountExpression(text: String): Boolean {
        val t = text.lowercase().trim()
        return Regex(
            """^(?:un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio|\d+(?:[.,]\d+)?)\s+\S+""",
        ).containsMatchIn(t)
    }

    fun isExplicitKilogram(text: String): Boolean {
        val t = FoodIdentity.normalize(text)
        return Regex("""\b\d+(?:[.,]\d+)?\s*(?:kg|kilo|kilos)\b""").containsMatchIn(t) ||
            t.contains("medio kilo") || t.contains("medio kilogramo")
    }

    fun plausibilityClamp(
        food: FoodItem?,
        grams: Double,
        query: String? = null,
        explicitKilogram: Boolean,
        quantity: Double = 1.0,
    ): Double {
        if (explicitKilogram) return grams.coerceAtLeast(1.0)
        if (!grams.isFinite() || grams <= 0.0) return defaultGrams(food, query)
        val qty = quantity.coerceAtLeast(0.01)
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        val maxPerUnit = when {
            family == "huevo" -> 80.0
            isBreadQuery(food, query) -> 180.0
            family == "pan" || family == "pan_chileno" -> 180.0
            else -> MAX_ITEM_GRAMS_WITHOUT_KG
        }
        val max = if (isCountable(food, query) || isCountable(null, query)) {
            (maxPerUnit * qty).coerceAtMost(MAX_ITEM_GRAMS_WITHOUT_KG)
        } else {
            MAX_ITEM_GRAMS_WITHOUT_KG
        }
        if (grams <= max) return grams
        return if (isCountable(food, query) || isCountable(null, query)) {
            unitGrams(food, query) * qty
        } else {
            defaultGrams(food, query)
        }
    }

    fun itemKcalIsPlausible(
        food: FoodItem,
        grams: Double,
        explicitKilogram: Boolean,
    ): Boolean {
        if (explicitKilogram) return true
        val serving = food.servingSize.coerceAtLeast(1.0)
        val kcal = food.calories * grams / serving
        return kcal <= MAX_ITEM_KCAL_WITHOUT_KG && grams <= MAX_ITEM_GRAMS_WITHOUT_KG
    }

    fun catalogFoodFor(query: String): FoodItem? = householdStaticFood(query)

    /** Static catalog only. Never an OFF/USDA supermarket SKU. Exact phrase beats a longer dish. */
    fun householdStaticFood(tag: String): FoodItem? =
        (findFoodExactByNormalized(tag) ?: findFoodByNormalized(tag))
            ?.takeUnless { isGlobalSku(it) }

    fun isGlobalSku(food: FoodItem): Boolean {
        val src = food.source.orEmpty().uppercase()
        if (food.id.startsWith("off_", ignoreCase = true)) return true
        return src.contains("OFF") || src.contains("USDA")
    }

    fun isHouseholdIdentity(food: FoodItem, brandHint: String? = null): Boolean {
        if (isGlobalSku(food)) return !brandHint.isNullOrBlank()
        return true
    }

    fun rejectUnbrandedGlobal(food: FoodItem?, brandHint: String?): FoodItem? {
        if (food == null) return null
        return food.takeIf { isHouseholdIdentity(it, brandHint) }
    }

    fun operationalAutoStatus(
        food: FoodItem?,
        grams: Double,
        brandHint: String?,
        explicitKilogram: Boolean,
        amountIntent: AmountIntent,
    ): FoodResolutionStatus {
        if (food == null || !isHouseholdIdentity(food, brandHint)) return FoodResolutionStatus.NO_RESOLVED
        if (!FoodIdentity.hasPlausibleMacros(food)) return FoodResolutionStatus.NO_RESOLVED
        if (!grams.isFinite() || grams <= 0.0) return FoodResolutionStatus.NO_RESOLVED
        val massOk = explicitKilogram ||
            amountIntent == AmountIntent.EXPLICIT_MASS ||
            itemKcalIsPlausible(food, grams, explicitKilogram)
        return if (massOk) FoodResolutionStatus.AUTO else FoodResolutionStatus.NO_RESOLVED
    }

    fun isSimpleUnbrandedQuery(query: String): Boolean {
        if (isExplicitKilogram(query)) return false
        val tokens = FoodIdentity.normalize(query).split(" ").filter { it.isNotBlank() && it.length > 1 }
        return tokens.size <= 2
    }

    /**
     * Tab Buscar: a generic query must not persist a pack/SKU as the eaten identity.
     */
    fun identityForSearchPick(selected: FoodItem, query: String): FoodItem? {
        if (!isSimpleUnbrandedQuery(query)) return selected
        val bulk = looksLikePackName(selected.name) ||
            (selected.portionGrams ?: 0.0) >= PACK_GRAMS ||
            selected.servingSize >= PACK_GRAMS ||
            isGlobalSku(selected)
        if (!bulk) return selected
        return householdStaticFood(query)
    }

    fun eatenGramsForSearchPick(identity: FoodItem, query: String, selected: FoodItem): Double {
        val kg = isExplicitKilogram(query)
        val parsed = when {
            kg -> selected.servingSize.takeIf { it.isFinite() && it > 0.0 }
            identity.id == selected.id ->
                selected.servingSize.takeIf { isHouseholdHint(it, identity, query) }
            else -> null
        }
        return resolveEatenGrams(
            intent = if (kg) AmountIntent.EXPLICIT_MASS else AmountIntent.UNSPECIFIED,
            quantity = 1.0,
            food = identity,
            parsedGrams = parsed,
            query = query,
            explicitKilogram = kg,
        )
    }

    /**
     * Meal-memory template: stored grams are a hint, never a supermarket pack.
     */
    fun eatenGramsForTemplateFood(stored: LoggedFood, description: String): Pair<FoodItem?, Double> {
        val catalog = householdStaticFood(stored.foodName)
        val kg = isExplicitKilogram(description)
        val parsed = stored.amount.takeIf { it.isFinite() && it > 0.0 }
        val packHint = !kg && parsed != null && (
            parsed >= MAX_ITEM_GRAMS_WITHOUT_KG ||
                (parsed >= PACK_GRAMS && (
                    isCountable(catalog, stored.foodName) ||
                        looksLikePackName(stored.foodName) ||
                        (catalog != null && isGlobalSku(catalog))
                    ))
            )
        val grams = resolveEatenGrams(
            intent = when {
                kg -> AmountIntent.EXPLICIT_MASS
                packHint -> AmountIntent.UNSPECIFIED
                parsed != null -> AmountIntent.RESOLVED_SUBJECTIVE
                else -> AmountIntent.UNSPECIFIED
            },
            quantity = stored.quantity.coerceAtLeast(1.0),
            food = catalog,
            parsedGrams = parsed.takeUnless { packHint },
            query = stored.foodName,
            explicitKilogram = kg,
        )
        return catalog to grams
    }

    private fun isBreadQuery(food: FoodItem?, query: String?): Boolean {
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name).joinToString(" "),
        )
        return BREAD_MARKERS.any { blob.contains(it) } ||
            FoodIdentity.familyFor(blob) in setOf("pan", "pan_chileno")
    }
}
