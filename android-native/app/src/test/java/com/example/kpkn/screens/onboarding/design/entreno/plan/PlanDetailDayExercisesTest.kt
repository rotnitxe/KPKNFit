package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.SwapKind
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.screens.onboarding.SetupPlanRevealExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Tu semana» con los ejercicios de cada día: tocar un día despliega su lista; «Cambiar» abre el panel con las alternativas
 * y elegir una llama a [PlanDetailOverlay]'s `onSwap`; un ejercicio cambiado se puede deshacer.
 */
@RunWith(RobolectricTestRunner::class)
// Ventana alta: con la de por defecto (320×470 dp) la barra inferior tapa el día y el toque no llega (como en la hoja de render).
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class PlanDetailDayExercisesTest {

    @get:Rule
    val rule = createComposeRule()

    private val card = PlanCardModel(
        id = "fuerza", title = "Fuerza y masa", kicker = "3 días", blurb = "Un programa de fuerza.",
        profile = TrainingGoalProfile.STRENGTH_MUSCLE, daysLabel = "3 días", minutesLabel = "60 min", levelLabel = "Intermedio", coverSeed = 3,
    )

    /** Torso el lunes (principal) y pierna el jueves: el jueves es el día que se despliega en las pruebas. */
    private val model = PlanDetailModel(
        card = card,
        description = "Tres días a la semana.",
        mainExercises = listOf("Press banca"),
        blocks = listOf(PlanBlockModel("Volumen", "Semanas 1–4", "Más series.")),
        week = listOf(PlanDayModel(1, "Torso", 60, 2, true), PlanDayModel(4, "Pierna", 50, 2, false)),
        reasons = emptyList(),
        notes = emptyList(),
        attribution = null,
    )

    private val jueves = mapOf(
        4 to listOf(
            SetupPlanRevealExercise(instanceId = "jue-1", name = "Sentadilla", setsLabel = "4 × 6–8", swappable = true),
            SetupPlanRevealExercise(instanceId = "jue-2", name = "Peso muerto", setsLabel = "3 × 5", swappable = true),
        ),
    )

    private val opciones = listOf(
        SwapOption(configId = "cfg-a", displayName = "Sentadilla frontal", variant = "Barra", pattern = "knee_dominant", kind = SwapKind.EQUIVALENT),
        SwapOption(configId = "cfg-b", displayName = "Prensa", variant = "", pattern = "knee_dominant", kind = SwapKind.SAME_PATTERN),
    )

    private var swapped: Pair<String, String>? = null
    private var undone: String? = null

    /**
     * Pinta [DetailSheet] directamente, sin la ventana de diálogo de [PlanDetailOverlay]: en Robolectric los toques inyectados
     * no llegan a la ventana del diálogo (la hoja no se abría con un toque). Es el mismo camino que la hoja de render.
     */
    private fun show(
        selected: Boolean = true,
        exercises: Map<Int, List<SetupPlanRevealExercise>> = jueves,
        options: List<SwapOption> = opciones,
    ) {
        rule.setContent {
            DetailSheet(
                model = model,
                selected = selected,
                reduced = true,
                blur = false,
                shown = { 1f },
                onChoose = {},
                onClose = {},
                exercises = exercises,
                swapOptionsOf = { options },
                onSwap = { instance, config -> swapped = instance to config },
                onUndoSwap = { undone = it },
            )
        }
    }

    @Test
    fun tappingTheTrainedDayOpensItsExercises() {
        show()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertDoesNotExist()
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertExists()
        rule.onNodeWithText("Sentadilla").assertExists()
        rule.onNodeWithText("4 × 6–8").assertExists()
        rule.onNodeWithText("Peso muerto").assertExists()
    }

    @Test
    fun theDayTargetIsAtLeast48DpHigh() {
        show()
        rule.onNodeWithTag("setup-plan-day-4").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingTheSameDayFoldsItAgain() {
        show()
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertExists()
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertDoesNotExist()
    }

    @Test
    fun aDayWithoutExercisesIsNotTappable() {
        show(exercises = emptyMap())
        rule.onNodeWithTag("setup-plan-day-4").assertDoesNotExist()
    }

    @Test
    fun cambiarOpensThePanelAndChoosingAnOptionSwapsIt() {
        show()
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-swap-jue-1").performScrollTo().performClick()
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertExists()
        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").performScrollTo().performClick()
        assertEquals("jue-1" to "cfg-a", swapped)
    }

    @Test
    fun withoutOptionsThePanelSaysSo() {
        show(options = emptyList())
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-swap-jue-1").performScrollTo().performClick()
        rule.onNodeWithText("No hay otra opción con el material de este día.").assertExists()
        assertNull(swapped)
    }

    @Test
    fun aSwappedExerciseCanBeUndone() {
        val cambiado = mapOf(
            4 to listOf(
                SetupPlanRevealExercise(
                    instanceId = "jue-1", name = "Sentadilla frontal", setsLabel = "4 × 6–8",
                    swappable = true, swappedConfigId = "cfg-a",
                ),
            ),
        )
        show(exercises = cambiado)
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-undo-jue-1").assertExists()
        rule.onNodeWithTag("setup-plan-undo-jue-1").performScrollTo().performClick()
        assertEquals("jue-1", undone)
    }

    @Test
    fun theChangeActionIsOnlyOfferedForTheChosenProgram() {
        show(selected = false)
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertExists()
        rule.onNodeWithTag("setup-plan-swap-jue-1").assertDoesNotExist()
    }
}
