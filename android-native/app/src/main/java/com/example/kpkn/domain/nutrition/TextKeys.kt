package com.example.kpkn.domain.nutrition

import java.text.Normalizer

/**
 * Precompiled text-key primitives for the nutrition pipeline (WP-N1 / WP-S7).
 *
 * The search-key normalizer runs inside the hottest loops of parsing, indexing and
 * ranking (once per candidate, per token, per alias). Compiling its three regexes on
 * every call dominated those loops, so they are built exactly once here and every
 * identical normalizer delegates to [normalize].
 *
 * Pure Kotlin / JVM: no Android or database dependency.
 */
object TextKeys {
    /** Combining marks left by NFD decomposition (accents, the tilde of n-tilde, diaeresis). */
    val MARKS = Regex("""\p{Mn}+""")

    /** Any run of characters that is neither a letter nor a decimal digit. */
    val NON_ALNUM = Regex("""[^\p{L}\p{Nd}]+""")

    /** Any run of whitespace. */
    val SPACES = Regex("""\s+""")

    /**
     * Accent-free, lower-case search key: letters and decimal digits separated by single
     * spaces. NFD decomposition, combining marks removed, lower-cased (root locale), every
     * other run replaced by one space, then trimmed. Hence n-tilde becomes "n" and "1,5"
     * becomes "1 5".
     *
     * This is exactly the chain FoodIdentity.normalize and FoodIndex.normalizeSearch ran
     * inline before they delegated here, so every key they produce is unchanged.
     */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(NON_ALNUM, " ")
            .replace(SPACES, " ")
            .trim()
}
