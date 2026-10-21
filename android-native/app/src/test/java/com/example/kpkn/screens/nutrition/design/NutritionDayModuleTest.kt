package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.GoalsAbsence
import com.example.kpkn.screens.nutrition.NutritionSamples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionDayModuleTest {

    @get:Rule
    val rule = createComposeRule()

    /** Pinta «Tu día» con los datos de ejemplo; lo que cambia de un caso a otro se pasa por parámetro. */
    @Composable
    private fun Day(
        loaded: Boolean = true,
        goals: DayGoalsResult = NutritionSamples.goals,
        hasPlan: Boolean = true,
        totals: DailyMacroTotals = NutritionSamples.totals,
        onSelect: (String) -> Unit = {},
    ) {
        NutritionDayModule(
            loaded = loaded,
            days = NutritionSamples.week(listOf(2120, 2480, 1930, 1240, 0, 0, 0), goal = (goals as? DayGoalsResult.Present)?.goals?.calorieGoal),
            selectedDate = NutritionSamples.today.toString(),
            totals = totals,
            goals = goals,
            hasActivePlan = hasPlan,
            onSelectDate = onSelect,
            onDefineGoals = {},
        )
    }

    @Test
    fun conPlanSeVenLosAnillosYLoQueQuedaDelDia() {
        rule.setContent { Day() }

        rule.onNodeWithTag("setup-nutrition-rings").assertExists()
        rule.onNodeWithText("te quedan 1.060").assertExists()
        rule.onNodeWithText("TU DÍA").assertExists()
    }

    @Test
    fun sinObjetivosOfreceDefinirSinAnillos() {
        rule.setContent {
            Day(goals = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY), hasPlan = false)
        }

        rule.onNodeWithText("Aún no tienes objetivos.").assertExists()
        rule.onNodeWithText("Definir objetivos").assertExists()
        rule.onNodeWithTag("setup-nutrition-rings").assertDoesNotExist()
    }

    @Test
    fun conPlanSinMetaEsSoloSeguimientoSinOfrecerMetas() {
        rule.setContent {
            Day(goals = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY), hasPlan = true)
        }

        rule.onNodeWithText("Definir objetivos").assertDoesNotExist()
        rule.onNodeWithText("Aún no tienes objetivos.").assertDoesNotExist()
        rule.onNodeWithTag("setup-nutrition-rings").assertDoesNotExist()
    }

    @Test
    fun mientrasCargaEsUnEsqueletoYNuncaSinObjetivos() {
        rule.setContent {
            Day(loaded = false, goals = DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY), hasPlan = false)
        }

        rule.onNodeWithText("TU DÍA").assertDoesNotExist()
        rule.onNodeWithText("Aún no tienes objetivos.").assertDoesNotExist()
        rule.onNodeWithText("Definir objetivos").assertDoesNotExist()
    }

    @Test
    fun tocarUnDiaPideSuFecha() {
        var picked: String? = null
        rule.setContent { Day(onSelect = { picked = it }) }

        rule.onNodeWithTag("nutrition-day-2026-10-10").performClick()

        assertEquals("2026-10-10", picked)
    }
}
