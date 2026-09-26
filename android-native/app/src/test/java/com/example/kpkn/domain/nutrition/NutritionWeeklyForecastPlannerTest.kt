package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Planificador de la previsión semanal: periodo FIJO (no rolling), hoy/pasado
 * fijados con sus macros aunque no haya snapshot, presupuesto exacto y
 * conservación de la semana cuando no hay solución válida.
 */
class NutritionWeeklyForecastPlannerTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)
    private val thursday: LocalDate = monday.plusDays(3)
    private val friday: LocalDate = monday.plusDays(4)
    private val saturday: LocalDate = monday.plusDays(5)
    private val sunday: LocalDate = monday.plusDays(6)

    private fun target(date: LocalDate, kcal: Int, protein: Int, carbs: Int, fat: Int) =
        NutritionEditorDayTarget(date, kcal, protein, carbs, fat)

    /** Previsión previa de la semana completa, con el jueves (hoy) ya repartido. */
    private val priorTargets = listOf(
        target(monday, 2_100, 160, 240, 65),
        target(monday.plusDays(1), 1_900, 150, 220, 60),
        target(monday.plusDays(2), 2_000, 155, 230, 62),
        target(thursday, 2_300, 170, 260, 70),
        target(friday, 2_600, 175, 300, 72),
        target(saturday, 2_400, 172, 280, 70),
        target(sunday, 1_800, 150, 200, 55),
    )

    private fun plan(
        inputs: Map<String, String>,
        calorieTarget: Int = 2_000,
        protein: Int = 150,
        carbs: Int = 250,
        fat: Int = 60,
        direction: PlanDirection? = null,
    ): NutritionPlan = NutritionPlan(
        id = "plan-1",
        name = "Plan",
        calorieTarget = calorieTarget,
        proteinGoal = protein,
        carbGoal = carbs,
        fatGoal = fat,
        direction = direction,
        calculationOrigin = CalculationOrigin.PLAN,
        calculationSnapshot = NutritionPlanCalculationSnapshot(
            engineVersion = "eer-2023-v1",
            formula = "test",
            calculatedAt = "2026-09-21T00:00:00Z",
            inputs = inputs,
        ),
    )

    private fun forecastInput(
        revision: Int = 1,
        effectiveDate: LocalDate? = monday,
    ): Map<String, String> = mapOf(
        WEEKLY_FORECAST_KEY to encodeWeeklyForecast(priorTargets, revision = revision, effectiveDate = effectiveDate),
    )

    /** Gasto por fecha: el futuro cambia (viernes/sábado con sesión, domingo descanso). */
    private val expenditures: Map<LocalDate, DayExpenditure> = mapOf(
        monday to DayExpenditure.Estimated(500.0),
        monday.plusDays(1) to DayExpenditure.Rest,
        monday.plusDays(2) to DayExpenditure.Estimated(450.0),
        thursday to DayExpenditure.Estimated(500.0),
        friday to DayExpenditure.Estimated(600.0),
        saturday to DayExpenditure.Estimated(900.0),
        sunday to DayExpenditure.Rest,
    )

    // ─── Jueves: hoy/pasado fijos (kcal Y macros) y presupuesto exacto ───────

    @Test
    fun `thursday update keeps today kcal and macros and preserves the exact week budget`() {
        val plan = plan(forecastInput())
        val revised = revisedWeeklyForecast(
            NutritionForecastRevisionInput(
                plan = plan,
                today = thursday,
                expenditures = expenditures,
                snapshots = emptyList(),
            ),
        )

        assertNotNull(revised)
        val document = decodeWeeklyForecastDocument(
            revised!!.calculationSnapshot!!.inputs.getValue(WEEKLY_FORECAST_KEY),
        )!!
        val byDate = document.days.associateBy { it.date }

        // HOY (jueves) y el pasado no se mueven: ni calorías ni macros, aunque
        // la única evidencia sea la previsión previa (no hay snapshot).
        assertEquals(7, document.days.size)
        assertEquals(2_300, byDate.getValue(thursday).calorieTargetKcal)
        assertEquals(170, byDate.getValue(thursday).proteinG)
        assertEquals(260, byDate.getValue(thursday).carbsG)
        assertEquals(70, byDate.getValue(thursday).fatG)
        for (date in listOf(monday, monday.plusDays(1), monday.plusDays(2))) {
            val prior = priorTargets.first { it.date == date }
            assertEquals(prior.calorieTargetKcal, byDate.getValue(date).calorieTargetKcal)
            assertEquals(prior.proteinG, byDate.getValue(date).proteinG)
            assertEquals(prior.carbsG, byDate.getValue(date).carbsG)
            assertEquals(prior.fatG, byDate.getValue(date).fatG)
        }

        // El futuro SÍ se re-reparte según el gasto (sábado > viernes > domingo).
        val byDateKcal = document.days.associate { it.date to it.calorieTargetKcal }
        assertEquals(2_000, byDateKcal.getValue(friday))
        assertEquals(2_300, byDateKcal.getValue(saturday))
        assertEquals(1_400, byDateKcal.getValue(sunday))
        assertTrue(byDateKcal.getValue(saturday) > byDateKcal.getValue(friday))
        assertTrue(byDateKcal.getValue(friday) > byDateKcal.getValue(sunday))

        // Presupuesto del periodo FIJO: Σ T_i = 7 · B exacto, sin compensar
        // ingesta ni gasto registrado.
        assertEquals(7 * 2_000, document.days.sumOf { it.calorieTargetKcal })

        // Revisión + fecha de efectividad versionadas (v2), no el v1 de solo
        // versión de esquema.
        assertEquals(2, document.revision)
        assertEquals(2, document.formatVersion)
        assertEquals(monday, document.effectiveDate)
    }

    // ─── Sin solución válida: conservar la semana vigente ───────────────────

    @Test
    fun `impossible bounds keep the current week untouched`() {
        // Límites incompatibles con cualquier reparto (EER almacenada 3000 →
        // 3000..3500) y una base de 2000: no hay solución entera válida.
        val plan = plan(
            inputs = forecastInput() + ("eerKcal" to "3000"),
            direction = PlanDirection.SURPLUS,
        )
        val revised = revisedWeeklyForecast(
            NutritionForecastRevisionInput(
                plan = plan,
                today = thursday,
                expenditures = expenditures,
                snapshots = emptyList(),
            ),
        )
        // Sin solución: NO se reescribe nada (la semana vigente se conserva
        // hasta que haya una válida).
        assertNull(revised)
    }

    // ─── Sin horizonte previo: nada que actualizar ──────────────────────────

    @Test
    fun `plan without a previous forecast is left untouched`() {
        val plan = plan(inputs = mapOf("weeklyDistribution" to "VARIABLE"))
        assertNull(
            revisedWeeklyForecast(
                NutritionForecastRevisionInput(
                    plan = plan,
                    today = thursday,
                    expenditures = expenditures,
                    snapshots = emptyList(),
                ),
            ),
        )
    }

    // ─── Periodo FIJO, nunca una ventana rolling hoy..+6 ───────────────────

    @Test
    fun `period without previous forecast starts today and covers the window`() {
        assertEquals(
            (0L until 7L).map { thursday.plusDays(it) },
            weeklyForecastPeriodFor(null, thursday),
        )
    }

    @Test
    fun `period in progress keeps the same horizon instead of rolling to today plus six`() {
        val prior = priorTargets.map { WeeklyForecastDay(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG) }
        // El jueves NO reabre la ventana en jue..mié: se conserva lun..dom.
        assertEquals(prior.map { it.date }.sorted(), weeklyForecastPeriodFor(prior, thursday))
    }

    @Test
    fun `last day of the period keeps the current horizon so today is not rewritten`() {
        val prior = priorTargets.map { WeeklyForecastDay(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG) }
        // HOY == último día vigente: NO se salta al siguiente periodo (eso
        // borraría la previsión actual y cambiaría el objetivo de HOY).
        assertEquals(prior.map { it.date }, weeklyForecastPeriodFor(prior, sunday))
    }

    @Test
    fun `consumed period rolls to the next period anchored on the same weekday`() {
        val prior = priorTargets.map { WeeklyForecastDay(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG) }
        // Consumido SOLO con prior.last < today (aquí, un día después del domingo).
        val afterEnd = sunday.plusDays(1)
        val period = weeklyForecastPeriodFor(prior, afterEnd)
        assertEquals(sunday.plusDays(1), period.first())
        assertEquals(7, period.size)
        assertTrue(period.all { it.isAfter(sunday) })
    }

    @Test
    fun `last day without snapshot keeps the non uniform target documented for today`() {
        // HOY es el último día del periodo y NO hay snapshot: la previsión ya
        // documenta un objetivo NO uniforme para hoy (1800 kcal / 55 g grasa)
        // y debe seguir siendo el que resuelve el resolver, no la media del
        // plan (2000 kcal / 250 g CHO), que el resolver NO debe alcanzar.
        val plan = plan(forecastInput())
        val prior = priorTargets.map { WeeklyForecastDay(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG) }
        val period = weeklyForecastPeriodFor(prior, sunday)
        assertEquals(prior.map { it.date }, period)

        // Sin fechas por mover en el último día: la previsión NO se reescribe.
        assertNull(
            revisedWeeklyForecast(
                NutritionForecastRevisionInput(plan = plan, today = sunday, expenditures = expenditures, snapshots = emptyList()),
            ),
        )

        // Objetivo resuelto para HOY = el documentado, con SUS macros.
        val resolved = planDayTargetForDate(plan, sunday, NutritionGoalSource.PLAN_FORECAST)
        assertEquals(1_800, resolved.calorieTargetKcal)
        assertEquals(150, resolved.proteinGoalG)
        assertEquals(200, resolved.carbGoalG)
        assertEquals(55, resolved.fatGoalG)

        // Y la evidencia fijada de hoy (sin snapshot) conserva esas macros.
        val fixed = fixedWeeklyEvidenceFor(prior, sunday, period, emptyList(), plan.id)
        assertEquals(1_800, fixed.getValue(sunday).calorieTargetKcal)
        assertEquals(200, fixed.getValue(sunday).carbsG)
        assertEquals(55, fixed.getValue(sunday).fatG)
    }

    @Test
    fun `gap after a consumed period lands on the period containing today`() {
        val prior = priorTargets.map { WeeklyForecastDay(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG) }
        val laterWednesday = sunday.plusDays(10) // 2026-10-07 (miércoles)
        val period = weeklyForecastPeriodFor(prior, laterWednesday)
        assertEquals(7, period.size)
        assertTrue(period.first().isBefore(laterWednesday) || period.first() == laterWednesday)
        assertTrue(period.last().isAfter(laterWednesday))
        assertEquals(java.time.DayOfWeek.MONDAY, period.first().dayOfWeek)
    }

    // ─── Previsión versionada: v2 con revisión/efectividad, v1 legado ───────

    @Test
    fun `forecast payload round trips its revision and effective date`() {
        val raw = encodeWeeklyForecast(priorTargets, revision = 5, effectiveDate = monday)
        val document = decodeWeeklyForecastDocument(raw)!!
        assertEquals(2, document.formatVersion)
        assertEquals(5, document.revision)
        assertEquals(monday, document.effectiveDate)
        assertEquals(7, document.days.size)
        assertEquals(2_300, document.days.first { it.date == thursday }.calorieTargetKcal)
    }

    @Test
    fun `legacy v1 payload still decodes with schema version only`() {
        val document = decodeWeeklyForecastDocument("v1|2026-09-21=2000,150,250,60")!!
        assertEquals(1, document.formatVersion)
        assertEquals(1, document.revision)
        assertNull(document.effectiveDate)
        assertEquals(1, document.days.size)
        assertEquals(2_000, document.days.first().calorieTargetKcal)
        assertNull(decodeWeeklyForecastDocument("v3|whatever"))
    }

    // ─── Evidencia fijada: snapshot o previsión previa ──────────────────────

    @Test
    fun `fixed evidence prefers the snapshot but falls back to the previous forecast`() {
        val prior = priorTargets.map { WeeklyForecastDay(it.date, it.calorieTargetKcal, it.proteinG, it.carbsG, it.fatG) }
        val period = weeklyForecastPeriodFor(prior, thursday)
        val snapshot = com.example.kpkn.data.models.DailyGoalSnapshot(
            date = thursday.toString(),
            planId = "plan-1",
            calorieTargetKcal = 2_450,
            proteinGoalG = 180,
            carbGoalG = 270,
            fatGoalG = 75,
            direction = PlanDirection.DEFICIT,
            calculationOrigin = CalculationOrigin.PLAN,
            capturedAtEpochMs = 1L,
        )

        val withSnapshot = fixedWeeklyEvidenceFor(prior, thursday, period, listOf(snapshot), "plan-1")
        assertEquals(2_450, withSnapshot.getValue(thursday).calorieTargetKcal)
        assertEquals(180, withSnapshot.getValue(thursday).proteinG)

        val withoutSnapshot = fixedWeeklyEvidenceFor(prior, thursday, period, emptyList(), "plan-1")
        assertEquals(2_300, withoutSnapshot.getValue(thursday).calorieTargetKcal)
        assertEquals(170, withoutSnapshot.getValue(thursday).proteinG)

        // Snapshots de OTRA identidad de plan no mandan: la evidencia propia
        // (la previsión del plan) sigue siendo la de este plan.
        val foreign = fixedWeeklyEvidenceFor(prior, thursday, period, listOf(snapshot), "otro-plan")
        assertEquals(2_300, foreign.getValue(thursday).calorieTargetKcal)

        // El futuro jamás se fija.
        assertTrue(withoutSnapshot.keys.all { !it.isAfter(thursday) })
    }
}
