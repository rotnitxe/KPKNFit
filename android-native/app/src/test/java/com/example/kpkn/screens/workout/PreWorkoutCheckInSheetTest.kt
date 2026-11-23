package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h740dp-xxhdpi")
class PreWorkoutCheckInSheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun domsSectionIsAlwaysVisibleAndResolvesCatalogMuscles() {
        val session = Session(
            id = "s1",
            name = "Empuje",
            exercises = listOf(
                Exercise(
                    id = "ex1",
                    name = "Press banca",
                    catalogDefinitionId = "bench_press",
                    catalogConfigurationId = "bench_press__barbell",
                    sets = listOf(ExerciseSet(id = "set1", weight = 80.0, targetReps = 8)),
                ),
            ),
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    PreWorkoutCheckInSheet(
                        session = session,
                        weekLabel = null,
                        initial = null,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
        }
        rule.onNodeWithText("AGUJETAS (DOMS)").assertExists()
        rule.onNodeWithText("+ Otro músculo").assertExists()
        rule.onNodeWithTag("readiness-scale-energy").assertIsDisplayed()
        rule.onNodeWithTag("readiness-scale-sleep").assertIsDisplayed()
        rule.onNodeWithTag("readiness-scale-stress").assertIsDisplayed()
        val resolved = checkInSessionMuscles(
            session = session,
            catalogIndex = mapOf(
                "bench_press__barbell" to ExerciseMuscleInfo(
                    id = "bench_press__barbell",
                    name = "Press banca",
                    involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
                ),
            ),
        )
        assertTrue("Expected resolved muscles for bench_press", resolved == listOf("Pectorales"))
    }

    @Test
    fun addAnotherMusclePickerShowsCanonicalGroups() {
        val session = Session(id = "s_empty", name = "Vacía")
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    PreWorkoutCheckInSheet(
                        session = session,
                        weekLabel = null,
                        initial = null,
                        onDismiss = {},
                        onConfirm = {},
                    )
                }
            }
        }
        rule.onNodeWithText("+ Otro músculo").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithTag("readiness-doms-search").assertExists()
        rule.onNodeWithText("+ Cuádriceps").assertExists()
    }

    @Test
    fun continueWithoutAnswersDoesNotInventAnObservation() {
        var dismissed = false
        var stored: com.example.kpkn.data.models.DailyWellbeingLog? = null
        val session = Session(
            id = "s-empty",
            name = "Pierna A",
            exercises = listOf(
                Exercise(
                    "e",
                    "Sentadilla",
                    effectiveMuscles = listOf("Cuádriceps", "Glúteos").map { InvolvedMuscle(it) },
                ),
            ),
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    PreWorkoutCheckInSheet(
                        session = session,
                        weekLabel = null,
                        initial = null,
                        onDismiss = { dismissed = true },
                        onConfirm = { stored = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("readiness-confirm").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertTrue(dismissed)
            org.junit.Assert.assertNull(stored)
        }
    }

    @Test
    fun onlyExplicitlyAnsweredFieldsAreCaptured() {
        var stored: com.example.kpkn.data.models.DailyWellbeingLog? = null
        val session = Session(
            id = "s-energy",
            name = "Pierna A",
            exercises = listOf(
                Exercise(
                    "e",
                    "Sentadilla",
                    effectiveMuscles = listOf("Cuádriceps").map { InvolvedMuscle(it) },
                ),
            ),
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    PreWorkoutCheckInSheet(
                        session = session,
                        weekLabel = null,
                        initial = null,
                        onDismiss = {},
                        onConfirm = { stored = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("readiness-scale-energy").performSemanticsAction(SemanticsActions.SetProgress) { action ->
            assertTrue(action(10f))
        }
        rule.onNodeWithTag("readiness-confirm").performClick()
        rule.runOnIdle {
            val saved = requireNotNull(stored)
            org.junit.Assert.assertEquals(10, saved.energy)
            org.junit.Assert.assertEquals(setOf("energy"), saved.capturedFields)
            assertTrue(saved.muscleDoms.isEmpty())
        }
    }

    @Test
    fun clearingInitialDomsPreservesMuscleDomsInCapturedFieldsAndClearsMap() {
        var stored: com.example.kpkn.data.models.DailyWellbeingLog? = null
        val session = Session(
            id = "s-doms-clear",
            name = "Empuje",
            exercises = listOf(
                Exercise("e1", "Press banca", effectiveMuscles = listOf(InvolvedMuscle("Pectorales"))),
            ),
        )
        val initial = com.example.kpkn.data.models.DailyWellbeingLog(
            id = "w-1",
            date = "2026-11-21",
            muscleDoms = mapOf("Pectorales" to 3),
            doms = 3,
            capturedFields = setOf("muscleDoms"),
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    PreWorkoutCheckInSheet(
                        session = session,
                        weekLabel = null,
                        initial = initial,
                        onDismiss = {},
                        onConfirm = { stored = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("readiness-confirm").assertExists()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Quitar agujetas de Pectorales"))
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithTag("readiness-confirm").performClick()
        rule.runOnIdle {
            val saved = requireNotNull(stored)
            assertTrue(saved.muscleDoms.isEmpty())
            assertTrue("muscleDoms" in saved.capturedFields)
        }
    }

    @Test
    fun reSavingExistingCheckInPreservesInitialSleepInCapturedFields() {
        var stored: com.example.kpkn.data.models.DailyWellbeingLog? = null
        val session = Session(
            id = "s-resave",
            name = "Empuje",
            exercises = listOf(
                Exercise("e1", "Press banca", effectiveMuscles = listOf(InvolvedMuscle("Pectorales"))),
            ),
        )
        val initial = com.example.kpkn.data.models.DailyWellbeingLog(
            id = "w-2",
            date = "2026-11-21",
            energy = 7,
            sleepQuality = 2,
            capturedFields = setOf("energy", "sleep"),
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    PreWorkoutCheckInSheet(
                        session = session,
                        weekLabel = null,
                        initial = initial,
                        onDismiss = {},
                        onConfirm = { stored = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("readiness-scale-energy").performSemanticsAction(SemanticsActions.SetProgress) { action ->
            assertTrue(action(9f))
        }
        rule.onNodeWithTag("readiness-confirm").performClick()
        rule.runOnIdle {
            val saved = requireNotNull(stored)
            org.junit.Assert.assertEquals(9, saved.energy)
            org.junit.Assert.assertEquals(2, saved.sleepQuality)
            org.junit.Assert.assertEquals(setOf("energy", "sleep"), saved.capturedFields)
        }
    }
}
