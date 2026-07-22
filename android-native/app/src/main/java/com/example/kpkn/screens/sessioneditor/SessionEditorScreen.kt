package com.example.kpkn.screens.sessioneditor

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import com.example.kpkn.domain.templates.SplitTemplateDayGroup
import com.example.kpkn.domain.templates.FocusTemplateGroup
import androidx.compose.animation.animateContentSize
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.calculations.calculateEstimatedMetric
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.domain.calculations.resolveReferenceCapacity
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios
import com.example.kpkn.screens.wikilab.CustomExerciseCreatorContent
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

private fun String.toEditorColor(default: Color = Color(0xFF00F0FF)): Color =
    runCatching { Color(AndroidColor.parseColor(this)) }.getOrDefault(default)

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

@Composable
private fun DragLiftPreview(
    exercise: Exercise,
    rect: Rect,
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Surface(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (rect.left + offset.x).roundToInt(),
                    y = (rect.top + offset.y).roundToInt(),
                )
            }
            .width(with(density) { rect.width.toDp() })
            .heightIn(min = 70.dp),
        shape = RoundedCornerShape(20.dp),
        color = DarkEditorSurface.copy(alpha = 0.98f),
        shadowElevation = 28.dp,
        tonalElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name.ifBlank { "Ejercicio" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.sets.size} series · ${trainingModeLabel(exercise.trainingMode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DropGapProjection(
    visible: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)) + expandVertically(animationSpec = tween(140)),
        exit = fadeOut(tween(80)) + shrinkVertically(animationSpec = tween(120)),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            color = accentColor.copy(alpha = 0.14f),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = accentColor.copy(alpha = 0.48f),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
                            ),
                            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Soltar aquí",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = accentColor,
                )
            }
        }
    }
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
    SessionCoverGradient("gradient://graphite", "Graphite", listOf(Color(0xFF09090B), Color(0xFF27272A), Color(0xFF52525B))),
    SessionCoverGradient("gradient://steel-blue", "Steel Blue", listOf(Color(0xFF0F172A), Color(0xFF1E3A5F), Color(0xFF38BDF8))),
    SessionCoverGradient("gradient://deep-red", "Deep Red", listOf(Color(0xFF120607), Color(0xFF7F1D1D), Color(0xFFEF4444))),
    SessionCoverGradient("gradient://mint-night", "Mint Night", listOf(Color(0xFF07130F), Color(0xFF14532D), Color(0xFF34D399))),
    SessionCoverGradient("gradient://indigo", "Indigo", listOf(Color(0xFF111827), Color(0xFF3730A3), Color(0xFF818CF8))),
    SessionCoverGradient("gradient://bronze", "Bronze", listOf(Color(0xFF15100A), Color(0xFF92400E), Color(0xFFF59E0B))),
)

private val sessionSolidPresets = listOf(
    SessionCoverGradient("solid://obsidian", "Obsidian", listOf(Color(0xFF111318), Color(0xFF111318), Color(0xFF111318))),
    SessionCoverGradient("solid://steel", "Steel", listOf(Color(0xFF334155), Color(0xFF334155), Color(0xFF334155))),
    SessionCoverGradient("solid://ember-red", "Ember Red", listOf(Color(0xFF7F1D1D), Color(0xFF7F1D1D), Color(0xFF7F1D1D))),
    SessionCoverGradient("solid://ocean", "Ocean", listOf(Color(0xFF0F3D5E), Color(0xFF0F3D5E), Color(0xFF0F3D5E))),
    SessionCoverGradient("solid://moss", "Moss", listOf(Color(0xFF244B3C), Color(0xFF244B3C), Color(0xFF244B3C))),
    SessionCoverGradient("solid://charcoal", "Charcoal", listOf(Color(0xFF1F2329), Color(0xFF1F2329), Color(0xFF1F2329))),
    SessionCoverGradient("solid://slate", "Slate", listOf(Color(0xFF283241), Color(0xFF283241), Color(0xFF283241))),
    SessionCoverGradient("solid://wine", "Wine", listOf(Color(0xFF581C27), Color(0xFF581C27), Color(0xFF581C27))),
    SessionCoverGradient("solid://pine", "Pine", listOf(Color(0xFF12352A), Color(0xFF12352A), Color(0xFF12352A))),
    SessionCoverGradient("solid://navy", "Navy", listOf(Color(0xFF10233F), Color(0xFF10233F), Color(0xFF10233F))),
    SessionCoverGradient("solid://aubergine", "Aubergine", listOf(Color(0xFF2A1835), Color(0xFF2A1835), Color(0xFF2A1835))),
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
    openCompetitionConfig: Boolean = false,
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTemplates by viewModel.allTemplates.collectAsStateWithLifecycle()
    val session = uiState.session
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAutoExpandExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCompetitionConfigSheet by rememberSaveable { mutableStateOf(openCompetitionConfig) }
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
            blurRadius = 28.dp,
            tint = HazeTint(Color.Black.copy(alpha = 0.44f)),
            backgroundColor = Color.Black.copy(alpha = 0.18f),
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
    var dragStartPartRect by remember { mutableStateOf<Rect?>(null) }
    var dragStartExerciseRect by remember { mutableStateOf<Rect?>(null) }

    fun beginExerciseDrag(partId: String, exerciseId: String) {
        draggingExerciseId = exerciseId
        draggingExercisePartId = partId
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        dragStartExerciseRect = exerciseBounds["$partId|$exerciseId"]
    }

    fun updateExerciseDrag(delta: Offset) {
        val activeSession = uiState.session ?: return
        val groupedPartsForDrag = activeSession.parts.filterNot { it.isUncategorized() }
        val activeExerciseId = draggingExerciseId ?: return
        val currentPartId = draggingExercisePartId ?: return
        draggingExerciseOffset += delta
        val startRect = dragStartExerciseRect ?: exerciseBounds["$currentPartId|$activeExerciseId"] ?: return
        val center = Offset(startRect.center.x + draggingExerciseOffset.x, startRect.center.y + draggingExerciseOffset.y)
        val targetPartId = when {
            looseContentBounds?.contains(center) == true -> "__loose__"
            else -> groupedPartsForDrag.firstOrNull { candidate -> partContentBounds[candidate.id]?.contains(center) == true }?.id
        }
        exerciseDropTargetPartId = targetPartId
        if (targetPartId != null) {
            val sourceList = if (currentPartId == "__loose__") activeSession.exercises else activeSession.parts.firstOrNull { it.id == currentPartId }?.exercises.orEmpty()
            val draggedGroupId = sourceList.firstOrNull { it.id == activeExerciseId }?.supersetGroupRefOrLegacyId()
            val draggedIds = if (draggedGroupId != null) {
                sourceList.filter { it.supersetGroupRefOrLegacyId() == draggedGroupId }.map { it.id }.toSet()
            } else {
                setOf(activeExerciseId)
            }
            val orderedKeys = exerciseBounds
                .filterKeys { it.startsWith("$targetPartId|") }
                .filterKeys { key -> key.substringAfter("|") !in draggedIds }
                .entries
                .sortedBy { it.value.center.y }
            val before = orderedKeys.firstOrNull { (_, rect) -> center.y < rect.center.y }
            exerciseDropTargetKey = before?.key
            exerciseDropTargetIndex = if (before != null) {
                val targetExerciseId = before.key.substringAfter("|")
                val targetList = if (targetPartId == "__loose__") activeSession.exercises else activeSession.parts.firstOrNull { it.id == targetPartId }?.exercises.orEmpty()
                targetList.indexOfFirst { it.id == targetExerciseId }.takeIf { it >= 0 } ?: 0
            } else {
                val targetListSize = when (targetPartId) {
                    "__loose__" -> activeSession.exercises.size
                    else -> activeSession.parts.firstOrNull { it.id == targetPartId }?.exercises?.size ?: 0
                }
                targetListSize
            }
        } else {
            exerciseDropTargetKey = null
            exerciseDropTargetIndex = null
        }
    }

    fun endExerciseDrag() {
        val activeSession = uiState.session ?: return
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
                    "__loose__" -> activeSession.exercises.indexOfFirst { it.id == tExId }
                    else -> activeSession.parts.firstOrNull { it.id == tPartId }?.exercises?.indexOfFirst { it.id == tExId }
                }
                if (idx != null && idx >= 0) {
                    viewModel.moveExerciseToPart(currentPartId.takeUnless { it == "__loose__" }, activeExerciseId, tPartId.takeUnless { it == "__loose__" }, idx)
                }
            } else if (finalTargetPart != null && finalTargetPart != currentPartId) {
                viewModel.moveExerciseToPart(currentPartId.takeUnless { it == "__loose__" }, activeExerciseId, finalTargetPart.takeUnless { it == "__loose__" }, null)
            } else if (finalTargetIdx != null) {
                viewModel.moveExerciseToPart(currentPartId.takeUnless { it == "__loose__" }, activeExerciseId, currentPartId.takeUnless { it == "__loose__" }, finalTargetIdx)
            }
        }
        draggingExerciseId = null
        draggingExercisePartId = null
        draggingExerciseOffset = Offset.Zero
        exerciseDropTargetKey = null
        exerciseDropTargetPartId = null
        exerciseDropTargetIndex = null
        dragStartExerciseRect = null
    }

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
    val draggedExerciseIds = remember(session, draggingExerciseId, draggingExercisePartId) {
        val activeId = draggingExerciseId ?: return@remember emptySet<String>()
        val sourcePartId = draggingExercisePartId
        val sourceList = when (sourcePartId) {
            "__loose__" -> session.exercises
            null -> emptyList()
            else -> session.parts.firstOrNull { it.id == sourcePartId }?.exercises.orEmpty()
        }
        val groupId = sourceList.firstOrNull { it.id == activeId }?.supersetGroupRefOrLegacyId()
        if (groupId != null) sourceList.filter { it.supersetGroupRefOrLegacyId() == groupId }.map { it.id }.toSet() else setOf(activeId)
    }

    fun projectedShiftFor(
        partId: String,
        index: Int,
        exerciseId: String,
        itemHeight: Float = exerciseBounds["$partId|$exerciseId"]?.height ?: 88f,
    ): Float {
        val activeId = draggingExerciseId ?: return 0f
        val sourcePartId = draggingExercisePartId ?: return 0f
        val keyTargetPart = exerciseDropTargetKey?.substringBefore("|")
        val keyTargetExercise = exerciseDropTargetKey?.substringAfter("|")
        val targetPartId = exerciseDropTargetPartId ?: keyTargetPart ?: return 0f
        val targetIndex = exerciseDropTargetIndex ?: keyTargetExercise?.let { targetExerciseId ->
            val targetList = if (targetPartId == "__loose__") session.exercises else session.parts.firstOrNull { it.id == targetPartId }?.exercises.orEmpty()
            targetList.indexOfFirst { it.id == targetExerciseId }.takeIf { it >= 0 }
        } ?: return 0f
        if (exerciseId in draggedExerciseIds) return 0f
        if (partId != targetPartId) return 0f
        val movingCount = draggedExerciseIds.size.coerceAtLeast(1)
        val gap = (itemHeight + 10f) * movingCount
        if (partId != sourcePartId) {
            return if (index >= targetIndex) gap else 0f
        }
        val sourceList = if (partId == "__loose__") session.exercises else session.parts.firstOrNull { it.id == partId }?.exercises.orEmpty()
        val sourceIndex = sourceList.indexOfFirst { it.id == activeId }
        if (sourceIndex < 0 || targetIndex == sourceIndex) return 0f
        return when {
            targetIndex < sourceIndex && index >= targetIndex && index < sourceIndex -> gap
            targetIndex > sourceIndex && index > sourceIndex && index < targetIndex -> -gap
            targetIndex >= sourceList.size && index > sourceIndex -> -gap
            else -> 0f
        }
    }

    LaunchedEffect(openCompetitionConfig, session.id) {
        if (openCompetitionConfig && session.isMeetDay) {
            showCompetitionConfigSheet = true
        }
    }

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
                HeroGlassFab(
                    summary = uiState.augeSummary,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 104.dp),
                    onClick = { viewModel.openSheet(SessionEditorSheet.AUGE) },
                )
            },
            floatingActionButtonPosition = FabPosition.End,
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
                    onMeetDayChange = {},
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
                    onOpenCoverSheet = { viewModel.openSheet(SessionEditorSheet.BACKGROUND) },
                    onOpenTransfer = { viewModel.openSheet(SessionEditorSheet.TRANSFER) },
                    onOpenHistory = { viewModel.openSheet(SessionEditorSheet.HISTORY) },
                    onOpenRules = { viewModel.openSheet(SessionEditorSheet.RULES) },
                    onAutoSaveToggle = { viewModel.setAutoSaveEnabled(!uiState.autoSaveEnabled) },
                    sessionsOnSameDay = uiState.siblingSessions.filter { it.dayOfWeek == session.dayOfWeek },
                    onSwitchSession = viewModel::requestSessionSwitch,
                    onSetMainSession = viewModel::setMainSessionForDay,
                    // Feature 2
                    targetDurationMinutes = uiState.targetDurationMinutes ?: session.targetDurationMinutes,
                    sessionTimeBreakdown = uiState.sessionTimeBreakdown,
                    onSetTargetDuration = viewModel::setTargetDuration,
                    // Feature 3
                    activeVariant = uiState.activeVariant,
                    availableVariants = uiState.availableVariants,
                    onCreateVariant = { variant, name -> viewModel.createVariant(variant, name) },
                    onDeleteVariant = { viewModel.deleteVariant(it) },
                    onSwitchVariant = { viewModel.commitActiveVariantChanges(); viewModel.switchVariant(it) },
                )
            }

            if (session.isMeetDay) {
                item("competition-mode-editor") {
                    CompetitionSessionEditor(
                        session = session,
                        onUpdateSession = { updater: (Session) -> Session -> viewModel.updateCurrentSession(updater) },
                        onOpenConfig = { showCompetitionConfigSheet = true },
                        onAddCompetitionMovement = {
                            viewModel.openSheet(SessionEditorSheet.EXERCISE_PICKER)
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
                            .onGloballyPositioned { looseContentBounds = it.boundsInRoot() },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        session.exercises.forEachIndexed { index, exercise ->
                            val supersetGroupId = exercise.supersetGroupRefOrLegacyId()
                            if (supersetGroupId != null) {
                                val isFirstSupersetMember = session.exercises.firstOrNull { it.supersetGroupRefOrLegacyId() == supersetGroupId }?.id == exercise.id
                                if (!isFirstSupersetMember) return@forEachIndexed
                                val supersetGroup = session.allSupersetGroups().firstOrNull { it.id == supersetGroupId }
                                val supersetMembers = SupersetRules.orderedMembers(session, supersetGroupId)
                                    .filter { member -> session.exercises.any { it.id == member.id } }
                                if (supersetGroup != null && supersetMembers.size >= 2) {
                                    DropGapProjection(
                                        visible = draggingExerciseId != null && ((exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == index) || exerciseDropTargetKey == "__loose__|${exercise.id}"),
                                        accentColor = PART_COLORS.first().toEditorColor(),
                                    )
                                    val projectedShift by animateFloatAsState(
                                        targetValue = projectedShiftFor("__loose__", index, supersetMembers.first().id),
                                        animationSpec = tween(150),
                                        label = "looseSupersetProjectedShift",
                                    )
                                        SupersetGroupEditorCard(
                                        group = supersetGroup,
                                        exercises = supersetMembers,
                                        accentHex = PART_COLORS.first(),
                                        partId = null,
                                        isDragging = draggingExerciseId == supersetMembers.first().id,
                                        dragOffset = if (draggingExerciseId == supersetMembers.first().id) draggingExerciseOffset else Offset.Zero,
                                        modifier = Modifier.graphicsLayer { translationY = projectedShift },
                                        onBoundsChange = { rect -> exerciseBounds["__loose__|${supersetMembers.first().id}"] = rect },
                                        onDragStart = { beginExerciseDrag("__loose__", supersetMembers.first().id) },
                                        onDrag = ::updateExerciseDrag,
                                        onDragEnd = ::endExerciseDrag,
                                        onOpenSupersetCreator = viewModel::openSupersetCreator,
                                        onUpdateSupersetRest = viewModel::updateSupersetRest,
                                        onUpdateRoundRest = viewModel::updateSupersetRoundRest,
                                        onToggleOptional = viewModel::toggleSupersetOptional,
                                        onUpdateExercise = { exerciseId, updater -> viewModel.updateExercise(null, exerciseId, updater) },
                                        onAddSet = { exerciseId -> viewModel.addSet(null, exerciseId) },
                                        onUpdateSet = { exerciseId, setId, updater -> viewModel.updateSet(null, exerciseId, setId, updater) },
                                        onRemoveSet = { exerciseId, setId -> viewModel.removeSet(null, exerciseId, setId) },
                                        onMoveSet = { exerciseId, setId, dir -> viewModel.moveSet(null, exerciseId, setId, dir) },
                                        onRemoveRound = { roundIndex -> viewModel.removeSupersetRound(supersetGroup.id, null, roundIndex) },
                                        relationshipAnchorName = { member -> resolveRelationshipAnchorName(session, member) },
                                        onOpenRelationshipPicker = { exerciseId -> viewModel.openRelationshipPicker(null, exerciseId) },
                                        onClearRelationship = { exerciseId -> viewModel.linkExerciseRelativeTo(null, exerciseId, null) },
                                        onRemoveFromSuperset = { groupId, exerciseId -> viewModel.removeExerciseFromSupersetGroup(groupId, null, exerciseId) },
                                        onDissolve = viewModel::dissolveSupersetGroup,
                                        onAddRound = {
                                            val nextRound = ((supersetGroup.rounds ?: supersetMembers.maxOfOrNull { it.sets.size } ?: 0) + 1).coerceAtLeast(1)
                                            viewModel.updateSupersetRest(supersetGroup.id, null, null, nextRound)
                                            supersetMembers.forEach { member ->
                                                if (member.sets.size < nextRound) viewModel.addSet(null, member.id)
                                            }
                                        },
                                    ) {
                                        supersetMembers.forEach { member ->
                                            val memberIndex = session.exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: index
                                            key("loose|${member.id}") {
                                                ExerciseEditorCard(
                                                    exercise = member,
                                                    exerciseInfo = EXERCISE_DATABASE.find { it.id == member.exerciseDbId },
                                                    accentHex = PART_COLORS.first(),
                                                    partId = "__loose__",
                                                    isCompetitionMovement = member.matchesCompetitionMovement(uiState.competitionMovementIds),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    isDragging = draggingExerciseId == member.id,
                                                    dragOffset = if (draggingExerciseId == member.id) draggingExerciseOffset else Offset.Zero,
                                                    isDropTarget = (exerciseDropTargetKey == "__loose__|${member.id}" || (exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == memberIndex)) && draggingExerciseId != member.id,
                                                    isPartDropTarget = exerciseDropTargetPartId == "__loose__" && draggingExerciseId != member.id,
                                                    onBoundsChange = { rect -> exerciseBounds["__loose__|${member.id}"] = rect },
                                                    onDragStart = { beginExerciseDrag("__loose__", member.id) },
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
                                                            exerciseDropTargetPartId = null
                                                            exerciseDropTargetIndex = null
                                                        } else {
                                                            exerciseDropTargetKey = null
                                                            val targetPartId = when {
                                                                looseContentBounds?.contains(center) == true -> "__loose__"
                                                                else -> groupedParts.firstOrNull { candidate ->
                                                                    partContentBounds[candidate.id]?.contains(center) == true
                                                                }?.id
                                                            }
                                                            exerciseDropTargetPartId = targetPartId
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
                                                    onUpdateExercise = { updater -> viewModel.updateExercise(null, member.id, updater) },
                                                    onAddSet = { viewModel.addSet(null, member.id) },
                                                    onUpdateSet = { setId, updater -> viewModel.updateSet(null, member.id, setId, updater) },
                                                    onRemoveSet = { setId -> viewModel.removeSet(null, member.id, setId) },
                                                    onMoveSet = { setId, dir -> viewModel.moveSet(null, member.id, setId, dir) },
                                                    onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(null, member.id, mobilityId) },
                                                    onOpenQuickActions = { viewModel.openExerciseQuickActions(null, member.id) },
                                                    relationshipAnchorName = resolveRelationshipAnchorName(session, member),
                                                    onOpenRelationshipPicker = { viewModel.openRelationshipPicker(null, member.id) },
                                                    onClearRelationship = { viewModel.linkExerciseRelativeTo(null, member.id, null) },
                                                    onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(null, member.id, type) },
                                                    onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(null, member.id, notes) },
                                                    autoExpand = pendingAutoExpandExerciseId == member.id,
                                                    onAutoExpandHandled = {
                                                        if (pendingAutoExpandExerciseId == member.id) pendingAutoExpandExerciseId = null
                                                    },
                                                    suppressIndividualRest = true,
                                                )
                                            }
                                        }
                                    }
                                    return@forEachIndexed
                                }
                            }
                            key("loose|${exercise.id}") {
                                DropGapProjection(
                                    visible = draggingExerciseId != null && ((exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == index) || exerciseDropTargetKey == "__loose__|${exercise.id}"),
                                    accentColor = PART_COLORS.first().toEditorColor(),
                                )
                                val projectedShift by animateFloatAsState(
                                    targetValue = projectedShiftFor("__loose__", index, exercise.id),
                                    animationSpec = tween(150),
                                    label = "looseExerciseProjectedShift",
                                )
                                ExerciseEditorCard(
                                    exercise = exercise,
                                    exerciseInfo = EXERCISE_DATABASE.find { it.id == exercise.exerciseDbId },
                                    accentHex = PART_COLORS.first(),
                                    partId = "__loose__",
                                    isCompetitionMovement = exercise.matchesCompetitionMovement(uiState.competitionMovementIds),
                                    modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = projectedShift },
                                    isDragging = draggingExerciseId == exercise.id,
                                    dragOffset = if (draggingExerciseId == exercise.id) draggingExerciseOffset else Offset.Zero,
                                    isDropTarget = (exerciseDropTargetKey == "__loose__|${exercise.id}" || ((exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == index) || exerciseDropTargetKey == "__loose__|${exercise.id}")) && draggingExerciseId != exercise.id,
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
                                    onAddSet = { side -> viewModel.addSet(null, exercise.id, side) },
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
                                    ((exerciseDropTargetPartId == "__loose__" && exerciseDropTargetIndex == index) || exerciseDropTargetKey == "__loose__|${exercise.id}")
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
                                val currentSupersetId = exercise.supersetGroupRefOrLegacyId()
                                val nextSupersetId = session.exercises[index + 1].supersetGroupRefOrLegacyId()
                                currentSupersetId == null || currentSupersetId != nextSupersetId
                            } else {
                                false
                            }
                            if (shouldDrawDivider) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                )
                            } else if (exercise.supersetGroupRefOrLegacyId() != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .padding(horizontal = 20.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
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
                        dragStartPartRect = partBounds[part.id]
                    },
                    onDrag = { deltaY ->
                        val activeId = draggingPartId ?: return@GroupEditorCard
                        draggingPartOffsetY += deltaY
                        val startRect = dragStartPartRect ?: partBounds[activeId] ?: return@GroupEditorCard
                        val centerY = startRect.center.y + draggingPartOffsetY
                        val targetId = groupedParts.firstOrNull { candidate ->
                            candidate.id != activeId && partBounds[candidate.id]?.contains(Offset(startRect.center.x, centerY)) == true
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
                        dragStartPartRect = null
                    },
                    onAddExercise = { viewModel.openPicker(part.id) },
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            part.exercises.forEachIndexed { targetIndex, exercise ->
                                val supersetGroupId = exercise.supersetGroupRefOrLegacyId()
                                if (supersetGroupId != null) {
                                    val isFirstSupersetMember = part.exercises.firstOrNull { it.supersetGroupRefOrLegacyId() == supersetGroupId }?.id == exercise.id
                                    if (!isFirstSupersetMember) return@forEachIndexed
                                    val supersetGroup = session.allSupersetGroups().firstOrNull { it.id == supersetGroupId }
                                    val supersetMembers = SupersetRules.orderedMembers(session, supersetGroupId)
                                        .filter { member -> part.exercises.any { it.id == member.id } }
                                    if (supersetGroup != null && supersetMembers.size >= 2) {
                                        DropGapProjection(
                                            visible = draggingExerciseId != null && ((exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == targetIndex) || exerciseDropTargetKey == "${part.id}|${exercise.id}"),
                                            accentColor = (part.color ?: PART_COLORS.first()).toEditorColor(),
                                        )
                                        val projectedShift by animateFloatAsState(
                                            targetValue = projectedShiftFor(part.id, targetIndex, supersetMembers.first().id),
                                            animationSpec = tween(150),
                                            label = "partSupersetProjectedShift",
                                        )
                                        SupersetGroupEditorCard(
                                            group = supersetGroup,
                                        exercises = supersetMembers,
                                        accentHex = part.color,
                                        partId = part.id,
                                        isDragging = draggingExerciseId == supersetMembers.first().id,
                                        dragOffset = if (draggingExerciseId == supersetMembers.first().id) draggingExerciseOffset else Offset.Zero,
                                        modifier = Modifier.graphicsLayer { translationY = projectedShift },
                                        onBoundsChange = { rect -> exerciseBounds["${part.id}|${supersetMembers.first().id}"] = rect },
                                        onDragStart = { beginExerciseDrag(part.id, supersetMembers.first().id) },
                                        onDrag = ::updateExerciseDrag,
                                        onDragEnd = ::endExerciseDrag,
                                        onOpenSupersetCreator = viewModel::openSupersetCreator,
                                            onUpdateSupersetRest = viewModel::updateSupersetRest,
                                            onUpdateRoundRest = viewModel::updateSupersetRoundRest,
                                            onToggleOptional = viewModel::toggleSupersetOptional,
                                            onUpdateExercise = { exerciseId, updater -> viewModel.updateExercise(part.id, exerciseId, updater) },
                                            onAddSet = { exerciseId -> viewModel.addSet(part.id, exerciseId) },
                                            onUpdateSet = { exerciseId, setId, updater -> viewModel.updateSet(part.id, exerciseId, setId, updater) },
                                            onRemoveSet = { exerciseId, setId -> viewModel.removeSet(part.id, exerciseId, setId) },
                                            onMoveSet = { exerciseId, setId, dir -> viewModel.moveSet(part.id, exerciseId, setId, dir) },
                                            onRemoveRound = { roundIndex -> viewModel.removeSupersetRound(supersetGroup.id, part.id, roundIndex) },
                                            relationshipAnchorName = { member -> resolveRelationshipAnchorName(session, member) },
                                            onOpenRelationshipPicker = { exerciseId -> viewModel.openRelationshipPicker(part.id, exerciseId) },
                                            onClearRelationship = { exerciseId -> viewModel.linkExerciseRelativeTo(part.id, exerciseId, null) },
                                            onRemoveFromSuperset = { groupId, exerciseId -> viewModel.removeExerciseFromSupersetGroup(groupId, part.id, exerciseId) },
                                            onDissolve = viewModel::dissolveSupersetGroup,
                                            onAddRound = {
                                                val nextRound = ((supersetGroup.rounds ?: supersetMembers.maxOfOrNull { it.sets.size } ?: 0) + 1).coerceAtLeast(1)
                                                viewModel.updateSupersetRest(supersetGroup.id, null, null, nextRound)
                                                supersetMembers.forEach { member ->
                                                    if (member.sets.size < nextRound) viewModel.addSet(part.id, member.id)
                                                }
                                            },
                                        ) {
                                            supersetMembers.forEach { member ->
                                                val memberIndex = part.exercises.indexOfFirst { it.id == member.id }.takeIf { it >= 0 } ?: targetIndex
                                                key("${part.id}|${member.id}") {
                                                    ExerciseEditorCard(
                                                        exercise = member,
                                                        exerciseInfo = EXERCISE_DATABASE.find { it.id == member.exerciseDbId },
                                                        accentHex = part.color,
                                                        partId = part.id,
                                                        isCompetitionMovement = member.matchesCompetitionMovement(uiState.competitionMovementIds),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        isDragging = draggingExerciseId == member.id,
                                                        dragOffset = if (draggingExerciseId == member.id) draggingExerciseOffset else Offset.Zero,
                                                        isDropTarget = (exerciseDropTargetKey == "${part.id}|${member.id}" || (exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == memberIndex)) && draggingExerciseId != member.id,
                                                        isPartDropTarget = exerciseDropTargetPartId == part.id && draggingExerciseId != member.id,
                                                        onBoundsChange = { rect -> exerciseBounds["${part.id}|${member.id}"] = rect },
                                                        onDragStart = {
                                                            draggingExerciseId = member.id
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

                                                            val targetExerciseKey = exerciseBounds.entries.firstOrNull { (key, rect) ->
                                                                key != "$currentPartId|$activeExerciseId" && rect.contains(center)
                                                            }?.key
                                                            if (targetExerciseKey != null) {
                                                                exerciseDropTargetKey = targetExerciseKey
                                                                exerciseDropTargetPartId = null
                                                                exerciseDropTargetIndex = null
                                                            } else {
                                                                exerciseDropTargetKey = null
                                                                val targetPartId = when {
                                                                    looseContentBounds?.contains(center) == true -> "__loose__"
                                                                    else -> groupedParts.firstOrNull { candidate ->
                                                                        partContentBounds[candidate.id]?.contains(center) == true
                                                                    }?.id
                                                                }
                                                                exerciseDropTargetPartId = targetPartId
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
                                                        onUpdateExercise = { updater -> viewModel.updateExercise(part.id, member.id, updater) },
                                                        onAddSet = { viewModel.addSet(part.id, member.id) },
                                                        onUpdateSet = { setId, updater -> viewModel.updateSet(part.id, member.id, setId, updater) },
                                                        onRemoveSet = { setId -> viewModel.removeSet(part.id, member.id, setId) },
                                                        onMoveSet = { setId, dir -> viewModel.moveSet(part.id, member.id, setId, dir) },
                                                        onRemoveMobility = { mobilityId -> viewModel.removeMobilitySeries(part.id, member.id, mobilityId) },
                                                        onOpenQuickActions = { viewModel.openExerciseQuickActions(part.id, member.id) },
                                                        relationshipAnchorName = resolveRelationshipAnchorName(session, member),
                                                        onOpenRelationshipPicker = { viewModel.openRelationshipPicker(part.id, member.id) },
                                                        onClearRelationship = { viewModel.linkExerciseRelativeTo(part.id, member.id, null) },
                                                        onUpdateRelationshipType = { type -> viewModel.updateExerciseRelationshipType(part.id, member.id, type) },
                                                        onUpdateRelationshipNotes = { notes -> viewModel.updateExerciseRelationshipNotes(part.id, member.id, notes) },
                                                        autoExpand = pendingAutoExpandExerciseId == member.id,
                                                        onAutoExpandHandled = {
                                                            if (pendingAutoExpandExerciseId == member.id) pendingAutoExpandExerciseId = null
                                                        },
                                                        suppressIndividualRest = true,
                                                    )
                                                }
                                            }
                                        }
                                        return@forEachIndexed
                                    }
                                }
                                    key("${part.id}|${exercise.id}") {
                                        DropGapProjection(
                                            visible = draggingExerciseId != null && ((exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == targetIndex) || exerciseDropTargetKey == "${part.id}|${exercise.id}"),
                                            accentColor = (part.color ?: PART_COLORS.first()).toEditorColor(),
                                        )
                                        val projectedShift by animateFloatAsState(
                                            targetValue = projectedShiftFor(part.id, targetIndex, exercise.id),
                                            animationSpec = tween(150),
                                            label = "partExerciseProjectedShift",
                                        )
                                        ExerciseEditorCard(
                                            exercise = exercise,
                                            exerciseInfo = EXERCISE_DATABASE.find { it.id == exercise.exerciseDbId },
                                            accentHex = part.color,
                                            partId = part.id,
                                            isCompetitionMovement = exercise.matchesCompetitionMovement(uiState.competitionMovementIds),
                                            modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = projectedShift },
                                            isDragging = draggingExerciseId == exercise.id,
                                            dragOffset = if (draggingExerciseId == exercise.id) draggingExerciseOffset else Offset.Zero,
                                            isDropTarget = (exerciseDropTargetKey == "${part.id}|${exercise.id}" || ((exerciseDropTargetPartId == part.id && exerciseDropTargetIndex == targetIndex) || exerciseDropTargetKey == "${part.id}|${exercise.id}")) && draggingExerciseId != exercise.id,
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
                                            onAddSet = { side -> viewModel.addSet(part.id, exercise.id, side) },
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
                                    val currentSupersetId = exercise.supersetGroupRefOrLegacyId()
                                    val nextSupersetId = part.exercises[targetIndex + 1].supersetGroupRefOrLegacyId()
                                    currentSupersetId == null || currentSupersetId != nextSupersetId
                                } else {
                                    false
                                }
                                if (shouldDrawDivider) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    )
                                } else if (exercise.supersetGroupRefOrLegacyId() != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .padding(horizontal = 20.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
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
                    FilledTonalButton(
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(250f),
        ) {
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
                competitionKeyDaysInWeek = uiState.competitionKeyDaysInWeek,
                onCreateSessionForDay = { day ->
                    viewModel.createSessionForDay(day)
                },
                onCreateCompetitionSessionForDay = { day ->
                    viewModel.createCompetitionSessionForDay(day)
                },
                isSimpleProgram = uiState.isSimpleProgram,
                hasActiveLoops = uiState.hasActiveLoops,
                hazeState = hazeState,
                hazeStyle = roadmapGlassStyle,
                onSetMainSessionForDay = viewModel::setMainSessionForDay,
                currentSessionId = session.id,
                currentDayOfWeek = uiState.dayOfWeek,
            )
        }

        val previewExercise = draggingExerciseId?.let { activeId -> session.allExercises().firstOrNull { it.id == activeId } }
        val previewPartId = draggingExercisePartId
        val previewRect = if (previewPartId != null && draggingExerciseId != null) {
            exerciseBounds["$previewPartId|$draggingExerciseId"]
        } else {
            null
        }
        if (previewExercise != null && previewRect != null) {
            DragLiftPreview(
                exercise = previewExercise,
                rect = previewRect,
                offset = draggingExerciseOffset,
                modifier = Modifier.zIndex(500f),
            )
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
        onToggleExerciseSelection = viewModel::toggleExerciseSelection,
        onClearExerciseSelection = viewModel::clearExerciseSelection,
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
                snackbarHostState.showKpknSnackbar("Ajuste aplicado", SnackbarType.SUCCESS)
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
        onRuleDefaultsChange = { partId, setCount, reps, rpe, normalRest, sideRest, supersetBetween, supersetRound, applyToNew, intensityType ->
            viewModel.updateRuleDefaults(
                partId = partId,
                setCount = setCount,
                reps = reps,
                rpe = rpe,
                normalRestSeconds = normalRest,
                betweenSidesRestSeconds = sideRest,
                supersetBetweenRestSeconds = supersetBetween,
                supersetRoundRestSeconds = supersetRound,
                applyToNewItems = applyToNew,
                intensityType = intensityType,
            )
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
        setTargetDuration = viewModel::setTargetDuration,
        setPartTargetDuration = viewModel::setPartTargetDuration,
        setExerciseTargetDuration = viewModel::setExerciseTargetDuration,
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

    if (showCompetitionConfigSheet && session.isMeetDay) {
        CompetitionConfigSheet(
            session = session,
            onDismiss = { showCompetitionConfigSheet = false },
            onUpdateSession = { updater: (Session) -> Session -> viewModel.updateCurrentSession(updater) },
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
    onOpenCoverSheet: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onAutoSaveToggle: () -> Unit,
    sessionsOnSameDay: List<Session> = emptyList(),
    onSwitchSession: (String) -> Unit = {},
    onSetMainSession: (String) -> Unit = {},
    // Feature 2: duración objetivo
    targetDurationMinutes: Int? = null,
    sessionTimeBreakdown: com.example.kpkn.domain.calculations.SessionTimeBreakdown? = null,
    onSetTargetDuration: (Int?) -> Unit = {},
    // Feature 3: variantes derivadas de la original
    activeVariant: WeekVariant = WeekVariant.A,
    availableVariants: List<WeekVariant> = listOf(WeekVariant.A),
    onCreateVariant: (WeekVariant, String) -> Unit = { _, _ -> },
    onDeleteVariant: (WeekVariant) -> Unit = {},
    onSwitchVariant: (WeekVariant) -> Unit = {},
) {
    var showVariantMenu by remember { mutableStateOf(false) }
    var showCreateVariantDialog by remember { mutableStateOf(false) }
    var newVariantName by remember { mutableStateOf("") }
    val nextVariant = remember(availableVariants, session) {
        listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D)
            .firstOrNull { it !in availableVariants }
    }
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
                  verticalArrangement = Arrangement.spacedBy(0.dp),
              ) {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      // Left side: Variant chips
                      if (availableVariants.size > 1 || session.sessionB == null && session.sessionC == null && session.sessionD == null) {
                          Row(
                              modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                              horizontalArrangement = Arrangement.spacedBy(6.dp),
                              verticalAlignment = Alignment.CenterVertically,
                          ) {
                              availableVariants.forEach { variant ->
                                  val isActive = variant == activeVariant
                                  val variantName = when (variant) {
                                      WeekVariant.A -> "Original"
                                      WeekVariant.B -> session.sessionB?.name ?: "Derivada"
                                      WeekVariant.C -> session.sessionC?.name ?: "Derivada"
                                      WeekVariant.D -> session.sessionD?.name ?: "Derivada"
                                  }
                                  AssistChip(
                                      onClick = { if (!isActive) onSwitchVariant(variant) },
                                      label = {
                                          Text(
                                              variantName,
                                              style = MaterialTheme.typography.labelSmall,
                                              fontWeight = if (isActive) FontWeight.Black else FontWeight.Normal,
                                          )
                                      },
                                      leadingIcon = if (isActive) ({ Icon(Icons.Default.Check, null, Modifier.size(13.dp)) }) else null,
                                      trailingIcon = if (isActive && variant != WeekVariant.A) ({
                                          Box {
                                              Icon(
                                                  Icons.Default.MoreVert, null,
                                                  Modifier.size(13.dp).clickable { showVariantMenu = true },
                                              )
                                              DropdownMenu(expanded = showVariantMenu, onDismissRequest = { showVariantMenu = false }) {
                                                  DropdownMenuItem(
                                                      text = { Text("Eliminar variante") },
                                                      onClick = { showVariantMenu = false; onDeleteVariant(variant) },
                                                  )
                                              }
                                          }
                                      }) else null,
                                      shape = RoundedCornerShape(999.dp),
                                      colors = AssistChipDefaults.assistChipColors(
                                          containerColor = if (isActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                                          labelColor = Color.White,
                                      ),
                                  )
                              }
                              // Botón para crear nueva variante derivada
                              if (nextVariant != null) {
                                  Surface(
                                      onClick = {
                                          newVariantName = "${session.name} – Rápida"
                                          showCreateVariantDialog = true
                                      },
                                      shape = RoundedCornerShape(999.dp),
                                      color = Color.White.copy(alpha = 0.08f),
                                  ) {
                                      Row(
                                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                          horizontalArrangement = Arrangement.spacedBy(3.dp),
                                          verticalAlignment = Alignment.CenterVertically,
                                      ) {
                                          Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.75f))
                                          Text("Derivada", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                                      }
                                  }
                              }
                          }
                      }
                      Spacer(Modifier.width(8.dp))
                      // Right side: Auto:on, color, save
                      Row(
                          horizontalArrangement = Arrangement.spacedBy(6.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          DarkChoiceChip(
                              label = if (autoSaveEnabled) "Auto: On" else "Auto: Off",
                              selected = autoSaveEnabled,
                              onClick = onAutoSaveToggle,
                          )
                          Surface(
                              onClick = { onOpenCoverSheet() },
                              shape = CircleShape,
                              color = DarkEditorChip,
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
                          HeroGlassIconButton(
                              icon = Icons.Default.Save,
                              contentDescription = "Guardar sesión",
                              onClick = onSave,
                              showUnsavedDot = hasChanges,
                          )
                      }
                  }

                  val titleFontSize = when {
                      session.name.length < 15 -> 36.sp
                      session.name.length < 25 -> 28.sp
                      else -> 22.sp
                  }

                  BasicTextField(
                      value = session.name,
                      onValueChange = onNameChange,
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 18.dp),
                      singleLine = true,
                      textStyle = MaterialTheme.typography.displaySmall.copy(
                          fontSize = titleFontSize,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                      ),
                      cursorBrush = SolidColor(Color.White),
                      decorationBox = { innerTextField ->
                          Box(Modifier.fillMaxWidth()) {
                              if (session.name.isBlank()) Text("Nueva sesión", color = Color.White.copy(alpha = 0.72f), fontSize = titleFontSize, fontWeight = FontWeight.Bold)
                              innerTextField()
                          }
                      },
                  )

                  BasicTextField(
                      value = session.description.orEmpty(),
                      onValueChange = onDescriptionChange,
                      modifier = Modifier
                          .fillMaxWidth()
                          .padding(top = 4.dp, bottom = 8.dp),
                      singleLine = false,
                      maxLines = 2,
                      textStyle = MaterialTheme.typography.bodyMedium.copy(
                          color = Color.White.copy(alpha = 0.86f),
                          fontWeight = FontWeight.Medium,
                      ),
                      cursorBrush = SolidColor(Color.White),
                      decorationBox = { innerTextField ->
                          Box(Modifier.fillMaxWidth()) {
                              if (session.description.isNullOrBlank()) Text("Añadir descripción", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodyMedium)
                              innerTextField()
                          }
                      },
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
                     SessionHeroActionChip("Transferir", Icons.Default.SwapHoriz, onOpenTransfer)
                     SessionHeroActionChip("Historial", Icons.Default.History, onOpenHistory)
                     SessionHeroActionChip("Reglas y tiempo", Icons.Default.Settings, onOpenRules)
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

                // Dialog para nombrar la variante al crearla
                if (showCreateVariantDialog && nextVariant != null) {
                    AlertDialog(
                        onDismissRequest = { showCreateVariantDialog = false },
                        title = { Text("Nueva variante") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Crea una variante derivada de la sesión original. " +
                                    "Tendrá sus propios ejercicios, series y descansos independientes.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedTextField(
                                    value = newVariantName,
                                    onValueChange = { newVariantName = it },
                                    label = { Text("Nombre de la variante") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (newVariantName.isNotBlank()) {
                                        onCreateVariant(nextVariant, newVariantName.trim())
                                        showCreateVariantDialog = false
                                    }
                                },
                                enabled = newVariantName.isNotBlank(),
                            ) { Text("Crear variante") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCreateVariantDialog = false }) { Text("Cancelar") }
                        },
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
private fun HeroGlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    showUnsavedDot: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DarkEditorChip,
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = DarkEditorChip,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
private fun SessionHeroActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.34f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
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
    onSelectDay: (Int) -> Unit,
    roadmapOptions: List<SessionRoadmapOption>,
    onSelectRoadmapOption: (SessionRoadmapOption) -> Unit,
    competitionKeyDaysInWeek: Set<Int>,
    onCreateSessionForDay: (Int) -> Unit,
    onCreateCompetitionSessionForDay: (Int) -> Unit,
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
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .hazeEffect(state = hazeState, style = hazeStyle)
    } else {
        Modifier.fillMaxWidth()
    }
    Surface(
        modifier = navModifier,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        color = Color.Transparent,
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
            Box(modifier = Modifier.fillMaxWidth()) {
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
                            val isCompetitionKeyDay = day in competitionKeyDaysInWeek
                            val sessionCount = daySessions.size
                            val isMultiSession = sessionCount > 1
                            val selectedDayChip = selectedDay == day
                            val selectedDayColor = Color(0xFF2563EB)
                            val isDimmed = !hasSession
                            val alphaFactor = if (isDimmed) 0.35f else 1f

                            val backgroundColor = when {
                                selectedDayChip && hasSession -> selectedDayColor
                                selectedDayChip -> selectedDayColor.copy(alpha = 0.35f)
                                isDimmed -> DarkEditorChip.copy(alpha = 0.7f)
                                else -> DarkEditorChip
                            }
                            val borderColor = when {
                                selectedDayChip && hasSession -> Color.Transparent
                                isCompetitionKeyDay -> Color(0xFFF59E0B).copy(alpha = if (selectedDayChip) 1f else 0.72f)
                                selectedDayChip -> selectedDayColor
                                isDimmed -> Color.Transparent
                                else -> Color.Transparent
                            }
                            val borderWidth = if (selectedDayChip) 1.8.dp else 1.dp
                            val textColor = when {
                                selectedDayChip && hasSession -> Color.White
                                selectedDayChip -> selectedDayColor
                                isDimmed -> Color.White.copy(alpha = 0.38f)
                                else -> Color.White.copy(alpha = 0.86f)
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
                                if (isCompetitionKeyDay) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (-3).dp, y = (-3).dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF59E0B)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "C",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black,
                                        )
                                    }
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
        val isCompetitionDay = pendingCreateDay in competitionKeyDaysInWeek
        AlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("¿Crear sesión para ${dayLabel(pendingCreateDay)}?") },
            text = {
                Text(
                    if (isCompetitionDay) {
                        "Este día coincide con una fecha clave de competición. Puedes crear una sesión de competición directamente."
                    } else {
                        "Este día no tiene una sesión asignada. ¿Deseas crear una nueva sesión aquí?"
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showCreateSessionDialog = false
                    if (isCompetitionDay) onCreateCompetitionSessionForDay(pendingCreateDay)
                    else onCreateSessionForDay(pendingCreateDay)
                }) {
                    Text(if (isCompetitionDay) "Crear sesión de competición" else "Crear sesión")
                }
            },
            dismissButton = {
                if (isCompetitionDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showCreateSessionDialog = false
                            onCreateSessionForDay(pendingCreateDay)
                        }) {
                            Text("Crear normal")
                        }
                        OutlinedButton(onClick = { showCreateSessionDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                } else {
                    OutlinedButton(onClick = { showCreateSessionDialog = false }) {
                        Text("Cancelar")
                    }
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
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
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
                                    detectDragGestures(
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
                            modifier = Modifier.onGloballyPositioned { onContentBoundsChange(it.boundsInRoot()) },
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

internal fun buildSessionExerciseEditorBlocks(
    session: Session,
    containerExercises: List<Exercise>,
): List<SessionExerciseEditorBlock> {
    return containerExercises.map { exercise -> SessionExerciseEditorBlock.Single(exercise) }
}

@Composable
private fun SupersetGroupEditorCard(
    group: SupersetGroup,
    exercises: List<Exercise>,
    accentHex: String?,
    partId: String?,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    dragOffset: Offset = Offset.Zero,
    onBoundsChange: (Rect) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onOpenSupersetCreator: (String?, List<String>) -> Unit,
    onUpdateSupersetRest: (String, Int?, Int?, Int?) -> Unit,
    onUpdateRoundRest: (String, Int, Int?, Int?) -> Unit = { _, _, _, _ -> },
    onToggleOptional: (String) -> Unit = {},
    onUpdateExercise: (String, (Exercise) -> Exercise) -> Unit = { _, _ -> },
    onAddSet: (String) -> Unit = {},
    onUpdateSet: (String, String, (ExerciseSet) -> ExerciseSet) -> Unit = { _, _, _ -> },
    onRemoveSet: (String, String) -> Unit = { _, _ -> },
    onMoveSet: (String, String, Int) -> Unit = { _, _, _ -> },
    onRemoveRound: (Int) -> Unit = {},
    relationshipAnchorName: (Exercise) -> String? = { null },
    onOpenRelationshipPicker: (String) -> Unit = {},
    onClearRelationship: (String) -> Unit = {},
    onRemoveFromSuperset: (String, String) -> Unit,
    onDissolve: (String) -> Unit,
    onAddRound: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var configExerciseId by rememberSaveable(group.id) { mutableStateOf<String?>(null) }
    val accentColor = remember(accentHex) {
        runCatching { Color(AndroidColor.parseColor(accentHex ?: PART_COLORS.first())) }
            .getOrDefault(Color(0xFF00F0FF))
    }
    val rounds = (group.rounds ?: exercises.maxOfOrNull { it.sets.size } ?: 1).coerceAtLeast(1)
    val totalSets = exercises.sumOf { it.sets.size }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .graphicsLayer {
                translationX = 0f
                translationY = 0f
                alpha = if (isDragging) 0.22f else 1f
                shadowElevation = if (isDragging) 6.dp.toPx() else 0f
            }
            .zIndex(if (isDragging) 12f else 0f)
            .clip(RoundedCornerShape(14.dp))
            .background(lerp(DarkEditorSurface, accentColor, if (expanded) 0.12f else 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accentColor.copy(alpha = if (expanded) 0.85f else 0.30f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reordenar superserie",
                    tint = accentColor.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(18.dp)
                        .pointerInput(group.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragCancel = { onDragEnd() },
                                onDragEnd = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(Offset(dragAmount.x, dragAmount.y))
                                },
                            )
                        },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = { expanded = !expanded },
                    ),
            ) {
                Text(
                    text = "Superserie",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (group.isOptional) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                "Opcional",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        text = "${exercises.size} ejercicios · $rounds ronda${if (rounds == 1) "" else "s"} · $totalSets series",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Plegar" else "Desplegar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }

        AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 4.dp)) {
                items(exercises, key = { it.id }) { exercise ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (configExerciseId == exercise.id) accentColor.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.10f),
                        modifier = Modifier.clickable { configExerciseId = exercise.id },
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 170.dp),
                            )
                            IconButton(
                                onClick = { onRemoveFromSuperset(group.id, exercise.id) },
                                modifier = Modifier.size(24.dp),
                                enabled = exercises.size > 2,
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar de superserie", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(enabled = exercises.size < 4) {
                                onOpenSupersetCreator(partId, exercises.map { it.id })
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Añadir ejercicio", tint = accentColor, modifier = Modifier.size(16.dp))
                            Text("Añadir", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
                        }
                    }
                }
            }

            exercises.firstOrNull { it.id == configExerciseId }?.let { selected ->
                SupersetExerciseConfigOverlay(
                    exercise = selected,
                    accentColor = accentColor,
                    relationshipAnchorName = relationshipAnchorName(selected),
                    onUpdateExercise = { updater -> onUpdateExercise(selected.id, updater) },
                    onUpdateSet = { setId, updater -> onUpdateSet(selected.id, setId, updater) },
                    onOpenRelationshipPicker = { onOpenRelationshipPicker(selected.id) },
                    onClearRelationship = { onClearRelationship(selected.id) },
                    onDismiss = { configExerciseId = null },
                )
            }

            SupersetRoundsCarousel(
                group = group,
                exercises = exercises,
                rounds = rounds,
                accentColor = accentColor,
                onUpdateRoundRest = { roundIndex, restBetween, restAfter -> onUpdateRoundRest(group.id, roundIndex, restBetween, restAfter) },
                onUpdateSet = onUpdateSet,
                onRemoveSet = onRemoveSet,
                onMoveSet = onMoveSet,
                onAddRound = onAddRound,
                onRemoveRound = onRemoveRound,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DarkChoiceChip("OPCIONAL", group.isOptional, accentColor = accentColor) { onToggleOptional(group.id) }
                }
                TextButton(onClick = { onDissolve(group.id) }) {
                    Text("Disolver", fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }

}

@Composable
private fun SupersetExerciseConfigOverlay(
    exercise: Exercise,
    accentColor: Color,
    relationshipAnchorName: String?,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onOpenRelationshipPicker: () -> Unit,
    onClearRelationship: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSmartLoadSheet by remember { mutableStateOf(false) }
    var showGoalSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkEditorSurfaceSoft,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar configuración", modifier = Modifier.size(16.dp))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactModeSelector(
                    currentMode = exercise.trainingMode,
                    accentColor = accentColor,
                ) { mode -> onUpdateExercise { current -> current.copy(trainingMode = mode) } }
                CompactGoalTrackingButton(
                    isActive = exercise.isStarTarget,
                    accentColor = accentColor,
                    onToggle = { onUpdateExercise { current -> current.copy(isStarTarget = !current.isStarTarget) } },
                    onOpenSheet = { showGoalSheet = true },
                )
                DarkChoiceChip(
                    label = relationshipAnchorName?.let { "ANCLA: $it" } ?: "VINCULAR",
                    selected = exercise.relativeToCanonicalExerciseId != null,
                    accentColor = accentColor,
                    modifier = Modifier.widthIn(max = 170.dp),
                ) {
                    if (exercise.relativeToCanonicalExerciseId == null) onOpenRelationshipPicker() else onClearRelationship()
                }
                DarkChoiceChip(
                    label = "CARGA INTELIGENTE",
                    selected = false,
                    accentColor = accentColor,
                    modifier = Modifier.widthIn(max = 180.dp),
                ) {
                    if (exercise.trainingMode != TrainingMode.SOLO_RPE) showSmartLoadSheet = true
                }
                UnilateralModeSelector(
                    mode = exercise.unilateralMode,
                    accentColor = accentColor,
                    onToggleUnilateral = {
                        onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                    },
                )
                if (exercise.isEffectivelyUnilateral()) {
                    SideOrderChip(
                        sideOrder = exercise.unilateralSideOrder,
                        accentColor = accentColor,
                    ) {
                        onUpdateExercise { current ->
                            current.copy(
                                unilateralSideOrder = if (current.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                    UnilateralSideOrder.RIGHT_LEFT
                                } else {
                                    UnilateralSideOrder.LEFT_RIGHT
                                },
                            )
                        }
                    }
                }
            }
            relationshipAnchorName?.let {
                Text("Vinculado a $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showSmartLoadSheet) {
        SupersetSmartLoadDialog(exercise, onUpdateExercise, onDismiss = { showSmartLoadSheet = false })
    }

    if (showGoalSheet) {
        SupersetGoalDialog(exercise, onUpdateExercise, onDismiss = { showGoalSheet = false })
    }

}

@Composable
private fun SupersetSmartLoadDialog(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var rmInputMode by remember(exercise.id, exercise.prFor1RM) { mutableStateOf(if (exercise.prFor1RM != null) "pr" else "direct") }
    var directRmInput by rememberSaveable(exercise.id, exercise.reference1RM) { mutableStateOf(formatEditableNumber(exercise.reference1RM)) }
    var prWeightInput by rememberSaveable(exercise.id, exercise.prFor1RM) { mutableStateOf(formatEditableNumber(exercise.prFor1RM?.weight)) }
    var prRepsInput by rememberSaveable(exercise.id, exercise.prFor1RM) { mutableStateOf(exercise.prFor1RM?.reps?.takeIf { it > 0 }?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carga inteligente", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleToken("RM directo", rmInputMode == "direct") { rmInputMode = "direct" }
                    ToggleToken("Desde PR", rmInputMode == "pr") { rmInputMode = "pr" }
                }
                if (rmInputMode == "direct") {
                    EditorMiniField("RM referencial", directRmInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth()) { input ->
                        directRmInput = input
                        onUpdateExercise { it.copy(reference1RM = input.safeDoubleOrNull()?.takeIf { value -> value > 0 }) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditorMiniField("PR kg", prWeightInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f)) { input ->
                            prWeightInput = input
                            val weight = input.safeDoubleOrNull()
                            val reps = prRepsInput.safeIntOrNull()
                            onUpdateExercise { current ->
                                if (weight != null && weight > 0 && reps != null && reps > 0) current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                else current.copy(prFor1RM = null)
                            }
                        }
                        EditorMiniField("PR reps", prRepsInput, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)) { input ->
                            prRepsInput = input
                            val weight = prWeightInput.safeDoubleOrNull()
                            val reps = input.safeIntOrNull()
                            onUpdateExercise { current ->
                                if (weight != null && weight > 0 && reps != null && reps > 0) current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                else current.copy(prFor1RM = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun SupersetGoalDialog(
    exercise: Exercise,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var goalRmInput by rememberSaveable(exercise.id, exercise.goal1RM) { mutableStateOf(formatEditableNumber(exercise.goal1RM)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta / PR", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Marcar como objetivo", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = exercise.isStarTarget,
                        onCheckedChange = { checked -> onUpdateExercise { it.copy(isStarTarget = checked) } },
                    )
                }
                EditorMiniField("Meta 1RM kg", goalRmInput, keyboardType = KeyboardType.Decimal, modifier = Modifier.fillMaxWidth()) { input ->
                    goalRmInput = input
                    onUpdateExercise { it.copy(goal1RM = input.safeDoubleOrNull()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun SupersetRoundsCarousel(
    group: SupersetGroup,
    exercises: List<Exercise>,
    rounds: Int,
    accentColor: Color,
    onUpdateRoundRest: (Int, Int?, Int?) -> Unit,
    onUpdateSet: (String, String, (ExerciseSet) -> ExerciseSet) -> Unit,
    onRemoveSet: (String, String) -> Unit,
    onMoveSet: (String, String, Int) -> Unit,
    onAddRound: () -> Unit,
    onRemoveRound: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rondas", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = onAddRound,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ronda", fontWeight = FontWeight.Bold)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items((0 until rounds).toList(), key = { it }) { roundIndex ->
                var showRoundRestPicker by rememberSaveable(group.id, roundIndex) { mutableStateOf(false) }
                val roundRestBetween = group.roundRestBetweenExercises[roundIndex] ?: group.restBetweenExercises
                val roundRestAfter = group.roundRestAfterSuperset[roundIndex] ?: group.restAfterSuperset
                Surface(
                    modifier = Modifier.width(320.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Ronda ${roundIndex + 1}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, color = accentColor)
                            IconButton(onClick = { onRemoveRound(roundIndex) }, enabled = rounds > 1, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar ronda", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        SupersetRestPickerButton(
                            restBetweenSeconds = roundRestBetween,
                            restAfterSeconds = roundRestAfter,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showRoundRestPicker = true },
                        )
                        exercises.forEach { exercise ->
                            val set = exercise.sets.getOrNull(roundIndex)
                            if (set != null) {
                                Text(exercise.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val orderedSides = when (exercise.unilateralSideOrder) {
                                    UnilateralSideOrder.LEFT_RIGHT -> listOf("L", "R")
                                    UnilateralSideOrder.RIGHT_LEFT -> listOf("R", "L")
                                }
                                if (exercise.isEffectivelyUnilateral()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        orderedSides.forEach { side ->
                                            val isLeft = side == "L"
                                            val showCard = if (isLeft) set.leftTarget != null else set.rightTarget != null
                                            val isFirstVisible = orderedSides.takeWhile { it != side }.none { prior ->
                                                if (prior == "L") set.leftTarget != null else set.rightTarget != null
                                            }
                                            if (showCard) {
                                                InlineSetRow(
                                                    set = set,
                                                    index = roundIndex,
                                                    reference1RM = resolveReferenceCapacity(exercise),
                                                    predictedWeight = calculateSuggestedLoad(exercise, set),
                                                    estimatedMetric = calculateEstimatedMetric(exercise, set),
                                                    trainingMode = exercise.trainingMode,
                                                    customUnit = exercise.customUnit,
                                                    accentColor = if (isLeft) Color(0xFF2196F3) else Color(0xFFFF5252),
                                                    canMoveUp = isFirstVisible && roundIndex > 0,
                                                    canMoveDown = isFirstVisible && roundIndex < exercise.sets.lastIndex,
                                                    isUnilateral = true,
                                                    fixedUnilateralSide = side,
                                                    showSetActions = isFirstVisible,
                                                    unilateralIntensityMode = exercise.unilateralIntensityMode,
                                                    onUpdate = { updater -> onUpdateSet(exercise.id, set.id, updater) },
                                                    onRemove = { onRemoveSet(exercise.id, set.id) },
                                                    onMoveUp = { onMoveSet(exercise.id, set.id, -1) },
                                                    onMoveDown = { onMoveSet(exercise.id, set.id, 1) },
                                                )
                                            } else {
                                                UnilateralAddGhostCard(
                                                    side = side,
                                                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(184.dp),
                                                    onClick = {
                                                        onUpdateSet(exercise.id, set.id) { current ->
                                                            val default = UnilateralTarget(
                                                                weight = current.weight,
                                                                targetReps = current.targetReps,
                                                                targetDuration = current.targetDuration,
                                                                targetValue = current.plannedTargetV2,
                                                                targetRPE = current.targetRPE,
                                                                targetRIR = current.targetRIR,
                                                                intensityMode = current.intensityMode,
                                                            )
                                                            if (side == "L") {
                                                                current.copy(leftTarget = current.leftTarget ?: default)
                                                            } else {
                                                                current.copy(rightTarget = current.rightTarget ?: default)
                                                            }
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    InlineSetRow(
                                        set = set,
                                        index = roundIndex,
                                        reference1RM = resolveReferenceCapacity(exercise),
                                        predictedWeight = calculateSuggestedLoad(exercise, set),
                                        estimatedMetric = calculateEstimatedMetric(exercise, set),
                                        trainingMode = exercise.trainingMode,
                                        customUnit = exercise.customUnit,
                                        accentColor = accentColor,
                                        canMoveUp = roundIndex > 0,
                                        canMoveDown = roundIndex < exercise.sets.lastIndex,
                                        isUnilateral = false,
                                        unilateralIntensityMode = exercise.unilateralIntensityMode,
                                        onUpdate = { updater -> onUpdateSet(exercise.id, set.id, updater) },
                                        onRemove = { onRemoveSet(exercise.id, set.id) },
                                        onMoveUp = { onMoveSet(exercise.id, set.id, -1) },
                                        onMoveDown = { onMoveSet(exercise.id, set.id, 1) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (showRoundRestPicker) {
                    SupersetRestPickerDialog(
                        initialRestBetweenSeconds = roundRestBetween,
                        initialRestAfterSeconds = roundRestAfter,
                        accentColor = accentColor,
                        onDismiss = { showRoundRestPicker = false },
                        onConfirm = { restBetween, restAfter ->
                            onUpdateRoundRest(roundIndex, restBetween, restAfter)
                            showRoundRestPicker = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SupersetRestPickerButton(
    restBetweenSeconds: Int,
    restAfterSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = accentColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Timer, contentDescription = "Descansos de superserie", tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Entre ${formatRestSummary(restBetweenSeconds)} · Ronda ${formatRestSummary(restAfterSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SupersetRestPickerDialog(
    initialRestBetweenSeconds: Int,
    initialRestAfterSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var betweenMinutes by rememberSaveable(initialRestBetweenSeconds) { mutableStateOf((initialRestBetweenSeconds / 60).coerceIn(0, 59)) }
    var betweenSeconds by rememberSaveable(initialRestBetweenSeconds) { mutableStateOf((initialRestBetweenSeconds % 60).coerceIn(0, 59)) }
    var afterMinutes by rememberSaveable(initialRestAfterSeconds) { mutableStateOf((initialRestAfterSeconds / 60).coerceIn(0, 59)) }
    var afterSeconds by rememberSaveable(initialRestAfterSeconds) { mutableStateOf((initialRestAfterSeconds % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descansos de superserie", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SupersetRestWheelRow(
                    label = "Entre ejercicios",
                    minutes = betweenMinutes,
                    seconds = betweenSeconds,
                    accentColor = accentColor,
                    onMinutesChange = { betweenMinutes = it },
                    onSecondsChange = { betweenSeconds = it },
                )
                SupersetRestWheelRow(
                    label = "Fin de ronda",
                    minutes = afterMinutes,
                    seconds = afterSeconds,
                    accentColor = accentColor,
                    onMinutesChange = { afterMinutes = it },
                    onSecondsChange = { afterSeconds = it },
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        betweenMinutes * 60 + betweenSeconds,
                        afterMinutes * 60 + afterSeconds,
                    )
                },
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun SupersetRestWheelRow(
    label: String,
    minutes: Int,
    seconds: Int,
    accentColor: Color,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accentColor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeWheelPicker("Min", minutes, 0..59, accentColor, Modifier.weight(1f), onMinutesChange)
            NativeWheelPicker("Seg", seconds, 0..59, accentColor, Modifier.weight(1f), onSecondsChange)
        }
        Text(
            "Seleccionado: ${minutes}:${seconds.toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    isCompetitionMovement: Boolean,
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
    onAddSet: (String?) -> Unit,
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
    suppressIndividualRest: Boolean = false,
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

    val isSupersetExercise = exercise.supersetGroupRefOrLegacyId() != null
    val supersetShape = RoundedCornerShape(14.dp)
    val containerHighlight = when {
        isDragging -> accentColor.copy(alpha = 0.10f)
        isDropTarget -> accentColor.copy(alpha = 0.08f)
        isPartDropTarget -> accentColor.copy(alpha = 0.06f)
        isSupersetExercise -> accentColor.copy(alpha = if (expanded) 0.12f else 0.08f)
        else -> Color.Transparent
    }
    val containerModifier = when {
        isSupersetExercise -> Modifier
            .clip(supersetShape)
            .background(containerHighlight)
            .border(2.dp, accentColor.copy(alpha = 0.6f), supersetShape)

        containerHighlight.alpha > 0f -> Modifier.background(containerHighlight)
        else -> Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBoundsChange(it.boundsInRoot()) }
            .graphicsLayer {
                translationX = 0f
                translationY = 0f
                alpha = if (isDragging) 0.22f else 1f
                shadowElevation = if (isDragging) 6.dp.toPx() else 0f
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
                        detectDragGestures(
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (exercise.supersetGroupRefOrLegacyId() != null) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = exercise.name.ifBlank { "Seleccionar ejercicio" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = buildString {
                        append("${exercise.sets.size} series · ")
                        if (!suppressIndividualRest) append("${formatRestSummary(exercise.restTime)} · ")
                        append(trainingModeLabel(exercise.trainingMode))
                        if (exercise.supersetGroupRefOrLegacyId() != null) append(" · Superserie")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCompetitionMovement) {
                    Text(
                        text = "Movimiento de competición",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    if (!suppressIndividualRest) {
                        item("rest") {
                            CompactRestBundleButton(
                                primaryLabel = if (exercise.isEffectivelyUnilateral() && !isSupersetExercise) "Series L/R" else "Descanso",
                                primarySeconds = restSelectionSeconds,
                                sideSeconds = if (exercise.isEffectivelyUnilateral() && !isSupersetExercise) exercise.restBetweenSidesSeconds ?: 0 else null,
                                accentColor = accentColor,
                                onConfirm = { primary, side ->
                                    restSelectionSeconds = primary
                                    onUpdateExercise { draft ->
                                        draft.copy(
                                            restTime = primary,
                                            restBetweenSidesSeconds = side?.takeIf { it > 0 },
                                        )
                                    }
                                }
                            )
                        }
                    }
                    
                    // Mode selector (compact, no label)
                    item("mode") {
                        CompactModeSelector(
                            currentMode = exercise.trainingMode,
                            accentColor = accentColor,
                        ) { mode ->
                            onUpdateExercise { current -> current.copy(trainingMode = mode) }
                        }
                    }
                    
                    // Goal tracking star button
                    item("goal") {
                        CompactGoalTrackingButton(
                            isActive = exercise.isStarTarget,
                            accentColor = accentColor,
                            onToggle = { onUpdateExercise { ex -> ex.copy(isStarTarget = !ex.isStarTarget) } },
                            onOpenSheet = { showGoalSheet = true },
                        )
                    }

                    // Track ROM toggle chip
                    item("track-rom") {
                        DarkChoiceChip(
                            label = "MEDIR ROM",
                            selected = exercise.trackRom,
                            accentColor = accentColor,
                            onClick = {
                                onUpdateExercise { current ->
                                    current.copy(trackRom = !current.trackRom)
                                }
                            },
                        )
                    }

                    item("relationship") {
                        DarkChoiceChip(
                            label = relationshipAnchorName?.let { "ANCLA: $it" } ?: "VINCULAR",
                            selected = exercise.relativeToCanonicalExerciseId != null,
                            accentColor = accentColor,
                            modifier = Modifier.widthIn(max = 180.dp),
                            onClick = {
                                if (exercise.relativeToCanonicalExerciseId == null) onOpenRelationshipPicker() else onClearRelationship()
                            },
                        )
                    }

                    item("unilateral") {
                        UnilateralModeSelector(
                            mode = exercise.unilateralMode,
                            accentColor = accentColor,
                            onToggleUnilateral = {
                                onUpdateExercise { current -> current.toggledBilateralUnilateral() }
                            },
                        )
                    }

                    if (exercise.isEffectivelyUnilateral()) {
                        item("side-order") {
                            SideOrderChip(
                                sideOrder = exercise.unilateralSideOrder,
                                accentColor = accentColor,
                                onToggle = {
                                    onUpdateExercise { current ->
                                        current.copy(
                                            unilateralSideOrder = if (current.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                                UnilateralSideOrder.RIGHT_LEFT
                                            } else {
                                                UnilateralSideOrder.LEFT_RIGHT
                                            },
                                        )
                                    }
                                },
                            )
                        }

                        item("intensity-mode") {
                            DarkChoiceChip(
                                label = if (exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED) "LADOS VINCULADOS" else "LADOS INDEPENDIENTES",
                                selected = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED,
                                accentColor = accentColor,
                            ) {
                                onUpdateExercise { current ->
                                    val newMode = if (current.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                                        UnilateralIntensityMode.INDEPENDENT
                                    } else {
                                        UnilateralIntensityMode.SHARED
                                    }
                                    current.copy(unilateralIntensityMode = newMode)
                                }
                            }
                        }
                    }
                }

                if (exercise.isEffectivelyUnilateral()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkEditorSurfaceSoft,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Unilateral",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SideOrderChip(
                                sideOrder = exercise.unilateralSideOrder,
                                accentColor = accentColor,
                                onToggle = {
                                    onUpdateExercise { current ->
                                        current.copy(
                                            unilateralSideOrder = if (current.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                                UnilateralSideOrder.RIGHT_LEFT
                                            } else {
                                                UnilateralSideOrder.LEFT_RIGHT
                                            },
                                        )
                                    }
                                },
                            )
                            DarkChoiceChip(
                                label = if (exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED) "LADOS VINCULADOS" else "LADOS INDEPENDIENTES",
                                selected = exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED,
                                accentColor = accentColor,
                                onClick = {
                                    onUpdateExercise { current ->
                                        current.copy(
                                            unilateralIntensityMode = if (current.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                                                UnilateralIntensityMode.INDEPENDENT
                                            } else {
                                                UnilateralIntensityMode.SHARED
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                if (isSupersetExercise && !suppressIndividualRest) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Link, null, Modifier.size(18.dp), tint = accentColor)
                                Text("Superserie activa", fontWeight = FontWeight.Black, color = accentColor, style = MaterialTheme.typography.labelMedium)
                            }
                            Text(
                                "Los ejercicios agrupados comparten descanso: ${exercise.supersetRestBetween ?: 60}s entre ejercicios, ${exercise.supersetRestAfter ?: 120}s post-ronda.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (exercise.restTime != null) {
                                Text(
                                    "El descanso individual (${exercise.restTime}s) es reemplazado por los de la superserie.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
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

                if (exercise.trainingMode != TrainingMode.SOLO_RPE) {
                    FilledTonalButton(
                        onClick = { showSmartLoadSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkEditorChip,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CARGA INTELIGENTE", fontWeight = FontWeight.Black)
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
                                                        current.copy(
                                                            prFor1RM = current.prFor1RM?.copy(weight = weight ?: current.prFor1RM.weight),
                                                            reference1RM = current.reference1RM,
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
                                                onUpdateExercise { current ->
                                                    if (weight != null && weight > 0 && reps != null && reps > 0) {
                                                        current.copy(prFor1RM = PrReference(weight, reps), reference1RM = calculateHybrid1RM(weight, reps))
                                                    } else {
                                                        current.copy(
                                                            prFor1RM = current.prFor1RM?.copy(reps = reps ?: current.prFor1RM.reps),
                                                            reference1RM = current.reference1RM,
                                                        )
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

                if (exercise.relativeToCanonicalExerciseId != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkEditorSurfaceSoft,
                    ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                                DarkChoiceChip(type.displayLabel().uppercase(), exercise.relationshipType == type, accentColor = accentColor) {
                                    onUpdateRelationshipType(type)
                                }
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
                    }
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkEditorChip,
                            unfocusedContainerColor = DarkEditorChip,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
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
    isUnilateral: Boolean = false,
    fixedUnilateralSide: String? = null,
    showSetActions: Boolean = true,
    unilateralIntensityMode: UnilateralIntensityMode = UnilateralIntensityMode.SHARED,
    onUpdate: ((ExerciseSet) -> ExerciseSet) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var showAmrapDialog by remember(set.id) { mutableStateOf(false) }
    var showIntensityMenu by remember(set.id) { mutableStateOf(false) }
    var showLoadModeMenu by remember(set.id) { mutableStateOf(false) }
    var showPlannedIntensity by rememberSaveable(set.id) {
        mutableStateOf(
            set.intensityMode != null ||
                set.targetRPE != null ||
                set.targetRIR != null ||
                set.isFailure ||
                set.leftTarget?.intensityMode != null ||
                set.rightTarget?.intensityMode != null,
        )
    }
    val isNarrowScreen = LocalConfiguration.current.screenWidthDp <= 380
    val isRmMode = trainingMode == TrainingMode.RM
    val isSoloRpeMode = trainingMode == TrainingMode.SOLO_RPE
    val isAmrapMode = set.isAmrap || trainingMode == TrainingMode.AMRAP
    var selectedUniSide by remember(set.id, fixedUnilateralSide) { mutableStateOf(fixedUnilateralSide ?: "L") }
    val activeUniSide = fixedUnilateralSide ?: selectedUniSide
    val setStateKeySuffix = if (isUnilateral) activeUniSide else "B"
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
    val activeSideTarget = if (isUnilateral) {
        if (activeUniSide == "L") set.leftTarget else set.rightTarget
    } else null
    fun uniOrSetDbl(getSet: (ExerciseSet) -> Double?, getTarget: (UnilateralTarget?) -> Double?): Double? =
        if (isUnilateral && activeSideTarget != null) getTarget(activeSideTarget) else getSet(set)
    fun uniOrSetInt(getSet: (ExerciseSet) -> Int?, getTarget: (UnilateralTarget?) -> Int?): Int? =
        if (isUnilateral && activeSideTarget != null) getTarget(activeSideTarget) else getSet(set)
    val metricValue = when (trainingMode) {
        TrainingMode.RM -> formatEstimatedMetric(estimatedMetric, trainingMode, customUnit)
        TrainingMode.TIME -> (
            if (isUnilateral && activeSideTarget != null) activeSideTarget.targetDuration else set.targetDuration
        )?.toString().orEmpty()
        TrainingMode.SOLO_RPE -> formatEditableNumber(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE }))
        TrainingMode.DISTANCE,
        TrainingMode.CUSTOM,
        -> formatEditableNumber(
            if (isUnilateral && activeSideTarget != null) activeSideTarget.targetValue else set.plannedTargetV2
        ).ifBlank { (uniOrSetInt({ it.targetReps }, { it?.targetReps })?.toString()).orEmpty() }
        else -> (uniOrSetInt({ it.targetReps }, { it?.targetReps })?.toString()).orEmpty()
    }
    val intensityValue = when (set.intensityMode ?: IntensityMode.RPE) {
        IntensityMode.RPE -> formatEditableNumber(uniOrSetDbl({ it.targetRPE }, { it?.targetRPE }))
        IntensityMode.RIR -> (uniOrSetInt({ it.targetRIR }, { it?.targetRIR })?.toString()).orEmpty()
        IntensityMode.FAILURE -> "Auto"
        IntensityMode.SOLO_RM -> formatEditableNumber(set.targetPercentageRM ?: sliderPercent)
        IntensityMode.AMRAP -> ""
        IntensityMode.LOAD -> formatEditableNumber(uniOrSetDbl({ it.weight }, { it?.weight }))
    }

    fun updateUniSet(updater: (UnilateralTarget) -> UnilateralTarget): ((ExerciseSet) -> ExerciseSet) {
        val side = activeUniSide
        return { current ->
            val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
            val updated = updater(currentSide)
            if (unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                val mirrored = updated.copy()
                if (side == "L") current.copy(leftTarget = updated, rightTarget = mirrored)
                else current.copy(rightTarget = updated, leftTarget = mirrored)
            } else {
                if (side == "L") current.copy(leftTarget = updated)
                else current.copy(rightTarget = updated)
            }
        }
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
    val loadModeLabel = when (set.loadModeV2 ?: LoadModeV2.LOAD) {
        LoadModeV2.LOAD -> "Carga externa"
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre"
        LoadModeV2.ASSISTED -> "Asistido"
    }
    val setSurface = lerp(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), accentColor, 0.14f)
    val estimatedSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val estimatedText = MaterialTheme.colorScheme.onSurface
    val estimatedSubtle = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        shape = RoundedCornerShape(if (isNarrowScreen) 14.dp else 16.dp),
        color = setSurface,
        border = null,
    ) {
        Column(
            Modifier.padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 6.dp else 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 4.dp else 6.dp),
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                    Text(
                        text = "S${index + 1}${if (isUnilateral) "-${activeUniSide}" else ""}",
                        modifier = Modifier.padding(horizontal = if (isNarrowScreen) 5.dp else 6.dp, vertical = 1.dp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (isUnilateral && fixedUnilateralSide == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("L" to Color(0xFF2196F3), "R" to Color(0xFFFF5252)).forEach { (label, sideColor) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedUniSide == label) sideColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedUniSide == label) sideColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                ),
                                modifier = Modifier.clickable { selectedUniSide = label },
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    fontWeight = if (selectedUniSide == label) FontWeight.Black else FontWeight.Medium,
                                    color = if (selectedUniSide == label) sideColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (showSetActions) Box {
                    FilledTonalButton(
                        onClick = { showLoadModeMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkEditorChip,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            loadModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(expanded = showLoadModeMenu, onDismissRequest = { showLoadModeMenu = false }) {
                        listOf(
                            LoadModeV2.LOAD to "Carga externa",
                            LoadModeV2.BODYWEIGHT to "Peso corporal",
                            LoadModeV2.LASTRE to "Lastre",
                            LoadModeV2.ASSISTED to "Asistido",
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    showLoadModeMenu = false
                                    onUpdate { current -> current.copy(loadModeV2 = mode) }
                                },
                            )
                        }
                    }
                }
                if (showSetActions) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(if (isNarrowScreen) 24.dp else 28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(if (isNarrowScreen) 14.dp else 15.dp),
                        )
                    }
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
                        stateKey = "metric-${set.id}-${setStateKeySuffix}",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    ) { input ->
                        onUpdate(if (isUnilateral) updateUniSet { it.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE) } else { current ->
                            current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE, targetRIR = null, targetPercentageRM = null, targetReps = null, targetDuration = null, isFailure = false, isAmrap = false)
                        })
                    }
                } else {
                    EditorMiniField(
                        label = metricLabel,
                        value = metricValue,
                        stateKey = "metric-${set.id}-${setStateKeySuffix}",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(if (isAmrapMode) if (isNarrowScreen) 1.2f else 1.35f else 1f),
                    ) { input ->
                        onUpdate(if (isUnilateral) updateUniSet {
                            when (trainingMode) {
                                TrainingMode.TIME -> it.copy(targetDuration = input.safeIntOrNull())
                                TrainingMode.DISTANCE,
                                TrainingMode.CUSTOM,
                                -> it.copy(targetValue = input.safeDoubleOrNull())
                                else -> it.copy(targetReps = input.safeIntOrNull())
                            }
                        } else { current ->
                            when (trainingMode) {
                                TrainingMode.TIME -> current.copy(targetDuration = input.safeIntOrNull())
                                TrainingMode.DISTANCE,
                                TrainingMode.CUSTOM,
                                -> current.copy(plannedTargetV2 = input.safeDoubleOrNull())
                                else -> current.copy(targetReps = input.safeIntOrNull())
                            }
                        })
                    }
                }
                if (!isAmrapMode && !isRmMode && !isSoloRpeMode) {
                    if (!showPlannedIntensity) {
                        FilledTonalButton(
                            onClick = {
                                showPlannedIntensity = true
                                onUpdate { current ->
                                    current.copy(
                                        intensityMode = IntensityMode.RPE,
                                        targetRPE = current.targetRPE ?: 8.0,
                                        targetRIR = null,
                                        isFailure = false,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DarkEditorChip,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                if (isNarrowScreen) "Intensidad" else "Programar intensidad",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 3.dp else 4.dp)) {
                        Text(
                            if (isNarrowScreen) "Intens." else "Intensidad",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Box {
                            FilledTonalButton(
                                onClick = { showIntensityMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = DarkEditorChip,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(intensityLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                                            val updater: (ExerciseSet) -> ExerciseSet = {
                                                when (mode) {
                                                    IntensityMode.RPE -> it.copy(intensityMode = IntensityMode.RPE, isFailure = false, targetRPE = it.targetRPE ?: 8.0, targetRIR = null)
                                                    IntensityMode.RIR -> it.copy(intensityMode = IntensityMode.RIR, isFailure = false, targetRIR = it.targetRIR ?: 2, targetRPE = null)
                                                    IntensityMode.FAILURE -> it.copy(intensityMode = IntensityMode.FAILURE, isFailure = true, targetRIR = null, targetRPE = null)
                                                    else -> it
                                                }
                                            }
                                            if (isUnilateral) {
                                                val side = activeUniSide
                                                onUpdate { current ->
                                                    val currentSide = (if (side == "L") current.leftTarget else current.rightTarget) ?: UnilateralTarget()
                                                    val temp = ExerciseSet(id = "", targetRPE = currentSide.targetRPE, targetRIR = currentSide.targetRIR, intensityMode = currentSide.intensityMode ?: current.intensityMode)
                                                    val updated = updater(temp)
                                                    val newSide = currentSide.copy(targetRPE = updated.targetRPE, targetRIR = updated.targetRIR, intensityMode = updated.intensityMode)
                                                    if (side == "L") current.copy(leftTarget = newSide, isFailure = updated.isFailure)
                                                    else current.copy(rightTarget = newSide, isFailure = updated.isFailure)
                                                }
                                            } else {
                                                onUpdate(updater)
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
                            stateKey = "intensity-${set.id}-${setStateKeySuffix}",
                            keyboardType = if ((set.intensityMode ?: IntensityMode.RPE) == IntensityMode.RPE) KeyboardType.Decimal else KeyboardType.Number,
                            modifier = Modifier.weight(if (isNarrowScreen) 0.82f else 0.9f),
                        ) { input ->
                            onUpdate(if (isUnilateral) updateUniSet {
                                when (set.intensityMode ?: IntensityMode.RPE) {
                                    IntensityMode.RPE -> it.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                                    IntensityMode.RIR -> it.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                                    IntensityMode.LOAD -> it.copy(weight = input.safeDoubleOrNull())
                                    else -> it
                                }
                            } else { current ->
                                when (current.intensityMode ?: IntensityMode.RPE) {
                                    IntensityMode.RPE -> current.copy(targetRPE = input.safeDoubleOrNull(), intensityMode = IntensityMode.RPE)
                                    IntensityMode.RIR -> current.copy(targetRIR = input.safeIntOrNull(), intensityMode = IntensityMode.RIR)
                                    IntensityMode.LOAD -> current.copy(weight = input.safeDoubleOrNull(), intensityMode = IntensityMode.LOAD)
                                    else -> current
                                }
                            })
                        }
                    }
                    }
                } else if (isAmrapMode) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = if (set.isCalibrator) "AMRAP calibrador" else "AMRAP",
                            modifier = Modifier.padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 8.dp else 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                }
            }

            if (!isSoloRpeMode) {
                Surface(
                    shape = RoundedCornerShape(if (isNarrowScreen) 12.dp else 14.dp),
                    color = estimatedSurface,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = if (isNarrowScreen) 8.dp else 10.dp, vertical = if (isNarrowScreen) 5.dp else 6.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrowScreen) 4.dp else 5.dp),
                    ) {
                        Text(
                            text = buildString {
                                append(displayedWeight?.let { "${"%.1f".format(it)} kg" } ?: "Usa carga inteligente para estimar la carga inicial")
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

            // ─── Feature 4: Selector de técnica programada (Dropset / Rest-Pause) ─
            val currentTechniques = set.plannedIntensityTechniques
            val hasDropSet = currentTechniques.any { it.type == TechniqueType.DROP_SET }
            val hasRestPause = currentTechniques.any { it.type == TechniqueType.REST_PAUSE }

            var showDropSetConfig by rememberSaveable(set.id) { mutableStateOf(hasDropSet) }
            var showRestPauseConfig by rememberSaveable(set.id) { mutableStateOf(hasRestPause) }

            // Chips de técnica
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Chip Drop-set
                FilterChip(
                    selected = hasDropSet,
                    onClick = {
                        if (hasDropSet) {
                            // Quitar drop-set
                            onUpdate { current ->
                                current.copy(
                                    plannedIntensityTechniques = current.plannedIntensityTechniques.filter { it.type != TechniqueType.DROP_SET },
                                    isDropSet = false,
                                )
                            }
                            showDropSetConfig = false
                        } else {
                            // Añadir drop-set con defaults: 3 drops (-15%, -25%, -35%)
                            onUpdate { current ->
                                val newTechnique = PlannedTechnique(
                                    id = java.util.UUID.randomUUID().toString(),
                                    type = TechniqueType.DROP_SET,
                                    params = mapOf("weightPcts" to "-15,-25,-35", "count" to "3"),
                                )
                                current.copy(
                                    plannedIntensityTechniques = current.plannedIntensityTechniques + newTechnique,
                                    isDropSet = true,
                                )
                            }
                            showDropSetConfig = true
                        }
                    },
                    label = { Text("Drop-set", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (hasDropSet) ({ Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }) else null,
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                // Chip Rest-pause
                FilterChip(
                    selected = hasRestPause,
                    onClick = {
                        if (hasRestPause) {
                            onUpdate { current ->
                                current.copy(
                                    plannedIntensityTechniques = current.plannedIntensityTechniques.filter { it.type != TechniqueType.REST_PAUSE },
                                    isRestPause = false,
                                )
                            }
                            showRestPauseConfig = false
                        } else {
                            onUpdate { current ->
                                val newTechnique = PlannedTechnique(
                                    id = java.util.UUID.randomUUID().toString(),
                                    type = TechniqueType.REST_PAUSE,
                                    params = mapOf("count" to "3", "pauseSeconds" to "10", "reps" to "3"),
                                )
                                current.copy(
                                    plannedIntensityTechniques = current.plannedIntensityTechniques + newTechnique,
                                    isRestPause = true,
                                )
                            }
                            showRestPauseConfig = true
                        }
                    },
                    label = { Text("Rest-pause", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (hasRestPause) ({ Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }) else null,
                    shape = RoundedCornerShape(999.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                    ),
                )
            }

            // Config expandida de Drop-set
            if (showDropSetConfig && hasDropSet) {
                val dsTechnique = currentTechniques.firstOrNull { it.type == TechniqueType.DROP_SET }
                if (dsTechnique != null) {
                    val dropPcts = (dsTechnique.params["weightPcts"] ?: "-15,-25,-35")
                        .split(",").map { it.trim() }
                    AnimatedVisibility(visible = true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Drop-set programado",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Mini-drops: ${dropPcts.size}  ·  Reducciones: ${dropPcts.joinToString(", ")}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // Botón para configurar número de drops
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Drops:", style = MaterialTheme.typography.labelSmall)
                                    listOf(2, 3, 4).forEach { n ->
                                        val isSelected = dropPcts.size == n
                                        val defaultPcts = when (n) {
                                            2 -> "-15,-25"
                                            3 -> "-15,-25,-35"
                                            4 -> "-10,-20,-30,-40"
                                            else -> "-15,-25,-35"
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onUpdate { current ->
                                                    val updated = dsTechnique.copy(
                                                        params = mapOf("weightPcts" to defaultPcts, "count" to n.toString()),
                                                    )
                                                    current.copy(
                                                        plannedIntensityTechniques = current.plannedIntensityTechniques.map {
                                                            if (it.id == dsTechnique.id) updated else it
                                                        },
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        ) {
                                            Text("$n", style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Config expandida de Rest-pause
            if (showRestPauseConfig && hasRestPause) {
                val rpTechnique = currentTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }
                if (rpTechnique != null) {
                    val rpCount = rpTechnique.params["count"]?.toIntOrNull() ?: 3
                    val rpPause = rpTechnique.params["pauseSeconds"]?.toIntOrNull() ?: 10
                    val rpReps  = rpTechnique.params["reps"]?.toIntOrNull() ?: 3
                    AnimatedVisibility(visible = true, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Rest-pause programado",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Mini-sets
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Mini-series", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                if (rpCount > 2) onUpdate { current ->
                                                    val updated = rpTechnique.copy(params = rpTechnique.params + ("count" to (rpCount - 1).toString()))
                                                    current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                                }
                                            }, modifier = Modifier.size(24.dp)) { Text("-", fontWeight = FontWeight.Black) }
                                            Text("$rpCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = {
                                                if (rpCount < 6) onUpdate { current ->
                                                    val updated = rpTechnique.copy(params = rpTechnique.params + ("count" to (rpCount + 1).toString()))
                                                    current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                                }
                                            }, modifier = Modifier.size(24.dp)) { Text("+", fontWeight = FontWeight.Black) }
                                        }
                                    }
                                    // Pausa
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Pausa (s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                if (rpPause > 5) onUpdate { current ->
                                                    val updated = rpTechnique.copy(params = rpTechnique.params + ("pauseSeconds" to (rpPause - 5).toString()))
                                                    current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                                }
                                            }, modifier = Modifier.size(24.dp)) { Text("-", fontWeight = FontWeight.Black) }
                                            Text("${rpPause}s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = {
                                                if (rpPause < 30) onUpdate { current ->
                                                    val updated = rpTechnique.copy(params = rpTechnique.params + ("pauseSeconds" to (rpPause + 5).toString()))
                                                    current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                                }
                                            }, modifier = Modifier.size(24.dp)) { Text("+", fontWeight = FontWeight.Black) }
                                        }
                                    }
                                    // Reps por mini-serie
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Reps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                if (rpReps > 1) onUpdate { current ->
                                                    val updated = rpTechnique.copy(params = rpTechnique.params + ("reps" to (rpReps - 1).toString()))
                                                    current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                                }
                                            }, modifier = Modifier.size(24.dp)) { Text("-", fontWeight = FontWeight.Black) }
                                            Text("$rpReps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = {
                                                if (rpReps < 10) onUpdate { current ->
                                                    val updated = rpTechnique.copy(params = rpTechnique.params + ("reps" to (rpReps + 1).toString()))
                                                    current.copy(plannedIntensityTechniques = current.plannedIntensityTechniques.map { if (it.id == rpTechnique.id) updated else it })
                                                }
                                            }, modifier = Modifier.size(24.dp)) { Text("+", fontWeight = FontWeight.Black) }
                                        }
                                    }
                                }
                                Text(
                                    "Resumen: $rpCount × $rpReps reps · Pausa ${rpPause}s entre mini-series",
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
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF3A3A42),
            unfocusedContainerColor = Color(0xFF2E2E35),
            disabledContainerColor = Color(0xFF27272D),
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
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
                focusedContainerColor = Color(0xFF3A3A42),
                unfocusedContainerColor = Color(0xFF2E2E35),
                disabledContainerColor = Color(0xFF27272D),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedLabelColor = accentColor,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun CatalogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun CompactCatalogFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(28.dp),
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        border = null,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            labelColor = Color.White.copy(alpha = 0.86f),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            iconColor = Color.White.copy(alpha = 0.7f),
        ),
    )
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
    onToggleExerciseSelection: (String) -> Unit,
    onClearExerciseSelection: () -> Unit,
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
    onRuleDefaultsChange: (String?, Int?, Int?, Double?, Int?, Int?, Int?, Int?, Boolean?, DefaultIntensityType?) -> Unit,
    onRuleLimitsChange: (Double?, Int?) -> Unit,
    onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
    onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
    setTargetDuration: (Int?) -> Unit,
    setPartTargetDuration: (String, Int?) -> Unit,
    setExerciseTargetDuration: (String, Int?) -> Unit,
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
         var showInlineCreator by remember { mutableStateOf(false) }
         var highlightedCreatedExerciseId by remember { mutableStateOf<String?>(null) }
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
                 
                  if (showInlineCreator) {
                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .padding(horizontal = 14.dp, vertical = 8.dp),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          Column(Modifier.weight(1f)) {
                              Text("Crear ejercicio", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                              Text("Se guardará en Creados por ti", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.68f))
                          }
                          TextButton(onClick = { showInlineCreator = false }) {
                              Text("Catálogo")
                          }
                      }
                      CustomExerciseCreatorContent(
                          onBack = { showInlineCreator = false },
                          onSaved = { createdId ->
                              highlightedCreatedExerciseId = createdId
                              showInlineCreator = false
                              onExerciseSearch("")
                          },
                          modifier = Modifier
                              .fillMaxWidth()
                              .weight(1f),
                      )
                   } else {
                       ExercisePickerSheet(
                           query = uiState.searchQuery,
                           catalog = EXERCISE_DATABASE,
                           workoutLogs = uiState.workoutLogs,
                           editingExisting = uiState.pickerTargetExerciseId != null,
                           highlightedExerciseId = highlightedCreatedExerciseId,
                           selectedExercisesIds = uiState.selectedExercisesIds,
                           onToggleExerciseSelection = onToggleExerciseSelection,
                           onClearExerciseSelection = onClearExerciseSelection,
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
                           onOpenExerciseCreator = { showInlineCreator = true },
                           onDismiss = requestPickerDismiss,
                           onSelectionChange = { pendingPickerSelection = it },
                       )
                   }
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
            SessionEditorSheet.BACKGROUND -> CoverSheet(
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
                setTargetDuration = setTargetDuration,
                setPartTargetDuration = setPartTargetDuration,
                setExerciseTargetDuration = setExerciseTargetDuration,
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
                templates = allTemplates,
                onApplyAugeCorrection = onApplyAugeCorrection,
                onAddGhostExercise = onAddGhostExercise,
                onApplyAssistantSuggestion = onApplyAssistantSuggestion,
                onTemplateSearchChange = onTemplateSearchChange,
                onSelectTemplate = onSelectTemplate,
                onConfirmApplyTemplate = onConfirmApplyTemplate,
                onCancelTemplateApply = onCancelTemplateApply,
            )
            SessionEditorSheet.WARMUP -> WarmupSheet(exercise = warmupExercise, onSave = onWarmupSave)
            SessionEditorSheet.MOBILITY_PICKER -> MobilityPickerSheet(
                onAdd = onAddMobilityExercise,
                onDismiss = onDismiss,
            )
            SessionEditorSheet.SUPERSERIE_MANAGER -> {
                val supersetExercises = uiState.supersetManagerSupersetId
                    ?.let { SupersetRules.orderedMembers(session, it) }
                    .orEmpty()
                val supersetGroup = uiState.supersetManagerSupersetId
                    ?.let { id -> session.allSupersetGroups().firstOrNull { it.id == id } }
                SupersetManagerSheet(
                    exercises = supersetExercises,
                    partId = uiState.supersetManagerPartId,
                    supersetId = uiState.supersetManagerSupersetId ?: "",
                    restBetweenSeconds = supersetGroup?.restBetweenExercises
                        ?: supersetExercises.firstOrNull()?.supersetRestBetween,
                    restAfterSeconds = supersetGroup?.restAfterSuperset
                        ?: supersetExercises.firstOrNull()?.supersetRestAfter,
                    onUpdateRestBetween = onUpdateSupersetRestBetween,
                    onUpdateRestAfter = onUpdateSupersetRestAfter,
                    onRemove = onRemoveFromSuperset,
                    onDismiss = onDismiss,
                )
            }
            SessionEditorSheet.SUPERSET_CREATOR -> {
                val draft = uiState.supersetDraft ?: return@ModalBottomSheet
                SupersetMemberPickerSheet(
                    draft = draft,
                    sessionExercises = session.allExercises(),
                    onUpdateDraft = onSupersetDraftUpdate,
                    onConfirm = onCreateSupersetGroup,
                    onOpenCatalog = onOpenExerciseCreator,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TemplateCatalogBrowser(
                        templates = templates,
                        searchQuery = searchQuery,
                        onSelectTemplate = onSelectTemplate,
                        exerciseIndex = remember { EXERCISE_DATABASE.associateBy { it.id.lowercase() } }
                    )
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
    var showRestPicker by rememberSaveable { mutableStateOf(false) }
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
            (draft.exerciseIds + exerciseId).take(4)
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
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Crear superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Elige entre 2 y 4 ejercicios de la sesión, ordena la secuencia y configura descansos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Seleccionar ejercicios", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableExercises.forEach { exercise ->
                    val selected = exercise.id in draft.exerciseIds
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = selected || draft.exerciseIds.size < 4) { toggleExercise(exercise.id) },
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
                                } else if (draft.exerciseIds.size >= 4) {
                                    Text(
                                        "Limite 4 ejercicios",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            SupersetRestPickerButton(
                restBetweenSeconds = draft.restBetweenExercises,
                restAfterSeconds = draft.restAfterSuperset,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showRestPicker = true },
            )
            if (showRestPicker) {
                SupersetRestPickerDialog(
                    initialRestBetweenSeconds = draft.restBetweenExercises,
                    initialRestAfterSeconds = draft.restAfterSuperset,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onDismiss = { showRestPicker = false },
                    onConfirm = { restBetween, restAfter ->
                        onUpdateDraft(
                            draft.copy(
                                restBetweenExercises = restBetween,
                                restAfterSuperset = restAfter,
                            ),
                        )
                        showRestPicker = false
                    },
                )
            }
            EditorMiniField(
                label = "Rondas (opcional)",
                value = draft.rounds?.toString().orEmpty(),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                onCommit = { input ->
                    val clean = input.filter { it.isDigit() }.take(3)
                    onUpdateDraft(draft.copy(rounds = clean.toIntOrNull()))
                },
            )
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancelar")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = draft.exerciseIds.distinct().size in 2..4,
            ) {
                Text("Crear superserie", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SupersetMemberPickerSheet(
    draft: SupersetDraft,
    sessionExercises: List<Exercise>,
    onUpdateDraft: (SupersetDraft) -> Unit,
    onConfirm: () -> Unit,
    onOpenCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIds = draft.exerciseIds.toSet()
    val availableExercises = remember(sessionExercises, draft.exerciseIds) {
        sessionExercises.filter { it.id !in selectedIds && !it.isInSuperset() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Añadir a superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            "Mueve un ejercicio que ya está en esta sesión o abre el catálogo para crear otro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableExercises.forEach { exercise ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = draft.exerciseIds.size < 4) {
                            onUpdateDraft(draft.copy(exerciseIds = (draft.exerciseIds + exercise.id).distinct().take(4)))
                            onConfirm()
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(exercise.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (availableExercises.isEmpty()) {
                Text("No hay ejercicios libres en esta sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Abrir catálogo")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
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
        CatalogSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar movilidad, zona o molestia",
        )
        // Body region filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                CompactCatalogFilterChip(
                    selected = selectedRegion.isBlank(),
                    onClick = { selectedRegion = "" },
                    label = "Todas",
                )
            }
            items(uniqueRegions) { region ->
                CompactCatalogFilterChip(
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = region.replaceFirstChar { it.uppercase() },
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                                maxLines = 1,
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
    restBetweenSeconds: Int?,
    restAfterSeconds: Int?,
    onUpdateRestBetween: (String?, String, Int) -> Unit,
    onUpdateRestAfter: (String?, String, Int) -> Unit,
    onRemove: (String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var restBetween by rememberSaveable(supersetId, restBetweenSeconds) {
        mutableStateOf(restBetweenSeconds?.toString().orEmpty())
    }
    var restAfter by rememberSaveable(supersetId, restAfterSeconds) {
        mutableStateOf(restAfterSeconds?.toString().orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gestionar superserie", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${exercises.size} ejercicios vinculados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            exercises.forEachIndexed { index, exercise ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${index + 1}", fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${exercise.sets.size} series · descanso individual reemplazado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onRemove(partId, exercise.id); onDismiss() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(
    query: String,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    editingExisting: Boolean,
    selectedExercisesIds: Set<String> = emptySet(),
    onToggleExerciseSelection: (String) -> Unit = {},
    onClearExerciseSelection: () -> Unit = {},
    onSearch: (String) -> Unit,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)? = null,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenExerciseCreator: () -> Unit,
    onDismiss: () -> Unit,
    highlightedExerciseId: String? = null,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit = {},
) {
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val fullCatalog = remember(catalog, customExercises) {
        (customExercises + catalog)
            .distinctBy { it.id.lowercase() }
    }
    var selectedRegion by rememberSaveable { mutableStateOf<ExerciseCatalogRegion?>(null) }
    var selectedTrait by rememberSaveable { mutableStateOf<ExerciseCatalogTrait?>(null) }
    var sortMode by rememberSaveable { mutableStateOf(ExerciseCatalogSort.RELEVANCE) }
    var showSortMenu by remember { mutableStateOf(false) }
    var infoExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

    var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeadName by rememberSaveable { mutableStateOf<String?>(null) }
    var showRegionMenu by remember { mutableStateOf(false) }
    var showMuscleMenu by remember { mutableStateOf(false) }
    var showHeadMenu by remember { mutableStateOf(false) }

    var showEmphasisCard by remember { mutableStateOf(true) }

    LaunchedEffect(selectedMuscle) {
        selectedHeadName = null
        showEmphasisCard = true
    }
    LaunchedEffect(selectedHeadName) {
        showEmphasisCard = true
    }

    var selectionOrder by rememberSaveable { mutableStateOf(listOf<String>()) }
    LaunchedEffect(selectedExercisesIds) {
        selectionOrder = selectionOrder.filter { it in selectedExercisesIds } +
            selectedExercisesIds.filterNot { it in selectionOrder }
    }
    val selectedExercises = remember(selectedExercisesIds, fullCatalog, selectionOrder) {
        val byId = fullCatalog.associateBy { it.id }
        selectionOrder.mapNotNull(byId::get).filter { it.id in selectedExercisesIds }
    }

    val normalizedQuery = query.trim()
    val activeRegion = selectedRegion ?: ExerciseCatalogRegion.ALL
    val showGroupBrowser = false

    val exercisesByRegion = remember(fullCatalog) {
        fullCatalog.groupBy { resolveExerciseRegion(it) }
    }
    val exercisesByMuscle = remember(fullCatalog) {
        val map = mutableMapOf<String, MutableList<ExerciseMuscleInfo>>()
        fullCatalog.forEach { ex ->
            ex.involvedMuscles.forEach { m ->
                map.getOrPut(m.muscle.lowercase()) { mutableListOf() }.add(ex)
            }
        }
        map
    }

    val filteredMuscles = remember(activeRegion, fullCatalog, exercisesByRegion) {
        if (activeRegion == ExerciseCatalogRegion.ALL) {
            ALL_MUSCLES
        } else {
            val regionExs = exercisesByRegion[activeRegion].orEmpty()
            val presentMuscles = regionExs.flatMap { ex -> ex.involvedMuscles.map { it.muscle.lowercase() } }.toSet()
            ALL_MUSCLES.filter { it.canonicalName.lowercase() in presentMuscles }
        }
    }

    LaunchedEffect(filteredMuscles) {
        if (selectedMuscle != null && filteredMuscles.none { it.canonicalName.equals(selectedMuscle, ignoreCase = true) }) {
            selectedMuscle = null
        }
    }

    val filteredRegions = remember(selectedMuscle, fullCatalog, exercisesByMuscle) {
        val allRegions = ExerciseCatalogRegion.values().toList()
        if (selectedMuscle == null) {
            allRegions
        } else {
            val muscleExs = exercisesByMuscle[selectedMuscle!!.lowercase()].orEmpty()
            val presentRegions = muscleExs.map { resolveExerciseRegion(it) }.toSet()
            allRegions.filter { it == ExerciseCatalogRegion.ALL || it in presentRegions }
        }
    }

    LaunchedEffect(filteredRegions) {
        val currentRegion = selectedRegion ?: ExerciseCatalogRegion.ALL
        if (currentRegion != ExerciseCatalogRegion.ALL && filteredRegions.none { it == currentRegion }) {
            selectedRegion = null
        }
    }

    val filteredSortModes = remember(selectedRegion, selectedMuscle) {
        ExerciseCatalogSort.values().filter { option ->
            when (option) {
                ExerciseCatalogSort.GROUP_BY_REGION -> selectedRegion == null
                ExerciseCatalogSort.GROUP_BY_MUSCLE -> selectedMuscle == null
                else -> true
            }
        }
    }

    LaunchedEffect(filteredSortModes) {
        if (sortMode !in filteredSortModes) {
            sortMode = ExerciseCatalogSort.RELEVANCE
        }
    }

    var variantFlowExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }

    fun handleSelect(info: ExerciseMuscleInfo) {
        if (editingExisting) {
            onSelect(info)
        } else {
            selectionOrder = if (info.id in selectedExercisesIds) {
                selectionOrder - info.id
            } else {
                selectionOrder + info.id
            }
            onToggleExerciseSelection(info.id)
        }
    }
    val results = remember(query, fullCatalog, activeRegion, selectedTrait, sortMode, selectedMuscle, selectedHeadName) {
        val muscleAnatomy = selectedMuscle?.let { MUSCLE_BY_CANONICAL[it] }
        val muscleHead = if (muscleAnatomy != null && selectedHeadName != null) {
            muscleAnatomy.heads.firstOrNull { it.name == selectedHeadName }
        } else null

        val baseFiltered = fullCatalog.filter { info ->
            val regionMatch = activeRegion == ExerciseCatalogRegion.ALL || resolveExerciseRegion(info) == activeRegion
            val traitMatch = selectedTrait == null || matchesCatalogTrait(info, selectedTrait!!)
            val muscleMatch = muscleAnatomy == null || matchesMuscle(info, muscleAnatomy)
            val headMatch = muscleHead == null || matchesMuscleHead(info, muscleAnatomy!!, muscleHead)
            regionMatch && traitMatch && muscleMatch && headMatch
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

        val sorted = when (sortMode) {
            ExerciseCatalogSort.RELEVANCE -> searched
            ExerciseCatalogSort.FATIGUE_HIGH -> {
                val fatigueMap: Map<String, Int> = searched.associate { it.id to calculateFriendlyFatigue(it).overall }
                searched.sortedByDescending { fatigueMap[it.id] ?: 0 }
            }
            ExerciseCatalogSort.FATIGUE_LOW -> {
                val fatigueMap: Map<String, Int> = searched.associate { it.id to calculateFriendlyFatigue(it).overall }
                searched.sortedBy { fatigueMap[it.id] ?: 0 }
            }
            ExerciseCatalogSort.NAME -> searched.sortedBy { it.name }
            ExerciseCatalogSort.GROUP_BY_MUSCLE -> searched.sortedWith(compareBy({ resolvePrimaryMuscleLabel(it) }, { it.name }))
            else -> searched
        }
        val finalSorted = if (selectedMuscle != null && selectedMuscle.equals("Trapecio", ignoreCase = true)) {
            sorted.sortedBy { exercise ->
                val involvement = exercise.involvedMuscles.find {
                    VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis).equals("Trapecio", ignoreCase = true)
                }
                when (involvement?.role) {
                    MuscleRole.PRIMARY -> 0
                    MuscleRole.SECONDARY -> 1
                    else -> 2
                }
            }
        } else {
            sorted
        }
        deduplicateCatalogVisualResults(finalSorted)
    }
    val resultListState = rememberLazyListState()
    LaunchedEffect(normalizedQuery, activeRegion, selectedTrait, sortMode, selectedMuscle, selectedHeadName) {
        resultListState.scrollToItem(0)
    }

    val infoExercise = remember(infoExerciseId, fullCatalog) { fullCatalog.firstOrNull { it.id == infoExerciseId } }
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
    val createdCatalog = remember(customExercises) {
        customExercises.sortedBy { it.name.lowercase() }
    }
    val highlightedExercise = remember(highlightedExerciseId, fullCatalog) {
        highlightedExerciseId?.let { id -> fullCatalog.firstOrNull { it.id == id } }
    }
    val categorizedCatalog = remember(fullCatalog) {
        fullCatalog
            .filter { !it.category.isNullOrBlank() }
            .groupBy { it.category!!.trim() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .toList()
    }
    val uncategorizedCatalog = remember(fullCatalog) {
        fullCatalog.filter { it.category.isNullOrBlank() }
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
                    "${fullCatalog.size} ejercicios",
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
                    FilledTonalButton(
                        onClick = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sortMode.label, style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        filteredSortModes.forEach { option ->
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

        CatalogSearchField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar por nombre, músculo o equipo",
        )

         val currentHeadMuscle = selectedMuscle?.let { MUSCLE_BY_CANONICAL[it] }
         val hasHeads = currentHeadMuscle != null && currentHeadMuscle.heads.isNotEmpty()
         Row(
             modifier = Modifier.fillMaxWidth(),
             horizontalArrangement = Arrangement.spacedBy(6.dp),
             verticalAlignment = Alignment.CenterVertically,
         ) {
             Box(modifier = Modifier.weight(1f)) {
                 FilledTonalButton(
                     onClick = { showRegionMenu = true },
                     contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                     shape = RoundedCornerShape(8.dp),
                     modifier = Modifier.fillMaxWidth(),
                 ) {
                     Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                     Spacer(Modifier.width(4.dp))
                     Text(activeRegion.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                     Spacer(Modifier.width(2.dp))
                     Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                 }
                 DropdownMenu(expanded = showRegionMenu, onDismissRequest = { showRegionMenu = false }) {
                     filteredRegions.forEach { region ->
                         DropdownMenuItem(
                             text = { Text(region.label) },
                             onClick = { selectedRegion = if (region == ExerciseCatalogRegion.ALL) null else region; showRegionMenu = false },
                         )
                     }
                 }
             }
             Box(modifier = Modifier.weight(1f)) {
                 FilledTonalButton(
                     onClick = { showMuscleMenu = true },
                     contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                     shape = RoundedCornerShape(8.dp),
                     modifier = Modifier.fillMaxWidth(),
                 ) {
                     Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(14.dp))
                     Spacer(Modifier.width(4.dp))
                     Text(selectedMuscle?.let { MUSCLE_BY_CANONICAL[it]?.displayName } ?: "Músculo", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                     Spacer(Modifier.width(2.dp))
                     Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                 }
                 DropdownMenu(expanded = showMuscleMenu, onDismissRequest = { showMuscleMenu = false }) {
                     DropdownMenuItem(text = { Text("Todos") }, onClick = { selectedMuscle = null; showMuscleMenu = false })
                     filteredMuscles.forEach { muscle ->
                         DropdownMenuItem(
                             text = { Text(muscle.displayName) },
                             onClick = { selectedMuscle = muscle.canonicalName; showMuscleMenu = false },
                         )
                     }
                 }
             }
             if (hasHeads) {
                 Box(modifier = Modifier.weight(1f)) {
                     FilledTonalButton(
                         onClick = { showHeadMenu = true },
                         contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                         shape = RoundedCornerShape(8.dp),
                         modifier = Modifier.fillMaxWidth(),
                     ) {
                         Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                         Spacer(Modifier.width(4.dp))
                         Text(selectedHeadName ?: "Zona", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                         Spacer(Modifier.width(2.dp))
                         Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
                     }
                     DropdownMenu(expanded = showHeadMenu, onDismissRequest = { showHeadMenu = false }) {
                         DropdownMenuItem(text = { Text("Completo") }, onClick = { selectedHeadName = null; showHeadMenu = false })
                         currentHeadMuscle!!.heads.forEach { head ->
                             DropdownMenuItem(
                                 text = { Text(head.name) },
                                 onClick = { selectedHeadName = head.name; showHeadMenu = false },
                             )
                         }
                     }
                 }
             }
         }

         AnimatedVisibility(visible = hasHeads && showEmphasisCard) {
             val emphasisTitle = if (selectedHeadName != null) {
                 "Énfasis: $selectedHeadName"
             } else {
                 "Énfasis en porciones de ${currentHeadMuscle?.displayName}"
             }
             val emphasisBody = getMuscleEmphasisEducationalText(selectedMuscle ?: "", selectedHeadName)

             Card(
                 modifier = Modifier
                     .fillMaxWidth()
                     .padding(vertical = 6.dp),
                 colors = CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                 ),
                 shape = RoundedCornerShape(12.dp),
                 border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
             ) {
                 Column(modifier = Modifier.padding(12.dp)) {
                     Row(
                         modifier = Modifier.fillMaxWidth(),
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         Row(
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier.weight(1f)
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Info,
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(16.dp)
                             )
                             Spacer(Modifier.width(6.dp))
                             Text(
                                 text = emphasisTitle,
                                 style = MaterialTheme.typography.titleSmall,
                                 color = MaterialTheme.colorScheme.primary,
                                 fontWeight = FontWeight.SemiBold,
                                 maxLines = 1,
                                 overflow = TextOverflow.Ellipsis
                             )
                         }
                         IconButton(
                             onClick = { showEmphasisCard = false },
                             modifier = Modifier.size(24.dp)
                         ) {
                             Icon(
                                 imageVector = Icons.Default.Close,
                                 contentDescription = "Cerrar",
                                 tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                 modifier = Modifier.size(16.dp)
                             )
                         }
                     }
                     if (emphasisBody.isNotEmpty()) {
                         Spacer(Modifier.height(4.dp))
                         Text(
                             text = emphasisBody,
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant
                         )
                         Spacer(Modifier.height(4.dp))
                         Text(
                             text = "*El énfasis desplaza el estímulo relativo, pero no aísla por completo el músculo del resto de sus cabezas.",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                             fontStyle = FontStyle.Italic
                         )
                     }
                 }
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
                 val count = fullCatalog.count { region == ExerciseCatalogRegion.ALL || resolveExerciseRegion(it) == region }
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
                                      isSelected = info.id in selectedExercisesIds,
                                      onSelect = { handleSelect(info) },
                                      onInfo = { infoExerciseId = info.id },
                                      onOpenVariantFlow = { variantFlowExercise = info },
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
                          isSelected = info.id in selectedExercisesIds,
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
             state = resultListState,
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ExerciseCatalogTrait.values().toList(), key = { it.name }) { trait ->
                            CompactCatalogFilterChip(
                                selected = selectedTrait == trait,
                                onClick = { selectedTrait = if (selectedTrait == trait) null else trait },
                                label = trait.label,
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
                         if (selectedTrait != null || activeRegion != ExerciseCatalogRegion.ALL || normalizedQuery.isNotBlank() || selectedMuscle != null || selectedHeadName != null) {
                             TextButton(
                                 onClick = {
                                     selectedRegion = null
                                     selectedTrait = null
                                     selectedMuscle = null
                                     selectedHeadName = null
                                     onSearch("")
                                     sortMode = ExerciseCatalogSort.RELEVANCE
                                 }
                             ) { Text("Limpiar") }
                         }
                     }
                     if (highlightedExercise != null || (createdCatalog.isNotEmpty() && normalizedQuery.isBlank() && selectedTrait == null && activeRegion == ExerciseCatalogRegion.ALL)) {
                         Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             Text(
                                 "Creados por ti",
                                 style = MaterialTheme.typography.labelLarge,
                                 fontWeight = FontWeight.Black,
                                 color = Color.White,
                             )
                             highlightedExercise?.let { info ->
                                 ExercisePickerDetailedCard(
                                     info = info,
                                     isSelected = info.id in selectedExercisesIds,
                                     onSelect = { handleSelect(info) },
                                     onInfo = { infoExerciseId = info.id },
                                 )
                             }
                             createdCatalog
                                 .filterNot { it.id == highlightedExercise?.id }
                                 .take(4)
                                 .forEach { info ->
                                     ExercisePickerDetailedCard(
                                         info = info,
                                         isSelected = info.id in selectedExercisesIds,
                                         onSelect = { handleSelect(info) },
                                         onInfo = { infoExerciseId = info.id },
                                     )
                                 }
                         }
                     }
                 }
             }
              items(results, key = { it.id }) { info ->
                  ExercisePickerDetailedCard(
                      info = info,
                      isSelected = info.id in selectedExercisesIds,
                      onSelect = { handleSelect(info) },
                      onInfo = { infoExerciseId = info.id },
                      onOpenVariantFlow = { variantFlowExercise = info },
                  )
              }
          }

        if (!editingExisting && selectedExercises.isNotEmpty()) {
            var showSelectedList by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSelectedList = !showSelectedList },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${selectedExercises.size} seleccionados",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            if (showSelectedList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            null,
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    if (showSelectedList) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            selectedExercises.forEach { info ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        info.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(
                                        onClick = {
                                            selectionOrder = selectionOrder - info.id
                                            onToggleExerciseSelection(info.id)
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            "Quitar",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedExercises.size >= 2 && onCreateSuperset != null) {
                            Button(
                                onClick = {
                                    onCreateSuperset(selectedExercises)
                                    onClearExerciseSelection()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Crear superserie", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                onMultiSelect(selectedExercises)
                                onClearExerciseSelection()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Agregar ${selectedExercises.size}", maxLines = 1)
                        }
                    }
                }
            }
        }
        }
    }

    infoExercise?.let { selected ->
        ExerciseCatalogInfoDialog(
            exercise = selected,
            catalog = fullCatalog,
            associatedDiscomforts = discomfortByExercise[selected.id].orEmpty(),
            onOpenExercise = onOpenExerciseDetail,
            onDismiss = { infoExerciseId = null },
            onOpenVariantFlow = { ex -> variantFlowExercise = ex },
        )
    }

    variantFlowExercise?.let { exercise ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        VariantFlowSheet(
            initialExercise = exercise,
            sheetState = sheetState,
            onConfirm = { selectedVariant, selectedAspects ->
                VariantFlowResultCache.store(
                    exerciseDbId = selectedVariant.id,
                    variantName = selectedVariant.variantName,
                    variantGroupId = selectedVariant.variantGroupId,
                    variantGroupName = selectedVariant.variantGroupName,
                    selectedAspects = selectedAspects,
                )
                if (editingExisting) {
                    onSelect(selectedVariant)
                } else {
                    if (selectedVariant.id !in selectedExercisesIds) {
                        selectionOrder = selectionOrder + selectedVariant.id
                        onToggleExerciseSelection(selectedVariant.id)
                    }
                }
                variantFlowExercise = null
            },
            onDismiss = { variantFlowExercise = null },
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
    onOpenVariantFlow: (() -> Unit)? = null,
) {
    val bgAlpha = if (isSelected) 0.40f else 0.24f
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha),
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
                if (onOpenVariantFlow != null && !info.variantGroupId.isNullOrBlank()) {
                    IconButton(onClick = onOpenVariantFlow, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configuración avanzada",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
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
    onOpenVariantFlow: (() -> Unit)? = null,
) {
    val primaryMuscle = resolvePrimaryMuscleLabel(info)
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
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onOpenVariantFlow != null && !info.variantGroupId.isNullOrBlank()) {
                    IconButton(onClick = onOpenVariantFlow) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configuración avanzada",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
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

            if (!info.description.isNullOrBlank()) {
                Text(
                    info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
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
    onOpenVariantFlow: ((ExerciseMuscleInfo) -> Unit)? = null,
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

                if (onOpenVariantFlow != null && !exercise.variantGroupId.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenVariantFlow(exercise)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Configuración avanzada (Aspectos técnicos)")
                    }
                }

                exercise.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                com.example.kpkn.screens.wikilab.ExerciseMinimalistChipsCarousel(
                    exercise = exercise,
                    fatigueScore = fatigue.overall,
                    modifier = Modifier.fillMaxWidth()
                )

                val muscleContributions = remember(exercise.id, exercise.involvedMuscles) {
                    oneSeriesVolumeContributions(exercise)
                }
                if (muscleContributions.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Músculos involucrados",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black
                        )
                        muscleContributions.forEach { item ->
                            val color = com.example.kpkn.screens.wikilab.wikilabMuscleColor(item.muscle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = RoundedCornerShape(50),
                                        color = color,
                                    ) {}
                                    Column {
                                        Text(
                                            item.muscle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            roleVolumeLabel(item.role),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = color.copy(alpha = 0.08f),
                                ) {
                                    Text(
                                        formatSeriesEquivalent(item.seriesEquivalent),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                    )
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

                ExerciseFatigueScenarios(exercise = exercise)
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
private fun CoverSheet(
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
    val isImageBackground = session.background?.type == SessionBackgroundType.IMAGE
    var coverTab by rememberSaveable { mutableStateOf(if (session.background?.value?.startsWith("solid://") == true) "solid" else "gradient") }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader("Portada de sesión", "Elige un fondo y ajusta solo lo que corresponde a ese tipo.")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(DarkEditorChip)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CoverTabButton("GRADIENTES", coverTab == "gradient", modifier = Modifier.weight(1f)) { coverTab = "gradient" }
            CoverTabButton("SÓLIDOS", coverTab == "solid", modifier = Modifier.weight(1f)) { coverTab = "solid" }
        }

        if (coverTab == "gradient") {
            Text("Gradientes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
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
        }
        if (coverTab == "solid") {
            Text("Colores sólidos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
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
        }
        if (isImageBackground) {
            Text(
                "Los fondos con imagen local se desactivaron para esta versión. Usa color sólido o gradiente.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoverTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) DarkEditorChipSelected else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistorySheet(
    uiState: SessionEditorUiState,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader("Historial y borradores", "Restaura snapshots locales o revisa sesiones registradas.")
        Text("Cambios recientes del borrador", style = MaterialTheme.typography.labelLarge)
        if (uiState.localDraftHistory.isEmpty()) {
            Text("Todavía no hay snapshots locales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            uiState.localDraftHistory.asReversed().forEachIndexed { index, snapshot ->
                val title = snapshot.session.name.ifBlank { "Sesión" }
                val diffSummary = snapshot.changedFields.take(3).joinToString(" · ")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onRestoreSnapshot(snapshot) },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkEditorSurfaceSoft,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
private fun RestTimeField(
label: String,
seconds: Int,
modifier: Modifier = Modifier,
onClick: () -> Unit
) {
val minutes = seconds / 60
val secs = seconds % 60
val displayValue = String.format(java.util.Locale.US, "%d:%02d", minutes, secs)

Box(
modifier = modifier
.clickable { onClick() }
) {
OutlinedTextField(
value = displayValue,
onValueChange = {},
readOnly = true,
label = { Text(label) },
singleLine = true,
modifier = Modifier.fillMaxWidth(),
enabled = false,
shape = RoundedCornerShape(14.dp),
textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
colors = OutlinedTextFieldDefaults.colors(
disabledTextColor = Color.White,
disabledBorderColor = Color.White.copy(alpha = 0.35f),
disabledLabelColor = Color.White.copy(alpha = 0.5f)
)
)
Box(
modifier = Modifier
.matchParentSize()
.background(Color.Transparent)
.clickable { onClick() }
)
}
}

@Composable
private fun RestTimePickerDialog(
title: String,
initialSeconds: Int,
onConfirm: (Int) -> Unit,
onDismiss: () -> Unit
) {
var minInput by remember { mutableStateOf((initialSeconds / 60).toString()) }
var secInput by remember { mutableStateOf((initialSeconds % 60).toString()) }

AlertDialog(
onDismissRequest = onDismiss,
title = { Text(title, fontWeight = FontWeight.Bold) },
text = {
Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
Text("Ingresa los minutos y segundos para el descanso.", style = MaterialTheme.typography.bodyMedium)
Row(
modifier = Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.spacedBy(16.dp)
) {
OutlinedTextField(
value = minInput,
onValueChange = { minInput = it.filter { char -> char.isDigit() }.take(2) },
label = { Text("Minutos") },
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
modifier = Modifier.weight(1f),
singleLine = true,
shape = RoundedCornerShape(12.dp)
)
OutlinedTextField(
value = secInput,
onValueChange = { secInput = it.filter { char -> char.isDigit() }.take(2) },
label = { Text("Segundos") },
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
modifier = Modifier.weight(1f),
singleLine = true,
shape = RoundedCornerShape(12.dp)
)
}
}
},
confirmButton = {
TextButton(
onClick = {
val m = minInput.toIntOrNull() ?: 0
val s = secInput.toIntOrNull() ?: 0
onConfirm(m * 60 + s)
}
) {
Text("Aceptar", fontWeight = FontWeight.Bold)
}
},
dismissButton = {
TextButton(onClick = onDismiss) {
Text("Cancelar")
}
}
)
}

@Composable
private fun RulesSheet(
uiState: SessionEditorUiState,
onApplyRules: (String?) -> Unit,
onRuleDefaultsChange: (String?, Int?, Int?, Double?, Int?, Int?, Int?, Int?, Boolean?, DefaultIntensityType?) -> Unit,
onRuleLimitsChange: (Double?, Int?) -> Unit,
onAdvancedRuleLimitsChange: (Double?, Double?, Int?, Boolean) -> Unit,
onApplyGlobalIntensityAdjustment: (IntensityMode, Double, Set<String>?) -> Unit,
setTargetDuration: (Int?) -> Unit,
setPartTargetDuration: (String, Int?) -> Unit,
setExerciseTargetDuration: (String, Int?) -> Unit,
onSave: () -> Unit = {},
onDismiss: () -> Unit = {},
) {
var activeTab by remember { mutableIntStateOf(0) }
var scopePartId by remember { mutableStateOf<String?>(null) }

val defaults = remember(scopePartId, uiState.ruleDefaults, uiState.partRuleDefaults) {
if (scopePartId == null) uiState.ruleDefaults
else (uiState.partRuleDefaults[scopePartId] ?: uiState.ruleDefaults)
}

var activeRestDialog by remember { mutableStateOf<String?>(null) }

if (activeRestDialog != null) {
val (title, currentSecs, onConfirmCallback) = when (activeRestDialog) {
"normal" -> Triple(
"Descanso de series",
defaults.normalRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, secs, null, null, null, null, null) }
)
"sides" -> Triple(
"Descanso entre lados",
defaults.betweenSidesRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, secs, null, null, null, null) }
)
"between" -> Triple(
"Descanso entre ejercicios",
defaults.supersetBetweenRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, null, secs, null, null, null) }
)
"round" -> Triple(
"Descanso de rondas",
defaults.supersetRoundRestSeconds,
{ secs: Int -> onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, secs, null, null) }
)
else -> Triple("", 0, { _: Int -> })
}
RestTimePickerDialog(
title = title,
initialSeconds = currentSecs,
onConfirm = {
onConfirmCallback(it)
activeRestDialog = null
},
onDismiss = { activeRestDialog = null }
)
}

Column(
Modifier
.fillMaxWidth()
.verticalScroll(rememberScrollState())
.imePadding()
.padding(horizontal = 18.dp, vertical = 14.dp),
verticalArrangement = Arrangement.spacedBy(14.dp),
) {
SheetHeader(title = "Reglas y tiempo", subtitle = "Configura límites de tiempo y reglas base de la sesión.")

TabRow(
selectedTabIndex = activeTab,
containerColor = Color.Transparent,
contentColor = MaterialTheme.colorScheme.primary
) {
Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
Text("Reglas", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}
Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
Text("Límites de tiempo", modifier = Modifier.padding(vertical = 10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}
}

if (activeTab == 0) {
// Scope Selector UI (for different rules per category/session)
Text("Configurar reglas por grupo:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
Row(
horizontalArrangement = Arrangement.spacedBy(8.dp),
modifier = Modifier.horizontalScroll(rememberScrollState())
) {
DarkChoiceChip("Toda la sesión", scopePartId == null) { scopePartId = null }
uiState.session?.parts?.forEach { part ->
DarkChoiceChip(part.name, scopePartId == part.id) { scopePartId = part.id }
}
}
Spacer(Modifier.height(2.dp))

Surface(
shape = RoundedCornerShape(18.dp),
color = DarkEditorSurface,
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
Text("Valores de serie", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)

Row(
horizontalArrangement = Arrangement.spacedBy(12.dp),
modifier = Modifier.fillMaxWidth(),
verticalAlignment = Alignment.CenterVertically
) {
Text("Intensidad:", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
listOf(DefaultIntensityType.RPE, DefaultIntensityType.RIR, DefaultIntensityType.FALLO).forEach { type ->
val selected = defaults.intensityType == type
val label = when (type) {
DefaultIntensityType.RPE -> "RPE"
DefaultIntensityType.RIR -> "RIR"
DefaultIntensityType.FALLO -> "Fallo"
}
Box(
modifier = Modifier
.clip(RoundedCornerShape(8.dp))
.background(if (selected) MaterialTheme.colorScheme.primary else DarkEditorChip)
.clickable { onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, null, type) }
.padding(horizontal = 12.dp, vertical = 6.dp)
) {
Text(
text = label,
color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
fontWeight = FontWeight.Bold,
style = MaterialTheme.typography.bodySmall
)
}
}
}
}

Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
EditorMiniField("Series", defaults.setCount.toString(), keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)) {
onRuleDefaultsChange(scopePartId, it.safeIntOrNull(), null, null, null, null, null, null, null, null)
}
EditorMiniField("Reps", defaults.reps.toString(), keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f)) {
onRuleDefaultsChange(scopePartId, null, it.safeIntOrNull(), null, null, null, null, null, null, null)
}
if (defaults.intensityType != DefaultIntensityType.FALLO) {
val label = if (defaults.intensityType == DefaultIntensityType.RPE) "RPE" else "RIR"
EditorMiniField(label, formatEditableNumber(defaults.rpe), keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f)) {
onRuleDefaultsChange(scopePartId, null, null, it.safeDoubleOrNull(), null, null, null, null, null, null)
}
}
}

Text("Descansos (Min:Seg)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
RestTimeField("Normal", defaults.normalRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "normal"
}
RestTimeField("Lados", defaults.betweenSidesRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "sides"
}
}
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
RestTimeField("Entre ej.", defaults.supersetBetweenRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "between"
}
RestTimeField("Rondas", defaults.supersetRoundRestSeconds, modifier = Modifier.weight(1f)) {
activeRestDialog = "round"
}
}

Row(
modifier = Modifier
.fillMaxWidth()
.clip(RoundedCornerShape(14.dp))
.background(DarkEditorChip)
.padding(horizontal = 12.dp, vertical = 8.dp),
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
Column(Modifier.weight(1f)) {
Text("Aplicar a nuevos elementos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
Text("Ejercicios, series, lados y supersets nuevos heredan estos valores.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
Switch(
checked = defaults.applyToNewItems,
onCheckedChange = { onRuleDefaultsChange(scopePartId, null, null, null, null, null, null, null, it, null) },
)
}
}
}

FilledTonalButton(onClick = { onApplyRules(scopePartId) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
Text("Aplicar", fontWeight = FontWeight.Black)
}
} else {
// Tab 2: Límites de tiempo
val session = uiState.session
if (session != null) {
var timeInput by remember(session.targetDurationMinutes) {
val m = session.targetDurationMinutes ?: 0
mutableStateOf("%02d:%02d:%02d".format(m / 60, m % 60, 0))
}

fun updateGlobalFromText(raw: String) {
timeInput = raw
}

fun applyGlobalTimeBudget() {
val parts = timeInput.split(":").map { it.toIntOrNull() ?: 0 }
val hh = parts.getOrElse(0) { 0 }
val mm = parts.getOrElse(1) { 0 }
val ss = parts.getOrElse(2) { 0 }
val totalSecs = hh * 3600 + mm * 60 + ss
val totalMin = if (totalSecs == 0) null else totalSecs / 60
setTargetDuration(totalMin)
}

Surface(
shape = RoundedCornerShape(18.dp),
color = DarkEditorSurface,
modifier = Modifier.fillMaxWidth()
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Text("Límite de tiempo global (guía)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
Text(
"Establece un límite de tiempo objetivo para toda la sesión. Sirve de referencia de ritmo durante el entrenamiento.",
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.onSurfaceVariant
)
OutlinedTextField(
value = timeInput,
onValueChange = { updateGlobalFromText(it) },
label = { Text("HH:MM:SS") },
placeholder = { Text("01:30:00", color = Color.White.copy(alpha = 0.4f)) },
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
modifier = Modifier.fillMaxWidth(),
textStyle = MaterialTheme.typography.bodyMedium.copy(
fontFamily = FontFamily.Monospace,
textAlign = TextAlign.Center,
color = Color.White
),
colors = OutlinedTextFieldDefaults.colors(
focusedTextColor = Color.White,
unfocusedTextColor = Color.White,
focusedBorderColor = MaterialTheme.colorScheme.primary,
unfocusedBorderColor = Color.Gray,
cursorColor = Color.White,
)
)
val partsSum = session.parts.sumOf { it.targetDurationMinutes ?: 0 } +
session.exercises.sumOf { it.targetDurationMinutes ?: 0 }
val sessionBudget = session.targetDurationMinutes ?: 0
if (sessionBudget > 0) {
val isOverBudget = partsSum > sessionBudget
val remaining = sessionBudget - partsSum
Text(
text = if (isOverBudget) {
"⚠️ Excede el presupuesto global por ${partsSum - sessionBudget} min ($partsSum min asignados)"
} else {
"⏱️ $partsSum de $sessionBudget min asignados (${if (remaining >= 0) "$remaining min disponibles" else ""})"
},
style = MaterialTheme.typography.bodySmall,
color = if (isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
fontWeight = FontWeight.Bold,
modifier = Modifier.padding(top = 4.dp)
)
}
}
}

Text("Tiempos por Grupos y Ejercicios", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
Text(
"Define presupuestos específicos (en minutos) en función del global, por ejemplo, para ejercicios que demandan mucho tiempo de setup.",
style = MaterialTheme.typography.bodySmall,
color = MaterialTheme.colorScheme.onSurfaceVariant
)

// Render parts (categories) and exercises inside them
session.parts.forEach { part ->
var partMinutesInput by remember(part.targetDurationMinutes) {
mutableStateOf(part.targetDurationMinutes?.toString() ?: "")
}
Card(
colors = CardDefaults.cardColors(containerColor = DarkEditorSurface),
modifier = Modifier.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth()
) {
Row(
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.weight(1f)
) {
Text("📁", fontSize = 16.sp)
Spacer(Modifier.width(6.dp))
Text(
part.name,
fontWeight = FontWeight.Bold,
style = MaterialTheme.typography.bodyMedium,
color = Color.White
)
}
Box(Modifier.width(90.dp).clipToBounds()) {
BasicTextField(
value = partMinutesInput,
onValueChange = {
partMinutesInput = it
setPartTargetDuration(part.id, it.toIntOrNull())
},
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, textAlign = TextAlign.End),
cursorBrush = SolidColor(Color.White),
decorationBox = { innerTextField ->
Row(
horizontalArrangement = Arrangement.End,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier
.fillMaxWidth()
.clipToBounds()
.background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
.padding(horizontal = 8.dp, vertical = 4.dp)
) {
Box(Modifier.weight(1f, fill = false).clipToBounds()) {
if (partMinutesInput.isEmpty()) {
Text("– min", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.4f))
}
innerTextField()
}
if (partMinutesInput.isNotEmpty()) {
Text(" min", style = MaterialTheme.typography.bodyMedium, color = Color.White)
}
}
}
)
}
}

val exercisesSum = part.exercises.sumOf { it.targetDurationMinutes ?: 0 }
val partBudget = part.targetDurationMinutes ?: 0
if (partBudget > 0) {
val isOverBudget = exercisesSum > partBudget
val remaining = partBudget - exercisesSum
Text(
text = if (isOverBudget) {
"⚠️ Excede el presupuesto del grupo por ${exercisesSum - partBudget} min ($exercisesSum min asignados)"
} else {
"⏱️ $exercisesSum de $partBudget min asignados (${if (remaining >= 0) "$remaining min disponibles" else ""})"
},
style = MaterialTheme.typography.bodySmall,
color = if (isOverBudget) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
fontWeight = FontWeight.Bold,
modifier = Modifier.padding(top = 2.dp)
)
}

if (part.exercises.isNotEmpty()) {
HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
part.exercises.forEach { ex ->
var exMinutesInput by remember(ex.targetDurationMinutes) {
mutableStateOf(ex.targetDurationMinutes?.toString() ?: "")
}
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
) {
Text(
ex.name,
style = MaterialTheme.typography.bodySmall,
color = Color.White.copy(alpha = 0.8f),
maxLines = 1,
overflow = TextOverflow.Ellipsis,
modifier = Modifier.weight(1f)
)
Spacer(Modifier.width(8.dp))
Box(Modifier.width(80.dp).clipToBounds()) {
BasicTextField(
value = exMinutesInput,
onValueChange = {
exMinutesInput = it
setExerciseTargetDuration(ex.id, it.toIntOrNull())
},
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, textAlign = TextAlign.End),
cursorBrush = SolidColor(Color.White),
decorationBox = { innerTextField ->
Row(
horizontalArrangement = Arrangement.End,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier
.fillMaxWidth()
.clipToBounds()
.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
.padding(horizontal = 6.dp, vertical = 2.dp)
) {
Box(Modifier.weight(1f, fill = false).clipToBounds()) {
if (exMinutesInput.isEmpty()) {
Text("– min", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
}
innerTextField()
}
if (exMinutesInput.isNotEmpty()) {
Text(" min", style = MaterialTheme.typography.bodySmall, color = Color.White)
}
}
}
)
}
}
}
}
}
}
}

// Render loose exercises (no part)
if (session.exercises.isNotEmpty()) {
Card(
colors = CardDefaults.cardColors(containerColor = DarkEditorSurface),
modifier = Modifier.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
Text("Otros Ejercicios", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
session.exercises.forEach { ex ->
var exMinutesInput by remember(ex.targetDurationMinutes) {
mutableStateOf(ex.targetDurationMinutes?.toString() ?: "")
}
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth()
) {
Text(
ex.name,
style = MaterialTheme.typography.bodySmall,
color = Color.White.copy(alpha = 0.8f),
maxLines = 1,
overflow = TextOverflow.Ellipsis,
modifier = Modifier.weight(1f)
)
Spacer(Modifier.width(8.dp))
Box(Modifier.width(80.dp).clipToBounds()) {
BasicTextField(
value = exMinutesInput,
onValueChange = {
exMinutesInput = it
setExerciseTargetDuration(ex.id, it.toIntOrNull())
},
singleLine = true,
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, textAlign = TextAlign.End),
cursorBrush = SolidColor(Color.White),
decorationBox = { innerTextField ->
Row(
horizontalArrangement = Arrangement.End,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier
.fillMaxWidth()
.clipToBounds()
.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
.padding(horizontal = 6.dp, vertical = 2.dp)
) {
Box(Modifier.weight(1f, fill = false).clipToBounds()) {
if (exMinutesInput.isEmpty()) {
Text("– min", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
}
innerTextField()
}
if (exMinutesInput.isNotEmpty()) {
Text(" min", style = MaterialTheme.typography.bodySmall, color = Color.White)
}
}
}
)
}
}
}
}
}
}
Spacer(Modifier.height(8.dp))
FilledTonalButton(
onClick = {
applyGlobalTimeBudget()
onSave()
onDismiss()
},
modifier = Modifier.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
) {
Text("Guardar cambios", fontWeight = FontWeight.Black)
}
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
                DarkChoiceChip(
                    label = "Copiar hacia",
                    selected = mode == SessionClonerMode.CLONE_TO_DAYS,
                    onClick = { mode = SessionClonerMode.CLONE_TO_DAYS },
                )
                DarkChoiceChip(
                    label = "Traer desde",
                    selected = mode == SessionClonerMode.IMPORT_FROM_DAY,
                    onClick = { mode = SessionClonerMode.IMPORT_FROM_DAY },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionCloneApplyMode.entries.forEach { candidate ->
                    DarkChoiceChip(
                        label = if (candidate == SessionCloneApplyMode.APPEND) "Agregar" else "Reemplazar",
                        selected = applyMode == candidate,
                        onClick = { applyModeName = candidate.name },
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
        shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else DarkEditorSurfaceSoft,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkEditorSurfaceSoft)
                        .clickable { clonePartial = !clonePartial }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Clonación parcial (ejercicios seleccionados)")
                    DarkChoiceChip(
                        label = if (clonePartial) "ON" else "OFF",
                        selected = clonePartial,
                        onClick = { clonePartial = !clonePartial },
                    )
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
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(DarkEditorChip))
                            }
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
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = DarkEditorChipSelected,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
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
                            else DarkEditorSurfaceSoft,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkEditorSurfaceSoft)
                        .clickable { importPartial = !importPartial }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Importación parcial (ejercicios seleccionados)")
                    DarkChoiceChip(
                        label = if (importPartial) "ON" else "OFF",
                        selected = importPartial,
                        onClick = { importPartial = !importPartial },
                    )
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
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(DarkEditorChip))
                            }
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
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = DarkEditorChipSelected,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = DarkEditorChip,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
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

private enum class AssistantMuscleChartMode {
    VOLUME,
    AUGE_DRAIN,
}

@Composable
private fun AssistantSheet(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (String) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
) {
    val report = uiState.assistantReport
    val summary = uiState.augeSummary
    val accentColor = augeStatusColor(summary.status, summary.hasCriticalAlerts)
    var ringsExpanded by rememberSaveable { mutableStateOf(false) }
    var volumeExpanded by rememberSaveable { mutableStateOf(true) }
    var countIndirectVolume by rememberSaveable { mutableStateOf(false) }
    var adjustVolumeByIntensity by rememberSaveable { mutableStateOf(false) }
    var expandedMuscleName by remember { mutableStateOf<String?>(null) }
    var muscleChartMode by rememberSaveable { mutableStateOf(AssistantMuscleChartMode.AUGE_DRAIN) }
    var suggestionsExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Asistente", "Plantillas")

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Asistente de sesión", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(DarkEditorChip)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                DarkChoiceChip(
                    label = title.uppercase(),
                    selected = selectedTab == index,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = index },
                )
            }
        }

        if (selectedTab == 1) {
            AssistantTemplatesTab(
                templates = templates,
                searchQuery = uiState.templateSearchQuery,
                applyDecision = uiState.templateApplyDecision,
                onSearchChange = onTemplateSearchChange,
                onSelectTemplate = onSelectTemplate,
                onConfirmApplyTemplate = onConfirmApplyTemplate,
                onCancelApply = onCancelTemplateApply,
            )
            return@Column
        }

        val energySummary = summary.sessionEnergy
        // ─── Feature 1: Tarjeta de desglose de tiempos ───────────────────────────
        val timeBreakdown = uiState.sessionTimeBreakdown
        if (timeBreakdown != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Tiempo Estimado de Sesión",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    // Desglose de 3 líneas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Preparación",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${timeBreakdown.setupMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Ejecución",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${timeBreakdown.executionMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Descansos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${timeBreakdown.restMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    // Total grande
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Duración estimada: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${timeBreakdown.totalMinutes} min",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

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

        if (report == null) {
            Text(
                "Calculando análisis...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        // Muscle chart section (volume or AUGE drain)
        val isDrainMode = muscleChartMode == AssistantMuscleChartMode.AUGE_DRAIN
        val sortedVolumeEntries = remember(uiState.session, countIndirectVolume, adjustVolumeByIntensity) {
            val session = uiState.session ?: return@remember emptyList<Map.Entry<String, Double>>()
            val exerciseIndex = EXERCISE_DATABASE.associateBy { it.id.lowercase() }
            val volumeMap = mutableMapOf<String, Double>()
            session.allExercises().forEach { exercise ->
                val effectiveSets = countDisplaySets(exercise.sets, adjustVolumeByIntensity)
                if (effectiveSets <= 0.0) return@forEach
                val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: return@forEach
                
                val contributions = buildDisplayContributions(dbInfo.involvedMuscles, countIndirectVolume)
                contributions.forEach { (canonical, multiplier) ->
                    volumeMap[canonical] = (volumeMap[canonical] ?: 0.0) + effectiveSets * multiplier
                }
            }
            volumeMap.entries
                .filter { it.value > 0.0 }
                .sortedByDescending { it.value }
        }
        val sortedDrainEntries = remember(summary.muscleDrainProjection) {
            summary.muscleDrainProjection
                .entries
                .filter { it.value > 0 }
                .map { it.key to it.value.toDouble() }
                .sortedByDescending { it.second }
        }
        val chartEntries: List<Pair<String, Double>> = if (isDrainMode) sortedDrainEntries else sortedVolumeEntries.map { it.key to it.value }

        if (chartEntries.isNotEmpty()) {
            val maxChartValue = chartEntries.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isDrainMode) "Drenaje por músculo" else "Series por músculos",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                checked = isDrainMode,
                                onCheckedChange = { enabled ->
                                    muscleChartMode = if (enabled) AssistantMuscleChartMode.AUGE_DRAIN else AssistantMuscleChartMode.VOLUME
                                },
                                modifier = Modifier.scale(0.82f),
                            )
                            IconButton(onClick = { volumeExpanded = !volumeExpanded }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (volumeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                    if (volumeExpanded) {
                        if (!isDrainMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Indirecto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = countIndirectVolume,
                                        onCheckedChange = { countIndirectVolume = it },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ajustar RPE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = adjustVolumeByIntensity,
                                        onCheckedChange = { adjustVolumeByIntensity = it },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            chartEntries.forEach { (muscle, value) ->
                                val threshold = if (!isDrainMode) report?.umbralesPorMusculo?.get(muscle) else null
                                val mev = threshold?.mev
                                val mav = threshold?.mav
                                val mrv = threshold?.mrv
                                val indicatorColor = if (isDrainMode) {
                                    when {
                                        value >= 70.0 -> Color(0xFFEF4444)
                                        value >= 40.0 -> Color(0xFFF59E0B)
                                        else -> Color(0xFF22C55E)
                                    }
                                } else {
                                    when {
                                        mrv != null && value > mrv -> Color(0xFFEF4444)
                                        mav != null && value > mav -> Color(0xFFF59E0B)
                                        mev != null && value >= mev -> Color(0xFF22C55E)
                                        else -> accentColor
                                    }
                                }
                                val valueText = if (isDrainMode) {
                                    "${value.roundToInt()}%"
                                } else {
                                    "${if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)} sets"
                                }
                                val isExpanded = expandedMuscleName == muscle
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (!isDrainMode && (muscle == "Deltoides" || muscle == "Glúteos")) {
                                                expandedMuscleName = if (isExpanded) null else muscle
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(muscle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            valueText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = indicatorColor,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (if (isDrainMode) value / 100.0 else value / maxChartValue).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = indicatorColor,
                                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                                    )
                                    if (isExpanded && !isDrainMode && uiState.session != null) {
                                        SessionSubMuscleBreakdownList(
                                            muscleName = muscle,
                                            session = uiState.session,
                                            countIndirect = countIndirectVolume,
                                            adjustByIntensity = adjustVolumeByIntensity
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (report?.ajustes?.isNotEmpty() == true) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { suggestionsExpanded = !suggestionsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Ajustes sugeridos",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Icon(
                            imageVector = if (suggestionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                    if (suggestionsExpanded) {
                        report.ajustes.forEach { suggestion ->
                            AssistantSuggestionCard(suggestion, onApplyAssistantSuggestion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantTemplatesTab(
    templates: List<SessionTemplate>,
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Buscar plantilla...") },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    Spacer(Modifier.height(10.dp))
    TemplateCatalogBrowser(
        templates = templates,
        searchQuery = searchQuery,
        onSelectTemplate = onSelectTemplate,
        exerciseIndex = remember { EXERCISE_DATABASE.associateBy { it.id.lowercase() } }
    )
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

private fun Exercise.matchesCompetitionMovement(competitionMovementIds: Set<String>): Boolean {
    if (isCompetitionLift) return true
    return listOfNotNull(
        resolvedCanonicalExerciseId(),
        exerciseDbId,
        exerciseId,
        canonicalExerciseId,
    ).any { it in competitionMovementIds }
}


@Composable
private fun CompetitionSessionEditor(
    session: Session,
    onUpdateSession: ((Session) -> Session) -> Unit,
    onOpenConfig: () -> Unit,
    onAddCompetitionMovement: () -> Unit,
) {
    val details = session.competitionDetails
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Sesión de competición", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Solo movimientos competitivos. Sin grupos, biseries, descansos ni series planificadas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenConfig,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Configurar", maxLines = 1)
                }
            }

            Text(
                "Formato de competición",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val selectedMode = session.competitionRecordMode ?: CompetitionRecordMode.HYBRID
                FilterChip(
                    selected = selectedMode == CompetitionRecordMode.TECHNICAL,
                    onClick = {
                        onUpdateSession { current -> current.copy(competitionRecordMode = CompetitionRecordMode.TECHNICAL) }
                    },
                    label = { Text("Técnica") },
                )
                FilterChip(
                    selected = selectedMode == CompetitionRecordMode.JOURNAL,
                    onClick = {
                        onUpdateSession { current -> current.copy(competitionRecordMode = CompetitionRecordMode.JOURNAL) }
                    },
                    label = { Text("Simple") },
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                details?.competitionDate?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Fecha", it) }
                details?.startTime?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Inicio", it) }
                details?.location?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Lugar", it) }
                details?.federation?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Fed.", it) }
                details?.category?.takeIf { it.isNotBlank() }?.let { CompetitionInfoChip("Categoría", it) }
            }

            competitionReminderSummary(details).takeIf { it.isNotBlank() }?.let { summary ->
                Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            details?.strategyNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (session.exercises.isEmpty()) {
                OutlinedButton(onClick = onAddCompetitionMovement, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agregar movimiento de competición")
                }
            } else {
                Text("Movimientos de competición", fontWeight = FontWeight.Bold)
                session.exercises.forEach { movement ->
                    CompetitionMovementCard(
                        movement = movement,
                        onUpdateMovement = { updater ->
                            onUpdateSession { current ->
                                current.copy(exercises = current.exercises.map { if (it.id == movement.id) updater(it) else it })
                            }
                        },
                        onRemove = {
                            onUpdateSession { current ->
                                current.copy(exercises = current.exercises.filterNot { it.id == movement.id })
                            }
                        },
                    )
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

@Composable
private fun CompetitionInfoChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        label = { Text("$label: $value", style = MaterialTheme.typography.labelSmall) },
        shape = RoundedCornerShape(999.dp),
    )
}

@Composable
private fun CompetitionMovementCard(
    movement: Exercise,
    onUpdateMovement: ((Exercise) -> Exercise) -> Unit,
    onRemove: () -> Unit,
) {
    val pr = movement.prFor1RM
    var directRmInput by rememberSaveable(movement.id, movement.reference1RM) {
        mutableStateOf(formatEditableNumber(movement.reference1RM))
    }
    var prWeightInput by rememberSaveable(movement.id, pr?.weight) {
        mutableStateOf(formatEditableNumber(pr?.weight))
    }
    var prRepsInput by rememberSaveable(movement.id, pr?.reps) {
        mutableStateOf(pr?.reps?.takeIf { it > 0 }?.toString().orEmpty())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(movement.name.ifBlank { "Movimiento" }, fontWeight = FontWeight.Black)
                    Text("Movimiento de competición", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar movimiento")
                }
            }

            Text(
                "Referencia competitiva opcional. Sirve para comparar este movimiento en sesiones normales.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EditorMiniField(
                label = "Último PR / RM directo",
                value = directRmInput,
                keyboardType = KeyboardType.Decimal,
                stateKey = "comp-direct-rm-${movement.id}",
            ) { input ->
                directRmInput = input
                val parsed = input.safeDoubleOrNull()
                onUpdateMovement { current ->
                    current.copy(
                        reference1RM = parsed,
                        prFor1RM = null,
                        isCompetitionLift = true,
                        sets = emptyList(),
                        warmupSets = emptyList(),
                        restTime = null,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "PR peso",
                    value = prWeightInput,
                    keyboardType = KeyboardType.Decimal,
                    stateKey = "comp-pr-weight-${movement.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    prWeightInput = input
                    val weight = input.safeDoubleOrNull()
                    val reps = prRepsInput.safeIntOrNull()?.coerceAtLeast(1)
                    onUpdateMovement { current ->
                        if (weight != null && reps != null) {
                            current.copy(
                                prFor1RM = PrReference(weight, reps),
                                reference1RM = calculateHybrid1RM(weight, reps),
                                isCompetitionLift = true,
                                sets = emptyList(),
                                warmupSets = emptyList(),
                                restTime = null,
                            )
                        } else {
                            current.copy(prFor1RM = null, isCompetitionLift = true, sets = emptyList(), warmupSets = emptyList(), restTime = null)
                        }
                    }
                }
                EditorMiniField(
                    label = "Reps",
                    value = prRepsInput,
                    keyboardType = KeyboardType.Number,
                    stateKey = "comp-pr-reps-${movement.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    prRepsInput = input
                    val weight = prWeightInput.safeDoubleOrNull()
                    val reps = input.safeIntOrNull()?.coerceAtLeast(1)
                    onUpdateMovement { current ->
                        if (weight != null && reps != null) {
                            current.copy(
                                prFor1RM = PrReference(weight, reps),
                                reference1RM = calculateHybrid1RM(weight, reps),
                                isCompetitionLift = true,
                                sets = emptyList(),
                                warmupSets = emptyList(),
                                restTime = null,
                            )
                        } else {
                            current.copy(prFor1RM = null, isCompetitionLift = true, sets = emptyList(), warmupSets = emptyList(), restTime = null)
                        }
                    }
                }
            }

            movement.reference1RM?.takeIf { it > 0.0 }?.let { rm ->
                Text(
                    "Referencia actual: ${formatEditableNumber(rm)} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompetitionConfigSheet(
    session: Session,
    onDismiss: () -> Unit,
    onUpdateSession: ((Session) -> Session) -> Unit,
) {
    val details = session.competitionDetails ?: CompetitionDetails()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Configurar competición", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Datos del evento, pesaje y recordatorios. Mantenerlo separado evita que el editor de movimientos se sature.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Fecha (YYYY-MM-DD)",
                    value = details.competitionDate.orEmpty(),
                    stateKey = "comp-sheet-date-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(competitionDate = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "Hora inicio",
                    value = details.startTime.orEmpty(),
                    stateKey = "comp-sheet-time-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(startTime = input.ifBlank { null }) } }
                }
            }
            EditorMiniField("Ubicación", details.location.orEmpty(), "comp-sheet-location-${session.id}") { input ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(location = input.ifBlank { null }) } }
            }
            EditorMiniField("Federación", details.federation.orEmpty(), "comp-sheet-fed-${session.id}") { input ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(federation = input.ifBlank { null }) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Categoría",
                    value = details.category.orEmpty(),
                    stateKey = "comp-sheet-category-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(category = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "División",
                    value = details.division.orEmpty(),
                    stateKey = "comp-sheet-division-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(division = input.ifBlank { null }) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Equipamiento",
                    value = details.equipment.orEmpty(),
                    stateKey = "comp-sheet-equipment-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(equipment = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "Peso objetivo",
                    value = formatEditableNumber(details.targetBodyweightKg),
                    keyboardType = KeyboardType.Decimal,
                    stateKey = "comp-sheet-target-bw-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(targetBodyweightKg = input.safeDoubleOrNull()) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorMiniField(
                    label = "Pesaje fecha",
                    value = details.weighInDate.orEmpty(),
                    stateKey = "comp-sheet-weigh-date-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(weighInDate = input.ifBlank { null }) } }
                }
                EditorMiniField(
                    label = "Pesaje hora",
                    value = details.weighInTime.orEmpty(),
                    stateKey = "comp-sheet-weigh-time-${session.id}",
                    modifier = Modifier.weight(1f),
                ) { input ->
                    onUpdateSession { current -> current.withCompetitionDetails { copy(weighInTime = input.ifBlank { null }) } }
                }
            }
            Text("Recordatorios", fontWeight = FontWeight.Bold)
            CompetitionConfigCheckRow("Una semana antes", details.reminderOneWeekEnabled) { checked ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(reminderOneWeekEnabled = checked) } }
            }
            CompetitionConfigCheckRow("48 horas antes", details.reminder48hEnabled) { checked ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(reminder48hEnabled = checked) } }
            }
            CompetitionConfigCheckRow("Al inicio del evento", details.reminderStartEnabled) { checked ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(reminderStartEnabled = checked) } }
            }
            EditorMiniField(
                label = "Estrategia / notas",
                value = details.strategyNotes.orEmpty(),
                stateKey = "comp-sheet-strategy-${session.id}",
            ) { input ->
                onUpdateSession { current -> current.withCompetitionDetails { copy(strategyNotes = input.ifBlank { null }) } }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Listo")
            }
        }
    }
}

@Composable
private fun CompetitionConfigCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Session.withCompetitionDetails(update: CompetitionDetails.() -> CompetitionDetails): Session =
    copy(competitionDetails = (competitionDetails ?: CompetitionDetails()).update())

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
    if (details.reminderStartEnabled) reminders += "inicio"
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

private val DarkEditorSurface = Color(0xE61B1B20)
private val DarkEditorSurfaceSoft = Color(0xB8232329)
private val DarkEditorChip = Color(0xFF2A2A31)
private val DarkEditorChipSelected = Color(0xFF333A42)

@Composable
private fun SheetHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DarkChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) DarkEditorChipSelected else DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactRestBundleButton(
    primaryLabel: String,
    primarySeconds: Int,
    sideSeconds: Int?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int, Int?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val summary = if (sideSeconds != null) {
        "$primaryLabel ${formatRestSummary(primarySeconds)} · Lados ${formatRestSummary(sideSeconds)}"
    } else {
        "$primaryLabel ${formatRestSummary(primarySeconds)}"
    }
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showPicker = true },
        shape = RoundedCornerShape(999.dp),
        color = DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Timer, contentDescription = "Configurar descansos", tint = Color.White, modifier = Modifier.size(18.dp))
            Text(summary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }

    if (showPicker) {
        RestBundleDialog(
            primaryLabel = primaryLabel,
            initialPrimarySeconds = primarySeconds,
            initialSideSeconds = sideSeconds,
            accentColor = accentColor,
            onDismiss = { showPicker = false },
            onConfirm = { primary, side ->
                onConfirm(primary, side)
                showPicker = false
            },
        )
    }
}

@Composable
private fun RestBundleDialog(
    primaryLabel: String,
    initialPrimarySeconds: Int,
    initialSideSeconds: Int?,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int?) -> Unit,
) {
    var primaryMinutes by rememberSaveable(initialPrimarySeconds) { mutableStateOf((initialPrimarySeconds / 60).coerceIn(0, 59)) }
    var primarySeconds by rememberSaveable(initialPrimarySeconds) { mutableStateOf((initialPrimarySeconds % 60).coerceIn(0, 59)) }
    var sideMinutes by rememberSaveable(initialSideSeconds) { mutableStateOf(((initialSideSeconds ?: 0) / 60).coerceIn(0, 59)) }
    var sideSeconds by rememberSaveable(initialSideSeconds) { mutableStateOf(((initialSideSeconds ?: 0) % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descansos", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SupersetRestWheelRow(primaryLabel, primaryMinutes, primarySeconds, accentColor, { primaryMinutes = it }, { primarySeconds = it })
                if (initialSideSeconds != null) {
                    SupersetRestWheelRow("Entre lados", sideMinutes, sideSeconds, accentColor, { sideMinutes = it }, { sideSeconds = it })
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        primaryMinutes * 60 + primarySeconds,
                        initialSideSeconds?.let { sideMinutes * 60 + sideSeconds },
                    )
                },
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CompactRestPickerButton(
    label: String,
    totalSeconds: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showPicker = true },
        color = DarkEditorChip,
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
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$label ${formatRestSummary(totalSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
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
internal fun CompactModeSelector(
    currentMode: TrainingMode,
    accentColor: Color,
    onModeSelected: (TrainingMode) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box {
        Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { showMenu = true },
            color = DarkEditorChip,
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
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 90.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            listOf(
                TrainingMode.REPS to "Reps",
                TrainingMode.AMRAP to "AMRAP",
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
@OptIn(ExperimentalFoundationApi::class)
private fun CompactGoalTrackingButton(
    isActive: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onOpenSheet: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .combinedClickable(
                onClick = { if (onOpenSheet != null) onOpenSheet() else onToggle() },
                onLongClick = { onOpenSheet?.invoke() },
            ),
        color = if (isActive) DarkEditorChipSelected else DarkEditorChip,
        border = if (isActive) BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Seguimiento de metas",
                tint = if (isActive) Color(0xFFFFB300) else Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Meta",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFFFFB300) else Color.White,
            )
        }
    }
}

@Composable
internal fun UnilateralModeSelector(
    mode: UnilateralMode,
    accentColor: Color,
    onToggleUnilateral: () -> Unit,
) {
    val isUnilateral = mode != UnilateralMode.BILATERAL
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { showMenu = true },
            color = if (isUnilateral) DarkEditorChipSelected else DarkEditorChip,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = if (isUnilateral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (isUnilateral) "Unilateral" else "Bilateral",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnilateral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = if (isUnilateral) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("Bilateral") }, onClick = {
                showMenu = false
                if (isUnilateral) onToggleUnilateral()
            })
            DropdownMenuItem(text = { Text("Unilateral") }, onClick = {
                showMenu = false
                if (!isUnilateral) onToggleUnilateral()
            })
        }
    }
}

@Composable
internal fun SideOrderChip(
    sideOrder: UnilateralSideOrder,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    val label = when (sideOrder) {
        UnilateralSideOrder.LEFT_RIGHT -> "Orden L/R"
        UnilateralSideOrder.RIGHT_LEFT -> "Orden R/L"
    }
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onToggle() },
        color = DarkEditorChip,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = "Cambiar orden unilateral",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

internal fun Exercise.toggledBilateralUnilateral(): Exercise {
    val nextUnilateral = !isEffectivelyUnilateral()
    return if (nextUnilateral) {
        copy(
            isUnilateral = true,
            unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            sets = sets.map { set ->
                val target = UnilateralTarget(
                    weight = set.weight,
                    targetReps = set.targetReps,
                    targetDuration = set.targetDuration,
                    targetValue = set.plannedTargetV2,
                    targetRPE = set.targetRPE,
                    targetRIR = set.targetRIR,
                    intensityMode = set.intensityMode,
                )
                set.copy(
                    leftTarget = target,
                    rightTarget = target,
                )
            }
        )
    } else {
        copy(
            isUnilateral = false,
            unilateralMode = UnilateralMode.BILATERAL,
            restBetweenSidesSeconds = null,
            sets = sets.map { set ->
                val source = set.leftTarget ?: set.rightTarget
                if (source != null) {
                    set.copy(
                        weight = source.weight,
                        targetReps = source.targetReps,
                        targetDuration = source.targetDuration,
                        plannedTargetV2 = source.targetValue,
                        targetRPE = source.targetRPE,
                        targetRIR = source.targetRIR,
                        intensityMode = source.intensityMode,
                        restBetweenSides = null,
                        leftTarget = null,
                        rightTarget = null,
                    )
                } else {
                    set.copy(
                        restBetweenSides = null,
                        leftTarget = null,
                        rightTarget = null,
                    )
                }
            },
        )
    }
}

@Composable
internal fun ExerciseSetsCarousel(
    exercise: Exercise,
    reference1RM: Double?,
    trainingMode: TrainingMode,
    customUnit: String?,
    predictedMetrics: Map<String, Double?>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onAddSet: (String?) -> Unit,
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
                onClick = { onAddSet(null) },
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
        val showUnilateralDualCards = exercise.isEffectivelyUnilateral()
        val orderedSides = when (exercise.unilateralSideOrder) {
            UnilateralSideOrder.LEFT_RIGHT -> listOf("L", "R")
            UnilateralSideOrder.RIGHT_LEFT -> listOf("R", "L")
        }
        // Carousel using LazyRow
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showUnilateralDualCards) 392.dp else 214.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            .width(292.dp)
                            .fillMaxHeight(),
                    ) {
                        if (showUnilateralDualCards) {
                            val showLeftCard = set.leftTarget != null
                            val showRightCard = set.rightTarget != null
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                orderedSides.forEach { side ->
                                    val isLeft = side == "L"
                                    val showCard = if (isLeft) showLeftCard else showRightCard
                                    Box(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (showCard) {
                                            val isFirstVisible = orderedSides.takeWhile { it != side }.none { prior ->
                                                if (prior == "L") showLeftCard else showRightCard
                                            }
                                            InlineSetRow(
                                                set = set,
                                                index = index,
                                                reference1RM = reference1RM,
                                                predictedWeight = predictedWeight,
                                                estimatedMetric = estimatedMetric,
                                                trainingMode = trainingMode,
                                                customUnit = customUnit,
                                                accentColor = if (isLeft) Color(0xFF2196F3) else Color(0xFFFF5252),
                                                canMoveUp = isFirstVisible && index > 0,
                                                canMoveDown = isFirstVisible && index < exercise.sets.size - 1,
                                                isUnilateral = true,
                                                fixedUnilateralSide = side,
                                                showSetActions = isFirstVisible,
                                                unilateralIntensityMode = exercise.unilateralIntensityMode,
                                                onUpdate = { updater -> onUpdateSet(set.id, updater) },
                                                onRemove = { onRemoveSet(set.id) },
                                                onMoveUp = { onMoveSet(set.id, -1) },
                                                onMoveDown = { onMoveSet(set.id, 1) },
                                            )
                                        } else {
                                            UnilateralAddGhostCard(
                                                side = side,
                                                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(),
                                                onClick = {
                                                    onUpdateSet(set.id) { s ->
                                                        val default = UnilateralTarget(
                                                            weight = s.weight,
                                                            targetReps = s.targetReps,
                                                            targetDuration = s.targetDuration,
                                                            targetValue = s.plannedTargetV2,
                                                            targetRPE = s.targetRPE,
                                                            targetRIR = s.targetRIR,
                                                            intensityMode = s.intensityMode,
                                                        )
                                                        if (side == "L") {
                                                            s.copy(leftTarget = s.leftTarget ?: default)
                                                        } else {
                                                            s.copy(rightTarget = s.rightTarget ?: default)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
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
                                isUnilateral = exercise.isEffectivelyUnilateral(),
                                unilateralIntensityMode = exercise.unilateralIntensityMode,
                                onUpdate = { updater -> onUpdateSet(set.id, updater) },
                                onRemove = { onRemoveSet(set.id) },
                                onMoveUp = { onMoveSet(set.id, -1) },
                                onMoveDown = { onMoveSet(set.id, 1) },
                            )
                        }
                    }
                }
            }
            item("add-set") {
                Box(
                    modifier = Modifier
                        .width(292.dp)
                        .fillMaxHeight()
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AddSetGhostCard(onAddSet = { onAddSet(null) })
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
private fun UnilateralAddGhostCard(
    side: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = DarkEditorSurfaceSoft,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Añadir lado $side", tint = accentColor, modifier = Modifier.size(14.dp))
                Text(
                    "Añadir $side",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                )
            }
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
    val supersetFactor = if (session.allExercises().any { !it.supersetGroupRefOrLegacyId().isNullOrBlank() }) 1.08 else 1.0
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
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onAddSet() },
        color = DarkEditorSurfaceSoft,
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

@Composable
private fun CompactTemplateCard(
    template: SessionTemplate,
    onApply: () -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
) {
    var expanded by rememberSaveable(template.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.emoji.ifBlank { "💪" },
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        template.estimatedDurationMinutes?.let {
                            Text(
                                text = "~${it} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Text(
                            text = "${template.session.allExercises().size} ej.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                        val diffText = when (template.difficulty) {
                            com.example.kpkn.data.splits.Difficulty.PRINCIPIANTE -> "Principiante"
                            com.example.kpkn.data.splits.Difficulty.INTERMEDIO -> "Intermedio"
                            com.example.kpkn.data.splits.Difficulty.AVANZADO -> "Avanzado"
                        }
                        val diffColor = when (template.difficulty) {
                            com.example.kpkn.data.splits.Difficulty.PRINCIPIANTE -> Color(0xFF66BB6A)
                            com.example.kpkn.data.splits.Difficulty.INTERMEDIO -> Color(0xFFFFA726)
                            com.example.kpkn.data.splits.Difficulty.AVANZADO -> Color(0xFFEF5350)
                        }
                        Text(
                            text = diffText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = diffColor
                        )
                    }
                }
                
                Spacer(Modifier.width(6.dp))

                FilledTonalButton(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Aplicar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                TemplateExpandedDetails(template, exerciseIndex)
            }
        }
    }
}

@Composable
private fun TemplateExpandedDetails(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (template.description.isNotBlank()) {
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Ejercicios incluidos:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            template.session.allExercises().forEachIndexed { idx, ex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${idx + 1}. ${ex.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val setsCount = ex.sets.size
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$setsCount ${if (setsCount == 1) "serie" else "series"}", fontSize = 10.sp) },
                        modifier = Modifier.height(22.dp)
                    )
                }
            }
        }

        val estimatedVol = remember(template, exerciseIndex) {
            SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(template.session, exerciseIndex)
        }

        val drain = remember(template, exerciseIndex) {
            SessionTemplateCatalogPolicy.evaluateTemplateRings(template, exerciseIndex)
        }

        val warnings = remember(template, exerciseIndex) {
            val list = mutableListOf<String>()
            val isPl = SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)
            val maxCns = if (isPl) 45 else 35
            val maxMuscular = if (isPl) 50 else 45
            val maxSpinal = if (isPl) 40 else 30

            if (drain.cns > maxCns) list += "SNC elevada (${drain.cns}% > $maxCns%)"
            if (drain.muscular > maxMuscular) list += "Muscular elevada (${drain.muscular}% > $maxMuscular%)"
            if (drain.spinal > maxSpinal) list += "Axial/espinal elevada (${drain.spinal}% > $maxSpinal%)"
            list
        }

        if (estimatedVol.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Volumen estimado por músculo:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    estimatedVol.entries.sortedByDescending { it.value }.forEach { (muscle, sets) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = "$muscle: ${formatOneDecimal(sets)} series",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Fatiga SNC: ${drain.cns}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Muscular: ${drain.muscular}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Axial: ${drain.spinal}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                warnings.forEach { warning ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Advertencia",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCatalogBrowser(
    templates: List<SessionTemplate>,
    searchQuery: String,
    onSelectTemplate: (SessionTemplate) -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
) {
    val splits = remember { SPLIT_TEMPLATES.filterNot { it.id == "custom" } }

    val splitsWithGroups = remember(templates, splits, exerciseIndex) {
        splits.map { split ->
            split to SessionTemplateCatalogPolicy.templatesForSplit(split, templates, exerciseIndex)
        }.filter { it.second.any { g -> g.templates.isNotEmpty() } }
    }

    val independentGroups = remember(templates) {
        SessionTemplateCatalogPolicy.independentTemplateGroups(templates)
    }

    val userGroup = remember(templates) {
        SessionTemplateCatalogPolicy.userTemplateGroup(templates)
    }

    val isSearching = searchQuery.isNotBlank()
    val filteredTemplates = remember(templates, searchQuery) {
        if (searchQuery.isBlank()) templates
        else templates.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.muscleGroupsSummary.contains(searchQuery, ignoreCase = true) ||
            it.shortDescription.contains(searchQuery, ignoreCase = true)
        }
    }

    if (isSearching) {
        if (filteredTemplates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin resultados para \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Resultados de búsqueda:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                filteredTemplates.forEach { template ->
                    CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (userGroup.templates.isNotEmpty()) {
                var userExpanded by rememberSaveable("user-templates") { mutableStateOf(true) }

                Text(
                    text = "Mis plantillas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { userExpanded = !userExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Guardadas por ti",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${userGroup.templates.size} ${if (userGroup.templates.size == 1) "plantilla" else "plantillas"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (userExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = userExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                userGroup.templates.forEach { template ->
                                    CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                                }
                            }
                        }
                    }
                }
            }

            if (splitsWithGroups.isNotEmpty()) {
                Text(
                    text = "Organizado por Split",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                splitsWithGroups.forEach { (split, dayGroups) ->
                    var splitExpanded by rememberSaveable("split-${split.id}") { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { splitExpanded = !splitExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = split.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (split.description.isNotBlank()) {
                                        Text(
                                            text = split.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = if (splitExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = splitExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    dayGroups.forEach { group ->
                                        if (group.templates.isNotEmpty()) {
                                            var dayExpanded by rememberSaveable("split-${split.id}-day-${group.dayIndex}") { mutableStateOf(true) }

                                            Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { dayExpanded = !dayExpanded }
                                                        .padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                        modifier = Modifier.height(24.dp)
                                                    ) {
                                                        Text(
                                                            text = group.dayLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "${group.templates.size} ${if (group.templates.size == 1) "opción" else "opciones"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(Modifier.weight(1f))
                                                    Icon(
                                                        imageVector = if (dayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                AnimatedVisibility(visible = dayExpanded) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        group.templates.forEach { template ->
                                                            CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (independentGroups.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Plantillas por Enfoque",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                independentGroups.forEach { group ->
                    var focusExpanded by rememberSaveable("focus-${group.category.name}") { mutableStateOf(false) }

                    val categoryLabel = when (group.category) {
                        SessionTemplateFocusCategory.PIERNAS -> "Piernas"
                        SessionTemplateFocusCategory.BRAZOS -> "Brazos"
                        SessionTemplateFocusCategory.GLUTEOS -> "Glúteos"
                        SessionTemplateFocusCategory.PECHO -> "Pecho"
                        SessionTemplateFocusCategory.ESPALDA -> "Espalda"
                        SessionTemplateFocusCategory.HOMBROS -> "Hombros"
                        SessionTemplateFocusCategory.FULL_BODY -> "Full Body"
                        SessionTemplateFocusCategory.POWERLIFTING -> "Powerlifting"
                        SessionTemplateFocusCategory.MINIMALISTA -> "Minimalista"
                        SessionTemplateFocusCategory.RECUPERACION -> "Recuperación"
                    }

                    val categoryEmoji = when (group.category) {
                        SessionTemplateFocusCategory.PIERNAS -> "🦵"
                        SessionTemplateFocusCategory.BRAZOS -> "💪"
                        SessionTemplateFocusCategory.GLUTEOS -> "🍑"
                        SessionTemplateFocusCategory.PECHO -> "🛡️"
                        SessionTemplateFocusCategory.ESPALDA -> "🦅"
                        SessionTemplateFocusCategory.HOMBROS -> "✈️"
                        SessionTemplateFocusCategory.FULL_BODY -> "🌟"
                        SessionTemplateFocusCategory.POWERLIFTING -> "🏋️"
                        SessionTemplateFocusCategory.MINIMALISTA -> "⚡"
                        SessionTemplateFocusCategory.RECUPERACION -> "🩹"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { focusExpanded = !focusExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = categoryEmoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = categoryLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${group.templates.size} ${if (group.templates.size == 1) "plantilla" else "plantillas"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (focusExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = focusExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    group.templates.forEach { template ->
                                        CompactTemplateCard(template, onApply = { onSelectTemplate(template) }, exerciseIndex)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun resolveSpecificSubMuscle(muscle: String, emphasis: String?): String {
    val lower = muscle.lowercase().replace("-", " ").replace("_", " ").trim()
    if (lower.contains("deltoides") || lower.contains("hombro")) {
        return when {
            lower.contains("posterior") || lower.contains("trasero") -> "Deltoides Posterior"
            lower.contains("lateral") || lower.contains("medio") -> "Deltoides Lateral"
            else -> "Deltoides Anterior"
        }
    }
    if (lower.contains("glúteo") || lower.contains("gluteo") || lower.contains("tensor de la fascia lata") || lower.contains("tensor fascia")) {
        return when {
            lower.contains("medio") || lower.contains("medius") || lower.contains("mínimo") || lower.contains("minimus") || lower.contains("tensor") -> "Glúteo Medio"
            else -> "Glúteo Mayor"
        }
    }
    return muscle
}

private fun com.example.kpkn.data.models.ExerciseSet.effectiveTargetRpe(): Double {
    if (isFailure || intensityMode == com.example.kpkn.data.models.IntensityMode.FAILURE) return 10.0
    targetRPE?.let { return it.coerceIn(1.0, 10.0) }
    targetRIR?.let { return (10 - it).toDouble().coerceIn(1.0, 10.0) }
    return 8.0
}

private fun buildDisplayContributions(
    involvedMuscles: List<com.example.kpkn.data.models.InvolvedMuscle>,
    countIndirect: Boolean
): Map<String, Double> {
    val grouped = linkedMapOf<String, Double>()
    involvedMuscles.forEach { involvement ->
        val isMatch = if (countIndirect) {
            involvement.role == com.example.kpkn.data.models.MuscleRole.SECONDARY || involvement.role == com.example.kpkn.data.models.MuscleRole.STABILIZER
        } else {
            involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
        }
        if (isMatch) {
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
            val current = grouped[canonical] ?: 0.0
            if (contribution > current) {
                grouped[canonical] = contribution
            }
        }
    }
    return grouped.filterValues { it > 0.0 }
}

private fun countDisplaySets(exerciseSets: List<com.example.kpkn.data.models.ExerciseSet>, adjustByIntensity: Boolean): Double {
    var total = 0.0
    val activeSets = exerciseSets.filterNot { it.isIneffective }
    val counted = activeSets.filter { set ->
        ((set.completedReps ?: set.targetReps ?: 0) > 0 || (set.weight ?: 0.0) > 0.0)
    }
    val targetList = if (counted.isEmpty()) activeSets else counted
    targetList.forEach { set ->
        val mult = if (adjustByIntensity) {
            com.example.kpkn.domain.auge.AugeClassifiers.getEffectiveVolumeMultiplier(set.effectiveTargetRpe())
        } else {
            1.0
        }
        total += mult
    }
    return total
}

private fun calculateSubMuscleBreakdown(
    canonicalMuscle: String,
    session: Session,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    countIndirect: Boolean,
    adjustByIntensity: Boolean
): List<Pair<String, List<Pair<String, Double>>>> {
    val targetSubMuscles = when (canonicalMuscle) {
        "Deltoides" -> listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
        "Glúteos" -> listOf("Glúteo Mayor", "Glúteo Medio")
        else -> return emptyList()
    }
    
    val subMuscleVolumes = targetSubMuscles.associateWith { mutableMapOf<String, Double>() }.toMutableMap()
    
    session.allExercises().forEach { exercise ->
        val effectiveSets = countDisplaySets(exercise.sets, adjustByIntensity)
        if (effectiveSets <= 0.0) return@forEach
        val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: return@forEach
        
        dbInfo.involvedMuscles.forEach { involvement ->
            val isMatch = if (countIndirect) {
                involvement.role == com.example.kpkn.data.models.MuscleRole.SECONDARY || involvement.role == com.example.kpkn.data.models.MuscleRole.STABILIZER
            } else {
                involvement.role == com.example.kpkn.data.models.MuscleRole.PRIMARY
            }
            if (isMatch) {
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
                if (canonical == canonicalMuscle) {
                    val subMuscle = resolveSpecificSubMuscle(involvement.muscle, involvement.emphasis)
                    val map = subMuscleVolumes[subMuscle]
                    if (map != null) {
                        val contribution = com.example.kpkn.data.models.resolveMuscleVolumeContribution(involvement)
                        val current = map[exercise.name] ?: 0.0
                        if (effectiveSets * contribution > current) {
                            map[exercise.name] = effectiveSets * contribution
                        }
                    }
                }
            }
        }
    }
    
    return targetSubMuscles.map { subName ->
        val exerciseMap = subMuscleVolumes[subName] ?: emptyMap()
        subName to exerciseMap.entries
            .map { it.key to it.value }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
    }
}

private fun getMuscleEmphasisEducationalText(muscle: String, headName: String?): String {
    val normalizedMuscle = muscle.trim().lowercase()
    val normalizedHead = headName?.trim()?.lowercase() ?: ""

    return when (normalizedMuscle) {
        "pectorales" -> {
            when {
                normalizedHead.contains("clavicular") || normalizedHead.contains("superior") ->
                    "La porción superior (clavicular) se enfatiza mediante la flexión del hombro (ej. press inclinado o cruces en polea baja), donde la trayectoria ascendente alinea la línea de tracción con la dirección de sus fibras musculares."
                normalizedHead.contains("esternal") || normalizedHead.contains("inferior") ->
                    "La porción inferior (esternal/costal) se enfatiza mediante la aducción horizontal declinada (ej. fondos en paralelas o cruces en polea alta), alineando el plano de empuje con las fibras inferiores."
                normalizedHead.contains("plano") || normalizedHead.contains("medio") ->
                    "La porción media se enfatiza con la aducción horizontal pura perpendicular al torso (ej. press de banca plano o aperturas planas)."
                else ->
                    "El pectoral se divide en porciones clavicular, esternal y costal. Modificar la inclinación del press o la trayectoria de los cruces de polea cambia la alineación de las fibras activadas por los brazos de momento de la carga."
            }
        }
        "deltoides" -> {
            when {
                normalizedHead.contains("anterior") ->
                    "El deltoides anterior se enfatiza mediante la flexión del hombro (ej. press militar, press de hombros con mancuernas o elevaciones frontales)."
                normalizedHead.contains("lateral") || normalizedHead.contains("medio") ->
                    "El deltoides lateral se enfatiza mediante la abducción pura del hombro (ej. elevaciones laterales), idealmente realizadas en el plano escapular (30° al frente) para mayor seguridad articular."
                normalizedHead.contains("posterior") ->
                    "El deltoides posterior se enfatiza mediante la abducción horizontal y extensión del hombro (ej. pájaros con mancuernas o cruces invertidos en polea)."
                else ->
                    "El deltoides consta de cabezas anterior, lateral y posterior. Se enfatizan modificando la dirección del plano del movimiento del hombro (flexión, abducción o abducción horizontal)."
            }
        }
        "trapecio" -> {
            when {
                normalizedHead.contains("descendente") || normalizedHead.contains("superior") ->
                    "El trapecio superior se enfatiza mediante la elevación escapular (ej. encogimientos de hombros), donde las fibras tiran hacia arriba, y también contribuye en la abducción del brazo por encima de la cabeza."
                normalizedHead.contains("transversa") || normalizedHead.contains("media") ->
                    "El trapecio medio se enfatiza mediante la retracción escapular (ej. jalones a la cara/face pulls, o remos abiertos juntando las escápulas)."
                normalizedHead.contains("ascendente") || normalizedHead.contains("inferior") ->
                    "El trapecio inferior se enfatiza mediante la depresión escapular (ej. jalones escapulares o elevaciones en Y), donde las fibras tiran de la escápula hacia abajo."
                else ->
                    "El trapecio se divide en superior, medio e inferior. Sus fibras cambian de orientación funcional, requiriendo elevación, retracción o depresión de las escápulas para enfatizar cada zona."
            }
        }
        "cuádriceps" -> {
            when {
                normalizedHead.contains("recto femoral") ->
                    "El recto femoral es biarticular (cruza cadera y rodilla). Se enfatiza cuando la cadera está extendida y la rodilla se flexiona (ej. sentadilla sissy o extensiones con el torso inclinado hacia atrás), aumentando su tensión de estiramiento."
                else ->
                    "Los vastos (lateral, medial, intermedio) son monoarticulares y se activan en conjunto en flexo-extensión de rodilla (ej. prensa o sentadillas). El recto femoral requiere cambios en la extensión de la cadera para modificar su participación relativa."
            }
        }
        "glúteos" -> {
            when {
                normalizedHead.contains("mayor") ->
                    "El glúteo mayor es el extensor primario de cadera. Se enfatiza con cargas donde la máxima tensión coincide con la cadera extendida (ej. hip thrust) o estirada (ej. peso muerto rumano o sentadilla profunda)."
                normalizedHead.contains("medio") || normalizedHead.contains("menor") ->
                    "Los glúteos medio y menor actúan como abductores y rotadores. Se enfatizan mediante la abducción pura de la cadera (ej. abducciones en polea, máquina de abductores o caminatas laterales con banda)."
                else ->
                    "El complejo glúteo incluye el mayor (extensor principal) y el medio/menor (estabilizadores y abductores). Alternar ejercicios de extensión pura con movimientos de abducción cambia el énfasis entre estas porciones."
            }
        }
        "pantorrillas" -> {
            when {
                normalizedHead.contains("gastrocnemio") ->
                    "El gastrocnemio es biarticular. Se enfatiza con la rodilla extendida (ej. elevaciones de talones de pie o en prensa), donde puede estirarse y contraerse en condiciones óptimas."
                normalizedHead.contains("sóleo") ->
                    "El sóleo es monoarticular. Se enfatiza con la rodilla flexionada a 90° (ej. elevaciones de talones sentado), posición que acorta e inactiva en gran parte al gastrocnemio."
                else ->
                    "La pantorrilla se compone de gastrocnemios y sóleo. Flexionar la rodilla altera drásticamente la contribución de los gastrocnemios debido a la insuficiencia activa, dejando la mayor parte del trabajo al sóleo."
            }
        }
        "bíceps" -> {
            when {
                normalizedHead.contains("larga") ->
                    "La cabeza larga (biarticular) se enfatiza al colocar el hombro en extensión (ej. curl en banco inclinado), lo que la sitúa en una posición de mayor preestiramiento."
                normalizedHead.contains("corta") ->
                    "La cabeza corta se enfatiza cuando el hombro está flexionado (ej. curl predicador o curl araña), lo que reduce el preestiramiento de la cabeza larga e incrementa el estímulo relativo en la porción interna."
                normalizedHead.contains("braquial") ->
                    "El braquial y braquiorradial se enfatizan usando agarres neutros o pronos (ej. curl martillo o curl invertido), donde disminuye la ventaja mecánica de las cabezas del bíceps."
                else ->
                    "El flexor del codo incluye la cabeza larga, corta y el músculo braquial. Modificar la posición del hombro respecto al torso o cambiar la orientación del agarre altera la ventaja mecánica de cada porción."
            }
        }
        "tríceps" -> {
            when {
                normalizedHead.contains("larga") ->
                    "La cabeza larga es la única biarticular del tríceps. Se enfatiza mediante la flexión del hombro (brazo elevado sobre la cabeza, ej. copas de tríceps o extensiones tras nuca), colocándola en una posición de estiramiento máximo."
                normalizedHead.contains("lateral") ->
                    "La cabeza lateral se enfatiza con el brazo al costado del cuerpo y agarre prono o neutro (ej. extensiones en polea alta con cuerda o barra V)."
                normalizedHead.contains("medial") ->
                    "La cabeza medial es el caballo de batalla del tríceps, activa en todos los movimientos de extensión, siendo especialmente demandada al final del rango en el bloqueo del codo."
                else ->
                    "El tríceps tiene cabezas lateral, medial y larga. Dado que solo la cabeza larga cruza la articulación del hombro, elevar el brazo por encima de la cabeza es indispensable para estirarla y enfatizarla."
            }
        }
        "antebrazo" -> {
            when {
                normalizedHead.contains("flexores") ->
                    "Los flexores se enfatizan mediante movimientos de flexión de muñeca (palma hacia el antebrazo) bajo resistencia."
                normalizedHead.contains("extensores") ->
                    "Los extensores se enfatizan mediante la extensión de muñeca (dorso de la mano hacia el antebrazo)."
                else ->
                    "El antebrazo se divide principalmente en extensores y flexores. Cambiar la orientación del agarre de la barra (prono o supino) redirige el estímulo y la tensión mecánica a cada grupo."
            }
        }
        else -> ""
    }
}

@Composable
private fun SessionSubMuscleBreakdownList(
    muscleName: String,
    session: Session,
    countIndirect: Boolean,
    adjustByIntensity: Boolean,
) {
    val exerciseIndex = remember { EXERCISE_DATABASE.associateBy { it.id.lowercase() } }
    val breakdown = remember(muscleName, session, countIndirect, adjustByIntensity) {
        calculateSubMuscleBreakdown(muscleName, session, exerciseIndex, countIndirect, adjustByIntensity)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        breakdown.forEach { (subName, exercises) ->
            val totalSubSets = exercises.sumOf { it.second }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = subName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${if (totalSubSets == totalSubSets.toLong().toDouble()) totalSubSets.toLong().toString() else "%.1f".format(totalSubSets)} sets",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (exercises.isEmpty()) {
                    Text(
                        text = "  Sin aportes registrados para esta cabeza.",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                } else {
                    exercises.forEach { (exName, valSets) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• $exName",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (valSets == valSets.toLong().toDouble()) valSets.toLong().toString() else "%.1f".format(valSets)} sets",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
