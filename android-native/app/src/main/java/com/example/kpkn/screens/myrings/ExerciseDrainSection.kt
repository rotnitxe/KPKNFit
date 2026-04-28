package com.example.kpkn.screens.myrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ExerciseDrainRanking
import kotlin.math.roundToInt

@Composable
fun ExerciseDrainSection(
    rankings: List<ExerciseDrainRanking>,
    selectedFilter: DrainFilter,
    onFilterSelected: (DrainFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "EJERCICIOS QUE MÁS DRENAN",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DrainFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter
                val chipColor = when (filter) {
                    DrainFilter.GENERAL  -> MaterialTheme.colorScheme.primary
                    DrainFilter.MUSCULAR -> Color(0xFFFF5252)
                    DrainFilter.CNS      -> Color(0xFF448AFF)
                    DrainFilter.COLUMNA  -> Color(0xFFFFD740)
                }
                val chipLabel = when (filter) {
                    DrainFilter.GENERAL  -> "General"
                    DrainFilter.MUSCULAR -> "Muscular"
                    DrainFilter.CNS      -> "CNC"
                    DrainFilter.COLUMNA  -> "Columna"
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(filter) },
                    label = {
                        Text(
                            chipLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                            fontSize = 11.sp,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.15f),
                        selectedLabelColor = chipColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = chipColor.copy(alpha = 0.4f),
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        if (rankings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sin ejercicios registrados aún.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            return@Column
        }

        val maxDrain = when (selectedFilter) {
            DrainFilter.GENERAL  -> rankings.maxOfOrNull { it.overallDrain } ?: 1.0
            DrainFilter.MUSCULAR -> rankings.maxOfOrNull { it.muscularDrain } ?: 1.0
            DrainFilter.CNS      -> rankings.maxOfOrNull { it.cnsDrain } ?: 1.0
            DrainFilter.COLUMNA  -> rankings.maxOfOrNull { it.spinalDrain } ?: 1.0
        }.coerceAtLeast(0.01)

        rankings.take(10).forEachIndexed { index, exercise ->
            val drainValue = when (selectedFilter) {
                DrainFilter.GENERAL  -> exercise.overallDrain
                DrainFilter.MUSCULAR -> exercise.muscularDrain
                DrainFilter.CNS      -> exercise.cnsDrain
                DrainFilter.COLUMNA  -> exercise.spinalDrain
            }
            val barFraction = (drainValue / maxDrain).toFloat().coerceIn(0f, 1f)
            val barColor = when (selectedFilter) {
                DrainFilter.GENERAL  -> MaterialTheme.colorScheme.primary
                DrainFilter.MUSCULAR -> Color(0xFFFF5252)
                DrainFilter.CNS      -> Color(0xFF448AFF)
                DrainFilter.COLUMNA  -> Color(0xFFFFD740)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(18.dp),
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            exercise.exerciseName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${(drainValue * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = barColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                            )
                            Text(
                                "· ${exercise.sessionCount}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { barFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    )
                }
            }
        }
    }
}
