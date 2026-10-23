package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import org.junit.Assert.*
import org.junit.Test

class Cal5bTrainingMaxSessionReferencesTest {
    private fun exercise(id: String) = Exercise(id,id,catalogConfigurationId = CatalogIds.OHP,
        loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,trainingMode = TrainingMode.RM,
        sets = listOf(ExerciseSet("$id-set",weight = 40.0,targetPercentageRM = 80.0,loadBasis = LoadBasis.PERCENT_TM)))
    private fun program(session: Session,manual: List<ManualSessionOverride> = emptyList()) = Program("p","Plan",
        manualSessionOverrides = manual,macrocycles = listOf(Macrocycle("m","M",blocks = listOf(Block("b","B",
            mesocycles = listOf(Mesocycle("meso","M",weeks = listOf(ProgramWeek("w","W",sessions = listOf(session))))))))))
    @Test fun pendingOccurrencesHydrateButDoneAndManualLoadsStayExact() {
        val source = exercise("source")
        val done = exercise("done")
        val manual = exercise("manual")
        val pending = exercise("pending")
        val other = exercise("other").copy(catalogConfigurationId = CatalogIds.OHP_SEATED)
        val session = Session("s","Fuerza",exercises = listOf(source,done,manual,pending,other))
        val completed = CompletedSet("done",weight = 43.0,originalLoad = 43.0,reps = 5)
        val state = WorkoutUiState(session = session,completedSets = mapOf(workoutSetKey(done.id,0) to completed),
            manualLoadOverrides = mapOf(workoutSetKey(manual.id,0) to 45.0))
        val refreshed = sessionWithConfirmedTrainingMax(state,program(session),source,100.0)!!
        assertEquals(listOf(100.0,100.0,100.0,100.0,null),refreshed.exercises.map { it.referenceTrainingMax })
        assertEquals(listOf(80.0,40.0,40.0,80.0,40.0),refreshed.exercises.map { it.sets.single().weight })
        assertEquals(completed,state.completedSets.values.single())
        assertEquals(other,refreshed.exercises.last())
    }
    @Test fun doneLeftStaysExactWhilePendingRightGetsTheConfirmedBase() {
        val source = exercise("source")
        val unilateral = exercise("uni").copy(unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
            sets = listOf(exercise("uni").sets.single().copy(leftTarget = UnilateralTarget(weight = 40.0),rightTarget = UnilateralTarget(weight = 50.0))))
        val session = Session("s","Fuerza",exercises = listOf(source,unilateral))
        val completed = CompletedSet("left",weight = 40.0,reps = 5,side = "left")
        val state = WorkoutUiState(session = session,completedSets = mapOf(workoutSetKey("uni",0,"left") to completed))
        val updated = sessionWithConfirmedTrainingMax(state,program(session),source,100.0)!!.exercises.last()
        assertEquals(40.0,updated.sets.single().leftTarget!!.weight!!,0.0)
        assertEquals(80.0,updated.sets.single().rightTarget!!.weight!!,0.0)
        assertEquals(40.0,updated.sets.single().weight!!,0.0)
    }
    @Test fun aManualVariantSessionKeepsItsExplicitBaseAndDose() {
        val source = exercise("source")
        val manual = exercise("manual").copy(referenceTrainingMax = 60.0)
        val session = Session("s","Fuerza",exercises = listOf(source),sessionB = Session("manual-session","B",exercises = listOf(manual)))
        val state = WorkoutUiState(session = session)
        val updated = sessionWithConfirmedTrainingMax(state,program(session,listOf(ManualSessionOverride("manual-session"))),source,100.0)!!
        assertEquals(manual,updated.sessionB!!.exercises.single())
        assertEquals(100.0,updated.exercises.single().referenceTrainingMax!!,0.0)
    }
}
