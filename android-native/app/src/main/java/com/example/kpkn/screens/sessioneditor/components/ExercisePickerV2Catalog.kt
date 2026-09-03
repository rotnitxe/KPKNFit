package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.example.kpkn.R
import com.example.kpkn.screens.sessioneditor.CatalogCommitAction
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
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
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
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

private typealias CatalogExerciseImageVariant = ExerciseTechniqueImageVariant

private data class CatalogSearchResultState(
    val committedQuery: String = "",
    val filteredHits: List<ExerciseSearchHitV2> = emptyList(),
    val globalHits: List<ExerciseSearchHitV2> = emptyList(),
    val isSettled: Boolean = false,
)

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
): List<ExerciseDefinitionV2> {
    fun definitionMatchesFilter(definition: ExerciseDefinitionV2): Boolean {
        val configs = definition.configurations
        if (filterRegion != null && configs.none { it.profile.bodyRegion.name == filterRegion }) return false
        if (filterMuscle != null && configs.none { config -> config.profile.primaryMuscles.contains(filterMuscle) }) return false
        return true
    }

    return when {
        query.isBlank() -> catalog.families
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

private fun exerciseCatalogImageVariants(definitionId: String): List<CatalogExerciseImageVariant> =
    ExerciseTechniqueImageLookup.variants(definitionId)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogExerciseImageCarousel(
    definitionId: String,
    selectedImplementation: String?,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onImplementationSettled: (String) -> Unit,
) {
    val variants = remember(definitionId) { exerciseCatalogImageVariants(definitionId) }
    if (variants.isEmpty()) return

    val selectedIndex = variants.indexOfFirst { it.implementation == selectedImplementation }
        .takeIf { it >= 0 }
        ?: 0
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogFoldedVariantChips(
    definitionId: String,
    values: List<String>,
    selectedValue: String?,
    singleLine: Boolean,
    onVariantSelected: (String) -> Unit,
) {
    if (values.isEmpty()) return

    @Composable
    fun VariantChip(value: String, selected: Boolean) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (selected) Color.White.copy(alpha = 0.95f)
                    else Color.White.copy(alpha = 0.10f),
                )
                .clickable { onVariantSelected(value) }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text(
                exerciseCatalogVariantTagLabel(value, definitionId),
                color = if (selected) Color(0xFF101214) else Color.White.copy(alpha = 0.85f),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }

    if (!singleLine) {
        val visibleValues = values.take(4)
        val extraCount = (values.size - visibleValues.size).coerceAtLeast(0)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visibleValues.forEach { value ->
                VariantChip(value = value, selected = value == selectedValue)
            }
            if (extraCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "+$extraCount",
                        color = Color.White.copy(alpha = 0.60f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
        return
    }

    // Las tarjetas con imagen usan una sola fila con scroll horizontal:
    // conserva el orden estable de variantes y solo mueve el énfasis blanco.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEach { value ->
            VariantChip(value = value, selected = value == selectedValue)
        }
    }
}

/**
 * The only runtime exercise picker. It deliberately has no legacy fallback:
 * while v2 is loading we show a stable loading surface, never the old catalog.
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
    val retryScope = rememberCoroutineScope()
    // The editor/live hosts can still use the shared glass fallback, while the
    // navigation destination is a real page: no sampled backdrop, blur or sheet
    // shape is allowed there.
    val glassHaze = if (opaqueSurface) null else LocalHazeState.current

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
                // Edge-to-edge leaves the notification bar over the content.
                // Keep an additional breathing band after the inset so the
                // back affordance and title cannot sit against system icons.
                .padding(horizontal = if (opaqueSurface) 16.dp else 12.dp, vertical = if (opaqueSurface) 14.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Search is intentionally always visible as a floating pill at the
            // bottom. It must not be hidden behind an icon or disappear while the
            // asset is being decoded.
            when (val current = state) {
                ExerciseCatalogStateV2.Loading -> {
                    if (opaqueSurface) CatalogBackOnlyHeader(onDismiss = onDismiss)
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
                    if (opaqueSurface) CatalogBackOnlyHeader(onDismiss = onDismiss)
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
                        onCreateSupersetConfigured = onCreateSupersetConfigured,
                        isSupersetAddMode = isSupersetAddMode,
                        onDismiss = onDismiss,
                        initialCatalogDefinitionId = initialCatalogDefinitionId,
                        initialCatalogConfigurationId = initialCatalogConfigurationId,
                        dismissAfterMultiSelect = dismissAfterMultiSelect,
                        opaqueSurface = opaqueSurface,
                        hazeState = glassHaze,
                        targetGroupName = targetGroupName,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogBackOnlyHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogFilterHeader(
    opaqueSurface: Boolean,
    onDismiss: () -> Unit,
    filterRegion: String?,
    filterMuscle: String?,
    muscleFilterExpanded: Boolean,
    onClearFilters: () -> Unit,
    onToggleUpper: () -> Unit,
    onToggleLower: () -> Unit,
    onToggleMuscle: () -> Unit,
    onToggleMuscleId: (String) -> Unit,
    hazeState: HazeState?,
    targetGroupName: String? = null,
) {
    val filterScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(
                hazeState = hazeState,
                shape = RoundedCornerShape(16.dp),
                additionalScrim = Color.Black.copy(alpha = 0.06f),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!targetGroupName.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF67E8F9).copy(alpha = 0.25f),
                    modifier = Modifier.size(8.dp),
                ) {}
                Text(
                    "Agrega ejercicios para $targetGroupName",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF67E8F9),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (opaqueSurface) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(filterScrollState)
                        .padding(start = if (opaqueSurface) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = filterRegion == null && filterMuscle == null,
                        onClick = onClearFilters,
                        label = { Text("Todos") },
                        colors = catalogFilterChipColors(),
                    )
                    FilterChip(
                        selected = filterRegion == "UPPER",
                        onClick = onToggleUpper,
                        label = { Text("Tren Superior") },
                        colors = catalogFilterChipColors(),
                    )
                    FilterChip(
                        selected = filterRegion == "LOWER",
                        onClick = onToggleLower,
                        label = { Text("Tren Inferior") },
                        colors = catalogFilterChipColors(),
                    )
                    FilterChip(
                        selected = muscleFilterExpanded || filterMuscle != null,
                        onClick = onToggleMuscle,
                        label = { Text(filterMuscle?.let(::exerciseCatalogMuscleLabel) ?: "Músculo") },
                        colors = catalogFilterChipColors(),
                    )
                }
                if (opaqueSurface && filterScrollState.value > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            // Keep the fade constrained to the chip row. Using
                            // fillMaxHeight here lets a horizontal scroll expand
                            // the whole header to the screen height.
                            .height(48.dp)
                            .width(26.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.92f), Color.Transparent),
                                ),
                            ),
                    )
                }
            }
        }
        if (muscleFilterExpanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CATALOG_FILTER_MUSCLE_IDS.forEach { muscleId ->
                    FilterChip(
                        selected = filterMuscle == muscleId,
                        onClick = { onToggleMuscleId(muscleId) },
                        label = { Text(exerciseCatalogMuscleLabel(muscleId)) },
                        colors = catalogFilterChipColors(),
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
                additionalScrim = Color.Black.copy(alpha = 0.08f),
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

@Composable
private fun IntegratedCatalogAddButton(
    selectedCount: Int,
    expanded: Boolean,
    onAdd: () -> Unit,
    onToggle: () -> Unit,
) {
    val shape = if (expanded) {
        RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    } else {
        RoundedCornerShape(14.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)), shape),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Agregar $selectedCount ejercicio(s)",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.12f))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Ocultar ejercicios seleccionados" else "Mostrar ejercicios seleccionados",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

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
    opaqueSurface: Boolean,
    hazeState: HazeState?,
    targetGroupName: String? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
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
    val searchSettled = query.isBlank() || catalogSearchResultsAreCurrent(
        query = query,
        committedQuery = searchState.committedQuery,
        isSettled = searchState.isSettled,
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
    )
    LaunchedEffect(definitions, searchSettled, query) {
        if (searchSettled || query.isBlank()) lastStableDefinitions = definitions
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
    val selectedRows = remember(catalog, selectedExercisesIds) {
        // neverEqualPolicy: reordenar el LinkedHashMap produce un mapa igual
        // (la igualdad de Maps ignora el orden), y sin esto Compose no
        // recompondría y el reorden no se vería.
        val restored = linkedMapOf<String, ExerciseMuscleInfo>()
        selectedExercisesIds.forEach { selectedId ->
            val definition = definitionsById[selectedId]
            val info = when {
                definition != null -> exactInfo(catalog, definition, definition.configurations.firstOrNull()?.id)
                else -> catalog.families
                    .asSequence()
                    .flatMap { it.definitions.asSequence() }
                    .firstNotNullOfOrNull { candidate ->
                        candidate.configurations.firstOrNull { it.id.equals(selectedId, ignoreCase = true) }
                            ?.let { exactInfo(catalog, candidate, it.id) }
                    }
            }
            if (info != null) restored[info.id] = info
        }
        mutableStateOf<Map<String, ExerciseMuscleInfo>>(restored, neverEqualPolicy())
    }
    var selectedListExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedRows.value.isEmpty()) {
        if (selectedRows.value.isEmpty()) selectedListExpanded = false
    }
    var expandedDefinitionId by rememberSaveable { mutableStateOf(initialCatalogDefinitionId) }
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

    var bottomPanelHeight by remember { mutableStateOf(152) }
    val density = LocalDensity.current

    CatalogFilterHeader(
        opaqueSurface = opaqueSurface,
        onDismiss = onDismiss,
        filterRegion = filterRegion,
        filterMuscle = filterMuscle,
        muscleFilterExpanded = muscleFilterExpanded,
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
        hazeState = hazeState,
        targetGroupName = targetGroupName,
    )

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
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = with(density) { bottomPanelHeight.toDp() },
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
                selectedConfigurationId?.let { it in selectedExercisesIds } == true ||
                effectiveExerciseInfo?.id?.let { it in selectedExercisesIds } == true
            val isExpanded = expandedDefinitionId == definition.id
            val hasOptions = definition.optionAxes.isNotEmpty()
            val selectBringIntoView = remember(definition.id) { BringIntoViewRequester() }
            val imeVisible = WindowInsets.isImeVisible
            LaunchedEffect(isExpanded, imeVisible) {
                if (isExpanded) {
                    kotlinx.coroutines.delay(80)
                    selectBringIntoView.bringIntoView()
                }
            }
            val defaultMuscles = remember(default) { default?.profile?.primaryMuscles.orEmpty() }
            val imageVariants = remember(definition) { exerciseCatalogImageVariants(definition.id) }
            val selectedImplementation = effectiveSelectedOptions["implement"]
            val firstAxis = definition.optionAxes.firstOrNull()
            val variantValues = remember(definition, firstAxis) {
                if (firstAxis == null) emptyList()
                else definition.configurations
                    .mapNotNull { it.selectedOptions[firstAxis] }
                    .distinct()
            }
            val selectOption: (axis: String, value: String) -> Unit = { axis, value ->
                val newDraft = draftAfterAxisSelection(
                    definition = definition,
                    selectedOptions = selectedOptions,
                    axis = axis,
                    value = value,
                )
                draftByDefinition.value = draftByDefinition.value + (definition.id to newDraft)
            }

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
                    .combinedClickable(
                        onClick = {
                            expandedDefinitionId = toggleCatalogDefinitionExpansion(
                                currentDefinitionId = expandedDefinitionId,
                                tappedDefinitionId = definition.id,
                            )
                        },
                        onLongClick = if (!hasOptions) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isSelected) {
                                    val next = selectedRows.value - definition.id
                                    selectedRows.value = next
                                    onSelectionChange(next.values.toList())
                                } else {
                                    val info = exactInfo(catalog, definition, resolvedConfigurationId)
                                    if (info != null) {
                                        if (editingExisting) {
                                            onSelect(info)
                                        } else {
                                            val next = selectedRows.value + (definition.id to info)
                                            selectedRows.value = next
                                            onSelectionChange(next.values.toList())
                                        }
                                    }
                                }
                            }
                        } else null,
                    ),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Músculos que trabaja: arriba del título, texto pequeño y cian tenue.
                    if (!isExpanded && defaultMuscles.isNotEmpty()) {
                        Text(
                            defaultMuscles.joinToString(" · ") { exerciseCatalogMuscleLabel(it) },
                            color = Color(0xFF67E8F9).copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Título del ejercicio: ancho completo, por encima de imagen y descripción.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isExpanded) {
                            CatalogAdaptiveExerciseTitle(
                                text = definition.canonicalName,
                                modifier = Modifier.weight(1f),
                                baseStyle = MaterialTheme.typography.titleLarge,
                                maxLines = 3,
                            )
                        } else {
                            Text(
                                definition.canonicalName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    val aka = com.example.kpkn.domain.exercises.ExerciseNicknameResolver.nicknames[definition.id]
                        ?.trim()?.takeIf { it.isNotBlank() }
                    if (!aka.isNullOrBlank()) {
                        Text(
                            "a.k.a. $aka",
                            color = Color.White.copy(alpha = 0.62f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Chips de búsqueda: fila propia debajo del título, nunca pegados a él.
                    if (!isExpanded && query.isNotBlank()) {
                        val searchChips = searchMatchChips[definition.id].orEmpty()
                        if (searchChips.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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

                    if (!isExpanded) {
                        if (imageVariants.isNotEmpty()) {
                            // Zona central: imagen a la izquierda, descripción a la derecha aprovechando el espacio vertical.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                CatalogExerciseImageCarousel(
                                    definitionId = definition.id,
                                    selectedImplementation = selectedImplementation,
                                    expanded = false,
                                    modifier = Modifier.size(132.dp),
                                    onImplementationSettled = { implementation ->
                                        selectOption("implement", implementation)
                                    },
                                )
                                Text(
                                    definition.description,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 6,
                                    lineHeight = 19.sp,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Text(
                                definition.description,
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                lineHeight = 19.sp,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Chips técnicos: abajo de todo, una sola fila con scroll horizontal.
                    if (!isExpanded) {
                        CatalogFoldedVariantChips(
                            definitionId = definition.id,
                            values = variantValues,
                            selectedValue = if (hasExplicitDraft) firstAxis?.let(effectiveSelectedOptions::get) else null,
                            singleLine = true,
                            onVariantSelected = { value -> selectOption("implement", value) },
                        )
                    }

                    if (isExpanded) {
                        if (imageVariants.isNotEmpty()) {
                            CatalogExerciseImageCarousel(
                                definitionId = definition.id,
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
                                if (isSelected) {
                                    val next = selectedRows.value - definition.id
                                    selectedRows.value = next
                                    onSelectionChange(next.values.toList())
                                } else {
                                    val info = exactInfo(catalog, definition, resolvedConfigurationId)
                                    if (info != null) {
                                        if (editingExisting) {
                                            onSelect(info)
                                        } else {
                                            val next = selectedRows.value + (definition.id to info)
                                            selectedRows.value = next
                                            onSelectionChange(next.values.toList())
                                        }
                                    }
                                }
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
        item { Spacer(Modifier.height(4.dp)) }
        }
        // Search stays above the selection action. Opening the attached list
        // increases the dock upwards, so the search field moves up with it.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .onSizeChanged { bottomPanelHeight = it.height },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingCatalogSearch(
                value = query,
                onValueChange = onSearch,
                hazeState = hazeState,
            )
            var showSupersetConfigurator by rememberSaveable { mutableStateOf(false) }
            if (!editingExisting && selectedRows.value.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                ) {
                    if (selectedListExpanded) {
                        SelectedExercisesAppendix(
                            selected = selectedRows.value.entries.toList(),
                            hazeState = hazeState,
                            onRemove = { id ->
                                val next = selectedRows.value - id
                                selectedRows.value = next
                                onSelectionChange(next.values.toList())
                            },
                            onMove = { index, delta ->
                                val ids = selectedRows.value.keys.toMutableList()
                                val toIndex = (index + delta).coerceIn(0, ids.lastIndex)
                                if (toIndex == index) return@SelectedExercisesAppendix
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
                            onSupersetAction = if (selectedRows.value.size >= 2 && (onCreateSupersetConfigured != null || onCreateSuperset != null || isSupersetAddMode)) {
                                {
                                    if (isSupersetAddMode) {
                                        val ids = onMultiSelect(selectedRows.value.values.toList())
                                        if (ids.isNotEmpty() && dismissAfterMultiSelect) onDismiss()
                                    } else {
                                        showSupersetConfigurator = true
                                    }
                                }
                            } else null,
                            isSupersetAddMode = isSupersetAddMode,
                        )
                    }
                    IntegratedCatalogAddButton(
                        selectedCount = selectedRows.value.size,
                        expanded = selectedListExpanded,
                        onAdd = {
                            val ids = onMultiSelect(selectedRows.value.values.toList())
                            if (ids.isNotEmpty() && dismissAfterMultiSelect) onDismiss()
                        },
                        onToggle = { selectedListExpanded = !selectedListExpanded },
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
    var expanded by rememberSaveable(exercise.id) { mutableStateOf(false) }
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
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expanded) {
                    CatalogAdaptiveExerciseTitle(
                        text = exercise.name,
                        modifier = Modifier.weight(1f),
                        baseStyle = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                    )
                } else {
                    Text(
                        exercise.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
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

            if (expanded) {
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

                Button(
                    onClick = {
                        if (selected) {
                            onToggle()
                        } else if (editingExisting) {
                            onSelect()
                        } else {
                            onToggle()
                        }
                        expanded = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selected) "Deseleccionar ejercicio" else "Seleccionar ejercicio")
                }
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

/** Apéndice de selección unido visualmente al botón de agregar. */
@Composable
private fun SelectedExercisesAppendix(
    selected: List<Map.Entry<String, ExerciseMuscleInfo>>,
    hazeState: HazeState?,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onTap: (String) -> Unit,
    onSupersetAction: (() -> Unit)? = null,
    isSupersetAddMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(
                hazeState = hazeState,
                shape = shape,
                additionalScrim = Color.Black.copy(alpha = 0.06f),
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                shape,
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Orden de selección",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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
                            tint = if (index > 0) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.20f),
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
                            tint = if (index < selected.lastIndex) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.20f),
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
        if (selected.size >= 2 && onSupersetAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onSupersetAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF67E8F9).copy(alpha = 0.20f),
                    contentColor = Color(0xFF67E8F9),
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    if (isSupersetAddMode) "Agregar a superserie" else "Crear superserie",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
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
            "\"$query\" no está en el catálogo",
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
