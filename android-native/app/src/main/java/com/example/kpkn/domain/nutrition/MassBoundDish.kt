package com.example.kpkn.domain.nutrition

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * A mass said next to a named dish weighs the whole dish (WP-N9; audit finding A-P5): "200 g de arroz con pollo" is 100 g of rice
 * and 100 g of chicken, not 200 g of rice and a chicken of unknown weight, and "arroz con pollo 200 g" is the same plate.
 *
 * The dish is a key of [FoodCombinationParser]'s known dishes that the parser would cut at its "con" or its "y"; its parts take the
 * mass by the shares of the dish, and each part is an explicit mass of its own. Without a mass the dish still splits into its
 * foods, as before ("arroz con pollo" is two mentions). A mass binds to the whole dish only when:
 *
 * - the dish is a dish by its first word (rice, pasta, bread, arepa, salad, omelette, sandwich, quesadilla): "150 g de pollo con
 *   arroz" is 150 g of chicken with rice on the side, and the mass stays with the chicken;
 * - every part is a solid food of the plate. A drink or a soup base ("avena con leche", "arroz con leche"), a sauce or a garnish
 *   ("pasta con salsa de tomate", "manzana con canela") and a staple weighed dry on its own ("100 g de avena con plátano") keep
 *   the mass for their first food: "100 g de avena con leche" is still 100 g of oats;
 * - no protected phrase names the dish already: "250 g de pan con palta" is the catalog row, not bread and avocado;
 * - the dish ends the mention: a connector or a separator follows it ("200 g de arroz con pollo y ensalada" opens a second mention
 *   at the "y"; "200 g de arroz con pollo al horno" is left to the old reading).
 *
 * A mass in front of a vessel ("un plato de arroz con pollo") is not touched: the vessel is read by the portion engine.
 *
 * Pure Kotlin / JVM: no Android dependency.
 */
internal object MassBoundDish {

    /** A dish and its mass found in a text: [range] covers the mass, its "de" and the dish, or the dish and its mass. */
    class Match(
        val range: IntRange,
        /** The dish as [FoodCombinationParser] names it ("arroz con pollo"). */
        val dish: String,
        val grams: Double,
        /** What each part is called: the person's own words when they name as many parts as the dish has ("pollo a la plancha"). */
        val parts: List<String>,
        /** The share of the mass of each part; they add up to 1. */
        val shares: List<Double>,
    )

    private const val MASS_UNITS = "kilogramos?|kilos?|kg|gramos?|gr|g|oz|onzas?|lb|libras?"
    private const val MAX_DISH_WORDS = 8

    /** Longest text scanned: a meal description is a few lines, and anything beyond this is left to the old reading. */
    private const val MAX_SCAN_CHARS = 2000

    // Possessive quantifiers throughout: none of these patterns needs to give back what it has read, and a long run of blanks
    // cannot make them backtrack.

    /** "200 g de ": the mass and the "de" that joins it to what comes next. */
    private val MASS_IN_FRONT = Regex(
        """(?<![\p{L}\p{N}_.])(\d++(?:\.\d++)?+)\s*+($MASS_UNITS)(?![\p{L}\p{N}_])(?:\s++de(?![\p{L}\p{N}_]))?+\s++""",
        RegexOption.IGNORE_CASE,
    )

    /** " 200 g": the mass said after the dish. */
    private val MASS_BEHIND = Regex("""\s++(\d++(?:\.\d++)?+)\s*+($MASS_UNITS)(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)

    /** What may follow a dish for its mass to be the mass of the dish: the end, a separator or the connector that opens the next mention. */
    private val DISH_END = Regex(
        """^(?:\s*+$|[ \t]*+[,;+.\r\n]|\s++(?:y|e|mas|más|con|sin)(?![\p{L}\p{N}_]))""",
        RegexOption.IGNORE_CASE,
    )

    private val WORD = Regex("""\S++""")
    private val SPACES = Regex("""\s++""")
    private val PART_SPLIT = Regex("""\s++(?:con|y)\s++|\s*+,\s*+""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION = charArrayOf(',', ';', '.')

    /** Parts that are not solid food on the plate: the dish is a drink, a porridge, a soup or a dessert with its cream. */
    private val LIQUID_PARTS = setOf("leche", "caldo", "yogurt", "cafe", "crema", "helado", "jugo", "agua", "bebida")

    /** Staples weighed dry on their own: the mass is theirs, whatever goes with them. */
    private val DRY_STAPLES = setOf("avena", "cereal", "granola")

    /**
     * Keys that name a dish as such by their first word ("arroz con pollo", "pan con queso", "ensalada de lechuga y tomate"): the
     * mass is the dish's. A key that opens with a protein ("pollo con arroz", "bistec con papas") names what is weighed and what goes
     * with it, and "150 g de pollo con arroz" keeps the 150 g for the chicken, as before.
     */
    private val DISH_HEADS = setOf("arroz", "pasta", "pan", "arepa", "ensalada", "tortilla", "sandwich", "quesadilla")

    private class Dish(val foods: List<String>, val shares: List<Double>)

    private val NOT_A_DISH = Any()
    private val dishes = ConcurrentHashMap<String, Any>()

    private fun stripAccents(text: String): String =
        if (text.all { it.code < 128 }) text else Normalizer.normalize(text, Normalizer.Form.NFD).replace(TextKeys.MARKS, "")

    /** Every mass-bound dish of [text], left to right, without overlaps. */
    fun findAll(text: String): List<Match> {
        if (text.length > MAX_SCAN_CHARS || text.none { it.isDigit() }) return emptyList()
        val found = ArrayList<Match>(1)
        for (mass in MASS_IN_FRONT.findAll(text)) {
            val start = mass.range.last + 1
            val words = WORD.findAll(text, start).take(MAX_DISH_WORDS).toList()
            for (count in words.size downTo 2) {
                val end = trimmedEnd(text, words[count - 1])
                val candidate = text.substring(start, end)
                val dish = dishOf(candidate) ?: continue
                if (!DISH_END.containsMatchIn(text.substring(end))) continue
                found += match(mass.range.first until end, candidate, dish, mass.groupValues[1], mass.groupValues[2])
                break
            }
        }
        for (mass in MASS_BEHIND.findAll(text)) {
            if (found.any { mass.range.first <= it.range.last && it.range.first <= mass.range.last }) continue
            val before = text.substring(0, mass.range.first)
            val words = WORD.findAll(before).toList().takeLast(MAX_DISH_WORDS)
            for (count in words.size downTo 2) {
                val first = words[words.size - count].range.first
                val candidate = before.substring(first)
                val dish = dishOf(candidate) ?: continue
                if (!DISH_END.containsMatchIn(text.substring(mass.range.last + 1))) continue
                found += match(first..mass.range.last, candidate, dish, mass.groupValues[1], mass.groupValues[2])
                break
            }
        }
        return found.sortedBy { it.range.first }
    }

    /** The match when the whole of [text] is a mass and a dish ("200 g de arroz con pollo"), null otherwise. */
    fun whole(text: String): Match? {
        val trimmed = text.trim()
        return findAll(trimmed).singleOrNull()?.takeIf { it.range.first == 0 && it.range.last == trimmed.length - 1 }
    }

    /** One "100 g de arroz" per part: the mass of the dish by the shares of its parts, the last part taking the rounding. */
    fun fragments(match: Match): List<String> {
        var given = 0.0
        return match.parts.mapIndexed { index, part ->
            val grams = if (index == match.parts.lastIndex) {
                round1(match.grams - given).coerceAtLeast(0.0)
            } else {
                round1(match.grams * match.shares[index]).also { given += it }
            }
            "${format(grams)} g de $part"
        }
    }

    // ─── Internals ─────────────────────────────────────────────────────────────────────────────────────────────────

    private fun trimmedEnd(text: String, word: MatchResult): Int {
        var end = word.range.last + 1
        while (end > word.range.first + 1 && text[end - 1] in TRAILING_PUNCTUATION) end--
        return end
    }

    private fun match(range: IntRange, candidate: String, dish: Dish, number: String, unit: String): Match {
        val own = candidate.split(PART_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        return Match(
            range = range,
            dish = candidate.trim().lowercase().replace(SPACES, " "),
            grams = toGrams(number.toDoubleOrNull() ?: 0.0, unit),
            parts = if (own.size == dish.foods.size) own else dish.foods,
            shares = dish.shares,
        )
    }

    private fun toGrams(value: Double, unit: String): Double {
        val lower = unit.lowercase()
        return when {
            lower.startsWith("k") -> value * 1000.0
            lower.startsWith("o") -> value * 28.3495
            lower.startsWith("l") -> value * 453.592
            else -> value
        }
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private fun format(grams: Double): String =
        if (grams == kotlin.math.floor(grams)) grams.toLong().toString() else String.format(Locale.ROOT, "%.1f", grams)

    private fun dishOf(candidate: String): Dish? {
        val key = candidate.trim().lowercase().replace(SPACES, " ")
        return dishes.getOrPut(key) { lookup(key) ?: NOT_A_DISH } as? Dish
    }

    private fun lookup(key: String): Dish? {
        // A dish with no connector is one mention already: there is nothing to keep from being cut.
        if (!(" con " in key || " y " in key || ',' in key)) return null
        if (ProtectedPhrases.isPhrase(key)) return null
        if (stripAccents(key.substringBefore(' ')) !in DISH_HEADS) return null
        val combination = known(key) ?: known(stripAccents(key)) ?: return null
        val foods = listOf(combination.baseFood) + combination.accompaniments.map { it.food }
        val kinds = foods.map { stripAccents(it.lowercase()) }
        val plate = combination.accompaniments.none {
            it.role == FoodCombinationParser.Role.SAUCE || it.role == FoodCombinationParser.Role.GARNISH
        } && kinds.none { it in LIQUID_PARTS } && kinds.first() !in DRY_STAPLES
        if (!plate) return null
        val shares = listOf(combination.baseProportion) + combination.accompaniments.map { it.proportion }
        val total = shares.sum()
        if (total <= 0.0) return null
        return Dish(foods, shares.map { it / total })
    }

    private fun known(key: String): FoodCombinationParser.ParsedCombination? =
        FoodCombinationParser.parse(key).takeIf { it.isKnownDish && it.dishName == key }
}
