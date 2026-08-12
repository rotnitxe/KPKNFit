package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.SupersetVisualPlacement
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

object SupersetRules {
    private const val MaxSupersetMembers = 4

    fun normalizeSession(session: Session): Session {
        val allExercises = session.allExercises()
        if (allExercises.isEmpty()) return session

        val existingGroups = session.supersetGroups.associateBy { it.id }
        val groupIds = allExercises
            .mapNotNull { it.supersetGroupRefOrLegacyId() }
            .distinct()

        if (groupIds.isEmpty()) {
            return if (session.supersetGroups.isEmpty()) session else session.copy(supersetGroups = emptyList())
        }

        val normalizedGroups = groupIds.mapNotNull { groupId ->
            val members = allExercises.filter { it.supersetGroupRefOrLegacyId() == groupId }
            if (members.size < 2) return@mapNotNull null
            val existing = existingGroups[groupId]
            val orderedFromExisting = existing?.exerciseOrder.orEmpty().filter { id -> members.any { it.id == id } }
            val orderedIds = (orderedFromExisting + members.map { it.id }).distinct().take(MaxSupersetMembers)
            if (orderedIds.size < 2) return@mapNotNull null
            SupersetGroup(
                id = groupId,
                exerciseOrder = orderedIds,
                restBetweenExercises = existing?.restBetweenExercises
                    ?: members.firstOrNull()?.supersetRestBetween
                    ?: 60,
                restAfterSuperset = existing?.restAfterSuperset
                    ?: members.firstOrNull()?.supersetRestAfter
                    ?: 120,
                rounds = existing?.rounds,
                visualPlacement = existing?.visualPlacement,
                roundRestBetweenExercises = existing?.roundRestBetweenExercises.orEmpty(),
                roundRestAfterSuperset = existing?.roundRestAfterSuperset.orEmpty(),
                isOptional = existing?.isOptional ?: false,
            )
        }
        val validGroupIds = normalizedGroups.map { it.id }.toSet()
        val groupById = normalizedGroups.associateBy { it.id }

        fun updateExercise(exercise: Exercise): Exercise {
            val groupId = exercise.supersetGroupRefOrLegacyId()
            val group = groupId
                ?.let(groupById::get)
                ?.takeIf { exercise.id in it.exerciseOrder }
            return if (group == null) {
                exercise.copy(
                    supersetGroupRef = null,
                    supersetId = null,
                    supersetRestBetween = null,
                    supersetRestAfter = null,
                )
            } else {
                exercise.copy(
                    supersetGroupRef = group.id,
                    supersetId = group.id,
                    supersetRestBetween = group.restBetweenExercises,
                    supersetRestAfter = group.restAfterSuperset,
                )
            }
        }

        return session.copy(
            exercises = session.exercises.map(::updateExercise),
            parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::updateExercise)) },
            supersetGroups = normalizedGroups.filter { it.id in validGroupIds },
        )
    }

    fun createSuperset(
        session: Session,
        groupId: String,
        exerciseIds: List<String>,
        restBetweenExercises: Int,
        restAfterSuperset: Int,
        rounds: Int? = null,
        anchorPartId: String? = null,
        anchorExerciseId: String? = exerciseIds.firstOrNull(),
    ): Session {
        val targetIds = exerciseIds.distinct().take(MaxSupersetMembers)
        if (targetIds.size < 2) return session
        val allIds = session.allExercises().map { it.id }.toSet()
        if (!targetIds.all { it in allIds }) return session

        val maxSetsForTargets = targetIds.mapNotNull { tid -> session.allExercises().find { it.id == tid }?.sets?.size }.maxOrNull() ?: 1
        val clampedRounds = rounds?.coerceAtLeast(1)?.let { maxOf(it, maxSetsForTargets) }
        val group = SupersetGroup(
            id = groupId,
            exerciseOrder = targetIds,
            restBetweenExercises = restBetweenExercises.coerceAtLeast(0),
            restAfterSuperset = restAfterSuperset.coerceAtLeast(0),
            rounds = clampedRounds,
            visualPlacement = SupersetVisualPlacement(
                partId = anchorPartId,
                anchorExerciseId = anchorExerciseId,
            ),
            roundRestBetweenExercises = clampedRounds?.let { count ->
                (0 until count.coerceAtLeast(1)).associateWith { restBetweenExercises.coerceAtLeast(0) }
            }.orEmpty(),
            roundRestAfterSuperset = clampedRounds?.let { count ->
                (0 until count.coerceAtLeast(1)).associateWith { restAfterSuperset.coerceAtLeast(0) }
            }.orEmpty(),
        )

        val previousGroupIds = session.allExercises()
            .filter { it.id in targetIds }
            .mapNotNull { it.supersetGroupRefOrLegacyId() }
            .toSet() - groupId

        // First, extract all exercises to find where they currently are and update them
        val allCurrentExercises = session.allExercises()
        val updatedMembers = targetIds.mapNotNull { id ->
            allCurrentExercises.find { it.id == id }?.copy(
                supersetGroupRef = groupId,
                supersetId = groupId,
                supersetRestBetween = group.restBetweenExercises,
                supersetRestAfter = group.restAfterSuperset,
            )
        }

        // Strip them from everywhere
        var resultSession = session.copy(
            exercises = session.exercises.filterNot { it.id in targetIds },
            parts = session.parts.map { it.copy(exercises = it.exercises.filterNot { it.id in targetIds }) }
        )

        // Find the absolute best insertion point. 
        // If anchorPartId is specified, we definitely move to that part.
        // If not, we check where the anchorExerciseId *was*.
        val requestedAnchorPartId = anchorPartId?.takeIf { requestedId ->
            session.parts.any { it.id == requestedId }
        }
        val resolvedAnchorPartId = requestedAnchorPartId
            ?: session.parts.find { part -> part.exercises.any { it.id == anchorExerciseId } }?.id

        if (resolvedAnchorPartId == null) {
            // Re-insert into loose exercises
            val mutableLoose = resultSession.exercises.toMutableList()
            // Find where the anchor was in the original loose list, or just append
            val originalAnchorIdx = session.exercises.indexOfFirst { it.id == anchorExerciseId }
            val safeIdx = if (originalAnchorIdx >= 0) {
                // Adjust index based on how many target items were BEFORE it and are now removed
                val removedBefore = session.exercises.take(originalAnchorIdx).count { it.id in targetIds }
                (originalAnchorIdx - removedBefore).coerceIn(0, mutableLoose.size)
            } else {
                mutableLoose.size
            }
            mutableLoose.addAll(safeIdx, updatedMembers)
            resultSession = resultSession.copy(exercises = mutableLoose)
        } else {
            // Re-insert into the specific part
            resultSession = resultSession.copy(
                parts = resultSession.parts.map { part ->
                    if (part.id != resolvedAnchorPartId) part
                    else {
                        val mutableP = part.exercises.toMutableList()
                        val originalAnchorIdx = session.parts.find { it.id == resolvedAnchorPartId }?.exercises?.indexOfFirst { it.id == anchorExerciseId } ?: -1
                        val safeIdx = if (originalAnchorIdx >= 0) {
                            val removedBefore = session.parts.find { it.id == resolvedAnchorPartId }!!.exercises.take(originalAnchorIdx).count { it.id in targetIds }
                            (originalAnchorIdx - removedBefore).coerceIn(0, mutableP.size)
                        } else {
                            mutableP.size
                        }
                        mutableP.addAll(safeIdx, updatedMembers)
                        part.copy(exercises = mutableP)
                    }
                }
            )
        }

        return normalizeSession(resultSession.copy(
            supersetGroups = resultSession.supersetGroups
                .filterNot { it.id == groupId }
                .map { existing ->
                    if (existing.id in previousGroupIds) {
                        val nextOrder = existing.exerciseOrder.filterNot { it in targetIds }
                        existing.copy(exerciseOrder = nextOrder)
                    } else {
                        existing
                    }
                }
                .filter { it.id !in previousGroupIds || it.exerciseOrder.size >= 2 }
                .plus(group),
        ))
    }

    fun orderedMembers(session: Session, groupId: String): List<Exercise> {
        val group = session.allSupersetGroups().firstOrNull { it.id == groupId }
        val members = session.allExercises().filter { it.supersetGroupRefOrLegacyId() == groupId }
        if (members.isEmpty()) return emptyList()
        val byId = members.associateBy { it.id }
        val ordered = group?.exerciseOrder.orEmpty().mapNotNull(byId::get)
        val orderedIds = ordered.map { it.id }.toSet()
        return ordered + members.filterNot { it.id in orderedIds }
    }

    fun roundCount(session: Session, groupId: String): Int {
        val group = session.allSupersetGroups().firstOrNull { it.id == groupId }
        val plannedRounds = group?.rounds?.takeIf { it > 0 }
        if (plannedRounds != null) return plannedRounds
        return orderedMembers(session, groupId).maxOfOrNull { it.sets.size } ?: 0
    }

    fun nextTarget(
        session: Session,
        visibleExercises: List<Exercise>,
        currentExerciseIdx: Int,
        currentSetIdx: Int,
    ): Pair<Int, Int>? {
        val current = visibleExercises.getOrNull(currentExerciseIdx) ?: return null
        val groupId = current.supersetGroupRefOrLegacyId() ?: return null
        val orderedIds = orderedMembers(session, groupId).map { it.id }
        if (orderedIds.size < 2) return null

        val currentOrderIdx = orderedIds.indexOf(current.id)
        if (currentOrderIdx < 0) return null

        fun visibleIndexFor(exerciseId: String): Int =
            visibleExercises.indexOfFirst { it.id == exerciseId }

        for (orderIdx in (currentOrderIdx + 1) until orderedIds.size) {
            val exerciseId = orderedIds[orderIdx]
            val visibleIdx = visibleIndexFor(exerciseId)
            val exercise = visibleExercises.getOrNull(visibleIdx)
            if (exercise != null && currentSetIdx in exercise.sets.indices) {
                return visibleIdx to currentSetIdx
            }
        }

        val nextRound = currentSetIdx + 1
        for (exerciseId in orderedIds) {
            val visibleIdx = visibleIndexFor(exerciseId)
            val exercise = visibleExercises.getOrNull(visibleIdx)
            if (exercise != null && nextRound in exercise.sets.indices) {
                return visibleIdx to nextRound
            }
        }

        return null
    }

    fun updateRest(
        session: Session,
        groupId: String,
        restBetweenExercises: Int? = null,
        restAfterSuperset: Int? = null,
        rounds: Int? = null,
    ): Session {
        val existing = session.allSupersetGroups().firstOrNull { it.id == groupId } ?: return session
        val nextRestBetween = restBetweenExercises?.coerceAtLeast(0) ?: existing.restBetweenExercises
        val nextRestAfter = restAfterSuperset?.coerceAtLeast(0) ?: existing.restAfterSuperset
        val rawNextRounds = rounds?.coerceAtLeast(1) ?: existing.rounds
        // C2: rounds nunca < max(sets) para evitar sets inalcanzables y progreso <100%
        val maxSets = session.allExercises().filter { it.supersetGroupRefOrLegacyId() == groupId }.maxOfOrNull { it.sets.size } ?: 1
        val nextRounds = rawNextRounds?.let { maxOf(it, maxSets) }
        val roundCount = nextRounds ?: roundCount(session, groupId)
        val nextRoundBetween = (0 until roundCount).associateWith { round ->
            existing.roundRestBetweenExercises[round] ?: nextRestBetween
        }
        val nextRoundAfter = (0 until roundCount).associateWith { round ->
            existing.roundRestAfterSuperset[round] ?: nextRestAfter
        }

        fun updateExercise(exercise: Exercise): Exercise {
            if (exercise.supersetGroupRefOrLegacyId() != groupId) return exercise
            return exercise.copy(
                supersetRestBetween = nextRestBetween,
                supersetRestAfter = nextRestAfter,
            )
        }

        return normalizeSession(session.copy(
            exercises = session.exercises.map(::updateExercise),
            parts = session.parts.map { it.copy(exercises = it.exercises.map(::updateExercise)) },
            supersetGroups = session.supersetGroups.map {
                if (it.id == groupId) {
                    it.copy(
                        restBetweenExercises = nextRestBetween,
                        restAfterSuperset = nextRestAfter,
                        rounds = nextRounds,
                        roundRestBetweenExercises = nextRoundBetween,
                        roundRestAfterSuperset = nextRoundAfter,
                    )
                } else {
                    it
                }
            },
        ))
    }

    fun updateRoundRest(
        session: Session,
        groupId: String,
        roundIndex: Int,
        restBetweenExercises: Int? = null,
        restAfterSuperset: Int? = null,
    ): Session {
        val safeRound = roundIndex.coerceAtLeast(0)
        return normalizeSession(session.copy(
            supersetGroups = session.supersetGroups.map { group ->
                if (group.id != groupId) group else group.copy(
                    roundRestBetweenExercises = restBetweenExercises?.let {
                        group.roundRestBetweenExercises + (safeRound to it.coerceAtLeast(0))
                    } ?: group.roundRestBetweenExercises,
                    roundRestAfterSuperset = restAfterSuperset?.let {
                        group.roundRestAfterSuperset + (safeRound to it.coerceAtLeast(0))
                    } ?: group.roundRestAfterSuperset,
                )
            },
        ))
    }

    fun removeExercise(session: Session, groupId: String, exerciseId: String): Session {
        val group = session.allSupersetGroups().firstOrNull { it.id == groupId } ?: return session
        val memberIds = orderedMembers(session, groupId).map { it.id }
            .ifEmpty { group.exerciseOrder }
        val remainingIds = memberIds.filterNot { it == exerciseId }
        val idsToClear = if (remainingIds.size <= 1) memberIds.toSet() else setOf(exerciseId)

        fun updateExercise(exercise: Exercise): Exercise {
            if (exercise.id !in idsToClear) return exercise
            return exercise.copy(
                supersetGroupRef = null,
                supersetId = null,
                supersetRestBetween = null,
                supersetRestAfter = null,
            )
        }

        val updatedGroups = if (remainingIds.size <= 1) {
            session.supersetGroups.filterNot { it.id == groupId }
        } else {
            session.supersetGroups.map { if (it.id == groupId) it.copy(exerciseOrder = remainingIds) else it }
        }

        return normalizeSession(session.copy(
            exercises = session.exercises.map(::updateExercise),
            parts = session.parts.map { it.copy(exercises = it.exercises.map(::updateExercise)) },
            supersetGroups = updatedGroups,
        ))
    }

    /**
     * Deletes a member from the session instead of merely taking it out of the
     * superset. The group is kept when at least two members remain and is
     * dissolved automatically when the deletion leaves fewer than two.
     */
    fun deleteExercise(session: Session, groupId: String, exerciseId: String): Session {
        val group = session.allSupersetGroups().firstOrNull { it.id == groupId } ?: return session
        val memberIds = orderedMembers(session, groupId).map { it.id }
            .ifEmpty { group.exerciseOrder }
        if (exerciseId !in memberIds) return session

        val remainingIds = memberIds.filterNot { it == exerciseId }
        val updatedGroups = if (remainingIds.size < 2) {
            session.supersetGroups.filterNot { it.id == groupId }
        } else {
            session.supersetGroups.map { current ->
                if (current.id == groupId) current.copy(exerciseOrder = remainingIds) else current
            }
        }

        return normalizeSession(
            session.copy(
                exercises = session.exercises.filterNot { it.id == exerciseId },
                parts = session.parts.map { part ->
                    part.copy(exercises = part.exercises.filterNot { it.id == exerciseId })
                },
                supersetGroups = updatedGroups,
            ),
        )
    }

    /** Deletes the superset and every exercise that belongs to it. */
    fun deleteGroup(session: Session, groupId: String): Session {
        val group = session.allSupersetGroups().firstOrNull { it.id == groupId } ?: return session
        val memberIds = orderedMembers(session, groupId).map { it.id }
            .ifEmpty { group.exerciseOrder }
            .toSet()

        return normalizeSession(
            session.copy(
                exercises = session.exercises.filterNot { it.id in memberIds },
                parts = session.parts.map { part ->
                    part.copy(exercises = part.exercises.filterNot { it.id in memberIds })
                },
                supersetGroups = session.supersetGroups.filterNot { it.id == groupId },
            ),
        )
    }

    fun dissolve(session: Session, groupId: String): Session {
        val group = session.allSupersetGroups().firstOrNull { it.id == groupId }
        val copiedRoundRest = group?.restAfterSuperset?.takeIf { it > 0 }
        fun updateExercise(exercise: Exercise): Exercise {
            if (exercise.supersetGroupRefOrLegacyId() != groupId) return exercise
            return exercise.copy(
                supersetGroupRef = null,
                supersetId = null,
                supersetRestBetween = null,
                supersetRestAfter = null,
                // Dissolving a superset turns the round-level rest into the
                // individual rest for every member. Keep the old exercise
                // value only when the group has no usable round rest.
                restTime = copiedRoundRest ?: exercise.restTime,
            )
        }
        return session.copy(
            exercises = session.exercises.map(::updateExercise),
            parts = session.parts.map { it.copy(exercises = it.exercises.map(::updateExercise)) },
            supersetGroups = session.supersetGroups.filterNot { it.id == groupId },
        )
    }

    fun moveGroup(
        session: Session,
        groupId: String,
        targetPartId: String?,
        targetIndex: Int?,
    ): Session {
        val orderedMembers = orderedMembers(session, groupId)
        if (orderedMembers.size < 2) return session
        val movingIds = orderedMembers.map { it.id }.toSet()

        fun withoutGroup(exercises: List<Exercise>) = exercises.filterNot { it.id in movingIds }

        val sessionWithout = session.copy(
            exercises = withoutGroup(session.exercises),
            parts = session.parts.map { part -> part.copy(exercises = withoutGroup(part.exercises)) },
        )

        fun insertInto(exercises: List<Exercise>): List<Exercise> {
            val index = (targetIndex ?: exercises.size).coerceIn(0, exercises.size)
            return exercises.toMutableList().also { list -> list.addAll(index, orderedMembers) }
        }

        return if (targetPartId == null) {
            sessionWithout.copy(exercises = insertInto(sessionWithout.exercises))
        } else {
            sessionWithout.copy(
                parts = sessionWithout.parts.map { part ->
                    if (part.id == targetPartId) part.copy(exercises = insertInto(part.exercises)) else part
                },
            )
        }
    }

    fun exercisesInContainer(part: SessionPart?, session: Session): List<Exercise> =
        part?.exercises ?: session.exercises
}
