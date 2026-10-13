package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknType

/**
 * Ajuste manual de los macros de un alimento (N3b): calorías, proteína, hidratos y grasas, cada una con su valor y sus botones ±
 * («Menos proteína», «Más proteína»). Cada pulsación llama a [onChange] con el macro y el valor nuevo, sin bajar de cero.
 */
@Composable
fun LoggerMacroSteppers(
    calories: Double,
    protein: Double,
    carbs: Double,
    fats: Double,
    onChange: (LoggerMacro, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "Ajustar macros", style = KpknType.caption, color = WizardColors.textMuted)
        LoggerMacroStepper(label = "Calorías", unit = "kcal", value = calories, step = 5.0) {
            onChange(LoggerMacro.CALORIES, it)
        }
        LoggerMacroStepper(label = "Proteína", unit = "g", value = protein, step = 1.0) {
            onChange(LoggerMacro.PROTEIN, it)
        }
        LoggerMacroStepper(label = "Hidratos", unit = "g", value = carbs, step = 1.0) {
            onChange(LoggerMacro.CARBS, it)
        }
        LoggerMacroStepper(label = "Grasas", unit = "g", value = fats, step = 1.0) {
            onChange(LoggerMacro.FATS, it)
        }
    }
}

/** Una fila: el nombre, la cifra con su unidad y los botones de menos y más, con zona táctil de 48 dp. */
@Composable
private fun LoggerMacroStepper(
    label: String,
    unit: String,
    value: Double,
    step: Double,
    onValue: (Double) -> Unit,
) {
    val macro = label.lowercase()
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        KpknMetric(
            value = kotlin.math.round(value).toInt().toString(),
            unit = unit,
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
        )
        Spacer(Modifier.width(8.dp))
        KpknIconButton(
            icon = KpknLineIcons.Minus,
            contentDescription = "Menos $macro",
            onClick = { onValue((value - step).coerceAtLeast(0.0)) },
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Más $macro",
            onClick = { onValue((value + step).coerceAtMost(9999.0)) },
        )
    }
}
