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
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.screens.auge.AugeViewModel
import com.example.kpkn.screens.programdetail.components.*
import com.example.kpkn.services.workout.LoopNotificationManager
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onStartWorkout: (Session, Program) -> Unit,
    onEditSession: (String) -> Unit,
    onCreateSession: (String, String, Int, Int, Int) -> Unit,
    onContextTabStateChange: (MainTab, (MainTab) -> Unit) -> Unit = { _, _ -> },
    viewModel: ProgramDetailViewModel = viewModel(factory = ProgramDetailViewModel.factory(programId)),
) {
    val augeViewModel: AugeViewModel = viewModel()
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
    val batteries by augeViewModel.batteries.collectAsState()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val context = LocalContext.current

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
                muscularBattery = batteries.muscular,
                sncBattery = batteries.cnc,
                spinalBattery = batteries.spinal,
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
        AlertDialog(
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
            "Split" to StructureSubTab.SPLIT,
            "Macrociclo" to StructureSubTab.MACROCICLO,
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
    onCreateSession: (String, String, Int, Int, Int) -> Unit,
) {
    val currentWeekId by viewModel.activeProgramState.collectAsState()

    fun focusWeek(blockId: String, weekId: String) {
        viewModel.selectBlock(blockId)
        viewModel.selectWeek(weekId)
        viewModel.setStructureSubTab(StructureSubTab.SEMANA)
    }

    fun createSessionForWeek(weekId: String, preferredDayOfWeek: Int) {
        val located = locateWeekForSessionCreation(program, weekId) ?: return
        val suggestedDay = chooseSessionCreationDay(
            existingSessions = located.sessions,
            preferredDayOfWeek = preferredDayOfWeek,
            startDay = program.startDay ?: 1,
        )
        viewModel.selectBlock(located.blockId)
        viewModel.selectWeek(located.weekId)
        viewModel.setStructureSubTab(StructureSubTab.SEMANA)
        onCreateSession(
            java.util.UUID.randomUUID().toString(),
            located.weekId,
            located.macroIndex,
            located.mesoIndex,
            suggestedDay,
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

        BlockRoadmap(
            roadmapBlocks = roadmapBlocks,
            currentWeeks = currentWeeks,
            selectedBlockId = selectedBlockId,
            selectedWeekId = selectedWeekId,
            currentWeekId = currentWeekId?.currentWeekId,
            isSimpleProgram = program.isSimpleTemporalProgram,
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
        )

        Spacer(Modifier.height(8.dp))

        when (structureSubTab) {
            StructureSubTab.SEMANA -> {
                DayView(
                    program = program,
                    isSimpleProgram = program.isSimpleTemporalProgram,
                    selectedWeek = selectedWeekMeta,
                    sessions = displayedSessions,
                    onEditSession = onEditSession,
                    onAddSession = { dayId ->
                        val block = roadmapBlocks.find { it.id == selectedBlockId }
                        val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                        val weekId = selectedWeekId
                        if (block != null && weekMeta != null && weekId != null) {
                            onCreateSession(
                                java.util.UUID.randomUUID().toString(),
                                weekId,
                                block.macroIndex,
                                weekMeta.mesoIndex,
                                dayId,
                            )
                        }
                    },
                    onDeleteSession = { sessionId ->
                        val block = roadmapBlocks.find { it.id == selectedBlockId }
                        val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                        if (block != null && weekMeta != null) {
                            viewModel.deleteSession(sessionId, block.macroIndex, weekMeta.mesoIndex, selectedWeekId ?: "")
                        }
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
                onFocusWeek = ::focusWeek,
                onCreateSessionForWeek = ::createSessionForWeek,
            )
            StructureSubTab.LOOPS -> MacrocycleEditor(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
                onFocusWeek = ::focusWeek,
                onCreateSessionForWeek = ::createSessionForWeek,
            )
            StructureSubTab.PROTOCOLOS -> ProtocolsView(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
        }

        Spacer(Modifier.height(120.dp))
    }
}

private data class WeekSessionLocation(
    val blockId: String,
    val weekId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val sessions: List<Session>,
)

private fun locateWeekForSessionCreation(program: Program, weekId: String): WeekSessionLocation? {
    program.macrocycles.forEachIndexed { macroIndex, macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEachIndexed { mesoIndex, meso ->
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
