@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.kpkn.screens.myrings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.screens.home.batteryColor

private data class MuscleGroupDef(val label: String, val muscles: List<String>)

private val MUSCLE_GROUPS = listOf(
    MuscleGroupDef("Pecho",    listOf("Pectorales")),
    MuscleGroupDef("Espalda",  listOf("Dorsales", "Trapecio", "Erectores Espinales")),
    MuscleGroupDef("Hombros",  listOf("Deltoides")),
    MuscleGroupDef("Brazos",   listOf("Biceps", "Triceps", "Antebrazo", "Bíceps", "Tríceps")),
    MuscleGroupDef("Core",     listOf("Abdomen", "Core")),
    MuscleGroupDef("Piernas",  listOf("Cuadriceps", "Cuádriceps", "Isquiosurales", "Gluteos", "Glúteos", "Pantorrillas", "Aductores")),
)

@Composable
fun MuscleBreakdownSection(
    perMuscle: Map<String, MuscleRecoveryStatus>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val activeGroups = remember(perMuscle) {
        MUSCLE_GROUPS.mapNotNull { group ->
            val scores = group.muscles.mapNotNull { perMuscle[it]?.recoveryScore }
            if (scores.isEmpty()) null else group
        }
    }
    val visibleGroups = if (expanded) activeGroups else activeGroups.take(4)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DESGLOSE MUSCULAR",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            if (activeGroups.size > 4) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                    modifier = Modifier.clickable { expanded = !expanded },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (expanded) "Colapsar" else "Ver todo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (visibleGroups.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
            ) {
                Text(
                    text = "No hay datos musculares disponibles por ahora.",
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2,
        ) {
            visibleGroups.forEach { group ->
                MuscleGroupCard(
                    group = group,
                    perMuscle = perMuscle,
                )
            }
        }
    }
}

@Composable
private fun MuscleGroupCard(
    group: MuscleGroupDef,
    perMuscle: Map<String, MuscleRecoveryStatus>,
) {
    val groupScores = group.muscles.mapNotNull { perMuscle[it]?.recoveryScore }
    val groupAvg = if (groupScores.isEmpty()) 100 else groupScores.average().toInt()
    val groupColor = batteryColor(groupAvg)

    Surface(
        modifier = Modifier.widthIn(min = 155.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$groupAvg%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = groupColor,
                )
            }

            group.muscles.take(3).forEach { muscle ->
                val status = perMuscle[muscle]
                val score = status?.recoveryScore ?: 100
                val hoursLeft = status?.hoursToRecovery ?: 0
                val barColor = batteryColor(score)

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            muscle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (hoursLeft > 0) {
                                Text(
                                    "${hoursLeft}h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                "$score%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = barColor,
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50)),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    )
                }
            }
        }
    }
}
