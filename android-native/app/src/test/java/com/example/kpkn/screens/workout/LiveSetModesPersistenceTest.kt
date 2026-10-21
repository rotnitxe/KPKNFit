package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.workout.components.liveModeExercise
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The card's complete payload reaches the real recorder, Room and process recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSetModesPersistenceTest {
    @get:Rule val room = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()); LiveSessionVmFixture.resetAugeRepository() }
    @After fun after() {
        try { fixture?.close(); LiveSessionVmFixture.resetAugeRepository(); ProgramRepository.closeInstance(); KpknDatabase.closeInstance() }
        finally { Dispatchers.resetMain() }
    }
    @Test fun repsPersistAndUpdateOneStableRecord() = persist(TrainingMode.REPS, 9.0, 100.0)
    @Test fun rmPersistsActualRepsAndUsedWeight() = persist(TrainingMode.RM, 9.0, 100.0)
    @Test fun soloRpePersistsTheLoadChosenAtExecution() = persist(TrainingMode.SOLO_RPE, 10.0, 12.5)
    @Test fun timePersistsRealSecondsBeyondTheGoal() = persist(TrainingMode.TIME, 65.0, 0.0)
    @Test fun distancePersistsDecimalMetersWithoutTruncation() = persist(TrainingMode.DISTANCE, 12.25, 24.0)
    @Test fun customPersistsDecimalQuantityAndItsExerciseUnit() = persist(TrainingMode.CUSTOM, 31.5, 0.0)
    @Test fun localRmReferenceAndPercentSurviveRoomAndRecreationWithoutEditingTheProgram() = runBlocking {
        val exercise = liveModeExercise(TrainingMode.RM)
        val f = LiveSessionVmFixture(Session("rm-draft-session", "RM", exercises = listOf(exercise))).prepare().also { fixture = it }
        val vm = f.open()
        vm.updateSetDraft(exercise.id, 0, draft = WorkoutSetDraft(weightText = "112,5", valueText = "9", rmReference1RM = 150.0, rmPercent = 75.0, isDirty = true))
        vm.flushOngoingForBackgroundAndAwait()
        val restored = f.recreate(vm).getSetDraft(exercise.id, 0)
        assertEquals("112,5", restored?.weightText); assertEquals("9", restored?.valueText)
        assertEquals(150.0, restored?.rmReference1RM); assertEquals(75.0, restored?.rmPercent)
        assertEquals(125.0, f.programSession()!!.allExercises().single().reference1RM)
    }

    private fun persist(mode: TrainingMode, quantity: Double, weight: Double) = runBlocking {
        val template = liveModeExercise(mode)
        val exercise = template.copy(sets = List(2) { template.sets.single().copy(id = "s$it") })
        val f = LiveSessionVmFixture(Session("mode-session", "Modos", exercises = listOf(exercise))).prepare().also { fixture = it }
        val vm = f.open()
        val unit = liveSetUnitMode(exercise, exercise.sets.first())
        suspend fun submit(value: Double) = vm.recordSetV2(weight = weight, value = value, intensity = 8.0,
            advanced = SetAdvancedFeedback(actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0, intensityAdjusted = true,
                timerElapsedSeconds = value.toInt().takeIf { unit == UnitModeV2.TIME }, rom = 75, notes = "Resultado real"),
            loadMode = exercise.sets.first().loadModeV2, unitMode = unit, bodyWeight = 80.0,
            setIdxOverride = 0, expectedExerciseId = exercise.id, expectedSetIdx = 0)
        assertTrue(submit(quantity) is RecordSetResult.Created)
        vm.flushOngoingForBackgroundAndAwait()
        val first = f.storedOngoing().completedSets.getValue("${exercise.id}_0")
        assertEquals(unit, first.recordedPayloadV3?.unitMode)
        assertEquals(weight, first.originalLoad!!, 0.0)
        assertEquals(exercise.sets.first().loadModeV2, first.recordedPayloadV3?.loadInputMode)
        if (exercise.sets.first().loadModeV2 == LoadModeV2.BODYWEIGHT) {
            assertEquals(80.0, first.recordedPayloadV3?.bodyWeightSnapshot!!, 0.0)
            assertEquals(80.0, first.weight, 0.0) // carga equivalente de Auge, separada de la carga original.
        } else assertEquals(weight, first.weight, 0.0)
        assertEquals(quantity, completedLiveQuantity(first, unit)!!, .00001)
        assertEquals(IntensityMode.RPE, first.actualIntensityMode)
        assertEquals(8.0, first.actualIntensityValue!!, 0.0)
        assertEquals(75, first.rom)
        assertEquals("Resultado real", first.notes)
        assertTrue(submit(quantity + 1) is RecordSetResult.Updated)
        vm.flushOngoingForBackgroundAndAwait()
        val stored = f.storedOngoing()
        assertEquals(1, stored.completedSets.size)
        assertEquals(first.id, stored.completedSets.getValue("${exercise.id}_0").id)
        val recreated = f.recreate(vm).uiState.value
        val reopened = recreated.completedSets.getValue("${exercise.id}_0")
        assertEquals(quantity + 1, completedLiveQuantity(reopened, unit)!!, .00001)
        assertEquals(IntensityMode.RPE, reopened.actualIntensityMode)
        assertEquals(8.0, reopened.actualIntensityValue!!, 0.0)
        if (mode == TrainingMode.CUSTOM) assertEquals("brazadas", recreated.session!!.allExercises().single().customUnit)
    }
}
