package com.example.kpkn.screens.programdetail.components
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.SimpleProgramSnapshot
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.models.buildSimpleCalendarWeeks
import com.example.kpkn.data.models.nextSimpleCalendarStart
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.domain.training.ProgramTemplateEngine
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.primaryLoopCadenceCycles
import com.example.kpkn.data.models.primaryLoopLengthWeeks
import com.example.kpkn.data.models.simpleCycleWeeks
import com.example.kpkn.data.models.suggestCalendarTrainingDays
import com.example.kpkn.data.models.totalBlockCount
import com.example.kpkn.data.models.totalMesocycleCount
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.buildProgramDraft
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.domain.training.ProgramProtocolEngine
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramEndDateStatus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.example.kpkn.ui.components.KpknAlertDialog

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditor(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onAddProgramCopy: (Program) -> Unit = {},
    onCompetitionKeyDateSaved: (updatedProgram: Program, keyDate: ProgramKeyDate) -> Unit = { _, _ -> },
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int, keyDateId: String?) -> Unit = { _, _, _ -> },
    showSimpleCalendarizationSheet: Boolean = false,
    onShowSimpleCalendarizationSheetChange: (Boolean) -> Unit = {},
    calendarizationStartDate: String = "",
    onCalendarizationStartDateChange: (String) -> Unit = {},
    calendarizationEndDate: String = "",
    onCalendarizationEndDateChange: (String) -> Unit = {},
    calendarizationStartDayOfWeek: Int = 1,
    onCalendarizationStartDayOfWeekChange: (Int) -> Unit = {},
    calendarizationTrainingDays: Set<Int> = emptySet(),
    onCalendarizationTrainingDaysChange: (Set<Int>) -> Unit = {},
    onApplySimpleCalendarizedBreak: () -> Unit = {},
    onCalendarizeSimpleCycle: () -> Unit = {},
    onRecoverCyclicProgram: () -> Unit = {},
    onStartFreshCyclicProgram: () -> Unit = {},
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
    var editingManualEndDate by remember(program.calendarization?.manualEndDate) { mutableStateOf(program.calendarization?.manualEndDate ?: "") }
    var editingCompetitionDate by remember(program.keyDates) {
        val competition = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }
        mutableStateOf((competition?.eventDate ?: competition?.startDate).orEmpty())
    }
    var showKeyDatesSheet by remember { mutableStateOf(false) }
    var showAdvancedRoadmap by remember { mutableStateOf(false) }
    var showLibrarySheet by remember { mutableStateOf(false) }
    var showLoopsSheet by remember { mutableStateOf(false) }
    var editingMeso by remember { mutableStateOf<EditingMesoTarget?>(null) }
    var pendingTemplate by remember { mutableStateOf<ProgramTemplateOption?>(null) }
    var pendingProtocol by remember { mutableStateOf<Protocol?>(null) }
    var pendingCompetitionKeyDateDelete by remember { mutableStateOf<String?>(null) }

    fun applyAdvancedCalendarSave() {
        val result = ProgramKeyDateEngine.applyAdvancedCalendarSave(
            program = program,
            timelineStartDate = editingTimelineStartDate,
            competitionDate = editingCompetitionDate,
            manualEndDate = editingManualEndDate,
            competitionRepository = CompetitionRepository.getInstance(),
        )
        onUpdateProgram(result.program)
        result.competitionKeyDate?.let { onCompetitionKeyDateSaved(result.program, it) }
        showKeyDatesSheet = false
    }

    fun removeCompetitionKeyDate(keyDateId: String, mode: ProgramKeyDateEngine.KeyDateDeleteMode) {
        val updated = ProgramKeyDateEngine.deleteKeyDate(
            program = program,
            keyDateId = keyDateId,
            mode = mode,
            competitionRepository = CompetitionRepository.getInstance(),
        )
        val result = ProgramKeyDateEngine.applyAdvancedCalendarSave(
            program = updated,
            timelineStartDate = editingTimelineStartDate,
            competitionDate = "",
            manualEndDate = editingManualEndDate,
            competitionRepository = CompetitionRepository.getInstance(),
        )
        onUpdateProgram(result.program)
        editingCompetitionDate = ""
        showKeyDatesSheet = false
        editingKeyDate = null
    }

    val temporalInsight = remember(program) { program.toTemporalInsight() }
    val stats = remember(program) { program.toProgramStats() }
    val advancedRoadmap = remember(program) { buildAdvancedRoadmap(program) }
    val simpleCalendarizationHazeState = remember { HazeState() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = simpleCalendarizationHazeState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacrocycleToolbar(
            insight = temporalInsight,
            stats = stats,
            keyDatesCount = program.keyDates.size,
            hasTimelineStartDate = !program.timelineStartDate.isNullOrBlank(),
            showRoadmap = showAdvancedRoadmap,
            isSimpleCalendarized = program.simpleProgramKind == SimpleProgramKind.CALENDARIZED,
            onToggleRoadmap = { showAdvancedRoadmap = !showAdvancedRoadmap },
            onOpenKeyDates = { showKeyDatesSheet = true },
            onOpenLibrary = { showLibrarySheet = true },
            onOpenLoops = { showLoopsSheet = true },
            onOpenSimpleCalendarization = { onShowSimpleCalendarizationSheetChange(true) },
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
                        onUpdateProgram(program.addWeekToBlock(macroIdx, blockIdx, weekName).alignTemporalMetadata())
                    },
                    onSelectWeek = { weekId -> onFocusWeek(block.id, weekId) },
                    onEditWeek = { mesoIdx, weekIdx, week ->
                        editingExistingWeek = EditingWeekTarget(macroIdx, blockIdx, mesoIdx, weekIdx, week)
                    },
                    onAddMesocycle = {
                        editingMeso = EditingMesoTarget(macroIdx, blockIdx, null, null, isAdd = true)
                    },
                    onEditMesocycle = { mesoIdx, meso ->
                        editingMeso = EditingMesoTarget(macroIdx, blockIdx, mesoIdx, meso, isAdd = false)
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
                onUpdateProgram(updated.alignTemporalMetadata())
                editingBlock = null
            },
            onDismiss = { editingBlock = null },
        )
    }

    editingWeek?.let { item ->
        WeekEditDialog(
            onSave = { name ->
                val updated = program.addWeekToBlock(item.macroIndex ?: 0, item.blockIndex ?: 0, name)
                onUpdateProgram(updated.alignTemporalMetadata())
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
                onUpdateProgram(updated.alignTemporalMetadata())
                editingExistingWeek = null
            },
            onDelete = {
                pendingDelete = DeleteTarget.Week(target.macroIndex, target.blockIndex, target.mesoIndex, target.weekIndex)
                editingExistingWeek = null
            },
            onDismiss = { editingExistingWeek = null },
        )
    }

    editingMeso?.let { target ->
        val mesoCount = program.macrocycles
            .getOrNull(target.macroIndex)
            ?.blocks?.getOrNull(target.blockIndex)
            ?.mesocycles?.size ?: 0
        MesoEditDialog(
            meso = if (target.isAdd) null else target.meso,
            canDelete = !target.isAdd && mesoCount > 1,
            onSave = { name, goal ->
                val updated = if (target.isAdd) {
                    program.addMesocycle(target.macroIndex, target.blockIndex, name, goal)
                } else {
                    program.renameMesocycle(
                        target.macroIndex,
                        target.blockIndex,
                        target.mesoIndex ?: 0,
                        name,
                        goal,
                    )
                }.alignTemporalMetadata()
                onUpdateProgram(updated)
                editingMeso = null
            },
            onDelete = {
                pendingDelete = DeleteTarget.Meso(
                    target.macroIndex,
                    target.blockIndex,
                    target.mesoIndex ?: 0,
                )
                editingMeso = null
            },
            onDismiss = { editingMeso = null },
        )
    }

    editingKeyDate?.let { keyDate ->
        KeyDateEditSheet(
            keyDate = keyDate,
            onSave = { updatedKeyDate ->
                var updated = ProgramKeyDateEngine.upsertKeyDate(program, updatedKeyDate)
                if (updatedKeyDate.type == KeyDateType.COMPETITION) {
                    updated = ProgramKeyDateEngine.syncCompetitionLinkedEntities(
                        program = updated,
                        keyDate = updatedKeyDate,
                        competitionRepository = CompetitionRepository.getInstance(),
                    )
                }
                onUpdateProgram(updated)
                editingKeyDate = null
                showKeyDatesSheet = true
            },
            onDelete = {
                if (keyDate.type == KeyDateType.COMPETITION &&
                    ProgramKeyDateEngine.hasLinkedCompetitionEntities(
                        program,
                        keyDate.id,
                        CompetitionRepository.getInstance(),
                    )
                ) {
                    pendingCompetitionKeyDateDelete = keyDate.id
                    editingKeyDate = null
                } else {
                    val updated = ProgramKeyDateEngine.deleteKeyDate(
                        program = program,
                        keyDateId = keyDate.id,
                        competitionRepository = CompetitionRepository.getInstance(),
                    )
                    onUpdateProgram(updated)
                    if (keyDate.type == KeyDateType.COMPETITION) {
                        editingCompetitionDate = ""
                    }
                    editingKeyDate = null
                    showKeyDatesSheet = true
                }
            },
            onDismiss = { editingKeyDate = null },
        )
    }

    pendingDelete?.let { target ->
        KpknAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    when (target) {
                        is DeleteTarget.Block -> "Eliminar este bloque puede cambiar la lógica temporal del programa."
                        is DeleteTarget.Meso -> "Eliminar este mesociclo quitará sus semanas y sesiones."
                        is DeleteTarget.Week -> "Eliminar esta semana quitará sus sesiones."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = when (target) {
                            is DeleteTarget.Block -> program.removeBlock(target.macroIndex, target.blockIndex)
                            is DeleteTarget.Meso -> program.removeMesocycle(target.macroIndex, target.blockIndex, target.mesoIndex)
                            is DeleteTarget.Week -> program.removeWeek(target.macroIndex, target.blockIndex, target.mesoIndex, target.weekIndex)
                        }.alignTemporalMetadata()
                        onUpdateProgram(updated)
                        pendingDelete = null
                    },
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }

    pendingCompetitionKeyDateDelete?.let { keyDateId ->
        val linkedSessions = ProgramKeyDateEngine.linkedCompetitionSessionCount(program, keyDateId)
        KpknAlertDialog(
            onDismissRequest = { pendingCompetitionKeyDateDelete = null },
            title = { Text("Eliminar competición", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (linkedSessions > 0) {
                            "Hay $linkedSessions sesión(es) vinculada(s). Elige si desvincularlas (quedan como sesiones normales) o archivarlas junto con su registro de competición."
                        } else {
                            "Se eliminará la fecha clave de competición del calendario del programa."
                        },
                    )
                    if (linkedSessions > 0) {
                        OutlinedButton(
                            onClick = {
                                removeCompetitionKeyDate(
                                    keyDateId,
                                    ProgramKeyDateEngine.KeyDateDeleteMode.ARCHIVE_SESSION_AND_RECORD,
                                )
                                pendingCompetitionKeyDateDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Archivar sesión y registro")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        removeCompetitionKeyDate(
                            keyDateId,
                            ProgramKeyDateEngine.KeyDateDeleteMode.UNLINK_SESSION,
                        )
                        pendingCompetitionKeyDateDelete = null
                    },
                ) {
                    Text(if (linkedSessions > 0) "Desvincular sesiones" else "Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCompetitionKeyDateDelete = null }) { Text("Cancelar") }
            },
        )
    }

    if (pendingSimpleToAdvanced) {
        KpknAlertDialog(
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
                            .alignTemporalMetadata()
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
            manualEndDate = editingManualEndDate,
            onTimelineStartDateChange = { editingTimelineStartDate = it },
            onCompetitionDateChange = { editingCompetitionDate = it },
            onManualEndDateChange = { editingManualEndDate = it },
            otherKeyDates = program.keyDates.filter { it.type != KeyDateType.COMPETITION },
            onAddOtherKeyDate = {
                editingKeyDate = ProgramKeyDate(
                    id = "key_${System.nanoTime()}",
                    title = "",
                    type = KeyDateType.EXAMS,
                    startDate = LocalDate.now().toString(),
                )
                showKeyDatesSheet = false
            },
            onEditOtherKeyDate = { keyDate ->
                editingKeyDate = keyDate
                showKeyDatesSheet = false
            },
            onDeleteOtherKeyDate = { keyDateId ->
                val keyDate = program.keyDates.firstOrNull { it.id == keyDateId }
                if (keyDate?.type == KeyDateType.COMPETITION &&
                    ProgramKeyDateEngine.hasLinkedCompetitionEntities(
                        program,
                        keyDateId,
                        CompetitionRepository.getInstance(),
                    )
                ) {
                    pendingCompetitionKeyDateDelete = keyDateId
                } else {
                    val updated = ProgramKeyDateEngine.deleteKeyDate(
                        program = program,
                        keyDateId = keyDateId,
                        mode = ProgramKeyDateEngine.KeyDateDeleteMode.UNLINK_SESSION,
                        competitionRepository = CompetitionRepository.getInstance(),
                    )
                    onUpdateProgram(updated)
                }
            },
            onSave = {
                val existingCompetition = ProgramKeyDateEngine.competitionKeyDate(program)
                if (existingCompetition != null && editingCompetitionDate.isBlank()) {
                    if (ProgramKeyDateEngine.hasLinkedCompetitionEntities(
                            program,
                            existingCompetition.id,
                            CompetitionRepository.getInstance(),
                        )
                    ) {
                        pendingCompetitionKeyDateDelete = existingCompetition.id
                    } else {
                        removeCompetitionKeyDate(
                            existingCompetition.id,
                            ProgramKeyDateEngine.KeyDateDeleteMode.UNLINK_SESSION,
                        )
                    }
                } else {
                    applyAdvancedCalendarSave()
                }
            },
            onDismiss = { showKeyDatesSheet = false },
        )
    }

    if (showLibrarySheet) {
        TemplatesProtocolsSheet(
            currentProgram = program,
            onApplyTemplate = { template ->
                pendingTemplate = template
            },
            onApplyProtocol = { protocol ->
                pendingProtocol = protocol
            },
            onDismiss = { showLibrarySheet = false },
        )
    }

    pendingProtocol?.let { protocol ->
        KpknAlertDialog(
            onDismissRequest = { pendingProtocol = null },
            title = { Text(protocol.name, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(protocol.description)
                    Text(
                        "${protocol.blocks.size} bloques · ${protocol.blocks.sumOf { it.weeks }} semanas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (ProgramTemplateEngine.hasSessionContent(program)) {
                        Text(
                            "Este programa ya tiene sesiones. Se creará una copia borrador para no perder el original.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val base = if (ProgramTemplateEngine.hasSessionContent(program)) {
                        program.copy(
                            id = "${program.id}_protocol_${System.nanoTime()}",
                            name = "${program.name} · ${protocol.name}",
                            isDraft = true,
                        )
                    } else {
                        program
                    }
                    val updated = ProgramProtocolEngine.applyProtocol(base, protocol)
                    if (base.id != program.id) onAddProgramCopy(updated) else onUpdateProgram(updated)
                    pendingProtocol = null
                    showLibrarySheet = false
                }) { Text("Aplicar protocolo") }
            },
            dismissButton = { TextButton(onClick = { pendingProtocol = null }) { Text("Cancelar") } },
        )
    }

    pendingTemplate?.let { template ->
        KpknAlertDialog(
            onDismissRequest = { pendingTemplate = null },
            title = { Text(template.name, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(template.description)
                    Text(
                        "${template.trackLabel ?: "Programa"} · ${template.blockNames.size} bloques · ${template.weeks} semanas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (ProgramTemplateEngine.hasSessionContent(program)) {
                        Text(
                            "Este programa ya tiene sesiones. Se creará una copia borrador para no perder el original.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val result = ProgramTemplateEngine.applyTemplate(program, template)
                    if (result.createdCopy) {
                        onAddProgramCopy(result.program)
                    } else {
                        onUpdateProgram(result.program)
                    }
                    pendingTemplate = null
                    showLibrarySheet = false
                }) { Text("Aplicar plantilla") }
            },
            dismissButton = { TextButton(onClick = { pendingTemplate = null }) { Text("Cancelar") } },
        )
    }

    if (temporalInsight.isSimple && program.simpleProgramKind == SimpleProgramKind.CYCLIC && showLoopsSheet) {
        KpknSheet(
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

    if (temporalInsight.isSimple && showSimpleCalendarizationSheet) {
        SimpleCalendarizationSheet(
            program = program,
            onDismiss = { onShowSimpleCalendarizationSheetChange(false) },
            startDate = calendarizationStartDate,
            onStartDateChange = onCalendarizationStartDateChange,
            endDate = calendarizationEndDate,
            onEndDateChange = onCalendarizationEndDateChange,
            startDayOfWeek = calendarizationStartDayOfWeek,
            onStartDayOfWeekChange = onCalendarizationStartDayOfWeekChange,
            trainingDays = calendarizationTrainingDays,
            onTrainingDaysChange = onCalendarizationTrainingDaysChange,
            onStartBreak = onApplySimpleCalendarizedBreak,
            onCalendarizeCycle = onCalendarizeSimpleCycle,
            onRecoverCycle = onRecoverCyclicProgram,
            onStartFreshCycle = onStartFreshCyclicProgram,
            hazeState = simpleCalendarizationHazeState,
        )
    }
}

@Composable
private fun MacrocycleToolbar(
    insight: TemporalInsight,
    stats: ProgramStats,
    keyDatesCount: Int,
    hasTimelineStartDate: Boolean,
    showRoadmap: Boolean,
    isSimpleCalendarized: Boolean,
    onToggleRoadmap: () -> Unit,
    onOpenKeyDates: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLoops: () -> Unit,
    onOpenSimpleCalendarization: () -> Unit,
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
                    if (insight.isSimple && isSimpleCalendarized) "Programa Simple Calendarizado · ${insight.weeks} semanas"
                    else if (insight.isSimple) "Programa Simple Cíclico · ${insight.cycleWeeks ?: 0} sem/ciclo"
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
                    if (insight.isSimple && isSimpleCalendarized) "Calendarizado"
                    else if (insight.isSimple) "Cíclico"
                    else "Avanzado",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (insight.isSimple) {
                if (!isSimpleCalendarized) {
                    OutlinedButton(onClick = onOpenLoops) { Text("Loops") }
                }
                OutlinedButton(
                    onClick = onOpenSimpleCalendarization,
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (isSimpleCalendarized) "Calendarización" else "Calendarizar",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
private fun SimpleCalendarizationSheet(
    program: Program,
    onDismiss: () -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    startDayOfWeek: Int,
    onStartDayOfWeekChange: (Int) -> Unit,
    trainingDays: Set<Int>,
    onTrainingDaysChange: (Set<Int>) -> Unit,
    onStartBreak: () -> Unit,
    onCalendarizeCycle: () -> Unit,
    onRecoverCycle: () -> Unit,
    onStartFreshCycle: () -> Unit,
    hazeState: HazeState,
) {
    val isCalendarized = program.simpleProgramKind == SimpleProgramKind.CALENDARIZED &&
        program.calendarization?.mode == ProgramCalendarizationMode.SIMPLE_DATED
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            when (target) {
                SheetValue.Hidden -> false
                SheetValue.PartiallyExpanded -> false
                SheetValue.Expanded -> true
            }
        },
    )
    val parsedStartDate = parseProgramDate(startDate)
    val parsedEndDate = parseProgramDate(endDate)
    val weekCount = if (parsedStartDate != null && parsedEndDate != null) {
        inclusiveCalendarWeekCount(parsedStartDate, parsedEndDate)
    } else {
        0
    }

    KpknSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        hazeState = hazeState,
        dismissible = false,
    ) {
        val sheetPrimary = Color.White
        val sheetSecondary = Color.White.copy(alpha = 0.74f)
        val sheetGlass = Color.White.copy(alpha = 0.11f)
        val sheetGlassStrong = Color.White.copy(alpha = 0.16f)
        if (isCalendarized) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Programa Simple Calendarizado",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = sheetPrimary,
                )
                Text(
                    "Esto sirve cuando tu semana real cambia: turnos rotativos, viajes, exámenes, semanas con pocos días libres o una etapa donde no puedes repetir el ciclo normal. Tu rutina cíclica queda pausada; mientras dura este break, los loops y eventos cíclicos no se aplican.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sheetSecondary,
                    lineHeight = 17.sp,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = sheetGlassStrong),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Break calendarizado activo", fontWeight = FontWeight.Black, color = sheetPrimary)
                        Text(
                            "Cuando termines estas semanas, puedes recuperar la rutina cíclica anterior o empezar una nueva desde cero.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sheetSecondary,
                        )
                    }
                }
                Button(
                    onClick = onRecoverCycle,
                    enabled = program.pausedCyclicSnapshot != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Recuperar ciclo guardado")
                }
                OutlinedButton(onClick = onStartFreshCycle, modifier = Modifier.fillMaxWidth()) {
                    Text("Empezar ciclo desde cero")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cerrar", color = sheetSecondary)
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Pasar a semanas calendarizadas",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = sheetPrimary,
                )
                Text(
                    "Define el rango de fechas, el día en que comienza tu semana y los días de entrenamiento. Si eliges un día distinto al lunes, cada semana irá desde ese día hasta el mismo día de la semana siguiente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sheetSecondary,
                    lineHeight = 17.sp,
                )

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("La semana comienza el:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = sheetPrimary)
                        WeekStartSelector(
                            startDayOfWeek = startDayOfWeek,
                            onStartDayOfWeekChange = onStartDayOfWeekChange,
                        )
                    }

                    NativeDateField(
                        label = "Fecha de inicio",
                        value = startDate,
                        emptyLabel = "Seleccionar inicio",
                        onValueChange = onStartDateChange,
                        firstDayOfWeek = startDayOfWeek,
                    )
                    NativeDateField(
                        label = "Fecha de fin",
                        value = endDate,
                        emptyLabel = "Seleccionar fin",
                        onValueChange = onEndDateChange,
                        firstDayOfWeek = startDayOfWeek,
                    )

                    if (weekCount > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = sheetGlassStrong),
                        ) {
                            Text(
                                "$weekCount semanas desde ${parsedStartDate?.let { formatFullDate(it) } ?: "?"} hasta ${parsedEndDate?.let { formatFullDate(it) } ?: "?"}",
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = sheetPrimary,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Días de entrenamiento", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = sheetPrimary)
                        CalendarDayChecklist(
                            selectedDays = trainingDays,
                            onToggleDay = { day ->
                                onTrainingDaysChange(if (day in trainingDays) trainingDays - day else trainingDays + day)
                            },
                            startDayOfWeek = startDayOfWeek,
                            primaryColor = sheetPrimary,
                            secondaryColor = sheetSecondary,
                            rowColor = sheetGlass,
                        )
                    }
                }

                Button(
                    onClick = onCalendarizeCycle,
                    enabled = parsedStartDate != null && trainingDays.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Calendarizar ciclo actual")
                }
                OutlinedButton(
                    onClick = onStartBreak,
                    enabled = parsedStartDate != null && parsedEndDate != null && parsedEndDate.isAfter(parsedStartDate) && trainingDays.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear break calendarizado")
                }
                Text(
                    "Calendarizar ciclo fija fechas reales a tus semanas actuales. El break pausa el ciclo y crea semanas temporales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = sheetSecondary,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cerrar", color = sheetSecondary)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WeekStartSelector(
    startDayOfWeek: Int,
    onStartDayOfWeekChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..7).forEach { day ->
            val isSelected = day == startDayOfWeek
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.10f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onStartDayOfWeekChange(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayLabelShort(day),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.86f),
                )
            }
        }
    }
}

@Composable
private fun CalendarDayChecklist(
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit,
    startDayOfWeek: Int = 1,
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    rowColor: Color = Color.Transparent,
) {
    val rotatedDays = remember(startDayOfWeek) {
        rotatedWeekDays(startDayOfWeek)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rotatedDays.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(rowColor)
                    .clickable { onToggleDay(day) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = day in selectedDays,
                    onCheckedChange = { onToggleDay(day) },
                )
                Column {
                    Text(
                        dayFullLabel(day),
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                    )
                    Text(
                        "Se asignará fecha real en cada semana.",
                        fontSize = 10.sp,
                        color = secondaryColor,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun KeyDatesManagementSheet(
    program: Program,
    timelineStartDate: String,
    competitionDate: String,
    manualEndDate: String,
    otherKeyDates: List<ProgramKeyDate>,
    onTimelineStartDateChange: (String) -> Unit,
    onCompetitionDateChange: (String) -> Unit,
    onManualEndDateChange: (String) -> Unit,
    onAddOtherKeyDate: () -> Unit,
    onEditOtherKeyDate: (ProgramKeyDate) -> Unit,
    onDeleteOtherKeyDate: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = remember(program, timelineStartDate, competitionDate, manualEndDate) {
        buildCalendarPreview(program, timelineStartDate, competitionDate, manualEndDate)
    }
    val canSave = timelineStartDate.isBlank() || parseProgramDate(timelineStartDate) != null
    val canSaveCompetition = competitionDate.isBlank() || parseProgramDate(competitionDate) != null
    val canSaveManualEnd = manualEndDate.isBlank() || parseProgramDate(manualEndDate) != null
    val hasRequiredStart = competitionDate.isBlank() || timelineStartDate.isNotBlank()

    KpknSheet(onDismissRequest = onDismiss) {
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
            NativeDateField(
                label = "Término manual opcional",
                value = manualEndDate,
                emptyLabel = "Usar término proyectado",
                onValueChange = onManualEndDateChange,
            )

            CalendarPreviewCard(preview = preview)

            if (otherKeyDates.isNotEmpty()) {
                Text("Otras fechas clave", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                otherKeyDates.forEach { keyDate ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditOtherKeyDate(keyDate) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(keyDate.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    "${keyDate.startDate}${keyDate.endDate?.let { " → $it" } ?: ""}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            KeyDateTypeBadge(keyDate.type)
                            IconButton(onClick = { onDeleteOtherKeyDate(keyDate.id) }) {
                                Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onAddOtherKeyDate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Agregar fecha clave (exámenes, vacaciones, viaje…)")
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave && canSaveCompetition && canSaveManualEnd && hasRequiredStart,
            ) { Text("Guardar calendario") }
            if (!hasRequiredStart) {
                Text(
                    "Para calendarizar una competición debes definir el inicio estricto del plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
    firstDayOfWeek: Int = 1,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsed = parseProgramDate(value)
    val initialDate = parsed ?: LocalDate.now()

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
                    .clickable { showPicker = true },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showPicker = true }) { Text("Abrir calendario") }
            if (parsed != null) {
                TextButton(onClick = { onValueChange("") }) { Text("Limpiar") }
            }
        }
    }

    if (showPicker) {
        WeekStartDatePickerDialog(
            selectedDate = initialDate,
            firstDayOfWeek = firstDayOfWeek,
            onDateSelected = { selected ->
                onValueChange(selected.toString())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun WeekStartDatePickerDialog(
    selectedDate: LocalDate,
    firstDayOfWeek: Int,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var visibleMonth by remember(selectedDate) { mutableStateOf(selectedDate.withDayOfMonth(1)) }
    val safeFirstDay = firstDayOfWeek.coerceIn(1, 7)
    val rotatedDays = remember(safeFirstDay) { rotatedWeekDays(safeFirstDay) }
    val firstOfMonth = visibleMonth.withDayOfMonth(1)
    val leadingCells = (firstOfMonth.dayOfWeek.value - safeFirstDay + 7) % 7
    val monthDates = (1..visibleMonth.lengthOfMonth()).map { day -> visibleMonth.withDayOfMonth(day) }
    val cells = List(leadingCells) { null } + monthDates
    val rows = cells.chunked(7)
    val monthFormatter = remember {
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-CL"))
    }

    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar fecha", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                    }
                    Text(
                        visibleMonth.format(monthFormatter),
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    rotatedDays.forEach { day ->
                        Text(
                            dayLabelShort(day),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { column ->
                            val date = row.getOrNull(column)
                            val isSelected = date == selectedDate
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(if (date != null) Modifier.clickable { onDateSelected(date) } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date?.dayOfMonth?.toString().orEmpty(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
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
                    preview.manualEndDate?.let { end ->
                        CalendarPreviewLine("Término manual", formatFullDate(end))
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
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int, keyDateId: String?) -> Unit,
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
                            onCreateSessionForWeek(competitionSlot.weekId, preferredDay, competitionMark.keyDateId)
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
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(keyDate.id) { mutableStateOf(keyDate.title) }
    var startDate by remember(keyDate.id) { mutableStateOf(keyDate.startDate) }
    var endDate by remember(keyDate.id) { mutableStateOf(keyDate.endDate ?: "") }
    var notes by remember(keyDate.id) { mutableStateOf(keyDate.notes ?: "") }
    var type by remember(keyDate.id) { mutableStateOf(keyDate.type) }

    KpknSheet(onDismissRequest = onDismiss) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Text("Eliminar", color = Color(0xFFEF4444))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
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
                }
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
    val simpleTemplates = remember { PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.SIMPLE } }
    val advancedTemplates = remember { PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX } }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Plantillas / protocolos", fontWeight = FontWeight.Black, fontSize = 20.sp)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Simple") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Avanzado") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Protocolos") })
            }
            when (selectedTab) {
                0 -> simpleTemplates.forEach { template ->
                    TemplatePreviewCard(template = template, onClick = { onApplyTemplate(template) })
                }
                1 -> advancedTemplates.forEach { template ->
                    TemplatePreviewCard(template = template, onClick = { onApplyTemplate(template) })
                }
                2 -> PROTOCOL_LIBRARY.forEach { protocol ->
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
private fun TemplatePreviewCard(
    template: ProgramTemplateOption,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${template.emoji} ${template.name}", fontWeight = FontWeight.Black)
            Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${template.trackLabel ?: "Programa"} · ${template.blockNames.size} bloques · ${template.weeks} semanas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
    onAddMesocycle: () -> Unit,
    onEditMesocycle: (Int, Mesocycle) -> Unit,
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
                    Text("Mesociclos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    block.mesocycles.forEachIndexed { mesoIdx, meso ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditMesocycle(mesoIdx, meso) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(meso.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(meso.goal.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    OutlinedButton(onClick = onAddMesocycle, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar mesociclo")
                    }
                    Spacer(Modifier.height(8.dp))
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
    KpknAlertDialog(
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
    canDelete: Boolean = false,
    onSave: (String, MesocycleGoal) -> Unit,
    onDelete: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(meso?.name ?: "Nuevo mesociclo") }
    var goal by remember { mutableStateOf(meso?.goal ?: MesocycleGoal.ACCUMULATION) }
    KpknAlertDialog(
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
        confirmButton = {
            Button(onClick = { onSave(name.trim(), goal) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("Eliminar", color = Color(0xFFEF4444))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun WeekEditDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("Nueva semana") }
    KpknAlertDialog(
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

    KpknAlertDialog(
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
    val manualEndDate: LocalDate?,
    val endDateStatus: ProgramEndDateStatus,
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

private data class EditingMesoTarget(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int?,
    val meso: Mesocycle?,
    val isAdd: Boolean,
)

private sealed class DeleteTarget {
    data class Block(val macroIndex: Int, val blockIndex: Int) : DeleteTarget()
    data class Meso(val macroIndex: Int, val blockIndex: Int, val mesoIndex: Int) : DeleteTarget()
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
    val week = if (
        isSimpleTemporalProgram &&
        simpleProgramKind == SimpleProgramKind.CALENDARIZED &&
        calendarization?.mode == ProgramCalendarizationMode.SIMPLE_DATED
    ) {
        buildSimpleCalendarWeeks(
            startDate = nextSimpleCalendarStart(),
            weekCount = 1,
            startDayOfWeek = startDay ?: 1,
            trainingDays = suggestCalendarTrainingDays(),
        ).first()
    } else {
        defaultWeek(name)
    }
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
    val projection = ProgramCalendarEngine.project(
        if (program.calendarization == null && !program.timelineStartDate.isNullOrBlank()) {
            program.copy(calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization())
        } else {
            program
        }
    )
    val startDate = projection.startDate ?: return AdvancedRoadmap(startDate = null, weekSlots = emptyList())
    val slots = projection.weeks.map { week ->
        AdvancedWeekSlot(
            blockId = week.blockId,
            blockName = week.blockName,
            weekId = week.weekId,
            weekName = week.weekName,
            weekStart = week.startDate,
            weekEnd = week.endDate,
            marks = week.keyDates.mapNotNull { keyDate ->
                buildKeyDateMark(keyDate, week.startDate, week.endDate)
            },
        )
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
    manualEndDate: String,
): CalendarPreview {
    val start = parseProgramDate(timelineStartDate)
    val competition = parseProgramDate(competitionDate)
    val manualEnd = parseProgramDate(manualEndDate)
    val blockStarts = mutableListOf<BlockStartPreview>()
    val messages = mutableListOf<String>()

    val projectedProgram = program.copy(
        timelineStartDate = start?.toString(),
        calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization().copy(
            manualEndDate = manualEndDate.trim().ifBlank { null },
        ),
    )
    val projection = ProgramCalendarEngine.project(projectedProgram)
    val programEnd = projection.projectedEndDate

    projection.weeks
        .groupBy { it.blockId }
        .values
        .forEach { weeks ->
            val first = weeks.firstOrNull() ?: return@forEach
            val last = weeks.last()
            blockStarts += BlockStartPreview(
                blockName = first.blockName,
                startDate = first.startDate,
                endDate = last.endDate,
                weeks = weeks.size,
            )
    }

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
    when (projection.endDateStatus) {
        ProgramEndDateStatus.BEFORE_PROJECTED ->
            messages += "El término manual queda antes del término proyectado. Agrega/quita semanas para que calce."
        ProgramEndDateStatus.AFTER_PROJECTED ->
            messages += "El término manual queda después del término proyectado. Se mostrará como objetivo, sin reescalar semanas."
        ProgramEndDateStatus.INVALID_MANUAL ->
            messages += "El término manual no tiene formato válido."
        else -> Unit
    }

    return CalendarPreview(
        startDate = start,
        programEnd = programEnd,
        manualEndDate = manualEnd,
        endDateStatus = projection.endDateStatus,
        competitionDate = competition,
        competitionWeekRange = competitionWeekRange,
        daysUntilCompetition = daysUntilCompetition,
        blockStarts = blockStarts,
        messages = messages,
    )
}

private fun findProgramWeekRange(program: Program, targetDate: LocalDate): Pair<LocalDate, LocalDate>? {
    val projection = ProgramCalendarEngine.project(program)
    if (projection.enabled) {
        return projection.weekForDate(targetDate)?.let { it.startDate to it.endDate }
    }
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

private fun calendarWeekTitle(startDate: LocalDate): String {
    return "Semana: ${startDate.format(DateTimeFormatter.ofPattern("MM/dd", Locale.US))}"
}

private fun dayFullLabel(day: Int): String = when (day) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día"
}

private fun rotatedWeekDays(startDayOfWeek: Int): List<Int> {
    val safeDayIndex = startDayOfWeek.coerceIn(1, 7) - 1
    return (1..7).toList().let { all ->
        all.drop(safeDayIndex) + all.take(safeDayIndex)
    }
}

private fun inclusiveCalendarWeekCount(startDate: LocalDate, endDate: LocalDate): Int {
    val inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0) + 1
    return ((inclusiveDays + 6) / 7).toInt().coerceAtLeast(1)
}

private fun dayLabelShort(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "Día"
}
