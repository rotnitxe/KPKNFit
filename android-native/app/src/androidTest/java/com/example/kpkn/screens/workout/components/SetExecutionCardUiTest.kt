package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.RecordSetResult
import com.example.kpkn.screens.workout.SetAdvancedFeedback
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.screens.workout.WorkoutTechniqueDraftKind
import com.example.kpkn.screens.workout.WorkoutTechniqueMainCaptureDraft
import com.example.kpkn.screens.workout.WorkoutTechniqueProgressDraft
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.services.workout.VoiceSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SetExecutionCardUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unilateralLockedSideWheelKeepsStableDraftValue() {
        var lastDraft: WorkoutSetDraft? = null
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = unilateralExercise(),
                    setIndex = 0,
                    currentSet = unilateralSet(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    isActivePage = true,
                    activeSide = "left",
                    sideLocked = true,
                    onDraftChange = { draft, _ -> lastDraft = draft },
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("11").performClick()
        composeRule.waitForIdle()
        assertEquals("11", lastDraft?.valueText)
        composeRule.onNodeWithText("12").performClick()
        composeRule.waitForIdle()
        assertEquals("12", lastDraft?.valueText)
        composeRule.onNodeWithText("11").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("11").assertExists()
        assertEquals("11", lastDraft?.valueText)
        assertEquals("left", lastDraft?.selectedSide)
    }

    @Test
    fun suggestedLoadUsesChipWithoutSeparateSuggestionCard() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = bilateralExercise(),
                    setIndex = 0,
                    currentSet = bilateralSet(),
                    ghostSet = null,
                    weightSuggestion = WeightSuggestion(suggestedWeight = 82.5, reason = "AUGE"),
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    isActivePage = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Carga sugerida").assertDoesNotExist()
        composeRule.onNodeWithText("Sugerida").assertDoesNotExist()
        composeRule.onNodeWithText("82,5").assertExists()
    }

    @Test
    fun commandDockShowsIconOnlyCompleteFabAndMicAppendage() {
        composeRule.setContent {
            MaterialTheme {
                val completed = remember { mutableStateOf(false) }
                val voice = remember { mutableStateOf(false) }
                WorkoutCommandDock(
                    exercise = bilateralExercise(),
                    setIndex = 0,
                    activeSide = null,
                    isUnilateral = false,
                    voiceSessionEnabled = false,
                    voiceSessionState = VoiceSessionState(),
                    onToggleVoice = { voice.value = true },
                    onPrimaryAction = { completed.value = true },
                )
                if (completed.value) androidx.compose.material3.Text("completed")
                if (voice.value) androidx.compose.material3.Text("voice")
            }
        }

        composeRule.onNodeWithText("Completar S1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Completar serie").performClick()
        composeRule.onNodeWithContentDescription("Activar control por voz").performClick()
        composeRule.onNodeWithText("completed").assertExists()
        composeRule.onNodeWithText("voice").assertExists()
    }

    @Test
    fun commandDockIgnoresCompleteTapWhileRecording() {
        composeRule.setContent {
            MaterialTheme {
                val completed = remember { mutableStateOf(false) }
                WorkoutCommandDock(
                    exercise = bilateralExercise(),
                    setIndex = 0,
                    activeSide = null,
                    isUnilateral = false,
                    voiceSessionEnabled = false,
                    voiceSessionState = VoiceSessionState(),
                    onToggleVoice = {},
                    onPrimaryAction = { completed.value = true },
                    primaryActionEnabled = false,
                )
                if (completed.value) androidx.compose.material3.Text("completed")
            }
        }

        composeRule.onNodeWithContentDescription("Registrando serie").performClick()
        composeRule.onNodeWithText("completed").assertDoesNotExist()
    }

    @Test
    fun recordActionUsesActiveUnilateralSideOnly() {
        var recordedSide: String? = null
        val holder = RecordActionHolder()
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = unilateralExercise(),
                    setIndex = 0,
                    currentSet = unilateralSet(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = holder,
                    isActivePage = true,
                    activeSide = "right",
                    sideLocked = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _: LoadModeV2, _: UnitModeV2, _: Double, _: Double, _: Double?, _: SetAdvancedFeedback, _: Boolean, _: Double?, side: String?, onResult: (RecordSetResult) -> Unit ->
                        recordedSide = side
                        onResult(RecordSetResult.Created("bilateral-ex_0"))
                    },
                )
            }
        }

        composeRule.runOnIdle { holder.action?.invoke() }
        composeRule.runOnIdle { assertEquals("right", recordedSide) }
    }

    @Test
    fun romIsNotReportedWhenTheExerciseDoesNotTrackIt() {
        // The ROM slider is hidden for this exercise, so a default of 100 would
        // be sent as a measurement nobody took.
        assertNull(recordOnce(bilateralExercise().copy(trackRom = false)).rom)
    }

    @Test
    fun romStartsAtFullRangeWhenTheExerciseTracksIt() {
        assertEquals(100, recordOnce(bilateralExercise().copy(trackRom = true)).rom)
    }

    @Test
    fun record_action_rebinds_from_s1_to_s2_after_successful_ui_callback() {
        val exercise = bilateralExercise()
        val holder = RecordActionHolder()
        val scopeOwner = Any()
        val activeSet = mutableIntStateOf(0)
        val recordedSetIndexes = mutableListOf<Int>()

        composeRule.setContent {
            MaterialTheme {
                val setIndex = activeSet.intValue
                SetInputCardV2(
                    exercise = exercise,
                    setIndex = setIndex,
                    currentSet = bilateralSet(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = holder,
                    recordActionScopeOwner = scopeOwner,
                    recordActionPageKey = "${exercise.id}:$setIndex:B",
                    isActivePage = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, onResult ->
                        recordedSetIndexes += setIndex
                        activeSet.intValue = setIndex + 1
                        onResult(RecordSetResult.Created("bilateral-ex_$setIndex"))
                    },
                )
            }
        }

        composeRule.runOnIdle {
            holder.actionForPage("${exercise.id}:0:B")?.invoke()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, activeSet.intValue)
            assertNull(holder.actionForPage("${exercise.id}:0:B"))
            assertNotNull(holder.actionForPage("${exercise.id}:1:B"))
            holder.actionForPage("${exercise.id}:1:B")?.invoke()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(0, 1), recordedSetIndexes)
    }

    @Test
    fun recordFabShowsRegisterLabelByDefault() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutRecordFab(
                    sessionAccentColor = Color(0xFF00E5FF),
                    isUpdateMode = false,
                    enabled = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Registrar serie").assertExists()
    }

    @Test
    fun recordFabShowsUpdateLabelWhenSeriesAlreadyLogged() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutRecordFab(
                    sessionAccentColor = Color(0xFF00E5FF),
                    isUpdateMode = true,
                    enabled = true,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Actualizar serie").assertExists()
    }

    @Test
    fun recordFabInvokesOnClickWhenEnabled() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                WorkoutRecordFab(
                    sessionAccentColor = Color(0xFF00E5FF),
                    isUpdateMode = false,
                    enabled = true,
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Registrar serie").performClick()
        composeRule.runOnIdle { assertEquals(true, clicked) }
    }

    @Test
    fun advancedOptionsCtaUsesNewCopyAndHidesReportLabel() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = bilateralExercise(),
                    setIndex = 0,
                    currentSet = bilateralSet(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    isActivePage = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Opciones avanzadas").assertExists()
        composeRule.onNodeWithText("Ver ejercicio/Fotos").assertExists()
        composeRule.onNodeWithText("Reportar serie").assertDoesNotExist()
        composeRule.onNodeWithText("¿Cambio de planes o añadir técnica de intensidad?").assertDoesNotExist()
    }

    @Test
    fun scheduledVolumeReplacedKeepsCardWithoutGuidedOverlay() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = bilateralExercise(),
                    setIndex = 0,
                    currentSet = scheduledDropset(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    isActivePage = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("DROPSET").assertExists()
        composeRule.onNodeWithText("Opciones avanzadas").assertExists()
        composeRule.onNodeWithText("Ver ejercicio/Fotos").assertExists()
        composeRule.onNodeWithText("Confirma las 3 reps de esta mini-serie.").assertDoesNotExist()
        composeRule.onNodeWithText("Saltar técnica y registrar solo la serie").assertDoesNotExist()
    }

    @Test
    fun guidedDropFinalCommitRetryDoesNotAppendAnotherRow() {
        val initialRows = listOf(DropSetData(weight = 15.0, reps = 8))
        val finalRows = initialRows + DropSetData(weight = 12.5, reps = 6)
        assertGuidedCommitRetry(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 1,
            phaseCount = 2,
            dropRows = initialRows,
            restRows = emptyList(),
            dropWeightText = "12.5",
            repsText = "6",
            currentSet = guidedDropSet(),
            expected = guidedExpectedFeedback(dropSets = finalRows),
        )
    }

    @Test
    fun guidedRestPauseFinalCommitRetryDoesNotAppendAnotherRow() {
        // The restored row keeps the rest it was recorded with (20 s, different
        // on purpose).  The row captured now carries the rest the guided
        // countdown actually ran: the fixed configured pause, not a copy of
        // the previous row.
        val initialRows = listOf(RestPauseData(restTime = 20, reps = 8))
        val finalRows = initialRows + RestPauseData(restTime = RestPausePlanDefaults.PauseSeconds, reps = 6)
        assertGuidedCommitRetry(
            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
            phaseIndex = 1,
            phaseCount = 2,
            dropRows = emptyList(),
            restRows = initialRows,
            dropWeightText = "",
            repsText = "6",
            currentSet = guidedRestPauseSet(),
            expected = guidedExpectedFeedback(restPauses = finalRows),
        )
    }

    @Test
    fun guidedSkipRetryKeepsExplicitEmptyCommitSeparateFromEnteredRows() {
        val partialRows = listOf(DropSetData(weight = 15.0, reps = 8))
        assertGuidedCommitRetry(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 0,
            phaseCount = 2,
            dropRows = partialRows,
            restRows = emptyList(),
            dropWeightText = "12.5",
            repsText = "6",
            currentSet = guidedDropSet(),
            expected = guidedExpectedFeedback(),
            skipOnFirstAttempt = true,
            expectedFormDropRows = partialRows,
        )
    }

    @Test
    fun guidedRestPauseCountdownSkipRetryKeepsExplicitEmptyCommit() {
        assertGuidedCommitRetry(
            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
            phaseIndex = 0,
            phaseCount = 2,
            dropRows = emptyList(),
            restRows = emptyList(),
            dropWeightText = "",
            repsText = "5",
            currentSet = guidedRestPauseSet(),
            expected = guidedExpectedFeedback(),
            skipOnFirstAttempt = true,
            restRemainingSeconds = 17,
        )
    }

    private fun assertGuidedCommitRetry(
        kind: WorkoutTechniqueDraftKind,
        phaseIndex: Int,
        phaseCount: Int,
        dropRows: List<DropSetData>,
        restRows: List<RestPauseData>,
        dropWeightText: String,
        repsText: String,
        currentSet: ExerciseSet,
        expected: SetAdvancedFeedback,
        skipOnFirstAttempt: Boolean = false,
        expectedFormDropRows: List<DropSetData> = expected.dropSets,
        restRemainingSeconds: Int? = null,
    ) {
        val exercise = bilateralExercise()
        val holder = RecordActionHolder()
        val attempts = mutableListOf<SetAdvancedFeedback>()
        val outcomes = mutableListOf<(RecordSetResult) -> Unit>()
        var lastDraft: WorkoutSetDraft? = null
        val progress = WorkoutTechniqueProgressDraft(
            kind = kind,
            phaseIndex = phaseIndex,
            phaseCount = phaseCount,
            mainCapture = WorkoutTechniqueMainCaptureDraft(
                loadMode = LoadModeV2.LOAD,
                unitMode = UnitModeV2.REPS,
                weight = 20.0,
                value = 8.0,
                intensity = 8.0,
                amrapOverride = false,
            ),
            dropRows = dropRows,
            restPauseRows = restRows,
            dropWeightText = dropWeightText,
            dropRepsText = repsText,
            restPauseRepsText = repsText,
            restRemainingSeconds = restRemainingSeconds,
        )
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = exercise,
                    setIndex = 0,
                    currentSet = currentSet,
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = holder,
                    isActivePage = true,
                    initialDraft = WorkoutSetDraft(
                        weightText = "20",
                        valueText = "8",
                        intensityText = "8",
                        isDirty = true,
                        techniqueProgress = progress,
                    ),
                    onDraftChange = { draft, _ -> lastDraft = draft },
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, advanced, _, _, _, onResult ->
                        attempts += advanced
                        outcomes += onResult
                    },
                )
            }
        }
        composeRule.waitForIdle()
        if (skipOnFirstAttempt) {
            // Skipping discards the sub-sets, so the panel asks for an explicit
            // confirmation before the empty commit is submitted.
            composeRule.onNodeWithText("Saltar técnica y registrar solo la serie").performClick()
            composeRule.onNodeWithText("Sí, solo la serie").performClick()
        } else {
            composeRule.runOnIdle { holder.action?.invoke() }
        }
        composeRule.waitForIdle()
        assertEquals(1, attempts.size)
        assertEquals(expected, attempts.single())
        val pendingProgress = lastDraft?.techniqueProgress
        assertEquals(true, pendingProgress?.awaitingCommit)
        assertEquals(expectedFormDropRows, pendingProgress?.dropRows)
        assertEquals(expected.dropSets, pendingProgress?.commitDropRows)
        assertEquals(expected.restPauses, pendingProgress?.commitRestPauseRows)
        assertEquals(expected.actualIntensityMode, attempts.single().actualIntensityMode)
        assertEquals(expected.actualIntensityValue, attempts.single().actualIntensityValue)
        assertEquals(expected.amrapMinimumReps, attempts.single().amrapMinimumReps)
        assertEquals(expected.timerElapsedSeconds, attempts.single().timerElapsedSeconds)

        composeRule.runOnIdle {
            outcomes.single()(RecordSetResult.PersistenceFailed(IllegalStateException("Room final write failed")))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { holder.action?.invoke() }
        composeRule.waitForIdle()

        assertEquals(listOf(expected, expected), attempts)
        assertEquals(attempts.first(), attempts.last())
        assertEquals(expected.dropSets, attempts.last().dropSets)
        assertEquals(expected.restPauses, attempts.last().restPauses)
        assertEquals(expected.actualIntensityMode, attempts.last().actualIntensityMode)
        assertEquals(expected.actualIntensityValue, attempts.last().actualIntensityValue)
        assertEquals(expected.amrapMinimumReps, attempts.last().amrapMinimumReps)
        assertEquals(expected.timerElapsedSeconds, attempts.last().timerElapsedSeconds)
        val retryProgress = lastDraft?.techniqueProgress
        assertEquals(true, retryProgress?.awaitingCommit)
        assertEquals(expectedFormDropRows, retryProgress?.dropRows)
        assertEquals(expected.dropSets, retryProgress?.commitDropRows)
        assertEquals(expected.restPauses, retryProgress?.commitRestPauseRows)
        composeRule.runOnIdle { outcomes.last()(RecordSetResult.Created("bilateral-ex_0")) }
        composeRule.waitForIdle()
    }

    private fun recordOnce(exercise: Exercise): SetAdvancedFeedback {
        val holder = RecordActionHolder()
        var recorded: SetAdvancedFeedback? = null
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = exercise,
                    setIndex = 0,
                    currentSet = bilateralSet(),
                    ghostSet = null,
                    weightSuggestion = null,
                    initialBodyWeight = 80.0,
                    recordActionHolder = holder,
                    isActivePage = true,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    onRecordV2 = { _, _, _, _, _, advanced, _, _, _, onResult ->
                        recorded = advanced
                        onResult(RecordSetResult.Created("bilateral-ex_0"))
                    },
                )
            }
        }
        composeRule.runOnIdle { holder.action?.invoke() }
        composeRule.waitForIdle()
        return requireNotNull(recorded) { "The record action never reached onRecordV2" }
    }

    private fun guidedExpectedFeedback(
        dropSets: List<DropSetData> = emptyList(),
        restPauses: List<RestPauseData> = emptyList(),
    ) = SetAdvancedFeedback(
        dropSets = dropSets,
        restPauses = restPauses,
        actualIntensityMode = IntensityMode.RPE,
        actualIntensityValue = 8.0,
        amrapMinimumReps = 8,
        timerElapsedSeconds = 8,
    )

    private fun unilateralExercise() = Exercise(
        id = "uni-ex",
        name = "Split squat",
        isUnilateral = true,
        trainingMode = TrainingMode.REPS,
        sets = listOf(unilateralSet()),
    )

    private fun unilateralSet() = ExerciseSet(
        id = "uni-set",
        targetReps = 10,
        targetRPE = 8.0,
        weight = 20.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
        intensityMode = IntensityMode.RPE,
        leftTarget = UnilateralTarget(weight = 20.0, targetReps = 10, targetRPE = 8.0),
        rightTarget = UnilateralTarget(weight = 20.0, targetReps = 10, targetRPE = 8.0),
    )

    private fun bilateralExercise() = Exercise(
        id = "bilateral-ex",
        name = "Press banca",
        trainingMode = TrainingMode.REPS,
        sets = listOf(bilateralSet()),
    )

    private fun bilateralSet() = ExerciseSet(
        id = "bilateral-set",
        targetReps = 8,
        targetRPE = 8.0,
        weight = 80.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
        intensityMode = IntensityMode.RPE,
    )

    private fun guidedDropSet() = bilateralSet().copy(isDropSet = true)

    private fun guidedRestPauseSet() = bilateralSet().copy(isRestPause = true)

    private fun scheduledDropset() = ExerciseSet(
        id = "scheduled-drop-set",
        targetReps = 8,
        targetRPE = 8.0,
        weight = 80.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
        intensityMode = IntensityMode.RPE,
        isDropSet = true,
        plannedIntensityTechniques = listOf(
            com.example.kpkn.data.models.PlannedTechnique(
                id = "scheduled-drop",
                type = com.example.kpkn.data.models.TechniqueType.DROP_SET,
                params = mapOf(
                    "weightPcts" to "-5",
                    "count" to "1",
                    "betweenMarked" to "true",
                ),
            ),
        ),
    )
}
