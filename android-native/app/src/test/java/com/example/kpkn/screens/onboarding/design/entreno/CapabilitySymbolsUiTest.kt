package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las capacidades con Compose sobre Robolectric: tocar el símbolo avanza de nivel con vuelta al primero, tocar un
 * segmento lo fija y lo que se anuncia (nombre del ejercicio y nivel, segmento elegido).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class CapabilitySymbolsUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val levels = mutableStateMapOf<CapabilitySkill, CapabilityLevel>()
    private val changes = mutableListOf<Pair<CapabilitySkill, CapabilityLevel>>()

    private fun show(
        skills: List<CapabilitySkill> = CapabilitySkill.entries.toList(),
        initial: Map<CapabilitySkill, CapabilityLevel> = emptyMap(),
    ) {
        levels.clear()
        levels.putAll(initial)
        rule.setContent {
            CapabilitySymbols(
                skills = skills,
                levels = levels.toMap(),
                onLevel = { skill, level ->
                    changes += skill to level
                    levels[skill] = level
                },
            )
        }
    }

    private fun symbol(s: CapabilitySkill) = rule.onNodeWithTag("setup-capability-${s.name}")

    private fun segment(s: CapabilitySkill, l: CapabilityLevel) = rule.onNodeWithTag("setup-capability-${s.name}-${l.name}")

    @Test
    fun everyExerciseHasItsSymbolAndThreeSegments() {
        show()
        for (s in CapabilitySkill.entries) {
            symbol(s).assertExists()
            for (l in CapabilityLevel.entries) segment(s, l).assertExists()
        }
    }

    @Test
    fun anOddNumberOfExercisesStillShowsAllOfThem() {
        show(skills = listOf(CapabilitySkill.PULL_UP, CapabilitySkill.PUSH_UP, CapabilitySkill.DIP))
        symbol(CapabilitySkill.DIP).assertExists()
        symbol(CapabilitySkill.PISTOL_SQUAT).assertDoesNotExist()
    }

    @Test
    fun tappingTheSymbolWithoutAnAnswerChoosesSome() {
        show()
        symbol(CapabilitySkill.PULL_UP).performClick()
        rule.waitForIdle()
        assertEquals(listOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME), changes)
    }

    @Test
    fun tappingTheSymbolAdvancesAndComesBackToTheFirst() {
        show(initial = mapOf(CapabilitySkill.DIP to CapabilityLevel.NONE))
        repeat(3) {
            symbol(CapabilitySkill.DIP).performClick()
            rule.waitForIdle()
        }
        assertEquals(
            listOf(CapabilityLevel.SOME, CapabilityLevel.MANY, CapabilityLevel.NONE),
            changes.map { it.second },
        )
        assertTrue(changes.all { it.first == CapabilitySkill.DIP })
    }

    @Test
    fun tappingASegmentSetsThatLevel() {
        show()
        segment(CapabilitySkill.PUSH_UP, CapabilityLevel.MANY).performClick()
        rule.waitForIdle()
        segment(CapabilitySkill.PUSH_UP, CapabilityLevel.NONE).performClick()
        rule.waitForIdle()
        assertEquals(
            listOf(
                CapabilitySkill.PUSH_UP to CapabilityLevel.MANY,
                CapabilitySkill.PUSH_UP to CapabilityLevel.NONE,
            ),
            changes,
        )
    }

    @Test
    fun theSymbolAnnouncesTheExerciseAndItsLevel() {
        show(initial = mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME))
        val config = symbol(CapabilitySkill.PULL_UP).fetchSemanticsNode().config
        assertTrue(config[SemanticsProperties.ContentDescription].contains("Dominadas, Algunas"))
        assertEquals("Algunas", config[SemanticsProperties.StateDescription])
        val unanswered = symbol(CapabilitySkill.DIP).fetchSemanticsNode().config
        assertTrue(unanswered[SemanticsProperties.ContentDescription].contains("Fondos, sin responder"))
    }

    @Test
    fun theChosenSegmentIsSelectedAndTheOthersAreNot() {
        show(initial = mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME))
        for (l in CapabilityLevel.entries) {
            val config = segment(CapabilitySkill.PULL_UP, l).fetchSemanticsNode().config
            assertEquals(Role.RadioButton, config[SemanticsProperties.Role])
            assertEquals(l.name, l == CapabilityLevel.SOME, config[SemanticsProperties.Selected])
        }
    }

    @Test
    fun withoutAnAnswerNoSegmentIsSelected() {
        show()
        for (l in CapabilityLevel.entries) {
            val config = segment(CapabilitySkill.PISTOL_SQUAT, l).fetchSemanticsNode().config
            assertFalse(l.name, config[SemanticsProperties.Selected])
        }
    }

    @Test
    fun everyTouchTargetIsAtLeast48dp() {
        show()
        for (s in CapabilitySkill.entries) {
            symbol(s).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            for (l in CapabilityLevel.entries) segment(s, l).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
}
