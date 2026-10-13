package com.example.kpkn.screens.programdetail.components
/**
 * Macrocycle editor implementation (legacy monolito).
 * Edición de Block.goal / progressionScheme y preview de progresión viven en
 * la hoja de bloque ([ProgramBlockSheet], P3b); esta fachada no duplica esa
 * lógica (plan 2026-08-20 Fase C).
 */
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
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
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.models.buildSimpleCalendarWeeks
import com.example.kpkn.data.models.nextSimpleCalendarStart
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.domain.training.ProgramTemplateEngine
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.convertSimpleCalendarizedToAdvanced
import com.example.kpkn.data.models.validateTemporalStructure
import com.example.kpkn.data.models.primaryLoopCadenceCycles
import com.example.kpkn.data.models.primaryLoopLengthWeeks
import com.example.kpkn.data.models.simpleCycleWeeks
import com.example.kpkn.data.models.suggestCalendarTrainingDays
import com.example.kpkn.data.models.totalBlockCount
import com.example.kpkn.data.models.totalMesocycleCount
import com.example.kpkn.data.models.totalProgramWeeks
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramEndDateStatus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.screens.programdetail.components.editor.CalendarSection
import com.example.kpkn.screens.programdetail.components.editor.KeyDateDialog
import com.example.kpkn.screens.programdetail.components.editor.KeyDatesSection
import com.example.kpkn.screens.programdetail.components.editor.LibrarySection
import com.example.kpkn.screens.programdetail.components.editor.CyclesSection
import com.example.kpkn.screens.programdetail.components.editor.RouteSection
import com.example.kpkn.screens.programdetail.dayLabelShort
import com.example.kpkn.screens.programdetail.formatDaysUntil
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.screens.programdetail.programCurrentWeekId
import com.example.kpkn.screens.programdetail.programTrainingWeekdays
import com.example.kpkn.screens.programdetail.design.programProgressLabel
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import com.example.kpkn.screens.programdetail.design.ProgramWeekEditSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockList
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockUi
import com.example.kpkn.screens.programdetail.design.structure.ProgramCalendarModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramConvertBlocksDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramDeleteDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramHistorySheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramMesoSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramStructureNotice
import com.example.kpkn.screens.programdetail.design.structure.ProgramTemplateConfirmSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramToolsKind
import com.example.kpkn.screens.programdetail.design.structure.ProgramToolsModule
import com.example.kpkn.screens.programdetail.design.structure.programBlockUis
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.ui.design.kpknEntrance

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditorLegacy(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onAddProgramCopy: (Program) -> Unit = {},
    onCompetitionKeyDateSaved: (updatedProgram: Program, keyDate: ProgramKeyDate) -> Unit = { _, _ -> },
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int, keyDateId: String?) -> Unit = { _, _, _ -> },
    onApplyProgramTemplate: (com.example.kpkn.data.programs.ProgramTemplateOption) -> Unit = {},
    onApplyProgramTemplateOverwrite: (com.example.kpkn.data.programs.ProgramTemplateOption, Boolean) -> Unit = { template, overwrite ->
        if (overwrite) onApplyProgramTemplate(template) else onApplyProgramTemplate(template)
    },
    onApplyProtocol: (Protocol, Boolean) -> Unit = { _, _ -> },
    programSnapshots: List<com.example.kpkn.domain.training.ProgramSnapshot> = emptyList(),
    onRestoreProgramSnapshot: (String) -> Unit = {},
    onRefreshProgramSnapshots: () -> Unit = {},
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
    macrocycleRoadmapExpanded: Boolean? = null,
    onMacrocycleRoadmapExpandedChange: (Boolean) -> Unit = {},
    macrocycleKeyDatesSheetOpen: Boolean? = null,
    onMacrocycleKeyDatesSheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLibrarySheetOpen: Boolean? = null,
    onMacrocycleLibrarySheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLoopsSheetOpen: Boolean? = null,
    onMacrocycleLoopsSheetOpenChange: (Boolean) -> Unit = {},
    macrocycleTimelineStartDate: String? = null,
    onMacrocycleTimelineStartDateChange: (String) -> Unit = {},
    macrocycleManualEndDate: String? = null,
    onMacrocycleManualEndDateChange: (String) -> Unit = {},
    macrocycleCompetitionDate: String? = null,
    onMacrocycleCompetitionDateChange: (String) -> Unit = {},
    // Bloques (P3b): la semana elegida y la copiada, y las acciones que el ViewModel ya tiene para bloques y semanas.
    selectedWeekId: String? = null,
    copiedWeekId: String? = null,
    onCopyWeek: (weekId: String) -> Unit = {},
    onPasteWeek: (weekId: String) -> Unit = {},
    onUpdateBlock: (blockId: String, name: String, description: String?, goal: BlockGoal?, progressionScheme: BlockProgressionScheme?) -> Unit =
        { _, _, _, _, _ -> },
    blockProgressionPreview: (blockId: String) -> String? = { null },
    onDeleteBlock: (blockId: String) -> Unit = {},
    onUpdateWeek: (weekId: String, name: String, description: String?) -> Unit = { _, _, _ -> },
    onDeleteWeek: (weekId: String) -> Unit = {},
    onAddSimpleWeek: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Bloques (P3b): la hoja de bloque, los menús de bloque y de semana, y el borrador de la semana que se edita.
    var blockSheet by remember { mutableStateOf<BlockSheetTarget?>(null) }
    var blockMenu by remember { mutableStateOf<AnchoredTarget?>(null) }
    var weekMenu by remember { mutableStateOf<AnchoredTarget?>(null) }
    var weekEdit by remember { mutableStateOf<WeekEditTarget?>(null) }
    var editingKeyDate by remember { mutableStateOf<ProgramKeyDate?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var pendingSimpleToAdvanced by remember { mutableStateOf(false) }
    var localEditingTimelineStartDate by remember(program.timelineStartDate) { mutableStateOf(program.timelineStartDate ?: "") }
    var localEditingManualEndDate by remember(program.calendarization?.manualEndDate) { mutableStateOf(program.calendarization?.manualEndDate ?: "") }
    var localEditingCompetitionDate by remember(program.keyDates) {
        val competition = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }
        mutableStateOf((competition?.eventDate ?: competition?.startDate).orEmpty())
    }
    var localShowKeyDatesSheet by remember { mutableStateOf(false) }
    // La ruta es una hoja que abre la herramienta «Ruta» (P3c): empieza cerrada, nunca se abre sola.
    var localShowRouteSheet by remember(program.id) { mutableStateOf(false) }
    var localShowLibrarySheet by remember { mutableStateOf(false) }
    var localShowLoopsSheet by remember { mutableStateOf(false) }
    var editingMeso by remember { mutableStateOf<EditingMesoTarget?>(null) }
    var pendingTemplate by remember { mutableStateOf<ProgramTemplateOption?>(null) }
    var pendingProtocol by remember { mutableStateOf<Protocol?>(null) }
    var showSnapshots by remember { mutableStateOf(false) }
    var pendingCompetitionKeyDateDelete by remember { mutableStateOf<String?>(null) }
    // La baldosa «Fechas clave» abre la hoja del calendario con la lista de fechas clave a la vista (P3a).
    var keyDatesFocusList by remember { mutableStateOf(false) }

    val editingTimelineStartDate = macrocycleTimelineStartDate ?: localEditingTimelineStartDate
    val editingManualEndDate = macrocycleManualEndDate ?: localEditingManualEndDate
    val editingCompetitionDate = macrocycleCompetitionDate ?: localEditingCompetitionDate
    val showKeyDatesSheet = macrocycleKeyDatesSheetOpen ?: localShowKeyDatesSheet
    val showRouteSheet = macrocycleRoadmapExpanded ?: localShowRouteSheet
    val showLibrarySheet = macrocycleLibrarySheetOpen ?: localShowLibrarySheet
    val showLoopsSheet = macrocycleLoopsSheetOpen ?: localShowLoopsSheet

    fun setTimelineStartDate(value: String) {
        onMacrocycleTimelineStartDateChange(value)
        if (macrocycleTimelineStartDate == null) localEditingTimelineStartDate = value
    }

    fun setManualEndDate(value: String) {
        onMacrocycleManualEndDateChange(value)
        if (macrocycleManualEndDate == null) localEditingManualEndDate = value
    }

    fun setCompetitionDate(value: String) {
        onMacrocycleCompetitionDateChange(value)
        if (macrocycleCompetitionDate == null) localEditingCompetitionDate = value
    }

    fun setKeyDatesSheetOpen(value: Boolean) {
        onMacrocycleKeyDatesSheetOpenChange(value)
        if (macrocycleKeyDatesSheetOpen == null) localShowKeyDatesSheet = value
    }

    // Abre o cierra la hoja de la ruta (antes, la sección desplegable de la página).
    fun setRoadmapExpanded(value: Boolean) {
        onMacrocycleRoadmapExpandedChange(value)
        if (macrocycleRoadmapExpanded == null) localShowRouteSheet = value
    }

    fun setLibrarySheetOpen(value: Boolean) {
        onMacrocycleLibrarySheetOpenChange(value)
        if (macrocycleLibrarySheetOpen == null) localShowLibrarySheet = value
    }

    fun setLoopsSheetOpen(value: Boolean) {
        onMacrocycleLoopsSheetOpenChange(value)
        if (macrocycleLoopsSheetOpen == null) localShowLoopsSheet = value
    }

    fun adjustBlockWeeks(blockId: String, deltaWeeks: Int) {
        val updated = if (deltaWeeks > 0) {
            ProgramKeyDateEngine.addWeeksToBlock(program, blockId, deltaWeeks)
        } else {
            ProgramKeyDateEngine.removeWeeksFromBlock(program, blockId, -deltaWeeks)
        }
        onUpdateProgram(updated.alignTemporalMetadata())
    }

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
        setKeyDatesSheetOpen(false)
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
        setCompetitionDate("")
        setKeyDatesSheetOpen(false)
        editingKeyDate = null
    }

    val temporalInsight = remember(program) { program.toTemporalInsight() }
    val stats = remember(program) { program.toProgramStats() }
    val advancedRoadmap = remember(program) { buildAdvancedRoadmap(program) }
    val simpleCalendarizationHazeState = remember { HazeState() }
    // Una sola lectura del reloj para toda la pestaña Estructura: las fechas relativas y la hoja de render dependen de ella (P3a).
    val today = remember { LocalDate.now() }

    // Qué herramientas y qué calendario muestra cada tipo de programa (P3a).
    val toolsKind = when {
        !temporalInsight.isSimple -> ProgramToolsKind.BLOCKS
        program.simpleProgramKind == SimpleProgramKind.CALENDARIZED -> ProgramToolsKind.CALENDARIZED
        else -> ProgramToolsKind.CYCLIC
    }
    val temporalIssues = remember(program) { program.validateTemporalStructure() }
    // Lo que el módulo de calendario muestra sale de lo GUARDADO, no de los borradores de las hojas.
    val savedCompetition = program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }
    val savedPreview = remember(program, today) {
        buildCalendarPreview(
            program = program,
            timelineStartDate = program.timelineStartDate.orEmpty(),
            competitionDate = savedCompetition?.let { it.eventDate ?: it.startDate }.orEmpty(),
            manualEndDate = program.calendarization?.manualEndDate.orEmpty(),
            today = today,
        )
    }
    val simpleProjection = remember(program) { ProgramCalendarEngine.project(program) }
    val currentWeekId = remember(program) { programCurrentWeekId(program) }
    val trainingDays = remember(program, currentWeekId) { programTrainingWeekdays(program, currentWeekId) }

    // Bloques (P3b): el modelo de cada módulo sale del programa; la semana en curso ya está calculada arriba.
    val cycle = program.runState?.cycleNumber ?: program.loopState?.currentCycle?.takeIf { it > 0 }
    val blockUis = remember(program, currentWeekId, cycle) { programBlockUis(program, currentWeekId, cycle) }
    val canDeleteBlock = blockUis.size > 1
    val canDeleteWeek = program.totalProgramWeeks > 1

    // Añadir una semana (P3b): en un programa simple la crea el ViewModel, con sus fechas; en uno por bloques, al final del bloque.
    fun addWeekTo(block: ProgramBlockUi) {
        if (temporalInsight.isSimple) {
            onAddSimpleWeek()
            return
        }
        val weekName = "Semana ${program.countWeeksBeforeAppendingToBlock(block.macroIndex, block.blockIndex) + 1}"
        onUpdateProgram(program.addWeekToBlock(block.macroIndex, block.blockIndex, weekName).alignTemporalMetadata())
    }

    // Editar un mesociclo (P3b): abre su hoja con el mesociclo elegido.
    fun editMeso(block: ProgramBlockUi, mesoIndex: Int) {
        val meso = program.macrocycles
            .getOrNull(block.macroIndex)?.blocks?.getOrNull(block.blockIndex)?.mesocycles?.getOrNull(mesoIndex)
            ?: return
        editingMeso = EditingMesoTarget(block.macroIndex, block.blockIndex, mesoIndex, meso, isAdd = false)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = simpleCalendarizationHazeState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgramToolsModule(
            kind = toolsKind,
            blockCount = stats.blocks,
            weekCount = stats.weeks,
            sessionCount = stats.sessions,
            keyDateCount = program.keyDates.count { it.type != KeyDateType.COMPETITION },
            historyCount = programSnapshots.size,
            onCalendar = {
                keyDatesFocusList = false
                setKeyDatesSheetOpen(true)
            },
            onKeyDates = {
                keyDatesFocusList = true
                setKeyDatesSheetOpen(true)
            },
            onRoute = { setRoadmapExpanded(true) },
            onCycles = { setLoopsSheetOpen(true) },
            onTemplates = { setLibrarySheetOpen(true) },
            onHistory = {
                onRefreshProgramSnapshots()
                showSnapshots = true
            },
            onCalendarize = { onShowSimpleCalendarizationSheetChange(true) },
            modifier = Modifier.kpknEntrance("programdetail.estructura", 0),
        )
        ProgramCalendarModule(
            kind = toolsKind,
            today = today,
            startDate = if (toolsKind == ProgramToolsKind.BLOCKS) savedPreview.startDate else simpleProjection.startDate,
            endDate = if (toolsKind == ProgramToolsKind.BLOCKS) {
                savedPreview.manualEndDate ?: savedPreview.programEnd
            } else {
                simpleProjection.effectiveEndDate
            },
            endIsEstimate = if (toolsKind == ProgramToolsKind.BLOCKS) {
                savedPreview.manualEndDate == null
            } else {
                simpleProjection.manualEndDate == null
            },
            competitionDate = savedPreview.competitionDate,
            trainingDays = trainingDays,
            repeatingWeekLabel = programProgressLabel(
                weekIndex = null,
                weekCount = null,
                phase = null,
                repeatingWeek = true,
                cycle = program.runState?.cycleNumber,
            ) ?: "Semana que se repite",
            durationWeeks = simpleProjection.weeks.size,
            messages = if (toolsKind == ProgramToolsKind.BLOCKS) savedPreview.messages else emptyList(),
            onEdit = {
                if (toolsKind == ProgramToolsKind.BLOCKS) {
                    keyDatesFocusList = false
                    setKeyDatesSheetOpen(true)
                } else {
                    onShowSimpleCalendarizationSheetChange(true)
                }
            },
            onCalendarize = { onShowSimpleCalendarizationSheetChange(true) },
            modifier = Modifier.kpknEntrance("programdetail.estructura", 1),
        )
        if (temporalIssues.isNotEmpty()) {
            ProgramStructureNotice(
                messages = temporalIssues.map { it.message },
                modifier = Modifier.kpknEntrance("programdetail.estructura", 2),
            )
        }
        // Bloques (P3b): un módulo por bloque, en el orden del programa, con sus mesociclos y sus semanas en círculos. Las hojas y los
        // menús se abren desde aquí y se pintan al final de la pantalla.
        ProgramBlockList(
            blocks = blockUis,
            isSimple = temporalInsight.isSimple,
            selectedWeekId = selectedWeekId,
            onFocusWeek = { blockId, weekId -> onFocusWeek(blockId, weekId) },
            onWeekLongPress = { _, weekId, anchor -> weekMenu = AnchoredTarget(weekId, anchor) },
            onBlockMore = { blockId, anchor -> blockMenu = AnchoredTarget(blockId, anchor) },
            onEditMeso = { blockId, mesoIndex -> blockUis.blockAt(blockId)?.let { editMeso(it, mesoIndex) } },
            onAddWeekToBlock = { blockId -> blockUis.blockAt(blockId)?.let { addWeekTo(it) } },
            onAddMesoToBlock = { blockId ->
                blockUis.blockAt(blockId)?.let { block ->
                    editingMeso = EditingMesoTarget(block.macroIndex, block.blockIndex, mesoIndex = null, meso = null, isAdd = true)
                }
            },
            onAddBlock = { blockSheet = BlockSheetTarget.Add },
            onConvert = { pendingSimpleToAdvanced = true },
        )

        Spacer(Modifier.height(120.dp))
    }

    // Hoja de bloque (P3b): un bloque nuevo, o uno existente con la vista previa de su progresión. Un bloque nuevo se crea en una sola
    // escritura, con su objetivo y su esquema.
    blockSheet?.let { target ->
        val editing = (target as? BlockSheetTarget.Edit)?.let { edit ->
            program.macrocycles.flatMap { it.blocks }.firstOrNull { it.id == edit.blockId }
        }
        if (target is BlockSheetTarget.Add || editing != null) {
            ProgramBlockSheet(
                block = editing,
                suggestedName = "Bloque ${blockUis.size + 1}",
                preview = editing?.let { blockProgressionPreview(it.id) },
                onSave = { name, description, goal, scheme ->
                    when (target) {
                        is BlockSheetTarget.Add -> {
                            val base = program.ensureMacrocycle()
                            val updated = base.addBlockToMacro(base.macrocycles.lastIndex, name, description, goal, scheme)
                            onUpdateProgram(updated.alignTemporalMetadata())
                        }
                        is BlockSheetTarget.Edit -> onUpdateBlock(target.blockId, name, description, goal, scheme)
                    }
                    blockSheet = null
                },
                onDismiss = { blockSheet = null },
            )
        }
    }

    // Hoja de la semana (P3b): la de Semana, con «Eliminar semana» si el programa tiene más de una.
    weekEdit?.let { draft ->
        val onDeleteDraft: () -> Unit = {
            pendingDelete = DeleteTarget.Week(draft.weekId)
            weekEdit = null
        }
        ProgramWeekEditSheet(
            name = draft.name,
            description = draft.description,
            onNameChange = { weekEdit = draft.copy(name = it) },
            onDescriptionChange = { weekEdit = draft.copy(description = it) },
            onSave = {
                onUpdateWeek(draft.weekId, draft.name.trim(), draft.description.trim().ifBlank { null })
                weekEdit = null
            },
            onDismiss = { weekEdit = null },
            onDelete = if (canDeleteWeek) onDeleteDraft else null,
        )
    }

    editingMeso?.let { target ->
        val mesocycles = program.macrocycles.getOrNull(target.macroIndex)?.blocks?.getOrNull(target.blockIndex)?.mesocycles
            ?: emptyList()
        ProgramMesoSheet(
            meso = if (target.isAdd) null else target.meso,
            suggestedName = "Mesociclo ${mesocycles.size + 1}",
            canDelete = !target.isAdd && mesocycles.size > 1,
            onSave = { name, goal ->
                val updated = if (target.isAdd) {
                    program.addMesocycle(target.macroIndex, target.blockIndex, name, goal)
                } else {
                    program.renameMesocycle(target.macroIndex, target.blockIndex, target.mesoIndex ?: 0, name, goal)
                }
                onUpdateProgram(updated.alignTemporalMetadata())
                editingMeso = null
            },
            onDelete = {
                pendingDelete = DeleteTarget.Meso(target.macroIndex, target.blockIndex, target.mesoIndex ?: 0)
                editingMeso = null
            },
            onDismiss = { editingMeso = null },
        )
    }

    // Menú del bloque (P3b): «Editar bloque» y, si hay más de uno, «Eliminar bloque».
    blockMenu?.let { menu ->
        ProgramItemMenu(
            items = buildList<ProgramAnchoredMenuItem> {
                add(ProgramAnchoredMenuItem(label = "Editar bloque", onClick = { blockSheet = BlockSheetTarget.Edit(menu.id) }))
                if (canDeleteBlock) {
                    add(
                        ProgramAnchoredMenuItem(
                            label = "Eliminar bloque",
                            onClick = { pendingDelete = DeleteTarget.Block(menu.id) },
                            destructive = true,
                        ),
                    )
                }
            },
            anchor = menu.anchor,
            onDismiss = { blockMenu = null },
        )
    }

    // Menú de la semana (P3b): editar, copiar, pegar aquí (con una semana copiada) y eliminar.
    weekMenu?.let { menu ->
        val week = program.findProgramWeek(menu.id)
        if (week != null) {
            ProgramItemMenu(
                items = buildList<ProgramAnchoredMenuItem> {
                    add(
                        ProgramAnchoredMenuItem(
                            label = "Editar semana",
                            onClick = { weekEdit = WeekEditTarget(menu.id, week.name, week.description.orEmpty()) },
                        ),
                    )
                    add(ProgramAnchoredMenuItem(label = "Copiar semana", onClick = { onCopyWeek(menu.id) }))
                    if (copiedWeekId != null && copiedWeekId != menu.id) {
                        add(ProgramAnchoredMenuItem(label = "Pegar aquí", onClick = { onPasteWeek(menu.id) }))
                    }
                    if (canDeleteWeek) {
                        add(
                            ProgramAnchoredMenuItem(
                                label = "Eliminar semana",
                                onClick = { pendingDelete = DeleteTarget.Week(menu.id) },
                                destructive = true,
                            ),
                        )
                    }
                },
                anchor = menu.anchor,
                onDismiss = { weekMenu = null },
            )
        }
    }

    editingKeyDate?.let { keyDate ->
        KeyDateDialog(
            keyDate = keyDate,
            isNew = program.keyDates.none { it.id == keyDate.id },
            today = today,
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
                setKeyDatesSheetOpen(true)
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
                        setCompetitionDate("")
                    }
                    editingKeyDate = null
                    setKeyDatesSheetOpen(true)
                }
            },
            onDismiss = { editingKeyDate = null },
        )
    }

    pendingDelete?.let { target ->
        ProgramDeleteDialog(
            text = when (target) {
                is DeleteTarget.Block -> "Eliminar este bloque puede cambiar la lógica temporal del programa."
                is DeleteTarget.Meso -> "Eliminar este mesociclo quitará sus semanas y sesiones."
                is DeleteTarget.Week -> "Eliminar esta semana quitará sus sesiones."
            },
            onConfirm = {
                when (target) {
                    is DeleteTarget.Block -> onDeleteBlock(target.blockId)
                    is DeleteTarget.Meso -> onUpdateProgram(
                        program.removeMesocycle(target.macroIndex, target.blockIndex, target.mesoIndex).alignTemporalMetadata(),
                    )
                    is DeleteTarget.Week -> onDeleteWeek(target.weekId)
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
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
        ProgramConvertBlocksDialog(
            onConfirm = {
                val updated = program
                    .ensureMacrocycle()
                    .addBlockToMacro(0, "Bloque 2")
                    .convertSimpleCalendarizedToAdvanced()
                    .alignTemporalMetadata()
                onUpdateProgram(updated)
                pendingSimpleToAdvanced = false
            },
            onDismiss = { pendingSimpleToAdvanced = false },
        )
    }

    if (!temporalInsight.isSimple && showKeyDatesSheet && editingKeyDate == null) {
        KeyDatesSection(
            program = program,
            today = today,
            focusKeyDates = keyDatesFocusList,
            timelineStartDate = editingTimelineStartDate,
            competitionDate = editingCompetitionDate,
            manualEndDate = editingManualEndDate,
            onTimelineStartDateChange = ::setTimelineStartDate,
            onCompetitionDateChange = ::setCompetitionDate,
            onManualEndDateChange = ::setManualEndDate,
            otherKeyDates = program.keyDates.filter { it.type != KeyDateType.COMPETITION },
            onAddOtherKeyDate = {
                editingKeyDate = ProgramKeyDate(
                    id = "key_${System.nanoTime()}",
                    title = "",
                    type = KeyDateType.EXAMS,
                    startDate = LocalDate.now().toString(),
                )
                setKeyDatesSheetOpen(false)
            },
            onEditOtherKeyDate = { keyDate ->
                editingKeyDate = keyDate
                setKeyDatesSheetOpen(false)
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
            onDismiss = { setKeyDatesSheetOpen(false) },
        )
    }

    if (showLibrarySheet) {
        LibrarySection(
            onApplyTemplate = { template ->
                setLibrarySheetOpen(false)
                pendingTemplate = template
            },
            onApplyProtocol = { protocol ->
                setLibrarySheetOpen(false)
                pendingProtocol = protocol
            },
            onDismiss = { setLibrarySheetOpen(false) },
        )
    }

    pendingProtocol?.let { protocol ->
        com.example.kpkn.screens.programs.ProtocolDetailSheet(
            protocol = protocol,
            onDismiss = { pendingProtocol = null },
            continueLabel = "Reemplazar plan",
            onContinue = {
                onApplyProtocol(protocol, true)
                pendingProtocol = null
                setLibrarySheetOpen(false)
            },
            onCreateCopy = {
                onApplyProtocol(protocol, false)
                pendingProtocol = null
                setLibrarySheetOpen(false)
            },
        )
    }

    pendingTemplate?.let { template ->
        ProgramTemplateConfirmSheet(
            template = template,
            hasSessions = ProgramTemplateEngine.hasSessionContent(program),
            onConfirm = { overwrite ->
                onApplyProgramTemplateOverwrite(template, overwrite)
                pendingTemplate = null
                setLibrarySheetOpen(false)
            },
            onDismiss = { pendingTemplate = null },
        )
    }

    if (showSnapshots) {
        ProgramHistorySheet(
            snapshots = programSnapshots,
            onRestore = { snapshotId ->
                onRestoreProgramSnapshot(snapshotId)
                showSnapshots = false
            },
            onDismiss = { showSnapshots = false },
        )
    }

    if (temporalInsight.isSimple && program.simpleProgramKind == SimpleProgramKind.CYCLIC && showLoopsSheet) {
        CyclesSection(
            program = program,
            onUpdateProgram = onUpdateProgram,
            onFocusWeek = { blockId, weekId ->
                setLoopsSheetOpen(false)
                onFocusWeek(blockId, weekId)
            },
            onDismiss = { setLoopsSheetOpen(false) },
        )
    }

    if (!temporalInsight.isSimple && showRouteSheet) {
        RouteSection(
            program = program,
            roadmap = advancedRoadmap,
            today = today,
            onFocusWeek = { blockId, weekId ->
                setRoadmapExpanded(false)
                onFocusWeek(blockId, weekId)
            },
            onAdjustBlockWeeks = { blockId, deltaWeeks -> adjustBlockWeeks(blockId, deltaWeeks) },
            onDismiss = { setRoadmapExpanded(false) },
        )
    }

    if (temporalInsight.isSimple && showSimpleCalendarizationSheet) {
        CalendarSection(
            program = program,
            today = today,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MacrocycleToolbar(
    insight: TemporalInsight,
    stats: ProgramStats,
    keyDatesCount: Int,
    hasTimelineStartDate: Boolean,
    showRoadmap: Boolean,
    isSimpleCalendarized: Boolean,
    snapshotCount: Int = 0,
    onToggleRoadmap: () -> Unit,
    onOpenKeyDates: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLoops: () -> Unit,
    onOpenSimpleCalendarization: () -> Unit,
    onOpenSnapshots: () -> Unit = {},
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
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (insight.isSimple) {
                if (!isSimpleCalendarized) {
                    OutlinedButton(onClick = onOpenLoops) {
                        Text("Loops", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
                OutlinedButton(onClick = onOpenKeyDates) {
                    Text("Fechas clave", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onToggleRoadmap) {
                    Text(
                        if (showRoadmap) "Ocultar roadmap" else "Roadmap",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedButton(onClick = onOpenLibrary) {
                Text("Plantillas", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onOpenSnapshots,
                enabled = snapshotCount > 0,
                modifier = Modifier.testTag("macrocycle_history_button"),
            ) {
                Text(
                    if (snapshotCount > 0) "Historial ($snapshotCount)" else "Historial",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
internal fun LegacySimpleCalendarizationSheet(
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
    val parsedStartDate = parseIsoDateExact(startDate)
    val parsedEndDate = parseIsoDateExact(endDate)
    val weekCount = if (parsedStartDate != null && parsedEndDate != null) {
        inclusiveCalendarWeekCount(parsedStartDate, parsedEndDate)
    } else {
        0
    }

    KpknSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        dismissible = false,
    ) {
        val sheetPrimary = Color.White
        val sheetSecondary = Color.White.copy(alpha = 0.74f)
        val sheetGlass = Color.White.copy(alpha = 0.11f)
        val sheetGlassStrong = Color.White.copy(alpha = 0.16f)
        if (isCalendarized) {
            val latestBreakTitle = program.calendarBreaks.lastOrNull()?.title
            val isCycleCalendarization = latestBreakTitle == "Ciclo calendarizado"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (isCycleCalendarization) "Programa Simple Calendarizado" else "Programa Simple Calendarizado",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = sheetPrimary,
                )
                Text(
                    if (isCycleCalendarization) {
                        "Tu ciclo quedó anclado a fechas reales. Los loops y eventos cíclicos quedan pausados hasta que recuperes el ciclo cíclico."
                    } else {
                        "Esto sirve cuando tu semana real cambia: turnos rotativos, viajes, exámenes, semanas con pocos días libres o una etapa donde no puedes repetir el ciclo normal. Tu rutina cíclica queda pausada; mientras dura este break, los loops y eventos cíclicos no se aplican."
                    },
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
                        Text(
                            if (isCycleCalendarization) "Calendarización activa" else "Break calendarizado activo",
                            fontWeight = FontWeight.Black,
                            color = sheetPrimary,
                        )
                        Text(
                            if (isCycleCalendarization) {
                                "Cuando termines estas semanas, puedes recuperar la rutina cíclica anterior o empezar una nueva desde cero."
                            } else {
                                "Cuando termines estas semanas, puedes recuperar la rutina cíclica anterior o empezar una nueva desde cero."
                            },
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
                    "Úsalo cuando tus días libres cambian semana a semana (turnos rotativos, viajes). " +
                        "\"Calendarizar ciclo actual\" conserva tus sesiones y las ancla a fechas. " +
                        "\"Crear break\" abre semanas vacías fechadas sin tocar el ciclo cíclico guardado en snapshot. " +
                        "Después puedes ajustar la fecha de cada día de entrenamiento en la vista Semana.",
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
internal fun LegacyKeyDatesManagementSheet(
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
        buildCalendarPreview(program, timelineStartDate, competitionDate, manualEndDate, LocalDate.now())
    }
    val canSave = timelineStartDate.isBlank() || parseIsoDateExact(timelineStartDate) != null
    val canSaveCompetition = competitionDate.isBlank() || parseIsoDateExact(competitionDate) != null
    val canSaveManualEnd = manualEndDate.isBlank() || parseIsoDateExact(manualEndDate) != null
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
    val parsed = parseIsoDateExact(value)
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
internal fun LegacyKeyDateDialog(
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
                    // La competición se edita exclusivamente vía "Fechas clave" → applyAdvancedCalendarSave,
                    // para no dejar la calendarización/eventDate inconsistente con este flujo genérico.
                    KeyDateType.entries.filter { it != KeyDateType.COMPETITION }.forEach { entry ->
                        FilterChip(selected = type == entry, onClick = { type = entry }, label = { Text(entry.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                    if (keyDate.type == KeyDateType.COMPETITION) {
                        Text(
                            "La fecha de competición se edita desde \"Fechas clave y calendario\". Aquí solo puedes eliminarla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        enabled = title.isNotBlank() && startDate.isNotBlank() && type != KeyDateType.COMPETITION,
                    ) { Text("Guardar") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun LegacyLibrarySection(
    currentProgram: Program,
    onApplyTemplate: (ProgramTemplateOption) -> Unit,
    onApplyProtocol: (Protocol) -> Unit,
    onDismiss: () -> Unit,
) {
    com.example.kpkn.screens.programs.CreateProgramTemplateSheet(
        onDismiss = onDismiss,
        onCreateBlank = null,
        onCreateFromTemplate = onApplyTemplate,
        onSelectProtocol = onApplyProtocol,
    )
}

internal data class ProgramStats(val weeks: Int, val sessions: Int, val mesos: Int, val blocks: Int)

internal data class CalendarPreview(
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

internal data class BlockStartPreview(
    val blockName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weeks: Int,
)

internal data class TemporalInsight(
    val isSimple: Boolean,
    val cycleWeeks: Int?,
    val loopCadenceCycles: Int?,
    val loopLengthWeeks: Int?,
    val blocks: Int,
    val mesocycles: Int,
    val weeks: Int,
    val hadLoopsOrEvents: Boolean,
)

/** Lo que se edita en la hoja de bloque (P3b): un bloque nuevo, o uno existente por su id. */
private sealed class BlockSheetTarget {
    object Add : BlockSheetTarget()
    data class Edit(val blockId: String) : BlockSheetTarget()
}

/** Un menú anclado a un bloque o a una semana (P3b): su id y dónde está su ancla en la ventana. */
private data class AnchoredTarget(val id: String, val anchor: Rect)

/** Borrador de la hoja «Editar semana» (P3b): la semana que se edita y lo escrito. */
private data class WeekEditTarget(val weekId: String, val name: String, val description: String)

private data class EditingMesoTarget(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int?,
    val meso: Mesocycle?,
    val isAdd: Boolean,
)

/** Lo que se borra tras confirmar (P3b): un bloque y una semana por su id; un mesociclo, por su posición. */
private sealed class DeleteTarget {
    data class Block(val blockId: String) : DeleteTarget()
    data class Meso(val macroIndex: Int, val blockIndex: Int, val mesoIndex: Int) : DeleteTarget()
    data class Week(val weekId: String) : DeleteTarget()
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

private fun defaultBlock(
    name: String,
    description: String? = null,
    goal: BlockGoal? = null,
    progressionScheme: BlockProgressionScheme? = null,
): Block {
    return Block(
        id = "block_${System.nanoTime()}",
        name = name,
        description = description,
        mesocycles = listOf(defaultMesocycle()),
        goal = goal,
        progressionScheme = progressionScheme,
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

private fun Program.addBlockToMacro(
    macroIndex: Int,
    blockName: String,
    description: String? = null,
    goal: BlockGoal? = null,
    progressionScheme: BlockProgressionScheme? = null,
): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { index, macro ->
            if (index != macroIndex) macro
            else macro.copy(blocks = macro.blocks + defaultBlock(blockName, description, goal, progressionScheme))
        }
    )
}

/** El bloque del módulo con ese id (P3b). */
private fun List<ProgramBlockUi>.blockAt(blockId: String): ProgramBlockUi? = firstOrNull { it.id == blockId }

/** La semana con ese id, esté en el bloque o el mesociclo que esté (P3b). */
private fun Program.findProgramWeek(weekId: String): ProgramWeek? =
    macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.firstOrNull { it.id == weekId }

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
                    else {
                        val lastMesoIndex = block.mesocycles.lastIndex
                        if (lastMesoIndex < 0) {
                            block.copy(mesocycles = listOf(defaultMesocycle().copy(weeks = listOf(week))))
                        } else {
                            block.copy(
                                mesocycles = block.mesocycles.mapIndexed { currentMesoIndex, meso ->
                                    if (currentMesoIndex != lastMesoIndex) meso
                                    else meso.copy(weeks = meso.weeks + week)
                                }
                            )
                        }
                    }
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

internal data class AdvancedRoadmap(
    val startDate: LocalDate?,
    val weekSlots: List<AdvancedWeekSlot>,
    val keyDateTracks: List<KeyDateTrack> = emptyList(),
    val competitionDate: LocalDate? = null,
)

internal data class AdvancedWeekSlot(
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

internal data class KeyDateMark(
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
            KeyDateType.COMPETITION -> "Ver semana"
            KeyDateType.EXAMS -> "Crear sesión ligera"
            KeyDateType.VACATION -> "Crear sesión ajuste"
            KeyDateType.TRAVEL -> "Crear sesión viaje"
            KeyDateType.CUSTOM -> "Crear sesión"
        }

    val preferredDayOfWeek: Int
        get() = preferredDayOfWeek(type)
}

internal enum class KeyDateMarkKind { SPECIAL_WEEK, SPECIAL_SESSION }
internal enum class KeyDateTrackKind { BEFORE, INSIDE, AFTER }

internal data class KeyDateTrack(
    val keyDate: ProgramKeyDate,
    val slots: List<KeyDateTrackSlot>,
)

internal data class KeyDateTrackSlot(
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
            parseIsoDateExact(keyDate.eventDate) ?: parseIsoDateExact(keyDate.startDate)
        },
    )
}

private fun buildKeyDateMark(
    keyDate: ProgramKeyDate,
    weekStart: LocalDate,
    weekEnd: LocalDate,
): KeyDateMark? {
    val eventDate = parseIsoDateExact(keyDate.eventDate)
    val start = parseIsoDateExact(keyDate.startDate) ?: eventDate ?: return null
    val end = parseIsoDateExact(keyDate.endDate) ?: start
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

/**
 * Vista previa del calendario: los bloques proyectados desde [timelineStartDate], la semana de la competición y los avisos del motor.
 * [today] cuenta la cuenta atrás; lo recibe de la pantalla para que la vista sea determinista.
 */
internal fun buildCalendarPreview(
    program: Program,
    timelineStartDate: String,
    competitionDate: String,
    manualEndDate: String,
    today: LocalDate,
): CalendarPreview {
    val start = parseIsoDateExact(timelineStartDate)
    val competition = parseIsoDateExact(competitionDate)
    val manualEnd = parseIsoDateExact(manualEndDate)
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
    val daysUntilCompetition = competition?.let { ChronoUnit.DAYS.between(today, it) }

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
    var cursor = parseIsoDateExact(program.timelineStartDate) ?: return null
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

private fun formatFullDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-CL")))
}

private fun buildKeyDateTrack(
    keyDate: ProgramKeyDate,
    slots: List<AdvancedWeekSlot>,
): KeyDateTrack? {
    val start = parseIsoDateExact(keyDate.startDate) ?: return null
    val end = parseIsoDateExact(keyDate.endDate) ?: start
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
