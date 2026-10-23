package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.screens.sessioneditor.normalizeExercise
import com.example.kpkn.screens.sessioneditor.sessionExerciseListDose
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import com.example.kpkn.screens.workout.components.liveModeExercise
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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

    @Test fun modeTransitionsKeepTheActiveUnitAcrossReferencePersistenceAndRecreation() = runBlocking {
        val source = Exercise(
            id = "press",
            name = "Press Militar",
            catalogConfigurationId = CatalogIds.OHP,
            trainingMode = TrainingMode.REPS,
            sets = List(4) { index ->
                ExerciseSet(
                    id = "press-set-" + index,
                    targetReps = 8,
                    targetRPE = 9.0,
                    loadModeV2 = LoadModeV2.BODYWEIGHT,
                    weight = 0.0,
                )
            },
        )
        val soloRpe = source.withTrainingMode(TrainingMode.SOLO_RPE).normalizeExercise()
        val timeMode = soloRpe.withTrainingMode(TrainingMode.TIME)
        val time = timeMode.copy(
            sets = timeMode.sets.map { it.copy(targetReps = null, targetDuration = 45, plannedTargetV2 = null) },
        ).normalizeExercise()
        val distanceMode = time.withTrainingMode(TrainingMode.DISTANCE)
        val distance = distanceMode.copy(
            sets = distanceMode.sets.mapIndexed { index, set -> set.copy(plannedTargetV2 = 400.0 + index * 5.0) },
        ).normalizeExercise()
        val customMode = distance.withTrainingMode(TrainingMode.CUSTOM)
        val customDraft = customMode.copy(
            customUnit = "pasos",
            sets = customMode.sets.mapIndexed { index, set ->
                set.copy(
                    targetReps = null,
                    plannedTargetV2 = if (index == 0) 12.0 else if (index == 1) 1.0 else 12.0,
                    targetRPE = 9.0,
                )
            },
        ).normalizeExercise()
        val repsDraft = time.withTrainingMode(TrainingMode.REPS)
        assertTrue(repsDraft.sets.all { it.unitModeV2 == UnitModeV2.REPS })
        assertTrue(customDraft.sets.all { it.unitModeV2 == UnitModeV2.CUSTOM })
        assertEquals(45, customDraft.sets.first().targetDuration)

        val f = LiveSessionVmFixture(
            Session("g1-mode-transition", "G1 · Press Militar", exercises = listOf(customDraft)),
        ).prepare().also { fixture = it }
        val vm = f.open()
        val exerciseId = customDraft.id
        assertEquals(TrainingMode.CUSTOM, vm.uiState.value.session!!.allExercises().single().trainingMode)
        assertEquals(UnitModeV2.CUSTOM, liveSetUnitMode(vm.uiState.value.session!!.allExercises().single(),
            vm.uiState.value.session!!.allExercises().single().sets[1]))

        // Same Confirmar cambios boundary as the live editor. Null -> 46.5 must persist before the session mutation is applied.
        vm.updateExerciseDefinition(exerciseId) { it.copy(reference1RM = 46.5) }
        withTimeout(20_000) {
            vm.uiState.first { state ->
                val saved = state.session?.allExercises()?.singleOrNull() ?: return@first false
                saved.reference1RM == 46.5 && saved.trainingMode == TrainingMode.CUSTOM &&
                    saved.sets[1].plannedTargetV2 == 1.0 && saved.sets.all { it.unitModeV2 == UnitModeV2.CUSTOM }
            }
        }
        val resolved = vm.uiState.value.session!!.allExercises().single()
        val program = f.repository.getProgramById(f.programId)!!
        assertEquals(46.5, ExerciseTrainingMax.capturedKg(program, resolved, PlanLoadReferenceKind.EXERCISE_1RM)!!, 0.0)
        assertEquals(UnitModeV2.CUSTOM, liveSetUnitMode(resolved, resolved.sets[1]))
        assertTrue(sessionExerciseListDose(resolved, includeRest = false)!!.contains("pasos"))
        assertFalse(sessionExerciseListDose(resolved, includeRest = false)!!.contains("45 s"))

        vm.flushOngoingForBackgroundAndAwait()
        val stored = f.storedOngoing().session.allExercises().single()
        assertEquals(46.5, stored.reference1RM, 0.0)
        assertEquals(UnitModeV2.CUSTOM, stored.sets[1].unitModeV2)
        assertEquals(1.0, stored.sets[1].plannedTargetV2!!, 0.0)
        var reopened = f.recreate(vm)
        var restored = reopened.uiState.value.session!!.allExercises().single()
        assertEquals(UnitModeV2.CUSTOM, liveSetUnitMode(restored, restored.sets[1]))
        assertEquals(1.0, restored.sets[1].plannedTargetV2!!, 0.0)
        assertFalse(sessionExerciseListDose(restored, includeRest = false)!!.contains("45 s"))

        suspend fun record(index: Int, value: Double, unit: UnitModeV2) {
            val result = reopened.recordSetV2(
                weight = 0.0,
                value = value,
                intensity = 9.0,
                advanced = SetAdvancedFeedback(actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 9.0,
                    intensityAdjusted = true),
                loadMode = LoadModeV2.BODYWEIGHT,
                unitMode = unit,
                bodyWeight = 80.0,
                setIdxOverride = index,
                expectedExerciseId = exerciseId,
                expectedSetIdx = index,
            )
            assertTrue("registro real de la serie " + index, result is RecordSetResult.Created)
            withTimeout(10_000) {
                reopened.uiState.first { it.completedSets["press_" + index]?.recordedPayloadV3?.unitMode == unit }
            }
        }
        record(0, 12.0, UnitModeV2.CUSTOM)
        record(1, 1.0, UnitModeV2.CUSTOM)
        reopened.flushOngoingForBackgroundAndAwait()
        val customLog = f.storedOngoing().completedSets.getValue("press_1")
        assertEquals(UnitModeV2.CUSTOM, customLog.recordedPayloadV3?.unitMode)
        assertNull(customLog.recordedPayloadV3?.timerTargetSeconds)
        assertEquals(1.0, completedLiveQuantity(customLog, UnitModeV2.CUSTOM)!!, 0.0)

        reopened.updateExerciseDefinition(exerciseId) { current ->
            current.withTrainingMode(TrainingMode.TIME).copy(
                sets = current.sets.map { it.copy(targetReps = null, targetDuration = 45, plannedTargetV2 = null) },
            )
        }
        withTimeout(10_000) {
            reopened.uiState.first { state ->
                state.session?.allExercises()?.singleOrNull()?.let { it.trainingMode == TrainingMode.TIME &&
                    it.sets.all { set -> set.unitModeV2 == UnitModeV2.TIME && set.targetDuration == 45 } } == true
            }
        }
        reopened.updateExerciseDefinition(exerciseId) { current ->
            current.withTrainingMode(TrainingMode.REPS).copy(
                sets = current.sets.map { it.copy(targetReps = 8, targetDuration = 45, plannedTargetV2 = null) },
            )
        }
        withTimeout(10_000) {
            reopened.uiState.first { state ->
                state.session?.allExercises()?.singleOrNull()?.let { it.trainingMode == TrainingMode.REPS &&
                    it.sets.all { set -> set.unitModeV2 == UnitModeV2.REPS && set.targetDuration == null } } == true
            }
        }
        restored = reopened.uiState.value.session!!.allExercises().single()
        assertEquals(UnitModeV2.REPS, liveSetUnitMode(restored, restored.sets[2]))
        assertEquals("4 × 8 · RPE 9", sessionExerciseListDose(restored, includeRest = false))
        record(2, 8.0, UnitModeV2.REPS)
        reopened.flushOngoingForBackgroundAndAwait()
        val repsLog = f.storedOngoing().completedSets.getValue("press_2")
        assertEquals(UnitModeV2.REPS, repsLog.recordedPayloadV3?.unitMode)
        assertEquals(8.0, completedLiveQuantity(repsLog, UnitModeV2.REPS)!!, 0.0)
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
