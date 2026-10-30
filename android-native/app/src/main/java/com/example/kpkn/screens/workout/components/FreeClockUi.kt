package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.domain.workout.FreeClock
import com.example.kpkn.domain.workout.FreeClockState
import com.example.kpkn.domain.workout.FreeTimerPhase
import com.example.kpkn.domain.workout.LapMark
import com.example.kpkn.domain.workout.LapRow
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknKeyframes
import com.example.kpkn.ui.design.motion.rememberKpknPose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Cronómetro y temporizador libres de la hoja «Tiempo» (Pulido v1 · punto 16). Escenas `lx-t-*` del lienzo (`src/scenes-lx.js`,
 * `src/lx.css`). Todo el tiempo se calcula con la hora que lee [rememberFrameNowMs] y el estado de `FreeClock`; aquí solo se dibuja.
 */

/** Tokens del paquete (los `--var` del lienzo). */
internal object Lx {
    val Ink = WizardColors.text
    val Muted = WizardColors.textMuted
    val Faint = WizardColors.textFaint
    val Dim = Color(0xFF5F5C57)
    val Done = WizardColors.done
    val Mus = Color(0xFFF49A6E)
    val Danger = WizardColors.danger
    val Div = Color(0x0FFFFFFF)
    val ChipFill = Color(0x0BFFFFFF)
    val OnInk = Color(0xFF111111)

    fun inter(size: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified, line: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = WizardFonts.body, fontSize = size, fontWeight = weight, letterSpacing = letterSpacing, lineHeight = line,
            // CSS reparte el interlineado por igual arriba y abajo del glifo; sin esto, con `line-height: 1` el texto cae más bajo que en el lienzo.
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None))

    /** `.lx-num`: Inter 600 con cifras tabulares. */
    fun num(size: TextUnit, letterSpacing: TextUnit = TextUnit.Unspecified, weight: FontWeight = FontWeight.SemiBold) =
        inter(size, weight, letterSpacing).copy(fontFeatureSettings = "tnum, lnum")
}

/** Todo lo que la hoja puede pedirle al reloj libre. */
internal class FreeClockActions(
    val onStopwatchToggle: () -> Unit = {},
    val onStopwatchLapOrReset: () -> Unit = {},
    val onKey: (String) -> Unit = {},
    val onPreset: (Int) -> Unit = {},
    val onSound: (Boolean) -> Unit = {},
    val onVibrate: (Boolean) -> Unit = {},
    val onStart: () -> Unit = {},
    val onPauseResume: () -> Unit = {},
    val onAdd: (Int) -> Unit = {},
    val onBackToConfig: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    /** La interfaz ve que el fin ya llegó (por si la alarma tarda): idempotente. */
    val onFinishIfDue: () -> Unit = {},
)

/** Hora de pared que avanza con el reloj de cuadros mientras [active] y la pantalla está a la vista (se detiene en segundo plano). */
@Composable
internal fun rememberFrameNowMs(active: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        now.longValue = System.currentTimeMillis()
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) withFrameMillis { now.longValue = System.currentTimeMillis() }
        }
    }
    return now
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Piezas sueltas: chips, botones, pestañas
// ---------------------------------------------------------------------------------------------------------------------------------

/** `.lx-chip`: 40 dp de alto, radio 20, relleno blanco al 4 %; elegido, en tinta. */
@Composable
internal fun LxChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Boolean = false,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.height(40.dp).clip(shape).background(if (on) Lx.Ink else Lx.ChipFill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = on }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = if (fill) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (on) Lx.OnInk else Lx.Muted
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = ink)
        Text(label, style = Lx.num(13.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = ink, maxLines = 1, softWrap = false)
    }
}

/** `.pl-btn`: 52 dp, píldora en tinta; `ghost` = relleno blanco al 6 % con borde. [enabled] false = opacidad .38. */
@Composable
internal fun LxButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    horizontalPadding: Dp = 12.dp,
    fontSize: TextUnit = 15.sp,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier.height(height).graphicsLayer { alpha = if (enabled) 1f else 0.38f }.clip(shape)
            .background(if (ghost) Color(0x0FFFFFFF) else Lx.Ink)
            .then(if (ghost) Modifier.border(1.dp, Color(0x1FFFFFFF), shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (ghost) Lx.Ink else Lx.OnInk
        if (icon != null) Icon(icon, null, Modifier.size(if (icon == FreeClockIcons.Play) 16.dp else 17.dp), tint = ink)
        Text(label, style = Lx.inter(fontSize, FontWeight.SemiBold), color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** `.lx-sec`: rótulo de sección, 10,5 sp en mayúsculas con 1,4 de espaciado. */
@Composable
internal fun LxSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
}

/** `.lx-seg`: pestañas Sesión · Cronómetro · Temporizador en un carril de 24 dp de radio; el punto verde marca el reloj en marcha. */
@Composable
internal fun LxTabs(selected: Int, onSelect: (Int) -> Unit, stopwatchRunning: Boolean, timerRunning: Boolean, modifier: Modifier = Modifier) {
    val labels = listOf("Sesión", "Cronómetro", "Temporizador")
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x0AFFFFFF)).padding(4.dp)
            .semantics { contentDescription = "Reloj" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val running = (i == 1 && stopwatchRunning) || (i == 2 && timerRunning)
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Lx.Ink else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics {
                        this.selected = on
                        if (running) stateDescription = "En marcha"
                    }
                    .testTag("time-tab-$i").padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Lx.inter(13.sp, FontWeight.SemiBold), color = if (on) Lx.OnInk else Lx.Muted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                if (running) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 4.dp).size(6.dp).clip(RoundedCornerShape(50)).background(Lx.Done))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Esfera del cronómetro (SVG `lxDial` del lienzo, viewBox 240 × 240)
// ---------------------------------------------------------------------------------------------------------------------------------

/**
 * Esfera con 60 marcas, números 60 · 15 · 45, segundero (una vuelta por minuto), subesfera ([subMaxMinutes] > 0) y arco exterior.
 * Con [goalMinutes] el arco avanza hacia el objetivo (y se vuelve coral al pasarlo, trazo de 7); sin objetivo es la vuelta del minuto
 * (trazo de 3 al 50 %). El tiempo se lee al dibujar, así que cada cuadro solo repinta la esfera.
 */
@Composable
internal fun ClockDial(ms: () -> Long, goalMinutes: Int, subMaxMinutes: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val numberStyle = remember { Lx.inter(11.sp, FontWeight.SemiBold) }
    Canvas(modifier.aspectRatio(1f)) {
        val u = size.minDimension / 240f
        fun pt(x: Float, y: Float) = Offset(x * u, y * u)
        val value = ms()
        val goal = goalMinutes > 0
        val ring = (if (goal) 7f else 3f) * u
        drawCircle(Color(0xFF0D0D0D), 118f * u, pt(120f, 120f))
        drawCircle(Color(0x14FFFFFF), 118f * u, pt(120f, 120f), style = Stroke(1f * u))
        drawCircle(Color(0x0DFFFFFF), 112f * u, pt(120f, 120f), style = Stroke(ring))
        val fraction = FreeClock.dialArcFraction(value, goalMinutes)
        val arcColor = if (goal && value > goalMinutes * 60_000L) Color(0xFFFF9B92) else Lx.Ink
        if (fraction > 0f) drawArc(
            color = arcColor.copy(alpha = if (goal) 0.95f else 0.5f), startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
            topLeft = pt(8f, 8f), size = Size(224f * u, 224f * u), style = Stroke(ring, cap = StrokeCap.Round),
        )
        for (i in 0 until 60) {
            val major = i % 5 == 0
            rotate(i * 6f, pt(120f, 120f)) {
                drawLine(Lx.Ink.copy(alpha = if (major) 0.9f else 0.32f), pt(120f, if (major) 16f else 19f), pt(120f, if (major) 29f else 24f),
                    strokeWidth = (if (major) 2f else 1f) * u, cap = StrokeCap.Round)
            }
        }
        listOf("60" to 0f, "15" to 90f, "45" to 270f).forEach { (label, deg) ->
            val rad = deg * PI.toFloat() / 180f
            val cx = 120f + sin(rad) * 74f
            val cy = 120f - cos(rad) * 74f
            val layout = measurer.measure(label, numberStyle.copy(color = Lx.Faint, fontSize = (11f * u).toSp()))
            // El texto SVG se ancla en su línea base (y + 4) y se centra en x.
            drawText(layout, topLeft = Offset(cx * u - layout.size.width / 2f, (cy + 4f) * u - layout.firstBaseline))
        }
        if (subMaxMinutes > 0) {
            drawCircle(Color(0x06FFFFFF), 26f * u, pt(120f, 168f))
            drawCircle(Color(0x26FFFFFF), 26f * u, pt(120f, 168f), style = Stroke(1f * u))
            for (i in 0 until 6) rotate(i * 60f, pt(120f, 168f)) {
                drawLine(Lx.Ink.copy(alpha = 0.65f), pt(120f, 145f), pt(120f, 150f), strokeWidth = 1.4f * u, cap = StrokeCap.Round)
            }
            rotate(FreeClock.subHandDegrees(value, subMaxMinutes), pt(120f, 168f)) {
                drawLine(Lx.Ink, pt(120f, 174f), pt(120f, 150f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
            drawCircle(Lx.Ink, 2.6f * u, pt(120f, 168f))
        }
        rotate(FreeClock.secondHandDegrees(value), pt(120f, 120f)) {
            drawLine(Lx.Ink, pt(120f, 142f), pt(120f, 22f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            drawLine(Lx.Ink, pt(120f, 120f), pt(120f, 146f), strokeWidth = 5f * u, cap = StrokeCap.Round)
        }
        drawCircle(Lx.Ink, 6f * u, pt(120f, 120f))
        drawCircle(Color(0xFF0D0D0D), 2.2f * u, pt(120f, 120f))
    }
}

/** Anillo del temporizador (`lxTimerRing`): pista, arco que se vacía y 60 marcas interiores. [pulse] = 0 sin pulso; 1 = el más grueso. */
@Composable
internal fun TimerRing(fraction: () -> Float, modifier: Modifier = Modifier, pulsing: Boolean = false) {
    val pulse = rememberKpknPose(LxPulse, key = pulsing)
    Canvas(modifier.aspectRatio(1f)) {
        val u = size.minDimension / 240f
        fun pt(x: Float, y: Float) = Offset(x * u, y * u)
        // `lxpulse`: el trazo pasa de 7 a 11 mientras la opacidad baja a 0,7 (dos vueltas de 1,4 s al terminar).
        val k = if (pulsing) 1f - pulse.pose().alpha.coerceIn(0.7f, 1f) / 1f else 0f
        val arcWidth = (7f + 4f * (k / 0.3f)) * u
        val arcAlpha = if (pulsing) pulse.pose().alpha else 1f
        drawCircle(Color(0x10FFFFFF), 108f * u, pt(120f, 120f), style = Stroke(7f * u))
        val f = fraction().coerceIn(0f, 1f)
        if (f > 0f) drawArc(
            Lx.Ink.copy(alpha = arcAlpha), -90f, 360f * f, false, topLeft = pt(12f, 12f), size = Size(216f * u, 216f * u),
            style = Stroke(arcWidth, cap = StrokeCap.Round),
        )
        for (i in 0 until 60) {
            val major = i % 5 == 0
            rotate(i * 6f, pt(120f, 120f)) {
                drawLine(Lx.Ink.copy(alpha = if (major) 0.35f else 0.14f), pt(120f, if (major) 26f else 28f), pt(120f, if (major) 34f else 31f),
                    strokeWidth = (if (major) 1.6f else 1f) * u, cap = StrokeCap.Round)
            }
        }
    }
}

/** `@keyframes lxpulse` (0 % y 100 %: opacidad 1; 50 %: 0,7) · 1,4 s · ease-in-out · dos vueltas. El grosor sale de la opacidad. */
private val LxPulse: KpknAnimation by lazy {
    KpknAnimation(
        kpknKeyframes("lxpulse", "@keyframes lxpulse { 0%, 100% { stroke-width: 7; opacity: 1; } 50% { stroke-width: 11; opacity: 0.7; } }") {
            at(0f) { alpha(1f) }
            at(0.5f) { alpha(0.7f) }
            at(1f) { alpha(1f) }
        },
        KpknDuration(1400), KpknMotion.curve("vaiven").easing, iterations = 2f,
    )
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Cronómetro
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
internal fun StopwatchPane(state: FreeClockState, nowMs: State<Long>, actions: FreeClockActions, modifier: Modifier = Modifier) {
    val sw = state.stopwatch
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // `.lx-ro`: 54 sp Inter 600, décimas al 52 % en gris tenue.
        Row(Modifier.fillMaxWidth().testTag("stopwatch-readout"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            val main by remember(sw, nowMs) { derivedMs { FreeClock.formatStopwatchMain(FreeClock.stopwatchElapsedMs(sw, nowMs.value)) } }
            val tenths by remember(sw, nowMs) { derivedMs { FreeClock.formatStopwatchTenths(FreeClock.stopwatchElapsedMs(sw, nowMs.value)) } }
            Text(main, Modifier.alignByBaseline(), style = Lx.num(54.sp, (-1.8).sp, FontWeight.SemiBold).copy(lineHeight = 54.sp), color = Lx.Ink)
            Text(tenths, Modifier.padding(start = 2.dp).alignByBaseline(), style = Lx.num(28.sp, 0.sp, FontWeight.SemiBold).copy(lineHeight = 28.sp), color = Lx.Faint)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ClockDial({ FreeClock.stopwatchElapsedMs(sw, nowMs.value) }, goalMinutes = 0, subMaxMinutes = 30,
                modifier = Modifier.heightIn(max = 236.dp).fillMaxHeight().testTag("stopwatch-dial"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val idle = sw.idle
            val resets = !sw.running && !idle
            LxButton(if (resets) "Reiniciar" else "Vuelta", actions.onStopwatchLapOrReset, Modifier.weight(1f), ghost = true,
                icon = if (resets) FreeClockIcons.Reset else FreeClockIcons.Flag, enabled = !idle, tag = "stopwatch-lap")
            LxButton(if (sw.running) "Pausar" else if (sw.accumulatedMs > 0) "Seguir" else "Iniciar", actions.onStopwatchToggle, Modifier.weight(1f),
                icon = if (sw.running) FreeClockIcons.Pause else FreeClockIcons.Play, tag = "stopwatch-go")
        }
        LapTable(state, nowMs, Modifier.fillMaxWidth().height(176.dp))
    }
}

/** Función de lectura sin estado propio: se reevalúa cuando cambia `nowMs` (se lee dentro de [androidx.compose.runtime.derivedStateOf]). */
private fun <T> derivedMs(block: () -> T): State<T> = androidx.compose.runtime.derivedStateOf(block)

@Composable
private fun LapTable(state: FreeClockState, nowMs: State<Long>, modifier: Modifier = Modifier) {
    val rows by remember(state.stopwatch, nowMs) { derivedMs { FreeClock.lapRows(state.stopwatch, nowMs.value) } }
    Column(modifier.testTag("stopwatch-laps")) {
        if (rows.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().topLine().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FreeClockIcons.Flag, null, Modifier.size(16.dp), tint = Lx.Faint)
                Text("Las vueltas guardan el parcial y el total de cada una.", style = Lx.inter(12.5.sp, line = 17.5.sp), color = Lx.Faint, textAlign = TextAlign.Center)
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 4.dp)) {
            val head = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.3.sp)
            Text("VUELTA", Modifier.weight(1.35f), style = head, color = Lx.Faint)
            Text("PARCIAL", Modifier.weight(1f), style = head, color = Lx.Faint, textAlign = TextAlign.End)
            Text("TOTAL", Modifier.weight(1f), style = head, color = Lx.Faint, textAlign = TextAlign.End)
        }
        // `mask-image: linear-gradient(#000 calc(100% - 22px), transparent)`: la tabla se desvanece en su borde inferior (lx-X05).
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                drawContent()
                val fade = 22.dp.toPx().coerceAtMost(size.height)
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                    topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade), blendMode = BlendMode.DstIn)
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            items(rows, key = { it.number }) { LapLine(it) }
        }
    }
}

@Composable
private fun LapLine(row: LapRow) {
    val partialColor = when { row.live -> Lx.Ink; row.mark == LapMark.FASTEST -> Lx.Done; row.mark == LapMark.SLOWEST -> Lx.Mus; else -> Lx.Ink }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .then(if (row.live) Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x0DFFFFFF)) else Modifier.topLine())
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.Center) {
            Text("Vuelta ${row.number}", style = Lx.inter(13.5.sp, FontWeight.Medium, line = 16.sp), color = if (row.live) Lx.Ink else Lx.Muted)
            // La marca lleva TEXTO además de color (accesibilidad).
            when (row.mark) {
                LapMark.FASTEST -> Text("más rápida", style = Lx.inter(11.sp, FontWeight.SemiBold, line = 13.sp), color = Lx.Done)
                LapMark.SLOWEST -> Text("más lenta", style = Lx.inter(11.sp, FontWeight.SemiBold, line = 13.sp), color = Lx.Mus)
                LapMark.NONE -> Unit
            }
        }
        Text(FreeClock.formatLap(row.partialMs), Modifier.weight(1f), style = Lx.num(13.5.sp), color = partialColor, textAlign = TextAlign.End)
        Text(FreeClock.formatLap(row.totalMs), Modifier.weight(1f), style = Lx.num(13.5.sp), color = Lx.Muted, textAlign = TextAlign.End)
    }
}

/** `border-top: 1px solid var(--div)`. */
private fun Modifier.topLine(): Modifier = this.drawWithContent {
    drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
    drawContent()
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Temporizador
// ---------------------------------------------------------------------------------------------------------------------------------

private val TimerPresets = listOf(30, 60, 90, 120, 180, 300)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerPane(state: FreeClockState, nowMs: State<Long>, actions: FreeClockActions, modifier: Modifier = Modifier) {
    val t = state.timer
    when (t.phase) {
        FreeTimerPhase.CONFIG -> TimerConfig(state, actions, modifier)
        FreeTimerPhase.FINISHED -> Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.heightIn(max = 236.dp).fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    TimerRing({ 1f }, Modifier.fillMaxSize(), pulsing = true)
                    TimerCenter("0:00", "Se acabó el tiempo", strong = true)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(KpknLineIcons.Bell, null, Modifier.size(16.dp), tint = Lx.Faint)
                val what = listOfNotNull("Sonido".takeIf { t.sound }, "vibración".takeIf { t.vibrate }).joinToString(" y ")
                Text(if (what.isEmpty()) "Sin aviso al terminar" else "$what al terminar", style = Lx.inter(13.sp), color = Lx.Faint)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LxButton("Cerrar", actions.onBackToConfig, Modifier.weight(1f), ghost = true, tag = "timer-close")
                LxButton("Repetir ${FreeClock.formatClock(t.totalMs / 1000)}", actions.onRepeat, Modifier.weight(1f), icon = FreeClockIcons.Reset, tag = "timer-repeat")
            }
        }
        else -> {
            val paused = t.phase == FreeTimerPhase.PAUSED
            Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.heightIn(max = 236.dp).fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        TimerRing({ FreeClock.timerFraction(t, nowMs.value) }, Modifier.fillMaxSize())
                        val left by remember(t, nowMs) { derivedMs { FreeClock.formatClock((FreeClock.timerRemainingMs(t, nowMs.value) + 999) / 1000) } }
                        TimerCenter(left, if (paused) "En pausa" else "restante", strong = false)
                    }
                }
                val endLabel = remember(t.endAtMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t.endAtMs)) }
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        if (paused) append("En pausa · de ${FreeClock.formatClock(t.totalMs / 1000)}") else {
                            append("Termina a las ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = Lx.Ink, fontWeight = FontWeight.SemiBold))
                            append(endLabel)
                            pop()
                            append(" · de ${FreeClock.formatClock(t.totalMs / 1000)}")
                        }
                    },
                    Modifier.fillMaxWidth().testTag("timer-info"), style = Lx.inter(13.sp).copy(fontFeatureSettings = "tnum"), color = Lx.Faint, textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    listOf(15 to "+15 s", 30 to "+30 s", 60 to "+1 min").forEach { (s, label) ->
                        LxChip(label, false, { actions.onAdd(s) }, tag = "timer-add-$s")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LxButton("Cancelar", actions.onBackToConfig, Modifier.weight(1f), ghost = true, tag = "timer-cancel")
                    LxButton(if (paused) "Seguir" else "Pausar", actions.onPauseResume, Modifier.weight(1f),
                        icon = if (paused) FreeClockIcons.Play else FreeClockIcons.Pause, tag = "timer-pause")
                }
            }
        }
    }
}

/** `.lx-rc`: cifra de 58 sp y su etiqueta, en el centro del anillo. */
@Composable
private fun TimerCenter(value: String, label: String, strong: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = Lx.num(58.sp, (-2).sp).copy(lineHeight = 58.sp), color = Lx.Ink, modifier = Modifier.testTag("timer-readout"))
        Text(label, style = Lx.inter(13.sp, if (strong) FontWeight.SemiBold else FontWeight.Medium), color = if (strong) Lx.Ink else Lx.Muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerConfig(state: FreeClockState, actions: FreeClockActions, modifier: Modifier) {
    val t = state.timer
    val total = FreeClock.timerDigitsSeconds(t.digits)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().semantics { contentDescription = "Tiempo del temporizador ${FreeClock.timerDigitsDisplay(t.digits)} minutos y segundos" },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(FreeClock.timerDigitsDisplay(t.digits), style = Lx.num(64.sp, (-2).sp).copy(lineHeight = 64.sp), color = if (total == 0) Lx.Dim else Lx.Ink,
                modifier = Modifier.testTag("timer-digits"))
            Text("MIN : S", Modifier.padding(top = 6.dp), style = Lx.inter(11.sp, FontWeight.Medium, 2.sp), color = Lx.Faint)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TimerPresets.forEach { s ->
                LxChip(FreeClock.formatClock(s.toLong()), total == s, { actions.onPreset(s) }, Modifier.weight(1f), fill = true, tag = "timer-preset-$s")
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "del")).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key -> Keypad(key, { actions.onKey(key) }, Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LxSectionLabel("Al terminar", Modifier.padding(start = 2.dp, end = 6.dp))
            LxChip("Sonido", t.sound, { actions.onSound(!t.sound) }, icon = FreeClockIcons.Sound, tag = "timer-sound")
            LxChip("Vibración", t.vibrate, { actions.onVibrate(!t.vibrate) }, icon = FreeClockIcons.Vibrate, tag = "timer-vibrate")
        }
        LxButton("Iniciar" + if (total > 0) " ${FreeClock.formatClock(total.toLong())}" else "", actions.onStart, Modifier.fillMaxWidth(),
            icon = FreeClockIcons.Play, enabled = total > 0, tag = "timer-start")
    }
}

@Composable
private fun Keypad(key: String, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(if (pressed) Color(0x1AFFFFFF) else Lx.ChipFill)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (key == "del") "Borrar" else key }.testTag("timer-key-$key"),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "del") Icon(FreeClockIcons.Backspace, null, Modifier.size(22.dp), tint = Lx.Ink)
        else Text(key, style = Lx.num(21.sp), color = Lx.Ink)
    }
}
