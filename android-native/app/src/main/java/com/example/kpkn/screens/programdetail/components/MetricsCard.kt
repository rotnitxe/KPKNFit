package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TrendDirection { UP, DOWN, STABLE }

@Composable
fun MetricsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trend: TrendDirection? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = accentColor)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Value
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                )
                if (trend != null) {
                    Spacer(Modifier.width(8.dp))
                    TrendBadge(trend)
                }
            }

            // Subtitle
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun TrendBadge(trend: TrendDirection) {
    val (symbol, color) = when (trend) {
        TrendDirection.UP -> "\u2191" to Color(0xFF10B981)
        TrendDirection.DOWN -> "\u2193" to Color(0xFFEF4444)
        TrendDirection.STABLE -> "\u2192" to Color(0xFF9CA3AF)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(symbol, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun MetricsRow(
    metrics: List<Triple<String, String, TrendDirection?>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        metrics.forEach { (label, value, trend) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    if (trend != null) {
                        Spacer(Modifier.width(3.dp))
                        TrendBadge(trend)
                    }
                }
                Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
