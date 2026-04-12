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
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateWeightFrom1RMAndIntensity
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

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
    val session = uiState.session
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoExpandExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

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

    val isCompactHeader by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160
        }
    }
    val partBounds = remember { mutableMapOf<String, Rect>() }
    val partContentBounds = remember { mutableMapOf<String, Rect>() }
    val exerciseBounds = remember { mutableMapOf<String, Rect>() }
    var draggingPartId by remember { mutableStateOf<String?>(null) }
    var draggingPartOffsetY by remember { mutableStateOf(0f) }
    var partDropTargetId by remember { mutableStateOf<String?>(null) }
    var draggingExerciseId by remember { mutableStateOf<String?>(null) }
    var draggingExercisePartId by remember { mutableStateOf<String?>(null) }
    var draggingExerciseOffset by remember { mutableStateOf(Offset.Zero) }
    var exerciseDropTargetKey by remember { mutableStateOf<String?>(null) }
    var exerciseDropTargetPartId by remember { mutableStateOf<String?>(null) }

    if (session == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LinearProgressIndicator(modifier = Modifier.width(180.dp))
        }
        return
    }
    val groupedParts = session.parts.filterNot { it.isUncategorized() }

    BackHandler(enabled = !showDiscardDialog && uiState.sheet == SessionEditorSheet.NONE) {
        if (uiState.hasUnsavedChanges) showDiscardDialog = true else onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) { KpknSnackbar(it) } },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                HeroGlassFab(
                    summary = uiState.augeSummary,
                    onClick = { viewModel.openSheet(SessionEditorSheet.AUGE) },
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            bottomBar = {
                SessionContextNavigator(
                    sessions = uiState.siblingSessions,
                    selectedSessionId = uiState.selectedSiblingSessionId ?: session.id,
                    onSelectSession = viewModel::requestSessionSwitch,
                    weekStartDay = uiState.weekStartDay,
                    activeDayOfWeek = uiState.dayOfWeek,
                    onCreateSessionForDay = { day ->
                        val result = viewModel.createSessionForDay(day)
                        scope.launch {
                            snackbarHostState.showKpknSnackbar(
                                result.message,
                                if (result.success) SnackbarType.SUCCESS else SnackbarType.DANGER,
                            )
                        }
                    },
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
                    onNameChange = viewModel::updateSessionName,
                    onDescriptionChange = viewModel::updateSessionDescription,
                    onMeetDayChange = viewModel::updateSessionMeetDay,
                    onMeetBodyweightChange = viewModel::updateSessionMeetBodyweight,
                    onClose = {
                        if (uiState.hasUnsavedChanges) showDiscardDialog = true else onBack()
                    },
                    onSave = { viewModel.openSheet(SessionEditorSheet.SAVE) },
                    onOpenBackgroundSheet = { viewModel.openSheet(SessionEditorSheet.BACKGROUND) },
                    onOpenTransfer = { viewModel.openSheet(SessionEditorSheet.TRANSFER) },
                    onOpenHistory = { viewModel.openSheet(SessionEditorSheet.HISTORY) },
                    onOpenRules = { viewModel.openSheet(SessionEditorSheet.RULES) },
                )
            }

            item {
                SessionOverviewStrip(
                    session = session,
                    uiState = uiState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (session.exercises.isNotEmpty()) {
                item("loose-exercises") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
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
                                    isDropTarget = exerciseDropTargetKey == "__loose__|${exercise.id}" && draggingExerciseId != exercise.id,
                                    isPartDropTarget = exerciseDropTargetPartId == "__loose__" && draggingExerciseId != exercise.id,
                                    onBoundsChange = { rect -> exerciseBounds["__loose__|${exercise.id}"] = rect },
                                    onDragStart = {
                                        draggingExerciseId = exercise.id
                                        draggingExercisePartId = "__loose__"
                                        draggingExerciseOffset = Offset.Zero
                                        exerciseDropTargetKey = null
                                        exerciseDropTargetPartId = null
                                    },
                                    onDrag = { delta ->
                                        val activeExerciseId = draggingExerciseId ?: return@ExerciseEditorCard
                                        val currentPartId = draggingExercisePartId ?: return@ExerciseEditorCard
                                        draggingExerciseOffset += delta
                                        val activeRect = exerciseBounds["$currentPartId|$activeExerciseId"] ?: return@ExerciseEditorCard
                                        val center = Offset(activeRect.center.x + draggingExerciseOffset.x, activeRect.center.y + draggingExerciseOffset.y)
                                        val targetExerciseKey = exerciseBounds.entries.firstOrNull { (key, rect) ->
                                            key != "$currentPartId|$activeExerciseId" && rect.contains(center)
                                        }?.key
                                        if (targetExerciseKey != null) {
                                            exerciseDropTargetKey = targetExerciseKey
                                            val targetPartId = targetExerciseKey.substringBefore("|")
                                            val targetExerciseId = targetExerciseKey.substringAfter("|")
                                            val targetIdx = when (targetPartId) {
                                                "__loose__" -> session.exercises.indexOfFirst { it.id == targetExerciseId }
                                                else -> session.parts.firstOrNull { it.id == targetPartId }?.exercises?.indexOfFirst { it.id == targetExerciseId } ?: -1
                                            }
                                            if (targetIdx >= 0) {
                                                viewModel.moveExerciseToPart(
                                                    sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                    exerciseId = activeExerciseId,
                                                    targetPartId = targetPartId.takeUnless { it == "__loose__" },
                                                    targetIndex = targetIdx,
                                                )
                                                draggingExercisePartId = targetPartId
                                                draggingExerciseOffset = Offset.Zero
                                            }
                                        } else {
                                            val targetPartId = groupedParts.firstOrNull { candidate ->
                                                partContentBounds[candidate.id]?.contains(center) == true
                                            }?.id
                                            exerciseDropTargetPartId = targetPartId
                                            if (targetPartId != null && targetPartId != currentPartId) {
                                                viewModel.moveExerciseToPart(
                                                    sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                    exerciseId = activeExerciseId,
                                                    targetPartId = targetPartId,
                                                    targetIndex = null,
                                                )
                                                draggingExercisePartId = targetPartId
                                                draggingExerciseOffset = Offset.Zero
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingExerciseId = null
                                        draggingExercisePartId = null
                                        draggingExerciseOffset = Offset.Zero
                                        exerciseDropTargetKey = null
                                        exerciseDropTargetPartId = null
                                    },
                                    onUpdateExercise = { updater -> viewModel.updateExercise(null, exercise.id, updater) },
                                    onAddSet = { viewModel.addSet(null, exercise.id) },
                                    onUpdateSet = { setId, updater -> viewModel.updateSet(null, exercise.id, setId, updater) },
                                    onRemoveSet = { setId -> viewModel.removeSet(null, exercise.id, setId) },
                                    onMoveSet = { setId, dir -> viewModel.moveSet(null, exercise.id, setId, dir) },
                                    onOpenQuickActions = { viewModel.openExerciseQuickActions(null, exercise.id) },
                                    autoExpand = pendingAutoExpandExerciseId == exercise.id,
                                    onAutoExpandHandled = {
                                        if (pendingAutoExpandExerciseId == exercise.id) pendingAutoExpandExerciseId = null
                                    },
                                )
                            }
                            AnimatedVisibility(
                                visible = draggingExerciseId != null && exerciseDropTargetKey == "__loose__|${exercise.id}",
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .padding(horizontal = 16.dp)
                                        .drawWithContent {
                                            drawContent()
                                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                            drawRoundRect(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                                style = Stroke(width = 2f, pathEffect = dashEffect),
                                                cornerRadius = CornerRadius(12.dp.toPx()),
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

            itemsIndexed(groupedParts, key = { _, part -> part.id }) { _, part ->
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
                            val currentIndex = groupedParts.indexOfFirst { it.id == activeId }
                            val targetIndex = groupedParts.indexOfFirst { it.id == targetId }
                            if (currentIndex != -1 && targetIndex != -1 && currentIndex != targetIndex) {
                                viewModel.movePartToIndex(activeId, targetIndex)
                                draggingPartOffsetY = 0f
                            }
                        }
                    },
                    onDragEnd = {
                        draggingPartId = null
                        draggingPartOffsetY = 0f
                        partDropTargetId = null
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
                                            isDropTarget = exerciseDropTargetKey == "${part.id}|${exercise.id}" && draggingExerciseId != exercise.id,
                                            isPartDropTarget = exerciseDropTargetPartId == part.id && draggingExerciseId != exercise.id,
                                            onBoundsChange = { rect -> exerciseBounds["${part.id}|${exercise.id}"] = rect },
                                            onDragStart = {
                                                draggingExerciseId = exercise.id
                                                draggingExercisePartId = part.id
                                                draggingExerciseOffset = Offset.Zero
                                                exerciseDropTargetKey = null
                                                exerciseDropTargetPartId = null
                                            },
                                            onDrag = { delta ->
                                                val activeExerciseId = draggingExerciseId ?: return@ExerciseEditorCard
                                                val currentPartId = draggingExercisePartId ?: return@ExerciseEditorCard
                                                draggingExerciseOffset += delta
                                                val activeRect = exerciseBounds["$currentPartId|$activeExerciseId"] ?: return@ExerciseEditorCard
                                                val center = Offset(activeRect.center.x + draggingExerciseOffset.x, activeRect.center.y + draggingExerciseOffset.y)
                                                val targetExerciseKey = exerciseBounds.entries.firstOrNull { (key, rect) ->
                                                    key != "$currentPartId|$activeExerciseId" && rect.contains(center)
                                                }?.key
                                                if (targetExerciseKey != null) {
                                                    exerciseDropTargetKey = targetExerciseKey
                                                    val targetPartId = targetExerciseKey.substringBefore("|")
                                                    val targetExerciseId = targetExerciseKey.substringAfter("|")
                                                    val targetIdx = when (targetPartId) {
                                                        "__loose__" -> session.exercises.indexOfFirst { it.id == targetExerciseId }
                                                        else -> session.parts.firstOrNull { it.id == targetPartId }?.exercises?.indexOfFirst { it.id == targetExerciseId } ?: -1
                                                    }
                                                    if (targetIdx >= 0) {
                                                        viewModel.moveExerciseToPart(
                                                            sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                            exerciseId = activeExerciseId,
                                                            targetPartId = targetPartId.takeUnless { it == "__loose__" },
                                                            targetIndex = targetIdx,
                                                        )
                                                        draggingExercisePartId = targetPartId
                                                        draggingExerciseOffset = Offset.Zero
                                                    }
                                                } else {
                                                    val targetPartId = groupedParts.firstOrNull { candidate ->
                                                        partContentBounds[candidate.id]?.contains(center) == true
                                                    }?.id
                                                    exerciseDropTargetPartId = targetPartId
                                                    if (targetPartId != null && targetPartId != currentPartId) {
                                                        viewModel.moveExerciseToPart(
                                                            sourcePartId = currentPartId.takeUnless { it == "__loose__" },
                                                            exerciseId = activeExerciseId,
                                                            targetPartId = targetPartId,
                                                            targetIndex = null,
                                                        )
                                                        draggingExercisePartId = targetPartId
                                                        draggingExerciseOffset = Offset.Zero
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingExerciseId = null
                                                draggingExercisePartId = null
                                                draggingExerciseOffset = Offset.Zero
                                                exerciseDropTargetKey = null
                                                exerciseDropTargetPartId = null
                                            },
                                            onUpdateExercise = { updater -> viewModel.updateExercise(part.id, exercise.id, updater) },
                                            onAddSet = { viewModel.addSet(part.id, exercise.id) },
                                            onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, exercise.id, setId, updater) },
                                            onRemoveSet = { setId -> viewModel.removeSet(part.id, exercise.id, setId) },
                                            onMoveSet = { setId, dir -> viewModel.moveSet(part.id, exercise.id, setId, dir) },
                                            onOpenQuickActions = { viewModel.openExerciseQuickActions(part.id, exercise.id) },
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

            item {
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
        onExportToSession = viewModel::exportToSession,
        onImportFromSession = viewModel::importFromSession,
        onSave = { saveScope ->
            val saveResult = viewModel.saveSession(saveScope)
            scope.launch {
                if (saveResult.success) {
                    snackbarHostState.showKpknSnackbar(saveResult.message, SnackbarType.SUCCESS)
                    onBack()
                } else {
                    snackbarHostState.showKpknSnackbar(saveResult.message, SnackbarType.DANGER)
                }
            }
        },
        onApplyAugeCorrection = { alertId ->
            viewModel.applyAugeCorrection(alertId)
            scope.launch {
                snackbarHostState.showKpknSnackbar("Ajuste AUGE aplicado", SnackbarType.SUCCESS)
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
        onQuickActionOpenPicker = viewModel::triggerQuickActionOpenPicker,
        onQuickActionOpenWarmup = viewModel::triggerQuickActionOpenWarmup,
        onQuickActionDelete = viewModel::triggerQuickActionDelete,
        onQuickActionLinkSuperset = viewModel::triggerQuickActionLinkSuperset,
        onQuickActionUnlinkSuperset = viewModel::triggerQuickActionUnlinkSuperset,
        onOpenSupersetManager = viewModel::openSupersetManager,
        onUpdateSupersetRestBetween = viewModel::updateSupersetRestBetween,
        onUpdateSupersetRestAfter = viewModel::updateSupersetRestAfter,
        onRemoveFromSuperset = viewModel::removeFromSuperset,
    )

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Salir del editor", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tienes cambios sin guardar. Puedes guardar un borrador local antes de salir.")
                    OutlinedButton(
                        onClick = {
                            showDiscardDialog = false
                            viewModel.discardDraftForCurrentSession()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Salir sin guardar")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val saved = viewModel.saveDraftForExit()
                    scope.launch {
                        if (saved) {
                            snackbarHostState.showKpknSnackbar("Borrador guardado", SnackbarType.SUCCESS)
                            showDiscardDialog = false
                            onBack()
                        } else {
                            snackbarHostState.showKpknSnackbar("No pudimos guardar el borrador", SnackbarType.DANGER)
                        }
                    }
                }) { Text("Guardar borrador y salir") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Continuar editando") }
            },
        )
    }
}

@Composable
private fun SessionHero(
    session: Session,
    hasChanges: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMeetDayChange: (Boolean) -> Unit,
    onMeetBodyweightChange: (Double?) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onOpenBackgroundSheet: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
) {
    var descriptionExpanded by rememberSaveable(session.id) { mutableStateOf(false) }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = dayLabel(session.dayOfWeek),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroGlassIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Salir",
                        onClick = onClose,
                    )
                    HeroGlassIconButton(
                        icon = Icons.Default.Save,
                        contentDescription = "Guardar sesión",
                        onClick = onSave,
                        showUnsavedDot = hasChanges,
                    )
                    HeroGlassIconButton(
                        icon = Icons.Default.Palette,
                        contentDescription = "Editar fondo",
                        onClick = onOpenBackgroundSheet,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = session.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre de la sesión", color = Color.White.copy(alpha = 0.72f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
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

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { descriptionExpanded = !descriptionExpanded },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.20f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.18f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (session.description.isNullOrBlank()) "Añadir descripción" else "Descripción",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = if (descriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (descriptionExpanded) "Ocultar descripción" else "Mostrar descripción",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                AnimatedVisibility(descriptionExpanded) {
                    OutlinedTextField(
                        value = session.description.orEmpty(),
                        onValueChange = onDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descripción", color = Color.White.copy(alpha = 0.72f)) },
                        minLines = 1,
                        maxLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeroActionIcon(
                            icon = Icons.Default.SwapHoriz,
                            contentDescription = "Transferir",
                            onClick = onOpenTransfer,
                        )
                        HeroActionIcon(
                            icon = Icons.Default.History,
                            contentDescription = "Historial",
                            onClick = onOpenHistory,
                        )
                        HeroActionIcon(
                            icon = Icons.Default.Settings,
                            contentDescription = "Reglas",
                            onClick = onOpenRules,
                        )
                        HeroActionIcon(
                            icon = Icons.Default.WorkspacePremium,
                            contentDescription = "Modo competición",
                            onClick = { onMeetDayChange(!session.isMeetDay) },
                            selected = session.isMeetDay,
                        )
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
private fun SessionMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun SessionOverviewStrip(
    session: Session,
    uiState: SessionEditorUiState,
    modifier: Modifier = Modifier,
) {
    val totalExercises = remember(session) { session.allExercises().size }
    val totalSets = remember(session) { session.allExercises().sumOf { it.sets.size } }
    val estimatedMinutes = uiState.estimatedDurationMinutes
    val drainPercent = uiState.augeSummary.combinedSessionDrainPercent()

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionMetricCard(
            label = "Ejercicios",
            value = totalExercises.toString(),
            modifier = Modifier.width(104.dp),
        )
        SessionMetricCard(
            label = "Series",
            value = totalSets.toString(),
            modifier = Modifier.width(94.dp),
        )
        SessionMetricCard(
            label = "Duración est.",
            value = "${estimatedMinutes} min",
            modifier = Modifier.width(118.dp),
        )
        SessionMetricCard(
            label = "Estrés AUGE",
            value = "${drainPercent}%",
            modifier = Modifier.width(118.dp),
        )
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
private fun HeroGlassFab(
    summary: SessionEditorAugeSummary,
    onClick: () -> Unit,
) {
    val pulseTransition = rememberInfiniteTransition(label = "auge-fab-pulse")
    val pulsingScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auge-fab-scale",
    )
    val accentColor = augeStatusColor(summary.status, summary.hasCriticalAlerts)
    val scale = if (summary.hasCriticalAlerts) pulsingScale else 1f
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = accentColor,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Abrir Asistente de sesión",
                    modifier = Modifier.size(20.dp),
                )
                if (summary.alertCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp),
                        shape = CircleShape,
                        color = accentColor,
                    ) {
                        Text(
                            text = summary.alertCount.toString(),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Text(
                text = "Asistente de sesión",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun SessionContextNavigator(
    sessions: List<Session>,
    selectedSessionId: String,
    onSelectSession: (String) -> Unit,
    weekStartDay: Int,
    activeDayOfWeek: Int?,
    onCreateSessionForDay: (Int) -> Unit,
) {
    val orderedDays = remember(weekStartDay) {
        val safeStart = weekStartDay.coerceIn(1, 7)
        val base = listOf(1, 2, 3, 4, 5, 6, 7)
        val offset = safeStart - 1
        base.drop(offset) + base.take(offset)
    }
    var selectedDay by remember(activeDayOfWeek, orderedDays) {
        mutableStateOf(activeDayOfWeek ?: orderedDays.first())
    }

    val sessionsForSelectedDay = remember(sessions, selectedDay) {
        sessions.filter { it.dayOfWeek == selectedDay }
    }

    Surface(
        shadowElevation = 14.dp,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                orderedDays.forEach { day ->
                    val hasSession = sessions.any { it.dayOfWeek == day }
                    val selectedDayChip = selectedDay == day
                    FilterChip(
                        selected = selectedDayChip,
                        onClick = {
                            selectedDay = day
                            sessions.firstOrNull { it.dayOfWeek == day }?.let { onSelectSession(it.id) }
                        },
                        label = { Text(dayLabelShort(day)) },
                        leadingIcon = if (hasSession) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        } else null,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (sessionsForSelectedDay.isEmpty()) {
                        "Sin sesión en ${dayLabelShort(selectedDay)}"
                    } else {
                        "${sessionsForSelectedDay.size} sesión${if (sessionsForSelectedDay.size > 1) "es" else ""} en ${dayLabelShort(selectedDay)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sessionsForSelectedDay.isEmpty()) {
                    AssistChip(
                        onClick = { onCreateSessionForDay(selectedDay) },
                        label = { Text("Agregar") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Mantén pulsado para reordenar grupo",
                        tint = partColor.copy(alpha = if (isDragging) 0.92f else 0.56f),
                        modifier = Modifier.size(18.dp),
                    )
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(partColor)
                            .clickable { showColorPicker = !showColorPicker },
                    )
                    if (part.isUncategorized()) {
                        Text(
                            "Sin grupo",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = partColor,
                        )
                    } else {
                        OutlinedTextField(
                            value = part.name,
                            onValueChange = onRename,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = partColor,
                                unfocusedBorderColor = partColor.copy(alpha = 0.4f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = partColor,
                            ),
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
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
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar ejercicio", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar grupo", fontWeight = FontWeight.Black) },
            text = { Text("Antes de borrar el grupo, decide qué hacer con los ejercicios que contiene.") },
            confirmButton = {
                FilledTonalButton(onClick = {
                    showDeleteConfirm = false
                    showDeleteModeConfirm = true
                }) {
                    Text("Continuar")
                }
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
    onOpenQuickActions: () -> Unit,
    autoExpand: Boolean,
    onAutoExpandHandled: () -> Unit,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }

    val resolved1RM = remember(exercise.reference1RM, exercise.prFor1RM) {
        when {
            exercise.reference1RM != null -> exercise.reference1RM
            exercise.prFor1RM != null -> calculateHybrid1RM(exercise.prFor1RM.weight, exercise.prFor1RM.reps)
            else -> null
        }
    }
    var rmInputMode by remember(exercise.id, exercise.prFor1RM) {
        mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct")
    }
    val initialLoad = exercise.consolidatedWeight?.weightKg ?: exercise.sets.firstOrNull()?.weight
    var restSelectionSeconds by rememberSaveable(exercise.id) { mutableStateOf(exercise.restTime ?: 90) }
    var directRmInput by rememberSaveable(exercise.id) { mutableStateOf(formatEditableNumber(exercise.reference1RM)) }
    var prWeightInput by rememberSaveable(exercise.id) { mutableStateOf(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prRepsInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()) }
    var initialLoadInput by rememberSaveable(exercise.id) { mutableStateOf(formatEditableNumber(initialLoad)) }
    var customUnitInput by rememberSaveable(exercise.id) { mutableStateOf(exercise.customUnit.orEmpty()) }
    var goalRmInput by rememberSaveable(exercise.id) { mutableStateOf(formatEditableNumber(exercise.goal1RM)) }
    val localPrEstimatedRm = remember(prWeightInput, prRepsInput) {
        val weight = prWeightInput.safeDoubleOrNull()
        val reps = prRepsInput.safeIntOrNull()
        if (weight != null && weight > 0 && reps != null && reps > 0) calculateHybrid1RM(weight, reps) else null
    }
    val accentColor = remember(accentHex) {
        runCatching { Color(AndroidColor.parseColor(accentHex ?: PART_COLORS.first())) }.getOrDefault(Color(0xFF00F0FF))
    }
    val predictedWeights = remember(exercise.reference1RM, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to exercise.reference1RM?.let { reference ->
                calculateWeightFrom1RMAndIntensity(reference, set)
            }
        }
    }

    LaunchedEffect(exercise.id, exercise.restTime) { restSelectionSeconds = exercise.restTime ?: 90 }
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
            .pointerInput(partId, exercise.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragCancel = { onDragEnd() },
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(Offset(dragAmount.x, dragAmount.y))
                    },
                )
            }
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
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    onLongClick = onOpenQuickActions,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Mantén pulsado para reordenar ejercicio",
                tint = accentColor.copy(alpha = if (isDragging) 0.9f else 0.48f),
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
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
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!expanded) {
                        Text(
                            text = "Mantén o toca ⋯",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onOpenQuickActions, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "Acciones rápidas",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Plegar" else "Desplegar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

                // Compact rest + mode + goal tracking
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Rest picker with timer icon only
                    CompactRestPickerButton(
                        totalSeconds = restSelectionSeconds,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
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
                }

                // Initial load only for REPS mode
                if (exercise.trainingMode == TrainingMode.REPS) {
                    EditorMiniField(
                        label = "Carga inicial (opcional)",
                        value = initialLoadInput,
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) { input ->
                        initialLoadInput = input
                        val load = input.safeDoubleOrNull()
                        onUpdateExercise { current ->
                            current.copy(
                                consolidatedWeight = load?.let { ConsolidatedWeight(it, current.sets.firstOrNull()?.targetReps ?: 1) },
                                sets = current.sets.map { set -> set.copy(weight = load) },
                            )
                        }
                    }
                }

                if (exercise.trainingMode == TrainingMode.CUSTOM) {
                    EditorMiniField(
                        label = "Unidad personalizada",
                        value = customUnitInput,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        customUnitInput = it
                        onUpdateExercise { current -> current.copy(customUnit = it.ifBlank { null }) }
                    }
                }

                // 1RM reference (percent mode)
                if (exercise.trainingMode == TrainingMode.PERCENT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleToken("1RM directo", rmInputMode == "direct") { rmInputMode = "direct" }
                        ToggleToken("Desde PR", rmInputMode == "pr") { rmInputMode = "pr" }
                    }
                    if (rmInputMode == "direct") {
                        EditorMiniField(
                            label = "1RM referencial",
                            value = directRmInput,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            directRmInput = it
                            onUpdateExercise { current -> current.copy(reference1RM = it.safeDoubleOrNull()) }
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
                                if (weight != null && weight > 0 && reps != null && reps > 0) {
                                    onUpdateExercise { current ->
                                        current.copy(
                                            prFor1RM = PrReference(weight, reps),
                                            reference1RM = calculateHybrid1RM(weight, reps),
                                        )
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
                                if (weight != null && weight > 0 && reps != null && reps > 0) {
                                    onUpdateExercise { current ->
                                        current.copy(
                                            prFor1RM = PrReference(weight, reps),
                                            reference1RM = calculateHybrid1RM(weight, reps),
                                        )
                                    }
                                }
                            }
                        }
                        if (localPrEstimatedRm != null) {
                            Text(
                                "1RM calculado: ${formatEditableNumber(localPrEstimatedRm)} kg",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Goal tracking details - only visible when star is active
                AnimatedVisibility(exercise.isStarTarget) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EditorMiniField(
                            label = "Meta 1RM kg (opcional)",
                            value = goalRmInput,
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.fillMaxWidth(),
                        ) { input ->
                            goalRmInput = input
                            onUpdateExercise { ex -> ex.copy(goal1RM = input.safeDoubleOrNull()) }
                        }
                        // Show PR and goal info
                        if (exercise.prFor1RM != null || exercise.goal1RM != null) {
                            Text(
                                buildString {
                                    val prText = exercise.prFor1RM?.let { "PR: ${formatEditableNumber(it.weight)} kg × ${it.reps}" }
                                    val goalText = exercise.goal1RM?.let { "Meta: ${formatEditableNumber(it)} kg" }
                                    append(listOfNotNull(prText, goalText).joinToString(" · "))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Sets section
                Text("Series", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercise.sets.forEachIndexed { setIndex, set ->
                        InlineSetRow(
                            set = set,
                            index = setIndex,
                            reference1RM = resolved1RM,
                            predictedWeight = predictedWeights[set.id],
                            trainingMode = exercise.trainingMode,
                            customUnit = exercise.customUnit,
                            accentColor = accentColor,
                            canMoveUp = setIndex > 0,
                            canMoveDown = setIndex < exercise.sets.lastIndex,
                            onUpdate = { updater -> onUpdateSet(set.id, updater) },
                            onRemove = { onRemoveSet(set.id) },
                            onMoveUp = { onMoveSet(set.id, -1) },
                            onMoveDown = { onMoveSet(set.id, 1) },
                        )
                    }
                    FilledTonalButton(onClick = onAddSet) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Serie", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineSetRow(
    set: ExerciseSet,
    index: Int,
    reference1RM: Double?,
    predictedWeight: Double?,
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
    val isRmMode = trainingMode == TrainingMode.PERCENT
    val isAmrapMode = set.isAmrap
    val sliderPercent = remember(set.targetPercentageRM, set.targetReps, set.intensityMode, predictedWeight, reference1RM) {
        when {
            set.intensityMode == IntensityMode.SOLO_RM && set.targetPercentageRM != null -> set.targetPercentageRM
            predictedWeight != null && reference1RM != null && reference1RM > 0 -> ((predictedWeight / reference1RM) * 100.0).coerceIn(40.0, 100.0)
            set.targetReps != null -> estimatePercent1RM(set.targetReps)
            else -> 75.0
        }
    }
    val displayedWeight = when {
        reference1RM == null -> null
        set.intensityMode == IntensityMode.SOLO_RM -> ((reference1RM * sliderPercent / 100.0) * 4).toInt() / 4.0
        else -> predictedWeight
    }
    val metricLabel = when (trainingMode) {
        TrainingMode.PERCENT, TrainingMode.REPS -> if (isAmrapMode) "Reps mín." else "Reps"
        TrainingMode.TIME -> if (isAmrapMode) "Tiempo mín." else "Tiempo"
        TrainingMode.DISTANCE -> if (isAmrapMode) "Dist. mín." else "Dist."
        TrainingMode.CUSTOM -> if (isAmrapMode) "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} mín." else (customUnit?.ifBlank { "Unidad" } ?: "Unidad")
    }
    val metricValue = when (trainingMode) {
        TrainingMode.TIME -> set.targetDuration?.toString().orEmpty()
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
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(if (isNarrowScreen) 24.dp else 28.dp)) {
                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(if (isNarrowScreen) 14.dp else 15.dp))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(if (isNarrowScreen) 24.dp else 28.dp)) {
                    Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(if (isNarrowScreen) 14.dp else 15.dp))
                }
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
                if (!isAmrapMode) {
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
                                    if (isRmMode) IntensityMode.SOLO_RM to "Solo RM" else null,
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
                                                    IntensityMode.SOLO_RM -> it.copy(intensityMode = IntensityMode.SOLO_RM, isFailure = false, targetPercentageRM = it.targetPercentageRM ?: estimatePercent1RM(it.targetReps ?: 1), targetRIR = null, targetRPE = null)
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
                            keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE || (set.intensityMode ?: IntensityMode.RPE) == IntensityMode.SOLO_RM) KeyboardType.Decimal else KeyboardType.Number,
                            modifier = Modifier.weight(if (isNarrowScreen) 0.82f else 0.9f),
                        ) { input ->
                            onUpdate { current ->
                                when (current.intensityMode ?: IntensityMode.RPE) {
                                    IntensityMode.RPE -> current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                                    IntensityMode.RIR -> current.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                                    IntensityMode.SOLO_RM -> current.copy(targetPercentageRM = input.safeDoubleOrNull(), intensityMode = IntensityMode.SOLO_RM)
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

            if (isRmMode) {
                Surface(
                    shape = RoundedCornerShape(if (isNarrowScreen) 16.dp else 20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(if (isNarrowScreen) 10.dp else 14.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 8.dp else 10.dp),
                    ) {
                        Text(
                            "Carga estimada",
                            style = if (isNarrowScreen) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 8.dp else 12.dp),
                        ) {
                            Text(
                                text = displayedWeight?.let { "${"%.1f".format(it)} kg" } ?: "Sin referencia",
                                style = if (isNarrowScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = reference1RM?.let { "${sliderPercent.toInt()}% RM" } ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = sliderPercent.toFloat(),
                            onValueChange = { onUpdate { current -> current.copy(targetPercentageRM = it.toDouble(), intensityMode = if (current.intensityMode == IntensityMode.SOLO_RM) IntensityMode.SOLO_RM else current.intensityMode) } },
                            valueRange = 45f..100f,
                            enabled = reference1RM != null && set.intensityMode == IntensityMode.SOLO_RM,
                        )
                        Text(
                            text = if (isAmrapMode) {
                                "En AMRAP solo importa la carga objetivo; las repeticiones se toman como mínimo."
                            } else if (set.intensityMode == IntensityMode.SOLO_RM) {
                                "Ajusta la carga directamente con el control de %RM."
                            } else {
                                "La carga cambia automáticamente según repeticiones e intensidad."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
            ) {
                ToggleToken(
                    label = when {
                        set.isAmrap && set.isCalibrator -> "AMRAP calibrador"
                        set.isAmrap -> "AMRAP"
                        else -> "Activar AMRAP"
                    },
                    selected = set.isAmrap,
                ) {
                    if (set.isAmrap) {
                        onUpdate { it.copy(isAmrap = false, isCalibrator = false, intensityMode = IntensityMode.RPE) }
                    } else {
                        showAmrapDialog = true
                    }
                }
            }
        }
    }

    if (showAmrapDialog) {
        AlertDialog(
            onDismissRequest = { showAmrapDialog = false },
            title = { Text("Configurar AMRAP", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Elige si esta serie debe calibrar futuras cargas o quedar solo como referencia.")
                    OutlinedButton(
                        onClick = {
                            onUpdate { it.copy(isAmrap = true, isCalibrator = true, intensityMode = IntensityMode.AMRAP) }
                            showAmrapDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("AMRAP calibrador") }
                    FilledTonalButton(
                        onClick = {
                            onUpdate { it.copy(isAmrap = true, isCalibrator = false, intensityMode = IntensityMode.AMRAP) }
                            showAmrapDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("AMRAP aislado") }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }
}

private fun SessionPart.isUncategorized(): Boolean =
    name.trim().lowercase() in setOf("sin categoría", "sin categoria", "sin grupo")

private fun Session.allExercises(): List<Exercise> = exercises + parts.flatMap { it.exercises }

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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onApplyRules: (String?) -> Unit,
    onExportToSession: (String) -> Unit,
    onImportFromSession: (String) -> Unit,
    onSave: (SessionSaveScope) -> Unit,
    onApplyAugeCorrection: (String) -> Unit,
    onDiscardSwitch: (() -> Unit)?,
    onWarmupSave: (String, List<WarmupSetDefinition>) -> Unit,
    onRestoreSnapshot: (Session) -> Unit,
    onRuleDefaultsChange: (Int?, Int?, Double?) -> Unit,
    onRuleLimitsChange: (Double?, Int?) -> Unit,
    onQuickActionOpenPicker: () -> Unit,
    onQuickActionOpenWarmup: () -> Unit,
    onQuickActionDelete: () -> Unit,
    onQuickActionLinkSuperset: () -> Unit,
    onQuickActionUnlinkSuperset: () -> Unit,
    onOpenSupersetManager: (String?, String) -> Unit,
    onUpdateSupersetRestBetween: (String?, String, Int) -> Unit,
    onUpdateSupersetRestAfter: (String?, String, Int) -> Unit,
    onRemoveFromSuperset: (String?, String) -> Unit,
) {
    val session = uiState.session ?: return
    if (uiState.sheet == SessionEditorSheet.NONE) return

    val warmupExercise = session.allExercises().find { it.id == uiState.warmupExerciseId }
    val quickActionExercise = uiState.quickActionsExerciseId?.let { targetId ->
        session.allExercises().find { it.id == targetId }
    }
    val quickActionCanLinkWithNext = remember(session, uiState.quickActionsPartId, uiState.quickActionsExerciseId) {
        val targetId = uiState.quickActionsExerciseId ?: return@remember false
        val source = if (uiState.quickActionsPartId == null) {
            session.exercises
        } else {
            session.parts.firstOrNull { it.id == uiState.quickActionsPartId }?.exercises.orEmpty()
        }
        val idx = source.indexOfFirst { it.id == targetId }
        idx >= 0 && idx < source.lastIndex
    }

    if (uiState.sheet == SessionEditorSheet.EXERCISE_PICKER) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 18.dp, horizontal = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ExercisePickerSheet(
                    query = uiState.searchQuery,
                    catalog = EXERCISE_DATABASE,
                    workoutLogs = uiState.workoutLogs,
                    editingExisting = uiState.pickerTargetExerciseId != null,
                    onSearch = onExerciseSearch,
                    onSelect = onSelectExercise,
                    onDismiss = onDismiss,
                )
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
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
            SessionEditorSheet.RULES -> RulesSheet(uiState, onApplyRules, onRuleDefaultsChange, onRuleLimitsChange)
            SessionEditorSheet.TRANSFER -> TransferSheet(uiState, onExportToSession, onImportFromSession)
            SessionEditorSheet.SAVE -> SaveSheet(onSave = onSave, onDiscardSwitch = onDiscardSwitch)
            SessionEditorSheet.AUGE -> AugeSheet(uiState, onApplyAugeCorrection)
            SessionEditorSheet.WARMUP -> WarmupSheet(exercise = warmupExercise, onSave = onWarmupSave)
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
            SessionEditorSheet.QUICK_ACTIONS -> ExerciseQuickActionsSheet(
                exercise = quickActionExercise,
                canLinkWithNext = quickActionCanLinkWithNext,
                onOpenPicker = onQuickActionOpenPicker,
                onOpenWarmup = onQuickActionOpenWarmup,
                onDelete = onQuickActionDelete,
                onLinkSuperset = onQuickActionLinkSuperset,
                onUnlinkSuperset = onQuickActionUnlinkSuperset,
            )
            SessionEditorSheet.NONE -> Unit
        }
    }
}

@Composable
private fun ExerciseQuickActionsSheet(
    exercise: Exercise?,
    canLinkWithNext: Boolean,
    onOpenPicker: () -> Unit,
    onOpenWarmup: () -> Unit,
    onDelete: () -> Unit,
    onLinkSuperset: () -> Unit,
    onUnlinkSuperset: () -> Unit,
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
        if (exercise.supersetId != null) {
            OutlinedButton(onClick = onUnlinkSuperset, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Desvincular superserie")
            }
        } else {
            OutlinedButton(
                onClick = onLinkSuperset,
                enabled = canLinkWithNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (canLinkWithNext) "Crear superserie" else "No hay siguiente")
            }
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
private fun ExercisePickerSheet(
    query: String,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    editingExisting: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedRegion by rememberSaveable { mutableStateOf<ExerciseCatalogRegion?>(null) }
    var selectedTrait by rememberSaveable { mutableStateOf<ExerciseCatalogTrait?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(ExerciseCatalogSort.RELEVANCE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var infoExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

    val normalizedQuery = query.trim()
    val activeRegion = selectedRegion ?: ExerciseCatalogRegion.ALL
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
                val key = report.exerciseDbId ?: report.exerciseId
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                )
                Text(
                    "${catalog.size} ejercicios",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                    color = Color.Transparent,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar catálogo")
                    }
                }
                Box {
                    OutlinedButton(onClick = { showSortMenu = true }) {
                        Text(sortMode.label)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null)
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

        if (selectedRegion == null && normalizedQuery.isBlank()) {
            Text(
                "Grupos",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "Explorar por grupo muscular",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Text(region.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("$count ejercicios", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(exercisesInCategory, key = { it.id }) { info ->
                                    ExercisePickerCompactCard(
                                        info = info,
                                        onSelect = onSelect,
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
                        )
                    }
                    items(uncategorizedCatalog, key = { it.id }) { info ->
                        ExercisePickerDetailedCard(
                            info = info,
                            onSelect = onSelect,
                            onInfo = { infoExerciseId = info.id },
                        )
                    }
                }
            }
            return
        }

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
                    onSelect = onSelect,
                    onInfo = { infoExerciseId = info.id },
                )
            }
        }
    }

    infoExercise?.let { selected ->
        ExerciseCatalogInfoDialog(
            exercise = selected,
            catalog = catalog,
            associatedDiscomforts = discomfortByExercise[selected.id].orEmpty(),
            onDismiss = { infoExerciseId = null },
        )
    }
}

private fun fatigueColor(score: Int): Color = when {
    score <= 3 -> Color(0xFF22C55E)
    score <= 6 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

@Composable
private fun ExercisePickerCompactCard(
    info: ExerciseMuscleInfo,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onInfo: () -> Unit,
) {
    val fatigue = calculateFriendlyFatigue(info)
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect(info) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    info.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = fatigueColor(fatigue.overall).copy(alpha = 0.16f),
            ) {
                Text(
                    "Fatiga ${fatigue.overall}/10",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = fatigueColor(fatigue.overall),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerDetailedCard(
    info: ExerciseMuscleInfo,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onInfo: () -> Unit,
) {
    val fatigue = calculateFriendlyFatigue(info)
    val primaryMuscle = resolvePrimaryMuscleLabel(info)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(info) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(primaryMuscle, info.equipment, info.type).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = fatigueColor(fatigue.overall).copy(alpha = 0.16f),
                ) {
                    Text(
                        "Fatiga ${fatigue.overall}/10",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = fatigueColor(fatigue.overall),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ExerciseCatalogInfoDialog(
    exercise: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    associatedDiscomforts: List<Pair<String, Int>>,
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
                    ExerciseFactChip("Fatiga", "${fatigue.overall}/10")
                    ExerciseFactChip("Región", resolveExerciseRegion(exercise).label)
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Fatiga AUGE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        FriendlyFatigueRow("Muscular", fatigue.muscle)
                        FriendlyFatigueRow("SNC", fatigue.snc)
                        FriendlyFatigueRow("Espinal", fatigue.spinal)
                    }
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Transferencia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        Text(
                            inferTransferLabel(exercise),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            kinship.similar.forEach { similar ->
                                val similarFatigue = calculateFriendlyFatigue(similar.exercise).overall
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(similar.exercise.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(resolvePrimaryMuscleLabel(similar.exercise), similar.exercise.equipment).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            similar.rationale,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "${similar.band.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            "Sim ${similar.similarityScore}% · Transf ${similar.transferScore}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            "Fatiga $similarFatigue/10",
                                            color = fatigueColor(similarFatigue),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (kinship.transfer.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Transferencia cruzada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            kinship.transfer.forEach { transfer ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(transfer.exercise.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(resolvePrimaryMuscleLabel(transfer.exercise), transfer.exercise.equipment).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            transfer.rationale,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        "Transf ${transfer.transferScore}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black,
                                    )
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
    onRestoreSnapshot: (Session) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Historial y borradores", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("Cambios recientes del borrador", style = MaterialTheme.typography.labelLarge)
        uiState.localDraftHistory.reversed().take(8).forEachIndexed { index, snapshot ->
            OutlinedButton(onClick = { onRestoreSnapshot(snapshot) }, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurar estado ${index + 1}: ${snapshot.name.ifBlank { "Sesión" }}")
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
) {
    var scopePartId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("defaults") }
    var maxRpeInput by remember(uiState.ruleLimits.maxRPE) {
        mutableStateOf(formatEditableNumber(uiState.ruleLimits.maxRPE))
    }
    var maxExercisesInput by remember(uiState.ruleLimits.maxExercisesPerMuscle) {
        mutableStateOf(uiState.ruleLimits.maxExercisesPerMuscle?.toString().orEmpty())
    }

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Reglas del editor", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "defaults", onClick = { tab = "defaults" }, label = { Text("Defaults") })
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
            Button(
                onClick = {
                    onRuleLimitsChange(
                        maxRpeInput.safeDoubleOrNull(),
                        maxExercisesInput.safeIntOrNull(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Guardar límites", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TransferSheet(
    uiState: SessionEditorUiState,
    onExportToSession: (String) -> Unit,
    onImportFromSession: (String) -> Unit,
) {
    var exportMode by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Copiar bloques entre sesiones", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            if (exportMode) {
                "Envía una copia de los bloques actuales a otra sesión. No se reemplaza nada: se agregan al final."
            } else {
                "Trae una copia de bloques desde otra sesión hacia esta. El contenido actual se mantiene intacto."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = exportMode, onClick = { exportMode = true }, label = { Text("Exportar bloques") })
            FilterChip(selected = !exportMode, onClick = { exportMode = false }, label = { Text("Importar bloques") })
        }
        uiState.weekSessions.filterNot { it.id == uiState.session?.id }.forEach { sibling ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (exportMode) onExportToSession(sibling.id) else onImportFromSession(sibling.id)
                    },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                ),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sibling.name.ifBlank { "Sesión" }, fontWeight = FontWeight.Black)
                    Text(
                        "${dayLabel(sibling.dayOfWeek)} · ${sibling.allExercises().size} ejercicios",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (exportMode) "Agregar mis bloques aquí" else "Traer bloques de esta sesión",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveSheet(
    onSave: (SessionSaveScope) -> Unit,
    onDiscardSwitch: (() -> Unit)?,
) {
    var saveScope by rememberSaveable { mutableStateOf(SessionSaveScope.SESSION_ONLY) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Guardar cambios", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            if (onDiscardSwitch != null) {
                "Hay un cambio de sesión pendiente. Si guardas, te llevamos directo a la sesión que elegiste."
            } else {
                "Puedes guardar solo esta sesión o propagar el mismo molde al mesociclo."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Button(onClick = { onSave(saveScope) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (onDiscardSwitch != null) "Guardar y cambiar sesión" else "Guardar",
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
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Series de aproximación · ${exercise.name}", fontWeight = FontWeight.Black, fontSize = 16.sp)
        sets.forEachIndexed { index, set ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelMedium)
                EditorMiniField(label = "Carga %", value = formatEditableNumber(set.percentageOfWorkingWeight), keyboardType = KeyboardType.Decimal, stateKey = "warmup-percent-${set.id}") {
                    val newPercent = it.safeDoubleOrNull() ?: set.percentageOfWorkingWeight
                    sets = sets.toMutableList().also { list ->
                        list[index] = set.copy(
                            percentageOfWorkingWeight = newPercent,
                            targetReps = suggestWarmupReps(newPercent)
                        )
                    }
                }
                EditorMiniField(label = "Reps", value = set.targetReps.toString(), keyboardType = KeyboardType.Number, stateKey = "warmup-reps-${set.id}") {
                    sets = sets.toMutableList().also { list -> list[index] = set.copy(targetReps = it.safeIntOrNull() ?: set.targetReps) }
                }
                IconButton(onClick = { sets = sets.filterIndexed { idx, _ -> idx != index } }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
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
private fun AugeSheet(
    uiState: SessionEditorUiState,
    onApplyAugeCorrection: (String) -> Unit,
) {
    val summary = uiState.augeSummary
    val accentColor = augeStatusColor(summary.status, summary.hasCriticalAlerts)

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Asistente", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "Monitorea fatiga, alertas y margen antes de cerrar o guardar la sesión.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AugeOverviewMetric(
                    title = "Sets",
                    value = summary.sessionSetCount.toString(),
                    subtitle = "Semana ${summary.weeklySetCount}",
                    modifier = Modifier.weight(1f),
                )
                AugeOverviewMetric(
                    title = "Duración",
                    value = "${summary.sessionDurationMinutes} min",
                    subtitle = "Semana ${summary.weeklyDurationMinutes}",
                    modifier = Modifier.weight(1f),
                )
                AugeOverviewMetric(
                    title = "Dificultad",
                    value = "${summary.sessionDifficulty}/10",
                    subtitle = "Semana ${summary.weeklyDifficulty}/10",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionMetricBadge(
                    label = "Estado",
                    value = when (summary.status) {
                        SessionEditorAugeStatus.OPTIMAL -> "Óptimo"
                        SessionEditorAugeStatus.WARNING -> "Vigilar"
                        SessionEditorAugeStatus.FATIGUING -> "Fatigante"
                    },
                    tone = accentColor,
                    modifier = Modifier.weight(1f),
                )
                SessionMetricBadge(
                    label = "Riesgo",
                    value = if (summary.hasCriticalAlerts) "Alto" else if (summary.alertCount > 0) "Medio" else "Bajo",
                    tone = if (summary.hasCriticalAlerts) Color(0xFFEA580C) else if (summary.alertCount > 0) Color(0xFFF59E0B) else Color(0xFF16A34A),
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                "${summary.combinedSessionDrainPercent()}% carga sesión · ${summary.combinedWeeklyDrainPercent()}% semanal",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Baterías AUGE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("Sesión", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                AugePercentBar("Muscular", summary.sessionDrain.muscular)
                AugePercentBar("SNC", summary.sessionDrain.cns)
                AugePercentBar("Espinal", summary.sessionDrain.spinal)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Text("Semana", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                AugePercentBar("Muscular", summary.weeklyDrain.muscular)
                AugePercentBar("SNC", summary.weeklyDrain.cns)
                AugePercentBar("Espinal", summary.weeklyDrain.spinal)
            }
        }

        // ── Volumen por músculo ──────────────────────────────────────────────
        val session = uiState.session
        if (session != null) {
            val allExercises = session.allExercises()
            val setsPerMuscle = remember(allExercises) {
                val map = mutableMapOf<String, Int>()
                allExercises.forEach { exercise ->
                    val setCount = exercise.sets.size.coerceAtLeast(1)
                    val dbEntry = EXERCISE_DATABASE.firstOrNull { it.id == exercise.exerciseDbId }
                    val muscles = dbEntry?.involvedMuscles?.map { im ->
                        VolumeCalculator.normalizeCanonicalMuscleGroup(im.muscle, im.emphasis)
                    }?.distinct() ?: listOf(exercise.name.ifBlank { "General" })
                    muscles.forEach { muscle ->
                        map[muscle] = (map[muscle] ?: 0) + setCount
                    }
                }
                map.entries.sortedByDescending { it.value }.take(8)
            }

            if (setsPerMuscle.isNotEmpty()) {
                val maxSets = setsPerMuscle.maxOf { it.value }.coerceAtLeast(1)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Volumen por músculo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "Sets estimados por grupo muscular en esta sesión.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        setsPerMuscle.forEach { (muscle, sets) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(muscle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "$sets sets",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { sets.toFloat() / maxSets },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                    color = accentColor,
                                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (summary.alerts.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp),
                )
                Text("Todo en rango", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
            }
        } else {
            AugeSectionTitle(
                title = "Alertas prioritarias",
                subtitle = "Esto es lo primero que AUGE corregiría antes de seguir cargando la sesión.",
            )
            summary.alerts.forEach { alert ->
                AugeAlertCard(
                    alert = alert,
                    onApplyCorrection = onApplyAugeCorrection,
                )
            }
        }

        if (summary.suggestions.isNotEmpty()) {
            Text("Sugerencias", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                summary.suggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            if (suggestion.correctionType != null) {
                                onApplyAugeCorrection(suggestion.id)
                            }
                        },
                        label = { Text(suggestion.title, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        icon = {
                            Icon(
                                augeSeverityIcon(suggestion.severity),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = augeSeverityColor(suggestion.severity),
                            )
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            augeSeverityColor(suggestion.severity).copy(alpha = 0.32f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AugeOverviewMetric(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
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
private fun SessionMetricBadge(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = tone.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = tone,
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
private fun AugePercentBar(
    label: String,
    value: Int,
) {
    val safeValue = value.coerceIn(0, 100)
    val color = drainColor(safeValue)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Text("$safeValue%", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(
            progress = { safeValue / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
        )
    }
}

@Composable
private fun AugeAlertCard(
    alert: SessionEditorAugeAlert,
    onApplyCorrection: (String) -> Unit,
) {
    val accentColor = augeSeverityColor(alert.severity)
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
                        imageVector = augeSeverityIcon(alert.severity),
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
                        augeSeverityLabel(alert.severity),
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
                AugeTag(augeSourceLabel(alert.source), accentColor)
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

private fun SessionEditorAugeSummary.combinedSessionDrainPercent(): Int =
    ((sessionDrain.cns + sessionDrain.muscular + sessionDrain.spinal) / 3.0).roundToInt()

private fun SessionEditorAugeSummary.combinedWeeklyDrainPercent(): Int =
    ((weeklyDrain.cns + weeklyDrain.muscular + weeklyDrain.spinal) / 3.0).roundToInt()

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

private fun augeSeverityLabel(severity: SessionEditorAugeAlertSeverity): String = when (severity) {
    SessionEditorAugeAlertSeverity.INFO -> "Sugerencia"
    SessionEditorAugeAlertSeverity.WARNING -> "Atención"
    SessionEditorAugeAlertSeverity.CRITICAL -> "Importante"
}

private fun augeSeverityColor(severity: SessionEditorAugeAlertSeverity): Color = when (severity) {
    SessionEditorAugeAlertSeverity.INFO -> Color(0xFF2563EB)
    SessionEditorAugeAlertSeverity.WARNING -> Color(0xFFF59E0B)
    SessionEditorAugeAlertSeverity.CRITICAL -> Color(0xFFEA580C)
}

private fun augeSeverityIcon(severity: SessionEditorAugeAlertSeverity): ImageVector = when (severity) {
    SessionEditorAugeAlertSeverity.INFO -> Icons.Default.TipsAndUpdates
    SessionEditorAugeAlertSeverity.WARNING -> Icons.Default.AutoAwesome
    SessionEditorAugeAlertSeverity.CRITICAL -> Icons.Default.PriorityHigh
}

private fun augeSourceLabel(source: SessionEditorAugeAlertSource): String = when (source) {
    SessionEditorAugeAlertSource.SESSION -> "Sesión"
    SessionEditorAugeAlertSource.WEEK -> "Semana"
    SessionEditorAugeAlertSource.SYSTEM -> "Sistema"
    SessionEditorAugeAlertSource.EXERCISE -> "Ejercicio"
}

private fun augeCorrectionLabel(correctionType: SessionEditorAugeCorrectionType): String = when (correctionType) {
    SessionEditorAugeCorrectionType.REDUCE_SERIES -> "Aplicar recorte de series"
    SessionEditorAugeCorrectionType.REDUCE_RPE -> "Aplicar baja de RPE"
    SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE -> "Bajar volumen e intensidad"
    SessionEditorAugeCorrectionType.ADD_SERIES -> "Agregar una serie"
}

private fun drainColor(value: Int): Color = when {
    value < 40 -> Color(0xFF16A34A)
    value < 70 -> Color(0xFFF59E0B)
    else -> Color(0xFFEA580C)
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

private fun formatRestSummary(restTime: Int?): String {
    val total = restTime ?: 90
    val minutes = total / 60
    val seconds = total % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

private fun trainingModeLabel(mode: TrainingMode): String = when (mode) {
    TrainingMode.REPS -> "Reps"
    TrainingMode.TIME -> "Tiempo"
    TrainingMode.PERCENT -> "RM"
    TrainingMode.CUSTOM -> "Personalizado"
    TrainingMode.DISTANCE -> "Distancia"
}

private fun formatEditableNumber(value: Double?): String {
    if (value == null) return ""
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
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

private fun String.safeIntOrNull(): Int? = toIntOrNull()
private fun String.safeDoubleOrNull(): Double? = replace(",", ".").toDoubleOrNull()

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
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trainingModeLabel(currentMode),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
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
                TrainingMode.PERCENT to "RM",
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
