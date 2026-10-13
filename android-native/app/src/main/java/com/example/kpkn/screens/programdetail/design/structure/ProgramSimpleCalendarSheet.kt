package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.calendarWeekCount
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.screens.programdetail.weekdayInitial
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (isCalendarized) {
                Text(text = "Ciclo calendarizado", style = KpknType.title, color = WizardColors.text)
                Spacer(Modifier.height(6.dp))
                Text(text = CALENDARIZADO_TEXTO, style = KpknType.caption, color = WizardColors.textMuted)
                Spacer(Modifier.height(18.dp))
                KpknPrimaryPill(
                    text = "Recuperar el ciclo guardado",
                    onClick = onRecoverCycle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasSavedCycle,
                )
                Spacer(Modifier.height(10.dp))
                KpknSecondaryPill(
                    text = "Empezar un ciclo nuevo",
                    onClick = onStartFreshCycle,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(text = "Calendarizar el ciclo", style = KpknType.title, color = WizardColors.text)
                Spacer(Modifier.height(6.dp))
                Text(text = SIN_CALENDARIZAR_TEXTO, style = KpknType.caption, color = WizardColors.textMuted)
                Spacer(Modifier.height(14.dp))
                ProgramDateRow(
                    label = "Inicio",
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
                    label = "Fin (opcional)",
                    selected = end,
                    today = today,
                    expanded = openRow == ROW_END,
                    onToggle = { openRow = if (openRow == ROW_END) null else ROW_END },
                    onPick = { picked ->
                        onEndDateChange(picked.toString())
                        openRow = null
                    },
                    minDate = start,
                    emptyValue = "Sin fecha de fin",
                    onClear = if (end != null) ({ onEndDateChange("") }) else null,
                )
                if (weeks > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = numberWithUnit(SpanishPlurals.weeks(weeks)),
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ProgramSectionLabel(text = "La semana empieza el")
                Spacer(Modifier.height(8.dp))
                KpknSegmentedControl(
                    options = (1..7).map(::weekdayInitial),
                    selectedIndex = (startDayOfWeek - 1).coerceIn(0, 6),
                    onSelect = { index -> onStartDayOfWeekChange(index + 1) },
                    size = KpknSegmentedSize.Compact,
                )
                Spacer(Modifier.height(16.dp))
                ProgramSectionLabel(text = "Días de entreno")
                Spacer(Modifier.height(8.dp))
                ProgramWeekdayToggles(
                    trainingDays = trainingDays,
                    startDay = startDayOfWeek,
                    onToggle = { day ->
                        onTrainingDaysChange(if (day in trainingDays) trainingDays - day else trainingDays + day)
                    },
                )
                Spacer(Modifier.height(18.dp))
                KpknPrimaryPill(
                    text = "Calendarizar ciclo actual",
                    onClick = onCalendarizeCycle,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = start != null && trainingDays.isNotEmpty(),
                )
                Spacer(Modifier.height(10.dp))
                KpknSecondaryPill(
                    text = "Crear pausa calendarizada",
                    onClick = onStartBreak,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = start != null && end != null && end.isAfter(start) && trainingDays.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                KpknTextAction(text = "Cerrar", onClick = onDismiss)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
