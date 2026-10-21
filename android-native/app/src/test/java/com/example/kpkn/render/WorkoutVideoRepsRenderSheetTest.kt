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
import com.example.kpkn.screens.workout.design.WorkoutRestVideoLine
import com.example.kpkn.screens.workout.design.VideoThumbBadge
import com.example.kpkn.screens.workout.design.RestVideoTextTag
import com.example.kpkn.screens.workout.design.RestVideoThumbTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.rules.TemporaryFolder
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
    @get:Rule val folder = TemporaryFolder()
    private var host: View? = null
    @After fun restoreFontScale() { RuntimeEnvironment.setFontScale(1f) }
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
        awaitRestPreviews()
        val bitmap = capture(includePopup)
        val bounds = rule.onNodeWithTag("vt8b-render").fetchSemanticsNode().boundsInRoot
        val cropped = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        FileOutputStream(File(SymbolRenderSheet.dir()!!, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (cropped !== bitmap) cropped.recycle()
        return bitmap
    }

    /** Espera el PNG local de la miniatura; una captura no debe congelar el marcador de carga de Coil. */
    private fun awaitRestPreviews() {
        val thumbs = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(RestVideoThumbTag), useUnmergedTree = true).fetchSemanticsNodes()
        if (thumbs.isEmpty()) return
        val density = host!!.resources.displayMetrics.density
        rule.waitUntil(10_000L) {
            val bitmap = capture()
            try {
                thumbs.all { thumb ->
                    val color = bitmap.getPixel((thumb.boundsInRoot.left + 8f * density).toInt(), (thumb.boundsInRoot.top + 22f * density).toInt())
                    kotlin.math.abs(android.graphics.Color.red(color) - 42) <= 2 &&
                        kotlin.math.abs(android.graphics.Color.green(color) - 38) <= 2 && kotlin.math.abs(android.graphics.Color.blue(color) - 34) <= 2
                }
            } finally {
                bitmap.recycle()
            }
        }
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
    private val restStates = listOf(
        "analyzing" to SetVideoAnalysisState.Analyzing(42),
        "mismatch_high" to SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.ALTA),
        "failure" to SetVideoAnalysisState.Mismatch(8, 7, 0, 1, CountConfidence.ALTA),
        "agrees" to SetVideoAnalysisState.Agrees(8),
        "mismatch_medium" to SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.MEDIA),
        "equal_medium" to SetVideoAnalysisState.Mismatch(8, 8, 0, 0, CountConfidence.MEDIA),
        "inconclusive" to SetVideoAnalysisState.Inconclusive,
        "failed" to SetVideoAnalysisState.Failed("decode"),
        "partials" to SetVideoAnalysisState.Mismatch(12, 8, 3, 2, CountConfidence.ALTA, loggedPartials = 2),
        "unsupported" to SetVideoAnalysisState.Unsupported("Este ejercicio todavía no admite análisis de técnica con esta variante"),
    )
    private fun badge(state: SetVideoAnalysisState): VideoThumbBadge? = when (state) {
        is SetVideoAnalysisState.Analyzing -> VideoThumbBadge.Analyzing(state.progress)
        is SetVideoAnalysisState.Mismatch -> VideoThumbBadge.Reps(state.counted, state.confidence == CountConfidence.MEDIA)
        is SetVideoAnalysisState.Agrees -> VideoThumbBadge.Reps(state.counted, false)
        SetVideoAnalysisState.Inconclusive -> VideoThumbBadge.Inconclusive
        is SetVideoAnalysisState.Failed -> VideoThumbBadge.Failed
        else -> null
    }

    /** Miniatura local de prueba: una barra y su levantamiento; nunca abre la cámara. */
    private val restMedia by lazy {
        val file = folder.newFile("thumbnail")
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(42, 38, 34))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(242, 238, 230); strokeWidth = 4f
        }
        canvas.drawLine(20f, 45f, 100f, 45f, paint)
        canvas.drawLine(60f, 45f, 60f, 78f, paint)
        canvas.drawLine(60f, 78f, 40f, 104f, paint)
        canvas.drawLine(60f, 78f, 82f, 104f, paint)
        canvas.drawCircle(60f, 31f, 7f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        WorkoutMedia("video", WorkoutMediaKind.VIDEO, "", thumbPath = file.absolutePath, createdAtMs = 1L, durationMs = 12_000L)
    }

    @Composable private fun RestLine(state: SetVideoAnalysisState) {
        WorkoutRestVideoLine(restMedia, SetVideoAnalysisUi("video", state), badge(state), {}, {}, {})
    }

    @Composable private fun RestSurface(kind: Int, result: SetVideoAnalysisState?) {
        val slot: (@Composable () -> Unit)? = result?.let { { RestLine(it) } }
        val draft = remember { PostExerciseFeedbackDraft.from(listOf(exercise), emptyMap()).also { it.setTechnical(exercise.id, 8) } }
        RestFrame(kind) {
            Box(Modifier.fillMaxSize().testTag("video_rest_container")) {
                when (kind) {
                    0 -> RestLiveCard(remainingSeconds = 86, totalSeconds = 120, sessionAccentColor = WizardColors.text,
                        restState = rest, pendingRestSuggestion = null, lastSetOutcome = null,
                        lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 8),
                        onDecrease = {}, onIncrease = {}, onSkip = {}, onUseAdaptive = null, onExpand = {},
                        nextSet = RestNextSetSummary(2, 4, loadKg = 95.0, repsText = "4–6", intensityText = "RIR 2"),
                        clockActive = false, videoSlot = slot)
                    1 -> RestFeedbackCard(remainingSeconds = 86, totalSeconds = 120, shape = RestClockShape.TOWER,
                        feedbackExercises = listOf(exercise), draft = draft, insights = FeedbackInsights.Empty,
                        explainDiscomfort = { null }, linked = emptyList(), onSave = {}, onSkipRest = {}, onExpand = {},
                        clockActive = false, videoSlot = slot)
                    else -> RestTimerOverlay(state = rest, remainingSeconds = 86, hazeState = remember { HazeState() },
                        lastCompletedSet = CompletedSet(id = "last", weight = 95.0, reps = 8),
                        onDecrease = {}, onIncrease = {}, onSkip = {}, clockActive = false, onMinimize = {},
                        videoSlot = slot,
                        postExerciseFeedbackContent = if (kind == 3) ({ PostExerciseFeedbackPanel(
                            feedbackExercises = listOf(exercise), postExerciseFeedbackByExerciseId = emptyMap(),
                            sessionAccentColor = SymbolPalette.musculo, canonicalKeyOf = { it.id }, onSave = {},
                            draft = draft, insights = FeedbackInsights.Empty,
                        ) }) else null,
                    )
                }
            }
        }
    }

    @Composable private fun RestFrame(kind: Int, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (kind < 2) Box(Modifier.padding(top = 16.dp).width(312.dp)) {
                LiveSessionCardFrame { content() }
            } else Box(Modifier.size(360.dp, 880.dp)) { content() }
        }
    }

    /** Compara el contenedor en píxeles, no solo una altura declarada, con y sin análisis. */
    private fun restSurface(name: String, scale: Float, kind: Int) {
        val state = mutableStateOf<SetVideoAnalysisState?>(null)
        render(name, scale, 880.dp, saveImmediately = false) { RestSurface(kind, state.value) }
        val bounds = rule.onNodeWithTag("video_rest_container").fetchSemanticsNode().boundsInRoot
        val baseline = save("${name}_none")
        rule.runOnIdle { state.value = SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.MEDIA) }
        val rendered = save(name)
        assertEquals(bounds, rule.onNodeWithTag("video_rest_container").fetchSemanticsNode().boundsInRoot)
        if (kind == 0 && scale > 1f) {
            val text = rule.onNodeWithTag(RestVideoTextTag)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val range = layout.layoutInput.text.getLinkAnnotations(0, layout.layoutInput.text.length).last()
            val link = layout.getPathForRange(range.start, range.end).getBounds()
            val box = text.fetchSemanticsNode().boundsInRoot
            val top = box.top + (box.height - layout.size.height) / 2f
            var brightInk = 0
            // La mitad inferior de la última acción debe conservar la tinta, aun con contenido desplazable debajo.
            for (y in (top + link.center.y).toInt() until (top + link.bottom).toInt()) {
                for (x in (box.left + link.left).toInt() until (box.left + link.right).toInt()) {
                    if (android.graphics.Color.red(rendered.getPixel(x, y)) >= 230) brightInk++
                }
            }
            assertTrue("El fundido no borra la acción de la fila del vídeo", brightInk > 5)
        }
        val radius = (28f * host!!.resources.displayMetrics.density).toInt()
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                val edgeX = minOf(x - bounds.left.toInt(), bounds.right.toInt() - 1 - x)
                val edgeY = minOf(y - bounds.top.toInt(), bounds.bottom.toInt() - 1 - y)
                if (edgeX < 3 || edgeY < 3 || (kind < 2 && edgeX < radius && edgeY < radius)) {
                    assertEquals("Contenedor idéntico en $name ($x,$y)", baseline.getPixel(x, y), rendered.getPixel(x, y))
                }
            }
        }
        baseline.recycle(); rendered.recycle()
    }

    private fun restLines(scale: Float, label: String) {
        render("video_rest_lines_$label", scale, 920.dp, saveImmediately = false) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                restStates.forEach { (name, state) ->
                    Column {
                        Text(name, style = KpknType.caption, color = WizardColors.textFaint)
                        // Ancho real de la tarjeta: 272 dp menos sus 28 dp de relleno.
                        Box(Modifier.width(244.dp)) { RestLine(state) }
                    }
                }
            }
        }
        save("video_rest_lines_$label").recycle()
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag(RestVideoTextTag)).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) }
            assertFalse("La línea no se corta: ${layouts.single().layoutInput.text}", layouts.single().hasVisualOverflow)
        }
    }

    @Test fun states100() = render("video_states_100", 1f, 1300.dp) { States() }
    @Test fun states130() = render("video_states_130", 1.3f, 1500.dp) { States() }
    @Test fun card100() = cardStates(1f, "100")
    @Test fun card130() = cardStates(1.3f, "130")
    @Test fun rest100() = restSurface("video_rest_100", 1f, 2)
    @Test fun rest130() = restSurface("video_rest_130", 1.3f, 2)
    @Test fun restFeedback100() = restSurface("video_rest_feedback_100", 1f, 3)
    @Test fun restFeedback130() = restSurface("video_rest_feedback_130", 1.3f, 3)
    @Test fun compact100() = restSurface("video_rest_compact_100", 1f, 0)
    @Test fun compact130() = restSurface("video_rest_compact_130", 1.3f, 0)
    @Test fun compactFeedback100() = restSurface("video_feedback_compact_100", 1f, 1)
    @Test fun compactFeedback130() = restSurface("video_feedback_compact_130", 1.3f, 1)
    @Test fun restLines100() = restLines(1f, "100")
    @Test fun restLines130() = restLines(1.3f, "130")
}
