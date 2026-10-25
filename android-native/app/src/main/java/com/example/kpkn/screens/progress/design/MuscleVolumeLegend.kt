package com.example.kpkn.screens.progress.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.progress.RegionKind
import com.example.kpkn.screens.progress.RegionPaint
import com.example.kpkn.ui.design.KpknType

/** Una entrada de la leyenda: cómo se pinta ([paint]) y qué significa ([label]). */
data class LegendEntry(val paint: RegionPaint, val label: String)

/**
 * Leyenda del cuerpo (T5a): el título con la unidad y una muestra por escalón, con la misma pintura que las regiones. «Sin datos»
 * (trazo discontinuo) y «0» (trazo liso) son entradas distintas. Se lee como una sola frase para TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleVolumeLegend(
    title: String,
    entries: List<LegendEntry>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("muscle-volume-legend")
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: " + entries.joinToString(", ") { it.label }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = KpknType.caption, color = WizardColors.textMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(entry.paint)
                    Spacer(Modifier.width(6.dp))
                    Text(text = entry.label, style = KpknType.caption, color = WizardColors.text, maxLines = 1)
                }
            }
        }
    }
}

/** Muestra de 16 dp con la misma forma de pintar que el cuerpo. */
@Composable
private fun Swatch(paint: RegionPaint) {
    Canvas(Modifier.size(16.dp)) {
        val inset = 0.5.dp.toPx()
        val topLeft = Offset(inset, inset)
        val box = Size(size.width - inset * 2, size.height - inset * 2)
        val radius = CornerRadius(4.dp.toPx())
        when (paint.kind) {
            RegionKind.NO_DATA -> drawRoundRect(
                color = paint.edge,
                topLeft = topLeft,
                size = box,
                cornerRadius = radius,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
            )
            RegionKind.ZERO -> {
                drawRoundRect(paint.fill, topLeft, box, radius)
                drawRoundRect(paint.edge, topLeft, box, radius, style = Stroke(width = 1.dp.toPx()))
            }
            RegionKind.FILLED -> {
                drawRoundRect(paint.fill, topLeft, box, radius)
                drawRoundRect(paint.edge, topLeft, box, radius, style = Stroke(width = 1.dp.toPx()))
            }
        }
    }
}
