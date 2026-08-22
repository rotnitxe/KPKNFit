package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.SupersetRules

/** Atomic result of an editor exercise drag. */
data class SessionEditorMoveRequest(
    val sourcePartId: String?,
    val exerciseId: String,
    val targetPartId: String?,
    val targetIndex: Int?,
    val moveAsGroup: Boolean = true,
    val targetGroupId: String? = null,
)

/**
 * Pure structural move used by both drag/drop and deterministic tests.
 * Removal, insertion, superset membership and normalization happen as one
 * transaction, so a failed cross-zone move cannot leave half a group behind.
 */
object SessionEditorMoveEngine {
    fun move(session: Session, request: SessionEditorMoveRequest): Session {
        val normalized = SupersetRules.normalizeSession(session)
        val sourceExercises = exercisesFor(normalized, request.sourcePartId)
        val sourceIndex = sourceExercises.indexOfFirst { it.id == request.exerciseId }
        if (sourceIndex < 0) return session

        val dragged = sourceExercises[sourceIndex]
        val sourcePart = request.sourcePartId?.let { id -> normalized.parts.firstOrNull { it.id == id } }
        val targetPart = request.targetPartId?.let { id -> normalized.parts.firstOrNull { it.id == id } }
        val isCardio = dragged.isCardio || sourcePart?.isCardioPart() == true
        val targetIsCardio = targetPart?.isCardioPart() == true
        if (isCardio != targetIsCardio) return session

        val sourceGroupId = dragged.supersetGroupRefOrLegacyId()
        val movingIds = if (request.moveAsGroup && sourceGroupId != null) {
            val group = normalized.allSupersetGroups().firstOrNull { it.id == sourceGroupId }
            val ordered = group?.exerciseOrder.orEmpty().filter { id -> sourceExercises.any { it.id == id } }
            (ordered + sourceExercises.filter { it.supersetGroupRefOrLegacyId() == sourceGroupId }.map { it.id })
                .distinct()
        } else {
            listOf(request.exerciseId)
        }
        val moving = movingIds.mapNotNull { id -> sourceExercises.firstOrNull { it.id == id } }
        if (moving.isEmpty()) return session

        val targetGroup = request.targetGroupId?.let { id -> normalized.allSupersetGroups().firstOrNull { it.id == id } }
        if (request.targetGroupId != null && targetGroup == null) return session
        // Use normalized visual order, not only the persisted order list. Legacy
        // sessions can have valid group references that were not serialized into
        // exerciseOrder yet.
        val targetExistingIds = targetGroup
            ?.let { group -> SupersetRules.orderedMembers(normalized, group.id).map { it.id } }
            .orEmpty()
            .filter { id -> exercisesFor(normalized, request.targetPartId).any { it.id == id } && id !in movingIds }
        if (request.targetGroupId != null && targetExistingIds.size + moving.size > MAX_SUPERSET_MEMBERS) return session

        val movedExercises = if (request.moveAsGroup) {
            moving
        } else {
            moving.map { exercise ->
                exercise.copy(
                    supersetGroupRef = null,
                    supersetId = null,
                    supersetRestBetween = null,
                    supersetRestAfter = null,
                )
            }
        }

        val stripped = replaceList(normalized, request.sourcePartId) { list ->
            list.filterNot { it.id in movingIds }
        }
        val targetAfterRemoval = exercisesFor(stripped, request.targetPartId)
        val requestedIndex = request.targetIndex ?: targetAfterRemoval.size
        val adjustedIndex = if (request.sourcePartId == request.targetPartId) {
            // targetIndex is projected against the pre-removal list. Correct it
            // by the moving IDs that precede that insertion point; subtracting
            // the whole group size breaks non-contiguous supersets.
            val prefixEnd = requestedIndex.coerceIn(0, sourceExercises.size)
            requestedIndex - sourceExercises.take(prefixEnd).count { it.id in movingIds }
        } else requestedIndex
        val inserted = targetAfterRemoval.toMutableList().apply {
            addAll(adjustedIndex.coerceIn(0, size), movedExercises)
        }
        var result = replaceList(stripped, request.targetPartId) { inserted }

        if (!request.moveAsGroup && request.targetGroupId != null) {
            val groupIds = (targetExistingIds + movingIds).distinct()
            if (groupIds.size < 2) return session
            val group = targetGroup ?: return session
            // Keep the projected list position. createSuperset() is insertion
            // oriented and would move the target block back to its old anchor,
            // making a drop between members jump after release.
            val updatedGroup = group.copy(
                exerciseOrder = orderGroupMembers(
                    targetExistingIds = targetExistingIds,
                    movingIds = movingIds,
                    targetAfterRemoval = targetAfterRemoval,
                    insertionIndex = adjustedIndex,
                ),
            )
            result = result.copy(
                exercises = result.exercises.map { it.withSupersetMembership(updatedGroup) },
                parts = result.parts.map { part ->
                    part.copy(exercises = part.exercises.map { it.withSupersetMembership(updatedGroup) })
                },
                supersetGroups = result.supersetGroups.map { existing ->
                    if (existing.id == updatedGroup.id) updatedGroup else existing
                },
            )
        }

        return SupersetRules.normalizeSession(result)
    }

    private fun exercisesFor(session: Session, partId: String?): List<Exercise> =
        if (partId == null) session.exercises else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()

    private fun replaceList(session: Session, partId: String?, transform: (List<Exercise>) -> List<Exercise>): Session =
        if (partId == null) {
            session.copy(exercises = transform(session.exercises))
        } else {
            session.copy(parts = session.parts.map { part ->
                if (part.id == partId) part.copy(exercises = transform(part.exercises)) else part
            })
        }

    private fun orderGroupMembers(
        targetExistingIds: List<String>,
        movingIds: List<String>,
        targetAfterRemoval: List<Exercise>,
        insertionIndex: Int,
    ): List<String> {
        val nextExistingId = targetAfterRemoval
            .drop(insertionIndex.coerceIn(0, targetAfterRemoval.size))
            .firstOrNull { it.id in targetExistingIds }
            ?.id
        if (nextExistingId == null) return (targetExistingIds + movingIds).distinct()

        val nextIndex = targetExistingIds.indexOf(nextExistingId).coerceAtLeast(0)
        return targetExistingIds.toMutableList().apply {
            addAll(nextIndex, movingIds.filterNot { it in this })
        }
    }

    private fun Exercise.withSupersetMembership(group: SupersetGroup): Exercise =
        if (id in group.exerciseOrder) copy(
            supersetGroupRef = group.id,
            supersetId = group.id,
            supersetRestBetween = group.restBetweenExercises,
            supersetRestAfter = group.restAfterSuperset,
        ) else this

    private const val MAX_SUPERSET_MEMBERS = 4
}
