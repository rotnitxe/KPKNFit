package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La hoja de detalle con Compose sobre Robolectric: sus secciones en orden y con sus textos de COPY, el botón principal
 * («Elegir este programa» o «Programa elegido»), cerrar, y que no se enseñen más de seis ejercicios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanDetailOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val card = PlanCardModel(
        id = "texas", title = "Texas Method", kicker = "Mark Rippetoe", blurb = "Un clásico de fuerza.",
        profile = TrainingGoalProfile.POWERLIFTING, daysLabel = "3 días", minutesLabel = "75 min", levelLabel = "Intermedio", coverSeed = 11,
    )

    private fun model(
        exercises: List<String> = listOf("Sentadilla", "Press banca", "Peso muerto"),
        notes: List<String> = listOf("Versión inicial: aún faltan variantes."),
        attribution: String? = "Basado en el método de Mark Rippetoe.",
        week: List<PlanDayModel> = listOf(PlanDayModel(1, "Volumen", 75, 6, true), PlanDayModel(3, "Ligero", 50, 5, false)),
    ) = PlanDetailModel(
        card = card,
        description = "Un programa que reparte la semana en volumen, intensidad y recuperación.",
        mainExercises = exercises,
        blocks = listOf(
            PlanBlockModel("Volumen", "Semanas 1–4", "Más series y repeticiones."),
            PlanBlockModel("Intensidad", "Semanas 5–8", "Cargas más cercanas a tu máximo."),
        ),
        week = week,
        reasons = listOf("Entrenas 3 días: es la frecuencia ideal.", "Tienes barra, rack y banco."),
        notes = notes,
        attribution = attribution,
    )

    private var chosen = 0
    private var closed = 0

    private fun show(model: PlanDetailModel = model(), selected: Boolean = false) {
        rule.setContent {
            PlanDetailOverlay(model, selected, onChoose = { chosen++ }, onClose = { closed++ }, reduced = true)
        }
    }

    @Test
    fun theSheetHasItsTag() {
        show()
        rule.onNodeWithTag("setup-plan-detail").assertExists()
        assertEquals("setup-plan-detail", PLAN_DETAIL_TAG)
    }

    @Test
    fun theSectionsAreThereWithTheirTitles() {
        show()
        rule.onNodeWithText("Ejercicios principales").assertExists()
        rule.onNodeWithText("Estructura").assertExists()
        rule.onNodeWithText("Tu semana").assertExists()
        rule.onNodeWithText("Por qué este programa").assertExists()
        // Contenido de cada una.
        rule.onNodeWithText("Press banca").assertExists()
        rule.onNodeWithText("Semanas 5–8").assertExists()
        rule.onNodeWithText("Entrenas 3 días: es la frecuencia ideal.").assertExists()
        rule.onNodeWithText("Versión inicial: aún faltan variantes.").assertExists()
        rule.onNodeWithText("Basado en el método de Mark Rippetoe.").assertExists()
    }

    @Test
    fun theCoverIsTheHeaderOfTheSheet() {
        show()
        rule.onNodeWithText("Texas Method").assertExists()
        rule.onNodeWithText("MARK RIPPETOE").assertExists()
        rule.onNodeWithText("3 días  ·  75 min  ·  Intermedio").assertExists()
    }

    @Test
    fun theGentleFixedSentenceAndThePrimaryButtonAreThere() {
        show()
        rule.onNodeWithText("Podrás modificarlo libremente después.").assertExists()
        rule.onNodeWithText("Elegir este programa").assertExists()
        rule.onNodeWithText("Programa elegido").assertDoesNotExist()
    }

    @Test
    fun aChosenProgramShowsTheDoneButton() {
        show(selected = true)
        rule.onNodeWithText("Programa elegido").assertExists()
        rule.onNodeWithText("Elegir este programa").assertDoesNotExist()
    }

    @Test
    fun chooseReportsTheTap() {
        show()
        rule.onNodeWithTag("setup-plan-detail-choose").performClick()
        assertEquals(1, chosen)
        assertEquals(0, closed)
    }

    @Test
    fun closeReportsTheTapOnce() {
        show()
        rule.onNodeWithTag("setup-plan-detail-close").performClick()
        rule.waitForIdle()
        assertEquals(1, closed)
        assertEquals(0, chosen)
    }

    @Test
    fun theCloseButtonIsAnnouncedAndBigEnough() {
        show()
        val node = rule.onNodeWithTag("setup-plan-detail-close")
        node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val config = node.fetchSemanticsNode().config
        assertEquals("Cerrar", config[SemanticsProperties.ContentDescription].joinToString())
        assertEquals(Role.Button, config[SemanticsProperties.Role])
    }

    @Test
    fun theMainButtonIsABigTarget() {
        show()
        rule.onNodeWithTag("setup-plan-detail-choose").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun atMostSixExercisesAreShown() {
        val eight = (1..8).map { "Ejercicio $it" }
        show(model(exercises = eight))
        for (i in 1..6) rule.onNodeWithText("Ejercicio $i").assertExists()
        rule.onNodeWithText("Ejercicio 7").assertDoesNotExist()
        rule.onNodeWithText("Ejercicio 8").assertDoesNotExist()
        assertEquals(6, MAX_MAIN_EXERCISES)
    }

    @Test
    fun emptySectionsAreLeftOut() {
        show(model(exercises = emptyList(), notes = emptyList(), attribution = null, week = emptyList()))
        rule.onNodeWithText("Ejercicios principales").assertDoesNotExist()
        rule.onNodeWithText("Tu semana").assertDoesNotExist()
        rule.onNodeWithText("Basado en el método de Mark Rippetoe.").assertDoesNotExist()
        // Las que sí tienen contenido siguen.
        rule.onNodeWithText("Estructura").assertExists()
    }

    @Test
    fun theWeekIsAnnouncedDayByDay() {
        assertEquals("Lunes: Volumen, 75 min, 6 ejercicios, sesión principal", weekDayDescription(1, PlanDayModel(1, "Volumen", 75, 6, true)))
        assertEquals("Miércoles: Ligero, 50 min, 5 ejercicios", weekDayDescription(3, PlanDayModel(3, "Ligero", 50, 5, false)))
        assertEquals("Domingo: descanso", weekDayDescription(7, null))
    }

    @Test
    fun theWidestTitleDecidesIfTheyFitUnderTheirCircle() {
        val widths = mapOf("Torso" to 30f, "Intensidad" to 66f, "Pierna" to 36f)
        assertEquals(66f, widestTitlePx(widths.keys.toList()) { widths.getValue(it) }, 0f)
        assertEquals(0f, widestTitlePx(emptyList()) { 10f }, 0f)
        assertFalse(widestTitlePx(listOf("Torso")) { widths.getValue(it) } > 41f)
        assertTrue(widestTitlePx(listOf("Intensidad")) { widths.getValue(it) } > 41f)
    }
}
