package com.example.kpkn.screens.stories

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.drawLineCover
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * El marco `storyShell` del lienzo Registros v4 (competencia, cuerpo y nutrición), traducido a px del lienzo de 1080 × 1920
 * (1 cqw = 10,8 px). Columna `.st-in` del 7 % al 93 % y del `top` de cada historia al 95 % (1824 px); flex en columna con los
 * márgenes del CSS: un margen `auto` se queda con el sitio libre y, sin él, `.end` pega el bloque abajo.
 */

/** 1 % del ancho del lienzo (1 cqw del CSS) en px. */
internal const val CQW = 10.8f

/** Borde inferior de `.st-in` (bottom: 5 %). */
internal const val SHELL_BOTTOM = 1824f

/** Separaciones del CSS: nombre (arriba 2,5 cqw, abajo 2 cqw), pie (4 cqw; 7 cqw en `.end`) y filas en `.end` (5 cqw). */
internal const val SHELL_NAME_TOP = 27f
internal const val SHELL_NAME_BOTTOM = 21.6f
internal const val SHELL_FOOT_GAP = 43.2f
internal const val SHELL_FOOT_GAP_END = 75.6f
internal const val SHELL_ROWS_GAP_END = 54f

/** Interlineado del nombre (Syne 10,5 cqw con `line-height: 1`). */
private const val SHELL_NAME_LINE = 113.4f

/** Filas `.st-lifts`: separación entre filas, relleno superior y regla de 1 px blanca al 8 % (#ffffff14). */
private const val SHELL_ROW_GAP = 25.9f
private const val SHELL_ROW_PAD = 23.8f
private const val SHELL_RULE_ALPHA = 0x14 / 255f

/** Insignia `.st-badge`: margen superior, rellenos, icono y su separación. */
private const val SHELL_BADGE_GAP = 43.2f
private const val SHELL_BADGE_PAD_Y = 15.1f
private const val SHELL_BADGE_PAD_X = 34.6f
private const val SHELL_BADGE_ICON = 38.9f
private const val SHELL_BADGE_ICON_GAP = 17.3f

/** Línea `.st-inline`: margen superior, hueco entre piezas, punto de 1,2 cqw y margen de la unidad. */
private const val SHELL_INLINE_TOP = 32.4f
private const val SHELL_INLINE_GAP = 32.4f
private const val SHELL_INLINE_DOT = 13f
private const val SHELL_INLINE_UNIT_GAP = 10.8f

/** Estilos del marco. En un objeto para que los modelos de las historias se prueben sin cargar las fuentes. */
internal object ShellType {
    val eyebrow = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 38.9f.sp, letterSpacing = 0.25f.em)
    val name = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = SHELL_NAME_LINE.sp,
        lineHeight = SHELL_NAME_LINE.sp,
        letterSpacing = (-0.04f).em,
        lineBreak = LineBreak.Heading,
    )
    val meta = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 41f.sp)
    val rowName = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 38.9f.sp)
    fun rowValue(size: Float = 64.8f) = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp,
        letterSpacing = (-0.03f).em,
    )
    val badge = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 34.6f.sp)
    val inlineValue = TextStyle(
        fontFamily = WizardFonts.display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 58.3f.sp,
        letterSpacing = (-0.03f).em,
    )
    val inlineUnit = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 36.7f.sp)
    val scoreLabel = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 32.4f.sp, letterSpacing = 0.1f.em)
    val scoreValue = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 47.5f.sp)
    val scoreMark = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 25.9f.sp)
    val macroName = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 38.9f.sp)
    val macroValue = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 49.7f.sp)
    val dayLetter = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 30.2f.sp)
    val ringValue = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 91.8f.sp, fontFeatureSettings = "tnum")
    val ringUnit = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 36.7f.sp)
    fun medalDigit(size: Float) = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = size.sp)
}

/** Un bloque de la columna: su alto, su margen superior (o `auto`) y cómo se pinta con su borde superior en la y dada. */
internal class ShellBlock(
    val height: Float,
    val marginTop: Float = 0f,
    val auto: Boolean = false,
    val paint: DrawScope.(top: Float) -> Unit,
)

/**
 * Coloca [blocks] como el flex en columna de `.st-in` entre [top] y [bottom]: un margen `auto` reparte el sitio libre; sin ninguno,
 * [end] los pega abajo (si no caben, desbordan por arriba, como el CSS) y, si no, empiezan en [top].
 */
internal fun DrawScope.drawShell(top: Float, end: Boolean, blocks: List<ShellBlock>, bottom: Float = SHELL_BOTTOM) {
    val fixed = blocks.fold(0f) { acc, block -> acc + block.height + if (block.auto) 0f else block.marginTop }
    val autos = blocks.count { it.auto }
    val free = bottom - top - fixed
    var y = when {
        autos == 0 && end -> bottom - fixed
        else -> top
    }
    val share = if (autos > 0) max(0f, free) / autos else 0f
    for (block in blocks) {
        y += if (block.auto) share else block.marginTop
        block.paint(this, y)
        y += block.height
    }
}

/** Mayúsculas en español (las del eyebrow y las etiquetas). */
internal fun String.upperEs(): String = uppercase(Locale.forLanguageTag("es"))

/** Cabecera de `storyShell`: eyebrow (si hay), nombre de hasta tres líneas con su margen inferior y la meta [sub] (si hay). */
internal fun TextMeasurer.shellHeader(eyebrow: String?, title: String?, sub: String?): List<ShellBlock> = buildList {
    val column = StoryCanvas.COLUMN_WIDTH.toInt()
    if (!eyebrow.isNullOrBlank()) {
        val layout = storyText(eyebrow.upperEs(), ShellType.eyebrow, width = column, maxLines = 1, ellipsis = true)
        add(ShellBlock(layout.size.height.toFloat()) { top -> drawText(layout, StoryCanvas.Faint, Offset(StoryCanvas.LEFT, top)) })
    }
    if (!title.isNullOrBlank()) {
        val layout = storyText(title, ShellType.name, width = column, maxLines = 3, ellipsis = true)
        val natural = storyText(title, ShellType.name.copy(lineHeight = TextUnit.Unspecified), width = column, maxLines = 3, ellipsis = true)
        val naturalLine = natural.size.height.toFloat() / natural.lineCount.coerceAtLeast(1)
        val lift = (SHELL_NAME_LINE - naturalLine) / 2f
        add(
            ShellBlock(layout.lineCount * SHELL_NAME_LINE + SHELL_NAME_BOTTOM, marginTop = if (isEmpty()) 0f else SHELL_NAME_TOP) { top ->
                drawText(layout, StoryCanvas.Ink, Offset(StoryCanvas.LEFT, top + lift))
            },
        )
    }
    if (!sub.isNullOrBlank()) {
        val layout = storyText(sub, ShellType.meta, width = column, maxLines = 1, ellipsis = true)
        add(ShellBlock(layout.size.height.toFloat()) { top -> drawText(layout, StoryCanvas.Muted, Offset(StoryCanvas.LEFT, top)) })
    }
}

/** Pie «KPKN · [caption]» con su margen (4 cqw, o 7 cqw en `.end`). */
internal fun TextMeasurer.shellFoot(caption: String, end: Boolean): ShellBlock {
    val brand = storyText("KPKN", ShellType.name.copy(fontSize = 43.2f.sp, lineHeight = TextUnit.Unspecified, letterSpacing = (-0.03f).em))
    val note = storyText(caption, ShellType.meta.copy(fontSize = 30.2f.sp), maxLines = 1)
    val height = max(brand.size.height, note.size.height).toFloat()
    return ShellBlock(height, marginTop = if (end) SHELL_FOOT_GAP_END else SHELL_FOOT_GAP) { top -> drawStoryFoot(this@shellFoot, caption, bottom = top + height) }
}

/** Una fila de `.st-lifts`: [label] a la izquierda (gris) y [value] a la derecha (tinta), sobre la misma línea base. */
internal data class StoryPair(val label: String, val value: String)

/** Filas `.st-lifts`: regla, relleno y nombre y valor en la misma base; [valueSize] es el tamaño del valor (6 cqw por defecto). */
internal fun TextMeasurer.shellRows(rows: List<StoryPair>, marginTop: Float, auto: Boolean, valueSize: Float = 64.8f): ShellBlock? {
    if (rows.isEmpty()) return null
    val measured = rows.map { row ->
        val value = storyText(row.value, ShellType.rowValue(valueSize), maxLines = 1)
        val nameWidth = (StoryCanvas.COLUMN_WIDTH - value.size.width - 40f).toInt().coerceAtLeast(120)
        val name = storyText(row.label, ShellType.rowName, width = nameWidth, maxLines = 1, ellipsis = true)
        val baseline = max(name.firstBaseline, value.firstBaseline)
        val height = max(baseline - name.firstBaseline + name.size.height, baseline - value.firstBaseline + value.size.height)
        Triple(name, value, baseline to height)
    }
    val total = measured.fold(0f) { acc, it -> acc + 1f + SHELL_ROW_PAD + it.third.second } + SHELL_ROW_GAP * (measured.size - 1)
    return ShellBlock(total, marginTop = marginTop, auto = auto) { top ->
        var y = top
        for ((name, value, metrics) in measured) {
            drawRect(Color.White, Offset(StoryCanvas.LEFT, y), Size(StoryCanvas.COLUMN_WIDTH, 1f), alpha = SHELL_RULE_ALPHA)
            val base = y + 1f + SHELL_ROW_PAD + metrics.first
            drawText(name, StoryCanvas.Muted, Offset(StoryCanvas.LEFT, base - name.firstBaseline))
            drawText(value, StoryCanvas.Ink, Offset(StoryCanvas.RIGHT - value.size.width, base - value.firstBaseline))
            y += 1f + SHELL_ROW_PAD + metrics.second + SHELL_ROW_GAP
        }
    }
}

/** Icono de la insignia: el destello (`spark`) o la llama (`fire`) de KPKN Line. */
internal enum class ShellIcon(val path: String) {
    SPARK("M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z"),
    FIRE("M12 20.5a6 6 0 006-6c0-4-3.5-5.5-3.5-10C11 6 8 8.5 8 12c-1.5-.5-2-2-2-2-.5 1.3-.5 2.4-.5 3.7a6.5 6.5 0 006.5 6.8z"),
}

/** Dibuja el icono de línea [icon] (retícula 24, trazo 1,75) en un cuadrado de [size] px con su esquina en [topLeft]. */
internal fun DrawScope.drawLineIcon(icon: ShellIcon, topLeft: Offset, size: Float, color: Color) {
    val path = PathParser().parsePathString(icon.path).toPath()
    val k = size / 24f
    translate(topLeft.x, topLeft.y) {
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Insignia `.st-badge`: borde de 1 px en acento al 44 %, icono y texto en acento; 4 cqw por encima. */
internal fun TextMeasurer.shellBadge(text: String, icon: ShellIcon): ShellBlock {
    val layout = storyText(text, ShellType.badge, maxLines = 1)
    val height = max(SHELL_BADGE_ICON, layout.size.height.toFloat()) + 2 * SHELL_BADGE_PAD_Y
    val width = 2 * SHELL_BADGE_PAD_X + SHELL_BADGE_ICON + SHELL_BADGE_ICON_GAP + layout.size.width
    return ShellBlock(height, marginTop = SHELL_BADGE_GAP) { top ->
        drawRoundRect(
            color = StoryCanvas.Accent,
            topLeft = Offset(StoryCanvas.LEFT + 0.5f, top + 0.5f),
            size = Size(width - 1f, height - 1f),
            cornerRadius = CornerRadius(height / 2f),
            style = Stroke(width = 1f),
            alpha = 0x70 / 255f,
        )
        drawLineIcon(icon, Offset(StoryCanvas.LEFT + SHELL_BADGE_PAD_X, top + (height - SHELL_BADGE_ICON) / 2f), SHELL_BADGE_ICON, StoryCanvas.Accent)
        drawText(
            layout,
            StoryCanvas.Accent,
            Offset(StoryCanvas.LEFT + SHELL_BADGE_PAD_X + SHELL_BADGE_ICON + SHELL_BADGE_ICON_GAP, top + (height - layout.size.height) / 2f),
        )
    }
}

/** Una cifra de `.st-inline`: el valor (Syne) y, si hay, su unidad pequeña (Inter). */
internal data class StoryInlineItem(val value: String, val unit: String?)

/** Línea `.st-inline`: cifras en la misma base separadas por puntos de acento centrados; 3 cqw por encima. */
internal fun TextMeasurer.shellInline(items: List<StoryInlineItem>): ShellBlock? {
    if (items.isEmpty()) return null
    val measured = items.map { storyText(it.value, ShellType.inlineValue) to it.unit?.let { u -> storyText(u, ShellType.inlineUnit) } }
    val ascent = measured.maxOf { (v, u) -> max(v.firstBaseline, u?.firstBaseline ?: 0f) }
    val descent = measured.maxOf { (v, u) -> max(v.size.height - v.firstBaseline, u?.let { it.size.height - it.firstBaseline } ?: 0f) }
    val height = ascent + descent
    return ShellBlock(height, marginTop = SHELL_INLINE_TOP) { top ->
        val base = top + ascent
        var x = StoryCanvas.LEFT
        measured.forEachIndexed { index, (value, unit) ->
            if (index > 0) {
                x += SHELL_INLINE_GAP
                drawCircle(StoryCanvas.Accent, radius = SHELL_INLINE_DOT / 2f, center = Offset(x + SHELL_INLINE_DOT / 2f, top + height / 2f))
                x += SHELL_INLINE_DOT + SHELL_INLINE_GAP
            }
            drawText(value, StoryCanvas.Ink, Offset(x, base - value.firstBaseline))
            x += value.size.width
            if (unit != null) {
                x += SHELL_INLINE_UNIT_GAP
                drawText(unit, StoryCanvas.Muted, Offset(x, base - unit.firstBaseline))
                x += unit.size.width
            }
        }
    }
}

/** Una celda del marcador: el peso y si fue válido (true), nulo (false) o aún sin juzgar (null). */
internal data class StoryAttemptCell(val value: String, val good: Boolean?)

/** Una fila del marcador: la etiqueta («SENT.») y sus intentos. */
internal data class StoryAttemptRow(val label: String, val cells: List<StoryAttemptCell>)

/** Marcador `.st-score`: columna de 16 cqw y tres de 1fr con 1,8 cqw de hueco; celdas de 12 cqw con radio de 3 cqw. */
internal fun TextMeasurer.shellScore(rows: List<StoryAttemptRow>): ShellBlock? {
    if (rows.isEmpty()) return null
    val gap = 1.8f * CQW
    val labelWidth = 16f * CQW
    val cellWidth = (StoryCanvas.COLUMN_WIDTH - labelWidth - 3 * gap) / 3f
    val cellHeight = 12f * CQW
    val measured = rows.map { row ->
        val label = storyText(row.label.upperEs(), ShellType.scoreLabel, width = labelWidth.toInt(), maxLines = 2, ellipsis = true)
        val cells = row.cells.take(3).map { cell ->
            val value = storyText(
                cell.value,
                ShellType.scoreValue.copy(textDecoration = if (cell.good == false) TextDecoration.LineThrough else null),
                maxLines = 1,
            )
            ScoreCell(cell.good, value, cell.good?.let { storyText(if (it) "✓" else "✗", ShellType.scoreMark) })
        }
        label to cells
    }
    val heights = measured.map { (label, _) -> max(cellHeight, label.size.height.toFloat()) }
    val total = heights.sum() + gap * (rows.size - 1)
    return ShellBlock(total, auto = true) { top ->
        var y = top
        measured.forEachIndexed { index, (label, cells) ->
            val h = heights[index]
            drawText(label, StoryCanvas.Faint, Offset(StoryCanvas.LEFT, y + (h - label.size.height) / 2f))
            cells.forEachIndexed { column, cell ->
                val left = StoryCanvas.LEFT + labelWidth + gap + column * (cellWidth + gap)
                val cellTop = y + (h - cellHeight) / 2f
                drawScoreCell(cell, Rect(left, cellTop, left + cellWidth, cellTop + cellHeight))
            }
            y += h + gap
        }
    }
}

/** Una celda del marcador ya medida. */
private class ScoreCell(val good: Boolean?, val value: TextLayoutResult, val mark: TextLayoutResult?)

/** Una celda: válida en tinta llena con texto negro; nula tachada en gris; con borde blanco al 13 % si no es válida. */
private fun DrawScope.drawScoreCell(cell: ScoreCell, box: Rect) {
    val radius = CornerRadius(3f * CQW)
    if (cell.good == true) {
        drawRoundRect(StoryCanvas.Ink, box.topLeft, box.size, radius)
    } else {
        drawRoundRect(Color.White, Offset(box.left + 0.5f, box.top + 0.5f), Size(box.width - 1f, box.height - 1f), radius, style = Stroke(1f), alpha = 0x22 / 255f)
    }
    val color = when (cell.good) {
        true -> Color(0xFF111111)
        false -> StoryCanvas.Off
        null -> StoryCanvas.Ink
    }
    val markGap = 0.6f * CQW
    val contentHeight = cell.value.size.height + (cell.mark?.let { markGap + it.size.height } ?: 0f)
    var y = box.top + (box.height - contentHeight) / 2f
    drawText(cell.value, color, Offset(box.center.x - cell.value.size.width / 2f, y))
    y += cell.value.size.height + markGap
    cell.mark?.let { drawText(it, color.copy(alpha = 0.7f), Offset(box.center.x - it.size.width / 2f, y)) }
}

/**
 * Foto a pantalla completa con recorte centrado y, encima, el velo `.photo:after` (de transparente al 30 % a negro al 93 % en el
 * 68 %).
 */
internal fun DrawScope.drawStoryPhoto(photo: ImageBitmap) {
    val w = StoryCanvas.WIDTH.toFloat()
    val h = StoryCanvas.HEIGHT.toFloat()
    val scale = max(w / photo.width, h / photo.height)
    val srcWidth = min(photo.width, (w / scale).roundToInt()).coerceAtLeast(1)
    val srcHeight = min(photo.height, (h / scale).roundToInt()).coerceAtLeast(1)
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - srcWidth) / 2, (photo.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(StoryCanvas.WIDTH, StoryCanvas.HEIGHT),
    )
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0f),
            0.30f to Color.Black.copy(alpha = 0f),
            0.68f to Color.Black.copy(alpha = 0xEE / 255f),
            1f to Color.Black.copy(alpha = 0xEE / 255f),
            startY = 0f,
            endY = h,
        ),
        size = Size(w, h),
    )
}

/**
 * Las líneas de [cover] sobre la foto (`.bgart.over`): al 50 % y en modo «trama» (screen) en el rectángulo de arriba hasta
 * [artBottom], fundidas a negro en su 45 % inferior para que no lleguen al texto.
 */
internal fun DrawScope.drawCoverOverPhoto(cover: LineCoverSpec, measurer: TextMeasurer, artBottom: Float, progress: Float = 1f) {
    val width = StoryCanvas.WIDTH.toFloat()
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, width, artBottom), Paint().apply { alpha = 0.5f; blendMode = BlendMode.Screen })
        clipRect(0f, 0f, width, artBottom) {
            inset(left = 0f, top = 0f, right = size.width - width, bottom = size.height - artBottom) {
                drawLineCover(cover, measurer, progress)
            }
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black), startY = artBottom * 0.55f, endY = artBottom),
            topLeft = Offset(0f, artBottom * 0.55f),
            size = Size(width, artBottom * 0.45f),
        )
        canvas.restore()
    }
}

/** Fondo liso de las historias sin portada (`art: ''`). */
internal fun DrawScope.drawStoryPlain() {
    drawRect(StoryCanvas.Background, size = Size(StoryCanvas.WIDTH.toFloat(), StoryCanvas.HEIGHT.toFloat()))
}

/** Alto de una portada cuyo `artBottom` del CSS es [percent] % (inset inferior). */
internal fun artHeight(percent: Float): Float = StoryCanvas.HEIGHT * (100f - percent) / 100f

/** Un `top` del CSS en % del alto, en px. */
internal fun topAt(percent: Float): Float = StoryCanvas.HEIGHT * percent / 100f
