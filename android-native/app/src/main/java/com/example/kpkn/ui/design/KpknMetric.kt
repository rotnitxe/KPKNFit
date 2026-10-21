package com.example.kpkn.ui.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Cifra con unidad (KPKN_UI_V2 §5): la cifra en [style] y la unidad en [unitStyle], sobre la misma línea base y con 3 dp entre las dos.
 * Su semántica lee el texto entero («92 %»). Sin [unit], solo la cifra.
 */
@Composable
fun KpknMetric(
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = KpknType.metricL,
    unitStyle: TextStyle = KpknType.unit,
    color: Color = WizardColors.text,
    unitColor: Color = WizardColors.textMuted,
) {
    val unitText = unit.orEmpty()
    val hasUnit = unitText.isNotEmpty()
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = if (hasUnit) "$value $unitText" else value
        },
    ) {
        Text(
            text = value,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (hasUnit) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = unitText,
                style = unitStyle,
                color = unitColor,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
