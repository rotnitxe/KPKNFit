package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.CompletedSetPatch
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class WorkoutRestVideoInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val state = mutableStateOf<SetVideoAnalysisState>(SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.ALTA))
    private val patches = mutableListOf<CompletedSetPatch>()
    private val opened = mutableListOf<String>()
    private val retries = mutableListOf<String>()

    private fun show() = rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(3f, 1.3f)) {
            KPKNTheme {
                Box(Modifier.width(244.dp)) {
                    WorkoutRestVideoLine(null, SetVideoAnalysisUi("right-video", state.value), null,
                        onPatch = { patches += it }, onOpenAnalysis = { opened += it }, onRetryAnalysis = { retries += it })
                }
            }
        }
    }

    private fun action(action: RestVideoAction) {
        val node = rule.onNodeWithTag(RestVideoTextTag).fetchSemanticsNode()
        rule.runOnIdle { assertTrue(node.config[SemanticsActions.CustomActions].single { it.label == action.label }.action()) }
    }

    @Test fun high_and_medium_correction_send_the_original_patch_and_never_apply_automatically() {
        show()
        val expected = CompletedSetPatch(VoiceSetEditPatch(metricValue = 7), partialReps = 1, reachedFailure = true)
        rule.runOnIdle { assertTrue(patches.isEmpty()) }
        action(RestVideoAction.CORRECT)
        rule.runOnIdle {
            assertEquals(expected, patches.single())
            state.value = (state.value as SetVideoAnalysisState.Mismatch).copy(confidence = CountConfidence.MEDIA)
        }
        action(RestVideoAction.CORRECT)
        rule.runOnIdle { assertEquals(listOf(expected, expected), patches) }
        val labels = rule.onNodeWithTag(RestVideoTextTag).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertFalse(RestVideoAction.MARK_FAILURE.label in labels)
        action(RestVideoAction.OPEN)
        rule.runOnIdle { assertEquals(listOf("right-video"), opened) }
    }

    @Test fun marking_failure_preserves_counts_and_retry_uses_the_exact_media_id() {
        show()
        action(RestVideoAction.MARK_FAILURE)
        rule.runOnIdle {
            assertEquals(CompletedSetPatch(reachedFailure = true), patches.single())
            state.value = SetVideoAnalysisState.Failed("decode")
        }
        action(RestVideoAction.RETRY)
        rule.onNodeWithTag(RestVideoThumbTag).performClick()
        rule.runOnIdle {
            assertEquals(listOf("right-video"), retries)
            assertEquals(listOf("right-video"), opened)
            assertEquals(1, patches.size)
        }
    }

    @Test fun links_are_semibold_at_caption_size_and_the_whole_48dp_band_is_touchable() {
        show()
        val layouts = mutableListOf<TextLayoutResult>()
        val text = rule.onNodeWithTag(RestVideoTextTag)
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow)
        assertTrue(layout.lineCount <= 2)
        val links = layout.layoutInput.text.getLinkAnnotations(0, layout.layoutInput.text.length)
        assertEquals(2, links.size)
        links.forEach { range ->
            val style = (range.item as LinkAnnotation.Clickable).styles!!.style!!
            assertEquals(FontWeight.SemiBold, style.fontWeight)
            assertEquals(androidx.compose.ui.unit.TextUnit.Unspecified, style.fontSize)
            val bounds = layout.getPathForRange(range.start, range.end).getBounds()
            val node = text.fetchSemanticsNode()
            assertEquals(48f * 3f, node.boundsInRoot.height, 0.01f)
            text.performTouchInput { click(Offset(bounds.center.x, node.boundsInRoot.height - 1f)) }
        }
        rule.runOnIdle { assertEquals(listOf(completedVideoCorrectionPatch(state.value as SetVideoAnalysisState.Mismatch), CompletedSetPatch(reachedFailure = true)), patches) }
        assertEquals(listOf(workoutRestVideoLine(state.value).text), text.fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
    }
}
