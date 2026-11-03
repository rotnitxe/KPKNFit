package com.example.kpkn.ui.design.depth

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/*
 * PROFUNDIDAD · sombras (Fase 0.2 de «Pulido v1»).
 *
 * Una sola implementación de `box-shadow` de CSS para toda la app: capas con desplazamiento, desenfoque, extensión y color, `inset` incluido,
 * recortadas como en el navegador (las de caída y los resplandores se pintan SOLO fuera de la forma; las `inset`, SOLO dentro).
 * Los valores de los presets salen de `docs/design/pulido-v1/paridad/cimientos.json` (catálogo cerrado del lienzo aprobado); la
 * calibración contra las capturas del lienzo está en `paridad/CALIBRACION_DEPTH.md`. Prohibido escribir una sombra suelta en una pantalla.
 *
 * Tres formas de usarlo (cada una en tres líneas):
 *
 *   // 1. Un preset del catálogo, tal cual:
 *   Box(Modifier.background(KpknSurface.card, shape).kpknShadow(KpknShadow.IslaVidrio, shape))
 *
 *   // 2. El valor EXACTO de una fila de la matriz (el preset nombra; la fila manda): cambia una capa del canónico.
 *   val spec = KpknShadow.IslaVidrio.spec.copyLayer(0) { it.copy(y = 10.dp, color = Color(0x99000000)) }
 *   Box(Modifier.background(KpknSurface.card, shape).kpknShadow(spec, shape))
 *
 *   // 3. Un preset con color de módulo (resplandores «zona», anillo de cardio…): el color va por parámetro.
 *   Box(Modifier.kpknShadow(KpknShadow.ResplandorZona12, CircleShape, color = zoneColor))
 *
 * Orden en la cadena de modificadores: `kpknShadow` va DESPUÉS del fondo (`background`/vidrio) si el preset lleva capas `inset` (los realces
 * interiores se pintan sobre el fondo); las capas exteriores se recortan fuera de la forma y no importan en qué orden vayan.
 */

/** Papel de un preset de sombra: de qué familia es en el catálogo. */
enum class KpknShadowKind { Caida, Anillo, Interior, Resplandor, Nula }

/**
 * Una capa de `box-shadow`: `x y blur spread color [inset]`, todo en dp. Los CSS `0 0 0 2px #fff` son capas con `blur = 0.dp` y `spread = 2.dp`.
 * Si [dynamicAlpha] no es null, la capa lleva el color del módulo (`var(--c)`, `var(--zc)`): [color] queda sin especificar y se rellena con
 * [KpknShadowSpec.withDynamicColor] (el alfa del color se multiplica por [dynamicAlpha]; p. ej. `color-mix(… 75%, transparent)` = 0,75).
 */
@Immutable
data class KpknShadowLayer(
    val x: Dp = 0.dp,
    val y: Dp = 0.dp,
    val blur: Dp = 0.dp,
    val spread: Dp = 0.dp,
    val color: Color = Color.Black,
    val inset: Boolean = false,
    val dynamicAlpha: Float? = null,
)

/**
 * Lista de capas de una sombra, en el orden del CSS: la PRIMERA se pinta encima. Las `inset` se pintan sobre el fondo; las demás, bajo él.
 */
@Immutable
data class KpknShadowSpec(val layers: List<KpknShadowLayer>) {
    constructor(vararg layers: KpknShadowLayer) : this(layers.toList())

    /** Cambia la capa [index] (constructor corto del valor exacto de una fila: `spec.copyLayer(0) { it.copy(y = 10.dp) }`). */
    fun copyLayer(index: Int, transform: (KpknShadowLayer) -> KpknShadowLayer): KpknShadowSpec =
        KpknShadowSpec(layers.mapIndexed { i, l -> if (i == index) transform(l) else l })

    /** Rellena las capas dinámicas con [color] (alfa × [KpknShadowLayer.dynamicAlpha]). Con `null` o sin capas dinámicas, no cambia nada. */
    fun withDynamicColor(color: Color?): KpknShadowSpec {
        if (color == null || layers.none { it.dynamicAlpha != null }) return this
        return KpknShadowSpec(layers.map { l ->
            val a = l.dynamicAlpha ?: return@map l
            l.copy(color = color.copy(alpha = (color.alpha * a).coerceIn(0f, 1f)))
        })
    }

    /** Solo las capas exteriores (caídas, anillos y resplandores). */
    val outerLayers: List<KpknShadowLayer> get() = layers.filterNot { it.inset }

    /** Solo las capas interiores (`inset`). */
    val insetLayers: List<KpknShadowLayer> get() = layers.filter { it.inset }

    /** Cuánto sobresale la sombra por cada lado, en dp (lo que hay que dejar libre alrededor para que no se recorte). */
    val reach: Dp
        get() = layers.filterNot { it.inset }.maxOfOrNull { l ->
            (max(abs(l.x.value), abs(l.y.value)) + l.spread.value + l.blur.value).dp
        } ?: 0.dp

    companion object {
        /** Sombra de una sola capa con los mismos nombres que el CSS: `of(y = 8.dp, blur = 28.dp, color = Color(0xAA000000))`. */
        fun of(
            x: Dp = 0.dp,
            y: Dp = 0.dp,
            blur: Dp = 0.dp,
            spread: Dp = 0.dp,
            color: Color = Color.Black,
            inset: Boolean = false,
        ): KpknShadowSpec = KpknShadowSpec(KpknShadowLayer(x, y, blur, spread, color, inset))

        val None = KpknShadowSpec(emptyList())
    }
}

/**
 * Constantes de equivalencia CSS → Android que salieron de la calibración (ver `paridad/CALIBRACION_DEPTH.md`).
 * CSS `box-shadow` con desenfoque B: gaussiana de σ = B / 2 (px de CSS = dp). Android `BlurMaskFilter(radius)`: σ = 0,57735 · radius + 0,5 (en px de pantalla).
 */
object KpknDepthCalibration {
    /** σ (en dp) de la gaussiana por cada dp de desenfoque de `box-shadow` y `text-shadow`: CSS dice B / 2. */
    const val ShadowSigmaPerBlur = 0.5f

    /** Skia: σ = [SkiaSigmaSlope] · radius + [SkiaSigmaOffset] (la conversión de `BlurMaskFilter` y de `setShadowLayer`). */
    const val SkiaSigmaSlope = 0.57735f
    const val SkiaSigmaOffset = 0.5f

    /** Radio de `BlurMaskFilter` (px) que da una gaussiana de [sigmaPx]; nunca 0 (Android lo rechaza). */
    fun skiaRadius(sigmaPx: Float): Float = ((sigmaPx - SkiaSigmaOffset) / SkiaSigmaSlope).coerceAtLeast(0.01f)

    /** Radio de `BlurMaskFilter` / `Shadow.blurRadius` (px) para un `blur` de CSS de [blurPx] píxeles de pantalla. */
    fun skiaRadiusForCssBlur(blurPx: Float): Float = skiaRadius(blurPx * ShadowSigmaPerBlur)

    /** Elevación de `Modifier.shadow` (Android < 9, sin `BlurMaskFilter` acelerado) más cercana a un desenfoque CSS de [blur]. */
    fun fallbackElevation(blur: Dp): Dp = (blur.value * FallbackElevationPerBlur).dp
    const val FallbackElevationPerBlur = 0.5f
}

/* ------------------------------------------------------------------------------------------------------------------------------------ */
/* Modificadores                                                                                                                         */
/* ------------------------------------------------------------------------------------------------------------------------------------ */

/**
 * Dibuja el preset [preset] (todas sus capas, en el orden de pintado del CSS) alrededor de [shape]. [color] es el color del módulo para los
 * presets dinámicos (`KpknShadow.isDynamic`); en los fijos se ignora. Sin color, usa el de muestra de la hoja del lienzo.
 */
fun Modifier.kpknShadow(preset: KpknShadow, shape: Shape, color: Color = Color.Unspecified): Modifier =
    kpknShadow(preset.spec(color), shape)

/** Igual que la sobrecarga con preset, con un [spec] cualquiera (el valor exacto de una fila: `preset.spec.copyLayer(...)`). */
fun Modifier.kpknShadow(spec: KpknShadowSpec, shape: Shape): Modifier = kpknDepthLayers(spec, shape, drawOuter = true, drawInset = true)

/**
 * Anillo: las capas EXTERIORES de [preset] (`0 0 0 2px …`, aureolas, `corte-hoja`…). Es un borde de color que sobresale de la forma; no hace
 * falta que el elemento tenga espacio reservado para él.
 */
fun Modifier.kpknRing(preset: KpknShadow, shape: Shape, color: Color = Color.Unspecified): Modifier =
    kpknDepthLayers(preset.spec(color), shape, drawOuter = true, drawInset = false)

fun Modifier.kpknRing(spec: KpknShadowSpec, shape: Shape): Modifier = kpknDepthLayers(spec, shape, drawOuter = true, drawInset = false)

/**
 * Realce interior: las capas `inset` de [preset] (`inset 0 1px #ffffff14`, subrayados `inset 0 -2px`, bordes interiores). Va DESPUÉS del fondo
 * en la cadena de modificadores (se pinta sobre él, bajo el contenido).
 */
fun Modifier.kpknInset(preset: KpknShadow, shape: Shape, color: Color = Color.Unspecified): Modifier =
    kpknDepthLayers(preset.spec(color), shape, drawOuter = false, drawInset = true)

fun Modifier.kpknInset(spec: KpknShadowSpec, shape: Shape): Modifier = kpknDepthLayers(spec, shape, drawOuter = false, drawInset = true)

private fun Modifier.kpknDepthLayers(spec: KpknShadowSpec, shape: Shape, drawOuter: Boolean, drawInset: Boolean): Modifier {
    // Android < 9: BlurMaskFilter no se acelera por hardware. Las capas con desenfoque caen a la elevación de plataforma más cercana
    // (sombra de Material) y las demás (anillos, interiores) se siguen dibujando, porque no necesitan desenfoque.
    val fallbackBlur = drawOuter && Build.VERSION.SDK_INT < 28
    val fallbackLayer = if (fallbackBlur) spec.outerLayers.firstOrNull { it.blur.value > 0f && it.color.alpha > 0f } else null
    val base = if (fallbackLayer != null) {
        Modifier.shadow(KpknDepthCalibration.fallbackElevation(fallbackLayer.blur), shape, clip = false)
    } else {
        Modifier
    }
    return this.then(base).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val shapeGeometry = ShapeGeometry.of(outline)
        val density = this.density
        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.drawKpknLayers(
                    spec = spec,
                    geometry = shapeGeometry,
                    density = density,
                    drawOuter = drawOuter,
                    drawInset = drawInset,
                    skipBlurredOuter = fallbackBlur,
                )
            }
        }
    }
}

/** Dibuja [spec] alrededor de [shape] dentro de un [DrawScope] cualquiera (para dibujos propios: gráficos, anillos de progreso…). */
fun DrawScope.drawKpknShadow(spec: KpknShadowSpec, shape: Shape, topLeft: Offset = Offset.Zero, size: androidx.compose.ui.geometry.Size = this.size) {
    val geometry = ShapeGeometry.of(shape.createOutline(size, layoutDirection, this)).translated(topLeft)
    drawIntoCanvas { canvas ->
        canvas.drawKpknLayers(spec, geometry, density, drawOuter = true, drawInset = true, skipBlurredOuter = false)
    }
}

/* ------------------------------------------------------------------------------------------------------------------------------------ */
/* Motor de dibujo                                                                                                                       */
/* ------------------------------------------------------------------------------------------------------------------------------------ */

/** La forma del elemento como rectángulo redondeado (lo habitual: permite inflar y desinflar con la extensión) o como trazado cualquiera. */
internal class ShapeGeometry private constructor(val rrect: RoundRect?, val path: Path, val bounds: Rect) {
    fun translated(by: Offset): ShapeGeometry =
        if (by == Offset.Zero) this else of(
            if (rrect != null) Outline.Rounded(rrect.moved(by.x, by.y)) else Outline.Generic(Path().also { it.addPath(path, by) }),
        )

    companion object {
        fun of(outline: Outline): ShapeGeometry = when (outline) {
            is Outline.Rectangle -> RoundRect(outline.rect, CornerRadius.Zero).let { ShapeGeometry(it, Path().also { p -> p.addRoundRect(it) }, outline.rect) }
            is Outline.Rounded -> ShapeGeometry(outline.roundRect, Path().also { it.addRoundRect(outline.roundRect) }, outline.roundRect.let { Rect(it.left, it.top, it.right, it.bottom) })
            is Outline.Generic -> ShapeGeometry(null, outline.path, outline.path.getBounds())
        }
    }
}

/** CSS: con radio 0 la extensión no redondea la esquina; con radio > 0 el radio crece (o decrece) lo mismo que la extensión. */
private fun CornerRadius.grow(d: Float): CornerRadius =
    if (x <= 0f || y <= 0f) CornerRadius.Zero else CornerRadius((x + d).coerceAtLeast(0f), (y + d).coerceAtLeast(0f))

private fun RoundRect.moved(dx: Float, dy: Float): RoundRect = RoundRect(
    left = left + dx, top = top + dy, right = right + dx, bottom = bottom + dy,
    topLeftCornerRadius = topLeftCornerRadius, topRightCornerRadius = topRightCornerRadius,
    bottomRightCornerRadius = bottomRightCornerRadius, bottomLeftCornerRadius = bottomLeftCornerRadius,
)

private fun RoundRect.inflate(d: Float): RoundRect = RoundRect(
    left = left - d, top = top - d, right = right + d, bottom = bottom + d,
    topLeftCornerRadius = topLeftCornerRadius.grow(d), topRightCornerRadius = topRightCornerRadius.grow(d),
    bottomRightCornerRadius = bottomRightCornerRadius.grow(d), bottomLeftCornerRadius = bottomLeftCornerRadius.grow(d),
)

/** La forma inflada [d] px (la extensión del CSS) y desplazada [dx], [dy]. Un trazado cualquiera se escala desde su centro. */
private fun ShapeGeometry.shifted(d: Float, dx: Float, dy: Float): Path {
    val r = rrect
    if (r != null) {
        val g = r.inflate(d)
        if (g.right <= g.left || g.bottom <= g.top) return Path()
        return Path().also { it.addRoundRect(g.moved(dx, dy)) }
    }
    val w = bounds.width.coerceAtLeast(1f)
    val h = bounds.height.coerceAtLeast(1f)
    val m = Matrix()
    m.translate(bounds.center.x + dx, bounds.center.y + dy)
    m.scale((w + 2 * d) / w, (h + 2 * d) / h)
    m.translate(-bounds.center.x, -bounds.center.y)
    return Path().also { it.addPath(path); it.transform(m) }
}

private fun Canvas.drawKpknLayers(
    spec: KpknShadowSpec,
    geometry: ShapeGeometry,
    density: Float,
    drawOuter: Boolean,
    drawInset: Boolean,
    skipBlurredOuter: Boolean,
) {
    val paint = Paint().also { it.isAntiAlias = true }
    val fw = paint.asFrameworkPaint()
    fun applyBlur(blurPx: Float) {
        fw.maskFilter = if (blurPx > 0f) BlurMaskFilter(KpknDepthCalibration.skiaRadiusForCssBlur(blurPx), BlurMaskFilter.Blur.NORMAL) else null
    }
    // 1. Capas exteriores (la última del CSS se pinta primero, la primera queda encima), recortadas FUERA de la forma.
    if (drawOuter && spec.layers.any { !it.inset }) {
        save()
        clipPath(geometry.path, ClipOp.Difference)
        for (layer in spec.layers.asReversed()) {
            if (layer.inset || layer.color.alpha <= 0f) continue
            if (skipBlurredOuter && layer.blur.value > 0f) continue
            val blurPx = layer.blur.value * density
            val path = geometry.shifted(layer.spread.value * density, layer.x.value * density, layer.y.value * density)
            paint.color = layer.color
            applyBlur(blurPx)
            drawPath(path, paint)
        }
        restore()
    }
    // 2. Capas interiores: se pinta el «marco» (todo menos el agujero desplazado y encogido) y se recorta DENTRO de la forma.
    if (drawInset && spec.layers.any { it.inset }) {
        save()
        clipPath(geometry.path, ClipOp.Intersect)
        for (layer in spec.layers.asReversed()) {
            if (!layer.inset || layer.color.alpha <= 0f) continue
            val blurPx = layer.blur.value * density
            val spreadPx = layer.spread.value * density
            val dx = layer.x.value * density
            val dy = layer.y.value * density
            val hole = geometry.shifted(-spreadPx, dx, dy)
            val pad = blurPx * 1.5f + abs(dx) + abs(dy) + abs(spreadPx) + 16f * density
            val frame = Path().also {
                it.fillType = PathFillType.EvenOdd
                it.addRect(geometry.bounds.inflate(pad))
                it.addPath(hole)
            }
            paint.color = layer.color
            applyBlur(blurPx)
            drawPath(frame, paint)
        }
        restore()
    }
    fw.maskFilter = null
}

