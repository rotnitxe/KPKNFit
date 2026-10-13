package com.example.kpkn.screens.onboarding.design.entreno.plan.cover

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.TAU
import com.example.kpkn.screens.onboarding.design.entreno.onSymbolsVisible
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 * El motor «CoverStudio» (K3a): el arte a sangre de una portada según su identidad. Cada estilo dibuja sobre un marco 3:4 que
 * cubre la caja (en una banda ancha, recorta alrededor de su foco). Los trazos se escalan con la portada (1 unidad de diseño =
 * min(w, h) / 300 px) y todo lo que no cambia con el tiempo (curvas, ruta, textos medidos) se prepara una vez por tamaño.
 */

/** Ciclos de cada movimiento, en segundos: lentos y sutiles (8 a 12 s). */
private const val NUMERAL_PERIOD = 10f
private const val TOPO_PERIOD = 12f
private const val PLATES_PERIOD = 8f
private const val GRID_PERIOD = 9f
private const val WAVES_PERIOD = 12f
private const val DOT_PERIOD = 10f

/** Muestras de cada contorno, de la ruta del topo y de cada onda. */
private const val CONTOUR_SEGMENTS = 72
private const val ROUTE_SAMPLES = 40
private const val WAVE_SAMPLES = 60

/** Alturas (fracción del alto) de los discos desde el centro hacia fuera. */
private val DISC_HEIGHTS = floatArrayOf(0.50f, 0.42f, 0.35f, 0.29f, 0.25f, 0.22f)

/** El numeral de NUMERAL: Syne ExtraBold, sin ajustes de texto. */
private val NUMERAL_STYLE = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02f).em)

/** La etiqueta «SEMANAS» de NUMERAL: versalitas espaciadas, en 13 sp (el mínimo del wizard). */
private val LABEL_STYLE = WizardTypography.eyebrow.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 1.6.sp)

// ---------------------------------------------------------------- funciones puras (con prueba)

/** Un número en [0, 1) fijo para una semilla y un índice: mezcla de 64 bits, igual en cada proceso. */
internal fun seedUnit(seed: Int, index: Int): Float {
    var z = (seed.toLong() shl 32) xor (index.toLong() and 0xFFFFFFFFL)
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    z = z xor (z ushr 31)
    return ((z ushr 40) and 0xFFFFFFL).toFloat() / 16777216f
}

/** Intensidad (0–1) de la semana [week] de [weeks]: sube con la acumulación y la intensificación; la descarga (la última) va tenue. */
internal fun gridLoadOf(week: Int, weeks: Int): Float = when {
    weeks <= 1 -> 1f
    week >= weeks - 1 -> 0.25f
    else -> 0.35f + 0.65f * week / maxOf(1, weeks - 2)
}

/** Hasta dónde llega la ruta del topo en el ciclo [u] (0–1): crece hasta el 62 % del ciclo y se queda entera. */
internal fun routeHeadOf(u: Float): Float = (u / 0.625f).coerceIn(0f, 1f)

/** Desde dónde se ve la ruta del topo en el ciclo [u]: tras el 62 % del ciclo se borra desde atrás. */
internal fun routeTailOf(u: Float): Float = ((u - 0.625f) / 0.375f).coerceIn(0f, 1f)

/** El punto de foco de cada estilo (0–1 del marco de diseño): el que queda en el centro de una banda ancha. */
internal fun coverFocusOf(style: CoverStudioStyle): Offset = when (style) {
    CoverStudioStyle.NUMERAL -> Offset(0.5f, 0.40f)
    CoverStudioStyle.TOPO -> Offset(0.62f, 0.32f)
    CoverStudioStyle.PLATES -> Offset(0.5f, 0.36f)
    CoverStudioStyle.GRID -> Offset(0.5f, 0.35f)
    CoverStudioStyle.WAVES -> Offset(0.5f, 0.5f)
}

/** El marco de diseño de una caja, en px relativos a la caja: un 3:4 que la cubre entera. */
internal class CoverFrame(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * El marco que cubre una caja de [boxW] × [boxH] px con el 3:4 de diseño y centra [focus] en su centro, sin dejar huecos. En una
 * portada 3:4 es la caja misma; en una banda ancha recorta el arte alrededor de su motivo.
 */
internal fun coverFrameOf(boxW: Float, boxH: Float, focus: Offset): CoverFrame {
    val s = maxOf(boxW / 3f, boxH / 4f)
    val w = 3f * s
    val h = 4f * s
    return CoverFrame(
        x = (boxW / 2f - focus.x * w).coerceIn(boxW - w, 0f),
        y = (boxH / 2f - focus.y * h).coerceIn(boxH - h, 0f),
        w = w,
        h = h,
    )
}

/** Los colores de una portada según su fondo: [background] llena la caja; [main] es el grafismo principal, [second] el acento secundario. */
internal class CoverRoles(val background: Color, val main: Color, val second: Color, val ink: Color)

/** NOCHE pinta el acento principal sobre negro; TINTA y PAPEL, negro y el acento secundario. */
internal fun coverRolesOf(identity: CoverIdentity): CoverRoles = when (identity.palette) {
    CoverPaletteKind.NOCHE -> CoverRoles(
        background = lerp(COVER_INK, identity.primary, 0.08f),
        main = identity.primary,
        second = identity.secondary,
        ink = WizardColors.text,
    )
    CoverPaletteKind.TINTA -> CoverRoles(identity.primary, COVER_INK, identity.secondary, COVER_INK)
    CoverPaletteKind.PAPEL -> CoverRoles(COVER_PAPER, COVER_INK, identity.secondary, COVER_INK)
}

// ---------------------------------------------------------------- escenas

/** Lo que comparten las escenas de una caja: la identidad, el marco, la unidad de trazo y los colores. */
private class StudioContext(val identity: CoverIdentity, val frame: CoverFrame, val unit: Float, val roles: CoverRoles) {
    /** Abscisa en px de la posición [u] del marco (0 = borde izquierdo del marco, 1 = derecho). */
    fun px(u: Float): Float = frame.x + u * frame.w

    /** Ordenada en px de la posición [v] del marco (0 = borde superior, 1 = inferior). */
    fun py(v: Float): Float = frame.y + v * frame.h
}

/** NUMERAL: la cifra de la motif en Syne ExtraBold, a sangre, con una copia de contorno que se desliza. */
private class NumeralScene(private val ctx: StudioContext, measurer: TextMeasurer, density: Density) {
    private val outline = Stroke(width = 1.5f * ctx.unit)

    /** Cuerpo de la cifra: 0,56 h en px, convertido a sp para que no cambie con la letra grande del sistema. */
    private val numeralSize = (0.56f * ctx.frame.h / (density.density * density.fontScale)).sp

    /** El relleno: un bloque de texto sin trazo. */
    private val fill: TextLayoutResult = measurer.measure(
        text = ctx.identity.motif.toString(),
        style = NUMERAL_STYLE.copy(fontSize = numeralSize),
        maxLines = 1,
        softWrap = false,
    )

    /**
     * El contorno: otro bloque de texto, con su propio estilo de trazo. Con el mismo bloque, el trazo se quedaba activo y el relleno
     * salía hueco. La línea base queda en 0,60 h: por encima de la peana (desde 0,56 h) y del título de tres líneas.
     */
    private val edge: TextLayoutResult = measurer.measure(
        text = ctx.identity.motif.toString(),
        style = NUMERAL_STYLE.copy(fontSize = numeralSize, drawStyle = outline),
        maxLines = 1,
        softWrap = false,
    )
    private val label: TextLayoutResult = measurer.measure(
        text = if (ctx.identity.motif == 1) "SEMANA" else "SEMANAS",
        style = LABEL_STYLE,
        maxLines = 1,
        softWrap = false,
    )

    fun draw(scope: DrawScope, t: Float, animated: Boolean) {
        with(scope) {
            val r = ctx.roles
            for (i in 0 until 5) {
                val y = ctx.py(0.07f + 0.065f * i)
                drawLine(r.ink.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f * ctx.unit)
            }
            drawText(label, color = r.ink.copy(alpha = 0.8f), topLeft = Offset(ctx.px(0.92f) - label.size.width, ctx.py(0.175f)), drawStyle = Fill)
            val slide = if (animated) 0.01f * ctx.frame.w * sin(TAU * t / NUMERAL_PERIOD) else 0f
            val base = ctx.py(0.60f) - fill.firstBaseline
            val left = ctx.px(0.16f)
            // La copia de contorno va detrás, desplazada hacia arriba y a la derecha; la cifra va encima.
            drawText(
                edge,
                color = r.second.copy(alpha = 0.6f),
                topLeft = Offset(left + 0.02f * ctx.frame.w + slide, base - 0.02f * ctx.frame.h),
                drawStyle = outline,
            )
            // El relleno pide `Fill` de forma explícita: si no, hereda el trazo del contorno que se pintó antes y sale hueco.
            drawText(fill, color = r.main, topLeft = Offset(left, base), drawStyle = Fill)
        }
    }
}

/** TOPO: curvas de nivel alrededor de una cumbre, con una ruta punteada que sube hasta ella y se borra. */
private class TopoScene(private val ctx: StudioContext) {
    private val rings = ctx.identity.motif.coerceAtLeast(4)
    private val contours = Array(rings) { Path() }
    private val thin = Stroke(width = 1f * ctx.unit)
    private val thick = Stroke(width = 1.6f * ctx.unit)
    private val summit = Path()
    private val route = Path()
    private val routeX = FloatArray(ROUTE_SAMPLES)
    private val routeY = FloatArray(ROUTE_SAMPLES)
    private val routeStroke = Stroke(
        width = 1.4f * ctx.unit,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * ctx.unit, 5f * ctx.unit)),
    )

    init {
        val cx = ctx.px(0.62f)
        val cy = ctx.py(0.32f)
        val p1 = seedUnit(ctx.identity.seed, 1) * TAU
        val p2 = seedUnit(ctx.identity.seed, 2) * TAU
        val p3 = seedUnit(ctx.identity.seed, 3) * TAU
        for (i in 0 until rings) {
            val radius = (0.03f + 0.068f * i) * ctx.frame.w
            val path = contours[i]
            for (k in 0..CONTOUR_SEGMENTS) {
                val th = TAU * k / CONTOUR_SEGMENTS
                // Ruido de baja frecuencia (la semilla fija las fases): un círculo deformado y algo aplastado.
                val wobble = 1f + 0.10f * sin(2f * th + p1 + 0.15f * i) + 0.06f * sin(3f * th + p2) + 0.03f * sin(5f * th + p3)
                val x = cx + radius * cos(th) * wobble
                val y = cy + radius * 0.78f * sin(th) * wobble
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        // La cumbre: un triángulo de 8 dp.
        val s = 8f * ctx.unit
        summit.moveTo(cx, cy - 0.6f * s)
        summit.lineTo(cx + 0.5f * s, cy + 0.4f * s)
        summit.lineTo(cx - 0.5f * s, cy + 0.4f * s)
        summit.close()
        // La ruta: una curva cuadrática desde abajo a la izquierda hasta la cumbre, muestreada.
        val sx = ctx.px(0.08f)
        val sy = ctx.py(0.96f)
        val qx = ctx.px(0.22f)
        val qy = ctx.py(0.55f)
        for (k in 0 until ROUTE_SAMPLES) {
            val u = k / (ROUTE_SAMPLES - 1f)
            val a = (1f - u) * (1f - u)
            val b = 2f * (1f - u) * u
            val c = u * u
            routeX[k] = a * sx + b * qx + c * cx
            routeY[k] = a * sy + b * qy + c * cy
        }
    }

    fun draw(scope: DrawScope, t: Float, animated: Boolean) {
        with(scope) {
            val r = ctx.roles
            for (i in 0 until rings) {
                // Las tres interiores llevan el acento principal; las exteriores, tinta tenue.
                if (i >= rings - 3) drawPath(contours[i], r.main, style = thick)
                else drawPath(contours[i], r.ink.copy(alpha = 0.22f), style = thin)
            }
            drawPath(summit, r.main)
            val u = (t / TOPO_PERIOD) % 1f
            val head = if (animated) routeHeadOf(u) else 1f
            val tail = if (animated) routeTailOf(u) else 0f
            val from = (tail * (ROUTE_SAMPLES - 1)).toInt().coerceIn(0, ROUTE_SAMPLES - 1)
            val to = ((head * (ROUTE_SAMPLES - 1)).toInt() + 1).coerceAtMost(ROUTE_SAMPLES - 1)
            if (to > from) {
                route.reset()
                route.moveTo(routeX[from], routeY[from])
                for (k in from + 1..to) route.lineTo(routeX[k], routeY[k])
                drawPath(route, r.second, style = routeStroke)
            }
        }
    }
}

/** PLATES: una barra con sus discos de perfil sobre un suelo; el reflejo bajo el suelo va al 14 %. */
private class PlatesScene(private val ctx: StudioContext) {
    private val discs = ctx.identity.motif.coerceAtLeast(1)
    private val innerOff = FloatArray(discs)
    private val outerOff = FloatArray(discs)
    private val halfH = FloatArray(discs)

    init {
        // Desde el centro hacia fuera: el disco más ancho es el primero y los de fuera son más estrechos y bajos.
        var cursor = 0.04f * ctx.frame.w
        for (i in 0 until discs) {
            val frac = if (discs > 1) i / (discs - 1f) else 0f
            val widthFrac = 0.08f - 0.03f * frac
            innerOff[i] = cursor
            cursor += widthFrac * ctx.frame.w
            outerOff[i] = cursor
            cursor += 0.006f * ctx.frame.w
            halfH[i] = 0.5f * DISC_HEIGHTS[minOf(i, DISC_HEIGHTS.size - 1)] * ctx.frame.h
        }
    }

    fun draw(scope: DrawScope, t: Float, animated: Boolean) {
        with(scope) {
            val r = ctx.roles
            // La barra respira: sube y baja 1 % de la altura; el suelo no se mueve.
            val bob = if (animated) 0.01f * ctx.frame.h * sin(TAU * t / PLATES_PERIOD) else 0f
            // El disco mayor (0,50 h de alto) apoya en el suelo a 0,61 h, por encima del velo: el texto ya no cae sobre un disco.
            val axis = ctx.py(0.36f) + bob
            val ground = ctx.py(0.61f)
            val cx = ctx.px(0.5f)
            drawLine(r.ink.copy(alpha = 0.5f), Offset(0f, ground), Offset(size.width, ground), strokeWidth = 1f * ctx.unit)
            drawLine(
                r.ink.copy(alpha = 0.9f),
                Offset(ctx.px(-0.1f), axis),
                Offset(ctx.px(1.1f), axis),
                strokeWidth = 3f * ctx.unit,
                cap = StrokeCap.Round,
            )
            drawSide(-1f, cx, axis, ground, r)
            drawSide(1f, cx, axis, ground, r)
        }
    }

    /** Los discos de un lado ([sign] −1 o +1) y su collarín; cada disco lleva su reflejo al 14 % bajo el suelo. */
    private fun DrawScope.drawSide(sign: Float, cx: Float, axis: Float, ground: Float, r: CoverRoles) {
        val rad = CornerRadius(0.012f * ctx.frame.w)
        for (i in 0 until discs) {
            val x1 = cx + sign * innerOff[i]
            val x2 = cx + sign * outerOff[i]
            val left = min(x1, x2)
            val width = abs(x2 - x1)
            val h = 2f * halfH[i]
            val color = when (i % 3) {
                0 -> r.main
                1 -> r.second
                else -> r.ink.copy(alpha = 0.55f)
            }
            drawRoundRect(
                color = color.copy(alpha = 0.14f),
                topLeft = Offset(left, 2f * ground - (axis + halfH[i])),
                size = Size(width, h),
                cornerRadius = rad,
            )
            drawRoundRect(color = color, topLeft = Offset(left, axis - halfH[i]), size = Size(width, h), cornerRadius = rad)
        }
        val collarW = 0.012f * ctx.frame.w
        val collarX = cx + sign * (outerOff[discs - 1] + 0.004f * ctx.frame.w)
        drawRoundRect(
            color = r.ink,
            topLeft = Offset(if (sign > 0f) collarX else collarX - collarW, axis - 0.06f * ctx.frame.h),
            size = Size(collarW, 0.12f * ctx.frame.h),
            cornerRadius = rad,
        )
    }
}

/** GRID: semanas por días; los días del programa se encienden con la carga de su semana y una fila recorre las semanas. */
private class GridScene(private val ctx: StudioContext) {
    private val weeks = ctx.identity.motif.coerceAtLeast(1)
    private val gap = 0.018f * ctx.frame.w

    /** Los días que se encienden, repartidos por la semana (3 días: lunes, miércoles y viernes, por ejemplo). */
    private val lit = BooleanArray(7).also { cols ->
        val days = ctx.identity.days.coerceIn(1, 7)
        for (i in 0 until days) cols[i * 7 / days] = true
    }

    /**
     * Celda de 0,085 w; con una sola semana, 0,11 w para que la fila no quede diminuta. Si las semanas no caben en el 70 % superior, se
     * encoge.
     */
    private val cell: Float = minOf((if (weeks == 1) 0.11f else 0.085f) * ctx.frame.w, (0.70f * ctx.frame.h - (weeks - 1) * gap) / weeks)
    private val left: Float = ctx.px(0.5f) - (7 * cell + 6 * gap) / 2f
    private val top: Float = ctx.py(0.35f) - (weeks * cell + (weeks - 1) * gap) / 2f
    private val outline = Stroke(width = 1f * ctx.unit)

    fun draw(scope: DrawScope, t: Float, animated: Boolean) {
        with(scope) {
            val r = ctx.roles
            // Una fila se enciende de izquierda a derecha y pasa a la siguiente semana, en un ciclo de 9 s.
            val phase = if (animated) (t / GRID_PERIOD) % 1f else 0f
            val scaled = phase * weeks
            val sweepRow = if (animated) floor(scaled).toInt().coerceIn(0, weeks - 1) else -1
            val sweep = scaled - floor(scaled)
            val radius = CornerRadius(0.18f * cell)
            for (w in 0 until weeks) {
                val load = gridLoadOf(w, weeks)
                for (c in 0 until 7) {
                    val x = left + c * (cell + gap)
                    val y = top + w * (cell + gap)
                    val swept = w == sweepRow && (c + 0.5f) / 7f <= sweep
                    when {
                        swept -> drawRoundRect(color = r.second, topLeft = Offset(x, y), size = Size(cell, cell), cornerRadius = radius)
                        lit[c] -> drawRoundRect(
                            color = r.main.copy(alpha = load),
                            topLeft = Offset(x, y),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                        )
                        else -> drawRoundRect(
                            color = r.ink.copy(alpha = 0.22f),
                            topLeft = Offset(x, y),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                            style = outline,
                        )
                    }
                }
            }
        }
    }
}

/** WAVES: ondas apiladas a todo el ancho, de secundario arriba a primario abajo; un punto recorre la cresta de la quinta. */
private class WavesScene(private val ctx: StudioContext) {
    private val count = ctx.identity.motif.coerceAtLeast(5)
    private val paths = Array(count) { Path() }
    // Trazo de 1,6 dp arriba a 2,5 dp abajo; la onda de arriba no baja del 55 % de opacidad (en NOCHE, sin esto se perdían).
    private val strokes = Array(count) { i -> Stroke(width = (1.6f + 0.9f * fraction(i)) * ctx.unit, cap = StrokeCap.Round) }
    private val colors = Array<Color>(count) { i ->
        lerp(ctx.roles.second, ctx.roles.main, fraction(i)).copy(alpha = 0.55f + 0.45f * fraction(i))
    }
    private val baseY = FloatArray(count) { i -> ctx.py(0.20f + 0.75f * fraction(i)) }

    /** La amplitud crece hacia el centro de la pila y vuelve a bajar (la ondulación). */
    private val amp = FloatArray(count) { i -> 0.045f * ctx.frame.h * (0.45f + 0.55f * sin(PI.toFloat() * fraction(i))) }
    private val phase = FloatArray(count) { i -> i * 0.42f + 0.6f * seedUnit(ctx.identity.seed, i) }

    private fun fraction(i: Int): Float = i / (count - 1f)

    private fun yOf(i: Int, u: Float, drift: Float): Float = baseY[i] + amp[i] * sin(TAU * 1.4f * u + phase[i] + drift)

    fun draw(scope: DrawScope, t: Float, animated: Boolean) {
        with(scope) {
            // Las fases avanzan despacio: una vuelta cada 12 s.
            val drift = if (animated) TAU * t / WAVES_PERIOD else 0f
            for (i in 0 until count) {
                val path = paths[i]
                path.reset()
                for (k in 0..WAVE_SAMPLES) {
                    val u = -0.02f + 1.04f * k / WAVE_SAMPLES
                    val x = ctx.px(u)
                    val y = yOf(i, u, drift)
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, colors[i], style = strokes[i])
            }
            val crest = minOf(4, count - 1)
            val u = if (animated) (t / DOT_PERIOD) % 1f else 0.5f
            drawCircle(color = ctx.roles.main, radius = 3f * ctx.unit, center = Offset(ctx.px(u), yOf(crest, u, drift)))
        }
    }
}

/**
 * Pinta el arte de una portada en una caja de [boxW] × [boxH] px: el fondo de su paleta y el grafismo de su estilo. Las trayectorias,
 * los trazos y los textos se preparan al crearse (una vez por tamaño); al pintar solo se recorren.
 */
internal class CoverStudioPainter(
    identity: CoverIdentity,
    boxW: Float,
    boxH: Float,
    measurer: TextMeasurer,
    density: Density,
) {
    private val ctx = StudioContext(
        identity = identity,
        frame = coverFrameOf(boxW, boxH, coverFocusOf(identity.style)),
        unit = min(boxW, boxH) / 300f,
        roles = coverRolesOf(identity),
    )
    private val numeral = if (identity.style == CoverStudioStyle.NUMERAL) NumeralScene(ctx, measurer, density) else null
    private val topo = if (identity.style == CoverStudioStyle.TOPO) TopoScene(ctx) else null
    private val plates = if (identity.style == CoverStudioStyle.PLATES) PlatesScene(ctx) else null
    private val grid = if (identity.style == CoverStudioStyle.GRID) GridScene(ctx) else null
    private val waves = if (identity.style == CoverStudioStyle.WAVES) WavesScene(ctx) else null

    /** Pinta la portada en el instante [t] (segundos del reloj, si [animated]) y desplaza el grafismo [dx] px. */
    fun draw(scope: DrawScope, t: Float, animated: Boolean, dx: Float) {
        scope.drawRect(ctx.roles.background)
        scope.withTransform({ translate(dx, 0f) }) {
            numeral?.draw(this, t, animated)
            topo?.draw(this, t, animated)
            plates?.draw(this, t, animated)
            grid?.draw(this, t, animated)
            waves?.draw(this, t, animated)
        }
    }
}

/**
 * El arte a sangre de [identity]: el fondo de su paleta y el grafismo de su estilo, sin texto, sin velo y sin franja. Con [animate] y
 * sin movimiento reducido, cada estilo se mueve con un bucle lento; si no, queda en su cuadro fijo. [parallax] (−1 a 1, se lee al
 * dibujar) desplaza el grafismo un 3 % de su ancho.
 */
@Composable
internal fun CoverStudioArt(
    identity: CoverIdentity,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    reduced: Boolean,
    parallax: () -> Float = { 0f },
) {
    val running = animate && !reduced
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val clock = rememberSymbolClock(active = running && foreground && visible)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Box(
        modifier
            .clipToBounds()
            .onSymbolsVisible { visible = it }
            .drawWithCache {
                // Sin tamaño no hay nada que pintar (y los intervalos del trazo punteado no pueden valer cero).
                if (size.width <= 0f || size.height <= 0f) return@drawWithCache onDrawBehind {}
                val painter = CoverStudioPainter(identity, size.width, size.height, measurer, density)
                onDrawBehind {
                    painter.draw(this, if (running) clock.seconds else 0f, running, parallax() * 0.03f * size.width)
                }
            },
    )
}
