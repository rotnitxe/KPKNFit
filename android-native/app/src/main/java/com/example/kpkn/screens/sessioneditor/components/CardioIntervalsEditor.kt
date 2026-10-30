package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioValueKind
import com.example.kpkn.domain.cardio.CardioZones
import com.example.kpkn.screens.workout.CardioZoneColors
import java.util.UUID

/*
 * Pulido v1 · CARDIO DEL EDITOR · la forma «Intervalos» (`ceInterUi` de `scenes-ce.js`): una gráfica tocable con el total, las rondas del
 * circuito, el inspector del bloque elegido (tipo, duración y solo las magnitudes que admite la máquina) y «Añadir bloque».
 * Sustituye a las tres vistas del mismo dato de antes (gráfica, peldaños y una fila por bloque) y al diálogo de plantillas.
 */

/** Lo que pide abrir el selector de una magnitud: su escala, la posición de partida y qué hacer al aplicar. */
internal class CardioValueRequest(
    val kind: CardioValueKind,
    val n: Int,
    val subtitle: String,
    val mode: CardioValueMode = CardioValueMode.VALUE,
    val apply: (n: Int, mode: CardioValueMode) -> Unit,
)

/** Un cambio de los bloques recalcula siempre la duración total (bloques × rondas), como hacía el editor de antes. */
internal fun CardioDetails.withBlocks(blocks: List<CardioIntervalBlock>): CardioDetails =
    copy(intervalBlocks = blocks, targetDurationSeconds = blocks.sumOf { it.durationSeconds } * intervalRounds.coerceIn(1, 99))

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
    return details.withBlocks(newBlocks)
}

/** El bloque nuevo de «Añadir bloque»: un esfuerzo de 1:00 a RPE 7. */
internal fun newCardioBlock(): CardioIntervalBlock =
    CardioIntervalBlock(id = UUID.randomUUID().toString(), type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 9.0, intensityLevel = 7)

/** Nivel de esfuerzo que se enseña de un bloque: el suyo o el de su tipo. */
internal fun CardioIntervalBlock.shownLevel(details: CardioDetails): Int = (
    intensityLevel ?: when (type) {
        CardioBlockType.WORK -> details.resolvedIntensityLevel()
        CardioBlockType.RECOVER -> 1
        CardioBlockType.WARMUP -> 3
        CardioBlockType.COOLDOWN -> 2
    }
    ).coerceIn(1, 10)

/** Velocidad que se enseña de un bloque sin ella (`ceV`): 10 en un esfuerzo, 6 en el calentamiento, 5,5 en la pausa y 5 en el enfriamiento. */
internal fun CardioIntervalBlock.shownSpeed(): Double = speedKmh ?: when (type) {
    CardioBlockType.WARMUP -> 6.0
    CardioBlockType.WORK -> 10.0
    CardioBlockType.RECOVER -> 5.5
    CardioBlockType.COOLDOWN -> 5.0
}

private fun blockName(type: CardioBlockType) = CardioPrescriptionFormatter.phaseLabel(type)

/** La forma «Intervalos» de la tarjeta. [selected] es el bloque del inspector; [onSelect] lo cambia. */
@Composable
internal fun CardioIntervalsSection(
    details: CardioDetails,
    selected: Int,
    onSelect: (Int) -> Unit,
    onChange: (CardioDetails) -> Unit,
    onOpenValue: (CardioValueRequest) -> Unit,
    onOpenZone: (current: Int, apply: (Int) -> Unit) -> Unit,
    onOpenBlockMenu: () -> Unit,
    typeName: String,
) {
    val blocks = details.intervalBlocks
    if (blocks.isEmpty()) return
    val i = selected.coerceIn(0, blocks.lastIndex)
    val block = blocks[i]
    val catalog = CardioCatalog.findByType(details.type)
    val total = details.totalIntervalSeconds()
    val rounds = details.intervalRounds.coerceIn(1, 99)

    fun update(updated: CardioIntervalBlock) = onChange(details.withBlocks(blocks.toMutableList().also { it[i] = updated }))

    // La gráfica con el total y su ayuda.
    Column {
        CeBlockChart(cardioBars(details), height = 72.dp, selected = i, onPick = onSelect)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = EdColors.ink, fontWeight = FontWeight.SemiBold)) {
                    append(CardioPrescriptionFormatter.formatDuration(total))
                }
                append(" en total · ${blocks.size} bloques")
                if (rounds > 1) append(" × $rounds rondas")
            },
            modifier = Modifier.padding(top = 10.dp),
            style = edText(13f, FontWeight.Normal, EdColors.muted, 18.2f, true),
        )
        CeHint("Toca una barra para editarla. Altura = intensidad, ancho = duración.", Modifier.padding(top = 2.dp))
    }

    // Rondas del circuito.
    CeRow(topRule = true, controlRow = true) {
        CeRowLabel("Rondas del circuito", if (rounds > 1) "Se repite $rounds veces" else "1 vuelta completa", bold = true)
        CeStepper(
            value = rounds,
            onMinus = { onChange(details.copy(intervalRounds = rounds - 1, targetDurationSeconds = blocks.sumOf { it.durationSeconds } * (rounds - 1))) },
            onPlus = { onChange(details.copy(intervalRounds = rounds + 1, targetDurationSeconds = blocks.sumOf { it.durationSeconds } * (rounds + 1))) },
            canMinus = rounds > 1,
            canPlus = rounds < 99,
            label = "rondas",
        )
    }

    // El inspector del bloque elegido.
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(CeColors.cell, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                blockName(block.type),
                modifier = Modifier.weight(1f, fill = false),
                style = edText(16f, FontWeight.SemiBold, EdColors.ink, 20f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text("${i + 1}/${blocks.size}", modifier = Modifier.padding(start = 4.dp), style = edText(12.5f, FontWeight.Medium, EdColors.faint, 16f, true), maxLines = 1)
            Spacer(Modifier.weight(1f))
            CeRoundIcon(CeIcons.Back, "Bloque anterior", enabled = i > 0) { onSelect(i - 1) }
            CeRoundIcon(CeIcons.Fwd, "Bloque siguiente", enabled = i < blocks.lastIndex) { onSelect(i + 1) }
            CeRoundIcon(CeIcons.More, "Más acciones del bloque", enabled = true, onClick = onOpenBlockMenu)
        }
        val types = listOf(CardioBlockType.WARMUP, CardioBlockType.WORK, CardioBlockType.RECOVER, CardioBlockType.COOLDOWN)
        CeSegmented(
            options = types.map(::blockName),
            selected = types.indexOf(block.type),
            onSelect = { update(block.copy(type = types[it])) },
            twoColumns = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val (value, unit) = durationCellParts(block.durationSeconds)
            CeCell(
                label = "Duración",
                value = value,
                unit = unit,
                onClick = {
                    onOpenValue(
                        CardioValueRequest(
                            CardioValueKind.BLOCK_DURATION,
                            CardioValueKind.BLOCK_DURATION.nOf(block.durationSeconds).coerceIn(1, 120),
                            "$typeName · bloque ${i + 1}",
                        ) { n, _ -> update(block.copy(durationSeconds = CardioValueKind.BLOCK_DURATION.valueOf(n))) },
                    )
                },
                modifier = Modifier.weight(1f),
                background = CeColors.module,
            )
            CePill("−15 s", { update(block.copy(durationSeconds = (block.durationSeconds - 15).coerceAtLeast(15))) })
            CePill("+15 s", { update(block.copy(durationSeconds = block.durationSeconds + 15)) }, filled = true)
        }
        val cells = buildList<@Composable (Modifier) -> Unit> {
            if (catalog?.supportsSpeed != false) {
                add { m ->
                    CeCell("Velocidad", cellNumber(block.shownSpeed()), "km/h", compact = true, modifier = m, onClick = {
                        onOpenValue(
                            CardioValueRequest(CardioValueKind.SPEED, Math.round(block.shownSpeed() * 10).toInt().coerceIn(30, 250), "Bloque ${i + 1}") { n, _ ->
                                update(block.copy(speedKmh = n / 10.0))
                            },
                        )
                    })
                }
            }
            if (catalog?.supportsIncline == true) {
                add { m ->
                    CeCell("Inclinación", cellNumber(block.inclinePercent ?: 1.0), "%", compact = true, modifier = m, onClick = {
                        onOpenValue(
                            CardioValueRequest(CardioValueKind.INCLINE, Math.round((block.inclinePercent ?: 1.0) * 10).toInt().coerceIn(0, 200), "Bloque ${i + 1}") { n, _ ->
                                update(block.copy(inclinePercent = n / 10.0))
                            },
                        )
                    })
                }
            }
            if (catalog?.supportsRpm == true) {
                add { m ->
                    CeCell("Cadencia", (block.rpm ?: 80).toString(), "rpm", compact = true, modifier = m, onClick = {
                        onOpenValue(
                            CardioValueRequest(CardioValueKind.CADENCE, (block.rpm ?: 80).coerceIn(40, 130), "Bloque ${i + 1}") { n, _ -> update(block.copy(rpm = n)) },
                        )
                    })
                }
            }
            if (catalog?.supportsWatts == true) {
                add { m ->
                    CeCell("Vatios", (block.watts ?: 120).toString(), "W", compact = true, modifier = m, onClick = {
                        onOpenValue(
                            CardioValueRequest(CardioValueKind.WATTS, ((block.watts ?: 120) / 5).coerceIn(0, 100), "Bloque ${i + 1}") { n, _ -> update(block.copy(watts = n * 5)) },
                        )
                    })
                }
            }
            val level = block.shownLevel(details)
            add { m ->
                CeCell(
                    "RPE", level.toString(), CardioPrescriptionFormatter.rpeAnchor(level), compact = true, modifier = m,
                    leadingDot = CardioZoneColors[CardioZones.zoneOfRpe(level) - 1],
                    onClick = {
                        onOpenValue(CardioValueRequest(CardioValueKind.BLOCK_RPE, level, "Bloque ${i + 1}") { n, _ -> update(block.copy(intensityLevel = n)) })
                    },
                )
            }
            val hrZone = block.targetHrPercent?.let(CardioZones::zoneOfPercent)
            add { m ->
                CeCell(
                    "Pulso", if (hrZone != null) "Z$hrZone" else "—", null, compact = true, modifier = m,
                    leadingDot = hrZone?.let { CardioZoneColors[it - 1] },
                    onClick = {
                        onOpenZone(hrZone ?: 0) { z -> update(block.copy(targetHrPercent = z.takeIf { it > 0 }?.let(CardioZones::midpointPercent))) }
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { cell -> cell(Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    CeLink(
        "Añadir bloque",
        onClick = {
            val added = blocks + newCardioBlock()
            onChange(details.withBlocks(added))
            onSelect(added.lastIndex)
        },
        leading = CeIcons.Plus,
    )
}

/** `.ce-ib`: botón redondo de 36 dp del inspector (anterior, siguiente, más). */
@Composable
internal fun CeRoundIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.039f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.133f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { CeIcon(icon, 18.dp, EdColors.ink) }
}

/** `.ce-menu` de un bloque: duplicar, mover antes, mover después y quitar (las acciones de las filas de antes, intactas). */
@Composable
internal fun CardioBlockMenuSheet(
    typeName: String,
    details: CardioDetails,
    selected: Int,
    onChange: (CardioDetails, newSelected: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val blocks = details.intervalBlocks
    if (blocks.isEmpty()) return
    val i = selected.coerceIn(0, blocks.lastIndex)
    fun swap(other: Int) {
        val list = blocks.toMutableList()
        val t = list[other]
        list[other] = list[i]
        list[i] = t
        onChange(details.copy(intervalBlocks = list), other)
    }
    CeSheet(onDismiss = onDismiss, modifier = Modifier.verticalScroll(rememberScrollState())) {
        CeSheetHeader("Bloque ${i + 1} · ${blockName(blocks[i].type)}", typeName)
        Column(Modifier.fillMaxWidth()) {
            CeMenuRow(CeIcons.Copy, "Duplicar bloque", "Queda justo después.", enabled = true, first = true) {
                CardioMessages.post("Bloque duplicado.")
                onChange(cardioDetailsWithDuplicatedBlock(details, i) { UUID.randomUUID().toString() }, i + 1)
            }
            CeMenuRow(CeIcons.Up, "Mover antes", null, enabled = i > 0) { swap(i - 1) }
            CeMenuRow(CeIcons.Down, "Mover después", null, enabled = i < blocks.lastIndex) { swap(i + 1) }
            CeMenuRow(CeIcons.Trash, "Quitar bloque", if (blocks.size <= 1) "Tiene que quedar al menos uno." else null, enabled = blocks.size > 1) {
                val list = blocks.filterIndexed { idx, _ -> idx != i }
                CardioMessages.post("Bloque quitado.")
                onChange(details.withBlocks(list), i.coerceAtMost(list.lastIndex))
            }
        }
    }
}

/** `.ce-mr`: fila de un menú (62 dp, disco con icono, rótulo con ayuda). */
@Composable
internal fun CeMenuRow(
    icon: ImageVector,
    title: String,
    sub: String?,
    enabled: Boolean = true,
    first: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 62.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(
                if (first) Modifier else Modifier.drawBehind {
                    drawRect(CeColors.rule, size = Size(size.width, 1.dp.toPx()))
                },
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CeRowIcon(icon)
        CeRowLabel(title, sub, bold = true)
    }
}
