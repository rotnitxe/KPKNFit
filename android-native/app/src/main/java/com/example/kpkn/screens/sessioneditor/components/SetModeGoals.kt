package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.safeIntOrNull
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlinx.coroutines.delay

/*
 * Dosis de los modos que no son Reps (escenas ed-serie-solorpe, ed-serie-tiempo y ed-serie-distancia). Mismos campos con − y + que
 * el resto de la serie; la lógica de lo que se guarda no cambia (un único targetRPE, un único targetDuration en segundos, un
 * único valor de distancia o de unidad).
 */

/** B3c · `ed-serie-solorpe`: solo un objetivo de esfuerzo (1–10, de medio en medio), con campo y deslizador, sin pedir una carga fija. */
@Composable
internal fun SoloRpeGoal(value: Double?, onChange: (Double) -> Unit) {
    val current = (value ?: 8.0).coerceIn(1.0, 10.0)
    var slider by remember(value) { mutableStateOf(current.toFloat()) }
    fun commit(raw: Double) = onChange((kotlin.math.round(raw * 2.0) / 2.0).coerceIn(1.0, 10.0))
    EdSection(label = "RPE objetivo", hint = "sin carga fija · esfuerzo de 1 a 10") {
        EdStepperField(
            text = edNumber(slider.toDouble()),
            label = "RPE objetivo",
            onStep = { direction -> commit(current + direction * 0.5) },
            canDecrement = slider > 1f,
            canIncrement = slider < 10f,
            keyboard = KeyboardType.Decimal,
            onCommitText = { input -> input.safeDoubleOrNull()?.takeIf { it.isFinite() }?.let { commit(it) } },
            minusDescription = "Menos RPE",
            plusDescription = "Más RPE",
        )
        EdSlider(
            value = slider,
            onValueChange = { slider = it },
            onValueChangeFinished = { commit(slider.toDouble()) },
            valueRange = 1f..10f,
            steps = 17,
            description = "RPE objetivo",
            startLabel = "1",
            endLabel = "10",
        )
    }
}

/** B3d · `ed-serie-tiempo`: los dos campos guardan un único targetDuration, en segundos. La prueba no modifica la prescripción. */
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
    fun commit(total: Int) = onChange(total.coerceIn(0, 3599))
    EdSection(label = "Duración objetivo", hint = "en vivo · ${edRest(if (running) remaining else duration)}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EdStepperField(
                text = (duration / 60).toString(),
                label = "Minutos",
                small = "min",
                compact = true,
                onStep = { direction -> commit(duration + direction * 60) },
                canDecrement = duration >= 60,
                canIncrement = duration < 3540,
                onCommitText = { input -> input.safeIntOrNull()?.takeIf { it >= 0 }?.let { commit(it * 60 + duration % 60) } },
                modifier = Modifier.weight(1f),
                minusDescription = "Menos minutos",
                plusDescription = "Más minutos",
            )
            EdStepperField(
                text = (duration % 60).toString(),
                label = "Segundos",
                small = "s",
                compact = true,
                onStep = { direction -> commit(duration + direction * 5) },
                canDecrement = duration > 0,
                canIncrement = duration < 3599,
                onCommitText = { input -> input.safeIntOrNull()?.takeIf { it in 0..59 }?.let { commit(duration / 60 * 60 + it) } },
                modifier = Modifier.weight(1f),
                minusDescription = "Menos segundos",
                plusDescription = "Más segundos",
            )
        }
        EdTextLink(
            text = if (running) "Detener prueba" else "Probar cronómetro",
            icon = if (running) KpknLineIcons.Pause else EdIcons.Play,
            onClick = { running = !running },
        )
    }
}

/** B3e/B3f · `ed-serie-distancia`: metros (de 5 en 5) en distancia; la unidad confirmada del ejercicio (de 1 en 1) en personalizado. */
@Composable
internal fun MetricGoal(label: String, hint: String, unit: String?, value: Double?, step: Double, onChange: (Double) -> Unit) {
    fun commit(raw: Double) = onChange(raw.coerceAtLeast(0.0).coerceAtMost(99999.0))
    EdSection(label = label, hint = hint) {
        EdStepperField(
            text = edNumber(value ?: 0.0),
            label = label,
            unit = unit,
            onStep = { direction -> commit((value ?: 0.0) + direction * step) },
            canDecrement = (value ?: 0.0) > 0.0,
            keyboard = KeyboardType.Decimal,
            onCommitText = { input -> input.safeDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let { commit(it) } },
            minusDescription = "Reducir $label",
            plusDescription = "Aumentar $label",
        )
    }
}
