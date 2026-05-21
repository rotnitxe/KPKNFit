package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.workout.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

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
    completedSets: Map<String, CompletedSet>,
    onSelect: (Int) -> Unit,
    onSelectGroup: (String) -> Unit = {},
    onOpenContext: (String) -> Unit = {},
    enableLongPress: Boolean = true,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val isKeyboardVisible = WindowInsets.isImeVisible
    var localMode by remember { mutableStateOf(RoadmapMode.COMPACT) }

    // Enforce COMPACT mode when virtual keyboard is active to prevent overlapping conflicts
    val activeMode = if (isKeyboardVisible) RoadmapMode.COMPACT else localMode

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

    // Local helper to guess muscle group names from exercise names
    fun guessMuscleGroup(exerciseName: String, partName: String?): String {
        val nameLower = exerciseName.lowercase()
        return when {
            nameLower.contains("press") || nameLower.contains("pecho") || nameLower.contains("bench") || nameLower.contains("pecho") -> "PECHO"
            nameLower.contains("sentadilla") || nameLower.contains("squat") || nameLower.contains("quad") || nameLower.contains("prensa") || nameLower.contains("extensi") || nameLower.contains("zancada") || nameLower.contains("búlgar") -> "CUÁDRICEPS"
            nameLower.contains("peso muerto") || nameLower.contains("deadlift") || nameLower.contains("femoral") || nameLower.contains("curl fem") || nameLower.contains("isquio") || nameLower.contains("hip thrust") || nameLower.contains("glúteo") -> "FEMORAL/GLÚTEOS"
            nameLower.contains("dominadas") || nameLower.contains("pull up") || nameLower.contains("remo") || nameLower.contains("row") || nameLower.contains("espalda") || nameLower.contains("jalon") || nameLower.contains("lat ") -> "ESPALDA"
            nameLower.contains("militar") || nameLower.contains("hombro") || nameLower.contains("shoulder") || nameLower.contains("lateral raise") || nameLower.contains("vuelos") -> "HOMBROS"
            nameLower.contains("bicep") || nameLower.contains("tricep") || nameLower.contains("curl") || nameLower.contains("brazo") || nameLower.contains("copa") || nameLower.contains("fondos") -> "BRAZOS"
            nameLower.contains("abs") || nameLower.contains("plank") || nameLower.contains("core") || nameLower.contains("abdomen") || nameLower.contains("espin") -> "CORE"
            else -> partName?.uppercase() ?: "GENERAL"
        }
    }

    WorkoutGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = WorkoutUiTokens.CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
        hazeState = hazeState,
        glassStyle = HazeStyle(
            blurRadius = 26.dp,
            tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.34f)),
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f),
            noiseFactor = 0.02f,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Drag handle to toggle Compact vs Expanded mode (hidden if keyboard is active)
            if (!isKeyboardVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clickable {
                            localMode = if (localMode == RoadmapMode.COMPACT) RoadmapMode.EXPANDED else RoadmapMode.COMPACT
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
            } else {
                Spacer(Modifier.height(8.dp))
            }

            if (activeMode == RoadmapMode.COMPACT) {
                // COMPACT MODE: Horizontal LazyRow (traditional carousel)
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
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
                        val partName = part?.name?.takeIf { it.isNotBlank() }

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
                                onClick = { onSelect(idx) },
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
                                onClick = { onSelectGroup(group.groupId) },
                                onLongClick = if (enableLongPress) ({ onOpenContext(exercise.id) }) else null,
                            )
                        }
                    }
                }
            } else {
                // EXPANDED MODE: Grouped muscular cockpit grid view
                val groupedByMuscle = remember(roadmapGroups, parts) {
                    roadmapGroups.groupBy { group ->
                        val exercise = group.exercises.firstOrNull()
                        val part = parts.firstOrNull { it.exercises.any { e -> e.id == exercise?.id } }
                        guessMuscleGroup(exercise?.name.orEmpty(), part?.name)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cockpit de Entrenamiento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val totalExercisesCount = exercises.size
                        val totalCompletedCount = exercises.sumOf { e ->
                            e.sets.indices.sumOf { sIdx ->
                                e.completionKeysForSet(sIdx).count { completedSets.containsKey(it) }
                            }
                        }
                        val totalSetsCount = exercises.sumOf { e ->
                            e.sets.indices.sumOf { e.completionKeysForSet(it).size }
                        }

                        Surface(
                            shape = WorkoutUiTokens.ChipShape,
                            color = sessionAccentColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Progreso total: $totalCompletedCount/$totalSetsCount",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = sessionAccentColor
                            )
                        }
                    }

                    groupedByMuscle.forEach { (muscle, groups) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = WorkoutUiTokens.InnerCardShape,
                            color = WorkoutUiTokens.setInnerColor(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Group Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = muscle,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )

                                    val groupDoneSets = groups.sumOf { g ->
                                        g.exercises.sumOf { e ->
                                            e.sets.indices.sumOf { sIdx ->
                                                e.completionKeysForSet(sIdx).count { completedSets.containsKey(it) }
                                            }
                                        }
                                    }
                                    val groupTotalSets = groups.sumOf { g ->
                                        g.exercises.sumOf { e ->
                                            e.sets.indices.sumOf { e.completionKeysForSet(it).size }
                                        }
                                    }

                                    Text(
                                        text = "$groupDoneSets/$groupTotalSets series",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (groupDoneSets == groupTotalSets && groupTotalSets > 0) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }

                                // Linear Progress bar for this muscle group
                                val groupDoneSets = groups.sumOf { g ->
                                    g.exercises.sumOf { e ->
                                        e.sets.indices.sumOf { sIdx ->
                                            e.completionKeysForSet(sIdx).count { completedSets.containsKey(it) }
                                        }
                                    }
                                }
                                val groupTotalSets = groups.sumOf { g ->
                                    g.exercises.sumOf { e ->
                                        e.sets.indices.sumOf { e.completionKeysForSet(it).size }
                                    }
                                }
                                val progress = if (groupTotalSets > 0) groupDoneSets.toFloat() / groupTotalSets.toFloat() else 0f
                                
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(WorkoutUiTokens.ChipShape),
                                    color = if (groupDoneSets == groupTotalSets && groupTotalSets > 0) Color(0xFF66BB6A) else sessionAccentColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )

                                Spacer(Modifier.height(2.dp))

                                // List of exercises in this group
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    groups.forEach { group ->
                                        val exercise = group.exercises.firstOrNull() ?: return@forEach
                                        val idx = exercises.indexOfFirst { it.id == exercise.id }.coerceAtLeast(0)
                                        val isCurrent = group.exercises.any { it.id == exercises.getOrNull(currentIdx)?.id }

                                        val exerciseCompleted = group.exercises.sumOf { member ->
                                            member.sets.indices.sumOf { sIdx ->
                                                member.completionKeysForSet(sIdx).count { completedSets.containsKey(it) }
                                            }
                                        }
                                        val exerciseTotal = group.exercises.sumOf { member ->
                                            member.sets.indices.sumOf { member.completionKeysForSet(it).size }
                                        }
                                        val isAllDone = exerciseCompleted >= exerciseTotal && exerciseTotal > 0

                                        val bgGrad = if (isCurrent) {
                                            Brush.horizontalGradient(
                                                colors = listOf(sessionAccentColor.copy(alpha = 0.22f), sessionAccentColor.copy(alpha = 0.08f))
                                            )
                                        } else {
                                            Brush.horizontalGradient(colors = listOf(Color.Transparent, Color.Transparent))
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(WorkoutUiTokens.InnerCardShape)
                                                .background(bgGrad)
                                                .clickable {
                                                    if (group.groupId != null && group.exercises.size > 1) {
                                                        onSelectGroup(group.groupId)
                                                    } else {
                                                        onSelect(idx)
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Indicator Dot
                                                Surface(
                                                    modifier = Modifier.size(8.dp),
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = when {
                                                        isCurrent -> sessionAccentColor
                                                        isAllDone -> Color(0xFF66BB6A)
                                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                                    }
                                                ) {}

                                                Text(
                                                    text = if (group.groupId != null && group.exercises.size > 1) "Superserie (${group.exercises.joinToString { it.name }})" else exercise.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Medium,
                                                    color = if (isCurrent) sessionAccentColor else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = if (isAllDone) "✓ Listo" else "$exerciseCompleted/$exerciseTotal sets",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    isAllDone -> Color(0xFF66BB6A)
                                                    isCurrent -> sessionAccentColor
                                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                }
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

private data class ExerciseRoadmapGroup(
    val groupId: String?,
    val exercises: List<Exercise>,
)

private fun Exercise.supersetGroupRefOrLegacyId(): String? =
    supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }

private fun Exercise.isEffectivelyUnilateral(): Boolean =
    unilateralMode != UnilateralMode.BILATERAL || isUnilateral

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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val nameLength = exercise.name.length
    val minWidth = when {
        nameLength > 30 -> 130.dp
        nameLength > 22 -> 110.dp
        else -> 88.dp
    }
    val containerColor = when {
        isCurrent -> accent.copy(alpha = 0.88f)
        isAllDone -> Color(0xFF1A3A1A)
        else -> accent.copy(alpha = 0.15f)
    }
    val contentColor = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isCurrent) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)

    // Interactive scale dynamic click feedback
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1.0f)

    Surface(
        modifier = Modifier
            .widthIn(min = minWidth, max = 170.dp)
            .heightIn(min = if (groupName != null) 60.dp else 48.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            },
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
                color = if (isCurrent) Color.White.copy(alpha = 0.16f) else accent.copy(alpha = 0.20f),
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
                    text = exercise.name,
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
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val safeRoundCount = roundCount.coerceAtLeast(1)
    val title = if (supersetCount > 1) "Superserie $supersetNumber" else "Superserie"

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1.0f)

    Surface(
        modifier = Modifier
            .widthIn(min = 214.dp, max = 280.dp)
            .heightIn(min = 68.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            },
        shape = WorkoutUiTokens.InnerCardShape,
        color = WorkoutUiTokens.setInnerColor(),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = when {
                isCurrent -> accent
                isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.62f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(min = 82.dp, max = 104.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
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
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = WorkoutUiTokens.InnerCardShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isCurrent) 0.2f else 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(safeRoundCount) { roundIdx ->
                        val roundKeys = exercises.flatMap { it.completionKeysForSet(roundIdx) }
                        val roundDone = roundKeys.isNotEmpty() && roundKeys.all { completedSets.containsKey(it) }
                        val isRoundCurrent = isCurrent && currentRound == roundIdx + 1
                        Surface(
                            modifier = Modifier.size(if (isRoundCurrent) 24.dp else 18.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = when {
                                isRoundCurrent -> accent
                                roundDone -> Color(0xFF66BB6A)
                                else -> Color.Transparent
                            },
                            border = BorderStroke(
                                width = if (isRoundCurrent) 0.dp else 1.4.dp,
                                color = when {
                                    roundDone -> Color(0xFF66BB6A)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                                },
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${roundIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isRoundCurrent) 10.sp else 9.sp),
                                    fontWeight = FontWeight.Black,
                                    color = if (isRoundCurrent || roundDone) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
