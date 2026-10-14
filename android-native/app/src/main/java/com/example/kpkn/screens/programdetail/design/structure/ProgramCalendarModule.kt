package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.screens.programdetail.formatDaysUntil
import com.example.kpkn.screens.programdetail.formatShortDate
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Módulo «Calendario del programa» de la pestaña Estructura (P3a). Lee las fechas GUARDADAS del programa:
 * - por bloques: inicio, fin (manual o proyectado con «≈») y competición con su cuenta atrás;
 * - calendarizado: inicio, fin y la duración en semanas;
 * - cíclico: la semana que se repite, con «Calendarizar» debajo.
 *
 * Siempre va la fila de días de entreno de la semana en curso y, debajo, los avisos del motor. «Editar» abre la hoja del calendario
 * (o la de calendarización, en un programa simple). Sin estado: las fechas y los avisos llegan ya calculados.
 */
@Composable
internal fun ProgramCalendarModule(
    kind: ProgramToolsKind,
    today: LocalDate,
    startDate: LocalDate?,
    endDate: LocalDate?,
    endIsEstimate: Boolean,
    competitionDate: LocalDate?,
    trainingDays: Set<Int>,
    repeatingWeekLabel: String,
    durationWeeks: Int,
    messages: List<String>,
    onEdit: () -> Unit,
    onCalendarize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(
            label = "Calendario",
            action = { KpknTextAction(text = "Editar", onClick = onEdit) },
        )
        Spacer(Modifier.height(6.dp))
        when (kind) {
            ProgramToolsKind.BLOCKS -> {
                CalendarFact(label = "Inicio", value = startDate?.let(::formatShortDate) ?: "Sin fecha", faint = startDate == null)
                ModuleDivider()
                CalendarFact(
                    label = "Fin",
                    value = endValue(startDate, endDate, endIsEstimate),
                    faint = startDate == null || endDate == null,
                    estimate = endIsEstimate && startDate != null && endDate != null,
                )
                if (competitionDate != null) {
                    ModuleDivider()
                    CalendarFact(
                        label = "Competición",
                        value = formatShortDate(competitionDate),
                        note = formatDaysUntil(ChronoUnit.DAYS.between(today, competitionDate)),
                    )
                }
            }
            ProgramToolsKind.CALENDARIZED -> {
                CalendarFact(label = "Inicio", value = startDate?.let(::formatShortDate) ?: "Sin fecha", faint = startDate == null)
                ModuleDivider()
                CalendarFact(
                    label = "Fin",
                    value = endValue(startDate, endDate, endIsEstimate),
                    faint = startDate == null || endDate == null,
                    estimate = endIsEstimate && startDate != null && endDate != null,
                )
                ModuleDivider()
                CalendarFact(label = "Duración", value = numberWithUnit(SpanishPlurals.weeks(durationWeeks)))
            }
            ProgramToolsKind.CYCLIC -> {
                Text(
                    text = repeatingWeekLabel,
                    style = KpknType.body,
                    color = WizardColors.text,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = "Días de entreno", style = KpknType.caption, color = WizardColors.textMuted)
        Spacer(Modifier.height(6.dp))
        ProgramWeekdayRow(trainingDays = trainingDays)
        if (kind == ProgramToolsKind.CYCLIC) {
            Spacer(Modifier.height(10.dp))
            KpknTextAction(text = "Calendarizar", onClick = onCalendarize)
        }
        messages.forEach { message -> ProgramEngineMessage(text = message) }
    }
}

/**
 * Fin del programa: la fecha manual, o la proyectada con «≈» delante. Sin inicio o sin fin no se sabe: «Sin fecha», nunca una fecha inventada.
 */
private fun endValue(start: LocalDate?, end: LocalDate?, estimate: Boolean): String = when {
    start == null || end == null -> "Sin fecha"
    estimate -> "≈ ${formatShortDate(end)}"
    else -> formatShortDate(end)
}

/**
 * Una fila de dato: la etiqueta en `caption` y el valor en `bodyStrong` tabular, a la derecha, en una sola fila de 48 dp. Con [note], la
 * línea de la cuenta atrás va debajo en `energia`.
 */
@Composable
private fun CalendarFact(
    label: String,
    value: String,
    faint: Boolean = false,
    estimate: Boolean = false,
    note: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = when {
                    faint -> WizardColors.textFaint
                    estimate -> WizardColors.textMuted
                    else -> WizardColors.text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            note?.let {
                Text(
                    text = it,
                    style = KpknType.caption,
                    color = SymbolPalette.energia,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Separador de 1 dp entre filas de dato. */
@Composable
private fun ModuleDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
