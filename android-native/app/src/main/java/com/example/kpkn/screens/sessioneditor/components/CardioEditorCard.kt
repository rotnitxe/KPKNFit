package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.calculations.CardioCalorieEngine
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog

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
                "${cardioTypeLabel(details.type)} · MET estimado ${"%.1f".format(defaultMet)}",
                color = Color.White.copy(alpha = 0.68f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CardioDurationField(
                    durationMinutes = durationText.toIntOrNull() ?: 1,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onConfirm = { minutes ->
                        durationText = minutes.toString()
                        onChange(details.copy(targetDurationSeconds = minutes * 60))
                    },
                )
                if (details.supportsDistance) {
                    CardioAccentField(
                        value = distanceText,
                        onValueChange = { value ->
                            distanceText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                            onChange(details.copy(targetDistanceKm = distanceText.replace(',', '.').toDoubleOrNull()))
                        },
                        modifier = Modifier.weight(1f),
                        label = "Distancia km",
                        accentColor = accentColor,
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
                    CardioIntensityChip(
                        selected = details.intensity == intensity,
                        onClick = { onChange(details.copy(intensity = intensity)) },
                        label = intensityLabel(intensity),
                        accentColor = accentColor,
                    )
                }
            }
            if (details.type.isOutdoor()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Registrar GPS en vivo", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (details.requiresGps) "Guarda distancia y ritmo mientras entrenas."
                            else "Puedes activarlo para este cardio exterior.",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor.copy(alpha = 0.84f),
                        )
                    }
                    Switch(
                        checked = details.requiresGps,
                        onCheckedChange = { enabled -> onChange(details.copy(requiresGps = enabled)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor.copy(alpha = 0.75f),
                            checkedBorderColor = accentColor,
                            uncheckedThumbColor = accentColor.copy(alpha = 0.9f),
                            uncheckedTrackColor = accentColor.copy(alpha = 0.08f),
                            uncheckedBorderColor = accentColor.copy(alpha = 0.55f),
                        ),
                    )
                }
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { _ -> }
                androidx.compose.runtime.LaunchedEffect(details.requiresGps) {
                    if (details.requiresGps) {
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                }
                if (details.requiresGps) {
                    Text(
                        "Este cardio requiere ubicación. KPKN solicitará el permiso al iniciar el recorrido; si lo deniegas, conservarás el registro manual.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }
        }
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

@Composable
private fun CardioIntensityChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accentColor: Color,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = if (selected) 0.34f else 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = if (selected) 0.95f else 0.52f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
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
}

private fun intensityLabel(intensity: CardioIntensity): String = when (intensity) {
    CardioIntensity.BAJA -> "Baja"
    CardioIntensity.MEDIA -> "Media"
    CardioIntensity.ALTA -> "Alta"
    CardioIntensity.MUY_ALTA -> "Muy alta"
}
