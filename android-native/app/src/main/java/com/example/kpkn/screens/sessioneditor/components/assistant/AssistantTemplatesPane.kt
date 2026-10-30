@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionPreview
import com.example.kpkn.domain.sessionassistant.SessionTotals
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import kotlinx.coroutines.launch

internal data class AsPartLine(val name: String, val sets: Int, val reps: Int?, val rpe: Double?, val restSeconds: Int?)
internal data class AsPart(val title: String, val lines: List<AsPartLine>)

/** La ficha de una plantilla: portada, descripción, cifras, ejercicios por grupo y volumen estimado por músculo. */
internal data class AsTemplateDetail(
    val card: AsTemplateCard,
    val description: String,
    val parts: List<AsPart>,
    val volume: List<Pair<String, Double>>,
)

internal data class AsMineRow(
    val id: String,
    val name: String,
    val description: String,
    val level: Int,
    val exercises: Int,
    val archived: Boolean,
)

/** La pregunta Reemplazar / Añadir con el resultado de cada una. */
internal data class AsDecisionModel(
    val name: String,
    val replace: SessionTotals,
    val append: SessionTotals,
    val appendOverMinutes: Int?,
    val suggested: SessionTemplateApplyMode = SessionTemplateApplyMode.REPLACE,
)

/** El estado «aplicando» y «aplicada». */
internal data class AsApplyModel(val title: String, val body: String, val done: Boolean, val note: String = "")

internal enum class AsPlaView { LIST, DETAIL, MINE, SAVE, EDIT }

/** Estado del guardado: sin enviar, error de nombre o guardando. */
internal enum class AsSaveState { IDLE, ERROR, SAVING }

private val AsTypeChips = listOf("Piernas", "Torso", "Brazos", "Full body", "Pecho", "Espalda", "Hombros", "Glúteos")

/** Un recuadro de contenido a sangre del módulo padre (`margin: 0 -20px`). */
internal fun Modifier.asBleed(extra: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val bleed = extra.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * bleed, maxWidth = constraints.maxWidth + 2 * bleed))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

/**
 * Pestaña Plantillas (`as-pla-*`): catálogo con filtros, ficha con vista previa, pregunta Reemplazar o añadir, aplicando / aplicada,
 * «Mis plantillas» y guardar. El estado de pantalla vive aquí; los datos y las acciones llegan hoisted.
 */
@Composable
internal fun AssistantTemplatesPane(
    ready: Boolean,
    cards: List<AsTemplateCard>,
    mineRows: List<AsMineRow>,
    corruptCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    detailFor: (String) -> AsTemplateDetail?,
    sessionName: String,
    sessionSummary: String,
    decision: AsDecisionModel?,
    applying: AsApplyModel?,
    onApplyTemplate: (String) -> Unit,
    onConfirmDecision: (SessionTemplateApplyMode) -> Unit,
    onCancelDecision: () -> Unit,
    onFinishApply: () -> Unit,
    onUndoApply: () -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: suspend (id: String, name: String, description: String, level: Int) -> Boolean,
    onSave: suspend (name: String, description: String) -> Boolean,
    modifier: Modifier = Modifier,
    initialView: AsPlaView = AsPlaView.LIST,
    initialDetailId: String? = null,
    initialFilters: AsTemplateFilters = AsTemplateFilters(),
    initialPanelOpen: Boolean = false,
    initialMenuIndex: Int? = null,
    initialSaveState: AsSaveState = AsSaveState.IDLE,
) {
    var view by rememberSaveable { mutableStateOf(initialView) }
    var detailId by rememberSaveable { mutableStateOf(initialDetailId) }
    var type by rememberSaveable { mutableStateOf(initialFilters.type) }
    var level by rememberSaveable { mutableStateOf(initialFilters.level) }
    var duration by rememberSaveable { mutableStateOf(initialFilters.duration) }
    var group by rememberSaveable { mutableStateOf(initialFilters.group) }
    var panelOpen by rememberSaveable { mutableStateOf(initialPanelOpen) }
    var shut by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var menuIndex by rememberSaveable { mutableStateOf(initialMenuIndex) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = AsTemplateFilters(query = query, type = type, level = level, duration = duration, group = group)

    Box(modifier.fillMaxSize()) {
        val footer = view == AsPlaView.DETAIL
        AsScrollPane(Modifier.fillMaxSize(), bottomPadding = if (footer) 128.dp else 36.dp) {
            when (view) {
                AsPlaView.LIST -> CatalogList(
                    ready = ready,
                    cards = cards,
                    filters = filters,
                    panelOpen = panelOpen,
                    shut = shut,
                    mineCount = mineRows.size,
                    onQueryChange = onQueryChange,
                    onType = { type = if (type == it) null else it },
                    onLevel = { level = it },
                    onDuration = { duration = it },
                    onGroup = { group = it },
                    onPanel = { panelOpen = !panelOpen },
                    onToggleSection = { key -> shut = if (key in shut) shut - key else shut + key },
                    onOpen = { id -> detailId = id; view = AsPlaView.DETAIL },
                    onClear = { onQueryChange(""); type = null; level = null; duration = null },
                    onMine = { view = AsPlaView.MINE },
                )
                AsPlaView.DETAIL -> {
                    val detail = detailId?.let(detailFor)
                    if (detail == null) LaunchedEffect(Unit) { view = AsPlaView.LIST } else TemplateDetail(detail) { view = AsPlaView.LIST }
                }
                AsPlaView.MINE -> MineList(
                    rows = mineRows,
                    corruptCount = corruptCount,
                    menuIndex = menuIndex,
                    onBack = { view = AsPlaView.LIST },
                    onSaveSession = { view = AsPlaView.SAVE },
                    onOpenRow = { id -> detailId = id; view = AsPlaView.DETAIL },
                    onMenu = { index -> menuIndex = if (menuIndex == index) null else index },
                    onCloseMenu = { menuIndex = null },
                    onEditRow = { id -> menuIndex = null; editId = id; view = AsPlaView.EDIT },
                    onArchiveRow = { id -> menuIndex = null; onArchive(id) },
                    onRestoreRow = onRestore,
                    onDeleteRow = { id -> menuIndex = null; confirmDelete = id },
                )
                AsPlaView.SAVE -> TemplateForm(
                    title = "Guardar sesión como plantilla",
                    intro = "Guarda «$sessionName» tal como está: $sessionSummary. Podrás aplicarla en cualquier sesión.",
                    submit = "Guardar",
                    submitting = "Guardando…",
                    initialName = "",
                    initialDescription = "",
                    initialLevel = null,
                    initialState = initialSaveState,
                    onBack = { view = AsPlaView.MINE },
                    onSubmit = { name, description, _ -> onSave(name, description) },
                    onDone = { view = AsPlaView.MINE },
                )
                AsPlaView.EDIT -> {
                    val row = mineRows.firstOrNull { it.id == editId }
                    if (row == null) LaunchedEffect(Unit) { view = AsPlaView.MINE } else TemplateForm(
                        title = "Editar plantilla",
                        intro = "Cambia el nombre, la descripción o el nivel de «${row.name}». Sus ejercicios no se tocan.",
                        submit = "Guardar cambios",
                        submitting = "Guardando…",
                        initialName = row.name,
                        initialDescription = row.description,
                        initialLevel = row.level,
                        initialState = AsSaveState.IDLE,
                        onBack = { view = AsPlaView.MINE },
                        onSubmit = { name, description, lvl -> onEdit(row.id, name, description, lvl ?: row.level) },
                        onDone = { view = AsPlaView.MINE },
                    )
                }
            }
        }
        if (footer) {
            // as-G01 as-foot — `linear-gradient(#1b1b1d00, #1b1b1d 30%)` sobre el contenido (preset velo-hoja)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .kpknGradient(KpknGradient.VeloHoja)
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsButton("Aplicar plantilla", Modifier.fillMaxWidth(), leading = AsIcons.Layers, onClick = { detailId?.let(onApplyTemplate) })
            }
        }
        if (decision != null) DecisionOverlay(decision, onConfirmDecision, onCancelDecision)
        confirmDelete?.let { id ->
            val row = mineRows.firstOrNull { it.id == id }
            if (row != null) ConfirmDeleteOverlay(row.name, onConfirm = { confirmDelete = null; onDelete(id) }, onCancel = { confirmDelete = null })
        }
        if (applying != null) ApplyingOverlay(applying, onFinishApply, onUndoApply)
    }
}

// ── Catálogo ─────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatalogList(
    ready: Boolean,
    cards: List<AsTemplateCard>,
    filters: AsTemplateFilters,
    panelOpen: Boolean,
    shut: Set<String>,
    mineCount: Int,
    onQueryChange: (String) -> Unit,
    onType: (String) -> Unit,
    onLevel: (Int?) -> Unit,
    onDuration: (AsDuration?) -> Unit,
    onGroup: (AsGroupMode) -> Unit,
    onPanel: () -> Unit,
    onToggleSection: (String) -> Unit,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onMine: () -> Unit,
) {
    val shown = AsTemplateCatalog.filter(cards, filters)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchBox(filters.query, onQueryChange, Modifier.weight(1f))
        FilterButton(open = panelOpen, count = filters.activeCount, onClick = onPanel)
    }
    // as-X02 as-chips — `mask-image: linear-gradient(90deg, transparent, #000 20px, #000 calc(100% - 28px), transparent)` sobre la fila de tipos (antes del scroll)
    Row(
        Modifier.asBleed(20.dp).asEdgeMask(20.dp, 28.dp).horizontalScrollRow().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsTypeChips.forEach { name -> AsChip(name, on = filters.type == name, onClick = { onType(name) }) }
    }
    if (panelOpen) FilterPanel(filters, onLevel, onDuration, onGroup)
    // `.as-flow[data-r=act]`: vacío también ocupa su hueco de 12 entre los módulos.
    AsFlow {
        if (!panelOpen && filters.level != null) AsChip("Nivel: ${AsLevelNames[filters.level]}", on = true, trailing = AsIcons.Close, onClick = { onLevel(null) })
        if (!panelOpen && filters.duration != null) AsChip("Duración: ${filters.duration.label}", on = true, trailing = AsIcons.Close, onClick = { onDuration(null) })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AsText(AsTemplateCatalog.resultLabel(cards.size, shown.size, filters), 13f, Modifier.weight(1f), weight = FontWeight.SemiBold)
        AsLink("Mis plantillas · $mineCount", leading = AsIcons.Book, onClick = onMine)
    }
    when {
        !ready -> AsText("Cargando plantillas…", 13f, Modifier.fillMaxWidth().padding(top = 24.dp), color = AsColors.faint, align = TextAlign.Center)
        cards.isEmpty() && !filters.anyFilter -> AsEmpty(
            icon = AsIcons.Layers,
            title = "Aún no hay plantillas",
            body = "El catálogo todavía no tiene sesiones disponibles.",
            topPadding = 24.dp,
        )
        shown.isEmpty() -> AsEmpty(
            icon = AsIcons.Search,
            title = "Sin resultados",
            body = if (filters.query.isNotBlank()) "Ninguna sesión coincide con «${filters.query}» y los filtros activos." else "Ninguna sesión coincide con los filtros activos.",
            topPadding = 24.dp,
            action = {
                AsCaption("Prueba a limpiar filtros o cambiar el grupo.")
                AsButton("Limpiar filtros", Modifier.padding(top = 6.dp), small = true, kind = AsButtonKind.GHOST, onClick = onClear)
            },
        )
        else -> AsTemplateCatalog.sections(shown, filters.group).forEach { section ->
            val closed = section.key in shut
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // as-T12 as-sh .c — el chevron gira 0,22 s cubic-bezier(.2,.8,.2,1) · as-X08 as-sec.shut .as-sh .c — −90° al plegar
                val sectionChevron = asFloatState(if (closed) -90f else 0f, 220, AsAnim.Std)
                Row(
                    Modifier.fillMaxWidth().height(44.dp).asClick(role = Role.Button, onClick = { onToggleSection(section.key) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsText(section.key, 15f, weight = FontWeight.Bold, family = AsSyne, letterSpacing = -0.2f)
                    AsText("${section.cards.size}", 12f, color = AsColors.faint, tabular = true)
                    Box(Modifier.weight(1f).height(1.dp).background(AsColors.line))
                    AsIcon(AsIcons.Down, 18.dp, AsColors.dim, Modifier.asRotation(sectionChevron))
                }
                if (!closed) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        section.cards.forEach { card -> TemplateCardRow(card) { onOpen(card.id) } }
                    }
                }
            }
        }
    }
}

private fun Modifier.horizontalScrollRow(): Modifier = composed {
    horizontalScroll(rememberScrollState())
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    // as-focus `.as-in:focus-within` — el borde pasa a #f2eee655 (sin transición en el lienzo)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(AsColors.inset)
            .border(1.dp, if (focused) AsColors.ink.copy(alpha = 0x55 / 255f) else AsColors.white(0x14), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsIcon(AsIcons.Search, 18.dp, AsColors.faint)
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = AsColors.ink, fontFamily = AsInter, fontSize = asSp(15f, 1.15f)),
                cursorBrush = SolidColor(AsColors.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = "Buscar plantilla" },
            )
            if (query.isEmpty()) AsText("Buscar plantilla…", 15f, color = AsColors.faint, cap = 1.15f, maxLines = 1)
        }
    }
}

@Composable
private fun FilterButton(open: Boolean, count: Int, onClick: () -> Unit) {
    val on = open || count > 0
    Box(Modifier.size(48.dp).semantics { contentDescription = "Filtros de sesión" }) {
        Box(
            Modifier.matchParentSize().clip(CircleShape).background(AsColors.inset)
                .border(1.dp, if (on) AsColors.ink.copy(alpha = 0x55 / 255f) else AsColors.white(0x14), CircleShape)
                .asClick(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { AsIcon(AsIcons.Filter, 20.dp, if (on) AsColors.ink else AsColors.muted) }
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).heightIn(min = 18.dp).widthIn(min = 18.dp)
                    .clip(RoundedCornerShape(9.dp)).background(AsColors.ink).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { AsText("$count", 10.5f, color = AsColors.onInk, weight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

@Composable
private fun FilterPanel(filters: AsTemplateFilters, onLevel: (Int?) -> Unit, onDuration: (AsDuration?) -> Unit, onGroup: (AsGroupMode) -> Unit) {
    // as-A07 as-fp.open — entrada de 0,22 s (asIn, dx 0) al abrirse el panel
    Column(
        Modifier.asEnter(AsAnim.FilterPanelIn).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp)).padding(horizontal = 17.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            PanelLabel("Nivel")
            AsSegmented(listOf("Todos") + AsLevelNames, (filters.level ?: -1) + 1, { onLevel(if (it == 0) null else it - 1) })
        }
        Column {
            PanelLabel("Duración")
            AsSegmented(listOf("Todas") + AsDuration.entries.map { it.label }, (filters.duration?.ordinal ?: -1) + 1, { onDuration(if (it == 0) null else AsDuration.entries[it - 1]) })
            AsCaption("Corta ≤45 min · Media 46–75 · Larga ≥76", Modifier.padding(top = 6.dp))
        }
        Column {
            PanelLabel("Agrupar")
            AsSegmented(AsGroupMode.entries.map { it.label }, filters.group.ordinal, { onGroup(AsGroupMode.entries[it]) })
        }
    }
}

@Composable
private fun PanelLabel(text: String) =
    AsText(text, 11f, Modifier.padding(bottom = 8.dp), color = AsColors.faint, weight = FontWeight.SemiBold, letterSpacing = 1.2f, uppercase = true)

@Composable
private fun TemplateCardRow(card: AsTemplateCard, onClick: () -> Unit) {
    // as-T18 as-tc — `transition: background .18s, transform .18s` (ease); `:active` = fondo #161618 (el lienzo no define transform en :active)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val cardBg by asColorState(if (pressed) AsColors.pressed else AsColors.module, 180)
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(22.dp)).background(cardBg)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp))
            .asClick(label = card.name, source = source, onClick = onClick)
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TemplateCover(card, Modifier.width(62.dp).heightIn(min = 84.dp).fillMaxHeight(), 14)
        Column(Modifier.weight(1f).padding(end = 2.dp, top = 2.dp, bottom = 2.dp).align(Alignment.CenterVertically)) {
            AsText(card.name, 15.5f, weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.2f)
            AsText(card.line, 12.5f, Modifier.padding(top = 3.dp), color = AsColors.muted, lineHeight = 1.35f)
            MetaLine(card, Modifier.padding(top = 8.dp))
            AsText(card.muscles, 11.5f, Modifier.padding(top = 5.dp), color = AsColors.faint, cap = 1.12f)
        }
        Box(Modifier.align(Alignment.CenterVertically)) { AsIcon(AsIcons.Forward, 18.dp, AsColors.dim) }
    }
}

@Composable
private fun MetaLine(card: AsTemplateCard, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (card.minutes != null) AsText("${card.minutes} min", 12f, color = AsColors.ink, weight = FontWeight.SemiBold, cap = 1.12f, tabular = true, maxLines = 1)
        AsText("${card.exercises} ej.", 12f, color = AsColors.faint, cap = 1.12f, tabular = true, maxLines = 1)
        AsText("${card.sets} series", 12f, color = AsColors.faint, cap = 1.12f, tabular = true, maxLines = 1)
        LevelMarks(card.level)
    }
}

@Composable
private fun LevelMarks(level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (0..2).forEach { i ->
                Box(Modifier.size(width = 4.dp, height = 9.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= level) AsColors.ink else AsColors.white(0x26)))
            }
        }
        AsText(AsLevelNames[level.coerceIn(0, 2)], 12f, color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.12f, maxLines = 1)
    }
}

/** `.as-cv`: la portada de líneas de la plantilla. */
@Composable
private fun TemplateCover(card: AsTemplateCard, modifier: Modifier, radius: Int) {
    // as-S01 as-cv — `inset 0 0 0 1px #ffffff14` (preset borde-interior): anillo interior bajo la portada, que lo cubre, como en el lienzo
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.clip(shape).background(Color(0xFF111111)).kpknInset(KpknShadow.BordeInterior, shape)) {
        LineCoverArt(spec = card.cover, modifier = Modifier.matchParentSize())
    }
}

// ── Ficha ────────────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BackHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.negMargin(top = (-6).dp, start = (-10).dp).fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AsIconButton(AsIcons.Back, "Volver", size = 22.dp, onClick = onBack)
        AsText(title, 13f, Modifier.weight(1f), color = AsColors.muted, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) trailing()
    }
}

@Composable
private fun TemplateDetail(detail: AsTemplateDetail, onBack: () -> Unit) {
    val card = detail.card
    BackHeader("Plantillas", onBack)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        TemplateCover(card, Modifier.size(width = 84.dp, height = 108.dp), 18)
        Column(Modifier.weight(1f)) {
            AsText(card.name, 21f, Modifier.padding(bottom = 6.dp), weight = FontWeight.ExtraBold, family = AsSyne, cap = 1.15f, lineHeight = 1.08f, letterSpacing = -0.7f)
            AsText(detail.description, 13f, color = AsColors.muted, lineHeight = 1.45f)
        }
    }
    DetailFigures(card)
    AsModule {
        AsModuleHeader("Ejercicios")
        var index = 0
        detail.parts.forEach { part ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsText(part.title, 11f, color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.15f, letterSpacing = 1.2f, uppercase = true)
                Box(Modifier.weight(1f).height(1.dp).background(AsColors.line))
            }
            part.lines.forEach { line ->
                index++
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 6.dp)
                        .drawBehind { drawLine(AsColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsText("$index", 12f, Modifier.width(24.dp), color = AsColors.dim, weight = FontWeight.SemiBold, tabular = true)
                    AsText(line.name, 14f, Modifier.weight(1f), weight = FontWeight.Medium, cap = 1.15f, lineHeight = 1.3f)
                    Column(horizontalAlignment = Alignment.End) {
                        AsText("${line.sets} × ${line.reps ?: "—"}", 12.5f, color = AsColors.muted, cap = 1.12f, tabular = true, maxLines = 1)
                        val extra = listOfNotNull(line.rpe?.let { "RPE ${AssistantSuggestionPreview.number(it)}" }, line.restSeconds?.let { restText(it) })
                        if (extra.isNotEmpty()) AsText(extra.joinToString(" · "), 11f, Modifier.padding(top = 2.dp), color = AsColors.faint, maxLines = 1)
                    }
                }
            }
        }
    }
    if (detail.volume.isNotEmpty()) {
        val mx = detail.volume.first().second * 1.06
        AsModule {
            AsModuleHeader("Volumen estimado") { AsCaption("series efectivas") }
            Column {
                detail.volume.forEach { (name, value) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsText(name, 13f, Modifier.weight(1.15f), color = AsColors.muted, cap = 1.12f, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1.4f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(AsColors.white(0x0d))) {
                            Box(Modifier.fillMaxWidth((value / mx).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(AsColors.ink))
                        }
                        AsText(AssistantSuggestionPreview.number(value), 13f, Modifier.widthIn(min = 28.dp), weight = FontWeight.SemiBold, cap = 1.12f, tabular = true, align = TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
    }
}

internal fun restText(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun DetailFigures(card: AsTemplateCard) {
    val cells = listOf(
        Triple(card.minutes?.toString() ?: "—", if (card.minutes != null) "min" else null, "Duración"),
        Triple("${card.exercises}", null, "Ejerc."),
        Triple("${card.sets}", null, "Series"),
        Triple(AsLevelNames[card.level.coerceIn(0, 2)], null, "Nivel"),
    )
    AsFrRow(
        weights = listOf(1f, 1f, 1f, 1f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(20.dp)).padding(horizontal = 1.dp, vertical = 13.dp),
    ) {
        cells.forEachIndexed { i, (value, unit, label) ->
            Column(
                Modifier
                    .then(
                        if (i == 0) Modifier.padding(start = 14.dp, end = 8.dp)
                        else Modifier.drawBehind { drawLine(AsColors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }.padding(horizontal = 8.dp),
                    ),
            ) {
                if (i == 3) AsText(value, 15f, weight = FontWeight.Bold, lineHeight = 1.25f, noWrap = true)
                else Row {
                    AsFigure(value, 20f, Modifier.alignByBaseline(), cap = 1.2f)
                    if (unit != null) AsText(unit, 11f, Modifier.padding(start = 2.dp).alignByBaseline(), color = AsColors.muted, weight = FontWeight.SemiBold, noWrap = true)
                }
                AsText(label, 10f, Modifier.padding(top = 5.dp), color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.1f, letterSpacing = 0.9f, noWrap = true, uppercase = true)
            }
        }
    }
}

// ── Reemplazar o añadir · aplicando · borrar ─────────────────────────────────────────────────────────────────────

@Composable
private fun BottomOverlay(onScrim: () -> Unit, content: @Composable () -> Unit) {
    // as-A04 as-dec.open — el fondo (y el panel con él) aparece en 0,2 s ease · as-A03 as-dec > div — el panel sube 0,36 s cubic-bezier(.2,.8,.2,1)
    Box(Modifier.fillMaxSize().asEnter(AsAnim.OverlayFade).background(Color.Black.copy(alpha = 0xa6 / 255f)).asClick(role = Role.Button, onClick = onScrim), contentAlignment = Alignment.BottomCenter) {
        // as-S02 as-dec > div — sombra 0 -10px 30px #000000bb (preset pie-sube)
        val panelShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        Column(
            Modifier.fillMaxWidth().asEnter(AsAnim.DecisionRise).kpknShadow(KpknShadow.PieSube, panelShape).clip(panelShape).background(AsColors.decision)
                .asClick(role = Role.Button, onClick = {})
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun DecisionOverlay(model: AsDecisionModel, onConfirm: (SessionTemplateApplyMode) -> Unit, onCancel: () -> Unit) {
    var mode by rememberSaveable(model.name) { mutableStateOf(model.suggested) }
    BottomOverlay(onScrim = onCancel) {
        AsText("Aplicar «${model.name}»", 19f, weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f)
        AsText("La sesión ya tiene ejercicios. ¿Qué quieres hacer con la plantilla?", 13.5f, Modifier.padding(bottom = 4.dp), color = AsColors.muted, lineHeight = 1.45f)
        DecisionOption(
            on = mode == SessionTemplateApplyMode.REPLACE,
            title = "Reemplazar",
            tag = if (model.suggested == SessionTemplateApplyMode.REPLACE) "Sugerido" else null,
            description = "Sustituye ejercicios, grupos y calentamiento. Conserva el nombre y los datos de la sesión.",
            totals = model.replace,
            over = null,
            onClick = { mode = SessionTemplateApplyMode.REPLACE },
        )
        DecisionOption(
            on = mode == SessionTemplateApplyMode.APPEND,
            title = "Añadir al final",
            tag = if (model.suggested == SessionTemplateApplyMode.APPEND) "Sugerido" else null,
            description = "Suma los grupos y ejercicios de la plantilla a lo que ya tienes.",
            totals = model.append,
            over = model.appendOverMinutes,
            onClick = { mode = SessionTemplateApplyMode.APPEND },
        )
        AsButton("Aplicar", Modifier.fillMaxWidth().padding(top = 6.dp), onClick = { onConfirm(mode) })
        AsTextAction("Cancelar", onClick = onCancel)
    }
}

@Composable
private fun DecisionOption(on: Boolean, title: String, tag: String?, description: String, totals: SessionTotals, over: Int?, onClick: () -> Unit) {
    // as-T08 as-op — `transition: border-color .18s, background .18s` (ease)
    val shape = RoundedCornerShape(20.dp)
    val opBg by asColorState(if (on) AsColors.ink.copy(alpha = 0x0d / 255f) else AsColors.white(0x08), 180)
    val opBorder by asColorState(if (on) AsColors.ink else AsColors.white(0x12), 180)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(opBg)
            .border(1.5.dp, opBorder, shape)
            .semantics { role = Role.RadioButton; selected = on }
            .asClick(role = Role.RadioButton, onClick = onClick).padding(15.5.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).border(2.dp, if (on) AsColors.ink else AsColors.white(0x40), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(AsColors.ink)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsText(title, 15f, weight = FontWeight.SemiBold)
                if (tag != null) {
                    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(AsColors.white(0x0d)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        AsText(tag, 10f, color = AsColors.faint, weight = FontWeight.SemiBold, letterSpacing = 0.9f, uppercase = true, maxLines = 1)
                    }
                }
            }
            AsText(description, 12.5f, Modifier.padding(top = 3.dp), color = AsColors.muted, lineHeight = 1.4f)
            // `.as-op .r`: flex con hueco de 14 en los dos sentidos
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ResultStat("${totals.exercises}", "ejercicios")
                ResultStat("${totals.sets}", "series")
                ResultStat("≈${totals.minutes}", "min")
                if (over != null) AsText("+$over min sobre tu límite", 12.5f, color = AsColors.energy, weight = FontWeight.SemiBold, cap = 1.12f, tabular = true, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ResultStat(value: String, unit: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AsText(value, 12.5f, weight = FontWeight.SemiBold, cap = 1.12f, tabular = true, maxLines = 1)
        AsText(unit, 12.5f, color = AsColors.muted, cap = 1.12f, maxLines = 1)
    }
}

@Composable
private fun ConfirmDeleteOverlay(name: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    BottomOverlay(onScrim = onCancel) {
        AsText("Borrar plantilla", 19f, weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f)
        AsText("Esta acción es permanente. ¿Eliminar «$name»?", 13.5f, Modifier.padding(bottom = 4.dp), color = AsColors.muted, lineHeight = 1.45f)
        AsButton("Borrar", Modifier.fillMaxWidth(), kind = AsButtonKind.DANGER, onClick = onConfirm)
        AsTextAction("Cancelar", onClick = onCancel)
    }
}

/**
 * as-J03 `dec-go` / as-J06 `apTimer`: en la app, «aplicando» dura lo que tarda el motor (no el temporizador de 1,3 s del lienzo) y pasa a «aplicada»
 * cuando `done` cambia. Las animaciones son las del lienzo: entrada del panel (as-A02, 0,2 s), giro del arco (as-A12, 1,1 s lineal, infinito), marca
 * que crece de 0,6 a 1 con fundido (as-X01 + as-T01, `transition: all .35s`) y entrada de los botones (as-A01, 0,3 s).
 */
@Composable
private fun ApplyingOverlay(model: AsApplyModel, onFinish: () -> Unit, onUndo: () -> Unit) {
    // as-T01 as-ap.done .ok — opacity 0 → 1 y scale(.6) → none en 0,35 s cubic-bezier(.2,.8,.2,1)
    val okAlpha = asFloatState(if (model.done) 1f else 0f, 350, AsAnim.Std)
    val okScale = asFloatState(if (model.done) 1f else 0.6f, 350, AsAnim.Std)
    Column(
        Modifier.fillMaxSize().asEnter(AsAnim.OverlayFade).background(AsColors.sheet).asClick(role = Role.Button, onClick = {}).padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            // `<circle cx=42 cy=42 r=36 stroke-width=5>` en un lienzo de 84: el trazo va centrado en el borde del rectángulo, a 6 del lado
            val stroke = 5.dp
            val inset = 6.dp
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val arcSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset.toPx(), size.height - 2 * inset.toPx())
                drawArc(AsColors.white(0x14), 0f, 360f, false, Offset(inset.toPx(), inset.toPx()), arcSize, style = Stroke(stroke.toPx()))
            }
            // as-A12 as-rg .sp — el arco (`stroke-dasharray="60 170"` sobre una circunferencia de 226 = 95,5°, desde las 3 en punto) gira 1,1 s lineal, infinito (quieto con movimiento reducido); al terminar se apaga sin transición
            if (!model.done) {
                androidx.compose.foundation.Canvas(Modifier.matchParentSize().asEnter(AsAnim.Spin)) {
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - 2 * inset.toPx(), size.height - 2 * inset.toPx())
                    drawArc(AsColors.ink, 0f, 95.5f, false, Offset(inset.toPx(), inset.toPx()), arcSize, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
                }
            }
            AsIcon(AsIcons.Check, 40.dp, AsColors.done, Modifier.graphicsLayer { alpha = okAlpha.value; scaleX = okScale.value; scaleY = okScale.value })
        }
        AsText(model.title, 19f, weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f, align = TextAlign.Center)
        AsText(model.body, 13.5f, color = AsColors.muted, lineHeight = 1.45f, align = TextAlign.Center)
        if (model.note.isNotEmpty()) AsCaption(model.note, align = TextAlign.Center)
        if (model.done) {
            Column(Modifier.asEnter(AsAnim.ApplyAfterIn).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsButton("Ver mi sesión", Modifier.fillMaxWidth(), onClick = onFinish)
                AsTextAction("Deshacer", leading = AsIcons.Undo, onClick = onUndo)
            }
        }
    }
}

// ── Mis plantillas · guardar · editar ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MineList(
    rows: List<AsMineRow>,
    corruptCount: Int,
    menuIndex: Int?,
    onBack: () -> Unit,
    onSaveSession: () -> Unit,
    onOpenRow: (String) -> Unit,
    onMenu: (Int) -> Unit,
    onCloseMenu: () -> Unit,
    onEditRow: (String) -> Unit,
    onArchiveRow: (String) -> Unit,
    onRestoreRow: (String) -> Unit,
    onDeleteRow: (String) -> Unit,
) {
    BackHeader("Mis plantillas", onBack) { AsLink("Guardar sesión", Modifier.padding(end = 2.dp), leading = AsIcons.Save, onClick = onSaveSession) }
    if (corruptCount > 0) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AsColors.tint(AsColors.energy, 0x0f))
                .border(1.dp, AsColors.tint(AsColors.energy, 0x29), RoundedCornerShape(16.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsIcon(AsIcons.Warn, 18.dp, AsColors.energy)
            AsText(
                "Hay $corruptCount ${if (corruptCount == 1) "plantilla ilegible" else "plantillas ilegibles"} en cuarentena. No se aplicará hasta reparar el archivo.",
                12.5f, color = AsColors.energy, lineHeight = 1.4f,
            )
        }
    }
    rows.forEachIndexed { index, row ->
        MineRow(row, menuOpen = menuIndex == index, onOpen = { onOpenRow(row.id) }, onMenu = { onMenu(index) }, onRestore = { onRestoreRow(row.id) },
            onEdit = { onEditRow(row.id) }, onArchive = { onArchiveRow(row.id) }, onDelete = { onDeleteRow(row.id) }, onClose = onCloseMenu)
    }
    if (rows.isEmpty()) {
        AsEmpty(AsIcons.Book, "Aún no tienes plantillas", "Guarda esta sesión como plantilla para aplicarla en cualquier otra.", topPadding = 16.dp)
    }
    AsText("Archivar la saca del catálogo sin borrarla. Borrar es permanente.", 12f, Modifier.padding(horizontal = 4.dp), color = AsColors.faint, lineHeight = 1.4f)
}

@Composable
private fun MineRow(
    row: AsMineRow,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    onRestore: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().then(if (menuOpen) Modifier.zIndex(1f) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(20.dp)).background(AsColors.module)
                .border(1.dp, AsColors.line, RoundedCornerShape(20.dp))
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f).heightIn(min = 48.dp).asClick(label = row.name, onClick = onOpen), verticalArrangement = Arrangement.Center) {
                AsText(row.name, 14.5f, color = if (row.archived) AsColors.muted else AsColors.ink, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AsText(if (row.archived) "Archivada" else "Disponible · ${row.exercises} ejercicios", 12f, Modifier.padding(top = 2.dp), color = AsColors.faint)
            }
            if (row.archived) AsButton("Restaurar", small = true, kind = AsButtonKind.GHOST, onClick = onRestore)
            AsIconButton(AsIcons.More, "Más acciones de ${row.name}", onClick = onMenu)
        }
        if (menuOpen) {
            // as-S04 as-menu — sombra 0 14px 40px #000000cc (preset menu) · as-A09 as-menu.open — entrada de 0,18 s (asIn, dx 0)
            Column(
                Modifier.floating(top = 74.dp, end = (-4).dp).asEnter(AsAnim.MenuIn).width(224.dp)
                    .kpknShadow(KpknShadow.Menu, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(AsColors.menu)
                    .border(1.dp, AsColors.white(0x14), RoundedCornerShape(20.dp)).padding(7.dp),
            ) {
                MenuItem(AsIcons.Edit, "Editar", false, onEdit)
                MenuItem(AsIcons.Archive, "Archivar", false, onArchive, enabled = !row.archived)
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(AsColors.white(0x14)))
                MenuItem(AsIcons.Trash, "Borrar", true, onDelete)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val color = if (danger) AsColors.danger else AsColors.ink
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).then(if (enabled) Modifier.asClick(onClick = onClick) else Modifier).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsIcon(icon, 18.dp, if (enabled) color else AsColors.dim)
        AsText(label, 14f, color = if (enabled) color else AsColors.dim, maxLines = 1)
    }
}

/** Guardar la sesión como plantilla y editar una propia: nombre, descripción y, al editar, el nivel. */
@Composable
private fun TemplateForm(
    title: String,
    intro: String,
    submit: String,
    submitting: String,
    initialName: String,
    initialDescription: String,
    initialLevel: Int?,
    initialState: AsSaveState,
    onBack: () -> Unit,
    onSubmit: suspend (name: String, description: String, level: Int?) -> Boolean,
    onDone: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var level by rememberSaveable { mutableStateOf(initialLevel) }
    var state by rememberSaveable { mutableStateOf(initialState) }
    val scope = rememberCoroutineScope()
    BackHeader(title, onBack)
    AsText(intro, 13f, color = AsColors.muted, lineHeight = 1.45f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsText("Nombre", 12f, Modifier.padding(start = 4.dp), color = AsColors.faint)
        FormField(name, { name = it; if (state == AsSaveState.ERROR) state = AsSaveState.IDLE }, "Nombre de la plantilla", error = state == AsSaveState.ERROR, singleLine = true)
        if (state == AsSaveState.ERROR) AsText("Escribe un nombre para la plantilla.", 12f, Modifier.padding(start = 4.dp), color = AsColors.danger)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AsText("Descripción", 12f, Modifier.padding(start = 4.dp), color = AsColors.faint)
        FormField(description, { description = it }, "Opcional", error = false, singleLine = false, minHeight = 84)
    }
    if (level != null) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsText("Nivel", 12f, Modifier.padding(start = 4.dp), color = AsColors.faint)
            AsSegmented(AsLevelNames, level!!, { level = it })
        }
    }
    AsButton(
        if (state == AsSaveState.SAVING) submitting else submit,
        Modifier.fillMaxWidth(),
        enabled = state != AsSaveState.SAVING,
        onClick = {
            if (name.isBlank()) {
                state = AsSaveState.ERROR
            } else {
                state = AsSaveState.SAVING
                scope.launch {
                    val ok = onSubmit(name.trim(), description.trim(), level)
                    if (ok) onDone() else state = AsSaveState.IDLE
                }
            }
        },
    )
    AsTextAction("Cancelar", onClick = onBack)
}

@Composable
private fun FormField(value: String, onValueChange: (String) -> Unit, placeholder: String, error: Boolean, singleLine: Boolean, minHeight: Int = 48) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(16.dp)).background(AsColors.inset)
            .border(1.dp, if (error) AsColors.tint(AsColors.danger, 0x66) else AsColors.white(0x1a), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = AsColors.ink, fontFamily = AsInter, fontSize = asSp(15f), lineHeight = (15f * 1.35f).sp),
            cursorBrush = SolidColor(AsColors.ink),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) AsText(placeholder, 15f, color = AsColors.faint, lineHeight = 1.35f)
    }
}


/** Coloca el contenido sobre lo que hay debajo (`position: absolute`): no ocupa sitio en la fila que lo contiene. */
private fun Modifier.floating(top: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { placeable.place(constraints.maxWidth - placeable.width - end.roundToPx(), top.roundToPx()) }
}
