package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.CalorieRange
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.EnergyEstimateSource
import com.example.kpkn.data.models.SessionEnergySummary
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.multisport.HybridProgramPlanner
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HybridPlanningRoomTest {
    @Before fun setUp() {
        SyncRoomExecutor.install()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
        SyncRoomExecutor.uninstall()
    }

    private suspend fun freshRepository(): ProgramRepository {
        val repository = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        return repository
    }

    @Test fun proposalIsInRoomBeforeSuccessAndPreservesSessionsCalendarAndHistory() = runBlocking {
        val repository = freshRepository()
        val original = HybridPlanningFixtures.program()
        assertTrue(repository.addProgramNow(original).isSuccess)
        val before = repository.getProgramById(original.id)!!
        val historyBefore = repository.history.value
        val result = HybridPlanningService(repository).prepareAndSave(
            original.id, "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        ) as HybridProposalSaveResult.Saved
        val persisted = repository.databaseForTests().programDao().getById(original.id)!!.toProgram()
        val cached = repository.getProgramById(original.id)!!
        assertEquals(cached, persisted)
        assertEquals(result.proposal, HybridPlanningFixtures.week(persisted).hybridProposal)
        assertEquals(HybridPlanningFixtures.week(before).sessions, HybridPlanningFixtures.week(persisted).sessions)
        assertEquals(before.schedulePlan, persisted.schedulePlan)
        assertEquals(before.runState, persisted.runState)
        assertEquals(historyBefore, repository.history.value)
        assertTrue(HybridProgramPlanner.isCurrent(persisted, "week", result.proposal))
        assertEquals(before.macrocycles.single().blocks.single().mesocycles.single().weeks[1],
            persisted.macrocycles.single().blocks.single().mesocycles.single().weeks[1])
    }

    @Test fun roomFailureLeavesTheCacheAndExistingProgramUnchanged() = runBlocking {
        val repository = freshRepository()
        val original = HybridPlanningFixtures.program()
        assertTrue(repository.addProgramNow(original).isSuccess)
        val before = repository.getProgramById(original.id)!!
        repository.databaseForTests().close()
        val result = HybridPlanningService(repository).prepareAndSave(
            original.id, "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        )
        assertTrue(result is HybridProposalSaveResult.StorageFailure)
        assertEquals(before, repository.getProgramById(original.id))
    }

    @Test fun proposalSavePreservesAnAlreadyActiveDatedProgramAfterRepositoryNormalization() = runBlocking {
        val repository = freshRepository()
        val dated = HybridPlanningFixtures.program().copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = "2026-11-02", weekStartDay = 1,
                trainingDays = setOf(2, 4), mode = ScheduleMode.DATED,
            ),
        )
        assertTrue(repository.addProgramNow(dated).isSuccess)
        repository.startProgram(dated.id)
        // Serialize a no-op through the durable lane after startProgram's existing normalization.
        assertTrue(repository.mutateProgramNow(dated.id) { it })
        val before = repository.getProgramById(dated.id)!!
        val activeBefore = repository.activeProgramState.value
        assertTrue(HybridPlanningFixtures.week(before).trainingDayDates.isNotEmpty())
        val result = HybridPlanningService(repository).prepareAndSave(
            dated.id, "week", HybridPlanningFixtures.configuration, HybridPlanningFixtures.bindings,
        ) as HybridProposalSaveResult.Saved
        val after = repository.databaseForTests().programDao().getById(dated.id)!!.toProgram()
        assertEquals(before.schedulePlan, after.schedulePlan)
        assertEquals(before.runState, after.runState)
        assertEquals(activeBefore, repository.activeProgramState.value)
        assertEquals(HybridPlanningFixtures.week(before).sessions, HybridPlanningFixtures.week(after).sessions)
        assertEquals(HybridPlanningFixtures.week(before).trainingDayDates, HybridPlanningFixtures.week(after).trainingDayDates)
        assertTrue(HybridProgramPlanner.isCurrent(after, "week", result.proposal))
    }

    @Test fun legacyProgramJsonDoesNotAcquirePlanningConfigurationOrProposal() {
        val program = Json { ignoreUnknownKeys = true }.decodeFromString<Program>(
            """{"id":"old","name":"Anterior","macrocycles":[{"id":"m","name":"M","blocks":[{"id":"b","name":"B","mesocycles":[{"id":"s","name":"S","weeks":[{"id":"w","name":"W"}]}]}]}]}""",
        )
        assertNull(program.hybridPlanning)
        assertNull(HybridPlanningFixtures.week(program).hybridProposal)
        assertNull(program.trainingIntent)
    }

    @Test fun completedTrainingQueryReadsTheExistingFinalizedLogWithoutAddingItsSetCaloriesAgain() = runBlocking {
        val repository = freshRepository()
        val log = WorkoutLog(
            id = "mixed-completed", programId = "external-plan", sessionId = "existing-mixed-session",
            sessionName = "Sesión mixta existente", date = "2026-10-18T10:00:00Z", durationMinutes = 53,
            completedExercises = listOf(CompletedExercise(
                exerciseId = "manual-cardio", exerciseName = "Cardio",
                sets = listOf(CompletedSet("set", calories = 900.0)),
            )),
            energySummary = SessionEnergySummary(
                source = EnergyEstimateSource.FINAL, totalKcal = CalorieRange(200, 300, 400),
            ),
        )
        repository.finalizeWorkout(log)
        val before = repository.history.value
        val summary = repository.summarizeCompletedTraining()
        assertEquals(1, summary.activities.size)
        assertEquals(53L, summary.totalDurationMinutes)
        assertEquals(300L, summary.totalFinalEnergyKcal!!.mid)
        assertFalse(summary.hasPartialEnergyTotal)
        assertEquals(before, repository.history.value)
    }
}
