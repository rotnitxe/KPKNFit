package com.example.kpkn.ui.components.rings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/*
 * Torre de tres anillos del logo de KPKN («torre anidada», `res/drawable/kpkn_logo_vertical.xml`).
 *
 * Cada anillo es la banda entre su elipse exterior y su interior. El logo desplaza hacia arriba el centro de la elipse
 * interior, así que la banda de delante es más gruesa que la de detrás; aquí se dibuja con la misma geometría.
 * Abajo Columna, en medio Músculos y arriba Energía. La pista va al 12 % de la tinta de la marca y el arco de progreso
 * empieza en el punto frontal inferior y avanza en sentido horario.
 *
 * Las funciones puras de este archivo (geometría, barrido, bandas de toque y descripción) tienen prueba unitaria.
 */

/** Los tres anillos de la torre, de abajo arriba (orden del logo). */
enum class RingChannel { COLUMNA, MUSCULOS, ENERGIA }

/** Valores 0–100 de la torre. `null` = sin calibrar: la pista sale discontinua y sin arco. */
data class TowerRingValues(val columna: Int?, val musculos: Int?, val energia: Int?) {
    operator fun get(channel: RingChannel): Int? = when (channel) {
        RingChannel.COLUMNA -> columna
        RingChannel.MUSCULOS -> musculos
        RingChannel.ENERGIA -> energia
    }
}

/** Colores de marca de los anillos (mismos valores que `WelcomeScenePalette`) y la tinta de la pista. */
object KpknRingColors {
    val columna = Color(0xFF8FB2FF)
    val musculos = Color(0xFFF49A6E)
    val energia = Color(0xFFF7CF73)

    /** Tinta cálida de la marca; la pista se pinta con ella al 12 %. */
    val ink = Color(0xFFF2EEE6)

    fun of(channel: RingChannel): Color = when (channel) {
        RingChannel.COLUMNA -> columna
        RingChannel.MUSCULOS -> musculos
        RingChannel.ENERGIA -> energia
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Geometría (unidades del logo)
// ──────────────────────────────────────────────────────────────────────────────────────────────

/** Caja de la torre en unidades del logo: x 12–88 e y 35,5–82. */
private object TowerUnits {
    const val Left = 12f
    const val Top = 35.5f
    const val Width = 76f
    const val Height = 46.5f
    const val CenterX = 50f
}

/** Un anillo del logo: elipse exterior e interior, cada una con su centro vertical (el logo desplaza la interior). */
private class TowerRingShape(
    val channel: RingChannel,
    val outerCy: Float,
    val outerRx: Float,
    val outerRy: Float,
    val innerCy: Float,
    val innerRx: Float,
    val innerRy: Float,
)

/** Los tres anillos del logo, de abajo arriba: Columna, Músculos y Energía. */
private val TowerShapes = listOf(
    TowerRingShape(RingChannel.COLUMNA, outerCy = 70f, outerRx = 38f, outerRy = 12f, innerCy = 68f, innerRx = 33f, innerRy = 8.5f),
    TowerRingShape(RingChannel.MUSCULOS, outerCy = 55f, outerRx = 28f, outerRy = 9f, innerCy = 53.5f, innerRx = 24f, innerRy = 6.5f),
    TowerRingShape(RingChannel.ENERGIA, outerCy = 42f, outerRx = 18f, outerRy = 6.5f, innerCy = 41f, innerRx = 14.5f, innerRy = 4.3f),
)

/** Cortes entre bandas de toque (unidades del logo): el punto medio entre los centros de dos anillos vecinos. */
private const val BandCutTop = 48.5f // (42 + 55) / 2
private const val BandCutBottom = 62.5f // (55 + 70) / 2

/** Escala y centrado de la torre dentro de un área de [width] × [height] (px o dp: da igual, es lineal). */
private class TowerLayout(width: Float, height: Float) {
    val scale: Float = minOf(width / TowerUnits.Width, height / TowerUnits.Height).coerceAtLeast(0f)
    private val left = (width - TowerUnits.Width * scale) / 2f
    private val top = (height - TowerUnits.Height * scale) / 2f

    fun x(unit: Float): Float = left + (unit - TowerUnits.Left) * scale
    fun y(unit: Float): Float = top + (unit - TowerUnits.Top) * scale
}

/** Barrido del arco de progreso: 360 · v/100, con v recortado a 0–100. */
internal fun towerSweepDegrees(percent: Float): Float = 360f * percent.coerceIn(0f, 100f) / 100f

/** Canal que recibe un toque a la altura [unitY] (en unidades del logo). */
internal fun towerChannelAtUnit(unitY: Float): RingChannel = when {
    unitY < BandCutTop -> RingChannel.ENERGIA
    unitY < BandCutBottom -> RingChannel.MUSCULOS
    else -> RingChannel.COLUMNA
}

/** Sin valor, la pista sale con trazo discontinuo. */
internal fun towerTrackIsDashed(percent: Int?): Boolean = percent == null

/** «Columna 72 %, Músculos 64 %, Energía 80 %»; «sin calibrar» donde falte el valor. Solo los canales visibles. */
internal fun towerRingsDescription(values: TowerRingValues, visible: Set<RingChannel>): String =
    RingChannel.entries.filter { it in visible }.joinToString(", ") { channel ->
        val name = channel.spanishName()
        val percent = values[channel]
        if (percent == null) "$name sin calibrar" else "$name ${percent.coerceIn(0, 100)} %"
    }

/** Centro del anillo [channel] (su elipse exterior) en un área de [width] × [height]: ancla del ajuste que se abre al tocarlo. */
internal fun towerRingCenter(channel: RingChannel, width: Float, height: Float): Offset {
    val layout = TowerLayout(width, height)
    val shape = TowerShapes.first { it.channel == channel }
    return Offset(layout.x(TowerUnits.CenterX), layout.y(shape.outerCy))
}

/**
 * Franja vertical del anillo [channel] dentro de la caja de la torre, como fracciones de su altura (0 = borde superior, 1 = inferior):
 * la de su elipse exterior. Con la caja en la proporción de la torre, la hoja del RING la usa para recortar el arte a su contenido (I12).
 */
internal fun towerRingBandFractions(channel: RingChannel): Pair<Float, Float> {
    val shape = TowerShapes.first { it.channel == channel }
    fun fraction(unitY: Float): Float = (unitY - TowerUnits.Top) / TowerUnits.Height
    return fraction(shape.outerCy - shape.outerRy) to fraction(shape.outerCy + shape.outerRy)
}

private fun RingChannel.spanishName(): String = when (this) {
    RingChannel.COLUMNA -> "Columna"
    RingChannel.MUSCULOS -> "Músculos"
    RingChannel.ENERGIA -> "Energía"
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Composable
// ──────────────────────────────────────────────────────────────────────────────────────────────

/** Duración del relleno de cada anillo al entrar y escalón entre anillos (de abajo arriba). */
private const val RevealMillis = 700
private const val RevealStaggerMillis = 120
private const val TotalRevealMillis = 2 * RevealStaggerMillis + RevealMillis

/** Alfa de los anillos que no están resaltados. */
private const val DimAlpha = 0.35f
private const val TrackAlpha = 0.12f
private const val DashedTrackAlpha = 0.40f

/** Punto frontal inferior, en grados (la torre gira en sentido horario desde ahí). */
private const val TowerStartDegrees = 90f

/** Barrido a partir del cual el anillo se dibuja completo (evita un arco de 360° degenerado). */
private const val FullSweepDegrees = 359.9f

/** Mínimo de área táctil con toques, en dp. */
private val MinTouchHeight = 48.dp

/** Estilo visual de la torre de RINGS. */
enum class TowerRingsStyle {
    /** Estilo clásico plano. */
    CLASSIC,
    /**
     * Estilo luminoso (LV1 punto 12.2):
     * - Pista más oscura con profundidad (sombra interior suave).
     * - Arco con degradado a lo largo, más claro en la cabeza.
     * - Resplandor suave detrás del arco (alfa <= 0,25).
     * - Destello sutil periódico que recorre cada arco (~6 s).
     */
    LUMINOUS,
}

/**
 * La torre de tres anillos del logo de KPKN.
 *
 * - [values]: porcentajes 0–100; `null` = sin calibrar.
 * - [from]: valores de partida de la animación (p. ej. antes de un entreno); sin él, los anillos se llenan desde 0.
 * - [visible]: canales que se pintan y se describen (el modo Free solo muestra Energía).
 * - [highlighted]: canal que queda a plena intensidad; los otros, atenuados al 35 %.
 * - [onRingTap]: si existe, cada banda vertical del anillo es un botón; área táctil de al menos 48 dp.
 * - [animate]: `false` (o movimiento reducido del sistema) pinta el valor final sin animación.
 * - [style]: estilo visual (clásico plano o luminoso con profundidad, degradado, resplandor y destello).
 * - [forcedGleamProgress]: anula el tiempo para fijar el destello en un punto (0..1), útil para hojas de render.
 */
@Composable
fun KpknTowerRings(
    values: TowerRingValues,
    modifier: Modifier = Modifier,
    from: TowerRingValues? = null,
    visible: Set<RingChannel> = RingChannel.entries.toSet(),
    highlighted: RingChannel? = null,
    onRingTap: ((RingChannel) -> Unit)? = null,
    animate: Boolean = true,
    style: TowerRingsStyle = TowerRingsStyle.CLASSIC,
    forcedGleamProgress: Float? = null,
) {
    val reduced = wizardReducedMotion()
    val animated = animate && !reduced
    // Tras la primera composición los anillos pasan de `from` (o 0) a `values`; `revealing` da el escalón de entrada.
    var started by remember { mutableStateOf(!animated) }
    var revealing by remember { mutableStateOf(animated) }
    LaunchedEffect(Unit) {
        started = true
        delay(TotalRevealMillis.toLong())
        revealing = false
    }
    val anims: List<State<Float>> = RingChannel.entries.map { channel ->
        val target = if (started || !animated) values[channel] ?: 0 else from?.get(channel) ?: 0
        animateFloatAsState(
            targetValue = target.toFloat(),
            animationSpec = if (!animated) snap<Float>() else tween<Float>(
                durationMillis = RevealMillis,
                delayMillis = if (revealing) channel.ordinal * RevealStaggerMillis else 0,
                easing = FastOutSlowInEasing,
            ),
            label = "kpknTowerRing",
        )
    }

    val gleamTransition = rememberInfiniteTransition(label = "kpknRingGleam")
    val gleamRaw = if (style == TowerRingsStyle.LUMINOUS && animated && forcedGleamProgress == null) {
        gleamTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "gleamTimer",
        )
    } else null

    val gleamProgressForChannel: (RingChannel) -> Float = { channel ->
        if (forcedGleamProgress != null) {
            forcedGleamProgress
        } else if (gleamRaw != null) {
            val ringOffset = channel.ordinal * 600f
            val localTime = (gleamRaw.value - ringOffset).mod(6000f)
            if (localTime in 0f..1400f) localTime / 1400f else -1f
        } else {
            -1f
        }
    }

    val paths = remember { TowerPaths() }
    val description = towerRingsDescription(values, visible)
    Box(
        (if (onRingTap != null) modifier.heightIn(min = MinTouchHeight) else modifier)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawTowerRings(values, anims, visible, highlighted, paths, style, gleamProgressForChannel)
        }
        if (onRingTap != null) TowerRingZones(values, visible, onRingTap)
    }
}

/** Trayectorias reutilizadas entre cuadros (sin asignar un Path por cada dibujo). */
private class TowerPaths {
    val track = Path()
    val arc = Path()
}

private fun DrawScope.drawTowerRings(
    values: TowerRingValues,
    anims: List<State<Float>>,
    visible: Set<RingChannel>,
    highlighted: RingChannel?,
    paths: TowerPaths,
    style: TowerRingsStyle = TowerRingsStyle.CLASSIC,
    gleamProgressForChannel: (RingChannel) -> Float = { -1f },
) {
    val layout = TowerLayout(size.width, size.height)
    // Primero todas las pistas: anillo completo con el grosor del anillo; sin valor, trazo discontinuo.
    for (shape in TowerShapes) {
        val channel = shape.channel
        if (channel !in visible) continue
        val alpha = ringAlpha(channel, highlighted)
        paths.track.setRingTrack(shape, layout)
        if (towerTrackIsDashed(values[channel])) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            drawPath(
                paths.track,
                KpknRingColors.ink.copy(alpha = DashedTrackAlpha * alpha),
                style = Stroke(width = 1.dp.toPx(), pathEffect = dash),
            )
        } else {
            if (style == TowerRingsStyle.LUMINOUS) {
                // Pista un poco más oscura con profundidad: fondo oscuro + sombra interior suave
                drawPath(paths.track, Color.Black.copy(alpha = 0.50f * alpha))
                drawPath(paths.track, KpknRingColors.ink.copy(alpha = 0.06f * alpha))
                drawPath(
                    paths.track,
                    Color.Black.copy(alpha = 0.55f * alpha),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            } else {
                drawPath(paths.track, KpknRingColors.ink.copy(alpha = TrackAlpha * alpha))
            }
        }
    }
    // Después los arcos, de abajo arriba: el anillo de arriba queda por delante, como en el logo.
    for (shape in TowerShapes) {
        val channel = shape.channel
        if (channel !in visible || values[channel] == null) continue
        val sweep = towerSweepDegrees(anims[channel.ordinal].value)
        if (sweep <= 0f) continue
        val alpha = ringAlpha(channel, highlighted)
        val baseColor = KpknRingColors.of(channel).copy(alpha = alpha)

        if (style == TowerRingsStyle.LUMINOUS) {
            // 1. Resplandor muy suave detrás del arco (alfa <= 0,25), sin bordes brillantes
            val glowWidth = layout.scale * (shape.outerRy - shape.innerRy) + 5.dp.toPx()
            paths.arc.setRingSector(shape, layout, sweep)
            drawPath(
                paths.arc,
                color = baseColor.copy(alpha = 0.20f * alpha),
                style = Stroke(width = glowWidth),
            )

            // 2. Arco con degradado a lo largo, más claro en la cabeza
            val headColor = Color(
                red = (baseColor.red * 0.70f + 0.30f).coerceIn(0f, 1f),
                green = (baseColor.green * 0.70f + 0.30f).coerceIn(0f, 1f),
                blue = (baseColor.blue * 0.70f + 0.30f).coerceIn(0f, 1f),
                alpha = baseColor.alpha,
            )
            val rMidX = (shape.outerRx + shape.innerRx) / 2f
            val rMidY = (shape.outerRy + shape.innerRy) / 2f
            val cY = (shape.outerCy + shape.innerCy) / 2f
            val radStart = Math.toRadians(TowerStartDegrees.toDouble())
            val radHead = Math.toRadians((TowerStartDegrees + sweep).toDouble())
            val startX = layout.x(TowerUnits.CenterX + rMidX * cos(radStart).toFloat())
            val startY = layout.y(cY + rMidY * sin(radStart).toFloat())
            val headX = layout.x(TowerUnits.CenterX + rMidX * cos(radHead).toFloat())
            val headY = layout.y(cY + rMidY * sin(radHead).toFloat())

            val brush = Brush.linearGradient(
                colors = listOf(baseColor.copy(alpha = 0.85f * alpha), headColor),
                start = Offset(startX, startY),
                end = Offset(headX, headY),
            )

            if (sweep >= FullSweepDegrees) {
                paths.track.setRingTrack(shape, layout)
                drawPath(paths.track, brush = brush)
            } else {
                paths.arc.setRingSector(shape, layout, sweep)
                drawPath(paths.arc, brush = brush)
            }

            // 3. Destello: recorre cada arco al aparecer y después cada ~6 s
            val gleamProg = gleamProgressForChannel(channel)
            if (gleamProg in 0f..1f) {
                val gleamSpan = minOf(30f, sweep * 0.35f)
                val gleamCenterAngle = TowerStartDegrees + gleamProg * sweep
                val gleamStartAngle = (gleamCenterAngle - gleamSpan / 2f).coerceAtLeast(TowerStartDegrees)
                val actualSpan = (gleamCenterAngle + gleamSpan / 2f).coerceAtMost(TowerStartDegrees + sweep) - gleamStartAngle
                if (actualSpan > 0f) {
                    paths.arc.reset()
                    paths.arc.arcTo(layout.outerRect(shape), gleamStartAngle, actualSpan, true)
                    paths.arc.arcTo(layout.innerRect(shape), gleamStartAngle + actualSpan, -actualSpan, false)
                    paths.arc.close()
                    drawPath(paths.arc, Color.White.copy(alpha = 0.28f * alpha))
                }
            }
        } else {
            if (sweep >= FullSweepDegrees) {
                paths.track.setRingTrack(shape, layout)
                drawPath(paths.track, baseColor)
            } else {
                paths.arc.setRingSector(shape, layout, sweep)
                drawPath(paths.arc, baseColor)
            }
        }
    }
}

private fun ringAlpha(channel: RingChannel, highlighted: RingChannel?): Float =
    if (highlighted == null || highlighted == channel) 1f else DimAlpha

private fun TowerLayout.outerRect(shape: TowerRingShape): Rect = Rect(
    left = x(TowerUnits.CenterX - shape.outerRx),
    top = y(shape.outerCy - shape.outerRy),
    right = x(TowerUnits.CenterX + shape.outerRx),
    bottom = y(shape.outerCy + shape.outerRy),
)

private fun TowerLayout.innerRect(shape: TowerRingShape): Rect = Rect(
    left = x(TowerUnits.CenterX - shape.innerRx),
    top = y(shape.innerCy - shape.innerRy),
    right = x(TowerUnits.CenterX + shape.innerRx),
    bottom = y(shape.innerCy + shape.innerRy),
)

/** Banda completa del anillo: elipse exterior menos la interior (regla par-impar). */
private fun Path.setRingTrack(shape: TowerRingShape, layout: TowerLayout) {
    reset()
    fillType = PathFillType.EvenOdd
    addOval(layout.outerRect(shape))
    addOval(layout.innerRect(shape))
}

/** Sector de la banda de [sweep] grados desde el punto frontal inferior, en sentido horario. */
private fun Path.setRingSector(shape: TowerRingShape, layout: TowerLayout, sweep: Float) {
    reset()
    arcTo(layout.outerRect(shape), TowerStartDegrees, sweep, true)
    arcTo(layout.innerRect(shape), TowerStartDegrees + sweep, -sweep, false)
    close()
}

/** Tres bandas verticales (Energía arriba, Músculos en medio, Columna abajo), cada una un botón si el canal es visible. */
@Composable
private fun TowerRingZones(
    values: TowerRingValues,
    visible: Set<RingChannel>,
    onRingTap: (RingChannel) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = maxHeight.value
        val layout = TowerLayout(maxWidth.value, total)
        val cutTop = layout.y(BandCutTop).coerceIn(0f, total)
        val cutBottom = layout.y(BandCutBottom).coerceIn(cutTop, total)
        Column(Modifier.fillMaxSize()) {
            TowerZone(RingChannel.ENERGIA, values, visible, onRingTap, Modifier.fillMaxWidth().height(cutTop.dp))
            TowerZone(RingChannel.MUSCULOS, values, visible, onRingTap, Modifier.fillMaxWidth().height((cutBottom - cutTop).dp))
            TowerZone(RingChannel.COLUMNA, values, visible, onRingTap, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun TowerZone(
    channel: RingChannel,
    values: TowerRingValues,
    visible: Set<RingChannel>,
    onRingTap: (RingChannel) -> Unit,
    modifier: Modifier,
) {
    if (channel !in visible) {
        Box(modifier)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onRingTap(channel)
            }
            .semantics { contentDescription = towerRingsDescription(values, setOf(channel)) },
    )
}
