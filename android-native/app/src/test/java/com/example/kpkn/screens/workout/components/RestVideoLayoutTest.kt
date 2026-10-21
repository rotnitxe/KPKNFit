package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.design.*
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class RestVideoLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val state = mutableStateOf<SetVideoAnalysisState?>(null)
    private val surface = mutableStateOf(0)
    private val rest = WorkoutRestModalState(exerciseName = "Sentadilla", kind = RestTimerKind.STANDARD, plannedSeconds = 120, activeSeconds = 120)
    private val exercise = Exercise("e", "Sentadilla")
    private val next = RestNextSetSummary(2, 4, loadKg = 95.0, repsText = "4–6", intensityText = "RIR 2")
    private val cases = listOf(
        SetVideoAnalysisState.Analyzing(42),
        SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.ALTA),
        SetVideoAnalysisState.Mismatch(8, 7, 0, 1, CountConfidence.ALTA),
        SetVideoAnalysisState.Mismatch(12, 8, 3, 2, CountConfidence.ALTA, loggedPartials = 2),
        SetVideoAnalysisState.Agrees(8),
        SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.MEDIA),
        SetVideoAnalysisState.Mismatch(8, 8, 1, 1, CountConfidence.MEDIA, loggedPartials = 1),
        SetVideoAnalysisState.Inconclusive, SetVideoAnalysisState.Failed("decode"),
        SetVideoAnalysisState.Unsupported("Este ejercicio todavía no admite análisis de técnica con esta variante"),
    )

    private fun check(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale), LocalWizardReducedMotion provides true, LocalRestClockFrozen provides 1.7f) {
                KPKNTheme {
                    Box(Modifier.size(360.dp, 880.dp)) {
                        key(surface.value) {
                            SurfaceFrame(surface.value) {
                                val slot: (@Composable () -> Unit)? = state.value?.let { result ->
                                    { WorkoutRestVideoLine(null, SetVideoAnalysisUi("v", result), null, {}, {}, {}) }
                                }
                                when (surface.value) {
                                    0 -> RestLiveCard(86, 120, androidx.compose.ui.graphics.Color.White, rest, null, null,
                                        CompletedSet("last", weight = 95.0, reps = 8), {}, {}, {}, null, {}, nextSet = next, clockActive = false, videoSlot = slot)
                                    1 -> RestFeedbackCard(86, 120, RestClockShape.TOWER, listOf(exercise),
                                        remember { PostExerciseFeedbackDraft.from(listOf(exercise), emptyMap()) }, FeedbackInsights.Empty,
                                        { null }, emptyList(), {}, {}, {}, clockActive = false, videoSlot = slot)
                                    else -> RestTimerOverlay(rest, 86, remember { HazeState() }, onDecrease = {}, onIncrease = {}, onSkip = {},
                                        postExerciseFeedbackContent = if (surface.value == 3) ({ Text("Feedback", Modifier.testTag("feedback_content")) }) else null,
                                        clockActive = false, videoSlot = slot)
                                }
                        }
                        }
                    }
                }
            }
        }
        for (kind in 0..3) {
            rule.runOnIdle { state.value = null; surface.value = kind }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("rest_surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val anchor = if (kind == 3) rule.onNodeWithTag("feedback_content", useUnmergedTree = true) else rule.onNodeWithText("1:26", useUnmergedTree = true)
            val anchorBounds = anchor.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            cases.forEach { result ->
                rule.runOnIdle { state.value = result }
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
                assertEquals(bounds, rule.onNodeWithTag("rest_surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
                assertEquals("El reloj o el feedback se mueve en $kind/$result", anchorBounds, anchor.fetchSemanticsNode().boundsInRoot)
                val line = try {
                    rule.onNodeWithTag(RestVideoLineTag, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
                } catch (failure: AssertionError) {
                    throw AssertionError("Fila no visible en superficie $kind al $fontScale: $result; marco $bounds; fila ${rule.onNodeWithTag(RestVideoLineTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot}", failure)
                }
                assertEquals(48f * 3f, line.boundsInRoot.height, 0.01f)
                val layouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithTag(RestVideoTextTag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse("Texto cortado en $kind/$result: ${layout.layoutInput.text}", layout.hasVisualOverflow)
                assertTrue(layout.lineCount <= 2)
                val links = layout.layoutInput.text.getLinkAnnotations(0, layout.layoutInput.text.length)
                assertEquals(workoutRestVideoLine(result).actions.size, links.size)
                assertTrue("Se dibuja toda la frase", layout.getLineEnd(layout.lineCount - 1) == layout.layoutInput.text.length)
            }
        }
    }

    @Test fun every_rest_surface_keeps_its_bounds_and_displays_complete_text_at_100_percent() = check(1f)
    @Test fun every_rest_surface_keeps_its_bounds_and_displays_complete_text_at_130_percent() = check(1.3f)

    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }

    /** El marco actual de la app: 24 dp de margen por lado, misma altura a ambas escalas. */
    @Composable private fun SurfaceFrame(kind: Int, content: @Composable () -> Unit) {
        if (kind < 2) Box(Modifier.width(312.dp)) {
            LiveSessionCardFrame { Box(Modifier.fillMaxSize().testTag("rest_surface")) { content() } }
        } else Box(Modifier.size(360.dp, 880.dp).testTag("rest_surface")) { content() }
    }
}
