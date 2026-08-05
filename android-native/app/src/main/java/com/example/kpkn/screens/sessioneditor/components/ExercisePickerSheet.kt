package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.ALL_MUSCLES
import com.example.kpkn.domain.exercises.CATALOG_MOVEMENT_PATTERNS
import com.example.kpkn.domain.exercises.ExerciseCatalogExclusiveFilter
import com.example.kpkn.domain.exercises.ExerciseCatalogSort
import com.example.kpkn.domain.exercises.matchingTechnicalAspectOptions
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.screens.sessioneditor.CatalogSelectionDraftBridge
import com.example.kpkn.screens.sessioneditor.CatalogSelectionWizard
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors

private enum class CatalogFilterBrowse {
    CLOSED,
    REGION,
    CHAIN,
    MUSCLE,
    PATTERN,
}

private fun sortDirectionLabel(
    sortMode: ExerciseCatalogSort,
    ascending: Boolean,
    hasActiveSearch: Boolean = false,
): String = when {
    hasActiveSearch &&
        (sortMode == ExerciseCatalogSort.NAME || sortMode == ExerciseCatalogSort.RELEVANCE) ->
        "Relevancia"
    sortMode == ExerciseCatalogSort.NAME -> if (ascending) "A → Z" else "Z → A"
    else -> if (ascending) "Menos → más" else "Más → menos"
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
    onDismiss: () -> Unit,
    highlightedExerciseId: String? = null,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit = {},
    editingCatalogDefinitionId: String? = null,
    editingCatalogConfigurationId: String? = null,
) {
    val v2Context = LocalContext.current
    val v2Repository = remember(v2Context) { ApprovedAssetExerciseCatalogRepositoryV2(v2Context) }
    LaunchedEffect(v2Repository) { v2Repository.load() }
    // v2 owns the whole surface, including Loading and Error states. The old
    // catalog must never render as a transient fallback while the asset loads.
    ExercisePickerV2Catalog(
        repository = v2Repository,
        query = query,
        onSearch = onSearch,
        editingExisting = editingExisting,
        selectedExercisesIds = selectedExercisesIds,
        onSelect = onSelect,
        onMultiSelect = onMultiSelect,
        onSelectionChange = onSelectionChange,
        onOpenExerciseDetail = onOpenExerciseDetail,
        onCreateSuperset = onCreateSuperset,
        onDismiss = onDismiss,
        initialCatalogDefinitionId = editingCatalogDefinitionId,
        initialCatalogConfigurationId = editingCatalogConfigurationId,
    )
    return
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val fullCatalog = remember(catalog, customExercises) {
        (customExercises + catalog).distinctBy { it.id.lowercase() }
    }

    var sortMode by rememberSaveable { mutableStateOf(ExerciseCatalogSort.NAME) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var filterKey by rememberSaveable { mutableStateOf(ExerciseCatalogExclusiveFilter.None.storageKey) }
    var filterBrowse by rememberSaveable { mutableStateOf(CatalogFilterBrowse.CLOSED) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedInfoExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var variantFlowExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    var aspectsByExerciseId by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    val normalizedQuery = query.trim()

    val exclusiveFilter = remember(filterKey) {
        ExerciseCatalogExclusiveFilter.fromStorageKey(filterKey)
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

    fun aspectsFor(info: ExerciseMuscleInfo): Map<String, String> =
        aspectsByExerciseId[info.id]
            ?: (defaultAspectSelection(info) + matchingTechnicalAspectOptions(info, normalizedQuery))

    fun updateAspects(info: ExerciseMuscleInfo, aspects: Map<String, String>) {
        aspectsByExerciseId = aspectsByExerciseId + (info.id to aspects)
        if (!info.catalogOptionAxes.isNullOrEmpty()) {
            CatalogSelectionDraftBridge.store(
                exerciseDbId = info.id,
                variantName = info.variantName,
                variantGroupId = info.variantGroupId,
                variantGroupName = info.variantGroupName,
                selectedAspects = aspects,
            )
        }
    }

    fun toggleInfo(info: ExerciseMuscleInfo) {
        expandedInfoExerciseId = if (expandedInfoExerciseId == info.id) null else info.id
    }

    fun handleSelect(info: ExerciseMuscleInfo) {
        if (editingExisting) {
            val aspects = aspectsFor(info)
            if (!info.catalogOptionAxes.isNullOrEmpty()) {
                updateAspects(info, aspects)
            }
            onSelect(info)
        } else {
            val selecting = info.id !in selectedExercisesIds
            selectionOrder = if (!selecting) {
                selectionOrder - info.id
            } else {
                selectionOrder + info.id
            }
            if (selecting && !info.catalogOptionAxes.isNullOrEmpty()) {
                updateAspects(info, aspectsFor(info))
            }
            onToggleExerciseSelection(info.id)
        }
    }

    val results = remember(normalizedQuery, fullCatalog, sortMode, sortAscending, exclusiveFilter) {
        filterAndSortExerciseCatalog(
            fullCatalog = fullCatalog,
            normalizedQuery = normalizedQuery,
            sortMode = sortMode,
            exclusiveFilter = exclusiveFilter,
            ascending = sortAscending,
        )
    }
    val matchedAspectOptionsByExerciseId = remember(results, normalizedQuery) {
        results.associate { it.id to matchingTechnicalAspectOptions(it, normalizedQuery) }
    }
    val resultListState = rememberLazyListState()
    var lastScrollIndex by remember { mutableIntStateOf(0) }
    var filtersVisible by rememberSaveable { mutableStateOf(true) }
    val scrollingDown by remember {
        derivedStateOf {
            val index = resultListState.firstVisibleItemIndex
            val offset = resultListState.firstVisibleItemScrollOffset
            index > 0 || offset > 24
        }
    }
    LaunchedEffect(scrollingDown, resultListState.isScrollInProgress) {
        val index = resultListState.firstVisibleItemIndex
        if (index > lastScrollIndex || (index == lastScrollIndex && scrollingDown && resultListState.isScrollInProgress)) {
            filtersVisible = false
        } else if (index < lastScrollIndex || index == 0) {
            filtersVisible = true
        }
        lastScrollIndex = index
    }
    LaunchedEffect(normalizedQuery, sortMode, sortAscending, filterKey) {
        resultListState.scrollToItem(0)
        filtersVisible = true
    }
    val createdCatalog = remember(customExercises) { customExercises.sortedBy { it.name.lowercase() } }
    val highlightedExercise = remember(highlightedExerciseId, fullCatalog) {
        highlightedExerciseId?.let { id -> fullCatalog.firstOrNull { it.id == id } }
    }

    val showSelectionDock = !editingExisting && selectedExercises.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (editingExisting) "Cambiar ejercicio" else "Catálogo",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = KpknSheetTokens.TitleStrong,
                )
                Text(
                    if (results.isEmpty()) "Sin resultados" else "${results.size} ejercicios",
                    style = MaterialTheme.typography.labelSmall,
                    color = KpknSheetTokens.MutedStrong,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        searchExpanded = !searchExpanded
                        filtersVisible = true
                        if (!searchExpanded && query.isNotBlank()) onSearch("")
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (searchExpanded || query.isNotBlank()) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchExpanded) "Cerrar búsqueda" else "Buscar",
                        tint = KpknSheetTokens.Body.copy(alpha = 0.88f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = filtersVisible || searchExpanded || query.isNotBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = searchExpanded || query.isNotBlank()) {
                    CatalogSearchField(
                        value = query,
                        onValueChange = onSearch,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Buscar por nombre, músculo o equipo",
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Ordenar",
                style = MaterialTheme.typography.labelSmall,
                color = KpknSheetTokens.GlassControlLabelMuted,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactCatalogFilterChip(
                    selected = sortMode == ExerciseCatalogSort.NAME ||
                        sortMode == ExerciseCatalogSort.RELEVANCE,
                    onClick = {
                        if (sortMode == ExerciseCatalogSort.NAME ||
                            sortMode == ExerciseCatalogSort.RELEVANCE
                        ) {
                            // While searching, relevance order is fixed (no A↔Z flip).
                            if (normalizedQuery.isBlank()) {
                                sortAscending = !sortAscending
                            }
                        } else {
                            sortMode = ExerciseCatalogSort.NAME
                            sortAscending = true
                        }
                    },
                    label = if (normalizedQuery.isNotBlank()) "Relevancia" else "Alfabético",
                )
                CompactCatalogFilterChip(
                    selected = sortMode == ExerciseCatalogSort.FATIGUE_HIGH,
                    onClick = {
                        if (sortMode == ExerciseCatalogSort.FATIGUE_HIGH) {
                            sortAscending = !sortAscending
                        } else {
                            sortMode = ExerciseCatalogSort.FATIGUE_HIGH
                            sortAscending = false
                        }
                    },
                    label = "Fatiga",
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val relevanceLocked = normalizedQuery.isNotBlank() &&
                            (sortMode == ExerciseCatalogSort.NAME ||
                                sortMode == ExerciseCatalogSort.RELEVANCE)
                        if (!relevanceLocked) {
                            sortAscending = !sortAscending
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = KpknSheetTokens.Body,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        sortDirectionLabel(
                            sortMode = sortMode,
                            ascending = sortAscending,
                            hasActiveSearch = normalizedQuery.isNotBlank(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = KpknSheetTokens.Body,
                    )
                }
            }

            Text(
                "Filtros",
                style = MaterialTheme.typography.labelSmall,
                color = KpknSheetTokens.ControlLabelMuted,
                fontWeight = FontWeight.Bold,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    CompactCatalogFilterChip(
                        selected = exclusiveFilter is ExerciseCatalogExclusiveFilter.None &&
                            filterBrowse == CatalogFilterBrowse.CLOSED,
                        onClick = {
                            filterKey = ExerciseCatalogExclusiveFilter.None.storageKey
                            filterBrowse = CatalogFilterBrowse.CLOSED
                        },
                        label = "Todos",
                    )
                }
                item {
                    CompactCatalogFilterChip(
                        selected = filterBrowse == CatalogFilterBrowse.REGION ||
                            exclusiveFilter is ExerciseCatalogExclusiveFilter.UpperBody ||
                            exclusiveFilter is ExerciseCatalogExclusiveFilter.LowerBody,
                        onClick = {
                            filterBrowse = if (filterBrowse == CatalogFilterBrowse.REGION) {
                                CatalogFilterBrowse.CLOSED
                            } else {
                                CatalogFilterBrowse.REGION
                            }
                        },
                        label = when (exclusiveFilter) {
                            ExerciseCatalogExclusiveFilter.UpperBody,
                            ExerciseCatalogExclusiveFilter.LowerBody,
                            -> exclusiveFilter.label
                            else -> "Región"
                        },
                    )
                }
                item {
                    CompactCatalogFilterChip(
                        selected = filterBrowse == CatalogFilterBrowse.CHAIN ||
                            exclusiveFilter is ExerciseCatalogExclusiveFilter.AnteriorChain ||
                            exclusiveFilter is ExerciseCatalogExclusiveFilter.PosteriorChain,
                        onClick = {
                            filterBrowse = if (filterBrowse == CatalogFilterBrowse.CHAIN) {
                                CatalogFilterBrowse.CLOSED
                            } else {
                                CatalogFilterBrowse.CHAIN
                            }
                        },
                        label = when (exclusiveFilter) {
                            ExerciseCatalogExclusiveFilter.AnteriorChain,
                            ExerciseCatalogExclusiveFilter.PosteriorChain,
                            -> exclusiveFilter.label
                            else -> "Cadena"
                        },
                    )
                }
                item {
                    CompactCatalogFilterChip(
                        selected = filterBrowse == CatalogFilterBrowse.MUSCLE ||
                            exclusiveFilter is ExerciseCatalogExclusiveFilter.Muscle,
                        onClick = {
                            filterBrowse = if (filterBrowse == CatalogFilterBrowse.MUSCLE) {
                                CatalogFilterBrowse.CLOSED
                            } else {
                                CatalogFilterBrowse.MUSCLE
                            }
                        },
                        label = when (exclusiveFilter) {
                            is ExerciseCatalogExclusiveFilter.Muscle -> exclusiveFilter.label
                            else -> "Músculo"
                        },
                    )
                }
                item {
                    CompactCatalogFilterChip(
                        selected = filterBrowse == CatalogFilterBrowse.PATTERN ||
                            exclusiveFilter is ExerciseCatalogExclusiveFilter.MovementPattern,
                        onClick = {
                            filterBrowse = if (filterBrowse == CatalogFilterBrowse.PATTERN) {
                                CatalogFilterBrowse.CLOSED
                            } else {
                                CatalogFilterBrowse.PATTERN
                            }
                        },
                        label = when (exclusiveFilter) {
                            is ExerciseCatalogExclusiveFilter.MovementPattern -> exclusiveFilter.label
                            else -> "Patrón"
                        },
                    )
                }
            }

            AnimatedVisibility(visible = filterBrowse != CatalogFilterBrowse.CLOSED) {
                when (filterBrowse) {
                    CatalogFilterBrowse.REGION -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(
                                listOf(
                                    ExerciseCatalogExclusiveFilter.UpperBody,
                                    ExerciseCatalogExclusiveFilter.LowerBody,
                                ),
                            ) { option ->
                                CompactCatalogFilterChip(
                                    selected = filterKey == option.storageKey,
                                    onClick = {
                                        filterKey = option.storageKey
                                        filterBrowse = CatalogFilterBrowse.CLOSED
                                    },
                                    label = option.label,
                                )
                            }
                        }
                    }
                    CatalogFilterBrowse.CHAIN -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(
                                listOf(
                                    ExerciseCatalogExclusiveFilter.AnteriorChain,
                                    ExerciseCatalogExclusiveFilter.PosteriorChain,
                                ),
                            ) { option ->
                                CompactCatalogFilterChip(
                                    selected = filterKey == option.storageKey,
                                    onClick = {
                                        filterKey = option.storageKey
                                        filterBrowse = CatalogFilterBrowse.CLOSED
                                    },
                                    label = option.label,
                                )
                            }
                        }
                    }
                    CatalogFilterBrowse.MUSCLE -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ALL_MUSCLES, key = { it.canonicalName }) { muscle ->
                                val option = ExerciseCatalogExclusiveFilter.Muscle(
                                    muscle.canonicalName,
                                    muscle.displayName,
                                )
                                CompactCatalogFilterChip(
                                    selected = filterKey == option.storageKey,
                                    onClick = {
                                        filterKey = option.storageKey
                                        filterBrowse = CatalogFilterBrowse.CLOSED
                                    },
                                    label = muscle.displayName,
                                )
                            }
                        }
                    }
                    CatalogFilterBrowse.PATTERN -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(CATALOG_MOVEMENT_PATTERNS) { pattern ->
                                val option = ExerciseCatalogExclusiveFilter.MovementPattern(pattern)
                                CompactCatalogFilterChip(
                                    selected = filterKey == option.storageKey,
                                    onClick = {
                                        filterKey = option.storageKey
                                        filterBrowse = CatalogFilterBrowse.CLOSED
                                    },
                                    label = pattern,
                                )
                            }
                        }
                    }
                    CatalogFilterBrowse.CLOSED -> Unit
                }
            }

            if (exclusiveFilter !is ExerciseCatalogExclusiveFilter.None) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Activo: ${exclusiveFilter.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = KpknSheetTokens.Body,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = {
                            filterKey = ExerciseCatalogExclusiveFilter.None.storageKey
                            filterBrowse = CatalogFilterBrowse.CLOSED
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Limpiar", color = KpknSheetTokens.Body)
                    }
                }
            }
        }
        } // filters column
        } // AnimatedVisibility filters

        LazyColumn(
            state = resultListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (highlightedExercise != null ||
                (createdCatalog.isNotEmpty() && normalizedQuery.isBlank() && exclusiveFilter is ExerciseCatalogExclusiveFilter.None)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Creados por ti",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = KpknSheetTokens.Body,
                        )
                        highlightedExercise?.let { info ->
                         ExercisePickerDetailedCard(
                             info = info,
                             isSelected = info.id in selectedExercisesIds,
                             onSelect = { handleSelect(info) },
                             isInfoExpanded = expandedInfoExerciseId == info.id,
                             onToggleInfo = { toggleInfo(info) },
                             onOpenExerciseDetail = { onOpenExerciseDetail(info.id) },
                             showAspects = editingExisting,
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
                                     isInfoExpanded = expandedInfoExerciseId == info.id,
                                     onToggleInfo = { toggleInfo(info) },
                                     onOpenExerciseDetail = { onOpenExerciseDetail(info.id) },
                                     showAspects = editingExisting,
                                 )
                            }
                    }
                }
            }
            items(results, key = { it.id }) { info ->
                ExercisePickerDetailedCard(
                    info = info,
                    isSelected = info.id in selectedExercisesIds,
                    onSelect = { handleSelect(info) },
                    isInfoExpanded = expandedInfoExerciseId == info.id,
                    onToggleInfo = { toggleInfo(info) },
                    onOpenExerciseDetail = { onOpenExerciseDetail(info.id) },
                    onOpenVariantFlow = { variantFlowExercise = info },
                    selectedAspects = aspectsFor(info),
                     highlightedAspectOptions = matchedAspectOptionsByExerciseId[info.id].orEmpty(),
                     onAspectsChange = { updateAspects(info, it) },
                     showAspects = editingExisting,
                 )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        if (showSelectionDock) {
            ExercisePickerSelectionDock(
                selectedExercises = selectedExercises,
                onRemove = { id ->
                    selectionOrder = selectionOrder - id
                    onToggleExerciseSelection(id)
                },
                onCreateSuperset = onCreateSuperset,
                onClearExerciseSelection = onClearExerciseSelection,
                onMultiSelect = onMultiSelect,
            )
        }
    }

    variantFlowExercise?.let { exercise ->
        CatalogSelectionWizard(
            initialExercise = exercise,
            onConfirm = { selectedVariant, selectedAspects ->
                CatalogSelectionDraftBridge.store(
                    exerciseDbId = selectedVariant.id,
                    variantName = selectedVariant.variantName,
                    variantGroupId = selectedVariant.variantGroupId,
                    variantGroupName = selectedVariant.variantGroupName,
                    selectedAspects = selectedAspects,
                )
                if (editingExisting) {
                    onSelect(selectedVariant)
                } else if (selectedVariant.id !in selectedExercisesIds) {
                    selectionOrder = selectionOrder + selectedVariant.id
                    onToggleExerciseSelection(selectedVariant.id)
                }
                variantFlowExercise = null
            },
            onDismiss = { variantFlowExercise = null },
        )
    }
}
