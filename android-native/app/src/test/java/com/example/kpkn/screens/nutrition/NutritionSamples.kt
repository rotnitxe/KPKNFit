package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.domain.nutrition.NutritionPlanEditorMode
import com.example.kpkn.domain.nutrition.NutritionPlanRecommendation
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import java.time.LocalDate

/** Datos de ejemplo del editor de Nutrición para las hojas de render y las pruebas de su raíz. Deterministas: sin reloj ni azar. */
object NutritionEditorSamples {

    /** Día de referencia de la muestra (martes): la franja marca ese día. */
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    /** Base revisada de la muestra: 2.150 kcal, con el total Atwater distinto (2.110), para que se lea la línea del total. */
    val base = NutritionEditorBase(caloriesKcal = 2150, proteinG = 160, carbsG = 210, fatG = 70)

    /** Base con la que se cargó el plan: el «antes» del cambio que muestra «Guardar». */
    val originalBase = NutritionEditorBase(caloriesKcal = 2300, proteinG = 170, carbsG = 230, fatG = 70)

    /** Plan existente con meta de peso, datos de cálculo completos y ecuación elegida. */
    val fullDraft = NutritionPlanEditorDraft(
        planId = "plan-muestra",
        direction = PlanDirection.DEFICIT,
        goalMetric = GoalMetric.WEIGHT,
        targetValueText = "68,5",
        ageText = "34",
        heightText = "178",
        weightText = "74,2",
        weightUnit = "kg",
        equationSex = EerSex.MALE,
        activity = EerActivity.ACTIVE,
        base = base,
        baseEdited = true,
        provenance = NutritionEditorProvenance.AUTOMATIC,
        mode = NutritionPlanEditorMode.ACTIVE_PLAN,
        weeklyDistribution = NutritionWeeklyDistributionMode.VARIABLE,
    )

    /** Recomendación con el mantenimiento (EER) de la muestra: el anillo exterior lleva su marca del 100 %. */
    val recommendation = NutritionPlanRecommendation(
        direction = PlanDirection.DEFICIT,
        eerKcal = 2380.0,
        calorieTargetKcal = 2150,
        snapshot = NutritionPlanCalculationSnapshot(
            engineVersion = "eer-2023-v1",
            formula = "muestra",
            calculatedAt = "2026-10-13T00:00:00Z",
        ),
    )

    /** La semana de la muestra, de lunes a domingo, con sus kcal y macros. */
    val weekly: List<NutritionEditorDayTarget> = listOf(
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 12), 2150, 160, 210, 70),
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 13), 2080, 150, 200, 70),
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 14), 2400, 170, 260, 75),
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 15), 2150, 160, 210, 70),
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 16), 2300, 165, 230, 72),
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 17), 1950, 150, 190, 65),
        NutritionEditorDayTarget(LocalDate.of(2026, 10, 18), 2200, 160, 220, 70),
    )

    /** Un plan existente con cambios sin guardar: base revisada, semana y el «antes» de la base. */
    fun editedPlanState(): NutritionPlanEditorUiState = NutritionPlanEditorUiState(
        isLoading = false,
        draft = fullDraft,
        recommendation = recommendation,
        base = base,
        originalBase = originalBase,
        weeklyTargets = weekly,
        isDirty = true,
    )
}
