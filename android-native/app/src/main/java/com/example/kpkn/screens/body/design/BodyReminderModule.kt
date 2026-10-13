package com.example.kpkn.screens.body.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Frecuencias con píldora propia, en días. Cualquier otra se lee con «Otra». */
internal val BODY_REMINDER_PRESET_DAYS: List<Int> = listOf(1, 7, 14, 30)

/** Límites, en días, del intervalo que se elige a mano con «Otra». */
internal const val BODY_REMINDER_MIN_DAYS = 1
internal const val BODY_REMINDER_MAX_DAYS = 365

private val SPANISH: Locale = Locale.forLanguageTag("es")

/**
 * Módulo «Recordatorio de pesaje» (BODY_V1 §2.6): el interruptor, la frecuencia en píldoras con «Otra» (y su selector de 1 a 365 días), la
 * hora, que se edita con el reloj, y la próxima fecha. Las píldoras van en fila envolvente porque cinco segmentos no caben sin cortar
 * el texto a 360 dp. Sin estado: [customInterval] dice si se muestra «Otra», y la hora la abre quien llama con [onEditTime].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyReminderModule(
    schedule: MeasurementSchedule,
    customInterval: Boolean,
    onCustomIntervalChange: (Boolean) -> Unit,
    onUpdate: (MeasurementSchedule) -> Unit,
    onEditTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(
            label = "Recordatorio de pesaje",
            action = {
                KpknSwitch(
                    checked = schedule.enabled,
                    onCheckedChange = { on -> onUpdate(schedule.copy(enabled = on)) },
                    modifier = Modifier.semantics { contentDescription = "Recordatorio de pesaje" },
                )
            },
        )
        if (schedule.enabled) {
            Spacer(Modifier.height(14.dp))
            Text(text = "Frecuencia", style = KpknType.caption, color = WizardColors.textMuted)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BODY_REMINDER_PRESET_DAYS.forEach { days ->
                    KpknChip(
                        text = bodyReminderPresetLabel(days),
                        selected = !customInterval && schedule.intervalDays == days,
                        onClick = {
                            onCustomIntervalChange(false)
                            onUpdate(schedule.copy(intervalDays = days))
                        },
                    )
                }
                KpknChip(
                    text = "Otra",
                    selected = customInterval,
                    onClick = { onCustomIntervalChange(true) },
                )
            }
            if (customInterval) {
                Spacer(Modifier.height(12.dp))
                BodyIntervalStepper(
                    days = schedule.intervalDays,
                    onDays = { days -> onUpdate(schedule.copy(intervalDays = days)) },
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            BodyReminderTimeRow(
                hour = schedule.reminderHour,
                minute = schedule.reminderMinute,
                onClick = onEditTime,
            )
            val next = bodyReminderNextLabel(schedule.nextDate, schedule.reminderHour, schedule.reminderMinute)
            if (next != null) {
                Spacer(Modifier.height(6.dp))
                Text(text = "Próxima medición: $next", style = KpknType.caption, color = WizardColors.textMuted)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Activa el recordatorio para elegir cada cuánto y a qué hora.",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}

/** Texto de una píldora de frecuencia: «Cada día» o «7 días». */
internal fun bodyReminderPresetLabel(days: Int): String = if (days == 1) "Cada día" else "$days días"

/** Hora en formato 24 h, «09:05». */
internal fun bodyClockText(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

/**
 * Próxima medición en lenguaje natural, «martes 20 de octubre a las 09:00». «a las» y la hora van unidos con espacios de no separación:
 * al partirse la línea, la hora nunca queda sola. Null si no hay fecha válida.
 */
internal fun bodyReminderNextLabel(nextDate: String?, hour: Int, minute: Int): String? {
    val day = nextDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() } ?: return null
    val weekday = day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH))
    return "$weekday a las ${bodyClockText(hour, minute)}"
}

/** Selector de «Otra»: «Cada 21 días» con − y +, entre 1 y 365 días. */
@Composable
private fun BodyIntervalStepper(
    days: Int,
    onDays: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknIconButton(
            icon = KpknLineIcons.Minus,
            contentDescription = "Quitar un día",
            onClick = { onDays((days - 1).coerceAtLeast(BODY_REMINDER_MIN_DAYS)) },
        )
        Spacer(Modifier.width(8.dp))
        Text(text = "Cada", style = KpknType.bodyStrong, color = WizardColors.textMuted)
        Spacer(Modifier.width(8.dp))
        KpknMetric(value = "$days", unit = if (days == 1) "día" else "días", style = KpknType.metricM)
        Spacer(Modifier.width(8.dp))
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Añadir un día",
            onClick = { onDays((days + 1).coerceAtMost(BODY_REMINDER_MAX_DAYS)) },
        )
    }
}

/** Fila de la hora del recordatorio: toda la fila abre el reloj de [onClick]. Zona táctil de 56 dp. */
@Composable
private fun BodyReminderTimeRow(
    hour: Int,
    minute: Int,
    onClick: () -> Unit,
) {
    val clock = bodyClockText(hour, minute)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Cambiar la hora", onClick = onClick)
            .semantics { contentDescription = "Hora del recordatorio, $clock" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Clock,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = WizardColors.textMuted,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Hora del recordatorio",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
        KpknMetric(value = clock, style = KpknType.metricM)
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = WizardColors.textFaint,
        )
    }
}
