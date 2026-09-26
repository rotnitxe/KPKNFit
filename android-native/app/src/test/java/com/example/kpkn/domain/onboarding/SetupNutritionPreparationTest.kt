package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.nutrition.DayExpenditure
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionConfigurationMode
import com.example.kpkn.domain.nutrition.NutritionDistributionStatus
import com.example.kpkn.domain.nutrition.NutritionPlanPreparationStatus
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.decodeWeeklyForecast
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Preparación nutricional del ALTA (onboarding): espeja el editor directo
 * (mismo `draftFromWizard`), adapta el calendario real y reparte la semana.
 *
 * Honestidad verificada:
 * - Objetivos propios (SELF_DEFINED) funcionan sin EER y no caen en
 *   KEPT_PREVIOUS: el presupuesto semanal cuadra y el reparto es variable.
 * - `eligibilityUnknown` bloquea la EQUACIÓN, no el gasto del calendario.
 * - TRACKING_ONLY no fabrica plan, metas ni defaults, pero sí resuelve el gasto.
 * - La composición corporal ACTUAL viaja como `startValue`, nunca como meta.
 */
class SetupNutritionPreparationTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)
    private val week: List<LocalDate> = (0L until 7L).map { monday.plusDays(it) }
    private val settings = Settings()

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private fun weightedSession(id: String, day: Int): Session {
        val set = ExerciseSet(id = "${id}_s", weight = 100.0, targetReps = 8, targetRPE = 8.0)
        return Session(
            id = id,
            name = "Fuerza $id",
            dayOfWeek = day,
            exercises = listOf(
                Exercise(id = "${id}_e", name = "Press", restTime = 90, sets = listOf(set)),
            ),
        )
    }

    private fun program(sessions: List<Session>): Program =
        Program(
            id = "prog-setup",
            name = "Programa alta",
            structure = ProgramStructure.COMPLEX,
            calendarization = ProgramCalendarization(
                mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
                strictStart = true,
            ),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-09-21",
                weekStartDay = 1,
                trainingDays = setOf(1, 3, 5),
                mode = ScheduleMode.DATED,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "mac1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "m1",
                                    name = "Meso",
                                    goal = MesocycleGoal.ACCUMULATION,
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "Semana 1", sessions = sessions),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun manualDraft(
        calories: String = "2000",
        protein: String = "160",
        carbs: String = "200",
        fat: String = "60",
        configurationMode: NutritionConfigurationMode = NutritionConfigurationMode.SELF_DEFINED,
        bodyFatText: String = "",
        targetBodyFatText: String = "",
        goalMetric: GoalMetric = GoalMetric.WEIGHT,
    ): NutritionWizardDraft =
        NutritionWizardDraft(
            direction = PlanDirection.DEFICIT,
            goalMetric = goalMetric,
            ageText = "",
            heightText = "",
            weightText = "",
            configurationMode = configurationMode,
            manualCalorieTargetText = calories,
            manualProteinText = protein,
            manualCarbsText = carbs,
            manualFatText = fat,
            bodyFatText = bodyFatText,
            targetBodyFatText = targetBodyFatText,
        )

    private fun prepare(
        draft: NutritionWizardDraft,
        program: Program? = this.program(listOf(weightedSession("a", 1))),
    ): SetupNutritionPreparationResult =
        SetupNutritionPreparation.prepare(
            SetupNutritionPreparationInput(
                draft = draft,
                program = program,
                settings = settings,
                today = monday,
            ),
        )

    // ─── Objetivos propios (manual) sin EER ─────────────────────────────────

    @Test
    fun `self-defined manual plan is READY with exact weekly budget and variable split`() {
        val result = prepare(manualDraft())

        assertEquals(NutritionPlanPreparationStatus.READY, result.status)
        assertTrue(result.errors.isEmpty())
        val plan = assertNotNullAndGet(result.plan)
        assertEquals(2000, plan.calorieTarget)
        assertEquals(CalculationOrigin.MANUAL, plan.calculationOrigin)
        assertNull(plan.startValue)

        // La base del reparto viene DEL PLAN FINAL, jamás de una re-derivación.
        assertEquals(NutritionDistributionStatus.VARIABLE, result.distributionStatus)
        assertEquals(week.toSet(), result.days.map { it.date }.toSet())
        // Presupuesto semanal exacto: Σ T_i = 7 · B = 14000.
        assertEquals(14000, result.days.sumOf { it.calorieTargetKcal })
        // Reparto real: el día con entrenamiento supera a un día de descanso.
        assertTrue(result.days.first().calorieTargetKcal > result.days[1].calorieTargetKcal)
        assertTrue(result.days.all { it.proteinG > 0 && it.carbsG > 0 && it.fatG > 0 })

        // Gasto del calendario adaptado de verdad.
        val mondayExpenditure = result.expendituresByDate[week[0]]
        assertTrue(mondayExpenditure is DayExpenditure.Estimated)
        assertTrue((mondayExpenditure as DayExpenditure.Estimated).kcal > 0.0)
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[1]])

        // Previsión semanal escrita para resolver el objetivo por fecha después.
        val snapshot = assertNotNullAndGet(plan.calculationSnapshot)
        val forecast = assertNotNullAndGet(decodeWeeklyForecast(snapshot.inputs.getValue(WEEKLY_FORECAST_KEY)))
        assertEquals(7, forecast.size)
        assertEquals(monday, forecast.first().date)
    }

    // ─── Elegibilidad desconocida ≠ gasto desconocido ───────────────────────

    @Test
    fun `eligibilityUnknown blocks the equation but expenditures are still resolved`() {
        val draft = manualDraft(
            configurationMode = NutritionConfigurationMode.AUTOMATIC,
        ).copy(
            ageText = "30",
            heightText = "180",
            weightText = "90",
            equationSex = EerSex.MALE,
            eligibilityUnknown = true,
            manualCalorieTargetText = "",
            manualProteinText = "",
            manualCarbsText = "",
            manualFatText = "",
        )
        val result = prepare(draft)

        assertNull(result.plan)
        assertEquals(NutritionPlanPreparationStatus.BLOCKED_EQUATION, result.status)
        assertNotNull(result.errors["eligibility"])
        assertTrue(result.days.isEmpty())
        assertNull(result.distributionStatus)
        // El motor EER sí pudo calcular: el bloqueo es de aplicabilidad, nunca
        // un gasto desconocido.
        assertNotNull(result.recommendation?.eerKcal)
        // El gasto del calendario se resuelve independientemente del plan.
        val mondayExpenditure = result.expendituresByDate[week[0]]
        assertTrue(mondayExpenditure is DayExpenditure.Estimated)
        assertTrue((mondayExpenditure as DayExpenditure.Estimated).kcal > 0.0)
    }

    // ─── Solo registro: sin plan ni metas, pero con gasto ───────────────────

    @Test
    fun `tracking only returns no plan no days and keeps the calendar expenditure`() {
        val result = prepare(
            manualDraft(
                configurationMode = NutritionConfigurationMode.TRACKING_ONLY,
            ).copy(direction = null),
        )

        assertNull(result.plan)
        assertEquals(NutritionPlanPreparationStatus.TRACKING_ONLY, result.status)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.days.isEmpty())
        assertNull(result.distributionStatus)
        assertNull(result.recommendation)
        // Sin metas fabricadas, pero sin perder el gasto del calendario.
        val mondayExpenditure = result.expendituresByDate[week[0]]
        assertTrue(mondayExpenditure is DayExpenditure.Estimated)
        assertEquals(DayExpenditure.Rest, result.expendituresByDate[week[3]])
        assertEquals(week.toSet(), result.expendituresByDate.keys)
    }

    // ─── Composición actual: startValue, nunca meta ─────────────────────────

    @Test
    fun `body fat goal keeps current composition as start value and target as goal`() {
        val result = prepare(
            manualDraft(
                goalMetric = GoalMetric.BODY_FAT,
                bodyFatText = "25.0",
                targetBodyFatText = "18.0",
            ),
        )

        assertEquals(NutritionPlanPreparationStatus.READY, result.status)
        val plan = assertNotNullAndGet(result.plan)
        // La composición ACTUAL del borrador viaja como punto de partida.
        assertEquals(25.0, plan.startValue ?: Double.NaN, 1e-9)
        assertEquals(18.0, plan.goalValue, 1e-9)
        assertEquals(18.0, plan.typedBodyGoal?.targetValueSi ?: Double.NaN, 1e-9)
        assertEquals(18.0, plan.targetBodyFat ?: Double.NaN, 1e-9)
        // La meta nunca es el valor actual.
        assertFalse(plan.startValue == plan.goalValue)
        // Y la previsión semanal se escribe en el plan del alta.
        val snapshot = assertNotNullAndGet(plan.calculationSnapshot)
        assertNotNull(decodeWeeklyForecast(snapshot.inputs.getValue(WEEKLY_FORECAST_KEY)))
    }

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}