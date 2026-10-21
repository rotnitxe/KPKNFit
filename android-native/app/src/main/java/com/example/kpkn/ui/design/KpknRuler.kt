package com.example.kpkn.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import kotlin.math.roundToInt

/**
 * Regla de valores enteros de KPKN (KPKN_UI_V2): sustituye al `Slider` de Material. Una línea fina con una marca por valor, la parte
 * recorrida en tinta y un pulgar de 20 dp. Sin estado de negocio: [value] lo pone quien la usa y [onValueChange] recibe cada valor nuevo.
 * Tocar o arrastrar la lleva al valor más cercano dentro de [range]. Su zona táctil mide 48 dp de alto y se anuncia como un ajustable:
 * [label] es su nombre y [valueText] dice el valor («RPE 7»).
 */
@Composable
fun KpknRuler(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    valueText: (Int) -> String = { it.toString() },
) {
    val first = range.first
    val last = range.last
    val steps = (last - first).coerceAtLeast(1)
    val current = value.coerceIn(first, last)
    var widthPx by remember { mutableStateOf(0f) }
    val latestOnChange by rememberUpdatedState(onValueChange)

    // Valor más cercano al punto x de la regla. Antes de medirla, devuelve el valor actual.
    fun valueAt(x: Float): Int {
        if (widthPx <= 0f) return current
        val fraction = (x / widthPx).coerceIn(0f, 1f)
        return (first + (fraction * steps).roundToInt()).coerceIn(first, last)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(first, last) {
                detectTapGestures { offset -> latestOnChange(valueAt(offset.x)) }
            }
            .pointerInput(first, last) {
                detectDragGestures(
                    onDragStart = { offset -> latestOnChange(valueAt(offset.x)) },
                    onDrag = { change, _ ->
                        change.consume()
                        latestOnChange(valueAt(change.position.x))
                    },
                )
            }
            .semantics {
                contentDescription = label
                stateDescription = valueText(current)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = current.toFloat(),
                    range = first.toFloat()..last.toFloat(),
                    steps = steps - 1,
                )
                setProgress { target ->
                    val next = target.roundToInt().coerceIn(first, last)
                    if (next != current) latestOnChange(next)
                    true
                }
            },
    ) {
        val centerY = size.height / 2f
        val lineWidth = 2.dp.toPx()
        val thumbRadius = 10.dp.toPx()
        // Margen para que el pulgar de los extremos no se corte.
        val inset = thumbRadius
        val usable = size.width - 2f * inset
        fun xOf(index: Int): Float = inset + usable * (index.toFloat() / steps)
        val thumbX = xOf(current - first)

        drawLine(
            color = WizardColors.glassBorder,
            start = Offset(inset, centerY),
            end = Offset(size.width - inset, centerY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = WizardColors.text,
            start = Offset(inset, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        for (index in 0..steps) {
            val x = xOf(index)
            drawLine(
                color = WizardColors.textFaint,
                start = Offset(x, centerY - 6.dp.toPx()),
                end = Offset(x, centerY + 6.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        // Un anillo del color del fondo separa el pulgar de las marcas.
        drawCircle(color = WizardColors.background, radius = thumbRadius + 2.dp.toPx(), center = Offset(thumbX, centerY))
        drawCircle(color = WizardColors.text, radius = thumbRadius, center = Offset(thumbX, centerY))
    }
}
