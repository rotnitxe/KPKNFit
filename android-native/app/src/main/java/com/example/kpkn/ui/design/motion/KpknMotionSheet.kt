package com.example.kpkn.ui.design.motion

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------------------------------------------------------------------
// Hojas de calibración de movimiento (`cm-mov-*` del lienzo): mismas posiciones, tamaños, textos y tiempos que `src/scenes-cm.js`
// y `src/cm.css`. La hoja se pinta en un instante (el que da [timeMs]) o, sin él, con el reloj de cuadros.
// ------------------------------------------------------------------------------------------------------------------------------

/** Ids de las hojas de movimiento, como las escenas del lienzo. */
internal val KPKN_MOTION_SHEETS: List<String> = listOf(
    "cm-mov-curvas-1", "cm-mov-curvas-2", "cm-mov-duraciones-1", "cm-mov-duraciones-2",
    "cm-mov-entradas", "cm-mov-pulsaciones", "cm-mov-bucles", "cm-mov-trazos-1", "cm-mov-trazos-2", "cm-mov-cuenta",
)

private val SheetBg = Color(0xFF0B0B0C)
private val Cream = Color(0xFFF2EEE6)
private val Muted = Color(0xFF8E8980)
private val Soft = Color(0xFFC4BFB6)
private val CellBg = Color(0xFF111111)
private val ChipBg = Color(0xFF262628)
private val Hairline = Color(0x14FFFFFF)

private const val HOLD_MS = 600
private const val TRAVEL_DP = 200f
private const val ROWS_PER_SHEET = 12
private const val ROW_PITCH = 64
private const val ROW_Y0 = 64
private const val CELL_W = 152
private const val CELL_H = 120
private const val CELL_PITCH = 128
private const val CELLS_PER_SHEET = 12
private const val REPLAY_MS = 3200f

private val Inter = WizardFonts.body
private val Syne = WizardFonts.display

private fun inter(size: Float, weight: FontWeight, lineHeightEm: Float, color: Color) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeightEm).sp,
    color = color,
    lineHeightStyle = CssLineHeight,
)

/** `line-height` de CSS: el hueco sobrante (o faltante) se reparte por igual arriba y abajo y no se recorta nada. */
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * Hoja de calibración de movimiento: [sheet] es el id de la escena del lienzo (`cm-mov-curvas-1`, `cm-mov-trazos-2`, `cm-mov-cuenta`…).
 * Con [timeMs] la hoja se pinta en ese instante (milisegundos desde el principio, como `shots.cjs --t=<ms>`); sin él, corre con el reloj
 * de cuadros y las entradas se repiten cada 3,2 s, igual que en el lienzo. 360 × 880 dp.
 *
 * ```
 * KpknMotionSheet("cm-mov-curvas-1")                      // en marcha
 * KpknMotionSheet("cm-mov-pulsaciones", timeMs = { 60f })     // congelada a los 60 ms
 * ```
 */
@Composable
internal fun KpknMotionSheet(sheet: String, timeMs: (() -> Float)? = null) {
    val clock = rememberKpknClockMs(running = timeMs == null)
    val now: () -> Float = timeMs ?: { clock.value }
    Box(Modifier.size(360.dp, 880.dp).clipToBounds().background(SheetBg)) {
        val rows = remember(sheet) { rideRows(sheet) }
        when {
            rows != null -> {
                val (title, subtitle) = rideHeader(sheet)
                Header(title, subtitle)
                Rides(rows, now)
            }
            sheet == "cm-mov-cuenta" -> Counts(now)
            else -> FamilySheet(sheet, now)
        }
    }
}

private fun allCurveRows(): List<RideRow> = KpknMotion.curves.flatMap { curve ->
    curve.typicalMs.ifEmpty { listOf(280) }.map { RideRow(curve.name, shorten(curve.css, 36), it, curve.easing) }
}

private fun allDurationRows(): List<RideRow> = KpknMotion.durations.map {
    RideRow("estandar", if (it.exact) "duración exacta" else "escalón de la escala", it.duration.ms, KpknMotion.Estandar)
}

/** Las filas de una hoja de curvas o de duraciones (12 por hoja), o `null` si [sheet] no es de ese tipo. */
private fun rideRows(sheet: String): List<RideRow>? = when {
    sheet.startsWith("cm-mov-curvas-") -> allCurveRows().drop((sheet.removePrefix("cm-mov-curvas-").toInt() - 1) * ROWS_PER_SHEET).take(ROWS_PER_SHEET)
    sheet.startsWith("cm-mov-duraciones-") -> allDurationRows().drop((sheet.removePrefix("cm-mov-duraciones-").toInt() - 1) * ROWS_PER_SHEET).take(ROWS_PER_SHEET)
    else -> null
}

private fun rideHeader(sheet: String): Pair<String, String> {
    val curves = sheet.startsWith("cm-mov-curvas-")
    val index = sheet.substringAfterLast('-').toInt()
    val total = ((if (curves) allCurveRows().size else allDurationRows().size) + ROWS_PER_SHEET - 1) / ROWS_PER_SHEET
    return if (curves) {
        "Curvas de movimiento" to "$index/$total · círculo de 40 dp que recorre 200 dp"
    } else {
        "Escala de duraciones" to "$index/$total · curva «estandar», círculo de 40 dp, 200 dp"
    }
}

/**
 * Instantes (ms) en los que se calibra una hoja: 0, 25, 50, 75 y 100 % de la duración de cada elemento (en las rodadas, de la ida, más
 * la mitad de la vuelta; en las familias, desde su retardo, y en los bucles, de su periodo; en la cuenta, de 1100 ms). Las pruebas
 * de fotogramas y las capturas del lienzo (`shots.cjs --t=<ms>`) usan esta misma lista.
 */
internal fun kpknMotionSheetInstants(sheet: String): List<Int> {
    fun quarters(start: Float, duration: Float) = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { (start + it * duration).roundToInt() }
    val out = java.util.TreeSet<Int>()
    out += 0
    val rows = rideRows(sheet)
    when {
        rows != null -> rows.forEach { row ->
            out += quarters(0f, row.ms.toFloat())
            out += row.ms + HOLD_MS + row.ms / 2
        }
        sheet == "cm-mov-cuenta" -> out += quarters(0f, KPKN_COUNT_UP_MS.toFloat())
        else -> sheetFamilies(sheet).forEach { family ->
            val demo = family.demo
            out += quarters(demo.delay.ms.toFloat(), demo.duration.ms.toFloat())
            out += demo.delay.ms
        }
    }
    return out.filter { rows != null || sheet == "cm-mov-cuenta" || it < REPLAY_MS.toInt() }
}

private fun shorten(text: String, n: Int): String = if (text.length > n) text.substring(0, n - 1) + "…" else text

@Composable
private fun Header(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().height(56.dp).background(SheetBg).padding(start = 16.dp, end = 16.dp, top = 14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            title.uppercase(),
            style = TextStyle(
                fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 12.1.sp,
                letterSpacing = 1.sp, color = Cream, lineHeightStyle = CssLineHeight,
            ),
        )
        BasicText(subtitle, style = inter(10f, FontWeight.Medium, 1.2f, Muted))
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Curvas y duraciones: círculo de 40 dp que recorre 200 dp
// ------------------------------------------------------------------------------------------------------------------------------

private class RideRow(val name: String, val sub: String, val ms: Int, val easing: KpknEasing)

/**
 * El `@keyframes cm-ride-N` del lienzo: ida con la curva, espera 600 ms, vuelta con la misma curva y otra espera. Los desplazamientos
 * de los tramos de espera son lineales (`animation-timing-function: linear`) y la animación entera es lineal; los porcentajes del
 * lienzo están redondeados a milésimas de por ciento y aquí también.
 */
private fun rideAnimation(row: RideRow): KpknAnimation {
    val cycle = 2 * row.ms + 2 * HOLD_MS
    fun p(x: Int): Float = (x.toDouble() / cycle * 100000).roundToInt() / 100000f
    val keyframes = kpknKeyframes("cm-ride", "@keyframes cm-ride") {
        at(0f, row.easing) { translateX(0f) }
        at(p(row.ms), KpknMotion.Lineal) { translateX(TRAVEL_DP) }
        at(p(row.ms + HOLD_MS), row.easing) { translateX(TRAVEL_DP) }
        at(p(2 * row.ms + HOLD_MS)) { translateX(0f) }
        at(1f) { translateX(0f) }
    }
    return KpknAnimation(keyframes, KpknDuration(cycle), KpknMotion.Lineal, iterations = KpknAnimation.INFINITE)
}

@Composable
private fun Rides(rows: List<RideRow>, now: () -> Float) {
    rows.forEachIndexed { i, row ->
        val animation = remember(row) { rideAnimation(row) }
        Box(Modifier.offset(0.dp, (ROW_Y0 + i * ROW_PITCH).dp).size(360.dp, 64.dp)) {
            val label = buildAnnotatedString {
                withStyle(SpanStyle(color = Cream, fontWeight = FontWeight.SemiBold)) { append(row.name) }
                append(" · ${row.sub} · ")
                withStyle(SpanStyle(color = Cream, fontWeight = FontWeight.SemiBold)) { append("${row.ms} ms") }
            }
            BasicText(label, Modifier.offset(16.dp, 0.dp), style = inter(10f, FontWeight.Medium, 1.2f, Muted), maxLines = 1, softWrap = false)
            Box(Modifier.offset(60.dp, 18.dp).size(240.dp, 40.dp)) {
                Box(Modifier.offset(0.dp, 19.dp).size(240.dp, 2.dp).background(Hairline, RoundedCornerShape(1.dp)))
                Canvas(Modifier.offset(200.dp, 0.dp).size(40.dp)) {
                    drawCircle(Color(0x33FFFFFF), radius = size.minDimension / 2 - 0.5f * density, style = Stroke(width = 1f * density))
                }
                Box(
                    Modifier.size(40.dp).kpknPose(animation, now).background(Cream, CircleShape),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Familias de @keyframes: una celda por familia
// ------------------------------------------------------------------------------------------------------------------------------

private fun sheetFamilies(sheet: String): List<KpknFamily> {
    val sections = when (sheet.substringBeforeLast('-').takeIf { sheet.endsWith("-1") || sheet.endsWith("-2") } ?: sheet) {
        "cm-mov-trazos" -> listOf("trazos")
        "cm-mov-entradas" -> listOf("entradas", "hojas")
        "cm-mov-pulsaciones" -> listOf("pulsaciones")
        "cm-mov-bucles" -> listOf("bucles")
        else -> emptyList()
    }
    val all = KpknFamilies.all.filter { it.sheet in sections }
    val page = if (sheet.endsWith("-2")) 1 else 0
    return all.drop(page * CELLS_PER_SHEET).take(CELLS_PER_SHEET)
}

private fun sheetTitle(sheet: String): String = when {
    sheet == "cm-mov-entradas" -> "Entradas, fundidos y hojas"
    sheet == "cm-mov-pulsaciones" -> "Pulsaciones y rebotes"
    sheet == "cm-mov-bucles" -> "Pulsos y bucles infinitos"
    sheet.startsWith("cm-mov-trazos") -> "Trazos, barras y destellos"
    else -> sheet
}

@Composable
private fun FamilySheet(sheet: String, now: () -> Float) {
    val families = sheetFamilies(sheet)
    val pages = if (sheet.startsWith("cm-mov-trazos")) 2 else 1
    val page = if (sheet.endsWith("-2")) 2 else 1
    Header(
        sheetTitle(sheet),
        (if (pages > 1) "$page/$pages · " else "") + "@keyframes reales del lienzo, uno por familia",
    )
    families.forEachIndexed { i, family ->
        val x = if (i % 2 == 0) 16 else 192
        val y = ROW_Y0 + (i / 2) * CELL_PITCH
        val once = !family.demo.isInfinite
        val cellTime: () -> Float = if (once) ({ now() % REPLAY_MS }) else now
        Box(
            Modifier.offset(x.dp, y.dp).size(CELL_W.dp, CELL_H.dp).clip(RoundedCornerShape(18.dp)).background(CellBg),
        ) {
            Column(
                Modifier.offset(12.dp, 10.dp).width((CELL_W - 12 - 10).dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            ) {
                BasicText(family.name, style = inter(11f, FontWeight.SemiBold, 1.15f, Cream))
                BasicText(
                    "${family.keyframes.name} · ${family.demoDurationText} · ${family.demoCurve.name}" + if (family.demo.isInfinite) " · ∞" else "",
                    style = inter(9f, FontWeight.Medium, 1.25f, Muted),
                )
            }
            Box(
                Modifier.offset(0.dp, (CELL_H - 6 - 64).dp).size(CELL_W.dp, 64.dp).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Demo(family, cellTime)
            }
        }
    }
}

/** Texto y color de las celdas de la hoja: lo que `cmDemoEl` pinta para cada familia. */
@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, color: Color = Cream, background: Color = ChipBg, fill: Boolean = false) {
    Box(
        modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).height(28.dp).background(background, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = if (fill) Alignment.CenterStart else Alignment.Center,
    ) {
        BasicText(text, style = inter(12f, FontWeight.SemiBold, 28f / 12f, color), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Dot(modifier: Modifier = Modifier) {
    Box(modifier.size(26.dp).background(Cream, CircleShape))
}

/** El elemento animado de la celda, con la animación de demostración de la familia. */
@Composable
private fun Demo(family: KpknFamily, time: () -> Float) {
    val animation = family.demo
    val pose = remember { KpknPose() }
    // Las propiedades que no son de capa (ancho, color, trazo, sombra, filtro) se leen en composición: la hoja se recompone por cuadro.
    animation.poseAt(time(), pose)
    // Los puntos son una sola forma: se atenúan sin capa de grupo (en Robolectric una capa con opacidad recorta al tamaño sin escalar).
    val single = family.name in setOf("latido", "pulso", "flotar", "asentar", "rebote", "rebote-aparece", "aparece-escala", "onda-anillo", "pulso-anillo", "ignicion")
    val layer = Modifier.kpknPose(
        animation, time,
        compositingStrategy = if (single) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto,
    )
    when (family.name) {
        "dibujo-trazo" -> StrokeDemo(pose.get(KpknChannel.DashOffset))
        "giro" -> Spinner(layer)
        "brillo-barrido" -> Skeleton(pose.get(KpknChannel.BackgroundShiftPct))
        "crece-x" -> Track(110.dp, 10.dp, 5.dp) {
            Box(Modifier.fillMaxSize().kpknPose(animation, time, transformOrigin = TransformOrigin(0f, 0.5f)).background(Cream, RoundedCornerShape(5.dp)))
        }
        "crece-y" -> Track(26.dp, 54.dp, 5.dp) {
            Box(Modifier.fillMaxSize().kpknPose(animation, time, transformOrigin = TransformOrigin(0.5f, 1f)).background(Cream, RoundedCornerShape(5.dp)))
        }
        "barrido-ancho" -> {
            val fraction = pose[KpknChannel.WidthFraction]
            val opacity = pose.alpha
            Track(110.dp, 10.dp, 5.dp) {
                Box(
                    Modifier.fillMaxHeight().width((110f * fraction).dp)
                        .graphicsLayer { alpha = opacity }.background(Cream, RoundedCornerShape(5.dp)),
                )
            }
        }
        "barrido-linea" -> Window {
            Box(Modifier.fillMaxWidth().height(3.dp).then(layer).background(Cream, RoundedCornerShape(2.dp)))
        }
        "hoja-sube", "hoja-baja" -> Window {
            Box(Modifier.align(Alignment.BottomStart)) { Sheet(layer) }
        }
        "revelado" -> Video(pose[KpknChannel.Brightness], pose[KpknChannel.BlurDp], pose.scaleX)
        "entrada-implicita" -> Chip("Chip", layer)
        "caida" -> Window {
            Box(Modifier.offset(51.dp, 6.dp).size(18.dp).then(layer).background(Color(0xFFF49A6E), RoundedCornerShape(4.dp)))
        }
        "onda-anillo", "pulso-anillo", "ignicion" -> ShadowDot(family.name, pose[KpknChannel.ShadowAlpha], pose[KpknChannel.ShadowSpreadDp], pose[KpknChannel.ShadowBlurDp], layer)
        "destello" -> {
            val color = if (pose.active) {
                kpknCssColorLerp(Color(0xFFF49A6E), Soft, pose[KpknChannel.ColorMix])
            } else {
                Cream
            }
            Chip("Destello", layer, color = color)
        }
        "despliegue-fila" -> Window {
            RowReveal(pose.active, pose[KpknChannel.MaxHeightDp], pose[KpknChannel.ColorMix], pose.alpha)
        }
        "onda" -> Box(Modifier.requiredSize(90.dp).then(layer), contentAlignment = Alignment.Center) {
            // La capa mide 90 dp para que la escala 3,4 quepa: con una capa de 26 dp, Robolectric recorta la onda al tamaño sin escalar.
            Dot()
        }
        "aviso" -> Chip("+2,5 kg", layer)
        "latido", "pulso", "flotar", "asentar", "rebote", "rebote-aparece", "aparece-escala" -> Dot(layer)
        else -> Chip("Muestra", layer)
    }
}

/** `.cm-box`: 120 × 52, radio 12, fondo de la hoja, recorta lo que sale. */
@Composable
private fun Window(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(Modifier.size(120.dp, 52.dp).clip(RoundedCornerShape(12.dp)).background(SheetBg), content = content)
}

/** `.cm-bar` / `.cm-bary`: pista de #ffffff14 que recorta. */
@Composable
private fun Track(width: Dp, height: Dp, radius: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(radius)).background(Hairline)) { content() }
}

/** `.cm-spin`: 30 × 30, anillo de 3 dp a #ffffff22 y el cuarto superior del borde en crema. */
@Composable
private fun Spinner(layer: Modifier) {
    Canvas(Modifier.size(30.dp).then(layer)) {
        val w = 3f * density
        val r = size.minDimension / 2 - w / 2
        drawCircle(Color(0x22FFFFFF), radius = r, style = Stroke(width = w))
        drawArc(
            color = Cream, startAngle = -135f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(w / 2, w / 2), size = Size(2 * r, 2 * r), style = Stroke(width = w),
        )
    }
}

/** `.cm-sk`: 110 × 26, radio 10, degradado de 100° al 220 % de ancho que se desplaza (`background-position` de 120 % a −120 %). */
@Composable
private fun Skeleton(positionPct: Float) {
    Canvas(Modifier.size(110.dp, 26.dp).clip(RoundedCornerShape(10.dp))) {
        drawRect(Color(0xFF0F0F0F))
        val imageW = size.width * 2.2f
        val h = size.height
        val offsetX = (size.width - imageW) * positionPct / 100f
        val angle = Math.toRadians(100.0)
        val dx = sin(angle).toFloat()
        val dy = (-cos(angle)).toFloat()
        val length = kotlin.math.abs(imageW * dx) + kotlin.math.abs(h * dy)
        val center = Offset(imageW / 2, h / 2)
        val start = Offset(center.x - dx * length / 2, center.y - dy * length / 2)
        val end = Offset(center.x + dx * length / 2, center.y + dy * length / 2)
        val brush = Brush.linearGradient(
            0.3f to Color(0xFF0F0F0F), 0.5f to Color(0xFF2A2A2A), 0.7f to Color(0xFF0F0F0F), start = start, end = end,
        )
        translate(left = offsetX) { drawRect(brush, size = Size(imageW, h)) }
    }
}

/** El trazo del lienzo (`M6 30 C 26 -6, 52 46, 94 8`, 3,5 de grosor, `pathLength` 100): se dibuja de 0 a (1 − dashoffset) de su largo. */
@Composable
private fun StrokeDemo(dashOffset: Float) {
    Canvas(Modifier.size(100.dp, 40.dp)) {
        val path = Path().apply {
            moveTo(6f * density, 30f * density)
            cubicTo(26f * density, -6f * density, 52f * density, 46f * density, 94f * density, 8f * density)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val visible = measure.length * (1f - dashOffset).coerceIn(0f, 1f)
        if (visible > 0f) {
            val segment = Path()
            measure.getSegment(0f, visible, segment, true)
            drawPath(segment, Cream, style = Stroke(width = 3.5f * density, cap = StrokeCap.Round))
        }
    }
}

/** `.cm-sheet` dentro de la caja: 34 dp de alto, esquinas superiores de 14 dp, fondo #1B1B1D y filo interior de 1 dp. */
@Composable
private fun Sheet(layer: Modifier) {
    Canvas(Modifier.size(120.dp, 34.dp).then(layer)) {
        val r = 14f * density
        val shape = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, size.width, size.height,
                    topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                ),
            )
        }
        drawPath(shape, Color(0xFF1B1B1D))
        clipPath(shape) {
            drawRect(Color(0x1CFFFFFF))
            translate(top = 1f * density) { drawPath(shape, Color(0xFF1B1B1D)) }
        }
    }
}

/** `.cm-vid`: 110 × 52, radio 12, degradado de 135°; el filtro (`brightness` y `blur`) y la escala vienen de la pose. */
@Composable
private fun Video(brightness: Float, blurDp: Float, scale: Float) {
    fun shade(c: Color) = Color((c.red * brightness).coerceIn(0f, 1f), (c.green * brightness).coerceIn(0f, 1f), (c.blue * brightness).coerceIn(0f, 1f))
    Box(
        Modifier.size(110.dp, 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (blurDp > 0f) Modifier.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
            .drawBehind {
                val dir = Offset(0.70710678f, 0.70710678f)
                val length = (size.width + size.height) * 0.70710678f
                val c = Offset(size.width / 2, size.height / 2)
                val brush = Brush.linearGradient(
                    listOf(shade(Color(0xFF8FB2FF)), shade(Color(0xFFF49A6E))),
                    start = Offset(c.x - dir.x * length / 2, c.y - dir.y * length / 2),
                    end = Offset(c.x + dir.x * length / 2, c.y + dir.y * length / 2),
                )
                drawRoundRect(brush, cornerRadius = CornerRadius(12f * density, 12f * density))
            },
    )
}

/** Punto de 26 dp con la `box-shadow` de la familia (anillo de crema, o resplandor y anillo en el verde de la zona). */
@Composable
private fun ShadowDot(family: String, alpha: Float, spread: Float, blur: Float, layer: Modifier) {
    val color = if (family == "ignicion") Color(0xFF87C9A5) else Cream
    Box(
        Modifier.size(26.dp).then(layer)
            .drawBehind { if (alpha > 0f) drawCircleShadow(color, alpha, spread, blur) }
            .background(Cream, CircleShape),
    )
}

/**
 * Dibujo mínimo de `box-shadow: 0 0 <blur> <spread> <color>` de un círculo (para la hoja de calibración): anillo de [spreadDp] y, si hay
 * [blurDp], desenfoque gaussiano con σ = blur / 2 como en CSS. La profundidad de las pantallas la construye `ui/design/depth`.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircleShadow(color: Color, alpha: Float, spreadDp: Float, blurDp: Float) {
    val radius = size.minDimension / 2 + spreadDp * density
    if (blurDp <= 0f) {
        drawCircle(color, radius = radius, alpha = alpha)
    } else {
        val sigma = blurDp * density / 2f
        val skiaRadius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.argb(
                    (color.alpha * alpha * 255f).roundToInt(), (color.red * 255f).roundToInt(),
                    (color.green * 255f).roundToInt(), (color.blue * 255f).roundToInt(),
                )
                maskFilter = BlurMaskFilter(skiaRadius, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawCircle(size.width / 2, size.height / 2, radius, paint)
        }
    }
}

/** `ed-fresh`: fila de 120 × 28 que se despliega (altura máxima, fondo de claro a #111 y opacidad), con el texto siempre a su tamaño. */
@Composable
private fun RowReveal(active: Boolean, maxHeight: Float, colorMix: Float, alpha: Float) {
    val background = if (active) kpknCssColorLerp(Color(0x1FF2EEE6), Color(0xFF111111), colorMix) else Color(0xFF111111)
    Box(
        Modifier.requiredSize(120.dp, 28.dp).graphicsLayer { this.alpha = alpha }.drawBehind {
            val h = minOf(size.height, maxHeight * density)
            if (h > 0f) {
                val r = minOf(14f * density, h / 2f)
                drawRoundRect(background, size = Size(size.width, h), cornerRadius = CornerRadius(r, r))
            }
        }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText("Serie 3", style = inter(12f, FontWeight.SemiBold, 28f / 12f, Cream), maxLines = 1, softWrap = false)
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Cuenta de cifras
// ------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun Counts(now: () -> Float) {
    Header("Cuenta de cifras", "salida cúbica 1 − (1 − t)³ en 1100 ms")
    val rows = listOf(
        Triple("Series", 1247.0, 0) to ("" to 70),
        Triple("Calorías", 2150.0, 0) to (" kcal" to 190),
        Triple("Peso", 82.5, 1) to (" kg" to 310),
        Triple("Volumen", 18420.0, 0) to (" kg" to 430),
        Triple("Proteína", 164.8, 1) to (" g" to 550),
    )
    for ((data, tail) in rows) {
        val (name, value, decimals) = data
        val (suffix, top) = tail
        Column(
            Modifier.offset(24.dp, top.dp).width(312.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                name.uppercase(),
                style = inter(11f, FontWeight.Medium, 1f, Muted).copy(letterSpacing = 0.6.sp),
            )
            KpknCountUp(
                target = value,
                decimals = decimals,
                suffix = suffix,
                style = TextStyle(
                    fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 42.sp,
                    letterSpacing = (-0.5).sp, color = Cream, fontFeatureSettings = "'lnum' 1, 'tnum' 1", lineHeightStyle = CssLineHeight,
                ),
                timeMs = now,
            )
        }
    }
    val hint: AnnotatedString = buildAnnotatedString {
        append("Mismo contador que usan Inicio y los resúmenes del lienzo: ")
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Soft)) { append("[data-count]") }
        append(", 1100 ms, sin retardo, cifras con tabulares y coma decimal. Arranca al abrir la hoja.")
    }
    BasicText(
        hint,
        Modifier.offset(24.dp, 690.dp).width(312.dp),
        style = inter(11f, FontWeight.Medium, 1.4f, Muted),
    )
}
