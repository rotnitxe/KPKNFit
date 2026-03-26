package com.example.kpkn.screens.programdetail.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.programdetail.AnalyticsSubTab
import com.example.kpkn.screens.programdetail.MainTab
import com.example.kpkn.screens.programdetail.StructureSubTab

data class SubTabItem(
    val label: String,
    val value: String,
)

@Composable
fun IntegratedTabs(
    activeMainTab: MainTab,
    onMainTabChange: (MainTab) -> Unit,
    activeSubTab: String,
    onSubTabChange: (String) -> Unit,
    isSimpleProgram: Boolean,
    modifier: Modifier = Modifier,
) {
    val isTraining = activeMainTab == MainTab.TRAINING

    val subTabs = remember(isTraining, isSimpleProgram) {
        if (isTraining) {
            buildList {
                add(SubTabItem("Semana", StructureSubTab.SEMANA.name))
                add(SubTabItem("Split", StructureSubTab.SPLIT.name))
                add(SubTabItem("Macrociclo", StructureSubTab.MACROCICLO.name))
                add(
                    if (isSimpleProgram) SubTabItem("Loops", StructureSubTab.LOOPS.name)
                    else SubTabItem("Protocolos", StructureSubTab.PROTOCOLOS.name)
                )
            }
        } else {
            listOf(
                SubTabItem("Volumen", AnalyticsSubTab.VOLUMEN.name),
                SubTabItem("Progreso", AnalyticsSubTab.PROGRESO.name),
                SubTabItem("Historiales", AnalyticsSubTab.HISTORIALES.name),
            )
        }
    }

    Column(modifier = modifier) {
        // ─── Main Tabs (Liquid Glass pill) ─────────────────────────────────
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = activeMainTab == tab
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            when (tab) {
                                MainTab.TRAINING  -> MaterialTheme.colorScheme.primary
                                MainTab.ANALYTICS -> MaterialTheme.colorScheme.tertiary
                            }
                        } else Color.Transparent,
                        label = "main-tab-bg",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(bgColor)
                            .clickable { onMainTabChange(tab) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (tab) {
                                MainTab.TRAINING  -> "Estructura"
                                MainTab.ANALYTICS -> "Analíticas"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Sub Tabs (contextual) ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                subTabs.forEach { tab ->
                    val isActive = activeSubTab == tab.value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.surface
                                else Color.Transparent
                            )
                            .clickable { onSubTabChange(tab.value) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Black else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
