package com.example.kpkn.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Calendario de un mes para elegir una fecha (KPKN_UI_V2). Cabecera con el mes y las flechas, semana que empieza en lunes, hoy con un
 * anillo y la fecha elegida en un círculo de tinta con el número en negro. Los días fuera de [minDate]..[maxDate] salen apagados y no se
 * tocan. Cada día mide 48 dp de alto como mínimo. No guarda datos: solo recuerda el mes que muestra, que parte de la fecha elegida.
 */
@Composable
fun KpknMonthCalendar(
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var shown by rememberSaveable { mutableStateOf(YearMonth.from(selected ?: today).toString()) }
    val month = YearMonth.parse(shown)
    val firstMonth = minDate?.let { YearMonth.from(it) }
    val lastMonth = maxDate?.let { YearMonth.from(it) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthStep(
                description = "Mes anterior",
                icon = KpknLineIcons.ChevronLeft,
                enabled = firstMonth == null || month > firstMonth,
                onClick = { shown = month.minusMonths(1).toString() },
            )
            Text(
                text = monthTitle(month),
                modifier = Modifier.weight(1f),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            MonthStep(
                description = "Mes siguiente",
                icon = KpknLineIcons.ChevronRight,
                enabled = lastMonth == null || month < lastMonth,
                onClick = { shown = month.plusMonths(1).toString() },
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { day ->
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW, Locale.forLanguageTag("es")).uppercase(Locale.forLanguageTag("es")),
                    modifier = Modifier.weight(1f),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        kpknMonthCells(month).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    } else {
                        KpknDayCell(
                            date = date,
                            enabled = kpknDayEnabled(date, minDate, maxDate),
                            selected = date == selected,
                            isToday = date == today,
                            onPick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Flecha de mes: 48 dp de zona táctil, con nombre para TalkBack y apagada si el mes queda fuera del rango. */
@Composable
private fun MonthStep(description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** Un día: el número en un círculo de 40 dp. Elegido = tinta con número negro; hoy = anillo fino. */
@Composable
private fun KpknDayCell(
    date: LocalDate,
    enabled: Boolean,
    selected: Boolean,
    isToday: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
        append(kpknDayLabel(date))
        if (isToday) append(", hoy")
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        val numberColor = when {
            selected -> WizardColors.ctaContent
            enabled -> WizardColors.text
            else -> WizardColors.textFaint
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(if (selected) Modifier.background(WizardColors.text, CircleShape) else Modifier)
                .then(if (isToday && !selected) Modifier.border(1.dp, WizardColors.textMuted, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = numberColor,
                maxLines = 1,
            )
        }
    }
}

/** «miércoles 14 de octubre de 2026», para TalkBack. */
private fun kpknDayLabel(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")))

/** «Octubre de 2026»: tipo frase, en español. */
private fun monthTitle(month: YearMonth): String =
    month.atDay(1)
        .format(java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es")))
        .replaceFirstChar { it.uppercase(Locale.forLanguageTag("es")) }

/**
 * Celdas de la rejilla de [month], de lunes a domingo: `null` en los huecos antes del día 1 y después del último, para que cada
 * semana tenga siete. Función pura, para probarla sin pantalla.
 */
internal fun kpknMonthCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val days: List<LocalDate?> = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val trailing = (7 - (leading + days.size) % 7) % 7
    return List(leading) { null } + days + List(trailing) { null }
}

/** true si [date] se puede elegir: dentro de [minDate] y [maxDate], ambos incluidos cuando los hay. */
internal fun kpknDayEnabled(date: LocalDate, minDate: LocalDate?, maxDate: LocalDate?): Boolean =
    (minDate == null || !date.isBefore(minDate)) && (maxDate == null || !date.isAfter(maxDate))
