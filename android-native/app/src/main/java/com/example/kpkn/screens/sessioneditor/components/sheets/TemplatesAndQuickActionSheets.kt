package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknSheetWhiteFieldColors
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.CatalogSearchField
import com.example.kpkn.screens.sessioneditor.CompactCatalogFilterChip
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.components.CatalogDescription
import com.example.kpkn.screens.sessioneditor.components.CatalogInvolvementAccordions
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.example.kpkn.ui.components.KpknAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplatesSheet(
    templates: List<SessionTemplate>,
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Plantillas de sesión",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text("Buscar plantilla...", color = KpknSheetTokens.ControlPlaceholder)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = KpknSheetTokens.ControlLabel,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = KpknSheetTokens.ControlLabel),
                colors = kpknSheetWhiteFieldColors(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TemplateCatalogBrowser(
                    templates = templates,
                    searchQuery = searchQuery,
                    onSelectTemplate = onSelectTemplate,
                    exerciseIndex = exerciseCatalogSnapshot().associateBy { it.id.lowercase() },
                    glassDark = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (applyDecision != null) {
        KpknAlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Aplicar plantilla", fontWeight = FontWeight.Black) },
            text = {
                Text("La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla \"${applyDecision.template.name}\"?")
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                ) {
                    Text("Añadir al final")
                }
            },
        )
    }
}

@Composable
internal fun TemplateCard(
    template: SessionTemplate,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = template.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (template.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (template.muscleGroupsSummary.isNotBlank() || template.estimatedDurationMinutes != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (template.muscleGroupsSummary.isNotBlank()) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(template.muscleGroupsSummary, fontSize = 11.sp) },
                            )
                        }
                        template.estimatedDurationMinutes?.let {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("~${it}min", fontSize = 11.sp) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onApply,
                colors = kpknSheetWhiteTonalButtonColors(),
            ) {
                Text("Aplicar")
            }
        }
    }
}

@Composable
internal fun ExerciseQuickActionsSheet(
    exercise: Exercise?,
    catalog: List<ExerciseMuscleInfo>,
    workoutLogs: List<WorkoutLog>,
    onOpenExerciseDetail: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenWarmup: () -> Unit,
    onOpenMobility: () -> Unit,
    onDelete: () -> Unit,
    onManageSuperset: () -> Unit,
) {
    if (exercise == null) {
        Text(
            text = "No encontramos el ejercicio seleccionado.",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val catalogLookup = catalogExerciseIndex()
    val selectedInfo = remember(exercise.id, catalogLookup) {
        resolveCatalogExerciseInfo(exercise, catalogLookup)
    }
    val context = LocalContext.current
    var catalogEntry by remember { mutableStateOf(CatalogV2ProcessCache.peek()) }
    LaunchedEffect(context) {
        if (catalogEntry == null) {
            catalogEntry = runCatching {
                withContext(Dispatchers.IO) {
                    CatalogV2ProcessCache.getOrLoad(context)
                }
            }.getOrNull()
        }
    }
    val resolvedV2 = remember(exercise.id, selectedInfo, catalogEntry) {
        resolveCatalogExerciseV2(
            exercise = exercise,
            catalog = catalogEntry?.catalog,
            legacyInfo = selectedInfo,
        )
    }
    val involvedMuscles = remember(exercise.id, exercise.effectiveMuscles, selectedInfo) {
        (exercise.effectiveMuscles?.takeIf { it.isNotEmpty() }
            ?: selectedInfo?.involvedMuscles.orEmpty())
            .filter { it.muscle.isNotBlank() }
    }
    val muscleInvolvement = remember(involvedMuscles) {
        listOf(
            MuscleRole.PRIMARY to "Principales",
            MuscleRole.SECONDARY to "Secundarios",
            MuscleRole.STABILIZER to "Estabilizadores",
            MuscleRole.NEUTRALIZER to "Neutralizadores",
        ).mapNotNull { (role, label) ->
            val names = involvedMuscles
                .filter { it.role == role }
                .map { it.muscle.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(8)
            names.takeIf { it.isNotEmpty() }?.let { "$label: ${it.joinToString(" · ")}" }
        }.joinToString("\n")
    }
    val articularProfile = remember(exercise.id, selectedInfo) {
        listOfNotNull(
            selectedInfo?.articulationType?.let { "Tipo: ${articulationLabel(it)}" },
            selectedInfo?.movementPattern?.takeIf { it.isNotBlank() }?.let { "Patrón: $it" },
            selectedInfo?.force?.takeIf { it.isNotBlank() }?.let { "Dirección: $it" },
            selectedInfo?.anatomicalConsiderations?.firstOrNull()?.trait?.takeIf { it.isNotBlank() }?.let { "Consideración: $it" },
        )
    }
    val tags = listOfNotNull(
        selectedInfo?.equipment,
        selectedInfo?.category,
        selectedInfo?.type,
        selectedInfo?.variantName,
    ).filter { it.isNotBlank() }.distinct()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Información del ejercicio", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(
            exercise.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tags, key = { it }) { tag ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.09f),
                    ) {
                        Text(
                            tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.84f),
                        )
                    }
                }
            }
        }

        if (resolvedV2 != null) {
            CatalogDescription(
                definition = resolvedV2.definition,
                configuration = resolvedV2.configuration,
                initiallyExpanded = true,
            )
            CatalogInvolvementAccordions(
                info = resolvedV2.legacyInfo.takeIf { it.involvedMuscles.isNotEmpty() },
                joints = resolvedV2.configuration.profile.jointInvolvement,
            )
        } else {
            QuickInfoBlock(
                title = "Descripción",
                value = selectedInfo?.let { adaptedExerciseDescription(it, exercise.selectedAspects.orEmpty()) }
                    ?.takeIf { it.isNotBlank() }
                    ?: "No hay una descripción editorial disponible para esta configuración.",
            )
            QuickInfoBlock(
                title = "Músculos involucrados",
                value = muscleInvolvement.ifBlank { "Información muscular no disponible" },
            )
            QuickInfoBlock(
                title = "Involucramiento articular",
                value = articularProfile.joinToString(" · ").ifBlank { "Perfil articular no disponible" },
            )
        }

        FilledTonalButton(
            onClick = onOpenPicker,
            modifier = Modifier.fillMaxWidth(),
            colors = kpknSheetWhiteTonalButtonColors(),
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cambiar")
        }
    }
}

@Composable
private fun QuickInfoBlock(
    title: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.07f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.92f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
        }
    }
}

private fun articulationLabel(value: String): String = when (value.uppercase()) {
    "MULTIARTICULAR" -> "Multiarticular"
    "AISLADO" -> "Aislado"
    else -> value.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}


@Composable
internal fun MobilityPickerSheet(
    selectedMobilityIds: Set<String> = emptySet(),
    onAdd: (MobilityExercise) -> Unit,
    onRemove: (MobilityExercise) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRegion by rememberSaveable { mutableStateOf("") }
    val allMobility = remember { MobilityExerciseCatalog.getAllMobilityExercises() }
    val uniqueRegions = remember(allMobility) { allMobility.map { it.bodyRegion }.distinct().sorted() }
    val results = remember(query, selectedRegion, allMobility) {
        val byQuery = if (query.isBlank()) allMobility else {
            val normalized = query.trim().lowercase()
            allMobility.filter { exercise ->
                exercise.name.contains(normalized, ignoreCase = true) ||
                    exercise.description.contains(normalized, ignoreCase = true) ||
                    exercise.bodyRegion.contains(normalized, ignoreCase = true) ||
                    exercise.discomfortIds.any { discomfortLabel(it).contains(normalized, ignoreCase = true) }
            }
        }
        if (selectedRegion.isBlank()) byQuery else byQuery.filter { it.bodyRegion == selectedRegion }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Catálogo de movilidad", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "${allMobility.size} ejercicios correctivos separados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }
        CatalogSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar movilidad, zona o molestia",
        )
        // Body region filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                CompactCatalogFilterChip(
                    selected = selectedRegion.isBlank(),
                    onClick = { selectedRegion = "" },
                    label = "Todas",
                    glassDark = true,
                )
            }
            items(uniqueRegions) { region ->
                CompactCatalogFilterChip(
                    selected = selectedRegion == region,
                    onClick = { selectedRegion = region },
                    label = region.replaceFirstChar { it.uppercase() },
                    glassDark = true,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { mobility ->
                val alreadyAdded = mobility.id in selectedMobilityIds
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = KpknSheetTokens.Panel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(mobility.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${mobility.durationSeconds}s · ${mobility.bodyRegion} · ${mobility.discomfortIds.joinToString { discomfortLabel(it) }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                mobility.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalButton(
                            onClick = { if (alreadyAdded) onRemove(mobility) else onAdd(mobility) },
                            colors = kpknSheetWhiteTonalButtonColors(),
                        ) {
                            Text(if (alreadyAdded) "Agregado (Quitar)" else "Agregar")
                        }
                    }
                }
            }
        }
    }
}
