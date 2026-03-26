package com.example.kpkn.screens.programdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.programdetail.components.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onEditProgram: (String) -> Unit,
    viewModel: ProgramDetailViewModel = viewModel(factory = ProgramDetailViewModel.factory(programId)),
) {
    val program by viewModel.program.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isActive by viewModel.isActiveProgram.collectAsState()
    val isPaused by viewModel.isPausedProgram.collectAsState()
    val currentWeeks by viewModel.currentWeeks.collectAsState()
    val displayedSessions by viewModel.displayedSessions.collectAsState()
    val totalWeeks by viewModel.totalWeeks.collectAsState()
    val currentWeekIndex by viewModel.currentWeekIndex.collectAsState()
    val totalAdherence by viewModel.totalAdherence.collectAsState()
    val roadmapBlocks by viewModel.roadmapBlocks.collectAsState()
    val programLogs by viewModel.programLogs.collectAsState()
    val isSimpleProgram by viewModel.isSimpleProgram.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    // Welcome Tour (uses SharedPreferences — single source of truth)
    val tourState = rememberTourState(programId)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            // Hero Banner
            CompactHeroBanner(
                programName = p.name,
                programDescription = p.description,
                isActive = isActive,
                isPaused = isPaused,
                currentWeekIndex = currentWeekIndex,
                totalWeeks = totalWeeks,
                totalAdherence = totalAdherence,
                focusMode = p.mode.name.lowercase(),
                onBack = onBack,
                onEdit = { onEditProgram(programId) },
                onStartPause = {
                    if (isActive) viewModel.pauseProgram()
                    else viewModel.startProgram()
                },
                onFocusChange = { mode ->
                    val programMode = try {
                        com.example.kpkn.data.models.ProgramMode.valueOf(mode.uppercase())
                    } catch (_: Exception) {
                        com.example.kpkn.data.models.ProgramMode.HYPERTROPHY
                    }
                    viewModel.updateProgram(p.copy(mode = programMode))
                },
            )

            // Integrated Tabs
            val activeSubTabName = when (uiState.activeTab) {
                MainTab.TRAINING -> uiState.structureSubTab.name
                MainTab.ANALYTICS -> uiState.analyticsSubTab.name
            }

            IntegratedTabs(
                activeMainTab = uiState.activeTab,
                onMainTabChange = { viewModel.setActiveTab(it) },
                activeSubTab = activeSubTabName,
                onSubTabChange = { tabName ->
                    if (uiState.activeTab == MainTab.TRAINING) {
                        try { viewModel.setStructureSubTab(StructureSubTab.valueOf(tabName)) } catch (_: Exception) {}
                    } else {
                        try { viewModel.setAnalyticsSubTab(AnalyticsSubTab.valueOf(tabName)) } catch (_: Exception) {}
                    }
                },
                isSimpleProgram = isSimpleProgram,
            )

            Spacer(Modifier.height(8.dp))

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
                        selectedBlockId = uiState.selectedBlockId,
                        selectedWeekId = uiState.selectedWeekId,
                        structureSubTab = uiState.structureSubTab,
                        onStartWorkout = onStartWorkout,
                        onEditSession = onEditSession,
                    )
                    MainTab.ANALYTICS -> AnalyticsPanel(
                        viewModel = viewModel,
                        program = p,
                        analyticsSubTab = uiState.analyticsSubTab,
                        programLogs = programLogs,
                        totalAdherence = totalAdherence,
                    )
                }
            }
        }
    }

    WelcomeTourDialog(tourState = tourState)
}

// ─── Training Panel ─────────────────────────────────────────────────────────

@Composable
private fun TrainingPanel(
    viewModel: ProgramDetailViewModel,
    program: Program,
    roadmapBlocks: List<com.example.kpkn.domain.training.RoadmapBlock>,
    currentWeeks: List<com.example.kpkn.domain.training.WeekWithMeta>,
    displayedSessions: List<Session>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    structureSubTab: StructureSubTab,
    onStartWorkout: (Session, Program) -> Unit,
    onEditSession: (String) -> Unit,
) {
    val currentWeekId by viewModel.activeProgramState.collectAsState()
    var addSessionForDay by remember { mutableStateOf<Int?>(null) }

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
            onSelectBlock = { viewModel.selectBlock(it) },
            onSelectWeek = { viewModel.selectWeek(it) },
        )

        Spacer(Modifier.height(8.dp))

        when (structureSubTab) {
            StructureSubTab.SEMANA -> {
                if (displayedSessions.isEmpty()) {
                    EmptyWeekState(onAddSession = { addSessionForDay = program.startDay ?: 1 })
                } else {
                    DayView(
                        program = program,
                        sessions = displayedSessions,
                        onEditSession = onEditSession,
                        onAddSession = { dayId -> addSessionForDay = dayId },
                        onDeleteSession = { sessionId ->
                            val block = roadmapBlocks.find { it.id == selectedBlockId }
                            val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                            if (block != null && weekMeta != null) {
                                viewModel.deleteSession(sessionId, block.macroIndex, weekMeta.mesoIndex, selectedWeekId ?: "")
                            }
                        },
                        onStartWorkout = { onStartWorkout(it, program) },
                        onReorderSessions = { fromIdx, toIdx ->
                            val weekId = selectedWeekId ?: ""
                            if (weekId.isNotEmpty()) {
                                viewModel.reorderSessions(weekId, fromIdx, toIdx)
                            }
                        },
                    )
                }
            }
            StructureSubTab.SPLIT -> SplitView(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
            StructureSubTab.MACROCICLO -> MacrocycleEditor(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
            StructureSubTab.LOOPS -> LoopsView(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
            StructureSubTab.PROTOCOLOS -> ProtocolsView(
                program = program,
                onUpdateProgram = { viewModel.updateProgram(it) },
            )
        }

        Spacer(Modifier.height(120.dp))
    }

    if (addSessionForDay != null) {
        AddSessionDialog(
            dayOfWeek = addSessionForDay!!,
            onConfirm = { name ->
                val block = roadmapBlocks.find { it.id == selectedBlockId }
                val weekMeta = currentWeeks.find { it.id == selectedWeekId }
                if (block != null && weekMeta != null) {
                    val session = Session(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        dayOfWeek = addSessionForDay,
                    )
                    viewModel.addSession(block.macroIndex, weekMeta.mesoIndex, selectedWeekId ?: "", session)
                }
                addSessionForDay = null
            },
            onDismiss = { addSessionForDay = null },
        )
    }
}

// ─── Analytics Panel ────────────────────────────────────────────────────────

@Composable
private fun AnalyticsPanel(
    viewModel: ProgramDetailViewModel,
    program: Program,
    analyticsSubTab: AnalyticsSubTab,
    programLogs: List<com.example.kpkn.data.models.WorkoutLog>,
    totalAdherence: Int,
) {
    // Edge case: no history
    if (programLogs.isEmpty()) {
        EmptyHistoryState()
        return
    }

    Column {
        Spacer(Modifier.height(8.dp))

        when (analyticsSubTab) {
            AnalyticsSubTab.VOLUMEN -> {
                val weeklyAdherence by viewModel.weeklyAdherence.collectAsState()
                val programDiscomforts by viewModel.programDiscomforts.collectAsState()
                VolumeView(
                    program = program,
                    programLogs = programLogs,
                    totalAdherence = totalAdherence,
                    weeklyAdherence = weeklyAdherence,
                    programDiscomforts = programDiscomforts,
                )
            }
            AnalyticsSubTab.PROGRESO -> ProgressView(
                program = program,
                programLogs = programLogs,
            )
            AnalyticsSubTab.HISTORIALES -> HistoryView(
                program = program,
                programLogs = programLogs,
            )
        }

        Spacer(Modifier.height(120.dp))
    }
}

// ─── Add Session Dialog ──────────────────────────────────────────────────────

@Composable
private fun AddSessionDialog(dayOfWeek: Int, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Nueva sesión", fontWeight = FontWeight.Black) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre de la sesión") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
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
private fun EmptyWeekState(onAddSession: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("\uD83C\uDFCB\uFE0F", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Semana vacía", fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                "No hay sesiones programadas",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAddSession) {
                Text("Agregar sesión", fontWeight = FontWeight.Bold)
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
