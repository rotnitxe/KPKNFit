package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.formatDateRange
import com.example.kpkn.screens.programdetail.formatDayMonth
import com.example.kpkn.screens.programdetail.keyDateTypeLabel
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMonthCalendar
import java.time.LocalDate

/** Claves de lo que se despliega en la hoja. Solo una cosa está desplegada a la vez; la hoja de render abre una de salida. */
internal const val CALENDAR_ROW_START = "inicio"
internal const val CALENDAR_ROW_COMPETITION = "competicion"
internal const val CALENDAR_ROW_END = "fin"

/** Nombre de cada día ISO (1 = lunes), con mayúscula: empieza la fecha larga de los campos. */
private val WEEKDAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

/** «Lunes 6 oct 2026»: la fecha de un campo de la hoja Fechas. Pura. */
internal fun datesFieldLabel(date: LocalDate): String =
    "${WEEKDAY_NAMES[date.dayOfWeek.value - 1]} ${formatDayMonth(date)} ${date.year}"

/** «Open regional · 16 nov»: la fila de la competición; sin fecha, lo que hace falta para fijarla. Pura. */
internal fun competitionRowDetail(date: LocalDate?, title: String?, hasStart: Boolean): String = when {
    date != null -> listOfNotNull(title?.takeIf { it.isNotBlank() && it != "Competición" }, formatDayMonth(date)).joinToString(" · ")
    !hasStart -> "Elige antes cuándo empieza el programa"
    else -> "Sin fecha"
}

/** «20 – 24 oct · Viaje»: la fila de otra fecha clave. Pura. */
internal fun otherKeyDateDetail(keyDate: ProgramKeyDate): String {
    val start = parseIsoDateExact(keyDate.startDate)
    val end = parseIsoDateExact(keyDate.endDate)
    val range = when {
        start == null -> "Sin fecha"
        end == null || end == start -> formatDayMonth(start)
        else -> formatDateRange(start, end)
    }
    return "$range · ${keyDateTypeLabel(keyDate.type)}"
}

/**
 * e-dates, hoja «Fechas»: «Empieza» y «Termina» (con «· calculado» si el fin sale de las semanas) como campos que despliegan el calendario
 * de mes; los avisos del motor; «Fechas clave» con la competición, las otras fechas y «Añadir fecha clave»; los días de entreno, y
 * «Añadir una pausa». Cada cambio se guarda al elegirlo: la hoja no tiene «Guardar». Sin estado salvo qué está desplegado.
 */
@Composable
internal fun ProgramCalendarSheet(
    today: LocalDate,
    start: LocalDate?,
    manualEnd: LocalDate?,
    projectedEnd: LocalDate?,
    competition: LocalDate?,
    competitionTitle: String?,
    otherKeyDates: List<ProgramKeyDate>,
    trainingDays: Set<Int>,
    messages: List<String>,
    onStartChange: (LocalDate?) -> Unit,
    onEndChange: (LocalDate?) -> Unit,
    onCompetitionChange: (LocalDate?) -> Unit,
    onAddKeyDate: () -> Unit,
    onEditKeyDate: (ProgramKeyDate) -> Unit,
    onAddPause: () -> Unit,
    onDismiss: () -> Unit,
    initialOpenRow: String? = null,
) {
    var openRow by rememberSaveable { mutableStateOf(initialOpenRow) }
    fun toggle(row: String) {
        openRow = if (openRow == row) null else row
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(
                title = "Fechas",
                subtitle = "Con fechas, cada día de la Semana lleva su número y Inicio sabe qué toca hoy.",
            )
            StructureField(
                label = "Empieza",
                onClick = { toggle(CALENDAR_ROW_START) },
                modifier = Modifier.semantics { stateDescription = if (openRow == CALENDAR_ROW_START) "Desplegado" else "Plegado" },
            ) {
                DateValue(text = start?.let(::datesFieldLabel), empty = "Sin fecha")
            }
            if (openRow == CALENDAR_ROW_START) {
                DatePicker(
                    selected = start,
                    today = today,
                    minDate = null,
                    // Con competición, el inicio no se puede quitar: la competición necesita una semana en la que caer.
                    onClear = if (start != null && competition == null) ({ onStartChange(null) }) else null,
                    onPick = { picked ->
                        onStartChange(picked)
                        openRow = null
                    },
                )
            }
            StructureField(
                label = "Termina",
                onClick = { toggle(CALENDAR_ROW_END) },
                modifier = Modifier.semantics { stateDescription = if (openRow == CALENDAR_ROW_END) "Desplegado" else "Plegado" },
            ) {
                when {
                    manualEnd != null -> DateValue(text = datesFieldLabel(manualEnd), empty = "")
                    projectedEnd != null -> Text(
                        text = buildAnnotatedString {
                            append(datesFieldLabel(projectedEnd))
                            withStyle(StructureCaptionStyle.toSpanStyle().copy(color = StructureFaint)) { append(" · calculado") }
                        },
                        style = StructureFieldValueStyle.tabular(),
                        color = StructureInk,
                    )
                    else -> DateValue(text = null, empty = "Sin fecha")
                }
            }
            if (openRow == CALENDAR_ROW_END) {
                DatePicker(
                    selected = manualEnd,
                    today = today,
                    minDate = start,
                    onClear = if (manualEnd != null) ({ onEndChange(null) }) else null,
                    onPick = { picked ->
                        onEndChange(picked)
                        openRow = null
                    },
                )
            }
            messages.forEach { message ->
                StructureCaption(text = message, color = WizardColors.danger, modifier = Modifier.padding(top = 8.dp))
            }

            StructureSheetGroup(text = "Fechas clave")
            StructureMenuRow(
                icon = KpknLineIcons.Flag,
                title = "Competición",
                detail = competitionRowDetail(competition, competitionTitle, hasStart = start != null),
                divider = false,
                enabled = start != null,
                onClick = { toggle(CALENDAR_ROW_COMPETITION) },
            )
            if (openRow == CALENDAR_ROW_COMPETITION && start != null) {
                DatePicker(
                    selected = competition,
                    today = today,
                    minDate = today,
                    onClear = if (competition != null) ({ onCompetitionChange(null) }) else null,
                    onPick = { picked ->
                        onCompetitionChange(picked)
                        openRow = null
                    },
                )
            }
            otherKeyDates.forEach { keyDate ->
                StructureMenuRow(
                    icon = KpknLineIcons.Flag,
                    title = keyDate.title.ifBlank { "Sin nombre" },
                    detail = otherKeyDateDetail(keyDate),
                    onClick = { onEditKeyDate(keyDate) },
                )
            }
            StructureMenuRow(
                icon = KpknLineIcons.Plus,
                title = "Añadir fecha clave",
                detail = "Test, viaje, vacaciones",
                onClick = onAddKeyDate,
            )

            StructureSheetGroup(text = "Días de entreno")
            StructureWeekdayRow(trainingDays = trainingDays)

            StructureSheetGroup(text = "Pausas")
            StructureMenuRow(
                icon = KpknLineIcons.Pause,
                title = "Añadir una pausa",
                detail = "Las semanas siguientes se corren",
                onClick = onAddPause,
            )
        }
    }
}

/** El valor de un campo de fecha: la fecha en tinta, con cifras tabulares, o [empty] en `--faint`. */
@Composable
private fun DateValue(text: String?, empty: String) {
    Text(
        text = text ?: empty,
        style = StructureFieldValueStyle.tabular(),
        color = if (text != null) StructureInk else StructureFaint,
    )
}

/** El calendario de mes bajo un campo y, si hay fecha que quitar, «Quitar» a la derecha. */
@Composable
private fun DatePicker(
    selected: LocalDate?,
    today: LocalDate,
    minDate: LocalDate?,
    onClear: (() -> Unit)?,
    onPick: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        KpknMonthCalendar(selected = selected, onSelect = onPick, today = today, minDate = minDate)
        if (onClear != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StructureAction(text = "Quitar", onClick = onClear, faint = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
