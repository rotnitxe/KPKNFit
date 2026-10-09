package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanTuningContext
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardNutritionGoal
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanCallbacks
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanModel
import com.example.kpkn.screens.onboarding.design.WizardNutritionPlanPanel
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Textos y marcas del objetivo de nutrición: los tres subtextos de la dirección, el peso objetivo con su diferencia y su
 * estimación, y «Hasta tu objetivo» en el resultado (solo con Definir o Volumen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class NutritionGoalComposeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val noopCallbacks = WizardNutritionPlanCallbacks(
        onProtein = {},
        onCarbs = {},
        onFat = {},
        onPaceRate = {},
        onPacePreset = {},
        onChangeFinished = {},
        onReset = {},
        onSelectDay = {},
    )

    /** Un plan de 2.000 kcal con 2.400 de gasto: pierde unos 0,36 kg por semana. */
    private fun planTuning(direction: PlanDirection): PlanTuning {
        val values = PlanValues(kcal = 2000, proteinG = 160, carbsG = 200, fatG = 60)
        val context = PlanTuningContext(weightKg = 80.0, eerKcal = 2400.0, direction = direction, sex = EerSex.MALE)
        return PlanTuning(context, baseline = values, values = values)
    }

    @Test
    fun `el paso de direccion muestra los tres subtextos`() {
        rule.setContent {
            SetupBodyChoiceCards(
                step = SetupStepId.NUTRITION_DIRECTION,
                selected = emptySet(),
                onSelect = {},
                subtitles = nutritionDirectionSubtitles,
            )
        }
        rule.onNodeWithText("Comes un poco menos de lo que gastas para bajar grasa sin perder músculo.").assertIsDisplayed()
        rule.onNodeWithText("Comes lo que gastas para sostener tu peso mientras entrenas.").assertIsDisplayed()
        rule.onNodeWithText("Comes un poco más de lo que gastas para ganar músculo poco a poco.").assertIsDisplayed()
    }

    @Test
    fun `el resultado con objetivo y definir muestra hasta tu objetivo`() {
        rule.setContent {
            WizardNutritionPlanPanel(
                model = WizardNutritionPlanModel(
                    tuning = planTuning(PlanDirection.DEFICIT),
                    goal = WizardNutritionGoal(currentKg = 80.0, targetKg = 72.0),
                ),
                callbacks = noopCallbacks,
            )
        }
        rule.onNodeWithTag("setup-nutrition-goal").assertIsDisplayed()
        rule.onNodeWithText("Hasta tu objetivo").assertIsDisplayed()
    }

    @Test
    fun `mantener no añade la seccion de objetivo`() {
        rule.setContent {
            WizardNutritionPlanPanel(
                model = WizardNutritionPlanModel(
                    tuning = planTuning(PlanDirection.MAINTENANCE),
                    goal = WizardNutritionGoal(currentKg = 80.0, targetKg = 72.0),
                ),
                callbacks = noopCallbacks,
            )
        }
        assertTrue(rule.onAllNodesWithTag("setup-nutrition-goal").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `el peso objetivo sin fijar explica como fijarlo y su limite`() {
        rule.setContent {
            NutritionTargetControl(
                unit = WizardMassUnit.KG,
                currentKg = 80.0,
                direction = PlanDirection.DEFICIT,
                pace = WizardPacePreset.MEDIUM,
                committedKg = null,
                onCommit = {},
            )
        }
        rule.onNodeWithText("Desliza la regla y toca la cifra para fijarlo: entre 48 y 79,5 kg.").assertIsDisplayed()
    }

    @Test
    fun `el peso objetivo muestra la diferencia y la estimacion con el ritmo medio`() {
        rule.setContent {
            NutritionTargetControl(
                unit = WizardMassUnit.KG,
                currentKg = 80.0,
                direction = PlanDirection.DEFICIT,
                pace = WizardPacePreset.MEDIUM,
                committedKg = 71.5,
                onCommit = {},
            )
        }
        rule.onNodeWithText("−8,5 kg desde tus 80 kg").assertIsDisplayed()
        rule.onNodeWithText("≈ 18–24 semanas a ritmo medio").assertIsDisplayed()
    }

    @Test
    fun `la regla de peso no pasa de sus límites al arrastrar`() {
        var confirmed = Double.NaN
        rule.setContent {
            WizardWeightRule(
                unit = WizardMassUnit.KG,
                valueKg = 75.0,
                onValueChange = { confirmed = it },
                min = 72.0,
                max = 74.0,
            )
        }
        rule.waitForIdle()
        val ruleNode = rule.onNodeWithContentDescription("Regla de peso en kilogramos")
        // El arranque (75) queda en el límite más cercano.
        rule.onNodeWithTag("setup-weight-value").assertTextEquals("74 kg")
        // Arrastres largos en los dos sentidos: la regla se para en 72 y en 74 sin pasarse.
        repeat(3) {
            ruleNode.performTouchInput { swipeRight(startX = centerX - 200f, endX = centerX + 200f, startY = bottom - 30f) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("setup-weight-value").assertTextEquals("72 kg")
        repeat(3) {
            ruleNode.performTouchInput { swipeLeft(startX = centerX + 200f, endX = centerX - 200f, startY = bottom - 30f) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("setup-weight-value").assertTextEquals("74 kg")
        rule.onNodeWithTag("setup-weight-value").performClick()
        assertEquals(74.0, confirmed, 1e-9)
    }
}
