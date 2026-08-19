package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.data.models.CardioIntervalPrograms
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.domain.cardio.CardioIntervalProgramBuilder
import com.example.kpkn.screens.workout.CardioIntervalChart
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun CardioIntervalsEditor(
    details: CardioDetails,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
) {
    val hasIntervals = details.hasIntervals()
    var showTemplatePicker by remember { mutableStateOf(false) }
    var totalMinutesText by remember(details.totalIntervalSeconds(), details.targetDurationSeconds) {
        mutableStateOf(((details.targetDurationSeconds ?: details.totalIntervalSeconds()).coerceAtLeast(60) / 60).toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Circuitos / Intervalos", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (hasIntervals) "${details.intervalBlocks.size} bloques · ${details.intervalRounds} ronda(s) · ${formatMinutes(details.totalIntervalSeconds())}"
                    else "Programa velocidades por tramos, como en la cinta",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
            Text(
                "Modo intervalos activo",
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            IntervalAccentField(
                value = totalMinutesText,
                onValueChange = { raw ->
                    totalMinutesText = raw.filter(Char::isDigit).take(4)
                    val minutes = totalMinutesText.toIntOrNull()?.coerceIn(1, 240)
                    if (minutes != null) {
                        val current = details.totalIntervalSeconds().coerceAtLeast(60)
                        if (minutes * 60 != current) {
                            onChange(rescaleIntervalDetails(details, minutes * 60))
                        }
                    }
                },
                label = "Duración total (min)",
                accentColor = accentColor,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Text(
                "El patrón escala automáticamente; cada bloque sigue siendo editable.",
                modifier = Modifier.weight(1.2f).align(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Patrón", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            CardioIntervalPrograms.specs.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { spec ->
                        val selected = details.intervalBlocks.isNotEmpty() &&
                            spec.pattern != CardioIntervalPattern.CUSTOM &&
                            details.intervalBlocks.any { it.type == CardioBlockType.WARMUP } &&
                            details.intervalBlocks.count { it.type == CardioBlockType.WORK } >= spec.units.count { it.type == CardioBlockType.WORK }
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                if (spec.pattern == CardioIntervalPattern.CUSTOM) {
                                    onChange(details.copy(hiit = null))
                                } else {
                                    val total = totalMinutesText.toIntOrNull()?.coerceIn(1, 240)?.times(60)
                                        ?: details.totalIntervalSeconds().coerceAtLeast(20 * 60)
                                    onChange(
                                        CardioIntervalProgramBuilder.buildDetails(
                                            pattern = spec.pattern,
                                            totalSeconds = total,
                                            type = details.type,
                                            baseLevel = details.resolvedIntensityLevel(),
                                            base = details.copy(hiit = null),
                                        ),
                                    )
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = accentColor.copy(alpha = if (selected) 0.28f else 0.06f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = if (selected) 0.85f else 0.30f)),
                        ) {
                            Text(spec.label, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (!hasIntervals) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val seed = listOf(
                            CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 9.0, intensityLevel = 7),
                            CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0, intensityLevel = 3),
                        )
                        onChange(details.copy(intervalBlocks = seed, intervalRounds = 1, targetDurationSeconds = seed.sumOf { it.durationSeconds }))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Text(" Crear circuito", fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp))
                }
                Button(
                    onClick = { showTemplatePicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = accentColor)
                    Text(" Plantillas HIIT", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        } else {
            // Rounds stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Rondas del circuito", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            val newRounds = (details.intervalRounds - 1).coerceAtLeast(1)
                            val total = details.intervalBlocks.sumOf { it.durationSeconds } * newRounds
                            onChange(details.copy(intervalRounds = newRounds, targetDurationSeconds = total))
                        },
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)),
                    ) { Text("−", color = Color.White, fontWeight = FontWeight.Black) }
                    Text("${details.intervalRounds}×", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(
                        onClick = {
                            val newRounds = (details.intervalRounds + 1).coerceAtMost(20)
                            val total = details.intervalBlocks.sumOf { it.durationSeconds } * newRounds
                            onChange(details.copy(intervalRounds = newRounds, targetDurationSeconds = total))
                        },
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accentColor.copy(alpha = 0.35f)),
                    ) { Text("+", color = Color.White, fontWeight = FontWeight.Black) }
                }
            }

            // Mini preview chart
            CardioIntervalChart(details = details, accentColor = accentColor, modifier = Modifier.fillMaxWidth(), showLabels = false, compact = true)

            // Blocks list
            details.intervalBlocks.forEachIndexed { idx, block ->
                CardioBlockRow(
                    block = block,
                    index = idx,
                    total = details.intervalBlocks.size,
                    accentColor = accentColor,
                    catalogSupportsSpeed = CardioCatalog.findByType(details.type)?.supportsSpeed ?: true,
                    catalogSupportsIncline = CardioCatalog.findByType(details.type)?.supportsIncline ?: false,
                    catalogSupportsRpm = CardioCatalog.findByType(details.type)?.supportsRpm ?: false,
                    catalogSupportsWatts = CardioCatalog.findByType(details.type)?.supportsWatts ?: false,
                    onUpdate = { updated ->
                        val newBlocks = details.intervalBlocks.toMutableList()
                        newBlocks[idx] = updated
                        val total = newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                    },
                    onDelete = {
                        val newBlocks = details.intervalBlocks.filterIndexed { i, _ -> i != idx }
                        val total = if (newBlocks.isEmpty()) 20 * 60 else newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                    },
                    onMoveUp = if (idx > 0) {
                        {
                            val newBlocks = details.intervalBlocks.toMutableList()
                            val tmp = newBlocks[idx - 1]
                            newBlocks[idx - 1] = newBlocks[idx]
                            newBlocks[idx] = tmp
                            onChange(details.copy(intervalBlocks = newBlocks))
                        }
                    } else null,
                    onMoveDown = if (idx < details.intervalBlocks.size - 1) {
                        {
                            val newBlocks = details.intervalBlocks.toMutableList()
                            val tmp = newBlocks[idx + 1]
                            newBlocks[idx + 1] = newBlocks[idx]
                            newBlocks[idx] = tmp
                            onChange(details.copy(intervalBlocks = newBlocks))
                        }
                    } else null,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val newBlock = CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 9.0, intensityLevel = 7)
                        val newBlocks = details.intervalBlocks + newBlock
                        val total = newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                    },
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(" Añadir bloque", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { showTemplatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = accentColor)
                    Text(" Plantilla", color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }

    if (showTemplatePicker) {
        HiitTemplatePickerDialog(
            cardioType = details.type,
            accentColor = accentColor,
            onSelect = { template ->
                val newDetails = template.toDetails(details.type, details.intensity)
                // Preserve GPS/distance flags from current details where relevant
                onChange(newDetails.copy(requiresGps = details.requiresGps, supportsDistance = details.supportsDistance))
                showTemplatePicker = false
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

@Composable
private fun CardioBlockRow(
    block: CardioIntervalBlock,
    index: Int,
    total: Int,
    accentColor: Color,
    catalogSupportsSpeed: Boolean,
    catalogSupportsIncline: Boolean,
    catalogSupportsRpm: Boolean,
    catalogSupportsWatts: Boolean,
    onUpdate: (CardioIntervalBlock) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var showDurationPicker by remember { mutableStateOf(false) }
    var speedText by remember(block.id) { mutableStateOf(block.speedKmh?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var inclineText by remember(block.id) { mutableStateOf(block.inclinePercent?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var rpmText by remember(block.id) { mutableStateOf(block.rpm?.toString() ?: "") }
    var wattsText by remember(block.id) { mutableStateOf(block.watts?.toString() ?: "") }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${index + 1}. ${CardioIntervalEngine.blockTypeLabel(block.type)}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onMoveUp != null) IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.ArrowUpward, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
                    if (onMoveDown != null) IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.ArrowDownward, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Delete, null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp)) }
                }
            }
            // Type chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CardioBlockType.entries.forEach { t ->
                    val selected = block.type == t
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.clickable { onUpdate(block.copy(type = t)) },
                    ) {
                        Text(
                            CardioIntervalEngine.blockTypeShortLabel(t),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            // Duration
            Box(modifier = Modifier.fillMaxWidth()) {
                IntervalAccentField(
                    value = formatMinutes(block.durationSeconds),
                    onValueChange = {},
                    label = "Duración",
                    accentColor = accentColor,
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.Timer, null, tint = accentColor, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showDurationPicker = true })
            }
            if (showDurationPicker) {
                KpknNativeTimePickerDialog(
                    title = "Duración del bloque",
                    initialHour = (block.durationSeconds / 60) / 60,
                    initialMinute = (block.durationSeconds / 60) % 60,
                    hint = "Horas : minutos",
                    onConfirm = { h, m ->
                        val sec = (h * 3600 + m * 60).coerceAtLeast(15)
                        onUpdate(block.copy(durationSeconds = sec))
                        showDurationPicker = false
                    },
                    onDismiss = { showDurationPicker = false },
                )
            }
            // Conditional fields
            if (catalogSupportsSpeed) {
                IntervalAccentField(
                    value = speedText,
                    onValueChange = { v ->
                        speedText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                        val parsed = speedText.replace(',', '.').toDoubleOrNull()
                        onUpdate(block.copy(speedKmh = parsed))
                    },
                    label = "Velocidad (km/h)",
                    accentColor = accentColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (catalogSupportsIncline) {
                IntervalAccentField(
                    value = inclineText,
                    onValueChange = { v ->
                        inclineText = v.filter { it.isDigit() || it == '.' || it == '-' }.take(5)
                        val parsed = inclineText.replace(',', '.').toDoubleOrNull()
                        onUpdate(block.copy(inclinePercent = parsed))
                    },
                    label = "Inclinación (%)",
                    accentColor = accentColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (catalogSupportsRpm) {
                IntervalAccentField(
                    value = rpmText,
                    onValueChange = { v ->
                        rpmText = v.filter { it.isDigit() }.take(4)
                        onUpdate(block.copy(rpm = rpmText.toIntOrNull()))
                    },
                    label = "RPM",
                    accentColor = accentColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (catalogSupportsWatts) {
                IntervalAccentField(
                    value = wattsText,
                    onValueChange = { v ->
                        wattsText = v.filter { it.isDigit() }.take(4)
                        onUpdate(block.copy(watts = wattsText.toIntOrNull()))
                    },
                    label = "Vatios (W)",
                    accentColor = accentColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!catalogSupportsSpeed) {
                // Intensidad genérica 1-10 como fallback cuando no hay velocidad (elíptica, escaladora, o bici/remo como nivel)
                var levelText by remember(block.id) { mutableStateOf(block.intensityLevel?.toString() ?: "") }
                IntervalAccentField(
                    value = levelText,
                    onValueChange = { v ->
                        levelText = v.filter { it.isDigit() }.take(2)
                        onUpdate(block.copy(intensityLevel = levelText.toIntOrNull()?.coerceIn(1, 10)))
                    },
                    label = "Intensidad 1-10",
                    accentColor = accentColor,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun IntervalAccentField(
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
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.clip(shape).background(accentColor.copy(alpha = if (focused) 0.14f else 0.08f)).border(1.dp, accentColor.copy(alpha = if (focused) 0.90f else 0.50f), shape).onFocusChanged { focused = it.isFocused }.padding(horizontal = 10.dp, vertical = 7.dp),
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(accentColor),
        decorationBox = { inner ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.labelSmall, color = if (focused) accentColor else accentColor.copy(alpha = 0.80f), fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp)) { inner() }
                }
                trailingIcon?.invoke()
            }
        },
    )
}

private fun formatMinutes(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (s == 0) "${m} min" else "${m}m ${s}s"
}

private fun rescaleIntervalDetails(details: CardioDetails, totalSeconds: Int): CardioDetails {
    val rounds = details.intervalRounds.coerceIn(1, 99)
    val perRoundTarget = (totalSeconds / rounds).coerceAtLeast(details.intervalBlocks.size * 15)
    val current = details.intervalBlocks.sumOf { it.durationSeconds }.coerceAtLeast(1)
    val scaled = details.intervalBlocks.map { block ->
        block.copy(durationSeconds = (block.durationSeconds.toDouble() * perRoundTarget / current).roundToInt().coerceAtLeast(15))
    }.toMutableList()
    var delta = perRoundTarget - scaled.sumOf { it.durationSeconds }
    var index = scaled.lastIndex
    while (delta != 0 && scaled.isNotEmpty()) {
        if (delta > 0) {
            val add = minOf(delta, 5)
            scaled[index] = scaled[index].copy(durationSeconds = scaled[index].durationSeconds + add)
            delta -= add
        } else {
            val removable = (scaled[index].durationSeconds - 15).coerceAtLeast(0)
            if (removable == 0) {
                index = (index - 1).takeIf { it >= 0 } ?: scaled.lastIndex
                if (scaled.all { it.durationSeconds <= 15 }) break
                continue
            }
            val subtract = minOf(-delta, removable, 5)
            scaled[index] = scaled[index].copy(durationSeconds = scaled[index].durationSeconds - subtract)
            delta += subtract
        }
        index = (index - 1).takeIf { it >= 0 } ?: scaled.lastIndex
    }
    val actual = scaled.sumOf { it.durationSeconds } * rounds
    return details.copy(intervalBlocks = scaled, intervalRounds = rounds, targetDurationSeconds = actual)
}
