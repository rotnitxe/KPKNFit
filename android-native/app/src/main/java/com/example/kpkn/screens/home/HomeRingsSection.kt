package com.example.kpkn.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.*
import com.example.kpkn.ui.components.KpknAlertDialog

// ─── Ring Constants ──────────────────────────────────────────────────────────

internal val RingColors = listOf(
    Color(0xFFC96B5C), // Músculos — terracota
    Color(0xFF4FA3A5), // Energía — turquesa grisáceo
    Color(0xFF9A86C8), // Columna — lavanda apagado
)
private val RingLabels = listOf("Músculos", "Energía", "Columna")

@Composable
fun HomeRingsSection(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    hasActiveProgram: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progressValues = remember(muscularProgress, sncProgress, columnaProgress, isLoading) {
        if (isLoading) listOf(0f, 0f, 0f)
        else listOf(muscularProgress, sncProgress, columnaProgress)
    }

    val ringColors = remember(hasActiveProgram, isLoading) {
        when {
            isLoading -> listOf(Color(0xFF444444), Color(0xFF555555), Color(0xFF666666))
            hasActiveProgram -> RingColors
            else -> listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
        }
    }

    var showInfoDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MIS RINGS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                onClick = { showInfoDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "¿Qué es esto?",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        CombinedRingsView(progressValues, ringColors, hasActiveProgram, isLoading = isLoading)
        Spacer(Modifier.height(4.dp))
    }

    if (showInfoDialog) {
        RingsInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun CombinedRingsView(
    progressValues: List<Float>,
    ringColors: List<Color>,
    hasActiveProgram: Boolean = true,
    isLoading: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val hostHeight = augeRingsHostHeightDp(maxWidth.value).dp
                Box(
                    Modifier
                        .height(hostHeight)
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            val desc = if (isLoading) {
                                "Calculando recuperación"
                            } else {
                                "Rings: Músculos ${(progressValues[0] * 100).toInt()}%, " +
                                    "Energía ${(progressValues[1] * 100).toInt()}%, " +
                                    "Columna ${(progressValues[2] * 100).toInt()}%"
                            }
                            contentDescription = desc
                            stateDescription = desc
                        },
                ) {
                    AugeRingsCanvas(progressValues[0], progressValues[1], progressValues[2], ringColors)
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            // Keep the captions clear of the lowered energy ring.
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                progressValues.forEachIndexed { i, progress ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            RingLabels[i].uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ringColors[i],
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            if (isLoading) "…" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
    }
}

private val AugeRingCoreStroke = 2.dp
internal val AugeRingBloomFar = 8.dp
private val AugeRingBloomNear = 4.5.dp
private val AugeRingHighlight = 1.dp

internal const val AugeRingsRadiusWidthDivisor = 5f
internal const val AugeRingsStaggerDx = 1.45f
internal const val AugeRingsStaggerDy = 0.48f

internal data class AugeRingsLayout(
    val radius: Float,
    val centers: List<Offset>,
)

/**
 * Olympic 3-ring cluster. Radius is width-bound (`width / 5`) so growing the
 * host to fit bloom padding never shrinks the rings. [bloomPx] is inset on
 * every side because the glow stroke is drawn centered on the path.
 */
internal fun augeRingsLayout(width: Float, height: Float, bloomPx: Float): AugeRingsLayout {
    val inset = bloomPx.coerceAtLeast(0f)
    val usableH = (height - 2f * inset).coerceAtLeast(1f)
    val radiusFromWidth = (width / AugeRingsRadiusWidthDivisor).coerceAtLeast(1f)
    val maxRadiusForHeight = usableH / (2f * (1f + AugeRingsStaggerDy))
    val radius = min(radiusFromWidth, maxRadiusForHeight)
    val centerX = width / 2f
    val centerY = height / 2f
    val dx = radius * AugeRingsStaggerDx
    val dy = radius * AugeRingsStaggerDy
    return AugeRingsLayout(
        radius = radius,
        centers = listOf(
            Offset(centerX - dx, centerY - dy),
            Offset(centerX, centerY + dy),
            Offset(centerX + dx, centerY - dy),
        ),
    )
}

/** Host height that keeps radius = width/5 after bloom inset. */
internal fun augeRingsHostHeightDp(contentWidthDp: Float, bloomDp: Float = 8f): Float {
    val radius = (contentWidthDp / AugeRingsRadiusWidthDivisor).coerceAtLeast(1f)
    val verticalExtent = radius * (1f + AugeRingsStaggerDy)
    return 2f * verticalExtent + 2f * bloomDp
}

internal fun augeRingsClusterFits(width: Float, height: Float, bloomPx: Float): Boolean {
    val layout = augeRingsLayout(width, height, bloomPx)
    val halfStroke = bloomPx / 2f
    return layout.centers.all { center ->
        val top = center.y - layout.radius - halfStroke
        val bottom = center.y + layout.radius + halfStroke
        top >= -0.5f && bottom <= height + 0.5f
    }
}

@Composable
internal fun SingleRingCanvas(
    value: Float,
    color: Color,
    ringDiameter: Float = 140f,
    @Suppress("UNUSED_PARAMETER") strokeWidth: Float = 8f,
) {
    val animatedValue by animateFloatAsState(targetValue = value, label = "ringValue")
    Canvas(
        Modifier
            .size(ringDiameter.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val bloomFarPx = AugeRingBloomFar.toPx()
        val r = (size.minDimension - bloomFarPx) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val progress = animatedValue.coerceIn(0f, 1f)
        drawAugeRingTrack(c, r, color)
        drawAugeRingBlooms(c, r, color, progress, BlendMode.Plus)
        drawAugeRingCore(c, r, color, progress)
    }
}

@Composable
internal fun AugeRingsCanvas(mp: Float, sp: Float, cp: Float, ringColors: List<Color> = RingColors) {
    val values = listOf(mp, sp, cp)
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val layout = augeRingsLayout(size.width, size.height, AugeRingBloomFar.toPx())
        val radius = layout.radius
        val rings = layout.centers.mapIndexed { index, center ->
            Triple(center, ringColors[index], values[index].coerceIn(0f, 1f))
        }

        rings.forEach { (center, color, _) ->
            drawAugeRingTrack(center, radius, color)
        }
        rings.forEach { (center, color, progress) ->
            drawAugeRingBlooms(center, radius, color, progress, BlendMode.Plus)
        }
        rings.forEach { (center, color, progress) ->
            drawAugeRingCore(center, radius, color, progress)
        }
    }
}

internal fun DrawScope.drawAugeRingTrack(center: Offset, radius: Float, color: Color) {
    drawCircle(
        color = color.copy(alpha = 0.16f),
        radius = radius,
        center = center,
        style = Stroke(width = AugeRingCoreStroke.toPx()),
        blendMode = BlendMode.SrcOver,
    )
}

internal fun DrawScope.drawAugeRingBlooms(
    center: Offset,
    radius: Float,
    color: Color,
    progress: Float,
    blendMode: BlendMode,
) {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val full = clamped >= 1f
    listOf(AugeRingBloomFar.toPx() to 0.10f, AugeRingBloomNear.toPx() to 0.18f).forEach { (width, alpha) ->
        if (full) {
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = width),
                blendMode = blendMode,
            )
        } else {
            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Butt),
                blendMode = blendMode,
            )
        }
    }
}

internal fun DrawScope.drawAugeRingCore(center: Offset, radius: Float, color: Color, progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val full = clamped >= 1f
    val highlight = lerp(color, Color.White, 0.35f)
    if (full) {
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = AugeRingCoreStroke.toPx()),
            blendMode = BlendMode.SrcOver,
        )
        drawCircle(
            color = highlight,
            radius = radius,
            center = center,
            style = Stroke(width = AugeRingHighlight.toPx()),
            blendMode = BlendMode.SrcOver,
        )
    } else {
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = AugeRingCoreStroke.toPx(), cap = StrokeCap.Round),
            blendMode = BlendMode.SrcOver,
        )
        drawArc(
            color = highlight,
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = AugeRingHighlight.toPx(), cap = StrokeCap.Round),
            blendMode = BlendMode.SrcOver,
        )
    }
}
internal fun batteryColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF22C55E)
    score >= 50 -> Color(0xFFFACC15)
    else        -> Color(0xFFEF4444)
}

// ─── RINGS Info Dialog ─────────────────────────────────────────────────────

@Composable
private fun RingsInfoDialog(onDismiss: () -> Unit) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "¿Qué son los RINGS?",
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Tus RINGS son la representación visual del estado de tu cuerpo, dividido en tres áreas relevantes del entrenamiento:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Text(
                    "1. RING Músculos:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = com.example.kpkn.ui.theme.RingRed,
                )
                Text(
                    "Muestra el promedio del estado de todos tus músculos, puede entenderse de dos formas, qué tan recuperados están o qué tan preparados están para una sesión de entrenamiento. El anillo representa el estado general de todos tus músculos, si deseas corregir el porcentaje porque no representa tu estado real, puedes dirigirte al músculo en específico y corregir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Text(
                    "2. RING Energía:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = com.example.kpkn.ui.theme.RingBlue,
                )
                Text(
                    "Es tu sistema nervioso estructural: la carga neural de entrenar más cómo te sientes. No pedimos horas de sueño; si dormiste mal, eso ya llega en esta sensación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                Text(
                    "3. RING Columna:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = com.example.kpkn.ui.theme.RingYellow,
                )
                Text(
                    "Mezcla la carga axial reciente (sentadilla, peso muerto y similares), el piso articular y el estado de dorsales, erectores/lumbar y trapecio. Si esos músculos están cansados, la columna queda más expuesta. No es una medición clínica.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    "¿Cómo se actualizan los RINGS?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Se recalculan con lo que entrenas (y un decaimiento en el tiempo). Puedes corregir cómo te sientes en el sheet de estado antes de entrenar y, si hace falta, al cerrar la sesión. Si no tocas los rings, no se “aprende” un tiempo de recuperación nuevo: solo se ancla lo que reportas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Entendido",
                    fontWeight = FontWeight.Black,
                )
            }
        }
    )
}
