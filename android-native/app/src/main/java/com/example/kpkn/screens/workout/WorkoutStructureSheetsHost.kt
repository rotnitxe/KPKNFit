package com.example.kpkn.screens.workout

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Reorder
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.CardioProgramMode
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.UnilateralTarget
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
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.ExerciseSetsCarousel
import com.example.kpkn.screens.sessioneditor.components.CardioEditorCard
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.design.WorkoutSetSettingsBody
import com.example.kpkn.screens.workout.design.WorkoutStructureActionRow
import com.example.kpkn.screens.workout.design.WorkoutStructureDivider
import com.example.kpkn.screens.workout.design.WorkoutSupersetCandidate
import com.example.kpkn.screens.workout.design.WorkoutSupersetCreatorBody
import com.example.kpkn.screens.workout.design.WorkoutSupersetRoundsBody
import com.example.kpkn.screens.workout.design.WorkoutTagSheetBody
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.util.UUID
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
            if (contextExercise?.isCardio == true) {
                val cardioExercise = contextExercise
                Text(
                    cardioContextSummary(cardioExercise),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Edit,
                        title = "Editar cardio",
                        onClick = {
                            state.editSheetExerciseId = cardioExercise.id
                            state.exerciseContextExerciseId = null
                        },
                    )
                    WorkoutStructureDivider()
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Swap,
                        title = "Reemplazar",
                        onClick = {
                            state.replaceCardioTargetExerciseId = cardioExercise.id
                            state.showReplaceCardioPicker = true
                            state.exerciseContextExerciseId = null
                        },
                    )
                    WorkoutStructureDivider()
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.ChevronRight,
                        title = "Omitir",
                        onClick = {
                            viewModel.skipExercise(cardioExercise.id)
                            state.exerciseContextExerciseId = null
                        },
                    )
                }
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
                KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Route,
                        title = "Ir a la superserie",
                        onClick = {
                            viewModel.selectSupersetGroup(contextSupersetGroupId)
                            state.exerciseContextExerciseId = null
                        },
                    )
                    WorkoutStructureDivider()
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Repeat,
                        title = "Rondas",
                        onClick = {
                            state.supersetSettingsGroupId = contextSupersetGroupId
                            state.exerciseContextExerciseId = null
                        },
                    )
                    WorkoutStructureDivider()
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Layers,
                        title = "Agregar de sesión",
                        onClick = {
                            state.workoutSupersetSelectedExerciseId = contextExercise.id
                            state.showWorkoutSupersetCreator = true
                            state.exerciseContextExerciseId = null
                        },
                    )
                    WorkoutStructureDivider()
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Search,
                        title = "Agregar del catálogo",
                        onClick = {
                            state.addCatalogToSupersetGroupId = contextSupersetGroupId
                            state.addCatalogSearchQuery = ""
                            state.addCatalogSelectedIds = emptySet()
                            state.exerciseContextExerciseId = null
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknModuleHeader(label = "Miembros")
                    KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        members.forEachIndexed { index, member ->
                            if (index > 0) WorkoutStructureDivider()
                            WorkoutStructureActionRow(
                                icon = KpknLineIcons.Training,
                                title = member.name,
                                onClick = {
                                    state.exerciseContextForceMemberActions = true
                                    state.exerciseContextExerciseId = member.id
                                },
                            )
                        }
                    }
                }
                KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    WorkoutStructureActionRow(
                        icon = KpknLineIcons.Unlink,
                        title = "Disolver superserie",
                        destructive = true,
                        onClick = {
                            viewModel.dissolveLiveSuperset(contextSupersetGroupId, preferredExerciseId = contextExercise.id)
                            state.exerciseContextExerciseId = null
                        },
                    )
                }
            } else {
                val exercise = contextExercise
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknModuleHeader(label = "Este ejercicio")
                    KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.History,
                            title = "Historial",
                            onClick = {
                                exercise?.let { viewModel.showHistoryForExercise(it) }
                                state.exerciseContextExerciseId = null
                            },
                        )
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Tag,
                            title = "Etiquetas",
                            onClick = {
                                if (exercise != null && exercise.id == currentExercise?.id) {
                                    state.requestLiveTagList = true
                                } else {
                                    state.tagSheetExerciseId = exercise?.id
                                }
                                state.exerciseContextExerciseId = null
                            },
                        )
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Edit,
                            title = "Editar",
                            onClick = {
                                state.editSheetExerciseId = exercise?.id
                                state.exerciseContextExerciseId = null
                            },
                        )
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Swap,
                            title = "Reemplazar",
                            onClick = {
                                state.replaceTargetExerciseId = exercise?.id
                                state.replaceSearchQuery = if (exercise?.catalogDefinitionId == null) exercise?.name.orEmpty() else ""
                                state.showReplaceExercisePicker = true
                                state.exerciseContextExerciseId = null
                            },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknModuleHeader(label = "La sesión")
                    KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Plus,
                            title = "Agregar otro",
                            onClick = {
                                state.addExerciseAfterId = exercise?.id
                                state.addExerciseSearchQuery = ""
                                state.addExerciseSelectedIds = emptySet()
                                state.exerciseContextExerciseId = null
                            },
                        )
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Reorder,
                            title = "Reordenar",
                            onClick = {
                                state.reorderSheetExerciseIds = visibleExercises.map { it.id }
                                state.showReorderSheet = true
                                state.exerciseContextExerciseId = null
                            },
                        )
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.Link,
                            title = "Crear superserie",
                            onClick = {
                                state.workoutSupersetSelectedExerciseId = exercise?.id
                                state.showWorkoutSupersetCreator = true
                                state.exerciseContextExerciseId = null
                            },
                        )
                        WorkoutStructureDivider()
                        WorkoutStructureActionRow(
                            icon = KpknLineIcons.ChevronRight,
                            title = "Omitir",
                            onClick = {
                                viewModel.skipExercise(exerciseId)
                                state.exerciseContextExerciseId = null
                            },
                        )
                    }
                }
            }
        }
    }

    state.supersetSettingsGroupId?.let { groupId ->
        val group = modeSession.allSupersetGroups().firstOrNull { it.id == groupId }
        if (group == null) {
            state.supersetSettingsGroupId = null
        } else {
            WorkoutDrawer(
                title = "Rondas y descansos",
                onDismiss = { state.supersetSettingsGroupId = null },
                hazeState = bottomHazeState,
            ) {
                WorkoutSupersetRoundsBody(
                    rounds = group.rounds ?: SupersetRules.roundCount(modeSession, groupId),
                    restBetween = group.restBetweenExercises,
                    restAfter = group.restAfterSuperset,
                    onSave = { rounds, restBetween, restAfter ->
                        viewModel.updateLiveSupersetRest(
                            groupId = groupId,
                            restBetween = restBetween,
                            restAfter = restAfter,
                            rounds = rounds,
                        )
                        state.supersetSettingsGroupId = null
                    },
                    onCancel = { state.supersetSettingsGroupId = null },
                )
            }
        }
    }

    fun closeReorderSheet() {
        state.showReorderSheet = false
        state.reorderSheetExerciseIds = emptyList()
        state.showReorderCrossBoundaryConfirm = false
        state.reorderCrossBoundaryMessages = emptyList()
        state.pendingGlobalReorderIds = emptyList()
    }

    if (state.showReorderSheet) {
        fun moveReorderItem(fromIndex: Int, delta: Int) {
            if (state.reorderSheetExerciseIds.isEmpty() || fromIndex !in state.reorderSheetExerciseIds.indices) return
            val targetIndex = (fromIndex + delta).coerceIn(0, state.reorderSheetExerciseIds.lastIndex)
            if (targetIndex == fromIndex) return
            state.reorderSheetExerciseIds = state.reorderSheetExerciseIds.toMutableList().also { list ->
                val moved = list.removeAt(fromIndex)
                list.add(targetIndex, moved)
            }
        }

        val reorderExerciseLookup = remember(visibleExercises) { visibleExercises.associateBy { it.id } }

        fun detectCrossBoundaryMoves(orderedIds: List<String>, partMap: Map<String, String>): List<String> {
            if (orderedIds.size < 2) return emptyList()
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
            title = { Text("Reordenar ejercicios", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Reordena todos los ejercicios globalmente.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.reorderSheetExerciseIds.isEmpty()) {
                        Text(
                            "No hay ejercicios para mover.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            itemsIndexed(state.reorderSheetExerciseIds, key = { _, exId -> exId }) { index, exId ->
                                val ex = reorderExerciseLookup[exId]
                                val partName = originalExercisePartMap[exId]
                                if (ex != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("${index + 1}", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Black)
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ex.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (partName != null) {
                                                    Text(
                                                        partName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { moveReorderItem(index, -1) },
                                                enabled = index > 0,
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, null)
                                            }
                                            IconButton(
                                                onClick = { moveReorderItem(index, 1) },
                                                enabled = index < state.reorderSheetExerciseIds.lastIndex,
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowDown, null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val crossBoundaryMessages = detectCrossBoundaryMoves(state.reorderSheetExerciseIds, originalExercisePartMap)
                        if (crossBoundaryMessages.isEmpty()) {
                            viewModel.applyReorderAndPromptPersistence(state.reorderSheetExerciseIds, originalExercisePartMap, false)
                            closeReorderSheet()
                        } else {
                            state.reorderCrossBoundaryMessages = crossBoundaryMessages
                            state.pendingGlobalReorderIds = state.reorderSheetExerciseIds
                            state.showReorderCrossBoundaryConfirm = true
                        }
                    },
                    enabled = state.reorderSheetExerciseIds.size >= 2,
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { closeReorderSheet() }) { Text("Cancelar") }
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
            title = { Text("Cambio de grupo", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Los siguientes ejercicios se saldrán del grupo en el que estaban:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.reorderCrossBoundaryMessages.forEach { message ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Text(
                                "⚠ $message",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Text(
                        "¿Estás seguro de mantener este orden? Los ejercicios cambiarán al grupo donde fueron colocados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyReorderAndPromptPersistence(state.pendingGlobalReorderIds, originalExercisePartMap, true)
                        closeReorderSheet()
                    },
                ) { Text("Guardar de todas formas", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.showReorderCrossBoundaryConfirm = false
                        state.reorderCrossBoundaryMessages = emptyList()
                        state.pendingGlobalReorderIds = emptyList()
                    },
                ) { Text("Cancelar") }
            },
        )
    }

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
        val supersetCandidateExercises = remember(supersetAnchorId, supersetAnchorPart, supersetAnchorMemberIds, modeSession, visibleExercises, uiState.completedSets) {
            val visibleIds = visibleExercises.map { it.id }.toSet()
            modeSession.allExercises().filter { exercise ->
                val completed = exercise.sets.isNotEmpty() && exercise.sets.indices.all { setIdx ->
                    if (exercise.isEffectivelyUnilateral()) {
                        uiState.completedSets.containsKey("${exercise.id}_${setIdx}_L") &&
                            uiState.completedSets.containsKey("${exercise.id}_${setIdx}_R")
                    } else {
                        uiState.completedSets.containsKey("${exercise.id}_$setIdx")
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
        fun closeWorkoutSupersetCreator() {
            state.showWorkoutSupersetCreator = false
            state.workoutSupersetSelectedExerciseId = null
        }
        WorkoutDrawer(
            title = if (supersetAnchorGroupId == null) "Crear superserie" else "Agregar a superserie",
            onDismiss = { closeWorkoutSupersetCreator() },
            hazeState = bottomHazeState,
        ) {
            WorkoutSupersetCreatorBody(
                candidates = supersetCandidateExercises.map { WorkoutSupersetCandidate(id = it.id, name = it.name) },
                anchorId = supersetAnchorId,
                selectedIds = supersetSelectedIds,
                onSelectedIdsChange = { supersetSelectedIds = it },
                defaultRounds = supersetDefaults.first,
                defaultRestBetween = supersetDefaults.second,
                defaultRestAfter = supersetDefaults.third,
                confirmLabel = if (supersetAnchorGroupId == null) "Crear superserie" else "Actualizar superserie",
                canOpenCatalog = onOpenCatalog != null,
                onConfirm = { exerciseIds, rounds, restBetween, restAfter ->
                    viewModel.createLiveSuperset(
                        exerciseIds = exerciseIds,
                        partId = supersetAnchorPartId,
                        restBetween = restBetween,
                        restAfter = restAfter,
                        rounds = rounds,
                    )
                    closeWorkoutSupersetCreator()
                },
                onOpenCatalog = {
                    val anchor = supersetAnchorId
                    closeWorkoutSupersetCreator()
                    state.createSupersetFromCatalogAnchorId = anchor
                },
                onDismiss = { closeWorkoutSupersetCreator() },
            )
        }
    }

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
                val w = prWeightInput.toDoubleOrNull()
                val r = prRepsInput.toIntOrNull()
                if (w != null && w > 0 && r != null && r > 0) {
                    if (draftExercise.trainingMode == TrainingMode.TIME || draftExercise.trainingMode == TrainingMode.DISTANCE || draftExercise.trainingMode == TrainingMode.CUSTOM) {
                        calculateGeneralizedCapacity(w, r.toDouble())
                    } else {
                        calculateHybrid1RM(w, r)
                    }
                } else null
            }
            val resolved1RM = remember(draftExercise.trainingMode, draftExercise.reference1RM, draftExercise.prFor1RM, localPrEstimatedRm) {
                draftExercise.reference1RM ?: localPrEstimatedRm ?: draftExercise.consolidatedWeight?.weightKg
            }

            WorkoutDrawer(
                title = "${draftExercise.name} · ${if (draftExercise.cardioDetails != null) "Editar cardio" else "Editar series"}",
                onDismiss = { state.editSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        WorkoutSetSettingsBody(
                            trainingMode = draftExercise.trainingMode,
                            onTrainingModeChange = { mode -> draftExercise = draftExercise.copy(trainingMode = mode) },
                            unilateral = draftExercise.isEffectivelyUnilateral(),
                            onUnilateralChange = { on ->
                                if (on != draftExercise.isEffectivelyUnilateral()) {
                                    draftExercise = draftExercise.toggledBilateralUnilateral()
                                }
                            },
                            sideOrder = draftExercise.unilateralSideOrder,
                            onSideOrderChange = { order -> draftExercise = draftExercise.copy(unilateralSideOrder = order) },
                            restBetweenSidesSeconds = draftExercise.restBetweenSidesSeconds ?: 0,
                            onRestBetweenSidesChange = { seconds ->
                                draftExercise = draftExercise.copy(restBetweenSidesSeconds = seconds.coerceAtLeast(0))
                            },
                            smartLoadSummary = resolved1RM?.takeIf { it > 0.0 }?.let { "1RM: ${formatEditableNumber(it)} kg" },
                            onOpenSmartLoad = { showSmartLoadDialog = true },
                            setsSlot = {
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
                            },
                        )
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
                    }
                    KpknPrimaryPill(
                        text = "Confirmar cambios",
                        onClick = {
                            val confirmedDraft = draftExercise
                            viewModel.updateExerciseDefinition(editEx.id) { current ->
                                confirmedDraft.copy(id = current.id)
                            }
                            state.selectedExerciseContextTab = null
                            state.editSheetExerciseId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
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

    // ─── Tag-only sheet (from "Etiquetas" card) ────────────────────────────
    if (state.tagSheetExerciseId != null) {
        val tagEx = visibleExercises.firstOrNull { it.id == state.tagSheetExerciseId }
        val currentExTag = uiState.exerciseTags[state.tagSheetExerciseId]
        if (tagEx != null) {
            WorkoutDrawer(
                title = "${tagEx.name} · Etiquetas",
                onDismiss = { state.tagSheetExerciseId = null },
                hazeState = bottomHazeState,
            ) {
                WorkoutTagSheetBody(
                    currentTag = currentExTag,
                    userTags = allUserTags,
                    suggestedTag = null,
                    profiles = viewModel.profilesForExercise(tagEx),
                    onTagSet = { tag -> if (tag.isBlank()) viewModel.clearExerciseTag(tagEx.id) else viewModel.setExerciseTag(tagEx.id, tag) },
                    onDismiss = { state.tagSheetExerciseId = null },
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
                title = "${setupEx.name} · Setup",
                onDismiss = { state.setupSheetExerciseId = null },
                hazeState = bottomHazeState,
                ) {
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
            title = { Text("Persistencia de reemplazo", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Cómo quieres guardar este cambio?")
                    options.filter { it != ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING }.forEach { scope ->
                        OutlinedButton(
                            onClick = {
                                viewModel.commitPendingReplacementPersistence(scope)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (scope) {
                                    ReplacementPersistenceScopeV2.SESSION_ONLY -> "Solo esta vez"
                                    ReplacementPersistenceScopeV2.PERMANENT -> "Reemplazar en todo el programa"
                                    ReplacementPersistenceScopeV2.BLOCK_MATCHING -> "Aplicar a todo el bloque"
                                    ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> ""
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPendingReplacementPersistencePrompt()
                    }
                ) { Text("Cancelar") }
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
            title = { Text(title, fontWeight = FontWeight.Black) },
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
                        }
                    )
                    options.filter { it != ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING }.forEach { scope ->
                        OutlinedButton(
                            onClick = {
                                viewModel.commitStructuralPersistence(scope)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when (scope) {
                                    ReplacementPersistenceScopeV2.SESSION_ONLY -> "Solo esta vez"
                                    ReplacementPersistenceScopeV2.PERMANENT -> "Guardar en esta semana"
                                    ReplacementPersistenceScopeV2.BLOCK_MATCHING -> "Aplicar a todo el bloque"
                                    ReplacementPersistenceScopeV2.MESOCYCLE_MATCHING -> ""
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPendingStructuralPersistence()
                    }
                ) { Text("Cancelar") }
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
