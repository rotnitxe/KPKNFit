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
    var dragStartGrabOffset by mutableStateOf(Offset(24f, 24f))

    var isExerciseDragging by mutableStateOf(false)
    var frozenExerciseBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenPartContentBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenLooseContentBounds by mutableStateOf<Rect?>(null)

    // Compensación de scroll durante drag: en window-coords todo se desplaza
    // al scrollear. Los frozen se capturan en window y deben desplazarse
    // inverso al scroll para que el pointer (dedo en window) siga comparándose
    // contra rects en window actuales.
    private var accumulatedScrollPx: Float = 0f

    fun clearBounds() {
        partBounds.clear()
        partContentBounds.clear()
        exerciseBounds.clear()
        looseContentBounds = null
    }

    /** Mantiene solo los bounds de ítems que siguen existiendo (y no están colapsados). */
    fun pruneBounds(session: Session, collapsedPartIds: Set<String>) {
        val activePartIds = session.parts.filterNot { it.isUncategorizedPart() }.map { it.id }.toSet()
        partBounds.keys.retainAll(activePartIds)
        partContentBounds.keys.retainAll(activePartIds)
        val validExerciseKeys = buildSet {
            session.exercises.forEach { add("$LOOSE_PART_ID|${it.id}") }
            session.parts.forEach { part ->
                if (part.id !in collapsedPartIds) {
                    part.exercises.forEach { add("${part.id}|${it.id}") }
                }
            }
        }
        exerciseBounds.keys.retainAll(validExerciseKeys)
        if (session.exercises.isEmpty()) looseContentBounds = null
    }

    fun beginExerciseDrag(partId: String, exerciseId: String, grabOffset: Offset = Offset(24f, 24f)) {
        draggingExerciseId = exerciseId
        draggingExercisePartId = partId
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        dragStartExerciseRect = exerciseBounds["$partId|$exerciseId"]
        dragStartGrabOffset = grabOffset
        isExerciseDragging = true
        accumulatedScrollPx = 0f
        frozenExerciseBounds = exerciseBounds.toMap()
        // Zonas derivadas del frame actual: nunca acumular historial de
        // scrolls/desplazamientos visuales, o la zona "suelta" engulle la pantalla.
        val freshLoose = unionRects(frozenExerciseBounds.filterKeys { it.startsWith("$LOOSE_PART_ID|") }.values)
        frozenLooseContentBounds = freshLoose
        val freshParts = partContentBounds.keys.mapNotNull { pid ->
            val rects = frozenExerciseBounds.filterKeys { it.startsWith("$pid|") }.values.toMutableList()
            partContentBounds[pid]?.let { rects += it }
            pid to (unionRects(rects) ?: return@mapNotNull null)
        }.toMap()
        frozenPartContentBounds = freshParts
        // Sanear las zonas vivas para que el siguiente drag parta limpio.
        looseContentBounds = freshLoose
        partContentBounds.clear()
        partContentBounds.putAll(freshParts)
    }

    /**
     * Desplaza todos los Rect congelados inverso al scroll de la lista.
     * deltaPx > 0  => contenido sube (scroll down), window Y de los Rect baja.
     * Se invoca desde el auto-scroll de SessionEditorScreen.
     */
    fun applyScrollDelta(deltaPx: Float) {
        if (!isExerciseDragging && draggingPartId == null) return
        if (deltaPx == 0f) return
        accumulatedScrollPx += deltaPx
        fun Rect.shifted(): Rect = Rect(left, top - deltaPx, right, bottom - deltaPx)
        frozenExerciseBounds = frozenExerciseBounds.mapValues { (_, r) -> r.shifted() }
        frozenPartContentBounds = frozenPartContentBounds.mapValues { (_, r) -> r.shifted() }
        frozenLooseContentBounds = frozenLooseContentBounds?.shifted()
        if (partBounds.isNotEmpty()) {
            val shiftedParts = partBounds.mapValues { (_, r) -> r.shifted() }
            partBounds.clear()
            partBounds.putAll(shiftedParts)
        }
        if (exerciseBounds.isNotEmpty()) {
            val shiftedExercises = exerciseBounds.mapValues { (_, r) -> r.shifted() }
            exerciseBounds.clear()
            exerciseBounds.putAll(shiftedExercises)
        }
        if (partContentBounds.isNotEmpty()) {
            val shiftedPartContents = partContentBounds.mapValues { (_, r) -> r.shifted() }
            partContentBounds.clear()
            partContentBounds.putAll(shiftedPartContents)
        }
        looseContentBounds = looseContentBounds?.shifted()
    }

    fun registerExerciseBoundsDuringDrag(key: String, rect: Rect) {
        if (!isExerciseDragging) {
            exerciseBounds[key] = rect
            return
        }
        exerciseBounds[key] = rect
        if (key in frozenExerciseBounds) return
        frozenExerciseBounds = frozenExerciseBounds + (key to rect)
        val partId = key.substringBefore("|")
        if (partId == LOOSE_PART_ID) {
            frozenLooseContentBounds = unionRects(listOfNotNull(frozenLooseContentBounds, rect))
            looseContentBounds = frozenLooseContentBounds
        } else {
            val existing = frozenPartContentBounds[partId]
            val expanded = if (existing != null) unionRects(listOf(existing, rect)) else rect
            if (expanded != null) {
                frozenPartContentBounds = frozenPartContentBounds + (partId to expanded)
                val liveExisting = partContentBounds[partId]
                val liveExpanded = if (liveExisting != null) unionRects(listOf(liveExisting, rect)) else rect
                if (liveExpanded != null) partContentBounds[partId] = liveExpanded
            }
        }
    }

    fun registerPartBoundsDuringDrag(partId: String, rect: Rect) {
        if (draggingPartId == null && !isExerciseDragging) {
            partBounds[partId] = rect
            return
        }
        partBounds[partId] = rect
    }

    private fun unionRects(rects: Collection<Rect>): Rect? {
        if (rects.isEmpty()) return null
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        rects.forEach { r ->
            left = minOf(left, r.left)
            top = minOf(top, r.top)
            right = maxOf(right, r.right)
            bottom = maxOf(bottom, r.bottom)
        }
        return Rect(left, top, right, bottom)
    }

    /** Tolerancia vertical: soltar justo en los bordes/gaps de una sección no se pierde. */
    private fun Rect.containsTolerant(point: Offset, tolerance: Float = 28f): Boolean =
        point.x >= left - tolerance && point.x <= right + tolerance &&
            point.y >= top - tolerance && point.y <= bottom + tolerance

    private fun nearestDropZone(
        looseBounds: Rect?,
        partBounds: Map<String, Rect>,
        pointer: Offset,
    ): String? {
        var best: Pair<String, Rect>? = null
        var bestDistance = Float.MAX_VALUE
        if (looseBounds != null) {
            val d = gapDistance(looseBounds, pointer)
            if (d < bestDistance) {
                bestDistance = d
                best = LOOSE_PART_ID to looseBounds
            }
        }
        partBounds.forEach { (id, rect) ->
            val d = gapDistance(rect, pointer)
            if (d < bestDistance) {
                bestDistance = d
                best = id to rect
            }
        }
        val zone = best ?: return null
        // Solo "absorbe" el drop si el dedo está razonablemente cerca de la zona.
        return if (bestDistance <= 96f) zone.first else null
    }

    private fun gapDistance(rect: Rect, point: Offset): Float {
        val dx = when {
            point.x < rect.left -> rect.left - point.x
            point.x > rect.right -> point.x - rect.right
            else -> 0f
        }
        val dy = when {
            point.y < rect.top -> rect.top - point.y
            point.y > rect.bottom -> point.y - rect.bottom
            else -> 0f
        }
        return dx + dy
    }

    fun updateExerciseDrag(delta: Offset, session: Session) {
        val groupedPartsForDrag = session.parts.filterNot { it.isUncategorizedPart() }
        val activeExerciseId = draggingExerciseId ?: return
        val currentPartId = draggingExercisePartId ?: return
        draggingExerciseOffset += delta
        val startRect = dragStartExerciseRect ?: frozenExerciseBounds["$currentPartId|$activeExerciseId"] ?: return
        val pointer = Offset(
            startRect.left + dragStartGrabOffset.x + draggingExerciseOffset.x,
            startRect.top + dragStartGrabOffset.y + draggingExerciseOffset.y,
        )
        val targetPartId = when {
            frozenLooseContentBounds?.containsTolerant(pointer) == true -> LOOSE_PART_ID
            else -> groupedPartsForDrag.firstOrNull { candidate ->
                frozenPartContentBounds[candidate.id]?.containsTolerant(pointer) == true
            }?.id
        } ?: nearestDropZone(frozenLooseContentBounds, frozenPartContentBounds, pointer)
        exerciseDropTargetPartId = targetPartId
        if (targetPartId != null) {
            val sourceList = exerciseListFor(session, targetPartId(currentPartId))
            val draggedGroupId = sourceList.firstOrNull { it.id == activeExerciseId }?.supersetGroupRefOrLegacyId()
            val draggedIds = if (draggedGroupId != null) {
                sourceList.filter { it.supersetGroupRefOrLegacyId() == draggedGroupId }.map { it.id }.toSet()
            } else {
                setOf(activeExerciseId)
            }
            // La línea de caída exacta: la parte superior del ítem arrastrado caerá
            // donde esté el dedo menos el punto de agarre dentro de la tarjeta.
            val insertionY = startRect.top + dragStartGrabOffset.y + draggingExerciseOffset.y
            val orderedKeys = frozenExerciseBounds
                .filterKeys { it.startsWith("$targetPartId|") }
                .filterKeys { key -> key.substringAfter("|") !in draggedIds }
                .entries
                .sortedBy { it.value.top }
            val before = orderedKeys.firstOrNull { (_, rect) -> rect.top >= insertionY }
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
        dragStartGrabOffset = Offset(24f, 24f)
        isExerciseDragging = false
        frozenExerciseBounds = emptyMap()
        frozenPartContentBounds = emptyMap()
        frozenLooseContentBounds = null
        accumulatedScrollPx = 0f
    }

    fun beginPartDrag(partId: String) {
        draggingPartId = partId
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        dragStartPartRect = partBounds[partId]
        accumulatedScrollPx = 0f
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
        accumulatedScrollPx = 0f
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
