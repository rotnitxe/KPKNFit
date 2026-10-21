package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.RestClockMath
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Lo que la pantalla de la sesión necesita saber del chip del descanso (LV6, F5): si debe verse y adónde lleva al tocarlo. Lo rellena
 * el paginador (que sabe qué página está a la vista) y lo lee quien dibuja la banda de arriba.
 */
@Stable
internal class RestChipHolder {
    var visible by mutableStateOf(false)

    /** Vuelve a la página del descanso. */
    var onOpen: () -> Unit = {}
}

/**
 * El chip compacto «Descanso 2:41 · +15 s» (F5): con el descanso minimizado y otra página a la vista, el descanso sigue arriba. Un aro
 * pequeño con el progreso, la palabra, el tiempo que queda y «+15 s». Tocar el chip vuelve a la página del descanso; «+15 s» suma 15 s
 * con la misma función de siempre. Mide 40 dp dentro de una zona táctil de 48 dp.
 */
@Composable
internal fun RestMiniChip(
    remainingSeconds: Int,
    totalSeconds: Int,
    onOpen: () -> Unit,
    onAddTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val progress = RestClockMath.progress(totalSeconds.coerceAtLeast(safeRemaining).coerceAtLeast(1), safeRemaining)
    val open = remember { MutableInteractionSource() }
    val add = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(
                interactionSource = open,
                indication = null,
                role = Role.Button,
                onClickLabel = "Abrir el descanso",
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(KpknShape.pill)
                .background(KpknSurface.module, KpknShape.pill)
                .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniRing(progress = progress, modifier = Modifier.size(32.dp))
            Text(
                text = "Descanso",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                modifier = Modifier.semantics {
                    contentDescription = RestClockMath.spokenRemaining(safeRemaining)
                },
            )
            Text(
                text = KpknSetFormat.rest(safeRemaining),
                style = KpknType.metricM,
                color = WizardColors.text,
                maxLines = 1,
            )
            // «+15 s»: pastilla de 28 dp dentro de una zona táctil de 48 × 40 dp (el alto del chip).
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 56.dp)
                    .height(40.dp)
                    .clickable(
                        interactionSource = add,
                        indication = null,
                        role = Role.Button,
                        onClick = onAddTime,
                    )
                    .semantics { contentDescription = "Sumar 15 segundos" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(KpknShape.pill)
                        .background(KpknSurface.inset, KpknShape.pill)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+15 s",
                        style = KpknType.chip,
                        color = WizardColors.text,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** El aro de 32 dp del chip: pista gris y el progreso del descanso en el color de Músculos, desde arriba y en sentido horario. */
@Composable
private fun MiniRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color(0xFF2A2A2A),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        if (progress > 0.002f) {
            drawArc(
                color = SymbolPalette.musculo,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
