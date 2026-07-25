package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_ID_ALIASES
import com.example.kpkn.data.models.*
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.exercises.*
import kotlin.math.roundToInt
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.DarkChoiceChip
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.EstimatedRingsRow
import com.example.kpkn.screens.sessioneditor.SessionSubMuscleBreakdownList
import com.example.kpkn.screens.sessioneditor.countDisplaySets
import com.example.kpkn.screens.sessioneditor.buildDisplayContributions
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun AssistantSheet(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    onApplyAugeCorrection: (String) -> Unit,
    onAddGhostExercise: (String) -> Unit,
    onApplyAssistantSuggestion: (String) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
) {
    val report = uiState.assistantReport
    val summary = uiState.augeSummary
    val accentColor = augeStatusColor(summary.status, summary.hasCriticalAlerts)
    var ringsExpanded by rememberSaveable { mutableStateOf(false) }
    var volumeExpanded by rememberSaveable { mutableStateOf(true) }
    var countIndirectVolume by rememberSaveable { mutableStateOf(false) }
    var adjustVolumeByIntensity by rememberSaveable { mutableStateOf(false) }
    var expandedMuscleName by remember { mutableStateOf<String?>(null) }
    var muscleChartMode by rememberSaveable { mutableStateOf(AssistantMuscleChartMode.AUGE_DRAIN) }
    var suggestionsExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Asistente", "Plantillas")

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Asistente de sesión", fontWeight = FontWeight.Black, fontSize = 18.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(DarkEditorChip)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                DarkChoiceChip(
                    label = title.uppercase(),
                    selected = selectedTab == index,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = index },
                )
            }
        }

        if (selectedTab == 1) {
            AssistantTemplatesTab(
                templates = templates,
                searchQuery = uiState.templateSearchQuery,
                applyDecision = uiState.templateApplyDecision,
                onSearchChange = onTemplateSearchChange,
                onSelectTemplate = onSelectTemplate,
                onConfirmApplyTemplate = onConfirmApplyTemplate,
                onCancelApply = onCancelTemplateApply,
            )
            return@Column
        }

        val energySummary = summary.sessionEnergy
        // ─── Feature 1: Tarjeta de desglose de tiempos ───────────────────────────
        val timeBreakdown = uiState.sessionTimeBreakdown
        if (timeBreakdown != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Tiempo Estimado de Sesión",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    // Desglose de 3 líneas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Preparación",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${timeBreakdown.setupMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Ejecución",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${timeBreakdown.executionMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Descansos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${timeBreakdown.restMinutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    // Total grande
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Duración estimada: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${timeBreakdown.totalMinutes} min",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (energySummary.totalKcal.mid > 0) {

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kcal estimadas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${energySummary.totalKcal.mid}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${energySummary.totalKcal.low}–${energySummary.totalKcal.high} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EPOC estimado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${energySummary.epocKcal.mid} kcal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Confianza", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (energySummary.confidence) {
                                EnergyConfidence.HIGH -> "alta"
                                EnergyConfidence.MEDIUM -> "media"
                                EnergyConfidence.LOW -> "baja"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = when (energySummary.confidence) {
                                EnergyConfidence.HIGH -> Color(0xFF22C55E)
                                EnergyConfidence.MEDIUM -> Color(0xFFF59E0B)
                                EnergyConfidence.LOW -> Color(0xFFEF4444)
                            },
                        )
                    }
                }
            }
        }

        if (report == null) {
            Text(
                "Calculando análisis...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Rings section (kept from old AugeSheet)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ringsExpanded = !ringsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "RINGS de sesión",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Icon(
                        imageVector = if (ringsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                    )
                }
                EstimatedRingsRow(
                    energy = (100 - summary.sessionDrain.cns).coerceIn(0, 100),
                    spine = (100 - summary.sessionDrain.spinal).coerceIn(0, 100),
                )
            }
        }

        // Muscle chart section (volume or AUGE drain)
        val isDrainMode = muscleChartMode == AssistantMuscleChartMode.AUGE_DRAIN
        val sortedVolumeEntries = remember(uiState.session, countIndirectVolume, adjustVolumeByIntensity) {
            val session = uiState.session ?: return@remember emptyList<Map.Entry<String, Double>>()
            val exerciseIndex = EXERCISE_DATABASE.associateBy { it.id.lowercase() }
            val volumeMap = mutableMapOf<String, Double>()
            session.allExercises().forEach { exercise ->
                val effectiveSets = countDisplaySets(exercise.sets, adjustVolumeByIntensity)
                if (effectiveSets <= 0.0) return@forEach
                val dbInfo = exercise.exerciseDbId?.let { exerciseIndex[it.lowercase()] } ?: return@forEach
                
                val contributions = buildDisplayContributions(dbInfo.involvedMuscles, countIndirectVolume)
                contributions.forEach { (canonical, multiplier) ->
                    volumeMap[canonical] = (volumeMap[canonical] ?: 0.0) + effectiveSets * multiplier
                }
            }
            volumeMap.entries
                .filter { it.value > 0.0 }
                .sortedByDescending { it.value }
        }
        val sortedDrainEntries = remember(summary.muscleDrainProjection) {
            summary.muscleDrainProjection
                .entries
                .filter { it.value > 0 }
                .map { it.key to it.value.toDouble() }
                .sortedByDescending { it.second }
        }
        val chartEntries: List<Pair<String, Double>> = if (isDrainMode) sortedDrainEntries else sortedVolumeEntries.map { it.key to it.value }

        if (chartEntries.isNotEmpty()) {
            val maxChartValue = chartEntries.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isDrainMode) "Drenaje por músculo" else "Series por músculos",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Switch(
                                checked = isDrainMode,
                                onCheckedChange = { enabled ->
                                    muscleChartMode = if (enabled) AssistantMuscleChartMode.AUGE_DRAIN else AssistantMuscleChartMode.VOLUME
                                },
                                modifier = Modifier.scale(0.82f),
                            )
                            IconButton(onClick = { volumeExpanded = !volumeExpanded }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (volumeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                    if (volumeExpanded) {
                        if (!isDrainMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Indirecto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = countIndirectVolume,
                                        onCheckedChange = { countIndirectVolume = it },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ajustar RPE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = adjustVolumeByIntensity,
                                        onCheckedChange = { adjustVolumeByIntensity = it },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            chartEntries.forEach { (muscle, value) ->
                                val threshold = if (!isDrainMode) report?.umbralesPorMusculo?.get(muscle) else null
                                val mev = threshold?.mev
                                val mav = threshold?.mav
                                val mrv = threshold?.mrv
                                val indicatorColor = if (isDrainMode) {
                                    when {
                                        value >= 70.0 -> Color(0xFFEF4444)
                                        value >= 40.0 -> Color(0xFFF59E0B)
                                        else -> Color(0xFF22C55E)
                                    }
                                } else {
                                    when {
                                        mrv != null && value > mrv -> Color(0xFFEF4444)
                                        mav != null && value > mav -> Color(0xFFF59E0B)
                                        mev != null && value >= mev -> Color(0xFF22C55E)
                                        else -> accentColor
                                    }
                                }
                                val valueText = if (isDrainMode) {
                                    "${value.roundToInt()}%"
                                } else {
                                    "${if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)} sets"
                                }
                                val isExpanded = expandedMuscleName == muscle
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (!isDrainMode && (muscle == "Deltoides" || muscle == "Glúteos")) {
                                                expandedMuscleName = if (isExpanded) null else muscle
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(muscle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            valueText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = indicatorColor,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (if (isDrainMode) value / 100.0 else value / maxChartValue).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = indicatorColor,
                                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                                    )
                                    if (isExpanded && !isDrainMode && uiState.session != null) {
                                        SessionSubMuscleBreakdownList(
                                            muscleName = muscle,
                                            session = uiState.session,
                                            countIndirect = countIndirectVolume,
                                            adjustByIntensity = adjustVolumeByIntensity
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (report?.ajustes?.isNotEmpty() == true) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { suggestionsExpanded = !suggestionsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Ajustes sugeridos",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Icon(
                            imageVector = if (suggestionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                    if (suggestionsExpanded) {
                        report.ajustes.forEach { suggestion ->
                            AssistantSuggestionCard(suggestion, onApplyAssistantSuggestion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssistantTemplatesTab(
    templates: List<SessionTemplate>,
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Buscar plantilla...") },
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    Spacer(Modifier.height(10.dp))
    TemplateCatalogBrowser(
        templates = templates,
        searchQuery = searchQuery,
        onSelectTemplate = onSelectTemplate,
        exerciseIndex = remember { EXERCISE_DATABASE.associateBy { it.id.lowercase() } }
    )
    if (applyDecision != null) {
        AlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Aplicar plantilla", fontWeight = FontWeight.Black) },
            text = {
                Text("La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla \"${applyDecision.template.name}\"?")
            },
            confirmButton = {
                Button(onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) }) {
                    Text("Reemplazar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) }) {
                    Text("Añadir al final")
                }
            },
        )
    }
}

@Composable
internal fun AssistantSuggestionCard(
    suggestion: com.example.kpkn.domain.sessionassistant.AssistantSuggestion,
    onApplySuggestion: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(suggestion.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(suggestion.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.LOWER_RPE ||
                suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.REDUCE_SET ||
                suggestion.type == com.example.kpkn.domain.sessionassistant.AssistantActionType.REMOVE_FAILURE
            ) {
                FilledTonalButton(onClick = { onApplySuggestion(suggestion.id) }) {
                    Text("Aplicar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

internal fun buildExerciseCatalogLookup(catalog: List<ExerciseMuscleInfo>): Map<String, ExerciseMuscleInfo> {
    val base = catalog.associateBy { it.id.lowercase() }
    val aliasEntries = EXERCISE_ID_ALIASES.mapNotNull { (alias, canonical) ->
        base[canonical]?.let { alias.lowercase() to it }
    }.toMap()
    return base + aliasEntries
}

internal fun resolveCatalogExerciseInfo(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? {
    val byId = exercise.exerciseDbId ?: exercise.exerciseId
    return byId?.lowercase()?.let(catalogLookup::get)
        ?: catalogLookup.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
}

internal fun buildDiscomfortByExercise(
    workoutLogs: List<WorkoutLog>,
): Map<String, List<Pair<String, Int>>> {
    val map = mutableMapOf<String, MutableMap<String, Int>>()
    workoutLogs.forEach { log ->
        log.postExerciseReports.forEach { report ->
            val key = report.canonicalExerciseId ?: report.exerciseDbId ?: report.exerciseId
            if (key.isBlank()) return@forEach
            val bucket = map.getOrPut(key) { mutableMapOf() }
            report.discomfortIds
                .filter { it != "none" }
                .forEach { discomfortId ->
                    val label = discomfortLabel(discomfortId)
                    bucket[label] = (bucket[label] ?: 0) + 1
                }
        }
    }

    return map.mapValues { (_, value) ->
        value.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }
    }
}


internal enum class AssistantMuscleChartMode {
    VOLUME,
    AUGE_DRAIN,
}
