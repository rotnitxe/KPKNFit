package com.example.kpkn.screens.sessioneditor

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** An edit may arrive after navigation was requested, while the destination is still loading. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorNavigationRaceAuditTest {
    @get:Rule val room = RoomTestRule()
    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()

    @After fun close() {
        store.clear()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private suspend fun loadedEditor(): SessionEditorViewModel {
        Dispatchers.setMain(main)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ProgramRepository.initForTests(app)
        withTimeout(10_000) { while (!repo.isReady.value) { main.scheduler.runCurrent(); delay(10) } }
        val programId = "navigation-race-${UUID.randomUUID()}"
        fun session(id: String, day: Int) = Session(id = id, name = id, dayOfWeek = day, exercises = listOf(
            Exercise(id = "exercise-$id", name = "Ejercicio", exerciseDbId = "custom:$id",
                sets = listOf(ExerciseSet(id = "set-$id", targetReps = 8))),
        ))
        val week = ProgramWeek(id = "week", name = "Semana", sessions = listOf(session("origin", 1), session("target", 2)))
        val meso = Mesocycle(id = "meso", name = "Meso", weeks = listOf(week))
        val block = Block(id = "block", name = "Bloque", mesocycles = listOf(meso))
        repo.addProgram(Program(id = programId, name = "Programa", structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(Macrocycle(id = "macro", name = "Macro", blocks = listOf(block)))))
        val vm = SessionEditorViewModel(app, programId, "origin", "week", 0, 0, 1)
        store.put("vm", vm)
        withTimeout(10_000) { while (vm.currentUiState.session == null) { main.scheduler.runCurrent(); delay(10) } }
        return vm
    }

    private suspend fun awaitNavigationDecision(vm: SessionEditorViewModel) {
        withTimeout(10_000) {
            while (vm.currentUiState.session?.id != "target" && vm.currentUiState.snackbarMessage?.contains("cambió") != true) {
                main.scheduler.runCurrent()
                delay(10)
            }
        }
    }

    @Test fun anEditDuringDestinationLoadingCannotBeOverwrittenByNavigation() = runBlocking {
        val vm = loadedEditor()
        vm.requestSessionSwitch("target")
        vm.updateSessionName("Edición durante el cambio")
        awaitNavigationDecision(vm)

        assertEquals("origin", vm.currentUiState.session?.id)
        assertEquals("Edición durante el cambio", vm.currentUiState.session?.name)
        assertTrue(vm.currentUiState.hasUnsavedChanges)
    }

    @Test fun aDeletionDuringDestinationLoadingKeepsItsUndoInTheOriginalSession() = runBlocking {
        val vm = loadedEditor()
        vm.requestSessionSwitch("target")
        vm.requestExerciseRemoval(null, "exercise-origin")
        awaitNavigationDecision(vm)
        assertEquals("origin", vm.currentUiState.session?.id)
        assertEquals("exercise-origin", vm.currentUiState.pendingExerciseRemoval?.exerciseId)
        vm.undoExerciseRemoval()
        assertNull(vm.currentUiState.pendingExerciseRemoval)
    }
}
