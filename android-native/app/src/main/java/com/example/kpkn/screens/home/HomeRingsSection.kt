package com.example.kpkn.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.screens.home.design.HomeGlyph
import com.example.kpkn.screens.home.design.HomeGlyphButton
import com.example.kpkn.screens.home.design.HomeRingRow
import com.example.kpkn.screens.home.design.HomeSectionLabel
import com.example.kpkn.screens.home.design.RingTowerAspect
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.rings.RingsIntroOverlay
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import kotlin.math.*

// ─── Ring Constants ──────────────────────────────────────────────────────────

/** Colores de los anillos en el orden de las etiquetas (Músculos, Energía, Columna): los de marca de la torre. */
internal val RingColors = listOf(
    KpknRingColors.musculos,
    KpknRingColors.energia,
    KpknRingColors.columna,
)

/** Filas de RINGS en Home, de arriba abajo: el orden de la torre (Energía, Músculos y Columna). */
private val RingRowOrder = listOf(
    RecoveryChannelId.SYSTEM,
    RecoveryChannelId.MUSCULAR,
    RecoveryChannelId.STRUCTURE,
)

/**
 * «Tus RINGS» de Home: el titular del motor; a la izquierda, la torre (46 % del ancho) y, a la derecha, una fila por canal con su
 * cifra, su banda o su procedencia. Tocar la torre o una fila abre el detalle del canal: el canal abierto ([selectedChannel]) lo
 * guarda HomeScreen. La (i) abre la introducción de RINGS en repaso.
 */
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
    /** Canal con el detalle abierto; HomeScreen lo guarda por nombre para sobrevivir a girar la pantalla. */
    selectedChannel: RecoveryChannelId? = null,
    onSelectedChannelChange: (RecoveryChannelId?) -> Unit = {},
    /** Músculos del motor: el detalle de Músculos los lista uno a uno. */
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    /** Guarda el ajuste manual de un canal (HomeScreen lo cablea con AugeViewModel). */
    onSaveAdjustment: (RecoveryChannelId, Int) -> Unit = { _, _ -> },
    onDismissAdvisory: (String) -> Unit = {},
    onModelNoticeShown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Sin ninguna cobertura, «sin datos»: la sección nunca afirma un porcentaje que no tenga procedencia.
    val ringsCoverage = coverage ?: snapshot?.coverage ?: RingsCoverage.NO_DATA
    var showIntro by remember { mutableStateOf(false) }

    // Puntuación del canal (0–100). Sin dato, null: la torre lo deja sin arco y la fila dice «—». Cargando, 0.
    fun scoreOf(channel: RecoveryChannelId): Int? {
        if (isLoading) return 0
        if (!ringsCoverage.channel(channel).hasData) return null
        val progress = when (channel) {
            RecoveryChannelId.MUSCULAR -> muscularProgress
            RecoveryChannelId.SYSTEM -> sncProgress
            RecoveryChannelId.STRUCTURE -> columnaProgress
        }
        return (progress * 100).toInt().coerceIn(0, 100)
    }
    val towerValues = TowerRingValues(
        columna = scoreOf(RecoveryChannelId.STRUCTURE),
        musculos = scoreOf(RecoveryChannelId.MUSCULAR),
        energia = scoreOf(RecoveryChannelId.SYSTEM),
    )
    // Una sola lectura de los RINGS para TalkBack, en el orden de la torre.
    val towerDescription = if (isLoading) {
        "Calculando recuperación"
    } else {
        "Rings: " + RingRowOrder.joinToString(", ") { channel ->
            val name = RecoveryBands.channelTitle(channel)
            val score = scoreOf(channel)
            val cov = ringsCoverage.channel(channel)
            when {
                score == null -> "$name sin datos"
                cov.isEstimated -> "$name $score % (${cov.label.lowercase()})"
                else -> "$name $score %"
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionLabel(
            text = "TUS RINGS",
            action = {
                HomeGlyphButton(
                    glyph = HomeGlyph.INFO,
                    description = "Qué son los RINGS",
                    onClick = { showIntro = true },
                    modifier = Modifier.offset(x = 12.dp).testTag("home-rings-info"),
                )
            },
        )
        dashboard?.headline?.takeIf { it.isNotBlank() && !isLoading }?.let { headline ->
            Text(
                text = headline,
                modifier = Modifier.padding(start = WizardSpacing.gutter, end = WizardSpacing.gutter, top = 12.dp),
                style = WizardTypography.questionCompact,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WizardSpacing.gutter)
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .aspectRatio(RingTowerAspect)
                    .tourAnchor(TourAnchors.HOME_RINGS)
                    // Una sola lectura de los RINGS: la descripción del contenedor sustituye a la de la torre y sus bandas.
                    .clearAndSetSemantics {
                        contentDescription = towerDescription
                        stateDescription = towerDescription
                    },
            ) {
                KpknTowerRings(
                    values = towerValues,
                    modifier = Modifier.fillMaxSize().alpha(if (hasActiveProgram || isLoading) 1f else 0.5f),
                    onRingTap = if (isLoading) null else { ring -> onSelectedChannelChange(ring.recoveryChannel()) },
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RingRowOrder.forEach { channel ->
                    val score = scoreOf(channel)
                    val cov = ringsCoverage.channel(channel)
                    val secondary = when {
                        isLoading -> null
                        score == null -> "Sin calibrar"
                        cov.isEstimated -> cov.label
                        else -> RecoveryBands.labelForScore(score)
                    }
                    HomeRingRow(
                        channel = channel,
                        name = RecoveryBands.channelTitle(channel),
                        score = score,
                        secondary = secondary,
                        loading = isLoading,
                        onClick = { onSelectedChannelChange(channel) },
                    )
                }
            }
        }
        if (dashboard != null && !isLoading) {
            Text(
                text = "Confianza ${dashboard.confidenceLabel} · toca un anillo para ver por qué",
                modifier = Modifier.padding(start = WizardSpacing.gutter, end = WizardSpacing.gutter, top = 14.dp),
                style = WizardTypography.note,
                color = WizardColors.textFaint,
            )
        }
    }

    // (i): la introducción de RINGS (K6) en modo repaso. No escribe nada; «Entendido» la cierra.
    if (showIntro) {
        RingsIntroOverlay(
            onContinue = { showIntro = false },
            onBack = { showIntro = false },
            replay = true,
        )
    }
    val channel = selectedChannel
    if (channel != null && snapshot != null) {
        RingDetailSheet(
            channel = channel,
            snapshot = snapshot,
            showModelUpdateNotice = showModelUpdateNotice,
            onDismiss = { onSelectedChannelChange(null) },
            onDismissAdvisory = onDismissAdvisory,
            onModelNoticeShown = onModelNoticeShown,
            coverage = ringsCoverage,
            dataLabel = dataLabel,
            perMuscle = perMuscle,
            onSaveAdjustment = onSaveAdjustment,
        )
    }
}

/** Canal de RINGS de la torre (su banda de toque) al canal de Home. */
private fun RingChannel.recoveryChannel(): RecoveryChannelId = when (this) {
    RingChannel.MUSCULOS -> RecoveryChannelId.MUSCULAR
    RingChannel.ENERGIA -> RecoveryChannelId.SYSTEM
    RingChannel.COLUMNA -> RecoveryChannelId.STRUCTURE
}

private val AugeRingCoreStroke = 2.dp
internal val AugeRingBloomFar = 8.dp
private val AugeRingBloomNear = 4.5.dp
private val AugeRingHighlight = 1.dp

internal const val AugeRingsRadiusWidthDivisor = 5f
internal const val AugeRingsStaggerDy = 0.48f

/** Host height that keeps radius = width/5 after bloom inset. */
internal fun augeRingsHostHeightDp(contentWidthDp: Float, bloomDp: Float = 8f): Float {
    val radius = (contentWidthDp / AugeRingsRadiusWidthDivisor).coerceAtLeast(1f)
    val verticalExtent = radius * (1f + AugeRingsStaggerDy)
    return 2f * verticalExtent + 2f * bloomDp
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
