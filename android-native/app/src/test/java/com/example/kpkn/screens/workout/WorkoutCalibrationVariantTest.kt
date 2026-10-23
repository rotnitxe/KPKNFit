package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Cada variante conserva sus IDs de serie; la ejecución y el registro pertenecen a la sesión base. */
class WorkoutCalibrationVariantTest {
    private val now = 1_790_000_002_000L
    private data class Fixture(val state: WorkoutUiState, val exercise: Exercise,
        val source: CompletedSet, val record: CalibrationDecisionRecord)

    private fun fixture(mode: WeekVariant = WeekVariant.B): Fixture {
        val template = apreDurableExercise()
        fun scoped(prefix: String): Exercise {
            val sets = template.sets.map { it.copy(id = "$prefix-${it.id}") }
            return template.copy(sets = sets, apreBlock = template.apreBlock!!.copy(setIds = sets.map { it.id }))
        }
        val a = scoped("a"); val b = scoped("b"); val c = scoped("c"); val d = scoped("d")
        val base = Session("base-session", "A", exercises = listOf(a),
            sessionB = Session("b-session", "B", exercises = listOf(b)),
            sessionC = Session("c-session", "C", exercises = listOf(c)),
            sessionD = Session("d-session", "D", exercises = listOf(d)))
        val exercise = when (mode) { WeekVariant.A -> a; WeekVariant.B -> b; WeekVariant.C -> c; WeekVariant.D -> d }
        val state = apreDurableState().copy(session = base, activeMode = mode, programId = "variant-program")
        val context = calibrationContextForSet(state, exercise, 2, null)
        val source = CompletedSet("source-s3", weight = 150.0, originalLoad = 150.0, reps = 12,
            rir = 0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 10,
            loadMode = LoadModeV2.LOAD, contextKey = context.key, setIndex = 2)
        val recorded = state.copy(completedSets = mapOf(workoutSetKey(exercise.id, 2) to source))
        val record = checkNotNull(buildRecordedApreCalibration(recorded, exercise, 2, source, now))
        assertEquals(155.0, record.decision.affectedSets.single().afterKg, 0.0)
        return Fixture(recorded.copy(calibrationDecisions = listOf(record)), exercise, source, record)
    }

    @Test fun applyRouteAndRevertUseTheOwnSetIdsOfB_C_AndD() {
        listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D).forEach { mode ->
            val f = fixture(mode)
            val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, now + 1)
            val record = applied.calibrationDecisions.single()
            assertEquals(mode.name, CalibrationStatus.APPLIED, record.status)
            assertEquals(setOf(f.exercise.sets[3].id), record.appliedSetIds)
            assertEquals(155.0, calibrationLoadForSet(applied, f.exercise, 3, null)!!.weight, 0.0)
            assertEquals(mapOf(workoutSetKey(f.exercise.id, 3) to 155.0), calibrationRouteLoads(applied))
            assertEquals(f.state.session, applied.session)
            assertEquals(f.state.completedSets, applied.completedSets)
            assertEquals(calibrationExecutionId(f.state), calibrationExecutionId(applied))
            val reverted = resolveCalibration(applied, f.record.id, CalibrationStatus.REVERTED, now + 2)
            assertEquals(CalibrationStatus.REVERTED, reverted.calibrationDecisions.single().status)
            assertEquals(150.0, calibrationLoadForSet(reverted, f.exercise, 3, null)!!.weight, 0.0)
            assertTrue(calibrationRouteLoads(reverted).isEmpty())
        }
    }

    @Test fun aProposalFromBDoesNotApplyToTheSameExerciseIdInA() {
        val f = fixture()
        val otherMode = f.state.copy(activeMode = WeekVariant.A)
        assertEquals(otherMode, resolveCalibration(otherMode, f.record.id, CalibrationStatus.APPLIED, now + 1))
    }

    @Test fun aChangedTargetIdentityInBStillRejectsTheOldProposal() {
        val f = fixture()
        val sets = f.exercise.sets.mapIndexed { index, set -> if (index == 3) set.copy(id = "b-new-s4") else set }
        val changed = f.exercise.copy(sets = sets, apreBlock = f.exercise.apreBlock!!.copy(setIds = sets.map { it.id }))
        val state = f.state.copy(session = f.state.session!!.copy(
            sessionB = f.state.session!!.sessionB!!.copy(exercises = listOf(changed))))
        assertEquals(state, resolveCalibration(state, f.record.id, CalibrationStatus.APPLIED, now + 1))
    }

    @Test fun correctedS3RebuildsTheBProposalWithoutBorrowingASetIds() = runBlocking {
        val f = fixture()
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, now + 1)
        val corrected = f.source.copy(weight = 145.0, originalLoad = 145.0)
        val next = applied.copy(completedSets = mapOf(workoutSetKey(f.exercise.id, 2) to corrected))
        val refreshed = refreshCorrectedCalibrations(applied, next, emptyList(), now = now + 2) { emptyList() }
        assertEquals(CalibrationStatus.SUPERSEDED, refreshed.calibrationDecisions.first().status)
        val replacement = refreshed.calibrationDecisions.last()
        assertEquals(CalibrationStatus.PROPOSED, replacement.status)
        assertEquals(f.exercise.sets[2].id, replacement.apre!!.sourcePlannedSetId)
        assertEquals(f.exercise.sets[3].id, replacement.apre!!.targetPlannedSetId)
        assertEquals(150.0, replacement.decision.affectedSets.single().afterKg, 0.0)
        assertEquals(f.state.session, refreshed.session)
    }

    @Test fun correctedS4RetainsBIdentityAndItsPhysicalXrm() = runBlocking {
        val f = fixture()
        val applied = resolveCalibration(f.state, f.record.id, CalibrationStatus.APPLIED, now + 1)
        val raw = f.source.copy(id = "source-s4", weight = 155.0, originalLoad = 155.0, reps = 17, setIndex = 3)
        val capture = checkNotNull(buildRecordedApreXrm(applied, f.exercise, 3, raw, now + 2))
        assertEquals(162.5, capture.reference.capturedLoadKg!!, 0.0)
        val key = workoutSetKey(f.exercise.id, 3)
        val before = applied.copy(completedSets = applied.completedSets + (key to raw.copy(apreXrm = capture)))
        val next = before.copy(completedSets = before.completedSets + (key to
            before.completedSets.getValue(key).copy(weight = 150.0, originalLoad = 150.0)))
        val refreshed = refreshCorrectedCalibrations(before, next, emptyList(), now = now + 3) { emptyList() }
        val corrected = checkNotNull(refreshed.completedSets.getValue(key).apreXrm)
        assertEquals(f.exercise.sets[3].id, corrected.sourcePlannedSetId)
        assertEquals(157.5, corrected.reference.capturedLoadKg!!, 0.0)
        assertEquals(f.state.session, refreshed.session)
    }
}
