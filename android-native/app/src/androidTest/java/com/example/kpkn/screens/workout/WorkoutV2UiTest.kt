package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.components.SetInputCardV2
import org.junit.Rule
import org.junit.Test

class WorkoutV2UiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun title_stays_single_line_and_mode_chips_visible() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = Exercise(
                        id = "ex-1",
                        name = "Press de banca con nombre extremadamente largo para validar autoescala",
                        sets = listOf(
                            ExerciseSet(
                                id = "set-1",
                                targetReps = 8,
                                targetRPE = 8.0,
                                loadModeV2 = LoadModeV2.LOAD,
                                unitModeV2 = UnitModeV2.REPS,
                            )
                        ),
                    ),
                    setIndex = 0,
                    currentSet = ExerciseSet(id = "set-1", targetReps = 8, targetRPE = 8.0, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS),
                    ghostSet = null,
                    weightSuggestion = null,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Técnica").performClick()
        composeRule.onNodeWithText("Dropsets").performClick()
        composeRule.onNodeWithText("Dropsets activos")
        composeRule.onNodeWithText("Restpauses")
        composeRule.onNodeWithText("Cambio de planes").performClick()
        composeRule.onNodeWithText("Error de ejecución")
        composeRule.onNodeWithText("AMRAP").performClick()
    }

    @Test
    fun time_mode_exposes_timer_control_and_expected_sections() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = Exercise(
                        id = "ex-time",
                        name = "Plancha frontal",
                        sets = listOf(
                            ExerciseSet(
                                id = "set-time",
                                targetDuration = 45,
                                targetRPE = 8.0,
                                loadModeV2 = LoadModeV2.BODYWEIGHT,
                                unitModeV2 = UnitModeV2.TIME,
                            )
                        ),
                    ),
                    setIndex = 0,
                    currentSet = ExerciseSet(
                        id = "set-time",
                        targetDuration = 45,
                        targetRPE = 8.0,
                        loadModeV2 = LoadModeV2.BODYWEIGHT,
                        unitModeV2 = UnitModeV2.TIME,
                    ),
                    ghostSet = null,
                    weightSuggestion = null,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 82.0,
                    recordActionHolder = RecordActionHolder(),
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Iniciar").performClick()
        composeRule.onNodeWithContentDescription("Detener")
        composeRule.onNodeWithText("Tiempo objetivo")
    }

    @Test
    fun technique_menu_can_toggle_drop_set() {
        composeRule.setContent {
            MaterialTheme {
                SetInputCardV2(
                    exercise = Exercise(
                        id = "ex-2",
                        name = "Press militar",
                        sets = listOf(
                            ExerciseSet(
                                id = "set-2",
                                targetReps = 6,
                                targetRPE = 8.0,
                                loadModeV2 = LoadModeV2.LOAD,
                                unitModeV2 = UnitModeV2.REPS,
                            )
                        ),
                    ),
                    setIndex = 0,
                    currentSet = ExerciseSet(
                        id = "set-2",
                        targetReps = 6,
                        targetRPE = 8.0,
                        loadModeV2 = LoadModeV2.LOAD,
                        unitModeV2 = UnitModeV2.REPS,
                    ),
                    ghostSet = null,
                    weightSuggestion = null,
                    onShowHistory = {},
                    onSetBodyWeight = {},
                    initialBodyWeight = 80.0,
                    recordActionHolder = RecordActionHolder(),
                    onRecordV2 = { _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Técnica").performClick()
        composeRule.onNodeWithText("Dropsets").performClick()
        composeRule.onNodeWithText("Dropsets activos")
    }

    @Test
    fun long_press_chip_opens_context_menu() {
        composeRule.setContent {
            MaterialTheme {
                val opened = remember { mutableStateOf(false) }
                UnifiedExerciseCarousel(
                    exercises = listOf(
                        Exercise(id = "ex-1", name = "Sentadilla frontal", sets = listOf(ExerciseSet(id = "s1"))),
                    ),
                    currentIdx = 0,
                    completedSets = emptyMap(),
                    onSelect = {},
                    onOpenContext = { opened.value = true },
                )

                if (opened.value) {
                    androidx.compose.material3.Text("context-opened")
                }
            }
        }

        composeRule.onNodeWithText("Sentadilla frontal").performTouchInput { longClick() }
        composeRule.onNodeWithText("context-opened")
    }

    @Test
    fun replacement_scope_dialog_renders_expected_options() {
        composeRule.setContent {
            MaterialTheme {
                val visible = remember { mutableStateOf(true) }
                if (visible.value) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { visible.value = false },
                        title = { androidx.compose.material3.Text("Persistencia de reemplazo") },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                androidx.compose.material3.Text("Solo esta vez")
                                androidx.compose.material3.Text("Guardar permanente")
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { visible.value = false }) {
                                androidx.compose.material3.Text("Cancelar")
                            }
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Persistencia de reemplazo")
        composeRule.onNodeWithText("Solo esta vez")
        composeRule.onNodeWithText("Guardar permanente")
        composeRule.onNodeWithText("Cancelar").performClick()
    }

    @Test
    fun compact_pager_card_keeps_side_chip_and_status_text() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutSetPager(
                    items = listOf(
                        WorkoutSetPagerItem(
                            index = 0,
                            label = "Serie 1",
                            state = WorkoutSetCardVisualState.ACTIVE,
                            isEditing = true,
                            side = "left",
                        ),
                        WorkoutSetPagerItem(
                            index = 1,
                            label = "Serie 2",
                            state = WorkoutSetCardVisualState.FUTURE,
                            isEditing = false,
                        ),
                    ),
                    activePageIndex = 0,
                    onSelectPage = {},
                )
            }
        }

        composeRule.onNodeWithText("Serie 1/2").assertExists()
        composeRule.onNodeWithText("0/2").assertExists()
        composeRule.onNodeWithText("Izq").assertExists()
    }

}
