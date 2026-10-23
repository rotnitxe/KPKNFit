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
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.services.workout.VoiceUndoPayload

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ApreDurableRoomTest {
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
        state = apreDurableState()
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
    private var candidate: WorkoutUiState? = null
    private var atPublish: OngoingWorkoutState? = null
    private var undo: VoiceUndoPayload? = null
    private fun exercise() = state.session!!.exercises.first()
    private suspend fun stored() = repository.databaseForTests().stateDao().getOngoingWorkout()!!.toOngoingWorkoutState()!!
    private suspend fun record(index: Int, weight: Double = 150.0, reps: Int = 12): RecordSetResult {
        state = state.copy(currentSetIdx = index)
        return recorder.record(weight, reps.toDouble(), 0.0, SetAdvancedFeedback(rir = 0, reachedFailure = true),
            LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = index)
    }
    private suspend fun action(status: CalibrationStatus) {
        val next = resolveCalibration(state, state.calibrationDecisions.last().id, status, 2_000L)
        assertTrue(persistence.persistAndAwait(next) { state = next }.succeeded)
    }
    private fun controller() = WorkoutVoiceSetMutationController("", state.session!!.id, WorkoutRecordingGate(),
        { state }, { transform ->
            atPublish = runBlocking { stored() }
            state = transform(state)
        }, { it.session!!.exercises }, { snapshot, publish ->
            candidate = snapshot
            persistence.persistAndAwait(snapshot, publish)
        }, { token, _ -> token == undo }, { token -> if(token == undo) { undo = null; true } else false },
        {}, {}, refreshCalibrations = { previous, next ->
            if (!needsCorrectedCalibrationRefresh(previous) && !needsCorrectedCalibrationRefresh(next)) next else
                refreshCorrectedCalibrations(previous, next, emptyList(), now = 3_000L, accepted = { emptyList() })
        })
    private fun failWrites() {
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_apre_insert BEFORE INSERT ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'apre'); END")
        db.execSQL("CREATE TRIGGER fail_apre_update BEFORE UPDATE ON ongoing_workout BEGIN SELECT RAISE(ABORT, 'apre'); END")
    }
    private fun allowWrites() {
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("DROP TRIGGER fail_apre_insert"); db.execSQL("DROP TRIGGER fail_apre_update")
    }
    @Test fun s3ApplyRestartAndRevertPreserveActualS4AndItsXrm() = runBlocking {
        assertTrue(record(2) is RecordSetResult.Created)
        assertNull(state.calibrationDecisions.single().decision.capacityKg)
        action(CalibrationStatus.APPLIED)
        assertEquals(155.0, calibrationLoadForSet(state, exercise(), 3, null)!!.weight, 0.0)
        assertTrue(record(3, 155.0, 17) is RecordSetResult.Created)
        val restored = stored()
        state = state.copy(completedSets = restored.completedSets, calibrationDecisions = restored.calibrationDecisions)
        assertEquals(162.5, state.completedSets[workoutSetKey("apre",3)]!!.apreXrm!!.reference.capturedLoadKg!!, 0.0)
        action(CalibrationStatus.REVERTED)
        assertEquals(155.0, calibrationLoadForSet(state, exercise(), 3, null)!!.weight, 0.0)
        assertEquals(restored.completedSets, stored().completedSets)
    }
    @Test fun keepAndUnchangedRetryNeverAccumulateDecisions() = runBlocking {
        assertTrue(record(2) is RecordSetResult.Created)
        action(CalibrationStatus.KEPT)
        assertEquals(150.0, calibrationLoadForSet(state, exercise(), 3, null)!!.weight, 0.0)
        assertTrue(record(2) is RecordSetResult.Rejected)
        assertEquals(1, stored().calibrationDecisions.size)
    }
    @Test fun failedRecorderWriteKeepsDraftAndRetryRecordsOneS3() = runBlocking {
        val key = workoutSetKey("apre",2)
        state = state.copy(setDrafts = mapOf(key to WorkoutSetDraft(weightText = "150", valueText = "12", isDirty = true)))
        failWrites()
        assertTrue(record(2) is RecordSetResult.PersistenceFailed)
        assertTrue(state.completedSets.isEmpty()); assertTrue(stored().completedSets.isEmpty())
        assertTrue(key in state.setDrafts)
        allowWrites()
        assertTrue(record(2) is RecordSetResult.Created)
        assertEquals(1, stored().calibrationDecisions.size)
    }
    private suspend fun correctS4(withS3: Boolean) {
        if(withS3) assertTrue(record(2) is RecordSetResult.Created)
        assertTrue(record(3,155.0,17) is RecordSetResult.Created)
        val key = workoutSetKey("apre",3)
        assertEquals(162.5,state.completedSets[key]!!.apreXrm!!.reference.capturedLoadKg!!,0.0)
        val result = controller().patchAt(key, CompletedSetPatch(VoiceSetEditPatch(metricValue = 11)))
        assertTrue(result is WorkoutVoiceMutationResult.Committed)
        val corrected = state.completedSets[key]!!
        assertEquals(155.0,corrected.apreXrm!!.reference.capturedLoadKg!!,0.0)
        assertEquals(corrected,candidate!!.completedSets[key]); assertEquals(corrected,atPublish!!.completedSets[key])
        assertEquals(corrected,stored().completedSets[key]); assertEquals(corrected,(result as WorkoutVoiceMutationResult.Committed).set)
        undo = VoiceUndoPayload(key,"apre",3,null,System.currentTimeMillis()+60_000)
        assertTrue(controller().undo(undo!!).isCommitted)
        assertFalse(key in state.completedSets); assertFalse(key in stored().completedSets); assertNull(undo)
    }
    @Test fun correctionWithS3IsDurableAndUndoRemovesTheCapture() = runBlocking { correctS4(true) }
    @Test fun correctionWithoutS3IsDurableAndUndoRemovesTheCapture() = runBlocking { correctS4(false) }
    @Test fun failedCorrectionRollsBackUiAndRoomThenRetryRebuildsCapture() = runBlocking {
        assertTrue(record(3,155.0,17) is RecordSetResult.Created)
        val before = state.completedSets
        val key = workoutSetKey("apre",3)
        failWrites()
        assertTrue(controller().patchAt(key,CompletedSetPatch(VoiceSetEditPatch(metricValue = 11))) is WorkoutVoiceMutationResult.PersistenceFailed)
        assertEquals(before,state.completedSets); assertEquals(before,stored().completedSets); assertNull(atPublish)
        allowWrites()
        assertTrue(controller().patchAt(key,CompletedSetPatch(VoiceSetEditPatch(metricValue = 11))).isCommitted)
        assertEquals(155.0,stored().completedSets[key]!!.apreXrm!!.reference.capturedLoadKg!!,0.0)
    }
    @Test fun failedS4RecordingLeavesNoCaptureAndRetryStoresItExactlyOnce() = runBlocking {
        failWrites()
        assertTrue(record(3,155.0,17) is RecordSetResult.PersistenceFailed)
        assertTrue(stored().completedSets.isEmpty()); assertTrue(state.completedSets.isEmpty())
        allowWrites()
        assertTrue(record(3,155.0,17) is RecordSetResult.Created)
        assertEquals(162.5,stored().completedSets.values.single().apreXrm!!.reference.capturedLoadKg!!,0.0)
        assertTrue(record(3,155.0,17) is RecordSetResult.Rejected)
        assertEquals(1,stored().completedSets.size)
    }
    @Test fun s3CorrectionReplacesAppliedDecisionAtomicallyAndUndoRemovesItsEffect() = runBlocking {
        assertTrue(record(2) is RecordSetResult.Created)
        action(CalibrationStatus.APPLIED)
        val key = workoutSetKey("apre",2)
        assertTrue(controller().patchAt(key,CompletedSetPatch(VoiceSetEditPatch(metricValue = 17))).isCommitted)
        assertEquals(listOf(CalibrationStatus.SUPERSEDED,CalibrationStatus.PROPOSED),stored().calibrationDecisions.map { it.status })
        assertEquals(157.5,stored().calibrationDecisions.last().apre!!.set4LoadKg,0.0)
        assertEquals(150.0,calibrationLoadForSet(state,exercise(),3,null)!!.weight,0.0)
        undo = VoiceUndoPayload(key,"apre",2,null,System.currentTimeMillis()+60_000)
        assertTrue(controller().undo(undo!!).isCommitted)
        assertTrue(stored().calibrationDecisions.all { it.status == CalibrationStatus.SUPERSEDED })
        assertTrue(stored().completedSets.isEmpty())
    }
    @Test fun correctingS4LoadRebuildsFromItsActualKgWhileOtherEntriesStayIntact() = runBlocking {
        assertTrue(record(2) is RecordSetResult.Created)
        assertTrue(record(3,155.0,11) is RecordSetResult.Created)
        val source = state.completedSets[workoutSetKey("apre",2)]!!
        val key = workoutSetKey("apre",3)
        val result = controller().patchAt(key,CompletedSetPatch(VoiceSetEditPatch(weightKg = 140.0)))
        assertTrue(result.isCommitted)
        assertEquals(140.0,stored().completedSets[key]!!.originalLoad!!,0.0)
        assertEquals(140.0,stored().completedSets[key]!!.apreXrm!!.reference.capturedLoadKg!!,0.0)
        assertEquals(source,state.completedSets[workoutSetKey("apre",2)])
        assertEquals(source,stored().completedSets[workoutSetKey("apre",2)])
    }
}
