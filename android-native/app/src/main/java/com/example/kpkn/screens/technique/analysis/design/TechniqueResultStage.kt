package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.analysis.ANALYSIS_REVIEW_ORIGINAL_TAG
import com.example.kpkn.screens.technique.analysis.AnalysisResultsUi
import com.example.kpkn.screens.technique.analysis.AnalysisReviewUi
import com.example.kpkn.screens.technique.analysis.ObservationUi
import com.example.kpkn.screens.technique.analysis.RepMetricKind
import com.example.kpkn.screens.technique.analysis.ReviewedAnalysisResult
import com.example.kpkn.screens.technique.analysis.TECHNIQUE_ANALYSIS_NOTICE_TAG
import com.example.kpkn.screens.technique.analysis.TECHNIQUE_ANALYSIS_TRENDS_TAG
import com.example.kpkn.screens.technique.analysis.TECHNIQUE_ANALYSIS_USE_TAG
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisActions
import com.example.kpkn.screens.technique.analysis.TechniqueAnalysisUiState
import com.example.kpkn.screens.technique.analysis.buildLiveHudStatus
import com.example.kpkn.screens.technique.analysis.encodeRepCorrections
import com.example.kpkn.screens.technique.analysis.reviewLabel
import com.example.kpkn.screens.technique.analysis.scrubberSpeedWeights
import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.screens.technique.player.TechniqueTrailSource
import com.example.kpkn.screens.technique.player.design.TECHNIQUE_SPEED_TAG
import com.example.kpkn.screens.technique.player.design.TechniqueOverlayOptions
import com.example.kpkn.screens.technique.player.design.TechniquePlayerControls
import com.example.kpkn.screens.technique.player.design.TechniqueRepChips
import com.example.kpkn.screens.technique.player.design.TechniqueScrubber
import com.example.kpkn.screens.technique.player.design.TechniqueSpeedSelector
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.motion.KpknMotion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TechniqueSheetDetent(val index: Int) { PEEK(0), MED(1), FULL(2) }
enum class TechniqueViewMode(val label: String) {
    DATOS("Con datos"),
    TRAY("Solo trayectoria"),
    LIMPIO("Solo vídeo"),
}

const val TECHNIQUE_RESULT_STAGE_TAG = "technique-result-stage"
const val TECHNIQUE_SHEET_TAG = "technique-sheet"
const val TECHNIQUE_SHEET_HANDLE_TAG = "technique-sheet-handle"
const val TECHNIQUE_CONFIDENCE_TAG = "technique-confidence"
const val TECHNIQUE_CONFIDENCE_CARD_TAG = "technique-confidence-card"
const val TECHNIQUE_MODES_TAG = "technique-modes"
const val TECHNIQUE_MINIREP_TAG = "technique-minirepro"

private val StageBlack = Color(0xFF050507)
private val StageCream = Color(0xFFF2EEE6)
private val StageGreen = Color(0xFF7FE08A)
private val StageAmber = Color(0xFFF7CF73)
private val StageRed = Color(0xFFE05252)

/**
 * Escenario fullscreen Pulido v1 `.tc` para resultados listos:
 * vídeo a pantalla completa con escalado dinámico al minireproductor en `FULL`,
 * isla flotante `.tc-isl`, HUD superior/inferior (`.tc-q` + `.tc-qcard`, 3 modos `.tc-modes`,
 * `.tc-repchip`, `.tc-stat`, `.tc-vtag`, `.tc-obs`, `.tc-speed`) y hoja de vidrio con 3 detents (`PEEK` / `MED` / `FULL`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechniqueResultStage(
    state: TechniqueAnalysisUiState,
    results: AnalysisResultsUi,
    player: TechniquePlayerState,
    plateFrame: androidx.compose.ui.graphics.ImageBitmap?,
    actions: TechniqueAnalysisActions,
    videoSlot: @Composable (Modifier, TechniqueOverlayOptions) -> Unit,
    review: AnalysisReviewUi?,
    diameter: Int,
    onSelectDiameter: (Int) -> Unit,
    onPickOtherDiameter: () -> Unit,
    onOpenCalibration: () -> Unit,
    chosenMetricName: String,
    onSelectMetric: (RepMetricKind) -> Unit,
    corrections: Map<Int, RepKind>,
    onUpdateCorrections: (String) -> Unit,
    trailLayer: Boolean,
    bodyLayer: Boolean,
    onToggleTrail: () -> Unit,
    onToggleBody: () -> Unit,
    onOpenMenu: () -> Unit,
    onMenuAnchorChanged: (Rect) -> Unit,
    showMenu: Boolean,
    onOpenHelp: () -> Unit,
    scroll: ScrollState,
    hazeState: HazeState,
    viewportHeight: Dp? = null,
    enterReveal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var detent by rememberSaveable { mutableStateOf(TechniqueSheetDetent.MED) }
    var viewMode by rememberSaveable { mutableStateOf(TechniqueViewMode.DATOS) }
    var confidenceOpen by rememberSaveable { mutableStateOf(false) }
    var modeTip by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(modeTip) {
        if (modeTip != null) {
            delay(1400L)
            modeTip = null
        }
    }

    // Coreografía `.tc-enter` al entrar desde `Analizando` (`tc-exito`)
    var entered by remember(enterReveal) { mutableStateOf(!enterReveal || reduced) }
    LaunchedEffect(enterReveal, reduced) {
        if (enterReveal && !reduced) entered = true
    }

    val topSafeDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navSafeDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasFooter = review != null && actions.onUseReviewedResult != null

    val overlayOptions = when (viewMode) {
        TechniqueViewMode.DATOS -> TechniqueOverlayOptions(skeleton = bodyLayer, trail = trailLayer, guide = true)
        TechniqueViewMode.TRAY -> TechniqueOverlayOptions(skeleton = false, trail = true, guide = true)
        TechniqueViewMode.LIMPIO -> TechniqueOverlayOptions(skeleton = false, trail = false, guide = false)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StageBlack)
            .testTag(TECHNIQUE_RESULT_STAGE_TAG),
    ) {
        val viewportW = maxWidth
        val viewportH = viewportHeight ?: maxHeight
        val peekHeightDp = (if (hasFooter) 244.dp else 166.dp) + navSafeDp
        val medHeightDp = (viewportH * 0.45f).coerceIn(peekHeightDp + 76.dp, viewportH * 0.54f)
        val fullHeightDp = (viewportH - (topSafeDp + 226.dp)).coerceAtLeast(medHeightDp + 72.dp)

        val peekPx = with(density) { peekHeightDp.toPx() }
        val medPx = with(density) { medHeightDp.toPx() }
        val fullPx = with(density) { fullHeightDp.toPx() }
        val viewportWPx = with(density) { viewportW.toPx() }
        val viewportHPx = with(density) { viewportH.toPx() }
        val topSafePx = with(density) { topSafeDp.toPx() }

        val targetPx = when (detent) {
            TechniqueSheetDetent.PEEK -> peekPx
            TechniqueSheetDetent.MED -> medPx
            TechniqueSheetDetent.FULL -> fullPx
        }

        val sheetHeightAnim = remember { Animatable(targetPx) }
        var isDragging by remember { mutableStateOf(false) }

        LaunchedEffect(targetPx, isDragging, reduced) {
            if (!isDragging) {
                if (reduced) {
                    sheetHeightAnim.snapTo(targetPx)
                } else {
                    sheetHeightAnim.animateTo(
                        targetValue = targetPx,
                        animationSpec = tween(durationMillis = 420, easing = KpknMotion.Material),
                    )
                }
            }
        }

        val liveSheetPx = sheetHeightAnim.value.coerceIn(peekPx, fullPx)
        val liveSheetDp = with(density) { liveSheetPx.toDp() }
        val medProgress = if (medPx > peekPx) ((liveSheetPx - peekPx) / (medPx - peekPx)).coerceIn(0f, 1f) else 0f
        val fullProgress = if (fullPx > medPx) ((liveSheetPx - medPx) / (fullPx - medPx)).coerceIn(0f, 1f) else 0f

        // Geometría dinámica del vídeo `.tc-vid` -> `.tc-compact`
        val islandBottomPx = topSafePx + with(density) { 80.dp.toPx() }
        val medWindowCenterY = (islandBottomPx + (viewportHPx - medPx)) / 2f
        val medScale = (((viewportHPx - medPx - islandBottomPx + with(density) { 12.dp.toPx() }) / viewportHPx)
            .coerceIn(0.46f, 0.78f))
        val medShiftY = (medWindowCenterY - viewportHPx / 2f) * medProgress

        val compactWidthPx = with(density) { 94.dp.toPx() }
        val compactScale = (compactWidthPx / viewportWPx.coerceAtLeast(1f)).coerceIn(0.18f, 0.34f)
        val compactCenterX = with(density) { 16.dp.toPx() } + (viewportWPx * compactScale) / 2f
        val compactCenterY = islandBottomPx + with(density) { 10.dp.toPx() } + (viewportHPx * compactScale) / 2f

        val videoScale = lerpF(lerpF(1f, medScale, medProgress), compactScale, fullProgress)
        val videoShiftX = lerpF(0f, compactCenterX - viewportWPx / 2f, fullProgress)
        val videoShiftY = lerpF(medShiftY, compactCenterY - viewportHPx / 2f, fullProgress)
        val videoRadiusDp = (fullProgress * 22f).dp

        // Capa 1: Vídeo fullscreen con hazeSource + tap para volver desde minireproductor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        scaleX = videoScale
                        scaleY = videoScale
                        translationX = videoShiftX
                        translationY = videoShiftY
                        clip = fullProgress > 0.04f
                        shape = RoundedCornerShape(videoRadiusDp)
                    }
                    .then(
                        if (detent == TechniqueSheetDetent.FULL || fullProgress > 0.65f) {
                            Modifier
                                .border(1.5.dp, Color(0x38FFFFFF), RoundedCornerShape(videoRadiusDp))
                                .clickable { detent = TechniqueSheetDetent.MED }
                                .testTag(TECHNIQUE_MINIREP_TAG)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                videoSlot(Modifier.fillMaxSize(), overlayOptions)
            }
        }

        // Mini resumen junto al minireproductor cuando sheet está en `FULL`
        if (fullProgress > 0.2f) {
            val activeRepIdx = player.selectedRep.takeIf { it in results.repTelemetry.indices } ?: 0
            val activeHud = results.repTelemetry.getOrNull(activeRepIdx)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 124.dp, end = 16.dp, top = with(density) { islandBottomPx.toDp() } + 14.dp)
                    .alpha(fullProgress),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (activeHud != null) "REP ${activeHud.number} DE ${results.repTelemetry.size}" else "ANÁLISIS COMPLETO",
                    style = KpknType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                    color = StageCream.copy(alpha = 0.65f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val totalShown = review?.let { it.complete + it.partial + it.failed } ?: results.repTelemetry.size
                    MiniStatChip("$totalShown reps", StageCream)
                    if (activeHud?.romDeg != null) {
                        MiniStatChip("${if (activeHud.romApprox) "≈ " else ""}${activeHud.romDeg}° ROM", StageCream)
                    }
                }
                Text(
                    text = "Toca el vídeo para ampliar el reproductor",
                    style = KpknType.caption.copy(fontSize = 11.5.sp),
                    color = StageCream.copy(alpha = 0.75f),
                )
            }
        }

        // Capa 2: HUD superior + inferior sobre el escenario visible del vídeo
        val hudAlpha = (1f - fullProgress * 2.4f).coerceIn(0f, 1f)
        if (hudAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(hudAlpha),
            ) {
                // HUD Superior (`.tc-q` + `.tc-qcard` a la izquierda, `.tc-modes` a la derecha)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = topSafeDp + 76.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false).padding(end = 10.dp)) {
                        if (viewMode == TechniqueViewMode.DATOS) {
                            ConfidencePill(
                                confidence = results.confidence,
                                expanded = confidenceOpen,
                                hazeState = hazeState,
                                onClick = { confidenceOpen = !confidenceOpen },
                                modifier = Modifier.testTag(TECHNIQUE_CONFIDENCE_TAG),
                            )
                            AnimatedVisibility(
                                visible = confidenceOpen,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(150)),
                            ) {
                                ConfidenceCard(
                                    confidence = results.confidence,
                                    reasons = results.qualityReasons,
                                    hazeState = hazeState,
                                    onOpenHelp = {
                                        confidenceOpen = false
                                        onOpenHelp()
                                    },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .testTag(TECHNIQUE_CONFIDENCE_CARD_TAG),
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        ViewModeSelector(
                            current = viewMode,
                            onSelect = { next ->
                                viewMode = next
                                modeTip = next.label
                                if (next != TechniqueViewMode.DATOS) {
                                    confidenceOpen = false
                                    if (detent != TechniqueSheetDetent.PEEK) {
                                        detent = TechniqueSheetDetent.PEEK
                                    }
                                }
                            },
                            hazeState = hazeState,
                            modifier = Modifier.testTag(TECHNIQUE_MODES_TAG),
                        )
                        modeTip?.let { tip ->
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xB3121216))
                                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(tip, style = KpknType.caption.copy(fontSize = 11.sp), color = StageCream)
                            }
                        }
                    }
                }

                // HUD Inferior justo encima del borde de la hoja (`bottom = liveSheetDp + 10.dp`)
                if (viewMode != TechniqueViewMode.LIMPIO) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = liveSheetDp + 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (viewMode == TechniqueViewMode.DATOS) {
                                ActiveObservationHudPill(
                                    observations = results.observations,
                                    positionUs = { player.positionUs },
                                    hazeState = hazeState,
                                )
                                LivePhaseStatPill(
                                    state = state,
                                    results = results,
                                    positionUs = { player.positionUs },
                                    hazeState = hazeState,
                                )
                                BarSourceHudTag(
                                    source = state.overlay?.trailSource,
                                    hazeState = hazeState,
                                )
                            }
                            RepHudChip(
                                results = results,
                                selectedRep = player.selectedRep,
                                hazeState = hazeState,
                                onCycleRep = {
                                    val count = state.overlay?.reps?.size ?: 0
                                    if (count > 0) {
                                        val next = if (player.selectedRep in 0 until count) (player.selectedRep + 1) % count else 0
                                        player.selectRep(next)
                                    }
                                },
                            )
                        }

                        TechniqueSpeedSelector(
                            speed = player.speed,
                            onSpeedChange = player::setSpeed,
                            modifier = Modifier.testTag(TECHNIQUE_SPEED_TAG),
                        )
                    }
                }
            }
        }

        // Capa 3: Isla flotante `.tc-isl` arriba
        TechniqueFloatingIsland(
            exerciseName = state.exerciseName,
            setInfo = state.setInfo,
            onBack = actions.onBack,
            hazeState = hazeState,
            showMenu = showMenu,
            onOpenMenu = onOpenMenu,
            onMenuAnchorChanged = onMenuAnchorChanged,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topSafeDp + 10.dp, start = 14.dp, end = 14.dp),
        )

        // Capa 4: Hoja draggable `.tc-sheet` de 3 detents
        val draggableState = rememberDraggableState { delta ->
            isDragging = true
            scope.launch {
                sheetHeightAnim.snapTo((sheetHeightAnim.value - delta).coerceIn(peekPx, fullPx))
            }
        }
        val onDragStopped: suspend kotlinx.coroutines.CoroutineScope.(Float) -> Unit = { velocityY ->
            isDragging = false
            val cur = sheetHeightAnim.value
            val upwardVelocity = -velocityY
            detent = when {
                upwardVelocity > 500f -> when {
                    cur < medPx -> TechniqueSheetDetent.MED
                    else -> TechniqueSheetDetent.FULL
                }
                upwardVelocity < -500f -> when {
                    cur > medPx -> TechniqueSheetDetent.MED
                    else -> TechniqueSheetDetent.PEEK
                }
                else -> listOf(
                    TechniqueSheetDetent.PEEK to abs(cur - peekPx),
                    TechniqueSheetDetent.MED to abs(cur - medPx),
                    TechniqueSheetDetent.FULL to abs(cur - fullPx),
                ).minBy { it.second }.first
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(liveSheetDp)
                .testTag(TECHNIQUE_SHEET_TAG),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .kpknGlassPreset(
                        hazeState = hazeState,
                        preset = KpknGlassPreset.VidrioHoja,
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                        withBorder = true,
                        additionalScrim = Color(0xDC0C0C10),
                    ),
            )
            Column(modifier = Modifier.fillMaxSize()) {
            // Cabecera draggable + Strip de conteo + Scrubber + Controles 1 fila
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = onDragStopped,
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable {
                            detent = when (detent) {
                                TechniqueSheetDetent.PEEK -> TechniqueSheetDetent.MED
                                TechniqueSheetDetent.MED -> TechniqueSheetDetent.FULL
                                TechniqueSheetDetent.FULL -> TechniqueSheetDetent.PEEK
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 24.dp)
                        .testTag(TECHNIQUE_SHEET_HANDLE_TAG),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x4DFFFFFF)),
                    )
                }

                SheetMetricsStrip(
                    review = review,
                    fallbackTotal = results.repTelemetry.size,
                    detent = detent,
                    onToggleExpand = {
                        detent = when (detent) {
                            TechniqueSheetDetent.PEEK -> TechniqueSheetDetent.MED
                            TechniqueSheetDetent.MED -> TechniqueSheetDetent.FULL
                            TechniqueSheetDetent.FULL -> TechniqueSheetDetent.PEEK
                        }
                    },
                )

                val marks = state.overlay?.reps.orEmpty()
                TechniqueScrubber(
                    positionUs = { player.positionUs },
                    durationUs = player.durationUs,
                    reps = marks,
                    selectedRep = player.selectedRep,
                    onSeek = player::scrubTo,
                    onScrubStart = player::scrubStart,
                    onScrubEnd = player::scrubEnd,
                    repSpeedWeights = results.breakdown.scrubberSpeedWeights(),
                    observationTimesUs = results.observations.mapNotNull { it.seekUs },
                )

                TechniquePlayerControls(
                    isPlaying = player.isPlaying,
                    speed = player.speed,
                    loopEnabled = player.loopEnabled,
                    onPlayPause = player::togglePlay,
                    onFrameBack = { player.stepFrame(-1) },
                    onFrameForward = { player.stepFrame(1) },
                    onSpeedChange = player::setSpeed,
                    onLoopToggle = player::toggleLoop,
                    loopAvailable = marks.isNotEmpty(),
                    showSpeedRow = false,
                    onPrevRep = { stepRep(player, -1) },
                    onNextRep = { stepRep(player, +1) },
                    repNavigationAvailable = marks.isNotEmpty(),
                )
            }

            // Contenido scrollable `.tc-x` (se despliega al pasar de `PEEK` a `MED` / `FULL`)
            if (liveSheetPx > peekPx + 24f) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .alpha((medProgress * 1.15f).coerceIn(0f, 1f))
                        .verticalScroll(scroll)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.notice?.let { notice ->
                        KpknModule(
                            modifier = Modifier.testTag(TECHNIQUE_ANALYSIS_NOTICE_TAG),
                            accent = WizardColors.danger,
                            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 6.dp, bottom = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(notice, style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                                KpknIconButton(KpknLineIcons.Close, "Cerrar el aviso", actions.onDismissNotice)
                            }
                        }
                    }

                    // Selector de capas + Repetición activa + chips rápidos
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            KpknChip(text = "Trayectoria de barra", selected = trailLayer, onClick = onToggleTrail)
                            KpknChip(text = "Articulaciones", selected = bodyLayer, onClick = onToggleBody)
                        }
                        ActiveRepSummaryRow(
                            position = player.selectedRep.takeIf { it in results.repTelemetry.indices },
                            results = results,
                            review = review,
                        )
                        val marks = state.overlay?.reps.orEmpty()
                        if (marks.isNotEmpty()) {
                            TechniqueRepChips(
                                reps = marks,
                                selectedRep = player.selectedRep,
                                onSelectRep = player::selectRep,
                            )
                        }
                    }

                    results.breakdown?.let { breakdown ->
                        AnalysisDivider()
                        RepBreakdownModule(
                            breakdown = breakdown,
                            selectedMetric = RepMetricKind.entries.firstOrNull { it.name == chosenMetricName } ?: RepMetricKind.VELOCITY,
                            onSelectMetric = onSelectMetric,
                            selectedRep = player.selectedRep,
                            onSelectRep = player::selectRep,
                            review = review,
                            onToggleKind = { pos ->
                                val current = review?.kindAt(pos) ?: return@RepBreakdownModule
                                val original = review.reps.getOrNull(pos)?.originalKind ?: return@RepBreakdownModule
                                val next = when (current) {
                                    RepKind.FULL -> RepKind.PARTIAL
                                    RepKind.PARTIAL -> RepKind.FULL
                                    RepKind.FAILED -> return@RepBreakdownModule
                                }
                                val updated = corrections.toMutableMap()
                                if (next == original) updated.remove(pos) else updated[pos] = next
                                onUpdateCorrections(encodeRepCorrections(updated))
                            },
                            embedded = true,
                        )
                    }

                    AnalysisDivider()
                    AnalysisSummaryModule(summary = results.summary, embedded = true)

                    if (results.observations.isNotEmpty()) {
                        AnalysisDivider()
                        ObservationsModule(
                            items = results.observations,
                            onSee = { obs ->
                                if (obs.repPosition >= 0) player.selectRep(obs.repPosition)
                                obs.seekUs?.let(player::seekToUs)
                                detent = TechniqueSheetDetent.MED
                                scope.launch { scroll.animateScrollTo(0) }
                            },
                            embedded = true,
                        )
                    }

                    results.plate?.let { plate ->
                        AnalysisDivider()
                        PlateCalibrationModule(
                            plate = plate,
                            frame = plateFrame,
                            selectedDiameterMm = diameter,
                            onSelectDiameter = onSelectDiameter,
                            onPickOtherDiameter = onPickOtherDiameter,
                            onConfirm = { actions.onConfirmPlate(diameter) },
                            onOpenCalibration = onOpenCalibration,
                            embedded = true,
                        )
                    }

                    if (results.tips.isNotEmpty()) {
                        AnalysisDivider()
                        CameraTipsModule(tips = results.tips, embedded = true)
                    }

                    val exerciseKey = state.exerciseKey
                    if (state.canOpenTrends && exerciseKey != null) {
                        KpknSecondaryPill(
                            text = "Ver tendencia",
                            onClick = { actions.onOpenTrends(exerciseKey) },
                            modifier = Modifier.fillMaxWidth(),
                            tag = TECHNIQUE_ANALYSIS_TRENDS_TAG,
                        )
                    }

                    Spacer(Modifier.height(if (hasFooter) 6.dp else navSafeDp + 12.dp))
                }
            }

            // Footer fijo de entrega al borrador (solo si `deliverToDraft`)
            val useResult = actions.onUseReviewedResult
            if (review != null && useResult != null) {
                ReviewDeliveryFooter(
                    review = review,
                    onUse = {
                        useResult(
                            ReviewedAnalysisResult(
                                completeReps = review.complete,
                                partialReps = review.partial,
                                failedReps = review.failed,
                                corrections = corrections,
                            ),
                        )
                    },
                )
            } else if (liveSheetPx <= peekPx + 24f) {
                Spacer(Modifier.navigationBarsPadding())
            }
            }
        }
    }
}

private fun stepRep(player: TechniquePlayerState, delta: Int) {
    val reps = player.overlay?.reps.orEmpty()
    if (reps.isEmpty()) return
    val current = player.selectedRep
    val next = when {
        current !in reps.indices -> if (delta > 0) 0 else reps.lastIndex
        else -> (current + delta).coerceIn(0, reps.lastIndex)
    }
    player.selectRep(next)
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
private fun ConfidencePill(
    confidence: CountConfidence,
    expanded: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dotColor, label) = when (confidence) {
        CountConfidence.ALTA -> StageGreen to "Confianza alta"
        CountConfidence.MEDIA -> StageAmber to "Confianza media"
        CountConfidence.BAJA -> StageRed to "Confianza baja"
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, RoundedCornerShape(20.dp), withBorder = true, Color(0x88101014))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(7.dp))
        Text(text = label, style = KpknType.chip.copy(fontSize = 11.5.sp), color = StageCream)
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
            contentDescription = null,
            tint = Color(0x99FFFFFF),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ConfidenceCard(
    confidence: CountConfidence,
    reasons: List<String>,
    hazeState: HazeState,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (confidence) {
        CountConfidence.ALTA -> StageGreen
        CountConfidence.MEDIA -> StageAmber
        CountConfidence.BAJA -> StageRed
    }
    Box(modifier = modifier.widthIn(min = 248.dp, max = 268.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioHoja, RoundedCornerShape(18.dp), withBorder = true, Color(0xC8101014)),
        )
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Por qué esta confianza",
                style = KpknType.bodyStrong.copy(fontSize = 12.5.sp),
                color = StageCream,
            )
            for (reason in reasons) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(dotColor))
                    Spacer(Modifier.width(8.dp))
                    Text(text = reason, style = KpknType.caption.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = WizardColors.textMuted)
                }
            }
            Text(
                text = "Ver cómo leer este análisis ›",
                style = KpknType.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                color = StageCream,
                modifier = Modifier
                    .clickable(onClick = onOpenHelp)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ViewModeSelector(
    current: TechniqueViewMode,
    onSelect: (TechniqueViewMode) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, RoundedCornerShape(24.dp), withBorder = true, Color(0x88101014))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (mode in TechniqueViewMode.entries) {
            val isSelected = mode == current
            val icon: ImageVector = when (mode) {
                TechniqueViewMode.DATOS -> KpknLineIcons.Layers
                TechniqueViewMode.TRAY -> KpknLineIcons.Route
                TechniqueViewMode.LIMPIO -> KpknLineIcons.Eye
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) StageCream else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(mode) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = mode.label
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF111111) else StageCream,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ActiveObservationHudPill(
    observations: List<ObservationUi>,
    positionUs: () -> Long,
    hazeState: HazeState,
) {
    val now = positionUs()
    val hit = observations.firstOrNull { obs ->
        val seek = obs.seekUs ?: return@firstOrNull false
        abs(now - seek) <= 1_200_000L
    } ?: return
    Row(
        modifier = Modifier
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, RoundedCornerShape(14.dp), withBorder = true, Color(0xA614120B))
            .border(1.dp, StageAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(StageAmber))
        Spacer(Modifier.width(7.dp))
        Text(
            text = hit.text,
            style = KpknType.caption.copy(fontSize = 11.5.sp),
            color = StageCream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LivePhaseStatPill(
    state: TechniqueAnalysisUiState,
    results: AnalysisResultsUi,
    positionUs: () -> Long,
    hazeState: HazeState,
) {
    val live = buildLiveHudStatus(state.profileId, results, positionUs()) ?: return
    val phaseColor = when {
        live.direction > 0 -> StageGreen
        live.direction < 0 -> StageAmber
        else -> StageCream
    }
    val arrow = when {
        live.direction > 0 -> "▲"
        live.direction < 0 -> "▼"
        else -> "•"
    }
    Row(
        modifier = Modifier
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, RoundedCornerShape(14.dp), withBorder = true, Color(0x8A101014))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = arrow, style = KpknType.caption.copy(fontSize = 10.sp), color = phaseColor)
        Spacer(Modifier.width(6.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = StageCream, fontWeight = FontWeight.SemiBold)) { append(live.phaseLabel) }
                withStyle(SpanStyle(color = Color(0xCCFFFFFF))) { append(" · ${live.valueLabel}") }
            },
            style = KpknType.caption.copy(fontSize = 11.5.sp, fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun BarSourceHudTag(source: TechniqueTrailSource?, hazeState: HazeState) {
    val label = when (source) {
        TechniqueTrailSource.PLATE -> "Barra: disco"
        TechniqueTrailSource.POSE -> "Barra: pose"
        null -> return
    }
    Box(
        modifier = Modifier
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, RoundedCornerShape(12.dp), withBorder = true, Color(0x80101014))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text = label, style = KpknType.caption.copy(fontSize = 10.5.sp), color = Color(0xB8FFFFFF))
    }
}

@Composable
private fun RepHudChip(
    results: AnalysisResultsUi,
    selectedRep: Int,
    hazeState: HazeState,
    onCycleRep: () -> Unit,
) {
    val rep = results.repTelemetry.getOrNull(selectedRep) ?: return
    val rom = rep.romDeg?.let { " · ${if (rep.romApprox) "≈ " else ""}$it°" }.orEmpty()
    Row(
        modifier = Modifier
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, RoundedCornerShape(16.dp), withBorder = true, Color(0x94121216))
            .clickable(role = Role.Button, onClick = onCycleRep)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Rep ${rep.number} de ${results.repTelemetry.size}$rom",
            style = KpknType.chip.copy(fontSize = 11.5.sp, fontFeatureSettings = "tnum"),
            color = StageCream,
        )
    }
}

@Composable
private fun SheetMetricsStrip(
    review: AnalysisReviewUi?,
    fallbackTotal: Int,
    detent: TechniqueSheetDetent,
    onToggleExpand: () -> Unit,
) {
    val complete = review?.complete ?: fallbackTotal
    val partial = review?.partial ?: 0
    val failed = review?.failed ?: 0
    val total = complete + partial + failed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripStat(label = "Total", value = total.toString(), color = StageCream, Modifier.weight(1f))
        StripStat(label = "Completas", value = complete.toString(), color = StageCream, Modifier.weight(1f))
        StripStat(label = "Parciales", value = partial.toString(), color = if (partial > 0) StageAmber else Color(0x73FFFFFF), Modifier.weight(1f))
        StripStat(label = "Fallidas", value = failed.toString(), color = if (failed > 0) StageRed else Color(0x73FFFFFF), Modifier.weight(1f))
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF))
                .clickable(role = Role.Button, onClick = onToggleExpand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (detent == TechniqueSheetDetent.FULL) KpknLineIcons.ChevronDown else KpknLineIcons.ChevronUp,
                contentDescription = if (detent == TechniqueSheetDetent.FULL) "Contraer panel" else "Expandir panel",
                tint = StageCream,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StripStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = KpknType.bodyStrong.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = color)
        Text(text = label, style = KpknType.caption.copy(fontSize = 10.sp), color = Color(0x78FFFFFF), maxLines = 1)
    }
}

@Composable
private fun ActiveRepSummaryRow(
    position: Int?,
    results: AnalysisResultsUi,
    review: AnalysisReviewUi?,
) {
    val bar = position?.let { results.breakdown?.reps?.getOrNull(it) }
    val reviewed = review?.kindAt(position ?: -1) ?: bar?.kind
    val rom = bar?.romDeg ?: position?.let { results.repTelemetry.getOrNull(it)?.romDeg }
    val romApprox = bar?.romApprox ?: position?.let { results.repTelemetry.getOrNull(it)?.romApprox } ?: false
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (position != null) "REPETICIÓN ${position + 1}" else "REPETICIÓN",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = reviewed?.reviewLabel() ?: "Sin elegir",
                style = KpknType.title.copy(fontSize = 19.sp),
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (position != null && review != null && review.isChangedAt(position)) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Original: ${bar?.kind?.reviewLabel()} · Revisión: ${reviewed?.reviewLabel()}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag(ANALYSIS_REVIEW_ORIGINAL_TAG),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "ROM ESTIMADO", style = KpknType.eyebrow, color = WizardColors.textMuted)
            Spacer(Modifier.height(2.dp))
            if (rom != null) {
                KpknMetric(value = (if (romApprox) "≈" else "") + rom, unit = "°", style = KpknType.metricL)
            } else {
                KpknMetric(value = "—", unit = null, style = KpknType.metricL)
            }
        }
    }
}

@Composable
private fun ReviewDeliveryFooter(review: AnalysisReviewUi, onUse: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xE80A0A0E))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(text = "Conteo propuesto", style = KpknType.eyebrow, color = WizardColors.textMuted)
                    Text(text = "${review.proposal} reps", style = KpknType.title, color = WizardColors.text)
                }
                val secondary = buildString {
                    if (review.failed > 0) append(if (review.failed == 1) "1 fallida" else "${review.failed} fallidas")
                    if (review.changed) {
                        if (isNotEmpty()) append(" · ")
                        append("Original: ${review.originalProposal} reps")
                    }
                }
                if (secondary.isNotEmpty()) {
                    Text(
                        text = secondary,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = if (review.changed) Modifier.testTag(ANALYSIS_REVIEW_ORIGINAL_TAG) else Modifier,
                    )
                }
            }
            KpknPrimaryPill(
                text = "Revisar y usar resultado",
                onClick = onUse,
                modifier = Modifier.fillMaxWidth(),
                tag = TECHNIQUE_ANALYSIS_USE_TAG,
            )
        }
    }
}

@Composable
private fun MiniStatChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = KpknType.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold), color = color)
    }
}
