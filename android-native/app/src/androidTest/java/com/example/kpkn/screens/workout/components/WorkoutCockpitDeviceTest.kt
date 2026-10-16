package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** QA del cockpit en el dispositivo: gesto, navegación, métricas y prioridad de Atrás. */
@RunWith(AndroidJUnit4::class)
class WorkoutCockpitDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val lift = Exercise(id = "lv11-lift", name = "Levantamiento LV11", sets = List(3) { ExerciseSet(id = "s$it") })
    private val row = Exercise(id = "lv11-row", name = "Remo LV11", sets = listOf(ExerciseSet(id = "r")))
    private val sets = listOf(
        CompletedSet(id = "s0", weight = 100.0, reps = 5, rpe = 8.0),
        CompletedSet(id = "s1", weight = 80.0, reps = 10, rir = 1),
        CompletedSet(id = "s2", weight = 50.0, reps = 4),
        CompletedSet(id = "warmup", weight = 20.0, reps = 10, rpe = 3.0, isWarmup = true),
    )

    @Test
    fun metricsKeepRealTonnageAndOnlyAverageReportedEffort() {
        val metrics = cockpitSessionMetrics(
            listOf(lift), sets.take(3).mapIndexed { index, set -> "${lift.id}_$index" to set }.toMap(),
            listOf(CompletedExercise(lift.id, lift.name, sets = sets)), emptySet(), SessionEnergySummary(), 80.0,
        )
        assertEquals(3, metrics.completed)
        assertEquals(3, metrics.total)
        assertEquals(1500.0, metrics.tonnage, 0.0001)
        assertEquals(1.5, metrics.averageRir!!, 0.0001)
        assertNull("sin estimación real no se inventan kcal", metrics.calories.kcal)
    }

    @Test
    fun swipingTheSameBarOpensAndClosesWithoutEditingChrome() {
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    var mode by remember { mutableStateOf(RoadmapMode.COMPACT) }
                    Column {
                        Text(if (mode == RoadmapMode.EXPANDED) "Abierto" else "Plegado")
                        WorkoutRoadmapBar(
                            exercises = listOf(lift, row), currentIdx = 0, completedSets = emptyMap(), onSelect = {},
                            mode = mode, onModeChange = { mode = it },
                            godMode = true, // Compatibilidad: este parámetro antiguo ya no activa nada.
                            modifier = Modifier.testTag("lv11-bar"),
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("lv11-bar").performTouchInput { swipeUp() }
        rule.onNodeWithText("Abierto").assertExists()
        rule.onAllNodesWithContentDescription("Eliminar ejercicio").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Agregar ejercicio").assertCountEquals(0)
        rule.onNodeWithTag("lv11-bar").performTouchInput { swipeDown() }
        rule.onNodeWithText("Plegado").assertExists()
    }

    @Test
    fun sessionKeepsAllPagesAndItsRouteSelectsTheRealExercise() {
        var selected: String? = null
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSessionCockpit(
                            exercises = listOf(lift, row), completedSets = emptyMap(), milestones = emptyList(),
                            sessionProgressLabel = "0 / 4", liveEnergySummary = SessionEnergySummary(), sessionNotes = "",
                            sessionPhotos = emptyList(), sessionChecklist = emptyList(), onSessionNotesChange = {},
                            onAddSessionPhoto = {}, onRemoveSessionPhoto = {}, onAddChecklistItem = {},
                            onToggleChecklistItem = {}, onRemoveChecklistItem = {}, sessionAccentColor = androidx.compose.ui.graphics.Color(0xFFF49A6E),
                            currentExerciseId = lift.id, onSelectExercise = { selected = it },
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Tonelaje").assertExists()
        rule.onNodeWithText("RIR medio").assertExists()
        rule.onNodeWithText("Hitos").assertExists()
        rule.onNodeWithText("Fotos").assertExists()
        rule.onNodeWithText("Herramientas").assertExists()
        rule.onNodeWithText("Notas").assertExists()
        rule.onAllNodesWithText("RINGS EN VIVO").assertCountEquals(0)
        rule.onNodeWithText("Remo LV11").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(row.id, selected) }
    }

    @Test
    fun anEmptySessionStillHasAVisibleAddAction() {
        var adds = 0
        rule.setContent {
            KPKNTheme { MaterialTheme {
                WorkoutRoadmapBar(exercises = emptyList(), currentIdx = 0, completedSets = emptyMap(), onSelect = {}, onAddExercise = { adds++ })
            } }
        }
        rule.onNodeWithText("Añadir ejercicio").performClick()
        rule.runOnIdle { assertEquals(1, adds) }
    }

    @Test
    fun backCollapsesTheCockpitAfterHigherPriorityOverlays() {
        assertEquals(WorkoutBackAction.COLLAPSE_COCKPIT, resolveWorkoutBackAction(WorkoutOverlayFlags(cockpitExpanded = true)))
        assertEquals(WorkoutBackAction.MINIMIZE_REST, resolveWorkoutBackAction(WorkoutOverlayFlags(cockpitExpanded = true, isRestOverlayVisible = true)))
        assertEquals(WorkoutBackAction.DISMISS_FINISH_SHEET, resolveWorkoutBackAction(WorkoutOverlayFlags(cockpitExpanded = true, showFinishSheet = true)))
    }
}
