package com.example.kpkn.screens.onboarding.rings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.auge.RingChannelId
import com.example.kpkn.domain.auge.RingsIntroSimulator
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import kotlin.math.roundToInt

/** Alto de la torre de la página que queda bajo el overlay. */
private val RingsPageTowerHeight = 200.dp

/** Alto del lienzo de la línea de tiempo y margen interior para que quepan las etiquetas de 0 h y 72 h. */
internal val RingsTimelineHeight = 64.dp
private val TimelineEdge = 24.dp

private val TickShort = WizardColors.textMuted.copy(alpha = 0.32f)
private val TickLong = WizardColors.textMuted.copy(alpha = 0.80f)
private val TickLabel = WizardColors.textMuted.copy(alpha = 0.72f)

/**
 * Paso `RINGS_INTRO` tal como queda bajo el overlay: la torre en reposo, sin controles. La demostración vive en
 * [RingsIntroOverlay], que el anfitrión pinta encima mientras el cursor está en este paso. El paso no escribe nada.
 */
@Composable
internal fun RingsIntroStep(modifier: Modifier = Modifier) {
    KpknTowerRings(
        values = TowerRingValues(columna = 100, musculos = 100, energia = 100),
        animate = false,
        modifier = modifier.fillMaxWidth().height(RingsPageTowerHeight),
    )
}

/** Lo que dice un anillo encendido, o la invitación a tocar uno (copia de la intro de siempre). */
internal fun highlightText(channel: RingChannelId?): String = when (channel) {
    null -> "Toca un anillo para ver qué mide."
    RingChannelId.MUSCULOS -> "Cuánto se han recuperado tus músculos."
    RingChannelId.ENERGIA -> "Tu reserva para entrenar fuerte."
    RingChannelId.COLUMNA -> "La carga que acumula tu espalda."
}

/** Nombre de un anillo en español. */
internal fun ringName(channel: RingChannelId): String = when (channel) {
    RingChannelId.COLUMNA -> "Columna"
    RingChannelId.MUSCULOS -> "Músculos"
    RingChannelId.ENERGIA -> "Energía"
}

internal fun RingChannelId.towerChannel(): RingChannel = when (this) {
    RingChannelId.COLUMNA -> RingChannel.COLUMNA
    RingChannelId.MUSCULOS -> RingChannel.MUSCULOS
    RingChannelId.ENERGIA -> RingChannel.ENERGIA
}

internal fun RingChannel.domainChannel(): RingChannelId = when (this) {
    RingChannel.COLUMNA -> RingChannelId.COLUMNA
    RingChannel.MUSCULOS -> RingChannelId.MUSCULOS
    RingChannel.ENERGIA -> RingChannelId.ENERGIA
}

/**
 * Línea de tiempo «Después del entreno» (0–72 h) como lienzo: arrastrar, tocar o las acciones «Más tarde» y «Antes» (12 h)
 * mueven la hora; los anillos siguen sus curvas en vivo. [onDragChange] avisa del arrastre para que la torre siga al dedo sin
 * animar. El tamaño lo fija quien la compone.
 */
@Composable
internal fun RingsIntroTimelineCanvas(
    hours: Float,
    onHours: (Float) -> Unit,
    onDragChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val edgePx = with(LocalDensity.current) { TimelineEdge.toPx() }
    val onHoursNow by rememberUpdatedState(onHours)
    val onDragNow by rememberUpdatedState(onDragChange)
    val labels = remember(measurer) { HashMap<Int, TextLayoutResult>() }
    val shown = hours.roundToInt()
    Canvas(
        modifier = modifier
            .testTag("rings-intro-timeline")
            .semantics {
                contentDescription = "Después del entreno: $shown h"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = shown.toFloat(),
                    range = 0f..RingsIntroSimulator.MAX_HOURS,
                    steps = 71,
                )
                setProgress { target ->
                    onHoursNow(target)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Más tarde") {
                        onHoursNow(shown + 12f)
                        true
                    },
                    CustomAccessibilityAction("Antes") {
                        onHoursNow(shown - 12f)
                        true
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHoursNow(hoursAt(offset.x, size.width.toFloat(), edgePx))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onDragNow(true)
                        onHoursNow(hoursAt(offset.x, size.width.toFloat(), edgePx))
                    },
                    onDragEnd = { onDragNow(false) },
                    onDragCancel = { onDragNow(false) },
                    onHorizontalDrag = { change, _ ->
                        onHoursNow(hoursAt(change.position.x, size.width.toFloat(), edgePx))
                    },
                )
            },
    ) {
        drawHoursRuler(hours, edgePx, measurer, labels)
    }
}

/** Hora de la línea (sin redondear; el dominio redondea) a partir de la x de un toque o arrastre. */
private fun hoursAt(x: Float, width: Float, edgePx: Float): Float {
    val span = (width - 2f * edgePx).coerceAtLeast(1f)
    return ((x - edgePx) / span * RingsIntroSimulator.MAX_HOURS).coerceIn(0f, RingsIntroSimulator.MAX_HOURS)
}

/** Marcas cada 12 h (largas y con etiqueta cada 24 h) y el cursor verde en la hora actual. */
private fun DrawScope.drawHoursRuler(
    hours: Float,
    edgePx: Float,
    measurer: TextMeasurer,
    labels: MutableMap<Int, TextLayoutResult>,
) {
    val span = size.width - 2f * edgePx
    val baseline = 6.dp.toPx()
    val shortLen = 14.dp.toPx()
    val longLen = 30.dp.toPx()
    val tickWidth = 1.5.dp.toPx()
    val labelTop = baseline + longLen + 6.dp.toPx()
    val max = RingsIntroSimulator.MAX_HOURS
    for (mark in 0..72 step 12) {
        val x = edgePx + span * mark / max
        val long = mark % 24 == 0
        drawLine(
            color = if (long) TickLong else TickShort,
            start = Offset(x, baseline),
            end = Offset(x, baseline + if (long) longLen else shortLen),
            strokeWidth = tickWidth,
            cap = StrokeCap.Round,
        )
        if (long) {
            val layout = labels.getOrPut(mark) { measurer.measure("$mark h", WizardTypography.note) }
            drawText(layout, color = TickLabel, topLeft = Offset(x - layout.size.width / 2f, labelTop))
        }
    }
    val cursorX = edgePx + span * hours / max
    drawLine(
        color = WizardColors.ruleCursor,
        start = Offset(cursorX, 0f),
        end = Offset(cursorX, baseline + longLen + 3.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
