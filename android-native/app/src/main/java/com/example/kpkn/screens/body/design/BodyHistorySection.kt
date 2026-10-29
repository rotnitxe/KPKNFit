package com.example.kpkn.screens.body.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.stories.BodyStoryShareSheet
import com.example.kpkn.screens.stories.bodyStoryAvailable
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import java.time.YearMonth
import java.util.Locale

/** Un mes del historial con sus mediciones, de la más reciente a la más antigua. */
internal data class BodyHistoryMonth(val month: YearMonth, val rows: List<BodyHistoryRow>)

/** Agrupa las mediciones por mes, del mes más reciente al más antiguo; dentro de cada mes, de la más reciente a la más antigua. */
internal fun bodyHistoryMonths(rows: List<BodyHistoryRow>): List<BodyHistoryMonth> = rows
    .groupBy { YearMonth.from(it.dateTime) }
    .map { (month, monthRows) -> BodyHistoryMonth(month, monthRows.sortedByDescending { it.timestampEpochMs }) }
    .sortedByDescending { it.month }

/**
 * Historial de Cuerpo para una lista perezosa (BODY_V1 §2.7): cada mes es una etiqueta de grupo sobre el negro y, debajo, un módulo con
 * una fila por medición. La fila muestra la fecha y la hora, la grasa y el músculo en `caption`, el peso tabular a la derecha y la
 * insignia «Health Connect» si viene de ahí. Tocar una fila llama a [onOpen]; sin mediciones, el estado vacío con su acción [onAdd].
 */
fun LazyListScope.bodyHistoryItems(
    rows: List<BodyHistoryRow>,
    weightUnit: WizardMassUnit,
    onOpen: (BodyHistoryRow) -> Unit,
    onAdd: () -> Unit,
) {
    if (rows.isEmpty()) {
        item(key = "body-history-empty") {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                KpknEmptyState(
                    icon = KpknLineIcons.Body,
                    title = "Aún no hay medidas",
                    body = "Cada pesaje y cada medida quedan aquí, ordenados por mes.",
                    action = { KpknPrimaryPill(text = "Registrar medidas", onClick = onAdd) },
                )
            }
        }
        return
    }
    bodyHistoryMonths(rows).forEachIndexed { index, group ->
        item(key = "body-history-${group.month}") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp)
                    .kpknEntrance("body-history", index),
            ) {
                val label = bodyMonthEs(group.month).uppercase(Locale.forLanguageTag("es"))
                if (index == 0) {
                    // El ⋯ del historial (h-body-sheet) abre «Compartir tu progreso» con todas las mediciones.
                    BodyHistoryHeadWithMenu(label = label, rows = rows, weightUnit = weightUnit)
                } else {
                    Text(
                        text = label,
                        style = KpknType.eyebrow,
                        color = WizardColors.textMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }
                KpknModule(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    group.rows.forEachIndexed { rowIndex, row ->
                        if (rowIndex > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(1.dp)
                                    .background(KpknSurface.divider),
                            )
                        }
                        BodyHistoryRowItem(row = row, unit = weightUnit, onClick = { onOpen(row) })
                    }
                }
            }
        }
    }
}

/**
 * Etiqueta del primer mes con el ⋯ a la derecha: su menú tiene «Compartir tu progreso», que abre la hoja de historias de Cuerpo.
 * Sin datos para una historia, solo la etiqueta (mismo aspecto que los demás meses).
 */
@Composable
private fun BodyHistoryHeadWithMenu(label: String, rows: List<BodyHistoryRow>, weightUnit: WizardMassUnit) {
    val canShare = remember(rows) { bodyStoryAvailable(rows) }
    var menu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    if (sharing) BodyStoryShareSheet(rows = rows, weightUnit = weightUnit, onDismiss = { sharing = false })
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = if (canShare) 0.dp else 8.dp),
        )
        if (canShare) {
            Box {
                KpknIconButton(
                    icon = KpknLineIcons.More,
                    contentDescription = "Más opciones del historial",
                    onClick = { menu = true },
                    modifier = Modifier.testTag("body-history-more"),
                )
                KpknDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Compartir tu progreso") },
                        onClick = {
                            menu = false
                            sharing = true
                        },
                    )
                }
            }
        }
    }
}

/** Una medición del historial: 56 dp como mínimo, con relleno `modulePressed` al pulsar y `Role.Button`. */
@Composable
private fun BodyHistoryRowItem(row: BodyHistoryRow, unit: WizardMassUnit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowShape = RoundedCornerShape(16.dp)
    val dateLabel = "${bodyWeekdayDayEs(row.dateTime.toLocalDate())} · ${bodyTimeEs(row.dateTime)}"
    // Cada etiqueta va unida a su cifra con espacios sin ruptura: al 130 % no se parte «Músculo» de su número.
    val details = listOfNotNull(
        row.bodyFatPercent?.let { "Grasa ${formatDecimalEs(it, 1)} %" },
        row.muscleMassPercent?.let { "Músculo ${formatDecimalEs(it, 1)} %" },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(rowShape)
            .background(if (pressed) KpknSurface.modulePressed else Color.Transparent, rowShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (row.fromHealthConnect) "Ver medición" else "Editar medición",
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = dateLabel, style = KpknType.bodyStrong, color = WizardColors.text)
            if (details.isNotEmpty()) {
                Text(text = details, style = KpknType.caption, color = WizardColors.textMuted)
            }
            if (row.fromHealthConnect) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(KpknShape.pill)
                        .background(KpknSurface.inset, KpknShape.pill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(text = "Health Connect", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        KpknMetric(
            value = row.weightKg?.let { bodyWeightValue(it, unit) } ?: "—",
            unit = row.weightKg?.let { unit.code },
            style = KpknType.metricM,
            color = if (row.weightKg != null) WizardColors.text else WizardColors.textFaint,
        )
    }
}
