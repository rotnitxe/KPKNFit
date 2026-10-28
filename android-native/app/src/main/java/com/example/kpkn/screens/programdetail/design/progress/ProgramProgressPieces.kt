package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/*
 * Piezas de la pestaña Progreso: el resumen, la lista de fuerza y las barras de series. Cada una recibe ya calculado lo que muestra
 * (ver ProgramProgressModel.kt) y solo da formato y colocación.
 */

/** Trazo de lo previsto: blanco al 19 %, como el contorno discontinuo de las barras y de la leyenda. */
private val PlannedOutlineColor = Color.White.copy(alpha = 0.19f)

/** Altura de la zona de barras de series; las barras se alinean por abajo y se escalan a la mayor de la gráfica. */
private val WeekBarZoneHeight = 110.dp

/** Frase principal de la pestaña: Syne Bold 19 / 24. */
private val ProgressHeadlineStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 19.sp,
    lineHeight = 24.sp,
)

/** Etiqueta de una cifra del resumen: Inter SemiBold 10 / 14, con el espaciado de los kickers. */
private val ProgressKpiLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.8.sp,
)

/** Valor de una cifra del resumen: Syne ExtraBold 20 / 24, con cifras tabulares. */
private val ProgressKpiValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** Pie de una cifra del resumen: Inter Regular 11 / 14. */
private val ProgressKpiFooterStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

/** Nombre del ejercicio en la lista de fuerza: Inter SemiBold 14 / 20. */
private val ProgressLiftNameStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

/** Pie de una fila de fuerza («3 sesiones»): Inter Regular 12 / 16. */
private val ProgressLiftCaptionStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** Texto de la cápsula de estado: Inter SemiBold 10,5 / 14. */
private val ProgressCapsuleStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
)

/** Último e1RM de una fila: Syne Bold 16 / 20, con cifras tabulares. */
private val ProgressLiftValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** Cambio de una fila («+7,5»): Inter SemiBold 11 / 14, con cifras tabulares. */
private val ProgressLiftDeltaStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** Etiqueta de una semana bajo su barra: Inter Regular 10 / 14. */
private val ProgressBarLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 14.sp,
)

/** Etiqueta de la semana actual: la misma, en semibold. */
private val ProgressBarCurrentLabelStyle = ProgressBarLabelStyle.copy(fontWeight = FontWeight.SemiBold)

/**
 * Resumen de la pestaña: el periodo como eyebrow, una frase y tres cifras (cumplimiento, series y tonelaje). Lo que muestra ya viene
 * calculado en [ui]; aquí solo se da formato.
 */
@Composable
internal fun ProgressSummaryModule(ui: ProgramProgressUi, modifier: Modifier = Modifier) {
    val kpis = ui.kpis
    val sessionWord = if (kpis.sessionsPlanned == 1) "sesión" else "sesiones"
    KpknModule(modifier = modifier.fillMaxWidth()) {
        Text(
            text = ui.periodLabel.uppercase(Locale.forLanguageTag("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = ui.headline, style = ProgressHeadlineStyle, color = WizardColors.text)
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressKpiTile(
                label = "CUMPLIMIENTO",
                value = kpis.compliancePct?.let { "$it %" } ?: "—",
                footer = "${kpis.sessionsDone} de ${kpis.sessionsPlanned} $sessionWord",
                modifier = Modifier.weight(1f),
            )
            ProgressKpiTile(
                label = "SERIES",
                value = kpis.setsDone.toString(),
                footer = "de ${kpis.setsPlanned} planificadas",
                modifier = Modifier.weight(1f),
            )
            ProgressKpiTile(
                label = "TONELAJE",
                value = "${formatDecimalEs(kpis.tonnageKg / 1000.0, 1)} t",
                footer = "kg × reps",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Una cifra del resumen sobre la superficie interior: etiqueta en mayúsculas, valor grande y pie de una o dos líneas. */
@Composable
private fun ProgressKpiTile(label: String, value: String, footer: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(KpknSurface.inset, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = ProgressKpiLabelStyle,
            color = WizardColors.textFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = ProgressKpiValueStyle,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = footer,
            style = ProgressKpiFooterStyle,
            color = WizardColors.textFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Lista de fuerza: cada ejercicio con su estado, su línea de e1RM, su último valor y su cambio. Con más ejercicios que filas, la
 * cabecera ofrece «Todos» para verlos.
 */
@Composable
internal fun ProgressLiftsModule(
    rows: List<ProgressLiftRow>,
    allCount: Int,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action: (@Composable () -> Unit)? = if (allCount > rows.size) {
        { KpknTextAction(text = "Todos", onClick = onSeeAll) }
    } else {
        null
    }
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Fuerza · e1RM", action = action)
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            ProgressLiftItem(row = row)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "e1RM = repetición máxima estimada de tu mejor serie de cada sesión.",
            style = KpknType.caption,
            color = WizardColors.textFaint,
        )
    }
}

/**
 * Una fila de fuerza: nombre, cápsula de estado y pie a la izquierda; la línea en el centro; el último e1RM y el cambio a la derecha.
 * Para lectores de pantalla, la fila se lee entera como «{nombre}: {estado}, {valor}».
 */
@Composable
private fun ProgressLiftItem(row: ProgressLiftRow) {
    val color = liftStatusColor(row.status)
    val status = liftStatusLabel(row.status)
    val value = formatLiftKg(row.currentKg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${row.name}: $status, $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = ProgressLiftNameStyle,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            LiftStatusCapsule(status = row.status, label = status)
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.caption,
                style = ProgressLiftCaptionStyle,
                color = WizardColors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LiftSparkline(
            points = row.points,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp).size(74.dp, 28.dp),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = ProgressLiftValueStyle, color = WizardColors.text, maxLines = 1)
            Text(
                text = formatLiftDelta(row.deltaKg),
                style = ProgressLiftDeltaStyle,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/**
 * Cápsula del estado: relleno del color del estado al 10 % y texto en ese color; sin tendencia, relleno blanco al 5 % y texto muted.
 */
@Composable
private fun LiftStatusCapsule(status: LiftTrendStatus, label: String) {
    val color = liftStatusColor(status)
    val fill = if (status == LiftTrendStatus.NO_TREND) Color.White.copy(alpha = 0.05f) else color.copy(alpha = 0.10f)
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(fill, KpknShape.pill)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = ProgressCapsuleStyle, color = color, maxLines = 1)
    }
}

/**
 * Línea de la evolución del e1RM: los puntos normalizados entre su mínimo y su máximo, de izquierda a derecha, en el color del estado.
 * Con menos de dos puntos no dibuja nada, pero ocupa su sitio para que las filas no salten.
 */
@Composable
private fun LiftSparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val low = points.minOrNull() ?: return@Canvas
        val high = points.maxOrNull() ?: return@Canvas
        val inset = 2.dp.toPx()
        val drawable = size.height - 2 * inset
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, value ->
            val ratio = if (high > low) ((value - low) / (high - low)).toFloat() else 0.5f
            val x = index * stepX
            val y = inset + drawable * (1f - ratio)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Series duras por semana: una barra por semana, con las hechas sobre el contorno discontinuo de las previstas, y la nota de la
 * semana con más déficit. Sin semanas (alcance de semana) solo salen la cabecera y la nota.
 */
@Composable
internal fun ProgressWorkModule(
    weeks: List<ProgressWeekBar>,
    setsDone: Int,
    setsPlanned: Int,
    note: String?,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(
            label = "Series duras por semana",
            action = { Text(text = "$setsDone / $setsPlanned", style = KpknType.metricM, color = WizardColors.text) },
        )
        if (weeks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            WeekBars(weeks = weeks)
            Spacer(Modifier.height(12.dp))
            WorkLegend()
        }
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = note, style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

/** Las barras de todas las semanas a la misma escala: la mayor cantidad de hechas o previstas ocupa toda la zona. */
@Composable
private fun WeekBars(weeks: List<ProgressWeekBar>) {
    val scale = (weeks.maxOfOrNull { maxOf(it.done, it.planned) } ?: 0).coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weeks.forEach { week ->
            WeekBarColumn(week = week, scale = scale, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Una semana: el contorno discontinuo a la altura de lo previsto y, encima y alineado abajo, el relleno de lo hecho; debajo, la
 * etiqueta. La semana actual va en el acento de músculo y su etiqueta, en tinta y semibold.
 */
@Composable
private fun WeekBarColumn(week: ProgressWeekBar, scale: Int, modifier: Modifier = Modifier) {
    val fill = if (week.isCurrent) SymbolPalette.musculo else week.color.copy(alpha = 0.85f)
    val barShape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${week.label}: ${week.done} hechas de ${week.planned} planificadas"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(WeekBarZoneHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WeekBarZoneHeight * (week.planned.toFloat() / scale))
                    .drawBehind { drawDashedOutline() },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WeekBarZoneHeight * (week.done.toFloat() / scale))
                    .clip(barShape)
                    .background(fill, barShape),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = week.label,
            style = if (week.isCurrent) ProgressBarCurrentLabelStyle else ProgressBarLabelStyle,
            color = if (week.isCurrent) WizardColors.text else WizardColors.textMuted,
            maxLines = 1,
        )
    }
}

/** Leyenda de las barras: un punto lleno para lo hecho y uno con borde discontinuo para lo previsto. */
@Composable
private fun WorkLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(SymbolPalette.musculo, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text = "Hechas", style = KpknType.caption, color = WizardColors.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).drawBehind { drawDashedCircle() })
            Spacer(Modifier.width(6.dp))
            Text(text = "Planificadas", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

/** Contorno discontinuo de 1 dp con esquinas de 6 dp: la parte prevista de una barra. Sin hueco si la barra no tiene altura. */
private fun DrawScope.drawDashedOutline() {
    val stroke = 1.dp.toPx()
    if (size.width <= stroke * 2 || size.height <= stroke * 2) return
    drawRoundRect(
        color = PlannedOutlineColor,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = stroke, pathEffect = dashEffect()),
    )
}

/** Círculo discontinuo de la leyenda, con el mismo trazo que el contorno de las barras. */
private fun DrawScope.drawDashedCircle() {
    val stroke = 1.dp.toPx()
    drawCircle(
        color = PlannedOutlineColor,
        radius = (size.minDimension - stroke) / 2f,
        style = Stroke(width = stroke, pathEffect = dashEffect()),
    )
}

/** Trazo discontinuo: rayas de 4 dp con huecos de 3 dp. */
private fun DrawScope.dashEffect(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))

/** Color del estado de un ejercicio: verde si sube, ámbar si está estancado, rojo si baja y muted si aún no hay tendencia. */
private fun liftStatusColor(status: LiftTrendStatus): Color = when (status) {
    LiftTrendStatus.UP -> SymbolPalette.ok
    LiftTrendStatus.STAGNANT -> SymbolPalette.energia
    LiftTrendStatus.DOWN -> WizardColors.danger
    LiftTrendStatus.NO_TREND -> WizardColors.textMuted
}

/** Etiqueta del estado, tal como se lee en su cápsula. */
private fun liftStatusLabel(status: LiftTrendStatus): String = when (status) {
    LiftTrendStatus.UP -> "Sube"
    LiftTrendStatus.STAGNANT -> "Estancado"
    LiftTrendStatus.DOWN -> "Baja"
    LiftTrendStatus.NO_TREND -> "Sin tendencia"
}

/** Último e1RM en kg con coma y sin decimal sobrante: «142,5 kg», «140 kg». «—» si no hay dato. */
private fun formatLiftKg(kg: Double?): String =
    if (kg == null) "—" else "${formatDecimalEs(kg, 1).removeSuffix(",0")} kg"

/** Cambio con signo: «+7,5», «−2,5»; «±0» si se redondea a cero. «—» si no hay dato. */
private fun formatLiftDelta(delta: Double?): String = when {
    delta == null -> "—"
    Math.round(delta * 10.0) == 0L -> "±0"
    else -> formatSignedDecimalEs(delta, 1).removeSuffix(",0")
}
