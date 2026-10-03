package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.domain.nutrition.MacroGoals
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionRecoveryEngineTest {

    private fun log(date: LocalDate, calories: Double, protein: Double): NutritionLog =
        NutritionLog(
            id = "log-$date",
            date = date.toString(),
            mealType = MealType.LUNCH,
            foods = listOf(
                LoggedFood(
                    id = "food-$date",
                    foodName = "Comida",
                    amount = 100.0,
                    calories = calories,
                    protein = protein,
                ),
            ),
        )

    /** Formato real del drawer (`FoodLoggerDrawer`) y de `MacroCalculator`: fecha + hora ISO. */
    private fun drawerLog(date: LocalDate, calories: Double, protein: Double): NutritionLog =
        NutritionLog(
            id = "drawer-$date",
            date = "${date}T12:00:00.000Z",
            mealType = MealType.LUNCH,
            foods = listOf(
                LoggedFood(
                    id = "food-$date",
                    foodName = "Comida",
                    amount = 100.0,
                    calories = calories,
                    protein = protein,
                ),
            ),
        )

    private fun deficitGoals(today: LocalDate): Map<LocalDate, DayGoalsResult> = mapOf(
        today to DayGoalsResult.Present(
            MacroGoals(calorieGoal = 2_000, proteinGoal = 100, carbGoal = 200, fatGoal = 60),
        ),
    )

    @Test
    fun emptyLogs_doNotAssumeDeficit() {
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = emptyList(),
            goalsByDate = mapOf(
                LocalDate.now() to DayGoalsResult.Present(
                    MacroGoals(calorieGoal = 2_500, proteinGoal = 150, carbGoal = 250, fatGoal = 70),
                ),
            ),
        )
        assertEquals(1.0, result.recoveryTimeMultiplier, 0.001)
        assertEquals(NutritionRecoveryStatus.MAINTENANCE, result.status)
    }

    @Test
    fun noGoals_doNotInferDeficitNorProteinPenalty() {
        // Ingesta muy baja: sin metas no se infiere déficit ni se penaliza la
        // proteína (mismo criterio neutro que «sin comidas en la ventana»).
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = listOf(log(LocalDate.now(), calories = 200.0, protein = 5.0)),
            goalsByDate = mapOf(
                LocalDate.now() to DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY),
                LocalDate.now().minusDays(1) to DayGoalsResult.Absent(GoalsAbsence.NO_GOAL),
            ),
        )
        assertEquals(1.0, result.recoveryTimeMultiplier, 0.001)
        assertEquals(NutritionRecoveryStatus.MAINTENANCE, result.status)
        assertTrue(result.factors.any { it.contains("Sin metas") })
    }

    @Test
    fun explicitZeroGoals_doNotInferDeficitNorProteinPenalty() {
        // Un 0 explícito no es una meta > 0 contra la que inferir déficit.
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = listOf(log(LocalDate.now(), calories = 200.0, protein = 5.0)),
            goalsByDate = mapOf(
                LocalDate.now() to DayGoalsResult.Present(
                    MacroGoals(calorieGoal = 0, proteinGoal = 0, carbGoal = 0, fatGoal = 0),
                ),
            ),
        )
        assertEquals(1.0, result.recoveryTimeMultiplier, 0.001)
        assertEquals(NutritionRecoveryStatus.MAINTENANCE, result.status)
        assertTrue(result.factors.any { it.contains("Sin metas") })
    }

    @Test
    fun calorieDeficitIsStillInferredWhenGoalsExist() {
        // Las fórmulas no cambian: con metas reales el déficit se calcula igual.
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = listOf(log(LocalDate.now(), calories = 400.0, protein = 10.0)),
            goalsByDate = mapOf(
                LocalDate.now() to DayGoalsResult.Present(
                    MacroGoals(calorieGoal = 2_000, proteinGoal = 100, carbGoal = 200, fatGoal = 60),
                ),
            ),
        )
        assertEquals(NutritionRecoveryStatus.DEFICIT, result.status)
        assertTrue(result.recoveryTimeMultiplier > 1.0)
    }

    @Test
    fun logDatedInTheDrawerFormatCountsInsideTheWindow() {
        // WP-U4 / C4: "${fecha}T12:00:00.000Z" hacía lanzar a LocalDate.parse(log.date) y la
        // comida se descartaba en silencio: AUGE veía «Sin comidas en la ventana».
        val today = LocalDate.now()
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = listOf(drawerLog(today, calories = 400.0, protein = 10.0)),
            goalsByDate = deficitGoals(today),
        )
        assertEquals(NutritionRecoveryStatus.DEFICIT, result.status)
        assertTrue(result.recoveryTimeMultiplier > 1.0)
        assertFalse(result.factors.any { it.contains("Sin comidas") })
    }

    @Test
    fun malformedLogDateIsIgnoredWithoutThrowing() {
        val today = LocalDate.now()
        val goals = deficitGoals(today)
        // Muy por encima de la meta: si se contara, el estado sería SURPLUS.
        val garbage = listOf("not-a-date", "2026", "").mapIndexed { index, date ->
            NutritionLog(
                id = "garbage-$index",
                date = date,
                mealType = MealType.LUNCH,
                foods = listOf(
                    LoggedFood(id = "g$index", foodName = "Comida", amount = 100.0, calories = 5_000.0, protein = 10.0),
                ),
            )
        }

        // Solo fechas rotas: nada que contar, resultado neutro y sin excepción.
        val onlyGarbage = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(garbage, goals)
        assertEquals(1.0, onlyGarbage.recoveryTimeMultiplier, 0.001)
        assertEquals(NutritionRecoveryStatus.MAINTENANCE, onlyGarbage.status)
        assertTrue(onlyGarbage.factors.any { it.contains("Sin comidas") })

        // Mezcladas con un registro válido solo cuenta el válido (déficit, no superávit).
        val mixed = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = garbage + drawerLog(today, calories = 400.0, protein = 10.0),
            goalsByDate = goals,
        )
        assertEquals(NutritionRecoveryStatus.DEFICIT, mixed.status)
    }

    @Test
    fun proteinPenaltyNeedsAnExplicitProteinGoal() {
        // Con meta calórica pero sin meta de proteína no se penaliza la proteína.
        val result = NutritionRecoveryEngine.computeNutritionRecoveryMultiplier(
            nutritionLogs = listOf(log(LocalDate.now(), calories = 4_000.0, protein = 10.0)),
            goalsByDate = mapOf(
                LocalDate.now() to DayGoalsResult.Present(
                    MacroGoals(calorieGoal = 2_000, proteinGoal = null, carbGoal = null, fatGoal = null),
                ),
            ),
        )
        assertEquals(NutritionRecoveryStatus.MAINTENANCE, result.status)
        assertEquals(1.0, result.recoveryTimeMultiplier, 0.001)
    }
}
