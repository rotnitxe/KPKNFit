package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.ExerciseMuscleInfo
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El guardado automático contra el programa real (ER1a §5): un cambio llega a Room tras los 1,2 s de calma, salir lo guarda sin
 * esperar, y un bloqueo deja el aviso y lo escrito en pantalla. El temporizador corre con el reloj virtual del dispatcher de prueba,
 * así que se adelanta a mano.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorAutoSaveTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository
    private val programId = "program-autosave"
    private val sessionId = "session-autosave"

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
        repository.addProgram(seedProgram())
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    /** El programa de prueba: una semana con una sesión llamada «Día». */
    private fun seedProgram(): Program = Program(
        id = programId,
        name = "Programa",
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
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week",
                                        name = "Semana",
                                        sessions = listOf(Session(id = sessionId, name = "Día")),
                                    )
                                ),
                            )
                        ),
                    )
                ),
            )
        ),
    )

    /** Abre el editor de la sesión, con un ejercicio para que el guardado tenga contenido válido. */
    private suspend fun openEditorWithExercise(): SessionEditorViewModel {
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
        vm.awaitSessionLoaded()
        vm.addExerciseToPart(
            partId = null,
            info = ExerciseMuscleInfo(
                id = "sentadilla_barra",
                name = "Sentadilla con barra",
                category = "Fuerza",
                equipment = "Barra",
            ),
        )
        return vm
    }

    /** El nombre de la sesión tal como está escrito en el programa de Room. */
    private fun savedName(): String? = repository.getProgramById(programId)
        ?.macrocycles?.flatMap { it.blocks }
        ?.flatMap { it.mesocycles }
        ?.flatMap { it.weeks }
        ?.flatMap { it.sessions }
        ?.firstOrNull { it.id == sessionId }
        ?.name

    @Test
    fun aChangeReachesTheProgramOnlyAfterTheQuietPeriod() = runBlocking {
        val vm = openEditorWithExercise()
        vm.updateSessionName("Pierna pesada")
        vm.scheduleProgramAutoSave()

        dispatcher.scheduler.advanceTimeBy(PROGRAM_AUTO_SAVE_QUIET_MS - 1)
        dispatcher.scheduler.runCurrent()
        assertEquals("Día", savedName())

        dispatcher.scheduler.advanceTimeBy(2)
        dispatcher.scheduler.runCurrent()
        withTimeout(5_000) {
            while (savedName() != "Pierna pesada") {
                delay(25)
            }
        }
        assertNull(vm.autoSaveBlock.value)
    }

    @Test
    fun leavingTheEditorSavesThePendingChangeWithoutWaiting() = runBlocking {
        val vm = openEditorWithExercise()
        vm.updateSessionName("Salida")
        vm.scheduleProgramAutoSave()

        assertTrue(vm.flushProgramAutoSaveAndAwait())
        assertEquals("Salida", savedName())
        assertNull(vm.autoSaveBlock.value)
    }

    @Test
    fun aBlockedSaveKeepsTheTextAndRetriesOnTheNextPass() = runBlocking {
        val vm = openEditorWithExercise()
        vm.updateSessionName("Lo escrito")
        vm.scheduleProgramAutoSave()

        // Sin programa, el guardado no puede escribir: es un bloqueo como el de la validación. Lo escrito se queda en pantalla.
        repository.clearPrograms()
        assertFalse(vm.flushProgramAutoSaveAndAwait())
        assertNotNull(vm.autoSaveBlock.value)
        assertEquals("Lo escrito", vm.uiState.value.session?.name)

        // El siguiente intento, con el programa de vuelta, guarda lo escrito y quita el aviso.
        repository.addProgram(seedProgram())
        assertTrue(vm.flushProgramAutoSaveAndAwait())
        assertNull(vm.autoSaveBlock.value)
        assertEquals("Lo escrito", savedName())
    }
}
