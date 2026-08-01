package com.example.kpkn.screens.sessioneditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/**
 * Unified drag-and-drop state for reordering parts and exercises in the session editor.
 * Uses a single Y-sorted hit-testing algorithm for consistent drop behavior.
 */
class SessionEditorDragController {

    val partBounds = mutableStateMapOf<String, Rect>()
    val partContentBounds = mutableStateMapOf<String, Rect>()
    val exerciseBounds = mutableStateMapOf<String, Rect>()

    var looseContentBounds by mutableStateOf<Rect?>(null)

    var draggingPartId by mutableStateOf<String?>(null)
    var draggingPartOffsetY by mutableStateOf(0f)
    var partDropTargetId by mutableStateOf<String?>(null)
    var partDropTargetIndex by mutableStateOf<Int?>(null)
    var dragStartPartRect by mutableStateOf<Rect?>(null)

    var draggingExerciseId by mutableStateOf<String?>(null)
    var draggingExercisePartId by mutableStateOf<String?>(null)
    var draggingExerciseOffset by mutableStateOf(Offset.Zero)
    var exerciseDropTargetKey by mutableStateOf<String?>(null)
    var exerciseDropTargetPartId by mutableStateOf<String?>(null)
    var exerciseDropTargetIndex by mutableStateOf<Int?>(null)
    var dragStartExerciseRect by mutableStateOf<Rect?>(null)

    fun clearBounds() {
        partBounds.clear()
        partContentBounds.clear()
        exerciseBounds.clear()
        looseContentBounds = null
    }

    fun beginExerciseDrag(partId: String, exerciseId: String) {
        draggingExerciseId = exerciseId
        draggingExercisePartId = partId
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        dragStartExerciseRect = exerciseBounds["$partId|$exerciseId"]
    }

    fun updateExerciseDrag(delta: Offset, session: Session) {
        val groupedPartsForDrag = session.parts.filterNot { it.isUncategorizedPart() }
        val activeExerciseId = draggingExerciseId ?: return
        val currentPartId = draggingExercisePartId ?: return
        draggingExerciseOffset += delta
        val startRect = dragStartExerciseRect ?: exerciseBounds["$currentPartId|$activeExerciseId"] ?: return
        val center = Offset(
            startRect.center.x + draggingExerciseOffset.x,
            startRect.center.y + draggingExerciseOffset.y,
        )
        val targetPartId = when {
            looseContentBounds?.contains(center) == true -> LOOSE_PART_ID
            else -> groupedPartsForDrag.firstOrNull { candidate ->
                partContentBounds[candidate.id]?.contains(center) == true
            }?.id
        }
        exerciseDropTargetPartId = targetPartId
        if (targetPartId != null) {
            val sourceList = exerciseListFor(session, targetPartId(currentPartId))
            val draggedGroupId = sourceList.firstOrNull { it.id == activeExerciseId }?.supersetGroupRefOrLegacyId()
            val draggedIds = if (draggedGroupId != null) {
                sourceList.filter { it.supersetGroupRefOrLegacyId() == draggedGroupId }.map { it.id }.toSet()
            } else {
                setOf(activeExerciseId)
            }
            val orderedKeys = exerciseBounds
                .filterKeys { it.startsWith("$targetPartId|") }
                .filterKeys { key -> key.substringAfter("|") !in draggedIds }
                .entries
                .sortedBy { it.value.center.y }
            val before = orderedKeys.firstOrNull { (_, rect) -> center.y < rect.center.y }
            val targetIndex = if (before != null) {
                val targetExerciseId = before.key.substringAfter("|")
                exerciseListFor(session, targetPartId(targetPartId))
                    .indexOfFirst { it.id == targetExerciseId }
                    .takeIf { it >= 0 }
            } else {
                exerciseListFor(session, targetPartId(targetPartId)).size
            }
            // A stale bounds entry must not silently become "insert at 0".
            if (before != null && targetIndex == null) {
                exerciseDropTargetKey = null
                exerciseDropTargetIndex = null
            } else {
                exerciseDropTargetKey = before?.key
                exerciseDropTargetIndex = targetIndex
            }
        } else {
            exerciseDropTargetKey = null
            exerciseDropTargetIndex = null
        }
    }

    fun endExerciseDrag(
        session: Session,
        onMoveExercise: (fromPartId: String?, exerciseId: String, toPartId: String?, toIndex: Int?) -> Unit,
    ) {
        val activeExerciseId = draggingExerciseId
        val currentPartId = draggingExercisePartId
        if (activeExerciseId != null && currentPartId != null) {
            val finalTargetKey = exerciseDropTargetKey
            val finalTargetPart = exerciseDropTargetPartId
            val finalTargetIdx = exerciseDropTargetIndex
            if (finalTargetKey != null) {
                val tPartId = finalTargetKey.substringBefore("|")
                val tExId = finalTargetKey.substringAfter("|")
                val idx = when (tPartId) {
                    LOOSE_PART_ID -> session.exercises.indexOfFirst { it.id == tExId }
                    else -> session.parts.firstOrNull { it.id == tPartId }
                        ?.exercises?.indexOfFirst { it.id == tExId }
                }
                if (idx != null && idx >= 0) {
                    onMoveExercise(
                        targetPartId(currentPartId),
                        activeExerciseId,
                        targetPartId(tPartId),
                        idx,
                    )
                }
            } else if (finalTargetPart != null && finalTargetPart != currentPartId) {
                onMoveExercise(
                    targetPartId(currentPartId),
                    activeExerciseId,
                    targetPartId(finalTargetPart),
                    null,
                )
            } else if (finalTargetIdx != null) {
                onMoveExercise(
                    targetPartId(currentPartId),
                    activeExerciseId,
                    targetPartId(currentPartId),
                    finalTargetIdx,
                )
            }
        }
        resetExerciseDrag()
    }

    fun resetExerciseDrag() {
        draggingExerciseId = null
        draggingExercisePartId = null
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        dragStartExerciseRect = null
    }

    fun beginPartDrag(partId: String) {
        draggingPartId = partId
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        dragStartPartRect = partBounds[partId]
    }

    fun updatePartDrag(deltaY: Float, groupedParts: List<com.example.kpkn.data.models.SessionPart>) {
        val activeId = draggingPartId ?: return
        draggingPartOffsetY += deltaY
        val startRect = dragStartPartRect ?: partBounds[activeId] ?: return
        val pointerY = startRect.center.y + draggingPartOffsetY
        val ordered = groupedParts
            .filter { it.id != activeId }
            .mapNotNull { part -> partBounds[part.id]?.let { part to it } }
            .sortedBy { it.second.center.y }
        val before = ordered.firstOrNull { (_, rect) -> pointerY < rect.center.y }
        if (before != null) {
            partDropTargetId = before.first.id
            partDropTargetIndex = groupedParts.indexOfFirst { it.id == before.first.id }.takeIf { it >= 0 }
        } else {
            partDropTargetId = null
            partDropTargetIndex = groupedParts.size
        }
    }

    fun endPartDrag(
        groupedParts: List<com.example.kpkn.data.models.SessionPart>,
        onMovePart: (partId: String, toIndex: Int) -> Unit,
    ) {
        val activeId = draggingPartId
        val targetIndex = partDropTargetIndex
        if (activeId != null && targetIndex != null) {
            val currentIndex = groupedParts.indexOfFirst { it.id == activeId }
            var adjusted = targetIndex
            if (currentIndex >= 0 && targetIndex > currentIndex) {
                adjusted = (targetIndex - 1).coerceAtLeast(0)
            }
            if (currentIndex != -1 && adjusted != currentIndex) {
                onMovePart(activeId, adjusted.coerceIn(0, (groupedParts.size - 1).coerceAtLeast(0)))
            }
        }
        draggingPartId = null
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        dragStartPartRect = null
    }

    fun projectedShiftFor(partId: String, index: Int, exerciseId: String): Float {
        val activeId = draggingExerciseId ?: return 0f
        val currentPartId = draggingExercisePartId ?: return 0f
        if (exerciseDropTargetPartId != partId || exerciseDropTargetIndex == null) return 0f
        val targetIndex = exerciseDropTargetIndex ?: return 0f
        val activeKey = "$currentPartId|$activeId"
        val thisKey = "$partId|$exerciseId"
        if (activeKey == thisKey) return 0f
        val activeRect = exerciseBounds[activeKey] ?: return 0f
        val thisRect = exerciseBounds[thisKey] ?: return 0f
        return if (index >= targetIndex && thisRect.top >= activeRect.top) {
            activeRect.height
        } else {
            0f
        }
    }

    companion object {
        const val LOOSE_PART_ID = "__loose__"

        private fun targetPartId(partId: String): String? =
            partId.takeUnless { it == LOOSE_PART_ID }

        private fun exerciseListFor(session: Session, partId: String?) =
            if (partId == null) session.exercises
            else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()
    }
}
