package com.example.kpkn.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TrainingConcept
import com.example.kpkn.domain.concepts.ConceptMatch
import com.example.kpkn.domain.concepts.ConceptSearchHit
import com.example.kpkn.domain.concepts.conceptCategoriesByArea
import com.example.kpkn.domain.concepts.conceptReadingMinutes
import com.example.kpkn.domain.concepts.dailyConceptoClave
import com.example.kpkn.domain.concepts.findTrainingConcept
import com.example.kpkn.domain.concepts.nextConceptCategory
import com.example.kpkn.domain.concepts.orderConceptosClave
import com.example.kpkn.domain.concepts.searchConceptosClaveHits
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTopIsland
import java.text.Collator
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Conceptos clave: la enciclopedia de KPKN (propuesta aprobada `docs/design/profile-concepts-v1`). Tres vistas en una ruta: la
 * biblioteca (concepto del día, temas por área, índice de la A a la Z y búsqueda con resaltado), la página de un tema y la lectura.
 * Las vistas se apilan: atrás vuelve a la anterior y, en la primera, cierra. Un enlace con [expandedConceptId] abre directamente la
 * lectura de ese concepto; cerrarla vuelve a donde estabas.
 */
@Composable
fun ConceptosClaveScreen(
    expandedConceptId: String? = null,
    onBack: () -> Unit,
) {
    val linked = remember(expandedConceptId) { findTrainingConcept(expandedConceptId)?.id }
    var stack by rememberSaveable(linked) { mutableStateOf(listOf(linked?.let { "read:$it" } ?: "library")) }
    var query by rememberSaveable { mutableStateOf("") }
    val top = stack.last()
    val push: (String) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            top.startsWith("read:") -> {
                val concept = findTrainingConcept(top.removePrefix("read:"))
                if (concept == null) {
                    LaunchedEffect(top) { pop() }
                } else {
                    ConceptoClaveReading(
                        concept = concept,
                        isRoot = stack.size == 1,
                        onClose = pop,
                        onOpen = { id -> push("read:$id") },
                        onOpenLibrary = { stack = listOf("library") },
                    )
                }
            }
            top.startsWith("topic:") -> {
                val category = ConceptCategory.entries.firstOrNull { it.name == top.removePrefix("topic:") } ?: ConceptCategory.LOAD_MANAGEMENT
                ConceptTopicPage(
                    category = category,
                    onBack = pop,
                    onOpen = { id -> push("read:$id") },
                    onNextTopic = { next -> stack = stack.dropLast(1) + "topic:${next.name}" },
                )
            }
            else -> ConceptLibrary(
                query = query,
                onQueryChange = { query = it },
                onClose = pop,
                onOpen = { id -> push("read:$id") },
                onOpenTopic = { category -> push("topic:${category.name}") },
            )
        }
        // Lo que pasa por debajo de la barra de estado se funde en negro.
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.6f)))),
        )
    }
}

// ─── Barra superior compartida ───────────────────────────────────────────────────────────────────────────────────────

/** Botón flotante de vidrio arriba a la izquierda; al desplazar, la isla superior con el título lo sustituye. */
@Composable
internal fun ConceptTopBar(
    listState: LazyListState,
    close: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > threshold } }
    val icon = if (close) KpknLineIcons.Close else KpknLineIcons.ChevronLeft
    val description = if (close) "Cerrar" else "Volver"
    ConceptGlassButton(
        icon, description, onClick,
        Modifier.statusBarsPadding().padding(start = 12.dp, top = 6.dp).alpha(if (scrolled) 0f else 1f).testTag("concept_top_button"),
    )
    KpknTopIsland(
        visible = scrolled,
        title = title,
        subtitle = subtitle,
        leading = { KpknIconButton(icon, description, onClick) },
    )
}

// ─── Biblioteca ──────────────────────────────────────────────────────────────────────────────────────────────────────

private val spanishCollator: Collator = Collator.getInstance(Locale("es", "ES")).apply { strength = Collator.PRIMARY }

/** Letra del índice: la inicial sin tilde, o «#» si empieza por una cifra. */
private fun indexLetter(name: String): String {
    val first = Normalizer.normalize(name.take(1), Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").uppercase(Locale.ROOT)
    return if (first.firstOrNull()?.isLetter() == true) first else "#"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConceptLibrary(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenTopic: (ConceptCategory) -> Unit,
) {
    val listState = rememberLazyListState()
    val concepts = TRAINING_CONCEPTS_DATABASE
    val areas = remember { conceptCategoriesByArea() }
    val topicCount = remember(areas) { areas.values.sumOf { it.size } }
    val hits = remember(query) { searchConceptosClaveHits(query) }
    val searching = query.isNotBlank()
    val daily = remember { dailyConceptoClave(LocalDate.now().toEpochDay()) }
    val alphabetical = remember { concepts.sortedWith { a, b -> spanishCollator.compare(a.name, b.name) }.groupBy { indexLetter(it.name) } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("concept_library"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp),
    ) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding().padding(top = 62.dp)) {
                ConceptEyebrow("Aprende")
                Text(
                    "Conceptos clave", Modifier.padding(top = 8.dp, bottom = 8.dp).semantics { heading() },
                    style = ConceptType.syne(30, FontWeight.ExtraBold, 33), color = ConceptTokens.Ink,
                )
                Text(
                    "Aprende lo que cambia tu entrenamiento. ${concepts.size} conceptos en $topicCount temas.",
                    style = ConceptType.rowSub.copy(fontSize = 14.sp, lineHeight = 20.sp), color = ConceptTokens.Muted,
                )
            }
        }
        stickyHeader(key = "search") {
            // Fijado arriba, el buscador queda bajo la isla; en su sitio, pegado al título.
            val stuck by remember { derivedStateOf { listState.firstVisibleItemIndex >= 1 } }
            // Fijado, una banda negra (que cubre también el margen lateral de la lista) tapa lo que pasa por debajo.
            val band = Modifier.drawBehind {
                val bleed = 18.dp.toPx()
                drawRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                    topLeft = androidx.compose.ui.geometry.Offset(-bleed, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width + bleed * 2, size.height),
                )
            }
            Box(Modifier.fillMaxWidth().then(if (stuck) band.statusBarsPadding().padding(top = 62.dp) else Modifier).padding(top = 8.dp, bottom = 10.dp)) {
                ConceptSearchField(query, onQueryChange)
            }
        }
        if (searching) {
            librarySearchResults(query, hits, onOpen, onQueryChange)
        } else {
            daily?.let { concept ->
                item(key = "daily") { DailyConceptCard(concept, onOpen, Modifier.padding(top = 12.dp)) }
            }
            areas.forEach { (area, categories) ->
                item(key = "area-${area.name}") {
                    ConceptEyebrow(area.label, Modifier.padding(start = 2.dp, top = 30.dp, bottom = 10.dp))
                }
                categories.chunked(2).forEachIndexed { index, pair ->
                    item(key = "topics-${area.name}-$index") {
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            pair.forEach { (category, count) ->
                                TopicTile(category, count, { onOpenTopic(category) }, Modifier.weight(1f).fillMaxHeight())
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item(key = "index-head") {
                ConceptEyebrow("Todos los conceptos, de la A a la Z", Modifier.padding(start = 2.dp, top = 30.dp))
            }
            alphabetical.forEach { (letter, items) ->
                item(key = "letter-$letter") {
                    Text(letter, Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), style = ConceptType.syne(18), color = ConceptTokens.Faint)
                }
                item(key = "letter-group-$letter") {
                    ConceptModule {
                        items.forEachIndexed { index, concept -> ConceptRow(concept, onOpen, divider = index > 0) }
                    }
                }
            }
        }
        item(key = "bottom") {
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
    ConceptTopBar(listState, close = true, title = "Conceptos clave", subtitle = "${concepts.size} conceptos", onClick = onClose)
}

private fun LazyListScope.librarySearchResults(
    query: String,
    hits: List<ConceptSearchHit>,
    onOpen: (String) -> Unit,
    onQueryChange: (String) -> Unit,
) {
    if (hits.isEmpty()) {
        item(key = "empty") { ConceptSearchEmpty(query.trim(), onQueryChange) }
        return
    }
    item(key = "count") {
        Text(
            "${hits.size} ${if (hits.size == 1) "resultado" else "resultados"} para «${query.trim()}»",
            Modifier.padding(start = 4.dp, top = 8.dp).testTag("concept_result_count"),
            style = ConceptType.rowSub.copy(fontSize = 12.sp), color = ConceptTokens.Faint,
        )
    }
    hits.groupBy { it.concept.category }.forEach { (category, group) ->
        item(key = "group-${category.name}") {
            Column {
                ConceptEyebrow(
                    "",
                    Modifier.padding(start = 4.dp, top = 22.dp, bottom = 9.dp),
                    dot = category.color,
                    annotated = highlighted("${category.label.uppercase()} · ${group.size}", query),
                )
                ConceptModule {
                    group.forEachIndexed { index, hit ->
                        ConceptRow(hit.concept, onOpen, divider = index > 0, query = query,
                            snippet = hit.snippet.takeIf { hit.match == ConceptMatch.BODY })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConceptSearchField(query: String, onQueryChange: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF262626), Color(0xFF151515))))
            .border(1.dp, Color(0x1FFFFFFF), shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KpknLineIcons.Search, null, Modifier.size(19.dp), tint = ConceptTokens.Muted)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp).testTag("concept_search_field")
                .semantics { contentDescription = "Buscar conceptos" },
            singleLine = true,
            textStyle = TextStyle(fontFamily = WizardFonts.body, fontSize = 16.sp, color = ConceptTokens.Ink),
            cursorBrush = SolidColor(ConceptTokens.Ink),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Buscar conceptos", style = TextStyle(fontFamily = WizardFonts.body, fontSize = 16.sp), color = ConceptTokens.Faint)
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { onQueryChange("") }
                    .semantics { contentDescription = "Limpiar búsqueda" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Close, null, Modifier.size(16.dp), tint = ConceptTokens.Muted)
            }
        }
    }
}

/** Sin resultados: repite la búsqueda, explica qué se puede buscar y ofrece conceptos reales para tocar. */
@Composable
private fun ConceptSearchEmpty(query: String, onQueryChange: (String) -> Unit) {
    val suggestions = remember {
        listOf("rir", "volumen-entrenamiento", "deload", "creatina", "proteina-diaria", "tendinopatia")
            .mapNotNull { findTrainingConcept(it) }
            .take(4)
            .map { it.name.substringBefore(" (") }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 44.dp, start = 12.dp, end = 12.dp).testTag("concept_search_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(ConceptTokens.Inset), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Search, null, Modifier.size(26.dp), tint = ConceptTokens.Ink2)
        }
        Text(
            "Nada con «$query»", Modifier.padding(top = 16.dp, bottom = 8.dp), style = ConceptType.syne(21),
            color = ConceptTokens.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "Busca por nombre, sigla o idea: el buscador también mira dentro del texto de cada concepto. Prueba con:",
            style = ConceptType.rowSub, color = ConceptTokens.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { name -> ConceptChip(name) { onQueryChange(name) } }
        }
        ConceptChip("Borrar búsqueda", Modifier.padding(top = 20.dp)) { onQueryChange("") }
    }
}

@Composable
private fun ConceptChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier.heightIn(min = 40.dp).clip(shape).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x14FFFFFF), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ConceptType.action, color = ConceptTokens.Ink)
    }
}

/** Concepto del día: el mismo que enseña «Aprende» en Home. Glifo grande y tenue del tema al fondo. */
@Composable
private fun DailyConceptCard(concept: TrainingConcept, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.fillMaxWidth().clip(shape).background(ConceptTokens.GlassBrush).border(1.dp, Color(0x1CFFFFFF), shape)
            .testTag("concept_daily_${concept.id}"),
    ) {
        Icon(
            concept.category.glyph, null,
            Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-10).dp).size(140.dp).alpha(0.13f),
            tint = concept.category.color,
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp)) {
            ConceptEyebrow("Concepto del día · ${concept.category.label}", dot = concept.category.color)
            Text(
                concept.name, Modifier.fillMaxWidth(0.88f).padding(top = 12.dp, bottom = 8.dp),
                style = ConceptType.syne(25, line = 28), color = ConceptTokens.Ink,
            )
            if (concept.shortDescription.isNotBlank()) {
                Text(concept.shortDescription, style = ConceptType.rowSub.copy(fontSize = 14.sp, lineHeight = 20.sp), color = ConceptTokens.Muted)
            }
            Row(
                Modifier.padding(top = 16.dp).heightIn(min = 46.dp).clip(RoundedCornerShape(23.dp)).background(ConceptTokens.Ink)
                    .clickable(role = Role.Button, onClickLabel = "Leer ${concept.name}") { onOpen(concept.id) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KpknLineIcons.Book, null, Modifier.size(17.dp), tint = Color(0xFF0B0B0B))
                Spacer(Modifier.size(8.dp))
                Text("Leer · ${conceptReadingMinutes(concept)} min", style = ConceptType.action.copy(fontSize = 14.sp), color = Color(0xFF0B0B0B))
            }
        }
    }
}

@Composable
private fun TopicTile(category: ConceptCategory, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ConceptTokens.Module)
            .border(1.dp, ConceptTokens.Line, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Abrir ${category.label}", onClick = onClick)
            .heightIn(min = 112.dp)
            .padding(start = 13.dp, end = 13.dp, top = 14.dp, bottom = 13.dp)
            .testTag("concept_topic_${category.name}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConceptGlyphTile(category)
        Spacer(Modifier.weight(1f))
        Column {
            Text(category.label, style = ConceptType.rowTitle.copy(fontSize = 14.sp, lineHeight = 18.sp), color = ConceptTokens.Ink)
            Text("$count ${if (count == 1) "concepto" else "conceptos"}", Modifier.padding(top = 3.dp),
                style = ConceptType.rowSub.copy(fontSize = 12.sp), color = ConceptTokens.Faint)
        }
    }
}

// ─── Página de un tema ───────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConceptTopicPage(
    category: ConceptCategory,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNextTopic: (ConceptCategory) -> Unit,
) {
    val listState = rememberSaveable(category, saver = LazyListState.Saver) { LazyListState() }
    val items = remember(category) { orderConceptosClave().filter { it.category == category } }
    val next = remember(category) { nextConceptCategory(category) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("concept_topic_page_${category.name}"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp),
    ) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding().padding(top = 66.dp)) {
                ConceptGlyphTile(category, size = 52)
                Text(
                    category.label, Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
                    style = ConceptType.syne(30, FontWeight.ExtraBold, 33), color = ConceptTokens.Ink,
                )
                ConceptEyebrow("${category.area.label} · ${items.size} ${if (items.size == 1) "concepto" else "conceptos"}")
            }
        }
        item(key = "list") {
            ConceptModule(Modifier.padding(top = 20.dp)) {
                items.forEachIndexed { index, concept -> ConceptRow(concept, onOpen, divider = index > 0) }
            }
        }
        if (next != category) {
            item(key = "next") {
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(ConceptTokens.ModuleShape).background(ConceptTokens.Module)
                        .border(1.dp, ConceptTokens.Line, ConceptTokens.ModuleShape)
                        .clickable(role = Role.Button, onClickLabel = "Siguiente tema: ${next.label}") { onNextTopic(next) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConceptGlyphTile(next)
                    Column(Modifier.weight(1f)) {
                        ConceptEyebrow("Siguiente tema")
                        Text(next.label, Modifier.padding(top = 3.dp), style = ConceptType.rowTitle, color = ConceptTokens.Ink)
                    }
                    Icon(KpknLineIcons.ChevronRight, null, Modifier.size(18.dp), tint = ConceptTokens.Dim)
                }
            }
        }
        item(key = "bottom") {
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
    ConceptTopBar(listState, close = false, title = category.label, subtitle = "Tema", onClick = onBack)
}
