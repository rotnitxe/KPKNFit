package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorLoadGenerationTest {

    @get:Rule
    val roomRule = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
        withTimeout(10_000) {
            while (!repository.isReady.value) delay(25)
        }
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun importedEditorRejectsMutationsWhenItsProgramIdDoesNotMatchTheStore() = runBlocking {
        val original = program("draft-${java.util.UUID.randomUUID()}", Session(id = "session", name = "Original"))
        val store = ImportedProgramEditorStore(original) { true }
        val vm = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = "otro", sessionId = "session", draftWeekId = "week", draftMacroIndex = 0,
            draftMesoIndex = 0, draftDayOfWeek = 1, importedProgramStore = store,
        )
        assertFalse(vm.mutateEditorProgram { it.copy(name = "No guardar") })
        assertFalse(vm.upsertEditorSession("week", 0, 0, Session(id = "otro", name = "No guardar")))
        assertEquals(original, store.program.value)
    }

    @Test
    fun importedEditorDoesNotShowANewSessionWhenDraftPersistenceFails() = runBlocking {
        val id = "import-editor-failure-${java.util.UUID.randomUUID()}"
        val original = program(id)
        repository.addProgram(original)
        val store = ImportedProgramEditorStore(original) { false }
        val vm = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = id, sessionId = "new-session", draftWeekId = "week", draftMacroIndex = 0,
            draftMesoIndex = 0, draftDayOfWeek = 1, importedProgramStore = store,
        )
        withTimeout(10_000) { while (vm.uiState.value.loadErrorMessage == null) delay(25) }
        assertEquals(null, vm.uiState.value.session)
        assertEquals(original, store.program.value)
        assertTrue(repository.getProgramById(id)!!.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.isEmpty())
    }

    @Test
    fun importedEditorAutosavesOnlyToDraftEvenWhenLibraryHasSameId() = runBlocking {
        val id = "import-editor-isolation-${java.util.UUID.randomUUID()}"
        val session = Session(id = "draft-session-${java.util.UUID.randomUUID()}", name = "Importada")
        val libraryProgram = program(id, session.copy(name = "Biblioteca"))
        repository.addProgram(libraryProgram)
        var durable = program(id, session)
        val store = ImportedProgramEditorStore(durable) { next -> durable = next; true }
        val vm = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = id, sessionId = session.id, draftWeekId = "week", draftMacroIndex = 0,
            draftMesoIndex = 0, draftDayOfWeek = 1, importedProgramStore = store,
        )
        withTimeout(10_000) { while (vm.uiState.value.session == null) delay(25) }
        assertEquals("Importada", vm.uiState.value.session?.name)
        vm.addExerciseToPart(null, ExerciseMuscleInfo(id = "sentadilla_barra", name = "Sentadilla con barra", category = "Fuerza", equipment = "Barra"))
        vm.updateSessionName("Editada")
        assertTrue(vm.flushProgramAutoSaveAndAwait())
        val importedName = durable.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single().name
        val libraryName = repository.getProgramById(id)!!.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single().name
        assertEquals("Editada", importedName)
        assertEquals("Biblioteca", libraryName)
        assertFalse(vm.upsertEditorSession("semana-ausente", 0, 0, session.copy(id = "no-guardar")))
        assertFalse(vm.upsertEditorSession("week", 99, 0, session.copy(id = "no-guardar")))
        assertEquals("Biblioteca", repository.getProgramById(id)!!.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single().name)
        assertTrue(vm.createSessionForDay(2).success)
        withTimeout(10_000) { while (vm.uiState.value.dayOfWeek != 2) delay(25) }
        assertEquals(2, durable.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.size)
        assertEquals(1, repository.getProgramById(id)!!.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.size)
    }

    @Test
    fun cancelledInitialLoadAndRepositoryEmissionDoNotReplaceDirtySwitchedSession() = runBlocking {
        val programId = "editor-load-generation-program"
        val initial = Session(id = "session-a", name = "A")
        val target = Session(id = "session-b", name = "B")
        repository.addProgram(program(programId, initial, target))

        val viewModel = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = programId,
            sessionId = initial.id,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = 1,
        )

        // Invalidate the constructor's A load, then switch while that load may
        // still be resolving its IO snapshot. The switch generation must own UI.
        viewModel.invalidateInitialSessionLoadForSwitch()
        viewModel.switchToSession(target.id, "week", 0, 0)
        withTimeout(10_000) {
            while (viewModel.uiState.value.session?.id != target.id) delay(10)
        }
        assertEquals(target.id, viewModel.uiState.value.session?.id)

        viewModel.updateUi { state ->
            state.copy(session = state.session?.copy(name = "B editada localmente"))
        }
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        val latestProgram = requireNotNull(repository.getProgramById(programId))
        repository.updateProgramNow(latestProgram.copy(description = "Actualizacion externa"))

        assertEquals("B editada localmente", viewModel.uiState.value.session?.name)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    private fun program(programId: String, vararg sessions: Session) = Program(
        id = programId,
        name = "Programa de prueba",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week",
                                        name = "Semana 1",
                                        sessions = sessions.toList(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
