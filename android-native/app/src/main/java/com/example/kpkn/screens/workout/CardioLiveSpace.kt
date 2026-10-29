package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.CardioActivityFigure
import com.example.kpkn.screens.workout.components.CardioFigureSpec
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * Espacio de cardio (iteración 03, aprobada): todo en crema sobre negro; el único color es el de la zona activa (la corona del anillo,
 * la cinta de zonas y el perfil del plan). Zonas por % de la FC máxima: Z1 50–60, Z2 60–70, Z3 70–80, Z4 80–90, Z5 90–100.
 */

internal val CardioZoneColors = listOf(Color(0xFF8AA3B3), Color(0xFF87C9A5), Color(0xFFCDBB8F), Color(0xFFF49A6E), Color(0xFFE9736C))
internal val CardioZoneNames = listOf("Recuperación", "Base aeróbica", "Tempo", "Umbral", "Máximo")
private val CardioBody = TextStyle(fontFamily = WizardFonts.body)
private val CardioSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)
private val CardioInk = WizardColors.text

internal fun cardioZoneColor(zone: Int): Color = CardioZoneColors[(zone - 1).coerceIn(0, 4)]

/** Un paso del plan: nombre, vuelta (si la hay), duración, zona y consigna («12,0 km/h»). */
internal data class CardioStep(val name: String, val round: Int?, val seconds: Int, val zone: Int, val cue: String, val block: CardioIntervalBlock?)

internal fun cardioZoneForPercent(percent: Int): Int = when {
    percent < 60 -> 1
    percent < 70 -> 2
    percent < 80 -> 3
    percent < 90 -> 4
    else -> 5
}

private fun cardioZoneForBlock(block: CardioIntervalBlock): Int = block.targetHrPercent?.let(::cardioZoneForPercent) ?: when (block.type) {
    CardioBlockType.WARMUP -> 2
    CardioBlockType.WORK -> 4
    CardioBlockType.RECOVER -> 2
    CardioBlockType.COOLDOWN -> 1
}

internal fun cardioDecimal(value: Double, decimals: Int = 1): String = "%.${decimals}f".format(java.util.Locale.forLanguageTag("es"), value)

private fun cardioCue(block: CardioIntervalBlock): String = listOfNotNull(
    block.speedKmh?.let { "${cardioDecimal(it)} km/h" },
    block.watts?.let { "$it W" },
    block.rpm?.let { "$it rpm" },
    block.targetPaceSecondsPerKm?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')} /km" },
    block.intensityLevel?.takeIf { block.speedKmh == null && block.watts == null }?.let { "Nivel $it" },
).firstOrNull() ?: ""

internal fun cardioSteps(details: CardioDetails): List<CardioStep> {
    if (!details.hasIntervals()) {
        val zone = details.targetHrPercent?.let(::cardioZoneForPercent) ?: ((details.resolvedIntensityLevel() + 1) / 2).coerceIn(1, 5)
        return listOf(CardioStep("Continuo", null, details.effectiveDurationSeconds().coerceAtLeast(60), zone,
            details.targetPaceSecondsPerKm?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')} /km" } ?: "", null))
    }
    var work = 0
    var recover = 0
    return CardioIntervalEngine.expandedBlocks(details).map { block ->
        val round = when (block.type) { CardioBlockType.WORK -> ++work; CardioBlockType.RECOVER -> ++recover; else -> null }
        CardioStep(CardioIntervalEngine.blockTypeLabel(block.type), round, block.durationSeconds, cardioZoneForBlock(block), cardioCue(block), block)
    }
}

internal fun cardioClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "${s / 3600}:${((s % 3600) / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Posición de la aguja en la cinta (0–100): la zona del pulso y dónde cae dentro de ella. */
internal fun cardioNeedlePercent(hr: Int, maxHr: Int): Float {
    val pct = hr * 100f / maxHr
    val zoneIndex = (cardioZoneForPercent(pct.roundToInt()) - 1)
    val low = 50f + zoneIndex * 10f
    val within = ((pct - low) / 10f).coerceIn(0f, 1f)
    return ((zoneIndex + within) * 20f).coerceIn(1f, 99f)
}

/** Alto del recuadro del reloj para el alto disponible de la página: nunca menos de lo que pide su contenido. */
internal fun cardioClockPanelHeight(available: Dp, fraction: Float): Dp = (available * fraction).coerceIn(196.dp, 330.dp)

/**
 * El recuadro del reloj (sustituye al anillo): carbón con radio 24 a todo el ancho y con el alto que le toca. Arriba el rótulo del
 * paso con el punto de su zona y el total restante; en medio el tiempo del paso en Syne, en una sola línea y con el tamaño que cabe en
 * el hueco medido (no depende del tamaño de letra del sistema); debajo la figura haciendo la actividad y el avance del paso. El único
 * color es el de la zona (el punto y un velo tenue al pie).
 */
@Composable
internal fun CardioClockPanel(
    label: String,
    eyebrow: String,
    trailing: String?,
    sub: String?,
    fraction: Float?,
    zoneColor: Color,
    figure: CardioFigureSpec,
    cadence: Float,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(shape).background(Color(0xFF111111))
            .drawBehind { drawRect(Brush.verticalGradient(.55f to Color.Transparent, 1f to zoneColor.copy(alpha = .10f))) }
            .border(1.dp, Color(0x10FFFFFF), shape)
            .testTag("cardio-clock"),
    ) {
        val figureHeight = (maxHeight * .36f).coerceIn(52.dp, 124.dp)
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(zoneColor))
                Text(eyebrow, Modifier.weight(1f).testTag("cardio-clock-eyebrow"), style = CardioBody.copy(fontSize = 10.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                trailing?.let {
                    Text(it, style = CardioBody.copy(fontSize = 10.sp, letterSpacing = .6.sp), color = LiveSheetTokens.Faint, maxLines = 1,
                        modifier = Modifier.testTag("cardio-clock-trailing"))
                }
            }
            CardioFittedClock(label, Modifier.weight(1f).fillMaxWidth())
            sub?.let {
                Text(it, Modifier.fillMaxWidth(), style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CardioActivityFigure(figure, cadence, Modifier.fillMaxWidth().height(figureHeight), dimmed = dimmed)
            if (fraction != null) {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x14FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(CardioInk).testTag("cardio-clock-progress"))
                }
            }
        }
    }
}

/**
 * El tiempo en una sola línea, con el tamaño que llena el hueco medido: se mide una plantilla con ceros (las cifras son tabulares, así
 * que el tamaño no salta cada segundo) y se escala al ancho y al alto disponibles.
 */
@Composable
private fun CardioFittedClock(label: String, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val base = CardioSyne.copy(fontSize = 100.sp, lineHeight = 100.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "'tnum' 1")
        val template = label.map { if (it.isDigit()) '0' else it }.joinToString("")
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val fontScale = LocalDensity.current.fontScale
        val sizeSp = remember(template, widthPx, heightPx, fontScale) {
            val measured = measurer.measure(template, base, maxLines = 1, softWrap = false).size
            if (measured.width <= 0 || measured.height <= 0 || widthPx <= 0 || heightPx <= 0) 8f
            else (100f * minOf(widthPx * .9f / measured.width, heightPx * .94f / measured.height)).coerceAtLeast(8f)
        }
        Text(label, style = base.copy(fontSize = sizeSp.sp, lineHeight = sizeSp.sp), color = CardioInk, textAlign = TextAlign.Center, maxLines = 1,
            softWrap = false, modifier = Modifier.testTag("cardio-clock-label"))
    }
}

/** `.tape`: cinco franjas (o diez de RPE); la objetivo, más alta y entera; la aguja crema marca el pulso. */
@Composable
internal fun CardioTape(labels: List<String>, colors: List<Color>, target: Set<Int>, needlePercent: Float?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("cardio-tape")) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                labels.indices.forEach { i ->
                    val on = i in target
                    Box(Modifier.weight(1f).height(if (on) 12.dp else 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(colors[i].copy(alpha = if (on) 1f else .32f)))
                }
            }
            needlePercent?.let { p ->
                Box(Modifier.offset(x = maxWidth * (p / 100f) - 2.dp).width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                    .background(CardioInk).testTag("cardio-needle"))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { i, label ->
                Text(label, Modifier.weight(1f), style = CardioBody.copy(fontSize = 9.sp, fontWeight = if (i in target) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (i in target) CardioInk else LiveSheetTokens.Faint, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.profile`: el perfil del plan (ancho por duración, alto por zona) con la posición actual. */
@Composable
internal fun CardioProfile(steps: List<CardioStep>, current: Int, progress: Float, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val total = steps.sumOf { it.seconds }.coerceAtLeast(1)
    val position = steps.take(current.coerceAtLeast(0)).sumOf { it.seconds } + (if (current >= 0) steps.getOrNull(current)?.seconds?.times(progress)?.toInt() ?: 0 else 0)
    BoxWithConstraints(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Perfil del plan" }.testTag("cardio-profile")) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            steps.forEachIndexed { i, step ->
                val zoneHeight = listOf(.34f, .5f, .66f, .86f, 1f)[(step.zone - 1).coerceIn(0, 4)]
                Box(Modifier.weight(step.seconds.toFloat().coerceAtLeast(1f)).fillMaxHeight(zoneHeight).clip(RoundedCornerShape(3.dp))
                    .background(cardioZoneColor(step.zone).copy(alpha = when {
                        current < 0 -> .85f
                        i < current -> .35f
                        i == current -> 1f
                        else -> .6f
                    })))
            }
        }
        if (current >= 0) {
            Box(Modifier.offset(x = maxWidth * (position.toFloat() / total) - 1.dp).width(2.dp).fillMaxHeight().background(CardioInk))
        }
    }
}

/** `.status`: la frase de estado con su icono; con aviso, el borde toma el color de la zona en la que estás. */
@Composable
internal fun CardioStatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, small: String?, warnColor: Color?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).clip(shape).background(Color(0x0AFFFFFF))
            .border(1.dp, warnColor?.copy(alpha = .6f) ?: Color(0x10FFFFFF), shape).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = listOfNotNull(text, small).joinToString(", ") }.testTag("cardio-status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(17.dp), tint = warnColor ?: CardioInk) }
        Text(text, Modifier.weight(1f), style = CardioBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
        small?.let { Text(it, style = CardioBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint) }
    }
}

/**
 * Ficha de dato (sin borde: no parece un botón) o, con [onClick], ficha de acción: borde visible, icono en tinta y el «+» o el lápiz
 * de [actionIcon] al final, con 44 dp de alto para el dedo.
 */
@Composable
internal fun CardioChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String? = null,
    onClick: (() -> Unit)? = null,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val action = onClick != null
    Row(
        modifier.heightIn(min = if (action) 44.dp else 36.dp).clip(shape).background(Color(0x0DFFFFFF))
            .then(if (action) Modifier.border(1.dp, Color(0x38FFFFFF), shape).clickable(role = Role.Button, onClick = onClick!!) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(15.dp), tint = if (action) CardioInk else LiveSheetTokens.Muted) }
        label?.let { Text(it, style = CardioBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint) }
        Text(text, style = CardioBody.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
        actionIcon?.let { Icon(it, null, Modifier.size(14.dp), tint = CardioInk) }
    }
}

/** `.cdock`: los puntos de las tres páginas y Bloquear · Pausa/Reanudar · Saltar paso (o el desbloqueo manteniendo). */
@Composable
internal fun CardioDock(
    page: Int,
    onPage: (Int) -> Unit,
    locked: Boolean,
    mainLabel: String,
    mainIcon: androidx.compose.ui.graphics.vector.ImageVector,
    mainEnabled: Boolean,
    onMain: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(Color(0xC2333333), Color(0xB3212121))))
            .border(1.dp, Color(0x24FFFFFF), shape).padding(horizontal = 10.dp, vertical = 8.dp).testTag("cardio-dock"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Ahora", "Plan", "Resumen").forEachIndexed { i, name ->
                Box(Modifier.size(width = 30.dp, height = 24.dp).clickable(role = Role.Tab) { onPage(i) }
                    .semantics { contentDescription = name; selected = i == page }.testTag("cardio-page-$i"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = if (i == page) 16.dp else 6.dp, height = 6.dp).clip(CircleShape)
                        .background(if (i == page) CardioInk else Color(0x40FFFFFF)))
                }
            }
        }
        if (locked) {
            CardioHoldToUnlock(onUnlock, Modifier.padding(top = 4.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CardioCtrl("Bloquear", null, onLock, Modifier.weight(1f), lockGlyph = true, tag = "cardio-lock")
                CardioCtrl(mainLabel, mainIcon, onMain, Modifier.weight(1.3f), main = true, enabled = mainEnabled, tag = "cardio-main")
                // Sin pasos que saltar (cardio continuo o aún sin empezar) el mando no se enseña: nada de botones apagados que no hacen nada.
                if (onSkip != null) CardioCtrl("Saltar paso", KpknLineIcons.FrameForward, onSkip, Modifier.weight(1f), tag = "cardio-skip")
            }
        }
    }
}

@Composable
private fun CardioCtrl(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier,
    main: Boolean = false,
    enabled: Boolean = true,
    lockGlyph: Boolean = false,
    tag: String,
) {
    Column(
        modifier.heightIn(min = 60.dp).clip(RoundedCornerShape(18.dp)).background(if (main) CardioInk else Color(0x0DFFFFFF))
            .alpha(if (enabled) 1f else .4f).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        val ink = if (main) Color(0xFF111111) else CardioInk
        if (lockGlyph) CardioLockGlyph(ink, Modifier.size(20.dp)) else icon?.let { Icon(it, null, Modifier.size(if (main) 22.dp else 20.dp), tint = ink) }
        Text(label, style = CardioBody.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = ink, textAlign = TextAlign.Center)
    }
}

/** El candado (no hay icono de línea propio): cuerpo redondeado y arco. */
@Composable
private fun CardioLockGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * .2f, h * .45f), Size(w * .6f, h * .45f), androidx.compose.ui.geometry.CornerRadius(w * .1f), style = Stroke(1.6.dp.toPx()))
        drawArc(color, 180f, 180f, false, Offset(w * .32f, h * .18f), Size(w * .36f, h * .5f), style = Stroke(1.6.dp.toPx()))
    }
}

/** Bloqueo: mantener 1,2 s desbloquea (con TalkBack, la acción accesible desbloquea al instante). */
@Composable
private fun CardioHoldToUnlock(onUnlock: () -> Unit, modifier: Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (!holding) { progress = 0f; return@LaunchedEffect }
        val start = System.nanoTime()
        while (holding) {
            progress = ((System.nanoTime() - start) / 1_200_000_000f).coerceIn(0f, 1f)
            if (progress >= 1f) { holding = false; onUnlock(); break }
            delay(16)
        }
    }
    Row(modifier.fillMaxWidth().heightIn(min = 74.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(74.dp).semantics { contentDescription = "Desbloquear"; onClick(label = "Desbloquear") { onUnlock(); true } }
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); holding = true; do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed }); holding = false } }
                .testTag("cardio-unlock"),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = 33.dp.toPx()
                drawCircle(Color(0x14FFFFFF), r, center, style = Stroke(3.dp.toPx()))
                drawArc(CardioInk, -90f, 360f * progress, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            CardioLockGlyph(CardioInk, Modifier.size(22.dp))
        }
        Text("Mantén para\ndesbloquear", style = CardioBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = LiveSheetTokens.Muted)
    }
}

/** `.pause-veil`: «CARDIO EN PAUSA», el tiempo en pausa (aparte del activo), «Reanudar» y «Terminar cardio». */
@Composable
internal fun CardioPauseVeil(pausedSeconds: Int, stepLeft: String, onResume: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Color(0xD9000000)).clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 30.dp).testTag("cardio-pause-veil"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("CARDIO EN PAUSA", style = CardioBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
        Text(cardioClock(pausedSeconds), style = CardioSyne.copy(fontSize = 44.sp, lineHeight = 57.sp, fontWeight = FontWeight.ExtraBold), color = CardioInk)
        Text("El tiempo en pausa no cuenta como activo. El paso actual queda en $stepLeft.", Modifier.padding(bottom = 10.dp),
            style = CardioBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.Center)
        com.example.kpkn.screens.workout.components.RestRef02Button("Reanudar", onResume, primary = true, modifier = Modifier.width(200.dp),
            icon = KpknLineIcons.Play, tag = "cardio-resume")
        com.example.kpkn.screens.workout.components.RestRef02Button("Terminar cardio", onFinish, primary = false, modifier = Modifier.width(200.dp),
            tag = "cardio-finish")
    }
}

/** `.count`: la cuenta atrás 3-2-1 con lo que empieza. */
@Composable
internal fun CardioCountdown(number: Int, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Color(0xE6000000)).testTag("cardio-countdown"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("$number", style = CardioSyne.copy(fontSize = 120.sp, lineHeight = 150.sp, fontWeight = FontWeight.ExtraBold), color = CardioInk)
        Text(subtitle, style = CardioBody.copy(fontSize = 13.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.Center)
    }
}

/** `.sumcell`: rótulo de 9, la cifra en Syne y la unidad o la nota. */
@Composable
internal fun CardioSumCell(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).background(Color(0x0AFFFFFF)).border(1.dp, Color(0x0FFFFFFF), shape).padding(12.dp)) {
        Text(label, style = CardioBody.copy(fontSize = 9.sp, letterSpacing = .8.sp), color = LiveSheetTokens.Faint)
        Text(value, Modifier.padding(top = 4.dp), style = CardioSyne.copy(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold), color = CardioInk)
        Text(note, style = CardioBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
    }
}

@Composable
internal fun CardioPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 8.dp), style = CardioBody.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
        color = LiveSheetTokens.Faint)
}

/** «¿Cuánto te costó?»: diez botones; empieza «Sin marcar» y el plan solo se subraya como referencia. */
@Composable
internal fun CardioRpePicker(selected: Int?, planned: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag("cardio-rpe"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..10).forEach { n ->
            val on = selected == n
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) CardioInk else Color(0x0DFFFFFF))
                    .drawBehind {
                        if (n == planned && !on) drawLine(Color(0x55FFFFFF), Offset(4.dp.toPx(), size.height - 1.dp.toPx()),
                            Offset(size.width - 4.dp.toPx(), size.height - 1.dp.toPx()), 2.dp.toPx())
                    }
                    .clickable(role = Role.RadioButton) { onSelect(n) }
                    .semantics { contentDescription = "Esfuerzo $n" + if (n == planned) " · plan" else ""; this.selected = on }
                    .testTag("cardio-rpe-$n"),
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", style = CardioSyne.copy(fontSize = 13.sp), color = if (on) Color(0xFF111111) else CardioInk)
            }
        }
    }
}

/**
 * «Ahora vamos con cardio» (sesión mixta): el título por palabras, lo que se terminó, la tarjeta del primer bloque con su zona, el perfil
 * del plan y «Desliza para empezar». «Ver el plan» abre la lista de pasos sin empezar.
 */
@Composable
internal fun CardioTransitionIn(
    title: String,
    lead: String?,
    exerciseName: String,
    meta: String,
    firstStep: CardioStep?,
    steps: List<CardioStep>,
    totalLabel: String,
    onSwipeStart: () -> Unit,
    onSeePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { if (!reduced) { delay(120); shown = true } }
    Column(modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp).testTag("cardio-transition-in")) {
        Spacer(Modifier.weight(.6f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(KpknLineIcons.Training, null, Modifier.size(19.dp), tint = LiveSheetTokens.Muted)
            Box(Modifier.width(48.dp).height(2.dp).background(Brush.horizontalGradient(listOf(Color(0x40FFFFFF), cardioZoneColor(firstStep?.zone ?: 2)))))
            Icon(KpknLineIcons.Run, null, Modifier.size(19.dp), tint = cardioZoneColor(firstStep?.zone ?: 2))
        }
        Text(title, Modifier.padding(top = 18.dp).alpha(if (shown) 1f else 0f),
            style = CardioSyne.copy(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold), color = CardioInk)
        lead?.let { Text(it, Modifier.padding(top = 10.dp), style = CardioBody.copy(fontSize = 13.sp, lineHeight = 19.sp), color = LiveSheetTokens.Muted) }
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(shape).background(Color(0xFF111111)).border(1.dp, Color(0x10FFFFFF), shape).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x0BFFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(KpknLineIcons.Run, null, Modifier.size(22.dp), tint = CardioInk)
                }
                Column {
                    Text(exerciseName, style = CardioSyne.copy(fontSize = 15.sp, lineHeight = 20.sp), color = CardioInk)
                    Text(meta, style = CardioBody.copy(fontSize = 10.5.sp), color = LiveSheetTokens.Faint)
                }
            }
            firstStep?.let { step ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Primer bloque: ${step.name} ${cardioClock(step.seconds)}", Modifier.weight(1f), style = CardioBody.copy(fontSize = 12.sp), color = CardioInk)
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(cardioZoneColor(step.zone)))
                        Text("Zona ${step.zone}", style = CardioBody.copy(fontSize = 10.sp), color = CardioInk)
                    }
                }
                if (step.cue.isNotBlank()) {
                    Text("Ajusta a ${step.cue}", Modifier.padding(top = 8.dp), style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                }
            }
            CardioProfile(steps, current = -1, progress = 0f, modifier = Modifier.padding(top = 12.dp))
            Text(totalLabel, Modifier.padding(top = 6.dp), style = CardioBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
        }
        Text("Ver el plan", Modifier.padding(top = 12.dp).heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onSeePlan).padding(vertical = 10.dp)
            .testTag("cardio-see-plan"), style = CardioBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
        Spacer(Modifier.weight(1f))
        CardioSwipeToStart(onSwipeStart, Modifier.padding(bottom = 18.dp))
    }
}

/**
 * «Ahora vamos con fuerza» (`c-trans-out`): al terminar el cardio no hay descanso. Resume el cardio con lo medido y presenta la siguiente
 * serie de fuerza como se verá en Tarjetas: ejercicio, primera serie con su esfuerzo del plan, la última vez y las series (aproximación
 * incluida). «Desliza para continuar» pasa al ejercicio de fuerza.
 */
@Composable
internal fun CardioTransitionOut(
    lead: String?,
    exerciseName: String,
    meta: String,
    firstSet: String?,
    planEffort: String?,
    lastTime: String?,
    chips: List<String>,
    footnote: String?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { if (!reduced) { delay(120); shown = true } }
    // `.trans`: arriba el trayecto (`.trans-top`: carrera en círculo, línea y mancuerna en círculo discontinuo), el titular en Syne 800
    // con interlineado .98, el resumen medido y, abajo y pegada al deslizador, la tarjeta de la siguiente serie (`.trans-card`).
    Column(modifier.fillMaxSize().background(Color.Black).padding(horizontal = 18.dp).testTag("cardio-transition-out")) {
        Spacer(Modifier.weight(.35f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x0DFFFFFF)), contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.Run, null, Modifier.size(19.dp), tint = CardioInk)
            }
            Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0x1AFFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (shown) 1f else 0f).background(CardioInk))
            }
            Box(Modifier.size(40.dp).drawBehind {
                drawCircle(Color(0x40FFFFFF), size.minDimension / 2 - .5.dp.toPx(),
                    style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            }, contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.Training, null, Modifier.size(19.dp), tint = CardioInk)
            }
        }
        Text("Ahora vamos\ncon fuerza", Modifier.padding(top = 12.dp).alpha(if (shown) 1f else 0f).semantics { heading() },
            style = CardioSyne.copy(fontSize = 34.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp), color = CardioInk)
        lead?.let { Text(it, Modifier.padding(top = 12.dp), style = CardioBody.copy(fontSize = 13.5.sp, lineHeight = 19.5.sp), color = LiveSheetTokens.Muted) }
        Spacer(Modifier.weight(1f))
        val shape = RoundedCornerShape(25.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(Color(0xFF111111)).border(1.dp, Color(0x10FFFFFF), shape).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x0BFFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(KpknLineIcons.Training, null, Modifier.size(24.dp), tint = CardioInk)
                }
                Column {
                    Text(exerciseName, style = CardioSyne.copy(fontSize = 17.sp, lineHeight = 20.sp), color = CardioInk)
                    Text(meta, style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
                }
            }
            if (firstSet != null) {
                Row(Modifier.fillMaxWidth().padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        append("Primera serie: ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = CardioInk, fontWeight = FontWeight.SemiBold)); append(firstSet); pop()
                    }, Modifier.weight(1f), style = CardioBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
                    planEffort?.let {
                        Text(it, Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = CardioBody.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
                    }
                }
            }
            lastTime?.let { last ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x08FFFFFF))
                    .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(KpknLineIcons.History, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        append("Última vez: ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = CardioInk, fontWeight = FontWeight.SemiBold)); append(last); pop()
                    }, style = CardioBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
                }
            }
            if (chips.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    chips.forEach { label ->
                        val warm = label.startsWith("A")
                        Box(Modifier.weight(if (warm) 1f else 2f).height(30.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (warm) Color(0x07FFFFFF) else Color(0x12FFFFFF)), contentAlignment = Alignment.Center) {
                            Text(label, style = CardioSyne.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                        }
                    }
                }
            }
            footnote?.let { Text(it, Modifier.padding(top = 8.dp), style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint) }
        }
        CardioSwipeToStart(onContinue, Modifier.padding(top = 14.dp, bottom = 18.dp), label = "Desliza para continuar", actionLabel = "Continuar con fuerza",
            arrow = true)
    }
}

/** «Desliza para empezar»: el mando se arrastra hasta el final (con TalkBack, la acción accesible empieza al instante). */
@Composable
internal fun CardioSwipeToStart(onDone: () -> Unit, modifier: Modifier = Modifier, label: String = "Desliza para empezar",
    actionLabel: String = "Empezar", arrow: Boolean = false) {
    val density = LocalDensity.current
    var offsetPx by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(31.dp)).background(Color(0x14FFFFFF)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(31.dp))
            .semantics { contentDescription = label; onClick(label = actionLabel) { onDone(); true } }.testTag("cardio-swipe"),
    ) {
        val maxPx = with(density) { (maxWidth - 62.dp).toPx() }.coerceAtLeast(1f)
        Box(Modifier.fillMaxHeight().width(with(density) { (offsetPx.toDp() + 62.dp) }).clip(RoundedCornerShape(31.dp)).background(Color(0x1AFFFFFF)))
        Text(label, Modifier.align(Alignment.Center), style = CardioBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = if (arrow) LiveSheetTokens.Faint else LiveSheetTokens.Muted)
        // `.swipe .chev`: las dos flechas tenues a la derecha.
        if (arrow) Text("»", Modifier.align(Alignment.CenterEnd).padding(end = 20.dp), style = CardioBody.copy(fontSize = 18.sp), color = Color(0x40FFFFFF))
        Box(
            Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) }.size(62.dp).padding(5.dp).clip(CircleShape).background(CardioInk)
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (offsetPx >= maxPx * .9f) { offsetPx = maxPx; onDone() } else offsetPx = 0f },
                        onDragCancel = { offsetPx = 0f },
                    ) { change, dx -> change.consume(); offsetPx = (offsetPx + dx).coerceIn(0f, maxPx) }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (arrow) {
                // La flecha del prototipo (no hay icono de línea con ese trazo).
                androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                    val w = 1.8.dp.toPx(); val y = size.height / 2
                    val ink = Color(0xFF111111)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(2.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), y), w, StrokeCap.Round)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), y - 5.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), y), w, StrokeCap.Round)
                    drawLine(ink, androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), y + 5.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), y), w, StrokeCap.Round)
                }
            } else Icon(KpknLineIcons.ChevronRight, null, Modifier.size(20.dp), tint = Color(0xFF111111))
        }
    }
}
