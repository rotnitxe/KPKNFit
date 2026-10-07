package com.example.kpkn.domain.training.split

import java.text.Normalizer

/**
 * Texto en minúsculas, sin tildes ni signos: la forma en la que se comparan nombres de ejercicio y etiquetas de reparto
 * («Tirón» y «tiron» son la misma palabra, «Pecho/Espalda» son dos).
 */
internal object SplitText {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9]+")

    fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return separators.replace(combiningMarks.replace(decomposed, ""), " ").trim()
    }

    /** Palabras de [raw], ya normalizadas y sin vacíos. */
    fun words(raw: String): List<String> = normalize(raw).split(' ').filter { it.isNotEmpty() }
}
