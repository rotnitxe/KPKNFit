package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.DailyEnergyBalance
import com.example.kpkn.data.models.DailyEnergyStatus
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.domain.nutrition.MacroGoals
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.domain.nutrition.NutritionPlanEditorMode
import com.example.kpkn.domain.nutrition.NutritionPlanRecommendation
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.ResolvedTag
import java.time.LocalDate

/**
 * Datos de ejemplo de las hojas de render y las pruebas de Nutrición. No son datos de la app: solo sirven para dibujar las pantallas
 * sin base de datos ni repositorios. En la raíz (N1), «hoy» es el 13 de octubre de 2026 y la tira va de hoy −3 a hoy +3.
 */
internal object NutritionSamples {

    /** Lo que una persona escribiría en «¿Qué comiste?». */
    const val DESCRIBE_TEXT = "Dos huevos con tostadas y un café"

    /** Una fila de resultado de búsqueda, con lo que pinta la fila. */
    data class SearchRow(val name: String, val caption: String?, val kcal: Int, val basisGrams: Int)

    /** Cinco resultados para la búsqueda «pan», con su marca o su nota de coincidencia cuando la tienen. */
    val panResults: List<SearchRow> = listOf(
        SearchRow("Pan integral de molde", "Bimbo", 247, 100),
        SearchRow("Pan blanco de molde", "Coincidencia media", 265, 100),
        SearchRow("Pan árabe", "Coincidencia baja", 275, 100),
        SearchRow("Pan de hamburguesa", null, 258, 100),
        SearchRow("Pan de centeno", "Marca blanca", 259, 100),
    )

    val today: LocalDate = LocalDate.of(2026, 10, 13)

    /** Siete días de la tira con sus kcal (en el orden de la tira) y una meta común. */
    fun week(kcals: List<Int>, goal: Int?): List<NutritionWeekDay> = (-3L..3L).mapIndexed { index, offset ->
        NutritionWeekDay(
            date = today.plusDays(offset).toString(),
            kcal = kcals[index],
            kcalGoal = goal,
            isToday = offset == 0L,
        )
    }

    /** Un día con meta: 2.300 kcal, de las que van comidas 1.240 y quedan 1.060. */
    val goals: DayGoalsResult = DayGoalsResult.Present(
        MacroGoals(calorieGoal = 2300, proteinGoal = 160, carbGoal = 260, fatGoal = 75),
    )

    /** Sin meta, con plan activo: solo seguimiento. */
    val trackingOnly: DayGoalsResult = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY)

    val totals: DailyMacroTotals = DailyMacroTotals(
        calories = 1240.0,
        protein = 92.0,
        carbs = 118.0,
        fats = 38.0,
        caffeineMg = 120.0,
        creatineG = 5.0,
    )

    /** Pasado de kcal: 120 por encima de la meta. */
    val totalsOver: DailyMacroTotals = DailyMacroTotals(calories = 2420.0, protein = 150.0, carbs = 280.0, fats = 90.0, caffeineMg = 180.0, creatineG = 5.0)

    val balance: DailyEnergyBalance = DailyEnergyBalance(
        consumedKcal = 1240,
        trainingBurnKcal = 320,
        netKcal = 1240,
        targetKcal = 2300,
        deltaFromTarget = -1060,
        status = DailyEnergyStatus.DEFICIT,
    )

    /** Un caso de la hoja de render: lo que pintan la cabecera, «Tu día», el balance y los suplementos. */
    data class RenderCase(
        val loaded: Boolean,
        val hasPlan: Boolean,
        val days: List<NutritionWeekDay>,
        val totals: DailyMacroTotals,
        val goals: DayGoalsResult,
        val balance: DailyEnergyBalance,
    )

    /** Los casos de la hoja, con su nombre de archivo: con plan, pasado de kcal, sin plan, solo seguimiento y cargando. */
    fun renderCases(): List<Pair<String, RenderCase>> {
        val weekKcals = listOf(2120, 2480, 1930, 1240, 0, 0, 0)
        return listOf(
            "con_plan" to RenderCase(
                loaded = true,
                hasPlan = true,
                days = week(weekKcals, goal = 2300),
                totals = totals,
                goals = goals,
                balance = balance,
            ),
            "pasado" to RenderCase(
                loaded = true,
                hasPlan = true,
                days = week(weekKcals, goal = 2300),
                totals = totalsOver,
                goals = goals,
                balance = balance.copy(consumedKcal = 2420, trainingBurnKcal = 0, deltaFromTarget = 120),
            ),
            "sin_plan" to RenderCase(
                loaded = true,
                hasPlan = false,
                days = week(listOf(0, 0, 0, 1240, 0, 0, 0), goal = null),
                totals = totals,
                goals = DayGoalsResult.Absent(GoalsAbsence.NO_GOAL),
                balance = balance.copy(targetKcal = 0, trainingBurnKcal = 0, deltaFromTarget = 0, status = DailyEnergyStatus.MAINTENANCE),
            ),
            "solo_seguimiento" to RenderCase(
                loaded = true,
                hasPlan = true,
                days = week(listOf(0, 0, 0, 1240, 0, 0, 0), goal = null),
                totals = totals,
                goals = trackingOnly,
                balance = balance.copy(targetKcal = 0, trainingBurnKcal = 0, deltaFromTarget = 0, status = DailyEnergyStatus.MAINTENANCE),
            ),
            "cargando" to RenderCase(
                loaded = false,
                hasPlan = false,
                days = emptyList(),
                totals = DailyMacroTotals(),
                goals = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY),
                balance = DailyEnergyBalance(),
            ),
        )
    }

    /** Alimento interpretado y resuelto, sin dudas: la fila normal. */
    val chickenTag: ResolvedTag = ResolvedTag(
        tag = "pollo a la plancha",
        portion = PortionPreset.MEDIUM,
        amountGrams = 150.0,
        baseAmountGrams = 150.0,
        isResolved = true,
        loggedFood = LoggedFood(
            id = "pollo",
            foodName = "Pollo a la plancha",
            amount = 150.0,
            calories = 248.0,
            protein = 46.0,
            carbs = 0.0,
            fats = 5.4,
        ),
    )

    /** Tortilla con la composición por aclarar: su fila lleva la pregunta pendiente ([tortillaQuestion]). */
    val tortillaTag: ResolvedTag = ResolvedTag(
        tag = "tortilla",
        portion = PortionPreset.LARGE,
        amountGrams = 180.0,
        baseAmountGrams = 180.0,
        isResolved = false,
        loggedFood = LoggedFood(
            id = "tortilla",
            foodName = "Tortilla de papas",
            amount = 180.0,
            calories = 320.0,
            protein = 11.0,
            carbs = 28.0,
            fats = 18.0,
        ),
    )

    /** La pregunta de la tortilla: qué llevaba, con dos candidatos y las composiciones fijas de la hoja. */
    val tortillaQuestion: ClarificationRequest = ClarificationRequest.Identity(
        requestId = "tortilla_composition",
        candidateIds = listOf("tortilla_maiz", "tortilla_papa"),
        candidateLabels = listOf("Tortilla de maíz", "Tortilla de papas"),
    )

    /** Arroz estimado con rango: la cifra lleva «≈» y su rango de kcal. */
    val riceTag: ResolvedTag = ResolvedTag(
        tag = "arroz blanco",
        portion = PortionPreset.MEDIUM,
        amountGrams = 200.0,
        baseAmountGrams = 200.0,
        isResolved = true,
        isUncertain = true,
        loggedFood = LoggedFood(
            id = "arroz",
            foodName = "Arroz blanco cocido",
            amount = 200.0,
            calories = 260.0,
            protein = 5.0,
            carbs = 57.0,
            fats = 0.6,
            caloriesMin = 220.0,
            caloriesMax = 300.0,
            isUncertain = true,
        ),
    )
}

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
