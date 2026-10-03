package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem

/**
 * NutritionHeuristicEstimator — Keyword-based macro estimator for foods not in the database.
 *
 * Used as fallback when:
 *   - Mode is BASIC (rules-only, no AI)
 *   - PRO mode but Qwen model not loaded / timeout
 *   - AI returned items without nutritionPer100g for some entries
 *
 * Keyword profiles are provisional estimates, not a measured accuracy guarantee.
 * A missing keyword is explicitly distinguished from a referenced composition.
 * Values are per 100g.
 */

data class NutritionProfile(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

/** Density uncertainty is independent of the certainty of the consumed mass. */
data class NutritionEstimateEvidence(
    val assumption: String,
    val referenceFoodIds: List<String> = emptyList(),
    val referenceSourceRecordIds: List<String> = emptyList(),
    val minPer100g: NutritionProfile,
    val maxPer100g: NutritionProfile,
    val requiresCompositionClarification: Boolean = false,
    val isUnmatchedFallback: Boolean = false,
)

data class NutritionEstimate(val profile: NutritionProfile, val evidence: NutritionEstimateEvidence)

// ─── Profiles per 100g ───────────────────────────────────────────────────────

private val LEAN_PROTEIN    = NutritionProfile(165.0, 31.0,  0.0,  3.6)
private val FATTY_PROTEIN   = NutritionProfile(220.0, 26.0,  0.0, 12.0)
private val FISH_LEAN       = NutritionProfile(120.0, 22.0,  0.0,  3.0)
private val FISH_FATTY      = NutritionProfile(200.0, 20.0,  0.0, 13.0)
private val EGG_PROFILE     = NutritionProfile(155.0, 13.0,  1.0, 11.0)
private val LEGUME          = NutritionProfile(116.0,  9.0, 20.0,  0.4)
private val GRAIN_COOKED    = NutritionProfile(130.0,  3.0, 27.0,  0.5)
private val GRAIN_DRY       = NutritionProfile(360.0, 13.0, 68.0,  6.0)
private val PASTA_COOKED    = NutritionProfile(131.0,  5.0, 25.0,  1.1)
private val BREAD           = NutritionProfile(265.0,  9.0, 49.0,  3.0)
private val STARCHY_VEG     = NutritionProfile( 85.0,  1.9, 20.0,  0.1)
private val DAIRY_LIQUID    = NutritionProfile( 62.0,  3.2,  4.8,  3.4)
private val DAIRY_THICK     = NutritionProfile( 97.0,  9.0,  3.9,  5.0)  // yogurt / cottage
private val CHEESE          = NutritionProfile(350.0, 22.0,  2.0, 28.0)
private val OIL_FAT         = NutritionProfile(780.0,  0.0,  0.0, 87.0)
private val NUTS_SEEDS      = NutritionProfile(580.0, 20.0, 20.0, 50.0)
private val FRUIT           = NutritionProfile( 60.0,  0.8, 14.0,  0.3)
private val VEGETABLE       = NutritionProfile( 28.0,  2.0,  5.0,  0.3)
private val PROTEIN_POWDER  = NutritionProfile(380.0, 80.0,  8.0,  5.0)
private val SUGAR_SWEET     = NutritionProfile(380.0,  0.5, 90.0,  0.2)
private val PROCESSED_MEAT  = NutritionProfile(240.0, 16.0,  2.0, 19.0)
private val SAUCE_DRESSING  = NutritionProfile(250.0,  1.5, 15.0, 20.0)
private val MIXED_DISH      = NutritionProfile(160.0, 10.0, 16.0,  6.0)  // generic fallback
private val NOODLE_DISH     = NutritionProfile(165.0,  7.0, 22.0,  6.0)

// Dessert (WP-N11), per 100 g: the median of 13 sourced rows. USDA FoodData Central (data/usdaFoodsOffline.json): flan 167574
// (145 kcal), vanilla ice cream 167575 (207), apple pie 175011 (237), lemon meringue pie 172785 (268), sponge cake 172706 (290),
// fruit coffeecake 174937 (311, the kuchen row of the catalog), pound cake 172704 (353, the queque row), commercial cheesecake
// 172711 (321), iced cream puff 2708031 (334, the closest to a mil hojas), commercial brownies 172713 (405) and brownies from
// recipe 174949 (466); and the catalog's own "tres leches" (280) and homemade alfajor (390). The median is 311 kcal, 5.0 g
// protein and 12.0 g fat, with carbohydrate by difference (46 g) so that 4/4/9 closes; DESSERT_RANGE holds the lowest and the
// highest value of each nutrient over the same rows.
private val DESSERT         = NutritionProfile(310.0,  5.0, 46.0, 12.0)

// Hot dog / "completo" (WP-N11), per 100 g: the app's two curated completos, "Completo Italiano" (cl002, 380 kcal per 200 g) and
// "Completo Americano" (cl035, 420 kcal per 220 g), are 190 and 191 kcal, 6 g protein, 16 g carbohydrate and 11 g fat. The plain
// parts are denser (USDA hot dog roll 172796: 279 kcal, frankfurter 172968: 290 kcal), so this is the figure of a dressed one.
private val HOT_DOG         = NutritionProfile(190.0,  6.0, 16.0, 11.0)

/** Marginal per-100 g bounds of a profile that has a sourced range (not a confidence interval): the lowest and highest value of each nutrient. */
private class SourcedRange(val min: NutritionProfile, val max: NutritionProfile)

private val DESSERT_RANGE = SourcedRange(
    min = NutritionProfile(145.0, 1.5, 22.8, 2.7),
    max = NutritionProfile(466.0, 6.2, 63.9, 29.1),
)

/** The profiles whose evidence carries a sourced range instead of the generic 0..900 kcal of a category guess. */
private val SOURCED_RANGES: Map<NutritionProfile, SourcedRange> = mapOf(DESSERT to DESSERT_RANGE)

// ─── Keyword → Profile mapping ───────────────────────────────────────────────
// Rules are checked in order — more specific rules first. A keyword names the food only as a whole word (or whole words) of the
// name, never inside a longer word (WP-N11): "repollo" is not "pollo", "fresa" and "fresco" are not "res", "papaya" is not "papa",
// and "tres leches" is a dessert, not three milks. See PROFILE_MATCHERS.

private val KEYWORD_PROFILES: List<Pair<List<String>, NutritionProfile>> = listOf(

    // ── Platos de fideos (antes que "pan"/"thai" residuales y MIXED_DISH) ──
    listOf(
        "pad thai", "padthai", "pad-thai",
        "ramen", "pho", "fideos salteados", "fideo salteado",
        "chow mein", "yakisoba",
    ) to NOODLE_DISH,

    // ── Suplementos (antes que cualquier otro match) ──────────────────────────
    listOf(
        "whey", "proteína en polvo", "proteina en polvo",
        "caseína", "caseina", "suplemento proteico", "mass gainer", "gainers"
    ) to PROTEIN_POWDER,

    // ── Pastas de frutos secos (antes que "pasta" y "mantequilla"): la pasta de maní no son fideos ni aceite ──
    // USDA: peanut butter 172470 (598 kcal), almond butter 168588 (614), tahini 168604 (592).
    listOf(
        "pasta de mani", "crema de mani", "mantequilla de mani",
        "pasta de almendras", "crema de almendras", "mantequilla de almendras",
        "mantequilla de nueces", "tahini",
    ) to NUTS_SEEDS,

    // ── Completo / hot dog (antes que embutidos y proteínas: un completo con vienesa es un completo) ──
    listOf(
        "completo", "completo italiano", "completo americano", "hot dog", "hotdog", "perro caliente",
    ) to HOT_DOG,

    // ── Proteínas magras ──────────────────────────────────────────────────────
    listOf(
        "pechuga de pollo", "pechuga de pavo", "pollo a la plancha",
        "pechuga", "pechuga cocida", "pollo cocido", "pavo cocido",
        "clara de huevo", "claras de huevo", "claras",
        "atún", "atun", "merluza", "tilapia", "corvina", "reineta",
        "camarón", "camaron", "langostino", "pulpo", "calamar",
        "pollo", "pavo"
    ) to LEAN_PROTEIN,

    // ── Proteínas con grasa ───────────────────────────────────────────────────
    listOf(
        "carne molida", "carne de vacuno", "carne de res", "carne de cerdo",
        "asado", "costilla", "costillar", "lomo vetado", "punta de ganso", "tapapecho", "huachalomo",
        "paleta", "pierna de cerdo", "chuleta", "filete", "lomo",
        "vacuno", "res", "cerdo", "carne"
    ) to FATTY_PROTEIN,

    // ── Pescado graso ─────────────────────────────────────────────────────────
    listOf("salmón", "salmon", "sardina", "caballa", "atún en aceite", "jurel") to FISH_FATTY,

    // ── Pescado magro ─────────────────────────────────────────────────────────
    listOf("pescado", "congrio", "lenguado", "bacalao") to FISH_LEAN,

    // ── Huevo ─────────────────────────────────────────────────────────────────
    listOf("huevo duro", "huevo cocido", "huevo frito", "huevo revuelto",
        "huevos duros", "huevo", "huevos") to EGG_PROFILE,

    // ── Embutidos / procesados ────────────────────────────────────────────────
    listOf(
        "jamón", "jamon", "salchicha", "vienesa", "longaniza",
        "mortadela", "salame", "cecina", "tocino", "panceta"
    ) to PROCESSED_MEAT,

    // ── Postres (antes que legumbres, lácteos, frutas y dulces: "helado de frutilla" y "torta de zanahoria" son postres) ──
    listOf(
        "tres leches", "torta", "queque", "kuchen", "helado", "pie", "flan",
        "mil hojas", "milhojas", "brownie", "alfajor",
    ) to DESSERT,

    // ── Legumbres ─────────────────────────────────────────────────────────────
    listOf(
        "lentejas", "lenteja", "garbanzos", "garbanzo",
        "porotos", "poroto", "frijoles", "frijol",
        "habas", "edamame", "soya"
    ) to LEGUME,

    // ── Pasta (cocida) ────────────────────────────────────────────────────────
    listOf(
        "pasta", "fideos", "spaghetti", "espagueti", "tallarín", "tallarines",
        "penne", "macarrón", "macarrones", "lasaña"
    ) to PASTA_COOKED,

    // ── Avena cruda / granola / cereales secos ────────────────────────────────
    listOf("avena", "granola", "müesli", "muesli", "cereal") to GRAIN_DRY,

    // ── Arroz y granos cocidos ────────────────────────────────────────────────
    listOf(
        "arroz blanco", "arroz integral", "arroz",
        "quinoa", "cebada cocida", "mijo cocido", "trigo bulgur"
    ) to GRAIN_COOKED,

    // ── Pan / masas ───────────────────────────────────────────────────────────
    listOf(
        "marraqueta", "hallulla", "baguette", "pan de molde",
        "pan integral", "pan blanco", "pan", "tortilla", "arepa", "pita",
        "empanada", "panqueque", "choripan"
    ) to BREAD,

    // ── Vegetales con almidón ─────────────────────────────────────────────────
    listOf(
        "papa", "papas", "batata", "camote", "yuca", "ñame", "taro"
    ) to STARCHY_VEG,

    // ── Queso ─────────────────────────────────────────────────────────────────
    listOf(
        "queso cheddar", "queso gauda", "queso mantecoso", "queso fresco",
        "queso crema", "queso", "mozzarella", "parmesano"
    ) to CHEESE,

    // ── Yogurt / cottage (antes que "leche") ──────────────────────────────────
    listOf(
        "yogurt griego", "yogur griego", "yogurt natural", "yogur natural",
        "yogurt", "yogur", "queso cottage"
    ) to DAIRY_THICK,

    // ── Leche ─────────────────────────────────────────────────────────────────
    listOf("leche entera", "leche descremada", "leche semi", "leche") to DAIRY_LIQUID,

    // ── Aceites y grasas puras ────────────────────────────────────────────────
    listOf(
        "aceite de oliva", "aceite de coco", "aceite vegetal", "aceite",
        "mantequilla", "manteca", "ghee", "margarina"
    ) to OIL_FAT,

    // ── Nueces y semillas ─────────────────────────────────────────────────────
    listOf(
        "almendra", "almendras", "nuez", "nueces", "maní", "mani",
        "pecan", "pistache", "semillas de chia", "semillas de linaza",
        "semillas de girasol", "semillas de zapallo", "semilla", "chía", "chia"
    ) to NUTS_SEEDS,

    // ── Frutas ────────────────────────────────────────────────────────────────
    listOf(
        "manzana", "plátano", "platano", "banana", "naranja", "uva",
        "pera", "durazno", "mango", "papaya", "sandía", "sandia",
        "melón", "melon", "piña", "pina", "frutilla", "fresa", "frambuesa",
        "arándano", "arandano", "kiwi", "ciruela", "damasco",
        "maracuyá", "lúcuma", "fruta"
    ) to FRUIT,

    // ── Verduras ──────────────────────────────────────────────────────────────
    listOf(
        "lechuga", "espinaca", "brócoli", "brocoli", "zanahoria",
        "tomate", "cebolla", "pepino", "zapallo", "pimentón", "pimenton",
        "apio", "rúcula", "repollo", "coliflor", "poroto verde",
        "choclo", "betarraga", "champiñon", "verdura", "vegetal",
        "acelga", "alcachofa", "berenjena", "zucchini", "pepinillo"
    ) to VEGETABLE,

    // ── Azúcar / dulces ───────────────────────────────────────────────────────
    listOf(
        "azúcar", "azucar", "miel", "sirope", "jarabe",
        "mermelada", "dulce de leche", "manjar", "chocolate"
    ) to SUGAR_SWEET,

    // ── Salsas / aderezos ─────────────────────────────────────────────────────
    listOf(
        "mayonesa", "ketchup", "mostaza", "salsa de tomate",
        "salsa de soya", "salsa", "aderezo", "vinagreta"
    ) to SAUCE_DRESSING,
)

/**
 * One matcher per profile, compiled once (WP-N11): all its keywords in a single alternation, longest first, that has to match
 * whole words of the accent-free, lower-case name ([RegexEs.bounded], which reads the same on the JVM and on Android), with an
 * optional Spanish plural ("papa" -> "papas", "alfajor" -> "alfajores"). A keyword is folded like the name, so "atún" and "atun"
 * are one entry; folded keywords hold only letters, digits and single spaces, so they go into the pattern as they are.
 */
private val PROFILE_MATCHERS: List<Pair<Regex, NutritionProfile>> = KEYWORD_PROFILES.map { (words, profile) ->
    val keywords = words.map { TextKeys.normalize(it) }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
    Regex(RegexEs.bounded("(?:" + keywords.joinToString("|") + ")(?:e?s)?")) to profile
}

/** "helado" is also the adjective of an iced drink ("té helado", "café helado"): that drink is not a dessert. */
private val ICED_DRINK = Regex(RegexEs.bounded("""(?:te|cafe|mate|agua|jugo|bebida|refresco|batido)\s+helad[oa]s?"""))

/** The profile whose keywords name [foodName]: the first rule, in table order, with a keyword among the whole words of the name. */
private fun profileFor(foodName: String): NutritionProfile? {
    val key = TextKeys.normalize(foodName).replace(ICED_DRINK, " ").trim()
    if (key.isEmpty()) return null
    return PROFILE_MATCHERS.firstOrNull { (matcher, _) -> matcher.containsMatchIn(key) }?.second
}

object NutritionHeuristicEstimator {
    fun estimatePer100g(foodName: String): NutritionProfile {
        return estimateNutritionByKeyword(foodName) ?: MIXED_DISH
    }

    /**
     * Profile of a dish with no catalog row that the parser says was prepared with [method] (its cooking word is no longer in
     * [foodName]): the method acts on the profile once. Frying adds its fat in grams ([HEURISTIC_FRYING_FAT_G_PER_100G]; breading
     * also its carbs) and the kcal follow 4/4/9; the dry methods use their factor. A name that still carries a cooking word got
     * that same effect in [estimateNutritionByKeyword], so the profile comes back as it is.
     */
    fun withCookingMethod(profile: NutritionProfile, foodName: String, method: CookingMethod?): NutritionProfile {
        if (method == null || detectCookingBoost(foodName.lowercase()) != null) return profile
        val boost = when (method) {
            CookingMethod.FRITO -> frying()
            CookingMethod.EMPANIZADO_FRITO -> frying(HEURISTIC_BREADING_CARBS_G_PER_100G)
            in CONCENTRATING_METHODS -> dryMethod(method)
            else -> return profile
        }
        return boost.applyTo(profile)
    }

    /** References are injected catalog rows; the mixture is an explicit assumption, never a recipe fact. */
    fun estimateWithEvidence(foodName: String, referenceFoods: List<FoodItem> = emptyList()): NutritionEstimate {
        val salad = FoodIdentity.normalize(foodName) == "ensalada"
        val vegetables = listOf("gen066", "gen026").mapNotNull { id ->
            referenceFoods.firstOrNull { it.id == id && NutrientBasis.isVerified(it) }
        }
        if (salad && vegetables.isNotEmpty()) {
            val profiles = vegetables.map { food ->
                val factor = 100.0 / NutrientBasis.grams(food)
                NutritionProfile(food.calories * factor, food.protein * factor, food.carbs * factor, food.fats * factor)
            }
            val central = NutritionProfile(profiles.map { it.calories }.average(), profiles.map { it.protein }.average(),
                profiles.map { it.carbs }.average(), profiles.map { it.fats }.average())
            return NutritionEstimate(central, NutritionEstimateEvidence(
                assumption = "Asumí ${vegetables.joinToString(" y ") { it.name.lowercase() }}${if (vegetables.size > 1) " a partes iguales" else ""}, sin aderezo.",
                referenceFoodIds = vegetables.map { it.id },
                referenceSourceRecordIds = vegetables.map { it.sourceRecordId ?: it.id },
                minPer100g = NutritionProfile(0.0, 0.0, 0.0, 0.0),
                maxPer100g = NutritionProfile(900.0, 100.0, 100.0, 100.0),
                requiresCompositionClarification = true,
            ))
        }
        val keywordProfile = profileFor(foodName)
        val matched = keywordProfile != null
        val range = keywordProfile?.let { SOURCED_RANGES[it] }
        return NutritionEstimate(estimatePer100g(foodName), NutritionEstimateEvidence(
            assumption = if (matched) "Perfil provisional por categoría; composición sin confirmar."
                else "Sin referencia nutricional: valores provisionales de un plato genérico.",
            // The sourced range of the profile when it has one; otherwise conservative marginal composition bounds, not an
            // empirical confidence interval.
            minPer100g = range?.min ?: NutritionProfile(0.0, 0.0, 0.0, 0.0),
            maxPer100g = range?.max ?: NutritionProfile(900.0, 100.0, 100.0, 100.0),
            isUnmatchedFallback = !matched,
        ))
    }
}

fun estimateNutritionByKeyword(foodName: String): NutritionProfile? {
    val lower = foodName.trim().lowercase()

    // Detect cooking method indicators in the food name
    val cookingBoost = detectCookingBoost(lower)

    profileFor(foodName)?.let { profile -> return cookingBoost?.applyTo(profile) ?: profile }
    return if (lower.length >= 3) cookingBoost?.applyTo(MIXED_DISH) ?: MIXED_DISH else null
}

/**
 * Fat a fried dish absorbs, in grams per 100 g: the heuristic counterpart of the oil path (6 g for lean protein, which is what
 * separates "Pechuga de Pollo" from "Pechuga de Pollo (frita)").
 */
const val HEURISTIC_FRYING_FAT_G_PER_100G = 6.0

/** Carbohydrate that the breading of a breaded dish adds, in grams per 100 g. */
const val HEURISTIC_BREADING_CARBS_G_PER_100G = 15.0

/**
 * How a cooking word changes the per-100 g profile of a dish with no catalog row, exactly once (WP-N10). Frying adds fat in
 * grams ([addedFatGrams]; breading also [addedCarbsGrams]) and the kcal follow 4/4/9 from that macro change: there is no
 * independent kcal multiplier. The dry methods apply their table [factor] (see [CONCENTRATING_METHODS]).
 */
private data class CookingEstimateBoost(
    val factor: CookingFactor = CookingFactor(),
    val addedFatGrams: Double = 0.0,
    val addedCarbsGrams: Double = 0.0,
) {
    fun applyTo(profile: NutritionProfile): NutritionProfile = NutritionProfile(
        calories = profile.calories * factor.kcal + 4.0 * addedCarbsGrams + 9.0 * addedFatGrams,
        protein = profile.protein * factor.protein,
        carbs = profile.carbs * factor.carbs + addedCarbsGrams,
        fats = profile.fats * factor.fats + addedFatGrams,
    )
}

private fun frying(addedCarbsGrams: Double = 0.0) =
    CookingEstimateBoost(addedFatGrams = HEURISTIC_FRYING_FAT_G_PER_100G, addedCarbsGrams = addedCarbsGrams)

private fun dryMethod(method: CookingMethod) = CookingEstimateBoost(factor = cookingFactorFor(method))

private val REGEX_FRITO = Regex("""\bfrit[oa]s?\b|\bfritura\b|\bfre[ií]do\b""")
private val REGEX_EMPANIZADO = Regex("""\bempanizad[oa]s?\b|\bapanad[oa]s?\b|\brebozad[oa]s?\b|\btempura\b|\bcapead[oa]s?\b""")
private val REGEX_SALTEADO = Regex("""\bsaltead[oa]s?\b|\bsofrit[oa]s?\b|\bwokead[oa]s?\b""")
private val REGEX_CONFITADO = Regex("""\bconfitad[oa]s?\b""")
private val REGEX_GRATINADO = Regex("""\bgratinad[oa]s?\b|\bcon\s+queso\s+gratinado\b""")
private val REGEX_PLANCHA = Regex("""\bplancha\b|\ba\s+la\s+plancha\b""")
private val REGEX_PARRILLA = Regex("""\bparrilla\b|\basad[oa]s?\s+a\s+la\s+parrilla\b""")

private fun detectCookingBoost(foodName: String): CookingEstimateBoost? {
    val lower = foodName.lowercase()
    var boost: CookingEstimateBoost? = null

    // B6/WP-N10: one source of truth. Frying and sauteing add fat in grams; the dry methods use their table factor.
    if (REGEX_FRITO.containsMatchIn(lower)) {
        boost = frying()
    }
    if (REGEX_EMPANIZADO.containsMatchIn(lower)) {
        boost = frying(HEURISTIC_BREADING_CARBS_G_PER_100G)
    }
    if (REGEX_SALTEADO.containsMatchIn(lower)) {
        boost = frying()
    }
    if (REGEX_CONFITADO.containsMatchIn(lower)) {
        boost = frying()
    }
    if (REGEX_GRATINADO.containsMatchIn(lower)) {
        boost = dryMethod(CookingMethod.HORNO)
    }
    if (REGEX_PLANCHA.containsMatchIn(lower)) {
        boost = dryMethod(CookingMethod.PLANCHA)
    }
    if (REGEX_PARRILLA.containsMatchIn(lower)) {
        boost = dryMethod(CookingMethod.ASADO_PARRILLA)
    }

    return boost
}
