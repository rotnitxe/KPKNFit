package com.example.kpkn.screens.sessioneditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/** Outcome of [SessionEditorDragController.endExerciseDrag] for UI feedback. */
enum class ExerciseDragEndResult {
    Moved,
    OutOfRange,
    NoOp,
    Inactive,
}

/**
 * Snapshot of drag UI fields for ViewModel [StateFlow] observation (UDF).
 * Bounds maps stay on the controller; this covers gesture/target feedback only.
 */
data class SessionEditorDragUiState(
    val draggingExerciseId: String? = null,
    val draggingExercisePartId: String? = null,
    val draggingExerciseOffset: Offset = Offset.Zero,
    val exerciseDropTargetKey: String? = null,
    val exerciseDropTargetPartId: String? = null,
    val exerciseDropTargetIndex: Int? = null,
    val exerciseDropOutOfRange: Boolean = false,
    val draggingPartId: String? = null,
    val draggingPartOffsetY: Float = 0f,
    val partDropTargetId: String? = null,
    val partDropTargetIndex: Int? = null,
    val dragStartExerciseRect: Rect? = null,
    val dragStartPartRect: Rect? = null,
)

/**
 * Unified drag-and-drop state for reordering parts and exercises in the session editor.
 * Uses robust section boundary hit-testing and symmetrical midpoint thresholds for drop targets.
 */
class SessionEditorDragController {

    /** Optional listener for ViewModel StateFlow mirroring after UI-relevant mutations. */
    var onUiStateChanged: (() -> Unit)? = null

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
    var dragPartPointerStartWindow by mutableStateOf<Offset?>(null)

    var draggingExerciseId by mutableStateOf<String?>(null)
    var draggingExercisePartId by mutableStateOf<String?>(null)
    var draggingExerciseOffset by mutableStateOf(Offset.Zero)
    var exerciseDropTargetKey by mutableStateOf<String?>(null)
    var exerciseDropTargetPartId by mutableStateOf<String?>(null)
    var exerciseDropTargetIndex by mutableStateOf<Int?>(null)
    var dragStartExerciseRect by mutableStateOf<Rect?>(null)
    /** @deprecated Prefer [dragPointerStartWindow]; kept for legacy grab-offset callers. */
    var dragStartGrabOffset by mutableStateOf(Offset(24f, 24f))
    /** Window-space pointer position at drag start (eliminates handle-relative bias). */
    var dragPointerStartWindow by mutableStateOf<Offset?>(null)
    /** True when the pointer is outside any valid section by more than [SECTION_NEUTRAL_MARGIN_PX]. */
    var exerciseDropOutOfRange by mutableStateOf(false)

    var isExerciseDragging by mutableStateOf(false)
    var frozenExerciseBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenPartBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenPartFooterBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenPartContentBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
    var frozenLooseContentBounds by mutableStateOf<Rect?>(null)
    var frozenPartDragBounds by mutableStateOf<Map<String, Rect>>(emptyMap())

    /** Active session snapshot for recompute-from-scroll without a fresh call site. */
    private var lastExerciseDragSession: Session? = null
    private var lastExerciseDragGroupedParts: List<SessionPart>? = null
    private var lastPartDragGroupedParts: List<SessionPart>? = null
    private var collapsedPartIdsForDrag: Set<String> = emptySet()

    // Compensación de scroll durante drag (legacy counter; applyScrollDelta is authoritative).
    @Suppress("unused")
    private var accumulatedScrollPx: Float = 0f

    fun snapshotUiState(): SessionEditorDragUiState = SessionEditorDragUiState(
        draggingExerciseId = draggingExerciseId,
        draggingExercisePartId = draggingExercisePartId,
        draggingExerciseOffset = draggingExerciseOffset,
        exerciseDropTargetKey = exerciseDropTargetKey,
        exerciseDropTargetPartId = exerciseDropTargetPartId,
        exerciseDropTargetIndex = exerciseDropTargetIndex,
        exerciseDropOutOfRange = exerciseDropOutOfRange,
        draggingPartId = draggingPartId,
        draggingPartOffsetY = draggingPartOffsetY,
        partDropTargetId = partDropTargetId,
        partDropTargetIndex = partDropTargetIndex,
        dragStartExerciseRect = dragStartExerciseRect,
        dragStartPartRect = dragStartPartRect,
    )

    private fun notifyUiStateChanged() {
        onUiStateChanged?.invoke()
    }

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

    /**
     * @param pointerStartWindow Finger position in window coords at gesture start (preferred).
     * @param grabOffset Legacy handle-local offset; ignored when [pointerStartWindow] is set.
     */
    fun beginExerciseDrag(
        partId: String,
        exerciseId: String,
        grabOffset: Offset = Offset(24f, 24f),
        liveBounds: Map<String, Rect>? = null,
        pointerStartWindow: Offset? = null,
        session: Session? = null,
        collapsedPartIds: Set<String> = emptySet(),
    ): Boolean {
        if (liveBounds != null) {
            liveBounds.forEach { (k, r) ->
                when {
                    k.startsWith("header|") -> partBounds[k.removePrefix("header|")] = r
                    k.startsWith("footer|") -> partFooterBounds[k.removePrefix("footer|")] = r
                    k.startsWith("loose_container|") -> {
                        // Only accept StrengthAddActions as loose zone when there are no loose exercises.
                        if (looseContentBounds == null ||
                            exerciseBounds.keys.none { it.startsWith("$LOOSE_PART_ID|") }
                        ) {
                            looseContentBounds = r
                        }
                    }
                    else -> exerciseBounds[k] = r
                }
            }
        }

        // Superset-as-block: always drag via the first member's key / block rect.
        val resolved = resolveSupersetBlockAnchor(partId, exerciseId, session)
        val resolvedPartId = resolved.partId
        val resolvedExerciseId = resolved.exerciseId
        val blockRect = resolved.blockRect
            ?: liveBounds?.get("$resolvedPartId|$resolvedExerciseId")
            ?: exerciseBounds["$resolvedPartId|$resolvedExerciseId"]

        if (blockRect == null) {
            // Abort: no initial rect → refuse to start a gesture with origin (0,0).
            return false
        }

        draggingExerciseId = resolvedExerciseId
        draggingExercisePartId = resolvedPartId
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        exerciseDropOutOfRange = false
        dragStartGrabOffset = grabOffset
        dragPointerStartWindow = pointerStartWindow
        dragStartExerciseRect = blockRect
        isExerciseDragging = true
        accumulatedScrollPx = 0f
        collapsedPartIdsForDrag = collapsedPartIds
        lastExerciseDragSession = session
        lastExerciseDragGroupedParts = session?.parts?.filterNot { it.isUncategorizedPart() }

        frozenExerciseBounds = collapseSupersetMemberBounds(exerciseBounds.toMap(), session)
        frozenPartBounds = partBounds.toMap()
        frozenPartFooterBounds = partFooterBounds.toMap()

        rebuildFrozenZones(session)
        notifyUiStateChanged()
        return true
    }

    /**
     * Desplaza todos los Rect congelados inverso al scroll de la lista.
     * deltaPx > 0  => contenido sube (scroll down), window Y de los Rect baja.
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
        frozenPartDragBounds = frozenPartDragBounds.mapValues { (_, r) -> r.shifted() }
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
        // Window-space finger position stays fixed during auto-scroll; only content rects move.
        // Do not shift dragPointerStartWindow / dragPartPointerStartWindow / dragStart*Rect.
    }

    /**
     * Registers live bounds during drag after subtracting the projected shift feedback
     * so frozen midpoints are not polluted by the shift graphicsLayer (N5).
     */
    fun registerExerciseBoundsDuringDrag(key: String, rect: Rect, shiftY: Float = 0f) {
        val corrected = if (shiftY != 0f) {
            Rect(rect.left, rect.top - shiftY, rect.right, rect.bottom - shiftY)
        } else {
            rect
        }
        if (!isExerciseDragging) {
            exerciseBounds[key] = corrected
            return
        }
        // During drag: never let live (possibly shift-tainted) writes invent member keys
        // for a collapsed-superset block — only update existing frozen keys or new viewport keys
        // that are not member-duplicates of a block already frozen via firstMember.
        exerciseBounds[key] = corrected
        val partId = key.substringBefore("|")
        val exerciseId = key.substringAfter("|")
        val session = lastExerciseDragSession
        if (session != null && isNonFirstSupersetMember(session, partId, exerciseId)) {
            return
        }
        frozenExerciseBounds = frozenExerciseBounds + (key to corrected)
        rebuildFrozenZones(session)
    }

    fun registerPartBoundsDuringDrag(partId: String, rect: Rect) {
        if (draggingPartId == null && !isExerciseDragging) {
            partBounds[partId] = rect
            return
        }
        // During part drag, frozen map is authoritative; only seed missing keys (off-screen entry).
        partBounds[partId] = rect
        if (draggingPartId != null && partId !in frozenPartDragBounds) {
            frozenPartDragBounds = frozenPartDragBounds + (partId to rect)
        }
    }

    fun registerPartFooterBounds(partId: String, rect: Rect) {
        partFooterBounds[partId] = rect
        if (isExerciseDragging && partId !in frozenPartFooterBounds) {
            frozenPartFooterBounds = frozenPartFooterBounds + (partId to rect)
            rebuildFrozenZones(lastExerciseDragSession)
        }
    }

    /** Direct assignment of part content zone (single write path; no monotonic merge). */
    fun setPartContentBounds(partId: String, rect: Rect) {
        if (isExerciseDragging) {
            // Live writes during drag must not stomp shift-adjusted frozen zones unless new.
            if (partId !in frozenPartContentBounds) {
                frozenPartContentBounds = frozenPartContentBounds + (partId to rect)
                partContentBounds[partId] = rect
            }
            return
        }
        partContentBounds[partId] = rect
    }

    /** Direct assignment of loose zone outside drag; during drag only seeds if empty. */
    fun setLooseContentBounds(rect: Rect, fromStrengthAddActions: Boolean = false) {
        if (isExerciseDragging) {
            if (frozenLooseContentBounds == null && !fromStrengthAddActions) {
                frozenLooseContentBounds = rect
                looseContentBounds = rect
            }
            return
        }
        if (fromStrengthAddActions) {
            val hasLooseExercises = exerciseBounds.keys.any { it.startsWith("$LOOSE_PART_ID|") }
            if (hasLooseExercises) return
            // Empty-session / empty-loose fallback only.
            looseContentBounds = rect
            return
        }
        looseContentBounds = rect
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

    private fun rebuildFrozenZones(session: Session?) {
        val looseFromExercises = unionRects(
            frozenExerciseBounds.filterKeys { it.startsWith("$LOOSE_PART_ID|") }.values,
        )
        val strengthParts = session?.parts?.filterNot { it.isUncategorizedPart() || it.isCardioPart() }.orEmpty()
        val hasStrengthGroups = strengthParts.isNotEmpty()
        val hasLooseExercises = session?.exercises?.isNotEmpty() == true ||
            frozenExerciseBounds.keys.any { it.startsWith("$LOOSE_PART_ID|") }
        val isEmptySession = session != null &&
            session.exercises.isEmpty() &&
            session.parts.none { !it.isUncategorizedPart() }

        frozenLooseContentBounds = when {
            looseFromExercises != null -> looseFromExercises
            isEmptySession -> looseContentBounds
            !hasLooseExercises && hasStrengthGroups -> {
                syntheticLooseZone(strengthParts)
            }
            else -> looseContentBounds.takeIf { hasLooseExercises || isEmptySession }
        }
        if (frozenLooseContentBounds != null) {
            looseContentBounds = frozenLooseContentBounds
        }

        val allPartIds = (
            frozenPartBounds.keys +
                frozenExerciseBounds.keys.mapNotNull { key ->
                    val pid = key.substringBefore("|")
                    pid.takeUnless { it == LOOSE_PART_ID }
                } +
                frozenPartFooterBounds.keys
            ).toSet()

        val freshParts = allPartIds.mapNotNull { pid ->
            val rects = mutableListOf<Rect>()
            frozenPartBounds[pid]?.let { rects += it }
            rects.addAll(frozenExerciseBounds.filterKeys { it.startsWith("$pid|") }.values)
            frozenPartFooterBounds[pid]?.let { rects += it }
            val union = unionRects(rects) ?: return@mapNotNull null
            pid to union
        }.toMap()

        // Enforce disjoint sections vs loose (clip overlapping tops).
        val disjointParts = enforceDisjointAgainstLoose(freshParts, frozenLooseContentBounds)
        frozenPartContentBounds = disjointParts
        partContentBounds.clear()
        partContentBounds.putAll(disjointParts)
    }

    private fun syntheticLooseZone(strengthParts: List<SessionPart>): Rect? {
        val firstPartId = strengthParts.firstOrNull()?.id ?: return null
        val firstTop = frozenPartBounds[firstPartId]?.top
            ?: frozenPartContentBounds[firstPartId]?.top
            ?: partBounds[firstPartId]?.top
            ?: return null
        val left = frozenPartBounds[firstPartId]?.left
            ?: partBounds[firstPartId]?.left
            ?: 0f
        val right = frozenPartBounds[firstPartId]?.right
            ?: partBounds[firstPartId]?.right
            ?: 300f
        return Rect(left, firstTop - SYNTHETIC_LOOSE_HEIGHT_PX, right, firstTop)
    }

    private fun enforceDisjointAgainstLoose(
        parts: Map<String, Rect>,
        loose: Rect?,
    ): Map<String, Rect> {
        if (loose == null) return parts
        return parts.mapValues { (_, rect) ->
            if (rect.top < loose.bottom && rect.bottom > loose.top) {
                // Clip part top to loose.bottom so zones do not overlap.
                if (rect.bottom > loose.bottom) {
                    Rect(rect.left, maxOf(rect.top, loose.bottom), rect.right, rect.bottom)
                } else {
                    rect
                }
            } else {
                rect
            }
        }
    }

    /** Returns section zones sorted by top; guaranteed pairwise disjoint for tests. */
    fun buildSortedSectionsForTest(
        session: Session,
        groupedParts: List<SessionPart>? = null,
        isDraggingCardio: Boolean = false,
    ): List<Pair<String, Rect>> {
        return buildSections(session, groupedParts, isDraggingCardio).map { it.id to it.bounds }
    }

    private data class SectionZone(val id: String, val bounds: Rect)

    private fun buildSections(
        session: Session,
        groupedParts: List<SessionPart>?,
        isDraggingCardio: Boolean,
    ): List<SectionZone> {
        val groupedPartsForDrag = groupedParts ?: session.parts.filterNot { it.isUncategorizedPart() }
        val sections = mutableListOf<SectionZone>()

        if (!isDraggingCardio) {
            val looseRect = frozenLooseContentBounds
                ?: looseContentBounds
                ?: unionRects(frozenExerciseBounds.filterKeys { it.startsWith("$LOOSE_PART_ID|") }.values)
            if (looseRect != null) {
                sections.add(SectionZone(LOOSE_PART_ID, looseRect))
            }
            groupedPartsForDrag.filterNot { it.isCardioPart() }.forEach { part ->
                resolvePartZone(part.id)?.let { sections.add(SectionZone(part.id, it)) }
            }
        } else {
            groupedPartsForDrag.filter { it.isCardioPart() }.forEach { part ->
                resolvePartZone(part.id)?.let { sections.add(SectionZone(part.id, it)) }
            }
        }

        val sorted = sections.sortedBy { it.bounds.top }
        return makeDisjointByMidpoints(sorted)
    }

    private fun resolvePartZone(partId: String): Rect? {
        frozenPartContentBounds[partId]?.let { return it }
        val rects = mutableListOf<Rect>()
        frozenPartBounds[partId]?.let { rects += it }
        frozenPartFooterBounds[partId]?.let { rects += it }
        rects.addAll(frozenExerciseBounds.filterKeys { it.startsWith("$partId|") }.values)
        unionRects(rects)?.let { return it }
        val live = mutableListOf<Rect>()
        partBounds[partId]?.let { live += it }
        partFooterBounds[partId]?.let { live += it }
        return unionRects(live)
    }

    /** Clip overlapping section bottoms/tops to midpoints so zones are pairwise disjoint. */
    private fun makeDisjointByMidpoints(sorted: List<SectionZone>): List<SectionZone> {
        if (sorted.size <= 1) return sorted
        val result = sorted.toMutableList()
        for (i in 0 until result.size - 1) {
            val current = result[i]
            val next = result[i + 1]
            if (current.bounds.bottom > next.bounds.top) {
                val mid = (current.bounds.bottom + next.bounds.top) / 2f
                result[i] = current.copy(
                    bounds = Rect(current.bounds.left, current.bounds.top, current.bounds.right, mid),
                )
                result[i + 1] = next.copy(
                    bounds = Rect(next.bounds.left, mid, next.bounds.right, next.bounds.bottom),
                )
            }
        }
        return result
    }

    fun currentExercisePointer(): Offset? {
        val startWindow = dragPointerStartWindow
        if (startWindow != null) {
            return startWindow + draggingExerciseOffset
        }
        val startRect = dragStartExerciseRect ?: return null
        return Offset(
            startRect.left + dragStartGrabOffset.x + draggingExerciseOffset.x,
            startRect.top + dragStartGrabOffset.y + draggingExerciseOffset.y,
        )
    }

    fun updateExerciseDrag(
        delta: Offset,
        session: Session,
        groupedParts: List<SessionPart>? = null,
    ) {
        if (draggingExerciseId == null || draggingExercisePartId == null) return
        draggingExerciseOffset += delta
        lastExerciseDragSession = session
        lastExerciseDragGroupedParts = groupedParts ?: session.parts.filterNot { it.isUncategorizedPart() }
        recomputeExerciseDropTarget()
    }

    /** Recompute drop target from the current pointer without consuming a new delta (auto-scroll). */
    fun recomputeExerciseDropTarget() {
        val session = lastExerciseDragSession ?: return
        val groupedPartsForDrag = lastExerciseDragGroupedParts
            ?: session.parts.filterNot { it.isUncategorizedPart() }
        val activeExerciseId = draggingExerciseId ?: return
        val currentPartId = draggingExercisePartId ?: return
        val pointer = currentExercisePointer() ?: return

        val isDraggingCardio = run {
            val sourceList = exerciseListFor(session, toNullablePartId(currentPartId))
            val ex = sourceList.firstOrNull { it.id == activeExerciseId }
            ex?.isCardio == true || (groupedPartsForDrag.firstOrNull { it.id == currentPartId }?.isCardioPart() == true)
        }

        val sortedSections = buildSections(session, groupedPartsForDrag, isDraggingCardio)

        val targetPartId: String? = resolveSectionTarget(pointer.y, sortedSections)
        exerciseDropOutOfRange = targetPartId == null && sortedSections.isNotEmpty()

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

            val targetPart = groupedPartsForDrag.firstOrNull { it.id == toNullablePartId(targetPartId) }
            val isCollapsedTarget = targetPart != null && targetPart.id in collapsedPartIdsForDrag

            if (candidateEntries.isEmpty()) {
                newDropTargetKey = null
                // Collapsed part: append (N10). Empty expanded part: index 0.
                newDropTargetIndex = if (isCollapsedTarget) targetList.size else 0
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
        notifyUiStateChanged()
    }

    private fun resolveSectionTarget(pointerY: Float, sortedSections: List<SectionZone>): String? {
        if (sortedSections.isEmpty()) return null
        if (sortedSections.size == 1) {
            val only = sortedSections.first()
            val dist = when {
                pointerY < only.bounds.top -> only.bounds.top - pointerY
                pointerY > only.bounds.bottom -> pointerY - only.bounds.bottom
                else -> 0f
            }
            return if (dist <= SECTION_NEUTRAL_MARGIN_PX) only.id else null
        }
        if (pointerY < sortedSections.first().bounds.top) {
            val dist = sortedSections.first().bounds.top - pointerY
            return if (dist <= SECTION_NEUTRAL_MARGIN_PX) sortedSections.first().id else null
        }
        if (pointerY >= sortedSections.last().bounds.bottom) {
            val dist = pointerY - sortedSections.last().bounds.bottom
            return if (dist <= SECTION_NEUTRAL_MARGIN_PX) sortedSections.last().id else null
        }
        for (i in 0 until sortedSections.size - 1) {
            val current = sortedSections[i]
            val next = sortedSections[i + 1]
            val boundaryY = (current.bounds.bottom + next.bounds.top) / 2f
            if (pointerY < boundaryY) return current.id
        }
        return sortedSections.last().id
    }

    fun endExerciseDrag(
        session: Session,
        onMoveExercise: (fromPartId: String?, exerciseId: String, toPartId: String?, toIndex: Int?) -> Unit,
    ): ExerciseDragEndResult {
        lastExerciseDragSession = session
        // Final recompute before commit (N10) — covers last auto-scroll tick lag.
        recomputeExerciseDropTarget()
        val activeExerciseId = draggingExerciseId
        val currentPartId = draggingExercisePartId
        if (activeExerciseId == null || currentPartId == null) {
            resetExerciseDrag()
            return ExerciseDragEndResult.Inactive
        }
        if (exerciseDropOutOfRange) {
            resetExerciseDrag()
            return ExerciseDragEndResult.OutOfRange
        }
        var result = ExerciseDragEndResult.NoOp
        val finalTargetPart = exerciseDropTargetPartId
        val finalTargetIdx = exerciseDropTargetIndex
        if (finalTargetPart != null && finalTargetIdx != null) {
            val isCardio = run {
                val srcList = exerciseListFor(session, toNullablePartId(currentPartId))
                val ex = srcList.firstOrNull { it.id == activeExerciseId }
                ex?.isCardio == true || (session.parts.firstOrNull { it.id == currentPartId }?.isCardioPart() == true)
            }
            val targetIsCardio = session.parts.firstOrNull { it.id == toNullablePartId(finalTargetPart) }?.isCardioPart() == true
            if (isCardio == targetIsCardio && !isNoOpExerciseMove(session, currentPartId, activeExerciseId, finalTargetPart, finalTargetIdx)) {
                onMoveExercise(
                    toNullablePartId(currentPartId),
                    activeExerciseId,
                    toNullablePartId(finalTargetPart),
                    finalTargetIdx,
                )
                result = ExerciseDragEndResult.Moved
            }
        }
        resetExerciseDrag()
        return result
    }

    /** Cancel without committing (N2). */
    fun cancelExerciseDrag() {
        resetExerciseDrag()
    }

    private fun isNoOpExerciseMove(
        session: Session,
        currentPartId: String,
        exerciseId: String,
        targetPartId: String,
        targetIndex: Int,
    ): Boolean {
        if (currentPartId != targetPartId) return false
        val sourceList = exerciseListFor(session, toNullablePartId(currentPartId))
        val sourceIndex = sourceList.indexOfFirst { it.id == exerciseId }
        if (sourceIndex < 0) return false
        val groupId = sourceList[sourceIndex].supersetGroupRefOrLegacyId()
        val blockSize = if (groupId != null) {
            sourceList.count { it.supersetGroupRefOrLegacyId() == groupId }.coerceAtLeast(1)
        } else {
            1
        }
        // Same-part: dropping at sourceIndex or sourceIndex+blockSize is a no-op.
        return targetIndex == sourceIndex || targetIndex == sourceIndex + blockSize
    }

    fun resetExerciseDrag() {
        draggingExerciseId = null
        draggingExercisePartId = null
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        exerciseDropOutOfRange = false
        dragStartExerciseRect = null
        dragStartGrabOffset = Offset(24f, 24f)
        dragPointerStartWindow = null
        isExerciseDragging = false
        frozenExerciseBounds = emptyMap()
        frozenPartBounds = emptyMap()
        frozenPartFooterBounds = emptyMap()
        frozenPartContentBounds = emptyMap()
        frozenLooseContentBounds = null
        accumulatedScrollPx = 0f
        lastExerciseDragSession = null
        lastExerciseDragGroupedParts = null
        collapsedPartIdsForDrag = emptySet()
        notifyUiStateChanged()
    }

    fun beginPartDrag(
        partId: String,
        livePartBounds: Map<String, Rect>? = null,
        liveStartRect: Rect? = null,
        pointerStartWindow: Offset? = null,
        groupedParts: List<SessionPart>? = null,
    ): Boolean {
        val startRect = liveStartRect ?: livePartBounds?.get(partId) ?: partBounds[partId]
        if (startRect == null) return false
        draggingPartId = partId
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        if (livePartBounds != null) {
            partBounds.putAll(livePartBounds)
        }
        dragStartPartRect = startRect
        dragPartPointerStartWindow = pointerStartWindow
        frozenPartDragBounds = (livePartBounds ?: partBounds.toMap())
        lastPartDragGroupedParts = groupedParts
        accumulatedScrollPx = 0f
        notifyUiStateChanged()
        return true
    }

    fun updatePartDrag(deltaY: Float, groupedParts: List<SessionPart>) {
        if (draggingPartId == null) return
        draggingPartOffsetY += deltaY
        lastPartDragGroupedParts = groupedParts
        recomputePartDropTarget()
    }

    fun recomputePartDropTarget() {
        val activeId = draggingPartId ?: return
        val groupedParts = lastPartDragGroupedParts ?: return
        val pointerY = when {
            dragPartPointerStartWindow != null -> dragPartPointerStartWindow!!.y + draggingPartOffsetY
            else -> {
                val startRect = dragStartPartRect ?: frozenPartDragBounds[activeId] ?: partBounds[activeId] ?: return
                startRect.center.y + draggingPartOffsetY
            }
        }

        val remaining = groupedParts.filter { it.id != activeId }
        val boundsSource = if (frozenPartDragBounds.isNotEmpty()) frozenPartDragBounds else partBounds
        val ordered = remaining
            .mapNotNull { part -> boundsSource[part.id]?.let { part to it } }
            .sortedBy { it.second.center.y }

        if (ordered.isEmpty()) {
            partDropTargetId = null
            partDropTargetIndex = 0
            notifyUiStateChanged()
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
        notifyUiStateChanged()
    }

    fun endPartDrag(
        groupedParts: List<SessionPart>,
        onMovePart: (partId: String, toIndex: Int) -> Unit,
    ) {
        lastPartDragGroupedParts = groupedParts
        recomputePartDropTarget()
        val activeId = draggingPartId
        val targetIndex = partDropTargetIndex
        if (activeId != null && targetIndex != null) {
            val sourceIndex = groupedParts.indexOfFirst { it.id == activeId }
            // partDropTargetIndex is relative to the remaining list (without the dragged part).
            // Restoring the original order ⇒ targetIndex == sourceIndex.
            val isNoOp = sourceIndex >= 0 && targetIndex == sourceIndex
            if (!isNoOp) {
                onMovePart(activeId, targetIndex)
            }
        }
        resetPartDrag()
    }

    fun cancelPartDrag() {
        resetPartDrag()
    }

    private fun resetPartDrag() {
        draggingPartId = null
        draggingPartOffsetY = 0f
        partDropTargetId = null
        partDropTargetIndex = null
        dragStartPartRect = null
        dragPartPointerStartWindow = null
        frozenPartDragBounds = emptyMap()
        lastPartDragGroupedParts = null
        accumulatedScrollPx = 0f
        notifyUiStateChanged()
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
        if (exerciseDropOutOfRange) return 0f

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

        if (currentPartId != targetPartId) {
            return if (index >= targetIndex) gap else 0f
        }

        val sourceIndex = sourceList.indexOfFirst { it.id == activeId }
        if (sourceIndex < 0 || targetIndex == sourceIndex) return 0f

        return when {
            targetIndex < sourceIndex && index >= targetIndex && index < sourceIndex -> gap
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
        val activeRect = frozenExerciseBounds[activeKey] ?: exerciseBounds[activeKey] ?: return 0f
        return if (index >= targetIndex) activeRect.height else 0f
    }

    fun projectedFooterShiftFor(partId: String, partExerciseCount: Int): Float {
        val activeId = draggingExerciseId ?: return 0f
        val currentPartId = draggingExercisePartId ?: return 0f
        val targetPartId = exerciseDropTargetPartId ?: return 0f
        val targetIndex = exerciseDropTargetIndex ?: return 0f
        if (targetPartId != partId) return 0f
        if (targetIndex < partExerciseCount) return 0f
        val activeKey = "$currentPartId|$activeId"
        val activeRect = frozenExerciseBounds[activeKey] ?: exerciseBounds[activeKey] ?: return 0f
        return activeRect.height
    }

    private data class SupersetAnchor(
        val partId: String,
        val exerciseId: String,
        val blockRect: Rect?,
    )

    private fun resolveSupersetBlockAnchor(
        partId: String,
        exerciseId: String,
        session: Session?,
    ): SupersetAnchor {
        if (session == null) return SupersetAnchor(partId, exerciseId, null)
        val list = exerciseListFor(session, toNullablePartId(partId))
        val exercise = list.firstOrNull { it.id == exerciseId } ?: return SupersetAnchor(partId, exerciseId, null)
        val groupId = exercise.supersetGroupRefOrLegacyId() ?: return SupersetAnchor(partId, exerciseId, null)
        val members = list.filter { it.supersetGroupRefOrLegacyId() == groupId }
        if (members.size < 2) return SupersetAnchor(partId, exerciseId, null)
        val first = members.first()
        val blockRect = exerciseBounds["$partId|${first.id}"]
            ?: unionRects(members.mapNotNull { exerciseBounds["$partId|${it.id}"] })
        return SupersetAnchor(partId, first.id, blockRect)
    }

    private fun collapseSupersetMemberBounds(
        bounds: Map<String, Rect>,
        session: Session?,
    ): Map<String, Rect> {
        if (session == null) return bounds
        val result = bounds.toMutableMap()
        fun collapseList(partKey: String, exercises: List<Exercise>) {
            val byGroup = exercises
                .mapNotNull { ex -> ex.supersetGroupRefOrLegacyId()?.let { it to ex } }
                .groupBy({ it.first }, { it.second })
            byGroup.values.forEach { members ->
                if (members.size < 2) return@forEach
                val first = members.first()
                val firstKey = "$partKey|${first.id}"
                val block = result[firstKey]
                    ?: unionRects(members.mapNotNull { result["$partKey|${it.id}"] })
                    ?: return@forEach
                result[firstKey] = block
                members.drop(1).forEach { m -> result.remove("$partKey|${m.id}") }
            }
        }
        collapseList(LOOSE_PART_ID, session.exercises)
        session.parts.forEach { part -> collapseList(part.id, part.exercises) }
        return result
    }

    private fun isNonFirstSupersetMember(session: Session, partId: String, exerciseId: String): Boolean {
        val list = exerciseListFor(session, toNullablePartId(partId))
        val exercise = list.firstOrNull { it.id == exerciseId } ?: return false
        val groupId = exercise.supersetGroupRefOrLegacyId() ?: return false
        val first = list.firstOrNull { it.supersetGroupRefOrLegacyId() == groupId } ?: return false
        return first.id != exerciseId && list.count { it.supersetGroupRefOrLegacyId() == groupId } >= 2
    }

    companion object {
        const val LOOSE_PART_ID = "__loose__"
        const val SYNTHETIC_LOOSE_HEIGHT_PX = 24f
        const val SECTION_NEUTRAL_MARGIN_PX = 48f

        fun toNullablePartId(partId: String): String? =
            partId.takeUnless { it == LOOSE_PART_ID }

        fun exerciseListFor(session: Session, partId: String?): List<Exercise> =
            if (partId == null) session.exercises
            else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()
    }
}
