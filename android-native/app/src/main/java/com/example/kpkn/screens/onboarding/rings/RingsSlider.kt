package com.example.kpkn.screens.onboarding.rings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import kotlin.math.roundToInt

/** Deslizador: alto de toque de 48 dp, grosor de la pista y radio del pulgar. */
private val SliderHeight = WizardSpacing.touchTarget
private val SliderTrack = 2.dp
private val SliderThumb = 9.dp

/** Pista sin llenar. */
private val TrackIdle = WizardColors.textFaint.copy(alpha = 0.45f)

/** Alfa del relleno y del pulgar cuando el valor es una estimación y no un dato de la persona. */
private const val DimmedAlpha = 0.4f

/**
 * Deslizador 0–100 de un canal de RINGS, con el color de marca de su anillo. Arrastrar en horizontal avisa con [onPreview]
 * (null = sin vista previa) y soltar confirma con [onCommit]; un toque confirma directamente. Con [dimmed] el valor es una
 * estimación y se pinta atenuado hasta que la persona lo mueve. [stateText] es el estado que lee TalkBack.
 */
@Composable
internal fun RingsChannelSlider(
    accent: Color,
    name: String,
    value: Int,
    onPreview: (Int?) -> Unit,
    onCommit: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    stateText: String = "$value %",
) {
    val onPreviewNow by rememberUpdatedState(onPreview)
    val onCommitNow by rememberUpdatedState(onCommit)
    val thumbPx = with(LocalDensity.current) { SliderThumb.toPx() }
    val trackPx = with(LocalDensity.current) { SliderTrack.toPx() }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .testTag(tag)
            .semantics {
                contentDescription = "Ajuste de $name"
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = 0f..100f,
                    steps = 99,
                )
                setProgress { target ->
                    onCommitNow(target.roundToInt().coerceIn(0, 100))
                    true
                }
            }
            .pointerInput(tag) {
                detectTapGestures { offset ->
                    onCommitNow(percentAt(offset.x, size.width.toFloat(), thumbPx))
                }
            }
            .pointerInput(tag) {
                var latest = 0
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latest = percentAt(offset.x, size.width.toFloat(), thumbPx)
                        onPreviewNow(latest)
                    },
                    onDragEnd = { onCommitNow(latest) },
                    onDragCancel = { onPreviewNow(null) },
                    onHorizontalDrag = { change, _ ->
                        latest = percentAt(change.position.x, size.width.toFloat(), thumbPx)
                        onPreviewNow(latest)
                    },
                )
            },
    ) {
        drawSlider(value, accent, thumbPx, trackPx, dimmed)
    }
}

private fun percentAt(x: Float, width: Float, thumbPx: Float): Int {
    val span = (width - 2f * thumbPx).coerceAtLeast(1f)
    return ((x - thumbPx) / span * 100f).roundToInt().coerceIn(0, 100)
}

private fun DrawScope.drawSlider(value: Int, accent: Color, thumbPx: Float, trackPx: Float, dimmed: Boolean) {
    val mid = size.height / 2f
    val left = thumbPx
    val right = size.width - thumbPx
    val x = left + (right - left) * value.coerceIn(0, 100) / 100f
    val alpha = if (dimmed) DimmedAlpha else 1f
    drawLine(TrackIdle, Offset(left, mid), Offset(right, mid), trackPx, StrokeCap.Round)
    drawLine(accent.copy(alpha = alpha), Offset(left, mid), Offset(x, mid), trackPx, StrokeCap.Round)
    drawCircle(color = WizardColors.text.copy(alpha = alpha), radius = thumbPx, center = Offset(x, mid))
}
