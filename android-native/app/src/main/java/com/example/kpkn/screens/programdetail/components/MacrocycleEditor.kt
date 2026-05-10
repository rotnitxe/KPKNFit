package com.example.kpkn.screens.programdetail.components

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.models.normalizedTemporalStructure
import com.example.kpkn.data.models.primaryLoopCadenceCycles
import com.example.kpkn.data.models.primaryLoopLengthWeeks
import com.example.kpkn.data.models.simpleCycleWeeks
import com.example.kpkn.data.models.totalBlockCount
import com.example.kpkn.data.models.totalMesocycleCount
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.buildProgramDraft
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditor(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var expandedBlocks by remember { mutableStateOf(setOf("0")) }
    var editingBlock by remember { mutableStateOf<EditingItem?>(null) }
    var editingWeek by remember { mutableStateOf<EditingItem?>(null) }
    var editingExistingWeek by remember { mutableStateOf<EditingWeekTarget?>(null) }
    var editingKeyDate by remember { mutableStateOf<ProgramKeyDate?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var pendingSimpleToAdvanced by remember { mutableStateOf(false) }
    var editingTimelineStartDate by remember(program.timelineStartDate) { mutableStateOf(program.timelineStartDate ?: "") }
    var editingCompetitionDate by remember(program.keyDates) {
        val competition = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }
        mutableStateOf((competition?.eventDate ?: competition?.startDate).orEmpty())
    }
    var showKeyDatesSheet by remember { mutableStateOf(false) }
    var showAdvancedRoadmap by remember { mutableStateOf(false) }
    var showLibrarySheet by remember { mutableStateOf(false) }
    var showLoopsSheet by remember { mutableStateOf(false) }

    val temporalInsight = remember(program) { program.toTemporalInsight() }
    val stats = remember(program) { program.toProgramStats() }
    val advancedRoadmap = remember(program) { buildAdvancedRoadmap(program) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacrocycleToolbar(
            insight = temporalInsight,
            stats = stats,
            keyDatesCount = program.keyDates.size,
            hasTimelineStartDate = !program.timelineStartDate.isNullOrBlank(),
            showRoadmap = showAdvancedRoadmap,
            onToggleRoadmap = { showAdvancedRoadmap = !showAdvancedRoadmap },
            onOpenKeyDates = { showKeyDatesSheet = true },
            onOpenLibrary = { showLibrarySheet = true },
            onOpenLoops = { showLoopsSheet = true },
        )
        if (!temporalInsight.isSimple && showAdvancedRoadmap) {
            AdvancedRoadmapCard(
                roadmap = advancedRoadmap,
                onFocusWeek = onFocusWeek,
                onCreateSessionForWeek = onCreateSessionForWeek,
            )
        }

        program.ensureMacrocycle().macrocycles.forEachIndexed { macroIdx, macro ->
            MacroHeader(macro = macro, macroIndex = macroIdx, isSimple = temporalInsight.isSimple)

            macro.blocks.forEachIndexed { blockIdx, block ->
                val blockKey = "$macroIdx-$blockIdx"
                val isExpanded = blockKey in expandedBlocks

                BlockNode(
                    block = block,
                    macroIndex = macroIdx,
                    blockIndex = blockIdx,
                    isExpanded = isExpanded,
                    onToggle = {
                        expandedBlocks = if (isExpanded) expandedBlocks - blockKey else expandedBlocks + blockKey
                    },
                    onEditBlock = {
                        editingBlock = EditingItem(
                            type = EditType.EDIT,
                            macroIndex = macroIdx,
                            blockIndex = blockIdx,
                            data = block,
                        )
                    },
                    onDeleteBlock = { pendingDelete = DeleteTarget.Block(macroIdx, blockIdx) },
                    onAddWeek = {
                        val weekName = "Semana ${program.countWeeksBeforeAppendingToBlock(macroIdx, blockIdx) + 1}"
                        onUpdateProgram(program.addWeekToBlock(macroIdx, blockIdx, weekName).normalizedTemporalStructure())
                    },
                    onSelectWeek = { weekId -> onFocusWeek(block.id, weekId) },
                    onEditWeek = { mesoIdx, weekIdx, week ->
                        editingExistingWeek = EditingWeekTarget(macroIdx, blockIdx, mesoIdx, weekIdx, week)
                    },
                )
            }

            OutlinedButton(
                onClick = {
                    if (temporalInsight.isSimple) pendingSimpleToAdvanced = true
                    else editingBlock = EditingItem(type = EditType.ADD, macroIndex = macroIdx)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (temporalInsight.isSimple) "Agregar bloque y convertir a avanzado" else "Agregar bloque")
            }
        }

        Spacer(Modifier.height(120.dp))
    }

    editingBlock?.let { item ->
        BlockEditDialog(
            block = item.data as? Block,
            onSave = { name ->
                val updated = if (item.type == EditType.ADD) {
                    program.ensureMacrocycle().addBlockToMacro(item.macroIndex ?: 0, name)
                } else {
                    program.renameBlock(item.macroIndex ?: 0, item.blockIndex ?: 0, name)
                }
                onUpdateProgram(updated.normalizedTemporalStructure())
                editingBlock = null
            },
            onDismiss = { editingBlock = null },
        )
    }

    editingWeek?.let { item ->
        WeekEditDialog(
            onSave = { name ->
                val updated = program.addWeekToBlock(item.macroIndex ?: 0, item.blockIndex ?: 0, name)
                onUpdateProgram(updated.normalizedTemporalStructure())
                editingWeek = null
            },
            onDismiss = { editingWeek = null },
        )
    }

    editingExistingWeek?.let { target ->
        WeekMetadataDialog(
            week = target.week,
            canDelete = program.totalProgramWeeks > 1,
            onSave = { name, description ->
                val updated = program.updateWeekAt(
                    macroIndex = target.macroIndex,
                    blockIndex = target.blockIndex,
                    mesoIndex = target.mesoIndex,
                    weekIndex = target.weekIndex,
                ) { week ->
                    week.copy(
                        name = name.trim().ifBlank { week.name },
                        description = description?.trim()?.takeIf { it.isNotBlank() },
                    )
                }
                onUpdateProgram(updated.normalizedTemporalStructure())
                editingExistingWeek = null
            },
            onDelete = {
                pendingDelete = DeleteTarget.Week(target.macroIndex, target.blockIndex, target.mesoIndex, target.weekIndex)
                editingExistingWeek = null
            },
            onDismiss = { editingExistingWeek = null },
        )
    }

    editingKeyDate?.let { keyDate ->
        KeyDateEditSheet(
            keyDate = keyDate,
            onSave = { updatedKeyDate ->
                val updatedDates = program.keyDates
                    .filterNot { it.id == updatedKeyDate.id }
                    .plus(updatedKeyDate)
                    .sortedBy { it.startDate }
                onUpdateProgram(program.copy(keyDates = updatedDates))
                editingKeyDate = null
                showKeyDatesSheet = true
            },
            onDismiss = { editingKeyDate = null },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    when (target) {
                        is DeleteTarget.Block -> "Eliminar este bloque puede cambiar la lógica temporal del programa."
                        is DeleteTarget.Week -> "Eliminar esta semana quitará sus sesiones."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = when (target) {
                            is DeleteTarget.Block -> program.removeBlock(target.macroIndex, target.blockIndex)
                            is DeleteTarget.Week -> program.removeWeek(target.macroIndex, target.blockIndex, target.mesoIndex, target.weekIndex)
                        }.normalizedTemporalStructure()
                        onUpdateProgram(updated)
                        pendingDelete = null
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }

    if (pendingSimpleToAdvanced) {
        AlertDialog(
            onDismissRequest = { pendingSimpleToAdvanced = false },
            title = { Text("Convertir a programa avanzado", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Agregar un segundo bloque hace que este programa deje de ser simple. " +
                        "La estructura pasa a ser lineal, se terminan los loops y los eventos cíclicos dejan de ser compatibles."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = program
                            .ensureMacrocycle()
                            .addBlockToMacro(0, "Bloque 2")
                            .copy(structure = ProgramStructure.COMPLEX)
                            .normalizedTemporalStructure()
                        onUpdateProgram(updated)
                        pendingSimpleToAdvanced = false
                    },
                ) { Text("Convertir") }
            },
            dismissButton = { TextButton(onClick = { pendingSimpleToAdvanced = false }) { Text("Cancelar") } },
        )
    }

    if (!temporalInsight.isSimple && showKeyDatesSheet && editingKeyDate == null) {
        KeyDatesManagementSheet(
            program = program,
            timelineStartDate = editingTimelineStartDate,
            competitionDate = editingCompetitionDate,
            onTimelineStartDateChange = { editingTimelineStartDate = it },
            onCompetitionDateChange = { editingCompetitionDate = it },
            onSave = {
                val competition = editingCompetitionDate.trim().takeIf { it.isNotBlank() }?.let { date ->
                    val calendarProgram = program.copy(
                        timelineStartDate = editingTimelineStartDate.trim().ifBlank { null },
                        keyDates = program.keyDates.filterNot { it.type == KeyDateType.COMPETITION },
                    )
                    val competitionDay = parseProgramDate(date)
                    val assignedWeek = competitionDay?.let { findProgramWeekRange(calendarProgram, it) }
                    ProgramKeyDate(
                        id = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }?.id ?: "competition_${System.nanoTime()}",
                        title = "Competición",
                        type = KeyDateType.COMPETITION,
                        startDate = assignedWeek?.first?.toString() ?: date,
                        endDate = assignedWeek?.second?.toString(),
                        eventDate = date,
                    )
                }
                onUpdateProgram(
                    program.copy(
                        timelineStartDate = editingTimelineStartDate.trim().ifBlank { null },
                        keyDates = program.keyDates.filterNot { it.type == KeyDateType.COMPETITION } + listOfNotNull(competition),
                    )
                )
                showKeyDatesSheet = false
            },
            onDismiss = { showKeyDatesSheet = false },
        )
    }

    if (showLibrarySheet) {
        TemplatesProtocolsSheet(
            currentProgram = program,
            onApplyTemplate = { template ->
                val updated = template
                    .buildProgramDraft(program)
                    .copy(structureTemplateId = template.id)
                    .normalizedTemporalStructure()
                onUpdateProgram(updated)
                showLibrarySheet = false
            },
            onApplyProtocol = { protocol ->
                val updated = buildProgramFromProtocol(program, protocol).normalizedTemporalStructure()
                onUpdateProgram(updated)
                showLibrarySheet = false
            },
            onDismiss = { showLibrarySheet = false },
        )
    }

    if (temporalInsight.isSimple && showLoopsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLoopsSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Crear/ver loops", fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Los loops viven solo en programas simples y te permiten repetir ciclos con eventos programados.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LoopsView(
                    program = program,
                    onUpdateProgram = onUpdateProgram,
                    onFocusWeek = onFocusWeek,
                )
            }
        }
    }
}

@Composable
private fun MacrocycleToolbar(
    insight: TemporalInsight,
    stats: ProgramStats,
    keyDatesCount: Int,
    hasTimelineStartDate: Boolean,
    showRoadmap: Boolean,
    onToggleRoadmap: () -> Unit,
    onOpenKeyDates: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLoops: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Macrociclo", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    if (insight.isSimple) "Simple · ${insight.cycleWeeks ?: 0} sem/ciclo"
                    else if (hasTimelineStartDate) "Avanzado · $keyDatesCount fechas clave"
                    else "Avanzado · sin calendario",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (insight.isSimple) "Simple" else "Avanzado",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolbarStatChip("Bloques", "${stats.blocks}")
            ToolbarStatChip("Semanas", "${stats.weeks}")
            ToolbarStatChip("Sesiones", "${stats.sessions}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (insight.isSimple) {
                OutlinedButton(onClick = onOpenLoops) { Text("Loops") }
            } else {
                OutlinedButton(onClick = onOpenLibrary) { Text("Plantillas") }
                OutlinedButton(onClick = onOpenKeyDates) { Text("Fechas clave") }
                OutlinedButton(onClick = onToggleRoadmap) { Text(if (showRoadmap) "Ocultar roadmap" else "Roadmap") }
            }
        }
    }
}

@Composable
private fun ToolbarStatChip(label: String, value: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text("$label $value", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun KeyDatesManagementSheet(
    program: Program,
    timelineStartDate: String,
    competitionDate: String,
    onTimelineStartDateChange: (String) -> Unit,
    onCompetitionDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = remember(program, timelineStartDate, competitionDate) {
        buildCalendarPreview(program, timelineStartDate, competitionDate)
    }
    val canSave = timelineStartDate.isBlank() || parseProgramDate(timelineStartDate) != null
    val canSaveCompetition = competitionDate.isBlank() || parseProgramDate(competitionDate) != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Calendario del programa", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                "Elige fechas con el selector nativo de Android. La competición se asigna a la semana completa que contiene ese día para que puedas programarla en la vista de Semana.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NativeDateField(
                label = "Inicio estimado",
                value = timelineStartDate,
                emptyLabel = "Seleccionar inicio",
                onValueChange = onTimelineStartDateChange,
            )
            NativeDateField(
                label = "Día de competición",
                value = competitionDate,
                emptyLabel = "Seleccionar competición",
                onValueChange = onCompetitionDateChange,
            )

            CalendarPreviewCard(preview = preview)

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave && canSaveCompetition,
            ) { Text("Guardar calendario") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NativeDateField(
    label: String,
    value: String,
    emptyLabel: String,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val parsed = parseProgramDate(value)
    val initialDate = parsed ?: LocalDate.now()

    fun showPicker() {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onValueChange(LocalDate.of(year, month + 1, dayOfMonth).toString())
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth,
        ).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = parsed?.let { formatFullDate(it) } ?: "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                placeholder = { Text(emptyLabel) },
                readOnly = true,
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showPicker() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showPicker() }) { Text("Abrir calendario") }
            if (parsed != null) {
                TextButton(onClick = { onValueChange("") }) { Text("Limpiar") }
            }
        }
    }
}

@Composable
private fun CalendarPreviewCard(preview: CalendarPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Fechas estimadas", fontWeight = FontWeight.Black, fontSize = 13.sp)
            when {
                preview.startDate == null -> Text(
                    "Selecciona un inicio estimado para calcular cuándo comienza cada bloque.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.blockStarts.isEmpty() -> Text(
                    "El programa aún no tiene semanas suficientes para proyectar bloques.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    preview.programEnd?.let { end ->
                        CalendarPreviewLine("Duración estimada", "${formatFullDate(preview.startDate!!)} → ${formatFullDate(end)}")
                    }
                    preview.daysUntilCompetition?.let { days ->
                        CalendarPreviewLine("Falta para competir", formatDaysUntil(days))
                    }
                    preview.competitionWeekRange?.let { range ->
                        CalendarPreviewLine("Semana asignada", "${formatFullDate(range.first)} → ${formatFullDate(range.second)}")
                    }
                    preview.blockStarts.take(5).forEach { block ->
                        CalendarPreviewLine(
                            block.blockName,
                            "inicia ${formatFullDate(block.startDate)} · ${block.weeks} sem",
                        )
                    }
                }
            }
            preview.messages.forEach { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF59E0B),
                )
            }
        }
    }
}

@Composable
private fun CalendarPreviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
    }
}

@Composable
private fun AdvancedRoadmapCard(
    roadmap: AdvancedRoadmap,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Roadmap visual", fontWeight = FontWeight.Black, fontSize = 16.sp)
            if (roadmap.startDate == null) {
                Text(
                    "Guarda primero la fecha de inicio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val segments = roadmap.blockSegments()
                val totalWeeks = roadmap.weekSlots.size.coerceAtLeast(1)
                val programEnd = roadmap.weekSlots.lastOrNull()?.weekEnd
                val competitionSlot = roadmap.competitionSlot()
                val competitionMark = competitionSlot?.marks?.firstOrNull { it.type == KeyDateType.COMPETITION }
                val competition = competitionMark?.eventDate ?: roadmap.competitionDate
                Text(
                    "${formatFullDate(roadmap.startDate)} → ${programEnd?.let { formatFullDate(it) } ?: formatFullDate(roadmap.startDate)} · $totalWeeks semanas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Guía: los bloques muestran el plan completo y los puntos son semanas reales. La competición se reserva como semana completa para que puedas entrar y programar el día exacto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        segments.forEachIndexed { index, segment ->
                            Box(
                                modifier = Modifier
                                    .weight(segment.weeks.toFloat().coerceAtLeast(1f))
                                    .height(26.dp)
                                    .background(roadmapSegmentColor(index), RoundedCornerShape(999.dp))
                                    .clickable { onFocusWeek(segment.firstBlockId, segment.firstWeekId) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(segment.blockName, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(roadmap.weekSlots, key = { _, slot -> slot.weekId }) { index, slot ->
                            RoadmapWeekDot(
                                index = index + 1,
                                slot = slot,
                                onClick = { onFocusWeek(slot.blockId, slot.weekId) },
                            )
                        }
                    }
                }

                if (competitionSlot != null && competitionMark != null) {
                    CompetitionWeekCard(
                        weekIndex = roadmap.weekSlots.indexOf(competitionSlot) + 1,
                        slot = competitionSlot,
                        mark = competitionMark,
                        onFocusWeek = { onFocusWeek(competitionSlot.blockId, competitionSlot.weekId) },
                        onCreateCompetitionSession = {
                            val preferredDay = (competitionMark.eventDate ?: competitionSlot.weekStart).dayOfWeek.value
                            onCreateSessionForWeek(competitionSlot.weekId, preferredDay)
                        },
                    )
                } else if (competition != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.13f)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.42f)),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Competición fuera del programa", fontWeight = FontWeight.Black, color = Color(0xFFF59E0B))
                            Text(
                                "${formatFullDate(competition)} no cae dentro de las $totalWeeks semanas proyectadas. Ajusta el inicio estimado, añade semanas o cambia la fecha para que KPKN pueda reservar la semana completa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                roadmap.weekSlots.filter { slot -> slot.marks.any { it.type != KeyDateType.COMPETITION } }.take(2).forEach { slot ->
                    val mark = slot.marks.first { it.type != KeyDateType.COMPETITION }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KeyDateTypeBadge(mark.type)
                            Column(Modifier.weight(1f)) {
                                Text(mark.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Semana ${slot.weekName} · ${slot.dateRangeLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadmapWeekDot(
    index: Int,
    slot: AdvancedWeekSlot,
    onClick: () -> Unit,
) {
    val competitionMark = slot.marks.firstOrNull { it.type == KeyDateType.COMPETITION }
    val hasKeyDate = slot.marks.isNotEmpty()
    val accent = if (competitionMark != null) Color(0xFFF59E0B) else MaterialTheme.colorScheme.tertiary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = if (hasKeyDate) accent else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (hasKeyDate) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (hasKeyDate) BorderStroke(2.dp, accent.copy(alpha = 0.9f)) else null,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("S$index", fontSize = 10.sp, fontWeight = FontWeight.Black)
                if (competitionMark != null) Text("Comp", fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            slot.weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL"))),
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompetitionWeekCard(
    weekIndex: Int,
    slot: AdvancedWeekSlot,
    mark: KeyDateMark,
    onFocusWeek: () -> Unit,
    onCreateCompetitionSession: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF59E0B), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("Comp", fontWeight = FontWeight.Black, fontSize = 10.sp, color = Color.Black)
                }
                Column(Modifier.weight(1f)) {
                    Text("Semana de competición · S$weekIndex", fontWeight = FontWeight.Black)
                    Text(slot.blockName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Día clave: ${formatFullDate(mark.eventDate ?: slot.weekStart)}. Semana reservada: ${formatFullDate(slot.weekStart)} → ${formatFullDate(slot.weekEnd)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFocusWeek, modifier = Modifier.weight(1f)) { Text("Ver semana") }
                OutlinedButton(onClick = onCreateCompetitionSession, modifier = Modifier.weight(1f)) { Text("Crear día") }
            }
        }
    }
}

@Composable
private fun KeyDateTypeBadge(type: KeyDateType) {
    val text = when (type) {
        KeyDateType.COMPETITION -> "Competición"
        KeyDateType.EXAMS -> "Exámenes"
        KeyDateType.VACATION -> "Vacaciones"
        KeyDateType.TRAVEL -> "Viaje"
        KeyDateType.CUSTOM -> "Personalizada"
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun KeyDateEditSheet(
    keyDate: ProgramKeyDate,
    onSave: (ProgramKeyDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(keyDate.id) { mutableStateOf(keyDate.title) }
    var startDate by remember(keyDate.id) { mutableStateOf(keyDate.startDate) }
    var endDate by remember(keyDate.id) { mutableStateOf(keyDate.endDate ?: "") }
    var notes by remember(keyDate.id) { mutableStateOf(keyDate.notes ?: "") }
    var type by remember(keyDate.id) { mutableStateOf(keyDate.type) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Fecha clave", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("Inicio (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("Fin opcional (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas opcionales") })
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyDateType.entries.forEach { entry ->
                        FilterChip(selected = type == entry, onClick = { type = entry }, label = { Text(entry.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            keyDate.copy(
                                title = title.trim(),
                                type = type,
                                startDate = startDate.trim(),
                                endDate = endDate.trim().ifBlank { null },
                                eventDate = if (type == KeyDateType.COMPETITION) startDate.trim() else keyDate.eventDate,
                                notes = notes.trim().ifBlank { null },
                            )
                        )
                    },
                    enabled = title.isNotBlank() && startDate.isNotBlank(),
                ) { Text("Guardar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesProtocolsSheet(
    currentProgram: Program,
    onApplyTemplate: (ProgramTemplateOption) -> Unit,
    onApplyProtocol: (Protocol) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val advancedTemplates = remember { PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Plantillas / protocolos", fontWeight = FontWeight.Black, fontSize = 20.sp)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Plantillas de programa") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Protocolos de entrenamiento") })
            }
            if (selectedTab == 0) {
                advancedTemplates.forEach { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplyTemplate(template) },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(template.name, fontWeight = FontWeight.Black)
                            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${template.trackLabel ?: "Programa"} · ${template.blockNames.size} bloques · ${template.weeks} semanas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            } else {
                PROTOCOL_LIBRARY.forEach { protocol ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onApplyProtocol(protocol) },
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(protocol.name, fontWeight = FontWeight.Black)
                            Text(protocol.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${protocol.blocks.sumOf { it.weeks }} semanas · ${protocol.blocks.size} bloques · ${protocol.sessionCategories.size} partes por sesión",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (protocol.sessionCategories.isNotEmpty()) {
                                Text(
                                    protocol.sessionCategories.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MacroHeader(
    macro: Macrocycle,
    macroIndex: Int,
    isSimple: Boolean,
) {
    val title = if (isSimple) "Macrociclo base" else macro.name
    Text(
        text = if (macroIndex == 0) title else "${macro.name} · ${macroIndex + 1}",
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun InsightChip(label: String, value: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Black, color = accent)
            Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.86f))
        }
    }
}

@Composable
private fun BlockNode(
    block: Block,
    macroIndex: Int,
    blockIndex: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEditBlock: () -> Unit,
    onDeleteBlock: () -> Unit,
    onAddWeek: () -> Unit,
    onSelectWeek: (String) -> Unit,
    onEditWeek: (Int, Int, ProgramWeek) -> Unit,
) {
    val flatWeeks = block.mesocycles.flatMapIndexed { mesoIndex, meso ->
        meso.weeks.mapIndexed { weekIndex, week -> Triple(mesoIndex, weekIndex, week) }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(block.name, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        "${flatWeeks.size} semana${if (flatWeeks.size != 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEditBlock) { Icon(Icons.Default.Edit, "Editar") }
                IconButton(onClick = onDeleteBlock) { Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFEF4444)) }
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 24.dp, end = 14.dp, bottom = 14.dp)) {
                    WeekPillGrid(
                        weeks = flatWeeks,
                        onSelectWeek = onSelectWeek,
                        onEditWeek = onEditWeek,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onAddWeek, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar semana")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekPillGrid(
    weeks: List<Triple<Int, Int, ProgramWeek>>,
    onSelectWeek: (String) -> Unit,
    onEditWeek: (Int, Int, ProgramWeek) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Semanas del bloque", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                weeks.chunked(3).forEach { rowWeeks ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowWeeks.forEach { (mesoIdx, weekIdx, week) ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (week.isLoopWeek) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(999.dp),
                                    )
                                    .combinedClickable(
                                        onClick = { onSelectWeek(week.id) },
                                        onLongClick = { onEditWeek(mesoIdx, weekIdx, week) },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(if (week.isLoopWeek) "Loop · ${week.name}" else week.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockEditDialog(
    block: Block?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(block?.name ?: "Nuevo bloque") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (block != null) "Editar bloque" else "Nuevo bloque", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun MesoEditDialog(
    meso: Mesocycle?,
    onSave: (String, MesocycleGoal) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(meso?.name ?: "Nuevo mesociclo") }
    var goal by remember { mutableStateOf(meso?.goal ?: MesocycleGoal.ACCUMULATION) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mesociclo", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
                Text("Objetivo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MesocycleGoal.entries.forEach { entry ->
                        FilterChip(
                            selected = goal == entry,
                            onClick = { goal = entry },
                            label = { Text(entry.label, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), goal) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WeekEditDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("Nueva semana") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Semana", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun WeekMetadataDialog(
    week: ProgramWeek,
    canDelete: Boolean,
    onSave: (String, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(week.id) { mutableStateOf(week.name) }
    var description by remember(week.id) { mutableStateOf(week.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar semana", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Toca una semana para verla. Mantener presionado abre esta edición segura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción de la semana") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description) },
                enabled = name.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class ProgramStats(val weeks: Int, val sessions: Int, val mesos: Int, val blocks: Int)

private data class CalendarPreview(
    val startDate: LocalDate?,
    val programEnd: LocalDate?,
    val competitionDate: LocalDate?,
    val competitionWeekRange: Pair<LocalDate, LocalDate>?,
    val daysUntilCompetition: Long?,
    val blockStarts: List<BlockStartPreview>,
    val messages: List<String>,
)

private data class BlockStartPreview(
    val blockName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weeks: Int,
)

private data class TemporalInsight(
    val isSimple: Boolean,
    val cycleWeeks: Int?,
    val loopCadenceCycles: Int?,
    val loopLengthWeeks: Int?,
    val blocks: Int,
    val mesocycles: Int,
    val weeks: Int,
    val hadLoopsOrEvents: Boolean,
)

private enum class EditType { ADD, EDIT }

private data class EditingItem(
    val type: EditType,
    val macroIndex: Int?,
    val blockIndex: Int? = null,
    val mesoIndex: Int? = null,
    val data: Any? = null,
)

private data class EditingWeekTarget(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
    val week: ProgramWeek,
)

private sealed class DeleteTarget {
    data class Block(val macroIndex: Int, val blockIndex: Int) : DeleteTarget()
    data class Week(val macroIndex: Int, val blockIndex: Int, val mesoIndex: Int, val weekIndex: Int) : DeleteTarget()
}

private fun Program.toProgramStats(): ProgramStats {
    val sessions = macrocycles.sumOf { macro ->
        macro.blocks.sumOf { block ->
            block.mesocycles.sumOf { meso -> meso.weeks.sumOf { it.sessions.size } }
        }
    }
    return ProgramStats(
        weeks = totalProgramWeeks,
        sessions = sessions,
        mesos = totalMesocycleCount,
        blocks = totalBlockCount,
    )
}

private fun Program.toTemporalInsight(): TemporalInsight {
    return TemporalInsight(
        isSimple = isSimpleTemporalProgram,
        cycleWeeks = simpleCycleWeeks,
        loopCadenceCycles = primaryLoopCadenceCycles,
        loopLengthWeeks = primaryLoopLengthWeeks,
        blocks = totalBlockCount,
        mesocycles = totalMesocycleCount,
        weeks = totalProgramWeeks,
        hadLoopsOrEvents = loops.isNotEmpty() || events.isNotEmpty(),
    )
}

private fun Program.ensureMacrocycle(): Program {
    if (macrocycles.isNotEmpty()) return this
    return copy(
        macrocycles = listOf(
            Macrocycle(
                id = "macro_${System.nanoTime()}",
                name = "Macrociclo 1",
                blocks = listOf(defaultBlock("Bloque 1")),
            )
        )
    )
}

private fun defaultBlock(name: String): Block {
    return Block(
        id = "block_${System.nanoTime()}",
        name = name,
        mesocycles = listOf(defaultMesocycle()),
    )
}

private fun defaultMesocycle(
    name: String = "Mesociclo 1",
    goal: MesocycleGoal = MesocycleGoal.ACCUMULATION,
): Mesocycle {
    return Mesocycle(
        id = "meso_${System.nanoTime()}",
        name = name,
        goal = goal,
        weeks = listOf(defaultWeek("Semana 1")),
    )
}

private fun defaultWeek(name: String): ProgramWeek {
    return ProgramWeek(id = "week_${System.nanoTime()}", name = name)
}

private fun Program.addBlockToMacro(macroIndex: Int, blockName: String): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { index, macro ->
            if (index != macroIndex) macro
            else macro.copy(blocks = macro.blocks + defaultBlock(blockName))
        }
    )
}

private fun Program.renameBlock(macroIndex: Int, blockIndex: Int, name: String): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex == blockIndex) block.copy(name = name) else block
                }
            )
        }
    )
}

private fun Program.addMesocycle(macroIndex: Int, blockIndex: Int, name: String, goal: MesocycleGoal): Program {
    val mesocycle = defaultMesocycle(name = name, goal = goal)
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(mesocycles = block.mesocycles + mesocycle)
                }
            )
        }
    )
}

private fun Program.renameMesocycle(
    macroIndex: Int,
    blockIndex: Int,
    mesoIndex: Int,
    name: String,
    goal: MesocycleGoal,
): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(
                        mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                            if (currentMesoIndex == mesoIndex) meso.copy(name = name, goal = goal) else meso
                        }
                    )
                }
            )
        }
    )
}

private fun Program.addWeekToBlock(macroIndex: Int, blockIndex: Int, name: String): Program {
    val week = defaultWeek(name)
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(
                        mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                            if (currentMesoIndex != 0) meso
                            else meso.copy(weeks = meso.weeks + week)
                        }
                    )
                }
            )
        }
    )
}

private fun Program.countWeeksBeforeAppendingToBlock(targetMacroIndex: Int, targetBlockIndex: Int): Int {
    var count = 0
    macrocycles.forEachIndexed { macroIndex, macro ->
        if (macroIndex > targetMacroIndex) return count
        macro.blocks.forEachIndexed { blockIndex, block ->
            if (macroIndex == targetMacroIndex && blockIndex > targetBlockIndex) return count
            count += block.mesocycles.sumOf { it.weeks.size }
        }
    }
    return count
}

private fun Program.removeBlock(macroIndex: Int, blockIndex: Int): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(blocks = macro.blocks.filterIndexed { index, _ -> index != blockIndex })
        }.filter { it.blocks.isNotEmpty() }
    ).ensureMacrocycle()
}

private fun Program.removeMesocycle(macroIndex: Int, blockIndex: Int, mesoIndex: Int): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(mesocycles = block.mesocycles.filterIndexed { index, _ -> index != mesoIndex })
                }
            )
        }
    )
}

private fun Program.removeWeek(macroIndex: Int, blockIndex: Int, mesoIndex: Int, weekIndex: Int): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(
                        mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                            if (currentMesoIndex != mesoIndex) meso
                            else meso.copy(weeks = meso.weeks.filterIndexed { index, _ -> index != weekIndex })
                        }
                    )
                }
            )
        }
    )
}

private fun Program.updateWeekAt(
    macroIndex: Int,
    blockIndex: Int,
    mesoIndex: Int,
    weekIndex: Int,
    update: (ProgramWeek) -> ProgramWeek,
): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(
                        mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                            if (currentMesoIndex != mesoIndex) meso
                            else meso.copy(
                                weeks = meso.weeks.mapIndexed { currentWeekIndex, week ->
                                    if (currentWeekIndex == weekIndex) update(week) else week
                                }
                            )
                        }
                    )
                }
            )
        }
    )
}

private data class AdvancedRoadmap(
    val startDate: LocalDate?,
    val weekSlots: List<AdvancedWeekSlot>,
    val keyDateTracks: List<KeyDateTrack> = emptyList(),
    val competitionDate: LocalDate? = null,
)

private data class RoadmapBlockSegment(
    val blockName: String,
    val firstBlockId: String,
    val firstWeekId: String,
    val weeks: Int,
)

private data class AdvancedWeekSlot(
    val blockId: String,
    val blockName: String,
    val weekId: String,
    val weekName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val marks: List<KeyDateMark>,
) {
    val dateRangeLabel: String
        get() = "${formatFullDate(weekStart)} → ${formatFullDate(weekEnd)}"
}

private data class KeyDateMark(
    val keyDateId: String,
    val title: String,
    val type: KeyDateType,
    val kind: KeyDateMarkKind,
    val eventDate: LocalDate?,
) {
    val isActionable: Boolean
        get() = true

    val ctaLabel: String
        get() = when (type) {
            KeyDateType.COMPETITION -> "Crear sesión clave"
            KeyDateType.EXAMS -> "Crear sesión ligera"
            KeyDateType.VACATION -> "Crear sesión ajuste"
            KeyDateType.TRAVEL -> "Crear sesión viaje"
            KeyDateType.CUSTOM -> "Crear sesión"
        }

    val preferredDayOfWeek: Int
        get() = preferredDayOfWeek(type)
}

private enum class KeyDateMarkKind { SPECIAL_WEEK, SPECIAL_SESSION }
private enum class KeyDateTrackKind { BEFORE, INSIDE, AFTER }

private data class KeyDateTrack(
    val keyDate: ProgramKeyDate,
    val slots: List<KeyDateTrackSlot>,
)

private data class KeyDateTrackSlot(
    val blockId: String,
    val weekId: String,
    val weekName: String,
    val relativeLabel: String,
    val trackKind: KeyDateTrackKind,
)

private fun buildAdvancedRoadmap(program: Program): AdvancedRoadmap {
    if (program.isSimpleTemporalProgram) return AdvancedRoadmap(startDate = null, weekSlots = emptyList())
    val startDate = parseProgramDate(program.timelineStartDate)
    if (startDate == null) return AdvancedRoadmap(startDate = null, weekSlots = emptyList())

    var currentStart: LocalDate = startDate
    val slots = mutableListOf<AdvancedWeekSlot>()

    program.macrocycles.forEach { macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { week ->
                    val weekEnd = currentStart.plusDays(6)
                    val marks = program.keyDates.mapNotNull { keyDate ->
                        buildKeyDateMark(keyDate, currentStart, weekEnd)
                    }
                    slots += AdvancedWeekSlot(
                        blockId = block.id,
                        blockName = block.name,
                        weekId = week.id,
                        weekName = week.name,
                        weekStart = currentStart,
                        weekEnd = weekEnd,
                        marks = marks,
                    )
                    currentStart = currentStart.plusWeeks(1)
                }
            }
        }
    }

    val tracks = program.keyDates.mapNotNull { keyDate ->
        buildKeyDateTrack(keyDate, slots)
    }

    return AdvancedRoadmap(
        startDate = startDate,
        weekSlots = slots,
        keyDateTracks = tracks,
        competitionDate = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }?.let { keyDate ->
            parseProgramDate(keyDate.eventDate) ?: parseProgramDate(keyDate.startDate)
        },
    )
}

private fun AdvancedRoadmap.blockSegments(): List<RoadmapBlockSegment> {
    val segments = mutableListOf<RoadmapBlockSegment>()
    weekSlots.forEach { slot ->
        val last = segments.lastOrNull()
        if (last != null && last.firstBlockId == slot.blockId) {
            segments[segments.lastIndex] = last.copy(weeks = last.weeks + 1)
        } else {
            segments += RoadmapBlockSegment(
                blockName = slot.blockName,
                firstBlockId = slot.blockId,
                firstWeekId = slot.weekId,
                weeks = 1,
            )
        }
    }
    return segments
}

private fun roadmapSegmentColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF7C3AED),
        Color(0xFF059669),
        Color(0xFFEA580C),
        Color(0xFFDC2626),
    )
    return colors[index % colors.size]
}

private fun buildKeyDateMark(
    keyDate: ProgramKeyDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
): KeyDateMark? {
    val eventDate = parseProgramDate(keyDate.eventDate)
    val start = parseProgramDate(keyDate.startDate) ?: eventDate ?: return null
    val end = parseProgramDate(keyDate.endDate) ?: start
    if (end < weekStart || start > weekEnd) return null
    val kind = KeyDateMarkKind.SPECIAL_WEEK
    return KeyDateMark(
        keyDateId = keyDate.id,
        title = keyDate.title,
        type = keyDate.type,
        kind = kind,
        eventDate = eventDate ?: start,
    )
}

private fun parseProgramDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return try {
        LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun buildCalendarPreview(
    program: Program,
    timelineStartDate: String,
    competitionDate: String,
): CalendarPreview {
    val start = parseProgramDate(timelineStartDate)
    val competition = parseProgramDate(competitionDate)
    val blockStarts = mutableListOf<BlockStartPreview>()
    val messages = mutableListOf<String>()
    var programEnd: LocalDate? = null

    if (start != null) {
        var cursor: LocalDate = start
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                val weeks = block.mesocycles.sumOf { it.weeks.size }
                if (weeks > 0) {
                    val blockStart = cursor
                    val blockEnd = cursor.plusWeeks(weeks.toLong()).minusDays(1)
                    blockStarts += BlockStartPreview(
                        blockName = block.name,
                        startDate = blockStart,
                        endDate = blockEnd,
                        weeks = weeks,
                    )
                    cursor = cursor.plusWeeks(weeks.toLong())
                    programEnd = blockEnd
                }
            }
        }
    }

    val projectedProgram = program.copy(timelineStartDate = start?.toString())
    val competitionWeekRange = competition?.let { findProgramWeekRange(projectedProgram, it) }
    val daysUntilCompetition = competition?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    if (competition != null && start == null) {
        messages += "Selecciona inicio estimado para asignar la competición a una semana del programa."
    }
    if (competition != null && start != null && competitionWeekRange == null) {
        messages += "La competición no cae dentro de las semanas actuales del programa. Ajusta fechas o agrega semanas."
    }
    if (competitionWeekRange != null) {
        messages += "La semana completa de competición se marcará como especial en el roadmap y en los puntos superiores."
    }

    return CalendarPreview(
        startDate = start,
        programEnd = programEnd,
        competitionDate = competition,
        competitionWeekRange = competitionWeekRange,
        daysUntilCompetition = daysUntilCompetition,
        blockStarts = blockStarts,
        messages = messages,
    )
}

private fun findProgramWeekRange(program: Program, targetDate: LocalDate): Pair<LocalDate, LocalDate>? {
    var cursor = parseProgramDate(program.timelineStartDate) ?: return null
    program.macrocycles.forEach { macro ->
        macro.blocks.forEach { block ->
            block.mesocycles.forEach { meso ->
                meso.weeks.forEach { _ ->
                    val weekStart = cursor
                    val weekEnd = cursor.plusDays(6)
                    if (targetDate in weekStart..weekEnd) return weekStart to weekEnd
                    cursor = cursor.plusWeeks(1)
                }
            }
        }
    }
    return null
}

private fun AdvancedRoadmap.competitionSlot(): AdvancedWeekSlot? {
    return weekSlots.firstOrNull { slot -> slot.marks.any { it.type == KeyDateType.COMPETITION } }
}

private fun formatFullDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CL")))
}

private fun formatDaysUntil(days: Long): String = when {
    days < 0 -> "hace ${kotlin.math.abs(days)} días"
    days == 0L -> "hoy"
    days == 1L -> "1 día"
    days < 7 -> "$days días"
    else -> {
        val weeks = days / 7
        val rest = days % 7
        if (rest == 0L) "$weeks semanas" else "$weeks semanas y $rest días"
    }
}

private fun buildKeyDateTrack(
    keyDate: ProgramKeyDate,
    slots: List<AdvancedWeekSlot>,
): KeyDateTrack? {
    val start = parseProgramDate(keyDate.startDate) ?: return null
    val end = parseProgramDate(keyDate.endDate) ?: start
    val relevantSlots = slots.filter { slot ->
        val distanceBefore = java.time.temporal.ChronoUnit.DAYS.between(slot.weekEnd, start)
        val distanceAfter = java.time.temporal.ChronoUnit.DAYS.between(end, slot.weekStart)
        slot.weekStart <= end && slot.weekEnd >= start || distanceBefore in 0..13 || distanceAfter in 0..13
    }
    if (relevantSlots.isEmpty()) return null
    return KeyDateTrack(
        keyDate = keyDate,
        slots = relevantSlots.map { slot ->
            val kind = when {
                slot.weekStart <= end && slot.weekEnd >= start -> KeyDateTrackKind.INSIDE
                slot.weekEnd < start -> KeyDateTrackKind.BEFORE
                else -> KeyDateTrackKind.AFTER
            }
            KeyDateTrackSlot(
                blockId = slot.blockId,
                weekId = slot.weekId,
                weekName = slot.weekName,
                relativeLabel = when (kind) {
                    KeyDateTrackKind.INSIDE -> "Objetivo"
                    KeyDateTrackKind.BEFORE -> "-${java.time.temporal.ChronoUnit.WEEKS.between(slot.weekStart, start).coerceAtLeast(0)} sem"
                    KeyDateTrackKind.AFTER -> "+${java.time.temporal.ChronoUnit.WEEKS.between(end, slot.weekStart).coerceAtLeast(0)} sem"
                },
                trackKind = kind,
            )
        }
    )
}

private fun ProgramKeyDate.dateSummary(): String {
    val displayDate = eventDate ?: startDate
    return if (endDate.isNullOrBlank() || endDate == startDate) {
        displayDate
    } else {
        "$displayDate · semana $startDate → $endDate"
    }
}

private fun preferredDayOfWeek(type: KeyDateType): Int {
    return when (type) {
        KeyDateType.COMPETITION -> 6
        KeyDateType.EXAMS -> 1
        KeyDateType.VACATION -> 3
        KeyDateType.TRAVEL -> 2
        KeyDateType.CUSTOM -> 1
    }
}

private fun buildProgramFromProtocol(program: Program, protocol: Protocol): Program {
    val splitPattern = SPLIT_TEMPLATES.firstOrNull { it.id == protocol.defaultSplit }?.pattern.orEmpty()
    val sessionParts = protocol.sessionCategories.ifEmpty {
        listOf("Parte principal", "Suplementario", "Accesorios")
    }

    val blocks = protocol.blocks.map { protocolBlock ->
        val goal = when (protocolBlock.goal.lowercase()) {
            "acumulación" -> MesocycleGoal.ACCUMULATION
            "intensificación" -> MesocycleGoal.INTENSIFICATION
            "realización" -> MesocycleGoal.REALIZATION
            "descarga" -> MesocycleGoal.DELOAD
            else -> MesocycleGoal.CUSTOM
        }
        Block(
            id = "block_${System.nanoTime()}_${protocolBlock.name}",
            name = protocolBlock.name,
            mesocycles = listOf(
                Mesocycle(
                    id = "meso_${System.nanoTime()}_${protocolBlock.name}",
                    name = protocolBlock.name,
                    goal = goal,
                    weeks = (1..protocolBlock.weeks).map { weekNumber ->
                        ProgramWeek(
                            id = "week_${System.nanoTime()}_${weekNumber}",
                            name = "Semana $weekNumber",
                            sessions = buildProtocolSessions(splitPattern, sessionParts, protocol),
                        )
                    },
                )
            ),
        )
    }

    return program.copy(
        structure = ProgramStructure.COMPLEX,
        structureTemplateId = protocol.id,
        macrocycles = listOf(
            Macrocycle(
                id = "macro_${System.nanoTime()}",
                name = protocol.name,
                blocks = blocks,
            )
        ),
    )
}

private fun buildProtocolSessions(
    splitPattern: List<String>,
    parts: List<String>,
    protocol: Protocol,
): List<Session> {
    val effectivePattern = splitPattern
        .mapIndexedNotNull { index, label ->
            if (label.equals("Descanso", ignoreCase = true)) null else (index + 1) to label
        }
        .ifEmpty { listOf(1 to protocol.name) }

    return effectivePattern.mapIndexed { sessionIndex, day ->
        Session(
            id = "session_${System.nanoTime()}_${sessionIndex}",
            name = day.second,
            dayOfWeek = day.first,
            assignedDays = listOf(day.first),
            parts = parts.mapIndexed { partIndex, partName ->
                SessionPart(
                    id = "part_${System.nanoTime()}_${partIndex}",
                    name = partName,
                )
            },
        )
    }
}
