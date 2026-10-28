package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.workout.WorkoutStoryData
import com.example.kpkn.screens.workout.WorkoutStoryOptions
import com.example.kpkn.screens.workout.WorkoutStoryTemplate
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ----------------------------------------------------------------------------------------------------------- modelo

/** Una cifra de la historia: [value] grande, [unit] pequeña a su derecha (o null) y [label] debajo. */
internal data class WorkoutStat(val value: String, val unit: String?, val label: String)

/** Una ganancia de fuerza del entreno: el [name] del ejercicio y su [delta], como «+2,5 kg». */
internal data class WorkoutGain(val name: String, val delta: String)

/**
 * Historia de un entreno ya en texto. [dateLine] es la fecha de la cabecera («MIÉ 28 OCT · SEMANA 4») y [title] el nombre de la sesión;
 * [meta] es el programa. [stats] y [gains] van en la portada general y [records] cuenta las marcas nuevas. El ejercicio elegido trae su
 * e1RM actual ([exerciseE1rm]), su ganancia en el historial ([exerciseDelta]), sus sesiones y los puntos de la gráfica
 * ([exercisePoints]), y la mejor serie con su esfuerzo ([bestSet], [bestEffort]). [photoPath] es la primera foto de la sesión.
 */
internal data class WorkoutStory(
    val dateLine: String,
    val title: String,
    val meta: String?,
    val stats: List<WorkoutStat>,
    val gains: List<WorkoutGain>,
    val records: Int,
    val exerciseName: String?,
    val exerciseE1rm: String?,
    val exerciseDelta: String?,
    val exerciseSessions: Int,
    val exercisePoints: List<Double>,
    val bestSet: String?,
    val bestEffort: String?,
    val photoPath: String?,
)

/**
 * Historia del entreno [data] con las [options]: solo entran las cifras, ganancias y ejercicio que la plantilla pide. Sin datos
 * inventados: lo que falte en [data] sale null o vacío.
 */
internal fun workoutStoryFrom(data: WorkoutStoryData, options: WorkoutStoryOptions): WorkoutStory {
    val presentation = data.presentation
    val stats = buildList<WorkoutStat> {
        if (options.duration) add(WorkoutStat(data.durationMinutes.toString(), "min", "Duración"))
        add(WorkoutStat(workoutThousands(data.volume), "kg", "Volumen"))
        add(WorkoutStat(data.sets.toString(), null, "Series"))
        val rir = data.averageRir
        if (options.intensity && rir != null) add(WorkoutStat(workoutDecimalComma(rir), null, "RIR medio"))
    }
    val gains = if (options.improvements) {
        data.lifts
            .filter { it.isImproved }
            .sortedByDescending { it.deltaKg ?: 0.0 }
            .take(2)
            .map { WorkoutGain(it.name, "+${workoutKgText(it.deltaKg ?: 0.0)} kg") }
    } else {
        emptyList<WorkoutGain>()
    }
    // El ejercicio pedido si existe en el entreno; si no, el de la mayor ganancia (o el primero).
    val exerciseId = options.exerciseId?.takeIf { id -> data.lifts.any { it.exerciseId == id } } ?: data.defaultExerciseId
    val lift = exerciseId?.let { id -> data.lifts.firstOrNull { it.exerciseId == id } }
    val history = lift?.history.orEmpty()
    val best = lift?.best
    val last = history.lastOrNull()
    // Ganancia del historial: último e1RM menos el primero; solo se muestra si al redondear a la décima sigue siendo positiva.
    val diff = if (history.size >= 2) history.last().estimated1Rm - history.first().estimated1Rm else 0.0
    return WorkoutStory(
        dateLine = workoutDateLine(presentation.today, presentation.weekNumber),
        title = data.sessionName,
        meta = presentation.program?.name,
        stats = stats,
        gains = gains,
        records = presentation.records,
        exerciseName = lift?.name,
        exerciseE1rm = last?.let { workoutKgText(it.estimated1Rm) },
        exerciseDelta = if (diff > 0 && workoutTenths(diff) > 0) "+${workoutKgText(diff)} kg" else null,
        exerciseSessions = history.size,
        exercisePoints = history.takeLast(8).map { it.estimated1Rm },
        bestSet = best?.let { "${workoutKgText(it.weight)} kg × ${it.reps}" },
        bestEffort = best?.rir?.let { "RIR $it" },
        photoPath = presentation.photos.firstOrNull()?.path,
    )
}

/** Días de la semana y meses en español en mayúsculas, con tablas propias para no depender de la configuración regional. */
private val workoutDaysEs = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")
private val workoutMonthsEs = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")

/** Fecha de la cabecera: «MIÉ 28 OCT», y «· SEMANA N» al final si hay semana. */
private fun workoutDateLine(today: LocalDate, week: Int?): String {
    val date = "${workoutDaysEs[today.dayOfWeek.value - 1]} ${today.dayOfMonth} ${workoutMonthsEs[today.monthValue - 1]}"
    return if (week != null) "$date · SEMANA $week" else date
}

/** Décimas de kilo de [kg], redondeadas: 142.35 → 1424. */
private fun workoutTenths(kg: Double): Long = (kg * 10).roundToLong()

/** Kilos con coma decimal a la décima y sin «,0»: 142.5 → «142,5», 120 → «120», 142.3 → «142,3». */
private fun workoutKgText(kg: Double): String {
    val tenths = workoutTenths(kg)
    val sign = if (tenths < 0) "-" else ""
    val whole = abs(tenths) / 10
    val tenth = abs(tenths) % 10
    return if (tenth == 0L) "$sign$whole" else "$sign$whole,$tenth"
}

/** Un número con una cifra decimal y coma, siempre: 1.6 → «1,6», 2 → «2,0». */
private fun workoutDecimalComma(value: Double): String {
    val tenths = workoutTenths(value)
    val sign = if (tenths < 0) "-" else ""
    return "$sign${abs(tenths) / 10},${abs(tenths) % 10}"
}

/** Entero con puntos de miles: 6240.4 → «6.240», 980 → «980». */
private fun workoutThousands(value: Double): String {
    val whole = value.roundToLong()
    val digits = abs(whole).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (whole < 0) "-$digits" else digits
}

/** Texto de las marcas nuevas: «1 marca nueva», «2 marcas nuevas». */
private fun workoutRecordsText(records: Int): String = if (records == 1) "1 marca nueva" else "$records marcas nuevas"

/** Pie de la historia de ejercicio: la fecha sin la semana, como «MIÉ 28 OCT». */
private fun workoutCaption(dateLine: String): String = dateLine.substringBefore(" · ")

// ------------------------------------------------------------------------------------------------------------- dibujo

/**
 * Pinta la historia [story] con la plantilla [template] en su lienzo de 1080 × 1920. [photo] solo se usa en Fotos (sin ella, Fotos
 * pinta la portada de relieve); [progress] es el avance de la portada (1 = completa).
 */
internal fun DrawScope.drawWorkoutStory(
    story: WorkoutStory,
    template: WorkoutStoryTemplate,
    measurer: TextMeasurer,
    photo: ImageBitmap? = null,
    progress: Float = 1f,
) {
    when (template) {
        WorkoutStoryTemplate.GENERAL -> drawWorkoutGeneral(story, measurer, progress)
        WorkoutStoryTemplate.EXERCISE -> drawWorkoutExercise(story, measurer, progress)
        WorkoutStoryTemplate.PHOTOS -> drawWorkoutPhotos(story, measurer, photo, progress)
    }
}

/** Pinta la historia con la plantilla [template] en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderWorkoutStoryBitmap(
    context: Context,
    story: WorkoutStory,
    template: WorkoutStoryTemplate,
    photo: ImageBitmap? = null,
): android.graphics.Bitmap = renderStory(context) { drawWorkoutStory(story, template, it, photo) }

// ------------------------------------------------------------------------------------------------------- medidas (px)

/** Pie de las historias general y de fotos. */
private const val WORKOUT_FOOT = "Entreno de hoy"

/** Separación entre el bloque de contenido y el pie. */
private const val WORKOUT_FOOT_GAP = 75.6f

/** Pie de la historia de fotos: su borde inferior queda a 1585 px, como en la referencia, no al fondo del lienzo. */
private const val WORKOUT_PHOTO_FOOT_BOTTOM = 1585f

/** Separación entre la línea de cifras de fotos y el pie, y entre el título y esa línea. */
private const val WORKOUT_PHOTO_STATS_GAP = 54f
private const val WORKOUT_PHOTO_TITLE_GAP = 80f

/** Separación entre el bloque de contenido y la meta, de la meta al nombre y del nombre a la fecha. */
private const val WORKOUT_META_GAP = 54f
private const val WORKOUT_NAME_META_GAP = 21.6f
private const val WORKOUT_EYEBROW_NAME_GAP = 27f

/** Tamaño del nombre de la sesión (y de su línea de interlineado). */
private const val WORKOUT_NAME_SIZE = 113.4f

/** Altura de la línea de cada cifra, separación con su etiqueta y con su unidad, y línea base dentro de la celda. */
private const val WORKOUT_STAT_LINE = 101.5f
private const val WORKOUT_STAT_LABEL_GAP = 15f
private const val WORKOUT_STAT_UNIT_GAP = 13f
private const val WORKOUT_STAT_BASELINE = 84.5f

/** Separación entre filas de la rejilla, separación mínima entre sus columnas y entre la rejilla y las ganancias. */
private const val WORKOUT_GRID_GAP = 43.2f
private const val WORKOUT_GRID_MIN_GAP = 54f

/** Separación entre el bloque de contenido y la cápsula de marca. */
private const val WORKOUT_BADGE_GAP = 51.2f

/** Relleno de la cápsula de marca y tamaño y separación de su icono. */
private const val WORKOUT_BADGE_PAD_Y = 15f
private const val WORKOUT_BADGE_PAD_X = 34.6f
private const val WORKOUT_BADGE_ICON = 38.9f
private const val WORKOUT_BADGE_ICON_GAP = 17f

/** Separación entre filas de texto, relleno superior de cada fila, hueco mínimo entre el nombre y el valor, y grosor de la regla. */
private const val WORKOUT_ROW_GAP = 25.9f
private const val WORKOUT_ROW_PAD = 23.8f
private const val WORKOUT_ROW_NAME_GAP = 40f
private const val WORKOUT_RULE = 1f

/** Puntos naranja que separan las cifras de la historia de fotos: radio, hueco a cada lado y altura sobre la línea base. */
private const val WORKOUT_DOT_RADIUS = 7f
private const val WORKOUT_DOT_GAP = 27f
private const val WORKOUT_DOT_LIFT = 24f

/** Hasta dónde llega la portada de cada plantilla (Fotos, solo cuando no hay foto). */
private const val WORKOUT_GENERAL_ART_BOTTOM = 700f
private const val WORKOUT_EXERCISE_ART_BOTTOM = 1036.8f
private const val WORKOUT_PHOTOS_ART_BOTTOM = 1200f

/** Donde empieza el degradado a negro de la foto: el 55 % de la altura (1056 px). */
private const val WORKOUT_FADE_TOP = 1056f

/** Ejercicio: separación entre la cifra y las filas, entre la cifra y su unidad, y la gráfica sobre la cifra. */
private const val WORKOUT_EXERCISE_BLOCK_GAP = 200f
private const val WORKOUT_BIG_UNIT_GAP = 21.6f
private const val WORKOUT_CHART_TOP = 300f
private const val WORKOUT_CHART_GAP = 150f
private const val WORKOUT_CHART_MIN = 160f

/** Gráfica del e1RM: margen vertical de los puntos y márgenes izquierdo y derecho de la caja. */
private const val WORKOUT_CHART_PAD = 60f
private const val WORKOUT_CHART_INSET_LEFT = 20f
private const val WORKOUT_CHART_INSET_RIGHT = 48f

// ------------------------------------------------------------------------------------------------------------- estilos

/**
 * Estilos de la historia de entreno. Están dentro de un objeto para que cargar el modelo no toque [WizardFonts]: así la prueba del
 * modelo puede ser pura. Se cargan la primera vez que se dibuja.
 */
private object WorkoutType {
    /** Eyebrow: Inter SemiBold 38.9 px con 0.25 em de interletraje (en mayúsculas). */
    val eyebrow = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.9f.sp,
        letterSpacing = 0.25f.em,
    )

    /** Nombre de la sesión: Syne ExtraBold de [size] px con interlineado igual al tamaño. */
    fun name(size: Float): TextStyle = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        lineHeight = size.sp,
        letterSpacing = (-0.04f).em,
        lineBreak = LineBreak.Heading,
    )

    /** Meta del programa y ganancia del ejercicio: Inter Medium 41 px. */
    val meta = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 41f.sp)

    /** Nombre de fila (ganancia, serie, esfuerzo): Inter Medium 38.9 px. */
    val rowName = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 38.9f.sp)

    /** Valor de fila (ganancia, mejor serie, esfuerzo): Syne ExtraBold 56 px con −0.03 em. */
    val rowValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56f.sp,
        letterSpacing = (-0.03f).em,
    )

    /** Cifra de la rejilla: Syne ExtraBold 101.5 px con interlineado de 101.5 px, −0.04 em y cifras tabulares. */
    val statValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 101.5f.sp,
        lineHeight = 101.5f.sp,
        letterSpacing = (-0.04f).em,
        fontFeatureSettings = "tnum",
    )

    /** Unidad de la cifra de la rejilla: Inter SemiBold 38.9 px. */
    val statUnit = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 38.9f.sp)

    /** Etiqueta de la cifra de la rejilla: Inter SemiBold 30.2 px con 0.16 em de interletraje (en mayúsculas). */
    val statLabel = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.2f.sp,
        letterSpacing = 0.16f.em,
    )

    /** Texto de la cápsula de marca: Inter SemiBold 34.6 px. */
    val badge = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 34.6f.sp)

    /** Cifra grande de la gráfica de ejercicio: Syne ExtraBold 205 px con interlineado de 184.5 px y −0.05 em. */
    val bigValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 205f.sp,
        lineHeight = 184.5f.sp,
        letterSpacing = (-0.05f).em,
        fontFeatureSettings = "tnum",
    )

    /** Unidad de la cifra grande («kg»): Inter SemiBold 56 px. */
    val bigUnit = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 56f.sp)

    /** Cifra de la línea de fotos: Syne ExtraBold 64.8 px, o [scale] veces eso si la línea no cabe. */
    fun photoValue(scale: Float): TextStyle = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = (64.8f * scale).sp,
        fontFeatureSettings = "tnum",
    )

    /** Unidad de la línea de fotos: Inter SemiBold 36.7 px, o [scale] veces eso si la línea no cabe. */
    fun photoUnit(scale: Float): TextStyle = TextStyle(
        fontFamily = WizardFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = (36.7f * scale).sp,
    )
}

// ---------------------------------------------------------------------------------------------------------- piezas

/** La cabecera medida: [eyebrow] (fecha o ejercicio), [name] de la sesión con su [nameLift] y la [meta] si hay. */
private class WorkoutHeader(
    val eyebrow: TextLayoutResult,
    val name: TextLayoutResult,
    val nameLift: Float,
    val meta: TextLayoutResult?,
)

/** Mide la cabecera. El nombre usa el interlineado de [WORKOUT_NAME_SIZE] y sube la mitad del espacio sobrante, como drawStoryHeader. */
private fun TextMeasurer.workoutHeader(eyebrow: String, name: String, meta: String?): WorkoutHeader {
    val column = StoryCanvas.COLUMN_WIDTH.toInt()
    val style = WorkoutType.name(WORKOUT_NAME_SIZE)
    val nameLayout = storyText(name, style, width = column, maxLines = 3, ellipsis = true)
    val natural = storyText(name, style.copy(lineHeight = TextUnit.Unspecified), width = column, maxLines = 3, ellipsis = true)
    val naturalLine = natural.size.height.toFloat() / natural.lineCount.coerceAtLeast(1)
    return WorkoutHeader(
        eyebrow = storyText(
            eyebrow.uppercase(Locale.forLanguageTag("es")),
            WorkoutType.eyebrow,
            width = column,
            maxLines = 1,
            ellipsis = true,
        ),
        name = nameLayout,
        nameLift = (WORKOUT_NAME_SIZE - naturalLine) / 2f,
        meta = meta?.let { storyText(it, WorkoutType.meta, width = column, maxLines = 1, ellipsis = true) },
    )
}

/**
 * Pinta la cabecera encima de un bloque de contenido que empieza en [contentTop]: la meta (o el nombre, si no hay) acaba [gap] px por
 * encima.
 */
private fun DrawScope.drawWorkoutHeader(header: WorkoutHeader, contentTop: Float, gap: Float = WORKOUT_META_GAP) {
    var bottom = contentTop - gap
    header.meta?.let { meta ->
        val metaTop = bottom - meta.size.height
        drawText(meta, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, metaTop))
        bottom = metaTop - WORKOUT_NAME_META_GAP
    }
    val nameTop = bottom - header.name.lineCount * WORKOUT_NAME_SIZE
    val eyebrowTop = nameTop - WORKOUT_EYEBROW_NAME_GAP - header.eyebrow.size.height
    drawText(header.eyebrow, color = StoryCanvas.Faint, topLeft = Offset(StoryCanvas.LEFT, eyebrowTop))
    drawText(header.name, color = StoryCanvas.Ink, topLeft = Offset(StoryCanvas.LEFT, nameTop + header.nameLift))
}

/** Pone [layout] con su línea base en [baseline] y su borde izquierdo en [x]. */
private fun DrawScope.drawWorkoutOnBaseline(layout: TextLayoutResult, color: Color, x: Float, baseline: Float) {
    drawText(layout, color = color, topLeft = Offset(x, baseline - layout.firstBaseline))
}

/** Regla de 1 px a todo el ancho de la columna, con su parte superior en [top] y la opacidad [alpha]. */
private fun DrawScope.drawWorkoutRule(top: Float, alpha: Float) {
    drawRect(Color.White, topLeft = Offset(StoryCanvas.LEFT, top), size = Size(StoryCanvas.COLUMN_WIDTH, WORKOUT_RULE), alpha = alpha)
}

/** Alto de una pila de filas de texto [heights]: cada fila con su regla y su relleno, y [WORKOUT_ROW_GAP] entre filas. */
private fun workoutStackHeight(heights: List<Float>): Float =
    heights.fold(0f) { acc, height -> acc + WORKOUT_RULE + WORKOUT_ROW_PAD + height } +
        WORKOUT_ROW_GAP * (heights.size - 1).coerceAtLeast(0)

/** Una cifra de la rejilla ya medida: [value] con su [unit] (si hay) y la [label] en mayúsculas debajo. */
private class WorkoutStatCell(val value: TextLayoutResult, val unit: TextLayoutResult?, val label: TextLayoutResult) {
    /** Ancho de la celda: la cifra con su unidad, o la etiqueta si es más ancha. */
    val width: Float = max(
        value.size.width + (unit?.let { WORKOUT_STAT_UNIT_GAP + it.size.width } ?: 0f),
        label.size.width.toFloat(),
    )

    /** Alto de la celda: la línea de la cifra, la separación y la etiqueta. */
    val height: Float = WORKOUT_STAT_LINE + WORKOUT_STAT_LABEL_GAP + label.size.height
}

/** Mide una cifra de la rejilla. */
private fun TextMeasurer.workoutStatCell(stat: WorkoutStat): WorkoutStatCell = WorkoutStatCell(
    value = storyText(stat.value, WorkoutType.statValue),
    unit = stat.unit?.let { storyText(it, WorkoutType.statUnit) },
    label = storyText(stat.label.uppercase(Locale.forLanguageTag("es")), WorkoutType.statLabel),
)

/** Alto de la rejilla de dos columnas: filas de la altura de la celda más alta, separadas por [WORKOUT_GRID_GAP]. */
private fun workoutGridHeight(cells: List<WorkoutStatCell>): Float {
    if (cells.isEmpty()) return 0f
    val rows = (cells.size + 1) / 2
    return rows * cells.maxOf { it.height } + (rows - 1) * WORKOUT_GRID_GAP
}

/**
 * X de la segunda columna: pegada al margen derecho, como en la referencia («6.240 kg» acaba en la columna), y nunca más cerca de la
 * primera que [WORKOUT_GRID_MIN_GAP].
 */
private fun workoutGridSecondX(cells: List<WorkoutStatCell>): Float {
    val first = cells.filterIndexed { index, _ -> index % 2 == 0 }.maxOfOrNull { it.width } ?: 0f
    val second = cells.filterIndexed { index, _ -> index % 2 == 1 }.maxOfOrNull { it.width } ?: 0f
    return max(StoryCanvas.RIGHT - second, StoryCanvas.LEFT + first + WORKOUT_GRID_MIN_GAP)
}

/** Rejilla de cifras: cada cifra con su unidad y su etiqueta; la primera columna en el margen y la segunda a la derecha. */
private fun DrawScope.drawWorkoutGrid(cells: List<WorkoutStatCell>, top: Float) {
    val rowHeight = cells.maxOfOrNull { it.height } ?: return
    val second = workoutGridSecondX(cells)
    cells.forEachIndexed { index, cell ->
        val x = if (index % 2 == 0) StoryCanvas.LEFT else second
        val y = top + (index / 2) * (rowHeight + WORKOUT_GRID_GAP)
        drawWorkoutOnBaseline(cell.value, StoryCanvas.Ink, x, y + WORKOUT_STAT_BASELINE)
        cell.unit?.let { unit ->
            drawWorkoutOnBaseline(unit, StoryCanvas.Muted, x + cell.value.size.width + WORKOUT_STAT_UNIT_GAP, y + WORKOUT_STAT_BASELINE)
        }
        drawText(cell.label, color = StoryCanvas.Faint, topLeft = Offset(x, y + WORKOUT_STAT_LINE + WORKOUT_STAT_LABEL_GAP))
    }
}

/** Una fila de texto medida: [name] a la izquierda (gris) y [value] a la derecha (tinta). */
private class WorkoutRow(val name: TextLayoutResult, val value: TextLayoutResult) {
    /** Alto de la fila: lo mayor de sus dos textos. */
    val height: Float = max(name.size.height.toFloat(), value.size.height.toFloat())
}

/** Mide una fila; el nombre tiene el ancho que deja libre el valor y se corta con puntos suspensivos. */
private fun TextMeasurer.workoutRow(label: String, value: String): WorkoutRow {
    val valueLayout = storyText(value, WorkoutType.rowValue)
    val nameWidth = (StoryCanvas.COLUMN_WIDTH - valueLayout.size.width - WORKOUT_ROW_NAME_GAP).toInt().coerceAtLeast(120)
    return WorkoutRow(
        name = storyText(label, WorkoutType.rowName, width = nameWidth, maxLines = 1, ellipsis = true),
        value = valueLayout,
    )
}

/** Filas de texto: regla al 8 %, nombre a la izquierda y valor a la derecha, centrados en vertical. */
private fun DrawScope.drawWorkoutRows(rows: List<WorkoutRow>, top: Float) {
    var y = top
    for (row in rows) {
        drawWorkoutRule(y, alpha = 0.08f)
        val middle = y + WORKOUT_RULE + WORKOUT_ROW_PAD + row.height / 2f
        drawText(row.name, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, middle - row.name.size.height / 2f))
        drawText(
            row.value,
            color = StoryCanvas.Ink,
            topLeft = Offset(StoryCanvas.RIGHT - row.value.size.width, middle - row.value.size.height / 2f),
        )
        y += WORKOUT_RULE + WORKOUT_ROW_PAD + row.height + WORKOUT_ROW_GAP
    }
}

/** Cápsula de marca medida: su texto. */
private class WorkoutBadge(val text: TextLayoutResult) {
    /** Alto: lo mayor entre el icono y el texto, más el relleno de arriba y abajo. */
    val height: Float = max(WORKOUT_BADGE_ICON, text.size.height.toFloat()) + 2 * WORKOUT_BADGE_PAD_Y

    /** Ancho: relleno, icono, separación, texto y relleno final. */
    val width: Float = 2 * WORKOUT_BADGE_PAD_X + WORKOUT_BADGE_ICON + WORKOUT_BADGE_ICON_GAP + text.size.width
}

/** Cápsula de marca: borde de 1 px en acento al 44 %, destello de cuatro puntas y el texto en acento. */
private fun DrawScope.drawWorkoutBadge(badge: WorkoutBadge, top: Float) {
    val left = StoryCanvas.LEFT
    drawRoundRect(
        color = StoryCanvas.Accent,
        topLeft = Offset(left + 0.5f, top + 0.5f),
        size = Size(badge.width - 1f, badge.height - 1f),
        cornerRadius = CornerRadius(badge.height / 2f),
        style = Stroke(width = 1f),
        alpha = 0.44f,
    )
    // El icono es de 38.9 px; el destello ocupa 26 px (16 de sus 24 unidades), como en la historia del mes.
    val iconCenter = Offset(left + WORKOUT_BADGE_PAD_X + WORKOUT_BADGE_ICON / 2f, top + badge.height / 2f)
    drawPath(
        path = workoutSparkle(iconCenter, 13f),
        color = StoryCanvas.Accent,
        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawText(
        badge.text,
        color = StoryCanvas.Accent,
        topLeft = Offset(
            left + WORKOUT_BADGE_PAD_X + WORKOUT_BADGE_ICON + WORKOUT_BADGE_ICON_GAP,
            top + (badge.height - badge.text.size.height) / 2f,
        ),
    )
}

/** Destello de cuatro puntas con los lados cóncavos, de radio [r] alrededor de [center]. */
private fun workoutSparkle(center: Offset, r: Float): Path {
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

/** La línea de ganancia del ejercicio: «+7,5 kg» en acento ([delta], si hay) y «en 6 sesiones» en gris ([rest]). */
private class WorkoutDeltaLine(val delta: TextLayoutResult?, val rest: TextLayoutResult?) {
    /** Alto de la línea: el mayor de sus dos trozos. */
    val height: Float = maxOf(delta?.size?.height ?: 0, rest?.size?.height ?: 0).toFloat()
}

/** Mide la línea de ganancia: la del historial y, si hay sesiones, «en N sesiones» (en singular con una). */
private fun TextMeasurer.workoutDeltaLine(delta: String?, sessions: Int): WorkoutDeltaLine? {
    val sessionsText = when {
        sessions == 1 -> "en 1 sesión"
        sessions > 1 -> "en $sessions sesiones"
        else -> null
    }
    val deltaLayout = delta?.let { storyText(it, WorkoutType.meta) }
    val restText = if (deltaLayout != null) sessionsText?.let { " $it" } else sessionsText
    if (deltaLayout == null && restText == null) return null
    return WorkoutDeltaLine(deltaLayout, restText?.let { storyText(it, WorkoutType.meta) })
}

/** Alto del bloque de la cifra de ejercicio: la etiqueta y, si hay, la cifra y la línea de ganancia con sus separaciones. */
private fun workoutNumberBlockHeight(eyebrow: TextLayoutResult, value: TextLayoutResult?, line: WorkoutDeltaLine?): Float {
    var height = eyebrow.size.height.toFloat()
    if (value != null) height += WORKOUT_EYEBROW_NAME_GAP + value.size.height
    if (line != null) height += WORKOUT_NAME_META_GAP + line.height
    return height
}

/** Pinta el bloque de la cifra de ejercicio desde [top]: la etiqueta, la cifra con su unidad y la línea de ganancia. */
private fun DrawScope.drawWorkoutNumberBlock(
    eyebrow: TextLayoutResult,
    value: TextLayoutResult?,
    unit: TextLayoutResult?,
    line: WorkoutDeltaLine?,
    top: Float,
) {
    var y = top
    drawText(eyebrow, color = StoryCanvas.Faint, topLeft = Offset(StoryCanvas.LEFT, y))
    y += eyebrow.size.height
    if (value != null) {
        y += WORKOUT_EYEBROW_NAME_GAP
        drawText(value, color = StoryCanvas.Ink, topLeft = Offset(StoryCanvas.LEFT, y))
        if (unit != null) {
            drawWorkoutOnBaseline(
                unit,
                StoryCanvas.Muted,
                StoryCanvas.LEFT + value.size.width + WORKOUT_BIG_UNIT_GAP,
                y + value.firstBaseline,
            )
        }
        y += value.size.height
    }
    if (line != null) {
        y += WORKOUT_NAME_META_GAP
        var x = StoryCanvas.LEFT
        line.delta?.let { delta ->
            drawText(delta, color = StoryCanvas.Accent, topLeft = Offset(x, y))
            x += delta.size.width
        }
        line.rest?.let { rest -> drawText(rest, color = StoryCanvas.Muted, topLeft = Offset(x, y)) }
    }
}

/**
 * Evolución del e1RM: una línea blanca con un punto hueco por sesión y el último en acento con su halo, un velo bajo la línea y cuatro
 * guías como en la referencia. Los puntos ocupan la caja entre [top] y [bottom] con margen arriba y abajo.
 */
private fun DrawScope.drawWorkoutChart(points: List<Double>, top: Float, bottom: Float) {
    val low = points.minOrNull() ?: return
    val high = points.maxOrNull() ?: return
    val left = StoryCanvas.LEFT + WORKOUT_CHART_INSET_LEFT
    val right = StoryCanvas.RIGHT - WORKOUT_CHART_INSET_RIGHT
    for (step in 0..3) drawWorkoutRule(top + (bottom - top) * step / 3f, alpha = 0.08f)

    val xs = points.indices.map { index -> left + index * (right - left) / (points.size - 1) }
    val span = high - low
    val ys = points.map { value ->
        if (span <= 0.0) {
            (top + bottom) / 2f
        } else {
            bottom - WORKOUT_CHART_PAD - ((value - low) / span).toFloat() * (bottom - top - 2 * WORKOUT_CHART_PAD)
        }
    }

    val line = Path()
    val area = Path()
    xs.forEachIndexed { index, x ->
        if (index == 0) {
            line.moveTo(x, ys[index])
            area.moveTo(x, ys[index])
        } else {
            line.lineTo(x, ys[index])
            area.lineTo(x, ys[index])
        }
    }
    area.lineTo(xs.last(), bottom)
    area.lineTo(xs.first(), bottom)
    area.close()
    drawPath(
        area,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0f)),
            startY = top,
            endY = bottom,
        ),
    )
    drawPath(line, color = StoryCanvas.Ink, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    xs.forEachIndexed { index, x ->
        val center = Offset(x, ys[index])
        if (index == xs.lastIndex) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to StoryCanvas.Accent.copy(alpha = 0.5f),
                    1f to StoryCanvas.Accent.copy(alpha = 0f),
                    center = center,
                    radius = 44f,
                ),
                radius = 44f,
                center = center,
            )
            drawCircle(StoryCanvas.Accent, radius = 16f, center = center)
        } else {
            drawCircle(StoryCanvas.Background, radius = 12f, center = center)
            drawCircle(StoryCanvas.Ink, radius = 12f, center = center, style = Stroke(width = 5f))
        }
    }
}

// ------------------------------------------------------------------------------------------------------- plantillas

/** General: portada de ondas, fecha, título con el programa, rejilla de cifras, ganancias, marcas nuevas y pie. */
private fun DrawScope.drawWorkoutGeneral(story: WorkoutStory, measurer: TextMeasurer, progress: Float) {
    drawStoryBackdrop(
        cover = LineCoverSpec(LineCoverStyle.ONDAS, LineCoverBackground.CARBON, seed = 4),
        measurer = measurer,
        progress = progress,
        artBottom = WORKOUT_GENERAL_ART_BOTTOM,
    )
    val cells = story.stats.map { measurer.workoutStatCell(it) }
    val rows = story.gains.map { measurer.workoutRow(it.name, it.delta) }
    val badge = story.records.takeIf { it > 0 }?.let { WorkoutBadge(measurer.storyText(workoutRecordsText(it), WorkoutType.badge)) }
    val gridHeight = workoutGridHeight(cells)
    val rowsHeight = if (rows.isEmpty()) 0f else WORKOUT_GRID_GAP + workoutStackHeight(rows.map { it.height })
    val badgeHeight = badge?.let { WORKOUT_BADGE_GAP + it.height } ?: 0f
    val header = measurer.workoutHeader(story.dateLine, story.title, story.meta)
    val footTop = drawStoryFoot(measurer, WORKOUT_FOOT)
    val contentTop = footTop - WORKOUT_FOOT_GAP - (gridHeight + rowsHeight + badgeHeight)
    drawWorkoutHeader(header, contentTop)
    drawWorkoutGrid(cells, contentTop)
    drawWorkoutRows(rows, contentTop + gridHeight + WORKOUT_GRID_GAP)
    badge?.let { drawWorkoutBadge(it, contentTop + gridHeight + rowsHeight + WORKOUT_BADGE_GAP) }
}

/**
 * Ejercicio: portada de relieve, gráfica del e1RM, cifra grande con su ganancia y las filas de la mejor serie y el esfuerzo. Sin
 * ejercicio con datos, solo la fecha, el título y el pie.
 */
private fun DrawScope.drawWorkoutExercise(story: WorkoutStory, measurer: TextMeasurer, progress: Float) {
    drawStoryBackdrop(
        cover = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, seed = 5),
        measurer = measurer,
        progress = progress,
        artBottom = WORKOUT_EXERCISE_ART_BOTTOM,
    )
    // El relieve queda de fondo, apagado como en el prototipo: la gráfica y la cifra mandan.
    drawRect(StoryCanvas.Background, size = Size(StoryCanvas.WIDTH.toFloat(), WORKOUT_EXERCISE_ART_BOTTOM), alpha = 0.55f)
    val footTop = drawStoryFoot(measurer, workoutCaption(story.dateLine))
    val name = story.exerciseName
    if (name == null) {
        drawWorkoutHeader(measurer.workoutHeader(story.dateLine, story.title, meta = null), contentTop = footTop - WORKOUT_FOOT_GAP)
        return
    }
    val rows = listOfNotNull(
        story.bestSet?.let { measurer.workoutRow("Mejor serie de hoy", it) },
        story.bestEffort?.let { measurer.workoutRow("Esfuerzo", it) },
    )
    val rowsTop = footTop - WORKOUT_FOOT_GAP - workoutStackHeight(rows.map { it.height })

    val eyebrow = measurer.storyText(
        "$name · E1RM".uppercase(Locale.forLanguageTag("es")),
        WorkoutType.eyebrow,
        width = StoryCanvas.COLUMN_WIDTH.toInt(),
        maxLines = 1,
        ellipsis = true,
    )
    val value = story.exerciseE1rm?.let { measurer.storyText(it, WorkoutType.bigValue) }
    val unit = value?.let { measurer.storyText("kg", WorkoutType.bigUnit) }
    val line = measurer.workoutDeltaLine(story.exerciseDelta, story.exerciseSessions)
    val blockTop = rowsTop - WORKOUT_EXERCISE_BLOCK_GAP - workoutNumberBlockHeight(eyebrow, value, line)

    drawWorkoutNumberBlock(eyebrow, value, unit, line, blockTop)
    drawWorkoutRows(rows, rowsTop)
    val chartBottom = blockTop - WORKOUT_CHART_GAP
    if (story.exercisePoints.size >= 2 && chartBottom - WORKOUT_CHART_TOP >= WORKOUT_CHART_MIN) {
        drawWorkoutChart(story.exercisePoints, top = WORKOUT_CHART_TOP, bottom = chartBottom)
    }
}

/**
 * Fotos: la foto a pantalla completa con degradado a negro en el 45 % inferior (sin foto, la portada de relieve con ese mismo
 * degradado), y abajo la fecha, el título y la línea de cifras separadas por puntos naranja.
 */
private fun DrawScope.drawWorkoutPhotos(story: WorkoutStory, measurer: TextMeasurer, photo: ImageBitmap?, progress: Float) {
    if (photo != null) {
        drawWorkoutPhoto(photo)
    } else {
        // Sin foto, el relieve ocupa la parte de arriba y se funde a negro antes del texto, como en el prototipo.
        drawStoryBackdrop(
            cover = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, seed = 5),
            measurer = measurer,
            progress = progress,
            artBottom = WORKOUT_PHOTOS_ART_BOTTOM,
        )
    }
    // Degradado a negro en el 45 % inferior, con foto o sin ella: el texto nunca pisa líneas ni una foto clara.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(StoryCanvas.Background.copy(alpha = 0f), StoryCanvas.Background),
            startY = WORKOUT_FADE_TOP,
            endY = StoryCanvas.HEIGHT.toFloat(),
        ),
        topLeft = Offset(0f, WORKOUT_FADE_TOP),
        size = Size(StoryCanvas.WIDTH.toFloat(), StoryCanvas.HEIGHT - WORKOUT_FADE_TOP),
    )
    val footTop = drawStoryFoot(measurer, WORKOUT_FOOT, bottom = WORKOUT_PHOTO_FOOT_BOTTOM)
    // La línea de Fotos lleva tres cifras como el prototipo (duración, volumen y series): el RIR medio queda para Sesión.
    val photoStats = story.stats.filterNot { it.label == "RIR medio" }
    val natural = measurer.workoutPhotoLine(photoStats, 1f)
    val line = if (natural.width > StoryCanvas.COLUMN_WIDTH) {
        measurer.workoutPhotoLine(photoStats, StoryCanvas.COLUMN_WIDTH / natural.width)
    } else {
        natural
    }
    val statsTop = footTop - WORKOUT_PHOTO_STATS_GAP - line.height
    drawWorkoutPhotoLine(line, top = statsTop)
    drawWorkoutHeader(
        measurer.workoutHeader(story.dateLine, story.title, meta = null),
        contentTop = statsTop,
        gap = WORKOUT_PHOTO_TITLE_GAP,
    )
}

/** Foto a pantalla completa con recorte centrado (ContentScale.Crop): escala para cubrir el lienzo y recorta lo que sobra. */
private fun DrawScope.drawWorkoutPhoto(photo: ImageBitmap) {
    val scale = max(StoryCanvas.WIDTH.toFloat() / photo.width, StoryCanvas.HEIGHT.toFloat() / photo.height)
    val srcWidth = min(photo.width, (StoryCanvas.WIDTH / scale).roundToInt()).coerceAtLeast(1)
    val srcHeight = min(photo.height, (StoryCanvas.HEIGHT / scale).roundToInt()).coerceAtLeast(1)
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - srcWidth) / 2, (photo.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(StoryCanvas.WIDTH, StoryCanvas.HEIGHT),
    )
}

/** Una cifra de la línea de fotos medida: el valor (Syne) y su unidad (Inter) sobre la misma línea base. */
private class WorkoutPhotoStat(val value: TextLayoutResult, val unit: TextLayoutResult) {
    /** Ancho de la cifra con su unidad. */
    val width: Float = value.size.width + WORKOUT_STAT_UNIT_GAP + unit.size.width
}

/** La línea «48 min • 6.240 kg • 17 series» medida: sus cifras y sus alturas por encima y por debajo de la línea base. */
private class WorkoutPhotoLine(val stats: List<WorkoutPhotoStat>) {
    /** Ancho total: las cifras y, entre cada par, un punto con su hueco a cada lado. */
    val width: Float = stats.fold(0f) { acc, stat -> acc + stat.width } +
        max(0, stats.size - 1) * (2 * WORKOUT_DOT_GAP + 2 * WORKOUT_DOT_RADIUS)

    /** Lo que sube la línea sobre su base: lo más alto de sus textos. */
    val ascent: Float = stats.fold(0f) { acc, stat -> max(acc, max(stat.value.firstBaseline, stat.unit.firstBaseline)) }

    /** Lo que baja la línea bajo su base: lo más bajo de sus textos. */
    val descent: Float = stats.fold(0f) { acc, stat ->
        max(acc, max(stat.value.size.height - stat.value.firstBaseline, stat.unit.size.height - stat.unit.firstBaseline))
    }

    /** Alto de la línea. */
    val height: Float = ascent + descent
}

/** Mide la línea de fotos con [scale] sobre el tamaño de sus cifras (1 para la medida natural). */
private fun TextMeasurer.workoutPhotoLine(stats: List<WorkoutStat>, scale: Float): WorkoutPhotoLine = WorkoutPhotoLine(
    stats.map { stat ->
        WorkoutPhotoStat(
            value = storyText(stat.value, WorkoutType.photoValue(scale)),
            unit = storyText(stat.unit ?: stat.label.lowercase(Locale.forLanguageTag("es")), WorkoutType.photoUnit(scale)),
        )
    },
)

/** Pinta la línea de fotos con su borde superior en [top]: las cifras sobre una misma base y un punto naranja entre cada par. */
private fun DrawScope.drawWorkoutPhotoLine(line: WorkoutPhotoLine, top: Float) {
    val baseline = top + line.ascent
    var x = StoryCanvas.LEFT
    line.stats.forEachIndexed { index, stat ->
        if (index > 0) {
            x += WORKOUT_DOT_GAP
            drawCircle(
                StoryCanvas.Accent,
                radius = WORKOUT_DOT_RADIUS,
                center = Offset(x + WORKOUT_DOT_RADIUS, baseline - WORKOUT_DOT_LIFT),
            )
            x += 2 * WORKOUT_DOT_RADIUS + WORKOUT_DOT_GAP
        }
        drawWorkoutOnBaseline(stat.value, StoryCanvas.Ink, x, baseline)
        x += stat.value.size.width + WORKOUT_STAT_UNIT_GAP
        drawWorkoutOnBaseline(stat.unit, StoryCanvas.Muted, x, baseline)
        x += stat.unit.size.width
    }
}
