package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.kpkn.data.models.*
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutRoadmapBar(
    exercises: List<Exercise>,
    parts: List<SessionPart> = emptyList(),
    supersetGroups: List<SupersetGroup> = emptyList(),
    currentIdx: Int,
    currentSetIdx: Int = 0,
    currentSide: String? = null,
    completedSets: Map<String, CompletedSet>,
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
    exerciseNote: String = "",
    exercisePhotos: List<String> = emptyList(),
    onExerciseNoteChange: (String) -> Unit = {},
    onAddExercisePhoto: (android.net.Uri) -> Unit = {},
    onRemoveExercisePhoto: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeMode = mode

    val accentByPartId = remember(parts) {
        parts.associate { part ->
            part.id to runCatching {
                Color((part.color ?: "#3B82F6").toColorInt())
            }.getOrDefault(Color(0xFF3B82F6))
        }
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIdx - 1).coerceAtLeast(0)
    )

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

    LaunchedEffect(currentIdx, roadmapEntries.size) {
        val currentExerciseId = exercises.getOrNull(currentIdx)?.id
        val currentEntryIdx = roadmapEntries.indexOfFirst { entry ->
            entry is RoadmapEntry.Group && entry.group.exercises.any { it.id == currentExerciseId }
        }
        if (currentEntryIdx >= 0) {
            listState.scrollToItem((currentEntryIdx - 1).coerceAtLeast(0))
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .kpknGlassOrFallback(hazeState, roadmapShape, withBorder = false)
                .clickable(enabled = activeMode == RoadmapMode.COMPACT) {
                    onModeChange(RoadmapMode.EXPANDED)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                val totalCompletedCount = exercises.sumOf { e ->
                    e.sets.indices.sumOf { sIdx ->
                        e.completionKeysForSet(sIdx).count { completedSets.containsKey(it) }
                    }
                }
                val totalSetsCount = exercises.sumOf { e ->
                    e.sets.indices.sumOf { e.completionKeysForSet(it).size }
                }
                WorkoutSessionCockpit(
                    currentExercise = exercises.getOrNull(currentIdx),
                    completedSets = completedSets,
                    milestones = milestones,
                    exerciseNote = exerciseNote,
                    exercisePhotos = exercisePhotos,
                    sessionProgressLabel = "Progreso: $totalCompletedCount/$totalSetsCount",
                    onNoteChange = onExerciseNoteChange,
                    onAddPhoto = onAddExercisePhoto,
                    onRemovePhoto = onRemoveExercisePhoto,
                    sessionAccentColor = sessionAccentColor,
                )
            }

            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    count = roadmapEntries.size,
                    key = { entryIdx ->
                        when (val entry = roadmapEntries[entryIdx]) {
                            RoadmapEntry.CardioDivider -> "roadmap-cardio-divider"
                            is RoadmapEntry.Group -> {
                                val group = entry.group
                                group.groupId ?: group.exercises.firstOrNull()?.id ?: "group-$entryIdx"
                            }
                        }
                    },
                ) { entryIdx ->
                    when (val entry = roadmapEntries[entryIdx]) {
                        RoadmapEntry.CardioDivider -> CardioRoadmapDivider()
                        is RoadmapEntry.Group -> {
                            val group = entry.group
                            val exercise = group.exercises.firstOrNull() ?: return@items

                            val idx = exercises.indexOfFirst { it.id == exercise.id }.coerceAtLeast(0)
                            val part = parts.firstOrNull { it.exercises.any { e -> e.id == exercise.id } }
                            val accent = accentByPartId[part?.id] ?: sessionAccentColor
                            val partName = normalizeWorkoutHeaderLabel(part?.name)

                            val completedCount = group.exercises.sumOf { member ->
                                member.sets.indices.sumOf { setIdx ->
                                    member.completionKeysForSet(setIdx).count { key -> completedSets.containsKey(key) }
                                }
                            }
                            val totalSets = group.exercises.sumOf { member ->
                                member.sets.indices.sumOf { setIdx -> member.completionKeysForSet(setIdx).size }
                            }
                            val isAllDone = completedCount >= totalSets && totalSets > 0
                            val isCurrent = group.exercises.any { it.id == exercises.getOrNull(currentIdx)?.id }

                            if (exercise.isCardio) {
                                // Cardio gets its own roadmap language. It has no strength
                                // set/side steppers or strength action semantics.
                                CardioRoadmapCard(
                                    exercise = exercise,
                                    isCurrent = isCurrent,
                                    isAllDone = isAllDone,
                                    onClick = { onSelect(idx) },
                                    onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
                                )
                            } else if (group.groupId == null || group.exercises.size == 1) {
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
                                    onClick = { onSelect(idx) },
                                    onSelectStep = { setIdx, side ->
                                        onSelectStep(WorkoutStepRules.workingStepKey(exercise.id, setIdx, side))
                                    },
                                    onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
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
                                    isCurrent = isCurrent,
                                    isAllDone = isAllDone,
                                    accent = accent,
                                    groupName = partName,
                                    currentExerciseId = exercises.getOrNull(currentIdx)?.id,
                                    currentRound = if (isCurrent) currentSetIdx + 1 else null,
                                    currentSide = if (isCurrent) currentSide else null,
                                    onClick = { onSelectGroup(group.groupId) },
                                    onSelectStep = { exerciseId, setIdx, side ->
                                        onSelectStep(WorkoutStepRules.workingStepKey(exerciseId, setIdx, side))
                                    },
                                    onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
                                    onMemberLongClick = if (enableLongPress) onOpenMemberContext else ({}) ,
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

private data class ExerciseRoadmapGroup(
    val groupId: String?,
    val exercises: List<Exercise>,
)

private sealed interface RoadmapEntry {
    data class Group(val group: ExerciseRoadmapGroup) : RoadmapEntry
    object CardioDivider : RoadmapEntry
}

private fun Exercise.supersetGroupRefOrLegacyId(): String? =
    supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }

private fun Exercise.isEffectivelyUnilateral(): Boolean =
    unilateralMode != UnilateralMode.BILATERAL || isUnilateral

private fun Exercise.expectedSidesForSet(setIndex: Int): List<String> {
    if (!isEffectivelyUnilateral()) return listOf("Bilateral")
    return WorkoutStepRules.workingSidesForSet(this, setIndex)
}

private fun Exercise.completionKeysForSet(setIndex: Int): List<String> {
    if (setIndex !in sets.indices) return emptyList()
    if (!isEffectivelyUnilateral()) return listOf("${id}_$setIndex")

    val set = sets[setIndex]
    val hasLeftOnly = set.leftTarget != null && set.rightTarget == null
    val hasRightOnly = set.rightTarget != null && set.leftTarget == null
    return when {
        hasLeftOnly -> listOf("${id}_${setIndex}_L")
        hasRightOnly -> listOf("${id}_${setIndex}_R")
        else -> listOf("${id}_${setIndex}_L", "${id}_${setIndex}_R")
    }
}

private val CardioRoadmapAccent = Color(0xFFE0A13A)

@Composable
private fun CardioRoadmapDivider() {
    Row(
        modifier = Modifier
            .width(82.dp)
            .height(64.dp),
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

    Surface(
        modifier = Modifier
            .widthIn(min = 190.dp, max = 280.dp)
            .height(64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
) {
    val displayName = exercise.displayNameWithSelectedChips()
    val isUnilateral = exercise.isEffectivelyUnilateral()
    val nameLength = displayName.length
    val minWidth = when {
        isUnilateral -> 120.dp
        nameLength > 30 -> 130.dp
        nameLength > 22 -> 110.dp
        else -> 88.dp
    }
    val containerColor = when {
        isCurrent -> accent.copy(alpha = 0.86f)
        isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.30f)
        else -> accent.copy(alpha = 0.28f)
    }
    val contentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isCurrent) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1.0f)

    Surface(
        modifier = Modifier
            .widthIn(min = minWidth, max = 220.dp)
            .height(64.dp)
            .scale(scale)
            .combinedClickable(
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isCurrent) Color.White.copy(alpha = 0.20f) else accent.copy(alpha = 0.28f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "$completedCount/$totalCount",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                if (!groupName.isNullOrBlank()) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SupersetRoadmapCard(
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
    onMemberLongClick: (String) -> Unit = {},
) {
    val safeRoundCount = roundCount.coerceAtLeast(1)
    val currentRoundIndex = ((currentRound ?: 1) - 1).coerceIn(0, safeRoundCount - 1)
    // Superseries share the same quiet roadmap language as normal exercises.
    // Accent is reserved for the tiny progress markers; using it as the whole
    // container made this card read as a neon, unrelated component.
    val cardColor = when {
        isAllDone -> Color(0xFF304236).copy(alpha = 0.86f)
        isCurrent -> Color(0xFF303236).copy(alpha = 0.98f)
        else -> Color(0xFF252629).copy(alpha = 0.94f)
    }
    val outline = when {
        isAllDone -> Color(0xFF7FBF8A).copy(alpha = 0.42f)
        isCurrent -> Color.White.copy(alpha = 0.20f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    }
    Surface(
        modifier = Modifier
            .height(64.dp)
            .widthIn(min = 240.dp, max = 360.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = WorkoutUiTokens.InnerCardShape,
        color = cardColor,
        border = BorderStroke(1.dp, outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.widthIn(min = 72.dp, max = 92.dp)) {
                Text(
                    "Superserie",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    if (isAllDone) "Completada" else "Ronda ${currentRound ?: 1}/$safeRoundCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercises.forEach { member ->
                    val memberLabel = member.displayNameWithSelectedChips()
                    val memberRoundDone = member.completionKeysForSet(currentRoundIndex).let { keys ->
                        keys.isNotEmpty() && keys.all { completedSets.containsKey(it) }
                    }
                    val memberIsCurrent = member.id == currentExerciseId && isCurrent
                     Surface(
                         modifier = Modifier
                            .widthIn(min = 84.dp, max = 138.dp)
                            .height(46.dp)
                            .combinedClickable(
                                onClick = {
                                    val side = if (member.isEffectivelyUnilateral()) {
                                        member.expectedSidesForSet(currentRoundIndex).firstOrNull { candidate ->
                                            !completedSets.containsKey("${member.id}_${currentRoundIndex}_${candidate.take(1).uppercase()}")
                                        }?.lowercase()
                                    } else null
                                    onSelectStep(member.id, currentRoundIndex, side)
                                },
                                onLongClick = { onMemberLongClick(member.id) },
                            ),
                        shape = RoundedCornerShape(8.dp),
                         color = if (memberIsCurrent) Color(0xFF45474B) else Color(0xFF1D1E20),
                         border = BorderStroke(
                             1.dp,
                             if (memberIsCurrent) Color.White.copy(alpha = 0.20f)
                             else Color.White.copy(alpha = 0.08f),
                         ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                memberLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = if (memberIsCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(safeRoundCount) { roundIdx ->
                                    val done = member.completionKeysForSet(roundIdx).let { keys ->
                                        keys.isNotEmpty() && keys.all { completedSets.containsKey(it) }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(if (roundIdx == currentRoundIndex) 8.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    done -> Color(0xFF66BB6A)
                                                    roundIdx == currentRoundIndex -> accent
                                                    else -> Color.White.copy(alpha = 0.28f)
                                                }
                                            ),
                                    )
                                }
                                if (memberRoundDone) {
                                    Text("✓", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF66BB6A))
                                }
                            }
                        }
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
        isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val borderColor = when {
        isCurrent -> accent
        isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.62f)
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
                                roundDone -> Color(0xFF66BB6A)
                                isRoundCurrent -> accent
                                else -> Color.White.copy(alpha = 0.10f)
                            },
                            border = BorderStroke(
                                width = if (isRoundCurrent || roundDone) 0.dp else 1.dp,
                                color = when {
                                    roundDone -> Color(0xFF66BB6A)
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
                                                            sideDone -> Color(0xFF66BB6A)
                                                            isSideCurrent -> accent
                                                            else -> Color.White.copy(alpha = 0.12f)
                                                        }
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = when {
                                                            sideDone -> Color(0xFF66BB6A)
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
                                                    if (roundDone) Color(0xFF66BB6A).copy(alpha = 0.7f)
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

    val cardColor = if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.06f)
    val borderColor = if (isCompleted) Color(0xFF66BB6A).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
    val accentColor = if (isCompleted) Color(0xFF66BB6A) else sessionAccentColor

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
