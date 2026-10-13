package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.DailyEnergyBalance
import com.example.kpkn.data.models.DailyEnergyStatus
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.domain.nutrition.MacroGoals
import java.time.LocalDate

/**
 * Datos de ejemplo de la raíz de Nutrición (N1) para las pruebas y la hoja de render. «Hoy» es el 13 de octubre de 2026 y la tira
 * va de hoy −3 a hoy +3.
 */
internal object NutritionSamples {
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
}
