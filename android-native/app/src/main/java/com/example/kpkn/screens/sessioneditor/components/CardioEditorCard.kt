package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioProgramMode
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.domain.cardio.CardioHiitProgramBuilder
import com.example.kpkn.domain.cardio.CardioIntervalProgramBuilder
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import kotlin.math.roundToInt

private enum class CardioTargetMode(val label: String) {
    DURATION("Tiempo"),
    DISTANCE("Distancia"),
    BOTH("Ambos"),
}

@Composable
internal fun CardioEditorCard(
    details: CardioDetails,
    accentColor: Color,
    exerciseName: String? = null,
    onChange: (CardioDetails) -> Unit,
) {
    val currentTargetMode = remember(details.targetDurationSeconds, details.targetDistanceKm, details.supportsDistance) {
        if (!details.supportsDistance) {
            CardioTargetMode.DURATION
        } else when {
            details.targetDurationSeconds != null && details.targetDistanceKm != null -> CardioTargetMode.BOTH
            details.targetDistanceKm != null && details.targetDurationSeconds == null -> CardioTargetMode.DISTANCE
            else -> CardioTargetMode.DURATION
        }
    }

    var distanceText by remember(details.targetDistanceKm) {
        mutableStateOf(details.targetDistanceKm?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty())
    }

    val currentIntensityLevel = details.resolvedIntensityLevel()
    val programMode = details.programMode()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Nombre del ejercicio como título principal
            val title = exerciseName?.takeIf { it.isNotBlank() } ?: cardioTypeLabel(details.type)
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                "Modo de programación",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    CardioProgramMode.STEADY to "Estático",
                    CardioProgramMode.HIIT_SIT to "HIIT / SIT",
                    CardioProgramMode.INTERVALS to "Intervalos",
                ).forEach { (mode, label) ->
                    CardioModeChip(
                        selected = programMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (programMode != mode) when (mode) {
                                CardioProgramMode.STEADY -> onChange(
                                    details.copy(
                                        intervalBlocks = emptyList(),
                                        intervalRounds = 1,
                                        hiit = null,
                                        targetDurationSeconds = details.targetDurationSeconds ?: 20 * 60,
                                    ),
                                )
                                CardioProgramMode.HIIT_SIT -> {
                                    val config = details.hiit ?: CardioHiitConfig(
                                        targetRpe = 9.0,
                                        protocol = com.example.kpkn.data.models.HiitProtocol.HIIT,
                                    )
                                    onChange(CardioHiitProgramBuilder.buildDetails(config, details.type, details))
                                }
                                CardioProgramMode.INTERVALS -> {
                                    val total = details.targetDurationSeconds
                                        ?: details.effectiveDurationSeconds().takeIf { it > 0 }
                                        ?: 20 * 60
                                    onChange(
                                        CardioIntervalProgramBuilder.buildDetails(
                                            pattern = CardioIntervalPattern.PYRAMID,
                                            totalSeconds = total,
                                            type = details.type,
                                            baseLevel = details.resolvedIntensityLevel(),
                                            base = details.copy(hiit = null),
                                        ),
                                    )
                                }
                            }
                        },
                        label = label,
                        accentColor = accentColor,
                    )
                }
            }
            Text(
                text = when (programMode) {
                    CardioProgramMode.STEADY -> "Una intensidad continua. Define tiempo, distancia o ambos y listo."
                    CardioProgramMode.HIIT_SIT -> "Alterna esfuerzo y recuperación. HIIT parte en RPE 9; SIT parte en RPE 10."
                    CardioProgramMode.INTERVALS -> "Construye una secuencia por bloques; la altura muestra intensidad y el ancho, duración."
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )

            // Selector de tipo de objetivo (Tiempo / Distancia / Ambos) — oculto cuando hay intervalos (duración deriva del circuito)
            if (programMode == CardioProgramMode.STEADY) {
                if (details.supportsDistance) {
                    Text(
                        "Objetivo a programar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CardioTargetMode.entries.forEach { mode ->
                            CardioModeChip(
                                selected = currentTargetMode == mode,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (mode) {
                                        CardioTargetMode.DURATION -> {
                                            val duration = details.targetDurationSeconds ?: (20 * 60)
                                            onChange(details.copy(targetDurationSeconds = duration, targetDistanceKm = null))
                                        }
                                        CardioTargetMode.DISTANCE -> {
                                            val distance = details.targetDistanceKm ?: 3.0
                                            distanceText = if (distance % 1.0 == 0.0) distance.toInt().toString() else distance.toString()
                                            onChange(details.copy(targetDurationSeconds = null, targetDistanceKm = distance))
                                        }
                                        CardioTargetMode.BOTH -> {
                                            val duration = details.targetDurationSeconds ?: (20 * 60)
                                            val distance = details.targetDistanceKm ?: 3.0
                                            distanceText = if (distance % 1.0 == 0.0) distance.toInt().toString() else distance.toString()
                                            onChange(details.copy(targetDurationSeconds = duration, targetDistanceKm = distance))
                                        }
                                    }
                                },
                                label = mode.label,
                                accentColor = accentColor,
                            )
                        }
                    }
                }

                // Inputs de duración y/o distancia según el modo seleccionado
                when (currentTargetMode) {
                    CardioTargetMode.DURATION -> {
                        CardioDurationField(
                            durationMinutes = ((details.targetDurationSeconds ?: (20 * 60)) / 60).coerceAtLeast(1),
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxWidth(),
                            onConfirm = { minutes ->
                                onChange(details.copy(targetDurationSeconds = minutes * 60))
                            },
                        )
                    }
                    CardioTargetMode.DISTANCE -> {
                        CardioAccentField(
                            value = distanceText,
                            onValueChange = { value ->
                                distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                                val parsed = distanceText.replace(',', '.').toDoubleOrNull()
                                onChange(details.copy(targetDistanceKm = parsed))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Distancia meta (km)",
                            accentColor = accentColor,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    CardioTargetMode.BOTH -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CardioDurationField(
                                durationMinutes = ((details.targetDurationSeconds ?: (20 * 60)) / 60).coerceAtLeast(1),
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f),
                                onConfirm = { minutes ->
                                    onChange(details.copy(targetDurationSeconds = minutes * 60))
                                },
                            )
                            CardioAccentField(
                                value = distanceText,
                                onValueChange = { value ->
                                    distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                                    val parsed = distanceText.replace(',', '.').toDoubleOrNull()
                                    onChange(details.copy(targetDistanceKm = parsed))
                                },
                                modifier = Modifier.weight(1f),
                                label = "Distancia (km)",
                                accentColor = accentColor,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            } else {
                // Cuando hay intervalos, la duración total se deriva del circuito; la distancia sigue editable si aplica
                if (details.supportsDistance) {
                    CardioAccentField(
                        value = distanceText,
                        onValueChange = { value ->
                            distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                            val parsed = distanceText.replace(',', '.').toDoubleOrNull()
                            onChange(details.copy(targetDistanceKm = parsed))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Distancia meta (km) (opcional con intervalos)",
                        accentColor = accentColor,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text(
                    "Duración total: ${formatIntervalTotal(details.totalIntervalSeconds())} (deriva de los bloques)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }

            // Slider de RPE programado de 1 a 10. En HIIT/SIT se oculta: ese
            // modo tiene su propio slider exacto dentro del panel HIIT/SIT.
            if (programMode != CardioProgramMode.HIIT_SIT) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("RPE programado", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.88f))
                        Text(
                            "${currentIntensityLevel}/10 · ${intensityRpeAnchor(currentIntensityLevel)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                    }
                    Slider(
                        value = currentIntensityLevel.toFloat(),
                        onValueChange = { newValue ->
                            val rounded = newValue.roundToInt().coerceIn(1, 10)
                            if (rounded != currentIntensityLevel) {
                                val newIntensity = CardioIntensity.fromLevel(rounded)
                                onChange(details.copy(intensityLevel = rounded, intensity = newIntensity))
                            }
                        },
                        valueRange = 1f..10f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = accentColor.copy(alpha = 0.20f),
                            activeTickColor = Color.White.copy(alpha = 0.6f),
                            inactiveTickColor = accentColor.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when (programMode) {
                CardioProgramMode.STEADY -> Unit
                CardioProgramMode.HIIT_SIT -> CardioHiitEditor(
                    details = details,
                    accentColor = accentColor,
                    onChange = onChange,
                )
                CardioProgramMode.INTERVALS -> CardioIntervalsEditor(
                    details = details,
                    accentColor = accentColor,
                    onChange = onChange,
                )
            }

            // Apartado de GPS en vivo simplificado y compacto (sin texto redundante que gaste espacio)
            if (details.type.isOutdoor()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { _ -> }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (details.requiresGps) accentColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("GPS en vivo", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Registra ruta, ritmo y distancia con GPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.60f),
                        )
                    }
                    Switch(
                        checked = details.requiresGps,
                        onCheckedChange = { enabled ->
                            onChange(details.copy(requiresGps = enabled))
                            if (enabled) {
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor.copy(alpha = 0.85f),
                            checkedBorderColor = accentColor,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.25f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardioModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CardioDurationField(
    durationMinutes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        CardioAccentField(
            value = "${durationMinutes.coerceAtLeast(1)} min",
            onValueChange = {},
            label = "Duración objetivo",
            accentColor = accentColor,
            readOnly = true,
            trailingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = accentColor) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }
    if (showPicker) {
        KpknNativeTimePickerDialog(
            title = "Duración objetivo",
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
private fun CardioAccentField(
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
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp)) {
                        innerTextField()
                    }
                }
                trailingIcon?.invoke()
            }
        },
    )
}

private fun CardioType.isOutdoor(): Boolean = when (this) {
    CardioType.RUN_OUTDOOR,
    CardioType.BIKE_OUTDOOR,
    CardioType.WALK,
    -> true
    else -> false
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
    CardioType.AIR_BIKE -> "Air Bike"
    CardioType.SKI_ERG -> "SkiErg"
    CardioType.CURVED_TREADMILL -> "Cinta curva"
    CardioType.SLED -> "Trineo"
}

private fun intensityRpeAnchor(level: Int): String = when (level.coerceIn(1, 10)) {
    in 1..2 -> "Muy suave"
    in 3..4 -> "Suave"
    in 5..6 -> "Algo duro"
    in 7..8 -> "Duro"
    9 -> "Muy duro"
    else -> "Máximo"
}

private fun formatIntervalTotal(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (s == 0) "${m} min" else "${m}m ${s}s"
}
