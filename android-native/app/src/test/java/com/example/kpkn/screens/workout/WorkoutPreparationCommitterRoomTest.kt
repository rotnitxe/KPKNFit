package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPreparationCommitterRoomTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var repository: ProgramRepository
    private lateinit var persistence: WorkoutPersistenceController
    private lateinit var committer: WorkoutPreparationCommitter
    private lateinit var gate: WorkoutRecordingGate
    private lateinit var session: Session
    private var uiState = WorkoutUiState()
    private val failures = mutableListOf<String>()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        session = Session(
            id = "preparation-session",
            name = "Preparation durability",
            exercises = listOf(
                Exercise(
                    id = "exercise",
                    name = "Fixture squat",
                    sets = listOf(ExerciseSet(id = "work-1", targetReps = 8)),
                    warmupSets = listOf(WarmupSetDefinition(id = "warmup-1", percentageOfWorkingWeight = 0.5, targetReps = 6)),
                    mobilitySeries = listOf(MobilitySeries(id = "mobility-1", name = "Hip opener", sets = 1)),
                ),
            ),
        )
        assertEquals(
            StartWorkoutResult.Started,
            repository.startWorkout(OngoingWorkoutState(programId = "", session = session, startTime = 100L)),
        )
        uiState = WorkoutUiState(session = session, startTimeMs = 100L)
        persistence = WorkoutPersistenceController(
            scope = owner,
            programId = "",
            sessionId = session.id,
            getState = { uiState },
            visibleExercises = { it.session!!.exercises },
            writeOngoing = repository::updateOngoingWorkoutAndFlush,
            persistDispatcher = dispatcher,
            publishDispatcher = dispatcher,
        )
        gate = WorkoutRecordingGate()
        committer = WorkoutPreparationCommitter(
            tryStartRecording = gate::tryStart,
            finishRecording = gate::finish,
            getState = { uiState },
            updateState = { transform ->
                val previous = uiState
                val next = transform(previous)
                if (next != previous) uiState = next.copy(persistenceRevision = previous.persistenceRevision + 1)
            },
            persistAndAwait = { snapshot, onCommitted -> persistence.persistAndAwait(snapshot, onCommitted) },
            onPersistFailure = failures::add,
        )
    }

    @After
    fun tearDown() {
        owner.cancel()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    private fun failInsertAndUpdate() {
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_preparation_insert BEFORE INSERT ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected preparation insert failure'); END")
        db.execSQL("CREATE TRIGGER fail_preparation_update BEFORE UPDATE ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'injected preparation update failure'); END")
    }

    private fun allowWrites() {
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER IF EXISTS fail_preparation_insert")
        db.execSQL("DROP TRIGGER IF EXISTS fail_preparation_update")
    }

    private suspend fun room() = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!

    private fun warmupCandidate(base: WorkoutUiState, reps: Int, weight: Double): WorkoutUiState {
        val key = WorkoutStepRules.warmupStepKey("exercise", "warmup-1")
        val set = (base.completedSets[key] ?: CompletedSet(id = key)).copy(
            reps = reps,
            weight = weight,
            isWarmup = true,
        )
        return base.copy(
            completedSets = base.completedSets + (key to set),
            warmupCompletedExerciseIds = base.warmupCompletedExerciseIds + key,
            preparationReports = base.preparationReports + (
                key to PreparationReport(reps.toDouble(), PreparationReportUnit.REPS, weightKg = weight, reps = reps)
            ),
            persistenceRevision = base.persistenceRevision + 1,
        )
    }

    private fun mobilityCandidate(base: WorkoutUiState, seconds: Double): WorkoutUiState {
        val key = WorkoutStepRules.mobilityStepKey("exercise", "mobility-1", 0)
        return base.copy(
            mobilityCompletedExerciseIds = base.mobilityCompletedExerciseIds + key,
            preparationReports = base.preparationReports + (key to PreparationReport(seconds, PreparationReportUnit.SECONDS)),
            persistenceRevision = base.persistenceRevision + 1,
        )
    }

    @Test
    fun warmupRoomInsertFailure_keepsReportAndCompletionUnpublished_thenRetryRestoresBoth() = runBlocking {
        val key = WorkoutStepRules.warmupStepKey("exercise", "warmup-1")
        failInsertAndUpdate()

        val failed = committer.commit(key, warmupCandidate(uiState, reps = 7, weight = 30.0))

        assertTrue(failed is WorkoutPersistResult.Failed)
        assertFalse(key in uiState.warmupCompletedExerciseIds)
        assertFalse(key in uiState.preparationReports)
        assertTrue(uiState.completedSets.isEmpty())
        assertFalse(key in room().warmupCompletedExerciseIds)
        assertTrue(room().preparationReports.isEmpty())
        assertEquals(1, failures.size)
        assertFalse(gate.isBusy())

        allowWrites()
        val retry = committer.commit(key, warmupCandidate(uiState, reps = 7, weight = 30.0))
        val restored = room()
        assertTrue(retry.succeeded)
        assertTrue(key in restored.warmupCompletedExerciseIds)
        assertEquals(7.0, restored.preparationReports[key]!!.value, 0.0)
        assertEquals(30.0, restored.completedSets[key]!!.weight, 0.0)
        assertEquals(7, restored.completedSets[key]!!.reps)
        assertTrue(key in uiState.warmupCompletedExerciseIds)
        assertFalse(gate.isBusy())
    }

    @Test
    fun mobilityRoomUpdateFailure_keepsPreviouslyCommittedReport_thenRetryUpdatesTheSameOccurrence() = runBlocking {
        val key = WorkoutStepRules.mobilityStepKey("exercise", "mobility-1", 0)
        val first = committer.commit(key, mobilityCandidate(uiState, seconds = 20.0))
        assertTrue(first.succeeded)
        assertEquals(20.0, room().preparationReports[key]!!.value, 0.0)

        failInsertAndUpdate()
        val failed = committer.commit(key, mobilityCandidate(uiState, seconds = 35.0))
        assertTrue(failed is WorkoutPersistResult.Failed)
        assertEquals(20.0, room().preparationReports[key]!!.value, 0.0)
        assertEquals(20.0, uiState.preparationReports[key]!!.value, 0.0)
        assertTrue(key in uiState.mobilityCompletedExerciseIds)
        assertFalse(gate.isBusy())

        allowWrites()
        val retry = committer.commit(key, mobilityCandidate(uiState, seconds = 35.0))
        val restored = room()
        assertTrue(retry.succeeded)
        assertTrue(key in restored.mobilityCompletedExerciseIds)
        assertEquals(35.0, restored.preparationReports[key]!!.value, 0.0)
        assertEquals(setOf(key), restored.preparationReports.keys)
        assertFalse(gate.isBusy())
    }
}
