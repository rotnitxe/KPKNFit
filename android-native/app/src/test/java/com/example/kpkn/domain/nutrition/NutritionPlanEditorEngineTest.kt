package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Editor nutricional directo: coherencia calorías/macros, ceros manuales,
 * guardado exacto de la base revisada y modo durable de solo registro.
 */
class NutritionPlanEditorEngineTest {

    private val monday: LocalDate = LocalDate.of(2026, 9, 21)

    private fun validDraft(base: NutritionEditorBase? = null, baseEdited: Boolean = false) = NutritionPlanEditorDraft(
        planId = null,
        direction = PlanDirection.DEFICIT,
        goalMetric = GoalMetric.WEIGHT,
        targetValueText = "",
        ageText = "30",
        heightText = "180",
        weightText = "80",
        weightUnit = "kg",
        equationSex = EerSex.MALE,
        activity = EerActivity.ACTIVE,
        base = base,
        baseEdited = baseEdited,
    )

    @Test
    fun editingCaloriesScalesAllThreeMacrosAndPreservesManualZeros() {
        val base = NutritionEditorBase(caloriesKcal = 2000, proteinG = 150, carbsG = 250, fatG = 55)

        val scaled = base.withCalories(2400)

        assertEquals(2400, scaled.caloriesKcal)
        // Escalan los TRES macros (no solo carbohidratos).
        assertEquals(scaleMacrosToCalories(150.0, 250.0, 55.0, 2400).first, scaled.proteinG)
        assertEquals(scaleMacrosToCalories(150.0, 250.0, 55.0, 2400).second, scaled.carbsG)
        assertEquals(scaleMacrosToCalories(150.0, 250.0, 55.0, 2400).third, scaled.fatG)
        assertTrue(scaled.proteinG != 150 || scaled.carbsG != 250 || scaled.fatG != 55)

        // Un 0 manual es un cero válido y sobrevive al escalado.
        val withZeroCarbs = NutritionEditorBase(2000, 150, 0, 55).withCalories(2400)
        assertEquals(0, withZeroCarbs.carbsG)
        assertTrue(withZeroCarbs.proteinG > 0)
        assertTrue(withZeroCarbs.fatG > 0)

        val withZeroFat = NutritionEditorBase(2000, 150, 250, 0).withCalories(1800)
        assertEquals(0, withZeroFat.fatG)
    }

    @Test
    fun editingMacroUpdatesEnergyTotalWithAtwater() {
        val base = NutritionEditorBase(caloriesKcal = 2000, proteinG = 150, carbsG = 250, fatG = 55)

        val edited = base.withMacro(protein = 180.0)

        // El total energético mostrado se deriva de los gramos (Atwater 4/4/9).
        assertEquals(atwaterKcal(180.0, 250.0, 55.0), edited.caloriesKcal)
        assertEquals(180, edited.proteinG)
        // Los otros dos macros quedan intactos.
        assertEquals(250, edited.carbsG)
        assertEquals(55, edited.fatG)

        val zeroFat = base.withMacro(fat = 0.0)
        assertEquals(0, zeroFat.fatG)
        assertEquals(atwaterKcal(150.0, 250.0, 0.0), zeroFat.caloriesKcal)
    }

    @Test
    fun editingCarbsUpdatesEnergyTotalAndKeepsProteinAndFatIntact() {
        val base = NutritionEditorBase(caloriesKcal = 2000, proteinG = 150, carbsG = 250, fatG = 55)

        val edited = base.withMacro(carbs = 300.0)

        assertEquals(300, edited.carbsG)
        assertEquals(150, edited.proteinG)
        assertEquals(55, edited.fatG)
        assertEquals(atwaterKcal(150.0, 300.0, 55.0), edited.caloriesKcal)
    }

    @Test
    fun editingOneMacroPreservesTheManualZeroOfAnotherMacro() {
        // Cero manual de grasa: sigue en 0 cuando se edita carbohidratos.
        val edited = NutritionEditorBase(2000, 150, 250, 0).withMacro(carbs = 300.0)

        assertEquals(0, edited.fatG)
        assertEquals(300, edited.carbsG)
        assertEquals(atwaterKcal(150.0, 300.0, 0.0), edited.caloriesKcal)

        // Cero manual de proteína: sigue en 0 cuando se edita la grasa.
        val zeroProtein = NutritionEditorBase(2000, 0, 250, 55).withMacro(fat = 70.0)

        assertEquals(0, zeroProtein.proteinG)
        assertEquals(70, zeroProtein.fatG)
        assertEquals(atwaterKcal(0.0, 250.0, 70.0), zeroProtein.caloriesKcal)
    }

    @Test
    fun editingCaloriesAfterAMacroKeepsASingleCoherentBase() {
        // Primero se edita un macro y después las calorías: la base sigue
        // siendo única y coherente (macros escalados, total = lo introducido).
        val base = NutritionEditorBase(2000, 150, 250, 55).withMacro(protein = 180.0)
        val scaled = base.withCalories(2400)

        assertEquals(2400, scaled.caloriesKcal)
        assertEquals(
            scaleMacrosToCalories(
                base.proteinG.toDouble(),
                base.carbsG.toDouble(),
                base.fatG.toDouble(),
                2400,
                preserveZeros = true,
            ).first,
            scaled.proteinG,
        )
        assertEquals(
            scaleMacrosToCalories(
                base.proteinG.toDouble(),
                base.carbsG.toDouble(),
                base.fatG.toDouble(),
                2400,
                preserveZeros = true,
            ).second,
            scaled.carbsG,
        )
        assertEquals(
            scaleMacrosToCalories(
                base.proteinG.toDouble(),
                base.carbsG.toDouble(),
                base.fatG.toDouble(),
                2400,
                preserveZeros = true,
            ).third,
            scaled.fatG,
        )
    }

    @Test
    fun reviewedBaseIsSavedExactlyWithoutSilentCorrections() {
        // Base revisada coherente: el total es el Atwater de sus gramos.
        val base = NutritionEditorBase(2000, 150, 250, 55).withMacro(protein = 180.0)
        val draft = validDraft(base = base, baseEdited = true)

        val prepared = NutritionPlanPreparation.prepare(
            preparationInputOf(draft, planId = "plan-1", existingPlan = null),
        )

        assertTrue(prepared.errors.isEmpty())
        val plan = prepared.plan!!
        // Guardar EXACTAMENTE la base revisada: sin redondeos ni correcciones.
        assertTrue(matchesReviewedBase(plan, base))
        assertEquals(base.caloriesKcal, plan.calorieTarget)
        assertEquals(base.proteinG, plan.proteinGoal)
        assertEquals(base.carbsG, plan.carbGoal)
        assertEquals(base.fatG, plan.fatGoal)
    }

    @Test
    fun manualZeroMacroSurvivesPreparationAndStaysZero() {
        val base = NutritionEditorBase(2000, 150, 250, 55).withMacro(fat = 0.0)
        assertEquals(0, base.fatG)
        val draft = validDraft(base = base, baseEdited = true)

        val plan = NutritionPlanPreparation.prepare(
            preparationInputOf(draft, planId = "plan-zero", existingPlan = null),
        ).plan!!

        // 0 = valor legítimo; no se sustituye por un default ni se corrige.
        assertEquals(0, plan.fatGoal)
        assertTrue(matchesReviewedBase(plan, base))
    }

    @Test
    fun zeroCaloriesAreRejectedInsteadOfSilentlyReplaced() {
        val base = NutritionEditorBase(0, 0, 0, 0)
        val draft = validDraft(base = base, baseEdited = true)

        val errors = editorErrorsOf(draft, base)

        // Nada de correcciones silenciosas: el error se muestra y no se guarda.
        assertTrue(errors.containsKey("calories"))
        assertTrue(editorErrorsOf(draft, NutritionEditorBase(2000, 0, 0, 0)).containsKey("macros"))
    }

    @Test
    fun uneditedAutomaticDraftSavesTheDisplayedRecommendationBase() {
        val draft = validDraft(base = null, baseEdited = false)
        val recommendation = NutritionEnergyEngine.recommendPlan(
            input = EerInput(30, 180.0, 80.0, EerSex.MALE, EerActivity.ACTIVE),
            direction = PlanDirection.DEFICIT,
        )
        val base = reviewedBaseOf(draft, recommendation)
        assertNotNull(base)

        val plan = NutritionPlanPreparation.prepare(
            preparationInputOf(draft, planId = "plan-auto", existingPlan = null),
        ).plan!!

        // Lo mostrado y lo guardado son la misma base.
        assertTrue(matchesReviewedBase(plan, base!!))
    }

    @Test
    fun trackingOnlyDraftProducesNoPlanAndReportsTrackingOnlyStatus() {
        val draft = validDraft().copy(mode = NutritionPlanEditorMode.TRACKING_ONLY)

        assertEquals(NutritionConfigurationMode.TRACKING_ONLY, configurationModeOf(draft))
        val prepared = NutritionPlanPreparation.prepare(
            preparationInputOf(draft, planId = "plan-no", existingPlan = null),
        )

        // Solo registro: sin plan, sin metas fabricadas y sin defaults.
        assertNull(prepared.plan)
        assertNull(prepared.recommendation)
        assertTrue(prepared.errors.isEmpty())
        assertEquals(NutritionPlanPreparationStatus.TRACKING_ONLY, prepared.status)
    }

    @Test
    fun weeklyDistributionScalesMacrosFromTheSameReviewedBase() {
        val base = NutritionEditorBase(2000, 150, 250, 55)
        val today = LocalDate.of(2026, 9, 21)
        val dates = (0L until 7L).map { today.plusDays(it) }
        val expenditures = mapOf(
            dates[0] to DayExpenditure.Estimated(500.0),
            dates[2] to DayExpenditure.Estimated(500.0),
        )

        val targets = weeklyTargetsFor(base, NutritionWeeklyDistributionMode.VARIABLE, dates, expenditures)

        assertEquals(7, targets.size)
        // Presupuesto fijo: el reparto NUNCA añade calorías al presupuesto.
        assertEquals(7 * base.caloriesKcal, targets.sumOf { it.calorieTargetKcal })
        // Cada día escala los TRES macros desde la MISMA base (sin encadenar).
        targets.forEach { day ->
            val (p, c, f) = scaleMacrosToCalories(
                base.proteinG.toDouble(),
                base.carbsG.toDouble(),
                base.fatG.toDouble(),
                day.calorieTargetKcal,
            )
            assertEquals(p, day.proteinG)
            assertEquals(c, day.carbsG)
            assertEquals(f, day.fatG)
        }
        // Días con gasto previsto reciben más que los de descanso.
        assertTrue(targets[0].calorieTargetKcal > targets[1].calorieTargetKcal)
    }

    @Test
    fun uniformDistributionIsChosenExplicitly() {
        val base = NutritionEditorBase(2000, 150, 250, 55)
        val today = LocalDate.of(2026, 9, 21)
        val dates = (0L until 7L).map { today.plusDays(it) }

        val targets = weeklyTargetsFor(base, NutritionWeeklyDistributionMode.UNIFORM, dates)

        assertEquals(7 * base.caloriesKcal, targets.sumOf { it.calorieTargetKcal })
        assertEquals(
            targets.minOf { it.calorieTargetKcal },
            targets.maxOf { it.calorieTargetKcal },
        )
    }

    @Test
    fun midWeekDistributionKeepsTodayAndPastFixedAndPreservesTheBudget() {
        val base = NutritionEditorBase(2000, 150, 250, 55)
        val weekDates = (0L until 7L).map { monday.plusDays(it) }
        // Hoy es miércoles: lunes, martes y miércoles ya están FIJADOS por el
        // historial del mismo plan y consumen presupuesto.
        val fixed = weekDates.take(3).associateWith { 2000 }
        val future = weekDates.drop(3)
        val expenditures = future.associateWith { DayExpenditure.Estimated(500.0) }

        val result = weeklyDistributionResultFor(
            base = base,
            mode = NutritionWeeklyDistributionMode.VARIABLE,
            dates = future,
            expenditures = expenditures,
            fixedTargets = fixed,
        )

        // Los fijados no se mueven...
        assertEquals(fixed, result.fixedTargets)
        assertTrue(result.targetsByDate.keys.none { it in fixed })
        // ...y el reparto cubre TODA la ventana cuadrando el presupuesto total
        // (R = 14000 − 6000 = 8000 repartido en las 4 fechas futuras).
        assertEquals(4, result.targetsByDate.size)
        assertEquals(14_000, result.allTargets().values.sum())
        assertEquals(14_000 - 6_000, result.remainingBudgetKcal)
        assertEquals(8_000, result.targetsByDate.values.sum())
    }

    @Test
    fun noValidSolutionKeepsPreviousTargetsUntilNextWeek() {
        val base = NutritionEditorBase(2000, 150, 250, 55)
        val weekDates = (0L until 7L).map { monday.plusDays(it) }
        val previous = weekDates.associateWith { 2100 }

        val result = weeklyDistributionResultFor(
            base = base,
            mode = NutritionWeeklyDistributionMode.VARIABLE,
            dates = weekDates,
            expenditures = emptyMap(),
            // Ningún objetivo entero cabe en el rango → sin solución válida.
            bounds = 3_000..3_200,
            previousTargets = previous,
        )

        // El reparto vigente se devuelve INTACTO: la semana siguiente recalcula
        // sobre los nuevos gastos en vez de improvisar un valor.
        assertEquals(NutritionDistributionStatus.KEPT_PREVIOUS, result.status)
        assertEquals(previous, result.targetsByDate)
        assertTrue(result.fixedTargets.isEmpty())
    }

    @Test
    fun foodLoggingStaysAvailableInTrackingOnlyAndSkippedKeepsHidingNutrition() {
        val trackingOnly = com.example.kpkn.data.models.Settings(nutritionTrackingOnly = true)
        val skipped = com.example.kpkn.data.models.Settings(
            nutritionTrackingChoice = com.example.kpkn.data.models.NutritionTrackingChoice.SKIPPED,
        )

        // Solo registro: registro de alimentos disponible sin plan activo.
        assertTrue(isFoodLoggingAvailable(trackingOnly, hasActivePlan = false))
        assertFalse(areGoalDeficitAlertsAvailable(trackingOnly))
        assertTrue(areMealRemindersAvailable(trackingOnly.copy(mealReminderEnabled = true)))
        assertTrue(isNutritionVisible(trackingOnly))

        // SKIPPED conserva su semántica: oculta y silencia Nutrición.
        assertFalse(isNutritionVisible(skipped))
        assertFalse(isFoodLoggingAvailable(skipped, hasActivePlan = true))
        assertFalse(areMealRemindersAvailable(skipped.copy(mealReminderEnabled = true)))
    }
}
