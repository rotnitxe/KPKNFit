package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.screens.workout.components.LocalRestClockFrozen
import com.example.kpkn.screens.workout.components.RestFeedbackCard
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** QA en el dispositivo: guardar técnica y conservar el borrador al expandir la tarjeta del descanso. */
@RunWith(AndroidJUnit4::class)
class WorkoutRestFeedbackDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val squat = Exercise(id = "sq", name = "Sentadilla trasera")

    @Test
    fun techniqueIsSavedWithoutASecondIntensityQuestion() {
        var saved: List<PostExerciseFeedback> = emptyList()
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalFeedbackBounded provides true, LocalRestClockFrozen provides 1.7f) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            PostExerciseFeedbackPanel(
                                feedbackExercises = listOf(squat),
                                postExerciseFeedbackByExerciseId = emptyMap(),
                                sessionAccentColor = Color(0xFFF49A6E),
                                canonicalKeyOf = { it.id },
                                onSave = { saved = it },
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Calidad técnica")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        rule.onAllNodesWithText("Qué tan intenso fue", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Guardar").performClick()
        rule.runOnIdle {
            assertEquals(5, saved.single().technicalQuality)
            assertEquals(listOf("none"), saved.single().discomfortIds)
            assertNull(saved.single().perceivedIntensityRpe)
        }
    }

    @Test
    fun expandingTheCardKeepsTechniqueAndTheDiscomfortSheetReportsToTheSameDraft() {
        var saved: List<PostExerciseFeedback> = emptyList()
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    val draft = remember { PostExerciseFeedbackDraft.from(listOf(squat), emptyMap()) }
                    var compact by remember { mutableStateOf(true) }
                    CompositionLocalProvider(LocalFeedbackBounded provides true, LocalRestClockFrozen provides 1.7f) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            if (compact) {
                                RestFeedbackCard(
                                    remainingSeconds = 158,
                                    totalSeconds = 249,
                                    shape = RestClockShape.TOWER,
                                    feedbackExercises = listOf(squat),
                                    draft = draft,
                                    insights = FeedbackInsights(likely = listOf("knee_patellar")),
                                    explainDiscomfort = { null },
                                    linked = emptyList(),
                                    onSave = feedbackSave(listOf(squat), draft, emptyMap(), emptyList(), { it.id }) { saved = it },
                                    onDefer = {},
                                    onExpand = { compact = false },
                                    clockActive = false,
                                )
                            } else {
                                PostExerciseFeedbackPanel(
                                    feedbackExercises = listOf(squat),
                                    postExerciseFeedbackByExerciseId = emptyMap(),
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    canonicalKeyOf = { it.id },
                                    onSave = { saved = it },
                                    draft = draft,
                                    insights = FeedbackInsights(likely = listOf("knee_patellar")),
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Calidad técnica")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        rule.onNodeWithContentDescription("Expandir descanso").performClick()
        rule.onNodeWithText("/ 10 · irregular").assertExists()
        rule.onNodeWithText("Rodilla anterior").performClick()
        rule.onNodeWithText("Reportar molestia").performClick()
        rule.onNodeWithText("Guardar").performClick()
        rule.runOnIdle {
            assertEquals(4, saved.single().technicalQuality)
            assertEquals(listOf("knee_patellar"), saved.single().discomfortIds)
        }
    }
}
