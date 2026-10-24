package com.example.kpkn.domain.nutrition.checkin

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CheckinPlannerTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val days = (56L downTo 1L).map { DayObservation(today.minusDays(it), 2400.0) }
    private val weights = days.map { WeightObservation(it.date, 80.0) }
    private val plan = NutritionPlan(id = "plan", calorieTarget = 2100, direction = PlanDirection.MAINTENANCE, weeklyChangeKg = 0.0)
    private fun series(observations: List<DayObservation> = days) = ExpenditureEstimator().estimateSeries(
        ExpenditureInput(2400.0, CheckinPlanner.estimatorFloor(1600.0), observations, weights, today.minusDays(1)))
    private fun build(observations: List<DayObservation> = days, target: NutritionPlan = plan, preferences: CheckinPreferences = CheckinPreferences()): CheckinPlan {
        val series = series(observations)
        return CheckinPlanner.build(series.last(), series, observations, target, today, today, preferences)
    }

    @Test fun `los modulos conservan el orden fijo y ocultar uno no oculta la decision`() {
        val observations = days.map { when (it.date) {
            today.minusDays(2) -> it.copy(intakeKcal = 250.0)
            today.minusDays(4) -> it.copy(intakeKcal = null)
            else -> it
        } }
        val first = build(observations)
        assertEquals(listOf(CheckinModule.INTRODUCTION, CheckinModule.FASTING, CheckinModule.PARTIAL_DAYS,
            CheckinModule.WEIGH_IN, CheckinModule.TARGET_UPDATE), first.modules)
        val hidden = build(observations, preferences = CheckinPreferences(introductionSeen = true, dismissedModules = setOf(CheckinModule.FASTING)))
        assertFalse(hidden.modules.contains(CheckinModule.INTRODUCTION))
        assertFalse(hidden.modules.contains(CheckinModule.FASTING))
        assertTrue(hidden.modules.contains(CheckinModule.TARGET_UPDATE))
    }

    @Test fun `el ritmo respeta la direccion y cada propuesta limita el paso`() {
        val deficit = build(target = plan.copy(calorieTarget = 2400, direction = PlanDirection.DEFICIT, weeklyChangeKg = 0.5)).proposal!!
        val surplus = build(target = plan.copy(calorieTarget = 2400, direction = PlanDirection.SURPLUS, weeklyChangeKg = -0.5)).proposal!!
        assertEquals(2250, deficit.afterKcal)
        assertEquals(2550, surplus.afterKcal)
        assertEquals(today.plusDays(1).toString(), deficit.effectiveFrom)
        assertEquals(2400, deficit.beforeKcal)
    }

    @Test fun `revisa tambien los dias bajos anteriores a la ultima semana`() {
        val older = today.minusDays(12)
        assertTrue(build(days.map { if (it.date == older) it.copy(intakeKcal = 250.0) else it }).partialDays.contains(older))
    }

    @Test fun `un objetivo inferior al suelo no provoca una propuesta mayor que el tope`() {
        val built = build(target = plan.copy(calorieTarget = 1000))
        assertNull(built.proposal)
        assertTrue(built.reason!!.contains("suelo"))
    }

    @Test fun `siete dias sin registro pausan la propuesta y reanudar respeta los topes`() {
        val gap = days.map { if (it.date >= today.minusDays(7)) it.copy(intakeKcal = null) else it }
        val paused = build(gap)
        assertTrue(paused.loggingBreak)
        assertNull(paused.proposal)
        assertTrue(paused.modules.contains(CheckinModule.LOGGING_BREAK))
        assertTrue(paused.reason!!.contains("pausa"))
        val resumed = build()
        assertTrue(kotlin.math.abs(resumed.proposal!!.afterKcal - resumed.proposal.beforeKcal) <= CheckinPlanner.MAX_TARGET_STEP_KCAL)
    }

    @Test fun `con pocos pesajes muestra el motivo y no propone cambiar el objetivo`() {
        val series = ExpenditureEstimator().estimateSeries(ExpenditureInput(2400.0, 1400.0, days, weights.takeLast(2), today.minusDays(1)))
        val built = CheckinPlanner.build(series.last(), series, days, plan, today, today)
        assertEquals(PauseReason.INSUFFICIENT_WEIGHTS, built.estimate!!.pauseReason)
        assertTrue(built.modules.contains(CheckinModule.WEIGH_IN))
        assertTrue(built.reason!!.contains("pesajes"))
        assertNull(built.proposal)
    }

    @Test fun `parcial excluye y completo confirmado conserva el dia sin borrar comidas`() {
        val date = today.minusDays(2)
        val logs = days.map { NutritionLog(id = it.date.toString(), date = it.date.toString(), foods = listOf(
            LoggedFood(calories = if (it.date == date) 250.0 else 2400.0))) }
        fun marked(mark: DayMark) = buildDayObservations(logs, mapOf(date to DayMarkEntry(date, mark, DayMarkSource.USER, Instant.EPOCH)), today.minusDays(1))
        assertTrue(build(buildDayObservations(logs, emptyMap(), today.minusDays(1))).partialDays.contains(date))
        assertEquals(ExclusionReason.PARTIAL, series(marked(DayMark.PARTIAL)).last().quality.excludedDates[date])
        assertFalse(series(marked(DayMark.COMPLETE_CONFIRMED)).last().quality.excludedDates.containsKey(date))
        assertEquals(56, logs.size)
        assertEquals(250.0, logs.single { it.date == date.toString() }.foods.single().calories, 0.0)
    }

    @Test fun `ayuno confirmado aporta cero y el vacio sin marcar se excluye`() {
        val date = today.minusDays(3)
        val empty = days.map { if (it.date == date) it.copy(intakeKcal = null) else it }
        val unmarked = series(empty).last()
        val fasting = series(empty.map { if (it.date == date) it.copy(mark = DayMark.FASTING) else it }).last()
        assertEquals(ExclusionReason.NO_LOG, unmarked.quality.excludedDates[date])
        assertFalse(fasting.quality.excludedDates.containsKey(date))
        assertEquals(unmarked.quality.usedDays + 1, fasting.quality.usedDays)
    }

    @Test fun `una comida futura no altera hoy y editar el pasado conserva todo el prefijo`() {
        val input = ExpenditureInput(2400.0, 1400.0, days, weights, today.minusDays(1))
        val before = ExpenditureEstimator().estimateSeries(input)
        assertEquals(before, ExpenditureEstimator().estimateSeries(input.copy(days = days + DayObservation(today.plusDays(1), 8000.0))))
        val edit = today.minusDays(8)
        val after = ExpenditureEstimator().estimateSeries(input.copy(days = days.map { if (it.date == edit) it.copy(intakeKcal = 3300.0) else it }))
        assertEquals(before.filter { it.date < edit }, after.filter { it.date < edit })
        assertNotEquals(before.last().kcal, after.last().kcal)
    }

    @Test fun `la evidencia enumera usados y cada exclusion con su motivo`() {
        val observations = days.map { if (it.date == today.minusDays(2)) it.copy(mark = DayMark.PARTIAL) else it }
        val built = build(observations)
        val proposal = built.proposal!!
        assertEquals(built.estimate!!.quality.usedDays, proposal.usedDates.size)
        assertEquals("PARTIAL", proposal.excludedDates[today.minusDays(2).toString()])
        assertEquals(built.estimate.quality.windowDays, proposal.usedDates.size + proposal.excludedDates.size)
        assertTrue(proposal.lowKcal <= proposal.expenditureKcal && proposal.highKcal >= proposal.expenditureKcal)
    }

    @Test fun `el suelo propio no interviene en historiales normales del banco con semillas fijas`() {
        assertEquals(1000.0, CheckinPlanner.estimatorFloor(null), 0.0)
        assertEquals(1800.0, CheckinPlanner.estimatorFloor(2000.0), 0.0)
        for (seed in 1101L..1108L) {
            val simulation = ExpenditureSimulation.generate(seed)
            val floor = CheckinPlanner.estimatorFloor(1600.0 + (seed % 6) * 100)
            val estimates = ExpenditureEstimator().estimateSeries(simulation.input.copy(floorKcal = floor))
            assertTrue(estimates.all { it.kcal >= floor })
            assertTrue(estimates.count { it.kcal == floor }.toDouble() / estimates.size < 1.0 / 100.0)
            assertTrue(estimates.zipWithNext().all { (a, b) -> kotlin.math.abs(a.kcal - b.kcal) <= ExpenditureParams.V1.maximumDailyChangeKcal + 1e-6 })
        }
    }
}
