package com.example.kpkn.screens.programdetail.components
/**
 * Macrocycle editor implementation (legacy monolito).
 * Edición de Block.goal / progressionScheme y preview de progresión viven en
 * la hoja de bloque ([ProgramBlockSheet], P3b); esta fachada no duplica esa
 * lógica (plan 2026-08-20 Fase C).
 */
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramKeyDate
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
import com.example.kpkn.domain.training.ProgramAnalyticsReport
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
import com.example.kpkn.screens.programdetail.design.structure.ProgramFromProgramItem
import com.example.kpkn.screens.programdetail.design.structure.ProgramFromProgramModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramMapModule
import com.example.kpkn.screens.programdetail.design.structure.programMapDateLabel
import com.example.kpkn.screens.programdetail.design.structure.programMapNowLabel
import com.example.kpkn.screens.programdetail.design.structure.programMapSegments
import com.example.kpkn.screens.programdetail.design.structure.programBlockUis
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MacrocycleEditorLegacy(
    program: Program,
    onUpdateProgram: (Program) -> Unit,
    onAddProgramCopy: (Program) -> Unit = {},
    onCompetitionKeyDateSaved: (updatedProgram: Program, keyDate: ProgramKeyDate) -> Unit = { _, _ -> },
    onFocusWeek: (blockId: String, weekId: String) -> Unit = { _, _ -> },
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
    analyticsReport: ProgramAnalyticsReport? = null,
    onOpenTrainingMaxes: (() -> Unit)? = null,
    trainingMaxSummary: String? = null,
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
        // Estructura empieza por el mapa del programa, que sustituye a «Herramientas»: los bloques como tramos, la semana en curso y las fechas.
        val mapStart = if (toolsKind == ProgramToolsKind.BLOCKS) savedPreview.startDate else simpleProjection.startDate
        val mapEnd = if (toolsKind == ProgramToolsKind.BLOCKS) {
            savedPreview.manualEndDate ?: savedPreview.programEnd
        } else {
            simpleProjection.effectiveEndDate
        }
        ProgramMapModule(
            segments = remember(blockUis) { programMapSegments(blockUis) },
            startLabel = programMapDateLabel(mapStart),
            nowLabel = programMapNowLabel(blockUis),
            endLabel = programMapDateLabel(mapEnd),
            onDates = {
                if (toolsKind == ProgramToolsKind.BLOCKS) {
                    keyDatesFocusList = false
                    setKeyDatesSheetOpen(true)
                } else {
                    onShowSimpleCalendarizationSheetChange(true)
                }
            },
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

        ProgramFromProgramModule(
            items = buildList<ProgramFromProgramItem> {
                if (onOpenTrainingMaxes != null) {
                    add(
                        ProgramFromProgramItem(
                            title = "Cargas de referencia (TM)",
                            detail = trainingMaxSummary ?: "Máximos de entrenamiento de los básicos",
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
                        icon = KpknLineIcons.Layers,
                        tag = "estructura_plantillas",
                        onClick = { setLibrarySheetOpen(true) },
                    ),
                )
                add(
                    ProgramFromProgramItem(
                        title = "Versiones guardadas",
                        detail = when (programSnapshots.size) {
                            0 -> "Aún no hay versiones guardadas"
                            1 -> "1 versión · restaurar una copia"
                            else -> "${programSnapshots.size} versiones · restaurar una copia"
                        },
                        icon = KpknLineIcons.History,
                        tag = "estructura_versiones",
                        enabled = programSnapshots.isNotEmpty(),
                        onClick = {
                            onRefreshProgramSnapshots()
                            showSnapshots = true
                        },
                    ),
                )
                if (toolsKind == ProgramToolsKind.BLOCKS) {
                    add(
                        ProgramFromProgramItem(
                            title = "Ruta y semanas por bloque",
                            detail = "Cuántas semanas tiene cada bloque y cómo encaja con la competición",
                            icon = KpknLineIcons.Route,
                            tag = "estructura_ruta",
                            onClick = { setRoadmapExpanded(true) },
                        ),
                    )
                    val keyDateCount = program.keyDates.count { it.type != KeyDateType.COMPETITION }
                    add(
                        ProgramFromProgramItem(
                            title = "Fechas clave",
                            detail = when (keyDateCount) {
                                0 -> "Descargas, tests y otras fechas del programa"
                                1 -> "1 fecha clave"
                                else -> "$keyDateCount fechas clave"
                            },
                            icon = KpknLineIcons.Flag,
                            tag = "estructura_fechas_clave",
                            onClick = {
                                keyDatesFocusList = true
                                setKeyDatesSheetOpen(true)
                            },
                        ),
                    )
                }
                if (toolsKind == ProgramToolsKind.CYCLIC) {
                    add(
                        ProgramFromProgramItem(
                            title = "Ciclos",
                            detail = "Descargas, tests y competiciones que se repiten",
                            icon = KpknLineIcons.Repeat,
                            tag = "estructura_ciclos",
                            onClick = { setLoopsSheetOpen(true) },
                        ),
                    )
                }
            },
            modifier = Modifier.kpknEntrance("programdetail.estructura", 3),
        )
        if (analyticsReport != null) {
            Text(
                text = "REVISIÓN DEL PLAN",
                style = KpknType.eyebrow,
                color = WizardColors.textFaint,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            )
            VolumeAnalyticsCard(report = analyticsReport)
        }
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

    // Menú del bloque (P3b, P7): «Añadir semana», «Añadir mesociclo», «Editar bloque» y, si hay más de uno, «Eliminar bloque».
    blockMenu?.let { menu ->
        ProgramItemMenu(
            items = buildList<ProgramAnchoredMenuItem> {
                add(ProgramAnchoredMenuItem(label = "Añadir semana", onClick = { blockUis.blockAt(menu.id)?.let { addWeekTo(it) } }))
                add(
                    ProgramAnchoredMenuItem(
                        label = "Añadir mesociclo",
                        onClick = {
                            blockUis.blockAt(menu.id)?.let { block ->
                                editingMeso = EditingMesoTarget(
                                    block.macroIndex,
                                    block.blockIndex,
                                    mesoIndex = null,
                                    meso = null,
                                    isAdd = true,
                                )
                            }
                        },
                    ),
                )
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

    return AdvancedRoadmap(
        startDate = startDate,
        weekSlots = slots,
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
        messages += "La competición cae fuera de las semanas del programa. Mueve la fecha o agrega semanas."
    }
    if (competitionWeekRange != null) {
        messages += "La semana completa de competición se marcará como especial en el roadmap y en los puntos superiores."
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

private fun preferredDayOfWeek(type: KeyDateType): Int {
    return when (type) {
        KeyDateType.COMPETITION -> 6
        KeyDateType.EXAMS -> 1
        KeyDateType.VACATION -> 3
        KeyDateType.TRAVEL -> 2
        KeyDateType.CUSTOM -> 1
    }
}

private fun inclusiveCalendarWeekCount(startDate: LocalDate, endDate: LocalDate): Int {
    val inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0) + 1
    return ((inclusiveDays + 6) / 7).toInt().coerceAtLeast(1)
}
