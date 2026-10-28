package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.programdetail.calendarWeekCount
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.screens.programdetail.weekdayInitial
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.KpknSheet
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

/** Texto de hoy para un programa simple que aún no está calendarizado (≤ 160 caracteres, sin anglicismos). */
private const val SIN_CALENDARIZAR_TEXTO =
    "Úsalo si tus días libres cambian cada semana. Calendarizar fija fechas reales a tu ciclo; la pausa añade semanas vacías y deja el ciclo guardado intacto."

/** Texto de hoy para un ciclo ya calendarizado (≤ 160 caracteres). */
private const val CALENDARIZADO_TEXTO =
    "Tu ciclo quedó anclado a fechas reales. Los ciclos repetidos quedan en pausa hasta que recuperes el ciclo guardado."

/** Filas de fecha de la hoja. Solo una puede estar desplegada a la vez. */
private const val ROW_START = "inicio"
private const val ROW_END = "fin"

/**
 * Hoja de calendarización de un programa simple (P3a): sustituye la piel de la hoja anterior. Sus callbacks y sus dos estados no cambian:
 * - sin calendarizar: las fechas, el día que empieza la semana, los días de entreno y las dos acciones (calendarizar el ciclo o crear una
 *   pausa calendarizada);
 * - calendarizado: recuperar el ciclo guardado (apagado si no hay copia) o empezar uno nuevo.
 *
 * No se cierra con el asa ni con atrás (como antes): «Cerrar» es la salida.
 */
@Composable
internal fun ProgramSimpleCalendarSheet(
    today: LocalDate,
    isCalendarized: Boolean,
    startDate: String,
    endDate: String,
    startDayOfWeek: Int,
    trainingDays: Set<Int>,
    hasSavedCycle: Boolean,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onStartDayOfWeekChange: (Int) -> Unit,
    onTrainingDaysChange: (Set<Int>) -> Unit,
    onStartBreak: () -> Unit,
    onCalendarizeCycle: () -> Unit,
    onRecoverCycle: () -> Unit,
    onStartFreshCycle: () -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
) {
    val start = parseIsoDateExact(startDate)
    val end = parseIsoDateExact(endDate)
    val scroll = rememberScrollState()
    var openRow by rememberSaveable { mutableStateOf<String?>(null) }
    val weeks = if (start != null && end != null && !end.isBefore(start)) calendarWeekCount(start, end) else 0

    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState, dismissible = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(StructureSheetPadding),
        ) {
            if (isCalendarized) {
                StructureSheetHeader(title = "Ciclo calendarizado", subtitle = CALENDARIZADO_TEXTO)
                StructurePrimaryPill(
                    text = "Recuperar el ciclo guardado",
                    onClick = onRecoverCycle,
                    enabled = hasSavedCycle,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                StructureSecondarySmallPill(
                    text = "Empezar un ciclo nuevo",
                    onClick = onStartFreshCycle,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            } else {
                StructureSheetHeader(title = "Calendarizar el ciclo", subtitle = SIN_CALENDARIZAR_TEXTO)
                ProgramDateRow(
                    label = "Empieza",
                    selected = start,
                    today = today,
                    expanded = openRow == ROW_START,
                    onToggle = { openRow = if (openRow == ROW_START) null else ROW_START },
                    onPick = { picked ->
                        onStartDateChange(picked.toString())
                        openRow = null
                    },
                    onClear = if (start != null) ({ onStartDateChange("") }) else null,
                )
                ProgramDateRow(
                    label = "Termina",
                    selected = end,
                    today = today,
                    expanded = openRow == ROW_END,
                    onToggle = { openRow = if (openRow == ROW_END) null else ROW_END },
                    onPick = { picked ->
                        onEndDateChange(picked.toString())
                        openRow = null
                    },
                    minDate = start,
                    emptyValue = "Opcional",
                    onClear = if (end != null) ({ onEndDateChange("") }) else null,
                )
                if (weeks > 0) {
                    StructureCaption(
                        text = numberWithUnit(SpanishPlurals.weeks(weeks)),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                StructureSheetGroup(text = "La semana empieza el")
                StartDayChips(selected = startDayOfWeek, onSelect = onStartDayOfWeekChange)
                StructureSheetGroup(text = "Días de entreno")
                Spacer(Modifier.height(4.dp))
                ProgramWeekdayToggles(
                    trainingDays = trainingDays,
                    startDay = startDayOfWeek,
                    onToggle = { day ->
                        onTrainingDaysChange(if (day in trainingDays) trainingDays - day else trainingDays + day)
                    },
                )
                StructurePrimaryPill(
                    text = "Calendarizar ciclo actual",
                    onClick = onCalendarizeCycle,
                    enabled = start != null && trainingDays.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                )
                if (start != null && end != null && end.isAfter(start) && trainingDays.isNotEmpty()) {
                    StructureSecondarySmallPill(
                        text = "Crear pausa calendarizada",
                        onClick = onStartBreak,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                } else {
                    StructureCaption(
                        text = "Para crear una pausa calendarizada, elige cuándo empieza y cuándo termina.",
                        center = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            StructureAction(text = "Cerrar", onClick = onDismiss, faint = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** «La semana empieza el»: una chip por día (L M X J V S D) y la elegida en tinta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartDayChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            StructureChip(text = weekdayInitial(day), selected = selected == day, onClick = { onSelect(day) })
        }
    }
}
