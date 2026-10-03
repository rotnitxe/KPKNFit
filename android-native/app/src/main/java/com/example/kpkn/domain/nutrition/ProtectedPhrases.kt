package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.staticFoodPhrases
import java.text.Normalizer

/**
 * The one list of multi-word food phrases that no pass may rewrite or cut (WP-N9; audit findings A-P3 and A-P5).
 *
 * Three passes need the same list. The typo pass of [TextNormalizer] must not "correct" the words of a dish ("porotos con riendas"
 * is not "poroto con riendas"); [parseMealDescription] must not cut a dish at its "con" or its "y" ("agua con gas" is one drink,
 * not water plus a food called gas); and the amount and modifier readers must see a dish name as a whole. Each of them kept its own
 * copy of the list, and the typo pass ran before the others, so the copies never met a plural. Here they are one:
 *
 * - [PROTECTED_ENTITIES]: dishes and drinks named with a connector (they lived in FoodParser);
 * - [COMPOUND_NAMES]: names that read as two foods and are one (a carbonated water, a sandwich named after its filling, a product
 *   made of maize);
 * - [FLAVOR_PAIRS]: two flavours of a food that comes in flavours ("helado de vainilla y chocolate");
 * - the multi-word names and aliases of the static catalog, and "salsa de tomate".
 *
 * Every phrase is protected in each spelling a person writes it in: with and without accents, with the head noun in the plural
 * ("cafés con leche", "panes con palta", "sándwiches de jamón y queso") and, for a phrase named in the plural, in the singular.
 *
 * Pure Kotlin / JVM: no Android dependency.
 */
object ProtectedPhrases {

    // ─── The lists ─────────────────────────────────────────────────────────────────────────────────────────────────

    private val ENTITY_LITERALS = listOf(
        "arroz con leche",
        "pastel de choclo", "pasteles de choclo",
        "empanada de pino", "empanadas de pino",
        "empanada de queso", "empanadas de queso",
        "porotos con riendas",
        "cafe con leche", "café con leche",
        "te con leche", "té con leche",
        "leche con chocolate",
        "leche con platano", "leche con plátano",
        "sandwich de pollo con mayonesa", "sandwich de jamon con mayonesa",
        "sándwich de pollo con mayonesa", "sándwich de jamón con mayonesa",
        "sandwich de jamon y queso", "sandwich de jamón y queso",
        "sándwich de jamon y queso", "sándwich de jamón y queso",
        "hamburguesa con queso", "hamburguesas con queso",
        "papas fritas con mayonesa", "papa fritas con mayonesa",
        "papas con mayo",
    )

    /**
     * Dishes and drinks the parser keeps whole: they are named with "con" or "y", and cutting them at the connector leaves foods
     * nobody ate ("riendas", "queso"). A plural entry stays in the plural for the singularizer, like a catalog name does
     * ("empanadas de pino", "pasteles de choclo"), and the dishes named with a number word ("tres leches") belong here too.
     */
    val PROTECTED_ENTITIES: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ENTITY_LITERALS + TextNormalizer.numberWordFoodNames
    }

    /** Sandwiches, pastries and snacks named after a filling of two foods: "empanada de jamón y queso" is one thing. */
    private val FILLED_HEADS = listOf(
        "sándwich", "sanguche", "emparedado", "tostada", "tostadita", "empanada", "croissant", "wrap", "quesadilla", "calzone", "pizza",
    )

    /** Foods made of maize: the "maíz" of "tortilla de maíz" is a material, it is not the choclo of the vegetable stall. */
    private val MAIZE_PRODUCTS = listOf(
        "tortilla", "harina", "aceite", "almidón", "fécula", "sémola", "pan", "galleta", "palomitas", "cereal", "hojuelas", "jarabe",
        "snack", "nachos", "tostadas", "chips",
    )

    /**
     * Names that read as two foods and are one. "con gas" and "sin gas" qualify the water (there is no food called gas), an
     * "ave palta" is the chicken-and-avocado sandwich of a Chilean counter, and a product made of maize keeps its "maíz" (the
     * synonym of [TextNormalizer] would turn it into "tortilla de choclo", a dish nobody sells).
     */
    val COMPOUND_NAMES: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildList {
            addAll(listOf("agua con gas", "agua sin gas", "agua mineral con gas", "agua mineral sin gas", "ave palta", "ave mayo"))
            for (head in FILLED_HEADS) {
                add("$head de jamón y queso")
                add("$head de queso y jamón")
            }
            for (product in MAIZE_PRODUCTS) add("$product de maíz")
        }
    }

    /** Flavours that come in pairs: a scoop, a cake or a yogurt "de vainilla y chocolate" is one food with two flavours. */
    private val FLAVORS = listOf(
        "vainilla", "chocolate", "frutilla", "lúcuma", "manjar", "menta", "frambuesa", "mora", "coco", "caramelo", "avellana",
        "pistacho", "limón", "maracuyá",
    )

    /**
     * Foods that come in flavours: after one of them, "de vainilla y chocolate" is one food with two flavours. A drink or a juice is
     * left alone ("jugo de naranja y plátano" may well be a juice and a banana, and its estimate would be read from the banana).
     */
    private val FLAVORED_HEADS = listOf(
        "helado", "helados", "torta", "tortas", "pastel", "pasteles", "queque", "queques", "keke", "kekes", "kuchen", "pie", "pies",
        "mousse", "flan", "flanes", "budín", "budines", "yogur", "yogures", "yogurt", "yogurts", "galleta", "galletas", "alfajor",
        "alfajores", "panqueque", "panqueques", "crepe", "crepes", "cheesecake", "cupcake", "cupcakes", "muffin", "muffins", "brownie",
        "brownies", "bizcocho", "bizcochos", "bombón", "bombones", "paleta", "paletas", "sorbete", "sorbetes", "trufa", "trufas",
        "turrón", "turrones", "barra", "barras", "crema", "cremas",
    )

    /** Every ordered pair of [FLAVORS] ("vainilla y chocolate"); protected only after a [FLAVORED_HEADS] food and its "de". */
    val FLAVOR_PAIRS: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        FLAVORS.flatMap { first -> FLAVORS.filter { it != first }.map { second -> "$first y $second" } }
    }

    /** A catalog food that is not a row of the static catalog (it is a sauce), protected like one. */
    private const val SAUCE_PHRASE = "salsa de tomate"

    // ─── Spellings ─────────────────────────────────────────────────────────────────────────────────────────────────

    private val WHITESPACE = Regex("""\s+""")

    /** Longest text masked: a meal description is a few lines; a longer one is left as it is rather than scanned for every phrase. */
    private const val MAX_MASK_CHARS = 10_000

    /** The word after the head noun that makes a phrase "head noun + complement": "papas con mayo", "pastel de choclo". */
    private val HEAD_CONNECTORS = setOf("con", "de", "y", "e", "sin", "a", "al")

    private val ACCENTED_PLURALS = mapOf("ón" to "ones", "án" to "anes", "én" to "enes", "ín" to "ines", "ún" to "unes")

    private fun stripAccents(text: String): String =
        if (text.all { it.code < 128 }) text else Normalizer.normalize(text, Normalizer.Form.NFD).replace(TextKeys.MARKS, "")

    /** Plural of a singular noun ("pan" -> "panes", "café" -> "cafés", "arroz" -> "arroces", "limón" -> "limones"); null when it is plural already. */
    private fun pluralOf(word: String): String? {
        val lower = word.lowercase()
        if (lower.length < 2 || lower.endsWith("s")) return null
        ACCENTED_PLURALS.entries.firstOrNull { lower.endsWith(it.key) }?.let { return lower.dropLast(it.key.length) + it.value }
        return when {
            lower.endsWith("z") -> lower.dropLast(1) + "ces"
            lower.last() in "aeiouáéíóú" -> lower + "s"
            else -> lower + "es"
        }
    }

    /** Singular of a plural noun ("porotos" -> "poroto"); null when the word is singular already or has no safe singular. */
    private fun singularOf(word: String): String? =
        SpanishSingularizer.singularizeWord(word.lowercase()).takeIf { it != word.lowercase() }

    /**
     * Every spelling of [phrase] that a person writes: as given, without accents and, when the phrase is a head noun followed
     * by a connector ("pan con palta", "pastel de choclo"), with the head noun in the plural and in the singular. Lower case.
     */
    fun spellings(phrase: String): List<String> {
        val base = phrase.trim().lowercase().replace(WHITESPACE, " ")
        if (base.isEmpty()) return emptyList()
        val forms = arrayListOf(base)
        val tokens = base.split(' ')
        if (tokens.size >= 3 && tokens[1] in HEAD_CONNECTORS) {
            val rest = tokens.drop(1).joinToString(" ")
            pluralOf(tokens[0])?.let { forms += "$it $rest" }
            singularOf(tokens[0])?.let { forms += "$it $rest" }
        }
        return forms.flatMap { listOf(it, stripAccents(it)) }.distinct()
    }

    // ─── Lookups ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** Canonical forms the singularizer keeps whole ("empanadas de pino" stays plural, like a catalog name). */
    val lexiconEntries: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { PROTECTED_ENTITIES + COMPOUND_NAMES }

    /** Dishes of [PROTECTED_ENTITIES] and [COMPOUND_NAMES], as typed and without accents (lower case). */
    private val ENTITY_SET: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        lexiconEntries.flatMapTo(HashSet()) { listOf(it.lowercase(), stripAccents(it.lowercase())) }
    }

    private val ENTITY_KEYS: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        lexiconEntries.mapTo(HashSet()) { TextKeys.normalize(it) }
    }

    /** Every protected phrase in every spelling, longest first so that the longest phrase wins at a position. */
    private val PHRASE_SPELLINGS: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        (lexiconEntries + staticFoodPhrases() + SAUCE_PHRASE)
            .flatMap { spellings(it) }
            .distinct()
            .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }

    private val PHRASE_SET: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { PHRASE_SPELLINGS.toHashSet() }

    /** Catalog names such as "castañas de cajú" or "ñame (cocido)" start or end in an accented letter: the edges come from [RegexEs]. */
    private val PHRASE_REGEX: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Regex(RegexEs.bounded(PHRASE_SPELLINGS.joinToString("|") { Regex.escape(it) }), RegexOption.IGNORE_CASE)
    }

    /** A food that comes in flavours with its flavour pair ("helado de vainilla y chocolate"): "vainilla y chocolate" alone is a list. */
    private val FLAVOR_PAIR_REGEX: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        fun alternation(words: List<String>): String =
            words.flatMap { listOf(it, stripAccents(it)) }.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val heads = alternation(FLAVORED_HEADS)
        val pairs = alternation(FLAVOR_PAIRS)
        Regex(RegexEs.bounded("(?:$heads)" + """\s+de\s+""" + "(?:$pairs)"), RegexOption.IGNORE_CASE)
    }

    /** True when [text] is exactly a dish of [PROTECTED_ENTITIES] or [COMPOUND_NAMES] (case ignored, with or without accents). */
    fun isEntity(text: String): Boolean = text.trim().lowercase() in ENTITY_SET

    /** Same as [isEntity] but also across accents, case and punctuation ("Café con  leche" is the entity "café con leche"). */
    fun isEntityKey(text: String): Boolean = TextKeys.normalize(text) in ENTITY_KEYS

    /** True when [text] is exactly a protected phrase of any kind in any of its spellings (case ignored). */
    fun isPhrase(text: String): Boolean = text.trim().lowercase() in PHRASE_SET

    /** True when [text] holds a protected phrase (not a flavour pair: those only matter where a mention is cut). */
    fun containsPhrase(text: String): Boolean = PHRASE_REGEX.containsMatchIn(text)

    /** The words that join the parts of a name: "empanada de pino", "agua con gas", "pollo a la plancha". */
    private val NAME_CONNECTORS = setOf("de", "del", "con", "y", "sin", "a", "al")

    /** Only the protected phrases that hold a connector: the ones a reader of amounts would cut in the wrong place. */
    private val CONNECTED_REGEX: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val connected = PHRASE_SPELLINGS.filter { spelling -> spelling.split(' ').any { it in NAME_CONNECTORS } }
        Regex(RegexEs.bounded(connected.joinToString("|") { Regex.escape(it) }), RegexOption.IGNORE_CASE)
    }

    /**
     * True when [text] holds a protected name with a connector inside ("empanada de pino", "agua con gas"): reading an amount out
     * of it would cut the name at its "de" and leave "pino" as the food. A name with no connector ("papas fritas", "porotos
     * negros") is safe to read an amount from: "una bolsa de papas fritas" is a bag of them.
     */
    fun containsConnectedPhrase(text: String): Boolean = CONNECTED_REGEX.containsMatchIn(text)

    // ─── Masking ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** [text] with every protected phrase replaced by a token, and the phrases in the order of their tokens. */
    class Masked(val text: String, val originals: List<String>, private val token: (Int) -> String) {
        /** Puts the phrases back in [rewritten], a text whose tokens a pass has left alone. */
        fun restore(rewritten: String): String {
            var result = rewritten
            originals.forEachIndexed { index, original -> result = result.replace(token(index), original) }
            return result
        }
    }

    /**
     * Replaces each protected phrase of [text] (and each flavoured food with its two flavours) with `token(n)`, n counting from 0. A
     * pass that rewrites words, or cuts at connectors, runs on [Masked.text] and calls [Masked.restore] when it is done.
     */
    fun mask(text: String, token: (Int) -> String): Masked {
        if (text.isEmpty() || text.length > MAX_MASK_CHARS) return Masked(text, emptyList(), token)
        val originals = ArrayList<String>()
        val replace = { match: MatchResult ->
            originals += match.value
            token(originals.size - 1)
        }
        val flavoured = FLAVOR_PAIR_REGEX.replace(text, replace)
        return Masked(PHRASE_REGEX.replace(flavoured, replace), originals, token)
    }
}
