package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.domain.auge.SessionMuscleFilter
import com.example.kpkn.domain.training.VolumeCalculator
import java.util.UUID

private enum class TemporalSplitScope { CURRENT_WEEK, ALL_WEEKS }
private enum class AdvancedSplitMode { GLOBAL, PER_BLOCK }
private enum class SessionMigrationMode { MIGRATE, CLEAN }

private data class SplitBlockOption(
    val id: String,
    val name: String,
    val macroName: String,
)

private data class SplitPatternDay(
    val label: String,
    val dayOfWeek: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitView(
    program: Program,
    selectedBlockId: String?,
    selectedWeekId: String?,
    onUpdateProgram: (Program) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<SplitTag?>(null) }
    var infoSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCompare by rememberSaveable { mutableStateOf(false) }
    val comparedIds = remember { mutableStateListOf<String>() }

    val blocks = remember(program.id, program.macrocycles) {
        program.macrocycles.flatMap { macro ->
            macro.blocks.map { block ->
                SplitBlockOption(block.id, block.name, macro.name)
            }
        }
    }
    val totalWeeks = remember(program.id, program.macrocycles) {
        program.macrocycles.sumOf { macro -> macro.blocks.sumOf { block -> block.mesocycles.sumOf { meso -> meso.weeks.size } } }
    }
    val isAdvancedProgram = blocks.size > 1

    val filteredSplits = remember(searchQuery, selectedTag) {
        SPLIT_TEMPLATES.filter { split ->
            if (split.id == "custom") return@filter false
            val matchesTag = selectedTag == null || split.tags.contains(selectedTag)
            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isBlank() ||
                split.name.lowercase().contains(query) ||
                split.description.lowercase().contains(query) ||
                split.pros.any { it.lowercase().contains(query) } ||
                split.cons.any { it.lowercase().contains(query) }
            matchesTag && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SplitHeaderCard(program = program, isAdvancedProgram = isAdvancedProgram)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar split") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )

        SplitTagBar(selectedTag = selectedTag, onSelectTag = { selectedTag = it })

        if (comparedIds.isNotEmpty()) {
            CompareStrip(
                comparedIds = comparedIds,
                onOpenCompare = { showCompare = true },
                onRemove = { comparedIds.remove(it) },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            filteredSplits.forEach { split ->
                SplitCatalogCard(
                    split = split,
                    isCurrent = program.selectedSplitId == split.id || program.blockSplitSelections.containsValue(split.id),
                    isCompared = split.id in comparedIds,
                    onToggleCompare = {
                        if (split.id in comparedIds) comparedIds.remove(split.id)
                        else if (comparedIds.size < 3) comparedIds.add(split.id)
                    },
                    onShowInfo = { infoSplitId = split.id },
                    onSelect = { sheetSplitId = split.id },
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    val compareSplits = remember(comparedIds.toList()) {
        comparedIds.mapNotNull { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id } }
    }

    if (showCompare && compareSplits.size >= 2) {
        CompareDialog(splits = compareSplits, onDismiss = { showCompare = false })
    }

    infoSplitId?.let { splitId ->
        SPLIT_TEMPLATES.firstOrNull { it.id == splitId }?.let { split ->
            SplitInfoDialog(split = split, onDismiss = { infoSplitId = null })
        }
    }

    sheetSplitId?.let { splitId ->
        SPLIT_TEMPLATES.firstOrNull { it.id == splitId }?.let { split ->
            SplitApplySheet(
                program = program,
                selectedSplit = split,
                blocks = blocks,
                selectedBlockId = selectedBlockId,
                selectedWeekId = selectedWeekId,
                defaultStartDay = program.startDay ?: 1,
                isAdvancedProgram = isAdvancedProgram,
                totalWeeks = totalWeeks,
                onDismiss = { sheetSplitId = null },
                onApply = {
                    onUpdateProgram(it)
                    sheetSplitId = null
                },
            )
        }
    }
}

@Composable
private fun SplitHeaderCard(
    program: Program,
    isAdvancedProgram: Boolean,
) {
    val label = when {
        program.blockSplitSelections.isNotEmpty() -> "Por bloque · ${program.blockSplitSelections.size} configurados"
        program.selectedSplitId != null -> SPLIT_TEMPLATES.firstOrNull { it.id == program.selectedSplitId }?.name ?: "Split activo"
        else -> "Sin split definido"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Catálogo de Splits", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isAdvancedProgram) {
                    "Compara plantillas y decide si usar un split global o uno distinto por bloque."
                } else {
                    "Explora plantillas, compáralas y aplícalas con ayuda de migración si ya hay sesiones creadas."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SplitTagBar(
    selectedTag: SplitTag?,
    onSelectTag: (SplitTag?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            androidx.compose.material3.FilterChip(
                selected = selectedTag == null,
                onClick = { onSelectTag(null) },
                label = { Text("Todos") },
            )
        }
        items(SplitTag.entries) { tag ->
            androidx.compose.material3.FilterChip(
                selected = selectedTag == tag,
                onClick = { onSelectTag(if (selectedTag == tag) null else tag) },
                label = { Text(tag.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@Composable
private fun CompareStrip(
    comparedIds: List<String>,
    onOpenCompare: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Comparador", fontWeight = FontWeight.Black)
                TextButton(onClick = onOpenCompare) { Text("Abrir") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(comparedIds, key = { it }) { splitId ->
                    val split = SPLIT_TEMPLATES.firstOrNull { it.id == splitId } ?: return@items
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(split.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier
                                    .clickable { onRemove(splitId) }
                                    .padding(2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitCatalogCard(
    split: SplitTemplate,
    isCurrent: Boolean,
    isCompared: Boolean,
    onToggleCompare: () -> Unit,
    onShowInfo: () -> Unit,
    onSelect: () -> Unit,
) {
    var expanded by rememberSaveable(split.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(split.name, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        if (isCurrent) {
                            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    "Actual",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        split.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row {
                    IconButton(onClick = onShowInfo) { Icon(Icons.Default.Info, contentDescription = "Info") }
                    IconButton(onClick = onToggleCompare) {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = "Comparar",
                            tint = if (isCompared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SplitPatternPreview(pattern = split.pattern)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DifficultyPill(split.difficulty)
                split.tags.take(4).forEach { tag -> TagPill(tag) }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SplitExplainer(title = "En qué consiste", items = listOf(split.description))
                    SplitExplainer(title = "Beneficios", items = split.pros.take(3))
                    SplitExplainer(title = "Precauciones", items = split.cons.take(3))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ver menos" else "Ver más")
                }
                Button(onClick = onSelect) { Text("Seleccionar") }
            }
        }
    }
}

@Composable
private fun SplitPatternPreview(pattern: List<String>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(pattern) { label ->
            val isRest = label.equals("Descanso", ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isRest) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    text = if (isRest) "Desc" else label.take(10),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DifficultyPill(difficulty: Difficulty) {
    val color = when (difficulty) {
        Difficulty.PRINCIPIANTE -> Color(0xFF10B981)
        Difficulty.INTERMEDIO -> Color(0xFFF59E0B)
        Difficulty.AVANZADO -> Color(0xFFEF4444)
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TagPill(tag: SplitTag) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Text(
            tag.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SplitExplainer(
    title: String,
    items: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 12.sp)
        items.forEach { item ->
            Text("• $item", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SplitInfoDialog(
    split: SplitTemplate,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(split.name, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(split.description)
                SplitExplainer("Beneficios", split.pros.take(4))
                SplitExplainer("Precauciones", split.cons.take(4))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun CompareDialog(
    splits: List<SplitTemplate>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comparar splits", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                splits.forEach { split ->
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(split.name, fontWeight = FontWeight.Black)
                            SplitPatternPreview(split.pattern)
                            Text(split.description, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Beneficios: ${split.pros.take(2).joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Precauciones: ${split.cons.take(2).joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitApplySheet(
    program: Program,
    selectedSplit: SplitTemplate,
    blocks: List<SplitBlockOption>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    defaultStartDay: Int,
    isAdvancedProgram: Boolean,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onApply: (Program) -> Unit,
) {
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    var temporalScope by rememberSaveable { mutableStateOf(TemporalSplitScope.CURRENT_WEEK) }
    var advancedMode by rememberSaveable { mutableStateOf(AdvancedSplitMode.GLOBAL) }
    var migrationMode by rememberSaveable { mutableStateOf(SessionMigrationMode.MIGRATE) }
    val blockSelections = remember {
        blocks.associate { it.id to selectedSplit.id }.toMutableMap()
    }

    val targetHasSessions = remember(program, selectedBlockId, selectedWeekId, temporalScope, advancedMode) {
        hasSessionsInTarget(program, selectedBlockId, selectedWeekId, temporalScope, advancedMode)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Aplicar split", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "Configura el alcance del cambio y cómo quieres tratar las sesiones existentes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedSplit.name, fontWeight = FontWeight.Black)
                    SplitPatternPreview(pattern = selectedSplit.pattern)
                }
            }

            WeekStartMenu(startDay = startDay, onSelect = { startDay = it })

            if (totalWeeks > 1 && !(isAdvancedProgram && advancedMode == AdvancedSplitMode.PER_BLOCK)) {
                ScopeSelector(
                    title = "Alcance temporal",
                    currentValue = temporalScope.name,
                    options = listOf(
                        "CURRENT_WEEK" to "Solo semana seleccionada",
                        "ALL_WEEKS" to "Todas las semanas",
                    ),
                    onSelect = { temporalScope = TemporalSplitScope.valueOf(it) },
                )
            }

            if (isAdvancedProgram) {
                ScopeSelector(
                    title = "Programa avanzado",
                    currentValue = advancedMode.name,
                    options = listOf(
                        "GLOBAL" to "Un split para todos los bloques",
                        "PER_BLOCK" to "Elegir uno distinto por bloque",
                    ),
                    onSelect = { advancedMode = AdvancedSplitMode.valueOf(it) },
                )
            }

            if (isAdvancedProgram && advancedMode == AdvancedSplitMode.PER_BLOCK) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Asignación por bloque", fontWeight = FontWeight.Black)
                    blocks.forEach { block ->
                        BlockSplitSelector(
                            block = block,
                            selectedSplitId = blockSelections[block.id] ?: selectedSplit.id,
                            onSelect = { blockSelections[block.id] = it },
                        )
                    }
                }
            }

            if (targetHasSessions) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Migración de sesiones", fontWeight = FontWeight.Black)
                    SplitModeCard(
                        title = "Migrar sesiones",
                        description = "Intenta mover sesiones al nuevo split según músculos trabajados y nombres.",
                        selected = migrationMode == SessionMigrationMode.MIGRATE,
                        onClick = { migrationMode = SessionMigrationMode.MIGRATE },
                    )
                    SplitModeCard(
                        title = "Empezar de cero",
                        description = "Genera sesiones base vacías según el split nuevo.",
                        selected = migrationMode == SessionMigrationMode.CLEAN,
                        onClick = { migrationMode = SessionMigrationMode.CLEAN },
                    )
                }
            }

            Button(
                onClick = {
                    onApply(
                        applySplitSelection(
                            program = program,
                            selectedSplit = selectedSplit,
                            selectedBlockId = selectedBlockId,
                            selectedWeekId = selectedWeekId,
                            startDay = startDay,
                            temporalScope = temporalScope,
                            advancedMode = advancedMode,
                            migrationMode = migrationMode,
                            perBlockSelections = blockSelections,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Aplicar split", fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun WeekStartMenu(
    startDay: Int,
    onSelect: (Int) -> Unit,
) {
    val days = listOf(1 to "Lunes", 2 to "Martes", 3 to "Miércoles", 4 to "Jueves", 5 to "Viernes", 6 to "Sábado", 7 to "Domingo")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Inicio de semana", fontWeight = FontWeight.Black)
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(days.firstOrNull { it.first == startDay }?.second ?: "Lunes")
                Text("Cambiar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            days.forEach { day ->
                DropdownMenuItem(
                    text = { Text(day.second) },
                    onClick = {
                        onSelect(day.first)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScopeSelector(
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Black)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                androidx.compose.material3.FilterChip(
                    selected = currentValue == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun SplitModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BlockSplitSelector(
    block: SplitBlockOption,
    selectedSplitId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSplit = SPLIT_TEMPLATES.firstOrNull { it.id == selectedSplitId }

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${block.macroName} · ${block.name}", fontWeight = FontWeight.Black, fontSize = 12.sp)
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    currentSplit?.name ?: "Seleccionar split",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SPLIT_TEMPLATES.filter { it.id != "custom" }.forEach { split ->
                    DropdownMenuItem(
                        text = { Text(split.name) },
                        onClick = {
                            onSelect(split.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun hasSessionsInTarget(
    program: Program,
    selectedBlockId: String?,
    selectedWeekId: String?,
    temporalScope: TemporalSplitScope,
    advancedMode: AdvancedSplitMode,
): Boolean {
    return program.macrocycles.any { macro ->
        macro.blocks.any { block ->
            val blockMatches = when {
                advancedMode == AdvancedSplitMode.PER_BLOCK -> true
                temporalScope == TemporalSplitScope.CURRENT_WEEK -> block.id == selectedBlockId
                else -> true
            }

            if (!blockMatches) return@any false

            block.mesocycles.any { meso ->
                meso.weeks.any { week ->
                    val weekMatches = when (temporalScope) {
                        TemporalSplitScope.CURRENT_WEEK -> week.id == selectedWeekId
                        TemporalSplitScope.ALL_WEEKS -> true
                    }
                    weekMatches && week.sessions.isNotEmpty()
                }
            }
        }
    }
}

private fun applySplitSelection(
    program: Program,
    selectedSplit: SplitTemplate,
    selectedBlockId: String?,
    selectedWeekId: String?,
    startDay: Int,
    temporalScope: TemporalSplitScope,
    advancedMode: AdvancedSplitMode,
    migrationMode: SessionMigrationMode,
    perBlockSelections: Map<String, String>,
): Program {
    val blockAssignments = if (advancedMode == AdvancedSplitMode.PER_BLOCK) perBlockSelections else emptyMap()

    return program.copy(
        startDay = startDay,
        selectedSplitId = if (advancedMode == AdvancedSplitMode.GLOBAL) selectedSplit.id else program.selectedSplitId,
        customSplitPattern = if (advancedMode == AdvancedSplitMode.GLOBAL) selectedSplit.pattern else program.customSplitPattern,
        blockSplitSelections = if (advancedMode == AdvancedSplitMode.PER_BLOCK) blockAssignments else emptyMap(),
        splitTrialSeen = false,
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    val blockSplit = if (advancedMode == AdvancedSplitMode.PER_BLOCK) {
                        SPLIT_TEMPLATES.firstOrNull { it.id == blockAssignments[block.id] } ?: selectedSplit
                    } else {
                        selectedSplit
                    }

                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    val shouldApply = when (temporalScope) {
                                        TemporalSplitScope.CURRENT_WEEK -> week.id == selectedWeekId
                                        TemporalSplitScope.ALL_WEEKS -> {
                                            when {
                                                advancedMode == AdvancedSplitMode.PER_BLOCK -> true
                                                selectedBlockId != null && program.macrocycles.sumOf { it.blocks.size } > 1 -> true
                                                else -> true
                                            }
                                        }
                                    }

                                    if (!shouldApply) {
                                        week
                                    } else {
                                        week.copy(
                                            sessions = buildSessionsForSplit(
                                                pattern = blockSplit.pattern,
                                                startDay = startDay,
                                                existingSessions = week.sessions,
                                                migrationMode = migrationMode,
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}

private fun buildSessionsForSplit(
    pattern: List<String>,
    startDay: Int,
    existingSessions: List<Session>,
    migrationMode: SessionMigrationMode,
): List<Session> {
    val trainingDays = patternToTrainingDays(pattern, startDay)
    if (trainingDays.isEmpty()) return emptyList()

    if (existingSessions.isEmpty() || migrationMode == SessionMigrationMode.CLEAN) {
        return normalizeMainSessions(
            trainingDays.map { day ->
                Session(
                    id = UUID.randomUUID().toString(),
                    name = day.label,
                    exercises = emptyList(),
                    parts = emptyList(),
                    dayOfWeek = day.dayOfWeek,
                    scheduleLabel = day.label,
                    isMainSession = true,
                )
            }
        )
    }

    val reassigned = existingSessions.map { session ->
        val target = bestTrainingDayForSession(session, trainingDays)
        session.copy(
            dayOfWeek = target.dayOfWeek,
            scheduleLabel = target.label,
        )
    }.toMutableList()

    val coveredDays = reassigned.mapNotNull { it.dayOfWeek }.toSet()
    trainingDays.filterNot { it.dayOfWeek in coveredDays }.forEach { missingDay ->
        reassigned.add(
            Session(
                id = UUID.randomUUID().toString(),
                name = missingDay.label,
                exercises = emptyList(),
                parts = emptyList(),
                dayOfWeek = missingDay.dayOfWeek,
                scheduleLabel = missingDay.label,
                isMainSession = false,
            )
        )
    }

    return normalizeMainSessions(reassigned)
}

private fun patternToTrainingDays(
    pattern: List<String>,
    startDay: Int,
): List<SplitPatternDay> {
    val orderedDays = listOf(1, 2, 3, 4, 5, 6, 7)
    val offset = (startDay - 1).coerceIn(0, 6)
    val rotated = orderedDays.drop(offset) + orderedDays.take(offset)

    return pattern.mapIndexedNotNull { index, label ->
        if (label.equals("Descanso", ignoreCase = true)) null
        else SplitPatternDay(label = label, dayOfWeek = rotated[index % rotated.size])
    }
}

private fun bestTrainingDayForSession(
    session: Session,
    trainingDays: List<SplitPatternDay>,
): SplitPatternDay {
    val sessionMuscles = collectSessionMuscles(session)
    val sessionText = buildString {
        append(session.name.lowercase())
        append(' ')
        append(session.description.orEmpty().lowercase())
    }

    return trainingDays.maxByOrNull { day ->
        scoreSplitDay(day.label, sessionMuscles, sessionText)
    } ?: trainingDays.first()
}

private fun collectSessionMuscles(session: Session): Set<String> {
    val muscles = linkedSetOf<String>()

    fun collectFromExercises(exercises: List<Exercise>) {
        exercises.forEach { exercise ->
            val info = exercise.exerciseDbId?.lowercase()?.let { EXERCISE_DATABASE_BY_ID[it] }
            SessionMuscleFilter.relevantMusclesFor(info).forEach { involved ->
                muscles.add(
                    normalizeCanonicalMuscle(
                        VolumeCalculator.normalizeMuscleGroup(
                            specificMuscle = involved.muscle,
                            emphasis = involved.emphasis,
                        )
                    )
                )
            }
        }
    }

    collectFromExercises(session.exercises)
    session.parts.forEach { part: SessionPart -> collectFromExercises(part.exercises) }
    listOfNotNull(session.sessionB, session.sessionC, session.sessionD).forEach { nested ->
        muscles.addAll(collectSessionMuscles(nested))
    }

    return muscles
}

private fun normalizeCanonicalMuscle(muscle: String): String {
    return when (muscle.lowercase()) {
        "cuadriceps", "cuádriceps" -> "Cuádriceps"
        "gluteos", "glúteos" -> "Glúteos"
        "biceps", "bíceps" -> "Bíceps"
        "triceps", "tríceps" -> "Tríceps"
        else -> muscle
    }
}

private val upperBodyKeywords = setOf("Pectorales", "Dorsales", "Trapecio", "Bíceps", "Tríceps", "Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior")
private val lowerBodyKeywords = setOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores")

private fun scoreSplitDay(
    label: String,
    sessionMuscles: Set<String>,
    sessionText: String,
): Int {
    val keywords = splitKeywords(label)
    val textBonus = keywords.count { sessionText.contains(it.lowercase()) } * 3
    val muscleBonus = sessionMuscles.count { muscle ->
        keywords.any { keyword ->
            muscle.lowercase().contains(keyword.lowercase()) || keyword.lowercase().contains(muscle.lowercase())
        }
    } * 4
    val genericBonus = when {
        label.contains("Torso", ignoreCase = true) && sessionMuscles.any { it in upperBodyKeywords } -> 5
        label.contains("Full", ignoreCase = true) && sessionMuscles.isNotEmpty() -> 4
        label.contains("Pierna", ignoreCase = true) && sessionMuscles.any { it in lowerBodyKeywords } -> 5
        else -> 0
    }
    return textBonus + muscleBonus + genericBonus
}

private fun splitKeywords(label: String): Set<String> {
    val lower = label.lowercase()
    val keywords = linkedSetOf<String>()
    if ("empuje" in lower || "push" in lower) keywords.addAll(listOf("Pectorales", "Tríceps", "Deltoides"))
    if ("tirón" in lower || "tiron" in lower || "pull" in lower || "tracción" in lower || "traccion" in lower) keywords.addAll(listOf("Dorsales", "Trapecio", "Bíceps", "Deltoides Posterior"))
    if ("pierna" in lower || "lower" in lower) keywords.addAll(listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas"))
    if ("torso" in lower || "upper" in lower) keywords.addAll(upperBodyKeywords)
    if ("full" in lower || "sbd" in lower) keywords.addAll(upperBodyKeywords + lowerBodyKeywords)
    if ("pecho" in lower || "banca" in lower) keywords.add("Pectorales")
    if ("espalda" in lower) keywords.addAll(listOf("Dorsales", "Trapecio", "Erectores Espinales"))
    if ("hombro" in lower) keywords.addAll(listOf("Deltoides Anterior", "Deltoides Lateral", "Deltoides Posterior"))
    if ("brazo" in lower) keywords.addAll(listOf("Bíceps", "Tríceps"))
    if ("cuádriceps" in lower || "cuadriceps" in lower) keywords.add("Cuádriceps")
    if ("isquios" in lower || "femoral" in lower) keywords.add("Isquiosurales")
    if ("glúteo" in lower || "gluteo" in lower) keywords.add("Glúteos")
    if ("peso muerto" in lower || "deadlift" in lower) keywords.addAll(listOf("Isquiosurales", "Glúteos", "Erectores Espinales", "Trapecio"))
    if ("sentadilla" in lower || "squat" in lower) keywords.addAll(listOf("Cuádriceps", "Glúteos"))
    if (keywords.isEmpty()) keywords.add(label.replaceFirstChar { it.uppercase() })
    return keywords
}

private fun normalizeMainSessions(sessions: List<Session>): List<Session> {
    val mainByDay = mutableMapOf<Int, String>()
    val fallbackByDay = mutableMapOf<Int, String>()

    sessions.forEach { session ->
        val day = session.dayOfWeek ?: 1
        fallbackByDay.putIfAbsent(day, session.id)
        if (session.isMainSession && day !in mainByDay) {
            mainByDay[day] = session.id
        }
    }

    fallbackByDay.forEach { (day, sessionId) ->
        mainByDay.putIfAbsent(day, sessionId)
    }

    return sessions.map { session ->
        val day = session.dayOfWeek ?: 1
        session.copy(isMainSession = mainByDay[day] == session.id)
    }
}
