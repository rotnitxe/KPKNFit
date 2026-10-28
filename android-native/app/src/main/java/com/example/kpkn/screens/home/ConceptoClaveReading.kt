package com.example.kpkn.screens.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.data.wikilab.TrainingConcept
import com.example.kpkn.data.wikilab.sourceReferencesForConcept
import com.example.kpkn.domain.concepts.ConceptTextSpan
import com.example.kpkn.domain.concepts.adjacentConceptosClave
import com.example.kpkn.domain.concepts.conceptBodyParagraphs
import com.example.kpkn.domain.concepts.conceptReadingMinutes
import com.example.kpkn.domain.concepts.parseConceptText
import com.example.kpkn.domain.concepts.relatedConceptosClave
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Lectura de un concepto: tema y minutos, título en Syne, el resumen como entradilla, el texto con enlaces de wiki tocables, el
 * apartado «En la práctica» en vidrio, las fuentes (abren PubMed), «Ver también» y Anterior / Siguiente en el orden de la biblioteca.
 * [isRoot] indica que se llegó por un enlace: ✕ cierra la pantalla y al final se ofrece la biblioteca.
 */
@Composable
internal fun ConceptoClaveReading(
    concept: TrainingConcept,
    isRoot: Boolean,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    // Cada concepto empieza arriba: el estado de la lista depende del id que se lee.
    val listState = rememberSaveable(concept.id, saver = LazyListState.Saver) { LazyListState() }
    val paragraphs = remember(concept.id) { conceptBodyParagraphs(concept) }
    val sources = remember(concept.id) { sourceReferencesForConcept(concept.id) }
    val related = remember(concept.id) { relatedConceptosClave(concept) }
    val (previous, next) = remember(concept.id) { adjacentConceptosClave(concept.id) }
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("concept_reading_${concept.id}"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
    ) {
        item(key = "head") {
            Column(Modifier.statusBarsPadding().padding(top = 64.dp)) {
                ConceptEyebrow(
                    "${concept.category.label} · ${conceptReadingMinutes(concept)} min de lectura",
                    dot = concept.category.color,
                )
                Text(
                    concept.name, Modifier.padding(top = 12.dp, bottom = 14.dp).semantics { heading() },
                    // Los títulos largos (estudios con autor y año) bajan un punto para no ocupar media pantalla.
                    style = if (concept.name.length > 32) ConceptType.syne(23, FontWeight.ExtraBold, 27)
                    else ConceptType.syne(28, FontWeight.ExtraBold, 31),
                    color = ConceptTokens.Ink,
                )
                if (concept.shortDescription.isNotBlank()) {
                    Text(concept.shortDescription, Modifier.padding(bottom = 20.dp), style = ConceptType.lead, color = ConceptTokens.Ink)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
            }
        }
        paragraphs.forEachIndexed { index, paragraph ->
            item(key = "p-$index") {
                ConceptParagraph(paragraph, onOpen, Modifier.padding(top = if (index == 0) 20.dp else 16.dp), tag = "concept_body_${concept.id}_$index")
            }
        }
        if (concept.practice.isNotBlank()) {
            item(key = "practice") {
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.padding(top = 20.dp).fillMaxWidth().clip(shape).background(ConceptTokens.GlassBrush)
                        .border(1.dp, Color(0x1CFFFFFF), shape).padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 16.dp)
                        .testTag("concept_practice_${concept.id}"),
                ) {
                    ConceptEyebrow("En la práctica", Modifier.padding(bottom = 8.dp), dot = concept.category.color)
                    ConceptParagraph(concept.practice, onOpen, style = ConceptType.body.copy(fontSize = 14.5.sp, lineHeight = 23.sp), color = ConceptTokens.Ink)
                }
            }
        }
        if (sources.isNotEmpty()) {
            item(key = "sources") {
                ReadingSection("Fuentes", KpknLineIcons.Book) {
                    ConceptModule {
                        sources.forEachIndexed { index, source ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .drawBehind { if (index > 0) drawLine(ConceptTokens.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                                    .clickable(role = Role.Button, onClickLabel = "Abrir en PubMed") { runCatching { uriHandler.openUri(source.url) } }
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(source.title, style = ConceptType.rowTitle.copy(fontSize = 13.sp, lineHeight = 18.sp), color = ConceptTokens.Ink)
                                    Text(source.publisher, Modifier.padding(top = 3.dp), style = ConceptType.rowSub.copy(fontSize = 12.sp), color = ConceptTokens.Faint)
                                }
                                Icon(KpknLineIcons.Link, null, Modifier.size(16.dp), tint = ConceptTokens.Faint)
                            }
                        }
                    }
                }
            }
        }
        if (related.isNotEmpty()) {
            item(key = "related") {
                ReadingSection("Ver también") {
                    ConceptModule {
                        related.forEachIndexed { index, item -> ConceptRow(item, onOpen, divider = index > 0) }
                    }
                }
            }
        }
        item(key = "adjacent") {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdjacentCard("‹ Anterior", previous, onOpen, Modifier.weight(1f).fillMaxHeight(), TextAlign.Start)
                AdjacentCard("Siguiente ›", next, onOpen, Modifier.weight(1f).fillMaxHeight(), TextAlign.End)
            }
        }
        if (isRoot) {
            item(key = "library") {
                Box(Modifier.fillMaxWidth().padding(top = 22.dp), contentAlignment = Alignment.Center) {
                    val shape = RoundedCornerShape(22.dp)
                    Row(
                        Modifier.heightIn(min = 44.dp).clip(shape).background(Color(0x0DFFFFFF)).border(1.dp, Color(0x14FFFFFF), shape)
                            .clickable(role = Role.Button, onClick = onOpenLibrary).padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("concept_open_library"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(KpknLineIcons.Book, null, Modifier.size(16.dp), tint = ConceptTokens.Ink)
                        Spacer(Modifier.size(7.dp))
                        Text("Ver los ${TRAINING_CONCEPTS_DATABASE.size} conceptos", style = ConceptType.action, color = ConceptTokens.Ink)
                    }
                }
            }
        }
        item(key = "bottom") {
            Spacer(Modifier.height(48.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
    ConceptTopBar(listState, close = isRoot, title = concept.name, subtitle = concept.category.label, onClick = onClose)
}

/** Párrafo con los enlaces de wiki subrayados; tocar uno abre ese concepto. */
@Composable
private fun ConceptParagraph(
    text: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = ConceptType.body,
    color: Color = ConceptTokens.Ink2,
    tag: String? = null,
) {
    val annotated: AnnotatedString = remember(text) {
        buildAnnotatedString {
            parseConceptText(text).forEach { span ->
                when (span) {
                    is ConceptTextSpan.Plain -> append(span.text)
                    is ConceptTextSpan.Link -> withLink(
                        LinkAnnotation.Clickable(
                            tag = span.conceptId,
                            styles = TextLinkStyles(
                                SpanStyle(color = ConceptTokens.Ink, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                            ),
                            linkInteractionListener = { onOpen(span.conceptId) },
                        ),
                    ) { append(span.text) }
                }
            }
        }
    }
    Text(annotated, modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier), style = style, color = color)
}

@Composable
private fun ReadingSection(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        Row(Modifier.padding(start = 2.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, Modifier.size(15.dp), tint = ConceptTokens.Faint)
                Spacer(Modifier.size(8.dp))
            }
            ConceptEyebrow(label)
        }
        content()
    }
}

@Composable
private fun AdjacentCard(label: String, concept: TrainingConcept?, onOpen: (String) -> Unit, modifier: Modifier, align: TextAlign) {
    if (concept == null) {
        Spacer(modifier)
        return
    }
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(ConceptTokens.Module).border(1.dp, ConceptTokens.Line, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "$label: ${concept.name}") { onOpen(concept.id) }
            .heightIn(min = 72.dp).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start,
    ) {
        Text(label.uppercase(), style = ConceptType.eyebrow.copy(fontSize = 10.sp), color = ConceptTokens.Faint, textAlign = align)
        Text(concept.name, style = ConceptType.rowTitle.copy(fontSize = 13.sp, lineHeight = 18.sp), color = ConceptTokens.Ink, textAlign = align)
    }
}
