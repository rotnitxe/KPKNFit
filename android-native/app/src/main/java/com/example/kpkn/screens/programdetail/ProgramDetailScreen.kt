package com.example.kpkn.screens.programdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.programs.PlanInfoMode
import com.example.kpkn.screens.programs.PlanInfoSheet
import com.example.kpkn.screens.programs.TrainingMaxWizard
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.ProgramAnalyticsEngine
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.BlockTransitionEngine
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.programdetail.components.*
import com.example.kpkn.services.workout.LoopNotificationManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.domain.training.HomeSessionResolver.allWeekLocations
import com.example.kpkn.screens.onboarding.design.WizardDarkSystemBars
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.design.ProgramAboutModule
import com.example.kpkn.screens.programdetail.design.ProgramProgressModule
import com.example.kpkn.screens.programdetail.design.ProgramDecisionsSheet
import com.example.kpkn.screens.programdetail.design.WeeklyCheckinSheet
import com.example.kpkn.screens.programdetail.design.ProgramVolumeCalibrationModule
import com.example.kpkn.screens.programdetail.design.ProgramPlanningVolumeTargetSheet
import com.example.kpkn.screens.programdetail.design.PlanningRangeProposalsSheet
import com.example.kpkn.screens.programdetail.design.repeatingCycleDeloadNotice
import com.example.kpkn.screens.programdetail.design.ProgramDetailPlaceholder
import com.example.kpkn.screens.programdetail.design.ProgramEditDraft
import com.example.kpkn.screens.programdetail.design.ProgramEditSheet
import com.example.kpkn.screens.programdetail.design.ProgramEmptyStructure
import com.example.kpkn.screens.programdetail.design.ProgramHeader
import com.example.kpkn.screens.programdetail.design.ProgramHeaderActions
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramMoreMenu
import com.example.kpkn.screens.programdetail.design.ProgramOneRmSheet
import com.example.kpkn.screens.programdetail.design.ProgramPinnedTabs
import com.example.kpkn.screens.programdetail.design.ProgramPinnedTopOffset
import com.example.kpkn.screens.programdetail.design.ProgramIslandBottom
import com.example.kpkn.screens.programdetail.design.ProgramPlanSheet
import com.example.kpkn.screens.programdetail.design.programDetailTabsOptions
import com.example.kpkn.screens.programdetail.design.programEditDraftOf
import com.example.kpkn.screens.programdetail.design.programHeaderDotColor
import com.example.kpkn.screens.programdetail.design.programHeaderPrimaryOf
import com.example.kpkn.screens.programdetail.design.programHeaderStatusOf
import com.example.kpkn.screens.programdetail.design.programMetaLine
import com.example.kpkn.screens.programdetail.design.programScopeLine
import com.example.kpkn.screens.programdetail.design.programNextSessionOf
import com.example.kpkn.screens.programdetail.design.repeatingWeekSubtitle
import com.example.kpkn.screens.programdetail.design.programProgressLabel
import com.example.kpkn.screens.programdetail.design.rememberPassedLine
import com.example.kpkn.ui.components.rememberProgramCover
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknStatusVeil
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopIsland
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.screens.programdetail.design.AutoregulationProposalsCard
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramCopyWeekSheet
import com.example.kpkn.screens.programdetail.design.ProgramDeleteSessionDialog
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import com.example.kpkn.screens.programdetail.design.ProgramPhaseBlockUi
import com.example.kpkn.screens.programdetail.design.ProgramPhaseWeekUi
import com.example.kpkn.screens.programdetail.design.ProgramPhasesModule
import com.example.kpkn.screens.programdetail.design.ProgramTransitionModule
import com.example.kpkn.screens.programdetail.design.ProgramWeekEditSheet
import com.example.kpkn.screens.programdetail.design.ProgramWeekHeader
import com.example.kpkn.screens.programdetail.design.ProgramWeekStrip
import com.example.kpkn.screens.programdetail.design.programPhaseEyebrow
import com.example.kpkn.screens.programdetail.design.programPhaseOf
import androidx.compose.ui.geometry.Rect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onEditSession: (String) -> Unit,
    onCreateSession: (String, String, Int, Int, Int) -> Unit,
    onOpenProgram: (String) -> Unit = {},
    onOpenCatalog: () -> Unit = {},
    viewModel: ProgramDetailViewModel = viewModel(factory = ProgramDetailViewModel.factory(programId)),
) {
    val program by viewModel.program.collectAsState()
    val progressReport by viewModel.progressReport.collectAsStateWithLifecycle()
    val plannedLoadProjection by viewModel.plannedLoadProjection.collectAsStateWithLifecycle()
    var showPlannedLoad by remember(plannedLoadProjection?.weekId,plannedLoadProjection?.cycleNumber) { mutableStateOf(false) }
    val pendingWeeklyCheckin by viewModel.pendingWeeklyCheckin.collectAsStateWithLifecycle()
    val weeklyCheckinPreview by viewModel.weeklyCheckinPreview.collectAsStateWithLifecycle()
    val weeklyCheckinBusy by viewModel.weeklyCheckinBusy.collectAsStateWithLifecycle()
    val pendingBlockReview by viewModel.pendingBlockReview.collectAsStateWithLifecycle()
    val blockReviewPreview by viewModel.blockReviewPreview.collectAsStateWithLifecycle()
    val blockReviewBusy by viewModel.blockReviewBusy.collectAsStateWithLifecycle()
    val pendingContinuation by viewModel.pendingContinuation.collectAsStateWithLifecycle()
    val continuationPreview by viewModel.continuationPreview.collectAsStateWithLifecycle()
    val continuationBusy by viewModel.continuationBusy.collectAsStateWithLifecycle()
    val openCatalogRequested by viewModel.openCatalogRequested.collectAsStateWithLifecycle()
    val exerciseSwapRequest by viewModel.exerciseSwapRequest.collectAsStateWithLifecycle()
    val exerciseSwapPreview by viewModel.exerciseSwapPreview.collectAsStateWithLifecycle()
    val exerciseSwapBusy by viewModel.exerciseSwapBusy.collectAsStateWithLifecycle()
    LaunchedEffect(openCatalogRequested) {
        if (openCatalogRequested) { viewModel.consumeOpenCatalogRequest(); onOpenCatalog() }
    }
    var showWeeklyCheckin by remember(pendingWeeklyCheckin) { mutableStateOf(false) }
    var showDecisions by rememberSaveable { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val isActive by viewModel.isActiveProgram.collectAsState()
    val isPaused by viewModel.isPausedProgram.collectAsState()
    val currentWeeks by viewModel.currentWeeks.collectAsState()
    val displayedSessions by viewModel.displayedSessions.collectAsState()
    val selectedWeekMeta by viewModel.selectedWeekMeta.collectAsState()
    val roadmapBlocks by viewModel.roadmapBlocks.collectAsState()
    val activeProgramState by viewModel.activeProgramState.collectAsState()
    val programsReady by viewModel.programsReady.collectAsState()
    val primarySession by viewModel.headerPrimarySession.collectAsState()
    val programLine by viewModel.headerProgramLine.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(programId) {
        viewModel.loadFeedbacks(context)
        viewModel.attachSnapshotStore(
            com.example.kpkn.data.preferences.PreferenceStores.programSnapshots(context),
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showKpknSnackbar(message, snackbarTypeFor(message))
        viewModel.consumeSnackbarMessage()
    }

    LaunchedEffect(uiState.pendingOpenProgramId) {
        val id = uiState.pendingOpenProgramId ?: return@LaunchedEffect
        viewModel.consumePendingOpenProgram()
        onOpenProgram(id)
    }
    var showVolumeSetupNotice by remember { mutableStateOf(false) }
    var openVolumeSheetToken by remember { mutableIntStateOf(0) }
    var notifiedLoopWeekId by remember { mutableStateOf<String?>(null) }
    var showSplitPage by rememberSaveable { mutableStateOf(false) }
    var showOneRmDialog by rememberSaveable { mutableStateOf(false) }
    var showTmEditor by rememberSaveable { mutableStateOf(false) }
    var showVolumeSheet by rememberSaveable { mutableStateOf(false) }
    var showPlanSheet by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    // Borrador de «Editar programa»: se abre desde la cabecera o desde «Más opciones» y no se escribe hasta «Guardar».
    var editDraft by remember { mutableStateOf<ProgramEditDraft?>(null) }
    // Id de la entrada del catálogo cuya hoja «Cómo funciona» está abierta (solo lectura); null = cerrada.
    var planInfoEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    // Máximos del test de 1RM en kg para la hoja «Registrar tus máximos»; null = sin marca declarada.
    var squat1RmKg by rememberSaveable { mutableStateOf<Double?>(null) }
    var bench1RmKg by rememberSaveable { mutableStateOf<Double?>(null) }
    var deadlift1RmKg by rememberSaveable { mutableStateOf<Double?>(null) }
    var overhead1RmKg by rememberSaveable { mutableStateOf<Double?>(null) }

    WizardDarkSystemBars()

    // Sin programa cargado: esqueletos. «No encontrado» (y la salida) solo cuando el repositorio ya leyó sus programas.
    val p = program
    if (p == null) {
        if (programsReady) {
            LaunchedEffect(Unit) {
                snackbarHostState.showKpknSnackbar("Programa no encontrado", SnackbarType.DANGER)
                kotlinx.coroutines.delay(1500)
                onBack()
            }
        }
        ProgramDetailPlaceholder(programsReady = programsReady, onBack = onBack, snackbarHostState = snackbarHostState)
        return
    }

    val bundledCover = rememberProgramCover(programCoverEntryIdOf(p.coverImage), with(density) { 88.dp.roundToPx() })

    LaunchedEffect(p.id, p.volumeSetupPromptSeen) {
        if (!p.volumeSetupPromptSeen) {
            showVolumeSetupNotice = true
        }
    }
    // «Calibrar ahora» del aviso sube el token: se abre la hoja de calibración del volumen.
    LaunchedEffect(openVolumeSheetToken) {
        if (openVolumeSheetToken > 0) showVolumeSheet = true
    }
    // Recorrido de Entreno: la primera vez que se entra en un programa ya cargado (visto = el controlador no arranca).
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(p.id) { tourHost?.startTour(TourIds.TRAINING, TourScripts.training) }

    // La isla aparece cuando la cabecera sale por arriba; las pestañas se pegan cuando llegan bajo la isla. Todo en la raíz.
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val islandLinePx = statusTopPx + with(density) { ProgramIslandBottom.toPx() }
    val pinLinePx = statusTopPx + with(density) { ProgramPinnedTopOffset.toPx() }
    val scroll = rememberScrollState()
    val headerBottomRoot = remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val tabsTopRoot = remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val islandVisible = rememberPassedLine(markerRootY = headerBottomRoot, lineRootPx = islandLinePx)
    val tabsPinned = rememberPassedLine(markerRootY = tabsTopRoot, lineRootPx = pinLinePx)
    val slidePx = with(density) { 12.dp.roundToPx() }

    val status = programHeaderStatusOf(isActive = isActive, isPaused = isPaused)
    val weekCount = remember(p) { p.allWeekLocations().size }
    val repeatingWeek = p.isSimpleProgram && weekCount <= 1
    val primaryAction = programHeaderPrimaryOf(
        isActive = isActive,
        sessionName = primarySession?.takeIf { !it.isCompleted }?.session?.name,
        sessionOngoing = primarySession?.isOngoing == true,
    )
    val coverKind = heroCoverKind(p.coverImage, hasBundledCover = bundledCover != null)
    val identity = remember(p.id, p.structureTemplateId, p.coverImage) { coverIdentityForProgram(p) }
    // La siguiente sesión de la semana que se ve: la fila de acciones la enseña en un día sin sesión («Siguiente: jueves · Pierna A»).
    val nextSession = remember(displayedSessions) {
        programNextSessionOf(
            sessions = displayedSessions.mapNotNull { session -> session.dayOfWeek?.let { it to session.name } },
            today = java.time.LocalDate.now().dayOfWeek.value,
        )
    }
    val headerState = ProgramHeaderState(
        name = p.name,
        status = status,
        meta = programMetaLine(weekCount = weekCount, repeatingWeek = repeatingWeek, daysPerWeek = p.weekDays, mode = p.mode),
        identity = identity,
        photoUrl = p.coverImage.takeIf { coverKind == HeroCoverKind.REMOTE },
        bundled = bundledCover.takeIf { coverKind == HeroCoverKind.BUNDLED },
        weekCount = weekCount,
        currentWeekIndex = programLine?.weekIndex,
        progressLabel = programProgressLabel(
            weekIndex = programLine?.weekIndex,
            weekCount = programLine?.weekCount,
            phase = programLine?.phase,
            repeatingWeek = repeatingWeek,
            cycle = p.runState?.cycleNumber,
        ),
        primary = primaryAction,
        nextSession = nextSession,
        scopeLine = programScopeLine(p),
        deloadNotice = repeatingCycleDeloadNotice(p),
    )
    val openEdit: () -> Unit = {
        editDraft = programEditDraftOf(p)
        showEditSheet = true
    }
    val headerActions = ProgramHeaderActions(
        onPrimary = {
            when (primaryAction) {
                is ProgramHeaderPrimary.Start, ProgramHeaderPrimary.Continue -> primarySession?.let { onStartWorkout(it.session, p) }
                ProgramHeaderPrimary.Activate -> viewModel.activateOrResume()
                ProgramHeaderPrimary.None -> Unit
            }
        },
        onStartPause = { viewModel.toggleStartPause() },
        onEdit = openEdit,
        onOpenNextSession = {
            // Abre ese día en la pestaña Semana: se despliega solo ese día de la semana que se ve.
            val weekId = selectedWeekMeta?.id
            val day = nextSession?.dayOfWeek
            if (weekId != null && day != null) viewModel.setExpandedWeekDays(weekId, setOf(day))
            viewModel.setStructureSubTab(StructureSubTab.SEMANA)
        },
    )
    val planSummary = remember(p) { buildProgramPlanDisplaySummary(p) }
    val tabOptions = programDetailTabsOptions
    val tabIndex = uiState.structureSubTab.ordinal
    val selectTab: (Int) -> Unit = { index -> viewModel.setStructureSubTab(StructureSubTab.entries[index]) }

    Box(modifier = Modifier.fillMaxSize().background(KpknSurface.canvas)) {
        if (showSplitPage) {
            // La página de repartos hace su propio scroll (y su isla); aquí solo se reserva la barra de estado.
            SplitView(
                program = p,
                selectedBlockId = uiState.selectedBlockId,
                selectedWeekId = uiState.selectedWeekId,
                onUpdateProgram = {
                    viewModel.updateProgram(it)
                    showSplitPage = false
                },
                onBack = { showSplitPage = false },
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .statusBarsPadding(),
            ) {
                KpknTopRow(
                    onBack = onBack,
                    actions = {
                        KpknIconButton(
                            icon = KpknLineIcons.More,
                            contentDescription = "Más opciones",
                            onClick = { showMoreMenu = true },
                        )
                    },
                )
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .kpknEntrance("programdetail", 0)
                            .onGloballyPositioned { headerBottomRoot.floatValue = it.positionInRoot().y + it.size.height },
                    ) {
                        ProgramHeader(state = headerState, actions = headerActions)
                    }
                    Spacer(Modifier.height(16.dp))
                    ProgramAboutModule(
                        provenance = aboutProvenanceLine(planSummary),
                        notes = planSummary.planNotes,
                        onOpenPlanInfo = planSummary.catalogEntry?.let { entry -> { planInfoEntryId = entry.id } },
                        onOpenMore = { showPlanSheet = true },
                        modifier = Modifier.fillMaxWidth().kpknEntrance("programdetail", 1),
                    )
                    Spacer(Modifier.height(20.dp))
                    ProgramProgressModule(report = progressReport, onDecisions = { showDecisions = true })
                    if (progressReport != null) Spacer(Modifier.height(20.dp))
                    plannedLoadProjection?.takeIf { it.loads.isNotEmpty() }?.let { projection ->
                        com.example.kpkn.screens.programdetail.design.ProgramPlannedLoadModule(projection) { showPlannedLoad = true }
                        Spacer(Modifier.height(20.dp))
                    }
                    pendingContinuation?.let { pending ->
                        ProgramTransitionModule(message = "Programa terminado. Elige cómo seguir.",
                            primaryLabel = "Repetir programa",onPrimary = { viewModel.reviewContinuation(pending,com.example.kpkn.domain.training.block.ContinuationChoice.REPEAT_WITH_NEW_MAXES) },
                            secondaryLabel = "Elegir catálogo",onSecondary = { viewModel.reviewContinuation(pending,com.example.kpkn.domain.training.block.ContinuationChoice.PICK_FROM_CATALOG) },
                            dismissLabel = "Semana de mantenimiento",onDismiss = { viewModel.reviewContinuation(pending,com.example.kpkn.domain.training.block.ContinuationChoice.MAINTENANCE_WEEK) })
                        Spacer(Modifier.height(20.dp))
                    }
                    pendingBlockReview?.let { pending ->
                        val alternative = pending.adjustment == null && pending.alternatives.isNotEmpty()
                        ProgramTransitionModule(message = pending.review.summary,
                            primaryLabel = if (alternative) "Revisar inicio suave" else "Revisar bloque",
                            onPrimary = { viewModel.reviewBlockAdjustment(pending, if (alternative) 0 else null) },
                            secondaryLabel = "Conservar plan", onSecondary = { viewModel.keepBlockPlan(pending) },
                            dismissLabel = "Ver decisiones", onDismiss = { showDecisions = true })
                        Spacer(Modifier.height(20.dp))
                    }
                    pendingWeeklyCheckin?.let { request ->
                        ProgramTransitionModule(
                            message = "¿Cómo fue la semana pasada? Puedes revisar una propuesta para los accesorios de la nueva semana.",
                            primaryLabel = "Responder", onPrimary = { viewModel.clearWeeklyCheckinPreview(); showWeeklyCheckin = true },
                            secondaryLabel = null, onSecondary = {}, dismissLabel = "Omitir esta semana",
                            onDismiss = { viewModel.omitWeeklyCheckin(com.example.kpkn.domain.training.cycle.WeeklyCheckin(
                                request.weekId, request.cycleNumber, System.currentTimeMillis(), skipped = true,
                                programRunId = request.programRunId, feedbackWeekId = request.feedbackWeekId,
                                feedbackCycleNumber = request.feedbackCycleNumber)) },
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { tabsTopRoot.floatValue = it.positionInRoot().y }
                        .tourAnchor(TourAnchors.TRAINING_ESTRUCTURA),
                ) {
                    // Cuando están pegadas, el control vive en la isla de abajo; aquí queda su hueco de la misma altura.
                    if (tabsPinned) {
                        Spacer(Modifier.fillMaxWidth().height(48.dp))
                    } else {
                        KpknSegmentedControl(
                            options = tabOptions,
                            selectedIndex = tabIndex,
                            onSelect = selectTab,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = uiState.structureSubTab,
                    transitionSpec = {
                        // El contenido nuevo entra desde el lado de la pestaña elegida: 12 dp y 220 ms.
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(animationSpec = tween(durationMillis = 220)) +
                            slideInHorizontally(animationSpec = tween(durationMillis = 220)) { direction * slidePx })
                            .togetherWith(
                                fadeOut(animationSpec = tween(durationMillis = 220)) +
                                    slideOutHorizontally(animationSpec = tween(durationMillis = 220)) { -direction * slidePx },
                            )
                    },
                    label = "programDetailTab",
                ) { tab ->
                    TrainingPanel(
                        viewModel = viewModel,
                        program = p,
                        roadmapBlocks = roadmapBlocks,
                        currentWeeks = currentWeeks,
                        displayedSessions = displayedSessions,
                        selectedWeekMeta = selectedWeekMeta,
                        selectedBlockId = uiState.selectedBlockId,
                        selectedWeekId = uiState.selectedWeekId,
                        structureSubTab = tab,
                        onStartWorkout = onStartWorkout,
                        onEditSession = onEditSession,
                        onCreateSession = onCreateSession,
                        onOpenProgram = onOpenProgram,
                        onOpenSplitPage = { showSplitPage = true },
                        onOpenOneRmDialog = { showOneRmDialog = true },
                        onOpenTmEditor = { showTmEditor = true },
                    )
                }
            }
        }

        if (!showSplitPage) {
            // Velo bajo la barra de estado: por encima del contenido que se desliza y por debajo de la isla.
            KpknStatusVeil(modifier = Modifier.align(Alignment.TopCenter))
            KpknTopIsland(
                visible = islandVisible,
                title = p.name,
                leading = {
                    KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = onBack)
                },
                actions = {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(programHeaderDotColor(status)))
                    Spacer(Modifier.width(8.dp))
                    KpknIconButton(
                        icon = KpknLineIcons.More,
                        contentDescription = "Más opciones",
                        onClick = { showMoreMenu = true },
                    )
                },
            )
            ProgramPinnedTabs(
                visible = tabsPinned,
                options = tabOptions,
                selectedIndex = tabIndex,
                onSelect = selectTab,
                topOffset = ProgramPinnedTopOffset,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) { KpknSnackbar(it) }
    }

    if (showVolumeSheet) {
        VolumeCalibrationSheet(
            currentMode = p.mode,
            onDismiss = { showVolumeSheet = false },
            onSave = { result ->
                viewModel.updateProgram(
                    p.copy(
                        mode = result.mode,
                        volumeSystem = com.example.kpkn.data.models.VolumeSystem.KPNK,
                        athleteProfileScore = result.score,
                        volumeRecommendations = result.recommendations,
                    ),
                )
                viewModel.saveVolumeCalibrationOriginals(result)
                showVolumeSheet = false
            },
        )
    }
    if (showPlanSheet) {
        ProgramPlanSheet(summary = planSummary, onDismiss = { showPlanSheet = false })
    }
    exerciseSwapRequest?.let { request ->
        com.example.kpkn.screens.programdetail.design.ProgramSwapSheet(request.sourceName,request.options,
            request.requiresEquipmentConfirmation,exerciseSwapPreview?.result?.programDecisions?.lastOrNull()?.detail,
            exerciseSwapBusy,viewModel::previewExerciseSwap,viewModel::confirmExerciseSwap,
            viewModel::clearExerciseSwapPreview,viewModel::closeExerciseSwap)
    }
    if (showPlannedLoad) plannedLoadProjection?.let { projection ->
        com.example.kpkn.screens.programdetail.design.ProgramPlannedLoadSheet(projection) { showPlannedLoad = false }
    }
    continuationPreview?.let { preview ->
        com.example.kpkn.screens.programdetail.design.ProgramContinuationSheet(preview,continuationBusy,
            viewModel::confirmContinuation,viewModel::clearContinuationPreview)
    }
    blockReviewPreview?.let { preview ->
        com.example.kpkn.screens.programdetail.design.ProgramBlockReviewSheet(preview, blockReviewBusy,
            viewModel::confirmBlockReview, viewModel::clearBlockReviewPreview)
    }
    if (showDecisions) {
        ProgramDecisionsSheet(decisions = progressReport?.decisions.orEmpty(), onDismiss = { showDecisions = false })
    }
    if (showWeeklyCheckin) pendingWeeklyCheckin?.let { request ->
        WeeklyCheckinSheet(
            request = request,
            previewDetail = weeklyCheckinPreview?.takeIf { it.answer.weekId == request.weekId && it.answer.programRunId == request.programRunId }
                ?.result?.programDecisions?.lastOrNull()?.detail,
            busy = weeklyCheckinBusy,
            onPreview = viewModel::previewWeeklyCheckin, onConfirm = viewModel::confirmWeeklyCheckin,
            onEdit = viewModel::clearWeeklyCheckinPreview, onOmit = viewModel::omitWeeklyCheckin,
            onDisable = { viewModel.disableWeeklyCheckin(); showWeeklyCheckin = false },
            onDismiss = { viewModel.clearWeeklyCheckinPreview(); showWeeklyCheckin = false },
        )
    }
    if (showMoreMenu) {
        ProgramMoreMenu(
            items = listOf(
                ProgramMenuItem(label = "Editar programa", onClick = openEdit),
                ProgramMenuItem(label = "Historial", onClick = { viewModel.setStructureSubTab(StructureSubTab.MACROCICLO) }),
                ProgramMenuItem(label = "Decisiones del sistema", onClick = { showDecisions = true }),
            ),
            onDismiss = { showMoreMenu = false },
        )
    }
    if (showEditSheet) {
        val draft = editDraft ?: programEditDraftOf(p)
        ProgramEditSheet(
            program = p,
            draft = draft,
            onDraftChange = { editDraft = it },
            onSave = {
                viewModel.updateProgram(
                    p.copy(name = draft.trimmedName, description = draft.trimmedDescription, coverImage = draft.cover),
                )
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false },
        )
    }
    LaunchedEffect(p.id, p.loops, p.macrocycles) {
        if (p.isSimpleTemporalProgram && p.loops.isNotEmpty()) {
            val materialized = LoopEngine.materializeLoopWeeks(p)
            if (materialized != p) viewModel.updateProgram(materialized)
        }
    }

    LaunchedEffect(p.id, activeProgramState?.currentWeekId, activeProgramState?.currentWeekInstanceId, currentWeeks) {
        val activeWeekId = activeProgramState?.takeIf { it.programId == p.id }?.currentWeekId ?: return@LaunchedEffect
        val templateId = ProgramProgressEngine.templateWeekIdFromInstance(activeWeekId) ?: activeWeekId
        val instanceId = activeProgramState?.currentWeekInstanceId
        val loopWeek = currentWeeks.firstOrNull { week ->
            week.isLoopWeek && (week.id == activeWeekId || week.id == templateId || week.id == instanceId)
        } ?: return@LaunchedEffect
        if (notifiedLoopWeekId == loopWeek.id) return@LaunchedEffect
        notifiedLoopWeekId = loopWeek.id
        snackbarHostState.showKpknSnackbar("Ciclo activo: ${loopWeek.name}. Ya puedes programar sus sesiones.", SnackbarType.SUGGESTION)
        LoopNotificationManager(context).notifyLoopActive(p.name, loopWeek.name)
    }

    if (showVolumeSetupNotice) {
        KpknAlertDialog(
            onDismissRequest = {
                viewModel.markVolumeSetupPromptSeen()
                showVolumeSetupNotice = false
            },
            title = { Text("Calibrar volumen del programa", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Para ajustar recomendaciones por músculo y activar automatizaciones de volumen, completa tu calibración inicial ahora."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.markVolumeSetupPromptSeen()
                    showVolumeSetupNotice = false
                    openVolumeSheetToken++
                }) {
                    Text("Calibrar ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.markVolumeSetupPromptSeen()
                    showVolumeSetupNotice = false
                }) {
                    Text("Más tarde")
                }
            },
        )
    }

    // La unidad es la de Ajustes, una sola en toda la app: las hojas de máximos no tienen conmutador. Lo guardado son kg.
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val settingsUnit = if (settings.weightUnit == WeightUnit.LBS) MarksMath.UNIT_LB else MarksMath.UNIT_KG
    if (showOneRmDialog) {
        // CAL5: el test pide solo los levantamientos del programa (Smolov: la sentadilla; con press militar, también ese) y se precarga con
        // las series «Test» registradas. La persona confirma o corrige cada valor: nada se guarda hasta «Guardar y continuar».
        val testLifts = remember(p.id, p.sourceRecipe) { viewModel.oneRmTestLifts() }
        val testPrefill = remember(p.id, showOneRmDialog) { viewModel.oneRmTestPrefill() }
        LaunchedEffect(p.id, showOneRmDialog) {
            squat1RmKg = squat1RmKg ?: testPrefill[com.example.kpkn.data.protocols.LiftSlot.SQUAT]
            bench1RmKg = bench1RmKg ?: testPrefill[com.example.kpkn.data.protocols.LiftSlot.BENCH]
            deadlift1RmKg = deadlift1RmKg ?: testPrefill[com.example.kpkn.data.protocols.LiftSlot.DEADLIFT]
            overhead1RmKg = overhead1RmKg ?: testPrefill[com.example.kpkn.data.protocols.LiftSlot.OVERHEAD]
        }
        val marks = buildMap<LiftMark, Double> {
            squat1RmKg?.let { put(LiftMark.SQUAT, it) }
            bench1RmKg?.let { put(LiftMark.BENCH, it) }
            deadlift1RmKg?.let { put(LiftMark.DEADLIFT, it) }
            overhead1RmKg?.let { put(LiftMark.OVERHEAD_PRESS, it) }
        }
        fun markOf(lift: com.example.kpkn.data.protocols.LiftSlot): LiftMark = when (lift) {
            com.example.kpkn.data.protocols.LiftSlot.SQUAT -> LiftMark.SQUAT
            com.example.kpkn.data.protocols.LiftSlot.BENCH -> LiftMark.BENCH
            com.example.kpkn.data.protocols.LiftSlot.DEADLIFT -> LiftMark.DEADLIFT
            com.example.kpkn.data.protocols.LiftSlot.OVERHEAD -> LiftMark.OVERHEAD_PRESS
        }
        ProgramOneRmSheet(
            valuesKg = marks,
            unit = settingsUnit,
            onValueKg = { lift, kg ->
                when (lift) {
                    LiftMark.SQUAT -> squat1RmKg = kg
                    LiftMark.BENCH -> bench1RmKg = kg
                    LiftMark.DEADLIFT -> deadlift1RmKg = kg
                    LiftMark.OVERHEAD_PRESS -> overhead1RmKg = kg
                    else -> Unit
                }
            },
            onSave = { squat, bench, deadlift ->
                viewModel.recordPendingOneRmTest(squat, bench, deadlift)
                showOneRmDialog = false
            },
            onDismiss = { showOneRmDialog = false },
            lifts = testLifts.map(::markOf),
            prefilled = testPrefill.isNotEmpty(),
            onSaveMarks = { values ->
                viewModel.recordPendingOneRmTestMarks(
                    testLifts.mapNotNull { lift -> values[markOf(lift)]?.let { lift to it } }.toMap(),
                )
                showOneRmDialog = false
            },
        )
    }

    if (showTmEditor) {
        TrainingMaxWizard(
            initial = p.powerliftingProfile,
            autoEstimate = viewModel.estimatedTmProfile(),
            trainingMaxPercent = p.sourceRecipe?.trainingMaxPercent ?: 0.90,
            confirmLabel = "Guardar TM",
            unit = settingsUnit,
            onDismiss = { showTmEditor = false },
            onConfirm = { profile ->
                viewModel.updatePowerliftingProfile(profile)
                showTmEditor = false
            },
        )
    }

    // «Ver cómo funciona» de la tarjeta de plan: la misma hoja de la biblioteca, sin botón primario.
    val planInfoEntry = remember(planInfoEntryId) { planInfoEntryId?.let(PersonalizedPlanCatalog::find) }
    if (planInfoEntry != null) {
        PlanInfoSheet(
            entry = planInfoEntry,
            mode = PlanInfoMode.READ_ONLY,
            onDismiss = { planInfoEntryId = null },
        )
    }
}

// ─── Training Panel ─────────────────────────────────────────────────────────

@Composable
private fun TrainingPanel(
    viewModel: ProgramDetailViewModel,
    program: Program,
    roadmapBlocks: List<com.example.kpkn.domain.training.RoadmapBlock>,
    currentWeeks: List<com.example.kpkn.domain.training.WeekWithMeta>,
    displayedSessions: List<Session>,
    selectedWeekMeta: com.example.kpkn.domain.training.WeekWithMeta?,
    selectedBlockId: String?,
    selectedWeekId: String?,
    structureSubTab: StructureSubTab,
    onStartWorkout: (Session, Program) -> Unit,
    onEditSession: (String) -> Unit,
    onCreateSession: (String, String, Int, Int, Int) -> Unit,
    onOpenProgram: (String) -> Unit = {},
    onOpenSplitPage: () -> Unit = {},
    onOpenOneRmDialog: () -> Unit = {},
    onOpenTmEditor: () -> Unit = {},
) {
    val volumeEvidence by viewModel.volumeEvidence.collectAsStateWithLifecycle()
    val closedCycleEvidence by viewModel.closedCycleEvidence.collectAsStateWithLifecycle()
    val volumeProfileResponses by viewModel.volumeProfileResponses.collectAsStateWithLifecycle()
    val planningVolumeTargetBusy by viewModel.planningVolumeTargetBusy.collectAsStateWithLifecycle()
    val planningRangeProposals by viewModel.planningRangeProposals.collectAsStateWithLifecycle()
    val planningRangePreview by viewModel.planningRangePreview.collectAsStateWithLifecycle()
    var showPlanningVolumeTargets by remember(program.id, structureSubTab) { mutableStateOf(false) }
    val currentWeekId by viewModel.activeProgramState.collectAsState()
    val editorUiState by viewModel.uiState.collectAsState()
    val blockTransitionBanner by viewModel.blockTransitionBanner.collectAsState()
    val showSimpleCalendarizationSheet by viewModel.showSimpleCalendarizationSheet.collectAsState()
    val calendarizationStartDate by viewModel.calendarizationStartDate.collectAsState()
    val calendarizationEndDate by viewModel.calendarizationEndDate.collectAsState()
    val calendarizationStartDayOfWeek by viewModel.calendarizationStartDayOfWeek.collectAsState()
    val calendarizationTrainingDays by viewModel.calendarizationTrainingDays.collectAsState()
    val restoreBlockedSessionIds by viewModel.restoreBlockedSessionIds.collectAsState()
    val nativeProgressionCard by viewModel.nativeProgressionCard.collectAsState()
    var copiedRoadmapWeekId by remember(program.id) { mutableStateOf<String?>(null) }
    var showCopyWeekDialog by remember { mutableStateOf(false) }
    val allProgramWeeks = remember(program, roadmapBlocks) {
        com.example.kpkn.domain.training.ProgramDetailHelpers.getAllWeeks(roadmapBlocks, program)
    }
    var pendingDeleteSession by remember { mutableStateOf<Session?>(null) }
    // Hoja «Editar semana», menú de la semana (copiar y pegar) y días desplegados (en el VM: sobreviven a Estructura).
    var weekEdit by remember(program.id) { mutableStateOf<WeekEditDraft?>(null) }
    var weekMenu by remember { mutableStateOf<Pair<String, Rect>?>(null) }
    val completedSessionIds by viewModel.completedSessionIds.collectAsState()
    val expandedWeekDays by viewModel.expandedWeekDays.collectAsState()
    val activeWeekId = currentWeekId?.currentWeekId
    // Módulo «Fases»: cada bloque con sus semanas numeradas en orden global. Hecha = antes de la semana en curso.
    val phaseBlocks = remember(roadmapBlocks, allProgramWeeks, activeWeekId, program) {
        val currentIndex = allProgramWeeks.indexOfFirst { it.id == activeWeekId }
        roadmapBlocks.map { block ->
            val weeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmapBlocks, program)
            val phase = programPhaseOf(
                goal = block.goal,
                hasCompetitionWeek = weeks.any { it.keyDateType == KeyDateType.COMPETITION },
            )
            ProgramPhaseBlockUi(
                id = block.id,
                eyebrow = programPhaseEyebrow(block.name, phase, block.goal),
                phase = phase,
                weeks = weeks.map { week ->
                    val index = allProgramWeeks.indexOfFirst { it.id == week.id }
                    ProgramPhaseWeekUi(
                        id = week.id,
                        number = index + 1,
                        isCurrent = week.id == activeWeekId,
                        isDone = currentIndex >= 0 && index < currentIndex,
                    )
                },
            )
        }
    }
    // Programa simple: sin módulo; si tiene varias semanas, la fila de círculos sin caja.
    val simpleWeeks = remember(currentWeeks, activeWeekId) {
        val currentIndex = currentWeeks.indexOfFirst { it.id == activeWeekId }
        currentWeeks.mapIndexed { index, week ->
            ProgramPhaseWeekUi(
                id = week.id,
                number = index + 1,
                isCurrent = week.id == activeWeekId,
                isDone = currentIndex >= 0 && index < currentIndex,
            )
        }
    }
    // Elegir una semana de cualquier bloque: si es de otro bloque, primero se elige ese bloque.
    fun selectPhaseWeek(weekId: String) {
        val block = phaseBlocks.firstOrNull { b -> b.weeks.any { it.id == weekId } }
        if (block != null && block.id != selectedBlockId) viewModel.selectBlock(block.id)
        viewModel.selectWeek(weekId)
    }
    // «+» de un bloque: la semana nueva va a ese bloque.
    fun addWeekToBlock(blockId: String) {
        if (blockId != selectedBlockId) viewModel.selectBlock(blockId)
        viewModel.addWeekToSelectedAdvancedBlock("", null)
    }
    // Pegar la semana copiada: sin conflictos se copia de una vez; si hay sesiones que reemplazar, la hoja decide.
    fun pasteWeek(targetWeekId: String) {
        copiedRoadmapWeekId?.let { sourceWeekId ->
            val conflicts = viewModel.previewWeekCopyConflicts(sourceWeekId, setOf(targetWeekId))
            if (conflicts.isEmpty()) {
                viewModel.copyWeekSessions(
                    sourceWeekId = sourceWeekId,
                    targetWeekIds = setOf(targetWeekId),
                    replaceWeekIds = emptySet(),
                )
            } else {
                showCopyWeekDialog = true
            }
        }
    }

    fun focusWeek(blockId: String, weekId: String) {
        viewModel.selectBlock(blockId)
        viewModel.selectWeek(weekId)
        viewModel.setStructureSubTab(StructureSubTab.SEMANA)
    }

    // Sin bloques, Semana no tiene nada que enseñar: la salida es crear el primer bloque en Estructura (no se queda vacía).
    if (roadmapBlocks.isEmpty() && structureSubTab == StructureSubTab.SEMANA) {
        ProgramEmptyStructure(onAddFirstBlock = { viewModel.setStructureSubTab(StructureSubTab.MACROCICLO) })
        return
    }

    Column {
        Spacer(modifier = Modifier.height(4.dp))

        if (structureSubTab != StructureSubTab.VOLUMEN && roadmapBlocks.isNotEmpty()) {
            if (structureSubTab == StructureSubTab.SEMANA) {
                // Semana: el módulo «Fases» (o la fila de semanas de un programa simple). Estructura lista sus bloques aparte (P3b).
                if (!program.isSimpleProgram) {
                    ProgramPhasesModule(
                        blocks = phaseBlocks,
                        selectedWeekId = selectedWeekId,
                        onSelectWeek = { weekId -> selectPhaseWeek(weekId) },
                        onLongPressWeek = { weekId, anchor -> weekMenu = weekId to anchor },
                        onAddWeek = { blockId -> addWeekToBlock(blockId) },
                        modifier = Modifier.padding(horizontal = 16.dp).kpknEntrance("programdetail.semana", 0),
                    )
                } else if (simpleWeeks.size > 1) {
                    ProgramWeekStrip(
                        weeks = simpleWeeks,
                        selectedWeekId = selectedWeekId,
                        onSelectWeek = { weekId -> viewModel.selectWeek(weekId) },
                        onLongPressWeek = { weekId, anchor -> weekMenu = weekId to anchor },
                        onAdd = { viewModel.addWeekToSimpleProgram() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            blockTransitionBanner?.let { banner ->
                Spacer(modifier = Modifier.height(12.dp))
                val explicit = banner.requiresExplicitConfirmation
                val isAutoreg = explicit && banner.pendingType == PendingProgramActionType.CONFIRM_AUTOREGULATION
                ProgramTransitionModule(
                    message = banner.message,
                    primaryLabel = when {
                        isAutoreg -> "Ver propuesta"
                        explicit -> "Registrar resultado"
                        else -> null
                    },
                    onPrimary = {
                        when {
                            // R-03: «Ver propuesta» solo lleva a la pestaña Semana, donde las propuestas se aplican una a una.
                            isAutoreg -> viewModel.setStructureSubTab(StructureSubTab.SEMANA)
                            else -> onOpenOneRmDialog()
                        }
                    },
                    secondaryLabel = when {
                        explicit && !isAutoreg -> "Omitir"
                        else -> null
                    },
                    onSecondary = {
                        viewModel.skipPendingOneRmTest()
                    },
                    dismissLabel = if (explicit) "Más tarde: el programa espera tu decisión" else "Entendido",
                    onDismiss = { viewModel.dismissBlockTransitionBanner() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (structureSubTab == StructureSubTab.SEMANA) {
            ExerciseTrainingMaxPanel(program, onConfirm = viewModel::updateExerciseTrainingMaxAndAwait, modifier = Modifier.padding(horizontal = 16.dp))
            AutoregulationProposalsCard(
                program = program,
                nativeCard = nativeProgressionCard,
                onAccept = { viewModel.acceptAutoregulation() },
                onAcceptProposal = { viewModel.acceptAutoregulation(it) },
                onReject = { viewModel.rejectAutoregulation() },
                onAcceptNative = { viewModel.acceptNativeProgressionProposal(it) },
                onRejectNative = { viewModel.rejectNativeProgressionProposal(it) },
                onDismissNativeNotice = { viewModel.dismissNativeProgressionNotice(it) },
                onOpenTm = onOpenTmEditor,
                onRematerialize = { viewModel.rematerializePending() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            // Insignia: las progresiones por revisar también se avisan desde las otras pestañas.
            nativeProgressionCard.pendingLabel?.let { label ->
                NativeProgressionBadge(
                    label = label,
                    onClick = { viewModel.setStructureSubTab(StructureSubTab.SEMANA) },
                )
            }
        }

        when (structureSubTab) {
            StructureSubTab.SEMANA -> {
                selectedWeekMeta?.let { week ->
                    // «Semana 3 · Acumulación»; en un programa simple de una sola semana, «Semana que se repite».
                    val repeating = program.isSimpleProgram && currentWeeks.size <= 1
                    val weekIndex = if (program.isSimpleProgram) {
                        currentWeeks.indexOfFirst { it.id == week.id }
                    } else {
                        allProgramWeeks.indexOfFirst { it.id == week.id }
                    }
                    val phase = week.mesoGoal
                        .takeIf { !program.isSimpleProgram && it != com.example.kpkn.data.models.MesocycleGoal.CUSTOM }
                        ?.label
                    ProgramWeekHeader(
                        title = if (repeating) "Semana que se repite" else listOfNotNull("Semana ${weekIndex + 1}", phase).joinToString(" · "),
                        // Un programa simple no repite el nombre de la semana («Semana repetible»): dice sus sesiones y su duración.
                        name = if (repeating) {
                            repeatingWeekSubtitle(
                                sessionMinutes = displayedSessions.map {
                                    com.example.kpkn.domain.training.SessionDurationEstimator.estimate(it).totalMinutes
                                },
                            )
                        } else {
                            week.name.takeUnless { WeekDefaultName.matches(it.trim()) }
                        },
                        description = week.description,
                        onEdit = { weekEdit = WeekEditDraft(weekId = week.id, name = week.name, description = week.description.orEmpty()) },
                        onChangeSplit = onOpenSplitPage,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                val swapContext = LocalContext.current.applicationContext
                DayView(
                    program = program,
                    modifier = Modifier.tourAnchor(TourAnchors.TRAINING_SEMANA),
                    isSimpleProgram = program.isSimpleTemporalProgram,
                    isCalendarized = ProgramCalendarEngine.isCalendarized(program),
                    selectedWeek = selectedWeekMeta,
                    sessions = displayedSessions,
                    onEditSession = onEditSession,
                    onSwapExercise = { sessionId,exerciseId ->
                        viewModel.openExerciseSwap(sessionId,exerciseId) {
                            com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache.getOrLoad(swapContext).catalog
                        }
                    },
                    onAddSession = { dayId ->
                        val block = roadmapBlocks.find { it.id == selectedBlockId }
                        val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                        val weekId = selectedWeekId
                        if (block != null && weekMeta != null && weekId != null) {
                            val sessionId = java.util.UUID.randomUUID().toString()
                            viewModel.addSession(
                                macroIndex = block.macroIndex,
                                mesoIndex = weekMeta.mesoIndex,
                                weekId = weekId,
                                session = createBlankRoadmapSession(sessionId, dayId),
                            )
                            onCreateSession(
                                sessionId,
                                weekId,
                                block.macroIndex,
                                weekMeta.mesoIndex,
                                dayId,
                            )
                        }
                    },
                    onDeleteSession = { sessionId ->
                        pendingDeleteSession = displayedSessions.firstOrNull { it.id == sessionId }
                    },
                    onStartWorkout = { onStartWorkout(it, program) },
                    onApplySessionsLayout = { updatedSessions ->
                        val weekId = selectedWeekId ?: ""
                        if (weekId.isNotEmpty()) {
                            viewModel.replaceWeekSessions(weekId, updatedSessions)
                        }
                    },
                    onUpdateStartDay = { startDay, scope, sessionMode ->
                        viewModel.updateStartDay(startDay, scope, sessionMode)
                    },
                    onUpdateWeekMetadata = { weekId, name, description ->
                        viewModel.updateWeekMetadata(weekId, name, description)
                    },
                    onUpdateTrainingDayDate = { weekId, dayOfWeek, isoDate ->
                        viewModel.updateWeekTrainingDayDate(weekId, dayOfWeek, isoDate)
                    },
                    onToggleOptionalConfirmation = { dayIso, sessionId ->
                        viewModel.toggleOptionalSessionConfirmation(dayIso, sessionId)
                    },
                    onRestoreManualSessionFromPlan = viewModel::restoreManualSessionFromPlan,
                    restoreBlockedSessionIds = restoreBlockedSessionIds,
                    completedSessionIds = completedSessionIds,
                    expandedDayIds = expandedWeekDays?.takeIf { it.first == selectedWeekMeta?.id }?.second,
                    onExpandedDayIdsChange = { days ->
                        selectedWeekMeta?.id?.let { weekId -> viewModel.setExpandedWeekDays(weekId, days) }
                    },
                )
            }
            StructureSubTab.MACROCICLO -> MacrocycleEditor(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
                onFocusWeek = ::focusWeek,
                // Bloques (P3b): las acciones de bloque, semana y copia, con las mismas llamadas al ViewModel de siempre.
                selectedWeekId = selectedWeekId,
                copiedWeekId = copiedRoadmapWeekId,
                onCopyWeek = { copiedRoadmapWeekId = it },
                onPasteWeek = ::pasteWeek,
                onUpdateBlock = { blockId, name, description, goal, scheme ->
                    viewModel.updateBlockMetadata(
                        blockId = blockId,
                        name = name,
                        description = description,
                        goal = goal,
                        progressionScheme = scheme,
                    )
                },
                blockProgressionPreview = { blockId -> viewModel.previewBlockProgression(blockId) },
                onDeleteBlock = { blockId -> viewModel.deleteBlockFromRoadmap(blockId) },
                onUpdateWeek = { weekId, name, description -> viewModel.updateWeekMetadata(weekId, name, description) },
                onDeleteWeek = { weekId -> viewModel.deleteWeekFromRoadmap(weekId) },
                onAddSimpleWeek = { viewModel.addWeekToSimpleProgram() },
                onApplyProgramTemplate = { viewModel.applyProgramTemplate(it) },
                onApplyProgramTemplateOverwrite = { template, overwrite ->
                    viewModel.applyProgramTemplate(template, overwrite)
                },
                onApplyProtocol = { protocol, overwrite ->
                    viewModel.applyProtocolOverwrite(protocol, overwrite)
                },
                programSnapshots = viewModel.programSnapshots.collectAsStateWithLifecycle().value,
                onRestoreProgramSnapshot = { viewModel.restoreProgramSnapshot(it) },
                onRefreshProgramSnapshots = { viewModel.refreshProgramSnapshots() },
                showSimpleCalendarizationSheet = showSimpleCalendarizationSheet,
                onShowSimpleCalendarizationSheetChange = { viewModel.setShowSimpleCalendarizationSheet(it) },
                calendarizationStartDate = calendarizationStartDate,
                onCalendarizationStartDateChange = { viewModel.setCalendarizationStartDate(it) },
                calendarizationEndDate = calendarizationEndDate,
                onCalendarizationEndDateChange = { viewModel.setCalendarizationEndDate(it) },
                calendarizationStartDayOfWeek = calendarizationStartDayOfWeek,
                onCalendarizationStartDayOfWeekChange = { viewModel.setCalendarizationStartDayOfWeek(it) },
                calendarizationTrainingDays = calendarizationTrainingDays,
                onCalendarizationTrainingDaysChange = { viewModel.setCalendarizationTrainingDays(it) },
                onApplySimpleCalendarizedBreak = { viewModel.applySimpleCalendarizedBreak() },
                onCalendarizeSimpleCycle = { viewModel.calendarizeSimpleCycle() },
                onRecoverCyclicProgram = { viewModel.recoverCyclicProgram() },
                onStartFreshCyclicProgram = { viewModel.startFreshCyclicProgram() },
                macrocycleRoadmapExpanded = editorUiState.macrocycleRoadmapExpanded,
                onMacrocycleRoadmapExpandedChange = { viewModel.setMacrocycleRoadmapExpanded(it) },
                macrocycleKeyDatesSheetOpen = editorUiState.macrocycleKeyDatesSheetOpen,
                onMacrocycleKeyDatesSheetOpenChange = { viewModel.setMacrocycleKeyDatesSheetOpen(it) },
                macrocycleLibrarySheetOpen = editorUiState.macrocycleLibrarySheetOpen,
                onMacrocycleLibrarySheetOpenChange = { viewModel.setMacrocycleLibrarySheetOpen(it) },
                macrocycleLoopsSheetOpen = editorUiState.macrocycleLoopsSheetOpen,
                onMacrocycleLoopsSheetOpenChange = { viewModel.setMacrocycleLoopsSheetOpen(it) },
                macrocycleTimelineStartDate = editorUiState.macrocycleTimelineStartDate,
                onMacrocycleTimelineStartDateChange = { viewModel.setMacrocycleTimelineStartDate(it) },
                macrocycleManualEndDate = editorUiState.macrocycleManualEndDate,
                onMacrocycleManualEndDateChange = { viewModel.setMacrocycleManualEndDate(it) },
                macrocycleCompetitionDate = editorUiState.macrocycleCompetitionDate,
                onMacrocycleCompetitionDateChange = { viewModel.setMacrocycleCompetitionDate(it) },
                onAddProgramCopy = { copy ->
                    viewModel.addProgramCopy(copy)
                    onOpenProgram(copy.id)
                },
            )
            StructureSubTab.VOLUMEN -> {
                val progLogs by viewModel.programLogs.collectAsState()
                val isActiveProg by viewModel.isActiveProgram.collectAsState()
                val discomforts by viewModel.programDiscomforts.collectAsState()
                val exDiscomfortAssoc by viewModel.exerciseDiscomfortAssociations.collectAsState()
                val analyticsReport = remember(program, progLogs) {
                    ProgramAnalyticsEngine.analyze(
                        program = program,
                        logs = progLogs,
                        exerciseCatalog = exerciseCatalogSnapshot(),
                    )
                }
                val hasCreatedSessions = remember(program) {
                    program.macrocycles
                        .flatMap { it.blocks }
                        .flatMap { it.mesocycles }
                        .flatMap { it.weeks }
                        .any { it.sessions.isNotEmpty() }
                }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ProgramVolumeCalibrationModule(volumeEvidence, program.planningVolumeTargets, closedCycleEvidence,
                        volumeProfileResponses, program.volumeCalibrationNotes, onEdit = { showPlanningVolumeTargets = true },
                        proposals = planningRangeProposals, onReviewProposals = viewModel::reviewPlanningRanges)
                }
                CompositionLocalProvider(
                    com.example.kpkn.screens.programdetail.components.LocalVolumeCalibrationSaved provides viewModel::saveVolumeCalibrationOriginals,
                ) {
                VolumeView(
                    program = program,
                    isProgramActive = isActiveProg,
                    hasCreatedSessions = hasCreatedSessions,
                    onActivateProgram = { viewModel.activateOrResume() },
                    onGoCreateSession = {
                        val firstBlock = program.macrocycles.firstOrNull()?.blocks?.firstOrNull()
                        val firstWeek = firstBlock?.mesocycles?.firstOrNull()?.weeks?.firstOrNull()
                        if (firstBlock != null && firstWeek != null) {
                            viewModel.selectBlock(firstBlock.id)
                            viewModel.selectWeek(firstWeek.id)
                            viewModel.setStructureSubTab(StructureSubTab.SEMANA)
                        } else {
                            viewModel.setStructureSubTab(StructureSubTab.MACROCICLO)
                        }
                    },
                    onApplyVolumeCalibration = { mode, athleteScore, recommendations ->
                        viewModel.updateProgram(
                            program.copy(
                                mode = mode,
                                volumeSystem = com.example.kpkn.data.models.VolumeSystem.KPNK,
                                athleteProfileScore = athleteScore,
                                volumeRecommendations = recommendations,
                            )
                        )
                    },
                    programDiscomforts = discomforts,
                    exerciseDiscomfortAssociations = exDiscomfortAssoc,
                    analyticsReport = analyticsReport,
                    programLogs = progLogs,
                )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    planningRangePreview?.let { preview ->
        PlanningRangeProposalsSheet(preview.proposals, planningVolumeTargetBusy,
            onAccept = { viewModel.resolvePlanningRanges(accept = true) },
            onReject = { viewModel.resolvePlanningRanges(accept = false) },
            onDismiss = viewModel::clearPlanningRangePreview)
    }
    if (showPlanningVolumeTargets) {
        ProgramPlanningVolumeTargetSheet(
            targets = program.planningVolumeTargets, evidence = volumeEvidence,
            cycles = closedCycleEvidence, responses = volumeProfileResponses, busy = planningVolumeTargetBusy,
            onSave = { target -> viewModel.savePlanningVolumeTarget(target) { showPlanningVolumeTargets = false } },
            onReset = { group -> viewModel.resetPlanningVolumeTarget(group) { showPlanningVolumeTargets = false } },
            onDismiss = { showPlanningVolumeTargets = false },
        )
    }
    if (showCopyWeekDialog) {
        ProgramCopyWeekSheet(
            weeks = allProgramWeeks,
            currentBlockWeekIds = currentWeeks.map { it.id }.toSet(),
            selectedWeekId = copiedRoadmapWeekId ?: selectedWeekId,
            conflictsFor = { source, targets -> viewModel.previewWeekCopyConflicts(source, targets) },
            onCopy = { source, targets, replace -> viewModel.copyWeekSessions(source, targets, replace) },
            onDismiss = { showCopyWeekDialog = false },
        )
    }

    weekEdit?.let { draft ->
        ProgramWeekEditSheet(
            name = draft.name,
            description = draft.description,
            onNameChange = { weekEdit = draft.copy(name = it) },
            onDescriptionChange = { weekEdit = draft.copy(description = it) },
            onSave = {
                viewModel.updateWeekMetadata(draft.weekId, draft.name.trim(), draft.description.trim().ifBlank { null })
                weekEdit = null
            },
            onDismiss = { weekEdit = null },
        )
    }

    weekMenu?.let { (weekId, anchor) ->
        ProgramItemMenu(
            items = buildList<ProgramAnchoredMenuItem> {
                add(ProgramAnchoredMenuItem(label = "Copiar semana", onClick = { copiedRoadmapWeekId = weekId }))
                if (copiedRoadmapWeekId != null && copiedRoadmapWeekId != weekId) {
                    add(ProgramAnchoredMenuItem(label = "Pegar semana", onClick = { pasteWeek(weekId) }))
                }
            },
            anchor = anchor,
            onDismiss = { weekMenu = null },
        )
    }

    pendingDeleteSession?.let { sessionToDelete ->
        ProgramDeleteSessionDialog(
            onConfirm = {
                val block = roadmapBlocks.find { it.id == selectedBlockId }
                val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                if (block != null && weekMeta != null) {
                    viewModel.deleteSession(sessionToDelete.id, block.macroIndex, weekMeta.mesoIndex, selectedWeekId ?: "")
                }
                pendingDeleteSession = null
            },
            onDismiss = { pendingDeleteSession = null },
        )
    }
}

/** Borrador de la hoja «Editar semana» (P2): la semana que se edita y lo que hay escrito. */
private data class WeekEditDraft(val weekId: String, val name: String, val description: String)

/** Nombre por defecto de una semana («Semana 3»): no se repite como subtítulo en la cabecera. */
private val WeekDefaultName = Regex("Semana \\d+")

private fun createBlankRoadmapSession(sessionId: String, dayOfWeek: Int): Session =
    Session(
        id = sessionId,
        name = "Sesión ${dayLabelFull(dayOfWeek)}",
        lastModifiedAtMs = System.currentTimeMillis(),
        dayOfWeek = dayOfWeek,
        isMainSession = true,
    )

private fun dayLabelFull(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "día"
}

/** Insignia «N progresiones por revisar» para las pestañas donde no se ve la tarjeta de propuestas. */
@Composable
private fun NativeProgressionBadge(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("native_progression_badge"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "VER",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Los avisos del ViewModel llegan como texto: los fallos y rechazos se pintan de rojo, «No se
 * aplicó…» (la propuesta ya no tenía dónde aplicarse) es una sugerencia y el resto, éxito.
 */
internal fun snackbarTypeFor(message: String): SnackbarType = when {
    message.startsWith("No se pudo") || message.startsWith("Termina o descarta") -> SnackbarType.DANGER
    message.startsWith("No se aplicó") -> SnackbarType.SUGGESTION
    else -> SnackbarType.SUCCESS
}

/**
 * true si la receta trabaja con el TM del atleta y la tarjeta de propuestas debe mostrarlo y dejar
 * editarlo: porque su progresión lo mueve (la del método: `CycleIncrement` y `WeeklyKg`; la de
 * rendimiento: `TopSetPr`, AMRAP y serie al máximo), porque declara el gancho AMRAP sobre el TM o, aunque
 * no progrese, porque alguna serie de trabajo de un levantamiento se prescribe como porcentaje del TM
 * (`LoadBasis.PERCENT_TM`). Las recetas con porcentaje del 1RM no cuentan: su base no es el TM.
 */
internal fun usesTrainingMax(recipe: com.example.kpkn.data.protocols.TrainingPlanRecipe): Boolean {
    if (recipe.autoregulationHooks.any { it.kind == com.example.kpkn.data.protocols.AutoregulationHookKind.AMRAP_TM }) return true
    val movedByProgression = when (recipe.progression) {
        is com.example.kpkn.data.protocols.ProgressionRule.CycleIncrement,
        is com.example.kpkn.data.protocols.ProgressionRule.WeeklyKg,
        is com.example.kpkn.data.protocols.ProgressionRule.TopSetPr,
        com.example.kpkn.data.protocols.ProgressionRule.RepMaxAutoregulated,
        is com.example.kpkn.data.protocols.ProgressionRule.AmrapDrivenTm,
        is com.example.kpkn.data.protocols.ProgressionRule.RepTargetDrivenTm,
        is com.example.kpkn.data.protocols.ProgressionRule.RepsOverStandardTm,
        is com.example.kpkn.data.protocols.ProgressionRule.StagedVolume,
        is com.example.kpkn.data.protocols.ProgressionRule.ApreXrm,
        -> true
        is com.example.kpkn.data.protocols.ProgressionRule.WeeklyPercent,
        com.example.kpkn.data.protocols.ProgressionRule.None,
        -> false
    }
    if (movedByProgression) return true
    return recipe.weeks.any { week ->
        week.days.any { day ->
            day.slots.any { slot ->
                slot.lift.liftSlot != null && slot.sets.any { set ->
                    !set.isWarmup && set.percent != null &&
                        set.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM
                }
            }
        }
    }
}

