package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.entitlements.*
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class FreeProgramMutationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var level = PlanLevel.FREE
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { store.clear(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance(); Dispatchers.resetMain() }

    private fun weekly(count: Int = 0): Program {
        val week = ProgramWeek("week", "Week", sessions = (1..count).map { Session("session-$it", "Session $it", dayOfWeek = it) })
        val meso = Mesocycle("meso", "Meso", weeks = listOf(week))
        val block = Block("block", "Block", mesocycles = listOf(meso))
        return Program("free-mutations", "Weekly", macrocycles = listOf(Macrocycle("macro", "Macro", listOf(block))))
    }
    private fun sessions(program: Program) = program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions
    private fun withWeeks(program: Program, weeks: List<ProgramWeek>): Program {
        val macro = program.macrocycles.single(); val block = macro.blocks.single(); val meso = block.mesocycles.single()
        return program.copy(macrocycles = listOf(macro.copy(blocks = listOf(block.copy(mesocycles =
            listOf(meso.copy(weeks = weeks)))))))
    }
    private fun mapWeek(program: Program, update: (ProgramWeek) -> ProgramWeek): Program =
        withWeeks(program, listOf(update(program.macrocycles.single().blocks.single().mesocycles.single().weeks.single())))
    private suspend fun repository() = ProgramRepository.initForTests(context) { level }.also {
        withTimeout(10_000) { it.isReady.first { ready -> ready } }
    }
    private fun viewModel(): ProgramDetailViewModel = ProgramDetailViewModel("free-mutations").also { store.put("detail", it) }

    @Test fun detailAddsFiveSessionsAndRejectsSixthWithVisibleErrorWithoutThrowing() = runBlocking {
        val repo = repository(); assertTrue(repo.addProgramNow(weekly()).isSuccess)
        val vm = viewModel()
        repeat(5) { index -> vm.addSession(0, 0, "week", Session("session-${index+1}", "Session ${index+1}", dayOfWeek = index+1)) }
        repo.flushPendingWrites()
        assertEquals(5, sessions(repo.getProgramById("free-mutations")!!).size)
        val persistedBefore = repo.databaseForTests().programDao().getById("free-mutations")!!
        // This is the actual UI callback. A limit denial must not escape and crash Compose.
        assertFalse(vm.addSession(0, 0, "week", Session("session-6", "Sixth", dayOfWeek = 6)))
        repo.flushPendingWrites()
        assertEquals(persistedBefore, repo.databaseForTests().programDao().getById("free-mutations"))
        assertEquals(5, sessions(repo.getProgramById("free-mutations")!!).size)
        assertFalse(vm.uiState.value.snackbarMessage.isNullOrBlank())
    }

    @Test fun editingAnExistingSessionAtTheLimitRemainsAvailable() = runBlocking {
        val repo = repository(); assertTrue(repo.addProgramNow(weekly(5)).isSuccess)
        viewModel().addSession(0, 0, "week", Session("session-3", "Edited third", dayOfWeek = 3))
        repo.flushPendingWrites()
        val stored = repo.databaseForTests().programDao().getById("free-mutations")!!.toProgram()
        assertEquals(5, sessions(stored).size)
        assertEquals("Edited third", sessions(stored).single { it.id == "session-3" }.name)
    }

    @Test fun repeatedAssignmentsCannotExpandOneSessionIntoSixWeeklySlots() = runBlocking {
        val repo = repository(); val original = weekly(1); assertTrue(repo.addProgramNow(original).isSuccess)
        val blocked = sessions(original).single().copy(assignedDays = listOf(1,2,3,4,5,6))
        assertTrue(runCatching { repo.upsertSessionInProgramNow(original.id, "week", 0, 0, blocked) }.exceptionOrNull() is CapabilityRequiredException)
        assertEquals(original, repo.getProgramById(original.id))
        assertEquals(original, repo.databaseForTests().programDao().getById(original.id)!!.toProgram())
    }

    @Test fun eachAdvancedMutationIsRejectedBeforeCacheAndRoomChangeAndAcceptedByPro() = runBlocking {
        val repo = repository(); val original = weekly(1); assertTrue(repo.addProgramNow(original).isSuccess)
        val macro = original.macrocycles.single(); val block = macro.blocks.single(); val meso = block.mesocycles.single()
        val variants = linkedMapOf(
            "complex" to original.copy(structure = ProgramStructure.COMPLEX),
            "second-macro" to original.copy(macrocycles = listOf(macro, macro.copy(id = "macro-2"))),
            "second-block" to original.copy(macrocycles = listOf(macro.copy(blocks = listOf(block, block.copy(id = "block-2"))))),
            "second-meso" to original.copy(macrocycles = listOf(macro.copy(blocks = listOf(block.copy(mesocycles = listOf(meso, meso.copy(id = "meso-2"))))))),
            "second-week" to withWeeks(original, listOf(meso.weeks.single(), meso.weeks.single().copy(id = "week-2"))),
            "recipe" to original.copy(sourceRecipe = TrainingPlanRecipe("paid-fixture", emptyList())),
            "protocol" to original.copy(sourceProtocolId = "paid-protocol-fixture"),
            "propose" to original.copy(autoregulationMode = AutoregulationMode.PROPOSE),
            "auto" to original.copy(autoregulationMode = AutoregulationMode.AUTO),
            "volume" to original.copy(autoVolumeEnabled = true),
            "variant-b" to mapWeek(original) { it.copy(sessions = listOf(it.sessions.single().copy(sessionB = Session("b", "B")))) },
            "variant-c" to mapWeek(original) { it.copy(sessions = listOf(it.sessions.single().copy(sessionC = Session("c", "C")))) },
            "variant-d" to mapWeek(original) { it.copy(sessions = listOf(it.sessions.single().copy(sessionD = Session("d", "D")))) },
            "micro" to mapWeek(original) { it.copy(sessions = listOf(it.sessions.single().copy(microProgram = SessionMicroProgram(enabled = true)))) },
        )
        val rowBefore = repo.databaseForTests().programDao().getById(original.id)
        for ((name, advanced) in variants) {
            assertTrue(name, repo.addProgramNow(advanced).exceptionOrNull() is CapabilityRequiredException)
            assertTrue(name, runCatching { repo.mutateProgramNow(original.id) { advanced } }.exceptionOrNull() is CapabilityRequiredException)
            assertEquals(name, original, repo.getProgramById(original.id))
            assertEquals(name, rowBefore, repo.databaseForTests().programDao().getById(original.id))
        }
        level = PlanLevel.PRO
        for ((name, advanced) in variants) {
            assertTrue(name, repo.addProgramNow(advanced.copy(id = "pro-$name")).isSuccess)
            assertNotNull(name, repo.databaseForTests().programDao().getById("pro-$name"))
        }
    }

    @Test fun addingAnotherWeekFromDetailLeavesFreeWeekIntactAndReportsTheLimit() = runBlocking {
        val repo = repository(); val original=weekly(2); assertTrue(repo.addProgramNow(original).isSuccess)
        val vm=viewModel(); vm.addWeekToSimpleProgram()
        repo.flushPendingWrites()
        assertEquals(original, repo.getProgramById(original.id))
        assertEquals(original, repo.databaseForTests().programDao().getById(original.id)!!.toProgram())
        assertFalse(vm.uiState.value.snackbarMessage.isNullOrBlank())
    }
}
