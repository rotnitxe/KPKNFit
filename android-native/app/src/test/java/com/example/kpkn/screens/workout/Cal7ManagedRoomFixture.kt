package com.example.kpkn.screens.workout

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.Cal5TestSupport
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*

/**
 * CAL7 provisional harness. Materializes the published recipe, starts real live VMs and records every working set.
 * The 15 mode journeys will be completed after APRE/CAL5b/PLAN-OBS are integrated; no synthetic workout logs.
 */
internal class Cal7ManagedRoomFixture(private val protocolId: String, private val mode: AutoregulationMode) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val programId = "cal7-$protocolId-${mode.name.lowercase()}"
    lateinit var repository: ProgramRepository
        private set
    private val opened = mutableListOf<WorkoutViewModel>()
    val program: Program get() = checkNotNull(repository.getProgramById(programId))
    val weeks: List<ProgramWeek> get() = Cal5TestSupport.weeksOf(program)

    suspend fun prepare(): Cal7ManagedRoomFixture {
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
        repository.addProgram(ExerciseTrainingMax.migrate(materialized.copy(structure = ProgramStructure.COMPLEX, runState = run)))
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
    suspend fun completeAndFinish(vm: WorkoutViewModel): WorkoutLog {
        val session = vm.uiState.value.session!!
        session.allExercises().forEachIndexed { exerciseIndex, exercise ->
            exercise.sets.forEachIndexed setLoop@ { setIndex, set ->
                if (set.isEmptySlot) return@setLoop
                vm.selectExercise(exerciseIndex)
                vm.jumpToSet(setIndex)
                val reps = if (set.purpose.isAmrap) (set.amrapMinimumReps ?: set.targetReps ?: 5) + 3 else set.targetReps ?: 8
                val kg = set.weight ?: 40.0
                val result = vm.recordSetV2(kg, reps.toDouble(), 1.0, SetAdvancedFeedback(rir = 1),
                    LoadModeV2.LOAD, UnitModeV2.REPS, 75.0, setIdxOverride = setIndex,
                    expectedExerciseId = exercise.id, expectedSetIdx = setIndex)
                assertTrue("$protocolId/$mode ${exercise.name} S${setIndex + 1}: $result", result is RecordSetResult.Created)
            }
        }
        val recorded = vm.uiState.value.completedSets.values.map { it.id }.toSet()
        vm.finishWorkout("Recorrido CAL7 $protocolId/$mode", 5, SessionClosingFeedback(5, 0, 0, 0, emptyList()))
        val log = withTimeout(60_000) { repository.history.first { logs -> logs.any { it.sessionId == session.id } } }
            .last { it.sessionId == session.id }
        withTimeout(60_000) { repository.ongoingWorkout.first { it == null } }
        assertEquals(recorded, log.completedExercises.flatMap { it.sets }.map { it.id }.toSet())
        assertEquals(programId, log.programId)
        assertEquals(session.id, log.sessionId)
        vm.viewModelScope.cancel()
        opened.remove(vm)
        return log
    }

    fun close() { opened.forEach { it.viewModelScope.cancel() }; opened.clear() }
}
