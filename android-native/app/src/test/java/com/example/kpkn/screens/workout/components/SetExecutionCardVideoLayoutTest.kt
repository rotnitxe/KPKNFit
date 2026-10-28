package com.example.kpkn.screens.workout.components

import android.view.MotionEvent
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.design.WorkoutVideoChipTag
import com.example.kpkn.screens.workout.design.WorkoutVideoMenuTag
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
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
class SetExecutionCardVideoLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val analysis = mutableStateOf<SetVideoAnalysisUi?>(null)
    private val unit = mutableStateOf(UnitModeV2.REPS)
    private var opened: String? = null
    private var retried: String? = null

    private fun assertCompleteText(text: String, layouts: List<TextLayoutResult>) {
        assertTrue("Hay una medida para $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val lastLine = layout.lineCount - 1
            assertEquals("Se dibuja todo $text", text.length, layout.getLineEnd(lastLine))
            // El párrafo conserva el ancho máximo aun cuando Text se ajuste a sus glifos.
            // Se comprueba cada línea contra el rectángulo que Text dibuja, sin tolerancias.
            for (line in 0..lastLine) {
                assertFalse("Sin elipsis en $text", layout.isLineEllipsized(line))
                assertTrue("Inicio visible en $text", layout.getLineLeft(line) >= 0f)
                assertTrue("Final visible en $text: ${layout.getLineRight(line)} / ${layout.size.width}",
                    kotlin.math.ceil(layout.getLineRight(line)).toInt() <= layout.size.width)
                assertTrue("Alto visible en $text: ${layout.getLineBottom(line)} / ${layout.size.height}",
                    kotlin.math.ceil(layout.getLineBottom(line)).toInt() <= layout.size.height)
            }
        }
    }

    private fun show(fontScale: Float = 1f) {
        // Popup tiene otra ventana: la escala también debe llegar a sus recursos Android.
        RuntimeEnvironment.setFontScale(fontScale)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale), LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    val set = ExerciseSet(id = "s", targetReps = 8, weight = 95.0, targetRIR = 2,
                        intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = unit.value)
                    Box(Modifier.width(360.dp).padding(16.dp)) {
                        SetInputCardV2(
                            exercise = Exercise(id = "e", name = "Sentadilla", sets = listOf(set)), setIndex = 0,
                            currentSet = set, ghostSet = null, onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                            recordActionHolder = RecordActionHolder(), videoAnalysis = analysis.value,
                            onOpenAnalysis = { opened = it }, onRetryAnalysis = { retried = it },
                            onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> }, effortFireSecondsOverride = 1f,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun checkBounds(fontScale: Float) {
        show(fontScale)
        val card = rule.onNodeWithTag("set_execution_card").fetchSemanticsNode().boundsInRoot
        val header = rule.onNodeWithTag("set_reps_header").fetchSemanticsNode().boundsInRoot
        val effort = rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().boundsInRoot
        val cases = listOf(
            SetVideoAnalysisState.Analyzing(42),
            SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA, 95),
            SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.MEDIA),
            SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.ALTA),
            SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.MEDIA),
            SetVideoAnalysisState.Agrees(8), SetVideoAnalysisState.Inconclusive,
            SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA),
            SetVideoAnalysisState.Mismatch(8, 27, 4, 2, CountConfidence.BAJA),
            SetVideoAnalysisState.Failed(null), SetVideoAnalysisState.Unsupported("Por tiempo"),
        )
        cases.forEach { state ->
            rule.runOnIdle { analysis.value = SetVideoAnalysisUi("v", state) }
            assertEquals(state.toString(), card, rule.onNodeWithTag("set_execution_card").fetchSemanticsNode().boundsInRoot)
            assertEquals(state.toString(), header, rule.onNodeWithTag("set_reps_header").fetchSemanticsNode().boundsInRoot)
            // Una fila insertada puede desplazar el esfuerzo aunque el contenedor ya tenga altura fija.
            assertEquals(state.toString(), effort, rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().boundsInRoot)
            if (state is SetVideoAnalysisState.Unsupported) {
                rule.onNodeWithTag(WorkoutVideoChipTag).assertDoesNotExist()
            } else {
                val chip = rule.onNodeWithTag(WorkoutVideoChipTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(header.contains(chip.topLeft) && header.contains(Offset(chip.right - 1, chip.bottom - 1)))
                rule.onNodeWithTag(WorkoutVideoChipTag).performClick()
                rule.onNodeWithTag(WorkoutVideoMenuTag).assertIsDisplayed()
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
                val textLayouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithText(com.example.kpkn.screens.workout.design.workoutVideoRepsText(state))
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
                assertCompleteText(com.example.kpkn.screens.workout.design.workoutVideoRepsText(state), textLayouts)
                val action = when (state) {
                    is SetVideoAnalysisState.Suggest -> when (state.confidence) {
                        CountConfidence.ALTA -> "Usar"
                        CountConfidence.MEDIA -> "Usar ≈\u00A0${state.reps}"
                        CountConfidence.BAJA -> "Ver análisis"
                    }
                    is SetVideoAnalysisState.Mismatch -> when (state.confidence) {
                        CountConfidence.ALTA -> "Corregir"
                        CountConfidence.MEDIA -> "Corregir a ≈\u00A0${state.counted}"
                        CountConfidence.BAJA -> "Ver análisis"
                    }
                    else -> "Ver análisis"
                }
                val actionLayouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithText(action).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(actionLayouts) }
                assertCompleteText(action, actionLayouts)
                assertEquals(card, rule.onNodeWithTag("set_execution_card").fetchSemanticsNode().boundsInRoot)
                rule.onNodeWithText("Ver análisis").performClick()
                rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
            }
        }
    }

    @Test fun all_states_keep_card_header_and_effort_bounds_at_100_percent() = checkBounds(1f)
    @Test fun all_states_keep_card_header_and_effort_bounds_at_130_percent() = checkBounds(1.3f)

    @Test fun chip_is_absent_in_time_distance_and_custom_modes() {
        analysis.value = SetVideoAnalysisUi("v", SetVideoAnalysisState.Agrees(8))
        show()
        rule.onNodeWithTag(WorkoutVideoChipTag).assertExists()
        listOf(UnitModeV2.TIME, UnitModeV2.DISTANCE, UnitModeV2.CUSTOM).forEach { next ->
            rule.runOnIdle { unit.value = next }
            rule.onNodeWithTag(WorkoutVideoChipTag).assertDoesNotExist()
        }
    }

    @Test fun chip_reads_the_whole_phrase_and_its_touch_target_extends_beyond_the_pill() {
        analysis.value = SetVideoAnalysisUi("v", SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.ALTA, 95))
        show(1.3f)
        rule.onNodeWithContentDescription("El vídeo cuenta 8 · +1 parcial · ROM\u00A0≈\u00A095\u00A0%. Toca para ver las opciones").assertExists()
        val chip = rule.onNodeWithTag(WorkoutVideoChipTag).fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("set_execution_card").fetchSemanticsNode().boundsInRoot
        // Refinamiento 02: la cabecera de la cantidad mide 21 dp (antes 24) y la píldora ocupa su altura; el área táctil sigue en 48 dp.
        assertEquals(21f * 3f, chip.height, 0.01f)
        assertTrue(chip.width >= 48f * 3f)
        val touchBounds = rule.onNodeWithTag(WorkoutVideoChipTag).fetchSemanticsNode().touchBoundsInRoot
        assertTrue(touchBounds.height >= 48f * 3f && touchBounds.width >= 48f * 3f)
        rule.onNodeWithTag("set_execution_card").performTouchInput {
            click(Offset(chip.center.x - card.left, chip.top - card.top - 10f * 3f))
        }
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertIsDisplayed()
    }

    @Test fun failed_analysis_retries_explicitly_and_dismisses_the_menu() {
        analysis.value = SetVideoAnalysisUi("failed", SetVideoAnalysisState.Failed("decode"))
        show()
        rule.runOnIdle { assertNull(retried) }
        rule.onNodeWithTag(WorkoutVideoChipTag).performClick()
        rule.onNodeWithText("Reintentar").performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.runOnIdle { assertEquals("failed", retried) }
    }

    @Test fun outside_touch_dismisses_the_popup_without_applying_anything() {
        analysis.value = SetVideoAnalysisUi("v", SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.MEDIA))
        show()
        rule.onNodeWithTag(WorkoutVideoChipTag).performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertExists()
        rule.runOnUiThread {
            val popup = WindowInspector.getGlobalWindowViews().last { it.javaClass.simpleName == "PopupLayout" }
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try { popup.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.runOnIdle { assertNull(opened); assertNull(retried) }
    }
}
