package com.example.kpkn.screens.sessioneditor.components.sheets

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
import com.example.kpkn.ui.components.KpknSheet
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.catalogSearchRedirects
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

import com.example.kpkn.screens.sessioneditor.SessionEditorSheet
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.ApplyRulesOutcome
import com.example.kpkn.screens.sessioneditor.SessionSaveScope
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SessionCloneApplyMode
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.screens.sessioneditor.DefaultIntensityType
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.components.CardioCatalogSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.CoverSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.HistorySheet
import com.example.kpkn.screens.sessioneditor.components.sheets.RulesSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SessionClonerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SaveSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.WarmupSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.MobilityPickerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetManagerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.SupersetMemberPickerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.RelationshipPickerSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.ExerciseQuickActionsSheet
import com.example.kpkn.screens.sessioneditor.components.sheets.TemplatesSheet
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
import com.example.kpkn.screens.sessioneditor.formatHistoryTimestamp
import com.example.kpkn.screens.sessioneditor.trainingModeLabel
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabelShort
import com.example.kpkn.screens.sessioneditor.dayInitial
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
import com.example.kpkn.screens.sessioneditor.SessionCloneDayOption
import com.example.kpkn.screens.sessioneditor.SessionCloneExerciseOption
import com.example.kpkn.screens.sessioneditor.SessionCloneSourceOption
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.ProgramExerciseCandidate
import com.example.kpkn.screens.sessioneditor.SessionExerciseEditorBlock
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeAlert
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeStatus
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeCorrectionType
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeSummary
import com.example.kpkn.screens.sessioneditor.CatalogSelectionWizard
import com.example.kpkn.screens.sessioneditor.CatalogSelectionDraftBridge
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
import com.example.kpkn.screens.sessioneditor.components.HeroGlassFab
import com.example.kpkn.ui.components.KpknAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionEditorSheets(
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
    onApplyRules: (String?) -> ApplyRulesOutcome,
    onCloneCurrentToTargets: (Set<String>, Set<String>?, SessionCloneApplyMode) -> Unit,
    onImportFromSource: (String, Set<String>?, SessionCloneApplyMode) -> Unit,
    onSave: (SessionSaveScope) -> Unit,
    onApplyAssistantSuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
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
    onDistributeTargetAcrossParts: () -> Unit = {},
    onApplyRuleTemplate: (String, String?) -> Unit = { _, _ -> },
    onSaveRuleTemplate: (String) -> Unit = {},
    onRenameRuleTemplate: (String, String) -> Unit = { _, _ -> },
    onDeleteRuleTemplate: (String) -> Unit = {},
    onPatchRuleDefaults: (String?, (com.example.kpkn.screens.sessioneditor.SessionEditorRuleDefaults) -> com.example.kpkn.screens.sessioneditor.SessionEditorRuleDefaults) -> Unit = { _, _ -> },
    onApplyTimeCoachSuggestion: (String) -> Unit = {},
    onDismissTimeCoachSuggestion: (String) -> Unit = {},
    onRefreshTimeCoach: () -> Unit = {},
    onRulesInitialTabConsumed: () -> Unit = {},
    onQuickActionOpenPicker: () -> Unit,
    onQuickActionOpenWarmup: () -> Unit,
    onQuickActionOpenMobility: () -> Unit,
    onAddMobilityExercise: (MobilityExercise) -> Unit,
    onRemoveMobilityExercise: (MobilityExercise) -> Unit = {},
    onAddCardio: (CardioCatalogItem) -> Unit = {},
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
    onOpenCatalog: () -> Unit,
    useFullPageCatalog: Boolean = false,
    allTemplates: List<SessionTemplate>,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
    onTemplateSearchChange: (String) -> Unit,
) {
    val session = uiState.session ?: return
    if (uiState.sheet == SessionEditorSheet.NONE) return
    val mobilityTargetSeries = uiState.quickActionsExerciseId
        ?.let { targetId -> session.allExercises().firstOrNull { it.id == targetId }?.mobilitySeries }
        .orEmpty()

    // AUGE is rendered as an in-composition Liquid Glass overlay in SessionEditorScreen
    // (sibling of hazeSource). Do NOT put it in KpknSheet — blur would die.
    if (uiState.sheet == SessionEditorSheet.AUGE) return

    val warmupExercise = session.allExercises().find { it.id == uiState.warmupExerciseId }
    val quickActionExercise = uiState.quickActionsExerciseId?.let { targetId ->
        session.allExercises().find { it.id == targetId }
    }

    if (uiState.sheet == SessionEditorSheet.EXERCISE_PICKER && useFullPageCatalog) return

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
         KpknSheet(
             onDismissRequest = requestPickerDismiss,
             safeTopInset = true,
           maxHeightFraction = 1f,
           stableHeightFraction = 1f,
           additionalGlassScrim = Color.Black.copy(alpha = 0.22f),
       ) {
              Column(
                 modifier = Modifier.fillMaxSize(),
              ) {
                   val pickerTargetExercise = uiState.pickerTargetExerciseId?.let { targetId ->
                       session.allExercises().firstOrNull { it.id == targetId }
                   }
                   ExercisePickerSheet(
                        query = uiState.searchQuery.ifBlank {
                            pickerTargetExercise?.takeIf { it.catalogDefinitionId == null }?.name.orEmpty()
                        },
                       catalog = exerciseCatalogSnapshot(),
                       workoutLogs = uiState.workoutLogs,
                       editingExisting = uiState.pickerTargetExerciseId != null,
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
                       onDismiss = requestPickerDismiss,
                       onSelectionChange = { pendingPickerSelection = it },
                       editingCatalogDefinitionId = pickerTargetExercise?.catalogDefinitionId,
                       editingCatalogConfigurationId = pickerTargetExercise?.catalogConfigurationId,
                       targetGroupName = uiState.pickerTargetPartId?.let { pid ->
                           session.parts.firstOrNull { it.id == pid }?.name
                       },
                   )
             }
         }
         if (showPickerExitConfirm) {
             KpknAlertDialog(
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

    KpknSheet(
        onDismissRequest = onDismiss,
        stableHeightFraction = if (uiState.sheet == SessionEditorSheet.RULES) 0.82f else null,
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
                onDistributeTargetAcrossParts = onDistributeTargetAcrossParts,
                onApplyRuleTemplate = onApplyRuleTemplate,
                onSaveRuleTemplate = onSaveRuleTemplate,
                onRenameRuleTemplate = onRenameRuleTemplate,
                onDeleteRuleTemplate = onDeleteRuleTemplate,
                onPatchRuleDefaults = onPatchRuleDefaults,
                onApplyTimeCoachSuggestion = onApplyTimeCoachSuggestion,
                onDismissTimeCoachSuggestion = onDismissTimeCoachSuggestion,
                onRefreshTimeCoach = onRefreshTimeCoach,
                onInitialTabConsumed = onRulesInitialTabConsumed,
                onDismiss = onDismiss,
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
            SessionEditorSheet.AUGE -> Unit // handled as glass overlay in SessionEditorScreen
            SessionEditorSheet.WARMUP -> WarmupSheet(exercise = warmupExercise, onSave = onWarmupSave)
            SessionEditorSheet.MOBILITY_PICKER -> MobilityPickerSheet(
                selectedMobilityIds = mobilityTargetSeries.map { it.catalogIdentityKey() }.toSet(),
                onAdd = onAddMobilityExercise,
                onRemove = onRemoveMobilityExercise,
                onDismiss = onDismiss,
            )
            SessionEditorSheet.CARDIO_PICKER -> CardioCatalogSheet(onAdd = onAddCardio)
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
                val draft = uiState.supersetDraft ?: return@KpknSheet
                SupersetMemberPickerSheet(
                    draft = draft,
                    sessionExercises = session.allExercises(),
                    onUpdateDraft = onSupersetDraftUpdate,
                    onConfirm = onCreateSupersetGroup,
                    onOpenCatalog = onOpenCatalog,
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
                catalog = exerciseCatalogSnapshot(),
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
