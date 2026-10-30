package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.sessionassistant.AssistantNoticeKind
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail

/** La caja de «cómo quedaría» de una sugerencia: «Duración 59 → 57 min» o «Glúteos · límite 10: 10,5 → 10». */
internal data class AsFx(
    val label: String,
    val labelSuffix: String?,
    val before: String,
    val after: String,
    val unit: String,
    /** Anchura del relleno (0–1) y posición de la marca del límite (0–1). */
    val barFraction: Float,
    val limitFraction: Float,
    val text: String,
    val ok: Boolean,
)

/** Una línea plegada: aplicada (con su resumen) o descartada. */
internal data class AsOutcomeRow(val suggestionId: String, val applied: Boolean, val summary: String)

/** Una entrada de la lista: la tarjeta viva o la línea plegada en su sitio. */
internal sealed interface AsSuggestionEntry {
    data class Card(val suggestion: AssistantSuggestion) : AsSuggestionEntry
    data class Folded(val row: AsOutcomeRow) : AsSuggestionEntry
}

/** Las dos líneas de «Lo que el asistente revisa» del estado «todo en orden». */
internal data class AsReviewLine(val title: String, val status: String)

internal data class AsSuggestionsModel(
    /** Null mientras el análisis se calcula. */
    val entries: List<AsSuggestionEntry>?,
    val notes: List<String>,
    val timeReview: AsReviewLine,
    val limitsReview: AsReviewLine,
)

/**
 * Pestaña Sugerencias (`as-sug-lista`, `as-sug-aplicada`, `as-sug-vacio`, `as-sug-nota`, `as-sug-carga`): tarjetas con el motivo, la caja de
 * efecto en vivo y las casillas; al aplicar o descartar, la tarjeta se pliega en una línea con «Deshacer».
 */
@Composable
internal fun AssistantSuggestionsPane(
    model: AsSuggestionsModel,
    fxFor: (AssistantSuggestion, List<AssistantSuggestionDetail>) -> AsFx?,
    onApply: (AssistantSuggestion, acceptedIds: List<String>, position: Int) -> Unit,
    onDismiss: (suggestionId: String, position: Int) -> Unit,
    onUndo: (suggestionId: String) -> Unit,
    onGoTimeRules: () -> Unit,
    onGoLimitRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AsScrollPane(modifier) {
        val entries = model.entries
        when {
            entries == null -> SuggestionsLoading()
            entries.isEmpty() && model.notes.isEmpty() -> SuggestionsAllClear(model, onGoTimeRules, onGoLimitRules)
            else -> {
                val cards = entries.count { it is AsSuggestionEntry.Card }
                if (entries.isNotEmpty()) {
                    AsText(
                        "$cards ${if (cards == 1) "aviso" else "avisos"} · como mucho tres a la vez",
                        12f, Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp), color = AsColors.faint,
                    )
                }
                entries.forEachIndexed { position, entry ->
                    when (entry) {
                        is AsSuggestionEntry.Card -> SuggestionCard(
                            suggestion = entry.suggestion,
                            fxFor = fxFor,
                            onApply = { ids -> onApply(entry.suggestion, ids, position) },
                            onDismiss = { onDismiss(entry.suggestion.id, position) },
                        )
                        is AsSuggestionEntry.Folded -> FoldedRow(entry.row) { onUndo(entry.row.suggestionId) }
                    }
                }
                model.notes.forEach { note -> NoteCard(note) }
                if (model.notes.isNotEmpty()) {
                    AsCaption(
                        "Solo informa: no hay nada que aplicar. El asistente muestra como mucho tres avisos a la vez.",
                        Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsLoading() {
    // pulido:as-A18 as-sk-l i — destello de 1,4 s lineal, infinito · pulido:as-G02 as-sk-l i — degradado del esqueleto
    val bars = listOf(
        listOf(0.34f to 12.dp, 0.78f to 16.dp, 0.92f to 12.dp, 0.60f to 12.dp),
        listOf(0.40f to 12.dp, 0.70f to 16.dp, 0.88f to 12.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp)).padding(horizontal = 17.dp, vertical = 19.dp)
            .semantics { contentDescription = "Calculando sugerencias" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        bars[0].forEach { (w, h) -> SkeletonBar(w, h) }
        Box(Modifier.padding(top = 6.dp)) { SkeletonBar(1f, 44.dp, radius = 14.dp) }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp)).padding(horizontal = 17.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { bars[1].forEach { (w, h) -> SkeletonBar(w, h) } }
    AsText("Comparando la sesión con tu tiempo y tus límites…", 12f, Modifier.fillMaxWidth(), color = AsColors.faint, lineHeight = 1.4f, align = TextAlign.Center)
}

@Composable
private fun SkeletonBar(fraction: Float, height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        Modifier.fillMaxWidth(fraction).height(height).clip(RoundedCornerShape(radius))
            .background(
                Brush.horizontalGradient(
                    0.2f to AsColors.white(0x0a), 0.5f to AsColors.white(0x1c), 0.8f to AsColors.white(0x0a),
                ),
            ),
    )
}

/** El estado «todo en orden»: el icono y su anillo van en verde (pulido:as-J01 — el lienzo los tiñe con estilo en línea desde JS). */
@Composable
private fun SuggestionsAllClear(model: AsSuggestionsModel, onGoTimeRules: () -> Unit, onGoLimitRules: () -> Unit) {
    AsEmpty(
        icon = AsIcons.Check,
        title = "Sin sugerencias para esta sesión",
        body = "Si algo choca con el tiempo o con los límites que elegiste, aparecerá aquí.",
        topPadding = 18.dp,
        iconTint = AsColors.done,
        ringColor = AsColors.tint(AsColors.done, 0x55),
    )
    AsModule {
        AsModuleHeader("Lo que el asistente revisa")
        ReviewRow(model.timeReview, first = true, onClick = onGoTimeRules)
        ReviewRow(model.limitsReview, first = false, onClick = onGoLimitRules)
    }
}

@Composable
private fun ReviewRow(line: AsReviewLine, first: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp)
            .then(if (first) Modifier else Modifier.drawBehind { drawLine(AsColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AsText(line.title, 14f, weight = FontWeight.SemiBold)
            AsText(line.status, 12f, Modifier.padding(top = 2.dp), color = AsColors.faint)
        }
        AsLink("Cambiar", trailing = AsIcons.Forward, minHeight = 44.dp, onClick = onClick)
    }
}

@Composable
private fun NoteCard(note: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp)).padding(horizontal = 17.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(AsColors.white(0x0d)), contentAlignment = Alignment.Center) {
            AsIcon(AsIcons.Info, 18.dp, AsColors.muted)
        }
        Column(Modifier.weight(1f)) {
            Kicker(AssistantNoticeKind.INFO.label, null)
            AsText(note, 13.5f, Modifier.padding(top = 3.dp), color = AsColors.muted, lineHeight = 1.45f)
        }
    }
}

@Composable
private fun Kicker(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(Modifier.heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) AsIcon(icon, 13.dp, AsColors.faint)
        AsText(text, 10.5f, color = AsColors.faint, weight = FontWeight.SemiBold, cap = 1.15f, letterSpacing = 1.2f, uppercase = true)
    }
}

/** pulido:as-J05 coach-ok — al aplicar, la tarjeta se pliega en su línea con «Deshacer» (el lienzo la atenúa al 40 %). */
@Composable
private fun SuggestionCard(
    suggestion: AssistantSuggestion,
    fxFor: (AssistantSuggestion, List<AssistantSuggestionDetail>) -> AsFx?,
    onApply: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val accepted = remember(suggestion.id, suggestion.details) {
        mutableStateMapOf<String, Boolean>().apply { suggestion.details.forEach { put(it.id, it.defaultAccepted) } }
    }
    val checked = suggestion.details.filter { accepted[it.id] ?: it.defaultAccepted }
    val fx = if (suggestion.details.isEmpty()) null else fxFor(suggestion, checked)
    // pulido:as-T11 as-sg — la tarjeta se funde y se desplaza 0,26 s al plegarse
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AsColors.module)
            .border(1.dp, AsColors.line, RoundedCornerShape(22.dp))
            .padding(start = 17.dp, top = 17.dp, end = 17.dp, bottom = 15.dp),
    ) {
        Kicker(suggestion.kind.label, if (suggestion.kind == AssistantNoticeKind.USER_LIMIT) AsIcons.Lock else null)
        AsText(suggestion.title, 18f, Modifier.padding(top = 6.dp, bottom = 6.dp), weight = FontWeight.Bold, family = AsSyne, lineHeight = 1.2f, letterSpacing = -0.3f)
        AsText(suggestion.message, 13.5f, color = AsColors.muted, lineHeight = 1.45f)
        if (fx != null) FxBox(fx)
        if (suggestion.details.isNotEmpty()) {
            Column(Modifier.padding(top = 8.dp)) {
                suggestion.details.forEachIndexed { index, detail ->
                    val on = accepted[detail.id] ?: detail.defaultAccepted
                    DetailRow(detail, on, first = index == 0) { accepted[detail.id] = !on }
                }
            }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (suggestion.details.isEmpty()) {
                AsButton("Aplicar", Modifier.fillMaxWidth(), onClick = { onApply(emptyList()) })
            } else {
                AsButton(
                    "Aplicar selección",
                    Modifier.fillMaxWidth(),
                    enabled = checked.isNotEmpty(),
                    suffix = "(${checked.size})",
                    onClick = { onApply(checked.map { it.id }) },
                )
            }
            AsTextAction("Descartar", onClick = onDismiss)
        }
    }
}

@Composable
private fun FxBox(fx: AsFx) {
    // pulido:as-T05 as-fx .bar i — el ancho del relleno cambia en 0,32 s cubic-bezier(.2,.8,.2,1)
    Column(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AsColors.card)
            .border(1.dp, AsColors.line, RoundedCornerShape(16.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f)) {
                AsText(fx.label, 12.5f, color = AsColors.muted, maxLines = 1)
                if (fx.labelSuffix != null) AsText(" · ${fx.labelSuffix}", 12.5f, color = AsColors.faint, maxLines = 1)
            }
            AsText("${fx.before} → ${fx.after}${if (fx.unit.isNotEmpty()) " ${fx.unit}" else ""}", 14f, weight = FontWeight.SemiBold, tabular = true, maxLines = 1)
        }
        BoxWithConstraints(Modifier.padding(top = 10.dp, bottom = 8.dp).fillMaxWidth().height(6.dp)) {
            val w = maxWidth
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(3.dp)).background(AsColors.white(0x0d))) {
                Box(Modifier.width(w * fx.barFraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(AsColors.ink))
            }
            Box(
                Modifier.offset(x = w * fx.limitFraction.coerceIn(0f, 1f), y = (-4).dp).size(width = 2.dp, height = 14.dp)
                    .clip(RoundedCornerShape(1.dp)).background(AsColors.energy),
            )
        }
        AsText(fx.text, 12.5f, color = if (fx.ok) AsColors.done else AsColors.muted, lineHeight = 1.4f)
    }
}

@Composable
private fun DetailRow(detail: AssistantSuggestionDetail, on: Boolean, first: Boolean, onToggle: () -> Unit) {
    val savedMin = detail.effect?.let { (-it.deltaSeconds / 60.0).let { m -> Math.round(m).toInt() } } ?: 0
    val savedSets = detail.effect?.let { -it.deltaSets } ?: 0
    // pulido:as-T03 as-ck — fondo, borde y color 0,18 s ease
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp)
            .then(if (first) Modifier else Modifier.drawBehind { drawLine(AsColors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) })
            .semantics { role = Role.Checkbox; stateDescription = if (on) "Marcado" else "Sin marcar"; contentDescription = detail.label }
            .asClick(role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                .background(if (on) AsColors.ink else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.75.dp, if (on) AsColors.ink else AsColors.white(0x40), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { if (on) AsIcon(AsIcons.Check, 15.dp, AsColors.onInk) }
        AsText(detail.label, 13.5f, Modifier.weight(1f), color = if (on) AsColors.ink else AsColors.muted, lineHeight = 1.35f)
        if (savedMin > 0 || savedSets > 0) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (savedMin > 0) AsText("−$savedMin min", 12.5f, color = AsColors.done, weight = FontWeight.SemiBold, tabular = true, maxLines = 1)
                if (savedSets > 0) AsText("−$savedSets ${if (savedSets == 1) "serie" else "series"}", 11f, color = AsColors.faint, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FoldedRow(row: AsOutcomeRow, onUndo: () -> Unit) {
    val applied = row.applied
    // pulido:as-A17 as-done — entrada de 0,26 s (asIn, dx 0)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (applied) AsColors.tint(AsColors.done, 0x0f) else AsColors.white(0x08))
            .border(1.dp, if (applied) AsColors.tint(AsColors.done, 0x33) else AsColors.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (applied) AsColors.tint(AsColors.done, 0x24) else AsColors.white(0x10)),
            contentAlignment = Alignment.Center,
        ) { AsIcon(if (applied) AsIcons.Check else AsIcons.Close, if (applied) 18.dp else 16.dp, if (applied) AsColors.done else AsColors.muted) }
        Column(Modifier.weight(1f)) {
            AsText(if (applied) "Aplicado" else "Descartada", 14f, weight = FontWeight.SemiBold)
            AsText(
                if (applied) row.summary else "No volverá a salir mientras no cambie la sesión.",
                12f, Modifier.padding(top = 2.dp), color = AsColors.muted,
            )
        }
        Row(
            Modifier.heightIn(min = 44.dp).padding(horizontal = 6.dp).asClick(onClick = onUndo),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AsIcon(AsIcons.Undo, 16.dp, AsColors.ink)
            AsText("Deshacer", 13.5f, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
