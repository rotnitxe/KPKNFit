package com.example.kpkn.screens.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.domain.training.TrainingMaxResolver
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSheetWhiteButton
import com.example.kpkn.ui.components.kpknSheetWhiteFieldColors

@Composable
fun TrainingMaxWizard(
    initial: PowerliftingProfile? = null,
    autoEstimate: PowerliftingProfile? = null,
    trainingMaxPercent: Double = 0.90,
    confirmLabel: String = "Aplicar y crear",
    onDismiss: () -> Unit,
    onConfirm: (PowerliftingProfile) -> Unit,
) {
    var squat by remember(initial) { mutableStateOf(initial?.squat1RM?.toString().orEmpty()) }
    var bench by remember(initial) { mutableStateOf(initial?.bench1RM?.toString().orEmpty()) }
    var deadlift by remember(initial) { mutableStateOf(initial?.deadlift1RM?.toString().orEmpty()) }
    var overhead by remember(initial) { mutableStateOf(initial?.overhead1RM?.toString().orEmpty()) }
    var attempted by remember { mutableStateOf(false) }

    fun parsed(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Máximo de entrenamiento (TM)", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(
                trainingMaxIntro(trainingMaxPercent),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            trainingMaxFiveRepNote(trainingMaxPercent)?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            if (autoEstimate != null && squat.isBlank() && bench.isBlank() && deadlift.isBlank()) {
                TextButton(onClick = {
                    autoEstimate.squat1RM?.let { squat = trimTrailingZero(it) }
                    autoEstimate.bench1RM?.let { bench = trimTrailingZero(it) }
                    autoEstimate.deadlift1RM?.let { deadlift = trimTrailingZero(it) }
                    autoEstimate.overhead1RM?.let { overhead = trimTrailingZero(it) }
                }) {
                    Text("Calcular desde mi historial")
                }
            }
            TmField(value = squat, onValueChange = { squat = it }, label = "Sentadilla 1RM (kg)")
            TmField(value = bench, onValueChange = { bench = it }, label = "Press de banca 1RM (kg)")
            TmField(value = deadlift, onValueChange = { deadlift = it }, label = "Peso muerto 1RM (kg)")
            TmField(value = overhead, onValueChange = { overhead = it }, label = "Press militar 1RM (kg, opcional)")
            if (attempted && parsed(squat) == null && parsed(bench) == null && parsed(deadlift) == null) {
                Text(
                    "Introduce al menos un 1RM válido para guardar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFBBF24),
                )
            }
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
                trainingMaxPreviewLine(preview.squatTM, preview.benchTM, preview.deadliftTM),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            val hasValidInput = parsed(squat) != null || parsed(bench) != null || parsed(deadlift) != null
            KpknSheetWhiteButton(
                text = confirmLabel,
                enabled = hasValidInput,
                onClick = {
                    attempted = true
                    if (!hasValidInput) return@KpknSheetWhiteButton
                    onConfirm(preview)
                },
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Más tarde", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TmField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = kpknSheetWhiteFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

private fun trimTrailingZero(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}

/** Porcentaje del 1RM que usan Texas Method y Madcow como TM (D7): un peso parecido a un 5RM. */
private const val TEXAS_MADCOW_TM_PERCENT = 87

/** Explicación bajo el título; el porcentaje es el de la receta del plan que se va a crear. */
internal fun trainingMaxIntro(trainingMaxPercent: Double): String =
    "Introduce 1RM de competición. El TM se calcula al ${(trainingMaxPercent * 100).toInt()} % de tu 1RM."

/**
 * Nota de D7. Solo aparece cuando el TM del plan es el 87 % del 1RM (Texas Method y Madcow): en los
 * demás planes el porcentaje es otro y la nota no aplica.
 */
internal fun trainingMaxFiveRepNote(trainingMaxPercent: Double): String? =
    if ((trainingMaxPercent * 100).toInt() == TEXAS_MADCOW_TM_PERCENT) {
        "Texas Method y Madcow usan el $TEXAS_MADCOW_TM_PERCENT % de tu 1RM como TM (parecido a un 5RM)."
    } else {
        null
    }

/** `TM: sentadilla 85 · press de banca 60 · peso muerto 100`, con «—» donde aún no hay marca. */
internal fun trainingMaxPreviewLine(squatTM: Double?, benchTM: Double?, deadliftTM: Double?): String {
    fun tmText(tm: Double?): String = tm?.toInt()?.toString() ?: "—"
    return "TM: sentadilla ${tmText(squatTM)} · press de banca ${tmText(benchTM)} · peso muerto ${tmText(deadliftTM)}"
}
