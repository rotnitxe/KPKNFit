package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType

/**
 * Píldora del descanso minimizado (KPKN LS3 §2): «1:45 · Descanso» en `bodyStrong` tabular, con un arco fino de progreso, sobre el
 * vidrio v2 de isla. Tocarla vuelve al descanso ([onOpen]). Sin estado.
 */
@Composable
fun WorkoutRestPill(
    remainingSeconds: Int,
    totalSeconds: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = LocalHazeState.current
    val remaining = remainingSeconds.coerceAtLeast(0)
    val progress = (remaining.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    val clock = formatWorkoutRestClock(remaining)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .kpknGlassOrFallback(hazeState, KpknShape.pill, variant = KpknGlassVariant.ISLAND)
            .clickable(role = Role.Button, onClickLabel = "Volver al descanso", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Descanso, quedan $clock. Volver al descanso"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val stroke = 2.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(
                color = WizardColors.glassBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = SymbolPalette.columna,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "$clock · Descanso",
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.text,
            maxLines = 1,
        )
    }
}
