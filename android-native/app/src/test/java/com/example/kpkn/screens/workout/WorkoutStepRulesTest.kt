package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepRulesTest {
    @Test
    fun buildSteps_emitsDedicatedCardioStep_andIgnoresLegacySyntheticSets() {
        val cardio = Exercise(
            id = "cardio-1",
            name = "Cinta",
            sets = listOf(ExerciseSet("legacy-strength-set")),
            warmupSets = listOf(WarmupSetDefinition(id = "legacy-warmup", percentageOfWorkingWeight = 50.0, targetReps = 5)),
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
        )

        val steps = WorkoutStepRules.buildSteps(
            Session(id = "s", name = "Sesion", exercises = listOf(cardio)),
        )

        assertEquals(listOf(WorkoutStepType.CARDIO), steps.map { it.type })
        assertEquals("cardio-1_cardio", steps.single().stepKey)
        assertEquals(0, steps.single().setIndex)
    }

    @Test
    fun buildSteps_places_global_mobility_parts_before_strength_without_affecting_strength_order() {
        val mobility = SessionPart(
            id = "mobility-part",
            name = "Movilidad global",
            isMobilityGroup = true,
            mobilitySeries = listOf(
                MobilitySeries(id = "hips", name = "Cadera", durationSeconds = 45),
                MobilitySeries(id = "ankles", name = "Tobillo", durationSeconds = 30),
            ),
        )
        val strength = Exercise(id = "squat", name = "Sentadilla", sets = listOf(ExerciseSet("s1")))

        val steps = WorkoutStepRules.buildSteps(
            Session(id = "s", name = "Sesion", parts = listOf(mobility), exercises = listOf(strength)),
        )

        assertEquals(
            listOf(WorkoutStepType.MOBILITY_GROUP, WorkoutStepType.MOBILITY_GROUP, WorkoutStepType.WORKING_SET),
            steps.map { it.type },
        )
        assertEquals(listOf("hips", "ankles", "squat"), steps.map { it.mobilitySeriesId ?: it.exerciseId })
        assertEquals("squat_0", steps.last().stepKey)
    }

    @Test
    fun buildSteps_ordersMobilityWarmupBeforeWorkingSets() {
        val exercise = Exercise(
            id = "squat",
            name = "Sentadilla",
            mobilitySeries = listOf(MobilitySeries(id = "mob-1", name = "Tobillo")),
            warmupSets = listOf(WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 5)),
            sets = listOf(ExerciseSet(id = "s1")),
        )

        val steps = WorkoutStepRules.buildSteps(Session(id = "s", name = "Sesion", exercises = listOf(exercise)))

        assertEquals(listOf(WorkoutStepType.MOBILITY, WorkoutStepType.WARMUP, WorkoutStepType.WORKING_SET), steps.map { it.type })
        assertEquals("squat_mob-1", steps[0].stepKey)
        assertEquals("squat_warmup_w1", steps[1].stepKey)
        assertEquals("squat_0", steps[2].stepKey)
        assertEquals(RestTimerKind.WARMUP, steps[1].restAfterKind)
    }

    @Test
    fun buildSteps_expandsConfiguredMobilitySetsIntoIndependentSteps() {
        val exercise = Exercise(
            id = "squat",
            name = "Sentadilla",
            mobilitySeries = listOf(
                MobilitySeries(id = "ankle", name = "Tobillo", sets = 3, reps = "8", restBetweenSeconds = 20),
            ),
            sets = listOf(ExerciseSet("s1")),
        )

        val steps = WorkoutStepRules.buildSteps(
            Session(id = "s", name = "Sesion", exercises = listOf(exercise)),
        )

        assertEquals(
            listOf("squat_ankle", "squat_ankle_set_1", "squat_ankle_set_2", "squat_0"),
            steps.map { it.stepKey },
        )
        assertEquals(listOf(0, 1, 2), steps.take(3).map { it.mobilitySetIndex })
        assertEquals(listOf(20, 20, 20), steps.take(3).map { it.mobilitySeries.single().restBetweenSeconds })
    }

    @Test
    fun buildSteps_expandsUnilateralSidesInConfiguredOrder() {
        val exercise = Exercise(
            id = "curl",
            name = "Curl",
            unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
            unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
            restBetweenSidesSeconds = 20,
            sets = listOf(ExerciseSet(id = "s1")),
        )

        val steps = WorkoutStepRules.buildSteps(Session(id = "s", name = "Sesion", exercises = listOf(exercise)))

        assertEquals(listOf("right", "left"), steps.map { it.side })
        assertEquals(RestTimerKind.BETWEEN_SIDES, steps.first().restAfterKind)
    }

    @Test
    fun buildSteps_doesNotStartBetweenSidesTimerWhenRestIsZero() {
        val exercise = Exercise(
            id = "curl",
            name = "Curl",
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            restBetweenSidesSeconds = 0,
            sets = listOf(ExerciseSet(id = "s1")),
        )

        val steps = WorkoutStepRules.buildSteps(Session(id = "s", name = "Sesion", exercises = listOf(exercise)))

        assertEquals(listOf("left", "right"), steps.map { it.side })
        assertEquals(RestTimerKind.STANDARD, steps.first().restAfterKind)
    }

    @Test
    fun buildSteps_allowsOneSidedExtraUnilateralSet() {
        val exercise = Exercise(
            id = "curl",
            name = "Curl",
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            sets = listOf(
                ExerciseSet(id = "s1"),
                ExerciseSet(id = "s2", leftTarget = UnilateralTarget(targetReps = 10), rightTarget = null),
                ExerciseSet(id = "s3", leftTarget = null, rightTarget = UnilateralTarget(targetReps = 12)),
            ),
        )

        val steps = WorkoutStepRules.buildSteps(Session(id = "s", name = "Sesion", exercises = listOf(exercise)))
            .filter { it.type == WorkoutStepType.WORKING_SET }

        assertEquals(listOf(0, 0, 1, 2), steps.map { it.setIndex })
        assertEquals(listOf("left", "right", "left", "right"), steps.map { it.side })
    }

    @Test
    fun buildSteps_interleavesSupersetRoundsAndRestKinds() {
        val a = Exercise(id = "a", name = "A", sets = listOf(ExerciseSet("a1"), ExerciseSet("a2")))
        val b = Exercise(id = "b", name = "B", sets = listOf(ExerciseSet("b1")))
        val session = SupersetRules.createSuperset(
            session = Session(id = "s", name = "Sesion", exercises = listOf(a, b)),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 30,
            restAfterSuperset = 120,
        )

        val workingSteps = WorkoutStepRules.buildSteps(session)
            .filter { it.type == WorkoutStepType.WORKING_SET }

        assertEquals(listOf("a", "b", "a"), workingSteps.map { it.exerciseId })
        assertEquals(listOf(0, 0, 1), workingSteps.map { it.setIndex })
        assertEquals(RestTimerKind.SUPERSET_INTRA, workingSteps[0].restAfterKind)
        assertEquals(RestTimerKind.SUPERSET_ROUND, workingSteps[1].restAfterKind)
        assertEquals(RestTimerKind.SUPERSET_ROUND, workingSteps[2].restAfterKind)
    }

    @Test
    fun buildSteps_expandsUnilateralSupersetMemberInConfiguredSideOrder() {
        val unilateral = Exercise(
            id = "a",
            name = "A",
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
            restBetweenSidesSeconds = 15,
            sets = listOf(ExerciseSet("a1")),
        )
        val bilateral = Exercise(id = "b", name = "B", sets = listOf(ExerciseSet("b1")))
        val session = SupersetRules.createSuperset(
            session = Session(id = "s", name = "Sesion", exercises = listOf(unilateral, bilateral)),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 30,
            restAfterSuperset = 120,
        )

        val workingSteps = WorkoutStepRules.buildSteps(session)
            .filter { it.type == WorkoutStepType.WORKING_SET }

        assertEquals(listOf("a", "a", "b"), workingSteps.map { it.exerciseId })
        assertEquals(listOf("right", "left", null), workingSteps.map { it.side })
        assertEquals(RestTimerKind.BETWEEN_SIDES, workingSteps[0].restAfterKind)
        assertEquals(RestTimerKind.SUPERSET_INTRA, workingSteps[1].restAfterKind)
        assertEquals(RestTimerKind.SUPERSET_ROUND, workingSteps[2].restAfterKind)
    }

    @Test
    fun buildSteps_keepsSupersetMobilityOneCardPerSeries() {
        val a = Exercise(
            id = "a",
            name = "A",
            mobilitySeries = listOf(MobilitySeries(id = "mob-a", name = "Cadera")),
            sets = listOf(ExerciseSet("a1")),
        )
        val b = Exercise(
            id = "b",
            name = "B",
            mobilitySeries = listOf(MobilitySeries(id = "mob-b", name = "Escapula")),
            sets = listOf(ExerciseSet("b1")),
        )
        val session = SupersetRules.createSuperset(
            session = Session(id = "s", name = "Sesion", exercises = listOf(a, b)),
            groupId = "ss-1",
            exerciseIds = listOf("a", "b"),
            restBetweenExercises = 30,
            restAfterSuperset = 120,
        )

        val steps = WorkoutStepRules.buildSteps(session)
        val mobilitySteps = steps.filter { it.type == WorkoutStepType.MOBILITY }

        assertEquals(2, mobilitySteps.size)
        assertEquals(listOf("Movilidad de superserie", "Movilidad de superserie"), mobilitySteps.map { it.exerciseName })
        assertEquals(listOf("a_mob-a", "b_mob-b"), mobilitySteps.map { it.stepKey })
        assertEquals(listOf("mob-a"), mobilitySteps.first().mobilitySeries.map { it.id })
        assertEquals(listOf("mob-b"), mobilitySteps.last().mobilitySeries.map { it.id })
        assertEquals(listOf(WorkoutStepType.MOBILITY, WorkoutStepType.MOBILITY, WorkoutStepType.WORKING_SET, WorkoutStepType.WORKING_SET), steps.map { it.type })
    }

    @Test
    fun buildWorkingPositions_expandsUnilateralSidesAndBuildSetPositionsDeduplicates() {
        val unilateral = Exercise(
            id = "lunges",
            name = "Lunges",
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            sets = listOf(ExerciseSet("l1"), ExerciseSet("l2")),
        )
        val row = Exercise(id = "row", name = "Row", sets = listOf(ExerciseSet("r1")))

        val positions = WorkoutStepRules.buildWorkingPositions(
            Session(id = "s", name = "Sesion", exercises = listOf(unilateral, row)),
        )
        val setPositions = WorkoutStepRules.buildSetPositions(
            Session(id = "s", name = "Sesion", exercises = listOf(unilateral, row)),
        )

        assertEquals(listOf("lunges", "lunges", "lunges", "lunges", "row"), positions.map { it.exerciseId })
        assertEquals(listOf(0, 0, 1, 1, 0), positions.map { it.setIndex })
        assertEquals(listOf("left", "right", "left", "right", null), positions.map { it.side })
        assertEquals(listOf("lunges", "lunges", "row"), setPositions.map { it.exerciseId })
        assertEquals(listOf(0, 1, 0), setPositions.map { it.setIndex })
    }

    @Test
    fun buildWorkingPositions_isTheCanonicalSupersetNavigationOrder() {
        val a = Exercise(id = "a", name = "A", sets = listOf(ExerciseSet("a1"), ExerciseSet("a2")))
        val b = Exercise(id = "b", name = "B", sets = listOf(ExerciseSet("b1"), ExerciseSet("b2")))
        val session = SupersetRules.createSuperset(
            session = Session(id = "s", name = "Sesion", exercises = listOf(a, b)),
            groupId = "ss-1",
            exerciseIds = listOf("b", "a"),
            restBetweenExercises = 0,
            restAfterSuperset = 90,
        )

        val positions = WorkoutStepRules.buildWorkingPositions(session)

        assertEquals(listOf("b", "a", "b", "a"), positions.map { it.exerciseId })
        assertEquals(listOf(0, 0, 1, 1), positions.map { it.setIndex })
    }
}
