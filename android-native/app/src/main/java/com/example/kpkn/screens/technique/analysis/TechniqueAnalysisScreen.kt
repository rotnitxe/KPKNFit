package com.example.kpkn.screens.technique.analysis

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.design.AnalysisStatusModule
import com.example.kpkn.screens.technique.analysis.design.AnalysisSummaryModule
import com.example.kpkn.screens.technique.analysis.design.CameraTipsModule
import com.example.kpkn.screens.technique.analysis.design.ObservationsModule
import com.example.kpkn.screens.technique.analysis.design.PlateCalibrationModule
import com.example.kpkn.screens.technique.analysis.design.RepBreakdownModule
import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.screens.technique.player.TechniqueRepMark
import com.example.kpkn.screens.technique.player.TechniqueVideoPlayer
import com.example.kpkn.screens.technique.player.design.TechniqueOverlayOptions
import com.example.kpkn.screens.technique.player.design.TechniquePlayerControls
import com.example.kpkn.screens.technique.player.design.TechniqueRepChips
import com.example.kpkn.screens.technique.player.design.TechniqueScrubber
import com.example.kpkn.screens.technique.player.rememberTechniquePlayerState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch
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

/** Las tres pestañas del análisis: reemplazan la columna interminable y el jugador sigue visible. */
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

/** Grupo de `kpknEntrance` de los módulos de la pantalla. */
private const val ENTRANCE_GROUP = "technique-analysis"

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
 * La pantalla «Análisis de técnica» lista para enchufar a la navegación (la registra VT9b): crea el ViewModel de [mediaId], el estado
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
 * La pantalla «Análisis de técnica» (VT7b), sin ViewModel: el reproductor con su capa (esqueleto, trayectoria y guía), el estado del
 * análisis y, cuando hay resultados, las pestañas «Trayectoria», «Métricas» y «Repeticiones» con el jugador siempre visible. El
 * reproductor llega en [videoSlot] (en las hojas de render, un vídeo de mentira) y se gobierna con [player].
 *
 * - **Una sola fuente de la repetición elegida:** [TechniquePlayerState.selectedRep]. La gráfica, los chips y «Ver» pasan por él.
 * - **Alto del reproductor:** con resultados, el 27 % de [viewportHeight] como máximo para dejar sitio al panel; en los estados sin
 *   resultados, el 62 %.
 * - [magnifierEnabled] apaga la lupa del diálogo del disco (las hojas de render no tienen ventana real).
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
    val scope = rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var showProfilePicker by rememberSaveable { mutableStateOf(false) }
    var pickedProfile by rememberSaveable { mutableStateOf<AnalysisProfileId?>(null) }
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    var metricName by rememberSaveable { mutableStateOf(RepMetricKind.VELOCITY.name) }
    var showPlateDialog by rememberSaveable { mutableStateOf(false) }
    var showOtherDiameter by rememberSaveable { mutableStateOf(false) }
    var chosenDiameter by rememberSaveable { mutableIntStateOf(0) }
    var panelName by rememberSaveable { mutableStateOf(AnalysisPanel.TRAYECTORIA.name) }
    var trailLayer by rememberSaveable { mutableStateOf(true) }
    var bodyLayer by rememberSaveable { mutableStateOf(true) }
    var correctionsCode by rememberSaveable { mutableStateOf("") }
    val corrections = remember(correctionsCode) { decodeRepCorrections(correctionsCode) }

    val plate = state.results?.plate
    // El diámetro de los chips parte del del módulo, hasta que el usuario elige otro.
    val diameter = if (chosenDiameter > 0) chosenDiameter else plate?.diameterMm ?: 450

    // El fotograma del disco hace falta para la miniatura (disco sin confirmar) y para el diálogo.
    LaunchedEffect(plate?.state, plate?.frameUs, showPlateDialog) {
        if (plate != null && (plate.state == PlateModuleState.SUGGESTED || showPlateDialog)) actions.onRequestPlateFrame()
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    val menuRows = buildList {
        if (state.status !is AnalysisStatusUi.NotAnalyzed && !state.isAnalyzing) add(MenuRow.REANALYZE)
        if (state.canOpenTrends) add(MenuRow.TRENDS)
        if (state.canChooseMovement) add(MenuRow.CHOOSE_MOVEMENT)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .testTag(TECHNIQUE_ANALYSIS_SCREEN_TAG),
    ) {
        val viewport = viewportHeight ?: maxHeight
        if (state.phase != AnalysisPhase.READY || state.results == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scroll),
        ) {
            KpknTopRow(
                onBack = actions.onBack,
                actions = {
                    if (state.phase == AnalysisPhase.READY && menuRows.isNotEmpty()) {
                        KpknIconButton(
                            icon = KpknLineIcons.More,
                            contentDescription = "Más opciones",
                            onClick = { menuOpen = true },
                            modifier = Modifier
                                .testTag(TECHNIQUE_ANALYSIS_MENU_TAG)
                                .onGloballyPositioned { menuAnchor = it.boundsInWindow() },
                        )
                    }
                },
            )
            when (state.phase) {
                AnalysisPhase.LOADING -> LoadingBody()
                AnalysisPhase.MEDIA_MISSING -> Box(Modifier.padding(horizontal = 16.dp)) {
                    KpknEmptyState(
                        icon = KpknLineIcons.Info,
                        title = "No encontramos este vídeo",
                        body = "Puede que se haya borrado de la galería de la serie.",
                    )
                }
                AnalysisPhase.READY -> {
                    AnalysisHeader(state = state, modifier = Modifier.kpknEntrance(ENTRANCE_GROUP, 0))
                    Spacer(Modifier.height(16.dp))
                    PlayerSection(
                        state = state,
                        player = player,
                        videoSlot = videoSlot,
                        viewport = viewport,
                        modifier = Modifier.kpknEntrance(ENTRANCE_GROUP, 1),
                    )
                    Spacer(Modifier.height(20.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.notice?.let { notice ->
                            AnalysisNotice(text = notice, onDismiss = actions.onDismissNotice)
                        }
                        if (showProfilePicker) {
                            val guess = state.profileId
                            AnalysisStatusModule(
                                status = AnalysisStatusUi.NeedsProfile(guess),
                                modifier = Modifier.kpknEntrance(ENTRANCE_GROUP, 2),
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
                            )
                        } else {
                            val guess = (state.status as? AnalysisStatusUi.NeedsProfile)?.guess
                            AnalysisStatusModule(
                                status = state.status,
                                modifier = Modifier.kpknEntrance(ENTRANCE_GROUP, 2),
                                selectedProfile = pickedProfile ?: guess,
                                profilesExpanded = profilesExpanded,
                                onSelectProfile = { pickedProfile = it },
                                onToggleProfiles = { profilesExpanded = !profilesExpanded },
                                onConfirmProfile = {
                                    val chosen = pickedProfile ?: guess
                                    if (chosen != null) actions.onChooseProfile(chosen)
                                },
                                onAnalyze = actions.onAnalyze,
                                onRetry = actions.onReanalyze,
                            )
                        }
                        val exerciseKey = state.exerciseKey
                        if (state.canOpenTrends && exerciseKey != null) {
                            KpknSecondaryPill(
                                text = "Ver tendencia",
                                onClick = { actions.onOpenTrends(exerciseKey) },
                                modifier = Modifier.fillMaxWidth().kpknEntrance(ENTRANCE_GROUP, 8),
                                tag = TECHNIQUE_ANALYSIS_TRENDS_TAG,
                            )
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(32.dp))
                }
            }
        }
        }

        val results = state.results
        if (state.phase == AnalysisPhase.READY && results != null) {
            val panel = AnalysisPanel.entries.firstOrNull { it.name == panelName } ?: AnalysisPanel.TRAYECTORIA
            val breakdown = results.breakdown
            val review = breakdown?.let { buildReview(it.reps, corrections) }
            val largeFont = LocalDensity.current.fontScale >= 1.2f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                KpknTopRow(
                    onBack = actions.onBack,
                    actions = {
                        if (menuRows.isNotEmpty()) {
                            KpknIconButton(
                                icon = KpknLineIcons.More,
                                contentDescription = "Más opciones",
                                onClick = { menuOpen = true },
                                modifier = Modifier
                                    .testTag(TECHNIQUE_ANALYSIS_MENU_TAG)
                                    .onGloballyPositioned { menuAnchor = it.boundsInWindow() },
                            )
                        }
                    },
                )
                AnalysisHeader(state = state, modifier = Modifier.kpknEntrance(ENTRANCE_GROUP, 0))
                Spacer(Modifier.height(8.dp))
                PlayerSection(
                    state = state,
                    player = player,
                    videoSlot = videoSlot,
                    viewport = viewport,
                    maxShare = if (largeFont) 0.20f else MAX_PLAYER_SHARE_WITH_PANEL,
                    showRepChips = false,
                    showTrailLegend = false,
                    overlayOptions = TechniqueOverlayOptions(skeleton = bodyLayer, trail = trailLayer),
                    modifier = Modifier.kpknEntrance(ENTRANCE_GROUP, 1),
                )
                Spacer(Modifier.height(8.dp))
                KpknSegmentedControl(
                    options = AnalysisPanel.entries.map { it.label },
                    selectedIndex = AnalysisPanel.entries.indexOf(panel),
                    onSelect = { panelName = AnalysisPanel.entries[it].name },
                    size = KpknSegmentedSize.Compact,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag(ANALYSIS_PANEL_TABS_TAG),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.notice?.let { notice -> AnalysisNotice(text = notice, onDismiss = actions.onDismissNotice) }
                    if (showProfilePicker) {
                        val guess = state.profileId
                        AnalysisStatusModule(
                            status = AnalysisStatusUi.NeedsProfile(guess),
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
                        )
                    } else {
                        when (panel) {
                            AnalysisPanel.TRAYECTORIA -> TrajectoryPanel(
                                marks = state.overlay?.reps.orEmpty(),
                                reps = breakdown?.reps.orEmpty(),
                                review = review,
                                selectedRep = player.selectedRep,
                                onSelectRep = player::selectRep,
                                trailLayer = trailLayer,
                                bodyLayer = bodyLayer,
                                onToggleTrail = { trailLayer = !trailLayer },
                                onToggleBody = { bodyLayer = !bodyLayer },
                            )
                            AnalysisPanel.METRICAS -> MetricsPanel(
                                results = results,
                                plateFrame = plateFrame,
                                diameter = diameter,
                                onSelectDiameter = { chosenDiameter = it },
                                onPickOtherDiameter = { showOtherDiameter = true },
                                onConfirmPlate = { actions.onConfirmPlate(diameter) },
                                onOpenCalibration = { showPlateDialog = true },
                                onSeeObservation = { observation ->
                                    if (observation.repPosition >= 0) player.selectRep(observation.repPosition)
                                    observation.seekUs?.let { player.seekToUs(it) }
                                },
                                onOpenTrends = actions.onOpenTrends,
                                exerciseKey = state.exerciseKey,
                                canOpenTrends = state.canOpenTrends,
                            )
                            AnalysisPanel.REPETICIONES -> if (breakdown != null) {
                                RepetitionsPanel(
                                    breakdown = breakdown,
                                    review = review,
                                    selectedMetric = RepMetricKind.entries.firstOrNull { it.name == metricName } ?: RepMetricKind.VELOCITY,
                                    onSelectMetric = { metricName = it.name },
                                    selectedRep = player.selectedRep,
                                    onSelectRep = player::selectRep,
                                    onToggleKind = toggle@ { position ->
                                        val current = review?.kindAt(position) ?: return@toggle
                                        val original = review.reps.getOrNull(position)?.originalKind ?: return@toggle
                                        val next = when (current) {
                                            RepKind.FULL -> RepKind.PARTIAL
                                            RepKind.PARTIAL -> RepKind.FULL
                                            RepKind.FAILED -> return@toggle
                                        }
                                        val updated = corrections.toMutableMap()
                                        if (next == original) updated.remove(position) else updated[position] = next
                                        correctionsCode = encodeRepCorrections(updated)
                                    },
                                )
                            }
                        }
                    }
                }
                val useResult = actions.onUseReviewedResult
                if (review != null && useResult != null) {
                    ReviewFooter(
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
                }
            }
        }

        // El menú «⋯», anclado a su botón.
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
                                    scope.launch { scroll.animateScrollTo(0) }
                                }
                            }
                        },
                    )
                }
            }
        }

        // Marcar el disco: pantalla completa sobre negro.
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
}

/** El eyebrow, el nombre del ejercicio (2 líneas como máximo) y la línea de la serie. */
@Composable
private fun AnalysisHeader(state: TechniqueAnalysisUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = "Análisis de técnica".uppercase(Locale.forLanguageTag("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.exerciseName,
            style = KpknType.title,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        val info = state.setInfo
        if (info != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = info, style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

/** Proporción del vídeo mientras no se conoce: vertical, como una grabación del móvil. */
private const val DEFAULT_VIDEO_ASPECT = 9f / 16f

/** Cuánto de la altura de la pantalla puede ocupar el reproductor como máximo. */
private const val MAX_PLAYER_SHARE = 0.62f

/** Con las pestañas, el reproductor cede altura al panel: sigue visible, pero no se come la consulta. */
private const val MAX_PLAYER_SHARE_WITH_PANEL = 0.27f

/**
 * El reproductor y lo que lo gobierna: el vídeo con esquinas de 20 dp, la leyenda de la trayectoria (solo si la hay), la línea de
 * tiempo, los mandos y los chips de repetición. Lo que cambia con el estado del reproductor se lee aquí, aparte del resto de la
 * pantalla; la posición, solo al dibujar.
 *
 * - [overlayOptions] decide qué capas pinta el vídeo (Trayectoria → barra y articulaciones).
 * - [showRepChips] y [showTrailLegend] se apagan en la revisión: los chips viven en su pestaña y la leyenda, junto a las capas.
 */
@Composable
private fun PlayerSection(
    state: TechniqueAnalysisUiState,
    player: TechniquePlayerState,
    videoSlot: @Composable (Modifier, TechniqueOverlayOptions) -> Unit,
    viewport: Dp,
    modifier: Modifier = Modifier,
    maxShare: Float = MAX_PLAYER_SHARE,
    showRepChips: Boolean = true,
    showTrailLegend: Boolean = true,
    overlayOptions: TechniqueOverlayOptions = TechniqueOverlayOptions(),
) {
    val aspect = state.videoAspect.takeIf { it > 0f } ?: DEFAULT_VIDEO_ASPECT
    val reps: List<TechniqueRepMark> = state.overlay?.reps.orEmpty()
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val videoHeight = minOf(maxWidth / aspect, viewport * maxShare).coerceAtLeast(160.dp)
        Column {
            videoSlot(
                Modifier
                    .fillMaxWidth()
                    .height(videoHeight)
                    .clip(RoundedCornerShape(20.dp)),
                overlayOptions,
            )
            if (showTrailLegend && state.overlay?.hasTrail == true) {
                Spacer(Modifier.height(8.dp))
                TrailLegend()
            }
            Spacer(Modifier.height(4.dp))
            TechniqueScrubber(
                positionUs = { player.positionUs },
                durationUs = player.durationUs,
                reps = reps,
                selectedRep = player.selectedRep,
                onSeek = player::scrubTo,
                onScrubStart = player::scrubStart,
                onScrubEnd = player::scrubEnd,
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
                loopAvailable = reps.isNotEmpty(),
            )
            if (showRepChips) {
                TechniqueRepChips(reps = reps, selectedRep = player.selectedRep, onSelectRep = player::selectRep)
            }
        }
    }
}

/** «Trayectoria: lenta → rápida» con una muestra del degradado azul → ámbar de la capa. Solo cuando hay trayectoria. */
@Composable
private fun TrailLegend() {
    Row(
        modifier = Modifier.semantics { contentDescription = "Trayectoria de la barra: de azul, lenta, a ámbar, rápida" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Trayectoria: lenta → rápida", style = KpknType.caption, color = WizardColors.textMuted)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Brush.horizontalGradient(listOf(SymbolPalette.columna, SymbolPalette.energia))),
        )
    }
}

/**
 * Pestaña «Trayectoria»: las capas que pinta el vídeo, la repetición elegida con su clasificación revisada y su ROM, el original
 * cuando hubo corrección y los chips para recorrer las repeticiones. El reproductor sigue arriba, siempre visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrajectoryPanel(
    marks: List<TechniqueRepMark>,
    reps: List<RepBarUi>,
    review: AnalysisReviewUi?,
    selectedRep: Int,
    onSelectRep: (Int) -> Unit,
    trailLayer: Boolean,
    bodyLayer: Boolean,
    onToggleTrail: () -> Unit,
    onToggleBody: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KpknChip(text = "Trayectoria de barra", selected = trailLayer, onClick = onToggleTrail)
        KpknChip(text = "Articulaciones", selected = bodyLayer, onClick = onToggleBody)
    }
    val position = selectedRep.takeIf { it in reps.indices }
    val rep = position?.let(reps::get)
    val reviewed = review?.kindAt(position ?: -1) ?: rep?.kind
    val rom = rep?.romDeg
    KpknModule {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (position != null) "REPETICIÓN ${position + 1}" else "REPETICIÓN",
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = reviewed?.reviewLabel() ?: "Sin elegir",
                    style = KpknType.title,
                    color = WizardColors.text,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "ROM ESTIMADO", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Spacer(Modifier.height(2.dp))
                if (rom != null) {
                    KpknMetric(
                        value = (if (rep.romApprox) "≈" else "") + rom,
                        unit = "°",
                        style = KpknType.metricL,
                    )
                } else {
                    KpknMetric(value = "—", unit = null, style = KpknType.metricL)
                }
            }
        }
        if (position != null && review != null && review.isChangedAt(position)) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Original: ${rep?.kind?.reviewLabel()} · Revisión: ${reviewed?.reviewLabel()}",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.testTag(ANALYSIS_REVIEW_ORIGINAL_TAG),
            )
        }
    }
    Text(
        text = "Selecciona una repetición y recorre su movimiento. Las líneas muestran la trayectoria medida.",
        style = KpknType.caption,
        color = WizardColors.textMuted,
    )
    if (marks.isNotEmpty()) {
        TechniqueRepChips(reps = marks, selectedRep = selectedRep, onSelectRep = onSelectRep)
    }
}

/** Pestaña «Métricas»: el resumen real, el disco, las observaciones, los consejos y la tendencia del ejercicio. */
@Composable
private fun MetricsPanel(
    results: AnalysisResultsUi,
    plateFrame: ImageBitmap?,
    diameter: Int,
    onSelectDiameter: (Int) -> Unit,
    onPickOtherDiameter: () -> Unit,
    onConfirmPlate: () -> Unit,
    onOpenCalibration: () -> Unit,
    onSeeObservation: (ObservationUi) -> Unit,
    onOpenTrends: (String) -> Unit,
    exerciseKey: String?,
    canOpenTrends: Boolean,
) {
    AnalysisSummaryModule(summary = results.summary)
    results.plate?.let { plate ->
        PlateCalibrationModule(
            plate = plate,
            frame = plateFrame,
            selectedDiameterMm = diameter,
            onSelectDiameter = onSelectDiameter,
            onPickOtherDiameter = onPickOtherDiameter,
            onConfirm = onConfirmPlate,
            onOpenCalibration = onOpenCalibration,
        )
    }
    ObservationsModule(items = results.observations, onSee = onSeeObservation)
    CameraTipsModule(tips = results.tips)
    if (canOpenTrends && exerciseKey != null) {
        KpknSecondaryPill(
            text = "Ver tendencia",
            onClick = { onOpenTrends(exerciseKey) },
            modifier = Modifier.fillMaxWidth(),
            tag = TECHNIQUE_ANALYSIS_TRENDS_TAG,
        )
    }
}

/** Pestaña «Repeticiones»: la comparación por velocidad, ROM o tiempo, con la corrección completa/parcial. */
@Composable
private fun RepetitionsPanel(
    breakdown: RepBreakdownUi,
    review: AnalysisReviewUi?,
    selectedMetric: RepMetricKind,
    onSelectMetric: (RepMetricKind) -> Unit,
    selectedRep: Int,
    onSelectRep: (Int) -> Unit,
    onToggleKind: (Int) -> Unit,
) {
    RepBreakdownModule(
        breakdown = breakdown,
        selectedMetric = selectedMetric,
        onSelectMetric = onSelectMetric,
        selectedRep = selectedRep,
        onSelectRep = onSelectRep,
        review = review,
        onToggleKind = onToggleKind,
    )
    Text(
        text = if (selectedMetric == RepMetricKind.VELOCITY) {
            "Velocidad relativa; no se muestran m/s sin calibración."
        } else {
            "Selecciona una barra para revisar esa repetición."
        },
        style = KpknType.caption,
        color = WizardColors.textMuted,
    )
}

/**
 * El pie fijo de la revisión: el conteo propuesto (con el original al lado si hubo corrección) y «Revisar y usar resultado». Solo
 * aparece cuando quien monta la pantalla puede recibir el resultado; usarlo no registra la serie, que sigue siendo un paso aparte.
 */
@Composable
private fun ReviewFooter(review: AnalysisReviewUi, onUse: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(text = "Conteo propuesto", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text(
                    text = "${review.proposal} reps",
                    style = KpknType.title,
                    color = WizardColors.text,
                )
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

/** Aviso de que una acción no se pudo guardar: módulo teñido de `danger` con su cierre. */
@Composable
private fun AnalysisNotice(text: String, onDismiss: () -> Unit) {
    KpknModule(
        modifier = Modifier.testTag(TECHNIQUE_ANALYSIS_NOTICE_TAG),
        accent = WizardColors.danger,
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar el aviso", onClick = onDismiss)
        }
    }
}

/** Mientras carga: el sitio del reproductor y dos módulos de esqueleto, para que la pantalla no salte al llegar los datos. */
@Composable
private fun LoadingBody() {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KpknSkeleton(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(20.dp))
        KpknSkeletonModule(lines = 3)
        KpknSkeletonModule(lines = 2)
    }
}
