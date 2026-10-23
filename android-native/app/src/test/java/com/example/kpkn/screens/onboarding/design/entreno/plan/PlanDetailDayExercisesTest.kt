package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import org.robolectric.annotation.GraphicsMode

/**
 * «Tu semana» con los ejercicios de cada día: tocar un día despliega su lista; «Cambiar» abre el panel con las alternativas
 * y elegir una llama a [PlanDetailOverlay]'s `onSwap`; un ejercicio cambiado se puede deshacer.
 */
@RunWith(RobolectricTestRunner::class)
// Ventana alta: con la de por defecto (320×470 dp) la barra inferior tapa el día y el toque no llega (como en la hoja de render).
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
// Gráficos nativos: los títulos se miden de verdad y «Tu semana» enumera las sesiones en filas cuando no caben bajo los círculos.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        detail: PlanDetailModel = model,
    ) {
        rule.setContent {
            DetailSheet(
                model = detail,
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

    /** Con títulos que no caben bajo los círculos, «Tu semana» enumera las sesiones en filas: tocar la fila despliega ese día. */
    @Test
    fun tappingTheSessionRowOpensItsDayLikeTheCircle() {
        val wide = model.copy(
            week = listOf(
                PlanDayModel(1, "Torso B", 65, 6, true),
                PlanDayModel(4, "Pierna A de fuerza máxima", 64, 7, false),
            ),
        )
        show(detail = wide)
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertDoesNotExist()
        rule.onNodeWithTag("setup-plan-session-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-exercise-jue-1").assertExists()
        rule.onNodeWithText("Sentadilla").assertExists()
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

    private fun openThePanel() {
        rule.onNodeWithTag("setup-plan-day-4").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-swap-jue-1").performScrollTo().performClick()
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertExists()
    }

    /** G4: «Cambiar» abre la hoja con lo que se reemplaza, las alternativas y el botón que confirma; nada se aplica al abrirla. */
    @Test
    fun cambiarOpensTheReplacementSheetWithoutChangingAnything() {
        show()
        openThePanel()

        // «Cambiar» es también la acción de cada fila: el título de la hoja es el de dentro del panel.
        rule.onNode(hasText("Cambiar") and hasAnyAncestor(hasTestTag(SWAP_PANEL_TAG))).assertExists()
        rule.onNodeWithText("REEMPLAZANDO").assertExists()
        rule.onNodeWithTag(SWAP_CURRENT_TAG).assertExists()
        rule.onNodeWithText("PRIMERO LOS MÁS PARECIDOS").assertExists()
        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(SWAP_KEEPS_TAG).assertTextEquals("Se mantienen las series del plan: 4 × 6–8")
        assertNull(swapped)
    }

    /** G4: parte con la más parecida marcada; marcar otra cambia el botón y sigue sin aplicar nada hasta confirmar. */
    @Test
    fun theFirstOptionStartsMarkedAndPickingAnotherOnlyChangesTheButton() {
        show()
        openThePanel()

        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").assertIsSelected()
        rule.onNodeWithTag(SWAP_CONFIRM_TAG).assertTextEquals("Cambiar por Sentadilla frontal")

        rule.onNodeWithTag("setup-plan-swap-option-cfg-b").performClick()

        rule.onNodeWithTag("setup-plan-swap-option-cfg-b").assertIsSelected()
        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").assertIsNotSelected()
        rule.onNodeWithTag(SWAP_CONFIRM_TAG).assertTextEquals("Cambiar por Prensa")
        assertNull("elegir no aplica: solo confirmar", swapped)
    }

    /** G4: confirmar aplica la alternativa marcada y cierra la hoja. */
    @Test
    fun confirmingAppliesTheMarkedOptionAndClosesTheSheet() {
        show()
        openThePanel()
        rule.onNodeWithTag("setup-plan-swap-option-cfg-b").performClick()

        rule.onNodeWithTag(SWAP_CONFIRM_TAG).performClick()

        assertEquals("jue-1" to "cfg-b", swapped)
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertDoesNotExist()
    }

    /** G4: confirmar sin tocar nada aplica la primera, la más parecida. */
    @Test
    fun confirmingWithoutPickingAppliesTheMostSimilarOne() {
        show()
        openThePanel()

        rule.onNodeWithTag(SWAP_CONFIRM_TAG).performClick()

        assertEquals("jue-1" to "cfg-a", swapped)
    }

    /** G4: cancelar, el velo y Atrás cierran la hoja sin tocar el programa. */
    @Test
    fun cancellingTheScrimAndTheCancelButtonCloseTheSheetWithoutSwapping() {
        show()
        openThePanel()
        rule.onNodeWithTag(SWAP_CANCEL_TAG).performClick()
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertDoesNotExist()
        assertNull(swapped)

        rule.onNodeWithTag("setup-plan-swap-jue-1").performScrollTo().performClick()
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertExists()
        // El velo solo se ve sobre la hoja: se toca en su esquina de arriba.
        rule.onNodeWithTag(SWAP_SCRIM_TAG).performTouchInput { click(Offset(10f, 10f)) }
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertDoesNotExist()
        assertNull(swapped)
    }

    /** G4: al cancelar y volver a abrir, la hoja parte otra vez de la más parecida. */
    @Test
    fun reopeningTheSheetStartsAgainFromTheFirstOption() {
        show()
        openThePanel()
        rule.onNodeWithTag("setup-plan-swap-option-cfg-b").performClick()
        rule.onNodeWithTag(SWAP_CANCEL_TAG).performClick()

        rule.onNodeWithTag("setup-plan-swap-jue-1").performScrollTo().performClick()

        rule.onNodeWithTag("setup-plan-swap-option-cfg-a").assertIsSelected()
    }

    @Test
    fun withoutOptionsThePanelSaysSoAndOffersNothingToConfirm() {
        show(options = emptyList())
        openThePanel()

        rule.onNodeWithText("No hay otra opción con el material de este día.").assertExists()
        rule.onNodeWithTag(SWAP_CONFIRM_TAG).performClick()
        assertNull(swapped)
        rule.onNodeWithTag(SWAP_CANCEL_TAG).performClick()
        rule.onNodeWithTag(SWAP_PANEL_TAG).assertDoesNotExist()
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
