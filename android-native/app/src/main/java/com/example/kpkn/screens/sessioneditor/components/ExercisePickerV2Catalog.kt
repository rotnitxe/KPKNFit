package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.explainMuscleContribution
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.wikilab.wikilabMuscleColor
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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
    onOpenExerciseCreator: () -> Unit,
    onDismiss: () -> Unit,
    initialCatalogDefinitionId: String? = null,
    initialCatalogConfigurationId: String? = null,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val retryScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Catálogo de ejercicios",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    when (val current = state) {
                        ExerciseCatalogStateV2.Loading -> "Cargando ejercicios…"
                        is ExerciseCatalogStateV2.Error -> "No se pudo cargar el catálogo"
                        is ExerciseCatalogStateV2.Ready -> {
                            val count = current.catalog.families.sumOf { it.definitions.size }
                            "$count ejercicios · selecciona un ejercicio para ver sus opciones"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.70f),
                )
            }
            TextButton(onClick = onOpenExerciseCreator) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("Crear", color = Color.White)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }

        // Search is intentionally always visible. It must not be hidden behind
        // an icon or disappear while the asset is being decoded.
        CatalogSearchField(
            value = query,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar ejercicio, implemento o músculo",
        )

        when (val current = state) {
            ExerciseCatalogStateV2.Loading -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(10.dp))
                        Text("Preparando el catálogo…", color = Color.White.copy(alpha = 0.78f))
                    }
                }
            }

            is ExerciseCatalogStateV2.Error -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
            }

            is ExerciseCatalogStateV2.Ready -> {
                CatalogReadyContent(
                    catalog = current.catalog,
                    repository = repository,
                    query = query,
                    editingExisting = editingExisting,
                    selectedExercisesIds = selectedExercisesIds,
                    onSelect = onSelect,
                    onMultiSelect = onMultiSelect,
                    onSelectionChange = onSelectionChange,
                    onOpenExerciseDetail = onOpenExerciseDetail,
                    onDismiss = onDismiss,
                    initialCatalogDefinitionId = initialCatalogDefinitionId,
                    initialCatalogConfigurationId = initialCatalogConfigurationId,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CatalogReadyContent(
    catalog: ExerciseCatalogV2,
    repository: ExerciseCatalogRepositoryV2,
    query: String,
    editingExisting: Boolean,
    selectedExercisesIds: Set<String>,
    onSelect: (ExerciseMuscleInfo) -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
    onSelectionChange: (List<ExerciseMuscleInfo>) -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    onDismiss: () -> Unit,
    initialCatalogDefinitionId: String?,
    initialCatalogConfigurationId: String?,
) {
    val resolver = remember(catalog) { ExerciseCatalogV2Resolver(catalog) }
    val definitionsById = remember(catalog) {
        catalog.families.flatMap { it.definitions }.associateBy { it.id }
    }
    val searchHits = remember(catalog, query) {
        if (query.isBlank()) emptyList() else resolver.search(query)
    }
    val definitions = remember(catalog, query, searchHits) {
        if (query.isBlank()) {
            catalog.families
                .flatMap { it.definitions }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.canonicalName })
        } else {
            // The resolver already orders hits by relevance. Keep that order
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
    val selectedRows = remember { mutableStateOf<Map<String, ExerciseMuscleInfo>>(emptyMap()) }
    var expandedDefinitionId by rememberSaveable { mutableStateOf<String?>(null) }
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val visibleCustomExercises = remember(customExercises, query) {
        customExercises.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            items(visibleCustomExercises, key = { "custom:" + it.id }) { custom ->
                val selected = custom.id in selectedRows.value
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(custom.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Ejercicio personalizado · selección manual", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall)
                        if (editingExisting) {
                            Button(onClick = { onSelect(custom) }, modifier = Modifier.fillMaxWidth()) { Text("Usar ejercicio") }
                        } else {
                            TextButton(
                                onClick = {
                                    val next = if (selected) selectedRows.value - custom.id else selectedRows.value + (custom.id to custom)
                                    selectedRows.value = next
                                    onSelectionChange(next.values.toList())
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (selected) "Quitar" else "Elegir", color = Color.White) }
                        }
                    }
                }
            }
        }

        items(definitions, key = { it.id }) { definition ->
            val default = definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
            val selectedOptions = draftByDefinition.value[definition.id]
                ?: default?.selectedOptions.orEmpty()
            val compatibility = repository.compatibility(definition.id, selectedOptions)
            val selectedConfigurationId = compatibility.exactConfigurationId
            val selectedConfiguration = selectedConfigurationId?.let { id ->
                definition.configurations.firstOrNull { it.id == id }
            }
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
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF153A2E) else Color.White.copy(alpha = 0.08f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isExpanded) {
                            expandedDefinitionId = null
                            if (editingExisting) {
                                exactInfo(catalog, definition, selectedConfigurationId)?.let(onSelect)
                            } else if (isSelected) {
                                val next = selectedRows.value - definition.id
                                selectedRows.value = next
                                onSelectionChange(next.values.toList())
                            }
                        } else {
                            expandedDefinitionId = definition.id
                            if (!editingExisting && !isSelected) {
                                exactInfo(catalog, definition, selectedConfigurationId)?.let { info ->
                                    val next = selectedRows.value + (definition.id to info)
                                    selectedRows.value = next
                                    onSelectionChange(next.values.toList())
                                }
                            }
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
                                )
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
                        CatalogDescription(
                            definition = definition,
                            configuration = effectiveConfiguration,
                            catalog = catalog,
                        )

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
                                if (!editingExisting && isSelected) {
                                    val newConfigurationId = repository.compatibility(definition.id, newDraft).exactConfigurationId
                                    newConfigurationId?.let { exactInfo(catalog, definition, it) }?.let { info ->
                                        val next = selectedRows.value + (definition.id to info)
                                        selectedRows.value = next
                                        onSelectionChange(next.values.toList())
                                    }
                                }
                            }
                            if (compactAxes) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    axesWithOptions.forEach { (axis, visibleOptions) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                                    maxLines = 1,
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

                        Text(
                            effectiveConfiguration?.let { exerciseCatalogConfigurationSummary(it, definition.id) }
                                ?: "Configuración incompleta: selecciona los chips restantes",
                            color = if (effectiveConfiguration != null) Color.White else Color(0xFFFFC857),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (definitions.isEmpty() && visibleCustomExercises.isEmpty()) {
            item("empty") {
                Text(
                    "No encontramos ejercicios con esa búsqueda.",
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }

    if (!editingExisting && selectedRows.value.isNotEmpty()) {
        Button(
            onClick = {
                val ids = onMultiSelect(selectedRows.value.values.toList())
                if (ids.isNotEmpty()) onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Agregar ${selectedRows.value.size} ejercicio(s)") }
    }
}

@Composable
private fun CatalogDescription(
    definition: ExerciseDefinitionV2,
    configuration: com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2?,
    catalog: ExerciseCatalogV2?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Descripción", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            configuration?.profile?.description?.takeIf { it.isNotBlank() }
                ?: definition.description,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
        )
        configuration?.let { selected ->
            if (catalog != null) {
                val legacyInfo = exactInfo(catalog, definition, selected.id)
                if (legacyInfo != null && legacyInfo.involvedMuscles.isNotEmpty()) {
                    MuscleInvolvementSection(legacyInfo)
                }
            }
        }
    }
}

@Composable
private fun MuscleInvolvementSection(exercise: ExerciseMuscleInfo) {
    val expandedMuscle = remember { mutableStateOf<String?>(null) }
    val contributions = oneSeriesVolumeContributions(exercise)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("Involucramiento muscular", color = Color.White, fontWeight = FontWeight.Bold)
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
                        muscleName,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
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

private fun exactInfo(    catalog: ExerciseCatalogV2,
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
