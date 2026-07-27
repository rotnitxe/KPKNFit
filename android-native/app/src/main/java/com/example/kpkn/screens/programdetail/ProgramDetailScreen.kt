package com.example.kpkn.screens.programdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.data.models.CompetitionDetails
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.ProgramAnalyticsEngine
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.screens.auge.rememberAugeViewModel
import com.example.kpkn.screens.programdetail.components.*
import com.example.kpkn.services.workout.LoopNotificationManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.example.kpkn.ui.components.KpknAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onEditSession: (String) -> Unit,
    onCreateSession: (String, String, Int, Int, Int, Boolean) -> Unit,
    onOpenProgram: (String) -> Unit = {},
    onContextTabStateChange: (MainTab, (MainTab) -> Unit) -> Unit = { _, _ -> },
    initialTab: MainTab? = null,
    viewModel: ProgramDetailViewModel = viewModel(factory = ProgramDetailViewModel.factory(programId)),
) {
    val augeViewModel = rememberAugeViewModel()
    val program by viewModel.program.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isActive by viewModel.isActiveProgram.collectAsState()
    val isPaused by viewModel.isPausedProgram.collectAsState()
    val currentWeeks by viewModel.currentWeeks.collectAsState()
    val displayedSessions by viewModel.displayedSessions.collectAsState()
    val selectedWeekMeta by viewModel.selectedWeekMeta.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeekIndex by viewModel.currentWeekIndex.collectAsState()
    val roadmapBlocks by viewModel.roadmapBlocks.collectAsState()
    val simpleRoadmapLoopMarkers by viewModel.simpleRoadmapLoopMarkers.collectAsState()
    val programLogs by viewModel.programLogs.collectAsState()
    val isSimpleProgram by viewModel.isSimpleProgram.collectAsState()
    val activeProgramState by viewModel.activeProgramState.collectAsState()
    val showSimpleCalendarizationSheet by viewModel.showSimpleCalendarizationSheet.collectAsState()
    val calendarizationStartDate by viewModel.calendarizationStartDate.collectAsState()
    val calendarizationEndDate by viewModel.calendarizationEndDate.collectAsState()
    val calendarizationStartDayOfWeek by viewModel.calendarizationStartDayOfWeek.collectAsState()
    val calendarizationTrainingDays by viewModel.calendarizationTrainingDays.collectAsState()
    val augeSnapshot by augeViewModel.snapshot.collectAsState()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val context = LocalContext.current
    val muscleCdbsStatus by viewModel.muscleCdbsStatus.collectAsState()

    LaunchedEffect(programId) {
        viewModel.loadFeedbacks(context)
    }

    LaunchedEffect(programId, initialTab) {
        if (initialTab != null) viewModel.setActiveTab(initialTab)
    }

    LaunchedEffect(uiState.activeTab) {
        onContextTabStateChange(uiState.activeTab) { viewModel.setActiveTab(it) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showVolumeSetupNotice by remember { mutableStateOf(false) }
    var openVolumeSheetToken by remember { mutableIntStateOf(0) }
    var notifiedLoopWeekId by remember { mutableStateOf<String?>(null) }

    // Edge case: program not found
    val p = program
    if (p == null) {
        LaunchedEffect(Unit) {
            snackbarHostState.showKpknSnackbar("Programa no encontrado", SnackbarType.DANGER)
            kotlinx.coroutines.delay(1500)
            onBack()
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } }) {
            Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    LaunchedEffect(p.id, p.volumeSetupPromptSeen) {
        if (!p.volumeSetupPromptSeen) {
            viewModel.markVolumeSetupPromptSeen()
            showVolumeSetupNotice = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding() + 120.dp),
        ) {
            // Hero Banner
            CompactHeroBanner(
                programName = p.name,
                programDescription = p.description,
                coverValue = p.coverImage,
                isActive = isActive,
                isPaused = isPaused,
                focusMode = p.mode.name.lowercase(),
                muscularBattery = augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR),
                sncBattery = augeSnapshot.ringScore(RecoveryChannelId.SYSTEM),
                spinalBattery = augeSnapshot.ringScore(RecoveryChannelId.STRUCTURE),
                isVolumeCalibrated = p.volumeRecommendations.isNotEmpty() && p.athleteProfileScore != null,
                onBack = onBack,
                onStartPause = {
                    if (isActive) viewModel.pauseProgram()
                    else viewModel.startProgram()
                },
                onTitleDescriptionChange = { name, description ->
                    viewModel.updateProgram(p.copy(name = name, description = description))
                },
                onFocusChange = { mode ->
                    val programMode = try {
                        ProgramMode.valueOf(mode.uppercase())
                    } catch (_: Exception) {
                        ProgramMode.HYPERTROPHY
                    }
                    viewModel.updateProgram(p.copy(mode = programMode))
                },
                onCoverChange = { coverImage ->
                    viewModel.updateProgram(p.copy(coverImage = coverImage))
                },
                onApplyVolumeCalibration = { mode, athleteScore, recommendations ->
                    viewModel.updateProgram(
                        p.copy(
                            mode = mode,
                            volumeSystem = com.example.kpkn.data.models.VolumeSystem.KPNK,
                            athleteProfileScore = athleteScore,
                            volumeRecommendations = recommendations,
                        )
                    )
                },
                onIncreaseVolumeCurrentWeek = {
                    val result = viewModel.increaseCurrentWeekVolumeBy20Percent()
                    scope.launch {
                        when (result) {
                            VolumeAdjustmentResult.SUCCESS -> snackbarHostState.showKpknSnackbar(
                                "Aumentamos en 20% el volumen de la semana actual por músculo.",
                                SnackbarType.SUCCESS,
                            )
                            VolumeAdjustmentResult.REQUIRES_CALIBRATION -> snackbarHostState.showKpknSnackbar(
                                "Primero calibra el volumen del programa.",
                                SnackbarType.SUGGESTION,
                            )
                            VolumeAdjustmentResult.NO_WEEK_SELECTED -> snackbarHostState.showKpknSnackbar(
                                "No encontramos una semana activa para ajustar.",
                                SnackbarType.SUGGESTION,
                            )
                            VolumeAdjustmentResult.NO_ADJUSTABLE_VOLUME -> snackbarHostState.showKpknSnackbar(
                                "No encontramos volumen ajustable en la semana actual.",
                                SnackbarType.SUGGESTION,
                            )
                        }
                    }
                },
                onReduceVolumeCurrentWeek = {
                    val result = viewModel.reduceCurrentWeekVolumeBy20Percent()
                    scope.launch {
                        when (result) {
                            VolumeAdjustmentResult.SUCCESS -> snackbarHostState.showKpknSnackbar(
                                "Reducimos en 20% el volumen de la semana actual por músculo.",
                                SnackbarType.SUCCESS,
                            )
                            VolumeAdjustmentResult.REQUIRES_CALIBRATION -> snackbarHostState.showKpknSnackbar(
                                "Primero calibra el volumen del programa.",
                                SnackbarType.SUGGESTION,
                            )
                            VolumeAdjustmentResult.NO_WEEK_SELECTED -> snackbarHostState.showKpknSnackbar(
                                "No encontramos una semana activa para ajustar.",
                                SnackbarType.SUGGESTION,
                            )
                            VolumeAdjustmentResult.NO_ADJUSTABLE_VOLUME -> snackbarHostState.showKpknSnackbar(
                                "No encontramos volumen ajustable en la semana actual.",
                                SnackbarType.SUGGESTION,
                            )
                        }
                    }
                },
                openVolumeSheetToken = openVolumeSheetToken,
            )

            CompactSubTabs(
                activeMainTab = uiState.activeTab,
                structureSubTab = uiState.structureSubTab,
                analyticsSubTab = uiState.analyticsSubTab,
                onStructureSubTabChange = { viewModel.setStructureSubTab(it) },
                onAnalyticsSubTabChange = { viewModel.setAnalyticsSubTab(it) },
            )

            // Tab Content with animation
            AnimatedContent(
                targetState = uiState.activeTab,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.98f))
                },
                label = "tab-content",
            ) { tab ->
                when (tab) {
                    MainTab.TRAINING -> TrainingPanel(
                        viewModel = viewModel,
                        program = p,
                        roadmapBlocks = roadmapBlocks,
                        currentWeeks = currentWeeks,
                        displayedSessions = displayedSessions,
                        selectedWeekMeta = selectedWeekMeta,
                        selectedBlockId = uiState.selectedBlockId,
                        selectedWeekId = uiState.selectedWeekId,
                        simpleRoadmapLoopMarkers = simpleRoadmapLoopMarkers,
                        structureSubTab = uiState.structureSubTab,
                        onStartWorkout = onStartWorkout,
                        onEditSession = onEditSession,
                        onCreateSession = onCreateSession,
                        onOpenProgram = onOpenProgram,
                    )
                    MainTab.ANALYTICS -> AnalyticsPanel(
                        viewModel = viewModel,
                        program = p,
                        isProgramActive = isActive,
                        analyticsSubTab = uiState.analyticsSubTab,
                        programLogs = programLogs,
                        userBodyWeightKg = settings.userVitals.weight,
                    )
                }
            }
        }
    }

    LaunchedEffect(p.id, p.loops, p.macrocycles) {
        if (p.isSimpleTemporalProgram && p.loops.isNotEmpty()) {
            val materialized = LoopEngine.materializeLoopWeeks(p)
            if (materialized != p) viewModel.updateProgram(materialized)
        }
    }

    LaunchedEffect(p.id, activeProgramState?.currentWeekId, currentWeeks) {
        val activeWeekId = activeProgramState?.takeIf { it.programId == p.id }?.currentWeekId ?: return@LaunchedEffect
        val loopWeek = currentWeeks.firstOrNull { it.id == activeWeekId && it.isLoopWeek } ?: return@LaunchedEffect
        if (notifiedLoopWeekId == loopWeek.id) return@LaunchedEffect
        notifiedLoopWeekId = loopWeek.id
        snackbarHostState.showKpknSnackbar("Loop activo: ${loopWeek.name}. Ya puedes programar sus sesiones.", SnackbarType.SUGGESTION)
        LoopNotificationManager(context).notifyLoopActive(p.name, loopWeek.name)
    }

    if (showVolumeSetupNotice) {
        KpknAlertDialog(
            onDismissRequest = { showVolumeSetupNotice = false },
            title = { Text("Calibrar volumen del programa", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Para ajustar recomendaciones por músculo y activar automatizaciones de volumen, completa tu calibración inicial ahora."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showVolumeSetupNotice = false
                    openVolumeSheetToken++
                }) {
                    Text("Calibrar ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVolumeSetupNotice = false }) {
                    Text("Más tarde")
                }
            },
        )
    }
}

@Composable
private fun CompactSubTabs(
    activeMainTab: MainTab,
    structureSubTab: StructureSubTab,
    analyticsSubTab: AnalyticsSubTab,
    onStructureSubTabChange: (StructureSubTab) -> Unit,
    onAnalyticsSubTabChange: (AnalyticsSubTab) -> Unit,
) {
    val items = if (activeMainTab == MainTab.TRAINING) {
        listOf(
            "Semana" to StructureSubTab.SEMANA,
            "Estructura" to StructureSubTab.MACROCICLO,
            "Split" to StructureSubTab.SPLIT,
        )
    } else {
        listOf(
            "Volumen" to AnalyticsSubTab.VOLUMEN,
            "Progreso" to AnalyticsSubTab.PROGRESO,
            "Historiales" to AnalyticsSubTab.HISTORIALES,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.12f),
                    )
                ),
                shape = RoundedCornerShape(18.dp),
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { (label, value) ->
                val selected = when (value) {
                    is StructureSubTab -> structureSubTab == value || (value == StructureSubTab.MACROCICLO && structureSubTab == StructureSubTab.LOOPS)
                    is AnalyticsSubTab -> analyticsSubTab == value
                    else -> false
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            when (value) {
                                is StructureSubTab -> onStructureSubTabChange(value)
                                is AnalyticsSubTab -> onAnalyticsSubTabChange(value)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.05f),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 9.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
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
    simpleRoadmapLoopMarkers: List<com.example.kpkn.domain.training.RoadmapLoopMarker>,
    structureSubTab: StructureSubTab,
    onStartWorkout: (Session, Program) -> Unit,
    onEditSession: (String) -> Unit,
    onCreateSession: (String, String, Int, Int, Int, Boolean) -> Unit,
    onOpenProgram: (String) -> Unit = {},
) {
    val currentWeekId by viewModel.activeProgramState.collectAsState()
    val showSimpleCalendarizationSheet by viewModel.showSimpleCalendarizationSheet.collectAsState()
    val calendarizationStartDate by viewModel.calendarizationStartDate.collectAsState()
    val calendarizationEndDate by viewModel.calendarizationEndDate.collectAsState()
    val calendarizationStartDayOfWeek by viewModel.calendarizationStartDayOfWeek.collectAsState()
    val calendarizationTrainingDays by viewModel.calendarizationTrainingDays.collectAsState()
    var copiedRoadmapWeekId by remember(program.id) { mutableStateOf<String?>(null) }
    var pendingCompetitionCreation by remember { mutableStateOf<PendingCompetitionSessionCreation?>(null) }
    var pendingCompetitionModeSelection by remember { mutableStateOf<PendingCompetitionModeSelection?>(null) }
    var showCompetitionEligibilityNotice by remember { mutableStateOf(false) }
    var pendingDeleteSession by remember { mutableStateOf<Session?>(null) }
    var showCompetitionDeleteFollowup by remember { mutableStateOf(false) }

    fun focusWeek(blockId: String, weekId: String) {
        viewModel.selectBlock(blockId)
        viewModel.selectWeek(weekId)
        viewModel.setStructureSubTab(StructureSubTab.SEMANA)
    }

    fun addSessionForWeek(
        weekId: String,
        preferredDayOfWeek: Int,
        competitionKeyDate: ProgramKeyDate? = null,
        competitionRecordMode: CompetitionRecordMode = CompetitionRecordMode.HYBRID,
    ) {
        val located = locateWeekForSessionCreation(program, weekId) ?: return
        val suggestedDay = chooseSessionCreationDay(
            existingSessions = located.sessions,
            preferredDayOfWeek = preferredDayOfWeek,
            startDay = program.startDay ?: 1,
        )
        viewModel.selectBlock(located.blockId)
        viewModel.selectWeek(located.weekId)
        viewModel.setStructureSubTab(StructureSubTab.SEMANA)
        val sessionId = java.util.UUID.randomUUID().toString()
        val competitionKey = competitionKeyDate?.takeIf { it.type == KeyDateType.COMPETITION }
        val recordId = competitionKey?.let { java.util.UUID.randomUUID().toString() }
        val session = if (competitionKey != null && recordId != null) {
            createCompetitionRoadmapSession(
                sessionId = sessionId,
                dayOfWeek = suggestedDay,
                keyDate = competitionKey,
                competitionRecordId = recordId,
                program = program,
                competitionRecordMode = competitionRecordMode,
            )
        } else {
            createBlankRoadmapSession(sessionId, suggestedDay)
        }
        viewModel.addSession(
            macroIndex = located.macroIndex,
            mesoIndex = located.mesoIndex,
            weekId = located.weekId,
            session = session,
        )
        if (competitionKey != null && recordId != null) {
            CompetitionRepository.getInstance().upsert(
                createCompetitionRecordForSession(
                    recordId = recordId,
                    sessionId = sessionId,
                    weekId = located.weekId,
                    keyDate = competitionKey,
                    program = program,
                    competitionRecordMode = competitionRecordMode,
                )
            )
        }
        onCreateSession(
            sessionId,
            located.weekId,
            located.macroIndex,
            located.mesoIndex,
            suggestedDay,
            competitionKey != null,
        )
    }

    fun createSessionForWeek(weekId: String, preferredDayOfWeek: Int, keyDateId: String? = null) {
        val keyDate = keyDateId?.let { id -> program.keyDates.firstOrNull { it.id == id } }
        if (keyDate?.type == KeyDateType.COMPETITION) {
            if (!program.canCreateCompetitionSession()) {
                showCompetitionEligibilityNotice = true
                return
            }
            pendingCompetitionCreation = PendingCompetitionSessionCreation(weekId, preferredDayOfWeek, keyDate)
            return
        }
        addSessionForWeek(weekId, preferredDayOfWeek)
    }

    fun handleCompetitionKeyDateSaved(updatedProgram: Program, keyDate: ProgramKeyDate) {
        if (keyDate.type != KeyDateType.COMPETITION) return
        val alreadyLinked = updatedProgram.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
            .any { it.competitionKeyDateId == keyDate.id }
        if (alreadyLinked) return
        val target = locateCompetitionWeekDay(updatedProgram, keyDate) ?: return
        pendingCompetitionModeSelection = PendingCompetitionModeSelection(
            weekId = target.first,
            preferredDayOfWeek = target.second,
            keyDate = keyDate,
        )
    }

    // Edge case: empty program
    if (roadmapBlocks.isEmpty()) {
        EmptyProgramState(onAddStructure = {
            viewModel.setStructureSubTab(StructureSubTab.MACROCICLO)
        })
        return
    }

    Column {
        Spacer(Modifier.height(4.dp))

        if (structureSubTab != StructureSubTab.SPLIT) {
            BlockRoadmap(
                roadmapBlocks = roadmapBlocks,
                currentWeeks = currentWeeks,
                selectedBlockId = selectedBlockId,
                selectedWeekId = selectedWeekId,
                currentWeekId = currentWeekId?.currentWeekId,
                isSimpleProgram = program.isSimpleTemporalProgram,
                isSimpleCalendarized = program.simpleProgramKind == SimpleProgramKind.CALENDARIZED,
                simpleLoopMarkers = simpleRoadmapLoopMarkers,
                currentCycle = program.loopState?.currentCycle ?: 0,
                onSelectBlock = { viewModel.selectBlock(it) },
                onSelectWeek = { viewModel.selectWeek(it) },
                onAddSimpleWeek = { viewModel.addWeekToSimpleProgram() },
                onAddAdvancedWeek = { name, description -> viewModel.addWeekToSelectedAdvancedBlock(name, description) },
                onAddAdvancedBlock = { name, description -> viewModel.addAdvancedBlockFromRoadmap(name, description) },
                onUpdateWeek = { weekId, name, description -> viewModel.updateWeekMetadata(weekId, name, description) },
                onDeleteWeek = { weekId -> viewModel.deleteWeekFromRoadmap(weekId) },
                onUpdateBlock = { blockId, name, description -> viewModel.updateBlockMetadata(blockId, name, description) },
                onDeleteBlock = { blockId -> viewModel.deleteBlockFromRoadmap(blockId) },
                copiedWeekId = copiedRoadmapWeekId,
                onCopyWeek = { copiedRoadmapWeekId = it },
                onPasteWeek = { targetWeekId ->
                    copiedRoadmapWeekId?.let { sourceWeekId ->
                        viewModel.copyWeekSessions(
                            sourceWeekId = sourceWeekId,
                            targetWeekIds = setOf(targetWeekId),
                            replaceWeekIds = setOf(targetWeekId),
                        )
                    }
                },
            )

            Spacer(Modifier.height(8.dp))
        }

        when (structureSubTab) {
            StructureSubTab.SEMANA -> {
                DayView(
                    program = program,
                    isSimpleProgram = program.isSimpleTemporalProgram,
                    isCalendarized = program.simpleProgramKind == SimpleProgramKind.CALENDARIZED,
                    selectedWeek = selectedWeekMeta,
                    sessions = displayedSessions,
                    onEditSession = onEditSession,
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
                                false,
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
                )
            }
            StructureSubTab.SPLIT -> SplitView(
                program = program,
                selectedBlockId = selectedBlockId,
                selectedWeekId = selectedWeekId,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
            StructureSubTab.MACROCICLO -> MacrocycleEditor(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
                onCompetitionKeyDateSaved = ::handleCompetitionKeyDateSaved,
                onFocusWeek = ::focusWeek,
                onCreateSessionForWeek = ::createSessionForWeek,
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
                onAddProgramCopy = { copy ->
                    viewModel.addProgramCopy(copy)
                    onOpenProgram(copy.id)
                },
            )
            StructureSubTab.LOOPS -> MacrocycleEditor(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
                onCompetitionKeyDateSaved = ::handleCompetitionKeyDateSaved,
                onFocusWeek = ::focusWeek,
                onCreateSessionForWeek = ::createSessionForWeek,
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
                onAddProgramCopy = { copy ->
                    viewModel.addProgramCopy(copy)
                    onOpenProgram(copy.id)
                },
            )
            StructureSubTab.PROTOCOLOS -> ProtocolsView(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
        }

        Spacer(Modifier.height(120.dp))
    }

    pendingCompetitionCreation?.let { pending ->
        KpknAlertDialog(
            onDismissRequest = { pendingCompetitionCreation = null },
            title = { Text("Configurar competición", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "KPKN creará una sesión especial para la fecha clave y te llevará al editor para configurar federación, ubicación, hora y movimientos de competición."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        addSessionForWeek(pending.weekId, pending.preferredDayOfWeek, pending.keyDate)
                        pendingCompetitionCreation = null
                    },
                ) { Text("Crear y configurar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCompetitionCreation = null }) { Text("Cancelar") }
            },
        )
    }

    pendingDeleteSession?.let { sessionToDelete ->
        val isCompetition = sessionToDelete.isMeetDay || sessionToDelete.isCompetitionSession
        KpknAlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = { Text("Confirmar eliminación", fontWeight = FontWeight.Black) },
            text = {
                Text("¿Confirmas que eliminas esta sesión?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val block = roadmapBlocks.find { it.id == selectedBlockId }
                        val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                        if (block != null && weekMeta != null) {
                            viewModel.deleteSession(sessionToDelete.id, block.macroIndex, weekMeta.mesoIndex, selectedWeekId ?: "")
                        }
                        pendingDeleteSession = null
                        if (isCompetition) showCompetitionDeleteFollowup = true
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) { Text("Cancelar") }
            },
        )
    }

    if (showCompetitionDeleteFollowup) {
        KpknAlertDialog(
            onDismissRequest = { showCompetitionDeleteFollowup = false },
            title = { Text("Fecha de competición", fontWeight = FontWeight.Black) },
            text = {
                Text("La sesión de competición fue eliminada. ¿Vas a cambiar la fecha clave de competición o dejar el programa sin fecha de competición?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setStructureSubTab(StructureSubTab.MACROCICLO)
                        showCompetitionDeleteFollowup = false
                    },
                ) { Text("Cambiar fecha") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCompetitionKeyDate()
                        showCompetitionDeleteFollowup = false
                    },
                ) { Text("Dejar sin fecha") }
            },
        )
    }

    pendingCompetitionModeSelection?.let { pending ->
        KpknAlertDialog(
            onDismissRequest = { pendingCompetitionModeSelection = null },
            title = { Text("Tipo de sesión de competición", fontWeight = FontWeight.Black) },
            text = {
                Text("Elige si esta sesión será técnica (movimientos de competición) o simple (registro de resultados/fotos estilo bitácora).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        addSessionForWeek(
                            weekId = pending.weekId,
                            preferredDayOfWeek = pending.preferredDayOfWeek,
                            competitionKeyDate = pending.keyDate,
                            competitionRecordMode = CompetitionRecordMode.TECHNICAL,
                        )
                        pendingCompetitionModeSelection = null
                    },
                ) { Text("Técnica") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        addSessionForWeek(
                            weekId = pending.weekId,
                            preferredDayOfWeek = pending.preferredDayOfWeek,
                            competitionKeyDate = pending.keyDate,
                            competitionRecordMode = CompetitionRecordMode.JOURNAL,
                        )
                        pendingCompetitionModeSelection = null
                    },
                ) { Text("Simple") }
            },
        )
    }

    if (showCompetitionEligibilityNotice) {
        KpknAlertDialog(
            onDismissRequest = { showCompetitionEligibilityNotice = false },
            title = { Text("Sesión de competición no disponible", fontWeight = FontWeight.Black) },
            text = {
                Text("Solo se crean sesiones de competición desde programas avanzados con calendarización de competición y fecha clave configurada.")
            },
            confirmButton = {
                TextButton(onClick = { showCompetitionEligibilityNotice = false }) { Text("Entendido") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyWeekDialog(
    weeks: List<com.example.kpkn.domain.training.WeekWithMeta>,
    selectedWeekId: String?,
    selectedBlockId: String?,
    viewModel: ProgramDetailViewModel,
    onDismiss: () -> Unit,
) {
    var sourceWeekId by remember(weeks, selectedWeekId) { mutableStateOf(selectedWeekId ?: weeks.firstOrNull()?.id.orEmpty()) }
    var targetWeekIds by remember(weeks, sourceWeekId) {
        mutableStateOf(weeks.filter { it.id != sourceWeekId }.take(1).map { it.id }.toSet())
    }
    var replaceWeekIds by remember { mutableStateOf(emptySet<String>()) }
    val conflicts = remember(sourceWeekId, targetWeekIds, weeks) {
        viewModel.previewWeekCopyConflicts(sourceWeekId, targetWeekIds)
    }
    val sourceHasSessions = weeks.firstOrNull { it.id == sourceWeekId }?.sessions?.isNotEmpty() == true

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copiar sesiones de semana", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Semana origen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                sourceWeekId = week.id
                                targetWeekIds = targetWeekIds - week.id
                                replaceWeekIds = emptySet()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sourceWeekId == week.id, onClick = { sourceWeekId = week.id })
                        Column {
                            Text(week.name, fontWeight = FontWeight.SemiBold)
                            Text("${week.sessions.size} sesiones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Text("Destino", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            targetWeekIds = weeks.filter { it.id != sourceWeekId }.map { it.id }.toSet()
                            replaceWeekIds = emptySet()
                        },
                        label = { Text("Bloque actual") },
                    )
                    AssistChip(
                        onClick = {
                            targetWeekIds = weeks.filter { it.id != sourceWeekId }.take(1).map { it.id }.toSet()
                            replaceWeekIds = emptySet()
                        },
                        label = { Text("Una semana") },
                    )
                }
                weeks.filter { it.id != sourceWeekId }.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                targetWeekIds = if (week.id in targetWeekIds) targetWeekIds - week.id else targetWeekIds + week.id
                                replaceWeekIds = replaceWeekIds - week.id
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = week.id in targetWeekIds,
                            onCheckedChange = { checked ->
                                targetWeekIds = if (checked) targetWeekIds + week.id else targetWeekIds - week.id
                                replaceWeekIds = replaceWeekIds - week.id
                            },
                        )
                        Column {
                            Text(week.name, fontWeight = FontWeight.SemiBold)
                            Text(week.dateRangeLabel ?: "${week.sessions.size} sesiones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (conflicts.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Conflictos por semana", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                    conflicts.forEach { conflict ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(conflict.weekName, fontWeight = FontWeight.Bold)
                                Text("Ya tiene sesiones en: ${conflict.dayLabels.joinToString(", ").ifBlank { "días asignados" }}")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = conflict.weekId in replaceWeekIds,
                                        onCheckedChange = { checked ->
                                            replaceWeekIds = if (checked) replaceWeekIds + conflict.weekId else replaceWeekIds - conflict.weekId
                                        },
                                    )
                                    Text("Reemplazar en esta semana")
                                }
                            }
                        }
                    }
                }

                if (!sourceHasSessions) {
                    Text("La semana origen no tiene sesiones para copiar.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = sourceHasSessions && targetWeekIds.isNotEmpty(),
                onClick = {
                    viewModel.copyWeekSessions(sourceWeekId, targetWeekIds, replaceWeekIds)
                    onDismiss()
                },
            ) { Text("Copiar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarWeeksDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, Set<Int>) -> Unit,
) {
    var startDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weekCountText by rememberSaveable { mutableStateOf("8") }
    var selectedDays by rememberSaveable { mutableStateOf(setOf(1, 3, 5)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val count = weekCountText.toIntOrNull()?.coerceIn(1, 52) ?: 0

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear semanas desde calendario", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Inicio: $startDate")
                }
                OutlinedTextField(
                    value = weekCountText,
                    onValueChange = { input -> weekCountText = input.filter(Char::isDigit).take(2) },
                    label = { Text("Cantidad de semanas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Días de entrenamiento", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            },
                            label = { Text(dayLabelShort(day)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = count > 0, onClick = { onCreate(startDate, count, selectedDays) }) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )

    if (showDatePicker) {
        val initialMillis = remember(startDate) {
            runCatching {
                LocalDate.parse(startDate)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        KpknGlassDialog(
            onDismissRequest = { showDatePicker = false },
            shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                startDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString()
                            }
                            showDatePicker = false
                        },
                    ) { Text("Usar fecha") }
                }
            }
        }
    }
}

private data class WeekSessionLocation(
    val blockId: String,
    val weekId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val sessions: List<Session>,
)

private data class PendingCompetitionSessionCreation(
    val weekId: String,
    val preferredDayOfWeek: Int,
    val keyDate: ProgramKeyDate,
)

private data class PendingCompetitionModeSelection(
    val weekId: String,
    val preferredDayOfWeek: Int,
    val keyDate: ProgramKeyDate,
)

private fun createBlankRoadmapSession(sessionId: String, dayOfWeek: Int): Session =
    Session(
        id = sessionId,
        name = "Sesión ${dayLabelFull(dayOfWeek)}",
        lastModifiedAtMs = System.currentTimeMillis(),
        dayOfWeek = dayOfWeek,
        isMainSession = true,
    )

private fun createCompetitionRoadmapSession(
    sessionId: String,
    dayOfWeek: Int,
    keyDate: ProgramKeyDate,
    competitionRecordId: String,
    program: Program,
    competitionRecordMode: CompetitionRecordMode = CompetitionRecordMode.HYBRID,
): Session {
    val eventDate = keyDate.eventDate ?: keyDate.startDate
    val sportType = defaultCompetitionSportType(program)
    return Session(
        id = sessionId,
        name = keyDate.title.ifBlank { "Competición" },
        description = keyDate.notes,
        lastModifiedAtMs = System.currentTimeMillis(),
        dayOfWeek = dayOfWeek,
        isMainSession = true,
        isMeetDay = true,
        isCompetitionSession = true,
        focus = "Competición",
        competitionDetails = CompetitionDetails(
            competitionDate = eventDate,
        ),
        competitionRecordId = competitionRecordId,
        competitionKeyDateId = keyDate.id,
        competitionSportType = sportType,
        competitionRecordMode = competitionRecordMode,
    )
}

private fun createCompetitionRecordForSession(
    recordId: String,
    sessionId: String,
    weekId: String,
    keyDate: ProgramKeyDate,
    program: Program,
    competitionRecordMode: CompetitionRecordMode = CompetitionRecordMode.HYBRID,
): CompetitionRecord {
    val eventDate = keyDate.eventDate ?: keyDate.startDate
    val sportType = defaultCompetitionSportType(program)
    return CompetitionRecord(
        id = recordId,
        title = keyDate.title.ifBlank { "Competición" },
        eventDate = eventDate,
        sportType = sportType,
        recordMode = competitionRecordMode,
        status = CompetitionRecordStatus.PLANNED,
        notes = keyDate.notes,
        plannedProgramId = program.id,
        plannedSessionId = sessionId,
        plannedWeekId = weekId,
        keyDateId = keyDate.id,
    )
}

private fun Program.canCreateCompetitionSession(): Boolean =
    structure == ProgramStructure.COMPLEX &&
        calendarization?.mode == ProgramCalendarizationMode.ADVANCED_COMPETITION &&
        keyDates.any { it.type == KeyDateType.COMPETITION }

private fun defaultCompetitionSportType(program: Program): CompetitionTemplateType =
    when (program.mode) {
        ProgramMode.POWERLIFTING -> CompetitionTemplateType.POWERLIFTING
        ProgramMode.POWERBUILDING -> CompetitionTemplateType.POWERLIFTING
        ProgramMode.HYPERTROPHY -> CompetitionTemplateType.CUSTOM
    }

private fun dayLabelShort(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}

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

private fun locateWeekForSessionCreation(program: Program, weekId: String): WeekSessionLocation? {
    var globalMesoIndex = 0
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                val mesoIndex = globalMesoIndex++
                meso.weeks.forEach { week ->
                    if (week.id == weekId) {
                        return WeekSessionLocation(
                            blockId = block.id,
                            weekId = week.id,
                            macroIndex = macroIndex,
                            mesoIndex = mesoIndex,
                            sessions = week.sessions,
                        )
                    }
                }
            }
        }
    }
    return null
}

private fun locateCompetitionWeekDay(program: Program, keyDate: ProgramKeyDate): Pair<String, Int>? {
    val eventDate = parseIsoDate(keyDate.eventDate ?: keyDate.startDate) ?: return null
    program.macrocycles.forEach { macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    val dayByTrainingDate = week.trainingDayDates.entries.firstOrNull { (_, date) ->
                        parseIsoDate(date) == eventDate
                    }?.key
                    if (dayByTrainingDate != null) return week.id to dayByTrainingDate
                    val start = parseIsoDate(week.startDate)
                    val end = parseIsoDate(week.endDate)
                    if (start != null && end != null && !eventDate.isBefore(start) && !eventDate.isAfter(end)) {
                        return week.id to eventDate.dayOfWeek.value
                    }
                }
            }
        }
    }
    return null
}

private fun parseIsoDate(raw: String?): LocalDate? =
    raw?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun chooseSessionCreationDay(
    existingSessions: List<Session>,
    preferredDayOfWeek: Int,
    startDay: Int,
): Int {
    val normalizedPreferred = preferredDayOfWeek.coerceIn(1, 7)
    val occupiedDays = existingSessions.mapNotNull { it.dayOfWeek?.takeIf { day -> day in 1..7 } }.toSet()
    if (normalizedPreferred !in occupiedDays) return normalizedPreferred

    val normalizedStart = startDay.coerceIn(1, 7)
    val orderedDays = (normalizedStart..7).toList() + (1 until normalizedStart).toList()
    return orderedDays.firstOrNull { it !in occupiedDays } ?: normalizedPreferred
}

// ─── Analytics Panel ────────────────────────────────────────────────────────

@Composable
private fun AnalyticsPanel(
    viewModel: ProgramDetailViewModel,
    program: Program,
    isProgramActive: Boolean,
    analyticsSubTab: AnalyticsSubTab,
    programLogs: List<com.example.kpkn.data.models.WorkoutLog>,
    userBodyWeightKg: Double?,
) {
    val analyticsReport = remember(program, programLogs) {
        ProgramAnalyticsEngine.analyze(
            program = program,
            logs = programLogs,
            exerciseCatalog = EXERCISE_DATABASE,
        )
    }
    val muscleCdbsStatus by viewModel.muscleCdbsStatus.collectAsState()

    Column {
        Spacer(Modifier.height(8.dp))

        when (analyticsSubTab) {
            AnalyticsSubTab.VOLUMEN -> {
                val programDiscomforts by viewModel.programDiscomforts.collectAsState()
                val exerciseDiscomfortAssociations by viewModel.exerciseDiscomfortAssociations.collectAsState()
                val hasCreatedSessions = remember(program) {
                    program.macrocycles
                        .flatMap { it.blocks }
                        .flatMap { it.mesocycles }
                        .flatMap { it.weeks }
                        .any { it.sessions.isNotEmpty() }
                }
                VolumeView(
                    program = program,
                    isProgramActive = isProgramActive,
                    hasCreatedSessions = hasCreatedSessions,
                    onActivateProgram = { viewModel.startProgram() },
                    onGoCreateSession = {
                        val firstBlock = program.macrocycles.firstOrNull()?.blocks?.firstOrNull()
                        val firstWeek = firstBlock?.mesocycles?.firstOrNull()?.weeks?.firstOrNull()
                        viewModel.setActiveTab(MainTab.TRAINING)
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
                    programDiscomforts = programDiscomforts,
                    exerciseDiscomfortAssociations = exerciseDiscomfortAssociations,
                    analyticsReport = analyticsReport,
                    muscleCdbsStatus = muscleCdbsStatus,
                    programLogs = programLogs,
                )
            }
            AnalyticsSubTab.PROGRESO -> {
                if (programLogs.isEmpty()) {
                    EmptyHistoryState()
                } else {
                    ProgressView(
                        program = program,
                        programLogs = programLogs,
                        userBodyWeightKg = userBodyWeightKg,
                        onUpdateProgram = { viewModel.updateProgram(it) },
                        analyticsReport = analyticsReport,
                    )
                }
            }
            AnalyticsSubTab.HISTORIALES -> {
                if (programLogs.isEmpty()) {
                    EmptyHistoryState()
                } else {
                    HistoryView(
                        program = program,
                        programLogs = programLogs,
                        analyticsReport = analyticsReport,
                    )
                }
            }
        }

        Spacer(Modifier.height(120.dp))
    }
}

// ─── Empty States ───────────────────────────────────────────────────────────

@Composable
private fun EmptyProgramState(onAddStructure: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83D\uDCCB", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("Programa vacío", fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                "Agrega estructura para comenzar",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddStructure) {
                Text("Agregar estructura", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83D\uDCCA", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("Sin datos aún", fontSize = 16.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                "Completa sesiones de entrenamiento para ver estadísticas",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
