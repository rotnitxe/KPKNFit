package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.DailyEnergyBalance
import com.example.kpkn.data.models.DailyEnergyStatus
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealGroup
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.domain.nutrition.MacroGoals
import com.example.kpkn.domain.nutrition.computeMealGroups
import com.example.kpkn.screens.nutrition.design.NutritionWeekBar
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

    // ─── Módulos de comida y «Últimos 7 días» (N2) ───────────────────────

    /** Un alimento de ejemplo: kcal y macros del alimento; [min] y [max] si su estimación tiene rango. */
    fun food(
        name: String,
        amount: Double,
        unit: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        min: Double? = null,
        max: Double? = null,
    ): LoggedFood = LoggedFood(
        id = "food-${name.replace(' ', '-')}",
        foodName = name,
        amount = amount,
        unit = unit,
        calories = kcal,
        protein = protein,
        carbs = carbs,
        fats = fats,
        caloriesMin = min,
        caloriesMax = max,
        isUncertain = min != null,
    )

    /** Un registro de ejemplo del día de la muestra, en la comida [meal]. */
    fun mealLog(id: String, meal: MealType, vararg foods: LoggedFood): NutritionLog = NutritionLog(
        id = id,
        date = today.toString() + "T12:00:00.000Z",
        mealType = meal,
        foods = foods.toList(),
        status = NutritionStatus.CONSUMED,
    )

    /** Los registros del día de la muestra: desayuno con una estimación con rango, almuerzo, merienda vacía y cena con dos registros. */
    val mealLogs: List<NutritionLog> = listOf(
        mealLog("log-avena", MealType.BREAKFAST, food("Avena con leche", 250.0, "g", 230.0, 9.0, 40.0, 4.0)),
        mealLog(
            "log-pan",
            MealType.BREAKFAST,
            food("Pan integral", 60.0, "g", 150.0, 5.0, 28.0, 2.0, min = 140.0, max = 160.0),
            food("Queso fresco", 40.0, "g", 90.0, 6.0, 1.0, 7.0, min = 80.0, max = 110.0),
        ),
        mealLog("log-pollo", MealType.LUNCH, food("Pollo con arroz", 350.0, "g", 520.0, 45.0, 60.0, 10.0)),
        mealLog("log-atun", MealType.DINNER, food("Ensalada de atún", 300.0, "g", 380.0, 30.0, 20.0, 18.0)),
        mealLog("log-yogur", MealType.DINNER, food("Yogur griego", 150.0, "g", 95.0, 15.0, 6.0, 1.0)),
    )

    /** Los cuatro grupos de comida del día de la muestra, como los entrega el ViewModel. */
    val mealGroups: List<MealGroup> = computeMealGroups(mealLogs)

    /** Las siete barras de «Últimos 7 días» (del 7 al 13 de octubre): dos días sin registros, un día con banda incierta y hoy. */
    val weekBars: List<NutritionWeekBar> = listOf(
        NutritionWeekBar(date = "2026-10-07", kcal = 2120, goal = 2300),
        NutritionWeekBar(date = "2026-10-08", kcal = null, goal = 2300),
        NutritionWeekBar(date = "2026-10-09", kcal = 1930, kcalMin = 1800, kcalMax = 2100, goal = 2300),
        NutritionWeekBar(date = "2026-10-10", kcal = 2480, goal = 2300),
        NutritionWeekBar(date = "2026-10-11", kcal = null, goal = 2300),
        NutritionWeekBar(date = "2026-10-12", kcal = 1240, goal = 2300),
        NutritionWeekBar(date = "2026-10-13", kcal = 1820, goal = 2300, isToday = true),
    )
}
