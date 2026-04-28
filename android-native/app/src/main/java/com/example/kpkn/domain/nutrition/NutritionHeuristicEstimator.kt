package com.example.kpkn.domain.nutrition

/**
 * NutritionHeuristicEstimator — Keyword-based macro estimator for foods not in the database.
 *
 * Used as fallback when:
 *   - Mode is BASIC (rules-only, no AI)
 *   - PRO mode but Qwen model not loaded / timeout
 *   - AI returned items without nutritionPer100g for some entries
 *
 * Precision: ±20–30%. Tagged as AnalysisSource.LOCAL_HEURISTIC so the UI shows "Heur. ~".
 * Values are per 100g.
 */

data class NutritionProfile(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

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

// ─── Keyword → Profile mapping ───────────────────────────────────────────────
// Rules are checked in order — more specific rules first.

private val KEYWORD_PROFILES: List<Pair<List<String>, NutritionProfile>> = listOf(

    // ── Suplementos (antes que cualquier otro match) ──────────────────────────
    listOf(
        "whey", "proteína en polvo", "proteina en polvo",
        "caseína", "caseina", "suplemento proteico", "mass gainer", "gainers"
    ) to PROTEIN_POWDER,

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
        "asado", "costilla", "lomo vetado", "punta de ganso", "tapapecho",
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
        "pan integral", "pan blanco", "pan", "tortilla", "arepa", "pita"
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
        "melón", "melon", "piña", "pina", "frutilla", "frambuesa",
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

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Estimates macros per 100g from the food name using keyword matching.
 * Returns null if no keyword matched (unknown food category).
 * Confidence: ~0.45 (rough estimate, ±20–30%).
 *
 * Also detects cooking method keywords and boosts fats/kcal accordingly.
 */
fun estimateNutritionByKeyword(foodName: String): NutritionProfile? {
    val lower = foodName.trim().lowercase()

    // Detect cooking method indicators in the food name
    val cookingBoost = detectCookingBoost(lower)

    for ((keywords, profile) in KEYWORD_PROFILES) {
        if (keywords.any { lower.contains(it) }) {
            if (cookingBoost != null) {
                return NutritionProfile(
                    calories = profile.calories * cookingBoost.kcal,
                    protein = profile.protein * cookingBoost.protein,
                    carbs = profile.carbs * cookingBoost.carbs + cookingBoost.additiveCarbs,
                    fats = profile.fats * cookingBoost.fats,
                )
            }
            return profile
        }
    }
    return if (lower.length >= 3) {
        if (cookingBoost != null) {
            NutritionProfile(
                calories = MIXED_DISH.calories * cookingBoost.kcal,
                protein = MIXED_DISH.protein * cookingBoost.protein,
                carbs = MIXED_DISH.carbs * cookingBoost.carbs + cookingBoost.additiveCarbs,
                fats = MIXED_DISH.fats * cookingBoost.fats,
            )
        } else MIXED_DISH
    } else null
}

private data class CookingEstimateBoost(
    val kcal: Double = 1.0,
    val protein: Double = 1.0,
    val carbs: Double = 1.0,
    val fats: Double = 1.0,
    val additiveCarbs: Double = 0.0,
)

private fun detectCookingBoost(foodName: String): CookingEstimateBoost? {
    val lower = foodName.lowercase()
    var boost: CookingEstimateBoost? = null

    if (Regex("""\bfrit[oa]s?\b|\bfritura\b|\bfre[ií]do\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 2.0, fats = 3.0)
    }
    if (Regex("""\bempanizad[oa]s?\b|\bapanad[oa]s?\b|\brebozad[oa]s?\b|\btempura\b|\bcapead[oa]s?\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 3.0, fats = 3.0, additiveCarbs = 15.0)
    }
    if (Regex("""\bsaltead[oa]s?\b|\bsofrit[oa]s?\b|\bwokead[oa]s?\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 1.5, fats = 1.8)
    }
    if (Regex("""\bconfitad[oa]s?\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 2.5, fats = 3.5)
    }
    if (Regex("""\bgratinad[oa]s?\b|\bcon\s+queso\s+gratinado\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 1.8, fats = 2.0, protein = 1.15)
    }
    if (Regex("""\bplancha\b|\ba\s+la\s+plancha\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 1.0, fats = 0.9, protein = 1.05)
    }
    if (Regex("""\bparrilla\b|\basad[oa]s?\s+a\s+la\s+parrilla\b""").containsMatchIn(lower)) {
        boost = CookingEstimateBoost(kcal = 1.05, fats = 0.9, protein = 1.10)
    }

    return boost
}
