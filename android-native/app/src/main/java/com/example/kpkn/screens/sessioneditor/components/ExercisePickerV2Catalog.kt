package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.SmartCreateRequest
import com.example.kpkn.domain.exercises.ExercisePatternDetector
import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import com.example.kpkn.domain.exercises.explainMuscleContribution
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchFiltersV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchHitV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.wikilab.wikilabMuscleColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState

/**
 * The only runtime exercise picker. It deliberately has no legacy fallback:
 * while v2 is loading we show a stable loading surface, never the old catalog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExercisePickerV2Catalog(
    repository: ExerciseCatalogRepositoryV2,
    query: String,
    onSearch: (String) -> Unit,
    editingExisting: Boolean,
    selectedExercisesIds: Set<String>,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)? = null,
    onDismiss: () -> Unit,
    initialCatalogDefinitionId: String? = null,
    initialCatalogConfigurationId: String? = null,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val retryScope = rememberCoroutineScope()
    val glassHaze = LocalHazeState.current

    val catalogShape = RoundedCornerShape(KpknGlass.SheetCornerRadius)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .kpknGlassOrFallback(
                hazeState = glassHaze,
                shape = catalogShape,
                additionalScrim = Color.Black.copy(alpha = 0.12f),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "CATÁLOGO DE EJERCICIOS",
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            // Search is intentionally always visible as a floating pill at the
            // bottom. It must not be hidden behind an icon or disappear while the
            // asset is being decoded.
            when (val current = state) {
                ExerciseCatalogStateV2.Loading -> {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(10.dp))
                                Text("Preparando el catálogo…", color = Color.White.copy(alpha = 0.78f))
                            }
                        }
                        FloatingCatalogSearch(
                            value = query,
                            onValueChange = onSearch,
                            hazeState = glassHaze,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                is ExerciseCatalogStateV2.Error -> {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "No se pudo cargar el catálogo de ejercicios.",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    current.reason,
                                    color = Color.White.copy(alpha = 0.66f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Button(onClick = { retryScope.launch { repository.load() } }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                        FloatingCatalogSearch(
                            value = query,
                            onValueChange = onSearch,
                            hazeState = glassHaze,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                is ExerciseCatalogStateV2.Ready -> {
                    CatalogReadyContent(
                        catalog = current.catalog,
                        repository = repository,
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
                        initialCatalogDefinitionId = initialCatalogDefinitionId,
                        initialCatalogConfigurationId = initialCatalogConfigurationId,
                        hazeState = glassHaze,
                    )
                }
            }
        }
    }
}

/** Píldora flotante de búsqueda con sombra, anclada al borde inferior del área. */
@Composable
private fun FloatingCatalogSearch(
    value: String,
    onValueChange: (String) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .shadow(
                elevation = 36.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.60f),
                clip = false,
            )
            .kpknGlassOrFallback(
                hazeState = hazeState,
                shape = shape,
                additionalScrim = Color.Black.copy(alpha = 0.16f),
            ),
    ) {
        CatalogSearchField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "Buscar ejercicio, implemento o músculo",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
private fun ColumnScope.CatalogReadyContent(
    catalog: ExerciseCatalogV2,
    repository: ExerciseCatalogRepositoryV2,
    query: String,
    onSearch: (String) -> Unit,
    editingExisting: Boolean,
    selectedExercisesIds: Set<String>,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)?,
    onDismiss: () -> Unit,
    initialCatalogDefinitionId: String?,
    initialCatalogConfigurationId: String?,
    hazeState: HazeState?,
) {
    val scope = rememberCoroutineScope()
    val definitionsById = remember(catalog) {
        catalog.families.flatMap { it.definitions }.associateBy { it.id }
    }
    var filterRegion by rememberSaveable { mutableStateOf<String?>(null) }
    var filterMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var muscleFilterExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFilters = remember(filterRegion, filterMuscle) {
        ExerciseSearchFiltersV2(
            bodyRegions = filterRegion?.let { setOf(ExerciseBodyRegionV2.valueOf(it)) }.orEmpty(),
            muscleIds = filterMuscle?.let { setOf(it) }.orEmpty(),
        )
    }
    fun definitionMatchesFilter(definition: ExerciseDefinitionV2): Boolean {
        val configs = definition.configurations
        if (filterRegion != null && configs.none { it.profile.bodyRegion.name == filterRegion }) return false
        if (filterMuscle != null && configs.none { config -> config.profile.primaryMuscles.contains(filterMuscle) }) return false
        return true
    }
    // Debounced search: one fuzzy pass per ~150 ms pause on Dispatchers.Default
    // instead of a synchronous stemming pass on the main thread per keystroke.
    val currentQuery by rememberUpdatedState(query)
    val searchHits by produceState<List<ExerciseSearchHitV2>>(
        initialValue = emptyList(),
        repository,
        searchFilters,
    ) {
        snapshotFlow { currentQuery }
            .debounce(150)
            .collectLatest { committedQuery ->
                value = if (committedQuery.isBlank()) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        repository.search(committedQuery, searchFilters)
                    }
                }
            }
    }
    val definitions = remember(catalog, query, searchHits, filterRegion, filterMuscle) {
        if (query.isBlank()) {
            catalog.families
                .flatMap { it.definitions }
                .filter(::definitionMatchesFilter)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.canonicalName })
        } else {
            // The repository search already orders hits by relevance. Keep that order
            // instead of alphabetizing it again: an exact parent match must
            // never be displaced by a weak token match from a secondary alias
            // (for example, "peso corporal" + "bicho muerto").
            searchHits
                .mapNotNull { hit -> definitionsById[hit.definitionId] }
                .distinctBy { it.id }
        }
    }
    val initialDraftByDefinition = remember(
        catalog,
        initialCatalogDefinitionId,
        initialCatalogConfigurationId,
    ) {
        val definition = when {
            initialCatalogDefinitionId != null -> definitionsById[initialCatalogDefinitionId]
            initialCatalogConfigurationId != null -> definitionsById.values.firstOrNull { definition ->
                definition.configurations.any { it.id == initialCatalogConfigurationId }
            }
            else -> null
        }
        val configuration = definition?.configurations?.firstOrNull { it.id == initialCatalogConfigurationId }
        if (definition != null && configuration != null) mapOf(definition.id to configuration.selectedOptions) else emptyMap()
    }
    val draftByDefinition = remember(initialDraftByDefinition) { mutableStateOf(initialDraftByDefinition) }
    val suggestedDrafts = remember(searchHits, definitionsById) {
        searchHits.mapNotNull { hit ->
            val suggestedId = hit.suggestedConfigurationId ?: return@mapNotNull null
            val def = definitionsById[hit.definitionId] ?: return@mapNotNull null
            val cfg = def.configurations.firstOrNull { it.id == suggestedId } ?: return@mapNotNull null
            def.id to cfg.selectedOptions
        }.toMap()
    }
    // Cuando el usuario escribe "Press Inclinado con Mancuernas", el mejor hit
    // trae su configuración sugerida: se preseleccionan los chips del draft para
    // que al presionar el ejercicio quede agregado al instante (sin expandir).
    LaunchedEffect(suggestedDrafts) {
        if (query.isNotBlank() && suggestedDrafts.isNotEmpty()) {
            val current = draftByDefinition.value
            val additions = suggestedDrafts.filter { (id, options) -> current[id] != options }
            if (additions.isNotEmpty()) {
                draftByDefinition.value = current + additions
            }
        }
    }
    // Chips del título: las opciones coincidentes de la búsqueda, de-duplicadas
    // y solo las más específicas (ej. "Polea Baja" y no "Polea" + "Polea Baja").
    val searchMatchChips = remember(searchHits, definitionsById) {
        searchHits.mapNotNull { hit ->
            val def = definitionsById[hit.definitionId] ?: return@mapNotNull null
            val cfgId = hit.suggestedConfigurationId ?: return@mapNotNull null
            val cfg = def.configurations.firstOrNull { it.id == cfgId } ?: return@mapNotNull null
            def.id to searchMatchChipLabels(cfg, def)
        }.toMap()
    }
    val selectedRows = remember {
        // neverEqualPolicy: reordenar el LinkedHashMap produce un mapa igual
        // (la igualdad de Maps ignora el orden), y sin esto Compose no
        // recompondría y el reorden no se vería.
        mutableStateOf<Map<String, ExerciseMuscleInfo>>(emptyMap(), neverEqualPolicy())
    }
    var expandedDefinitionId by rememberSaveable { mutableStateOf(initialCatalogDefinitionId) }
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val editingCustomExercise = remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    val deletingCustomExercise = remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    val visibleCustomExercises = remember(customExercises, query) {
        customExercises.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
        }
    }

    val exactMatch = remember(query, definitionsById, customExercises) {
        ExerciseMatchLexicon.hasExactMatch(
            query = query,
            definitions = definitionsById.values.toList(),
            customExercises = customExercises,
        )
    }

    val editing = editingCustomExercise.value
    if (editing != null) {
        SmartExerciseEditorPage(
            exercise = editing,
            onSave = { saved ->
                val wasSelected = selectedRows.value.containsKey(saved.id)
                val next = if (wasSelected) selectedRows.value + (saved.id to saved) else selectedRows.value
                selectedRows.value = next
                if (wasSelected) onSelectionChange(next.values.toList())
                editingCustomExercise.value = null
            },
            onClose = { editingCustomExercise.value = null },
        )
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(query, searchHits) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }
    LaunchedEffect(expandedDefinitionId, definitions, visibleCustomExercises) {
        val targetId = expandedDefinitionId ?: return@LaunchedEffect
        val customOffset = if (visibleCustomExercises.isNotEmpty()) visibleCustomExercises.size + 1 else 0
        val indexInDefinitions = definitions.indexOfFirst { it.id == targetId }
        if (indexInDefinitions < 0) return@LaunchedEffect
        val targetIndex = 1 + customOffset + indexInDefinitions
        listState.animateScrollToItem(targetIndex)
        // Settle the expanded card near the upper third of the viewport so the
        // options never sit glued to the top or bottom edge of the sheet.
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            ?: return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val desiredTop = (viewportHeight / 3) - (itemInfo.size / 2)
        val delta = itemInfo.offset - desiredTop
        if (delta != 0) listState.animateScrollBy(delta.toFloat())
    }

    var filterBarHeight by remember { mutableStateOf(0) }
    var selectionPanelHeight by remember { mutableStateOf(84) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // The filter surface floats above the list; cards intentionally continue
            // underneath it so the top edge is revealed by the fade below.
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = with(density) { selectionPanelHeight.toDp() },
            ),
        ) {
            item("result-count") {
                Text(
                    if (query.isBlank()) "Todos los ejercicios" else "Resultados para «$query»",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        if (visibleCustomExercises.isNotEmpty()) {
            item("custom-heading") {
                Text("Ejercicios personalizados", color = Color.White, fontWeight = FontWeight.Bold)
            }
            items(visibleCustomExercises, key = { "custom:" + it.id }, contentType = { "custom" }) { custom ->
                val selected = custom.id in selectedRows.value
                CustomExerciseCard(
                    exercise = custom,
                    selected = selected,
                    editingExisting = editingExisting,
                    hazeState = hazeState,
                    onSelect = { onSelect(custom) },
                    onToggle = {
                        val next = if (selected) selectedRows.value - custom.id else selectedRows.value + (custom.id to custom)
                        selectedRows.value = next
                        onSelectionChange(next.values.toList())
                    },
                    onEdit = { editingCustomExercise.value = custom },
                    onDelete = { deletingCustomExercise.value = custom },
                )
            }
        }

        items(definitions, key = { it.id }, contentType = { "catalog-def" }) { definition ->
            val default = remember(definition) {
                definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
            }
            val selectedOptions = draftByDefinition.value[definition.id]
                ?: default?.selectedOptions.orEmpty()
            // Memoized: typing, selection and expansion recompose every visible
            // card; without remember this matcher would re-run for all of them.
            val compatibility = remember(repository, definition.id, selectedOptions) {
                repository.compatibility(definition.id, selectedOptions)
            }
            val selectedConfigurationId = compatibility.exactConfigurationId
            val selectedConfiguration = remember(definition, selectedConfigurationId) {
                selectedConfigurationId?.let { id ->
                    definition.configurations.firstOrNull { it.id == id }
                }
            }
// Fallback: si el draft del usuario tiene opciones seleccionadas pero no
            // colapsa a una configuración exacta, resolver la mejor configuración
            // compatible en lugar de caer en silencio a la configuración por defecto.
            val resolvedConfigurationId = selectedConfigurationId
                ?: bestMatchingConfigurationId(definition, selectedOptions, default)
            // A compatible partial draft can resolve to exactly one materialized
            // configuration (for example implement=cable implies station=standing).
            // Render all implied axis values as selected so the chips never show
            // an apparently incomplete state while the summary says otherwise.
            val effectiveSelectedOptions = selectedConfiguration?.selectedOptions ?: selectedOptions
            val effectiveConfiguration = selectedConfiguration ?: default
            val isSelected = definition.id in selectedRows.value ||
                selectedConfigurationId?.let { it in selectedExercisesIds } == true
            val isExpanded = expandedDefinitionId == definition.id
            val hasOptions = definition.optionAxes.isNotEmpty()
            val defaultMuscles = remember(default) { default?.profile?.primaryMuscles.orEmpty() }
            val firstAxis = definition.optionAxes.firstOrNull()
            val variantValues = remember(definition, firstAxis) {
                if (firstAxis == null) emptyList()
                else definition.configurations
                    .mapNotNull { it.selectedOptions[firstAxis] }
                    .distinct()
            }
            val visibleVariants = variantValues.take(4)
            val extraVariantCount = (variantValues.size - visibleVariants.size).coerceAtLeast(0)

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF4ADE80).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .kpknGlassOrFallback(
                        hazeState = hazeState,
                        shape = RoundedCornerShape(16.dp),
                        additionalScrim = if (isSelected) {
                            Color(0xFF1E5A44).copy(alpha = 0.38f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable {
                        when {
                            isExpanded -> {
                                expandedDefinitionId = null
                                if (editingExisting) {
                                    exactInfo(catalog, definition, selectedConfigurationId)?.let(onSelect)
                                }
                            }
                            isSelected -> {
                                // Deseleccionar con un toque.
                                val next = selectedRows.value - definition.id
                                selectedRows.value = next
                                onSelectionChange(next.values.toList())
                            }
                            editingExisting -> expandedDefinitionId = definition.id
                            !hasOptions || definition.id in suggestedDrafts -> {
                                // 2B/2C: sin opciones o ya pre-configurado por la búsqueda
                                // → se agrega al instante, sin pasar por opciones.
                                exactInfo(catalog, definition, resolvedConfigurationId)?.let { info ->
                                    val next = selectedRows.value + (definition.id to info)
                                    selectedRows.value = next
                                    onSelectionChange(next.values.toList())
                                }
                            }
                            else -> expandedDefinitionId = definition.id
                        }
                    },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    definition.canonicalName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                // Chips del título: opciones coincidentes de la búsqueda,
                                // pequeñas y de-duplicadas (solo la más específica).
                                if (!isExpanded && query.isNotBlank()) {
                                    val searchChips = searchMatchChips[definition.id].orEmpty()
                                    if (searchChips.isNotEmpty()) {
                                        Spacer(Modifier.width(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            searchChips.take(3).forEach { label ->
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.White.copy(alpha = 0.14f))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                                ) {
                                                    Text(
                                                        label,
                                                        color = Color.White.copy(alpha = 0.92f),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                            if (searchChips.size > 3) {
                                                Text(
                                                    "+${searchChips.size - 3}",
                                                    color = Color.White.copy(alpha = 0.55f),
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        if (defaultMuscles.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                defaultMuscles.joinToString(" · ") { exerciseCatalogMuscleLabel(it) },
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (!isExpanded) {
                        Text(
                            definition.description,
                            color = Color.White.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (visibleVariants.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                visibleVariants.forEach { value ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White.copy(alpha = 0.10f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            exerciseCatalogVariantTagLabel(value, definition.id),
                                            color = Color.White.copy(alpha = 0.82f),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                if (extraVariantCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            "+$extraVariantCount",
                                            color = Color.White.copy(alpha = 0.60f),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isExpanded) {
                        if (hasOptions) {
                            Text(
                                "Selecciona las opciones",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            val axesWithOptions = compatibility.axes.map { axis ->
                                axis to axis.options
                                    .filter { it.enabled || effectiveSelectedOptions[axis.axis] == it.value }
                            }
                            val totalChips = axesWithOptions.sumOf { it.second.size }
                            val compactAxes = totalChips <= 7 && axesWithOptions.size > 1
                            val selectOption: (axis: String, value: String) -> Unit = { axis, value ->
                                val newDraft = draftAfterAxisSelection(
                                    definition = definition,
                                    selectedOptions = selectedOptions,
                                    axis = axis,
                                    value = value,
                                )
                                draftByDefinition.value = draftByDefinition.value + (definition.id to newDraft)
                            }
                            if (compactAxes) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    axesWithOptions.forEach { (axis, visibleOptions) ->
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                "${exerciseCatalogAxisLabel(axis.axis, definition.id)}:",
                                                color = Color.White.copy(alpha = 0.72f),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            visibleOptions.forEach { option ->
                                                AxisChip(
                                                    value = option.value,
                                                    definitionId = definition.id,
                                                    selected = effectiveSelectedOptions[axis.axis] == option.value,
                                                    enabled = option.enabled,
                                                    onClick = { selectOption(axis.axis, option.value) },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                axesWithOptions.forEachIndexed { axisIndex, (axis, visibleOptions) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (visibleOptions.size <= 4) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    "${axisIndex + 1}. ${exerciseCatalogAxisLabel(axis.axis, definition.id)}",
                                                    color = Color.White.copy(alpha = 0.72f),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.widthIn(min = 96.dp),
                                                )
                                                FlowRow(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    visibleOptions.forEach { option ->
                                                        AxisChip(
                                                            value = option.value,
                                                            definitionId = definition.id,
                                                            selected = effectiveSelectedOptions[axis.axis] == option.value,
                                                            enabled = option.enabled,
                                                            onClick = { selectOption(axis.axis, option.value) },
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(
                                                "${axisIndex + 1}. ${exerciseCatalogAxisLabel(axis.axis, definition.id)}",
                                                color = Color.White.copy(alpha = 0.72f),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                visibleOptions.forEach { option ->
                                                    AxisChip(
                                                        value = option.value,
                                                        definitionId = definition.id,
                                                        selected = effectiveSelectedOptions[axis.axis] == option.value,
                                                        enabled = option.enabled,
                                                        onClick = { selectOption(axis.axis, option.value) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        CatalogDescription(
                            definition = definition,
                            configuration = effectiveConfiguration,
                            catalog = catalog,
                        )

                        Text(
                            effectiveConfiguration?.let { exerciseCatalogConfigurationSummary(it, definition.id) }
                                ?: "Configuración incompleta: selecciona los chips restantes",
                            color = if (effectiveConfiguration != null) Color.White else Color(0xFFFFC857),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        // Selección explícita: el usuario elige opciones y confirma.
                        Button(
                            onClick = {
                                val info = if (editingExisting) {
                                    exactInfo(catalog, definition, selectedConfigurationId)
                                } else {
                                    exactInfo(catalog, definition, resolvedConfigurationId)
                                }
                                if (info != null) {
                                    if (editingExisting) {
                                        onSelect(info)
                                    } else {
                                        val next = selectedRows.value + (definition.id to info)
                                        selectedRows.value = next
                                        onSelectionChange(next.values.toList())
                                    }
                                }
                                expandedDefinitionId = null
                            },
                            enabled = resolvedConfigurationId != null || !hasOptions,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (editingExisting) "Usar este ejercicio" else "Seleccionar ejercicio")
                        }
                    }
                }
            }
        }

        if (query.isNotBlank() && !exactMatch) {
            item("smart-create") {
                SmartCreateSuggestion(
                    query = query,
                    hasPartialResults = definitions.isNotEmpty() || visibleCustomExercises.isNotEmpty(),
                    onCreate = { info ->
                        if (editingExisting) {
                            onSelect(info)
                        } else {
                            selectedRows.value = selectedRows.value + (info.id to info)
                            onSelectionChange(selectedRows.value.values.toList())
                        }
                        onSearch("")
                    },
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(with(density) { filterBarHeight.toDp() + 18.dp })
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.94f),
                        0.72f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { filterBarHeight = it.height }
                .kpknGlassOrFallback(
                    hazeState = hazeState,
                    shape = RoundedCornerShape(16.dp),
                    additionalScrim = Color.Black.copy(alpha = 0.12f),
                )
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(filterRegion == null && filterMuscle == null, {
                    filterRegion = null; filterMuscle = null; muscleFilterExpanded = false
                }, label = { Text("Todos") }, colors = catalogFilterChipColors())
                FilterChip(filterRegion == "UPPER", {
                    filterRegion = if (filterRegion == "UPPER") null else "UPPER"; filterMuscle = null
                }, label = { Text("Tren Superior") }, colors = catalogFilterChipColors())
                FilterChip(filterRegion == "LOWER", {
                    filterRegion = if (filterRegion == "LOWER") null else "LOWER"; filterMuscle = null
                }, label = { Text("Tren Inferior") }, colors = catalogFilterChipColors())
                FilterChip(muscleFilterExpanded || filterMuscle != null, {
                    muscleFilterExpanded = !muscleFilterExpanded
                }, label = { Text(filterMuscle?.let(::exerciseCatalogMuscleLabel) ?: "Músculo") }, colors = catalogFilterChipColors())
            }
            if (muscleFilterExpanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CATALOG_FILTER_MUSCLE_IDS.forEach { muscleId ->
                        FilterChip(filterMuscle == muscleId, {
                            filterMuscle = if (filterMuscle == muscleId) null else muscleId
                        }, label = { Text(exerciseCatalogMuscleLabel(muscleId)) }, colors = catalogFilterChipColors())
                    }
                }
            }
        }

    // Franja flotante de seleccionados + buscador, anclados al borde inferior.
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!editingExisting && selectedRows.value.isNotEmpty()) {
            SelectedExercisesAccordion(
                selected = selectedRows.value.entries.toList(),
                modifier = Modifier.onSizeChanged { selectionPanelHeight = it.height + 16 },
                onRemove = { id ->
                    val next = selectedRows.value - id
                    selectedRows.value = next
                    onSelectionChange(next.values.toList())
                },
                onMove = { index, delta ->
                    val ids = selectedRows.value.keys.toMutableList()
                    val toIndex = (index + delta).coerceIn(0, ids.lastIndex)
                    if (toIndex == index) return@SelectedExercisesAccordion
                    val moved = ids.removeAt(index)
                    ids.add(toIndex, moved)
                    selectedRows.value = ids.associateWith { selectedRows.value.getValue(it) }
                    onSelectionChange(selectedRows.value.values.toList())
                },
                onTap = { id ->
                    val customOffset = if (visibleCustomExercises.isNotEmpty()) visibleCustomExercises.size + 1 else 0
                    val indexInDefinitions = definitions.indexOfFirst { it.id == id }
                    if (indexInDefinitions >= 0) {
                        scope.launch {
                            listState.animateScrollToItem(1 + customOffset + indexInDefinitions)
                        }
                    }
                },
            )
        }
        FloatingCatalogSearch(
            value = query,
            onValueChange = onSearch,
            hazeState = hazeState,
        )
    }
    }

    if (!editingExisting && selectedRows.value.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedRows.value.size >= 2 && onCreateSuperset != null) {
                Button(
                    onClick = {
                        onCreateSuperset(selectedRows.value.values.toList())
                        selectedRows.value = emptyMap()
                        onSelectionChange(emptyList())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Crear superserie", fontWeight = FontWeight.Black)
                }
            }
            Button(
                onClick = {
                    val ids = onMultiSelect(selectedRows.value.values.toList())
                    if (ids.isNotEmpty()) onDismiss()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Agregar ${selectedRows.value.size} ejercicio(s)", fontWeight = FontWeight.Black) }
        }
    }
    deletingCustomExercise.value?.let { deleting ->
        KpknAlertDialog(
            onDismissRequest = { deletingCustomExercise.value = null },
            title = { Text("Eliminar ejercicio", color = Color.White) },
            text = { Text("¿Eliminar «${deleting.name}»? Esta acción no se puede deshacer.", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    val wasSelected = selectedRows.value.containsKey(deleting.id)
                    CustomExerciseRepository.delete(deleting.id)
                    if (wasSelected) {
                        val next = selectedRows.value - deleting.id
                        selectedRows.value = next
                        onSelectionChange(next.values.toList())
                    }
                    deletingCustomExercise.value = null
                }) { Text("Eliminar", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCustomExercise.value = null }) {
                    Text("Cancelar", color = Color.White)
                }
            },
        )
    }
}

/** Colores oscuros del sheet para los chips de filtro del catálogo. */
@Composable
private fun catalogFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.08f),
    labelColor = Color.White.copy(alpha = 0.88f),
    selectedContainerColor = Color.White.copy(alpha = 0.96f),
    selectedLabelColor = Color(0xFF101214),
)

/** Músculos principales para el filtro "Por Músculo". */
private val CATALOG_FILTER_MUSCLE_IDS = listOf(
    "pectoralis", "latissimus_dorsi", "deltoid", "biceps", "triceps", "forearm",
    "quadriceps", "hamstrings", "gluteus_maximus", "adductors", "calves",
    "erector_spinae", "core", "trapezius", "neck",
)

@Composable
private fun CatalogDescription(
    definition: ExerciseDefinitionV2,
    configuration: com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2?,
    catalog: ExerciseCatalogV2?,
) {
    val configurationId = configuration?.id
    var descriptionExpanded by remember(definition.id, configurationId) { mutableStateOf(false) }
    var techniqueExpanded by remember(definition.id, configurationId) { mutableStateOf(false) }
    var muscleExpanded by remember(definition.id, configurationId) { mutableStateOf(false) }
    var jointExpanded by remember(definition.id, configurationId) { mutableStateOf(false) }
    val legacyInfo = remember(catalog, definition.id, configurationId) {
        if (catalog != null && configuration != null) {
            exactInfo(catalog, definition, configuration.id)
        } else {
            null
        }
    }
    val description = configuration?.profile?.description
        ?.takeIf { it.isNotBlank() }
        ?: definition.description

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CatalogInfoAccordion(
            title = "Descripción",
            expanded = descriptionExpanded,
            onToggle = { descriptionExpanded = !descriptionExpanded },
        ) {
            Text(
                description,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
            configuration?.profile?.benefits?.takeIf { it.isNotEmpty() }?.let { benefits ->
                Text(
                    "Qué aporta",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                )
                benefits.forEach { benefit ->
                    EditorialBullet(text = benefit, color = Color(0xFF4ADE80))
                }
            }
        }

        if (configuration?.profile?.techniqueSummary?.isNotBlank() == true) {
            CatalogInfoAccordion(
                title = "Técnica",
                expanded = techniqueExpanded,
                onToggle = { techniqueExpanded = !techniqueExpanded },
            ) {
                Text(
                    configuration.profile.techniqueSummary,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (legacyInfo?.involvedMuscles?.isNotEmpty() == true) {
            CatalogInfoAccordion(
                title = "Involucramiento Muscular",
                expanded = muscleExpanded,
                onToggle = { muscleExpanded = !muscleExpanded },
            ) {
                MuscleInvolvementSection(legacyInfo, showHeader = false)
            }
        }

        if (configuration?.profile?.jointInvolvement?.isNotEmpty() == true) {
            CatalogInfoAccordion(
                title = "Involucramiento Articular",
                expanded = jointExpanded,
                onToggle = { jointExpanded = !jointExpanded },
            ) {
                JointInvolvementSection(configuration.profile.jointInvolvement, showHeader = false)
            }
        }
    }
}

@Composable
private fun CatalogInfoAccordion(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Contraer $title" else "Expandir $title",
                tint = Color.White.copy(alpha = 0.68f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun EditorialBullet(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text,
            color = Color.White.copy(alpha = 0.76f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

private val JOINT_LABELS = mapOf(
    "glenohumeral" to "Hombro",
    "acromioclavicular" to "Acromioclavicular",
    "esternoclavicular" to "Esternoclavicular",
    "escapulotoracica" to "Escapulotorácica",
    "codo" to "Codo",
    "radiocubital-proximal" to "Radiocubital proximal",
    "muñeca" to "Muñeca",
    "columna-cervical" to "Columna cervical",
    "columna-toracica" to "Columna torácica",
    "columna-lumbar" to "Columna lumbar",
    "sacroiliaca" to "Sacroilíaca",
    "cadera" to "Cadera",
    "rodilla" to "Rodilla",
    "tobillo" to "Tobillo",
    "subtalar" to "Subastragalina",
)

private fun catalogTitleLabel(value: String): String = value
    .split(" ")
    .joinToString(" ") { word ->
        word.replaceFirstChar { character -> character.uppercaseChar() }
    }

private fun jointLabel(id: String): String = JOINT_LABELS[id] ?: id.replace('-', ' ').replaceFirstChar { it.uppercaseChar() }

private fun jointRoleLabel(role: JointRoleV2): String = when (role) {
    JointRoleV2.PRIMARY -> "Principal"
    JointRoleV2.SECONDARY -> "Secundaria"
    JointRoleV2.STABILIZER -> "Estabilizadora"
}

@Composable
private fun JointInvolvementSection(
    joints: List<JointInvolvementV2>,
    showHeader: Boolean = true,
) {
    val expandedJoint = remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showHeader) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (showHeader) {
            Text("Involucramiento Articular", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Qué articulación mueve, transmite o estabiliza la carga en la configuración elegida.",
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        joints.forEach { joint ->
            val isExpanded = expandedJoint.value == joint.jointId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expandedJoint.value = if (isExpanded) null else joint.jointId }
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        jointLabel(joint.jointId),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        jointRoleLabel(joint.role),
                        color = Color(0xFFFBBF24),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (isExpanded) "▴" else "▾",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                    Text(
                        joint.actions.joinToString(" · ") { catalogTitleLabel(it) },
                        color = Color.White.copy(alpha = 0.64f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                if (isExpanded) {
                    Text(
                        joint.note,
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleInvolvementSection(
    exercise: ExerciseMuscleInfo,
    showHeader: Boolean = true,
) {
    val expandedMuscle = remember { mutableStateOf<String?>(null) }
    val contributions = remember(exercise) { oneSeriesVolumeContributions(exercise) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showHeader) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (showHeader) {
            Text("Involucramiento Muscular", color = Color.White, fontWeight = FontWeight.Bold)
        }
        contributions.forEach { contribution ->
            val muscleName = contribution.muscle
            val involvement = contribution.sourceInvolvement
            val isExpanded = expandedMuscle.value == muscleName
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expandedMuscle.value = if (isExpanded) null else muscleName }
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(wikilabMuscleColor(muscleName)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        catalogTitleLabel(muscleName),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val emphasisCode = contribution.emphasis?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    val emphasisLabel = emphasisCode?.let(::catalogTitleLabel)
                    if (emphasisLabel != null && !muscleName.lowercase().contains(emphasisCode)) {
                        Spacer(Modifier.width(6.dp))
                        EmphasisChip(emphasisLabel)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatSeriesEquivalent(contribution.seriesEquivalent),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        if (isExpanded) " ▴" else " ▾",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (isExpanded) {
                    Text(
                        roleVolumeLabel(contribution.role),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    val explanation = involvement?.biomechanicalReason
                        ?.takeIf { it.isNotBlank() }
                        ?: involvement?.let { explainMuscleContribution(exercise, it) }
                    if (explanation != null) {
                        Text(
                            explanation,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de un ejercicio personalizado (creado por el usuario) renderizada con
 * el mismo lenguaje visual que las tarjetas del catálogo: músculos principales,
 * chips de aspectos técnicos y el involucramiento muscular derivado por
 * similitud. El toque en la tarjeta agrega el ejercicio, igual que el catálogo.
 */
@Composable
private fun CustomExerciseCard(
    exercise: ExerciseMuscleInfo,
    selected: Boolean,
    editingExisting: Boolean,
    hazeState: HazeState?,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val primaryMuscles = remember(exercise) {
        exercise.involvedMuscles
            .filter { it.role == MuscleRole.PRIMARY }
            .map { it.muscle }
            .distinct()
            .take(3)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF4ADE80).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f),
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(
                hazeState = hazeState,
                shape = RoundedCornerShape(16.dp),
                additionalScrim = if (selected) {
                    Color(0xFF1E5A44).copy(alpha = 0.38f)
                } else {
                    Color.Transparent
                },
            )
            .clickable { if (editingExisting) onSelect() else onToggle() },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Seleccionado",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (primaryMuscles.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        primaryMuscles.joinToString(" · ") { exerciseCatalogMuscleLabel(it) },
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar ejercicio",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar ejercicio",
                        tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (exercise.catalogVariantChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    exercise.catalogVariantChips.forEach { value ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                value,
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            exercise.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    desc,
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (exercise.involvedMuscles.isNotEmpty()) {
                MuscleInvolvementSection(exercise)
            }
        }
    }
}

@Composable
private fun EmphasisChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AxisChip(
    value: String,
    definitionId: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // The repository has already filtered this level against the choices made
    // above it. Never expose a disabled combination as if it were a valid chip.
    FilterChip(
        selected = selected,
        onClick = { if (enabled) onClick() },
        label = { Text(exerciseCatalogOptionLabel(value, definitionId)) },
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            labelColor = Color.White.copy(alpha = 0.88f),
            selectedContainerColor = Color.White.copy(alpha = 0.96f),
            selectedLabelColor = Color(0xFF101214),
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledLabelColor = Color.White.copy(alpha = 0.30f),
        ),
    )
}

/** Chips del título: opciones coincidentes de la búsqueda, sin duplicados y solo
 *  las más específicas (ej. "Polea Baja", no "Polea" + "Polea Baja"). */
private fun searchMatchChipLabels(
    configuration: ExerciseConfigurationV2,
    definition: ExerciseDefinitionV2,
): List<String> {
    val labels = configuration.selectedOptions.values
        .map { exerciseCatalogOptionLabel(it, definition.id) }
        .filter { it.isNotBlank() }
    val mostSpecific = labels.filter { label ->
        !labels.any { other -> other != label && other.contains(label, ignoreCase = true) }
    }
    val name = definition.canonicalName
    return mostSpecific
        .filter { label -> !name.contains(label, ignoreCase = true) }
        .distinct()
}

/** Lista flotante de los ejercicios seleccionados, plegable tipo acordeón:
 *  quitar, reordenar con flechas y tocar para ir al ejercicio. Ahorra espacio
 *  porque por defecto queda colapsada en una barra compacta. */
@Composable
private fun SelectedExercisesAccordion(
    selected: List<Map.Entry<String, ExerciseMuscleInfo>>,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(
                hazeState = LocalHazeState.current,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            border = null,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${selected.size} seleccionado${if (selected.size == 1) "" else "s"}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Plegar seleccionados" else "Desplegar seleccionados",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                selected.forEachIndexed { index, (id, info) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            IconButton(
                                onClick = { onMove(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Subir en el orden",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { onMove(index, 1) },
                                enabled = index < selected.lastIndex,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Bajar en el orden",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Text(
                                info.name,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onTap(id) }
                                    .padding(vertical = 8.dp),
                            )
                            IconButton(
                                onClick = { onRemove(id) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar seleccionado",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        if (index < selected.lastIndex) {
                            androidx.compose.material3.Divider(color = Color.White.copy(alpha = 0.08f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fallback resolution for a partial draft that doesn't collapse to exactly one
 * configuration. Returns the first configuration that satisfies every selected
 * option, preferring the catalog default on unspecified axes (i.e. the row with
 * the fewest deviations from the default). This avoids silently falling back to
 * the default configuration when the user explicitly picked a non-default value.
 */
internal fun bestMatchingConfigurationId(
    definition: ExerciseDefinitionV2,
    selectedOptions: Map<String, String>,
    default: ExerciseConfigurationV2?,
): String? {
    val compatible = definition.configurations.filter { configuration ->
        selectedOptions.all { (axis, value) -> configuration.selectedOptions[axis] == value }
    }
    if (compatible.isEmpty()) return null
    compatible.firstOrNull { it.id == default?.id }?.let { return it.id }
    val defaultOptions = default?.selectedOptions.orEmpty()
    return compatible.minByOrNull { configuration ->
        configuration.selectedOptions.count { (axis, value) -> defaultOptions[axis] != value }
    }?.id
}
private fun exactInfo(
    catalog: ExerciseCatalogV2,
    definition: ExerciseDefinitionV2,
    configurationId: String?,
): ExerciseMuscleInfo? = configurationId?.let {
    catalog.toLegacySelection(
        ExerciseSelectionV2(
            definitionId = definition.id,
            configurationId = it,
            catalogRevision = catalog.catalogRevision,
        ),
    )
}

private fun draftAfterAxisSelection(
    definition: ExerciseDefinitionV2,
    selectedOptions: Map<String, String>,
    axis: String,
    value: String,
): Map<String, String> {
    val candidate = (selectedOptions - axis) + (axis to value)
    if (definition.configurations.any { configuration ->
            candidate.all { (key, selected) -> configuration.selectedOptions[key] == selected }
        }) {
        return candidate
    }

    val configurationsWithChoice = definition.configurations.filter {
        it.selectedOptions[axis] == value
    }
    if (configurationsWithChoice.isEmpty()) return selectedOptions

    val result = candidate.toMutableMap()
    selectedOptions.keys
        .filter { it != axis }
        .filter { key ->
            configurationsWithChoice.none {
                it.selectedOptions[key] == selectedOptions[key]
            }
        }
        .forEach(result::remove)
    while (result.size > 1 && definition.configurations.none { configuration ->
            result.all { (key, selected) -> configuration.selectedOptions[key] == selected }
        }) {
        result.keys.firstOrNull { it != axis }?.let(result::remove) ?: break
    }
    return result
}
/**
 * Diálogo reutilizable para editar un ejercicio personalizado existente.
 */
@Composable
fun SmartExerciseEditorDialog(
    initial: ExerciseMuscleInfo,
    onSave: (ExerciseMuscleInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknGlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Editar (${initial.name})",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            SmartExerciseForm(initial = initial, onSave = onSave, onDismiss = onDismiss)
        }
    }
}

/**
 * Página de edición dentro del sheet: reemplaza el catálogo completo (sin
 * filtros ni título de catálogo) y muestra solo el editor con su cabecera.
 */
@Composable
internal fun SmartExerciseEditorPage(
    exercise: ExerciseMuscleInfo,
    onSave: (ExerciseMuscleInfo) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
            }
            Text(
                "Editar (${exercise.name})",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        SmartExerciseForm(initial = exercise, onSave = onSave, onDismiss = onClose)
    }
}

/**
 * Formulario del creador inteligente. Se usa inline en el buscador sin
 * resultados y dentro del diálogo de edición. Deriva nombre → patrón →
 * match de catálogo → músculos, permite descripción propia y ajuste manual
 * del involucramiento cuando la detección es incierta.
 */
@Composable
internal fun SmartExerciseForm(
    initial: ExerciseMuscleInfo?,
    onSave: (ExerciseMuscleInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val catalog = remember { exerciseCatalogSnapshot() }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var implementoId by remember(initial) {
        mutableStateOf(
            initial?.catalogVariantChips?.getOrNull(0)
                ?.let { SmartExerciseCreator.implementoIdFromLabel(it) }
                ?: initial?.equipment?.let { SmartExerciseCreator.implementoIdFromLabel(it) },
        )
    }
    var estacionId by remember(initial) {
        mutableStateOf(
            initial?.catalogVariantChips?.getOrNull(1)
                ?.let { SmartExerciseCreator.estacionIdFromLabel(it) },
        )
    }
    var lateralidadId by remember(initial) {
        mutableStateOf(
            initial?.catalogVariantChips?.getOrNull(2)
                ?.let { SmartExerciseCreator.lateralidadIdFromLabel(it) }
        )
    }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var musclesOverride by remember(initial) {
        mutableStateOf(initial?.involvedMuscles?.takeIf { it.isNotEmpty() })
    }
    var manualExpanded by remember(initial) { mutableStateOf(initial != null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val preview = remember(name, implementoId, estacionId, lateralidadId, description, musclesOverride) {
        if (name.isNotBlank()) {
            runCatching {
                SmartExerciseCreator.preview(
                    SmartCreateRequest(
                        name = name,
                        implementoId = implementoId ?: "",
                        estacionId = estacionId,
                        lateralidadId = lateralidadId,
                        description = description,
                        musclesOverride = musclesOverride,
                        existingId = initial?.id,
                    ),
                    catalog,
                )
            }.getOrNull()
        } else null
    }



    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre del ejercicio", color = Color.White.copy(alpha = 0.6f)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.4f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OptionChipRow(
            title = "Implemento",
            required = true,
            value = implementoId,
            options = SmartExerciseCreator.IMPLEMENTO_IDS,
            label = SmartExerciseCreator::implementoLabel,
            onSelect = { implementoId = it },
        )
        OptionChipRow(
            title = "Estación",
            value = estacionId,
            options = SmartExerciseCreator.ESTACION_IDS,
            label = SmartExerciseCreator::estacionLabel,
            onSelect = { estacionId = it },
        )
        OptionChipRow(
            title = "Lateralidad",
            value = lateralidadId,
            options = SmartExerciseCreator.LATERALIDAD_IDS,
            label = SmartExerciseCreator::lateralidadLabel,
            onSelect = { lateralidadId = it },
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción", color = Color.White.copy(alpha = 0.6f)) },
            placeholder = {
                Text(
                    if (preview?.manualRecommended == true) {
                        "El nombre no permite inferirla; describe brevemente el ejercicio"
                    } else {
                        "Se genera automáticamente si la dejas vacía"
                    },
                    color = Color.White.copy(alpha = 0.35f),
                )
            },
            minLines = 2,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.4f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        preview?.let { p ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Derivado automáticamente por similitud",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                p.detectedPattern?.let { PatternBanner(it) }
                if (p.manualRecommended) {
                    Text(
                        "No pudimos detectar el patrón con certeza: revisa el involucramiento muscular antes de guardar.",
                        color = Color(0xFFFBBF24),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (p.exercise.catalogVariantChips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        p.exercise.catalogVariantChips.forEach { value ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    value,
                                    color = Color.White.copy(alpha = 0.82f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                if (p.exercise.involvedMuscles.isNotEmpty()) {
                    MuscleInvolvementSection(p.exercise)
                }
            }
        }

        val editableMuscles = musclesOverride ?: preview?.exercise?.involvedMuscles.orEmpty()
        if (editableMuscles.isNotEmpty() && (manualExpanded || preview?.manualRecommended == true)) {
            ManualMuscleEditor(
                muscles = editableMuscles,
                onChange = { updated -> musclesOverride = updated },
            )
        }
        TextButton(
            onClick = { manualExpanded = !manualExpanded },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                if (manualExpanded) "Ocultar ajuste manual" else "Ajustar involucramiento manualmente",
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
            }
            Button(
                onClick = {
                    val result = preview?.exercise ?: return@Button
                    creating = true
                    scope.launch {
                        withContext(Dispatchers.IO) { CustomExerciseRepository.upsert(result) }
                        creating = false
                        onSave(result)
                    }
                },
                enabled = name.isNotBlank() && implementoId != null && !creating && preview != null,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (initial == null) "Crear y seleccionar" else "Guardar cambios")
                }
            }
        }
    }
}

/**
 * Empty-search entry point: "«query» no está en el catálogo". Crea el
 * ejercicio automáticamente con el nombre buscado y los datos del ejercicio
 * de referencia; el editor queda disponible al editar el ejercicio después.
 */
@Composable
private fun SmartCreateSuggestion(
    query: String,
    hasPartialResults: Boolean = false,
    onCreate: (ExerciseMuscleInfo) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalog = remember { exerciseCatalogSnapshot() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (hasPartialResults) "\"$query\" no coincide exactamente con ningún ejercicio" else "\"$query\" no está en el catálogo",
            color = Color.White.copy(alpha = 0.72f),
        )
        Button(
            onClick = {
                if (creating) return@Button
                creating = true
                scope.launch {
                    val created = withContext(Dispatchers.IO) {
                        val info = SmartExerciseCreator.createAutomatic(query, catalog)
                        CustomExerciseRepository.upsert(info)
                        info
                    }
                    creating = false
                    onCreate(created)
                }
            },
            enabled = !creating,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (creating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Crear este ejercicio automáticamente")
            }
        }
    }
}

@Composable
private fun PatternBanner(pattern: ExercisePatternDetector.DetectedMovementPattern) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Patrón detectado:",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            pattern.label,
            color = Color(0xFF4ADE80),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val MANUAL_MUSCLE_OPTIONS = listOf(
    "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Antebrazo",
    "Cuádriceps", "Isquiosurales", "Glúteos", "Core", "Abdomen", "Trapecio",
    "Romboides", "Erectores Espinales", "Pantorrillas", "Aductores",
)

@Composable
private fun ManualMuscleEditor(
    muscles: List<InvolvedMuscle>,
    onChange: (List<InvolvedMuscle>) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Involucramiento manual",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
        muscles.forEachIndexed { index, muscle ->
            val volume = (muscle.volumeContribution ?: resolveMuscleVolumeContribution(muscle)).toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        muscle.muscle,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onChange(muscles.filterIndexed { i, _ -> i != index }) },
                    ) {
                        Text(
                            "Quitar",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        MuscleRole.PRIMARY to "Primario",
                        MuscleRole.SECONDARY to "Secundario",
                        MuscleRole.STABILIZER to "Estabilizador",
                    ).forEach { (role, label) ->
                        FilterChip(
                            selected = muscle.role == role,
                            onClick = {
                                onChange(
                                    muscles.toMutableList().also { it[index] = muscle.copy(role = role) },
                                )
                            },
                            label = { Text(label, color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (muscle.role == role) {
                                    Color.White.copy(alpha = 0.22f)
                                } else {
                                    Color.White.copy(alpha = 0.08f)
                                },
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = muscle.role == role,
                                borderColor = Color.White.copy(alpha = 0.25f),
                                selectedBorderColor = Color.White.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
                Slider(
                    value = volume.coerceIn(0.0f, 1.0f),
                    onValueChange = { newVolume ->
                        onChange(
                            muscles.toMutableList().also {
                                it[index] = muscle.copy(
                                    volumeContribution = newVolume.toDouble().coerceIn(0.0, 1.0),
                                )
                            },
                        )
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                )
                Text(
                    "Aporte: ${"%.2f".format(volume)}",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val available = MANUAL_MUSCLE_OPTIONS.filter { option ->
            muscles.none { it.muscle.equals(option, ignoreCase = true) }
        }
        if (available.isNotEmpty()) {
            Text(
                "Agregar músculo",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                available.take(12).forEach { option ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onChange(muscles + InvolvedMuscle(option, MuscleRole.SECONDARY, 0.5))
                        },
                        label = { Text(option, color = Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = Color.White.copy(alpha = 0.25f),
                            selectedBorderColor = Color.White.copy(alpha = 0.6f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionChipRow(
    title: String,
    value: String?,
    options: List<String>,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    required: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (required) "$title *" else title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { optionId ->
                val selected = value == optionId
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(optionId) },
                    label = { Text(label(optionId), color = Color.White) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = if (selected) 0.22f else 0.08f),
                        labelColor = Color.White.copy(alpha = 0.85f),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = Color.White.copy(alpha = 0.25f),
                        selectedBorderColor = Color.White.copy(alpha = 0.6f),
                    ),
                )
            }
        }
    }
}
