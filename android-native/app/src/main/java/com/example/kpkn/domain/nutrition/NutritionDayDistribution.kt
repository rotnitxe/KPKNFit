package com.example.kpkn.domain.nutrition

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/** Variante de una misma sesión. Solo UNA variante cuenta por sesión. */
enum class SessionVariant { A, B, C, D }

/**
 * Sesión real planificada para una fecha con su gasto previsto por variante.
 * Una variante sin estimación (clave ausente) es «no estimable» y nunca debe
 * confundirse con un descanso.
 */
data class PlannedSessionLoad(
    val sessionId: String,
    val date: LocalDate,
    /**
     * Gasto previsto por variante. B/C/D son alternativas de A: nunca se suman
     * entre sí, solo cuenta la variante elegida (o la A si no hay elección).
     */
    val variantEstimatesKcal: Map<SessionVariant, Double> = emptyMap(),
    /** Elección planificada de variante; null = sin elección → cuenta la A. */
    val chosenVariant: SessionVariant? = null,
    /** Sesión opcional: solo cuenta si está confirmada para esa fecha. */
    val optional: Boolean = false,
    val confirmedForDate: Boolean = false,
)

/** Gasto de entrenamiento previsto para una fecha. */
sealed interface DayExpenditure {
    /** Sin sesión: gasto de entrenamiento 0 (descanso). */
    object Rest : DayExpenditure

    /** Sesión(es) estimadas: gasto previsto total del día. */
    data class Estimated(val kcal: Double) : DayExpenditure

    /** Hay sesión pero no todo es estimable: gasto desconocido. NO es descanso. */
    object NotEstimable : DayExpenditure
}

/** Estado explícito del reparto de objetivos por fecha. */
enum class NutritionDistributionStatus {
    /** Reparto variable T_i = B + α·(E_i − Ē) con α > 0. */
    VARIABLE,

    /** Objetivos uniformes elegidos por el usuario. */
    UNIFORM_BY_CHOICE,

    /** Reparto variable que acaba uniforme porque los límites llevaron α a 0. */
    UNIFORM_BY_LIMITS,

    /** Sesión no estimable: uniforme provisional hasta estimar el gasto. */
    PROVISIONAL_UNIFORM,

    /** Sin solución válida: se devuelve el reparto vigente sin mutarlo. */
    KEPT_PREVIOUS,
}

data class NutritionDayDistributionInput(
    /** Conjunto a repartir (F): fechas sin objetivo fijado. */
    val futureDates: List<LocalDate>,
    /**
     * Gasto previsto por fecha. Una fecha ausente es descanso (E = 0); para
     * distinguir «no estimable» de descanso construye el mapa con
     * [NutritionDayDistribution.sessionExpendituresByDate].
     */
    val expenditures: Map<LocalDate, DayExpenditure> = emptyMap(),
    /** Objetivo calórico diario medio confirmado (B). */
    val dailyMeanKcal: Double,
    /** Presupuesto vigente del periodo; por defecto 7·B (semana completa). */
    val periodBudgetKcal: Int = (dailyMeanKcal * 7.0).roundToInt(),
    /**
     * Objetivos ya fijados. Nunca se mueven y consumen presupuesto:
     * R = [periodBudgetKcal] − Σ fijados.
     */
    val fixedTargets: Map<LocalDate, Int> = emptyMap(),
    /** Límites aplicables por fecha ([calorieBoundsFor]); null = sin límites. */
    val bounds: IntRange? = null,
    /** Intensidad común inicial α (1.0 por defecto). */
    val alpha: Double = 1.0,
    /** El usuario eligió reparto uniforme. */
    val uniformByChoice: Boolean = false,
    /**
     * Reparto vigente del periodo (solo las fechas movibles), para devolverlo
     * intacto con [NutritionDistributionStatus.KEPT_PREVIOUS].
     */
    val previousTargets: Map<LocalDate, Int> = emptyMap(),
)

data class NutritionDayDistributionResult(
    val status: NutritionDistributionStatus,
    /** Objetivos de las fechas repartidas (sin los ya fijados). */
    val targetsByDate: Map<LocalDate, Int>,
    /** Objetivos ya fijados, intactos. */
    val fixedTargets: Map<LocalDate, Int>,
    /** Base del reparto: B en semana completa o B_F = R/|F| en semana parcial. */
    val baseKcal: Double,
    /** Presupuesto que cuadra exactamente en [targetsByDate]: R = periodo − fijados. */
    val remainingBudgetKcal: Int,
    /** α común efectivamente usado tras el recorte por límites. */
    val alphaUsed: Double,
    /** Fechas futuras cuyo gasto sigue sin estimar. */
    val missingEstimateDates: List<LocalDate> = emptyList(),
) {
    /** Objetivos completos del periodo: fijados + repartidos. */
    fun allTargets(): Map<LocalDate, Int> = fixedTargets + targetsByDate
}

/** Objetivos de macros de un día, escalados desde la base del plan. */
data class NutritionDayMacroTarget(
    val calorieTargetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/**
 * Reparto del objetivo calórico diario alrededor de la media:
 *
 *   T_i = B + α·(E_i − Ē)
 *
 * con B = objetivo calórico diario medio confirmado, E_i = gasto previsto del
 * entrenamiento de la fecha, Ē = media de esos gastos y α = intensidad común
 * (inicialmente 1.0). El presupuesto es fijo: Σ T_i = 7·B en una semana
 * completa y el ejercicio NUNCA añade calorías al presupuesto. Si los límites
 * aplicables ([calorieBoundsFor]) se violan, α se reduce de forma común hasta
 * que todas las fechas quepan; jamás se recortan días por separado, porque el
 * residuo se redistribuye para conservar el presupuesto.
 */
object NutritionDayDistribution {
    private const val ALPHA_SEARCH_ITERATIONS = 60

    /**
     * Recuento de sesiones por fecha:
     * - Una sola variante por sesión (A si no hay elección planificada);
     *   B/C/D nunca se suman entre sí.
     * - Las sesiones opcionales solo cuentan si están confirmadas para esa fecha.
     * - La MISMA sesión puede repetirse en fechas distintas (p. ej. un el mismo
     *   `Session` planificado en dos semanas); se cuenta una vez POR fecha,
     *   nunca se colapsa a nivel global por `sessionId`.
     * - Dos sesiones reales distintas en el mismo día sí se suman.
     * - Una sesión no estimable marca la fecha como [DayExpenditure.NotEstimable],
     *   que NO equivale a descanso.
     */
    fun sessionExpendituresByDate(sessions: List<PlannedSessionLoad>): Map<LocalDate, DayExpenditure> =
        sessions
            .filter { !it.optional || it.confirmedForDate }
            .distinctBy { it.date to it.sessionId }
            .groupBy { it.date }
            .mapValues { (_, daySessions) ->
                val estimates = daySessions.map { session ->
                    session.variantEstimatesKcal[session.chosenVariant ?: SessionVariant.A]
                }
                if (estimates.any { estimate -> estimate == null || !estimate.isFinite() }) {
                    DayExpenditure.NotEstimable
                } else {
                    DayExpenditure.Estimated(estimates.filterNotNull().sum())
                }
            }

    fun distribute(input: NutritionDayDistributionInput): NutritionDayDistributionResult {
        val dates = input.futureDates.distinct().sorted()
        val fixed = input.fixedTargets.toSortedMap()
        val remainingBudget = input.periodBudgetKcal - input.fixedTargets.values.sum()
        if (dates.isEmpty()) {
            return NutritionDayDistributionResult(
                status = if (input.uniformByChoice) NutritionDistributionStatus.UNIFORM_BY_CHOICE else NutritionDistributionStatus.VARIABLE,
                targetsByDate = emptyMap(),
                fixedTargets = fixed,
                baseKcal = 0.0,
                remainingBudgetKcal = remainingBudget,
                alphaUsed = 0.0,
            )
        }
        // Semana completa: B_F = 7·B / 7 = B. Semana parcial: B_F = R / |F|.
        val base = remainingBudget.toDouble() / dates.size
        val missing = dates.filter { input.expenditures[it] === DayExpenditure.NotEstimable }
        fun uniformTargets(): Map<LocalDate, Int> =
            roundToBudget(dates, List(dates.size) { base }, remainingBudget)

        // Sesión no estimable ≠ descanso: con gasto desconocido no se reparte
        // sobre E = 0; se devuelve uniforme provisional con las fechas sin gasto.
        if (missing.isNotEmpty() && !input.uniformByChoice) {
            return NutritionDayDistributionResult(
                status = NutritionDistributionStatus.PROVISIONAL_UNIFORM,
                targetsByDate = uniformTargets(),
                fixedTargets = fixed,
                baseKcal = base,
                remainingBudgetKcal = remainingBudget,
                alphaUsed = 0.0,
                missingEstimateDates = missing,
            )
        }
        // Uniforme por elección del usuario: estado distinto del provisional.
        if (input.uniformByChoice) {
            return NutritionDayDistributionResult(
                status = NutritionDistributionStatus.UNIFORM_BY_CHOICE,
                targetsByDate = uniformTargets(),
                fixedTargets = fixed,
                baseKcal = base,
                remainingBudgetKcal = remainingBudget,
                alphaUsed = 0.0,
                missingEstimateDates = missing,
            )
        }

        val expenditureKcal = dates.map { (input.expenditures[it] as? DayExpenditure.Estimated)?.kcal ?: 0.0 }
        val mean = expenditureKcal.average()
        val deltas = expenditureKcal.map { it - mean }
        val alpha0 = if (input.alpha.isFinite() && input.alpha > 0.0) input.alpha else 0.0
        val bounds = input.bounds
        val alpha = if (bounds == null) {
            alpha0
        } else {
            maxFeasibleAlpha(dates, base, deltas, remainingBudget, bounds, alpha0)
        }
        if (alpha == null) {
            // Sin solución válida: el reparto vigente se devuelve sin mutarlo.
            return NutritionDayDistributionResult(
                status = NutritionDistributionStatus.KEPT_PREVIOUS,
                targetsByDate = input.previousTargets,
                fixedTargets = fixed,
                baseKcal = base,
                remainingBudgetKcal = remainingBudget,
                alphaUsed = 0.0,
                missingEstimateDates = missing,
            )
        }
        val targets = roundToBudget(dates, dates.indices.map { base + alpha * deltas[it] }, remainingBudget)
        // Reparto variable que acaba uniforme por límites: estado propio.
        val collapsedToUniform = targets == uniformTargets() && alpha < alpha0
        return NutritionDayDistributionResult(
            status = if (collapsedToUniform) NutritionDistributionStatus.UNIFORM_BY_LIMITS else NutritionDistributionStatus.VARIABLE,
            targetsByDate = targets,
            fixedTargets = fixed,
            baseKcal = base,
            remainingBudgetKcal = remainingBudget,
            alphaUsed = alpha,
            missingEstimateDates = missing,
        )
    }

    /**
     * Mayor α común factible en [0, alpha0] bajo los límites. Si ni siquiera el
     * uniforme (α = 0) cabe, no hay solución y se devuelve null.
     */
    private fun maxFeasibleAlpha(
        dates: List<LocalDate>,
        base: Double,
        deltas: List<Double>,
        budget: Int,
        bounds: IntRange,
        alpha0: Double,
    ): Double? {
        fun feasible(alpha: Double): Boolean =
            roundToBudget(dates, dates.indices.map { base + alpha * deltas[it] }, budget)
                .values
                .all { it in bounds }
        if (!feasible(0.0)) return null
        if (feasible(alpha0)) return alpha0
        var lo = 0.0
        var hi = alpha0
        repeat(ALPHA_SEARCH_ITERATIONS) {
            val mid = (lo + hi) / 2.0
            if (feasible(mid)) lo = mid else hi = mid
        }
        return if (lo < 1e-9) 0.0 else lo
    }

    /**
     * Redondeo determinista a kcal enteras con reparto del residuo por mayor
     * parte fraccionaria; empates por fecha ascendente (reparto estable). La
     * suma entera cuadra exactamente con [budget].
     */
    private fun roundToBudget(dates: List<LocalDate>, values: List<Double>, budget: Int): Map<LocalDate, Int> {
        val rounded = values.map { floor(it).toInt() }.toIntArray()
        var residual = budget - rounded.sum()
        val order = dates.indices.sortedWith(
            compareByDescending<Int> { values[it] - floor(values[it]) }.thenBy { dates[it] },
        )
        var step = 0
        while (residual > 0) {
            rounded[order[step % order.size]] += 1
            step++
            residual--
        }
        while (residual < 0) {
            rounded[order[order.size - 1 - (step % order.size)]] -= 1
            step++
            residual++
        }
        return dates.mapIndexed { index, date -> date to rounded[index] }.toMap()
    }
}

/**
 * Macros por fecha escalando los TRES desde la MISMA base con
 * [scaleMacrosToCalories], nunca encadenando el resultado del día anterior.
 *
 * Tolerancia documentada: el redondeo a gramos admite ~±2 kcal por día (los
 * tests existentes admiten ±4) y NO se promete igualdad Atwater exacta
 * universal entre los gramos redondeados y [NutritionDayMacroTarget.calorieTargetKcal].
 */
fun macroTargetsByDate(
    targetsByDate: Map<LocalDate, Int>,
    baseProteinG: Double,
    baseCarbsG: Double,
    baseFatG: Double,
): Map<LocalDate, NutritionDayMacroTarget> =
    targetsByDate.mapValues { (_, kcal) ->
        val (protein, carbs, fat) = scaleMacrosToCalories(baseProteinG, baseCarbsG, baseFatG, kcal)
        NutritionDayMacroTarget(
            calorieTargetKcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
        )
    }
