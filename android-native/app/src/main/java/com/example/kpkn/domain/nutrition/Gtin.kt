package com.example.kpkn.domain.nutrition

/**
 * Códigos de barras de producto (GTIN): EAN-13, EAN-8, UPC-A (12 dígitos, igual a un EAN-13 con un cero delante) y UPC-E
 * (8 dígitos comprimidos que se expanden a UPC-A). Puro: sin Android ni red.
 *
 * Reglas:
 * - Solo cuentan los dígitos; los espacios y los guiones se descartan. Cualquier otra cosa (letras, una dirección web de un
 *   QR, una cantidad de dígitos que no sea 8, 12 o 13) no es un código de producto.
 * - El último dígito es el de control (módulo 10, pesos 3 y 1 contando desde la derecha) y tiene que cuadrar.
 * - Un código de 8 dígitos es EAN-8 si su dígito de control cuadra como tal; si no, se lee como UPC-E (solo con sistema de
 *   numeración 0 o 1) y se valida por el UPC-A al que se expande. Si cuadran los dos, [parse] dice EAN-8 y [candidates] prueba
 *   las dos lecturas. La cámara no tiene esta duda: ML Kit informa del formato y el lector expande el UPC-E con [upcEToUpcA].
 * - La forma canónica ([normalize]) es siempre de 13 dígitos: así un mismo producto da el mismo texto lo lea el lector como
 *   EAN-13, como UPC-A o como UPC-E.
 */
object Gtin {

    enum class Format { EAN13, EAN8, UPCA, UPCE }

    /** Un código válido: su [format] de origen, los [digits] tal como se leyeron y la forma canónica de 13 dígitos. */
    data class Parsed(val format: Format, val digits: String, val ean13: String)

    /** Valida y clasifica [raw]; null si no es un GTIN de los cuatro formatos admitidos o su dígito de control no cuadra. */
    fun parse(raw: String): Parsed? {
        val digits = raw.filterNot { it.isWhitespace() || it == '-' }
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return null
        return when (digits.length) {
            13 -> if (checkDigitOk(digits)) Parsed(Format.EAN13, digits, digits) else null
            12 -> if (checkDigitOk(digits)) Parsed(Format.UPCA, digits, "0$digits") else null
            8 -> when {
                checkDigitOk(digits) -> Parsed(Format.EAN8, digits, digits.padStart(13, '0'))
                else -> expandUpcE(digits)?.let { Parsed(Format.UPCE, digits, "0$it") }
            }
            else -> null
        }
    }

    /** La forma canónica de 13 dígitos de [raw], o null si no es un código válido. */
    fun normalize(raw: String): String? = parse(raw)?.ean13

    fun isValid(raw: String): Boolean = parse(raw) != null

    /**
     * Las variantes con las que probar la búsqueda de [raw] en una base: Open Food Facts a veces guarda el código sin los ceros
     * de la izquierda o en su forma corta. Sin repetidos y sin variantes de otra longitud significativa:
     * - la canónica de 13 dígitos (siempre), la de 14 con un cero delante y la de 12 si el 13 empieza por cero (UPC-A);
     * - la de 8 si la forma de 13 empieza por cinco ceros (un EAN-8 con sus ceros delante);
     * - la misma sin ningún cero a la izquierda.
     * Nunca incluye el UPC-E tal como se leyó: sus 8 dígitos podrían coincidir con el EAN-8 de OTRO producto. Una forma de 13 que empieza
     * por cinco ceros procede de 8 dígitos que pueden ser también un UPC-E: lleva además las variantes de su expansión a UPC-A.
     * Solo depende de la forma canónica: el mismo producto da las mismas variantes lo teclee el usuario como lo teclee.
     * Vacía si [raw] no es un código válido.
     */
    fun candidates(raw: String): List<String> {
        val parsed = parse(raw) ?: return emptyList()
        val e = parsed.ean13
        // Cinco ceros delante de la forma de 13 vienen de un código de 8 dígitos (un EAN-8, o un UPC-E que cuadra como EAN-8). Se mira la
        // forma canónica y no el formato de origen: el mismo producto da las mismas variantes lo teclee el usuario como lo teclee.
        val deOcho = e.startsWith("00000")
        return buildList {
            add(e)
            add("0$e")
            if (e.startsWith("0")) add(e.drop(1))
            if (deOcho) add(e.takeLast(8))
            e.trimStart('0').takeIf { it.isNotEmpty() }?.let { add(it) }
            // Un código de 8 dígitos puede ser también un UPC-E (con compresión de 5 a 9 siempre cuadra como EAN-8): si el lector no
            // dijo cuál era, se prueban las dos lecturas.
            if (deOcho) {
                upcEToUpcA(e.takeLast(8))?.let { upcA ->
                    add("0$upcA")
                    add(upcA)
                    upcA.trimStart('0').takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        }.distinct()
    }

    /** Dígito de control de [body] (todos los dígitos menos el último), pesos 3 y 1 desde la derecha. */
    internal fun checkDigitFor(body: String): Int {
        var sum = 0
        body.reversed().forEachIndexed { index, c -> sum += (c - '0') * if (index % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10
    }

    private fun checkDigitOk(digits: String): Boolean =
        checkDigitFor(digits.dropLast(1)) == digits.last() - '0'

    /**
     * Expande un UPC-E de 8 dígitos (sistema de numeración 0 o 1, seis dígitos comprimidos y el de control) a su UPC-A de 12.
     * La cifra de compresión es el último de los seis (f): 0, 1 o 2 → «a b f 0 0 0 0 c d e»; 3 → «a b c 0 0 0 0 0 d e»;
     * 4 → «a b c d 0 0 0 0 0 e»; 5 a 9 → «a b c d e 0 0 0 0 f».
     */
    private fun expandUpcE(digits: String): String? = expandUpcEUnchecked(digits)?.takeIf { checkDigitOk(it) }

    /**
     * El UPC-A de 12 dígitos al que se expande un UPC-E de 8 (con su dígito de control comprobado), o null si [digits] no es un UPC-E
     * válido. Lo usa el lector de cámara: ML Kit dice que el código es UPC-E, así que se expande aquí y no queda ambigüedad con un
     * EAN-8 (con compresión de 5 a 9, todos los UPC-E cuadran también como EAN-8; con las demás, uno de cada cinco).
     */
    fun upcEToUpcA(digits: String): String? = if (digits.length == 8 && digits.all { it in '0'..'9' }) expandUpcE(digits) else null

    private fun expandUpcEUnchecked(digits: String): String? {
        val system = digits[0]
        if (system != '0' && system != '1') return null
        val a = digits[1]; val b = digits[2]; val c = digits[3]; val d = digits[4]; val e = digits[5]; val f = digits[6]
        val body = when (f) {
            '0', '1', '2' -> "$a$b$f" + "0000" + "$c$d$e"
            '3' -> "$a$b$c" + "00000" + "$d$e"
            '4' -> "$a$b$c$d" + "00000" + "$e"
            else -> "$a$b$c$d$e" + "0000" + "$f"
        }
        return system + body + digits[7]
    }
}
