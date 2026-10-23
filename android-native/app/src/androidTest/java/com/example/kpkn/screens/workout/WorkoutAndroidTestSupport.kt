package com.example.kpkn.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.screens.workout.components.RoadmapMode
import com.example.kpkn.screens.workout.components.WorkoutRoadmapBar

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kpkn.data.models.SessionPart

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UnifiedExerciseCarousel(
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
) {
    // The historical helper now drives the same integrated line and route as production.
    var route by remember { mutableStateOf<LiveSessionDestination?>(null) }
    val selectExercise: (String) -> Unit = { id -> exercises.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(onSelect) }
    LiveSessionLine(exercises, exercises.getOrNull(currentIdx)?.id, null,
        { route = LiveSessionDestination.ROUTE }, {}, selectExercise, { if (enableLongPress) onOpenContext(it) }, {}, false, false,
        supersetGroups = supersetGroups, onSelectSuperset = onSelectGroup)
    LiveSessionSheetsHost(route, { route = it }, exercises = exercises, completedSets = completedSets,
        currentExerciseId = exercises.getOrNull(currentIdx)?.id, supersetGroups = supersetGroups,
        onSelectExercise = selectExercise, onSelectSuperset = onSelectGroup, onOpenExerciseOptions = onOpenContext)
}





@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExerciseRoadmapCard(
    exercise: Exercise,
    completedCount: Int,
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
        else -> accent.copy(alpha = 0.18f)
    }
    val contentColor = if (isCurrent) Color.White else Color.White.copy(alpha = 0.90f)
    val borderColor = if (isCurrent) Color.Transparent else Color.White.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier
            .widthIn(min = minWidth, max = 170.dp)
            .heightIn(min = if (groupName != null) 60.dp else 46.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isCurrent) Color.White.copy(alpha = 0.16f) else accent.copy(alpha = 0.20f),
            ) {
                Text(
                    text = if (isAllDone) "✓" else "$completedCount/${exercise.sets.size}",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                if (!groupName.isNullOrBlank()) {
                    Text(
                        text = groupName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SupersetRoadmapCard(
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

    Surface(
        modifier = Modifier
            .widthIn(min = 214.dp, max = 280.dp)
            .heightIn(min = 68.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101010),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = when {
                isCurrent -> accent
                isAllDone -> Color(0xFF66BB6A).copy(alpha = 0.62f)
                else -> Color.White.copy(alpha = 0.12f)
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
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isAllDone) "Completada" else currentRound?.let { "Ronda $it/$safeRoundCount" } ?: "$safeRoundCount rondas",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) accent else Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(15.dp),
                color = Color.White.copy(alpha = if (isCurrent) 0.13f else 0.07f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
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
                                    else -> Color.White.copy(alpha = 0.42f)
                                },
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${roundIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isRoundCurrent) 10.sp else 9.sp),
                                    fontWeight = FontWeight.Black,
                                    color = if (isRoundCurrent || roundDone) Color.Black else Color.White.copy(alpha = 0.70f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
