package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.domain.calibration.CalibrationDecisionType
import com.example.kpkn.domain.training.Apre
import com.example.kpkn.domain.training.ApreVariant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ApreLiveCalibrationTest {
    private data class Fixture(val state: WorkoutUiState, val exercise: Exercise, val completed: CompletedSet,
        val record: CalibrationDecisionRecord)

    private fun fixture(variant: ApreVariant = ApreVariant.APRE_10, reps: Int = 12, partials: Int = 0,
        side: String? = null): Fixture {
        val sets = Apre.block(200.0, variant).map { item ->
            ExerciseSet("s${item.ordinal}", targetReps = item.targetReps, weight = if (item.ordinal >= 3) 150.0 else item.loadKg,
                purpose = item.purpose, amrapMinimumReps = item.minimumReps, loadModeV2 = LoadModeV2.LOAD,
                leftTarget = if (side != null) UnilateralTarget(weight = 150.0) else null,
                rightTarget = if (side != null) UnilateralTarget(weight = 140.0) else null)
        }
        val exercise = Exercise("press", "Press banca", sets = sets,
            unilateralMode = if (side != null) UnilateralMode.UNILATERAL_DIFFERENTIAL else UnilateralMode.BILATERAL,
            apreBlock = ApreBlockReference(variant.xrmReps, sets.map { it.id }))
        val completed = CompletedSet("done-s3", weight = 150.0, originalLoad = 150.0, reps = reps, partialReps = partials,
            purpose = SetPurpose.AMRAP, setIndex = 2, amrapMinimumReps = variant.xrmReps, side = side,
            loadMode = LoadModeV2.LOAD, isFailure = true)
        val state = WorkoutUiState(programId = "program", startTimeMs = 100L,
            session = Session("session", "Sesión", exercises = listOf(exercise,
                Exercise("other", "Remo", sets = listOf(ExerciseSet("other-s1", weight = 80.0))))),
            completedSets = mapOf(workoutSetKey(exercise.id, 2, side) to completed))
        val record = buildRecordedApreCalibration(state, exercise, 2, completed, 1_000L)!!
        return Fixture(state.copy(calibrationDecisions = listOf(record)), exercise, completed, record)
    }

    @Test fun lasTresTablasProponenSoloSuS4SinCapacidadInventada() {
        listOf(ApreVariant.APRE_10 to 12, ApreVariant.APRE_6 to 13, ApreVariant.APRE_3 to 6).forEach { (variant, reps) ->
            val f = fixture(variant, reps)
            val expected = Apre.set4LoadKg(150.0, reps, variant)
            assertEquals(expected, f.record.decision.affectedSets.single().afterKg, 0.0)
            assertEquals("s4", f.record.decision.affectedSets.single().setId)
            assertNull(f.record.decision.capacityKg)
            assertNull(f.record.decision.ratio)
            assertEquals(1.0, f.record.decision.factor, 0.0)
            val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, 1_001L)
            assertEquals(expected, calibrationLoadForSet(applied, f.exercise, 3, null)!!.weight, 0.0)
            assertEquals(f.state.session, applied.session)
            assertEquals(f.state.completedSets, applied.completedSets)
            assertNull(calibrationLoadForSet(applied, f.exercise, 1, null))
        }
    }

    @Test fun confirmedApreAssignmentReachesTheCardAdapterBeforeS4Recording() = kotlinx.coroutines.test.runTest {
        val f = fixture()
        var state = f.state
        val ports = java.lang.reflect.Proxy.newProxyInstance(
            WorkoutLoadSuggestionController.Ports::class.java.classLoader,
            arrayOf(WorkoutLoadSuggestionController.Ports::class.java)) { _, method, _ ->
            when (method.name) {
                "effectiveLoadModeForExercise" -> LoadModeV2.LOAD
                else -> error("Unexpected port: ${method.name}")
            }
        } as WorkoutLoadSuggestionController.Ports
        val controller = WorkoutLoadSuggestionController(scope=this, getState={ state },
            updateState={ state=it(state) }, ports=ports)
        state=resolveCalibration(state,f.record.id,CalibrationStatus.APPLIED,1_001L)
        val assignment=controller.getWeightSuggestionWithAutoRegulation(f.exercise,3)!!
        assertTrue(assignment.isCalibrationAssignment)
        assertEquals(155.0,assignment.suggestedWeight,0.0)
        assertEquals(f.state.completedSets,state.completedSets)
        state=resolveCalibration(state,f.record.id,CalibrationStatus.REVERTED,1_002L)
        val reverted=controller.getWeightSuggestionWithAutoRegulation(f.exercise,3)!!
        assertTrue(reverted.isCalibrationAssignment)
        assertEquals(150.0,reverted.suggestedWeight,0.0)
    }

    @Test fun parcialesYaSeparadasNoSeDescuentanDosVeces() {
        for ((total, bad) in listOf(13 to 2, 14 to 2)) {
            val full = Apre.validReps(total, bad)
            val f = fixture(reps = full, partials = bad)
            assertEquals(full, f.record.apre!!.validReps)
            assertEquals(Apre.set4LoadKg(150.0, full, ApreVariant.APRE_10), f.record.apre!!.set4LoadKg, 0.0)
        }
    }

    @Test fun aplicarConservarYRevertirSonIdempotentes() {
        val f = fixture()
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, 1_001L)
        assertEquals(applied, resolveCalibration(applied, f.record.id, CalibrationStatus.APPLIED, 1_002L))
        val reverted = resolveCalibration(applied, f.record.id, CalibrationStatus.REVERTED, 1_003L)
        assertEquals(150.0, calibrationLoadForSet(reverted, f.exercise, 3, null)!!.weight, 0.0)
        assertEquals(reverted, resolveCalibration(reverted, f.record.id, CalibrationStatus.REVERTED, 1_004L))
        val kept = resolveCalibration(f.state, f.record.id, CalibrationStatus.KEPT, 1_001L)
        assertEquals(kept, resolveCalibration(kept, f.record.id, CalibrationStatus.APPLIED, 1_002L))
        assertEquals(150.0, calibrationLoadForSet(kept, f.exercise, 3, null)!!.weight, 0.0)
    }

    @Test fun fijadaHechaYElOtroLadoSeConservan() {
        val f = fixture(side = "left")
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, 1_001L)
        assertEquals(155.0, calibrationLoadForSet(applied, f.exercise, 3, "left")!!.weight, 0.0)
        assertNull(calibrationLoadForSet(applied, f.exercise, 3, "right"))
        val key = workoutSetKey(f.exercise.id, 3, "left")
        val fixed = f.state.copy(manualLoadOverrides = mapOf(key to 180.0))
        val fixedApplied = resolveCalibration(fixed, f.record.id, CalibrationStatus.APPLIED, 1_001L)
        assertTrue(fixedApplied.calibrationDecisions.single().appliedSetIds.isEmpty())
        assertEquals(fixed.manualLoadOverrides, fixedApplied.manualLoadOverrides)
        val done = applied.copy(completedSets = applied.completedSets + (key to CompletedSet("done-s4", weight = 155.0,
            originalLoad = 155.0, reps = 10, purpose = SetPurpose.AMRAP, side = "left", loadMode = LoadModeV2.LOAD)))
        val reverted = resolveCalibration(done, f.record.id, CalibrationStatus.REVERTED, 1_003L)
        assertEquals(done.completedSets, reverted.completedSets)
        assertEquals(155.0, calibrationLoadForSet(reverted, f.exercise, 3, "left")!!.weight, 0.0)
    }

    @Test fun noEntranAmrapSueltaCalibradorS4NiOtraIdentidad() {
        val f = fixture()
        assertNull(buildRecordedApreCalibration(f.state, f.exercise.copy(apreBlock = null), 2, f.completed, 1_001L))
        assertNull(buildRecordedApreCalibration(f.state, f.exercise, 2, f.completed.copy(purpose = SetPurpose.CALIBRATOR), 1_001L))
        assertNull(buildRecordedApreCalibration(f.state, f.exercise, 3, f.completed, 1_001L))
        val changed = f.exercise.copy(apreBlock = null)
        val editedState = f.state.copy(session = f.state.session!!.copy(exercises = listOf(changed)))
        assertEquals(editedState, resolveCalibration(editedState, f.record.id, CalibrationStatus.APPLIED, 1_001L))
    }

    @Test fun corporalAsistidaYCargaInvalidaSoloSeRegistran() {
        val f = fixture()
        for (mode in listOf(LoadModeV2.BODYWEIGHT, LoadModeV2.ASSISTED)) {
            val record = buildRecordedApreCalibration(f.state, f.exercise, 2, f.completed.copy(loadMode = mode), 1_001L)!!
            assertEquals(CalibrationDecisionType.NOT_APPLIED, record.decision.type)
            assertNull(record.decision.capacityKg)
        }
        val empty = buildRecordedApreCalibration(f.state, f.exercise, 2, f.completed.copy(weight = 0.0, originalLoad = 0.0), 1_001L)!!
        assertEquals(CalibrationDecisionType.NOT_APPLIED, empty.decision.type)
    }

    @Test fun laCorreccionReconstruyeDesdeLaBaseYSuUndoRetiraLaDecision() = runTest {
        val f = fixture()
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, 1_001L)
        val updated = f.completed.copy(reps = 17)
        val edited = applied.copy(completedSets = mapOf(workoutSetKey(f.exercise.id, 2) to updated))
        val refreshed = refreshCorrectedCalibrations(applied, edited, emptyList(), now = 1_002L, accepted = { emptyList() })
        assertEquals(CalibrationStatus.SUPERSEDED, refreshed.calibrationDecisions.first().status)
        val next = refreshed.calibrationDecisions.last()
        assertEquals(150.0, next.decision.affectedSets.single().beforeKg, 0.0)
        assertEquals(Apre.set4LoadKg(150.0, 17, ApreVariant.APRE_10), next.apre!!.set4LoadKg, 0.0)
        val undone = refreshCorrectedCalibrations(refreshed, refreshed.copy(completedSets = emptyMap()), emptyList(),
            now = 1_003L, accepted = { emptyList() })
        assertTrue(undone.calibrationDecisions.all { it.status == CalibrationStatus.SUPERSEDED })
    }

    @Test fun elJsonPreservaLaDecisionYSigueLeyendoElFormatoAnterior() {
        val f = fixture()
        val json = Json { ignoreUnknownKeys = true }
        val restored = json.decodeFromString<CalibrationDecisionRecord>(json.encodeToString(f.record))
        assertEquals(f.record, restored)
        assertNull(json.decodeFromString<CalibrationDecisionRecord>(json.encodeToString(f.record.copy(apre = null))).apre)
        assertTrue(calibrationSummaryLines(f.state.calibrationDecisions).single().contains("APRE"))
    }
}
