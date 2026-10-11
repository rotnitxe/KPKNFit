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

    /** The cup of a drink that declares no serving of its own: a lone drink in a meal context and one of "2 cafés" weigh this. */
    private const val DRINK_CUP_GRAMS = 220.0

    /** A "jugo en caja" is the 200 ml carton sold for one person. */
    private const val JUICE_BOX_GRAMS = 200.0

    /** Grams a per-100 g basis refers to: a denominator, not a portion anyone eats. */
    private const val NUTRIENT_DENOMINATOR_GRAMS = 100.0

    /**
     * The portions of one food of a main plate when the person gave no amount (WP-N8b), before the factor of the meal context (breakfast and dinner
     * x0.9, lunch x1.1). They are what the household references say of a plate, so that every context stays within 25 % of them:
     *  - a cooked starch (rice, pasta, potatoes), 190 g: 1.2 cups of cooked rice of 158 g (USDA FoodData Central 168878), 209 g at lunch and 171 g
     *    at dinner; the references say 200 g;
     *  - a piece of meat, poultry or fish, 125 g: the cooked chicken piece of 130 g, between half a USDA breast (86 g) and a whole one (172 g),
     *    137 g at lunch and 112 g at dinner;
     *  - any other food, 90 g: a side of 99 g at lunch.
     * They used to be 220 and 140 g for the first two: the 242 g of rice and the 154 g of chicken of a lunch plate were 20 % above the references.
     */
    private const val PLATE_STARCH_GRAMS = 190.0
    private const val PLATE_PROTEIN_GRAMS = 125.0
    private const val PLATE_SIDE_GRAMS = 90.0

    /**
     * A plate of cooked legumes (lentils, beans, chickpeas), 198 g: one cup, the USDA household weight of cooked lentils (FDC 172421). The cups of the
     * other cooked legumes weigh 164 to 182 g, so it is the upper end of the household range; a dry or raw legume has its own default.
     */
    private const val LEGUME_SERVING_GRAMS = 198.0

    /** A spoonful of manjar or dulce de leche, 20 g: a spread, never the 200 g of the milk that its name holds. OFF Chile pots declare 20 to 30 g servings. */
    private const val SPREAD_SPOONFUL_GRAMS = 20.0

    /**
     * What each part of a sandwich built from loose foods weighs inside it (WP-N8b, see [sandwichPartGrams]). Rounded household weights: two slices of
     * sandwich bread of 30 g (half a marraqueta is 50 g in the INTA table), two slices of a cold cut of 20 g (USDA deli ham, FDC 173863), one slice of
     * cheese of 30 g (1 oz), cooked chicken or meat in thin slices, 60 g, and 40 g of any other filling (two slices of tomato of 20 g).
     */
    private const val SANDWICH_BREAD_GRAMS = 60.0
    private const val SANDWICH_COLD_CUT_GRAMS = 40.0
    private const val SANDWICH_CHEESE_GRAMS = 30.0
    private const val SANDWICH_MEAT_GRAMS = 60.0
    private const val SANDWICH_AVOCADO_GRAMS = 60.0
    private const val SANDWICH_FILLING_GRAMS = 40.0

    /** The source of the recipe rows of the static catalog (WP-D1): complete dishes made of named USDA components. */
    private const val RECIPE_ESTIMATE_SOURCE = "RECIPE_ESTIMATE"

    /**
     * The piece weights, the lists of what is counted by piece and the portion defaults by family: the `householdUnits` section of
     * [FoodKnowledge] (WP-N13), read at each use so that an install takes effect at once.
     */
    private val units: HouseholdUnitsKnowledge get() = FoodKnowledge.current().householdUnits

    /** Unit id of an amount that is a count of pieces of a food that is not countable by default ("2 yogures", "media palta"). */
    const val COUNT_UNIT_ID = "unidad"

    // A cut of poultry, beef, turkey or fish has a household piece ("pechuga" 150 g, "trutro" 120 g): "2 pechugas" are two of
    // them. Rice, pasta and corn have anchors too, but those are plates and cobs of kernels, not pieces.
    private val PIECE_FAMILIES = setOf(
        FoodStapleOntology.Family.POLLO,
        FoodStapleOntology.Family.VACUNO,
        FoodStapleOntology.Family.PAVO,
        FoodStapleOntology.Family.PESCADO,
    )

    private val CHEESE_MARKERS = listOf("queso", "gouda", "gauda", "cheddar", "mantecoso")
    private val FAT_MARKERS = listOf("aceite", "mantequilla", "mayo", "mayonesa")

    /** A fat marker as a WHOLE word: "mayo" is not found inside "mayor" (WP-N11b). The text is accent-free and lower case. */
    private val FAT_PATTERN = Regex(RegexEs.bounded(FAT_MARKERS.joinToString("|")))

    /**
     * The words of a drink that is named by them: water, tea, coffee, juice, soft drinks and shakes (WP-N11b). The text is accent-free and
     * lower case; a word is whole ("mate" is not in "tomate"). Milk is one only as the first word ([DRINK_HEAD_PATTERN]): it is also in
     * the "dulce de leche" and the "arroz con leche".
     */
    private val DRINK_NAME_PATTERN = Regex(
        RegexEs.bounded("""aguas?|tes?|cafes?|bebidas?|gaseosas?|jugos?|zumos?|nectar(?:es)?|cervezas?|refrescos?|mates?|infusion(?:es)?|batidos?|licuados?|smoothies?|coca|sprite|fanta|pepsi"""),
    )

    /** "atún al agua" is a food in water, not a drink. */
    private val IN_WATER_PATTERN = Regex(RegexEs.bounded("""(?:al|en)\s+agua"""))

    /** A drink named by its first word: milk and the coffees not called "café" ("leche descremada", "capuchino"); "dulce de leche" is not one. */
    private val DRINK_HEAD_PATTERN = Regex("""^(?:leches?|capuchinos?|cortados?|expresos?|espressos?|lattes?)(?: |$)""")

    /** "helado" is also the adjective of an iced drink ("té helado"): that drink is no dessert, whichever way it is weighed. */
    private val ICED_DRINK_PATTERN = Regex(RegexEs.bounded("""(?:te|cafe|mate|agua|jugo|bebida|refresco|batido)\s+helad[oa]s?"""))

    /**
     * The weight of ONE piece of a dessert or a hot piece when it has no row of its own (WP-N11b), so that an estimate is a slice
     * and not a plate: a pie weighed as the generic dish of [HEURISTIC_DISH_GRAMS] came to 1085 kcal. Rounded household weights,
     * every one from the data the app ships or from USDA FoodData Central (FDC) household measures:
     *  - A slice of a cake or a pie, 100 g (torta, pie, kuchen, mil hojas, cheesecake, tres leches): FDC pieces of apple pie 175011
     *    (125 g, 1/8 of a 9" pie), lemon meringue pie 172785 (113 g) and commercial cheesecake 172711 (80 g; 125 g the NLEA
     *    serving); a pie de limón of 120 g and a cheesecake of 90 g in OFF Chile; the catalog's kuchen is a piece of 100 g (gen190).
     *    100 g of the dessert profile (310 kcal per 100 g) is the energy of the piece of apple pie: 125 g x 237 kcal = 296 kcal.
     *  - A queque, 70 g: the piece of the catalog row (gen152); FDC pound cake 172704 (1/6 of a loaf) is 61 g and OFF Chile has a
     *    70 g queque mármol. A brownie, 60 g: FDC 172713 (a large square) is 56 g and the median brownie of OFF Chile 61 g. An
     *    alfajor, 45 g: the median of the 31 alfajores of OFF Chile.
     *  - A cup of flan, mousse or pudding, 120 g: 110 g in OFF Chile (17 flans and a mousse); half a cup of FDC flan 167574 is 153 g.
     *  - A helado, 100 g: the serving of the catalog row (gen193); half a cup of FDC vanilla ice cream 167575 is 66 g.
     *  - A completo, 220 g: the unit weight of WP-N8; the catalog's completos are 200 g and 220 g (cl002, cl035).
     *  - A hot dog or a choripán, 180 g: a roll of 80 g (OFF Chile, "pan de hot dog x 6" is 480 g) and a frankfurter of 49 g (FDC,
     *    foundation) with their toppings; a choripán is a pan-fried chorizo of 80 g (FDC, medium link) in a marraqueta of 100 g.
     *  - A salad, 150 g (WP-N8b): the serving that the catalog declares for its Ensalada Chilena (cl028), and the 150 g of cabbage dressed with a
     *    teaspoon of oil of the blind corpus. A salad on a main plate is its side, 99 g ([inferredItemGrams]).
     *  - Papas a lo pobre, 250 g (WP-N8b): fries topped with a fried egg and fried onion, 255 g in the plate of the blind corpus: 150 g of fries
     *    (FDC 170698), 50 g of fried egg (FDC 173423), 50 g of onion (FDC 170000) and the 5 g of oil that cooked it (FDC 171413).
     * The words are whole words of the accent-free name with the optional Spanish plural; the first rule that matches wins. "chesecake" is
     * how the text normalizer leaves a "cheesecake" (it folds the double e).
     */
    private class PiecePortion(words: List<String>, val grams: Double) {
        val matcher = Regex(RegexEs.bounded("(?:" + words.joinToString("|") + ")(?:e?s)?"))
    }

    private val PIECE_PORTIONS: List<PiecePortion> = listOf(
        PiecePortion(listOf("hot dog", "hotdog", "perro caliente", "choripan"), 180.0),
        PiecePortion(listOf("papas a lo pobre", "papa a lo pobre"), 250.0),
        PiecePortion(listOf("ensalada"), 150.0),
        PiecePortion(listOf("completo"), 220.0),
        PiecePortion(listOf("flan", "mousse", "budin"), 120.0),
        PiecePortion(listOf("tres leches", "torta", "pie", "kuchen", "mil hojas", "milhojas", "cheesecake", "chesecake", "cheese cake"), 100.0),
        PiecePortion(listOf("queque", "keke"), 70.0),
        PiecePortion(listOf("brownie"), 60.0),
        PiecePortion(listOf("alfajor"), 45.0),
        PiecePortion(listOf("helado"), 100.0),
    )

    /** The weight of the piece that the accent-free [blob] names, or null when it names none of [PIECE_PORTIONS]. */
    internal fun pieceGrams(blob: String): Double? {
        val key = blob.replace(ICED_DRINK_PATTERN, " ")
        return PIECE_PORTIONS.firstOrNull { it.matcher.containsMatchIn(key) }?.grams
    }

    /** True when the [text] holds the name of a drink (a juice, a coffee, a cola, a milk) and is not a food in water. */
    internal fun isDrinkName(text: String): Boolean {
        val name = FoodIdentity.normalize(text)
        return DRINK_NAME_PATTERN.containsMatchIn(name.replace(IN_WATER_PATTERN, " ")) || DRINK_HEAD_PATTERN.containsMatchIn(name)
    }

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

    // A dry-bean legume named by its first word (WP-N8b): "lentejas (cocidas)", "porotos negros", "garbanzos". "Poroto verde" is a vegetable, and a raw or
    // dry legume has the default of its density category (45 g), not a cooked plate.
    private val LEGUME_HEAD_PATTERN = Regex("""^(?:porotos?|lentejas?|garbanzos?|frijol(?:es)?|frejol(?:es)?)(?: |$)""")
    private val GREEN_BEAN_PATTERN = Regex(RegexEs.bounded("""porotos?\s+verdes?"""))
    private val DRY_STATE_PATTERN = Regex(RegexEs.bounded("""crud[oa]s?|sec[oa]s?|deshidratad[oa]s?"""))

    // Manjar and dulce de leche are spreads by their first word; "torta de manjar" is a cake.
    private val SPREAD_HEAD_PATTERN = Regex("""^(?:manjar|dulce de leche)(?: |$)""")

    // A salad by name: "ensalada chilena", "ensaladas", "ensalada de repollo".
    private val SALAD_PATTERN = Regex(RegexEs.bounded("""ensaladas?"""))

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
        val countable = units
        if (food != null) {
            if (food.unit.equals("u", ignoreCase = true)) return true
            val family = FoodIdentity.familyFor(food)
            if (family in countable.countableFamilies) return true
            val blob = FoodIdentity.normalize(food.name + " " + food.searchAliases.joinToString(" "))
            if (countable.countableNameMarkers.any { blob.contains(it) }) return true
        }
        val q = query?.let(FoodIdentity::normalize).orEmpty()
        if (q.isBlank()) return false
        if (FoodIdentity.familyFor(q) in countable.countableFamilies) return true
        return countable.countableNameMarkers.any { q.contains(it) }
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
        // One piece of a fruit or a completo: the 100 g of a per-100 g row is a denominator, not a piece (WP-N8).
        if (isCountable(food, query)) unitWeightByToken(q, food)?.let { return it }
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

    /**
     * Typical weight of one piece of [query] from `unitGramsByToken`, by the first content word: "manzana verde" and
     * "yogurt griego" qualify, "pan con palta", "ensalada de tomate" and "tomate cherry" do not. Null for any other food and for a
     * [food] that is custom (its serving is what its owner typed).
     */
    fun unitWeightByToken(query: String?, food: FoodItem? = null): Double? {
        if (food?.isCustom == true) return null
        val tokens = FoodIdentity.contentTokens(query.orEmpty())
        val head = tokens.firstOrNull { token -> token.any(Char::isLetter) } ?: return null
        val pieces = units
        if (tokens.any { it in pieces.notAWholePiece }) return null
        return pieces.unitGramsByToken[head]
    }

    /**
     * Mass of ONE unit when the person counted a food that is not countable by default ("2 yogures", "3 tomates", "media palta",
     * "2 cervezas", "2 pechugas"), or null when the food has no unit weight of its own (WP-N8). The sources, in order: the piece of
     * `unitGramsByToken`; the serving of a drink (the catalog row declares its glass, can or copa, a branded row its own can
     * or bottle); the household piece of a poultry, beef, turkey or fish cut. Nuts, berries, dishes and every other food have none:
     * their default is a portion, and multiplying it by "20 almendras" would log 600 g.
     */
    fun countedUnitGrams(food: FoodItem?, query: String? = null, quantity: Double = 1.0): Double? {
        if (isCountable(food, query)) return unitGrams(food, query)
        return unitWeightByToken(query, food) ?: drinkServingGrams(food, query) ?: anchoredPieceGrams(food, query)
            ?: dessertPieceGrams(food, query, quantity)
    }

    /**
     * One piece of a dessert or a hot piece when the person counted several ("2 helados", "dos brownies"), N11b: the food is an estimate or a
     * row that is only its 100 g denominator, and a count of one or of a fraction is not read ("medio pie" is half a pie, not half a slice).
     */
    private fun dessertPieceGrams(food: FoodItem?, query: String?, quantity: Double): Double? =
        if (quantity >= 2.0 && (food == null || isDenominatorOnlyServing(food))) pieceGrams(FoodIdentity.normalize(query.orEmpty())) else null

    /**
     * True when a count typed before [query] must scale a unit weight. A bare "un"/"una" does so only for a food with a piece of
     * its own ("una palta" is 150 g, "un café" stays the 220 g cup it already was).
     */
    fun countAppliesTo(food: FoodItem?, query: String?, quantity: Double): Boolean {
        if (countedUnitGrams(food, query, quantity) == null) return false
        return quantity != 1.0 || unitWeightByToken(query, food) != null
    }

    // The serving of a drink: the catalog row of a drink declares its glass, can or copa (also when the person's pick is a
    // branded row of the same drink); a branded row its own can or bottle; any other drink is the usual cup, the same one a
    // lone drink gets in a meal context.
    private fun drinkServingGrams(food: FoodItem?, query: String?): Double? {
        if (food == null) return null
        if (isBeverageRow(food)) return NutrientBasis.massForServingUnits(food, food.servingSize)
        val q = FoodIdentity.normalize(query.orEmpty())
        val isDrink = q.isNotBlank() &&
            SubjectivePortionEngine.detectDensityCategory(q) == SubjectivePortionEngine.FoodDensityCategory.LIQUID
        if (!isDrink) return null
        findFoodExactByNormalized(q)?.takeIf(::isBeverageRow)?.let { return NutrientBasis.massForServingUnits(it, it.servingSize) }
        val branded = if (isGlobalSku(food)) food.portionGrams.positiveOrNull() else null
        return branded?.takeIf { isHouseholdHint(it, food, query) } ?: DRINK_CUP_GRAMS
    }

    private fun anchoredPieceGrams(food: FoodItem?, query: String?): Double? {
        val q = query.orEmpty()
        if (FoodStapleOntology.detectFamily(q) !in PIECE_FAMILIES) return null
        return FoodStapleOntology.householdDefaultGrams(q, food)
    }

    fun defaultGrams(food: FoodItem?, query: String? = null): Double {
        if (isCountable(food, query)) return unitGrams(food, query)
        FoodStapleOntology.householdDefaultGrams(query ?: "", food)?.let { return it }
        // A beverage row declares its own single serving: a glass of water, a can of soda, a copa of wine.
        food?.takeIf(::isBeverageRow)?.let { return NutrientBasis.massForServingUnits(it, it.servingSize) }
        // A prepared dish of the catalog is eaten in the serving it declares, whatever else its name says: "Pan con Queso" is a bread with cheese of
        // 100 g, not a cheese of 30 g, and the plate of "Porotos con Riendas" is its 350 g, not the 99 g of a side (WP-N8b).
        ownServingGrams(food)?.let { return it }
        // A drink that is a supermarket SKU (a branded cola) is the can or the glass it is sold in, not the 100 g of its table (WP-N8b).
        if (food != null && isGlobalSku(food) && isDrinkName(query ?: food.name)) drinkServingGrams(food, query)?.let { return it }
        // A row that is only its 100 g denominator has no piece of its own: a dessert weighs the piece that is eaten ("un alfajor" is 45 g) (WP-N11b).
        if (food != null && isDenominatorOnlyServing(food)) pieceGrams(FoodIdentity.normalize(query ?: food.name))?.let { return it }
        val queryNorm = FoodIdentity.normalize(query.orEmpty())
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name, food?.searchAliases?.joinToString(" ")).joinToString(" "),
        )
        when {
            isFatItem(food, blob) -> return 10.0
            SubjectivePortionLexicon.looksLikePortionExpression(query ?: food?.name.orEmpty()) &&
                (CHEESE_MARKERS.any { blob.contains(it) } || FoodIdentity.familyFor(blob) == "queso") ->
                return SubjectivePortionLexicon.resolve(query ?: food?.name.orEmpty(), blob)?.grams
                    ?: 40.0
            // The cheese of a prepared dish ("Pan con Queso") is not the dish: a bread with cheese is eaten as a bread (WP-N8b).
            CHEESE_MARKERS.any { blob.contains(it) } && !isPreparedDish(food) -> return 30.0
            queryNorm.contains("papas fritas") || queryNorm.contains("patatas fritas") ->
                return 120.0
            CHIPS_PATTERN.containsMatchIn(queryNorm) -> return 30.0
            queryNorm.contains("galletas") -> return 30.0
            queryNorm.contains("galleta") || queryNorm.contains("cookie") -> return 12.0
            blob.contains("chocolate") && !blob.contains("caliente") && !blob.contains("bebida") ->
                return 25.0
            blob.contains("granola") -> return 30.0
            blob.contains("avena") -> return 40.0
            isCookedLegume(food, query) -> return LEGUME_SERVING_GRAMS
            isSpread(food, query) -> return SPREAD_SPOONFUL_GRAMS
        }
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        // A bread is a piece; every other family has its default portion in the knowledge table.
        if (family == "pan" || family == "pan_chileno") return unitGrams(food, query)
        val familyGrams = family?.let { units.familyDefaultGrams[it] }
        if (familyGrams != null) return familyGrams
        return food?.let { NutrientBasis.massForServingUnits(it, getContextualDefaultServingSize(it)) }
            ?: 100.0
    }

    private fun isBeverageRow(food: FoodItem?): Boolean = food?.category.equals(BEVERAGE_CATEGORY, ignoreCase = true)

    /**
     * True when [blob] (accent-free, lower case) names a fat or a condiment by one of its words and [food] is not a prepared dish of the
     * catalog (WP-N11b). "Ave mayo" is a sandwich of 180 g and its serving wins: the fat in its name never caps it at the 15 g of a
     * spoonful of mayonnaise. A row that is only its 100 g denominator ("Mayonesa", "Aceite Vegetal") and a supermarket SKU stay fats.
     */
    private fun isFatItem(food: FoodItem?, blob: String): Boolean =
        FAT_PATTERN.containsMatchIn(blob) && !isPreparedDish(food)

    /** A prepared dish of the static catalog: a row tagged "preparacion" that is no supermarket SKU ("Ave mayo", "Sándwich de Pavita"). */
    private fun isPreparedDish(food: FoodItem?): Boolean =
        food != null && !isGlobalSku(food) && food.tags.any { it.equals("preparacion", ignoreCase = true) }

    /**
     * The serving that a prepared dish of the catalog declares (a whole sandwich of 150 g, a plate of beans of 350 g, a bowl of cazuela of 400 ml that
     * weighs 450 g), in grams; null for any other food. A dish named with no amount is eaten in this serving (WP-N8b).
     */
    private fun ownServingGrams(food: FoodItem?): Double? {
        val dish = food?.takeIf { isPreparedDish(it) } ?: return null
        // A dish of exactly 100 g ("Pan con Queso", "Pan con Jamón") declares no other serving: its macros are per 100 g, and that is also the unit it is eaten in.
        val portion = declaredPortionGrams(dish) ?: dish.servingSize.positiveOrNull() ?: return null
        // The portion of a per-100 g row is in grams already; any other row declares its serving in its own unit (g, ml or a piece).
        return if (dish.nutritionBasis.startsWith("PER_100G") && !dish.isCustom) portion else NutrientBasis.massForServingUnits(dish, portion)
    }

    /** True when the food is a cooked legume plate by name (WP-N8b); a prepared dish, a vegetable bean and a raw or dry legume are not. */
    private fun isCookedLegume(food: FoodItem?, query: String?): Boolean {
        if (isPreparedDish(food)) return false
        val names = listOfNotNull(query, food?.name).map { FoodIdentity.normalize(it) }
        if (names.none { LEGUME_HEAD_PATTERN.containsMatchIn(it) }) return false
        val blob = names.joinToString(" ")
        return !GREEN_BEAN_PATTERN.containsMatchIn(blob) && !DRY_STATE_PATTERN.containsMatchIn(blob)
    }

    /** True when the food is manjar or dulce de leche by its first word: a spread that its name classes as milk (WP-N8b). */
    private fun isSpread(food: FoodItem?, query: String?): Boolean =
        listOfNotNull(query, food?.name).any { SPREAD_HEAD_PATTERN.containsMatchIn(FoodIdentity.normalize(it)) }

    /** A complete dish row of the catalog, a prepared dish or a recipe estimate: its numbers already hold the cooking its name says (WP-N8b). */
    internal fun isCompleteDish(food: FoodItem): Boolean =
        isPreparedDish(food) || food.source.equals(RECIPE_ESTIMATE_SOURCE, ignoreCase = true)

    internal fun hasClassDefault(food: FoodItem?, query: String?): Boolean {
        if (FoodStapleOntology.hasAnchoredPortion(query ?: "", food)) return true
        if (isCountable(food, query)) return true
        // A beverage row of the catalog declares its own single serving (a glass, a can, a copa).
        if (isBeverageRow(food)) return true
        // A prepared dish declares its serving, a cooked legume is a plate and a spread is a spoonful (WP-N8b).
        if (ownServingGrams(food) != null || isCookedLegume(food, query) || isSpread(food, query)) return true
        val blob = FoodIdentity.normalize(
            listOfNotNull(query, food?.name, food?.searchAliases?.joinToString(" ")).joinToString(" "),
        )
        if (isFatItem(food, blob)) return true
        if (CHEESE_MARKERS.any { blob.contains(it) } && !isPreparedDish(food)) return true
        if (blob.contains("granola") || blob.contains("avena")) return true
        val family = food?.let(FoodIdentity::familyFor) ?: query?.let(FoodIdentity::familyFor)
        return family in setOf("huevo", "pan", "pan_chileno", "leche", "yogurt", "arroz", "avena", "pasta", "pollo", "queso", "papa")
    }

    /**
     * Locked eaten grams. Dataset / pack sizes only apply as a last hint inside
     * household range, and never after an explicit mass, utensil, or count.
     *
     * [countExpressed] (WP-N8): the amount is a count typed before a food that is not countable by default ("2 yogures",
     * "media palta", "3 cervezas"). With no mass or utensil it is [quantity] units of the food: one unit is the confirmed
     * personal portion in [datasetHint], else [countedUnitGrams], else the portion default.
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
        countExpressed: Boolean = false,
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
        if (countExpressed && intent == AmountIntent.UNSPECIFIED) {
            val unit = datasetHint?.takeIf { it.isFinite() && it > 0.0 && isHouseholdHint(it, food, query) }
                ?: countedUnitGrams(food, query, qty)
                ?: defaultGrams(food, query)
            return unit * qty
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
        // A salad that shares a main plate is its side dish: the plate's side portion, not the serving it has on its own (WP-N8b).
        if (context.shape == InferredMealContext.Shape.MAIN_PLATE && SALAD_PATTERN.containsMatchIn(blob)) {
            return (PLATE_SIDE_GRAMS * factor).coerceIn(8.0, MAX_ITEM_GRAMS_WITHOUT_KG)
        }
        // A dessert or a hot piece with no row of its own is that piece in any meal, not the filling or the side of its plate (WP-N11b).
        if (food == null) pieceGrams(blob)?.let { return it }
        // A drink that is no beverage row (a branded cola) is still its can or its glass next to a plate (WP-N11b); a drinks-only meal knows drinks.
        if (context.shape != InferredMealContext.Shape.BEVERAGE && food != null && isDrinkName(query)) {
            drinkServingGrams(food, query)?.let { return it }
        }
        val role = inferredRole(blob)
        // A prepared dish of the catalog is eaten in the serving it declares, in any plate or meal: the role of a word of its name ("pollo" of a cazuela,
        // "papa" of a pastel) does not size it (WP-N8b).
        ownServingGrams(food)?.let { return it.coerceIn(8.0, MAX_ITEM_GRAMS_WITHOUT_KG) }
        // A fat or a spread in a meal is the spoonful it is, not the filling of its shape: "mantequilla" is 10 g, two pats of 5 g (USDA household weight).
        if (isFatItem(food, blob)) return defaultGrams(food, query).coerceIn(8.0, MAX_ITEM_GRAMS_WITHOUT_KG)
        val grams = when (context.shape) {
            InferredMealContext.Shape.MAIN_PLATE -> when (role) {
                "starch" -> PLATE_STARCH_GRAMS * factor
                "protein" -> if (blob.contains("huevo")) unitGrams(food, query) else PLATE_PROTEIN_GRAMS * factor
                // A dry cereal or dairy portion has its own prior; it is not a generic side dish.
                else -> if (hasClassDefault(food, query)) defaultGrams(food, query) else PLATE_SIDE_GRAMS * factor
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
                CHEESE_MARKERS.any { blob.contains(it) } -> SANDWICH_CHEESE_GRAMS
                blob.contains("palta") -> SANDWICH_AVOCADO_GRAMS
                // A prepared dish of the catalog ("Sándwich de Pavita", 150 g) keeps its serving, and a drink next to a sandwich is the glass,
                // the cup or the can it is: neither is the 40 g of a filling (WP-N11b).
                else -> ownServingGrams(food)
                    ?: if (isDrinkName(query)) drinkServingGrams(food, query) ?: loneDrinkGrams(food, query) else SANDWICH_FILLING_GRAMS
            }
            // The cup is the portion of a drink: any other food of a drinks-only meal ("ave mayo y un jugo") keeps its own portion (WP-N11b).
            InferredMealContext.Shape.BEVERAGE ->
                if (food != null && !isDrinkName(query)) ownServingGrams(food) ?: defaultGrams(food, query) else loneDrinkGrams(food, query)
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

    // A lone drink of a meal context is the usual cup, a juice the glass it is counted in: "jugo natural de naranja" weighs what
    // "un jugo de naranja" does, so its size ("grande") enlarges the same glass.
    private fun loneDrinkGrams(food: FoodItem?, query: String?): Double =
        if (JUICE_HEAD_PATTERN.containsMatchIn(FoodIdentity.normalize(query.orEmpty()))) unitGrams(food, query) else DRINK_CUP_GRAMS

    fun isWholeDish(query: String): Boolean = WHOLE_DISH_PATTERN
        .containsMatchIn(FoodIdentity.normalize(query))

    /**
     * The portion of one part of a sandwich built from loose foods ("sándwich de jamón y queso": the bread and what goes in it; WP-N8b). A part
     * weighs what it does inside a sandwich, not what the food weighs on a plate: the 100 g of pan and the 100 g of ham came from their standalone
     * defaults, and the 150 g of chicken from the plate. The bread is two slices ([SANDWICH_BREAD_GRAMS]), a cold cut two slices, cheese one slice,
     * poultry, meat and tuna thin slices, an avocado a share, and any other filling [SANDWICH_FILLING_GRAMS]. Null for an egg and for a prepared dish,
     * which have a piece or a serving of their own; the caller caps a sauce at its spoonful.
     */
    fun sandwichPartGrams(food: FoodItem?, name: String, isBread: Boolean): Double? {
        if (isBread) return SANDWICH_BREAD_GRAMS
        if (ownServingGrams(food) != null) return null
        val blob = FoodIdentity.normalize("$name ${food?.name.orEmpty()}")
        val family = FoodStapleOntology.detectFamily(blob)
        return when {
            blob.contains("huevo") -> null
            CHEESE_MARKERS.any { blob.contains(it) } || FoodIdentity.familyFor(blob) == "queso" -> SANDWICH_CHEESE_GRAMS
            COLD_CUT_MARKERS.any { blob.contains(it) } || blob.contains("pavo") -> SANDWICH_COLD_CUT_GRAMS
            blob.contains("palta") -> SANDWICH_AVOCADO_GRAMS
            family in PIECE_FAMILIES || family == FoodStapleOntology.Family.ATUN -> SANDWICH_MEAT_GRAMS
            else -> SANDWICH_FILLING_GRAMS
        }
    }

    fun heuristicDishGrams(query: String, context: ContextDetector.ContextResult? = null): Double {
        val blob = FoodIdentity.normalize(query)
        // A dessert or a hot piece weighs the piece that is eaten, not the generic plate (WP-N11b, see [PIECE_PORTIONS]).
        pieceGrams(blob)?.let { return it }
        // A whole mixed dish keeps a meal portion even if its name includes cheese/oil.
        if (isWholeDish(query)) return 250.0
        if (isNoodleDish(blob)) return 320.0
        val raw = when (context?.shape) {
            InferredMealContext.Shape.WRAP -> 120.0
            InferredMealContext.Shape.BEVERAGE -> loneDrinkGrams(null, query)
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
        // The cheese, the nuts or the chocolate in the name of a prepared dish of the catalog do not make it a snack: "Pan con Queso" is a bread (WP-N8b).
        if (isPreparedDish(food)) return false
        val blob = FoodIdentity.normalize("$query ${food?.name.orEmpty()}")
        if (isFatItem(food, blob)) return true
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
        if (isFatItem(food, blob)) return 15.0
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

    /**
     * True for a row imported from OpenFoodFacts or USDA: a supermarket SKU or a laboratory record. The id says so ("off_<barcode>",
     * "usda_<fdcId>"); the free text of `source` does not, because a curated row cites its reference there ("USDA SR Legacy
     * (rounded)" is still a catalog row). Only a row with no id falls back to the text of `source`.
     */
    fun isGlobalSku(food: FoodItem): Boolean {
        if (food.id.isNotBlank()) return food.id.startsWith("off_", ignoreCase = true) || food.id.startsWith("usda_", ignoreCase = true)
        val src = food.source.orEmpty().uppercase()
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
