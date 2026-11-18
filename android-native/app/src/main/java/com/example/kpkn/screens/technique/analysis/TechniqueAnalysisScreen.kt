package com.example.kpkn.screens.technique.analysis

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.analysis.design.TechniqueAnalyzingStage
import com.example.kpkn.screens.technique.analysis.design.TechniqueHowToReadSheet
import com.example.kpkn.screens.technique.analysis.design.TechniqueLoadingSkeletonStage
import com.example.kpkn.screens.technique.analysis.design.TechniqueMediaMissingStage
import com.example.kpkn.screens.technique.analysis.design.TechniqueProfileModalStage
import com.example.kpkn.screens.technique.analysis.design.TechniqueResultStage
import com.example.kpkn.screens.technique.analysis.design.TechniqueStillStateStage
import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.screens.technique.player.TechniqueVideoPlayer
import com.example.kpkn.screens.technique.player.design.TechniqueOverlayOptions
import com.example.kpkn.screens.technique.player.rememberTechniquePlayerState
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.motion.KpknMotion
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/** Etiquetas de prueba de la pantalla. */
const val TECHNIQUE_ANALYSIS_SCREEN_TAG = "technique-analysis-screen"
const val TECHNIQUE_ANALYSIS_MENU_TAG = "technique-analysis-menu"
const val TECHNIQUE_ANALYSIS_TRENDS_TAG = "technique-analysis-trends"
const val TECHNIQUE_ANALYSIS_NOTICE_TAG = "technique-analysis-notice"
const val ANALYSIS_PANEL_TABS_TAG = "analysis-panel-tabs"
const val ANALYSIS_REP_TOGGLE_TAG = "analysis-rep-toggle"
const val ANALYSIS_REVIEW_ORIGINAL_TAG = "analysis-review-original"
const val TECHNIQUE_ANALYSIS_USE_TAG = "technique-analysis-use"

/** Las pestañas históricas del panel inferior (mantenidas por compatibilidad de referencias). */
enum class AnalysisPanel(val label: String) {
    TRAYECTORIA("Trayectoria"),
    METRICAS("Métricas"),
    REPETICIONES("Repeticiones"),
}

/**
 * Lo que la pantalla entrega al pulsar «Revisar y usar resultado»: el conteo revisado (completas, parciales y fallidas) y las
 * correcciones de clasificación por posición. Quien lo recibe lo incorpora al borrador; registrar la serie sigue siendo un paso aparte.
 */
data class ReviewedAnalysisResult(
    val completeReps: Int,
    val partialReps: Int,
    val failedReps: Int,
    val corrections: Map<Int, RepKind> = emptyMap(),
)

/** Clave de navegación con la que el analizador entrega su resultado a la sesión que lo abrió. */
const val TECHNIQUE_ANALYSIS_RESULT_KEY = "technique-analysis-result"

/**
 * El resultado revisado tal como viaja de la pantalla a la sesión (JSON en `savedStateHandle`): incluye el vídeo del que salió para
 * que el borrador se aplique a su serie y no a otra. Las correcciones van por nombre para no atar el guardado al enum del motor.
 */
@Serializable
data class ReviewedAnalysisDelivery(
    val mediaId: String,
    val complete: Int,
    val partial: Int,
    val failed: Int,
    val corrections: Map<Int, String> = emptyMap(),
)

/** Lo que la pantalla le pide a quien la monta. Todo son lambdas: la pantalla no sabe nada del ViewModel. */
class TechniqueAnalysisActions(
    val onBack: () -> Unit,
    val onOpenTrends: (exerciseKey: String) -> Unit,
    val onReanalyze: () -> Unit,
    val onAnalyze: () -> Unit,
    val onChooseProfile: (AnalysisProfileId) -> Unit,
    val onConfirmPlate: (diameterMm: Int) -> Unit,
    val onSavePlate: (seed: PlateSeed, diameterMm: Int) -> Unit,
    val onDismissNotice: () -> Unit,
    val onRequestPlateFrame: () -> Unit,
    /** Nulo cuando la pantalla se abrió fuera de una sesión: entonces no hay borrador al que devolver el resultado. */
    val onUseReviewedResult: ((ReviewedAnalysisResult) -> Unit)? = null,
    val onCancelAnalysis: () -> Unit = {},
) {
    companion object {
        /** Todo en blanco: para las hojas de render y las pruebas. */
        val None: TechniqueAnalysisActions = TechniqueAnalysisActions(
            onBack = {}, onOpenTrends = {}, onReanalyze = {}, onAnalyze = {}, onChooseProfile = {},
            onConfirmPlate = {}, onSavePlate = { _, _ -> }, onDismissNotice = {}, onRequestPlateFrame = {},
        )
    }
}

/**
 * La pantalla «Análisis de técnica» lista para enchufar a la navegación: crea el ViewModel de [mediaId], el estado
 * del reproductor y monta [TechniqueAnalysisScreen] con el reproductor de verdad. [onOpenTrends] recibe la clave del ejercicio.
 */
@Composable
fun TechniqueAnalysisRoute(
    mediaId: String,
    onBack: () -> Unit,
    onOpenTrends: (exerciseKey: String) -> Unit,
    onUseReviewedResult: ((ReviewedAnalysisResult) -> Unit)? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: TechniqueAnalysisViewModel = viewModel(
        key = "technique-analysis-$mediaId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TechniqueAnalysisViewModel(application, mediaId) as T
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val plateFrame by viewModel.plateFrame.collectAsStateWithLifecycle()
    val player = rememberTechniquePlayerState(state.overlay)
    val frameImage: ImageBitmap? = remember(plateFrame) { plateFrame?.bitmap?.asImageBitmap() }
    val actions = remember(viewModel, onBack, onOpenTrends, onUseReviewedResult) {
        TechniqueAnalysisActions(
            onBack = onBack,
            onOpenTrends = onOpenTrends,
            onReanalyze = viewModel::reanalyze,
            onAnalyze = viewModel::analyze,
            onChooseProfile = viewModel::chooseProfile,
            onConfirmPlate = viewModel::confirmPlate,
            onSavePlate = viewModel::savePlateSeed,
            onDismissNotice = viewModel::dismissNotice,
            onRequestPlateFrame = viewModel::requestPlateFrame,
            onUseReviewedResult = onUseReviewedResult,
            onCancelAnalysis = viewModel::cancelAnalysis,
        )
    }
    TechniqueAnalysisScreen(
        state = state,
        player = player,
        plateFrame = frameImage,
        actions = actions,
        videoSlot = { slotModifier, options ->
            val file = remember(state.videoPath) { state.videoPath?.let { File(it) } }
            if (file != null) {
                TechniqueVideoPlayer(file = file, overlay = state.overlay, state = player, modifier = slotModifier, options = options)
            } else {
                Box(slotModifier.background(KpknSurface.canvas), contentAlignment = Alignment.Center) {
                    Text(text = "No se encuentra el vídeo", style = KpknType.body, color = WizardColors.textMuted)
                }
            }
        },
    )
}

/**
 * La pantalla «Análisis de técnica» (VT7b + Pulido v1 `.tc`):
 * - `LOADING`: esqueleto fullscreen `.tc-sk`
 * - `MEDIA_MISSING`: estado vacío con isla superior `.tc-isl`
 * - `Analyzing` (`QUEUED`/`RUNNING`/`Actualizando`): visor fullscreen `.tc-an` con curva de señal viva y cancelación real
 * - `NotAnalyzed`/`Failed`/`Unsupported`/`ReportMissing`: vídeo atenuado `.tc-still` + tarjeta flotante `.tc-card`
 * - `NeedsProfile` (o «Elegir el tipo de movimiento»): modal `.tc-fs` reutilizando [ProfilePickerContent]
 * - `Done` (`results != null`): lienzo fullscreen `.tc` con isla `.tc-isl`, HUD flotante y hoja draggable de 3 detents (`PEEK` / `MED` / `FULL`).
 */
@Composable
fun TechniqueAnalysisScreen(
    state: TechniqueAnalysisUiState,
    player: TechniquePlayerState,
    plateFrame: ImageBitmap?,
    actions: TechniqueAnalysisActions,
    videoSlot: @Composable (Modifier, TechniqueOverlayOptions) -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    viewportHeight: Dp? = null,
    magnifierEnabled: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val hazeState = rememberHazeState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var showProfilePicker by rememberSaveable { mutableStateOf(false) }
    var pickedProfile by rememberSaveable { mutableStateOf<AnalysisProfileId?>(null) }
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    var metricName by rememberSaveable { mutableStateOf(RepMetricKind.ROM.name) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showPlateDialog by rememberSaveable { mutableStateOf(false) }
    var showOtherDiameter by rememberSaveable { mutableStateOf(false) }
    var chosenDiameter by rememberSaveable { mutableIntStateOf(0) }
    var trailLayer by rememberSaveable { mutableStateOf(true) }
    var bodyLayer by rememberSaveable { mutableStateOf(true) }
    var correctionsCode by rememberSaveable { mutableStateOf("") }
    val corrections = remember(correctionsCode) { decodeRepCorrections(correctionsCode) }

    // Coreografía `tc-exito` cuando pasa de Analizando -> Listo
    var wasAnalyzing by remember { mutableStateOf(false) }
    var justFinishedExit by remember { mutableStateOf(false) }
    var exitFade by remember { mutableFloatStateOf(1f) }
    val isNowReadyWithResults = state.phase == AnalysisPhase.READY && state.results != null
    LaunchedEffect(state.status, isNowReadyWithResults) {
        when {
            state.status is AnalysisStatusUi.Analyzing -> wasAnalyzing = true
            wasAnalyzing && isNowReadyWithResults -> {
                wasAnalyzing = false
                if (!reduced) {
                    justFinishedExit = true
                    exitFade = 1f
                    delay(16L)
                    exitFade = 0f
                    delay(340L)
                }
                justFinishedExit = false
            }
            else -> wasAnalyzing = false
        }
    }

    val plate = state.results?.plate
    val diameter = if (chosenDiameter > 0) chosenDiameter else plate?.diameterMm ?: 450

    LaunchedEffect(plate?.state, plate?.frameUs, showPlateDialog) {
        if (plate != null && (plate.state == PlateModuleState.SUGGESTED || showPlateDialog)) actions.onRequestPlateFrame()
    }
    BackHandler(enabled = menuOpen || showProfilePicker || showHelp) {
        when {
            menuOpen -> menuOpen = false
            showHelp -> showHelp = false
            showProfilePicker -> showProfilePicker = false
        }
    }

    val menuRows = buildList {
        if (state.status !is AnalysisStatusUi.NotAnalyzed && !state.isAnalyzing) add(MenuRow.REANALYZE)
        if (state.canOpenTrends) add(MenuRow.TRENDS)
        if (state.canChooseMovement) add(MenuRow.CHOOSE_MOVEMENT)
        if (isNowReadyWithResults) add(MenuRow.HOW_TO_READ)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (viewportHeight != null) Modifier.height(viewportHeight) else Modifier.fillMaxHeight())
            .background(Color(0xFF050507))
            .testTag(TECHNIQUE_ANALYSIS_SCREEN_TAG),
    ) {
        when (state.phase) {
            AnalysisPhase.LOADING -> TechniqueLoadingSkeletonStage(onBack = actions.onBack)
            AnalysisPhase.MEDIA_MISSING -> TechniqueMediaMissingStage(onBack = actions.onBack)
            AnalysisPhase.READY -> {
                val results = state.results
                val status = state.status
                when {
                    status is AnalysisStatusUi.Analyzing -> {
                        TechniqueAnalyzingStage(
                            exerciseName = state.exerciseName,
                            setInfo = state.setInfo,
                            videoAspect = state.videoAspect,
                            durationMs = state.durationMs,
                            status = status,
                            telemetry = state.liveTelemetry,
                            onClose = actions.onBack,
                            onCancel = actions.onCancelAnalysis,
                        )
                    }
                    results != null -> {
                        val breakdown = results.breakdown
                        val review = breakdown?.let { buildReview(it.reps, corrections) }
                        TechniqueResultStage(
                            state = state,
                            results = results,
                            player = player,
                            plateFrame = plateFrame,
                            actions = actions,
                            videoSlot = videoSlot,
                            review = review,
                            diameter = diameter,
                            onSelectDiameter = { chosenDiameter = it },
                            onPickOtherDiameter = { showOtherDiameter = true },
                            onOpenCalibration = { showPlateDialog = true },
                            chosenMetricName = metricName,
                            onSelectMetric = { metricName = it.name },
                            corrections = corrections,
                            onUpdateCorrections = { correctionsCode = it },
                            trailLayer = trailLayer,
                            bodyLayer = bodyLayer,
                            onToggleTrail = { trailLayer = !trailLayer },
                            onToggleBody = { bodyLayer = !bodyLayer },
                            onOpenMenu = { menuOpen = true },
                            onMenuAnchorChanged = { menuAnchor = it },
                            showMenu = menuRows.isNotEmpty(),
                            onOpenHelp = { showHelp = true },
                            scroll = scroll,
                            hazeState = hazeState,
                            viewportHeight = viewportHeight,
                            enterReveal = justFinishedExit || wasAnalyzing,
                        )
                    }
                    else -> {
                        TechniqueStillStateStage(
                            state = state,
                            status = status,
                            videoSlot = videoSlot,
                            hazeState = hazeState,
                            onBack = actions.onBack,
                            onAnalyze = actions.onAnalyze,
                            onRetry = actions.onReanalyze,
                            onOpenTrends = actions.onOpenTrends,
                            onDismissNotice = actions.onDismissNotice,
                            onPlayPreviewToggle = player::togglePlay,
                            isPlayingPreview = player.isPlaying,
                        )
                        if (status is AnalysisStatusUi.NeedsProfile && !showProfilePicker) {
                            val guess = status.guess
                            TechniqueProfileModalStage(
                                guess = guess,
                                selectedProfile = pickedProfile ?: guess,
                                profilesExpanded = profilesExpanded,
                                onSelectProfile = { pickedProfile = it },
                                onToggleProfiles = { profilesExpanded = !profilesExpanded },
                                onConfirmProfile = {
                                    val chosen = pickedProfile ?: guess
                                    if (chosen != null) actions.onChooseProfile(chosen)
                                },
                                hazeState = hazeState,
                            )
                        }
                    }
                }

                // Velo `tc-exito` suavizado si acaba de pasar de Analizando a Done
                if (justFinishedExit) {
                    val alphaAnim = androidx.compose.animation.core.animateFloatAsState(
                        targetValue = exitFade,
                        animationSpec = tween(320, easing = KpknMotion.Salida),
                        label = "tcExitoFade",
                    ).value
                    val scaleAnim = 1f + (1f - alphaAnim) * 0.04f
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = alphaAnim
                                scaleX = scaleAnim
                                scaleY = scaleAnim
                            }
                            .background(Color(0xFF050507)),
                    )
                }

                if (showProfilePicker) {
                    val guess = state.profileId
                    TechniqueProfileModalStage(
                        guess = guess,
                        selectedProfile = pickedProfile ?: guess,
                        profilesExpanded = profilesExpanded,
                        onSelectProfile = { pickedProfile = it },
                        onToggleProfiles = { profilesExpanded = !profilesExpanded },
                        onConfirmProfile = {
                            val chosen = pickedProfile ?: guess
                            if (chosen != null) {
                                showProfilePicker = false
                                actions.onChooseProfile(chosen)
                            }
                        },
                        hazeState = hazeState,
                        onDismiss = { showProfilePicker = false },
                    )
                }
            }
        }

        // Menú contextual `⋯` anclado a su botón
        val anchor = menuAnchor
        if (menuOpen && anchor != null && menuRows.isNotEmpty()) {
            KpknContextMenu(anchor = anchor, rows = menuRows.size, onDismiss = { menuOpen = false }) {
                for (row in menuRows) {
                    KpknContextMenuRow(
                        label = row.label,
                        onClick = {
                            menuOpen = false
                            when (row) {
                                MenuRow.REANALYZE -> actions.onReanalyze()
                                MenuRow.TRENDS -> state.exerciseKey?.let(actions.onOpenTrends)
                                MenuRow.CHOOSE_MOVEMENT -> {
                                    pickedProfile = state.profileId
                                    showProfilePicker = true
                                }
                                MenuRow.HOW_TO_READ -> showHelp = true
                            }
                        },
                    )
                }
            }
        }

        if (showHelp) {
            TechniqueHowToReadSheet(onDismiss = { showHelp = false })
        }

        if (showPlateDialog && plate != null) {
            PlateCalibrationDialog(
                plate = plate,
                frame = plateFrame,
                initialDiameterMm = diameter,
                onSave = { seed, mm ->
                    showPlateDialog = false
                    chosenDiameter = mm
                    actions.onSavePlate(seed, mm)
                },
                onDismiss = { showPlateDialog = false },
                magnifierEnabled = magnifierEnabled,
            )
        }
        if (showOtherDiameter) {
            PlateOtherDiameterDialog(
                initialMm = diameter,
                onDismiss = { showOtherDiameter = false },
                onAccept = { mm ->
                    chosenDiameter = mm
                    showOtherDiameter = false
                },
            )
        }
    }
}

private enum class MenuRow(val label: String) {
    REANALYZE("Reanalizar"),
    TRENDS("Ver tendencia"),
    CHOOSE_MOVEMENT("Elegir el tipo de movimiento"),
    HOW_TO_READ("Cómo leer este análisis"),
}
