package com.example.kpkn.screens.onboarding.design.entreno

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Matemática pura de la regla de marcas («¿Conoces tus marcas?»), sin Compose para poder probarla en JVM.
 *
 * Lo que se guarda es SIEMPRE kg. La unidad ([UNIT_KG] o [UNIT_LB]) solo decide cómo se muestra y en qué
 * escalones se arrastra: de 2,5 en 2,5 kg o de 5 en 5 lb. Una marca declarada en lb se convierte a kg una sola vez
 * ([snapKg]) y desde entonces es un kg normal: cambiar de unidad reexpresa el mismo kg sin tocarlo.
 *
 * Rango de la regla: 20–400 kg o 44–880 lb (los mismos extremos vistos desde la otra unidad). Marcas cada 1
 * kg/lb, intermedias cada [MID_EVERY] y largas, con número, cada [LONG_EVERY].
 */
object MarksMath {
    const val UNIT_KG = "kg"
    const val UNIT_LB = "lb"

    /** Libras por kilo exactas (definición internacional de la libra). */
    const val KG_PER_LB = 0.45359237

    const val MIN_KG = 20.0
    const val MAX_KG = 400.0
    const val MIN_LB = 44.0
    const val MAX_LB = 880.0

    /** Escalón al arrastrar, en la unidad mostrada. */
    const val STEP_KG = 2.5
    const val STEP_LB = 5.0

    /** Cada cuántas marcas hay una intermedia y una larga (con número). */
    const val MID_EVERY = 5
    const val LONG_EVERY = 10

    /** Tipo de marca de la regla. */
    enum class Tick { SHORT, MID, LONG }

    // ---------------------------------------------------------------- unidad

    /** `true` si [unit] es libras (sin distinguir mayúsculas). Cualquier otra cosa se trata como kg. */
    fun isLb(unit: String): Boolean = unit.trim().equals(UNIT_LB, ignoreCase = true)

    /** La unidad en su forma canónica: «kg» o «lb». */
    fun normalizeUnit(unit: String): String = if (isLb(unit)) UNIT_LB else UNIT_KG

    /** La otra unidad (para el conmutador). */
    fun otherUnit(unit: String): String = if (isLb(unit)) UNIT_KG else UNIT_LB

    // ---------------------------------------------------------------- conversión

    fun kgToLb(kg: Double): Double = kg / KG_PER_LB

    fun lbToKg(lb: Double): Double = lb * KG_PER_LB

    /** Un peso en kg expresado en [unit], sin redondear. */
    fun toDisplay(kg: Double, unit: String): Double = if (isLb(unit)) kgToLb(kg) else kg

    /** Un valor en [unit] expresado en kg, sin redondear. */
    fun toKg(value: Double, unit: String): Double = if (isLb(unit)) lbToKg(value) else value

    /**
     * Redondeo de presentación: una décima, a la mitad hacia arriba. Es lo que se muestra («314,2 lb») y lo que
     * garantiza que ida y vuelta kg → lb → kg no se aleje más de 0,05 (la mitad de una décima de lb pesa 0,023 kg).
     * Un valor no finito se devuelve intacto.
     */
    fun roundForDisplay(value: Double): Double =
        if (value.isFinite()) floor(value * 10.0 + 0.5) / 10.0 else value

    /** [toDisplay] con el redondeo de presentación. */
    fun displayValue(kg: Double, unit: String): Double = roundForDisplay(toDisplay(kg, unit))

    // ---------------------------------------------------------------- rango y escalón

    fun minDisplay(unit: String): Double = if (isLb(unit)) MIN_LB else MIN_KG

    fun maxDisplay(unit: String): Double = if (isLb(unit)) MAX_LB else MAX_KG

    fun step(unit: String): Double = if (isLb(unit)) STEP_LB else STEP_KG

    /**
     * Satura [value] (en [unit]) al rango de la regla. NaN y −∞ caen en el mínimo y +∞ en el máximo: un valor
     * imposible nunca llega a la regla.
     */
    fun clampDisplay(value: Double, unit: String): Double {
        val lo = minDisplay(unit)
        val hi = maxDisplay(unit)
        if (value.isNaN()) return lo
        return value.coerceIn(lo, hi)
    }

    /** Satura un peso en kg a 20–400. */
    fun clampKg(kg: Double): Double = if (kg.isNaN()) MIN_KG else kg.coerceIn(MIN_KG, MAX_KG)

    /**
     * Ajusta [value] (en [unit]) al escalón de la regla (2,5 kg o 5 lb), a la mitad hacia arriba, y lo satura al
     * primer y último escalón del rango. Con libras el escalón parte de 0: el primer valor posible es 45 lb (la regla
     * empieza en 44) y el último, 880.
     */
    fun snapMark(value: Double, unit: String): Double {
        val step = step(unit)
        val first = ceil(minDisplay(unit) / step) * step
        val last = floor(maxDisplay(unit) / step) * step
        if (value.isNaN()) return first
        return (floor(value / step + 0.5) * step).coerceIn(first, last)
    }

    /** Lo que se guarda al soltar la regla: [snapMark] convertido a kg. */
    fun snapKg(value: Double, unit: String): Double = toKg(snapMark(value, unit), unit)

    /** El kg guardado que más se parece a [kg] sobre el escalón de [unit] (sin salirse del rango). */
    fun snapFromKg(kg: Double, unit: String): Double = snapKg(toDisplay(clampKg(kg), unit), unit)

    // ---------------------------------------------------------------- texto

    /** Un número con coma decimal y a lo sumo un decimal: «142», «142,5». Un valor no finito es «—». */
    fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "—"
        val rounded = roundForDisplay(value)
        if (rounded == floor(rounded)) return rounded.toLong().toString()
        return String.format(Locale.ROOT, "%.1f", rounded).replace('.', ',')
    }

    /** La marca como se lee: «142,5 kg» o «315 lb». [kg] es el valor guardado. */
    fun formatMark(kg: Double, unit: String): String {
        val u = normalizeUnit(unit)
        if (!kg.isFinite()) return "— $u"
        return "${formatNumber(toDisplay(kg, u))} $u"
    }

    // ---------------------------------------------------------------- marcas de la regla

    /** Tipo de la marca del entero [n]: larga cada 10, intermedia cada 5, corta el resto. */
    fun tickOf(n: Int): Tick = when {
        n % LONG_EVERY == 0 -> Tick.LONG
        n % MID_EVERY == 0 -> Tick.MID
        else -> Tick.SHORT
    }

    /** Primera marca entera de la regla (20 kg, 44 lb). */
    fun firstTick(unit: String): Int = ceil(minDisplay(unit)).toInt()

    /** Última marca entera de la regla (400 kg, 880 lb). */
    fun lastTick(unit: String): Int = floor(maxDisplay(unit)).toInt()

    // ---------------------------------------------------------------- posición ↔ valor

    /**
     * Distancia (px) desde el extremo de abajo de la regla hasta [value] (en [unit]); [pxPerUnit] es el ancho de
     * una marca. Es el desplazamiento que deja ese valor bajo el cursor.
     */
    fun offsetOf(value: Double, unit: String, pxPerUnit: Double): Double =
        (clampDisplay(value, unit) - minDisplay(unit)) * pxPerUnit

    /** Inversa de [offsetOf]: el valor (en [unit]) que queda bajo el cursor con un desplazamiento de [offsetPx]. */
    fun valueAt(offsetPx: Double, unit: String, pxPerUnit: Double): Double {
        if (!(pxPerUnit > 0.0) || offsetPx.isNaN()) return minDisplay(unit)
        return clampDisplay(minDisplay(unit) + offsetPx / pxPerUnit, unit)
    }

    /** Ancho total de la regla (px) de extremo a extremo. */
    fun lengthPx(unit: String, pxPerUnit: Double): Double = (maxDisplay(unit) - minDisplay(unit)) * pxPerUnit
}
