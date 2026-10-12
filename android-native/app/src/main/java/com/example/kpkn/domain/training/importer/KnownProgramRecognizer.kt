package com.example.kpkn.domain.training.importer

import java.text.Normalizer
import java.util.Locale

/** Un programa de KPKN que el archivo importado parece ser: el id de su plan y la frase que lo delató. */
data class KnownProgramMatch(val planId: String, val aliasMatched: String)

/**
 * H3f · Reconoce cuándo un archivo importado es un programa que KPKN ya tiene (nSuns, GZCLP, Smolov, Texas Method…). Puro: solo
 * lee texto. Las frases cuentan como palabras completas tras normalizar (sin tildes ni signos, en minúsculas).
 *
 * Prioridad de fuentes: el título manda; si no dice nada, los nombres de hoja; si tampoco, las celdas de texto. Dentro de un
 * nivel gana la frase más específica (la que contiene a las demás: «smolov jr» antes que «smolov»). Si quedan planes distintos
 * en el mismo nivel, no se reconoce ninguno.
 */
object KnownProgramRecognizer {

    /** Cuántas filas no vacías de cada hoja (o líneas del texto pegado) se miran. */
    private const val FIRST_ROWS = 10

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    /**
     * Frase normalizada → id del plan en `PersonalizedPlanCatalog`. Solo planes que se ofrecen (lo comprueba
     * `KnownProgramRecognizerTest`). «5/3/1» solo no basta: lo usan también otros programas. Los ids de PHUL y PHAT son los de
     * `AuthoredPhulPhatRecipes`; «PPL» solo no basta: hace falta «reddit ppl» o «metallicadpa».
     */
    internal val ALIASES: List<Pair<String, String>> = listOf(
        "nsuns" to "protocol:nsuns-531-lp-4d",
        "n suns" to "protocol:nsuns-531-lp-4d",
        "gzclp" to "protocol:gzclp",
        "smolov" to "protocol:smolov",
        "smolov jr" to "protocol:smolov-jr",
        "smolov junior" to "protocol:smolov-jr",
        "candito 6" to "protocol:candito-6",
        "sheiko 29" to "protocol:sheiko-29-32",
        "sheiko 30" to "protocol:sheiko-29-32",
        "sheiko 31" to "protocol:sheiko-29-32",
        "sheiko 32" to "protocol:sheiko-29-32",
        "texas method" to "protocol:texas-method-3d",
        "texas method 3 dias" to "protocol:texas-method-3d",
        "texas method 3 days" to "protocol:texas-method-3d",
        "texas method 4 dias" to "protocol:texas-method-4d",
        "texas method 4 days" to "protocol:texas-method-4d",
        "texas method 4 day" to "protocol:texas-method-4d",
        "phul" to "original:phul-ms-2021-r1",
        "phul adaptado" to "adapted:phul-kpkn-r1",
        "phat" to "original:phat-biolayne-2016-r1",
        "phat adaptado" to "adapted:phat-kpkn-r1",
        "reddit ppl" to "protocol:kpkn-ppl-6",
        "metallicadpa" to "protocol:kpkn-ppl-6",
        "madcow" to "protocol:madcow-5x5",
        "juggernaut 2" to "protocol:juggernaut-2",
        "calgary barbell 16" to "protocol:calgary-16",
        "calgary 16" to "protocol:calgary-16",
        "tsa 9" to "protocol:tsa-9",
        "cube method" to "protocol:cube-method",
        "westside" to "protocol:westside-conjugate",
        "coan philippi" to "protocol:coan-phillipi-dl",
        "coan y philippi" to "protocol:coan-phillipi-dl",
        "coan phillipi" to "protocol:coan-phillipi-dl",
        "jacked tan 2" to "protocol:gzcl-jt-2",
        "jacked and tan 2" to "protocol:gzcl-jt-2",
        "rippler" to "protocol:gzcl-rippler",
        "uhf 9" to "protocol:gzcl-uhf-9",
    )

    /**
     * El programa que KPKN ya tiene para [title], [sheetNames] y [firstTexts] (las celdas de texto de las primeras filas), o null.
     * Se decide en el primer nivel que nombra algún programa; si ese nivel nombra dos planes distintos, no se reconoce ninguno.
     */
    fun recognize(title: String, sheetNames: List<String>, firstTexts: List<String>): KnownProgramMatch? {
        val levels = listOf(listOf(title), sheetNames, firstTexts)
        for (texts in levels) {
            val hits = texts.flatMap { hitsIn(normalize(it)) }
            if (hits.isEmpty()) continue
            // Una frase contenida en otra más específica no cuenta: «smolov» dentro de «smolov jr».
            val strongest = hits.filter { hit ->
                hits.none { other -> other.phrase != hit.phrase && " ${other.phrase} ".contains(" ${hit.phrase} ") }
            }
            val planId = strongest.map { it.planId }.distinct().singleOrNull() ?: return null
            val phrase = strongest.filter { it.planId == planId }.sortedByDescending { it.phrase.length }.first().phrase
            return KnownProgramMatch(planId, phrase)
        }
        return null
    }

    /** Un archivo ya leído: su título, los nombres de hoja y las celdas de texto de las primeras filas no vacías de cada hoja. */
    fun recognizeSheets(title: String, sheets: List<ImportSheet>): KnownProgramMatch? = recognize(
        title = title,
        sheetNames = sheets.map { it.name },
        firstTexts = sheets.flatMap { sheet ->
            sheet.rows.filter { row -> row.any { it.isNotBlank() } }
                .take(FIRST_ROWS)
                .flatMap { row -> row.filter { it.isNotBlank() } }
        },
    )

    /** Un texto pegado: no tiene título ni hojas; sus primeras líneas no vacías hacen de celdas. */
    fun recognizeText(text: String): KnownProgramMatch? = recognize(
        title = "",
        sheetNames = emptyList(),
        firstTexts = text.lines().filter { it.isNotBlank() }.take(FIRST_ROWS),
    )

    /** Minúsculas, sin tildes ni signos y con espacios simples: lo que se compara con [ALIASES]. */
    internal fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()

    private class Hit(val phrase: String, val planId: String)

    /** Las frases de la tabla que aparecen como palabras completas en [normalizedText]. */
    private fun hitsIn(normalizedText: String): List<Hit> {
        val padded = " $normalizedText "
        return ALIASES.filter { (phrase, _) -> padded.contains(" $phrase ") }.map { (phrase, planId) -> Hit(phrase, planId) }
    }
}
