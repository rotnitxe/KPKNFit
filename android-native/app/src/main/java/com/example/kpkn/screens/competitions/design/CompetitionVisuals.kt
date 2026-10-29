package com.example.kpkn.screens.competitions.design

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.competitions.PowerliftingFederation
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionBout
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.domain.competitions.CompetitionChartPoint
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionHubRow
import com.example.kpkn.domain.competitions.CompetitionMedalHonor
import com.example.kpkn.domain.competitions.liftLabel
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─── Tarjeta «Próxima» (`.next-card`) ─────────────────────────────────────────────────────────────────────────

/** La tarjeta de la próxima: portada de líneas al 90 % fundida a #0c0c0c al 78 % y el contenido desde [topPadding]. */
@Composable
internal fun CompNextCard(
    style: LineCoverStyle,
    background: LineCoverBackground,
    seed: Int,
    modifier: Modifier = Modifier,
    topPadding: Dp = 150.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val reduced = wizardReducedMotion()
    Box(
        modifier
            .padding(top = 14.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(CompColors.card)
            .border(1.dp, Color(0x16FFFFFF), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Box(Modifier.matchParentSize().alpha(0.9f)) {
            LineCoverArt(spec = LineCoverSpec(style, background, seed), modifier = Modifier.fillMaxSize(), animate = true, reduced = reduced)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.2f to Color(0x000C0C0C), 0.78f to CompColors.card)))
        }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = topPadding, bottom = 18.dp), content = content)
    }
}

/** La cuenta atrás (`.cd`): cifra Syne 64 que cuenta al abrir y su unidad. */
@Composable
internal fun CompCountdown(value: Long, unit: String, modifier: Modifier = Modifier, countUp: Boolean = true) {
    val reduced = wizardReducedMotion()
    val animate = countUp && !reduced
    val anim = remember { Animatable(if (animate) 0f else value.toFloat()) }
    LaunchedEffect(value, animate) {
        if (animate) anim.animateTo(value.toFloat(), tween(1100, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) else anim.snapTo(value.toFloat())
    }
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "$value $unit" }, verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Math.round(anim.value).toString(), style = CompType.syne(64, line = 0.9f, spacing = -3f), color = CompColors.ink)
        Text(unit, Modifier.padding(bottom = 6.dp), style = CompType.inter(15f, FontWeight.SemiBold, 1.2f), color = CompColors.muted)
    }
}

/** Los tres primeros intentos (`.openers`). */
@Composable
internal fun CompOpeners(openers: List<Pair<String, String>>) {
    if (openers.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        openers.forEach { (label, kg) ->
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.weight(1f).clip(shape).background(Color(0x09FFFFFF)).border(1.dp, Color(0x10FFFFFF), shape).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(label, style = CompType.inter(13f, FontWeight.SemiBold, 1.15f, 1f), color = CompColors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(kg, Modifier.padding(top = 4.dp), style = CompType.syne(18, FontWeight.Bold, 1.1f), color = CompColors.ink)
                Text("1.er intento", style = CompType.inter(13f, line = 1.2f), color = CompColors.faint)
            }
        }
    }
}

// ─── Historial (`.meet`) ───────────────────────────────────────────────────────────────────────────────────────

/** Una competencia del historial: medalla (o aro con el icono de su disciplina), nombre con «RÉCORD»/«MP», fecha y la cifra. */
@Composable
internal fun CompMeetRow(row: CompetitionHubRow, onClick: () -> Unit, divider: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.drawBehind { drawLine(CompColors.div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompMedal(row.medal, 46.dp, icon = CompetitionIcons.byKey(row.icon), iconSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, Modifier.weight(1f, fill = false), style = CompType.syne(16, FontWeight.Bold, 1.2f, -0.2f), color = CompColors.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                row.badge?.let { CompRecordChip(it) }
            }
            Text(row.subtitle, Modifier.padding(top = 3.dp), style = CompType.cap, color = CompColors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val h = row.headline
        if (h != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(h.value, style = CompType.syne(19, line = 1.1f), color = CompColors.ink, maxLines = 1)
                h.caption?.let { Text(it, style = CompType.inter(13f, line = 1.2f), color = CompColors.faint, maxLines = 1) }
            }
        } else if (!row.completed) {
            Text("Sin registrar", style = CompType.cap, color = CompColors.faint)
        }
    }
}

/** `.prc`: «RÉCORD» / «MP». */
@Composable
internal fun CompRecordChip(text: String) {
    Box(
        Modifier.padding(start = 6.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22F49A6E)).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CompType.inter(13f, FontWeight.Bold, 1f, 0.6f).copy(fontSize = 13.sp), color = CompColors.mus, maxLines = 1)
    }
}

/** La línea del total en competición (`totalsChart`): se dibuja al aparecer; el último punto en naranja y cada cifra encima. */
@Composable
internal fun CompTotalsChart(points: List<CompetitionChartPoint>) {
    if (points.size < 2) return
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(points, reduced) { if (!reduced) progress.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) else progress.snapTo(1f) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, color = CompColors.faint)
    val values = points.map { it.totalKg }
    val description = "Total en competición: " + values.joinToString(", ") { CompetitionFormat.kg(it) + " kg" }
    Canvas(Modifier.fillMaxWidth().height(98.dp).padding(top = 6.dp).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val pad = 20.dp.toPx()
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val lo = min - span * 0.2
        val hi = max + span * 0.11
        fun x(i: Int) = pad + i * (w - 2 * pad) / (values.size - 1)
        fun y(v: Double) = (h - 12.dp.toPx() - ((v - lo) / (hi - lo)) * (h - 26.dp.toPx())).toFloat()
        val path = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(partial, CompColors.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            val c = Offset(x(i), y(v))
            drawCircle(if (last) CompColors.mus else Color(0xFF0A0A0A), radius = (if (last) 5f else 3.5f).dp.toPx(), center = c)
            drawCircle(if (last) CompColors.mus else CompColors.ink, radius = (if (last) 5f else 3.5f).dp.toPx(), center = c, style = Stroke(1.6.dp.toPx()))
            val label = measurer.measure(CompetitionFormat.kg(v), labelStyle)
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - 10.dp.toPx() - label.size.height))
        }
    }
}

// ─── Tabla de intentos (`.att`) ───────────────────────────────────────────────────────────────────────────────

/**
 * La tabla de intentos: cada fila con su rótulo de [labelWidth] y tres celdas. [currentId] marca el intento de ahora (c-today) y
 * [pendingDashed] dibuja los pendientes con borde discontinuo (c-plan, c-today).
 */
@Composable
internal fun CompAttemptGrid(
    rows: List<CompetitionTechnicalBlock>,
    labelWidth: Dp = 72.dp,
    currentId: String? = null,
    pendingDashed: Boolean = false,
    onTap: ((CompetitionTechnicalBlock, Int) -> Unit)? = null,
    onLongPress: ((CompetitionTechnicalBlock, Int) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        rows.forEach { block ->
            val label = block.movementType.liftLabel(block.title)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, Modifier.width(labelWidth), style = CompType.inter(13f, FontWeight.SemiBold, 1.2f), color = CompColors.muted, maxLines = 2)
                block.attempts.sortedBy { it.attemptNumber }.forEach { attempt ->
                    val state = when (attempt.resultType) {
                        CompetitionAttemptResult.GOOD_LIFT -> "válido"
                        CompetitionAttemptResult.NO_LIFT -> "nulo"
                        CompetitionAttemptResult.SKIPPED -> "saltado"
                        CompetitionAttemptResult.PENDING -> "pendiente"
                    }
                    AttemptButton(
                        weight = attempt.weightKg,
                        result = attempt.resultType,
                        current = attempt.id == currentId,
                        pendingDashed = pendingDashed,
                        description = "$label, intento ${attempt.attemptNumber}: ${CompetitionFormat.kg(attempt.weightKg)} kg, $state",
                        onClick = onTap?.let { tap -> { tap(block, attempt.attemptNumber) } },
                        onLongClick = onLongPress?.let { long -> { long(block, attempt.attemptNumber) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ─── Contra el reloj, combates y partidos ─────────────────────────────────────────────────────────────────────

/** Barras de parciales por kilómetro (`.splits`): la más rápida (la última en el lienzo) en naranja. */
@Composable
internal fun CompSplits(splits: List<Double>) {
    if (splits.isEmpty()) return
    val fastest = splits.indexOf(splits.min())
    val slowest = splits.max()
    val quickest = splits.min()
    Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        splits.forEachIndexed { i, v ->
            val range = (slowest - quickest).takeIf { it > 0 } ?: 1.0
            val fraction = (0.25 + 0.75 * (slowest - v) / range).toFloat().coerceIn(0.1f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight(fraction).clip(RoundedCornerShape(3.dp))
                .background(if (i == fastest) CompColors.mus else CompColors.ink.copy(alpha = 0.8f)))
        }
    }
}

/** Una fila de combate o partido (`.bout`): disco G/P, ronda y cómo fue; con [onClick], la flecha. */
@Composable
internal fun CompBoutRow(bout: CompetitionBout, divider: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.drawBehind { drawLine(CompColors.div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (bout.won) Color(0x2243D18C) else Color(0x1AFF9B92)),
            contentAlignment = Alignment.Center,
        ) { Text(if (bout.won) "G" else "P", style = CompType.syne(13, line = 1f), color = if (bout.won) CompColors.done else CompColors.danger) }
        Column(Modifier.weight(1f)) {
            Text(bout.round, style = CompType.inter(14f, FontWeight.SemiBold, 1.3f), color = CompColors.ink)
            if (bout.method.isNotBlank()) Text(bout.method, style = CompType.cap, color = CompColors.faint)
        }
        if (onClick != null) Icon(CompetitionIcons.Fwd, null, Modifier.size(16.dp), tint = CompColors.faint)
    }
}

// ─── Controles de los pasos ───────────────────────────────────────────────────────────────────────────────────

/** `.seg`: dos o más opciones en una cápsula; la elegida en #272727. */
@Composable
internal fun CompSeg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Color(0xFF0D0D0D)).border(1.dp, Color(0x10FFFFFF), shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF272727) else Color.Transparent)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = CompType.inter(13f, FontWeight.SemiBold, 1.2f), color = if (on) CompColors.ink else CompColors.faint) }
        }
    }
}

/** Un botón de disciplina de c-new (`.disc button`): círculo de 52 con el icono (de tinta si está elegido) y el nombre. */
@Composable
internal fun CompDisciplineButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics { this.selected = selected }.clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(if (selected) CompColors.ink else Color(0x0AFFFFFF))
                .border(1.dp, if (selected) CompColors.ink else Color(0x14FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = if (selected) Color(0xFF111111) else CompColors.ink) }
        Text(label, style = CompType.inter(13f, if (selected) FontWeight.SemiBold else FontWeight.Normal, 1.15f), color = if (selected) CompColors.ink else CompColors.muted,
            textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Una medalla de c-place (`.medals button`): el disco gira al elegirlo. */
@Composable
internal fun CompMedalChoice(medal: CompetitionMedalHonor, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val spin = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) { spin.snapTo(360f); spin.animateTo(0f, tween(900, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) Color(0x14F7CF73) else Color(0x08FFFFFF))
            .border(1.dp, if (selected) Color(0x66F7CF73) else Color(0x12FFFFFF), shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val density = LocalDensity.current.density
        CompMedal(medal, 64.dp, modifier = Modifier.graphicsLayer { rotationY = spin.value; cameraDistance = 12f * density })
        Text(medal.label, style = CompType.inter(13f, FontWeight.SemiBold, 1.2f), color = CompColors.ink)
    }
}

/** Una cara de «Cómo te sentiste» (`.feel button`): círculo de 54; elegida de tinta. */
@Composable
internal fun CompFeelButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (selected) CompColors.ink else Color(0x08FFFFFF))
            .border(1.dp, if (selected) CompColors.ink else Color(0x12FFFFFF), CircleShape)
            .semantics { contentDescription = label; this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(26.dp), tint = if (selected) Color(0xFF111111) else CompColors.ink) }
}

// ─── Celebración (`.cele`) ────────────────────────────────────────────────────────────────────────────────────

/** El fondo de la celebración: el halo dorado, el estallido de líneas finas que se dibuja desde el centro y el confeti que cae. */
@Composable
internal fun BoxScope.CompCelebrationBackdrop(centerFraction: Float = 0.30f) {
    val reduced = wizardReducedMotion()
    val draw = remember { Animatable(if (reduced) 1f else 0f) }
    val fall = remember { Animatable(if (reduced) 0f else 0f) }
    LaunchedEffect(reduced) {
        if (!reduced) {
            kotlinx.coroutines.coroutineScope {
                launch { draw.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))) }
                launch { fall.animateTo(1f, tween(3700, easing = LinearEasing)) }
            }
        }
    }
    val confetti = remember { confettiPieces(30) }
    val burst = remember { burstLines(28) }
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.radialGradient(listOf(Color(0x22F7CF73), Color.Black), center = Offset(size.width / 2, size.height * 0.34f),
            radius = size.width * 0.9f))
        val scale = 1.dp.toPx()
        val c = Offset(size.width / 2, size.height * centerFraction)
        burst.forEach { line ->
            val p = ((draw.value * 1100f - line.delayMs) / (1100f - line.delayMs)).coerceIn(0f, 1f)
            if (p <= 0f) return@forEach
            if (line.ring) {
                drawArc(CompColors.ink.copy(alpha = line.alpha), startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                    topLeft = Offset(c.x - line.r0 * scale, c.y - line.r0 * scale),
                    size = androidx.compose.ui.geometry.Size(line.r0 * 2 * scale, line.r0 * 2 * scale), style = Stroke(1.dp.toPx()))
            } else {
                val start = Offset(c.x + cos(line.angle) * line.r0 * scale, c.y + sin(line.angle) * line.r0 * scale)
                val endR = line.r0 + (line.r1 - line.r0) * p
                val end = Offset(c.x + cos(line.angle) * endR * scale, c.y + sin(line.angle) * endR * scale)
                drawLine(line.color.copy(alpha = line.alpha), start, end, line.width.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        if (!reduced) confetti.forEach { piece ->
            val t = ((fall.value * 3700f - piece.delayMs) / 2800f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val eased = 1 - (1 - t) * (1 - t)
            val y = -20.dp.toPx() + eased * 900.dp.toPx()
            val x = piece.left * size.width
            rotate(degrees = 540f * eased, pivot = Offset(x, y)) {
                drawRoundRect(piece.color.copy(alpha = 0.9f * (1 - t)), topLeft = Offset(x - piece.w.dp.toPx() / 2, y),
                    size = androidx.compose.ui.geometry.Size(piece.w.dp.toPx(), piece.h.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(piece.radius.dp.toPx()))
            }
        }
    }
}

private class BurstLine(val angle: Float, val r0: Float, val r1: Float, val color: Color, val width: Float, val alpha: Float, val delayMs: Float, val ring: Boolean)
private class ConfettiPiece(val left: Float, val color: Color, val delayMs: Float, val w: Float, val h: Float, val radius: Float)

/** El generador del lienzo (`rng(seed)`, mulberry32) para que el estallido y el confeti caigan igual que en el dibujo. */
private fun canvasRng(seed: Int): () -> Float {
    var a = seed
    return {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

private fun burstLines(n: Int): List<BurstLine> {
    val r = canvasRng(11)
    val acc = Color(0xFFF7CF73)
    val lines = (0 until n).map { i ->
        val a = (i.toFloat() / n * 2 * PI).toFloat()
        val r0 = 70 + r() * 20
        val r1 = 150 + r() * 95
        val accent = i % 5 == 0
        BurstLine(a, r0, r1, if (accent) acc else CompColors.ink, if (accent) 2.2f else 1.1f, if (accent) 1f else 0.55f, ((i % 7) * 40).toFloat(), false)
    }
    val rings = (0 until 3).map { k -> BurstLine(0f, 58f + k * 26, 0f, CompColors.ink, 1f, 0.18f - k * 0.04f, (200 + k * 120).toFloat(), true) }
    return lines + rings
}

private fun confettiPieces(n: Int): List<ConfettiPiece> {
    val r = canvasRng(5)
    val colors = listOf(Color(0xFFF7CF73), Color(0xFFF49A6E), Color(0xFFF2EEE6), Color(0xFF8FB2FF), Color(0xFF43D18C))
    return (0 until n).map { i ->
        val left = r()
        val delay = Math.round(r() * 900).toFloat()
        val w = 4 + Math.round(r() * 6).toFloat()
        val h = 8 + Math.round(r() * 10).toFloat()
        ConfettiPiece(left, colors[i % 5], delay, w, h, if (i % 3 != 0) 2f else 6f)
    }
}

// ─── Federaciones (C-D7) ─────────────────────────────────────────────────────────────────────────────────────

/** El logotipo de una federación (o sus siglas sobre su color) en un círculo. */
@Composable
internal fun FederationMark(federation: PowerliftingFederation?, modifier: Modifier = Modifier, size: Dp = 38.dp, customLabel: String? = null) {
    val context = LocalContext.current
    val color = remember(federation?.colorHex) { parseFederationColor(federation?.colorHex) }
    val bitmap = remember(federation?.logoFile) { loadFederationLogo(context, federation?.logoFile) }
    val letters = federation?.shortName ?: customLabel?.take(4)?.uppercase() ?: "?"
    Box(modifier.size(size).clip(CircleShape).border(1.dp, Color(0x1FFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), federation?.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.matchParentSize().background(color), contentAlignment = Alignment.Center) {
                Text(letters, style = CompType.inter(13f, FontWeight.Bold, 1f).copy(fontSize = if (letters.length <= 3) 13.sp else 13.sp), color = Color.White, maxLines = 1)
            }
        }
    }
}

internal fun parseFederationColor(hex: String?): Color {
    val clean = hex.orEmpty().removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return Color(0xFF6B6B6B)
    return Color(0xFF000000 or value)
}

private fun loadFederationLogo(context: Context, fileName: String?) =
    fileName?.let { name ->
        runCatching { context.assets.open("${PowerliftingFederationCatalog.LOGO_ASSET_DIR}/$name").use(BitmapFactory::decodeStream) }.getOrNull()
    }

/** Estado recordado de un texto que se edita en una hoja. */
@Composable
internal fun rememberText(initial: String): androidx.compose.runtime.MutableState<String> = remember(initial) { mutableStateOf(initial) }

@Composable
internal fun CompSheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 2.dp, bottom = 4.dp), style = CompType.sheetTitle, color = CompColors.ink)
}

@Composable
internal fun CompSheetSub(text: String) {
    Text(text, Modifier.padding(bottom = 12.dp), style = CompType.inter(13f, line = 1.45f), color = CompColors.muted)
}

/** Fila mínima táctil para listas dentro de hojas. */
@Composable
internal fun CompSheetOption(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, leading: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .drawBehind { drawLine(CompColors.div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = CompType.rowTitle, color = CompColors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = CompType.cap, color = CompColors.faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (selected) Icon(CompetitionIcons.Check, null, Modifier.size(18.dp), tint = CompColors.ink)
    }
}

