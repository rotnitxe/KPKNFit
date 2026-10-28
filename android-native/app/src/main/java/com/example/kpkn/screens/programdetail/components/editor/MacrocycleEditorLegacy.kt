package com.example.kpkn.screens.programdetail.components
/**
 * La pestaña Estructura de Program Detail, pieza a pieza como el lienzo v6: e-map (programa por bloques y programa simple con fechas) y
 * e-cycles (rutina que se repite), con sus hojas e-block, e-dates, e-library y e-history. Las hojas sin escena (mesociclo, fecha clave,
 * ciclos, calendarizar una rutina) siguen el patrón de e-block y e-dates.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.convertSimpleCalendarizedToAdvanced
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.data.models.validateTemporalStructure
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramCalendarProjection
import com.example.kpkn.domain.training.ProgramEndDateStatus
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.domain.training.ProgramSnapshot
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.screens.programdetail.components.editor.CalendarSection
import com.example.kpkn.screens.programdetail.components.editor.CyclesSection
import com.example.kpkn.screens.programdetail.components.editor.KeyDateDialog
import com.example.kpkn.screens.programdetail.components.editor.LibrarySection
import com.example.kpkn.screens.programdetail.components.editor.applyCycleAction
import com.example.kpkn.screens.programdetail.components.editor.programCycleLoops
import com.example.kpkn.screens.programdetail.components.editor.saveCycleDraft
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import com.example.kpkn.screens.programdetail.design.structure.ProgramBaseWeekModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockList
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockUi
import com.example.kpkn.screens.programdetail.design.structure.ProgramCalendarSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramConvertBlocksDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleAction
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleDeleteDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleEditorSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleLoop
import com.example.kpkn.screens.programdetail.design.structure.ProgramDeleteDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramFromProgramItem
import com.example.kpkn.screens.programdetail.design.structure.ProgramFromProgramModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramHistorySheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramMapModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramMesoSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramStructureNotice
import com.example.kpkn.screens.programdetail.design.structure.StructureGroupLabel
import com.example.kpkn.screens.programdetail.design.structure.StructureSecondarySmallPill
import com.example.kpkn.screens.programdetail.design.structure.baseWeekHeadline
import com.example.kpkn.screens.programdetail.design.structure.blockSheetSubtitle
import com.example.kpkn.screens.programdetail.design.structure.programBlockUis
import com.example.kpkn.screens.programdetail.design.structure.programCycleRows
import com.example.kpkn.screens.programdetail.design.structure.programLatestCopyLine
import com.example.kpkn.screens.programdetail.design.structure.programMapDateLabel
import com.example.kpkn.screens.programdetail.design.structure.programMapNowLabel
import com.example.kpkn.screens.programdetail.design.structure.programMapSegments
import com.example.kpkn.screens.programdetail.formatDateRange
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.screens.programdetail.programCurrentWeekId
import com.example.kpkn.screens.programdetail.programTrainingWeekdays
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.kpknEntrance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.util.UUID

/** Qué página dibuja la pestaña: e-map con bloques, e-map de un programa simple con fechas, o e-cycles de una rutina. */
private enum class StructurePage { BLOCKS, CALENDARIZED, ROUTINE }

private const val ENTRANCE = "programdetail.estructura"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditorLegacy(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onAddProgramCopy: (Program) -> Unit = {},
    onCompetitionKeyDateSaved: (updatedProgram: Program, keyDate: ProgramKeyDate) -> Unit = { _, _ -> },
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
    onWeekLongPress: (weekId: String, anchor: Rect) -> Unit = { _, _ -> },
    onApplyProgramTemplateOverwrite: (ProgramTemplateOption, Boolean) -> Unit = { _, _ -> },
    onApplyProtocol: (Protocol, Boolean) -> Unit = { _, _ -> },
    programSnapshots: List<ProgramSnapshot> = emptyList(),
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
    macrocycleKeyDatesSheetOpen: Boolean? = null,
    onMacrocycleKeyDatesSheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLibrarySheetOpen: Boolean? = null,
    onMacrocycleLibrarySheetOpenChange: (Boolean) -> Unit = {},
    macrocycleLoopsSheetOpen: Boolean? = null,
    onMacrocycleLoopsSheetOpenChange: (Boolean) -> Unit = {},
    onUpdateBlock: (blockId: String, name: String, description: String?, goal: BlockGoal?, progressionScheme: BlockProgressionScheme?) -> Unit =
        { _, _, _, _, _ -> },
    blockProgressionPreview: (blockId: String) -> String? = { null },
    onDeleteBlock: (blockId: String) -> Unit = {},
    onAddSimpleWeek: () -> Unit = {},
    onOpenTrainingMaxes: (() -> Unit)? = null,
    trainingMaxSummary: String? = null,
    modifier: Modifier = Modifier,
) {
    var blockSheet by remember { mutableStateOf<BlockSheetTarget?>(null) }
    var blockMenu by remember { mutableStateOf<AnchoredTarget?>(null) }
    var editingKeyDate by remember { mutableStateOf<ProgramKeyDate?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }
    var pendingSimpleToAdvanced by remember { mutableStateOf(false) }
    var localShowDatesSheet by remember { mutableStateOf(false) }
    var localShowLibrarySheet by remember { mutableStateOf(false) }
    var localShowLoopsSheet by remember { mutableStateOf(false) }
    var editingMeso by remember { mutableStateOf<EditingMesoTarget?>(null) }
    var editingCycle by remember { mutableStateOf<ProgramCycleLoop?>(null) }
    var pendingCycleDelete by remember { mutableStateOf<ProgramCycleLoop?>(null) }
    var showSnapshots by remember { mutableStateOf(false) }
    var pendingCompetitionKeyDateDelete by remember { mutableStateOf<String?>(null) }

    val showDatesSheet = macrocycleKeyDatesSheetOpen ?: localShowDatesSheet
    val showLibrarySheet = macrocycleLibrarySheetOpen ?: localShowLibrarySheet
    val showLoopsSheet = macrocycleLoopsSheetOpen ?: localShowLoopsSheet

    fun setDatesSheetOpen(value: Boolean) {
        onMacrocycleKeyDatesSheetOpenChange(value)
        if (macrocycleKeyDatesSheetOpen == null) localShowDatesSheet = value
    }

    fun setLibrarySheetOpen(value: Boolean) {
        onMacrocycleLibrarySheetOpenChange(value)
        if (macrocycleLibrarySheetOpen == null) localShowLibrarySheet = value
    }

    fun setLoopsSheetOpen(value: Boolean) {
        onMacrocycleLoopsSheetOpenChange(value)
        if (macrocycleLoopsSheetOpen == null) localShowLoopsSheet = value
    }

    // Una sola lectura del reloj para toda la pestaña: las fechas relativas dependen de ella.
    val today = remember { LocalDate.now() }
    val simpleCalendarizationHazeState = remember { HazeState() }
    val isSimple = program.isSimpleTemporalProgram
    val page = when {
        !isSimple -> StructurePage.BLOCKS
        program.simpleProgramKind == SimpleProgramKind.CALENDARIZED -> StructurePage.CALENDARIZED
        else -> StructurePage.ROUTINE
    }
    val temporalIssues = remember(program) { program.validateTemporalStructure() }
    // Lo que se pinta sale de lo GUARDADO: la hoja «Fechas» guarda cada cambio al elegirlo.
    val savedCompetition = ProgramKeyDateEngine.competitionKeyDate(program)
    val savedCompetitionDate = savedCompetition?.let { it.eventDate ?: it.startDate }.orEmpty()
    val savedPreview = remember(program, today) {
        buildCalendarPreview(
            program = program,
            timelineStartDate = program.timelineStartDate.orEmpty(),
            competitionDate = savedCompetitionDate,
            manualEndDate = program.calendarization?.manualEndDate.orEmpty(),
        )
    }
    val simpleProjection = remember(program) { ProgramCalendarEngine.project(program) }
    // Las fechas de cada bloque (subtítulo de e-block): un programa por bloques con inicio y sin calendario se proyecta con el de siempre.
    val datesProjection = remember(program) {
        if (program.calendarization == null && !program.timelineStartDate.isNullOrBlank()) {
            ProgramCalendarEngine.project(program.copy(calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization()))
        } else {
            simpleProjection
        }
    }
    val currentWeekId = remember(program) { programCurrentWeekId(program) }
    val trainingDays = remember(program, currentWeekId) { programTrainingWeekdays(program, currentWeekId) }
    val cycle = program.runState?.cycleNumber ?: program.loopState?.currentCycle?.takeIf { it > 0 }
    val blockUis = remember(program, currentWeekId, cycle) { programBlockUis(program, currentWeekId, cycle) }
    // e-map: el bloque de la semana en curso empieza desplegado, con la progresión de ese bloque.
    var expandedBlockId by remember(program.id) {
        mutableStateOf(blockUis.firstOrNull { block -> block.weeks.any { it.isCurrent } }?.id)
    }
    val canDeleteBlock = blockUis.size > 1

    // Añadir una semana: en un programa simple la crea el ViewModel, con sus fechas; en uno por bloques, al final del bloque.
    fun addWeekTo(block: ProgramBlockUi) {
        if (isSimple) {
            onAddSimpleWeek()
            return
        }
        onUpdateProgram(ProgramKeyDateEngine.addWeeksToBlock(program, block.id, 1).alignTemporalMetadata())
    }

    fun removeLastWeekFrom(block: ProgramBlockUi) {
        onUpdateProgram(ProgramKeyDateEngine.removeWeeksFromBlock(program, block.id, 1).alignTemporalMetadata())
    }

    fun addMesoTo(block: ProgramBlockUi) {
        editingMeso = EditingMesoTarget(block.macroIndex, block.blockIndex, mesoIndex = null, meso = null, isAdd = true, blockName = block.name)
    }

    fun editMeso(block: ProgramBlockUi, mesoIndex: Int) {
        val meso = program.macrocycles
            .getOrNull(block.macroIndex)?.blocks?.getOrNull(block.blockIndex)?.mesocycles?.getOrNull(mesoIndex)
            ?: return
        editingMeso = EditingMesoTarget(block.macroIndex, block.blockIndex, mesoIndex, meso, isAdd = false, blockName = block.name)
    }

    fun openDates() {
        if (page == StructurePage.BLOCKS) setDatesSheetOpen(true) else onShowSimpleCalendarizationSheetChange(true)
    }

    // e-dates guarda cada cambio al elegirlo, con las otras dos fechas como están guardadas.
    fun saveDates(start: String, competition: String, end: String) {
        val result = ProgramKeyDateEngine.applyAdvancedCalendarSave(
            program = program,
            timelineStartDate = start,
            competitionDate = competition,
            manualEndDate = end,
            competitionRepository = CompetitionRepository.getInstance(),
        )
        onUpdateProgram(result.program)
        result.competitionKeyDate?.let { onCompetitionKeyDateSaved(result.program, it) }
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
            timelineStartDate = program.timelineStartDate.orEmpty(),
            competitionDate = "",
            manualEndDate = program.calendarization?.manualEndDate.orEmpty(),
            competitionRepository = CompetitionRepository.getInstance(),
        )
        onUpdateProgram(result.program)
        editingKeyDate = null
    }

    // Quitar una fecha clave de competición: con sesiones o registros vinculados, la confirmación decide qué hacer con ellos.
    fun requestCompetitionRemoval(keyDateId: String) {
        if (ProgramKeyDateEngine.hasLinkedCompetitionEntities(program, keyDateId, CompetitionRepository.getInstance())) {
            pendingCompetitionKeyDateDelete = keyDateId
        } else {
            removeCompetitionKeyDate(keyDateId, ProgramKeyDateEngine.KeyDateDeleteMode.UNLINK_SESSION)
        }
    }

    val fromProgram = buildList {
        if (onOpenTrainingMaxes != null) {
            add(
                ProgramFromProgramItem(
                    title = "Cargas de referencia (TM)",
                    detail = trainingMaxSummary,
                    icon = KpknLineIcons.Target,
                    tag = "estructura_tm",
                    onClick = onOpenTrainingMaxes,
                ),
            )
        }
        add(
            ProgramFromProgramItem(
                title = "Aplicar plantilla o método",
                detail = "Rellena semanas con una plantilla de la biblioteca",
                icon = KpknLineIcons.Book,
                tag = "estructura_plantillas",
                onClick = { setLibrarySheetOpen(true) },
            ),
        )
        add(
            ProgramFromProgramItem(
                title = "Versiones guardadas",
                detail = programLatestCopyLine(programSnapshots, today),
                icon = KpknLineIcons.History,
                tag = "estructura_versiones",
                onClick = {
                    onRefreshProgramSnapshots()
                    showSnapshots = true
                },
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeSource(state = simpleCalendarizationHazeState)
            .padding(horizontal = 16.dp),
    ) {
        if (page == StructurePage.ROUTINE) {
            RoutinePage(
                program = program,
                trainingDays = trainingDays,
                cycleLabel = cycle?.let { "Ciclo $it" },
                temporalIssues = temporalIssues.map { it.message },
                onEditBaseWeek = {
                    val block = blockUis.firstOrNull()
                    val week = block?.weeks?.firstOrNull()
                    if (block != null && week != null) onFocusWeek(block.id, week.id)
                },
                onEditCycle = { loopId -> editingCycle = programCycleLoops(program).firstOrNull { it.id == loopId } },
                onAddCycle = { setLoopsSheetOpen(true) },
                block = blockUis.firstOrNull(),
                onEditMeso = { mesoIndex -> blockUis.firstOrNull()?.let { editMeso(it, mesoIndex) } },
                onAddMeso = { blockUis.firstOrNull()?.let { addMesoTo(it) } },
                onAddWeek = onAddSimpleWeek,
                onCalendarize = { onShowSimpleCalendarizationSheetChange(true) },
                onConvert = { pendingSimpleToAdvanced = true },
                fromProgram = fromProgram,
            )
        } else {
            val mapStart = if (page == StructurePage.BLOCKS) savedPreview.startDate else simpleProjection.startDate
            val mapEnd = if (page == StructurePage.BLOCKS) {
                savedPreview.manualEndDate ?: savedPreview.programEnd
            } else {
                simpleProjection.effectiveEndDate
            }
            ProgramMapModule(
                segments = remember(blockUis) { programMapSegments(blockUis) },
                startLabel = programMapDateLabel(mapStart),
                nowLabel = programMapNowLabel(blockUis),
                endLabel = programMapDateLabel(mapEnd),
                onDates = ::openDates,
                modifier = Modifier.padding(bottom = 12.dp).kpknEntrance(ENTRANCE, 0),
            )
            if (temporalIssues.isNotEmpty()) {
                ProgramStructureNotice(
                    messages = temporalIssues.map { it.message },
                    modifier = Modifier.padding(bottom = 12.dp).kpknEntrance(ENTRANCE, 1),
                )
            }
            StructureGroupLabel(text = "Bloques")
            ProgramBlockList(
                blocks = blockUis,
                expandedBlockId = expandedBlockId,
                progressionOf = blockProgressionPreview,
                onToggleBlock = { blockId -> expandedBlockId = if (expandedBlockId == blockId) null else blockId },
                onFocusWeek = onFocusWeek,
                onWeekLongPress = { _, weekId, anchor -> onWeekLongPress(weekId, anchor) },
                onBlockMore = { blockId, anchor -> blockMenu = AnchoredTarget(blockId, anchor) },
                onAddWeekToBlock = { blockId -> blockUis.blockAt(blockId)?.let { addWeekTo(it) } },
                entranceFirst = 2,
            )
            if (page == StructurePage.BLOCKS) {
                StructureSecondarySmallPill(
                    text = "Añadir bloque",
                    onClick = { blockSheet = BlockSheetTarget.Add },
                    icon = KpknLineIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ProgramFromProgramModule(
                    label = null,
                    items = listOf(
                        ProgramFromProgramItem(
                            title = "Pasar a programa por bloques",
                            detail = null,
                            icon = KpknLineIcons.Training,
                            tag = "estructura_convertir",
                            onClick = { pendingSimpleToAdvanced = true },
                        ),
                    ),
                )
            }
            ProgramFromProgramModule(items = fromProgram)
        }
        Spacer(Modifier.height(120.dp))
    }

    // e-block: un bloque nuevo, o uno existente con su subtítulo de semanas y fechas, la vista previa de su progresión y «Eliminar bloque».
    blockSheet?.let { target ->
        val editing = (target as? BlockSheetTarget.Edit)?.let { edit ->
            program.macrocycles.flatMap { it.blocks }.firstOrNull { it.id == edit.blockId }
        }
        val editingUi = editing?.let { blockUis.blockAt(it.id) }
        if (target is BlockSheetTarget.Add || editing != null) {
            ProgramBlockSheet(
                block = editing,
                subtitle = editingUi?.let { ui ->
                    blockSheetSubtitle(ui.weeks.map { it.number }, blockDatesLabel(datesProjection, ui.id))
                },
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
                onDelete = if (editing != null && canDeleteBlock && page == StructurePage.BLOCKS) {
                    {
                        pendingDelete = DeleteTarget.Block(editing.id)
                        blockSheet = null
                    }
                } else {
                    null
                },
            )
        }
    }

    editingMeso?.let { target ->
        val mesocycles = program.macrocycles.getOrNull(target.macroIndex)?.blocks?.getOrNull(target.blockIndex)?.mesocycles
            ?: emptyList()
        ProgramMesoSheet(
            meso = if (target.isAdd) null else target.meso,
            subtitle = target.blockName,
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

    // ⋯ del bloque: editar (nombre, objetivo, progresión y eliminar), «+ / −» semanas (lo que hacía «Ruta») y sus mesociclos.
    blockMenu?.let { menu ->
        val block = blockUis.blockAt(menu.id)
        if (block != null) {
            ProgramItemMenu(
                items = buildList {
                    if (page == StructurePage.BLOCKS) {
                        add(ProgramAnchoredMenuItem(label = "Editar bloque", onClick = { blockSheet = BlockSheetTarget.Edit(menu.id) }))
                    }
                    add(ProgramAnchoredMenuItem(label = "Añadir una semana", onClick = { addWeekTo(block) }))
                    if (block.weeks.size > 1 && !isSimple) {
                        add(ProgramAnchoredMenuItem(label = "Quitar la última semana", onClick = { removeLastWeekFrom(block) }))
                    }
                    add(ProgramAnchoredMenuItem(label = "Añadir mesociclo", onClick = { addMesoTo(block) }))
                    if (block.mesocycles.size > 1) {
                        block.mesocycles.forEachIndexed { index, meso ->
                            add(ProgramAnchoredMenuItem(label = "Editar «${meso.name}»", onClick = { editMeso(block, index) }))
                        }
                    }
                    if (canDeleteBlock && page == StructurePage.BLOCKS) {
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
    }

    // e-dates: «Fechas» de un programa por bloques. Se oculta mientras se edita una fecha clave y vuelve al guardarla.
    if (page == StructurePage.BLOCKS && showDatesSheet && editingKeyDate == null) {
        val competitionDate = parseIsoDateExact(savedCompetitionDate)
        ProgramCalendarSheet(
            today = today,
            start = savedPreview.startDate,
            manualEnd = savedPreview.manualEndDate,
            projectedEnd = savedPreview.programEnd,
            competition = competitionDate,
            competitionTitle = savedCompetition?.title,
            otherKeyDates = program.keyDates.filter { it.type != KeyDateType.COMPETITION },
            trainingDays = trainingDays,
            messages = savedPreview.messages,
            onStartChange = { start ->
                saveDates(start?.toString().orEmpty(), savedCompetitionDate, program.calendarization?.manualEndDate.orEmpty())
            },
            onEndChange = { end ->
                saveDates(program.timelineStartDate.orEmpty(), savedCompetitionDate, end?.toString().orEmpty())
            },
            onCompetitionChange = { date ->
                if (date == null) {
                    savedCompetition?.let { requestCompetitionRemoval(it.id) }
                } else {
                    saveDates(program.timelineStartDate.orEmpty(), date.toString(), program.calendarization?.manualEndDate.orEmpty())
                }
            },
            onAddKeyDate = {
                editingKeyDate = ProgramKeyDate(
                    id = "key_${System.nanoTime()}",
                    title = "",
                    type = KeyDateType.EXAMS,
                    startDate = today.toString(),
                )
            },
            onEditKeyDate = { keyDate -> editingKeyDate = keyDate },
            onAddPause = {
                onUpdateProgram(program.insertPauseWeekAfter(currentWeekId).alignTemporalMetadata())
            },
            onDismiss = { setDatesSheetOpen(false) },
        )
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
            },
            onDelete = {
                if (keyDate.type == KeyDateType.COMPETITION) {
                    editingKeyDate = null
                    requestCompetitionRemoval(keyDate.id)
                } else {
                    onUpdateProgram(
                        ProgramKeyDateEngine.deleteKeyDate(
                            program = program,
                            keyDateId = keyDate.id,
                            competitionRepository = CompetitionRepository.getInstance(),
                        ),
                    )
                    editingKeyDate = null
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
            },
            onConfirm = {
                when (target) {
                    is DeleteTarget.Block -> onDeleteBlock(target.blockId)
                    is DeleteTarget.Meso -> onUpdateProgram(
                        program.removeMesocycle(target.macroIndex, target.blockIndex, target.mesoIndex).alignTemporalMetadata(),
                    )
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
                            "Se eliminará la fecha clave de competición del programa."
                        },
                    )
                    if (linkedSessions > 0) {
                        OutlinedButton(
                            onClick = {
                                removeCompetitionKeyDate(keyDateId, ProgramKeyDateEngine.KeyDateDeleteMode.ARCHIVE_SESSION_AND_RECORD)
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
                        removeCompetitionKeyDate(keyDateId, ProgramKeyDateEngine.KeyDateDeleteMode.UNLINK_SESSION)
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

    if (showLibrarySheet) {
        LibrarySection(
            onApplyTemplate = { template, replace -> onApplyProgramTemplateOverwrite(template, replace) },
            onApplyProtocol = { protocol, replace -> onApplyProtocol(protocol, replace) },
            onDismiss = { setLibrarySheetOpen(false) },
        )
    }

    if (showSnapshots) {
        ProgramHistorySheet(
            snapshots = programSnapshots,
            today = today,
            onRestore = { snapshotId ->
                onRestoreProgramSnapshot(snapshotId)
                showSnapshots = false
            },
            onSaveCopy = {
                showSnapshots = false
                onAddProgramCopy(program.copy(id = UUID.randomUUID().toString(), name = "${program.name} (copia)", isDraft = true))
            },
            onDismiss = { showSnapshots = false },
        )
    }

    // e-cycles: la fila de un ciclo abre su editor, con posponer, cancelar, reactivar y eliminar (este, tras confirmar).
    editingCycle?.let { loop ->
        ProgramCycleEditorSheet(
            loop = loop,
            existing = programCycleLoops(program),
            onSave = { draft ->
                onUpdateProgram(saveCycleDraft(program, draft))
                editingCycle = null
            },
            onAction = { action ->
                if (action == ProgramCycleAction.DELETE) {
                    pendingCycleDelete = loop
                } else {
                    onUpdateProgram(applyCycleAction(program, loop.id, action))
                    editingCycle = null
                }
            },
            onDismiss = { editingCycle = null },
        )
    }

    pendingCycleDelete?.let { loop ->
        ProgramCycleDeleteDialog(
            onConfirm = {
                onUpdateProgram(applyCycleAction(program, loop.id, ProgramCycleAction.DELETE))
                pendingCycleDelete = null
                editingCycle = null
            },
            onDismiss = { pendingCycleDelete = null },
        )
    }

    if (page == StructurePage.ROUTINE && showLoopsSheet) {
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

    if (isSimple && showSimpleCalendarizationSheet) {
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

/**
 * e-cycles, la Estructura de una rutina: «Semana base» (sus sesiones, su duración y sus días), el aviso de la estructura si lo hay, el
 * grupo «Ciclos» con «Añadir ciclo», el módulo del pie con los mesociclos, añadir una semana al ciclo, calendarizar y «Pasar a programa por
 * bloques», y «Del programa».
 */
@Composable
private fun RoutinePage(
    program: Program,
    trainingDays: Set<Int>,
    cycleLabel: String?,
    temporalIssues: List<String>,
    onEditBaseWeek: () -> Unit,
    onEditCycle: (loopId: String) -> Unit,
    onAddCycle: () -> Unit,
    block: ProgramBlockUi?,
    onEditMeso: (mesoIndex: Int) -> Unit,
    onAddMeso: () -> Unit,
    onAddWeek: () -> Unit,
    onCalendarize: () -> Unit,
    onConvert: () -> Unit,
    fromProgram: List<ProgramFromProgramItem>,
) {
    val baseWeek = remember(program) {
        program.macrocycles.firstOrNull()?.blocks?.firstOrNull()?.mesocycles?.firstOrNull()?.weeks?.firstOrNull()
    }
    val headline = remember(baseWeek) {
        val sessions = baseWeek?.sessions.orEmpty()
        baseWeekHeadline(sessions.size, sessions.sumOf { SessionDurationEstimator.estimate(it).totalMinutes })
    }
    val cycleRows = remember(program) { programCycleRows(program) }

    ProgramBaseWeekModule(
        headline = headline,
        trainingDays = trainingDays,
        cycleLabel = cycleLabel,
        onEdit = onEditBaseWeek,
        modifier = Modifier.padding(bottom = 12.dp).kpknEntrance(ENTRANCE, 0),
    )
    if (temporalIssues.isNotEmpty()) {
        ProgramStructureNotice(messages = temporalIssues, modifier = Modifier.padding(bottom = 12.dp).kpknEntrance(ENTRANCE, 1))
    }
    ProgramFromProgramModule(
        label = "Ciclos",
        items = cycleRows.map { row ->
            ProgramFromProgramItem(
                title = row.title,
                detail = row.detail,
                icon = row.icon,
                tag = "estructura_ciclo_${row.loopId}",
                onClick = { onEditCycle(row.loopId) },
            )
        } + ProgramFromProgramItem(
            title = "Añadir ciclo",
            detail = null,
            icon = KpknLineIcons.Plus,
            tag = "estructura_ciclos",
            onClick = onAddCycle,
        ),
        modifier = Modifier.padding(bottom = 12.dp),
    )
    ProgramFromProgramModule(
        label = null,
        items = buildList {
            val mesocycles = block?.mesocycles.orEmpty()
            if (mesocycles.size > 1) {
                mesocycles.forEachIndexed { index, meso ->
                    add(
                        ProgramFromProgramItem(
                            title = meso.name,
                            detail = meso.goalLabel,
                            icon = KpknLineIcons.Layers,
                            tag = "estructura_meso_$index",
                            onClick = { onEditMeso(index) },
                        ),
                    )
                }
            }
            add(ProgramFromProgramItem("Añadir mesociclo", null, KpknLineIcons.Layers, "estructura_meso_nuevo", onAddMeso))
            add(ProgramFromProgramItem("Añadir una semana al ciclo", null, KpknLineIcons.Plus, "estructura_semana_nueva", onAddWeek))
            add(
                ProgramFromProgramItem(
                    "Calendarizar el ciclo",
                    "Fechas reales o una pausa",
                    KpknLineIcons.Calendar,
                    "estructura_calendarizar",
                    onCalendarize,
                ),
            )
            add(ProgramFromProgramItem("Pasar a programa por bloques", null, KpknLineIcons.Training, "estructura_convertir", onConvert))
        },
    )
    ProgramFromProgramModule(items = fromProgram)
}

/** «29 oct – 9 nov»: las fechas de un bloque si el programa tiene fechas; null si no. */
private fun blockDatesLabel(projection: ProgramCalendarProjection, blockId: String): String? {
    val weeks = projection.weeks.filter { it.blockId == blockId }
    val first = weeks.firstOrNull() ?: return null
    return formatDateRange(first.startDate, weeks.last().endDate)
}

/** Lo que se edita en la hoja de bloque: un bloque nuevo, o uno existente por su id. */
private sealed class BlockSheetTarget {
    object Add : BlockSheetTarget()
    data class Edit(val blockId: String) : BlockSheetTarget()
}

/** El ⋯ de un bloque: su id y dónde está su ancla en la ventana. */
private data class AnchoredTarget(val id: String, val anchor: Rect)

private data class EditingMesoTarget(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int?,
    val meso: Mesocycle?,
    val isAdd: Boolean,
    val blockName: String?,
)

/** Lo que se borra tras confirmar: un bloque por su id; un mesociclo, por su posición. */
private sealed class DeleteTarget {
    data class Block(val blockId: String) : DeleteTarget()
    data class Meso(val macroIndex: Int, val blockIndex: Int, val mesoIndex: Int) : DeleteTarget()
}

/** Las fechas guardadas del programa para el mapa y la hoja «Fechas», con los avisos del motor. */
internal data class CalendarPreview(
    val startDate: LocalDate?,
    val programEnd: LocalDate?,
    val manualEndDate: LocalDate?,
    val messages: List<String>,
)

/**
 * Inserta una semana vacía «Pausa» después de [afterWeekId] (la semana en curso), en su mismo mesociclo: las semanas siguientes se corren
 * una. Sin semana en curso, la pausa va al principio del programa. Pura.
 */
internal fun Program.insertPauseWeekAfter(afterWeekId: String?, pauseId: String = "week_${System.nanoTime()}"): Program {
    val pause = ProgramWeek(id = pauseId, name = "Pausa")
    val hasTarget = afterWeekId != null &&
        macrocycles.any { macro -> macro.blocks.any { block -> block.mesocycles.any { meso -> meso.weeks.any { it.id == afterWeekId } } } }
    var inserted = false
    return copy(
        macrocycles = macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            when {
                                inserted -> meso
                                hasTarget -> {
                                    val index = meso.weeks.indexOfFirst { it.id == afterWeekId }
                                    if (index < 0) {
                                        meso
                                    } else {
                                        inserted = true
                                        meso.copy(weeks = meso.weeks.take(index + 1) + pause + meso.weeks.drop(index + 1))
                                    }
                                }
                                else -> {
                                    inserted = true
                                    meso.copy(weeks = listOf(pause) + meso.weeks)
                                }
                            }
                        },
                    )
                },
            )
        },
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
            ),
        ),
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
        weeks = listOf(ProgramWeek(id = "week_${System.nanoTime()}", name = "Semana 1")),
    )
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
        },
    )
}

/** El bloque de la tarjeta con ese id. */
private fun List<ProgramBlockUi>.blockAt(blockId: String): ProgramBlockUi? = firstOrNull { it.id == blockId }

private fun Program.addMesocycle(macroIndex: Int, blockIndex: Int, name: String, goal: MesocycleGoal): Program {
    val mesocycle = defaultMesocycle(name = name, goal = goal)
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(mesocycles = block.mesocycles + mesocycle)
                },
            )
        },
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
                        },
                    )
                },
            )
        },
    )
}

private fun Program.removeMesocycle(macroIndex: Int, blockIndex: Int, mesoIndex: Int): Program {
    return copy(
        macrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            if (currentMacroIndex != macroIndex) macro
            else macro.copy(
                blocks = macro.blocks.mapIndexed { currentBlockIndex, block ->
                    if (currentBlockIndex != blockIndex) block
                    else block.copy(mesocycles = block.mesocycles.filterIndexed { index, _ -> index != mesoIndex })
                },
            )
        },
    )
}

/**
 * Las fechas guardadas del programa proyectadas desde [timelineStartDate], con los avisos del motor: competición sin inicio o fuera de
 * las semanas, y una fecha de fin que no cuadra con las semanas.
 */
internal fun buildCalendarPreview(
    program: Program,
    timelineStartDate: String,
    competitionDate: String,
    manualEndDate: String,
): CalendarPreview {
    val start = parseIsoDateExact(timelineStartDate)
    val competition = parseIsoDateExact(competitionDate)
    val manualEnd = parseIsoDateExact(manualEndDate)
    val messages = mutableListOf<String>()

    val projectedProgram = program.copy(
        timelineStartDate = start?.toString(),
        calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization().copy(
            manualEndDate = manualEndDate.trim().ifBlank { null },
        ),
    )
    val projection = ProgramCalendarEngine.project(projectedProgram)

    if (competition != null && start == null) {
        messages += "Elige cuándo empieza el programa para asignar la competición a una de sus semanas."
    }
    if (competition != null && start != null && projection.weekForDate(competition) == null) {
        messages += "La competición cae fuera de las semanas del programa. Mueve la fecha o añade semanas."
    }
    when (projection.endDateStatus) {
        ProgramEndDateStatus.BEFORE_PROJECTED ->
            messages += "La fecha de fin que pusiste llega antes de que acabe el programa. Quita semanas o mueve la fecha."
        ProgramEndDateStatus.AFTER_PROJECTED ->
            messages += "La fecha de fin que pusiste llega después de lo previsto. Se muestra como objetivo y las semanas no cambian."
        ProgramEndDateStatus.INVALID_MANUAL ->
            messages += "La fecha de fin no es válida. Revísala."
        else -> Unit
    }

    return CalendarPreview(
        startDate = start,
        programEnd = projection.projectedEndDate,
        manualEndDate = manualEnd,
        messages = messages,
    )
}
