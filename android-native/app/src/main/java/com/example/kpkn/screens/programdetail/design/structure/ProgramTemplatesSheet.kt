package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.programs.isLibraryFamilyEntry
import com.example.kpkn.screens.programs.libraryOrder
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknShadow
import dev.chrisbanes.haze.HazeState

/** Una opción de la biblioteca: su clave, su nombre y la línea de debajo («4 sesiones por semana», «Ondas de 3 semanas…»). */
internal data class ProgramLibraryItem(
    val key: String,
    val name: String,
    val detail: String?,
)

/** «4 sesiones por semana» o «De 3 a 5 sesiones por semana»: los días de una plantilla. Pura. */
internal fun libraryFrequencyLine(frequencies: IntRange): String {
    val first = frequencies.first
    val last = frequencies.last
    return when {
        first == last && first == 1 -> "1 sesión por semana"
        first == last -> "$first sesiones por semana"
        else -> "De $first a $last sesiones por semana"
    }
}

/** La primera frase de una descripción, sin el punto final: la línea de un método de progresión. Null si no hay texto. Pura. */
internal fun libraryMethodLine(description: String): String? {
    val text = description.trim()
    if (text.isEmpty()) return null
    val end = text.indexOfFirst { it == '.' || it == '\n' }
    return (if (end > 0) text.substring(0, end) else text).trim().trimEnd('.')
}

/* ─── Colores y estilos de catálogo (ct.css) ─── */
private val CtBg = Color(0xFF1B1B1D)
private val CtModuleBg = Color(0xFF131314)
private val CtInk = Color(0xFFF2EEE6)
private val CtMuted = Color(0xFFB3ADA3)
private val CtFaint = Color(0xFF8E8980)
private val CtMus = Color(0xFFF49A6E)
private val CtEne = Color(0xFFF7CF73)
private val CtOk = Color(0xFF43D18C)
private val CtDel = Color(0xFF7FCFA4)
private val CtDanger = Color(0xFFE05252)

/* ─── Curvas de animación estándar ─── */
private val CtStandardCurve = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * e-library, hoja «Aplicar plantilla o método» como catálogo completo (paquete ct, 1:1 con prototipo HTML).
 */
@Composable
internal fun ProgramTemplatesSheet(
    templates: List<ProgramLibraryItem>,
    methods: List<ProgramLibraryItem>,
    replace: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    onPickTemplate: (key: String, replace: Boolean, onResult: (success: Boolean, undoSnapshotId: String?) -> Unit) -> Unit,
    onPickMethod: (key: String, replace: Boolean, onResult: (success: Boolean, undoSnapshotId: String?) -> Unit) -> Unit,
    onUndo: (snapshotId: String) -> Unit = {},
    onDismiss: () -> Unit,
    /**
     * Entradas que el llamador sabe aplicar de verdad. Si se indica, el catálogo solo ofrece esas: una entrada
     * seleccionable nunca termina en un callback que no la resuelve (p. ej. `native:machine-muscle` sin plantilla
     * ni protocolo). Vacío = sin filtro (fixtures y llamadas antiguas).
     */
    applicableIds: Set<String> = emptySet(),
) {
    val entries = remember(applicableIds) {
        libraryOrder(
            PersonalizedPlanCatalog.listedEntries()
                .filter {
                    it.publication == PublicationState.PUBLISHED &&
                        isLibraryFamilyEntry(it) &&
                        (applicableIds.isEmpty() || it.id in applicableIds)
                }
        )
    }

    val hazeState = remember { HazeState() }

    var selectedEntry by remember { mutableStateOf<CatalogEntry?>(null) }
    var seg by rememberSaveable { mutableStateOf("todo") }
    var query by rememberSaveable { mutableStateOf("") }
    var filterLevel by rememberSaveable { mutableStateOf<String?>(null) }
    var filterDays by rememberSaveable { mutableStateOf<Int?>(null) }
    var filterDuration by rememberSaveable { mutableStateOf<String?>(null) }
    var filterObjective by rememberSaveable { mutableStateOf<String?>(null) }
    var openTray by rememberSaveable { mutableStateOf<String?>(null) }

    var applyMode by rememberSaveable { mutableStateOf(if (replace) "replace" else "copy") }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var applying by rememberSaveable { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var toastIsError by remember { mutableStateOf(false) }
    var toastUndoSnapshot by remember { mutableStateOf<String?>(null) }

    // Aplica EXACTAMENTE la entrada confirmada y solo anuncia el resultado cuando el motor responde. No hay
    // retardo artificial: el usuario puede volver atrás sin que se aplique otra entrada ni se muestre un éxito falso.
    fun applyConfirmed(confirmed: CatalogEntry, replacing: Boolean) {
        if (applying) return
        applying = true
        val result: (Boolean, String?) -> Unit = { success, snapshotId ->
            applying = false
            toastIsError = !success
            if (success) {
                toastText = if (replacing) "Plan sustituido" else "Copia creada"
                toastUndoSnapshot = snapshotId
            } else {
                toastText = "No se pudo aplicar. Revisa el aviso e inténtalo de nuevo."
                toastUndoSnapshot = null
            }
        }
        if (confirmed.template != null) onPickTemplate(confirmed.id, replacing, result) else onPickMethod(confirmed.id, replacing, result)
    }

    // Sincroniza replace con el llamador
    LaunchedEffect(applyMode) {
        onReplaceChange(applyMode == "replace")
    }

    // Sheet container (.ct-sheet: ct-S08, ct-A02)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    KpknSheet(onDismissRequest = { if (!applying) onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(760.dp)
                .background(CtBg, sheetShape)
                /* ct-S08: .ct-sheet -> preset hoja */
                .kpknShadow(KpknShadow.Hoja, sheetShape)
                .clip(sheetShape)
        ) {
            // Animación de entrada de contenido nav (.ct-sheet.ct-nav: ct-A01)
            AnimatedContent(
                targetState = selectedEntry,
                transitionSpec = {
                    if (targetState != null) {
                        /* ct-A01: .ct-sheet.ct-nav > .ct-fscroll -> preset entrada-derecha, dur 240 */
                        (slideInHorizontally(tween(240, easing = CtStandardCurve)) { it } + fadeIn(tween(240)))
                            .togetherWith(slideOutHorizontally(tween(240, easing = CtStandardCurve)) { -it / 3 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(tween(240, easing = CtStandardCurve)) { -it / 3 } + fadeIn(tween(240)))
                            .togetherWith(slideOutHorizontally(tween(240, easing = CtStandardCurve)) { it } + fadeOut(tween(180)))
                    }
                },
                label = "CatalogNav"
            ) { entry ->
                if (entry == null) {
                    CatalogHome(
                        entries = entries,
                        seg = seg,
                        onSegChange = { seg = it; openTray = null },
                        query = query,
                        onQueryChange = { query = it },
                        filterLevel = filterLevel,
                        onFilterLevelChange = { filterLevel = it; openTray = null },
                        filterDays = filterDays,
                        onFilterDaysChange = { filterDays = it; openTray = null },
                        filterDuration = filterDuration,
                        onFilterDurationChange = { filterDuration = it; openTray = null },
                        filterObjective = filterObjective,
                        onFilterObjectiveChange = { filterObjective = it; openTray = null },
                        openTray = openTray,
                        onToggleTray = { openTray = if (openTray == it) null else it },
                        onClearFilters = {
                            query = ""
                            filterLevel = null
                            filterDays = null
                            filterDuration = null
                            filterObjective = null
                            openTray = null
                        },
                        onSelectEntry = { selectedEntry = it },
                        onClose = { if (!applying) onDismiss() },
                        hazeState = hazeState
                    )
                } else {
                    CatalogDetail(
                        entry = entry,
                        applyMode = applyMode,
                        onApplyModeChange = { applyMode = it },
                        applying = applying,
                        onBack = { if (!applying) selectedEntry = null },
                        onApply = {
                            if (applyMode == "replace") {
                                showConfirmDialog = true
                            } else {
                                applyConfirmed(entry, replacing = false)
                            }
                        },
                        hazeState = hazeState
                    )
                }
            }

            // Grab handle (.ct-grab)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 9.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0x66FFFFFF), CircleShape)
            )

            // Confirm Dialog (.ct-dlg: ct-S03). La entrada se captura para no depender de una selección viva.
            val pendingConfirm = selectedEntry
            if (showConfirmDialog && pendingConfirm != null) {
                CatalogConfirmDialog(
                    programName = pendingConfirm.displayName,
                    targetName = pendingConfirm.shortName,
                    weeksCount = catalogWeeks(pendingConfirm) ?: 0,
                    onConfirm = {
                        // La entrada es la del diálogo: aunque la selección cambie o el usuario vuelva atrás,
                        // se aplica esta y solo esta.
                        showConfirmDialog = false
                        applyConfirmed(pendingConfirm, replacing = true)
                    },
                    onDismiss = { showConfirmDialog = false }
                )
            }

            // Toast (.ct-toast: ct-A04)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 144.dp, start = 12.dp, end = 12.dp)
            ) {
                AnimatedVisibility(
                    visible = toastText != null,
                    /* ct-A04: .ct-toast.in -> preset entrada-sube, dur 320 */
                    enter = slideInVertically(tween(320, easing = CtStandardCurve)) { it } + fadeIn(tween(240)),
                    exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(180))
                ) {
                    CatalogToast(
                        text = toastText ?: "",
                        error = toastIsError,
                        onUndo = toastUndoSnapshot?.let { snapshotId ->
                            {
                                onUndo(snapshotId)
                                toastText = null
                                toastUndoSnapshot = null
                            }
                        },
                        onDismiss = { toastText = null; toastUndoSnapshot = null },
                    )
                }
            }
        }
    }
}

/** Una fase publicada de la receta: su nombre y el tramo real de semanas. */
internal data class CatalogPhase(val label: String, val weeks: String)

internal fun catalogRecipe(entry: CatalogEntry) = entry.recipe ?: entry.template?.recipe

internal fun catalogWeeks(entry: CatalogEntry): Int? =
    catalogRecipe(entry)?.weeks?.size ?: entry.durationWeeks

internal fun catalogDurationText(entry: CatalogEntry): String =
    catalogWeeks(entry)?.let { "$it semanas" } ?: "duración sin publicar"

/** Días por semana solo si el catálogo declara uno exacto; un rango (1..6) no se convierte en un número inventado. */
internal fun catalogExactDays(entry: CatalogEntry): Int? =
    catalogRecipe(entry)?.daysPerWeek?.takeIf { it > 0 }
        ?: entry.supportedFrequencies.last.takeIf { entry.supportedFrequencies.first == entry.supportedFrequencies.last }

internal fun catalogPhaseList(entry: CatalogEntry): List<CatalogPhase> {
    val recipe = catalogRecipe(entry) ?: return emptyList()
    return recipe.weeks.groupBy { it.blockIndex }.toSortedMap().values.map { weeks ->
        val first = weeks.minOf { it.weekNumber }
        val last = weeks.maxOf { it.weekNumber }
        val name = weeks.first().blockName.takeIf { it.isNotBlank() } ?: weeks.first().blockGoal.label
        CatalogPhase(name, if (first == last) "Semana $first" else "Semanas $first–$last")
    }
}

internal fun catalogDeloadWeeks(entry: CatalogEntry): Set<Int> =
    catalogRecipe(entry)?.weeks.orEmpty()
        .filter { it.kind == com.example.kpkn.data.models.WeekExecutionKind.DELOAD || it.blockGoal == com.example.kpkn.data.models.BlockGoal.DELOAD }
        .map { it.weekNumber }
        .toSet()

internal fun catalogLevelLabels(entry: CatalogEntry): List<String> = entry.levels.sortedBy { it.ordinal }.map {
    when (it) {
        CatalogLevel.BEGINNER -> "Principiante"
        CatalogLevel.INTERMEDIATE -> "Intermedio"
        CatalogLevel.ADVANCED -> "Avanzado"
    }
}

internal fun catalogGoalLabels(entry: CatalogEntry): List<String> = entry.references.map {
    when (it) {
        TrainingReference.POWERLIFTING -> "Fuerza"
        TrainingReference.HYPERTROPHY -> "Músculo"
        TrainingReference.POWERBUILDING -> "Fuerza y músculo"
    }
}.distinct()

internal fun catalogEquipmentLabels(entry: CatalogEntry): List<String> = entry.requiredEquipment.mapNotNull { code ->
    when (code) {
        "general_gym" -> "Gimnasio completo"
        "machine" -> "Máquinas"
        "bodyweight" -> "Peso corporal"
        "support" -> "Con apoyo"
        "pull_up_bar" -> "Barra de dominadas"
        "dumbbells" -> "Mancuernas"
        "barbell" -> "Barra"
        else -> null
    }
}.distinct()

/** Portada de líneas helper para CatalogEntry */
internal fun catalogCoverSpecFor(entry: CatalogEntry): LineCoverSpec {
    val bg = when {
        entry.references.contains(TrainingReference.POWERLIFTING) -> LineCoverBackground.CARBON
        entry.references.contains(TrainingReference.HYPERTROPHY) -> LineCoverBackground.MUSGO
        entry.references.contains(TrainingReference.POWERBUILDING) -> LineCoverBackground.NOCHE
        else -> LineCoverBackground.GRAFITO
    }
    val st = when {
        entry.kind == PlanKind.ESTRUCTURA -> LineCoverStyle.RELIEVE
        (entry.durationWeeks ?: 1) > 1 -> LineCoverStyle.PERFIL
        else -> LineCoverStyle.ONDAS
    }
    val hash = entry.id.hashCode() and 0x7FFFFFFF
    return LineCoverSpec(
        style = st,
        background = bg,
        seed = (hash % 100) + 4,
        weekLoads = listOf(0.62f, 0.7f, 0.8f, 0.66f, 0.74f, 0.38f),
        currentWeek = 2,
        weeks = catalogWeeks(entry) ?: 1
    )
}

/* ──────────────────────────────────────────────────────────────────────────
 * Vista Principal: Inicio del Catálogo (.ct-head, .ct-body, .ct-hs, .ct-row)
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun CatalogHome(
    entries: List<CatalogEntry>,
    seg: String,
    onSegChange: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    filterLevel: String?,
    onFilterLevelChange: (String?) -> Unit,
    filterDays: Int?,
    onFilterDaysChange: (Int?) -> Unit,
    filterDuration: String?,
    onFilterDurationChange: (String?) -> Unit,
    filterObjective: String?,
    onFilterObjectiveChange: (String?) -> Unit,
    openTray: String?,
    onToggleTray: (String) -> Unit,
    onClearFilters: () -> Unit,
    onSelectEntry: (CatalogEntry) -> Unit,
    onClose: () -> Unit,
    hazeState: HazeState
) {
    val filtered = remember(entries, seg, query, filterLevel, filterDays, filterDuration, filterObjective) {
        entries.filter { e ->
            val matchSeg = when (seg) {
                "plantillas" -> e.template != null || e.kind == PlanKind.ESTRUCTURA
                "metodos" -> e.template == null && e.kind != PlanKind.ESTRUCTURA
                else -> true
            }
            val matchLevel = filterLevel == null || when (filterLevel) {
                "B" -> e.levels.contains(CatalogLevel.BEGINNER)
                "I" -> e.levels.contains(CatalogLevel.INTERMEDIATE)
                "A" -> e.levels.contains(CatalogLevel.ADVANCED)
                else -> true
            }
            val matchDays = filterDays == null || (filterDays in e.supportedFrequencies)
            val matchDur = filterDuration == null || when (filterDuration) {
                "r" -> e.duration == CatalogDuration.REPEATING_WEEK || e.duration == CatalogDuration.REPEATING_CYCLE
                "c" -> (e.durationWeeks ?: 1) <= 8
                "m" -> (e.durationWeeks ?: 1) in 9..12
                "l" -> (e.durationWeeks ?: 1) >= 13
                else -> true
            }
            val matchObj = filterObjective == null || when (filterObjective) {
                "f" -> e.references.contains(TrainingReference.POWERLIFTING)
                "m" -> e.references.contains(TrainingReference.HYPERTROPHY)
                "fm" -> e.references.contains(TrainingReference.POWERBUILDING)
                else -> true
            }
            val q = query.trim().lowercase()
            val matchQ = q.isEmpty() || e.displayName.lowercase().contains(q) ||
                    e.summary.lowercase().contains(q) ||
                    (e.sourceAuthor?.lowercase()?.contains(q) == true)

            matchSeg && matchLevel && matchDays && matchDur && matchObj && matchQ
        }
    }

    val isFiltering = query.isNotBlank() || filterLevel != null || filterDays != null || filterDuration != null || filterObjective != null

    Column(modifier = Modifier.fillMaxSize()) {
        // Cabecera fija (.ct-head)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CtBg)
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CATÁLOGO DE PROGRAMAS",
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.4.sp),
                        color = CtFaint
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aplicar plantilla o método",
                        style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                        color = CtInk
                    )
                }
                // Botón cerrar (.ct-x)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0x14FFFFFF), CircleShape)
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = KpknLineIcons.Close, contentDescription = "Cerrar", tint = CtInk, modifier = Modifier.size(20.dp))
                }
            }

            // Buscador (.ct-search)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(48.dp)
                    .background(Color(0xFF0F0F10), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = KpknLineIcons.Search, contentDescription = null, tint = CtFaint, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = WizardFonts.body, fontSize = 15.sp, color = CtInk),
                    cursorBrush = SolidColor(CtInk),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Buscar por nombre, autor o levantamiento…",
                                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 14.sp, color = CtFaint)
                            )
                        }
                        innerTextField()
                    }
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = KpknLineIcons.Close, contentDescription = "Borrar", tint = CtMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Selector segmentado (.ct-seg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(Color(0xFF0F0F10), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val segs = listOf("todo" to "Todo", "plantillas" to "Plantillas", "metodos" to "Métodos")
                segs.forEach { (key, label) ->
                    val isSel = seg == key
                    val count = remember(entries, key) {
                        entries.count { e ->
                            when (key) {
                                "plantillas" -> e.template != null || e.kind == PlanKind.ESTRUCTURA
                                "metodos" -> e.template == null && e.kind != PlanKind.ESTRUCTURA
                                else -> true
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(if (isSel) Color(0xFF2B2B2E) else Color.Transparent, RoundedCornerShape(20.dp))
                            .clickable { onSegChange(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = label,
                                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                color = if (isSel) CtInk else CtFaint
                            )
                            Text(
                                text = count.toString(),
                                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.5.sp),
                                color = (if (isSel) CtInk else CtFaint).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Fila de filtros chips (.ct-fr)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filtro Nivel (.ct-fc: ct-S04)
                CatalogFilterChip(
                    label = if (filterLevel != null) when (filterLevel) {
                        "B" -> "Principiante"
                        "I" -> "Intermedio"
                        else -> "Avanzado"
                    } else "Nivel",
                    isActive = filterLevel != null,
                    isOpen = openTray == "lv",
                    onClick = { onToggleTray("lv") }
                )
                // Filtro Días
                CatalogFilterChip(
                    label = if (filterDays != null) "$filterDays días" else "Días",
                    isActive = filterDays != null,
                    isOpen = openTray == "d",
                    onClick = { onToggleTray("d") }
                )
                // Filtro Duración
                CatalogFilterChip(
                    label = if (filterDuration != null) when (filterDuration) {
                        "r" -> "Ciclo"
                        "c" -> "Corto"
                        "m" -> "Medio"
                        else -> "Largo"
                    } else "Duración",
                    isActive = filterDuration != null,
                    isOpen = openTray == "u",
                    onClick = { onToggleTray("u") }
                )
                // Filtro Objetivo
                CatalogFilterChip(
                    label = if (filterObjective != null) when (filterObjective) {
                        "f" -> "Fuerza"
                        "m" -> "Músculo"
                        else -> "Fuerza y músculo"
                    } else "Objetivo",
                    isActive = filterObjective != null,
                    isOpen = openTray == "o",
                    onClick = { onToggleTray("o") }
                )

                if (isFiltering) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .padding(horizontal = 8.dp)
                            .clickable(onClick = onClearFilters),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Borrar",
                            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                            color = CtMuted
                        )
                    }
                }
            }

            // Bandeja de opciones expandida (.ct-tray)
            if (openTray != null) {
                CatalogOptionsTray(
                    tray = openTray,
                    filterLevel = filterLevel,
                    onSelectLevel = onFilterLevelChange,
                    filterDays = filterDays,
                    onSelectDays = onFilterDaysChange,
                    filterDuration = filterDuration,
                    onSelectDuration = onFilterDurationChange,
                    filterObjective = filterObjective,
                    onSelectObjective = onFilterObjectiveChange
                )
            }
        }

        // Cuerpo con scroll (.ct-body)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!isFiltering) {
                // Sección Destacados (.ct-sec)
                Text(
                    text = "DESTACADOS",
                    style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp),
                    color = CtFaint,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // Carrusel horizontal (.ct-hs)
                val featuredIds = listOf("native:strength-foundation-v2", "powerbuild-16-4", "body-12-3")
                val featuredList = remember(entries) {
                    entries.filter { it.id in featuredIds || it.sourceId in featuredIds }.take(3).ifEmpty { entries.take(3) }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(featuredList) { feat ->
                        FeaturedPlanCard(
                            entry = feat,
                            onClick = { onSelectEntry(feat) }
                        )
                    }
                }

                // Sección Plantillas completas
                val templatesList = remember(entries) { entries.filter { it.template != null || it.kind == PlanKind.ESTRUCTURA } }
                if (templatesList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PLANTILLAS COMPLETAS",
                            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp),
                            color = CtFaint
                        )
                        Text(text = templatesList.size.toString(), style = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.sp), color = CtFaint)
                    }

                    templatesList.forEach { item ->
                        CatalogRow(entry = item, onClick = { onSelectEntry(item) })
                    }
                }

                // Sección Métodos de progresión
                val methodsList = remember(entries) { entries.filter { it.template == null && it.kind != PlanKind.ESTRUCTURA } }
                if (methodsList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MÉTODOS DE PROGRESIÓN",
                            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp),
                            color = CtFaint
                        )
                        Text(text = methodsList.size.toString(), style = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.sp), color = CtFaint)
                    }

                    methodsList.forEach { item ->
                        CatalogRow(entry = item, onClick = { onSelectEntry(item) })
                    }
                }
            } else {
                // Resultados filtrados
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filtered.size} resultados",
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = CtMuted
                    )
                    Text(
                        text = "Borrar filtros",
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = CtInk,
                        modifier = Modifier.clickable(onClick = onClearFilters)
                    )
                }

                if (filtered.isEmpty()) {
                    // Estado vacío (.ct-empty)
                    CatalogEmptyState(query = query, onClear = onClearFilters)
                } else {
                    filtered.forEach { item ->
                        CatalogRow(entry = item, onClick = { onSelectEntry(item) })
                    }
                }
            }
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

/** Chip de filtro con preset de sombra (.ct-fc: ct-S04) */
@Composable
private fun CatalogFilterChip(
    label: String,
    isActive: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    val chipShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .background(if (isActive) CtInk else Color(0x14FFFFFF), chipShape)
            /* ct-S04: .ct-fc.open -> preset interior-crema-1p5-1 */
            .then(if (isOpen) Modifier.kpknInset(KpknShadow.InteriorCrema1p51, chipShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp),
                color = if (isActive) Color(0xFF111111) else CtMuted
            )
            Icon(
                imageVector = if (isOpen) KpknLineIcons.ChevronLeft else KpknLineIcons.ChevronDown,
                contentDescription = null,
                tint = if (isActive) Color(0xFF111111) else CtMuted,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/** Bandeja de opciones de filtro (.ct-tray, .ct-oc) */
@Composable
private fun CatalogOptionsTray(
    tray: String,
    filterLevel: String?,
    onSelectLevel: (String?) -> Unit,
    filterDays: Int?,
    onSelectDays: (Int?) -> Unit,
    filterDuration: String?,
    onSelectDuration: (String?) -> Unit,
    filterObjective: String?,
    onSelectObjective: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (tray) {
            "lv" -> {
                listOf("B" to "Principiante", "I" to "Intermedio", "A" to "Avanzado").forEach { (key, name) ->
                    val sel = filterLevel == key
                    TrayOptionChip(label = name, selected = sel, onClick = { onSelectLevel(if (sel) null else key) })
                }
            }
            "d" -> {
                (1..6).forEach { d ->
                    val sel = filterDays == d
                    TrayOptionChip(label = "$d ${if (d == 1) "día" else "días"}", selected = sel, onClick = { onSelectDays(if (sel) null else d) })
                }
            }
            "u" -> {
                listOf("r" to "Ciclo recurrente", "c" to "Corto (≤8 sem)", "m" to "Medio (9-12 sem)", "l" to "Largo (13+ sem)").forEach { (key, name) ->
                    val sel = filterDuration == key
                    TrayOptionChip(label = name, selected = sel, onClick = { onSelectDuration(if (sel) null else key) })
                }
            }
            "o" -> {
                listOf("f" to "Fuerza", "m" to "Músculo", "fm" to "Fuerza y músculo").forEach { (key, name) ->
                    val sel = filterObjective == key
                    TrayOptionChip(label = name, selected = sel, onClick = { onSelectObjective(if (sel) null else key) })
                }
            }
        }
    }
}

@Composable
private fun TrayOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .background(if (selected) Color(0x33F2EEE6) else Color.Transparent, shape)
            .border(1.dp, if (selected) Color.Transparent else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp),
            color = CtInk
        )
    }
}

/** Tarjeta destacada en carrusel (.ct-hc: ct-S07, ct-G02) */
@Composable
private fun FeaturedPlanCard(entry: CatalogEntry, onClick: () -> Unit) {
    val cardShape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .width(260.dp)
            .height(292.dp)
            .background(Color(0xFF111111), cardShape)
            /* ct-S07: .ct-hc -> preset borde-interior */
            .kpknInset(KpknShadow.BordeInterior, cardShape)
            .clip(cardShape)
            .clickable(onClick = onClick)
    ) {
        // Portada generativa de fondo (.ct-cv)
        LineCoverArt(
            spec = catalogCoverSpecFor(entry),
            modifier = Modifier.fillMaxSize()
        )

        // Degradado oscuro superpuesto (.ct-hc::after: ct-G02)
        Box(
            modifier = Modifier
                .fillMaxSize()
                /* ct-G02: .ct-hc::after -> preset velo-negro-4 */
                .kpknGradient(KpknGradient.VeloNegro4, cardShape)
        )

        // Rótulo inferior (.ct-hct)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            val eyebrow = (if (entry.kind == PlanKind.ESTRUCTURA) "ESTRUCTURA" else "PROGRAMA") + " · " + (entry.sourceAuthor ?: "KPKN")
            Text(
                text = eyebrow,
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.3.sp),
                color = CtMus
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.displayName,
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.sp),
                color = CtInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${entry.supportedFrequencies.first}–${entry.supportedFrequencies.last} días · ${catalogDurationText(entry)}",
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp),
                color = CtMuted
            )
        }
    }
}

/** Fila individual del catálogo (.ct-row, .ct-cv: ct-S02) */
@Composable
private fun CatalogRow(entry: CatalogEntry, onClick: () -> Unit) {
    val coverShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Portada (.ct-cv: ct-S02)
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 78.dp)
                .background(Color(0xFF111111), coverShape)
                /* ct-S02: .ct-cv -> preset borde-interior */
                .kpknInset(KpknShadow.BordeInterior, coverShape)
                .clip(coverShape)
        ) {
            LineCoverArt(
                spec = catalogCoverSpecFor(entry),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Contenido (.ct-rt)
        Column(modifier = Modifier.weight(1f)) {
            val eyebrow = if (entry.kind == PlanKind.ESTRUCTURA) "ESTRUCTURA" else (entry.sourceAuthor ?: "KPKN")
            Text(
                text = eyebrow.uppercase(),
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.3.sp),
                color = CtFaint
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.displayName,
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.sp),
                color = CtInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${entry.supportedFrequencies.first}–${entry.supportedFrequencies.last} días · ${catalogDurationText(entry)}",
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp),
                color = CtMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.summary,
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp, lineHeight = 16.sp),
                color = CtFaint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            tint = Color(0x66FFFFFF),
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Estado vacío cuando no hay resultados (.ct-empty) */
@Composable
private fun CatalogEmptyState(query: String, onClear: () -> Unit) {
    val coverShape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 150.dp)
                .background(Color(0xFF111111), coverShape)
                .kpknInset(KpknShadow.BordeInterior, coverShape)
                .clip(coverShape)
        ) {
            LineCoverArt(
                spec = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.GRAFITO, seed = 19),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (query.isNotBlank()) "Nada con «$query»" else "Nada con esos filtros",
            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp),
            color = CtInk,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (query.isNotBlank()) "Prueba con otro nombre o con un autor, como Wendler, Sheiko o Smolov."
            else "Quita algún filtro para ver más plantillas y métodos.",
            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 14.sp, lineHeight = 20.sp),
            color = CtMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .background(Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .clickable(onClick = onClear)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Borrar búsqueda y filtros",
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
                color = CtInk
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────────
 * Vista Detalle: Ficha del Catálogo (.ct-fscroll, .ct-hero, .ct-fb, .ct-foot)
 * ────────────────────────────────────────────────────────────────────────── */
@Composable
private fun CatalogDetail(
    entry: CatalogEntry,
    applyMode: String,
    onApplyModeChange: (String) -> Unit,
    applying: Boolean,
    onBack: () -> Unit,
    onApply: () -> Unit,
    hazeState: HazeState
) {
    val scrollState = rememberScrollState()
    val isScrolledPastHero = scrollState.value > 150

    /* ct-T01: .ct-vtop -> transición de opacidad 0.18s ease */
    val topVeilAlpha by animateFloatAsState(
        targetValue = if (isScrolledPastHero) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "TopVeilOpacity"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Hero (.ct-hero: ct-G03)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF111111))
            ) {
                // Portada a sangre
                LineCoverArt(
                    spec = catalogCoverSpecFor(entry),
                    modifier = Modifier.fillMaxSize()
                )

                // Velo degradado inferior (.ct-hero::after: ct-G03)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .align(Alignment.BottomCenter)
                        /* ct-G03: .ct-hero::after -> preset velo-hoja-largo */
                        .kpknGradient(KpknGradient.VeloHojaLargo, RectangleShape)
                )

                // Rótulo del hero (.ct-ht)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val kindLabel = if (entry.template != null) "PLANTILLA" else "MÉTODO"
                    Text(
                        text = "$kindLabel · ${entry.sourceAuthor ?: "KPKN"}",
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.3.sp),
                        color = CtMus
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.displayName,
                        style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 30.sp),
                        color = CtInk
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${entry.supportedFrequencies.first}–${entry.supportedFrequencies.last} días · ${catalogDurationText(entry)}",
                        style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp),
                        color = CtMuted
                    )
                }
            }

            // Cuerpo de la ficha (.ct-fb)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Resumen
                Text(
                    text = entry.summary,
                    style = TextStyle(fontFamily = WizardFonts.body, fontSize = 15.sp, lineHeight = 23.sp),
                    color = CtMuted
                )

                // Aviso si es especialización o complemento (.ct-warn)
                if (entry.kind == PlanKind.ESPECIALIZACION || entry.kind == PlanKind.COMPLEMENTO) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .background(Color(0x14F7CF73), RoundedCornerShape(18.dp))
                            .border(1.dp, Color(0x33F7CF73), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = KpknLineIcons.Alert, contentDescription = null, tint = CtEne, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (entry.kind == PlanKind.COMPLEMENTO)
                                "Ojo: es un complemento. Aplicado aquí no se suma a tu plan: tu programa pasaría a ser solo esta sesión. Como copia, tu programa no se toca."
                            else
                                "Ojo: es de un solo levantamiento. Aplicado aquí, tu programa pasaría a ser solo este levantamiento. Como copia, tu programa no se toca.",
                            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, lineHeight = 19.sp),
                            color = CtMuted
                        )
                    }
                }

                // Módulo Estructura / Qué trae (.ct-mod, .ct-stats, .ct-grid: ct-S05, ct-S06)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (entry.template != null) "Qué trae" else "Estructura",
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 19.sp),
                    color = CtInk
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CtModuleBg, RoundedCornerShape(22.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    // Estadísticas (.ct-stats): solo lo que el catálogo y la receta declaran; sin dato, «—».
                    val weeks = catalogWeeks(entry)
                    val exactDays = catalogExactDays(entry)
                    val phases = catalogPhaseList(entry)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem(value = weeks?.toString() ?: "—", label = "Semanas")
                        StatItem(
                            value = exactDays?.toString()
                                ?: "${entry.supportedFrequencies.first}–${entry.supportedFrequencies.last}",
                            label = "Días/sem",
                        )
                        StatItem(value = if (phases.isEmpty()) "—" else phases.size.toString(), label = "Fases")
                        StatItem(
                            value = if (weeks != null && exactDays != null) "${weeks * exactDays}" else "—",
                            label = "Sesiones",
                        )
                    }

                    // Cuadrícula visual de sesiones (.ct-grid: ct-S05, ct-S06).
                    Spacer(modifier = Modifier.height(16.dp))
                    CatalogSessionGrid(
                        weeksCount = weeks,
                        daysCount = exactDays,
                        deloadWeeks = catalogDeloadWeeks(entry),
                    )

                    // Fases / Bloques (.ct-bl): nombres y tramos de la receta; sin receta no se inventan.
                    Spacer(modifier = Modifier.height(14.dp))
                    CatalogPhaseList(phases = phases)
                }

                // Sección Para quién (.ct-chips)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Para quién",
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 19.sp),
                    color = CtInk
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val chips = (catalogLevelLabels(entry) + catalogGoalLabels(entry) + catalogEquipmentLabels(entry)).distinct()
                    if (chips.isEmpty()) {
                        CatalogChipItem(label = "Sin datos publicados")
                    } else {
                        chips.forEach { CatalogChipItem(label = it) }
                    }
                }

                // Sección Aplicar (.ct-opts, .ct-strip: ct-S09, ct-S10)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Aplicar",
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 19.sp),
                    color = CtInk
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CtModuleBg, RoundedCornerShape(22.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    // Opción: Crear una copia
                    CatalogRadioOption(
                        title = "Crear una copia",
                        badge = "MÁS SEGURO",
                        description = "Tu programa no cambia. Se crea una copia como borrador y se abre.",
                        selected = applyMode == "copy",
                        onClick = { onApplyModeChange("copy") }
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x1AFFFFFF)))
                    // Opción: Sustituir mi programa
                    CatalogRadioOption(
                        title = "Sustituir mi programa",
                        badge = null,
                        description = "Reemplaza las semanas y sus sesiones. Antes guardamos una versión.",
                        selected = applyMode == "replace",
                        onClick = { onApplyModeChange("replace") }
                    )
                }

                // Tira de previsualización (.ct-pv, .ct-strip: ct-S09, ct-S10)
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CtModuleBg, RoundedCornerShape(22.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (applyMode == "copy") "Copia nueva" else "Después",
                            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                            color = CtInk
                        )
                        Text(
                            text = catalogWeeks(entry)?.let { "$it semanas" } ?: "Duración sin publicar",
                            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp),
                            color = CtFaint
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Strip preview (.ct-strip: ct-S09, ct-S10)
                    catalogWeeks(entry)?.let { weeks ->
                        CatalogStripPreview(
                            weeksCount = weeks,
                            isReplace = applyMode == "replace",
                            deloadWeeks = catalogDeloadWeeks(entry),
                        )
                    }
                }

                // Atribución (.ct-attr)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = entry.attributionLine ?: "Plan propio de KPKN.",
                    style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = CtFaint
                )

                Spacer(modifier = Modifier.height(90.dp))
            }
        }

        // Velo superior que aparece al hacer scroll (.ct-vtop: ct-G04, ct-T01)
        if (topVeilAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    /* ct-G04: .ct-vtop -> preset velo-hoja-sale */
                    .kpknGradient(KpknGradient.VeloHojaSale, RectangleShape)
                    .padding(start = 66.dp, end = 16.dp, top = 22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = entry.displayName,
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = CtInk.copy(alpha = topVeilAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Botón volver (.ct-back: ct-S01, ct-V01)
        Box(
            modifier = Modifier
                .padding(start = 12.dp, top = 22.dp)
                .size(44.dp)
                .background(Color(0xCC000000), CircleShape)
                /* ct-V01: .ct-back -> preset velo-desenfoque (12dp) */
                .kpknGlassPreset(hazeState, KpknGlassPreset.VeloDesenfoque, CircleShape)
                /* ct-S01: .ct-back -> preset interior-blanco-1-tenue */
                .kpknInset(KpknShadow.InteriorBlanco1Tenue, CircleShape)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", tint = CtInk, modifier = Modifier.size(20.dp))
        }

        // Pie fijo con botón (.ct-foot: ct-G01)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                /* ct-G01: .ct-foot -> preset velo-hoja */
                .kpknGradient(KpknGradient.VeloHoja, RectangleShape)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (applyMode == "replace") CtDanger else CtInk,
                        RoundedCornerShape(26.dp)
                    )
                    .clickable(enabled = !applying, onClick = onApply),
                contentAlignment = Alignment.Center
            ) {
                if (applying) {
                    // Spinner giratorio (.ct-spin: ct-A03)
                    CatalogSpinner()
                } else {
                    Text(
                        text = if (applyMode == "replace") "Sustituir mi programa…"
                        else "Crear copia con este plan",
                        style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        color = if (applyMode == "replace") Color.White else Color(0xFF111111)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
            color = CtInk
        )
        Text(
            text = label,
            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.5.sp),
            color = CtFaint
        )
    }
}

/** Cuadrícula de sesiones con píldoras (.ct-grid: ct-S05, ct-S06). Sin semanas/días publicados, no se inventa. */
@Composable
private fun CatalogSessionGrid(weeksCount: Int?, daysCount: Int?, deloadWeeks: Set<Int>) {
    if (weeksCount == null || daysCount == null || weeksCount <= 0 || daysCount <= 0) {
        Text(
            text = "La estructura de semanas no está publicada para este plan.",
            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp),
            color = CtFaint,
        )
        return
    }
    val pillShape = RoundedCornerShape(3.dp)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until daysCount) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until weeksCount) {
                    val isDeload = (col + 1) in deloadWeeks
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(11.dp)
                            .background(if (isDeload) Color.Transparent else CtMus, pillShape)
                            /* ct-S05: .ct-grid i.dl -> preset interior-zona-1p5 */
                            .then(if (isDeload) Modifier.kpknInset(KpknShadow.InteriorZona1p5, pillShape, color = CtDel) else Modifier)
                    )
                }
            }
        }
    }
}

/** Tira visual de semanas (.ct-strip: ct-S09, ct-S10): descargas reales, sin celdas vacías inventadas. */
@Composable
private fun CatalogStripPreview(weeksCount: Int, isReplace: Boolean, deloadWeeks: Set<Int>) {
    val stripShape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until weeksCount) {
            val isDeload = (i + 1) in deloadWeeks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (isDeload) Color.Transparent else if (isReplace) CtMus.copy(alpha = 0.82f) else CtMus, stripShape)
                    /* ct-S09: .ct-strip i.dl -> preset interior-zona-1p5 */
                    .then(if (isDeload) Modifier.kpknInset(KpknShadow.InteriorZona1p5, stripShape, color = CtDel) else Modifier)
            )
        }
    }
}

@Composable
private fun CatalogPhaseList(phases: List<CatalogPhase>) {
    if (phases.isEmpty()) {
        Text(
            text = "Las fases no están publicadas para este plan.",
            style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp),
            color = CtFaint,
        )
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        phases.forEach { phase ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(CtMus, RoundedCornerShape(3.dp)))
                    Text(text = phase.label, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.5.sp), color = CtInk)
                }
                Text(text = phase.weeks, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp), color = CtFaint)
            }
        }
    }
}

@Composable
private fun CatalogChipItem(label: String) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp), color = CtMuted)
    }
}

@Composable
private fun CatalogRadioOption(
    title: String,
    badge: String?,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Color(0x14FFFFFF) else Color.Transparent)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Radio circle
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .border(2.dp, if (selected) CtInk else Color(0x66FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(10.dp).background(CtInk, CircleShape))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = CtInk
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x2643D18C), RoundedCornerShape(7.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badge,
                            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
                            color = CtOk
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.5.sp, lineHeight = 17.sp),
                color = CtMuted
            )
        }
    }
}

/** Diálogo modal de confirmación (.ct-dlg: ct-S03) */
@Composable
private fun CatalogConfirmDialog(
    programName: String,
    targetName: String,
    weeksCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogShape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .background(Color(0xFF242427), dialogShape)
                /* ct-S03: .ct-dlg -> preset dialogo-1 */
                .kpknShadow(KpknShadow.Dialogo1, dialogShape)
                .clickable(enabled = false) {}
                .padding(22.dp)
        ) {
            Text(
                text = "¿Sustituir «$programName»?",
                style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp),
                color = CtInk
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (weeksCount > 0) {
                    "Sus semanas se reemplazan por las $weeksCount de «$targetName»."
                } else {
                    "Sus semanas se reemplazan por la estructura publicada de «$targetName»."
                },
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 14.sp, lineHeight = 21.sp),
                color = CtMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 4 puntos clave
            ConfirmPoint(icon = KpknLineIcons.History, text = "Guardamos antes una versión de seguridad.", highlight = true)
            ConfirmPoint(icon = KpknLineIcons.Repeat, text = "Se reinician fechas clave y pausas del calendario.")
            ConfirmPoint(icon = KpknLineIcons.Check, text = "Tu historial de sesiones hechas no se toca.")

            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(CtDanger, RoundedCornerShape(24.dp))
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sustituir mi programa",
                    style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancelar",
                    style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = CtMuted
                )
            }
        }
    }
}

@Composable
private fun ConfirmPoint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (highlight) CtOk else CtFaint, modifier = Modifier.size(18.dp))
        Text(text = text, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp), color = if (highlight) CtInk else CtMuted)
    }
}

/** Spinner de carga (.ct-spin: ct-A03) */
@Composable
private fun CatalogSpinner() {
    val transition = rememberInfiniteTransition(label = "CatalogSpin")
    /* ct-A03: .ct-spin -> preset giro · curva lineal · dur 800 */
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "CatalogSpinRot"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation)
                .border(2.5.dp, Color(0x33111111), CircleShape)
        )
        Text(
            text = "Aplicando…",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = Color(0xFF111111)
        )
    }
}

/** Toast de confirmación (.ct-toast: ct-A04) */
@Composable
private fun CatalogToast(text: String, error: Boolean, onUndo: (() -> Unit)?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xFF242427), RoundedCornerShape(30.dp))
            .kpknShadow(KpknShadow.IslaVidrio, RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (error) Color(0x33E05252) else Color(0x3343D18C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (error) KpknLineIcons.Alert else KpknLineIcons.Check,
                    contentDescription = null,
                    tint = if (error) CtDanger else CtOk,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = CtInk
            )
        }
        Text(
            text = if (onUndo != null) "Deshacer" else "Cerrar",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
            color = if (onUndo != null) CtMus else CtMuted,
            modifier = Modifier.clickable(onClick = { if (onUndo != null) onUndo() else onDismiss() })
        )
    }
}

/**
 * Contenido de la biblioteca sin la hoja: las plantillas y los métodos que recibe, el interruptor de sustitución y
 * las filas que llaman a su callback. Se usa en pruebas y en llamadas que montan el contenido dentro de otra hoja.
 */
@Composable
internal fun ProgramTemplatesContent(
    templates: List<ProgramLibraryItem>,
    methods: List<ProgramLibraryItem>,
    replace: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    onPickTemplate: (key: String) -> Unit,
    onPickMethod: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Aplicar plantilla o método",
            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = CtInk,
        )
        Text("PLANTILLAS", style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp), color = CtFaint)
        templates.forEach { item ->
            CatalogLibraryRow(item) { onPickTemplate(item.key) }
        }
        Text("MÉTODOS DE PROGRESIÓN", style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp), color = CtFaint)
        methods.forEach { item ->
            CatalogLibraryRow(item) { onPickMethod(item.key) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onReplaceChange(!replace) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Sustituir semanas con sesiones",
                style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                color = CtInk,
            )
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 24.dp)
                    .background(if (replace) CtMus else Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                contentAlignment = if (replace) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(18.dp).background(CtInk, CircleShape))
            }
        }
    }
}

@Composable
private fun CatalogLibraryRow(item: ProgramLibraryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CtModuleBg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp), color = CtInk)
            item.detail?.let { detail ->
                Text(detail, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp), color = CtMuted)
            }
        }
        Icon(KpknLineIcons.ChevronRight, null, tint = CtFaint, modifier = Modifier.size(18.dp))
    }
}
