package com.example.kpkn.domain.nutrition

/**
 * FoodCombinationParser — Detecta y parsea combinaciones de comida.
 *
 * Estructuras lingüísticas principales:
 * - [A] con [B] → pan con palta, arroz con pollo
 * - [A] de [B] → ensalada de lechuga, tortilla de patatas
 * - [A] y [B] → jamón y queso, arroz y frijoles
 * - [A] a la/el [B] → pollo a la plancha, pescado al horno
 *
 * Los platos conocidos y su descomposición son la sección `dishCompositions` de [FoodKnowledge] (WP-N13); aquí quedan la lógica y lo que se
 * deriva de ella.
 */
object FoodCombinationParser {

    private val CON_Y_COMMA_LEADING_PATTERN = Regex("""^\s*(?:con|y|,)\s*""")
    private val COMBO_SPLIT_PATTERN = Regex("""\s+(?:con|y|,)\s+""")

    /** The first word of a new, quantified mention: a digit, an article or a number word ("1 jugo", "una coca cola", "media palta"). */
    private const val QUANTIFIED_OPENER =
        """(?:\d|(?:un|una|unos|unas|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|medio|media|otro|otra)(?![\p{L}\p{N}_]))"""

    /** The start of a quantified mention: what comes after a separator that is not a filling ("y un jugo", ", 2 galletas"). */
    private val QUANTIFIED_START = Regex("^$QUANTIFIED_OPENER", RegexOption.IGNORE_CASE)

    /** The opening of a generic sandwich mention, "sándwich de ": its fillings follow. */
    private val SANDWICH_OPENING = Regex(RegexEs.LEFT_EDGE + """(?:s[aá]ndwich|sandwich)\s+de\s+""", RegexOption.IGNORE_CASE)

    /**
     * What stands between two fillings: a comma ("jamón, queso"), "y", "e", "con", "mas" or "más". "ademas" and "además" separate
     * too, but they end the sandwich: what follows is something else said in addition.
     */
    private val FILLING_SEPARATOR = Regex(
        """\s*,\s*(?:(?:y|e|con)\s+)?|\s+(?:y|e|con|mas|más|ademas|además)\s+""",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_FILLINGS = 6
    private const val MAX_FILLING_LENGTH = 80

    /** A sandwich and what goes in it: [start] and [end] delimit the mention in the lower-cased text, [fillings] are what is in it. */
    private class SandwichClause(val start: Int, val end: Int, val fillings: List<String>)

    /**
     * "sándwich de <filling> [<separator> <filling>]..." bounded to its own mention (WP-N11, N11b): one filling ("sándwich de queso
     * y un jugo" has one), two or a list ("sándwich de jamón, queso y palta"). The mention ends at the first thing that is not a
     * filling: punctuation ('.' or ';'), a new quantified mention ("... y una coca cola", ", 2 galletas"), a drink ("... y café",
     * ", jugo") or the end of the text. The greedy tail this replaces swallowed everything said after the sandwich. Null when [lower]
     * has no sandwich with a filling.
     */
    private fun sandwichClause(lower: String, from: Int = 0): SandwichClause? {
        val opening = SANDWICH_OPENING.find(lower, from) ?: return null
        val tailStart = opening.range.last + 1
        val limit = lower.indexOfAny(charArrayOf(';', '.'), tailStart).let { if (it < 0) lower.length else it }
        val fillings = ArrayList<String>()
        var cursor = tailStart
        var end = tailStart
        while (fillings.size < MAX_FILLINGS) {
            val separator = FILLING_SEPARATOR.find(lower, cursor)?.takeIf { it.range.first < limit }
            val raw = lower.substring(cursor, separator?.range?.first ?: limit)
            val fragment = raw.trim()
            if (!isFilling(fragment, first = fillings.isEmpty())) break
            fillings += fragment
            end = cursor + raw.trimEnd().length
            if (separator == null || separator.value.contains("ademas") || separator.value.contains("además")) break
            cursor = separator.range.last + 1
        }
        return if (fillings.isEmpty()) null else SandwichClause(opening.range.first, end, fillings)
    }

    /** A fragment that can be a filling: not blank, not a new quantified mention and, after the first, not a drink. */
    private fun isFilling(fragment: String, first: Boolean): Boolean {
        if (fragment.isEmpty() || fragment.length > MAX_FILLING_LENGTH) return false
        if (QUANTIFIED_START.containsMatchIn(fragment)) return false
        return first || !HouseholdPortions.isDrinkName(fragment)
    }

    /** A known dish as a whole phrase: it ends the text or is followed by a connector ("arroz con leche condensada" is not "arroz con leche"). */
    private fun dishRegex(dishName: String): Regex = Regex(
        RegexEs.boundedLiteral(dishName) + """(?=$|\s+(?:con|y|e|mas|más|sin|a|al|de|,)\b)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The known dishes the parser works with: the `dishCompositions` section of [FoodKnowledge] (WP-N13) and what is derived from it, built
     * once per snapshot (an install that changes the knowledge builds it again). Each dish has its compiled regex, shared by every analysis:
     * the logger can run two of them at once.
     */
    private class KnownDishes(source: DishCompositionsKnowledge) {
        class Dish(val name: String, val components: List<DishComponent>, val regex: Regex)

        /** In the order of the section: of two matching dishes of the same length, the first one wins. */
        val dishes: List<Dish> = source.dishes.map { Dish(it.name, it.components, dishRegex(it.name)) }

        /** The known sandwiches, in the same order: the dishes whose name starts with "sandwich" or "sándwich" ([nextSandwichSpan]). */
        val sandwiches: List<Dish> = dishes.filter { it.name.startsWith("sandwich") || it.name.startsWith("sándwich") }
    }

    private val KNOWN_DISHES = KnowledgeCache { snapshot -> KnownDishes(snapshot.dishCompositions) }

    data class ParsedCombination(
        val baseFood: String,
        val baseProportion: Double,
        val accompaniments: List<Accompaniment>,
        val cookingMethod: String?,
        val isKnownDish: Boolean,
        val dishName: String?,
        val confidence: Double,
    )

    data class Accompaniment(
        val food: String,
        val proportion: Double,
        val role: Role,
    )

    enum class Role {
        SIDE, STARCH, SAUCE, TOPPING, FILLING, GARNISH
    }

    // ─── Patrones lingüísticos genéricos ───────────────────────────────────

    data class PatternEntry(val regex: Regex, val type: String)

    private val COMBINATION_PATTERNS = listOf(
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+con\s+([a-záéíóúñü\s]+?)(?:\s+y\s+([a-záéíóúñü\s]+?))?$""", RegexOption.IGNORE_CASE), "con_triple"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+con\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "con_doble"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+de\s+([a-záéíóúñü\s]+?)(?:\s+con\s+([a-záéíóúñü\s]+?))?$""", RegexOption.IGNORE_CASE), "de_con"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "de_doble"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+y\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "y_doble"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+a\s+la\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "a_la"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+al\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "al"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+en\s+salsa\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "en_salsa_de"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+con\s+salsa\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "con_salsa_de"),
        PatternEntry(Regex("""^([a-záéíóúñü\s]+?)\s+rellen[oa]\s+de\s+([a-záéíóúñü\s]+?)$""", RegexOption.IGNORE_CASE), "relleno_de"),
    )

    /** A description with more sandwiches than this has its first ones expanded. */
    private const val MAX_SANDWICH_MENTIONS = 4

    /**
     * The part of [text] that ONE sandwich mention consumes, or null when [text] names no sandwich (WP-N11): the longest known
     * sandwich dish, else "sándwich de" and its fillings (see [sandwichClause]). Whatever the person said before or after it is not
     * part of it. Give this span to [parse]: the whole description would let the sandwich take the mentions that follow it.
     * This is the first of [sandwichMentions].
     */
    fun sandwichMention(text: String): String? = sandwichMentions(text).firstOrNull()

    /**
     * Every sandwich mention of [text], in the order they are said ("un sándwich de jamón y otro de queso" has two, and the second one
     * is also expanded, N11b): each one is a known dish or "sándwich de" and its fillings, and ends where [sandwichMention] says.
     */
    fun sandwichMentions(text: String): List<String> {
        val lower = text.lowercase().trim()
        val mentions = ArrayList<String>()
        var from = 0
        while (from < lower.length && mentions.size < MAX_SANDWICH_MENTIONS) {
            val span = nextSandwichSpan(lower, from) ?: break
            mentions += lower.substring(span.first, span.last + 1)
            from = span.last + 1
        }
        return mentions
    }

    /** The span of the first sandwich mention of [lower] at or after [from]: the known dish or the clause that starts first (the dish when both do). */
    private fun nextSandwichSpan(lower: String, from: Int): IntRange? {
        var known: MatchResult? = null
        var knownName = ""
        for (dish in KNOWN_DISHES.get().sandwiches) {
            val match = dish.regex.find(lower, from) ?: continue
            val start = known?.range?.first
            if (start == null || match.range.first < start || (match.range.first == start && dish.name.length > knownName.length)) {
                known = match
                knownName = dish.name
            }
        }
        val clause = sandwichClause(lower, from)
        return when {
            known != null && (clause == null || known.range.first <= clause.start) -> known.range
            clause != null -> clause.start until clause.end
            else -> null
        }
    }

    /**
     * Parse a food combination string.
     */
    fun parse(text: String): ParsedCombination {
        val lower = text.lowercase().trim()

        // 1. Check known dishes first (highest confidence).
        // Match por palabras completas: "arroz con leche condensada" NO debe matchear
        // "arroz con leche". El plato solo se acepta al final del texto o seguido de un
        // conector ("con/y/e/mas/a/al/de"). Si varios platos matchean, gana el más largo.
        var bestDish: KnownDishes.Dish? = null
        for (dish in KNOWN_DISHES.get().dishes) {
            if (dish.regex.containsMatchIn(lower) && (bestDish == null || dish.name.length > bestDish.name.length)) {
                bestDish = dish
            }
        }

        if (bestDish != null) {
            val components = bestDish.components
            val base = components.first()
            val baseFood = base.food
            val baseProportion = base.proportion
            val accompaniments = components.drop(1).map { comp ->
                Accompaniment(food = comp.food, proportion = comp.proportion, role = comp.role)
            }

            return ParsedCombination(
                baseFood = baseFood,
                baseProportion = baseProportion,
                accompaniments = accompaniments,
                cookingMethod = null,
                isKnownDish = true,
                dishName = bestDish.name,
                confidence = 0.95,
            )
        }

        val sandwich = sandwichClause(lower)
        if (sandwich != null) {
            // The bread is 40 % and the fillings share the rest.
            val share = 0.6 / sandwich.fillings.size
            return ParsedCombination(
                baseFood = "pan",
                baseProportion = 0.4,
                accompaniments = sandwich.fillings.map { Accompaniment(it, share, inferRole(it)) },
                cookingMethod = null,
                isKnownDish = true,
                dishName = "sandwich de " + sandwich.fillings.joinToString(" y "),
                confidence = 0.85,
            )
        }

        // 2. Try generic patterns
        for (entry in COMBINATION_PATTERNS) {
            val match = entry.regex.find(lower) ?: continue

            when (entry.type) {
                "con_triple" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    val c = match.groupValues[3].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.5,
                        accompaniments = listOf(
                            Accompaniment(b, 0.3, inferRole(b)),
                            Accompaniment(c, 0.2, inferRole(c)),
                        ),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
                "con_doble" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.6,
                        accompaniments = listOf(
                            Accompaniment(b, 0.4, inferRole(b)),
                        ),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.75,
                    )
                }
                "de_con" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    val c = match.groupValues[3].trim()
                    val accs = mutableListOf<Accompaniment>(Accompaniment(a, 0.2, Role.SIDE))
                    if (c.isNotBlank()) accs.add(Accompaniment(c, 0.2, inferRole(c)))
                    return ParsedCombination(
                        baseFood = b,
                        baseProportion = 0.6,
                        accompaniments = accs,
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
                "de_doble" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = b,
                        baseProportion = 0.7,
                        accompaniments = listOf(Accompaniment(a, 0.3, Role.SIDE)),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.75,
                    )
                }
                "y_doble" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.5,
                        accompaniments = listOf(Accompaniment(b, 0.5, inferRole(b))),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
                "a_la", "al" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 1.0,
                        accompaniments = emptyList(),
                        cookingMethod = b,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.65,
                    )
                }
                "en_salsa_de", "con_salsa_de" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.7,
                        accompaniments = listOf(Accompaniment("salsa de $b", 0.3, Role.SAUCE)),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.65,
                    )
                }
                "relleno_de" -> {
                    val a = match.groupValues[1].trim()
                    val b = match.groupValues[2].trim()
                    return ParsedCombination(
                        baseFood = a,
                        baseProportion = 0.5,
                        accompaniments = listOf(Accompaniment(b, 0.5, Role.FILLING)),
                        cookingMethod = null,
                        isKnownDish = false,
                        dishName = null,
                        confidence = 0.70,
                    )
                }
            }
        }

        // 3. Fallback: single food
        return ParsedCombination(
            baseFood = lower,
            baseProportion = 1.0,
            accompaniments = emptyList(),
            cookingMethod = null,
            isKnownDish = false,
            dishName = null,
            confidence = 0.50,
        )
    }

    private fun inferRole(food: String): Role {
        val lower = food.lowercase()
        return when {
            lower.contains("arroz") || lower.contains("papa") || lower.contains("patata") || lower.contains("pasta") || lower.contains("fideo") || lower.contains("pure") || lower.contains("pan") || lower.contains("tortilla") -> Role.STARCH
            lower.contains("salsa") || lower.contains("mayonesa") || lower.contains("mayo") || lower.contains("ketchup") || lower.contains("catsup") || lower.contains("mostaza") || lower.contains("crema") || lower.contains("aderezo") || lower.contains("vinagreta") || lower.contains("pesto") || lower.contains("chimichurri") || lower.contains("aceite") || lower.contains("oil") || lower.contains("mantequilla") || lower.contains("margarina") || lower.contains("manteca") || lower.contains("ghee") -> Role.SAUCE
            lower.contains("queso") || lower.contains("huevo") || lower.contains("jamon") || lower.contains("tocino") || lower.contains("salchicha") -> Role.TOPPING
            lower.contains("ensalada") || lower.contains("verdura") || lower.contains("lechuga") || lower.contains("tomate") || lower.contains("cebolla") || lower.contains("zanahoria") || lower.contains("brocoli") || lower.contains("espinaca") -> Role.SIDE
            else -> Role.SIDE
        }
    }

    fun splitFoods(text: String): List<String> {
        val lower = text.lowercase().trim()
        val dishNames = KNOWN_DISHES.get().dishes.map { it.name }
        for (dishName in dishNames) {
            if (lower == dishName) return listOf(dishName)
        }
        val foods = mutableListOf<String>()
        var remaining = lower
        for (dishName in dishNames) {
            if (remaining.contains(dishName)) {
                foods.add(dishName)
                remaining = remaining.replace(dishName, "").trim()
                remaining = CON_Y_COMMA_LEADING_PATTERN.replace(remaining, "").trim()
            }
        }
        if (remaining.isNotBlank()) {
            val parts = remaining.split(COMBO_SPLIT_PATTERN).map { it.trim() }.filter { it.isNotBlank() }
            foods.addAll(parts)
        }
        return foods.ifEmpty { listOf(lower) }
    }
}
