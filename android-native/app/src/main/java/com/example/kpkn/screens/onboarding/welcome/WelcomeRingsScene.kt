package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.R
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.eInOut
import com.example.kpkn.screens.onboarding.design.eInQuad
import com.example.kpkn.screens.onboarding.design.eOutBack
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.mix
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.ui.theme.Syne
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Escena de RECUPERACIÓN: la pantalla Home de KPKN con los Rings en acción, como una mini historia de un día.
 *
 *   Despiertas  (0–2,9 s)   Músculo 88 · Energía 95 · Columna 92 → «Hoy puedes entrenar fuerte»
 *   Entrenas    (2,9–6,6)   aparece la pesa; Músculo −30, Energía −21 → «Mejor un día suave»
 *   Duermes     (6,6–9,7)   aparece la cama; Energía +23, Músculo +18, Columna +2
 *   Al otro día (9,7–12)    «Listo para otra buena sesión»
 *
 * Contrato (ver WelcomeScene.kt): función PURA de `t` en el lienzo de 300 × 620 dp, sin corrutinas ni `Animatable`.
 * Todo «qué vale cada cosa en t» está en las funciones puras de la primera mitad de este archivo (con prueba
 * unitaria, WelcomeRingsSceneTest); la segunda mitad solo las dibuja.
 *
 * Cierra sola: un velo del color de la pantalla cubre el contenido al final del ciclo y lo descubre al empezar el
 * siguiente (≈ 0,5 s en total) mientras la barra de estado se queda a la vista, como en las otras escenas; por eso
 * el marco no vuelve a fundirla (WelcomePageCopy.shellFadesLoop = false).
 */

/** Duración del bucle de la escena de Recuperación, en segundos. */
internal const val WelcomeRingsPeriod = 12f

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Lógica pura de la historia
// ──────────────────────────────────────────────────────────────────────────────────────────────

/** Los tres anillos de la Torre anidada. */
internal enum class RingKind { COLUMNA, MUSCULO, ENERGIA }

/** Porcentajes (0–100) de los tres anillos. */
internal data class RingsValues(val columna: Float, val musculo: Float, val energia: Float) {
    operator fun get(kind: RingKind): Float = when (kind) {
        RingKind.COLUMNA -> columna
        RingKind.MUSCULO -> musculo
        RingKind.ENERGIA -> energia
    }
}

/** Un movimiento de un anillo: empieza en [start] y dura [duration] segundos. */
internal data class RingMove(val start: Float, val duration: Float) {
    val end: Float get() = start + duration
}

internal object RingsStory {
    /** Cuándo empieza cada momento del día. */
    const val TrainAt = 2.9f
    const val SleepAt = 6.6f
    const val NextAt = 9.7f

    /** Valores al despertar, tras entrenar y tras dormir. */
    val morning = RingsValues(columna = 92f, musculo = 88f, energia = 95f)
    val afterTraining = RingsValues(columna = 89f, musculo = 58f, energia = 74f)
    val afterSleep = RingsValues(columna = 91f, musculo = 76f, energia = 97f)

    /** Los anillos se llenan al abrir la escena, bajan al entrenar (con un leve desfase) y suben al dormir. */
    val intro = mapOf(
        RingKind.COLUMNA to RingMove(0f, 0.9f),
        RingKind.MUSCULO to RingMove(0.15f, 0.9f),
        RingKind.ENERGIA to RingMove(0.3f, 0.9f),
    )
    val drain = mapOf(
        RingKind.MUSCULO to RingMove(3.2f, 1.4f),
        RingKind.ENERGIA to RingMove(3.35f, 1.3f),
        RingKind.COLUMNA to RingMove(3.5f, 1.0f),
    )
    val recover = mapOf(
        RingKind.ENERGIA to RingMove(7.0f, 2.0f),
        RingKind.MUSCULO to RingMove(7.2f, 2.4f),
        RingKind.COLUMNA to RingMove(7.4f, 1.4f),
    )
}

/** Porcentaje (con decimales) del anillo [kind] en [t]. */
internal fun ringValueAt(kind: RingKind, t: Float): Float {
    val morning = RingsStory.morning[kind]
    val trained = RingsStory.afterTraining[kind]
    val slept = RingsStory.afterSleep[kind]
    val intro = RingsStory.intro.getValue(kind)
    val drain = RingsStory.drain.getValue(kind)
    val recover = RingsStory.recover.getValue(kind)
    return when {
        t < drain.start -> lerpF(0f, morning, eOutCubic(seg(t, intro.start, intro.end)))
        t < recover.start -> lerpF(morning, trained, eInOut(seg(t, drain.start, drain.end)))
        else -> lerpF(trained, slept, eOutCubic(seg(t, recover.start, recover.end)))
    }
}

internal fun ringsValuesAt(t: Float): RingsValues = RingsValues(
    columna = ringValueAt(RingKind.COLUMNA, t),
    musculo = ringValueAt(RingKind.MUSCULO, t),
    energia = ringValueAt(RingKind.ENERGIA, t),
)

/** Lo que se lee en pantalla: el porcentaje entero. */
internal fun ringDisplayPercent(value: Float): Int = value.roundToInt().coerceIn(0, 100)

/** «−30 %» o «+23 %»: signo real (el menos de verdad) y un espacio antes del porcentaje. */
internal fun formatRingDelta(delta: Int): String = (if (delta < 0) "−" else "+") + abs(delta) + " %"

/** Etiqueta de cambio que sube y se desvanece junto a un anillo. [alpha] 0–1; [rise] 0–1 (de dónde parte a dónde llega). */
internal data class RingChip(val kind: RingKind, val text: String, val alpha: Float, val rise: Float)

private class ChipSpec(val kind: RingKind, val start: Float, val from: RingsValues, val to: RingsValues) {
    val text: String = formatRingDelta(ringDisplayPercent(to[kind]) - ringDisplayPercent(from[kind]))
}

/** Cuánto vive cada etiqueta de cambio, en segundos. */
internal const val RingChipLife = 1.8f

private val ChipSpecs = listOf(
    ChipSpec(RingKind.MUSCULO, 3.3f, RingsStory.morning, RingsStory.afterTraining),
    ChipSpec(RingKind.ENERGIA, 3.45f, RingsStory.morning, RingsStory.afterTraining),
    ChipSpec(RingKind.COLUMNA, 3.6f, RingsStory.morning, RingsStory.afterTraining),
    ChipSpec(RingKind.ENERGIA, 7.3f, RingsStory.afterTraining, RingsStory.afterSleep),
    ChipSpec(RingKind.MUSCULO, 7.5f, RingsStory.afterTraining, RingsStory.afterSleep),
    ChipSpec(RingKind.COLUMNA, 7.7f, RingsStory.afterTraining, RingsStory.afterSleep),
)

/** Etiquetas de cambio visibles en [t]. */
internal fun ringChipsAt(t: Float): List<RingChip> = ChipSpecs.mapNotNull { spec ->
    val local = t - spec.start
    if (local < 0f || local >= RingChipLife) return@mapNotNull null
    val alpha = seg(local, 0f, 0.2f) * (1f - seg(local, RingChipLife - 0.5f, RingChipLife))
    RingChip(spec.kind, spec.text, alpha, eOutCubic(seg(local, 0f, RingChipLife)))
}

internal enum class RingTrend { NONE, DOWN, UP }

/** Flecha pequeña junto al porcentaje: hacia abajo tras entrenar, hacia arriba mientras se recupera. */
internal data class RingTrendState(val trend: RingTrend, val alpha: Float)

internal fun ringTrendAt(kind: RingKind, t: Float): RingTrendState {
    val drain = RingsStory.drain.getValue(kind)
    val recover = RingsStory.recover.getValue(kind)
    return when {
        t < drain.start -> RingTrendState(RingTrend.NONE, 0f)
        t < recover.start -> RingTrendState(RingTrend.DOWN, seg(t, drain.start, drain.start + 0.3f))
        else -> RingTrendState(RingTrend.UP, seg(t, recover.start, recover.start + 0.3f))
    }
}

/** El momento del día en [t]. */
internal enum class RingsMoment { DESPIERTAS, ENTRENAS, DUERMES, AL_OTRO_DIA }

internal fun ringsMomentAt(t: Float): RingsMoment = when {
    t < RingsStory.TrainAt -> RingsMoment.DESPIERTAS
    t < RingsStory.SleepAt -> RingsMoment.ENTRENAS
    t < RingsStory.NextAt -> RingsMoment.DUERMES
    else -> RingsMoment.AL_OTRO_DIA
}

/** Línea de tiempo mínima: cuánto brilla cada icono (0–1) y cuánto se ha recorrido (0–1) con su opacidad. */
internal data class TimelineState(
    val sun: Float,
    val dumbbell: Float,
    val moon: Float,
    val progress: Float,
    val progressAlpha: Float,
)

internal fun timelineAt(t: Float): TimelineState {
    fun window(a: Float, b: Float) = seg(t, a, a + 0.3f) * (1f - seg(t, b, b + 0.3f))
    val train = RingsStory.TrainAt
    val sleep = RingsStory.SleepAt
    val next = RingsStory.NextAt
    return TimelineState(
        sun = max(window(-1f, train), window(next, 99f)),
        dumbbell = window(train, sleep),
        moon = window(sleep, next),
        progress = 0.5f * eInOut(seg(t, train, train + 0.6f)) + 0.5f * eInOut(seg(t, sleep, sleep + 0.6f)),
        progressAlpha = 1f - seg(t, next, next + 0.35f),
    )
}

internal enum class RingsBadge { DUMBBELL, BED }

/** El icono del momento (pesa al entrenar, cama al dormir): [scale] con rebote y [wave] de 0 a 1 mientras se expande el aro. */
internal data class BadgeState(val badge: RingsBadge, val scale: Float, val wave: Float)

internal fun badgeAt(t: Float): BadgeState? {
    fun state(badge: RingsBadge, from: Float, to: Float): BadgeState? {
        if (t < from || t >= to + 0.3f) return null
        val pop = eOutBack(seg(t, from, from + 0.45f)) * (1f - eInQuad(seg(t, to, to + 0.3f)))
        return BadgeState(badge, max(0f, pop), seg(t, from, from + 0.9f))
    }
    return state(RingsBadge.DUMBBELL, RingsStory.TrainAt + 0.1f, RingsStory.SleepAt - 0.1f)
        ?: state(RingsBadge.BED, RingsStory.SleepAt + 0.1f, RingsStory.NextAt - 0.1f)
}

/** Un texto que cambia entre varios: [from] → [to] con [k] de 0 a 1 durante el fundido. Fuera del fundido, from == to y k = 1. */
internal data class TextSwap(val from: Int, val to: Int, val k: Float)

internal fun textSwapAt(t: Float, switchTimes: List<Float>, fade: Float = 0.4f): TextSwap {
    val index = switchTimes.count { t >= it }
    if (index > 0) {
        val since = t - switchTimes[index - 1]
        if (since < fade) return TextSwap(index - 1, index, since / fade)
    }
    return TextSwap(index, index, 1f)
}

/** La lectura del día que da la app, con su subtítulo y el color del punto de estado. */
internal object RingsReading {
    val titles = listOf("Hoy puedes entrenar fuerte", "Mejor un día suave", "Listo para otra buena sesión")
    val titleSwitch = listOf(4.7f, RingsStory.NextAt + 0.2f)
    val subtitles = listOf(
        "Tus tres Rings están altos.",
        "Músculo y energía bajaron.",
        "Suben mientras descansas.",
        "Tus Rings volvieron a subir.",
    )
    val subtitleSwitch = listOf(4.7f, 7.3f, RingsStory.NextAt + 0.2f)

    /** La primera parada de la línea de tiempo: «Despiertas» y, cuando amanece el día siguiente, «Al otro día». */
    val wakeLabels = listOf("Despiertas", "Al otro día")
    val wakeSwitch = listOf(RingsStory.NextAt)

    /** La tarjeta de lectura entra poco después de que los anillos empiezan a llenarse. */
    fun cardAlpha(t: Float): Float = seg(t, 0.85f, 1.3f)
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Dibujo
// ──────────────────────────────────────────────────────────────────────────────────────────────

private val P = WelcomeScenePalette

private fun ringColor(kind: RingKind): Color = when (kind) {
    RingKind.COLUMNA -> P.columna
    RingKind.MUSCULO -> P.musculo
    RingKind.ENERGIA -> P.energia
}

private fun ringLabel(kind: RingKind): String = when (kind) {
    RingKind.COLUMNA -> "COLUMNA"
    RingKind.MUSCULO -> "MÚSCULO"
    RingKind.ENERGIA -> "ENERGÍA"
}

/** De izquierda a derecha: el orden en que lo dice el mensaje («tu músculo, tu energía y tu columna»). */
private val ReadoutOrder = listOf(RingKind.MUSCULO, RingKind.ENERGIA, RingKind.COLUMNA)
private val ReadoutCenterX = listOf(58f, 150f, 242f)

// Geometría de la Torre anidada (unidades del logo; ver kpkn_simbolo.xml). Energía va un poco más alta que en el
// logo para que sus trazos no se pisen con los de Músculo.
private class TowerRing(val kind: RingKind, val cy: Float, val rx: Float, val ry: Float)

private val Tower = listOf(
    TowerRing(RingKind.COLUMNA, cy = 61f, rx = 38f, ry = 12f),
    TowerRing(RingKind.MUSCULO, cy = 45f, rx = 28f, ry = 9f),
    TowerRing(RingKind.ENERGIA, cy = 24.5f, rx = 18f, ry = 6.5f),
)
private const val TowerTopUnit = 18f
private const val TowerScale = 2.95f
private const val TowerCenterX = 150f
private const val TowerTopY = 98f
private const val RingStrokeUnits = 4.4f

/** Posiciones verticales (dp del lienzo de 300 × 620): el dibujo y los textos salen de las mismas cifras. */
private object Y {
    const val Header = 50f
    const val ChipStart = 288f
    const val ChipRise = 16f
    const val Number = 302f
    const val Caption = 342f
    const val Card = 380f
    const val CardHeight = 90f
    const val Timeline = 528f
    const val TimelineLabel = 556f
}

/** Ancho de cada columna de porcentaje: tres columnas de 96 dp centradas en 58, 150 y 242. */
private const val ReadoutColumnWidth = 96f

private val SunPaths by lazy {
    listOf(
        parsePath("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"),
        parsePath("M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"),
    )
}
private val MoonPaths by lazy { listOf(parsePath("M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z")) }
private val DumbbellPaths by lazy { listOf(parsePath("M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11")) }
private val BedPaths by lazy {
    listOf(
        parsePath("M3 19V6M3 15h18v4M21 15v-2.5A3.5 3.5 0 0 0 17.5 9H11v6"),
        parsePath("M5 11.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"),
    )
}

private fun parsePath(d: String): Path = PathParser().parsePathString(d).toPath()

/** Icono de trazo en cuadrícula de 24, centrado en ([x], [y]) con lado [size] (misma técnica que el overlay de módulos). */
private fun DrawScope.drawStrokeIcon(paths: List<Path>, x: Float, y: Float, size: Float, color: Color, strokeWidth: Float, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val k = size / 24f
    withTransform({ translate(x, y); scale(k, k, Offset.Zero); translate(-12f, -12f) }) {
        paths.forEach { drawPath(it, color, alpha = clamp01(alpha), style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

private fun readinessColor(titleIndex: Int): Color = when (titleIndex) {
    1 -> P.energia
    else -> P.ok
}

/** Cuánto cubre el velo del bucle en [t]: 1 = pantalla vacía. Cierra el ciclo y abre el siguiente (≈ 0,25 s + 0,25 s). */
internal fun ringsVeilAt(t: Float): Float = 1f - loopAlpha(t, WelcomeRingsPeriod)

@Composable
internal fun WelcomeRingsScene(t: Float, modifier: Modifier = Modifier) {
    val values = ringsValuesAt(t)
    Box(modifier.fillMaxSize().background(P.screen).clipToBounds()) {
        RingsHeader(t)
        Canvas(Modifier.fillMaxSize()) { drawRingsScene(t, values) }
        ReadoutOrder.forEachIndexed { i, kind -> RingReadout(kind, ReadoutCenterX[i], values[kind], t) }
        RingsReadingText(t)
        TimelineLabels(t)
        // Velo del bucle: cubre el contenido (no la barra de estado) al cerrar un ciclo y al abrir el siguiente.
        Box(Modifier.fillMaxSize().background(P.screen.copy(alpha = ringsVeilAt(t))))
        RingsStatusChrome()
    }
}

@Composable
private fun RingsHeader(t: Float) {
    val intro = seg(t, 0f, 0.4f)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = intro }) {
        Image(
            painter = painterResource(R.drawable.kpkn_simbolo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(P.ink),
            modifier = Modifier.offset(20.dp, (Y.Header + 3f).dp).size(28.dp, 18.2.dp),
        )
        Text(
            "Tus Rings",
            style = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = P.ink),
            modifier = Modifier.offset(56.dp, (Y.Header + 1f).dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Lo que tiene cualquier teléfono y no cambia con la escena: hora, cobertura y batería, la etiqueta «EJEMPLO» (los datos
 * son de muestra) y la barra de gestos. Va por encima del velo del bucle, como en las otras escenas de la bienvenida.
 */
@Composable
private fun RingsStatusChrome() {
    Box(Modifier.fillMaxSize()) {
        Text(
            "9:41",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = P.ink),
            modifier = Modifier.offset(24.dp, 5.dp),
        )
        Canvas(Modifier.fillMaxSize()) { drawStatusIcons(size.width / WelcomeSceneSize.width.value) }
        Box(
            Modifier
                .offset(222.dp, 52.dp)
                .size(58.dp, 16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "EJEMPLO",
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.9.sp, color = P.muted),
            )
        }
    }
}

private fun DrawScope.drawStatusIcons(u: Float) {
    for (i in 0..3) {
        val h = 3f + i * 2f
        drawRoundRect(P.ink, Offset((236f + i * 4.2f) * u, (21.5f - h) * u), Size(2.8f * u, h * u), CornerRadius(1f * u))
    }
    drawRoundRect(P.ink.copy(alpha = 0.6f), Offset(260f * u, 12.5f * u), Size(20f * u, 9f * u), CornerRadius(2.6f * u), style = Stroke(1.1f * u))
    drawRoundRect(P.ink, Offset(261.8f * u, 14.3f * u), Size(12.6f * u, 5.4f * u), CornerRadius(1.2f * u))
    drawRoundRect(P.ink.copy(alpha = 0.6f), Offset(280.6f * u, 15.4f * u), Size(1.8f * u, 3.2f * u), CornerRadius(0.9f * u))
    drawRoundRect(P.ink.copy(alpha = 0.55f), Offset(102f * u, 608f * u), Size(96f * u, 4f * u), CornerRadius(2f * u))
}

/** Texto que se funde entre varios: el nuevo sube 4 dp mientras el viejo se va. */
@Composable
private fun SwapText(texts: List<String>, swap: TextSwap, style: TextStyle, align: Alignment, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = align) {
        if (swap.from == swap.to) {
            Text(texts[swap.to], style = style)
        } else {
            Text(texts[swap.from], style = style, modifier = Modifier.graphicsLayer { alpha = 1f - swap.k; translationY = -swap.k * 4.dp.toPx() })
            Text(texts[swap.to], style = style, modifier = Modifier.graphicsLayer { alpha = swap.k; translationY = (1f - swap.k) * 4.dp.toPx() })
        }
    }
}

/**
 * Porcentaje enorme (Syne ExtraBold: es una tipografía muy ancha, de ahí los 28 sp), la etiqueta de cambio que flota
 * encima y, debajo, el nombre del anillo con una flecha pequeña que dice hacia dónde fue lo último.
 */
@Composable
private fun RingReadout(kind: RingKind, centerX: Float, value: Float, t: Float) {
    val color = ringColor(kind)
    val intro = seg(t, 0.2f, 0.6f)
    val trend = ringTrendAt(kind, t)
    val chips = ringChipsAt(t).filter { it.kind == kind }
    Box(
        Modifier
            .offset((centerX - ReadoutColumnWidth / 2f).dp, 0.dp)
            .width(ReadoutColumnWidth.dp)
            .fillMaxSize()
            .graphicsLayer { alpha = intro },
    ) {
        // Etiquetas de cambio: nacen sobre el número y suben mientras se desvanecen. La subida es un desplazamiento
        // de la posición y la transparencia va en los colores: sin capas, así nada se recorta al moverse.
        chips.forEach { chip ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(0.dp, (Y.ChipStart - chip.rise * Y.ChipRise).dp)
                    .drawBehind {
                        val corner = CornerRadius(size.height / 2f)
                        drawRoundRect(color.copy(alpha = 0.16f * chip.alpha), cornerRadius = corner)
                        drawRoundRect(color.copy(alpha = 0.55f * chip.alpha), cornerRadius = corner, style = Stroke(1.dp.toPx()))
                    }
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    chip.text,
                    style = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = color.copy(alpha = chip.alpha)),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            buildAnnotatedString {
                append(ringDisplayPercent(value).toString())
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = P.muted)) { append("%") }
            },
            style = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, color = P.ink, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.TopCenter).offset(0.dp, Y.Number.dp),
        )
        Row(
            Modifier.align(Alignment.TopCenter).offset(0.dp, Y.Caption.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La flecha ocupa siempre su sitio (y su gemelo invisible a la izquierda) para que la etiqueta no se mueva.
            Spacer(Modifier.width(12.dp))
            Text(
                ringLabel(kind),
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp, color = color),
                maxLines = 1,
                softWrap = false,
            )
            Canvas(
                Modifier
                    .padding(start = 4.dp)
                    .size(8.dp, 7.dp)
                    .graphicsLayer { alpha = trend.alpha },
            ) {
                if (trend.trend == RingTrend.NONE) return@Canvas
                val down = trend.trend == RingTrend.DOWN
                val path = Path().apply {
                    if (down) { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height) }
                    else { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2f, 0f) }
                    close()
                }
                drawPath(path, if (down) P.danger else P.ok)
            }
        }
    }
}

/** El texto de la tarjeta de lectura (título y subtítulo); la tarjeta misma y el punto se dibujan en el lienzo. */
@Composable
private fun RingsReadingText(t: Float) {
    val alpha = RingsReading.cardAlpha(t)
    val titleSwap = textSwapAt(t, RingsReading.titleSwitch)
    val subSwap = textSwapAt(t, RingsReading.subtitleSwitch)
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; translationY = (1f - alpha) * 8.dp.toPx() }) {
        SwapText(
            RingsReading.titles, titleSwap,
            TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, color = P.ink, lineBreak = LineBreak.Heading),
            Alignment.BottomStart,
            Modifier.offset(56.dp, (Y.Card + 12f).dp).width(214.dp).height(44.dp),
        )
        SwapText(
            RingsReading.subtitles, subSwap,
            TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, color = P.muted),
            Alignment.TopStart,
            Modifier.offset(56.dp, (Y.Card + 60f).dp).width(214.dp).height(20.dp),
        )
    }
}

@Composable
private fun TimelineLabels(t: Float) {
    val tl = timelineAt(t)
    val lit = listOf(tl.sun, tl.dumbbell, tl.moon)
    val xs = listOf(50f, 150f, 250f)
    // La primera parada cambia de «Despiertas» a «Al otro día» cuando amanece el día siguiente.
    val wake = textSwapAt(t, RingsReading.wakeSwitch, 0.3f)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = seg(t, 1.1f, 1.6f) }) {
        lit.forEachIndexed { i, l ->
            val style = TextStyle(
                fontFamily = WizardFonts.body,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = mix(P.muted, P.ink, l),
            )
            val box = Modifier.offset((xs[i] - 40f).dp, Y.TimelineLabel.dp).width(80.dp).height(18.dp)
            if (i == 0) SwapText(RingsReading.wakeLabels, wake, style, Alignment.TopCenter, box)
            else Box(box, contentAlignment = Alignment.TopCenter) { Text(if (i == 1) "Entrenas" else "Duermes", style = style, maxLines = 1) }
        }
    }
}

private fun DrawScope.drawRingsScene(t: Float, values: RingsValues) {
    val u = size.width / WelcomeSceneSize.width.value
    drawTower(t, u, values)
    drawReadingCard(t, u)
    drawBadge(t, u)
    drawTimeline(t, u)
}

/** La Torre anidada: tres anillos de trazo grueso y redondeado con la perspectiva del logo, cada uno lleno hasta su porcentaje. */
private fun DrawScope.drawTower(t: Float, u: Float, values: RingsValues) {
    val k = TowerScale * u
    val stroke = RingStrokeUnits * k
    val cut = 0.3f * k
    val trackIn = seg(t, 0f, 0.3f)
    fun geometry(ring: TowerRing): Pair<Offset, Size> {
        val cy = (TowerTopY + (ring.cy - TowerTopUnit) * TowerScale) * u
        return Offset(TowerCenterX * u - ring.rx * k, cy - ring.ry * k) to Size(ring.rx * 2f * k, ring.ry * 2f * k)
    }
    // El brillo de la marca: dos pasadas anchas y casi transparentes que respiran muy despacio. Van todas debajo
    // de los trazos para que el resplandor de un anillo nunca tiña el trazo del vecino.
    Tower.forEachIndexed { index, ring ->
        val sweep = 360f * clamp01(values[ring.kind] / 100f)
        if (sweep <= 1f) return@forEachIndexed
        val (topLeft, oval) = geometry(ring)
        val color = ringColor(ring.kind)
        val breath = 0.88f + 0.12f * sin(t * 1.9f + index * 1.3f)
        drawArc(color.copy(alpha = 0.07f * breath), -90f, sweep, false, topLeft, oval, style = Stroke(stroke * 2.7f, cap = StrokeCap.Round))
        drawArc(color.copy(alpha = 0.15f * breath), -90f, sweep, false, topLeft, oval, style = Stroke(stroke * 1.7f, cap = StrokeCap.Round))
    }
    Tower.forEachIndexed { index, ring ->
        val color = ringColor(ring.kind)
        val (topLeft, oval) = geometry(ring)
        // El anillo de arriba pasa por delante: un filete del color de la pantalla lo separa del de abajo.
        if (index > 0) drawOval(P.screen, topLeft, oval, style = Stroke(stroke + 2f * cut))
        drawOval(color.copy(alpha = 0.17f * trackIn), topLeft, oval, style = Stroke(stroke))
        val sweep = 360f * clamp01(values[ring.kind] / 100f)
        if (sweep > 1f) drawArc(color, -90f, sweep, false, topLeft, oval, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** La tarjeta de la lectura (fondo, filete y punto de estado). */
private fun DrawScope.drawReadingCard(t: Float, u: Float) {
    val a = RingsReading.cardAlpha(t)
    if (a <= 0f) return
    val lift = (1f - a) * 8f * u
    val topLeft = Offset(20f * u, Y.Card * u + lift)
    val sz = Size(260f * u, Y.CardHeight * u)
    drawRoundRect(P.panel, topLeft, sz, CornerRadius(18f * u), alpha = a)
    drawRoundRect(P.line, topLeft, sz, CornerRadius(18f * u), alpha = a, style = Stroke(1f * u))
    val swap = textSwapAt(t, RingsReading.titleSwitch)
    val dot = mix(readinessColor(swap.from), readinessColor(swap.to), swap.k)
    val c = Offset(36f * u, (Y.Card + 34f) * u + lift)
    drawCircle(dot, 11f * u, c, alpha = 0.16f * a)
    drawCircle(dot, 5.5f * u, c, alpha = a)
}

/** El icono del momento: pesa al entrenar, cama al dormir, arriba a la izquierda de la Torre. */
private fun DrawScope.drawBadge(t: Float, u: Float) {
    val b = badgeAt(t) ?: return
    val color = if (b.badge == RingsBadge.DUMBBELL) P.musculo else P.energia
    val c = Offset(46f * u, 120f * u)
    val s = b.scale
    if (s > 0.01f) {
        drawCircle(P.panel, 23f * u * s, c)
        drawCircle(color.copy(alpha = 0.9f), 23f * u * s, c, style = Stroke(1.6f * u))
        drawStrokeIcon(if (b.badge == RingsBadge.DUMBBELL) DumbbellPaths else BedPaths, c.x, c.y, 26f * u * s, color, 1.9f)
    }
    if (b.wave > 0f && b.wave < 1f) {
        drawCircle(color, 23f * u * (1f + 0.9f * eOutCubic(b.wave)), c, alpha = (1f - b.wave) * 0.5f, style = Stroke(1.4f * u))
    }
}

/** Sol · pesa · luna: tres paradas unidas por una línea que se va llenando al avanzar el día. */
private fun DrawScope.drawTimeline(t: Float, u: Float) {
    val intro = seg(t, 1.1f, 1.6f)
    if (intro <= 0f) return
    val tl = timelineAt(t)
    val xs = listOf(50f, 150f, 250f)
    val y = Y.Timeline * u
    val r = 20f * u
    val colors = listOf(P.energia, P.musculo, P.mente)
    val lit = listOf(tl.sun, tl.dumbbell, tl.moon)
    val icons = listOf(SunPaths, DumbbellPaths, MoonPaths)
    // Líneas entre paradas.
    for (i in 0..1) {
        val x0 = (xs[i] + 26f) * u
        val x1 = (xs[i + 1] - 26f) * u
        drawLine(P.line, Offset(x0, y), Offset(x1, y), 2f * u, StrokeCap.Round, alpha = intro)
        val local = clamp01(tl.progress * 2f - i)
        if (local > 0f) {
            drawLine(P.ink, Offset(x0, y), Offset(x0 + (x1 - x0) * local, y), 2f * u, StrokeCap.Round, alpha = 0.55f * tl.progressAlpha * intro)
        }
    }
    xs.forEachIndexed { i, x ->
        val c = Offset(x * u, y)
        val l = lit[i]
        drawCircle(P.panel, r, c, alpha = intro)
        if (l > 0.01f) drawCircle(colors[i], r, c, alpha = 0.14f * l * intro)
        drawCircle(mix(P.line, colors[i], l), r, c, alpha = intro, style = Stroke(1.5f * u))
        drawStrokeIcon(icons[i], c.x, c.y, 22f * u, mix(P.muted, colors[i], l), 1.8f, intro)
    }
}
