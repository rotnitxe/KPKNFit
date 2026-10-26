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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.screens.onboarding.design.entreno.MarksMath
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
import com.example.kpkn.data.exercises.exerciseCatalogReady
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
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
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknStatusVeil
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
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
import com.example.kpkn.screens.home.design.HomeGroupGap
import com.example.kpkn.screens.home.design.HomeModuleGap
import com.example.kpkn.screens.home.design.HomePageGap
import com.example.kpkn.screens.home.design.homeGapAbove
import com.example.kpkn.screens.home.design.HomeWeekStrip
import com.example.kpkn.screens.home.design.homeDateLine
import com.example.kpkn.screens.home.design.homeEntrance
import com.example.kpkn.screens.home.design.HomeBodyToday
import com.example.kpkn.screens.home.design.HomeNutritionToday
import com.example.kpkn.screens.home.design.HomeNotice
import com.example.kpkn.screens.home.design.HomeNoticeTone
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import kotlinx.coroutines.launch
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import kotlin.math.roundToInt

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
    val uiState by viewModel.uiState.collectAsState()
    val nutritionRepo = remember { NutritionRepository.getInstance() }
    // WP-U7 (C5): si la hoja del logger estaba abierta, tras la muerte del proceso se reabre con su borrador.
    var showFoodLogger by rememberSaveable { mutableStateOf(false) }
    // Comida elegida con el «+» o con una comida de «Nutrición de hoy»; null = la de la hora (defaultMealTypeNow).
    var loggerMealChoice by rememberSaveable { mutableStateOf<MealType?>(null) }
    var showNutritionOverlay by remember { mutableStateOf(false) }
    // «Tu semana»: el día elegido sobrevive a girar la pantalla; por defecto, hoy.
    var selectedDate by rememberSaveable(stateSaver = HomeDateSaver) { mutableStateOf(LocalDate.now()) }
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
    LaunchedEffect(Unit) { tourHost?.startTour(TourIds.HOME, TourScripts.home(conTorre = false)) }
    val nutritionLogs by nutritionRepo.nutritionLogs.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(uiState.activeProgramId) {
        viewModel.loadFeedbacks(context)
        viewModel.loadWellbeing(context)
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
    var nutritionAnchorY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    // Borde inferior de la cabecera de la página (coordenadas de la raíz): decide el fundido de la barra superior.
    var mastheadBottomY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }

    fun anchorProgress(anchorY: Float): Float =
        if (headerBottomY.isFinite() && anchorY.isFinite()) {
            ((headerBottomY - anchorY) / handoffDistancePx).coerceIn(0f, 1f)
        } else 0f

    val greetingProgress = 1f
    val sessionProgress by remember { derivedStateOf { anchorProgress(sessionAnchorY) } }
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
            sessionProgress = sessionProgress,
             nutritionProgress = if (showNutrition) nutritionProgress else 0f,
            hasPrograms = uiState.hasActiveProgram,
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
                todaySessions = uiState.todaySessions,
                homeCompetition = uiState.homeCompetition,
                hasActiveProgram = uiState.hasActiveProgram,
                activeProgramId = uiState.activeProgramId,
                programs = uiState.programs,
                listState = listState,
                onSessionAnchorPositionChanged = { sessionAnchorY = it },
                onNutritionAnchorPositionChanged = { nutritionAnchorY = it },
                onStartWorkout = onStartWorkout,
                onRegisterCompetition = onRegisterCompetition,
                onResumeWorkout = onResumeWorkout,
                onEditSession = onEditSession,
                onNavigateToProgram = onNavigateToProgram,
                onCreateProgram = openCreate,
                onNavigateToCard = onNavigateToCard,
                onNavigate = onNavigate,
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
                 onNavigateToNutritionEditor = onNavigateToNutritionEditor,
                 modifier = Modifier
                     .fillMaxSize()
                     .navigationBarsPadding(),
             )

        // Velo fijo bajo la barra de estado (I8): va por encima de la lista y por debajo de la barra superior, que es hermana del NavHost.
        KpknStatusVeil(modifier = Modifier.align(Alignment.TopCenter))

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
                unit = if (settings.weightUnit == WeightUnit.LBS) MarksMath.UNIT_LB else MarksMath.UNIT_KG,
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
    todaySessions: List<TodaySessionItem>,
    homeCompetition: com.example.kpkn.domain.training.HomeCompetitionState?,
    hasActiveProgram: Boolean,
    activeProgramId: String?,
    programs: List<Program>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSessionAnchorPositionChanged: (Float) -> Unit,
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
    /** Abre el editor del plan de nutrición: la acción «Definir objetivos» de «Nutrición de hoy» (sin meta de kcal). */
    onNavigateToNutritionEditor: () -> Unit = {},
) {
    val homeAdapt = LocalViewportAdapt.current
    val isCatalogReady by exerciseCatalogReady.collectAsState()
    val trainingMetrics by viewModel.trainingMetrics.collectAsState()
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
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Sin `spacedBy`: cada ítem pone su aire arriba con `homeGapAbove` (regla única de la página, I12).
        verticalArrangement = Arrangement.Top,
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
        // «Esta semana» y la sesión del día son un solo módulo (I10a): la semana arriba y, separada por un filete, la sesión.
        item(key = "week-session") {
            val showWeek = hasActiveProgram && weekDays.isNotEmpty()
            KpknModule(
                modifier = Modifier
                    .homeGapAbove(HomePageGap)
                    .padding(horizontal = 16.dp)
                    .onGloballyPositioned { onSessionAnchorPositionChanged(it.positionInRoot().y) }
                    .homeEntrance(1),
            ) {
                if (showWeek) {
                    KpknModuleHeader(
                        label = "Esta semana",
                        action = if (activeProgramId == null) null else {
                            { KpknTextAction(text = "Programa", onClick = { onNavigateToProgram(activeProgramId) }) }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    HomeWeekStrip(
                        days = weekDays,
                        selected = selectedDate,
                        onSelect = onSelectDate,
                        contentPadding = PaddingValues(0.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .height(1.dp)
                            .background(KpknSurface.divider),
                    )
                }
                HomeSessionSection(
                    selectedDate = selectedDate,
                    weekDays = weekDays,
                    activeProgram = programs.firstOrNull { it.id == activeProgramId },
                    onNavigateToProgram = onNavigateToProgram,
                    sessions = todaySessions,
                    homeCompetition = homeCompetition,
                    hasActiveProgram = hasActiveProgram,
                    onStartWorkout = onStartWorkout,
                    onRegisterCompetition = onRegisterCompetition,
                    onResumeWorkout = onResumeWorkout,
                    onEditSession = onEditSession,
                    onCreateProgram = onCreateProgram,
                    onChooseProgram = { onNavigate(com.example.kpkn.navigation.KpknRoute.Training.route) },
                    competitionDatePromptProgramId = competitionDatePromptProgramId,
                    onOpenProgramForCompetitionDate = onNavigateToProgram,
                )
                HomeSessionCue(
                    cues = trainingMetrics.cues,
                    onAction = { cue ->
                        val today = todaySessions.firstOrNull { it.program != null }
                        if (cue.action == "Ajustar" && today?.program != null) {
                            onEditSession(today.session, today.program)
                        } else onNavigateToCard("training-metrics")
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item(key = "training-history") {
            HomeMetricsSection(
                metrics = trainingMetrics,
                onOpenHistory = { onNavigateToCard("training-metrics") },
                onOpenProgress = { onNavigateToCard("training-metrics") },
            )
        }
        item(key = "cards") {
            // «Nutrición de hoy» y «Cuerpo» van en un solo ítem: comparten la entrada (índice 6) y el aire entre secciones.
            Column(
                modifier = Modifier.homeGapAbove(HomeModuleGap).homeEntrance(6),
                verticalArrangement = Arrangement.spacedBy(HomeModuleGap),
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
            HomeProgramsSection(
                programs = programs,
                activeProgramId = activeProgramId,
                onProgramClick = onNavigateToProgram,
                onCreateProgram = onCreateProgram,
                modifier = Modifier
                    .homeGapAbove(HomeGroupGap)
                    .tourAnchor(TourAnchors.HOME_PROGRAMAS)
                    .homeEntrance(7),
                activeProgramLine = mastheadState.program,
                onSeeAll = { onNavigate(com.example.kpkn.navigation.KpknRoute.Training.route) },
            )
        }
        item(key = "wikilab") {
            Box(Modifier.homeGapAbove(HomeGroupGap).homeEntrance(8)) {
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
    @Suppress("UNUSED_PARAMETER") greetingProgress: Float,
    sessionProgress: Float,
    nutritionProgress: Float,
    hasPrograms: Boolean,
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
    /** Opacidad de la cabecera de la página respecto a la barra: con histéresis (≥ 0,55 la muestra, ≤ 0,45 la oculta). */
    barAlpha: Float = 1f,
) {
    // Histéresis (§3): aparece en ≥ 0,55 y se va en ≤ 0,45; entre ambos conserva lo que tenía.
    val shownState = remember { mutableStateOf(false) }
    val shown = homeBarShownAfter(previous = shownState.value, barAlpha = barAlpha)
    SideEffect { shownState.value = shown }
    // Con una hoja o un diálogo abiertos la isla sale con su animación y no roba toques (I12).
    val modalOpen = KpknModalTracker.openCount > 0
    // Contexto discreto (I12): el de la última sección cuyo ancla ya pasó bajo la isla, con la histéresis de la aparición.
    val contextState = remember { mutableStateOf(HomeBarContext.GREETING) }
    val context = homeBarContextAfter(
        previous = contextState.value,
        sessionProgress = sessionProgress,
        nutritionProgress = nutritionProgress,
    )
    SideEffect { contextState.value = context }

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
            visible = shown && !modalOpen,
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
                    // Contexto discreto (I12): icono, textos y acción cambian a la vez; el contenido nuevo sube 8 dp con fundido cruzado
                    // de 220 ms. Con movimiento reducido, solo fundido de 120 ms.
                    AnimatedContent(
                        targetState = context,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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
                        label = "homeBarContext",
                    ) { target ->
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    alpha = iconIn
                                    translationX = if (reduced) 0f else (1f - iconIn) * -sixPx
                                },
                            ) {
                                HomeBarContextIcon(icon = homeBarIcon(target), accent = homeBarAccent(target))
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        alpha = textIn
                                        translationX = if (reduced) 0f else (1f - textIn) * -sixPx
                                    },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                when (target) {
                                    HomeBarContext.GREETING -> HomeGreetingLine(greeting = greeting, userName = userName)
                                    HomeBarContext.SESSION -> MiniSessionCard(
                                        hasPrograms = hasPrograms,
                                        primarySession = primarySession,
                                        homeCompetition = homeCompetition,
                                        isRestDay = isRestDay,
                                        onStartWorkout = onStartWorkout,
                                        onRegisterCompetition = onRegisterCompetition,
                                        onCreateProgram = onCreateProgram,
                                    )
                                    HomeBarContext.NUTRITION -> MiniNutritionCard(
                                        dailyCalorieGoal = dailyCalorieGoal,
                                        consumedCalories = consumedCalories,
                                        onAddMeal = onAddMeal,
                                    )
                                }
                            }
                        }
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

/** Contexto que muestra la isla, en el orden de la página: saludo, sesión, RINGS y nutrición. */
internal enum class HomeBarContext { GREETING, SESSION, NUTRITION }

/**
 * Contexto de la isla (I12): el de la última sección cuyo ancla ya pasó bajo la isla. Cada sección «pasa» al llegar a 0,55 y «vuelve»
 * al bajar de 0,45, con la misma histéresis que la aparición; entre ambos conserva lo que tenía. Así la isla cambia de una vez, sin
 * mezclar dos textos a medio relevo.
 */
internal fun homeBarContextAfter(
    previous: HomeBarContext,
    sessionProgress: Float,
    nutritionProgress: Float,
): HomeBarContext = when {
    homeBarPassed(nutritionProgress, wasPassed = previous == HomeBarContext.NUTRITION) -> HomeBarContext.NUTRITION
    homeBarPassed(sessionProgress, wasPassed = previous >= HomeBarContext.SESSION) -> HomeBarContext.SESSION
    else -> HomeBarContext.GREETING
}

/** Una sección ha pasado bajo la isla: con 0,55 o más sí; con 0,45 o menos no; entre ambos, lo que era antes. */
private fun homeBarPassed(progress: Float, wasPassed: Boolean): Boolean = when {
    progress >= 0.55f -> true
    progress <= 0.45f -> false
    else -> wasPassed
}

/** Histéresis de la isla (§3): aparece en ≥ 0,55, se va en ≤ 0,45 y entre ambos conserva lo que tenía. */
internal fun homeBarShownAfter(previous: Boolean, barAlpha: Float): Boolean = when {
    barAlpha >= 0.55f -> true
    barAlpha <= 0.45f -> false
    else -> previous
}

/** Icono de cada contexto (KPKN Line, §4): la torre en el saludo y en RINGS, la barra en la sesión y el cuenco en nutrición. */
internal fun homeBarIcon(context: HomeBarContext): ImageVector = when (context) {
    HomeBarContext.GREETING -> KpknLineIcons.Tower
    HomeBarContext.SESSION -> KpknLineIcons.Training
    HomeBarContext.NUTRITION -> KpknLineIcons.Nutrition
}

/** Acento de cada contexto (§3): sesión = músculo, RINGS = columna, nutrición = energía. El saludo va en tinta. */
internal fun homeBarAccent(context: HomeBarContext): Color = when (context) {
    HomeBarContext.GREETING -> WizardColors.text
    HomeBarContext.SESSION -> SymbolPalette.musculo
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
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.ctaContent)
        }
    }
}

/**
 * Avatar de la isla: círculo de 36 dp con filete y la inicial del nombre, en una zona táctil de 48 dp. Abre Perfil. Sin nombre
 * muestra la silueta KPKN Line.
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
                    imageVector = KpknLineIcons.Person,
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
    // Sangría de 8 dp: alinea el saludo con el texto de las mini tarjetas, que tienen el mismo margen interior.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
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
            modifier = modifier.semantics { heading() },
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
                modifier = Modifier.weight(1f).semantics { heading() },
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
                modifier = Modifier.semantics { heading() },
            )
        } else {
            Text(
                primarySession.session.name,
                style = HomeTypography.barLabel,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HomeBarInkAction(
                icon = KpknLineIcons.PlayFilled,
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
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatKcalEs(consumedCalories), style = HomeTypography.barTitle, color = WizardColors.text)
                Spacer(Modifier.width(2.dp))
                Text(
                    dailyCalorieGoal?.let { "/ ${formatKcalEs(it)}" } ?: "sin objetivos",
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
        HomeBarInkAction(icon = KpknLineIcons.Plus, contentDescription = "Agregar", onClick = onAddMeal)
    }
}

/**
 * «Comidas de hoy»: el vidrio de siempre con las comidas registradas en filas sin caja (tipo, nombre y kcal, con un filete entre
 * filas) y la píldora principal «Registrar comida». Sin comidas, la frase de vacío.
 */
@Composable
internal fun NutritionTodayGlassOverlay(
    hazeState: HazeState,
    meals: List<NutritionLog>,
    onDismiss: () -> Unit,
    onAddMeal: () -> Unit,
) {
    // Capa abierta: la isla superior se oculta mientras esté, como con una hoja o un diálogo (I12).
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
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
