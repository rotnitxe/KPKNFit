package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import java.time.YearMonth
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/** Plantillas de la historia del mes; [label] es el nombre que ve quien la elige. */
internal enum class MonthStoryTemplate(val label: String) {
    /** Cuatro cifras del mes, la marca y el nombre del programa, sobre la portada de relieve. */
    RESUMEN("Resumen"),

    /** El calendario del mes con los días entrenados, las sesiones y la racha más larga. */
    CONSTANCIA("Constancia"),

    /** Lo ganado por ejercicio en el mes, con su línea de evolución. */
    FUERZA("Fuerza"),

    /** Las series por músculo en un radial, con los tres primeros. */
    MUSCULOS("Músculos"),
}

/** Una cifra de Resumen: [value] grande, [unit] pequeña a su derecha (o null) y [label] debajo. */
internal data class MonthStat(val value: String, val unit: String?, val label: String)

/** Un ejercicio con lo ganado en el mes: [name], sus e1RM por sesión en [points] y la [gain] («+7,5 kg»). */
internal data class MonthLift(val name: String, val points: List<Double>, val gain: String)

/** Series de trabajo de un músculo principal en el mes. */
internal data class MonthMuscle(val name: String, val sets: Int)

/**
 * Una historia del mes: el [monthLabel] («octubre 2026») y el [monthName] («octubre») de la cabecera, las cifras [stats], la marca
 * [badge] con el día [markDay], el calendario ([month] y [trainedDays]), las sesiones con su [consistencyMeta], la racha, las
 * ganancias [lifts] y las series por músculo [muscles].
 */
internal data class MonthStory(
    val monthLabel: String,
    val monthName: String,
    val programName: String?,
    val stats: List<MonthStat>,
    val badge: String?,
    val month: YearMonth,
    val trainedDays: Set<Int>,
    val markDay: Int?,
    val sessions: Int,
    val consistencyMeta: String,
    val longestStreak: Int,
    val lifts: List<MonthLift>,
    val totalSets: Int,
    val muscles: List<MonthMuscle>,
)

/** Plantillas que se ofrecen para [story]: Resumen y Constancia siempre; Fuerza si hay ganancias y Músculos si hay series. */
internal fun monthStoryTemplates(story: MonthStory): List<MonthStoryTemplate> = buildList {
    add(MonthStoryTemplate.RESUMEN)
    add(MonthStoryTemplate.CONSTANCIA)
    if (story.lifts.isNotEmpty()) add(MonthStoryTemplate.FUERZA)
    if (story.muscles.isNotEmpty()) add(MonthStoryTemplate.MUSCULOS)
}

/**
 * Historia del mes a partir del [report] del mes. [programName] null = todos los programas; [planPct] null = sin plan, así que el
 * cuarto dato son las series del mes.
 */
internal fun monthStoryFrom(report: MonthlyReport, programName: String?, planPct: Int?): MonthStory {
    val monthName = monthNamesEs[report.month.monthValue - 1]
    val hours = report.minutes / 60
    val minutesLeft = report.minutes % 60
    val time = if (report.minutes < 60) {
        MonthStat(twoDigits(report.minutes), "min", "Entrenando")
    } else {
        MonthStat(hours.toString(), "h ${twoDigits(minutesLeft)}", "Entrenando")
    }
    val fourth = if (planPct != null) {
        MonthStat(planPct.toString(), "%", "Del plan")
    } else {
        MonthStat(report.hardSets.toString(), null, "Series")
    }
    val best = report.bestMark
    return MonthStory(
        monthLabel = "$monthName ${report.month.year}",
        monthName = monthName,
        programName = programName,
        stats = listOf(
            MonthStat(report.sessions.toString(), null, "Sesiones"),
            time,
            MonthStat(decimalComma(report.tonnageKg / 1000.0), "t", "Movidas"),
            fourth,
        ),
        badge = best?.let { "Marca: ${it.exerciseName.lowercase()} ${kgText(it.e1rm)} kg" },
        month = report.month,
        trainedDays = report.trainedDays.toSet(),
        markDay = best?.date?.dayOfMonth,
        sessions = report.sessions,
        consistencyMeta = "$hours h ${twoDigits(minutesLeft)} min entrenando" + (planPct?.let { " · $it % del plan" } ?: ""),
        longestStreak = report.longestStreak,
        lifts = report.gains.take(3).map { MonthLift(it.exerciseName, it.points, "+${kgText(it.deltaKg)} kg") },
        totalSets = report.hardSets,
        muscles = report.muscles.sortedByDescending { it.sets }.take(9).map { MonthMuscle(it.muscle, it.sets) },
    )
}

/**
 * Dibuja la historia [story] con la plantilla [template] en su lienzo de 1080 × 1920. [progress] es el avance de la portada (1 =
 * completa); solo Resumen y Fuerza tienen portada.
 */
internal fun DrawScope.drawMonthStory(story: MonthStory, template: MonthStoryTemplate, measurer: TextMeasurer, progress: Float = 1f) {
    when (template) {
        MonthStoryTemplate.RESUMEN -> drawMonthResumen(story, measurer, progress)
        MonthStoryTemplate.CONSTANCIA -> drawMonthConstancia(story, measurer)
        MonthStoryTemplate.FUERZA -> drawMonthFuerza(story, measurer, progress)
        MonthStoryTemplate.MUSCULOS -> drawMonthMusculos(story, measurer)
    }
}

/** Pinta la historia [story] con la plantilla [template] en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderMonthStory(context: Context, story: MonthStory, template: MonthStoryTemplate): android.graphics.Bitmap =
    renderStory(context) { drawMonthStory(story, template, it) }

// ------------------------------------------------------------------------------------------------------------ medidas (px)

/** Pie de todas las historias del mes. */
private const val MONTH_FOOT = "Resumen del mes"

/** Separación entre el bloque de contenido y el pie. */
private const val MONTH_FOOT_GAP = 75.6f

/** Separación entre el bloque de contenido y la meta, de la meta al nombre y del nombre al eyebrow. */
private const val MONTH_META_GAP = 54f
private const val MONTH_NAME_META_GAP = 21.6f
private const val MONTH_EYEBROW_NAME_GAP = 27f

/** Altura de la línea de cada cifra de Resumen, separación con su etiqueta y con su unidad. */
private const val MONTH_STAT_LINE = 101.5f
private const val MONTH_STAT_LABEL_GAP = 15f
private const val MONTH_STAT_UNIT_GAP = 13f

/** Separación entre filas de la rejilla de Resumen (43.2 px). */
private const val MONTH_GRID_GAP = 43.2f

/** Separación entre la rejilla y la cápsula de marca: 51 px, lo que mide el prototipo (no 43.2). */
private const val MONTH_BADGE_GAP = 51.2f

/** Línea base de la cifra de Resumen dentro de su celda (la base de la «1»), medida en el prototipo. */
private const val MONTH_STAT_BASELINE = 84.5f

/** Relleno de la cápsula de marca y tamaño y separación de su icono. */
private const val MONTH_BADGE_PAD_Y = 15f
private const val MONTH_BADGE_PAD_X = 34.6f
private const val MONTH_BADGE_ICON = 38.9f
private const val MONTH_BADGE_ICON_GAP = 17f

/** Separación entre filas de Fuerza y Músculos, relleno superior de cada fila y grosor de la regla. */
private const val MONTH_ROW_GAP = 25.9f
private const val MONTH_ROW_PAD = 23.8f
private const val MONTH_RULE = 1f

/** Línea de evolución de Fuerza: 237.6 × 75.6 px, con su extremo derecho en x = 637.2 (1004.4 − 324 − 43.2). */
private const val MONTH_SPARK_W = 237.6f
private const val MONTH_SPARK_H = 75.6f
private val monthSparkRight = StoryCanvas.RIGHT - 324f - 43.2f

/** Hasta dónde llega la portada de Resumen. */
private const val MONTH_RESUMEN_ART_BOTTOM = 1036.8f

/** Calendario de Constancia: lienzo de 300 × 236 escalado a 885.6 px desde (97.2, 192), y el texto desde y = 998.4. */
private const val MONTH_CAL_LEFT = 97.2f
private const val MONTH_CAL_TOP = 192f
private const val MONTH_CAL_WIDTH = 885.6f
private const val MONTH_CAL_TEXT_TOP = 998.4f

/** Número grande de Constancia: altura de línea, línea base medida desde su borde superior y separaciones. */
private const val MONTH_COUNT_LINE = 184.5f
private const val MONTH_COUNT_BASELINE = 161f
private const val MONTH_COUNT_GAP = 21.6f
private const val MONTH_COUNT_META_GAP = 27f

/** Radial de Músculos: viewBox (−40, 0, 380, 280) a 1080 px de ancho, con el origen vertical a 96 px. */
private const val MONTH_RADIAL_SCALE = 1080.0 / 380.0
private const val MONTH_RADIAL_TOP = 96.0

/** Meses en español en minúsculas, para no depender de la configuración regional del dispositivo. */
private val monthNamesEs = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

// ------------------------------------------------------------------------------------------------------------- estilos

/** Eyebrow: Inter SemiBold 38.9 px con 0.25 em de interletraje (se escribe en mayúsculas). */
private val monthEyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 38.9f.sp,
    letterSpacing = 0.25f.em,
)

/** Nombre de la cabecera: Syne ExtraBold de [size] px con interlineado igual al tamaño, como en las historias de programa. */
private fun monthNameStyle(size: Float): TextStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = size.sp,
    letterSpacing = (-0.04f).em,
    lineBreak = LineBreak.Heading,
)

/** Meta de la cabecera: Inter Medium 41 px. */
private val monthMetaStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 41f.sp)

/** Nombre de fila (ejercicio, músculo) y la racha: Inter Medium 38.9 px. */
private val monthRowNameStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 38.9f.sp)

/** Cifra grande de Resumen: Syne ExtraBold 101.5 px con interlineado de 101.5 px, −0.04 em y cifras tabulares. */
private val monthStatValueStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 101.5f.sp,
    lineHeight = 101.5f.sp,
    letterSpacing = (-0.04f).em,
    fontFeatureSettings = "tnum",
)

/** Unidad de una cifra de Resumen: Inter SemiBold 38.9 px. */
private val monthStatUnitStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 38.9f.sp)

/** Etiqueta de una cifra de Resumen: Inter SemiBold 30.2 px con 0.16 em de interletraje (en mayúsculas). */
private val monthStatLabelStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 30.2f.sp,
    letterSpacing = 0.16f.em,
)

/** Texto de la cápsula de marca: Inter SemiBold 34.6 px. */
private val monthBadgeStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 34.6f.sp)

/** Número de sesiones de Constancia: Syne ExtraBold 205 px con interlineado de 184.5 px y −0.05 em. */
private val monthCountStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 205f.sp,
    lineHeight = MONTH_COUNT_LINE.sp,
    letterSpacing = (-0.05f).em,
    fontFeatureSettings = "tnum",
)

/** «sesiones» junto al número de Constancia: Inter SemiBold 47.5 px. */
private val monthCountUnitStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 47.5f.sp)

/** Valor de la racha de Constancia: Syne ExtraBold 49.7 px. */
private val monthStreakStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 49.7f.sp)

/** Ganancia de Fuerza: Syne ExtraBold 56 px. */
private val monthGainStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 56f.sp,
    letterSpacing = (-0.03f).em,
)

/** Series de un músculo: Syne ExtraBold 64.8 px. */
private val monthSetsStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 64.8f.sp)

/** La palabra «series» de Músculos: Inter SemiBold 36.7 px. */
private val monthSeriesStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 36.7f.sp)

/** Nombre de un músculo en el radial: Inter Normal 28.4 px. */
private val monthRadialLabelStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 28.4f.sp)

// ------------------------------------------------------------------------------------------------------------- piezas

/** La cabecera de una historia anclada abajo, ya medida: [eyebrow], [name] de [nameSize] px (con su [nameLift]) y [meta]. */
private class MonthHeader(
    val eyebrow: TextLayoutResult,
    val name: TextLayoutResult,
    val nameSize: Float,
    val nameLift: Float,
    val meta: TextLayoutResult,
)

/** Mide la cabecera. El nombre usa el interlineado de [nameSize] y sube la mitad del espacio sobrante, como drawStoryHeader. */
private fun TextMeasurer.monthHeader(eyebrow: String, name: String, nameSize: Float, meta: String): MonthHeader {
    val column = StoryCanvas.COLUMN_WIDTH.toInt()
    val style = monthNameStyle(nameSize)
    val nameLayout = storyText(name, style, width = column, maxLines = 3, ellipsis = true)
    val natural = storyText(name, style.copy(lineHeight = TextUnit.Unspecified), width = column, maxLines = 3, ellipsis = true)
    val naturalLine = natural.size.height.toFloat() / natural.lineCount.coerceAtLeast(1)
    return MonthHeader(
        eyebrow = storyText(eyebrow.uppercase(Locale.forLanguageTag("es")), monthEyebrowStyle, width = column, maxLines = 1),
        name = nameLayout,
        nameSize = nameSize,
        nameLift = (nameSize - naturalLine) / 2f,
        meta = storyText(meta, monthMetaStyle, width = column, maxLines = 1, ellipsis = true),
    )
}

/** Pinta la cabecera encima de un bloque de contenido que empieza en [contentTop]: la meta acaba 54 px por encima. */
private fun DrawScope.drawMonthHeader(header: MonthHeader, contentTop: Float) {
    val metaTop = contentTop - MONTH_META_GAP - header.meta.size.height
    val nameTop = metaTop - MONTH_NAME_META_GAP - header.name.lineCount * header.nameSize
    val eyebrowTop = nameTop - MONTH_EYEBROW_NAME_GAP - header.eyebrow.size.height
    drawText(header.eyebrow, color = StoryCanvas.Faint, topLeft = Offset(StoryCanvas.LEFT, eyebrowTop))
    drawText(header.name, color = StoryCanvas.Ink, topLeft = Offset(StoryCanvas.LEFT, nameTop + header.nameLift))
    drawText(header.meta, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, metaTop))
}

/** Textos sobre una misma línea base: [ascent] es lo que sube el más alto por encima de la base y [height] el alto del grupo. */
private class MonthBaseline(vararg parts: TextLayoutResult) {
    private val items: List<TextLayoutResult> = parts.toList()

    /** Distancia de la línea más alta a la base. */
    val ascent: Float = items.maxOf { it.firstBaseline }

    /** Alto del grupo, de la línea más alta a la más baja. */
    val height: Float = ascent + items.maxOf { it.size.height - it.firstBaseline }

    /** Ancho del grupo: los textos pegados uno detrás de otro. */
    val width: Float = items.sumOf { it.size.width }.toFloat()
}

/** Pone [layout] con su línea base en [baseline] y su borde izquierdo en [x]. */
private fun DrawScope.drawMonthOnBaseline(layout: TextLayoutResult, color: Color, x: Float, baseline: Float) {
    drawText(layout, color = color, topLeft = Offset(x, baseline - layout.firstBaseline))
}

/** Línea divisoria de 1 px a todo el ancho de la columna, con su parte superior en [top] y la opacidad [alpha]. */
private fun DrawScope.drawMonthRule(top: Float, alpha: Float) {
    drawRect(Color.White, topLeft = Offset(StoryCanvas.LEFT, top), size = Size(StoryCanvas.COLUMN_WIDTH, MONTH_RULE), alpha = alpha)
}

/** Alto de una pila de filas de contenido [heights]: cada fila con su regla y su relleno, y [MONTH_ROW_GAP] entre filas. */
private fun monthStackHeight(heights: List<Float>): Float =
    heights.fold(0f) { acc, height -> acc + MONTH_RULE + MONTH_ROW_PAD + height } + MONTH_ROW_GAP * (heights.size - 1).coerceAtLeast(0)

/** Dos dígitos con cero delante: «05», «40». */
private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

/** Kilos con coma decimal y redondeados a 0,5: «142,5», «4» (sin «,0»). */
private fun kgText(kg: Double): String {
    val halves = (kg * 2).roundToLong()
    return if (halves % 2L == 0L) (halves / 2).toString() else "${halves / 2},5"
}

/** Un número con una cifra decimal y coma: 42.4 → «42,4». */
private fun decimalComma(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10},${abs(tenths % 10)}"
}

// ----------------------------------------------------------------------------------------------------- Resumen

/** Una cifra de Resumen ya medida: [value] con su [unit] (si hay) y la [label] en mayúsculas debajo. */
private class MonthStatCell(val value: TextLayoutResult, val unit: TextLayoutResult?, val label: TextLayoutResult) {
    /** Alto de la celda: la línea de la cifra, 15 px y la etiqueta. */
    val height: Float = MONTH_STAT_LINE + MONTH_STAT_LABEL_GAP + label.size.height
}

/** Mide una cifra de Resumen. */
private fun TextMeasurer.monthStatCell(stat: MonthStat): MonthStatCell = MonthStatCell(
    value = storyText(stat.value, monthStatValueStyle),
    unit = stat.unit?.let { storyText(it, monthStatUnitStyle) },
    label = storyText(stat.label.uppercase(Locale.forLanguageTag("es")), monthStatLabelStyle),
)

/** Alto de la rejilla de dos columnas: filas de la altura de la celda más alta, separadas por [MONTH_GRID_GAP]. */
private fun monthGridHeight(cells: List<MonthStatCell>): Float {
    if (cells.isEmpty()) return 0f
    val rows = (cells.size + 1) / 2
    return rows * cells.maxOf { it.height } + (rows - 1) * MONTH_GRID_GAP
}

/** Rejilla de cifras: la primera columna en el margen y la segunda en x = 567; cada cifra con su unidad y su etiqueta. */
private fun DrawScope.drawMonthGrid(cells: List<MonthStatCell>, top: Float) {
    val rowHeight = cells.maxOfOrNull { it.height } ?: return
    val second = StoryCanvas.LEFT + (StoryCanvas.COLUMN_WIDTH + 54f) / 2f
    cells.forEachIndexed { index, cell ->
        val x = if (index % 2 == 0) StoryCanvas.LEFT else second
        val y = top + (index / 2) * (rowHeight + MONTH_GRID_GAP)
        drawMonthOnBaseline(cell.value, StoryCanvas.Ink, x, y + MONTH_STAT_BASELINE)
        cell.unit?.let { unit ->
            drawMonthOnBaseline(unit, StoryCanvas.Muted, x + cell.value.size.width + MONTH_STAT_UNIT_GAP, y + MONTH_STAT_BASELINE)
        }
        drawText(cell.label, color = StoryCanvas.Faint, topLeft = Offset(x, y + MONTH_STAT_LINE + MONTH_STAT_LABEL_GAP))
    }
}

/** La cápsula de marca medida: su texto. */
private class MonthBadge(val text: TextLayoutResult) {
    /** Alto: lo mayor entre el icono y el texto, más 15 px arriba y abajo. */
    val height: Float = max(MONTH_BADGE_ICON, text.size.height.toFloat()) + 2 * MONTH_BADGE_PAD_Y

    /** Ancho: relleno, icono, separación, texto y relleno final. */
    val width: Float = 2 * MONTH_BADGE_PAD_X + MONTH_BADGE_ICON + MONTH_BADGE_ICON_GAP + text.size.width
}

/** Cápsula de marca: borde de 1 px en acento al 44 %, destello de cuatro puntas y el texto en acento. */
private fun DrawScope.drawMonthBadge(badge: MonthBadge, top: Float) {
    val left = StoryCanvas.LEFT
    drawRoundRect(
        color = StoryCanvas.Accent,
        topLeft = Offset(left + 0.5f, top + 0.5f),
        size = Size(badge.width - 1f, badge.height - 1f),
        cornerRadius = CornerRadius(badge.height / 2f),
        style = Stroke(width = 1f),
        alpha = 0.44f,
    )
    // El icono es de 38.9 px; el destello ocupa 26 px (16 de sus 24 unidades), como en el prototipo.
    val iconCenter = Offset(left + MONTH_BADGE_PAD_X + MONTH_BADGE_ICON / 2f, top + badge.height / 2f)
    drawPath(
        path = monthSparkle(iconCenter, 13f),
        color = StoryCanvas.Accent,
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawText(
        badge.text,
        color = StoryCanvas.Accent,
        topLeft = Offset(
            left + MONTH_BADGE_PAD_X + MONTH_BADGE_ICON + MONTH_BADGE_ICON_GAP,
            top + (badge.height - badge.text.size.height) / 2f,
        ),
    )
}

/** Destello de cuatro puntas con los lados cóncavos, de radio [r] alrededor de [center]. */
private fun monthSparkle(center: Offset, r: Float): Path {
    val cx = center.x
    val cy = center.y
    val q = r * 0.2f
    return Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx + q, cy - q, cx + r, cy)
        quadraticTo(cx + q, cy + q, cx, cy + r)
        quadraticTo(cx - q, cy + q, cx - r, cy)
        quadraticTo(cx - q, cy - q, cx, cy - r)
        close()
    }
}

/** Resumen: portada de relieve, cabecera «Mi <mes>», la rejilla de cifras y, si hay, la cápsula de la marca. */
private fun DrawScope.drawMonthResumen(story: MonthStory, measurer: TextMeasurer, progress: Float) {
    drawStoryBackdrop(
        cover = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, seed = 5),
        measurer = measurer,
        progress = progress,
        artBottom = MONTH_RESUMEN_ART_BOTTOM,
    )
    val cells = story.stats.map { measurer.monthStatCell(it) }
    val badge = story.badge?.let { MonthBadge(measurer.storyText(it, monthBadgeStyle)) }
    val header = measurer.monthHeader(story.monthLabel, "Mi ${story.monthName}", 113.4f, story.programName ?: "Todos tus programas")
    val gridHeight = monthGridHeight(cells)
    val contentHeight = gridHeight + (badge?.let { MONTH_BADGE_GAP + it.height } ?: 0f)
    val footTop = drawStoryFoot(measurer, MONTH_FOOT)
    val contentTop = footTop - MONTH_FOOT_GAP - contentHeight
    drawMonthHeader(header, contentTop)
    drawMonthGrid(cells, contentTop)
    badge?.let { drawMonthBadge(it, contentTop + gridHeight + MONTH_BADGE_GAP) }
}

// ----------------------------------------------------------------------------------------------------- Constancia

/** Constancia: sin portada; el calendario del mes, el número de sesiones con su meta y, sobre el pie, la racha. */
private fun DrawScope.drawMonthConstancia(story: MonthStory, measurer: TextMeasurer) {
    drawRect(StoryCanvas.Background, size = Size(StoryCanvas.WIDTH.toFloat(), StoryCanvas.HEIGHT.toFloat()))
    drawMonthCalendar(story)
    val column = StoryCanvas.COLUMN_WIDTH.toInt()
    val eyebrow = measurer.storyText(
        "${story.monthLabel} · constancia".uppercase(Locale.forLanguageTag("es")),
        monthEyebrowStyle,
        width = column,
        maxLines = 1,
    )
    drawText(eyebrow, color = StoryCanvas.Faint, topLeft = Offset(StoryCanvas.LEFT, MONTH_CAL_TEXT_TOP))

    val countTop = MONTH_CAL_TEXT_TOP + eyebrow.size.height + MONTH_COUNT_GAP
    val count = measurer.storyText(story.sessions.toString(), monthCountStyle)
    val unit = measurer.storyText("sesiones", monthCountUnitStyle)
    // El número y «sesiones» van sobre la misma línea base: la base de la «1» (la «4» de Syne baja por debajo).
    val baseline = countTop + MONTH_COUNT_BASELINE
    drawMonthOnBaseline(count, StoryCanvas.Ink, StoryCanvas.LEFT, baseline)
    drawMonthOnBaseline(unit, StoryCanvas.Muted, StoryCanvas.LEFT + count.size.width + MONTH_COUNT_GAP, baseline)

    val metaTop = countTop + count.lineCount * MONTH_COUNT_LINE + MONTH_COUNT_META_GAP
    val meta = measurer.storyText(story.consistencyMeta, monthMetaStyle, width = column, maxLines = 1, ellipsis = true)
    drawText(meta, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, metaTop))

    val footTop = drawStoryFoot(measurer, MONTH_FOOT)
    drawMonthStreak(measurer, story.longestStreak, bottom = footTop - MONTH_GRID_GAP)
}

/**
 * Calendario del mes, semanas de lunes a domingo: los días entrenados en blanco, la marca en acento con su halo y el resto en
 * contorno.
 */
private fun DrawScope.drawMonthCalendar(story: MonthStory) {
    val scale = MONTH_CAL_WIDTH / 300f
    val lead = story.month.atDay(1).dayOfWeek.value - 1
    for (day in 1..story.month.lengthOfMonth()) {
        val index = day - 1 + lead
        val center = Offset(
            MONTH_CAL_LEFT + (24 + 42 * (index % 7)) * scale,
            MONTH_CAL_TOP + (24 + 42 * (index / 7)) * scale,
        )
        val radius = 15f * scale
        when {
            day == story.markDay -> {
                // Halo suave: el acento se apaga desde el borde del punto hasta 16 px fuera.
                val halo = radius + 16f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to StoryCanvas.Accent.copy(alpha = 0.5f),
                        radius / halo to StoryCanvas.Accent.copy(alpha = 0.45f),
                        1f to StoryCanvas.Accent.copy(alpha = 0f),
                        center = center,
                        radius = halo,
                    ),
                    radius = halo,
                    center = center,
                )
                drawCircle(StoryCanvas.Accent, radius = radius, center = center)
            }
            day in story.trainedDays -> drawCircle(StoryCanvas.Ink, radius = radius, center = center)
            else -> drawCircle(StoryCanvas.Ink, radius = radius, center = center, alpha = 0.2f, style = Stroke(width = 3.5f))
        }
    }
}

/** Racha sobre el pie: regla de 1 px al 8 %, relleno de 23.8 px y «Racha más larga» con «N sesiones» en la misma línea base. */
private fun DrawScope.drawMonthStreak(measurer: TextMeasurer, streak: Int, bottom: Float) {
    val label = measurer.storyText("Racha más larga", monthRowNameStyle)
    val value = measurer.storyText(if (streak == 1) "1 sesión" else "$streak sesiones", monthStreakStyle)
    val group = MonthBaseline(label, value)
    val top = bottom - MONTH_RULE - MONTH_ROW_PAD - group.height
    drawMonthRule(top, alpha = 0.08f)
    val baseline = top + MONTH_RULE + MONTH_ROW_PAD + group.ascent
    drawMonthOnBaseline(label, StoryCanvas.Muted, StoryCanvas.LEFT, baseline)
    drawMonthOnBaseline(value, StoryCanvas.Ink, StoryCanvas.RIGHT - value.size.width, baseline)
}

// ----------------------------------------------------------------------------------------------------- Fuerza

/** Un ejercicio de Fuerza medido: [name], [gain] y los [points] de su línea de evolución. */
private class MonthLiftRow(val name: TextLayoutResult, val gain: TextLayoutResult, val points: List<Double>) {
    /** Alto del contenido de la fila: lo mayor entre la línea de evolución y los textos. */
    val height: Float = maxOf(MONTH_SPARK_H, name.size.height.toFloat(), gain.size.height.toFloat())
}

/** Mide un ejercicio de Fuerza. El nombre tiene el ancho de la columna izquierda, hasta la línea de evolución. */
private fun TextMeasurer.monthLiftRow(lift: MonthLift): MonthLiftRow = MonthLiftRow(
    name = storyText(lift.name, monthRowNameStyle, width = 290, maxLines = 1, ellipsis = true),
    gain = storyText(lift.gain, monthGainStyle),
    points = lift.points,
)

/** Fuerza: portada de perfil, cabecera «Más fuerte» y hasta tres filas con nombre, línea de evolución y ganancia. */
private fun DrawScope.drawMonthFuerza(story: MonthStory, measurer: TextMeasurer, progress: Float) {
    drawStoryBackdrop(
        cover = LineCoverSpec(
            LineCoverStyle.PERFIL,
            LineCoverBackground.CARBON,
            seed = 3,
            weekLoads = listOf(0.55f, 0.62f, 0.7f, 0.8f),
            currentWeek = 3,
            weeks = 4,
        ),
        measurer = measurer,
        progress = progress,
    )
    val rows = story.lifts.take(3).map { measurer.monthLiftRow(it) }
    val header = measurer.monthHeader(story.monthLabel, "Más fuerte", 113.4f, "e1RM al final del mes")
    val footTop = drawStoryFoot(measurer, MONTH_FOOT)
    val contentTop = footTop - MONTH_FOOT_GAP - monthStackHeight(rows.map { it.height })
    drawMonthHeader(header, contentTop)
    drawMonthLifts(rows, contentTop)
}

/** Filas de Fuerza: regla, nombre a la izquierda, línea de evolución y ganancia en acento a la derecha, centradas en vertical. */
private fun DrawScope.drawMonthLifts(rows: List<MonthLiftRow>, top: Float) {
    var y = top
    for (row in rows) {
        drawMonthRule(y, alpha = 0.08f)
        val middle = y + MONTH_RULE + MONTH_ROW_PAD + row.height / 2f
        drawText(row.name, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, middle - row.name.size.height / 2f))
        drawText(
            row.gain,
            color = StoryCanvas.Accent,
            topLeft = Offset(StoryCanvas.RIGHT - row.gain.size.width, middle - row.gain.size.height / 2f),
        )
        drawMonthSpark(row.points, left = monthSparkRight - MONTH_SPARK_W, top = middle - MONTH_SPARK_H / 2f)
        y += MONTH_RULE + MONTH_ROW_PAD + row.height + MONTH_ROW_GAP
    }
}

/** Línea de evolución en la caja de 237.6 × 75.6 px con esquina ([left], [top]): lienzo de 84 × 28 unidades y trazo de 3 px. */
private fun DrawScope.drawMonthSpark(points: List<Double>, left: Float, top: Float) {
    if (points.size < 2) return
    val low = points.minOrNull() ?: return
    val high = points.maxOrNull() ?: return
    val path = Path()
    points.forEachIndexed { i, value ->
        val x = 2f + i * (80f / (points.size - 1))
        val y = if (high == low) 14f else 25f - ((value - low) / (high - low) * 22).toFloat()
        val px = left + x * (MONTH_SPARK_W / 84f)
        val py = top + y * (MONTH_SPARK_H / 28f)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, color = StoryCanvas.Ink, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ----------------------------------------------------------------------------------------------------- Músculos

/** Un músculo de la lista medido: [name], sus [value] series y la palabra « series»; [lead] si es el que más trabajó. */
private class MonthMuscleRow(val name: TextLayoutResult, val value: TextLayoutResult, val unit: TextLayoutResult, val lead: Boolean) {
    /** Las series y su palabra, sobre la misma línea base. */
    val group = MonthBaseline(value, unit)

    /** Alto del contenido de la fila: lo mayor entre el nombre y el grupo de series. */
    val height: Float = max(name.size.height.toFloat(), group.height)
}

/** Mide un músculo de la lista. */
private fun TextMeasurer.monthMuscleRow(muscle: MonthMuscle, lead: Boolean): MonthMuscleRow = MonthMuscleRow(
    name = storyText(muscle.name, monthRowNameStyle, width = 290, maxLines = 1, ellipsis = true),
    value = storyText(muscle.sets.toString(), monthSetsStyle),
    unit = storyText(" series", monthSeriesStyle),
    lead = lead,
)

/** Músculos: sin portada; el radial de series por músculo, la cabecera «Lo que más trabajé» y las tres primeras filas. */
private fun DrawScope.drawMonthMusculos(story: MonthStory, measurer: TextMeasurer) {
    drawRect(StoryCanvas.Background, size = Size(StoryCanvas.WIDTH.toFloat(), StoryCanvas.HEIGHT.toFloat()))
    val ranked = story.muscles.sortedByDescending { it.sets }
    drawMonthRadial(measurer, ranked.take(9))
    val rows = ranked.take(3).mapIndexed { index, muscle -> measurer.monthMuscleRow(muscle, lead = index == 0) }
    val header = measurer.monthHeader(
        "${story.monthLabel} · músculos",
        "Lo que más trabajé",
        97.2f,
        "${story.totalSets} series en el mes",
    )
    val footTop = drawStoryFoot(measurer, MONTH_FOOT)
    val contentTop = footTop - MONTH_FOOT_GAP - monthStackHeight(rows.map { it.height })
    drawMonthHeader(header, contentTop)
    drawMonthMuscleRows(rows, contentTop)
}

/** Punto (ux, uy) del radial en píxeles del lienzo de 1080 × 1920. */
private fun monthRadialAt(ux: Double, uy: Double): Offset = Offset(
    ((ux + 40.0) * MONTH_RADIAL_SCALE).toFloat(),
    (MONTH_RADIAL_TOP + uy * MONTH_RADIAL_SCALE).toFloat(),
)

/**
 * Radial: cuatro anillos y un rayo por músculo, desde el centro (150, 140) en orden de series a partir de arriba. El rayo del que
 * más trabajó va en acento y los que tienen cero series, apagados. Cada nombre va en el extremo, alineado hacia fuera.
 */
private fun DrawScope.drawMonthRadial(measurer: TextMeasurer, shown: List<MonthMuscle>) {
    val center = monthRadialAt(150.0, 140.0)
    for (radius in listOf(28.0, 56.0, 84.0, 112.0)) {
        drawCircle(
            color = Color.White,
            radius = (radius * MONTH_RADIAL_SCALE).toFloat(),
            center = center,
            alpha = 0.08f,
            style = Stroke(width = (0.7 * MONTH_RADIAL_SCALE).toFloat()),
        )
    }
    val count = shown.size
    shown.forEachIndexed { i, muscle ->
        val angle = -PI / 2 + i.toDouble() / count * 2 * PI
        val length = min(26.0 + muscle.sets * 1.65, 132.0)
        val lead = i == 0
        drawLine(
            color = if (lead) StoryCanvas.Accent else StoryCanvas.Ink,
            start = center,
            end = monthRadialAt(150.0 + cos(angle) * length, 140.0 + sin(angle) * length),
            strokeWidth = ((if (lead) 3.0 else 1.4) * MONTH_RADIAL_SCALE).toFloat(),
            cap = StrokeCap.Round,
            alpha = if (muscle.sets == 0) 0.3f else 1f,
        )
        // La etiqueta va 14 unidades más allá del extremo; su línea base, 4 unidades por debajo de ese punto.
        val reach = length + 14.0
        val anchor = monthRadialAt(150.0 + cos(angle) * reach, 140.0 + sin(angle) * reach + 4.0)
        val label = measurer.storyText(muscle.name, monthRadialLabelStyle)
        val cosine = cos(angle)
        val left = when {
            cosine > 0.3 -> anchor.x
            cosine < -0.3 -> anchor.x - label.size.width
            else -> anchor.x - label.size.width / 2f
        }
        drawMonthOnBaseline(label, StoryCanvas.Faint, left, anchor.y)
    }
}

/** Filas de Músculos: regla, nombre a la izquierda y las series a la derecha (la primera en acento), centradas en vertical. */
private fun DrawScope.drawMonthMuscleRows(rows: List<MonthMuscleRow>, top: Float) {
    var y = top
    for (row in rows) {
        drawMonthRule(y, alpha = 0.08f)
        val middle = y + MONTH_RULE + MONTH_ROW_PAD + row.height / 2f
        drawText(row.name, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, middle - row.name.size.height / 2f))
        val baseline = middle - row.group.height / 2f + row.group.ascent
        val x = StoryCanvas.RIGHT - row.group.width
        drawMonthOnBaseline(row.value, if (row.lead) StoryCanvas.Accent else StoryCanvas.Ink, x, baseline)
        drawMonthOnBaseline(row.unit, StoryCanvas.Faint, x + row.value.size.width, baseline)
        y += MONTH_RULE + MONTH_ROW_PAD + row.height + MONTH_ROW_GAP
    }
}
