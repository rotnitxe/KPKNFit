package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp-xxhdpi", application = Application::class)
class WorkoutEditSeriesAuditTest {
    @get:Rule val rule = createComposeRule()
    private var saved: Exercise? = null
    private lateinit var sheetState: WorkoutStructureSheetsState
    private var session by mutableStateOf(Session(id = "session", name = "Sesión", exercises = listOf(
        Exercise(id = "exercise", name = "Prueba", sets = listOf(ExerciseSet(id = "set", targetReps = 8))),
    )))

    private fun mount(): StateRestorationTester {
        val restoration = StateRestorationTester(rule)
        val overlays = KpknOverlayHostController(tourSeen = object : TourSeenStore {
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        })
        restoration.setContent {
            val state = rememberWorkoutStructureSheetsState().also { sheetState = it }
            CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                WorkoutEditSeriesSheet(state, session, Color.Cyan, remember { HazeState() }) { _, draft -> saved = draft }
                KpknOverlayHostContent(overlays)
            }
        }
        rule.runOnIdle { sheetState.editSheetExerciseId = "exercise" }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Editar series").fetchSemanticsNodes().isNotEmpty() }
        return restoration
    }

    private fun changeToTime() {
        tap("Reps")
        tap("Tiempo")
        rule.waitForIdle()
    }

    private fun tap(text: String) {
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun confirm() {
        tap("Confirmar cambios")
        rule.waitForIdle()
    }

    @Test fun aSourceRefreshDoesNotEraseTheLocalDraft() {
        mount()
        changeToTime()
        rule.runOnIdle {
            session = session.copy(exercises = session.exercises.map { it.copy(restTime = 125) })
        }
        confirm()
        assertEquals(TrainingMode.TIME, saved!!.trainingMode)
    }

    @Test fun aPristineDraftStillReceivesSourceUpdates() {
        mount()
        rule.runOnIdle {
            session = session.copy(exercises = session.exercises.map { it.copy(restTime = 125) })
        }
        confirm()
        assertEquals(125, saved!!.restTime)
    }

    @Test fun recreatingTheActivityDoesNotEraseUnconfirmedEdits() {
        val restoration = mount()
        changeToTime()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        confirm()
        assertEquals(TrainingMode.TIME, saved!!.trainingMode)
    }

    @Test fun addingADistanceSetPreservesItsPlannedValue() {
        session = session.copy(exercises = listOf(session.exercises.single().copy(
            trainingMode = TrainingMode.DISTANCE,
            sets = listOf(ExerciseSet(id = "set", plannedTargetV2 = 400.0, targetRIR = 2)),
        )))
        mount()
        // G1: «+ Añadir serie» pasó a ser la ficha «Serie» de la lista compacta (descripción «Añadir serie»).
        rule.onNodeWithContentDescription("Añadir serie").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        confirm()
        assertEquals(2, saved!!.sets.size)
        assertEquals(400.0, saved!!.sets.last().plannedTargetV2!!, 0.0)
    }
}
