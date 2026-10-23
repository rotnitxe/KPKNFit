package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.relator.RelatorProgressionContext
import com.example.kpkn.data.protocols.ProgressionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorPlanProgressionContextTest {
    private val recipe = TrainingPlanRecipe(
        id = "apre-mann",
        weeks = emptyList(),
        progression = ProgressionRule.ApreXrm(),
    )

    private val apreExercise = Exercise(
        id = "lift",
        name = "Sentadilla",
        catalogConfigurationId = "squat_low_bar",
        slotRole = SlotRole.T1_MAIN,
        sets = listOf(
            ExerciseSet(id = "s1", targetReps = 12, weight = 75.0),
            ExerciseSet(id = "s2", targetReps = 10, weight = 112.5),
            ExerciseSet(
                id = "s3", targetReps = 10, weight = 150.0, targetPercentageRM = 81.52, targetRPE = 8.0,
                purpose = SetPurpose.AMRAP, amrapMinimumReps = 10,
            ),
            ExerciseSet(
                id = "s4", targetReps = 10, weight = 150.0, targetPercentageRM = 81.52,
                purpose = SetPurpose.AMRAP, amrapMinimumReps = 10,
            ),
        ),
        apreBlock = ApreBlockReference(xrmReps = 10, setIds = listOf("s1", "s2", "s3", "s4")),
    )

    @Test
    fun uses_the_just_logged_set_after_the_cursor_has_advanced_and_keeps_real_hit_fields() {
        val state = WorkoutUiState(
            currentExerciseIdx = 1,
            currentSetIdx = 0,
            setJustLoggedKey = "lift_2",
            completedSets = mapOf(
                "lift_2" to CompletedSet(
                    id = "s3",
                    weight = 150.0,
                    reps = 12,
                    rir = 1,
                    isFailure = true,
                    partialReps = 2,
                    amrapPerformed = true,
                    purpose = SetPurpose.AMRAP,
                    amrapMinimumReps = 10,
                    setIndex = 2,
                ),
            ),
        )
        val nextExercise = Exercise(
            id = "next",
            name = "Press banca",
            sets = listOf(ExerciseSet(id = "next-s1", targetReps = 8)),
        )
        val progression = relatorProgressionContextFor(
            state = state,
            visibleExercises = listOf(apreExercise, nextExercise),
            currentExercise = nextExercise,
            currentSet = nextExercise.sets.first(),
            base = RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )

        val hit = progression.hit
        assertNotNull(hit)
        assertEquals(LiftSlot.SQUAT, hit!!.liftSlot)
        assertEquals(150.0, hit.weightKg!!, 1e-9)
        assertEquals(12, hit.actualReps)
        assertEquals(10, hit.prescribedReps)
        assertEquals(81.52, hit.percent!!, 1e-9)
        assertEquals(1.0, hit.weightRatio!!, 1e-9)
        assertNull(hit.meanRpe)
        assertEquals(1, hit.declaredRir)
        assertTrue(hit.reachedFailure)
        assertEquals(2, hit.partialReps)
        assertFalse(hit.isLastAmrapOfExercise)
        assertEquals(SlotRole.T1_MAIN, progression.slotRole)
        assertEquals(42, progression.plannedVolumeReps)
        assertEquals(10, progression.plannedMinimumReps)
        assertTrue(progression.isApreBlock)
        assertNull(progression.tierCheck)
        assertNull(progression.frozenCyclesBefore)
    }

    @Test
    fun ordinary_amrap_and_explicit_opt_out_do_not_become_apre() {
        val ordinaryExercise = apreExercise.copy(apreBlock = null)
        val ordinaryState = WorkoutUiState(
            setJustLoggedKey = "lift_2",
            completedSets = mapOf(
                "lift_2" to CompletedSet(
                    id = "s3", weight = 150.0, reps = 12, amrapPerformed = true,
                    purpose = SetPurpose.AMRAP, amrapMinimumReps = 10, setIndex = 2,
                ),
            ),
        )
        val ordinary = relatorProgressionContextFor(
            ordinaryState, listOf(ordinaryExercise), ordinaryExercise, ordinaryExercise.sets[2],
            RelatorProgressionContext(recipe = recipe),
        )
        assertNotNull(ordinary.hit)
        assertFalse(ordinary.isApreBlock)

        val legacyApre = relatorProgressionContextFor(
            ordinaryState, listOf(ordinaryExercise), ordinaryExercise, ordinaryExercise.sets[2],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertTrue(legacyApre.isApreBlock)

        val invalidMarker = ordinaryExercise.copy(
            apreBlock = ApreBlockReference(xrmReps = 10, setIds = listOf("s3", "s4")),
        )
        val invalidExplicitMarker = relatorProgressionContextFor(
            ordinaryState, listOf(invalidMarker), invalidMarker, invalidMarker.sets[2],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertFalse(invalidExplicitMarker.isApreBlock)

        val optedOut = ordinaryState.copy(
            completedSets = mapOf(
                "lift_2" to CompletedSet(
                    id = "s3", weight = 150.0, reps = 12, amrapPerformed = false,
                    purpose = SetPurpose.NONE, amrapMinimumReps = 10, setIndex = 2,
                ),
            ),
        )
        val noHit = relatorProgressionContextFor(
            optedOut, listOf(apreExercise), apreExercise, apreExercise.sets[2],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertNull(noHit.hit)
        assertFalse(noHit.isApreBlock)
    }
}
