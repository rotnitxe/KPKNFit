package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.DiscomfortSection
import com.example.kpkn.domain.discomfort.DiscomfortSearch
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.DiscomfortSelectedTag
import com.example.kpkn.screens.workout.FeedbackInsights
import com.example.kpkn.screens.workout.PostExerciseFeedbackDraft
import com.example.kpkn.screens.workout.reportedAgoLabel
import com.example.kpkn.screens.workout.reportedAgoShort
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/*
 * Las piezas de las molestias del feedback tras el ejercicio (LV6, F2 y F4): el buscador amable, los chips destacados (primero las
 * reportadas antes, luego las frecuentes en el ejercicio), las ya elegidas, el aviso del motor y los botones «Saltar descanso» y «Guardar».
 * Las usan la pantalla completa y la tarjeta; [compact] da la versión de la tarjeta (más baja, con los chips en una fila que se desliza).
 */

private val NoticeAccent = Color(0xFFF49A6E)

/** El color de la zona de una molestia del catálogo (el del punto de su chip). */
private fun zoneColorOf(discomfortId: String): Color =
    discomfortZoneColor(DISCOMFORT_CATALOG_BY_ID[discomfortId]?.section ?: DiscomfortSection.GENERAL)

/**
 * El cuerpo de «¿ALGUNA MOLESTIA?»: buscador, chips y las molestias ya elegidas. [onOpenDiscomfort] abre la ficha de una molestia (tocar
 * un chip). [focusName] es el ejercicio de «Frecuentes en …». [nowMs] da el «la reportaste el lunes».
 */
@Composable
internal fun DiscomfortPicker(
    draft: PostExerciseFeedbackDraft,
    insights: FeedbackInsights,
    focusName: String,
    nowMs: Long,
    onOpenDiscomfort: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
        Text(text = "¿ALGUNA MOLESTIA?", style = KpknType.eyebrow, color = WizardColors.textMuted)
        FeedbackSearchField(
            value = draft.query,
            onValueChange = { draft.query = it },
            compact = compact,
        )
        val query = draft.query.trim()
        if (query.isNotEmpty()) {
            val hits = remember(query) { DiscomfortSearch.search(query) }
            if (hits.isEmpty()) {
                Text(
                    text = "No se encontraron resultados para \"$query\"",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            } else {
                ChipFlow(compact) {
                    hits.forEach { hit ->
                        DiscomfortChip(
                            label = hit.entry.label,
                            zoneColor = zoneColorOf(hit.entry.id),
                            selected = draft.isSelected(hit.entry.id),
                            onClick = { onOpenDiscomfort(hit.entry.id) },
                            compact = compact,
                        )
                    }
                }
            }
        } else if (compact) {
            // La tarjeta no tiene sitio para el rótulo: una sola fila que se desliza, con lo reportado antes primero (F4).
            if (insights.reported.isEmpty()) {
                Text(
                    text = "Escribe para buscar la zona que molesta.",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            } else {
                ChipFlow(true) {
                    insights.reported.forEach { report ->
                        val label = DISCOMFORT_CATALOG_BY_ID[report.id]?.label ?: return@forEach
                        DiscomfortChip(
                            label = "$label · ${reportedAgoShort(report.lastReportedAtMs, nowMs)}",
                            zoneColor = zoneColorOf(report.id),
                            selected = draft.isSelected(report.id),
                            onClick = { onOpenDiscomfort(report.id) },
                            compact = true,
                            reported = true,
                        )
                    }

                }
            }
        } else {
            if (insights.reported.isNotEmpty()) {
                ChipFlow(compact) {
                    insights.reported.forEach { report ->
                        val label = DISCOMFORT_CATALOG_BY_ID[report.id]?.label ?: return@forEach
                        DiscomfortChip(
                            label = "$label · ${reportedAgoLabel(report.lastReportedAtMs, nowMs)}",
                            zoneColor = zoneColorOf(report.id),
                            selected = draft.isSelected(report.id),
                            onClick = { onOpenDiscomfort(report.id) },
                            compact = compact,
                            reported = true,
                        )
                    }
                }
            }
            if (insights.reported.isEmpty()) {
                Text(
                    text = "Escribe para buscar la zona que molesta.",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
        }
        if (draft.discomfortIds.isNotEmpty()) {
            ChipFlow(compact) {
                draft.discomfortIds.toList().forEach { id ->
                    DiscomfortSelectedTag(
                        label = DISCOMFORT_CATALOG_BY_ID[id]?.label ?: id,
                        onRemove = { draft.deselect(id) },
                    )
                }
            }
        }
    }
}

/** Los chips en varias líneas (pantalla completa) o en una sola fila que se desliza (tarjeta, de altura fija). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) {
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadeHorizontalWhenScrollable(scroll)
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) { content() }
    }
}

/**
 * Chip de una molestia (F2): píldora `inset` de 34 dp (30 en la tarjeta) con un punto del color de su zona; elegida, de tinta. Las
 * «reportadas antes» llevan filete cálido y el icono del historial. Su zona táctil mide 48 dp.
 */
@Composable
internal fun DiscomfortChip(
    label: String,
    zoneColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    reported: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val height = if (compact) 30.dp else 34.dp
    val dotBox = if (compact) 18.dp else 22.dp
    val dot = if (compact) 6.dp else 8.dp
    val fill = if (selected) WizardColors.text else KpknSurface.inset
    val ink = if (selected) WizardColors.ctaContent else WizardColors.text
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = height)
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .then(if (reported && !selected) Modifier.border(BorderStroke(1.dp, NoticeAccent.copy(alpha = 0.45f)), KpknShape.pill) else Modifier)
                .padding(start = if (reported) 10.dp else 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            if (reported) {
                Icon(
                    imageVector = KpknLineIcons.History,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                    tint = if (selected) ink else NoticeAccent,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(dotBox)
                        .background(zoneColor.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(dot).background(zoneColor, CircleShape))
                }
            }
            Text(
                text = label,
                style = KpknType.chip.copy(fontSize = if (compact) 12.sp else 13.sp),
                color = ink,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** El buscador de molestias (F2): píldora oscura de 42 dp (38 en la tarjeta) con la lupa; el texto se lee a 14 sp (13 en la tarjeta). */
@Composable
internal fun FeedbackSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val height = if (compact) 38.dp else 42.dp
    val textSize = if (compact) 13.sp else 14.sp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Buscar molestia" },
        singleLine = true,
        textStyle = KpknType.body.copy(fontSize = textSize, color = WizardColors.text),
        cursorBrush = SolidColor(WizardColors.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(KpknShape.pill)
                        .background(Color(0xFF0B0B0B), KpknShape.pill)
                        .border(1.dp, WizardColors.text.copy(alpha = 0.10f), KpknShape.pill)
                        .padding(horizontal = if (compact) 12.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                ) {
                    Icon(
                        imageVector = KpknLineIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                        tint = WizardColors.text.copy(alpha = 0.6f),
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Rodilla, lumbar, hombro…",
                                style = KpknType.body.copy(fontSize = textSize),
                                color = WizardColors.textFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                }
            }
        },
    )
}

/**
 * El aviso del motor (F2): una línea que sale de lo que los datos demuestran, en una caja cálida con un icono de rayos. Solo se pinta
 * si hay evidencia; quien lo llama no lo pinta sin texto.
 */
@Composable
internal fun DiscomfortNotice(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NoticeAccent.copy(alpha = 0.08f), shape)
            .border(1.dp, NoticeAccent.copy(alpha = 0.25f), shape)
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoticeIcon(modifier = Modifier.padding(top = 1.dp).size(if (compact) 16.dp else 18.dp))
        Text(
            text = text,
            style = KpknType.caption.copy(fontSize = if (compact) 12.sp else 13.sp, lineHeight = if (compact) 17.sp else 19.sp),
            color = WizardColors.text.copy(alpha = 0.85f),
            maxLines = if (compact) 4 else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Ocho rayos alrededor de un hueco (el icono del aviso de F2), en el color cálido. */
@Composable
private fun NoticeIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        fun ray(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(NoticeAccent, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), strokeWidth = 2f * unit, cap = StrokeCap.Round)
        }
        ray(12f, 3f, 12f, 6f)
        ray(12f, 18f, 12f, 21f)
        ray(3f, 12f, 6f, 12f)
        ray(18f, 12f, 21f, 12f)
        ray(5.6f, 5.6f, 7.7f, 7.7f)
        ray(16.3f, 16.3f, 18.4f, 18.4f)
        ray(5.6f, 18.4f, 7.7f, 16.3f)
        ray(16.3f, 7.7f, 18.4f, 5.6f)
    }
}

/**
 * «Saltar descanso» y «Guardar» (F2) o «Saltar» y «Guardar» en la tarjeta (F4). Sin descanso en marcha ([onSkipRest] nulo) queda solo
 * «Guardar», a todo el ancho. «Guardar» es el «Registrar feedback» de siempre.
 */
@Composable
internal fun FeedbackActions(
    onSkipRest: (() -> Unit)?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSkipRest != null) {
            FeedbackActionPill(
                text = if (compact) "Saltar" else "Saltar descanso",
                onClick = onSkipRest,
                primary = false,
                height = if (compact) 44.dp else 48.dp,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Saltar descanso" },
            )
        }
        FeedbackActionPill(
            text = "Guardar",
            onClick = onSave,
            primary = true,
            height = if (compact) 44.dp else 48.dp,
            modifier = Modifier.weight(if (onSkipRest != null) 1.4f else 1f),
        )
    }
}

/** Píldora de acción del feedback: de tinta (la principal) o `inset` (la secundaria). El texto encoge de 14 a 11 sp antes que cortarse. */
@Composable
private fun FeedbackActionPill(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val autoSize = remember { TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp) }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(KpknShape.pill)
                .background(if (primary) WizardColors.text else KpknSurface.inset, KpknShape.pill)
                .then(if (primary) Modifier else Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.12f), KpknShape.pill))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = TextStyle(
                    fontFamily = WizardFonts.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if (primary) WizardColors.ctaContent else WizardColors.text,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                autoSize = autoSize,
            )
        }
    }
}
