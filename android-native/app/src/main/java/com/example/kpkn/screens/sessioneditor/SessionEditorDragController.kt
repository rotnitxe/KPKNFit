package com.example.kpkn.screens.sessioneditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/**
 * Unified drag-and-drop state for reordering parts and exercises in the session editor.
 * Uses robust section boundary hit-testing and symmetrical midpoint thresholds for drop targets.
 */
class SessionEditorDragController {

    val partBounds = mutableStateMapOf<String, Rect>()
    val partFooterBounds = mutableStateMapOf<String, Rect>()
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
    var frozenPartBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenPartFooterBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenPartContentBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenLooseContentBounds by mutableStateOf<Rect?>(null)

    // Compensación de scroll durante drag: en window-coords todo se desplaza
    // al scrollear. Los frozen se capturan en window y deben desplazarse
    // inverso al scroll para que el pointer (dedo en window) siga comparándose
    // contra rects en window actuales.
    private var accumulatedScrollPx: Float = 0f

    fun clearBounds() {
        partBounds.clear()
        partFooterBounds.clear()
        partContentBounds.clear()
        exerciseBounds.clear()
        looseContentBounds = null
    }

    /** Mantiene solo los bounds de ítems que siguen existiendo (y no están colapsados). */
    fun pruneBounds(session: Session, collapsedPartIds: Set<String>) {
        val activePartIds = session.parts.filterNot { it.isUncategorizedPart() }.map { it.id }.toSet()
        partBounds.keys.retainAll(activePartIds)
        partFooterBounds.keys.retainAll(activePartIds)
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
    }

    fun beginExerciseDrag(
        partId: String,
        exerciseId: String,
        grabOffset: Offset = Offset(24f, 24f),
        liveBounds: Map<String, Rect>? = null,
    ) {
        draggingExerciseId = exerciseId
        draggingExercisePartId = partId
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        dragStartGrabOffset = grabOffset
        isExerciseDragging = true
        accumulatedScrollPx = 0f

        if (liveBounds != null) {
            liveBounds.forEach { (k, r) ->
                when {
                    k.startsWith("header|") -> partBounds[k.removePrefix("header|")] = r
                    k.startsWith("footer|") -> partFooterBounds[k.removePrefix("footer|")] = r
                    k.startsWith("loose_container|") -> looseContentBounds = r
                    else -> exerciseBounds[k] = r
                }
            }
        }

        dragStartExerciseRect = liveBounds?.get("$partId|$exerciseId")
            ?: exerciseBounds["$partId|$exerciseId"]

        frozenExerciseBounds = exerciseBounds.toMap()
        frozenPartBounds = partBounds.toMap()
        frozenPartFooterBounds = partFooterBounds.toMap()

        // Zonas derivadas de las coordenadas exactas actuales en ventana
        val freshLoose = unionRects(frozenExerciseBounds.filterKeys { it.startsWith("$LOOSE_PART_ID|") }.values)
            ?: looseContentBounds
        frozenLooseContentBounds = freshLoose

        val allPartIds = (frozenPartBounds.keys + frozenExerciseBounds.keys.mapNotNull {
            val pid = it.substringBefore("|")
            if (pid != LOOSE_PART_ID) pid else null
        } + frozenPartFooterBounds.keys).toSet()

        val freshParts = allPartIds.mapNotNull { pid ->
            val rects = mutableListOf<Rect>()
            frozenPartBounds[pid]?.let { rects += it }
            rects.addAll(frozenExerciseBounds.filterKeys { it.startsWith("$pid|") }.values)
            frozenPartFooterBounds[pid]?.let { rects += it }
            val union = unionRects(rects) ?: return@mapNotNull null
            pid to union
        }.toMap()

        frozenPartContentBounds = freshParts
        if (freshLoose != null) looseContentBounds = freshLoose
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
        frozenPartBounds = frozenPartBounds.mapValues { (_, r) -> r.shifted() }
        frozenPartFooterBounds = frozenPartFooterBounds.mapValues { (_, r) -> r.shifted() }
        frozenPartContentBounds = frozenPartContentBounds.mapValues { (_, r) -> r.shifted() }
        frozenLooseContentBounds = frozenLooseContentBounds?.shifted()
        if (partBounds.isNotEmpty()) {
            val shiftedParts = partBounds.mapValues { (_, r) -> r.shifted() }
            partBounds.clear()
            partBounds.putAll(shiftedParts)
        }
        if (partFooterBounds.isNotEmpty()) {
            val shiftedFooters = partFooterBounds.mapValues { (_, r) -> r.shifted() }
            partFooterBounds.clear()
            partFooterBounds.putAll(shiftedFooters)
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

    fun registerPartFooterBounds(partId: String, rect: Rect) {
        partFooterBounds[partId] = rect
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

    fun updateExerciseDrag(
        delta: Offset,
        session: Session,
        groupedParts: List<com.example.kpkn.data.models.SessionPart>? = null,
    ) {
        val groupedPartsForDrag = groupedParts ?: session.parts.filterNot { it.isUncategorizedPart() }
        val activeExerciseId = draggingExerciseId ?: return
        val currentPartId = draggingExercisePartId ?: return
        draggingExerciseOffset += delta
        val startRect = dragStartExerciseRect
            ?: frozenExerciseBounds["$currentPartId|$activeExerciseId"]
            ?: exerciseBounds["$currentPartId|$activeExerciseId"]
            ?: Rect(0f, 0f, 300f, 88f)
        val pointer = Offset(
            startRect.left + dragStartGrabOffset.x + draggingExerciseOffset.x,
            startRect.top + dragStartGrabOffset.y + draggingExerciseOffset.y,
        )

        val isDraggingCardio = run {
            val sourceList = exerciseListFor(session, toNullablePartId(currentPartId))
            val ex = sourceList.firstOrNull { it.id == activeExerciseId }
            ex?.isCardio == true || (groupedPartsForDrag.firstOrNull { it.id == currentPartId }?.isCardioPart() == true)
        }

        // Estructura de zonas ordenadas verticalmente para determinar el grupo objetivo sin solapamientos
        data class SectionZone(val id: String, val bounds: Rect)
        val sections = mutableListOf<SectionZone>()

        if (!isDraggingCardio) {
            // Fuerza SOLO puede ir a ejercicios sueltos o a grupos de fuerza
            val looseRect = frozenLooseContentBounds
                ?: looseContentBounds
                ?: unionRects(frozenExerciseBounds.filterKeys { it.startsWith("$LOOSE_PART_ID|") }.values)
            if (looseRect != null) {
                sections.add(SectionZone(LOOSE_PART_ID, looseRect))
            }

            groupedPartsForDrag.filterNot { it.isCardioPart() }.forEach { part ->
                val pBounds = frozenPartContentBounds[part.id] ?: run {
                    val rects = mutableListOf<Rect>()
                    frozenPartBounds[part.id]?.let { rects += it }
                    frozenPartFooterBounds[part.id]?.let { rects += it }
                    rects.addAll(frozenExerciseBounds.filterKeys { it.startsWith("${part.id}|") }.values)
                    unionRects(rects)
                } ?: run {
                    val rects = mutableListOf<Rect>()
                    partBounds[part.id]?.let { rects += it }
                    partFooterBounds[part.id]?.let { rects += it }
                    unionRects(rects)
                }
                if (pBounds != null) {
                    sections.add(SectionZone(part.id, pBounds))
                }
            }
        } else {
            // Cardio SOLO puede ir a grupos de cardio
            groupedPartsForDrag.filter { it.isCardioPart() }.forEach { part ->
                val pBounds = frozenPartContentBounds[part.id] ?: run {
                    val rects = mutableListOf<Rect>()
                    frozenPartBounds[part.id]?.let { rects += it }
                    frozenPartFooterBounds[part.id]?.let { rects += it }
                    rects.addAll(frozenExerciseBounds.filterKeys { it.startsWith("${part.id}|") }.values)
                    unionRects(rects)
                } ?: run {
                    val rects = mutableListOf<Rect>()
                    partBounds[part.id]?.let { rects += it }
                    partFooterBounds[part.id]?.let { rects += it }
                    unionRects(rects)
                }
                if (pBounds != null) {
                    sections.add(SectionZone(part.id, pBounds))
                }
            }
        }

        val sortedSections = sections.sortedBy { it.bounds.top }

        val targetPartId: String? = when {
            sortedSections.isEmpty() -> null
            sortedSections.size == 1 -> sortedSections.first().id
            pointer.y < sortedSections.first().bounds.top -> sortedSections.first().id
            pointer.y >= sortedSections.last().bounds.bottom -> sortedSections.last().id
            else -> {
                var matched: String? = null
                for (i in 0 until sortedSections.size - 1) {
                    val current = sortedSections[i]
                    val next = sortedSections[i + 1]
                    val boundaryY = (current.bounds.bottom + next.bounds.top) / 2f
                    if (pointer.y < boundaryY) {
                        matched = current.id
                        break
                    }
                }
                matched ?: sortedSections.last().id
            }
        }

        var newDropTargetKey: String? = null
        var newDropTargetIndex: Int? = null

        if (targetPartId != null) {
            val sourceList = exerciseListFor(session, toNullablePartId(currentPartId))
            val draggedGroupId = sourceList.firstOrNull { it.id == activeExerciseId }?.supersetGroupRefOrLegacyId()
            val draggedIds = if (draggedGroupId != null) {
                sourceList.filter { it.supersetGroupRefOrLegacyId() == draggedGroupId }.map { it.id }.toSet()
            } else {
                setOf(activeExerciseId)
            }

            val targetList = exerciseListFor(session, toNullablePartId(targetPartId))
            val candidateEntries = frozenExerciseBounds
                .filterKeys { it.startsWith("$targetPartId|") }
                .filterKeys { key -> key.substringAfter("|") !in draggedIds }
                .entries
                .sortedBy { (it.value.top + it.value.bottom) / 2f }

            if (candidateEntries.isEmpty()) {
                newDropTargetKey = null
                newDropTargetIndex = 0
            } else {
                val pointerY = pointer.y
                val firstCenter = (candidateEntries.first().value.top + candidateEntries.first().value.bottom) / 2f
                val lastCenter = (candidateEntries.last().value.top + candidateEntries.last().value.bottom) / 2f

                if (pointerY < firstCenter) {
                    val firstExId = candidateEntries.first().key.substringAfter("|")
                    val idx = targetList.indexOfFirst { it.id == firstExId }.coerceAtLeast(0)
                    newDropTargetKey = candidateEntries.first().key
                    newDropTargetIndex = idx
                } else if (pointerY >= lastCenter) {
                    val lastExId = candidateEntries.last().key.substringAfter("|")
                    val lastIdx = targetList.indexOfFirst { it.id == lastExId }
                    val idx = if (lastIdx >= 0) lastIdx + 1 else targetList.size
                    newDropTargetKey = null
                    newDropTargetIndex = idx
                } else {
                    var found = false
                    for (i in 0 until candidateEntries.size - 1) {
                        val currentEntry = candidateEntries[i]
                        val nextEntry = candidateEntries[i + 1]
                        val currCenter = (currentEntry.value.top + currentEntry.value.bottom) / 2f
                        val nextCenter = (nextEntry.value.top + nextEntry.value.bottom) / 2f
                        if (pointerY >= currCenter && pointerY < nextCenter) {
                            val nextExId = nextEntry.key.substringAfter("|")
                            val idx = targetList.indexOfFirst { it.id == nextExId }.coerceAtLeast(0)
                            newDropTargetKey = nextEntry.key
                            newDropTargetIndex = idx
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        newDropTargetKey = null
                        newDropTargetIndex = targetList.size
                    }
                }
            }
        }

        if (exerciseDropTargetPartId != targetPartId) {
            exerciseDropTargetPartId = targetPartId
        }
        if (exerciseDropTargetKey != newDropTargetKey) {
            exerciseDropTargetKey = newDropTargetKey
        }
        if (exerciseDropTargetIndex != newDropTargetIndex) {
            exerciseDropTargetIndex = newDropTargetIndex
        }
    }

    fun endExerciseDrag(
        session: Session,
        onMoveExercise: (fromPartId: String?, exerciseId: String, toPartId: String?, toIndex: Int?) -> Unit,
    ) {
        val activeExerciseId = draggingExerciseId
        val currentPartId = draggingExercisePartId
        if (activeExerciseId != null && currentPartId != null) {
            val finalTargetPart = exerciseDropTargetPartId
            val finalTargetIdx = exerciseDropTargetIndex
            if (finalTargetPart != null) {
                val isCardio = run {
                    val srcList = exerciseListFor(session, toNullablePartId(currentPartId))
                    val ex = srcList.firstOrNull { it.id == activeExerciseId }
                    ex?.isCardio == true || (session.parts.firstOrNull { it.id == currentPartId }?.isCardioPart() == true)
                }
                val targetIsCardio = session.parts.firstOrNull { it.id == toNullablePartId(finalTargetPart) }?.isCardioPart() == true
                if (isCardio == targetIsCardio) {
                    onMoveExercise(
                        toNullablePartId(currentPartId),
                        activeExerciseId,
                        toNullablePartId(finalTargetPart),
                        finalTargetIdx,
                    )
                }
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
        frozenPartBounds = emptyMap()
        frozenPartFooterBounds = emptyMap()
        frozenPartContentBounds = emptyMap()
        frozenLooseContentBounds = null
        accumulatedScrollPx = 0f
    }

    fun beginPartDrag(
        partId: String,
        livePartBounds: Map<String, Rect>? = null,
        liveStartRect: Rect? = null,
    ) {
        draggingPartId = partId
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        if (livePartBounds != null) {
            partBounds.putAll(livePartBounds)
        }
        dragStartPartRect = liveStartRect ?: livePartBounds?.get(partId) ?: partBounds[partId]
        accumulatedScrollPx = 0f
    }

    fun updatePartDrag(deltaY: Float, groupedParts: List<com.example.kpkn.data.models.SessionPart>) {
        val activeId = draggingPartId ?: return
        draggingPartOffsetY += deltaY
        val startRect = dragStartPartRect ?: partBounds[activeId] ?: Rect(0f, 0f, 300f, 56f)
        val pointerY = startRect.center.y + draggingPartOffsetY

        val remaining = groupedParts.filter { it.id != activeId }
        val ordered = remaining
            .mapNotNull { part -> partBounds[part.id]?.let { part to it } }
            .sortedBy { it.second.center.y }

        if (ordered.isEmpty()) {
            partDropTargetId = null
            partDropTargetIndex = 0
            return
        }

        val beforeIndex = ordered.indexOfFirst { (_, rect) -> pointerY < rect.center.y }
        if (beforeIndex != -1) {
            val targetPart = ordered[beforeIndex].first
            partDropTargetId = "BEFORE_${targetPart.id}"
            partDropTargetIndex = beforeIndex
        } else {
            val lastPart = ordered.last().first
            partDropTargetId = "AFTER_${lastPart.id}"
            partDropTargetIndex = ordered.size
        }
    }

    fun endPartDrag(
        groupedParts: List<com.example.kpkn.data.models.SessionPart>,
        onMovePart: (partId: String, toIndex: Int) -> Unit,
    ) {
        val activeId = draggingPartId
        val targetIndex = partDropTargetIndex
        if (activeId != null && targetIndex != null) {
            onMovePart(activeId, targetIndex)
        }
        draggingPartId = null
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        dragStartPartRect = null
        accumulatedScrollPx = 0f
    }

    fun calculateProjectedShift(
        session: Session,
        partId: String,
        index: Int,
        exerciseId: String,
        itemHeight: Float,
    ): Float {
        val activeId = draggingExerciseId ?: return 0f
        val currentPartId = draggingExercisePartId ?: return 0f
        val targetPartId = exerciseDropTargetPartId ?: return 0f
        val targetIndex = exerciseDropTargetIndex ?: return 0f

        if (partId != targetPartId) return 0f

        val sourceList = exerciseListFor(session, toNullablePartId(currentPartId))
        val draggedGroupId = sourceList.firstOrNull { it.id == activeId }?.supersetGroupRefOrLegacyId()
        val draggedIds = if (draggedGroupId != null) {
            sourceList.filter { it.supersetGroupRefOrLegacyId() == draggedGroupId }.map { it.id }.toSet()
        } else {
            setOf(activeId)
        }

        if (exerciseId in draggedIds) return 0f

        val movingCount = draggedIds.size.coerceAtLeast(1)
        val gap = (itemHeight + 8f) * movingCount

        // Cross-part drag
        if (currentPartId != targetPartId) {
            return if (index >= targetIndex) gap else 0f
        }

        // Same-part drag
        val sourceIndex = sourceList.indexOfFirst { it.id == activeId }
        if (sourceIndex < 0 || targetIndex == sourceIndex) return 0f

        return when {
            // Moving UP: elements between targetIndex (inclusive) and sourceIndex (exclusive) shift DOWN by gap
            targetIndex < sourceIndex && index >= targetIndex && index < sourceIndex -> gap
            // Moving DOWN: elements between sourceIndex (exclusive) and targetIndex (exclusive) shift UP by gap
            targetIndex > sourceIndex && index > sourceIndex && index < targetIndex -> -gap
            else -> 0f
        }
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
        return if (index >= targetIndex) activeRect.height else 0f
    }

    /**
     * Projected shift for the "add exercise" footer button of a part while an
     * exercise drag is targeting the very end of that part.
     */
    fun projectedFooterShiftFor(partId: String, partExerciseCount: Int): Float {
        val activeId = draggingExerciseId ?: return 0f
        val currentPartId = draggingExercisePartId ?: return 0f
        val targetPartId = exerciseDropTargetPartId ?: return 0f
        val targetIndex = exerciseDropTargetIndex ?: return 0f
        if (targetPartId != partId) return 0f
        if (targetIndex < partExerciseCount) return 0f
        val activeKey = "$currentPartId|$activeId"
        val activeRect = exerciseBounds[activeKey] ?: return 0f
        return activeRect.height
    }

    companion object {
        const val LOOSE_PART_ID = "__loose__"

        fun toNullablePartId(partId: String): String? =
            partId.takeUnless { it == LOOSE_PART_ID }

        fun exerciseListFor(session: Session, partId: String?): List<Exercise> =
            if (partId == null) session.exercises
            else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()
    }
}
