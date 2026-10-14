package com.example.kpkn.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * Regla horizontal de 0 a 100 (KPKN_UI_V2): pista de 4 dp en tinta al 12 %, la de los RINGS; relleno en [color] hasta [value] y un
 * pulgar de [color] de 20 dp. Se ajusta con un toque o arrastrando en horizontal, y su zona táctil mide 48 dp de alto. Sin estado: el
 * valor llega por [value] y cada cambio sale por [onValueChange]. Para TalkBack es un ajustable: [contentDescription] da el nombre y
 * el valor va en `stateDescription`, como en la torre.
 */
@Composable
fun KpknPercentRule(
    value: Int,
    onValueChange: (Int) -> Unit,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val clamped = value.coerceIn(0, 100)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var widthPx by remember { mutableFloatStateOf(0f) }
    // Los gestos se quedan con la lambda de su primera composición: por eso leen el estado al llamarse, no al crearse.
    val emitAt: (Float) -> Unit = { x ->
        if (widthPx > 0f) currentOnValueChange((x / widthPx * 100f).roundToInt().coerceIn(0, 100))
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = "$clamped %"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.toFloat(), 0f..100f, 100)
                setProgress { target ->
                    currentOnValueChange(target.roundToInt().coerceIn(0, 100))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> emitAt(offset.x) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    emitAt(change.position.x)
                }
            },
    ) {
        val y = size.height / 2f
        val track = 4.dp.toPx()
        val thumb = 10.dp.toPx()
        val x = size.width * clamped / 100f
        drawLine(
            color = WizardColors.text.copy(alpha = 0.12f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(x, y),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )
        drawCircle(color = color, radius = thumb, center = Offset(x, y))
    }
}
