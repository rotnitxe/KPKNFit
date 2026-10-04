package com.example.kpkn.domain.nutrition

/**
 * Word-boundary helpers that read the same on the JVM and on Android (WP-N4).
 *
 * `\b` is not portable: since JDK 19 it is ASCII-only (an accented letter is a non-word
 * character), while the ICU engine behind `java.util.regex` on Android treats `é` as a word
 * character. A token that starts or ends with an accented letter, or sits next to one
 * ("jam" in "jamón"), therefore matched differently in the unit tests than on the device:
 * `\bpoch[eé]\b` never matched "poché" on the JVM, and on the phone "huevo poché" was
 * swallowed whole. Neither `(?U)` nor `UNICODE_CHARACTER_CLASS` is an option: ICU rejects
 * unknown inline flags (the very failure `FoodParserAndroidTest` guards).
 *
 * The lookarounds below only use `\p{L}`, `\p{N}` and `_`, which both engines read the
 * same way, so a pattern built with them matches identically on either platform. Keep a
 * plain `\b` where the neighbours cannot be accented letters (digits, punctuation, anchors).
 *
 * Pure Kotlin / JVM: no Android dependency.
 */
object RegexEs {
    /** Not preceded by a letter, a digit or an underscore. */
    const val LEFT_EDGE: String = """(?<![\p{L}\p{N}_])"""

    /** Not followed by a letter, a digit or an underscore. */
    const val RIGHT_EDGE: String = """(?![\p{L}\p{N}_])"""

    /**
     * Whole-token form of the alternation [alt] (given without its enclosing group):
     * `(?<![\p{L}\p{N}_])(?:alt)(?![\p{L}\p{N}_])`. Capture groups inside [alt] keep their numbers.
     */
    fun bounded(alt: String): String = LEFT_EDGE + "(?:" + alt + ")" + RIGHT_EDGE

    /** [bounded] for one literal token (quoted, so punctuation in it is never a regex operator). */
    fun boundedLiteral(token: String): String = bounded(Regex.escape(token))
}
