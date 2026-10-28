package com.example.kpkn.domain.nutrition.v2

import java.util.concurrent.ConcurrentHashMap

/** Origen de un alias de concepto: español, inglés o falta de ortografía conocida. */
enum class AliasKind { ES, EN, TYPO }

enum class SizeV2 { S, M, L }

/** Estados de cocción del examen (`EXAMEN.md`). */
enum class FoodStateV2 { RAW, COOKED, FRIED, GRILLED, BAKED, STEAMED }

/** Formas del alimento del examen (`EXAMEN.md`). */
enum class FoodFormV2 {
    NA, POWDER_DRY, RECONSTITUTED, CONCENTRATE, LIQUID, DRIED, CANNED_DRAINED, CANNED_UNDRAINED, LIGHT, SUGAR_FREE,
}

enum class MediumV2 { WATER, MILK }

enum class ConnectorKind { AND, WITH }

/** Lo que el léxico sabe de un tramo de texto. Un mismo tramo puede llevar varias etiquetas (alias ambiguos). */
sealed interface LexTag {
    /** Alias de un concepto del catálogo. */
    data class Food(val conceptId: String, val aliasKind: AliasKind) : LexTag

    /** Unidad de masa: [grams] por unidad. */
    data class MassUnit(val grams: Double) : LexTag

    /** Unidad de volumen: [ml] por unidad. */
    data class VolumeUnit(val ml: Double) : LexTag

    /** Medida casera con su etiqueta canónica («taza», «cucharada»…). */
    data class Household(val label: String) : LexTag

    /** Envase con su etiqueta canónica («lata», «botella»…). */
    data class Container(val label: String) : LexTag

    data class Size(val size: SizeV2) : LexTag
    data class State(val state: FoodStateV2) : LexTag
    data class Form(val form: FoodFormV2) : LexTag
    data class Medium(val medium: MediumV2) : LexTag
    data object Negation : LexTag
    data class Connector(val kind: ConnectorKind) : LexTag
    data object Preposition : LexTag

    /** Marca de corrección («perdón», «eran», «actually»). */
    data object Correction : LexTag

    /** Relleno que se ignora («me comí», «I had»). */
    data object Filler : LexTag

    /** Cantidad. [weak] = también puede ser un artículo («un», «a», «one»). */
    data class Number(val value: Double, val weak: Boolean) : LexTag

    data class Punct(val symbol: String) : LexTag
}

/** Entrada del léxico: una secuencia de tokens normalizados y su etiqueta. */
data class LexEntry(val tokens: List<String>, val tag: LexTag) {
    companion object {
        /**
         * Entrada a partir de texto libre: pasa la frase por [NormalizerV2], igual que el texto del usuario, para que
         * las dos lados coincidan («tres leches» queda `3 leches` en ambos). Null si la frase no deja tokens.
         */
        fun of(phrase: String, tag: LexTag): LexEntry? {
            val tokens = NormalizerV2.tokenize(phrase).map { it.norm }
            return if (tokens.isEmpty()) null else LexEntry(tokens, tag)
        }
    }
}

/**
 * Tramo etiquetado: tokens [first]..[last] (índices incluidos). [tags] vacío = palabra desconocida.
 * [fuzzy] = algún token se corrigió por distancia de edición para llegar a la coincidencia.
 */
data class TaggedSpan(val first: Int, val last: Int, val tags: List<LexTag>, val fuzzy: Boolean)

/**
 * Léxico del parser v2 (F2, paso 2): trie de tokens con coincidencia más larga y corrección de faltas acotada.
 *
 * Cubre toda la entrada: cada token pertenece a exactamente un [TaggedSpan], en orden. Es inmutable y seguro para
 * usar desde varios hilos (la memoria de correcciones es concurrente y acotada).
 */
class LexiconV2(entries: List<LexEntry>) {

    private class Node {
        val children = HashMap<String, Node>(4)
        var tags: List<LexTag>? = null
    }

    private val root = Node()
    private val vocabulary = HashSet<String>()

    /** Candidatos para corregir faltas: palabras de vocabulario, solo letras, ≥ 4, indexadas por largo. */
    private val candidatesByLength: Map<Int, Array<String>>

    private val corrections = ConcurrentHashMap<String, String>()

    init {
        for (entry in entries) {
            if (entry.tokens.isEmpty()) continue
            var node = root
            for (token in entry.tokens) {
                vocabulary += token
                node = node.children.getOrPut(token) { Node() }
            }
            val current = node.tags
            node.tags = if (current == null) listOf(entry.tag) else if (entry.tag in current) current else current + entry.tag
        }
        candidatesByLength = vocabulary
            .filter { it.length >= MIN_FUZZY_LENGTH && it.all { c -> c in 'a'..'z' } }
            .groupBy { it.length }
            .mapValues { (_, words) -> words.sorted().toTypedArray() }
    }

    /** Etiqueta [tokens]: coincidencia más larga en cada posición; lo desconocido queda sin etiquetas. */
    fun tag(tokens: List<TokenV2>): List<TaggedSpan> {
        if (tokens.isEmpty()) return emptyList()
        val norms = ArrayList<String>(tokens.size)
        val corrected = BooleanArray(tokens.size)
        tokens.forEachIndexed { index, token ->
            val fixed = correct(token.norm)
            if (fixed != null) corrected[index] = true
            norms += fixed ?: token.norm
        }
        val spans = ArrayList<TaggedSpan>(tokens.size)
        var i = 0
        while (i < norms.size) {
            var node = root
            var j = i
            var bestEnd = -1
            var bestTags: List<LexTag>? = null
            while (j < norms.size) {
                node = node.children[norms[j]] ?: break
                node.tags?.let { bestEnd = j; bestTags = it }
                j++
            }
            if (bestEnd >= 0) {
                var fuzzy = false
                for (k in i..bestEnd) if (corrected[k]) fuzzy = true
                val tags = withSingleTokenTags(bestTags!!, tokens[i], i == bestEnd)
                spans += TaggedSpan(i, bestEnd, tags, fuzzy)
                i = bestEnd + 1
            } else {
                spans += TaggedSpan(i, i, withSingleTokenTags(emptyList(), tokens[i], true), false)
                i++
            }
        }
        return spans
    }

    /** Un token numérico o un signo siempre lleva su etiqueta de cantidad o de signo, además de las del léxico. */
    private fun withSingleTokenTags(tags: List<LexTag>, token: TokenV2, single: Boolean): List<LexTag> {
        if (!single) return tags
        return when {
            token.isNumber -> if (tags.any { it is LexTag.Number }) tags else tags + LexTag.Number(token.norm.toDouble(), weak = false)
            token.isPunct -> if (tags.any { it is LexTag.Punct }) tags else tags + LexTag.Punct(token.norm)
            else -> tags
        }
    }

    // ─── Corrección de faltas ───────────────────────────────────────────

    /**
     * Palabra de vocabulario a la que corregir [norm], o null si no se corrige: ya existe, es corta, no es solo
     * letras, no hay candidato o el más cercano no es único.
     */
    private fun correct(norm: String): String? {
        if (norm.length < MIN_FUZZY_LENGTH || norm in vocabulary || !norm.all { it in 'a'..'z' }) return null
        val cached = corrections[norm]
        if (cached != null) return cached.ifEmpty { null }
        val found = nearestUnique(norm).orEmpty()
        if (corrections.size >= MAX_MEMO) corrections.clear()
        corrections[norm] = found
        return found.ifEmpty { null }
    }

    private fun nearestUnique(word: String): String? {
        val maxDistance = if (word.length >= 8) 2 else 1
        var best: String? = null
        var bestDistance = maxDistance + 1
        var unique = false
        for (length in (word.length - 2)..(word.length + 2)) {
            val bucket = candidatesByLength[length] ?: continue
            for (candidate in bucket) {
                val distance = boundedDistance(word, candidate, minOf(bestDistance, maxDistance))
                if (distance > maxDistance || distance > bestDistance) continue
                if (distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                    unique = true
                } else {
                    unique = false
                }
            }
        }
        return if (unique) best else null
    }

    companion object {
        private const val MIN_FUZZY_LENGTH = 4
        private const val MAX_MEMO = 8192

        /** Léxico con el vocabulario de modificadores de [ModifierVocabularyV2] más los alias de comida dados. */
        fun withModifiers(foodAliases: List<LexEntry>): LexiconV2 =
            LexiconV2(ModifierVocabularyV2.entries + foodAliases)

        /**
         * Distancia de Levenshtein entre [a] y [b] si es ≤ [limit]; si no, un valor mayor que [limit]. Corta en cuanto
         * ninguna celda de la fila puede quedar dentro del límite.
         */
        internal fun boundedDistance(a: String, b: String, limit: Int): Int {
            val n = a.length
            val m = b.length
            if (kotlin.math.abs(n - m) > limit) return limit + 1
            var start = 0
            while (start < n && start < m && a[start] == b[start]) start++
            var endA = n
            var endB = m
            while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }
            val la = endA - start
            val lb = endB - start
            if (la == 0 || lb == 0) return maxOf(la, lb)
            // Con un solo cambio permitido, lo que queda tras quitar prefijo y sufijo comunes es una sustitución
            // (1 y 1) o nada más: se decide sin reservar memoria (es el caso caliente de la corrección de faltas).
            if (limit <= 1) return if (la == 1 && lb == 1) 1 else 2
            var previous = IntArray(lb + 1) { it }
            var current = IntArray(lb + 1)
            for (x in 1..la) {
                current[0] = x
                var rowMin = x
                val ca = a[start + x - 1]
                for (y in 1..lb) {
                    val cost = if (ca == b[start + y - 1]) 0 else 1
                    val value = minOf(previous[y] + 1, current[y - 1] + 1, previous[y - 1] + cost)
                    current[y] = value
                    if (value < rowMin) rowMin = value
                }
                if (rowMin > limit) return limit + 1
                val swap = previous; previous = current; current = swap
            }
            return previous[lb]
        }
    }
}
