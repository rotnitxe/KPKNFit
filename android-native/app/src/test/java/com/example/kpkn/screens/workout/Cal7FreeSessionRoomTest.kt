package com.example.kpkn.screens.workout

import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** CAL7: production ViewModel, recorder, durable decision, finish controller and Room; no mocked ports. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class Cal7FreeSessionRoomTest {
    @get:Rule val room = RoomTestRule()
    private val fixtures = mutableListOf<LiveSessionVmFixture>()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetAugeRepository() }
    @After fun cleanup() {
        try {
            fixtures.forEach { it.close() }
            LiveSessionVmFixture.resetAugeRepository()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
        } finally { Dispatchers.resetMain() }
    }

    private suspend fun open(session: Session = cal3Fixture().state.session!!): Pair<LiveSessionVmFixture, WorkoutViewModel> {
        // An athlete-authored, cyclic session with no recipe or managed TM progression.
        val fixture = LiveSessionVmFixture(session, simpleCyclic = true).prepare()
        fixtures += fixture
        assertNull(fixture.repository.getProgramById(fixture.programId)!!.sourceRecipe)
        return fixture to fixture.open()
    }
    private suspend fun record(vm: WorkoutViewModel, exerciseIndex: Int, setIndex: Int, kg: Double,
        reps: Int, rir: Int): CompletedSet {
        vm.selectExercise(exerciseIndex)
        vm.jumpToSet(setIndex)
        val exercise = vm.uiState.value.session!!.allExercises()[exerciseIndex]
        val result = vm.recordSetV2(kg, reps.toDouble(), rir.toDouble(), SetAdvancedFeedback(rir = rir),
            LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = setIndex,
            expectedExerciseId = exercise.id, expectedSetIdx = setIndex)
        assertTrue("Real recorder rejected $exerciseIndex/$setIndex: $result", result is RecordSetResult.Created)
        return vm.uiState.value.completedSets.getValue(workoutSetKey(exercise.id, setIndex))
    }
    private suspend fun resolve(vm: WorkoutViewModel, sourceId: String, status: CalibrationStatus) {
        val decisionId = vm.uiState.value.calibrationDecisions.last { it.sourceSetId == sourceId }.id
        vm.resolveCalibrationDecision(decisionId, status)
        withTimeout(20_000) { vm.uiState.first { it.calibrationActionId == null } }
        assertNull(vm.uiState.value.calibrationError)
        assertEquals(status, vm.uiState.value.calibrationDecisions.single { it.id == decisionId }.status)
    }
    private suspend fun finish(fixture: LiveSessionVmFixture, vm: WorkoutViewModel): WorkoutLog {
        vm.finishWorkout("Recorrido CAL7", 5, SessionClosingFeedback(5, 0, 0, 0, emptyList()))
        val log = withTimeout(60_000) { fixture.repository.history.first { logs -> logs.any { it.sessionId == fixture.sessionId } } }
            .single { it.sessionId == fixture.sessionId }
        withTimeout(60_000) { fixture.repository.ongoingWorkout.first { it == null } }
        assertEquals(log.calibrationDecisions, fixture.repository.calibrationDecisions.forSession(log.id))
        return log
    }

    @Test fun registerApplyRevertFinishAndTheNextExecutionConsumesOnlyTheAcceptedReference() = runBlocking {
        val base = cal3Fixture().state.session!!
        val session = base.copy(exercises = base.exercises.mapIndexed { index, exercise ->
            if (index != 0) exercise else exercise.copy(sets = exercise.sets.mapIndexed { setIndex, set ->
                if (setIndex == 1) set.copy(purpose = SetPurpose.CALIBRATOR, calibrationScope = CalibrationScope.NEXT_SESSIONS,
                    amrapMinimumReps = 6, targetReps = 6, weight = 69.0) else set
            })
        })
        val (fixture, vm) = open(session)
        val otherBefore = vm.uiState.value.session!!.exercises.last()
        val first = record(vm, 0, 0, 69.0, 10, 1)
        val recordedBefore = vm.uiState.value.completedSets
        resolve(vm, first.id, CalibrationStatus.APPLIED)
        var exercise = vm.uiState.value.session!!.exercises.first()
        assertEquals(62.5, vm.getWeightSuggestionWithAutoRegulation(exercise, 2)!!.suggestedWeight, 0.0)
        val reopened = fixture.recreate(vm)
        resolve(reopened, first.id, CalibrationStatus.REVERTED)
        exercise = reopened.uiState.value.session!!.exercises.first()
        assertEquals(60.0, reopened.getWeightSuggestionWithAutoRegulation(exercise, 2)!!.suggestedWeight, 0.0)
        assertEquals(recordedBefore, reopened.uiState.value.completedSets)
        assertEquals(otherBefore, reopened.uiState.value.session!!.exercises.last())
        val future = record(reopened, 0, 1, 69.0, 10, 1)
        resolve(reopened, future.id, CalibrationStatus.APPLIED)
        assertEquals("NEXT_SESSIONS keeps today's prescription", 60.0,
            reopened.getWeightSuggestionWithAutoRegulation(exercise, 2)!!.suggestedWeight, 0.0)
        record(reopened, 0, 2, 60.0, 12, 2)
        record(reopened, 0, 3, 60.0, 12, 2)
        record(reopened, 1, 0, 40.0, 8, 2)
        val log = finish(fixture, reopened)
        assertEquals(5, log.completedExercises.sumOf { it.sets.size })
        assertEquals(listOf(CalibrationStatus.REVERTED, CalibrationStatus.APPLIED), log.calibrationDecisions.map { it.status })
        assertTrue(calibrationSummaryLines(log.calibrationDecisions).any { it.contains("Revertida") })
        assertTrue(calibrationSummaryLines(log.calibrationDecisions).any { it.contains("próxima sesión") })
        reopened.viewModelScope.cancel()
        val next = fixture.open()
        val nextExercise = next.uiState.value.session!!.exercises.first()
        assertEquals(65.0, next.getWeightSuggestionWithAutoRegulation(nextExercise, 2)!!.suggestedWeight, 0.0)
        assertEquals("Referencia de calibración aceptada", next.getWeightSuggestionWithAutoRegulation(nextExercise, 2)!!.reason)
        assertTrue(next.uiState.value.calibrationDecisions.isEmpty())
        assertEquals(otherBefore, next.uiState.value.session!!.exercises.last())
        assertEquals(40.0, next.uiState.value.session!!.exercises.last().sets.single().weight!!, 0.0)
    }

    @Test fun cancelThroughTheViewModelRemovesTheAppliedDecisionAndLeavesNoNextSessionReference() = runBlocking {
        val (fixture, vm) = open(cal3Fixture(scope = CalibrationScope.NEXT_SESSIONS).state.session!!)
        val poolBefore = fixture.repository.getProgramById(fixture.programId)!!.exerciseLoadReferences
        val source = record(vm, 0, 0, 69.0, 10, 1)
        resolve(vm, source.id, CalibrationStatus.APPLIED)
        assertEquals(1, fixture.repository.calibrationDecisions.all().size)
        vm.cancelWorkout()
        withTimeout(30_000) { vm.uiState.first { it.wasCancelled } }
        assertNull(vm.uiState.value.cancellationError)
        assertNull(fixture.repository.ongoingWorkout.value)
        assertTrue(fixture.repository.calibrationDecisions.all().isEmpty())
        assertTrue(fixture.repository.history.value.isEmpty())
        assertEquals(poolBefore, fixture.repository.getProgramById(fixture.programId)!!.exerciseLoadReferences)
        val next = fixture.open()
        val exercise = next.uiState.value.session!!.exercises.first()
        assertTrue(next.uiState.value.acceptedCalibrationReferences.isEmpty())
        assertEquals(60.0, next.getWeightSuggestionWithAutoRegulation(exercise, 2)!!.suggestedWeight, 0.0)
        assertTrue(next.uiState.value.calibrationDecisions.isEmpty())
    }

    @Test fun ringsCountTheRecordedWorkEquallyForNormalAndCalibratorPurposes() = runBlocking {
        suspend fun completed(purpose: SetPurpose): WorkoutLog {
            val original = cal3Fixture().state.session!!
            val session = original.copy(exercises = listOf(original.exercises.first().copy(sets = listOf(
                original.exercises.first().sets.first().copy(purpose = purpose, targetReps = 10)))))
            val (fixture, vm) = open(session)
            val source = record(vm, 0, 0, 69.0, 10, 1)
            if (purpose == SetPurpose.CALIBRATOR) resolve(vm, source.id, CalibrationStatus.KEPT)
            else assertTrue(vm.uiState.value.calibrationDecisions.isEmpty())
            val log = finish(fixture, vm)
            fixture.close()
            return log
        }
        val normal = completed(SetPurpose.NONE)
        val calibrator = completed(SetPurpose.CALIBRATOR)
        val normalSet = normal.completedExercises.single().sets.single()
        val calibratorSet = calibrator.completedExercises.single().sets.single()
        assertEquals(SetPurpose.NONE, normalSet.purpose)
        assertEquals(SetPurpose.CALIBRATOR, calibratorSet.purpose)
        assertEquals(normalSet.weight, calibratorSet.weight, 0.0)
        assertEquals(normalSet.reps, calibratorSet.reps)
        assertEquals(normalSet.rir, calibratorSet.rir)
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val muscle = ExerciseMuscleInfo("cal3-barbell-squat", "Sentadilla", equipment = "barra", efc = 4.2,
            cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0)))
        val db = listOf("cal3-exercise", "cal3-barbell-squat", "cal3-squat").associateWith { muscle }
        fun preview(log: WorkoutLog) = AugeRecoveryEngine.previewPostSessionBatteries(emptyList(),
            log.copy(date = now.toString(), durationMinutes = 20), wellbeing = null, settings = Settings(),
            exerciseDb = db, nowOverrideMs = now.toEpochMilli())
        val ordinaryRings = preview(normal)
        val calibrationRings = preview(calibrator)
        assertEquals(ordinaryRings.neural, calibrationRings.neural)
        assertEquals(ordinaryRings.spinal, calibrationRings.spinal)
        assertEquals(ordinaryRings.muscular, calibrationRings.muscular)
        assertEquals(ordinaryRings.globalCnsDrain, calibrationRings.globalCnsDrain)
        assertEquals(ordinaryRings.globalSpinalDrain, calibrationRings.globalSpinalDrain)
        assertEquals(ordinaryRings.globalMuscularDrain, calibrationRings.globalMuscularDrain)
        assertEquals(ordinaryRings.perMuscle, calibrationRings.perMuscle)
        assertTrue("A real squat set must contribute work rather than produce a vacuous equality",
            ordinaryRings.globalCnsDrain > 0 || ordinaryRings.globalMuscularDrain > 0 || ordinaryRings.globalSpinalDrain > 0)
    }
}
