package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.screens.sessioneditor.components.buildSessionExerciseEditorBlocks
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnilateralTarget
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
    fun validateBeforeSave_ignores_legacy_rigid_limits() {
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

        assertNull(result.blockingError)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun validateBeforeSave_ignores_legacy_flexible_limits() {
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
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun validateBeforeSave_ignores_legacy_weekly_volume_limit() {
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

        assertNull(result.blockingError)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun applyDefaults_preserves_rest_between_sides_when_rule_is_zero() {
        val session = Session(
            id = "session-f1",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "ex-1",
                    name = "Lunge",
                    restBetweenSidesSeconds = 45,
                    sets = listOf(ExerciseSet(id = "s-1", targetReps = 10, targetRPE = 8.0, intensityMode = IntensityMode.RPE)),
                )
            ),
        )
        val keep = SessionEditorRulesEngine.applyDefaults(
            session = session,
            defaults = SessionEditorRuleDefaults(betweenSidesRestSeconds = 0),
            partId = null,
        )
        assertEquals(45, keep.exercises.first().restBetweenSidesSeconds)

        val overwrite = SessionEditorRulesEngine.applyDefaults(
            session = session,
            defaults = SessionEditorRuleDefaults(betweenSidesRestSeconds = 30),
            partId = null,
        )
        assertEquals(30, overwrite.exercises.first().restBetweenSidesSeconds)
    }

    @Test
    fun applyDefaults_updates_existing_unilateral_targets_without_creating_missing_side() {
        val session = Session(
            id = "session-unilateral-rules",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "unilateral-exercise",
                    name = "Split Squat",
                    isUnilateral = true,
                    sets = listOf(
                        ExerciseSet(
                            id = "unilateral-set",
                            leftTarget = UnilateralTarget(targetReps = 10, targetRPE = 8.0),
                            rightTarget = null,
                        ),
                    ),
                ),
            ),
        )

        val result = SessionEditorRulesEngine.applyDefaults(
            session = session,
            defaults = SessionEditorRuleDefaults(setCount = 1, reps = 6, rpe = 7.0),
            partId = null,
        )

        val set = result.exercises.single().sets.single()
        assertEquals(6, set.leftTarget?.targetReps)
        assertEquals(7.0, set.leftTarget?.targetRPE ?: 0.0, 0.001)
        assertNull(set.rightTarget)
    }

    @Test
    fun applyDefaults_uses_compound_and_isolation_overrides_in_the_selected_scope() {
        val compoundInfo = ExerciseMuscleInfo(
            id = "compound",
            name = "Compound",
            articulationType = "MULTIARTICULAR",
        )
        val isolationInfo = ExerciseMuscleInfo(
            id = "isolation",
            name = "Isolation",
            articulationType = "AISLADO",
        )
        val session = Session(
            id = "session-compound-isolation-rules",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "compound-exercise",
                    name = "Compound",
                    exerciseDbId = "compound",
                    sets = listOf(ExerciseSet("compound-set")),
                ),
                Exercise(
                    id = "isolation-exercise",
                    name = "Isolation",
                    exerciseDbId = "isolation",
                    sets = listOf(ExerciseSet("isolation-set")),
                ),
            ),
        )
        val defaults = SessionEditorRuleDefaults(
            scope = RuleScope.COMPOUND_ISOLATION,
            setCount = 2,
            reps = 10,
            rpe = 8.0,
            compoundReps = 5,
            compoundRpe = 6.0,
            compoundRestSeconds = 120,
            isolationReps = 12,
            isolationRpe = 9.0,
            isolationRestSeconds = 45,
        )

        val result = SessionEditorRulesEngine.applyDefaults(
            session = session,
            defaults = defaults,
            partId = null,
            exerciseIndex = mapOf("compound" to compoundInfo, "isolation" to isolationInfo),
        )

        val compound = result.exercises.first { it.exerciseDbId == "compound" }
        val isolation = result.exercises.first { it.exerciseDbId == "isolation" }
        assertEquals(5, compound.sets.first().targetReps)
        assertEquals(6.0, compound.sets.first().targetRPE ?: 0.0, 0.001)
        assertEquals(120, compound.restTime)
        assertEquals(12, isolation.sets.first().targetReps)
        assertEquals(9.0, isolation.sets.first().targetRPE ?: 0.0, 0.001)
        assertEquals(45, isolation.restTime)
    }

    @Test
    fun evaluateApply_returns_NoChanges_when_defaults_match_session() {
        val session = Session(
            id = "session-noop",
            name = "Sesion",
            exercises = listOf(
                Exercise(
                    id = "ex-1",
                    name = "Back Squat",
                    exerciseDbId = "squat",
                    restTime = 90,
                    restBetweenSidesSeconds = 0,
                    sets = listOf(
                        ExerciseSet(id = "s-1", targetReps = 10, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
                        ExerciseSet(id = "s-2", targetReps = 10, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
                        ExerciseSet(id = "s-3", targetReps = 10, targetRPE = 8.0, intensityMode = IntensityMode.RPE),
                    ),
                )
            ),
        )
        val defaults = SessionEditorRuleDefaults(
            setCount = 3, reps = 10, rpe = 8.0, normalRestSeconds = 90, betweenSidesRestSeconds = 0,
            intensityType = DefaultIntensityType.RPE,
        )
        val outcome = SessionEditorRulesEngine.evaluateApply(session, defaults, partId = null, exerciseIndex = exerciseIndex)
        assertTrue(outcome is ApplyRulesOutcome.NoChanges)
        // applyDefaults itself must also be a no-op (equals)
        assertEquals(session, SessionEditorRulesEngine.applyDefaults(session, defaults, partId = null, exerciseIndex = exerciseIndex))
    }

    @Test
    fun evaluateApply_returns_ScopeNotFound_for_missing_or_empty_part() {
        val emptyPart = com.example.kpkn.data.models.SessionPart(id = "part-empty", name = "Vacío", exercises = emptyList())
        val partWithEx = com.example.kpkn.data.models.SessionPart(
            id = "part-full", name = "Grupo A",
            exercises = listOf(Exercise(id = "ex-1", name = "Back Squat", exerciseDbId = "squat", sets = listOf(ExerciseSet(id = "s-1")))),
        )
        val session = Session(
            id = "session-scope",
            name = "Sesion",
            parts = listOf(emptyPart, partWithEx),
            exercises = emptyList(),
        )
        val defaults = SessionEditorRuleDefaults(setCount = 3)
        assertTrue(SessionEditorRulesEngine.evaluateApply(session, defaults, partId = "no-existe") is ApplyRulesOutcome.ScopeNotFound)
        assertTrue(SessionEditorRulesEngine.evaluateApply(session, defaults, partId = "part-empty") is ApplyRulesOutcome.ScopeNotFound)
        // applyDefaults with missing/empty scope must return identical session
        assertEquals(session, SessionEditorRulesEngine.applyDefaults(session, defaults, partId = "no-existe"))
        assertEquals(session, SessionEditorRulesEngine.applyDefaults(session, defaults, partId = "part-empty"))
    }

    @Test
    fun evaluateApply_returns_Applied_with_changed_exercise_count() {
        val session = Session(
            id = "session-applied",
            name = "Sesion",
            exercises = listOf(
                Exercise(id = "ex-1", name = "Back Squat", exerciseDbId = "squat", restTime = 90, sets = listOf(ExerciseSet(id = "s-1", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE))),
                Exercise(id = "ex-2", name = "Bench Press", exerciseDbId = "bench", restTime = 90, sets = listOf(ExerciseSet(id = "s-2", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE))),
            ),
        )
        val defaults = SessionEditorRuleDefaults(setCount = 4, reps = 6, rpe = 7.5, normalRestSeconds = 60, betweenSidesRestSeconds = 0)
        val outcome = SessionEditorRulesEngine.evaluateApply(session, defaults, partId = null, exerciseIndex = exerciseIndex)
        assertTrue(outcome is ApplyRulesOutcome.Applied)
        assertEquals(2, (outcome as ApplyRulesOutcome.Applied).exercisesChanged)
        val applied = SessionEditorRulesEngine.applyDefaults(session, defaults, partId = null, exerciseIndex = exerciseIndex)
        assertEquals(4, applied.exercises.first().sets.size)
        assertEquals(6, applied.exercises.first().sets.first().targetReps)
        assertEquals(60, applied.exercises.first().restTime)
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
