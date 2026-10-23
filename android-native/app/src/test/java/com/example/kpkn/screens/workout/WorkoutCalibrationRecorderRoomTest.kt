package com.example.kpkn.screens.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.calibration.*
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutCalibrationRecorderRoomTest {
    @get:Rule val roomRule = RoomTestRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var repository: ProgramRepository
    private lateinit var recorder: WorkoutSetRecorder
    private lateinit var persistence: WorkoutPersistenceController
    private lateinit var state: WorkoutUiState
    private var advances = 0

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        repository = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        repository.isReady.first { it }
        state = cal3Fixture().state.copy(completedSets = emptyMap(), calibrationDecisions = emptyList(),
            featureFlags = WorkoutFeatureFlags(workoutV2Homologation = false, workoutV2LoadModes = false, workoutV3UnifiedFlow = false))
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(OngoingWorkoutState(
            "", state.session!!, startTime = state.startTimeMs)))
        persistence = WorkoutPersistenceController(owner, "", state.session!!.id, { state }, { it.session!!.exercises },
            repository::updateOngoingWorkoutAndFlush, persistDispatcher = dispatcher, publishDispatcher = dispatcher)
        val ports = Proxy.newProxyInstance(WorkoutSetRecorder.Ports::class.java.classLoader,
            arrayOf(WorkoutSetRecorder.Ports::class.java)) { _, method, args ->
            when(method.name) {
                "visibleExercises" -> (args[0] as WorkoutUiState).session!!.exercises
                "activeContextProfile", "inferPlannedIntensity", "computeImbalanceNotice", "nextIncompleteStepAfter" -> null
                "inferUnitMode" -> UnitModeV2.REPS
                "effectiveLoadModeForExercise" -> LoadModeV2.LOAD
                "currentBodyWeight" -> 75.0
                "canonicalExerciseKey" -> (args[0] as Exercise).resolvedCanonicalExerciseId()
                "inferPlannedTarget" -> 6.0
                "workoutStepPositions" -> emptyList<WorkoutStep>()
                "recomputeLiveEnergy" -> SessionEnergySummary()
                "sessionForActiveMode", "adjustRestTimeForPace" -> args[0]
                "persistOngoingStateAndAwait" -> runBlocking {
                    @Suppress("UNCHECKED_CAST")
                    persistence.persistAndAwait(args[0] as WorkoutUiState, args[1] as () -> Unit)
                }
                "nextSet" -> { advances++; state = state.copy(currentSetIdx = state.currentSetIdx + 1); Unit }
                else -> Unit
            }
        } as WorkoutSetRecorder.Ports
        val gate = WorkoutRecordingGate()
        recorder = WorkoutSetRecorder(gate::tryStart, gate::finish, mutableSetOf(), repository, owner,
            { state }, { state = it(state) }, ports)
    }
    @After fun cleanup() { owner.cancel(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance(); Dispatchers.resetMain() }
    private fun exercise() = state.session!!.exercises.first()
    private fun editExercise(transform: (Exercise) -> Exercise) {
        state = state.copy(session = state.session!!.copy(exercises = state.session!!.exercises.mapIndexed { i,e -> if(i == 0) transform(e) else e }))
    }
    private suspend fun record(weight: Double = 69.0, reps: Int = 10, rir: Int? = 1,
        mode: LoadModeV2 = LoadModeV2.LOAD, partials: Int = 0, failure: Boolean = false) =
        recorder.record(weight, reps.toDouble(), rir?.toDouble(),
            SetAdvancedFeedback(rir = rir, partialReps = partials, reachedFailure = failure), mode, UnitModeV2.REPS, 75.0)
    private suspend fun persisted() = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!
    private suspend fun action(status: CalibrationStatus) {
        val next = resolveCalibration(state, state.calibrationDecisions.last().id, status, System.currentTimeMillis())
        assertTrue(persistence.persistAndAwait(next) { state = next }.succeeded)
    }
    private suspend fun assertDecision(type: CalibrationDecisionType, eligibility: CalibrationEligibility = CalibrationEligibility.OK) {
        val row = persisted().calibrationDecisions.last()
        assertEquals(type, row.decision.type)
        assertEquals(eligibility, row.decision.eligibility)
        assertEquals(CalibrationStatus.PROPOSED, row.status)
        assertEquals(listOf(row), repository.calibrationDecisions.forSession(row.sessionLogId))
        assertEquals(0, advances)
    }

    @Test fun registersRaiseAndKeepsTheResultOnTheRecordedCard() = runBlocking {
        assertTrue(record() is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.RAISE)
        assertEquals(94.3, state.calibrationDecisions.single().decision.capacityKg!!, .01)
        assertEquals(62.5, state.calibrationDecisions.single().decision.affectedSets.first().afterKg, 0.0)
    }
    @Test fun registersHold() = runBlocking {
        editExercise { it.copy(reference1RM = 94.3) }
        assertTrue(record() is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.HOLD)
    }
    @Test fun registersLower() = runBlocking {
        assertTrue(record(weight = 60.0, rir = 0) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.LOWER)
        assertEquals(55.0, state.calibrationDecisions.single().decision.affectedSets.first().afterKg, 0.0)
    }
    @Test fun registersFirstReferenceWithoutInventingImprovement() = runBlocking {
        editExercise { it.copy(reference1RM = null) }
        assertTrue(record() is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.FIRST_REFERENCE)
        assertNull(state.calibrationDecisions.single().decision.ratio)
        assertEquals(65.0, state.calibrationDecisions.single().decision.affectedSets.first().afterKg, 0.0)
    }
    @Test fun noEffortStillRegistersAndExplainsNoCalibration() = runBlocking {
        assertTrue(record(rir = null) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.NOT_APPLIED, CalibrationEligibility.NO_EFFORT)
    }
    @Test fun highReserveRegistersWithoutApplying() = runBlocking {
        assertTrue(record(rir = 3) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.NOT_APPLIED, CalibrationEligibility.RIR_TOO_HIGH)
    }
    @Test fun bodyweightRegistersWithoutKilogramCalibration() = runBlocking {
        assertTrue(record(weight = 0.0, mode = LoadModeV2.BODYWEIGHT) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.NOT_APPLIED, CalibrationEligibility.BODYWEIGHT)
    }
    @Test fun assistedRegistersWithoutExternalLoadCalibration() = runBlocking {
        assertTrue(record(weight = 20.0, mode = LoadModeV2.ASSISTED) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.NOT_APPLIED, CalibrationEligibility.NO_EXTERNAL_LOAD)
    }
    @Test fun partialsOnlyNeverBecomeFullReps() = runBlocking {
        assertTrue(record(reps = 0, partials = 4) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.NOT_APPLIED, CalibrationEligibility.PARTIALS_ONLY)
        assertEquals(0, persisted().completedSets.values.single().reps)
    }
    @Test fun twoRepsProposeFixedDrop() = runBlocking {
        assertTrue(record(reps = 2) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.LOW_REPS_DROP)
        assertEquals(57.5, state.calibrationDecisions.single().decision.affectedSets.first().afterKg, 0.0)
    }
    @Test fun zeroFullRepsWithDeclaredFailureUsesFixedDropAndKeepsExplicitZero() = runBlocking {
        assertTrue(record(reps = 0, rir = null, failure = true) is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.LOW_REPS_DROP)
        assertEquals(0, persisted().completedSets.values.single().recordedPayloadV3!!.completedReps)
    }
    @Test fun anAcceptedContextualReferenceWinsOverTheDeclaredOneRm() = runBlocking {
        val f = cal3Fixture()
        val accepted = f.record.copy(id = "old-accepted", sourceSetId = "previous-source", sessionLogId = "previous-log",
            status = CalibrationStatus.APPLIED, resolvedAt = System.currentTimeMillis() - 1,
            decision = f.record.decision.copy(capacityKg = 100.0))
        repository.calibrationDecisions.saveSnapshot(listOf(accepted))
        assertTrue(record() is RecordSetResult.Created)
        assertEquals(CalibrationReferenceSource.ACCEPTED_CALIBRATION, state.calibrationDecisions.single().decision.referenceSource)
        assertEquals(100.0, state.calibrationDecisions.single().decision.referenceKg!!, 0.0)
    }
    @Test fun nextSessionsAcceptsReferenceWithoutChangingTodaysLoads() = runBlocking {
        editExercise { it.copy(sets = it.sets.map { s -> if(s.purpose == SetPurpose.CALIBRATOR) s.copy(calibrationScope = CalibrationScope.NEXT_SESSIONS) else s }) }
        assertTrue(record() is RecordSetResult.Created)
        assertDecision(CalibrationDecisionType.RAISE)
        action(CalibrationStatus.APPLIED)
        val row = state.calibrationDecisions.single()
        assertTrue(row.decision.affectedSets.isEmpty())
        assertEquals(row.decision.capacityKg, repository.calibrationDecisions.accepted(System.currentTimeMillis()).single().capacityKg)
        assertEquals(60.0, exercise().sets[1].weight!!, 0.0)
    }
    @Test fun editSupersedesAppliedSourceWithoutAccumulationAndUnchangedRetryIsIdempotent() = runBlocking {
        assertTrue(record() is RecordSetResult.Created)
        action(CalibrationStatus.APPLIED)
        assertTrue(record() is RecordSetResult.Rejected)
        assertEquals(1, state.calibrationDecisions.size)
        assertTrue(record(weight = 70.0) is RecordSetResult.Updated)
        assertEquals(listOf(CalibrationStatus.SUPERSEDED, CalibrationStatus.PROPOSED), state.calibrationDecisions.map { it.status })
        assertEquals(CalibrationReferenceSource.DECLARED_1RM, state.calibrationDecisions.last().decision.referenceSource)
        assertEquals(60.0, calibrationLoadForSet(state, exercise(), 1, null)!!.weight, 0.0)
        action(CalibrationStatus.APPLIED)
        assertEquals(62.5, calibrationLoadForSet(state, exercise(), 1, null)!!.weight, 0.0)
        assertEquals(1, persisted().completedSets.size)
        assertEquals(2, repository.calibrationDecisions.forSession(calibrationExecutionId(state)).size)
    }
    @Test fun keepUndoAndRestorationUseDurableRecords() = runBlocking {
        assertTrue(record() is RecordSetResult.Created)
        action(CalibrationStatus.APPLIED)
        val stored = persisted()
        state = state.copy(calibrationDecisions = stored.calibrationDecisions, completedSets = stored.completedSets)
        assertEquals(62.5, calibrationLoadForSet(state, exercise(), 1, null)!!.weight, 0.0)
        action(CalibrationStatus.REVERTED)
        assertEquals(60.0, calibrationLoadForSet(state, exercise(), 1, null)!!.weight, 0.0)
        assertTrue(repository.calibrationDecisions.accepted(System.currentTimeMillis()).isEmpty())
    }
    @Test fun failedDecisionInsertRollsBackTheSetAndRetryCreatesOneDecision() = runBlocking {
        state = state.copy(setDrafts = mapOf("cal3-exercise_0" to WorkoutSetDraft(weightText = "69", valueText = "10", isDirty = true)))
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_cal3 BEFORE INSERT ON calibration_decisions BEGIN SELECT RAISE(ABORT, 'cal3 injected'); END")
        assertTrue(record() is RecordSetResult.PersistenceFailed)
        assertTrue(persisted().completedSets.isEmpty())
        assertTrue(state.calibrationDecisions.isEmpty())
        assertTrue(state.setDrafts.isNotEmpty())
        db.execSQL("DROP TRIGGER fail_cal3")
        assertTrue(record() is RecordSetResult.Created)
        assertEquals(1, persisted().completedSets.size)
        assertEquals(1, repository.calibrationDecisions.forSession(calibrationExecutionId(state)).size)
    }
    @Test fun ordinaryAmrapRecordsHistoryWithoutCreatingACalibrationDecision() = runBlocking {
        editExercise { it.copy(sets = it.sets.map { s -> if(s.purpose == SetPurpose.CALIBRATOR) s.copy(purpose = SetPurpose.AMRAP) else s }) }
        assertTrue(record(reps = 5) is RecordSetResult.Created)
        assertTrue(state.calibrationDecisions.isEmpty())
        assertTrue(persisted().completedSets.values.single().amrapBelowMinimum)
        assertTrue(repository.calibrationDecisions.forSession(calibrationExecutionId(state)).isEmpty())
    }
}
