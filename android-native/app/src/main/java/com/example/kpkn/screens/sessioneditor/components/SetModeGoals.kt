package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/** B3c: solo un objetivo de esfuerzo, sin pedir una carga fija. */
@Composable
internal fun SoloRpeGoal(value: Double?, onChange: (Double) -> Unit) {
    var slider by remember(value) { mutableStateOf((value ?: 8.0).toFloat().coerceIn(1f, 10f)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetValueCell(label = "RPE objetivo", value = formatEditableNumber(value), stateKey = "solo-rpe", listSkin = true,
            modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Decimal,
            onCommit = { input -> input.safeDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..10.0 }?.let(onChange) })
        Slider(value = slider, onValueChange = { slider = it }, onValueChangeFinished = { onChange(slider.toDouble()) },
            valueRange = 1f..10f, steps = 17, colors = inkSliderColors(), modifier = Modifier.fillMaxWidth())
        Text("Sin carga fija · esfuerzo de 1 a 10", style = KpknType.caption, color = WizardColors.textMuted)
    }
}

/** B3d: los dos campos guardan un único targetDuration, en segundos. La prueba no modifica la prescripción. */
@Composable
internal fun DurationGoal(seconds: Int?, onChange: (Int) -> Unit) {
    val duration = seconds?.coerceAtLeast(0) ?: 0
    var running by remember { mutableStateOf(false) }
    var remaining by remember(duration) { mutableStateOf(duration) }
    LaunchedEffect(running, duration) {
        if (running) {
            remaining = duration
            while (remaining > 0) { delay(1000); remaining-- }
            running = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Duración objetivo", style = KpknType.caption, color = WizardColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetValueCell(label = "Minutos", value = (duration / 60).toString(), stateKey = "duration-minutes", listSkin = true,
                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, onCommit = { input ->
                    input.safeIntOrNull()?.takeIf { it >= 0 && it.toLong() * 60 + duration % 60 <= Int.MAX_VALUE }?.let { onChange(it * 60 + duration % 60) }
                })
            SetValueCell(label = "Segundos", value = (duration % 60).toString(), stateKey = "duration-seconds", listSkin = true,
                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, onCommit = { input ->
                    input.safeIntOrNull()?.takeIf { it in 0..59 }?.let { onChange(duration / 60 * 60 + it) }
                })
        }
        Text("Así se cronometrará en vivo · ${KpknSetFormat.doseTime(if (running) remaining else duration)}",
            style = KpknType.caption, color = WizardColors.textMuted)
        KpknTextAction(text = if (running) "Detener prueba" else "Probar cronómetro", onClick = { running = !running })
    }
}

/** B3e/B3f: metros en distancia; la unidad confirmada del ejercicio en personalizado. */
@Composable
internal fun MetricGoal(label: String, value: Double?, step: Double, onChange: (Double) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        KpknIconButton(icon = KpknLineIcons.Minus, contentDescription = "Reducir $label",
            onClick = { onChange(((value ?: 0.0) - step).coerceAtLeast(0.0)) })
        SetValueCell(label = label, value = formatEditableNumber(value), stateKey = "metric-goal", listSkin = true,
            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
            onCommit = { input -> input.safeDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let(onChange) })
        KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Aumentar $label",
            onClick = { onChange((value ?: 0.0) + step) })
    }
}
