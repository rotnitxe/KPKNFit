package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.data.models.CardioIntervalPatternSpec
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
    var selectedBlockIdx by remember(details.intervalBlocks.size) { mutableIntStateOf(0) }
    var showCompactListOverview by remember { mutableStateOf(false) }

    // Asegurar índice válido
    val validSelectedIdx = if (details.intervalBlocks.isNotEmpty()) {
        selectedBlockIdx.coerceIn(0, details.intervalBlocks.lastIndex)
    } else 0

    val catalogInfo = remember(details.type) { CardioCatalog.findByType(details.type) }
    val catalogSupportsSpeed = catalogInfo?.supportsSpeed ?: true
    val catalogSupportsIncline = catalogInfo?.supportsIncline ?: false
    val catalogSupportsRpm = catalogInfo?.supportsRpm ?: false
    val catalogSupportsWatts = catalogInfo?.supportsWatts ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.06f))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Circuitos / Intervalos", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (hasIntervals) "${details.intervalBlocks.size} bloques · ${details.intervalRounds} ronda(s) · ${formatMinutes(details.totalIntervalSeconds())}"
                    else "Programa velocidades por tramos y pirámides",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                )
            }
            Text(
                "Modo intervalos",
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            "Duración derivada: ${formatMinutes(details.totalIntervalSeconds())}. Ajusta los bloques y las rondas para cambiarla.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f),
        )

        // Selector de patrones en carrusel: la forma de cada plantilla permite
        // reconocer una pirámide o una escalera antes de abrir el editor.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Patrón de intervalo", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 12.dp),
            ) {
                items(CardioIntervalPrograms.specs, key = { it.pattern.name }) { spec ->
                    val selected = intervalPatternLooksSelected(details, spec.pattern)
                    IntervalPatternTemplateCard(
                        spec = spec,
                        selected = selected,
                        accentColor = accentColor,
                        onClick = {
                            if (spec.pattern == CardioIntervalPattern.CUSTOM) {
                                onChange(details.copy(hiit = null))
                            } else {
                                val total = details.totalIntervalSeconds().coerceAtLeast(20 * 60)
                                onChange(
                                    CardioIntervalProgramBuilder.buildDetails(
                                        pattern = spec.pattern,
                                        totalSeconds = total,
                                        type = details.type,
                                        baseLevel = details.resolvedIntensityLevel(),
                                        base = details.copy(hiit = null),
                                    ),
                                )
                                selectedBlockIdx = 0
                            }
                        },
                    )
                }
            }
            val selectedPattern = CardioIntervalPrograms.specs.firstOrNull { spec ->
                spec.pattern != CardioIntervalPattern.CUSTOM && intervalPatternLooksSelected(details, spec.pattern)
            }
            Text(
                selectedPattern?.description
                    ?: "Elige un patrón para crear una base o usa Personalizado para editar bloque a bloque.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.58f),
            )
        }

        if (!hasIntervals) {
            // Estado inicial sin intervalos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val seed = listOf(
                            CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 9.0, intensityLevel = 7),
                            CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0, intensityLevel = 3),
                        )
                        onChange(details.copy(intervalBlocks = seed, intervalRounds = 1, targetDurationSeconds = seed.sumOf { it.durationSeconds }))
                        selectedBlockIdx = 0
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
                    Text(" Plantillas de intervalos", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                }
            }
        } else {
            // Control de Rondas estilizado y compacto (sin botones gigantes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Rondas del circuito", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (details.intervalRounds > 1) "Se repite ${details.intervalRounds} veces" else "1 vuelta completa",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }

                // Stepper de rondas estilizado
                CompactRoundStepper(
                    rounds = details.intervalRounds,
                    accentColor = accentColor,
                    onRoundsChange = { newRounds ->
                        val total = details.intervalBlocks.sumOf { it.durationSeconds } * newRounds
                        onChange(details.copy(intervalRounds = newRounds, targetDurationSeconds = total))
                    },
                )
            }

            // Gráfico interactivo centrado que llena el ancho y permite seleccionar bloques al tocar
            CardioIntervalChart(
                details = details,
                accentColor = accentColor,
                modifier = Modifier.fillMaxWidth(),
                showLabels = false,
                compact = true,
                selectedBlockIndex = validSelectedIdx,
                onSelectBlockIndex = { idx ->
                    selectedBlockIdx = idx
                },
            )
            Text(
                "Toca una barra para editarla · altura = intensidad · ancho = duración",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.52f),
            )

            // Selector horizontal de peldaños (Timeline de la pirámide/circuito)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Peldaños del circuito (${details.intervalBlocks.size})",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { showCompactListOverview = !showCompactListOverview },
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(
                            if (showCompactListOverview) Icons.Default.Tune else Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            if (showCompactListOverview) " Ver inspector" else " Ver lista",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Timeline de peldaños interactivo
                CardioIntervalStepBar(
                    blocks = details.intervalBlocks,
                    selectedIdx = validSelectedIdx,
                    accentColor = accentColor,
                    onSelectIdx = { selectedBlockIdx = it },
                )
            }

            if (!showCompactListOverview && details.intervalBlocks.isNotEmpty()) {
                // INSPECTOR DE BLOQUE SELECCIONADO (Diseño amigable, compacto y no repetitivo)
                val activeBlock = details.intervalBlocks[validSelectedIdx]

                CardioActiveBlockInspector(
                    block = activeBlock,
                    index = validSelectedIdx,
                    total = details.intervalBlocks.size,
                    accentColor = accentColor,
                    catalogSupportsSpeed = catalogSupportsSpeed,
                    catalogSupportsIncline = catalogSupportsIncline,
                    catalogSupportsRpm = catalogSupportsRpm,
                    catalogSupportsWatts = catalogSupportsWatts,
                    onUpdate = { updated ->
                        val newBlocks = details.intervalBlocks.toMutableList()
                        newBlocks[validSelectedIdx] = updated
                        val total = newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                    },
                    onDelete = {
                        val newBlocks = details.intervalBlocks.filterIndexed { i, _ -> i != validSelectedIdx }
                        val total = if (newBlocks.isEmpty()) 20 * 60 else newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                        if (validSelectedIdx >= newBlocks.size && newBlocks.isNotEmpty()) {
                            selectedBlockIdx = newBlocks.lastIndex
                        }
                    },
                    onDuplicate = {
                        val duplicate = activeBlock.copy(id = UUID.randomUUID().toString())
                        val newBlocks = details.intervalBlocks.toMutableList()
                        newBlocks.add(validSelectedIdx + 1, duplicate)
                        val total = newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                        selectedBlockIdx = validSelectedIdx + 1
                    },
                    onPrevious = if (validSelectedIdx > 0) {
                        { selectedBlockIdx = validSelectedIdx - 1 }
                    } else null,
                    onNext = if (validSelectedIdx < details.intervalBlocks.lastIndex) {
                        { selectedBlockIdx = validSelectedIdx + 1 }
                    } else null,
                )
            } else if (showCompactListOverview) {
                // VISTA DE LISTA COMPACTA DE TODOS LOS BLOQUES
                CardioCompactBlocksOverview(
                    blocks = details.intervalBlocks,
                    selectedIdx = validSelectedIdx,
                    accentColor = accentColor,
                    catalogSupportsSpeed = catalogSupportsSpeed,
                    onSelectIdx = { selectedBlockIdx = it },
                    onDelete = { idx ->
                        val newBlocks = details.intervalBlocks.filterIndexed { i, _ -> i != idx }
                        val total = if (newBlocks.isEmpty()) 20 * 60 else newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                        if (validSelectedIdx >= newBlocks.size && newBlocks.isNotEmpty()) {
                            selectedBlockIdx = newBlocks.lastIndex
                        }
                    },
                    onMoveUp = { idx ->
                        if (idx > 0) {
                            val newBlocks = details.intervalBlocks.toMutableList()
                            val tmp = newBlocks[idx - 1]
                            newBlocks[idx - 1] = newBlocks[idx]
                            newBlocks[idx] = tmp
                            onChange(details.copy(intervalBlocks = newBlocks))
                            selectedBlockIdx = idx - 1
                        }
                    },
                    onMoveDown = { idx ->
                        if (idx < details.intervalBlocks.size - 1) {
                            val newBlocks = details.intervalBlocks.toMutableList()
                            val tmp = newBlocks[idx + 1]
                            newBlocks[idx + 1] = newBlocks[idx]
                            newBlocks[idx] = tmp
                            onChange(details.copy(intervalBlocks = newBlocks))
                            selectedBlockIdx = idx + 1
                        }
                    },
                )
            }

            // Acciones inferiores
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val newBlock = CardioIntervalBlock(
                            id = UUID.randomUUID().toString(),
                            type = CardioBlockType.WORK,
                            durationSeconds = 60,
                            speedKmh = 9.0,
                            intensityLevel = 7,
                        )
                        val newBlocks = details.intervalBlocks + newBlock
                        val total = newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
                        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
                        selectedBlockIdx = newBlocks.lastIndex
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Text(" Añadir bloque", fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { showTemplatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
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
            title = "Plantillas de intervalos",
            onSelect = { template ->
                val newDetails = template.toDetails(details.type, details.intensity)
                onChange(newDetails.copy(requiresGps = details.requiresGps, supportsDistance = details.supportsDistance))
                showTemplatePicker = false
                selectedBlockIdx = 0
            },
            onDismiss = { showTemplatePicker = false },
        )
    }
}

/** Stepper compacto y estilizado de rondas con pastilla redondeada */
@Composable
internal fun CompactRoundStepper(
    rounds: Int,
    accentColor: Color,
    onRoundsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minRounds: Int = 1,
    maxRounds: Int = 30,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier.height(32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (rounds > minRounds) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable(enabled = rounds > minRounds) { onRoundsChange((rounds - 1).coerceAtLeast(minRounds)) },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", color = if (rounds > minRounds) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Black, fontSize = 14.sp)
            }

            Text(
                "${rounds}×",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (rounds < maxRounds) accentColor.copy(alpha = 0.40f) else Color.Transparent)
                    .clickable(enabled = rounds < maxRounds) { onRoundsChange((rounds + 1).coerceAtMost(maxRounds)) },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = if (rounds < maxRounds) Color.White else Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}

/** Selector horizontal de peldaños (Timeline) */
@Composable
private fun CardioIntervalStepBar(
    blocks: List<CardioIntervalBlock>,
    selectedIdx: Int,
    accentColor: Color,
    onSelectIdx: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        blocks.forEachIndexed { idx, block ->
            val isSelected = idx == selectedIdx
            val blockColor = when (block.type) {
                CardioBlockType.WARMUP -> Color(0xFF10B981)
                CardioBlockType.WORK -> accentColor
                CardioBlockType.RECOVER -> Color(0xFF38BDF8)
                CardioBlockType.COOLDOWN -> Color(0xFF10B981)
            }

            val badgeLabel = when {
                block.speedKmh != null -> "${block.speedKmh.toInt()}k"
                block.intensityLevel != null -> "N${block.intensityLevel}"
                block.watts != null -> "${block.watts}W"
                else -> CardioIntervalEngine.blockTypeShortLabel(block.type)
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectIdx(idx) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) blockColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f),
                border = BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) blockColor else Color.White.copy(alpha = 0.12f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${idx + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f),
                    )
                    Text(
                        badgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        color = if (isSelected) Color.White else blockColor.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

/** Inspector único para editar el bloque actualmente seleccionado */
@Composable
private fun CardioActiveBlockInspector(
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
    onDuplicate: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    var showDurationPicker by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Barra superior del inspector con navegación rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "${CardioIntervalEngine.blockTypeLabel(block.type)} (${index + 1}/$total)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPrevious ?: {},
                        enabled = onPrevious != null,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Anterior",
                            tint = if (onPrevious != null) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = onNext ?: {},
                        enabled = onNext != null,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Siguiente",
                            tint = if (onNext != null) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar bloque", tint = accentColor, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Selector de tipo de bloque
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CardioBlockType.entries.forEach { t ->
                    val selected = block.type == t
                    val typeColor = when (t) {
                        CardioBlockType.WARMUP -> Color(0xFF10B981)
                        CardioBlockType.WORK -> accentColor
                        CardioBlockType.RECOVER -> Color(0xFF38BDF8)
                        CardioBlockType.COOLDOWN -> Color(0xFF10B981)
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) typeColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, if (selected) typeColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onUpdate(block.copy(type = t)) },
                    ) {
                        Text(
                            CardioIntervalEngine.blockTypeShortLabel(t),
                            modifier = Modifier.padding(vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Duración con ajuste rápido y selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                CardioValuePill(
                    label = "Duración",
                    value = formatMinutes(block.durationSeconds),
                    accentColor = accentColor,
                    onClick = { showDurationPicker = true },
                    modifier = Modifier.weight(1f),
                )

                // Botones rápidos de ajuste de tiempo (+15s / -15s)
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.clickable {
                            val nextSec = (block.durationSeconds - 15).coerceAtLeast(15)
                            onUpdate(block.copy(durationSeconds = nextSec))
                        },
                    ) {
                        Text("-15s", modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable {
                            val nextSec = block.durationSeconds + 15
                            onUpdate(block.copy(durationSeconds = nextSec))
                        },
                    ) {
                        Text("+15s", modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (showDurationPicker) {
                CardioMinutesSecondsWheelDialog(
                    title = "Duración del bloque",
                    initialSeconds = block.durationSeconds,
                    accentColor = accentColor,
                    onDismiss = { showDurationPicker = false },
                    onConfirm = { seconds ->
                        onUpdate(block.copy(durationSeconds = seconds.coerceAtLeast(5)))
                        showDurationPicker = false
                    },
                )
            }

            var blockPicker by remember { mutableStateOf<String?>(null) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (catalogSupportsSpeed) {
                    CardioValuePill(
                        label = "km/h",
                        value = block.speedKmh?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "—",
                        accentColor = accentColor,
                        onClick = { blockPicker = "speed" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (catalogSupportsIncline) {
                    CardioValuePill(
                        label = "Incl %",
                        value = block.inclinePercent?.toString() ?: "—",
                        accentColor = accentColor,
                        onClick = { blockPicker = "incline" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (catalogSupportsRpm) {
                    CardioValuePill(
                        label = "RPM",
                        value = block.rpm?.toString() ?: "—",
                        accentColor = accentColor,
                        onClick = { blockPicker = "rpm" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (catalogSupportsWatts) {
                    CardioValuePill(
                        label = "W",
                        value = block.watts?.toString() ?: "—",
                        accentColor = accentColor,
                        onClick = { blockPicker = "watts" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!catalogSupportsSpeed) {
                    CardioValuePill(
                        label = "Nivel",
                        value = block.intensityLevel?.toString() ?: "—",
                        accentColor = accentColor,
                        onClick = { blockPicker = "level" },
                        modifier = Modifier.weight(1f),
                    )
                }
                CardioValuePill(
                    label = "Ritmo",
                    value = block.targetPaceSecondsPerKm?.let { com.example.kpkn.domain.cardio.CardioPrescriptionFormatter.formatPace(it) } ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "pace" },
                    modifier = Modifier.weight(1f),
                )
                CardioValuePill(
                    label = "% FC",
                    value = block.targetHrPercent?.toString() ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "hr" },
                    modifier = Modifier.weight(1f),
                )
            }
            when (blockPicker) {
                "speed" -> CardioTenthsWheelDialog(
                    title = "Velocidad",
                    initial = block.speedKmh,
                    wholeRange = 3..25,
                    unit = "km/h",
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(speedKmh = it)); blockPicker = null },
                )
                "incline" -> CardioTenthsWheelDialog(
                    title = "Inclinación",
                    initial = block.inclinePercent,
                    wholeRange = 0..20,
                    unit = "%",
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(inclinePercent = it)); blockPicker = null },
                )
                "rpm" -> CardioIntWheelDialog(
                    title = "Cadencia",
                    initial = block.rpm ?: 80,
                    range = 40..130,
                    unit = "rpm",
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(rpm = it)); blockPicker = null },
                    allowZeroAsNone = false,
                )
                "watts" -> CardioIntWheelDialog(
                    title = "Vatios",
                    initial = block.watts ?: 120,
                    range = 0..500,
                    unit = "W",
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(watts = it)); blockPicker = null },
                )
                "level" -> CardioIntWheelDialog(
                    title = "Nivel",
                    initial = block.intensityLevel ?: 6,
                    range = 1..10,
                    unit = "RPE",
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(intensityLevel = it)); blockPicker = null },
                    allowZeroAsNone = false,
                )
                "pace" -> CardioPaceWheelDialog(
                    title = "Ritmo",
                    initialSecondsPerKm = block.targetPaceSecondsPerKm,
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(targetPaceSecondsPerKm = it)); blockPicker = null },
                )
                "hr" -> CardioIntWheelDialog(
                    title = "% FC",
                    initial = block.targetHrPercent ?: 75,
                    range = 50..100,
                    unit = "%",
                    accentColor = accentColor,
                    onDismiss = { blockPicker = null },
                    onConfirm = { onUpdate(block.copy(targetHrPercent = it)); blockPicker = null },
                    allowZeroAsNone = false,
                )
            }
        }
    }
}

/** Vista panorámica en formato de tabla compacta */
@Composable
private fun CardioCompactBlocksOverview(
    blocks: List<CardioIntervalBlock>,
    selectedIdx: Int,
    accentColor: Color,
    catalogSupportsSpeed: Boolean,
    onSelectIdx: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEachIndexed { idx, block ->
            val isSelected = idx == selectedIdx
            val typeColor = when (block.type) {
                CardioBlockType.WARMUP -> Color(0xFF10B981)
                CardioBlockType.WORK -> accentColor
                CardioBlockType.RECOVER -> Color(0xFF38BDF8)
                CardioBlockType.COOLDOWN -> Color(0xFF10B981)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) accentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.03f),
                border = BorderStroke(1.dp, if (isSelected) accentColor.copy(alpha = 0.6f) else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectIdx(idx) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "#${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        CardioIntervalEngine.blockTypeShortLabel(block.type),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = typeColor,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        formatMinutes(block.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (catalogSupportsSpeed && block.speedKmh != null) {
                        Text(
                            "${block.speedKmh} km/h",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    } else if (block.intensityLevel != null) {
                        Text(
                            "Nivel ${block.intensityLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { onMoveUp(idx) }, enabled = idx > 0, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowUpward, null, tint = if (idx > 0) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = { onMoveDown(idx) }, enabled = idx < blocks.size - 1, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ArrowDownward, null, tint = if (idx < blocks.size - 1) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = { onDelete(idx) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntervalPatternTemplateCard(
    spec: CardioIntervalPatternSpec,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(178.dp)
            .height(142.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.055f),
        border = BorderStroke(
            1.dp,
            if (selected) accentColor.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    spec.label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val previewUnits = spec.units.take(10)
                val maxLevel = previewUnits.maxOfOrNull { it.intensityLevel }?.coerceAtLeast(1) ?: 1
                previewUnits.forEach { unit ->
                    val heightFraction = (unit.intensityLevel.toFloat() / maxLevel).coerceIn(0.22f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (unit.type == CardioBlockType.RECOVER) {
                                    Color.White.copy(alpha = 0.28f)
                                } else {
                                    accentColor.copy(alpha = if (selected) 0.9f else 0.64f)
                                },
                            ),
                    )
                }
            }

            Text(
                spec.description,
                color = Color.White.copy(alpha = 0.58f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun intervalPatternLooksSelected(
    details: CardioDetails,
    pattern: CardioIntervalPattern,
): Boolean {
    if (details.intervalBlocks.isEmpty()) return false
    if (pattern == CardioIntervalPattern.CUSTOM) {
        return CardioIntervalPrograms.specs
            .asSequence()
            .filter { it.pattern != CardioIntervalPattern.CUSTOM }
            .none { intervalPatternDistance(details, it) < 0.28f }
    }
    return intervalPatternDistance(details, CardioIntervalPrograms.spec(pattern)) < 0.28f
}

private fun intervalPatternDistance(
    details: CardioDetails,
    spec: CardioIntervalPatternSpec,
): Float {
    val actual = details.intervalBlocks.filter {
        it.type == CardioBlockType.WORK || it.type == CardioBlockType.RECOVER
    }
    if (actual.size != spec.units.size) return 1f
    val expectedWeights = spec.units.map { it.durationWeight.coerceAtLeast(1).toFloat() }
    val actualDurations = actual.map { it.durationSeconds.coerceAtLeast(1).toFloat() }
    val expectedTotal = expectedWeights.sum().coerceAtLeast(1f)
    val actualTotal = actualDurations.sum().coerceAtLeast(1f)
    val weightDistance = expectedWeights.zip(actualDurations).sumOf { (expected, actualDuration) ->
        kotlin.math.abs(expected / expectedTotal - actualDuration / actualTotal).toDouble()
    }.toFloat()
    val typeDistance = spec.units.zip(actual).count { (expected, block) ->
        expected.type != block.type
    } / spec.units.size.toFloat()
    return (weightDistance + typeDistance).coerceAtMost(1f)
}

@Composable
internal fun IntervalAccentField(
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
        modifier = modifier
            .clip(shape)
            .background(accentColor.copy(alpha = if (focused) 0.14f else 0.08f))
            .border(1.dp, accentColor.copy(alpha = if (focused) 0.90f else 0.35f), shape)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
