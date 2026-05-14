package com.example.kpkn.screens.sessioneditor

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.example.kpkn.screens.home.SingleRingCanvas
import com.example.kpkn.data.models.Session
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateTag
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private fun String.safeIntOrNull(): Int? = toIntOrNull()
private fun String.safeDoubleOrNull(): Double? = replace(",", ".").toDoubleOrNull()

private fun formatEditableNumber(value: Double?): String {
    if (value == null) return ""
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}


private fun formatOneDecimal(value: Double): String = "%.1f".format(value)

private fun dayInitial(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "L"
    2 -> "M"
    3 -> "X"
    4 -> "J"
    5 -> "V"
    6 -> "S"
    7 -> "D"
    else -> "?"
}

private fun formatRestSummary(restTime: Int?): String {
    val total = restTime ?: 90
    val minutes = total / 60
    val seconds = total % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

private fun trainingModeLabel(mode: TrainingMode): String = when (mode) {
    TrainingMode.REPS -> "Reps"
    TrainingMode.TIME -> "Tiempo"
    TrainingMode.RM -> "RM"
    TrainingMode.CUSTOM -> "Personalizado"
    TrainingMode.DISTANCE -> "Distancia"
    TrainingMode.SOLO_RPE -> "Solo RPE"
    TrainingMode.AMRAP -> "AMRAP"
}

private data class SessionCoverGradient(
    val id: String,
    val name: String,
    val colors: List<Color>,
)

private val sessionGradients = listOf(
    SessionCoverGradient("gradient://ember", "Ember", listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))),
    SessionCoverGradient("gradient://lagoon", "Lagoon", listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))),
    SessionCoverGradient("gradient://velvet", "Velvet", listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))),
    SessionCoverGradient("gradient://forest", "Forest", listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))),
)

private val sessionSolidPresets = listOf(
    SessionCoverGradient("solid://obsidian", "Obsidian", listOf(Color(0xFF111318), Color(0xFF111318), Color(0xFF111318))),
    SessionCoverGradient("solid://steel", "Steel", listOf(Color(0xFF334155), Color(0xFF334155), Color(0xFF334155))),
    SessionCoverGradient("solid://ember-red", "Ember Red", listOf(Color(0xFF7F1D1D), Color(0xFF7F1D1D), Color(0xFF7F1D1D))),
    SessionCoverGradient("solid://ocean", "Ocean", listOf(Color(0xFF0F3D5E), Color(0xFF0F3D5E), Color(0xFF0F3D5E))),
    SessionCoverGradient("solid://moss", "Moss", listOf(Color(0xFF244B3C), Color(0xFF244B3C), Color(0xFF244B3C))),
)

private val sessionBackgroundPresets = sessionGradients + sessionSolidPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditorScreen(
    programId: String,
    sessionId: String,
    onBack: () -> Unit,
    onOpenExerciseCreator: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit = {},
    onSavedAndExit: () -> Unit = onBack,
    draftWeekId: String? = null,
    draftMacroIndex: Int? = null,
    draftMesoIndex: Int? = null,
    draftDayOfWeek: Int? = null,
    viewModel: SessionEditorViewModel = viewModel(
        factory = SessionEditorViewModel.factory(
            programId = programId,
            sessionId = sessionId,
            draftWeekId = draftWeekId,
            draftMacroIndex = draftMacroIndex,
            draftMesoIndex = draftMesoIndex,
            draftDayOfWeek = draftDayOfWeek,
        )
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val allTemplates by viewModel.allTemplates.collectAsState()
    val session = uiState.session
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoExpandExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCompetitionModeConfirm by rememberSaveable { mutableStateOf(false) }
    var competitionToggleTarget by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> viewModel.saveDraftForExit()
                Lifecycle.Event.ON_RESUME -> viewModel.retryLoadSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.saveDraftForExit()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-scroll al ejercicio recién añadido para que el usuario vea la tarjeta expandida
    LaunchedEffect(pendingAutoExpandExerciseId) {
        val expandId = pendingAutoExpandExerciseId ?: return@LaunchedEffect
        val currentSession = session ?: return@LaunchedEffect
        if (currentSession.exercises.any { it.id == expandId }) {
            listState.animateScrollToItem(1)
            return@LaunchedEffect
        }
        val groupedParts = currentSession.parts.filterNot { it.isUncategorized() }
        val partIndex = groupedParts.indexOfFirst { part -> part.exercises.any { it.id == expandId } }
        if (partIndex >= 0) {
            val heroAndLooseOffset = if (currentSession.exercises.isNotEmpty()) 2 else 1
            listState.animateScrollToItem(partIndex + heroAndLooseOffset)
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.updateBackgroundValue(uri.toString(), SessionBackgroundType.IMAGE)
        }
    }

    val hazeState = remember { HazeState() }
    val roadmapGlassStyle = remember {
        HazeStyle(
            blurRadius = 20.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.30f)),
            backgroundColor = Color.Black.copy(alpha = 0.34f),
            noiseFactor = 0.03f,
        )
    }

    // Snackbar for auto-save and navigation messages from ViewModel
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showKpknSnackbar(msg, SnackbarType.SUCCESS)
            viewModel.clearSnackbarMessage()
        }
    }

    val isCompactHeader by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160
        }
    }
    val partBounds = remember { mutableMapOf<String, Rect>() }
    val partContentBounds = remember { mutableMapOf<String, Rect>() }
    val exerciseBounds = remember { mutableMapOf<String, Rect>() }
    var looseContentBounds by remember { mutableStateOf<Rect?>(null) }
    var draggingPartId by remember { mutableStateOf<String?>(null) }
    var draggingPartOffsetY by remember { mutableStateOf(0f) }
    var partDropTargetId by remember { mutableStateOf<String?>(null) }
    var partDropTargetIndex by remember { mutableStateOf<Int?>(null) }
    var draggingExerciseId by remember { mutableStateOf<String?>(null) }
    var draggingExercisePartId by remember { mutableStateOf<String?>(null) }
    var draggingExerciseOffset by remember { mutableStateOf(Offset.Zero) }
    var exerciseDropTargetKey by remember { mutableStateOf<String?>(null) }
    var exerciseDropTargetPartId by remember { mutableStateOf<String?>(null) }
    var exerciseDropTargetIndex by remember { mutableStateOf<Int?>(null) }

    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val loadErrorMessage = uiState.loadErrorMessage
            if (loadErrorMessage != null) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        loadErrorMessage,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Puedes reintentar la carga o volver al programa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack) { Text("Volver") }
                        Button(onClick = viewModel::retryLoadSession) { Text("Reintentar") }
                    }
                }
            } else {
                LinearProgressIndicator(modifier = Modifier.width(180.dp))
            }
        }
        return
    }
    val groupedParts = session.parts.filterNot { it.isUncategorized() }

    LaunchedEffect(session.exercises.isEmpty()) {
        if (session.exercises.isEmpty()) {
            looseContentBounds = null
        }
    }

    BackHandler(enabled = !showDiscardDialog && uiState.sheet == SessionEditorSheet.NONE) {
        viewModel.saveDraftForExit()
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TemplatesFab(onClick = viewModel::openTemplates)
                    HeroGlassFab(
                        summary = uiState.augeSummary,
                        onClick = { viewModel.openSheet(SessionEditorSheet.AUGE) },
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            bottomBar = {
                    SessionContextNavigator(
                        sessions = uiState.siblingSessions,
                        selectedSessionId = uiState.selectedSiblingSessionId ?: session.id,
                        onSelectSession = viewModel::requestSessionSwitch,
                        weekStartDay = uiState.weekStartDay,
                        activeDayOfWeek = uiState.dayOfWeek,
                        onSelectDay = { day ->
                            viewModel.selectRoadmapDay(day)
                        },
                        roadmapOptions = uiState.roadmapOptions,
                        onSelectRoadmapOption = viewModel::selectRoadmapOption,
                        onCreateSessionForDay = { day ->
                            viewModel.createSessionForDay(day)
                        },
                        isSimpleProgram = uiState.isSimpleProgram,
                        hasActiveLoops = uiState.hasActiveLoops,
                        hazeState = hazeState,
                        hazeStyle = roadmapGlassStyle,
                        onSetMainSessionForDay = viewModel::setMainSessionForDay,
                        currentSessionId = session.id,
                        currentDayOfWeek = uiState.dayOfWeek,
                    )
            },
        ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 110.dp),
        ) {
            item {
                SessionHero(
                    session = session,
                    hasChanges = uiState.hasUnsavedChanges,
                    autoSaveEnabled = uiState.autoSaveEnabled,
                    latestBodyMeasurement = uiState.latestBodyMeasurement,
                    onNameChange = viewModel::updateSessionName,
                    onDescriptionChange = viewModel::updateSessionDescription,
                    onMeetDayChange = { targetState ->
                        competitionToggleTarget = targetState
                        showCompetitionModeConfirm = true
                    },
                    onMeetBodyweightChange = viewModel::updateSessionMeetBodyweight,
                    onSyncMeetBodyweight = {
                        val result = viewModel.syncMeetBodyweightFromLatestMeasurement()
                        scope.launch {
                            snackbarHostState.showKpknSnackbar(
                                result.message,
                                if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                            )
                        }
                    },
                    onSave = { viewModel.openSheet(SessionEditorSheet.SAVE) },
                    onOpenBackgroundSheet = { viewModel.openSheet(SessionEditorSheet.BACKGROUND) },
                    onOpenTransfer = { viewModel.openSheet(SessionEditorSheet.TRANSFER) },
                    onOpenHistory = { viewModel.openSheet(SessionEditorSheet.HISTORY) },
                    onOpenRules = { viewModel.openSheet(SessionEditorSheet.RULES) },
                    onAutoSaveToggle = { viewModel.setAutoSaveEnabled(!uiState.autoSaveEnabled) },
                    sessionsOnSameDay = uiState.siblingSessions.filter { it.dayOfWeek == session.dayOfWeek },
                    onSwitchSession = viewModel::requestSessionSwitch,
                    onSetMainSession = viewModel::setMainSessionForDay,
                )
            }

            if (session.isMeetDay) {
                item("competition-mode-editor") {
                    CompetitionSessionEditor(
                        session = session,
                        onUpdateSession = { updater: (Session) -> Session -> viewModel.updateCurrentSession(updater) },
                        onAddCompetitionMovement = {
                            viewModel.addCompetitionMovement("Movimiento ${session.exercises.size + 1}")
                        },
                    )
                }
            }

            if (!session.isMeetDay && session.exercises.isNotEmpty()) {
                item("loose-exercises") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .onGloballyPositioned { looseContentBounds = it.boundsInWindow() },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        session.exercises.forEachIndexed { index, exercise ->
                            key("loose|${exercise.id}") {
                                ExerciseEditorCard(
                                    exercise = exercise,
                                    exerciseInfo = EXERCISE_DATABASE.find { it.id == exercise.exerciseDbId },
                                    accentHex = PART_COLORS.first(),
                                    partId = "__loose__",
                                    modifier = Modifier.fillMaxWidth(),
                                    isDragging = draggingExerciseId == exercise.id,
                                    dragOffset = if (draggingExerciseId == exercise.id) draggingExerciseOffset else Offset.Zero,
                                    isDropTarget = (exerciseDropTargetKey == "__loose__|${exercise.id}" || (exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == index)) && draggingExerciseId != exercise.id,
                                    isPartDropTarget = exerciseDropTargetPartId == "__loose__" && draggingExerciseId != exercise.id,
                                    onBoundsChange = { rect -> exerciseBounds["__loose__|${exercise.id}"] = rect },
                                    onDragStart = {
                                        draggingExerciseId = exercise.id
                                        draggingExercisePartId = "__loose__"
                                        draggingExerciseOffset = Offset.Zero
                                        exerciseDropTargetKey = null
                                        exerciseDropTargetPartId = null
                                        exerciseDropTargetIndex = null
                                    },
                                    onDrag = { delta ->
                                        val activeExerciseId = draggingExerciseId ?: return@ExerciseEditorCard
                                        val currentPartId = draggingExercisePartId ?: return@ExerciseEditorCard
                                        draggingExerciseOffset += delta
                                        val activeRect = exerciseBounds["$currentPartId|$activeExerciseId"] ?: return@ExerciseEditorCard
                                        val center = Offset(activeRect.center.x + draggingExerciseOffset.x, activeRect.center.y + draggingExerciseOffset.y)

                                        // Find target exercise by rect containment
                                        val targetExerciseKey = exerciseBounds.entries.firstOrNull { (key, rect) ->
                                            key != "$currentPartId|$activeExerciseId" && rect.contains(center)
                                        }?.key
                                        if (targetExerciseKey != null) {
                                            exerciseDropTargetKey = targetExerciseKey
                                            exerciseDropTargetPartId = null
                                            exerciseDropTargetIndex = null
                                        } else {
                                            exerciseDropTargetKey = null
                                            // Find target part by bounds containment
                                            val targetPartId = when {
                                                looseContentBounds?.contains(center) == true -> "__loose__"
                                                else -> groupedParts.firstOrNull { candidate ->
                                                    partContentBounds[candidate.id]?.contains(center) == true
                                                }?.id
                                            }
                                            exerciseDropTargetPartId = targetPartId
                                            // Find insertion index by comparing Y position with sorted exercise bounds
                                            if (targetPartId != null) {
                                                val relevantBounds = exerciseBounds.filterKeys { it.startsWith("$targetPartId|") }
                                                val orderedKeys = relevantBounds.entries.sortedBy { it.value.top }
                                                val insertIdx = orderedKeys.indexOfFirst { (key, rect) ->
                                                    key != "$targetPartId|$activeExerciseId" && center.y < rect.center.y
                                                }
                                                exerciseDropTargetIndex = if (insertIdx >= 0) {
                                                    val selfIdx = orderedKeys.indexOfFirst { it.key == "$targetPartId|$activeExerciseId" }
                                                    if (selfIdx >= 0 && insertIdx > selfIdx) insertIdx - 1 else insertIdx
                                                } else {
                                                    val partSize = when (targetPartId) {
                                                        "__loose__" -> session.exercises.size
                                                        else -> session.parts.firstOrNull { it.id == targetPartId }?.exercises?.size ?: 0
                                                    }
                                                    (partSize - 1).coerceAtLeast(0)
                                                }
                                            } else {
                                                exerciseDropTargetIndex = null
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val activeExerciseId = draggingExerciseId
                                        val currentPartId = draggingExercisePartId
                                        if (activeExerciseId != null && currentPartId != null) {
                                            val finalTargetKey = exerciseDropTargetKey
                                            val finalTargetPart = exerciseDropTargetPartId
                                            val finalTargetIdx = exerciseDropTargetIndex
                                            if (finalTargetKey != null) {
                                                val tPartId = finalTargetKey.substringBefore("|")
                                                val tExId = finalTargetKey.substringAfter("|")
                                                val idx = when (tPartId) {
                                                    "__loose__" -> session.exercises.indexOfFirst { it.id == tExId }
                                                    else -> session.parts.firstOrNull { it.id == tPartId }?.exercises?.indexOfFirst { it.id == tExId }
                                                }
                                                if (idx != null && idx >= 0) {
                                                    viewModel.moveExerciseToPart(
                                                        sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                        exerciseId = activeExerciseId,
                                                        targetPartId = tPartId.takeUnless { it == "__loose__" },
                                                        targetIndex = idx,
                                                    )
                                                }
                                            } else if (finalTargetPart != null && finalTargetPart != currentPartId) {
                                                viewModel.moveExerciseToPart(
                                                    sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                    exerciseId = activeExerciseId,
                                                    targetPartId = finalTargetPart.takeUnless { it == "__loose__" },
                                                    targetIndex = null,
                                                )
                                            } else if (finalTargetIdx != null) {
                                                val selfIdx = session.exercises.indexOfFirst { it.id == activeExerciseId }
                                                if (finalTargetIdx != selfIdx) {
                                                    viewModel.moveExerciseToPart(
                                                        sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                        exerciseId = activeExerciseId,
                                                        targetPartId = currentPartId.takeUnless { it == "__loose__" },
                                                        targetIndex = finalTargetIdx,
                                                    )
                                                }
                                            }
                                        }
                                        draggingExerciseId = null
                                        draggingExercisePartId = null
                                        draggingExerciseOffset = Offset.Zero
                                        exerciseDropTargetKey = null
                                        exerciseDropTargetPartId = null
                                        exerciseDropTargetIndex = null
                                    },
                                    onUpdateExercise = { updater -> viewModel.updateExercise(null, exercise.id, updater) },
                                    onAddSet = { viewModel.addSet(null, exercise.id) },
                                    onUpdateSet = { setId, updater -> viewModel.updateSet(null, exercise.id, setId, updater) },
                                    onRemoveSet = { setId -> viewModel.removeSet(null, exercise.id, setId) },
                                    onMoveSet = { setId, dir -> viewModel.moveSet(null, exercise.id, setId, dir) },
                                    onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(null, exercise.id, mobilityId) },
                                    onOpenQuickActions = { viewModel.openExerciseQuickActions(null, exercise.id) },
                                    relationshipAnchorName = resolveRelationshipAnchorName(session, exercise),
                                    onOpenRelationshipPicker = { viewModel.openRelationshipPicker(null, exercise.id) },
                                    onClearRelationship = { viewModel.linkExerciseRelativeTo(null, exercise.id, null) },
                                    onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(null, exercise.id, type) },
                                    onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(null, exercise.id, notes) },
                                    autoExpand = pendingAutoExpandExerciseId == exercise.id,
                                    onAutoExpandHandled = {
                                        if (pendingAutoExpandExerciseId == exercise.id) pendingAutoExpandExerciseId = null
                                    },
                                )
                            }
                            AnimatedVisibility(
                                visible = draggingExerciseId != null && (
                                    exerciseDropTargetKey == "__loose__|${exercise.id}" ||
                                    (exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == index)
                                ),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val cornerRadiusPx = with(LocalDensity.current) { 12.dp.toPx() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .padding(horizontal = 16.dp)
                                        .drawWithContent {
                                            drawContent()
                                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                            drawRoundRect(
                                                color = primaryColor.copy(alpha = 0.24f),
                                                style = Stroke(width = 2f, pathEffect = dashEffect),
                                                cornerRadius = CornerRadius(cornerRadiusPx),
                                            )
                                        },
                                )
                            }
                            val shouldDrawDivider = if (index < session.exercises.lastIndex) {
                                val currentSupersetId = exercise.supersetId
                                val nextSupersetId = session.exercises[index + 1].supersetId
                                currentSupersetId == null || currentSupersetId != nextSupersetId
                            } else {
                                false
                            }
                            if (shouldDrawDivider) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                )
                            }
                        }
                    }
                }
            }

            if (!session.isMeetDay) itemsIndexed(groupedParts, key = { _, part -> part.id }) { _, part ->
                GroupEditorCard(
                    part = part,
                    collapsed = part.id in uiState.collapsedPartIds,
                    onToggleCollapse = { viewModel.togglePartCollapsed(part.id) },
                    onRename = { viewModel.updatePartName(part.id, it) },
                    onChangeColor = { viewModel.updatePartColor(part.id, it) },
                    onRemove = { keepExercises -> viewModel.removePart(part.id, keepExercises) },
                    isDragging = draggingPartId == part.id,
                    dragOffsetY = if (draggingPartId == part.id) draggingPartOffsetY else 0f,
                    isDropTarget = partDropTargetId == part.id && draggingPartId != part.id,
                    onBoundsChange = { rect -> partBounds[part.id] = rect },
                    onContentBoundsChange = { rect -> partContentBounds[part.id] = rect },
                    onDragStart = {
                        draggingPartId = part.id
                        draggingPartOffsetY = 0f
                        partDropTargetId = null
                        partDropTargetIndex = null
                    },
                    onDrag = { deltaY ->
                        val activeId = draggingPartId ?: return@GroupEditorCard
                        draggingPartOffsetY += deltaY
                        val activeRect = partBounds[activeId] ?: return@GroupEditorCard
                        val centerY = activeRect.center.y + draggingPartOffsetY
                        val targetId = groupedParts.firstOrNull { candidate ->
                            candidate.id != activeId && partBounds[candidate.id]?.contains(Offset(activeRect.center.x, centerY)) == true
                        }?.id
                        partDropTargetId = targetId
                        if (targetId != null) {
                            partDropTargetIndex = groupedParts.indexOfFirst { it.id == targetId }
                        } else {
                            partDropTargetIndex = null
                        }
                    },
                    onDragEnd = {
                        val activeId = draggingPartId
                        if (activeId != null && partDropTargetIndex != null) {
                            val currentIndex = groupedParts.indexOfFirst { it.id == activeId }
                            if (currentIndex != -1 && partDropTargetIndex != currentIndex) {
                                viewModel.movePartToIndex(activeId, partDropTargetIndex!!)
                            }
                        }
                        draggingPartId = null
                        draggingPartOffsetY = 0f
                        partDropTargetId = null
                        partDropTargetIndex = null
                    },
                    onAddExercise = { viewModel.openPicker(part.id) },
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            part.exercises.forEachIndexed { targetIndex, exercise ->
                                    key("${part.id}|${exercise.id}") {
                                        ExerciseEditorCard(
                                            exercise = exercise,
                                            exerciseInfo = EXERCISE_DATABASE.find { it.id == exercise.exerciseDbId },
                                            accentHex = part.color,
                                            partId = part.id,
                                            modifier = Modifier.fillMaxWidth(),
                                            isDragging = draggingExerciseId == exercise.id,
                                            dragOffset = if (draggingExerciseId == exercise.id) draggingExerciseOffset else Offset.Zero,
                                            isDropTarget = (exerciseDropTargetKey == "${part.id}|${exercise.id}" || (exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == targetIndex)) && draggingExerciseId != exercise.id,
                                            isPartDropTarget = exerciseDropTargetPartId == part.id && draggingExerciseId != exercise.id,
                                            onBoundsChange = { rect -> exerciseBounds["${part.id}|${exercise.id}"] = rect },
                                            onDragStart = {
                                                draggingExerciseId = exercise.id
                                                draggingExercisePartId = part.id
                                                draggingExerciseOffset = Offset.Zero
                                                exerciseDropTargetKey = null
                                                exerciseDropTargetPartId = null
                                                exerciseDropTargetIndex = null
                                            },
                                            onDrag = { delta ->
                                                val activeExerciseId = draggingExerciseId ?: return@ExerciseEditorCard
                                                val currentPartId = draggingExercisePartId ?: return@ExerciseEditorCard
                                                draggingExerciseOffset += delta
                                                val activeRect = exerciseBounds["$currentPartId|$activeExerciseId"] ?: return@ExerciseEditorCard
                                                val center = Offset(activeRect.center.x + draggingExerciseOffset.x, activeRect.center.y + draggingExerciseOffset.y)

                                                // Find target exercise by rect containment
                                                val targetExerciseKey = exerciseBounds.entries.firstOrNull { (key, rect) ->
                                                    key != "$currentPartId|$activeExerciseId" && rect.contains(center)
                                                }?.key
                                                if (targetExerciseKey != null) {
                                                    exerciseDropTargetKey = targetExerciseKey
                                                    exerciseDropTargetPartId = null
                                                    exerciseDropTargetIndex = null
                                                } else {
                                                    exerciseDropTargetKey = null
                                                    // Find target part by bounds containment
                                                    val targetPartId = when {
                                                        looseContentBounds?.contains(center) == true -> "__loose__"
                                                        else -> groupedParts.firstOrNull { candidate ->
                                                            partContentBounds[candidate.id]?.contains(center) == true
                                                        }?.id
                                                    }
                                                    exerciseDropTargetPartId = targetPartId
                                                    // Find insertion index by comparing Y position with sorted exercise bounds
                                                    if (targetPartId != null) {
                                                        val relevantBounds = exerciseBounds.filterKeys { it.startsWith("$targetPartId|") }
                                                        val orderedKeys = relevantBounds.entries.sortedBy { it.value.top }
                                                        val insertIdx = orderedKeys.indexOfFirst { (key, rect) ->
                                                            key != "$targetPartId|$activeExerciseId" && center.y < rect.center.y
                                                        }
                                                        exerciseDropTargetIndex = if (insertIdx >= 0) {
                                                            val selfIdx = orderedKeys.indexOfFirst { it.key == "$targetPartId|$activeExerciseId" }
                                                            if (selfIdx >= 0 && insertIdx > selfIdx) insertIdx - 1 else insertIdx
                                                        } else {
                                                            val partSize = when (targetPartId) {
                                                                "__loose__" -> session.exercises.size
                                                                else -> session.parts.firstOrNull { it.id == targetPartId }?.exercises?.size ?: 0
                                                            }
                                                            (partSize - 1).coerceAtLeast(0)
                                                        }
                                                    } else {
                                                        exerciseDropTargetIndex = null
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                val activeExerciseId = draggingExerciseId
                                                val currentPartId = draggingExercisePartId
                                                if (activeExerciseId != null && currentPartId != null) {
                                                    val finalTargetKey = exerciseDropTargetKey
                                                    val finalTargetPart = exerciseDropTargetPartId
                                                    val finalTargetIdx = exerciseDropTargetIndex
                                                    if (finalTargetKey != null) {
                                                        val tPartId = finalTargetKey.substringBefore("|")
                                                        val tExId = finalTargetKey.substringAfter("|")
                                                        val idx = when (tPartId) {
                                                            "__loose__" -> session.exercises.indexOfFirst { it.id == tExId }
                                                            else -> session.parts.firstOrNull { it.id == tPartId }?.exercises?.indexOfFirst { it.id == tExId }
                                                        }
                                                        if (idx != null && idx >= 0) {
                                                            viewModel.moveExerciseToPart(
                                                                sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                                exerciseId = activeExerciseId,
                                                                targetPartId = tPartId.takeUnless { it == "__loose__" },
                                                                targetIndex = idx,
                                                            )
                                                        }
                                                    } else if (finalTargetPart != null && finalTargetPart != currentPartId) {
                                                        viewModel.moveExerciseToPart(
                                                            sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                            exerciseId = activeExerciseId,
                                                            targetPartId = finalTargetPart.takeUnless { it == "__loose__" },
                                                            targetIndex = null,
                                                        )
                                                    } else if (finalTargetIdx != null) {
                                                        val exercisesList = when (currentPartId) {
                                                            "__loose__" -> session.exercises
                                                            else -> session.parts.firstOrNull { it.id == currentPartId }?.exercises ?: emptyList()
                                                        }
                                                        val selfIdx = exercisesList.indexOfFirst { it.id == activeExerciseId }
                                                        if (finalTargetIdx != selfIdx) {
                                                            viewModel.moveExerciseToPart(
                                                                sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                                exerciseId = activeExerciseId,
                                                                targetPartId = currentPartId.takeUnless { it == "__loose__" },
                                                                targetIndex = finalTargetIdx,
                                                            )
                                                        }
                                                    }
                                                }
                                                draggingExerciseId = null
                                                draggingExercisePartId = null
                                                draggingExerciseOffset = Offset.Zero
                                                exerciseDropTargetKey = null
                                                exerciseDropTargetPartId = null
                                                exerciseDropTargetIndex = null
                                            },
                                            onUpdateExercise = { updater -> viewModel.updateExercise(part.id, exercise.id, updater) },
                                            onAddSet = { viewModel.addSet(part.id, exercise.id) },
                                            onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, exercise.id, setId, updater) },
                                            onRemoveSet = { setId -> viewModel.removeSet(part.id, exercise.id, setId) },
                                            onMoveSet = { setId, dir -> viewModel.moveSet(part.id, exercise.id, setId, dir) },
                                            onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(part.id, exercise.id, mobilityId) },
                                            onOpenQuickActions = { viewModel.openExerciseQuickActions(part.id, exercise.id) },
                                            relationshipAnchorName = resolveRelationshipAnchorName(session, exercise),
                                            onOpenRelationshipPicker = { viewModel.openRelationshipPicker(part.id, exercise.id) },
                                            onClearRelationship = { viewModel.linkExerciseRelativeTo(part.id, exercise.id, null) },
                                            onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(part.id, exercise.id, type) },
                                            onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(part.id, exercise.id, notes) },
                                            autoExpand = pendingAutoExpandExerciseId == exercise.id,
                                            onAutoExpandHandled = {
                                                if (pendingAutoExpandExerciseId == exercise.id) pendingAutoExpandExerciseId = null
                                            },
                                        )
                                    }
                                val shouldDrawDivider = if (targetIndex < part.exercises.lastIndex) {
                                    val currentSupersetId = exercise.supersetId
                                    val nextSupersetId = part.exercises[targetIndex + 1].supersetId
                                    currentSupersetId == null || currentSupersetId != nextSupersetId
                                } else {
                                    false
                                }
                                if (shouldDrawDivider) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    )
                                }
                            }
                        }
                    },
                )
            }

            if (!session.isMeetDay) item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = viewModel::openPickerForUncategorized,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir ejercicio", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = viewModel::addPart,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nuevo grupo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }

    }

    SessionEditorSheets(
        uiState = uiState,
        onDismiss = viewModel::closeSheet,
        onPickImage = { openDocument.launch(arrayOf("image/*")) },
        onSelectGradient = { viewModel.updateBackgroundValue(it, SessionBackgroundType.COLOR) },
        onBackgroundBlurChange = { viewModel.updateBackgroundStyle(blur = it) },
        onBackgroundBrightnessChange = { viewModel.updateBackgroundStyle(brightness = it) },
        onCoverBrightnessChange = viewModel::updateFilterBrightness,
        onCoverContrastChange = { viewModel.updateCoverFilters(contrast = it) },
        onCoverSaturationChange = { viewModel.updateCoverFilters(saturation = it) },
        onCoverGrayscaleChange = { viewModel.updateCoverFilters(grayscale = it) },
        onCoverVignetteChange = { viewModel.updateCoverFilters(vignette = it) },
        onCoverMotionChange = viewModel::updateCoverMotion,
        onLabelPositionChange = viewModel::updateLabelPosition,
        onExerciseSearch = viewModel::setSearchQuery,
        onSelectExercise = { info ->
            val targetPartId = uiState.pickerTargetPartId
            val targetExerciseId = uiState.pickerTargetExerciseId
            when {
                targetExerciseId != null -> {
                    viewModel.replaceExerciseInPart(targetPartId, targetExerciseId, info)
                    pendingAutoExpandExerciseId = targetExerciseId
                }
                else -> {
                    pendingAutoExpandExerciseId = viewModel.addExerciseToPart(targetPartId, info)
                }
            }
        },
        onMultiSelectExercises = { infos ->
            val targetPartId = uiState.pickerTargetPartId
            viewModel.addExercisesToPart(targetPartId, infos)
        },
        onApplyRules = { partId ->
            viewModel.applyRuleDefaultsToSession(partId)
            scope.launch {
                val message = if (partId == null) {
                    "Defaults aplicados a la sesión"
                } else {
                    "Defaults aplicados al grupo"
                }
                snackbarHostState.showKpknSnackbar(message, SnackbarType.SUCCESS)
            }
        },
        onCloneCurrentToTargets = { targetKeys, selectedExerciseIds, applyMode ->
            val result = viewModel.cloneCurrentSessionToTargets(targetKeys, selectedExerciseIds, applyMode)
            scope.launch {
                snackbarHostState.showKpknSnackbar(
                    result.message,
                    if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                )
            }
        },
        onImportFromSource = { sourceSessionId, selectedExerciseIds, applyMode ->
            val result = viewModel.importFromSourceSession(sourceSessionId, selectedExerciseIds, applyMode)
            scope.launch {
                snackbarHostState.showKpknSnackbar(
                    result.message,
                    if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                )
            }
        },
        onSave = { saveScope ->
            val hasPendingSwitch = uiState.pendingSessionSwitchId != null
            val saveResult = viewModel.saveSession(saveScope)
            if (saveResult.success && !hasPendingSwitch) {
                // Exit immediately after a successful save; waiting on snackbar blocks navigation.
                onSavedAndExit()
            } else {
                scope.launch {
                    snackbarHostState.showKpknSnackbar(
                        saveResult.message,
                        if (saveResult.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                    )
                }
            }
        },
        onApplyAugeCorrection = { alertId ->
            viewModel.applyAugeCorrection(alertId)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Ajuste AUGE aplicado", SnackbarType.SUCCESS)
            }
        },
        onAddGhostExercise = { cardId ->
            viewModel.addGhostExercise(cardId)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Ejercicio añadido a la sesión", SnackbarType.SUCCESS)
            }
        },
        onApplyAssistantSuggestion = { suggestionId ->
            viewModel.applyAssistantSuggestion(suggestionId)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Ajuste aplicado", SnackbarType.SUCCESS)
            }
        },
        onDiscardSwitch = if (uiState.pendingSessionSwitchId != null) viewModel::discardAndSwitchPendingSession else null,
        onWarmupSave = { exerciseId, sets ->
            val partId = session.parts.firstOrNull { part -> part.exercises.any { it.id == exerciseId } }?.id
            viewModel.updateWarmupSets(partId, exerciseId, sets)
            viewModel.closeSheet()
        },
        onRestoreSnapshot = viewModel::restoreDraftSnapshot,
        onRuleDefaultsChange = { setCount, reps, rpe ->
            viewModel.updateRuleDefaults(setCount = setCount, reps = reps, rpe = rpe)
        },
        onRuleLimitsChange = { maxRPE, maxExercisesPerMuscle ->
            viewModel.updateRuleLimits(maxRPE = maxRPE, maxExercisesPerMuscle = maxExercisesPerMuscle)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Límites guardados", SnackbarType.SUCCESS)
            }
        },
        onAdvancedRuleLimitsChange = { maxSessionVolume, maxWeeklyVolume, maxPattern, rigid ->
            viewModel.updateAdvancedRuleLimits(
                maxVolumePerMuscleSession = maxSessionVolume,
                maxVolumePerMuscleWeekly = maxWeeklyVolume,
                maxSamePatternPerSession = maxPattern,
                rigidLimits = rigid,
            )
        },
        onApplyGlobalIntensityAdjustment = { mode, value, muscles ->
            viewModel.applyGlobalIntensityAdjustment(mode, value, muscles)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Modificación global aplicada", SnackbarType.SUCCESS)
            }
        },
        onQuickActionOpenPicker = viewModel::triggerQuickActionOpenPicker,
        onQuickActionOpenWarmup = viewModel::triggerQuickActionOpenWarmup,
        onQuickActionOpenMobility = viewModel::triggerQuickActionOpenMobility,
        onAddMobilityExercise = viewModel::addMobilityToQuickActionExercise,
        onQuickActionDelete = viewModel::triggerQuickActionDelete,
        onQuickActionCreateSuperset = viewModel::triggerQuickActionCreateSuperset,
        onQuickActionManageSuperset = viewModel::triggerQuickActionManageSuperset,
        onLinkExerciseRelativeTo = viewModel::linkExerciseRelativeTo,
        onOpenSupersetManager = viewModel::openSupersetManager,
        onUpdateSupersetRestBetween = viewModel::updateSupersetRestBetween,
        onUpdateSupersetRestAfter = viewModel::updateSupersetRestAfter,
        onRemoveFromSuperset = viewModel::removeFromSuperset,
        onSupersetDraftUpdate = viewModel::updateSupersetDraft,
        onCreateSupersetGroup = viewModel::createSupersetGroupFromDraft,
        onOpenSupersetCreator = viewModel::openSupersetCreator,
        onOpenExerciseDetail = onOpenExerciseDetail,
        onOpenExerciseCreator = onOpenExerciseCreator,
        allTemplates = allTemplates,
        onSelectTemplate = viewModel::selectTemplate,
        onConfirmApplyTemplate = viewModel::confirmTemplateApply,
        onCancelTemplateApply = viewModel::cancelTemplateApply,
        onTemplateSearchChange = viewModel::setTemplateSearchQuery,
    )

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Salir del editor", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tienes cambios sin guardar.")
                    OutlinedButton(
                        onClick = {
                            showDiscardDialog = false
                            viewModel.discardDraftForCurrentSession()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cerrar sin guardar")
                    }
                    Button(
                        onClick = {
                            val result = viewModel.saveSession()
                            scope.launch {
                                if (result.success) {
                                    showDiscardDialog = false
                                    onSavedAndExit()
                                } else {
                                    snackbarHostState.showKpknSnackbar(result.message, SnackbarType.DANGER)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Guardar y salir")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Continuar editando") }
            },
        )
    }

    if (showCompetitionModeConfirm) {
        AlertDialog(
            onDismissRequest = { showCompetitionModeConfirm = false },
            title = {
                Text(
                    if (competitionToggleTarget) "Activar modo competición" else "Salir de modo competición",
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Text(
                    if (competitionToggleTarget) {
                        "Guardaremos respaldo de tu sesión de entrenamiento y transformaremos esta sesión en formato de competición."
                    } else {
                        "Vamos a restaurar tu sesión de entrenamiento desde el respaldo guardado para este día."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompetitionModeConfirm = false
                        viewModel.updateSessionMeetDay(competitionToggleTarget)
                        scope.launch {
                            snackbarHostState.showKpknSnackbar(
                                if (competitionToggleTarget) {
                                    "Modo competición activado"
                                } else {
                                    "Modo entrenamiento restaurado"
                                },
                                SnackbarType.SUCCESS,
                            )
                        }
                    }
                ) {
                    Text(if (competitionToggleTarget) "Activar" else "Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompetitionModeConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun SessionHero(
    session: Session,
    hasChanges: Boolean,
    autoSaveEnabled: Boolean,
    latestBodyMeasurement: BodyMeasurementEntry?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMeetDayChange: (Boolean) -> Unit,
    onMeetBodyweightChange: (Double?) -> Unit,
    onSyncMeetBodyweight: () -> Unit,
    onSave: () -> Unit,
    onOpenBackgroundSheet: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onAutoSaveToggle: () -> Unit,
    sessionsOnSameDay: List<Session> = emptyList(),
    onSwitchSession: (String) -> Unit = {},
    onSetMainSession: (String) -> Unit = {},
) {
      val background = session.background
      val brightness = background?.style?.brightness ?: 0.92f
      val blur = (background?.style?.blur ?: 0f).dp
      Box(
          modifier = Modifier.fillMaxWidth(),
      ) {
         Box(
             modifier = Modifier
                 .matchParentSize()
                 .background(Color.Black)
         ) {
             SessionBackgroundLayer(background = background, blurDp = blur)
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .background(
                         Brush.verticalGradient(
                             colors = listOf(
                                 Color.Black.copy(alpha = (1f - brightness.coerceIn(0.25f, 1f)) * 0.55f),
                                 Color.Black.copy(alpha = 0.12f),
                                 Color.Black.copy(alpha = 0.78f),
                             ),
                         )
                     ),
             )
         }

         Column(
             modifier = Modifier
                 .fillMaxWidth()
                 .statusBarsPadding()
                 .padding(horizontal = 16.dp, vertical = 10.dp),
             verticalArrangement = Arrangement.spacedBy(6.dp),
         ) {
              Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      SuggestionChip(
                          onClick = onAutoSaveToggle,
                          label = {
                              Text(
                                  if (autoSaveEnabled) "Auto: On" else "Auto: Off",
                                  style = MaterialTheme.typography.labelSmall,
                              )
                          },
                          icon = {
                              Icon(
                                  if (autoSaveEnabled) Icons.Default.CheckCircle else Icons.Default.Close,
                                  contentDescription = null,
                                  modifier = Modifier.size(14.dp),
                              )
                          },
                          shape = RoundedCornerShape(999.dp),
                      )
                      Spacer(Modifier.width(6.dp))
                      Surface(
                          onClick = onOpenBackgroundSheet,
                          shape = CircleShape,
                          color = Color.Black.copy(alpha = 0.24f),
                          border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                      ) {
                          Box(
                              modifier = Modifier.size(34.dp),
                              contentAlignment = Alignment.Center,
                          ) {
                              Icon(
                                  Icons.Default.Palette,
                                  "Editar fondo",
                                  tint = Color.White,
                                  modifier = Modifier.size(18.dp),
                              )
                          }
                      }
                      Spacer(Modifier.width(6.dp))
                      HeroGlassIconButton(
                          icon = Icons.Default.Save,
                          contentDescription = "Guardar sesión",
                          onClick = onSave,
                          showUnsavedDot = hasChanges,
                      )
                  }

                  val titleFontSize = when {
                      session.name.length < 15 -> 36.sp
                      session.name.length < 25 -> 28.sp
                      else -> 22.sp
                  }

                  OutlinedTextField(
                      value = session.name,
                      onValueChange = onNameChange,
                      modifier = Modifier.fillMaxWidth(),
                      placeholder = { Text("Nueva sesión", color = Color.White.copy(alpha = 0.72f)) },
                      singleLine = true,
                      shape = RoundedCornerShape(0.dp),
                      textStyle = MaterialTheme.typography.displaySmall.copy(
                          fontSize = titleFontSize,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                      ),
                      colors = OutlinedTextFieldDefaults.colors(
                          focusedContainerColor = Color.Transparent,
                          unfocusedContainerColor = Color.Transparent,
                          focusedBorderColor = Color.Transparent,
                          unfocusedBorderColor = Color.Transparent,
                          focusedTextColor = Color.White,
                          unfocusedTextColor = Color.White,
                          cursorColor = Color.White,
                      ),
                  )

                  OutlinedTextField(
                      value = session.description.orEmpty(),
                      onValueChange = onDescriptionChange,
                      modifier = Modifier.fillMaxWidth(),
                      placeholder = { Text("Añadir descripción", color = Color.White.copy(alpha = 0.62f)) },
                      singleLine = false,
                      minLines = 1,
                      maxLines = 2,
                      shape = RoundedCornerShape(0.dp),
                      textStyle = MaterialTheme.typography.bodyMedium.copy(
                          color = Color.White.copy(alpha = 0.86f),
                          fontWeight = FontWeight.Medium,
                      ),
                      colors = OutlinedTextFieldDefaults.colors(
                          focusedContainerColor = Color.Transparent,
                          unfocusedContainerColor = Color.Transparent,
                          focusedBorderColor = Color.Transparent,
                          unfocusedBorderColor = Color.Transparent,
                          focusedTextColor = Color.White,
                          unfocusedTextColor = Color.White,
                          cursorColor = Color.White,
                      ),
                  )

                  // Action chips row
                  Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .horizontalScroll(rememberScrollState())
                         .padding(horizontal = 4.dp),
                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                     verticalAlignment = Alignment.CenterVertically,
                 ) {
                     SuggestionChip(
                         onClick = onOpenTransfer,
                         icon = { Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(14.dp)) },
                         label = { Text("Transferir", style = MaterialTheme.typography.labelSmall) },
                         shape = RoundedCornerShape(999.dp),
                     )
                     SuggestionChip(
                         onClick = onOpenHistory,
                         icon = { Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp)) },
                         label = { Text("Historial", style = MaterialTheme.typography.labelSmall) },
                         shape = RoundedCornerShape(999.dp),
                     )
                     SuggestionChip(
                         onClick = onOpenRules,
                         icon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp)) },
                         label = { Text("Reglas", style = MaterialTheme.typography.labelSmall) },
                         shape = RoundedCornerShape(999.dp),
                     )
                     SuggestionChip(
                         onClick = { onMeetDayChange(!session.isMeetDay) },
                         icon = { Icon(Icons.Default.WorkspacePremium, null, modifier = Modifier.size(14.dp)) },
                         label = { Text("Cambiar a sesión de competición", style = MaterialTheme.typography.labelSmall) },
                         shape = RoundedCornerShape(999.dp),
                     )
                 }

                // Multi-session day: session switcher row
                if (sessionsOnSameDay.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        sessionsOnSameDay.forEach { ssn ->
                            val isCurrent = ssn.id == session.id
                            val isPrimary = ssn.isMainSession
                            AssistChip(
                                onClick = { if (!isCurrent) onSwitchSession(ssn.id) },
                                label = {
                                    Text(
                                        if (isPrimary) "★ ${ssn.name.ifBlank { "Sesión" }}" else ssn.name.ifBlank { "Sesión" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    if (isCurrent) {
                                        Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                                    } else if (!isPrimary) {
                                        Icon(
                                            Icons.Default.StarBorder,
                                            "Marcar como principal",
                                            Modifier.size(14.dp).clickable { onSetMainSession(ssn.id) },
                                        )
                                    } else {
                                        Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isCurrent) Color.White.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.10f),
                                    labelColor = Color.White,
                                ),
                            )
                        }
                    }
                }

                if (session.isMeetDay) {
                    OutlinedTextField(
                        value = session.meetBodyweight?.let(::formatEditableNumber).orEmpty(),
                        onValueChange = { onMeetBodyweightChange(it.safeDoubleOrNull()) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Peso corporal objetivo (kg)", color = Color.White.copy(alpha = 0.72f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.22f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.22f),
                            focusedBorderColor = Color.White.copy(alpha = 0.38f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color.White.copy(alpha = 0.82f),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.62f),
                            cursorColor = Color.White,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val measurementText = latestBodyMeasurement?.weight?.let { weight ->
                            "Medición reciente: ${formatOneDecimal(weight)} kg (${latestBodyMeasurement.date})"
                        } ?: "Sin medición corporal reciente"
                        Text(
                            text = measurementText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        OutlinedButton(
                            onClick = onSyncMeetBodyweight,
                            enabled = latestBodyMeasurement?.weight != null,
                        ) {
                            Text("Usar medición")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionBackgroundLayer(background: SessionBackground?, blurDp: androidx.compose.ui.unit.Dp) {
    when {
        background == null || background.type == SessionBackgroundType.COLOR -> {
            val gradient = sessionBackgroundPresets.firstOrNull { it.id == background?.value } ?: sessionGradients.first()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradient.colors))
                    .blur(blurDp),
            )
        }
        else -> {
            AsyncImage(
                model = background.value,
                contentDescription = "Fondo de sesión",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurDp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun HeroGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    showUnsavedDot: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.24f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            if (showUnsavedDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)),
                )
            }
        }
    }
}

@Composable
private fun HeroActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color.White.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
        ),
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TemplatesFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Plantillas de sesión",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun HeroGlassFab(
    summary: SessionEditorAugeSummary,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = "Abrir Asistente de sesión",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SessionContextNavigator(
    sessions: List<Session>,
    selectedSessionId: String,
    onSelectSession: (String) -> Unit,
    weekStartDay: Int,
    activeDayOfWeek: Int?,
    onSelectDay: (Int) -> Unit,
    roadmapOptions: List<SessionRoadmapOption>,
    onSelectRoadmapOption: (SessionRoadmapOption) -> Unit,
    onCreateSessionForDay: (Int) -> Unit,
    isSimpleProgram: Boolean,
    hasActiveLoops: Boolean,
    hazeState: HazeState?,
    hazeStyle: HazeStyle,
    onSetMainSessionForDay: (String) -> Unit,
    currentSessionId: String,
    currentDayOfWeek: Int?,
) {
    val orderedDays = remember(weekStartDay) {
        val safeStart = weekStartDay.coerceIn(1, 7)
        val base = listOf(1, 2, 3, 4, 5, 6, 7)
        val offset = safeStart - 1
        base.drop(offset) + base.take(offset)
    }
    val selectedSessionDay = remember(sessions, selectedSessionId) {
        sessions.firstOrNull { it.id == selectedSessionId }?.dayOfWeek
    }
    var selectedDay by remember(activeDayOfWeek, selectedSessionDay, orderedDays) {
        mutableStateOf(activeDayOfWeek ?: selectedSessionDay ?: orderedDays.first())
    }
    var showRoadmapMenu by remember { mutableStateOf(false) }
    val showRoadmapMenuButton = roadmapOptions.size > 1

    // Sessions grouped by day
    val sessionsByDay = remember(sessions) {
        sessions.groupBy { it.dayOfWeek ?: 99 }
    }
    val sessionsOnSelectedDay = remember(sessionsByDay, selectedDay) {
        sessionsByDay[selectedDay].orEmpty()
    }

    // Block chips (advanced programs only)
    val uniqueBlocks = remember(roadmapOptions) {
        roadmapOptions.map { it.blockIndex to it.blockName }.distinctBy { it.first }
    }
    var selectedBlockIndex by rememberSaveable { mutableStateOf(-1) }

    // Create session dialog state
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var pendingCreateDay by remember { mutableIntStateOf(-1) }

    val navModifier = if (hazeState != null) {
        Modifier.fillMaxWidth().hazeEffect(state = hazeState, style = hazeStyle)
    } else {
        Modifier.fillMaxWidth()
    }
    Surface(
        modifier = navModifier,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        color = Color.Black.copy(alpha = 0.18f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Block chips for advanced programs
            if (!isSimpleProgram && uniqueBlocks.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    uniqueBlocks.forEach { (blockIndex, blockName) ->
                        FilterChip(
                            selected = selectedBlockIndex == blockIndex,
                            onClick = {
                                selectedBlockIndex = blockIndex
                                val option = roadmapOptions.firstOrNull { it.blockIndex == blockIndex }
                                if (option != null) onSelectRoadmapOption(option)
                            },
                            label = {
                                Text(blockName, style = MaterialTheme.typography.labelSmall)
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            // Week info for simple programs with active loops
            if (isSimpleProgram && hasActiveLoops) {
                val currentOpt = roadmapOptions.firstOrNull { it.weekId.isNotBlank() }
                if (currentOpt != null) {
                    Text(
                        text = "Semana ${currentOpt.weekIndex + 1} · ${currentOpt.weekName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // Day circles row
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        orderedDays.forEach { day ->
                            val daySessions = sessionsByDay[day].orEmpty()
                            val hasSession = daySessions.isNotEmpty()
                            val sessionCount = daySessions.size
                            val isMultiSession = sessionCount > 1
                            val selectedDayChip = selectedDay == day
                            val selectedDayColor = Color(0xFF2563EB)
                            val isDimmed = !hasSession
                            val alphaFactor = if (isDimmed) 0.35f else 1f

                            val backgroundColor = when {
                                selectedDayChip && hasSession -> selectedDayColor
                                selectedDayChip -> selectedDayColor.copy(alpha = 0.35f)
                                isDimmed -> Color.White.copy(alpha = 0.2f)
                                else -> Color.White
                            }
                            val borderColor = when {
                                selectedDayChip && hasSession -> Color.Transparent
                                selectedDayChip -> selectedDayColor
                                isDimmed -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.outline
                            }
                            val borderWidth = if (selectedDayChip) 1.8.dp else 1.dp
                            val textColor = when {
                                selectedDayChip && hasSession -> Color.White
                                selectedDayChip -> selectedDayColor
                                isDimmed -> Color.Black.copy(alpha = 0.4f)
                                else -> Color.Black
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(backgroundColor)
                                    .border(
                                        width = borderWidth,
                                        color = borderColor,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        selectedDay = day
                                        if (hasSession) {
                                            val primaryOrFirst = daySessions.firstOrNull { it.isMainSession } ?: daySessions.first()
                                            onSelectSession(primaryOrFirst.id)
                                        } else {
                                            pendingCreateDay = day
                                            showCreateSessionDialog = true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dayInitial(day),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                )
                                // Small dot for days with sessions (not selected)
                                if (hasSession && !selectedDayChip) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-4).dp, y = (-4).dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(selectedDayColor),
                                    )
                                }
                                // Session count badge for multi-session days
                                if (isMultiSession && selectedDayChip) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$sessionCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        // Roadmap menu button (three dots)
                        if (showRoadmapMenuButton) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { showRoadmapMenu = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Cambiar roadmap",
                                    tint = Color.White.copy(alpha = 0.7f),
                                )
                                DropdownMenu(
                                    expanded = showRoadmapMenu,
                                    onDismissRequest = { showRoadmapMenu = false },
                                ) {
                                    roadmapOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        "B${option.blockIndex + 1} · S${option.weekIndex + 1}",
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                    Text(
                                                        option.weekName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showRoadmapMenu = false
                                                onSelectRoadmapOption(option)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Multi-session day: session switcher pills
            if (sessionsOnSelectedDay.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sessionsOnSelectedDay.forEach { ssn ->
                        val isCurrent = ssn.id == currentSessionId
                        val isPrimary = ssn.isMainSession
                        AssistChip(
                            onClick = { if (!isCurrent) onSelectSession(ssn.id) },
                            label = {
                                Text(
                                    if (isPrimary) "★ ${ssn.name.ifBlank { "Sesión" }}" else ssn.name.ifBlank { "Sesión" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = {
                                if (isCurrent) {
                                    Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                                } else if (!isPrimary) {
                                    Icon(
                                        Icons.Default.StarBorder,
                                        "Marcar como principal",
                                        Modifier.size(14.dp).clickable { onSetMainSessionForDay(ssn.id) },
                                    )
                                } else {
                                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        }
    }

    // Create session dialog
    if (showCreateSessionDialog && pendingCreateDay > 0) {
        AlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("¿Crear sesión para ${dayLabel(pendingCreateDay)}?") },
            text = {
                Text("Este día no tiene una sesión asignada. ¿Deseas crear una nueva sesión aquí?")
            },
            confirmButton = {
                Button(onClick = {
                    showCreateSessionDialog = false
                    onCreateSessionForDay(pendingCreateDay)
                }) {
                    Text("Crear sesión")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateSessionDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun GroupEditorCard(
    part: SessionPart,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRename: (String) -> Unit,
    onChangeColor: (String) -> Unit,
    onRemove: (Boolean) -> Unit,
    isDragging: Boolean,
    dragOffsetY: Float,
    isDropTarget: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onContentBoundsChange: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onAddExercise: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember(part.id) { mutableStateOf(false) }
    var showDeleteModeConfirm by remember(part.id) { mutableStateOf(false) }
    val partColor = remember(part.color) {
        runCatching { Color(AndroidColor.parseColor(part.color ?: PART_COLORS.first())) }.getOrDefault(Color(0xFF00F0FF))
    }
    val dropScale by animateFloatAsState(if (isDropTarget) 1.01f else 1f, label = "partDropScale")
    val normalizedName = part.name.trim()
    val displayName = if (normalizedName.isBlank()) "GRUPO" else normalizedName.uppercase()
    val shouldShowDeleteChoice = part.exercises.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = if (isDragging) 1.02f else dropScale
                scaleY = if (isDragging) 1.02f else dropScale
                alpha = if (isDragging) 0.96f else 1f
                shadowElevation = if (isDragging) 28.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 10f else 0f),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = partColor.copy(alpha = 0.13f),
            border = if (isDragging || isDropTarget) {
                androidx.compose.foundation.BorderStroke(
                    width = if (isDragging) 2.dp else 1.5.dp,
                    color = partColor.copy(alpha = if (isDragging) 0.95f else 0.7f),
                )
            } else null,
        ) {
            Column {
                SwipeToDeleteCard(
                    onDelete = {
                        if (shouldShowDeleteChoice) {
                            showDeleteModeConfirm = true
                        } else {
                            showDeleteConfirm = true
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Mantén pulsado para reordenar grupo",
                            tint = partColor.copy(alpha = if (isDragging) 0.92f else 0.56f),
                            modifier = Modifier
                                .size(18.dp)
                                .pointerInput(part.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { onDragStart() },
                                        onDragCancel = { onDragEnd() },
                                        onDragEnd = { onDragEnd() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            onDrag(dragAmount.y)
                                        }
                                    )
                                },
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(partColor)
                                .clickable { showColorPicker = !showColorPicker },
                        )
                        if (part.isUncategorized()) {
                            Text(
                                "SIN GRUPO",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = partColor,
                            )
                        } else {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicTextField(
                                    value = normalizedName.uppercase(),
                                    onValueChange = { input ->
                                        onRename(input.trim())
                                    },
                                    singleLine = true,
                                    cursorBrush = SolidColor(partColor),
                                    textStyle = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (normalizedName.isBlank()) {
                                                Text(
                                                    displayName,
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 14.sp,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .fillMaxWidth()
                                        .background(partColor.copy(alpha = 0.4f))
                                )
                            }
                        }
                        IconButton(
                            onClick = onToggleCollapse,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (collapsed) "Expandir" else "Colapsar",
                                tint = partColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                AnimatedVisibility(showColorPicker) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PART_COLORS.forEach { hex ->
                                val c = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.Gray)
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (hex == part.color) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        .clickable {
                                            onChangeColor(hex)
                                            showColorPicker = false
                                        }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(!collapsed) {
                        Column(
                            modifier = Modifier.onGloballyPositioned { onContentBoundsChange(it.boundsInWindow()) },
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            content()
                            FilledTonalButton(
                                onClick = onAddExercise,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Agregar ejercicio en ${displayName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar grupo", fontWeight = FontWeight.Black) },
            text = { Text("¿Eliminar este grupo?") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showDeleteConfirm = false
                    onRemove(false)
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showDeleteModeConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteModeConfirm = false },
            title = { Text("¿Qué hacemos con los ejercicios?", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Puedes conservarlos sin grupo o borrar también todo su contenido.")
                    OutlinedButton(
                        onClick = {
                            onRemove(true)
                            showDeleteModeConfirm = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Conservar ejercicios")
                    }
                    FilledTonalButton(
                        onClick = {
                            onRemove(false)
                            showDeleteModeConfirm = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Borrar grupo y ejercicios")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeleteModeConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEditorCard(
    exercise: Exercise,
    exerciseInfo: ExerciseMuscleInfo?,
    accentHex: String?,
    partId: String,
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    dragOffset: Offset,
    isDropTarget: Boolean,
    isPartDropTarget: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onRemoveMobility: (String) -> Unit,
    onOpenQuickActions: () -> Unit,
    relationshipAnchorName: String?,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onUpdateRelationshipType: (ExerciseRelationshipType?) -> Unit,
    onUpdateRelationshipNotes: (String?) -> Unit,
    autoExpand: Boolean,
    onAutoExpandHandled: () -> Unit,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showCustomUnitModal by remember { mutableStateOf(false) }
    var showSmartLoadSheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }

    val resolved1RM = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM) {
        resolveReferenceCapacity(exercise)
    }
    var rmInputMode by remember(exercise.id, exercise.prFor1RM) {
        mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct")
    }
    var restSelectionSeconds by rememberSaveable(exercise.id) { mutableStateOf(exercise.restTime ?: 90) }
    var directRmInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.reference1RM)) }
    var prWeightInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prRepsInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()) }
    var customUnitInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.customUnit.orEmpty()) }
    var goalRmInput by rememberSaveable(exercise.id) { mutableStateOf<String>(formatEditableNumber(exercise.goal1RM)) }
    val localPrEstimatedRm = remember(prWeightInput, prRepsInput) {
        val weight = prWeightInput.safeDoubleOrNull()
        val reps = prRepsInput.safeIntOrNull()
        if (weight != null && weight > 0 && reps != null && reps > 0) {
            when (exercise.trainingMode) {
                TrainingMode.REPS,
                TrainingMode.RM,
                -> calculateHybrid1RM(weight, reps)
                TrainingMode.TIME,
                TrainingMode.DISTANCE,
                TrainingMode.CUSTOM,
                -> calculateGeneralizedCapacity(weight, reps.toDouble())
                TrainingMode.SOLO_RPE -> null
                TrainingMode.AMRAP -> null
            }
        } else null
    }
    val accentColor = remember(accentHex) {
        runCatching { Color(AndroidColor.parseColor(accentHex ?: PART_COLORS.first())) }.getOrDefault(Color(0xFF00F0FF))
    }
    val predictedWeights = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to calculateSuggestedLoad(exercise, set)
        }
    }
    val predictedMetrics = remember(exercise.trainingMode, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to calculateEstimatedMetric(exercise, set)
        }
    }

    LaunchedEffect(exercise.id, exercise.restTime) { restSelectionSeconds = exercise.restTime ?: 90 }
    LaunchedEffect(exercise.id, exercise.reference1RM) {
        directRmInput = formatEditableNumber(exercise.reference1RM)
    }
    LaunchedEffect(exercise.id, exercise.prFor1RM) {
        prWeightInput = formatEditableNumber(exercise.prFor1RM?.weight)
        prRepsInput = exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()
        rmInputMode = if (exercise.prFor1RM != null) "pr" else rmInputMode
    }
    LaunchedEffect(exercise.id, exercise.goal1RM) {
        goalRmInput = formatEditableNumber(exercise.goal1RM)
    }
    LaunchedEffect(autoExpand) {
        if (autoExpand) { expanded = true; onAutoExpandHandled() }
    }

    val isSupersetExercise = exercise.supersetId != null
    val supersetShape = RoundedCornerShape(14.dp)
    val containerHighlight = when {
        isDragging -> accentColor.copy(alpha = 0.10f)
        isDropTarget -> accentColor.copy(alpha = 0.08f)
        isPartDropTarget -> accentColor.copy(alpha = 0.06f)
        isSupersetExercise -> accentColor.copy(alpha = if (expanded) 0.08f else 0.05f)
        else -> Color.Transparent
    }
    val containerModifier = when {
        isSupersetExercise -> Modifier
            .clip(supersetShape)
            .background(containerHighlight)
            .border(1.dp, accentColor.copy(alpha = if (expanded) 0.34f else 0.20f), supersetShape)

        containerHighlight.alpha > 0f -> Modifier.background(containerHighlight)
        else -> Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
                alpha = if (isDragging) 0.94f else 1f
                shadowElevation = if (isDragging) 22.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 12f else 0f)
            .then(containerModifier),
    ) {
        // Top accent line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accentColor.copy(alpha = if (expanded) 0.85f else 0.30f)),
        )

        // Header row — always visible, tap to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Drag handle — exclusive drag zone, larger touch target
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(exercise.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragCancel = { onDragEnd() },
                            onDragEnd = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(Offset(dragAmount.x, dragAmount.y))
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Mantén pulsado para reordenar ejercicio",
                    tint = accentColor.copy(alpha = if (isDragging) 0.9f else 0.48f),
                    modifier = Modifier.size(18.dp),
                )
            }
            // Name & subtitle — click to expand, long-press for quick actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = onOpenQuickActions,
                    ),
            ) {
                Text(
                    text = exercise.name.ifBlank { "Seleccionar ejercicio" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("${exercise.sets.size} series · ${formatRestSummary(exercise.restTime)} · ${trainingModeLabel(exercise.trainingMode)}")
                        if (exercise.supersetId != null) append(" · Superserie")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Plegar" else "Desplegar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }

        // Inline expanded editor
        AnimatedVisibility(expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Info chips
                if (exerciseInfo != null) {
                    val infoText = listOfNotNull(exerciseInfo.category, exerciseInfo.type, exerciseInfo.equipment).joinToString(" · ")
                    if (infoText.isNotBlank()) {
                        Text(infoText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (exercise.mobilitySeries.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Movilidad asociada", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        exercise.mobilitySeries.forEach { mobility ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mobility.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOfNotNull(
                                                "${mobility.sets} serie${if (mobility.sets == 1) "" else "s"}",
                                                mobility.reps?.let { "$it reps" },
                                                mobility.durationSeconds?.let { "${it}s" },
                                                mobility.notes,
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveMobility(mobility.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Quitar movilidad", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Compact rest + mode + goal tracking
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Rest picker with timer icon only
                    CompactRestPickerButton(
                        totalSeconds = restSelectionSeconds,
                        accentColor = accentColor,
                        modifier = Modifier,
                    ) { totalSeconds ->
                        restSelectionSeconds = totalSeconds
                        onUpdateExercise { draft -> draft.copy(restTime = totalSeconds) }
                    }
                    
                    // Mode selector (compact, no label)
                    CompactModeSelector(
                        currentMode = exercise.trainingMode,
                        accentColor = accentColor,
                    ) { mode ->
                        onUpdateExercise { current -> current.copy(trainingMode = mode) }
                    }
                    
                    // Goal tracking star button
                    CompactGoalTrackingButton(
                        isActive = exercise.isStarTarget,
                        accentColor = accentColor,
                    ) {
                        onUpdateExercise { ex -> ex.copy(isStarTarget = !ex.isStarTarget) }
                    }

                    UnilateralModeSelector(
                        mode = exercise.unilateralMode,
                        accentColor = accentColor,
                        onModeChanged = { newMode ->
                            onUpdateExercise { ex ->
                                ex.copy(
                                    unilateralMode = newMode,
                                    isUnilateral = newMode != UnilateralMode.BILATERAL,
                                )
                            }
                        },
                    )
                }

                if (exercise.trainingMode == TrainingMode.CUSTOM) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCustomUnitModal = true },
                        color = accentColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Unidad personalizada",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                            )
                            Text(
                                customUnitInput.ifBlank { "Presiona para configurar" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (customUnitInput.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (customUnitInput.isBlank()) FontWeight.Normal else FontWeight.Bold,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                        FilledTonalButton(
                            onClick = { showSmartLoadSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Carga inteligente", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FilledTonalButton(
                        onClick = { showGoalSheet = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Meta / PR", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (showSmartLoadSheet) {
                    AlertDialog(
                        onDismissRequest = { showSmartLoadSheet = false },
                        title = { Text("Carga inteligente", fontWeight = FontWeight.Black) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "Configura la referencia que alimenta las sugerencias de carga y %RM.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (exercise.trainingMode == TrainingMode.REPS || exercise.trainingMode == TrainingMode.RM) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ToggleToken("RM directo", rmInputMode == "direct") { rmInputMode = "direct" }
                                        ToggleToken("Desde PR", rmInputMode == "pr") { rmInputMode = "pr" }
                                    }
                                    if (rmInputMode == "direct") {
                                        EditorMiniField(
                                            label = "RM referencial",
                                            value = directRmInput,
                                            keyboardType = KeyboardType.Decimal,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            directRmInput = it
                                            val parsed = it.safeDoubleOrNull()?.takeIf { value -> value > 0 }
                                            onUpdateExercise { current -> current.copy(reference1RM = parsed) }
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            EditorMiniField(
                                                label = "PR kg",
                                                value = prWeightInput,
                                                keyboardType = KeyboardType.Decimal,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                prWeightInput = it
                                                val weight = it.safeDoubleOrNull()
                                                val reps = prRepsInput.safeIntOrNull()
                                                onUpdateExercise { current ->
                                                    if (weight != null && weight > 0 && reps != null && reps > 0) {
                                                        current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                                    } else {
                                                        current.copy(prFor1RM = null, reference1RM = null)
                                                    }
                                                }
                                            }
                                            EditorMiniField(
                                                label = "PR reps",
                                                value = prRepsInput,
                                                keyboardType = KeyboardType.Number,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                prRepsInput = it
                                                val weight = prWeightInput.safeDoubleOrNull()
                                                val reps = it.safeIntOrNull()
                                                onUpdateExercise { current ->
                                                    if (weight != null && weight > 0 && reps != null && reps > 0) {
                                                        current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                                    } else {
                                                        current.copy(prFor1RM = null, reference1RM = null)
                                                    }
                                                }
                                            }
                                        }
                                        localPrEstimatedRm?.let { estimate ->
                                            Text(
                                                "RM calculado: ${formatEditableNumber(estimate)} kg",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                } else if (exercise.trainingMode == TrainingMode.TIME || exercise.trainingMode == TrainingMode.DISTANCE || exercise.trainingMode == TrainingMode.CUSTOM) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        EditorMiniField(
                                            label = "Carga base",
                                            value = prWeightInput,
                                            keyboardType = KeyboardType.Decimal,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            prWeightInput = it
                                            val weight = it.safeDoubleOrNull()
                                            val metric = prRepsInput.safeIntOrNull()
                                            if (weight != null && weight > 0 && metric != null && metric > 0) {
                                                onUpdateExercise { current ->
                                                    current.copy(prFor1RM = PrReference(weight, metric), reference1RM = calculateGeneralizedCapacity(weight, metric.toDouble()))
                                                }
                                            }
                                        }
                                        EditorMiniField(
                                            label = smartReferenceMetricLabel(exercise.trainingMode, customUnitInput),
                                            value = prRepsInput,
                                            keyboardType = KeyboardType.Number,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            prRepsInput = it
                                            val weight = prWeightInput.safeDoubleOrNull()
                                            val metric = it.safeIntOrNull()
                                            if (weight != null && weight > 0 && metric != null && metric > 0) {
                                                onUpdateExercise { current ->
                                                    current.copy(prFor1RM = PrReference(weight, metric), reference1RM = calculateGeneralizedCapacity(weight, metric.toDouble()))
                                                }
                                            }
                                        }
                                    }
                                }
                                val needsRmReference = exercise.sets.any { it.targetPercentageRM != null } && resolved1RM == null
                                if (needsRmReference) {
                                    Text(
                                        "Falta referencia para %RM. Agrega RM directo o PR para autocompletar cargas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSmartLoadSheet = false }) { Text("Listo") }
                        },
                    )
                }

                if (showGoalSheet) {
                    AlertDialog(
                        onDismissRequest = { showGoalSheet = false },
                        title = { Text("Meta / PR", fontWeight = FontWeight.Black) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Marcar como objetivo", fontWeight = FontWeight.SemiBold)
                                        Text("Activa seguimiento destacado para este ejercicio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = exercise.isStarTarget,
                                        onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                                    )
                                }
                                EditorMiniField(
                                    label = "Meta 1RM kg (opcional)",
                                    value = goalRmInput,
                                    keyboardType = KeyboardType.Decimal,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { input ->
                                    goalRmInput = input
                                    onUpdateExercise { ex -> ex.copy(goal1RM = input.safeDoubleOrNull()) }
                                }
                                Text(
                                    buildString {
                                        val prText = exercise.prFor1RM?.let { "PR: ${formatEditableNumber(it.weight)} kg × ${it.reps}" }
                                        val goalText = exercise.goal1RM?.let { "Meta: ${formatEditableNumber(it)} kg" }
                                        append(listOfNotNull(prText, goalText).ifEmpty { listOf("Sin PR/meta configurada") }.joinToString(" · "))
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showGoalSheet = false }) { Text("Listo") }
                        },
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Relacion con otro ejercicio",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(
                        onClick = onOpenRelationshipPicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            relationshipAnchorName?.let { "Ancla: $it" } ?: "Vincular a otro ejercicio",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (exercise.relativeToCanonicalExerciseId != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = exercise.relationshipType?.displayLabel()?.let { "$it de ${relationshipAnchorName ?: exercise.relativeToCanonicalExerciseId}" }
                                    ?: "Vinculado a ${relationshipAnchorName ?: exercise.relativeToCanonicalExerciseId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearRelationship) {
                                Text("Quitar")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExerciseRelationshipType.values().forEach { type ->
                                FilterChip(
                                    selected = exercise.relationshipType == type,
                                    onClick = { onUpdateRelationshipType(type) },
                                    label = { Text(type.displayLabel()) },
                                )
                            }
                        }
                        EditorMiniField(
                            label = "Notas de relacion",
                            value = exercise.relationshipNotes.orEmpty(),
                            stateKey = "relationship-notes-${exercise.id}",
                            modifier = Modifier.fillMaxWidth(),
                        ) { input ->
                            onUpdateRelationshipNotes(input.ifBlank { null })
                        }
                    } else {
                        Text(
                            "Puedes enlazar variantes, asistencias o sobrecargas al ejercicio ancla para ordenar mejor el historial.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Series carousel section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Series",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ExerciseSetsCarousel(
                        exercise = exercise,
                        reference1RM = resolved1RM,
                        trainingMode = exercise.trainingMode,
                        customUnit = exercise.customUnit,
                        predictedMetrics = predictedMetrics,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth(),
                        onAddSet = onAddSet,
                        onUpdateSet = onUpdateSet,
                        onRemoveSet = onRemoveSet,
                        onMoveSet = onMoveSet,
                    )
                }

            }
        }
    }

    // Custom unit modal dialog
    if (showCustomUnitModal) {
        AlertDialog(
            onDismissRequest = { showCustomUnitModal = false },
            title = { Text("Unidad Personalizada", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Define el nombre de la unidad personalizada para este ejercicio (ej: brazadas, pasos, intentos)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = customUnitInput,
                        onValueChange = { customUnitInput = it },
                        label = { Text("Nombre de la unidad") },
                        placeholder = { Text("ej: brazadas") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        onUpdateExercise { current -> current.copy(customUnit = customUnitInput.ifBlank { null }) }
                        showCustomUnitModal = false
                    },
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomUnitModal = false },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineSetRow(
    set: ExerciseSet,
    index: Int,
    reference1RM: Double?,
    predictedWeight: Double?,
    estimatedMetric: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    accentColor: Color,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var showAmrapDialog by remember(set.id) { mutableStateOf(false) }
    var showIntensityMenu by remember(set.id) { mutableStateOf(false) }
    val isNarrowScreen = LocalConfiguration.current.screenWidthDp <= 380
    val isRmMode = trainingMode == TrainingMode.RM
    val isSoloRpeMode = trainingMode == TrainingMode.SOLO_RPE
    val isAmrapMode = set.isAmrap
    val sliderPercent = remember(set.targetPercentageRM, set.targetReps, set.intensityMode, predictedWeight, reference1RM) {
        when {
            isRmMode && set.targetPercentageRM != null -> set.targetPercentageRM
            predictedWeight != null && reference1RM != null && reference1RM > 0 -> ((predictedWeight / reference1RM) * 100.0).coerceIn(40.0, 100.0)
            set.targetReps != null -> estimatePercent1RM(set.targetReps)
            else -> 75.0
        }
    }
    val displayedWeight = predictedWeight
    val metricLabel = when (trainingMode) {
        TrainingMode.RM -> "Reps est."
        TrainingMode.REPS -> if (isAmrapMode) "Reps mín." else "Reps"
        TrainingMode.TIME -> if (isAmrapMode) "Tiempo mín." else "Tiempo"
        TrainingMode.DISTANCE -> if (isAmrapMode) "Dist. mín." else "Dist."
        TrainingMode.CUSTOM -> if (isAmrapMode) "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} mín." else (customUnit?.ifBlank { "Unidad" } ?: "Unidad")
        TrainingMode.SOLO_RPE -> "RPE obj."
        TrainingMode.AMRAP -> "AMRAP"
    }
    val metricValue = when (trainingMode) {
        TrainingMode.RM -> formatEstimatedMetric(estimatedMetric, trainingMode, customUnit)
        TrainingMode.TIME -> set.targetDuration?.toString().orEmpty()
        TrainingMode.SOLO_RPE -> formatEditableNumber(set.targetRPE)
        else -> set.targetReps?.toString().orEmpty()
    }
    val intensityLabel = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> "RPE"
        IntensityMode.RIR -> "RIR"
        IntensityMode.FAILURE -> "Fallo"
        IntensityMode.SOLO_RM -> "Solo RM"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.LOAD -> "Carga"
    }
    val intensityValueLabel = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> "RPE"
        IntensityMode.RIR -> "RIR"
        IntensityMode.FAILURE -> "Fallo"
        IntensityMode.SOLO_RM -> "%RM"
        IntensityMode.AMRAP -> "AMRAP"
        IntensityMode.LOAD -> "Carga"
    }
    val intensityValue = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> formatEditableNumber(set.targetRPE)
        IntensityMode.RIR -> set.targetRIR?.toString().orEmpty()
        IntensityMode.FAILURE -> "Auto"
        IntensityMode.SOLO_RM -> formatEditableNumber(set.targetPercentageRM ?: sliderPercent)
        IntensityMode.AMRAP -> ""
        IntensityMode.LOAD -> formatEditableNumber(set.weight)
    }
    val setSurface = lerp(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), accentColor, 0.14f)
    val estimatedSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val estimatedText = MaterialTheme.colorScheme.onSurface
    val estimatedSubtle = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isNarrowScreen) 16.dp else 18.dp),
        color = setSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = if (isNarrowScreen) 0.20f else 0.22f)),
    ) {
        Column(
            Modifier.padding(if (isNarrowScreen) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 8.dp else 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                    Text(
                        text = "S${index + 1}",
                        modifier = Modifier.padding(horizontal = if (isNarrowScreen) 6.dp else 8.dp, vertical = if (isNarrowScreen) 2.dp else 2.dp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(if (isNarrowScreen) 24.dp else 28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(if (isNarrowScreen) 14.dp else 15.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (isRmMode) {
                    EditorMiniField(
                        label = "%RM",
                        value = formatEditableNumber(set.targetPercentageRM ?: sliderPercent),
                        stateKey = "percent-${set.id}",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    ) { input ->
                        onUpdate { current ->
                            current.copy(
                                targetPercentageRM = input.safeDoubleOrNull(),
                                intensityMode = IntensityMode.LOAD,
                                targetRPE = null,
                                targetRIR = null,
                                isFailure = false,
                            )
                        }
                    }
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}",
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    ) {}
                } else if (isSoloRpeMode) {
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) { input ->
                        onUpdate { current ->
                            current.copy(
                                targetRPE = input.safeDoubleOrNull(),
                                intensityMode = IntensityMode.RPE,
                                targetRIR = null,
                                targetPercentageRM = null,
                                targetReps = null,
                                targetDuration = null,
                                isFailure = false,
                                isAmrap = false,
                            )
                        }
                    }
                } else {
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(if (isAmrapMode) if (isNarrowScreen) 1.2f else 1.35f else 1f),
                    ) { input ->
                        onUpdate { current ->
                            when (trainingMode) {
                                TrainingMode.TIME -> current.copy(targetDuration = input.safeIntOrNull())
                                else -> current.copy(targetReps = input.safeIntOrNull())
                            }
                        }
                    }
                }
                if (!isAmrapMode && !isRmMode && !isSoloRpeMode) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 4.dp else 6.dp)) {
                        Text(
                            if (isNarrowScreen) "Intens." else "Intensidad",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Box {
                            OutlinedButton(
                                onClick = { showIntensityMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(intensityLabel, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(if (isNarrowScreen) 4.dp else 6.dp))
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                            DropdownMenu(expanded = showIntensityMenu, onDismissRequest = { showIntensityMenu = false }) {
                                listOfNotNull(
                                    IntensityMode.RPE to "RPE",
                                    IntensityMode.RIR to "RIR",
                                    IntensityMode.FAILURE to "Fallo",
                                ).forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            showIntensityMenu = false
                                            onUpdate {
                                                when (mode) {
                                                    IntensityMode.RPE -> it.copy(intensityMode = IntensityMode.RPE, isFailure = false, targetRPE = it.targetRPE ?: 8.0, targetRIR = null)
                                                    IntensityMode.RIR -> it.copy(intensityMode = IntensityMode.RIR, isFailure = false, targetRIR = it.targetRIR ?: 2, targetRPE = null)
                                                    IntensityMode.FAILURE -> it.copy(intensityMode = IntensityMode.FAILURE, isFailure = true, targetRIR = 0, targetRPE = null)
                                                    else -> it
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.FAILURE) {
                        EditorMiniField(
                            label = intensityValueLabel,
                            value = intensityValue,
                            stateKey = "intensity-${set.id}",
                            enabled = false,
                            modifier = Modifier.weight(if (isNarrowScreen) 0.82f else 0.9f),
                        ) {}
                    } else {
                        EditorMiniField(
                            label = intensityValueLabel,
                            value = intensityValue,
                            stateKey = "intensity-${set.id}",
                            keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number,
                            modifier = Modifier.weight(if (isNarrowScreen) 0.82f else 0.9f),
                        ) { input ->
                            onUpdate { current ->
                                when (current.intensityMode ?: IntensityMode.RPE) {
                                    IntensityMode.RPE -> current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                                    IntensityMode.RIR -> current.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                                    IntensityMode.LOAD -> current.copy(weight = input.safeDoubleOrNull(), intensityMode = IntensityMode.LOAD)
                                    else -> current
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(if (isNarrowScreen) 14.dp else 18.dp),
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = if (set.isCalibrator) "AMRAP calibrador" else "AMRAP",
                            modifier = Modifier.padding(horizontal = if (isNarrowScreen) 10.dp else 12.dp, vertical = if (isNarrowScreen) 12.dp else 16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
            }

            if (!isSoloRpeMode) {
                Surface(
                    shape = RoundedCornerShape(if (isNarrowScreen) 14.dp else 16.dp),
                    color = estimatedSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(if (isNarrowScreen) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
                    ) {
                        Text(
                            text = buildString {
                                append(displayedWeight?.let { "${"%.1f".format(it)} kg" } ?: "—")
                                if (isRmMode && reference1RM != null) {
                                    append(" · ${sliderPercent.toInt()}% RM")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = estimatedText,
                        )
                        if (isRmMode) {
                            Slider(
                                value = sliderPercent.toFloat(),
                                onValueChange = { onUpdate { current -> current.copy(targetPercentageRM = it.toDouble(), intensityMode = IntensityMode.LOAD) } },
                                valueRange = 45f..100f,
                                enabled = reference1RM != null,
                            )
                        }
                    }
                }
            }

            // AMRAP ahora es un TrainingMode gestionado desde el selector de modo
        }
    }
}

private fun SessionPart.isUncategorized(): Boolean =
    name.trim().lowercase() in setOf("sin categoría", "sin categoria", "sin grupo")

private fun resolveRelationshipAnchorName(
    session: Session,
    exercise: Exercise,
): String? {
    val anchorId = exercise.relativeToCanonicalExerciseId ?: return null
    return session.allExercises()
        .firstOrNull { candidate ->
            candidate.id != exercise.id && candidate.resolvedCanonicalExerciseId() == anchorId
        }
        ?.name
        ?: anchorId
}

@Composable
private fun EditorMiniField(
    label: String,
    value: String,
    stateKey: String = label,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var localValue by rememberSaveable(stateKey) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(stateKey, value, isFocused) {
        if (!isFocused && value != localValue) {
            localValue = value
        }
    }
    OutlinedTextField(
        value = localValue,
        onValueChange = {
            localValue = it
            onCommit(it)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        },
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun DurationPickerField(
    label: String,
    totalSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = formatRestSummary(totalSeconds),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = accentColor) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = accentColor.copy(alpha = 0.5f),
                focusedLabelColor = accentColor,
                cursorColor = accentColor,
            ),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        DurationPickerDialog(
            initialTotalSeconds = totalSeconds,
            accentColor = accentColor,
            onDismiss = { showPicker = false },
            onConfirm = {
                onConfirm(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun DurationPickerDialog(
    initialTotalSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by rememberSaveable(initialTotalSeconds) { mutableStateOf((initialTotalSeconds / 60).coerceIn(0, 59)) }
    var seconds by rememberSaveable(initialTotalSeconds) { mutableStateOf((initialTotalSeconds % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir descanso", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Ajusta el descanso con un selector visual nativo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NativeWheelPicker(
                        label = "Min",
                        value = minutes,
                        range = 0..59,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { minutes = it }
                    NativeWheelPicker(
                        label = "Seg",
                        value = seconds,
                        range = 0..59,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { seconds = it }
                }
                Text(
                    "Descanso seleccionado: ${minutes}:${seconds.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(minutes * 60 + seconds) }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun NativeWheelPicker(
    label: String,
    value: Int,
    range: IntRange,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accentColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = range.first
                        maxValue = range.last
                        wrapSelectorWheel = true
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFormatter { it.toString().padStart(2, '0') }
                        setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
                    }
                },
                update = { picker ->
                    if (picker.minValue != range.first) picker.minValue = range.first
                    if (picker.maxValue != range.last) picker.maxValue = range.last
                    if (picker.value != value) picker.value = value
                },
            )
        }
    }
}

@Composable
private fun ToggleToken(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    )
}

@Composable
private fun ExerciseFactChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Text(
            text = "$label · $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = accentColor?.let { lerp(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), it, 0.12f) }
            ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                content()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionEditorSheets(
    uiState: SessionEditorUiState,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onSelectGradient: (String) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundBrightnessChange: (Float) -> Unit,
    onCoverBrightnessChange: (Float) -> Unit,
    onCoverContrastChange: (Float) -> Unit,
    onCoverSaturationChange: (Float) -> Unit,
    onCoverGrayscaleChange: (Float) -> Unit,
    onCoverVignetteChange: (Float) -> Unit,
    onCoverMotionChange: (Boolean) -> Unit,
    onLabelPositionChange: (LabelPosition) -> Unit,
    onExerciseSearch: (String) -> Unit,
    onSelectExercise: (ExerciseMuscleInfo) -> Unit,
    onMultiSelectExercises: (List<ExerciseMuscleInfo>) -> List<String>,
    onApplyRules: (String?) -> Unit,
    onCloneCurrentToTargets: (Set<String>, Set<String>?, SessionCloneApplyMode) -> Unit,
    onImportFromSource: (String, Set<String>?, SessionCloneApplyMode) -> Unit,
    onSave: (SessionSaveScope) -> Unit,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (String) -> Unit,
    onDiscardSwitch: (() -> Unit)?,
    onWarmupSave: (String, List<WarmupSetDefinition>) -> Unit,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
    onRuleDefaultsChange: (Int?, Int?, Double?) -> Unit,
    onRuleLimitsChange: (Double?, Int?) -> Unit,
    onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
    onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
    onQuickActionOpenPicker: () -> Unit,
    onQuickActionOpenWarmup: () -> Unit,
    onQuickActionOpenMobility: () -> Unit,
    onAddMobilityExercise: (MobilityExercise) -> Unit,
    onQuickActionDelete: () -> Unit,
    onQuickActionCreateSuperset: () -> Unit,
    onQuickActionManageSuperset: () -> Unit,
    onLinkExerciseRelativeTo: (String?, String, String?) -> Unit,
    onOpenSupersetManager: (String?, String) -> Unit,
    onUpdateSupersetRestBetween: (String?, String, Int) -> Unit,
    onUpdateSupersetRestAfter: (String?, String, Int) -> Unit,
    onRemoveFromSuperset: (String?, String) -> Unit,
    onSupersetDraftUpdate: (SupersetDraft) -> Unit,
    onCreateSupersetGroup: () -> Unit,
    onOpenSupersetCreator: (String?, List<String>) -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenExerciseCreator: () -> Unit,
    allTemplates: List<SessionTemplate>,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
    onTemplateSearchChange: (String) -> Unit,
) {
    val session = uiState.session ?: return
    if (uiState.sheet == SessionEditorSheet.NONE) return

    val warmupExercise = session.allExercises().find { it.id == uiState.warmupExerciseId }
    val quickActionExercise = uiState.quickActionsExerciseId?.let { targetId ->
        session.allExercises().find { it.id == targetId }
    }

     if (uiState.sheet == SessionEditorSheet.EXERCISE_PICKER) {
         var pendingPickerSelection by remember { mutableStateOf<List<ExerciseMuscleInfo>>(emptyList()) }
         var showPickerExitConfirm by remember { mutableStateOf(false) }
         val requestPickerDismiss = {
             if (pendingPickerSelection.isNotEmpty()) {
                 showPickerExitConfirm = true
             } else {
                 onDismiss()
             }
         }
         val sheetState = rememberModalBottomSheetState(
             skipPartiallyExpanded = true,
             confirmValueChange = { target ->
                 when (target) {
                     SheetValue.Hidden -> {
                         if (pendingPickerSelection.isNotEmpty()) {
                             showPickerExitConfirm = true
                             false
                         } else {
                             true
                         }
                     }
                     SheetValue.PartiallyExpanded -> false
                     SheetValue.Expanded -> true
                 }
             },
         )
         ModalBottomSheet(
              onDismissRequest = requestPickerDismiss,
              sheetState = sheetState,
              modifier = Modifier.fillMaxHeight(),
              scrimColor = Color.Black.copy(alpha = 0.32f),
              dragHandle = null,
          ) {
              Column(
                  modifier = Modifier
                      .fillMaxWidth()
                      .fillMaxHeight()
                      .navigationBarsPadding(),
              ) {
                 // Drag handle indicator
                 Box(
                     modifier = Modifier
                         .align(Alignment.CenterHorizontally)
                         .padding(top = 8.dp, bottom = 4.dp)
                         .width(32.dp)
                         .height(4.dp)
                         .clip(RoundedCornerShape(2.dp))
                         .background(MaterialTheme.colorScheme.outlineVariant),
                 )
                 
                  ExercisePickerSheet(
                      query = uiState.searchQuery,
                      catalog = EXERCISE_DATABASE,
                      workoutLogs = uiState.workoutLogs,
                      editingExisting = uiState.pickerTargetExerciseId != null,
                       onSearch = onExerciseSearch,
                       onSelect = onSelectExercise,
                       onMultiSelect = onMultiSelectExercises,
                       onCreateSuperset = { infos ->
                           val exerciseIds = onMultiSelectExercises(infos)
                           if (exerciseIds.size >= 2) {
                               onOpenSupersetCreator(uiState.pickerTargetPartId, exerciseIds)
                           }
                       },
                       onOpenExerciseDetail = { id ->
                           onDismiss()
                           onOpenExerciseDetail(id)
                      },
                      onOpenExerciseCreator = onOpenExerciseCreator,
                      onDismiss = requestPickerDismiss,
                      onSelectionChange = { pendingPickerSelection = it },
                  )
             }
         }
         if (showPickerExitConfirm) {
             AlertDialog(
                 onDismissRequest = { showPickerExitConfirm = false },
                 title = { Text("Ejercicios seleccionados") },
                 text = {
                     Column {
                         Text("Seleccionaste ${pendingPickerSelection.size} ejercicios.")
                         if (pendingPickerSelection.size >= 2) {
                             Spacer(Modifier.height(8.dp))
                             Text(
                                 "Puedes agregarlos por separado o agruparlos en una superserie.",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                             )
                         }
                     }
                 },
                 confirmButton = {
                     Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                         if (pendingPickerSelection.size >= 2) {
                             Button(
                                 onClick = {
                                      val exerciseIds = onMultiSelectExercises(pendingPickerSelection)
                                      if (exerciseIds.isNotEmpty()) {
                                          onOpenSupersetCreator(uiState.pickerTargetPartId, exerciseIds)
                                      }
                                      pendingPickerSelection = emptyList()
                                      showPickerExitConfirm = false
                                  },
                                 modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                             ) { Text("Agrupar como superserie", fontWeight = FontWeight.Black) }
                         }
                         TextButton(
                             onClick = {
                                 onMultiSelectExercises(pendingPickerSelection)
                                 pendingPickerSelection = emptyList()
                                 showPickerExitConfirm = false
                                 onDismiss()
                             },
                             modifier = Modifier.fillMaxWidth(),
                         ) { Text("Agregar por separado") }
                     }
                 },
                 dismissButton = {
                     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         TextButton(
                             onClick = {
                                 pendingPickerSelection = emptyList()
                                 showPickerExitConfirm = false
                                 onDismiss()
                             },
                         ) { Text("Descartar") }
                         TextButton(onClick = { showPickerExitConfirm = false }) {
                             Text("Cancelar")
                         }
                     }
                 },
             )
         }
         return
     }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        when (uiState.sheet) {
            SessionEditorSheet.EXERCISE_PICKER -> Unit
            SessionEditorSheet.BACKGROUND -> BackgroundSheet(
                session = session,
                onPickImage = onPickImage,
                onSelectGradient = onSelectGradient,
                onBackgroundBlurChange = onBackgroundBlurChange,
                onBackgroundBrightnessChange = onBackgroundBrightnessChange,
                onCoverBrightnessChange = onCoverBrightnessChange,
                onCoverContrastChange = onCoverContrastChange,
                onCoverSaturationChange = onCoverSaturationChange,
                onCoverGrayscaleChange = onCoverGrayscaleChange,
                onCoverVignetteChange = onCoverVignetteChange,
                onCoverMotionChange = onCoverMotionChange,
                onLabelPositionChange = onLabelPositionChange,
            )
            SessionEditorSheet.HISTORY -> HistorySheet(uiState, onRestoreSnapshot)
            SessionEditorSheet.RULES -> RulesSheet(
                uiState = uiState,
                onApplyRules = onApplyRules,
                onRuleDefaultsChange = onRuleDefaultsChange,
                onRuleLimitsChange = onRuleLimitsChange,
                onAdvancedRuleLimitsChange = onAdvancedRuleLimitsChange,
                onApplyGlobalIntensityAdjustment = onApplyGlobalIntensityAdjustment,
            )
            SessionEditorSheet.TRANSFER -> SessionClonerSheet(
                uiState = uiState,
                onCloneCurrentToTargets = onCloneCurrentToTargets,
                onImportFromSource = onImportFromSource,
            )
            SessionEditorSheet.SAVE -> SaveSheet(
                onSave = onSave,
                onDiscardSwitch = onDiscardSwitch,
                isSimpleProgram = uiState.isSimpleProgram,
            )
            SessionEditorSheet.AUGE -> AssistantSheet(
                uiState = uiState,
                onApplyAugeCorrection = onApplyAugeCorrection,
                onAddGhostExercise = onAddGhostExercise,
                onApplyAssistantSuggestion = onApplyAssistantSuggestion,
            )
            SessionEditorSheet.WARMUP -> WarmupSheet(exercise = warmupExercise, onSave = onWarmupSave)
            SessionEditorSheet.MOBILITY_PICKER -> MobilityPickerSheet(
                onAdd = onAddMobilityExercise,
                onDismiss = onDismiss,
            )
            SessionEditorSheet.SUPERSERIE_MANAGER -> {
                val supersetExercises = session.allExercises().filter { it.supersetId == uiState.supersetManagerSupersetId }
                SupersetManagerSheet(
                    exercises = supersetExercises,
                    partId = uiState.supersetManagerPartId,
                    supersetId = uiState.supersetManagerSupersetId ?: "",
                    onUpdateRestBetween = onUpdateSupersetRestBetween,
                    onUpdateRestAfter = onUpdateSupersetRestAfter,
                    onRemove = onRemoveFromSuperset,
                    onDismiss = onDismiss,
                )
            }
            SessionEditorSheet.SUPERSET_CREATOR -> {
                val draft = uiState.supersetDraft ?: return@ModalBottomSheet
                val scopedExercises = draft.partId?.let { partId ->
                    session.parts.firstOrNull { it.id == partId }?.exercises
                } ?: session.exercises
                SupersetCreatorSheet(
                    draft = draft,
                    sessionExercises = scopedExercises,
                    onUpdateDraft = onSupersetDraftUpdate,
                    onConfirm = {
                        onCreateSupersetGroup()
                        onDismiss()
                    },
                    onDismiss = onDismiss,
                )
            }
            SessionEditorSheet.RELATIONSHIP_PICKER -> {
                val targetExerciseId = uiState.pickerTargetExerciseId
                val targetExercise = targetExerciseId?.let { targetId -> session.allExercises().find { it.id == targetId } }
                RelationshipPickerSheet(
                    targetExercise = targetExercise,
                    candidates = uiState.allProgramExerciseCandidates.filter { it.exerciseId != targetExerciseId },
                    query = uiState.searchQuery,
                    onSearch = onExerciseSearch,
                    onSelectAnchor = { anchorExerciseId ->
                        val target = targetExercise ?: return@RelationshipPickerSheet
                        onLinkExerciseRelativeTo(uiState.pickerTargetPartId, target.id, anchorExerciseId)
                    },
                    onDismiss = onDismiss,
                )
            }
            SessionEditorSheet.QUICK_ACTIONS -> ExerciseQuickActionsSheet(
                exercise = quickActionExercise,
                catalog = EXERCISE_DATABASE,
                workoutLogs = uiState.workoutLogs,
                onOpenExerciseDetail = { id ->
                    onDismiss()
                    onOpenExerciseDetail(id)
                },
                onOpenPicker = onQuickActionOpenPicker,
                onOpenWarmup = onQuickActionOpenWarmup,
                onOpenMobility = onQuickActionOpenMobility,
                onDelete = onQuickActionDelete,
                onManageSuperset = {
                    if (quickActionExercise?.isInSuperset() == true) {
                        onQuickActionManageSuperset()
                    } else {
                        onQuickActionCreateSuperset()
                    }
                },
            )
            SessionEditorSheet.TEMPLATES -> TemplatesSheet(
                templates = allTemplates,
                searchQuery = uiState.templateSearchQuery,
                applyDecision = uiState.templateApplyDecision,
                onSearchChange = onTemplateSearchChange,
                onSelectTemplate = onSelectTemplate,
                onConfirmApplyTemplate = onConfirmApplyTemplate,
                onCancelApply = onCancelTemplateApply,
                onDismiss = onDismiss,
            )
            SessionEditorSheet.NONE -> Unit
        }
    }
}

@Composable
private fun RelationshipPickerSheet(
    targetExercise: Exercise?,
    candidates: List<ProgramExerciseCandidate>,
    query: String,
    onSearch: (String) -> Unit,
    onSelectAnchor: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (targetExercise == null) {
        Text(
            text = "No encontramos el ejercicio que quieres vincular.",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val currentAnchorId = targetExercise.relativeToCanonicalExerciseId
    val filteredCandidates = remember(candidates, query) {
        candidates
            .filter { candidate ->
                query.isBlank() || candidate.exerciseName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.exerciseName.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Vincular ejercicio", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    targetExercise.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Text(
            "Elige un ejercicio ancla de cualquier sesión del programa para marcar esta variante, asistencia o sobrecarga.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar ejercicio ancla") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )

        if (currentAnchorId != null) {
            OutlinedButton(
                onClick = { onSelectAnchor(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Quitar vinculo actual")
            }
        }

        if (filteredCandidates.isEmpty()) {
            Text(
                "No hay ejercicios candidatos para este vinculo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredCandidates, key = { it.exerciseId }) { candidate ->
                    val selected = candidate.exerciseDbId == currentAnchorId ||
                        candidate.exerciseName.equals(currentAnchorId?.removePrefix("custom:"), ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectAnchor(candidate.exerciseId) },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                        },
                        border = if (selected) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        } else {
                            null
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.exerciseName, fontWeight = FontWeight.Bold)
                                Text(
                                    buildString {
                                        append(candidate.sessionName)
                                        if (candidate.partName != null) append(" · ${candidate.partName}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesSheet(
    templates: List<SessionTemplate>,
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Plantillas de sesión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Buscar plantilla...") },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            val filtered = remember(templates, searchQuery) {
                if (searchQuery.isBlank()) templates
                else templates.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true) ||
                    it.muscleGroupsSummary.contains(searchQuery, ignoreCase = true)
                }
            }
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No hay plantillas disponibles"
                               else "Sin resultados para \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { template ->
                        TemplateCard(template = template, onApply = { onSelectTemplate(template) })
                    }
                }
            }
        }
    }
    if (applyDecision != null) {
        AlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Aplicar plantilla", fontWeight = FontWeight.Black) },
            text = {
                Text("La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla \"${applyDecision.template.name}\"?")
            },
            confirmButton = {
                Button(onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) }) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) }) {
                    Text("Añadir al final")
                }
            },
        )
    }
}

@Composable
private fun TemplateCard(
    template: SessionTemplate,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = template.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (template.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (template.muscleGroupsSummary.isNotBlank() || template.estimatedDurationMinutes != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (template.muscleGroupsSummary.isNotBlank()) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(template.muscleGroupsSummary, fontSize = 11.sp) },
                            )
                        }
                        template.estimatedDurationMinutes?.let {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("~${it}min", fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onApply) {
                Text("Aplicar")
            }
        }
    }
}

@Composable
private fun ExerciseQuickActionsSheet(
    exercise: Exercise?,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenWarmup: () -> Unit,
    onOpenMobility: () -> Unit,
    onDelete: () -> Unit,
    onManageSuperset: () -> Unit,
) {
    if (exercise == null) {
        Text(
            text = "No encontramos el ejercicio seleccionado.",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var showDeleteConfirm by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showInfoDialog by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val catalogLookup = remember(catalog) { buildExerciseCatalogLookup(catalog) }
    val selectedInfo = remember(exercise.id, catalogLookup) {
        resolveCatalogExerciseInfo(exercise, catalogLookup)
    }
    val discomfortByExercise = remember(workoutLogs) {
        buildDiscomfortByExercise(workoutLogs)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Acciones rápidas", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            exercise.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedInfo != null) {
            OutlinedButton(onClick = { showInfoDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver información")
            }
        }

        OutlinedButton(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cambiar ejercicio")
        }
        OutlinedButton(onClick = onOpenWarmup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Series de aproximación")
        }
        OutlinedButton(onClick = onOpenMobility, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Agregar series de movilidad")
        }
        OutlinedButton(onClick = onManageSuperset, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (exercise.isInSuperset()) "Gestionar superserie" else "Crear superserie")
        }
        Button(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Eliminar")
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar ejercicio", fontWeight = FontWeight.Black) },
            text = { Text("¿Quieres borrar este ejercicio de la sesión?") },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    if (showInfoDialog && selectedInfo != null) {
        ExerciseCatalogInfoDialog(
            exercise = selectedInfo,
            catalog = catalog,
            associatedDiscomforts = discomfortByExercise[selectedInfo.id].orEmpty(),
            onOpenExercise = onOpenExerciseDetail,
            onDismiss = { showInfoDialog = false },
        )
    }

}

@Composable
private fun SupersetCreatorSheet(
    draft: SupersetDraft,
    sessionExercises: List<Exercise>,
    onUpdateDraft: (SupersetDraft) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedExercises = remember(draft.exerciseIds, sessionExercises) {
        draft.exerciseIds.mapNotNull { id -> sessionExercises.find { it.id == id } }
    }
    val availableExercises = remember(sessionExercises, draft.exerciseIds) {
        sessionExercises.filter { exercise ->
            exercise.id in draft.exerciseIds || !exercise.isInSuperset()
        }
    }

    fun toggleExercise(exerciseId: String) {
        val nextIds = if (exerciseId in draft.exerciseIds) {
            draft.exerciseIds.filterNot { it == exerciseId }
        } else {
            draft.exerciseIds + exerciseId
        }
        onUpdateDraft(draft.copy(exerciseIds = nextIds.distinct()))
    }

    fun moveSelectedExercise(exerciseId: String, delta: Int) {
        val currentIndex = draft.exerciseIds.indexOf(exerciseId)
        if (currentIndex < 0) return
        val targetIndex = (currentIndex + delta).coerceIn(0, draft.exerciseIds.lastIndex)
        if (targetIndex == currentIndex) return
        val nextIds = draft.exerciseIds.toMutableList().also { ids ->
            val moved = ids.removeAt(currentIndex)
            ids.add(targetIndex, moved)
        }
        onUpdateDraft(draft.copy(exerciseIds = nextIds))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Crear superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "Elige 2 o más ejercicios del mismo grupo, ordena la secuencia y configura descansos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Seleccionar ejercicios", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        LazyColumn(
            modifier = Modifier.heightIn(max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(availableExercises, key = { it.id }) { exercise ->
                val selected = exercise.id in draft.exerciseIds
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().clickable { toggleExercise(exercise.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { toggleExercise(exercise.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                            if (exercise.id in draft.exerciseIds) {
                                Text(
                                    "Orden ${draft.exerciseIds.indexOf(exercise.id) + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("Orden de la superserie", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        if (selectedExercises.isEmpty()) {
            Text(
                "Selecciona ejercicios arriba para armar la superserie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedExercises.forEachIndexed { index, exercise ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                            Text(exercise.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { moveSelectedExercise(exercise.id, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, null)
                            }
                            IconButton(onClick = { moveSelectedExercise(exercise.id, 1) }, enabled = index < selectedExercises.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                            IconButton(onClick = { toggleExercise(exercise.id) }) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Rest configuration
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            EditorMiniField(
                label = "Descanso entre ejercicios (s)",
                value = draft.restBetweenExercises.toString(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                onCommit = { input ->
                    val value = input.filter { it.isDigit() }.take(4)
                    onUpdateDraft(draft.copy(restBetweenExercises = value.toIntOrNull() ?: 0))
                },
                modifier = Modifier.weight(1f),
            )
            EditorMiniField(
                label = "Descanso post-superserie (s)",
                value = draft.restAfterSuperset.toString(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                onCommit = { input ->
                    val value = input.filter { it.isDigit() }.take(4)
                    onUpdateDraft(draft.copy(restAfterSuperset = value.toIntOrNull() ?: 0))
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Optional rounds
        EditorMiniField(
            label = "Rondas (opcional)",
            value = draft.rounds?.toString().orEmpty(),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            onCommit = { input ->
                val clean = input.filter { it.isDigit() }.take(3)
                onUpdateDraft(draft.copy(rounds = clean.toIntOrNull()))
            },
        )

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancelar")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = draft.exerciseIds.distinct().size >= 2,
            ) {
                Text("Crear superserie", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MobilityPickerSheet(
    onAdd: (MobilityExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRegion by rememberSaveable { mutableStateOf("") }
    val allMobility = remember { MobilityExerciseCatalog.getAllMobilityExercises() }
    val uniqueRegions = remember(allMobility) { allMobility.map { it.bodyRegion }.distinct().sorted() }
    val results = remember(query, selectedRegion, allMobility) {
        val byQuery = if (query.isBlank()) allMobility else {
            val normalized = query.trim().lowercase()
            allMobility.filter { exercise ->
                exercise.name.contains(normalized, ignoreCase = true) ||
                    exercise.description.contains(normalized, ignoreCase = true) ||
                    exercise.bodyRegion.contains(normalized, ignoreCase = true) ||
                    exercise.discomfortIds.any { discomfortLabel(it).contains(normalized, ignoreCase = true) }
            }
        }
        if (selectedRegion.isBlank()) byQuery else byQuery.filter { it.bodyRegion == selectedRegion }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Catálogo de movilidad", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "${allMobility.size} ejercicios correctivos separados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("Buscar movilidad, zona o molestia") },
        )
        // Body region filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedRegion.isBlank(),
                    onClick = { selectedRegion = "" },
                    label = { Text("Todas") },
                )
            }
            items(uniqueRegions) { region ->
                FilterChip(
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = { Text(region.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { mobility ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(mobility.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${mobility.durationSeconds}s · ${mobility.bodyRegion} · ${mobility.discomfortIds.joinToString { discomfortLabel(it) }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                mobility.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalButton(onClick = { onAdd(mobility) }) {
                            Text("Agregar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupersetManagerSheet(
    exercises: List<Exercise>,
    partId: String?,
    supersetId: String,
    onUpdateRestBetween: (String?, String, Int) -> Unit,
    onUpdateRestAfter: (String?, String, Int) -> Unit,
    onRemove: (String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var restBetween by rememberSaveable(supersetId) {
        mutableStateOf(exercises.firstOrNull()?.supersetRestBetween?.toString().orEmpty())
    }
    var restAfter by rememberSaveable(supersetId) {
        mutableStateOf(exercises.firstOrNull()?.supersetRestAfter?.toString().orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gestionar superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "${exercises.size} ejercicios vinculados",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        exercises.forEachIndexed { index, exercise ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                Text(exercise.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(
                    onClick = { onRemove(partId, exercise.id); onDismiss() },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            EditorMiniField(
                label = "Descanso entre ejercicios (s)",
                value = restBetween,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            ) { input ->
                restBetween = input
                input.safeIntOrNull()?.let { onUpdateRestBetween(partId, supersetId, it) }
            }
            EditorMiniField(
                label = "Descanso post-superserie (s)",
                value = restAfter,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            ) { input ->
                restAfter = input
                input.safeIntOrNull()?.let { onUpdateRestAfter(partId, supersetId, it) }
            }
        }
    }
}

@Composable
internal fun ExercisePickerSheet(
    query: String,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    editingExisting: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)? = null,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenExerciseCreator: () -> Unit,
    onDismiss: () -> Unit,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit = {},
) {
    var selectedRegion by rememberSaveable { mutableStateOf<ExerciseCatalogRegion?>(null) }
    var selectedTrait by rememberSaveable { mutableStateOf<ExerciseCatalogTrait?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(ExerciseCatalogSort.RELEVANCE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var infoExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedExercises by remember { mutableStateOf<List<ExerciseMuscleInfo>>(emptyList()) }

    val normalizedQuery = query.trim()
    val activeRegion = selectedRegion ?: ExerciseCatalogRegion.ALL
    val showGroupBrowser = false

    fun handleSelect(info: ExerciseMuscleInfo) {
        if (editingExisting) {
            onSelect(info)
        } else {
            val nextSelection = if (selectedExercises.any { it.id == info.id }) {
                selectedExercises.filterNot { it.id == info.id }
            } else {
                selectedExercises + info
            }
            selectedExercises = nextSelection
            onSelectionChange(nextSelection)
        }
    }
    val results = remember(query, catalog, activeRegion, selectedTrait, sortMode) {
        val baseFiltered = catalog.filter { info ->
            val regionMatch = activeRegion == ExerciseCatalogRegion.ALL || resolveExerciseRegion(info) == activeRegion
            val traitMatch = selectedTrait == null || matchesCatalogTrait(info, selectedTrait!!)
            regionMatch && traitMatch
        }

        val searched = if (normalizedQuery.isBlank()) {
            baseFiltered
        } else {
            baseFiltered
                .map { it to calculateSearchScore(it, normalizedQuery) }
                .filter { it.second > 0 }
                .sortedWith(
                    compareByDescending<Pair<ExerciseMuscleInfo, Int>> { it.second }
                        .thenBy { kotlin.math.abs(it.first.name.length - normalizedQuery.length) }
                        .thenBy { it.first.name }
                )
                .map { it.first }
        }

        when (sortMode) {
            ExerciseCatalogSort.RELEVANCE -> searched
            ExerciseCatalogSort.FATIGUE_HIGH -> searched.sortedByDescending { calculateFriendlyFatigue(it).overall }
            ExerciseCatalogSort.FATIGUE_LOW -> searched.sortedBy { calculateFriendlyFatigue(it).overall }
            ExerciseCatalogSort.NAME -> searched.sortedBy { it.name }
            ExerciseCatalogSort.MUSCLE -> searched.sortedWith(compareBy({ resolvePrimaryMuscleLabel(it) }, { it.name }))
        }
    }

    val infoExercise = remember(infoExerciseId, catalog) { catalog.firstOrNull { it.id == infoExerciseId } }
    val discomfortByExercise = remember(workoutLogs) {
        val map = mutableMapOf<String, MutableMap<String, Int>>()
        workoutLogs.forEach { log ->
            log.postExerciseReports.forEach { report ->
                val key = report.canonicalExerciseId ?: report.exerciseDbId ?: report.exerciseId
                if (key.isBlank()) return@forEach
                val bucket = map.getOrPut(key) { mutableMapOf() }
                report.discomfortIds
                    .filter { it != "none" }
                    .forEach { discomfortId ->
                        val label = discomfortLabel(discomfortId)
                        bucket[label] = (bucket[label] ?: 0) + 1
                    }
            }
        }
        map.mapValues { (_, value) ->
            value.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
        }
    }
    val categorizedCatalog = remember(catalog) {
        catalog
            .filter { !it.category.isNullOrBlank() }
            .groupBy { it.category!!.trim() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }
    val uncategorizedCatalog = remember(catalog) {
        catalog.filter { it.category.isNullOrBlank() }
    }

     Column(
         Modifier
             .fillMaxWidth()
             .fillMaxHeight()
             .padding(horizontal = 14.dp, vertical = 12.dp),
         verticalArrangement = Arrangement.spacedBy(10.dp),
     ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (editingExisting) "Cambiar ejercicio" else "Catálogo",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    "${catalog.size} ejercicios",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onOpenExerciseCreator,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Crear", style = MaterialTheme.typography.labelSmall)
                }
                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sortMode.label, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        ExerciseCatalogSort.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortMode = option
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nombre, músculo o equipo") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )

         LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
             items(ExerciseCatalogRegion.values().toList(), key = { it.name }) { region ->
                 FilterChip(
                     selected = activeRegion == region,
                     onClick = { selectedRegion = if (region == ExerciseCatalogRegion.ALL) null else region },
                     label = { Text(region.label) },
                 )
             }
         }

     if (showGroupBrowser && selectedRegion == null && normalizedQuery.isBlank()) {
         Text(
             "Grupos",
             style = MaterialTheme.typography.labelLarge,
             fontWeight = FontWeight.Bold,
             color = Color.White
         )
         LazyColumn(
             modifier = Modifier.weight(1f),
             verticalArrangement = Arrangement.spacedBy(8.dp),
         ) {
             item {
                 Text(
                     "Explorar por grupo muscular",
                     style = MaterialTheme.typography.labelMedium,
                     color = Color.White.copy(alpha = 0.7f),
                     fontWeight = FontWeight.SemiBold,
                 )
             }
             items(ExerciseCatalogRegion.values(), key = { it.name }) { region ->
                 val count = catalog.count { region == ExerciseCatalogRegion.ALL || resolveExerciseRegion(it) == region }
                 Surface(
                     modifier = Modifier
                         .fillMaxWidth()
                         .clickable { selectedRegion = region }
                         .padding(horizontal = 2.dp),
                     shape = RoundedCornerShape(14.dp),
                     color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                 ) {
                     Row(
                         modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically,
                     ) {
                         Column(modifier = Modifier.weight(1f)) {
                             Text(region.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                             Text("$count ejercicios", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                         }
                         Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                     }
                 }
             }

             if (categorizedCatalog.isNotEmpty()) {
                 item {
                     Spacer(Modifier.height(8.dp))
                     Text(
                         "Por grupo muscular",
                         style = MaterialTheme.typography.labelMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         fontWeight = FontWeight.SemiBold,
                     )
                 }
                 items(categorizedCatalog, key = { it.first }) { (category, exercisesInCategory) ->
                     Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                         Text(
                             category,
                             style = MaterialTheme.typography.titleSmall,
                             fontWeight = FontWeight.Black,
                             color = Color.White
                         )
                          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                              items(exercisesInCategory, key = { it.id }) { info ->
                                  ExercisePickerCompactCard(
                                      info = info,
                                      isSelected = selectedExercises.any { it.id == info.id },
                                      onSelect = { handleSelect(info) },
                                      onInfo = { infoExerciseId = info.id },
                                  )
                              }
                          }
                     }
                 }
             }

             if (uncategorizedCatalog.isNotEmpty()) {
                 item {
                     Spacer(Modifier.height(8.dp))
                     Text(
                         "Sin grupo",
                         style = MaterialTheme.typography.titleSmall,
                         fontWeight = FontWeight.Black,
                         color = Color.White
                     )
                 }
                  items(uncategorizedCatalog, key = { it.id }) { info ->
                      ExercisePickerDetailedCard(
                          info = info,
                          isSelected = selectedExercises.any { it.id == info.id },
                          onSelect = { handleSelect(info) },
                          onInfo = { infoExerciseId = info.id },
                      )
                  }
             }
         }
     } else {
         if (selectedRegion != null) {
             Text(activeRegion.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
         }

         LazyColumn(
             modifier = Modifier.weight(1f),
             verticalArrangement = Arrangement.spacedBy(10.dp),
         ) {
             item {
                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                     Text(
                         "Filtros rápidos",
                         style = MaterialTheme.typography.labelLarge,
                         fontWeight = FontWeight.Bold,
                         color = Color.White
                     )
                     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                         ExerciseCatalogTrait.values().forEach { trait ->
                             FilterChip(
                                 selected = selectedTrait == trait,
                                 onClick = { selectedTrait = if (selectedTrait == trait) null else trait },
                                 label = { Text(trait.label) },
                             )
                         }
                     }
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalAlignment = Alignment.CenterVertically,
                     ) {
                         Text(
                             if (results.isEmpty()) "Sin resultados" else "${results.size} resultados",
                             style = MaterialTheme.typography.labelMedium,
                             fontWeight = FontWeight.Bold,
                             color = Color.White
                         )
                         if (selectedTrait != null || activeRegion != ExerciseCatalogRegion.ALL || normalizedQuery.isNotBlank()) {
                             TextButton(
                                 onClick = {
                                     selectedRegion = null
                                     selectedTrait = null
                                     onSearch("")
                                     sortMode = ExerciseCatalogSort.RELEVANCE
                                 }
                             ) { Text("Limpiar") }
                         }
                     }
                 }
             }
              items(results, key = { it.id }) { info ->
                  ExercisePickerDetailedCard(
                      info = info,
                      isSelected = selectedExercises.any { it.id == info.id },
                      onSelect = { handleSelect(info) },
                      onInfo = { infoExerciseId = info.id },
                  )
              }
          }

        if (!editingExisting && selectedExercises.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${selectedExercises.size} seleccionados", color = Color.White, modifier = Modifier.weight(1f))
                    if (selectedExercises.size >= 2 && onCreateSuperset != null) {
                        Button(onClick = {
                            onCreateSuperset(selectedExercises)
                            selectedExercises = emptyList()
                            onSelectionChange(emptyList())
                        }) {
                            Text("Crear superserie")
                        }
                    }
                    FilledTonalButton(onClick = {
                        onMultiSelect(selectedExercises)
                        selectedExercises = emptyList()
                        onSelectionChange(emptyList())
                    }) {
                        Text("Agregar ${selectedExercises.size}")
                    }
                }
            }
        }
        }
    }

    infoExercise?.let { selected ->
        ExerciseCatalogInfoDialog(
            exercise = selected,
            catalog = catalog,
            associatedDiscomforts = discomfortByExercise[selected.id].orEmpty(),
            onOpenExercise = onOpenExerciseDetail,
            onDismiss = { infoExerciseId = null },
        )
    }
}

private fun buildExerciseUtilityBullets(exercise: ExerciseMuscleInfo): List<String> {
    val bullets = mutableListOf<String>()
    val region = resolveExerciseRegion(exercise)
    val type = exercise.type?.lowercase().orEmpty()
    val fatigue = calculateFriendlyFatigue(exercise).overall

    if (exercise.functionalTransfer?.isNotBlank() == true) {
        bullets += exercise.functionalTransfer
    }

    if (type.contains("básico") || type.contains("basico") || exercise.tier?.equals("T1", true) == true) {
        bullets += "Muy útil para mejorar básicos del programa y capacidad de producir fuerza."
    }

    if (type.contains("aislamiento") || type.contains("accesorio")) {
        bullets += "Buena opción para reforzar puntos débiles con fatiga sistémica controlada."
    }

    if (exercise.bracingRecommended == true || fatigue >= 7) {
        bullets += "Puede mejorar tolerancia estructural y control técnico cuando se programa con criterio."
    } else {
        bullets += "Útil para salud muscular/articular al acumular práctica de calidad con fatiga moderada."
    }

    bullets += when (region) {
        ExerciseCatalogRegion.FULL -> "Aporta utilidad general para rendimiento global y coordinación."
        ExerciseCatalogRegion.UPPER -> "Útil para salud de hombro y mejora de empuje/tirón del tren superior."
        ExerciseCatalogRegion.LOWER -> "Útil para potencia del tren inferior, estabilidad y rendimiento atlético."
        ExerciseCatalogRegion.CORE -> "Útil para estabilidad del tronco y transmisión de fuerza."
        ExerciseCatalogRegion.ALL -> "Útil para construir base general según tu objetivo."
    }

    if (!exercise.sportsRelevance.isNullOrEmpty()) {
        bullets += "Muy usado en: ${exercise.sportsRelevance.take(4).joinToString(", ")}."
    }

    bullets += when {
        fatigue <= 3 -> "Permite acumular práctica técnica sin castigar demasiado la recuperación."
        fatigue <= 6 -> "Equilibrio entre estímulo y recuperación para progresar con constancia."
        else -> "Conviene periodizar su uso porque genera una demanda alta de recuperación."
    }

    return bullets.distinct().take(5)
}

private data class MuscleVolumeContribution(
    val muscle: String,
    val role: MuscleRole,
    val seriesEquivalent: Double,
)

private fun oneSeriesVolumeContributions(exercise: ExerciseMuscleInfo): List<MuscleVolumeContribution> {
    if (exercise.involvedMuscles.isEmpty()) return emptyList()

    val grouped = linkedMapOf<String, MutableList<MuscleVolumeContribution>>()
    val rolePriority = mapOf(
        MuscleRole.PRIMARY to 0,
        MuscleRole.SECONDARY to 1,
        MuscleRole.STABILIZER to 2,
        MuscleRole.NEUTRALIZER to 3,
    )
    exercise.involvedMuscles.forEach { involvement ->
        val muscle = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
        val contribution = resolveMuscleVolumeContribution(involvement)
        grouped.getOrPut(muscle) { mutableListOf() }
            .add(MuscleVolumeContribution(muscle, involvement.role, contribution))
    }

    return grouped.values.map { entries ->
        val topRole = entries.minByOrNull { rolePriority[it.role] ?: 99 }?.role ?: MuscleRole.SECONDARY
        MuscleVolumeContribution(
            muscle = entries.first().muscle,
            role = topRole,
            // 1 serie del ejercicio no puede aportar > 1.0 serie a un músculo
            seriesEquivalent = entries.maxOf { it.seriesEquivalent }.coerceIn(0.0, 1.0),
        )
    }.sortedByDescending { it.seriesEquivalent }
}

private fun roleVolumeLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Primario"
    MuscleRole.SECONDARY -> "Secundario"
    MuscleRole.STABILIZER -> "Estabilizador"
    MuscleRole.NEUTRALIZER -> "Neutralizador"
}

private fun formatSeriesEquivalent(value: Double): String {
    val normalized = value.coerceAtLeast(0.0)
    val text = "%.1f".format(normalized)
    return "$text serie"
}

private fun fatigueColor(score: Int): Color = when {
    score <= 3 -> Color(0xFF22C55E)
    score <= 6 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

private fun fatigueLabel(score: Int): String = when {
    score <= 3 -> "Poca fatiga"
    score <= 6 -> "Fatiga media"
    score <= 8 -> "Alta fatiga"
    else -> "Fatiga muy alta"
}

@Composable
internal fun ExercisePickerCompactCard(
    info: ExerciseMuscleInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInfo: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
    val bgAlpha = if (isSelected) 0.40f else 0.24f
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    info.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onInfo, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "Ver detalle", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                listOfNotNull(resolvePrimaryMuscleLabel(info), info.equipment).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ExercisePickerDetailedCard(
    info: ExerciseMuscleInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInfo: () -> Unit,
) {
    val primaryMuscle = resolvePrimaryMuscleLabel(info)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val bgAlpha = if (isSelected) 0.44f else 0.28f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha),
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Text(
                        listOfNotNull(primaryMuscle, info.equipment, info.type).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onInfo) {
                    Icon(Icons.Default.Info, contentDescription = "Ver detalle")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseFactChip("Región", resolveExerciseRegion(info).label)
                info.category?.takeIf { it.isNotBlank() }?.let { ExerciseFactChip("Perfil", it) }
                if (matchesCatalogTrait(info, ExerciseCatalogTrait.BASIC)) ExerciseFactChip("Básico", "Sí")
                if (matchesCatalogTrait(info, ExerciseCatalogTrait.FREE)) ExerciseFactChip("Libre", "Sí")
                if (matchesCatalogTrait(info, ExerciseCatalogTrait.MACHINE)) ExerciseFactChip("Máquina", "Sí")
                if (matchesCatalogTrait(info, ExerciseCatalogTrait.UNILATERAL)) ExerciseFactChip("Unilateral", "Sí")
            }

            if (!info.description.isNullOrBlank()) {
                Text(
                    info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ExerciseCatalogInfoDialog(
    exercise: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    associatedDiscomforts: List<Pair<String, Int>>,
    onOpenExercise: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val fatigue = remember(exercise.id) { calculateFriendlyFatigue(exercise) }
    val kinship = remember(exercise.id, catalog) { buildExerciseKinships(exercise, catalog) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            listOfNotNull(resolvePrimaryMuscleLabel(exercise), exercise.equipment, exercise.type).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenExercise(exercise.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir página completa")
                }

                exercise.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseFactChip("Set-up", inferSetupTimeLabel(exercise))
                    ExerciseFactChip("Curva", inferLearningCurveLabel(exercise))
                    ExerciseFactChip("Fatiga", fatigueLabel(fatigue.overall))
                    ExerciseFactChip("Región", resolveExerciseRegion(exercise).label)
                }

                ExerciseFatigueScenarios(exercise = exercise)

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Utilidad del ejercicio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        buildExerciseUtilityBullets(exercise).forEach { bullet ->
                            Text(
                                "• $bullet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val muscleContributions = remember(exercise.id, exercise.involvedMuscles) {
                    oneSeriesVolumeContributions(exercise)
                }
                if (muscleContributions.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Músculos involucrados y aporte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            Text(
                                "Aporte estimado por 1 serie efectiva del ejercicio.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            muscleContributions.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.muscle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            roleVolumeLabel(item.role),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    ) {
                                        Text(
                                            formatSeriesEquivalent(item.seriesEquivalent),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (associatedDiscomforts.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Molestias asociadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            associatedDiscomforts.forEach { (label, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(
                                        "x$count",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }

                if (kinship.similar.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Otras opciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            Text(
                                "Mismo patrón de movimiento y perfil similar. Pulsa una opción para abrir su ficha.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(kinship.similar, key = { it.exercise.id }) { similar ->
                                    val similarFatigue = calculateFriendlyFatigue(similar.exercise).overall
                                    Card(
                                        modifier = Modifier
                                            .width(250.dp)
                                            .clickable {
                                                onDismiss()
                                                onOpenExercise(similar.exercise.id)
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(similar.exercise.name, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(resolvePrimaryMuscleLabel(similar.exercise), similar.exercise.equipment, similar.exercise.type)
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                similar.rationale,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                fatigueLabel(similarFatigue),
                                                color = fatigueColor(similarFatigue),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!exercise.setupCues.isNullOrEmpty() || !exercise.executionCues.isNullOrEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Claves rápidas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            exercise.setupCues.orEmpty().take(2).forEach { cue ->
                                Text("Set-up: $cue", style = MaterialTheme.typography.bodySmall)
                            }
                            exercise.executionCues.orEmpty().take(2).forEach { cue ->
                                Text("Ejecución: $cue", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendlyFatigueRow(label: String, score: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 10f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fatigueColor(score))
            )
        }
        Text("$score/10", color = fatigueColor(score), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BackgroundSheet(
    session: Session,
    onPickImage: () -> Unit,
    onSelectGradient: (String) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundBrightnessChange: (Float) -> Unit,
    onCoverBrightnessChange: (Float) -> Unit,
    onCoverContrastChange: (Float) -> Unit,
    onCoverSaturationChange: (Float) -> Unit,
    onCoverGrayscaleChange: (Float) -> Unit,
    onCoverVignetteChange: (Float) -> Unit,
    onCoverMotionChange: (Boolean) -> Unit,
    onLabelPositionChange: (LabelPosition) -> Unit,
) {
    val blur = session.background?.style?.blur ?: 0f
    val brightness = session.background?.style?.brightness ?: 0.92f
    val coverContrast = session.coverStyle?.filters?.contrast ?: 1f
    val coverSaturation = session.coverStyle?.filters?.saturation ?: 1f
    val coverGrayscale = session.coverStyle?.filters?.grayscale ?: 0f
    val coverVignette = session.coverStyle?.filters?.vignette ?: 0f
    val coverMotion = session.coverStyle?.enableMotion ?: false
    val coverBrightness = session.coverStyle?.filters?.brightness ?: 1f
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Fondo de sesión", fontWeight = FontWeight.Black, fontSize = 18.sp)
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image, null)
            Spacer(Modifier.width(8.dp))
            Text("Subir imagen")
        }
        Text("Gradientes")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            sessionGradients.forEach { gradient ->
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 74.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(gradient.colors))
                        .border(
                            width = if (session.background?.value == gradient.id) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { onSelectGradient(gradient.id) }
                )
            }
        }
        Text("Colores sólidos")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            sessionSolidPresets.forEach { solid ->
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(solid.colors.first())
                        .border(
                            width = if (session.background?.value == solid.id) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onSelectGradient(solid.id) }
                )
            }
        }
        Text("Desenfoque: ${blur.toInt()}")
        Slider(value = blur, onValueChange = onBackgroundBlurChange, valueRange = 0f..18f)
        Text("Brillo fondo: ${(brightness * 100).toInt()}%")
        Slider(value = brightness, onValueChange = onBackgroundBrightnessChange, valueRange = 0.25f..1f)
        Text("Brillo portada: ${(coverBrightness * 100).toInt()}%")
        Slider(value = coverBrightness, onValueChange = onCoverBrightnessChange, valueRange = 0.5f..1.4f)
        Text("Contraste portada: ${(coverContrast * 100).toInt()}%")
        Slider(value = coverContrast, onValueChange = onCoverContrastChange, valueRange = 0.5f..1.5f)
        Text("Saturación portada: ${(coverSaturation * 100).toInt()}%")
        Slider(value = coverSaturation, onValueChange = onCoverSaturationChange, valueRange = 0f..2f)
        Text("Escala de grises: ${(coverGrayscale * 100).toInt()}%")
        Slider(value = coverGrayscale, onValueChange = onCoverGrayscaleChange, valueRange = 0f..1f)
        Text("Viñeta: ${(coverVignette * 100).toInt()}%")
        Slider(value = coverVignette, onValueChange = onCoverVignetteChange, valueRange = 0f..1f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Movimiento portada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = coverMotion, onCheckedChange = onCoverMotionChange)
        }
        Text("Posición del título")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LabelPosition.BOTTOM_LEFT, LabelPosition.CENTER, LabelPosition.BOTTOM_CENTER).forEach { position ->
                FilterChip(selected = session.coverStyle?.labelPosition == position, onClick = { onLabelPositionChange(position) }, label = { Text(position.name.lowercase()) })
            }
        }
    }
}

@Composable
private fun HistorySheet(
    uiState: SessionEditorUiState,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Historial y borradores", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Cambios recientes del borrador", style = MaterialTheme.typography.labelLarge)
        if (uiState.localDraftHistory.isEmpty()) {
            Text("Todavía no hay snapshots locales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            uiState.localDraftHistory.asReversed().forEachIndexed { index, snapshot ->
                val title = snapshot.session.name.ifBlank { "Sesión" }
                val diffSummary = snapshot.changedFields.take(3).joinToString(" · ")
                OutlinedButton(onClick = { onRestoreSnapshot(snapshot) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("${index + 1}. $title", fontWeight = FontWeight.Bold)
                        Text(
                            "${formatHistoryTimestamp(snapshot.savedAtMs)} · ${snapshot.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${snapshot.exerciseCount} ejercicios · ${snapshot.setCount} series · ${snapshot.partCount} grupos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Cambios: $diffSummary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Sesiones registradas", style = MaterialTheme.typography.labelLarge)
        if (uiState.workoutLogs.isEmpty()) {
            Text("Todavía no hay historiales de esta sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.workoutLogs.forEach { log ->
                    Card {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(log.date.substringBefore("T"), fontWeight = FontWeight.Black)
                            Text("Duración ${log.durationMinutes} min · Volumen ${"%.0f".format(log.totalVolume)}", style = MaterialTheme.typography.bodySmall)
                            Text("Fatiga ${log.fatigueLevel ?: 0}/10 · Estrés ${log.sessionStressScore?.toInt() ?: 0}", style = MaterialTheme.typography.bodySmall)
                            if (!log.discomforts.isNullOrEmpty()) Text("Molestias: ${log.discomforts.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            if (!log.notes.isNullOrBlank()) Text(log.notes, style = MaterialTheme.typography.bodySmall)
                            uiState.feedbackByLogId[log.id]?.let { feedback ->
                                Text("Feedback muscular: ${feedback.muscleFeedback.keys.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesSheet(
    uiState: SessionEditorUiState,
    onApplyRules: (String?) -> Unit,
    onRuleDefaultsChange: (Int?, Int?, Double?) -> Unit,
    onRuleLimitsChange: (Double?, Int?) -> Unit,
    onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
    onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
) {
    var scopePartId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("defaults") }
    var maxRpeInput by remember(uiState.ruleLimits.maxRPE) {
        mutableStateOf(formatEditableNumber(uiState.ruleLimits.maxRPE))
    }
    var maxExercisesInput by remember(uiState.ruleLimits.maxExercisesPerMuscle) {
        mutableStateOf(uiState.ruleLimits.maxExercisesPerMuscle?.toString().orEmpty())
    }
    var maxSessionVolumeInput by remember(uiState.ruleLimits.maxVolumePerMuscleSession) {
        mutableStateOf(formatEditableNumber(uiState.ruleLimits.maxVolumePerMuscleSession))
    }
    var maxWeeklyVolumeInput by remember(uiState.ruleLimits.maxVolumePerMuscleWeekly) {
        mutableStateOf(formatEditableNumber(uiState.ruleLimits.maxVolumePerMuscleWeekly))
    }
    var maxPatternInput by remember(uiState.ruleLimits.maxSamePatternPerSession) {
        mutableStateOf(uiState.ruleLimits.maxSamePatternPerSession?.toString().orEmpty())
    }
    var rigidLimits by remember(uiState.ruleLimits.rigidLimits) { mutableStateOf(uiState.ruleLimits.rigidLimits) }
    var adjustmentModeName by remember { mutableStateOf(IntensityMode.RPE.name) }
    var adjustmentValueInput by remember { mutableStateOf("7.5") }
    var useMuscleScope by remember { mutableStateOf(false) }
    var selectedMuscles by remember { mutableStateOf(setOf<String>()) }
    val adjustmentMode = runCatching { IntensityMode.valueOf(adjustmentModeName) }.getOrElse { IntensityMode.RPE }
    val availableMuscles = remember(uiState.session) {
        uiState.session?.allExercises()?.mapNotNull { exercise ->
            EXERCISE_DATABASE
                .firstOrNull { it.id == exercise.exerciseDbId || it.id == exercise.exerciseId || it.name.equals(exercise.name, ignoreCase = true) }
                ?.involvedMuscles
                ?.firstOrNull { muscle -> muscle.role == MuscleRole.PRIMARY }
                ?.let { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
        }?.distinct().orEmpty()
    }

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Reglas del editor", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "defaults", onClick = { tab = "defaults" }, label = { Text("Defaults") })
            FilterChip(selected = tab == "modify", onClick = { tab = "modify" }, label = { Text("Modificar") })
            FilterChip(selected = tab == "limits", onClick = { tab = "limits" }, label = { Text("Límites") })
        }

        if (tab == "defaults") {
            Text("Defaults rápidos para rellenar sets y descansos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            EditorMiniField(label = "Series", value = uiState.ruleDefaults.setCount.toString(), keyboardType = KeyboardType.Number) { onRuleDefaultsChange(it.safeIntOrNull(), null, null) }
            EditorMiniField(label = "Reps", value = uiState.ruleDefaults.reps.toString(), keyboardType = KeyboardType.Number) { onRuleDefaultsChange(null, it.safeIntOrNull(), null) }
            EditorMiniField(label = "RPE", value = formatEditableNumber(uiState.ruleDefaults.rpe), keyboardType = KeyboardType.Decimal) { onRuleDefaultsChange(null, null, it.safeDoubleOrNull()) }
            Text("Aplicar sobre")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = scopePartId == null, onClick = { scopePartId = null }, label = { Text("Toda la sesión") })
                uiState.session?.parts?.forEach { part ->
                    FilterChip(selected = scopePartId == part.id, onClick = { scopePartId = part.id }, label = { Text(part.name) })
                }
            }
            Button(onClick = { onApplyRules(scopePartId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Aplicar defaults", fontWeight = FontWeight.Black)
            }
        } else if (tab == "modify") {
            Text("Modifica intensidad globalmente por modo y alcance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Modo objetivo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(IntensityMode.RPE, IntensityMode.RIR, IntensityMode.FAILURE, IntensityMode.SOLO_RM).forEach { mode ->
                    FilterChip(
                        selected = adjustmentMode == mode,
                        onClick = { adjustmentModeName = mode.name },
                        label = { Text(mode.name) },
                    )
                }
            }
            EditorMiniField(
                label = when (adjustmentMode) {
                    IntensityMode.RPE -> "RPE objetivo"
                    IntensityMode.RIR -> "RIR objetivo"
                    IntensityMode.SOLO_RM -> "%RM objetivo"
                    IntensityMode.FAILURE -> "Valor"
                    else -> "Valor"
                },
                value = adjustmentValueInput,
                keyboardType = KeyboardType.Decimal,
            ) { adjustmentValueInput = it }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = useMuscleScope, onCheckedChange = { useMuscleScope = it })
                Text("Aplicar solo a músculos seleccionados")
            }
            if (useMuscleScope) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableMuscles.forEach { muscle ->
                        val selected = muscle in selectedMuscles
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedMuscles = if (selected) selectedMuscles - muscle else selectedMuscles + muscle
                            },
                            label = { Text(muscle) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val value = adjustmentValueInput.safeDoubleOrNull()
                    if (value != null) {
                        onApplyGlobalIntensityAdjustment(
                            adjustmentMode,
                            value,
                            if (useMuscleScope) selectedMuscles else null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Aplicar modificación", fontWeight = FontWeight.Black)
            }
        } else {
            Text("Límites de seguridad al guardar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            EditorMiniField(
                label = "RPE máximo",
                value = maxRpeInput,
                keyboardType = KeyboardType.Decimal,
                stateKey = "rules-max-rpe",
            ) { value ->
                maxRpeInput = value
            }
            EditorMiniField(
                label = "Máx ejercicios / músculo",
                value = maxExercisesInput,
                keyboardType = KeyboardType.Number,
                stateKey = "rules-max-ex-muscle",
            ) { value ->
                maxExercisesInput = value
            }
            EditorMiniField(
                label = "Volumen máx por músculo (sesión)",
                value = maxSessionVolumeInput,
                keyboardType = KeyboardType.Decimal,
                stateKey = "rules-max-vol-session",
            ) { value ->
                maxSessionVolumeInput = value
            }
            EditorMiniField(
                label = "Volumen máx por músculo (semana)",
                value = maxWeeklyVolumeInput,
                keyboardType = KeyboardType.Decimal,
                stateKey = "rules-max-vol-week",
            ) { value ->
                maxWeeklyVolumeInput = value
            }
            EditorMiniField(
                label = "Máx patrón repetido / sesión",
                value = maxPatternInput,
                keyboardType = KeyboardType.Number,
                stateKey = "rules-max-pattern",
            ) { value ->
                maxPatternInput = value
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = rigidLimits, onCheckedChange = { rigidLimits = it })
                Text(if (rigidLimits) "Límites rígidos (bloquea guardar)" else "Límites flexibles (solo alerta)")
            }
            Button(
                onClick = {
                    onRuleLimitsChange(
                        maxRpeInput.safeDoubleOrNull(),
                        maxExercisesInput.safeIntOrNull(),
                    )
                    onAdvancedRuleLimitsChange(
                        maxSessionVolumeInput.safeDoubleOrNull(),
                        maxWeeklyVolumeInput.safeDoubleOrNull(),
                        maxPatternInput.safeIntOrNull(),
                        rigidLimits,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar límites", fontWeight = FontWeight.Black)
            }
        }
    }
}

private enum class SessionClonerMode { CLONE_TO_DAYS, IMPORT_FROM_DAY }

@Composable
private fun SessionClonerSheet(
    uiState: SessionEditorUiState,
    onCloneCurrentToTargets: (Set<String>, Set<String>?, SessionCloneApplyMode) -> Unit,
    onImportFromSource: (String, Set<String>?, SessionCloneApplyMode) -> Unit,
) {
    val currentSession = uiState.session ?: return
    val availableExercises = remember(currentSession) {
        currentSession.parts.flatMap { part ->
            part.exercises.map { ex -> SessionCloneExerciseOption(ex.id, ex.name.ifBlank { "Ejercicio" }, part.name) }
        } + currentSession.exercises.map { ex ->
            SessionCloneExerciseOption(ex.id, ex.name.ifBlank { "Ejercicio" }, null)
        }
    }

    var mode by rememberSaveable { mutableStateOf(SessionClonerMode.CLONE_TO_DAYS) }
    var applyModeName by rememberSaveable { mutableStateOf(SessionCloneApplyMode.APPEND.name) }
    val applyMode = remember(applyModeName) {
        runCatching { SessionCloneApplyMode.valueOf(applyModeName) }.getOrElse { SessionCloneApplyMode.APPEND }
    }
    var clonePartial by rememberSaveable { mutableStateOf(false) }
    var importPartial by rememberSaveable { mutableStateOf(false) }
    var selectedTargetKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedCloneExerciseIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedSourceSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImportExerciseIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    val sourceOption = remember(uiState.cloneSourceOptions, selectedSourceSessionId) {
        uiState.cloneSourceOptions.firstOrNull { it.sessionId == selectedSourceSessionId }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Clonador de sesiones", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                "Copia esta sesión a varios días o trae una sesión de otro día/semana/bloque.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == SessionClonerMode.CLONE_TO_DAYS,
                    onClick = { mode = SessionClonerMode.CLONE_TO_DAYS },
                    label = { Text("Copiar hacia") },
                )
                FilterChip(
                    selected = mode == SessionClonerMode.IMPORT_FROM_DAY,
                    onClick = { mode = SessionClonerMode.IMPORT_FROM_DAY },
                    label = { Text("Traer desde") },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionCloneApplyMode.entries.forEach { candidate ->
                    FilterChip(
                        selected = applyMode == candidate,
                        onClick = { applyModeName = candidate.name },
                        label = { Text(if (candidate == SessionCloneApplyMode.APPEND) "Agregar" else "Reemplazar") },
                    )
                }
            }
        }

        if (mode == SessionClonerMode.CLONE_TO_DAYS) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Selecciona días destino", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                uiState.cloneDayOptions
                    .filterNot { it.isCurrentSessionDay }
                    .forEach { target ->
                        val selected = target.key in selectedTargetKeys
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedTargetKeys = if (selected) {
                                        selectedTargetKeys - target.key
                                    } else {
                                        selectedTargetKeys + target.key
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            ),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${dayLabel(target.dayOfWeek)} · ${target.weekName}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        "${target.blockName} · ${target.mesoName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    if (target.existingSessionId != null) {
                                        "Destino: ${target.existingSessionName?.ifBlank { "Sesión" } ?: "Sesión"}"
                                    } else {
                                        "Sin sesión"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (target.existingSessionId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = clonePartial, onCheckedChange = { clonePartial = it })
                    Text("Clonación parcial (ejercicios seleccionados)")
                }

                if (clonePartial) {
                    availableExercises.forEach { exercise ->
                        val selected = exercise.exerciseId in selectedCloneExerciseIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedCloneExerciseIds = if (selected) {
                                        selectedCloneExerciseIds - exercise.exerciseId
                                    } else {
                                        selectedCloneExerciseIds + exercise.exerciseId
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text(exercise.sourcePartName ?: "Sin grupo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Button(
                    onClick = {
                        onCloneCurrentToTargets(
                            selectedTargetKeys,
                            if (clonePartial) selectedCloneExerciseIds else null,
                            applyMode,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clonar hacia días seleccionados", fontWeight = FontWeight.Black)
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Selecciona sesión origen", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                uiState.cloneSourceOptions.forEach { source ->
                    val selected = selectedSourceSessionId == source.sessionId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedSourceSessionId = source.sessionId },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(source.sessionName, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text(
                                    "${dayLabel(source.dayOfWeek)} · ${source.weekName} · ${source.blockName} · ${source.mesoName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${source.exerciseCount} ejercicios",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = importPartial, onCheckedChange = { importPartial = it })
                    Text("Importación parcial (ejercicios seleccionados)")
                }

                if (importPartial && sourceOption != null) {
                    sourceOption.exercises.forEach { exercise ->
                        val selected = exercise.exerciseId in selectedImportExerciseIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedImportExerciseIds = if (selected) {
                                        selectedImportExerciseIds - exercise.exerciseId
                                    } else {
                                        selectedImportExerciseIds + exercise.exerciseId
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, fontWeight = FontWeight.SemiBold)
                                Text(exercise.sourcePartName ?: "Sin grupo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Button(
                    onClick = {
                        val sourceId = selectedSourceSessionId ?: return@Button
                        onImportFromSource(
                            sourceId,
                            if (importPartial) selectedImportExerciseIds else null,
                            applyMode,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedSourceSessionId != null,
                ) {
                    Text("Traer sesión al editor actual", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun formatHistoryTimestamp(timestampMs: Long): String {
    return runCatching {
        SimpleDateFormat("dd MMM · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(timestampMs))
    }.getOrDefault("Momento desconocido")
}

@Composable
private fun SaveSheet(
    onSave: (SessionSaveScope) -> Unit,
    onDiscardSwitch: (() -> Unit)?,
    isSimpleProgram: Boolean,
) {
    var saveScope by rememberSaveable { mutableStateOf(SessionSaveScope.SESSION_ONLY) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Guardar cambios", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            if (onDiscardSwitch != null) {
                "Hay cambios sin guardar. Si guardas, continuarás editando la sesión destino. Si descartas, los cambios se perderán."
            } else if (isSimpleProgram) {
                "Este programa es simple: el guardado aplica solo a esta sesión."
            } else {
                "Puedes guardar solo esta sesión o propagar el mismo molde al mesociclo."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isSimpleProgram) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = saveScope == SessionSaveScope.SESSION_ONLY,
                    onClick = { saveScope = SessionSaveScope.SESSION_ONLY },
                    label = { Text("Solo esta sesión") },
                )
                FilterChip(
                    selected = saveScope == SessionSaveScope.MESOCYCLE,
                    onClick = { saveScope = SessionSaveScope.MESOCYCLE },
                    label = { Text("Todo el mesociclo") },
                )
            }
        }
        Button(
            onClick = {
                onSave(if (isSimpleProgram) SessionSaveScope.SESSION_ONLY else saveScope)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (onDiscardSwitch != null) "Guardar y continuar" else "Guardar y volver al programa",
                fontWeight = FontWeight.Black,
            )
        }
        if (onDiscardSwitch != null) {
            OutlinedButton(onClick = onDiscardSwitch, modifier = Modifier.fillMaxWidth()) {
                Text("Descartar y cambiar sesión")
            }
        }
    }
}

@Composable
private fun WarmupSheet(
    exercise: Exercise?,
    onSave: (String, List<WarmupSetDefinition>) -> Unit,
) {
    if (exercise == null) return
    var sets by remember(exercise.id) {
        mutableStateOf(exercise.warmupSets.ifEmpty {
            listOf(WarmupSetDefinition(UUID.randomUUID().toString(), 50.0, 10))
        })
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Series de aproximación · ${exercise.name}", fontWeight = FontWeight.Black, fontSize = 16.sp)

        sets.forEachIndexed { index, set ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${index + 1}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        EditorMiniField(
                            label = "Carga %",
                            value = formatEditableNumber(set.percentageOfWorkingWeight),
                            keyboardType = KeyboardType.Decimal,
                            stateKey = "warmup-percent-${set.id}",
                            modifier = Modifier.weight(1f),
                            onCommit = {
                                val newPercent = it.safeDoubleOrNull() ?: set.percentageOfWorkingWeight
                                sets = sets.toMutableList().also { list ->
                                    list[index] = set.copy(
                                        percentageOfWorkingWeight = newPercent,
                                        targetReps = suggestWarmupReps(newPercent),
                                    )
                                }
                            },
                        )
                        EditorMiniField(
                            label = "Reps",
                            value = set.targetReps.toString(),
                            keyboardType = KeyboardType.Number,
                            stateKey = "warmup-reps-${set.id}",
                            modifier = Modifier.weight(1f),
                            onCommit = {
                                sets = sets.toMutableList().also { list ->
                                    list[index] = set.copy(targetReps = it.safeIntOrNull() ?: set.targetReps)
                                }
                            },
                        )
                        IconButton(onClick = { sets = sets.filterIndexed { idx, _ -> idx != index } }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorMiniField(
                            label = "Descanso (s, opcional)",
                            value = set.restBetween?.toString().orEmpty(),
                            keyboardType = KeyboardType.Number,
                            stateKey = "warmup-rest-${set.id}",
                            modifier = Modifier.weight(1f),
                            onCommit = {
                                sets = sets.toMutableList().also { list ->
                                    list[index] = set.copy(restBetween = it.safeIntOrNull())
                                }
                            },
                        )
                        Spacer(Modifier.weight(1f)) // spacer to balance the delete button area
                    }
                }
            }
        }

        OutlinedButton(onClick = {
            val last = sets.lastOrNull()
            val nextPercent = ((last?.percentageOfWorkingWeight ?: 40.0) + 10).coerceAtMost(95.0)
            sets = sets + WarmupSetDefinition(UUID.randomUUID().toString(), nextPercent, suggestWarmupReps(nextPercent))
        }) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar aproximación")
        }
        Button(onClick = { onSave(exercise.id, sets) }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AssistantSheet(
    uiState: SessionEditorUiState,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (String) -> Unit,
) {
    val report = uiState.assistantReport
    val summary = uiState.augeSummary
    val accentColor = augeStatusColor(summary.status, summary.hasCriticalAlerts)
    var ringsExpanded by rememberSaveable { mutableStateOf(false) }
    var volumeExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Asistente de sesión", fontWeight = FontWeight.Black, fontSize = 18.sp)

        if (report != null) {
            AssistantVeredictoCard(report)
            if (report.riesgos.isNotEmpty()) {
                AssistantSectionTitle("Riesgos detectados")
                report.riesgos.forEach { risk ->
                    AssistantRiskCard(risk)
                }
            }
            if (report.ajustes.isNotEmpty()) {
                AssistantSectionTitle("Ajustes sugeridos")
                report.ajustes.forEach { suggestion ->
                    AssistantSuggestionCard(suggestion, onApplyAssistantSuggestion)
                }
            }
            if (report.tarjetasFantasma.isNotEmpty()) {
                AssistantSectionTitle("Propuestas")
                report.tarjetasFantasma.forEach { card ->
                    AssistantGhostCard(card, onAddGhostExercise)
                }
            }
            if (report.plantillasCompatibles.isNotEmpty()) {
                AssistantSectionTitle("Plantillas compatibles")
                report.plantillasCompatibles.forEach { preview ->
                    AssistantTemplatePreviewCard(preview)
                }
            }
        } else {
            Text(
                "Calculando análisis...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val energySummary = summary.sessionEnergy
        if (energySummary.totalKcal.mid > 0) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kcal estimadas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${energySummary.totalKcal.mid}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${energySummary.totalKcal.low}–${energySummary.totalKcal.high} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EPOC estimado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${energySummary.epocKcal.mid} kcal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Confianza", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (energySummary.confidence) {
                                EnergyConfidence.HIGH -> "alta"
                                EnergyConfidence.MEDIUM -> "media"
                                EnergyConfidence.LOW -> "baja"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = when (energySummary.confidence) {
                                EnergyConfidence.HIGH -> Color(0xFF22C55E)
                                EnergyConfidence.MEDIUM -> Color(0xFFF59E0B)
                                EnergyConfidence.LOW -> Color(0xFFEF4444)
                            },
                        )
                    }
                }
            }
        }

        // Rings section (kept from old AugeSheet)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ringsExpanded = !ringsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "RINGS de sesión",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Icon(
                        imageVector = if (ringsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                EstimatedRingsRow(
                    energy = (100 - summary.sessionDrain.cns).coerceIn(0, 100),
                    spine = (100 - summary.sessionDrain.spinal).coerceIn(0, 100),
                )
            }
        }

        // Volume section (kept from old AugeSheet)
        val sortedVolumeEntries = remember(report) {
            (report?.volumenPorMusculo ?: summary.sessionVolumeByMuscle)
                .entries
                .filter { it.value > 0.0 }
                .sortedByDescending { it.value }
        }
        if (sortedVolumeEntries.isNotEmpty()) {
            val maxSets = sortedVolumeEntries.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { volumeExpanded = !volumeExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Volumen por músculo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Icon(
                            imageVector = if (volumeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                    if (volumeExpanded) {
                        sortedVolumeEntries.forEach { (muscle, sets) ->
                            val threshold = report?.umbralesPorMusculo?.get(muscle)
                            val mev = threshold?.mev
                            val mav = threshold?.mav
                            val mrv = threshold?.mrv
                            val indicatorColor = when {
                                mrv != null && sets > mrv -> Color(0xFFEF4444)
                                mav != null && sets > mav -> Color(0xFFF59E0B)
                                mev != null && sets >= mev -> Color(0xFF22C55E)
                                else -> accentColor
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(muscle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "${if (sets == sets.toLong().toDouble()) sets.toLong().toString() else "%.1f".format(sets)} sets",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = indicatorColor,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (sets / maxSets).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = indicatorColor,
                                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantVeredictoCard(report: com.example.kpkn.domain.sessionassistant.SessionAssistantReport) {
    val color = when (report.veredicto) {
        com.example.kpkn.domain.sessionassistant.Verdict.OPTIMAL -> Color(0xFF22C55E)
        com.example.kpkn.domain.sessionassistant.Verdict.WARNING -> Color(0xFFF59E0B)
        com.example.kpkn.domain.sessionassistant.Verdict.FATIGUING -> Color(0xFFF97316)
        com.example.kpkn.domain.sessionassistant.Verdict.CRITICAL -> Color(0xFFEF4444)
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
                    Text(
                        "${report.scoreEstimado}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = color,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    report.veredicto.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = color,
                )
            }
            Text(
                report.resumenTexto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AssistantSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
}

@Composable
private fun AssistantRiskCard(risk: com.example.kpkn.domain.sessionassistant.SessionRisk) {
    val color = when (risk.severity) {
        com.example.kpkn.domain.sessionassistant.RiskSeverity.BLOCKING -> Color(0xFFEF4444)
        com.example.kpkn.domain.sessionassistant.RiskSeverity.WARNING -> Color(0xFFF59E0B)
        com.example.kpkn.domain.sessionassistant.RiskSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
                    Text(
                        risk.severity.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(risk.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Text(risk.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(risk.action, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AssistantSuggestionCard(
    suggestion: com.example.kpkn.domain.sessionassistant.AssistantSuggestion,
    onApplySuggestion: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(suggestion.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(suggestion.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.LOWER_RPE ||
                suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_SET ||
                suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.REMOVE_FAILURE
            ) {
                FilledTonalButton(onClick = { onApplySuggestion(suggestion.id) }) {
                    Text("Aplicar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AssistantGhostCard(
    card: com.example.kpkn.domain.sessionassistant.GhostExerciseCard,
    onAdd: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(card.name, fontWeight = FontWeight.Bold)
            }
            Text(card.motivo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(card.impactoVolumen, card.impactoDrenaje, card.impactoColumna).forEach { impact ->
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                        Text(
                            impact,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text("${card.sets} × ${card.reps} @ RPE ${card.rpe}", style = MaterialTheme.typography.labelSmall)
            FilledTonalButton(onClick = { onAdd(card.cardId) }) {
                Text("Añadir", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AssistantTemplatePreviewCard(preview: com.example.kpkn.domain.sessionassistant.TemplatePreview) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preview.template.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(preview.template.name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${preview.duracionEstimada}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                preview.modoRecomendado.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (preview.advertencias.isNotEmpty()) {
                preview.advertencias.forEach { adv ->
                    Text("⚠ $adv", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
                }
            }
        }
    }
}

private fun buildExerciseCatalogLookup(catalog: List<ExerciseMuscleInfo>): Map<String, ExerciseMuscleInfo> {
    val base = catalog.associateBy { it.id.lowercase() }
    val aliasEntries = EXERCISE_ID_ALIASES.mapNotNull { (alias, canonical) ->
        base[canonical]?.let { alias.lowercase() to it }
    }.toMap()
    return base + aliasEntries
}

private fun resolveCatalogExerciseInfo(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    val byId = exercise.exerciseDbId ?: exercise.exerciseId
    return byId?.lowercase()?.let(catalogLookup::get)
        ?: catalogLookup.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
}

private fun buildDiscomfortByExercise(
    workoutLogs: List<WorkoutLog>,
): Map<String, List<Pair<String, Int>>> {
    val map = mutableMapOf<String, MutableMap<String, Int>>()
    workoutLogs.forEach { log ->
        log.postExerciseReports.forEach { report ->
            val key = report.canonicalExerciseId ?: report.exerciseDbId ?: report.exerciseId
            if (key.isBlank()) return@forEach
            val bucket = map.getOrPut(key) { mutableMapOf() }
            report.discomfortIds
                .filter { it != "none" }
                .forEach { discomfortId ->
                    val label = discomfortLabel(discomfortId)
                    bucket[label] = (bucket[label] ?: 0) + 1
                }
        }
    }

    return map.mapValues { (_, value) ->
        value.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }
}

@Composable
private fun AugeOverviewMetric(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.7f else 0.8f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AugeSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AugeAlertCard(
    alert: SessionEditorAugeAlert,
    onApplyCorrection: (String) -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = accentColor,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        alert.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        "Recomendación",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    alert.exerciseName?.let { AugeTag(it, accentColor) }
                    alert.muscle?.let { AugeTag(it, accentColor) }
                }

            if (alert.correctionType != null) {
                FilledTonalButton(onClick = { onApplyCorrection(alert.id) }) {
                    Text(augeCorrectionLabel(alert.correctionType), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AugeTag(
    label: String,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.12f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun augeStatusLabel(status: SessionEditorAugeStatus): String = when (status) {
    SessionEditorAugeStatus.OPTIMAL -> "Sesión bien calibrada"
    SessionEditorAugeStatus.WARNING -> "Sesión a vigilar"
    SessionEditorAugeStatus.FATIGUING -> "Sesión fatigante"
}

private fun augeStatusColor(
    status: SessionEditorAugeStatus,
    critical: Boolean = false,
): Color = when {
    critical -> Color(0xFFEA580C)
    status == SessionEditorAugeStatus.OPTIMAL -> Color(0xFF16A34A)
    status == SessionEditorAugeStatus.WARNING -> Color(0xFFF59E0B)
    else -> Color(0xFFEA580C)
}

// (removed source label helpers - no longer displayed)

private fun augeCorrectionLabel(correctionType: SessionEditorAugeCorrectionType): String = when (correctionType) {
    SessionEditorAugeCorrectionType.REDUCE_SERIES -> "Aplicar recorte de series"
    SessionEditorAugeCorrectionType.REDUCE_RPE -> "Aplicar baja de intensidad"
    SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE -> "Bajar volumen e intensidad"
    SessionEditorAugeCorrectionType.ADD_SERIES -> "Agregar una serie"
}

private data class SuggestionGroup(
    val title: String,
    val alerts: List<SessionEditorAugeAlert>,
    val correctionType: SessionEditorAugeCorrectionType?,
)

private fun groupSuggestionsForSheet(suggestions: List<SessionEditorAugeAlert>): List<SuggestionGroup> {
    if (suggestions.isEmpty()) return emptyList()

    val grouped = suggestions
        .mapIndexed { index, alert -> index to alert }
        .groupBy { (_, alert) ->
            val normalizedTitle = alert.title
                .replace(Regex("\\s+para\\s+.+$", RegexOption.IGNORE_CASE), "")
                .trim()
            "$normalizedTitle|${alert.source}|${alert.correctionType ?: "none"}"
        }

    return grouped.values
        .sortedBy { pairs -> pairs.minOf { it.first } }
        .map { pairs ->
            val alerts = pairs.map { it.second }
            val normalizedTitle = alerts.first().title
                .replace(Regex("\\s+para\\s+.+$", RegexOption.IGNORE_CASE), "")
                .trim()
            val correctionType = alerts.mapNotNull { it.correctionType }.firstOrNull()
            SuggestionGroup(
                title = if (normalizedTitle.isBlank()) alerts.first().title else normalizedTitle,
                alerts = alerts,
                correctionType = correctionType,
            )
        }
}

private fun dayLabel(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Sin día"
}

private fun dayLabelShort(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
     else -> "?"
 }


private fun smartReferenceMetricLabel(mode: TrainingMode, customUnit: String?): String = when (mode) {
    TrainingMode.REPS,
    TrainingMode.RM,
    -> "Reps base"
    TrainingMode.TIME -> "Tiempo base"
    TrainingMode.DISTANCE -> "Dist. base"
    TrainingMode.CUSTOM -> "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} base"
    TrainingMode.SOLO_RPE -> "Base"
    TrainingMode.AMRAP -> "AMRAP"
}

private fun estimatedMetricLabel(mode: TrainingMode, customUnit: String?): String = when (mode) {
    TrainingMode.REPS,
    TrainingMode.RM,
    -> "Reps est."
    TrainingMode.TIME -> "Tiempo est."
    TrainingMode.DISTANCE -> "Dist. est."
    TrainingMode.CUSTOM -> "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} est."
    TrainingMode.SOLO_RPE -> "RPE"
    TrainingMode.AMRAP -> "AMRAP"
}

private fun formatEstimatedMetric(value: Double?, mode: TrainingMode, customUnit: String?): String {
    if (value == null) return "-"
    return when (mode) {
        TrainingMode.TIME -> "${value.toInt()}s"
        TrainingMode.CUSTOM -> formatEditableNumber(value)
        else -> value.toInt().toString()
    }
}


@Composable
private fun CompetitionSessionEditor(
    session: Session,
    onUpdateSession: ((Session) -> Session) -> Unit,
    onAddCompetitionMovement: () -> Unit,
) {
    var dateInput by rememberSaveable(session.id, session.competitionDetails?.competitionDate) {
        mutableStateOf(session.competitionDetails?.competitionDate.orEmpty())
    }
    var timeInput by rememberSaveable(session.id, session.competitionDetails?.startTime) {
        mutableStateOf(session.competitionDetails?.startTime.orEmpty())
    }
    var locationInput by rememberSaveable(session.id, session.competitionDetails?.location) {
        mutableStateOf(session.competitionDetails?.location.orEmpty())
    }
    var federationInput by rememberSaveable(session.id, session.competitionDetails?.federation) {
        mutableStateOf(session.competitionDetails?.federation.orEmpty())
    }
    var weighInDateInput by rememberSaveable(session.id, session.competitionDetails?.weighInDate) {
        mutableStateOf(session.competitionDetails?.weighInDate.orEmpty())
    }
    var weighInTimeInput by rememberSaveable(session.id, session.competitionDetails?.weighInTime) {
        mutableStateOf(session.competitionDetails?.weighInTime.orEmpty())
    }
    var strategyNotesInput by rememberSaveable(session.id, session.competitionDetails?.strategyNotes) {
        mutableStateOf(session.competitionDetails?.strategyNotes.orEmpty())
    }
    var reminderOneWeekEnabled by rememberSaveable(session.id, session.competitionDetails?.reminderOneWeekEnabled) {
        mutableStateOf(session.competitionDetails?.reminderOneWeekEnabled ?: true)
    }
    var reminder48hEnabled by rememberSaveable(session.id, session.competitionDetails?.reminder48hEnabled) {
        mutableStateOf(session.competitionDetails?.reminder48hEnabled ?: true)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Sesión de competición", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(
                "Configura datos del evento y registra intentos por movimiento. Sin timer de descanso.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val remindersText = competitionReminderSummary(session.competitionDetails)
            if (remindersText.isNotBlank()) {
                Text(
                    remindersText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            EditorMiniField(
                label = "Fecha competición (YYYY-MM-DD)",
                value = dateInput,
                stateKey = "comp-date-${session.id}",
            ) { input ->
                dateInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(competitionDate = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Hora inicio (HH:mm)",
                value = timeInput,
                stateKey = "comp-time-${session.id}",
            ) { input ->
                timeInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(startTime = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Ubicación",
                value = locationInput,
                stateKey = "comp-location-${session.id}",
            ) { input ->
                locationInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(location = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Federación",
                value = federationInput,
                stateKey = "comp-fed-${session.id}",
            ) { input ->
                federationInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(federation = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Pesaje (fecha YYYY-MM-DD)",
                value = weighInDateInput,
                stateKey = "comp-weigh-date-${session.id}",
            ) { input ->
                weighInDateInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(weighInDate = input.ifBlank { null }),
                    )
                }
            }
            EditorMiniField(
                label = "Pesaje (hora HH:mm)",
                value = weighInTimeInput,
                stateKey = "comp-weigh-time-${session.id}",
            ) { input ->
                weighInTimeInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(weighInTime = input.ifBlank { null }),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = reminderOneWeekEnabled,
                    onCheckedChange = { checked ->
                        reminderOneWeekEnabled = checked
                        onUpdateSession { current ->
                            current.copy(
                                competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(reminderOneWeekEnabled = checked),
                            )
                        }
                    },
                )
                Text("Recordatorio 1 semana antes", style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = reminder48hEnabled,
                    onCheckedChange = { checked ->
                        reminder48hEnabled = checked
                        onUpdateSession { current ->
                            current.copy(
                                competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(reminder48hEnabled = checked),
                            )
                        }
                    },
                )
                Text("Recordatorio 48h antes", style = MaterialTheme.typography.bodySmall)
            }
            EditorMiniField(
                label = "Estrategia / notas de competición",
                value = strategyNotesInput,
                stateKey = "comp-strategy-${session.id}",
            ) { input ->
                strategyNotesInput = input
                onUpdateSession { current ->
                    current.copy(
                        competitionDetails = (current.competitionDetails ?: CompetitionDetails()).copy(strategyNotes = input.ifBlank { null }),
                    )
                }
            }

            if (session.exercises.isEmpty()) {
                OutlinedButton(onClick = onAddCompetitionMovement, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar movimiento de competición")
                }
            } else {
                Text("Movimientos e intentos", fontWeight = FontWeight.Bold)
                session.exercises.forEach { movement ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(movement.name.ifBlank { "Movimiento" }, fontWeight = FontWeight.Black)
                            val placeholders = (3 - movement.sets.size).coerceAtLeast(0)
                            val sets = (movement.sets + List(placeholders) { idx ->
                                ExerciseSet(id = "placeholder-${movement.id}-$idx")
                            }).take(3)
                            sets.take(3).forEachIndexed { attemptIndex, attempt ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)),
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Intento ${attemptIndex + 1}", fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            EditorMiniField(
                                                label = "Peso",
                                                value = formatEditableNumber(attempt.weight),
                                                keyboardType = KeyboardType.Decimal,
                                                stateKey = "comp-w-${movement.id}-${attempt.id}",
                                                modifier = Modifier.weight(1f),
                                            ) { input ->
                                                val value = input.safeDoubleOrNull()
                                                onUpdateSession { current ->
                                                    current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                        set.copy(weight = value)
                                                    }
                                                }
                                            }
                                            EditorMiniField(
                                                label = "RPE",
                                                value = formatEditableNumber(attempt.targetRPE),
                                                keyboardType = KeyboardType.Decimal,
                                                stateKey = "comp-rpe-${movement.id}-${attempt.id}",
                                                modifier = Modifier.weight(1f),
                                            ) { input ->
                                                val value = input.safeDoubleOrNull()
                                                onUpdateSession { current ->
                                                    current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                        set.copy(targetRPE = value, intensityMode = IntensityMode.RPE)
                                                    }
                                                }
                                            }
                                            EditorMiniField(
                                                label = "Técnica (1-10)",
                                                value = attempt.technicalQuality?.toString().orEmpty(),
                                                keyboardType = KeyboardType.Number,
                                                stateKey = "comp-tech-${movement.id}-${attempt.id}",
                                                modifier = Modifier.weight(1f),
                                            ) { input ->
                                                val quality = input.safeIntOrNull()?.coerceIn(1, 10)
                                                onUpdateSession { current ->
                                                    current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                        set.copy(technicalQuality = quality)
                                                    }
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(
                                                AttemptResult.GOOD to "Aprobado",
                                                AttemptResult.NO_LIFT to "Nulo",
                                                AttemptResult.PENDING to "Pendiente",
                                            ).forEach { (result, label) ->
                                                FilterChip(
                                                    selected = attempt.attemptResult == result,
                                                    onClick = {
                                                        onUpdateSession { current ->
                                                            current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                                set.copy(attemptResult = result)
                                                            }
                                                        }
                                                    },
                                                    label = { Text(label) },
                                                )
                                            }
                                        }

                                        Text("Luces del jurado", style = MaterialTheme.typography.labelSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            repeat(3) { lightIndex ->
                                                val currentValue = attempt.judgingLights.getOrNull(lightIndex)
                                                FilterChip(
                                                    selected = currentValue == true,
                                                    onClick = {
                                                        val next = when (currentValue) {
                                                            true -> false
                                                            false -> null
                                                            null -> true
                                                        }
                                                        onUpdateSession { current ->
                                                            current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                                val mutable = set.judgingLights.toMutableList()
                                                                while (mutable.size < 3) mutable.add(null)
                                                                mutable[lightIndex] = next
                                                                set.copy(judgingLights = mutable)
                                                            }
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            when (currentValue) {
                                                                true -> "L${lightIndex + 1}: B"
                                                                false -> "L${lightIndex + 1}: R"
                                                                null -> "L${lightIndex + 1}: ?"
                                                            }
                                                        )
                                                    },
                                                )
                                            }
                                        }

                                        Text("Molestias", style = MaterialTheme.typography.labelSmall)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            DISCOMFORT_CATALOG.filterNot { it.id == "none" }.forEach { discomfort ->
                                                val selected = discomfort.id in attempt.discomfortIds
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = {
                                                        onUpdateSession { current ->
                                                            current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                                val nextIds = if (selected) {
                                                                    set.discomfortIds - discomfort.id
                                                                } else {
                                                                    set.discomfortIds + discomfort.id
                                                                }
                                                                set.copy(discomfortIds = nextIds)
                                                            }
                                                        }
                                                    },
                                                    label = { Text(discomfort.label) },
                                                )
                                            }
                                        }

                                        EditorMiniField(
                                            label = "Notas técnicas / arbitraje",
                                            value = attempt.refereeNotes.orEmpty(),
                                            stateKey = "comp-notes-${movement.id}-${attempt.id}",
                                        ) { input ->
                                            onUpdateSession { current ->
                                                current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                    set.copy(refereeNotes = input.ifBlank { null })
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Checkbox(
                                                checked = attempt.isFailure,
                                                onCheckedChange = { checked ->
                                                    onUpdateSession { current ->
                                                        current.updateCompetitionSetAtIndex(movement.id, attemptIndex) { set ->
                                                            set.copy(isFailure = checked)
                                                        }
                                                    }
                                                },
                                            )
                                            Text("Intento fallido")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(onClick = onAddCompetitionMovement, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar otro movimiento")
                }
            }
        }
    }
}

private fun competitionReminderSummary(details: CompetitionDetails?): String {
    val competitionDate = details?.competitionDate?.toLocalDateOrNull() ?: return ""
    val today = runCatching { LocalDate.now() }.getOrNull() ?: return ""
    val days = ChronoUnit.DAYS.between(today, competitionDate).toInt()
    if (days < 0) return "Competencia ya pasó (${kotlin.math.abs(days)} días)."
    val reminders = mutableListOf<String>()
    if (details.reminderOneWeekEnabled) reminders += "1 semana"
    if (details.reminder48hEnabled) reminders += "48h"
    val reminderSummary = if (reminders.isEmpty()) {
        "sin recordatorios"
    } else {
        reminders.joinToString(" + ")
    }
    return "Competencia en $days días · Recordatorios: $reminderSummary"
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun Session.updateCompetitionSetAtIndex(
    exerciseId: String,
    setIndex: Int,
    transform: (ExerciseSet) -> ExerciseSet,
): Session {
    return copy(
        exercises = exercises.map { exercise ->
            if (exercise.id != exerciseId) return@map exercise
            val safeIndex = setIndex.coerceAtLeast(0)
            val needed = (safeIndex + 1 - exercise.sets.size).coerceAtLeast(0)
            val baseSets = if (needed == 0) {
                exercise.sets
            } else {
                exercise.sets + List(needed) { ExerciseSet(id = UUID.randomUUID().toString()) }
            }
            exercise.copy(
                sets = baseSets.mapIndexed { index, set -> if (index == safeIndex) transform(set) else set }
            )
        }
    )
}

private fun suggestWarmupReps(percentage: Double): Int = when {
    percentage >= 90.0 -> 1
    percentage >= 85.0 -> 2
    percentage >= 80.0 -> 3
    percentage >= 75.0 -> 4
    percentage >= 70.0 -> 5
    percentage >= 65.0 -> 6
    percentage >= 60.0 -> 8
    percentage >= 50.0 -> 10
    else -> 12
}


// ===== COMPACT COMPONENTS FOR OPTIMIZED EXERCISE EDITOR =====

@Composable
private fun CompactRestPickerButton(
    totalSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .width(96.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showPicker = true },
        color = accentColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = "Descanso",
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                formatRestSummary(totalSeconds),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
    }
    
    if (showPicker) {
        DurationPickerDialog(
            initialTotalSeconds = totalSeconds,
            accentColor = accentColor,
            onDismiss = { showPicker = false },
            onConfirm = {
                onConfirm(it)
                showPicker = false
            },
        )
    }
}

@Composable
private fun CompactModeSelector(
    currentMode: TrainingMode,
    accentColor: Color,
    onModeSelected: (TrainingMode) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box {
        Surface(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { showMenu = true },
            color = accentColor.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trainingModeLabel(currentMode),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 90.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            listOf(
                TrainingMode.REPS to "Reps",
                TrainingMode.RM to "RM",
                TrainingMode.SOLO_RPE to "Solo RPE",
                TrainingMode.TIME to "Tiempo",
                TrainingMode.DISTANCE to "Distancia",
                TrainingMode.CUSTOM to "Personalizado",
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        showMenu = false
                        onModeSelected(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactGoalTrackingButton(
    isActive: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = if (isActive) accentColor.copy(alpha = 0.24f) else accentColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) accentColor.copy(alpha = 0.5f) else accentColor.copy(alpha = 0.2f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Seguimiento de metas",
                tint = if (isActive) Color(0xFFFFB300) else accentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun UnilateralModeSelector(
    mode: UnilateralMode,
    accentColor: Color,
    onModeChanged: (UnilateralMode) -> Unit,
) {
    val modes = UnilateralMode.entries.associateWith { label ->
        when (label) {
            UnilateralMode.BILATERAL -> "Bilateral"
            UnilateralMode.UNILATERAL_PAIRED -> "Uni. Pareado"
            UnilateralMode.UNILATERAL_DIFFERENTIAL -> "Uni. Diferencial"
        }
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true },
            color = if (mode != UnilateralMode.BILATERAL) accentColor.copy(alpha = 0.24f) else accentColor.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (mode != UnilateralMode.BILATERAL) accentColor.copy(alpha = 0.5f) else accentColor.copy(alpha = 0.2f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Modo unilateral",
                    tint = if (mode != UnilateralMode.BILATERAL) MaterialTheme.colorScheme.primary else accentColor,
                    modifier = Modifier.size(18.dp),
                )
                if (mode != UnilateralMode.BILATERAL) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        modes[mode].orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { (m, label) ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onModeChanged(m); expanded = false },
                    leadingIcon = {
                        if (m == mode) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = accentColor)
                    },
                )
            }
        }
    }
}

@Composable
private fun ExerciseSetsCarousel(
    exercise: Exercise,
    reference1RM: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    predictedMetrics: Map<String, Double?>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onAddSet: () -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
) {
    if (exercise.sets.isEmpty()) {
        // Empty state
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "No hay series añadidas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onAddSet,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(48.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Añadir serie",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        return
    }

    var currentSetIndex by remember(exercise.id) { mutableStateOf(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentSetIndex)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(exercise.sets.size) {
        val lastIndex = (exercise.sets.size - 1).coerceAtLeast(0)
        currentSetIndex = currentSetIndex.coerceIn(0, lastIndex)
    }
    LaunchedEffect(listState, exercise.sets.size) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val items = layoutInfo.visibleItemsInfo
                if (items.isEmpty()) return@collect
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = items.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                } ?: return@collect
                currentSetIndex = closest.index.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0))
            }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Carousel using LazyRow
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            state = listState,
        ) {
            itemsIndexed(exercise.sets) { index, set ->
                key(set.id) {
                    val predictedWeight = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM, set) {
                        calculateSuggestedLoad(exercise, set)
                    }
                    val estimatedMetric = predictedMetrics[set.id]

                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                    ) {
                        InlineSetRow(
                            set = set,
                            index = index,
                            reference1RM = reference1RM,
                            predictedWeight = predictedWeight,
                            estimatedMetric = estimatedMetric,
                            trainingMode = trainingMode,
                            customUnit = customUnit,
                            accentColor = accentColor,
                            canMoveUp = index > 0,
                            canMoveDown = index < exercise.sets.size - 1,
                            onUpdate = { updater -> onUpdateSet(set.id, updater) },
                            onRemove = { onRemoveSet(set.id) },
                            onMoveUp = { onMoveSet(set.id, -1) },
                            onMoveDown = { onMoveSet(set.id, 1) },
                        )
                    }
                }
            }
            item("add-set") {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(start = 4.dp, end = 16.dp)
                            .size(48.dp)
                            .clickable { onAddSet() },
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Añadir serie",
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dot stepper indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.sets.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentSetIndex) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentSetIndex) accentColor else accentColor.copy(alpha = 0.35f),
                            )
                            .clickable {
                                currentSetIndex = index
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                    )
                }
            }
            Text(
                "${currentSetIndex + 1}/${exercise.sets.size}",
                modifier = Modifier.align(Alignment.CenterEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EstimatedRingsRow(
    energy: Int,
    spine: Int,
) {
    val energyProgress = (energy.coerceIn(0, 100) / 100f)
    val spineProgress = (spine.coerceIn(0, 100) / 100f)
    val ringColors = listOf(Color(0xFF448AFF), Color(0xFFFFD740))
    val ringLabels = listOf("ENERGÍA", "COLUMNA")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(energyProgress to ringLabels[0], spineProgress to ringLabels[1]).forEachIndexed { index, entry ->
            val (progress, label) = entry
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SingleRingCanvas(
                    value = progress,
                    color = ringColors[index],
                    ringDiameter = 120f,
                    strokeWidth = 8f,
                )
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = ringColors[index])
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class SessionMuscleGroup(val label: String, val muscles: List<String>)

private enum class SessionAnalyticsScope(val label: String) {
    CURRENT("Sesión actual"),
    WEEK("Semana"),
}

private val SESSION_MUSCLE_GROUPS = listOf(
    SessionMuscleGroup("Pecho", listOf("Pectorales")),
    SessionMuscleGroup("Espalda", listOf("Dorsales", "Trapecio", "Erectores Espinales")),
    SessionMuscleGroup("Hombros", listOf("Deltoides")),
    SessionMuscleGroup("Brazos", listOf("Bíceps", "Tríceps", "Antebrazo")),
    SessionMuscleGroup("Core", listOf("Abdomen", "Core")),
    SessionMuscleGroup("Piernas", listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Aductores", "Pantorrillas")),
)

private fun computeSessionRoleWeightedSets(
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val exercises = session.allExercises()
    exercises.forEach { exercise ->
        val dbEntry = exercise.exerciseDbId?.lowercase()?.let(exerciseIndex::get)
            ?: exercise.exerciseId?.lowercase()?.let(exerciseIndex::get)
            ?: exerciseIndex.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
            ?: return@forEach
        val effectiveSetCount = exercise.sets.count { !it.isIneffective }.coerceAtLeast(1)
        dbEntry.involvedMuscles.forEach { involvement ->
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val roleMultiplier = resolveMuscleVolumeContribution(involvement)
            val weighted = effectiveSetCount * roleMultiplier
            result[canonical] = (result[canonical] ?: 0.0) + weighted
        }
    }
    return result
}

private fun computePredictedMuscleBatteries(
    session: Session,
    roleWeightedSets: Map<String, Double>,
    predictedMuscularDrain: Int,
): Map<String, Int> {
    if (roleWeightedSets.isEmpty()) return emptyMap()
    val totalRoleWeight = roleWeightedSets.values.sum().takeIf { it > 0.0 } ?: return emptyMap()
    val muscleCount = roleWeightedSets.size.coerceAtLeast(1)
    val totalSets = session.allExercises().sumOf { exercise ->
        exercise.sets.count { !it.isIneffective }
    }.coerceAtLeast(1)
    val avgSessionRest = session.allExercises().mapNotNull { it.restTime }.ifEmpty { listOf(90) }.average()
    val densityFactor = when {
        avgSessionRest <= 45.0 -> 1.16
        avgSessionRest <= 75.0 -> 1.10
        avgSessionRest >= 210.0 -> 0.92
        avgSessionRest >= 150.0 -> 0.96
        else -> 1.0
    }
    val progressionFactor = (1.0 + ((totalSets - 4).coerceAtLeast(0) / 14.0) * 0.22)
        .coerceIn(1.0, 1.30)
    val supersetFactor = if (session.allExercises().any { !it.supersetId.isNullOrBlank() }) 1.08 else 1.0
    val expectedDrop = predictedMuscularDrain.coerceIn(0, 100).toDouble()
    val adjustedExpectedDrop = (expectedDrop * densityFactor * progressionFactor * supersetFactor).coerceAtMost(100.0)

    return roleWeightedSets.mapValues { (_, weight) ->
        val share = (weight / totalRoleWeight).coerceIn(0.0, 1.0)
        val relativeShare = share * muscleCount.toDouble()
        val roleFactor = (0.60 + (0.40 * relativeShare)).coerceIn(0.45, 1.55)
        val modeledDrop = (adjustedExpectedDrop * roleFactor).coerceIn(0.0, 100.0)
        (100.0 - modeledDrop).roundToInt().coerceIn(0, 100)
    }
}

private fun computePredictedMuscleBatteriesFromVolumeMap(
    volumeByMuscle: Map<String, Double>,
    predictedMuscularDrain: Int,
): Map<String, Int> {
    if (volumeByMuscle.isEmpty()) return emptyMap()
    val totalVolume = volumeByMuscle.values.sum().takeIf { it > 0.0 } ?: return emptyMap()
    val expectedDrop = predictedMuscularDrain.coerceIn(0, 100).toDouble()
    return volumeByMuscle.mapValues { (_, sets) ->
        val share = (sets / totalVolume).coerceIn(0.0, 1.0)
        val modeledDrop = (expectedDrop * (0.65 + share * 0.9)).coerceIn(0.0, 100.0)
        (100.0 - modeledDrop).roundToInt().coerceIn(0, 100)
    }
}

private fun thresholdForScope(
    threshold: SessionEditorVolumeThreshold?,
    scope: SessionAnalyticsScope,
): Triple<Double, Double, Double>? {
    threshold ?: return null
    return when (scope) {
        SessionAnalyticsScope.CURRENT -> Triple(threshold.sessionMev, threshold.sessionMav, threshold.sessionMrv)
        SessionAnalyticsScope.WEEK -> Triple(threshold.weeklyMev, threshold.weeklyMav, threshold.weeklyMrv)
    }
}

@Composable
private fun PredictedMuscleBatterySection(perMuscle: Map<String, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Batería restante por músculo",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
        )
        val entries = perMuscle.entries.sortedBy { it.value }
        entries.forEach { (muscle, score) ->
            val color = when {
                score >= 80 -> Color(0xFF22C55E)
                score >= 50 -> Color(0xFFFACC15)
                else -> Color(0xFFEF4444)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    muscle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(max = 118.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                )
                Text(
                    "$score%",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun AddSetGhostCard(onAddSet: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAddSet() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Añadir serie",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Agregar serie",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
