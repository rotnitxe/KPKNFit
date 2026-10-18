package com.example.kpkn.render

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.screens.workout.design.WorkoutVideoRepsSuggestion
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Piezas reales de VT8b, a 360 dp y dos escalas; sin cámara ni ViewModel ficticio. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class WorkoutVideoRepsRenderSheetTest {
    @get:Rule val rule = createComposeRule()
    private var host: View? = null
    companion object {
        @JvmStatic @BeforeClass fun onlyWhenAsked() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }
    private val set = ExerciseSet(id = "s", targetReps = 8, weight = 95.0, targetRIR = 2,
        intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
    private val exercise = Exercise(id = "e", name = "Sentadilla trasera con barra", sets = listOf(set))
    private val rest = WorkoutRestModalState(exerciseId = "e", exerciseName = exercise.name,
        kind = RestTimerKind.STANDARD, plannedSeconds = 120, activeSeconds = 120)
    private val mismatch = SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.ALTA)

    private fun render(name: String, fontScale: Float, height: Dp = 800.dp, content: @Composable () -> Unit) {
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalRestClockFrozen provides 1.7f) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(height).background(WizardColors.background).testTag("vt8b-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("vt8b-render").fetchSemanticsNode().boundsInRoot
        rule.runOnUiThread { SymbolRenderSheet.savePng(host!!, bounds, name) }
    }
    @Composable private fun Suggestion(state: SetVideoAnalysisState = mismatch) {
        WorkoutVideoRepsSuggestion(SetVideoAnalysisUi("video", state), {}, {}, {}, {})
    }
    @Composable private fun States() {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                SetVideoAnalysisState.Analyzing(42),
                SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA, 95),
                SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.MEDIA),
                mismatch,
                SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.MEDIA),
                SetVideoAnalysisState.Suggest(0, 0, 1, CountConfidence.ALTA),
                SetVideoAnalysisState.Agrees(8),
                SetVideoAnalysisState.Inconclusive,
                SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA),
                SetVideoAnalysisState.Unsupported("Esta serie se registra por tiempo"),
            ).forEach { Suggestion(it) }
        }
    }
    @Composable private fun Card() {
        Box(Modifier.padding(16.dp)) {
            SetInputCardV2(exercise = exercise, setIndex = 0, currentSet = set, ghostSet = null,
                onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                recordActionHolder = remember { RecordActionHolder() },
                initialDraft = WorkoutSetDraft(weightText = "95", valueText = "8", intensityText = "2"),
                onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                effortFireSecondsOverride = 1.7f,
                videoAnalysis = SetVideoAnalysisUi("video", SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA, 95)),
            )
        }
    }
    @Composable private fun Expanded(feedback: Boolean = false) {
        RestTimerOverlay(state = rest, remainingSeconds = 86, hazeState = remember { HazeState() },
            lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 8),
            onDecrease = {}, onIncrease = {}, onSkip = {}, clockActive = false,
            videoSlot = { Suggestion() },
            postExerciseFeedbackContent = if (feedback) ({ Text("¿Cómo te salió?", style = KpknType.title, color = WizardColors.text) }) else null,
        )
    }
    @Composable private fun Compact() {
        Box(Modifier.padding(16.dp).height(520.dp)) {
            RestLiveCard(remainingSeconds = 86, totalSeconds = 120, sessionAccentColor = WizardColors.text,
                restState = rest, pendingRestSuggestion = null, lastSetOutcome = null,
                lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 8),
                onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = null, onExpand = {},
                clockActive = false, videoSlot = { Suggestion() })
        }
    }
    @Composable private fun CompactFeedback() {
        val draft = remember { PostExerciseFeedbackDraft.from(listOf(exercise), emptyMap()) }
        Box(Modifier.padding(16.dp).height(560.dp)) {
            RestFeedbackCard(remainingSeconds = 86, totalSeconds = 120, shape = RestClockShape.TOWER,
                feedbackExercises = listOf(exercise), draft = draft, insights = FeedbackInsights.Empty,
                explainDiscomfort = { null }, linked = emptyList(), onSave = {}, onSkipRest = {}, onExpand = {},
                clockActive = false, videoSlot = { Suggestion() })
        }
    }
    @Test fun states100() = render("video_states_100", 1f, 1300.dp) { States() }
    @Test fun states130() = render("video_states_130", 1.3f, 1500.dp) { States() }
    @Test fun card100() = render("video_card_100", 1f) { Card() }
    @Test fun card130() = render("video_card_130", 1.3f) { Card() }
    @Test fun rest100() = render("video_rest_100", 1f) { Expanded() }
    @Test fun rest130() = render("video_rest_130", 1.3f) { Expanded() }
    @Test fun restFeedback100() = render("video_rest_feedback_100", 1f) { Expanded(true) }
    @Test fun restFeedback130() = render("video_rest_feedback_130", 1.3f) { Expanded(true) }
    @Test fun compact100() = render("video_rest_compact_100", 1f) { Compact() }
    @Test fun compact130() = render("video_rest_compact_130", 1.3f) { Compact() }
    @Test fun compactFeedback100() = render("video_feedback_compact_100", 1f) { CompactFeedback() }
    @Test fun compactFeedback130() = render("video_feedback_compact_130", 1.3f) { CompactFeedback() }
}
