package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutVoiceUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun confirmation_card_renders_summary_and_confirm_action() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = baseVoiceExercise(),
                    setIndex = 0,
                    currentSet = baseVoiceSet(),
                    initialDraft = WorkoutSetDraft(selectedSide = "right"),
                    voiceUiState = WorkoutVoiceUiState.Confirmation(
                        exerciseId = "voice-ex",
                        setIdx = 0,
                        side = "right",
                        interpretation = WorkoutVoiceInterpretation(
                            transcript = "80 por 8 derecha",
                            weightKg = 80.0,
                            metricValue = 8,
                            side = "right",
                            fields = setOf(
                                WorkoutVoiceField.WEIGHT,
                                WorkoutVoiceField.VALUE,
                                WorkoutVoiceField.SIDE,
                            ),
                        ),
                    ),
                    onVoiceStart = {},
                    onVoiceCancel = {},
                    onVoiceConfirm = {},
                    onDraftChange = { _, _ -> },
                    onTagSet = {},
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 80.0,
                    onSkipSet = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Confirmar voz").assertExists()
        composeRule.onNodeWithText("80 kg · 8 reps · Derecha").assertExists()
        composeRule.onNodeWithText("Carga").assertExists()
        composeRule.onNodeWithText("Reps/tiempo").assertExists()
        composeRule.onNodeWithText("Lado").assertExists()
        composeRule.onNodeWithText("Aplicar").assertExists()
    }

    @Test
    fun voice_applied_state_updates_draft_and_shows_understood_fields() {
        composeRule.setContent {
            MaterialTheme {
                val draftState = remember { mutableStateOf<WorkoutSetDraft?>(WorkoutSetDraft(selectedSide = "left")) }
                SetInputCardV2(
                    exercise = baseVoiceExercise(),
                    setIndex = 0,
                    currentSet = baseVoiceSet(),
                    initialDraft = draftState.value,
                    voiceUiState = WorkoutVoiceUiState.Applied(
                        exerciseId = "voice-ex",
                        setIdx = 0,
                        side = "left",
                        interpretation = WorkoutVoiceInterpretation(
                            transcript = "22 kilos 10 reps izquierda",
                            weightKg = 22.0,
                            metricValue = 10,
                            side = "left",
                            fields = setOf(
                                WorkoutVoiceField.WEIGHT,
                                WorkoutVoiceField.VALUE,
                                WorkoutVoiceField.SIDE,
                            ),
                        ),
                        message = "Voz aplicada: 22 kg · 10 reps · Izquierda",
                    ),
                    onVoiceStart = {},
                    onVoiceCancel = {},
                    onVoiceConfirm = {},
                    onDraftChange = { draft, _ -> draftState.value = draft },
                    onTagSet = {},
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 80.0,
                    onSkipSet = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Entendido por voz").assertExists()
        composeRule.onNodeWithText("Carga").assertExists()
        composeRule.onNodeWithText("Reps/tiempo").assertExists()
        composeRule.onNodeWithText("22").assertExists()
        composeRule.onNodeWithText("10").assertExists()
    }

    @Test
    fun listening_state_shows_examples_and_cancel_action() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = baseVoiceTimeExercise(),
                    setIndex = 0,
                    currentSet = baseVoiceTimeSet(),
                    voiceUiState = WorkoutVoiceUiState.Listening(
                        exerciseId = "voice-time",
                        setIdx = 0,
                        partialText = "45 segundos izquierda",
                        isReady = true,
                    ),
                    onVoiceStart = {},
                    onVoiceCancel = {},
                    onVoiceConfirm = {},
                    onDraftChange = { _, _ -> },
                    onTagSet = {},
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 80.0,
                    onSkipSet = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Escuchando...").assertExists()
        composeRule.onNodeWithText("45 segundos izquierda").assertExists()
        composeRule.onNodeWithText("Ejemplo: 45 segundos izquierda RPE 8").assertExists()
        composeRule.onNodeWithText("Solo se aplica al borrador cuando confirmas.").assertExists()
        composeRule.onNodeWithText("Cancelar").assertExists()
    }

    @Test
    fun mic_button_triggers_voice_start_callback() {
        composeRule.setContent {
            MaterialTheme {
                val opened = remember { mutableStateOf(false) }
                SetInputCardV2(
                    exercise = baseVoiceExercise(),
                    setIndex = 0,
                    currentSet = baseVoiceSet(),
                    initialDraft = WorkoutSetDraft(selectedSide = "right"),
                    onVoiceStart = { opened.value = true },
                    onVoiceCancel = {},
                    onVoiceConfirm = {},
                    onDraftChange = { _, _ -> },
                    onTagSet = {},
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 80.0,
                    onSkipSet = {},
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )

                if (opened.value) {
                    androidx.compose.material3.Text("voice-started")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Registrar por voz").performClick()
        composeRule.onNodeWithText("voice-started").assertExists()
    }

    private fun baseVoiceExercise() = Exercise(
        id = "voice-ex",
        name = "Curl mancuerna",
        isUnilateral = true,
        trainingMode = TrainingMode.REPS,
        sets = listOf(baseVoiceSet()),
    )

    private fun baseVoiceSet() = ExerciseSet(
        id = "voice-set",
        targetReps = 10,
        targetRPE = 8.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
        intensityMode = IntensityMode.RPE,
    )

    private fun baseVoiceTimeExercise() = Exercise(
        id = "voice-time",
        name = "Plancha lateral",
        isUnilateral = true,
        trainingMode = TrainingMode.TIME,
        sets = listOf(baseVoiceTimeSet()),
    )

    private fun baseVoiceTimeSet() = ExerciseSet(
        id = "voice-time-set",
        targetDuration = 45,
        targetRPE = 8.0,
        loadModeV2 = LoadModeV2.BODYWEIGHT,
        unitModeV2 = UnitModeV2.TIME,
        intensityMode = IntensityMode.RPE,
    )
}
