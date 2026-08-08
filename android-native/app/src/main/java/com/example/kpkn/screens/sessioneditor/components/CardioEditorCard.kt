package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.calculations.CardioCalorieEngine

@Composable
internal fun CardioEditorCard(
    details: CardioDetails,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
) {
    var durationText by remember(details.targetDurationSeconds) {
        mutableStateOf((details.targetDurationSeconds / 60).coerceAtLeast(1).toString())
    }
    var distanceText by remember(details.targetDistanceKm) {
        mutableStateOf(details.targetDistanceKm?.toString().orEmpty())
    }
    val defaultMet = CardioCalorieEngine.defaultMet(details.type, details.intensity)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("CARDIO", color = accentColor, fontWeight = FontWeight.Black)
            Text(
                "${cardioTypeLabel(details.type)} · MET estimado ${"%.1f".format(defaultMet)} · ${if (details.requiresGps) "GPS opcional" else "sin GPS"}",
                color = Color.White.copy(alpha = 0.68f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { value ->
                        durationText = value.filter { it.isDigit() }.take(4)
                        durationText.toIntOrNull()?.coerceIn(1, 999)?.let { minutes ->
                            onChange(details.copy(targetDurationSeconds = minutes * 60))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Minutos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (details.supportsDistance) {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = { value ->
                            distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                            onChange(details.copy(targetDistanceKm = distanceText.replace(',', '.').toDoubleOrNull()))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Distancia km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            Text("Intensidad", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.82f))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CardioIntensity.entries.forEach { intensity ->
                    FilterChip(
                        selected = details.intensity == intensity,
                        onClick = { onChange(details.copy(intensity = intensity)) },
                        label = { Text(intensityLabel(intensity)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.32f),
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Registrar GPS en vivo", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.84f))
                    Text("Opcional: si no hay permiso, se conserva el registro manual.", color = Color.White.copy(alpha = 0.56f))
                }
                Switch(checked = details.requiresGps, onCheckedChange = { onChange(details.copy(requiresGps = it)) })
            }
        }
    }
}

private fun cardioTypeLabel(type: CardioType): String = when (type) {
    CardioType.TREADMILL -> "Cinta"
    CardioType.ELLIPTICAL -> "Elíptica"
    CardioType.ROW_MACHINE -> "Remo"
    CardioType.BIKE_STATIONARY -> "Bici estática"
    CardioType.RUN_OUTDOOR -> "Carrera exterior"
    CardioType.BIKE_OUTDOOR -> "Bici exterior"
    CardioType.WALK -> "Caminata"
    CardioType.STAIR_CLIMBER -> "Escaladora"
}

private fun intensityLabel(intensity: CardioIntensity): String = when (intensity) {
    CardioIntensity.BAJA -> "Baja"
    CardioIntensity.MEDIA -> "Media"
    CardioIntensity.ALTA -> "Alta"
    CardioIntensity.MUY_ALTA -> "Muy alta"
}
