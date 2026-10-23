package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D9: el recorrido del ViewModel real se conserva tras cerrar y reabrir Room en un archivo de prueba. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionMobilityPersistenceTest {
    @get:Rule val room = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var repository: ProgramRepository
    private var editor: SessionEditorViewModel? = null
    private val databaseName = "screens-v2-edc-mobility-test.db"

    @Before fun prepare() = runBlocking {
        Dispatchers.setMain(dispatcher)
        context.deleteDatabase(databaseName)
        repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
        awaitReady()
    }
    @After fun close() {
        editor?.viewModelScope?.cancel()
        ProgramRepository.closeInstance()
        context.deleteDatabase(databaseName)
        Dispatchers.resetMain()
    }
    private suspend fun awaitReady() = withTimeout(10_000) {
        while (!repository.isReady.value) delay(25)
    }
    private fun program(session: Session) = Program(id = "mobility-program", name = "Preparación", structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(Macrocycle(id = "macro", name = "Macro", blocks = listOf(Block(id = "block", name = "Bloque",
            mesocycles = listOf(Mesocycle(id = "meso", name = "Meso", weeks = listOf(ProgramWeek(id = "week", name = "Semana", sessions = listOf(session))))))))))
    private fun Session.part(id: String) = parts.single { it.id == id }

    @Test fun editarDosisOrdenYDestinoSobreviveAReabrirLaBase() = runBlocking {
        val session = Session(id = "day", name = "Día", exercises = listOf(
            Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet(id = "work", targetReps = 8)))),
            parts = listOf(SessionPart(id = "mob-a", name = "Preparación A", isMobilityGroup = true),
                SessionPart(id = "mob-b", name = "Preparación B", isMobilityGroup = true)))
        repository.addProgramNow(program(session)).getOrThrow()
        val vm = SessionEditorViewModel(ApplicationProvider.getApplicationContext(), programId = "mobility-program", sessionId = "day",
            draftWeekId = "week", draftMacroIndex = 0, draftMesoIndex = 0, draftDayOfWeek = 1).also { editor = it }
        withTimeout(10_000) {
            while (vm.uiState.value.session?.id != session.id) delay(25)
        }
        val movements = MobilityExerciseCatalog.getAllMobilityExercises().take(2)
        assertEquals(2, movements.size)
        vm.openPartMobilityPicker("mob-a")
        vm.addMobilityToTarget(movements[0])
        vm.addMobilityToTarget(movements[0])
        vm.addMobilityToTarget(movements[1])
        var a = vm.uiState.value.session!!.part("mob-a")
        assertEquals(2, a.mobilitySeries.size)
        val firstId = a.mobilitySeries[0].id
        val secondId = a.mobilitySeries[1].id
        vm.updatePartMobility("mob-a", firstId) { it.copy(sets = 2, unit = MobilityUnit.REPS, reps = "8", durationSeconds = null) }
        vm.moveMobilityInPart("mob-a", secondId, -1)
        vm.updatePartMobilityConfig("mob-a", MobilityConfig(totalMinutes = 6))
        vm.openPartMobilityPicker("mob-b")
        vm.addMobilityToTarget(movements[0])
        val bId = vm.uiState.value.session!!.part("mob-b").mobilitySeries.single().id
        vm.removeMobilityFromPart("mob-b", bId)
        val result = vm.saveSession()
        assertTrue(result.message, result.success)
        vm.viewModelScope.cancel()
        editor = null
        ProgramRepository.closeInstance()
        repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
        awaitReady()
        val restored = repository.getProgramById("mobility-program")!!.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.single()
        a = restored.part("mob-a")
        assertEquals(listOf(movements[1].id, movements[0].id), a.mobilitySeries.map { it.catalogConfigurationId })
        val dose = a.mobilitySeries.last()
        assertEquals(firstId, dose.id)
        assertEquals(2, dose.sets)
        assertEquals(MobilityUnit.REPS, dose.unit)
        assertEquals("8", dose.reps)
        assertNull(dose.durationSeconds)
        assertEquals(6, a.mobilityConfig!!.totalMinutes)
        assertTrue(restored.part("mob-b").mobilitySeries.isEmpty())
        assertEquals(8, restored.exercises.single().sets.single().targetReps)
    }
}
