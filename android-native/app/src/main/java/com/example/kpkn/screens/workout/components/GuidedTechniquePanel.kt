package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults

@Composable
internal fun GuidedTechniquePanel(
    phase: GuidedTechniquePhase,
    accentColor: Color,
    dropWeightText: String,
    dropRepsText: String,
    restPauseRepsText: String,
    onDropWeightChange: (String) -> Unit,
    onDropRepsChange: (String) -> Unit,
    onRestPauseRepsChange: (String) -> Unit,
    onSkipTechnique: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (phase) {
                is GuidedTechniquePhase.DropSet -> {
                    Text(
                        "DROP-SET ${phase.index + 1}/${phase.total}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                    Text(
                        "Sin descanso. Baja el peso y completa ~${RestPausePlanDefaults.Reps} reps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WorkoutMiniTextField(
                            value = dropWeightText,
                            onValueChange = onDropWeightChange,
                            label = "Peso sugerido",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f),
                        )
                        WorkoutMiniTextField(
                            value = dropRepsText,
                            onValueChange = onDropRepsChange,
                            label = "Reps",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Sugerido: ${phase.suggestedWeight} kg para ~${RestPausePlanDefaults.Reps} reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }

                is GuidedTechniquePhase.RestPauseCountdown -> {
                    Text(
                        "REST-PAUSE ${phase.index + 1}/${phase.total}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${phase.secondsLeft}s",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            phase.secondsLeft.toFloat() / RestPausePlanDefaults.PauseSeconds.toFloat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.18f),
                    )
                    Text(
                        "Pausa fija de ${RestPausePlanDefaults.PauseSeconds}s. Luego harás ${RestPausePlanDefaults.Reps} reps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }

                is GuidedTechniquePhase.RestPauseReps -> {
                    Text(
                        "REST-PAUSE ${phase.index + 1}/${phase.total}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                    Text(
                        "Confirma las ${RestPausePlanDefaults.Reps} reps de esta mini-serie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    WorkoutMiniTextField(
                        value = restPauseRepsText,
                        onValueChange = onRestPauseRepsChange,
                        label = "Reps",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            TextButton(onClick = onSkipTechnique) {
                Text(
                    "Saltar técnica y registrar solo la serie",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
