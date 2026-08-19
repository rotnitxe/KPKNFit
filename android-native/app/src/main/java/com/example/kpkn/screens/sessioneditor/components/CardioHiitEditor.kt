package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioHiitProgramBuilder
import kotlin.math.roundToInt

/** Authoring panel for the explicit HIIT/SIT mode. */
@Composable
internal fun CardioHiitEditor(
    details: CardioDetails,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
) {
    val config = details.hiit ?: CardioHiitConfig(targetRpe = details.resolvedRpe())
    var showAllPresets by remember { mutableStateOf(false) }

    fun update(next: CardioHiitConfig) {
        onChange(CardioHiitProgramBuilder.buildDetails(next, details.type, details))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Estructura HIIT / SIT", color = Color.White, fontWeight = FontWeight.Black)
                    Text(
                        "${config.protocol.name} · RPE ${formatRpe(config.targetRpe)} · ${formatSeconds(details.effectiveDurationSeconds())}",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = { showAllPresets = !showAllPresets }) {
                    Text(if (showAllPresets) "Ocultar" else "Presets", color = accentColor)
                }
            }

            if (showAllPresets) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardioHiitTemplates.all.forEach { template ->
                        Button(
                            onClick = { update(template.toConfig()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.09f),
                                contentColor = Color.White,
                            ),
                        ) {
                            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                Text("${template.name} · ${template.protocol.name}", fontWeight = FontWeight.Bold)
                                Text(template.description, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }

            Text("Protocolo", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                HiitChip(
                    selected = config.protocol == HiitProtocol.HIIT,
                    label = "HIIT",
                    accentColor = accentColor,
                    onClick = { update(config.copy(protocol = HiitProtocol.HIIT, targetRpe = config.targetRpe.coerceIn(1.0, 9.5))) },
                )
                HiitChip(
                    selected = config.protocol == HiitProtocol.SIT,
                    label = "SIT · all-out",
                    accentColor = accentColor,
                    onClick = { update(config.copy(protocol = HiitProtocol.SIT, targetRpe = 10.0)) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HiitIntField("Calentamiento (s)", config.warmupSeconds, accentColor, Modifier.weight(1f)) {
                    update(config.copy(warmupSeconds = it.coerceIn(0, 1800)))
                }
                HiitIntField("Enfriamiento (s)", config.cooldownSeconds, accentColor, Modifier.weight(1f)) {
                    update(config.copy(cooldownSeconds = it.coerceIn(0, 1800)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HiitIntField("Trabajo (s)", config.workSeconds, accentColor, Modifier.weight(1f)) {
                    update(config.copy(workSeconds = it.coerceAtLeast(1)))
                }
                HiitIntField("Descanso (s)", config.restSeconds, accentColor, Modifier.weight(1f)) {
                    update(config.copy(restSeconds = it.coerceAtLeast(0)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HiitIntField("Rondas", config.rounds, accentColor, Modifier.weight(1f)) {
                    update(config.copy(rounds = it.coerceIn(1, 99)))
                }
                HiitIntField("Series", config.sets, accentColor, Modifier.weight(1f)) {
                    update(config.copy(sets = it.coerceIn(1, 5)))
                }
                HiitIntField("Entre series (s)", config.restBetweenSetsSeconds, accentColor, Modifier.weight(1.25f)) {
                    update(config.copy(restBetweenSetsSeconds = it.coerceAtLeast(0)))
                }
            }

            Text("Objetivo del bloque de trabajo", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                HiitChip(HiitWorkTarget.TIME == config.workTargetType, "Tiempo", accentColor) {
                    update(config.copy(workTargetType = HiitWorkTarget.TIME, workTargetValue = null))
                }
                HiitChip(HiitWorkTarget.KCAL == config.workTargetType, "Kcal", accentColor) {
                    update(config.copy(workTargetType = HiitWorkTarget.KCAL))
                }
                HiitChip(HiitWorkTarget.DISTANCE == config.workTargetType, "Distancia", accentColor) {
                    update(config.copy(workTargetType = HiitWorkTarget.DISTANCE))
                }
            }
            if (config.workTargetType != HiitWorkTarget.TIME) {
                HiitDecimalField(
                    label = if (config.workTargetType == HiitWorkTarget.KCAL) "Kcal por bloque" else "Metros por bloque",
                    value = config.workTargetValue?.toString().orEmpty(),
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                ) { value -> update(config.copy(workTargetValue = value.toDoubleOrNull()?.takeIf { it > 0.0 })) }
                if (config.workTargetType == HiitWorkTarget.KCAL && config.workTargetValue == null) {
                    Text(
                        "El objetivo en kcal usa tu peso corporal para estimar el corte; si falta, se pedirá antes de iniciar.",
                        color = Color(0xFFFFD166),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text("RPE programado exacto", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("RPE ${formatRpe(config.targetRpe)} · ${rpeAnchor(config.targetRpe)}", color = accentColor, fontWeight = FontWeight.Bold)
                Text(if (config.protocol == HiitProtocol.SIT) "SIT fija RPE 10" else "Guía subjetiva, no zona de quema", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = config.targetRpe.toFloat(),
                onValueChange = { value ->
                    val rpe = (value * 2f).roundToInt() / 2.0
                    update(config.copy(targetRpe = if (config.protocol == HiitProtocol.SIT) 10.0 else rpe))
                },
                valueRange = 1f..10f,
                steps = 17,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = accentColor.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Descanso activo", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                Switch(
                    checked = config.restNature == HiitRestNature.ACTIVE,
                    onCheckedChange = { active -> update(config.copy(restNature = if (active) HiitRestNature.ACTIVE else HiitRestNature.PASSIVE)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor),
                )
            }
            listOf(
                "Beep 3-2-1" to config.beepsEnabled,
                "Vibración" to config.vibrationEnabled,
                "Indicaciones de voz" to config.voiceCuesEnabled,
                "Mantener pantalla encendida" to config.keepScreenOn,
            ).forEach { (label, enabled) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            update(
                                when (label) {
                                    "Beep 3-2-1" -> config.copy(beepsEnabled = value)
                                    "Vibración" -> config.copy(vibrationEnabled = value)
                                    "Indicaciones de voz" -> config.copy(voiceCuesEnabled = value)
                                    else -> config.copy(keepScreenOn = value)
                                },
                            )
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun HiitChip(selected: Boolean, label: String, accentColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = if (selected) 0.30f else 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = if (selected) 0.9f else 0.4f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun HiitIntField(label: String, value: Int, accentColor: Color, modifier: Modifier, onCommit: (Int) -> Unit) {
    HiitDecimalField(label, value.toString(), accentColor, modifier) { raw ->
        raw.toIntOrNull()?.let(onCommit)
    }
}

@Composable
private fun HiitDecimalField(label: String, value: String, accentColor: Color, modifier: Modifier, onCommit: (String) -> Unit) {
    var text by remember(label, value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = text,
        onValueChange = { next ->
            text = next.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
            if (!focused) onCommit(text)
        },
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = if (focused) 0.14f else 0.08f))
            .border(1.dp, accentColor.copy(alpha = if (focused) 0.9f else 0.45f), shape)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (!state.isFocused) onCommit(text.replace(',', '.'))
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(accentColor),
        decorationBox = { inner ->
            Column {
                Text(label, color = accentColor.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp)) { inner() }
            }
        },
    )
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val seconds = totalSeconds.coerceAtLeast(0) % 60
    return if (seconds == 0) "$minutes min" else "$minutes min ${seconds}s"
}

private fun formatRpe(value: Double): String = "%.1f".format(value.coerceIn(1.0, 10.0))

private fun rpeAnchor(value: Double): String = when (value.coerceIn(1.0, 10.0)) {
    in 1.0..2.0 -> "Muy suave"
    in 2.5..4.0 -> "Suave"
    in 4.5..6.0 -> "Algo duro"
    in 6.5..8.0 -> "Duro"
    in 8.5..9.0 -> "Muy duro"
    else -> "Máximo"
}
