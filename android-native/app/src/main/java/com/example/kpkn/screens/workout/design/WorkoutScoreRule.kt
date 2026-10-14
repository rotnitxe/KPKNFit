package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import kotlin.math.roundToInt

/**
 * Regla de [range] (de 1 a 10 en el feedback del ejercicio) con una marca arrastrable (KPKN LS3 §3): sustituye al `Slider` de Material.
 * Diez marcas sobre una línea fina, y la marca del valor elegido en tinta. Tocar o arrastrar fija el valor; con TalkBack, la acción de
 * progreso lo sube y lo baja. Sin estado: [value] lo lleva quien la monta, y cada cambio llega a [onValueChange].
 */
@Composable
fun WorkoutScoreRule(
    value: Int,
    onValueChange: (Int) -> Unit,
    contentLabel: String,
    modifier: Modifier = Modifier,
    range: IntRange = 1..10,
) {
    val current = value.coerceIn(range)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = contentLabel
                stateDescription = "$current de ${range.last}"
                setProgress { fraction ->
                    val target = range.first + (fraction.coerceIn(0f, 1f) * (range.last - range.first)).roundToInt()
                    if (target != current) latestOnValueChange(target)
                    true
                }
            }
            .pointerInput(range) {
                val pad = 14.dp.toPx()
                detectTapGestures { offset ->
                    latestOnValueChange(workoutScoreValueAt(offset.x, size.width.toFloat(), pad, range))
                }
            }
            .pointerInput(range) {
                val pad = 14.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latestOnValueChange(workoutScoreValueAt(offset.x, size.width.toFloat(), pad, range))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestOnValueChange(workoutScoreValueAt(change.position.x, size.width.toFloat(), pad, range))
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 14.dp.toPx()
            val left = pad
            val right = size.width - pad
            val midY = size.height / 2f
            val steps = (range.last - range.first).coerceAtLeast(1)
            drawLine(
                color = WizardColors.divider,
                start = Offset(left, midY),
                end = Offset(right, midY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            for (i in 0..steps) {
                val x = left + (right - left) * i / steps
                drawLine(
                    color = WizardColors.textFaint,
                    start = Offset(x, midY - 6.dp.toPx()),
                    end = Offset(x, midY + 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val fraction = (current - range.first).toFloat() / steps
            drawCircle(
                color = WizardColors.text,
                radius = 9.dp.toPx(),
                center = Offset(left + (right - left) * fraction, midY),
            )
        }
    }
}

/**
 * Valor de [range] bajo la posición [x] (px) de una regla de [widthPx] de ancho, con el mismo margen [padPx] que el dibujo. Función pura.
 */
internal fun workoutScoreValueAt(x: Float, widthPx: Float, padPx: Float, range: IntRange): Int {
    val steps = range.last - range.first
    val span = widthPx - 2f * padPx
    if (steps <= 0 || span <= 0f) return range.first
    val fraction = ((x - padPx) / span).coerceIn(0f, 1f)
    return range.first + (fraction * steps).roundToInt()
}
