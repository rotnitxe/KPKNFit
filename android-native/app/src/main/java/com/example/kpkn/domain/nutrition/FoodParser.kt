package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.getGramsForReference
import com.example.kpkn.data.food.staticFoodPhrases
import com.example.kpkn.data.models.*

/**
 * FoodParser — Deterministic Spanish NLP parser for free-form food descriptions.
 * Mirrors utils/nutritionDescriptionParser.ts from PWA.
 * Handles: grams, portions, cooking methods, brands, modifiers.
 */

// ─── Regex Patterns ──────────────────────────────────────────────────────────

private const val GRAM_UNITS = "g|gr|gramos?|kg|kilos?|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?"

private val GRAM_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*(?:$GRAM_UNITS)\b(?:\s+de)?\s*""", RegexOption.IGNORE_CASE)

private val COMMA_OR_PLUS = Regex("""(?:,\s*|;\s*|\s+\+\s+|\s*[\r\n]+\s*)""")
private val CONNECTOR_Y = Regex("""\s+(?:y|e|mas|más)\s+""", RegexOption.IGNORE_CASE)
private val CONNECTOR_CON = Regex("""\s+con\s+""", RegexOption.IGNORE_CASE)

private val PROTECTED_ENTITIES = listOf(
    "arroz con leche", "arroz con pollo", "pollo con papas", "pan con queso",
    "pan con palta y jamon", "pan con palta y jamón", "pan con palta",
    "pan con mantequilla", "papas con mayo", "pastel de choclo", "pasteles de choclo",
    "empanada de pino", "empanadas de pino", "empanada de queso", "empanadas de queso",
    "sandwich de pollo con mayonesa", "sandwich de jamon con mayonesa",
    "sándwich de pollo con mayonesa", "sándwich de jamón con mayonesa",
    "hamburguesa con queso", "hamburguesas con queso", "papas fritas con mayonesa", "papa fritas con mayonesa",
    "cafe con leche", "café con leche", "te con leche", "té con leche",
    "leche con chocolate", "leche con platano", "leche con plátano", "porotos con riendas",
)

private val LITERAL_QUANTITIES = mapOf(
    "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0,
    "cuatro" to 4.0, "cinco" to 5.0, "seis" to 6.0, "siete" to 7.0,
    "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0,
    "once" to 11.0, "doce" to 12.0, "trece" to 13.0, "catorce" to 14.0,
    "quince" to 15.0, "dieciséis" to 16.0, "dieciseis" to 16.0,
    "diecisiete" to 17.0, "dieciocho" to 18.0, "diecinueve" to 19.0,
    "veinte" to 20.0, "veintiuno" to 21.0, "veintidós" to 22.0, "veintidos" to 22.0,
    "veintitrés" to 23.0, "veintitres" to 23.0, "veinticuatro" to 24.0,
    "veinticinco" to 25.0, "treinta" to 30.0,
    "media" to 0.5, "medio" to 0.5, "mitad" to 0.5,
    "cuarto" to 0.25, "tercio" to 0.33, "doble" to 2.0, "triple" to 3.0,
)

private val PORTION_PATTERNS = listOf(
    Triple(Regex("""\b(grande|generoso|generosa)\b""", RegexOption.IGNORE_CASE), PortionPreset.EXTRA, "extra"),
    Triple(Regex("""\bplato\s+grande\b""", RegexOption.IGNORE_CASE), PortionPreset.LARGE, "large"),
    Triple(Regex("""\bplato\s+mediano\b""", RegexOption.IGNORE_CASE), PortionPreset.MEDIUM, "medium"),
    Triple(Regex("""\bplato\s+(?:chico|pequeño|pequeña)\b""", RegexOption.IGNORE_CASE), PortionPreset.SMALL, "small"),
    Triple(Regex("""\b(mediano|mediana)\b""", RegexOption.IGNORE_CASE), PortionPreset.MEDIUM, "medium"),
    Triple(Regex("""\b(pequeño|pequeña|chico|chica)\b""", RegexOption.IGNORE_CASE), PortionPreset.SMALL, "small"),
)

private val COOKING_PATTERNS = listOf(
    Pair(Regex("""\b(?:empanizad[oa]s?|empanad[o]s?|apanad[oa]s?|breaded)\b""", RegexOption.IGNORE_CASE), CookingMethod.EMPANIZADO_FRITO),
    
    // 2. PLANCHA / PLANCHADO
    Pair(Regex("""\b(?:a\s+la\s+)?(?:plancha|planchad[oa]s?)\b""", RegexOption.IGNORE_CASE), CookingMethod.PLANCHA),
    
    // 3. HORNO
    Pair(Regex("""\b(?:al\s+)?horno\b|\bhorn(?:ead[oa]s?|er[oa]?)\b|\b(?:baked|airfryer|air\s*fryer|frito\s+al\s+aire)\b""", RegexOption.IGNORE_CASE), CookingMethod.HORNO),
    
    // 4. FRITO + SALTEADO + REVUELTO (unificados como PWA)
    Pair(Regex("""\b(?:frit[oa]s?|fre[ií]d[oa]s?|revuelt[oa]s?|saltead[oa]s?|saltear|sofrit[oa]s?|soffrit[oa]s?|fried)\b""", RegexOption.IGNORE_CASE), CookingMethod.FRITO),
    
    // 5. COCIDO / HERVIDO / SANCOCHADO
    Pair(Regex("""\b(?:cocid[oa]s?|hervid[oa]s?|sancochad[oa]s?|boiled|estofad[oa]s?)\b""", RegexOption.IGNORE_CASE), CookingMethod.COCIDO),
    
    // 6. CRUDO
    Pair(Regex("""\b(?:crud[oa]s?|fresc[oa]s?|raw)\b""", RegexOption.IGNORE_CASE), CookingMethod.CRUDO),
    
    // 7. VAPOR
    Pair(Regex("""\b(?:al\s+)?vapor\b|\bvaporizad[oa]s?\b|\bsteamed\b""", RegexOption.IGNORE_CASE), CookingMethod.VAPOR),
    
    // 8. OLLA
    Pair(Regex("""\b(?:a\s+la\s+)?olla\b""", RegexOption.IGNORE_CASE), CookingMethod.OLLA),
    
    // 9. ASADO_PARRILLA (ahora separado de plancha)
    Pair(Regex("""\b(?:a\s+la\s+)?parrilla\b|\bparrill[ae]r[oa]s?\b|\b(?:grilled|asad[oa]s?|al\s+carb[oó]n)\b""", RegexOption.IGNORE_CASE), CookingMethod.ASADO_PARRILLA),
    
    // 10. GUISADO
    Pair(Regex("""\bguisad[oa]s?\b|\bcazuel[ae]d[oa]s?\b""", RegexOption.IGNORE_CASE), CookingMethod.GUISADO),
    
    // 11. AHUMADO
    Pair(Regex("""\bahumad[oa]s?\b|\bhumad[oa]s?\b|\bsmoked\b""", RegexOption.IGNORE_CASE), CookingMethod.AHUMADO),
)

private val REFERENCE_PATTERNS = listOf(
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(cucharadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(un|una|media|1)\s+(cucharada)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(dos|tres)\s+(cucharadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(cucharaditas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "teaspoon"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(tazas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(taza)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(media|medio|1/2)\s+(taza)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(puñado)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "handful"),
    Pair(Regex("""\b(un|1)\s+(puño)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "fist"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(vasos?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "glass"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(rebanadas?|tajadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "slice"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(latas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "can"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(scoops?|medidas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "scoop"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(porci[oó]n(?:es)?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "portion"),
    Pair(Regex("""\b(un|una|1)\s+(trozo)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "piece"),
    Pair(Regex("""\b(un|una|1)\s+(pedazo)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "piece"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(trozos?|pedazos?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "piece"),
    // Indicadores subjetivos de cantidad (el normalizador conserva el literal "un/una"
    // ante estos sustantivos, y se acepta dígito como red de seguridad)
    Pair(Regex("""\b(un|1)\s+(poco)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "little"),
    Pair(Regex("""\b(poquito|poquita)\s+(?:de\s+)?(.+)""", RegexOption.IGNORE_CASE), "little"),
    Pair(Regex("""\b(una?|1)\s+(pizca)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "pinch"),
    Pair(Regex("""\b(un|1)\s+(chorrito)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "splash"),
)

// Precompiled Regex patterns for optimization
private val GROUP_PATTERN = Regex("^(.+?)\\s*\\((.+)\\)\\s*$")
private val STARTS_WITH_DIGIT = Regex("""^\d""")
private val NEGATION_PATTERN = Regex("""\b(?:sin|menos|no)\b""", RegexOption.IGNORE_CASE)
private val GRAM_UNIT_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*($GRAM_UNITS)\b""", RegexOption.IGNORE_CASE)
private val GRAM_POSITION_LOOKAHEAD = Regex("""(?=(?<!\d)(?<![.,])\d+(?:[.,]\d+)?\s*(?:$GRAM_UNITS)\b)""", RegexOption.IGNORE_CASE)
private val KG_LITER_PATTERN = Regex("kg|kilos?|l$|litros?")
private val OZ_PATTERN = Regex("oz|onzas?")
private val LB_PATTERN = Regex("lb|libras?")
private val MULTISPACE_PATTERN = Regex("\\s{2,}")
private val HALF_PATTERN = Regex("""\b1/2\b""")
private val QUARTER_PATTERN = Regex("""\b1/4\b""")
private val THREE_QUARTERS_PATTERN = Regex("""\b3/4\b""")
private val RANGE_QUANTITY_PATTERN = Regex("""^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s+(.+)$""")
private val NUMBER_QUANTITY_PATTERN = Regex("""^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$""")
private val LITERAL_QUANTITY_PATTERN = Regex("""^(un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciséis|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidós|veintidos|veintitrés|veintitres|veinticuatro|veinticinco|treinta|media|medio|mitad|cuarto|tercio|doble|triple)\s+(.+)$""", RegexOption.IGNORE_CASE)
private val PAIR_PATTERN = Regex("""^(?:un|1)\s+par\s+(?:de\s+)?(.+)$""", RegexOption.IGNORE_CASE)
private val SUFFIX_X_PATTERN = Regex("""^(\d+(?:[.,]\d+)?)?\s*(.+?)\s*[x×]\s*(\d+(?:[.,]\d+)?)\s*$""", RegexOption.IGNORE_CASE)
private val SPACES_PATTERN = Regex("\\s+")
private val LEADING_DE_PATTERN = Regex("^de\\s+")
private val LEADING_ARTICLE_PATTERN = Regex("^(?:el|la|los|las)\\s+")
private val PORTION_PREFIX_PATTERN = Regex("^(?:platos?|porciones?|porción|tazas?|vasos?|boles?|bowls?|fuentes?)\\s+de\\s+")
private val ARTICLE_PORTION_PREFIX_PATTERN = Regex("^(?:un|una|unos|unas)\\s+(?:platos?|porciones?|porción|tazas?|vasos?|boles?|bowls?|fuentes?)\\s+de\\s+")
private val TRAILING_DE_PATTERN = Regex("\\s+de\\s+$")

private val PROTECTED_ENTITY_PHRASES = (PROTECTED_ENTITIES + staticFoodPhrases() + listOf("salsa de tomate"))
    .distinct()
    .sortedByDescending { it.length }

private val PROTECTED_ENTITIES_REGEX = Regex(
    PROTECTED_ENTITY_PHRASES.joinToString("|") { "\\b${Regex.escape(it)}\\b" },
    RegexOption.IGNORE_CASE,
)

private val REFERENCE_KEYWORDS_FAST = listOf(
    "cucharad", "taza", "puñ", "vaso", "rebanad", "tajad", "lata",
    "scoop", "medida", "porcion", "porción", "trozo", "pedazo",
    "poco", "poquit", "pizca", "chorrit"
)

private val COOKING_KEYWORDS_FAST = listOf(
    "empaniz", "apanad", "breaded", "empanad", "plancha", "horno", "horn",
    "baked", "airfryer", "air fryer", "frito", "freid", "freíd", "revuelt",
    "saltea", "sofrit", "soffrit", "fried", "cocid", "hervid", "sancoch",
    "boiled", "estofad", "crud", "fresc", "raw", "vapor", "steamed", "olla",
    "parrill", "grilled", "asado", "carbón", "carbon", "guisad", "cazuel",
    "ahumad", "humad", "smoked"
)

private val PORTION_KEYWORDS_FAST = listOf(
    "grand", "generos", "plato", "median", "pequeñ", "chico", "chica"
)

private val MODIFIER_KEYWORDS_FAST = listOf(
    "piel", "grasa", "miga", "clara", "descremad", "light", "0%", "almibar",
    "almíbar", "azucar", "azúcar", "integral", "colmad", "generos", "rasa", "fina", "pequeñ"
)

// ─── Main Parser ─────────────────────────────────────────────────────────────

fun parseMealDescription(
    description: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
): ParsedMealDescription {
    // Phase G: Normalize user input before parsing
    val normalized = TextNormalizer.normalize(description)
    val trimmed = normalized.trim()
    if (trimmed.isEmpty()) return ParsedMealDescription(rawDescription = description)

    val fragments = splitByListConnectors(trimmed)
    val items = mutableListOf<ParsedMealItem>()
    val seen = mutableSetOf<String>()
    val globalPortion = extractGlobalPortion(trimmed)

    for (frag in fragments) {
        // D1: retrieval POR FRAGMENTO con confianza por ítem. El retrieval de la
        // descripción completa diluye la confianza entre varios alimentos y bloquea
        // priors buenos por el gate global; cada fragmento recibe el suyo.
        // Si el snapshot no está instalado (tests), se cae al retrieval provisto.
        val fragRetrieval = if (retrievalResult == null) {
            null
        } else {
            SemanticPortionRetriever.retrieve(frag)
                .takeIf { it.confidence > 0.0 || it.portionPriors.isNotEmpty() }
                ?: retrievalResult
        }
        val parsed = parseFragment(frag, fragRetrieval) ?: continue
        val key = canonicalTagKey(parsed.tag)
        if (key !in seen) {
            seen.add(key)
            items.add(parsed.copy(portion = if (parsed.portion == PortionPreset.MEDIUM && globalPortion != PortionPreset.MEDIUM) globalPortion else parsed.portion))
        } else {
            val idx = items.indexOfFirst { canonicalTagKey(it.tag) == key }
            if (idx >= 0) {
                items[idx] = items[idx].copy(
                    quantity = items[idx].quantity + parsed.quantity,
                    amountGrams = items[idx].amountGrams?.let { a -> a + (parsed.amountGrams ?: 0.0) },
                )
            }
        }
    }

    if (items.isEmpty() && trimmed.isNotEmpty()) {
        parseFragment(trimmed, retrievalResult)?.let { items.add(it) }
    }

    return ParsedMealDescription(items = items, rawDescription = trimmed)
}

private fun isKnownNegationModifier(text: String, negMatch: MatchResult): Boolean {
    val afterNeg = text.substring(negMatch.range.last + 1).trim().lowercase()
    val firstWord = afterNeg.split("\\s+".toRegex()).firstOrNull() ?: return false
    return firstWord in listOf("piel", "grasa", "miga", "pieles", "grasas")
}

// ─── Fragment Parser ─────────────────────────────────────────────────────────

private fun parseFragment(
    frag: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
): ParsedMealItem? {
    var text = frag.trim()
    if (text.isEmpty()) return null

    // Handle negated items: "sin leche" → parse "leche" and mark excluded
    var isExcluded = false
    val sinPrefix = Regex("""^sin\s+""", RegexOption.IGNORE_CASE)
    val sinMatch = sinPrefix.find(text)
    if (sinMatch != null) {
        isExcluded = true
        text = text.removeRange(0, sinMatch.range.last + 1).trim()
        if (text.isEmpty()) return null
    }

    // Handle group: "Recipe (item1, item2)"
    val groupMatch = GROUP_PATTERN.find(text)
    if (groupMatch != null) {
        val groupName = groupMatch.groupValues[1].trim()
        val content = groupMatch.groupValues[2].trim()
        val subFragments = splitByListConnectors(content)
        val subItems = subFragments.mapNotNull { parseFragment(it, retrievalResult) }
        if (subItems.isNotEmpty()) {
            return ParsedMealItem(tag = groupName, isGroup = true, subItems = subItems)
        }
    }

    // Extract grams
    val gramsResult = extractGramsFromFragment(text)
    var grams = gramsResult.first
    var working = gramsResult.second
    var refQuantity: Double? = null
    var amountIntent = if (grams != null) AmountIntent.EXPLICIT_MASS else AmountIntent.UNSPECIFIED

    // If no grams, try reference (e.g., "1 cucharada de aceite")
    if (grams == null) {
        val refResult = extractReferenceFromFragment(working, retrievalResult)
        if (refResult.grams != null) {
            grams = refResult.grams
            working = refResult.foodPart
            refQuantity = refResult.quantity
            amountIntent = AmountIntent.RESOLVED_SUBJECTIVE
        }
    }

    // Extract cooking method
    val cookingMethod = extractCookingMethod(working)
    working = cookingMethod.second

    // Extract anatomical/preparation modifiers (colmada/rasa scale subjective grams)
    val modifierResult = extractModifiers(working, grams, amountIntent)
    working = modifierResult.third
    val modifierMacros = modifierResult.first
    grams = modifierResult.second

    // Extract portion preset
    val portionResult = extractPortionFromFragment(working)
    working = portionResult.second

    // Extract quantity multiplier ("2 panes", "3 huevos")
    val quantityResult = parseQuantityMultiplier(working)
    val quantity = if (refQuantity != null) refQuantity * quantityResult.first else quantityResult.first
    val foodName = quantityResult.second

    if (foodName.length < 2) return null

    // Canonical resolution
    val shouldSingularize = STARTS_WITH_DIGIT.containsMatchIn(working.trim())
    val canonical = normalizeFoodName(foodName, singularize = shouldSingularize)
    // Dataset priors only fill when the user gave no measure at all.
    // El prior del dataset es POR UNIDAD: se multiplica por la cantidad para no
    // perderla ("2 huevos" + prior 60g → 120g, "media manzana" → 60g).
    val resolvedGrams = when (amountIntent) {
        AmountIntent.EXPLICIT_MASS, AmountIntent.RESOLVED_SUBJECTIVE -> grams
        AmountIntent.UNSPECIFIED -> grams ?: retrievalResult
            ?.takeIf { it.confidence >= DATASET_PORTION_MIN_CONFIDENCE }
            ?.let { SemanticPortionRetriever.getGramsForFood(canonical, it) }
            ?.let { prior -> prior * quantity }
    }

    return ParsedMealItem(
        tag = canonical,
        quantity = quantity,
        amountGrams = resolvedGrams,
        cookingMethod = cookingMethod.first,
        portion = portionResult.first,
        isFuzzyMatch = false,
        appliedCookingFactor = COOKING_FACTORS[cookingMethod.first]?.kcal ?: 1.0,
        modifierScale = modifierMacros?.let {
            MacroOverrides(calories = it.kcal, protein = it.protein, carbs = it.carbs, fats = it.fats)
        },
        isExcluded = isExcluded,
        amountIntent = amountIntent,
    )
}

// ─── Split Connectors ────────────────────────────────────────────────────────

private fun splitByListConnectors(description: String): List<String> {
    var trimmed = description.trim()
    if (trimmed.isEmpty()) return emptyList()

    // Mask protected entities
    val masks = mutableListOf<Pair<String, String>>()
    trimmed = PROTECTED_ENTITIES_REGEX.replace(trimmed) { match ->
        val token = "__PROTECTED_${masks.size}__"
        masks.add(token to match.value)
        token
    }

    // Split by connectors
    var parts = listOf(trimmed)
    val splitBy = { regex: Regex ->
        parts = parts.flatMap { it.split(regex).map { s -> s.trim() }.filter { it.isNotEmpty() } }
    }
    splitBy(COMMA_OR_PLUS)
    splitBy(CONNECTOR_Y)
    splitBy(CONNECTOR_CON)

    // Unmask and split negations into separate excluded fragments
    parts = parts.flatMap { p ->
        var unmasked = p
        for ((token, original) in masks) {
            unmasked = unmasked.replace(token, original)
        }
        val negMatch = NEGATION_PATTERN.find(unmasked)
        if (negMatch != null && !isKnownNegationModifier(unmasked, negMatch)) {
            val beforeNeg = unmasked.substring(0, negMatch.range.first).trim()
            val afterNeg = unmasked.substring(negMatch.range.last + 1).trim()
            val sinFragment = "sin $afterNeg"
            listOfNotNull(beforeNeg.ifEmpty { null }, sinFragment.ifEmpty { null })
        } else {
            listOf(unmasked.trim())
        }
    }.filter { it.isNotEmpty() }

    // Split fragments containing multiple explicit measures ("100g arroz 50g pollo")
    parts = parts.flatMap { part ->
        val positions = GRAM_POSITION_LOOKAHEAD.findAll(part).map { it.range.first }.toList()
        if (positions.size <= 1) {
            listOf(part)
        } else {
            positions.mapIndexed { index, start ->
                val end = positions.getOrNull(index + 1) ?: part.length
                part.substring(start, end).trim()
            }
        }
    }.filter { it.isNotEmpty() }

    return parts
}

// ─── Extract Grams ───────────────────────────────────────────────────────────

private fun extractGramsFromFragment(text: String): Pair<Double?, String> {
    val match = GRAM_PATTERN.find(text) ?: return Pair(null, text)
    val numMatch = GRAM_UNIT_PATTERN.find(match.value) ?: return Pair(null, text)

    var value = numMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return Pair(null, text)
    val unit = numMatch.groupValues[2].lowercase()

    value = when {
        unit.matches(KG_LITER_PATTERN) -> value * 1000
        unit.matches(OZ_PATTERN) -> value * 28.3495
        unit.matches(LB_PATTERN) -> value * 453.592
        else -> value
    }

    val cleaned = text.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()
    return Pair(value, cleaned)
}

// ─── Extract Portion Reference ───────────────────────────────────────────────

private data class ReferenceResult(
    val grams: Double?,
    val quantity: Double,
    val foodPart: String,
)

private fun extractReferenceFromFragment(
    text: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
): ReferenceResult {
    val lower = text.lowercase()
    if (REFERENCE_KEYWORDS_FAST.none { lower.contains(it) }) {
        return resolveViaSubjectiveEngine(text, retrievalResult)
    }
    for ((pattern, refType) in REFERENCE_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val foodPart = (match.groupValues.lastOrNull { it.isNotEmpty() } ?: continue).trim()
        if (foodPart.length < 2) continue

        // Parse quantity
        val qtyStr = match.groupValues[1].lowercase()
        val qty = LITERAL_QUANTITIES[qtyStr] ?: qtyStr.replace(",", ".").toDoubleOrNull() ?: 1.0

        // Find food in DB to get portion type
        val food = findFoodByNormalized(foodPart)

        // Try high-fidelity subjective resolution first
        val densityCategory = SubjectivePortionEngine.detectDensityCategory(foodPart)
        val subjectiveResult = SubjectivePortionEngine.resolve(
            expression = match.value,
            foodCategory = densityCategory,
            standardPortion = food?.servingSize,
            retrievalResult = retrievalResult,
        )

        val grams = if (subjectiveResult != null) {
            kotlin.math.round(subjectiveResult.grams * 10) / 10.0
        } else {
            val gramsPerUnit = getGramsForReference(refType, food)
            kotlin.math.round(gramsPerUnit * qty * 10) / 10.0
        }

        // Return foodPart as the working text so parseFragment can use it as the food name.
        // Using `cleaned` (text with match removed) was wrong: when the reference covers the full
        // fragment (e.g. "una taza de avena") cleaned becomes "" → foodName.length < 2 → null item.
        return ReferenceResult(grams, qty, foodPart)
    }
    return resolveViaSubjectiveEngine(text, retrievalResult)
}

/**
 * F1.1: Fallback directo al motor subjetivo completo (~310 expresiones: "un montón de",
 * "una botella de", "una marraqueta", "una rodaja de", utensilios, comparaciones…).
 * Antes estas expresiones caían a UNSPECIFIED porque el gate de REFERENCE_KEYWORDS_FAST
 * y los ~19 REFERENCE_PATTERNS no las alcanzaban. El motor devuelve null si no matchea,
 * así que el fallback es seguro.
 */
private val SUBJECTIVE_PHRASE_STRIP = Regex(
    """^(?:(?:un|una|unos|unas|\d+(?:[.,]\d+)?)\s+)?[a-záéíóúñü]+(?:\s+[a-záéíóúñü]+){0,2}\s+de\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)

private fun resolveViaSubjectiveEngine(
    text: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult?,
): ReferenceResult {
    // Entidades protegidas ("empanada de pino", "café con leche"…) se resuelven
    // como plato completo: el motor las fragmentaría mal ("una empanada de pino"
    // dejaría "pino" como alimento).
    if (PROTECTED_ENTITIES_REGEX.containsMatchIn(text)) {
        return ReferenceResult(null, 1.0, text)
    }
    val food = findFoodByNormalized(text)
    val densityCategory = SubjectivePortionEngine.detectDensityCategory(text)
    val result = SubjectivePortionEngine.resolve(
        expression = text,
        foodCategory = densityCategory,
        standardPortion = food?.servingSize,
        retrievalResult = retrievalResult,
    ) ?: return ReferenceResult(null, 1.0, text)

    // Quitar la frase subjetiva ("un montón de") conservando el alimento. Si no hay
    // "de" (ej. "una marraqueta"), se deja el texto completo: parseQuantityMultiplier
    // se encarga del artículo y deja "marraqueta" como nombre.
    val foodPart = SUBJECTIVE_PHRASE_STRIP.find(text)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.length >= 2 }
        ?: text

    return ReferenceResult(
        grams = kotlin.math.round(result.grams * 10) / 10.0,
        quantity = 1.0,
        foodPart = foodPart,
    )
}

private const val DATASET_PORTION_MIN_CONFIDENCE = 0.35

// ─── Extract Cooking Method ──────────────────────────────────────────────────

private fun extractCookingMethod(text: String): Pair<CookingMethod?, String> {
    val lower = text.lowercase()
    if (COOKING_KEYWORDS_FAST.none { lower.contains(it) }) {
        return Pair(null, text)
    }
    for ((pattern, method) in COOKING_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val cleaned = text.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()
        return Pair(method, cleaned)
    }
    return Pair(null, text)
}

// ─── Extract Portion ─────────────────────────────────────────────────────────

private fun extractPortionFromFragment(text: String): Pair<PortionPreset, String> {
    val lower = text.lowercase()
    if (PORTION_KEYWORDS_FAST.none { lower.contains(it) }) {
        return Pair(PortionPreset.MEDIUM, text)
    }
    for ((pattern, preset, _) in PORTION_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val cleaned = text.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()
        return Pair(preset, cleaned)
    }
    return Pair(PortionPreset.MEDIUM, text)
}

// ─── Quantity Multiplier ─────────────────────────────────────────────────────

private fun parseQuantityMultiplier(text: String): Pair<Double, String> {
    val trimmed = text.trim().replace(HALF_PATTERN, "0.5")
        .replace(QUARTER_PATTERN, "0.25")
        .replace(THREE_QUARTERS_PATTERN, "0.75")

    // Range: "1-2 manzanas"
    val rangeMatch = RANGE_QUANTITY_PATTERN.find(trimmed)
    if (rangeMatch != null) {
        val qty1 = rangeMatch.groupValues[1].toDoubleOrNull() ?: 1.0
        val qty2 = rangeMatch.groupValues[2].toDoubleOrNull() ?: 1.0
        val rest = rangeMatch.groupValues[3].trim()
        if (rest.length >= 2) {
            val avg = (qty1 + qty2) / 2.0
            return Pair(avg, rest)
        }
    }

    // Pair: "un par de huevos" → 2 (debe ir antes del patrón numérico)
    val pairMatch = PAIR_PATTERN.find(trimmed)
    if (pairMatch != null) {
        val rest = pairMatch.groupValues[1].trim()
        if (rest.length >= 2) {
            return Pair(2.0, rest)
        }
    }

    // Suffix multiplier: "huevos x2", "3 huevos x2" → cantidad × sufijo
    val suffixMatch = SUFFIX_X_PATTERN.find(trimmed)
    if (suffixMatch != null) {
        val rest = suffixMatch.groupValues[2].trim()
        if (rest.length >= 2) {
            val suffix = suffixMatch.groupValues[3].toDoubleOrNull() ?: 1.0
            val base = suffixMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            return Pair(base * suffix, rest)
        }
    }

    // Number: "2 manzanas", "3 huevos"
    val numMatch = NUMBER_QUANTITY_PATTERN.find(trimmed)
    if (numMatch != null) {
        val qty = numMatch.groupValues[1].toDoubleOrNull() ?: 1.0
        val rest = numMatch.groupValues[2].trim()
        if (rest.length >= 2) {
            return Pair(qty.coerceAtLeast(0.0), rest)
        }
    }

    // Literal: "dos huevos", "media manzana"
    val literalMatch = LITERAL_QUANTITY_PATTERN.find(trimmed)
    if (literalMatch != null) {
        val qty = LITERAL_QUANTITIES[literalMatch.groupValues[1].lowercase()]
        val rest = literalMatch.groupValues[2].trim()
        if (qty != null && rest.length >= 2) {
            return Pair(qty, rest)
        }
    }

    return Pair(1.0, trimmed)
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun stripAccents(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

/**
 * Clave canónica para DEDUPE (G5/G7): minúsculas sin tildes y singularizada.
 * "huevo", "huevos", "Huevo" y "2 huevos" + "1 huevo" se fusionan como el mismo alimento.
 * Solo afecta la fusión de items; el tag visible se conserva tal cual.
 */
private fun canonicalTagKey(tag: String): String {
    val stripped = stripAccents(tag.lowercase())
    if (stripped.length <= 4) return stripped
    return when {
        stripped.endsWith("ces") && stripped.length > 5 -> stripped.dropLast(3) + "z"
        stripped.endsWith("es") && stripped.length > 4 -> stripped.dropLast(2)
        stripped.endsWith("s") && stripped.length > 4 -> stripped.dropLast(1)
        else -> stripped
    }
}

private fun extractGlobalPortion(description: String): PortionPreset {
    for ((pattern, preset, _) in PORTION_PATTERNS) {
        if (pattern.containsMatchIn(description)) return preset
    }
    return PortionPreset.MEDIUM
}

private fun normalizeFoodName(name: String, singularize: Boolean = false): String {
    var normalized = name.trim()
        .lowercase()
        .replace(SPACES_PATTERN, " ")
        .replace(LEADING_DE_PATTERN, "")
        .replace(LEADING_ARTICLE_PATTERN, "")
        .replace(PORTION_PREFIX_PATTERN, "")
        .replace(ARTICLE_PORTION_PREFIX_PATTERN, "")
        .replace(TRAILING_DE_PATTERN, "")

    // Diminutivos con validación de raíz: "huevito"→"huevo" pero "mantequilla" NO se rompe
    normalized = TextNormalizer.canonicalizeDiminutives(normalized)

    if (singularize) {
        normalized = when {
            normalized.endsWith("ces") && normalized.length > 4 -> normalized.dropLast(3) + "z"
            normalized.endsWith("es") && normalized.length > 4 -> normalized.dropLast(2)
            normalized.endsWith("s") && normalized.length > 3 -> normalized.dropLast(1)
            else -> normalized
        }
    }

    return normalized
}

// ─── Anatomical / Preparation Modifiers ────────────────────────────────

private data class ModifierResult(
    val macroScale: MacroScale?,
    val grams: Double?,
    val cleanedText: String,
)

private data class MacroScale(
    val kcal: Double = 1.0,
    val protein: Double = 1.0,
    val carbs: Double = 1.0,
    val fats: Double = 1.0,
)

private val MODIFIER_PATTERNS = listOf(
    // sin piel / sin grasa → fats ×0.6
    Pair(Regex("""\bsin\s+(piel|grasa)\b""", RegexOption.IGNORE_CASE),
        MacroScale(fats = 0.6)),
    // sin miga (pan) → carbs ×0.6, kcal ×0.65
    Pair(Regex("""\bsin\s+miga\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 0.65, carbs = 0.6)),
    // solo claras → lower protein/fat
    Pair(Regex("""\bsolo?\s+claras?\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 0.55, protein = 0.85, carbs = 1.0, fats = 0.05)),
    // descremado / light / 0% → fats ×0.15 (lácteos)
    Pair(Regex("""\b(descremad[oa]|light|0\s*%)\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 0.7, fats = 0.15)),
    // en almíbar / con azúcar → carbs ×1.4, kcal ×1.3
    Pair(Regex("""\ben\s+alm[ií]bar\b|\bcon\s+az[uú]car\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 1.3, carbs = 1.4)),
    // integral → slight fiber boost (handled by finding integral variant)
    Pair(Regex("""\bintegral(es)?\b""", RegexOption.IGNORE_CASE),
        MacroScale(carbs = 0.9, kcal = 0.95)),
    // grande / colmada → portion ×1.25
    Pair(Regex("""\b(colmad[oa]|generos[oa])\b""", RegexOption.IGNORE_CASE),
        MacroScale()), // handled as portion modifier, not macro
    // rasa / fina / pequeña → portion ×0.75
    Pair(Regex("""\b(ras[oa]|fin[oa]|pequeñ[oa])\b""", RegexOption.IGNORE_CASE),
        MacroScale()), // handled as portion modifier
)

private fun extractModifiers(
    text: String,
    currentGrams: Double?,
    amountIntent: AmountIntent = AmountIntent.UNSPECIFIED,
): Triple<MacroScale?, Double?, String> {
    val lower = text.lowercase()
    if (MODIFIER_KEYWORDS_FAST.none { lower.contains(it) }) {
        return Triple(null, currentGrams, text)
    }
    var working = text
    var resultScale: MacroScale? = null
    var gramsOverride = currentGrams

    for ((pattern, scale) in MODIFIER_PATTERNS) {
        val match = pattern.find(working) ?: continue
        working = working.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()

        // Portion modifiers (colmada/rasa) scale subjective grams once; never touch explicit mass.
        val matchText = match.value.lowercase()
        if (matchText.contains("colmad") || matchText.contains("generos")) {
            if (amountIntent != AmountIntent.EXPLICIT_MASS && gramsOverride != null) {
                gramsOverride = gramsOverride * 1.25
            }
        } else if (matchText.contains("rasa") || matchText.contains("fina") || matchText.contains("pequeñ")) {
            if (amountIntent != AmountIntent.EXPLICIT_MASS && gramsOverride != null) {
                gramsOverride = gramsOverride * 0.75
            }
        } else {
            // It's a macro modifier - combine scales
            resultScale = if (resultScale != null) {
                MacroScale(
                    kcal = resultScale.kcal * scale.kcal,
                    protein = resultScale.protein * scale.protein,
                    carbs = resultScale.carbs * scale.carbs,
                    fats = resultScale.fats * scale.fats,
                )
            } else scale
        }
    }

    return Triple(resultScale, gramsOverride, working)
}
