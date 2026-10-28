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
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

/** `.ring-box`: corona de la zona, pista tenue, borde de luz que progresa con su punto, y el centro con la cifra. */
@Composable
internal fun CardioRing(
    diameter: Dp,
    fraction: Float,
    zoneColor: Color,
    label: String,
    sub: String,
    em: String?,
    showDot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(diameter).testTag("cardio-ring"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - 14.dp.toPx()
            val c = center
            drawCircle(zoneColor.copy(alpha = .16f), r, c, style = Stroke(18.dp.toPx()))
            drawCircle(zoneColor.copy(alpha = .28f), r, c, style = Stroke(8.dp.toPx()))
            drawCircle(Color(0x10FFFFFF), r, c, style = Stroke(5.dp.toPx()))
            val f = fraction.coerceIn(0f, 1f)
            drawArc(CardioInk, -90f, 360f * f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            if (showDot) {
                val angle = -PI.toFloat() / 2f + f * 2f * PI.toFloat()
                val dot = Offset(c.x + r * cos(angle), c.y + r * sin(angle))
                drawCircle(Color.White.copy(alpha = .35f), 7.dp.toPx(), dot)
                drawCircle(Color.White, 3.2.dp.toPx(), dot)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(label, style = CardioSyne.copy(fontSize = (diameter.value * .2f).coerceIn(28f, 52f).sp, lineHeight = (diameter.value * .26f).coerceIn(36f, 66f).sp,
                fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "'tnum' 1"), color = CardioInk, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("cardio-ring-label"))
            Text(sub, style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            em?.let {
                Text(it, Modifier.fillMaxWidth().padding(top = 6.dp), style = CardioBody.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                    color = zoneColor, textAlign = TextAlign.Center)
            }
        }
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

@Composable
internal fun CardioChip(text: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, label: String? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.heightIn(min = 36.dp).clip(shape).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x0FFFFFFF), shape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, Modifier.size(15.dp), tint = LiveSheetTokens.Muted) }
        label?.let { Text(it, style = CardioBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint) }
        Text(text, style = CardioBody.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
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
                CardioCtrl("Saltar paso", KpknLineIcons.FrameForward, { onSkip?.invoke() }, Modifier.weight(1f), enabled = onSkip != null, tag = "cardio-skip")
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
    Column(modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp).testTag("cardio-transition-out")) {
        Spacer(Modifier.weight(.6f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(KpknLineIcons.Run, null, Modifier.size(19.dp), tint = LiveSheetTokens.Muted)
            Box(Modifier.width(48.dp).height(2.dp).background(Brush.horizontalGradient(listOf(Color(0x40FFFFFF), CardioInk))))
            Icon(KpknLineIcons.Training, null, Modifier.size(19.dp), tint = CardioInk)
        }
        Text("Ahora vamos\ncon fuerza", Modifier.padding(top = 18.dp).alpha(if (shown) 1f else 0f).semantics { heading() },
            style = CardioSyne.copy(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold), color = CardioInk)
        lead?.let { Text(it, Modifier.padding(top = 10.dp), style = CardioBody.copy(fontSize = 13.sp, lineHeight = 19.sp), color = LiveSheetTokens.Muted) }
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(shape).background(Color(0xFF111111)).border(1.dp, Color(0x10FFFFFF), shape).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x0BFFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(KpknLineIcons.Training, null, Modifier.size(22.dp), tint = CardioInk)
                }
                Column {
                    Text(exerciseName, style = CardioSyne.copy(fontSize = 15.sp, lineHeight = 20.sp), color = CardioInk)
                    Text(meta, style = CardioBody.copy(fontSize = 10.5.sp), color = LiveSheetTokens.Faint)
                }
            }
            if (firstSet != null) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Primera serie: $firstSet", Modifier.weight(1f), style = CardioBody.copy(fontSize = 12.sp), color = CardioInk)
                    planEffort?.let {
                        Text(it, Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 8.dp, vertical = 4.dp),
                            style = CardioBody.copy(fontSize = 10.sp), color = CardioInk)
                    }
                }
            }
            lastTime?.let {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(KpknLineIcons.History, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
                    Text("Última vez: $it", style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
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
            footnote?.let { Text(it, Modifier.padding(top = 6.dp), style = CardioBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint) }
        }
        Spacer(Modifier.weight(1f))
        CardioSwipeToStart(onContinue, Modifier.padding(bottom = 18.dp), label = "Desliza para continuar", actionLabel = "Continuar con fuerza")
    }
}

/** «Desliza para empezar»: el mando se arrastra hasta el final (con TalkBack, la acción accesible empieza al instante). */
@Composable
internal fun CardioSwipeToStart(onDone: () -> Unit, modifier: Modifier = Modifier, label: String = "Desliza para empezar",
    actionLabel: String = "Empezar") {
    val density = LocalDensity.current
    var offsetPx by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(31.dp)).background(Color(0x14FFFFFF)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(31.dp))
            .semantics { contentDescription = label; onClick(label = actionLabel) { onDone(); true } }.testTag("cardio-swipe"),
    ) {
        val maxPx = with(density) { (maxWidth - 62.dp).toPx() }.coerceAtLeast(1f)
        Box(Modifier.fillMaxHeight().width(with(density) { (offsetPx.toDp() + 62.dp) }).clip(RoundedCornerShape(31.dp)).background(Color(0x1AFFFFFF)))
        Text(label, Modifier.align(Alignment.Center), style = CardioBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = LiveSheetTokens.Muted)
        Box(
            Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) }.size(62.dp).padding(5.dp).clip(CircleShape).background(CardioInk)
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (offsetPx >= maxPx * .9f) { offsetPx = maxPx; onDone() } else offsetPx = 0f },
                        onDragCancel = { offsetPx = 0f },
                    ) { change, dx -> change.consume(); offsetPx = (offsetPx + dx).coerceIn(0f, maxPx) }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(KpknLineIcons.ChevronRight, null, Modifier.size(20.dp), tint = Color(0xFF111111)) }
    }
}
