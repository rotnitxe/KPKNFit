package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.templates.SuggestionPrefs
import com.example.kpkn.domain.training.AdvancedSplitMode
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.SessionMigrationMode
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitApplicationRequest
import com.example.kpkn.domain.training.SplitBlockOption
import com.example.kpkn.domain.training.SplitImpactSummary
import com.example.kpkn.domain.training.SplitTemplateAlternativePreview
import com.example.kpkn.domain.training.SplitTemporalScope
import com.example.kpkn.domain.training.SplitWeekOption
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknDropdownMenu

private const val SPLIT_PAGE_SIZE = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitView(
    program: Program,
    selectedBlockId: String?,
    selectedWeekId: String?,
    onUpdateProgram: (Program) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<SplitTag?>(null) }
    var currentPage by rememberSaveable { mutableStateOf(1) }
    var infoSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomEditor by rememberSaveable { mutableStateOf(false) }
    var pendingCustomSplit by remember { mutableStateOf<Pair<SplitTemplate, Int>?>(null) }
    var showCompare by rememberSaveable { mutableStateOf(false) }
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    var showMultiApply by rememberSaveable { mutableStateOf(false) }
    val comparedIds = remember { mutableStateListOf<String>() }
    val multiSelectedIds = remember { mutableStateListOf<String>() }

    val blocks = remember(program.id, program.macrocycles) {
        SplitApplicationEngine.buildBlockOptions(program)
    }
    val weekOptions = remember(program.id, program.macrocycles) {
        SplitApplicationEngine.buildWeekOptions(program)
    }
    val totalWeeks = remember(program.id, program.macrocycles) {
        program.macrocycles.sumOf { macro -> macro.blocks.sumOf { block -> block.mesocycles.sumOf { meso -> meso.weeks.size } } }
    }
    val isAdvancedProgram = program.structure == ProgramStructure.COMPLEX

    val filteredSplits = remember(searchQuery, selectedTag) {
        SPLIT_TEMPLATES.filter { split ->
            if (split.id == "custom") return@filter false
            if (selectedTag == SplitTag.PERSONALIZADO) return@filter false
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

    LaunchedEffect(searchQuery, selectedTag) { currentPage = 1 }
    val totalPages = ((filteredSplits.size + SPLIT_PAGE_SIZE - 1) / SPLIT_PAGE_SIZE).coerceAtLeast(1)
    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) currentPage = totalPages
    }
    val visibleSplits = remember(filteredSplits, currentPage, selectedTag) {
        if (selectedTag == null) filteredSplits.drop((currentPage - 1) * SPLIT_PAGE_SIZE).take(SPLIT_PAGE_SIZE)
        else filteredSplits
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompactSplitHeader(isAdvancedProgram = isAdvancedProgram, onBack = onBack)

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (filteredSplits.isEmpty()) "Sin resultados" else "${filteredSplits.size} splits disponibles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    multiSelectMode = !multiSelectMode
                    if (!multiSelectMode) multiSelectedIds.clear()
                },
            ) {
                Text(if (multiSelectMode) "Cancelar selección" else "Seleccionar varios")
            }
        }

        if (multiSelectMode && multiSelectedIds.isNotEmpty()) {
            Button(onClick = { showMultiApply = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Aplicar ${multiSelectedIds.size} splits", fontWeight = FontWeight.Black)
            }
        }

        if (comparedIds.isNotEmpty()) {
            CompareStrip(
                comparedIds = comparedIds,
                onOpenCompare = { showCompare = true },
                onRemove = { comparedIds.remove(it) },
            )
        }

        if (selectedTag == SplitTag.PERSONALIZADO) {
            CustomSplitCatalogCard(
                program = program,
                isCurrent = program.selectedSplitId == "custom" || program.blockSplitSelections.containsValue("custom") || program.weekSplitSelections.containsValue("custom"),
                onCreate = { showCustomEditor = true },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                visibleSplits.forEach { split ->
                    SplitCatalogCard(
                        split = split,
                        isCurrent = program.selectedSplitId == split.id || program.blockSplitSelections.containsValue(split.id) || program.weekSplitSelections.containsValue(split.id),
                        isCompared = split.id in comparedIds,
                        isMultiSelectMode = multiSelectMode,
                        isMultiSelected = split.id in multiSelectedIds,
                        onToggleCompare = {
                            if (split.id in comparedIds) comparedIds.remove(split.id)
                            else if (comparedIds.size < 3) comparedIds.add(split.id)
                        },
                        onToggleMultiSelect = {
                            if (split.id in multiSelectedIds) multiSelectedIds.remove(split.id)
                            else multiSelectedIds.add(split.id)
                        },
                        onShowInfo = { infoSplitId = split.id },
                        onSelect = { sheetSplitId = split.id },
                    )
                }
                if (selectedTag == null && totalPages > 1) {
                    SplitPagination(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageChange = { currentPage = it },
                    )
                }
                Spacer(Modifier.height(96.dp))
            }
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
                weeks = weekOptions,
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

    if (showCustomEditor) {
        CustomSplitEditorSheet(
            program = program,
            defaultStartDay = program.startDay ?: 1,
            onDismiss = { showCustomEditor = false },
            onApply = { customSplit, startDay ->
                pendingCustomSplit = customSplit to startDay
                showCustomEditor = false
            },
        )
    }

    pendingCustomSplit?.let { (customSplit, startDay) ->
        SplitApplySheet(
            program = program,
            selectedSplit = customSplit,
            blocks = blocks,
            selectedBlockId = selectedBlockId,
            selectedWeekId = selectedWeekId,
            weeks = weekOptions,
            defaultStartDay = startDay,
            isAdvancedProgram = isAdvancedProgram,
            totalWeeks = totalWeeks,
            onDismiss = { pendingCustomSplit = null },
            onApply = {
                onUpdateProgram(it)
                pendingCustomSplit = null
            },
        )
    }
    if (showMultiApply) {
        MultiSplitApplySheet(
            program = program,
            splits = multiSelectedIds.mapNotNull { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id } },
            blocks = blocks,
            weeks = weekOptions,
            selectedBlockId = selectedBlockId,
            defaultStartDay = program.startDay ?: 1,
            onDismiss = { showMultiApply = false },
            onApply = {
                onUpdateProgram(it)
                showMultiApply = false
                multiSelectMode = false
                multiSelectedIds.clear()
            },
        )
    }
}

@Composable
private fun CompactSplitHeader(
    isAdvancedProgram: Boolean,
    onBack: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text("Cat\u00E1logo de Splits", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        } else {
            Text("Cat\u00E1logo de Splits", fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Text(
            "Un split es la distribuci\u00F3n semanal de grupos musculares o patrones de movimiento. Elegir el adecuado define la frecuencia con la que entrenas cada m\u00FAsculo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
                label = { Text(splitTagLabel(tag)) },
            )
        }
    }
}

private fun splitTagLabel(tag: SplitTag): String = when (tag) {
    SplitTag.RECOMENDADO_KPKN -> "Recomendado KPKN"
    SplitTag.ALTA_FRECUENCIA -> "Alta frecuencia"
    SplitTag.BAJA_FRECUENCIA -> "Baja frecuencia"
    SplitTag.BALANCEADO -> "Balanceado"
    SplitTag.ALTO_VOLUMEN -> "Alto volumen"
    SplitTag.ALTA_TOLERANCIA -> "Alta tolerancia"
    SplitTag.PERSONALIZADO -> "Personalizado"
    SplitTag.POWERLIFTING -> "Powerlifting"
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

@Composable
private fun SplitPagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((1..totalPages).toList()) { page ->
            androidx.compose.material3.FilterChip(
                selected = currentPage == page,
                onClick = { onPageChange(page) },
                label = { Text(page.toString()) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitCatalogCard(
    split: SplitTemplate,
    isCurrent: Boolean,
    isCompared: Boolean,
    isMultiSelectMode: Boolean,
    isMultiSelected: Boolean,
    onToggleCompare: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onShowInfo: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    if (isMultiSelectMode) {
                        Checkbox(checked = isMultiSelected, onCheckedChange = { onToggleMultiSelect() })
                    }
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
                AvailabilityPill(split)
                split.tags.take(3).forEach { tag -> TagPill(tag) }
                if (split.tags.size > 3) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                        Text(
                            "+${split.tags.size - 3}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = if (isMultiSelectMode) onToggleMultiSelect else onSelect) {
                    Text(if (isMultiSelectMode) if (isMultiSelected) "Quitar" else "Marcar" else "Seleccionar")
                }
            }
        }
    }
}

@Composable
private fun CustomSplitCatalogCard(
    program: Program,
    isCurrent: Boolean,
    onCreate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(program.customSplitName ?: "Crear split personalizado", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        program.customSplitDescription ?: "Parte desde un lienzo en blanco, define el inicio de semana y guarda tu propia distribución.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
            SplitPatternPreview(pattern = program.customSplitPattern.ifEmpty { List(7) { "Descanso" } })
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text(if (isCurrent) "Editar personalizado" else "Crear y usar")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomSplitEditorSheet(
    program: Program,
    defaultStartDay: Int,
    onDismiss: () -> Unit,
    onApply: (SplitTemplate, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(program.customSplitName ?: "Mi split") }
    var description by rememberSaveable { mutableStateOf(program.customSplitDescription ?: "") }
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    val pattern = remember(program.id) {
        mutableStateListOf<String>().apply {
            addAll(program.customSplitPattern.takeIf { it.size == 7 } ?: List(7) { "Descanso" })
        }
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Split personalizado", fontWeight = FontWeight.Black, fontSize = 20.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
            )
            if (ProgramCalendarEngine.isCalendarized(program)) {
                Text("El inicio semanal está fijado por el calendario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                WeekStartMenu(startDay = startDay, onSelect = { startDay = it })
            }
            Text("Distribución semanal", fontWeight = FontWeight.Black)
            pattern.forEachIndexed { index, label ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "D${index + 1}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                        )
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { pattern[index] = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Sesión o Descanso") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
            Button(
                onClick = {
                    val normalizedPattern = pattern.map { it.trim().ifBlank { "Descanso" } }
                    onApply(
                        SplitTemplate(
                            id = "custom",
                            name = name.trim().ifBlank { "Mi split" },
                            description = description.trim().ifBlank { "Split personalizado" },
                            tags = listOf(SplitTag.PERSONALIZADO),
                            pattern = normalizedPattern,
                            difficulty = Difficulty.INTERMEDIO,
                            pros = listOf("Diseñado según tus reglas"),
                            cons = listOf("Depende de una buena distribución de fatiga"),
                        ),
                        startDay,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) {
                Text("Guardar y usar", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
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
private fun AvailabilityPill(split: SplitTemplate) {
    val trainingDays = split.pattern.count { !it.equals("Descanso", ignoreCase = true) }
    Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFF0EA5E9).copy(alpha = 0.15f)) {
        Text(
            "$trainingDays días/semana",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color(0xFF0284C7),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TagPill(tag: SplitTag) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Text(
            splitTagLabel(tag),
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
    KpknAlertDialog(
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
    KpknAlertDialog(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SplitApplySheet(
    program: Program,
    selectedSplit: SplitTemplate,
    blocks: List<SplitBlockOption>,
    weeks: List<SplitWeekOption>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    defaultStartDay: Int,
    isAdvancedProgram: Boolean,
    totalWeeks: Int,
    onDismiss: () -> Unit,
    onApply: (Program) -> Unit,
) {
    val isCalendarized = ProgramCalendarEngine.isCalendarized(program)
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    var temporalScope by rememberSaveable { mutableStateOf(SplitTemporalScope.CURRENT_WEEK) }
    var advancedMode by rememberSaveable { mutableStateOf(AdvancedSplitMode.GLOBAL) }
    var migrationMode by rememberSaveable { mutableStateOf<SessionMigrationMode?>(null) }
    var destructiveAccepted by rememberSaveable { mutableStateOf(false) }
    var showFinalConfirm by rememberSaveable { mutableStateOf(false) }
    val blockSelections = remember {
        mutableStateMapOf<String, String>().apply { putAll(blocks.associate { it.id to selectedSplit.id }) }
    }
    val selectedWeekIds = remember(selectedWeekId) {
        mutableStateListOf<String>().apply { selectedWeekId?.let(::add) }
    }
    var focusOverrides by remember(selectedSplit.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val weekPreview = remember(selectedSplit, focusOverrides) {
        SplitApplicationEngine.prebuiltWeekPreview(
            split = selectedSplit,
            prefs = SuggestionPrefs(
                preferredDifficulty = selectedSplit.difficulty,
                forcedTemplateByDayIndex = focusOverrides,
            ),
        )
    }
    val templatePreview = weekPreview.days
    val prebuiltAvailable = templatePreview.isNotEmpty() && templatePreview.all { it.isAvailable }

    LaunchedEffect(temporalScope, migrationMode, advancedMode, selectedWeekIds.toList(), blockSelections.toMap()) {
        destructiveAccepted = false
    }

    val resolvedMode = migrationMode ?: SessionMigrationMode.MIGRATE
    val request = SplitApplicationRequest(
        program = program,
        selectedSplit = selectedSplit,
        selectedBlockId = selectedBlockId,
        selectedWeekId = selectedWeekId,
        startDay = startDay,
        temporalScope = temporalScope,
        selectedWeekIds = selectedWeekIds.toSet(),
        advancedMode = advancedMode,
        migrationMode = resolvedMode,
        perBlockSelections = blockSelections.toMap(),
        prebuiltPrefs = SuggestionPrefs(
            preferredDifficulty = selectedSplit.difficulty,
            forcedTemplateByDayIndex = focusOverrides,
        ),
    )
    val impact = SplitApplicationEngine.impactSummary(request)
    val targetHasSessions = impact.affectedSessions > 0
    val selectedModeAvailable = migrationMode != SessionMigrationMode.PREBUILT || prebuiltAvailable
    val canApply = impact.affectedWeeks > 0 && migrationMode != null && selectedModeAvailable && (!impact.willReplaceSessions || destructiveAccepted)

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Aplicar split", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "Elige el alcance y cómo se crearán las sesiones. Ninguna opción se aplica hasta que confirmes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedSplit.name, fontWeight = FontWeight.Black)
                    SplitPatternPreview(pattern = selectedSplit.pattern)
                }
            }

            if (isCalendarized) {
                Text(
                    "El inicio semanal está fijado por el calendario del programa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WeekStartMenu(startDay = startDay, onSelect = { startDay = it })
            }
            if (totalWeeks > 1 && !(isAdvancedProgram && advancedMode == AdvancedSplitMode.PER_BLOCK)) {
                ScopeSelector(
                    title = "Alcance temporal",
                    currentValue = temporalScope.name,
                    options = listOf(
                        "CURRENT_WEEK" to "Semana seleccionada",
                        "SELECTED_WEEKS" to "Elegir semanas",
                        "CURRENT_BLOCK" to "Bloque completo",
                        "WHOLE_PROGRAM" to "Todo el programa",
                    ),
                    onSelect = { temporalScope = SplitTemporalScope.valueOf(it) },
                )
            }

            if (temporalScope == SplitTemporalScope.SELECTED_WEEKS && advancedMode != AdvancedSplitMode.PER_BLOCK) {
                WeekMultiSelector(
                    weeks = weeks,
                    selectedWeekIds = selectedWeekIds,
                    onToggle = { weekId ->
                        if (weekId in selectedWeekIds) selectedWeekIds.remove(weekId) else selectedWeekIds.add(weekId)
                    },
                )
            }

            if (isAdvancedProgram) {
                ScopeSelector(
                    title = "Programa avanzado",
                    currentValue = advancedMode.name,
                    options = listOf(
                        "GLOBAL" to "Un split según el alcance",
                        "PER_BLOCK" to "Elegir uno distinto por bloque",
                    ),
                    onSelect = { advancedMode = AdvancedSplitMode.valueOf(it) },
                )
            }

            if (isAdvancedProgram && advancedMode == AdvancedSplitMode.PER_BLOCK) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Asignación por bloque", fontWeight = FontWeight.Black)
                    Text(
                        "Se aplicará el split elegido a todas las semanas de cada bloque.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    blocks.forEach { block ->
                        BlockSplitSelector(
                            block = block,
                            selectedSplitId = blockSelections[block.id] ?: selectedSplit.id,
                            onSelect = { blockSelections[block.id] = it },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Contenido de las sesiones", fontWeight = FontWeight.Black)
                if (targetHasSessions) {
                    SplitModeCard(
                        title = "Conservar y reubicar",
                        description = "Mantiene los ejercicios existentes e intenta alinearlos con los días del nuevo split.",
                        selected = migrationMode == SessionMigrationMode.MIGRATE,
                        onClick = { migrationMode = SessionMigrationMode.MIGRATE },
                    )
                }
                SplitModeCard(
                    title = "Sesiones vacías",
                    description = "Crea la estructura de días del split sin ejercicios.",
                    selected = migrationMode == SessionMigrationMode.CLEAN,
                    onClick = { migrationMode = SessionMigrationMode.CLEAN },
                )
                SplitModeCard(
                    title = "Sesiones pre-creadas",
                    description = "Carga ejercicios desde la plantilla más compatible para cada día.",
                    selected = migrationMode == SessionMigrationMode.PREBUILT,
                    onClick = { migrationMode = SessionMigrationMode.PREBUILT },
                )
            }

            if (migrationMode == SessionMigrationMode.PREBUILT) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Resumen de plantillas", fontWeight = FontWeight.Black)
                        if (weekPreview.exceedsWeeklyBudget || weekPreview.warnings.isNotEmpty()) {
                            val warningText = weekPreview.warnings.joinToString(" · ")
                                .ifBlank { "La carga semanal proyectada supera el presupuesto suave de anillos." }
                            Text(
                                warningText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        templatePreview.forEach { preview ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (preview.isAvailable) {
                                        "${preview.dayLabel} → ${preview.templateName} · ${preview.exerciseCount} ejercicios"
                                    } else {
                                        "${preview.dayLabel} → ${preview.unavailabilityReason ?: "Sin plantilla compatible"}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (preview.isAvailable) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                )
                                if (preview.isAvailable) {
                                    val chipOptions = buildList {
                                        preview.templateId?.let { id ->
                                            add(
                                                SplitTemplateAlternativePreview(
                                                    templateId = id,
                                                    templateName = preview.templateName.orEmpty(),
                                                    primaryFocusMuscle = preview.primaryFocusMuscle,
                                                    focusLabel = preview.focusLabel
                                                        ?: preview.primaryFocusMuscle
                                                        ?: preview.templateName.orEmpty(),
                                                )
                                            )
                                        }
                                        addAll(preview.alternatives)
                                    }.distinctBy { it.templateId }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        chipOptions.forEach { option ->
                                            val selected = preview.templateId == option.templateId
                                            androidx.compose.material3.FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    focusOverrides = focusOverrides + (preview.dayIndex to option.templateId)
                                                },
                                                label = {
                                                    Text(
                                                        option.focusLabel.ifBlank { option.templateName },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (impact.willReplaceSessions) {
                DestructiveImpactCard(
                    impact = impact,
                    accepted = destructiveAccepted,
                    onAcceptedChange = { destructiveAccepted = it },
                )
            }

            Button(
                onClick = {
                    if (impact.isLargeDestructiveChange) showFinalConfirm = true
                    else onApply(SplitApplicationEngine.apply(request))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canApply,
            ) {
                Text(
                    when {
                        migrationMode == null -> "Elige cómo crear las sesiones"
                        impact.willReplaceSessions -> "Reemplazar ${impact.affectedSessions} sesiones"
                        else -> "Aplicar split"
                    },
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showFinalConfirm) {
        KpknAlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            title = { Text("Confirmar reemplazo", fontWeight = FontWeight.Black) },
            text = { Text("Se reemplazarán ${impact.affectedSessions} sesiones en ${impact.affectedWeeks} semanas. Esta acción no se puede deshacer automáticamente.") },
            confirmButton = {
                Button(onClick = {
                    showFinalConfirm = false
                    onApply(SplitApplicationEngine.apply(request))
                }) { Text("Sí, reemplazar") }
            },
            dismissButton = { TextButton(onClick = { showFinalConfirm = false }) { Text("Cancelar") } },
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MultiSplitApplySheet(
    program: Program,
    splits: List<SplitTemplate>,
    blocks: List<SplitBlockOption>,
    weeks: List<SplitWeekOption>,
    selectedBlockId: String?,
    defaultStartDay: Int,
    onDismiss: () -> Unit,
    onApply: (Program) -> Unit,
) {
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    var temporalScope by rememberSaveable { mutableStateOf(SplitTemporalScope.CURRENT_BLOCK) }
    var migrationMode by rememberSaveable { mutableStateOf<SessionMigrationMode?>(null) }
    var destructiveAccepted by rememberSaveable { mutableStateOf(false) }
    var showFinalConfirm by rememberSaveable { mutableStateOf(false) }
    val selectedWeekIds = remember(selectedBlockId) {
        mutableStateListOf<String>().apply {
            addAll(weeks.filter { selectedBlockId == null || it.blockId == selectedBlockId }.map { it.id })
        }
    }

    val targetWeeks = when (temporalScope) {
        SplitTemporalScope.SELECTED_WEEKS -> weeks.filter { it.id in selectedWeekIds }
        SplitTemporalScope.CURRENT_BLOCK -> weeks.filter { selectedBlockId == null || it.blockId == selectedBlockId }
        SplitTemporalScope.WHOLE_PROGRAM -> weeks
        SplitTemporalScope.CURRENT_WEEK -> weeks.filter { it.id in selectedWeekIds.take(1) }
    }
    val affectedSessions = targetWeeks.sumOf { it.sessions.size }
    val willReplace = migrationMode != null && migrationMode != SessionMigrationMode.MIGRATE && affectedSessions > 0
    val prebuiltAvailable = splits.all { split ->
        SplitApplicationEngine.prebuiltWeekPreview(split).days.all { it.isAvailable }
    }
    val canApply = migrationMode != null && splits.isNotEmpty() && targetWeeks.isNotEmpty() &&
        (migrationMode != SessionMigrationMode.PREBUILT || prebuiltAvailable) && (!willReplace || destructiveAccepted)

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Aplicar varios splits", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(
                "Los splits seleccionados se distribuirán por las semanas destino en el mismo orden. Si hay más semanas que splits, se repiten en ciclo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                splits.forEach { split -> TagLikeText(split.name) }
            }

            if (ProgramCalendarEngine.isCalendarized(program)) {
                Text("El inicio semanal está fijado por el calendario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                WeekStartMenu(startDay = startDay, onSelect = { startDay = it })
            }

            ScopeSelector(
                title = "Destino",
                currentValue = temporalScope.name,
                options = listOf(
                    "SELECTED_WEEKS" to "Elegir semanas",
                    "CURRENT_BLOCK" to "Bloque actual",
                    "WHOLE_PROGRAM" to "Todo el programa",
                ),
                onSelect = { temporalScope = SplitTemporalScope.valueOf(it) },
            )

            if (temporalScope == SplitTemporalScope.SELECTED_WEEKS) {
                WeekMultiSelector(
                    weeks = weeks,
                    selectedWeekIds = selectedWeekIds,
                    onToggle = { weekId ->
                        if (weekId in selectedWeekIds) selectedWeekIds.remove(weekId)
                        else selectedWeekIds.add(weekId)
                    },
                )
            }

            if (blocks.size > 1 && selectedBlockId != null && temporalScope == SplitTemporalScope.CURRENT_BLOCK) {
                Text("Destino: bloque actual", fontWeight = FontWeight.Bold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Contenido de las sesiones", fontWeight = FontWeight.Black)
                SplitModeCard(
                    title = "Conservar y reubicar",
                    description = "Mantiene sesiones y las reubica según el split que toque en cada semana.",
                    selected = migrationMode == SessionMigrationMode.MIGRATE,
                    onClick = { migrationMode = SessionMigrationMode.MIGRATE },
                )
                SplitModeCard(
                    title = "Sesiones vacías",
                    description = "Reemplaza las semanas destino por sesiones base vacías.",
                    selected = migrationMode == SessionMigrationMode.CLEAN,
                    onClick = { migrationMode = SessionMigrationMode.CLEAN },
                )
                SplitModeCard(
                    title = "Sesiones pre-creadas",
                    description = "Carga ejercicios desde la mejor plantilla compatible de cada split.",
                    selected = migrationMode == SessionMigrationMode.PREBUILT,
                    onClick = { migrationMode = SessionMigrationMode.PREBUILT },
                )
            }

            if (willReplace) {
                DestructiveImpactCard(
                    impact = SplitImpactSummary(targetWeeks.size, affectedSessions, willReplaceSessions = true),
                    accepted = destructiveAccepted,
                    onAcceptedChange = { destructiveAccepted = it },
                )
            }

            Button(
                onClick = {
                    if (willReplace && (targetWeeks.size > 1 || affectedSessions > 4)) {
                        showFinalConfirm = true
                    } else {
                        onApply(applyMultipleSplits(program, splits, targetWeeks, selectedBlockId, startDay, migrationMode ?: return@Button))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canApply,
            ) {
                Text(when { migrationMode == null -> "Elige cómo crear las sesiones"; willReplace -> "Reemplazar $affectedSessions sesiones"; else -> "Aplicar splits" }, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    if (showFinalConfirm) {
        KpknAlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            title = { Text("Confirmar aplicación múltiple", fontWeight = FontWeight.Black) },
            text = { Text("Se reemplazarán $affectedSessions sesiones en ${targetWeeks.size} semanas usando ${splits.size} splits.") },
            confirmButton = {
                Button(onClick = {
                    showFinalConfirm = false
                    onApply(applyMultipleSplits(program, splits, targetWeeks, selectedBlockId, startDay, migrationMode ?: return@Button))
                }) { Text("Sí, aplicar") }
            },
            dismissButton = { TextButton(onClick = { showFinalConfirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TagLikeText(text: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

private fun applyMultipleSplits(
    program: Program,
    splits: List<SplitTemplate>,
    targetWeeks: List<SplitWeekOption>,
    selectedBlockId: String?,
    startDay: Int,
    migrationMode: SessionMigrationMode,
): Program {
    if (splits.isEmpty() || targetWeeks.isEmpty()) return program
    var updated = program
    splits.forEachIndexed { splitIndex, split ->
        val weekIds = targetWeeks.filterIndexed { index, _ -> index % splits.size == splitIndex }.map { it.id }.toSet()
        if (weekIds.isNotEmpty()) {
            updated = SplitApplicationEngine.apply(
                SplitApplicationRequest(
                    program = updated,
                    selectedSplit = split,
                    selectedBlockId = selectedBlockId,
                    selectedWeekId = weekIds.first(),
                    startDay = startDay,
                    temporalScope = SplitTemporalScope.SELECTED_WEEKS,
                    selectedWeekIds = weekIds,
                    advancedMode = AdvancedSplitMode.GLOBAL,
                    migrationMode = migrationMode,
                )
            )
        }
    }
    return updated
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
        KpknDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

@OptIn(ExperimentalLayoutApi::class)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekMultiSelector(
    weeks: List<SplitWeekOption>,
    selectedWeekIds: List<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Semanas del bloque", fontWeight = FontWeight.Black)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            weeks.forEach { week ->
                androidx.compose.material3.FilterChip(
                    selected = week.id in selectedWeekIds,
                    onClick = { onToggle(week.id) },
                    label = { Text(week.name) },
                )
            }
        }
    }
}

@Composable
private fun DestructiveImpactCard(
    impact: SplitImpactSummary,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Atención: empezar desde cero", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Se reemplazarán ${impact.affectedSessions} sesiones en ${impact.affectedWeeks} semanas. Revisa el alcance antes de continuar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = onAcceptedChange)
                Text(
                    "Entiendo que se reemplazarán estas sesiones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
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
            KpknDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
