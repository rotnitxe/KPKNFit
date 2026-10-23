package com.example.kpkn.screens.workout.components

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class Cal3CardUiTest {
    @get:Rule val rule = createComposeRule()
    private val output get() = System.getenv("KPKN_RENDER_DIR")?.let(::File)
    private fun state(f: Cal3Fixture, name: String, height: Int = 880, font: Float = 1f) {
        val contract = Cal3CardContract(rule)
        contract.show(f, height, font = font)
        contract.assertFrame()
        rule.onNodeWithTag("calibration-result").assertIsDisplayed()
        output?.let { contract.save(it, "cal3-$name-$height-${(font*100).toInt()}") }
    }
    @Test fun fullWalkthroughAt880() = Cal3CardContract(rule).walkthrough(880, output)
    @Test fun fullWalkthroughAt740() = Cal3CardContract(rule).walkthrough(740, output)
    @Test fun raise880() = state(cal3Fixture(), "raise")
    @Test fun raise740LargeText() = state(cal3Fixture(), "raise", 740, 1.3f)
    @Test fun hold880() { state(cal3Fixture(reference = 94.3), "hold"); rule.onNodeWithText("Mantener las cargas").assertIsDisplayed() }
    @Test fun hold740() = state(cal3Fixture(reference = 94.3), "hold", 740)
    @Test fun lower880() { state(cal3Fixture(weight = 60.0, rir = 0), "lower"); rule.onNodeWithText("Bajar las cargas").assertIsDisplayed() }
    @Test fun lower740() = state(cal3Fixture(weight = 60.0, rir = 0), "lower", 740)
    @Test fun firstReference880() { state(cal3Fixture(reference = null), "first"); rule.onNodeWithText("Primera referencia").assertIsDisplayed() }
    @Test fun firstReference740() = state(cal3Fixture(reference = null), "first", 740)
    @Test fun noEffort880() { state(cal3Fixture(rir = null), "no-effort"); rule.onNodeWithTag("calibration-apply").assertDoesNotExist() }
    @Test fun noEffort740() = state(cal3Fixture(rir = null), "no-effort", 740)
    @Test fun future880() { state(cal3Fixture(scope = CalibrationScope.NEXT_SESSIONS), "future"); rule.onNodeWithText("Guardar propuesta").assertIsDisplayed() }
    @Test fun future740() = state(cal3Fixture(scope = CalibrationScope.NEXT_SESSIONS), "future", 740)
    @Test fun normalAmrapShowsMinimumAndRealNoRuleEffect() {
        val contract = Cal3CardContract(rule)
        contract.show(cal3Fixture(), 740, normalAmrap = true)
        rule.onNodeWithText("Mínimo superado · 10/6 reps").assertIsDisplayed()
        rule.onNodeWithText("Registrado en tu historial y récords").assertIsDisplayed()
        rule.onNodeWithText("Sin regla de progresión: queda registrado").assertIsDisplayed()
        contract.assertFrame()
    }
    @Test fun normalAmrapNeverClampsAShortSetToItsMinimum() {
        Cal3CardContract(rule).show(cal3Fixture(reps = 5), 740, normalAmrap = true)
        rule.onNodeWithText("Mínimo no alcanzado · 5/6 reps").assertIsDisplayed()
    }
    @Test fun appliedLoadsRemainMarkedOnTheLineAt740WithLargeText() {
        val f = cal3Fixture()
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, 1_790_000_003_000L)
        state(f.copy(state = applied), "applied", 740, 1.3f)
        rule.onAllNodesWithContentDescription("calibrada", substring = true).assertCountEquals(3)
        rule.onAllNodesWithText("calibrada", useUnmergedTree = true).assertCountEquals(3)
        val labels = rule.onAllNodesWithText("calibrada", useUnmergedTree = true)
        repeat(2) { i ->
            val layouts = mutableListOf<TextLayoutResult>()
            labels[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertTrue(layout.getLineBottom(layout.lineCount - 1) <= labels[i].fetchSemanticsNode().boundsInRoot.height + 1)
        }
    }
    @Test fun aFailedDecisionIsVisibleInsideItsOwnCardAndCanBeRetried() {
        val f = cal3Fixture()
        val contract = Cal3CardContract(rule)
        contract.show(f.copy(state = f.state.copy(calibrationError = CalibrationActionFailure(f.record.id, "Reintenta; las cargas se conservan."))), 740)
        rule.onNodeWithTag("calibration-error").assertIsDisplayed()
        rule.onNodeWithTag("calibration-apply").assertIsEnabled()
        contract.assertFrame()
    }
    @Test fun anErrorFromAnotherDecisionNeverAppearsInThisExerciseCard() {
        val f = cal3Fixture()
        Cal3CardContract(rule).show(f.copy(state = f.state.copy(calibrationError = CalibrationActionFailure("other-decision", "Error ajeno"))), 740)
        rule.onNodeWithTag("calibration-error").assertDoesNotExist()
    }
    @Test fun editingWeightedBodyweightRestoresAddedKgInsteadOfNormalizedBodyMass() {
        val f = cal3Fixture(weight = 20.0, mode = LoadModeV2.LASTRE)
        val normalized = f.completed.copy(weight = 80.0)
        Cal3CardContract(rule).show(f.copy(completed = normalized,
            state = f.state.copy(completedSets = mapOf(workoutSetKey(f.exercise.id, 0) to normalized))), 740)
        rule.onNodeWithTag("calibration-edit").performClick()
        rule.onNode(hasSetTextAction() and hasText("20")).assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText("80")).assertDoesNotExist()
    }
}
