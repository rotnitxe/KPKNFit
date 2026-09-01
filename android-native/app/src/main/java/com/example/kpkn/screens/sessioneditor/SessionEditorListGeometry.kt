package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId

/**
 * One visible LazyColumn row, in list coordinates. [offset]/[size] match
 * [androidx.compose.foundation.lazy.LazyListItemInfo] so tests can feed the
 * same mapping the editor uses at runtime.
 */
internal data class SessionEditorVisibleItem(
    val key: String,
    val offset: Int,
    val size: Int,
)

/**
 * Maps LazyColumn item keys to drag-controller keys in window space.
 *
 * LayoutInfo is the hit-test authority: item offsets are not polluted by
 * [androidx.compose.ui.graphics.graphicsLayer] projection shifts on cards.
 */
internal fun collectSessionEditorListGeometry(
    visibleItems: List<SessionEditorVisibleItem>,
    windowBounds: Rect,
    session: Session?,
): Map<String, Rect> {
    val map = mutableMapOf<String, Rect>()
    val looseId = SessionEditorDragController.LOOSE_PART_ID
    visibleItems.forEach { item ->
        val top = windowBounds.top + item.offset
        val bottom = top + item.size
        val rect = Rect(windowBounds.left, top, windowBounds.right, bottom)
        when {
            item.key.startsWith("loose-exercise-") -> {
                val exId = item.key.removePrefix("loose-exercise-")
                map["$looseId|$exId"] = rect
            }
            item.key.startsWith("loose-superset-") -> {
                val groupId = item.key.removePrefix("loose-superset-")
                val members = session?.exercises
                    ?.filter { it.supersetGroupRefOrLegacyId() == groupId }
                    .orEmpty()
                members.firstOrNull()?.let { member ->
                    map["$looseId|${member.id}"] = rect
                }
            }
            item.key.startsWith("part-") && item.key.contains("-exercise-") -> {
                val rest = item.key.removePrefix("part-")
                val pid = rest.substringBefore("-exercise-")
                val exId = rest.substringAfter("-exercise-")
                map["$pid|$exId"] = rect
            }
            item.key.startsWith("part-") && item.key.contains("-superset-") -> {
                val rest = item.key.removePrefix("part-")
                val pid = rest.substringBefore("-superset-")
                val groupId = rest.substringAfter("-superset-")
                val part = session?.parts?.firstOrNull { it.id == pid }
                val members = part?.exercises
                    ?.filter { it.supersetGroupRefOrLegacyId() == groupId }
                    .orEmpty()
                members.firstOrNull()?.let { member ->
                    map["$pid|${member.id}"] = rect
                }
            }
            item.key.startsWith("part-header-") -> {
                val pid = item.key.removePrefix("part-header-")
                map["header|$pid"] = rect
            }
            item.key.startsWith("part-add-") -> {
                val pid = item.key.removePrefix("part-add-")
                map["footer|$pid"] = rect
            }
            item.key == "strength-add-actions" -> {
                val empty = session?.exercises.isNullOrEmpty() &&
                    session?.parts?.none { !it.isUncategorizedPart() } == true
                if (empty) map["loose_container|$looseId"] = rect
            }
        }
    }
    return map
}
