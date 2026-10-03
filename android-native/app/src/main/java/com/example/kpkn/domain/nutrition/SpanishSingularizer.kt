package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.CHILEAN_FOODS
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.GENERIC_FOODS

/**
 * Spanish plural to singular for food words and short food phrases (WP-N3).
 *
 * FoodParser.normalizeFoodName, FoodParser.canonicalTagKey and SmartFoodResolver.singularizeQuery used to
 * cut the tail blindly ("-es" -> drop 2, "-s" -> drop 1), which turned "3 tomates" into "tomat" (a ghost
 * dish of ~560 kcal) and the same for filetes, chocolates, aguacates and cafes. They now delegate here.
 * A plural in "-es" is ambiguous on its own: the singular of "tomates"
 * is "tomate" (the plural adds only "-s") but the singular of "limones" is "limon" (the
 * plural adds "-es" to a consonant). The deciding evidence is the vocabulary, so the rules
 * below ask a [Lexicon] (the static catalog names, aliases and alias keys, built once on
 * first use) and fall back to [CONSONANT_FINAL_LEMMAS] and to a few suffixes that only come
 * from consonant-final words ("-ones", "-ares", ...).
 *
 * - words of three letters or fewer, [INVARIANTS] and "-ss" endings never change;
 * - "-ces" -> "-z" ("nueces" -> "nuez", "peces" -> "pez"), unless only the "-ce" form is known ("dulces");
 * - "-es" -> the candidate that exists in the lexicon (drop "s" first, then drop "es"), else a
 *   consonant-final suffix, else drop "s";
 * - "-s" after an unstressed vowel -> drop it;
 * - "-is", "-us", a stressed vowel before the "s" ("cafes", "ingles") or a consonant before it
 *   ("snacks", "chips") change only when the stem is a known word: "kiwis" -> "kiwi" but "anis", "chips" stay;
 * - phrases: a catalog phrase keeps its plural ("papas fritas"); otherwise the head noun (and its
 *   agreeing words) is singularized only when the result is a catalog phrase ("panes integrales" -> "pan integral"),
 *   except a singular first word with a plural last word, a compound whose last word took the plural
 *   ("coca colas" -> "coca cola").
 *
 * The accent of the input is kept ("limones" -> "limon", "ajies" -> "aji"): every consumer
 * compares accent-free keys. Pure Kotlin / JVM: no Android dependency.
 */
object SpanishSingularizer {

    /**
     * Words the "-es" decision consults, accent- and case-insensitive. [words] are single
     * tokens, [phrases] whole multi-word names. Injectable so tests need no catalog.
     */
    class Lexicon(words: Iterable<String> = emptyList(), phrases: Iterable<String> = emptyList()) {
        private val wordKeys: Set<String> = words.mapNotNullTo(HashSet<String>()) { keyOf(it) }
        private val phraseKeys: Set<String> = phrases.mapNotNullTo(HashSet<String>()) { keyOf(it) }

        fun hasWord(word: String): Boolean = keyOf(word)?.let { it in wordKeys } ?: false

        fun hasPhrase(phrase: String): Boolean = keyOf(phrase)?.let { it in phraseKeys } ?: false

        /**
         * This vocabulary plus [entries]: an entry with spaces is a phrase, any entry adds its words.
         * The parser adds the dishes it protects from splitting ("empanadas de pino") this way.
         */
        fun withPhrases(entries: Iterable<String>): Lexicon {
            val keys = entries.mapNotNull { keyOf(it) }
            return Lexicon(
                words = wordKeys + keys.flatMap { it.split(' ') }.filter { it.length >= 2 },
                phrases = phraseKeys + keys.filter { ' ' in it },
            )
        }
    }

    private fun keyOf(text: String): String? = TextKeys.normalize(text).takeIf { it.isNotEmpty() }

    /** Food words whose singular ends in a consonant: their plural is "-es" ("limones", "panes", "yogures"). */
    val CONSONANT_FINAL_LEMMAS: Set<String> = setOf(
        "pan", "limón", "jamón", "melón", "salmón", "atún", "camarón", "champiñón", "pimentón",
        "calamar", "yogur", "maní", "pastel", "ají",
        "tamal", "cereal", "sándwich", "azúcar", "licor", "alfajor", "raviol", "frijol", "caracol",
        "canelón", "macarrón", "tallarín", "mazapán", "flan", "bombón", "turrón", "mejillón",
        "bol", "col", "sal", "miel",
    )

    /** Words that look plural but are not (or whose plural is the same word). Accent-free keys. */
    private val INVARIANTS: Set<String> = setOf(
        "lunes", "martes", "miercoles", "jueves", "viernes",
        "crisis", "chips", "nuggets", "cuscus", "anis", "tenis", "dosis", "tesis", "biceps", "triceps",
        "dos", "tres", "seis", "menos", "antes", "entonces", "pues",
    )

    /** Plural endings whose singular can only end in a consonant, used when the lexicon knows neither candidate. */
    private val CONSONANT_PLURAL_SUFFIXES = listOf("ones", "ares", "ores", "ures", "anes", "ines", "unes", "ales", "eles")

    /** A number word opens a count ("tres leches"), not a compound whose first word is a singular noun. */
    private val NUMBER_WORDS = setOf(
        "un", "una", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once",
        "doce", "trece", "catorce", "quince", "veinte", "treinta", "cuarenta", "cincuenta", "cien", "ciento", "mil",
        "medio", "media",
    )

    /** The first of these ends the head noun phrase: what follows stays as typed ("galletas de avena"). */
    private val PREPOSITIONS = setOf("de", "del", "con", "sin", "a", "al", "en", "para", "y", "e", "o")

    private const val MIN_WORD_LENGTH = 4
    private const val MIN_STEM_LENGTH = 3
    private const val VOWELS = "aeiou"
    private const val STRESSED_VOWELS = "áéíóú"

    private val CONSONANT_FINAL_KEYS: Set<String> by lazy {
        CONSONANT_FINAL_LEMMAS.mapTo(HashSet<String>()) { TextKeys.normalize(it) }
    }

    /** Vocabulary of the static catalog (names, search aliases, alias keys and targets) plus the common roots. */
    val defaultLexicon: Lexicon by lazy(LazyThreadSafetyMode.PUBLICATION) { buildCatalogLexicon() }

    private fun buildCatalogLexicon(): Lexicon {
        val entries = ArrayList<String>()
        for (food in GENERIC_FOODS + CHILEAN_FOODS) {
            entries += food.name
            // "Pechuga de Pollo (cruda)" is also the phrase "pechuga de pollo" for a person who omits the state.
            entries += food.name.substringBefore('(')
            entries += food.searchAliases
        }
        for ((alias, target) in FOOD_ALIASES) {
            entries += alias
            entries += target
        }
        entries += TextNormalizer.COMMON_FOOD_ROOTS
        entries += CONSONANT_FINAL_LEMMAS
        // Dishes named with a number word ("tres leches", "mil hojas") are plural names, not counts of a singular.
        entries += TextNormalizer.numberWordFoodNames
        return Lexicon().withPhrases(entries)
    }

    /**
     * Singular of [text]: one word or a short phrase. Returns [text] unchanged when there is
     * nothing to singularize or no safe singular.
     */
    fun singularize(text: String, lexicon: Lexicon = defaultLexicon): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text
        if (' ' !in trimmed) return singularizeWord(trimmed, lexicon)
        // A catalog name keeps its plural: "frutos secos", "papas fritas".
        if (lexicon.hasPhrase(trimmed)) return trimmed
        val tokens = trimmed.split(' ')
        val headEnd = tokens.indexOfFirst { it.lowercase() in PREPOSITIONS }.let { if (it > 0) it else tokens.size }
        val everyWord = tokens.joinToString(" ") { singularizeWord(it, lexicon) }
        val headPhrase = tokens.withIndex().joinToString(" ") { (index, token) ->
            if (index < headEnd) singularizeWord(token, lexicon) else token
        }
        for (candidate in listOf(headPhrase, everyWord)) {
            if (candidate != trimmed && lexicon.hasPhrase(candidate)) return candidate
        }
        // A singular first word followed by a plural last word is a compound whose last word took the plural
        // ("coca colas", "pisco sours"): there is no agreement to protect, so only that word changes.
        if (headEnd == tokens.size && tokens.first().lowercase() !in NUMBER_WORDS &&
            singularizeWord(tokens.first(), lexicon) == tokens.first()
        ) {
            val last = tokens.last()
            val lastSingular = singularizeWord(last, lexicon)
            if (lastSingular != last) return (tokens.dropLast(1) + lastSingular).joinToString(" ")
        }
        return trimmed
    }

    /** Singular of one word; the accent and case of the input are kept. */
    fun singularizeWord(word: String, lexicon: Lexicon = defaultLexicon): String {
        if (word.length < MIN_WORD_LENGTH) return word
        val key = TextKeys.normalize(word)
        if (key.length < MIN_WORD_LENGTH || !key.all { it in 'a'..'z' } || !key.endsWith('s')) return word
        if (key in INVARIANTS || key.endsWith("ss")) return word
        val stem = word.dropLast(1)
        return when {
            // "cafes"/"pures" (plural) against "ingles"/"ananas" (singular): only a known stem decides.
            word[word.length - 2].lowercaseChar() in STRESSED_VOWELS || key.endsWith("is") || key.endsWith("us") ->
                stem.takeIf { isKnown(it, lexicon) } ?: word
            key.endsWith("ces") -> singularizeCes(word, lexicon)
            key.endsWith("es") -> singularizeEs(word, key, lexicon)
            key[key.length - 2] in VOWELS -> stem
            // Consonant + "s" ("snacks", "chips") is a foreign plural: change it only when the stem is a known word.
            else -> stem.takeIf { isKnown(it, lexicon) } ?: word
        }
    }

    private fun singularizeCes(word: String, lexicon: Lexicon): String {
        val zForm = word.dropLast(3) + if (word[word.length - 3].isUpperCase()) "Z" else "z"
        val ceForm = word.dropLast(1)
        // "nueces" -> "nuez" but "dulces" -> "dulce": the -ce form wins only when it is the known one.
        return if (isKnown(ceForm, lexicon) && !isKnown(zForm, lexicon)) ceForm else zForm
    }

    private fun singularizeEs(word: String, key: String, lexicon: Lexicon): String {
        val dropS = word.dropLast(1)
        val dropEs = word.dropLast(2)
        return when {
            isKnown(dropS, lexicon) -> dropS
            isKnown(dropEs, lexicon) -> dropEs
            dropEs.length >= MIN_STEM_LENGTH && CONSONANT_PLURAL_SUFFIXES.any { key.endsWith(it) } -> dropEs
            else -> dropS
        }
    }

    private fun isKnown(candidate: String, lexicon: Lexicon): Boolean {
        if (candidate.length < MIN_STEM_LENGTH) return false
        return TextKeys.normalize(candidate) in CONSONANT_FINAL_KEYS || lexicon.hasWord(candidate)
    }
}
