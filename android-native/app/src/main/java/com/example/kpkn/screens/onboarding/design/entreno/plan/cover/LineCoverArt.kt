package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Portadas de líneas: port a Compose de `coverSvg` (docs/design/program-detail-v1/src/covers.js). Cada estilo dibuja sobre un lienzo
 * virtual de 300 × 375 recortado como el SVG «slice»: la escala es la mayor de ancho/300 y alto/375, y el lienzo queda centrado. Las
 * semillas, el orden de dibujo y las cuentas de números aleatorios son los del prototipo. El grano (feTurbulence) no se pinta.
 */

/** Lienzo virtual del prototipo, en unidades de diseño. */
private const val LINE_W = 300.0
private const val LINE_H = 375.0

/** Park–Miller del prototipo: módulo 2³¹ − 1 y multiplicador 16807. */
private const val LINE_RNG_MODULUS = 2147483647L
private const val LINE_RNG_MULTIPLIER = 16807L

/** Duración de la aparición de las líneas (1,8 s). */
private const val LINE_DRAW_MILLIS = 1800

/** Giro de la órbita (ÓRBITAS): −24° alrededor de (150, 190). */
private const val LINE_ORBIT_DEGREES = -24f
private const val LINE_ORBIT_X = 150.0
private const val LINE_ORBIT_Y = 190.0

private const val LINE_TWO_PI = 2.0 * PI

/** Carga semanal por defecto de PERFIL (la del prototipo), si el programa no trae una. */
private val LINE_PERFIL_LOADS: List<Double> = listOf(0.62, 0.7, 0.8, 0.66, 0.74, 0.38)

/** Filas por defecto de SESION (la del prototipo), si no llegan las de la sesión. */
private val LINE_SESION_ROWS: List<List<Double>> = listOf(
    listOf(0.7, 0.75, 0.8, 0.85),
    listOf(0.6, 0.62, 0.65),
    listOf(0.5, 0.5, 0.52),
    listOf(0.35, 0.36, 0.38),
    listOf(0.3, 0.3, 0.32),
)

/**
 * Portada de líneas. Con [animate] y sin [reduced], las líneas se dibujan una vez en 1,8 s al aparecer; si no, quietas y completas.
 * Las curvas se preparan una vez por tamaño y [spec]; al pintar solo se recorren.
 */
@Composable
internal fun LineCoverArt(
    spec: LineCoverSpec,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    reduced: Boolean = false,
    sessionRows: List<List<Float>> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val drawing = animate && !reduced
    val progress = remember { Animatable(if (drawing) 0f else 1f) }
    LaunchedEffect(spec, drawing) {
        if (drawing) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = LINE_DRAW_MILLIS))
        } else {
            progress.snapTo(1f)
        }
    }
    Box(
        modifier
            .clipToBounds()
            .drawWithCache {
                // Sin tamaño no hay nada que pintar.
                if (size.width <= 0f || size.height <= 0f) return@drawWithCache onDrawBehind {}
                val painter = LineCoverPainter(spec, measurer, this, size.width, size.height, sessionRows)
                onDrawBehind { painter.draw(this, progress.value) }
            },
    )
}

/** Pinta [spec] a sangre en este DrawScope. [progress] 0..1 dibuja los trazos escalonados. */
internal fun DrawScope.drawLineCover(
    spec: LineCoverSpec,
    measurer: TextMeasurer,
    progress: Float = 1f,
    sessionRows: List<List<Float>> = emptyList(),
) {
    if (size.width <= 0f || size.height <= 0f) return
    LineCoverPainter(spec, measurer, this, size.width, size.height, sessionRows).draw(this, progress)
}

/** Generador del prototipo: `s = s·16807 mod (2³¹ − 1)` y cada valor es `s / (2³¹ − 1)`. Una semilla no positiva da 7. */
private class LineCoverRandom(seed: Long) {
    private var state: Long = (seed % LINE_RNG_MODULUS).let { if (it <= 0L) 7L else it }

    fun next(): Double {
        state = state * LINE_RNG_MULTIPLIER % LINE_RNG_MODULUS
        return state.toDouble() / LINE_RNG_MODULUS
    }
}

/**
 * La portada de líneas de [spec] para un tamaño fijo: el fondo, los halos y las marcas en el orden del prototipo. Las marcas se
 * preparan al crearse; [draw] solo las recorre. Las marcas animadas se cuentan en orden de dibujo (rellenos aparte).
 */
private class LineCoverPainter(
    spec: LineCoverSpec,
    measurer: TextMeasurer,
    density: Density,
    width: Float,
    height: Float,
    sessionRows: List<List<Float>>,
) {
    private val palette = spec.background
    private val dark = palette.dark
    private val topColor = Color(palette.bg)
    private val bottomColor = Color(palette.bg2)
    private val inkColor = Color(palette.line)
    private val accentColor = Color(palette.accent)

    /** Píxeles por unidad de diseño (la escala «slice») y el desplazamiento del lienzo centrado en la caja. */
    private val scale: Float = max(width / LINE_W.toFloat(), height / LINE_H.toFloat())
    private val originX: Float = (width - LINE_W.toFloat() * scale) / 2f
    private val originY: Float = (height - LINE_H.toFloat() * scale) / 2f

    /** El degradado del fondo: de [topColor] arriba a la izquierda a [bottomColor] abajo (x al 30 %). */
    private val background: Brush = Brush.linearGradient(
        colors = listOf(topColor, bottomColor),
        start = at(0.0, 0.0),
        end = at(0.3 * LINE_W, LINE_H),
    )

    private val orbitPivot: Offset = at(LINE_ORBIT_X, LINE_ORBIT_Y)
    private val glowWide = Stroke(width = 9f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val glowNarrow = Stroke(width = 5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** Semilla del prototipo: `semilla·97 + nombre.length·13`. El seno de PERFIL, ONDAS, ÓRBITAS y CAMPO la usa tal cual. */
    private val seedValue: Long = spec.seed.toLong() * 97L + spec.style.name.lowercase().length * 13L
    private val seed: Double = seedValue.toDouble()

    private val glows = ArrayList<Glow>()
    private val marks = ArrayList<Mark>()
    private var animatedCount = 0

    init {
        val rng = LineCoverRandom(seedValue)
        when (spec.style) {
            LineCoverStyle.RELIEVE -> relieve(rng)
            LineCoverStyle.PERFIL -> perfil(spec, rng)
            LineCoverStyle.ONDAS -> ondas()
            LineCoverStyle.ORBITAS -> orbitas(rng)
            LineCoverStyle.CAMPO -> campo(rng)
            LineCoverStyle.ESPIRAL -> espiral(spec)
            LineCoverStyle.ECO -> eco(spec, measurer, density)
            LineCoverStyle.BRUMA -> bruma()
            LineCoverStyle.SESION -> sesion(sessionRows)
        }
        animatedCount = marks.count { it !is FillMark }
    }

    /** Pinta fondo, halos y marcas; [progress] (0..1) reparte el dibujo de los trazos. */
    fun draw(scope: DrawScope, progress: Float) {
        scope.drawRect(brush = background)
        for (glow in glows) scope.drawGlow(glow)
        var index = 0
        for (mark in marks) {
            if (mark is FillMark) {
                scope.drawPath(mark.path, mark.color)
                continue
            }
            val local = reveal(progress, index, animatedCount)
            index++
            if (local <= 0f) continue
            when (mark) {
                is StrokeMark -> scope.drawStrokeMark(mark, local)
                is DotMark -> scope.drawDotMark(mark, local)
                is TextMark -> scope.drawTextMark(mark, local)
                is FillMark -> Unit
            }
        }
    }

    // ------------------------------------------------------------------ estilos (el orden y los números son los del prototipo)

    /** RELIEVE: curvas de nivel alrededor de una cumbre; la séptima, en acento. */
    private fun relieve(rng: LineCoverRandom) {
        val cx = 150.0 + (rng.next() - 0.5) * 70.0
        val cy = 190.0 + (rng.next() - 0.5) * 80.0
        halo(cx, cy, 70.0, 0.28)
        for (k in 1..16) {
            val radius = k * 13.0
            val points = ArrayList<Offset>(73)
            for (a in 0..72) {
                val t = a / 72.0 * LINE_TWO_PI
                val rr = radius * (1.0 + 0.2 * sin(3 * t + k * 0.55) + 0.09 * sin(5 * t + k * 1.3) + 0.04 * sin(9 * t))
                points += at(cx + rr * cos(t), cy + rr * sin(t) * 1.18)
            }
            addLine(polyline(points, closed = true), accent = k == 7)
        }
    }

    /** PERFIL: crestas apiladas; la carga de cada semana forma la cordillera. La fila de la semana en curso, en acento. */
    private fun perfil(spec: LineCoverSpec, rng: LineCoverRandom) {
        val loads = spec.weekLoads.map { it.toDouble() }.ifEmpty { LINE_PERFIL_LOADS }
        val n = loads.size
        fun profile(x: Double): Double {
            if (n == 1) return loads[0]
            val f = x / LINE_W * (n - 1)
            val i = min(n - 2, floor(f).toInt())
            val t = f - i
            val s = t * t * (3 - 2 * t)
            return loads[i] * (1 - s) + loads[i + 1] * s
        }
        val accentRow = if (spec.currentWeek >= 0) (spec.currentWeek * 3 + 2).coerceIn(0, 23) else 11
        halo(150.0, 150.0, 80.0, 0.22)
        for (k in 0 until 24) {
            val y0 = 88.0 + k * 11.0
            val points = ArrayList<Offset>(61)
            for (i in 0..60) {
                val x = i * 5.0
                val u = (x - 150.0) / 95.0
                val env = exp(-u * u)
                val h = env * profile(x) * (70.0 + k * 1.2) * (0.55 + 0.45 * sin(k * 0.9 + x * 0.03 + seed)) + env * 6.0 * rng.next()
                points += at(x, y0 - abs(h))
            }
            addFill(points, alpha = 0.96f)
            addLine(polyline(points), accent = k == accentRow)
        }
    }

    /** ONDAS: líneas que se levantan en el centro como un pulso; la decimoquinta, en acento. */
    private fun ondas() {
        halo(150.0, 210.0, 60.0, 0.2)
        for (k in 0 until 26) {
            val y = 50.0 + k * 12.0
            val points = ArrayList<Offset>(76)
            for (i in 0..75) {
                val x = i * 4.0
                val u = (x - 150.0 - (k - 13) * 3.0) / 62.0
                val env = exp(-u * u)
                points += at(x, y - env * (8.0 + k * 0.9) * abs(sin(x / 8.0 + k * 0.6 + seed * 0.01)) * 2.2)
            }
            addFill(points, alpha = 0.9f)
            addLine(polyline(points), accent = k == 15)
        }
    }

    /** ÓRBITAS: elipses finas e inclinadas alrededor de la torre; la novena, en acento con un planeta. Luego, 30 estrellas. */
    private fun orbitas(rng: LineCoverRandom) {
        halo(150.0, 190.0, 55.0, 0.3)
        for (k in 0 until 14) {
            val oval = ovalPath(150.0, 190.0, 22.0 + k * 12.0, 11.0 + k * 7.0)
            if (k == 8) {
                addStroke(oval, accentColor, 2f * scale, glow = true, orbit = true)
            } else {
                addStroke(oval, inkColor.copy(alpha = (0.85 - k * 0.035).toFloat()), 1.05f * scale, glow = false, orbit = true)
            }
        }
        val planet = at(150.0 + (22.0 + 8 * 12.0) * cos(2.3), 190.0 + (11.0 + 8 * 7.0) * sin(2.3))
        addDot(planet, 4f * scale, accentColor, glow = true, orbit = true)
        for (i in 0 until 30) {
            val x = rng.next() * LINE_W
            val y = rng.next() * LINE_H
            val radius = rng.next() * 0.9 + 0.3
            val opacity = rng.next() * 0.4 + 0.15
            addDot(at(x, y), (radius * scale).toFloat(), inkColor.copy(alpha = opacity.toFloat()), glow = false, orbit = false)
        }
    }

    /** CAMPO: 70 trazos que siguen una corriente; el vigésimo cuarto, en acento. */
    private fun campo(rng: LineCoverRandom) {
        val hx = 90.0 + rng.next() * 120.0
        val hy = 120.0 + rng.next() * 140.0
        halo(hx, hy, 70.0, 0.18)
        fun angle(x: Double, y: Double): Double = sin(x * 0.011 + seed * 0.001) * 1.6 + cos(y * 0.013) * 1.2
        for (i in 0 until 70) {
            var x = rng.next() * LINE_W
            var y = rng.next() * LINE_H
            val points = ArrayList<Offset>(35)
            points += at(x, y)
            for (step in 0 until 34) {
                val a = angle(x, y)
                x += cos(a) * 5.0
                y += sin(a) * 5.0
                points += at(x, y)
            }
            addLine(polyline(points), accent = i == 23, width = 1.0, opacity = 0.55)
        }
    }

    /** ESPIRAL: una vuelta por semana, en medias vueltas; la primera media vuelta de la semana en curso, en acento. */
    private fun espiral(spec: LineCoverSpec) {
        val weeks = max(spec.weeks, 1)
        halo(150.0, 190.0, 60.0, 0.25)
        for (w in 0 until weeks * 2) {
            val end = ((w + 1) / 2.0) * PI * 2.0 + 0.01
            val points = ArrayList<Offset>()
            var t = (w / 2.0) * PI * 2.0
            while (t <= end) {
                val rr = 14.0 + t * 3.4
                points += at(150.0 + rr * cos(t), 190.0 + rr * sin(t) * 1.12)
                t += 0.06
            }
            val current = spec.currentWeek >= 0 && w / 2 == spec.currentWeek && w % 2 == 0
            addLine(polyline(points), accent = current, width = 1.2, opacity = 0.8)
        }
    }

    /** ECO: la cifra de semanas repetida en contornos, como un eco; el contorno de delante, en acento. */
    private fun eco(spec: LineCoverSpec, measurer: TextMeasurer, density: Density) {
        // 270 unidades de diseño en px, pasadas a sp con la densidad y la escala de fuente. El interletraje −8 unidades = −8/270 em.
        val fontSize = (270.0 * scale / (density.density * density.fontScale)).toFloat().sp
        val layout = measurer.measure(
            text = spec.weeks.toString(),
            style = TextStyle(
                fontFamily = WizardFonts.body,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                letterSpacing = (-8f / 270f).em,
            ),
            maxLines = 1,
            softWrap = false,
        )
        val left = px(LINE_W / 2.0) - layout.size.width / 2f
        for (k in 9 downTo 0) {
            val front = k == 0
            val top = py(292.0 - k * 9.0) - layout.firstBaseline
            addText(
                TextMark(
                    layout = layout,
                    topLeft = Offset(left, top),
                    color = if (front) accentColor else inkColor.copy(alpha = (0.7 - k * 0.065).toFloat()),
                    style = Stroke(width = (if (front) 2f else 1.1f) * scale),
                    glow = front,
                ),
            )
        }
    }

    /** BRUMA: el único estilo con color, difuminado; veinte líneas finas por encima. */
    private fun bruma() {
        val alpha = (if (dark) 0.55 else 0.45).toFloat()
        val blobs = listOf(
            Triple(70.0, 100.0, 110.0) to accentColor,
            Triple(240.0, 210.0, 100.0) to Color(0xFF8FB2FF),
            Triple(110.0, 330.0, 105.0) to Color(0xFFC9A8FF),
        )
        for ((spot, color) in blobs) {
            glows += Glow(at(spot.first, spot.second), (spot.third * 1.6 * scale).toFloat(), color.copy(alpha = alpha))
        }
        for (k in 0 until 20) {
            val y = 40.0 + k * 16.0
            val points = ArrayList<Offset>(51)
            for (i in 0..50) {
                val x = i * 6.0
                points += at(x, y + sin(x * 0.02 + k * 0.4) * 10.0 + sin(x * 0.05 + k) * 3.0)
            }
            addLine(polyline(points), accent = false, width = 1.0, opacity = 0.42)
        }
    }

    /** SESION: una línea por ejercicio, un pico por serie (alto = kg × reps); la fila de la primera, en acento. */
    private fun sesion(sessionRows: List<List<Float>>) {
        val rows = sessionRows.takeIf { it.isNotEmpty() }?.map { row -> row.map { it.toDouble() } } ?: LINE_SESION_ROWS
        halo(150.0, 140.0, 70.0, 0.22)
        rows.forEachIndexed { k, sets ->
            val y0 = 88.0 + k * 30.0
            val n = sets.size
            val points = ArrayList<Offset>(101)
            for (i in 0..100) {
                val x = i * 3.0
                var h = 0.0
                sets.forEachIndexed { j, v ->
                    val cx = 60.0 + j * (180.0 / max(1, n - 1))
                    val u = (x - cx) / (9.0 + k)
                    h += v * 70.0 * exp(-u * u)
                }
                points += at(x, y0 - h)
            }
            addFill(points, alpha = 0.94f)
            addLine(polyline(points), accent = k == 0)
        }
    }

    // ------------------------------------------------------------------ construcción de marcas

    /** El halo del prototipo: el acento difuminado; en crema, a 60 % de intensidad. */
    private fun halo(cx: Double, cy: Double, radius: Double, opacity: Double) {
        val alpha = if (dark) opacity else opacity * 0.6
        glows += Glow(at(cx, cy), (radius * 2.2 * scale).toFloat(), accentColor.copy(alpha = alpha.toFloat()))
    }

    /** El relleno del prototipo: la curva cerrada hasta abajo, con [alpha] de [bottomColor] (tapa lo que queda detrás). */
    private fun addFill(points: List<Offset>, alpha: Float) {
        marks += FillMark(polyline(points + listOf(at(LINE_W, LINE_H), at(0.0, LINE_H)), closed = true), bottomColor.copy(alpha = alpha))
    }

    /** Una línea del prototipo, `line(d, acc, w, op)`: el acento lleva brillo y es opaco; si no, la tinta con [opacity]. */
    private fun addLine(path: Path, accent: Boolean, width: Double = 1.15, opacity: Double = 0.78) {
        if (accent) {
            addStroke(path, accentColor, 2.1f * scale, glow = true)
        } else {
            addStroke(path, inkColor.copy(alpha = opacity.toFloat()), width.toFloat() * scale, glow = false)
        }
    }

    private fun addStroke(path: Path, color: Color, width: Float, glow: Boolean, orbit: Boolean = false) {
        marks += StrokeMark(path, color, Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round), glow, orbit)
    }

    private fun addDot(center: Offset, radius: Float, color: Color, glow: Boolean, orbit: Boolean) {
        marks += DotMark(center, radius, color, glow, orbit)
    }

    private fun addText(mark: TextMark) {
        marks += mark
    }

    // ------------------------------------------------------------------ geometría

    /** Px de la abscisa [x] del lienzo virtual. */
    private fun px(x: Double): Float = originX + (x * scale).toFloat()

    /** Px de la ordenada [y] del lienzo virtual. */
    private fun py(y: Double): Float = originY + (y * scale).toFloat()

    private fun at(x: Double, y: Double): Offset = Offset(px(x), py(y))

    /** Una polilínea por [points] (o un contorno cerrado si [closed]). */
    private fun polyline(points: List<Offset>, closed: Boolean = false): Path = Path().apply {
        points.forEachIndexed { i, p ->
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        if (closed) close()
    }

    private fun ovalPath(cx: Double, cy: Double, rx: Double, ry: Double): Path = Path().apply {
        addOval(Rect(px(cx - rx), py(cy - ry), px(cx + rx), py(cy + ry)))
    }

    /** Cuánto de la marca [index] (de [total]) está dibujado: la marca i empieza en progress i/(n+6) y tarda 6/(n+6). */
    private fun reveal(progress: Float, index: Int, total: Int): Float =
        ((progress * (total + 6) - index) / 6f).coerceIn(0f, 1f)

    // ------------------------------------------------------------------ pintado

    private fun DrawScope.drawGlow(glow: Glow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.color, glow.color.copy(alpha = 0f)),
                center = glow.center,
                radius = glow.radius,
            ),
            radius = glow.radius,
            center = glow.center,
        )
    }

    /** Dibuja [block] girado −24° alrededor de la órbita si [orbit]; si no, tal cual. */
    private fun DrawScope.drawOrbit(orbit: Boolean, block: DrawScope.() -> Unit) {
        if (orbit) withTransform({ rotate(LINE_ORBIT_DEGREES, orbitPivot) }, block) else block()
    }

    /** Un trazo: a trozos según [local] (su longitud dibujada); el acento lleva dos capas de brillo bajo la línea. */
    private fun DrawScope.drawStrokeMark(mark: StrokeMark, local: Float) = drawOrbit(mark.orbit) {
        val path = if (local >= 1f) {
            mark.path
        } else {
            Path().also { mark.measure.getSegment(0f, mark.measure.length * local, it, true) }
        }
        if (mark.glow) {
            drawPath(path, accentColor.copy(alpha = 0.10f), style = glowWide)
            drawPath(path, accentColor.copy(alpha = 0.18f), style = glowNarrow)
        }
        drawPath(path, mark.color, style = mark.style)
    }

    /** Un punto que se funde con [local]; el acento lleva brillo. */
    private fun DrawScope.drawDotMark(mark: DotMark, local: Float) = drawOrbit(mark.orbit) {
        if (mark.glow) {
            drawCircle(color = accentColor.copy(alpha = 0.10f * local), radius = mark.radius + 4.5f * scale, center = mark.center)
            drawCircle(color = accentColor.copy(alpha = 0.18f * local), radius = mark.radius + 2.5f * scale, center = mark.center)
        }
        drawCircle(color = mark.color.copy(alpha = mark.color.alpha * local), radius = mark.radius, center = mark.center)
    }

    /** Un contorno de texto que se funde con [local]; el de delante lleva brillo. */
    private fun DrawScope.drawTextMark(mark: TextMark, local: Float) {
        if (mark.glow) {
            drawText(mark.layout, color = accentColor.copy(alpha = 0.10f * local), topLeft = mark.topLeft, drawStyle = glowWide)
            drawText(mark.layout, color = accentColor.copy(alpha = 0.18f * local), topLeft = mark.topLeft, drawStyle = glowNarrow)
        }
        drawText(mark.layout, color = mark.color.copy(alpha = mark.color.alpha * local), topLeft = mark.topLeft, drawStyle = mark.style)
    }

    // ------------------------------------------------------------------ tipos

    /** Un halo: un círculo que va del [color] en el centro a transparente en [radius]. */
    private class Glow(val center: Offset, val radius: Float, val color: Color)

    /** Una marca del dibujo, en orden de dibujo. */
    private sealed class Mark

    /** Relleno sin animar: el área bajo una curva. */
    private class FillMark(val path: Path, val color: Color) : Mark()

    /** Trazo animado: se pinta a trozos con [measure]; [glow] si lleva brillo y [orbit] si va en la órbita girada. */
    private class StrokeMark(val path: Path, val color: Color, val style: Stroke, val glow: Boolean, val orbit: Boolean) : Mark() {
        val measure: PathMeasure = PathMeasure().apply { setPath(path, false) }
    }

    /** Punto animado: se funde con el progreso. */
    private class DotMark(val center: Offset, val radius: Float, val color: Color, val glow: Boolean, val orbit: Boolean) : Mark()

    /** Contorno de texto animado: se funde con el progreso. */
    private class TextMark(val layout: TextLayoutResult, val topLeft: Offset, val color: Color, val style: Stroke, val glow: Boolean) : Mark()
}
