package com.example.kpkn.domain.cardio

/**
 * Pulido v1 · CARDIO DEL EDITOR · punto 8: la escala de cada magnitud del selector único (duración, tiempos de fase, distancia, velocidad,
 * inclinación, cadencia, vatios, ritmo, RPE de bloque y objetivos kcal / metros). Es `CE_CFG` de `scenes-ce.js` en Kotlin puro.
 *
 * Una escala tiene posiciones enteras `n` (la regla se mueve de una en una) y cada `n` vale [step] unidades base: segundos en los tiempos,
 * décimas en la distancia, la velocidad y la inclinación, rpm, vatios, segundos por km. Así el valor real siempre sale entero y de la
 * regla: no hay valores intermedios (el lienzo dice «se ajusta a la regla en cada paso»).
 */
enum class CardioValueKind(
    val title: String,
    /** Primera y última posición de la regla. */
    val min: Int,
    val max: Int,
    /** Unidades base por cada posición `n`. */
    val step: Int,
    /** dp que mide cada marca de la regla (`px` del lienzo). */
    val tickDp: Int,
    /** Cada cuántas marcas hay una larga con número (`major`). */
    val major: Int,
    /** Texto de error al teclear fuera de rango. */
    val maxMessage: String,
    /** Atajos (posiciones `n`), en orden. */
    val shortcuts: List<Int>,
    /** Posición que significa «sin objetivo» (Libre, —) y su rótulo de atajo; null si la magnitud siempre tiene valor. */
    val noneN: Int? = null,
    val noneShortcut: String? = null,
    /** Con tres modos arriba (Con duración · Libre · Omitir): calentamiento y enfriamiento. */
    val hasModes: Boolean = false,
    /** Se teclea como minutos y segundos («1, 3, 0» = 1:30). */
    val typedAsMinSec: Boolean = false,
    /** Se teclea con coma decimal. */
    val typedDecimal: Boolean = false,
    /** Letras y cifras que admite el campo al teclear (el lienzo: 3 u 4 según el tipo). */
    val maxTypedDigits: Int = 3,
) {
    DURATION("Duración", 0, 180, 60, 9, 5, "Máximo 3 h", listOf(10, 15, 20, 30, 45, 60), noneN = 0, noneShortcut = "Libre"),
    WORK("Esfuerzo", 1, 120, 5, 8, 12, "Máximo 10 min", listOf(4, 6, 12, 24, 36), typedAsMinSec = true, maxTypedDigits = 4),
    REST("Pausa", 0, 120, 5, 8, 12, "Máximo 10 min", listOf(2, 6, 12, 24), typedAsMinSec = true, maxTypedDigits = 4),
    WARMUP("Calentamiento", 0, 80, 15, 8, 4, "Máximo 20 min", listOf(12, 20, 40), hasModes = true, typedAsMinSec = true, maxTypedDigits = 4),
    COOLDOWN("Enfriamiento", 0, 80, 15, 8, 4, "Máximo 20 min", listOf(8, 12, 20), hasModes = true, typedAsMinSec = true, maxTypedDigits = 4),
    REST_BETWEEN_SETS("Descanso entre series", 0, 40, 15, 10, 4, "Máximo 10 min", listOf(4, 8, 12), typedAsMinSec = true, maxTypedDigits = 4),
    BLOCK_DURATION("Duración del bloque", 1, 120, 15, 8, 12, "Máximo 30 min", listOf(2, 4, 8, 12, 20), typedAsMinSec = true, maxTypedDigits = 4),
    DISTANCE("Distancia", 0, 500, 1, 8, 10, "Máximo 50 km", listOf(10, 30, 50, 100), noneN = 0, noneShortcut = "Sin objetivo", typedDecimal = true, maxTypedDigits = 4),
    SPEED("Velocidad", 30, 250, 1, 8, 10, "Entre 3 y 25 km/h", listOf(60, 80, 100, 120, 140), typedDecimal = true, maxTypedDigits = 4),
    INCLINE("Inclinación", 0, 200, 1, 6, 10, "Máximo 20 %", listOf(0, 10, 20, 50, 80), typedDecimal = true, maxTypedDigits = 4),
    CADENCE("Cadencia", 40, 130, 1, 9, 10, "Entre 40 y 130 rpm", listOf(60, 70, 80, 90, 100)),
    WATTS("Vatios", 0, 100, 5, 9, 10, "Máximo 500 W", listOf(24, 32, 40, 50)),
    PACE("Ritmo objetivo", 60, 540, 1, 3, 30, "Entre 1:00 y 9:00 /km", listOf(300, 330, 360, 390, 420), noneN = 0, noneShortcut = "Sin objetivo", typedAsMinSec = true, maxTypedDigits = 4),
    BLOCK_RPE("RPE del bloque", 1, 10, 1, 24, 1, "Entre 1 y 10", listOf(3, 5, 7, 8, 9, 10)),
    WORK_KCAL("kcal por esfuerzo", 0, 80, 1, 8, 10, "Máximo 80 kcal", listOf(8, 12, 20, 30)),
    WORK_METERS("Metros por esfuerzo", 0, 40, 50, 12, 4, "Máximo 2.000 m", listOf(2, 4, 8, 10, 20)),
    ;

    /** Valor base (segundos, décimas, rpm, W…) de la posición [n]. */
    fun valueOf(n: Int): Int = n * step

    /** Posición más cercana de un valor base (se ajusta a la regla). */
    fun nOf(value: Int): Int = Math.round(value.toDouble() / step).toInt()

    /** La posición que hay que enseñar en la regla: la «sin objetivo» no está en el rango de la regla (ritmo), se queda en la primera marca. */
    fun rulerN(n: Int): Int = n.coerceIn(min, max)

    /** [n] dentro de la regla, o la posición «sin objetivo» si esa es. */
    fun clampN(n: Int): Int = if (noneN != null && n == noneN) n else n.coerceIn(min, max)

    /** Rótulo de la marca larga con número de la posición [n]. */
    fun tickLabel(n: Int): String = when (this) {
        DURATION, CADENCE, BLOCK_RPE, WORK_KCAL -> n.toString()
        WORK, REST -> mmss(n * 5)
        WARMUP, COOLDOWN, REST_BETWEEN_SETS, BLOCK_DURATION -> mmss(n * 15)
        DISTANCE, SPEED, INCLINE -> tenths(n)
        WATTS -> (n * 5).toString()
        PACE -> mmss(n)
        WORK_METERS -> (n * 50).toString()
    }

    /** Rótulo del atajo [n]. */
    fun shortcutLabel(n: Int): String = when {
        noneN != null && n == noneN -> noneShortcut ?: "—"
        this == DURATION -> "$n min"
        this == WORK || this == REST -> mmss(n * 5)
        this == WARMUP || this == COOLDOWN || this == REST_BETWEEN_SETS || this == BLOCK_DURATION -> mmss(n * 15)
        this == DISTANCE -> "${tenths(n)} km"
        this == SPEED -> "${tenths(n)} km/h"
        this == INCLINE -> "${tenths(n)} %"
        this == CADENCE -> "$n rpm"
        this == WATTS -> "${n * 5} W"
        this == PACE -> "${mmss(n)} /km"
        this == BLOCK_RPE -> n.toString()
        this == WORK_KCAL -> "$n kcal"
        else -> "${n * 50} m"
    }

    /** La cifra grande y su unidad (o, si empieza por letra, un texto como «Libre» con su frase). */
    fun big(n: Int): Pair<String, String> = when (this) {
        DURATION -> when {
            n == 0 -> "Libre" to "sin objetivo de tiempo"
            n < 60 -> n.toString() to "min"
            else -> "${n / 60}:${pad2(n % 60)}" to "h"
        }
        WORK, WARMUP, COOLDOWN, REST_BETWEEN_SETS, BLOCK_DURATION -> secBig(n * step)
        REST -> if (n == 0) "Sin pausa" to "" else secBig(n * step)
        DISTANCE -> if (n == 0) "—" to "sin objetivo de distancia" else tenthsFixed(n) to "km"
        SPEED -> tenthsFixed(n) to "km/h"
        INCLINE -> tenthsFixed(n) to "%"
        CADENCE -> n.toString() to "rpm"
        WATTS -> (n * 5).toString() to "W"
        PACE -> if (n == 0) "—" to "sin objetivo de ritmo" else mmss(n) to "/km"
        BLOCK_RPE -> n.toString() to CardioPrescriptionFormatter.rpeAnchor(n)
        WORK_KCAL -> if (n == 0) "—" to "sin objetivo de kcal" else n.toString() to "kcal"
        WORK_METERS -> if (n == 0) "—" to "sin objetivo de distancia" else (n * 50).toString() to "m"
    }

    /** La línea de ayuda cuando el valor pide una aclaración (solo la duración de una hora o más: «1 h 30 min»). */
    fun hint(n: Int): String = if (this == DURATION && n >= 60) CardioPrescriptionFormatter.formatDuration(n * 60) else ""

    /**
     * La posición que teclea [buffer] (solo cifras y una coma), o null si no es un número. Fuera de rango NO se recorta: el llamador compara
     * con [min] y [max] para decir «Máximo 3 h».
     */
    fun parseTyped(buffer: String): Int? {
        val text = buffer.trim()
        if (text.isEmpty()) return null
        return when {
            typedDecimal -> text.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 10).toInt() }
            typedAsMinSec -> text.toIntOrNull()?.let { v ->
                val seconds = (v / 100) * 60 + v % 100
                if (this == PACE) seconds else Math.round(seconds.toDouble() / step).toInt()
            }
            this == WATTS -> text.toIntOrNull()?.let { Math.round(it / 5.0).toInt() }
            this == WORK_METERS -> text.toIntOrNull()?.let { Math.round(it / 50.0).toInt() }
            else -> text.toIntOrNull()
        }
    }

    /** El texto tecleado en la cifra grande: en minutos y segundos se arma al estilo del microondas («130» → 1:30). */
    fun typedDisplay(buffer: String): String = if (typedAsMinSec) {
        val d = buffer.trimStart('0')
        when {
            d.length <= 2 -> d.ifEmpty { "0" }
            else -> "${d.dropLast(2)}:${d.takeLast(2)}"
        }
    } else buffer

    /** Dice si [n] cabe en la regla (o es la posición «sin objetivo»). */
    fun isInRange(n: Int): Boolean = (noneN != null && n == noneN) || n in min..max

    companion object {
        private fun pad2(v: Int) = v.toString().padStart(2, '0')

        /** `1:00`, `0:20`: «m:ss» como `KpknSetFormat.doseTime`. */
        fun mmss(seconds: Int): String = "${seconds / 60}:${pad2(seconds % 60)}"

        private fun secBig(seconds: Int): Pair<String, String> =
            if (seconds < 60) seconds.toString() to "s" else mmss(seconds) to "min"

        /** Décimas con coma y sin ceros de más: 60 → «6», 65 → «6,5». */
        fun tenths(t: Int): String = if (t % 10 == 0) (t / 10).toString() else "${t / 10},${t % 10}"

        /** Décimas con coma y SIEMPRE un decimal: 50 → «5,0». */
        fun tenthsFixed(t: Int): String = "${t / 10},${t % 10}"
    }
}
