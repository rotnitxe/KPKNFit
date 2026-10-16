package com.example.kpkn.domain.discomfort

import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DISCOMFORT_KNOWLEDGE_BY_ID
import com.example.kpkn.data.models.DiscomfortCatalogEntry
import java.text.Normalizer

/** Por qué una molestia salió en la búsqueda; el orden de los valores ES el orden de prioridad. */
enum class DiscomfortMatchKind {
    /** La etiqueta empieza por lo escrito («rodi» → «Rodilla anterior»). */
    LABEL_START,

    /** Cada palabra escrita es una palabra de la etiqueta o el comienzo de una («anterior»). */
    LABEL_WORD,

    /** Coincide con un sinónimo o forma coloquial («espalda baja» → «Lumbar»). */
    ALIAS,

    /** Coincide con una falta de una letra («rodila» → «Rodilla anterior»). */
    APPROXIMATE,
}

data class DiscomfortSearchHit(
    val entry: DiscomfortCatalogEntry,
    val kind: DiscomfortMatchKind,
)

/**
 * Búsqueda amable de molestias: sobre la etiqueta y los `searchAliases` de [DiscomfortKnowledge], sin tildes ni
 * mayúsculas y tolerante a UNA falta (una letra de más, de menos, cambiada o dos contiguas intercambiadas) en
 * palabras de [MIN_LENGTH_FOR_TYPO] letras o más. Dominio puro y determinista.
 *
 * Orden: coincidencia al inicio de la etiqueta > palabra de la etiqueta > sinónimo > aproximada; dentro de cada
 * grupo, el orden del catálogo. Una consulta vacía no devuelve nada (la interfaz muestra los chips destacados).
 */
object DiscomfortSearch {

    /** Una falta solo se tolera en palabras de 5 letras o más: en las cortas «codo»/«todo» se confundirían. */
    const val MIN_LENGTH_FOR_TYPO: Int = 5

    /** Faltas toleradas por palabra (distancia de edición). */
    private const val MAX_TYPO_DISTANCE: Int = 1

    /** Sin la entrada `"none"`: «Sin molestias» no se busca, es una opción aparte de la interfaz. */
    private val searchable: List<DiscomfortCatalogEntry> =
        DISCOMFORT_CATALOG.filter { it.id != "none" }

    fun search(
        query: String,
        catalog: List<DiscomfortCatalogEntry> = searchable,
        aliasesById: (String) -> List<String> = { id -> DISCOMFORT_KNOWLEDGE_BY_ID[id]?.searchAliases.orEmpty() },
    ): List<DiscomfortSearchHit> {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return emptyList()
        val normalizedQuery = queryTokens.joinToString(" ")
        return catalog
            .filter { it.id != "none" }
            .mapIndexedNotNull { index, entry ->
                kindFor(entry, normalizedQuery, queryTokens, aliasesById(entry.id))
                    ?.let { Triple(index, entry, it) }
            }
            .sortedWith(compareBy({ it.third.ordinal }, { it.first }))
            .map { (_, entry, kind) -> DiscomfortSearchHit(entry, kind) }
    }

    /** Solo el orden de ids, para quien no necesita el motivo. */
    fun searchIds(query: String): List<String> = search(query).map { it.entry.id }

    private fun kindFor(
        entry: DiscomfortCatalogEntry,
        normalizedQuery: String,
        queryTokens: List<String>,
        aliases: List<String>,
    ): DiscomfortMatchKind? {
        val label = normalize(entry.label)
        val labelWords = label.split(' ').filter { it.isNotEmpty() }
        if (label.startsWith(normalizedQuery)) return DiscomfortMatchKind.LABEL_START
        if (allTokensMatch(queryTokens, labelWords, allowTypo = false)) return DiscomfortMatchKind.LABEL_WORD

        val aliasWordLists = aliases.map { alias -> normalize(alias) }
            .filter { it.isNotEmpty() }
            .map { it to it.split(' ') }
        val aliasHit = aliasWordLists.any { (alias, words) ->
            alias.startsWith(normalizedQuery) || allTokensMatch(queryTokens, words, allowTypo = false)
        }
        // Un sinónimo también puede completarse con palabras de la etiqueta («rodilla» + «anterior»).
        val combinedWords = labelWords + aliasWordLists.flatMap { it.second }
        if (aliasHit || allTokensMatch(queryTokens, combinedWords, allowTypo = false)) {
            return DiscomfortMatchKind.ALIAS
        }
        if (allTokensMatch(queryTokens, combinedWords, allowTypo = true)) return DiscomfortMatchKind.APPROXIMATE
        return null
    }

    /** Cada palabra escrita debe ser (o empezar) una palabra del candidato; con [allowTypo], también por una falta. */
    private fun allTokensMatch(queryTokens: List<String>, words: List<String>, allowTypo: Boolean): Boolean =
        queryTokens.all { token ->
            words.any { word ->
                word == token || word.startsWith(token) ||
                    (allowTypo && token.length >= MIN_LENGTH_FOR_TYPO && word.length >= MIN_LENGTH_FOR_TYPO &&
                        editDistanceAtMostOne(token, word))
            }
        }

    /** Minúsculas, sin tildes ni signos, con espacios simples: «Muñeca / mano» → «muneca mano». */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                ch.isLetterOrDigit() -> builder.append(ch)
                else -> builder.append(' ')
            }
        }
        return builder.toString().trim().replace(Regex(" +"), " ")
    }

    private fun tokens(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /** Distancia de edición ≤ 1 (insertar, borrar, cambiar una letra o intercambiar dos contiguas). */
    internal fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        val lengthDiff = a.length - b.length
        if (lengthDiff > MAX_TYPO_DISTANCE || lengthDiff < -MAX_TYPO_DISTANCE) return false
        if (lengthDiff == 0) {
            val firstDiff = a.indices.first { a[it] != b[it] }
            if (a.substring(firstDiff + 1) == b.substring(firstDiff + 1)) return true // cambio de una letra
            return firstDiff + 1 < a.length &&
                a[firstDiff] == b[firstDiff + 1] && a[firstDiff + 1] == b[firstDiff] &&
                a.substring(firstDiff + 2) == b.substring(firstDiff + 2) // dos letras intercambiadas
        }
        val (shorter, longer) = if (lengthDiff < 0) a to b else b to a
        var i = 0
        while (i < shorter.length && shorter[i] == longer[i]) i++
        return shorter.substring(i) == longer.substring(i + 1)
    }
}
