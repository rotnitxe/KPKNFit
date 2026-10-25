package com.example.kpkn.domain.training.planning

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import org.junit.Assert.*
import org.junit.Test

class PlannedLoadProjectorTest {
    private fun week(sets: List<ExerciseSet>, parts: Boolean = false): ProgramWeek {
        val exercise = Exercise("press","Press",sets = sets,
            effectiveMuscles = listOf(InvolvedMuscle("Pectorales",MuscleRole.PRIMARY)))
        val session = if (parts) Session("s","D",parts = listOf(SessionPart("part","Fuerza",exercises = listOf(exercise))))
            else Session("s","D",exercises = listOf(exercise))
        return ProgramWeek("w","Semana",sessions = listOf(session))
    }
    @Test fun eight_explicit_90_percent_1rm_targets_are_described_without_a_recovery_score() {
        val w = week((1..8).map { ExerciseSet("s$it",targetReps = 3,targetPercentageRM = 90.0,loadBasis = LoadBasis.PERCENT_1RM) })
        val result = PlannedLoadProjector.project(w,emptyList())
        val chest = result.loads.single()
        assertEquals(8.0,chest.plannedSets,0.0)
        assertEquals(8.0,chest.highEffortTargetSets,0.0)
        assertEquals("w",result.weekId)
    }
    @Test fun tm_percentages_and_unknown_bases_are_not_relabelled_as_1rm_percentages() {
        val sets = listOf(ExerciseSet("tm",targetReps = 3,targetPercentageRM = 90.0,loadBasis = LoadBasis.PERCENT_TM),
            ExerciseSet("unknown",targetReps = 3,targetPercentageRM = 90.0))
        val chest = PlannedLoadProjector.project(week(sets),emptyList()).loads.single()
        assertEquals(2.0,chest.plannedSets,0.0)
        assertEquals(0.0,chest.highEffortTargetSets,0.0)
    }
    @Test fun explicit_rir_and_rpe_targets_work_in_parts_and_invalid_ratings_do_not_count() {
        val sets = listOf(ExerciseSet("rir",targetReps = 5,targetRIR = 1),
            ExerciseSet("rpe",targetReps = 5,targetRPE = 9.0),
            ExerciseSet("invalid",targetReps = 5,targetRIR = -1,targetRPE = Double.NaN),
            ExerciseSet("warmup",targetReps = 5,targetRIR = 0,isIneffective = true))
        val chest = PlannedLoadProjector.project(week(sets,parts = true),emptyList()).loads.single()
        assertEquals(3.0,chest.plannedSets,0.0)
        assertEquals(2.0,chest.highEffortTargetSets,0.0)
    }
    @Test fun optional_sessions_are_labelled_as_prescribed_and_the_week_is_never_modified() {
        val w = week(listOf(ExerciseSet("s",targetReps = 5,targetRIR = 1)))
        val optional = w.copy(sessions = w.sessions.map { it.copy(requirement = SessionRequirement.OPTIONAL) })
        val result = PlannedLoadProjector.project(optional,emptyList())
        assertEquals(1.0,result.loads.single().plannedSets,0.0)
        assertTrue(result.notes.any { it.contains("opcionales") })
        assertEquals(w.sessions.single().exercises,optional.sessions.single().exercises)
    }

    @Test fun cyclic_preview_is_explicitly_a_template_and_completed_runs_have_no_projection() {
        val w = week(listOf(ExerciseSet("s",targetReps = 5,targetRIR = 1)))
        val block = Block("b","B",mesocycles = listOf(Mesocycle("meso","M",weeks = listOf(w))))
        val p = Program("p","P",macrocycles = listOf(Macrocycle("m","M",listOf(block))),
            runState = ProgramRunState("run",weekId = "w",cycleNumber = 3))
        val next = PlannedLoadProjector.next(p,emptyList())!!
        assertTrue(next.nextCycleTemplate)
        assertEquals(4,next.cycleNumber)
        assertEquals("Semana",next.weekName)
        assertNull(PlannedLoadProjector.next(p.copy(runState = p.runState!!.copy(status = ProgramRunStatus.COMPLETED)),emptyList()))
        assertNull(PlannedLoadProjector.next(p.copy(structure = ProgramStructure.COMPLEX),emptyList()))
    }
}
