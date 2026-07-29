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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.ALL_MUSCLES
import com.example.kpkn.domain.exercises.CATALOG_MOVEMENT_PATTERNS
import com.example.kpkn.domain.exercises.ExerciseCatalogExclusiveFilter
import com.example.kpkn.domain.exercises.ExerciseCatalogSort
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.screens.sessioneditor.VariantFlowResultCache
import com.example.kpkn.screens.sessioneditor.VariantFlowSheet
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CatalogFilterBrowse {
    CLOSED,
    REGION,
    CHAIN,
    MUSCLE,
    PATTERN,
}

private fun sortDirectionLabel(sortMode: ExerciseCatalogSort, ascending: Boolean): String = when (sortMode) {
    ExerciseCatalogSort.NAME -> if (ascending) "A → Z" else "Z → A"
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
    onOpenExerciseCreator: () -> Unit,
    onDismiss: () -> Unit,
    highlightedExerciseId: String? = null,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit = {},
) {
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val fullCatalog = remember(catalog, customExercises) {
        (customExercises + catalog).distinctBy { it.id.lowercase() }
    }

    var sortMode by rememberSaveable { mutableStateOf(ExerciseCatalogSort.NAME) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var filterKey by rememberSaveable { mutableStateOf(ExerciseCatalogExclusiveFilter.None.storageKey) }
    var filterBrowse by rememberSaveable { mutableStateOf(CatalogFilterBrowse.CLOSED) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var infoExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var variantFlowExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }

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

    val normalizedQuery = query.trim()
    var results by remember { mutableStateOf<List<ExerciseMuscleInfo>>(emptyList()) }
    LaunchedEffect(query, fullCatalog, sortMode, sortAscending, filterKey) {
        results = withContext(Dispatchers.Default) {
            filterAndSortExerciseCatalog(
                fullCatalog = fullCatalog,
                normalizedQuery = normalizedQuery,
                sortMode = sortMode,
                exclusiveFilter = exclusiveFilter,
                ascending = sortAscending,
            )
        }
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

    val infoExercise = remember(infoExerciseId, fullCatalog) { fullCatalog.firstOrNull { it.id == infoExerciseId } }
    val discomfortByExercise = remember(workoutLogs) { discomfortCountsByExercise(workoutLogs) }
    val createdCatalog = remember(customExercises) { customExercises.sortedBy { it.name.lowercase() } }
    val highlightedExercise = remember(highlightedExerciseId, fullCatalog) {
        highlightedExerciseId?.let { id -> fullCatalog.firstOrNull { it.id == id } }
    }

    val showSelectionDock = !editingExisting && selectedExercises.isNotEmpty()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val pickerMaxHeight = screenHeightDp * 0.78f

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = pickerMaxHeight)
            .then(if (showSelectionDock) Modifier.height(pickerMaxHeight) else Modifier)
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
                FilledTonalButton(
                    onClick = onOpenExerciseCreator,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White.copy(alpha = 0.92f),
                    ),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Crear", style = MaterialTheme.typography.labelSmall)
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
                    selected = sortMode == ExerciseCatalogSort.NAME,
                    onClick = {
                        if (sortMode == ExerciseCatalogSort.NAME) {
                            sortAscending = !sortAscending
                        } else {
                            sortMode = ExerciseCatalogSort.NAME
                            sortAscending = true
                        }
                    },
                    label = "Alfabético",
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
                    onClick = { sortAscending = !sortAscending },
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
                        sortDirectionLabel(sortMode, sortAscending),
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
                .then(
                    if (showSelectionDock) Modifier.weight(1f)
                    else Modifier.heightIn(max = 480.dp),
                ),
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
            items(results, key = { it.id }) { info ->
                ExercisePickerDetailedCard(
                    info = info,
                    isSelected = info.id in selectedExercisesIds,
                    onSelect = { handleSelect(info) },
                    onInfo = { infoExerciseId = info.id },
                    onOpenVariantFlow = { variantFlowExercise = info },
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
        VariantFlowSheet(
            initialExercise = exercise,
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
