package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.ProtocolBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockProgressionEngineTest {

    private fun seedSession(): Session = Session(
        id = "seed-session",
        name = "Squat Day",
        exercises = listOf(
            Exercise(
                id = "ex-1",
                name = "Sentadilla",
                catalogConfigurationId = "low_bar_back_squat__barbell",
                isCompetitionLift = true,
                sets = listOf(
                    ExerciseSet(id = "s1", targetReps = 5, targetRPE = 7.0, intensityMode = IntensityMode.RPE),
                    ExerciseSet(id = "s2", targetReps = 5, targetRPE = 7.0, intensityMode = IntensityMode.RPE),
                    ExerciseSet(id = "s3", targetReps = 5, targetRPE = 7.0, intensityMode = IntensityMode.RPE),
                ),
            ),
        ),
    )

    private fun blockWithWeeks(weeks: Int, goal: BlockGoal = BlockGoal.ACCUMULATION): Block {
        val seed = seedSession()
        return Block(
            id = "b1",
            name = "Acumulación",
            goal = goal,
            progressionScheme = BlockProgressionScheme.PERCENT_RM,
            mesocycles = listOf(
                Mesocycle(
                    id = "m1",
                    name = "Meso",
                    goal = MesocycleGoal.ACCUMULATION,
                    weeks = (1..weeks).map { i ->
                        ProgramWeek(
                            id = "w$i",
                            name = "Semana $i",
                            sessions = if (i == 1) listOf(seed) else emptyList(),
                            progressionIndex = i,
                        )
                    },
                ),
            ),
        )
    }

    @Test
    fun percentageProgressesWeekToWeek() {
        val result = BlockProgressionEngine.applyProgression(
            block = blockWithWeeks(4),
            protocolBlock = ProtocolBlock("Acc", 4, "Acumulación", 60, 75, 1.2),
        )
        val weeks = result.block.mesocycles.first().weeks
        val pcts = weeks.map { week ->
            week.sessions.first().allExercises().first().sets.first().targetPercentageRM!!
        }
        assertTrue("semana1 < semana4: $pcts", pcts.first() < pcts.last())
        assertTrue(result.diffs.isNotEmpty())
        assertTrue(result.diffs.first().percentageDelta > 0)
    }

    @Test
    fun deepCloneProducesFreshIds() {
        val result = BlockProgressionEngine.applyProgression(blockWithWeeks(2))
        val w1 = result.block.mesocycles.first().weeks[0]
        val w2 = result.block.mesocycles.first().weeks[1]
        val ids1 = w1.sessions.flatMap { it.allExercises() }.flatMap { listOf(it.id) + it.sets.map { s -> s.id } }
        val ids2 = w2.sessions.flatMap { it.allExercises() }.flatMap { listOf(it.id) + it.sets.map { s -> s.id } }
        assertTrue(ids1.intersect(ids2.toSet()).isEmpty())
        assertNotEquals(w1.sessions.first().id, w2.sessions.first().id)
    }

    @Test
    fun noneSchemeLeavesBlockUntouched() {
        val block = blockWithWeeks(3).copy(progressionScheme = BlockProgressionScheme.NONE)
        val result = BlockProgressionEngine.applyProgression(block, scheme = BlockProgressionScheme.NONE)
        assertEquals(0, result.diffs.size)
        assertEquals(block.mesocycles.first().weeks[1].sessions.size, result.block.mesocycles.first().weeks[1].sessions.size)
    }

    @Test
    fun previewDiffSummarizesChange() {
        val block = blockWithWeeks(4, BlockGoal.INTENSIFICATION)
        val diff = BlockProgressionEngine.previewDiff(block, 1, 4)
        assertTrue(diff != null)
        assertTrue(diff!!.summary.contains("Semana 1→4"))
    }

    @Test
    fun progressionIndex_is_global_across_mesocycles_and_maps_nested_variants() {
        val first = blockWithWeeks(1).mesocycles.first()
        val second = first.copy(
            id = "m2",
            name = "Meso 2",
            weeks = listOf(
                ProgramWeek(
                    id = "w2",
                    name = "Semana 2",
                    // Legacy JSON commonly restarts this index at one.
                    progressionIndex = 1,
                    sessions = listOf(seedSession().copy(sessionB = seedSession().copy(id = "variant-b"))),
                ),
            ),
        )
        val result = BlockProgressionEngine.applyProgression(
            blockWithWeeks(1).copy(mesocycles = listOf(first, second)),
        ).block

        val weeks = result.mesocycles.flatMap { it.weeks }
        assertEquals(listOf(1, 2), weeks.map { it.progressionIndex })
        val firstPct = weeks[0].sessions.first().exercises.first().sets.first().targetPercentageRM
        val secondPct = weeks[1].sessions.first().exercises.first().sets.first().targetPercentageRM
        assertTrue(firstPct != null && secondPct != null && secondPct > firstPct)
        assertTrue(weeks[1].sessions.first().sessionB?.exercises?.first()?.sets?.first()?.targetPercentageRM != null)
    }

    @Test
    fun percentage_rm_is_reserved_for_primary_and_accessories_keep_reps_rpe() {
        val accessory = Exercise(
            id = "accessory",
            name = "Remo",
            sets = listOf(ExerciseSet(id = "a1", targetReps = 10, targetRPE = 7.0)),
        )
        val block = blockWithWeeks(1).copy(
            mesocycles = blockWithWeeks(1).mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    week.copy(sessions = listOf(seedSession().copy(exercises = seedSession().exercises + accessory)))
                })
            },
        )
        val session = BlockProgressionEngine.applyProgression(block).block
            .mesocycles.first().weeks.first().sessions.first()
        assertTrue(session.exercises.first().sets.first().targetPercentageRM != null)
        assertEquals(TrainingMode.RM, session.exercises.first().trainingMode)
        assertTrue(session.exercises[1].sets.all { it.targetPercentageRM == null })
        assertEquals(TrainingMode.REPS, session.exercises[1].trainingMode)
    }

    @Test
    fun deload_is_non_increasing_even_when_scheme_would_wave_or_add_load() {
        listOf(BlockProgressionScheme.LINEAR_LOAD, BlockProgressionScheme.UNDULATING).forEach { scheme ->
            val block = blockWithWeeks(4, BlockGoal.DELOAD).copy(progressionScheme = scheme)
            val weeks = BlockProgressionEngine.applyProgression(block, scheme = scheme)
                .block.mesocycles.first().weeks
            val pcts = weeks.map {
                it.sessions.single().exercises.single().sets.first().targetPercentageRM
                    ?: error("deload main lift must keep an RM anchor")
            }
            val rpes = weeks.map { it.sessions.single().exercises.single().sets.first().targetRPE ?: 0.0 }
            assertTrue("$scheme deload %RM debe descargar: $pcts", pcts.zipWithNext().all { (from, to) -> to <= from })
            assertTrue("$scheme deload RPE debe descargar: $rpes", rpes.zipWithNext().all { (from, to) -> to <= from })
        }
    }

    @Test
    fun names_alone_never_promote_ohp_or_legacy_squat_to_rm_without_anchor() {
        val ohp = Exercise(
            id = "ohp",
            name = "Overhead Press",
            sets = listOf(ExerciseSet(id = "ohp-set", targetReps = 5, targetRPE = 7.0)),
        )
        val legacySquat = Exercise(
            id = "legacy-squat",
            name = "Sentadilla trasera",
            sets = listOf(ExerciseSet(id = "legacy-set", targetReps = 5, targetRPE = 7.0)),
        )
        val unmarkedBench = Exercise(
            id = "unmarked-bench",
            name = "Press de banca",
            catalogConfigurationId = "bench_press__barbell",
            sets = listOf(ExerciseSet(id = "bench-set", targetReps = 8, targetRPE = 7.0)),
        )
        val block = blockWithWeeks(1).copy(
            mesocycles = blockWithWeeks(1).mesocycles.map { meso ->
                meso.copy(weeks = meso.weeks.map { week ->
                    week.copy(sessions = listOf(
                        seedSession().copy(exercises = listOf(ohp, legacySquat, unmarkedBench)),
                    ))
                })
            },
        )

        val session = BlockProgressionEngine.applyProgression(block).block
            .mesocycles.first().weeks.first().sessions.first()
        assertTrue(session.exercises.all { exercise ->
            exercise.trainingMode == TrainingMode.REPS &&
                exercise.sets.all { set -> set.targetPercentageRM == null }
        })
    }
}
