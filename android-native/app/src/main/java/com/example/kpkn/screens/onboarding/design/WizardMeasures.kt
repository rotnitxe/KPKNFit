package com.example.kpkn.screens.onboarding.design

import kotlin.math.roundToInt

/** Unidad de visualización. El valor canónico persistido es siempre SI (kg, cm). */
enum class WizardMassUnit(val code: String, val label: String) {
    KG("kg", "Kilogramos"),
    LB("lb", "Libras"),
}

enum class WizardHeightUnit(val label: String) {
    CM("Centímetros"),
    FT_IN("Pies y pulgadas"),
}

/**
 * Escala pura de la regla de peso.
 *
 * La persistencia interna es siempre kg; la visualización puede ser kg o lb y
 * toda cifra mostrada conserva precisión de una décima. Es la misma conversión
 * que ya usaba `WizChatWeightScale`, para no introducir deriva entre pantallas.
 *
 * Ningún método lanza con entradas no finitas: la regla puede recibir estados
 * intermedios y el plan exige acotar extremos y no finitos al rango canónico.
 */
object WizardWeightScale {
    const val STEP = 0.1
    const val KG_PER_LB = 0.45359237
    const val MIN_KG = 20.0
    const val MAX_KG = 500.0

    fun toDisplay(kg: Double, unit: WizardMassUnit): Double = when (unit) {
        WizardMassUnit.KG -> kg
        WizardMassUnit.LB -> kg / KG_PER_LB
    }

    fun toKg(display: Double, unit: WizardMassUnit): Double = when (unit) {
        WizardMassUnit.KG -> display
        WizardMassUnit.LB -> display * KG_PER_LB
    }

    /** Redondea a una décima. Un no finito se devuelve intacto, sin lanzar. */
    fun snap(display: Double): Double =
        if (display.isFinite()) (display * 10.0).roundToInt() / 10.0 else display

    fun displayRange(unit: WizardMassUnit): ClosedFloatingPointRange<Double> {
        val low = kotlin.math.ceil(toDisplay(MIN_KG, unit) * 10.0) / 10.0
        val high = kotlin.math.floor(toDisplay(MAX_KG, unit) * 10.0) / 10.0
        return low..high
    }

    /**
     * Acota al rango canónico. NaN y −inf recaen en el mínimo del rango y +inf en
     * el máximo, de modo que un valor no finito nunca propaga un peso imposible.
     */
    fun clampDisplay(display: Double, unit: WizardMassUnit): Double {
        val range = displayRange(unit)
        if (!display.isFinite()) return if (display > 0) range.endInclusive else range.start
        return snap(display).coerceIn(range.start, range.endInclusive)
    }

    /** Formato de una décima con coma decimal, tal como se usa en toda la app. */
    fun format(display: Double): String = when {
        !display.isFinite() -> "—"
        display % 1.0 == 0.0 -> display.toInt().toString()
        else -> "%.1f".format(display).replace('.', ',')
    }

    fun formatWithUnit(kg: Double, unit: WizardMassUnit): String =
        "${format(snap(toDisplay(kg, unit)))} ${unit.code}"
}

/**
 * Escala pura de la rueda de altura.
 *
 * El canónico es cm entero (100–250, los mismos límites que valida la app). En
 * modo `FT_IN` los pasos son de **una pulgada redondeada al entero más cercano**:
 * truncar hacia abajo mostraba 5′ 8″ para 175 cm, que es 5′ 8,9″.
 *
 * Toda conversión se parte siempre del cm canónico, nunca de la conversión
 * anterior, así que alternar unidades no acumula deriva. 175 cm → 5′ 9″ → 175 cm.
 */
object WizardHeightScale {
    const val MIN_CM = 100
    const val MAX_CM = 250
    const val CM_PER_INCH = 2.54
    const val INCHES_PER_FOOT = 12

    fun clampCm(cm: Int): Int = cm.coerceIn(MIN_CM, MAX_CM)

    fun clampCm(cm: Double): Int = clampCm(kotlin.math.round(cm).toInt())

    /** Pulgadas enteras más cercanas a un cm canónico. */
    fun totalInches(cm: Int): Int = kotlin.math.round(clampCm(cm) / CM_PER_INCH).toInt()

    fun cmFromTotalInches(totalInches: Int): Int = clampCm((totalInches * CM_PER_INCH).roundToInt())

    fun feet(cm: Int): Int = totalInches(cm) / INCHES_PER_FOOT

    fun inchesRemainder(cm: Int): Int = totalInches(cm) % INCHES_PER_FOOT

    fun cmFromFeetInches(feet: Int, inches: Int): Int =
        cmFromTotalInches(feet * INCHES_PER_FOOT + inches)

    fun formatCm(cm: Int): String = clampCm(cm).toString()

    fun formatFtIn(cm: Int): String = "${feet(cm)}′ ${inchesRemainder(cm)}″"

    fun format(cm: Int, unit: WizardHeightUnit): String = when (unit) {
        WizardHeightUnit.CM -> formatCm(cm)
        WizardHeightUnit.FT_IN -> formatFtIn(cm)
    }

    fun stepCount(unit: WizardHeightUnit): Int = when (unit) {
        WizardHeightUnit.CM -> MAX_CM - MIN_CM + 1
        WizardHeightUnit.FT_IN -> totalInches(MAX_CM) - totalInches(MIN_CM) + 1
    }

    fun cmForStepIndex(index: Int, unit: WizardHeightUnit): Int = when (unit) {
        WizardHeightUnit.CM -> clampCm(MIN_CM + index)
        WizardHeightUnit.FT_IN -> cmFromTotalInches(totalInches(MIN_CM) + index)
    }

    /**
     * Rótulos de la rueda. En centímetros la referencia muestra la unidad en
     * cada valor (`173 cm`); en pies y pulgadas la notación ya la lleva.
     */
    fun labels(unit: WizardHeightUnit): List<String> =
        (0 until stepCount(unit)).map { index ->
            val cm = cmForStepIndex(index, unit)
            if (unit == WizardHeightUnit.CM) "${format(cm, unit)} cm" else format(cm, unit)
        }

    /** Índice de rueda más cercano a una altura, para fijar la posición inicial. */
    fun nearestStepIndex(cm: Int, unit: WizardHeightUnit): Int {
        val count = stepCount(unit)
        val index = when (unit) {
            WizardHeightUnit.CM -> clampCm(cm) - MIN_CM
            WizardHeightUnit.FT_IN -> totalInches(cm) - totalInches(MIN_CM)
        }
        return index.coerceIn(0, count - 1)
    }
}
