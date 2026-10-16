package com.example.kpkn.screens.workout

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.CardioProgramMode
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.UnilateralSideOrder
import com.example.kpkn.data.models.UnilateralTarget
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.ui.draw.clip
import com.example.kpkn.data.models.PrReference
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.calculations.calculateGeneralizedCapacity
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.sessioneditor.components.ExerciseSmartLoadDialog
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.effectiveSupersetGroupFor
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import com.example.kpkn.domain.workout.LiveSessionReorder
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.CompactModeSelector
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.SideOrderChip
import com.example.kpkn.screens.sessioneditor.UnilateralModeSelector
import com.example.kpkn.screens.sessioneditor.components.CardioEditorCard
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetTokens
import dev.chrisbanes.haze.HazeState
import java.util.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import androidx.compose.foundation.border
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
@Stable
internal class WorkoutStructureSheetsState {
    var exerciseContextExerciseId by mutableStateOf<String?>(null)
    /** True when a superset mini-card opened the member's own action menu. */
    var exerciseContextForceMemberActions by mutableStateOf(false)
    var showReplaceExercisePicker by mutableStateOf(false)
    var replaceTargetExerciseId by mutableStateOf<String?>(null)
    var replaceSearchQuery by mutableStateOf("")
    var showReplaceCardioPicker by mutableStateOf(false)
    var replaceCardioTargetExerciseId by mutableStateOf<String?>(null)
    var setupSheetExerciseId by mutableStateOf<String?>(null)
    var tagSheetExerciseId by mutableStateOf<String?>(null)
    var requestLiveTagList by mutableStateOf(false)
    var selectedExerciseContextTab by mutableStateOf<WorkoutExerciseContextTab?>(null)
    var editSheetExerciseId by mutableStateOf<String?>(null)
    var showWorkoutSupersetCreator by mutableStateOf(false)
    var workoutSupersetSelectedExerciseId by mutableStateOf<String?>(null)
    var supersetSettingsGroupId by mutableStateOf<String?>(null)
    var addCatalogToSupersetGroupId by mutableStateOf<String?>(null)
    var addCatalogSearchQuery by mutableStateOf("")
    var addCatalogSelectedIds by mutableStateOf<Set<String>>(emptySet())
    var addExerciseAfterId by mutableStateOf<String?>(null)
    var addExerciseSearchQuery by mutableStateOf("")
    var addExerciseSelectedIds by mutableStateOf<Set<String>>(emptySet())
    var createSupersetFromCatalogAnchorId by mutableStateOf<String?>(null)
    var showReorderSheet by mutableStateOf(false)
    var reorderSheetExerciseIds by mutableStateOf<List<String>>(emptyList())
    var showReorderCrossBoundaryConfirm by mutableStateOf(false)
    var reorderCrossBoundaryMessages by mutableStateOf<List<String>>(emptyList())
    var pendingGlobalReorderIds by mutableStateOf<List<String>>(emptyList())

    fun hasOpenDrawer(): Boolean =
        exerciseContextExerciseId != null ||
            showReplaceExercisePicker ||
            showReplaceCardioPicker ||
            setupSheetExerciseId != null ||
            tagSheetExerciseId != null ||
            requestLiveTagList ||
            editSheetExerciseId != null ||
            showWorkoutSupersetCreator ||
            supersetSettingsGroupId != null ||
            addCatalogToSupersetGroupId != null ||
            addExerciseAfterId != null ||
            createSupersetFromCatalogAnchorId != null ||
            showReorderSheet
}

@Composable
internal fun rememberWorkoutStructureSheetsState(): WorkoutStructureSheetsState =
    remember { WorkoutStructureSheetsState() }

/**
 * Opción del cajón de opciones (KPKN_UI_V2 §1 y §4): un módulo tocable con el icono en un círculo `inset` de 36 dp y el texto en
 * Inter SemiBold 15 debajo. Va en la rejilla de dos columnas de antes; [danger] pinta icono y texto en `danger` («Omitir»).
 */
@Composable
private fun ContextActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val ink = if (danger) WizardColors.danger else WizardColors.text
    KpknModule(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = label,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila de dos opciones de la rejilla del cajón: las dos tienen la misma altura, la de la más alta. */
@Composable
private fun OptionsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        content = content,
    )
}

/**
 * Live-workout catalog host. Creation is integrated into the search flow, so
 * this simply delegates to the shared v2 picker surface.
 */
@Composable
private fun LiveExercisePickerWithCreator(
    query: String,
    workoutLogs: List<WorkoutLog>,
    editingExisting: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onOpenExerciseDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    selectedExercisesIds: Set<String> = emptySet(),
    onToggleExerciseSelection: (String) -> Unit = {},
    onClearExerciseSelection: () -> Unit = {},
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)? = null,
    editingCatalogDefinitionId: String? = null,
    editingCatalogConfigurationId: String? = null,
) {
    ExercisePickerSheet(
        query = query,
        catalog = exerciseCatalogSnapshot(),
        workoutLogs = workoutLogs,
        editingExisting = editingExisting,
        selectedExercisesIds = selectedExercisesIds,
        onToggleExerciseSelection = onToggleExerciseSelection,
        onClearExerciseSelection = onClearExerciseSelection,
        onSearch = onSearch,
        onSelect = onSelect,
        onMultiSelect = onMultiSelect,
        onCreateSuperset = onCreateSuperset,
        onOpenExerciseDetail = onOpenExerciseDetail,
        onDismiss = onDismiss,
        editingCatalogDefinitionId = editingCatalogDefinitionId,
        editingCatalogConfigurationId = editingCatalogConfigurationId,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutStructureSheetsHost(
    state: WorkoutStructureSheetsState,
    viewModel: WorkoutViewModel,
    uiState: WorkoutUiState,
    modeSession: Session,
    visibleExercises: List<Exercise>,
    currentExercise: Exercise?,
    currentSet: ExerciseSet?,
    renderedParts: List<SessionPart>,
    originalExercisePartMap: Map<String, String>,
    sessionAccentColor: Color,
    bottomHazeState: HazeState,
    allUserTags: List<String>,
    context: Context,
    onNavigateToWikiLab: (String) -> Unit,
    onOpenCatalog: ((CatalogLaunchRequest) -> Unit)? = null,
) {
    val useFullPageCatalog = onOpenCatalog != null

    LaunchedEffect(
        useFullPageCatalog,
        state.addCatalogToSupersetGroupId,
        state.addCatalogSearchQuery,
        state.addCatalogSelectedIds,
        state.addExerciseAfterId,
        state.createSupersetFromCatalogAnchorId,
        state.addExerciseSearchQuery,
        state.addExerciseSelectedIds,
        state.showReplaceExercisePicker,
        state.replaceTargetExerciseId,
        state.replaceSearchQuery,
    ) {
        val open = onOpenCatalog ?: return@LaunchedEffect
        val request = when {
            state.addCatalogToSupersetGroupId != null -> {
                val req = CatalogLaunchRequest(
                    origin = CatalogLaunchOrigin.SUPERSET,
                    selectionMode = CatalogSelectionMode.SUPERSET,
                    targetExerciseId = state.addCatalogToSupersetGroupId,
                    selectedExerciseIds = state.addCatalogSelectedIds.toList(),
                    initialQuery = state.addCatalogSearchQuery,
                )
                state.addCatalogToSupersetGroupId = null
                state.addCatalogSearchQuery = ""
                state.addCatalogSelectedIds = emptySet()
                req
            }
            state.addExerciseAfterId != null -> {
                val req = CatalogLaunchRequest(
                    origin = CatalogLaunchOrigin.LIVE_SESSION,
                    selectionMode = CatalogSelectionMode.MULTIPLE,
                    targetExerciseId = state.addExerciseAfterId,
                    selectedExerciseIds = state.addExerciseSelectedIds.toList(),
                    initialQuery = state.addExerciseSearchQuery,
                )
                state.addExerciseAfterId = null
                state.addExerciseSearchQuery = ""
                state.addExerciseSelectedIds = emptySet()
                req
            }
            state.createSupersetFromCatalogAnchorId != null -> {
                val req = CatalogLaunchRequest(
                    origin = CatalogLaunchOrigin.LIVE_SESSION,
                    selectionMode = CatalogSelectionMode.MULTIPLE,
                    targetExerciseId = state.createSupersetFromCatalogAnchorId,
                    selectedExerciseIds = emptyList(),
                    initialQuery = "",
                )
                state.createSupersetFromCatalogAnchorId = null
                req
            }
            state.showReplaceExercisePicker && state.replaceTargetExerciseId != null -> {
                val replaceTarget = modeSession.allExercises()
                    .firstOrNull { it.id == state.replaceTargetExerciseId }
                val req = CatalogLaunchRequest(
                    origin = CatalogLaunchOrigin.REPLACEMENT,
                    selectionMode = CatalogSelectionMode.REPLACEMENT,
                    targetExerciseId = state.replaceTargetExerciseId,
                    targetCatalogDefinitionId = replaceTarget?.catalogDefinitionId,
                    targetCatalogConfigurationId = replaceTarget?.catalogConfigurationId
                        ?: replaceTarget?.exerciseDbId,
                    initialQuery = state.replaceSearchQuery,
                )
                state.showReplaceExercisePicker = false
                state.replaceTargetExerciseId = null
                state.replaceSearchQuery = ""
                req
            }
            else -> null
        }
        request?.let(open)
    }
    if (state.exerciseContextExerciseId != null) {
        val exerciseId = state.exerciseContextExerciseId ?: return
        val contextExercise = visibleExercises.firstOrNull { it.id == exerciseId }
        // Prefer the member flag, but fall back to SupersetGroup.exerciseOrder
        // for legacy sessions whose JSON contains the group table before the
        // member refs were backfilled.  This keeps the outer group menu
        // reachable without changing the individual mini-card long-press path.
        val contextSupersetGroup = contextExercise?.let(modeSession::effectiveSupersetGroupFor)
            ?: contextExercise?.let { exercise ->
                modeSession.allSupersetGroups().firstOrNull { group ->
                    exercise.id in group.exerciseOrder
                }
            }
        val contextSupersetGroupId = contextExercise?.supersetGroupRefOrLegacyId()
            ?: contextSupersetGroup?.id
        WorkoutDrawer(
            title = when {
                contextExercise?.isCardio == true -> "Cardio"
                contextSupersetGroupId != null && !state.exerciseContextForceMemberActions -> "Superserie"
                else -> contextExercise?.name ?: "Acciones del ejercicio"
            },
            onDismiss = {
                state.exerciseContextExerciseId = null
                state.exerciseContextForceMemberActions = false
            },
            hazeState = bottomHazeState,
        ) {
            val actionRows: @Composable ColumnScope.() -> Unit = {
                if (contextExercise?.isCardio == true) {
                    val cardioExercise = contextExercise
                    ExerciseOptionsCardioRows(
                        summary = cardioContextSummary(cardioExercise),
                        onEdit = {
                            state.editSheetExerciseId = cardioExercise.id
                            state.exerciseContextExerciseId = null
                        },
                        onReplace = {
                            state.replaceCardioTargetExerciseId = cardioExercise.id
                            state.showReplaceCardioPicker = true
                            state.exerciseContextExerciseId = null
                        },
                        onSkip = {
                            viewModel.skipExercise(cardioExercise.id)
                            state.exerciseContextExerciseId = null
                        },
                    )
                } else if (contextExercise != null && contextSupersetGroupId != null && !state.exerciseContextForceMemberActions) {
                    val members = remember(contextSupersetGroupId, modeSession, contextSupersetGroup, visibleExercises) {
                        val ordered = SupersetRules.orderedMembers(modeSession, contextSupersetGroupId)
                        if (ordered.isNotEmpty()) ordered
                        else contextSupersetGroup?.exerciseOrder
                            ?.mapNotNull { id -> visibleExercises.firstOrNull { it.id == id } }
                            .orEmpty()
                    }
                    Text(
                        "${contextSupersetGroup?.rounds ?: SupersetRules.roundCount(modeSession, contextSupersetGroupId)} rondas · ${members.size} ejercicios",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    OptionsRow {
                        ContextActionButton("Ir a la superserie", KpknLineIcons.Route, {
                            viewModel.selectSupersetGroup(contextSupersetGroupId)
                            state.exerciseContextExerciseId = null
                        }, Modifier.weight(1f).fillMaxHeight())
                        ContextActionButton("Rondas", KpknLineIcons.Repeat, {
                            state.supersetSettingsGroupId = contextSupersetGroupId
                            state.exerciseContextExerciseId = null
                        }, Modifier.weight(1f).fillMaxHeight())
                    }
                    OptionsRow {
                        ContextActionButton("Agregar de sesión", KpknLineIcons.Plus, {
                            state.workoutSupersetSelectedExerciseId = contextExercise.id
                            state.showWorkoutSupersetCreator = true
                            state.exerciseContextExerciseId = null
                        }, Modifier.weight(1f).fillMaxHeight())
                        ContextActionButton("Agregar del catálogo", KpknLineIcons.Layers, {
                            state.addCatalogToSupersetGroupId = contextSupersetGroupId
                            state.addCatalogSearchQuery = ""
                            state.addCatalogSelectedIds = emptySet()
                            state.exerciseContextExerciseId = null
                        }, Modifier.weight(1f).fillMaxHeight())
                    }
                    Text("Miembros".uppercase(), style = KpknType.eyebrow, color = WizardColors.textMuted)
                    members.chunked(2).forEach { rowMembers ->
                        OptionsRow {
                            rowMembers.forEach { member ->
                                ContextActionButton(member.name, KpknLineIcons.Link, {
                                    state.exerciseContextForceMemberActions = true
                                    state.exerciseContextExerciseId = member.id
                                }, Modifier.weight(1f).fillMaxHeight())
                            }
                            if (rowMembers.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    ContextActionButton("Disolver superserie", KpknLineIcons.Unlink, {
                        viewModel.dissolveLiveSuperset(contextSupersetGroupId, preferredExerciseId = contextExercise.id)
                        state.exerciseContextExerciseId = null
                    }, Modifier.fillMaxWidth())
                } else {
                    val exercise = contextExercise
                    ExerciseOptionsStrengthRows(
                        onHistory = {
                            exercise?.let { viewModel.showHistoryForExercise(it) }
                            state.exerciseContextExerciseId = null
                        },
                        onTags = {
                            if (exercise != null && exercise.id == currentExercise?.id) {
                                state.requestLiveTagList = true
                            } else {
                                state.tagSheetExerciseId = exercise?.id
                            }
                            state.exerciseContextExerciseId = null
                        },
                        onEdit = {
                            state.editSheetExerciseId = exercise?.id
                            state.exerciseContextExerciseId = null
                        },
                        onReplace = {
                            state.replaceTargetExerciseId = exercise?.id
                            state.replaceSearchQuery = if (exercise?.catalogDefinitionId == null) exercise?.name.orEmpty() else ""
                            state.showReplaceExercisePicker = true
                            state.exerciseContextExerciseId = null
                        },
                        onAddOther = {
                            state.addExerciseAfterId = exercise?.id
                            state.addExerciseSearchQuery = ""
                            state.addExerciseSelectedIds = emptySet()
                            state.exerciseContextExerciseId = null
                        },
                        onReorder = {
                            state.reorderSheetExerciseIds = visibleExercises.map { it.id }
                            state.showReorderSheet = true
                            state.exerciseContextExerciseId = null
                        },
                        onCreateSuperset = {
                            state.workoutSupersetSelectedExerciseId = exercise?.id
                            state.showWorkoutSupersetCreator = true
                            state.exerciseContextExerciseId = null
                        },
                        onSkip = {
                            viewModel.skipExercise(exerciseId)
                            state.exerciseContextExerciseId = null
                        },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = actionRows)
        }
    }

    state.supersetSettingsGroupId?.let { groupId ->
        val group = modeSession.allSupersetGroups().firstOrNull { it.id == groupId }
        if (group == null) {
            state.supersetSettingsGroupId = null
        } else {
            var roundsText by remember(groupId, group.rounds) {
                mutableStateOf((group.rounds ?: SupersetRules.roundCount(modeSession, groupId)).toString())
            }
            var restBetweenText by remember(groupId, group.restBetweenExercises) {
                mutableStateOf(group.restBetweenExercises.toString())
            }
            var restAfterText by remember(groupId, group.restAfterSuperset) {
                mutableStateOf(group.restAfterSuperset.toString())
            }
            KpknAlertDialog(
                onDismissRequest = { state.supersetSettingsGroupId = null },
                title = { Text("Rondas y descansos", style = KpknType.dialogTitle, color = WizardColors.text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpknTextField(
                            value = roundsText,
                            onValueChange = { roundsText = it.filter(Char::isDigit).take(2) },
                            label = "Rondas",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        KpknTextField(
                            value = restBetweenText,
                            onValueChange = { restBetweenText = it.filter(Char::isDigit).take(4) },
                            label = "Descanso entre ejercicios (s)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        KpknTextField(
                            value = restAfterText,
                            onValueChange = { restAfterText = it.filter(Char::isDigit).take(4) },
                            label = "Descanso post-ronda (s)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    KpknPrimaryPill(
                        text = "Guardar",
                        onClick = {
                            viewModel.updateLiveSupersetRest(
                                groupId = groupId,
                                restBetween = restBetweenText.toIntOrNull(),
                                restAfter = restAfterText.toIntOrNull(),
                                rounds = roundsText.toIntOrNull(),
                            )
                            state.supersetSettingsGroupId = null
                        },
                    )
                },
                dismissButton = {
                    KpknTextAction(text = "Cancelar", onClick = { state.supersetSettingsGroupId = null })
                },
            )
        }
    }

    WorkoutReorderSheets(
        state = state,
        visibleExercises = visibleExercises,
        originalExercisePartMap = originalExercisePartMap,
        onApplyReorder = { ids, partMap, isGlobal -> viewModel.applyReorderAndPromptPersistence(ids, partMap, isGlobal) },
        session = modeSession,
    )

    WorkoutSupersetCreatorSheet(
        state = state,
        modeSession = modeSession,
        renderedParts = renderedParts,
        visibleExercises = visibleExercises,
        completedSets = uiState.completedSets,
        hasCatalogAction = onOpenCatalog != null,
        onCreateSuperset = { exerciseIds, partId, restBetween, restAfter, rounds ->
            viewModel.createLiveSuperset(
                exerciseIds = exerciseIds,
                partId = partId,
                restBetween = restBetween,
                restAfter = restAfter,
                rounds = rounds,
            )
        },
    )

    WorkoutEditSeriesSheet(
        state = state,
        modeSession = modeSession,
        sessionAccentColor = sessionAccentColor,
        bottomHazeState = bottomHazeState,
        onSaveDefinition = { exerciseId, draft ->
            viewModel.updateExerciseDefinition(exerciseId) { current -> draft.copy(id = current.id) }
        },
    )

    // ─── Tag-only sheet (from "Etiquetas" card) ────────────────────────────
    if (state.tagSheetExerciseId != null) {
        val tagEx = visibleExercises.firstOrNull { it.id == state.tagSheetExerciseId }
        val currentExTag = uiState.exerciseTags[state.tagSheetExerciseId]
        if (tagEx != null) {
            WorkoutDrawer(
                title = "Etiquetas",
                onDismiss = { state.tagSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                ExerciseSheetSubtitle(tagEx.name)
                ExerciseTagSheetContent(
                    currentTag = currentExTag,
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(tagEx.id) else viewModel.setExerciseTag(tagEx.id, tag) },
                    onDismiss = { state.tagSheetExerciseId = null },
                    userTags = allUserTags,
                    profiles = viewModel.profilesForExercise(tagEx),
                )
            }
        }
    }

    // ─── Setup/tag sheet (from context menu) ─────────────────────────────────
    if (state.setupSheetExerciseId != null) {
        val setupEx = visibleExercises.firstOrNull { it.id == state.setupSheetExerciseId }
        val currentExTag = uiState.exerciseTags[state.setupSheetExerciseId]
        val setupSet = if (setupEx?.id == currentExercise?.id) currentSet else setupEx?.sets?.firstOrNull()
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        if (setupEx != null) {
            val suggestedTag = remember(setupEx, workoutLogs) {
                com.example.kpkn.domain.workout.WorkoutContextRecurrenceEngine.detectDayRecurrence(
                    exerciseDbId = setupEx.exerciseDbId.orEmpty(),
                    dayOfWeek = java.time.LocalDate.now().dayOfWeek,
                    logs = workoutLogs
                ).tagId
            }
            WorkoutDrawer(
                title = "Setup",
                onDismiss = { state.setupSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                ExerciseSheetSubtitle(setupEx.name)
                ExerciseSetupSheetContent(
                    exercise = setupEx,
                    currentSet = setupSet,
                    currentTag = currentExTag,
                    profiles = viewModel.profilesForExercise(setupEx),
                    activeProfileId = uiState.activeContextProfileByExerciseId[setupEx.id],
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(setupEx.id) else viewModel.setExerciseTag(setupEx.id, tag) },
                    onSelectProfile = { profileId: String -> viewModel.setActiveContextProfile(setupEx.id, profileId) },
                    onSaveProfile = { profile: WorkoutContextProfile -> viewModel.upsertContextProfile(setupEx, profile) },
                    onUpdateExercise = { transform -> viewModel.updateExerciseDefinition(setupEx.id, transform = transform) },
                    onUpdateSet = { setId, transform -> viewModel.updateExerciseSetPlan(setupEx.id, setId, transform) },
                    onDismiss = { state.setupSheetExerciseId = null },
                    sessionAccentColor = sessionAccentColor,
                    userTags = allUserTags,
                    suggestedTag = suggestedTag,
                )
            }
        }
    }

    if (!useFullPageCatalog && state.addCatalogToSupersetGroupId != null) {
        val targetGroupId = state.addCatalogToSupersetGroupId ?: return
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        KpknSheet(
            onDismissRequest = {
                state.addCatalogToSupersetGroupId = null
                state.addCatalogSearchQuery = ""
                state.addCatalogSelectedIds = emptySet()
            },
            safeTopInset = true,
            maxHeightFraction = 1f,
            stableHeightFraction = 1f
        ) {
            LiveExercisePickerWithCreator(
                query = state.addCatalogSearchQuery,
                workoutLogs = workoutLogs,
                editingExisting = false,
                selectedExercisesIds = state.addCatalogSelectedIds,
                onToggleExerciseSelection = { id ->
                    state.addCatalogSelectedIds = if (id in state.addCatalogSelectedIds) {
                        state.addCatalogSelectedIds - id
                    } else {
                        state.addCatalogSelectedIds + id
                    }
                },
                onClearExerciseSelection = { state.addCatalogSelectedIds = emptySet() },
                onSearch = { state.addCatalogSearchQuery = it },
                onSelect = { info ->
                    viewModel.addCatalogExerciseToLiveSuperset(targetGroupId, info)
                    state.addCatalogToSupersetGroupId = null
                    state.addCatalogSearchQuery = ""
                    state.addCatalogSelectedIds = emptySet()
                },
                onMultiSelect = { infos ->
                    infos.forEach { info ->
                        viewModel.addCatalogExerciseToLiveSuperset(targetGroupId, info)
                    }
                    state.addCatalogToSupersetGroupId = null
                    state.addCatalogSearchQuery = ""
                    state.addCatalogSelectedIds = emptySet()
                    emptyList()
                },
                onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                onDismiss = {
                    state.addCatalogToSupersetGroupId = null
                    state.addCatalogSearchQuery = ""
                    state.addCatalogSelectedIds = emptySet()
                },
            )
        }
    }

    if (!useFullPageCatalog && state.addExerciseAfterId != null) {
        val targetExerciseId = state.addExerciseAfterId ?: return
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        KpknSheet(
            onDismissRequest = {
                state.addExerciseAfterId = null
                state.addExerciseSearchQuery = ""
                state.addExerciseSelectedIds = emptySet()
            },
            safeTopInset = true,
            maxHeightFraction = 1f,
            stableHeightFraction = 1f
        ) {
            LiveExercisePickerWithCreator(
                query = state.addExerciseSearchQuery,
                workoutLogs = workoutLogs,
                editingExisting = false,
                selectedExercisesIds = state.addExerciseSelectedIds,
                onToggleExerciseSelection = { id ->
                    state.addExerciseSelectedIds = if (id in state.addExerciseSelectedIds) {
                        state.addExerciseSelectedIds - id
                    } else {
                        state.addExerciseSelectedIds + id
                    }
                },
                onClearExerciseSelection = { state.addExerciseSelectedIds = emptySet() },
                onSearch = { state.addExerciseSearchQuery = it },
                onSelect = { info ->
                    viewModel.addExerciseAfter(targetExerciseId, info)
                    state.addExerciseAfterId = null
                    state.addExerciseSearchQuery = ""
                    state.addExerciseSelectedIds = emptySet()
                },
                onMultiSelect = { infos ->
                    infos.asReversed().forEach { info ->
                        viewModel.addExerciseAfter(targetExerciseId, info)
                    }
                    state.addExerciseAfterId = null
                    state.addExerciseSearchQuery = ""
                    state.addExerciseSelectedIds = emptySet()
                    emptyList()
                },
                onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                onDismiss = {
                    state.addExerciseAfterId = null
                    state.addExerciseSearchQuery = ""
                    state.addExerciseSelectedIds = emptySet()
                },
            )
        }
    }

    if (!useFullPageCatalog && state.showReplaceExercisePicker && state.replaceTargetExerciseId != null) {
        val programRepository = remember(context) { com.example.kpkn.data.repository.ProgramRepository.getInstance() }
        val workoutLogs by programRepository.history.collectAsStateWithLifecycle()
        val replaceTarget = visibleExercises.firstOrNull { it.id == state.replaceTargetExerciseId }

        KpknSheet(
            onDismissRequest = {
                state.showReplaceExercisePicker = false
                state.replaceTargetExerciseId = null
            },
            safeTopInset = true,
            maxHeightFraction = 1f,
            stableHeightFraction = 1f
        ) {
            LiveExercisePickerWithCreator(
                query = state.replaceSearchQuery,
                workoutLogs = workoutLogs,
                editingExisting = true,
                onSearch = { state.replaceSearchQuery = it },
                onSelect = { info ->
                    val target = state.replaceTargetExerciseId
                    if (target != null) {
                        state.showReplaceExercisePicker = false
                        state.replaceTargetExerciseId = null
                        viewModel.replaceExercise(
                            exerciseId = target,
                            replacement = info,
                            deferPersistencePrompt = true,
                        )
                        viewModel.revealReplacementPersistencePrompt(target)
                    }
                },
                onMultiSelect = { emptyList() },
                onOpenExerciseDetail = { dbId -> onNavigateToWikiLab(dbId) },
                onDismiss = {
                    state.showReplaceExercisePicker = false
                    state.replaceTargetExerciseId = null
                },
                editingCatalogDefinitionId = replaceTarget?.catalogDefinitionId,
                editingCatalogConfigurationId = replaceTarget?.catalogConfigurationId,
            )
        }
    }

    uiState.pendingReplacementPersistencePrompt?.let {
        val options = viewModel.replacementScopeOptions()
        KpknAlertDialog(
            onDismissRequest = {
                viewModel.dismissPendingReplacementPersistencePrompt()
            },
            title = { Text("Persistencia de reemplazo", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Cómo quieres guardar este cambio?", style = KpknType.body, color = WizardColors.text)
                    options.filter { it != ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING }.forEach { scope ->
                        KpknSecondaryPill(
                            text = when (scope) {
                                ReplacementPersistenceScopeV2.SESSION_ONLY -> "Solo esta vez"
                                ReplacementPersistenceScopeV2.PERMANENT -> "Reemplazar en todo el programa"
                                ReplacementPersistenceScopeV2.BLOCK_MATCHING -> "Aplicar a todo el bloque"
                                ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> ""
                            },
                            onClick = {
                                viewModel.commitPendingReplacementPersistence(scope)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                KpknTextAction(
                    text = "Cancelar",
                    onClick = {
                        viewModel.dismissPendingReplacementPersistencePrompt()
                    },
                )
            },
        )
    }

    uiState.pendingStructuralPersistence?.let { change ->
        // Voice AddSet already asks sesión/permanente by TTS — hide tactile dialog until resolved.
        if (change is PendingStructuralChange.AddSet &&
            uiState.voiceSessionEnabled &&
            uiState.voiceSessionState.pendingAddSetPersistence
        ) {
            return@let
        }
        val options = viewModel.replacementScopeOptions()
        val title = when (change) {
            is PendingStructuralChange.AddSet -> "Añadir serie"
            is PendingStructuralChange.AddExercise -> "Agregar ejercicio"
            is PendingStructuralChange.AddExercises -> "Agregar ejercicios"
            is PendingStructuralChange.AddSuperset -> "Crear superserie"
            is PendingStructuralChange.ReorderExercises -> "Reordenar ejercicios"
            is PendingStructuralChange.RemoveSet -> "Eliminar serie"
            is PendingStructuralChange.RemoveExercise -> "Eliminar ejercicio"
            is PendingStructuralChange.RemoveExercises -> "Eliminar ejercicios"
            is PendingStructuralChange.DissolveSuperset -> "Disolver superserie"
        }
        KpknAlertDialog(
            onDismissRequest = {
                viewModel.clearPendingStructuralPersistence()
            },
            title = { Text(title, style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (change) {
                            is PendingStructuralChange.AddSet -> {
                                "Se añadió una serie a «${change.exerciseName}». ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.AddExercise -> {
                                "Se agregó «${change.newExerciseName}». ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.AddExercises -> {
                                "Se agregaron ${change.newExerciseNames.size} ejercicios. ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.AddSuperset -> {
                                "Se creó una superserie con ${change.newExerciseNames.size} ejercicios. ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.ReorderExercises -> {
                                "Se reordenaron los ejercicios. ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.RemoveSet -> {
                                "Se eliminó una serie de «${change.exerciseName}». ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.RemoveExercise -> {
                                "Se eliminó «${change.exerciseName}». ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.RemoveExercises -> {
                                "Se eliminaron ${change.exerciseNames.size} ejercicios. ¿Cómo quieres guardar este cambio?"
                            }
                            is PendingStructuralChange.DissolveSuperset -> {
                                "Se disolvió una superserie (${change.exerciseNames.size} ejercicios). ¿Cómo quieres guardar este cambio?"
                            }
                        },
                        style = KpknType.body,
                        color = WizardColors.text,
                    )
                    options.filter { it != ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING }.forEach { scope ->
                        KpknSecondaryPill(
                            text = when (scope) {
                                ReplacementPersistenceScopeV2.SESSION_ONLY -> "Solo esta vez"
                                ReplacementPersistenceScopeV2.PERMANENT -> "Guardar en esta semana"
                                ReplacementPersistenceScopeV2.BLOCK_MATCHING -> "Aplicar a todo el bloque"
                                ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> ""
                            },
                            onClick = {
                                viewModel.commitStructuralPersistence(scope)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                KpknTextAction(
                    text = "Cancelar",
                    onClick = {
                        viewModel.clearPendingStructuralPersistence()
                    },
                )
            },
        )
    }

    if (state.showReplaceCardioPicker && state.replaceCardioTargetExerciseId != null) {
        val targetId = state.replaceCardioTargetExerciseId ?: return
        com.example.kpkn.ui.components.KpknSheet(
            onDismissRequest = {
                state.showReplaceCardioPicker = false
                state.replaceCardioTargetExerciseId = null
            }
        ) {
            com.example.kpkn.screens.sessioneditor.components.CardioCatalogSheet(
                isReplacing = true,
                onAdd = { item ->
                    viewModel.replaceCardioExercise(targetId, item)
                    state.showReplaceCardioPicker = false
                    state.replaceCardioTargetExerciseId = null
                },
            )
        }
    }
}

private fun cardioContextSummary(exercise: Exercise): String {
    val details = exercise.cardioDetails
    val mode = when (details?.programMode()) {
        CardioProgramMode.HIIT_SIT -> "HIIT / SIT"
        CardioProgramMode.INTERVALS -> "Intervalos"
        CardioProgramMode.STEADY -> "Estático"
        null -> "Cardio"
    }
    val duration = details?.effectiveDurationSeconds()?.takeIf { it > 0 }?.let { seconds ->
        val minutes = seconds / 60
        val remainder = seconds % 60
        when {
            minutes > 0 && remainder > 0 -> "${minutes}m ${remainder}s"
            minutes > 0 -> "${minutes} min"
            else -> "${remainder}s"
        }
    }
    return listOfNotNull("Espacio cardio", mode, duration).joinToString(" · ")
}

/**
 * Filas del cajón de opciones de un ejercicio de fuerza (D1): Historial, Etiquetas, Editar, Reemplazar, Agregar otro, Reordenar,
 * Crear superserie y Omitir, en el mismo orden y con las mismas filas de dos acciones. Las acciones llegan por lambdas; el anfitrión
 * decide qué hace cada una.
 */
@Composable
internal fun ExerciseOptionsStrengthRows(
    onHistory: () -> Unit,
    onTags: () -> Unit,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onAddOther: () -> Unit,
    onReorder: () -> Unit,
    onCreateSuperset: () -> Unit,
    onSkip: () -> Unit,
) {
    OptionsRow {
        ContextActionButton("Historial", KpknLineIcons.History, onHistory, Modifier.weight(1f).fillMaxHeight())
        ContextActionButton("Etiquetas", KpknLineIcons.Tag, onTags, Modifier.weight(1f).fillMaxHeight())
    }
    OptionsRow {
        ContextActionButton("Editar", KpknLineIcons.Edit, onEdit, Modifier.weight(1f).fillMaxHeight())
        ContextActionButton("Reemplazar", KpknLineIcons.Swap, onReplace, Modifier.weight(1f).fillMaxHeight())
    }
    OptionsRow {
        ContextActionButton("Agregar otro", KpknLineIcons.Plus, onAddOther, Modifier.weight(1f).fillMaxHeight())
        ContextActionButton("Reordenar", KpknLineIcons.Reorder, onReorder, Modifier.weight(1f).fillMaxHeight())
    }
    OptionsRow {
        ContextActionButton("Crear superserie", KpknLineIcons.Link, onCreateSuperset, Modifier.weight(1f).fillMaxHeight())
        ContextActionButton("Omitir", KpknLineIcons.Close, onSkip, Modifier.weight(1f).fillMaxHeight(), danger = true)
    }
}

/**
 * 1RM estimado con el PR que se escribe en el cajón de editar (LR1d). Pura: la prueba es WorkoutEditSeriesDecimalTest.
 */
internal fun localPrEstimated1RM(weightText: String, repsText: String, trainingMode: TrainingMode): Double? {
    val w = weightText.replace(',', '.').toDoubleOrNull()
    val r = repsText.toIntOrNull()
    if (w == null || w <= 0 || r == null || r <= 0) return null
    return if (trainingMode == TrainingMode.TIME || trainingMode == TrainingMode.DISTANCE || trainingMode == TrainingMode.CUSTOM) {
        calculateGeneralizedCapacity(w, r.toDouble())
    } else {
        calculateHybrid1RM(w, r)
    }
}

/** Filas del cajón de opciones de un ejercicio de cardio (D1): resumen, «Editar cardio», «Reemplazar» y «Omitir». */
@Composable
internal fun ExerciseOptionsCardioRows(
    summary: String,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(
        summary,
        style = KpknType.caption,
        color = WizardColors.textMuted,
    )
    OptionsRow {
        ContextActionButton("Editar cardio", KpknLineIcons.Edit, onEdit, Modifier.weight(1f).fillMaxHeight())
        ContextActionButton("Reemplazar", KpknLineIcons.Swap, onReplace, Modifier.weight(1f).fillMaxHeight())
    }
    ContextActionButton("Omitir", KpknLineIcons.Close, onSkip, Modifier.fillMaxWidth(), danger = true)
}

/**
 * Diálogos de reordenar ejercicios y de cambio de grupo (D13 y D15). Antes vivían dentro de [WorkoutStructureSheetsHost]: la
 * conducta no cambia; se separan para poder pintarlos con datos de ejemplo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutReorderSheets(
    state: WorkoutStructureSheetsState,
    visibleExercises: List<Exercise>,
    originalExercisePartMap: Map<String, String>,
    onApplyReorder: (orderedIds: List<String>, partMap: Map<String, String>, isGlobal: Boolean) -> Unit,
    /** La sesión de hoy: con ella, el aviso de «Cambio de grupo» sale de [LiveSessionReorder], la misma regla que aplica el orden. */
    session: Session? = null,
) {
    fun closeReorderSheet() {
        state.showReorderSheet = false
        state.reorderSheetExerciseIds = emptyList()
        state.showReorderCrossBoundaryConfirm = false
        state.reorderCrossBoundaryMessages = emptyList()
        state.pendingGlobalReorderIds = emptyList()
    }

    if (state.showReorderSheet) {
        val reorderExerciseLookup = remember(visibleExercises) { visibleExercises.associateBy { it.id } }

        // Una superserie es una unidad (LV4, 10.1): al llegar al borde de su bloque, la fila salta el bloque entero.
        fun moveReorderItem(fromIndex: Int, delta: Int) {
            if (state.reorderSheetExerciseIds.isEmpty() || fromIndex !in state.reorderSheetExerciseIds.indices) return
            val moved = LiveSessionReorder.moveUnit(
                order = state.reorderSheetExerciseIds,
                groupOf = { id -> reorderExerciseLookup[id]?.supersetGroupRefOrLegacyId() },
                fromIndex = fromIndex,
                delta = delta,
            )
            if (moved != state.reorderSheetExerciseIds) state.reorderSheetExerciseIds = moved
        }

        fun detectCrossBoundaryMoves(orderedIds: List<String>, partMap: Map<String, String>): List<String> {
            if (orderedIds.size < 2) return emptyList()
            if (session != null) {
                // Los ejercicios que, para aplicar el orden pedido, pasan de un grupo a otro (la misma regla que aplica el orden).
                return LiveSessionReorder.plan(session, orderedIds).groupChanges.map { change ->
                    val name = reorderExerciseLookup[change.exerciseId]?.name ?: change.exerciseId
                    "$name (${change.fromGroup ?: "Sesión Principal"} → ${change.toGroup ?: "Sesión Principal"})"
                }
            }
            val messages = mutableListOf<String>()
            data class ExBlock(val part: String?, val ids: List<String>)
            val blocks = mutableListOf<ExBlock>()
            var currentPart: String? = orderedIds.firstOrNull()?.let(partMap::get)
            var currentIds = mutableListOf<String>()
            for (id in orderedIds) {
                val p = partMap[id]
                if (p != currentPart && currentIds.isNotEmpty()) {
                    blocks.add(ExBlock(currentPart, currentIds.toList()))
                    currentIds = mutableListOf()
                    currentPart = p
                }
                currentIds.add(id)
            }
            if (currentIds.isNotEmpty()) blocks.add(ExBlock(currentPart, currentIds.toList()))
            for (i in blocks.indices) {
                val block = blocks[i]
                if (block.ids.size != 1) continue
                val prevPart = if (i > 0) blocks[i - 1].part else null
                val nextPart = if (i < blocks.lastIndex) blocks[i + 1].part else null
                if (prevPart != null && nextPart != null && prevPart == nextPart && prevPart != block.part) {
                    val exId = block.ids[0]
                    val exName = reorderExerciseLookup[exId]?.name ?: exId
                    messages.add("$exName (${block.part ?: "Sesión Principal"} → $prevPart)")
                }
            }
            return messages
        }

        KpknAlertDialog(
            onDismissRequest = { closeReorderSheet() },
            title = { Text("Reordenar ejercicios", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Cambia el orden de los ejercicios de hoy.",
                        style = KpknType.body,
                        color = WizardColors.text,
                    )
                    if (state.reorderSheetExerciseIds.isEmpty()) {
                        Text(
                            "No hay ejercicios para mover.",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            itemsIndexed(state.reorderSheetExerciseIds, key = { _, exId -> exId }) { index, exId ->
                                val ex = reorderExerciseLookup[exId]
                                val partName = originalExercisePartMap[exId]
                                if (ex != null) {
                                    KpknModule(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                "${index + 1}",
                                                modifier = Modifier.width(24.dp),
                                                style = KpknType.metricM,
                                                color = WizardColors.text,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    ex.name,
                                                    style = KpknType.bodyStrong,
                                                    color = WizardColors.text,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (partName != null) {
                                                    Text(
                                                        partName,
                                                        style = KpknType.caption,
                                                        color = WizardColors.textMuted,
                                                    )
                                                }
                                            }
                                            // Una flecha apagada no hace nada: moveReorderItem ya ignora el movimiento fuera de la lista.
                                            KpknIconButton(
                                                icon = KpknLineIcons.ChevronUp,
                                                contentDescription = "Subir",
                                                onClick = { moveReorderItem(index, -1) },
                                                modifier = Modifier.graphicsLayer { alpha = if (index > 0) 1f else 0.38f },
                                            )
                                            KpknIconButton(
                                                icon = KpknLineIcons.ChevronDown,
                                                contentDescription = "Bajar",
                                                onClick = { moveReorderItem(index, 1) },
                                                modifier = Modifier.graphicsLayer {
                                                    alpha = if (index < state.reorderSheetExerciseIds.lastIndex) 1f else 0.38f
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Guardar",
                    onClick = {
                        val crossBoundaryMessages = detectCrossBoundaryMoves(state.reorderSheetExerciseIds, originalExercisePartMap)
                        if (crossBoundaryMessages.isEmpty()) {
                            onApplyReorder(state.reorderSheetExerciseIds, originalExercisePartMap, false)
                            closeReorderSheet()
                        } else {
                            state.reorderCrossBoundaryMessages = crossBoundaryMessages
                            state.pendingGlobalReorderIds = state.reorderSheetExerciseIds
                            state.showReorderCrossBoundaryConfirm = true
                        }
                    },
                    enabled = state.reorderSheetExerciseIds.size >= 2,
                )
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { closeReorderSheet() })
            },
        )
    }

    if (state.showReorderCrossBoundaryConfirm) {
        KpknAlertDialog(
            onDismissRequest = {
                state.showReorderCrossBoundaryConfirm = false
                state.reorderCrossBoundaryMessages = emptyList()
                state.pendingGlobalReorderIds = emptyList()
            },
            title = { Text("Cambio de grupo", style = KpknType.dialogTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Los siguientes ejercicios se saldrán del grupo en el que estaban:",
                        style = KpknType.body,
                        color = WizardColors.text,
                    )
                    state.reorderCrossBoundaryMessages.forEach { message ->
                        // Antes, un Surface errorContainer; ahora, un módulo con el aviso en `danger`.
                        KpknModule(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            accent = WizardColors.danger,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "⚠ $message",
                                style = KpknType.body,
                                color = WizardColors.danger,
                            )
                        }
                    }
                    Text(
                        "¿Estás seguro de mantener este orden? Los ejercicios cambiarán al grupo donde fueron colocados.",
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                }
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Guardar de todas formas",
                    onClick = {
                        onApplyReorder(state.pendingGlobalReorderIds, originalExercisePartMap, true)
                        closeReorderSheet()
                    },
                )
            },
            dismissButton = {
                KpknTextAction(
                    text = "Cancelar",
                    onClick = {
                        state.showReorderCrossBoundaryConfirm = false
                        state.reorderCrossBoundaryMessages = emptyList()
                        state.pendingGlobalReorderIds = emptyList()
                    },
                )
            },
        )
    }
}

/**
 * Hoja de crear superserie (D13: «Crear superserie» y «Agregar a superserie»). Antes vivía dentro de [WorkoutStructureSheetsHost];
 * la conducta no cambia.
 */
@Composable
internal fun WorkoutSupersetCreatorSheet(
    state: WorkoutStructureSheetsState,
    modeSession: Session,
    renderedParts: List<SessionPart>,
    visibleExercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    hasCatalogAction: Boolean,
    onCreateSuperset: (exerciseIds: List<String>, partId: String?, restBetween: Int, restAfter: Int, rounds: Int) -> Unit,
) {
    if (state.showWorkoutSupersetCreator) {
        val supersetAnchorId = state.workoutSupersetSelectedExerciseId
        val supersetAnchorPart = remember(supersetAnchorId, renderedParts) {
            supersetAnchorId?.let { anchorId -> renderedParts.firstOrNull { part -> part.exercises.any { it.id == anchorId } } }
        }
        val supersetAnchorPartId = supersetAnchorPart?.id?.takeIf { it != "default" }
        val supersetAnchorGroupId = supersetAnchorId
            ?.let { anchorId -> modeSession.allExercises().firstOrNull { it.id == anchorId } }
            ?.supersetGroupRefOrLegacyId()
        val supersetAnchorMemberIds = remember(supersetAnchorGroupId, modeSession) {
            supersetAnchorGroupId
                ?.let { groupId -> SupersetRules.orderedMembers(modeSession, groupId).map { it.id } }
                .orEmpty()
        }
        val supersetCandidateExercises = remember(supersetAnchorId, supersetAnchorPart, supersetAnchorMemberIds, modeSession, visibleExercises, completedSets) {
            val visibleIds = visibleExercises.map { it.id }.toSet()
            modeSession.allExercises().filter { exercise ->
                val completed = exercise.sets.isNotEmpty() && exercise.sets.indices.all { setIdx ->
                    if (exercise.isEffectivelyUnilateral()) {
                        completedSets.containsKey("${exercise.id}_${setIdx}_L") &&
                            completedSets.containsKey("${exercise.id}_${setIdx}_R")
                    } else {
                        completedSets.containsKey("${exercise.id}_$setIdx")
                    }
                }
                exercise.id in visibleIds &&
                    !completed &&
                    (!exercise.isInSuperset() || exercise.id == supersetAnchorId || exercise.id in supersetAnchorMemberIds)
            }
        }
        var supersetSelectedIds by remember(supersetAnchorId, supersetAnchorMemberIds) {
            mutableStateOf(supersetAnchorMemberIds.ifEmpty { listOfNotNull(supersetAnchorId) })
        }
        val supersetDefaults = remember(supersetAnchorId, supersetAnchorGroupId, supersetCandidateExercises, supersetSelectedIds, modeSession) {
            val members = if (supersetAnchorGroupId != null) {
                SupersetRules.orderedMembers(modeSession, supersetAnchorGroupId)
            } else {
                supersetCandidateExercises.filter { it.id in supersetSelectedIds }
            }
            val rounds = modeSession.allSupersetGroups()
                .firstOrNull { it.id == supersetAnchorGroupId }
                ?.rounds
                ?.takeIf { it > 0 }
                ?: members.maxOfOrNull { it.sets.size }?.coerceAtLeast(1)
                ?: 1
            val between = members.firstNotNullOfOrNull { it.supersetRestBetween ?: it.restTime }
                ?.coerceAtLeast(0)
                ?: 60
            val after = members.firstNotNullOfOrNull { it.supersetRestAfter }
                ?.coerceAtLeast(0)
                ?: (members.firstOrNull()?.restTime ?: 120).coerceAtLeast(0)
            Triple(rounds, between, after)
        }
        var roundsText by remember(supersetAnchorId, supersetAnchorGroupId, supersetDefaults.first) {
            mutableStateOf(supersetDefaults.first.toString())
        }
        var restBetweenText by remember(supersetAnchorId, supersetAnchorGroupId, supersetDefaults.second) {
            mutableStateOf(supersetDefaults.second.toString())
        }
        var restAfterText by remember(supersetAnchorId, supersetAnchorGroupId, supersetDefaults.third) {
            mutableStateOf(supersetDefaults.third.toString())
        }
        fun closeWorkoutSupersetCreator() {
            state.showWorkoutSupersetCreator = false
            state.workoutSupersetSelectedExerciseId = null
        }
        KpknAlertDialog(
            onDismissRequest = { closeWorkoutSupersetCreator() },
            title = {
                Text(
                    if (supersetAnchorGroupId == null) "Crear superserie" else "Agregar a superserie",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona ejercicios pendientes de la sesión. La superserie se aplicará solo a este entrenamiento.",
                        style = KpknType.body,
                        color = WizardColors.text,
                    )
                    Text(
                        "Configuración inicial (puedes ajustarla antes de guardar)",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        KpknTextField(
                            value = roundsText,
                            onValueChange = { roundsText = it.filter(Char::isDigit).take(2) },
                            label = "Rondas",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        KpknTextField(
                            value = restBetweenText,
                            onValueChange = { restBetweenText = it.filter(Char::isDigit).take(4) },
                            label = "Entre ejercicios (s)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    KpknTextField(
                        value = restAfterText,
                        onValueChange = { restAfterText = it.filter(Char::isDigit).take(4) },
                        label = "Después de la ronda (s)",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (supersetCandidateExercises.size < 2) {
                        Text(
                            "No hay otro ejercicio disponible en este grupo.",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                        )
                    }
                    supersetCandidateExercises.forEach { ex ->
                        val isSelected = ex.id in supersetSelectedIds
                        val isAnchor = ex.id == supersetAnchorId
                        // Casilla y renglón hacen lo mismo: el límite de 4 ejercicios se aplica a los dos.
                        KpknCheckRow(
                            checked = isSelected,
                            title = ex.name,
                            subtitle = if (isAnchor) "Origen" else null,
                            onClick = {
                                if (isAnchor) return@KpknCheckRow
                                supersetSelectedIds = if (isSelected) {
                                    supersetSelectedIds.filterNot { it == ex.id }
                                } else {
                                    if (supersetSelectedIds.size >= 4) return@KpknCheckRow
                                    supersetSelectedIds + ex.id
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasCatalogAction) {
                        KpknTextAction(
                            text = "Desde catálogo",
                            onClick = {
                                val anchor = supersetAnchorId
                                closeWorkoutSupersetCreator()
                                state.createSupersetFromCatalogAnchorId = anchor
                            },
                        )
                    }
                    KpknPrimaryPill(
                        text = if (supersetAnchorGroupId == null) "Crear superserie" else "Actualizar superserie",
                        onClick = {
                            onCreateSuperset(
                                supersetSelectedIds,
                                supersetAnchorPartId,
                                restBetweenText.toIntOrNull()?.coerceAtLeast(0) ?: supersetDefaults.second,
                                restAfterText.toIntOrNull()?.coerceAtLeast(0) ?: supersetDefaults.third,
                                roundsText.toIntOrNull()?.coerceAtLeast(1) ?: supersetDefaults.first,
                            )
                            closeWorkoutSupersetCreator()
                        },
                        enabled = supersetSelectedIds.size >= 2,
                    )
                }
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { closeWorkoutSupersetCreator() })
            },
        )
    }
}

/**
 * Cajón «Editar series» (D3): modo, unilateral, descanso entre lados, «Carga inteligente» y «Confirmar cambios». Antes vivía dentro
 * de [WorkoutStructureSheetsHost]; la conducta no cambia.
 */
@Composable
internal fun WorkoutEditSeriesSheet(
    state: WorkoutStructureSheetsState,
    modeSession: Session,
    sessionAccentColor: Color,
    bottomHazeState: HazeState,
    onSaveDefinition: (exerciseId: String, draft: Exercise) -> Unit,
) {
    if (state.editSheetExerciseId != null) {
        val editEx = modeSession.allExercises().firstOrNull { it.id == state.editSheetExerciseId }
        if (editEx != null) {
            var draftExercise by remember(state.editSheetExerciseId, editEx) { mutableStateOf(editEx) }
            LaunchedEffect(editEx) {
                if (draftExercise.id == editEx.id && draftExercise == modeSession.allExercises().firstOrNull { it.id == editEx.id }) {
                    draftExercise = editEx
                }
            }

            var showSmartLoadDialog by remember { mutableStateOf(false) }
            var rmInputMode by remember(draftExercise.id) {
                mutableStateOf(if (draftExercise.prFor1RM != null) "pr" else "direct")
            }
            var directRmInput by remember(draftExercise.id, draftExercise.reference1RM) {
                mutableStateOf(formatEditableNumber(draftExercise.reference1RM))
            }
            var prWeightInput by remember(draftExercise.id, draftExercise.prFor1RM?.weight) {
                mutableStateOf(formatEditableNumber(draftExercise.prFor1RM?.weight))
            }
            var prRepsInput by remember(draftExercise.id, draftExercise.prFor1RM?.reps) {
                mutableStateOf(draftExercise.prFor1RM?.reps?.toString().orEmpty())
            }
            val localPrEstimatedRm = remember(prWeightInput, prRepsInput, draftExercise.trainingMode) {
                localPrEstimated1RM(prWeightInput, prRepsInput, draftExercise.trainingMode)
            }
            val resolved1RM = remember(draftExercise.trainingMode, draftExercise.reference1RM, draftExercise.prFor1RM, localPrEstimatedRm) {
                draftExercise.reference1RM ?: localPrEstimatedRm ?: draftExercise.consolidatedWeight?.weightKg
            }

            WorkoutDrawer(
                title = if (draftExercise.cardioDetails != null) "Editar cardio" else "Editar series",
                onDismiss = { state.editSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseSheetSubtitle(draftExercise.name)
                    if (draftExercise.cardioDetails != null) {
                        val cardioDetails = draftExercise.cardioDetails ?: return@Column
                        CardioEditorCard(
                            details = cardioDetails,
                            accentColor = sessionAccentColor,
                            exerciseName = draftExercise.name,
                            onChange = { details ->
                                draftExercise = draftExercise.copy(
                                    cardioDetails = details,
                                    targetDurationMinutes = details.effectiveDurationSeconds().let { (it / 60).coerceAtLeast(1) }.coerceAtLeast(1).takeIf { details.hasIntervals() || details.targetDurationSeconds != null } ?: 0,
                                )
                            },
                        )
                    } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        item {
                            CompactModeSelector(
                                currentMode = draftExercise.trainingMode,
                                accentColor = sessionAccentColor,
                            ) { mode -> draftExercise = draftExercise.copy(trainingMode = mode) }
                        }
                        item {
                            UnilateralModeSelector(
                                mode = draftExercise.unilateralMode,
                                accentColor = sessionAccentColor,
                                onToggleUnilateral = { draftExercise = draftExercise.toggledBilateralUnilateral() },
                            )
                        }
                        if (draftExercise.isEffectivelyUnilateral()) {
                            item {
                                SideOrderChip(
                                    sideOrder = draftExercise.unilateralSideOrder,
                                    accentColor = sessionAccentColor,
                                    onToggle = {
                                        draftExercise = draftExercise.copy(
                                            unilateralSideOrder = if (draftExercise.unilateralSideOrder == UnilateralSideOrder.LEFT_RIGHT) {
                                                UnilateralSideOrder.RIGHT_LEFT
                                            } else {
                                                UnilateralSideOrder.LEFT_RIGHT
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }

                    if (draftExercise.isEffectivelyUnilateral()) {
                        var restBetweenSidesText by remember(draftExercise.id, draftExercise.restBetweenSidesSeconds) {
                            mutableStateOf((draftExercise.restBetweenSidesSeconds ?: 0).toString())
                        }
                        KpknTextField(
                            value = restBetweenSidesText,
                            onValueChange = { raw ->
                                restBetweenSidesText = raw.filter(Char::isDigit).take(4)
                                draftExercise = draftExercise.copy(restBetweenSidesSeconds = restBetweenSidesText.toIntOrNull()?.coerceAtLeast(0))
                            },
                            label = "Descanso entre lados (s)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (draftExercise.cardioDetails == null) {
                        KpknModule(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showSmartLoadDialog = true },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = KpknLineIcons.Training,
                                        contentDescription = null,
                                        tint = sessionAccentColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        "Carga inteligente",
                                        style = KpknType.bodyStrong,
                                        color = WizardColors.text,
                                    )
                                }
                                if (resolved1RM != null && resolved1RM > 0.0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(KpknShape.pill)
                                            .background(KpknSurface.inset, KpknShape.pill)
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            "1RM: ${formatEditableNumber(resolved1RM).replace('.', ',')}\u00A0kg",
                                            style = KpknType.chip,
                                            color = WizardColors.text,
                                        )
                                    }
                                } else {
                                    Text(
                                        "Configurar",
                                        style = KpknType.caption,
                                        color = WizardColors.textMuted,
                                    )
                                }
                            }
                        }
                    }

                    if (showSmartLoadDialog) {
                        ExerciseSmartLoadDialog(
                            exercise = draftExercise,
                            rmInputMode = rmInputMode,
                            onRmInputModeChange = { rmInputMode = it },
                            directRmInput = directRmInput,
                            onDirectRmInputChange = { directRmInput = it },
                            prWeightInput = prWeightInput,
                            onPrWeightInputChange = { prWeightInput = it },
                            prRepsInput = prRepsInput,
                            onPrRepsInputChange = { prRepsInput = it },
                            customUnitInput = draftExercise.customUnit.orEmpty(),
                            localPrEstimatedRm = localPrEstimatedRm,
                            resolved1RM = resolved1RM,
                            onUpdateExercise = { transform ->
                                draftExercise = transform(draftExercise)
                            },
                            onDismiss = { showSmartLoadDialog = false },
                        )
                    }

                    ExerciseSetsCarousel(
                        exercise = draftExercise,
                        reference1RM = resolved1RM,
                        trainingMode = draftExercise.trainingMode,
                        customUnit = draftExercise.customUnit,
                        predictedMetrics = emptyMap(),
                        accentColor = sessionAccentColor,
                        onAddSet = { side ->
                            val lastSet = draftExercise.sets.lastOrNull()
                            val baseTarget = UnilateralTarget(
                                weight = lastSet?.weight,
                                targetReps = lastSet?.targetReps,
                                targetRepsRange = lastSet?.targetRepsRange,
                                targetDuration = lastSet?.targetDuration,
                                targetRPE = lastSet?.targetRPE,
                                targetRIR = lastSet?.targetRIR,
                                intensityMode = lastSet?.intensityMode,
                            )
                            val newSet = ExerciseSet(
                                id = UUID.randomUUID().toString(),
                                targetReps = lastSet?.targetReps,
                                targetRepsRange = lastSet?.targetRepsRange,
                                targetDuration = lastSet?.targetDuration,
                                targetRPE = lastSet?.targetRPE,
                                targetRIR = lastSet?.targetRIR,
                                weight = lastSet?.weight,
                                loadModeV2 = lastSet?.loadModeV2,
                                unitModeV2 = lastSet?.unitModeV2,
                                intensityMode = lastSet?.intensityMode,
                                targetPercentageRM = lastSet?.targetPercentageRM,
                                leftTarget = when (side) {
                                    "left" -> baseTarget
                                    "right" -> null
                                    else -> lastSet?.leftTarget
                                },
                                rightTarget = when (side) {
                                    "right" -> baseTarget
                                    "left" -> null
                                    else -> lastSet?.rightTarget
                                },
                            )
                            draftExercise = draftExercise.copy(sets = draftExercise.sets + newSet)
                        },
                        onUpdateSet = { setId, transform ->
                            draftExercise = draftExercise.copy(
                                sets = draftExercise.sets.map { set ->
                                    if (set.id == setId) transform(set) else set
                                },
                            )
                        },
                        onRemoveSet = { setId ->
                            draftExercise = draftExercise.copy(
                                sets = draftExercise.sets.filterNot { it.id == setId }.ifEmpty {
                                    listOf(ExerciseSet(id = UUID.randomUUID().toString()))
                                },
                            )
                        },
                        onMoveSet = { setId, direction ->
                            val currentIndex = draftExercise.sets.indexOfFirst { it.id == setId }
                            val targetIndex = (currentIndex + direction).coerceIn(0, draftExercise.sets.lastIndex)
                            if (currentIndex >= 0 && currentIndex != targetIndex) {
                                val mutable = draftExercise.sets.toMutableList()
                                val moved = mutable.removeAt(currentIndex)
                                mutable.add(targetIndex, moved)
                                draftExercise = draftExercise.copy(sets = mutable)
                            }
                        },
                    )
                    }

                    KpknPrimaryPill(
                        text = "Confirmar cambios",
                        onClick = {
                            val confirmedDraft = draftExercise
                            onSaveDefinition(editEx.id, confirmedDraft)
                            state.selectedExerciseContextTab = null
                            state.editSheetExerciseId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = KpknLineIcons.Check,
                    )
                }
            }
        } else {
            LaunchedEffect(state.editSheetExerciseId) {
                state.editSheetExerciseId = null
                state.selectedExerciseContextTab = null
            }
        }
    }
}

/** Nombre del ejercicio bajo el título de un cajón (LR1d): Inter en `textMuted`, hasta dos líneas. */
@Composable
internal fun ExerciseSheetSubtitle(exerciseName: String) {
    Text(
        text = exerciseName,
        style = KpknType.body,
        color = WizardColors.textMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
