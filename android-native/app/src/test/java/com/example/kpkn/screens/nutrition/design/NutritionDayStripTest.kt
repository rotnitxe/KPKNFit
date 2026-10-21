package com.example.kpkn.screens.nutrition.design

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.kpkn.screens.nutrition.NutritionSamples
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Tu día» tras la revisión de N1: la línea de entreno del día elegido y la tira con el día elegido marcado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class NutritionDayStripTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Day(training: Int, selected: String = NutritionSamples.today.toString()) {
        NutritionDayModule(
            loaded = true,
            days = NutritionSamples.week(listOf(2120, 2480, 1930, 1240, 0, 0, 0), goal = 2300),
            selectedDate = selected,
            totals = NutritionSamples.totals,
            goals = NutritionSamples.goals,
            hasActivePlan = true,
            onSelectDate = {},
            onDefineGoals = {},
            trainingBurnKcal = training,
        )
    }

    @Test
    fun conEntrenoSeLeeSuLinea() {
        rule.setContent { Day(training = 320) }
        rule.onNodeWithText("Entreno · 320 kcal").assertExists()
    }

    @Test
    fun sinEntrenoNoHayLinea() {
        rule.setContent { Day(training = 0) }
        rule.onNodeWithText("Entreno", substring = true).assertDoesNotExist()
    }

    @Test
    fun elDiaElegidoSeMarcaYHoyNoSiNoEsElElegido() {
        rule.setContent { Day(training = 0, selected = "2026-10-10") }
        rule.onNodeWithTag("nutrition-day-2026-10-10").assertIsSelected()
        rule.onNodeWithTag("nutrition-day-2026-10-13").assertIsNotSelected()
    }
}
