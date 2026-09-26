package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.PlanDirection
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionDayDistributionTest {
    private val monday: LocalDate = LocalDate.of(2026, 9, 21)
    private val week: List<LocalDate> = (0L..6L).map { monday.plusDays(it) }
    private val base = 2_500.0
    private val weeklyBudget = 17_500

    private fun expenditures(values: List<Double?>): Map<LocalDate, DayExpenditure> =
        week.zip(values).toMap().mapValues { (_, kcal) ->
            if (kcal == null) DayExpenditure.Rest else DayExpenditure.Estimated(kcal)
        }

    private fun input(
        dates: List<LocalDate> = week,
        expenditures: Map<LocalDate, DayExpenditure> = emptyMap(),
        fixedTargets: Map<LocalDate, Int> = emptyMap(),
        periodBudgetKcal: Int = weeklyBudget,
        bounds: IntRange? = null,
        uniformByChoice: Boolean = false,
        previousTargets: Map<LocalDate, Int> = emptyMap(),
    ) = NutritionDayDistributionInput(
        futureDates = dates,
        expenditures = expenditures,
        dailyMeanKcal = base,
        periodBudgetKcal = periodBudgetKcal,
        fixedTargets = fixedTargets,
        bounds = bounds,
        uniformByChoice = uniformByChoice,
        previousTargets = previousTargets,
    )

    @Test
    fun `weekly targets sum exactly to seven times the base across expenditure patterns`() {
        val patterns = listOf(
            expenditures(listOf(null, null, null, null, null, null, null)),
            expenditures(listOf(500.0, null, 500.0, null, 500.0, null, 500.0)),
            expenditures(listOf(null, null, null, null, null, null, 2_100.0)),
            expenditures(listOf(120.5, 480.25, null, 75.75, 900.0, null, 133.5)),
        )
        for (pattern in patterns) {
            val result = NutritionDayDistribution.distribute(input(expenditures = pattern))
            assertEquals(7, result.targetsByDate.size)
            // Σ T_i = 7·B exacto con redondeo entero.
            assertEquals(weeklyBudget, result.targetsByDate.values.sum())
            assertEquals(7 * base.toInt(), result.targetsByDate.values.sum())
        }
    }

    @Test
    fun `training expenditure never adds calories to the weekly budget`() {
        val result = NutritionDayDistribution.distribute(
            input(expenditures = expenditures(listOf(1_200.0, 1_200.0, 1_200.0, null, null, null, null))),
        )
        assertEquals(weeklyBudget, result.targetsByDate.values.sum())
    }

    @Test
    fun `common alpha shrinks when a calorie bound applies and the budget is preserved`() {
        val bounds = calorieBoundsFor(PlanDirection.DEFICIT, 2_600.0)!!
        val result = NutritionDayDistribution.distribute(
            input(expenditures = expenditures(listOf(null, null, null, null, null, null, 2_100.0)), bounds = bounds),
        )
        assertEquals(NutritionDistributionStatus.VARIABLE, result.status)
        assertTrue(result.alphaUsed > 0.0 && result.alphaUsed < 1.0)
        assertTrue(result.targetsByDate.values.all { it in bounds })
        assertEquals(weeklyBudget, result.targetsByDate.values.sum())
        // α es COMÚN: todos los días se desplazan con el mismo α, sin recortes
        // individuales que pierdan presupuesto.
        val high = result.targetsByDate.getValue(week[6])
        val low = result.targetsByDate.getValue(week[0])
        assertTrue(high > low)
        assertTrue(abs((high - low) - result.alphaUsed * 2_100.0) <= 3.0)
    }

    @Test
    fun `an unestimable session is provisional uniform and never treated as zero expenditure`() {
        val sessions = week.mapIndexed { index, date ->
            if (index == 3) {
                PlannedSessionLoad(sessionId = "s$index", date = date, variantEstimatesKcal = emptyMap())
            } else {
                PlannedSessionLoad(sessionId = "s$index", date = date, variantEstimatesKcal = mapOf(SessionVariant.A to 600.0))
            }
        }
        val byDate = NutritionDayDistribution.sessionExpendituresByDate(sessions)
        assertEquals(DayExpenditure.NotEstimable, byDate[week[3]])

        val result = NutritionDayDistribution.distribute(input(expenditures = byDate))
        assertEquals(NutritionDistributionStatus.PROVISIONAL_UNIFORM, result.status)
        assertEquals(listOf(week[3]), result.missingEstimateDates)
        // Uniforme provisional T_i = B: no es un gasto 0 de descanso.
        assertEquals(2_500, result.targetsByDate.getValue(week[3]))
        assertEquals(weeklyBudget, result.targetsByDate.values.sum())
    }

    @Test
    fun `an unestimable session differs from a rest day`() {
        val day = week[2]
        val sessions = listOf(
            PlannedSessionLoad("s1", day, mapOf(SessionVariant.A to 500.0), chosenVariant = SessionVariant.C),
        )
        assertEquals(DayExpenditure.NotEstimable, NutritionDayDistribution.sessionExpendituresByDate(sessions)[day])
    }

    @Test
    fun `one session counts a single variant and optional sessions need confirmation`() {
        val day = week[1]
        val sessions = listOf(
            PlannedSessionLoad("s-a", day, mapOf(SessionVariant.A to 400.0, SessionVariant.B to 500.0, SessionVariant.C to 600.0, SessionVariant.D to 700.0)),
            PlannedSessionLoad("s-b", day, mapOf(SessionVariant.A to 300.0, SessionVariant.B to 450.0), chosenVariant = SessionVariant.B),
            PlannedSessionLoad("s-opt", day, mapOf(SessionVariant.A to 999.0), optional = true, confirmedForDate = false),
            PlannedSessionLoad("s-confirmed-opt", day, mapOf(SessionVariant.A to 250.0), optional = true, confirmedForDate = true),
        )
        // Una sola variante por sesión (A sin elección; solo B si hay elección);
        // las variantes B/C/D nunca se suman entre sí; dos sesiones reales del
        // mismo día sí se suman; la opcional sin confirmar no cuenta.
        assertEquals(DayExpenditure.Estimated(1_100.0), NutritionDayDistribution.sessionExpendituresByDate(sessions)[day])
    }

    @Test
    fun `same session id repeated on the same date counts once and the first wins`() {
        val day = week[4]
        // El MISMO sessionId planificado dos veces en la misma fecha (duplicado
        // accidental o plantilla): se cuenta UNA vez por `date to sessionId`.
        val duplicated = listOf(
            PlannedSessionLoad("dup", day, mapOf(SessionVariant.A to 500.0)),
            PlannedSessionLoad("dup", day, mapOf(SessionVariant.A to 900.0)),
        )
        assertEquals(DayExpenditure.Estimated(500.0), NutritionDayDistribution.sessionExpendituresByDate(duplicated)[day])

        // La variante elegida del primero manda; la segunda entrada se ignora.
        val differentChoice = listOf(
            PlannedSessionLoad("dup", day, mapOf(SessionVariant.A to 500.0), chosenVariant = SessionVariant.B),
            PlannedSessionLoad("dup", day, mapOf(SessionVariant.A to 700.0), chosenVariant = SessionVariant.A),
        )
        assertEquals(DayExpenditure.NotEstimable, NutritionDayDistribution.sessionExpendituresByDate(differentChoice)[day])
    }

    @Test
    fun `two distinct real sessions on the same day sum their chosen variants`() {
        val day = week[2]
        val sessions = listOf(
            PlannedSessionLoad("a", day, mapOf(SessionVariant.A to 400.0)),
            PlannedSessionLoad("b", day, mapOf(SessionVariant.A to 300.0, SessionVariant.B to 450.0), chosenVariant = SessionVariant.B),
        )
        // La MISMA fecha con sesiones DISTINTAS: ambas cuentan (400 + 450),
        // nunca se colapsan por fecha.
        assertEquals(DayExpenditure.Estimated(850.0), NutritionDayDistribution.sessionExpendituresByDate(sessions)[day])
    }

    @Test
    fun `same session id on different dates is counted once per date`() {
        val duplicateAcrossDates = listOf(
            PlannedSessionLoad("shared", week[0], mapOf(SessionVariant.A to 500.0)),
            PlannedSessionLoad("shared", week[6], mapOf(SessionVariant.A to 500.0)),
        )
        val byDate = NutritionDayDistribution.sessionExpendituresByDate(duplicateAcrossDates)
        assertEquals(2, byDate.size)
        assertEquals(DayExpenditure.Estimated(500.0), byDate[week[0]])
        assertEquals(DayExpenditure.Estimated(500.0), byDate[week[6]])
    }

    @Test
    fun `uniform by choice stays distinct from provisional and limit-flattened distributions`() {
        val estimated = expenditures(listOf(600.0, 600.0, 600.0, 600.0, 600.0, 600.0, 2_100.0))

        val byChoice = NutritionDayDistribution.distribute(input(expenditures = estimated, uniformByChoice = true))
        assertEquals(NutritionDistributionStatus.UNIFORM_BY_CHOICE, byChoice.status)

        val provisional = NutritionDayDistribution.distribute(
            input(expenditures = estimated + (week[1] to DayExpenditure.NotEstimable)),
        )
        assertEquals(NutritionDistributionStatus.PROVISIONAL_UNIFORM, provisional.status)

        // Reparto variable que acaba uniforme por límites: B cae en el borde
        // inferior, así que cualquier α > 0 sacaría fechas del rango.
        val flattened = NutritionDayDistribution.distribute(
            input(expenditures = estimated, bounds = calorieBoundsFor(PlanDirection.SURPLUS, 2_500.0)),
        )
        assertEquals(NutritionDistributionStatus.UNIFORM_BY_LIMITS, flattened.status)
        assertEquals(week.associateWith { 2_500 }, flattened.targetsByDate)
    }

    @Test
    fun `calendar changes never move fixed targets and preserve the remaining budget`() {
        val fixed = week.take(4).associateWith { 2_500 }
        val future = week.drop(4)
        fun run(values: List<Double?>) = NutritionDayDistribution.distribute(
            input(
                dates = future,
                expenditures = future.zip(values).toMap().mapValues { (_, kcal) ->
                    if (kcal == null) DayExpenditure.Rest else DayExpenditure.Estimated(kcal)
                },
                fixedTargets = fixed,
            ),
        )
        val before = run(listOf(null, 750.0, null))
        val after = run(listOf(750.0, null, null))
        // El cambio de calendario/gastos no mueve los objetivos ya fijados...
        assertEquals(fixed, before.fixedTargets)
        assertEquals(fixed, after.fixedTargets)
        assertTrue(before.targetsByDate.keys.none { it in fixed })
        // ...y el reparto futuro conserva R = 17500 − 10000 = 7500.
        assertEquals(7_500, before.targetsByDate.values.sum())
        assertEquals(7_500, after.targetsByDate.values.sum())
    }

    @Test
    fun `partial week without a valid solution keeps the previous targets untouched`() {
        val fixed = week.take(2).associateWith { 2_500 }
        val future = week.drop(2)
        val previous = future.associateWith { 2_500 }
        val result = NutritionDayDistribution.distribute(
            input(
                dates = future,
                expenditures = future.associateWith { DayExpenditure.Rest },
                fixedTargets = fixed,
                bounds = 3_000..3_200,
                previousTargets = previous,
            ),
        )
        assertEquals(NutritionDistributionStatus.KEPT_PREVIOUS, result.status)
        assertEquals(previous, result.targetsByDate)
        assertEquals(fixed, result.fixedTargets)
    }

    @Test
    fun `integer rounding keeps the budget exact and ties go to the earliest dates`() {
        val days = listOf(monday, monday.plusDays(1), monday.plusDays(2))
        val result = NutritionDayDistribution.distribute(input(dates = days, periodBudgetKcal = 7_501))
        assertEquals(7_501, result.targetsByDate.values.sum())
        assertEquals(mapOf(days[0] to 2_501, days[1] to 2_500, days[2] to 2_500), result.targetsByDate)
    }

    @Test
    fun `day macros scale all three from the same base and keep a manual zero`() {
        val targets = mapOf(week[0] to 2_200, week[1] to 2_800, week[2] to 2_500)
        val macros = macroTargetsByDate(targets, 150.0, 250.0, 0.0)
        // Los tres escalan desde la MISMA base, nunca encadenando el día anterior.
        assertEquals(
            scaleMacrosToCalories(150.0, 250.0, 0.0, 2_200),
            Triple(macros.getValue(week[0]).proteinG, macros.getValue(week[0]).carbsG, macros.getValue(week[0]).fatG),
        )
        assertEquals(
            scaleMacrosToCalories(150.0, 250.0, 0.0, 2_800),
            Triple(macros.getValue(week[1]).proteinG, macros.getValue(week[1]).carbsG, macros.getValue(week[1]).fatG),
        )
        // Grasa 0 manual: sigue en 0 g en todos los días.
        assertEquals(0, macros.getValue(week[0]).fatG)
        assertEquals(0, macros.getValue(week[1]).fatG)
        assertEquals(0, macros.getValue(week[2]).fatG)
    }
}
