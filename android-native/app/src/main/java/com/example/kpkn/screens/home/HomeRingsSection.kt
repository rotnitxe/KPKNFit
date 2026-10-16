package com.example.kpkn.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.screens.home.design.HomeModuleMargin
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.home.design.homeAccent
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.rings.RingsIntroOverlay
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.HomeLivingRingsOverlay
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.LivingCharacterPose
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import kotlinx.coroutines.launch
import java.util.Locale

// ─── Ring Constants ──────────────────────────────────────────────────────────

/** Colores de los anillos en el orden de las etiquetas (Músculos, Energía, Columna): los de marca de la torre. */
internal val RingColors = listOf(
    KpknRingColors.musculos,
    KpknRingColors.energia,
    KpknRingColors.columna,
)

/** Filas / columnas de RINGS en Home: orden de la leyenda I1 (Músculos, Energía, Columna). */
private val LegendChannels = listOf(
    RecoveryChannelId.MUSCULAR,
    RecoveryChannelId.SYSTEM,
    RecoveryChannelId.STRUCTURE,
)

/**
 * «Tus RINGS» de Inicio vivo (I1): la gran torre central con proporción 300:230, brillos orbitales y
 * el personaje saltarín. Debajo, la leyenda de tres columnas (Músculos, Energía y Columna) con porcentaje
 * y estado, tocable para saltar al anillo (0,55 s) y abrir el detalle del canal.
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
    /** Parámetros de animación y pruebas/hojas de render */
    animatingOverride: Boolean? = null,
    forcedPose: LivingCharacterPose? = null,
    forcedOrbitTimeMillis: Float? = null,
    jumpDurationMillis: Int = 550,
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

    // Conjunto de canales calibrados para el planificador de saltos y órbitas
    val calibratedChannels = remember(ringsCoverage, isLoading) {
        if (isLoading) emptySet()
        else buildSet {
            if (ringsCoverage.channel(RecoveryChannelId.STRUCTURE).hasData) add(RingChannel.COLUMNA)
            if (ringsCoverage.channel(RecoveryChannelId.MUSCULAR).hasData) add(RingChannel.MUSCULOS)
            if (ringsCoverage.channel(RecoveryChannelId.SYSTEM).hasData) add(RingChannel.ENERGIA)
        }
    }

    // Una sola lectura de los RINGS para TalkBack, en el orden de la leyenda I1.
    val towerDescription = if (isLoading) {
        "Calculando recuperación"
    } else {
        "Rings: " + LegendChannels.joinToString(", ") { channel ->
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

    // Detección de ciclo de vida y visibilidad en pantalla para detener bucles
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isLifecycleStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val reducedMotion = wizardReducedMotion()
    var isVisibleOnScreen by remember { mutableStateOf(true) }

    val isAnimating = animatingOverride ?: (!reducedMotion && isLifecycleStarted && isVisibleOnScreen)

    // Salto directo al pulsar un anillo o columna de la leyenda (0,55 s antes de abrir el detalle)
    val coroutineScope = rememberCoroutineScope()
    var jumpTargetChannel by remember { mutableStateOf<RingChannel?>(null) }
    val jumpAnim = remember { Animatable(0f) }
    val jumpProgress = jumpTargetChannel?.let { jumpAnim.value }

    fun handleChannelClick(channel: RecoveryChannelId) {
        if (reducedMotion || jumpDurationMillis <= 0) {
            onSelectedChannelChange(channel)
        } else {
            coroutineScope.launch {
                jumpTargetChannel = channel.ringChannel()
                jumpAnim.snapTo(0f)
                jumpAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = jumpDurationMillis,
                        easing = CubicBezierEasing(0.3f, 0f, 0.6f, 1f),
                    ),
                )
                onSelectedChannelChange(channel)
                jumpTargetChannel = null
            }
        }
    }

    KpknModule(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HomeModuleMargin)
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    val bounds = coordinates.boundsInWindow()
                    isVisibleOnScreen = bounds.bottom > 0 && bounds.top < 4000f
                }
            },
    ) {
        KpknModuleHeader(
            label = "Tus RINGS",
            action = {
                KpknIconButton(
                    icon = KpknLineIcons.Info,
                    contentDescription = "Qué son los RINGS",
                    onClick = { showIntro = true },
                    modifier = Modifier.testTag("home-rings-info"),
                )
            },
        )
        dashboard?.headline?.takeIf { it.isNotBlank() && !isLoading }?.let { headline ->
            Text(
                text = headline,
                modifier = Modifier.padding(top = 6.dp),
                style = HomeTypography.ringsHeadline,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Héroe I1: torre central con proporción 300:230 y capa viva
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .aspectRatio(300f / 230f)
                .tourAnchor(TourAnchors.HOME_RINGS)
                .clearAndSetSemantics {
                    contentDescription = towerDescription
                    stateDescription = towerDescription
                },
        ) {
            KpknTowerRings(
                values = towerValues,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (hasActiveProgram || isLoading) 1f else 0.5f),
                onRingTap = if (isLoading) null else { ring -> handleChannelClick(ring.recoveryChannel()) },
            )
            HomeLivingRingsOverlay(
                calibratedChannels = calibratedChannels,
                isAnimating = isAnimating,
                jumpProgress = jumpProgress,
                targetChannel = jumpTargetChannel,
                forcedPose = forcedPose,
                forcedTimeMillis = forcedOrbitTimeMillis,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Leyenda I1: 3 columnas de izquierda a derecha (MÚSCULOS, ENERGÍA, COLUMNA)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LegendChannels.forEach { channel ->
                val score = scoreOf(channel)
                val cov = ringsCoverage.channel(channel)
                val secondary = when {
                    isLoading -> null
                    score == null -> "Sin calibrar"
                    cov.isEstimated -> cov.label
                    else -> RecoveryBands.labelForScore(score)
                }
                val name = RecoveryBands.channelTitle(channel)
                val figure = when {
                    isLoading -> "…"
                    score == null -> "—"
                    else -> "$score %"
                }
                val spoken = when {
                    isLoading -> "calculando recuperación"
                    score == null -> "sin datos"
                    else -> "$score %"
                }
                val description = listOfNotNull(name, spoken, secondary).joinToString(", ")
                val accent = channel.homeAccent()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("home-rings-row-${channel.name}")
                        .clickable(
                            enabled = !isLoading,
                            role = Role.Button,
                            onClick = { handleChannelClick(channel) },
                        )
                        .semantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = name.uppercase(Locale("es")),
                        style = KpknType.eyebrow,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = figure,
                        style = WizardTypography.measure.copy(fontSize = 20.sp, lineHeight = 24.sp),
                        color = WizardColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (secondary != null) {
                        Text(
                            text = secondary,
                            style = KpknType.caption,
                            color = WizardColors.textFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (dashboard != null && !isLoading) {
            Text(
                // «por qué» va sin corte: no deja «qué» huérfano en la segunda línea.
                text = "Confianza ${dashboard.confidenceLabel.lowercase()} · Toca un anillo para ver por qué",
                modifier = Modifier.padding(top = 14.dp),
                style = KpknType.caption,
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

/** Canal de Home al canal de la torre. */
private fun RecoveryChannelId.ringChannel(): RingChannel = when (this) {
    RecoveryChannelId.MUSCULAR -> RingChannel.MUSCULOS
    RecoveryChannelId.SYSTEM -> RingChannel.ENERGIA
    RecoveryChannelId.STRUCTURE -> RingChannel.COLUMNA
}

internal const val AugeRingsRadiusWidthDivisor = 5f
internal const val AugeRingsStaggerDy = 0.48f

/** Host height that keeps radius = width/5 after bloom inset. */
internal fun augeRingsHostHeightDp(contentWidthDp: Float, bloomDp: Float = 8f): Float {
    val radius = (contentWidthDp / AugeRingsRadiusWidthDivisor).coerceAtLeast(1f)
    val verticalExtent = radius * (1f + AugeRingsStaggerDy)
    return 2f * verticalExtent + 2f * bloomDp
}
