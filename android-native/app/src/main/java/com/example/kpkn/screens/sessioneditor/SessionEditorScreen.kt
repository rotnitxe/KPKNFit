package com.example.kpkn.screens.sessioneditor

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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

            itemsIndexed(session.parts, key = { _, part -> part.id }) { partIndex, part ->
                PartEditorCard(
                    part = part,
                    index = partIndex,
                    collapsed = part.id in uiState.collapsedPartIds,
                    onToggleCollapse = { viewModel.togglePartCollapsed(part.id) },
                    onRename = { viewModel.updatePartName(part.id, it) },
                    onChangeColor = { viewModel.updatePartColor(part.id, it) },
                    onMoveToIndex = { targetIndex -> viewModel.movePartToIndex(part.id, targetIndex) },
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
                        val activeId = draggingPartId ?: return@PartEditorCard
                        draggingPartOffsetY += deltaY
                        val activeRect = partBounds[activeId] ?: return@PartEditorCard
                        val centerY = activeRect.center.y + draggingPartOffsetY
                        val targetId = session.parts.firstOrNull { candidate ->
                            candidate.id != activeId && partBounds[candidate.id]?.contains(Offset(activeRect.center.x, centerY)) == true
                        }?.id
                        partDropTargetId = targetId
                        if (targetId != null) {
                            val currentIndex = session.parts.indexOfFirst { it.id == activeId }
                            val targetIndex = session.parts.indexOfFirst { it.id == targetId }
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
                        if (part.isUncategorized()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                part.exercises.forEachIndexed { index, exercise ->
                                    key("${part.id}|${part.color}|${exercise.id}|${exercise.sets.hashCode()}") {
                                    ExerciseEditorCard(
                                        exercise = exercise,
                                        exerciseInfo = EXERCISE_DATABASE.find { it.id == exercise.exerciseDbId },
                                        accentHex = part.color,
                                        partId = part.id,
                                        dropIndex = index,
                                        canLinkWithNext = index < part.exercises.lastIndex,
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
                                                val targetPart = session.parts.firstOrNull { it.id == targetPartId }
                                                val targetIdx = targetPart?.exercises?.indexOfFirst { it.id == targetExerciseId } ?: -1
                                                if (targetIdx >= 0) {
                                                    viewModel.moveExerciseToPart(currentPartId, activeExerciseId, targetPartId, targetIdx)
                                                    draggingExercisePartId = targetPartId
                                                    draggingExerciseOffset = Offset.Zero
                                                }
                                            } else {
                                                val targetPartId = session.parts.firstOrNull { candidate ->
                                                    partContentBounds[candidate.id]?.contains(center) == true
                                                }?.id
                                                exerciseDropTargetPartId = targetPartId
                                                if (targetPartId != null && targetPartId != currentPartId) {
                                                    viewModel.moveExerciseToPart(currentPartId, activeExerciseId, targetPartId, null)
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
                                        onOpenPicker = { viewModel.openPicker(part.id, exercise.id) },
                                        onUpdateExercise = { updater -> viewModel.updateExercise(part.id, exercise.id, updater) },
                                        onRemoveExercise = { viewModel.removeExercise(part.id, exercise.id) },
                                            onAddSet = { viewModel.addSet(part.id, exercise.id) },
                                            onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, exercise.id, setId, updater) },
                                        onRemoveSet = { setId -> viewModel.removeSet(part.id, exercise.id, setId) },
                                        onMoveSet = { setId, dir -> viewModel.moveSet(part.id, exercise.id, setId, dir) },
                                        onOpenWarmup = { viewModel.openWarmup(exercise.id) },
                                        onLinkWithNext = if (index < part.exercises.lastIndex) {
                                            { viewModel.linkExerciseWithNext(part.id, exercise.id) }
                                        } else null,
                                        onUnlinkSuperset = if (exercise.supersetId != null) {
                                            { viewModel.unlinkExerciseFromSuperset(part.id, exercise.id) }
                                        } else null,
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(part.exercises, key = { _, exercise -> "${part.id}|${part.color}|${exercise.id}|${exercise.sets.hashCode()}" }) { targetIndex, exercise ->
                                    ExerciseEditorCard(
                                        exercise = exercise,
                                        exerciseInfo = EXERCISE_DATABASE.find { it.id == exercise.exerciseDbId },
                                        accentHex = part.color,
                                        partId = part.id,
                                        dropIndex = targetIndex,
                                        canLinkWithNext = targetIndex < part.exercises.lastIndex,
                                        modifier = Modifier.width(296.dp),
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
                                                val targetPart = session.parts.firstOrNull { it.id == targetPartId }
                                                val targetIdx = targetPart?.exercises?.indexOfFirst { it.id == targetExerciseId } ?: -1
                                                if (targetIdx >= 0) {
                                                    viewModel.moveExerciseToPart(currentPartId, activeExerciseId, targetPartId, targetIdx)
                                                    draggingExercisePartId = targetPartId
                                                    draggingExerciseOffset = Offset.Zero
                                                }
                                            } else {
                                                val targetPartId = session.parts.firstOrNull { candidate ->
                                                    partContentBounds[candidate.id]?.contains(center) == true
                                                }?.id
                                                exerciseDropTargetPartId = targetPartId
                                                if (targetPartId != null && targetPartId != currentPartId) {
                                                    viewModel.moveExerciseToPart(currentPartId, activeExerciseId, targetPartId, null)
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
                                        onOpenPicker = { viewModel.openPicker(part.id, exercise.id) },
                                        onUpdateExercise = { updater -> viewModel.updateExercise(part.id, exercise.id, updater) },
                                        onRemoveExercise = { viewModel.removeExercise(part.id, exercise.id) },
                                        onAddSet = { viewModel.addSet(part.id, exercise.id) },
                                        onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, exercise.id, setId, updater) },
                                        onRemoveSet = { setId -> viewModel.removeSet(part.id, exercise.id, setId) },
                                        onMoveSet = { setId, dir -> viewModel.moveSet(part.id, exercise.id, setId, dir) },
                                        onOpenWarmup = { viewModel.openWarmup(exercise.id) },
                                        onLinkWithNext = if (targetIndex < part.exercises.lastIndex) {
                                            { viewModel.linkExerciseWithNext(part.id, exercise.id) }
                                        } else null,
                                        onUnlinkSuperset = if (exercise.supersetId != null) {
                                            { viewModel.unlinkExerciseFromSuperset(part.id, exercise.id) }
                                        } else null,
                                    )
                                }
                            }
                        }
                    },
                )
            }

            item {
                OutlinedButton(
                    onClick = viewModel::addPart,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nueva parte", fontWeight = FontWeight.Bold)
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
                targetPartId != null && targetExerciseId != null -> viewModel.replaceExerciseInPart(targetPartId, targetExerciseId, info)
                targetPartId != null -> viewModel.addExerciseToPart(targetPartId, info)
            }
        },
        onApplyRules = { partId ->
            viewModel.applyRuleDefaultsToSession(partId)
            scope.launch {
                val message = if (partId == null) {
                    "Defaults aplicados a la sesión"
                } else {
                    "Defaults aplicados a la parte"
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
            session.parts.firstOrNull { part -> part.exercises.any { it.id == exerciseId } }?.let { part ->
                viewModel.updateWarmupSets(part.id, exerciseId, sets)
            }
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
    val background = session.background
    val brightness = background?.style?.brightness ?: 0.92f
    val blur = (background?.style?.blur ?: 0f).dp
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showAdvanced) 372.dp else 286.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(Color.Black),
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeroGlassIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Salir",
                            onClick = onClose,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = dayLabel(session.dayOfWeek),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasChanges) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.16f),
                            ) {
                                Text(
                                    text = "Sin guardar",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        HeroGlassIconButton(
                            icon = Icons.Default.Save,
                            contentDescription = "Guardar sesión",
                            onClick = onSave,
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
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = session.name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre de la sesión", color = Color.White.copy(alpha = 0.72f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
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
                    OutlinedTextField(
                    value = session.description.orEmpty(),
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descripción", color = Color.White.copy(alpha = 0.72f)) },
                    minLines = 1,
                    maxLines = 2,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.26f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Color.White)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Opciones avanzadas",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    text = "Presiona para desplegar",
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                    }

                    AnimatedVisibility(showAdvanced) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                HeaderCarouselAction("Transferir", Icons.Default.SwapHoriz, onOpenTransfer)
                            }
                            item {
                                HeaderCarouselAction("Historiales", Icons.Default.History, onOpenHistory)
                            }
                            item {
                                HeaderCarouselAction("Reglas", Icons.Default.Settings, onOpenRules)
                            }
                        }
                    }

                    AnimatedVisibility(showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = session.isMeetDay,
                                    onClick = { onMeetDayChange(!session.isMeetDay) },
                                    label = { Text("Modo competición") },
                                    leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                                )
                            }
                            if (session.isMeetDay) {
                                OutlinedTextField(
                                    value = session.meetBodyweight?.let(::formatEditableNumber).orEmpty(),
                                    onValueChange = { onMeetBodyweightChange(it.safeDoubleOrNull()) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Peso corporal objetivo (kg)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
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
private fun HeroGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.24f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
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
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Abrir AUGE",
                modifier = Modifier.size(22.dp),
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
    }
}

@Composable
private fun HeaderCarouselAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.24f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier
                .width(134.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ContextualSessionHeader(
    session: Session,
    compact: Boolean,
    hasChanges: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(!compact) }
    LaunchedEffect(compact) {
        if (compact) expanded = false
    }
    val backgroundColor by animateColorAsState(
        if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        label = "header-bg",
    )
    Surface(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        color = backgroundColor,
        tonalElevation = if (compact) 7.dp else 3.dp,
        shadowElevation = if (compact) 10.dp else 2.dp,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Salir") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Opciones avanzadas",
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Historiales, reglas, transferencias y guardado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasChanges) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "Sin guardar",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                FilledTonalIconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Guardar")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded && !compact) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded && !compact) "Ocultar opciones" else "Mostrar opciones",
                    )
                }
            }
            AnimatedVisibility(visible = expanded && !compact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeaderActionTile(
                            label = "Fondo",
                            caption = "Portada y filtros",
                            icon = Icons.Default.Image,
                            onClick = onOpenBackground,
                            modifier = Modifier.weight(1f),
                        )
                        HeaderActionTile(
                            label = "Transferir",
                            caption = "Mover ejercicios",
                            icon = Icons.Default.SwapHoriz,
                            onClick = onOpenTransfer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeaderActionTile(
                            label = "Historial",
                            caption = "Logs y borradores",
                            icon = Icons.Default.History,
                            onClick = onOpenHistory,
                            modifier = Modifier.weight(1f),
                        )
                        HeaderActionTile(
                            label = "Reglas",
                            caption = if (session.isMeetDay) "Sesión de competición" else "Defaults y ayudas",
                            icon = if (session.isMeetDay) Icons.Default.WorkspacePremium else Icons.Default.Settings,
                            onClick = onOpenRules,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActionTile(
    label: String,
    caption: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        sessions.filter { it.dayOfWeek == selectedDay }.sortedBy { it.name }
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

            if (sessionsForSelectedDay.isEmpty()) {
                FilledTonalButton(
                    onClick = { onCreateSessionForDay(selectedDay) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Crear sesión para ${dayLabelShort(selectedDay)}", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sessionsForSelectedDay.forEach { session ->
                        val selected = session.id == selectedSessionId
                        Surface(
                            modifier = Modifier.clickable { onSelectSession(session.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
                            },
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = dayLabelShort(session.dayOfWeek),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Column {
                                    Text(
                                        text = session.name.ifBlank { "Sesión" },
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (session.isMeetDay) "Competición" else "Sesión ${dayLabelShort(session.dayOfWeek)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartEditorCard(
    part: SessionPart,
    index: Int,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRename: (String) -> Unit,
    onChangeColor: (String) -> Unit,
    onMoveToIndex: (Int) -> Unit,
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
    val categorySurface = lerp(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
        partColor,
        if (isDropTarget) 0.26f else 0.16f,
    )
    val dropScale by animateFloatAsState(if (isDropTarget) 1.01f else 1f, label = "partDropScale")
    val totalSets = remember(part.exercises) { part.exercises.sumOf { it.sets.size.coerceAtLeast(1) } }
    val mainMuscle = remember(part.exercises) { part.primaryMuscle() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = if (isDragging) 1.02f else dropScale
                scaleY = if (isDragging) 1.02f else dropScale
                alpha = if (isDragging) 0.96f else 1f
                shadowElevation = if (isDragging) 28.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 10f else 0f),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = categorySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = if (isDragging || isDropTarget) {
            androidx.compose.foundation.BorderStroke(
                width = if (isDragging) 2.dp else 1.5.dp,
                color = partColor.copy(alpha = if (isDragging) 0.95f else 0.7f),
            )
        } else null,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(partColor)
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalIconButton(onClick = { showColorPicker = !showColorPicker }) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(partColor)
                        )
                    }
                    IconButton(onClick = onToggleCollapse) {
                        Icon(if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (part.isUncategorized()) "Ejercicios sin categoría" else "Categoría",
                        style = MaterialTheme.typography.labelSmall,
                        color = partColor,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = part.name,
                        onValueChange = onRename,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = partColor,
                            unfocusedBorderColor = partColor.copy(alpha = 0.5f),
                            focusedLabelColor = partColor,
                            cursorColor = partColor,
                        ),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PartQuickPill("Ejercicios", part.exercises.size.toString(), Modifier.weight(1f))
                    PartQuickPill("Series", totalSets.toString(), Modifier.weight(1f))
                    PartQuickPill("Músculo", mainMuscle, Modifier.weight(1f))
                }

                AnimatedVisibility(showColorPicker) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PART_COLORS.forEach { hex ->
                            val c = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.Gray)
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        content()
                        FilledTonalButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar ejercicio", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar categoría", fontWeight = FontWeight.Black) },
            text = { Text("Antes de borrarla, decide qué hacer con los ejercicios que contiene.") },
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
                    Text("Puedes conservarlos en “Sin categoría” o borrar también todo su contenido.")
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
                        Text("Borrar categoría y ejercicios")
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
    dropIndex: Int,
    canLinkWithNext: Boolean,
    modifier: Modifier = Modifier,
    isDragging: Boolean,
    dragOffset: Offset,
    isDropTarget: Boolean,
    isPartDropTarget: Boolean,
    onBoundsChange: (Rect) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onOpenPicker: () -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onRemoveExercise: () -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onOpenWarmup: () -> Unit,
    onLinkWithNext: (() -> Unit)?,
    onUnlinkSuperset: (() -> Unit)?,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val accentColor = remember(accentHex) {
        runCatching { Color(AndroidColor.parseColor(accentHex ?: PART_COLORS.first())) }.getOrDefault(Color(0xFF00F0FF))
    }
    val exerciseSurface = lerp(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        accentColor,
        when {
            isDragging -> 0.34f
            isDropTarget -> 0.28f
            isPartDropTarget -> 0.22f
            else -> 0.18f
        },
    )
    val dropScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.03f
            isDropTarget -> 0.98f
            isPartDropTarget -> 1.01f
            else -> 1f
        },
        label = "exerciseDropScale",
    )
    val predictedWeights = remember(exercise.reference1RM, exercise.sets) {
        exercise.sets.associate { set ->
            set.id to exercise.reference1RM?.let { reference ->
                calculateWeightFrom1RMAndIntensity(reference, set)
            }
        }
    }
    Card(
        modifier = modifier
            .onGloballyPositioned { onBoundsChange(it.boundsInWindow()) }
            .pointerInput(partId, exercise.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragCancel = { onDragEnd() },
                    onDragEnd = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(Offset(dragAmount.x, dragAmount.y))
                    }
                )
            }
            .graphicsLayer {
                translationX = if (isDragging) dragOffset.x else 0f
                translationY = if (isDragging) dragOffset.y else 0f
                scaleX = dropScale
                scaleY = dropScale
                alpha = if (isDragging) 0.96f else 1f
                shadowElevation = if (isDragging) 22.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 12f else 0f),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = exerciseSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isDragging || isDropTarget || isPartDropTarget) {
            androidx.compose.foundation.BorderStroke(
                width = if (isDragging) 2.dp else 1.5.dp,
                color = when {
                    isDragging -> accentColor.copy(alpha = 0.95f)
                    isDropTarget -> accentColor.copy(alpha = 0.75f)
                    else -> accentColor.copy(alpha = 0.55f)
                },
            )
        } else androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.22f),
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor.copy(alpha = 0.9f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = true },
                        onLongClick = { },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (exercise.isCompetitionLift) Icons.Default.WorkspacePremium else Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = accentColor,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name.ifBlank { "Seleccionar ejercicio" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${exercise.sets.size} series · descanso ${formatRestSummary(exercise.restTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (exercise.isCompetitionLift) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFF59E0B))
                }
                if (exercise.isStarTarget) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFACC15))
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseFactChip("Descanso", formatRestSummary(exercise.restTime))
                exercise.variantName?.takeIf { it.isNotBlank() }?.let { ExerciseFactChip("Variante", it) }
                exerciseInfo?.equipment?.takeIf { it.isNotBlank() }?.let { ExerciseFactChip("Equipo", it) }
                exerciseInfo?.category?.takeIf { it.isNotBlank() }?.let { ExerciseFactChip("Perfil", it) }
                if (exercise.supersetId != null) ExerciseFactChip("Biserie", "Activa")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenPicker,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (expanded) {
        ExerciseEditorOverlay(
            exercise = exercise,
            exerciseInfo = exerciseInfo,
            predictedWeights = predictedWeights,
            accentColor = accentColor,
            onDismiss = { expanded = false },
            onOpenPicker = onOpenPicker,
            onUpdateExercise = onUpdateExercise,
            onRemoveExercise = onRemoveExercise,
            onAddSet = onAddSet,
            onUpdateSet = onUpdateSet,
            onRemoveSet = onRemoveSet,
            onMoveSet = onMoveSet,
            onOpenWarmup = onOpenWarmup,
            canLinkWithNext = canLinkWithNext,
            onLinkWithNext = onLinkWithNext,
            onUnlinkSuperset = onUnlinkSuperset,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar ejercicio", fontWeight = FontWeight.Black) },
            text = { Text("¿Quieres borrar este ejercicio de la categoría?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onRemoveExercise()
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun SetEditorCard(
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
    val isRmMode = trainingMode == TrainingMode.PERCENT
    val isAmrapMode = set.isAmrap
    val sliderPercent = remember(
        set.targetPercentageRM,
        set.targetReps,
        set.intensityMode,
        predictedWeight,
        reference1RM,
    ) {
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
    val setSurface = lerp(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        accentColor,
        0.28f,
    )

    Card(
        modifier = Modifier.width(332.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = setSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "Serie ${index + 1}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.ArrowUpward, null)
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.ArrowDownward, null)
                }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                EditorMiniField(
                    label = metricLabel,
                    value = metricValue,
                    stateKey = "metric-${set.id}",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(if (isAmrapMode) 1.35f else 1f),
                ) { input ->
                    onUpdate { current ->
                        when (trainingMode) {
                            TrainingMode.TIME -> current.copy(targetDuration = input.safeIntOrNull())
                            else -> current.copy(targetReps = input.safeIntOrNull())
                        }
                    }
                }

                if (!isAmrapMode) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Intensidad", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton(onClick = { showIntensityMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(intensityLabel)
                                Spacer(Modifier.width(6.dp))
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
                                                    IntensityMode.SOLO_RM -> it.copy(
                                                        intensityMode = IntensityMode.SOLO_RM,
                                                        isFailure = false,
                                                        targetPercentageRM = it.targetPercentageRM ?: estimatePercent1RM(it.targetReps ?: 1),
                                                        targetRIR = null,
                                                        targetRPE = null,
                                                    )
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
                            modifier = Modifier.weight(0.9f),
                        ) {}
                    } else {
                        EditorMiniField(
                            label = intensityValueLabel,
                            value = intensityValue,
                            stateKey = "intensity-${set.id}",
                            keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE || (set.intensityMode ?: IntensityMode.RPE) == IntensityMode.SOLO_RM) KeyboardType.Decimal else KeyboardType.Number,
                            modifier = Modifier.weight(0.9f),
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
                        shape = RoundedCornerShape(18.dp),
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = if (set.isCalibrator) "AMRAP calibrador" else "AMRAP",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
            }

            if (isRmMode) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Carga estimada", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = displayedWeight?.let { "${"%.1f".format(it)} kg" } ?: "Sin referencia",
                                style = MaterialTheme.typography.titleLarge,
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
                            onValueChange = {
                                onUpdate { current ->
                                    current.copy(
                                        targetPercentageRM = it.toDouble(),
                                        intensityMode = if (current.intensityMode == IntensityMode.SOLO_RM) IntensityMode.SOLO_RM else current.intensityMode,
                                    )
                                }
                            },
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    ) {
                        Text("AMRAP calibrador")
                    }
                    FilledTonalButton(
                        onClick = {
                            onUpdate { it.copy(isAmrap = true, isCalibrator = false, intensityMode = IntensityMode.AMRAP) }
                            showAmrapDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("AMRAP aislado")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEditorOverlay(
    exercise: Exercise,
    exerciseInfo: ExerciseMuscleInfo?,
    predictedWeights: Map<String, Double?>,
    accentColor: Color,
    onDismiss: () -> Unit,
    onOpenPicker: () -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onRemoveExercise: () -> Unit,
    onAddSet: () -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String) -> Unit,
    onMoveSet: (String, Int) -> Unit,
    onOpenWarmup: () -> Unit,
    canLinkWithNext: Boolean,
    onLinkWithNext: (() -> Unit)?,
    onUnlinkSuperset: (() -> Unit)?,
) {
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
    var showModeMenu by remember { mutableStateOf(false) }
    var baseSectionExpanded by rememberSaveable(exercise.id) { mutableStateOf(true) }
    val setsListState = rememberLazyListState()
    val activeSetIndex by remember(exercise.sets, setsListState) {
        derivedStateOf { setsListState.firstVisibleItemIndex.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0)) }
    }
    val localPrEstimatedRm = remember(prWeightInput, prRepsInput) {
        val weight = prWeightInput.safeDoubleOrNull()
        val reps = prRepsInput.safeIntOrNull()
        if (weight != null && weight > 0 && reps != null && reps > 0) calculateHybrid1RM(weight, reps) else null
    }
    LaunchedEffect(exercise.id, exercise.restTime) {
        restSelectionSeconds = exercise.restTime ?: 90
    }
    val overlaySurface = lerp(MaterialTheme.colorScheme.surface, accentColor, 0.14f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(32.dp),
            color = overlaySurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (exercise.isCompetitionLift) Icons.Default.WorkspacePremium else Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = accentColor,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            exercise.name.ifBlank { "Ejercicio" },
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${exercise.sets.size} series · ${formatRestSummary(exercise.restTime)} · ${trainingModeLabel(exercise.trainingMode)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (exerciseInfo != null) {
                            Text(
                                text = listOfNotNull(exerciseInfo.category, exerciseInfo.type, exerciseInfo.equipment).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onRemoveExercise) {
                        Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                    FilledTonalIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Check, contentDescription = "Cerrar")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EditorSectionCard("Base de la serie", accentColor = accentColor) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "Descanso, modo, carga inicial y preparación",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            AssistChip(
                                onClick = { baseSectionExpanded = !baseSectionExpanded },
                                label = { Text(if (baseSectionExpanded) "Plegar" else "Desplegar") },
                                leadingIcon = {
                                    Icon(
                                        if (baseSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        null,
                                    )
                                },
                            )
                        }

                        AnimatedVisibility(baseSectionExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                DurationPickerField(
                                    label = "Descanso",
                                    totalSeconds = restSelectionSeconds,
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = accentColor,
                                ) { totalSeconds ->
                                    restSelectionSeconds = totalSeconds
                                    onUpdateExercise { draft -> draft.copy(restTime = totalSeconds) }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Modo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Box {
                                            OutlinedButton(onClick = { showModeMenu = true }) {
                                                Text(trainingModeLabel(exercise.trainingMode))
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Default.KeyboardArrowDown, null)
                                            }
                                            DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
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
                                                            showModeMenu = false
                                                            onUpdateExercise { current -> current.copy(trainingMode = mode) }
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (exercise.trainingMode != TrainingMode.PERCENT) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Carga inicial", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                            EditorMiniField(
                                                label = "Opcional",
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

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilledTonalButton(onClick = onOpenWarmup, modifier = Modifier.weight(1f)) {
                                        Text("Series de aproximación")
                                    }
                                    OutlinedButton(onClick = onOpenPicker, modifier = Modifier.weight(1f)) {
                                        Text("Cambiar ejercicio")
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (exercise.supersetId != null) {
                                        OutlinedButton(
                                            onClick = { onUnlinkSuperset?.invoke() },
                                            enabled = onUnlinkSuperset != null,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Desvincular biserie")
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { onLinkWithNext?.invoke() },
                                            enabled = canLinkWithNext && onLinkWithNext != null,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (canLinkWithNext) "Vincular con siguiente" else "Sin siguiente ejercicio")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    EditorSectionCard("Series", accentColor = accentColor) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LazyRow(
                                state = setsListState,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(exercise.sets, key = { _, set -> set.id }) { setIndex, set ->
                                    SetEditorCard(
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
                            }
                            if (exercise.sets.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    exercise.sets.forEachIndexed { dotIndex, _ ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .width(if (dotIndex == activeSetIndex) 18.dp else 8.dp)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    if (dotIndex == activeSetIndex) accentColor
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
                                                )
                                        )
                                    }
                                }
                            }
                            FilledTonalButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Agregar serie", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun SessionPart.isUncategorized(): Boolean =
    name.trim().equals("Sin categoría", ignoreCase = true)

private fun SessionPart.primaryMuscle(): String {
    val muscle = exercises
        .flatMap { exercise ->
            EXERCISE_DATABASE.firstOrNull { it.id == exercise.exerciseDbId }
                ?.involvedMuscles
                .orEmpty()
        }
        .groupingBy { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return muscle ?: "General"
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
        shape = RoundedCornerShape(18.dp),
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
            shape = RoundedCornerShape(18.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
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
                .clip(RoundedCornerShape(18.dp))
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
private fun PartQuickPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        }
    }
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
) {
    val session = uiState.session ?: return
    if (uiState.sheet == SessionEditorSheet.NONE) return

    val warmupExercise = session.parts.flatMap { it.exercises }.find { it.id == uiState.warmupExerciseId }

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
            SessionEditorSheet.NONE -> Unit
        }
    }
}

@Composable
private fun ExercisePickerSheet(
    query: String,
    catalog: List<ExerciseMuscleInfo>,
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

        if (selectedRegion == null) {
            Text(
                "Categorías",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { selectedRegion = null }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Categorías")
            }
            Text(activeRegion.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
        }

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
                        selectedRegion = ExerciseCatalogRegion.ALL
                        selectedTrait = null
                        onSearch("")
                        sortMode = ExerciseCatalogSort.RELEVANCE
                    }
                ) { Text("Limpiar") }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(results, key = { it.id }) { info ->
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
                            IconButton(onClick = { infoExerciseId = info.id }) {
                                Icon(Icons.Default.Info, contentDescription = "Ver detalle")
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExerciseFactChip("Parte", resolveExerciseRegion(info).label)
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
        }
    }

    infoExercise?.let { selected ->
        ExerciseCatalogInfoDialog(
            exercise = selected,
            catalog = catalog,
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
private fun ExerciseCatalogInfoDialog(
    exercise: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    onDismiss: () -> Unit,
) {
    val fatigue = remember(exercise.id) { calculateFriendlyFatigue(exercise) }
    val comparisons = remember(exercise.id, catalog) { buildExerciseComparisons(exercise, catalog) }
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
                    ExerciseFactChip("Parte", resolveExerciseRegion(exercise).label)
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

                if (comparisons.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Comparado con otros", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            comparisons.forEach { similar ->
                                val similarFatigue = calculateFriendlyFatigue(similar).overall
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(similar.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(resolvePrimaryMuscleLabel(similar), similar.equipment).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
                            shape = RoundedCornerShape(18.dp),
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
                    Text("${dayLabel(sibling.dayOfWeek)} · ${sibling.parts.sumOf { it.exercises.size }} ejercicios", style = MaterialTheme.typography.bodySmall)
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
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Series de aproximación · ${exercise.name}", fontWeight = FontWeight.Black, fontSize = 18.sp)
        sets.forEachIndexed { index, set ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
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
                IconButton(onClick = { sets = sets.filterIndexed { idx, _ -> idx != index } }) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        OutlinedButton(onClick = {
            val last = sets.lastOrNull()
            val nextPercent = ((last?.percentageOfWorkingWeight ?: 40.0) + 10).coerceAtMost(95.0)
            sets = sets + WarmupSetDefinition(UUID.randomUUID().toString(), nextPercent, suggestWarmupReps(nextPercent))
        }) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Agregar aproximación")
        }
        Button(onClick = { onSave(exercise.id, sets) }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar series de aproximación", fontWeight = FontWeight.Black)
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
        Text("AUGE en vivo", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "Monitorea fatiga, alertas y margen antes de cerrar o guardar la sesión.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = accentColor.copy(alpha = 0.10f),
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                accentColor.copy(alpha = 0.24f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            augeStatusLabel(summary.status),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "${summary.combinedSessionDrainPercent()}% de carga en esta sesión · ${summary.combinedWeeklyDrainPercent()}% acumulado semanal",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = if (summary.alertCount > 0) "${summary.alertCount} alertas" else "En rango",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = accentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

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
            }
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

        if (summary.alerts.isEmpty()) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sin alertas prioritarias", fontWeight = FontWeight.Black)
                    Text(
                        "La sesión quedó dentro de un rango razonable. Revisa las oportunidades si quieres ajustar el estímulo fino.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
            AugeSectionTitle(
                title = "Oportunidades",
                subtitle = "Pequeños ajustes con margen para afinar el estímulo sin salirte del marco actual.",
            )
            summary.suggestions.forEach { alert ->
                AugeAlertCard(
                    alert = alert,
                    onApplyCorrection = onApplyAugeCorrection,
                )
            }
        }

        if (summary.topExercises.isNotEmpty()) {
            AugeSectionTitle(
                title = "Ejercicios que más pesan",
                subtitle = "Útil para ver rápido dónde se está yendo la fatiga dentro de la sesión.",
            )
            summary.topExercises.forEach { insight ->
                AugeInsightCard(insight = insight)
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("$safeValue%", color = color, fontWeight = FontWeight.Black)
        }
        LinearProgressIndicator(
            progress = { safeValue / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
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

@Composable
private fun AugeInsightCard(
    insight: SessionEditorAugeExerciseInsight,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        insight.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        insight.suggestion ?: "Carga repartida, sin un aviso puntual para este ejercicio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = drainColor(insight.total).copy(alpha = 0.12f),
                ) {
                    Text(
                        "Total ${insight.total}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        color = drainColor(insight.total),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            AugePercentBar("Muscular", insight.muscular)
            AugePercentBar("SNC", insight.cns)
            AugePercentBar("Espinal", insight.spinal)
        }
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
    critical -> Color(0xFFDC2626)
    status == SessionEditorAugeStatus.OPTIMAL -> Color(0xFF16A34A)
    status == SessionEditorAugeStatus.WARNING -> Color(0xFFF59E0B)
    else -> Color(0xFFEA580C)
}

private fun augeSeverityLabel(severity: SessionEditorAugeAlertSeverity): String = when (severity) {
    SessionEditorAugeAlertSeverity.INFO -> "Info"
    SessionEditorAugeAlertSeverity.WARNING -> "Atención"
    SessionEditorAugeAlertSeverity.CRITICAL -> "Crítica"
}

private fun augeSeverityColor(severity: SessionEditorAugeAlertSeverity): Color = when (severity) {
    SessionEditorAugeAlertSeverity.INFO -> Color(0xFF2563EB)
    SessionEditorAugeAlertSeverity.WARNING -> Color(0xFFF59E0B)
    SessionEditorAugeAlertSeverity.CRITICAL -> Color(0xFFDC2626)
}

private fun augeSeverityIcon(severity: SessionEditorAugeAlertSeverity): ImageVector = when (severity) {
    SessionEditorAugeAlertSeverity.INFO -> Icons.Default.TipsAndUpdates
    SessionEditorAugeAlertSeverity.WARNING -> Icons.Default.AutoAwesome
    SessionEditorAugeAlertSeverity.CRITICAL -> Icons.Default.WorkspacePremium
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
    else -> Color(0xFFDC2626)
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
