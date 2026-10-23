package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorAmrapTest {
    @Test fun materializedAuthorRecipeSurvivesEditingAndTheActualSaveNormalization() {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val recipe = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }.recipe!!
        val program = com.example.kpkn.domain.training.PlanMaterializer.materialize(
            com.example.kpkn.data.models.Program("cal1", "5/3/1"), recipe,
            com.example.kpkn.domain.training.CatalogCompositionTestSupport.metadata,
            profile = com.example.kpkn.data.models.PowerliftingProfile(squat1RM = 200.0, bench1RM = 100.0, deadlift1RM = 220.0),
        )
        val original = program.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.first()
        val source = original.allExercises().first { it.sets.any { set -> set.purpose.isAmrap } }
        assertEquals(TrainingMode.RM, source.trainingMode)
        val saved = original.copy(description = "Otro campo editado").normalizedForEditorSave()
        val after = saved.allExercises().first { it.id == source.id }
        assertEquals(source.sets.map { it.purpose }, after.sets.map { it.purpose })
        assertEquals(source.sets.map { it.amrapMinimumReps }, after.sets.map { it.amrapMinimumReps })
    }
    @Test
    fun selectingAmrap_inEditorTurnsEverySetIntoAnExecutableAmrap() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.AMRAP,
            sets = listOf(
                ExerciseSet(id = "set", targetReps = 6, targetRepsRange = RepRange(4, 6), targetRPE = 8.0),
            ),
        )

        val normalized = exercise.withTrainingMode(TrainingMode.AMRAP).normalizeExercise().sets.single()

        assertTrue(normalized.purpose.isAmrap)
        assertNull(normalized.intensityMode)
        assertEquals(4, normalized.amrapMinimumReps)
        assertEquals(6, normalized.targetReps)
        assertEquals(4, normalized.targetRepsRange?.min)
        assertNull(normalized.targetRPE)
    }

    @Test
    fun switchingAwayFromAmrap_clearsThePersistedAmrapMarker() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            sets = listOf(
                ExerciseSet(
                    id = "set",
                    targetReps = 6,
                    targetRepsRange = RepRange(4, 6),
                    intensityMode = IntensityMode.AMRAP,
                    purpose = SetPurpose.AMRAP,
                ),
            ),
        )

        val normalized = exercise.withTrainingMode(TrainingMode.REPS).normalizeExercise().sets.single()

        assertEquals(TrainingMode.REPS, exercise.trainingMode)
        assertEquals(6, normalized.targetReps)
        assertEquals(RepRange(4, 6), normalized.targetRepsRange)
        assertNull(normalized.intensityMode)
        assertTrue(!normalized.purpose.isAmrap)
    }

    @Test fun mixedRmRecipeKeepsItsFinalAmrapWhenAnotherFieldIsEditedAndSaved() {
        val exercise = Exercise(
            id = "rm", name = "Press", trainingMode = TrainingMode.RM,
            referenceTrainingMax = 90.0,
            sets = listOf(
                ExerciseSet("normal", targetReps = 5, targetPercentageRM = 75.0),
                ExerciseSet("plus", targetReps = 6, targetRepsRange = RepRange(4, 6),
                    targetPercentageRM = 85.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 4),
            ),
        )
        val edited = exercise.copy(restTime = 180).normalizeExercise()
        val saved = Session("s", "Sesión", exercises = listOf(edited)).normalizedForEditorSave()
        val restored = kotlinx.serialization.json.Json.decodeFromString<Session>(
            kotlinx.serialization.json.Json.encodeToString(Session.serializer(), saved),
        ).exercises.single()
        assertEquals(SetPurpose.NONE, restored.sets[0].purpose)
        assertEquals(SetPurpose.AMRAP, restored.sets[1].purpose)
        assertEquals(4, restored.sets[1].amrapMinimumReps)
        assertEquals(TrainingMode.RM, restored.trainingMode)
        assertEquals(180, restored.restTime)
    }

    @Test fun noMinimumRemainsExplicitAndInvalidMinimaBlockSavingWithoutRigidRules() {
        for (minimum in listOf(null, 0, -1, 4)) {
            val set = ExerciseSet("a", targetReps = 6, purpose = SetPurpose.AMRAP, amrapMinimumReps = minimum)
            val session = Session("s", "Sesión", exercises = listOf(Exercise("e", "Press", sets = listOf(set))))
                .normalizedForEditorSave()
            assertEquals(minimum, session.exercises.single().sets.single().amrapMinimumReps)
            val result = SessionEditorRulesEngine.validateBeforeSave(session, emptyList(), SessionEditorRuleLimits(), emptyMap())
            assertEquals(minimum != null && minimum <= 0, result.blockingError != null)
        }
    }
}
