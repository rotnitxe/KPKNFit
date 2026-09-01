package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.screens.sessioneditor.contentOn
import com.example.kpkn.screens.sessioneditor.isEditorUncategorized
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState

private enum class RoadmapCardScale { Full, Mini }

enum class RoadmapMode {
    COMPACT,
    EXPANDED
}

/**
 * WorkoutRoadmapBar / UnifiedExerciseCarousel component.
 * Acts as the training roadmap cockpit with compact vs expanded modes,
 * auto-resolves muscular groups, handles completion states, and blocks
 * expansion when the virtual keyboard is visible.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutRoadmapBar(
    exercises: List<Exercise>,
    parts: List<SessionPart> = emptyList(),
    supersetGroups: List<SupersetGroup> = emptyList(),
    currentIdx: Int,
    currentSetIdx: Int = 0,
    currentSide: String? = null,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String> = emptySet(),
    onSelect: (Int) -> Unit,
    onSelectStep: (String) -> Unit = {},
    onSelectGroup: (String) -> Unit = {},
    onOpenContext: (String) -> Unit = {},
    onOpenMemberContext: (String) -> Unit = onOpenContext,
    enableLongPress: Boolean = true,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    hazeState: HazeState? = null,
    mode: RoadmapMode = RoadmapMode.COMPACT,
    onModeChange: (RoadmapMode) -> Unit = {},
    milestones: List<SessionMilestone> = emptyList(),
    liveEnergySummary: SessionEnergySummary = SessionEnergySummary(),
    sessionNotes: String = "",
    sessionSavedNotes: List<com.example.kpkn.data.models.SessionSavedNote> = emptyList(),
    sessionPhotos: List<String> = emptyList(),
    sessionChecklist: List<SessionChecklistItem> = emptyList(),
    onSessionNotesChange: (String) -> Unit = {},
    onSaveSessionNote: (String) -> Unit = {},
    onAddSessionPhoto: (android.net.Uri) -> Unit = {},
    onRemoveSessionPhoto: (String) -> Unit = {},
    onAddChecklistItem: (String) -> Unit = {},
    onToggleChecklistItem: (String) -> Unit = {},
    onRemoveChecklistItem: (String) -> Unit = {},
    bodyWeight: Double? = null,
    aboveCarousel: (@Composable () -> Unit)? = null,
    godMode: Boolean = false,
    onDeleteExercise: (String) -> Unit = {},
    onSkipExercise: (String) -> Unit = {},
    onAddExercise: () -> Unit = {},
    onReorderExercises: (List<String>) -> Unit = {},
    onCreateSupersetFrom: (List<String>) -> Unit = {},
    onBatchSkip: (List<String>) -> Unit = {},
    onBatchDelete: (List<String>) -> Unit = {},
    onBatchUltraFast: (List<String>, com.example.kpkn.domain.sessionassistant.SeriesTechnique) -> Unit = { _, _ -> },
    onDissolveSuperset: (String) -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    clearSelectionNonce: Int = 0,
    godModeUndoStack: List<GodModeUndoSnapshot> = emptyList(),
    onRevertGodModeAction: (Int) -> Unit = {},
    planAspects: List<com.example.kpkn.screens.workout.SessionPlanAspect> = emptyList(),
    onRevertPlanAspect: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeMode = mode
    val godModeActive = godMode && activeMode == RoadmapMode.EXPANDED
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleteExerciseId by remember { mutableStateOf<String?>(null) }
    var pendingDissolveGroupId by remember { mutableStateOf<String?>(null) }
    var pendingUltraFast by remember { mutableStateOf(false) }
    var draggingEntryIndex by remember { mutableStateOf<Int?>(null) }
    var autoScrollDirection by remember { mutableStateOf(0) }
    LaunchedEffect(godModeActive) {
        if (!godModeActive) {
            selectedIds = emptySet()
            draggingEntryIndex = null
            autoScrollDirection = 0
        }
    }
    val selectionMode = selectedIds.isNotEmpty()
    LaunchedEffect(selectionMode) { onSelectionModeChange(selectionMode) }
    LaunchedEffect(clearSelectionNonce) {
        if (clearSelectionNonce > 0) selectedIds = emptySet()
    }
    var plusSlot by remember { mutableStateOf(godModeActive) }
    LaunchedEffect(godModeActive) {
        if (godModeActive) {
            plusSlot = true
        } else {
            delay(WorkoutUiTokens.GodModeExitMs.toLong())
            if (!godModeActive) plusSlot = false
        }
    }
    val canDeleteAnyExercise = exercises.size > 1
    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 12.dp.toPx() }
    val dragStridePx = with(density) { 96.dp.toPx() }

    val accentByPartId = remember(parts, sessionAccentColor) {
        parts.associate { part ->
            part.id to roadmapAccentForPart(part, sessionAccentColor)
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0)
    )
    val chromeScale = LocalViewportAdapt.current.uniformScale
    val carouselCardHeight = WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale)
    val compactCockpitHeight = WorkoutUiTokens.liveCockpitCompactHeight(chromeScale)
    val stepperSlotHeight = WorkoutUiTokens.liveCockpitStepperHeight(chromeScale)

    // The group table is authoritative for sessions loaded from older JSON:
    // some of those records have a valid SupersetGroup.exerciseOrder but one
    // or more member Exercise objects still lack supersetGroupRef.  Deriving
    // the roadmap only from the member flag silently rendered two normal
    // cards and made the outer long-press menu impossible to reach.
    val groupIdByExerciseId = remember(exercises, supersetGroups) {
        buildMap {
            supersetGroups.forEach { group ->
                group.exerciseOrder.forEach { exerciseId -> put(exerciseId, group.id) }
            }
            exercises.forEach { exercise ->
                exercise.supersetGroupRefOrLegacyId()?.let { groupId -> put(exercise.id, groupId) }
            }
        }
    }
    val roadmapGroups = remember(exercises, supersetGroups, groupIdByExerciseId) {
        val emitted = mutableSetOf<String>()
        exercises.mapNotNull { exercise ->
            val groupId = groupIdByExerciseId[exercise.id]
            when {
                groupId == null -> ExerciseRoadmapGroup(null, listOf(exercise))
                emitted.add(groupId) -> ExerciseRoadmapGroup(
                    groupId = groupId,
                    exercises = supersetGroups
                        .firstOrNull { it.id == groupId }
                        ?.exerciseOrder
                        ?.mapNotNull { memberId -> exercises.firstOrNull { it.id == memberId } }
                        ?.plus(exercises.filter { groupIdByExerciseId[it.id] == groupId })
                        ?.distinctBy { it.id }
                        ?.takeIf { it.size >= 2 }
                        ?: exercises.filter { groupIdByExerciseId[it.id] == groupId },
                )
                else -> null
            }
        }
    }

    val firstCardioGroupIndex = remember(roadmapGroups) {
        roadmapGroups.indexOfFirst { group ->
            group.exercises.firstOrNull()?.isCardio == true
        }
    }
    val roadmapEntries = remember(roadmapGroups, firstCardioGroupIndex) {
        buildList<RoadmapEntry> {
            roadmapGroups.forEachIndexed { groupIdx, group ->
                if (groupIdx == firstCardioGroupIndex) add(RoadmapEntry.CardioDivider)
                add(RoadmapEntry.Group(group))
            }
        }
    }

    LaunchedEffect(currentIdx) {
        val currentExerciseId = exercises.getOrNull(currentIdx)?.id ?: return@LaunchedEffect
        val currentEntryIdx = roadmapEntries.indexOfFirst { entry ->
            entry is RoadmapEntry.Group && entry.group.exercises.any { it.id == currentExerciseId }
        }
        if (currentEntryIdx < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val visible = info.visibleItemsInfo.firstOrNull { it.index == currentEntryIdx }
        if (visible != null) {
            val delta = lazyItemDeltaToCenterPx(visible.offset, visible.size, viewport)
            if (kotlin.math.abs(delta) > 8) {
                listState.animateScrollBy(delta.toFloat())
            }
            return@LaunchedEffect
        }
        listState.scrollToItem(currentEntryIdx)
        val after = listState.layoutInfo
        val afterViewport = after.viewportEndOffset - after.viewportStartOffset
        val afterItem = after.visibleItemsInfo.firstOrNull { it.index == currentEntryIdx }
            ?: return@LaunchedEffect
        val delta = lazyItemDeltaToCenterPx(afterItem.offset, afterItem.size, afterViewport)
        if (kotlin.math.abs(delta) > 8) {
            listState.animateScrollBy(delta.toFloat())
        }
    }

    LaunchedEffect(autoScrollDirection, draggingEntryIndex) {
        while (autoScrollDirection != 0 && draggingEntryIndex != null) {
            listState.animateScrollBy(autoScrollDirection * dragStridePx * 0.35f)
            delay(16)
        }
    }

    val supersetOrdinalById = remember(roadmapGroups) {
        roadmapGroups.mapNotNull { group ->
            group.groupId?.takeIf { group.exercises.size > 1 }
        }.distinct().withIndex().associate { (index, groupId) -> groupId to index + 1 }
    }

    val supersetGroupById = remember(supersetGroups) { supersetGroups.associateBy { it.id } }

    val roadmapShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = roadmapShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        val cockpitGlassModifier = Modifier
            .fillMaxWidth()
            .clip(roadmapShape)
            .kpknGlassOrFallback(hazeState, roadmapShape, withBorder = false)
        Box(
            modifier = cockpitGlassModifier
                .clickable(enabled = activeMode == RoadmapMode.COMPACT) {
                    onModeChange(RoadmapMode.EXPANDED)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (activeMode == RoadmapMode.COMPACT) {
                            Modifier.height(compactCockpitHeight).clipToBounds()
                        } else {
                            Modifier.wrapContentHeight()
                        }
                    ),
            ) {
            // Drag handle to toggle Compact vs Expanded mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WorkoutUiTokens.MinTouchTarget)
                    .clickable {
                        onModeChange(if (activeMode == RoadmapMode.COMPACT) RoadmapMode.EXPANDED else RoadmapMode.COMPACT)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Desplegar roadmap",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (activeMode == RoadmapMode.EXPANDED) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = activeMode == RoadmapMode.EXPANDED,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 260),
                ) + fadeIn(animationSpec = tween(durationMillis = 160)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 220),
                ) + fadeOut(animationSpec = tween(durationMillis = 140)),
            ) {
                val totalCompletedCount = WorkoutStepRules.completedRoadmapSlotsForExercises(
                    exercises = exercises,
                    completedSets = completedSets,
                    omittedSetKeys = omittedSetKeys,
                )
                val totalSetsCount = WorkoutStepRules.totalRoadmapSlotsForExercises(
                    exercises = exercises,
                    omittedSetKeys = omittedSetKeys,
                )
                WorkoutSessionCockpit(
                    exercises = exercises,
                    completedSets = completedSets,
                    milestones = milestones,
                    sessionProgressLabel = "Progreso: $totalCompletedCount/$totalSetsCount",
                    liveEnergySummary = liveEnergySummary,
                    sessionNotes = sessionNotes,
                    sessionSavedNotes = sessionSavedNotes,
                    sessionPhotos = sessionPhotos,
                    sessionChecklist = sessionChecklist,
                    onSessionNotesChange = onSessionNotesChange,
                    onSaveSessionNote = onSaveSessionNote,
                    onAddSessionPhoto = onAddSessionPhoto,
                    onRemoveSessionPhoto = onRemoveSessionPhoto,
                    onAddChecklistItem = onAddChecklistItem,
                    onToggleChecklistItem = onToggleChecklistItem,
                    onRemoveChecklistItem = onRemoveChecklistItem,
                    sessionAccentColor = sessionAccentColor,
                    bodyWeight = bodyWeight,
                    planAspects = planAspects,
                    onRevertPlanAspect = onRevertPlanAspect,
                    godModeActions = godModeUndoStack,
                    onRevertGodModeAction = onRevertGodModeAction,
                )
            }

            if (aboveCarousel != null || selectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = WorkoutUiTokens.LiveCockpitStepperSlotTopPadding,
                            bottom = WorkoutUiTokens.LiveCockpitStepperSlotBottomPadding,
                        )
                        .height(stepperSlotHeight),
                ) {
                    AnimatedContent(
                        targetState = selectionMode,
                        transitionSpec = {
                            fadeIn(tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing))
                        },
                        label = "godModeBatchBar",
                    ) { selecting ->
                        if (selecting) {
                            GodModeBatchBar(
                                selectedCount = selectedIds.size,
                                onCreateSuperset = {
                                    onCreateSupersetFrom(selectedIds.toList())
                                    selectedIds = emptySet()
                                },
                                onSkip = {
                                    onBatchSkip(selectedIds.toList())
                                    selectedIds = emptySet()
                                },
                                onDelete = {
                                    onBatchDelete(selectedIds.toList())
                                    selectedIds = emptySet()
                                },
                                onUltraFast = { pendingUltraFast = true },
                                onClear = { selectedIds = emptySet() },
                            )
                        } else {
                            aboveCarousel?.invoke()
                        }
                    }
                }
            }

            LazyRow(
                state = listState,
                userScrollEnabled = draggingEntryIndex == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WorkoutUiTokens.LiveCockpitCarouselBottomPadding)
                    .height(carouselCardHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fadePx = 28.dp.toPx()
                        val fadeFrac = if (size.width > 0f) (fadePx / size.width).coerceIn(0.04f, 0.18f) else 0.08f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    fadeFrac to Color.Black,
                                    (1f - fadeFrac) to Color.Black,
                                    1f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    count = roadmapEntries.size + if (plusSlot) 1 else 0,
                    key = { entryIdx ->
                        if (entryIdx >= roadmapEntries.size) "roadmap-plus"
                        else when (val entry = roadmapEntries[entryIdx]) {
                            RoadmapEntry.CardioDivider -> "roadmap-cardio-divider"
                            is RoadmapEntry.Group -> {
                                val group = entry.group
                                group.groupId ?: group.exercises.firstOrNull()?.id ?: "group-$entryIdx"
                            }
                        }
                    },
                ) { entryIdx ->
                    if (entryIdx >= roadmapEntries.size) {
                        AnimatedVisibility(
                            visible = godModeActive,
                            enter = fadeIn(tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.86f,
                                    animationSpec = tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing),
                                ),
                            exit = fadeOut(tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)) +
                                scaleOut(
                                    targetScale = 0.86f,
                                    animationSpec = tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing),
                                ),
                        ) {
                            GodModePlusCard(onClick = onAddExercise, accent = sessionAccentColor)
                        }
                        return@items
                    }
                    when (val entry = roadmapEntries[entryIdx]) {
                        RoadmapEntry.CardioDivider -> CardioRoadmapDivider()
                        is RoadmapEntry.Group -> {
                            val group = entry.group
                            val exercise = group.exercises.firstOrNull() ?: return@items

                            val idx = exercises.indexOfFirst { it.id == exercise.id }.coerceAtLeast(0)
                            val part = parts.firstOrNull { it.exercises.any { e -> e.id == exercise.id } }
                            val isSuperset = group.groupId != null && group.exercises.size > 1
                            val accent = when {
                                part != null -> accentByPartId[part.id] ?: sessionAccentColor
                                else -> sessionAccentColor
                            }
                            val partName = normalizeWorkoutHeaderLabel(part?.name)

                            val completedCount = WorkoutStepRules.completedRoadmapSlotsForExercises(
                                exercises = group.exercises,
                                completedSets = completedSets,
                                omittedSetKeys = omittedSetKeys,
                            )
                            val totalSets = WorkoutStepRules.totalRoadmapSlotsForExercises(
                                exercises = group.exercises,
                                omittedSetKeys = omittedSetKeys,
                            )
                            val isAllDone = completedCount >= totalSets && totalSets > 0
                            val isCurrent = group.exercises.any { it.id == exercises.getOrNull(currentIdx)?.id }
                            val groupSelected = group.exercises.any { it.id in selectedIds }
                            val compactLongPress = enableLongPress && !godModeActive
                            val onCardClick: () -> Unit = {
                                if (selectionMode) {
                                    val id = exercise.id
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                } else {
                                    if (isSuperset) onSelectGroup(group.groupId) else onSelect(idx)
                                }
                            }
                            val onCardLongClick: (() -> Unit)? = {
                                onOpenContext(exercise.id)
                            }.takeIf { compactLongPress }

                            val selectedBorder by animateDpAsState(
                                targetValue = if (groupSelected) 2.dp else 0.dp,
                                animationSpec = if (groupSelected) {
                                    tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)
                                } else {
                                    tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)
                                },
                                label = "godModeSelectBorder",
                            )
                            val selectedScrim by animateFloatAsState(
                                targetValue = if (groupSelected) 0.12f else 0f,
                                animationSpec = if (groupSelected) {
                                    tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing)
                                } else {
                                    tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing)
                                },
                                label = "godModeSelectScrim",
                            )
                            val pickedUp = draggingEntryIndex == entryIdx
                            var pickupOffset by remember(entryIdx) { mutableStateOf(0f) }

                            Box(
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(WorkoutUiTokens.GodModeExitMs, easing = FastOutSlowInEasing),
                                        placementSpec = tween(WorkoutUiTokens.GodModeEnterMs, easing = FastOutSlowInEasing),
                                    )
                                    .graphicsLayer {
                                        translationX = if (pickedUp) pickupOffset else 0f
                                        shadowElevation = if (pickedUp) 8f else 0f
                                        clip = false
                                    }
                                    .then(
                                        if (selectedBorder > 0.dp || selectedScrim > 0.01f) {
                                            Modifier
                                                .border(selectedBorder, Color.White.copy(alpha = 0.72f), WorkoutUiTokens.InnerCardShape)
                                                .drawWithContent {
                                                    drawContent()
                                                    if (selectedScrim > 0.01f) {
                                                        drawRect(Color.Black.copy(alpha = selectedScrim))
                                                    }
                                                }
                                        } else Modifier
                                    )
                                    .pointerInput(
                                        godModeActive,
                                        selectionMode,
                                        entryIdx,
                                        roadmapEntries.size,
                                        dragThresholdPx,
                                        dragStridePx,
                                    ) {
                                        if (!godModeActive || selectionMode) return@pointerInput
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val touchSlop = viewConfiguration.touchSlop
                                            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                            val wonLongPress = withTimeoutOrNull(longPressTimeout) {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: return@withTimeoutOrNull false
                                                    if (!change.pressed) return@withTimeoutOrNull false
                                                    if ((change.position - down.position).getDistance() > touchSlop) {
                                                        return@withTimeoutOrNull false
                                                    }
                                                }
                                            }
                                            if (wonLongPress != null) return@awaitEachGesture
                                            draggingEntryIndex = entryIdx
                                            var acc = 0f
                                            var dragging = false
                                            pickupOffset = 0f
                                            autoScrollDirection = 0
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!change.pressed) break
                                                    acc += change.position.x - change.previousPosition.x
                                                    if (!dragging && kotlin.math.abs(acc) >= dragThresholdPx) {
                                                        dragging = true
                                                    }
                                                    if (dragging) {
                                                        pickupOffset = acc
                                                        autoScrollDirection = when {
                                                            acc < -dragStridePx * 0.75f && entryIdx > 0 -> -1
                                                            acc > dragStridePx * 0.75f && entryIdx < roadmapEntries.lastIndex -> 1
                                                            else -> 0
                                                        }
                                                        change.consume()
                                                    }
                                                }
                                                if (dragging) {
                                                    val rawSteps = kotlin.math.round(acc / dragStridePx).toInt()
                                                    val steps = when {
                                                        rawSteps != 0 -> rawSteps
                                                        acc > dragThresholdPx -> 1
                                                        acc < -dragThresholdPx -> -1
                                                        else -> 0
                                                    }
                                                    val target = (entryIdx + steps).coerceIn(0, roadmapEntries.lastIndex)
                                                    if (target != entryIdx) {
                                                        onReorderExercises(
                                                            swappedRoadmapExerciseIds(roadmapEntries, entryIdx, target),
                                                        )
                                                    }
                                                } else {
                                                    selectedIds = selectedIds + exercise.id
                                                }
                                            } finally {
                                                pickupOffset = 0f
                                                draggingEntryIndex = null
                                                autoScrollDirection = 0
                                            }
                                        }
                                    },
                            ) {
                            if (exercise.isCardio) {
                                CardioRoadmapCard(
                                    exercise = exercise,
                                    isCurrent = isCurrent,
                                    isAllDone = isAllDone,
                                    onClick = onCardClick,
                                    onLongClick = onCardLongClick,
                                    pickedUp = pickedUp,
                                )
                            } else if (!isSuperset) {
                                ExerciseRoadmapCard(
                                    exercise = exercise,
                                    completedCount = completedCount,
                                    totalCount = totalSets,
                                    isCurrent = isCurrent,
                                    isAllDone = isAllDone,
                                    accent = accent,
                                    groupName = partName,
                                    completedSets = completedSets,
                                    currentSetIdx = if (isCurrent) currentSetIdx else null,
                                    currentSide = if (isCurrent) currentSide else null,
                                    onClick = onCardClick,
                                    onSelectStep = { setIdx, side ->
                                        onSelectStep(WorkoutStepRules.workingStepKey(exercise.id, setIdx, side))
                                    },
                                    onLongClick = onCardLongClick,
                                    pickedUp = pickedUp,
                                )
                            } else {
                                SupersetRoadmapCard(
                                    exercises = group.exercises,
                                    supersetNumber = group.groupId?.let(supersetOrdinalById::get) ?: 1,
                                    supersetCount = supersetOrdinalById.size,
                                    roundCount = group.groupId
                                        ?.let(supersetGroupById::get)
                                        ?.rounds
                                        ?.takeIf { it > 0 }
                                        ?: (group.exercises.maxOfOrNull { it.sets.size } ?: 0),
                                    completedSets = completedSets,
                                    omittedSetKeys = omittedSetKeys,
                                    isCurrent = isCurrent,
                                    isAllDone = isAllDone,
                                    accent = accent,
                                    groupName = partName,
                                    currentExerciseId = exercises.getOrNull(currentIdx)?.id,
                                    currentRound = if (isCurrent) currentSetIdx + 1 else null,
                                    currentSide = if (isCurrent) currentSide else null,
                                    onClick = onCardClick,
                                    onSelectStep = { exerciseId, setIdx, side ->
                                        onSelectStep(WorkoutStepRules.workingStepKey(exerciseId, setIdx, side))
                                    },
                                    onLongClick = onCardLongClick,
                                    onMemberLongClick = {
                                        if (godModeActive) selectedIds = selectedIds + it
                                        else if (compactLongPress) onOpenMemberContext(it)
                                    },
                                    godMode = godModeActive,
                                    onDissolve = { group.groupId?.let { pendingDissolveGroupId = it } },
                                    onDeleteMember = { memberId -> pendingDeleteExerciseId = memberId },
                                    pickedUp = pickedUp,
                                )
                            }
                            GodModeRoadmapBadges(
                                visible = godModeActive,
                                canDelete = if (isSuperset) true else canDeleteAnyExercise,
                                onDelete = {
                                    if (isSuperset) group.groupId?.let { pendingDissolveGroupId = it }
                                    else pendingDeleteExerciseId = exercise.id
                                },
                            )
                            }
                        }
                    }
                }
            }
            pendingDeleteExerciseId?.let { deleteId ->
                val name = exercises.firstOrNull { it.id == deleteId }?.displayNameWithSelectedChips() ?: "ejercicio"
                KpknAlertDialog(
                    onDismissRequest = { pendingDeleteExerciseId = null },
                    title = { Text("Eliminar ejercicio", fontWeight = FontWeight.Black) },
                    text = { Text("¿Eliminar $name de esta sesión?") },
                    confirmButton = {
                        TextButton(onClick = {
                            onDeleteExercise(deleteId)
                            pendingDeleteExerciseId = null
                        }) { Text("Eliminar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteExerciseId = null }) { Text("Cancelar") }
                    },
                )
            }
            pendingDissolveGroupId?.let { groupId ->
                KpknAlertDialog(
                    onDismissRequest = { pendingDissolveGroupId = null },
                    title = { Text("Disolver superserie", fontWeight = FontWeight.Black) },
                    text = { Text("Los ejercicios vuelven a ser independientes.") },
                    confirmButton = {
                        TextButton(onClick = {
                            onDissolveSuperset(groupId)
                            pendingDissolveGroupId = null
                        }) { Text("Disolver") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDissolveGroupId = null }) { Text("Cancelar") }
                    },
                )
            }
            if (pendingUltraFast) {
                KpknGlassDialog(onDismissRequest = { pendingUltraFast = false }) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Modo ultrarrápido", fontWeight = FontWeight.Black, color = Color.White)
                        KpknSheetWhiteButton(
                            text = "Drop-set",
                            onClick = {
                                onBatchUltraFast(selectedIds.toList(), SeriesTechnique.DROPSET)
                                selectedIds = emptySet()
                                pendingUltraFast = false
                            },
                        )
                        KpknSheetWhiteButton(
                            text = "Rest-pause",
                            onClick = {
                                onBatchUltraFast(selectedIds.toList(), SeriesTechnique.REST_PAUSE)
                                selectedIds = emptySet()
                                pendingUltraFast = false
                            },
                        )
                        TextButton(onClick = { pendingUltraFast = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancelar", color = Color.White)
                        }
                    }
                }
            }
            }
        }
    }
}

internal fun roadmapAccentForPart(part: SessionPart, coverAccent: Color): Color {
    if (part.isEditorUncategorized()) return coverAccent
    val colorId = part.color?.takeIf { it.isNotBlank() } ?: return coverAccent
    return resolvePartAccent(colorId).primary
}

internal fun lazyItemDeltaToCenterPx(
    itemOffsetPx: Int,
    itemSizePx: Int,
    viewportSizePx: Int,
): Int {
    if (viewportSizePx <= 0 || itemSizePx <= 0) return 0
    return (itemOffsetPx + itemSizePx / 2) - (viewportSizePx / 2)
}

private data class ExerciseRoadmapGroup(
    val groupId: String?,
    val exercises: List<Exercise>,
)

private sealed interface RoadmapEntry {
    data class Group(val group: ExerciseRoadmapGroup) : RoadmapEntry
    object CardioDivider : RoadmapEntry
}

internal fun swappedGroupedExerciseIds(
    groups: List<List<String>>,
    fromIndex: Int,
    toIndex: Int,
): List<String> {
    if (fromIndex !in groups.indices || toIndex !in groups.indices) return groups.flatten()
    val mutable = groups.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable.flatten()
}

private fun swappedRoadmapExerciseIds(
    entries: List<RoadmapEntry>,
    fromIndex: Int,
    toIndex: Int,
): List<String> = swappedGroupedExerciseIds(
    groups = entries.map { entry ->
        when (entry) {
            RoadmapEntry.CardioDivider -> emptyList()
            is RoadmapEntry.Group -> entry.group.exercises.map { it.id }
        }
    },
    fromIndex = fromIndex,
    toIndex = toIndex,
)

private fun Exercise.supersetGroupRefOrLegacyId(): String? =
    supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }

private fun Exercise.isEffectivelyUnilateral(): Boolean =
    unilateralMode != UnilateralMode.BILATERAL || isUnilateral

private fun Exercise.expectedSidesForSet(setIndex: Int): List<String> {
    if (!isEffectivelyUnilateral()) return listOf("Bilateral")
    return WorkoutStepRules.workingSidesForSet(this, setIndex)
}

private fun Exercise.completionKeysForSet(setIndex: Int): List<String> =
    WorkoutStepRules.completionKeysForRoadmapSet(this, setIndex)

private val CardioRoadmapAccent = Color(0xFFE0A13A)

@Composable
private fun CardioRoadmapDivider() {
    val chromeScale = LocalViewportAdapt.current.uniformScale
    Row(
        modifier = Modifier
            .width(82.dp * chromeScale)
            .height(WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CardioRoadmapAccent.copy(alpha = 0.76f)),
        )
        Text(
            text = "CARDIO",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = CardioRoadmapAccent,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CardioRoadmapAccent.copy(alpha = 0.76f)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardioRoadmapCard(
    exercise: Exercise,
    isCurrent: Boolean,
    isAllDone: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    pickedUp: Boolean = false,
) {
    val details = exercise.cardioDetails
    val modeLabel = when (details?.programMode()) {
        CardioProgramMode.HIIT_SIT -> "HIIT / SIT"
        CardioProgramMode.INTERVALS -> "Intervalos"
        CardioProgramMode.STEADY -> "Estático"
        null -> "Cardio"
    }
    val durationSeconds = details?.effectiveDurationSeconds()?.takeIf { it > 0 }
    val summary = buildString {
        append(modeLabel)
        durationSeconds?.let {
            append(" · ")
            append(formatRoadmapDuration(it))
        }
    }
    val displayName = exercise.displayNameWithSelectedChips()
    val cardColor = when {
        isCurrent -> CardioRoadmapAccent.copy(alpha = 0.72f)
        isAllDone -> Color(0xFF344238).copy(alpha = 0.90f)
        else -> Color(0xFF282725).copy(alpha = 0.96f)
    }
    val contentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = when {
        isCurrent -> CardioRoadmapAccent.copy(alpha = 0.82f)
        isAllDone -> Color(0xFF7FBF8A).copy(alpha = 0.44f)
        else -> CardioRoadmapAccent.copy(alpha = 0.30f)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed || pickedUp) 0.95f else 1.0f,
        label = "cardioPickup",
    )

    val chromeScale = LocalViewportAdapt.current.uniformScale
    Surface(
        modifier = Modifier
            .widthIn(min = 190.dp, max = 280.dp)
            .height(WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale))
            .scale(pressScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = WorkoutUiTokens.InnerCardShape,
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (isCurrent) Color.White.copy(alpha = 0.18f) else CardioRoadmapAccent.copy(alpha = 0.18f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "C",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else CardioRoadmapAccent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.68f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRoadmapCard(
    exercise: Exercise,
    completedCount: Int,
    totalCount: Int,
    isCurrent: Boolean,
    isAllDone: Boolean,
    accent: Color,
    groupName: String?,
    completedSets: Map<String, CompletedSet> = emptyMap(),
    currentSetIdx: Int? = null,
    currentSide: String? = null,
    onClick: () -> Unit,
    onSelectStep: (Int, String?) -> Unit = { _, _ -> },
    onLongClick: (() -> Unit)?,
    scale: RoadmapCardScale = RoadmapCardScale.Full,
    pickedUp: Boolean = false,
) {
    val displayName = exercise.displayNameWithSelectedChips()
    val isUnilateral = exercise.isEffectivelyUnilateral()
    val nameLength = displayName.length
    val isMini = scale == RoadmapCardScale.Mini
    val chromeScale = LocalViewportAdapt.current.uniformScale
    val minWidth = when {
        isMini -> 84.dp
        isUnilateral -> 120.dp
        nameLength > 30 -> 130.dp
        nameLength > 22 -> 110.dp
        else -> 88.dp
    } * chromeScale
    val maxWidth = (if (isMini) 138.dp else 220.dp) * chromeScale
    val cardHeight = if (isMini) {
        WorkoutUiTokens.liveRoadmapCarouselMiniCardHeight(chromeScale)
    } else {
        WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale)
    }
    val containerColor = when {
        isCurrent -> accent.copy(alpha = 0.86f)
        isAllDone -> accent.copy(alpha = 0.30f)
        else -> accent.copy(alpha = 0.26f)
    }
    val contentColor = if (isCurrent) contentOn(containerColor) else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isCurrent) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed || pickedUp) 0.95f else 1.0f,
        label = "exercisePress",
    )

    Surface(
        modifier = Modifier
            .widthIn(min = minWidth, max = maxWidth)
            .height(cardHeight)
            .scale(pressScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = WorkoutUiTokens.InnerCardShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isMini) 6.dp else 8.dp,
                    vertical = if (isMini) 4.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isMini) 4.dp else 6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "$completedCount/$totalCount",
                    modifier = Modifier.padding(
                        horizontal = if (isMini) 5.dp else 6.dp,
                        vertical = if (isMini) 1.dp else 2.dp,
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isMini) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isMini) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                if (!isMini && !groupName.isNullOrBlank()) {
                    Text(
                        text = groupName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

private fun formatRoadmapDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes} min"
        else -> "${seconds}s"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SupersetRoadmapCard(
    exercises: List<Exercise>,
    supersetNumber: Int,
    supersetCount: Int,
    roundCount: Int,
    completedSets: Map<String, CompletedSet>,
    omittedSetKeys: Set<String> = emptySet(),
    isCurrent: Boolean,
    isAllDone: Boolean,
    accent: Color,
    groupName: String?,
    currentExerciseId: String?,
    currentRound: Int?,
    currentSide: String? = null,
    onClick: () -> Unit,
    onSelectStep: (String, Int, String?) -> Unit = { _, _, _ -> },
    onLongClick: (() -> Unit)?,
    onMemberLongClick: (String) -> Unit = {},
    godMode: Boolean = false,
    onDissolve: () -> Unit = {},
    onDeleteMember: (String) -> Unit = {},
    pickedUp: Boolean = false,
) {
    val safeRoundCount = roundCount.coerceAtLeast(1)
    val currentRoundIndex = ((currentRound ?: 1) - 1).coerceIn(0, safeRoundCount - 1)
    val motherAccent = accent
    val cardColor = when {
        isAllDone -> accent.copy(alpha = 0.30f)
        isCurrent -> motherAccent.copy(alpha = 0.86f)
        else -> motherAccent.copy(alpha = 0.26f)
    }
    val onCard = if (isCurrent) contentOn(cardColor) else MaterialTheme.colorScheme.onSurface
    val outline = when {
        isCurrent -> Color.White.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed || pickedUp) 0.95f else 1.0f,
        label = "supersetPickup",
    )
    val chromeScale = LocalViewportAdapt.current.uniformScale
    Box(
        modifier = Modifier
            .height(WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale))
            .wrapContentWidth()
            .graphicsLayer { clip = false }
            .scale(pressScale)
            .background(cardColor, WorkoutUiTokens.InnerCardShape)
            .border(BorderStroke(1.dp, outline), WorkoutUiTokens.InnerCardShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.widthIn(min = 72.dp, max = 92.dp)) {
                Text(
                    "Superserie",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onCard,
                    maxLines = 1,
                )
                Text(
                    if (isAllDone) "Completada" else "Ronda ${currentRound ?: 1}/$safeRoundCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isCurrent) onCard.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercises.forEach { member ->
                    val memberCompleted = WorkoutStepRules.completedRoadmapSlotsForExercise(
                        exercise = member,
                        completedSets = completedSets,
                        omittedSetKeys = omittedSetKeys,
                    )
                    val memberTotal = WorkoutStepRules.totalRoadmapSlotsForExercise(
                        exercise = member,
                        omittedSetKeys = omittedSetKeys,
                    )
                    val memberAllDone = memberCompleted >= memberTotal && memberTotal > 0
                    val memberIsCurrent = member.id == currentExerciseId && isCurrent
                    Box(modifier = Modifier.graphicsLayer { clip = false }) {
                    ExerciseRoadmapCard(
                        exercise = member,
                        completedCount = memberCompleted,
                        totalCount = memberTotal,
                        isCurrent = memberIsCurrent,
                        isAllDone = memberAllDone,
                        accent = motherAccent,
                        groupName = null,
                        onClick = {
                            val side = if (member.isEffectivelyUnilateral()) {
                                member.expectedSidesForSet(currentRoundIndex).firstOrNull { candidate ->
                                    !completedSets.containsKey(
                                        "${member.id}_${currentRoundIndex}_${candidate.take(1).uppercase()}",
                                    )
                                }?.lowercase()
                            } else {
                                null
                            }
                            onSelectStep(member.id, currentRoundIndex, side)
                        },
                        onLongClick = { onMemberLongClick(member.id) },
                        scale = RoadmapCardScale.Mini,
                    )
                    GodModeRoadmapBadges(
                        visible = godMode,
                        canDelete = true,
                        onDelete = { onDeleteMember(member.id) },
                    )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SupersetRoadmapCardLegacy(
    exercises: List<Exercise>,
    supersetNumber: Int,
    supersetCount: Int,
    roundCount: Int,
    completedSets: Map<String, CompletedSet>,
    isCurrent: Boolean,
    isAllDone: Boolean,
    accent: Color,
    groupName: String?,
    currentExerciseId: String?,
    currentRound: Int?,
    currentSide: String? = null,
    onClick: () -> Unit,
    onSelectStep: (String, Int, String?) -> Unit = { _, _, _ -> },
    onLongClick: (() -> Unit)?,
) {
    val safeRoundCount = roundCount.coerceAtLeast(1)
    val title = if (supersetCount > 1) "Superserie $supersetNumber" else "Superserie"

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1.0f)

    // Manage expanded round in accordion style
    var userToggledRound by remember(currentRound, isCurrent, isAllDone) { mutableStateOf<Int?>(null) }
    val activeExpandedRound = when {
        userToggledRound != null -> if (userToggledRound == -1) null else userToggledRound
        isCurrent && currentRound != null && !isAllDone -> (currentRound - 1).coerceIn(0, safeRoundCount - 1)
        else -> null
    }

    val containerColor = when {
        isCurrent -> accent.copy(alpha = 0.20f)
        isAllDone -> accent.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val borderColor = when {
        isCurrent -> accent
        isAllDone -> accent.copy(alpha = 0.62f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    }

    Surface(
        modifier = Modifier
            .heightIn(min = 52.dp)
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = WorkoutUiTokens.InnerCardShape,
        color = containerColor,
        border = BorderStroke(width = if (isCurrent) 1.5.dp else 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Superset badge & Title Header (compact)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isCurrent) accent else Color.White.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = if (isAllDone) "✓" else if (supersetCount > 1) "SS$supersetNumber" else "SS",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isCurrent) Color.Black else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(
                    modifier = Modifier.widthIn(min = 54.dp, max = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isAllDone) "Completada" else currentRound?.let { "Ronda $it/$safeRoundCount" } ?: "$safeRoundCount rondas",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Medium,
                        color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Divider between title and horizontal rounds
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            )

            // Horizontal rounds carousel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(safeRoundCount) { roundIdx ->
                    val roundKeys = exercises.flatMap { it.completionKeysForSet(roundIdx) }
                    val roundDone = roundKeys.isNotEmpty() && roundKeys.all { completedSets.containsKey(it) }
                    val isRoundCurrent = isCurrent && currentRound == roundIdx + 1
                    val isExpanded = activeExpandedRound == roundIdx

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isExpanded -> Color.White.copy(alpha = 0.08f)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = if (isExpanded) 4.dp else 0.dp, vertical = 2.dp),
                    ) {
                        // R1 / R2 badge - Clickable to toggle expand / collapse!
                        Surface(
                            modifier = Modifier
                                .size(if (isRoundCurrent) 22.dp else 20.dp)
                                .clickable {
                                    userToggledRound = if (isExpanded) -1 else roundIdx
                                },
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                roundDone -> accent
                                isRoundCurrent -> accent
                                else -> Color.White.copy(alpha = 0.10f)
                            },
                            border = BorderStroke(
                                width = if (isRoundCurrent || roundDone) 0.dp else 1.dp,
                                color = when {
                                    roundDone -> accent
                                    isRoundCurrent -> accent
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                },
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (roundDone && !isExpanded) "✓" else "R${roundIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isRoundCurrent) 9.sp else 8.sp),
                                    fontWeight = FontWeight.Black,
                                    color = if (isRoundCurrent || roundDone) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                )
                            }
                        }

                        // Exercises in this round - Expanded horizontally when selected or active!
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandHorizontally(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
                            exit = shrinkHorizontally(animationSpec = tween(180)) + fadeOut(animationSpec = tween(100)),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 2.dp),
                            ) {
                                val roundExercises = exercises.filter { roundIdx in it.sets.indices }
                                roundExercises.forEachIndexed { visibleExIdx, ex ->
                                    val exKeys = ex.completionKeysForSet(roundIdx)
                                    val exDone = exKeys.isNotEmpty() && exKeys.all { completedSets.containsKey(it) }
                                    val isExCurrent = isRoundCurrent && currentExerciseId == ex.id
                                    val exLetter = ('A'.code + exercises.indexOf(ex)).toChar().toString()
                                    val sides: List<String?> = if (ex.isEffectivelyUnilateral()) {
                                        WorkoutStepRules.workingSidesForSet(ex, roundIdx)
                                    } else {
                                        listOf(null)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        sides.forEach { side ->
                                            val sideKey = side?.let { "${ex.id}_${roundIdx}_${it.take(1).uppercase()}" }
                                            val sideDone = sideKey?.let(completedSets::containsKey) ?: exDone
                                            val isSideCurrent = isExCurrent &&
                                                (currentSide == null || currentSide.equals(side, ignoreCase = true))
                                            val nodeLabel = side?.take(1)?.uppercase()?.let { "$exLetter$it" } ?: exLetter

                                            Box(
                                                modifier = Modifier
                                                    .height(22.dp)
                                                    .widthIn(min = 22.dp)
                                                    .padding(horizontal = 2.dp)
                                                    .clickable { onSelectStep(ex.id, roundIdx, side) }
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(
                                                        when {
                                                            sideDone -> accent
                                                            isSideCurrent -> accent
                                                            else -> Color.White.copy(alpha = 0.12f)
                                                        }
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = when {
                                                            sideDone -> accent
                                                            isSideCurrent -> accent
                                                            else -> Color.White.copy(alpha = 0.35f)
                                                        },
                                                        shape = RoundedCornerShape(999.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = nodeLabel,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    fontWeight = FontWeight.Black,
                                                    color = if (sideDone || isSideCurrent) Color.Black else Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp),
                                                )
                                            }
                                        }
                                    }

                                    if (visibleExIdx < roundExercises.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .width(6.dp)
                                                .height(1.dp)
                                                .background(
                                                    if (roundDone) accent.copy(alpha = 0.7f)
                                                    else Color.White.copy(alpha = 0.3f)
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleProgressCard(
    muscle: String,
    doneSets: Int,
    totalSets: Int,
    sessionAccentColor: Color,
    onClick: () -> Unit
) {
    val progress = if (totalSets > 0) doneSets.toFloat() / totalSets.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350),
        label = "muscleProgress"
    )
    val isCompleted = doneSets >= totalSets && totalSets > 0

    val cardColor = if (isCompleted) sessionAccentColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
    val borderColor = if (isCompleted) sessionAccentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
    val accentColor = sessionAccentColor

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = WorkoutUiTokens.InnerCardShape,
        color = cardColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = muscle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = if (isCompleted) "✓ $doneSets/$totalSets" else "$doneSets/$totalSets",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}
