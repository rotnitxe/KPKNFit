package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.nutrition.MealTime
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.kpknMonthCells
import kotlinx.coroutines.flow.filter
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Los atajos de arriba de la hoja de fecha (lienzo s-date): «Ahora», «Hace 1 h», «Hoy al mediodía» y «Ayer». */
enum class LoggerDateQuick(val label: String) {
    NOW("Ahora"),
    HOUR_AGO("Hace 1 h"),
    NOON("Hoy al mediodía"),
    YESTERDAY("Ayer"),
}

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * El día y la hora («HH:mm») que fija un atajo: «Ahora» es hoy a la hora actual; «Hace 1 h», una hora antes (puede caer en ayer);
 * «Hoy al mediodía», hoy a las 12:00; «Ayer», ayer a la hora actual.
 */
fun loggerDateQuickPick(quick: LoggerDateQuick, today: LocalDate, now: LocalTime): Pair<LocalDate, String> = when (quick) {
    LoggerDateQuick.NOW -> today to now.format(ClockFormat)
    LoggerDateQuick.HOUR_AGO -> today.atTime(now).minusHours(1).let { it.toLocalDate() to it.toLocalTime().format(ClockFormat) }
    LoggerDateQuick.NOON -> today to "12:00"
    LoggerDateQuick.YESTERDAY -> today.minusDays(1) to now.format(ClockFormat)
}

/** El atajo que coincide exactamente (mismo día, mismos minutos) con [date] y [time], o `null` si ninguno. */
fun loggerDateQuickSelected(date: LocalDate, time: String, today: LocalDate, now: LocalTime): LoggerDateQuick? =
    LoggerDateQuick.entries.firstOrNull { loggerDateQuickPick(it, today, now) == date to time }

/** Alto de una fila de la rueda de la hora (la cifra es Syne 40). */
private val WheelItemHeight = 52.dp

private val SpanishLocale = Locale.forLanguageTag("es")

/** Dos dígitos, sin depender de la configuración regional. */
private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

/**
 * Hoja de «¿Cuándo fue?» (lienzo s-date): atajos, calendario sin días futuros y la hora en dos ruedas. Elegir un día no cierra la hoja ni
 * toca la hora (F-S14); «Listo» entrega el día y la hora («HH:mm») con [onDone].
 *
 * Decisión: el lienzo no dibuja cabecera de mes, pero sin ella no se podría ir a meses anteriores; encima de la cuadrícula hay una fila
 * mínima con «octubre 2026» y las flechas ‹ › (› apagada en el mes de hoy).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoggerDateSheet(
    selected: LocalDate,
    selectedTime: String,
    today: LocalDate,
    now: LocalTime,
    onDone: (LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember(selectedTime, now) { MealTime.parse(selectedTime) ?: now }
    var date by remember(selected) { mutableStateOf(selected) }
    var hour by remember(selectedTime) { mutableIntStateOf(start.hour) }
    var minute by remember(selectedTime) { mutableIntStateOf(start.minute) }
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    val clock = "${twoDigits(hour)}:${twoDigits(minute)}"
    val currentQuick = loggerDateQuickSelected(date, clock, today, now)
    val lastMonth = YearMonth.from(today)

    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            LoggerSheetTitle(title = "¿Cuándo fue?")
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LoggerDateQuick.entries.forEach { quick ->
                    LoggerChip(
                        text = quick.label,
                        selected = quick == currentQuick,
                        height = 36.dp,
                        onClick = {
                            val (pickedDay, pickedTime) = loggerDateQuickPick(quick, today, now)
                            val parsed = MealTime.parse(pickedTime) ?: now
                            date = pickedDay
                            hour = parsed.hour
                            minute = parsed.minute
                            month = YearMonth.from(pickedDay)
                        },
                    )
                }
            }
            DateMonthHeader(
                month = month,
                canGoForward = month < lastMonth,
                onBack = { month = month.minusMonths(1) },
                onForward = { month = month.plusMonths(1) },
            )
            DateGrid(month = month, selected = date, today = today, onPick = { date = it })
            LoggerSheetGroup("Hora")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeWheel(count = 24, value = hour, label = "Hora", onChange = { hour = it })
                Text(
                    text = ":",
                    style = LoggerText.figure(40, FontWeight.ExtraBold).copy(letterSpacing = (-2).sp),
                    color = LoggerColors.ink,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                TimeWheel(count = 60, value = minute, label = "Minutos", onChange = { minute = it })
            }
            LoggerPill(
                text = "Listo",
                onClick = { onDone(date, clock) },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
        }
    }
}

/** Fila mínima sobre la cuadrícula: ‹ «octubre 2026» ›. */
@Composable
private fun DateMonthHeader(month: YearMonth, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoggerIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Mes anterior", onClick = onBack, size = 32.dp, iconSize = 18.dp)
        Text(
            text = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", SpanishLocale)),
            style = LoggerText.cap,
            color = LoggerColors.faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        LoggerIconButton(
            icon = KpknLineIcons.ChevronRight,
            contentDescription = "Mes siguiente",
            onClick = { if (canGoForward) onForward() },
            size = 32.dp,
            iconSize = 18.dp,
            tint = if (canGoForward) LoggerColors.muted else LoggerColors.dim,
            modifier = Modifier.semantics { if (!canGoForward) disabled() },
        )
    }
}

/** La cuadrícula del mes: L M X J V S D en `cap` y los días en celdas redondas de 36 dp con 4 dp de hueco. */
@Composable
private fun DateGrid(month: YearMonth, selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val cells = remember(month) { kpknMonthCells(month) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { letter ->
                Text(
                    text = letter,
                    style = LoggerText.cap,
                    color = LoggerColors.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "" },
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(36.dp))
                    } else {
                        DateCell(day = day, selected = day == selected, future = day.isAfter(today), onPick = { onPick(day) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Un día: círculo de 36 dp; el elegido, en tinta con número `#111` SemiBold; los futuros, a blanco 13 % y sin tocar. */
@Composable
private fun DateCell(day: LocalDate, selected: Boolean, future: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val description = day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SpanishLocale))
    Box(
        modifier = modifier
            .height(36.dp)
            .semantics { contentDescription = description; if (future) disabled() }
            .selectable(selected = selected, enabled = !future, role = Role.Button, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (selected) LoggerColors.ink else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = LoggerText.body.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFeatureSettings = "'tnum' 1",
                ),
                color = when {
                    selected -> LoggerColors.onInk
                    future -> Color.White.copy(alpha = 0.133f)
                    else -> LoggerColors.ink
                },
                maxLines = 1,
            )
        }
    }
}

/** Se traga lo que sobra del gesto: arrastrar la rueda más allá de sus extremos no mueve ni cierra la hoja. */
private object ConsumeRemainingScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/**
 * Una rueda de [count] valores (0 hasta count−1): tres filas visibles con la del centro en tinta y las vecinas atenuadas, cifras Syne 40
 * (`.big`) con dos dígitos. Al soltar encaja en la fila más cercana y avisa con [onChange]; si [value] cambia desde fuera, se desplaza
 * hasta él.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimeWheel(count: Int, value: Int, label: String, onChange: (Int) -> Unit) {
    val itemPx = with(LocalDensity.current) { WheelItemHeight.roundToPx() }
    val state = rememberScrollState(initial = value.coerceIn(0, count - 1) * itemPx)
    val centered by remember(state, itemPx, count) {
        derivedStateOf { (state.value.toFloat() / itemPx).roundToInt().coerceIn(0, count - 1) }
    }
    LaunchedEffect(state, itemPx, count) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect {
            val target = (state.value.toFloat() / itemPx).roundToInt().coerceIn(0, count - 1)
            if (state.value != target * itemPx) state.animateScrollTo(target * itemPx, tween(120))
            onChange(target)
        }
    }
    LaunchedEffect(value) {
        val target = value.coerceIn(0, count - 1)
        if (!state.isScrollInProgress && centered != target) state.animateScrollTo(target * itemPx, tween(200))
    }
    Column(
        modifier = Modifier
            .width(76.dp)
            .height(WheelItemHeight * 3)
            .semantics { contentDescription = label; stateDescription = twoDigits(centered) }
            .nestedScroll(ConsumeRemainingScroll)
            .verticalScroll(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(WheelItemHeight))
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .height(WheelItemHeight)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (index == centered) 1f else 0.3f }
                    .clickable(role = Role.Button, onClickLabel = "Elegir ${twoDigits(index)}") { onChange(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = twoDigits(index),
                    style = LoggerText.figure(40, FontWeight.ExtraBold).copy(letterSpacing = (-2).sp),
                    color = LoggerColors.ink,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(WheelItemHeight))
    }
}
