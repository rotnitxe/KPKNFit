package com.example.kpkn.screens.sessioneditor.components

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState
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
import com.example.kpkn.screens.sessioneditor.components.SetCardDensity
import com.example.kpkn.screens.sessioneditor.components.SetEditorCard
import com.example.kpkn.screens.sessioneditor.components.SessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorBreakpoint
import com.example.kpkn.screens.sessioneditor.components.rememberSessionEditorSpacing
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios
import com.example.kpkn.screens.wikilab.CustomExerciseCreatorContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

import com.example.kpkn.screens.sessioneditor.SessionCloneDayOption
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.dayInitial
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.DarkEditorChipSelected
import com.example.kpkn.screens.sessioneditor.DarkEditorSurface
import com.example.kpkn.screens.sessioneditor.DarkEditorSurfaceSoft
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.ToggleToken
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.screens.sessioneditor.EditorSectionCard
import com.example.kpkn.screens.sessioneditor.ExerciseFactChip
import com.example.kpkn.screens.sessioneditor.DurationPickerField
import com.example.kpkn.screens.sessioneditor.NativeWheelPicker
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.CompactGoalTrackingButton
import com.example.kpkn.screens.sessioneditor.CompactRestPickerButton
import com.example.kpkn.screens.sessioneditor.CompactRestBundleButton
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.EstimatedRingsRow
import com.example.kpkn.screens.sessioneditor.UnilateralAddGhostCard
import com.example.kpkn.screens.sessioneditor.AddSetGhostCard
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabelShort
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.smartReferenceMetricLabel
import com.example.kpkn.screens.sessioneditor.estimatedMetricLabel
import com.example.kpkn.screens.sessioneditor.formatEstimatedMetric
import com.example.kpkn.screens.sessioneditor.resolveRelationshipAnchorName
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.screens.sessioneditor.isEditorUncategorized
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.screens.sessioneditor.sessionSolidPresets
import com.example.kpkn.screens.sessioneditor.sessionBackgroundPresets
import com.example.kpkn.screens.sessioneditor.SessionCoverGradient
import com.example.kpkn.screens.sessioneditor.PART_COLORS
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SessionEditorSheet
import com.example.kpkn.screens.sessioneditor.SessionSaveScope
import com.example.kpkn.screens.sessioneditor.SessionCloneApplyMode
import com.example.kpkn.screens.sessioneditor.SessionCloneExerciseOption
import com.example.kpkn.screens.sessioneditor.SessionCloneSourceOption
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.ProgramExerciseCandidate
import com.example.kpkn.screens.sessioneditor.SessionExerciseEditorBlock
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeAlert
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeStatus
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeCorrectionType
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeSummary
import com.example.kpkn.screens.sessioneditor.VariantFlowSheet
import com.example.kpkn.screens.sessioneditor.VariantFlowResultCache
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.components.CompactTemplateCard
import com.example.kpkn.screens.sessioneditor.SessionSubMuscleBreakdownList
import com.example.kpkn.screens.sessioneditor.getMuscleEmphasisEducationalText
import com.example.kpkn.screens.sessioneditor.countDisplaySets
import com.example.kpkn.screens.sessioneditor.buildDisplayContributions
import com.example.kpkn.screens.sessioneditor.suggestWarmupReps
import com.example.kpkn.screens.sessioneditor.components.InlineSetRow
import com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogInfoDialog
import com.example.kpkn.screens.sessioneditor.components.SupersetRestPickerButton
import com.example.kpkn.screens.sessioneditor.components.SupersetRestPickerDialog
import com.example.kpkn.screens.sessioneditor.components.SupersetRestWheelRow
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.components.HeroGlassFab
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknDropdownMenu

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionContextNavigator(
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
    onSetMainSessionForDay: (String) -> Unit,
    currentSessionId: String,
    currentDayOfWeek: Int?,
    currentSession: Session? = null,
    activeVariant: WeekVariant = WeekVariant.A,
    availableVariants: List<WeekVariant> = listOf(WeekVariant.A),
    onCreateVariant: (WeekVariant, String) -> Unit = { _, _ -> },
    onDeleteVariant: (WeekVariant) -> Unit = {},
    onSwitchVariant: (WeekVariant) -> Unit = {},
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

    // Variant menu (long-press on current session day circle)
    var showVariantMenu by remember { mutableStateOf(false) }
    var showCreateVariantDialog by remember { mutableStateOf(false) }
    var newVariantName by remember { mutableStateOf("") }
    val nextVariant = remember(availableVariants) {
        listOf(WeekVariant.B, WeekVariant.C, WeekVariant.D)
            .firstOrNull { it !in availableVariants }
    }

    val navShape = RoundedCornerShape(22.dp)
    val navModifier = Modifier
        .wrapContentWidth()
        .padding(horizontal = 10.dp)
        .padding(top = 2.dp, bottom = 0.dp)
        .kpknGlassOrFallback(hazeState, navShape)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = navModifier) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 5.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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

                            val isCurrentSessionDay = hasSession && day == currentDayOfWeek
                            // Outer Box without clip so the variant dropdown isn't clipped to the circle.
                            Box {
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
                                        .combinedClickable(
                                            onClick = {
                                                selectedDay = day
                                                if (hasSession) {
                                                    val primaryOrFirst = daySessions.firstOrNull { it.isMainSession } ?: daySessions.first()
                                                    onSelectSession(primaryOrFirst.id)
                                                } else {
                                                    pendingCreateDay = day
                                                    showCreateSessionDialog = true
                                                }
                                            },
                                            onLongClick = {
                                                if (isCurrentSessionDay) {
                                                    selectedDay = day
                                                    showVariantMenu = true
                                                }
                                            },
                                        ),
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
                                if (isCurrentSessionDay) {
                                    KpknDropdownMenu(
                                        expanded = showVariantMenu,
                                        onDismissRequest = { showVariantMenu = false },
                                    ) {
                                        availableVariants.forEach { variant ->
                                            val isActive = variant == activeVariant
                                            val variantName = when (variant) {
                                                WeekVariant.A -> "Original"
                                                WeekVariant.B -> currentSession?.sessionB?.name ?: "Derivada"
                                                WeekVariant.C -> currentSession?.sessionC?.name ?: "Derivada"
                                                WeekVariant.D -> currentSession?.sessionD?.name ?: "Derivada"
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        if (isActive) "✓ $variantName" else variantName,
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    )
                                                },
                                                onClick = {
                                                    showVariantMenu = false
                                                    if (!isActive) onSwitchVariant(variant)
                                                },
                                            )
                                        }
                                        if (activeVariant != WeekVariant.A) {
                                            DropdownMenuItem(
                                                text = { Text("Eliminar variante activa") },
                                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                                onClick = {
                                                    showVariantMenu = false
                                                    onDeleteVariant(activeVariant)
                                                },
                                            )
                                        }
                                        if (nextVariant != null) {
                                            DropdownMenuItem(
                                                text = { Text("Crear sesión derivada") },
                                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                                onClick = {
                                                    showVariantMenu = false
                                                    newVariantName = "${currentSession?.name.orEmpty().ifBlank { "Sesión" }} – Rápida"
                                                    showCreateVariantDialog = true
                                                },
                                            )
                                        }
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
                                KpknDropdownMenu(
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
    }

    // Create session dialog
    if (showCreateSessionDialog && pendingCreateDay > 0) {
        val isCompetitionDay = pendingCreateDay in competitionKeyDaysInWeek
        KpknAlertDialog(
            onDismissRequest = { showCreateSessionDialog = false },
            icon = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("¿Crear sesión para ${sessionEditorDayLabel(pendingCreateDay)}?") },
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

    if (showCreateVariantDialog && nextVariant != null) {
        KpknAlertDialog(
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
