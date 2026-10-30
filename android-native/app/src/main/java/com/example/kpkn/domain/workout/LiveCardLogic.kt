package com.example.kpkn.domain.workout

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pulido v1 · tarjeta en vivo (paquete `lv`). Lógica pura (sin Android ni Compose) del carrusel de repeticiones y del ROM, tal como las
 * fija el lienzo aprobado (`scenes-lv.js`: `lvCarousel` y `lvRom`). La pantalla solo dibuja lo que esto calcula.
 */

/** Zonas del ROM (decisión aprobada): menos de 65 % parcial, de 65 a 94 % casi completo, desde 95 % completo. */
enum class RomZone(val label: String, val representative: Int) {
    PARTIAL("Parcial", 50),
    ALMOST_FULL("Casi completo", 80),
    FULL("Completo", 100),
}

object LiveRom {
    /** Rango del deslizador (decisión aprobada): de 10 a 100 %, de 5 en 5. */
    const val MIN = 10
    const val MAX = 100
    const val STEP = 5

    /** Marcas imantadas del deslizador y de los tres botones de zona. */
    val MARKS: List<Int> = listOf(50, 80, 100)

    /** Distancia (en puntos) a la que una marca atrapa el valor. */
    const val MARK_SNAP = 3

    const val PARTIAL_BELOW = 65
    const val FULL_FROM = 95

    fun zone(value: Int): RomZone = when {
        value >= FULL_FROM -> RomZone.FULL
        value >= PARTIAL_BELOW -> RomZone.ALMOST_FULL
        else -> RomZone.PARTIAL
    }

    /** «80 % · Casi completo»: lo que dice la burbuja sobre el pulgar mientras se arrastra. */
    fun tipText(value: Int): String = "$value % · ${zone(value).label}"

    /** Valor que deja un arrastre o un toque: de 5 en 5, con las marcas 50/80/100 imantadas, siempre dentro de 10–100. */
    fun snap(raw: Float): Int {
        var v = ((raw / STEP).roundToInt() * STEP)
        for (mark in MARKS) if (abs(v - mark) <= MARK_SNAP) v = mark
        return v.coerceIn(MIN, MAX)
    }

    /** Valor de un campo escrito (el «ROM · %» exacto de la hoja): se admite cualquier entero de 10 a 100, sin redondear a 5. */
    fun fromTyped(value: Int): Int = value.coerceIn(MIN, MAX)

    /** Fracción 0..1 de [value] a lo largo del deslizador (sin los márgenes del pulgar). */
    fun fraction(value: Int): Float = (value.coerceIn(MIN, MAX) - MIN).toFloat() / (MAX - MIN).toFloat()

    /**
     * El ROM que guarda una serie. Solo se anota si el ejercicio lo registra (`Exercise.trackRom`); un valor que ya traía la serie
     * registrada se conserva aunque el ejercicio haya dejado de registrarlo (editar nunca borra un dato guardado).
     */
    fun recordable(trackRom: Boolean, value: Int?, alreadyRecorded: Int?): Int? =
        when {
            trackRom -> value
            alreadyRecorded != null -> value ?: alreadyRecorded
            else -> null
        }?.coerceIn(MIN, MAX)
}

/** Cómo se ve una cifra del carrusel según su distancia (en celdas) al centro. */
object RepsCarouselMath {
    /** Ancho de una celda del carrusel, en dp. */
    const val CELL_DP = 54

    /** Cifras que se pintan a cada lado del centro (más lejos no se dibuja nada). */
    const val VISIBLE_DISTANCE = 3.2f

    /** Tamaño relativo: 100 % en el centro, 64 % el primer vecino, 48 % el segundo y 38 % el tercero. */
    fun scale(distance: Float): Float {
        val d = abs(distance)
        return when {
            d <= 1f -> 1f - .36f * d
            d <= 2f -> .64f - .16f * (d - 1f)
            else -> .48f - .1f * (d - 2f)
        }
    }

    /** Opacidad: 1 en el centro, 62 % el primer vecino, 28 % el segundo y nada al tercero. */
    fun alpha(distance: Float): Float {
        val d = abs(distance)
        return when {
            d <= 1f -> 1f - .38f * d
            d <= 2f -> .62f - .34f * (d - 1f)
            else -> .28f - .28f * (d - 2f).coerceAtMost(1f)
        }.coerceIn(0f, 1f)
    }

    /** Las cifras fuera del rango del plan se atenúan hasta el 62 % de su opacidad en cuanto se alejan del centro. */
    fun outOfRangeAlpha(distance: Float, alpha: Float): Float {
        val k = (abs(distance) / .6f).coerceIn(0f, 1f)
        return alpha * (1f + (.62f - 1f) * k)
    }

    /**
     * Mezcla de la cifra del centro: 0 = tinta oscura sobre el bloque crema (cifra en el centro), 1 = el color de las cifras sueltas.
     * Entre 0,3 y 0,62 celdas de distancia pasa de una a otra.
     */
    fun lensBlend(distance: Float): Float = ((abs(distance) - .3f) / .32f).coerceIn(0f, 1f)

    fun isInRange(value: Int, min: Int?, max: Int?): Boolean =
        min != null && value >= min && (max == null || value <= max)

    /**
     * Riel del rango bajo las cifras, en dp medidos desde el centro del carrusel: [Span.startDp] es el borde izquierdo y [Span.widthDp]
     * el ancho. Rango = de la celda `min` a la celda `max` con 7 dp de margen; fijo = una marca corta bajo ese valor; AMRAP = desde el
     * mínimo hacia la derecha hasta el final de la lista.
     */
    data class Span(val startDp: Float, val widthDp: Float)

    fun railSpan(min: Int, max: Int?, amrap: Boolean, position: Float, lastValue: Int): Span {
        val start = (min - .5f - position) * CELL_DP + 7f
        val width = if (amrap || max == null) (lastValue - min + 1) * CELL_DP.toFloat() else (max - min + 1) * CELL_DP - 14f
        return Span(start, width)
    }

    /** Rótulo derecho de la cabecera: el texto y si se enciende con ✓ (dentro del plan). */
    data class Label(val text: String, val inPlan: Boolean)

    /**
     * «Plan 4–6» (con ✓ dentro del rango, «Plan 4–6 · −1» o «· +2» fuera), «Plan 5 · −1», «Mínimo 8» / «Mínimo 8 · −2» en un AMRAP y «Serie
     * fallida». Sin plan, [noPlan] (el texto que la tarjeta ya mostraba: «Sin mínimo», «0 reps permitido»…) o vacío.
     */
    fun label(value: Int, min: Int?, max: Int?, amrap: Boolean, failed: Boolean, noPlan: String = ""): Label {
        if (failed) return Label("Serie fallida", inPlan = false)
        if (min == null) return Label(noPlan, inPlan = false)
        if (amrap) {
            val delta = value - min
            return Label("Mínimo $min" + if (delta < 0) " · −${-delta}" else "", inPlan = delta >= 0)
        }
        val top = max ?: min
        val plan = if (min == top) min.toString() else "$min–$top"
        return when {
            value in min..top -> Label("Plan $plan", inPlan = true)
            value < min -> Label("Plan $plan · −${min - value}", inPlan = false)
            else -> Label("Plan $plan · +${value - top}", inPlan = false)
        }
    }

    /** Por debajo del plan: trazo del acento de la sesión bajo el bloque central (no en un AMRAP, que solo marca el mínimo). */
    fun belowPlan(value: Int, min: Int?, amrap: Boolean, failed: Boolean): Boolean = !amrap && !failed && min != null && value < min

    /** Última cifra del carrusel: 50 como mínimo, o el valor actual + 10, o el del plan (la regla de siempre de la lista). */
    fun lastValue(current: Int, ghost: Int?, minimum: Int = 50): Int = maxOf(minimum, current + 10, ghost ?: 0)

    /** Tope de la cifra en letra grande: con el 130 % de letra la cifra se capa a 1,15× (el bloque central no crece). */
    const val MAX_FONT_SCALE = 1.15f
}

/**
 * Calor del slider de esfuerzo (`lvFireAt` del lienzo): a partir del nivel 2 (RPE 8) el relleno se calienta de crema a melocotón y, en el
 * fallo, a ámbar claro. Los colores van como 0xRRGGBB y el dibujo los convierte a `Color`.
 */
object EffortHeat {
    /** Extremo caliente del relleno ([tipRgb]), su tono medio ([midRgb]), cuánto del tramo se desvanece ([tail]), resplandor en dp y su alfa. */
    data class Fire(val tipRgb: Int, val midRgb: Int, val tail: Float, val blurDp: Float, val alpha: Float)

    private class Row(val level: Float, val tip: Int, val mid: Int, val tail: Float, val blur: Float, val alpha: Float)

    private val rows = listOf(
        Row(2f, 0xECCFB4, 0xE9C9A8, .26f, 7f, .16f),
        Row(3f, 0xF4B88F, 0xF2B080, .40f, 10f, .28f),
        Row(4f, 0xF49A6E, 0xF7CF73, .54f, 13f, .42f),
        Row(5f, 0xFFF1C8, 0xF06A3E, .70f, 16f, .60f),
    )

    /** Mezcla canal a canal, redondeando (el `lvMix` del lienzo). */
    fun mixRgb(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** El calor del nivel [level] (0 a 5), o nulo por debajo de 2 (sin calor). */
    fun fireAt(level: Float): Fire? {
        if (level < 2f) return null
        for (i in 0 until rows.size - 1) {
            val a = rows[i]
            val b = rows[i + 1]
            if (level <= b.level) {
                val t = (level - a.level) / (b.level - a.level)
                return Fire(
                    tipRgb = mixRgb(a.tip, b.tip, t), midRgb = mixRgb(a.mid, b.mid, t),
                    tail = a.tail + (b.tail - a.tail) * t, blurDp = a.blur + (b.blur - a.blur) * t, alpha = a.alpha + (b.alpha - a.alpha) * t,
                )
            }
        }
        val z = rows.last()
        return Fire(z.tip, z.mid, z.tail, z.blur, z.alpha)
    }

    /** Cuánto del calor llega al pulgar: de 0 en el nivel 1,5 a 1 en el 4,5. */
    fun hot(level: Float, hasFire: Boolean): Float = if (hasFire) ((level - 1.5f) / 3f).coerceAtMost(1f) else 0f

    /** Mezcla del color del pulgar: crema en reposo, melocotón en el calor y amarillo claro en el fallo. */
    fun thumbMix(level: Float): Float = ((level - 1.5f) / 2.2f).coerceIn(0f, 1f)
}
