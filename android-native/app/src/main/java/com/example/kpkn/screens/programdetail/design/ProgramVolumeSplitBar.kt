package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType

/**
 * Reparto de series entre empuje, tirón y pierna (P4): una barra apilada de 8 dp con los colores de su grupo (músculo, columna y
 * energía) y una leyenda con el porcentaje de cada uno. Sin series, lo dice en vez de dibujar una barra vacía.
 */
@Composable
fun ProgramVolumeSplitBar(
    split: ProgramVolumeSplit,
    modifier: Modifier = Modifier,
) {
    val total = split.push + split.pull + split.legs
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Reparto de empuje, tirón y pierna",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (total <= 0.0) {
            Text(
                text = "Sin series que repartir.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(KpknShape.pill),
            ) {
                if (split.push > 0.0) SplitSegment(split.push / total, SymbolPalette.musculo)
                if (split.pull > 0.0) SplitSegment(split.pull / total, SymbolPalette.columna)
                if (split.legs > 0.0) SplitSegment(split.legs / total, SymbolPalette.energia)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                SplitLegend(
                    label = "Empuje",
                    percent = programVolumeShare(split.push, total),
                    color = SymbolPalette.musculo,
                    modifier = Modifier.weight(1f),
                )
                SplitLegend(
                    label = "Tirón",
                    percent = programVolumeShare(split.pull, total),
                    color = SymbolPalette.columna,
                    modifier = Modifier.weight(1f),
                )
                SplitLegend(
                    label = "Pierna",
                    percent = programVolumeShare(split.legs, total),
                    color = SymbolPalette.energia,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Tramo de la barra apilada: ocupa la fracción [fraction] del ancho, en [color]. */
@Composable
private fun RowScope.SplitSegment(fraction: Double, color: Color) {
    Box(
        modifier = Modifier
            .weight(fraction.toFloat())
            .fillMaxHeight()
            .background(color),
    )
}

/** Punto de color, nombre del grupo y su porcentaje sobre la misma línea de lectura. */
@Composable
private fun SplitLegend(
    label: String,
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
            Text(
                text = "$percent %",
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
            )
        }
    }
}
