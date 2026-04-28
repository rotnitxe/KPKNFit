package com.example.kpkn.data.food

import java.text.Normalizer

/**
 * FoodDescriptionParser — Heuristic parser for OFF Chile product names.
 *
 * Mimics local-AI quality for description detection and macro validation
 * without requiring on-device ML. Uses the existing FoodDatabase as a
 * canonical reference, applies cleaning rules, category detection, and
 * macro sanity checks.
 *
 * Pipeline:
 * 1. Clean raw product name (strip brand prefixes, noise tokens, etc.)
 * 2. Extract brand hints
 * 3. Match against FoodDatabase (aliases + fuzzy)
 * 4. Validate/override macros using category heuristics
 * 5. Return enriched description with confidence score
 */
object FoodDescriptionParser {

    // ─── Noise tokens to strip from product names ─────────────────────────────

    private val NOISE_TOKENS = setOf(
        "original", "clasico", "clasico", "nuevo", "nueva", "oferta",
        "pack", "x2", "x3", "x4", "x6", "x12", "2x1",
        "sin", "con", "de", "del", "la", "el", "los", "las",
        "y", "o", "para", "por", "en", "al",
    )

    private val BRAND_PREFIXES = setOf(
        "herbalife", "nestle", "soprole", "colun", "loncoleche",
        "quaker", "carozzi", "unc", "santa isabel", "lider",
        "jumbo", "easy", "corona", "dos en uno", "mr",
        "kraft", "hellmann", "coca-cola", "pepsi", "bimbo",
        "pullman", "barros jarpa", "felfort", "arcoiris",
        "turk", "gardenias", "mamut", "kris", "poco a poco",
        "optimum nutrition", "muscle tech", "bsn", "dymatize",
        "kellogg", "general mills", "danone", "colun",
    )

    // ─── Category keywords for macro validation ───────────────────────────────

    private val CATEGORY_RULES = listOf(
        CategoryRule(
            keywords = setOf("proteina", "whey", "protein", "isolate", "concentrate", "caseina"),
            minKcal = 300.0, maxKcal = 420.0,
            minProtein = 60.0, maxProtein = 90.0,
            maxFat = 15.0, maxCarbs = 20.0,
            category = "suplemento_proteina",
        ),
        CategoryRule(
            keywords = setOf("creatina", "creatine", "bcaa", "amino", "glutamina"),
            minKcal = 0.0, maxKcal = 50.0,
            minProtein = 0.0, maxProtein = 100.0,
            maxFat = 5.0, maxCarbs = 10.0,
            category = "suplemento_puro",
        ),
        CategoryRule(
            keywords = setOf("yogurt", "yogur", "yoghurt"),
            minKcal = 40.0, maxKcal = 150.0,
            minProtein = 2.0, maxProtein = 12.0,
            maxFat = 10.0, maxCarbs = 20.0,
            category = "lacteo",
        ),
        CategoryRule(
            keywords = setOf("leche", "milk"),
            minKcal = 30.0, maxKcal = 120.0,
            minProtein = 2.0, maxProtein = 6.0,
            maxFat = 8.0, maxCarbs = 8.0,
            category = "lacteo",
        ),
        CategoryRule(
            keywords = setOf("queso", "cheese"),
            minKcal = 150.0, maxKcal = 450.0,
            minProtein = 15.0, maxProtein = 35.0,
            maxFat = 40.0, maxCarbs = 10.0,
            category = "lacteo",
        ),
        CategoryRule(
            keywords = setOf("pan", "bread", "marraqueta", "hallulla", "molde", "integral"),
            minKcal = 200.0, maxKcal = 350.0,
            minProtein = 6.0, maxProtein = 15.0,
            maxFat = 8.0, maxCarbs = 60.0,
            category = "panaderia",
        ),
        CategoryRule(
            keywords = setOf("arroz", "rice", "pasta", "fideos", "tallarines"),
            minKcal = 100.0, maxKcal = 400.0,
            minProtein = 2.0, maxProtein = 15.0,
            maxFat = 5.0, maxCarbs = 85.0,
            category = "cereal",
        ),
        CategoryRule(
            keywords = setOf("pollo", "chicken", "pechuga", "pavo", "turkey"),
            minKcal = 100.0, maxKcal = 250.0,
            minProtein = 20.0, maxProtein = 35.0,
            maxFat = 15.0, maxCarbs = 5.0,
            category = "carne_blanca",
        ),
        CategoryRule(
            keywords = setOf("vacuno", "beef", "carne", "lomo", "filete", "cerdo", "pork"),
            minKcal = 150.0, maxKcal = 350.0,
            minProtein = 20.0, maxProtein = 35.0,
            maxFat = 30.0, maxCarbs = 5.0,
            category = "carne_roja",
        ),
        CategoryRule(
            keywords = setOf("pescado", "fish", "salmon", "merluza", "atun", "tuna", "camaron"),
            minKcal = 80.0, maxKcal = 250.0,
            minProtein = 15.0, maxProtein = 30.0,
            maxFat = 20.0, maxCarbs = 5.0,
            category = "pescado",
        ),
        CategoryRule(
            keywords = setOf("aceite", "oil", "mantequilla", "butter", "margarina"),
            minKcal = 700.0, maxKcal = 900.0,
            minProtein = 0.0, maxProtein = 2.0,
            maxFat = 100.0, maxCarbs = 5.0,
            category = "grasa",
        ),
        CategoryRule(
            keywords = setOf("azucar", "sugar", "miel", "honey", "jarabe"),
            minKcal = 250.0, maxKcal = 400.0,
            minProtein = 0.0, maxProtein = 2.0,
            maxFat = 2.0, maxCarbs = 100.0,
            category = "endulzante",
        ),
        CategoryRule(
            keywords = setOf("bebida", "soda", "jugo", "juice", "agua", "water", "te", "cafe"),
            minKcal = 0.0, maxKcal = 150.0,
            minProtein = 0.0, maxProtein = 2.0,
            maxFat = 2.0, maxCarbs = 15.0,
            category = "bebida",
        ),
        CategoryRule(
            keywords = setOf("fruta", "apple", "manzana", "platano", "banana", "naranja", "uva"),
            minKcal = 30.0, maxKcal = 100.0,
            minProtein = 0.0, maxProtein = 2.0,
            maxFat = 2.0, maxCarbs = 25.0,
            category = "fruta",
        ),
        CategoryRule(
            keywords = setOf("barra", "bar", "snack", "galleta", "cookie"),
            minKcal = 300.0, maxKcal = 550.0,
            minProtein = 5.0, maxProtein = 30.0,
            maxFat = 30.0, maxCarbs = 70.0,
            category = "snack",
        ),
        CategoryRule(
            keywords = setOf("empanada", "pastel", "tarta", "pie"),
            minKcal = 200.0, maxKcal = 500.0,
            minProtein = 8.0, maxProtein = 25.0,
            maxFat = 30.0, maxCarbs = 50.0,
            category = "preparacion",
        ),
        CategoryRule(
            keywords = setOf("salsa", "sauce", "ketchup", "mostaza", "mayonesa", "pebre"),
            minKcal = 20.0, maxKcal = 700.0,
            minProtein = 0.0, maxProtein = 5.0,
            maxFat = 80.0, maxCarbs = 30.0,
            category = "condimento",
        ),
        CategoryRule(
            keywords = setOf("avena", "oat", "granola", "cereal", "muesli"),
            minKcal = 300.0, maxKcal = 500.0,
            minProtein = 8.0, maxProtein = 20.0,
            maxFat = 25.0, maxCarbs = 70.0,
            category = "cereal",
        ),
        CategoryRule(
            keywords = setOf("legumbre", "lenteja", "poroto", "garbanzo", "arveja", "frijol"),
            minKcal = 80.0, maxKcal = 160.0,
            minProtein = 5.0, maxProtein = 12.0,
            maxFat = 3.0, maxCarbs = 30.0,
            category = "legumbre",
        ),
        CategoryRule(
            keywords = setOf("verdura", "vegetable", "lechuga", "tomate", "cebolla", "zanahoria", "brocoli"),
            minKcal = 10.0, maxKcal = 60.0,
            minProtein = 0.0, maxProtein = 5.0,
            maxFat = 2.0, maxCarbs = 15.0,
            category = "verdura",
        ),
        CategoryRule(
            keywords = setOf("nuez", "almendra", "cashew", "mani", "semilla", "chia", "linaza"),
            minKcal = 400.0, maxKcal = 700.0,
            minProtein = 10.0, maxProtein = 30.0,
            maxFat = 70.0, maxCarbs = 30.0,
            category = "fruto_seco",
        ),
        CategoryRule(
            keywords = setOf("helado", "ice cream", "postre", "dessert", "dulce"),
            minKcal = 150.0, maxKcal = 350.0,
            minProtein = 2.0, maxProtein = 8.0,
            maxFat = 20.0, maxCarbs = 40.0,
            category = "postre",
        ),
        CategoryRule(
            keywords = setOf("completo", "hotdog", "vienesa", "salchicha", "longaniza"),
            minKcal = 200.0, maxKcal = 400.0,
            minProtein = 8.0, maxProtein = 18.0,
            maxFat = 30.0, maxCarbs = 35.0,
            category = "embutido",
        ),
        CategoryRule(
            keywords = setOf("papa", "patata", "frita", "chips"),
            minKcal = 150.0, maxKcal = 550.0,
            minProtein = 1.0, maxProtein = 8.0,
            maxFat = 35.0, maxCarbs = 55.0,
            category = "tuberculo",
        ),
    )

    // ─── Public API ───────────────────────────────────────────────────────────

    data class ParsedDescription(
        val rawName: String,
        val cleanedName: String,
        val brandHint: String?,
        val matchedFoodName: String?,
        val category: String?,
        val calories: Double,
        val protein: Double,
        val fats: Double,
        val carbs: Double,
        val fiber: Double,
        val sugars: Double,
        val sodiumMg: Double,
        val confidence: Float,
        val macroOverrideApplied: Boolean,
    )

    /**
     * Parse an OFF Chile product line into a structured description.
     *
     * @param rawName The raw product_name field (column 10)
     * @param rawBrand The raw brands field (column 18)
     * @param rawCalories energy-kcal_100g (column 88)
     * @param rawProtein proteins_100g (column 150)
     * @param rawFat fat_100g (column 92)
     * @param rawCarbs carbohydrates_100g (column 129)
     * @param rawFiber fiber_100g (column 131)
     * @param rawSugars sugars_100g (column 130)
     * @param rawSodium sodium_100g (column 128)
     */
    fun parse(
        rawName: String,
        rawBrand: String? = null,
        rawCalories: Double = 0.0,
        rawProtein: Double = 0.0,
        rawFat: Double = 0.0,
        rawCarbs: Double = 0.0,
        rawFiber: Double = 0.0,
        rawSugars: Double = 0.0,
        rawSodium: Double = 0.0,
    ): ParsedDescription {
        // Step 1: Clean the name
        val cleaned = cleanProductName(rawName)

        // Step 2: Extract brand
        val brand = extractBrand(rawName, rawBrand)

        // Step 3: Match against FoodDatabase
        val matched = matchFoodDatabase(cleaned, rawName)

        // Step 4: Detect category
        val category = detectCategory(cleaned, rawName)

        // Step 5: Validate and possibly override macros
        val (calories, protein, fats, carbs, fiber, sugars, sodiumMg, overrideApplied) =
            validateMacros(
                rawCalories, rawProtein, rawFat, rawCarbs,
                rawFiber, rawSugars, rawSodium,
                category, matched,
            )

        // Step 6: Compute confidence
        val confidence = computeConfidence(
            cleaned, matched, category,
            rawCalories, overrideApplied,
        )

        return ParsedDescription(
            rawName = rawName,
            cleanedName = cleaned,
            brandHint = brand,
            matchedFoodName = matched?.name,
            category = category,
            calories = calories,
            protein = protein,
            fats = fats,
            carbs = carbs,
            fiber = fiber,
            sugars = sugars,
            sodiumMg = sodiumMg,
            confidence = confidence,
            macroOverrideApplied = overrideApplied,
        )
    }

    // ─── Step 1: Clean product name ───────────────────────────────────────────

    private fun cleanProductName(raw: String): String {
        var name = raw.trim()

        // Remove common suffixes with measurements
        name = name.replace(Regex("""\s*\d+[\s]*(g|kg|ml|l|oz|lb)\b""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""\s*\d+[\s]*x[\s]*\d+"""), "")

        // Remove brand prefixes
        for (brand in BRAND_PREFIXES) {
            if (name.startsWith(brand, ignoreCase = true)) {
                name = name.substring(brand.length)
                    .trimStart('-', ' ', ':', '|', '/', '\\')
                    .trim()
                break
            }
        }

        // Remove noise tokens at start/end
        val words = name.split(Regex("""\s+""")).toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in NOISE_TOKENS) {
            words.removeAt(0)
        }
        while (words.isNotEmpty() && words.last().lowercase() in NOISE_TOKENS) {
            words.removeAt(words.lastIndex)
        }
        name = words.joinToString(" ")

        // Normalize whitespace
        name = name.replace(Regex("""\s+"""), " ").trim()

        // Remove excessive punctuation
        name = name.replace(Regex("""[^\p{L}\p{Nd}\s\-]"""), " ")
            .replace(Regex("""\s+"""), " ").trim()

        return name.ifBlank { raw.trim() }
    }

    // ─── Step 2: Extract brand ────────────────────────────────────────────────

    private fun extractBrand(rawName: String, rawBrand: String?): String? {
        // Prefer explicit brand field
        if (!rawBrand.isNullOrBlank()) {
            return rawBrand.trim()
        }

        // Try to detect brand from name
        for (brand in BRAND_PREFIXES) {
            if (rawName.contains(brand, ignoreCase = true)) {
                return brand.replaceFirstChar { it.uppercase() }
            }
        }

        return null
    }

    // ─── Step 3: Match against FoodDatabase ───────────────────────────────────

    private fun matchFoodDatabase(cleaned: String, rawName: String): com.example.kpkn.data.models.FoodItem? {
        // Try exact match on cleaned name
        findFoodByNormalized(cleaned)?.let { return it }

        // Try match on raw name
        findFoodByNormalized(rawName)?.let { return it }

        // Try word-by-word matching (longest match wins)
        val allWords = cleaned.lowercase().split(Regex("""\s+"""))
        var bestMatch: com.example.kpkn.data.models.FoodItem? = null
        var bestScore = 0

        for (windowSize in listOf(3, 2, 1)) {
            for (i in 0..maxOf(0, allWords.size - windowSize)) {
                val phrase = allWords.subList(i, minOf(i + windowSize, allWords.size)).joinToString(" ")
                if (phrase.length < 3) continue
                val match = findFoodByNormalized(phrase)
                if (match != null) {
                    val score = phrase.length
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = match
                    }
                }
            }
            if (bestMatch != null) break
        }

        return bestMatch
    }

    // ─── Step 4: Detect category ──────────────────────────────────────────────

    private fun detectCategory(cleaned: String, rawName: String): String? {
        val text = "$cleaned $rawName".lowercase()

        for (rule in CATEGORY_RULES) {
            for (keyword in rule.keywords) {
                if (text.contains(keyword)) {
                    return rule.category
                }
            }
        }

        return null
    }

    // ─── Step 5: Validate macros ──────────────────────────────────────────────

    private fun validateMacros(
        kcal: Double, protein: Double, fat: Double, carbs: Double,
        fiber: Double, sugars: Double, sodium: Double,
        category: String?, matched: com.example.kpkn.data.models.FoodItem?,
    ): Quad8 {
        val rule = category?.let { cat -> CATEGORY_RULES.find { it.category == cat } }

        var outKcal = kcal
        var outProtein = protein
        var outFat = fat
        var outCarbs = carbs
        var outFiber = fiber
        var outSugars = sugars
        var outSodium = sodium
        var overrideApplied = false

        // If no calories at all, try to fill from matched food
        if (kcal <= 0.0 && matched != null) {
            outKcal = matched.calories
            outProtein = matched.protein
            outFat = matched.fats
            outCarbs = matched.carbs
            overrideApplied = true
        }

        // Sanity check: if rule exists and values are wildly out of range, flag
        if (rule != null && kcal > 0.0) {
            val proteinD = protein.toDouble()
            val fatD = fat.toDouble()
            val carbsD = carbs.toDouble()
            val kcalD = kcal.toDouble()

            // Check if macros are physically possible (protein+fat+carbs should roughly account for kcal)
            val computedKcal = proteinD * 4.0 + fatD * 9.0 + carbsD * 4.0
            val kcalDeviation = if (kcalD > 0.0) kotlin.math.abs(computedKcal - kcalD) / kcalD else 1.0

            // If computed kcal deviates > 50% from stated kcal, data is suspect
            if (kcalDeviation > 0.5 && matched != null) {
                outKcal = matched.calories
                outProtein = matched.protein
                outFat = matched.fats
                outCarbs = matched.carbs
                overrideApplied = true
            }

            // Check for impossible values
            if (proteinD > 100.0 || fatD > 100.0 || carbsD > 100.0) {
                if (matched != null) {
                    outProtein = matched.protein
                    outFat = matched.fats
                    outCarbs = matched.carbs
                    overrideApplied = true
                } else {
                    outProtein = proteinD.coerceAtMost(60.0)
                    outFat = fatD.coerceAtMost(80.0)
                    outCarbs = carbsD.coerceAtMost(100.0)
                    overrideApplied = true
                }
            }

            // Check category-specific bounds
            if (kcalD < rule.minKcal || kcalD > rule.maxKcal * 3.0) {
                if (matched != null) {
                    outKcal = matched.calories
                    overrideApplied = true
                }
            }
        }

        // Sodium: OFF stores in grams, we need mg
        if (outSodium < 100.0 && outSodium > 0.0) {
            outSodium = outSodium * 1000.0
        }

        return Quad8(outKcal, outProtein, outFat, outCarbs, outFiber, outSugars, outSodium, overrideApplied)
    }

    private data class Quad8(
        val v1: Double, val v2: Double, val v3: Double, val v4: Double,
        val v5: Double, val v6: Double, val v7: Double, val v8: Boolean,
    )

    // ─── Step 6: Compute confidence ───────────────────────────────────────────

    private fun computeConfidence(
        cleaned: String,
        matched: com.example.kpkn.data.models.FoodItem?,
        category: String?,
        kcal: Double,
        overrideApplied: Boolean,
    ): Float {
        var score = 0.5f

        // Has a meaningful name
        if (cleaned.length >= 3) score += 0.1f
        if (cleaned.length >= 8) score += 0.05f

        // Matched against database
        if (matched != null) score += 0.2f

        // Category detected
        if (category != null) score += 0.1f

        // Has valid calories
        if (kcal > 0.0) score += 0.1f

        // Macros not overridden (original data was good)
        if (!overrideApplied) score += 0.05f

        return score.coerceIn(0.0f, 0.95f)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

/**
 * Category rule for macro validation.
 */
private data class CategoryRule(
    val keywords: Set<String>,
    val minKcal: Double,
    val maxKcal: Double,
    val minProtein: Double,
    val maxProtein: Double,
    val maxFat: Double,
    val maxCarbs: Double,
    val category: String,
)
