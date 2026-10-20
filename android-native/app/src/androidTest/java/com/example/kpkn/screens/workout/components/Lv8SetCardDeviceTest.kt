package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.SetAdvancedFeedback
import com.example.kpkn.screens.workout.RecordSetResult
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Contratos de LV8 en el dispositivo: esfuerzo elegido y tarjeta fija incluso al 130 %. */
@RunWith(AndroidJUnit4::class)
class Lv8SetCardDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val holder = RecordActionHolder()
    private val recorded = mutableListOf<Double?>()
    private val feedback = mutableListOf<SetAdvancedFeedback>()
    private val recordedValues = mutableListOf<Double>()
    private val set = ExerciseSet(
        id = "lv8-set", targetReps = 6, weight = 50.0,
        loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS,
    )

    private fun show(fontScale: Float = 1f, rom: Boolean = false, executionCallback: Boolean = false) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                KPKNTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.width(272.dp).testTag("lv8-device-card")) {
                            SetInputCardV2(
                                exercise = Exercise(id = "lv8-ex", name = "Press banca", sets = listOf(set), trackRom = rom),
                                setIndex = 0, currentSet = set, ghostSet = null, weightSuggestion = null,
                                initialBodyWeight = 80.0, recordActionHolder = holder, recordActionPageKey = "lv8-device",
                                onShowHistory = {}, onSetBodyWeight = {},
                                onExecutionError = if (executionCallback) ({ effort ->
                                    recorded += effort.actualIntensityValue
                                    feedback += effort
                                }) else null,
                                onRecordV2 = { _, _, _, value, intensity, advanced, _, _, _, done ->
                                    recorded += intensity
                                    recordedValues += value
                                    feedback += advanced
                                    done(RecordSetResult.Created("lv8-ex_0"))
                                },
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun record() {
        rule.runOnIdle {
            val action = holder.actionForPage("lv8-device")
            assertNotNull("El FAB debe enlazar la acción de la tarjeta", action)
            action?.invoke()
        }
        rule.waitForIdle()
    }

    @Test fun sinObjetivoNoRegistraRpeFantasmaYTocarElMinimoCuenta() {
        show()
        val height = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        record()
        assertTrue(recorded.isEmpty())
        rule.onNodeWithText(EffortRequiredHint).assertExists()
        val hintHeight = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue(abs(height - hintHeight) < 1f)
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        record()
        assertEquals(1, recorded.size)
        assertEquals(0.0, recorded.single()!!, 0.0)
    }

    private fun markExecutionErrorAndReturn(): Float {
        val original = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.onNodeWithText("Error de ejecución", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Volver a la serie").performClick()
        rule.waitForIdle()
        val failed = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Marcar Error no puede alargar la tarjeta: $original -> $failed", abs(original - failed) < 1f)
        return failed
    }

    @Test fun errorPrimeroExigeEsfuerzoSinDesactivarErrorYConservaAlturaYPayload() {
        show()
        val failedHeight = markExecutionErrorAndReturn()
        record()
        assertTrue(recorded.isEmpty())
        rule.onNodeWithText(EffortRequiredHint).assertExists()
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        val chosenHeight = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue(abs(failedHeight - chosenHeight) < 1f)
        record()
        assertEquals(1, recorded.size)
        assertEquals(0.0, recorded.single()!!, 0.0)
        assertEquals(0.0, recordedValues.single(), 0.0)
        assertTrue(feedback.single().isFailedSet)
        assertTrue(feedback.single().executionError)
        assertEquals(0.0, feedback.single().actualIntensityValue!!, 0.0)
    }

    @Test fun laAccionDeErrorDeLaSesionRecibeElEsfuerzoElegido() {
        show(executionCallback = true)
        markExecutionErrorAndReturn()
        record()
        assertTrue(feedback.isEmpty())
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        record()
        assertEquals(1, feedback.size)
        assertTrue(feedback.single().isFailedSet)
        assertTrue(feedback.single().executionError)
        assertEquals(0.0, feedback.single().actualIntensityValue!!, 0.0)
    }

    @Test fun avanzadoConRomYLetraGrandeNoAlargaLaTarjeta() {
        show(fontScale = 1.3f, rom = true)
        val height = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.onNodeWithText("Drop-set").performClick()
        rule.onNodeWithTag(TechniqueChainTag).assertExists()
        rule.waitForIdle()
        val advancedHeight = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue(abs(height - advancedHeight) < 1f)
        rule.onNodeWithContentDescription("Volver a la serie").performClick()
        rule.waitForIdle()
        val returnedHeight = rule.onNodeWithTag("lv8-device-card").fetchSemanticsNode().boundsInRoot.height
        assertTrue(abs(height - returnedHeight) < 1f)
    }
}
