package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El carrusel con Compose sobre Robolectric: cada tarjeta es un botón con su marca y el estado de selección real, «Elegir» y
 * «Ver detalles» hablan de la tarjeta central, la elegida lleva «Elegido» y una sola tarjeta se muestra sin pager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanCarouselTest {

    @get:Rule
    val rule = createComposeRule()

    private fun card(id: String, title: String, seed: Int, badge: String? = null) = PlanCardModel(
        id = id, title = title, kicker = "Jim Wendler", blurb = "Blurb de $title.",
        profile = TrainingGoalProfile.POWERLIFTING, daysLabel = "4 días", minutesLabel = "60 min",
        levelLabel = "Intermedio", badge = badge, coverSeed = seed,
    )

    private val cards = listOf(card("a", "Programa A", 1), card("b", "Programa B", 2), card("c", "Programa C", 3))
    private val selects = mutableListOf<String>()
    private val opens = mutableListOf<String>()

    private fun show(list: List<PlanCardModel> = cards, selectedId: String? = null) {
        // Como en el wizard, el carrusel vive dentro de una página que se desplaza.
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.width(320.dp)) {
                    PlanCarousel(list, selectedId, onSelect = { selects += it }, onOpen = { opens += it }, reduced = true)
                }
            }
        }
    }

    @Test
    fun theCentralCardAndItsNeighbourAreThere() {
        show()
        rule.onNodeWithTag("setup-plan-card-a").assertExists()
        rule.onNodeWithTag("setup-plan-card-b").assertExists()
        assertEquals("setup-plan-card-x", planCardTag("x"))
    }

    @Test
    fun theSelectedCardIsAnnouncedAsSelectedAndTheCarouselStartsOnIt() {
        show(selectedId = "b")
        rule.onNodeWithTag("setup-plan-card-b").assertIsSelected()
        rule.onNodeWithTag("setup-plan-card-a").assertIsNotSelected()
        val config = rule.onNodeWithTag("setup-plan-card-b").fetchSemanticsNode().config
        assertEquals("Programa elegido", config[SemanticsProperties.StateDescription])
        // Arranca en la elegida: «Elegido» es la acción de la tarjeta central.
        rule.onNodeWithText("Elegido").assertExists()
        rule.onNodeWithText("Blurb de Programa B.").assertExists()
    }

    @Test
    fun theCardIsAButtonDescribedWithItsData() {
        show()
        val config = rule.onNodeWithTag("setup-plan-card-a").fetchSemanticsNode().config
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        assertEquals("Ver detalles", config[SemanticsActions.OnClick].label)
        assertEquals(
            "Programa A. Jim Wendler. 4 días  ·  60 min  ·  Intermedio",
            config[SemanticsProperties.ContentDescription].joinToString(),
        )
    }

    @Test
    fun chooseReportsTheCentralCard() {
        show()
        rule.onNodeWithTag("setup-plan-choose-a").performScrollTo().performClick()
        assertEquals(listOf("a"), selects)
        assertTrue(opens.isEmpty())
    }

    @Test
    fun seeDetailsReportsTheCentralCard() {
        show()
        rule.onNodeWithTag("setup-plan-open-a").performScrollTo().performClick()
        assertEquals(listOf("a"), opens)
        assertTrue(selects.isEmpty())
    }

    @Test
    fun tappingTheCentralCardOpensIt() {
        show()
        rule.onNodeWithTag("setup-plan-card-a").performScrollTo().performClick()
        assertEquals(listOf("a"), opens)
    }

    @Test
    fun tappingANeighbourBringsItToTheCentreInsteadOfOpeningIt() {
        show()
        // Una vecina asoma a medias: se activa por su acción de accesibilidad, que no depende de dónde caiga el dedo.
        rule.onNodeWithTag("setup-plan-card-b").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue("una vecina no se abre", opens.isEmpty())
        // Ahora la central es B: su blurb y su acción de elegir.
        rule.onNodeWithText("Blurb de Programa B.").assertExists()
        rule.onNodeWithTag("setup-plan-choose-b").performScrollTo().performClick()
        assertEquals(listOf("b"), selects)
    }

    @Test
    fun aSingleCardIsShownWithoutAPager() {
        show(list = listOf(card("solo", "Único", 9)))
        rule.onNodeWithTag("setup-plan-card-solo").assertExists()
        rule.onNodeWithText("Blurb de Único.").assertExists()
        rule.onNodeWithTag("setup-plan-choose-solo").performScrollTo().performClick()
        rule.onNodeWithTag("setup-plan-open-solo").performScrollTo().performClick()
        assertEquals(listOf("solo"), selects)
        assertEquals(listOf("solo"), opens)
    }

    @Test
    fun anEmptyListPaintsNothing() {
        show(list = emptyList())
        rule.onNodeWithTag("setup-plan-card-a").assertDoesNotExist()
    }

    @Test
    fun theTextActionsAreTouchTargetsOfAtLeast48dp() {
        show()
        rule.onNodeWithTag("setup-plan-choose-a").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("setup-plan-open-a").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun theBadgeTravelsInTheAnnouncement() {
        show(list = listOf(card("a", "Programa A", 1, badge = "Recomendado"), card("b", "Programa B", 2)))
        val text = rule.onNodeWithTag("setup-plan-card-a").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(text.endsWith("Recomendado"))
    }
}
