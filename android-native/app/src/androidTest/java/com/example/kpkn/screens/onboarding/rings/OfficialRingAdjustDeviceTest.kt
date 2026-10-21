package com.example.kpkn.screens.onboarding.rings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.rings.EnergyColumnAdjust
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Contratos de ajuste oficial que requieren gestos reales en el ZFlip; sin datos ni guardados de producción. */
@RunWith(AndroidJUnit4::class)
class OfficialRingAdjustDeviceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dialPreviewsDuringDragAndCommitsOnlyOnRelease() {
        val commits = mutableListOf<Int>()
        var latest = 50
        rule.setContent {
            var value by remember { mutableIntStateOf(50) }
            var known by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    EnergyColumnAdjust(
                        channel = RingChannel.ENERGIA,
                        value = value,
                        before = 50,
                        valueKnown = known,
                        beforeKnown = false,
                        onValueChange = { value = it; known = true; latest = it },
                        onValueCommit = { commits += it },
                    )
                }
            }
        }
        rule.onNodeWithTag("ring-adjust-value", useUnmergedTree = true).assertTextEquals("—")
        rule.onNodeWithTag("ring-adjust-ring").performTouchInput {
            down(Offset(width * 0.92f, height * 0.5f))
            moveTo(Offset(width * 0.5f, height * 0.92f))
        }
        rule.runOnIdle { assertTrue("arrastrar no guarda", commits.isEmpty()) }
        rule.onNodeWithTag("ring-adjust-ring").performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf(latest), commits) }
    }

    @Test
    fun wizardResetRestoresUnknownChannelInsteadOfInventingPercentage() {
        var answers by mutableStateOf(SetupRingsAnswers())
        showWizard(
            answers = { answers },
            onChannel = { channel, value -> answers = answers.copy(manualRings = answers.manualRings + (channel to value)) },
            onResetChannel = { channel -> answers = answers.copy(manualRings = answers.manualRings - channel) },
        )
        val energy = rule.onNodeWithTag("rings-result-energia", useUnmergedTree = true)
        energy.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sin calibrar"))
        energy.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(73f) }
        rule.runOnIdle { assertEquals(73, answers.manualRings["energia"]) }
        rule.onAllNodesWithTag("ring-adjust-reset", useUnmergedTree = true).onFirst().performScrollTo().performClick()
        rule.runOnIdle { assertTrue(answers.manualRings.isEmpty()) }
        energy.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Sin calibrar"))
    }

    @Test
    fun wizardIndividualResetPreservesOtherMuscles() {
        var answers by mutableStateOf(SetupRingsAnswers(manualMusclesV2 = linkedMapOf("Pectorales" to 70, "Dorsales" to 35)))
        var globalResets = 0
        showWizard(
            answers = { answers },
            expanded = true,
            onResetMuscle = { pillar -> answers = answers.copy(manualMusclesV2 = answers.manualMusclesV2 - pillar) },
            onResetMuscles = { globalResets++ },
        )
        rule.onNodeWithTag("ring-adjust-muscle-reset-Pectorales", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(mapOf("Dorsales" to 35), answers.manualMusclesV2)
            assertEquals(0, globalResets)
        }
    }

    private fun showWizard(
        answers: () -> SetupRingsAnswers,
        expanded: Boolean = false,
        onChannel: (String, Int) -> Unit = { _, _ -> },
        onResetChannel: (String) -> Unit = {},
        onResetMuscle: (String) -> Unit = {},
        onResetMuscles: () -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        RingsResultBody(
                            coverage = RingsCoverage.NO_DATA,
                            muscleEstimates = emptyMap(),
                            answers = answers(),
                            error = null,
                            loading = false,
                            today = LocalDate.of(2026, 10, 16),
                            onCommitChannel = onChannel,
                            onCommitMuscle = { _, _ -> },
                            onResetMuscles = onResetMuscles,
                            onResetMuscle = onResetMuscle,
                            onResetChannel = onResetChannel,
                            onRetry = {},
                            initiallyExpanded = expanded,
                        )
                    }
                }
            }
        }
    }
}
