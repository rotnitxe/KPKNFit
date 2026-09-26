package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import java.time.LocalDate

/**
 * Planificador de la previsión semanal del reparto: decide QUÉ fechas se
 * actualizan, CUÁLES están fijas y con qué evidencia, y cómo se reescribe la
 * previsión versionada. No calcula calorías nuevas: delega en
 * [NutritionDayDistribution] (reparto) y en [macroTargetsByDate] (macros).
 *
 * Reglas:
 * - Las actualizaciones empiezan MAÑANA: hoy y el pasado solo se mueven si no
 *   hay evidencia; con snapshot del plan o con la previsión previa del plan se
 *   conservan tal cual (calorías Y macros).
 * - El presupuesto se preserva SOBRE EL PERIODO FIJO de la previsión vigente
 *   (nuna una ventana rolling «hoy..+6», que rompería la semana): el periodo
 *   solo avanza cuando se consumió entero.
 * - Sin solución válida ([NutritionDistributionStatus.KEPT_PREVIOUS]) no se
 *   reescribe nada: la semana vigente se conserva hasta la siguiente.
 */

/** Evidencia fijada de un día: calorías Y macros que no se mueven. */
data class NutritionFixedDayTarget(
    val calorieTargetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/**
 * Periodo (horizonte) de la previsión semanal para [today]:
 *
 * 1. Sin previsión previa → primera ventana `[today, today + windowDays)`.
 * 2. Previsión vigente → se conserva el MISMO horizonte mientras contenga
 *    HOY, incluido su **último día** (`prior.last == today`): borrarla
 *    reabriría la ventana y cambiaría el objetivo de HOY (el resolver
 *    caería al plan medio). Nunca se rueda a «hoy..+6» (eso partiría la semana).
 * 3. Periodo consumido SOLO con `prior.last < today` → siguiente periodo con el
 *    mismo ancla (día de la semana del primer día) y la misma longitud,
 *    empezando en la primera fecha ancla posterior al periodo anterior y
 *    avanzando semanas si hubo un hueco para que el periodo nuevo contenga hoy
 *    o empiece mañana.
 */
fun weeklyForecastPeriodFor(
    priorDays: List<WeeklyForecastDay>?,
    today: LocalDate,
    windowDays: Long = NutritionTrainingCalendarAdapter.DEFAULT_WINDOW_DAYS,
): List<LocalDate> {
    val prior = priorDays?.map { it.date }?.distinct()?.sorted().orEmpty()
    if (prior.isEmpty()) {
        return (0L until windowDays.coerceAtLeast(1L)).map { today.plusDays(it) }
    }
    // Vigente si el periodo aún cubre HOY (su último día incluido).
    if (!prior.last().isBefore(today)) return prior
    val anchor = prior.first().dayOfWeek
    val length = prior.size
    var start = prior.last().plusDays(1)
    while (start.dayOfWeek != anchor) start = start.plusDays(1)
    while (start.plusDays(length - 1L).isBefore(today)) start = start.plusDays(7L)
    return (0 until length).map { start.plusDays(it.toLong()) }
}

/**
 * Evidencia fijada de HOY y el PASADO dentro del periodo: snapshot histórico
 * del MISMO plan si existe; si no (o si le faltan macros), la previsión previa
 * del plan. Una fecha sin ninguna de las dos no está fijada: entra en el
 * reparto sin fabricarle un valor.
 *
 * @param planId identidad del plan dueño de la evidencia; null = sin evidencia.
 */
fun fixedWeeklyEvidenceFor(
    priorDays: List<WeeklyForecastDay>,
    today: LocalDate,
    period: List<LocalDate>,
    snapshots: List<DailyGoalSnapshot>,
    planId: String?,
): Map<LocalDate, NutritionFixedDayTarget> {
    if (planId == null) return emptyMap()
    val priorByDate = priorDays.associateBy { it.date }
    val snapshotsByDate = snapshots
        .filter { it.planId == planId }
        .associateBy { it.date }
    val fixed = linkedMapOf<LocalDate, NutritionFixedDayTarget>()
    for (date in period) {
        // Solo HOY y el PASADO: el futuro se reparte, no se fija.
        if (date.isAfter(today)) continue
        val snapshot = snapshotsByDate[date.toString()]
        val prior = priorByDate[date]
        val kcal = snapshot?.calorieTargetKcal ?: prior?.calorieTargetKcal ?: continue
        val protein = snapshot?.proteinGoalG ?: prior?.proteinG
        val carbs = snapshot?.carbGoalG ?: prior?.carbsG
        val fat = snapshot?.fatGoalG ?: prior?.fatG
        // Sin macros en ninguna fuente el día no tiene evidencia completa:
        // no se fija con valores fabricados.
        if (protein == null || carbs == null || fat == null) continue
        fixed[date] = NutritionFixedDayTarget(
            calorieTargetKcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
        )
    }
    return fixed
}

/**
 * Días finales de la previsión: los FIJADOS conservan sus calorías y SUS macros
 * (nunca se reescalan desde la base actual) y los repartidos escalan los TRES
 * macros desde la MISMA base con [macroTargetsByDate].
 */
fun forecastDaysFor(
    fixedEvidence: Map<LocalDate, NutritionFixedDayTarget>,
    distributed: Map<LocalDate, Int>,
    base: NutritionEditorBase,
): List<NutritionEditorDayTarget> {
    val scaled = macroTargetsByDate(
        targetsByDate = distributed,
        baseProteinG = base.proteinG.toDouble(),
        baseCarbsG = base.carbsG.toDouble(),
        baseFatG = base.fatG.toDouble(),
    )
    return (fixedEvidence.keys + distributed.keys)
        .distinct()
        .mapNotNull { date ->
            fixedEvidence[date]?.let {
                NutritionEditorDayTarget(date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG)
            } ?: scaled[date]?.let {
                NutritionEditorDayTarget(date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG)
            }
        }
        .sortedBy { it.date }
}

/** Entrada de la revisión de previsión que hace el coordinador fuera del editor. */
data class NutritionForecastRevisionInput(
    /** Plan activo con su previsión vigente. */
    val plan: NutritionPlan,
    /** Día del sistema (reloj inyectable en los tests). */
    val today: LocalDate,
    /** Gasto previsto por fecha resuelto por [NutritionTrainingCalendarAdapter]. */
    val expenditures: Map<LocalDate, DayExpenditure>,
    /** Snapshots históricos (evidencia fijada del mismo plan). */
    val snapshots: List<DailyGoalSnapshot> = emptyList(),
)

/**
 * Reescribe la previsión del plan para el periodo vigente. Devuelve el plan
 * revisado o null cuando NO hay nada que persistir:
 * - sin previsión previa (no hay horizonte que conservar),
 * - todas las fechas del periodo están fijas,
 * - sin solución válida (se conserva la semana vigente),
 * - o el resultado es idéntico al ya guardado (evita bucles de reescritura).
 *
 * Nunca toca snapshots, ni la ingesta, ni el gasto registrado: solo la previsión
 * de fechas futuras dentro del periodo.
 */
fun revisedWeeklyForecast(input: NutritionForecastRevisionInput): NutritionPlan? {
    val plan = input.plan
    val snapshot = plan.calculationSnapshot ?: return null
    val priorDocument = snapshot.inputs[WEEKLY_FORECAST_KEY]
        ?.let(::decodeWeeklyForecastDocument)
        ?: return null
    val prior = priorDocument.days
    if (prior.isEmpty()) return null
    val baseKcal = plan.calorieTarget
    if (baseKcal <= 0) return null

    val period = weeklyForecastPeriodFor(prior, input.today)
    val fixed = fixedWeeklyEvidenceFor(prior, input.today, period, input.snapshots, plan.id)
    val move = period.filterNot { it in fixed }
    if (move.isEmpty()) return null

    val distribution = NutritionDayDistribution.distribute(
        NutritionDayDistributionInput(
            futureDates = move,
            expenditures = input.expenditures,
            dailyMeanKcal = baseKcal.toDouble(),
            // Presupuesto del periodo FIJO: el reparto cuadra exacto aunque
            // hoy/pasado estén fijados (R = periodo − Σ fijados).
            periodBudgetKcal = period.size * baseKcal,
            fixedTargets = fixed.mapValues { it.value.calorieTargetKcal },
            bounds = boundsFor(plan),
            uniformByChoice = weeklyDistributionModeOf(snapshot.inputs) == NutritionWeeklyDistributionMode.UNIFORM,
            previousTargets = prior
                .filter { it.date in move }
                .associate { it.date to it.calorieTargetKcal },
        ),
    )
    // Sin solución válida: la semana vigente se conserva íntegra hasta la
    // siguiente (no se improvisan valores y no se reescribe la previsión).
    if (distribution.status == NutritionDistributionStatus.KEPT_PREVIOUS) return null

    val base = NutritionEditorBase(
        caloriesKcal = baseKcal,
        proteinG = plan.proteinGoal,
        carbsG = plan.carbGoal,
        fatG = plan.fatGoal,
    )
    val days = forecastDaysFor(fixed, distribution.targetsByDate, base)
    val storedByDate = prior.associate {
        it.date to NutritionEditorDayTarget(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG)
    }
    // Idéntico a lo guardado: no reescribir (misma revisión, cero bucles).
    if (days.associateBy { it.date } == storedByDate) return null

    val updatedInputs = snapshot.inputs + (WEEKLY_FORECAST_KEY to encodeWeeklyForecast(
        targets = days,
        revision = priorDocument.revision + 1,
        effectiveDate = period.first(),
    ))
    return plan.copy(calculationSnapshot = snapshot.copy(inputs = updatedInputs))
}

/** Límites calóricos del plan a partir de la EER guardada en su snapshot (sin fabricar). */
private fun boundsFor(plan: NutritionPlan): IntRange? {
    val direction = plan.direction ?: return null
    val eerKcal = plan.calculationSnapshot?.inputs?.get("eerKcal")?.toDoubleOrNull()
    return calorieBoundsFor(direction, eerKcal)
}

/** Modo de reparto semanal elegido y persistido en el plan. */
fun weeklyDistributionModeOf(inputs: Map<String, String>): NutritionWeeklyDistributionMode =
    inputs["weeklyDistribution"]
        ?.let { stored -> runCatching { NutritionWeeklyDistributionMode.valueOf(stored) }.getOrNull() }
        ?: NutritionWeeklyDistributionMode.VARIABLE
