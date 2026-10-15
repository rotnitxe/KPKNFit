package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.example.kpkn.domain.cardio.CardioIntervalProgramBuilder
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.CardioIntervalChart
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknStepperMetric
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import java.util.UUID

/**
 * Editor de bloques de un cardio por intervalos (la forma «Intervalos»): patrones, rondas, la gráfica en vivo, los peldaños, una fila
 * por bloque con su «⋯» (duplicar, subir, bajar y quitar) y el inspector del bloque elegido.
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

    // Asegurar índice válido
    val validSelectedIdx = if (details.intervalBlocks.isNotEmpty()) {
        selectedBlockIdx.coerceIn(0, details.intervalBlocks.lastIndex)
    } else 0

    val catalogInfo = remember(details.type) { CardioCatalog.findByType(details.type) }
    val catalogSupportsSpeed = catalogInfo?.supportsSpeed ?: true
    val catalogSupportsIncline = catalogInfo?.supportsIncline ?: false
    val catalogSupportsRpm = catalogInfo?.supportsRpm ?: false
    val catalogSupportsWatts = catalogInfo?.supportsWatts ?: false

    // Borra el bloque [idx] y deja la selección donde corresponde.
    fun deleteBlock(idx: Int) {
        val newBlocks = details.intervalBlocks.filterIndexed { i, _ -> i != idx }
        val total = if (newBlocks.isEmpty()) 20 * 60 else newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
        onChange(details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total))
        selectedBlockIdx = when {
            newBlocks.isEmpty() -> 0
            idx < validSelectedIdx -> validSelectedIdx - 1
            else -> validSelectedIdx.coerceAtMost(newBlocks.lastIndex)
        }
    }

    // Intercambia el bloque [idx] con el de [other] y sigue con la selección.
    fun swapBlocks(idx: Int, other: Int) {
        val newBlocks = details.intervalBlocks.toMutableList()
        val tmp = newBlocks[other]
        newBlocks[other] = newBlocks[idx]
        newBlocks[idx] = tmp
        onChange(details.copy(intervalBlocks = newBlocks))
        selectedBlockIdx = other
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Circuitos / Intervalos", style = KpknType.bodyStrong, color = WizardColors.text)
                Text(
                    if (hasIntervals) "${details.intervalBlocks.size} bloques · ${details.intervalRounds} ronda(s) · ${formatMinutes(details.totalIntervalSeconds())}"
                    else "Programa velocidades por tramos y pirámides",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            Text(
                "Modo intervalos",
                style = KpknType.eyebrow,
                color = WizardColors.text,
            )
        }

        Text(
            "Duración derivada: ${formatMinutes(details.totalIntervalSeconds())}. Ajusta los bloques y las rondas para cambiarla.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )

        // Selector de patrones en carrusel: la forma de cada plantilla permite
        // reconocer una pirámide o una escalera antes de abrir el editor.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PATRÓN DE INTERVALO", style = KpknType.eyebrow, color = WizardColors.textMuted)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp),
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
                style = KpknType.caption,
                color = WizardColors.textMuted,
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
            // Rondas del circuito: el stepper grande, centrado, y su texto debajo.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactRoundStepper(
                    rounds = details.intervalRounds,
                    accentColor = accentColor,
                    onRoundsChange = { newRounds ->
                        val total = details.intervalBlocks.sumOf { it.durationSeconds } * newRounds
                        onChange(details.copy(intervalRounds = newRounds, targetDurationSeconds = total))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Rondas del circuito",
                )
                Text(
                    if (details.intervalRounds > 1) "Se repite ${details.intervalRounds} veces" else "1 vuelta completa",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
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
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )

            // Peldaños del circuito: la línea de tiempo, tocable
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "PELDAÑOS DEL CIRCUITO (${details.intervalBlocks.size})",
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                CardioIntervalStepBar(
                    blocks = details.intervalBlocks,
                    selectedIdx = validSelectedIdx,
                    accentColor = accentColor,
                    onSelectIdx = { selectedBlockIdx = it },
                )
            }

            // Una fila de módulo por bloque: tipo, intensidad, duración y su «⋯».
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details.intervalBlocks.forEachIndexed { idx, block ->
                    CardioBlockRow(
                        index = idx,
                        total = details.intervalBlocks.size,
                        block = block,
                        selected = idx == validSelectedIdx,
                        accentColor = accentColor,
                        catalogSupportsSpeed = catalogSupportsSpeed,
                        onSelect = { selectedBlockIdx = idx },
                        onDuplicate = {
                            onChange(cardioDetailsWithDuplicatedBlock(details, idx, newId = { UUID.randomUUID().toString() }))
                            selectedBlockIdx = idx + 1
                        },
                        onMoveUp = { swapBlocks(idx, idx - 1) },
                        onMoveDown = { swapBlocks(idx, idx + 1) },
                        onDelete = { deleteBlock(idx) },
                    )
                }
            }

            // Inspector del bloque seleccionado: sus magnitudes y su duración.
            CardioActiveBlockInspector(
                block = details.intervalBlocks[validSelectedIdx],
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
                onPrevious = if (validSelectedIdx > 0) {
                    { selectedBlockIdx = validSelectedIdx - 1 }
                } else null,
                onNext = if (validSelectedIdx < details.intervalBlocks.lastIndex) {
                    { selectedBlockIdx = validSelectedIdx + 1 }
                } else null,
            )

            // Acciones inferiores
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                    modifier = Modifier.weight(1f),
                )
                KpknTextAction(
                    text = "Plantilla",
                    onClick = { showTemplatePicker = true },
                    leadingIcon = KpknLineIcons.Layers,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f),
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
 * Duplica el bloque [index] justo después de sí mismo, con un id nuevo, y recalcula la duración total con las rondas. Devuelve el
 * cardio igual si el índice no existe.
 */
internal fun cardioDetailsWithDuplicatedBlock(
    details: CardioDetails,
    index: Int,
    newId: () -> String,
): CardioDetails {
    val active = details.intervalBlocks.getOrNull(index) ?: return details
    val newBlocks = details.intervalBlocks.toMutableList()
    newBlocks.add(index + 1, active.copy(id = newId()))
    val total = newBlocks.sumOf { it.durationSeconds } * details.intervalRounds.coerceIn(1, 99)
    return details.copy(intervalBlocks = newBlocks, targetDurationSeconds = total)
}

/**
 * Color de un bloque, como en la gráfica: el esfuerzo en el acento, la recuperación en Columna y el calentamiento y el enfriamiento
 * en tinta apagada.
 */
internal fun cardioBlockColor(type: CardioBlockType, accent: Color): Color = when (type) {
    CardioBlockType.WORK -> accent
    CardioBlockType.RECOVER -> SymbolPalette.columna
    CardioBlockType.WARMUP, CardioBlockType.COOLDOWN -> WizardColors.textMuted
}

/** Paso de rondas: la cifra grande con «−» y «+» (KpknStepperMetric). [label] va en mayúsculas encima de la cifra. */
@Composable
internal fun CompactRoundStepper(
    rounds: Int,
    accentColor: Color,
    onRoundsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minRounds: Int = 1,
    maxRounds: Int = 30,
    label: String = "Rondas",
) {
    KpknStepperMetric(
        label = label.uppercase(Locale.forLanguageTag("es")),
        value = "$rounds ×",
        onMinus = { onRoundsChange((rounds - 1).coerceAtLeast(minRounds)) },
        onPlus = { onRoundsChange((rounds + 1).coerceAtMost(maxRounds)) },
        contentDescription = "$rounds ${label.lowercase(Locale.forLanguageTag("es"))}",
        minusEnabled = rounds > minRounds,
        plusEnabled = rounds < maxRounds,
        modifier = modifier,
    )
}

/** Peldaños del circuito: una pastilla por bloque, en su color, y la seleccionada en relieve. */
@Composable
private fun CardioIntervalStepBar(
    blocks: List<CardioIntervalBlock>,
    selectedIdx: Int,
    accentColor: Color,
    onSelectIdx: (Int) -> Unit,
) {
    val pillShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        blocks.forEachIndexed { idx, block ->
            val isSelected = idx == selectedIdx
            val blockColor = cardioBlockColor(block.type, accentColor)

            val badgeLabel = when {
                block.speedKmh != null -> "${block.speedKmh.toInt()} km/h"
                block.intensityLevel != null -> "N${block.intensityLevel}"
                block.watts != null -> "${block.watts} W"
                else -> shortPhaseLabel(block.type)
            }

            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(pillShape)
                    .background(if (isSelected) blockColor.copy(alpha = 0.22f) else KpknSurface.inset, pillShape)
                    .then(if (isSelected) Modifier.border(1.dp, blockColor, pillShape) else Modifier)
                    .clickable(role = Role.Button) { onSelectIdx(idx) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${idx + 1}",
                    style = KpknType.caption,
                    color = if (isSelected) WizardColors.text else WizardColors.textMuted,
                )
                Text(
                    badgeLabel,
                    style = KpknType.chip,
                    color = if (isSelected) WizardColors.text else blockColor,
                )
            }
        }
    }
}

/** Una fila de módulo para un bloque: su tipo con su punto de color, la intensidad, la duración y el «⋯» con sus acciones. */
@Composable
private fun CardioBlockRow(
    index: Int,
    total: Int,
    block: CardioIntervalBlock,
    selected: Boolean,
    accentColor: Color,
    catalogSupportsSpeed: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(KpknSurface.inset, rowShape)
            .then(if (selected) Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.24f), rowShape) else Modifier)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(cardioBlockColor(block.type, accentColor)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${index + 1} · ${CardioPrescriptionFormatter.phaseLabel(block.type)}",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                blockIntensityText(block, catalogSupportsSpeed),
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            KpknSetFormat.doseTime(block.durationSeconds),
            style = KpknType.metricM,
            color = WizardColors.text,
            maxLines = 1,
        )
        Box(modifier = Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Opciones del bloque ${index + 1}",
                onClick = { menuOpen = true },
            )
        }
    }
    if (menuOpen) {
        val rows = listOf(true, index > 0, index < total - 1, true).count { it }
        KpknContextMenu(anchor = anchor, rows = rows, onDismiss = { menuOpen = false }) {
            KpknContextMenuRow(
                label = "Duplicar bloque",
                onClick = {
                    menuOpen = false
                    onDuplicate()
                },
            )
            if (index > 0) {
                KpknContextMenuRow(
                    label = "Subir",
                    onClick = {
                        menuOpen = false
                        onMoveUp()
                    },
                )
            }
            if (index < total - 1) {
                KpknContextMenuRow(
                    label = "Bajar",
                    onClick = {
                        menuOpen = false
                        onMoveDown()
                    },
                )
            }
            KpknContextMenuRow(
                label = "Eliminar bloque",
                destructive = true,
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** Inspector único para editar el bloque actualmente seleccionado. */
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
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    var showDurationPicker by remember { mutableStateOf(false) }
    var blockPicker by remember { mutableStateOf<String?>(null) }
    val panelShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(KpknSurface.inset, panelShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Barra superior del inspector con navegación rápida
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${CardioPrescriptionFormatter.phaseLabel(block.type)} (${index + 1}/$total)",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onPrevious != null) {
                    KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Bloque anterior", onClick = onPrevious)
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                if (onNext != null) {
                    KpknIconButton(icon = KpknLineIcons.ChevronRight, contentDescription = "Bloque siguiente", onClick = onNext)
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // Selector de tipo de bloque
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardioBlockType.entries.forEach { t ->
                KpknChip(
                    text = CardioPrescriptionFormatter.phaseLabel(t),
                    selected = block.type == t,
                    onClick = { onUpdate(block.copy(type = t)) },
                )
            }
        }

        // Duración con ajuste rápido y selector
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val (durationValue, durationUnit) = durationCellParts(block.durationSeconds)
            CardioMagnitudeCell(
                label = "Duración",
                value = durationValue,
                unit = durationUnit,
                onClick = { showDurationPicker = true },
                modifier = Modifier.weight(1f),
            )
            KpknCompactPill(
                text = "−15 s",
                onClick = { onUpdate(block.copy(durationSeconds = (block.durationSeconds - 15).coerceAtLeast(15))) },
                filled = false,
            )
            KpknCompactPill(
                text = "+15 s",
                onClick = { onUpdate(block.copy(durationSeconds = block.durationSeconds + 15)) },
                filled = true,
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

        // Magnitudes del bloque, en celdas
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (catalogSupportsSpeed) {
                CardioMagnitudeCell(
                    label = "Velocidad",
                    value = block.speedKmh?.let { cellNumber(it) } ?: "—",
                    unit = block.speedKmh?.let { "km/h" },
                    onClick = { blockPicker = "speed" },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (catalogSupportsIncline) {
                CardioMagnitudeCell(
                    label = "Inclinación",
                    value = block.inclinePercent?.let { cellNumber(it) } ?: "—",
                    unit = block.inclinePercent?.let { "%" },
                    onClick = { blockPicker = "incline" },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (catalogSupportsRpm) {
                CardioMagnitudeCell(
                    label = "RPM",
                    value = block.rpm?.toString() ?: "—",
                    unit = null,
                    onClick = { blockPicker = "rpm" },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (catalogSupportsWatts) {
                CardioMagnitudeCell(
                    label = "Vatios",
                    value = block.watts?.toString() ?: "—",
                    unit = block.watts?.let { "W" },
                    onClick = { blockPicker = "watts" },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (!catalogSupportsSpeed) {
                CardioMagnitudeCell(
                    label = "Nivel",
                    value = block.intensityLevel?.toString() ?: "—",
                    unit = block.intensityLevel?.let { "RPE" },
                    onClick = { blockPicker = "level" },
                    modifier = Modifier.width(144.dp),
                )
            }
            val (paceValue, paceUnit) = paceCellParts(block.targetPaceSecondsPerKm)
            CardioMagnitudeCell(
                label = "Ritmo",
                value = paceValue,
                unit = paceUnit,
                onClick = { blockPicker = "pace" },
                modifier = Modifier.width(144.dp),
            )
            val hr = block.targetHrPercent
            CardioMagnitudeCell(
                label = "FC",
                value = hr?.toString() ?: "—",
                unit = hr?.let { "%" },
                onClick = { blockPicker = "hr" },
                modifier = Modifier.width(144.dp),
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

@Composable
private fun IntervalPatternTemplateCard(
    spec: CardioIntervalPatternSpec,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(178.dp)
            .height(142.dp)
            .clip(cardShape)
            .background(KpknSurface.inset, cardShape)
            .then(if (selected) Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.24f), cardShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                spec.label,
                color = WizardColors.text,
                style = KpknType.bodyStrong,
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
                        .background(cardioBlockColor(unit.type, accentColor).copy(alpha = if (selected) 0.9f else 0.64f)),
                )
            }
        }

        Text(
            spec.description,
            color = WizardColors.textMuted,
            style = KpknType.caption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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

/** Intensidad de un bloque en palabras y con su unidad: «9 km/h», «120 W», «Nivel 7». */
private fun blockIntensityText(block: CardioIntervalBlock, supportsSpeed: Boolean): String = when {
    supportsSpeed && block.speedKmh != null -> "${cellNumber(block.speedKmh)} km/h"
    block.watts != null -> "${block.watts} W"
    block.intensityLevel != null -> "Nivel ${block.intensityLevel}"
    else -> "—"
}

/** Tres letras para el peldaño sin velocidad ni nivel. */
private fun shortPhaseLabel(type: CardioBlockType): String = when (type) {
    CardioBlockType.WARMUP -> "CAL"
    CardioBlockType.WORK -> "ESF"
    CardioBlockType.RECOVER -> "PAU"
    CardioBlockType.COOLDOWN -> "ENF"
}

/**
 * Duración con el formato de la dosis («11 min 30 s»), con espacios inseparables entre cifra y unidad para que no se parta la «s».
 * Sin tiempo, «0 min».
 */
private fun formatMinutes(totalSeconds: Int): String =
    if (totalSeconds <= 0) "0 min" else CardioPrescriptionFormatter.formatDuration(totalSeconds).replace(' ', ' ')
