package com.example.kpkn.screens.workout

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.ExerciseLoadReferenceStore
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import java.time.Instant
import com.example.kpkn.domain.training.Cal5TestSupport
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*

/**
 * CAL7 provisional harness. Materializes the published recipe, starts real live VMs and records every working set.
 * Exercises, logs and cursor transitions come from production; fixture dates/check-in are explicit athlete input.
 */
internal class Cal7ManagedRoomFixture(private val protocolId: String, private val mode: AutoregulationMode) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val programId = "cal7-$protocolId-${mode.name.lowercase()}"
    lateinit var repository: ProgramRepository
        private set
    val preparedRepository: ProgramRepository? get() = if (::repository.isInitialized) repository else null
    private val opened = mutableListOf<WorkoutViewModel>()
    private val detail = mutableListOf<ProgramDetailViewModel>()
    lateinit var manualSession: Session
        private set
    lateinit var manualExerciseId: String
        private set
    val foreignExerciseId = "cal7-unrelated-seated-press"
    val explicitManualKg = 41.25
    val completedLogs = mutableListOf<WorkoutLog>()
    val program: Program get() = checkNotNull(repository.getProgramById(programId))
    val weeks: List<ProgramWeek> get() = Cal5TestSupport.weeksOf(program)

    suspend fun prepare(manualWeekIndex: Int = 0): Cal7ManagedRoomFixture {
        CatalogCompositionTestSupport.install()
        repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        val materialized = Cal5TestSupport.materialize(Cal5TestSupport.recipeOf(protocolId), mode = mode, id = programId)
        val macro = materialized.macrocycles.first()
        val block = macro.blocks.first()
        val meso = block.mesocycles.first()
        val week = meso.weeks.first()
        val run = ProgramRunState("$programId-run", weekId = week.id,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week.id), macrocycleId = macro.id,
            blockId = block.id, mesocycleId = meso.id)
        val allWeeks = Cal5TestSupport.weeksOf(materialized)
        val manualWeek = allWeeks[manualWeekIndex]
        val originalSession = manualWeek.sessions.first { session -> session.allExercises().none {
            it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == CatalogIds.OHP } }
        val manualExercise = originalSession.allExercises().first { it.slotRole == SlotRole.T3_ACCESSORY && it.sets.isNotEmpty() }
        manualExerciseId = manualExercise.id
        fun fixed(exercise: Exercise) = if (exercise.id != manualExerciseId) exercise else exercise.copy(
            sets = exercise.sets.mapIndexed { i, set -> if (i == 0) set.copy(weight = explicitManualKg,
                targetPercentageRM = null, loadBasis = null) else set })
        manualSession = originalSession.copy(exercises = originalSession.exercises.map(::fixed), parts = originalSession.parts.map {
            it.copy(exercises = it.exercises.map(::fixed)) })
        val customized = materialized.copy(runState = run, manualSessionOverrides = listOf(
            ManualSessionOverride(manualSession.id, weekId = manualWeek.id, reason = "Carga fijada por el atleta CAL7")),
            macrocycles = materialized.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = meso.weeks.map { week ->
                    week.copy(sessions = week.sessions.map { if (it.id == manualSession.id) manualSession else it })
                }) })
            }) })
        val unrelated = PlanLoadReference(PlanLoadReferenceKind.EXERCISE_TM, CatalogIds.OHP_SEATED,
            quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL, state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 45.0, capturedAtMs = 1L, metadata = PlanLoadReferenceMetadata(origin = "MANUAL",
                sourceId = "cal7-unrelated", reason = "Variante independiente"))
        val seeded = ExerciseLoadReferenceStore.appendCapture(ExerciseTrainingMax.migrate(customized), setOf(foreignExerciseId), unrelated)
        repository.addProgram(seeded)
        repository.flushPendingWrites()
        repository.updateActiveProgramState(ActiveProgramState(programId, status = ProgramStatus.ACTIVE,
            currentWeekId = week.id, currentWeekInstanceId = run.weekInstanceId,
            currentMacrocycleId = macro.id, currentBlockId = block.id, currentMesocycleId = meso.id))
        return this
    }

    suspend fun open(session: Session): WorkoutViewModel {
        assertNull("A pending human decision must be resolved before opening the next session", program.runState?.pendingAction)
        val vm = WorkoutViewModel(context, programId, session.id, WorkoutRestAlertManager(context))
        opened += vm
        withTimeout(60_000) { vm.uiState.first { it.session != null && !it.isStartingWorkout } }
        withTimeout(30_000) { repository.ongoingWorkout.first { it != null } }
        assertEquals(session.id, vm.uiState.value.session!!.id)
        return vm
    }

    /** Explicit athlete input in the fixture: accessories without author kg use 40kg, never a fabricated persisted reference. */
    suspend fun completeAndFinish(vm: WorkoutViewModel, completionInstant: Instant,
        repsOf: (Exercise, ExerciseSet) -> Int = { _, set -> set.amrapMinimumReps ?: set.targetReps ?: 8 }): WorkoutLog {
        val session = vm.uiState.value.session!!
        session.allExercises().forEachIndexed { exerciseIndex, exercise ->
            exercise.sets.forEachIndexed setLoop@ { setIndex, set ->
                if (set.isEmptySlot) return@setLoop
                vm.selectExercise(exerciseIndex)
                vm.jumpToSet(setIndex)
                val reps = repsOf(exercise, set)
                val kg = set.weight ?: 40.0
                val result = vm.recordSetV2(kg, reps.toDouble(), 1.0, SetAdvancedFeedback(rir = 1),
                    LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = setIndex,
                    expectedExerciseId = exercise.id, expectedSetIdx = setIndex)
                assertTrue("$protocolId/$mode ${exercise.name} S${setIndex + 1}: $result", result is RecordSetResult.Created)
            }
        }
        val recorded = vm.uiState.value.completedSets.values.map { it.id }.toSet()
        // Match the explicit, healthy athlete check-in to this real completion date so recovery does not
        // accidentally inherit missing or out-of-order readiness data from a different day.
        val readinessDate = completionInstant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        AugeRepository.getInstance(context).saveWellbeingLog(DailyWellbeingLog(
            "cal7-ready-$programId-${completionInstant.toEpochMilli()}", readinessDate.toString(),
            sleepQuality = 5, sleepHours = 8.0, stressLevel = 1, doms = 1, motivation = 5,
            manualNeuralBattery = 100, manualSpinalBattery = 100, manualMuscularBattery = 100,
            manualBatteryAnchorMs = completionInstant.toEpochMilli()))
        vm.finishWorkout("Recorrido CAL7 $protocolId/$mode", 5, SessionClosingFeedback(5, 0, 0, 0, emptyList(),
            completionInstantIso = completionInstant.toString()))
        val completionDate = completionInstant.toString()
        val log = withTimeout(60_000) {
            repository.history.first { logs -> logs.any { it.programId == programId && it.date == completionDate } }
                .single { it.programId == programId && it.date == completionDate }
        }
        withTimeout(20_000) { vm.uiState.first { it.logAlreadyWrittenId != null || it.isComplete || it.showVolumeAdvanceModal } }
        if (vm.uiState.value.showVolumeAdvanceModal) vm.dismissVolumeAdvance()
        withTimeout(60_000) { repository.ongoingWorkout.first { it == null } }
        assertEquals(completionInstant.toString(), log.date)
        assertEquals(recorded, log.completedExercises.flatMap { it.sets }.map { it.id }.toSet())
        assertEquals(programId, log.programId)
        assertEquals(session.id, log.sessionId)
        vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        opened.remove(vm)
        completedLogs += log
        return log
    }

    suspend fun resolveHumanGate(acceptPerformance: Boolean) {
        val pending = program.runState?.pendingAction ?: return
        val vm = ProgramDetailViewModel(programId).also { detail += it }
        withTimeout(20_000) { vm.program.first { it != null } }
        when (pending.type) {
            PendingProgramActionType.CONFIRM_AUTOREGULATION -> {
                if (acceptPerformance) {
                    // Explicitly accept only the method's TM proposals; unrelated recovery advice is declined.
                    pending.proposals.filter { it.kind == AutoregulationProposalKind.ADJUST_TM }.forEach { proposal ->
                        vm.acceptAutoregulation(proposal)
                        withTimeout(20_000) { repository.programs.first { programs -> programs.single { it.id == programId }
                            .runState?.pendingAction?.proposals?.contains(proposal) != true } }
                    }
                }
                if (program.runState?.pendingAction?.type == PendingProgramActionType.CONFIRM_AUTOREGULATION) vm.rejectAutoregulation()
            }
            PendingProgramActionType.CONFIRM_DELOAD -> vm.rejectPendingDeload()
            PendingProgramActionType.CONFIRM_1RM_TEST -> error("Unexpected 1RM gate before finishing the selected week/wave")
        }
        withTimeout(20_000) { repository.programs.first { programs -> programs.single { it.id == programId }.runState?.pendingAction == null } }
        repository.flushPendingWrites()
    }

    suspend fun close() {
        val workoutViewModels = opened.toList()
        val detailViewModels = detail.toList()
        opened.clear()
        detail.clear()
        workoutViewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        detailViewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        preparedRepository?.flushPendingWrites()
    }
}
