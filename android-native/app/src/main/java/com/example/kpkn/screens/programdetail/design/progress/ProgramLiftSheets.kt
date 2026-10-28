package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.structure.StructureBodyStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureCaptionStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureEyebrowStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted
import com.example.kpkn.screens.programdetail.design.structure.StructureSecondarySmallPill
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetGroup
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetPadding
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetHeader
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetSubStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetTitleStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureTextField
import com.example.kpkn.ui.components.KpknSheet
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Hojas de la fuerza de la pestaña Progreso: el detalle de un ejercicio (lienzo v6, g-lift) y la lista completa de «Todos», con buscador
 * (nota de g-summary; sin escena propia, con las mismas filas `.lift`).
 */

/** Gráfica de g-lift: `viewBox 0 0 330 130`, a todo el ancho. */
private const val CHART_W = 330f
private const val CHART_H = 130f

/** Gris de los rótulos de la gráfica (`#8e8980`). */
private val ChartLabel = Color(0xFF8E8980)

/**
 * La hoja de un ejercicio: el nombre (`.ey`), el estado (`h3`), la explicación, la línea de e1RM por sesión con la ventana de
 * estancamiento sombreada, «Mejores series», «Lectura» y dos píldoras: «Historial completo» y, si sigue en el plan, «Cambiar ejercicio».
 */
@Composable
internal fun ProgramLiftDetailSheet(
    detail: ProgressLiftDetail,
    onOpenHistory: () -> Unit,
    onSwap: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding)
                .testTag("progreso_hoja_ejercicio"),
        ) {
            Text(text = detail.name.uppercase(Locale.forLanguageTag("es")), style = StructureEyebrowStyle, color = StructureMuted)
            Text(
                text = detail.title,
                style = StructureSheetTitleStyle,
                color = StructureInk,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
            detail.summary?.let {
                Text(text = it, style = StructureSheetSubStyle, color = StructureMuted, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (detail.points.isNotEmpty()) {
                LiftChart(
                    points = detail.points,
                    shadeFrom = detail.shadeFrom,
                    color = liftStatusColor(detail.status),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (detail.bestSets.isNotEmpty()) {
                StructureSheetGroup(text = "Mejores series")
                detail.bestSets.forEach { best ->
                    ProgressRatioRow {
                        Text(text = best.dateLabel, style = ProgressRatioStyle, color = StructureInk, modifier = Modifier.weight(1f))
                        Text(text = best.detail, style = StructureCaptionStyle, color = StructureFaint)
                        Text(text = kgText(best.e1rmKg), style = ProgressRatioValueStyle, color = StructureInk)
                    }
                }
            }
            detail.reading?.let {
                StructureSheetGroup(text = "Lectura")
                Text(text = it, style = StructureBodyStyle, color = StructureMuted)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StructureSecondarySmallPill(
                    text = "Historial completo",
                    onClick = onOpenHistory,
                    modifier = Modifier.weight(1f).testTag("ejercicio_historial"),
                )
                if (onSwap != null) {
                    StructureSecondarySmallPill(
                        text = "Cambiar ejercicio",
                        onClick = onSwap,
                        modifier = Modifier.weight(1f).testTag("ejercicio_cambiar"),
                    )
                }
            }
        }
    }
}

/**
 * La línea de e1RM de g-lift, en las coordenadas del `viewBox` (330 × 130): la base a 18 del pie, las líneas guía con su cifra a la
 * derecha, la ventana sombreada (`#f7cf7314`, radio 8) desde [shadeFrom], la línea de 2 y un círculo de 3 por sesión (relleno `#111`,
 * borde de 1,6) y, debajo, la etiqueta de la semana en la primera sesión de cada una.
 */
@Composable
private fun LiftChart(points: List<LiftDetailPoint>, shadeFrom: Int?, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val values = points.map { it.e1rmKg }
    val low = floor(values.minOf { it }) - 1.0
    val high = ceil(values.maxOf { it }) + 1.0
    val step = gridStep(high - low)
    val grid = generateSequence(ceil((low + 0.001) / step) * step) { it + step }.takeWhile { it < high - 0.001 }.toList()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CHART_W / CHART_H)
            .semantics { contentDescription = "e1RM por sesión" },
    ) {
        val u = size.width / CHART_W
        fun y(v: Double): Float = (CHART_H - 18f - ((v - low) / (high - low)).toFloat() * (CHART_H - 36f)) * u
        fun x(i: Int): Float = if (points.size == 1) CHART_W / 2f * u else (14f + i * (CHART_W - 28f) / (points.size - 1)) * u
        drawLine(Color(0x14FFFFFF), Offset(10f * u, (CHART_H - 18f) * u), Offset((CHART_W - 10f) * u, (CHART_H - 18f) * u), strokeWidth = u)
        grid.forEach { v ->
            drawLine(Color(0x0AFFFFFF), Offset(10f * u, y(v)), Offset((CHART_W - 10f) * u, y(v)), strokeWidth = u)
            chartText(measurer, kgText(v), anchorX = (CHART_W - 8f) * u, baselineY = y(v) - 3f * u, u = u, end = true)
        }
        if (shadeFrom != null && shadeFrom in points.indices) {
            val left = x(shadeFrom) - 8f * u
            drawRoundRect(
                color = Color(0x14F7CF73),
                topLeft = Offset(left, 6f * u),
                size = Size(x(points.lastIndex) + 8f * u - left, (CHART_H - 24f) * u),
                cornerRadius = CornerRadius(8f * u),
            )
        }
        if (points.size > 1) {
            val path = Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            drawPath(path, color = color, style = Stroke(width = 2f * u))
        }
        values.forEachIndexed { i, v ->
            drawCircle(Color(0xFF111111), radius = 3f * u, center = Offset(x(i), y(v)))
            drawCircle(color, radius = 3f * u, center = Offset(x(i), y(v)), style = Stroke(width = 1.6f * u))
        }
        points.forEachIndexed { i, point ->
            point.weekLabel?.let { chartText(measurer, it, anchorX = x(i), baselineY = (CHART_H - 4f) * u, u = u, end = false) }
        }
    }
}

/** Paso de las líneas guía: 1 kg con un rango corto (como el lienzo: 127, 128, 129) y, si no, el que deja como mucho 4 líneas. */
private fun gridStep(range: Double): Double = listOf(1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0).firstOrNull { range / it <= 5.0 } ?: 100.0

/** Rótulo de la gráfica: Inter 9 en `#8e8980`, anclado al final (cifras) o al centro (semanas) sobre su línea base. */
private fun DrawScope.chartText(measurer: TextMeasurer, text: String, anchorX: Float, baselineY: Float, u: Float, end: Boolean) {
    val layout = measurer.measure(
        text = text,
        style = TextStyle(fontFamily = WizardFonts.body, fontSize = (9f * u).toSp(), color = ChartLabel),
    )
    val left = if (end) anchorX - layout.size.width else anchorX - layout.size.width / 2f
    drawText(layout, topLeft = Offset(left, baselineY - layout.firstBaseline))
}

/**
 * «Todos»: la lista completa de los ejercicios con sesiones en el alcance, con buscador. Cada fila es la de la lista de fuerza y abre su
 * detalle.
 */
@Composable
internal fun ProgramAllLiftsSheet(
    lifts: List<ProgressLiftRow>,
    onOpenLift: (ProgressLiftRow) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(lifts, query) {
        val needle = query.trim().lowercase(Locale.forLanguageTag("es"))
        if (needle.isEmpty()) lifts else lifts.filter { it.name.lowercase(Locale.forLanguageTag("es")).contains(needle) }
    }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding)
                .testTag("progreso_hoja_todos"),
        ) {
            StructureSheetHeader(title = "Fuerza · e1RM", subtitle = if (lifts.size == 1) "1 ejercicio" else "${lifts.size} ejercicios")
            StructureTextField(label = "Buscar", value = query, onValueChange = { query = it }, placeholder = "Nombre del ejercicio")
            Column(Modifier.padding(top = 8.dp)) {
                shown.forEachIndexed { index, row ->
                    ProgressLiftItem(row = row, onClick = { onOpenLift(row) }, divider = index > 0)
                }
                if (shown.isEmpty()) {
                    Text(
                        text = "Ningún ejercicio con ese nombre.",
                        style = StructureCaptionStyle,
                        color = StructureFaint,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
