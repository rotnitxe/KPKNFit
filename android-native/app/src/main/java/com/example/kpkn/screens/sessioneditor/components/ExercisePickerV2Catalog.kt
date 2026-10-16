package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.drawWithContent
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchFiltersV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSearchHitV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
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
import com.example.kpkn.ui.components.KpknGlass
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
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.sessioneditor.design.CatalogSupersetConfiguratorDialog
import com.example.kpkn.screens.sessioneditor.design.SmartExerciseEditorPage

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

/** Rótulo de la fila del ejercicio que se reemplaza (LV5). */
internal const val CatalogCurrentExerciseLabel = "Ejercicio actual"

/**
 * El catálogo se abre para reemplazar un ejercicio (LV5). [exerciseName] es el que ve la persona («Reemplazando: …»); puede faltar si el
 * ejercicio no tiene nombre resoluble, y entonces la cabecera solo cambia de título.
 */
internal data class CatalogReplacementContext(val exerciseName: String? = null)

/** El ejercicio que se reemplaza, ya localizado en el catálogo: de él salen el patrón y los músculos que ordenan la lista (LV5). */
internal data class CatalogReplacementAnchor(
    val definitionId: String,
    val movementPatternId: String?,
    val primaryMuscles: Set<String>,
)

/**
 * Localiza en el catálogo el ejercicio que se reemplaza. Prefiere su configuración exacta; si solo se conoce la definición, usa la
 * configuración por defecto. Devuelve nulo para un ejercicio sin identidad de catálogo (el orden alfabético se queda como está).
 */
internal fun catalogReplacementAnchorFor(
    definitionsById: Map<String, ExerciseDefinitionV2>,
    definitionId: String?,
    configurationId: String?,
): CatalogReplacementAnchor? {
    val definition = definitionId?.let(definitionsById::get)
        ?: configurationId?.takeIf { it.isNotBlank() }?.let { id ->
            definitionsById.values.firstOrNull { candidate -> candidate.configurations.any { it.id == id } }
        }
        ?: return null
    val configuration = definition.configurations.firstOrNull { it.id == configurationId }
        ?: definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
        ?: definition.configurations.firstOrNull()
        ?: return null
    return CatalogReplacementAnchor(
        definitionId = definition.id,
        movementPatternId = configuration.profile.movementPatternId.takeIf { it.isNotBlank() },
        primaryMuscles = configuration.profile.primaryMuscles.toSet(),
    )
}

/**
 * Orden de la lista al reemplazar (LV5): primero el ejercicio actual, luego los de su mismo patrón de movimiento, luego los que
 * comparten un músculo principal y al final el resto. Dentro de cada grupo se conserva el orden que traía la lista (alfabético), así que
 * no se quita ni se esconde ningún ejercicio: solo cambia por dónde se empieza a mirar.
 */
internal fun orderDefinitionsForReplacement(
    definitions: List<ExerciseDefinitionV2>,
    anchor: CatalogReplacementAnchor?,
): List<ExerciseDefinitionV2> {
    if (anchor == null || definitions.size < 2) return definitions
    fun tier(definition: ExerciseDefinitionV2): Int = when {
        definition.id == anchor.definitionId -> 0
        anchor.movementPatternId != null &&
            definition.configurations.any { it.profile.movementPatternId == anchor.movementPatternId } -> 1
        anchor.primaryMuscles.isNotEmpty() &&
            definition.configurations.any { configuration -> configuration.profile.primaryMuscles.any { it in anchor.primaryMuscles } } -> 2
        else -> 3
    }
    return definitions.sortedBy(::tier)
}

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

/** Internal (ER1f): la ficha del editor usa el mismo cálculo de imágenes que el catálogo. */
internal fun exerciseCatalogImageVariants(
    definitionId: String,
    catalogConfigurationId: String? = null,
    selectedImplementation: String? = null,
): List<ExerciseTechniqueImageVariant> {
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

/**
 * El implemento cuya imagen se enseña en la fila y en la ficha (LV5): el que la persona eligió; si no eligió ninguno, el de la
 * configuración que «Añadir» añadiría ([resolvedConfigurationId], la de por defecto mientras no haya elección). Nunca «la primera foto
 * de la lista»: el orden de las fotos es del archivo de imágenes y no tiene por qué coincidir con el material que dice la fila.
 * Nulo cuando la definición no tiene implemento (la lista de fotos solo trae la suya).
 */
internal fun catalogShownImplementation(
    definition: ExerciseDefinitionV2,
    selectedOptions: Map<String, String>,
    resolvedConfigurationId: String?,
): String? = selectedOptions["implement"]
    ?: definition.configurations.firstOrNull { it.id == resolvedConfigurationId }?.selectedOptions?.get("implement")

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
    replacement: CatalogReplacementContext? = null,
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
                isReplacement = replacement != null,
                replacingName = replacement?.exerciseName,
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
                    replacement = replacement,
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
    replacement: CatalogReplacementContext? = null,
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
    val visibleDefinitions = visibleDefinitionsForQuery(
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
    // Al reemplazar, la lista arranca con el ejercicio actual y los de su patrón (LV5). Una búsqueda manda: su orden es el de relevancia.
    val replacementAnchor = remember(replacement != null, definitionsById, initialCatalogDefinitionId, initialCatalogConfigurationId) {
        if (replacement == null) {
            null
        } else {
            catalogReplacementAnchorFor(definitionsById, initialCatalogDefinitionId, initialCatalogConfigurationId)
        }
    }
    val orderedByReplacement = replacementAnchor != null && query.isBlank()
    val definitions = remember(visibleDefinitions, replacementAnchor, orderedByReplacement) {
        if (orderedByReplacement) orderDefinitionsForReplacement(visibleDefinitions, replacementAnchor) else visibleDefinitions
    }
    LaunchedEffect(catalogProjection, visibleDefinitions, searchSettled, query) {
        if (searchSettled || query.isBlank()) lastStableDefinitions = visibleDefinitions
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
    // La ficha nunca se abre sola (LV5): al reemplazar, el ejercicio actual ya encabeza la lista y su ficha queda a un toque. Antes
    // arrancaba en el ejercicio actual y «Reemplazar» caía en su ficha en vez de en el catálogo.
    var expandedDefinitionId by rememberSaveable { mutableStateOf<String?>(null) }
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
                    when {
                        query.isNotBlank() -> "Resultados para «$query»"
                        orderedByReplacement -> "Primero los más parecidos al ejercicio actual"
                        else -> "Todos los ejercicios"
                    },
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
                    val defaultMuscles = remember(default) { default?.profile?.primaryMuscles.orEmpty() }
                    val selectedImplementation = effectiveSelectedOptions["implement"]
                    // La imagen es la del implemento que se ve: el elegido y, si no hay elección, el de la configuración que «Añadir»
                    // añadiría. Antes, sin elección se enseñaba la primera foto de la lista aunque la fila dijera otro material (LV5).
                    val shownImplementation = catalogShownImplementation(definition, effectiveSelectedOptions, resolvedConfigurationId)
                    val imageVariants = remember(definition.id, resolvedConfigurationId, shownImplementation) {
                        exerciseCatalogImageVariants(
                            definitionId = definition.id,
                            catalogConfigurationId = resolvedConfigurationId,
                            selectedImplementation = shownImplementation,
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
                    val thumbnailResId = selectedCatalogImageVariantIndex(imageVariants, shownImplementation)
                        ?.let { imageVariants[it].imageResId }
                    val muscleLabel = defaultMuscles.firstOrNull()?.let(::exerciseCatalogMuscleLabel)
                    val materialLabel = default?.selectedOptions?.get("implement")
                        ?.takeIf { it.isNotBlank() && it != "default" }
                        ?.let { exerciseCatalogOptionLabel(it, definition.id) }
                    // La ficha nombra el implemento elegido ahora (CF1); la tarjeta sigue con el de la configuración por defecto.
                    val fichaMaterialLabel = catalogFichaMaterialLabel(
                        selectedImplementation = selectedImplementation,
                        defaultImplementation = default?.selectedOptions?.get("implement"),
                        definitionId = definition.id,
                    )
                    val subtitle = listOfNotNull(muscleLabel, materialLabel).joinToString(" · ")
                    // La línea de coincidencias no repite lo que la fila ya dice (músculo y material), y sin búsqueda no se pinta (SE6b).
                    val searchMatchLabel = catalogMatchLabel(
                        matchChips = searchMatchChips[definition.id].orEmpty(),
                        shownInSubtitle = listOfNotNull(muscleLabel, materialLabel),
                        query = query,
                    )
                    // El ejercicio que se reemplaza se reconoce en la lista (LV5), con o sin búsqueda.
                    val matchLabel = if (replacementAnchor?.definitionId == definition.id) {
                        listOfNotNull(CatalogCurrentExerciseLabel, searchMatchLabel).joinToString(" · ")
                    } else {
                        searchMatchLabel
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
                                // Las variantes son las del primer eje de la definición, que no siempre es el implemento (agarre,
                                // lateralidad, ángulo…): elegir una cambia ESE eje. Antes llamaba siempre a «implement» y no hacía nada (LV5).
                                onSelect = { value -> selectOption(firstAxis ?: "implement", value) },
                                onOpenFicha = {
                                    expandedDefinitionId = toggleCatalogDefinitionExpansion(
                                        currentDefinitionId = expandedDefinitionId,
                                        tappedDefinitionId = definition.id,
                                    )
                                },
                            )
                        }
                        if (isExpanded) {
                            // La ficha de hoy: una hoja casi a pantalla completa (SE6b). «Añadir» o «Quitar» la cierran, como antes.
                            ExerciseCatalogInfoDialog(
                                model = CatalogDetailModel(
                                    name = definition.canonicalName,
                                    caption = listOfNotNull(
                                        muscleLabel,
                                        fichaMaterialLabel,
                                        pattern.label.takeIf { pattern != ExercisePattern.GENERIC },
                                    ).joinToString(" · ").takeIf { it.isNotBlank() },
                                    description = definition.description.takeIf { it.isNotBlank() },
                                    imageVariants = imageVariants,
                                    selectedImplementation = shownImplementation,
                                    axes = if (hasOptions) {
                                        compatibility.axes.map { axis ->
                                            CatalogDetailAxis(
                                                axis = axis.axis,
                                                label = exerciseCatalogAxisLabel(axis.axis, definition.id),
                                                options = axis.options
                                                    .filter { it.enabled || effectiveSelectedOptions[axis.axis] == it.value }
                                                    .map { option ->
                                                        CatalogDetailOption(
                                                            value = option.value,
                                                            label = exerciseCatalogOptionLabel(option.value, definition.id),
                                                            selected = hasExplicitDraft && effectiveSelectedOptions[axis.axis] == option.value,
                                                            enabled = option.enabled,
                                                        )
                                                    },
                                            )
                                        }
                                    } else {
                                        emptyList()
                                    },
                                    configurationLine = effectiveConfiguration
                                        ?.let { exerciseCatalogConfigurationSummary(it, definition.id) }
                                        ?: "Configuración incompleta: selecciona los chips restantes",
                                    configurationIncomplete = effectiveConfiguration == null,
                                    muscleInfo = effectiveExerciseInfo?.takeIf { it.involvedMuscles.isNotEmpty() },
                                    joints = effectiveConfiguration?.profile?.jointInvolvement
                                        ?.takeIf { it.isNotEmpty() }
                                        .orEmpty(),
                                    patternId = effectiveConfiguration?.profile?.movementPatternId,
                                    isSelected = isSelected,
                                    canSelect = resolvedConfigurationId != null || !hasOptions,
                                ),
                                onDismiss = { expandedDefinitionId = null },
                                onToggleSelection = {
                                    toggleSelection()
                                    expandedDefinitionId = null
                                },
                                onSelectOption = selectOption,
                                onImplementationSettled = { implementation -> selectOption("implement", implementation) },
                            )
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
 * La línea de coincidencias de una fila (SE6b): sin lo que la fila ya dice en su línea de músculo y material, y nunca sin búsqueda.
 * «Dorsales · Peso corporal» con la coincidencia «Peso corporal · Agarre prono» deja solo «Agarre prono».
 */
internal fun catalogMatchLabel(
    matchChips: List<String>,
    shownInSubtitle: List<String>,
    query: String,
): String? {
    if (query.isBlank()) return null
    val shown = shownInSubtitle.map { it.trim().lowercase() }.toSet()
    val rest = matchChips.filter { it.trim().lowercase() !in shown }
    if (rest.isEmpty()) return null
    return rest.take(3).joinToString(" · ") +
        if (rest.size > 3) " · +${rest.size - 3}" else ""
}

/** Etiqueta de un descanso de la superserie: «45 s» por debajo de un minuto y «1:30» a partir de ahí ([KpknSetFormat.doseTime]). */
internal fun formatCatalogSupersetRestLabel(seconds: Int): String = KpknSetFormat.doseTime(seconds)

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
