package com.example.kpkn.screens.nutrition.reuse

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
import androidx.compose.foundation.selection.toggleable
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
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknDayEnabled
import com.example.kpkn.ui.design.kpknMonthCells
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val EsLocale: Locale = Locale.forLanguageTag("es")

/**
 * Calendario de un mes con SELECCIÓN MÚLTIPLE (N4, hoja «Copiar a…»): lo mismo que `KpknMonthCalendar` (semana desde el lunes, hoy con
 * anillo) pero cada día elegido lleva un círculo de tinta y tocarlo otra vez lo quita. [blocked] son días que no se pueden elegir (el
 * día de origen); el resto del rango [minDate]..[maxDate] sí. Cada día mide 48 dp de alto y expone «elegido» a TalkBack. Sin datos
 * propios: solo recuerda el mes que muestra.
 */
@Composable
fun ReuseDatesCalendar(
    selected: Set<LocalDate>,
    onToggle: (LocalDate) -> Unit,
    today: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    modifier: Modifier = Modifier,
    blocked: Set<LocalDate> = emptySet(),
    initialMonth: YearMonth = YearMonth.from(selected.minOrNull() ?: today),
) {
    var shown by rememberSaveable { mutableStateOf(initialMonth.toString()) }
    val month = YearMonth.parse(shown)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthStep("Mes anterior", KpknLineIcons.ChevronLeft, month > YearMonth.from(minDate)) { shown = month.minusMonths(1).toString() }
            Text(
                text = monthTitle(month),
                modifier = Modifier.weight(1f),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            MonthStep("Mes siguiente", KpknLineIcons.ChevronRight, month < YearMonth.from(maxDate)) { shown = month.plusMonths(1).toString() }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { day ->
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW, EsLocale).uppercase(EsLocale),
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
                        ReuseDayCell(
                            date = date,
                            enabled = date !in blocked && kpknDayEnabled(date, minDate, maxDate),
                            isBlocked = date in blocked,
                            selected = date in selected,
                            isToday = date == today,
                            onToggle = { onToggle(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

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

/** Un día: número en un círculo de 40 dp. Elegido = tinta con el número en negro; hoy = anillo fino; origen = apagado con raya. */
@Composable
private fun ReuseDayCell(
    date: LocalDate,
    enabled: Boolean,
    isBlocked: Boolean,
    selected: Boolean,
    isToday: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
        append(date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", EsLocale)))
        if (isToday) append(", hoy")
        if (isBlocked) append(", día de origen")
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = description }
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
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
                .then(if (isToday && !selected) Modifier.border(1.dp, WizardColors.textMuted, CircleShape) else Modifier)
                // El día de origen no solo se apaga: lleva un filete de contorno, para no depender del color.
                .then(if (isBlocked) Modifier.border(1.dp, WizardColors.textFaint, CircleShape) else Modifier),
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

private fun monthTitle(month: YearMonth): String =
    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", EsLocale)).replaceFirstChar { it.uppercase(EsLocale) }
