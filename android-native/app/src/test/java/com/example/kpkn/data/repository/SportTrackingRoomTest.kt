package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.*
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
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
class SportTrackingRoomTest {
    @Before fun before() { SyncRoomExecutor.install(); Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { ProgramRepository.closeInstance(); KpknDatabase.closeInstance(); Dispatchers.resetMain(); SyncRoomExecutor.uninstall() }

    private val engine = object : SportDocumentEngine {
        override val id = "manual.test"
        override val version = 1
        override val sports = setOf(SportId.STRENGTH)
        override val purposes = SportDocumentPurpose.entries.toSet()
        override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> = emptyList()
    }
    private fun doc(id: String, purpose: SportDocumentPurpose) = SportTrackingDocument(
        id, engine.id, 1, SportId.STRENGTH.name, purpose.name, buildJsonObject { put("value", 3) },
    )
    private suspend fun fresh(): ProgramRepository {
        val repo = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        withTimeout(10000) { repo.isReady.first { it } }
        repo.resetAllStateSync()
        return repo
    }

    @Test fun `plan metadata persists without replacing native exercises or alternatives`() = runBlocking {
        val repo = fresh()
        val original = HybridPlanningFixtures.program()
        assertTrue(repo.addProgramNow(original).isSuccess)
        val before = repo.getProgramById(original.id)!!
        val session = HybridPlanningFixtures.week(before).sessions.first()
        val result = SportTrackingService(repo, SportTrackingRegistry(listOf(engine))).upsertBaseSessionPlans(
            original.id, session.id, listOf(doc("plan", SportDocumentPurpose.PLAN)),
        )
        assertTrue(result is SportTrackingSaveResult.Saved)
        val persisted = repo.databaseForTests().programDao().getById(original.id)!!.toProgram()
        val updated = HybridPlanningFixtures.week(persisted).sessions.first()
        assertEquals(session, updated.copy(sportDocuments = session.sportDocuments))
        assertEquals(1, updated.sportDocuments.size)
        assertEquals(repo.getProgramById(original.id), persisted)
        assertTrue(repo.history.value.isEmpty())
    }

    @Test fun `result correction retains the one existing activity its final energy and run`() = runBlocking {
        val repo = fresh()
        val future = doc("future", SportDocumentPurpose.OBSERVATION).copy(engineId = "future.engine", version = 98)
        repo.finalizeWorkout(WorkoutLog("existing", "", "session", "Existing activity", "2026-10-18", 55,
            energySummary = SessionEnergySummary(methodVersion = "independent-recorded-fixture-v1", totalKcal = CalorieRange(100, 120, 140), source = EnergyEstimateSource.FINAL),
            sportDocuments = listOf(future)))
        val before = repo.history.value.single()
        val service = SportTrackingService(repo, SportTrackingRegistry(listOf(engine)))
        assertTrue(service.upsertCompletedResults(before.id, listOf(doc("manual", SportDocumentPurpose.RESULT))) is SportTrackingSaveResult.Saved)
        val cached = repo.history.value.single()
        val persisted = repo.databaseForTests().workoutLogDao().getById(before.id)!!.toWorkoutLog()
        assertEquals(cached, persisted)
        assertEquals(before, cached.copy(sportDocuments = before.sportDocuments))
        assertEquals(listOf(future, doc("manual", SportDocumentPurpose.RESULT)), cached.sportDocuments)
        assertTrue(service.upsertCompletedResults(before.id, listOf(doc("manual", SportDocumentPurpose.OBSERVATION))) is SportTrackingSaveResult.Saved)
        assertEquals(1, repo.history.value.size)
        assertEquals(2, repo.history.value.single().sportDocuments.size)
    }

    @Test fun `retired energy is removed consistently from durable state and cache without losing originals`() = runBlocking {
        val repo = fresh()
        val input = WorkoutLog("retired-energy", "", "session", "Original", "2026-10-18", 17,
            notes = "Nota original", actualDurationSeconds = 61,
            energySummary = SessionEnergySummary(methodVersion = "auge-energy-v2", totalKcal = CalorieRange(100,120,140)))
        repo.finalizeWorkout(input)
        val cached = repo.history.value.single()
        val stored = repo.databaseForTests().workoutLogDao().getById(input.id)!!.toWorkoutLog()!!
        assertEquals(cached, stored)
        assertNull(cached.energySummary)
        assertEquals(input.notes, cached.notes)
        assertEquals(input.actualDurationSeconds, cached.actualDurationSeconds)
        assertEquals(input.id, cached.id)
    }

    @Test fun `invalid purpose cannot write or create a log and storage failure cannot report saved`() = runBlocking {
        val repo = fresh()
        repo.finalizeWorkout(WorkoutLog("existing", "", "session", "Existing", "2026-10-18", 10))
        val service = SportTrackingService(repo, SportTrackingRegistry(listOf(engine)))
        assertTrue(service.upsertCompletedResults("missing", listOf(doc("bad", SportDocumentPurpose.PLAN))) is SportTrackingSaveResult.Invalid)
        assertTrue(service.upsertCompletedResults("missing", listOf(doc("valid", SportDocumentPurpose.RESULT))) is SportTrackingSaveResult.NotFound)
        val before = repo.history.value
        val database = repo.databaseForTests()
        // Room can reopen after close(); reject the actual write instead.
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER kpkn_fail_sport_write BEFORE INSERT ON workout_logs " +
                "BEGIN SELECT RAISE(ABORT, 'kpkn_test_sport_storage_failure'); END",
        )
        try {
            assertTrue(service.upsertCompletedResults("existing", listOf(doc("valid", SportDocumentPurpose.RESULT))) is SportTrackingSaveResult.StorageFailure)
            assertEquals(before, repo.history.value)
            assertEquals(before.single(), database.workoutLogDao().getById("existing")!!.toWorkoutLog())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER kpkn_fail_sport_write")
        }
    }
}
