package com.example.kpkn.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.*
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.domain.onboarding.RingsCoverageSource

// ─── Ring Constants ──────────────────────────────────────────────────────────

/** Colores de los anillos en el orden de las etiquetas (Músculos, Energía, Columna): los de marca de la torre. */
internal val RingColors = listOf(
    KpknRingColors.musculos,
    KpknRingColors.energia,
    KpknRingColors.columna,
)
private val RingLabels = listOf("Músculos", "Energía", "Columna")

@Composable
fun HomeRingsSection(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    hasActiveProgram: Boolean = true,
    isLoading: Boolean = false,
    dashboard: RecoveryDashboard? = null,
    dataLabel: String? = null,
    snapshot: AugeSnapshot? = null,
    /** Cobertura por canal explícita; sin ella manda `snapshot.coverage` (publicada por el productor AugeViewModel) y sin ninguna → «sin datos». */
    coverage: RingsCoverage? = null,
    showModelUpdateNotice: Boolean = false,
    onDismissAdvisory: (String) -> Unit = {},
    onModelNoticeShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val progressValues = remember(muscularProgress, sncProgress, columnaProgress, isLoading) {
        if (isLoading) listOf(0f, 0f, 0f)
        else listOf(muscularProgress, sncProgress, columnaProgress)
    }
    // Entero de cada batería (Músculos, Energía, Columna) sin pasar por el Float del progreso; sin snapshot, ringPercent redondea el Float.
    val batteryScores = if (isLoading || snapshot == null) {
        emptyList<Int>()
    } else {
        listOf(RecoveryChannelId.MUSCULAR, RecoveryChannelId.SYSTEM, RecoveryChannelId.STRUCTURE).map { snapshot.ringScore(it) }
    }

    // «Sin calibrar» (sin historial, sin check-in y sin evidencia) no se renderiza
    // como un 100 % afirmativo: se marca «sin datos» en valor y accesibilidad.
    // Cobertura POR CANAL publicada por el productor del snapshot (AugeViewModel,
    // mismas entradas y corte temporal que las baterías). Esta sección no hace IO
    // ni usa etiquetas globales: sin snapshot todavía → «sin datos», nunca una
    // afirmación.
    val ringsCoverage = coverage ?: snapshot?.coverage ?: RingsCoverage.NO_DATA

    val ringColors = remember(hasActiveProgram, isLoading) {
        when {
            isLoading -> listOf(Color(0xFF444444), Color(0xFF555555), Color(0xFF666666))
            hasActiveProgram -> RingColors
            else -> listOf(Color(0xFF666666), Color(0xFF888888), Color(0xFFAAAAAA))
        }
    }

    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<RecoveryChannelId?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
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
        dashboard?.headline?.takeIf { it.isNotBlank() && !isLoading }?.let { headline ->
            Text(
                text = headline,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        dashboard?.takeIf { !isLoading }?.let { board ->
            val causes = board.channels.flatMap { it.causes }.distinct().take(2).joinToString(" · ")
            val coverageLine = buildString {
                append("Cobertura de datos: ")
                append(board.confidenceLabel)
                (dataLabel ?: board.dataLabel)?.let { append(" · ").append(it) }
                if (causes.isNotBlank()) append(" · ").append(causes)
            }
            Text(
                text = coverageLine,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CombinedRingsView(
            progressValues = progressValues,
            ringColors = ringColors,
            hasActiveProgram = hasActiveProgram,
            isLoading = isLoading,
            coverage = ringsCoverage,
            batteryScores = batteryScores,
            onChannelClick = { selectedChannel = it },
        )
        Spacer(Modifier.height(4.dp))
    }

    if (showInfoDialog) {
        RingsInfoDialog(onDismiss = { showInfoDialog = false })
    }
    val channel = selectedChannel
    if (channel != null && snapshot != null) {
        RingDetailSheet(
            channel = channel,
            snapshot = snapshot,
            showModelUpdateNotice = showModelUpdateNotice,
            onDismiss = { selectedChannel = null },
            onDismissAdvisory = onDismissAdvisory,
            onModelNoticeShown = onModelNoticeShown,
        )
    }
}

@Composable
private fun CombinedRingsView(
    progressValues: List<Float>,
    ringColors: List<Color>,
    hasActiveProgram: Boolean = true,
    isLoading: Boolean = false,
    coverage: RingsCoverage? = null,
    /** Batería entera de cada anillo (mismo orden que [progressValues]); vacía si no hay, y entonces se redondea el progreso. */
    batteryScores: List<Int> = emptyList(),
    onChannelClick: (RecoveryChannelId) -> Unit = {},
) {
    val channelIds = listOf(
        RecoveryChannelId.MUSCULAR,
        RecoveryChannelId.SYSTEM,
        RecoveryChannelId.STRUCTURE,
    )
    // Un solo entero por anillo: la torre, el texto, el color de banda y la accesibilidad leen el mismo valor.
    val percents = progressValues.mapIndexed { i, progress -> ringPercent(batteryScores.getOrNull(i), progress) }
    // Un canal sin datos no tiene valor: la torre lo muestra sin calibrar (pista discontinua), sin afirmar un 100 %.
    fun towerPercent(i: Int): Int? = when {
        isLoading -> 0
        coverage?.channel(channelIds[i])?.hasData == false -> null
        else -> percents[i]
    }
    val towerValues = TowerRingValues(
        columna = towerPercent(2),
        musculos = towerPercent(0),
        energia = towerPercent(1),
    )
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
                        .tourAnchor(TourAnchors.HOME_RINGS)
                        // Una sola lectura de los RINGS: la descripción del contenedor sustituye a la de la torre y sus bandas (TalkBack no las repite).
                        .clearAndSetSemantics {
                            val desc = if (isLoading) {
                                "Calculando recuperación"
                            } else {
                                "Rings: " + channelIds.mapIndexed { i, id ->
                                    val channel = coverage?.channel(id)
                                    val label = RingLabels[i]
                                    val scoreText = "${percents[i]}%"
                                    when {
                                        channel != null && !channel.hasData -> "$label sin datos"
                                        channel != null && channel.isEstimated -> "$label $scoreText (${channel.label.lowercase()})"
                                        else -> "$label $scoreText"
                                    }
                                }.joinToString(", ")
                            }
                            contentDescription = desc
                            stateDescription = desc
                        },
                ) {
                    KpknTowerRings(
                        values = towerValues,
                        modifier = Modifier.fillMaxSize().alpha(if (hasActiveProgram || isLoading) 1f else 0.5f),
                        onRingTap = if (isLoading) null else { channel ->
                            onChannelClick(
                                when (channel) {
                                    RingChannel.MUSCULOS -> RecoveryChannelId.MUSCULAR
                                    RingChannel.ENERGIA -> RecoveryChannelId.SYSTEM
                                    RingChannel.COLUMNA -> RecoveryChannelId.STRUCTURE
                                },
                            )
                        },
                    )
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
                percents.forEachIndexed { i, score ->
                    val channel = coverage?.channel(channelIds[i])
                    val noData = !isLoading && channel != null && !channel.hasData
                    val sourceChip = when (channel?.source) {
                        RingsCoverageSource.SUBJECTIVE_SENSATION -> "Estimación subjetiva"
                        RingsCoverageSource.INITIAL_ESTIMATE -> "Estimación inicial"
                        else -> null
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onChannelClick(channelIds[i]) },
                    ) {
                        Text(
                            RingLabels[i].uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ringColors[i],
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            // Sin datos no se afirma un porcentaje (ni 100 ni 0).
                            if (isLoading) "…" else if (noData) "Sin datos" else "$score%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                        )
                        if (!isLoading) {
                            if (noData) {
                                Text(
                                    "Sin calibrar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                val chipColor = if (sourceChip != null) ringColors[i] else Color(RecoveryBands.colorArgb(score).toInt())
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = chipColor.copy(alpha = 0.18f),
                                ) {
                                    Text(
                                        sourceChip ?: RecoveryBands.labelForScore(score),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chipColor,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
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
/**
 * Entero (0..100) que muestra un anillo: con la batería entera a mano se usa tal cual; si solo hay el progreso Float se redondea
 * (0,53f · 100 = 52,999996 daba 52 al truncar). Torre, texto, color de banda y accesibilidad leen este mismo entero.
 */
internal fun ringPercent(batteryInt: Int?, progress: Float): Int =
    (batteryInt ?: if (progress.isNaN()) 0 else (progress * 100f).roundToInt()).coerceIn(0, 100)

internal fun batteryColor(score: Int): Color = Color(RecoveryBands.colorArgb(score).toInt())

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
                    color = KpknRingColors.musculos,
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
                    color = KpknRingColors.energia,
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
                    color = KpknRingColors.columna,
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
                    "Se recalculan con lo que entrenas (y un decaimiento en el tiempo). Puedes corregir cómo te sientes en el sheet de estado antes de entrenar y, si hace falta, al cerrar la sesión. Si no tocas los rings, el modelo igual puede ajustar los tiempos de recuperación cuando rindes distinto a lo que el anillo predijo (mismo ejercicio, esfuerzo parecido). Si tocas un anillo, esa corrección manda ese día.",
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
