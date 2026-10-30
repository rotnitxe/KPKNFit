package com.example.kpkn.ui.design.depth

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * PROFUNDIDAD · degradados y resplandores: los 43 del catálogo (`KpknGradient`), con la semántica del CSS.
 *
 * Lo que CSS hace y Compose no, y cómo se resuelve aquí:
 *  - El ángulo de `linear-gradient(<deg>)` mide la línea del degradado como |W·sen| + |H·cos| desde el centro: se calcula con el tamaño real.
 *  - CSS interpola en alfa PREMULTIPLICADO (de `transparent` a un color no pasa por gris): se subdivide cada tramo en 8 partes interpoladas en
 *    premultiplicado, que Skia luego une en recta (error < 1/255).
 *  - `radial-gradient(<rx> <ry> at <cx> <cy>)` es una ELIPSE: se dibuja un radial circular con una matriz local que lo achata.
 *  - `repeating-linear-gradient` con paradas en px: se repite con `TileMode.REPEAT` sobre un periodo en px de pantalla.
 *  - `background-size` (la rejilla): se pinta teselado.
 *
 *   // 1. Un preset (un solo degradado) como fondo:  Box(Modifier.kpknGradient(KpknGradient.VeloNegro2, RoundedCornerShape(24.dp)))
 *   // 2. Un preset con color de módulo:             Modifier.kpknGradient(KpknGradient.ResplandorZona, CircleShape, KpknGradientArgs(zone = zoneColor))
 *   // 3. Valor exacto de una fila (cambia una parada): val s = KpknGradient.VeloNegro2.spec(); Modifier.kpknGradient(s.copy(layers = listOf((s.layers[0] as KpknGradientLayer.Linear).copy(angleDeg = 175f))))
 */

/** Una parada de color: [position] como fracción de la línea (0..1), o [px] en dp (CSS `3px`); sin ninguna, se reparte entre sus vecinas. */
@Immutable
data class KpknGradStop(val color: Color, val position: Float? = null, val px: Float? = null)

/** Una capa de `background-image`. */
sealed interface KpknGradientLayer {
    val stops: List<KpknGradStop>

    /** `linear-gradient(<angleDeg>deg, …)` (0° = hacia arriba, 90° = hacia la derecha, 180° = hacia abajo). */
    @Immutable
    data class Linear(val angleDeg: Float, override val stops: List<KpknGradStop>, val repeating: Boolean = false) : KpknGradientLayer

    /** `radial-gradient(<rx> <ry> at <cx> <cy>, …)`: radios y centro como fracción del ancho y del alto del elemento. */
    @Immutable
    data class Radial(val rx: Float, val ry: Float, val cx: Float, val cy: Float, override val stops: List<KpknGradStop>) : KpknGradientLayer
}

/** Un `background-image` de una o varias capas (la PRIMERA se pinta encima). [tile] = `background-size: <n>px <n>px`. */
@Immutable
data class KpknGradientSpec(val layers: List<KpknGradientLayer>, val tile: Dp? = null) {
    constructor(vararg layers: KpknGradientLayer, tile: Dp? = null) : this(layers.toList(), tile)
}

/** Colores de módulo y variables de los degradados dinámicos. Los valores por defecto son los de la hoja del lienzo (`--zc`, `--ink`, `--p`, `--col`, `--ene`). */
@Immutable
data class KpknGradientArgs(
    val zone: Color = Color(0xFF87C9A5),
    val ink: Color = Color(0xFFF2EEE6),
    val progress: Float = 0.5f,
    val cardio: Color = Color(0xFF8FB2FF),
    val energy: Color = Color(0xFFF7CF73),
)

/* ------------------------------------------------------------------------------------------------------------------------------------ */

private const val SubSteps = 8

private fun premulLerp(a: Color, b: Color, t: Float): Color {
    val alpha = a.alpha + (b.alpha - a.alpha) * t
    if (alpha <= 0f) return Color(0f, 0f, 0f, 0f)
    val r = (a.red * a.alpha + (b.red * b.alpha - a.red * a.alpha) * t) / alpha
    val g = (a.green * a.alpha + (b.green * b.alpha - a.green * a.alpha) * t) / alpha
    val bl = (a.blue * a.alpha + (b.blue * b.alpha - a.blue * a.alpha) * t) / alpha
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), bl.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
}

/** Posiciones (0..1 de la línea) de las paradas: px a fracción, monótonas y con las automáticas repartidas, como CSS. */
private fun resolvePositions(stops: List<KpknGradStop>, lengthPx: Float, density: Float): FloatArray {
    val n = stops.size
    val p = FloatArray(n) { Float.NaN }
    for (i in 0 until n) {
        val s = stops[i]
        p[i] = s.position ?: s.px?.let { if (lengthPx > 0f) it * density / lengthPx else 0f } ?: Float.NaN
    }
    if (p[0].isNaN()) p[0] = 0f
    if (p[n - 1].isNaN()) p[n - 1] = 1f
    var prev = Float.NEGATIVE_INFINITY
    for (i in 0 until n) {
        if (!p[i].isNaN()) {
            if (p[i] < prev) p[i] = prev
            prev = p[i]
        }
    }
    var i = 0
    while (i < n) {
        if (p[i].isNaN()) {
            var j = i
            while (p[j].isNaN()) j++
            val a = p[i - 1]
            val b = p[j]
            for (k in i until j) p[k] = a + (b - a) * (k - i + 1) / (j - i + 1)
            i = j
        }
        i++
    }
    return p
}

/** Subdivide cada tramo para imitar la interpolación premultiplicada del CSS. Devuelve colores ARGB y posiciones. */
private fun expandStops(stops: List<KpknGradStop>, pos: FloatArray): Pair<IntArray, FloatArray> {
    val colors = ArrayList<Int>()
    val positions = ArrayList<Float>()
    for (i in stops.indices) {
        colors += stops[i].color.toArgb()
        positions += pos[i]
        if (i < stops.size - 1 && pos[i + 1] - pos[i] > 1e-5f && stops[i].color != stops[i + 1].color) {
            for (k in 1 until SubSteps) {
                val t = k / SubSteps.toFloat()
                colors += premulLerp(stops[i].color, stops[i + 1].color, t).toArgb()
                positions += pos[i] + (pos[i + 1] - pos[i]) * t
            }
        }
    }
    return colors.toIntArray() to positions.toFloatArray()
}

internal fun KpknGradientLayer.createShader(size: Size, density: Float): Shader = when (this) {
    is KpknGradientLayer.Linear -> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val length = abs(size.width * dx) + abs(size.height * dy)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val sx = cx - dx * length / 2f
        val sy = cy - dy * length / 2f
        val pos = resolvePositions(stops, length, density)
        if (repeating) {
            val p0 = pos.first()
            val pl = pos.last()
            val span = (pl - p0).coerceAtLeast(1e-4f)
            val norm = FloatArray(pos.size) { (pos[it] - p0) / span }
            val (colors, positions) = expandStops(stops, norm)
            LinearGradient(
                sx + dx * length * p0, sy + dy * length * p0,
                sx + dx * length * pl, sy + dy * length * pl,
                colors, positions, Shader.TileMode.REPEAT,
            )
        } else {
            val (colors, positions) = expandStops(stops, pos)
            LinearGradient(sx, sy, sx + dx * length, sy + dy * length, colors, positions, Shader.TileMode.CLAMP)
        }
    }

    is KpknGradientLayer.Radial -> {
        val pos = resolvePositions(stops, size.width * rx, density)
        val (colors, positions) = expandStops(stops, pos)
        val cxPx = cx * size.width
        val cyPx = cy * size.height
        val radiusX = (rx * size.width).coerceAtLeast(0.01f)
        val radiusY = (ry * size.height).coerceAtLeast(0.01f)
        val shader = RadialGradient(cxPx, cyPx, radiusX, colors, positions, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(android.graphics.Matrix().also { it.setScale(1f, radiusY / radiusX, cxPx, cyPx) })
        shader
    }
}

/** El degradado de la capa como `Brush` (se rehace con el tamaño de cada dibujo). [density] = px por dp (para las paradas en px). */
fun KpknGradientLayer.toBrush(density: Float): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader = this@toBrush.createShader(size, density)
}

/** Un `Brush` para un preset de UNA sola capa y sin teselado (p. ej. para `Modifier.background(brush, shape)`). Con varias capas, usa [drawKpknGradient]. */
fun KpknGradientSpec.singleBrush(density: Float): Brush {
    require(layers.size == 1 && tile == null) { "singleBrush solo vale para un degradado de una capa sin teselado; usa drawKpknGradient" }
    return layers[0].toBrush(density)
}

/** Pinta [spec] en el rectángulo [topLeft] + [size], con las capas en orden de CSS (la primera, encima) y el teselado si lo lleva. */
fun DrawScope.drawKpknGradient(spec: KpknGradientSpec, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f) {
    val tile = spec.tile
    if (tile == null) {
        for (layer in spec.layers.asReversed()) drawRect(layer.toBrush(density), topLeft, size, alpha)
        return
    }
    val tilePx = tile.toPx()
    val cell = Size(tilePx, tilePx)
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            translate(topLeft.x + x, topLeft.y + y) {
                for (layer in spec.layers.asReversed()) drawRect(layer.toBrush(density), Offset.Zero, cell, alpha)
            }
            x += tilePx
        }
        y += tilePx
    }
}

/** Fondo con el degradado [preset] recortado a [shape]. Los dinámicos toman [args] (zona, tinta, progreso…). */
fun Modifier.kpknGradient(preset: KpknGradient, shape: Shape = RectangleShape, args: KpknGradientArgs = KpknGradientArgs()): Modifier =
    kpknGradient(preset.spec(args), shape)

/** Fondo con un [spec] cualquiera (el valor exacto de una fila: `preset.spec().copy(...)`) recortado a [shape]. */
fun Modifier.kpknGradient(spec: KpknGradientSpec, shape: Shape = RectangleShape): Modifier = drawBehind {
    if (shape === RectangleShape) {
        drawKpknGradient(spec)
    } else {
        val geometry = ShapeGeometry.of(shape.createOutline(size, layoutDirection, this))
        clipPath(geometry.path) { drawKpknGradient(spec) }
    }
}
