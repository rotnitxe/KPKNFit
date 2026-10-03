package com.example.kpkn.domain.nutrition

import java.text.Normalizer

/**
 * PhoneticEs — Metaphone adapted for Spanish for food name matching.
 * Collapses: b/v, c/z/s, h muda, j/g suave, ll/y, qu/k, w/u.
 *
 * The five vowels stay distinct and only a run of the same vowel collapses (WP-N11). The code is evidence that two spellings are
 * one word: with every vowel folded into a single letter "uva", "huevo", "ave" and "haba" encoded alike, and so did "pasta",
 * "pesto" and "posta", "mote" and "mate", "lima" and "lomo". Variant spellings of one sound still meet through their
 * consonants: "poyo" = "pollo", "uebo" = "wevo" = "huevo", "keso" = "queso".
 *
 * Used by SmartFoodResolver for fuzzy token matching and by FoodIdentity as evidence of identity.
 */
object PhoneticEs {

    fun encode(word: String): String {
        var s = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
            .replace(TextKeys.MARKS, "") // strip diacritics
            .trim()

        if (s.isEmpty()) return s
        if (s.length <= 2) return s.uppercase()

        // Leading h is silent in Spanish
        if (s.startsWith("h")) s = s.substring(1)
        if (s.isEmpty()) return s

        val result = StringBuilder()

        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)

            when {
                // b/v → B
                c == 'b' || c == 'v' -> result.append('B')

                // ch → CH (keep as digraph)
                c == 'c' && next == 'h' -> {
                    result.append("CH")
                    i++
                }

                // c before e/i → S; qu → K; otherwise → K
                c == 'c' && next != null && (next == 'e' || next == 'i') -> {
                    result.append('S')
                }
                c == 'c' && next == 'u' && s.getOrNull(i + 2) != null && (s.getOrNull(i + 2) == 'e' || s.getOrNull(i + 2) == 'i') -> {
                    // cue, cui → K
                    result.append('K')
                }
                c == 'c' -> result.append('K')

                // z → S
                c == 'z' -> result.append('S')

                // g before e/i → J (soft); gu → G
                c == 'g' && next != null && (next == 'e' || next == 'i') -> {
                    result.append('J')
                }
                c == 'g' && next == 'u' -> {
                    result.append('G')
                    if (s.getOrNull(i + 2) == 'e' || s.getOrNull(i + 2) == 'i') {
                        i++ // skip u in gue/gui
                    }
                }
                c == 'g' -> result.append('G')

                // h → silent (skip)
                c == 'h' -> { /* skip */ }

                // j → J
                c == 'j' -> result.append('J')

                // ll → Y
                c == 'l' && next == 'l' -> {
                    result.append('Y')
                    i++
                }

                // ñ → N (simplified)
                c == 'ñ' -> result.append('N')

                // qu → K
                c == 'q' && next == 'u' -> {
                    result.append('K')
                    i++
                }

                // r/rr → R
                c == 'r' -> result.append('R')

                // s → S
                c == 's' -> result.append('S')

                // x → KS (or S in Mexican Spanish)
                c == 'x' -> result.append('S')

                // w → U (rare in Spanish)
                c == 'w' -> result.append('U')

                // y/ll collapse to the same consonant sound.
                c == 'y' -> result.append('Y')

                // A vowel keeps its identity; only a run of the same vowel collapses ("polloooo" = "pollo").
                isVowel(c) -> {
                    val vowel = c.uppercaseChar()
                    if (result.isEmpty() || result.last() != vowel) {
                        result.append(vowel)
                    }
                }

                // Consonants → keep as uppercase
                c.isLetter() -> result.append(c.uppercaseChar())

                // Skip non-letters
            }
            i++
        }

        return result.toString()
    }

    private fun isVowel(c: Char?): Boolean {
        if (c == null) return false
        return c.lowercaseChar() in "aeiouáéíóú"
    }
}
