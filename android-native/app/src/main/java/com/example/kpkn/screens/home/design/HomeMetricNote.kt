package com.example.kpkn.screens.home.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknType

/**
 * Una cifra con su unidad ([KpknMetric]) y, a su derecha, una nota de una línea (KPKN_UI_V2 §5). Si la nota no cabe en la misma fila, baja
 * debajo de la cifra: a 130 % de letra la nota nunca se corta ni se parte por letras. [noteIcon], si se pasa, va delante de la nota.
 */
@Composable
internal fun HomeMetricNote(
    value: String,
    unit: String?,
    note: String?,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = KpknType.metricL,
    noteStyle: TextStyle = KpknType.caption,
    noteColor: Color = WizardColors.textMuted,
    noteIcon: ImageVector? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availablePx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { 10.dp.toPx() }
        val unitPx: Float = unit?.let { with(density) { 3.dp.toPx() } + measurer.measure(it, KpknType.unit).size.width } ?: 0f
        val valuePx: Float = measurer.measure(value, valueStyle, maxLines = 1).size.width + unitPx
        val notePx: Float = note?.let {
            measurer.measure(it, noteStyle, maxLines = 1).size.width + (if (noteIcon != null) with(density) { 22.dp.toPx() } else 0f)
        } ?: 0f
        val sameRow = note == null || valuePx + gapPx + notePx <= availablePx

        val noteLine: @Composable () -> Unit = {
            if (note != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (noteIcon != null) {
                        Icon(imageVector = noteIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = noteColor)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = note,
                        style = noteStyle,
                        color = noteColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (sameRow) {
            Row(verticalAlignment = Alignment.Bottom) {
                KpknMetric(value = value, unit = unit, style = valueStyle)
                if (note != null) {
                    Spacer(Modifier.width(10.dp))
                    // La nota se asienta en la línea base de la cifra (la cifra va con su descenso bajo la línea base).
                    Box(Modifier.padding(bottom = 4.dp)) { noteLine() }
                }
            }
        } else {
            Column {
                KpknMetric(value = value, unit = unit, style = valueStyle)
                noteLine()
            }
        }
    }
}
