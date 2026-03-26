package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.getGramsForReference
import com.example.kpkn.data.models.*

/**
 * FoodParser — Deterministic Spanish NLP parser for free-form food descriptions.
 * Mirrors utils/nutritionDescriptionParser.ts from PWA.
 * Handles: grams, portions, cooking methods, brands, modifiers.
 */

// ─── Regex Patterns ──────────────────────────────────────────────────────────

private val GRAM_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*(?:g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)(?:\s+de)?\s*""", RegexOption.IGNORE_CASE)

private val COMMA_OR_PLUS = Regex("""(,\s*|\s+\+\s+)""")
private val CONNECTOR_Y = Regex("""\s+(?:y|e|mas|más)\s+""", RegexOption.IGNORE_CASE)
private val CONNECTOR_CON = Regex("""\s+con\s+""", RegexOption.IGNORE_CASE)

private val PROTECTED_ENTITIES = listOf(
    "arroz con leche", "arroz con pollo", "pollo con papas", "pan con queso", "pan con palta",
    "pan con mantequilla", "papas con mayo", "pastel de choclo", "empanada de pino"
)

private val LITERAL_QUANTITIES = mapOf(
    "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0,
    "cuatro" to 4.0, "cinco" to 5.0, "seis" to 6.0, "siete" to 7.0,
    "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0,
    "media" to 0.5, "medio" to 0.5, "mitad" to 0.5,
    "cuarto" to 0.25, "tercio" to 0.33, "doble" to 2.0, "triple" to 3.0,
)

private val PORTION_PATTERNS = listOf(
    Triple(Regex("""\b(grande|generoso|generosa)\b""", RegexOption.IGNORE_CASE), PortionPreset.EXTRA, "extra"),
    Triple(Regex("""\bplato\s+grande\b""", RegexOption.IGNORE_CASE), PortionPreset.LARGE, "large"),
    Triple(Regex("""\b(mediano|mediana)\b""", RegexOption.IGNORE_CASE), PortionPreset.MEDIUM, "medium"),
    Triple(Regex("""\b(pequeño|chico|chica)\b""", RegexOption.IGNORE_CASE), PortionPreset.SMALL, "small"),
)

private val COOKING_PATTERNS = listOf(
    Pair(Regex("""\b(a\s+la\s+)?plancha\b""", RegexOption.IGNORE_CASE), CookingMethod.PLANCHA),
    Pair(Regex("""\b(hornead[oa]|al\s+horno|horno)\b""", RegexOption.IGNORE_CASE), CookingMethod.HORNO),
    Pair(Regex("""\b(frit[oa]|frito|fritos)\b""", RegexOption.IGNORE_CASE), CookingMethod.FRITO),
    Pair(Regex("""\b(cocid[oa]|cocido|cocidos)\b""", RegexOption.IGNORE_CASE), CookingMethod.COCIDO),
    Pair(Regex("""\b(crud[oa]|crudo|crudos)\b""", RegexOption.IGNORE_CASE), CookingMethod.CRUDO),
)

private val REFERENCE_PATTERNS = listOf(
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(cucharadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(un|una|media|1)\s+(cucharada)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(dos|tres)\s+(cucharadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(cucharaditas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "teaspoon"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(tazas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(taza)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(puñado)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "handful"),
    Pair(Regex("""\b(un|1)\s+(puño)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "fist"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(vasos?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "glass"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(rebanadas?|tajadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "slice"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(latas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "can"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(scoops?|medidas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "scoop"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(porciones?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "portion"),
)

// ─── Main Parser ─────────────────────────────────────────────────────────────

fun parseMealDescription(description: String): ParsedMealDescription {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return ParsedMealDescription(rawDescription = "")

    val fragments = splitByListConnectors(trimmed)
    val items = mutableListOf<ParsedMealItem>()
    val seen = mutableSetOf<String>()
    val globalPortion = extractGlobalPortion(trimmed)

    for (frag in fragments) {
        val parsed = parseFragment(frag) ?: continue
        if (parsed.tag !in seen) {
            seen.add(parsed.tag)
            items.add(parsed.copy(portion = if (parsed.portion == PortionPreset.MEDIUM && globalPortion != PortionPreset.MEDIUM) globalPortion else parsed.portion))
        } else {
            val idx = items.indexOfFirst { it.tag == parsed.tag }
            if (idx >= 0) {
                items[idx] = items[idx].copy(
                    quantity = items[idx].quantity + parsed.quantity,
                    amountGrams = items[idx].amountGrams?.let { a -> a + (parsed.amountGrams ?: 0.0) },
                )
            }
        }
    }

    if (items.isEmpty() && trimmed.isNotEmpty()) {
        parseFragment(trimmed)?.let { items.add(it) }
    }

    return ParsedMealDescription(items = items, rawDescription = trimmed)
}

// ─── Fragment Parser ─────────────────────────────────────────────────────────

private fun parseFragment(frag: String): ParsedMealItem? {
    var text = frag.trim()
    if (text.isEmpty()) return null

    // Handle group: "Recipe (item1, item2)"
    val groupMatch = Regex("^(.+?)\\s*\\((.+)\\)\\s*$").find(text)
    if (groupMatch != null) {
        val groupName = groupMatch.groupValues[1].trim()
        val content = groupMatch.groupValues[2].trim()
        val subFragments = splitByListConnectors(content)
        val subItems = subFragments.mapNotNull { parseFragment(it) }
        if (subItems.isNotEmpty()) {
            return ParsedMealItem(tag = groupName, isGroup = true, subItems = subItems)
        }
    }

    // Extract grams
    val gramsResult = extractGramsFromFragment(text)
    var grams = gramsResult.first
    var working = gramsResult.second

    // If no grams, try reference (e.g., "1 cucharada de aceite")
    if (grams == null) {
        val refResult = extractReferenceFromFragment(working)
        if (refResult.first != null) {
            grams = refResult.first
            working = refResult.second
        }
    }

    // Extract cooking method
    val cookingMethod = extractCookingMethod(working)
    working = cookingMethod.second

    // Extract portion preset
    val portionResult = extractPortionFromFragment(working)
    working = portionResult.second

    // Extract quantity multiplier ("2 panes", "3 huevos")
    val quantityResult = parseQuantityMultiplier(working)
    val quantity = quantityResult.first
    val foodName = quantityResult.second

    if (foodName.length < 2) return null

    // Canonical resolution
    val canonical = normalizeFoodName(foodName)

    return ParsedMealItem(
        tag = canonical,
        quantity = quantity,
        amountGrams = grams,
        cookingMethod = cookingMethod.first,
        portion = if (grams != null) PortionPreset.MEDIUM else portionResult.first,
        isFuzzyMatch = false,
    )
}

// ─── Split Connectors ────────────────────────────────────────────────────────

private fun splitByListConnectors(description: String): List<String> {
    var trimmed = description.trim()
    if (trimmed.isEmpty()) return emptyList()

    // Mask protected entities
    val masks = mutableListOf<Pair<String, String>>()
    PROTECTED_ENTITIES.forEachIndexed { index, entity ->
        val regex = Regex("""\b$entity\b""", RegexOption.IGNORE_CASE)
        trimmed = trimmed.replace(regex) { match ->
            val token = "__PROTECTED_${index}__"
            masks.add(token to match.value)
            token
        }
    }

    // Split by connectors
    var parts = listOf(trimmed)
    val splitBy = { regex: Regex ->
        parts = parts.flatMap { it.split(regex).map { s -> s.trim() }.filter { it.isNotEmpty() } }
    }
    splitBy(COMMA_OR_PLUS)
    splitBy(CONNECTOR_Y)
    splitBy(CONNECTOR_CON)

    // Unmask and clean negations
    parts = parts.map { p ->
        var unmasked = p
        for ((token, original) in masks) {
            unmasked = unmasked.replace(token, original)
        }
        // Remove "sin X" that isn't a known modifier
        val negMatch = Regex("""\b(?:sin|menos|no)\b""", RegexOption.IGNORE_CASE).find(unmasked)
        if (negMatch != null) {
            unmasked = unmasked.substring(0, negMatch.range.first)
        }
        unmasked.trim()
    }.filter { it.isNotEmpty() }

    return parts
}

// ─── Extract Grams ───────────────────────────────────────────────────────────

private fun extractGramsFromFragment(text: String): Pair<Double?, String> {
    val match = GRAM_PATTERN.find(text) ?: return Pair(null, text)
    val numMatch = Regex("""(\d+(?:[.,]\d+)?)\s*(g|gr|gramos?|kg|ml|mililitros?|l|litros?|oz|onzas?|lb|libras?)""", RegexOption.IGNORE_CASE).find(match.value) ?: return Pair(null, text)

    var value = numMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return Pair(null, text)
    val unit = numMatch.groupValues[2].lowercase()

    value = when {
        unit.matches(Regex("kg|l$|litros?")) -> value * 1000
        unit.matches(Regex("oz|onzas?")) -> value * 28.3495
        unit.matches(Regex("lb|libras?")) -> value * 453.592
        else -> value
    }

    val cleaned = text.replace(match.value, " ").replace(Regex("\\s{2,}"), " ").trim()
    return Pair(value, cleaned)
}

// ─── Extract Portion Reference ───────────────────────────────────────────────

private fun extractReferenceFromFragment(text: String): Pair<Double?, String> {
    for ((pattern, refType) in REFERENCE_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val foodPart = (match.groupValues.lastOrNull { it.isNotEmpty() } ?: continue).trim()
        if (foodPart.length < 2) continue

        // Parse quantity
        val qtyStr = match.groupValues[1].lowercase()
        val qty = LITERAL_QUANTITIES[qtyStr] ?: qtyStr.replace(",", ".").toDoubleOrNull() ?: 1.0

        // Find food in DB to get portion type
        val food = findFoodByNormalized(foodPart)
        val gramsPerUnit = getGramsForReference(refType, food)
        val grams = kotlin.math.round(gramsPerUnit * qty * 10) / 10.0

        val cleaned = text.replace(match.value, " ").replace(Regex("\\s{2,}"), " ").trim()
        return Pair(grams, cleaned)
    }
    return Pair(null, text)
}

// ─── Extract Cooking Method ──────────────────────────────────────────────────

private fun extractCookingMethod(text: String): Pair<CookingMethod?, String> {
    for ((pattern, method) in COOKING_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val cleaned = text.replace(match.value, " ").replace(Regex("\\s{2,}"), " ").trim()
        return Pair(method, cleaned)
    }
    return Pair(null, text)
}

// ─── Extract Portion ─────────────────────────────────────────────────────────

private fun extractPortionFromFragment(text: String): Pair<PortionPreset, String> {
    for ((pattern, preset, _) in PORTION_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val cleaned = text.replace(match.value, " ").replace(Regex("\\s{2,}"), " ").trim()
        return Pair(preset, cleaned)
    }
    return Pair(PortionPreset.MEDIUM, text)
}

// ─── Quantity Multiplier ─────────────────────────────────────────────────────

private fun parseQuantityMultiplier(text: String): Pair<Int, String> {
    val trimmed = text.trim().replace(Regex("""\b1/2\b"""), "0.5")
        .replace(Regex("""\b1/4\b"""), "0.25")
        .replace(Regex("""\b3/4\b"""), "0.75")

    // Range: "1-2 manzanas"
    val rangeMatch = Regex("""^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s+(.+)$""").find(trimmed)
    if (rangeMatch != null) {
        val qty1 = rangeMatch.groupValues[1].toDoubleOrNull() ?: 1.0
        val qty2 = rangeMatch.groupValues[2].toDoubleOrNull() ?: 1.0
        val rest = rangeMatch.groupValues[3].trim()
        if (rest.length >= 2) {
            val avg = kotlin.math.round((qty1 + qty2) / 2).toInt().coerceIn(1, 50)
            return Pair(avg, rest)
        }
    }

    // Number: "2 manzanas", "3 huevos"
    val numMatch = Regex("""^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$""").find(trimmed)
    if (numMatch != null) {
        val qty = numMatch.groupValues[1].toDoubleOrNull() ?: 1.0
        val rest = numMatch.groupValues[2].trim()
        if (rest.length >= 2) {
            return Pair(qty.toInt().coerceIn(1, 50), rest)
        }
    }

    // Literal: "dos huevos"
    val literalMatch = Regex("""^(un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|medio|mitad|cuarto|tercio|doble|triple)\s+(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
    if (literalMatch != null) {
        val qty = LITERAL_QUANTITIES[literalMatch.groupValues[1].lowercase()]
        val rest = literalMatch.groupValues[2].trim()
        if (qty != null && rest.length >= 2) {
            return Pair(qty.toInt().coerceIn(1, 50), rest)
        }
    }

    return Pair(1, trimmed)
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun extractGlobalPortion(description: String): PortionPreset {
    for ((pattern, preset, _) in PORTION_PATTERNS) {
        if (pattern.containsMatchIn(description)) return preset
    }
    return PortionPreset.MEDIUM
}

private fun normalizeFoodName(name: String): String {
    return name.trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^de\\s+"), "")
        .replace(Regex("\\s+de\\s+$"), "")
}
