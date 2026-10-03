package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.findStaticFoodById
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

    /** A liquid in a declared container ("2 latas", a 1 L carton) is no pack-sized meal: its mass cap is higher. */
    const val MAX_LIQUID_GRAMS_WITHOUT_KG = 1500.0

    /** [FoodItem.category] of the drinks of the static catalog (water, tea, soda, beer, wine...). */
    const val BEVERAGE_CATEGORY = "bebida"

    /** One counted drink with no vessel named ("un jugo de naranja", "2 jugos") is one standard glass, 250 ml. */
    private const val GLASS_GRAMS = 250.0

    /** A "jugo en caja" is the 200 ml carton sold for one person. */
    private const val JUICE_BOX_GRAMS = 200.0

    /** Grams a per-100 g basis refers to: a denominator, not a portion anyone eats. */
    private const val NUTRIENT_DENOMINATOR_GRAMS = 100.0

    private val COUNTABLE_FAMILIES = setOf(
        "pan_chileno", "pan", "huevo", "empanada", "wrap",
    )

    private val COUNTABLE_NAME_MARKERS = listOf(
        "hallulla", "hallula", "marraqueta", "sopaipilla", "empanada",
        "completo", "huevo", "galleta", "arepa", "pan amasado", "panecillo",
        "manzana", "platano", "naranja", "pera", "kiwi",
        "taco", "burrito", "sushi", "wrap", "hamburguesa",
        "galleta", "galletas",
    )

    private val CHEESE_MARKERS = listOf("queso", "gouda", "gauda", "cheddar", "mantecoso")
    private val FAT_MARKERS = listOf("aceite", "mantequilla", "mayo", "mayonesa")
    private val NUT_MARKERS = listOf(
        "almendra", "nuez", "mani", "maní", "cacahuate", "pistacho", "avellana",
        "nueces", "pecana", "marañon", "maranon",
    )
    private val CHOCOLATE_CANDY_MARKERS = listOf("chocolate", "cacao")
    private val COLD_CUT_MARKERS = listOf(
        "cecina", "jamon", "jamón", "salame", "mortadela", "tocino", "fiambre",
    )
    private val BREAD_MARKERS = listOf(
        "hallulla", "hallula", "marraqueta", "pan", "sopaipilla", "amasado",
    )

    // Compiled once: these used to be rebuilt on every call (some once per candidate).
    private val PACK_NAME_PATTERN = Regex("""\b(?:\d+\s*kg|kilo|pack|packe|x\s*\d+|x\d+)\b""")
    private val CHIPS_PATTERN = Regex("""\bchips\b""")
    private val WHOLE_DISH_PATTERN = Regex("""\b(?:sandwich|completo|hamburguesa|torta|quesadilla)\b""")
    private val VESSEL_PATTERN = Regex("""\b(?:plato|bowl|bol|tazon|taza|vaso|fuente)\b""")
    private val COUNT_EXPRESSION_PATTERN = Regex(
        """^(?:un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio|\d+(?:[.,]\d+)?)\s+\S+""",
    )
    // An amount stated in a bulk unit: kilograms or litres ("2 kg", "1 kilogramo", "2 litros", "1 l", "1 lt").
    private val EXPLICIT_BULK_UNIT_PATTERN = Regex(
        """\b\d+(?:[.,]\d+)?\s*(?:kg|kilos?|kilogramos?|l|lts?|litros?)\b""",
    )

    // A juice named by its head noun ("jugo de naranja", "zumo", "nectar"): counted in glasses, never in fruits.
    private val JUICE_HEAD_PATTERN = Regex("""^(?:jugos?|zumos?|nectar(?:es)?)(?: |$)""")

    fun looksLikePackName(name: String): Boolean {
        val n = FoodIdentity.normalize(name)
        return PACK_NAME_PATTERN.containsMatchIn(n) ||
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
        // "un jugo de naranja" counts glasses of juice ([unitGrams]); it is not an orange, whatever fruit it names.
        if (query != null && JUICE_HEAD_PATTERN.containsMatchIn(FoodIdentity.normalize(query))) return true
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
        if (JUICE_HEAD_PATTERN.containsMatchIn(q)) return if (q.contains("caja")) JUICE_BOX_GRAMS else GLASS_GRAMS
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
        if (q.contains("galletas")) return 30.0
        if (q.contains("galleta") || q.contains("cookie")) return 12.0
        if (FoodIdentity.familyFor(q) == "huevo" || q.contains("huevo")) {
            return food?.servingSize?.takeIf { it in 40.0..70.0 } ?: 50.0
        }
        // A per-100 g row with no declared portion has no serving of its own: its 100 g is the
        // nutrient denominator, so the unit mass comes from the family defaults below.
        if (food != null && !isDenominatorOnlyServing(food)) {
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
        return when (FoodIdentity.familyFor(q) ?: food?.let(FoodIdentity::familyFor)) {
            "pan_chileno" -> 80.0
            "pan" -> 50.0
            "huevo" -> 50.0
            else -> 100.0
        }
    }

    fun defaultGrams(food: FoodItem?, query: String? = null): Double {
        if (isCountable(food, query)) return unitGrams(food, query)
        FoodStapleOntology.householdDefaultGrams(query ?: "", food)?.let { return it }
        // A beverage row declares its own single serving: a glass of water, a can of soda, a copa of wine.
        food?.takeIf(::isBeverageRow)?.let { return NutrientBasis.massForServingUnits(it, it.servingSize) }
        val queryNorm = FoodIdentity.normalize(query.orEmpty())
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name, food?.searchAliases?.joinToString(" ")).joinToString(" "),
        )
        when {
            FAT_MARKERS.any { blob.contains(it) } -> return 10.0
            SubjectivePortionLexicon.looksLikePortionExpression(query ?: food?.name.orEmpty()) &&
                (CHEESE_MARKERS.any { blob.contains(it) } || FoodIdentity.familyFor(blob) == "queso") ->
                return SubjectivePortionLexicon.resolve(query ?: food?.name.orEmpty(), blob)?.grams
                    ?: 40.0
            CHEESE_MARKERS.any { blob.contains(it) } -> return 30.0
            queryNorm.contains("papas fritas") || queryNorm.contains("patatas fritas") ->
                return 120.0
            CHIPS_PATTERN.containsMatchIn(queryNorm) -> return 30.0
            queryNorm.contains("galletas") -> return 30.0
            queryNorm.contains("galleta") || queryNorm.contains("cookie") -> return 12.0
            blob.contains("chocolate") && !blob.contains("caliente") && !blob.contains("bebida") ->
                return 25.0
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
            "papa" -> 100.0
            "tomate" -> 80.0
            "palta" -> 80.0
            else -> {
                food?.let { NutrientBasis.massForServingUnits(it, getContextualDefaultServingSize(it)) }
                    ?: 100.0
            }
        }
    }

    private fun isBeverageRow(food: FoodItem?): Boolean = food?.category.equals(BEVERAGE_CATEGORY, ignoreCase = true)

    internal fun hasClassDefault(food: FoodItem?, query: String?): Boolean {
        if (FoodStapleOntology.hasAnchoredPortion(query ?: "", food)) return true
        if (isCountable(food, query)) return true
        // A beverage row of the catalog declares its own single serving (a glass, a can, a copa).
        if (isBeverageRow(food)) return true
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name, food?.searchAliases?.joinToString(" ")).joinToString(" "),
        )
        if (FAT_MARKERS.any { blob.contains(it) }) return true
        if (CHEESE_MARKERS.any { blob.contains(it) }) return true
        if (blob.contains("granola") || blob.contains("avena")) return true
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        return family in setOf("huevo", "pan", "pan_chileno", "leche", "yogurt", "arroz", "avena", "pasta", "pollo", "queso", "papa")
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
        unitId: String? = null,
    ): Double {
        val qty = quantity.coerceAtLeast(0.01)
        val parsed = parsedGrams?.takeIf { it.isFinite() && it > 0.0 }
        // A resolved vessel/slice/count already includes its quantity and fraction.
        if (unitId != null && parsed != null && intent == AmountIntent.RESOLVED_SUBJECTIVE) return parsed
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
                    return remapGenericContainerGrams(food, query, parsed)
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
        // A drink keeps its own serving whatever plate surrounds it: "un completo y una coca cola" is not 40 g of cola.
        if (isBeverageRow(food)) return defaultGrams(food, query)
        val factor = context.primaryContext.portionFactor.coerceIn(0.55, 1.45)
        val blob = FoodIdentity.normalize("$query ${food?.name.orEmpty()}")
        val role = inferredRole(blob)
        val grams = when (context.shape) {
            InferredMealContext.Shape.MAIN_PLATE -> when (role) {
                "starch" -> 220.0 * factor
                "protein" -> if (blob.contains("huevo")) unitGrams(food, query) else 140.0 * factor
                // A dry cereal or dairy portion has its own prior; it is not a generic side dish.
                else -> if (hasClassDefault(food, query)) defaultGrams(food, query) else 90.0 * factor
            }
            InferredMealContext.Shape.BREAKFAST_BOWL -> when {
                blob.contains("avena") -> 40.0
                blob.contains("granola") || blob.contains("nuez") || blob.contains("almendra") -> 30.0
                blob.contains("yogurt") || blob.contains("leche") -> 200.0
                blob.contains("platano") || blob.contains("fruta") || blob.contains("banana") ||
                    blob.contains("frutilla") || blob.contains("arandano") -> 100.0
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
            InferredMealContext.Shape.WRAP -> when {
                blob.contains("quesadilla") ||
                    CHEESE_MARKERS.any { blob.contains(it) } ||
                    food?.let { FoodIdentity.familyFor(it) == "queso" } == true ->
                    30.0
                else -> unitGrams(food, query)
            }
            InferredMealContext.Shape.UNKNOWN -> heuristicDishGrams(query, context)
            else -> defaultGrams(food, query)
        }
        return capEnergyDenseGuess(food, query, grams, role).coerceIn(8.0, MAX_ITEM_GRAMS_WITHOUT_KG)
    }

    fun isWholeDish(query: String): Boolean = WHOLE_DISH_PATTERN
        .containsMatchIn(FoodIdentity.normalize(query))

    fun heuristicDishGrams(query: String, context: ContextDetector.ContextResult? = null): Double {
        val blob = FoodIdentity.normalize(query)
        // A whole mixed dish keeps a meal portion even if its name includes cheese/oil.
        if (isWholeDish(query)) return 250.0
        if (isNoodleDish(blob)) return 320.0
        val raw = when (context?.shape) {
            InferredMealContext.Shape.WRAP -> 120.0
            InferredMealContext.Shape.BEVERAGE -> 220.0
            InferredMealContext.Shape.BREAKFAST_BOWL -> inferredItemGrams(null, query, context)
            InferredMealContext.Shape.SNACK_ITEM -> defaultGrams(null, query)
            else -> HEURISTIC_DISH_GRAMS
        }
        val role = inferredRole(blob)
        return capEnergyDenseGuess(null, query, raw, role)
    }

    fun isNoodleDish(query: String): Boolean {
        val blob = FoodIdentity.normalize(query)
        return listOf(
            "pad thai", "padthai", "ramen", "pho",
            "fideos salteados", "fideo salteado", "chow mein", "yakisoba",
        ).any { blob.contains(FoodIdentity.normalize(it)) }
    }

    /**
     * A generic plate/bowl volume (200–400 g) of dry cereal is a serving, not a
     * cooked plate. Yogurt in a bowl is a cup, not 300 ml of density math.
     */
    internal fun remapGenericContainerGrams(food: FoodItem?, query: String?, parsed: Double): Double {
        val blob = FoodIdentity.normalize("$query ${food?.name.orEmpty()}")
        val isDryCereal = listOf("avena", "granola", "muesli", "cereal").any { blob.contains(it) } &&
            !blob.contains("cocid") && !blob.contains("hidrat")
        if (isDryCereal && parsed in 150.0..450.0) {
            return if (blob.contains("granola")) 30.0 else defaultGrams(food, query)
        }
        val isYogurt = blob.contains("yogurt") || blob.contains("yogur")
        if (isYogurt && parsed in 240.0..400.0) return 180.0
        return parsed
    }

    private fun inferredRole(blob: String): String = when {
        listOf("arroz", "pasta", "papa", "fideo", "quinoa", "couscous", "ramen", "noodle", "pho", "pad thai").any { blob.contains(it) } -> "starch"
        listOf("pollo", "huevo", "carne", "pescado", "atun", "salmon", "cerdo", "vacuno", "pechuga").any { blob.contains(it) } -> "protein"
        else -> "side"
    }

    /**
     * Dense foods without an explicit vessel must not inherit a 350 g plate.
     * Never applies to EXPLICIT_MASS (caller already locked grams).
     */
    internal fun capEnergyDenseGuess(
        food: FoodItem?,
        query: String,
        grams: Double,
        role: String,
    ): Double {
        if (!grams.isFinite() || grams <= 0.0) return grams
        if (SubjectivePortionLexicon.looksLikePortionExpression(query) &&
            SubjectivePortionLexicon.resolve(query) != null
        ) {
            return grams
        }
        if (hasExplicitVessel(query)) return grams
        if (!isEnergyDenseFood(food, query, role)) return grams
        return minOf(grams, energyDenseHouseholdCap(food, query))
    }

    private fun hasExplicitVessel(query: String): Boolean {
        val blob = FoodIdentity.normalize(query)
        return VESSEL_PATTERN.containsMatchIn(blob)
    }

    private fun isEnergyDenseFood(food: FoodItem?, query: String, role: String): Boolean {
        val blob = FoodIdentity.normalize("$query ${food?.name.orEmpty()}")
        if (FAT_MARKERS.any { blob.contains(it) }) return true
        if (CHEESE_MARKERS.any { blob.contains(it) } || FoodIdentity.familyFor(blob) == "queso") return true
        if (NUT_MARKERS.any { blob.contains(it) }) return true
        if (CHOCOLATE_CANDY_MARKERS.any { blob.contains(it) } &&
            !blob.contains("caliente") &&
            !blob.contains("bebida") &&
            !blob.contains("leche")
        ) {
            return true
        }
        if (COLD_CUT_MARKERS.any { blob.contains(it) } && role != "protein") return true
        return false
    }

    private fun energyDenseHouseholdCap(food: FoodItem?, query: String): Double {
        val blob = FoodIdentity.normalize("$query ${food?.name.orEmpty()}")
        if (FAT_MARKERS.any { blob.contains(it) }) return 15.0
        if (NUT_MARKERS.any { blob.contains(it) }) return 35.0
        if (CHOCOLATE_CANDY_MARKERS.any { blob.contains(it) }) return 40.0
        if (CHEESE_MARKERS.any { blob.contains(it) } || FoodIdentity.familyFor(blob) == "queso") return 40.0
        if (COLD_CUT_MARKERS.any { blob.contains(it) }) return 40.0
        return 40.0
    }

    fun looksLikeCountExpression(text: String): Boolean {
        val t = text.lowercase().trim()
        return COUNT_EXPRESSION_PATTERN.containsMatchIn(t)
    }

    /**
     * True when the text states its amount in a bulk unit, kilograms or litres: such a mass is what the person ate,
     * never a pack default to cap ("2 litros de agua", "1 kg de papas"). Legacy name: the drawer and the warm-up call it.
     */
    fun isExplicitKilogram(text: String): Boolean = isExplicitBulkUnit(text)

    fun isExplicitBulkUnit(text: String): Boolean {
        val t = FoodIdentity.normalize(text)
        return EXPLICIT_BULK_UNIT_PATTERN.containsMatchIn(t) ||
            t.contains("medio kilo") || t.contains("medio kilogramo") || t.contains("medio litro")
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
        // Zero energy has no calories to cap: a litre of water is not a pack-sized meal.
        if (NutrientBasis.isZeroEnergy(food)) return true
        val serving = NutrientBasis.grams(food)
        val kcal = food.calories * grams / serving
        val maxGrams = if (isLiquidFood(food)) MAX_LIQUID_GRAMS_WITHOUT_KG else MAX_ITEM_GRAMS_WITHOUT_KG
        return kcal <= MAX_ITEM_KCAL_WITHOUT_KG && grams <= maxGrams
    }

    /** A volume-measured row ("ml"/"l") or a drink by name: its mass is a container's, not a pack's. */
    private fun isLiquidFood(food: FoodItem): Boolean =
        food.unit.trim().lowercase() in setOf("ml", "l") ||
            SubjectivePortionEngine.detectDensityCategory(food.name) == SubjectivePortionEngine.FoodDensityCategory.LIQUID

    fun catalogFoodFor(query: String): FoodItem? = householdStaticFood(query)

    /** Static catalog only. Never an OFF/USDA supermarket SKU. Exact phrase beats a longer dish. */
    fun householdStaticFood(tag: String): FoodItem? {
        FoodStapleOntology.defaultFoodIdForFamily(tag)?.let { id ->
            findStaticFoodById(id)?.let { return it }
        }
        FoodStapleOntology.resolveFoodId(tag)?.let { id ->
            findStaticFoodById(id)?.let { return it }
        }
        return (findFoodExactByNormalized(tag) ?: findFoodByNormalized(tag))
            ?.takeUnless { isGlobalSku(it) }
    }

    fun isGlobalSku(food: FoodItem): Boolean {
        val src = food.source.orEmpty().uppercase()
        if (food.id.startsWith("off_", ignoreCase = true)) return true
        return src.contains("OFF") || src.contains("USDA")
    }

    fun isHouseholdIdentity(food: FoodItem, brandHint: String? = null): Boolean {
        // Generic USDA composition profiles are valid household foods. OFF SKUs
        // remain available to explicit product searches without inferring pack mass.
        if (isGlobalSku(food)) return !brandHint.isNullOrBlank() || !looksLikePackName(food.name)
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
        identityAccepted: Boolean = true,
        // The person tapped this exact row in the search tab: a supermarket SKU is a valid identity.
        explicitPick: Boolean = false,
    ): FoodResolutionStatus {
        if (food == null || (!explicitPick && !isHouseholdIdentity(food, brandHint))) {
            return FoodResolutionStatus.NO_RESOLVED
        }
        if (!FoodIdentity.hasPlausibleMacros(food)) return FoodResolutionStatus.NO_RESOLVED
        if (!grams.isFinite() || grams <= 0.0) return FoodResolutionStatus.NO_RESOLVED
        if (!identityAccepted) return FoodResolutionStatus.NEEDS_CONFIRMATION
        val massOk = explicitKilogram ||
            amountIntent == AmountIntent.EXPLICIT_MASS ||
            itemKcalIsPlausible(food, grams, explicitKilogram)
        return if (massOk) FoodResolutionStatus.AUTO else FoodResolutionStatus.NO_RESOLVED
    }

    /**
     * Tab Buscar: the person tapped one specific row, so that row IS the eaten identity.
     * A supermarket SKU or a per-100 g profile only changes the portion ([eatenGramsForSearchPick]),
     * never the food: swapping the row (or silently doing nothing) used to log a different product.
     */
    @Suppress("UNUSED_PARAMETER")
    fun identityForSearchPick(selected: FoodItem, query: String): FoodItem = selected

    /**
     * Grams eaten after tapping [identity] while searching for [query].
     *
     * - Explicit kilograms in the query keep the row's declared mass (`portionGrams`, else `servingSize`).
     * - Otherwise only a household-sized declared portion applies: the 100 g of a per-100 g basis is a
     *   nutrient denominator, never a portion, and a pack mass is never what a person ate.
     * - Everything else falls back to the family defaults (milk 200 g, yogurt 125 g, egg 50 g, ...).
     */
    fun eatenGramsForSearchPick(identity: FoodItem, query: String): Double {
        val kg = isExplicitKilogram(query)
        val parsed = if (kg) {
            identity.portionGrams.positiveOrNull() ?: identity.servingSize.positiveOrNull()
        } else {
            declaredPortionGrams(identity)?.takeIf { isHouseholdHint(it, identity, query) }
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
     * True for a per-100 g row whose only "serving" is the default 100 g (no `portionGrams`): every
     * OFF/USDA row built by `GlobalFoodEntity.toFoodItem()` and the curated per-100 g rows that never
     * declared a portion. That 100 g is the nutrient denominator, not something a person eats. Custom
     * foods are excluded: their `servingSize` is always the serving the user typed.
     */
    private fun isDenominatorOnlyServing(food: FoodItem): Boolean =
        !food.isCustom &&
            food.nutritionBasis.startsWith("PER_100G") &&
            food.portionGrams.positiveOrNull() == null &&
            kotlin.math.abs(food.servingSize - NUTRIENT_DENOMINATOR_GRAMS) < 1e-6

    /** The one portion the row itself declares, in grams; null when its only serving is the denominator. */
    private fun declaredPortionGrams(food: FoodItem): Double? = when {
        isDenominatorOnlyServing(food) -> null
        food.isCustom || !food.nutritionBasis.startsWith("PER_100G") -> food.servingSize.positiveOrNull()
        else -> food.portionGrams.positiveOrNull() ?: food.servingSize.positiveOrNull()
    }

    private fun Double?.positiveOrNull(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

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
