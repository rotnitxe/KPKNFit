package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*

@Composable
fun UnilateralIntensityModeSelector(
    mode: UnilateralIntensityMode,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isShared = mode == UnilateralIntensityMode.SHARED
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(KpknSetEditorTokens.ChipShape)
            .clickable { onClick() },
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isShared) Icons.Default.Link else Icons.Default.LinkOff,
                contentDescription = if (isShared) "L/R compartido" else "Lados independientes",
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (isShared) "L/R compartido" else "Lados independientes",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
    }
}

@Composable
fun SideTargetRow(
    label: String,
    sideColor: Color,
    metricLabel: String,
    metricValue: String,
    intensityLabel: String?,
    intensityValue: String?,
    isAmrapMode: Boolean,
    onMetricChange: (String) -> Unit,
    onIntensityChange: (String) -> Unit,
    onRemoveSide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KpknSetEditorTokens.FieldGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = sideColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, sideColor.copy(alpha = 0.35f)),
            modifier = Modifier
                .width(48.dp)
                .height(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = sideColor
                )
            }
        }
        
        CompactNumericField(
            label = metricLabel,
            value = metricValue,
            modifier = Modifier.weight(1f),
            onValueChange = onMetricChange
        )
        
        if (!isAmrapMode) {
            CompactNumericField(
                label = intensityLabel ?: "Intens.",
                value = intensityValue.orEmpty(),
                modifier = Modifier.weight(1f),
                onValueChange = onIntensityChange
            )
        }
        
        IconButton(onClick = onRemoveSide, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Quitar lado",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun UnilateralAddGhostRow(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Añadir lado $label",
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Añadir $label",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }
        }
    }
}

@Composable
fun UnilateralSetContent(
    state: SetEditorCardState,
    hasLeftTarget: Boolean,
    hasRightTarget: Boolean,
    leftMetricValue: String,
    leftIntensityValue: String,
    rightMetricValue: String,
    rightIntensityValue: String,
    density: SetCardDensity,
    accentColor: Color,
    isLinked: Boolean,
    onToggleLink: () -> Unit,
    onAddLeft: () -> Unit,
    onAddRight: () -> Unit,
    onRemoveLeft: () -> Unit,
    onRemoveRight: () -> Unit,
    onLeftAction: (SetEditorAction) -> Unit,
    onRightAction: (SetEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasLeftTarget || hasRightTarget) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLinked) accentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onToggleLink() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isLinked) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = "Vincular",
                            tint = if (isLinked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isLinked) "Vinculados" else "Separados",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLinked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (hasLeftTarget) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "L",
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp).width(16.dp)
                )
                SetPrimaryInputs(
                    state = state.copy(
                        metricValue = leftMetricValue,
                        intensityValue = leftIntensityValue,
                        sideMode = SetSideMode.LEFT
                    ),
                    density = density,
                    accentColor = Color(0xFF2196F3),
                    onAction = onLeftAction,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemoveLeft) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            UnilateralAddGhostRow(
                label = "Lado Izquierdo",
                accentColor = Color(0xFF2196F3),
                onClick = onAddLeft
            )
        }

        if (hasRightTarget) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "R",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp).width(16.dp)
                )
                SetPrimaryInputs(
                    state = state.copy(
                        metricValue = rightMetricValue,
                        intensityValue = rightIntensityValue,
                        sideMode = SetSideMode.RIGHT
                    ),
                    density = density,
                    accentColor = Color(0xFFFF5252),
                    onAction = onRightAction,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemoveRight) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            UnilateralAddGhostRow(
                label = "Lado Derecho",
                accentColor = Color(0xFFFF5252),
                onClick = onAddRight
            )
        }
    }
}

@Composable
fun SupersetRoundCard(
    roundNumber: Int,
    accentColor: Color,
    restBetween: Int,
    restAfter: Int,
    onRemoveRound: () -> Unit,
    onRestClick: () -> Unit,
    canRemoveRound: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.width(320.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ronda $roundNumber",
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = onRemoveRound,
                    enabled = canRemoveRound,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar ronda",
                        tint = if (canRemoveRound) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRestClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Descanso: ${restBetween}s / post-ronda: ${restAfter}s",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                      )
                  }
              }
              
              Column(
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                  content = content
              )
          }
      }
  }

@Composable
fun CompactSupersetSetRow(
    exerciseName: String,
    state: SetEditorCardState,
    accentColor: Color,
    onAction: (SetEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            SetPrimaryInputs(
                state = state,
                density = SetCardDensity.SupersetCompact,
                accentColor = accentColor,
                onAction = onAction
            )
        }
    }
}
