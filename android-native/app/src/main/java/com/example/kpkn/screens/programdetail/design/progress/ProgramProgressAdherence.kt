package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.structure.StructureAction
import com.example.kpkn.screens.programdetail.design.structure.StructureCaptionStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureModule
import com.example.kpkn.screens.programdetail.design.structure.StructureModuleHeader
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted
import com.example.kpkn.screens.programdetail.design.structure.tabular
import com.example.kpkn.ui.components.icons.KpknLineIcons

/*
 * Módulos de la escena g-adh del lienzo v6: «Cumplimiento», «Esfuerzo registrado», «Molestias» y «Decisiones del sistema». Reciben ya
 * calculado lo que muestran (ProgramProgressAdherenceModel.kt).
 */

/** Columnas de la rejilla de cumplimiento por fila; con más semanas, la rejilla sigue en otra fila. */
private const val ADHERENCE_COLUMNS = 12

/**
 * «Cumplimiento»: el porcentaje en la cabecera, una columna por semana con un cuadro por sesión (hecha, faltó, futura) y, si los hay,
 * los ejercicios más omitidos tras un filete.
 */
@Composable
internal fun ProgressAdherenceModule(adherence: ProgressAdherence, modifier: Modifier = Modifier) {
    StructureModule(modifier = modifier.testTag("progreso_cumplimiento")) {
        StructureModuleHeader(
            label = "Cumplimiento",
            action = {
                Text(
                    text = buildAnnotatedString {
                        append(adherence.pct?.toString() ?: "—")
                        if (adherence.pct != null) {
                            withStyle(SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = StructureMuted, letterSpacing = 0.sp)) {
                                append(" %")
                            }
                        }
                    },
                    style = ProgressNumStyle,
                    color = StructureInk,
                )
            },
        )
        val columns = adherence.weeks.size.coerceIn(1, ADHERENCE_COLUMNS)
        adherence.weeks.chunked(ADHERENCE_COLUMNS).forEach { chunk ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.forEach { week -> AdherenceColumn(week, Modifier.weight(1f)) }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (adherence.mostOmitted.isNotEmpty()) {
            ProgressSectionDivider()
            Text(
                text = buildAnnotatedString {
                    append("Más omitidos: ")
                    adherence.mostOmitted.forEachIndexed { index, omitted ->
                        if (index > 0) append(" · ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = StructureMuted)) { append(omitted.name) }
                        append(if (index == 0) " (${omitted.count} ${if (omitted.count == 1) "vez" else "veces"})" else " (${omitted.count})")
                    }
                },
                style = StructureCaptionStyle,
                color = StructureFaint,
            )
        }
    }
}

/** Una semana de la rejilla: sus cuadros de 9 dp (3 dp entre ellos) y la etiqueta debajo. */
@Composable
private fun AdherenceColumn(week: ProgressAdherenceWeek, modifier: Modifier = Modifier) {
    val done = week.cells.count { it == AdherenceCell.DONE }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${week.label}: $done de ${week.cells.size} sesiones"
        },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        week.cells.forEach { cell ->
            val shape = RoundedCornerShape(3.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .then(
                        when (cell) {
                            AdherenceCell.DONE -> Modifier.background(ProgressDone, shape)
                            AdherenceCell.MISSED -> Modifier.background(Color(0x55FF9B92), shape)
                            AdherenceCell.FUTURE -> Modifier.background(Color(0x08FFFFFF), shape).dashedBorder(shape)
                        },
                    ),
            )
        }
        Text(
            text = week.label,
            style = ProgressBarLabelStyle.copy(fontSize = 9.sp, lineHeight = 12.sp),
            color = StructureFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** Contorno discontinuo de 1 dp (`outline:1px dashed #ffffff18`) de un cuadro futuro. */
private fun Modifier.dashedBorder(shape: RoundedCornerShape): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = Color(0x18FFFFFF),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = stroke,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
        ),
    )
}

/**
 * «Esfuerzo registrado»: «RIR medio» en la cabecera; por semana, «plan N» y el RIR registrado (en ámbar si fue más cerca del fallo de lo
 * pedido) y, debajo, la nota.
 */
@Composable
internal fun ProgressEffortModule(effort: ProgressEffort, modifier: Modifier = Modifier) {
    StructureModule(modifier = modifier.testTag("progreso_esfuerzo")) {
        StructureModuleHeader(
            label = "Esfuerzo registrado",
            action = { Text(text = "RIR medio", style = StructureCaptionStyle, color = StructureFaint) },
        )
        effort.rows.forEach { row ->
            ProgressRatioRow {
                Text(text = row.label, style = ProgressRatioStyle, color = StructureInk, modifier = Modifier.weight(1f))
                row.plannedRir?.let {
                    Text(text = "plan ${formatPlannedRir(it)}", style = StructureCaptionStyle, color = StructureFaint)
                }
                Text(
                    text = formatRir(row.rir),
                    style = ProgressRatioValueStyle,
                    color = if (row.closerThanPlanned) ProgressEne else StructureInk,
                )
            }
        }
        effort.note?.let {
            Text(text = it, style = StructureCaptionStyle, color = StructureFaint, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** «Molestias»: el periodo en la cabecera; cada molestia con sus veces y, debajo, con qué ejercicios coincide. */
@Composable
internal fun ProgressDiscomfortModule(discomforts: ProgressDiscomforts, modifier: Modifier = Modifier) {
    StructureModule(modifier = modifier.testTag("progreso_molestias")) {
        StructureModuleHeader(
            label = "Molestias",
            action = { Text(text = discomforts.window, style = StructureCaptionStyle, color = StructureFaint) },
        )
        discomforts.rows.forEach { row ->
            Column(Modifier.fillMaxWidth()) {
                ProgressDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = row.name, style = ProgressRatioStyle, color = StructureInk, modifier = Modifier.weight(1f))
                        Text(
                            text = row.count.toString(),
                            style = ProgressRatioStyle.copy(fontWeight = FontWeight.Bold).tabular(),
                            color = StructureInk,
                        )
                    }
                    row.coincidence?.let { Text(text = it, style = StructureCaptionStyle, color = StructureFaint) }
                }
            }
        }
    }
}

/**
 * «Decisiones del sistema»: las tres últimas en una línea de tiempo (línea de 1 dp a 4 dp del borde y un círculo de 9 dp por entrada) y
 * «Todas ›», que abre la hoja con todas.
 */
@Composable
internal fun ProgressDecisionsModule(rows: List<ProgressDecisionRow>, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    StructureModule(modifier = modifier.testTag("progreso_decisiones")) {
        StructureModuleHeader(
            label = "Decisiones del sistema",
            action = { StructureAction(text = "Todas", onClick = onSeeAll, trailingIcon = KpknLineIcons.ChevronRight, faint = true) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val x = 4.5.dp.toPx()
                    drawLine(Color(0x18FFFFFF), Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), strokeWidth = 1.dp.toPx())
                }
                .padding(start = 18.dp),
        ) {
            rows.forEach { row ->
                Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
                    Box(
                        Modifier
                            .offset(x = (-18).dp, y = 5.dp)
                            .size(9.dp)
                            .background(Color(0xFF111111), CircleShape)
                            .border(2.dp, StructureMuted, CircleShape),
                    )
                    Column {
                        Text(text = row.title, style = ProgressTimelineTitleStyle, color = StructureInk)
                        Spacer(Modifier.height(2.dp))
                        Text(text = row.line, style = ProgressTimelineLineStyle, color = StructureFaint)
                    }
                }
            }
        }
    }
}

