package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules

enum class WorkoutStepType {
    MOBILITY,
    WARMUP,
    WORKING_SET,
}

data class WorkoutStep(
    val type: WorkoutStepType,
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int? = null,
    val side: String? = null,
    val supersetGroupId: String? = null,
    val supersetRoundIndex: Int? = null,
    val mobilitySeries: List<MobilitySeries> = emptyList(),
    val restAfterKind: RestTimerKind = RestTimerKind.STANDARD,
)

object WorkoutStepRules {
    fun buildSteps(session: Session, visibleExercises: List<Exercise> = session.allExercises()): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        val emittedSupersets = mutableSetOf<String>()

        visibleExercises.forEach { exercise ->
            val groupId = exercise.supersetGroupRefOrLegacyId()
            if (groupId != null) {
                if (emittedSupersets.add(groupId)) {
                    appendSupersetSteps(session, visibleExercises, groupId, steps)
                }
            } else {
                appendExerciseSteps(exercise, groupId = null, steps = steps)
            }
        }

        return steps
    }

    fun buildWorkingPositions(
        session: Session,
        visibleExercises: List<Exercise> = session.allExercises(),
    ): List<WorkoutStep> {
        return buildSteps(session, visibleExercises)
            .filter { it.type == WorkoutStepType.WORKING_SET && it.setIndex != null }
    }

    fun buildSetPositions(
        session: Session,
        visibleExercises: List<Exercise> = session.allExercises(),
    ): List<WorkoutStep> {
        val emitted = mutableSetOf<Pair<String, Int>>()
        return buildSteps(session, visibleExercises)
            .asSequence()
            .filter { it.type == WorkoutStepType.WORKING_SET && it.setIndex != null }
            .filter { emitted.add(it.exerciseId to it.setIndex!!) }
            .toList()
    }

    private fun appendSupersetSteps(
        session: Session,
        visibleExercises: List<Exercise>,
        groupId: String,
        steps: MutableList<WorkoutStep>,
    ) {
        val visibleIds = visibleExercises.map { it.id }.toSet()
        val members = SupersetRules.orderedMembers(session, groupId)
            .filter { it.id in visibleIds }
        if (members.isEmpty()) return

        val combinedMobility = members.flatMap { it.mobilitySeries }
        if (combinedMobility.isNotEmpty()) {
            steps += WorkoutStep(
                type = WorkoutStepType.MOBILITY,
                exerciseId = members.first().id,
                exerciseName = "Movilidad de superserie",
                supersetGroupId = groupId,
                mobilitySeries = combinedMobility,
                restAfterKind = RestTimerKind.STANDARD,
            )
        }

        members.forEach { exercise ->
            appendWarmupSteps(exercise, groupId, steps)
        }

        val rounds = SupersetRules.roundCount(session, groupId)
        repeat(rounds) { roundIdx ->
            members.forEachIndexed { memberIdx, exercise ->
                if (roundIdx !in exercise.sets.indices) return@forEachIndexed
                val isLastMemberWithSet = members
                    .drop(memberIdx + 1)
                    .none { roundIdx in it.sets.indices }
                appendWorkingSetSteps(
                    exercise = exercise,
                    setIndex = roundIdx,
                    groupId = groupId,
                    roundIndex = roundIdx,
                    restAfterKind = if (isLastMemberWithSet) RestTimerKind.SUPERSET_ROUND else RestTimerKind.SUPERSET_INTRA,
                    steps = steps,
                )
            }
        }
    }

    private fun appendExerciseSteps(
        exercise: Exercise,
        groupId: String?,
        steps: MutableList<WorkoutStep>,
    ) {
        appendPreparationSteps(exercise, groupId, steps)
        exercise.sets.indices.forEach { setIndex ->
            appendWorkingSetSteps(
                exercise = exercise,
                setIndex = setIndex,
                groupId = groupId,
                roundIndex = null,
                restAfterKind = RestTimerKind.STANDARD,
                steps = steps,
            )
        }
    }

    private fun appendPreparationSteps(
        exercise: Exercise,
        groupId: String?,
        steps: MutableList<WorkoutStep>,
    ) {
        if (exercise.mobilitySeries.isNotEmpty()) {
            steps += WorkoutStep(
                type = WorkoutStepType.MOBILITY,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                supersetGroupId = groupId,
                mobilitySeries = exercise.mobilitySeries,
                restAfterKind = RestTimerKind.STANDARD,
            )
        }
        exercise.warmupSets.forEachIndexed { index, _ ->
            steps += warmupStep(exercise, groupId, index)
        }
    }

    private fun appendWarmupSteps(
        exercise: Exercise,
        groupId: String?,
        steps: MutableList<WorkoutStep>,
    ) {
        exercise.warmupSets.forEachIndexed { index, _ ->
            steps += warmupStep(exercise, groupId, index)
        }
    }

    private fun warmupStep(
        exercise: Exercise,
        groupId: String?,
        index: Int,
    ): WorkoutStep {
        return WorkoutStep(
            type = WorkoutStepType.WARMUP,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            setIndex = index,
            supersetGroupId = groupId,
            restAfterKind = RestTimerKind.WARMUP,
        )
    }

    private fun appendWorkingSetSteps(
        exercise: Exercise,
        setIndex: Int,
        groupId: String?,
        roundIndex: Int?,
        restAfterKind: RestTimerKind,
        steps: MutableList<WorkoutStep>,
    ) {
        if (!exercise.isEffectivelyUnilateral()) {
            steps += WorkoutStep(
                type = WorkoutStepType.WORKING_SET,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setIndex = setIndex,
                supersetGroupId = groupId,
                supersetRoundIndex = roundIndex,
                restAfterKind = restAfterKind,
            )
            return
        }

        val set = exercise.sets.getOrNull(setIndex)
        val hasLeftOnly = set?.leftTarget != null && set.rightTarget == null
        val hasRightOnly = set?.rightTarget != null && set.leftTarget == null
        val sides = when {
            hasLeftOnly -> listOf("left")
            hasRightOnly -> listOf("right")
            exercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT -> listOf("left", "right")
            else -> listOf("right", "left")
        }

        sides.forEachIndexed { sideIdx, side ->
            steps += WorkoutStep(
                type = WorkoutStepType.WORKING_SET,
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                setIndex = setIndex,
                side = side,
                supersetGroupId = groupId,
                supersetRoundIndex = roundIndex,
                restAfterKind = if (sideIdx == 0 && (exercise.restBetweenSidesSeconds ?: 0) > 0) {
                    RestTimerKind.BETWEEN_SIDES
                } else {
                    restAfterKind
                },
            )
        }
    }
}
