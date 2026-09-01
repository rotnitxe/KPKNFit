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

/** Whether a drag keeps a whole superset together or moves one member. */
enum class ExerciseDragScope {
    BLOCK,
    INDIVIDUAL,
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
    val exerciseDropTargetGroupId: String? = null,
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

    /**
     * The complete visual projection for the current exercise drag.  Keeping
     * this separate from the mutable bounds maps is important: bounds describe
     * the layout before the gesture, while the projection describes only what
     * should be painted for the current pointer position.
     */
    private data class ExerciseDragProjection(
        val shiftByKey: Map<String, Float> = emptyMap(),
        val footerShiftByPart: Map<String, Float> = emptyMap(),
    )

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
    var exerciseDropTargetGroupId by mutableStateOf<String?>(null)
    var dragStartExerciseRect by mutableStateOf<Rect?>(null)
    /** @deprecated Prefer [dragPointerStartWindow]; kept for legacy grab-offset callers. */
    var dragStartGrabOffset by mutableStateOf(Offset(24f, 24f))
    /** Window-space pointer position at drag start (eliminates handle-relative bias). */
    var dragPointerStartWindow by mutableStateOf<Offset?>(null)
    /** True when the pointer is outside any valid section by more than [SECTION_NEUTRAL_MARGIN_PX]. */
    var exerciseDropOutOfRange by mutableStateOf(false)

    var isExerciseDragging by mutableStateOf(false)
    var draggingExerciseScope by mutableStateOf(ExerciseDragScope.BLOCK)
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
    private var exerciseProjection = ExerciseDragProjection()

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
        exerciseDropTargetGroupId = exerciseDropTargetGroupId,
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

    /** Drop cached geometry after session mutations while no drag is active. */
    fun invalidateLayoutCachesIfIdle() {
        if (isExerciseDragging || draggingPartId != null) return
        clearBounds()
    }

    /**
     * Merge visible LazyColumn bounds at drag start and purge stale children for
     * partially visible groups (e.g. footer left at an old Y after prior drops).
     */
    private fun mergeLiveBoundsForExerciseDrag(liveBounds: Map<String, Rect>) {
        val refreshedPartIds = mutableSetOf<String>()
        val refreshedExerciseKeys = mutableSetOf<String>()
        liveBounds.forEach { (k, r) ->
            when {
                k.startsWith("header|") -> {
                    val pid = k.removePrefix("header|")
                    partBounds[pid] = r
                    refreshedPartIds += pid
                }
                k.startsWith("footer|") -> {
                    val pid = k.removePrefix("footer|")
                    partFooterBounds[pid] = r
                    refreshedPartIds += pid
                }
                k.startsWith("loose_container|") -> {
                    if (looseContentBounds == null ||
                        exerciseBounds.keys.none { it.startsWith("$LOOSE_PART_ID|") }
                    ) {
                        looseContentBounds = r
                    }
                }
                else -> {
                    exerciseBounds[k] = r
                    refreshedPartIds += k.substringBefore("|")
                    refreshedExerciseKeys += k
                }
            }
        }
        refreshedPartIds.forEach { pid ->
            partContentBounds.remove(pid)
            if (pid == LOOSE_PART_ID) return@forEach
            val visibleExerciseKeys = refreshedExerciseKeys.filter { it.startsWith("$pid|") }.toSet()
            exerciseBounds.keys.removeAll { key ->
                key.startsWith("$pid|") && key !in visibleExerciseKeys
            }
            if (!liveBounds.containsKey("footer|$pid")) {
                partFooterBounds.remove(pid)
            }
        }
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
        dragScope: ExerciseDragScope = ExerciseDragScope.BLOCK,
    ): Boolean {
        if (liveBounds != null) {
            mergeLiveBoundsForExerciseDrag(liveBounds)
        }

        // Header drags keep the superset together; member handles can opt into
        // an individual move so a member can leave or join another group.
        val resolved = if (dragScope == ExerciseDragScope.INDIVIDUAL) {
            SupersetAnchor(
                partId = partId,
                exerciseId = exerciseId,
                blockRect = exerciseBounds["$partId|$exerciseId"]
                    ?: liveBounds?.get("$partId|$exerciseId"),
            )
        } else {
            resolveSupersetBlockAnchor(partId, exerciseId, session)
        }
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
        exerciseDropTargetGroupId = null
        exerciseDropOutOfRange = false
        dragStartGrabOffset = grabOffset
        dragPointerStartWindow = pointerStartWindow
        dragStartExerciseRect = blockRect
        isExerciseDragging = true
        draggingExerciseScope = dragScope
        accumulatedScrollPx = 0f
        collapsedPartIdsForDrag = collapsedPartIds
        lastExerciseDragSession = session
        lastExerciseDragGroupedParts = session?.parts?.filterNot { it.isUncategorizedPart() }

        frozenExerciseBounds = if (dragScope == ExerciseDragScope.INDIVIDUAL) {
            exerciseBounds.toMap()
        } else {
            collapseSupersetMemberBounds(exerciseBounds.toMap(), session)
        }
        frozenPartBounds = partBounds.toMap()
        frozenPartFooterBounds = partFooterBounds.toMap()

        rebuildFrozenZones(session)
        exerciseProjection = ExerciseDragProjection()
        notifyUiStateChanged()
        return true
    }

    /**
     * Desplaza Rects congelados inverso al scroll. Las claves visibles de
     * [visibleGeometry] no se desplazan: [refreshVisibleGeometryDuringDrag]
     * las pisa con LayoutInfo, que es la autoridad del viewport.
     */
    fun applyScrollDelta(deltaPx: Float, visibleGeometry: Map<String, Rect> = emptyMap()) {
        if (!isExerciseDragging && draggingPartId == null) return
        if (deltaPx == 0f) {
            if (visibleGeometry.isNotEmpty()) refreshVisibleGeometryDuringDrag(visibleGeometry)
            return
        }
        accumulatedScrollPx += deltaPx
        val skip = parseVisibleSkipKeys(visibleGeometry)
        fun Rect.shifted(): Rect = Rect(left, top - deltaPx, right, bottom - deltaPx)
        fun Map<String, Rect>.shiftedSkipping(skipKeys: Set<String>): Map<String, Rect> =
            mapValues { (k, r) -> if (k in skipKeys) r else r.shifted() }

        frozenExerciseBounds = frozenExerciseBounds.shiftedSkipping(skip.exercises)
        frozenPartBounds = frozenPartBounds.shiftedSkipping(skip.parts)
        frozenPartFooterBounds = frozenPartFooterBounds.shiftedSkipping(skip.footers)
        frozenPartContentBounds = frozenPartContentBounds.shiftedSkipping(skip.parts)
        frozenLooseContentBounds = if (skip.looseContainer) frozenLooseContentBounds else frozenLooseContentBounds?.shifted()
        frozenPartDragBounds = frozenPartDragBounds.shiftedSkipping(skip.parts)
        if (partBounds.isNotEmpty()) {
            val shiftedParts = partBounds.toMap().shiftedSkipping(skip.parts)
            partBounds.clear()
            partBounds.putAll(shiftedParts)
        }
        if (partFooterBounds.isNotEmpty()) {
            val shiftedFooters = partFooterBounds.toMap().shiftedSkipping(skip.footers)
            partFooterBounds.clear()
            partFooterBounds.putAll(shiftedFooters)
        }
        if (exerciseBounds.isNotEmpty()) {
            val shiftedExercises = exerciseBounds.toMap().shiftedSkipping(skip.exercises)
            exerciseBounds.clear()
            exerciseBounds.putAll(shiftedExercises)
        }
        if (partContentBounds.isNotEmpty()) {
            val shiftedPartContents = partContentBounds.toMap().shiftedSkipping(skip.parts)
            partContentBounds.clear()
            partContentBounds.putAll(shiftedPartContents)
        }
        if (!skip.looseContainer) {
            looseContentBounds = looseContentBounds?.shifted()
        }
        if (visibleGeometry.isNotEmpty()) {
            refreshVisibleGeometryDuringDrag(visibleGeometry)
        }
    }

    /**
     * Overwrite frozen geometry for rows currently reported by LazyColumn.
     * Off-screen rows keep their last scroll-compensated rect.
     */
    fun refreshVisibleGeometryDuringDrag(visibleGeometry: Map<String, Rect>) {
        if ((!isExerciseDragging && draggingPartId == null) || visibleGeometry.isEmpty()) return
        val headers = mutableMapOf<String, Rect>()
        val footers = mutableMapOf<String, Rect>()
        val exercises = mutableMapOf<String, Rect>()
        visibleGeometry.forEach { (k, r) ->
            when {
                k.startsWith("header|") -> headers[k.removePrefix("header|")] = r
                k.startsWith("footer|") -> footers[k.removePrefix("footer|")] = r
                k.startsWith("loose_container|") -> Unit
                else -> exercises[k] = r
            }
        }
        if (isExerciseDragging) {
            if (headers.isNotEmpty()) frozenPartBounds = frozenPartBounds + headers
            if (footers.isNotEmpty()) frozenPartFooterBounds = frozenPartFooterBounds + footers
            if (exercises.isNotEmpty()) {
                val incoming = if (draggingExerciseScope == ExerciseDragScope.INDIVIDUAL) {
                    exercises
                } else {
                    collapseSupersetMemberBounds(exercises, lastExerciseDragSession)
                }
                frozenExerciseBounds = frozenExerciseBounds + incoming
            }
            dropStaleFooters(visibleExercises = exercises, liveFooters = footers)
            rebuildFrozenZones(lastExerciseDragSession)
        }
        if (draggingPartId != null && headers.isNotEmpty()) {
            frozenPartDragBounds = frozenPartDragBounds + headers
        }
    }

    /**
     * A footer cached above the lowest visible exercise of the same group is
     * leftover from before the group grew. Drop it so the zone can extend.
     */
    private fun dropStaleFooters(
        visibleExercises: Map<String, Rect>,
        liveFooters: Map<String, Rect>,
    ) {
        val partIds = visibleExercises.keys
            .map { it.substringBefore("|") }
            .filter { it != LOOSE_PART_ID }
            .toSet()
        var next = frozenPartFooterBounds
        var changed = false
        partIds.forEach { pid ->
            if (pid in liveFooters) return@forEach
            val footer = next[pid] ?: return@forEach
            val lowestVisible = visibleExercises
                .filterKeys { it.startsWith("$pid|") }
                .values
                .maxOfOrNull { it.bottom } ?: return@forEach
            if (footer.bottom <= lowestVisible + 1f) {
                next = next - pid
                changed = true
            }
        }
        if (changed) frozenPartFooterBounds = next
    }

    private data class VisibleSkipKeys(
        val exercises: Set<String>,
        val parts: Set<String>,
        val footers: Set<String>,
        val looseContainer: Boolean,
    )

    private fun parseVisibleSkipKeys(visibleGeometry: Map<String, Rect>): VisibleSkipKeys {
        val exercises = mutableSetOf<String>()
        val parts = mutableSetOf<String>()
        val footers = mutableSetOf<String>()
        var looseContainer = false
        visibleGeometry.keys.forEach { k ->
            when {
                k.startsWith("header|") -> parts += k.removePrefix("header|")
                k.startsWith("footer|") -> {
                    val pid = k.removePrefix("footer|")
                    footers += pid
                    parts += pid
                }
                k.startsWith("loose_container|") -> looseContainer = true
                else -> {
                    exercises += k
                    val pid = k.substringBefore("|")
                    if (pid != LOOSE_PART_ID) parts += pid
                }
            }
        }
        return VisibleSkipKeys(exercises, parts, footers, looseContainer)
    }

    /**
     * Registers live bounds during drag after subtracting the projected shift feedback
     * so frozen midpoints are not polluted by the shift graphicsLayer (N5).
     */
    fun registerExerciseBoundsDuringDrag(key: String, rect: Rect, shiftY: Float = 0f) {
        // The frozen snapshot is the only source used for hit testing and
        // projection while a drag is active.  A translated child can report a
        // different window rect on every animation frame; accepting it here
        // makes the target jump underneath the finger.  Keep the live map for
        // the next gesture, and seed only genuinely new/off-screen keys.
        val corrected = if (shiftY != 0f) {
            Rect(rect.left, rect.top - shiftY, rect.right, rect.bottom - shiftY)
        } else rect
        if (!isExerciseDragging) {
            exerciseBounds[key] = corrected
            return
        }
        exerciseBounds[key] = corrected
        val partId = key.substringBefore("|")
        val exerciseId = key.substringAfter("|")
        val session = lastExerciseDragSession
        if (key !in frozenExerciseBounds &&
            (session == null || !isNonFirstSupersetMember(session, partId, exerciseId))
        ) {
            frozenExerciseBounds = frozenExerciseBounds + (key to corrected)
            rebuildFrozenZones(session)
        }
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

    /**
     * Consecutive sections share a midpoint boundary so the Y axis between the
     * first top and last bottom is a partition (no dead strips, no overlap).
     */
    private fun makeDisjointByMidpoints(sorted: List<SectionZone>): List<SectionZone> {
        if (sorted.size <= 1) return sorted
        val result = sorted.toMutableList()
        for (i in 0 until result.size - 1) {
            val current = result[i]
            val next = result[i + 1]
            val mid = (current.bounds.bottom + next.bounds.top) / 2f
            result[i] = current.copy(
                bounds = Rect(current.bounds.left, current.bounds.top, current.bounds.right, mid),
            )
            result[i + 1] = next.copy(
                bounds = Rect(next.bounds.left, mid, next.bounds.right, next.bounds.bottom),
            )
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
            val draggedIds = if (draggingExerciseScope == ExerciseDragScope.BLOCK && draggedGroupId != null) {
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
                // Use the real slot midlines.  The previous implementation
                // derived shifts from one arbitrary card height, so an
                // expanded card could move the indicator several slots at
                // once.  The insertion boundaries below are monotonic and
                // every visible slot participates in the decision.
                val first = candidateEntries.first()
                val firstBoundary = first.value.top + first.value.height / 2f
                val last = candidateEntries.last()
                val lastBoundary = last.value.top + last.value.height / 2f

                if (pointerY < firstBoundary) {
                    val firstExId = candidateEntries.first().key.substringAfter("|")
                    val idx = targetList.indexOfFirst { it.id == firstExId }.coerceAtLeast(0)
                    newDropTargetKey = candidateEntries.first().key
                    newDropTargetIndex = idx
                } else if (pointerY >= lastBoundary) {
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
                        val currBoundary = currentEntry.value.top + currentEntry.value.height / 2f
                        val nextBoundary = nextEntry.value.top + nextEntry.value.height / 2f
                        if (pointerY >= currBoundary && pointerY < nextBoundary) {
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

            val rawTargetIndex = newDropTargetIndex
            val stableTargetIndex = stabilizeExerciseDropIndex(
                targetPartId = targetPartId,
                targetList = targetList,
                candidateEntries = candidateEntries,
                pointerY = pointer.y,
                rawTargetIndex = rawTargetIndex,
            )
            if (stableTargetIndex != rawTargetIndex) {
                newDropTargetIndex = stableTargetIndex
                newDropTargetKey = targetList.getOrNull(stableTargetIndex ?: -1)?.id?.let { id ->
                    frozenExerciseBounds.keys.firstOrNull { key ->
                        key == "$targetPartId|$id"
                    }
                }
            }
        }

        exerciseDropTargetGroupId = if (draggingExerciseScope == ExerciseDragScope.INDIVIDUAL && targetPartId != null) {
            // Joining a group is intentional only when the finger is over one
            // of its visible member cards. Gaps are an explicit "outside the
            // group" target, so a member can be pulled out even when dropped
            // immediately after the group.
            val targetListForGroup = exerciseListFor(session, toNullablePartId(targetPartId))
            frozenExerciseBounds
                .filterKeys { it.startsWith("$targetPartId|") }
                .entries
                .firstOrNull { (_, bounds) -> pointer.y >= bounds.top && pointer.y <= bounds.bottom }
                ?.key
                ?.substringAfter("|")
                ?.let { hitExerciseId ->
                    targetListForGroup.firstOrNull { it.id == hitExerciseId }?.supersetGroupRefOrLegacyId()
                }
        } else {
            null
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
        rebuildExerciseProjection(
            session = session,
            sourcePartId = currentPartId,
            targetPartId = targetPartId,
            targetIndex = newDropTargetIndex,
            draggedIds = draggedIdsFor(session, currentPartId, activeExerciseId),
        )
        notifyUiStateChanged()
    }

    /**
     * Keep the insertion slot stable while the pointer is grazing a card's
     * midpoint.  The tolerance is deliberately small: the pointer still has
     * to cross the next real boundary before the following sibling moves.
     */
    private fun stabilizeExerciseDropIndex(
        targetPartId: String,
        targetList: List<Exercise>,
        candidateEntries: List<Map.Entry<String, Rect>>,
        pointerY: Float,
        rawTargetIndex: Int?,
    ): Int? {
        val previousPartId = exerciseDropTargetPartId
        val previousIndex = exerciseDropTargetIndex
        if (previousPartId != targetPartId || previousIndex == null || rawTargetIndex == null || previousIndex == rawTargetIndex) {
            return rawTargetIndex
        }
        val boundaryIndex = if (rawTargetIndex > previousIndex) previousIndex else previousIndex - 1
        val boundaryExerciseId = targetList.getOrNull(boundaryIndex)?.id ?: return rawTargetIndex
        val boundary = candidateEntries
            .firstOrNull { it.key == "$targetPartId|$boundaryExerciseId" }
            ?.value
            ?.center
            ?.y
            ?: return rawTargetIndex
        val crossed = if (rawTargetIndex > previousIndex) {
            pointerY >= boundary + EXERCISE_DROP_HYSTERESIS_PX
        } else {
            pointerY < boundary - EXERCISE_DROP_HYSTERESIS_PX
        }
        return if (crossed) rawTargetIndex else previousIndex
    }

    private fun draggedIdsFor(session: Session, sourcePartId: String, activeExerciseId: String): Set<String> {
        val sourceList = exerciseListFor(session, toNullablePartId(sourcePartId))
        val groupId = sourceList.firstOrNull { it.id == activeExerciseId }?.supersetGroupRefOrLegacyId()
        return if (draggingExerciseScope == ExerciseDragScope.BLOCK && groupId != null) {
            sourceList.filter { it.supersetGroupRefOrLegacyId() == groupId }.map { it.id }.toSet()
        } else setOf(activeExerciseId)
    }

    private fun visibleEntries(partId: String, draggedIds: Set<String>): List<Pair<String, Rect>> =
        frozenExerciseBounds
            .filterKeys { it.startsWith("$partId|") }
            .filterKeys { it.substringAfter("|") !in draggedIds }
            .entries
            .sortedBy { it.value.top }
            .map { it.key to it.value }

    private fun displacementFor(entries: List<Pair<String, Rect>>, draggedHeight: Float): Float {
        if (entries.isEmpty()) return draggedHeight + DEFAULT_DRAG_GAP_PX
        val gaps = entries.zipWithNext { current, next ->
            (next.second.top - current.second.bottom).coerceAtLeast(0f)
        }.filter { it > 0f }
        val gap = gaps.average().toFloat().takeIf { it.isFinite() } ?: DEFAULT_DRAG_GAP_PX
        return draggedHeight + gap
    }

    private fun rebuildExerciseProjection(
        session: Session,
        sourcePartId: String,
        targetPartId: String?,
        targetIndex: Int?,
        draggedIds: Set<String>,
    ) {
        if (targetPartId == null || targetIndex == null || exerciseDropOutOfRange) {
            exerciseProjection = ExerciseDragProjection()
            return
        }
        val sourceList = exerciseListFor(session, toNullablePartId(sourcePartId))
        val sourceIndex = sourceList.indexOfFirst { it.id == draggingExerciseId }
        if (sourceIndex < 0) {
            exerciseProjection = ExerciseDragProjection()
            return
        }
        val draggedHeight = dragStartExerciseRect?.height
            ?: frozenExerciseBounds["$sourcePartId|${draggingExerciseId}"]?.height
            ?: DEFAULT_DRAG_ITEM_HEIGHT_PX
        val sourceEntries = visibleEntries(sourcePartId, draggedIds)
        val targetEntries = visibleEntries(targetPartId, draggedIds)
        val sourceDisplacement = displacementFor(sourceEntries, draggedHeight)
        val targetDisplacement = displacementFor(targetEntries, draggedHeight)
        val shifts = mutableMapOf<String, Float>()

        fun rawIndex(key: String): Int {
            val id = key.substringAfter("|")
            return exerciseListFor(session, toNullablePartId(key.substringBefore("|")))
                .indexOfFirst { it.id == id }
        }

        if (sourcePartId == targetPartId) {
            when {
                targetIndex < sourceIndex -> sourceEntries.forEach { (key, _) ->
                    val index = rawIndex(key)
                    if (index >= targetIndex && index < sourceIndex) shifts[key] = sourceDisplacement
                }
                targetIndex > sourceIndex -> sourceEntries.forEach { (key, _) ->
                    val index = rawIndex(key)
                    if (index > sourceIndex && index < targetIndex) shifts[key] = -sourceDisplacement
                }
            }
        } else {
            sourceEntries.forEach { (key, _) ->
                if (rawIndex(key) > sourceIndex) shifts[key] = -sourceDisplacement
            }
            targetEntries.forEach { (key, _) ->
                if (rawIndex(key) >= targetIndex) shifts[key] = targetDisplacement
            }
        }

        val footerShifts = mutableMapOf<String, Float>()
        if (sourcePartId != targetPartId) footerShifts[sourcePartId] = -sourceDisplacement
        if (targetPartId != sourcePartId) {
            val targetSize = exerciseListFor(session, toNullablePartId(targetPartId)).size
            if (targetIndex >= targetSize) footerShifts[targetPartId] = targetDisplacement
        }
        exerciseProjection = ExerciseDragProjection(shifts, footerShifts)
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
        if (draggingExerciseScope == ExerciseDragScope.INDIVIDUAL && groupId != exerciseDropTargetGroupId) {
            // A membership change is a real move even if the insertion index
            // happens to match the source index.
            return false
        }
        val blockSize = if (draggingExerciseScope == ExerciseDragScope.BLOCK && groupId != null) {
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
        exerciseDropTargetGroupId = null
        exerciseDropOutOfRange = false
        dragStartExerciseRect = null
        dragStartGrabOffset = Offset(24f, 24f)
        dragPointerStartWindow = null
        isExerciseDragging = false
        draggingExerciseScope = ExerciseDragScope.BLOCK
        frozenExerciseBounds = emptyMap()
        frozenPartBounds = emptyMap()
        frozenPartFooterBounds = emptyMap()
        frozenPartContentBounds = emptyMap()
        frozenLooseContentBounds = null
        exerciseProjection = ExerciseDragProjection()
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
    ): Float = exerciseProjection.shiftByKey["$partId|$exerciseId"] ?: 0f

    fun projectedShiftFor(partId: String, index: Int, exerciseId: String): Float {
        return exerciseProjection.shiftByKey["$partId|$exerciseId"] ?: 0f
    }

    fun projectedFooterShiftFor(partId: String, partExerciseCount: Int): Float {
        return exerciseProjection.footerShiftByPart[partId] ?: 0f
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
        const val EXERCISE_DROP_HYSTERESIS_PX = 6f
        const val DEFAULT_DRAG_GAP_PX = 8f
        const val DEFAULT_DRAG_ITEM_HEIGHT_PX = 88f

        fun toNullablePartId(partId: String): String? =
            partId.takeUnless { it == LOOSE_PART_ID }

        fun exerciseListFor(session: Session, partId: String?): List<Exercise> =
            if (partId == null) session.exercises
            else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()
    }
}
