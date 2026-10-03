package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.SearchConfidence
import com.example.kpkn.data.models.SearchSource
import kotlin.math.ln

/**
 * Ranking of the Search tab (WP-S2 / B2). Pure Kotlin: the repository gathers the candidate pool (static catalog, custom
 * foods, rows the DAO retrieved) and this object decides what the person sees and in which order.
 *
 * What the old scorer got wrong, and what replaces it:
 *  - "Substring" hits ("pan" found empanaditas, biopan, panchitos...). A query token now hits a name/alias/brand word only
 *    when it is the same word (equal, or equal after Spanish plural folding, see [stem]) or, for tokens of 4+ letters,
 *    the start of that word. `contains` is never used.
 *  - Inverted source priors (curated rows scored 0.6, OFF rows 0.65-0.9 and always won). A curated row now starts ahead of a
 *    supermarket SKU with the same match quality, see [sourcePrior].
 *  - The verified/identity filter ran AFTER `take(limit)`, so a screen of 15 results could end up with 3 usable rows.
 *    [rank] applies it BEFORE the limit.
 *
 * Score = coverage*0.32 + precision*0.14 + match tier + source prior + anchor + brand + penalties + usage + learned:
 *  - coverage: share of the query's content tokens that hit; precision: hits over the content tokens of the name without its
 *    state words ("(cruda)").
 *  - match tier (one of): exact name/alias 0.45 / name or alias that STARTS with the query phrase 0.20 / every token hits and
 *    the name has no other content word 0.15. An OFF row only reaches the exact tier when the query names its brand: an OFF
 *    name is free text, not a canonical identity ("Yogurt" by Colun is a product, not THE yogurt).
 *  - anchor 0.30: the food the app itself resolves the query to (`HouseholdPortions.householdStaticFood`), only when it is
 *    the same food the person typed ("galletas" resolving to Pan Blanco is an approximation and gets nothing).
 *  - brand +0.15 when the query names the row's brand; OFF row and no brand named -0.05; pack-sized name -0.10; the query
 *    declares a state (raw/cooked...) and the row has another -0.20.
 *  - usage ln(uses+1)/ln(10)*0.08 and the learned selection for this query +0.22.
 * Ties break by shorter normalized name, then foodId, so the order is total and stable across runs and devices.
 */
object FoodSearchRanker {

    /**
     * A parsed search request. [tokens] are the distinct content tokens (no stop words) of the normalized text and
     * [stems] their [stem], same order. [state] is the cooking/hydration state the person declared, if any.
     * [anchorId] is the household default food for the query, or null.
     */
    data class Query(
        val raw: String,
        val normalized: String,
        val tokens: List<String>,
        val stems: List<String>,
        val state: FoodState,
        val anchorId: String?,
    ) {
        /** Each token with only its trailing "s" dropped (see [dropS]); same order as [tokens]. */
        internal val withoutS: List<String> = tokens.map(::dropS)

        /** The query itself names a pack ("1kg", "pack"): a pack-sized row is then what was asked for. */
        internal val namesPack: Boolean = HouseholdPortions.looksLikePackName(normalized)

        /**
         * Tokens a row has to hit to be a candidate at all. A token that only states how the food is prepared ("cruda",
         * "fritos") qualifies the food, it does not identify it: "Espinaca (cruda)" is not an answer to "pechuga de pollo
         * cruda". A query made only of such words is the exception: then every token counts.
         */
        internal val identifying: List<Boolean> = tokens.map { it !in STATE_WORDS }.let { flags ->
            if (flags.none { it }) flags.map { true } else flags
        }

        /**
         * Tokens `FoodIdentity.matchesDeclaredIdentity` needs in a row (all but numbers, states, units and "sin"): a row
         * that does not carry them cannot pass the identity rules, which makes this the cheap check to run first.
         */
        internal val required: List<Boolean> = FoodIdentity.requiredTokens(normalized).toSet().let { needed -> tokens.map { it in needed } }
    }

    /** Parses [raw] into a [Query]. [anchorId] comes from the caller (it needs the static catalog). */
    fun query(raw: String, anchorId: String?): Query {
        val normalized = TextKeys.normalize(raw)
        val tokens = contentTokens(normalized).distinct()
        return Query(
            raw = raw,
            normalized = normalized,
            tokens = tokens,
            stems = tokens.map(::stem),
            state = if (normalized.isEmpty()) FoodState.UNKNOWN else FoodIdentity.stateFor(normalized),
            anchorId = anchorId,
        )
    }

    /**
     * Scores one row for [q], or returns null when no query token hits it. [learnedFoodId] is the food the person
     * previously picked for this exact query.
     */
    fun score(food: FoodItem, q: Query, learnedFoodId: String?): FoodCandidate? = scoreRow(food, q, learnedFoodId)?.candidate

    private fun scoreRow(food: FoodItem, q: Query, learnedFoodId: String?): Scored? {
        if (q.tokens.isEmpty()) return null
        val nameKey = key(food.normalizedName ?: food.name)
        val nameWords = contentTokens(nameKey)
        val aliasWordLists = food.searchAliases
            .map { contentTokens(key(it)) }
            .filter { it.isNotEmpty() && it != nameWords }
        val brandPhrases = brandPhrases(food)

        // 1) Which query tokens hit some word of the name, an alias or the brand.
        val hit = BooleanArray(q.tokens.size)
        var hits = 0
        fun markHits(words: List<String>) {
            for (word in words) {
                val wordStem = stem(word)
                val wordWithoutS = dropS(word)
                for (i in q.tokens.indices) {
                    if (!hit[i] && wordHit(q, i, word, wordStem, wordWithoutS)) {
                        hit[i] = true
                        hits++
                    }
                }
            }
        }
        markHits(nameWords)
        aliasWordLists.forEach(::markHits)
        brandPhrases.forEach(::markHits)
        if (q.tokens.indices.none { hit[it] && q.identifying[it] }) return null

        val coverage = hits.toDouble() / q.tokens.size
        val statesInName = parenthesizedStateWords(food.name)
        val coreWords = if (statesInName.isEmpty()) nameWords else nameWords.filter { it !in statesInName }
        val precision = (hits.toDouble() / coreWords.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)

        // 2) How the name relates to the whole query phrase.
        val brandNamed = brandPhrases.any { phrase -> phrase.all { word -> namesWord(q, word) } }
        val isOff = isOffRow(food)
        val exact = sequenceMatches(q, nameWords, wholeName = true) || aliasWordLists.any { sequenceMatches(q, it, wholeName = true) }
        val starts = exact || sequenceMatches(q, nameWords, wholeName = false) || aliasWordLists.any { sequenceMatches(q, it, wholeName = false) }
        val (matchBoost, matchTrace) = when {
            exact && !(isOff && !brandNamed) -> EXACT_BOOST to "exact"
            starts -> PHRASE_START_BOOST to "prefix"
            hits == q.tokens.size && precision >= 1.0 -> PLAIN_FOOD_BOOST to "plain"
            else -> 0.0 to null
        }

        // 3) Everything else that is not about the words.
        var score = coverage * COVERAGE_WEIGHT + precision * PRECISION_WEIGHT + matchBoost
        val trace = ArrayList<String>(6)
        matchTrace?.let(trace::add)

        score += sourcePrior(food)
        if (q.anchorId != null && food.id == q.anchorId && (hit[0] || sameFamily(q, food))) {
            score += ANCHOR_BOOST
            trace.add("anchor")
        }
        if (brandNamed) {
            score += BRAND_NAMED_BOOST
            trace.add("brand")
        } else if (isOff) {
            score += OFF_UNBRANDED_QUERY_PENALTY
        }
        if (!q.namesPack && mightBePack(nameKey) && HouseholdPortions.looksLikePackName(nameKey)) {
            score += PACK_PENALTY
            trace.add("pack")
        }
        if (q.state != FoodState.UNKNOWN && FoodIdentity.stateFor(food) != q.state) {
            score += STATE_PENALTY
            trace.add("state")
        }
        score += (ln((food.usageCount.coerceAtLeast(0) + 1).toDouble()) / ln(10.0)).coerceIn(0.0, 1.0) * USAGE_WEIGHT
        val learned = learnedFoodId != null && learnedFoodId == food.id
        if (learned) {
            score += LEARNED_BOOST
            trace.add("learned")
        }

        val candidate = FoodCandidate(
            foodId = food.id,
            displayName = food.name,
            score = score,
            confidence = when {
                score >= 0.82 -> SearchConfidence.HIGH
                score >= 0.58 -> SearchConfidence.MEDIUM
                else -> SearchConfidence.LOW
            },
            source = searchSource(food),
            food = food,
            trace = trace,
            queryCoverage = coverage,
            tokenPrecision = precision,
            brandMatched = brandNamed,
            learned = learned,
        )
        return Scored(candidate, nameKey.length, q.tokens.indices.all { !q.required[it] || hit[it] })
    }

    /**
     * The best [limit] rows of [pool] for [q]. With [loggerFilter] only rows the logger may accept are kept (verified
     * nutrients AND the identity the person declared, `FoodIdentity.matchesDeclaredIdentity`), and the filter runs BEFORE
     * the limit, so a screen is never left with fewer rows than exist because rejects took its places. The identity rules
     * are the expensive part: a row that lacks a word they require cannot pass, so it is skipped without running them.
     */
    fun rank(
        q: Query,
        pool: Collection<FoodItem>,
        learnedFoodId: String?,
        limit: Int,
        loggerFilter: Boolean,
    ): List<FoodCandidate> {
        if (limit <= 0 || q.tokens.isEmpty()) return emptyList()
        val scored = ArrayList<Scored>()
        for (food in pool) scoreRow(food, q, learnedFoodId)?.let { scored.add(it) }
        scored.sortWith(ORDER)
        val result = ArrayList<FoodCandidate>(minOf(limit, scored.size))
        val seen = HashSet<String>()
        for (entry in scored) {
            val candidate = entry.candidate
            val food = candidate.food
            if (!seen.add(food.id.ifBlank { "${food.name}|${food.brand.orEmpty()}" })) continue
            if (loggerFilter && !(entry.carriesRequired && NutrientBasis.isVerified(food) && FoodIdentity.matchesDeclaredIdentity(q.raw, food))) continue
            result.add(candidate)
            if (result.size == limit) break
        }
        return result
    }

    /**
     * Collapses rows that are the same product listed twice (same normalized name, state and brand: an OFF barcode
     * repeated, the curated "Pan Integral" and its twin) into the one that should be shown. Survivor: the household
     * default of the query ([anchorId]), then the curated catalog, the user's own foods, verification, priority and
     * usage. The curated catalog check is O(1) (the old comparator re-resolved every name through
     * `findFoodByNormalized`, ~450 regex per comparison, B13). Ties go to the lowest id among curated rows (the exact-name
     * lookup used to pick it) and to the highest among the rest, so the choice never depends on iteration order. The
     * order of the first occurrence of each group is kept.
     */
    fun collapseDuplicates(pool: Collection<FoodItem>, anchorId: String?): List<FoodItem> {
        val groups = LinkedHashMap<String, MutableList<FoodItem>>()
        for (food in pool) groups.getOrPut(duplicateKey(food)) { ArrayList(1) }.add(food)
        val winner = duplicateWinner(anchorId)
        return groups.values.mapNotNull { it.maxWithOrNull(winner) }
    }

    private fun duplicateKey(food: FoodItem): String =
        "${key(food.normalizedName ?: food.name)}:${food.foodState}:${key(food.normalizedBrand ?: food.brand.orEmpty())}"

    private fun duplicateWinner(anchorId: String?): Comparator<FoodItem> =
        compareBy<FoodItem> { it.id == anchorId }
            .thenBy { findStaticFoodById(it.id) != null }
            .thenBy { it.isCustom }
            .thenBy { it.verifiedScore }
            .thenBy { it.sourcePriority }
            .thenBy { it.usageCount }
            .thenComparator { a, b -> if (findStaticFoodById(a.id) != null) b.id.compareTo(a.id) else a.id.compareTo(b.id) }

    /**
     * Singular form of a Spanish plural, good enough to match "huevos" with "huevo": "-ces" becomes "-z" (peces,
     * nueces), "-es" after n/r/l/d/j/x drops the "es" (panes, limones), any other "-s" drops the "s" when 4+ letters
     * remain (papas, tomates). [NO_STEM] lists the words that merely look plural.
     */
    fun stem(token: String): String {
        if (token.length < 4 || token in NO_STEM) return token
        return when {
            token.length >= 5 && token.endsWith("ces") -> token.dropLast(3) + "z"
            token.endsWith("es") && token[token.length - 3] in ES_STEM_CONSONANTS -> token.dropLast(2)
            token.endsWith("s") && !token.endsWith("ss") && token.length - 1 >= 4 -> token.dropLast(1)
            else -> token
        }
    }

    // ─── Words ──────────────────────────────────────────────────────────────

    /** Spanish plural looks that [stem] must keep: "francés", "inglés", "hummus", "más", "país", "gris". */
    private val NO_STEM = setOf("frances", "ingles", "hummus", "mas", "pais", "gris")

    private const val ES_STEM_CONSONANTS = "nrldjx"

    /** Only the trailing "s" of a plural ("dulces" -> "dulce"): the second reading when [stem] guessed another singular. */
    private fun dropS(token: String): String =
        if (token.length - 1 >= 4 && token.endsWith("s") && !token.endsWith("ss") && token !in NO_STEM) token.dropLast(1) else token

    /** Same list as `FoodIdentity.contentTokens` (private there). */
    private val STOP_WORDS = setOf("de", "con", "y", "e", "la", "el", "los", "las", "un", "una", "a", "al", "del", "and", "with", "the")

    /** State words that qualify a name ("Pechuga de Pollo (cruda)") without being part of what the food is. */
    private val STATE_WORDS = setOf(
        "crudo", "cruda", "crudos", "crudas", "cocido", "cocida", "cocidos", "cocidas", "cocinado", "cocinada",
        "hervido", "hervida", "frito", "frita", "fritos", "fritas", "plancha", "horno", "asado", "asada", "asados",
        "asadas", "vapor", "parrilla", "hidratado", "hidratada", "remojado", "remojada", "seco", "seca", "secos", "secas",
    )

    /** Brand values that never identify a product line. */
    private val GENERIC_BRANDS = setOf("generico", "generica", "local", "off", "usda")

    private fun contentTokens(normalized: String): List<String> =
        normalized.split(' ').filter { it.length > 1 && it !in STOP_WORDS }

    /** Search key; strings that already are one (the usual case for catalog rows) skip the Unicode work. */
    private fun key(value: String): String = if (isKey(value)) value else TextKeys.normalize(value)

    private fun isKey(value: String): Boolean {
        var previousSpace = true
        for (c in value) {
            if (c == ' ') {
                if (previousSpace) return false
                previousSpace = true
            } else if (c in 'a'..'z' || c in '0'..'9') {
                previousSpace = false
            } else {
                return false
            }
        }
        return value.isEmpty() || !previousSpace
    }

    /** Is [word] (with its [wordStem] and [wordWithoutS]) the same word as query token [i], whatever its number? */
    private fun sameWord(q: Query, i: Int, word: String, wordStem: String, wordWithoutS: String): Boolean {
        val token = q.tokens[i]
        val tokenStem = q.stems[i]
        val tokenWithoutS = q.withoutS[i]
        return word == token || wordStem == tokenStem || wordWithoutS == tokenWithoutS ||
            word == tokenStem || word == tokenWithoutS || wordStem == token || wordWithoutS == token
    }

    /** Does [word] answer query token [i]: the same word, or (tokens of 4+ letters) a word that starts with it. */
    private fun wordHit(q: Query, i: Int, word: String, wordStem: String, wordWithoutS: String): Boolean =
        sameWord(q, i, word, wordStem, wordWithoutS) ||
            (q.tokens[i].length >= 4 && word.startsWith(q.tokens[i])) || (q.stems[i].length >= 4 && word.startsWith(q.stems[i]))

    /** Does some query token name this word (same word, no prefixes: brands are matched whole)? */
    private fun namesWord(q: Query, word: String): Boolean {
        val wordStem = stem(word)
        val wordWithoutS = dropS(word)
        return q.tokens.indices.any { i -> sameWord(q, i, word, wordStem, wordWithoutS) }
    }

    /**
     * Does [words] equal the whole query phrase ([wholeName] true) or start with it, word by word? Same words whatever
     * their number: "huevos fritos" is the phrase "huevo frito".
     */
    private fun sequenceMatches(q: Query, words: List<String>, wholeName: Boolean): Boolean {
        val size = q.tokens.size
        if (words.size < size || (wholeName && words.size != size)) return false
        for (i in 0 until size) {
            val word = words[i]
            if (!sameWord(q, i, word, stem(word), dropS(word))) return false
        }
        return true
    }

    private fun brandPhrases(food: FoodItem): List<List<String>> {
        val raw = food.brand?.takeIf { it.isNotBlank() } ?: food.normalizedBrand ?: return emptyList()
        return raw.split(',', ';', '/', '|')
            .map { contentTokens(key(it)) }
            .filter { it.isNotEmpty() && it.joinToString(" ") !in GENERIC_BRANDS }
    }

    /** State words written between parentheses: "Pechuga de Pollo (cruda)" -> {cruda}, "Leche Asada" -> {} (a dish). */
    private fun parenthesizedStateWords(rawName: String): Set<String> {
        var open = rawName.indexOf('(')
        if (open < 0) return emptySet()
        val states = HashSet<String>()
        while (open >= 0) {
            val close = rawName.indexOf(')', open + 1)
            val inner = if (close < 0) rawName.substring(open + 1) else rawName.substring(open + 1, close)
            contentTokens(TextKeys.normalize(inner)).filterTo(states) { it in STATE_WORDS }
            open = if (close < 0) -1 else rawName.indexOf('(', close + 1)
        }
        return states
    }

    private fun mightBePack(nameKey: String): Boolean =
        nameKey.any { it in '0'..'9' } || nameKey.contains("pack") || nameKey.contains("kilo") || nameKey.endsWith("kg")

    // ─── Source ─────────────────────────────────────────────────────────────

    /**
     * Where the row comes from, as a starting point for its score. The user's own foods lead, then the curated catalog
     * (static rows and the curated branded catalogs), USDA and OFF last; rows an estimator invented get nothing.
     */
    private fun sourcePrior(food: FoodItem): Double = when {
        food.isAiInferred -> 0.0
        food.isCustom -> PRIOR_CUSTOM
        findStaticFoodById(food.id) != null || food.source.equals("KPKN Curated", ignoreCase = true) -> PRIOR_CURATED
        isOffRow(food) -> PRIOR_OFF
        food.id.startsWith("usda_") || food.source.equals("USDA", ignoreCase = true) -> PRIOR_USDA
        else -> 0.0
    }

    private fun isOffRow(food: FoodItem): Boolean =
        food.id.startsWith("off_") || food.source?.startsWith("OFF", ignoreCase = true) == true

    private fun searchSource(food: FoodItem): SearchSource = when {
        food.tags.any { it.contains("OFF", ignoreCase = true) } -> SearchSource.OFF
        food.tags.any { it.contains("USDA", ignoreCase = true) } -> SearchSource.USDA
        else -> SearchSource.LOCAL
    }

    /** The anchor is the same food the person typed: same family as its head word ("banana" and Plátano). */
    private fun sameFamily(q: Query, food: FoodItem): Boolean {
        val queryFamily = FoodIdentity.familyFor(q.tokens.first()) ?: return false
        return queryFamily == FoodIdentity.familyFor(food)
    }

    /** A scored row; [carriesRequired]: it hit every token the identity rules require (see [Query.required]). */
    private class Scored(val candidate: FoodCandidate, val nameLength: Int, val carriesRequired: Boolean)

    private val ORDER: Comparator<Scored> = Comparator { a, b ->
        val byScore = b.candidate.score.compareTo(a.candidate.score)
        if (byScore != 0) byScore else {
            val byLength = a.nameLength.compareTo(b.nameLength)
            if (byLength != 0) byLength else a.candidate.foodId.compareTo(b.candidate.foodId)
        }
    }

    private const val COVERAGE_WEIGHT = 0.32
    private const val PRECISION_WEIGHT = 0.14
    private const val EXACT_BOOST = 0.45
    private const val PHRASE_START_BOOST = 0.20
    private const val PLAIN_FOOD_BOOST = 0.15
    private const val ANCHOR_BOOST = 0.30
    private const val PRIOR_CUSTOM = 0.22
    private const val PRIOR_CURATED = 0.20
    private const val PRIOR_USDA = 0.10
    private const val PRIOR_OFF = 0.06
    private const val BRAND_NAMED_BOOST = 0.15
    private const val OFF_UNBRANDED_QUERY_PENALTY = -0.05
    private const val PACK_PENALTY = -0.10
    private const val STATE_PENALTY = -0.20
    private const val USAGE_WEIGHT = 0.08
    private const val LEARNED_BOOST = 0.22
}