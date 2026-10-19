package com.example.kpkn.screens.technique.player.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.player.OverlayJoint
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

/** Qué capas pinta [TechniqueOverlay]. Por defecto, las tres. */
@Immutable
data class TechniqueOverlayOptions(
    val skeleton: Boolean = true,
    val trail: Boolean = true,
    val guide: Boolean = true,
)

/**
 * Rectángulo que ocupa un vídeo de proporción [videoAspect] (ancho/alto, ya girado) dentro de un contenedor de
 * [containerWidth] × [containerHeight] con `RESIZE_MODE_FIT`: centrado y sin recortar. Es el criterio de `AspectRatioFrameLayout`
 * (con menos de un 1 % de diferencia llena el contenedor), así la capa cae exactamente sobre los píxeles del vídeo.
 */
fun fitVideoRect(containerWidth: Float, containerHeight: Float, videoAspect: Float): Rect {
    val out = FloatArray(4)
    fitInto(containerWidth, containerHeight, videoAspect, out)
    return Rect(out[0], out[1], out[0] + out[2], out[1] + out[3])
}

/** Lo mismo que [fitVideoRect] sin asignar: escribe `[x, y, ancho, alto]` en [out]. */
internal fun fitInto(containerWidth: Float, containerHeight: Float, videoAspect: Float, out: FloatArray) {
    if (!(containerWidth > 0f && containerHeight > 0f)) {
        out[0] = 0f
        out[1] = 0f
        out[2] = 0f
        out[3] = 0f
        return
    }
    val deformation = if (videoAspect > 0f) videoAspect / (containerWidth / containerHeight) - 1f else 0f
    when {
        videoAspect <= 0f || abs(deformation) <= FIT_TOLERANCE -> {
            out[0] = 0f
            out[1] = 0f
            out[2] = containerWidth
            out[3] = containerHeight
        }
        deformation > 0f -> {
            // El vídeo es más ancho que el contenedor: llena el ancho y sobran bandas arriba y abajo.
            val h = containerWidth / videoAspect
            out[0] = 0f
            out[1] = (containerHeight - h) / 2f
            out[2] = containerWidth
            out[3] = h
        }
        else -> {
            val w = containerHeight * videoAspect
            out[0] = (containerWidth - w) / 2f
            out[1] = 0f
            out[2] = w
            out[3] = containerHeight
        }
    }
}

private const val FIT_TOLERANCE = 0.01f

/**
 * La capa de técnica sobre el vídeo (VT7a): esqueleto tenue, trayectoria de la barra coloreada por velocidad y guía vertical.
 * Sin estado: pinta lo que [overlay] tenga en el instante [positionUs].
 *
 * - [positionUs] se lee SOLO al dibujar, así que el cambio de fotograma redibuja la capa sin recomponer nada. Es el PTS del
 *   fotograma que se ve.
 * - [videoRect], en píxeles del lienzo, es donde está el vídeo; si es null se calcula con [videoAspect] (FIT, centrado), y con
 *   proporción 0 se usa todo el lienzo.
 * - **Esqueleto:** trazo de 1,5 dp en tinta al 35 %; el lado cercano, al 60 %.
 * - **Trayectoria:** la de la repetición en curso (o la última, mientras se descansa), solo hasta la posición actual; 3 dp, de azul
 *   (lento) a ámbar (rápido) según la velocidad relativa de esa repetición, con un punto de 6 dp en el instante actual. Con
 *   movimiento reducido no se anima: se pinta entera.
 * - **Guía:** línea punteada vertical sobre el medio pie.
 *
 * No asigna nada por fotograma: el `Path`, los pinceles, la paleta y los buffers se crean una vez.
 */
@Composable
fun TechniqueOverlay(
    overlay: TechniqueOverlayData,
    positionUs: () -> Long,
    modifier: Modifier = Modifier,
    videoRect: Rect? = null,
    videoAspect: Float = overlay.videoAspect,
    options: TechniqueOverlayOptions = TechniqueOverlayOptions(),
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val paint = remember(density.density, density.fontScale) { TechniqueOverlayPaint(density) }
    Canvas(modifier.fillMaxSize()) {
        val r = paint.rect
        if (videoRect != null) {
            r[0] = videoRect.left
            r[1] = videoRect.top
            r[2] = videoRect.width
            r[3] = videoRect.height
        } else {
            fitInto(size.width, size.height, videoAspect, r)
        }
        if (r[2] > 0f && r[3] > 0f) {
            drawTechniqueLayer(overlay, positionUs(), r[0], r[1], r[2], r[3], options, reduced, paint)
        }
    }
}

/**
 * Pinceles, colores y buffers de la capa, creados una vez por densidad. Dibujar con `Canvas.drawLine(…, Paint)` y estos pinceles
 * evita el `Stroke` que `DrawScope.drawLine` asigna en cada llamada: unas decenas de trazos por fotograma, sin ninguna asignación.
 */
internal class TechniqueOverlayPaint(density: Density) {
    val dotRadius: Float = with(density) { 3.dp.toPx() }
    val dotHaloRadius: Float = with(density) { 5.dp.toPx() }

    val farColor: Color = WizardColors.text.copy(alpha = 0.35f)
    val nearColor: Color = WizardColors.text.copy(alpha = 0.60f)

    /** Esqueleto: 1,5 dp, extremos redondos. El color se fija en cada pasada. */
    val skeleton: Paint = strokePaint(with(density) { 1.5.dp.toPx() }, farColor)

    /** Trayectoria: 3 dp, extremos redondos. El color se fija en cada tramo. */
    val trail: Paint = strokePaint(with(density) { 3.dp.toPx() }, nearColor)

    /** Contorno oscuro de la trayectoria, de 5,5 dp. */
    val casing: Paint = strokePaint(with(density) { 5.5.dp.toPx() }, Color.Black.copy(alpha = 0.42f)).apply {
        strokeJoin = StrokeJoin.Round
    }

    /** Guía vertical: punteada, de 1,5 dp, extremos romos. */
    val guide: Paint = strokePaint(with(density) { 1.5.dp.toPx() }, WizardColors.textFaint.copy(alpha = 0.85f)).apply {
        strokeCap = StrokeCap.Butt
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 6.dp.toPx() }), 0f)
    }

    /** Punto del instante actual y su halo. */
    val dot: Paint = fillPaint(WizardColors.text)
    val dotHalo: Paint = fillPaint(Color.Black.copy(alpha = 0.40f))

    /** Del azul de «lenta» al ámbar de «rápida» en [PALETTE] pasos: un `lerp` por tramo y fotograma asignaría. */
    private val palette = LongArray(PALETTE) { i ->
        lerp(SymbolPalette.columna, SymbolPalette.energia, i / (PALETTE - 1f)).value.toLong()
    }

    /** `Path` reutilizado del contorno oscuro de la trayectoria. */
    val casingPath = Path()

    /** `[x, y, ancho, alto]` del vídeo en el lienzo. */
    val rect = FloatArray(4)

    /** Color de una velocidad relativa (0 lenta, 1 rápida); sin dato, el punto medio. */
    fun speedColor(relative: Float): Color {
        val v = if (relative.isNaN()) 0.5f else relative.coerceIn(0f, 1f)
        return Color(palette[(v * (PALETTE - 1)).roundToInt()].toULong())
    }

    private fun strokePaint(width: Float, tint: Color): Paint = Paint().apply {
        isAntiAlias = true
        style = PaintingStyle.Stroke
        strokeWidth = width
        strokeCap = StrokeCap.Round
        color = tint
    }

    private fun fillPaint(tint: Color): Paint = Paint().apply {
        isAntiAlias = true
        style = PaintingStyle.Fill
        color = tint
    }

    companion object {
        const val PALETTE = 33
    }
}

// Huesos del esqueleto: parejas de puntos de OverlayJoint y el lado al que pertenecen (0 común, 1 izquierdo, 2 derecho).
private val BONE_A = intArrayOf(
    OverlayJoint.LEFT_SHOULDER, OverlayJoint.LEFT_HIP,
    OverlayJoint.LEFT_SHOULDER, OverlayJoint.LEFT_ELBOW, OverlayJoint.LEFT_SHOULDER, OverlayJoint.LEFT_HIP,
    OverlayJoint.LEFT_KNEE, OverlayJoint.LEFT_ANKLE, OverlayJoint.LEFT_HEEL, OverlayJoint.LEFT_ANKLE,
    OverlayJoint.RIGHT_SHOULDER, OverlayJoint.RIGHT_ELBOW, OverlayJoint.RIGHT_SHOULDER, OverlayJoint.RIGHT_HIP,
    OverlayJoint.RIGHT_KNEE, OverlayJoint.RIGHT_ANKLE, OverlayJoint.RIGHT_HEEL, OverlayJoint.RIGHT_ANKLE,
)
private val BONE_B = intArrayOf(
    OverlayJoint.RIGHT_SHOULDER, OverlayJoint.RIGHT_HIP,
    OverlayJoint.LEFT_ELBOW, OverlayJoint.LEFT_WRIST, OverlayJoint.LEFT_HIP, OverlayJoint.LEFT_KNEE,
    OverlayJoint.LEFT_ANKLE, OverlayJoint.LEFT_HEEL, OverlayJoint.LEFT_FOOT, OverlayJoint.LEFT_FOOT,
    OverlayJoint.RIGHT_ELBOW, OverlayJoint.RIGHT_WRIST, OverlayJoint.RIGHT_HIP, OverlayJoint.RIGHT_KNEE,
    OverlayJoint.RIGHT_ANKLE, OverlayJoint.RIGHT_HEEL, OverlayJoint.RIGHT_FOOT, OverlayJoint.RIGHT_FOOT,
)
private val BONE_SIDE = intArrayOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2)

/** Cola de la trayectoria cuando la serie no tiene repeticiones marcadas: solo los últimos 3 s. */
private const val TAIL_US = 3_000_000L

private fun DrawScope.drawTechniqueLayer(
    data: TechniqueOverlayData,
    positionUs: Long,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    options: TechniqueOverlayOptions,
    reducedMotion: Boolean,
    paint: TechniqueOverlayPaint,
) {
    val canvas = drawContext.canvas
    if (options.guide && !data.guideX.isNaN()) {
        val x = left + data.guideX * width
        canvas.drawLine(Offset(x, top), Offset(x, top + height), paint.guide)
    }
    if (options.skeleton) drawSkeleton(canvas, data, data.frameIndexAt(positionUs), left, top, width, height, paint)
    if (options.trail) drawTrail(canvas, data, positionUs, reducedMotion, left, top, width, height, paint)
}

private fun drawSkeleton(
    canvas: GraphicsCanvas,
    data: TechniqueOverlayData,
    frame: Int,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    paint: TechniqueOverlayPaint,
) {
    if (frame < 0) return
    val j = data.joints
    val base = frame * OverlayJoint.COUNT * 2
    val nearCode = when (data.nearSide) {
        Side.LEFT -> 1
        Side.RIGHT -> 2
        null -> -1
    }
    val pen = paint.skeleton
    // Primero lo común y el lado lejano, a 35 %; encima, el lado cercano, a 60 %.
    for (pass in 0..1) {
        pen.color = if (pass == 0) paint.farColor else paint.nearColor
        for (b in BONE_A.indices) {
            if ((BONE_SIDE[b] == nearCode) != (pass == 1)) continue
            val ia = base + BONE_A[b] * 2
            val ib = base + BONE_B[b] * 2
            val ax = j[ia]
            val bx = j[ib]
            if (ax.isNaN() || bx.isNaN()) continue
            canvas.drawLine(Offset(left + ax * width, top + j[ia + 1] * height), Offset(left + bx * width, top + j[ib + 1] * height), pen)
        }
    }
    // Cuello: del medio de los hombros a la nariz (o desde el hombro que se vea).
    val nose = base + OverlayJoint.NOSE * 2
    val ls = base + OverlayJoint.LEFT_SHOULDER * 2
    val rs = base + OverlayJoint.RIGHT_SHOULDER * 2
    if (!j[nose].isNaN() && (!j[ls].isNaN() || !j[rs].isNaN())) {
        val sx: Float
        val sy: Float
        if (!j[ls].isNaN() && !j[rs].isNaN()) {
            sx = 0.5f * (j[ls] + j[rs])
            sy = 0.5f * (j[ls + 1] + j[rs + 1])
        } else {
            val s = if (j[ls].isNaN()) rs else ls
            sx = j[s]
            sy = j[s + 1]
        }
        pen.color = paint.farColor
        canvas.drawLine(Offset(left + sx * width, top + sy * height), Offset(left + j[nose] * width, top + j[nose + 1] * height), pen)
    }
}

private fun drawTrail(
    canvas: GraphicsCanvas,
    data: TechniqueOverlayData,
    positionUs: Long,
    reducedMotion: Boolean,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    paint: TechniqueOverlayPaint,
) {
    val trail = data.trail ?: return
    val segment = data.trailSegmentAt(positionUs)
    if (segment < 0) return
    val current = data.trailIndexAt(positionUs)
    var from = trail.repFrom[segment]
    val to = trail.repTo[segment]
    if (from >= to) return
    if (data.reps.isEmpty() && current >= 0) {
        // Sin repeticiones marcadas no hay "la de ahora": se enseña la cola de los últimos segundos.
        val oldest = trail.timesUs[current] - TAIL_US
        while (from < to && trail.timesUs[from] < oldest) from++
    }
    val upto = if (reducedMotion) to else min(to, current + 1)
    val maxGap = data.maxGapUs
    val xs = trail.x
    val ys = trail.y
    val ts = trail.timesUs

    if (upto - from >= 2) {
        // Contorno oscuro de una pieza (un solo Path reutilizado) para que la línea se lea sobre cualquier fondo.
        val path = paint.casingPath
        path.rewind()
        var open = false
        for (i in from until upto) {
            if (xs[i].isNaN()) {
                open = false
                continue
            }
            val px = left + xs[i] * width
            val py = top + ys[i] * height
            if (open && ts[i] - ts[i - 1] <= maxGap) path.lineTo(px, py) else path.moveTo(px, py)
            open = true
        }
        canvas.drawPath(path, paint.casing)

        // Encima, tramo a tramo con el color de su velocidad relativa.
        val pen = paint.trail
        for (i in from + 1 until upto) {
            val a = i - 1
            if (xs[a].isNaN() || xs[i].isNaN() || ts[i] - ts[a] > maxGap) continue
            pen.color = paint.speedColor(trail.speed[i])
            canvas.drawLine(Offset(left + xs[a] * width, top + ys[a] * height), Offset(left + xs[i] * width, top + ys[i] * height), pen)
        }
    }

    // El punto del instante actual (con movimiento reducido el trazo se pinta entero, pero el punto sigue a la barra).
    val dot = if (current >= to) to - 1 else current
    if (dot >= from && dot < to && !xs[dot].isNaN()) {
        val center = Offset(left + xs[dot] * width, top + ys[dot] * height)
        canvas.drawCircle(center, paint.dotHaloRadius, paint.dotHalo)
        canvas.drawCircle(center, paint.dotRadius, paint.dot)
    }
}
