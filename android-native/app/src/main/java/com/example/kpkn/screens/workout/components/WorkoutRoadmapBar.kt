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

    val roadmapGroups = remember(exercises) {
        val emitted = mutableSetOf<String>()
        exercises.mapNotNull { exercise ->
            val groupId = exercise.supersetGroupRefOrLegacyId()
            when {
                groupId == null -> ExerciseRoadmapGroup(null, listOf(exercise))
                emitted.add(groupId) -> ExerciseRoadmapGroup(
                    groupId = groupId,
                    exercises = exercises.filter { it.supersetGroupRefOrLegacyId() == groupId },
                )
                else -> null
            }
        }
    }

    LaunchedEffect(currentIdx, roadmapGroups.size) {
        val currentExerciseId = exercises.getOrNull(currentIdx)?.id
        val currentGroupIdx = roadmapGroups.indexOfFirst { group ->
            group.exercises.any { it.id == currentExerciseId }
        }
        if (currentGroupIdx >= 0) {
            listState.scrollToItem((currentGroupIdx - 1).coerceAtLeast(0))
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
                    count = roadmapGroups.size,
                    key = { groupIdx ->
                        val group = roadmapGroups[groupIdx]
                        group.groupId ?: group.exercises.firstOrNull()?.id ?: "group-$groupIdx"
                    },
                ) { groupIdx ->
                    val group = roadmapGroups[groupIdx]
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

                    if (group.groupId == null || group.exercises.size == 1) {
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
                        )
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
            .heightIn(min = if (groupName != null || isUnilateral) 64.dp else 48.dp)
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
                if (isUnilateral && exercise.sets.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exercise.sets.indices.forEach { setIdx ->
                            val expectedSides = exercise.expectedSidesForSet(setIdx)
                            val isSetCurrent = isCurrent && currentSetIdx == setIdx
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.25f),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (isSetCurrent) accent else Color.White.copy(alpha = 0.15f)
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    expectedSides.forEach { side ->
                                        val sideKey = "${exercise.id}_${setIdx}_${side.take(1).uppercase()}"
                                        val isDone = completedSets.containsKey(sideKey)
                                        val isSideCurrent = isSetCurrent &&
                                            (currentSide == null || currentSide.equals(side, ignoreCase = true))
                                        val label = side.take(1).uppercase()
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    onSelectStep(setIdx, side.lowercase())
                                                }
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isDone -> Color(0xFF66BB6A)
                                                        isSideCurrent -> accent
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    0.8.dp,
                                                    if (isDone) Color(0xFF66BB6A) else if (isSideCurrent) accent else Color.White.copy(alpha = 0.4f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                fontWeight = FontWeight.Black,
                                                        color = if (isDone || isSideCurrent) Color.Black else Color.White,
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
) {
    val safeRoundCount = roundCount.coerceAtLeast(1)
    val title = if (supersetCount > 1) "Superserie $supersetNumber" else "Superserie"

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1.0f)

    Surface(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 340.dp)
            .heightIn(min = 68.dp)
            .scale(scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = WorkoutUiTokens.InnerCardShape,
        color = Color.White.copy(alpha = if (isCurrent) 0.18f else 0.14f),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = when {
                isCurrent -> accent
                isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.62f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(min = 72.dp, max = 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isAllDone) "Completada" else currentRound?.let { "Ronda $it/$safeRoundCount" } ?: "$safeRoundCount rondas",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!groupName.isNullOrBlank()) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                shape = WorkoutUiTokens.InnerCardShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isCurrent) 0.30f else 0.18f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(safeRoundCount) { roundIdx ->
                        val roundKeys = exercises.flatMap { it.completionKeysForSet(roundIdx) }
                        val roundDone = roundKeys.isNotEmpty() && roundKeys.all { completedSets.containsKey(it) }
                        val isRoundCurrent = isCurrent && currentRound == roundIdx + 1

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // R1 / R2 badge
                            Surface(
                                modifier = Modifier.size(if (isRoundCurrent) 20.dp else 16.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = when {
                                    roundDone -> Color(0xFF66BB6A)
                                    isRoundCurrent -> accent
                                    else -> Color.Transparent
                                },
                                border = BorderStroke(
                                    width = if (isRoundCurrent) 0.dp else 1.2.dp,
                                    color = when {
                                        roundDone -> Color(0xFF66BB6A)
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                                    },
                                ),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "R${roundIdx + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isRoundCurrent) 9.sp else 8.sp),
                                        fontWeight = FontWeight.Black,
                                        color = if (isRoundCurrent || roundDone) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                                    )
                                }
                            }

                            // Exercise nodes in this round. A unilateral member
                            // exposes independent L/R subnodes and each node
                            // navigates directly to its canonical step key.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                exercises.filter { roundIdx in it.sets.indices }.forEachIndexed { visibleExIdx, ex ->
                                    val exKeys = ex.completionKeysForSet(roundIdx)
                                    val exDone = exKeys.isNotEmpty() && exKeys.all { completedSets.containsKey(it) }
                                    val isExCurrent = isRoundCurrent && currentExerciseId == ex.id
                                    val exLetter = ('A'.code + exercises.indexOf(ex)).toChar().toString()
                                    val sides: List<String?> = if (ex.isEffectivelyUnilateral()) {
                                        WorkoutStepRules.workingSidesForSet(ex, roundIdx)
                                    } else {
                                        listOf(null)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .widthIn(min = 16.dp, max = 42.dp)
                                            .height(30.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = exLetter,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                fontWeight = FontWeight.Black,
                                                color = if (isExCurrent) accent else Color.White.copy(alpha = 0.75f),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(18.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                sides.forEach { side ->
                                                    val sideKey = side?.let { "${ex.id}_${roundIdx}_${it.take(1).uppercase()}" }
                                                    val sideDone = sideKey?.let(completedSets::containsKey) ?: exDone
                                                    val isSideCurrent = isExCurrent &&
                                                        (currentSide == null || currentSide.equals(side, ignoreCase = true))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clickable { onSelectStep(ex.id, roundIdx, side) }
                                                            .clip(CircleShape)
                                                            .background(
                                                                when {
                                                                    sideDone -> Color(0xFF66BB6A)
                                                                    isSideCurrent -> accent
                                                                    else -> Color.Transparent
                                                                }
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = when {
                                                                    sideDone -> Color(0xFF66BB6A)
                                                                    isSideCurrent -> accent
                                                                    else -> Color.White.copy(alpha = 0.35f)
                                                                },
                                                                shape = CircleShape,
                                                            ),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = side?.take(1)?.uppercase() ?: exLetter,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                            fontWeight = FontWeight.Black,
                                                            color = if (sideDone || isSideCurrent) Color.Black else Color.White.copy(alpha = 0.75f),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (visibleExIdx < exercises.count { roundIdx in it.sets.indices } - 1) {
                                        Column(
                                            modifier = Modifier
                                                .width(8.dp)
                                                .height(30.dp),
                                        ) {
                                            Spacer(Modifier.height(12.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(18.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(
                                                            if (roundDone) Color(0xFF66BB6A).copy(alpha = 0.7f)
                                                            else Color.White.copy(alpha = 0.3f)
                                                        )
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
