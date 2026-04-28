package com.example.kpkn.screens.myrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.SessionDrainRanking
import kotlin.math.roundToInt

private val COLOR_CNC  = Color(0xFF448AFF)
private val COLOR_MUS  = Color(0xFFFF5252)
private val COLOR_SPI  = Color(0xFFFFD740)

@Composable
fun SessionRankingsSection(
    rankings: List<SessionDrainRanking>,
    selectedTab: RankingsTab,
    onTabSelected: (RankingsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "RANKINGS DE SESIONES",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )

        // Tab row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RankingsTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tabColor = when (tab) {
                    RankingsTab.GENERAL -> MaterialTheme.colorScheme.primary
                    RankingsTab.ENERGIA -> COLOR_CNC
                    RankingsTab.COLUMNA -> COLOR_SPI
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    label = {
                        Text(
                            when (tab) {
                                RankingsTab.GENERAL -> "General"
                                RankingsTab.ENERGIA -> "Energia"
                                RankingsTab.COLUMNA -> "Columna"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tabColor.copy(alpha = 0.15f),
                        selectedLabelColor = tabColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = tabColor.copy(alpha = 0.4f),
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                )
            }
        }

        if (rankings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sin sesiones registradas aún.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            return@Column
        }

        val maxDrain = when (selectedTab) {
            RankingsTab.GENERAL -> rankings.maxOfOrNull { it.totalDrain } ?: 1.0
            RankingsTab.ENERGIA -> rankings.maxOfOrNull { it.cnsDrain } ?: 1.0
            RankingsTab.COLUMNA -> rankings.maxOfOrNull { it.spinalDrain } ?: 1.0
        }.coerceAtLeast(0.01)

        rankings.forEachIndexed { index, ranking ->
            val drainValue = when (selectedTab) {
                RankingsTab.GENERAL -> ranking.totalDrain
                RankingsTab.ENERGIA -> ranking.cnsDrain
                RankingsTab.COLUMNA -> ranking.spinalDrain
            }
            val barFraction = (drainValue / maxDrain).toFloat().coerceIn(0f, 1f)
            val barColor = when (selectedTab) {
                RankingsTab.GENERAL -> MaterialTheme.colorScheme.primary
                RankingsTab.ENERGIA -> COLOR_CNC
                RankingsTab.COLUMNA -> COLOR_SPI
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Rank badge
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "#${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = barColor,
                            fontSize = 11.sp,
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                ranking.sessionName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                ranking.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                            )
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

                        // Mini stats row
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MiniStatChip("Mús. ${(ranking.muscularDrain * 100).roundToInt()}%", COLOR_MUS)
                            MiniStatChip("CNC ${(ranking.cnsDrain * 100).roundToInt()}%", COLOR_CNC)
                            MiniStatChip("Col. ${(ranking.spinalDrain * 100).roundToInt()}%", COLOR_SPI)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatChip(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.8f),
        fontSize = 9.sp,
    )
}
