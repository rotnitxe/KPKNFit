package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.SetupWeighIn
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/*
 * Pesajes del paso «¿Quieres añadir tus últimos pesajes?» dibujados en la página: tendencia, lista sin caja, «Añadir»
 * y un editor en el mismo sitio. Sin Material (salvo el texto) y sin tarjetas: símbolos y filetes sobre el negro.
 */

/** Tendencia y báscula: 132 dp de alto a todo el ancho. */
private val TREND_HEIGHT = 132.dp
private val SCALE_SIZE = 72.dp
private val RULER_HEIGHT = 220.dp
private const val TREND_POINTS = 8
private const val DAY_COUNT = 14
private const val SCALE_NEEDLE_START_DEG = 40f
private const val FAT_START_PERCENT = 20.0
private const val MUSCLE_START_PERCENT = 40.0
private const val EDITOR_MOTION_MS = 220

/** Rango de la regla de músculo (%): de 20 a 70. */
internal val MUSCLE_PERCENT_RANGE: IntRange = 20..70

internal const val WEIGHINS_BODY_TAG = "setup-weighins-body"
internal const val WEIGHINS_TREND_TAG = "setup-weighins-trend"
internal const val WEIGHIN_ADD_TAG = "setup-weighin-add"
internal const val WEIGHIN_EDITOR_TAG = "setup-weighin-editor"
internal const val WEIGHIN_OTHER_DATE_TAG = "setup-weighin-other-date"
internal const val WEIGHIN_FAT_TOGGLE_TAG = "setup-weighin-fat-toggle"
internal const val WEIGHIN_MUSCLE_TOGGLE_TAG = "setup-weighin-muscle-toggle"
internal const val WEIGHIN_FAT_RULER_TAG = "setup-weighin-fat-ruler"
internal const val WEIGHIN_MUSCLE_RULER_TAG = "setup-weighin-muscle-ruler"
internal const val WEIGHIN_SAVE_TAG = "setup-weighin-save"
internal const val WEIGHIN_CANCEL_TAG = "setup-weighin-cancel"

internal fun weighInRowTag(dateIso: String): String = "setup-weighin-row-$dateIso"
internal fun weighInDeleteTag(dateIso: String): String = "setup-weighin-delete-$dateIso"
internal fun weighInDayTag(date: LocalDate): String = "setup-weighin-day-$date"

/** Fecha ISO de un pesaje; null si no se puede leer. */
internal fun weighInDateOf(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

/** Fechas del pesaje en español, sin depender del idioma del sistema. */
internal object WeighInDates {
    private val WEEKDAYS_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
    private val MONTHS_FULL = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )

    /** «Hoy», «Ayer» o «Mié 9 oct»: la fecha de cada fila. */
    fun shortLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> "${WEEKDAYS_SHORT[date.dayOfWeek.value - 1]} ${dayMonth(date)}"
    }

    /** «Hoy», «Ayer» o «Lun 6»: cada casilla de la franja de días. */
    fun dayChipLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> "${WEEKDAYS_SHORT[date.dayOfWeek.value - 1]} ${date.dayOfMonth}"
    }

    /** «9 oct». */
    fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS_SHORT[date.monthValue - 1]}"

    /** «9 de octubre»: para el nombre accesible del botón de eliminar. */
    fun longLabel(date: LocalDate): String = "${date.dayOfMonth} de ${MONTHS_FULL[date.monthValue - 1]}"

    /** «octubre»: el nombre del mes del calendario. */
    fun monthName(month: YearMonth): String = MONTHS_FULL[month.monthValue - 1]

    /** Los [count] días desde hoy hacia atrás. */
    fun recentDays(today: LocalDate, count: Int = DAY_COUNT): List<LocalDate> =
        (0 until count).map { today.minusDays(it.toLong()) }

    /** Primer día libre desde hoy hacia atrás, dentro de los últimos 14; si están todos usados, hoy. */
    fun firstFreeDay(today: LocalDate, used: Set<LocalDate>): LocalDate =
        recentDays(today).firstOrNull { it !in used } ?: today

    /** Un pesaje es válido con un día que no es futuro y no tiene otro pesaje (el que se edita no cuenta). */
    fun isValidDate(date: LocalDate, today: LocalDate, used: Set<LocalDate>): Boolean =
        !date.isAfter(today) && date !in used

    /** Se puede elegir en el calendario: válido y como mucho de hace un año. */
    fun isSelectable(date: LocalDate, today: LocalDate, used: Set<LocalDate>): Boolean =
        isValidDate(date, today, used) && !date.isBefore(today.minusYears(1))
}

/** Los últimos [TREND_POINTS] pesajes por fecha, del más antiguo al más reciente. */
internal fun weighInTrendRows(rows: List<SetupWeighIn>): List<SetupWeighIn> =
    rows.filter { weighInDateOf(it.dateIso) != null }.sortedBy { it.dateIso }.takeLast(TREND_POINTS)

/**
 * Tendencia de los pesajes: línea fina de tinta entre los puntos por fecha (hasta 8); el más reciente, en verde y con su
 * peso encima. Con uno, su punto centrado; sin ninguno, la báscula de baño con «Aún no hay pesajes.».
 */
@Composable
internal fun WizardWeighInTrend(
    rows: List<SetupWeighIn>,
    unit: WizardMassUnit,
    modifier: Modifier = Modifier,
) {
    val points = remember(rows) { weighInTrendRows(rows) }
    if (points.isEmpty()) {
        WizardBathScale(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = WizardTypography.note
    val days = remember(points) { points.map { weighInDateOf(it.dateIso)?.toEpochDay() ?: 0L } }
    val latest = points.last()
    val latestLabel = remember(latest.weightKg, unit) { WizardWeightScale.formatWithUnit(latest.weightKg, unit) }
    val latestText = remember(latestLabel) { measurer.measure(latestLabel, labelStyle) }
    val firstDate = remember(points) { weighInDateOf(points.first().dateIso)?.let { WeighInDates.dayMonth(it) }.orEmpty() }
    val lastDate = remember(points) { weighInDateOf(latest.dateIso)?.let { WeighInDates.dayMonth(it) }.orEmpty() }
    val firstText = remember(firstDate) { measurer.measure(firstDate, labelStyle) }
    val lastText = remember(lastDate) { measurer.measure(lastDate, labelStyle) }
    val summary = remember(points.size, latestLabel) {
        "Tendencia de tus pesajes: ${points.size} ${if (points.size == 1) "pesaje" else "pesajes"}, el último de $latestLabel."
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(TREND_HEIGHT)
            .testTag(WEIGHINS_TREND_TAG)
            .semantics { contentDescription = summary },
    ) {
        val inset = 14.dp.toPx()
        val top = 34.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val left = inset
        val right = size.width - inset
        val minKg = points.minOf { it.weightKg }
        val maxKg = points.maxOf { it.weightKg }
        val firstDay = days.first()
        val lastDay = days.last()

        fun xOf(index: Int): Float =
            if (points.size == 1 || lastDay == firstDay) {
                (left + right) / 2f
            } else {
                left + (days[index] - firstDay).toFloat() / (lastDay - firstDay).toFloat() * (right - left)
            }

        fun yOf(index: Int): Float {
            if (points.size == 1 || maxKg - minKg < 1e-9) return (top + bottom) / 2f
            val fraction = ((points[index].weightKg - minKg) / (maxKg - minKg)).toFloat()
            return bottom - fraction * (bottom - top)
        }

        val lineWidth = 1.5.dp.toPx()
        for (i in 1 until points.size) {
            drawLine(
                color = WizardColors.text.copy(alpha = 0.8f),
                start = Offset(xOf(i - 1), yOf(i - 1)),
                end = Offset(xOf(i), yOf(i)),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
            )
        }
        points.indices.forEach { i ->
            val isLatest = i == points.lastIndex
            drawCircle(
                color = if (isLatest) SymbolPalette.ok else WizardColors.text,
                radius = if (isLatest) 4.5.dp.toPx() else 3.dp.toPx(),
                center = Offset(xOf(i), yOf(i)),
            )
        }
        val latestX = xOf(points.lastIndex)
        val latestY = yOf(points.lastIndex)
        drawText(
            textLayoutResult = latestText,
            color = WizardColors.text,
            topLeft = Offset(
                (latestX - latestText.size.width / 2f).coerceIn(left, right - latestText.size.width),
                // Margen sobre el punto mayor que la pendiente de la última línea, para que no la cruce el texto.
                latestY - 16.dp.toPx() - latestText.size.height,
            ),
        )
        if (points.size > 1) {
            val labelTop = size.height - firstText.size.height
            drawText(textLayoutResult = firstText, color = WizardColors.textMuted, topLeft = Offset(left, labelTop))
            drawText(
                textLayoutResult = lastText,
                color = WizardColors.textMuted,
                topLeft = Offset(right - lastText.size.width, labelTop),
            )
        }
    }
}

/** Báscula de baño dibujada en Canvas: la aguja oscila suave y se detiene; con movimiento reducido, quieta. */
@Composable
private fun WizardBathScale(modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val needle = remember { Animatable(SCALE_NEEDLE_START_DEG) }
    LaunchedEffect(reduced) {
        if (reduced) {
            needle.snapTo(0f)
        } else {
            needle.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 170f))
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(TREND_HEIGHT)
            .testTag(WEIGHINS_TREND_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(SCALE_SIZE)) { drawBathScale(needle.value) }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Aún no hay pesajes.",
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** Dibujo de la báscula en un cuadrado de 72 dp (1 unidad de diseño = 1 dp). */
private fun DrawScope.drawBathScale(deflectionDeg: Float) {
    val u = size.minDimension / 72f
    val ink = WizardColors.textMuted
    val lineWidth = 2.dp.toPx()
    val thin = 1.5.dp.toPx()
    val center = Offset(36f * u, 34f * u)
    fun point(radiusDp: Double, angleDeg: Double): Offset = Offset(
        center.x + (radiusDp * u * cos(Math.toRadians(angleDeg))).toFloat(),
        center.y + (radiusDp * u * sin(Math.toRadians(angleDeg))).toFloat(),
    )
    // Plato y patas.
    drawRoundRect(
        color = ink,
        topLeft = Offset(6f * u, 10f * u),
        size = Size(60f * u, 52f * u),
        cornerRadius = CornerRadius(12f * u),
        style = Stroke(lineWidth),
    )
    drawLine(ink, Offset(18f * u, 62f * u), Offset(18f * u, 68f * u), lineWidth)
    drawLine(ink, Offset(54f * u, 62f * u), Offset(54f * u, 68f * u), lineWidth)
    // Esfera con cinco marcas en el arco superior.
    drawCircle(color = ink, radius = 17f * u, center = center, style = Stroke(thin))
    for (k in 0..4) {
        val angle = -150.0 + k * 30.0
        drawLine(ink, point(12.5, angle), point(17.0, angle), thin)
    }
    // Aguja (en reposo apunta al cero, arriba) y eje.
    drawLine(
        color = WizardColors.text,
        start = center,
        end = point(14.0, -90.0 + deflectionDeg),
        strokeWidth = lineWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(color = WizardColors.text, radius = 2.5.dp.toPx(), center = center)
}

/** Una fila por pesaje, sin caja: fecha corta a la izquierda, peso y % en el centro y «×» a la derecha (48 dp). */
@Composable
internal fun WizardWeighInRow(
    row: SetupWeighIn,
    unit: WizardMassUnit,
    today: LocalDate,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = weighInDateOf(row.dateIso)
    val dateLabel = date?.let { WeighInDates.shortLabel(it, today) } ?: row.dateIso
    val longLabel = date?.let { WeighInDates.longLabel(it) } ?: row.dateIso
    val composition = listOfNotNull(
        row.bodyFatPercent?.let { "${wizardBodyFatNumber(it)} % grasa" },
        row.muscleMassPercent?.let { "${wizardBodyFatNumber(it)} % músculo" },
    ).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .testTag(weighInRowTag(row.dateIso))
            .clickable(role = Role.Button, onClickLabel = "Editar pesaje", onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dateLabel,
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            modifier = Modifier.width(96.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = WizardWeightScale.formatWithUnit(row.weightKg, unit),
                style = WizardTypography.controlValue,
                color = WizardColors.text,
            )
            if (composition.isNotEmpty()) {
                Text(text = composition, style = WizardTypography.note, color = WizardColors.textMuted)
            }
        }
        Box(
            modifier = Modifier
                .size(WizardSpacing.touchTarget)
                .testTag(weighInDeleteTag(row.dateIso))
                .semantics {
                    contentDescription = "Eliminar el pesaje del $longLabel"
                    role = Role.Button
                }
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", style = WizardTypography.controlValue, color = WizardColors.textMuted)
        }
    }
}

/** «Añadir pesaje»: círculo de contorno de 48 dp con un «+» y la palabra al lado. */
@Composable
internal fun WizardWeighInAddAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = WizardSpacing.touchTarget)
            .testTag(WEIGHIN_ADD_TAG)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(WizardSpacing.touchTarget)
                .border(1.dp, WizardColors.textMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", style = WizardTypography.controlValue, color = WizardColors.text)
        }
        Spacer(Modifier.width(12.dp))
        Text(text = "Añadir pesaje", style = WizardTypography.cardSubtitle, color = WizardColors.text)
    }
}

/**
 * Editor de un pesaje, en la página (donde estaba «Añadir pesaje»): fecha (franja de 14 días o calendario), peso con la
 * regla en la unidad del alta y, opcionales, el % de grasa y el % de músculo. Nada se guarda hasta «Guardar pesaje».
 */
@Composable
internal fun WizardWeighInEditor(
    initial: SetupWeighIn?,
    rows: List<SetupWeighIn>,
    unit: WizardMassUnit,
    anchorKg: Double?,
    today: LocalDate,
    onSave: (SetupWeighIn) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val used = remember(rows, initial) {
        rows.filter { it.id != initial?.id }.mapNotNull { weighInDateOf(it.dateIso) }.toSet()
    }
    val recent = remember(today) { WeighInDates.recentDays(today) }
    var date by remember {
        mutableStateOf(initial?.let { weighInDateOf(it.dateIso) } ?: WeighInDates.firstFreeDay(today, used))
    }
    var calendarOpen by remember { mutableStateOf(false) }
    var weightKg by remember { mutableStateOf(initial?.weightKg) }
    var fatOpen by remember { mutableStateOf(initial?.bodyFatPercent != null) }
    var fatPercent by remember { mutableStateOf(initial?.bodyFatPercent) }
    var muscleOpen by remember { mutableStateOf(initial?.muscleMassPercent != null) }
    var musclePercent by remember { mutableStateOf(initial?.muscleMassPercent) }

    val dateValid = WeighInDates.isValidDate(date, today, used)
    val weight = weightKg
    val weightValid = weight != null && weight.isFinite() &&
        weight in WizardWeightScale.MIN_KG..WizardWeightScale.MAX_KG
    val valid = dateValid && weightValid
    val dateError = when {
        date in used -> "Ya hay un pesaje ese día."
        !dateValid -> "La fecha no puede ser futura."
        else -> null
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag(WEIGHIN_EDITOR_TAG),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Fecha: franja de días o calendario de mes.
        if (calendarOpen) {
            WizardWeighInCalendar(
                selected = date,
                today = today,
                used = used,
                onPick = {
                    date = it
                    calendarOpen = false
                },
            )
        } else {
            WizardWeighInDayRow(
                days = recent,
                selected = date,
                today = today,
                used = used,
                onPick = { date = it },
            )
        }
        TextButton(
            onClick = { calendarOpen = !calendarOpen },
            modifier = Modifier.defaultMinSize(minHeight = WizardSpacing.touchTarget).testTag(WEIGHIN_OTHER_DATE_TAG),
        ) {
            val label = when {
                calendarOpen -> "Volver a los últimos días"
                date in recent -> "Otra fecha"
                else -> "Otra fecha · ${WeighInDates.dayMonth(date)}"
            }
            Text(text = label, style = WizardTypography.cardSubtitle, color = WizardColors.text)
        }

        // Peso: la regla en la unidad del alta. Solo cuenta lo que se mueve o se toca.
        WizardWeightRule(
            unit = unit,
            valueKg = initial?.weightKg ?: anchorKg,
            onValueChange = { weightKg = it },
        )
        if (weight == null) {
            Text(
                text = "Desliza la regla para elegir el peso.",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
            )
        }

        // Porcentajes opcionales: cada uno se despliega con su regla y se quita con «Quitar».
        WizardWeighInPercentToggle(
            open = fatOpen,
            addLabel = "Añadir % de grasa",
            tag = WEIGHIN_FAT_TOGGLE_TAG,
            onOpen = { fatOpen = true },
            onRemove = {
                fatOpen = false
                fatPercent = null
            },
        )
        if (fatOpen) {
            WizardBodyFatRuler(
                percent = fatPercent ?: FAT_START_PERCENT,
                declared = fatPercent != null,
                onPercentChange = { fatPercent = it.toDouble() },
                modifier = Modifier.fillMaxWidth().height(RULER_HEIGHT).testTag(WEIGHIN_FAT_RULER_TAG),
            )
        }
        WizardWeighInPercentToggle(
            open = muscleOpen,
            addLabel = "Añadir % de músculo",
            tag = WEIGHIN_MUSCLE_TOGGLE_TAG,
            onOpen = { muscleOpen = true },
            onRemove = {
                muscleOpen = false
                musclePercent = null
            },
        )
        if (muscleOpen) {
            WizardBodyFatRuler(
                percent = musclePercent ?: MUSCLE_START_PERCENT,
                declared = musclePercent != null,
                onPercentChange = { musclePercent = it.toDouble() },
                modifier = Modifier.fillMaxWidth().height(RULER_HEIGHT).testTag(WEIGHIN_MUSCLE_RULER_TAG),
                range = MUSCLE_PERCENT_RANGE,
            )
        }

        dateError?.let { Text(text = it, style = WizardTypography.caption, color = WizardColors.danger) }

        // Acciones: cancelar y guardar (el acento verde solo cuando se puede guardar).
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.defaultMinSize(minHeight = WizardSpacing.touchTarget).testTag(WEIGHIN_CANCEL_TAG),
            ) {
                Text(text = "Cancelar", style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = valid,
                onClick = {
                    val kg = weightKg
                    if (valid && kg != null) {
                        onSave(
                            SetupWeighIn(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                dateIso = date.toString(),
                                weightKg = kg,
                                bodyFatPercent = fatPercent.takeIf { fatOpen },
                                muscleMassPercent = musclePercent.takeIf { muscleOpen },
                            ),
                        )
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = WizardSpacing.touchTarget).testTag(WEIGHIN_SAVE_TAG),
            ) {
                Text(
                    text = "Guardar pesaje",
                    style = WizardTypography.cardSubtitle,
                    color = if (valid) WizardColors.done else WizardColors.textFaint,
                )
            }
        }
    }
}

/** Acción de texto para desplegar una regla de porcentaje o quitarla («Quitar»). */
@Composable
private fun WizardWeighInPercentToggle(
    open: Boolean,
    addLabel: String,
    tag: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    TextButton(
        onClick = if (open) onRemove else onOpen,
        modifier = Modifier.defaultMinSize(minHeight = WizardSpacing.touchTarget).testTag(tag),
    ) {
        Text(
            text = if (open) "Quitar" else addLabel,
            style = WizardTypography.cardSubtitle,
            color = WizardColors.text,
        )
    }
}

/** Franja de los últimos 14 días: casillas de 48 dp; los días con pesaje salen apagados y no se pueden elegir. */
@Composable
private fun WizardWeighInDayRow(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    used: Set<LocalDate>,
    onPick: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { day ->
            val taken = day in used
            val isSelected = day == selected
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = WizardSpacing.touchTarget, minHeight = WizardSpacing.touchTarget)
                    .testTag(weighInDayTag(day))
                    .semantics {
                        role = Role.Button
                        stateDescription = when {
                            taken -> "Ya hay un pesaje ese día"
                            isSelected -> "Seleccionado"
                            else -> "Libre"
                        }
                        if (taken) disabled()
                    }
                    .clickable(enabled = !taken) { onPick(day) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = WeighInDates.dayChipLabel(day, today),
                    style = WizardTypography.note,
                    color = when {
                        taken -> WizardColors.textFaint
                        isSelected -> WizardColors.text
                        else -> WizardColors.textMuted
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Calendario de un mes propio (sin Material): días pasados de hasta un año; los no elegibles salen apagados. */
@Composable
private fun WizardWeighInCalendar(
    selected: LocalDate,
    today: LocalDate,
    used: Set<LocalDate>,
    onPick: (LocalDate) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val earliest = remember(today) { YearMonth.from(today.minusYears(1)) }
    val latest = remember(today) { YearMonth.from(today) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(description = "Mes anterior", enabled = month > earliest, forward = false) {
                month = month.minusMonths(1)
            }
            Text(
                text = "${WeighInDates.monthName(month)} de ${month.year}",
                style = WizardTypography.cardSubtitle,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(description = "Mes siguiente", enabled = month < latest, forward = true) {
                month = month.plusMonths(1)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { letter ->
                Text(
                    text = letter,
                    style = WizardTypography.note,
                    color = WizardColors.textFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val length = month.lengthOfMonth()
        val weeks = (offset + length + 6) / 7
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val day = week * 7 + column - offset + 1
                    if (day !in 1..length) {
                        Spacer(Modifier.weight(1f).height(WizardSpacing.touchTarget))
                        continue
                    }
                    val date = month.atDay(day)
                    val enabled = WeighInDates.isSelectable(date, today, used)
                    val isSelected = date == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(WizardSpacing.touchTarget)
                            .testTag(weighInDayTag(date))
                            .semantics {
                                role = Role.Button
                                stateDescription = if (enabled) "Libre" else "No se puede elegir"
                                if (!enabled) disabled()
                            }
                            .clickable(enabled = enabled) { onPick(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .then(if (isSelected) Modifier.border(1.dp, WizardColors.text, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$day",
                                style = WizardTypography.note,
                                color = when {
                                    !enabled -> WizardColors.textFaint
                                    isSelected -> WizardColors.text
                                    else -> WizardColors.textMuted
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(description: String, enabled: Boolean, forward: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(WizardSpacing.touchTarget)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (forward) "›" else "‹",
            style = WizardTypography.controlValue,
            color = if (enabled) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/**
 * Cuerpo del paso sin Compose de Material en los datos: tendencia, lista, «Añadir pesaje» o el editor en su sitio.
 * Sin estado de borrador: el anfitrión decide cómo guardar ([onSave]) y borrar ([onDelete]).
 */
@Composable
internal fun WizardWeighInsBody(
    rows: List<SetupWeighIn>,
    unit: WizardMassUnit,
    currentKg: Double?,
    today: LocalDate,
    onSave: (SetupWeighIn) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byDate = remember(rows) { rows.sortedByDescending { it.dateIso } }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SetupWeighIn?>(null) }
    val target = editing

    Column(modifier = modifier.fillMaxWidth().testTag(WEIGHINS_BODY_TAG)) {
        WizardWeighInTrend(rows = rows, unit = unit)
        Spacer(Modifier.height(12.dp))
        byDate.forEach { row ->
            WizardWeighInRow(
                row = row,
                unit = unit,
                today = today,
                onOpen = {
                    adding = false
                    editing = row
                },
                onDelete = {
                    if (editing?.id == row.id) editing = null
                    onDelete(row.id)
                },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
        }
        Spacer(Modifier.height(12.dp))
        // El editor aparece donde estaba «Añadir pesaje» (220 ms; con movimiento reducido, de golpe) y sale sin animar.
        val editorOpen = adding || target != null
        val motionMs = if (wizardReducedMotion()) 0 else EDITOR_MOTION_MS
        AnimatedVisibility(
            visible = editorOpen,
            enter = fadeIn(animationSpec = tween(motionMs)) + expandVertically(animationSpec = tween(motionMs)),
            exit = ExitTransition.None,
        ) {
            key(target?.id ?: "nuevo") {
                WizardWeighInEditor(
                    initial = target,
                    rows = rows,
                    unit = unit,
                    anchorKg = byDate.firstOrNull()?.weightKg ?: currentKg,
                    today = today,
                    onSave = { saved ->
                        onSave(saved)
                        adding = false
                        editing = null
                    },
                    onCancel = {
                        adding = false
                        editing = null
                    },
                )
            }
        }
        if (!editorOpen) {
            WizardWeighInAddAction(onClick = { adding = true })
        }
    }
}
