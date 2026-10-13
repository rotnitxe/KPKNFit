package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import kotlin.math.abs

/** Etiquetas del selector de rango, en el mismo orden que [BodyChartRange]. */
private val RANGE_LABELS = listOf("1M", "3M", "6M", "1A", "Todo")

/**
 * Módulo «Peso» de Cuerpo (BODY_V1 §2.2): el rango, el peso actual con «hoy» o «hace n días», el cambio desde el primer pesaje del
 * rango, el ritmo medido, la gráfica y el objetivo. Sin ningún pesaje, el estado vacío con su acción. Sin estado propio.
 *
 * @param goalKg meta de peso en kg, o null si el objetivo no es de peso.
 * @param onRangeChange recibe el rango elegido en el selector.
 * @param onAdd abre el registro de un pesaje.
 */
@Composable
fun BodyWeightModule(
    summary: BodyWeightSummary,
    goalKg: Double?,
    points: List<BodyChartPoint>,
    range: BodyChartRange,
    today: LocalDate,
    onRangeChange: (BodyChartRange) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = summary.current
    if (current == null) {
        Box(modifier = modifier.fillMaxWidth()) {
            KpknEmptyState(
                icon = KpknLineIcons.Body,
                title = "Registra tu primer pesaje",
                body = "Con unos pocos pesajes ves tu tendencia y tu ritmo.",
                action = { KpknPrimaryPill(text = "Registrar pesaje", onClick = onAdd) },
            )
        }
        return
    }
    val remaining = summary.remaining
    val change = summary.change
    val startDay = summary.rangeStartDate
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(
            label = "Peso",
            action = {
                KpknSegmentedControl(
                    options = RANGE_LABELS,
                    selectedIndex = BodyChartRange.entries.indexOf(range).coerceAtLeast(0),
                    onSelect = { index -> onRangeChange(BodyChartRange.entries[index]) },
                    size = KpknSegmentedSize.Compact,
                    modifier = Modifier.width(232.dp),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        KpknMetric(value = formatDecimalEs(current, 1), unit = "kg", style = KpknType.metricXL)
        summary.currentDate?.let { day ->
            Text(text = bodyRelativeDayEs(day, today), style = KpknType.caption, color = WizardColors.textMuted)
        }
        if (change != null && startDay != null) {
            Spacer(Modifier.height(8.dp))
            BodyChangeLine(
                change = change,
                unit = "kg",
                since = "desde el ${bodyDayMonthEs(startDay)}",
                tone = bodyChangeTone(change, remaining),
            )
        }
        Spacer(Modifier.height(10.dp))
        val rate = summary.measuredWeeklyRate
        if (rate != null) {
            Text(
                text = "Ritmo: ${formatSignedDecimalEs(rate, 1)} kg por semana",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
        } else {
            Text(text = "Sin ritmo medido aún", style = KpknType.body, color = WizardColors.textMuted)
            Text(
                text = "Hacen falta 2 semanas de pesajes para medir tu ritmo",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }
        Spacer(Modifier.height(16.dp))
        BodyTrendChart(points = points, goal = goalKg, unit = "kg", name = "peso", modifier = Modifier.fillMaxWidth())
        if (goalKg != null && remaining != null) {
            Spacer(Modifier.height(16.dp))
            val reached = summary.progressPercent == 100 || abs(remaining) < BODY_FLAT_CHANGE
            BodyGoalBlock(
                headline = if (reached) "Meta alcanzada" else "Te faltan ${formatDecimalEs(abs(remaining), 1)} kg",
                detail = "Meta: ${bodyTargetEs(goalKg)} kg · ${bodyEtaText(summary.eta)}",
                progress = summary.progressPercent?.let { it / 100f },
            )
        }
    }
}
