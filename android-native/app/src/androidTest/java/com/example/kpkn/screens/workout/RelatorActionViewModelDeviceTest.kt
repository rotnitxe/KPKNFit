package com.example.kpkn.screens.workout

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real dispatcher, ViewModel and isolated in-memory Room; no Ack, controller or state-flow stubs. */
@RunWith(AndroidJUnit4::class)
class RelatorActionViewModelDeviceTest {
    private val suffix = System.nanoTime().toString()
    private val programId = "relator-vm-$suffix"
    private val sessionId = "relator-session-$suffix"
    private val exerciseId = "relator-slot-$suffix"
    private val store = ViewModelStore()
    private lateinit var context: Context
    private lateinit var repository: ProgramRepository
    private var viewModel: WorkoutViewModel? = null
    private val exercise = Exercise(
        id = exerciseId, name = "Press de prueba A", canonicalExerciseId = "relator-press-a",
        exerciseId = "relator-press-a", exerciseDbId = "relator-press-a",
        catalogDefinitionId = "relator-press", catalogConfigurationId = "relator-press-a",
        isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
        sets = listOf(ExerciseSet("preserved-series", targetReps = 6, targetRIR = 2,
            weight = 20.0, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)),
    )
    private val session = Session(sessionId, "Relator real", exercises = listOf(exercise))

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        repository = ProgramRepository.initForTests(context)
        // PerformanceRangeStore in the real VM must use the same isolated Room instance.
        databaseSingleton(repository.databaseForTests())
        withTimeout(30_000) { repository.isReady.first { it } }
        val program = Program(programId, "Relator synthetic", planWarmupConfig = emptyList(), macrocycles = listOf(
            Macrocycle("macro-$suffix", "Macro", blocks = listOf(
                Block("block-$suffix", "Block", mesocycles = listOf(
                    Mesocycle("meso-$suffix", "Meso", weeks = listOf(
                        ProgramWeek("week-$suffix", "Week", sessions = listOf(session)),
                    )),
                )),
            )),
        ))
        assertTrue(repository.addProgramNow(program).isSuccess)
        val vm = onMain {
            WorkoutViewModel(context, programId, sessionId, WorkoutRestAlertManager(context)).also {
                viewModel = it
                store.put("relator-$suffix", it)
            }
        }
        withTimeout(30_000) {
            vm.uiState.first {
                it.session?.id == sessionId && !it.isStartingWorkout &&
                    repository.ongoingWorkout.value?.session?.id == sessionId
            }
        }
        assertEquals(exerciseId, vm.visibleExercises(vm.uiState.value).single().id)
    }

    @After fun teardown() {
        runBlocking {
            try {
                viewModel?.let { vm ->
                    if (!vm.uiState.value.wasCancelled && repository.ongoingWorkout.value != null) {
                        onMain { vm.cancelWorkout() }
                        withTimeout(10_000) { vm.uiState.first { it.wasCancelled } }
                    }
                }
            } finally {
                onMain { store.clear(); viewModel = null }
                ProgramRepository.closeInstance()
                databaseSingleton(null)
            }
        }
    }

    private fun databaseSingleton(database: KpknDatabase?) {
        // Same binding as the established WorkoutPreparationDurabilityInstrumentedTest fixture.
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, database)
    }
    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }
    private fun vm() = checkNotNull(viewModel)
    private fun draft(weight: String, side: String = "left") = WorkoutSetDraft(
        weightText = weight, valueText = "7", intensityText = "3", loadMode = LoadModeV2.LOAD,
        selectedSide = side, partialReps = 1, reachedFailure = false, rom = 85, assistedReps = 1,
        amrapOverride = true, amrapMinimumReps = 6, amrapReachFailure = false, amrapReserveReps = 2,
        notes = "Keep the athlete's complete input", techniqueProgress = WorkoutTechniqueProgressDraft(), isDirty = true,
    )
    private fun adjust(side: String = "left") = RelatorAssistAction(
        RelatorAssistActionKind.ADJUST_LOAD, label = "baja un 5 %", span = "baja un 5 %",
        exerciseId = exerciseId, setIndex = 0, side = side, loadDeltaPercent = -5.0,
    )
    private fun undo(action: RelatorAssistAction) = action.copy(
        label = "Deshacer", weightKg = null, loadDeltaPercent = null, undoPreviousLoad = true,
    )
    private fun assertDraftContent(expected: WorkoutSetDraft, actual: WorkoutSetDraft?) {
        assertNotNull(actual)
        // updateSetDraft deliberately stamps every accepted write; all user-entered fields must match.
        assertEquals(expected, actual!!.copy(updatedAtMs = expected.updatedAtMs))
    }
    private fun assertRejected(ack: RelatorAssistAck) {
        assertFalse(ack.applied)
        assertFalse(ack.canUndoLoad)
        assertNull(ack.appliedWeightKg)
        assertNull(ack.previousWeightKg)
    }

    @Test fun draftFortyOneAdjustsToThirtyNineAndUndoRestoresEveryInputField() = runBlocking {
        onMain {
            val vm = vm()
            vm.updateSetDraft(exerciseId, 0, "left", draft("41"))
            val before = checkNotNull(vm.getSetDraft(exerciseId, 0, "left"))
            val action = adjust()
            assertNull(action.weightKg)
            val ack = vm.performRelatorAssist(action)
            assertTrue(ack.applied)
            assertEquals(39.0, checkNotNull(ack.appliedWeightKg), 0.0)
            assertEquals(41.0, checkNotNull(ack.previousWeightKg), 0.0)
            assertTrue(ack.canUndoLoad)
            assertEquals(ack, vm.relatorAssistAck.value)
            assertDraftContent(before.copy(weightText = "39"), vm.getSetDraft(exerciseId, 0, "left"))
            val restored = vm.performRelatorAssist(undo(action))
            assertTrue(restored.applied)
            assertFalse(restored.canUndoLoad)
            assertEquals(restored, vm.relatorAssistAck.value)
            assertDraftContent(before, vm.getSetDraft(exerciseId, 0, "left"))
            assertTrue(vm.uiState.value.completedSets.isEmpty())
        }
    }

    @Test fun invalidTargetAndUndoWithoutSnapshotNeverClaimSuccessOrMutateDrafts() = runBlocking {
        onMain {
            val vm = vm()
            vm.updateSetDraft(exerciseId, 0, "left", draft("41"))
            val before = vm.uiState.value.setDrafts
            val invalid = vm.performRelatorAssist(adjust().copy(exerciseId = "missing-exercise"))
            assertRejected(invalid)
            assertEquals(invalid, vm.relatorAssistAck.value)
            assertEquals(before, vm.uiState.value.setDrafts)
            val noUndo = vm.performRelatorAssist(undo(adjust()))
            assertRejected(noUndo)
            assertEquals(noUndo, vm.relatorAssistAck.value)
            assertEquals(before, vm.uiState.value.setDrafts)
        }
    }

    @Test fun replacementWithTheSameSlotAndSeriesRejectsTheOldUndoWithoutChangingB() = runBlocking {
        onMain {
            val vm = vm()
            vm.updateSetDraft(exerciseId, 0, "left", draft("41"))
            val action = adjust()
            assertTrue(vm.performRelatorAssist(action).applied)
            val original = vm.visibleExercises(vm.uiState.value).single()
            vm.replaceExercise(exerciseId, ExerciseMuscleInfo(
                id = "relator-press-b", name = "Press de prueba B", equipment = "Mancuerna",
                catalogDefinitionId = "relator-press", catalogConfigurationId = "relator-press-b",
            ))
            val replacement = vm.visibleExercises(vm.uiState.value).single()
            assertEquals(original.id, replacement.id)
            assertEquals(original.sets.map { it.id }, replacement.sets.map { it.id })
            assertNotEquals(original.canonicalExerciseId, replacement.canonicalExerciseId)
            assertNull(vm.getSetDraft(exerciseId, 0, "left"))
            vm.updateSetDraft(exerciseId, 0, "left", draft("55"))
            val before = vm.uiState.value.setDrafts
            val rejected = vm.performRelatorAssist(undo(action))
            assertRejected(rejected)
            assertEquals(rejected, vm.relatorAssistAck.value)
            assertEquals(before, vm.uiState.value.setDrafts)
            assertEquals(replacement, vm.visibleExercises(vm.uiState.value).single())
        }
    }

    @Test fun undoForTheWrongSideCannotChangeEitherSideOrConsumeTheMatchingUndo() = runBlocking {
        onMain {
            val vm = vm()
            vm.updateSetDraft(exerciseId, 0, "left", draft("41"))
            vm.updateSetDraft(exerciseId, 0, "right", draft("63", "right"))
            val left = checkNotNull(vm.getSetDraft(exerciseId, 0, "left"))
            val right = checkNotNull(vm.getSetDraft(exerciseId, 0, "right"))
            val action = adjust()
            assertTrue(vm.performRelatorAssist(action).applied)
            val adjusted = vm.uiState.value.setDrafts
            assertRejected(vm.performRelatorAssist(undo(action).copy(side = "right")))
            assertEquals(adjusted, vm.uiState.value.setDrafts)
            assertDraftContent(right, vm.getSetDraft(exerciseId, 0, "right"))
            assertTrue(vm.performRelatorAssist(undo(action)).applied)
            assertDraftContent(left, vm.getSetDraft(exerciseId, 0, "left"))
            assertDraftContent(right, vm.getSetDraft(exerciseId, 0, "right"))
        }
    }
}