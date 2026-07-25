package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import kotlin.math.roundToInt

internal val PART_COLORS = listOf("#00F0FF", "#3B82F6", "#00F19F", "#A855F7", "#EAB308", "#F43F5E", "#06B6D4", "#8B5CF6")

@Composable
internal fun DragLiftPreview(
    exercise: Exercise,
    rect: Rect,
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Surface(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (rect.left + offset.x).roundToInt(),
                    y = (rect.top + offset.y).roundToInt(),
                )
            }
            .width(with(density) { rect.width.toDp() })
            .heightIn(min = 70.dp),
        shape = RoundedCornerShape(20.dp),
        color = DarkEditorSurface.copy(alpha = 0.98f),
        shadowElevation = 28.dp,
        tonalElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name.ifBlank { "Ejercicio" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.sets.size} series · ${trainingModeLabel(exercise.trainingMode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DropGapProjection(
    visible: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)) + expandVertically(animationSpec = tween(140)),
        exit = fadeOut(tween(80)) + shrinkVertically(animationSpec = tween(120)),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            color = accentColor.copy(alpha = 0.14f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.48f),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                            ),
                            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Soltar aquí",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                )
            }
        }
    }
}

internal val DarkEditorSurface = Color(0xE61B1B20)

internal val DarkEditorSurfaceSoft = Color(0xB8232329)

internal val DarkEditorChip = Color(0xFF2A2A31)

internal val DarkEditorChipSelected = Color(0xFF333A42)

@Composable
internal fun SheetHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DarkChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) DarkEditorChipSelected else DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

