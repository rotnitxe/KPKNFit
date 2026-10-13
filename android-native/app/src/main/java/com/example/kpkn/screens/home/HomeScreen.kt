package com.example.kpkn.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
import com.example.kpkn.data.exercises.exerciseCatalogReady
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.screens.auge.rememberAugeViewModel
import com.example.kpkn.screens.nutrition.NutritionViewModel
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.screens.nutrition.components.MealTypeSaver
import com.example.kpkn.screens.programs.CreateProgramTemplateSheet
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.programdetail.components.VolumeCalibrationSheet
import com.example.kpkn.screens.programs.ProgramsViewModel
import com.example.kpkn.screens.programs.ProtocolDetailSheet
import com.example.kpkn.screens.programs.TrainingMaxWizard
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
import com.example.kpkn.screens.home.design.HomeDateSaver
import com.example.kpkn.screens.home.design.HomeMasthead
import com.example.kpkn.screens.home.design.HomeMastheadState
import com.example.kpkn.screens.home.design.HomeMeals
import com.example.kpkn.screens.home.design.HomePrimaryPill
import com.example.kpkn.screens.home.design.HomeSectionAction
import com.example.kpkn.screens.home.design.HomeSectionGap
import com.example.kpkn.screens.home.design.HomeWeekStrip
import com.example.kpkn.screens.home.design.homeDateLine
import com.example.kpkn.screens.home.design.homeEntrance
import com.example.kpkn.screens.home.design.HomeBodyToday
import com.example.kpkn.screens.home.design.HomeNutritionToday
import com.example.kpkn.screens.home.design.HomeNotice
import com.example.kpkn.screens.home.design.HomeNoticeTone
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import kotlinx.coroutines.launch
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

typealias HomeGlassOverlay = @Composable (HazeState) -> Unit
typealias HomeGlassOverlayChange = (
    overlay: HomeGlassOverlay?,
    expectedCurrent: HomeGlassOverlay?,
) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToProgram: (String) -> Unit = {},
    onCreateProgram: () -> Unit = {},
    onStartWorkout: (Session, Program) -> Unit = { _, _ -> },
    onRegisterCompetition: (String) -> Unit = {},
    onResumeWorkout: () -> Unit = {},
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onNavigateToCard: (String) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onHeaderOverlayChange: HomeGlassOverlayChange = { _, _ -> },
    onNutritionOverlayChange: HomeGlassOverlayChange = { _, _ -> },
    onOnboardingOverlayChange: HomeGlassOverlayChange = { _, _ -> },
    onNavigateToNutritionEditor: () -> Unit = {},
    /** Abre el asistente de configuración; con [String] llega el plan que la persona eligió en la biblioteca (E-18). */
    onOpenSetupWizard: (String?) -> Unit = {},
    /** Abre un concepto de «Conceptos clave» desde la hoja «Cómo funciona» de la biblioteca; null = sin enlace. */
    onOpenConcept: ((String) -> Unit)? = null,
    viewModel: HomeViewModel = rememberHomeViewModel(),
    @Suppress("UNUSED_PARAMETER") nutritionViewModel: NutritionViewModel? = null,
) {
    // Theme toggle lives in Settings; params kept for API compatibility with MainActivity.
    @Suppress("UNUSED_VARIABLE")
    val unusedTheme = themeMode to onThemeChange

    val programsVm: ProgramsViewModel = viewModel()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val showNutrition = settings.nutritionTrackingChoice != com.example.kpkn.data.models.NutritionTrackingChoice.SKIPPED
    val showCreateProgramSheet by viewModel.showCreateProgramSheet.collectAsState()
    val pendingProgramName by viewModel.pendingProgramName.collectAsState()
    val pendingVolumeTemplateId by viewModel.pendingVolumeTemplateId.collectAsState()
    val pendingVolumeProtocol by viewModel.pendingVolumeProtocol.collectAsState()
    val pendingVolumeProfile by viewModel.pendingVolumeProfile.collectAsState()
    var selectedProtocol by remember { mutableStateOf<com.example.kpkn.data.protocols.Protocol?>(null) }
    var protocolForTm by remember { mutableStateOf<com.example.kpkn.data.protocols.Protocol?>(null) }
    var templateError by remember { mutableStateOf<String?>(null) }
    val createScope = rememberCoroutineScope()
    val openCreate = onCreateProgram
    val augeViewModel = rememberAugeViewModel()
    val augePerMuscle by augeViewModel.perMuscle.collectAsState()
    val augeSnapshot by augeViewModel.snapshot.collectAsState()

    val muscularProgress = augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR) / 100f
    val sncProgress = augeSnapshot.ringScore(RecoveryChannelId.SYSTEM) / 100f
    val columnaProgress = augeSnapshot.ringScore(RecoveryChannelId.STRUCTURE) / 100f
    val augeLoading = augeSnapshot.isLoading
    val uiState by viewModel.uiState.collectAsState()
    val nutritionRepo = remember { NutritionRepository.getInstance() }
    // WP-U7 (C5): si la hoja del logger estaba abierta, tras la muerte del proceso se reabre con su borrador.
    var showFoodLogger by rememberSaveable { mutableStateOf(false) }
    // Comida elegida con el «+» o con una comida de «Nutrición de hoy»; null = la de la hora (defaultMealTypeNow).
    var loggerMealChoice by rememberSaveable { mutableStateOf<MealType?>(null) }
    var showNutritionOverlay by remember { mutableStateOf(false) }
    var showAugeRecommendations by remember { mutableStateOf(false) }
    // «Tu semana»: el día elegido sobrevive a girar la pantalla; por defecto, hoy.
    var selectedDate by rememberSaveable(stateSaver = HomeDateSaver) { mutableStateOf(LocalDate.now()) }
    // RINGS: canal cuyo detalle está abierto. Se guarda por nombre para sobrevivir a girar la pantalla.
    var selectedChannelName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedChannel = selectedChannelName?.let { RecoveryChannelId.valueOf(it) }
    // Cabecera y «Tu semana», calculadas del programa activo y de sus sesiones de la semana.
    val todayDate = LocalDate.now()
    val activeProgram = remember(uiState.programs, uiState.activeProgramId) {
        uiState.programs.firstOrNull { it.id == uiState.activeProgramId }
    }
    val weekDays = remember(uiState.todaySessions, activeProgram, todayDate) {
        if (activeProgram == null || uiState.todaySessions.isEmpty()) {
            emptyList<HomeWeekDay>()
        } else {
            HomeWeekResolver.week(uiState.todaySessions, activeProgram.startDay, todayDate)
        }
    }
    val programLine = remember(uiState.todaySessions, activeProgram, todayDate) {
        HomeWeekResolver.programLine(activeProgram, uiState.todaySessions, todayDate)
    }
    val mastheadState = remember(uiState.userName, uiState.greeting, programLine, todayDate) {
        HomeMastheadState(
            dateLine = homeDateLine(todayDate),
            // El saludo llega como «¡Buenos días»: sin «¡» ni «!», y el nombre va aparte.
            greeting = uiState.greeting.replace("¡", "").replace("!", "").trim(),
            name = uiState.userName,
            initial = uiState.userName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            program = programLine,
        )
    }
    // Recorrido de Inicio: solo cuando los RINGS ya cargaron (si no, esperaría a sus anclas). Lo decide el controlador: visto = no arranca.
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(augeLoading) {
        if (!augeLoading) tourHost?.startTour(TourIds.HOME, TourScripts.home(conTorre = true))
    }
    val nutritionLogs by nutritionRepo.nutritionLogs.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(uiState.activeProgramId) {
        viewModel.loadFeedbacks(context)
    }

    val todayMeals = remember(nutritionLogs) {
        val today = LocalDate.now().toString()
        nutritionLogs.filter { it.date.startsWith(today) && it.status != NutritionStatus.PLANNED }
    }

    val latestNutritionOverlayChange by rememberUpdatedState(onNutritionOverlayChange)
    // Mutable holder so dispose can see the latest registered content identity.
    val nutritionRegistration = remember {
        object {
            var active: HomeGlassOverlay? = null
        }
    }
    LaunchedEffect(showNutritionOverlay, todayMeals) {
        val content: HomeGlassOverlay? =
            if (showNutritionOverlay) {
                { rootHazeState ->
                    NutritionTodayGlassOverlay(
                        hazeState = rootHazeState,
                        meals = todayMeals,
                        onDismiss = { showNutritionOverlay = false },
                        onAddMeal = {
                            showNutritionOverlay = false
                            showFoodLogger = true
                        },
                    )
                }
            } else {
                null
            }
        val previous = nutritionRegistration.active
        latestNutritionOverlayChange(content, previous)
        nutritionRegistration.active = content
    }
    LaunchedEffect(showNutrition) {
        if (!showNutrition) showNutritionOverlay = false
    }
    DisposableEffect(Unit) {
        onDispose {
            val owned = nutritionRegistration.active
            nutritionRegistration.active = null
            if (owned != null) {
                latestNutritionOverlayChange(null, owned)
            }
        }
    }

    val onboardingState by viewModel.onboardingState.collectAsState()
    val latestOpenSetupWizard by rememberUpdatedState(onOpenSetupWizard)
    LaunchedEffect(onboardingState.show) {
        if (onboardingState.show) {
            viewModel.dismissOnboarding()
            latestOpenSetupWizard(null)
        }
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Each mini-card is anchored to the actual element it summarizes, in root
    // coordinates. This avoids switching early when list items have internal
    // headers, spacers, or conditional cards above them.
    val handoffDistancePx = with(density) { 48.dp.toPx() }
    var headerBottomY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var sessionAnchorY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var ringsAnchorY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var nutritionAnchorY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    // Borde inferior de la cabecera de la página (coordenadas de la raíz): decide el fundido de la barra superior.
    var mastheadBottomY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }

    fun anchorProgress(anchorY: Float): Float =
        if (headerBottomY.isFinite() && anchorY.isFinite()) {
            ((headerBottomY - anchorY) / handoffDistancePx).coerceIn(0f, 1f)
        } else 0f

    val greetingProgress = 1f
    val sessionProgress by remember { derivedStateOf { anchorProgress(sessionAnchorY) } }
    val ringsProgress by remember { derivedStateOf { anchorProgress(ringsAnchorY) } }
    val nutritionProgress by remember { derivedStateOf { anchorProgress(nutritionAnchorY) } }
    // La barra aparece cuando la cabecera de la página sale por arriba (los últimos 48 dp antes de cruzarse).
    val mastheadBarAlpha by remember {
        derivedStateOf { homeBarAlphaFor(headerBottomY, mastheadBottomY, handoffDistancePx) }
    }
    val latestHeaderContent by rememberUpdatedState<@Composable (HazeState) -> Unit> { rootHazeState ->
        HomeTopBar(
            modifier = Modifier,
            greeting = uiState.greeting,
            userName = uiState.userName,
            greetingProgress = greetingProgress,
            ringsProgress = ringsProgress,
            sessionProgress = sessionProgress,
             nutritionProgress = if (showNutrition) nutritionProgress else 0f,
            hasPrograms = uiState.hasActiveProgram,
            muscularProgress = muscularProgress,
            sncProgress = sncProgress,
            columnaProgress = columnaProgress,
            primarySession = uiState.primarySession,
            homeCompetition = uiState.homeCompetition,
            isRestDay = uiState.primarySession == null && (uiState.isRestDay || uiState.todaySessions.isEmpty()),
             dailyCalorieGoal = if (showNutrition) uiState.dailyCalorieGoal else null,
             consumedCalories = if (showNutrition) uiState.todayNutritionTotals.calories.toInt() else 0,
            onStartWorkout = onStartWorkout,
            onRegisterCompetition = onRegisterCompetition,
            onCreateProgram = openCreate,
            onAddMeal = { showFoodLogger = true },
            onNavigateToProfile = onNavigateToProfile,
            hazeState = rootHazeState,
            onBottomPositionChanged = { headerBottomY = it },
            ringsCoverage = augeSnapshot.coverage ?: com.example.kpkn.domain.onboarding.RingsCoverage.NO_DATA,
            barAlpha = mastheadBarAlpha,
        )
    }
    val latestHeaderOverlayChange by rememberUpdatedState(onHeaderOverlayChange)
    // Holder so dispose only clears if this composition still owns the slot.
    val headerRegistration = remember {
        object {
            var active: HomeGlassOverlay? = null
        }
    }
    DisposableEffect(Unit) {
        val registered: HomeGlassOverlay = { rootHazeState ->
            latestHeaderContent(rootHazeState)
        }
        headerRegistration.active = registered
        latestHeaderOverlayChange(registered, null)
        onDispose {
            val owned = headerRegistration.active
            headerRegistration.active = null
            // Clear only our own registration so a remounted Home is not wiped by this dispose.
            if (owned != null) {
                latestHeaderOverlayChange(null, owned)
            }
        }
    }
    // Re-assert after the first frame: covers race where a previous Home's onDispose
    // cleared the slot after this composition already registered.
    LaunchedEffect(Unit) {
        val owned = headerRegistration.active
        if (owned != null) {
            latestHeaderOverlayChange(owned, null)
        }
    }
    Box(Modifier.fillMaxSize().background(WizardColors.background)) {
            HomeWithProgram(
                viewModel = viewModel,
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                augeLoading = augeLoading,
                perMuscle = augePerMuscle,
                todaySessions = uiState.todaySessions,
                homeCompetition = uiState.homeCompetition,
                hasActiveProgram = uiState.hasActiveProgram,
                activeProgramId = uiState.activeProgramId,
                programs = uiState.programs,
                listState = listState,
                onSessionAnchorPositionChanged = { sessionAnchorY = it },
                onRingsAnchorPositionChanged = { ringsAnchorY = it },
                onNutritionAnchorPositionChanged = { nutritionAnchorY = it },
                onStartWorkout = onStartWorkout,
                onRegisterCompetition = onRegisterCompetition,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
                onNavigateToProgram = onNavigateToProgram,
                onCreateProgram = openCreate,
                onNavigateToCard = onNavigateToCard,
                onNavigate = { destination ->
                    if (destination == "settings/auge") showAugeRecommendations = true else onNavigate(destination)
                },
                augeSnapshot = augeSnapshot,
                onDismissAdvisory = augeViewModel::dismissAdvisory,
                onModelNoticeShown = augeViewModel::markModelUpdateNoticeShown,
                overtrainedMuscles = uiState.overtrainedMuscles,
                 onAddMeal = { meal ->
                     if (showNutrition) {
                         loggerMealChoice = meal
                         showFoodLogger = true
                     }
                 },
                 onOpenNutritionOverlay = { if (showNutrition) showNutritionOverlay = true },
                 showNutrition = showNutrition,
                 selectedDate = selectedDate,
                 onSelectDate = { selectedDate = it },
                 mastheadState = mastheadState,
                 weekDays = weekDays,
                 onMastheadPositionChanged = { mastheadBottomY = it },
                 onOpenProfile = onNavigateToProfile,
                 todayMeals = todayMeals,
                 selectedChannel = selectedChannel,
                 onSelectedChannelChange = { selectedChannelName = it?.name },
                 onSaveRingAdjustment = { channel, value ->
                     when (channel) {
                         RecoveryChannelId.MUSCULAR -> augeViewModel.applyManualBatteries(muscular = value)
                         RecoveryChannelId.SYSTEM -> augeViewModel.applyManualBatteries(neural = value)
                         RecoveryChannelId.STRUCTURE -> augeViewModel.applyManualBatteries(spinal = value)
                     }
                 },
                 onNavigateToNutritionEditor = onNavigateToNutritionEditor,
                 modifier = Modifier
                     .fillMaxSize()
                     .navigationBarsPadding(),
             )

        // Velo fijo bajo la barra de estado (I8): va por encima de la lista y por debajo de la barra superior, que es hermana del NavHost.
        HomeStatusVeil(modifier = Modifier.align(Alignment.TopCenter))

        if (showFoodLogger) {
            // C12: la misma regla horaria que Nutrición, resuelta cada vez que se abre el logger. WP-U7: se guarda con el
            // resto del estado (la semilla del borrador manda de todos modos al restaurar).
            val loggerMeal by rememberSaveable(stateSaver = MealTypeSaver) {
                mutableStateOf(loggerMealChoice ?: com.example.kpkn.domain.nutrition.defaultMealTypeNow())
            }
            HomeFoodLoggerHost(
                nutritionRepo = nutritionRepo,
                selectedMealForLogger = loggerMeal,
                onDismiss = {
                    showFoodLogger = false
                    loggerMealChoice = null
                },
            )
        }
        if (showAugeRecommendations) {
            KpknAlertDialog(
                onDismissRequest = { showAugeRecommendations = false },
                title = { Text("Recomendación de recuperación", fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("KPKN detectó señales de fatiga y te muestra una recomendación calculada para tu sesión.")
                        augeSnapshot.autoDeloadMessage?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        if (uiState.overtrainedMuscles.isNotEmpty()) {
                            Text("Zonas con fatiga acumulada: ${uiState.overtrainedMuscles.joinToString(", ")}.")
                        }
                        Text("Es una sugerencia a partir de tu historial, no un diagnóstico. Puedes seguir entrenando o bajar volumen según cómo te sientas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { TextButton(onClick = { showAugeRecommendations = false }) { Text("Cerrar") } },
            )
        }
        if (showCreateProgramSheet) {
            CreateProgramTemplateSheet(
                onDismiss = viewModel::dismissCreateProgramSheet,
                onCreateBlank = {
                    val id = programsVm.createBlankProgram()
                    viewModel.onProgramCreated(id, activate = pendingProgramName != null)
                    onNavigateToProgram(id)
                },
                onCreateFromTemplate = { template ->
                    createScope.launch {
                        when (val outcome = programsVm.createProgramFromTemplateGated(template.id)) {
                            is ProgramsViewModel.TemplateApplyOutcome.Created -> {
                                viewModel.onProgramCreated(outcome.programId, activate = pendingProgramName != null)
                                onNavigateToProgram(outcome.programId)
                            }
                            ProgramsViewModel.TemplateApplyOutcome.RequiresCalibration ->
                                viewModel.requestVolumeCalibrationForTemplate(template.id)
                        }
                    }
                },
                onSelectProtocol = { protocol ->
                    viewModel.dismissCreateProgramSheet()
                    selectedProtocol = protocol
                },
                // E-18: con el asistente disponible, TODA tarjeta de la biblioteca (plan propio, de autor, plantilla o
                // método) sigue al asistente con el plan elegido; el atajo directo ya no se ofrece desde aquí.
                onSelectPlan = { entry ->
                    viewModel.dismissCreateProgramSheet()
                    onOpenSetupWizard(entry.id)
                },
                onOpenConcept = onOpenConcept?.let { open ->
                    { conceptId ->
                        viewModel.dismissCreateProgramSheet()
                        open(conceptId)
                    }
                },
            )
        }
        selectedProtocol?.let { protocol ->
            ProtocolDetailSheet(
                protocol = protocol,
                onDismiss = { selectedProtocol = null },
                onContinue = {
                    selectedProtocol = null
                    protocolForTm = protocol
                },
            )
        }
        protocolForTm?.let { protocol ->
            TrainingMaxWizard(
                initial = programsVm.estimatedProfileFromHistory(),
                trainingMaxPercent = protocol.recipe?.trainingMaxPercent ?: 0.90,
                onDismiss = {
                    val id = programsVm.createProgramFromProtocol(protocol.id, preferredName = pendingProgramName)
                    protocolForTm = null
                    if (id != null) {
                        viewModel.onProgramCreated(id, activate = true)
                        onNavigateToProgram(id)
                    } else {
                        viewModel.requestVolumeCalibrationForProtocol(protocol, null, pendingProgramName)
                    }
                },
                onConfirm = { profile ->
                    val id = programsVm.createProgramFromProtocol(protocol.id, profile, pendingProgramName)
                    protocolForTm = null
                    if (id != null) {
                        viewModel.onProgramCreated(id, activate = true)
                        onNavigateToProgram(id)
                    } else {
                        viewModel.requestVolumeCalibrationForProtocol(protocol, profile, pendingProgramName)
                    }
                },
            )
        }
        templateError?.let { message ->
            KpknAlertDialog(
                onDismissRequest = { templateError = null },
                title = { Text("No se pudo aplicar la plantilla", fontWeight = FontWeight.Black) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { templateError = null }) { Text("Entendido") }
                },
            )
        }
        pendingVolumeTemplateId?.let { templateId ->
            VolumeCalibrationSheet(
                currentMode = ProgramMode.HYPERTROPHY,
                onDismiss = {
                    val tid = templateId
                    viewModel.dismissVolumeCalibrationRequest()
                    createScope.launch {
                        when (val outcome = programsVm.createProgramFromTemplateGated(tid, skipCalibration = true)) {
                            is ProgramsViewModel.TemplateApplyOutcome.Created -> {
                                viewModel.onProgramCreated(outcome.programId, activate = pendingProgramName != null)
                                onNavigateToProgram(outcome.programId)
                            }
                            else -> {}
                        }
                    }
                },
                onSave = { result ->
                    viewModel.dismissVolumeCalibrationRequest()
                    createScope.launch {
                        programsVm.applyCalibrationToLatestDraft(result)
                        when (val outcome = programsVm.createProgramFromTemplateGated(templateId)) {
                            is ProgramsViewModel.TemplateApplyOutcome.Created -> {
                                viewModel.onProgramCreated(outcome.programId, activate = pendingProgramName != null)
                                onNavigateToProgram(outcome.programId)
                            }
                            ProgramsViewModel.TemplateApplyOutcome.RequiresCalibration ->
                                templateError = "No se pudo crear el programa ni con calibración."
                        }
                    }
                },
            )
        }
        pendingVolumeProtocol?.let { protocol ->
            VolumeCalibrationSheet(
                currentMode = ProgramMode.POWERLIFTING,
                onDismiss = {
                    val prot = protocol
                    val prof = pendingVolumeProfile
                    val name = pendingProgramName
                    viewModel.dismissVolumeCalibrationRequest()
                    val id = programsVm.createProgramFromProtocol(
                        prot.id,
                        prof,
                        name,
                        skipCalibration = true,
                    )
                    if (id != null) {
                        viewModel.onProgramCreated(id, activate = true)
                        onNavigateToProgram(id)
                    }
                },
                onSave = { result ->
                    viewModel.dismissVolumeCalibrationRequest()
                    val id = programsVm.createProgramFromProtocol(
                        protocol.id,
                        pendingVolumeProfile,
                        pendingProgramName,
                        calibration = result,
                    )
                    if (id != null) {
                        viewModel.onProgramCreated(id, activate = true)
                        onNavigateToProgram(id)
                    } else {
                        templateError = "No se pudo crear el programa ni con calibración."
                    }
                },
            )
        }
    }
}

/**
 * Velo fijo bajo la barra de estado (I8): la página que se desliza por arriba se funde con el negro de la marca. Alto = barra de
 * estado + 24 dp, de [WizardColors.background] pleno arriba a transparente abajo. No capta toques: el tacto pasa a la lista.
 */
@Composable
private fun HomeStatusVeil(modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusTop + 24.dp)
            .background(Brush.verticalGradient(listOf(WizardColors.background, Color.Transparent))),
    )
}

/**
 * Opacidad de la barra superior: 0 mientras el borde inferior de la cabecera de la página está a 48 dp o más de cruzar el de la
 * barra, y 1 cuando ya lo ha cruzado. Sin medidas (valores infinitos) la barra no aparece.
 */
private fun homeBarAlphaFor(barBottomY: Float, mastheadBottomY: Float, distancePx: Float): Float =
    if (barBottomY.isFinite() && mastheadBottomY.isFinite()) {
        ((barBottomY + distancePx - mastheadBottomY) / distancePx).coerceIn(0f, 1f)
    } else {
        0f
    }

@Composable
private fun HomeFoodLoggerHost(
    nutritionRepo: NutritionRepository,
    selectedMealForLogger: MealType,
    onDismiss: () -> Unit,
) {
    val foodDatabase by nutritionRepo.foodDatabase.collectAsState()
    FoodLoggerDrawer(
        nutritionRepo = nutritionRepo,
        isOpen = true,
        onDismiss = onDismiss,
        onSave = { log, confirmations -> nutritionRepo.saveNutritionLog(log, confirmations) },
        foodDatabase = foodDatabase,
        initialDate = LocalDate.now().toString(),
        initialMealType = selectedMealForLogger,
        initialDescription = null,
        initialTab = 0,
    )
}

@Composable
private fun HomeWithProgram(
    viewModel: HomeViewModel,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    augeLoading: Boolean = false,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    todaySessions: List<TodaySessionItem>,
    homeCompetition: com.example.kpkn.domain.training.HomeCompetitionState?,
    hasActiveProgram: Boolean,
    activeProgramId: String?,
    programs: List<Program>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSessionAnchorPositionChanged: (Float) -> Unit,
    onRingsAnchorPositionChanged: (Float) -> Unit,
    onNutritionAnchorPositionChanged: (Float) -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onRegisterCompetition: (String) -> Unit,
    onResumeWorkout: () -> Unit,
    onEditSession: (Session, Program) -> Unit = { _, _ -> },
    onNavigateToProgram: (String) -> Unit,
    onCreateProgram: () -> Unit,
    onNavigateToCard: (String) -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    augeSnapshot: AugeSnapshot? = null,
    onDismissAdvisory: (String) -> Unit = {},
    onModelNoticeShown: () -> Unit = {},
    overtrainedMuscles: List<String> = emptyList(),
    onAddMeal: (MealType?) -> Unit = {},
    onOpenNutritionOverlay: () -> Unit = {},
    showNutrition: Boolean = true,
    /** Día elegido en «Tu semana» (lo usa la sesión del día, I2). */
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    mastheadState: HomeMastheadState,
    weekDays: List<HomeWeekDay>,
    onMastheadPositionChanged: (Float) -> Unit,
    onOpenProfile: () -> Unit,
    /** Comidas de hoy (sin planeadas): los símbolos de «Nutrición de hoy». */
    todayMeals: List<NutritionLog> = emptyList(),
    /** Canal con el detalle de RINGS abierto, su cambio y el guardado del ajuste manual (lo cablea HomeScreen). */
    selectedChannel: RecoveryChannelId? = null,
    onSelectedChannelChange: (RecoveryChannelId?) -> Unit = {},
    onSaveRingAdjustment: (RecoveryChannelId, Int) -> Unit = { _, _ -> },
    /** Abre el editor del plan de nutrición: la acción «Definir objetivos» de «Nutrición de hoy» (sin meta de kcal). */
    onNavigateToNutritionEditor: () -> Unit = {},
) {
    val homeAdapt = LocalViewportAdapt.current
    val isCatalogReady by exerciseCatalogReady.collectAsState()
    // «Añadir la fecha de tu competición» (C4c): id del programa activo que aún no tiene fecha clave, o null.
    val competitionDatePromptProgramId by viewModel.competitionDatePromptProgramId.collectAsState()
    // «Nutrición de hoy» y «Cuerpo» (I4): totales y metas del día, y el estado del cuerpo, desde el ViewModel.
    val nutritionTotals by viewModel.todayNutritionTotals.collectAsState()
    val calorieGoal by viewModel.dailyCalorieGoal.collectAsState()
    val proteinGoal by viewModel.dailyProteinGoal.collectAsState()
    val carbGoal by viewModel.dailyCarbGoal.collectAsState()
    val fatGoal by viewModel.dailyFatGoal.collectAsState()
    val bodyToday by viewModel.bodyToday.collectAsState()
    val nutritionToday = remember(todayMeals, nutritionTotals, calorieGoal, proteinGoal, carbGoal, fatGoal) {
        homeNutritionState(todayMeals, nutritionTotals, calorieGoal, proteinGoal, carbGoal, fatGoal)
    }
    val homeAdvisory = remember(augeSnapshot, todaySessions, isCatalogReady) {
        val raw = augeSnapshot?.advisories
            ?.filter { LoadAdvisoryEngine.rank(it.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.ADJUST) }
            ?.firstOrNull()
            ?: return@remember null
        val session = todaySessions.firstOrNull { it.isToday && !it.isCompleted }
            ?: todaySessions.firstOrNull { !it.isCompleted }
            ?: todaySessions.firstOrNull()
        val hints = session?.session?.let { axialHintsForSession(it) }.orEmpty()
        LoadAdvisoryEngine.contextualize(raw, hints, axialCatalogAlternatives(hints))
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HomeSectionGap.adapt(homeAdapt)),
        // La cabecera de la página lleva su propia barra de estado: la lista arranca en el borde superior.
        contentPadding = PaddingValues(
            bottom = 140.dp.adapt(homeAdapt),
        ),
    ) {
        item(key = "masthead") {
            HomeMasthead(
                state = mastheadState,
                onOpenProfile = onOpenProfile,
                modifier = Modifier
                    .homeEntrance(0)
                    .onGloballyPositioned { onMastheadPositionChanged(it.positionInRoot().y + it.size.height) },
            )
        }
        if (hasActiveProgram && weekDays.isNotEmpty()) {
            item(key = "week") {
                HomeWeekStrip(
                    days = weekDays,
                    selected = selectedDate,
                    onSelect = onSelectDate,
                    modifier = Modifier.homeEntrance(1),
                )
            }
        }

        item(key = "session") {
            HomeSessionSection(
                selectedDate = selectedDate,
                weekDays = weekDays,
                activeProgram = programs.firstOrNull { it.id == activeProgramId },
                onNavigateToProgram = onNavigateToProgram,
                onOpenMuscleDetail = { onSelectedChannelChange(RecoveryChannelId.MUSCULAR) },
                sessions = todaySessions,
                homeCompetition = homeCompetition,
                hasActiveProgram = hasActiveProgram,
                perMuscle = perMuscle,
                loadAdvisory = homeAdvisory,
                onStartWorkout = onStartWorkout,
                onRegisterCompetition = onRegisterCompetition,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
                onCreateProgram = onCreateProgram,
                competitionDatePromptProgramId = competitionDatePromptProgramId,
                onOpenProgramForCompetitionDate = onNavigateToProgram,
                modifier = Modifier.onGloballyPositioned { onSessionAnchorPositionChanged(it.positionInRoot().y) }.homeEntrance(2),
            )
        }

        item(key = "rings") {
            HomeRingsSection(
                muscularProgress = muscularProgress,
                sncProgress = sncProgress,
                columnaProgress = columnaProgress,
                hasActiveProgram = hasActiveProgram,
                isLoading = augeLoading,
                dashboard = augeSnapshot?.dashboard,
                snapshot = augeSnapshot,
                showModelUpdateNotice = augeSnapshot?.showModelUpdateNotice == true,
                selectedChannel = selectedChannel,
                onSelectedChannelChange = onSelectedChannelChange,
                perMuscle = perMuscle,
                onSaveAdjustment = onSaveRingAdjustment,
                onDismissAdvisory = onDismissAdvisory,
                onModelNoticeShown = onModelNoticeShown,
                modifier = Modifier.onGloballyPositioned { onRingsAnchorPositionChanged(it.positionInRoot().y) }.homeEntrance(3),
            )
        }
        if (homeAdvisory != null) {
            item(key = "auto-deload") {
                HomeNotice(
                    title = homeAdvisory.title,
                    body = homeAdvisory.body,
                    actionLabel = "Entendido",
                    onAction = { onDismissAdvisory(homeAdvisory.id) },
                    tone = if (homeAdvisory.level == LoadAdvisoryLevel.UNLOAD) HomeNoticeTone.DANGER else HomeNoticeTone.WARN,
                    modifier = Modifier.homeEntrance(4),
                )
            }
        }
        if (overtrainedMuscles.isNotEmpty()) {
            item(key = "overtraining") {
                HomeNotice(
                    title = "Posible exceso de volumen",
                    body = "Hay señales de carga alta en: ${overtrainedMuscles.joinToString(", ")}. Considera reducir series semanales o un descanso activo.",
                    actionLabel = "Ver recomendaciones",
                    onAction = { onNavigate("settings/auge") },
                    tone = HomeNoticeTone.DANGER,
                    modifier = Modifier.homeEntrance(5),
                )
            }
        }
        item(key = "cards") {
            // «Nutrición de hoy» y «Cuerpo» van en un solo ítem: comparten la entrada (índice 6) y el aire entre secciones.
            Column(
                modifier = Modifier.homeEntrance(6),
                verticalArrangement = Arrangement.spacedBy(HomeSectionGap.adapt(homeAdapt)),
            ) {
                if (showNutrition) {
                    HomeNutritionToday(
                        state = nutritionToday,
                        onAddMeal = onAddMeal,
                        onOpenDay = onOpenNutritionOverlay,
                        onDefineGoals = onNavigateToNutritionEditor,
                        modifier = Modifier.onGloballyPositioned { onNutritionAnchorPositionChanged(it.positionInRoot().y) },
                    )
                }
                HomeBodyToday(
                    state = bodyToday,
                    onOpen = onNavigateToCard,
                )
            }
        }
        item(key = "programs") {
            Spacer(Modifier.height(8.dp))
            HomeProgramsSection(
                programs = programs,
                activeProgramId = activeProgramId,
                onProgramClick = onNavigateToProgram,
                onCreateProgram = onCreateProgram,
                modifier = Modifier.tourAnchor(TourAnchors.HOME_PROGRAMAS).homeEntrance(7),
                activeProgramLine = mastheadState.program,
                onSeeAll = { onNavigate(com.example.kpkn.navigation.KpknRoute.Training.route) },
            )
        }
        item(key = "wikilab") {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.homeEntrance(8)) {
                HomeWikiLabSection(onNavigate = onNavigate)
            }
        }
    }
}

@Composable
internal fun HomeTopBar(
    modifier: Modifier = Modifier,
    greeting: String,
    userName: String,
    greetingProgress: Float,
    ringsProgress: Float,
    sessionProgress: Float,
    nutritionProgress: Float,
    hasPrograms: Boolean,
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    primarySession: TodaySessionItem?,
    homeCompetition: com.example.kpkn.domain.training.HomeCompetitionState?,
    isRestDay: Boolean,
    dailyCalorieGoal: Int?,
    consumedCalories: Int,
    onStartWorkout: (Session, Program) -> Unit,
    onRegisterCompetition: (String) -> Unit,
    onCreateProgram: () -> Unit,
    onAddMeal: () -> Unit,
    onNavigateToProfile: () -> Unit,
    hazeState: HazeState,
    onBottomPositionChanged: (Float) -> Unit,
    ringsCoverage: com.example.kpkn.domain.onboarding.RingsCoverage = com.example.kpkn.domain.onboarding.RingsCoverage.NO_DATA,
    /** Opacidad de la cabecera de la página respecto a la barra: con histéresis (≥ 0,55 la muestra, ≤ 0,45 la oculta). */
    barAlpha: Float = 1f,
) {
    // Histéresis (§3): aparece en ≥ 0,55 y se va en ≤ 0,45; entre ambos conserva lo que tenía.
    val shownState = remember { mutableStateOf(false) }
    val shown = homeBarShownAfter(previous = shownState.value, barAlpha = barAlpha)
    SideEffect { shownState.value = shown }

    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    val slidePx = with(density) { 24.dp.roundToPx() }
    val fallPx = with(density) { 16.dp.roundToPx() }
    val contextSlidePx = with(density) { 8.dp.roundToPx() }
    val sixPx = with(density) { 6.dp.toPx() }
    val origin = TransformOrigin(0.5f, 0f)
    // Entrada y salida de la isla (§3). Con movimiento reducido, solo fundidos de 120 ms.
    val enter = if (reduced) {
        fadeIn(animationSpec = tween(durationMillis = 120))
    } else {
        fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) { -slidePx } +
            scaleIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.92f, transformOrigin = origin)
    }
    val exit = if (reduced) {
        fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        fadeOut(animationSpec = tween(durationMillis = 140)) +
            slideOutVertically(animationSpec = tween(durationMillis = 160)) { -fallPx } +
            scaleOut(animationSpec = tween(durationMillis = 160), targetScale = 0.96f, transformOrigin = origin)
    }
    val context = homeBarContextFor(sessionProgress = sessionProgress, ringsProgress = ringsProgress, nutritionProgress = nutritionProgress)

    // Caja fija de 56 dp bajo la barra de estado, con 12 dp de margen. Siempre compuesta, para medir su borde aunque la isla esté oculta.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .height(56.dp)
            .onGloballyPositioned { onBottomPositionChanged(it.positionInRoot().y + it.size.height) },
    ) {
        // Con la isla oculta no se compone nada que toque: no roba toques a la cabecera de la página.
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.fillMaxSize(),
            enter = enter,
            exit = exit,
        ) {
            // Contenido escalonado (§3): icono a 0 ms, textos a 40 ms y avatar a 80 ms, con fundido y 6 dp en horizontal.
            val appear = remember { MutableTransitionState(false).apply { targetState = true } }
            val stagger = updateTransition(transitionState = appear, label = "homeBarStagger")
            val iconIn by stagger.animateFloat(
                transitionSpec = { tween(durationMillis = if (reduced) 120 else 180) },
                label = "homeBarIconIn",
            ) { if (it) 1f else 0f }
            val textIn by stagger.animateFloat(
                transitionSpec = { tween(durationMillis = if (reduced) 120 else 180, delayMillis = if (reduced) 0 else 40) },
                label = "homeBarTextIn",
            ) { if (it) 1f else 0f }
            val avatarIn by stagger.animateFloat(
                transitionSpec = { tween(durationMillis = if (reduced) 120 else 180, delayMillis = if (reduced) 0 else 80) },
                label = "homeBarAvatarIn",
            ) { if (it) 1f else 0f }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .kpknGlass(hazeState, KpknShape.island, variant = KpknGlassVariant.ISLAND),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Icono del contexto: al cambiar de contexto sube 8 dp con fundido cruzado de 220 ms.
                    AnimatedContent(
                        targetState = context,
                        label = "homeBarContext",
                        transitionSpec = {
                            if (reduced) {
                                fadeIn(animationSpec = tween(durationMillis = 120))
                                    .togetherWith(fadeOut(animationSpec = tween(durationMillis = 120)))
                            } else {
                                (
                                    fadeIn(animationSpec = tween(durationMillis = 220)) +
                                        slideInVertically(animationSpec = tween(durationMillis = 220)) { contextSlidePx }
                                    ).togetherWith(fadeOut(animationSpec = tween(durationMillis = 220)))
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            alpha = iconIn
                            translationX = if (reduced) 0f else (1f - iconIn) * -sixPx
                        },
                    ) { target ->
                        HomeBarContextIcon(icon = homeBarIcon(target), accent = homeBarAccent(target))
                    }
                    val boxHeightDp = 24.dp
                    val topBarDensity = LocalDensity.current
                    val boxHeightPx = with(topBarDensity) { boxHeightDp.toPx() }

                    val greetingAlpha: Float
                    val greetingSlide: Float
                    val ringsAlpha: Float
                    val ringsSlide: Float
                    val sessionAlpha: Float
                    val sessionSlide: Float
                    val nutritionAlpha: Float
                    val nutritionSlide: Float

                    // Initially empty. Each mini-card appears only when its matching
                    // section reaches the pinned header, then hands off to the next one.
                    if (nutritionProgress > 0f) {
                        nutritionAlpha = nutritionProgress
                        nutritionSlide = (1f - nutritionProgress) * boxHeightPx
                        ringsAlpha = 1f - nutritionProgress
                        ringsSlide = -nutritionProgress * boxHeightPx
                        sessionAlpha = 0f
                        sessionSlide = 0f
                        greetingAlpha = 0f
                        greetingSlide = 0f
                    } else if (ringsProgress > 0f) {
                        ringsAlpha = ringsProgress
                        ringsSlide = (1f - ringsProgress) * boxHeightPx
                        sessionAlpha = 1f - ringsProgress
                        sessionSlide = -ringsProgress * boxHeightPx
                        nutritionAlpha = 0f
                        nutritionSlide = 0f
                        greetingAlpha = 0f
                        greetingSlide = 0f
                    } else if (sessionProgress > 0f) {
                        sessionAlpha = sessionProgress
                        sessionSlide = (1f - sessionProgress) * boxHeightPx
                        greetingAlpha = 1f - sessionProgress
                        greetingSlide = -sessionProgress * boxHeightPx
                        ringsAlpha = 0f
                        ringsSlide = 0f
                        nutritionAlpha = 0f
                        nutritionSlide = 0f
                    } else {
                        greetingAlpha = greetingProgress
                        greetingSlide = (1f - greetingProgress) * boxHeightPx
                        sessionAlpha = 0f
                        sessionSlide = 0f
                        ringsAlpha = 0f
                        ringsSlide = 0f
                        nutritionAlpha = 0f
                        nutritionSlide = 0f
                    }
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f)
                            .graphicsLayer {
                                alpha = textIn
                                translationX = if (reduced) 0f else (1f - textIn) * -sixPx
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        HomeGreetingLine(
                            greeting = greeting,
                            userName = userName,
                            modifier = Modifier.graphicsLayer { alpha = greetingAlpha; translationY = greetingSlide },
                        )
                        MiniSessionCard(
                            hasPrograms = hasPrograms,
                            primarySession = primarySession,
                            homeCompetition = homeCompetition,
                            isRestDay = isRestDay,
                            onStartWorkout = onStartWorkout,
                            onRegisterCompetition = onRegisterCompetition,
                            onCreateProgram = onCreateProgram,
                            modifier = Modifier.graphicsLayer { alpha = sessionAlpha; translationY = sessionSlide },
                        )
                        MiniRingsWidget(
                            muscularProgress = muscularProgress,
                            sncProgress = sncProgress,
                            columnaProgress = columnaProgress,
                            hasActiveProgram = hasPrograms,
                            coverage = ringsCoverage,
                            modifier = Modifier.graphicsLayer { alpha = ringsAlpha; translationY = ringsSlide },
                        )
                        MiniNutritionCard(
                            dailyCalorieGoal = dailyCalorieGoal,
                            consumedCalories = consumedCalories,
                            onAddMeal = onAddMeal,
                            modifier = Modifier.graphicsLayer { alpha = nutritionAlpha; translationY = nutritionSlide },
                        )
                    }
                    HomeBarAvatar(
                        userName = userName,
                        onClick = onNavigateToProfile,
                        modifier = Modifier.graphicsLayer {
                            alpha = avatarIn
                            translationX = if (reduced) 0f else (1f - avatarIn) * -sixPx
                        },
                    )
                }
            }
        }
    }
}

/** Contexto que muestra la isla: el del texto más visible del relevo según el scroll (nutrición, RINGS, sesión o saludo). */
internal enum class HomeBarContext { GREETING, SESSION, RINGS, NUTRITION }

/** Contexto dominante de la isla: el del texto que ya pasa de la mitad en el relevo, con el mismo orden que los textos. */
internal fun homeBarContextFor(sessionProgress: Float, ringsProgress: Float, nutritionProgress: Float): HomeBarContext = when {
    nutritionProgress > 0f -> if (nutritionProgress >= 0.5f) HomeBarContext.NUTRITION else HomeBarContext.RINGS
    ringsProgress > 0f -> if (ringsProgress >= 0.5f) HomeBarContext.RINGS else HomeBarContext.SESSION
    sessionProgress > 0f -> if (sessionProgress >= 0.5f) HomeBarContext.SESSION else HomeBarContext.GREETING
    else -> HomeBarContext.GREETING
}

/** Histéresis de la isla (§3): aparece en ≥ 0,55, se va en ≤ 0,45 y entre ambos conserva lo que tenía. */
internal fun homeBarShownAfter(previous: Boolean, barAlpha: Float): Boolean = when {
    barAlpha >= 0.55f -> true
    barAlpha <= 0.45f -> false
    else -> previous
}

/** Icono de cada contexto. Son de Material hasta que llegue el set KPKN Line (I11b): solo cambia esta función. */
internal fun homeBarIcon(context: HomeBarContext): ImageVector = when (context) {
    HomeBarContext.GREETING -> Icons.Filled.Home
    HomeBarContext.SESSION -> Icons.Filled.FitnessCenter
    HomeBarContext.RINGS -> Icons.Filled.DonutLarge
    HomeBarContext.NUTRITION -> Icons.Filled.Restaurant
}

/** Acento de cada contexto (§3): sesión = músculo, RINGS = columna, nutrición = energía. El saludo va en tinta. */
internal fun homeBarAccent(context: HomeBarContext): Color = when (context) {
    HomeBarContext.GREETING -> WizardColors.text
    HomeBarContext.SESSION -> SymbolPalette.musculo
    HomeBarContext.RINGS -> SymbolPalette.columna
    HomeBarContext.NUTRITION -> SymbolPalette.energia
}

/** Icono del contexto: círculo `inset` de 36 dp con el icono de 20 dp teñido con su acento. Sustituye al logo de 43 dp. */
@Composable
private fun HomeBarContextIcon(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(KpknSurface.inset),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent)
    }
}

/** Acción del contexto (play, «+»): círculo de tinta de 36 dp con el icono en negro, dentro de una zona táctil de 48 dp. */
@Composable
private fun HomeBarInkAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WizardColors.text),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = WizardColors.ctaContent)
        }
    }
}

/**
 * Avatar de la isla: círculo de 36 dp con filete y la inicial del nombre, en una zona táctil de 48 dp. Abre Perfil. Sin nombre
 * muestra la silueta de Material.
 */
@Composable
private fun HomeBarAvatar(userName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val initial = userName.trim().firstOrNull()?.uppercase().orEmpty()
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { this.contentDescription = "Perfil" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset)
                .border(1.dp, WizardColors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (initial.isNotEmpty()) {
                Text(text = initial, style = HomeTypography.avatarInitial, color = WizardColors.text)
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WizardColors.text,
                )
            }
        }
    }
}

/**
 * Saludo de la cabecera con el nombre entero: el completo de 16 a 12 sp; si no cabe, «Hola, …» (también de 16 a 12 sp);
 * y solo si ni así cabe, se corta con «…». Siempre en una línea y con la tipografía de la barra.
 */
@Composable
private fun HomeGreetingLine(greeting: String, userName: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    // Sin «¡» ni «!», como la cabecera de la página: el saludo va en Syne 700 y en tinta.
    val style = HomeTypography.barTitle
    val clean = greeting.replace("¡", "").replace("!", "").trim()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        val widthPx = constraints.maxWidth.toFloat()
        val fit = remember(clean, userName, widthPx, density.density, density.fontScale) {
            fitHomeGreeting(
                full = "$clean, $userName",
                short = "Hola, $userName",
                widthPx = widthPx,
            ) { text, sp ->
                measurer.measure(
                    text = text,
                    style = style.copy(fontSize = sp.sp),
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
            }
        }
        Text(
            text = fit.text,
            style = style.copy(fontSize = fit.sp.sp),
            color = WizardColors.text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

/** Saludo elegido para una cabecera: su texto, su tamaño en sp y si va cortado con «…» (ni a 12 sp cabe). */
internal data class HomeGreetingFit(val text: String, val sp: Float, val truncated: Boolean)

private val homeGreetingSizesSp = listOf(16f, 15f, 14f, 13f, 12f)

/**
 * Elige el saludo para un ancho de [widthPx] píxeles: el [full] en el mayor tamaño de 16 a 12 sp (de uno en uno) que quepa
 * en una línea; si no cabe, el [short] con la misma bajada; y si ni así, el [short] a 12 sp (la pantalla lo corta con «…»).
 * [measureWidthPx] da el ancho de una línea de texto a un tamaño en sp.
 */
internal fun fitHomeGreeting(
    full: String,
    short: String,
    widthPx: Float,
    measureWidthPx: (text: String, sp: Float) -> Float,
): HomeGreetingFit {
    fun sizeFor(text: String): Float? = homeGreetingSizesSp.firstOrNull { measureWidthPx(text, it) <= widthPx }
    sizeFor(full)?.let { return HomeGreetingFit(full, it, truncated = false) }
    sizeFor(short)?.let { return HomeGreetingFit(short, it, truncated = false) }
    return HomeGreetingFit(short, homeGreetingSizesSp.last(), truncated = true)
}

@Composable
private fun MiniRingsWidget(
    muscularProgress: Float,
    sncProgress: Float,
    columnaProgress: Float,
    modifier: Modifier = Modifier,
    hasActiveProgram: Boolean = true,
    coverage: com.example.kpkn.domain.onboarding.RingsCoverage = com.example.kpkn.domain.onboarding.RingsCoverage.NO_DATA,
) {
    val channels = listOf(RecoveryChannelId.MUSCULAR, RecoveryChannelId.SYSTEM, RecoveryChannelId.STRUCTURE)
    val allProgress = listOf(muscularProgress, sncProgress, columnaProgress)
    // Sin datos el canal queda sin calibrar (pista discontinua), sin afirmar un valor.
    fun percent(i: Int): Int? = if (coverage.channel(channels[i]).hasData) {
        (allProgress[i] * 100).toInt().coerceIn(0, 100)
    } else {
        null
    }
    KpknTowerRings(
        values = TowerRingValues(columna = percent(2), musculos = percent(0), energia = percent(1)),
        modifier = modifier.fillMaxWidth().height(40.dp).alpha(if (hasActiveProgram) 1f else 0.5f),
    )
}

@Composable
private fun MiniSessionCard(
    hasPrograms: Boolean,
    primarySession: TodaySessionItem?,
    homeCompetition: com.example.kpkn.domain.training.HomeCompetitionState?,
    isRestDay: Boolean,
    onStartWorkout: (Session, Program) -> Unit,
    onRegisterCompetition: (String) -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val registerPhase = homeCompetition?.phase == com.example.kpkn.domain.training.CompetitionHomePhase.REGISTER
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (!hasPrograms) {
            Button(
                onClick = onCreateProgram,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WizardColors.text, contentColor = WizardColors.ctaContent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Crear programa", style = HomeTypography.barLabel, maxLines = 1)
            }
        } else if (registerPhase && homeCompetition != null) {
            Text(
                homeCompetition.title,
                style = HomeTypography.barLabel,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onRegisterCompetition(homeCompetition.recordId) },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = WizardColors.text),
            ) {
                Text("Registrar", style = HomeTypography.barLabel)
            }
        } else if (isRestDay || primarySession == null) {
            Text(
                "Día de descanso",
                style = HomeTypography.barLabel,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
        } else {
            Text(
                primarySession.session.name,
                style = HomeTypography.barLabel,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            HomeBarInkAction(
                icon = Icons.Filled.PlayArrow,
                contentDescription = "Iniciar",
                onClick = { onStartWorkout(primarySession.session, primarySession.program) },
            )
        }
    }
}

@Composable
private fun MiniNutritionCard(
    dailyCalorieGoal: Int?,
    consumedCalories: Int,
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // null = sin objetivos: progreso 0 y sin denominador inventado.
    val pct = if (dailyCalorieGoal != null && dailyCalorieGoal > 0) {
        (consumedCalories.toFloat() / dailyCalorieGoal.toFloat()).coerceIn(0f, 1.5f)
    } else {
        0f
    }
    // Bajo la meta, energía; en la meta, ok; pasada, el rojo suave de la marca.
    val progressColor = when {
        pct < 0.9f -> SymbolPalette.energia
        pct <= 1.1f -> SymbolPalette.ok
        else -> WizardColors.danger
    }
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Calorías",
                style = HomeTypography.barLabel,
                color = WizardColors.textMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(consumedCalories.toString(), style = HomeTypography.barTitle, color = WizardColors.text)
                Spacer(Modifier.width(2.dp))
                Text(
                    dailyCalorieGoal?.let { "/ $it" } ?: "sin objetivos",
                    style = HomeTypography.barLabel,
                    color = WizardColors.textFaint,
                )
            }
            // Barra de 3 dp hecha con cajas (Home no usa LinearProgressIndicator): la pista al 14 % y el relleno del color.
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 8.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(WizardColors.glassBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct.coerceAtMost(1f))
                        .fillMaxHeight()
                        .background(progressColor),
                ) {}
            }
        }
        HomeBarInkAction(icon = Icons.Default.Add, contentDescription = "Agregar", onClick = onAddMeal)
    }
}

/**
 * «Comidas de hoy»: el vidrio de siempre con las comidas registradas en filas sin caja (tipo, nombre y kcal, con un filete entre
 * filas) y la píldora principal «Registrar comida». Sin comidas, la frase de vacío.
 */
@Composable
private fun NutritionTodayGlassOverlay(
    hazeState: HazeState,
    meals: List<NutritionLog>,
    onDismiss: () -> Unit,
    onAddMeal: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardColors.background.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .kpknGlass(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
                ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("REGISTRO DE HOY", style = HomeTypography.eyebrow, color = WizardColors.textMuted)
                        Spacer(Modifier.height(4.dp))
                        Text("Comidas registradas", style = WizardTypography.questionCompact, color = WizardColors.text)
                    }
                    HomeSectionAction(text = "Cerrar", onClick = onDismiss)
                }
                if (meals.isEmpty()) {
                    Text("Aún no registras comidas hoy.", style = WizardTypography.body, color = WizardColors.textMuted)
                } else {
                    Column {
                        meals.forEachIndexed { index, meal ->
                            val minKcal = kotlin.math.round(meal.foods.sumOf { it.caloriesMin ?: it.calories }).toInt()
                            val maxKcal = kotlin.math.round(meal.foods.sumOf { it.caloriesMax ?: it.calories }).toInt()
                            val hasRange = maxKcal > minKcal
                            val isEstimate = hasRange || meal.foods.any { it.isUncertain }
                            val mealLabel = HomeMeals.firstOrNull { it.first == meal.mealType }?.second ?: "Comida"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(mealLabel.uppercase(), style = HomeTypography.eyebrow, color = WizardColors.textMuted)
                                    Text(
                                        meal.foods.take(3).joinToString(" · ") { it.foodName }.ifBlank { "Comida registrada" },
                                        style = WizardTypography.bodySmall,
                                        color = WizardColors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    meal.foods.mapNotNull { it.nutritionReferenceNote }.distinct().forEach { note ->
                                        Text(note, style = WizardTypography.note, color = WizardColors.textFaint)
                                    }
                                    if (hasRange) {
                                        Text(
                                            "Rango estimado: $minKcal–$maxKcal kcal",
                                            style = WizardTypography.note,
                                            color = WizardColors.textFaint,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${if (isEstimate) "≈ " else ""}${kotlin.math.round(meal.foods.sumOf { it.calories }).toInt()} kcal",
                                    style = HomeTypography.barTitle,
                                    color = WizardColors.text,
                                )
                            }
                            if (index < meals.lastIndex) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
                            }
                        }
                    }
                }
                HomePrimaryPill(
                    text = "Registrar comida",
                    onClick = onAddMeal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
