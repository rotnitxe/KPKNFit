package com.example.kpkn.screens.programdetail.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.exercises.isExerciseCatalogV2RuntimeReady
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.screens.programdetail.design.split.SplitApplySheet
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogMode
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogScreen
import com.example.kpkn.screens.programdetail.design.split.SplitCompareSheet
import com.example.kpkn.screens.programdetail.design.split.SplitCustomEditorSheet
import com.example.kpkn.screens.programdetail.design.split.SplitMultiApplySheet
import com.example.kpkn.screens.programdetail.design.split.filterSplitCatalog
import com.example.kpkn.screens.programdetail.design.split.splitUnavailableReason

/**
 * «Cambiar reparto» (P5): la página de repartos de Program Detail. Este archivo guarda el estado (búsqueda, etiqueta, modo de selección,
 * hojas abiertas) y decide qué se aplica; la presentación está en `screens/programdetail/design/split/`. La lógica de repartos (motor,
 * impacto, disponibilidad por plantillas, aplicación y guardado) no cambia.
 *
 * La página hace su propio scroll: quien la abre no debe ponerle una columna desplazable alrededor.
 */
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
    var sheetSplitId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomEditor by rememberSaveable { mutableStateOf(false) }
    var pendingCustomSplit by remember { mutableStateOf<Pair<SplitTemplate, Int>?>(null) }
    var showCompareSheet by rememberSaveable { mutableStateOf(false) }
    var showMultiApply by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(SplitCatalogMode.BROWSE) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val context = LocalContext.current
    val templateRepository = remember(context) { SessionTemplateRepository.getInstance(context) }
    // Disponibilidad, vista previa y aplicación comparten esta lista hidratada. Antes de que Room esté lista está vacía, así que un
    // cambio del usuario no puede parecer que funciona y luego ser ignorado en silencio por la materialización.
    val generationTemplates by templateRepository.generationTemplates.collectAsState()
    // Hidratación de Room: empieza en emptyList(); un catálogo vacío explícito desactivaría el respaldo del motor.
    val applyTemplates = generationTemplates.takeIf { it.isNotEmpty() } ?: SESSION_TEMPLATES_SYSTEM

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
    val startDay = program.startDay ?: 1

    val splits = remember(searchQuery, selectedTag) { filterSplitCatalog(SPLIT_TEMPLATES, searchQuery, selectedTag) }
    // Un reparto sin plantilla para algún día se marca en su módulo; el motor sigue siendo quien decide al aplicar. Sin el catálogo de
    // ejercicios listo, la comprobación no es fiable: entonces no se marca ninguno en vez de afirmar un fallo que no se ha medido.
    // La disponibilidad de los ~45 repartos se calcula fuera del hilo principal; hasta que llega, no se marca ninguno.
    val exerciseCatalogReady = isExerciseCatalogV2RuntimeReady()
    val unavailable by produceState(initialValue = emptyMap<String, String?>(), applyTemplates, exerciseCatalogReady) {
        value = if (!exerciseCatalogReady) {
            emptyMap()
        } else {
            withContext(Dispatchers.Default) {
                SPLIT_TEMPLATES
                    .filter { it.isVisibleForApplication }
                    .associate { split -> split.id to splitUnavailableReason(split, applyTemplates) }
            }
        }
    }

    // El reparto que lleva la semana: la de la semana, si no la del bloque, si no la del programa.
    val currentId = selectedWeekId?.let { program.weekSplitSelections[it] }
        ?: selectedBlockId?.let { program.blockSplitSelections[it] }
        ?: program.selectedSplitId
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
    val currentSplit = when (currentId) {
        null -> null
        "custom" -> customTemplate
        else -> SPLIT_TEMPLATES.firstOrNull { it.id == currentId }
    }

    // Los repartos elegidos: dos en modo comparar, o todos los de la selección múltiple.
    val pickedSplits = selectedIds.mapNotNull { id -> SPLIT_TEMPLATES.firstOrNull { it.id == id && it.isVisibleForApplication } }

    // Entrar en un modo lo deja en exclusiva; tocar el mismo lo cierra. Los elegidos no sobreviven a un cambio de modo.
    fun setMode(next: SplitCatalogMode) {
        selectionMode = if (selectionMode == next) SplitCatalogMode.BROWSE else next
        selectedIds.clear()
    }

    // Comparar admite dos: el tercero sustituye al primero elegido. Varios repartos admite cuantos se quiera.
    fun toggleSelected(split: SplitTemplate) {
        val id = split.id
        if (id in selectedIds) {
            selectedIds.remove(id)
        } else {
            if (selectionMode == SplitCatalogMode.COMPARE && selectedIds.size >= 2) selectedIds.removeAt(0)
            selectedIds.add(id)
        }
    }

    BackHandler(enabled = selectionMode != SplitCatalogMode.BROWSE || onBack != null) {
        if (selectionMode != SplitCatalogMode.BROWSE) setMode(selectionMode) else onBack?.invoke()
    }

    SplitCatalogScreen(
        current = currentSplit,
        startDay = startDay,
        query = searchQuery,
        onQueryChange = { searchQuery = it },
        selectedTag = selectedTag,
        onSelectTag = { selectedTag = it },
        splits = splits,
        customSplit = customTemplate,
        unavailableReason = { split -> unavailable[split.id] },
        isCurrent = { split -> isCurrentSplit(program, split) },
        mode = selectionMode,
        selectedIds = selectedIds.toList(),
        onBack = onBack,
        onToggleCompareMode = { setMode(SplitCatalogMode.COMPARE) },
        onToggleMultiMode = { setMode(SplitCatalogMode.MULTI) },
        onApply = { split -> sheetSplitId = split.id },
        onToggleSelect = { split -> toggleSelected(split) },
        onEditCustom = { showCustomEditor = true },
        onApplyCustom = { customTemplate?.let { pendingCustomSplit = it to startDay } },
        onCompare = { showCompareSheet = true },
        onApplySelected = { showMultiApply = true },
        modifier = modifier,
    )

    if (showCompareSheet && pickedSplits.size == 2) {
        SplitCompareSheet(
            first = pickedSplits[0],
            second = pickedSplits[1],
            startDay = startDay,
            onDismiss = { showCompareSheet = false },
        )
    }

    sheetSplitId?.let { splitId ->
        SPLIT_TEMPLATES.firstOrNull { it.id == splitId && it.isVisibleForApplication }?.let { split ->
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
                generationTemplates = applyTemplates,
                onDismiss = { sheetSplitId = null },
                onApply = {
                    onUpdateProgram(it)
                    sheetSplitId = null
                },
            )
        }
    }

    if (showCustomEditor) {
        SplitCustomEditorSheet(
            program = program,
            defaultStartDay = program.startDay ?: 1,
            onDismiss = { showCustomEditor = false },
            onApply = { customSplit, customStartDay ->
                pendingCustomSplit = customSplit to customStartDay
                showCustomEditor = false
            },
        )
    }

    pendingCustomSplit?.let { (customSplit, customStartDay) ->
        SplitApplySheet(
            program = program,
            selectedSplit = customSplit,
            blocks = blocks,
            selectedBlockId = selectedBlockId,
            selectedWeekId = selectedWeekId,
            weeks = weekOptions,
            defaultStartDay = customStartDay,
            isAdvancedProgram = isAdvancedProgram,
            totalWeeks = totalWeeks,
            generationTemplates = applyTemplates,
            onDismiss = { pendingCustomSplit = null },
            onApply = {
                onUpdateProgram(it)
                pendingCustomSplit = null
            },
        )
    }

    if (showMultiApply) {
        SplitMultiApplySheet(
            program = program,
            splits = pickedSplits,
            weeks = weekOptions,
            selectedBlockId = selectedBlockId,
            defaultStartDay = program.startDay ?: 1,
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
}

/** Marca el catálogo: el reparto ya lo usa el programa, o alguna de sus semanas o bloques. */
private fun isCurrentSplit(program: Program, split: SplitTemplate): Boolean =
    program.selectedSplitId == split.id ||
        program.blockSplitSelections.containsValue(split.id) ||
        program.weekSplitSelections.containsValue(split.id)
