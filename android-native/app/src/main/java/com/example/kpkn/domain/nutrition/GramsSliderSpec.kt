package com.example.kpkn.domain.nutrition

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Reglas puras del control de gramos de una tarjeta de alimento (WP-U10 / C8).
 *
 * El tope del slider sale de un ANCLA estable ([anchorFor] + [maxFor]) que la tarjeta lee una
 * sola vez. Antes el tope seguía al valor arrastrado (`max(600, 2 * valor)`): por encima de
 * 300 g cada gramo movido movía también el tope y el pulgar se escapaba de forma exponencial.
 * También aquí viven las reglas del campo numérico que acompaña al slider.
 */
object GramsSliderSpec {

    /** Tope mínimo del slider: cubre cualquier porción cotidiana. */
    const val MIN_MAX_GRAMS = 600.0

    /** El tope sube en múltiplos de esta cantidad para que el extremo sea un número redondo. */
    const val MAX_STEP_GRAMS = 100.0

    /** Menor cantidad que acepta el control: 0 g no es un alimento registrable. */
    const val MIN_GRAMS = 1.0

    /** Mayor cantidad que acepta el campo numérico (10 kg por alimento). */
    const val MAX_TYPED_GRAMS = 10_000.0

    /** Largo máximo del texto del campo numérico (alcanza para «10000,5»). */
    const val MAX_INPUT_LENGTH = 7

    /** Absorbe el ruido de coma flotante (1500,0000000000002 * 2) sin subir un escalón de más. */
    private const val CEIL_TOLERANCE = 1e-9

    /**
     * Ancla del tope: la masa base de la tarjeta si la tiene; si no, los gramos actuales.
     * Se lee UNA vez por tarjeta; el valor que se arrastra nunca la mueve.
     */
    fun anchorFor(baseAmountGrams: Double?, currentGrams: Double): Double =
        baseAmountGrams?.takeIf { it.isFinite() && it > 0.0 } ?: currentGrams

    /** `max(600, ceilTo(ancla * 2, 100))`; un ancla inválida (NaN, infinito, <= 0) deja el piso de 600 g. */
    fun maxFor(anchorGrams: Double): Double {
        if (!anchorGrams.isFinite() || anchorGrams <= 0.0) return MIN_MAX_GRAMS
        return max(MIN_MAX_GRAMS, ceilTo(anchorGrams * 2.0, MAX_STEP_GRAMS))
    }

    /** Gramos enteros para mostrar y confirmar (el empate sube: 62,5 -> 63), nunca bajo [MIN_GRAMS]. */
    fun wholeGrams(grams: Double): Double =
        if (grams.isFinite()) grams.roundToLong().toDouble().coerceAtLeast(MIN_GRAMS) else MIN_GRAMS

    /**
     * Filtra lo que el usuario escribe en el campo numérico: solo dígitos y UN separador decimal
     * (`.` o `,`), porque ni todos los teclados respetan el tipo numérico ni «1,5» debe volverse 15.
     */
    fun sanitizeInput(raw: String): String {
        var separatorSeen = false
        val kept = StringBuilder()
        for (ch in raw) {
            when {
                ch in '0'..'9' -> kept.append(ch)
                (ch == '.' || ch == ',') && !separatorSeen -> {
                    separatorSeen = true
                    kept.append(ch)
                }
            }
        }
        return kept.toString().take(MAX_INPUT_LENGTH)
    }

    /**
     * Gramos enteros que representa el texto del campo, o null si no hay una cantidad válida (vacío,
     * solo separador, 0 g): el llamador conserva entonces el valor anterior. Por encima de
     * [MAX_TYPED_GRAMS] se recorta a ese máximo.
     */
    fun parseGrams(text: String): Double? {
        val value = sanitizeInput(text).replace(',', '.').toDoubleOrNull() ?: return null
        val rounded = value.roundToLong().toDouble()
        if (rounded < MIN_GRAMS) return null
        return rounded.coerceAtMost(MAX_TYPED_GRAMS)
    }

    private fun ceilTo(value: Double, step: Double): Double = ceil(value / step - CEIL_TOLERANCE) * step
}
