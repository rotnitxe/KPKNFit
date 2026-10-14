package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalJointKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
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
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.ExerciseTechniqueImageVariant
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.design.CatalogExerciseRow
import com.example.kpkn.screens.sessioneditor.design.CatalogListHeader
import com.example.kpkn.screens.sessioneditor.design.CatalogListSkeleton
import com.example.kpkn.screens.sessioneditor.design.CatalogLoadError
import com.example.kpkn.screens.sessioneditor.design.CatalogNoResults
import com.example.kpkn.screens.sessioneditor.design.CatalogSelectionIsland
import com.example.kpkn.screens.sessioneditor.design.CatalogSelectionSlot
import com.example.kpkn.screens.sessioneditor.design.CatalogVariantChips
import com.example.kpkn.screens.sessioneditor.design.catalogSelectionSummary
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

private typealias CatalogExerciseImageVariant = ExerciseTechniqueImageVariant

private data class CatalogSearchResultState(
    val committedQuery: String = "",
    val filteredHits: List<ExerciseSearchHitV2> = emptyList(),
    val globalHits: List<ExerciseSearchHitV2> = emptyList(),
    val isSettled: Boolean = false,
)

internal data class CatalogDefinitionProjection(
    val definitionsById: Map<String, ExerciseDefinitionV2>,
    val blankQueryDefinitions: List<ExerciseDefinitionV2>,
)

private data class CatalogDefinitionFilterKey(
    val region: String?,
    val muscle: String?,
)

/** Build the all-catalog projection away from Compose's main-thread pass. */
internal fun prepareCatalogDefinitionProjection(
    catalog: ExerciseCatalogV2,
    filterRegion: String?,
    filterMuscle: String?,
): CatalogDefinitionProjection {
    val definitions = catalog.families.flatMap { it.definitions }
    val definitionsById = definitions.associateBy { it.id }
    val filteredAndSorted = definitions.asSequence()
        .filter { definition ->
            val configurations = definition.configurations
            (filterRegion == null || configurations.any { it.profile.bodyRegion.name == filterRegion }) &&
                (filterMuscle == null || configurations.any { filterMuscle in it.profile.primaryMuscles })
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.canonicalName })
        .toList()
    return CatalogDefinitionProjection(
        definitionsById = definitionsById,
        blankQueryDefinitions = filteredAndSorted,
    )
}

internal fun catalogSearchResultsAreCurrent(
    query: String,
    committedQuery: String,
    isSettled: Boolean,
): Boolean = query == committedQuery && isSettled

/**
 * Lista visible del catálogo para una consulta.
 *
 * Mientras la búsqueda no está asentada se conserva la última lista estable:
 * vaciar la lista en cada tecla pintaba la pantalla de negro durante el
 * debounce (fondo opaco + tarjetas transparentes = flash negro).
 */
internal fun visibleDefinitionsForQuery(
    catalog: ExerciseCatalogV2,
    query: String,
    searchSettled: Boolean,
    searchHits: List<ExerciseSearchHitV2>,
    filterRegion: String?,
    filterMuscle: String?,
    definitionsById: Map<String, ExerciseDefinitionV2>,
    previousStable: List<ExerciseDefinitionV2>,
    blankQueryDefinitions: List<ExerciseDefinitionV2>? = null,
): List<ExerciseDefinitionV2> {
    fun definitionMatchesFilter(definition: ExerciseDefinitionV2): Boolean {
        val configs = definition.configurations
        if (filterRegion != null && configs.none { it.profile.bodyRegion.name == filterRegion }) return false
        if (filterMuscle != null && configs.none { config -> config.profile.primaryMuscles.contains(filterMuscle) }) return false
        return true
    }

    return when {
        query.isBlank() -> blankQueryDefinitions ?: catalog.families
            .flatMap { it.definitions }
            .filter(::definitionMatchesFilter)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.canonicalName })
        searchSettled -> searchHits
            .mapNotNull { hit -> definitionsById[hit.definitionId] }
            .distinctBy { it.id }
        else -> previousStable
    }
}

internal fun toggleCatalogDefinitionExpansion(
    currentDefinitionId: String?,
    tappedDefinitionId: String,
): String? = if (currentDefinitionId == tappedDefinitionId) null else tappedDefinitionId

internal fun shouldShowCatalogCreateSuggestion(
    query: String,
    searchSettled: Boolean,
    visibleCatalogResultCount: Int,
    globalCatalogResultCount: Int,
    customResultCount: Int,
): Boolean = query.isNotBlank() &&
    searchSettled &&
    visibleCatalogResultCount == 0 &&
    globalCatalogResultCount == 0 &&
    customResultCount == 0

private fun exerciseCatalogImageVariants(
    definitionId: String,
    catalogConfigurationId: String? = null,
    selectedImplementation: String? = null,
): List<CatalogExerciseImageVariant> {
    val variants = ExerciseTechniqueImageLookup.variants(definitionId)
    if (variants.isEmpty() || catalogConfigurationId == null) return variants

    val selectedImage = ExerciseTechniqueImageLookup.resolveImageResId(
        catalogDefinitionId = definitionId,
        exerciseDbId = null,
        exerciseId = definitionId,
        catalogConfigurationId = catalogConfigurationId,
        selectedImplementation = selectedImplementation,
    )
    return variants.takeIf { selectedImage != null }.orEmpty()
}

/**
 * An illustration must follow the catalog's current implement, never replace it.
 *
 * Returning null hides the carousel when that implement has no approved image yet.
 * In particular, this prevents the approved machine T-bar illustration from being
 * shown for, or silently switching, the separate landmine/T-bar configuration.
 */
internal fun selectedCatalogImageVariantIndex(
    variants: List<ExerciseTechniqueImageVariant>,
    selectedImplementation: String?,
): Int? {
    if (variants.isEmpty()) return null
    if (selectedImplementation == null) return 0
    return variants.indexOfFirst { it.implementation == selectedImplementation }
        .takeIf { it >= 0 }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogExerciseImageCarousel(
    variants: List<CatalogExerciseImageVariant>,
    selectedImplementation: String?,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onImplementationSettled: (String) -> Unit,
) {
    if (variants.isEmpty()) return

    val selectedIndex = selectedCatalogImageVariantIndex(variants, selectedImplementation) ?: return
    val pagerState = rememberPagerState(initialPage = selectedIndex) { variants.size }
    val currentSelectedImplementation by rememberUpdatedState(selectedImplementation)
    val currentOnImplementationSettled by rememberUpdatedState(onImplementationSettled)

    LaunchedEffect(selectedIndex) {
        pagerState.scrollToPage(selectedIndex)
    }
    LaunchedEffect(pagerState, variants) {
        snapshotFlow { pagerState.settledPage }.collectLatest { page ->
            variants.getOrNull(page)?.implementation?.let { implementation ->
                if (implementation != currentSelectedImplementation) {
                    currentOnImplementationSettled(implementation)
                }
            }
        }
    }

    val shape = RoundedCornerShape(if (expanded) 16.dp else 12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            userScrollEnabled = variants.size > 1,
            pageSpacing = 0.dp,
        ) { page ->
            val variant = variants[page]
            Image(
                painter = painterResource(variant.imageResId),
                contentDescription = "${if (expanded) "Ilustración" else "Miniatura"} del ejercicio con ${variant.label}",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (expanded) 4.dp else 2.dp),
                contentScale = ContentScale.Fit,
            )
        }
        if (variants.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (expanded) 8.dp else 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                variants.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) {
                                    Color.White.copy(alpha = 0.95f)
                                } else {
                                    Color.White.copy(alpha = 0.42f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * The only runtime exercise picker. It deliberately has no legacy fallback:
 * while v2 is loading we show a stable loading surface, never the old catalog.
 *
 * SE6a: la cabecera (título, buscador y filtros) va arriba en todos los estados; debajo, la lista, y la isla de selección flota abajo.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    onCreateSupersetConfigured: ((List<ExerciseMuscleInfo>, CatalogSupersetConfig) -> Unit)? = null,
    isSupersetAddMode: Boolean = false,
    onDismiss: () -> Unit,
    initialCatalogDefinitionId: String? = null,
    initialCatalogConfigurationId: String? = null,
    opaqueSurface: Boolean = false,
    dismissAfterMultiSelect: Boolean = true,
    targetGroupName: String? = null,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        val repairBenchmarkIoWindow =
            com.example.kpkn.screens.workout.RepairBenchmarkTrace.beginUiIoWindow("exercise_picker")
        onDispose {
            com.example.kpkn.screens.workout.RepairBenchmarkTrace.endUiIoWindow(repairBenchmarkIoWindow)
        }
    }
    val repairBenchmarkPickerRequestId = remember {
        com.example.kpkn.screens.workout.RepairBenchmarkTrace.pickerOpenRequested()
    }
    val repairBenchmarkSearchRequestId = remember { mutableStateOf(0L) }
    val repairBenchmarkLatestSearchQuery = remember { mutableStateOf(query) }
    val benchmarkOnSearch: (String) -> Unit = remember(
        onSearch,
        repairBenchmarkSearchRequestId,
        repairBenchmarkLatestSearchQuery,
    ) {
        { nextQuery ->
            repairBenchmarkLatestSearchQuery.value = nextQuery
            repairBenchmarkSearchRequestId.value =
                com.example.kpkn.screens.workout.RepairBenchmarkTrace.pickerSearchRequested()
            onSearch(nextQuery)
        }
    }
    val retryScope = rememberCoroutineScope()
    // The editor/live hosts can still use the shared glass fallback, while the
    // navigation destination is a real page: no sampled backdrop, blur or sheet
    // shape is allowed there.
    val glassHaze = if (opaqueSurface) null else LocalHazeState.current
    // Los filtros viven aquí y no en la lista: la cabecera los pinta también mientras carga o falla.
    var filterRegion by rememberSaveable { mutableStateOf<String?>(null) }
    var filterMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var muscleFilterExpanded by rememberSaveable { mutableStateOf(false) }

    val catalogShape = RoundedCornerShape(KpknGlass.SheetCornerRadius)
    Box(
        modifier = if (opaqueSurface) {
            Modifier.fillMaxSize().background(Color.Black)
        } else {
            Modifier
                .fillMaxSize()
                .kpknGlassOrFallback(
                    hazeState = glassHaze,
                    shape = catalogShape,
                    additionalScrim = Color.Black.copy(alpha = 0.06f),
                )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .then(if (opaqueSurface) Modifier.statusBarsPadding() else Modifier)
                .padding(top = if (opaqueSurface) 4.dp else 12.dp),
        ) {
            CatalogListHeader(
                query = query,
                onQueryChange = benchmarkOnSearch,
                onBack = if (opaqueSurface) onDismiss else null,
                targetGroupName = targetGroupName,
                filterRegion = filterRegion,
                filterMuscle = filterMuscle,
                muscleFilterExpanded = muscleFilterExpanded,
                muscleIds = CATALOG_FILTER_MUSCLE_IDS,
                onClearFilters = {
                    filterRegion = null
                    filterMuscle = null
                    muscleFilterExpanded = false
                },
                onToggleUpper = {
                    filterRegion = if (filterRegion == "UPPER") null else "UPPER"
                    filterMuscle = null
                },
                onToggleLower = {
                    filterRegion = if (filterRegion == "LOWER") null else "LOWER"
                    filterMuscle = null
                },
                onToggleMuscle = { muscleFilterExpanded = !muscleFilterExpanded },
                onToggleMuscleId = { muscleId ->
                    filterMuscle = if (filterMuscle == muscleId) null else muscleId
                },
            )
            Spacer(Modifier.height(12.dp))
            when (val current = state) {
                ExerciseCatalogStateV2.Loading -> CatalogListSkeleton(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                is ExerciseCatalogStateV2.Error -> CatalogLoadError(
                    onRetry = { retryScope.launch { repository.load() } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                is ExerciseCatalogStateV2.Ready -> CatalogReadyContent(
                    catalog = current.catalog,
                    repository = repository,
                    query = query,
                    onSearch = benchmarkOnSearch,
                    benchmarkSearchRequestId = repairBenchmarkSearchRequestId.value,
                    benchmarkLatestRequestedQuery = repairBenchmarkLatestSearchQuery.value,
                    editingExisting = editingExisting,
                    selectedExercisesIds = selectedExercisesIds,
                    onSelect = onSelect,
                    onMultiSelect = onMultiSelect,
                    onSelectionChange = onSelectionChange,
                    onOpenExerciseDetail = onOpenExerciseDetail,
                    onCreateSuperset = onCreateSuperset,
                    onCreateSupersetConfigured = onCreateSupersetConfigured,
                    isSupersetAddMode = isSupersetAddMode,
                    onDismiss = onDismiss,
                    initialCatalogDefinitionId = initialCatalogDefinitionId,
                    initialCatalogConfigurationId = initialCatalogConfigurationId,
                    dismissAfterMultiSelect = dismissAfterMultiSelect,
                    hazeState = glassHaze,
                    filterRegion = filterRegion,
                    filterMuscle = filterMuscle,
                    benchmarkRequestId = repairBenchmarkPickerRequestId,
                )
            }
        }
    }
}

/**
 * Lista del catálogo (SE6a): una fila por ejercicio ([CatalogExerciseRow]). Al tocarla se despliegan sus variantes bajo ella, o se abre
 * la ficha de hoy si no tiene. La isla de selección flota abajo cuando hay algo elegido y no hay teclado. La búsqueda, los filtros, la
 * selección y las llamadas son las de siempre.
 */
@OptIn(ExperimentalLayoutApi::class, FlowPreview::class, ExperimentalFoundationApi::class)
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
    onCreateSupersetConfigured: ((List<ExerciseMuscleInfo>, CatalogSupersetConfig) -> Unit)? = null,
    isSupersetAddMode: Boolean = false,
    onDismiss: () -> Unit,
    initialCatalogDefinitionId: String?,
    initialCatalogConfigurationId: String?,
    dismissAfterMultiSelect: Boolean,
    hazeState: HazeState?,
    filterRegion: String?,
    filterMuscle: String?,
    benchmarkRequestId: Long,
    benchmarkSearchRequestId: Long,
    benchmarkLatestRequestedQuery: String,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reducedMotion = wizardReducedMotion()
    val imeVisible = WindowInsets.isImeVisible
    val projectionCache = remember(catalog) {
        object : LinkedHashMap<CatalogDefinitionFilterKey, CatalogDefinitionProjection>(8, 0.75f, true) {}
    }
    val projectionKey = remember(filterRegion, filterMuscle) {
        CatalogDefinitionFilterKey(region = filterRegion, muscle = filterMuscle)
    }
    val catalogProjection by produceState<CatalogDefinitionProjection?>(
        initialValue = projectionCache[projectionKey],
        catalog,
        projectionKey,
        projectionCache,
    ) {
        value = projectionCache[projectionKey] ?: withContext(Dispatchers.Default) {
            prepareCatalogDefinitionProjection(
                catalog = catalog,
                filterRegion = projectionKey.region,
                filterMuscle = projectionKey.muscle,
            )
        }.also { prepared ->
            projectionCache[projectionKey] = prepared
            while (projectionCache.size > 8) {
                projectionCache.remove(projectionCache.keys.first())
            }
        }
    }
    val definitionsById = catalogProjection?.definitionsById.orEmpty()
    val searchFilters = remember(filterRegion, filterMuscle) {
        ExerciseSearchFiltersV2(
            bodyRegions = filterRegion?.let { setOf(ExerciseBodyRegionV2.valueOf(it)) }.orEmpty(),
            muscleIds = filterMuscle?.let { setOf(it) }.orEmpty(),
        )
    }
    // Debounced search: one fuzzy pass per ~150 ms pause on Dispatchers.Default
    // instead of a synchronous stemming pass on the main thread per keystroke.
    // Keep the query that produced the hits alongside them; otherwise a new
    // keystroke can render stale cards and the create prompt before the search
    // for the new text has completed.
    val currentQuery by rememberUpdatedState(query)
    val searchState by produceState<CatalogSearchResultState>(
        initialValue = CatalogSearchResultState(),
        repository,
        searchFilters,
    ) {
        snapshotFlow { currentQuery }
            .debounce(150)
            .collectLatest { committedQuery ->
                if (committedQuery.isBlank()) {
                    value = CatalogSearchResultState(
                        committedQuery = committedQuery,
                        isSettled = true,
                    )
                } else {
                    val hasActiveFilters = searchFilters.bodyRegions.isNotEmpty() || searchFilters.muscleIds.isNotEmpty()
                    val (filteredHits, globalHits) = withContext(Dispatchers.Default) {
                        if (hasActiveFilters) {
                            repository.search(committedQuery, searchFilters) to
                                repository.search(committedQuery)
                        } else {
                            repository.search(committedQuery, searchFilters).let { it to it }
                        }
                    }
                    value = CatalogSearchResultState(
                        committedQuery = committedQuery,
                        filteredHits = filteredHits,
                        globalHits = globalHits,
                        isSettled = true,
                    )
                }
            }
    }
    val searchSettled = catalogProjection != null && (
        query.isBlank() || catalogSearchResultsAreCurrent(
            query = query,
            committedQuery = searchState.committedQuery,
            isSettled = searchState.isSettled,
        )
    )
    val searchHits = if (searchSettled) searchState.filteredHits else emptyList()
    val globalSearchHits = if (searchSettled) searchState.globalHits else emptyList()
    var lastStableDefinitions by remember { mutableStateOf<List<ExerciseDefinitionV2>>(emptyList()) }
    val definitions = visibleDefinitionsForQuery(
        catalog = catalog,
        query = query,
        searchSettled = searchSettled,
        searchHits = searchHits,
        filterRegion = filterRegion,
        filterMuscle = filterMuscle,
        definitionsById = definitionsById,
        previousStable = lastStableDefinitions,
        blankQueryDefinitions = catalogProjection?.blankQueryDefinitions.orEmpty(),
    )
    LaunchedEffect(catalogProjection, definitions, searchSettled, query) {
        if (searchSettled || query.isBlank()) lastStableDefinitions = definitions
    }
    val initialDraftByDefinition = remember(
        catalog,
        catalogProjection,
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
        mutableStateOf(
            restoreSelectedCatalogRows(
                catalog = catalog,
                definitionsById = definitionsById,
                selectedIds = selectedExercisesIds,
                previous = emptyMap(),
                draftByDefinition = initialDraftByDefinition,
                initialCatalogDefinitionId = initialCatalogDefinitionId,
                initialCatalogConfigurationId = initialCatalogConfigurationId,
            ),
            neverEqualPolicy(),
        )
    }
    LaunchedEffect(catalog, catalogProjection, selectedExercisesIds) {
        val next = restoreSelectedCatalogRows(
            catalog = catalog,
            definitionsById = definitionsById,
            selectedIds = selectedExercisesIds,
            previous = selectedRows.value,
            draftByDefinition = draftByDefinition.value,
            initialCatalogDefinitionId = initialCatalogDefinitionId,
            initialCatalogConfigurationId = initialCatalogConfigurationId,
        )
        if (next != selectedRows.value) {
            selectedRows.value = next
        }
    }
    var selectedListExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedRows.value.isEmpty()) {
        if (selectedRows.value.isEmpty()) selectedListExpanded = false
    }
    // La ficha de hoy (expandedDefinitionId) y las variantes desplegadas bajo la fila (variantsDefinitionId) son estados distintos.
    var expandedDefinitionId by rememberSaveable { mutableStateOf(initialCatalogDefinitionId) }
    var variantsDefinitionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSupersetConfigurator by rememberSaveable { mutableStateOf(false) }
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val editingCustomExercise = remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    val deletingCustomExercise = remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    val normalizedCustomQuery = remember(query) { ExerciseMatchLexicon.normalize(query) }
    val visibleCustomExercises = remember(customExercises, normalizedCustomQuery) {
        customExercises.filter {
            normalizedCustomQuery.isBlank() || listOf(it.name, it.id, it.alias.orEmpty()).any { value ->
                ExerciseMatchLexicon.normalize(value).contains(normalizedCustomQuery)
            }
        }
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
    LaunchedEffect(searchHits) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }

    var bottomPanelHeight by remember { mutableStateOf(120) }
    val density = LocalDensity.current

    LaunchedEffect(expandedDefinitionId, definitions, visibleCustomExercises, bottomPanelHeight) {
        val targetId = expandedDefinitionId ?: return@LaunchedEffect
        val customOffset = if (visibleCustomExercises.isNotEmpty()) visibleCustomExercises.size + 1 else 0
        val indexInDefinitions = definitions.indexOfFirst { it.id == targetId }
        if (indexInDefinitions < 0) return@LaunchedEffect
        val targetIndex = 1 + customOffset + indexInDefinitions
        // Keep the expanded card header below the fixed catalog header. A
        // size-based centering pass can place the title behind that header as
        // soon as the card contains a medium-sized image and long accordions.
        listState.scrollToItem(targetIndex, scrollOffset = -8)
    }

    // La isla solo con algo elegido, fuera de la edición de una sola pieza y sin teclado abierto.
    val showIsland = !editingExisting && selectedRows.value.isNotEmpty() && !imeVisible
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().drawWithContent {
                drawContent()
                com.example.kpkn.screens.workout.RepairBenchmarkTrace.pickerResultsDrawn(
                    requestId = benchmarkRequestId,
                    definitionCount = definitions.size,
                    searchSettled = searchSettled,
                )
                com.example.kpkn.screens.workout.RepairBenchmarkTrace.pickerSearchResultsDrawn(
                    requestId = benchmarkSearchRequestId,
                    query = query,
                    latestRequestedQuery = benchmarkLatestRequestedQuery,
                    committedQuery = searchState.committedQuery,
                    projectionReady = catalogProjection != null,
                    isSettled = searchState.isSettled,
                    resultCount = definitions.size + visibleCustomExercises.size,
                )
            },
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 4.dp,
                end = 16.dp,
                bottom = if (showIsland) with(density) { bottomPanelHeight.toDp() } + 8.dp else 16.dp,
            ),
        ) {
            item("result-count") {
                Text(
                    if (query.isBlank()) "Todos los ejercicios" else "Resultados para «$query»",
                    color = WizardColors.textMuted,
                    style = KpknType.caption,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (visibleCustomExercises.isNotEmpty()) {
                item("custom-heading") {
                    Text(
                        "Ejercicios personalizados",
                        color = WizardColors.textMuted,
                        style = KpknType.caption,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(visibleCustomExercises, key = { "custom:" + it.id }, contentType = { "custom" }) { custom ->
                    CatalogCustomExerciseItem(
                        exercise = custom,
                        selected = custom.id in selectedRows.value,
                        editingExisting = editingExisting,
                        onSelect = { onSelect(custom) },
                        onToggle = {
                            val next = if (custom.id in selectedRows.value) {
                                selectedRows.value - custom.id
                            } else {
                                selectedRows.value + (custom.id to custom)
                            }
                            selectedRows.value = next
                            onSelectionChange(next.values.toList())
                        },
                        onEdit = { editingCustomExercise.value = custom },
                        onDelete = { deletingCustomExercise.value = custom },
                    )
                }
            }

            if (catalogProjection == null) {
                item("loading-results") {
                    CatalogListSkeleton(rows = 5)
                }
            } else {
                items(definitions, key = { it.id }, contentType = { "catalog-def" }) { definition ->
                    val default = remember(definition) {
                        definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
                    }
                    val hasExplicitDraft = draftByDefinition.value.containsKey(definition.id)
                    // Sin seleccion explicita, el draft parte VACIO para que el panel
                    // expandido muestre TODOS los implementos del primer eje. Si se
                    // usara el default (maquina), los demas chips quedarian
                    // incompatibles y el filtro del UI los ocultaria.
                    val selectedOptions = if (hasExplicitDraft) {
                        draftByDefinition.value[definition.id].orEmpty()
                    } else {
                        emptyMap()
                    }
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
                    val effectiveExerciseInfo = remember(catalog, definition.id, resolvedConfigurationId) {
                        exactInfo(catalog, definition, resolvedConfigurationId)
                    }
                    val isSelected = definition.id in selectedRows.value ||
                        selectedConfigurationId?.let { it in selectedRows.value } == true ||
                        effectiveExerciseInfo?.id?.let { it in selectedRows.value } == true ||
                        selectedRows.value.values.any { info ->
                            info.catalogDefinitionId == definition.id &&
                                (info.catalogConfigurationId == resolvedConfigurationId || info.id == resolvedConfigurationId)
                        }
                    val isExpanded = expandedDefinitionId == definition.id
                    val hasOptions = definition.optionAxes.isNotEmpty()
                    val selectBringIntoView = remember(definition.id) { BringIntoViewRequester() }
                    LaunchedEffect(isExpanded, imeVisible) {
                        if (isExpanded) {
                            kotlinx.coroutines.delay(80)
                            selectBringIntoView.bringIntoView()
                        }
                    }
                    val defaultMuscles = remember(default) { default?.profile?.primaryMuscles.orEmpty() }
                    val selectedImplementation = effectiveSelectedOptions["implement"]
                    val imageVariants = remember(definition.id, resolvedConfigurationId, selectedImplementation) {
                        exerciseCatalogImageVariants(
                            definitionId = definition.id,
                            catalogConfigurationId = resolvedConfigurationId,
                            selectedImplementation = selectedImplementation,
                        )
                    }
                    val firstAxis = definition.optionAxes.firstOrNull()
                    val variantValues = remember(definition, firstAxis) {
                        if (firstAxis == null) emptyList()
                        else definition.configurations
                            .mapNotNull { it.selectedOptions[firstAxis] }
                            .distinct()
                    }
                    val selectOption: (axis: String, value: String) -> Unit = select@{ axis, value ->
                        if (axis == "implement" && value.equals("default", ignoreCase = true)) return@select
                        val newDraft = draftAfterAxisSelection(
                            definition = definition,
                            selectedOptions = selectedOptions,
                            axis = axis,
                            value = value,
                        )
                        draftByDefinition.value = draftByDefinition.value + (definition.id to newDraft)
                        if (definition.id in selectedRows.value) {
                            val nextConfigId = repository.compatibility(definition.id, newDraft).exactConfigurationId
                                ?: bestMatchingConfigurationId(definition, newDraft, default)
                            val info = exactInfo(catalog, definition, nextConfigId)
                            if (info != null) {
                                val key = info.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: info.id
                                selectedRows.value = selectedRows.value
                                    .filterKeys { it != definition.id } + (key to info)
                            }
                        }
                    }
                    // El círculo y el botón de la ficha hacen lo mismo: quitar si ya está elegido, añadir si no.
                    val toggleSelection: () -> Unit = {
                        if (isSelected) {
                            val next = selectedRows.value.filterKeys { key ->
                                key != definition.id &&
                                    selectedRows.value[key]?.catalogDefinitionId != definition.id
                            }
                            selectedRows.value = next
                            onSelectionChange(next.values.toList())
                        } else {
                            val info = exactInfo(catalog, definition, resolvedConfigurationId)
                            if (info != null) {
                                if (editingExisting) {
                                    onSelect(info)
                                } else {
                                    val key = info.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: info.id
                                    val next = selectedRows.value.filterKeys { it != definition.id } + (key to info)
                                    selectedRows.value = next
                                    onSelectionChange(next.values.toList())
                                }
                            }
                        }
                    }
                    val pattern = remember(definition.canonicalName) { exercisePatternOf(definition.canonicalName) }
                    val thumbnailResId = selectedCatalogImageVariantIndex(imageVariants, selectedImplementation)
                        ?.let { imageVariants[it].imageResId }
                    val subtitle = listOfNotNull(
                        defaultMuscles.firstOrNull()?.let(::exerciseCatalogMuscleLabel),
                        default?.selectedOptions?.get("implement")
                            ?.takeIf { it.isNotBlank() && it != "default" }
                            ?.let { exerciseCatalogOptionLabel(it, definition.id) },
                    ).joinToString(" · ")
                    val matchChips = searchMatchChips[definition.id].orEmpty()
                    val matchLabel = if (query.isNotBlank() && matchChips.isNotEmpty()) {
                        matchChips.take(3).joinToString(" · ") +
                            if (matchChips.size > 3) " · +${matchChips.size - 3}" else ""
                    } else {
                        null
                    }
                    val hasVariants = variantValues.isNotEmpty()
                    val variantsOpen = hasVariants && variantsDefinitionId == definition.id
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CatalogExerciseRow(
                            name = definition.canonicalName,
                            subtitle = subtitle.takeIf { it.isNotBlank() },
                            matchLabel = matchLabel,
                            thumbnailResId = thumbnailResId,
                            pattern = pattern,
                            selected = isSelected,
                            onClick = {
                                if (hasVariants) {
                                    variantsDefinitionId = toggleCatalogDefinitionExpansion(
                                        currentDefinitionId = variantsDefinitionId,
                                        tappedDefinitionId = definition.id,
                                    )
                                } else {
                                    expandedDefinitionId = toggleCatalogDefinitionExpansion(
                                        currentDefinitionId = expandedDefinitionId,
                                        tappedDefinitionId = definition.id,
                                    )
                                }
                            },
                            onToggleSelection = toggleSelection,
                            expandable = true,
                            expanded = if (hasVariants) variantsOpen else isExpanded,
                            onLongClick = if (!hasOptions) {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleSelection()
                                }
                            } else {
                                null
                            },
                        )
                        if (variantsOpen) {
                            CatalogVariantChips(
                                values = variantValues,
                                labelOf = { value -> exerciseCatalogVariantTagLabel(value, definition.id) },
                                selectedValue = if (hasExplicitDraft) firstAxis?.let(effectiveSelectedOptions::get) else null,
                                onSelect = { value -> selectOption("implement", value) },
                                onOpenFicha = {
                                    expandedDefinitionId = toggleCatalogDefinitionExpansion(
                                        currentDefinitionId = expandedDefinitionId,
                                        tappedDefinitionId = definition.id,
                                    )
                                },
                            )
                        }
                        if (isExpanded) {
                            // La ficha de hoy: la imagen, las opciones, la descripción y la implicación muscular.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (imageVariants.isNotEmpty()) {
                                    CatalogExerciseImageCarousel(
                                        variants = imageVariants,
                                        selectedImplementation = selectedImplementation,
                                        expanded = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(176.dp),
                                        onImplementationSettled = { implementation ->
                                            selectOption("implement", implementation)
                                        },
                                    )
                                }
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
                                    axesWithOptions.forEachIndexed { axisIndex, (axis, visibleOptions) ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                "${axisIndex + 1}. ${exerciseCatalogAxisLabel(axis.axis, definition.id)}",
                                                color = Color.White.copy(alpha = 0.72f),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                visibleOptions.forEach { option ->
                                                    AxisChip(
                                                        value = option.value,
                                                        definitionId = definition.id,
                                                        selected = hasExplicitDraft && effectiveSelectedOptions[axis.axis] == option.value,
                                                        enabled = option.enabled,
                                                        onClick = { selectOption(axis.axis, option.value) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                CatalogDescription(
                                    definition = definition,
                                    configuration = effectiveConfiguration,
                                    initiallyExpanded = !hasOptions,
                                )

                                CatalogInvolvementAccordions(
                                    joints = effectiveConfiguration?.profile?.jointInvolvement
                                        ?.takeIf { it.isNotEmpty() }
                                        .orEmpty(),
                                    info = effectiveExerciseInfo?.takeIf { it.involvedMuscles.isNotEmpty() },
                                    patternId = effectiveConfiguration?.profile?.movementPatternId,
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
                                        toggleSelection()
                                        expandedDefinitionId = null
                                    },
                                    enabled = resolvedConfigurationId != null || !hasOptions,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(selectBringIntoView),
                                ) {
                                    Text(if (isSelected) "Deseleccionar ejercicio" else "Seleccionar ejercicio")
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(WizardColors.divider),
                        )
                    }
                }
            }

            // Creation is a terminal empty-state action, never a banner for a
            // merely non-exact query. Use the unfiltered catalog result so an
            // active body/muscle filter cannot suggest duplicating an exercise that
            // exists elsewhere in the catalog.
            if (shouldShowCatalogCreateSuggestion(
                    query = query,
                    searchSettled = searchSettled,
                    visibleCatalogResultCount = definitions.size,
                    globalCatalogResultCount = globalSearchHits.size,
                    customResultCount = visibleCustomExercises.size,
                )
            ) {
                item("smart-create") {
                    SmartCreateSuggestion(
                        query = query,
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
        }

        // La isla flota abajo: resumen, «Superserie» si se puede y «Añadir N». Entra con un fundido y 8 dp de subida.
        CatalogSelectionSlot(visible = showIsland, reducedMotion = reducedMotion) {
            val supersetAvailable = selectedRows.value.size >= 2 &&
                (onCreateSupersetConfigured != null || onCreateSuperset != null || isSupersetAddMode)
            CatalogSelectionIsland(
                count = selectedRows.value.size,
                summary = catalogSelectionSummary(selectedRows.value.values.map { it.name }),
                listExpanded = selectedListExpanded,
                onToggleList = { selectedListExpanded = !selectedListExpanded },
                onAdd = {
                    val ids = onMultiSelect(selectedRows.value.values.toList())
                    if (ids.isNotEmpty() && dismissAfterMultiSelect) onDismiss()
                },
                onSuperset = if (supersetAvailable) {
                    {
                        if (isSupersetAddMode) {
                            val ids = onMultiSelect(selectedRows.value.values.toList())
                            if (ids.isNotEmpty() && dismissAfterMultiSelect) onDismiss()
                        } else {
                            showSupersetConfigurator = true
                        }
                    }
                } else {
                    null
                },
                hazeState = hazeState,
                modifier = Modifier.onSizeChanged { bottomPanelHeight = it.height },
                detail = {
                    SelectedExercisesList(
                        selected = selectedRows.value.entries.toList(),
                        onRemove = { id ->
                            val next = selectedRows.value - id
                            selectedRows.value = next
                            onSelectionChange(next.values.toList())
                        },
                        onMove = { index, delta ->
                            val ids = selectedRows.value.keys.toMutableList()
                            val toIndex = (index + delta).coerceIn(0, ids.lastIndex)
                            if (toIndex == index) return@SelectedExercisesList
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
                },
            )
        }
    }

    if (showSupersetConfigurator) {
        CatalogSupersetConfiguratorDialog(
            selectedExercises = selectedRows.value.values.toList(),
            onDismiss = { showSupersetConfigurator = false },
            onConfirm = { config ->
                showSupersetConfigurator = false
                val selectedList = selectedRows.value.values.toList()
                if (onCreateSupersetConfigured != null) {
                    onCreateSupersetConfigured(selectedList, config)
                } else {
                    onCreateSuperset?.invoke(selectedList)
                }
                selectedRows.value = emptyMap()
                onSelectionChange(emptyList())
                if (dismissAfterMultiSelect) onDismiss()
            },
        )
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



/** Músculos principales para el filtro "Por Músculo". */
private val CATALOG_FILTER_MUSCLE_IDS = listOf(
    "pectoralis", "latissimus_dorsi", "deltoid", "biceps", "triceps", "forearm",
    "quadriceps", "hamstrings", "gluteus_maximus", "adductors", "calves",
    "erector_spinae", "core", "trapezius", "neck",
)

@Composable
internal fun CatalogDescription(
    definition: ExerciseDefinitionV2,
    configuration: com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2?,
    initiallyExpanded: Boolean = false,
) {
    val configurationId = configuration?.id
    var descriptionExpanded by remember(definition.id, configurationId, initiallyExpanded) {
        mutableStateOf(initiallyExpanded)
    }
    // La descripción principal de la definición es la única copia visible.
    val definitionDescription = definition.description

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
                definitionDescription,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Los involucramientos (muscular y articular) se despliegan como acordeón,
 * con el muscular primero y el articular debajo, para ahorrar espacio.
 */
@Composable
internal fun CatalogInvolvementAccordions(
    info: ExerciseMuscleInfo?,
    joints: List<JointInvolvementV2>,
    patternId: String? = null,
) {
    var muscleExpanded by remember { mutableStateOf(false) }
    var jointExpanded by remember { mutableStateOf(false) }
    var canonicalExplain by remember { mutableStateOf<CanonicalKnowledge?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        info?.let { muscleInfo ->
            CatalogInfoAccordion(
                title = "Involucramiento Muscular",
                expanded = muscleExpanded,
                onToggle = { muscleExpanded = !muscleExpanded },
            ) {
                MuscleInvolvementSection(
                    muscleInfo,
                    showHeader = false,
                    onKnowledge = { canonicalExplain = it },
                )
            }
        }
        if (joints.isNotEmpty()) {
            CatalogInfoAccordion(
                title = "Involucramiento Articular",
                expanded = jointExpanded,
                onToggle = { jointExpanded = !jointExpanded },
            ) {
                JointInvolvementSection(
                    joints,
                    showHeader = false,
                    onKnowledge = { canonicalExplain = it },
                )
            }
        }
        patternId?.let { id ->
            canonicalPatternKnowledge(id)?.let { pattern ->
                Surface(
                    modifier = Modifier
                        .clickable { canonicalExplain = pattern },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Text(
                        "Patrón · ${pattern.name}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        canonicalExplain?.let { knowledge ->
            CanonicalKnowledgeOverlay(
                knowledge = knowledge,
                onDismiss = { canonicalExplain = null },
            )
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

internal val JOINT_LABELS = mapOf(
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

internal fun catalogTitleLabel(value: String): String = value
    .split(" ")
    .joinToString(" ") { word ->
        word.replaceFirstChar { character -> character.uppercaseChar() }
    }

internal fun jointLabel(id: String): String = JOINT_LABELS[id] ?: id.replace('-', ' ').replaceFirstChar { it.uppercaseChar() }

internal fun jointRoleLabel(role: JointRoleV2): String = when (role) {
    JointRoleV2.PRIMARY -> "Principal"
    JointRoleV2.SECONDARY -> "Secundaria"
    JointRoleV2.STABILIZER -> "Estabilizadora"
}

@Composable
private fun JointInvolvementSection(
    joints: List<JointInvolvementV2>,
    showHeader: Boolean = true,
    onKnowledge: (CanonicalKnowledge) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showHeader) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showHeader) {
            Text("Involucramiento Articular", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            Text(
                "Articulaciones que transmiten, generan o estabilizan la carga en la configuración elegida.",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        joints.forEach { joint ->
            val knowledge = canonicalJointKnowledge(joint.jointId)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (knowledge != null) Modifier.clickable { onKnowledge(knowledge) } else Modifier),
                shape = RoundedCornerShape(9.dp),
                color = Color.White.copy(alpha = 0.045f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            jointLabel(joint.jointId),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.08f),
                        ) {
                            Text(
                                jointRoleLabel(joint.role),
                                color = Color.White.copy(alpha = 0.66f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    if (joint.actions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            joint.actions.forEach { action ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        catalogTitleLabel(action),
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
        Text(
            "Toca una articulación para abrir su introducción canónica cuando esté disponible.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun MuscleInvolvementSection(
    exercise: ExerciseMuscleInfo,
    showHeader: Boolean = true,
    onKnowledge: (CanonicalKnowledge) -> Unit = {},
) {
    val contributions = remember(exercise) { oneSeriesVolumeContributions(exercise) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showHeader) 4.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (showHeader) {
            Text(
                "Involucramiento muscular",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            "Aporte equivalente por una serie planificada",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall,
        )
        if (contributions.isEmpty()) {
            Text(
                "No hay un desglose canónico disponible para esta configuración.",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            contributions.forEach { contribution ->
                val knowledge = canonicalMuscleKnowledgeForVolumeLabel(contribution.muscle)
                val clickableModifier = if (knowledge != null) {
                    Modifier.clickable { onKnowledge(knowledge) }
                } else {
                    Modifier
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .then(clickableModifier),
                    shape = RoundedCornerShape(9.dp),
                    color = Color.White.copy(alpha = 0.045f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                catalogTitleLabel(contribution.muscle),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                roleVolumeLabel(contribution.role).substringBefore(" · "),
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(
                            "${formatSeriesEquivalent(contribution.seriesEquivalent)} · ${formatVolumePercent(contribution.seriesEquivalent)}",
                            color = Color.White.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
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

/**
 * Lo elegido, desplegado desde la isla del catálogo (SE6a): cada fila lleva su nombre (un toque lleva a su ejercicio en la lista), las
 * flechas para subir y bajar en el orden de selección, y la X para quitarlo. El orden decide el de una superserie.
 */
@Composable
private fun SelectedExercisesList(
    selected: List<Map.Entry<String, ExerciseMuscleInfo>>,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onTap: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        selected.forEachIndexed { index, (id, info) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    KpknIconButton(
                        icon = KpknLineIcons.ChevronDown,
                        contentDescription = "Subir ${info.name} en el orden",
                        onClick = { onMove(index, -1) },
                        modifier = Modifier.graphicsLayer { rotationZ = 180f },
                    )
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                if (index < selected.lastIndex) {
                    KpknIconButton(
                        icon = KpknLineIcons.ChevronDown,
                        contentDescription = "Bajar ${info.name} en el orden",
                        onClick = { onMove(index, 1) },
                    )
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Text(
                    text = info.name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = { onTap(id) })
                        .padding(horizontal = 8.dp),
                )
                KpknIconButton(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Quitar ${info.name}",
                    onClick = { onRemove(id) },
                )
            }
        }
    }
}

/**
 * Ejercicio personalizado en la lista (SE6a): la misma fila que el catálogo, sin imagen (el glifo de su nombre). Al tocarla despliega su
 * descripción, sus músculos y «Editar» o «Eliminar»; el círculo lo elige, como en el catálogo.
 */
@Composable
private fun CatalogCustomExerciseItem(
    exercise: ExerciseMuscleInfo,
    selected: Boolean,
    editingExisting: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val primaryMuscles = remember(exercise) {
        exercise.involvedMuscles
            .filter { it.role == MuscleRole.PRIMARY }
            .map { it.muscle }
            .distinct()
            .take(3)
    }
    val pattern = remember(exercise.name) { exercisePatternOf(exercise.name) }
    Column(modifier = Modifier.fillMaxWidth()) {
        CatalogExerciseRow(
            name = exercise.name,
            subtitle = primaryMuscles.takeIf { it.isNotEmpty() }?.joinToString(" · ") { exerciseCatalogMuscleLabel(it) },
            matchLabel = null,
            thumbnailResId = null,
            pattern = pattern,
            selected = selected,
            onClick = { expanded = !expanded },
            onToggleSelection = {
                if (selected) {
                    onToggle()
                } else if (editingExisting) {
                    onSelect()
                } else {
                    onToggle()
                }
            },
            expandable = true,
            expanded = expanded,
        )
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                exercise.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        desc,
                        color = WizardColors.textMuted,
                        style = KpknType.caption,
                    )
                }
                if (exercise.catalogVariantChips.isNotEmpty()) {
                    Text(
                        exercise.catalogVariantChips.joinToString(" · "),
                        color = WizardColors.textFaint,
                        style = KpknType.caption,
                    )
                }
                if (exercise.involvedMuscles.isNotEmpty()) {
                    MuscleInvolvementSection(exercise)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknTextAction(text = "Editar", onClick = onEdit)
                    KpknTextAction(text = "Eliminar", onClick = onDelete, color = WizardColors.danger)
                }
            }
        }
    }
}

/**
 * «No encontramos…» con «Crear ejercicio» (SE6a): crea el ejercicio con el nombre buscado, de forma automática, como antes, y lo elige.
 */
@Composable
private fun SmartCreateSuggestion(
    query: String,
    onCreate: (ExerciseMuscleInfo) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalog = remember { exerciseCatalogSnapshot() }
    CatalogNoResults(
        query = query,
        creating = creating,
        onCreateClick = {
            if (!creating) {
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
            }
        },
        modifier = Modifier.padding(top = 16.dp),
    )
}


/**
 * Título adaptativo para el catálogo: escala progresivamente la tipografía y permite hasta
 * dos líneas completas sin truncar el nombre antes de tiempo.
 */
@Composable
internal fun CatalogAdaptiveExerciseTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    baseStyle: TextStyle = MaterialTheme.typography.titleLarge,
    minFontSize: TextUnit = 11.sp,
    color: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    var scaledFontSize by remember(text, baseStyle.fontSize) {
        mutableStateOf(baseStyle.fontSize)
    }
    var readyToDraw by remember(text, baseStyle.fontSize) {
        mutableStateOf(false)
    }

    Text(
        text = text,
        modifier = modifier
            .semantics { contentDescription = text }
            .drawWithContent {
            if (readyToDraw) drawContent()
        },
        color = color,
        fontWeight = fontWeight,
        style = baseStyle.copy(fontSize = scaledFontSize),
        maxLines = maxLines,
        overflow = TextOverflow.Visible,
        softWrap = true,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && scaledFontSize.value > minFontSize.value) {
                val nextValue = scaledFontSize.value * 0.9f
                scaledFontSize = if (nextValue < minFontSize.value) minFontSize else nextValue.sp
            } else {
                readyToDraw = true
            }
        },
    )
}

internal fun formatCatalogSupersetRestLabel(seconds: Int): String {
    return if (seconds > 60) {
        val mins = seconds / 60
        val secs = seconds % 60
        "$mins:${secs.toString().padStart(2, '0')}"
    } else {
        "${seconds}s"
    }
}

/**
 * Diálogo compartido de configuración de superserie antes de confirmarla desde el catálogo.
 */
@Composable
internal fun CatalogSupersetConfiguratorDialog(
    selectedExercises: List<ExerciseMuscleInfo>,
    initialConfig: CatalogSupersetConfig = CatalogSupersetConfig(),
    onDismiss: () -> Unit,
    onConfirm: (CatalogSupersetConfig) -> Unit,
) {
    var rounds by remember { mutableStateOf(initialConfig.rounds) }
    var restBetween by remember { mutableStateOf(initialConfig.restBetweenExercisesSeconds) }
    var restAfter by remember { mutableStateOf(initialConfig.restAfterSupersetSeconds) }

    KpknGlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Configurar Superserie",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )

            // Lista de ejercicios seleccionados
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Ejercicios (${selectedExercises.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
                selectedExercises.forEachIndexed { idx, info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF67E8F9).copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${('A' + idx)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF67E8F9),
                                )
                            }
                        }
                        Text(
                            info.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Rondas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Rondas",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = { if (rounds > 1) rounds-- },
                        enabled = rounds > 1,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text("−", color = if (rounds > 1) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Text(
                        "$rounds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    IconButton(
                        onClick = { if (rounds < 10) rounds++ },
                        enabled = rounds < 10,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text("+", color = if (rounds < 10) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }

            // Descanso entre ejercicios
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Descanso entre ejercicios",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { restBetween = (restBetween - 15).coerceAtLeast(0) },
                            enabled = restBetween > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Text("−", color = if (restBetween > 0) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            formatCatalogSupersetRestLabel(restBetween),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF67E8F9),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(
                            onClick = { restBetween = (restBetween + 15).coerceAtMost(600) },
                            enabled = restBetween < 600,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Text("+", color = if (restBetween < 600) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0, 30, 60, 90).forEach { sec ->
                        val isSelected = restBetween == sec
                        Surface(
                            onClick = { restBetween = sec },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF67E8F9).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF67E8F9) else Color.Transparent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    formatCatalogSupersetRestLabel(sec),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            // Descanso tras superserie
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Descanso tras superserie",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { restAfter = (restAfter - 15).coerceAtLeast(0) },
                            enabled = restAfter > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Text("−", color = if (restAfter > 0) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            formatCatalogSupersetRestLabel(restAfter),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF67E8F9),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(
                            onClick = { restAfter = (restAfter + 15).coerceAtMost(600) },
                            enabled = restAfter < 600,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Text("+", color = if (restAfter < 600) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(60, 90, 120, 180).forEach { sec ->
                        val isSelected = restAfter == sec
                        Surface(
                            onClick = { restAfter = sec },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF67E8F9).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF67E8F9) else Color.Transparent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    formatCatalogSupersetRestLabel(sec),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onConfirm(CatalogSupersetConfig(rounds = rounds, restBetweenExercisesSeconds = restBetween, restAfterSupersetSeconds = restAfter))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
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

/**
 * Resolves the configuration to persist for a selected definition.
 * Never falls back to `configurations.first()`: that array order is editorial,
 * not the user's choice (e.g. t_bar_row starts with machine/wide).
 */
internal fun resolveSelectedCatalogConfigurationId(
    definition: ExerciseDefinitionV2,
    draftOptions: Map<String, String>?,
    previousConfigurationId: String?,
    initialConfigurationId: String?,
): String? {
    fun exists(id: String?) = !id.isNullOrBlank() && definition.configurations.any { it.id == id }
    if (exists(previousConfigurationId)) return previousConfigurationId
    val default = definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
    val fromDraft = draftOptions?.takeIf { it.isNotEmpty() }?.let { options ->
        bestMatchingConfigurationId(definition, options, default)
    }
    if (fromDraft != null) return fromDraft
    if (exists(initialConfigurationId)) return initialConfigurationId
    return default?.id
}

internal fun restoreSelectedCatalogRows(
    catalog: ExerciseCatalogV2,
    definitionsById: Map<String, ExerciseDefinitionV2>,
    selectedIds: Collection<String>,
    previous: Map<String, ExerciseMuscleInfo>,
    draftByDefinition: Map<String, Map<String, String>>,
    initialCatalogDefinitionId: String? = null,
    initialCatalogConfigurationId: String? = null,
): Map<String, ExerciseMuscleInfo> {
    val restored = linkedMapOf<String, ExerciseMuscleInfo>()
    selectedIds.forEach { selectedId ->
        val definitionById = definitionsById[selectedId]
        val definitionByConfig = if (definitionById == null) {
            definitionsById.values.firstOrNull { definition ->
                definition.configurations.any { it.id.equals(selectedId, ignoreCase = true) }
            }
        } else {
            null
        }
        val definition = definitionById ?: definitionByConfig
        if (definition == null) {
            previous[selectedId]?.let { restored[selectedId] = it }
            return@forEach
        }
        val previousInfo = previous[definition.id]
        val configurationId = if (definitionByConfig != null) {
            definition.configurations.first { it.id.equals(selectedId, ignoreCase = true) }.id
        } else {
            resolveSelectedCatalogConfigurationId(
                definition = definition,
                draftOptions = draftByDefinition[definition.id],
                previousConfigurationId = previousInfo?.catalogConfigurationId,
                initialConfigurationId = initialCatalogConfigurationId.takeIf {
                    initialCatalogDefinitionId == null || initialCatalogDefinitionId == definition.id
                },
            )
        }
        val info = exactInfo(catalog, definition, configurationId) ?: previousInfo
        if (info != null) {
            val key = info.catalogConfigurationId?.takeIf { it.isNotBlank() } ?: info.id
            restored[key] = info
        }
    }
    return restored
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
