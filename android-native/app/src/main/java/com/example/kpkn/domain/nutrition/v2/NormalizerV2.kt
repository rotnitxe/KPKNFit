package com.example.kpkn.domain.nutrition.v2

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer

/**
 * Una pieza del texto ya normalizada. [norm] es la forma canónica (minúsculas, sin tildes, números en cifras)
 * y [start]..[end) es el tramo EXACTO que ocupa en el texto original (con tildes y mayúsculas).
 */
data class TokenV2(val norm: String, val start: Int, val end: Int) {
    /** Palabra (`[a-z]+`). */
    val isWord: Boolean get() = norm.isNotEmpty() && norm[0] in 'a'..'z'

    /** Número ya en cifras («200», «1.5», «0.3333»). */
    val isNumber: Boolean get() = norm.isNotEmpty() && norm[0] in '0'..'9'

    /** Signo conservado (`, ; . + ( ) :`). */
    val isPunct: Boolean get() = norm.length == 1 && norm[0] in NormalizerV2.PUNCTUATION
}

/**
 * Normalizador del parser v2 (F2, paso 1): parte el texto en tokens y guarda de cada uno su tramo en el original.
 *
 * - Palabras `[a-z0-9]`: minúsculas, sin tildes (NFD, `ñ` → `n`). Cualquier otro símbolo (guiones, comillas,
 *   emojis…) se descarta y separa palabras.
 * - Signos que se conservan como token propio: `, ; . + ( ) :`.
 * - Números: «1,5» y «1.5» → `1.5`; «1/2» → `0.5`; «½ ¼ ¾ ⅓ ⅔…» → decimal con 4 decimales como máximo. Un número
 *   pegado a una unidad («200g», «2x») da dos tokens.
 * - Separador de miles: «1.000 g», «1,000 g» y «2.500 kcal» son mil, mil y dos mil quinientas (en Chile el punto separa
 *   miles); con kilos o litros («1.500 kg», «2,250 l») es decimal; «1.000.000» siempre es miles.
 * - Números hablados sin ambigüedad («treinta y cinco», «two hundred fifty») → una cifra con el tramo de toda la
 *   expresión. Los ambiguos (un, una, a, one, medio, cuarto, par, docena…) quedan como palabra para el léxico.
 * - «N y medio» («dos y medio», «un litro y medio», «kilo y medio», «a cup and a half») → N + 0,5 sobre el número.
 */
object NormalizerV2 {
    internal val PUNCTUATION = setOf(',', ';', '.', '+', '(', ')', ':')

    private val VULGAR_FRACTIONS: Map<Char, Pair<Int, Int>> = mapOf(
        '½' to (1 to 2), '⅓' to (1 to 3), '⅔' to (2 to 3), '¼' to (1 to 4), '¾' to (3 to 4),
        '⅕' to (1 to 5), '⅖' to (2 to 5), '⅗' to (3 to 5), '⅘' to (4 to 5),
        '⅙' to (1 to 6), '⅚' to (5 to 6),
        '⅛' to (1 to 8), '⅜' to (3 to 8), '⅝' to (5 to 8), '⅞' to (7 to 8),
    )

    fun tokenize(text: String): List<TokenV2> = dropFractionOf(mergeHalves(mergeSpokenNumbers(scan(text))))

    // ─── Escaneo de caracteres ─────────────────────────────────────────

    private fun scan(text: String): List<TokenV2> {
        val out = ArrayList<TokenV2>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            val fraction = VULGAR_FRACTIONS[c]
            when {
                c in '0'..'9' -> i = scanNumber(text, i, out)
                // «.5» con el punto al inicio de una palabra: número, no signo.
                c == '.' && i + 1 < n && text[i + 1] in '0'..'9' && (i == 0 || text[i - 1].isWhitespace()) -> {
                    var k = i + 1
                    while (k < n && text[k] in '0'..'9') k++
                    out += TokenV2(format(BigDecimal("0." + text.substring(i + 1, k))), i, k)
                    i = k
                }
                c in PUNCTUATION -> {
                    out += TokenV2(c.toString(), i, i + 1)
                    i++
                }
                // «pan & queso»: el «&» une igual que el «+».
                c == '&' -> {
                    out += TokenV2("+", i, i + 1)
                    i++
                }
                fraction != null -> {
                    out += TokenV2(format(divide(BigDecimal(fraction.first), BigDecimal(fraction.second))), i, i + 1)
                    i++
                }
                baseLetter(c) != null -> i = scanWord(text, i, out)
                else -> i++
            }
        }
        return out
    }

    private fun scanWord(text: String, from: Int, out: MutableList<TokenV2>): Int {
        val sb = StringBuilder()
        var j = from
        var end = from
        while (j < text.length) {
            val ch = text[j]
            val base = baseLetter(ch)
            if (base != null) {
                sb.append(base)
                j++
                end = j
            } else if (ch.category == CharCategory.NON_SPACING_MARK) {
                // Tilde suelta (texto ya descompuesto): pertenece a la letra anterior.
                j++
                end = j
            } else {
                break
            }
        }
        out += TokenV2(sb.toString(), from, end)
        return j
    }

    private fun scanNumber(text: String, from: Int, out: MutableList<TokenV2>): Int {
        val n = text.length
        var j = from
        while (j < n && text[j] in '0'..'9') j++
        val whole = BigDecimal(text.substring(from, j))
        val thousandsEnd = thousandsEnd(text, from, j)
        if (thousandsEnd > 0) {
            val digits = text.substring(from, thousandsEnd).filter { it in '0'..'9' }
            out += TokenV2(format(BigDecimal(digits)), from, thousandsEnd)
            return thousandsEnd
        }
        if (j + 1 < n && (text[j] == ',' || text[j] == '.') && text[j + 1] in '0'..'9') {
            var k = j + 1
            while (k < n && text[k] in '0'..'9') k++
            out += TokenV2(format(BigDecimal(text.substring(from, j) + "." + text.substring(j + 1, k))), from, k)
            return k
        }
        if (j + 1 < n && text[j] == '/' && text[j + 1] in '0'..'9') {
            var k = j + 1
            while (k < n && text[k] in '0'..'9') k++
            val denominator = BigDecimal(text.substring(j + 1, k))
            if (denominator.signum() != 0) {
                out += TokenV2(format(divide(whole, denominator)), from, k)
                return k
            }
        }
        out += TokenV2(format(whole), from, j)
        return j
    }

    private val DECIMAL_UNIT_WORDS = setOf(
        "kg", "kgs", "kilo", "kilos", "kilogramo", "kilogramos", "kilogram", "kilograms", "l", "lt", "lts", "litro",
        "litros", "liter", "liters", "litre", "litres", "lb", "lbs", "libra", "libras", "pound", "pounds",
    )
    private val THOUSANDS_UNIT_WORDS = setOf(
        "g", "gr", "grs", "gramo", "gramos", "gram", "grams", "ml", "mls", "mililitro", "mililitros", "cc", "kcal",
        "cal", "calorias", "caloria", "calories", "mg", "kj",
    )

    /**
     * Fin del número con separador de miles que empieza en [from] (parte entera hasta [wholeEnd]), o -1 si no lo es.
     * Grupos de exactamente tres cifras tras `.` o `,`: dos grupos o más siempre son miles; con uno, depende de la
     * unidad que sigue («1.000 g» = 1000; «1.500 kg» = 1,5) y, sin unidad, el punto es miles y la coma decimal.
     */
    private fun thousandsEnd(text: String, from: Int, wholeEnd: Int): Int {
        val wholeLength = wholeEnd - from
        if (wholeLength > 3 || text[from] == '0') return -1
        val n = text.length
        var k = wholeEnd
        var groups = 0
        while (k + 3 < n && (text[k] == '.' || text[k] == ',') &&
            text[k + 1] in '0'..'9' && text[k + 2] in '0'..'9' && text[k + 3] in '0'..'9'
        ) {
            k += 4
            groups++
        }
        if (groups == 0) return -1
        // Una cuarta cifra pegada («1.0005») no es un grupo de miles.
        if (k < n && text[k] in '0'..'9') return -1
        // «1.000.5»: tras los grupos hay otra parte decimal; no es un formato de miles.
        if (k + 1 < n && (text[k] == '.' || text[k] == ',') && text[k + 1] in '0'..'9') return -1
        if (groups >= 2) return k
        val separator = text[wholeEnd]
        var p = k
        while (p < n && text[p] == ' ') p++
        val word = StringBuilder()
        while (p < n && word.length < 12) {
            val base = baseLetter(text[p]) ?: break
            word.append(base)
            p++
        }
        val unit = word.toString()
        return when {
            unit in DECIMAL_UNIT_WORDS -> -1
            unit in THOUSANDS_UNIT_WORDS -> k
            separator == '.' -> k
            else -> -1
        }
    }

    /** Letra ASCII minúscula equivalente (sin tilde), o null si [c] no es una letra reducible a `a-z`. */
    private fun baseLetter(c: Char): Char? {
        if (c < '\u0080') {
            return when (c) {
                in 'a'..'z' -> c
                in 'A'..'Z' -> c + ('a' - 'A')
                else -> null
            }
        }
        if (!Character.isLetter(c)) return null
        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        val first = decomposed[0].lowercaseChar()
        return if (first in 'a'..'z') first else null
    }

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal = a.divide(b, 10, RoundingMode.HALF_UP)

    /** Cifra canónica: 4 decimales como máximo y sin ceros sobrantes. */
    private fun format(value: BigDecimal): String {
        val scaled = value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (scaled.signum() == 0) "0" else scaled.toPlainString()
    }

    // ─── Números hablados ──────────────────────────────────────────────

    private fun mergeSpokenNumbers(tokens: List<TokenV2>): List<TokenV2> {
        if (tokens.none { it.isWord }) return tokens
        val out = ArrayList<TokenV2>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.isWord) {
                val parsed = SpokenNumbers.parse(tokens, i)
                if (parsed != null) {
                    out += TokenV2(parsed.value.toString(), token.start, tokens[i + parsed.count - 1].end)
                    i += parsed.count
                    continue
                }
            }
            out += token
            i++
        }
        return out
    }

    // ─── «N y medio» ───────────────────────────────────────────────────

    private val HALF_UNIT_WORDS = setOf(
        "kilo", "kilos", "kg", "kgs", "litro", "litros", "l", "lt", "taza", "tazas", "vaso", "vasos", "cucharada",
        "cucharadas", "cucharadita", "cucharaditas", "plato", "platos", "porcion", "porciones", "lata", "latas",
        "botella", "botellas", "docena", "docenas", "rebanada", "rebanadas", "tajada", "tajadas", "trozo", "trozos",
        "pedazo", "pedazos", "copa", "copas", "pote", "potes", "paquete", "paquetes", "bolsa", "bolsas", "caja",
        "cajas", "cup", "cups", "glass", "glasses", "liter", "liters", "litre", "litres", "pound", "pounds", "lb",
        "lbs", "slice", "slices", "bottle", "bottles", "can", "cans", "tablespoon", "tablespoons", "teaspoon",
        "teaspoons", "tbsp", "tsp", "dozen", "bowl", "bowls", "mug", "mugs", "pint", "pints", "handful", "handfuls",
    )
    private val ARTICLE_ONE_WORDS = setOf("un", "una", "uno", "a", "an", "one")
    private val CLOCK_WORDS = setOf("las", "la", "hora", "at")

    /** Cuántos tokens ocupa «y medio»/«y media»/«and a half»/«and half» desde [k]; 0 si no están. */
    private fun halfLength(tokens: List<TokenV2>, k: Int): Int {
        val first = tokens.getOrNull(k)?.takeIf { it.isWord }?.norm ?: return 0
        val second = tokens.getOrNull(k + 1)?.takeIf { it.isWord }?.norm ?: return 0
        if (first == "y" && (second == "medio" || second == "media")) return 2
        if (first == "and") {
            if (second == "half") return 2
            val third = tokens.getOrNull(k + 2)?.takeIf { it.isWord }?.norm
            if (second == "a" && third == "half") return 3
        }
        return 0
    }

    /** Valor del número en [token] (cifra ya normalizada o artículo «un/a/one»), o null si no sirve de base. */
    private fun halfBase(token: TokenV2): Double? = when {
        token.isNumber -> token.norm.toDoubleOrNull()?.takeIf { it >= 1.0 && it <= 99.0 && it == Math.floor(it) }
        token.isWord && token.norm in ARTICLE_ONE_WORDS -> 1.0
        else -> null
    }

    private fun mergeHalves(tokens: List<TokenV2>): List<TokenV2> {
        if (tokens.size < 3 || tokens.none { it.isWord && (it.norm == "medio" || it.norm == "media" || it.norm == "half") }) {
            return tokens
        }
        val out = ArrayList<TokenV2>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val base = halfBase(token)
            // «a las 2 y media» es una hora, no una cantidad.
            val isClock = out.lastOrNull()?.let { it.isWord && it.norm in CLOCK_WORDS } == true
            // «dos y medio», «una y media», «one and a half».
            if (base != null && !isClock) {
                val half = halfLength(tokens, i + 1)
                // Un artículo suelto solo vale como «uno» si es «uno/una/one» (no «a/an/un»: «a and half» no existe).
                val articleOk = !token.isWord || token.norm == "uno" || token.norm == "una" || token.norm == "one"
                if (half > 0 && articleOk) {
                    out += TokenV2(format(BigDecimal(base + 0.5)), token.start, tokens[i + half].end)
                    i += half + 1
                    continue
                }
                // «un litro y medio», «2 tazas y media», «a cup and a half».
                val unit = tokens.getOrNull(i + 1)?.takeIf { it.isWord && it.norm in HALF_UNIT_WORDS }
                val unitHalf = if (unit != null) halfLength(tokens, i + 2) else 0
                if (unit != null && unitHalf > 0) {
                    out += TokenV2(format(BigDecimal(base + 0.5)), token.start, token.end)
                    out += TokenV2(unit.norm, unit.start, tokens[i + 1 + unitHalf].end)
                    i += unitHalf + 2
                    continue
                }
            }
            // «kilo y medio», «litro y medio», «vaso y medio»: sin número delante vale uno y medio.
            if (token.isWord && token.norm in HALF_UNIT_WORDS) {
                val half = halfLength(tokens, i + 1)
                val before = out.lastOrNull()
                val precededByNumber = before != null && (before.isNumber || (before.isWord && before.norm in ARTICLE_ONE_WORDS))
                if (half > 0 && !precededByNumber) {
                    out += TokenV2("1.5", token.start, token.end)
                    out += TokenV2(token.norm, token.start, tokens[i + half].end)
                    i += half + 1
                    continue
                }
            }
            out += token
            i++
        }
        return out
    }

    // ─── «la mitad de un…» ─────────────────────────────────────────────

    private val FRACTION_WORDS = setOf(
        "mitad", "tercio", "tercios", "cuarto", "cuartos", "quinto", "quintos", "sexto", "octavo", "half", "third",
        "thirds", "quarter", "quarters", "fifth", "eighth",
    )
    private val FRACTION_OF = setOf("de", "of")
    private val FRACTION_ARTICLES = setOf("un", "una", "el", "la", "este", "esta", "mi", "a", "an", "the", "my", "this")

    /**
     * «la mitad de una pizza», «un tercio de la bandeja», «half of a pizza»: se quitan el «de» y el artículo, de modo
     * que la fracción afecte a la cosa entera (si no, el «una» de después se lleva la cantidad: salía 1 en vez de 0,5).
     */
    private fun dropFractionOf(tokens: List<TokenV2>): List<TokenV2> {
        if (tokens.size < 3 || tokens.none { it.isWord && it.norm in FRACTION_WORDS }) return tokens
        val out = ArrayList<TokenV2>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            out += token
            // «la mitad del tarro»: el «del» ya trae el artículo.
            if (token.isWord && token.norm in FRACTION_WORDS &&
                tokens.getOrNull(i + 1)?.let { it.isWord && it.norm == "del" } == true &&
                tokens.getOrNull(i + 2)?.isWord == true
            ) {
                i += 2
                continue
            }
            if (token.isWord && token.norm in FRACTION_WORDS &&
                tokens.getOrNull(i + 1)?.let { it.isWord && it.norm in FRACTION_OF } == true &&
                tokens.getOrNull(i + 2)?.let { it.isWord && it.norm in FRACTION_ARTICLES } == true &&
                tokens.getOrNull(i + 3)?.isWord == true
            ) {
                i += 3
            } else {
                i++
            }
        }
        return out
    }

    private class Spoken(val value: Long, val count: Int)

    private class Part(val value: Int, val next: Int)

    private object SpokenNumbers {
        // Sin ambigüedad: valen solos. «un/una/uno» y «once» (la merienda chilena) solo cuentan dentro de un compuesto.
        private val ES_SMALL = mapOf(
            "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
            "diez" to 10, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
            "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20,
            "veintiuno" to 21, "veintiun" to 21, "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23,
            "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27,
            "veintiocho" to 28, "veintinueve" to 29,
        )
        private val ES_CONTEXT_ONLY = mapOf("uno" to 1, "un" to 1, "una" to 1, "once" to 11)
        private val ES_UNITS = mapOf(
            "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
            "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        )
        private val ES_TENS = mapOf(
            "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
            "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        )
        private val ES_HUNDREDS = mapOf(
            "cien" to 100, "ciento" to 100,
            "doscientos" to 200, "doscientas" to 200, "trescientos" to 300, "trescientas" to 300,
            "docientos" to 200, "docientas" to 200, "trecientos" to 300, "trecientas" to 300,
            "seicientos" to 600, "seicientas" to 600, "ochocientos" to 800,
            "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500, "quinientas" to 500,
            "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700, "setecientas" to 700,
            "ochocientas" to 800, "novecientos" to 900, "novecientas" to 900,
        )

        private val EN_SMALL = mapOf(
            "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
            "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
            "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        )
        private val EN_UNITS = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
            "eight" to 8, "nine" to 9,
        )
        private val EN_TENS = mapOf(
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        )

        fun parse(tokens: List<TokenV2>, i: Int): Spoken? {
            val word = tokens[i].norm
            return when {
                word in ES_SMALL || word in ES_TENS || word in ES_HUNDREDS || word == "mil" -> parseEs(tokens, i)
                word in EN_SMALL || word in EN_TENS || word == "hundred" || word == "thousand" || word == "one" ->
                    parseEn(tokens, i)
                else -> null
            }
        }

        private fun word(tokens: List<TokenV2>, k: Int): String? = tokens.getOrNull(k)?.takeIf { it.isWord }?.norm

        // ─── Español ───────────────────────────────────────────────────

        /** Cardinal < 1000 desde [i]. [inside] permite «un/una/uno/once» (ya hay un número antes). */
        private fun esBelowThousand(tokens: List<TokenV2>, i: Int, inside: Boolean): Part? {
            var j = i
            var value = 0
            var any = false
            ES_HUNDREDS[word(tokens, j)]?.let { value += it; j++; any = true }
            val w = word(tokens, j)
            if (w != null) {
                val small = ES_SMALL[w] ?: if (any || inside) ES_CONTEXT_ONLY[w] else null
                if (small != null) {
                    value += small
                    j++
                    any = true
                } else {
                    val tens = ES_TENS[w]
                    if (tens != null) {
                        value += tens
                        j++
                        any = true
                        val unit = if (word(tokens, j) == "y") ES_UNITS[word(tokens, j + 1)] else null
                        if (unit != null) {
                            value += unit
                            j += 2
                        }
                    }
                }
            }
            return if (any) Part(value, j) else null
        }

        private fun parseEs(tokens: List<TokenV2>, i: Int): Spoken? {
            val head = esBelowThousand(tokens, i, inside = false)
            val thousands = when {
                head != null && word(tokens, head.next) == "mil" -> Part(head.value * 1000, head.next + 1)
                head == null && word(tokens, i) == "mil" -> Part(1000, i + 1)
                else -> null
            }
            if (thousands == null) return head?.let { Spoken(it.value.toLong(), it.next - i) }
            var k = thousands.next
            if (word(tokens, k) == "y" && esBelowThousand(tokens, k + 1, inside = true) != null) k++
            val rest = esBelowThousand(tokens, k, inside = true)
            val total = thousands.value + (rest?.value ?: 0)
            return Spoken(total.toLong(), (rest?.next ?: thousands.next) - i)
        }

        // ─── Inglés ────────────────────────────────────────────────────

        private fun enBelowHundred(tokens: List<TokenV2>, i: Int, allowOne: Boolean): Part? {
            val w = word(tokens, i) ?: return null
            val small = EN_SMALL[w] ?: if (allowOne && w == "one") 1 else null
            if (small != null) return Part(small, i + 1)
            val tens = EN_TENS[w] ?: return null
            val unit = EN_UNITS[word(tokens, i + 1)]
            return if (unit != null) Part(tens + unit, i + 2) else Part(tens, i + 1)
        }

        /** Resto tras «hundred»/«thousand»: opcional «and» y un cardinal < 100. */
        private fun enTail(tokens: List<TokenV2>, from: Int, value: Int): Part {
            var k = from
            if (word(tokens, k) == "and" && enBelowHundred(tokens, k + 1, allowOne = true) != null) k++
            val rest = enBelowHundred(tokens, k, allowOne = true) ?: return Part(value, from)
            return Part(value + rest.value, rest.next)
        }

        private fun enBelowThousand(tokens: List<TokenV2>, i: Int, inside: Boolean): Part? {
            if (word(tokens, i) == "hundred") return enTail(tokens, i + 1, 100)
            val following = word(tokens, i + 1)
            val head = enBelowHundred(tokens, i, allowOne = inside || following == "hundred" || following == "thousand")
                ?: return null
            if (word(tokens, head.next) == "hundred") return enTail(tokens, head.next + 1, head.value * 100)
            return head
        }

        private fun parseEn(tokens: List<TokenV2>, i: Int): Spoken? {
            val head = enBelowThousand(tokens, i, inside = false)
            val thousands = when {
                head != null && word(tokens, head.next) == "thousand" -> Part(head.value * 1000, head.next + 1)
                head == null && word(tokens, i) == "thousand" -> Part(1000, i + 1)
                else -> null
            }
            if (thousands == null) return head?.let { Spoken(it.value.toLong(), it.next - i) }
            var k = thousands.next
            if (word(tokens, k) == "and" && enBelowThousand(tokens, k + 1, inside = true) != null) k++
            val rest = enBelowThousand(tokens, k, inside = true)
            val total = thousands.value + (rest?.value ?: 0)
            return Spoken(total.toLong(), (rest?.next ?: thousands.next) - i)
        }
    }
}
