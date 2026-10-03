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
 * The raw lists are the `protectedPhrases` section of [FoodKnowledge] (WP-N13). Everything this object derives from them (spellings,
 * sets, regexes) is built once per snapshot ([Index]) and built again when an install changes the lists.
 *
 * Pure Kotlin / JVM: no Android dependency.
 */
object ProtectedPhrases {

    // ─── The lists ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Dishes and drinks the parser keeps whole: they are named with "con" or "y", and cutting them at the connector leaves foods
     * nobody ate ("riendas", "queso"). A plural entry stays in the plural for the singularizer, like a catalog name does
     * ("empanadas de pino", "pasteles de choclo"), and the dishes named with a number word ("tres leches") belong here too.
     */
    val PROTECTED_ENTITIES: List<String> get() = index.entities

    /**
     * Names that read as two foods and are one. "con gas" and "sin gas" qualify the water (there is no food called gas), an
     * "ave palta" is the chicken-and-avocado sandwich of a Chilean counter, and a product made of maize keeps its "maíz" (the
     * synonym of [TextNormalizer] would turn it into "tortilla de choclo", a dish nobody sells).
     */
    val COMPOUND_NAMES: List<String> get() = index.compoundNames

    /** Every ordered pair of the flavours ("vainilla y chocolate"); protected only after a flavoured food and its "de". */
    val FLAVOR_PAIRS: List<String> get() = index.flavorPairs

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
    val lexiconEntries: List<String> get() = index.lexiconEntries

    /** True when [text] is exactly a dish of [PROTECTED_ENTITIES] or [COMPOUND_NAMES] (case ignored, with or without accents). */
    fun isEntity(text: String): Boolean = text.trim().lowercase() in index.entitySet

    /** Same as [isEntity] but also across accents, case and punctuation ("Café con  leche" is the entity "café con leche"). */
    fun isEntityKey(text: String): Boolean = TextKeys.normalize(text) in index.entityKeys

    /** True when [text] is exactly a protected phrase of any kind in any of its spellings (case ignored). */
    fun isPhrase(text: String): Boolean = text.trim().lowercase() in index.phraseSet

    /** True when [text] holds a protected phrase (not a flavour pair: those only matter where a mention is cut). */
    fun containsPhrase(text: String): Boolean = index.phraseRegex.containsMatchIn(text)

    /** The words that join the parts of a name: "empanada de pino", "agua con gas", "pollo a la plancha". */
    private val NAME_CONNECTORS = setOf("de", "del", "con", "y", "sin", "a", "al")

    /**
     * True when [text] holds a protected name with a connector inside ("empanada de pino", "agua con gas"): reading an amount out
     * of it would cut the name at its "de" and leave "pino" as the food. A name with no connector ("papas fritas", "porotos
     * negros") is safe to read an amount from: "una bolsa de papas fritas" is a bag of them.
     */
    fun containsConnectedPhrase(text: String): Boolean = index.connectedRegex.containsMatchIn(text)

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
        val current = index
        val flavoured = current.flavorPairRegex.replace(text, replace)
        return Masked(current.phraseRegex.replace(flavoured, replace), originals, token)
    }

    // ─── Derived structures ────────────────────────────────────────────────────────────────────────────────────────

    /** Everything derived from ONE snapshot of the lists, each part built on first use. */
    private class Index(private val lists: ProtectedPhrasesKnowledge) {
        val entities: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            lists.entityLiterals + TextNormalizer.numberWordFoodNames
        }

        val compoundNames: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            buildList {
                addAll(lists.fixedCompoundNames)
                for (head in lists.filledHeads) {
                    add("$head de jamón y queso")
                    add("$head de queso y jamón")
                }
                for (product in lists.maizeProducts) add("$product de maíz")
            }
        }

        val flavorPairs: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            lists.flavors.flatMap { first -> lists.flavors.filter { it != first }.map { second -> "$first y $second" } }
        }

        val lexiconEntries: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { entities + compoundNames }

        /** Dishes of [PROTECTED_ENTITIES] and [COMPOUND_NAMES], as typed and without accents (lower case). */
        val entitySet: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            lexiconEntries.flatMapTo(HashSet()) { listOf(it.lowercase(), stripAccents(it.lowercase())) }
        }

        val entityKeys: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            lexiconEntries.mapTo(HashSet()) { TextKeys.normalize(it) }
        }

        /** Every protected phrase in every spelling, longest first so that the longest phrase wins at a position. */
        val phraseSpellings: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            (lexiconEntries + staticFoodPhrases() + lists.catalogExtraPhrases)
                .flatMap { spellings(it) }
                .distinct()
                .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        }

        val phraseSet: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { phraseSpellings.toHashSet() }

        /** Catalog names such as "castañas de cajú" or "ñame (cocido)" start or end in an accented letter: the edges come from [RegexEs]. */
        val phraseRegex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            Regex(RegexEs.bounded(phraseSpellings.joinToString("|") { Regex.escape(it) }), RegexOption.IGNORE_CASE)
        }

        /** A food that comes in flavours with its flavour pair ("helado de vainilla y chocolate"): "vainilla y chocolate" alone is a list. */
        val flavorPairRegex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            fun alternation(words: List<String>): String =
                words.flatMap { listOf(it, stripAccents(it)) }.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            val heads = alternation(lists.flavoredHeads)
            val pairs = alternation(flavorPairs)
            Regex(RegexEs.bounded("(?:$heads)" + """\s+de\s+""" + "(?:$pairs)"), RegexOption.IGNORE_CASE)
        }

        /** Only the protected phrases that hold a connector: the ones a reader of amounts would cut in the wrong place. */
        val connectedRegex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val connected = phraseSpellings.filter { spelling -> spelling.split(' ').any { it in NAME_CONNECTORS } }
            Regex(RegexEs.bounded(connected.joinToString("|") { Regex.escape(it) }), RegexOption.IGNORE_CASE)
        }
    }

    private val INDEX = KnowledgeCache { snapshot -> Index(snapshot.protectedPhrases) }

    private val index: Index get() = INDEX.get()
}
