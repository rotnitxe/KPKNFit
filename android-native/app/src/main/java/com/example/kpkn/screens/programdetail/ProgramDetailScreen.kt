package com.example.kpkn.screens.programdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.kpkn.screens.stories.ProgramShareSheet
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.ProgramAnalyticsEngine
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.BlockTransitionEngine
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.programdetail.components.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.services.workout.LoopNotificationManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.domain.training.HomeSessionResolver.allWeekLocations
import com.example.kpkn.screens.onboarding.design.WizardDarkSystemBars
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.design.ProgramDecisionsSheet
import com.example.kpkn.screens.programdetail.design.WeeklyCheckinSheet
import com.example.kpkn.screens.programdetail.design.ProgramPlanningVolumeTargetSheet
import com.example.kpkn.screens.programdetail.design.PlanningRangeProposalsSheet
import com.example.kpkn.screens.programdetail.design.ProgramDetailPlaceholder
import com.example.kpkn.screens.programdetail.design.ProgramEditDraft
import com.example.kpkn.screens.programdetail.design.ProgramEditSheet
import com.example.kpkn.screens.programdetail.design.ProgramEmptyStructure
import com.example.kpkn.screens.programdetail.design.ProgramFloatingBar
import com.example.kpkn.screens.programdetail.design.ProgramFloatingBarContentInset
import com.example.kpkn.screens.programdetail.design.ProgramHeader
import com.example.kpkn.screens.programdetail.design.ProgramHeaderActions
import com.example.kpkn.screens.programdetail.design.ProgramHeaderPrimary
import com.example.kpkn.screens.programdetail.design.ProgramHeaderState
import com.example.kpkn.screens.programdetail.design.ProgramHeaderStatus
import com.example.kpkn.screens.programdetail.design.ProgramIslandBottom
import com.example.kpkn.screens.programdetail.design.ProgramMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramMoreMenu
import com.example.kpkn.screens.programdetail.design.ProgramOneRmSheet
import com.example.kpkn.screens.programdetail.design.ProgramPendingDetailSheet
import com.example.kpkn.screens.programdetail.design.ProgramPendingItem
import com.example.kpkn.screens.programdetail.design.ProgramPendingKind
import com.example.kpkn.screens.programdetail.design.ProgramPendingSheet
import com.example.kpkn.screens.programdetail.design.ProgramPlanSheet
import com.example.kpkn.screens.programdetail.design.ProgramRibbonWeek
import com.example.kpkn.screens.programdetail.design.ProgramScheduleModule
import com.example.kpkn.screens.programdetail.design.programEditDraftOf
import com.example.kpkn.screens.programdetail.design.programHeaderPrimaryOf
import com.example.kpkn.screens.programdetail.design.programHeaderStatusOf
import com.example.kpkn.screens.programdetail.design.programHeadlineMeta
import com.example.kpkn.screens.programdetail.design.programModeLabel
import com.example.kpkn.screens.programdetail.design.programNextSessionOf
import com.example.kpkn.screens.programdetail.design.programPhaseColor
import com.example.kpkn.screens.programdetail.design.programTodayAlsoLine
import com.example.kpkn.screens.programdetail.design.programProgressLabel
import com.example.kpkn.screens.programdetail.design.rememberPassedLine
import com.example.kpkn.ui.components.rememberProgramCover
import com.example.kpkn.ui.design.KpknStatusVeil
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.KpknSheet
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
import com.example.kpkn.screens.programdetail.design.programWeekStats
import com.example.kpkn.screens.programdetail.design.ProgramWeeksSheet
import com.example.kpkn.screens.programdetail.design.weekDateRange
import com.example.kpkn.screens.programdetail.design.weekNavigatorSubtitle
import com.example.kpkn.screens.programdetail.design.weekNavigatorTitle
import com.example.kpkn.screens.programdetail.design.weekSheetSubtitle
import com.example.kpkn.screens.programdetail.design.progress.ProgressLiftsModule
import com.example.kpkn.screens.programdetail.design.progress.ProgressScope
import com.example.kpkn.screens.programdetail.design.progress.ProgressScopeChips
import com.example.kpkn.screens.programdetail.design.progress.ProgramRangesSheet
import com.example.kpkn.screens.programdetail.design.progress.ProgramMonthlySummariesModule
import com.example.kpkn.screens.monthsummary.MonthlySummaryScreen
import com.example.kpkn.screens.monthsummary.summaryKey
import com.example.kpkn.screens.monthsummary.summaryMonths
import com.example.kpkn.screens.stories.MonthShareSheet
import com.example.kpkn.data.preferences.monthlySummaryStore
import com.example.kpkn.domain.training.progress.MonthlyReportEngine
import com.example.kpkn.ui.components.KpknPortal
import java.time.YearMonth
import com.example.kpkn.screens.programdetail.design.progress.rangesOriginOf
import com.example.kpkn.screens.programdetail.design.progress.ProgressSummaryModule
import com.example.kpkn.screens.programdetail.design.progress.ProgressWorkModule
import com.example.kpkn.screens.programdetail.design.progress.buildProgramProgress
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
    onOpenCompetition: ((String) -> Unit)? = null,
) {
    val program by viewModel.program.collectAsState()
    val progressReport by viewModel.progressReport.collectAsStateWithLifecycle()
    val pendingMissedSessions by viewModel.pendingMissedSessions.collectAsStateWithLifecycle()
    val inactivityNotice by viewModel.inactivityNotice.collectAsStateWithLifecycle()
    val schedulePreview by viewModel.schedulePreview.collectAsStateWithLifecycle()
    val scheduleBusy by viewModel.scheduleBusy.collectAsStateWithLifecycle()
    val competitionState by viewModel.competitionState.collectAsStateWithLifecycle()
    val competitionBusy by viewModel.competitionBusy.collectAsStateWithLifecycle()
    val meetResultPreview by viewModel.meetResultPreview.collectAsStateWithLifecycle()
    val competitionPlanSavedRecordId by viewModel.competitionPlanSavedRecordId.collectAsStateWithLifecycle()
    var planningCompetition by remember { mutableStateOf<ProgramCompetitionState?>(null) }
    var meetDayAttempts by remember { mutableStateOf<List<com.example.kpkn.data.models.PlannedAttempt>?>(null) }
    LaunchedEffect(competitionPlanSavedRecordId) {
        if (competitionPlanSavedRecordId != null) {
            if (planningCompetition?.record?.id == competitionPlanSavedRecordId) planningCompetition = null
            viewModel.consumeCompetitionPlanSaved()
        }
    }
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
    // Lo que antes pintaban la pestaña Semana y la portada: el banner de transición, las progresiones y los máximos (hoja «Pendiente»).
    val blockTransitionBanner by viewModel.blockTransitionBanner.collectAsState()
    val nativeProgressionCard by viewModel.nativeProgressionCard.collectAsState()
    val completedSessionIds by viewModel.completedSessionIds.collectAsState()
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
    var openVolumeSheetToken by remember { mutableIntStateOf(0) }
    var notifiedLoopWeekId by remember { mutableStateOf<String?>(null) }
    var showSplitPage by rememberSaveable { mutableStateOf(false) }
    // Semana copiada (para pegarla en otra): común a las pestañas, que AnimatedContent recrea al cambiar.
    val copiedWeekState = rememberSaveable(program?.id) { mutableStateOf<String?>(null) }
    var showOneRmDialog by rememberSaveable { mutableStateOf(false) }
    var showTmEditor by rememberSaveable { mutableStateOf(false) }
    var showVolumeSheet by rememberSaveable { mutableStateOf(false) }
    var showPlanSheet by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    // «Compartir en historias»: desde el botón de la portada o desde «Más opciones» (⋯).
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    // «Pendiente»: la lista (desde la campana o ⋯) y la hoja de detalle de un aviso, por su tipo.
    var showPending by remember { mutableStateOf(false) }
    var pendingDetail by remember { mutableStateOf<ProgramPendingKind?>(null) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    // Id de la entrada del catálogo cuya hoja «Cómo funciona» está abierta (solo lectura); null = cerrada.
    var planInfoEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    // Borrador de «Editar programa»: se abre desde la cabecera o desde «Más opciones» y no se escribe hasta «Guardar».
    var editDraft by remember { mutableStateOf<ProgramEditDraft?>(null) }
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

    // Token de la hoja de calibración del volumen: ningún aviso lo sube desde que la fila «Rangos» abre su hoja.
    LaunchedEffect(openVolumeSheetToken) {
        if (openVolumeSheetToken > 0) showVolumeSheet = true
    }
    // Recorrido de Entreno: la primera vez que se entra en un programa ya cargado (visto = el controlador no arranca).
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(p.id) { tourHost?.startTour(TourIds.TRAINING, TourScripts.training) }

    // La cabecera ya salió por arriba cuando su borde inferior pasa la línea: la barra flotante lo usa para el punto de ⋯. Todo en la raíz.
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val islandLinePx = statusTopPx + with(density) { ProgramIslandBottom.toPx() }
    val scroll = rememberScrollState()
    val headerBottomRoot = remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val headerOut = rememberPassedLine(markerRootY = headerBottomRoot, lineRootPx = islandLinePx)
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
    // Cinta de fases y «Bloque X de Y»: cada semana con la fase de su bloque. La semana actual sale de la línea de Home (1 = la primera).
    val allProgramWeeks = remember(roadmapBlocks, p) { ProgramDetailHelpers.getAllWeeks(roadmapBlocks, p) }
    val weekBlock = remember(roadmapBlocks, p) {
        roadmapBlocks.withIndex().flatMap { (blockIndex, block) ->
            val weeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmapBlocks, p)
            val phase = programPhaseOf(goal = block.goal, hasCompetitionWeek = weeks.any { it.keyDateType == KeyDateType.COMPETITION })
            weeks.map { week -> week.id to (phase to blockIndex) }
        }.toMap()
    }
    val currentWeekIndex = programLine?.weekIndex?.minus(1)
    val currentBlockIndex = currentWeekIndex?.let { allProgramWeeks.getOrNull(it)?.id }?.let { weekBlock[it]?.second }
    val ribbon = if (repeatingWeek) {
        emptyList<ProgramRibbonWeek>()
    } else {
        allProgramWeeks.mapIndexed { index, week ->
            ProgramRibbonWeek(
                color = programPhaseColor(weekBlock[week.id]?.first),
                done = currentWeekIndex != null && index < currentWeekIndex,
                current = index == currentWeekIndex,
            )
        }
    }
    val blockLabel = currentBlockIndex?.takeIf { roadmapBlocks.size > 1 }?.let { "Bloque ${it + 1} de ${roadmapBlocks.size}" }
    // «Hoy también»: otra sesión de hoy, la que sigue a la de la acción principal y todavía no está hecha.
    val todayDayOfWeek = java.time.LocalDate.now().dayOfWeek.value
    val primarySessionId = primarySession?.session?.id
    val todayAlso = displayedSessions
        .filter { it.dayOfWeek == todayDayOfWeek }
        .dropWhile { it.id != primarySessionId }
        .drop(1)
        .firstOrNull { it.id !in completedSessionIds }
        ?.let { other ->
            programTodayAlsoLine(
                sessionName = other.name,
                minutes = com.example.kpkn.domain.training.SessionDurationEstimator.estimate(other).totalMinutes,
            )
        }
    // «Pendiente»: lo que espera una decisión, con los mismos avisos de siempre. Cada uno abre su hoja de detalle; el check-in abre el suyo.
    fun openPendingDetail(kind: ProgramPendingKind): () -> Unit = { showPending = false; pendingDetail = kind }
    val pendingItems = buildList<ProgramPendingItem> {
        if (pendingMissedSessions != null || inactivityNotice != null) {
            add(
                ProgramPendingItem(
                    kind = ProgramPendingKind.MISSED_SESSIONS,
                    title = "Sesiones pendientes",
                    detail = "Elige cómo retomar el plan",
                    onOpen = openPendingDetail(ProgramPendingKind.MISSED_SESSIONS),
                ),
            )
        }
        // El banner de transición de bloque: explícito y de autorregulación, explícito de 1RM, o un cambio de bloque sin confirmar.
        blockTransitionBanner?.let { banner ->
            val kind = when {
                !banner.requiresExplicitConfirmation -> ProgramPendingKind.BLOCK_REVIEW
                banner.pendingType == PendingProgramActionType.CONFIRM_AUTOREGULATION -> ProgramPendingKind.AUTOREGULATION
                else -> ProgramPendingKind.ONE_RM_TEST
            }
            val title = when (kind) {
                ProgramPendingKind.AUTOREGULATION -> "Propuesta de ajuste"
                ProgramPendingKind.ONE_RM_TEST -> "Test de 1RM pendiente"
                else -> "Cambio de bloque"
            }
            add(ProgramPendingItem(kind = kind, title = title, detail = banner.message, onOpen = openPendingDetail(kind)))
        }
        val autoregPending = p.runState?.pendingAction?.type == PendingProgramActionType.CONFIRM_AUTOREGULATION
        if (nativeProgressionCard.pendingLabel != null || autoregPending) {
            add(
                ProgramPendingItem(
                    kind = ProgramPendingKind.PROGRESSION,
                    title = nativeProgressionCard.pendingLabel ?: "Propuestas de progresión",
                    detail = "Revisa y aplica o mantén la carga",
                    onOpen = openPendingDetail(ProgramPendingKind.PROGRESSION),
                ),
            )
        }
        pendingBlockReview?.let { pending ->
            add(
                ProgramPendingItem(
                    kind = ProgramPendingKind.BLOCK_REVIEW,
                    title = "Revisión de bloque",
                    detail = pending.review.summary,
                    onOpen = openPendingDetail(ProgramPendingKind.BLOCK_REVIEW),
                ),
            )
        }
        pendingWeeklyCheckin?.let { request ->
            // «Responder» abre el check-in de siempre, con la lista cerrada; «Omitir» hace la misma llamada que antes.
            val answer: () -> Unit = { showPending = false; viewModel.clearWeeklyCheckinPreview(); showWeeklyCheckin = true }
            add(
                ProgramPendingItem(
                    kind = ProgramPendingKind.WEEKLY_CHECKIN,
                    title = "Check-in de la semana",
                    detail = "¿Cómo fue la semana pasada?",
                    onOpen = answer,
                    primaryLabel = "Responder",
                    onPrimary = answer,
                    secondaryLabel = "Omitir esta semana",
                    onSecondary = {
                        viewModel.omitWeeklyCheckin(
                            com.example.kpkn.domain.training.cycle.WeeklyCheckin(
                                request.weekId, request.cycleNumber, System.currentTimeMillis(), skipped = true,
                                programRunId = request.programRunId, feedbackWeekId = request.feedbackWeekId,
                                feedbackCycleNumber = request.feedbackCycleNumber,
                            ),
                        )
                    },
                ),
            )
        }
        pendingContinuation?.let {
            add(
                ProgramPendingItem(
                    kind = ProgramPendingKind.CONTINUATION,
                    title = "Programa terminado",
                    detail = "Elige cómo seguir",
                    onOpen = openPendingDetail(ProgramPendingKind.CONTINUATION),
                ),
            )
        }
    }
    val pendingKinds = pendingItems.map { it.kind }
    // Si el aviso de una hoja de detalle se resuelve, la hoja se cierra sola.
    LaunchedEffect(pendingKinds) {
        val open = pendingDetail
        if (open != null && pendingKinds.none { it == open }) pendingDetail = null
    }
    val headerState = ProgramHeaderState(
        name = p.name,
        status = status,
        meta = programHeadlineMeta(
            goalLabel = programModeLabel(p.mode),
            weekCount = weekCount,
            blockCount = p.macrocycles.sumOf { it.blocks.size },
            isRoutine = repeatingWeek,
            daysPerWeek = p.weekDays,
        ),
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
        scopeLine = null,
        deloadNotice = null,
        ribbon = ribbon,
        blockLabel = blockLabel,
        todayAlso = todayAlso,
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
        onShare = { showShareSheet = true },
        pendingCount = pendingItems.size,
        onPending = { showPending = true },
    )
    val planSummary = remember(p) { buildProgramPlanDisplaySummary(p) }

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
                // Hueco de la barra flotante: el contenido empieza bajo ella.
                Spacer(Modifier.height(ProgramFloatingBarContentInset))
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
                    competitionState?.let { state ->
                        com.example.kpkn.screens.programdetail.design.ProgramCompetitionModule(state,competitionBusy,
                            onPlan = { planningCompetition = state },onMeetDay = { meetDayAttempts = state.record.plannedAttempts },
                            onReviewResult = { viewModel.reviewMeetResult(state.record.id) },
                            onOpenRecord = onOpenCompetition?.let { open -> { open(state.record.id) } })
                        Spacer(Modifier.height(20.dp))
                    }
                }
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
                        copiedWeekState = copiedWeekState,
                    )
                }
            }
        }

        if (!showSplitPage) {
            // Velo bajo la barra de estado: por encima del contenido que se desliza y por debajo de la barra flotante.
            KpknStatusVeil(modifier = Modifier.align(Alignment.TopCenter))
            // Barra flotante: atrás, las tres pestañas y ⋯. Con pendientes y la cabecera ya fuera, ⋯ lleva el punto.
            // El ancla del recorrido («Estructura») sigue en la barra, que es donde están ahora las pestañas.
            Box(modifier = Modifier.align(Alignment.TopCenter).tourAnchor(TourAnchors.TRAINING_ESTRUCTURA)) {
                ProgramFloatingBar(
                    selected = uiState.structureSubTab,
                    onSelect = { viewModel.setStructureSubTab(it) },
                    onBack = onBack,
                    onMore = { showMoreMenu = true },
                    pendingDot = pendingItems.isNotEmpty() && headerOut,
                )
            }
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
        ProgramPlanSheet(
            summary = planSummary,
            onDismiss = { showPlanSheet = false },
            // «Ver cómo funciona» cierra esta hoja y abre la de solo lectura de la biblioteca.
            onOpenPlanInfo = { entryId -> showPlanSheet = false; planInfoEntryId = entryId },
        )
    }
    exerciseSwapRequest?.let { request ->
        com.example.kpkn.screens.programdetail.design.ProgramSwapSheet(request.sourceName,request.options,
            request.requiresEquipmentConfirmation,exerciseSwapPreview?.result?.programDecisions?.lastOrNull()?.detail,
            exerciseSwapBusy,viewModel::previewExerciseSwap,viewModel::confirmExerciseSwap,
            viewModel::clearExerciseSwapPreview,viewModel::closeExerciseSwap)
    }
    planningCompetition?.let { state ->
        com.example.kpkn.screens.programdetail.design.CompetitionPlanSheet(state,competitionBusy,
            onSave = { attempts,category,weight -> viewModel.saveCompetitionPlan(state.record,attempts,category,weight) },
            onDismiss = { planningCompetition = null })
    }
    meetDayAttempts?.let { attempts ->
        com.example.kpkn.screens.programdetail.design.MeetDaySheet(attempts) { meetDayAttempts = null }
    }
    meetResultPreview?.let { preview ->
        com.example.kpkn.screens.programdetail.design.MeetResultReviewSheet(preview,competitionBusy,
            viewModel::confirmMeetResult,viewModel::clearMeetResultPreview)
    }
    schedulePreview?.let { preview ->
        com.example.kpkn.screens.programdetail.design.ProgramScheduleReviewSheet(preview.original,preview.result,
            scheduleBusy,viewModel::confirmScheduleContinuation,viewModel::clearSchedulePreview)
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
    if (showPending && pendingItems.isNotEmpty()) {
        ProgramPendingSheet(items = pendingItems, onDismiss = { showPending = false })
    }
    // La hoja de detalle de un aviso: dentro, el mismo módulo que antes se pintaba en la portada o en la pestaña Semana.
    pendingDetail?.let { kind ->
        ProgramPendingDetailSheet(
            title = pendingItems.firstOrNull { it.kind == kind }?.title ?: "Pendiente",
            onDismiss = { pendingDetail = null },
        ) {
            when (kind) {
                ProgramPendingKind.MISSED_SESSIONS -> ProgramScheduleModule(
                    p,
                    pendingMissedSessions,
                    inactivityNotice,
                    scheduleBusy,
                    onChoice = { viewModel.reviewScheduleContinuation(it) },
                    onSoftReturn = { viewModel.reviewScheduleContinuation() },
                )
                ProgramPendingKind.ONE_RM_TEST, ProgramPendingKind.AUTOREGULATION -> blockTransitionBanner?.let { banner ->
                    BlockTransitionBannerModule(
                        banner = banner,
                        viewModel = viewModel,
                        onOpenOneRmDialog = { showOneRmDialog = true },
                        onOpenProposals = { pendingDetail = ProgramPendingKind.PROGRESSION },
                    )
                }
                ProgramPendingKind.BLOCK_REVIEW -> {
                    blockTransitionBanner?.takeIf { !it.requiresExplicitConfirmation }?.let { banner ->
                        BlockTransitionBannerModule(
                            banner = banner,
                            viewModel = viewModel,
                            onOpenOneRmDialog = { showOneRmDialog = true },
                            onOpenProposals = { pendingDetail = ProgramPendingKind.PROGRESSION },
                        )
                    }
                    pendingBlockReview?.let { pending ->
                        val alternative = pending.adjustment == null && pending.alternatives.isNotEmpty()
                        ProgramTransitionModule(message = pending.review.summary,
                            primaryLabel = if (alternative) "Revisar inicio suave" else "Revisar bloque",
                            onPrimary = { viewModel.reviewBlockAdjustment(pending, if (alternative) 0 else null) },
                            secondaryLabel = "Conservar plan", onSecondary = { viewModel.keepBlockPlan(pending) },
                            dismissLabel = "Ver decisiones", onDismiss = { showDecisions = true })
                    }
                }
                ProgramPendingKind.PROGRESSION -> AutoregulationProposalsCard(
                    program = p,
                    nativeCard = nativeProgressionCard,
                    onAccept = { viewModel.acceptAutoregulation() },
                    onAcceptProposal = { viewModel.acceptAutoregulation(it) },
                    onReject = { viewModel.rejectAutoregulation() },
                    onAcceptNative = { viewModel.acceptNativeProgressionProposal(it) },
                    onRejectNative = { viewModel.rejectNativeProgressionProposal(it) },
                    onDismissNativeNotice = { viewModel.dismissNativeProgressionNotice(it) },
                    onOpenTm = { showTmEditor = true },
                    onRematerialize = { viewModel.rematerializePending() },
                )
                ProgramPendingKind.CONTINUATION -> pendingContinuation?.let { pending ->
                    ProgramTransitionModule(message = "Programa terminado. Elige cómo seguir.",
                        primaryLabel = "Repetir programa",onPrimary = { viewModel.reviewContinuation(pending,com.example.kpkn.domain.training.block.ContinuationChoice.REPEAT_WITH_NEW_MAXES) },
                        secondaryLabel = "Elegir catálogo",onSecondary = { viewModel.reviewContinuation(pending,com.example.kpkn.domain.training.block.ContinuationChoice.PICK_FROM_CATALOG) },
                        dismissLabel = "Semana de mantenimiento",onDismiss = { viewModel.reviewContinuation(pending,com.example.kpkn.domain.training.block.ContinuationChoice.MAINTENANCE_WEEK) })
                }
                else -> Unit
            }
        }
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
            items = buildList<ProgramMenuItem> {
                if (pendingItems.isNotEmpty()) add(ProgramMenuItem(label = "Pendiente · ${pendingItems.size}", onClick = { showPending = true }))
                add(ProgramMenuItem(label = "Sobre el programa", onClick = { showPlanSheet = true }))
                add(ProgramMenuItem(label = "Editar programa", onClick = openEdit))
                add(ProgramMenuItem(label = "Compartir en historias", onClick = { showShareSheet = true }))
                // Pausar, reanudar o activar: la misma llamada que tenía el botón de la cabecera.
                add(
                    ProgramMenuItem(
                        label = when (status) {
                            ProgramHeaderStatus.ACTIVE -> "Pausar programa"
                            ProgramHeaderStatus.PAUSED -> "Reanudar programa"
                            ProgramHeaderStatus.DRAFT -> "Activar programa"
                        },
                        onClick = { viewModel.toggleStartPause() },
                    ),
                )
                add(ProgramMenuItem(label = "Decisiones del sistema", onClick = { showDecisions = true }))
                add(ProgramMenuItem(label = "Historial", onClick = { viewModel.setStructureSubTab(StructureSubTab.MACROCICLO) }))
            },
            onDismiss = { showMoreMenu = false },
        )
    }
    if (showShareSheet) {
        val shareLogs by viewModel.programLogs.collectAsStateWithLifecycle()
        ProgramShareSheet(program = p, logs = shareLogs, onDismiss = { showShareSheet = false })
    }
    if (showEditSheet) {
        val draft = editDraft ?: programEditDraftOf(p)
        ProgramEditSheet(
            program = p,
            draft = draft,
            onDraftChange = { editDraft = it },
            onSave = {
                viewModel.updateProgram(
                    p.copy(name = draft.trimmedName, description = draft.trimmedDescription, coverImage = draft.cover, mode = draft.mode),
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
    // Rematerialización: un bloque con materialización pendiente se rehace aunque no haya ninguna hoja abierta.
    // (Antes lo disparaba la tarjeta de propuestas, que solo existe dentro de la hoja de progresiones.)
    // rematerializePending no rehace nada si ya no hay bloques pendientes, así que una llamada extra de la tarjeta es inocua.
    val needsRematerialize = p.macrocycles.any { macro -> macro.blocks.any { it.materializationPending } }
    LaunchedEffect(p.id, p.macrocycles.flatMap { it.blocks }.filter { it.materializationPending }.map { it.id }) {
        if (needsRematerialize) viewModel.rematerializePending()
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

    // «Ver cómo funciona» de la hoja «Sobre el programa»: la misma hoja de la biblioteca, sin botón primario.
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
    copiedWeekState: MutableState<String?> = remember(program.id) { mutableStateOf(null) },
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
    val showSimpleCalendarizationSheet by viewModel.showSimpleCalendarizationSheet.collectAsState()
    val calendarizationStartDate by viewModel.calendarizationStartDate.collectAsState()
    val calendarizationEndDate by viewModel.calendarizationEndDate.collectAsState()
    val calendarizationStartDayOfWeek by viewModel.calendarizationStartDayOfWeek.collectAsState()
    val calendarizationTrainingDays by viewModel.calendarizationTrainingDays.collectAsState()
    val restoreBlockedSessionIds by viewModel.restoreBlockedSessionIds.collectAsState()
    // Lo copiado vive por encima de las pestañas (copiedWeekState): cambiar de pestaña no lo pierde.
    var copiedRoadmapWeekId by copiedWeekState
    var showCopyWeekDialog by remember { mutableStateOf(false) }
    // Semana sobre la que se pegó: la hoja de conflictos la trae ya marcada como destino.
    var pasteTargetWeekId by remember { mutableStateOf<String?>(null) }
    val allProgramWeeks = remember(program, roadmapBlocks) {
        com.example.kpkn.domain.training.ProgramDetailHelpers.getAllWeeks(roadmapBlocks, program)
    }
    var pendingDeleteSession by remember { mutableStateOf<Session?>(null) }
    // Hoja «Editar semana», menú de la semana (copiar y pegar) y días desplegados (en el VM: sobreviven a Estructura).
    var weekEdit by remember(program.id) { mutableStateOf<WeekEditDraft?>(null) }
    var weekMenu by remember { mutableStateOf<Pair<String, Rect>?>(null) }
    // Navegador de la semana: la hoja «Semanas», el menú «Añadir sesión», el borrado de una semana y dónde están sus botones (para anclar menús).
    var showWeeksSheet by remember { mutableStateOf(false) }
    var addSessionMenu by remember { mutableStateOf<Rect?>(null) }
    var addSessionBounds by remember { mutableStateOf(Rect.Zero) }
    var weekMoreBounds by remember { mutableStateOf(Rect.Zero) }
    var pendingDeleteWeek by remember { mutableStateOf<String?>(null) }
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
                pasteTargetWeekId = targetWeekId
                showCopyWeekDialog = true
            }
        }
    }

    fun focusWeek(blockId: String, weekId: String) {
        viewModel.selectBlock(blockId)
        viewModel.selectWeek(weekId)
        viewModel.setStructureSubTab(StructureSubTab.SEMANA)
    }

    // Navegador de la semana: la lista en la que se navega (en un programa simple, las suyas; en el resto, todas) y la posición de la elegida.
    val isRoutine = program.isSimpleProgram && currentWeeks.size <= 1
    val navWeeks = if (program.isSimpleProgram) currentWeeks else allProgramWeeks
    val navIndex = selectedWeekMeta?.let { week -> navWeeks.indexOfFirst { it.id == week.id } } ?: -1
    // Una rutina no tiene a dónde ir: sus flechas se quedan apagadas.
    val prevNavWeek = if (!isRoutine && navIndex > 0) navWeeks[navIndex - 1] else null
    val nextNavWeek = if (!isRoutine && navIndex in 0 until navWeeks.lastIndex) navWeeks[navIndex + 1] else null
    // Fechas de la semana: las que guarda; si no guarda ninguna, las de la proyección del calendario (la misma que usa la agenda).
    val calendarProjection = remember(program) { ProgramCalendarEngine.project(program) }
    val weekDates = selectedWeekMeta?.let { week ->
        week.trainingDayDates.values.mapNotNull { ProgramCalendarEngine.parseIsoDate(it) }.ifEmpty {
            calendarProjection.weeks.firstOrNull { it.weekId == week.id }?.trainingDayDates?.values?.toList().orEmpty()
        }
    }.orEmpty()
    // La fase de la semana sale del bloque que la contiene: el color de su objetivo, sin la marca de competición (eso va en la fecha).
    val weekBlockId = selectedWeekMeta?.let { week -> phaseBlocks.firstOrNull { block -> block.weeks.any { it.id == week.id } }?.id }
    val weekBlockGoal = roadmapBlocks.firstOrNull { it.id == weekBlockId }?.goal
    val weekStats = remember(displayedSessions, completedSessionIds) { programWeekStats(displayedSessions, completedSessionIds) }
    // Ir a una semana de la lista de navegación: en un programa simple, la semana tal cual; en el resto, con su bloque.
    fun navigateToWeek(weekId: String) {
        if (program.isSimpleProgram) viewModel.selectWeek(weekId) else selectPhaseWeek(weekId)
    }
    // Añadir una sesión a un día de la semana elegida: lo usan la agenda (el «+» de cada día) y el menú «Añadir sesión».
    fun addSessionOnDay(dayId: Int) {
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
    }

    // Sin bloques, Semana no tiene nada que enseñar: la salida es crear el primer bloque en Estructura (no se queda vacía).
    if (roadmapBlocks.isEmpty() && structureSubTab == StructureSubTab.SEMANA) {
        ProgramEmptyStructure(onAddFirstBlock = { viewModel.setStructureSubTab(StructureSubTab.MACROCICLO) })
        return
    }

    Column {
        Spacer(modifier = Modifier.height(4.dp))

        // Las semanas ya no van en la página: el navegador de la pestaña Semana (abajo) abre la hoja «Semanas».
        if (structureSubTab != StructureSubTab.VOLUMEN && roadmapBlocks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Cargas de referencia (TM) por ejercicio: viven en Estructura («Del programa») y se editan en su hoja.
        var showExerciseTmSheet by rememberSaveable(program.id) { mutableStateOf(false) }
        val exerciseTmTargets = remember(program) { exerciseTrainingMaxTargets(program) }
        if (showExerciseTmSheet) {
            KpknSheet(onDismissRequest = { showExerciseTmSheet = false }, stableHeightFraction = 0.85f) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Cargas de referencia", style = KpknType.title, color = WizardColors.text)
                    Text(
                        "El TM de cada ejercicio que trabaja con porcentajes. Al cambiarlo se recalculan sus cargas.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    ExerciseTrainingMaxPanel(program, onConfirm = viewModel::updateExerciseTrainingMaxAndAwait)
                }
            }
        }

        when (structureSubTab) {
            StructureSubTab.SEMANA -> {
                // Navegador de la semana, en el lugar de la antigua cabecera: ‹ título › con sus tres cifras. El título abre la hoja «Semanas».
                selectedWeekMeta?.let { week ->
                    val phaseLabel = week.mesoGoal
                        .takeIf { !program.isSimpleProgram && it != com.example.kpkn.data.models.MesocycleGoal.CUSTOM }
                        ?.label
                    ProgramWeekHeader(
                        title = weekNavigatorTitle(isRoutine = isRoutine, weekIndex = navIndex, cycleNumber = program.runState?.cycleNumber),
                        subtitle = weekNavigatorSubtitle(phaseLabel = phaseLabel, dateRange = weekDateRange(weekDates)),
                        phaseColor = if (program.isSimpleProgram) null else programPhaseColor(programPhaseOf(goal = weekBlockGoal, hasCompetitionWeek = false)),
                        stats = weekStats,
                        canPrev = prevNavWeek != null,
                        canNext = nextNavWeek != null,
                        onPrev = { prevNavWeek?.let { navigateToWeek(it.id) } },
                        onNext = { nextNavWeek?.let { navigateToWeek(it.id) } },
                        onOpenWeeks = { if (!isRoutine) showWeeksSheet = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        name = week.name.takeUnless { WeekDefaultName.matches(it.trim()) },
                        description = week.description,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                val swapContext = LocalContext.current.applicationContext
                if (selectedWeekMeta != null && displayedSessions.isEmpty()) {
                    // Semana vacía: en vez de una agenda de días sin sesiones, copiar la anterior o añadir la primera sesión.
                    KpknModule(
                        modifier = Modifier.padding(horizontal = 16.dp).tourAnchor(TourAnchors.TRAINING_SEMANA),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 26.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Esta semana está vacía",
                                style = EmptyWeekTitleStyle,
                                color = WizardColors.text,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Copia una semana anterior o añade sesiones día a día.",
                                style = KpknType.body,
                                color = WizardColors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(18.dp))
                            // navIndex cuenta desde 0: la semana de antes lleva el número navIndex (los números empiezan en 1).
                            if (prevNavWeek != null && prevNavWeek.sessions.isNotEmpty()) {
                                KpknPrimaryPill(
                                    text = "Copiar la semana $navIndex",
                                    onClick = {
                                        viewModel.copyWeekSessions(
                                            sourceWeekId = prevNavWeek.id,
                                            targetWeekIds = setOf(selectedWeekMeta.id),
                                            replaceWeekIds = emptySet(),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            KpknSecondaryPill(
                                text = "Añadir sesión",
                                onClick = { addSessionMenu = addSessionBounds },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { addSessionBounds = it.boundsInWindow() },
                                icon = KpknLineIcons.Plus,
                            )
                        }
                    }
                } else {
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
                        onAddSession = { dayId -> addSessionOnDay(dayId) },
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
                    if (selectedWeekMeta != null) {
                        // Debajo de la agenda: «Añadir sesión» (se elige el día) y el menú de la semana (⋯).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KpknSecondaryPill(
                                text = "Añadir sesión",
                                onClick = { addSessionMenu = addSessionBounds },
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { addSessionBounds = it.boundsInWindow() },
                                icon = KpknLineIcons.Plus,
                            )
                            Spacer(Modifier.width(8.dp))
                            KpknIconButton(
                                icon = KpknLineIcons.More,
                                contentDescription = "Más opciones de la semana",
                                onClick = { weekMenu = selectedWeekMeta.id to weekMoreBounds },
                                modifier = Modifier.onGloballyPositioned { weekMoreBounds = it.boundsInWindow() },
                            )
                        }
                    }
                }
            }
            StructureSubTab.MACROCICLO -> {
                // «Revisión del plan» (antes al pie de Volumen) vive ahora al pie de Estructura: el mismo informe del motor.
                val structureLogs by viewModel.programLogs.collectAsState()
                val structureAnalyticsReport = remember(program, structureLogs) {
                    ProgramAnalyticsEngine.analyze(program = program, logs = structureLogs, exerciseCatalog = exerciseCatalogSnapshot())
                }
                MacrocycleEditor(
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
                analyticsReport = structureAnalyticsReport,
                onOpenTrainingMaxes = if (exerciseTmTargets.isNotEmpty()) ({ showExerciseTmSheet = true }) else null,
                trainingMaxSummary = exerciseTrainingMaxSummary(exerciseTmTargets.size, exerciseTrainingMaxMissing(program, exerciseTmTargets)),
            )
            }
            StructureSubTab.VOLUMEN -> {
                val progLogs by viewModel.programLogs.collectAsState()
                val isActiveProg by viewModel.isActiveProgram.collectAsState()
                val discomforts by viewModel.programDiscomforts.collectAsState()
                val exDiscomfortAssoc by viewModel.exerciseDiscomfortAssociations.collectAsState()
                val hasCreatedSessions = remember(program) {
                    program.macrocycles
                        .flatMap { it.blocks }
                        .flatMap { it.mesocycles }
                        .flatMap { it.weeks }
                        .any { it.sessions.isNotEmpty() }
                }
                // Pestaña Progreso: resumen, fuerza y trabajo por semana, con el alcance elegido. Debajo, el volumen por músculo con la fila «Rangos» (sustituye a «Calibrar»).
                var progressScope by rememberSaveable { mutableStateOf(ProgressScope.PROGRAM) }
                var showAllLifts by rememberSaveable { mutableStateOf(false) }
                val blockPosition = roadmapBlocks.indexOfFirst { it.id == selectedBlockId }
                val blockLabel = if (roadmapBlocks.size >= 2 && blockPosition >= 0) "Bloque ${blockPosition + 1}" else null
                val weekPosition = allProgramWeeks.indexOfFirst { it.id == selectedWeekId }
                val weekLabel = if (weekPosition >= 0) "Semana ${weekPosition + 1}" else null
                val progressUi = remember(program, progLogs, progressScope, selectedBlockId, selectedWeekId, activeWeekId, showAllLifts) {
                    buildProgramProgress(
                        program = program,
                        logs = progLogs,
                        scope = progressScope,
                        selectedBlockId = selectedBlockId,
                        selectedWeekId = selectedWeekId,
                        currentWeekId = activeWeekId,
                        maxLifts = if (showAllLifts) Int.MAX_VALUE else 5,
                    )
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    ProgressScopeChips(
                        selected = progressScope,
                        blockLabel = blockLabel,
                        weekLabel = weekLabel,
                        onSelect = { progressScope = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    ProgressSummaryModule(progressUi)
                    Spacer(Modifier.height(12.dp))
                    if (progressUi.lifts.isNotEmpty()) {
                        ProgressLiftsModule(
                            rows = progressUi.lifts,
                            allCount = progressUi.allLiftsCount,
                            onSeeAll = { showAllLifts = true },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    ProgressWorkModule(
                        weeks = progressUi.weeks,
                        setsDone = progressUi.kpis.setsDone,
                        setsPlanned = progressUi.kpis.setsPlanned,
                        note = progressUi.weekNote,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Resúmenes mensuales (P8c-2): los meses con resumen; cada fila abre su pantalla completa, con su hoja de compartir.
                    val summaryMonthList = remember(progLogs, program.id) { summaryMonths(progLogs, program.id, java.time.LocalDate.now()) }
                    var openSummaryMonth by rememberSaveable(program.id) { mutableStateOf<String?>(null) }
                    var shareSummary by remember { mutableStateOf(false) }
                    val summaryContext = LocalContext.current
                    if (summaryMonthList.isNotEmpty()) {
                        ProgramMonthlySummariesModule(
                            months = summaryMonthList,
                            onOpen = { month ->
                                monthlySummaryStore(summaryContext).markSeen(summaryKey(program.id, month))
                                openSummaryMonth = month.toString()
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    openSummaryMonth?.let { monthText ->
                        val month = remember(monthText) { YearMonth.parse(monthText) }
                        val report = remember(progLogs, month, program.id) { MonthlyReportEngine.build(progLogs, month, program.id) }
                        KpknPortal {
                            MonthlySummaryScreen(
                                report = report,
                                programName = program.name,
                                onShare = { shareSummary = true },
                                onOpenProgress = null,
                                onClose = {
                                    shareSummary = false
                                    openSummaryMonth = null
                                },
                            )
                        }
                        if (shareSummary) {
                            MonthShareSheet(report = report, programName = program.name, onDismiss = { shareSummary = false })
                        }
                    }
                }
                // Rangos: la fila del volumen abre su hoja; «Revisar mis respuestas» sube el token que abre la calibración de siempre.
                var showRangesSheet by rememberSaveable { mutableStateOf(false) }
                var answersToken by remember { mutableIntStateOf(0) }
                val rangesOrigin = rangesOriginOf(
                    ownRangesCount = program.planningVolumeTargets.size,
                    hasProfile = volumeProfileResponses != null || program.athleteProfileScore != null,
                )
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
                    programLogs = progLogs,
                    rangesOrigin = rangesOrigin,
                    onOpenRanges = { showRangesSheet = true },
                    openAnswersToken = answersToken,
                )
                }
                if (showRangesSheet) {
                    ProgramRangesSheet(
                        origin = rangesOrigin,
                        ownRangesCount = program.planningVolumeTargets.size,
                        proposalsCount = planningRangeProposals.size,
                        proposalsCycle = planningRangeProposals.maxOfOrNull { it.cycleNumber },
                        onChooseOwn = { showRangesSheet = false; showPlanningVolumeTargets = true },
                        onReviewAnswers = { showRangesSheet = false; answersToken++ },
                        onReviewProposals = { showRangesSheet = false; viewModel.reviewPlanningRanges() },
                        onDismiss = { showRangesSheet = false },
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
            initialTargetWeekId = pasteTargetWeekId,
            conflictsFor = { source, targets -> viewModel.previewWeekCopyConflicts(source, targets) },
            onCopy = { source, targets, replace -> viewModel.copyWeekSessions(source, targets, replace) },
            onDismiss = {
                showCopyWeekDialog = false
                pasteTargetWeekId = null
            },
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

    // Menú de la semana (⋯ del navegador, o pulsación larga de una semana en la hoja «Semanas»).
    weekMenu?.let { (weekId, anchor) ->
        val menuWeek = allProgramWeeks.firstOrNull { it.id == weekId } ?: currentWeeks.firstOrNull { it.id == weekId }
        val copied = copiedRoadmapWeekId
        val copiedIndex = navWeeks.indexOfFirst { it.id == copied }
        ProgramItemMenu(
            items = buildList<ProgramAnchoredMenuItem> {
                menuWeek?.let { week ->
                    add(
                        ProgramAnchoredMenuItem(
                            label = "Nombre y nota de la semana",
                            onClick = { weekEdit = WeekEditDraft(weekId = week.id, name = week.name, description = week.description.orEmpty()) },
                        ),
                    )
                }
                if (weekId == selectedWeekId) {
                    add(ProgramAnchoredMenuItem(label = "Cambiar reparto", onClick = onOpenSplitPage))
                }
                add(ProgramAnchoredMenuItem(label = "Copiar esta semana", onClick = { copiedRoadmapWeekId = weekId }))
                if (copied != null && copied != weekId) {
                    val pasteLabel = if (copiedIndex >= 0) "Pegar la semana ${copiedIndex + 1} aquí" else "Pegar semana aquí"
                    add(ProgramAnchoredMenuItem(label = pasteLabel, onClick = { pasteWeek(weekId) }))
                }
                add(
                    ProgramAnchoredMenuItem(
                        label = "Añadir una semana después",
                        onClick = {
                            if (program.isSimpleProgram) {
                                viewModel.addWeekToSimpleProgram()
                            } else {
                                phaseBlocks.firstOrNull { block -> block.weeks.any { it.id == weekId } }?.let { addWeekToBlock(it.id) }
                            }
                        },
                    ),
                )
                if (navWeeks.size > 1) {
                    add(ProgramAnchoredMenuItem(label = "Eliminar esta semana", onClick = { pendingDeleteWeek = weekId }, destructive = true))
                }
            },
            anchor = anchor,
            onDismiss = { weekMenu = null },
        )
    }

    // «Añadir sesión»: elige el día de la semana elegida.
    addSessionMenu?.let { anchor ->
        ProgramItemMenu(
            items = getDynamicDays(program.startDay ?: 1, program.weekDays ?: 7).map { day ->
                ProgramAnchoredMenuItem(label = day.name, onClick = { addSessionOnDay(day.id) })
            },
            anchor = anchor,
            onDismiss = { addSessionMenu = null },
        )
    }

    pendingDeleteWeek?.let { weekId ->
        KpknAlertDialog(
            onDismissRequest = { pendingDeleteWeek = null },
            title = "¿Eliminar esta semana?",
            text = "Se quitan sus sesiones. Tus registros se conservan.",
            confirmLabel = "Eliminar",
            onConfirm = {
                viewModel.deleteWeekFromRoadmap(weekId)
                pendingDeleteWeek = null
            },
            dismissLabel = "Cancelar",
            destructive = true,
        )
    }

    // Hoja «Semanas»: el índice de todas las semanas. Elegir una la abre y cierra la hoja.
    if (showWeeksSheet) {
        ProgramWeeksSheet(
            subtitle = weekSheetSubtitle(
                weekCount = navWeeks.size,
                blockCount = if (program.isSimpleProgram) null else roadmapBlocks.size,
            ),
            onDismiss = { showWeeksSheet = false },
            onGoToCurrent = activeWeekId?.let { id ->
                val goToCurrent: () -> Unit = {
                    if (program.isSimpleProgram) viewModel.selectWeek(id) else selectPhaseWeek(id)
                    showWeeksSheet = false
                }
                goToCurrent
            },
        ) {
            if (!program.isSimpleProgram) {
                ProgramPhasesModule(
                    blocks = phaseBlocks,
                    selectedWeekId = selectedWeekId,
                    onSelectWeek = { weekId ->
                        selectPhaseWeek(weekId)
                        showWeeksSheet = false
                    },
                    onLongPressWeek = { weekId, anchor -> weekMenu = weekId to anchor },
                    onAddWeek = { blockId -> addWeekToBlock(blockId) },
                )
            } else {
                ProgramWeekStrip(
                    weeks = simpleWeeks,
                    selectedWeekId = selectedWeekId,
                    onSelectWeek = { weekId ->
                        viewModel.selectWeek(weekId)
                        showWeeksSheet = false
                    },
                    onLongPressWeek = { weekId, anchor -> weekMenu = weekId to anchor },
                    onAdd = { viewModel.addWeekToSimpleProgram() },
                )
            }
        }
    }

    pendingDeleteSession?.let { sessionToDelete ->
        ProgramDeleteSessionDialog(
            onConfirm = {
                val block = roadmapBlocks.find { it.id == selectedBlockId }
                val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                if (block != null && weekMeta != null) {
                    viewModel.deleteSession(sessionToDelete.id, block.macroIndex, weekMeta.mesoIndex, selectedWeekId ?: "")
                } else {
                    // Sin bloque o semana elegidos no se sabe dónde está la sesión: se avisa en vez de cerrar sin hacer nada.
                    viewModel.showMessage("No se pudo eliminar la sesión: vuelve a abrir su semana e inténtalo de nuevo.")
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

/** El título de la semana vacía: Syne Bold 18 / 22. */
private val EmptyWeekTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
)

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

/**
 * El aviso de transición de bloque, con las mismas acciones de siempre. Vive en la hoja «Pendiente»: antes lo pintaba la pestaña Semana.
 */
@Composable
private fun BlockTransitionBannerModule(
    banner: BlockTransitionBanner,
    viewModel: ProgramDetailViewModel,
    onOpenOneRmDialog: () -> Unit,
    onOpenProposals: () -> Unit,
) {
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
                // «Ver propuesta» abre la hoja «Pendiente» de progresiones; aquí no se acepta ni se rechaza nada.
                isAutoreg -> onOpenProposals()
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
    )
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

