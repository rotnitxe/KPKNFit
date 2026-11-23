package com.example.kpkn.screens.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknWaveEdgeFade
import com.example.kpkn.ui.design.motion.KpknWaveField
import com.example.kpkn.ui.design.motion.KpknWaveState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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

/** Icono de línea propio de la modalidad de cardio (usado en transiciones y lista). */
internal fun cardioTypeIcon(type: CardioType): ImageVector = when (type) {
    CardioType.WALK -> KpknLineIcons.Walk
    CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR, CardioType.AIR_BIKE -> KpknLineIcons.Bike
    CardioType.ROW_MACHINE -> KpknLineIcons.Row
    CardioType.STAIR_CLIMBER -> KpknLineIcons.Stairs
    else -> KpknLineIcons.Run
}

/** `.cv-tape`: cinco franjas (o diez de RPE); la objetivo, más alta y entera; la aguja crema marca el pulso con borde de contraste. */
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
                val animatedFraction by animateKpknFloatAsState(
                    (p / 100f).coerceIn(0f, 1f),
                    KpknDuration(900),
                    KpknMotion.Estandar,
                    label = "cv-nd",
                )
                val needleX = (maxWidth * animatedFraction - 4.dp).coerceIn(0.dp, (maxWidth - 8.dp).coerceAtLeast(0.dp))
                Box(
                    Modifier.offset(x = needleX).width(8.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp)).background(Color(0xFF111111))
                        .padding(horizontal = 2.dp),
                ) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(CardioInk).testTag("cardio-needle"))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { i, label ->
                Text(label, Modifier.weight(1f), style = CardioBody.copy(fontSize = 10.sp, fontWeight = if (i in target) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (i in target) CardioInk else LiveSheetTokens.Faint, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.cv-prof`: el perfil del plan (ancho por duración, alto por zona) con animación `cv-grow` escalonada y marcador suavizado. */
@Composable
internal fun CardioProfile(
    steps: List<CardioStep>,
    current: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    animateBars: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val total = steps.sumOf { it.seconds }.coerceAtLeast(1)
    val rawFraction = if (current >= 0) {
        val pos = steps.take(current.coerceAtLeast(0)).sumOf { it.seconds } +
            (steps.getOrNull(current)?.seconds?.times(progress)?.toInt() ?: 0)
        (pos.toFloat() / total).coerceIn(0f, 1f)
    } else 0f
    val playheadFraction by animateKpknFloatAsState(rawFraction, KpknDuration(900), KpknMotion.Lineal, label = "cv-ps")
    val barClock = rememberKpknClockMs(
        running = animateBars && !reduced,
        key = steps.size,
        endMs = 600f + steps.size * 30f,
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Perfil del plan" }.testTag("cardio-profile")) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            steps.forEachIndexed { i, step ->
                val zoneHeight = listOf(.34f, .5f, .66f, .86f, 1f)[(step.zone - 1).coerceIn(0, 4)]
                val baseAlpha = when {
                    current < 0 -> .85f
                    i < current -> .30f
                    i == current -> 1f
                    else -> .6f
                }
                Box(
                    Modifier.weight(step.seconds.toFloat().coerceAtLeast(1f)).fillMaxHeight(zoneHeight)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            if (animateBars && !reduced) {
                                val p = ((barClock.value - i * 30f) / 600f).coerceIn(0f, 1f)
                                val eased = KpknMotion.Estandar.transform(p)
                                scaleY = 0.05f + 0.95f * eased
                                alpha = eased
                            }
                        }
                        .clip(RoundedCornerShape(3.dp))
                        .background(cardioZoneColor(step.zone).copy(alpha = baseAlpha)),
                )
            }
        }
        if (current >= 0) {
            val psX = (maxWidth * playheadFraction - 2.dp).coerceIn(0.dp, (maxWidth - 4.dp).coerceAtLeast(0.dp))
            Box(Modifier.offset(x = psX).padding(top = 0.dp).width(4.dp).fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF111111))
                .padding(horizontal = 1.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)).background(CardioInk))
            }
        }
    }
}

/** `.cv-dock`: pestañas con arrastre lateral + Bloquear · Pausa/Reanudar · Saltar paso (o mantener para desbloquear). */
@Composable
internal fun CardioDock(
    page: Int,
    visiblePages: List<Int> = listOf(0, 1, 2),
    onPage: (Int) -> Unit,
    locked: Boolean,
    mainLabel: String,
    mainIcon: ImageVector,
    mainEnabled: Boolean,
    onMain: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    mainTag: String = "cardio-main",
    onDragTabs: ((deltaPages: Int) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(32.dp)
    val compact = LocalConfiguration.current.screenHeightDp < 800
    val tabsAlpha by animateKpknFloatAsState(if (locked) 0.35f else 1f, KpknDuration(300), KpknMotion.Suave, label = "cv-tabs-alpha")
    Column(
        modifier.fillMaxWidth().kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape)
            .border(1.dp, Color(0x1FFFFFFF), shape).padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp).testTag("cardio-dock"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(36.dp).alpha(tabsAlpha).pointerInput(locked, visiblePages, page) {
                if (locked || visiblePages.size <= 1) return@pointerInput
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dx) > 40f) {
                            val dir = if (dx < 0) 1 else -1
                            if (onDragTabs != null) onDragTabs(dir)
                            else {
                                val current = visiblePages.indexOf(page).coerceAtLeast(0)
                                val next = (current + dir).coerceIn(0, visiblePages.lastIndex)
                                onPage(visiblePages[next])
                            }
                        }
                        dx = 0f
                    },
                    onDragCancel = { dx = 0f },
                ) { _, amount -> dx += amount }
            },
        ) {
            val selectedPosition = visiblePages.indexOf(page).coerceAtLeast(0)
            val indicatorX by animateDpAsState(maxWidth * ((selectedPosition * 2 + 1) / (visiblePages.size * 2f)) - 11.dp,
                animationSpec = tween(KpknDuration(420).ms, easing = KpknMotion.Estandar), label = "cv-ind")
            Row(Modifier.fillMaxSize()) {
                visiblePages.forEach { pageIndex ->
                    val name = when (pageIndex) { 0 -> "Ahora"; 1 -> "Plan"; else -> "Datos" }
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = !locked, role = Role.Tab) { onPage(pageIndex) }
                        .semantics { contentDescription = name; selected = pageIndex == page }.testTag("cardio-page-$pageIndex"), contentAlignment = Alignment.Center) {
                        Text(name, style = CardioBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = if (pageIndex == page) CardioInk else LiveSheetTokens.Faint)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomStart).offset(x = indicatorX).size(22.dp, 3.dp).clip(CircleShape).background(CardioInk))
        }
        if (locked) {
            CardioHoldToUnlock(onUnlock, Modifier.padding(top = 4.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CardioCtrl("Bloquear", null, onLock, Modifier.weight(1f), compact = compact, lockGlyph = true, tag = "cardio-lock")
                CardioCtrl(mainLabel, mainIcon, onMain, Modifier.weight(1.5f), main = true, enabled = mainEnabled, compact = compact, tag = mainTag)
                // Sin pasos que saltar (cardio continuo o aún sin empezar) el mando no se enseña: nada de botones apagados que no hacen nada.
                if (onSkip != null) CardioCtrl("Saltar paso", KpknLineIcons.FrameForward, onSkip, Modifier.weight(1f), compact = compact, tag = "cardio-skip")
            }
        }
    }
}

/** `.cv-lockcap`: píldora de vidrio flotante sobre el dock cuando la pantalla está bloqueada. */
@Composable
internal fun CardioLockCapsule(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Row(
        modifier.height(34.dp).kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape)
            .border(1.dp, Color(0x1FFFFFFF), shape).padding(horizontal = 14.dp)
            .kpknAnimate(KpknFamilies.ReboteAparece.demo, restartKey = "lockcap")
            .testTag("cardio-lockcap"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CardioLockGlyph(CardioInk, Modifier.size(14.dp))
        Text("Pantalla bloqueada", style = CardioBody.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
    }
}

/** `.cv-toast`: aviso breve flotante sobre el dock. */
@Composable
internal fun CardioToastPill(
    text: String,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Box(
        modifier.height(38.dp).kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape)
            .border(1.dp, Color(0x1FFFFFFF), shape).padding(horizontal = 16.dp)
            .kpknAnimate(KpknFamilies.ReboteAparece.demo, restartKey = text)
            .testTag("cardio-toast"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CardioBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = CardioInk, maxLines = 1)
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
    compact: Boolean = false,
    lockGlyph: Boolean = false,
    tag: String,
) {
    val ink = if (main) Color(0xFF111111) else CardioInk
    val control = modifier.height(if (compact) 52.dp else 56.dp).clip(RoundedCornerShape(20.dp))
        .background(if (main) CardioInk else Color(0x0FFFFFFF)).alpha(if (enabled) 1f else .4f)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag)
    if (main) Row(control, horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, Modifier.size(20.dp), tint = ink) }
        Text(label, style = CardioBody.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = ink)
    } else Column(control, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
        if (lockGlyph) CardioLockGlyph(ink, Modifier.size(18.dp)) else icon?.let { Icon(it, null, Modifier.size(18.dp), tint = ink) }
        Text(label, style = CardioBody.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold), color = ink, textAlign = TextAlign.Center)
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

/** `.cv-veil`: «CARDIO EN PAUSA», reloj grande (54 sp) aparte del activo, botón crema de 210 dp y secundario alt, con desenfoque sobre ondas. */
@Composable
internal fun CardioPauseVeil(
    pausedSeconds: Int,
    stepLeft: String,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    bottomClearance: Dp = 144.dp,
) {
    Column(
        modifier.fillMaxSize()
            .then(
                if (hazeState != null) {
                    Modifier.kpknGlassPreset(hazeState, KpknGlassPreset.VeloDesenfoque, RectangleShape, additionalScrim = Color(0xB8000000))
                } else {
                    Modifier.background(Color(0xB8000000))
                },
            )
            .kpknAnimate(KpknFamilies.FundidoEntrada.demo, restartKey = "pause-veil")
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 34.dp, end = 34.dp, bottom = bottomClearance)
            .testTag("cardio-pause-veil"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text("CARDIO EN PAUSA", style = CardioBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
        Text(cardioClock(pausedSeconds), style = CardioSyne.copy(fontSize = 54.sp, lineHeight = 57.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp, fontFeatureSettings = "'tnum' 1"), color = CardioInk)
        Text("El tiempo en pausa no cuenta como activo. El paso actual queda en $stepLeft.", Modifier.padding(top = 2.dp, bottom = 12.dp).widthIn(max = 270.dp),
            style = CardioBody.copy(fontSize = 12.5.sp, lineHeight = 19.sp), color = LiveSheetTokens.Muted, textAlign = TextAlign.Center)
        CardioPillButton("Reanudar", onResume, primary = true, icon = KpknLineIcons.Play, modifier = Modifier.width(210.dp), tag = "cardio-resume")
        CardioPillButton("Terminar cardio", onFinish, primary = false, modifier = Modifier.width(210.dp), tag = "cardio-finish")
    }
}

/** `.cv-cnt`: cuenta atrás 3-2-1 a 168 sp con onda Warm + halo + anillo expansivo + cápsula difuminada + toque para saltar. */
@Composable
internal fun CardioCountdown(
    number: Int,
    subtitle: String,
    modifier: Modifier = Modifier,
    zoneColor: Color = cardioZoneColor(2),
    onSkip: (() -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val localHaze = remember { HazeState() }
    val density = LocalDensity.current
    val numShadow = Shadow(Color.Black, Offset.Zero, with(density) { 40.dp.toPx() })
    val numberClock = rememberKpknClockMs(running = !reduced, key = number, endMs = 950f)

    BoxWithConstraints(
        modifier.fillMaxSize().background(Color.Black)
            .then(if (onSkip != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onSkip) else Modifier)
            .testTag("cardio-countdown"),
    ) {
        val outerScope = this
        Box(
            Modifier.matchParentSize().hazeSource(state = localHaze)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(zoneColor.copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.24f),
                            radius = maxOf(size.width, size.height) * 0.66f,
                        ),
                    )
                },
        ) {
            val heroTop = 140.dp
            val heroHeight = 560.dp.coerceAtMost((outerScope.maxHeight - heroTop).coerceAtLeast(240.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = heroTop).height(heroHeight)) {
                val bandScope = this
                CvHalo(zoneColor, bandScope.maxHeight, topFraction = 0.50f)
                KpknWaveField(
                    KpknWaveState.Warm,
                    Modifier.fillMaxSize(),
                    zoneColor = zoneColor,
                    lines = 24,
                    introSeconds = 1.2f,
                    edgeFade = KpknWaveEdgeFade.None,
                    viewBoxHeight = 560f,
                    restartKey = "cnt-wave",
                )
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!reduced) {
                Box(
                    Modifier.offset(y = (-15).dp).size(150.dp).graphicsLayer {
                        val p = (numberClock.value / 900f).coerceIn(0f, 1f)
                        val e = KpknMotion.Salida.transform(p)
                        val s = 0.5f + (3.4f - 0.5f) * e
                        scaleX = s
                        scaleY = s
                        alpha = 0.9f * (1f - e)
                    }.border(2.dp, zoneColor, CircleShape),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EMPIEZA EN", Modifier.padding(bottom = 6.dp), style = CardioBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
                Text(
                    "$number",
                    Modifier.graphicsLayer {
                        if (!reduced) {
                            val p = (numberClock.value / 900f).coerceIn(0f, 1f)
                            val e = KpknMotion.Estandar.transform(p)
                            val s = 1.5f + (1f - 1.5f) * e
                            scaleX = s
                            scaleY = s
                            alpha = (p / 0.30f).coerceIn(0f, 1f)
                        }
                    },
                    style = CardioSyne.copy(fontSize = 168.sp, lineHeight = 168.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-6).sp, shadow = numShadow),
                    color = CardioInk,
                )
                Box(
                    Modifier.padding(top = 14.dp)
                        .kpknGlassPreset(localHaze, KpknGlassPreset.VeloSuave, RoundedCornerShape(18.dp), additionalScrim = Color(0xB8000000))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(subtitle, style = CardioBody.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium), color = CardioInk, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** `.cv-cell`: tarjeta de resumen medido en Cardio (20 dp, 26 sp Syne, opcionalmente editable con borde y lápiz). */
@Composable
internal fun CardioSumCell(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (editable) Color(0x2EF2EEE6) else Color(0x10FFFFFF)
    Column(modifier.clip(shape).background(Color(0xFF111111)).border(1.dp, borderColor, shape).padding(horizontal = 14.dp, vertical = 13.dp)) {
        Text(label, style = CardioBody.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold), color = LiveSheetTokens.Faint)
        Text(value, Modifier.padding(top = 5.dp), style = CardioSyne.copy(fontSize = 26.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "'tnum' 1"), color = CardioInk, maxLines = 1)
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editable) Icon(KpknLineIcons.Edit, null, Modifier.size(12.dp), tint = LiveSheetTokens.Muted)
            Text(note, style = CardioBody.copy(fontSize = 11.5.sp), color = if (editable) LiveSheetTokens.Muted else LiveSheetTokens.Faint)
        }
    }
}

/** `.cv-btn` y `.cv-btn.alt` del prototipo (52 dp, radio 26 dp, relleno crema o translúcido). */
@Composable
internal fun CardioPillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    tag: String? = null,
) {
    val bg = if (primary) CardioInk else Color(0x14FFFFFF)
    val fg = if (primary) Color(0xFF111111) else CardioInk
    Row(
        modifier.height(height).alpha(if (enabled) 1f else 0.38f).clip(RoundedCornerShape(height / 2))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(height / 2)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(label, style = CardioBody.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
internal fun CardioPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(bottom = 8.dp), style = CardioBody.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
        color = LiveSheetTokens.Faint)
}

/** «¿Cuánto te costó?»: 50 dp, radio 12 dp, sube 3 dp al seleccionarse, referencia del plan abajo. */
@Composable
internal fun CardioRpePicker(selected: Int?, planned: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().testTag("cardio-rpe"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..10).forEach { n ->
            val on = selected == n
            val riseY by animateDpAsState(if (on) (-3).dp else 0.dp, tween(250), label = "cv-rpe-y")
            Box(
                Modifier.weight(1f).offset(y = riseY).heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) CardioInk else Color(0x0DFFFFFF))
                    .drawBehind {
                        if (n == planned && !on) drawLine(Color(0x59FFFFFF), Offset(6.dp.toPx(), size.height - 5.dp.toPx()),
                            Offset(size.width - 6.dp.toPx(), size.height - 5.dp.toPx()), 2.dp.toPx())
                    }
                    .clickable(role = Role.RadioButton) { onSelect(n) }
                    .semantics { contentDescription = "Esfuerzo $n" + if (n == planned) " · plan" else ""; this.selected = on }
                    .testTag("cardio-rpe-$n"),
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", style = CardioSyne.copy(fontSize = 14.sp), color = if (on) Color(0xFF111111) else CardioInk)
            }
        }
    }
}

@Composable
private fun Modifier.cvStaggeredRise(clockMs: () -> Float, delayMs: Float, durationMs: Float, reduced: Boolean): Modifier {
    val risePx = with(LocalDensity.current) { 14.dp.toPx() }
    return graphicsLayer {
        if (reduced) {
            alpha = 1f
            translationY = 0f
        } else {
            val p = ((clockMs() - delayMs) / durationMs).coerceIn(0f, 1f)
            val e = KpknMotion.Estandar.transform(p)
            alpha = e
            translationY = risePx * (1f - e)
        }
    }
}

/** Tira `.cv-route` compartida entre `CardioTransitionIn` y `CardioTransitionOut`: nodo origen, línea que crece en 1 s y nodo destino. */
@Composable
private fun CardioTransitionRouteRow(
    fromIcon: ImageVector,
    toIcon: ImageVector,
    lineBrush: Brush,
    toAccent: Color,
    toDashed: Boolean,
    ignite: Boolean,
    clockMs: () -> Float,
    reduced: Boolean,
) {
    val glowRadiusPx = with(LocalDensity.current) { 22.dp.toPx() }
    val pulseRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x0DFFFFFF)), contentAlignment = Alignment.Center) {
            Icon(fromIcon, null, Modifier.size(20.dp), tint = CardioInk)
        }
        Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0x14FFFFFF))) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = if (reduced) 1f else KpknMotion.Ruta.transform(((clockMs() - 250f) / 1000f).coerceIn(0f, 1f))
                }.background(lineBrush),
            )
        }
        Box(
            Modifier.size(42.dp)
                .then(
                    if (ignite) Modifier.drawBehind {
                        val p = if (reduced) 1f else ((clockMs() - 1100f) / 800f).coerceIn(0f, 1f)
                        if (p > 0f) {
                            if (p < 0.4f) {
                                val u = p / 0.4f
                                drawCircle(toAccent.copy(alpha = 0.30f * u), radius = size.minDimension / 2f + pulseRadiusPx * u)
                            } else {
                                val u = (p - 0.4f) / 0.6f
                                drawCircle(
                                    Brush.radialGradient(
                                        colors = listOf(toAccent.copy(alpha = 0.40f * u), Color.Transparent),
                                        center = center,
                                        radius = size.minDimension / 2f + glowRadiusPx,
                                    ),
                                    radius = size.minDimension / 2f + glowRadiusPx,
                                )
                            }
                        }
                    } else Modifier,
                )
                .clip(CircleShape)
                .then(
                    if (toDashed) Modifier.drawBehind {
                        drawCircle(Color(0x55FFFFFF), size.minDimension / 2f - 0.5.dp.toPx(),
                            style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                    } else Modifier.background(toAccent.copy(alpha = 0.16f)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(toIcon, null, Modifier.size(20.dp), tint = if (toDashed) CardioInk else toAccent)
        }
    }
}

/**
 * «Ahora vamos con cardio» (sesión mixta): olas full-screen `Work` + halo + trayectoria ignita + líneas escalonadas + tarjeta + `.cv-sw` vidrio.
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
    cardioType: CardioType = CardioType.RUN_OUTDOOR,
    hazeState: HazeState? = null,
) {
    val reduced = wizardReducedMotion()
    val zoneColor = cardioZoneColor(firstStep?.zone ?: 2)
    val localHaze = hazeState ?: remember { HazeState() }
    val clock = rememberKpknClockMs(running = !reduced, key = "trans-in", endMs = 2000f)
    val tMs = { if (reduced) 2000f else clock.value }
    val titleLines = remember(title) { title.split('\n').filter { it.isNotBlank() }.ifEmpty { listOf("Ahora vamos", "con cardio") } }
    val typeIcon = cardioTypeIcon(cardioType)

    Box(modifier.fillMaxSize().background(Color.Black).testTag("cardio-transition-in")) {
        Box(
            Modifier.matchParentSize().hazeSource(state = localHaze).drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(zoneColor.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.24f),
                        radius = maxOf(size.width, size.height) * 0.66f,
                    ),
                )
            },
        ) {
            KpknWaveField(
                KpknWaveState.Work,
                Modifier.fillMaxSize(),
                zoneColor = zoneColor,
                lines = 30,
                introSeconds = 2.2f,
                edgeFade = KpknWaveEdgeFade.TransitionIn,
                restartKey = "trans-in-wave",
            )
        }
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp)) {
            CardioTransitionRouteRow(
                fromIcon = KpknLineIcons.Training,
                toIcon = typeIcon,
                lineBrush = Brush.horizontalGradient(listOf(Color(0x88FFFFFF), zoneColor)),
                toAccent = zoneColor,
                toDashed = false,
                ignite = true,
                clockMs = tMs,
                reduced = reduced,
            )
            Column(Modifier.padding(top = 20.dp).semantics { heading() }) {
                titleLines.forEachIndexed { idx, line ->
                    Text(
                        line,
                        Modifier.cvStaggeredRise(tMs, delayMs = 350f + idx * 150f, durationMs = 600f, reduced = reduced),
                        style = CardioSyne.copy(fontSize = 34.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp),
                        color = CardioInk,
                    )
                }
            }
            lead?.let {
                Text(
                    it,
                    Modifier.padding(top = 12.dp).widthIn(max = 300.dp).cvStaggeredRise(tMs, delayMs = 750f, durationMs = 500f, reduced = reduced),
                    style = CardioBody.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = LiveSheetTokens.Muted,
                )
            }
            Spacer(Modifier.weight(1f))
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    .cvStaggeredRise(tMs, delayMs = 950f, durationMs = 600f, reduced = reduced)
                    .clip(shape).background(Color(0xFF111111)).border(1.dp, Color(0x12FFFFFF), shape).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) {
                        Icon(typeIcon, null, Modifier.size(22.dp), tint = CardioInk)
                    }
                    Column {
                        Text(exerciseName, style = CardioSyne.copy(fontSize = 16.sp, lineHeight = 20.sp), color = CardioInk)
                        Text(meta, style = CardioBody.copy(fontSize = 11.5.sp), color = LiveSheetTokens.Faint)
                    }
                }
                firstStep?.let { step ->
                    Row(Modifier.fillMaxWidth().padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Primer bloque: ${step.name} ${cardioClock(step.seconds)}", Modifier.weight(1f), style = CardioBody.copy(fontSize = 12.5.sp), color = CardioInk)
                        Row(Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(cardioZoneColor(step.zone)))
                            Text("Zona ${step.zone}", style = CardioBody.copy(fontSize = 11.sp), color = CardioInk)
                        }
                    }
                    if (step.cue.isNotBlank()) {
                        Text("Ajusta a ${step.cue}", Modifier.padding(top = 8.dp), style = CardioBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
                    }
                }
                CardioProfile(steps, current = -1, progress = 0f, modifier = Modifier.padding(top = 10.dp), animateBars = !reduced)
                Text(totalLabel, Modifier.padding(top = 7.dp), style = CardioBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
            }
            if (steps.size > 1) {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xD91B1B1D))
                            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button, onClick = onSeePlan)
                            .padding(horizontal = 18.dp).testTag("cardio-see-plan"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Ver el plan", style = CardioBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
                    }
                }
            }
            CardioSwipeToStart(
                onSwipeStart,
                Modifier.cvStaggeredRise(tMs, delayMs = 1250f, durationMs = 500f, reduced = reduced),
                hazeState = localHaze,
                arrow = true,
            )
        }
    }
}

/**
 * «Ahora vamos con fuerza»: olas `Done` en blanco full-screen, trayectoria inversa hacia mancuerna punteada, tarjeta de siguiente serie y swipe.
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
    cardioType: CardioType = CardioType.RUN_OUTDOOR,
    hazeState: HazeState? = null,
) {
    val reduced = wizardReducedMotion()
    val localHaze = hazeState ?: remember { HazeState() }
    val clock = rememberKpknClockMs(running = !reduced, key = "trans-out", endMs = 2000f)
    val tMs = { if (reduced) 2000f else clock.value }
    val typeIcon = cardioTypeIcon(cardioType)

    Box(modifier.fillMaxSize().background(Color.Black).testTag("cardio-transition-out")) {
        Box(Modifier.matchParentSize().hazeSource(state = localHaze)) {
            KpknWaveField(
                KpknWaveState.Done,
                Modifier.fillMaxSize(),
                zoneColor = Color.White,
                lines = 26,
                introSeconds = 1.8f,
                edgeFade = KpknWaveEdgeFade.TransitionOut,
                restartKey = "trans-out-wave",
            )
        }
        Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 22.dp)) {
            CardioTransitionRouteRow(
                fromIcon = typeIcon,
                toIcon = KpknLineIcons.Training,
                lineBrush = Brush.horizontalGradient(listOf(CardioInk, CardioInk)),
                toAccent = CardioInk,
                toDashed = true,
                ignite = false,
                clockMs = tMs,
                reduced = reduced,
            )
            Column(Modifier.padding(top = 20.dp).semantics { heading() }) {
                listOf("Ahora vamos", "con fuerza").forEachIndexed { idx, line ->
                    Text(
                        line,
                        Modifier.cvStaggeredRise(tMs, delayMs = 350f + idx * 150f, durationMs = 600f, reduced = reduced),
                        style = CardioSyne.copy(fontSize = 34.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp),
                        color = CardioInk,
                    )
                }
            }
            lead?.let {
                Text(
                    it,
                    Modifier.padding(top = 12.dp).widthIn(max = 300.dp).cvStaggeredRise(tMs, delayMs = 750f, durationMs = 500f, reduced = reduced),
                    style = CardioBody.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = LiveSheetTokens.Muted,
                )
            }
            Spacer(Modifier.weight(1f))
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    .cvStaggeredRise(tMs, delayMs = 900f, durationMs = 600f, reduced = reduced)
                    .clip(shape).background(Color(0xFF111111)).border(1.dp, Color(0x12FFFFFF), shape).padding(15.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) {
                        Icon(KpknLineIcons.Training, null, Modifier.size(22.dp), tint = CardioInk)
                    }
                    Column {
                        Text(exerciseName, style = CardioSyne.copy(fontSize = 16.sp, lineHeight = 20.sp), color = CardioInk)
                        Text(meta, style = CardioBody.copy(fontSize = 11.5.sp), color = LiveSheetTokens.Faint)
                    }
                }
                if (firstSet != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            append("Primera serie: ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = CardioInk, fontWeight = FontWeight.SemiBold)); append(firstSet); pop()
                        }, Modifier.weight(1f), style = CardioBody.copy(fontSize = 12.5.sp), color = LiveSheetTokens.Muted)
                        planEffort?.let {
                            Text(it, Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x0DFFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp),
                                style = CardioBody.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = CardioInk)
                        }
                    }
                }
                lastTime?.let { last ->
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp).height(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x08FFFFFF))
                        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(KpknLineIcons.History, null, Modifier.size(16.dp), tint = LiveSheetTokens.Muted)
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            append("Última vez: ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = CardioInk, fontWeight = FontWeight.SemiBold)); append(last); pop()
                        }, style = CardioBody.copy(fontSize = 12.5.sp), color = LiveSheetTokens.Muted)
                    }
                }
                if (chips.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        chips.forEach { label ->
                            val warm = label.startsWith("A")
                            Box(Modifier.weight(if (warm) 1f else 2f).height(30.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (warm) Color(0x08FFFFFF) else Color(0x12FFFFFF)), contentAlignment = Alignment.Center) {
                                Text(label, style = CardioSyne.copy(fontSize = 11.sp), color = LiveSheetTokens.Muted)
                            }
                        }
                    }
                }
                footnote?.let { Text(it, Modifier.padding(top = 8.dp), style = CardioBody.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = LiveSheetTokens.Faint) }
            }
            CardioSwipeToStart(
                onContinue,
                Modifier.cvStaggeredRise(tMs, delayMs = 1250f, durationMs = 500f, reduced = reduced),
                label = "Desliza para continuar",
                actionLabel = "Continuar con fuerza",
                arrow = true,
                hazeState = localHaze,
            )
        }
    }
}

/** «Desliza para empezar» (`.cv-sw`): pista de vidrio `VidrioInterruptor`, shimmer `cv-shim`, retorno resorte y flecha `cvarrow`. */
@Composable
internal fun CardioSwipeToStart(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Desliza para empezar",
    actionLabel: String = "Empezar",
    arrow: Boolean = true,
    hazeState: HazeState? = null,
) {
    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    val scope = rememberCoroutineScope()
    val latestOnDone by rememberUpdatedState(onDone)
    val offsetPx = remember { Animatable(0f) }
    var completed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(completed) {
        if (completed) {
            kotlinx.coroutines.delay(1200L)
            completed = false
            offsetPx.snapTo(0f)
        }
    }
    val shimmerClock = rememberKpknClockMs(running = !reduced && !completed, key = "cv-shim", loopMs = 2600f)

    BoxWithConstraints(
        modifier.fillMaxWidth().height(62.dp)
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioInterruptor, RoundedCornerShape(31.dp), additionalScrim = Color(0xC21B1B1D))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(31.dp))
            .clip(RoundedCornerShape(31.dp))
            .clickable(role = Role.Button) {
                if (!completed) {
                    completed = true
                    latestOnDone()
                }
            }
            .semantics { contentDescription = label; onClick(label = actionLabel) { latestOnDone(); true } }
            .testTag("cardio-swipe"),
    ) {
        val maxPx = with(density) { (maxWidth - 60.dp).toPx() }.coerceAtLeast(1f)
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val fillWidthDp = with(density) { (offsetPx.value.toDp() + 62.dp).coerceIn(62.dp, maxWidth) }
        val labelAlpha = (1f - (offsetPx.value / maxPx) * 1.3f).coerceIn(0f, 1f)

        Box(Modifier.fillMaxHeight().width(fillWidthDp).clip(RoundedCornerShape(31.dp)).background(Color(if (completed) 0xFF3A3936 else 0x18FFFFFF)))
        Box(Modifier.fillMaxSize().padding(start = 34.dp).alpha(labelAlpha), contentAlignment = Alignment.Center) {
            val brush = if (!reduced) {
                val t = (shimmerClock.value / 2600f).coerceIn(0f, 1f)
                val cx = (1.2f - 1.8f * t) * widthPx
                Brush.linearGradient(
                    colors = listOf(Color(0xA6FFFFFF), Color(0xA6FFFFFF), Color.White, Color(0xA6FFFFFF), Color(0xA6FFFFFF)),
                    start = Offset(cx - widthPx * 0.45f, 0f),
                    end = Offset(cx + widthPx * 0.45f, 0f),
                )
            } else null
            Text(
                label,
                style = CardioBody.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold).let { base ->
                    if (brush != null) base.copy(brush = brush) else base.copy(color = LiveSheetTokens.Muted)
                },
            )
        }
        Icon(KpknLineIcons.DoubleChevronRight, null, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(18.dp).alpha(labelAlpha), tint = Color(0x40FFFFFF))
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetPx.value >= maxPx * 0.65f && !completed) {
                                completed = true
                                scope.launch {
                                    offsetPx.animateTo(maxPx, tween(220, easing = KpknMotion.Estandar))
                                    latestOnDone()
                                }
                            } else if (!completed) {
                                scope.launch {
                                    offsetPx.animateTo(0f, tween(300, easing = KpknMotion.Estandar))
                                }
                            }
                        },
                        onDragCancel = {
                            if (!completed) {
                                scope.launch { offsetPx.animateTo(0f, tween(300, easing = KpknMotion.Estandar)) }
                            }
                        },
                    ) { change, dx ->
                        change.consume()
                        if (!completed) {
                            scope.launch { offsetPx.snapTo((offsetPx.value + dx).coerceIn(0f, maxPx)) }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .graphicsLayer { translationX = offsetPx.value }
                    .padding(5.dp).size(50.dp).clip(CircleShape).background(CardioInk),
                contentAlignment = Alignment.Center,
            ) {
                if (arrow) {
                    Canvas(Modifier.size(20.dp)) {
                        val w = 1.9.dp.toPx(); val y = size.height / 2f
                        val ink = Color(0xFF111111)
                        drawLine(ink, Offset(3.dp.toPx(), y), Offset(size.width - 3.dp.toPx(), y), w, StrokeCap.Round)
                        drawLine(ink, Offset(size.width - 8.5.dp.toPx(), y - 5.5.dp.toPx()), Offset(size.width - 3.dp.toPx(), y), w, StrokeCap.Round)
                        drawLine(ink, Offset(size.width - 8.5.dp.toPx(), y + 5.5.dp.toPx()), Offset(size.width - 3.dp.toPx(), y), w, StrokeCap.Round)
                    }
                } else {
                    Icon(KpknLineIcons.ChevronRight, null, Modifier.size(20.dp), tint = Color(0xFF111111))
                }
            }
        }
    }
}
