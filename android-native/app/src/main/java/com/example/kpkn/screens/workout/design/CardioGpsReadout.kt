package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknType

/**
 * Distancia y ritmo del GPS (KPKN LS5b): dos columnas, con la cifra en `metricL` tabular y su unidad en `caption` («km», «/km») en la
 * misma línea base. Sin dato, «—» y sin unidad. Sin estado.
 */
@Composable
fun CardioGpsReadout(
    distanceValue: String?,
    paceValue: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        CardioGpsColumn(
            label = "Distancia",
            value = distanceValue,
            unit = "km",
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        CardioGpsColumn(
            label = "Ritmo",
            value = paceValue,
            unit = "/km",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CardioGpsColumn(
    label: String,
    value: String?,
    unit: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
        KpknMetric(
            value = value ?: "—",
            unit = if (value != null) unit else null,
            style = KpknType.metricL,
            unitStyle = KpknType.caption,
            unitColor = WizardColors.textMuted,
        )
    }
}
