package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import kotlin.math.max

/** Plantillas de la historia de un programa; [label] es el nombre que ve quien la elige. */
internal enum class ProgramStoryTemplate(val label: String) {
    /** Los días de la semana, con su sesión y su carga. */
    SEMANA("Semana"),

    /** Los bloques del programa, con una pastilla por semana coloreada por fase. */
    BLOQUES("Bloques"),

    /** Lo ganado por ejercicio en el periodo. */
    PROGRESO("Progreso"),
}

/** Un día de la semana: [letter] (L, M…), [label] de la sesión (null = descanso) y [load] de 0 a 1. */
internal data class StoryDayRow(val letter: String, val label: String?, val load: Float)

/** Un bloque del programa: [name] a la izquierda y [detail] («3 sem · Doble progresión») a la derecha. */
internal data class StoryBlockRow(val name: String, val detail: String)

/** Un ejercicio con lo ganado: [name] a la izquierda y [gain] («+7,5 kg») a la derecha. */
internal data class StoryLiftRow(val name: String, val gain: String)

/** Cuerpo de la historia bajo la cabecera. [meta] es la línea de resumen que va bajo el nombre del programa. */
internal sealed interface ProgramStoryContent {
    /** Línea de resumen que va bajo el nombre del programa. */
    val meta: String

    /** Semana: una fila por día, de arriba abajo. */
    data class Week(override val meta: String, val days: List<StoryDayRow>) : ProgramStoryContent

    /** Bloques: una pastilla por cada color de [weekColors] (una por semana) y una fila por bloque. */
    data class Blocks(
        override val meta: String,
        val weekColors: List<Color>,
        val blocks: List<StoryBlockRow>,
    ) : ProgramStoryContent

    /** Progreso: lo ganado por ejercicio. */
    data class Progress(override val meta: String, val lifts: List<StoryLiftRow>) : ProgramStoryContent
}

/** Una historia completa: [name] y [eyebrow] de la cabecera, la portada [cover], el [content] y el texto del pie [footer]. */
internal data class ProgramStory(
    val name: String,
    val eyebrow: String,
    val cover: LineCoverSpec,
    val content: ProgramStoryContent,
    val footer: String = "Mi programa de entreno",
)

/** Línea divisoria y de borde: 1 px. */
private const val RULE = 1f

/** Separación vertical entre elementos del bloque de contenido. */
private const val ITEM_GAP = 25.9f

/** Relleno superior bajo la línea de un bloque o de un ejercicio. */
private const val ITEM_PAD = 23.8f

/** Relleno vertical de cada fila de día. */
private const val ROW_PAD = 18.4f

/** Donde empieza el nombre de la sesión: margen + columna de la letra (64.8) + separación (21.6). */
private const val NAME_X = 162f

/** Ancho del nombre de la sesión: hasta la zona de carga menos su separación de 21.6 px (496.8, redondeado hacia abajo). */
private const val DAY_NAME_WIDTH = 496

/** Ancho de la zona de carga: una carga de 1 ocupa 324 px que terminan en el borde derecho. */
private const val ZONE_WIDTH = 324f

/** Radio del círculo que marca el extremo de la carga (diámetro 25.9 px). */
private const val DOT_RADIUS = 12.95f

/** Alto de cada pastilla de fase. */
private const val PILL_HEIGHT = 34.6f

/** Separación entre pastillas de fase. */
private const val PILL_GAP = 10.8f

/** Radio de las esquinas de las pastillas de fase. */
private const val PILL_RADIUS = 17.3f

/** Letra del día: Syne ExtraBold 41 px. */
private val dayLetterStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 41f.sp,
)

/** Nombre de la sesión: Inter Medium 36.7 px. */
private val dayNameStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 36.7f.sp,
)

/** Nombre del bloque: Inter SemiBold 36.7 px. */
private val blockNameStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 36.7f.sp,
)

/** Detalle del bloque: Inter Medium 36.7 px. */
private val blockDetailStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 36.7f.sp,
)

/** Nombre del ejercicio en Progreso: Inter Medium 38.9 px. */
private val liftNameStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 38.9f.sp,
)

/** Lo ganado en Progreso: Syne ExtraBold 64.8 px con interletraje de -0.03 em. */
private val liftGainStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 64.8f.sp,
    letterSpacing = (-0.03f).em,
)

/** Un día ya medido: [load] acotada a 0..1, [rest] si es descanso, la letra, el nombre y el alto de su fila [height]. */
private class DayRow(
    val load: Float,
    val rest: Boolean,
    val letter: TextLayoutResult,
    val name: TextLayoutResult,
    val height: Float,
)

/** Dos textos de una fila alineados por la línea base: [left] en el margen y [right] pegado a la columna. */
private class PairRow(val left: TextLayoutResult, val right: TextLayoutResult) {
    private val baseline = max(left.firstBaseline, right.firstBaseline)

    /** Desplazamiento vertical del texto izquierdo dentro de la fila. */
    val leftTop: Float = baseline - left.firstBaseline

    /** Desplazamiento vertical del texto derecho dentro de la fila. */
    val rightTop: Float = baseline - right.firstBaseline

    /** Alto de la fila: lo que ocupa el texto más bajo. */
    val height: Float = max(leftTop + left.size.height, rightTop + right.size.height)
}

/**
 * Dibuja la historia entera en su lienzo de 1080 × 1920: fondo y portada, cabecera, pie y, pegado encima del pie con
 * [StoryCanvas.CONTENT_GAP] de separación, el contenido de su tipo. [progress] es el avance de la portada (1 = completa).
 */
internal fun DrawScope.drawProgramStory(story: ProgramStory, measurer: TextMeasurer, progress: Float = 1f) {
    drawStoryBackdrop(story.cover, measurer, progress)
    drawStoryHeader(measurer, story.eyebrow, story.name, story.content.meta)
    val contentBottom = drawStoryFoot(measurer, story.footer) - StoryCanvas.CONTENT_GAP
    when (val content = story.content) {
        is ProgramStoryContent.Week -> drawWeek(measurer, content.days, contentBottom)
        is ProgramStoryContent.Blocks -> drawBlocks(measurer, content, contentBottom)
        is ProgramStoryContent.Progress -> drawProgress(measurer, content.lifts, contentBottom)
    }
}

/** Pinta la historia [story] en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderProgramStory(context: Context, story: ProgramStory): android.graphics.Bitmap =
    renderStory(context) { drawProgramStory(story, it) }

/** Semana: las filas de los días, de arriba abajo, con la última acabada en [bottom]. */
private fun DrawScope.drawWeek(measurer: TextMeasurer, days: List<StoryDayRow>, bottom: Float) {
    val rows = days.map { measureDay(measurer, it) }
    var y = bottom - rows.fold(0f) { acc, row -> acc + row.height }
    for (row in rows) {
        drawDay(row, y)
        y += row.height
    }
}

/** Mide un día: la letra, el nombre (o «Descanso» si no hay sesión o la carga es cero) y el alto de su fila. */
private fun measureDay(measurer: TextMeasurer, day: StoryDayRow): DayRow {
    val rest = day.label == null || day.load <= 0f
    val name = day.label?.takeUnless { rest } ?: "Descanso"
    val letter = measurer.storyText(day.letter, dayLetterStyle)
    val nameLayout = measurer.storyText(name, dayNameStyle, width = DAY_NAME_WIDTH, maxLines = 1, ellipsis = true)
    return DayRow(
        load = day.load.coerceIn(0f, 1f),
        rest = rest,
        letter = letter,
        name = nameLayout,
        height = 2 * ROW_PAD + max(letter.size.height, nameLayout.size.height),
    )
}

/**
 * Pinta una fila de día con su parte superior en [top]: la línea divisoria, la letra y el nombre centrados en vertical y, si es
 * día de sesión, la barra de carga que termina en el borde derecho con su círculo en el extremo.
 */
private fun DrawScope.drawDay(row: DayRow, top: Float) {
    drawRule(top, alpha = 0.07f)
    val midY = top + ROW_PAD + (row.height - 2 * ROW_PAD) / 2f
    drawText(
        row.letter,
        color = if (row.rest) StoryCanvas.Off else StoryCanvas.Ink,
        topLeft = Offset(StoryCanvas.LEFT, midY - row.letter.size.height / 2f),
    )
    drawText(
        row.name,
        color = if (row.rest) StoryCanvas.Off else StoryCanvas.Muted,
        topLeft = Offset(NAME_X, midY - row.name.size.height / 2f),
    )
    if (row.rest) return
    val start = StoryCanvas.RIGHT - ZONE_WIDTH * row.load
    drawRect(StoryCanvas.Ink, topLeft = Offset(start, midY - RULE), size = Size(StoryCanvas.RIGHT - start, 2f))
    drawCircle(StoryCanvas.Ink, radius = DOT_RADIUS, center = Offset(start, midY))
}

/**
 * Bloques: primero la barra de fases (una pastilla por semana, si las hay) y, debajo, una fila por bloque. Todo queda acabado
 * en [bottom].
 */
private fun DrawScope.drawBlocks(measurer: TextMeasurer, content: ProgramStoryContent.Blocks, bottom: Float) {
    val rows = content.blocks.map { block ->
        PairRow(measurer.storyText(block.name, blockNameStyle), measurer.storyText(block.detail, blockDetailStyle))
    }
    val hasBar = content.weekColors.isNotEmpty()
    val heights = buildList<Float> {
        if (hasBar) add(PILL_HEIGHT)
        rows.forEach { add(RULE + ITEM_PAD + it.height) }
    }
    var y = bottom - stackHeight(heights)
    if (hasBar) {
        drawPhaseBar(content.weekColors, y)
        y += PILL_HEIGHT + ITEM_GAP
    }
    for (row in rows) {
        drawRule(y, alpha = 0.08f)
        drawPairRow(row, y + RULE + ITEM_PAD, leftColor = StoryCanvas.Ink, rightColor = StoryCanvas.Muted)
        y += RULE + ITEM_PAD + row.height + ITEM_GAP
    }
}

/** Una pastilla por color de [colors], repartidas a todo el ancho de la columna y con su parte superior en [top]. */
private fun DrawScope.drawPhaseBar(colors: List<Color>, top: Float) {
    val pill = (StoryCanvas.COLUMN_WIDTH - PILL_GAP * (colors.size - 1)) / colors.size
    colors.forEachIndexed { index, color ->
        drawRoundRect(
            color = color,
            topLeft = Offset(StoryCanvas.LEFT + index * (pill + PILL_GAP), top),
            size = Size(pill, PILL_HEIGHT),
            cornerRadius = CornerRadius(PILL_RADIUS),
            alpha = 0.85f,
        )
    }
}

/** Progreso: una fila por ejercicio, con el nombre a la izquierda y lo ganado a la derecha. Todo queda acabado en [bottom]. */
private fun DrawScope.drawProgress(measurer: TextMeasurer, lifts: List<StoryLiftRow>, bottom: Float) {
    val rows = lifts.map { lift ->
        PairRow(measurer.storyText(lift.name, liftNameStyle), measurer.storyText(lift.gain, liftGainStyle))
    }
    var y = bottom - stackHeight(rows.map { RULE + ITEM_PAD + it.height })
    for (row in rows) {
        drawRule(y, alpha = 0.08f)
        drawPairRow(row, y + RULE + ITEM_PAD, leftColor = StoryCanvas.Muted, rightColor = StoryCanvas.Ink)
        y += RULE + ITEM_PAD + row.height + ITEM_GAP
    }
}

/** Alto de una pila de elementos de alturas [heights], con [ITEM_GAP] entre uno y otro. */
private fun stackHeight(heights: List<Float>): Float =
    heights.fold(0f) { acc, height -> acc + height } + ITEM_GAP * (heights.size - 1).coerceAtLeast(0)

/** Línea divisoria de 1 px a todo el ancho de la columna, con su parte superior en [top] y la opacidad [alpha]. */
private fun DrawScope.drawRule(top: Float, alpha: Float) {
    drawRect(Color.White, topLeft = Offset(StoryCanvas.LEFT, top), size = Size(StoryCanvas.COLUMN_WIDTH, RULE), alpha = alpha)
}

/** Pinta [row] con su parte superior en [top]: el texto izquierdo en el margen y el derecho pegado a la columna. */
private fun DrawScope.drawPairRow(row: PairRow, top: Float, leftColor: Color, rightColor: Color) {
    drawText(row.left, color = leftColor, topLeft = Offset(StoryCanvas.LEFT, top + row.leftTop))
    drawText(row.right, color = rightColor, topLeft = Offset(StoryCanvas.RIGHT - row.right.size.width, top + row.rightTop))
}
