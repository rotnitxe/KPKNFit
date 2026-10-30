package com.example.kpkn.screens.programdetail.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.isExerciseCatalogV2RuntimeReady
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.AdvancedSplitMode
import com.example.kpkn.domain.training.SessionMigrationMode
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitApplicationRequest
import com.example.kpkn.domain.training.SplitTemporalScope
import com.example.kpkn.domain.training.split.CatalogExerciseTraitResolver
import com.example.kpkn.domain.training.split.ExerciseTraitResolver
import com.example.kpkn.domain.training.split.ProgramWeeks
import com.example.kpkn.domain.training.split.SplitCatalog
import com.example.kpkn.domain.training.split.SplitCatalogEntry
import com.example.kpkn.domain.training.split.SplitCatalogFilter
import com.example.kpkn.domain.training.split.SplitMigrationPlanner
import com.example.kpkn.screens.programdetail.design.split.RpAgendaActions
import com.example.kpkn.screens.programdetail.design.split.RpAppliedSummary
import com.example.kpkn.screens.programdetail.design.split.RpAuthorDialog
import com.example.kpkn.screens.programdetail.design.split.RpDecisionActions
import com.example.kpkn.screens.programdetail.design.split.RpDecisionsSheet
import com.example.kpkn.screens.programdetail.design.split.RpPreviewActions
import com.example.kpkn.screens.programdetail.design.split.RpReviewActions
import com.example.kpkn.screens.programdetail.design.split.RpReviewTab
import com.example.kpkn.screens.programdetail.design.split.RpWeekContext
import com.example.kpkn.screens.programdetail.design.split.SplitApplySheet
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogMode
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogScreen
import com.example.kpkn.screens.programdetail.design.split.SplitCompareSheet
import com.example.kpkn.screens.programdetail.design.split.SplitCustomEditorSheet
import com.example.kpkn.screens.programdetail.design.split.SplitErrorDialog
import com.example.kpkn.screens.programdetail.design.split.SplitFlowAssembler
import com.example.kpkn.screens.programdetail.design.split.SplitFlowController
import com.example.kpkn.screens.programdetail.design.split.SplitFlowModels
import com.example.kpkn.screens.programdetail.design.split.SplitMultiApplySheet
import com.example.kpkn.screens.programdetail.design.split.SplitPreviewScreen
import com.example.kpkn.screens.programdetail.design.split.SplitReviewScreen
import com.example.kpkn.ui.components.LocalHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Lo que queda tras aplicar un reparto: el programa de antes y el de después (para «Deshacer» y «Rehacer»), el resumen y si lo hizo el
 * planificador (que guarda su propia instantánea) o el camino de siempre.
 */
internal data class RpAppliedState(
    val before: Program,
    val after: Program,
    val summary: RpAppliedSummary,
    val viaPlanner: Boolean,
    val undone: Boolean = false,
)

private const val ROUTE_CATALOG = "catalog"
private const val ROUTE_PREVIEW = "preview"
private const val ROUTE_REVIEW = "review"

/**
 * «Cambiar reparto» (punto 23): la navegación del flujo. Catálogo → vista previa de tu semana con el reparto elegido → revisión → aplicado (la
 * hoja de resumen la pinta quien abre la página, sobre la semana). Este archivo guarda el estado (búsqueda, etiqueta, filtros, ruta) y pide el
 * plan al motor ([SplitFlowController]); la presentación está en `screens/programdetail/design/split/`. La hoja «Cómo aplicar» (alcance y
 * contenido) es todavía la de siempre y se abre desde la barra de la vista previa; aplica por su cuenta.
 *
 * La página hace su propio scroll: quien la abre no debe ponerle una columna desplazable alrededor.
 */
@Composable
internal fun SplitView(
    program: Program,
    selectedBlockId: String?,
    selectedWeekId: String?,
    onUpdateProgram: (Program) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /** Sesiones hechas o con series registradas (no se tocan). */
    completedSessionIds: Set<String> = emptySet(),
    /** «Semana 5» y sus fechas («3–9 nov»). */
    weekLabel: String = "Semana 1",
    weekRange: String? = null,
    /** Tiempo por sesión de la persona, si se conoce: activa la decisión «Tiempo por sesión». */
    targetMinutes: Int? = null,
    onApplied: (RpAppliedState) -> Unit = { onUpdateProgram(it.after) },
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<SplitTag?>(null) }
    var daysFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var route by rememberSaveable { mutableStateOf(ROUTE_CATALOG) }
    var openSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewTab by rememberSaveable { mutableStateOf(RpReviewTab.SESSION) }
    var showDecisions by rememberSaveable { mutableStateOf(false) }
    var optionsSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomEditor by rememberSaveable { mutableStateOf(false) }
    var showCompareSheet by rememberSaveable { mutableStateOf(false) }
    var showMultiApply by rememberSaveable { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(SplitCatalogMode.BROWSE) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templateRepository = remember(context) { SessionTemplateRepository.getInstance(context) }
    // Disponibilidad, vista previa y aplicación comparten esta lista hidratada. Antes de que Room esté lista está vacía, así que un
    // cambio del usuario no puede parecer que funciona y luego ser ignorado en silencio por la materialización.
    val generationTemplates by templateRepository.generationTemplates.collectAsState()
    // Hidratación de Room: empieza en emptyList(); un catálogo vacío explícito desactivaría el respaldo del motor.
    val applyTemplates = generationTemplates.takeIf { it.isNotEmpty() } ?: SESSION_TEMPLATES_SYSTEM

    val blocks = remember(program.id, program.macrocycles) { SplitApplicationEngine.buildBlockOptions(program) }
    val weekOptions = remember(program.id, program.macrocycles) { SplitApplicationEngine.buildWeekOptions(program) }
    val totalWeeks = remember(program.id, program.macrocycles) {
        program.macrocycles.sumOf { macro -> macro.blocks.sumOf { block -> block.mesocycles.sumOf { meso -> meso.weeks.size } } }
    }
    val isAdvancedProgram = program.structure == ProgramStructure.COMPLEX
    val startDay = program.startDay ?: 1

    // El resolutor del catálogo de ejercicios (indexarlo cuesta): se construye fuera del hilo principal; hasta entonces no clasifica nada.
    val catalogReady = isExerciseCatalogV2RuntimeReady()
    val resolver by produceState<ExerciseTraitResolver>(initialValue = ExerciseTraitResolver { null }, catalogReady) {
        value = if (!catalogReady) {
            ExerciseTraitResolver { null }
        } else {
            withContext(Dispatchers.Default) { approvedExerciseCatalogV2()?.let { CatalogExerciseTraitResolver(it) } ?: ExerciseTraitResolver { null } }
        }
    }

    val weekId = remember(program, selectedWeekId) {
        selectedWeekId ?: ProgramWeeks.effective(program).firstOrNull()?.week?.id ?: ProgramWeeks.all(program).firstOrNull()?.week?.id.orEmpty()
    }
    // Hoy dentro de la semana abierta (solo si hoy cae en sus fechas): los días nuevos no caen antes de hoy.
    val todayWeekday = remember(program, weekId) {
        val week = ProgramWeeks.all(program).firstOrNull { it.week.id == weekId }?.week
        val start = week?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = week?.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start?.plusDays(6)
        val today = LocalDate.now()
        if (start != null && end != null && !today.isBefore(start) && !today.isAfter(end)) today.dayOfWeek.value else null
    }
    val weekContext = remember(program, weekId, weekLabel, weekRange, startDay, todayWeekday, targetMinutes) {
        RpWeekContext(program, weekId, weekLabel, weekRange, startDay, todayWeekday, targetMinutes)
    }
    val controller = remember(program, weekId, resolver, completedSessionIds, todayWeekday, startDay, targetMinutes, applyTemplates) {
        SplitFlowController(
            SplitFlowController.Config(
                program = program,
                weekId = weekId,
                resolver = resolver,
                completedSessionIds = completedSessionIds,
                todayWeekday = todayWeekday,
                startDay = startDay,
                targetMinutes = targetMinutes,
                templates = applyTemplates,
                nowMs = System.currentTimeMillis(),
            ),
            scope,
        )
    }
    val flow by controller.state.collectAsState()

    val customTemplate = remember(program.customSplitPattern, program.customSplitName, program.customSplitDescription) {
        program.customSplitPattern.takeIf { it.size == 7 }?.let { pattern ->
            SplitTemplate(
                id = "custom",
                name = program.customSplitName ?: "Mi reparto",
                description = program.customSplitDescription ?: "Reparto propio",
                tags = listOf(SplitTag.PERSONALIZADO),
                pattern = pattern,
            )
        }
    }
    fun templateOf(id: String?): SplitTemplate? = when (id) {
        null -> null
        "custom" -> customTemplate
        else -> SPLIT_TEMPLATES.firstOrNull { it.id == id && it.isVisibleForApplication }
    }

    // Abre (o reabre, si el controlador se rehízo) el reparto elegido.
    LaunchedEffect(controller, openSplitId) {
        templateOf(openSplitId)?.let { controller.open(it) }
    }

    // El catálogo: «Ahora» y los repartos agrupados por si encajan con los días de la semana.
    val now = remember(weekContext) { SplitFlowAssembler.nowStrip(weekContext) }
    val haveDays = now?.trainingDays
    val catalogView = remember(daysFilter, selectedTag, searchQuery, now) {
        SplitCatalog.view(
            filter = SplitCatalogFilter(days = daysFilter, tag = selectedTag?.takeIf { it != SplitTag.PERSONALIZADO }, query = searchQuery),
            haveDays = haveDays,
            now = now,
        )
    }

    // Los repartos elegidos: dos en modo comparar, o todos los de la selección múltiple.
    val pickedSplits = selectedIds.mapNotNull { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id && it.isVisibleForApplication } }

    // Entrar en un modo lo deja en exclusiva; tocar el mismo lo cierra. Los elegidos no sobreviven a un cambio de modo.
    fun setMode(next: SplitCatalogMode) {
        selectionMode = if (selectionMode == next) SplitCatalogMode.BROWSE else next
        selectedIds.clear()
    }

    // Comparar admite dos: el tercero sustituye al primero elegido. Varios repartos admite cuantos se quiera.
    fun toggleSelected(entry: SplitCatalogEntry) {
        val id = entry.id
        if (id in selectedIds) {
            selectedIds.remove(id)
        } else {
            if (selectionMode == SplitCatalogMode.COMPARE && selectedIds.size >= 2) selectedIds.removeAt(0)
            selectedIds.add(id)
        }
    }

    fun openPreview(split: SplitTemplate) {
        openSplitId = split.id
        route = ROUTE_PREVIEW
        controller.open(split)
    }

    fun backToCatalog() {
        controller.close()
        openSplitId = null
        showDecisions = false
        route = ROUTE_CATALOG
    }

    /** Aplica lo que ve la persona: el plan de la revisión o, en una semana vacía, las plantillas propuestas. */
    fun applyFlow() {
        val split = flow.split ?: return
        val plan = flow.plan
        if (!flow.emptyWeek && plan != null) {
            val after = SplitMigrationPlanner.apply(plan)
            onApplied(RpAppliedState(program, after, SplitFlowModels.appliedSummary(program, plan, weekLabel, targetMinutes, startDay), viaPlanner = true))
            return
        }
        val request = SplitApplicationRequest(
            program = program,
            selectedSplit = split,
            selectedBlockId = selectedBlockId,
            selectedWeekId = weekId,
            startDay = startDay,
            temporalScope = SplitTemporalScope.CURRENT_WEEK,
            selectedWeekIds = setOf(weekId),
            advancedMode = AdvancedSplitMode.GLOBAL,
            migrationMode = SessionMigrationMode.PREBUILT,
            generationTemplates = applyTemplates,
            weekdays = flow.templateDays.map { it.weekday }.takeIf { it.size == com.example.kpkn.domain.training.split.SplitCatalogRules.trainingDays(split) },
        )
        runCatching { SplitApplicationEngine.apply(request) }
            .onSuccess { after ->
                onApplied(
                    RpAppliedState(
                        program,
                        after,
                        SplitFlowModels.appliedSummaryOf(program, after, weekId, SplitFlowAssembler.displayName(split), weekLabel, startDay),
                        viaPlanner = false,
                    ),
                )
            }
            .onFailure { applyError = it.message ?: "El motor no pudo aplicar este reparto." }
    }

    BackHandler(enabled = route != ROUTE_CATALOG || selectionMode != SplitCatalogMode.BROWSE || onBack != null) {
        when {
            route == ROUTE_REVIEW -> route = ROUTE_PREVIEW
            route == ROUTE_PREVIEW -> backToCatalog()
            selectionMode != SplitCatalogMode.BROWSE -> setMode(selectionMode)
            else -> onBack?.invoke()
        }
    }

    val hazeState = LocalHazeState.current
    when (route) {
        ROUTE_REVIEW -> {
            val review = SplitFlowAssembler.review(weekContext, flow, reviewTab)
            if (review != null) {
                SplitReviewScreen(
                    model = review,
                    actions = RpReviewActions(
                        onBack = { route = ROUTE_PREVIEW },
                        onTab = { reviewTab = it },
                        onDecide = { showDecisions = true },
                        onApply = { applyFlow() },
                    ),
                    hazeState = hazeState,
                    modifier = modifier,
                )
            } else {
                LaunchedEffect(Unit) { route = ROUTE_PREVIEW }
            }
        }
        ROUTE_PREVIEW -> {
            val split = flow.split ?: templateOf(openSplitId)
            if (split != null) {
                SplitPreviewScreen(
                    model = SplitFlowAssembler.preview(weekContext, split, flow, resolver),
                    actions = RpPreviewActions(
                        onBack = { backToCatalog() },
                        onView = controller::setView,
                        agenda = RpAgendaActions(onToggle = controller::toggleSelected, onMove = controller::move),
                        onReset = controller::reset,
                        onOpenOptions = { optionsSplitId = split.id },
                        onPick = { id -> templateOf(id)?.let { openPreview(it) } },
                        onSeeAll = { backToCatalog() },
                        onCta = { if (flow.emptyWeek) applyFlow() else route = ROUTE_REVIEW },
                        onAdjustTime = { showDecisions = true },
                        onRefusalAction = { optionsSplitId = split.id },
                    ),
                    hazeState = hazeState,
                    modifier = modifier,
                )
            }
        }
        else -> SplitCatalogScreen(
            now = now,
            nowWeekLabel = weekLabel.lowercase(),
            haveDays = haveDays,
            startDay = startDay,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            selectedTag = selectedTag,
            onSelectTag = { selectedTag = it },
            daysFilter = daysFilter,
            onDaysFilter = { daysFilter = it },
            groups = catalogView.groups,
            customSplit = customTemplate,
            mode = selectionMode,
            selectedIds = selectedIds.toList(),
            onBack = onBack,
            onToggleCompareMode = { setMode(SplitCatalogMode.COMPARE) },
            onToggleMultiMode = { setMode(SplitCatalogMode.MULTI) },
            onOpen = { entry -> templateOf(entry.id)?.let { openPreview(it) } },
            onToggleSelect = { entry -> toggleSelected(entry) },
            onEditCustom = { showCustomEditor = true },
            onApplyCustom = { customTemplate?.let { openPreview(it) } },
            onCompare = { showCompareSheet = true },
            onApplySelected = { showMultiApply = true },
            modifier = modifier,
        )
    }

    // El aviso de un plan con receta de autor: «Mantener mi reparto» vuelve al catálogo; «Adaptar» sigue con la vista previa.
    if (route == ROUTE_PREVIEW && flow.authorPrompt) {
        RpAuthorDialog(onKeep = { backToCatalog() }, onAdapt = controller::dismissAuthorPrompt)
    }

    // Lo que el motor no decide solo, con su propuesta ya puesta.
    val plan = flow.plan
    if (showDecisions && plan != null && route != ROUTE_CATALOG) {
        RpDecisionsSheet(
            model = SplitFlowModels.decisions(plan),
            actions = RpDecisionActions(onPlace = controller::place, onFit = controller::setFit, onDone = { showDecisions = false }),
            onDismiss = { showDecisions = false },
        )
    }

    if (showCompareSheet && pickedSplits.size == 2) {
        SplitCompareSheet(
            first = pickedSplits[0],
            second = pickedSplits[1],
            startDay = startDay,
            onDismiss = { showCompareSheet = false },
        )
    }

    // «Cómo aplicar» (alcance y contenido de las sesiones): la hoja de siempre hasta que se reduzca; aplica por su cuenta.
    templateOf(optionsSplitId)?.let { split ->
        SplitApplySheet(
            program = program,
            selectedSplit = split,
            blocks = blocks,
            selectedBlockId = selectedBlockId,
            selectedWeekId = selectedWeekId,
            weeks = weekOptions,
            defaultStartDay = startDay,
            isAdvancedProgram = isAdvancedProgram,
            totalWeeks = totalWeeks,
            generationTemplates = applyTemplates,
            onDismiss = { optionsSplitId = null },
            onApply = {
                onUpdateProgram(it)
                optionsSplitId = null
            },
        )
    }

    if (showCustomEditor) {
        SplitCustomEditorSheet(
            program = program,
            defaultStartDay = startDay,
            onDismiss = { showCustomEditor = false },
            onApply = { customSplit, _ ->
                showCustomEditor = false
                openPreview(customSplit)
            },
        )
    }

    if (showMultiApply) {
        SplitMultiApplySheet(
            program = program,
            splits = pickedSplits,
            weeks = weekOptions,
            selectedBlockId = selectedBlockId,
            defaultStartDay = startDay,
            generationTemplates = applyTemplates,
            onDismiss = { showMultiApply = false },
            onApply = {
                onUpdateProgram(it)
                showMultiApply = false
                selectionMode = SplitCatalogMode.BROWSE
                selectedIds.clear()
            },
        )
    }

    applyError?.let { SplitErrorDialog(message = it, onDismiss = { applyError = null }) }
}
