package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknType

/** Signo menos de las cifras de gasto: U+2212, no un guion. */
private const val MinusSign = "−"

/** Cifra de gasto de un canal: «−32»; sin gasto, «0». */
internal fun sessionDrainText(value: Int): String = if (value > 0) "$MinusSign$value" else "0"

/**
 * Las tres cifras del impacto (SE4): Músculos, Energía y Columna, una por fila. Cada fila lleva el nombre en `caption` a la izquierda y la
 * cifra en `metricM`, en %, con el color de su anillo, a la derecha. Así el bloque no supera la altura de la torre de 72 dp. La usan el
 * módulo y la pestaña Impacto.
 */
@Composable
fun SessionImpactFigures(channels: SessionImpactChannels, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SessionImpactFigure(name = "Músculos", value = channels.musculos, color = KpknRingColors.musculos)
        SessionImpactFigure(name = "Energía", value = channels.energia, color = KpknRingColors.energia)
        SessionImpactFigure(name = "Columna", value = channels.columna, color = KpknRingColors.columna)
    }
}

@Composable
private fun SessionImpactFigure(name: String, value: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        KpknMetric(value = sessionDrainText(value), unit = "%", style = KpknType.metricM, color = color)
    }
}
