package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import kotlinx.coroutines.delay

@Composable
internal fun CardioLiveCard(
    details: CardioDetails,
    completedSet: CompletedSet?,
    bodyWeightKg: Double?,
    accentColor: Color,
    progressionSuggestion: CardioProgressionSuggestion? = null,
    onRecord: (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) -> Unit,
    gpsState: CardioGpsState? = null,
    onRequestGps: () -> Unit = {},
    onPauseGps: () -> Unit = {},
    onResumeGps: () -> Unit = {},
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
    val gpsMode = details.requiresGps
    val gpsHasData = gpsMode && gpsState?.let { it.pointCount > 0 || it.distanceMeters >= 10.0 } == true
    val gpsDurationSeconds = gpsState?.elapsedActiveSeconds?.takeIf { it > 0L }?.toInt()
    val manualDurationSeconds = (durationText.toIntOrNull()?.coerceAtLeast(1) ?: (details.targetDurationSeconds / 60)) * 60
        .let { if (timerElapsedSeconds > 0) timerElapsedSeconds else it }
    val durationSeconds = if (gpsMode && gpsDurationSeconds != null) gpsDurationSeconds else manualDurationSeconds
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val recordedDistanceKm = if (gpsHasData) gpsState?.distanceMeters?.div(1_000.0) else distanceKm
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
    val gpsButtonLabel = when {
        !gpsMode -> if (timerRunning) "Pausar" else "Iniciar cardio"
        gpsState?.status == CardioGpsStatus.RECORDING || gpsState?.status == CardioGpsStatus.SIGNAL_LOST -> "Pausar GPS"
        gpsState?.status == CardioGpsStatus.PAUSED -> "Reanudar GPS"
        gpsState?.status == CardioGpsStatus.REQUESTING_PERMISSION -> "Buscando señal..."
        gpsState?.status == CardioGpsStatus.PERMISSION_DENIED -> "Reintentar GPS"
        gpsState?.status == CardioGpsStatus.LOCATION_DISABLED -> "Activar ubicación"
        else -> "Iniciar GPS"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.09f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
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
            if (gpsMode) {
                Text(
                    when (gpsState?.status) {
                        CardioGpsStatus.RECORDING -> "GPS grabando · ${formatCardioTime(durationSeconds)} · ${formatCardioDistance(recordedDistanceKm)}"
                        CardioGpsStatus.SIGNAL_LOST -> "GPS sin señal · ${formatCardioDistance(recordedDistanceKm)} · puedes continuar o registrar manualmente"
                        CardioGpsStatus.PAUSED -> "GPS pausado · ${formatCardioTime(durationSeconds)} · ${formatCardioDistance(recordedDistanceKm)}"
                        CardioGpsStatus.REQUESTING_PERMISSION -> "Solicitando señal de ubicación..."
                        CardioGpsStatus.PERMISSION_DENIED -> "Permiso de ubicación denegado · registro manual disponible"
                        CardioGpsStatus.LOCATION_DISABLED -> "Ubicación desactivada · registro manual disponible"
                        else -> "GPS listo · el registro manual sigue disponible"
                    },
                    color = if (gpsState?.status == CardioGpsStatus.RECORDING) accentColor else Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    if (timerRunning) "En curso · ${formatCardioTime(timerRemainingSeconds)} restantes"
                    else if (timerElapsedSeconds > 0) "Pausado · ${formatCardioTime(timerElapsedSeconds)} realizados"
                    else "Listo para cronometrar ${formatCardioTime(plannedDurationSeconds)}",
                    color = if (timerRunning) accentColor else Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = {
                    if (gpsMode) {
                        when (gpsState?.status) {
                            CardioGpsStatus.RECORDING,
                            CardioGpsStatus.SIGNAL_LOST,
                            -> onPauseGps()
                            CardioGpsStatus.PAUSED -> onResumeGps()
                            CardioGpsStatus.REQUESTING_PERMISSION -> Unit
                            else -> onRequestGps()
                        }
                    } else {
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
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = gpsState?.status != CardioGpsStatus.REQUESTING_PERMISSION,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.76f),
                    contentColor = Color.White,
                ),
            ) { Text(gpsButtonLabel) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CardioLiveDurationField(
                    durationMinutes = durationText.toIntOrNull() ?: 1,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onConfirm = { minutes -> durationText = minutes.toString() },
                )
                if (details.supportsDistance) {
                    CardioLiveAccentField(
                        value = if (gpsHasData) formatCardioDistance(recordedDistanceKm) else distanceText,
                        onValueChange = { distanceText = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(8) },
                        modifier = Modifier.weight(1f),
                        label = "Km",
                        accentColor = accentColor,
                        readOnly = gpsHasData,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                CardioLiveAccentField(
                    value = heartRateText,
                    onValueChange = { heartRateText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    label = "FC media",
                    accentColor = accentColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (gpsMode) {
                Text(
                    "Ritmo GPS: ${formatCardioPace(gpsState?.paceSecondsPerKm)}" +
                        if (gpsHasData) " · ${gpsState?.pointCount ?: 0} puntos locales" else "",
                    color = accentColor.copy(alpha = 0.88f),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Calorías estimadas: ${estimatedCalories?.let { "%.0f kcal".format(it) } ?: "añade peso corporal"}",
                color = Color.White.copy(alpha = 0.7f),
            )
            Button(
                onClick = {
                    timerRunning = false
                    onRecord(durationSeconds, recordedDistanceKm, heartRate)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.92f),
                    contentColor = Color.White,
                ),
            ) { Text(if (completedSet == null) "Registrar cardio" else "Actualizar cardio") }
            if (gpsMode) {
                Text(
                    "Si no hay señal, puedes registrar duración, distancia y FC manualmente.",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.58f),
                )
            }
        }
    }
}

@Composable
private fun CardioLiveDurationField(
    durationMinutes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        CardioLiveAccentField(
            value = "${durationMinutes.coerceAtLeast(1)} min",
            onValueChange = {},
            label = "Minutos",
            accentColor = accentColor,
            readOnly = true,
            trailingIcon = { androidx.compose.material3.Icon(Icons.Default.Timer, null, tint = accentColor) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) {
        KpknNativeTimePickerDialog(
            title = "Duración de cardio",
            initialHour = (durationMinutes / 60).coerceIn(0, 23),
            initialMinute = (durationMinutes % 60).coerceIn(0, 59),
            hint = "Horas : minutos",
            onConfirm = { hour, minute ->
                onConfirm((hour * 60 + minute).coerceAtLeast(1))
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun CardioLiveAccentField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    trailingIcon: (@Composable (() -> Unit))? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = accentColor.copy(alpha = 0.14f),
            unfocusedContainerColor = accentColor.copy(alpha = 0.08f),
            focusedBorderColor = accentColor,
            unfocusedBorderColor = accentColor.copy(alpha = 0.56f),
            focusedLabelColor = accentColor,
            unfocusedLabelColor = accentColor.copy(alpha = 0.82f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = accentColor,
        ),
    )
}

private fun com.example.kpkn.data.models.CardioType.isOutdoor(): Boolean = when (this) {
    com.example.kpkn.data.models.CardioType.RUN_OUTDOOR,
    com.example.kpkn.data.models.CardioType.BIKE_OUTDOOR,
    com.example.kpkn.data.models.CardioType.WALK,
    -> true
    else -> false
}

private fun formatCardioTime(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val seconds = totalSeconds.coerceAtLeast(0) % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatCardioDistance(distanceKm: Double?): String =
    distanceKm?.takeIf { it >= 0.0 }?.let { "%.2f km".format(it) } ?: "0.00 km"

private fun formatCardioPace(paceSecondsPerKm: Int?): String = paceSecondsPerKm?.let { seconds ->
    "%02d:%02d/km".format(seconds / 60, seconds % 60)
} ?: "--/km"
