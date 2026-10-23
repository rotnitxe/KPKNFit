package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ApreDurableViewModelRoomTest {
    @get:Rule val room = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetAugeRepository() }
    @After fun cleanup() {
        try { fixture?.close(); LiveSessionVmFixture.resetAugeRepository(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance() }
        finally { Dispatchers.resetMain() }
    }
    private suspend fun open(): Pair<LiveSessionVmFixture,WorkoutViewModel> {
        val f = LiveSessionVmFixture(apreDurableState().session!!).prepare().also { fixture = it }
        return f to f.open()
    }
    private suspend fun record(vm: WorkoutViewModel,index: Int,load: Double,reps: Int) = vm.recordSetV2(
        load,reps.toDouble(),0.0,SetAdvancedFeedback(rir = 0,reachedFailure = true),
        LoadModeV2.LOAD,UnitModeV2.REPS,75.0,setIdxOverride = index,
        expectedExerciseId = "apre",expectedSetIdx = index)
    private suspend fun correction(withS3: Boolean) {
        val (f,vm) = open()
        if(withS3) assertTrue(record(vm,2,150.0,12) is RecordSetResult.Created)
        assertTrue(record(vm,3,155.0,17) is RecordSetResult.Created)
        val key = workoutSetKey("apre",3)
        assertEquals(162.5,vm.uiState.value.completedSets[key]!!.apreXrm!!.reference.capturedLoadKg!!,0.0)
        vm.patchCompletedSet(key,CompletedSetPatch(VoiceSetEditPatch(metricValue = 11)))
        withTimeout(20_000) { vm.uiState.first { it.completedSets[key]?.reps == 11 } }
        val corrected = vm.uiState.value.completedSets[key]!!
        assertEquals(155.0,corrected.apreXrm!!.reference.capturedLoadKg!!,0.0)
        assertEquals(corrected,f.storedOngoing().completedSets[key])
        val reopened = f.recreate(vm)
        assertEquals(corrected,reopened.uiState.value.completedSets[key])
        assertNull(reopened.uiState.value.session!!.exercises.first().referenceTrainingMax)
        assertNull(reopened.uiState.value.session!!.exercises.first().reference1RM)
    }
    @Test fun videoVoiceCorrectionWithoutS3ReachesRealVmRoomAndRestart() = runBlocking { correction(false) }
    @Test fun videoVoiceCorrectionWithS3ReachesRealVmRoomAndRestart() = runBlocking { correction(true) }
    @Test fun s3ApplyRestartAndRevertUseRealDurableActions() = runBlocking {
        val (f,vm) = open()
        assertTrue(record(vm,2,150.0,12) is RecordSetResult.Created)
        val id = vm.uiState.value.calibrationDecisions.single().id
        suspend fun resolve(target: WorkoutViewModel,status: CalibrationStatus) {
            target.resolveCalibrationDecision(id,status)
            withTimeout(20_000) { target.uiState.first { it.calibrationActionId == null } }
            assertNull(target.uiState.value.calibrationError)
            assertEquals(status,f.storedOngoing().calibrationDecisions.single().status)
        }
        resolve(vm,CalibrationStatus.APPLIED)
        val reopened = f.recreate(vm)
        val exercise = reopened.uiState.value.session!!.exercises.first()
        assertEquals(155.0,calibrationLoadForSet(reopened.uiState.value,exercise,3,null)!!.weight,0.0)
        resolve(reopened,CalibrationStatus.REVERTED)
        assertEquals(150.0,calibrationLoadForSet(reopened.uiState.value,exercise,3,null)!!.weight,0.0)
        assertNull(f.storedOngoing().calibrationDecisions.single().decision.capacityKg)
    }
}
