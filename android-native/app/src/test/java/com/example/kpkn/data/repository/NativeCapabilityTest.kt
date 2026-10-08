package com.example.kpkn.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.data.onboarding.*
import com.example.kpkn.domain.entitlements.*
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
class NativeCapabilityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var level = PlanLevel.FREE
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { ProgramRepository.closeInstance(); KpknDatabase.closeInstance(); Dispatchers.resetMain() }
    private fun program(sessions: Int, weeks: Int = 1): Program {
        val entries = (1..weeks).map { index ->
            ProgramWeek("week-$index", "Week", sessions = (1..sessions).map { Session("session-$it", "Session") })
        }
        val meso = Mesocycle("meso", "Meso", weeks = entries)
        val block = Block("block", "Block", mesocycles = listOf(meso))
        return Program("capability", "Weekly", macrocycles = listOf(Macrocycle("macro", "Macro", listOf(block))))
    }
    private suspend fun repository(): ProgramRepository = ProgramRepository.initForTests(context) { level }.also {
        withTimeout(10_000) { it.isReady.first { ready -> ready } }
    }
    @Test fun everyProgramSaveEntryRejectsFreeSixthSessionBeforePublishing() = runBlocking {
        val repo = repository()
        val allowed = program(5); val blocked = program(6)
        assertTrue(repo.addProgramNow(allowed).isSuccess)
        assertThrows(CapabilityRequiredException::class.java) { repo.addProgram(blocked.copy(id = "new")) }
        assertThrows(CapabilityRequiredException::class.java) { repo.updateProgram(blocked) }
        assertTrue(runCatching { repo.updateProgramNow(blocked) }.exceptionOrNull() is CapabilityRequiredException)
        assertTrue(repo.addProgramNow(blocked).exceptionOrNull() is CapabilityRequiredException)
        assertTrue(runCatching { repo.mutateProgramNow(allowed.id) { blocked } }.exceptionOrNull() is CapabilityRequiredException)
        assertTrue(runCatching { repo.replaceProgramSafely(blocked) }.exceptionOrNull() is CapabilityRequiredException)
        assertEquals(allowed, repo.getProgramById(allowed.id))
        assertEquals(5, repo.databaseForTests().programDao().getById(allowed.id)!!.toProgram().macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.size)
    }
    @Test fun proExpiryRetainsAdvancedHistoryAndAllowsDeletion() = runBlocking {
        level = PlanLevel.PRO
        val repo = repository(); val advanced = program(6, 2)
        assertTrue(repo.addProgramNow(advanced).isSuccess)
        level = PlanLevel.FREE
        assertNotNull(repo.getProgramById(advanced.id))
        assertTrue(repo.addProgramNow(advanced.copy(name = "Edited")).isFailure)
        repo.deleteProgram(advanced.id); repo.flushPendingWrites()
        assertNull(repo.databaseForTests().programDao().getById(advanced.id))
    }
    @Test fun setupAndDirectNutritionCommitsEnforceSamePolicyAndReplaySurvivesExpiry() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        try {
            val setup = SetupCommitCoordinator(db, accessLevel = { level })
            val request = SetupCommitRequest("blocked", null, Settings(), program(6), null, false, false)
            assertTrue(runCatching { setup.commit(request) }.exceptionOrNull() is CapabilityRequiredException)
            assertTrue(db.programDao().getAll().isEmpty()); assertNull(db.settingsDao().get())
            val nutrition = NutritionPlanCommitCoordinator(db, accessLevel = { level })
            val edit = NutritionPlanCommitRequest("nutrition", null, false, true)
            assertTrue(runCatching { nutrition.commit(edit) }.exceptionOrNull() is CapabilityRequiredException)
            assertNull(db.settingsDao().get())
            level = PlanLevel.PRO
            val committed = nutrition.commit(edit)
            level = PlanLevel.FREE
            assertEquals(committed, nutrition.commit(edit))
        } finally { db.close() }
    }
    @Test fun nativeNutritionLogSavesRejectFreeAndDeleteKeepsHistoryControl() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val repo = ctor.newInstance(context, db, false).apply { accessLevel = { level } }
        val log = NutritionLog(id = "meal", date = "2026-10-08T12:00:00Z", foods = listOf(LoggedFood(foodName = "Rice", amount = 100.0,
            calories = 130.0, protein = 3.0, carbs = 28.0, fats = 1.0)))
        try {
            assertThrows(CapabilityRequiredException::class.java) { repo.addNutritionLog(log) }
            assertTrue(runCatching { repo.saveNutritionLog(log) }.exceptionOrNull() is CapabilityRequiredException)
            assertTrue(db.nutritionDao().getAllLogs().isEmpty())
            level = PlanLevel.PRO; repo.saveNutritionLog(log)
            level = PlanLevel.FREE
            assertEquals(1, repo.nutritionLogs.value.size)
            repo.deleteNutritionLogAndAwait(log.id)
            assertTrue(db.nutritionDao().getAllLogs().isEmpty())
        } finally {
            val job = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }.get(repo) as Job
            job.cancelAndJoin(); db.close()
        }
    }
    @Test fun repeatedDayAssignmentsAndVariantsCannotBypassWeeklyLimit() {
        val repeated = program(1).let { p ->
            val macro = p.macrocycles.single(); val block = macro.blocks.single(); val meso = block.mesocycles.single(); val week = meso.weeks.single()
            val editedWeek = week.copy(sessions = listOf(week.sessions.single().copy(assignedDays = (1..6).toList())))
            val editedMeso = meso.copy(weeks = listOf(editedWeek))
            val editedBlock = block.copy(mesocycles = listOf(editedMeso))
            p.copy(macrocycles = listOf(macro.copy(blocks = listOf(editedBlock))))
        }
        assertEquals("free_weekly_session_limit", ProgramAccessPolicy.rejection(PlanLevel.FREE, repeated))
        assertNull(ProgramAccessPolicy.rejection(PlanLevel.PRO, repeated))
    }
}
