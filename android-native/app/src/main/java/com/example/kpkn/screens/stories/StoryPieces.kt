package com.example.kpkn.screens.stories

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Piezas nuevas de las historias de Registros v4 sobre el lienzo de 1080 × 1920: la medalla con halo de rayos (`medalSvg`), el
 * anillo de calorías con los macros como arcos (`ringSvg`) y la curva de peso de 12 semanas (`bodyChart`).
 */

/** Metal de la medalla: color [c] y su sombra [c2] (`MEDAL` del lienzo), su nombre y la cifra del puesto. */
internal enum class StoryMedal(val label: String, val c: Color, val c2: Color, val digit: String) {
    ORO("Oro", Color(0xFFF7CF73), Color(0xFFB8913A), "1"),
    PLATA("Plata", Color(0xFFD9D6D0), Color(0xFF8F8B84), "2"),
    BRONCE("Bronce", Color(0xFFE39A6A), Color(0xFF9A5B37), "3"),
}

/**
 * La medalla con halo de rayos finos (retícula de 200): resplandor del metal, 24 rayos (uno de cada cuatro en el metal y más grueso),
 * el disco con su brillo, el aro interior y la cifra. [center] y [width] en px del lienzo; [progress] (0..1) dibuja los rayos.
 */
internal fun DrawScope.drawStoryMedal(medal: StoryMedal, center: Offset, width: Float, measurer: TextMeasurer, progress: Float = 1f) {
    val s = width / 200f
    // Resplandor: círculo de r 62 desenfocado 9 (un degradado radial que cae de r 53 a r 89).
    val glowR = 89f * s
    drawCircle(
        brush = Brush.radialGradient(
            0f to medal.c.copy(alpha = 0.35f),
            (53f / 89f) to medal.c.copy(alpha = 0.35f),
            (62f / 89f) to medal.c.copy(alpha = 0.18f),
            1f to medal.c.copy(alpha = 0f),
            center = center,
            radius = glowR,
        ),
        radius = glowR,
        center = center,
    )
    for (i in 0 until 24) {
        val a = i / 24.0 * PI * 2.0
        val r0 = 74.0 * s
        val r1 = (92.0 + (i % 3) * 6.0) * s
        val local = if (progress >= 1f) 1f else (progress * 24f - i * 0.5f).coerceIn(0f, 1f)
        if (local <= 0f) continue
        val accent = i % 4 == 0
        val start = Offset(center.x + (cos(a) * r0).toFloat(), center.y + (sin(a) * r0).toFloat())
        val full = Offset(center.x + (cos(a) * r1).toFloat(), center.y + (sin(a) * r1).toFloat())
        drawLine(
            color = if (accent) medal.c else StoryCanvas.Ink,
            start = start,
            end = Offset(start.x + (full.x - start.x) * local, start.y + (full.y - start.y) * local),
            strokeWidth = (if (accent) 2f else 1f) * s,
            cap = StrokeCap.Round,
            alpha = if (accent) 1f else 0.5f,
        )
    }
    drawStoryMedalDisc(medal, center, 56f * s, measurer)
}

/**
 * El disco de la medalla de radio [radius] (56 en la retícula de 200): degradado radial desde el brillo blanco (35 %, 30 %) al metal
 * y a su sombra, aro interior negro al 27 % y la cifra en Syne ExtraBold con su línea base 18 unidades bajo el centro.
 */
internal fun DrawScope.drawStoryMedalDisc(medal: StoryMedal, center: Offset, radius: Float, measurer: TextMeasurer) {
    val s = radius / 56f
    val box = 112f * s
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.6f),
            0.35f to medal.c,
            1f to medal.c2,
            center = Offset(center.x - radius + 0.35f * box, center.y - radius + 0.30f * box),
            radius = 0.8f * box,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(Color.Black, radius = 44f * s, center = center, style = Stroke(width = 2f * s), alpha = 0x44 / 255f)
    val layout = measurer.storyText(medal.digit, ShellType.medalDigit(52f * s))
    drawText(layout, Color(0xFF1B1206), Offset(center.x - layout.size.width / 2f, center.y + 18f * s - layout.firstBaseline))
}

/** Colores de los macros del lienzo (`--mus`, `--ene`, `--col`): proteína, carbohidratos y grasa. */
internal val StoryProtein = Color(0xFFF49A6E)
internal val StoryCarbs = Color(0xFFF7CF73)
internal val StoryFat = Color(0xFF8FB2FF)

/**
 * El anillo de calorías (retícula de 86): pista blanca al 6 % y cada parte de [parts] (fracción del total y color) como un arco de
 * trazo 7 con extremos redondos y 3 unidades menos de largo, sobre la fracción [fill] (0..1) del objetivo. Devuelve el centro.
 */
internal fun DrawScope.drawStoryRing(left: Float, top: Float, width: Float, fill: Float, parts: List<Pair<Float, Color>>): Offset {
    val k = width / 86f
    val center = Offset(left + 43f * k, top + 43f * k)
    val r = 36f * k
    val stroke = 7f * k
    drawCircle(Color.White, radius = r, center = center, style = Stroke(stroke), alpha = 0x10 / 255f)
    val circumference = (2 * PI * 36.0).toFloat()
    var offset = 0f
    for ((share, color) in parts) {
        val length = circumference * fill.coerceIn(0f, 1f) * share
        val visible = length - 3f
        if (visible > 0f) {
            drawArc(
                color = color,
                startAngle = -90f + offset / circumference * 360f,
                sweepAngle = visible / circumference * 360f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        offset += length
    }
    return center
}

/**
 * La curva de peso (retícula de 300 × 200): cuatro guías, el velo con degradado bajo la línea, la línea de tinta, el primer punto
 * hueco y el último en acento con su halo. [values] en orden; [low] y [high] fijan la escala vertical. [left], [top] y [width] en
 * px; el alto sale del ancho. [progress] (0..1) dibuja la línea de izquierda a derecha.
 */
internal fun DrawScope.drawStoryWeightCurve(
    values: List<Double>,
    low: Double,
    high: Double,
    left: Float,
    top: Float,
    width: Float,
    progress: Float = 1f,
) {
    if (values.size < 2) return
    val k = width / 300f
    val w = 300f
    val h = 200f
    val span = (high - low).takeIf { it > 1e-6 } ?: 1.0
    fun x(i: Int) = left + (14f + i * (w - 28f) / (values.size - 1)) * k
    fun y(v: Double) = top + (h - 20f - ((v - low) / span).toFloat() * (h - 50f)) * k
    for (step in 0..3) {
        drawRect(Color.White, Offset(left, top + (30f + step * 45f - 0.5f) * k), Size(w * k, k), alpha = 0x0E / 255f)
    }
    val line = Path()
    val area = Path()
    values.forEachIndexed { i, v ->
        if (i == 0) {
            line.moveTo(x(i), y(v))
            area.moveTo(x(i), y(v))
        } else {
            line.lineTo(x(i), y(v))
            area.lineTo(x(i), y(v))
        }
    }
    val bottom = top + h * k
    area.lineTo(x(values.lastIndex), bottom)
    area.lineTo(x(0), bottom)
    area.close()
    drawPath(
        area,
        brush = Brush.verticalGradient(
            listOf(StoryCanvas.Ink.copy(alpha = 0.16f), StoryCanvas.Ink.copy(alpha = 0f)),
            startY = values.minOf { y(it) },
            endY = bottom,
        ),
    )
    val reach = left + (14f + (w - 28f) * progress.coerceIn(0f, 1f) + 3f) * k
    clipRect(0f, 0f, reach, StoryCanvas.HEIGHT.toFloat()) {
        drawPath(line, StoryCanvas.Ink, style = Stroke(width = 2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val first = Offset(x(0), y(values.first()))
    drawCircle(StoryCanvas.Background, radius = 4f * k, center = first)
    drawCircle(StoryCanvas.Ink, radius = 4f * k, center = first, style = Stroke(1.6f * k))
    if (progress >= 1f) {
        val last = Offset(x(values.lastIndex), y(values.last()))
        drawCircle(
            brush = Brush.radialGradient(
                0f to StoryCanvas.Accent.copy(alpha = 0.55f),
                1f to StoryCanvas.Accent.copy(alpha = 0f),
                center = last,
                radius = 12f * k,
            ),
            radius = 12f * k,
            center = last,
        )
        drawCircle(StoryCanvas.Accent, radius = 6f * k, center = last)
    }
}
