package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.DifficultySignalV2
import com.example.kpkn.data.models.HistoryColorV2
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.definitions.MadcowProtocol
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PlanMaterializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** D7/§10: declared TM basis is load semantics; it does not prove authorship. */
class AuthoredTmLoadConsumerContractTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun installCatalog() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val madcow by lazy { materializeMadcow() }

    private fun materializeMadcow(squatOneRm: Double? = 200.0): Program =
        PlanMaterializer.materialize(
            program = Program(id = "madcow-consumer", name = "Madcow"),
            recipe = MadcowProtocol.recipe(),
            metadata = CatalogCompositionTestSupport.metadata,
            profile = PowerliftingProfile(squat1RM = squatOneRm, bench1RM = 200.0, deadlift1RM = 200.0),
        )

    private fun sessionOn(day: Int, program: Program = madcow): Session = program.macrocycles.single().blocks.single()
        .mesocycles.single().weeks[3].sessions.single { it.dayOfWeek == day }

    private fun squatOn(day: Int, program: Program = madcow): Exercise = sessionOn(day, program).allExercises()
        .single { it.catalogConfigurationId == CatalogIds.SQ_LOW }

    private fun assertKg(expected: Double, actual: Double?) {
        assertNotNull(actual)
        assertEquals(expected, requireNotNull(actual), 1e-9)
    }

    @Test
    fun published_madcow_week_four_keeps_the_monday_top_and_friday_triple_in_both_consumers() {
        listOf(1 to 174.0, 5 to 178.35).forEach { (day, expectedKg) ->
            val squat = squatOn(day)
            val top = squat.sets[4]
            assertEquals(if (day == 1) 5 else 3, top.targetReps)
            assertEquals(LoadBasis.PERCENT_TM, top.loadBasis)
            assertKg(expectedKg, top.weight)
            assertKg(expectedKg, calculateSuggestedLoad(squat, top))
            assertKg(expectedKg, calculateSuggestedLoad(squat, top, emptyList()))
            val normalized = top.normalizeSet(squat)
            assertEquals(top.targetPercentageRM, normalized.targetPercentageRM)
            assertEquals(top.targetReps, normalized.targetReps)
            assertKg(expectedKg, normalized.weight)
        }
    }

    @Test
    fun normalizing_the_real_friday_session_preserves_the_triple_and_backoff() {
        val session = sessionOn(5)
        val normalized = session.normalizeSession()
        val squat = normalized.allExercises().single { it.catalogConfigurationId == CatalogIds.SQ_LOW }
        val sets = squat.sets
        assertEquals(
            session.allExercises().single { it.catalogConfigurationId == CatalogIds.SQ_LOW }.sets[4].targetPercentageRM,
            sets[4].targetPercentageRM,
        )
        assertEquals(3, sets[4].targetReps)
        assertKg(178.35, sets[4].weight)
        assertEquals(8, sets[5].targetReps)
        assertKg(130.5, sets[5].weight)
    }

    @Test
    fun editing_a_declared_tm_percentage_recalculates_kg_without_clamping_or_rounding_the_percentage() {
        val squat = squatOn(5)
        val top = squat.sets[4]
        val edited = top.copy(targetPercentageRM = 105.012345).normalizeSet(squat)
        assertEquals(105.012345, requireNotNull(edited.targetPercentageRM), 0.0)
        assertKg(182.7214803, edited.weight)
        assertKg(182.7214803, calculateSuggestedLoad(squat, edited, emptyList()))
        val lowPercentage = top.copy(targetPercentageRM = 35.12345).normalizeSet(squat)
        assertEquals(35.12345, requireNotNull(lowPercentage.targetPercentageRM), 0.0)
        assertKg(61.114803, lowPercentage.weight)
    }

    private fun historyWithOneRm200(exercise: Exercise): List<WorkoutLog> = listOf(
        WorkoutLog(
            id = "one-rm-history", programId = "history", sessionId = "session", sessionName = "Squat",
            date = "2026-10-04T10:00:00Z", durationMinutes = 20,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = exercise.id, exerciseName = exercise.name,
                    canonicalExerciseId = exercise.canonicalExerciseId,
                    sets = listOf(
                        CompletedSet(
                            id = "single", weight = 200.0, reps = 1,
                            homologatedResultV3 = HomologatedPerformanceResult(
                                contextKey = "squat", globalKey = "squat", loadMode = LoadModeV2.LOAD,
                                unitMode = UnitModeV2.REPS, actualValue = 1.0,
                                metricType = "estimated_rm", metricValue = 200.0, estimatedRm = 200.0,
                                localPerformanceIndex = 1.0, globalPerformanceIndex = 1.0,
                                contextPercentile = 50.0, globalPercentile = 50.0,
                                contextEwma = 200.0, contextStdDev = 0.0,
                                globalEwma = 200.0, globalStdDev = 0.0,
                                isContextPr = true, isGlobalPr = true,
                                historyColor = HistoryColorV2.NEUTRAL, difficultySignal = DifficultySignalV2.MATCHED,
                                suggestedNextLoad = 210.0, augeEquivalentLoad = 200.0, augeEquivalentReps = 1,
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun an_explicit_tm_basis_does_not_guess_a_missing_tm_from_a_pr_or_history_one_rm() {
        val realSquat = squatOn(5)
        val top = realSquat.sets[4]
        val prOnly = realSquat.copy(reference1RM = null, prFor1RM = PrReference(200.0, 1))
        val history = historyWithOneRm200(prOnly)
        assertKg(200.0, resolveReferenceCapacity(prOnly, history))
        assertNull(calculateSuggestedLoad(prOnly, top))
        assertNull(calculateSuggestedLoad(prOnly, top, history))
        assertNull(top.normalizeSet(prOnly).weight)
        val historyOnly = prOnly.copy(prFor1RM = null)
        assertKg(200.0, resolveReferenceCapacity(historyOnly, history))
        assertNull(calculateSuggestedLoad(historyOnly, top, history))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalidTm ->
            val invalid = prOnly.copy(reference1RM = invalidTm)
            assertNull(calculateSuggestedLoad(invalid, top, history))
        }
    }

    @Test
    fun shared_history_preserves_a_pending_tm_and_does_not_fill_any_published_tm_load() {
        val pending = squatOn(5, materializeMadcow(squatOneRm = null))
        assertNull(pending.reference1RM)
        assertTrue(pending.sets.all { it.weight == null })
        val hydrated = pending.withSharedPerformanceFromHistory(historyWithOneRm200(pending))
        assertNull(hydrated.reference1RM)
        assertTrue(hydrated.sets.all { it.weight == null })
        assertEquals(pending.sets.map { it.targetPercentageRM }, hydrated.sets.map { it.targetPercentageRM })
        assertKg(200.0, hydrated.prFor1RM?.weight)
        assertKg(200.0, hydrated.consolidatedWeight?.weightKg)
    }

    @Test
    fun shared_history_keeps_a_missing_tm_percentage_pending_instead_of_defaulting_to_75_or_next_load() {
        val squat = squatOn(5)
        val pending = squat.copy(sets = squat.sets.mapIndexed { index, set ->
            if (index == 0) set.copy(targetPercentageRM = null, weight = null) else set
        })
        val hydrated = pending.withSharedPerformanceFromHistory(historyWithOneRm200(pending))
        assertKg(174.0, hydrated.reference1RM)
        assertNull(hydrated.sets.first().targetPercentageRM)
        assertNull(hydrated.sets.first().weight)
        assertKg(178.35, hydrated.sets[4].weight)
    }

    @Test
    fun shared_history_uses_the_existing_tm_even_when_history_has_a_higher_one_rm_and_next_load() {
        val squat = squatOn(5)
        val pendingWeights = squat.copy(sets = squat.sets.map { it.copy(weight = null) })
        val hydrated = pendingWeights.withSharedPerformanceFromHistory(historyWithOneRm200(pendingWeights))
        assertKg(174.0, hydrated.reference1RM)
        assertEquals(squat.sets[4].targetPercentageRM, hydrated.sets[4].targetPercentageRM)
        assertKg(178.35, hydrated.sets[4].weight)
    }

    @Test
    fun exercise_and_session_normalization_keep_a_missing_tm_despite_a_saved_pr_and_shared_history() {
        val pendingProgram = materializeMadcow(squatOneRm = null)
        val pendingSession = sessionOn(5, pendingProgram)
        val pendingSquat = squatOn(5, pendingProgram).copy(prFor1RM = PrReference(200.0, 1))
        val normalizedExercise = pendingSquat.normalizeExercise()
        assertNull(normalizedExercise.reference1RM)
        assertTrue(normalizedExercise.sets.all { it.weight == null })
        val hydrated = pendingSquat.withSharedPerformanceFromHistory(historyWithOneRm200(pendingSquat))
        val composedSession = pendingSession.copy(
            exercises = pendingSession.exercises.map { if (it.id == hydrated.id) hydrated else it },
            parts = pendingSession.parts.map { part ->
                part.copy(exercises = part.exercises.map { if (it.id == hydrated.id) hydrated else it })
            },
        )
        val normalized = composedSession.normalizeSession().allExercises()
            .single { it.catalogConfigurationId == CatalogIds.SQ_LOW }
        assertNull(normalized.reference1RM)
        assertTrue(normalized.sets.all { it.weight == null })
        assertKg(200.0, normalized.prFor1RM?.weight)
    }

    @Test
    fun manual_history_and_pr_hydration_keep_their_existing_defaults() {
        val manual = squatOn(5).copy(
            reference1RM = null,
            sets = listOf(ExerciseSet(id = "manual", targetReps = 5)),
        )
        val hydrated = manual.withSharedPerformanceFromHistory(historyWithOneRm200(manual))
        assertKg(200.0, hydrated.reference1RM)
        assertEquals(75.0, requireNotNull(hydrated.sets.single().targetPercentageRM), 0.0)
        assertKg(150.0, hydrated.sets.single().weight)
        val withPrOnly = manual.copy(prFor1RM = PrReference(200.0, 1)).normalizeExercise()
        assertKg(200.0, withPrOnly.reference1RM)
    }

    @Test
    fun invalid_tm_percentages_do_not_create_a_suggested_or_normalized_load() {
        val squat = squatOn(5)
        val top = squat.sets[4]
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { percent ->
            val invalid = top.copy(targetPercentageRM = percent)
            assertNull(calculateSuggestedLoad(squat, invalid))
            assertNull(calculateSuggestedLoad(squat, invalid, emptyList()))
            assertNull(invalid.normalizeSet(squat).targetPercentageRM)
            assertNull(invalid.normalizeSet(squat).weight)
        }
    }

    @Test
    fun manual_and_other_bases_keep_the_one_rm_ceiling_and_quarter_kg_rounding() {
        val manual = Exercise(id = "manual", name = "Squat", trainingMode = TrainingMode.RM, reference1RM = 200.0)
        listOf(null, LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX, LoadBasis.PERCENT_OF_TOP_SET).forEach { basis ->
            val over = ExerciseSet(id = "over", targetReps = 3, targetPercentageRM = 110.0, loadBasis = basis)
            assertKg(200.0, calculateSuggestedLoad(manual, over))
            assertKg(200.0, calculateSuggestedLoad(manual, over, emptyList()))
            val normalized = over.normalizeSet(manual)
            assertEquals(100.0, requireNotNull(normalized.targetPercentageRM), 0.0)
            assertKg(200.0, normalized.weight)
            val fractional = over.copy(targetPercentageRM = 70.06)
            assertKg(140.0, calculateSuggestedLoad(manual, fractional))
            assertKg(140.0, calculateSuggestedLoad(manual, fractional, emptyList()))
        }
    }

    @Test
    fun tm_basis_keeps_bodyweight_assisted_and_lastre_priority() {
        val exercise = Exercise(
            id = "pull-up", name = "Pull-up", trainingMode = TrainingMode.RM,
            reference1RM = 200.0, prFor1RM = PrReference(25.0, 8),
        )
        listOf(LoadModeV2.BODYWEIGHT, LoadModeV2.ASSISTED, LoadModeV2.LASTRE).forEach { mode ->
            val manual = ExerciseSet(id = "set", targetReps = 6, targetPercentageRM = 80.0, loadModeV2 = mode)
            val tm = manual.copy(loadBasis = LoadBasis.PERCENT_TM)
            val expected = calculateSuggestedLoad(exercise, manual)
            assertNotNull(expected)
            if (mode == LoadModeV2.BODYWEIGHT) assertKg(0.0, expected) else assertTrue(requireNotNull(expected) > 0.0)
            assertEquals(expected, calculateSuggestedLoad(exercise, tm))
            assertEquals(expected, calculateSuggestedLoad(exercise, tm, emptyList()))
            assertEquals(manual.normalizeSet(exercise).weight, tm.normalizeSet(exercise).weight)
        }
    }

    @Test
    fun declared_tm_basis_survives_json_and_legacy_sets_keep_null_basis() {
        val json = Json { ignoreUnknownKeys = true }
        val squat = squatOn(5)
        val restored = json.decodeFromString<Exercise>(json.encodeToString(squat))
        val top = restored.sets[4]
        assertEquals(LoadBasis.PERCENT_TM, top.loadBasis)
        assertKg(178.35, calculateSuggestedLoad(restored, top))
        assertKg(178.35, top.normalizeSet(restored).weight)
        val legacy = json.decodeFromString<ExerciseSet>(
            """{"id":"legacy","targetReps":3,"targetPercentageRM":110.0}""",
        )
        assertNull(legacy.loadBasis)
        assertKg(200.0, calculateSuggestedLoad(Exercise(id = "manual", name = "Manual", trainingMode = TrainingMode.RM, reference1RM = 200.0), legacy))
    }
}
