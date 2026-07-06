package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MuscleAdvance
import kotlin.math.roundToInt

@Composable
fun VolumeAdvanceModal(
    advances: List<MuscleAdvance>,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProtectedWorkoutBottomSheet(
        title = "Adelanto de volumen",
        onDismiss = onDismiss,
        showCloseButton = false,
    ) {
        Text(
            text = "Completaste más series de las planificadas. Puedes descontar ese volumen en la próxima sesión de la semana que use los mismos músculos.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f),
        )

        advances.forEach { advance ->
            val surplusSets = advance.deficitSets.roundToInt().coerceAtLeast(1)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2A2A2A),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = advance.muscleName.ifBlank { advance.muscleId },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        text = "+$surplusSets series extra en esta sesión",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7DD3FC),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Próxima sesión: ${advance.targetSessionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    advance.discountProposals.forEach { proposal ->
                        val discount = proposal.discountSets.roundToInt().coerceAtLeast(1)
                        Text(
                            text = "• ${proposal.exerciseName}: −$discount ${if (discount == 1) "serie" else "series"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Aplicar descuento", fontWeight = FontWeight.Black)
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Omitir por ahora", color = Color.White.copy(alpha = 0.85f))
        }
    }
}
