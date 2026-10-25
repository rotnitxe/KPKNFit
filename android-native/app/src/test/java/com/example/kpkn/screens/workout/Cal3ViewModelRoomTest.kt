package com.example.kpkn.screens.workout

import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
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
class Cal3ViewModelRoomTest {
    @get:Rule val room = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetTrainingObservationRepository() }
    @After fun cleanup() {
        try { fixture?.close(); LiveSessionVmFixture.resetTrainingObservationRepository(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance() }
        finally { Dispatchers.resetMain() }
    }
    private suspend fun open(scope: CalibrationScope = CalibrationScope.SESSION,
        simpleCyclic: Boolean = false): Pair<LiveSessionVmFixture, WorkoutViewModel> {
        val session = cal3Fixture(scope = scope).state.session!!
        val f = LiveSessionVmFixture(session, simpleCyclic).prepare().also { fixture = it }
        return f to f.open()
    }
    private suspend fun record(vm: WorkoutViewModel) = vm.recordSetV2(69.0, 10.0, 1.0,
        SetAdvancedFeedback(rir = 1), LoadModeV2.LOAD, UnitModeV2.REPS, 75.0,
        setIdxOverride = 0, expectedExerciseId = "cal3-exercise", expectedSetIdx = 0)
    private suspend fun resolve(vm: WorkoutViewModel, status: CalibrationStatus) {
        val id = vm.uiState.value.calibrationDecisions.last().id
        vm.resolveCalibrationDecision(id, status)
        withTimeout(20_000) { vm.uiState.first { it.calibrationActionId == null } }
        assertNull(vm.uiState.value.calibrationError)
        assertEquals(status, vm.uiState.value.calibrationDecisions.last().status)
    }
    @Test fun optionalOriginalContextSurvivesRecreationButIsNotCopiedIntoTheNextActivity() = runBlocking {
        val (f, vm) = open()
        val authored = vm.uiState.value.session
        vm.setSessionNotes("Contexto original de hoy", flush = true)
        vm.setRecordedSport("BOXING")
        withTimeout(20_000) { f.repository.ongoingWorkout.first {
            it?.recordedSportId == "BOXING" && it.sessionNotes == "Contexto original de hoy"
        } }
        val restored = f.recreate(vm)
        assertEquals("BOXING", restored.uiState.value.recordedSportId)
        assertEquals("Contexto original de hoy", restored.uiState.value.sessionNotes)
        assertEquals(authored, restored.uiState.value.session)
        assertTrue(record(restored) is RecordSetResult.Created)
        restored.finishWorkout("Cierre original", 0, SessionClosingFeedback(discomforts = emptyList()))
        val log = withTimeout(60_000) { f.repository.history.first { logs -> logs.any { it.sessionId == f.sessionId } } }
            .single { it.sessionId == f.sessionId }
        assertNull("El cierre no fabrica una valoración de fatiga", log.fatigueLevel)
        assertNull(log.clarityRating)
        assertEquals("BOXING", log.recordedSportId)
        assertEquals("Contexto original de hoy", log.sessionNotes)
        withTimeout(60_000) { f.repository.ongoingWorkout.first { it == null } }
        restored.viewModelScope.cancel()
        val next = f.open()
        assertNull("Una nueva actividad no hereda una declaración anterior", next.uiState.value.recordedSportId)
        assertEquals("", next.uiState.value.sessionNotes)
        assertEquals(log, f.repository.history.value.single { it.id == log.id })
    }
    @Test fun realViewModelAppliesPersistsReopensAndRevertsWithoutEditingAnotherExercise() = runBlocking {
        val (f, vm) = open()
        assertTrue(record(vm) is RecordSetResult.Created)
        val otherBefore = vm.uiState.value.session!!.exercises.last()
        resolve(vm, CalibrationStatus.APPLIED)
        val ex = vm.uiState.value.session!!.exercises.first()
        assertEquals(62.5, vm.getWeightSuggestionWithAutoRegulation(ex, 1)!!.suggestedWeight, 0.0)
        val stored = f.storedOngoing()
        assertEquals(CalibrationStatus.APPLIED, stored.calibrationDecisions.single().status)
        val restored = f.recreate(vm)
        assertEquals(0, restored.uiState.value.currentSetIdx)
        assertTrue(restored.uiState.value.isRestMinimized)
        val restoredExercise = restored.uiState.value.session!!.exercises.first()
        assertEquals(62.5, restored.getWeightSuggestionWithAutoRegulation(restoredExercise, 1)!!.suggestedWeight, 0.0)
        assertEquals("Calibración del protocolo activo", restored.getWeightSuggestionWithAutoRegulation(restoredExercise, 1)!!.reason)
        resolve(restored, CalibrationStatus.REVERTED)
        assertEquals(60.0, restored.getWeightSuggestionWithAutoRegulation(restoredExercise, 1)!!.suggestedWeight, 0.0)
        assertEquals(otherBefore, restored.uiState.value.session!!.exercises.last())
        assertEquals(1, f.storedOngoing().completedSets.size)
    }
    @Test fun nextSessionsReferenceIsIgnoredTodayEvenAfterRecreationAndConsumedOnANewExecution() = runBlocking {
        val (f, vm) = open(CalibrationScope.NEXT_SESSIONS, simpleCyclic = true)
        assertTrue(record(vm) is RecordSetResult.Created)
        resolve(vm, CalibrationStatus.APPLIED)
        val today = f.recreate(vm)
        assertTrue(today.uiState.value.acceptedCalibrationReferences.isEmpty())
        val exToday = today.uiState.value.session!!.exercises.first()
        assertNull(acceptedCalibrationLoad(today.uiState.value, exToday, 1, null, System.currentTimeMillis()))
        // La referencia solo vale para la sesión siguiente cuando esta se termina (una sesión descartada no la deja).
        today.finishWorkout("", 5, SessionClosingFeedback(discomforts = emptyList()))
        withTimeout(60_000) { f.repository.history.first { logs -> logs.any { it.sessionId == f.sessionId } } }
        withTimeout(60_000) { f.repository.ongoingWorkout.first { it == null } }
        today.viewModelScope.cancel()
        val next = f.open()
        val ex = next.uiState.value.session!!.exercises.first()
        assertEquals(65.0, next.getWeightSuggestionWithAutoRegulation(ex, 1)!!.suggestedWeight, 0.0)
        assertTrue(next.uiState.value.calibrationDecisions.isEmpty())
        assertEquals("Referencia de calibración aceptada", next.getWeightSuggestionWithAutoRegulation(ex, 1)!!.reason)
    }
    @Test fun discardingTheSessionDropsItsDecisionsSoTheyNeverBecomeAReference() = runBlocking {
        val (f, vm) = open(CalibrationScope.NEXT_SESSIONS)
        assertTrue(record(vm) is RecordSetResult.Created)
        resolve(vm, CalibrationStatus.APPLIED)
        assertEquals(1, f.repository.calibrationDecisions.all().size)
        assertEquals(1, f.repository.calibrationDecisions.accepted(System.currentTimeMillis()).size)
        f.repository.clearOngoingWorkoutAndFlush()
        assertTrue(f.repository.calibrationDecisions.all().isEmpty())
        assertTrue(f.repository.calibrationDecisions.accepted(System.currentTimeMillis()).isEmpty())
    }
    @Test fun clearingTheOngoingRowAfterTheLogWasWrittenKeepsTheFinishedDecisions() = runBlocking {
        val (f, vm) = open(CalibrationScope.NEXT_SESSIONS)
        assertTrue(record(vm) is RecordSetResult.Created)
        resolve(vm, CalibrationStatus.APPLIED)
        assertEquals(WorkoutPersistResult.Ok, f.repository.updateOngoingWorkoutAndFlush { it.copy(logAlreadyWrittenId = "written-log") })
        f.repository.clearOngoingWorkoutAndFlush()
        assertEquals(1, f.repository.calibrationDecisions.all().size)
    }
    @Test fun failedApplyKeepsProposedLoadsAndItsErrorCanBeRetried() = runBlocking {
        val (f, vm) = open()
        assertTrue(record(vm) is RecordSetResult.Created)
        val id = vm.uiState.value.calibrationDecisions.single().id
        val db = f.repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_cal3_action BEFORE INSERT ON calibration_decisions BEGIN SELECT RAISE(ABORT, 'cal3 action'); END")
        vm.resolveCalibrationDecision(id, CalibrationStatus.APPLIED)
        withTimeout(20_000) { vm.uiState.first { it.calibrationActionId == null } }
        assertNotNull(vm.uiState.value.calibrationError)
        assertEquals(CalibrationStatus.PROPOSED, vm.uiState.value.calibrationDecisions.single().status)
        assertEquals(CalibrationStatus.PROPOSED, f.storedOngoing().calibrationDecisions.single().status)
        db.execSQL("DROP TRIGGER fail_cal3_action")
        resolve(vm, CalibrationStatus.APPLIED)
        resolve(vm, CalibrationStatus.APPLIED)
        assertEquals(62.5, vm.getWeightSuggestionWithAutoRegulation(vm.uiState.value.session!!.exercises.first(), 1)!!.suggestedWeight, 0.0)
    }
    @Test fun correctingTheSourceThroughTheSharedVideoVoiceLaneReplacesItsDecisionAtomically() = runBlocking {
        val (f, vm) = open()
        assertTrue(record(vm) is RecordSetResult.Created)
        resolve(vm, CalibrationStatus.APPLIED)
        vm.patchCompletedSet("cal3-exercise_0", CompletedSetPatch(edit = com.example.kpkn.services.workout.VoiceSetEditPatch(metricValue = 9)))
        withTimeout(20_000) { vm.uiState.first { it.calibrationDecisions.size == 2 } }
        assertEquals(listOf(CalibrationStatus.SUPERSEDED, CalibrationStatus.PROPOSED), vm.uiState.value.calibrationDecisions.map { it.status })
        assertTrue(f.storedOngoing().calibrationDecisions.last().decision.affectedSets.all { it.beforeKg == 60.0 })
        assertEquals(60.0, vm.getWeightSuggestionWithAutoRegulation(vm.uiState.value.session!!.exercises.first(), 1)!!.suggestedWeight, 0.0)
    }
    @Test fun finishingBindsTheDecisionsToTheRealWorkoutLogAndKeepsTheirResolution() = runBlocking {
        val (f, vm) = open()
        assertTrue(record(vm) is RecordSetResult.Created)
        resolve(vm, CalibrationStatus.APPLIED)
        vm.finishWorkout("", 5, SessionClosingFeedback(discomforts = emptyList()))
        val log = withTimeout(60_000) { f.repository.history.first { logs -> logs.any { it.sessionId == f.sessionId } } }
            .single { it.sessionId == f.sessionId }
        val row = log.calibrationDecisions.single()
        assertEquals(log.id, row.sessionLogId)
        assertEquals(CalibrationStatus.APPLIED, row.status)
        assertEquals(listOf(row), f.repository.calibrationDecisions.forSession(log.id))
        assertTrue(calibrationSummaryLines(log.calibrationDecisions).single().contains("3 series calibradas"))
    }
}
