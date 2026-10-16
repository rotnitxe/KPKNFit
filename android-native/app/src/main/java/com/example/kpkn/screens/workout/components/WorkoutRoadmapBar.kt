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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.isEditorUncategorized
import com.example.kpkn.screens.sessioneditor.resolvePartAccent
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

private enum class RoadmapCardScale { Full, Mini }

enum class RoadmapMode {
    COMPACT,
    EXPANDED
}

/**
 * Lo que quedó pendiente al cerrar una capa de la sesión por accidente (LV4, 15): «Estado previo» o «Terminar». Se pinta como una píldora
 * en la fila del asa del cockpit y solo mientras está pendiente; tocarla vuelve a abrir la capa.
 */
data class RoadmapPendingAction(val label: String, val onClick: () -> Unit)

/**
 * Barra compacta de navegación: el asa y el gesto abren el cockpit. Las opciones y los lotes viven en sus hojas.
 * Los parámetros antiguos de edición se conservan para los llamadores existentes; ya no activan UI ni estado de modo dios.
 */
@Suppress("UNUSED_PARAMETER")
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
    onRoadmapHeightChanged: (Int) -> Unit = {},
    pendingActions: List<RoadmapPendingAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val activeMode = mode
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0))
    val chromeScale = LocalViewportAdapt.current.uniformScale
    val carouselCardHeight = WorkoutUiTokens.liveRoadmapCarouselCardHeight(chromeScale)
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


    val supersetOrdinalById = remember(roadmapGroups) {
        roadmapGroups.mapNotNull { it.groupId?.takeIf { _ -> it.exercises.size > 1 } }
            .distinct().withIndex().associate { (index, groupId) -> groupId to index + 1 }
    }
    val supersetGroupById = remember(supersetGroups) { supersetGroups.associateBy { it.id } }
    val roadmapShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
        modifier = modifier.fillMaxWidth().cockpitVerticalGesture(activeMode, onModeChange),
        shape = roadmapShape,
        color = Color.Transparent,
        contentColor = WizardColors.text,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().clip(roadmapShape).background(KpknSurface.module)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(WorkoutUiTokens.MinTouchTarget)
                    .clickable { onModeChange(if (activeMode == RoadmapMode.COMPACT) RoadmapMode.EXPANDED else RoadmapMode.COMPACT) },
                contentAlignment = Alignment.Center,
            ) {
                if (pendingActions.isEmpty()) {
                    RoadmapHandleIcons(expanded = activeMode == RoadmapMode.EXPANDED)
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            pendingActions.getOrNull(0)?.let { RoadmapPendingPill(it.label, it.onClick) }
                        }
                        RoadmapHandleIcons(expanded = activeMode == RoadmapMode.EXPANDED)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            pendingActions.getOrNull(1)?.let { RoadmapPendingPill(it.label, it.onClick) }
                        }
                    }
                }
            }
            if (aboveCarousel != null) {
                Box(Modifier.fillMaxWidth().padding(
                    top = WorkoutUiTokens.LiveCockpitStepperSlotTopPadding,
                    bottom = WorkoutUiTokens.LiveCockpitStepperSlotBottomPadding,
                ).height(stepperSlotHeight)) { aboveCarousel() }
            }
            if (exercises.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(carouselCardHeight), contentAlignment = Alignment.Center) {
                    com.example.kpkn.ui.design.KpknTextAction(text = "Añadir ejercicio", onClick = onAddExercise)
                }
            } else LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(bottom = WorkoutUiTokens.LiveCockpitCarouselBottomPadding)
                    .height(carouselCardHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fade = (28.dp.toPx() / size.width.coerceAtLeast(1f)).coerceIn(0.04f, 0.18f)
                        drawRect(Brush.horizontalGradient(colorStops = arrayOf(
                            0f to Color.Transparent, fade to Color.Black,
                            (1f - fade) to Color.Black, 1f to Color.Transparent,
                        )), blendMode = BlendMode.DstIn)
                    },
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(roadmapEntries.size, key = { index ->
                    when (val entry = roadmapEntries[index]) {
                        RoadmapEntry.CardioDivider -> "roadmap-cardio-divider"
                        is RoadmapEntry.Group -> entry.group.groupId ?: entry.group.exercises.first().id
                    }
                }) { index ->
                    when (val entry = roadmapEntries[index]) {
                        RoadmapEntry.CardioDivider -> CardioRoadmapDivider()
                        is RoadmapEntry.Group -> {
                            val group = entry.group
                            val exercise = group.exercises.firstOrNull() ?: return@items
                            val idx = exercises.indexOfFirst { it.id == exercise.id }.coerceAtLeast(0)
                            val part = parts.firstOrNull { it.exercises.any { e -> e.id == exercise.id } }
                            val partName = normalizeWorkoutHeaderLabel(part?.name)
                            val done = WorkoutStepRules.completedRoadmapSlotsForExercises(group.exercises, completedSets, omittedSetKeys)
                            val total = WorkoutStepRules.totalRoadmapSlotsForExercises(group.exercises, omittedSetKeys)
                            val isDone = done >= total && total > 0
                            val current = group.exercises.any { it.id == exercises.getOrNull(currentIdx)?.id }
                            val superset = group.groupId != null && group.exercises.size > 1
                            val select: () -> Unit = { if (superset) onSelectGroup(group.groupId!!) else onSelect(idx) }
                            val open: (() -> Unit)? = { onOpenContext(exercise.id) }.takeIf { enableLongPress }
                            if (exercise.isCardio) {
                                CardioRoadmapCard(exercise, current, isDone, select, open)
                            } else if (!superset) {
                                ExerciseRoadmapCard(
                                    exercise = exercise, completedCount = done, totalCount = total,
                                    isCurrent = current, isAllDone = isDone, groupName = partName,
                                    completedSets = completedSets, currentSetIdx = currentSetIdx.takeIf { current },
                                    currentSide = currentSide.takeIf { current }, onClick = select,
                                    onSelectStep = { set, side -> onSelectStep(WorkoutStepRules.workingStepKey(exercise.id, set, side)) },
                                    onLongClick = open,
                                )
                            } else {
                                SupersetRoadmapCard(
                                    exercises = group.exercises,
                                    supersetNumber = supersetOrdinalById[group.groupId] ?: 1,
                                    supersetCount = supersetOrdinalById.size,
                                    roundCount = supersetGroupById[group.groupId]?.rounds?.takeIf { it > 0 }
                                        ?: (group.exercises.maxOfOrNull { it.sets.size } ?: 0),
                                    completedSets = completedSets, omittedSetKeys = omittedSetKeys,
                                    isCurrent = current, isAllDone = isDone, groupName = partName,
                                    currentExerciseId = exercises.getOrNull(currentIdx)?.id,
                                    currentRound = (currentSetIdx + 1).takeIf { current },
                                    currentSide = currentSide.takeIf { current }, onClick = select,
                                    onSelectStep = { id, set, side -> onSelectStep(WorkoutStepRules.workingStepKey(id, set, side)) },
                                    onLongClick = open,
                                    onMemberLongClick = { if (enableLongPress) onOpenMemberContext(it) },
                                )
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

private val CardioRoadmapAccent = Color(0xFFE0A13A)

/** El asa del cockpit: las dos marcas, juntas y centradas. */
@Composable
private fun RoadmapHandleIcons(expanded: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = if (expanded) "Plegar resumen de sesión" else "Abrir resumen de sesión",
            tint = WizardColors.textFaint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = WizardColors.textFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Lo pendiente en la fila del asa (LV4, 15): píldora de 32 dp con el texto a 13 sp, como la «píldora compacta» de la app pero con menos
 * relleno a los lados, para que «Estado previo» quepa en su hueco también con la letra al 130 %. La zona táctil es toda la altura de la fila.
 */
@Composable
private fun RoadmapPendingPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(WorkoutUiTokens.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.action.copy(fontSize = 13.sp),
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
    // Cardio conserva su acento ámbar como identidad; el bloque activo va en tinta.
    val cardColor = if (isCurrent) WizardColors.text else KpknSurface.inset
    val contentColor = if (isCurrent) WizardColors.ctaContent else WizardColors.textMuted
    val borderColor = when {
        isCurrent -> WizardColors.text
        isAllDone -> SymbolPalette.ok.copy(alpha = 0.44f)
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
        shape = KpknShape.pill,
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
                color = if (isCurrent) WizardColors.ctaContent.copy(alpha = 0.12f) else CardioRoadmapAccent.copy(alpha = 0.18f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "C",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCurrent -> WizardColors.ctaContent
                        isAllDone -> SymbolPalette.ok
                        else -> CardioRoadmapAccent
                    },
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
                    color = if (isCurrent) WizardColors.ctaContent.copy(alpha = 0.7f) else WizardColors.textFaint,
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
    val containerColor = if (isCurrent) WizardColors.text else KpknSurface.inset
    val contentColor = if (isCurrent) WizardColors.ctaContent else WizardColors.textMuted
    val borderColor = if (isCurrent) WizardColors.text else KpknSurface.moduleBorder
    // El acento de sesión (musculo) solo marca el progreso parcial de la serie.
    val countColor = when {
        isCurrent -> WizardColors.ctaContent
        isAllDone -> SymbolPalette.ok
        completedCount > 0 -> SymbolPalette.musculo
        else -> WizardColors.textMuted
    }

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
        shape = KpknShape.pill,
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
                color = if (isCurrent) WizardColors.ctaContent.copy(alpha = 0.12f) else KpknSurface.module,
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
                    color = countColor,
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
                        color = if (isCurrent) WizardColors.ctaContent.copy(alpha = 0.7f) else WizardColors.textFaint,
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
    // El grupo es un módulo con filete; el grupo activo marca el filete en tinta.
    val cardColor = KpknSurface.module
    val onCard = if (isCurrent) WizardColors.text else WizardColors.textMuted
    val outline = when {
        isCurrent -> WizardColors.text.copy(alpha = 0.72f)
        else -> KpknSurface.moduleBorder
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
                    color = when {
                        isAllDone -> SymbolPalette.ok
                        isCurrent -> onCard.copy(alpha = 0.85f)
                        else -> WizardColors.textFaint
                    },
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
                    }
                }
            }
        }
    }
}
