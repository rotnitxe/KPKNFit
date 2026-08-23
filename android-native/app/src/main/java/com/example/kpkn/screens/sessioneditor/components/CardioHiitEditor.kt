package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitTemplate
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioHiitProgramBuilder
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog

/** Authoring panel for the explicit HIIT/SIT mode. */
@Composable
internal fun CardioHiitEditor(
    details: CardioDetails,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
) {
    val config = details.hiit ?: CardioHiitConfig(targetRpe = 9.0, protocol = HiitProtocol.HIIT)
    var showAllPresets by remember { mutableStateOf(false) }

    val effective = CardioHiitProgramBuilder.effectiveStructure(config)
    val gpsAvailable = details.type.isOutdoor()

    // TIME was historically exposed as a second total-work target.  It is no
    // longer an authoring control: HIIT/SIT duration comes from the visible
    // structure below.  Clear the legacy value when an old session is opened so
    // editing it cannot silently reintroduce the duplicate duration semantics.
    LaunchedEffect(config.workTargetType, config.workTargetValue) {
        if (config.workTargetType == HiitWorkTarget.TIME && config.workTargetValue != null) {
            onChange(
                CardioHiitProgramBuilder.buildDetails(
                    config.copy(workTargetValue = null),
                    details.type,
                    details,
                ),
            )
        }
    }

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

            Text(
                "Elige una plantilla para empezar; después ajusta rondas, tiempos y objetivo. El RPE se mueve de forma continua.",
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
            )

            Text(
                "Plantillas rápidas",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp),
            ) {
                items(
                    items = CardioHiitTemplates.all.filter { it.protocol == config.protocol }.take(6),
                    key = { it.id },
                ) { template ->
                    val selected = config.protocol == template.protocol &&
                        effective.rounds == template.rounds &&
                        config.workSeconds == template.toConfig().workSeconds
                    HiitTemplateCarouselCard(
                        template = template,
                        selected = selected,
                        accentColor = accentColor,
                        onClick = { update(template.toConfig()) },
                    )
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
                    modifier = Modifier.weight(1f),
                    onClick = {
                        update(
                            config.copy(
                                protocol = HiitProtocol.HIIT,
                                targetRpe = if (config.protocol == HiitProtocol.HIIT) config.targetRpe else 9.0,
                            ),
                        )
                    },
                )
                HiitChip(
                    selected = config.protocol == HiitProtocol.SIT,
                    label = "SIT · all-out",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        update(
                            config.copy(
                                protocol = HiitProtocol.SIT,
                                targetRpe = if (config.protocol == HiitProtocol.SIT) config.targetRpe else 10.0,
                            ),
                        )
                    },
                )
            }

            // ── Flujo ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HiitTimeField("Calentamiento", "", config.warmupSeconds, accentColor, Modifier.fillMaxWidth()) {
                        update(config.copy(warmupSeconds = it.coerceIn(0, 1800)))
                    }
                    FlowConnector(accentColor)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Rondas de esfuerzo", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                CompactRoundStepper(
                                    rounds = config.rounds,
                                    accentColor = accentColor,
                                    onRoundsChange = { update(config.copy(rounds = it.coerceIn(1, 99))) },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HiitTimeField("Esfuerzo", "", config.workSeconds, accentColor, Modifier.weight(1f)) {
                                    update(config.copy(workSeconds = it.coerceAtLeast(1)))
                                }
                                Text("→", color = accentColor.copy(alpha = 0.45f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                HiitTimeField("Recuperación", "", config.restSeconds, accentColor, Modifier.weight(1f)) {
                                    update(config.copy(restSeconds = it.coerceAtLeast(0)))
                                }
                            }
                        }
                    }
                    if (config.sets > 1) {
                        FlowConnector(accentColor)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HiitTimeField("Descanso entre series", "", config.restBetweenSetsSeconds, accentColor, Modifier.fillMaxWidth()) {
                                    update(config.copy(restBetweenSetsSeconds = it.coerceAtLeast(0)))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Series totales", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    CompactRoundStepper(
                                        rounds = config.sets,
                                        accentColor = accentColor,
                                        minRounds = 2,
                                        maxRounds = 5,
                                        onRoundsChange = { update(config.copy(sets = it.coerceIn(2, 5))) },
                                    )
                                }
                            }
                        }
                    }
                    FlowConnector(accentColor)
                    HiitTimeField("Enfriamiento", "", config.cooldownSeconds, accentColor, Modifier.fillMaxWidth()) {
                        update(config.copy(cooldownSeconds = it.coerceIn(0, 1800)))
                    }
                    HiitTimelineBar(config, accentColor, effective)
                }
            }

            Text("Objetivo opcional por bloque", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (gpsAvailable) {
                HiitChip(
                    selected = config.workTargetType == HiitWorkTarget.DISTANCE,
                    label = "Distancia",
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        update(config.copy(workTargetType = HiitWorkTarget.DISTANCE, workTargetValue = config.workTargetValue ?: 400.0))
                    },
                )
            }
            if (config.workTargetType == HiitWorkTarget.DISTANCE) {
                HiitDecimalField(
                    label = "Metros por bloque",
                    value = config.workTargetValue?.toString().orEmpty(),
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                ) { value -> update(config.copy(workTargetValue = value.toDoubleOrNull()?.takeIf { it > 0.0 })) }
            }

            Text("RPE ${formatRpe(config.targetRpe)} · ${rpeAnchor(config.targetRpe)}", color = accentColor, fontWeight = FontWeight.Bold)
            Slider(
                value = config.targetRpe.toFloat(),
                onValueChange = { value ->
                    update(config.copy(targetRpe = value.toDouble().coerceIn(1.0, 10.0)))
                },
                valueRange = 1f..10f,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = accentColor.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HiitTemplateCarouselCard(
    template: HiitTemplate,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(188.dp)
            .height(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.055f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accentColor.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                template.name,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val previewBlocks = template.blocks
                    .filter { it.type == CardioBlockType.WORK || it.type == CardioBlockType.RECOVER }
                    .take(10)
                val maxDuration = previewBlocks.maxOfOrNull { it.durationSeconds }?.coerceAtLeast(1) ?: 1
                previewBlocks.forEach { block ->
                    val heightFraction = (block.durationSeconds.toFloat() / maxDuration).coerceIn(0.22f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (block.type == CardioBlockType.RECOVER) {
                                    Color.White.copy(alpha = 0.28f)
                                } else {
                                    accentColor.copy(alpha = if (selected) 0.9f else 0.64f)
                                },
                            ),
                    )
                }
            }
            Text(
                "${template.rounds} rondas · ${template.level}",
                color = accentColor.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                template.description,
                color = Color.White.copy(alpha = 0.58f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HiitChip(
    selected: Boolean,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun HiitIntField(label: String, value: Int, accentColor: Color, modifier: Modifier, onCommit: (Int) -> Unit) {
    HiitDecimalField(label, value.toString(), accentColor, modifier) { raw ->
        raw.toIntOrNull()?.let(onCommit)
    }
}

/**
 * Campo de tiempo mm:ss que abre el picker flotante nativo de Android
 * (reloj + teclado), el mismo que usan los descansos de las series de fuerza.
 * Los minutos van en el campo de horas del picker y los segundos en el de
 * minutos ("Minutos : segundos").
 */
@Composable
private fun HiitTimeField(
    label: String,
    subtitle: String,
    totalSeconds: Int,
    accentColor: Color,
    modifier: Modifier,
    onCommit: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), shape)
            .clickable { showPicker = true }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Column {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = accentColor.copy(alpha = 0.70f), style = MaterialTheme.typography.labelSmall)
            Text(
                formatMmSs(totalSeconds.coerceAtLeast(0)),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (showPicker) {
        KpknNativeTimePickerDialog(
            title = label,
            initialHour = (totalSeconds.coerceAtLeast(0) / 60).coerceIn(0, 23),
            initialMinute = (totalSeconds.coerceAtLeast(0) % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                onCommit((minutes * 60 + seconds).coerceAtLeast(0))
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun FlowConnector(accentColor: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("▼", color = accentColor.copy(alpha = 0.30f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HiitCompactIntField(value: Int, accentColor: Color, modifier: Modifier, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = text,
        onValueChange = { next ->
            text = next.filter { it.isDigit() }.take(2)
            if (!focused) onCommit(text.toIntOrNull() ?: value)
        },
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = if (focused) 0.14f else 0.08f))
            .border(1.dp, accentColor.copy(alpha = if (focused) 0.9f else 0.45f), shape)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (!state.isFocused) onCommit(text.toIntOrNull() ?: value)
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        cursorBrush = SolidColor(accentColor),
    )
}

@Composable
private fun HiitTimelineBar(config: CardioHiitConfig, accentColor: Color, effective: CardioHiitProgramBuilder.EffectiveHiitStructure) {
    val wu = config.warmupSeconds
    val cd = config.cooldownSeconds
    val wrk = config.workSeconds
    val rst = config.restSeconds
    val rnd = effective.rounds
    val blk = effective.sets
    val lastWrk = effective.lastWorkSeconds
    val between = config.restBetweenSetsSeconds
    val perRound = wrk + rst
    val perBlock = perRound * rnd + if (blk > 1) between else 0

    val totalSeconds = if (effective.workTimeTargetSeconds != null) {
        wu + effective.workTimeTargetSeconds + (rnd - 1) * rst + cd
    } else {
        wu + perBlock * blk - (if (blk > 1) between else 0) + cd
    }

    val segments = buildList {
        if (wu > 0) add(TimelineSegment(wu, Color(0xFF22C55E)))
        repeat(blk) { i ->
            repeat(rnd) { roundIndex ->
                add(TimelineSegment(if (roundIndex == rnd - 1) lastWrk else wrk, Color(0xFFEF4444)))
                if (rst > 0 && !(effective.workTimeTargetSeconds != null && roundIndex == rnd - 1)) {
                    add(TimelineSegment(rst, Color(0xFF94A3B8)))
                }
            }
            if (i < blk - 1 && between > 0) add(TimelineSegment(between, Color(0xFFF59E0B)))
        }
        if (cd > 0) add(TimelineSegment(cd, Color(0xFF3B82F6)))
    }

    if (totalSeconds == 0) return

    val formula = buildString {
        if (wu > 0) append(formatMmSs(wu))
        append(" · [⚡💨]×${rnd}")
        if (blk > 1) append(" · ${formatMmSs(between)} · [⚡💨]×${rnd}").also {
            repeat(blk - 2) { append(" · ${formatMmSs(between)} · [⚡💨]×${rnd}") }
        }
        if (cd > 0) append(" · ${formatMmSs(cd)}")
        append(" = ${formatMmSs(totalSeconds)}")
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        ) {
            segments.forEach { seg ->
                Box(
                    modifier = Modifier
                        .weight(seg.durationSeconds.toFloat())
                        .fillMaxHeight()
                        .background(seg.color, RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            formula,
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private data class TimelineSegment(val durationSeconds: Int, val color: Color)

internal fun formatMmSs(totalSeconds: Int): String {
    val capped = totalSeconds.coerceIn(0, 99 * 60 + 59)
    val m = capped / 60
    val s = capped % 60
    return "%02d:%02d".format(m, s)
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

private fun CardioType.isOutdoor(): Boolean = when (this) {
    CardioType.RUN_OUTDOOR,
    CardioType.BIKE_OUTDOOR,
    CardioType.WALK,
    -> true
    else -> false
}
