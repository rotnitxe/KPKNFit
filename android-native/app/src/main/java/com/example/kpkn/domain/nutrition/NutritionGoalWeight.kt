package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.PlanDirection
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Peso objetivo del alta de nutrición: límites de la regla por dirección, ritmo semanal del motor y estimación de
 * semanas y de fecha. Puro (sin Android): lo usan el paso del objetivo, el resultado y el hito de nutrición.
 */

/** Holgura de la estimación: el rango va de semanas / 1,15 a semanas · 1,15. */
const val GOAL_ESTIMATE_SPREAD = 1.15

/** Definir y volumen exigen al menos esta diferencia con el peso actual (kg). */
const val GOAL_MIN_DELTA_KG = 0.5

/** Definir llega como mucho hasta −40 % del peso actual. */
const val GOAL_DEFICIT_MAX_FRACTION = 0.40

/** Volumen llega como mucho hasta +25 % del peso actual. */
const val GOAL_SURPLUS_MAX_FRACTION = 0.25

/** Mantener: ±5 kg alrededor del peso actual. */
const val GOAL_MAINTENANCE_BAND_KG = 5.0

/** Límites canónicos de un peso (kg), los mismos que valida el alta. */
const val GOAL_WEIGHT_MIN_KG = 20.0
const val GOAL_WEIGHT_MAX_KG = 500.0

/** Más semanas que esto no son una estimación útil (la misma cota que [estimatePlanEndDate]). */
private const val GOAL_MAX_WEEKS = 520.0

private const val NO_CHANGE_EPSILON = 1e-6

private val MONTHS_ES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * Intervalo de pesos objetivo admisibles (kg) según la dirección: Definir solo por debajo del actual (−0,5 kg como
 * mínimo, hasta −40 %), Volumen solo por encima (+0,5 kg, hasta +25 %) y Mantener ±5 kg. Sin dirección cuenta como
 * Mantener, igual que la preparación del plan. Null sin peso actual válido o si el intervalo queda vacío.
 */
fun goalWeightWindow(direction: PlanDirection?, currentKg: Double): ClosedFloatingPointRange<Double>? {
    if (!currentKg.isFinite() || currentKg <= 0.0) return null
    val (low, high) = when (direction) {
        PlanDirection.DEFICIT -> currentKg * (1.0 - GOAL_DEFICIT_MAX_FRACTION) to currentKg - GOAL_MIN_DELTA_KG
        PlanDirection.SURPLUS -> currentKg + GOAL_MIN_DELTA_KG to currentKg * (1.0 + GOAL_SURPLUS_MAX_FRACTION)
        else -> currentKg - GOAL_MAINTENANCE_BAND_KG to currentKg + GOAL_MAINTENANCE_BAND_KG
    }
    val first = low.coerceAtLeast(GOAL_WEIGHT_MIN_KG)
    val last = high.coerceAtMost(GOAL_WEIGHT_MAX_KG)
    return if (first <= last) first..last else null
}

/**
 * Ritmo semanal del motor en kg (magnitud) para una dirección y un preset: la fracción de [paceRateFor] por el peso
 * actual, como en `recommendedPlanValues`. Null en mantenimiento o sin peso válido.
 */
fun goalWeeklyChangeKg(direction: PlanDirection?, preset: WizardPacePreset, currentKg: Double): Double? {
    if (direction == null || !currentKg.isFinite() || currentKg <= 0.0) return null
    return paceRateFor(direction, preset)?.times(currentKg)
}

/**
 * Semanas hasta el peso objetivo al ritmo semanal [weeklyChangeKg] (kg, en magnitud): |Δ| / ritmo, con el rango de la
 * estimación (semanas / 1,15 … semanas · 1,15, redondeado y de al menos una semana). Null sin diferencia, sin ritmo
 * o si pasa de [GOAL_MAX_WEEKS].
 */
fun goalWeeksRange(currentKg: Double, targetKg: Double, weeklyChangeKg: Double): IntRange? {
    val delta = abs(targetKg - currentKg)
    val pace = abs(weeklyChangeKg)
    if (!delta.isFinite() || !pace.isFinite() || delta < NO_CHANGE_EPSILON || pace < NO_CHANGE_EPSILON) return null
    val weeks = delta / pace
    if (!weeks.isFinite() || weeks > GOAL_MAX_WEEKS) return null
    val low = (weeks / GOAL_ESTIMATE_SPREAD).roundToInt().coerceAtLeast(1)
    val high = (weeks * GOAL_ESTIMATE_SPREAD).roundToInt().coerceAtLeast(low)
    return low..high
}

/**
 * Fecha estimada de la meta con el mismo ±15 %, como frase («entre marzo y abril de 2027»). Usa [estimatePlanEndDate]
 * para los dos extremos: el ritmo ×1,15 da la fecha más pronta y el ÷1,15 la más tardía. Null si el peso no se mueve
 * hacia la meta con ese ritmo (o no hay ritmo): no se inventa una fecha que el plan no alcanza.
 */
fun goalDateSpanText(currentKg: Double, targetKg: Double, weeklyChangeKg: Double): String? {
    val towardGoal = (targetKg - currentKg) * weeklyChangeKg > NO_CHANGE_EPSILON
    if (!towardGoal) return null
    val earliest = estimatePlanEndDate(currentKg, targetKg, weeklyChangeKg * GOAL_ESTIMATE_SPREAD)
        ?.let { LocalDate.parse(it) } ?: return null
    val latest = estimatePlanEndDate(currentKg, targetKg, weeklyChangeKg / GOAL_ESTIMATE_SPREAD)
        ?.let { LocalDate.parse(it) } ?: return null
    return formatGoalMonthSpan(earliest, latest)
}

/** Meses de dos fechas en español: «en marzo de 2027», «entre marzo y abril de 2027», «entre diciembre de 2026 y enero de 2027». */
fun formatGoalMonthSpan(from: LocalDate, to: LocalDate): String {
    val first = if (from <= to) from else to
    val last = if (from <= to) to else from
    val firstName = MONTHS_ES[first.monthValue - 1]
    val lastName = MONTHS_ES[last.monthValue - 1]
    return when {
        first.year == last.year && first.monthValue == last.monthValue -> "en $firstName de ${first.year}"
        first.year == last.year -> "entre $firstName y $lastName de ${first.year}"
        else -> "entre $firstName de ${first.year} y $lastName de ${last.year}"
    }
}

/**
 * Peso objetivo guardado, en kg. El texto va en la unidad con la que se escribió (`weightUnit` del borrador
 * nutricional), igual que lo lee el motor; null si no hay un valor válido.
 */
fun nutritionTargetKgOf(draft: NutritionWizardDraft?): Double? {
    val nutrition = draft ?: return null
    val text = nutrition.targetWeightText.takeIf { it.isNotBlank() } ?: return null
    val value = parseLocalizedNumber(text) ?: return null
    return kilogramsFromInput(value, nutrition.weightUnit)
        ?.takeIf { it in GOAL_WEIGHT_MIN_KG..GOAL_WEIGHT_MAX_KG }
}

/** Una meta de peso cambia el resultado y la celebración solo con Definir o Volumen (Mantener no tiene ritmo). */
fun nutritionGoalActive(direction: PlanDirection?, targetKg: Double?): Boolean =
    targetKg != null && (direction == PlanDirection.DEFICIT || direction == PlanDirection.SURPLUS)
