package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RestClockMath
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestRunnerPoint
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * El reloj del descanso (LV6, F1 y F3): los RINGS de la app como reloj, sin círculo. Tres formas (Torre, Pista y Espiral) que se llenan
 * con el progreso del descanso, un corredor de trazo fino que recorre la forma y, en cada trazo, el estilo luminoso de los RINGS:
 * degradado a lo ancho, un halo del color del anillo y un destello blanco que lo recorre cada 6 s.
 *
 * El tiempo NO se cuenta aquí: el progreso llega ya calculado desde el restante del controlador (`RestClockMath.progress`). La
 * animación solo suaviza el salto entre dos lecturas para que el llenado sea continuo.
 */

/** Reloj de lo que se mueve en el reloj del descanso (destello, corredor y piernas). Solo se lee al dibujar, así que avanzarlo no recompone. */
@Stable
internal class RestClock {
    var seconds by mutableFloatStateOf(0f)
        internal set
}

/** Congela el reloj en este instante (s): lo usan las hojas de render y las pruebas para dibujar una pose fija. */
internal val LocalRestClockFrozen = compositionLocalOf<Float?> { null }

/**
 * Crea el reloj y lo mueve mientras [active] y la app esté en primer plano (al menos RESUMED). Al pararse conserva su valor. Usa
 * `withInfiniteAnimationFrameNanos`: se detiene con la app en segundo plano y las pruebas de Compose cancelan el bucle.
 */
@Composable
internal fun rememberRestClock(active: Boolean): RestClock {
    val frozen = LocalRestClockFrozen.current
    val clock = remember { RestClock().apply { if (frozen != null) seconds = frozen } }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(running, frozen) {
        if (frozen != null) {
            clock.seconds = frozen
            return@LaunchedEffect
        }
        if (!running) return@LaunchedEffect
        val base = clock.seconds
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                clock.seconds = base + ((now - start) / 1_000_000_000.0).toFloat()
            }
        }
    }
    return clock
}

/**
 * Pestañas «Torre · Pista · Espiral» (F1): el control segmentado pequeño de la app, de 252 dp. Lo elegido vale para el resto de la sesión.
 */
@Composable
internal fun RestShapeTabs(
    selected: RestClockShape,
    onSelect: (RestClockShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknSegmentedControl(
        options = RestClockShape.entries.map { it.label },
        selectedIndex = selected.ordinal,
        onSelect = { index -> onSelect(RestClockShape.entries[index]) },
        modifier = modifier.width(252.dp),
        size = KpknSegmentedSize.Compact,
    )
}

/**
 * La forma del reloj con el corredor. [progress] es el progreso del descanso (0–1) tal como lo da `RestClockMath.progress`; [shape] la
 * forma. Con [overtime] («Descanso cumplido») todo está lleno, el halo respira y el corredor se queda de pie. [animationsOn] en falso
 * (la página no se ve) o «reducir movimiento»: forma llena según el progreso, sin destello y con el corredor de pie en la cabeza del
 * llenado. [runnerScale] agranda al corredor en la versión pequeña (la tarjeta).
 */
@Composable
internal fun RestRingsClock(
    shape: RestClockShape,
    progress: Float,
    modifier: Modifier = Modifier,
    runnerScale: Float = 1f,
    overtime: Boolean = false,
    animationsOn: Boolean = true,
    trackColor: Color = TrackColor,
    strokeBoost: Float = 1f,
) {
    val reduced = wizardReducedMotion()
    val animated = animationsOn && !reduced
    val clock = rememberRestClock(animated)
    val target = if (overtime) 1f else progress.coerceIn(0f, 1f)
    val smooth by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(durationMillis = 900, easing = LinearEasing),
        label = "restRingsProgress",
    )
    val ringPos by animateFloatAsState(
        targetValue = RestClockMath.activeRing(target).toFloat(),
        animationSpec = if (reduced) snap<Float>() else tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "restRingsRunnerRing",
    )
    val geometry = remember { RestRingsGeometry() }
    Canvas(modifier = modifier.semantics { contentDescription = "Reloj de descanso: ${shape.label}" }) {
        drawRestClock(
            shape = shape,
            progress = smooth,
            ringPos = ringPos,
            seconds = clock.seconds,
            animated = animated,
            overtime = overtime,
            runnerScale = runnerScale,
            geometry = geometry,
            trackColor = trackColor,
            strokeBoost = strokeBoost,
        )
    }
}

// ── Dibujo ──────────────────────────────────────────────────────────────────────────────────────────────────────────

private val TrackColor = Color(0xFF151515)
private val InkColor = Color(0xFFF2EEE6)
private val HeadColor = Color(0xFF0B0B0B)

/** Color del halo de cada anillo de la Torre (Columna, Músculos y Energía) y de la Pista y la Espiral. */
private val TowerHalo = listOf(Color(0xFF8FB2FF), Color(0xFFF49A6E), Color(0xFFF7CF73))
private val TrackHalo = Color(0xFFF49A6E)
private val SpiralHalo = Color(0xFFF7CF73)

/** Degradado a lo ancho de cada anillo: Columna, Músculos y Energía (LV_COMUN2). */
private val TowerGradient = listOf(
    listOf(Color(0xFF5E7EC4), Color(0xFFB9CFFF)),
    listOf(Color(0xFFC7704A), Color(0xFFFFC2A3)),
    listOf(Color(0xFFC9A04A), Color(0xFFFFE7A8)),
)

/**
 * Halo del arco: el desenfoque de 5 dp del prototipo se pinta como tres trazos más anchos y casi transparentes (grosor extra y alfa),
 * que suman un 22 % en el centro y se desvanecen hacia fuera. Así no hace falta ningún filtro de desenfoque.
 */
private val GlowLayers = listOf(10f to 0.06f, 6f to 0.08f, 3f to 0.10f)

private const val GleamPeriod = 6f
private const val GleamActive = 2.4f
private const val GleamStagger = 1.2f

/** Largo del destello: 40/900 del perímetro (LV_COMUN2). */
private const val GleamFraction = 40f / 900f

/** Vuelta del corredor: 4,5 s en la Torre y la Pista, 9 s en la Espiral. */
private const val RunLapSeconds = 4.5f
private const val SpiralLapSeconds = 9f

/** Las piernas alternan cada 0,34 s. */
private const val LegPeriodSeconds = 0.34f

/** Trayectorias y degradados, hechos una sola vez en las unidades del dibujo (300 × 280). */
private class RestRingsGeometry {
    val tower: List<Path>
    val towerLength: List<Float>
    val towerBrush: List<Brush>
    val track: Path
    val trackBrush: Brush
    val spiral: Path
    val spiralBrush: Brush

    init {
        tower = RestClockMath.towerRings.map { ring ->
            Path().apply {
                val rect = Rect(
                    RestClockMath.CENTER_X - ring.rx,
                    ring.cy - ring.ry,
                    RestClockMath.CENTER_X + ring.rx,
                    ring.cy + ring.ry,
                )
                // Empieza en el extremo derecho y avanza en sentido horario: el halo y el arco comparten inicio y fin.
                arcTo(rect, 0f, 180f, true)
                arcTo(rect, 180f, 180f, false)
                close()
            }
        }
        towerLength = RestClockMath.towerRings.map { RestClockMath.ellipsePerimeter(it.rx, it.ry) }
        towerBrush = RestClockMath.towerRings.mapIndexed { index, ring ->
            Brush.horizontalGradient(
                colors = TowerGradient[index],
                startX = RestClockMath.CENTER_X - ring.rx,
                endX = RestClockMath.CENTER_X + ring.rx,
            )
        }
        track = Path().apply {
            moveTo(80f, 70f)
            lineTo(220f, 70f)
            arcTo(Rect(150f, 70f, 290f, 210f), -90f, 180f, false)
            lineTo(80f, 210f)
            arcTo(Rect(10f, 70f, 150f, 210f), 90f, 180f, false)
            close()
        }
        trackBrush = Brush.horizontalGradient(
            colors = TowerGradient[1],
            startX = 10f,
            endX = 290f,
        )
        spiral = Path().apply {
            for (k in 0 until RestClockMath.SPIRAL_ARCS) {
                val r = RestClockMath.SPIRAL_STEP * (k + 1)
                val even = k % 2 == 0
                val cy = if (even) 140f else 128f
                arcTo(Rect(150f - r, cy - r, 150f + r, cy + r), if (even) -90f else 90f, 180f, k == 0)
            }
        }
        spiralBrush = Brush.horizontalGradient(
            colors = TowerGradient[2],
            startX = 78f,
            endX = 222f,
        )
    }
}

private fun floorMod(value: Float, period: Float): Float = ((value % period) + period) % period

private fun DrawScope.drawRestClock(
    shape: RestClockShape,
    progress: Float,
    ringPos: Float,
    seconds: Float,
    animated: Boolean,
    overtime: Boolean,
    runnerScale: Float,
    geometry: RestRingsGeometry,
    trackColor: Color,
    strokeBoost: Float,
) {
    // Cada forma se escala a su propia caja (con margen para el halo y el corredor) y queda centrada en el lienzo.
    val box = RestClockMath.viewBox(shape, runnerScale)
    val unit = min(size.width / box.width, size.height / box.height)
    if (unit <= 0f) return
    val offsetX = (size.width - box.width * unit) / 2f - box.left * unit
    val offsetY = (size.height - box.height * unit) / 2f - box.top * unit
    // «Descanso cumplido»: el halo respira (1,8 s) en vez de quedarse quieto.
    val haloBoost = when {
        !overtime -> 1f
        animated -> 1.5f + 1.3f * (0.5f + 0.5f * sin(2.0 * PI * seconds / 1.8).toFloat())
        else -> 2f
    }
    withTransform({
        translate(offsetX, offsetY)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        when (shape) {
            RestClockShape.TOWER -> {
                // Pista y arco de cada anillo, de abajo arriba: el de arriba tapa la parte de atrás del de abajo, como en el logo.
                RestClockMath.towerRings.forEachIndexed { index, ring ->
                    val width = ring.stroke * strokeBoost
                    drawPath(geometry.tower[index], trackColor, style = Stroke(width = width))
                    drawLuminousStroke(
                        path = geometry.tower[index],
                        length = geometry.towerLength[index],
                        fill = RestClockMath.towerFill(progress, index),
                        width = width,
                        halo = TowerHalo[index],
                        brush = geometry.towerBrush[index],
                        closed = true,
                        seconds = seconds,
                        gleamIndex = index,
                        animated = animated,
                        haloBoost = haloBoost,
                    )
                }
            }
            RestClockShape.TRACK -> {
                drawPath(geometry.track, trackColor, style = Stroke(width = 20f * strokeBoost))
                drawPath(
                    geometry.track,
                    InkColor.copy(alpha = 0.08f),
                    style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)),
                )
                drawLuminousStroke(
                    path = geometry.track,
                    length = RestClockMath.trackLength,
                    fill = RestClockMath.singleFill(progress),
                    width = 20f * strokeBoost,
                    halo = TrackHalo,
                    brush = geometry.trackBrush,
                    closed = true,
                    seconds = seconds,
                    gleamIndex = 0,
                    animated = animated,
                    haloBoost = haloBoost,
                )
            }
            RestClockShape.SPIRAL -> {
                drawPath(geometry.spiral, trackColor, style = Stroke(width = 12f * strokeBoost, cap = StrokeCap.Round))
                drawLuminousStroke(
                    path = geometry.spiral,
                    length = RestClockMath.spiralLength,
                    fill = RestClockMath.singleFill(progress),
                    width = 12f * strokeBoost,
                    halo = SpiralHalo,
                    brush = geometry.spiralBrush,
                    closed = false,
                    seconds = seconds,
                    gleamIndex = 0,
                    animated = animated,
                    haloBoost = haloBoost,
                )
            }
        }
        drawRunnerOn(shape, progress, ringPos, seconds, animated, overtime, runnerScale)
    }
}

/**
 * Un trazo con el estilo luminoso: halo, arco con degradado y destello. [fill] (0–1) es lo que está lleno del recorrido; con el trazo
 * completo y cerrado se pinta sin cortes. El arco empieza donde empieza la trayectoria y avanza en sentido horario.
 */
private fun DrawScope.drawLuminousStroke(
    path: Path,
    length: Float,
    fill: Float,
    width: Float,
    halo: Color,
    brush: Brush,
    closed: Boolean,
    seconds: Float,
    gleamIndex: Int,
    animated: Boolean,
    haloBoost: Float,
) {
    if (fill <= 0.002f) return
    val filled = fill.coerceAtMost(1f)
    val dash = if (filled >= 0.999f && closed) {
        null
    } else {
        PathEffect.dashPathEffect(floatArrayOf(filled * length, length * 2f), 0f)
    }
    for ((extra, alpha) in GlowLayers) {
        drawPath(
            path,
            halo.copy(alpha = (alpha * haloBoost).coerceAtMost(0.6f)),
            style = Stroke(width = width + extra, cap = StrokeCap.Round, pathEffect = dash),
        )
    }
    drawPath(path, brush, style = Stroke(width = width, cap = StrokeCap.Round, pathEffect = dash))
    if (!animated) return
    // Destello: un trazo blanco corto recorre lo ya lleno cada 6 s, con 1,2 s de desfase entre anillos.
    val local = floorMod(seconds - gleamIndex * GleamStagger, GleamPeriod)
    if (local >= GleamActive) return
    val t = local / GleamActive
    val eased = t * t * (3f - 2f * t)
    val filledLength = filled * length
    val gleamLength = min(length * GleamFraction, filledLength)
    val start = eased * (filledLength - gleamLength)
    val period = gleamLength + length * 2f
    val gleam = PathEffect.dashPathEffect(floatArrayOf(gleamLength, length * 2f), (period - start) % period)
    drawPath(
        path,
        Color.White.copy(alpha = 0.55f),
        style = Stroke(width = width * 0.3f, cap = StrokeCap.Round, pathEffect = gleam),
    )
}

/** El corredor sobre la forma: en la Torre, sobre el anillo que se llena; en la Pista y la Espiral, por el trazo. */
private fun DrawScope.drawRunnerOn(
    shape: RestClockShape,
    progress: Float,
    ringPos: Float,
    seconds: Float,
    animated: Boolean,
    overtime: Boolean,
    runnerScale: Float,
) {
    if (runnerScale <= 0f) return
    val running = animated && !overtime
    var alpha = 1f
    val u: Float = if (running) {
        val lap = if (shape == RestClockShape.SPIRAL) SpiralLapSeconds else RunLapSeconds
        val phase = floorMod(seconds, lap) / lap
        if (shape == RestClockShape.SPIRAL) {
            // La espiral no es cerrada: el corredor entra y sale con un fundido corto en vez de saltar del final al centro.
            alpha = min(1f, min(phase / 0.04f, (1f - phase) / 0.04f)).coerceIn(0f, 1f)
        }
        phase
    } else {
        // De pie, en la cabeza del llenado.
        val head = when (shape) {
            RestClockShape.TOWER -> RestClockMath.towerFill(progress, RestClockMath.activeRing(progress))
            else -> RestClockMath.singleFill(progress)
        }
        head.coerceIn(0f, 0.9999f)
    }
    val point: RestRunnerPoint = when (shape) {
        RestClockShape.TOWER -> RestClockMath.towerPoint(ringPos, u)
        RestClockShape.TRACK -> RestClockMath.trackPoint(u)
        RestClockShape.SPIRAL -> RestClockMath.spiralPoint(u)
    }
    drawRunner(point, runnerScale, seconds, running, alpha)
}

/**
 * Figura de trazo fino: cabeza en aro, tronco, un brazo y dos piernas que alternan cada 0,34 s (F1). Se dibuja con los pies cerca del
 * punto [point] y mira hacia donde avanza. De pie ([running] en falso) las piernas quedan juntas.
 */
internal fun DrawScope.drawRunner(
    point: RestRunnerPoint,
    figureScale: Float,
    seconds: Float,
    running: Boolean,
    alpha: Float,
) {
    val ink = InkColor.copy(alpha = alpha)
    val stroke = 1.8f
    val swing = if (running) {
        26f * cos(2.0 * PI * seconds / LegPeriodSeconds).toFloat()
    } else {
        5f
    }
    withTransform({
        translate(point.x, point.y - 10f * figureScale)
        scale(figureScale * point.facing, figureScale, pivot = Offset.Zero)
    }) {
        drawCircle(HeadColor.copy(alpha = alpha), radius = 3.6f, center = Offset(0f, -15f))
        drawCircle(ink, radius = 3.6f, center = Offset(0f, -15f), style = Stroke(width = stroke))
        drawLine(ink, Offset(0f, -11f), Offset(0f, -2f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(0f, -8f), Offset(6f, -4f), strokeWidth = stroke, cap = StrokeCap.Round)
        rotate(swing, pivot = Offset(0f, -2f)) {
            drawLine(ink, Offset(0f, -2f), Offset(0f, 7f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        rotate(-swing, pivot = Offset(0f, -2f)) {
            drawLine(ink, Offset(0f, -2f), Offset(0f, 7f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
