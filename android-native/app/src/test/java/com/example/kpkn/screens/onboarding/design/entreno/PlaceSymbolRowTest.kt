package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La fila «¿Dónde entrenas?» con Compose sobre Robolectric: tocar un símbolo avisa con el lugar correcto, el estado
 * (seleccionado o no) se anuncia a TalkBack, cada símbolo es una casilla con su marca de prueba y su objetivo
 * táctil mide al menos 48 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlaceSymbolRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val toggled = mutableListOf<TrainingPlace>()
    private var selected by mutableStateOf(emptySet<TrainingPlace>())

    private fun show(initial: Set<TrainingPlace> = emptySet(), flip: Boolean = true) {
        selected = initial
        rule.setContent {
            PlaceSymbolRow(
                selected = selected,
                onToggle = { place ->
                    toggled += place
                    if (flip) selected = if (place in selected) selected - place else selected + place
                },
            )
        }
    }

    @Test
    fun everyPlaceHasItsTestTag() {
        show()
        for (place in TrainingPlace.entries) {
            rule.onNodeWithTag("setup-place-${place.name}").assertExists()
        }
        assertEquals("setup-place-GYM", placeSymbolTag(TrainingPlace.GYM))
        assertEquals("setup-place-HOME", placeSymbolTag(TrainingPlace.HOME))
        assertEquals("setup-place-PUBLIC", placeSymbolTag(TrainingPlace.PUBLIC))
    }

    @Test
    fun tappingEachSymbolReportsThatPlace() {
        show(flip = false)
        for (place in TrainingPlace.entries) {
            rule.onNodeWithTag("setup-place-${place.name}").performClick()
        }
        assertEquals(TrainingPlace.entries.toList(), toggled)
    }

    @Test
    fun announcesTheLabelAndWhetherItIsSelected() {
        show(initial = setOf(TrainingPlace.HOME))
        rule.onNodeWithTag("setup-place-GYM").assertContentDescriptionEquals("Gimnasio, sin seleccionar")
        rule.onNodeWithTag("setup-place-HOME").assertContentDescriptionEquals("En casa, seleccionado")
        rule.onNodeWithTag("setup-place-PUBLIC").assertContentDescriptionEquals("En espacios públicos, sin seleccionar")
    }

    @Test
    fun theAnnouncementFollowsTheState() {
        show()
        rule.onNodeWithTag("setup-place-GYM").assertIsOff()
        rule.onNodeWithTag("setup-place-GYM").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-place-GYM").assertIsOn()
        rule.onNodeWithTag("setup-place-GYM").assertContentDescriptionEquals("Gimnasio, seleccionado")
        rule.onNodeWithTag("setup-place-GYM").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-place-GYM").assertIsOff()
        rule.onNodeWithTag("setup-place-GYM").assertContentDescriptionEquals("Gimnasio, sin seleccionar")
    }

    @Test
    fun oneTwoOrAllThreeCanBeSelected() {
        show()
        for (place in TrainingPlace.entries) {
            rule.onNodeWithTag("setup-place-${place.name}").performClick()
            rule.waitForIdle()
        }
        assertEquals(TrainingPlace.entries.toSet(), selected)
        for (place in TrainingPlace.entries) {
            rule.onNodeWithTag("setup-place-${place.name}").assertIsOn()
        }
    }

    @Test
    fun eachSymbolIsACheckbox() {
        show()
        for (place in TrainingPlace.entries) {
            val config = rule.onNodeWithTag("setup-place-${place.name}").fetchSemanticsNode().config
            assertEquals(Role.Checkbox, config[SemanticsProperties.Role])
        }
    }

    @Test
    fun theTouchTargetIsAtLeast48dp() {
        show()
        for (place in TrainingPlace.entries) {
            rule.onNodeWithTag("setup-place-${place.name}").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun withReducedMotionTheSemanticsAreTheSame() {
        selected = setOf(TrainingPlace.GYM)
        rule.setContent {
            PlaceSymbolRow(selected = selected, onToggle = { toggled += it }, reduced = true)
        }
        rule.onNodeWithTag("setup-place-GYM").assertContentDescriptionEquals("Gimnasio, seleccionado")
        rule.onNodeWithTag("setup-place-HOME").assertContentDescriptionEquals("En casa, sin seleccionar")
        rule.onNodeWithTag("setup-place-HOME").performClick()
        assertEquals(listOf(TrainingPlace.HOME), toggled)
    }

    @Test
    fun theLabelIsNotAnnouncedTwice() {
        show()
        // El nombre solo viaja en la descripción del nodo: el texto de la etiqueta no se suma al árbol fusionado.
        val config = rule.onNodeWithTag("setup-place-GYM").fetchSemanticsNode().config
        assertTrue(!config.contains(SemanticsProperties.Text))
    }
}
