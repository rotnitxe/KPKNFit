package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules

enum class WorkoutStepType {
    CARDIO,
    MOBILITY,
    MOBILITY_GROUP,
    WARMUP,
    WORKING_SET,
}

data class WorkoutStep(
    val type: WorkoutStepType,
    val exerciseId: String,
    val exerciseName: String,
    val stepKey: String = "",
    val setIndex: Int? = null,
    val warmupSetId: String? = null,
    val mobilitySeriesId: String? = null,
    /** Zero-based repetition inside a configured mobility series. */
    val mobilitySetIndex: Int = 0,
    val side: String? = null,
    val supersetGroupId: String? = null,
    val supersetRoundIndex: Int? = null,
    val mobilitySeries: List<MobilitySeries> = emptyList(),
    val isEmptySlot: Boolean = false,
    val restAfterKind: RestTimerKind = RestTimerKind.STANDARD,
)

object WorkoutStepRules {
    fun buildSteps(session: Session, visibleExercises: List<Exercise> = session.allExercises()): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        val emittedSupersets = mutableSetOf<String>()
        val globalMobility = session.globalMobilityExercises()
        val globalIds = globalMobility.map { it.id }.toSet()

        globalMobility.forEach { exercise ->
            appendMobilityGroupSteps(exercise, steps)
        }

        visibleExercises.filterNot { it.id in globalIds }.forEach { exercise ->
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

    fun warmupStepKey(exerciseId: String, warmupSetId: String): String =
        "${exerciseId}_warmup_$warmupSetId"

    fun mobilityStepKey(
        exerciseId: String,
        mobilitySeriesId: String,
        mobilitySetIndex: Int = 0,
    ): String = buildString {
        append(exerciseId)
        append("_")
        append(mobilitySeriesId)
        if (mobilitySetIndex > 0) {
            append("_set_")
            append(mobilitySetIndex)
        }
    }

    fun cardioStepKey(exerciseId: String): String = "${exerciseId}_cardio"

    fun workingStepKey(exerciseId: String, setIndex: Int, side: String? = null): String =
        buildString {
            append(exerciseId)
            append("_")
            append(setIndex)
            side?.let {
                append("_")
                append(it.take(1).uppercase())
            }
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

        members.forEach { exercise ->
            if (exercise.isCardio) {
                appendCardioStep(exercise, groupId, steps)
            } else {
                exercise.mobilitySeries.forEach { mobility ->
                    repeat(mobility.sets.coerceAtLeast(1)) { mobilitySetIndex ->
                        steps += WorkoutStep(
                            type = WorkoutStepType.MOBILITY,
                            exerciseId = exercise.id,
                            exerciseName = "Movilidad de superserie",
                            stepKey = mobilityStepKey(exercise.id, mobility.id, mobilitySetIndex),
                            mobilitySeriesId = mobility.id,
                            mobilitySetIndex = mobilitySetIndex,
                            supersetGroupId = groupId,
                            mobilitySeries = listOf(mobility),
                            restAfterKind = RestTimerKind.STANDARD,
                        )
                    }
                }
            }
        }

        members.forEach { exercise ->
            appendWarmupSteps(exercise, groupId, steps)
        }

        val rounds = SupersetRules.roundCount(session, groupId)
        repeat(rounds) { roundIdx ->
            members.forEachIndexed { memberIdx, exercise ->
                if (exercise.isCardio) return@forEachIndexed
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

    @Suppress("KotlinConstantConditions")
    private fun appendExerciseSteps(
        exercise: Exercise,
        groupId: String?,
        steps: MutableList<WorkoutStep>,
    ) {
        if (exercise.isCardio) {
            appendCardioStep(exercise, groupId, steps)
            return
        }
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

    private fun appendMobilityGroupSteps(
        exercise: Exercise,
        steps: MutableList<WorkoutStep>,
    ) {
        exercise.mobilitySeries.forEach { mobility ->
            repeat(mobility.sets.coerceAtLeast(1)) { mobilitySetIndex ->
                steps += WorkoutStep(
                    type = WorkoutStepType.MOBILITY_GROUP,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    stepKey = mobilityStepKey(exercise.id, mobility.id, mobilitySetIndex),
                    mobilitySeriesId = mobility.id,
                    mobilitySetIndex = mobilitySetIndex,
                    mobilitySeries = listOf(mobility),
                    restAfterKind = RestTimerKind.STANDARD,
                )
            }
        }
    }

    private fun appendCardioStep(
        exercise: Exercise,
        groupId: String?,
        steps: MutableList<WorkoutStep>,
    ) {
        steps += WorkoutStep(
            type = WorkoutStepType.CARDIO,
            exerciseId = exercise.id,
            exerciseName = spokenWorkoutExerciseName(exercise),
            stepKey = cardioStepKey(exercise.id),
            setIndex = 0,
            supersetGroupId = groupId,
            restAfterKind = RestTimerKind.STANDARD,
        )
    }

    private fun appendPreparationSteps(
        exercise: Exercise,
        groupId: String?,
        steps: MutableList<WorkoutStep>,
    ) {
        exercise.mobilitySeries.forEach { mobility ->
            repeat(mobility.sets.coerceAtLeast(1)) { mobilitySetIndex ->
                steps += WorkoutStep(
                    type = WorkoutStepType.MOBILITY,
                    exerciseId = exercise.id,
                    exerciseName = spokenWorkoutExerciseName(exercise),
                    stepKey = mobilityStepKey(exercise.id, mobility.id, mobilitySetIndex),
                    mobilitySeriesId = mobility.id,
                    mobilitySetIndex = mobilitySetIndex,
                    supersetGroupId = groupId,
                    mobilitySeries = listOf(mobility),
                    restAfterKind = RestTimerKind.STANDARD,
                )
            }
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
        val warmupSet = exercise.warmupSets.getOrNull(index)
        return WorkoutStep(
            type = WorkoutStepType.WARMUP,
            exerciseId = exercise.id,
            exerciseName = spokenWorkoutExerciseName(exercise),
            stepKey = warmupSet?.let { warmupStepKey(exercise.id, it.id) }.orEmpty(),
            setIndex = index,
            warmupSetId = warmupSet?.id,
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
                exerciseName = spokenWorkoutExerciseName(exercise),
                stepKey = workingStepKey(exercise.id, setIndex),
                setIndex = setIndex,
                supersetGroupId = groupId,
                supersetRoundIndex = roundIndex,
                restAfterKind = restAfterKind,
            )
            return
        }

        val set = exercise.sets.getOrNull(setIndex)
        if (set?.isEmptySlot == true) {
            steps += WorkoutStep(
                type = WorkoutStepType.WORKING_SET,
                exerciseId = exercise.id,
                exerciseName = spokenWorkoutExerciseName(exercise),
                stepKey = workingStepKey(exercise.id, setIndex),
                setIndex = setIndex,
                supersetGroupId = groupId,
                supersetRoundIndex = roundIndex,
                isEmptySlot = true,
                restAfterKind = restAfterKind,
            )
            return
        }
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
                exerciseName = spokenWorkoutExerciseName(exercise),
                stepKey = workingStepKey(exercise.id, setIndex, side),
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
