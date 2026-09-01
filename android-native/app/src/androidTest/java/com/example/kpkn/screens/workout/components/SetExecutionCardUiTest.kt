package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.SetAdvancedFeedback
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.screens.workout.WorkoutSetDraft
import com.example.kpkn.services.workout.VoiceSessionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetExecutionCardUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unilateralLockedSideStepperKeepsStableDraftValue() {
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
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Aumentar")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Aumentar")[0].performClick()
        composeRule.onAllNodesWithContentDescription("Disminuir")[0].performClick()
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
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Carga sugerida").assertDoesNotExist()
        composeRule.onNodeWithText("Sugerida").assertExists()
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
                    onRecordV2 = { _: LoadModeV2, _: UnitModeV2, _: Double, _: Double, _: Double?, _: SetAdvancedFeedback, _: Boolean, _: Double?, side: String? ->
                        recordedSide = side
                    },
                )
            }
        }

        composeRule.runOnIdle { holder.action?.invoke() }
        composeRule.runOnIdle { assertEquals("right", recordedSide) }
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
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Opciones avanzadas").assertExists()
        composeRule.onNodeWithText("Ver ejercicio/Fotos").assertExists()
        composeRule.onNodeWithText("Reportar serie").assertDoesNotExist()
        composeRule.onNodeWithText("¿Cambio de planes o añadir técnica de intensidad?").assertDoesNotExist()
    }

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
}
