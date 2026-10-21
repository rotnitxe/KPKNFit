package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.design.WorkoutVideoChipTag
import com.example.kpkn.screens.workout.design.WorkoutVideoMenuTag
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.services.workout.VoiceSetEditPatch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class SetExecutionCardVideoTest {
    @get:Rule val rule = createComposeRule()
    private val holder = RecordActionHolder()
    private var recordedValue: Double? = null
    private var recordedAdvanced: SetAdvancedFeedback? = null
    private var draft: WorkoutSetDraft? = null

    private fun show(full: Int, partials: Int, withSuggestion: Boolean = true, unit: UnitModeV2 = UnitModeV2.REPS, confidence: CountConfidence = CountConfidence.ALTA) {
        val set = ExerciseSet(id = "s", targetReps = 8, weight = 95.0, targetRIR = 2,
            intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = unit)
        val exercise = Exercise(id = "e", name = "Sentadilla", sets = listOf(set))
        rule.setContent {
            KPKNTheme {
                Box(Modifier.width(320.dp)) {
                    SetInputCardV2(
                        exercise = exercise, setIndex = 0, currentSet = set, ghostSet = null,
                        onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                        recordActionHolder = holder, recordActionPageKey = "page",
                        initialDraft = WorkoutSetDraft(weightText = "95", valueText = if (withSuggestion) "8" else full.toString(),
                            intensityText = "2", partialReps = if (withSuggestion) null else partials),
                        onDraftChange = { value, _ -> draft = value },
                        videoAnalysis = if (withSuggestion) SetVideoAnalysisUi("v", SetVideoAnalysisState.Suggest(full, partials, 0, confidence)) else null,
                        onRecordV2 = { _, _, _, value, _, advanced, _, _, _, result ->
                            recordedValue = value
                            recordedAdvanced = advanced
                            result(RecordSetResult.Created("e_0"))
                        },
                        effortFireSecondsOverride = 1f,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun openMenu() {
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.onNodeWithTag(WorkoutVideoChipTag).performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertExists()
    }

    @Test fun use_only_updates_local_draft_and_keeps_effort_until_explicit_registration() {
        show(7, 1)
        openMenu()
        rule.onNodeWithText("Usar").performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.runOnIdle {
            assertNull(recordedValue)
            assertEquals("7", draft?.valueText)
            assertEquals(1, draft?.partialReps)
            assertEquals("2", draft?.intensityText)
        }
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(7.0, recordedValue!!, 0.0)
            assertEquals(1, recordedAdvanced?.partialReps)
            assertFalse(recordedAdvanced?.reachedFailure == true)
        }
    }

    @Test fun zero_complete_reps_with_partials_can_be_registered() {
        show(0, 2)
        openMenu()
        rule.onNodeWithText("Usar").performClick()
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(0.0, recordedValue!!, 0.0)
            assertEquals(2, recordedAdvanced?.partialReps)
        }
    }

    @Test fun restored_draft_partials_are_recorded_without_being_lost() {
        show(0, 3, withSuggestion = false)
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(0.0, recordedValue!!, 0.0)
            assertEquals(3, recordedAdvanced?.partialReps)
        }
    }

    @Test fun zero_without_partials_is_rejected() {
        show(0, 0, withSuggestion = false)
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle { assertNull(recordedValue) }
    }

    @Test fun zero_seconds_cannot_become_valid_by_adding_partials() {
        show(0, 3, withSuggestion = false, unit = UnitModeV2.TIME)
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle { assertNull(recordedValue) }
    }

    @Test fun manual_reps_selection_after_use_remains_authoritative() {
        show(7, 1)
        openMenu()
        rule.onNodeWithText("Usar").performClick()
        rule.onNodeWithText("9").performClick()
        rule.waitForIdle()
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(9.0, recordedValue!!, 0.0)
            assertEquals(1, recordedAdvanced?.partialReps)
            assertEquals("2", draft?.intensityText)
        }
    }
    private lateinit var completed: MutableState<CompletedSet>
    private val redraw = mutableStateOf(false)

    private fun showCompleted(full: Int = 8, partials: Int = 0, staleDraft: Boolean = true, confidence: CountConfidence = CountConfidence.ALTA) {
        completed = mutableStateOf(CompletedSet(id = "stable-id", weight = 95.0, reps = full,
            partialReps = partials.takeIf { it > 0 }, isPartial = partials > 0,
            actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 2.0, rir = 2))
        val set = ExerciseSet(id = "s", targetReps = 8, weight = 95.0, targetRIR = 2,
            intensityMode = IntensityMode.RIR, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
        val exercise = Exercise(id = "e", name = "Sentadilla", sets = listOf(set))
        rule.setContent {
            KPKNTheme {
                Box(Modifier.width(if (redraw.value) 319.dp else 320.dp)) {
                    SetInputCardV2(
                        exercise = exercise, setIndex = 0, currentSet = set, ghostSet = null,
                        sessionCompletedSet = completed.value,
                        onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                        recordActionHolder = holder, recordActionPageKey = "page",
                        initialDraft = if (staleDraft) WorkoutSetDraft(weightText = "95", valueText = "8",
                            intensityText = "2", reachedFailure = false) else null,
                        onDraftChange = { value, _ -> draft = value },
                        videoAnalysis = SetVideoAnalysisUi("v", SetVideoAnalysisState.Mismatch(
                            logged = 8, counted = 7, partials = 1, failed = 1, confidence = confidence)),
                        onVideoCorrect = { mismatch -> completed.value = applyCompletedSetPatch(completed.value,
                            CompletedSetPatch(edit = VoiceSetEditPatch(metricValue = mismatch.counted), partialReps = mismatch.partials)) },
                        onVideoMarkFailure = { completed.value = applyCompletedSetPatch(completed.value,
                            CompletedSetPatch(reachedFailure = true)) },
                        onRecordV2 = { _, _, _, value, _, advanced, _, _, _, result ->
                            recordedValue = value
                            recordedAdvanced = advanced
                            result(RecordSetResult.Updated("e_0"))
                        },
                        effortFireSecondsOverride = 1f,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun correcting_mounted_completed_set_with_same_id_survives_explicit_update() {
        showCompleted()
        openMenu()
        rule.onNodeWithText("Corregir").performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals("stable-id", completed.value.id)
            assertEquals("7", draft?.valueText)
            assertEquals(1, draft?.partialReps)
            assertEquals("2", draft?.intensityText)
            holder.actionForPage("page")!!.invoke()
        }
        rule.runOnIdle {
            assertEquals(7.0, recordedValue!!, 0.0)
            assertEquals(1, recordedAdvanced?.partialReps)
            assertFalse(recordedAdvanced?.reachedFailure == true)
        }
    }

    @Test fun muscular_failure_patch_with_same_id_updates_effort_and_does_not_become_execution_error() {
        showCompleted()
        openMenu()
        rule.onNodeWithText("Marcar fallo").performClick()
        rule.runOnIdle {
            assertEquals("stable-id", completed.value.id)
            assertEquals(true, draft?.reachedFailure)
            assertFalse(completed.value.isFailedSet)
            holder.actionForPage("page")!!.invoke()
        }
        rule.runOnIdle {
            assertEquals(8.0, recordedValue!!, 0.0)
            assertEquals(true, recordedAdvanced?.reachedFailure)
            assertEquals(IntensityMode.FAILURE, recordedAdvanced?.actualIntensityMode)
            assertFalse(recordedAdvanced?.isFailedSet == true)
        }
    }

    @Test fun mounting_completed_zero_full_with_partials_without_draft_keeps_zero() {
        showCompleted(full = 0, partials = 3, staleDraft = false)
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(0.0, recordedValue!!, 0.0)
            assertEquals(3, recordedAdvanced?.partialReps)
        }
    }

    @Test fun ordinary_recomposition_preserves_manual_edit_after_persisted_correction() {
        showCompleted()
        openMenu()
        rule.onNodeWithText("Corregir").performClick()
        rule.onNodeWithText("9").performClick()
        rule.runOnIdle { redraw.value = true }
        rule.waitForIdle()
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(9.0, recordedValue!!, 0.0)
            assertEquals(1, recordedAdvanced?.partialReps)
            assertEquals("2", draft?.intensityText)
        }
    }

    @Test fun medium_use_resets_the_wheel_and_keeps_partials_and_effort() {
        show(7, 2, confidence = CountConfidence.MEDIA)
        // El selector tenía una cifra distinta: «Usar» debe sustituir también su centro.
        rule.onNodeWithText("9").performClick()
        openMenu()
        rule.onNodeWithText("Usar ≈\u00A07").performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.runOnIdle {
            assertNull(recordedValue)
            assertEquals("7", draft?.valueText)
            assertEquals(2, draft?.partialReps)
            assertEquals("2", draft?.intensityText)
            holder.actionForPage("page")!!.invoke()
        }
        rule.runOnIdle {
            assertEquals(7.0, recordedValue!!, 0.0)
            assertEquals(2, recordedAdvanced?.partialReps)
            assertFalse(recordedAdvanced?.reachedFailure == true)
        }
    }

    @Test fun medium_correction_keeps_the_persisted_patch_and_reinitializes_the_wheel() {
        showCompleted(confidence = CountConfidence.MEDIA)
        rule.onNodeWithText("9").performClick()
        openMenu()
        rule.onNodeWithText("Marcar fallo").assertDoesNotExist()
        rule.onNodeWithText("Corregir a ≈\u00A07").performClick()
        rule.onNodeWithTag(WorkoutVideoMenuTag).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals("stable-id", completed.value.id)
            assertEquals(7, completed.value.reps)
            assertEquals(1, completed.value.partialReps)
            assertEquals("7", draft?.valueText)
            assertEquals("2", draft?.intensityText)
            holder.actionForPage("page")!!.invoke()
        }
        rule.runOnIdle {
            assertEquals(7.0, recordedValue!!, 0.0)
            assertEquals(1, recordedAdvanced?.partialReps)
        }
    }

    @Test fun medium_partials_with_zero_full_reps_can_be_used() {
        show(0, 2, confidence = CountConfidence.MEDIA)
        openMenu()
        rule.onNodeWithText("Usar ≈\u00A00").performClick()
        rule.runOnIdle { holder.actionForPage("page")!!.invoke() }
        rule.runOnIdle {
            assertEquals(0.0, recordedValue!!, 0.0)
            assertEquals(2, recordedAdvanced?.partialReps)
        }
    }

}
