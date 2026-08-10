package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.cardio.CardioGuideEngine
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus

@Composable
internal fun CardioLiveCard(
    details: CardioDetails,
    completedSet: CompletedSet?,
    accentColor: Color,
    executionState: CardioTimerState? = null,
    liveHeartRateBpm: Int? = null,
    onStartTimer: () -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onRequestRecord: (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) -> Unit = { _, _, _ -> },
    onCancelRecord: () -> Unit = {},
    onRecord: (durationSeconds: Int, distanceKm: Double?, averageHeartRate: Int?) -> Unit,
    gpsState: CardioGpsState? = null,
    onRequestGps: () -> Unit = {},
    onPauseGps: () -> Unit = {},
    onResumeGps: () -> Unit = {},
) {
    var distanceText by remember(completedSet?.distanceKm) {
        mutableStateOf(completedSet?.distanceKm?.toString().orEmpty())
    }
    var heartRateText by remember(completedSet?.avgHeartRate) {
        mutableStateOf(completedSet?.avgHeartRate?.toString().orEmpty())
    }
    var showRecordConfirmation by remember { mutableStateOf(false) }

    val status = executionState?.status ?: CardioExecutionStatus.READY
    val plannedDurationSeconds = details.targetDurationSeconds.coerceAtLeast(1)
    val timerElapsedSeconds = executionState?.elapsedSeconds ?: 0
    val timerRemainingSeconds = executionState?.remainingSeconds ?: plannedDurationSeconds
    val gpsMode = details.requiresGps
    val gpsHasData = gpsMode && gpsState?.let { it.pointCount > 0 || it.distanceMeters >= 10.0 } == true
    val gpsDurationSeconds = gpsState?.elapsedActiveSeconds?.takeIf { it > 0L }?.toInt()
    val durationSeconds = if (timerElapsedSeconds > 0) {
        timerElapsedSeconds
    } else if (gpsMode && gpsDurationSeconds != null) {
        gpsDurationSeconds
    } else {
        0
    }
    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val recordedDistanceKm = if (gpsHasData) gpsState?.distanceMeters?.div(1_000.0) else distanceKm
    val heartRate = (liveHeartRateBpm ?: heartRateText.toIntOrNull())?.coerceIn(30, 240)
    val showsCadence = details.type in setOf(
        com.example.kpkn.data.models.CardioType.TREADMILL,
        com.example.kpkn.data.models.CardioType.ELLIPTICAL,
        com.example.kpkn.data.models.CardioType.ROW_MACHINE,
        com.example.kpkn.data.models.CardioType.BIKE_STATIONARY,
    )
    val guide = remember(details.type, details.intensity) { CardioGuideEngine.guide(details) }

    LaunchedEffect(status) {
        if (status == CardioExecutionStatus.AWAITING_CONFIRMATION) showRecordConfirmation = true
        if (status == CardioExecutionStatus.RECORDED) showRecordConfirmation = false
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
                "Objetivo ${details.targetDurationSeconds / 60} min" +
                    (details.targetDistanceKm?.let { " · $it km" } ?: "") +
                    " · ${details.intensity.name.replace('_', ' ')}",
                color = Color.White.copy(alpha = 0.68f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CardioMetricCircle(
                    label = "Tiempo",
                    value = formatCardioTime(if (status == CardioExecutionStatus.READY) plannedDurationSeconds else timerElapsedSeconds),
                    progress = if (plannedDurationSeconds > 0) {
                        (timerElapsedSeconds.toFloat() / plannedDurationSeconds).coerceIn(0f, 1f)
                    } else 0f,
                    accentColor = accentColor,
                )
            }

            Text(
                when (status) {
                    CardioExecutionStatus.RUNNING -> "En curso · ${formatCardioTime(timerElapsedSeconds)} realizados · ${formatCardioTime(timerRemainingSeconds)} restantes"
                    CardioExecutionStatus.PAUSED -> "Pausado · ${formatCardioTime(timerElapsedSeconds)} realizados"
                    CardioExecutionStatus.AWAITING_CONFIRMATION -> "Listo para confirmar el registro"
                    CardioExecutionStatus.RECORDED -> "Cardio registrado · ${formatCardioTime(timerElapsedSeconds)}"
                    CardioExecutionStatus.READY -> "Listo · objetivo ${formatCardioTime(plannedDurationSeconds)}"
                },
                color = if (status == CardioExecutionStatus.RUNNING) accentColor else Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold,
            )

            if (gpsMode) {
                Text(
                    when (gpsState?.status) {
                        CardioGpsStatus.RECORDING -> "Señal GPS activa · ${formatCardioDistance(recordedDistanceKm)} · ${formatCardioTime(durationSeconds)}"
                        CardioGpsStatus.SIGNAL_LOST -> "Señal GPS perdida · puedes continuar o registrar manualmente"
                        CardioGpsStatus.PAUSED -> "GPS pausado · ${formatCardioDistance(recordedDistanceKm)}"
                        CardioGpsStatus.REQUESTING_PERMISSION -> "Solicitando permiso y señal de ubicación..."
                        CardioGpsStatus.PERMISSION_DENIED -> "Permiso denegado · registro manual disponible"
                        CardioGpsStatus.LOCATION_DISABLED -> "Ubicación desactivada · registro manual disponible"
                        else -> "GPS listo · el registro manual sigue disponible"
                    },
                    color = if (gpsState?.status == CardioGpsStatus.RECORDING) accentColor else Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Calentamiento" to "50-60%",
                    "Quema grasa" to "60-70%",
                    "Aeróbico" to "70-80%",
                    "Anaeróbico" to "80-90%",
                ).forEach { (zone, percent) ->
                    val isActive = guide.zoneName == zone
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            zone,
                            color = if (isActive) accentColor else Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            percent,
                            color = if (isActive) accentColor else Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (showsCadence) {
                            Text(
                                when (zone) {
                                    "Calentamiento" -> "50-60 RPM"
                                    "Quema grasa" -> "60-70 RPM"
                                    "Aeróbico" -> "70-85 RPM"
                                    else -> "85+ RPM"
                                },
                                color = if (isActive) accentColor else Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (gpsMode) {
                        when (gpsState?.status) {
                            CardioGpsStatus.RECORDING, CardioGpsStatus.SIGNAL_LOST -> onPauseGps()
                            CardioGpsStatus.PAUSED -> onResumeGps()
                            CardioGpsStatus.REQUESTING_PERMISSION -> Unit
                            else -> onRequestGps()
                        }
                    } else if (status == CardioExecutionStatus.RUNNING) {
                        onPauseTimer()
                    } else if (status != CardioExecutionStatus.AWAITING_CONFIRMATION && status != CardioExecutionStatus.RECORDED) {
                        onStartTimer()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = gpsState?.status != CardioGpsStatus.REQUESTING_PERMISSION &&
                    status !in setOf(CardioExecutionStatus.AWAITING_CONFIRMATION, CardioExecutionStatus.RECORDED),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.76f),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    when {
                        gpsMode && gpsState?.status == CardioGpsStatus.RECORDING -> "Pausar GPS"
                        gpsMode && gpsState?.status == CardioGpsStatus.SIGNAL_LOST -> "Pausar y conservar datos"
                        gpsMode && gpsState?.status == CardioGpsStatus.PAUSED -> "Reanudar GPS"
                        gpsMode -> "Iniciar GPS"
                        status == CardioExecutionStatus.RUNNING -> "Pausar"
                        status == CardioExecutionStatus.PAUSED -> "Reanudar"
                        else -> "Iniciar"
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (details.supportsDistance) {
                    CardioLiveAccentField(
                        value = if (gpsHasData) formatCardioDistance(recordedDistanceKm) else distanceText,
                        onValueChange = { distanceText = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(8) },
                        modifier = Modifier.weight(1f),
                        label = "Km (opcional)",
                        accentColor = accentColor,
                        readOnly = gpsHasData,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                CardioLiveAccentField(
                    value = liveHeartRateBpm?.toString() ?: heartRateText,
                    onValueChange = { heartRateText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    label = "FC media (opcional)",
                    accentColor = accentColor,
                    readOnly = liveHeartRateBpm != null,
                    trailingIcon = { androidx.compose.material3.Icon(Icons.Default.Favorite, null, tint = accentColor) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (gpsMode) {
                Text(
                    "Distancia ${formatCardioDistance(recordedDistanceKm)} · velocidad/ritmo ${formatCardioPace(gpsState?.paceSecondsPerKm)}",
                    color = accentColor.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = {
                    onRequestRecord(durationSeconds, recordedDistanceKm, heartRate)
                    showRecordConfirmation = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = status !in setOf(CardioExecutionStatus.READY, CardioExecutionStatus.RECORDED),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.92f),
                    contentColor = Color.White,
                ),
            ) { Text(if (completedSet == null) "Finalizar y registrar" else "Actualizar cardio") }
            if (gpsMode) {
                Text(
                    "Si se deniega el permiso o se pierde la señal, puedes registrar duración, distancia y FC manualmente.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.58f),
                )
            }
        }
    }

    if (showRecordConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showRecordConfirmation = false
                onCancelRecord()
            },
            title = { Text("Confirmar cardio") },
            text = {
                Text(
                    "Registrar ${formatCardioTime(durationSeconds)}" +
                        (recordedDistanceKm?.let { " · ${formatCardioDistance(it)}" } ?: "") +
                        " como resultado de esta sesión?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRecordConfirmation = false
                    onRecord(durationSeconds, recordedDistanceKm, heartRate)
                }) { Text("Registrar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecordConfirmation = false
                    onCancelRecord()
                }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun CardioMetricCircle(
    label: String,
    value: String,
    progress: Float,
    accentColor: Color,
) {
    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.matchParentSize(),
            color = accentColor,
            trackColor = accentColor.copy(alpha = 0.16f),
            strokeWidth = 8.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
            Text(value, fontWeight = FontWeight.Black, color = Color.White)
        }
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = if (focused) 0.14f else 0.08f))
            .border(1.dp, accentColor.copy(alpha = if (focused) 0.95f else 0.56f), shape)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(accentColor),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) accentColor else accentColor.copy(alpha = 0.82f),
                        fontWeight = FontWeight.Bold,
                    )
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp)) { innerTextField() }
                }
                trailingIcon?.invoke()
            }
        },
    )
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
