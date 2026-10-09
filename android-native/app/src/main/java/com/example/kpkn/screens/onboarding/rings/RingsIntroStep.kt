package com.example.kpkn.screens.onboarding.rings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.auge.RingChannelId
import com.example.kpkn.domain.auge.RingsIntroSimulator
import com.example.kpkn.domain.auge.RingsIntroState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import kotlin.math.roundToInt

/**
 * Alto de la torre del paso: 200 dp, a todo el ancho y centrada. Así la escala la fija el alto y cada banda de toque
 * (Músculos, la más estrecha) mide al menos 48 dp.
 */
private val RingsTowerHeight = 200.dp

/** Línea de tiempo: alto del dibujo y margen interior para que quepan las etiquetas de 0 h y 72 h. */
private val TimelineHeight = 64.dp
private val TimelineEdge = 16.dp

/** Fundido de la línea que cambia al tocar un anillo. */
private const val HighlightFadeMillis = 160

private val TickShort = WizardColors.textMuted.copy(alpha = 0.32f)
private val TickLong = WizardColors.textMuted.copy(alpha = 0.80f)
private val TickLabel = WizardColors.textMuted.copy(alpha = 0.72f)

/**
 * Paso `RINGS_INTRO`: qué son los RINGS con la torre del logo, una demostración con la ley real del motor y el ajuste de
 * la persona. Es solo una demostración: no escribe en el borrador y el paso se confirma sin dato. Su estado vive en la
 * pantalla; al salir del paso se pierde.
 */
@Composable
internal fun RingsIntroStep(modifier: Modifier = Modifier) {
    var demo by remember { mutableStateOf(RingsIntroState()) }
    var highlighted by remember { mutableStateOf<RingChannelId?>(null) }
    RingsIntroContent(
        demo = demo,
        highlighted = highlighted,
        onSimulateToggle = { demo = RingsIntroSimulator.toggleSession(demo) },
        onHours = { hours -> demo = RingsIntroSimulator.withHours(demo, hours) },
        onAdjust = { channel, value -> demo = RingsIntroSimulator.withAdjustment(demo, channel, value) },
        onHighlight = { channel -> highlighted = channel },
        modifier = modifier,
    )
}

/**
 * Cuerpo del paso sin estado propio: lo que se ve con [demo] y [highlighted]. Las acciones suben a quien lo compone.
 * Lo usan [RingsIntroStep] y la hoja de render.
 */
@Composable
internal fun RingsIntroContent(
    demo: RingsIntroState,
    highlighted: RingChannelId?,
    onSimulateToggle: () -> Unit,
    onHours: (Float) -> Unit,
    onAdjust: (RingChannelId, Int) -> Unit,
    onHighlight: (RingChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = RingsIntroSimulator.valuesAt(demo)
    // Mientras la persona arrastra la línea de tiempo, la torre sigue al dedo sin animar.
    var dragging by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WizardSpacing.titleGap),
        ) {
            KpknTowerRings(
                values = TowerRingValues(columna = values.columna, musculos = values.musculos, energia = values.energia),
                highlighted = highlighted?.towerChannel(),
                onRingTap = { ring -> onHighlight(ring.domainChannel()) },
                animate = !dragging,
                modifier = Modifier.fillMaxWidth().height(RingsTowerHeight).testTag("rings-intro-tower"),
            )
            HighlightLine(highlighted)
        }
        RingsDivider()
        SimulateAction(heavy = demo.heavySession, onClick = onSimulateToggle)
        if (demo.heavySession) {
            RingsDivider()
            RingsIntroTimeline(
                hours = demo.hoursAfter,
                onHours = onHours,
                onDragChange = { dragging = it },
            )
        }
        if (highlighted != null) {
            RingsDivider()
            AdjustBlock(
                channel = highlighted,
                shown = values[highlighted],
                onCommit = { value -> onAdjust(highlighted, value) },
            )
        }
        Text(
            text = "Con unas preguntas rápidas calculamos cómo llegas hoy.",
            style = WizardTypography.stepSubtitle,
            color = WizardColors.textMuted,
        )
    }
}

/** Línea bajo la torre: dice qué mide el anillo tocado. Cambia con un fundido corto (sin él, con movimiento reducido). */
@Composable
private fun HighlightLine(channel: RingChannelId?) {
    val reduced = wizardReducedMotion()
    Crossfade(
        targetState = highlightText(channel),
        animationSpec = if (reduced) snap<Float>() else tween<Float>(HighlightFadeMillis),
        label = "ringsIntroHighlight",
    ) { text ->
        Text(
            text = text,
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("rings-intro-line"),
        )
    }
}

private fun highlightText(channel: RingChannelId?): String = when (channel) {
    null -> "Toca un anillo para ver qué mide."
    RingChannelId.MUSCULOS -> "Cuánto se han recuperado tus músculos."
    RingChannelId.ENERGIA -> "Tu reserva para entrenar fuerte."
    RingChannelId.COLUMNA -> "La carga que acumula tu espalda."
}

@Composable
private fun RingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
}

/** Acción de texto con icono: simula el entreno o lo quita. Sus 48 dp de alto son el área de toque. */
@Composable
private fun SimulateAction(heavy: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag("rings-intro-simulate")
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarbellGlyph(Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (heavy) "Quitar el entreno" else "Simular un entreno pesado",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
        )
    }
}

/** Icono de barra con dos discos, dibujado con la tinta de la marca. */
@Composable
private fun BarbellGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val mid = size.height / 2f
        drawLine(WizardColors.text, Offset(0f, mid), Offset(size.width, mid), 2.dp.toPx(), StrokeCap.Round)
        val plate = size.width * 0.14f
        drawRect(WizardColors.text, topLeft = Offset(size.width * 0.12f, 0f), size = Size(plate, size.height))
        drawRect(WizardColors.text, topLeft = Offset(size.width * 0.74f, 0f), size = Size(plate, size.height))
    }
}

/**
 * Línea de tiempo «Después del entreno» (0–72 h). Arrastrar, tocar o las acciones «Más tarde» y «Antes» (12 h) mueven la
 * hora; los anillos siguen sus curvas en vivo. Solo existe con la sesión simulada.
 */
@Composable
private fun RingsIntroTimeline(
    hours: Float,
    onHours: (Float) -> Unit,
    onDragChange: (Boolean) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val edgePx = with(LocalDensity.current) { TimelineEdge.toPx() }
    val onHoursNow by rememberUpdatedState(onHours)
    val onDragNow by rememberUpdatedState(onDragChange)
    val labels = remember(measurer) { HashMap<Int, TextLayoutResult>() }
    val shown = hours.roundToInt()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.titleGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Después del entreno",
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
            )
            Text(
                text = "$shown h",
                style = WizardTypography.header,
                color = WizardColors.text,
                modifier = Modifier.testTag("rings-intro-hours"),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TimelineHeight)
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

/**
 * Ajuste de la persona para el anillo destacado. Mientras se arrastra, el número muestra el valor en curso; al soltar, el
 * anillo salta a ese valor y la curva sigue desde la hora actual.
 */
@Composable
private fun AdjustBlock(channel: RingChannelId, shown: Int, onCommit: (Int) -> Unit) {
    var preview by remember(channel) { mutableStateOf<Int?>(null) }
    val value = preview ?: shown
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.titleGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "¿Te sientes distinto? Ajústalo",
                style = WizardTypography.controlLabel,
                color = WizardColors.text,
            )
            Text(
                text = "$value %",
                style = WizardTypography.header,
                color = WizardColors.text,
            )
        }
        RingsChannelSlider(
            accent = KpknRingColors.of(channel.towerChannel()),
            name = channel.spanishName(),
            value = value,
            onPreview = { preview = it },
            onCommit = { chosen ->
                preview = null
                onCommit(chosen)
            },
            tag = "rings-intro-slider",
        )
        Text(
            text = "Si no coincide con cómo te sientes, ajústalo: KPKN usa tus ajustes para afinar tu ritmo de " +
                "recuperación con cada entreno.",
            style = WizardTypography.note,
            color = WizardColors.textMuted,
        )
    }
}

private fun RingChannelId.towerChannel(): RingChannel = when (this) {
    RingChannelId.COLUMNA -> RingChannel.COLUMNA
    RingChannelId.MUSCULOS -> RingChannel.MUSCULOS
    RingChannelId.ENERGIA -> RingChannel.ENERGIA
}

private fun RingChannel.domainChannel(): RingChannelId = when (this) {
    RingChannel.COLUMNA -> RingChannelId.COLUMNA
    RingChannel.MUSCULOS -> RingChannelId.MUSCULOS
    RingChannel.ENERGIA -> RingChannelId.ENERGIA
}

private fun RingChannelId.spanishName(): String = when (this) {
    RingChannelId.COLUMNA -> "Columna"
    RingChannelId.MUSCULOS -> "Músculos"
    RingChannelId.ENERGIA -> "Energía"
}
