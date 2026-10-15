package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deshacer el borrado de un ejercicio desde la lista (ER1b): el ejercicio se queda en su sitio hasta que el borrado se confirma. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorUndoViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository
    private val programId = "prog_undo_test"
    private val sessionId = "session_undo_test"

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
        withTimeout(10_000) {
            while (!repository.isReady.value) {
                delay(25)
            }
        }
        val session = Session(
            id = sessionId,
            name = "Tirón",
            exercises = listOf(
                Exercise(id = "e1", name = "Jalón", exerciseDbId = "lat_pulldown"),
                Exercise(id = "e2", name = "Remo", exerciseDbId = "row"),
            ),
            parts = emptyList(),
        )
        repository.addProgram(
            Program(
                id = programId,
                name = "Test Program",
                structure = ProgramStructure.SIMPLE,
                macrocycles = listOf(
                    Macrocycle(
                        id = "macro",
                        name = "Macro",
                        blocks = listOf(
                            Block(
                                id = "block",
                                name = "Block",
                                mesocycles = listOf(
                                    Mesocycle(
                                        id = "meso",
                                        name = "Meso",
                                        weeks = listOf(ProgramWeek(id = "week", name = "Semana", sessions = listOf(session))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private fun loadedViewModel(): SessionEditorViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )
        runBlocking { vm.awaitSessionLoaded() }
        return vm
    }

    private fun ids(vm: SessionEditorViewModel): List<String> =
        vm.uiState.value.session!!.exercises.map { it.id }

    @Test
    fun pedirElBorradoNoQuitaElEjercicioHastaConfirmarlo() {
        val vm = loadedViewModel()

        vm.requestExerciseRemoval(null, "e1")

        assertEquals(listOf("e1", "e2"), ids(vm))
        assertEquals("e1", vm.uiState.value.pendingExerciseRemoval?.exerciseId)
    }

    @Test
    fun deshacerDejaElEjercicioEnSuSitio() {
        val vm = loadedViewModel()

        vm.requestExerciseRemoval(null, "e1")
        vm.undoExerciseRemoval()

        assertEquals(listOf("e1", "e2"), ids(vm))
        assertNull(vm.uiState.value.pendingExerciseRemoval)
    }

    @Test
    fun confirmarQuitaElEjercicioPendiente() {
        val vm = loadedViewModel()

        vm.requestExerciseRemoval(null, "e1")
        vm.commitPendingExerciseRemoval()

        assertEquals(listOf("e2"), ids(vm))
        assertNull(vm.uiState.value.pendingExerciseRemoval)
    }

    @Test
    fun unNuevoBorradoConfirmaElAnteriorYDejaSoloUnAviso() {
        val vm = loadedViewModel()

        vm.requestExerciseRemoval(null, "e1")
        vm.requestExerciseRemoval(null, "e2")

        assertEquals(listOf("e2"), ids(vm))
        assertNotNull(vm.uiState.value.pendingExerciseRemoval)
        assertEquals("e2", vm.uiState.value.pendingExerciseRemoval?.exerciseId)
    }
}
