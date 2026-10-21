package com.example.kpkn.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.HeaderSheet
import com.example.kpkn.screens.workout.components.HeaderSheetAction
import com.example.kpkn.screens.workout.components.RoadmapPendingAction
import com.example.kpkn.screens.workout.components.LiveUndoPillHost
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs

internal val LiveSessionLineHeight = 112.dp

/** A gap keeps the original indices: compression never renumbers a pager destination. */
internal data class LiveSessionLineRange(val indices: IntRange, val compressed: Boolean)

internal fun liveSessionLineRanges(count: Int, active: Int, preparation: Set<Int>): List<LiveSessionLineRange> {
    if (count <= 9) return (0 until count).map { LiveSessionLineRange(it..it, false) }
    val keep = preparation + setOf(0, count - 1) + ((active - 2)..(active + 2)).filter { it in 0 until count }
    return buildList {
        var index = 0
        while (index < count) {
            val start = index
            if (index in keep) add(LiveSessionLineRange(index..index++, false))
            else {
                while (index + 1 < count && index + 1 !in keep) index++
                add(LiveSessionLineRange(start..index++, true))
            }
        }
    }
}

private data class FoldedLiveExercise(val key: String, val members: List<Exercise>, val ordinal: Int) {
    val title get() = members.joinToString(" + ", transform = ::displayWorkoutExerciseName)
}

/** V1: one island owns the route, preparation, series, folded exercises and the recording action. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveSessionLine(
    exercises: List<Exercise>,
    currentExerciseId: String?,
    snapshot: LiveSetStepperSnapshot?,
    onOpenRoute: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onSelectExercise: (String) -> Unit,
    onOpenExerciseOptions: (String) -> Unit,
    onRecord: () -> Unit,
    recordVisible: Boolean,
    recordEnabled: Boolean,
    isUpdateMode: Boolean = false,
    onAddSet: (() -> Unit)? = null,
    onLongPressPage: ((Int) -> Unit)? = null,
    onNavigateAdjacentExercise: ((Boolean) -> Unit)? = null,
    supersetGroups: List<SupersetGroup> = emptyList(),
    onSelectSuperset: (String) -> Unit = {},
    pendingActions: List<RoadmapPendingAction> = emptyList(),
    undoStack: List<GodModeUndoSnapshot> = emptyList(),
    completedSets: Map<String, CompletedSet> = emptyMap(),
    onUndo: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ink = WizardColors.text
    val reduced = wizardReducedMotion()
    val clouds = snapshot?.let { liveSessionLineSteps(it, it.activePageIndex) }.orEmpty()
    val selected = clouds.indexOfFirst { it.active }.coerceAtLeast(0)
    val preparation = clouds.indices.filter { clouds[it].preparationSelect != null }.toSet()
    val ranges = liveSessionLineRanges(clouds.size, selected, preparation)
    var showAllSteps by remember { mutableStateOf(false) }
    val groups = remember(exercises, supersetGroups) {
        val memberGroups = buildMap {
            supersetGroups.forEach { group -> group.exerciseOrder.forEach { put(it, group.id) } }
            exercises.forEach { ex -> ex.supersetGroupRefOrLegacyId()?.let { put(ex.id, it) } }
        }
        val emitted = mutableSetOf<String>()
        exercises.mapNotNull { ex ->
            val group = memberGroups[ex.id]
            val key = group ?: ex.id
            if (!emitted.add(key)) null else FoldedLiveExercise(key,
                if (group == null) listOf(ex) else {
                    val members = exercises.filter { memberGroups[it.id] == group }
                    val order = supersetGroups.firstOrNull { it.id == group }?.exerciseOrder.orEmpty()
                    members.sortedBy { order.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                }, emitted.size)
        }
    }
    val currentGroup = groups.indexOfFirst { group -> group.members.any { it.id == currentExerciseId } }
    val before = groups.take(currentGroup.coerceAtLeast(0))
    val after = if (currentGroup >= 0) groups.drop(currentGroup + 1) else groups
    val list = rememberLazyListState()
    val density = LocalDensity.current
    var overscroll by remember { mutableFloatStateOf(0f) }
    val adjacent by rememberUpdatedState(onNavigateAdjacentExercise)
    val gesture = remember(density) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) overscroll += available.x
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val forward = overscroll < 0
                val atEdge = if (forward) !list.canScrollForward else !list.canScrollBackward
                if (atEdge && abs(overscroll) >= with(density) { 56.dp.toPx() }) adjacent?.invoke(forward)
                overscroll = 0f
                return Velocity.Zero
            }
        }
    }
    val activeRailIndex = before.size + ranges.indexOfFirst { selected in it.indices }.coerceAtLeast(0)
    val railItemCount = before.size + ranges.size + after.size + if (clouds.isEmpty() && currentGroup >= 0) 1 else 0
    val railGesture = if (railItemCount <= 1 && adjacent != null) Modifier.pointerInput(Unit) {
        var drag = 0f
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
            onHorizontalDrag = { _, delta -> drag += delta }, onDragEnd = {
                if (abs(drag) >= 56.dp.toPx()) adjacent?.invoke(drag < 0f)
                drag = 0f
            })
    } else Modifier.nestedScroll(gesture)
    LaunchedEffect(clouds.map { it.key }, activeRailIndex, groups.map { it.key }) {
        if (groups.isNotEmpty()) {
            list.scrollToItem(activeRailIndex)
            val info = list.layoutInfo
            info.visibleItemsInfo.firstOrNull { it.index == activeRailIndex }?.let { item ->
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val delta = item.offset + item.size / 2f - center
                if (abs(delta) > 1f) {
                    if (reduced) list.scroll { scrollBy(delta) } else list.animateScrollBy(delta)
                }
            }
        }
    }
    fun selectCloud(cloud: LiveSessionLineStep) {
        if (cloud.preparationSelect != null) cloud.preparationSelect.invoke()
        else cloud.pageIndex?.let(onSelectPage)
    }
    fun selectGroup(group: FoldedLiveExercise) {
        if (group.members.size > 1) onSelectSuperset(group.key) else onSelectExercise(group.members.first().id)
    }
    Column(modifier.fillMaxWidth().height(LiveSessionLineHeight).testTag("live-session-line")
        .clip(RoundedCornerShape(26.dp)).background(androidx.compose.ui.graphics.Color(0xE6161616))
        .border(1.dp, ink.copy(alpha = .10f), RoundedCornerShape(26.dp)).padding(6.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                .combinedClickable(role = Role.Button, onClick = onOpenRoute,
                    onLongClick = currentExerciseId?.let { id -> { onOpenExerciseOptions(id) } })
                .semantics { contentDescription = "Abrir ruta de la sesión" }.testTag("live-session-route"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(KpknLineIcons.Route, null, Modifier.size(20.dp), tint = ink)
                Column(Modifier.weight(1f)) {
                    Text(groups.getOrNull(currentGroup)?.title ?: "Ruta de la sesión", style = KpknType.chip,
                        color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(buildString {
                        snapshot?.let { append("${it.completedCount}/${it.totalCount} · ") }
                        if (groups.isEmpty()) append("Sin ejercicios")
                        else append("${(currentGroup + 1).coerceAtLeast(1)} de ${groups.size} bloques")
                    }, style = KpknType.caption.copy(fontSize = 11.sp), color = WizardColors.textMuted, maxLines = 1)
                }
                Icon(KpknLineIcons.ChevronRight, null, Modifier.size(16.dp), tint = ink)
            }
            LiveUndoPillHost(undoStack, completedSets, onUndo, compact = true)
            pendingActions.forEach { pending ->
                HeaderSheetAction(pending.label, pending.onClick, Modifier.width(88.dp), multiline = true)
            }
            if (snapshot?.canAddSet == true && onAddSet != null) {
                SessionLinePill("+", "Añadir serie", false, onAddSet, tag = "live-session-add-set")
            }
        }
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                LazyRow(state = list, userScrollEnabled = railItemCount > 1,
                    modifier = Modifier.fillMaxSize().then(railGesture).testTag("live-session-steps")
                    .drawBehind { drawLine(ink.copy(alpha = .14f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx()) },
                    contentPadding = PaddingValues(horizontal = ((maxWidth - 48.dp) / 2).coerceAtLeast(0.dp)),
                    verticalAlignment = Alignment.CenterVertically) {
                    items(before, key = { "exercise-${it.key}" }) { group ->
                        SessionLinePill("· ${group.ordinal}", "Ir a ${group.title}", false,
                            { selectGroup(group) }, { onOpenExerciseOptions(group.members.first().id) })
                    }
                    items(ranges, key = { "range-${clouds[it.indices.first].key}" }) { range ->
                        if (range.compressed) SessionLinePill("…${range.indices.count()}", "Ver ${range.indices.count()} pasos lejanos", false, { showAllSteps = true })
                        else {
                            val cloud = clouds[range.indices.first]
                            SessionLinePill(cloud.label, cloud.description, cloud.active, { selectCloud(cloud) },
                                cloud.pageIndex?.let { page -> onLongPressPage?.let { open -> { open(page) } } }, cloud)
                        }
                    }
                    if (clouds.isEmpty() && currentGroup >= 0) item(key = "current-exercise") {
                        val group = groups[currentGroup]
                        SessionLinePill("· ${group.ordinal}", "Ir a ${group.title}", true,
                            { selectGroup(group) }, { onOpenExerciseOptions(group.members.first().id) })
                    }
                    items(after, key = { "exercise-${it.key}" }) { group ->
                        SessionLinePill("· ${group.ordinal}", "Ir a ${group.title}", false,
                            { selectGroup(group) }, { onOpenExerciseOptions(group.members.first().id) })
                    }
                }
            }
            if (recordVisible) Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp))
                .background(androidx.compose.ui.graphics.Color(0xFFF49A6E).copy(alpha = if (recordEnabled) 1f else .38f))
                .combinedClickable(enabled = recordEnabled, role = Role.Button, onClick = onRecord)
                .semantics { contentDescription = if (isUpdateMode) "Actualizar serie" else "Registrar serie" }
                .testTag("live-session-record"), contentAlignment = Alignment.Center) {
                Icon(if (isUpdateMode) Icons.Default.Update else KpknLineIcons.Check, null, Modifier.size(25.dp), tint = androidx.compose.ui.graphics.Color.Black)
            }
        }
    }
    if (showAllSteps) HeaderSheet(onDismiss = { showAllSteps = false }) { close ->
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Todos los pasos", style = KpknType.title, color = ink)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                clouds.forEach { cloud ->
                    HeaderSheetAction(cloud.description, { selectCloud(cloud); close() }, Modifier.fillMaxWidth())
                }
            }
            HeaderSheetAction("Volver", close, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionLinePill(label: String, description: String, active: Boolean, onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null, cloud: LiveSessionLineStep? = null, tag: String? = null) {
    val shape = RoundedCornerShape(18.dp)
    val ink = when {
        active -> androidx.compose.ui.graphics.Color.Black
        cloud?.completed == true -> SymbolPalette.ok
        else -> WizardColors.text
    }
    Box(Modifier.size(48.dp, 56.dp).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
        .semantics(mergeDescendants = true) {
            contentDescription = description
            selected = active
            stateDescription = buildList {
                if (active) add("Seleccionada")
                if (cloud?.completed == true) add("Completada")
                if (cloud?.skipped == true) add("Omitida")
                cloud?.preparationProgress?.takeIf { !cloud.completed }?.let { add("${(it.coerceIn(0f, 1f) * 100).toInt()} por ciento") }
                if (cloud?.volumeReplaced == true) add("Técnica de volumen reemplazado")
            }.joinToString(", ")
        }.testTag(tag ?: "live-session-step-${cloud?.key ?: description}"), contentAlignment = Alignment.Center) {
        Row(Modifier.size(44.dp, if (active) 44.dp else 34.dp).clip(shape)
            .background(if (active) WizardColors.text else androidx.compose.ui.graphics.Color(0xFF202020))
            .border(1.dp, if (cloud?.volumeReplaced == true) VolumeReplacedStepperTint else WizardColors.text.copy(alpha = .10f), shape),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            when (cloud?.description) {
                "Movilidad" -> Icon(Icons.Default.AccessibilityNew, null, Modifier.size(20.dp), tint = ink)
                "Aproximación" -> Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = ink)
                else -> Text((if (cloud?.completed == true) "✓" else "") + label,
                    style = KpknType.chip.copy(fontSize = 11.sp, lineHeight = 14.sp), color = ink.copy(alpha = if (cloud?.skipped == true) .4f else 1f), maxLines = 1)
            }
        }
    }
}
