package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.data.models.CardioIntervalPatternSpec
import com.example.kpkn.data.models.CardioIntervalPrograms
import com.example.kpkn.domain.cardio.CardioIntervalEngine
import com.example.kpkn.domain.cardio.CardioIntervalProgramBuilder
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.sessioneditor.SessionCardioDose
import com.example.kpkn.screens.workout.CardioIntervalChart
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import java.util.UUID

/**
 * Editor de circuitos e intervalos (SESSION_EDITOR_V1 §2, punto 7), con piel KPKN: sin cajas dentro del módulo, separado con aire y filetes.
 * Patrones, rondas, el gráfico interactivo de siempre, la tira de peldaños y el inspector del bloque o la lista. Sin cambios de lógica.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cabecera: el nombre del editor y su resumen.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Circuitos / Intervalos", style = KpknType.bodyStrong, color = WizardColors.text)
            Text(
                text = if (hasIntervals) {
                    "${details.intervalBlocks.size} bloques · ${if (details.intervalRounds == 1) "1 ronda" else "${details.intervalRounds} rondas"} ·${formatMinutes(details.totalIntervalSeconds())}"
                } else {
                    "Programa velocidades por tramos y pirámides"
                },
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            if (hasIntervals) {
                Text(
                    text = "Duración derivada: ${formatMinutes(details.totalIntervalSeconds())}. Ajusta los bloques y las rondas para cambiarla.",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
        }

        // Selector de patrones en carrusel: la forma de cada plantilla permite reconocer una pirámide o una escalera antes de abrir el editor.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Patrón de intervalo")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                items(CardioIntervalPrograms.specs, key = { it.pattern.name }) { spec ->
                    val selected = intervalPatternLooksSelected(details, spec.pattern)
                    IntervalPatternTemplate(
                        spec = spec,
                        selected = selected,
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
                text = selectedPattern?.description
                    ?: "Elige un patrón para crear una base o usa Personalizado para editar bloque a bloque.",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }

        if (!hasIntervals) {
            // Estado inicial sin intervalos
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KpknPrimaryPill(
                    text = "Crear circuito",
                    onClick = {
                        val seed = listOf(
                            CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 9.0, intensityLevel = 7),
                            CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0, intensityLevel = 3),
                        )
                        onChange(details.copy(intervalBlocks = seed, intervalRounds = 1, targetDurationSeconds = seed.sumOf { it.durationSeconds }))
                        selectedBlockIdx = 0
                    },
                    icon = KpknLineIcons.Plus,
                    modifier = Modifier.weight(1f),
                )
                KpknSecondaryPill(
                    text = "Plantillas",
                    onClick = { showTemplatePicker = true },
                    icon = KpknLineIcons.Layers,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // Rondas del circuito: el contador en píldora, sin botones grandes.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Rondas del circuito", style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(
                        text = if (details.intervalRounds > 1) "Se repite ${details.intervalRounds} veces" else "1 vuelta completa",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                CompactRoundStepper(
                    rounds = details.intervalRounds,
                    accentColor = accentColor,
                    onRoundsChange = { newRounds ->
                        val total = details.intervalBlocks.sumOf { it.durationSeconds } * newRounds
                        onChange(details.copy(intervalRounds = newRounds, targetDurationSeconds = total))
                    },
                )
            }

            // Gráfico interactivo que llena el ancho y permite seleccionar bloques al tocar.
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
                text = "Toca una barra para editarla · altura = intensidad · ancho = duración",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )

            // Peldaños del circuito: la tira de bloques y el cambio entre inspector y lista.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Kicker("Peldaños del circuito (${details.intervalBlocks.size})")
                    KpknTextAction(
                        text = if (showCompactListOverview) "Ver inspector" else "Ver lista",
                        onClick = { showCompactListOverview = !showCompactListOverview },
                    )
                }
                CardioIntervalStepBar(
                    blocks = details.intervalBlocks,
                    selectedIdx = validSelectedIdx,
                    onSelectIdx = { selectedBlockIdx = it },
                )
            }

            if (!showCompactListOverview && details.intervalBlocks.isNotEmpty()) {
                // Inspector del bloque seleccionado.
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
                // Vista de lista compacta de todos los bloques.
                CardioCompactBlocksOverview(
                    blocks = details.intervalBlocks,
                    selectedIdx = validSelectedIdx,
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

            // Acciones inferiores: cada una a su ancho natural y a los extremos, para que a 130 % no se corten a media palabra.
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                KpknTextAction(
                    text = "Añadir bloque",
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
                    leadingIcon = KpknLineIcons.Plus,
                )
                KpknTextAction(
                    text = "Plantilla",
                    onClick = { showTemplatePicker = true },
                    leadingIcon = KpknLineIcons.Layers,
                )
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

/**
 * Contador de rondas (SESSION_EDITOR_V1 §2): una píldora `inset` con el número en `metricM` y los dos botones de icono. En el límite, el
 * botón se queda en gris y no hace nada. Firma fija: lo usan el editor de intervalos y el de cardio.
 */
@Composable
internal fun CompactRoundStepper(
    rounds: Int,
    accentColor: Color,
    onRoundsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minRounds: Int = 1,
    maxRounds: Int = 30,
) {
    Row(
        modifier = modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknIconButton(
            icon = KpknLineIcons.Minus,
            contentDescription = "Quitar una ronda",
            onClick = { if (rounds > minRounds) onRoundsChange((rounds - 1).coerceAtLeast(minRounds)) },
            tint = if (rounds > minRounds) WizardColors.text else WizardColors.textFaint,
        )
        KpknMetric(
            value = rounds.toString(),
            unit = "×",
            style = KpknType.metricM,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Añadir una ronda",
            onClick = { if (rounds < maxRounds) onRoundsChange((rounds + 1).coerceAtMost(maxRounds)) },
            tint = if (rounds < maxRounds) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** Peldaños del circuito: una pastilla por bloque con su índice y su marca (velocidad, nivel o vatios). */
@Composable
private fun CardioIntervalStepBar(
    blocks: List<CardioIntervalBlock>,
    selectedIdx: Int,
    onSelectIdx: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        blocks.forEachIndexed { idx, block ->
            val badgeLabel = when {
                block.speedKmh != null -> "${block.speedKmh.toInt()} km/h"
                block.intensityLevel != null -> "N${block.intensityLevel}"
                block.watts != null -> "${block.watts} W"
                else -> CardioIntervalEngine.blockTypeShortLabel(block.type)
            }
            KpknChip(
                text = "#${idx + 1} · $badgeLabel",
                selected = idx == selectedIdx,
                onClick = { onSelectIdx(idx) },
            )
        }
    }
}

/** Inspector único para editar el bloque seleccionado: tipo, duración y las magnitudes del aparato. Sin caja: aire y un filete arriba. */
@OptIn(ExperimentalLayoutApi::class)
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
    var blockPicker by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))

        // Nombre del bloque con su posición, y la navegación entre bloques debajo.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(KpknSurface.inset),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.text,
                )
            }
            Text(
                text = "${CardioIntervalEngine.blockTypeLabel(block.type)} (${index + 1}/$total)",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KpknIconButton(
                    icon = KpknLineIcons.ChevronLeft,
                    contentDescription = "Anterior",
                    onClick = onPrevious ?: {},
                    tint = if (onPrevious != null) WizardColors.text else WizardColors.textFaint,
                )
                KpknIconButton(
                    icon = KpknLineIcons.ChevronRight,
                    contentDescription = "Siguiente",
                    onClick = onNext ?: {},
                    tint = if (onNext != null) WizardColors.text else WizardColors.textFaint,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KpknIconButton(
                    icon = KpknLineIcons.Repeat,
                    contentDescription = "Duplicar bloque",
                    onClick = onDuplicate,
                    tint = WizardColors.text,
                )
                KpknIconButton(
                    icon = KpknLineIcons.Trash,
                    contentDescription = "Eliminar bloque",
                    onClick = onDelete,
                    tint = WizardColors.danger,
                )
            }
        }

        // Tipo del bloque: esfuerzo, pausa, calentamiento o vuelta a la calma.
        KpknSegmentedControl(
            options = CardioBlockType.entries.map { it.segmentLabel() },
            selectedIndex = CardioBlockType.entries.indexOf(block.type),
            onSelect = { onUpdate(block.copy(type = CardioBlockType.entries[it])) },
            size = KpknSegmentedSize.Compact,
            // Los rótulos van abreviados para caber a 13 sp; TalkBack lee el nombre entero (SE8).
            descriptions = CardioBlockType.entries.map { it.fullName() },
        )

        // Duración: la píldora abre el selector; los ajustes rápidos son acciones de texto de 48 dp.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CardioValuePill(
                label = "Duración",
                value = formatMinutes(block.durationSeconds),
                accentColor = accentColor,
                onClick = { showDurationPicker = true },
                modifier = Modifier.weight(1f),
            )
            KpknTextAction(
                text = "−15 s",
                onClick = {
                    val nextSec = (block.durationSeconds - 15).coerceAtLeast(15)
                    onUpdate(block.copy(durationSeconds = nextSec))
                },
            )
            KpknTextAction(
                text = "+15 s",
                onClick = { onUpdate(block.copy(durationSeconds = block.durationSeconds + 15)) },
            )
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

        // Magnitudes del aparato y del bloque: píldoras en fila que salta de línea.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (catalogSupportsSpeed) {
                CardioValuePill(
                    label = "Velocidad",
                    value = block.speedKmh?.let { if (it % 1.0 == 0.0) "${it.toInt()} km/h" else "%.1f km/h".format(it) } ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "speed" },
                )
            }
            if (catalogSupportsIncline) {
                CardioValuePill(
                    label = "Inclinación",
                    value = block.inclinePercent?.let { "$it %" } ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "incline" },
                )
            }
            if (catalogSupportsRpm) {
                CardioValuePill(
                    label = "RPM",
                    value = block.rpm?.toString() ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "rpm" },
                )
            }
            if (catalogSupportsWatts) {
                CardioValuePill(
                    label = "Vatios",
                    value = block.watts?.let { "$it W" } ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "watts" },
                )
            }
            if (!catalogSupportsSpeed) {
                CardioValuePill(
                    label = "Nivel",
                    value = block.intensityLevel?.toString() ?: "—",
                    accentColor = accentColor,
                    onClick = { blockPicker = "level" },
                )
            }
            CardioValuePill(
                label = "Ritmo",
                value = block.targetPaceSecondsPerKm?.let { CardioPrescriptionFormatter.formatPace(it) } ?: "—",
                accentColor = accentColor,
                onClick = { blockPicker = "pace" },
            )
            CardioValuePill(
                label = "% FC",
                value = block.targetHrPercent?.let { "$it %" } ?: "—",
                accentColor = accentColor,
                onClick = { blockPicker = "hr" },
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

/** Vista de lista: un bloque por fila, con su posición, tipo y duración; los movimientos y la papelera van en la segunda línea. */
@Composable
private fun CardioCompactBlocksOverview(
    blocks: List<CardioIntervalBlock>,
    selectedIdx: Int,
    catalogSupportsSpeed: Boolean,
    onSelectIdx: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEachIndexed { idx, block ->
            val isSelected = idx == selectedIdx
            if (idx > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = { onSelectIdx(idx) })
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${idx + 1} · ${CardioIntervalEngine.blockTypeShortLabel(block.type)}",
                        style = KpknType.bodyStrong,
                        color = if (isSelected) WizardColors.text else WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMinutes(block.durationSeconds),
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.text,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val marker = when {
                        catalogSupportsSpeed && block.speedKmh != null -> "${block.speedKmh} km/h"
                        block.intensityLevel != null -> "Nivel ${block.intensityLevel}"
                        else -> ""
                    }
                    Text(
                        text = marker,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.ChevronUp,
                        contentDescription = "Subir bloque ${idx + 1}",
                        onClick = { onMoveUp(idx) },
                        tint = if (idx > 0) WizardColors.text else WizardColors.textFaint,
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.ChevronDown,
                        contentDescription = "Bajar bloque ${idx + 1}",
                        onClick = { onMoveDown(idx) },
                        tint = if (idx < blocks.size - 1) WizardColors.text else WizardColors.textFaint,
                    )
                    KpknIconButton(
                        icon = KpknLineIcons.Trash,
                        contentDescription = "Eliminar bloque ${idx + 1}",
                        onClick = { onDelete(idx) },
                        tint = WizardColors.danger,
                    )
                }
            }
        }
    }
}

/** Plantilla de patrón: el nombre, una columna por unidad (esfuerzo en `energia`, pausa en blanco al 12 %) y su descripción. Sin caja. */
@Composable
private fun IntervalPatternTemplate(
    spec: CardioIntervalPatternSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(178.dp)
            .clickable(role = Role.Button, onClickLabel = spec.label, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = spec.label,
                style = KpknType.bodyStrong,
                color = if (selected) WizardColors.text else WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(WizardColors.text),
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
                                Color.White.copy(alpha = 0.12f)
                            } else {
                                SymbolPalette.energia.copy(alpha = if (selected) 1f else 0.55f)
                            },
                        ),
                )
            }
        }
        Text(
            text = spec.description,
            style = KpknType.caption,
            color = WizardColors.textFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Etiqueta de sección en mayúsculas (kicker, `eyebrow`) dentro del módulo. */
@Composable
private fun Kicker(text: String) {
    Text(
        text = text.uppercase(Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
        // Hasta dos líneas (KPKN_UI_V2 §5): a 130 % el rótulo se parte entre palabras antes de cortarse.
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
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

/** «1 min 30 s», «45 s» o «2 min»: la misma regla de duración que el resto del cardio. */
private fun formatMinutes(totalSeconds: Int): String = SessionCardioDose.minutesText(totalSeconds)

/** Rótulo corto en español del tipo de bloque para el selector; los abreviados del motor salen en inglés («WORK», «COOL»). */
private fun CardioBlockType.segmentLabel(): String = when (this) {
    CardioBlockType.WARMUP -> "Calent."
    CardioBlockType.WORK -> "Trabajo"
    CardioBlockType.RECOVER -> "Pausa"
    CardioBlockType.COOLDOWN -> "Enfr."
}

/** Nombre entero del tipo de bloque, que TalkBack lee cuando el rótulo va abreviado (SE8). */
private fun CardioBlockType.fullName(): String = when (this) {
    CardioBlockType.WARMUP -> "Calentamiento"
    CardioBlockType.WORK -> "Trabajo"
    CardioBlockType.RECOVER -> "Pausa"
    CardioBlockType.COOLDOWN -> "Enfriamiento"
}
