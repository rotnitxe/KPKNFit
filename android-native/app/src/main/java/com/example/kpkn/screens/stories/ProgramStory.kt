package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
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
    /** CoverStudio identity for the default story art; a manual line-style choice clears it. */
    val studioCover: CoverIdentity? = null,
    /** Saved photo or bundled-cover reference. Loaded by the share sheet so preview and export use the same pixels. */
    val personalCoverValue: String? = null,
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

/** Separación mínima entre las dos columnas medidas de una fila. */
private const val STORY_PAIR_GAP = 28f

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
internal class PairRow(val left: TextLayoutResult, val right: TextLayoutResult) {
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
internal fun DrawScope.drawProgramStory(
    story: ProgramStory,
    measurer: TextMeasurer,
    progress: Float = 1f,
    personalPhoto: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    drawStoryBackdrop(story.cover, measurer, progress, studioCover = story.studioCover, personalPhoto = personalPhoto)
    val contentTop = drawStoryHeader(measurer, story.eyebrow, story.name, story.content.meta) + StoryCanvas.CONTENT_GAP
    val contentBottom = drawStoryFoot(measurer, story.footer) - StoryCanvas.CONTENT_GAP
    clipRect(top = contentTop, bottom = contentBottom) {
        when (val content = story.content) {
            is ProgramStoryContent.Week -> drawWeek(measurer, content.days, contentTop, contentBottom)
            is ProgramStoryContent.Blocks -> drawBlocks(measurer, content, contentTop, contentBottom)
            is ProgramStoryContent.Progress -> drawProgress(measurer, content.lifts, contentTop, contentBottom)
        }
    }
}

/** Pinta la historia [story] en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderProgramStory(
    context: Context,
    story: ProgramStory,
    personalPhoto: androidx.compose.ui.graphics.ImageBitmap? = null,
): android.graphics.Bitmap = renderStory(context) { drawProgramStory(story, it, personalPhoto = personalPhoto) }

/** Semana: las filas de los días, de arriba abajo, con la última acabada en [bottom]. */
private fun DrawScope.drawWeek(measurer: TextMeasurer, days: List<StoryDayRow>, top: Float, bottom: Float) {
    if (days.isEmpty() || bottom <= top) return
    val available = bottom - top
    val candidates = listOf(1f, 0.92f, 0.84f, 0.76f, 0.68f, 0.60f, 0.52f)
    val layout = candidates.asSequence().map { textScale ->
        val rowPad = (ROW_PAD * textScale).coerceAtLeast(5f)
        val rows = days.map { measureDay(measurer, it, textScale, rowPad) }
        WeekStoryLayout(rows, rowPad, rows.sumOf { it.height.toDouble() }.toFloat())
    }.firstOrNull { it.height <= available } ?: run {
        val scale = candidates.last()
        val basePad = (ROW_PAD * scale).coerceAtLeast(5f)
        val initialRows = days.map { measureDay(measurer, it, scale, basePad) }
        val rawHeight = initialRows.sumOf { it.height.toDouble() }.toFloat()
        if (rawHeight <= available) {
            WeekStoryLayout(initialRows, basePad, rawHeight)
        } else {
            val contentHeight = initialRows.sumOf { (it.height - 2 * basePad).toDouble() }.toFloat()
            val dynamicPad = ((available - contentHeight) / (days.size * 2f)).coerceIn(1f, basePad)
            val rows = days.map { measureDay(measurer, it, scale, dynamicPad) }
            WeekStoryLayout(rows, dynamicPad, rows.sumOf { it.height.toDouble() }.toFloat())
        }
    }
    // Compactamos tipografía y relleno en pasos medidos; nunca deformamos glifos para forzar las siete filas.
    var y = (bottom - layout.height).coerceAtLeast(top)
    for (row in layout.rows) {
        drawDay(row, y, layout.rowPad)
        y += row.height
    }
}

private data class WeekStoryLayout(val rows: List<DayRow>, val rowPad: Float, val height: Float)

/** Mide un día: la letra, el nombre (o «Descanso» si no hay sesión o la carga es cero) y el alto de su fila. */
private fun measureDay(measurer: TextMeasurer, day: StoryDayRow, textScale: Float, rowPad: Float): DayRow {
    val rest = day.label == null || day.load <= 0f
    val name = day.label?.takeUnless { rest } ?: "Descanso"
    val letter = measurer.storyText(day.letter, dayLetterStyle.storyScaled(textScale))
    val nameLayout = measurer.storyText(name, dayNameStyle.storyScaled(textScale), width = DAY_NAME_WIDTH, maxLines = 1, ellipsis = true)
    return DayRow(
        load = day.load.coerceIn(0f, 1f),
        rest = rest,
        letter = letter,
        name = nameLayout,
        height = 2 * rowPad + max(letter.size.height, nameLayout.size.height),
    )
}

/**
 * Pinta una fila de día con su parte superior en [top]: la línea divisoria, la letra y el nombre centrados en vertical y, si es
 * día de sesión, la barra de carga que termina en el borde derecho con su círculo en el extremo.
 */
private fun DrawScope.drawDay(row: DayRow, top: Float, rowPad: Float) {
    drawRule(top, alpha = 0.07f)
    val contentHeight = row.height - 2 * rowPad
    val midY = top + rowPad + contentHeight / 2f
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
private fun DrawScope.drawBlocks(measurer: TextMeasurer, content: ProgramStoryContent.Blocks, top: Float, bottom: Float) {
    if (bottom <= top) return
    val hasBar = content.weekColors.isNotEmpty()
    val layout = measureBlocksStoryLayout(measurer, content, bottom - top)
    var y = (bottom - layout.totalHeight).coerceAtLeast(top)
    if (hasBar) {
        drawPhaseBar(content.weekColors, y, layout.pillHeight, layout.gap)
        y += layout.pillHeight + layout.gap
    }
    for (row in layout.rows) {
        drawRule(y, alpha = 0.08f)
        drawPairRow(row, y + RULE + layout.itemPad, leftColor = StoryCanvas.Ink, rightColor = StoryCanvas.Muted)
        y += RULE + layout.itemPad + row.height + layout.gap
    }
}

internal fun measureBlocksStoryLayout(
    measurer: TextMeasurer,
    content: ProgramStoryContent.Blocks,
    availableHeight: Float,
): BlocksStoryLayout {
    val hasBar = content.weekColors.isNotEmpty()
    return listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f).asSequence().map { textScale ->
        val rows = content.blocks.map { block ->
            measuredPairRow(measurer, block.name, block.detail, blockNameStyle.storyScaled(textScale), blockDetailStyle.storyScaled(textScale), rightShare = 0.46f)
        }
        val pad = ITEM_PAD * textScale
        val gap = ITEM_GAP * textScale
        val pillHeight = PILL_HEIGHT * textScale
        val heights = buildList {
            if (hasBar) add(pillHeight)
            rows.forEach { add(RULE + pad + it.height) }
        }
        val total = stackHeight(heights, gap)
        BlocksStoryLayout(rows, pad, gap, pillHeight, total)
    }.firstOrNull { it.totalHeight <= availableHeight } ?: run {
        val scale = 0.6f
        val pad = ITEM_PAD * scale
        val gap = ITEM_GAP * scale
        val pillHeight = PILL_HEIGHT * scale
        val allRows = content.blocks.map { block ->
            measuredPairRow(measurer, block.name, block.detail, blockNameStyle.storyScaled(scale), blockDetailStyle.storyScaled(scale), rightShare = 0.46f)
        }
        var fittedCount = allRows.size
        var finalRows = allRows
        while (fittedCount > 0) {
            val overflowRow = if (fittedCount < allRows.size) {
                val remaining = allRows.size - fittedCount
                blockRemainderRow(measurer, remaining, scale)
            } else null
            val testRows = allRows.take(fittedCount) + listOfNotNull(overflowRow)
            val heights = buildList {
                if (hasBar) add(pillHeight)
                testRows.forEach { add(RULE + pad + it.height) }
            }
            if (stackHeight(heights, gap) <= availableHeight || fittedCount == 1) {
                finalRows = testRows
                break
            }
            fittedCount--
        }
        val heights = buildList {
            if (hasBar) add(pillHeight)
            finalRows.forEach { add(RULE + pad + it.height) }
        }
        BlocksStoryLayout(finalRows, pad, gap, pillHeight, stackHeight(heights, gap))
    }
}

internal data class BlocksStoryLayout(
    val rows: List<PairRow>,
    val itemPad: Float,
    val gap: Float,
    val pillHeight: Float,
    val totalHeight: Float,
)

internal fun blockRemainderRow(measurer: TextMeasurer, remaining: Int, textScale: Float): PairRow {
    require(remaining > 0)
    return measuredPairRow(
        measurer = measurer,
        left = "+$remaining bloques más",
        right = "",
        leftStyle = blockNameStyle.storyScaled(textScale),
        rightStyle = blockDetailStyle.storyScaled(textScale),
        rightShare = 0.46f,
        ellipsis = false,
    )
}

/** Una pastilla por color de [colors], repartidas a todo el ancho de la columna y con su parte superior en [top]. */
private fun DrawScope.drawPhaseBar(colors: List<Color>, top: Float, height: Float, densityScale: Float) {
    if (colors.isEmpty()) return
    val gap = (PILL_GAP * densityScale).coerceAtMost(StoryCanvas.COLUMN_WIDTH / colors.size.coerceAtLeast(1) * 0.22f)
    val pill = ((StoryCanvas.COLUMN_WIDTH - gap * (colors.size - 1)) / colors.size).coerceAtLeast(1f)
    colors.forEachIndexed { index, color ->
        drawRoundRect(
            color = color,
            topLeft = Offset(StoryCanvas.LEFT + index * (pill + gap), top),
            size = Size(pill, height),
            cornerRadius = CornerRadius(PILL_RADIUS * densityScale),
            alpha = 0.85f,
        )
    }
}

/** Progreso: una fila por ejercicio, con el nombre a la izquierda y lo ganado a la derecha. Todo queda acabado en [bottom]. */
private fun DrawScope.drawProgress(measurer: TextMeasurer, lifts: List<StoryLiftRow>, top: Float, bottom: Float) {
    if (lifts.isEmpty() || bottom <= top) return
    val layout = listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f).asSequence().map { textScale ->
        val rows = lifts.map { lift ->
            measuredPairRow(measurer, lift.name, lift.gain, liftNameStyle.storyScaled(textScale), liftGainStyle.storyScaled(textScale), rightShare = 0.43f)
        }
        val pad = ITEM_PAD * textScale
        val gap = ITEM_GAP * textScale
        ProgressStoryLayout(rows, pad, gap, stackHeight(rows.map { RULE + pad + it.height }, gap))
    }.firstOrNull { it.totalHeight <= bottom - top } ?: run {
        val scale = 0.6f
        val rows = lifts.map { lift ->
            measuredPairRow(measurer, lift.name, lift.gain, liftNameStyle.storyScaled(scale), liftGainStyle.storyScaled(scale), rightShare = 0.43f)
        }
        val pad = ITEM_PAD * scale
        val gap = ITEM_GAP * scale
        ProgressStoryLayout(rows, pad, gap, stackHeight(rows.map { RULE + pad + it.height }, gap))
    }
    var y = (bottom - layout.totalHeight).coerceAtLeast(top)
    for (row in layout.rows) {
        drawRule(y, alpha = 0.08f)
        drawPairRow(row, y + RULE + layout.itemPad, leftColor = StoryCanvas.Muted, rightColor = StoryCanvas.Ink)
        y += RULE + layout.itemPad + row.height + layout.gap
    }
}

private data class ProgressStoryLayout(val rows: List<PairRow>, val itemPad: Float, val gap: Float, val totalHeight: Float)

/** Alto de una pila de elementos de alturas [heights], con [ITEM_GAP] entre uno y otro. */
private fun stackHeight(heights: List<Float>, gap: Float = ITEM_GAP): Float =
    heights.fold(0f) { acc, height -> acc + height } + gap * (heights.size - 1).coerceAtLeast(0)

/** Línea divisoria de 1 px a todo el ancho de la columna, con su parte superior en [top] y la opacidad [alpha]. */
private fun DrawScope.drawRule(top: Float, alpha: Float) {
    drawRect(Color.White, topLeft = Offset(StoryCanvas.LEFT, top), size = Size(StoryCanvas.COLUMN_WIDTH, RULE), alpha = alpha)
}

/** Pinta [row] con su parte superior en [top]: el texto izquierdo en el margen y el derecho pegado a la columna. */
private fun DrawScope.drawPairRow(row: PairRow, top: Float, leftColor: Color, rightColor: Color) {
    drawText(row.left, color = leftColor, topLeft = Offset(StoryCanvas.LEFT, top + row.leftTop))
    drawText(row.right, color = rightColor, topLeft = Offset(StoryCanvas.RIGHT - row.right.size.width, top + row.rightTop))
}

internal fun measuredPairRow(
    measurer: TextMeasurer,
    left: String,
    right: String,
    leftStyle: TextStyle,
    rightStyle: TextStyle,
    rightShare: Float,
    ellipsis: Boolean = true,
): PairRow {
    val available = StoryCanvas.COLUMN_WIDTH - STORY_PAIR_GAP
    val rightWidth = (available * rightShare.coerceIn(0.25f, 0.5f)).toInt().coerceAtLeast(1)
    val leftWidth = (available - rightWidth).toInt().coerceAtLeast(1)
    return PairRow(
        measurer.storyText(left, leftStyle, width = leftWidth, maxLines = 1, ellipsis = ellipsis),
        measurer.storyText(right, rightStyle, width = rightWidth, maxLines = 1, ellipsis = ellipsis),
    )
}

private fun TextStyle.storyScaled(scale: Float): TextStyle = copy(
    fontSize = (fontSize.value * scale).sp,
    lineHeight = if (lineHeight.isSp) (lineHeight.value * scale).sp else lineHeight,
)
