package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.calculations.CardioCalorieInput
import com.example.kpkn.domain.calculations.CardioCalorieEngine
import com.example.kpkn.domain.workout.CardioProgressionSuggestion
import kotlinx.coroutines.delay

@Composable
internal fun CardioLiveCard(
    details: CardioDetails,
    completedSet: CompletedSet?,
    bodyWeightKg: Double?,
    accentColor: Color,
    progressionSuggestion: CardioProgressionSuggestion? = null,
    onRecord: (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) -> Unit,
) {
    var durationText by remember(details.targetDurationSeconds, completedSet?.timeSeconds) {
        mutableStateOf(((completedSet?.timeSeconds ?: details.targetDurationSeconds) / 60).coerceAtLeast(1).toString())
    }
    var distanceText by remember(details.targetDistanceKm, completedSet?.distanceKm) {
        mutableStateOf((completedSet?.distanceKm ?: details.targetDistanceKm)?.toString().orEmpty())
    }
    var heartRateText by remember(completedSet?.avgHeartRate) {
        mutableStateOf(completedSet?.avgHeartRate?.toString().orEmpty())
    }
    val plannedDurationSeconds = (progressionSuggestion?.durationSeconds
        ?: details.targetDurationSeconds).coerceAtLeast(1)
    var timerRunning by remember(details, completedSet?.id) { mutableStateOf(false) }
    var timerRemainingSeconds by remember(details, completedSet?.id) {
        mutableIntStateOf(plannedDurationSeconds)
    }
    var timerElapsedSeconds by remember(details, completedSet?.id) { mutableIntStateOf(0) }

    LaunchedEffect(timerRunning) {
        while (timerRunning && timerRemainingSeconds > 0) {
            delay(1000L)
            timerRemainingSeconds -= 1
            timerElapsedSeconds += 1
            if (timerRemainingSeconds == 0) timerRunning = false
        }
    }
    val durationSeconds = (durationText.toIntOrNull()?.coerceAtLeast(1) ?: (details.targetDurationSeconds / 60)) * 60
        .let { if (timerElapsedSeconds > 0) timerElapsedSeconds else it }
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val heartRate = heartRateText.toIntOrNull()?.coerceIn(30, 240)
    val estimatedCalories = bodyWeightKg?.takeIf { it > 0 }?.let { weight ->
        CardioCalorieEngine.estimate(
            CardioCalorieInput(
            details = details,
            weightKg = weight,
            durationSeconds = durationSeconds,
            averageHeartRate = heartRate,
            ),
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.06f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Cardio · ${details.type.name.replace('_', ' ')}", fontWeight = FontWeight.Black, color = accentColor)
            Text(
                "Objetivo ${details.targetDurationSeconds / 60} min" + (details.targetDistanceKm?.let { " · ${it} km" } ?: "") +
                    " · ${details.intensity.name.replace('_', ' ')}",
                color = Color.White.copy(alpha = 0.68f),
            )
            progressionSuggestion?.let { suggestion ->
                Text(
                    "Progresión 10%: ${suggestion.durationSeconds / 60} min" +
                        (suggestion.distanceKm?.let { " · ${"%.1f".format(it)} km" } ?: "") +
                        " · ${suggestion.reason}",
                    color = accentColor.copy(alpha = 0.9f),
                )
            }
            Text(
                if (timerRunning) "En curso · ${formatCardioTime(timerRemainingSeconds)} restantes"
                else if (timerElapsedSeconds > 0) "Pausado · ${formatCardioTime(timerElapsedSeconds)} realizados"
                else "Listo para cronometrar ${formatCardioTime(plannedDurationSeconds)}",
                color = if (timerRunning) accentColor else Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = {
                    if (timerRunning) {
                        timerRunning = false
                        if (timerElapsedSeconds > 0) durationText = ((timerElapsedSeconds + 59) / 60).toString()
                    } else {
                        if (timerRemainingSeconds <= 0) {
                            timerRemainingSeconds = plannedDurationSeconds
                            timerElapsedSeconds = 0
                        }
                        timerRunning = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (timerRunning) "Pausar" else "Iniciar cardio") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Minutos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (details.supportsDistance) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { distanceText = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(8) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(
                    value = heartRateText,
                    onValueChange = { heartRateText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    label = { Text("FC media") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text(
                "Calorías estimadas: ${estimatedCalories?.let { "%.0f kcal".format(it) } ?: "añade peso corporal"}",
                color = Color.White.copy(alpha = 0.7f),
            )
            Button(
                onClick = {
                    timerRunning = false
                    onRecord(durationSeconds, distanceKm, heartRate)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (completedSet == null) "Registrar cardio" else "Actualizar cardio") }
        }
    }
}

private fun formatCardioTime(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val seconds = totalSeconds.coerceAtLeast(0) % 60
    return "%02d:%02d".format(minutes, seconds)
}
