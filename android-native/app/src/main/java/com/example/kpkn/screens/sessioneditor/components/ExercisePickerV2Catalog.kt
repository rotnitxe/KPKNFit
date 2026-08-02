package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.data.repository.CustomExerciseRepository

/**
 * Contextual v2 picker. A parent is rendered once; every visible chip comes
 * from an explicitly materialized configuration and disabled values explain
 * why they cannot be combined with the current draft.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExercisePickerV2Catalog(
    repository: ExerciseCatalogRepositoryV2,
    query: String,
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
    val catalog = (state as? ExerciseCatalogStateV2.Ready)?.catalog ?: return
    val resolver = remember(catalog) { com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver(catalog) }
    val definitionsById = remember(catalog) {
        catalog.families.flatMap { it.definitions }.associateBy { it.id }
    }
    val searchHits = remember(catalog, query) {
        if (query.isBlank()) emptyList() else resolver.search(query)
    }
    val definitions = remember(catalog, query, searchHits) {
        if (query.isBlank()) {
            catalog.families.flatMap { it.definitions }
        } else {
            searchHits.mapNotNull { hit -> definitionsById[hit.definitionId] }
        }
    }
    val suggestedOptionsByDefinition = remember(catalog, searchHits) {
        searchHits.mapNotNull { hit ->
            val configurationId = hit.suggestedConfigurationId ?: return@mapNotNull null
            val configuration = definitionsById[hit.definitionId]?.configurations
                ?.firstOrNull { it.id == configurationId }
                ?: return@mapNotNull null
            hit.definitionId to configuration.selectedOptions
        }.toMap()
    }
    val initialDraftByDefinition = remember(
        catalog,
        initialCatalogDefinitionId,
        initialCatalogConfigurationId,
    ) {
        val definition = when {
            initialCatalogDefinitionId != null -> catalog.families
                .asSequence()
                .flatMap { it.definitions.asSequence() }
                .firstOrNull { it.id == initialCatalogDefinitionId }
            initialCatalogConfigurationId != null -> catalog.families
                .asSequence()
                .flatMap { it.definitions.asSequence() }
                .firstOrNull { definition ->
                    definition.configurations.any { it.id == initialCatalogConfigurationId }
                }
            else -> null
        }
        val configuration = definition?.configurations?.firstOrNull {
            it.id == initialCatalogConfigurationId
        }
        if (definition != null && configuration != null) {
            mapOf(definition.id to configuration.selectedOptions)
        } else {
            emptyMap()
        }
    }
    val draftByDefinition = remember(initialDraftByDefinition) {
        mutableStateOf(initialDraftByDefinition)
    }
    val selectedRows = remember { mutableStateOf<Map<String, ExerciseMuscleInfo>>(emptyMap()) }
    var expandedDefinitionId by remember { mutableStateOf<String?>(null) }
    val customExercises by CustomExerciseRepository.customExercises.collectAsStateWithLifecycle()
    val visibleCustomExercises = remember(customExercises, query) {
        customExercises.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Catálogo v2", fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    "Ejercicios v2 · ${definitions.size} resultados",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            TextButton(onClick = onOpenExerciseCreator) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Text("Crear", color = Color.White)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visibleCustomExercises.isNotEmpty()) {
                item("custom-heading") {
                    Text(
                        "Ejercicios personalizados",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                items(visibleCustomExercises, key = { "custom:" + it.id }) { custom ->
                    val selected = custom.id in selectedRows.value
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(custom.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "Personalizado · uso manual · excluido de AUGE, splits y reemplazos inteligentes si no tiene metadata v2.",
                                color = Color.White.copy(alpha = 0.68f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (editingExisting) {
                                Button(onClick = { onSelect(custom) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Usar ejercicio personalizado")
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        val next = if (selected) {
                                            selectedRows.value - custom.id
                                        } else {
                                            selectedRows.value + (custom.id to custom)
                                        }
                                        selectedRows.value = next
                                        onSelectionChange(next.values.toList())
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (selected) "Quitar" else "Elegir", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            items(definitions, key = { it.id }) { definition ->
                val default = definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
                val selectedOptions = draftByDefinition.value[definition.id]
                    ?: suggestedOptionsByDefinition[definition.id]
                    ?: default?.selectedOptions.orEmpty()
                val compatibility = repository.compatibility(definition.id, selectedOptions)
                val selectedConfigurationId = compatibility.exactConfigurationId
                val selected = selectedRows.value[definition.id]
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(definition.canonicalName, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    definition.description,
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!editingExisting) {
                                TextButton(onClick = {
                                    val info = selected ?: exactInfo(catalog, definition, selectedConfigurationId)
                                    if (info != null) {
                                        val next = if (definition.id in selectedRows.value) {
                                            selectedRows.value - definition.id
                                        } else {
                                            selectedRows.value + (definition.id to info)
                                        }
                                        selectedRows.value = next
                                        onSelectionChange(next.values.toList())
                                    }
                                }) {
                                    Text(if (definition.id in selectedRows.value || definition.id in selectedExercisesIds) "Quitar" else "Elegir", color = Color.White)
                                }
                            }
                        }
                        compatibility.axes.forEach { axis ->
                            // A chip may require changing another axis too
                            // (e.g. máquina implies pec deck + sentado). All
                            // values materialized for this parent are therefore
                            // reachable; the draft normalizer below clears only
                            // conflicting axes and never persists a cartesian
                            // combination that is not in the catalog.
                            val axisOptions = axis.options.map { option ->
                                option.copy(
                                    enabled = definition.configurations.any {
                                        it.selectedOptions[axis.axis] == option.value
                                    },
                                    disabledReason = null,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(exerciseCatalogAxisLabel(axis.axis), color = Color.White.copy(alpha = 0.66f), style = MaterialTheme.typography.labelSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    axisOptions.forEach { option ->
                                        AssistChip(
                                            onClick = {
                                                if (!option.enabled) return@AssistChip
                                                val candidate = draftAfterAxisSelection(
                                                    definition = definition,
                                                    selectedOptions = selectedOptions,
                                                    axis = axis.axis,
                                                    value = option.value,
                                                )
                                                draftByDefinition.value = draftByDefinition.value + (definition.id to candidate)
                                            },
                                            label = { Text(exerciseCatalogOptionLabel(option.value)) },
                                            enabled = option.enabled,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            selectedConfigurationId?.let { id ->
                                definition.configurations.firstOrNull { it.id == id }
                                    ?.let(::exerciseCatalogConfigurationSummary)
                            } ?: "Configuración incompleta · elige los chips restantes",
                            color = Color.White.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(
                            onClick = {
                                expandedDefinitionId = if (expandedDefinitionId == definition.id) null else definition.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (expandedDefinitionId == definition.id) "Ocultar metadata" else "Ver metadata técnica",
                                color = Color.White,
                            )
                        }
                        if (expandedDefinitionId == definition.id) {
                            val selectedConfiguration = selectedConfigurationId
                                ?.let { id -> definition.configurations.firstOrNull { it.id == id } }
                            selectedConfiguration?.let { configuration ->
                                val profile = configuration.profile
                                Text(
                                    "AUGE · EFC ${profile.efc} · CNC ${profile.cnc} · SSC ${profile.ssc} · TTC ${profile.ttc} · axial ${profile.axialLoadFactor} · técnica ${profile.technicalDifficulty}/10",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "Anatomía · primarios: ${profile.primaryMuscles.joinToString(", ") { exerciseCatalogMuscleLabel(it) }} · secundarios: ${profile.secondaryMuscles.joinToString(", ") { exerciseCatalogMuscleLabel(it) }.ifBlank { "—" }}",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    "Biomecánica · ${exerciseCatalogBodyRegionLabel(profile.bodyRegion)} · ${exerciseCatalogChainLabel(profile.kineticChain)} · ${exerciseCatalogLateralityLabel(profile.laterality)} · ${exerciseCatalogEquipmentLabel(profile.equipmentId)} · ${exerciseCatalogResistanceLabel(profile.resistanceProfile)}",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                profile.setupCues.forEach { cue ->
                                    Text("Setup · $cue", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                                }
                                profile.executionCues.forEach { cue ->
                                    Text("Ejecución · $cue", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { onOpenExerciseDetail(configuration.id) }) {
                                    Text("Abrir ficha completa", color = Color.White)
                                }
                            }
                        }
                        if (editingExisting) {
                            Button(
                                onClick = {
                                    exactInfo(catalog, definition, selectedConfigurationId)?.let(onSelect)
                                },
                                enabled = selectedConfigurationId != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Usar esta configuración") }
                        }
                    }
                }
            }
        }

        if (!editingExisting && selectedRows.value.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val ids = onMultiSelect(selectedRows.value.values.toList())
                    if (ids.isNotEmpty()) onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Agregar ${selectedRows.value.size} ejercicio(s)") }
        }
    }
}

private fun exactInfo(
    catalog: com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2,
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

/**
 * Changes one controlled axis without ever creating a persisted cartesian
 * combination. If the chosen value belongs to a configuration that requires
 * different values on other axes, only those conflicting values are cleared;
 * the user can then complete the exact explicit configuration with the chips.
 */
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
