package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.screens.programdetail.design.structure.StructureAction
import com.example.kpkn.screens.programdetail.design.structure.StructureCaptionStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureEyebrowStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureModule
import com.example.kpkn.screens.programdetail.design.structure.StructureModuleHeader
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted
import com.example.kpkn.screens.programdetail.design.structure.tabular
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale

/*
 * Módulos de la pestaña Progreso de las escenas g-summary, g-work y g-empty del lienzo v6: el resumen, «Fuerza · e1RM», «Series duras
 * por semana» y «Trabajo acumulado». Reciben ya calculado lo que muestran (ProgramProgressModel.kt) y solo dan formato y colocación.
 */

private val SpanishLocale = Locale.forLanguageTag("es")

/** Altura de la zona de barras de series (`.bars`). */
private val WeekBarZoneHeight = 110.dp

/** Altura de la fila de «Tonelaje por semana». */
private val TonnageZoneHeight = 44.dp

/** Contorno discontinuo de lo previsto (`#ffffff30`). */
private val PlannedOutline = Color(0x30FFFFFF)

/**
 * Resumen (g-summary / g-empty): el periodo como eyebrow, una frase y tres cifras. Con tendencia, «92 %» con «12 de 13 sesiones», las
 * series «de 264 planificadas» y el tonelaje con su comparación; recién empezado, «2/5» «sesiones», «de 64» y el periodo.
 */
@Composable
internal fun ProgressSummaryModule(ui: ProgramProgressUi, modifier: Modifier = Modifier) {
    val kpis = ui.kpis
    val sessionWord = if (kpis.sessionsPlanned == 1) "sesión" else "sesiones"
    val tonnage = formatDecimalEs(kpis.tonnageKg / 1000.0, 1)
    val tiles = if (ui.early) {
        listOf(
            ProgressKpiText("Cumplimiento", "${kpis.sessionsDone}/${kpis.sessionsPlanned}", footer = "sesiones"),
            ProgressKpiText("Series", kpis.setsDone.toString(), footer = "de ${kpis.setsPlanned}"),
            ProgressKpiText("Tonelaje", "$tonnage t", footer = ui.tonnageFooter),
        )
    } else {
        listOf(
            ProgressKpiText(
                label = "Cumplimiento",
                value = kpis.compliancePct?.toString() ?: "—",
                unit = kpis.compliancePct?.let { " %" },
                footer = "${kpis.sessionsDone} de ${kpis.sessionsPlanned} $sessionWord",
            ),
            ProgressKpiText("Series", kpis.setsDone.toString(), footer = "de ${kpis.setsPlanned} planificadas"),
            ProgressKpiText("Tonelaje", tonnage, unit = " t", footer = ui.tonnageFooter),
        )
    }
    StructureModule(modifier = modifier.testTag("progreso_resumen")) {
        Text(text = ui.periodLabel.uppercase(SpanishLocale), style = StructureEyebrowStyle, color = StructureMuted)
        Spacer(Modifier.height(4.dp))
        Text(text = ui.headline, style = ProgressHeadlineStyle, color = StructureInk)
        Spacer(Modifier.height(4.dp))
        ProgressKpiRow(tiles)
    }
}

/**
 * «Fuerza · e1RM»: cada ejercicio con su estado, su línea, su último e1RM y su cambio. Con tendencia, la cabecera lleva «Todos ›» (la
 * lista entera, con buscador) y debajo va la nota del e1RM; recién empezado, solo el eyebrow y las filas. Tocar una fila abre su hoja.
 */
@Composable
internal fun ProgressLiftsModule(
    rows: List<ProgressLiftRow>,
    early: Boolean,
    onSeeAll: () -> Unit,
    onOpenLift: (ProgressLiftRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier.testTag("progreso_fuerza")) {
        if (early) {
            Text(text = "FUERZA · E1RM", style = StructureEyebrowStyle, color = StructureMuted)
        } else {
            StructureModuleHeader(
                label = "Fuerza · e1RM",
                action = { StructureAction(text = "Todos", onClick = onSeeAll, trailingIcon = KpknLineIcons.ChevronRight, faint = true) },
            )
        }
        rows.forEach { row -> ProgressLiftItem(row = row, onClick = { onOpenLift(row) }) }
        if (!early) {
            Text(
                text = "e1RM = repetición máxima estimada de tu mejor serie de cada sesión.",
                style = StructureCaptionStyle,
                color = StructureFaint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * `.lift`: filete arriba y 11 dp de relleno; a la izquierda el nombre y, debajo, la cápsula del estado con el pie a 6 dp; en el centro la
 * línea de 74 dp; a la derecha el último e1RM y el cambio. Para lectores de pantalla se lee «{nombre}: {estado}, {valor}».
 */
@Composable
internal fun ProgressLiftItem(row: ProgressLiftRow, onClick: (() -> Unit)?, divider: Boolean = true) {
    val color = liftStatusColor(row.status)
    val value = row.currentKg?.let { "${kgText(it)} kg" } ?: "—"
    Column(Modifier.fillMaxWidth()) {
        if (divider) ProgressDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = "${row.name}: ${liftStatusLabel(row.status)}, $value" }
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.name, style = ProgressLiftNameStyle, color = StructureInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiftStatusCapsule(status = row.status)
                    Spacer(Modifier.width(6.dp))
                    Text(text = row.caption, style = ProgressLiftSpanStyle, color = StructureFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ProgressSparkline(values = row.points, color = color)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = buildAnnotatedString {
                        if (row.currentKg == null) {
                            append("—")
                        } else {
                            append(kgText(row.currentKg))
                            withStyle(ProgressKgSpan) { append(" kg") }
                        }
                    },
                    style = ProgressLiftValueStyle,
                    color = StructureInk,
                    maxLines = 1,
                )
                Text(text = formatLiftDelta(row.deltaKg), style = ProgressLiftDeltaStyle, color = color, maxLines = 1)
            }
        }
    }
}

/**
 * «Series duras por semana» (g-work): la cifra «212 / 264» en la cabecera, una barra por semana con lo hecho sobre el contorno
 * discontinuo de lo previsto, las etiquetas, la leyenda y la nota de la semana con déficit. Sin semanas (alcance de semana), la cabecera y
 * la nota.
 */
@Composable
internal fun ProgressWorkModule(
    weeks: List<ProgressWeekBar>,
    setsDone: Int,
    setsPlanned: Int,
    note: String?,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier.testTag("progreso_series")) {
        StructureModuleHeader(
            label = "Series duras por semana",
            action = { Text(text = "$setsDone / $setsPlanned", style = StructureCaptionStyle.tabular(), color = StructureFaint) },
        )
        if (weeks.isNotEmpty()) {
            val scale = (weeks.maxOfOrNull { maxOf(it.done, it.planned) } ?: 0).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(WeekBarZoneHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                weeks.forEach { week -> WeekBar(week = week, scale = scale, modifier = Modifier.weight(1f)) }
            }
            WeekLabels(labels = weeks.map { it.label to it.isCurrent })
            ProgressLegend {
                ProgressLegendItem("Hechas") { ProgressDot(ProgressMus) }
                ProgressLegendItem("Planificadas") { Box(Modifier.size(7.dp).drawBehind { drawDashedCircle() }) }
            }
        }
        if (note != null) {
            Text(text = note, style = StructureCaptionStyle, color = StructureFaint, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * Una semana: el contorno discontinuo a la altura de lo previsto y, encima y alineado abajo, el relleno de lo hecho en el color de su fase
 * al 85 %; la actual, en `--mus` y opaca.
 */
@Composable
private fun WeekBar(week: ProgressWeekBar, scale: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) { contentDescription = "${week.label}: ${week.done} hechas de ${week.planned} planificadas" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(week.planned.toFloat() / scale)
                .drawBehind { drawDashedOutline() },
        )
        if (week.done > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(week.done.toFloat() / scale)
                    .alpha(if (week.isCurrent) 1f else 0.85f)
                    .background(if (week.isCurrent) ProgressMus else week.color, shape),
            )
        }
    }
}

/** `.bars-l`: las etiquetas S1…, a 6 dp de las barras; la actual en tinta y semibold. */
@Composable
private fun WeekLabels(labels: List<Pair<String, Boolean>>) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { (label, current) ->
            Text(
                text = label,
                style = if (current) ProgressBarLabelStyle.copy(fontWeight = FontWeight.SemiBold) else ProgressBarLabelStyle,
                color = if (current) StructureInk else StructureFaint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * «Trabajo acumulado» (g-work): tonelaje («kg × reps»), repeticiones («efectivas») y tiempo con las sesiones; debajo, el filete y
 * «Tonelaje por semana» en barras de 44 dp (las semanas sin tonelaje, una raya de `#ffffff10`).
 */
@Composable
internal fun ProgressTotalsModule(totals: ProgressWorkTotals, modifier: Modifier = Modifier) {
    StructureModule(modifier = modifier.testTag("progreso_trabajo")) {
        Text(text = "TRABAJO ACUMULADO", style = StructureEyebrowStyle, color = StructureMuted)
        ProgressKpiRow(
            listOf(
                ProgressKpiText("Tonelaje", "${formatDecimalEs(totals.tonnageKg / 1000.0, 1)} t", footer = "kg × reps"),
                ProgressKpiText("Repeticiones", thousands(totals.reps), footer = "efectivas"),
                ProgressKpiText(
                    "Tiempo",
                    durationText(totals.minutes),
                    footer = if (totals.sessions == 1) "1 sesión" else "${totals.sessions} sesiones",
                ),
            ),
        )
        if (totals.weeks.isNotEmpty()) {
            ProgressSectionDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Tonelaje por semana", style = StructureCaptionStyle, color = StructureFaint)
                Text(text = "t", style = StructureCaptionStyle.tabular(), color = StructureFaint)
            }
            val top = totals.weeks.maxOf { it.tonnageKg }.takeIf { it > 0.0 } ?: 1.0
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(TonnageZoneHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                totals.weeks.forEach { week ->
                    val share = (week.tonnageKg / top).toFloat().coerceAtLeast(0.03f)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(share)
                            .alpha(if (week.isCurrent) 1f else 0.55f)
                            .background(if (week.tonnageKg > 0.0) StructureInk else Color(0x10FFFFFF), RoundedCornerShape(4.dp))
                            .semantics { contentDescription = "${week.label}: ${formatDecimalEs(week.tonnageKg / 1000.0, 1)} t" },
                    )
                }
            }
        }
    }
}

/** Contorno discontinuo de 1 dp con esquinas de 6 dp: la parte prevista de una barra. */
private fun DrawScope.drawDashedOutline() {
    val stroke = 1.dp.toPx()
    if (size.width <= stroke * 2 || size.height <= stroke * 2) return
    drawRoundRect(
        color = PlannedOutline,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** Punto discontinuo de la leyenda (`border:1px dashed #fff6`). */
private fun DrawScope.drawDashedCircle() {
    val stroke = 1.dp.toPx()
    drawCircle(
        color = Color(0x66FFFFFF),
        radius = (size.minDimension - stroke) / 2f,
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
    )
}

/** Cambio con signo: «+7,5», «−2,5»; «±0» si se redondea a cero; vacío si no hay dato. */
internal fun formatLiftDelta(delta: Double?): String = when {
    delta == null -> ""
    Math.round(delta * 10.0) == 0L -> "±0"
    else -> formatSignedDecimalEs(delta, 1).removeSuffix(",0")
}

/** Entero con punto de miles: «1.986». */
private fun thousands(value: Int): String = String.format(Locale.ROOT, "%,d", value).replace(',', '.')

/** «12 h 40», «12 h», «45 min». */
internal fun durationText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h ${m.toString().padStart(2, '0')}"
    }
}
