package com.example.kpkn.screens.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.domain.training.TrainingMaxResolver
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton

@Composable
fun TrainingMaxWizard(
    initial: PowerliftingProfile? = null,
    trainingMaxPercent: Double = 0.90,
    confirmLabel: String = "Aplicar y crear",
    onDismiss: () -> Unit,
    onConfirm: (PowerliftingProfile) -> Unit,
) {
    var squat by remember { mutableStateOf(initial?.squat1RM?.toString().orEmpty()) }
    var bench by remember { mutableStateOf(initial?.bench1RM?.toString().orEmpty()) }
    var deadlift by remember { mutableStateOf(initial?.deadlift1RM?.toString().orEmpty()) }
    var overhead by remember { mutableStateOf(initial?.overhead1RM?.toString().orEmpty()) }

    fun parsed(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Training Max", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(
                "Introduce 1RM de competición. El TM se calcula al ${(trainingMaxPercent * 100).toInt()} %.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            OutlinedTextField(value = squat, onValueChange = { squat = it }, label = { Text("Sentadilla 1RM (kg)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bench, onValueChange = { bench = it }, label = { Text("Banca 1RM (kg)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = deadlift, onValueChange = { deadlift = it }, label = { Text("Peso muerto 1RM (kg)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = overhead, onValueChange = { overhead = it }, label = { Text("Press militar 1RM (kg, opcional)") }, modifier = Modifier.fillMaxWidth())
            val preview = TrainingMaxResolver.hydrateProfile(
                PowerliftingProfile(
                    squat1RM = parsed(squat),
                    bench1RM = parsed(bench),
                    deadlift1RM = parsed(deadlift),
                    overhead1RM = parsed(overhead),
                ),
                trainingMaxPercent,
            )
            Text(
                "TM: SQ ${preview.squatTM?.toInt() ?: "—"} · BP ${preview.benchTM?.toInt() ?: "—"} · DL ${preview.deadliftTM?.toInt() ?: "—"}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            KpknSheetWhiteButton(text = confirmLabel, onClick = { onConfirm(preview) })
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Más tarde", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
