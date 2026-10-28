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

    /** Palabras de esos candidatos que suenan igual (misma [phoneticKey]). */
    private val byPhonetic: Map<String, List<String>>


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
        val fuzzyWords = vocabulary.filter { it.length >= MIN_FUZZY_LENGTH && it.all { c -> c in 'a'..'z' } }
        candidatesByLength = fuzzyWords
            .groupBy { it.length }
            .mapValues { (_, words) -> words.sorted().toTypedArray() }
        byPhonetic = fuzzyWords.sorted().groupBy { phoneticKey(it) }
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
     * letras, es una palabra común que no es comida, no hay candidato o el más cercano no es único.
     */
    private fun correct(norm: String): String? {
        if (norm.length < MIN_FUZZY_LENGTH || norm in vocabulary || !norm.all { it in 'a'..'z' }) return null
        if (norm in NEVER_CORRECT || norm in PLACE_WORDS) return null
        val cached = corrections[norm]
        if (cached != null) return cached.ifEmpty { null }
        val found = findCorrection(norm).orEmpty()
        if (corrections.size >= MAX_MEMO) corrections.clear()
        corrections[norm] = found
        return found.ifEmpty { null }
    }

    /**
     * Corrección de [word] en tres pasos: plural o singular de una palabra conocida, misma pronunciación, y un cambio de
     * letras (dos desde 8 letras) que respete la primera letra.
     */
    internal fun findCorrection(word: String): String? {
        pluralOrSingular(word)?.let { return it }
        val sameSound = byPhonetic[phoneticKey(word)]
        if (sameSound != null) return closestUnique(word, sameSound)
        val near = nearestUnique(word) ?: return null
        // Una falta rara vez cambia la primera letra: «hasta» no es «pasta», «tuve» no es «tube».
        return if (near[0] == word[0]) near else null
    }

    /** «panes» → «pan», «mate» → «mates»: la misma palabra con o sin plural, si está en el vocabulario. */
    private fun pluralOrSingular(word: String): String? {
        if (word.endsWith("es") && word.length >= 6 && word.dropLast(2) in vocabulary) return word.dropLast(2)
        if (word.endsWith("s") && word.length >= 5 && word.dropLast(1) in vocabulary) return word.dropLast(1)
        if ("${word}s" in vocabulary) return "${word}s"
        if ("${word}es" in vocabulary) return "${word}es"
        return null
    }

    /** El de [candidates] más parecido a [word] por letras, o null si hay empate en el primer puesto. */
    private fun closestUnique(word: String, candidates: List<String>): String? {
        if (candidates.size == 1) return candidates[0]
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var unique = false
        for (candidate in candidates) {
            val distance = boundedDistance(word, candidate, MAX_SOUND_DISTANCE)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
                unique = true
            } else if (distance == bestDistance) {
                unique = false
            }
        }
        return if (unique && bestDistance <= MAX_SOUND_DISTANCE) best else null
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
        private const val MAX_SOUND_DISTANCE = 3
        private const val MAX_MEMO = 8192

        /** ¿[word] es una palabra corriente (verbo, adverbio, día…) que nunca debe ser un alimento por sí sola? */
        fun isCommonWord(word: String): Boolean = word in NEVER_CORRECT

        /**
         * Palabras corrientes (no son comida) que jamás se corrigen aunque un alimento quede a una letra
         * («pero» no es «pera»). Lo que ya está en el vocabulario nunca se corrige de todos modos.
         */
        private val PLACE_WORDS: Set<String> = setOf(
            "habana", "bogota", "medellin", "cusco", "cuzco", "arequipa", "guayaquil", "caracas", "valencia", "maracaibo",
            "barranquilla", "cartagena", "cochabamba", "santiago", "valparaiso", "concepcion", "rosario", "cordoba",
            "mendoza", "monterrey", "guadalajara", "puebla", "oaxaca", "chorrillos", "miraflores", "maipo",
        )

        private val NEVER_CORRECT: Set<String> = setOf(
            // Días de la semana y verbos frecuentes: tampoco son alimento aunque un alias suelto se les parezca.
            "largo", "larga", "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "hubo",
            "hubieron", "has", "hacer",
            "pero", "para", "como", "esta", "este", "esto", "estos", "estas", "esas", "esos", "estoy", "estaba", "estan",
            "tenia", "tengo", "tiene", "tienen", "hace", "hice", "hizo", "hacia", "mucho", "mucha", "muchos", "muchas",
            "poco", "poca", "pocos", "pocas", "solo", "sola", "solos", "tambien", "porque", "cuando", "donde", "desde",
            "hasta", "entre", "otra", "otro", "otras", "otros", "todo", "toda", "todos", "todas", "ahora", "antes",
            "despues", "luego", "mismo", "misma", "mejor", "peor", "bien", "mala", "malo", "casi", "unos", "unas",
            "nada", "algo", "alguna", "alguno", "cada", "ellos", "ellas", "nosotros", "ustedes", "aqui", "alli",
            "tipo", "tipos", "veces", "tuve", "tuvo", "fuimos", "fueron", "fuera", "salimos", "llegue", "llego",
            "pase", "pasamos", "vine", "dame", "dime", "quiero", "queria", "quise", "salio", "ganas", "hambre",
            "rato", "casa", "cosas", "cosa", "algun", "demas", "tanto", "tanta", "tantos", "tantas", "menos", "dijo",
            "dijeron", "dicen", "pido", "pedi", "pidio", "puse", "puso", "estar", "hacer", "comer", "tomar", "beber",
            "cenar", "tenemos", "tuvimos",
            // Narración corriente: verbos, personas, lugares y tiempos que no son comida.
            "abria", "acabo", "acabe", "acabamos", "barriga", "barrio", "calor", "comparti", "compas", "equipo", "esquina",
            "habia", "habian", "hondo", "llena", "lleno", "mediodia", "paseo", "pedido", "pensado", "preparo", "preparamos",
            "pude", "pudo", "quedado", "quedar", "quito", "semana", "socio", "sorbo", "trajo", "traje", "termine",
            "terminar", "terminamos", "tienda", "vecina", "vecino", "amigo", "amiga", "amigos", "amigas", "hermano",
            "hermana", "familia", "trabajo", "trabaje", "clase", "clases", "colegio", "escuela", "oficina", "ciudad",
            "fiesta", "partido", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo", "hecho",
            "hecha", "dicho", "visto", "rapido", "rapida", "chamba", "cerrado", "cerrada", "jugar", "jugamos", "mirar", "mirando",
            "llevar", "llevamos", "venir", "volver", "salir", "estaban", "estabamos", "estuve", "estuvimos", "hicimos",
            "tuvieron", "sirvio", "sirvieron", "invito", "convido", "regalo", "reunion", "viaje", "visita", "turno",
            "each", "there", "though", "through", "wife", "husband", "friend", "friends", "place", "places", "house", "home",
            "work", "office", "school", "party", "game", "match", "week", "weekend", "hungry", "straight", "machine",
            "watts", "jacket", "smashed", "desk", "went", "dinner", "lunch", "breakfast", "snack", "drive", "thru",
            "that", "this", "these", "those", "then", "than", "them", "they", "their", "there", "with", "from", "have",
            "were", "been", "being", "just", "only", "also", "very", "much", "many", "more", "most", "less", "about",
            "around", "after", "before", "while", "because", "when", "where", "what", "which", "would", "could",
            "should", "into", "over", "under", "some", "such", "same", "like", "really", "quite", "maybe", "later",
            "again", "still", "went", "came", "make", "made", "took", "take", "gave", "give", "ordered", "bought",
            "ended", "decided", "tried", "wanted", "felt", "feel", "feeling", "morning", "evening", "night",
        )

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
            // Dos letras seguidas intercambiadas («pltao») cuentan como un solo cambio.
            if (la == 2 && lb == 2 && a[start] == b[start + 1] && a[start + 1] == b[start]) return 1
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

        /**
         * Clave de pronunciación en español: iguala las letras que suenan igual para que una palabra escrita de oído
         * («quezo», «poyo», «enzalada») comparta clave con la bien escrita («queso», «pollo», «ensalada»). Entrada:
         * palabra normalizada (`a-z`). La `h` muda cae, `ll` = `y`, `qu`/`k`/`c` duro = `k`, `c` suave y `z` = `s`,
         * `v` = `b`, `g` suave = `j`, `ch`/`sh` = `X` y las letras dobles (salvo `rr`) se funden.
         */
        internal fun phoneticKey(word: String): String {
            val sb = StringBuilder(word.length)
            val n = word.length
            fun at(k: Int): Char = if (k in 0 until n) word[k] else ' '
            var i = 0
            while (i < n) {
                val c = word[i]
                val next = at(i + 1)
                var out: Char? = null
                var step = 1
                when (c) {
                    'h' -> {}
                    'c' -> when {
                        next == 'h' -> { out = 'X'; step = 2 }
                        next == 'e' || next == 'i' || next == 'y' -> out = 's'
                        else -> out = 'k'
                    }
                    's' -> if (next == 'h') { out = 'X'; step = 2 } else out = 's'
                    'l' -> if (next == 'l') { out = 'y'; step = 2 } else out = 'l'
                    'q' -> { out = 'k'; if (next == 'u') step = 2 }
                    'z' -> out = 's'
                    'v' -> out = 'b'
                    'g' -> when {
                        next == 'u' && (at(i + 2) == 'e' || at(i + 2) == 'i') -> { out = 'g'; step = 2 }
                        next == 'e' || next == 'i' -> out = 'j'
                        else -> out = 'g'
                    }
                    else -> out = c
                }
                if (out != null) {
                    val last = if (sb.isEmpty()) ' ' else sb[sb.length - 1]
                    if (!(out == last && out != 'r')) sb.append(out)
                }
                i += step
            }
            return sb.toString()
        }
    }
}
