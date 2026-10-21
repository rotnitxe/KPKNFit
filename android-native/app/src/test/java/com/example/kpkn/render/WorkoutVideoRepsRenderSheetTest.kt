package com.example.kpkn.render

import android.view.View
import android.view.inspector.WindowInspector
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.screens.workout.design.WorkoutVideoRepsSuggestion
import com.example.kpkn.screens.workout.design.WorkoutVideoChipTag
import com.example.kpkn.screens.workout.design.WorkoutVideoMenuTag
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

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

    private fun render(name: String, fontScale: Float, height: Dp = 800.dp, saveImmediately: Boolean = true, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        rule.setContent {
            host = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalRestClockFrozen provides 1.7f,
                LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(height).background(WizardColors.background).testTag("vt8b-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        if (saveImmediately) save(name)
    }

    private fun capture(includePopup: Boolean = false): Bitmap {
        rule.runOnUiThread { host!!.invalidate() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        lateinit var bitmap: Bitmap
        rule.runOnUiThread {
            val view = host!!
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
            if (includePopup) {
                // Popup vive en otra ventana; dibujarlo también preserva su posición real sobre la tarjeta.
                val origin = IntArray(2).also(view::getLocationOnScreen)
                val popups = WindowInspector.getGlobalWindowViews().filter { it.javaClass.simpleName == "PopupLayout" }
                assertTrue("El menú tiene su propia ventana", popups.isNotEmpty())
                popups.forEach { popup ->
                    val location = IntArray(2).also(popup::getLocationOnScreen)
                    canvas.save()
                    canvas.translate((location[0] - origin[0]).toFloat(), (location[1] - origin[1]).toFloat())
                    popup.draw(canvas)
                    canvas.restore()
                }
            }
        }
        return bitmap
    }

    private fun save(name: String, includePopup: Boolean = false): Bitmap {
        val bitmap = capture(includePopup)
        val bounds = rule.onNodeWithTag("vt8b-render").fetchSemanticsNode().boundsInRoot
        val cropped = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        FileOutputStream(File(SymbolRenderSheet.dir()!!, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (cropped !== bitmap) cropped.recycle()
        return bitmap
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
    @Composable private fun Card(state: SetVideoAnalysisState? = SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA, 95)) {
        Box(Modifier.padding(16.dp)) {
            SetInputCardV2(exercise = exercise, setIndex = 0, currentSet = set, ghostSet = null,
                onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                recordActionHolder = remember { RecordActionHolder() },
                sessionAccentColor = SymbolPalette.musculo,
                weightSuggestion = WeightSuggestion(suggestedWeight = 95.0, reason = "Objetivo de la serie"),
                previousSessionFirstSetWeight = 92.5,
                initialDraft = WorkoutSetDraft(weightText = "95", valueText = "8", intensityText = "2"),
                onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                effortFireSecondsOverride = 1.7f,
                videoAnalysis = state?.let { SetVideoAnalysisUi("video", it) },
                onRetryAnalysis = {},
            )
        }
    }

    private fun cardStates(fontScale: Float, scale: String) {
        val state = mutableStateOf<SetVideoAnalysisState?>(null)
        // Cada escena parte de una tarjeta nueva con los mismos datos de la serie.
        render("video_card", fontScale, saveImmediately = false) { key(state.value) { Card(state.value) } }
        val bounds = rule.onNodeWithTag("set_execution_card").fetchSemanticsNode().boundsInRoot
        val baseline = save("video_card_none_$scale")
        val cases = listOf(
            "analyzing" to SetVideoAnalysisState.Analyzing(42),
            "suggest_high" to SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.ALTA),
            "partials" to SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA, 95),
            "suggest_medium" to SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.MEDIA),
            "mismatch_high" to mismatch,
            "mismatch_medium" to SetVideoAnalysisState.Mismatch(8, 7, 1, 0, CountConfidence.MEDIA),
            "agrees" to SetVideoAnalysisState.Agrees(8),
            "inconclusive" to SetVideoAnalysisState.Inconclusive,
            "low" to SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA),
            "failed" to SetVideoAnalysisState.Failed("decode"),
            "unsupported" to SetVideoAnalysisState.Unsupported("Esta serie se registra por tiempo"),
        )
        val radius = (28f * host!!.resources.displayMetrics.density).toInt()
        cases.forEach { (name, next) ->
            rule.runOnIdle { state.value = next }
            assertEquals(bounds, rule.onNodeWithTag("set_execution_card").fetchSemanticsNode().boundsInRoot)
            val rendered = save("video_card_${name}_$scale")
            val load = rule.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot
            var inkPixels = 0
            for (y in load.top.toInt() until load.bottom.toInt()) {
                for (x in load.left.toInt() until load.right.toInt()) {
                    if (android.graphics.Color.red(rendered.getPixel(x, y)) > 180) inkPixels++
                }
            }
            assertTrue("La captura incluye la cifra de carga en $name", inkPixels > 100)
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    val edgeX = minOf(x - bounds.left.toInt(), bounds.right.toInt() - 1 - x)
                    val edgeY = minOf(y - bounds.top.toInt(), bounds.bottom.toInt() - 1 - y)
                    if (edgeX < 3 || edgeY < 3 || (edgeX < radius && edgeY < radius)) {
                        assertEquals("Borde idéntico en $name al $scale % ($x, $y)", baseline.getPixel(x, y), rendered.getPixel(x, y))
                    }
                }
            }
            rendered.recycle()
        }
        baseline.recycle()
        listOf(
            "use_high" to SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA, 95),
            "correct_high" to mismatch,
            "use_medium" to SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.MEDIA),
            "correct_medium" to SetVideoAnalysisState.Mismatch(8, 7, 1, 0, CountConfidence.MEDIA),
            "retry" to SetVideoAnalysisState.Failed("decode"),
        ).forEach { (name, next) ->
            rule.runOnIdle { state.value = next }
            rule.onNodeWithTag(WorkoutVideoChipTag).performClick()
            rule.onNodeWithTag(WorkoutVideoMenuTag).assertExists()
            save("video_card_menu_${name}_$scale", includePopup = true).recycle()
            rule.onNodeWithText("Ver análisis").performClick()
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
    @Test fun card100() = cardStates(1f, "100")
    @Test fun card130() = cardStates(1.3f, "130")
    @Test fun rest100() = render("video_rest_100", 1f) { Expanded() }
    @Test fun rest130() = render("video_rest_130", 1.3f) { Expanded() }
    @Test fun restFeedback100() = render("video_rest_feedback_100", 1f) { Expanded(true) }
    @Test fun restFeedback130() = render("video_rest_feedback_130", 1.3f) { Expanded(true) }
    @Test fun compact100() = render("video_rest_compact_100", 1f) { Compact() }
    @Test fun compact130() = render("video_rest_compact_130", 1.3f) { Compact() }
    @Test fun compactFeedback100() = render("video_feedback_compact_100", 1f) { CompactFeedback() }
    @Test fun compactFeedback130() = render("video_feedback_compact_130", 1.3f) { CompactFeedback() }
}
