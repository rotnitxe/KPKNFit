package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.workout.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorRulesEngineTest {

    private val squatInfo = ExerciseMuscleInfo(
        id = "squat",
        name = "Back Squat",
        force = "Empuje",
        involvedMuscles = listOf(InvolvedMuscle("cuádriceps", MuscleRole.PRIMARY)),
    )

    private val benchInfo = ExerciseMuscleInfo(
        id = "bench",
        name = "Bench Press",
        force = "Empuje",
        involvedMuscles = listOf(InvolvedMuscle("pectoral mayor", MuscleRole.PRIMARY)),
    )

    private val exerciseIndex = mapOf(
        squatInfo.id.lowercase() to squatInfo,
        benchInfo.id.lowercase() to benchInfo,
    )

    @Test
    fun applyDefaults_rewrites_sets_with_target_defaults() {
        val session = Session(
            id = "session-1",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "exercise-1",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    sets = listOf(
                        ExerciseSet(id = "set-1", targetReps = 8, targetRPE = 8.0),
                    ),
                )
            ),
        )

        val result = SessionEditorRulesEngine.applyDefaults(
            session = session,
            defaults = SessionEditorRuleDefaults(setCount = 4, reps = 6, rpe = 7.5),
            partId = null,
        )

        val sets = result.exercises.first().sets
        assertEquals(4, sets.size)
        assertEquals(6, sets.first().targetReps)
        assertEquals(7.5, sets.first().targetRPE ?: 0.0, 0.001)
        assertEquals(IntensityMode.RPE, sets.first().intensityMode)
    }

    @Test
    fun applyGlobalIntensityAdjustment_scopes_to_selected_muscles() {
        val session = Session(
            id = "session-2",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "sq-ex",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    sets = listOf(ExerciseSet(id = "sq-set", targetRPE = 8.0, intensityMode = IntensityMode.RPE)),
                ),
                Exercise(
                    id = "bp-ex",
                    name = "Bench Press",
                    exerciseDbId = "bench",
                    sets = listOf(ExerciseSet(id = "bp-set", targetRPE = 8.0, intensityMode = IntensityMode.RPE)),
                ),
            ),
        )

        val result = SessionEditorRulesEngine.applyGlobalIntensityAdjustment(
            session = session,
            targetMode = IntensityMode.RIR,
            value = 2.0,
            targetMuscles = setOf("Cuádriceps"),
            exerciseIndex = exerciseIndex,
        )

        val squatSet = result.exercises.first { it.exerciseDbId == "squat" }.sets.first()
        val benchSet = result.exercises.first { it.exerciseDbId == "bench" }.sets.first()

        assertEquals(IntensityMode.RIR, squatSet.intensityMode)
        assertEquals(2, squatSet.targetRIR)
        assertNull(squatSet.targetRPE)

        assertEquals(IntensityMode.RPE, benchSet.intensityMode)
        assertEquals(8.0, benchSet.targetRPE ?: 0.0, 0.001)
    }

    @Test
    fun validateBeforeSave_rigid_limit_blocks_over_max_rpe() {
        val draft = Session(
            id = "session-3",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "exercise-1",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    sets = listOf(ExerciseSet(id = "set-1", targetRPE = 9.5, intensityMode = IntensityMode.RPE)),
                )
            ),
        )

        val result = SessionEditorRulesEngine.validateBeforeSave(
            draft = draft,
            weekSessions = listOf(draft),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 8.0, rigidLimits = true),
            exerciseIndex = exerciseIndex,
        )

        assertNotNull(result.blockingError)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun validateBeforeSave_flexible_limit_warns_instead_of_blocking() {
        val draft = Session(
            id = "session-4",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "exercise-1",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    sets = listOf(ExerciseSet(id = "set-1", targetRPE = 9.0, intensityMode = IntensityMode.RPE)),
                )
            ),
        )

        val result = SessionEditorRulesEngine.validateBeforeSave(
            draft = draft,
            weekSessions = listOf(draft),
            ruleLimits = SessionEditorRuleLimits(maxRPE = 8.0, rigidLimits = false),
            exerciseIndex = exerciseIndex,
        )

        assertNull(result.blockingError)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun validateBeforeSave_weekly_volume_limit_uses_draft_aware_week() {
        val draft = Session(
            id = "session-a",
            name = "Sesion A",
            exercises = listOf(
                Exercise(
                    id = "exercise-a",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    sets = listOf(
                        ExerciseSet(id = "a-1", targetRPE = 8.0),
                        ExerciseSet(id = "a-2", targetRPE = 8.0),
                        ExerciseSet(id = "a-3", targetRPE = 8.0),
                    ),
                )
            ),
        )
        val otherSession = Session(
            id = "session-b",
            name = "Sesion B",
            exercises = listOf(
                Exercise(
                    id = "exercise-b",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    sets = listOf(
                        ExerciseSet(id = "b-1", targetRPE = 8.0),
                        ExerciseSet(id = "b-2", targetRPE = 8.0),
                        ExerciseSet(id = "b-3", targetRPE = 8.0),
                    ),
                )
            ),
        )

        val result = SessionEditorRulesEngine.validateBeforeSave(
            draft = draft,
            weekSessions = listOf(draft, otherSession),
            ruleLimits = SessionEditorRuleLimits(maxVolumePerMuscleWeekly = 4.0, rigidLimits = true),
            exerciseIndex = exerciseIndex,
        )

        assertNotNull(result.blockingError)
    }

    @Test
    fun buildSessionExerciseEditorBlocks_keepsSupersetExercisesEditableIndividually() {
        val a = Exercise(id = "a", name = "A", sets = listOf(ExerciseSet("a1")))
        val b = Exercise(id = "b", name = "B", sets = listOf(ExerciseSet("b1")))
        val c = Exercise(id = "c", name = "C", sets = listOf(ExerciseSet("c1")))
        val session = SupersetRules.createSuperset(
            session = Session(id = "session-superset", name = "Sesion", exercises = listOf(a, b, c)),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 30,
            restAfterSuperset = 120,
        )

        val blocks = buildSessionExerciseEditorBlocks(session, session.exercises)

        assertEquals(3, blocks.size)
        assertEquals(listOf("a", "b", "c"), blocks.map { (it as SessionExerciseEditorBlock.Single).exercise.id })
        assertEquals(listOf("ss-1", "ss-1", null), blocks.map { (it as SessionExerciseEditorBlock.Single).exercise.supersetGroupRef })
    }
}
