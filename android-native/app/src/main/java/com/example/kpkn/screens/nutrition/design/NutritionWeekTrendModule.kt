package com.example.kpkn.screens.nutrition.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.TrendPoint
import com.example.kpkn.domain.nutrition.NutritionHistorySeries
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.time.LocalDate
import kotlin.math.round

/** Un día de «Últimos 7 días»: sus kcal registradas (null = sin registros), su banda mínima–máxima y su meta de kcal (null = sin meta). */
data class NutritionWeekBar(
    val date: String,
    val kcal: Int?,
    val kcalMin: Int? = null,
    val kcalMax: Int? = null,
    val goal: Int? = null,
    val isToday: Boolean = false,
) {
    /** La banda tiene ancho: el día lleva su bigote mínimo–máximo. */
    val hasRange: Boolean get() = kcal != null && kcalMin != null && kcalMax != null && kcalMin < kcalMax
}

/**
 * Une la tendencia de 7 días (kcal y meta por fecha) con la serie de historial (la banda mínima–máxima de cada fecha). Un día sin
 * registros no tiene kcal: nunca se pinta como un cero.
 */
internal fun buildNutritionWeekBars(
    trend: List<TrendPoint>,
    series: NutritionHistorySeries,
    today: String,
): List<NutritionWeekBar> {
    val bands = series.points.associateBy { it.date.toString() }
    return trend.map { point ->
        val band = bands[point.date]
        val registered = point.hasData
        NutritionWeekBar(
            date = point.date,
            kcal = if (registered) round(point.calories).toInt() else null,
            kcalMin = if (registered) band?.intakeCaloriesMin?.let { round(it).toInt() } else null,
            kcalMax = if (registered) band?.intakeCaloriesMax?.let { round(it).toInt() } else null,
            goal = point.goal?.let { round(it).toInt() },
            isToday = point.date == today,
        )
    }
}

/** La línea de abajo: «Media de los días registrados: 2.050 kcal · 5 de 7 días»; sin días, «Aún no hay días registrados». */
internal fun weekAverageLine(bars: List<NutritionWeekBar>): String {
    val registered = bars.mapNotNull { it.kcal }
    if (registered.isEmpty()) return "Aún no hay días registrados"
    val average = round(registered.average()).toInt()
    // Espacios no separables en «5 de 7 días»: el texto no puede partirse entre el número y su unidad.
    return "Media de los días registrados: ${formatKcalEs(average)} kcal · ${registered.size} de ${bars.size} días"
}

/** La descripción de accesibilidad del gráfico: «Últimos 7 días: media 2.050 kcal, 5 días registrados». */
internal fun weekSummaryDescription(bars: List<NutritionWeekBar>): String {
    val registered = bars.mapNotNull { it.kcal }
    if (registered.isEmpty()) return "Últimos 7 días: sin días registrados"
    val average = round(registered.average()).toInt()
    val days = if (registered.size == 1) "1 día registrado" else "${registered.size} días registrados"
    return "Últimos 7 días: media ${formatKcalEs(average)} kcal, $days"
}

/** Clave de la memoria de la primera vez: la barra crece solo la primera vez que se ve en el proceso. */
private const val NUTRITION_TREND_KEY = "nutrition-week-trend"

/** Altura de la zona de barras y ancho de cada barra. */
private val BarAreaHeight = 96.dp
private val BarWidth = 16.dp

/**
 * «Últimos 7 días» (N2, NUTRITION_V1 §2.7): siete barras diarias con la meta como línea discontinua. Hoy va en tinta y el resto en energía
 * al 70 %; un día con banda incierta lleva su bigote mínimo–máximo y un día sin registros, solo un punto en `textFaint`. Tocar una barra
 * elige ese día y lo marca con un anillo. Debajo, la media de los días registrados y, si hay historial, «Ver historial». La barra crece la
 * primera vez (500 ms); con movimiento reducido, no.
 */
@Composable
fun NutritionWeekTrendModule(
    bars: List<NutritionWeekBar>,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val firstSeen = remember { NutritionFigureMemory.firstTime(NUTRITION_TREND_KEY) }
    var grown by remember { mutableStateOf(!firstSeen) }
    LaunchedEffect(Unit) { grown = true }
    val spec: AnimationSpec<Float> = if (reduced) snap() else tween(durationMillis = 500, easing = FastOutSlowInEasing)
    val growth by animateFloatAsState(targetValue = if (grown) 1f else 0f, animationSpec = spec, label = "nutritionWeekGrowth")
    // Una sola escala para los siete días: el máximo de kcal, bigotes y metas, con un 15 % de aire arriba.
    val peak = bars.maxOfOrNull { maxOf(it.kcal ?: 0, it.kcalMax ?: 0, it.goal ?: 0) } ?: 0
    val scale = (peak * 1.15).coerceAtLeast(1.0)
    KpknModule(modifier = modifier) {
        if (onOpenHistory != null) {
            KpknModuleHeader(label = "Últimos 7 días", action = { NutritionHistoryAction(onOpen = onOpenHistory) })
        } else {
            KpknModuleHeader(label = "Últimos 7 días")
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = weekSummaryDescription(bars) },
            verticalAlignment = Alignment.Top,
        ) {
            bars.forEach { bar ->
                NutritionWeekBarColumn(
                    bar = bar,
                    selected = bar.date == selectedDate,
                    growth = growth,
                    scale = scale,
                    onSelect = { onSelectDate(bar.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(text = weekAverageLine(bars), style = KpknType.caption, color = WizardColors.textMuted)
    }
}

@Composable
private fun NutritionHistoryAction(onOpen: () -> Unit) {
    KpknTextAction(
        text = "Ver historial",
        onClick = onOpen,
        modifier = Modifier.tourAnchor(TourAnchors.NUTRITION_HISTORIAL),
    )
}

/**
 * Una columna del gráfico, de arriba abajo: la barra con su meta y su banda y, debajo, la letra del día (con anillo si es el elegido y
 * no es hoy). Es un `Role.Tab`; la zona táctil mide 48 dp de ancho aunque la columna sea algo más estrecha.
 */
@Composable
private fun NutritionWeekBarColumn(
    bar: NutritionWeekBar,
    selected: Boolean,
    growth: Float,
    scale: Double,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val date = remember(bar.date) { runCatching { LocalDate.parse(bar.date) }.getOrNull() }
    val interaction = remember { MutableInteractionSource() }
    val description = weekBarDescription(bar, date)
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .requiredWidth(48.dp)
                .testTag("nutrition-week-bar-${bar.date}")
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Tab,
                    onClick = onSelect,
                )
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(BarAreaHeight)) {
                drawWeekBar(bar = bar, growth = growth, scale = scale)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (selected && !bar.isToday) Modifier.border(1.5.dp, WizardColors.text, CircleShape) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date?.let { dayLetter(it) }.orEmpty(),
                    style = KpknType.caption,
                    color = if (bar.isToday) WizardColors.text else WizardColors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Dibuja una columna: la meta como línea discontinua, la barra desde la base, el bigote de la banda incierta y, si no hay registros, un
 * punto en la base (nunca una barra de cero). Todo se mide con [scale] (kcal que caben en toda la altura).
 */
private fun DrawScope.drawWeekBar(bar: NutritionWeekBar, growth: Float, scale: Double) {
    val h = size.height
    val cx = size.width / 2f
    val ink = if (bar.isToday) WizardColors.text else SymbolPalette.energia
    fun heightOf(kcal: Int): Float = (kcal / scale * h).toFloat()

    bar.goal?.let { goal ->
        val y = h - heightOf(goal)
        drawLine(
            color = WizardColors.textFaint,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }

    val kcal = bar.kcal
    if (kcal == null || kcal <= 0) {
        // Sin registros: un punto en la base, en `textFaint`; nunca una barra de cero que parezca un dato.
        drawCircle(color = WizardColors.textFaint, radius = 2.5.dp.toPx(), center = Offset(cx, h - 3.dp.toPx()))
        return
    }
    val barWidth = BarWidth.toPx()
    val barHeight = (heightOf(kcal) * growth).coerceAtLeast(if (growth > 0f) 4.dp.toPx() else 0f)
    drawRoundRect(
        color = ink.copy(alpha = if (bar.isToday) 1f else 0.7f),
        topLeft = Offset(cx - barWidth / 2f, h - barHeight),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )

    val low = bar.kcalMin
    val high = bar.kcalMax
    if (bar.hasRange && low != null && high != null) {
        val yLow = h - heightOf(low) * growth
        val yHigh = h - heightOf(high) * growth
        val stroke = 1.5.dp.toPx()
        val cap = 6.dp.toPx()
        drawLine(color = ink, start = Offset(cx, yHigh), end = Offset(cx, yLow), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = ink, start = Offset(cx - cap, yHigh), end = Offset(cx + cap, yHigh), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = ink, start = Offset(cx - cap, yLow), end = Offset(cx + cap, yLow), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private val weekDayNamesEs = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Descripción de una columna: «miércoles 8, hoy, 1.820 kilocalorías» o «miércoles 8, sin registros». */
private fun weekBarDescription(bar: NutritionWeekBar, date: LocalDate?): String {
    val day = date?.let { "${weekDayNamesEs[it.dayOfWeek.value - 1]} ${it.dayOfMonth}" } ?: bar.date
    val intake = bar.kcal?.takeIf { it > 0 }?.let { "${formatKcalEs(it)} kilocalorías" } ?: "sin registros"
    return listOfNotNull(day, "hoy".takeIf { bar.isToday }, intake).joinToString(", ")
}
