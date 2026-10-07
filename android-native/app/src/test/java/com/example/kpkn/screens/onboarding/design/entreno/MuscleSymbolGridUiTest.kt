package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.MuscleSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La cuadrícula de músculos con Compose sobre Robolectric: marcas de prueba, semántica de casilla, el tope («no» sin
 * llamar a `onToggle`) y la etiqueta «Sugerido» que se va al tocar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class MuscleSymbolGridUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val toggles = mutableListOf<MuscleSymbol>()
    private var selected by mutableStateOf(setOf<MuscleSymbol>())

    private val five = setOf(
        MuscleSymbol.CHEST, MuscleSymbol.BACK, MuscleSymbol.SHOULDERS, MuscleSymbol.BICEPS, MuscleSymbol.TRICEPS,
    )

    private fun show(initial: Set<MuscleSymbol> = emptySet(), suggested: Set<MuscleSymbol> = emptySet(), max: Int = 5) {
        selected = initial
        rule.setContent {
            MuscleSymbolGrid(
                selected = selected,
                suggested = suggested,
                maxSelected = max,
                onToggle = { m ->
                    toggles += m
                    selected = if (m in selected) selected - m else selected + m
                },
            )
        }
    }

    private fun cell(m: MuscleSymbol) = rule.onNodeWithTag("setup-muscle-${m.name}")

    @Test
    fun everyMuscleHasATagAndIsAnnouncedAsACheckbox() {
        show()
        for (m in MuscleSymbol.entries) {
            val config = cell(m).fetchSemanticsNode().config
            assertEquals(m.name, Role.Checkbox, config[SemanticsProperties.Role])
        }
    }

    @Test
    fun theNameOfEachMuscleIsRead() {
        show()
        for (m in MuscleSymbol.entries) {
            rule.onAllNodesWithText(m.label).assertCountEquals(1)
        }
    }

    @Test
    fun withRoomATapChoosesAndAnotherTapRemoves() {
        show()
        cell(MuscleSymbol.CHEST).performClick()
        rule.waitForIdle()
        assertEquals(listOf(MuscleSymbol.CHEST), toggles)
        assertEquals(setOf(MuscleSymbol.CHEST), selected)
        cell(MuscleSymbol.CHEST).performClick()
        rule.waitForIdle()
        assertEquals(listOf(MuscleSymbol.CHEST, MuscleSymbol.CHEST), toggles)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun withTheCapReachedANewMuscleIsRejectedWithoutCallingOnToggle() {
        show(five)
        cell(MuscleSymbol.ABS).performClick()
        rule.waitForIdle()
        assertTrue("no debe avisar: $toggles", toggles.isEmpty())
        assertEquals(five, selected)
    }

    @Test
    fun withTheCapReachedAChosenMuscleCanStillBeRemoved() {
        show(five)
        cell(MuscleSymbol.BACK).performClick()
        rule.waitForIdle()
        assertEquals(listOf(MuscleSymbol.BACK), toggles)
        assertEquals(five - MuscleSymbol.BACK, selected)
        // Con sitio otra vez, ahora sí entra uno nuevo.
        cell(MuscleSymbol.ABS).performClick()
        rule.waitForIdle()
        assertEquals(listOf(MuscleSymbol.BACK, MuscleSymbol.ABS), toggles)
    }

    @Test
    fun theMuscleThatCannotBeChosenAnyMoreSaysSo() {
        show(five)
        val abs = cell(MuscleSymbol.ABS).fetchSemanticsNode().config
        assertEquals("Máximo de músculos alcanzado", abs[SemanticsProperties.StateDescription])
        val chest = cell(MuscleSymbol.CHEST).fetchSemanticsNode().config
        assertFalse("los elegidos no llevan el aviso", chest.contains(SemanticsProperties.StateDescription))
    }

    @Test
    fun aCapOfOneAllowsASingleMuscle() {
        show(max = 1)
        cell(MuscleSymbol.QUADS).performClick()
        rule.waitForIdle()
        cell(MuscleSymbol.CALVES).performClick()
        rule.waitForIdle()
        assertEquals(listOf(MuscleSymbol.QUADS), toggles)
    }

    @Test
    fun suggestedIsLabelledUntilThePersonTouchesThatMuscle() {
        val suggested = setOf(MuscleSymbol.QUADS, MuscleSymbol.GLUTES)
        show(initial = suggested, suggested = suggested)
        rule.onAllNodesWithText("Sugerido").assertCountEquals(2)
        cell(MuscleSymbol.QUADS).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Sugerido").assertCountEquals(1)
        cell(MuscleSymbol.GLUTES).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Sugerido").assertCountEquals(0)
    }

    @Test
    fun withoutSuggestionsThereIsNoLabel() {
        show()
        rule.onAllNodesWithText("Sugerido").assertCountEquals(0)
    }

    @Test
    fun everyCellIsAtLeast48dpInBothDirections() {
        show()
        for (m in MuscleSymbol.entries) {
            cell(m).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
}
