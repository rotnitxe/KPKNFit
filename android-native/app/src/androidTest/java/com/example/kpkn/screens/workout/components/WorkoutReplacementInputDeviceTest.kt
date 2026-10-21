package com.example.kpkn.screens.workout.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/** Real card retained in composition across replacements; catalog navigation is covered separately. */
@RunWith(AndroidJUnit4::class)
class WorkoutReplacementInputDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val holder = RecordActionHolder()
    private var exercise by mutableStateOf(Exercise("slot-press", "Press militar", canonicalExerciseId = "overhead_press",
        catalogRevision = "rev-a", catalogDefinitionId = "overhead_press", catalogConfigurationId = "overhead_press__barbell",
        restTime = 120, sets = List(4) { index -> ExerciseSet("planned-$index", targetReps = 6, targetRepsRange = RepRange(4, 6),
            targetRIR = 2, restAfterSeconds = 150, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS) }))
    private var draft by mutableStateOf<WorkoutSetDraft?>(null)
    private var recordCalls = 0

    private fun show() {
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.width(272.dp)) {
                        val source = exercise
                        WorkoutSetInputOwner(source, source.sets.first().id) {
                            SetInputCardV2(exercise = source, setIndex = 0, currentSet = source.sets.first(), ghostSet = null,
                                recordActionHolder = holder, recordActionPageKey = "replacement-card", initialBodyWeight = 80.0,
                                initialDraft = draft,
                                onDraftChange = { value, _ ->
                                    if (workoutSetDraftSourceIsCurrent(exercise, source, 0)) draft = value.takeIf { it.isDirty }
                                }, onShowHistory = {}, onSetBodyWeight = {}, effortFireSecondsOverride = 1.7f,
                                onRecordV2 = { _, _, _, _, _, _, _, _, _, done ->
                                    recordCalls++; done(RecordSetResult.Created("slot-press_0"))
                                })
                        }
                    }
                }
            }
        }
    }
    private fun chooseBodyweight() {
        rule.onNodeWithContentDescription("Cambiar tipo de carga").performClick()
        rule.onNodeWithText("Peso corporal", useUnmergedTree = true).performClick()
        rule.onNodeWithText("PESO CORPORAL", useUnmergedTree = true).assertIsDisplayed()
    }
    private fun replace(id: String, name: String, equipment: String) {
        rule.runOnIdle {
            exercise = replacementExerciseFor(exercise, ExerciseMuscleInfo(id = id, name = name, equipment = equipment,
                catalogRevision = "rev-$id", catalogDefinitionId = id, catalogConfigurationId = id))
            // The structural controller already clears the outgoing exercise's persisted draft.
            draft = null
        }
    }
    private fun assertFreshExternalInput() {
        rule.onNodeWithText("CARGA (kg)", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("PESO CORPORAL", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithText("Sin marcar", useUnmergedTree = true).assertExists()
        rule.runOnIdle {
            assertEquals(4, exercise.sets.size)
            assertEquals(120, exercise.restTime)
            assertEquals(List(4) { "planned-$it" }, exercise.sets.map { it.id })
            exercise.sets.forEach { set ->
                assertEquals(RepRange(4, 6), set.targetRepsRange)
                assertEquals(2, set.targetRIR)
                assertEquals(150, set.restAfterSeconds)
                assertEquals(LoadModeV2.LOAD, set.loadModeV2)
                assertNull(set.weight)
            }
            assertFalse(draft?.loadMode == LoadModeV2.BODYWEIGHT)
            assertTrue(draft?.intensityText.isNullOrBlank())
            assertEquals(0, recordCalls)
        }
    }

    @Test fun changingModeThenReplacingTwiceDoesNotRetainTheOutgoingCardDraft() {
        show()
        chooseBodyweight()
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.onNodeWithContentDescription("Volver a la serie").performClick()
        rule.runOnIdle { assertEquals(LoadModeV2.BODYWEIGHT, draft?.loadMode) }
        replace("arnold_press__dumbbell", "Press Arnold", "Mancuerna")
        assertFreshExternalInput()
        chooseBodyweight()
        rule.runOnIdle { assertEquals(LoadModeV2.BODYWEIGHT, draft?.loadMode) }
        replace("seated_press__barbell", "Press de hombros sentado", "Barra")
        assertFreshExternalInput()
    }

    @Test fun editingTheSameExercisePlanKeepsTheAthletesCurrentInput() {
        show()
        chooseBodyweight()
        rule.runOnIdle {
            exercise = exercise.copy(reference1RM = 100.0, restTime = 180,
                sets = exercise.sets.map { it.copy(targetReps = 8, targetRIR = 1) })
        }
        rule.onNodeWithText("PESO CORPORAL", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(LoadModeV2.BODYWEIGHT, draft?.loadMode); assertEquals(0, recordCalls) }
    }
}
