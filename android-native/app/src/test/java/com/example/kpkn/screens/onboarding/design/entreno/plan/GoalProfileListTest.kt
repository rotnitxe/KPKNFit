package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La lista «¿Cuál es tu objetivo?» con Compose sobre Robolectric: una fila (botón de opción con su marca) por perfil,
 * elegir llama a `onSelect`, un perfil bloqueado llama a `onBlockedTap` y NUNCA a `onSelect`, y el estado (elegido,
 * no disponible y su razón) llega a TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class GoalProfileListTest {

    @get:Rule
    val rule = createComposeRule()

    private val selects = mutableListOf<TrainingGoalProfile>()
    private val blockedTaps = mutableListOf<TrainingGoalProfile>()
    private var selected by mutableStateOf<TrainingGoalProfile?>(null)
    private var blocked by mutableStateOf<Map<TrainingGoalProfile, String>>(emptyMap())

    private fun show(initial: TrainingGoalProfile? = null, reasons: Map<TrainingGoalProfile, String> = emptyMap(), flip: Boolean = true) {
        selected = initial
        blocked = reasons
        // Como en el wizard, la lista vive dentro de una página que se desplaza: diez filas no caben en la ventana.
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GoalProfileList(
                    selected = selected,
                    blockedReasons = blocked,
                    onSelect = {
                        selects += it
                        if (flip) selected = it
                    },
                    onBlockedTap = { blockedTaps += it },
                    reduced = true,
                )
            }
        }
    }

    private fun tag(profile: TrainingGoalProfile) = "setup-goal-${profile.name}"

    @Test
    fun everyProfileHasItsRowWithItsTag() {
        show()
        for (profile in TrainingGoalProfile.entries) rule.onNodeWithTag(tag(profile)).assertExists()
        assertEquals("setup-goal-POWERLIFTING", goalProfileTag(TrainingGoalProfile.POWERLIFTING))
        assertEquals(10, TrainingGoalProfile.entries.size)
    }

    @Test
    fun theTwoSectionsAreNamedAndTheLegendSaysItDependsOnTheMaterial() {
        show()
        rule.onNodeWithText("Generales").assertExists()
        rule.onNodeWithText("Disciplinas").assertExists()
        rule.onNodeWithText("Dependen de tu material.").assertExists()
    }

    @Test
    fun tappingAProfileSelectsItAndOnlyIt() {
        show()
        rule.onNodeWithTag(tag(TrainingGoalProfile.CALISTHENICS)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf(TrainingGoalProfile.CALISTHENICS), selects)
        assertTrue(blockedTaps.isEmpty())
        rule.onNodeWithTag(tag(TrainingGoalProfile.CALISTHENICS)).assertIsSelected()
        rule.onNodeWithTag(tag(TrainingGoalProfile.POWERLIFTING)).assertIsNotSelected()
    }

    @Test
    fun aBlockedProfileOnlyReportsTheTap() {
        show(reasons = mapOf(TrainingGoalProfile.POWERLIFTING to "Necesita barra, rack y banco."))
        rule.onNodeWithTag(tag(TrainingGoalProfile.POWERLIFTING)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf(TrainingGoalProfile.POWERLIFTING), blockedTaps)
        assertTrue("un bloqueado no se elige", selects.isEmpty())
        rule.onNodeWithTag(tag(TrainingGoalProfile.POWERLIFTING)).assertIsNotSelected()
    }

    @Test
    fun theReasonAndTheActionReachTalkBack() {
        show(reasons = mapOf(TrainingGoalProfile.WEIGHTLIFTING to "Necesita barra y rack."))
        val config = rule.onNodeWithTag(tag(TrainingGoalProfile.WEIGHTLIFTING)).fetchSemanticsNode().config
        assertEquals("No disponible. Necesita barra y rack.", config[SemanticsProperties.StateDescription])
        assertEquals("Cambiar mi material", config[SemanticsActions.OnClick].label)
        assertEquals(Role.RadioButton, config[SemanticsProperties.Role])
    }

    @Test
    fun theReasonIsPaintedInsteadOfTheTagline() {
        show(reasons = mapOf(TrainingGoalProfile.STRONGMAN to "Necesita barra y mancuernas o kettlebell."))
        rule.onNodeWithText("Necesita barra y mancuernas o kettlebell.", useUnmergedTree = true).assertExists()
        // Disponible: se ve su frase, no una razón.
        rule.onNodeWithText(TrainingGoalProfile.CALISTHENICS.tagline, useUnmergedTree = true).assertExists()
        rule.onNodeWithText(TrainingGoalProfile.STRONGMAN.tagline, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theSelectedProfileIsAnnouncedAsSelected() {
        show(initial = TrainingGoalProfile.POWERBUILDING)
        val node = rule.onNodeWithTag(tag(TrainingGoalProfile.POWERBUILDING))
        node.assertIsSelected()
        node.assertContentDescriptionEquals("Powerbuilding. Fuerza máxima y estética a la vez.")
        assertEquals("Seleccionado", node.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        // Los demás no llevan estado propio salvo ser elegibles.
        assertNull(rule.onNodeWithTag(tag(TrainingGoalProfile.CALISTHENICS)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun aBlockedProfileCannotLookSelected() {
        // Si el borrador conserva un perfil que el material ya no permite, la lista no lo enseña como elegido.
        show(initial = TrainingGoalProfile.POWERLIFTING, reasons = mapOf(TrainingGoalProfile.POWERLIFTING to "Necesita barra, rack y banco."))
        rule.onNodeWithTag(tag(TrainingGoalProfile.POWERLIFTING)).assertIsNotSelected()
    }

    @Test
    fun theAnnouncementFollowsTheSelection() {
        show(initial = TrainingGoalProfile.BODYBUILDING)
        rule.onNodeWithTag(tag(TrainingGoalProfile.BODYBUILDING)).assertIsSelected()
        rule.onNodeWithTag(tag(TrainingGoalProfile.ARMWRESTLING)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(tag(TrainingGoalProfile.ARMWRESTLING)).assertIsSelected()
        rule.onNodeWithTag(tag(TrainingGoalProfile.BODYBUILDING)).assertIsNotSelected()
    }

    @Test
    fun everyRowIsATouchTargetOfAtLeast48dp() {
        show()
        for (profile in TrainingGoalProfile.entries) {
            rule.onNodeWithTag(tag(profile)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun theRowIsARadioButton() {
        show()
        for (profile in TrainingGoalProfile.entries) {
            val config = rule.onNodeWithTag(tag(profile)).fetchSemanticsNode().config
            assertEquals(Role.RadioButton, config[SemanticsProperties.Role])
        }
    }

    @Test
    fun theLabelIsNotAnnouncedTwice() {
        show()
        // El nombre viaja solo en la descripción del nodo: los textos de la fila no se suman al árbol fusionado.
        val config = rule.onNodeWithTag(tag(TrainingGoalProfile.POWERLIFTING)).fetchSemanticsNode().config
        assertTrue(!config.contains(SemanticsProperties.Text))
    }

    @Test
    fun withTheSystemMotionSettingNothingChangesInTheSemantics() {
        selected = TrainingGoalProfile.STRENGTH_MUSCLE
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GoalProfileList(selected, emptyMap(), onSelect = { selects += it }, onBlockedTap = { blockedTaps += it })
            }
        }
        rule.onNodeWithTag(tag(TrainingGoalProfile.STRENGTH_MUSCLE)).assertIsSelected()
        rule.onNodeWithTag(tag(TrainingGoalProfile.FUNCTIONAL_HEALTH)).performScrollTo().performClick()
        assertEquals(listOf(TrainingGoalProfile.FUNCTIONAL_HEALTH), selects)
    }
}
