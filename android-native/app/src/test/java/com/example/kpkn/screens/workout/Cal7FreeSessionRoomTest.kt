package com.example.kpkn.screens.workout

import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.testsupport.awaitRepositoryScope
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
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetTrainingObservationRepository() }
    @After fun cleanup() {
        try {
            runBlocking {
                try { fixtures.forEach { it.closeAndJoin() } }
                finally {
                    val repositories = fixtures.mapNotNull { it.preparedRepository }.distinct()
                    try {
                        repositories.forEach { repository ->
                            try { repository.flushPendingWrites() }
                            finally { awaitRepositoryScope(repository) }
                        }
                    } finally {
                        try { LiveSessionVmFixture.resetTrainingObservationRepository() }
                        finally {
                            try { ProgramRepository.closeInstance() }
                            finally { KpknDatabase.closeInstance() }
                        }
                    }
                }
            }
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
        withTimeout(20_000) { vm.uiState.first { it.recordingSetKey == null } }
        val decisionId = vm.uiState.value.calibrationDecisions.last { it.sourceSetId == sourceId }.id
        val before = vm.uiState.value.calibrationDecisions.single { it.id == decisionId }.status
        assertNotEquals("La decisión debe cambiar al aplicar la acción del ViewModel", status, before)
        vm.resolveCalibrationDecision(decisionId, status)
        withTimeout(20_000) {
            vm.uiState.first { state ->
                state.calibrationActionId == null &&
                    state.calibrationDecisions.singleOrNull { it.id == decisionId }?.status == status
            }
        }
        assertNull(vm.uiState.value.calibrationError)
        assertEquals(status, vm.uiState.value.calibrationDecisions.single { it.id == decisionId }.status)
    }
    private suspend fun finish(
        fixture: LiveSessionVmFixture,
        vm: WorkoutViewModel,
        scenario: String = "CAL7",
    ): WorkoutLog {
        val sessionId = vm.uiState.value.session!!.id
        withTimeout(20_000) {
            vm.uiState.first { it.recordingSetKey == null && it.calibrationActionId == null }
        }
        // Un aviso de avance de volumen que ya abrió la sesión debe resolverse por la API de la pantalla.
        if (vm.uiState.value.showVolumeAdvanceModal) vm.dismissVolumeAdvance()
        withTimeout(20_000) {
            vm.uiState.first { !it.showVolumeAdvanceModal && it.pendingVolumeAdvances.isEmpty() }
        }
        val completionInstant = Instant.now()
        val completionDate = completionInstant.toString()
        vm.finishWorkout("Recorrido CAL7", 5, SessionClosingFeedback(discomforts = emptyList(),
            completionInstantIso = completionDate))
        try {
            withTimeout(30_000) {
                vm.uiState.first {
                    it.logAlreadyWrittenId != null || it.isComplete || it.showVolumeAdvanceModal || it.finishWarning != null
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            val state = vm.uiState.value
            val viewModelJob = vm.viewModelScope.coroutineContext[Job]
            val diagnostic = buildString {
                append("Timeout al cerrar $scenario; programa=${fixture.programId}, sesion=$sessionId; ")
                append("isFinishingWorkout=${state.isFinishingWorkout}, ")
                append("isStartingWorkout=${state.isStartingWorkout}, ")
                append("isCancellingWorkout=${state.isCancellingWorkout}, wasCancelled=${state.wasCancelled}, ")
                append("recordingSetKey=${state.recordingSetKey}, calibrationActionId=${state.calibrationActionId}, ")
                append("logAlreadyWrittenId=${state.logAlreadyWrittenId}, isComplete=${state.isComplete}, ")
                append("showVolumeAdvanceModal=${state.showVolumeAdvanceModal}, ")
                append("pendingVolumeAdvances=${state.pendingVolumeAdvances.size}, ")
                append("finishWarning=${state.finishWarning?.replace('\n', ' ')}, ")
                append("startPersistenceErrorPresent=${state.startPersistenceError != null}, ")
                append("pendingOngoingConflict=${state.pendingOngoingConflict != null}, ")
                append("pendingOngoingCorrupt=${state.pendingOngoingCorrupt}; ")
                append("viewModelJob(active=${viewModelJob?.isActive}, cancelled=${viewModelJob?.isCancelled}, ")
                append("completed=${viewModelJob?.isCompleted}, children=${viewModelJob?.children?.count()})")
            }
            throw AssertionError(diagnostic, timeout)
        }
        assertNull("El cierre real no debe dejar un aviso de error", vm.uiState.value.finishWarning)
        assertNotNull("El ViewModel debe publicar el ID del log durable", vm.uiState.value.logAlreadyWrittenId)
        val log = withTimeout(60_000) {
            fixture.repository.history.first { logs -> logs.any {
                it.programId == fixture.programId && it.date == completionDate
            } }.single { it.programId == fixture.programId && it.date == completionDate }
        }
        if (vm.uiState.value.showVolumeAdvanceModal) vm.dismissVolumeAdvance()
        withTimeout(60_000) { fixture.repository.ongoingWorkout.first { it == null } }
        assertEquals(fixture.programId, log.programId)
        assertEquals(sessionId, log.sessionId)
        assertEquals(completionDate, log.date)
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
        val log = finish(fixture, reopened, "sesion libre con calibrador resuelto")
        assertEquals(5, log.completedExercises.sumOf { it.sets.size })
        assertEquals(listOf(CalibrationStatus.REVERTED, CalibrationStatus.APPLIED), log.calibrationDecisions.map { it.status })
        assertTrue(calibrationSummaryLines(log.calibrationDecisions).any { it.contains("Revertida") })
        assertTrue(calibrationSummaryLines(log.calibrationDecisions).any { it.contains("próxima sesión") })
        reopened.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
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
            // Cada prepare cierra Room; el DAO del singleton AUGE anterior ya no es válido.
            LiveSessionVmFixture.resetTrainingObservationRepository()
            val original = cal3Fixture().state.session!!
            val session = original.copy(exercises = listOf(original.exercises.first().copy(sets = listOf(
                original.exercises.first().sets.first().copy(purpose = purpose, targetReps = 10)))))
            val (fixture, vm) = open(session)
            val source = record(vm, 0, 0, 69.0, 10, 1)
            if (purpose == SetPurpose.CALIBRATOR) resolve(vm, source.id, CalibrationStatus.KEPT)
            else assertTrue(vm.uiState.value.calibrationDecisions.isEmpty())
            val scenario = if (purpose == SetPurpose.NONE) "RINGS normal" else "RINGS calibrador"
            val log = finish(fixture, vm, scenario)
            fixture.closeAndJoin()
            fixture.repository.flushPendingWrites()
            awaitRepositoryScope(fixture.repository)
            fixtures.remove(fixture)
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
    }
}
