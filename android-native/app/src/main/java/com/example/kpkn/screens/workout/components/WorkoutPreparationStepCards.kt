package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.WarmupCalibrationEngine

/** A single live mobility block. It never renders strength-set controls. */
@Composable
internal fun MobilityExecutionCard(
    mobility: MobilitySeries,
    seriesIndex: Int,
    totalSeries: Int,
    accentColor: Color,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreparationCardSurface(accentColor = accentColor, modifier = modifier) {
        Text(
            text = "MOVILIDAD  ·  SERIE ${seriesIndex + 1}/$totalSeries",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accentColor,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = mobility.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreparationMetric(
                label = if (mobility.durationSeconds != null && mobility.durationSeconds > 0) "TIEMPO" else "REPS",
                value = mobility.durationSeconds?.let(::formatDuration) ?: mobility.reps.orEmpty().ifBlank { "—" },
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
            )
            PreparationMetric(
                label = "BLOQUE",
                value = "${mobility.sets.coerceAtLeast(1)} serie${if (mobility.sets == 1) "" else "s"}",
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
            )
        }
        if (mobility.restBetweenSeconds > 0) {
            Text(
                text = "Descanso entre series: ${mobility.restBetweenSeconds}s",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
        if (!mobility.notes.isNullOrBlank()) {
            Text(
                text = mobility.notes.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.60f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor.copy(alpha = 0.88f),
                contentColor = preparationContentColor(accentColor),
            ),
        ) {
            Text("Completar movilidad", fontWeight = FontWeight.Black)
        }
    }
}

/** A single live warm-up card with a real used-load capture. */
@Composable
internal fun WarmupExecutionCard(
    warmup: WarmupSetDefinition,
    seriesIndex: Int,
    totalSeries: Int,
    suggestedWeightKg: Double?,
    hasReference: Boolean,
    savedWeightKg: Double?,
    accentColor: Color,
    onComplete: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val percentage = WarmupCalibrationEngine.normalizePercentage(warmup.percentageOfWorkingWeight)
    var usedWeightText by remember(warmup.id, savedWeightKg, suggestedWeightKg) {
        mutableStateOf(savedWeightKg?.takeIf { it > 0.0 }?.formatKg() ?: suggestedWeightKg?.formatKg().orEmpty())
    }
    PreparationCardSurface(accentColor = accentColor, modifier = modifier) {
        Text(
            text = "APROXIMACIÓN  ·  SERIE ${seriesIndex + 1}/$totalSeries",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accentColor,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreparationMetric(
                label = "% PROGRAMADO",
                value = "${(percentage * 100.0).formatWhole()}%",
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
            )
            PreparationMetric(
                label = "REPS",
                value = warmup.targetReps.toString(),
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = suggestedWeightKg?.let { "Sugerido: ${it.formatKg()} kg" }
                ?: "Sin referencia de carga: puedes registrar el peso usado",
            style = MaterialTheme.typography.labelSmall,
            color = if (suggestedWeightKg != null) Color.White.copy(alpha = 0.68f) else accentColor.copy(alpha = 0.90f),
        )
        OutlinedTextField(
            value = usedWeightText,
            onValueChange = { usedWeightText = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Carga usada (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = preparationFieldColors(accentColor),
        )
        if (!hasReference && suggestedWeightKg == null) {
            Text(
                text = "La app no inventa una carga: guarda este dato para calibrar las próximas aproximaciones.",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.82f),
            )
        }
        if (warmup.restBetween != null && warmup.restBetween > 0) {
            Text(
                text = "Descanso después: ${warmup.restBetween}s",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = { onComplete(usedWeightText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor.copy(alpha = 0.88f),
                contentColor = preparationContentColor(accentColor),
            ),
        ) {
            Text("Completar aproximación", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PreparationCardSurface(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content,
        )
    }
}

@Composable
private fun PreparationMetric(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.78f), fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun preparationFieldColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = accentColor.copy(alpha = 0.14f),
    unfocusedContainerColor = accentColor.copy(alpha = 0.08f),
    disabledContainerColor = accentColor.copy(alpha = 0.06f),
    focusedBorderColor = accentColor,
    unfocusedBorderColor = accentColor.copy(alpha = 0.42f),
    cursorColor = accentColor,
    focusedLabelColor = accentColor,
    unfocusedLabelColor = accentColor.copy(alpha = 0.72f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
)

private fun preparationContentColor(accentColor: Color): Color {
    val luminance = 0.2126f * accentColor.red + 0.7152f * accentColor.green + 0.0722f * accentColor.blue
    return if (luminance > 0.48f) Color.Black else Color.White
}

private fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 60) "${safe / 60}m ${safe % 60}s" else "${safe}s"
}

private fun Double.formatKg(): String = String.format(java.util.Locale.US, "%.1f", this).trimEnd('0').trimEnd('.')

private fun Double.formatWhole(): String {
    val rounded = kotlin.math.round(this).toInt()
    return rounded.toString()
}
