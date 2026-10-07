package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.eInOut
import com.example.kpkn.screens.onboarding.design.eOutBack
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.mix
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * Escena de Nutrición · la hoja «Registrar comida»: el campo donde se teclea la frase (con el teclado), el
 * destello que la lee, los chips en que se convierte (en el mismo sitio de la frase) y la lista de alimentos con
 * sus kcal y minibarras de macros.
 */

private const val FieldInnerW = 236f
private const val FieldPadX = 12f
private const val FieldPadY = 10f
private const val LineH = 20f
private const val ChipIcon = 14f
private const val ChipGap = 6f

/** Medidas que dependen del texto (saltos de línea de la frase, anchos de los chips): se calculan una sola vez. */
internal class NutricionSheetGeometry(kit: NutricionKit) {
    val fieldText: TextLayoutResult = kit.block(NT.Field, NutricionMeal.Sentence, FieldInnerW)
    val placeholder: TextLayoutResult = kit.block(NT.Field, "Ej: Almorcé arroz con pollo y un poco de ensalada", FieldInnerW)

    /** Por alimento: los tramos de subrayado como [x0, x1, arriba, abajo] seguidos (coordenadas del texto). */
    val underline: Array<FloatArray>

    /** Por alimento: su tramo más ancho como [x, y, ancho, alto] en coordenadas del texto (de ahí sale el chip). */
    val source: Array<FloatArray>

    val chipW: FloatArray
    val chipX: FloatArray
    val chipRow = intArrayOf(0, 0, 1, 1)

    init {
        val spans = NutricionMeal.spans
        underline = Array(4) { k ->
            val span = spans[k]
            val out = ArrayList<Float>()
            for (line in fieldText.getLineForOffset(span.first)..fieldText.getLineForOffset(span.last)) {
                val a = max(span.first, fieldText.getLineStart(line))
                val b = minOf(span.last + 1, fieldText.getLineEnd(line, visibleEnd = true))
                if (b <= a) continue
                out += fieldText.getHorizontalPosition(a, true)
                out += fieldText.getHorizontalPosition(b, true)
                out += fieldText.getLineTop(line)
                out += fieldText.getLineBottom(line)
            }
            out.toFloatArray()
        }
        source = Array(4) { k ->
            val u = underline[k]
            var best = 0
            for (j in u.indices step 4) if (u[j + 1] - u[j] > u[best + 1] - u[best]) best = j
            floatArrayOf(u[best], u[best + 2], u[best + 1] - u[best], u[best + 3] - u[best + 2])
        }
        chipW = FloatArray(4) { k ->
            val food = NutricionMeal.foods[k]
            7f + ChipIcon + 4f + kit.width(NT.Chip, food.chipName) +
                (if (food.chipQty.isEmpty()) 0f else 3f + kit.width(NT.ChipQty, food.chipQty)) + 8f
        }
        // Cada fila de chips va centrada en el campo.
        val fieldW = NutricionLayout.Width - 2f * NutricionLayout.Margin
        chipX = FloatArray(4)
        for (row in 0..1) {
            val first = row * 2
            val total = chipW[first] + ChipGap + chipW[first + 1]
            chipX[first] = NutricionLayout.Margin + (fieldW - total) / 2f
            chipX[first + 1] = chipX[first] + chipW[first] + ChipGap
        }
    }
}

internal fun DrawScope.drawNutricionSheet(f: NutricionFrame, kit: NutricionKit, geo: NutricionSheetGeometry) {
    val s = f.sheet
    val l = NutricionLayout
    if (s.top >= l.Height) return

    // La hoja y su filete superior
    rr(0f, s.top, l.Width, l.Height - s.top + 40f, 22f, NC.sheet)
    rrLine(0f, s.top, l.Width, l.Height - s.top + 40f, 22f, Color.White.a(0.07f), 1f)

    withTransform({ translate(0f, s.top) }) {
        rr(l.Width / 2f - 17f, 8f, 34f, 4f, 2f, Color.White.a(0.20f))

        // Título, con la comida que se está registrando encima
        nTextMid(kit, NT.Section, "${NutricionMeal.Name.uppercase()} · HOY", l.Margin, 24f, NC.muted)
        nTextMid(kit, NT.SheetTitle, "Registrar comida", l.Margin, 43f, NC.ink)

        drawField(s, kit, geo)
        drawChips(s, kit, geo)

        val rowsTop = l.FieldTop + l.FieldH + l.RowsGap
        for (i in 0..3) drawRow(i, rowsTop + i * (l.RowH + l.RowGap), s, kit)

        val rowsEnd = rowsTop + l.RowsBlockH
        drawTotals(s, kit, rowsEnd + l.TotalGap)
        drawCta(s, kit, rowsEnd + l.TotalGap + l.TotalH + l.CtaGap)
    }

    drawNutricionKeyboard(s, kit)
}

// ── el campo de texto

private fun DrawScope.drawField(s: NutricionSheetFrame, kit: NutricionKit, geo: NutricionSheetGeometry) {
    val l = NutricionLayout
    val x = l.Margin
    val y = l.FieldTop
    val w = l.Width - 2f * l.Margin
    val h = l.FieldH
    rr(x, y, w, h, 12f, Color.White.a(0.065f))
    rrLine(x, y, w, h, 12f, mix(Color.White.a(0.10f), NC.kcal.a(0.80f), s.focus), 1.2f)

    clipRect(x, y, x + w, y + h) {
        val ox = x + FieldPadX
        val oy = y + FieldPadY

        if (s.typed == 0 && s.focus > 0f) {
            drawLayout(geo.placeholder, NC.muted.a(0.55f * s.focus), ox, oy)
        }

        // Lo tecleado: líneas completas y, en la línea del cursor, solo hasta el cursor.
        if (s.typed > 0 && s.textAlpha > 0.003f) {
            val n = s.typed
            val txt = geo.fieldText
            val color = NC.ink.a(s.textAlpha)
            if (n >= NutricionMeal.Sentence.length) {
                drawLayout(txt, color, ox, oy)
            } else {
                val line = txt.getLineForOffset(n)
                val lineTop = txt.getLineTop(line)
                val lineBottom = txt.getLineBottom(line)
                val caretX = txt.getHorizontalPosition(n, true)
                if (lineTop > 0f) clipRect(ox, oy, ox + FieldInnerW + 4f, oy + lineTop) { drawLayout(txt, color, ox, oy) }
                clipRect(ox, oy + lineTop, ox + caretX + 0.2f, oy + lineBottom) { drawLayout(txt, color, ox, oy) }
            }
        }

        // Palabras reconocidas: un subrayado que se dibuja al terminar cada una y se enciende con el destello.
        for (k in 0..3) {
            val u = s.underline[k]
            if (u <= 0.003f) continue
            val segs = geo.underline[k]
            var total = 0f
            for (j in segs.indices step 4) total += segs[j + 1] - segs[j]
            var left = u * total
            val lit = s.ignite[k]
            val color = mix(NC.kcal.a(0.55f), NC.kcal, lit).a(s.textAlpha)
            val width = 1.8f + 0.9f * lit
            for (j in segs.indices step 4) {
                val len = minOf(segs[j + 1] - segs[j], left)
                if (len <= 0f) break
                val yy = oy + segs[j + 3] - 2.5f
                drawLine(color, Offset(ox + segs[j], yy), Offset(ox + segs[j] + len, yy), width, StrokeCap.Round)
                left -= len
            }
        }

        // Cursor
        if (s.caret > 0.01f) {
            val txt = geo.fieldText
            val n = s.typed.coerceAtMost(NutricionMeal.Sentence.length)
            val cx = if (n == 0) 0f else txt.getHorizontalPosition(n, true)
            val ly = if (n == 0) 0f else txt.getLineTop(txt.getLineForOffset(n))
            rr(ox + cx - 0.2f, oy + ly + 1.5f, 1.6f, 17f, 0.8f, NC.ink.a(s.caret))
        }

        // El destello que lee la frase
        if (s.shimmer > 0f && s.shimmer < 1f) {
            val bx = lerpF(x - 44f, x + w + 44f, eInOut(s.shimmer))
            val strength = sin(PI.toFloat() * s.shimmer)
            val brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.a(0.30f * strength), Color.Transparent),
                startX = bx - 42f,
                endX = bx + 42f,
            )
            drawRect(brush, Offset(bx - 42f, y), Size(84f, h))
            drawSpark(bx - 6f, y + 20f, 3.8f * strength, NC.ink.a(0.9f * strength))
            drawSpark(bx + 16f, y + h - 22f, 2.6f * strength, NC.kcal.a(0.95f * strength))
        }
    }
}

private fun DrawScope.drawLayout(layout: TextLayoutResult, color: Color, x: Float, y: Float) {
    if (color.alpha <= 0.003f) return
    drawText(layout, color = color, topLeft = Offset(x, y))
}

// ── chips: las palabras de la frase se convierten en ellos, en el mismo campo

private fun DrawScope.drawChips(s: NutricionSheetFrame, kit: NutricionKit, geo: NutricionSheetGeometry) {
    val l = NutricionLayout
    for (k in 0..3) {
        val p = s.chips[k]
        if (p <= 0f) continue
        val food = NutricionMeal.foods[k]
        val tw = geo.chipW[k]
        val tx = geo.chipX[k]
        val ty = l.FieldTop + l.ChipsInset + geo.chipRow[k] * (l.ChipH + l.ChipRowGap)

        // Nace sobre su palabra (coordenadas de la hoja), crece hasta su forma de chip y se asienta con un frenado suave.
        val src = geo.source[k]
        val sx = l.Margin + FieldPadX + src[0]
        val sy = l.FieldTop + FieldPadY + src[1] + 2f
        val e = eOutCubic(p)
        val x = lerpF(sx, tx, e)
        val y = lerpF(sy, ty, e) - sin(PI.toFloat() * p) * 6f
        val w = lerpF(src[2], tw, e)
        val h = lerpF(LineH - 4f, l.ChipH, e)
        val appear = clamp01((p - 0.04f) / 0.30f)
        val content = clamp01((p - 0.14f) / 0.36f)
        val pop = 1f + 0.05f * sin(PI.toFloat() * clamp01((p - 0.6f) / 0.4f))

        withTransform({ scale(pop, pop, Offset(x + w / 2f, y + h / 2f)) }) {
            rr(x, y, w, h, h / 2f, Color(0xFF26282C).a(appear))
            rrLine(x, y, w, h, h / 2f, mix(NC.kcal.a(0.9f), Color.White.a(0.18f), e).a(appear), 1f)
            if (content > 0f) {
                // El contenido va anclado a la izquierda y lo recorta la forma del chip: se «descubre» al crecer.
                clipRect(x, y, x + w, y + h) {
                    drawFoodIcon(food.id, x + 7f + ChipIcon / 2f, y + h / 2f, ChipIcon + 1f, content)
                    val tx0 = x + 7f + ChipIcon + 4f
                    nTextMid(kit, NT.Chip, food.chipName, tx0, y + h / 2f, NC.ink.a(content))
                    if (food.chipQty.isNotEmpty()) {
                        nTextMid(kit, NT.ChipQty, food.chipQty, tx0 + kit.width(NT.Chip, food.chipName) + 3f, y + h / 2f, NC.kcal.a(content))
                    }
                }
            }
        }
    }
}

// ── filas de alimentos

private val MacroLetters = arrayOf("P", "H", "G")

private fun DrawScope.drawRow(i: Int, y: Float, s: NutricionSheetFrame, kit: NutricionKit) {
    val inP = s.rowIn[i]
    if (inP <= 0f) return
    val l = NutricionLayout
    val food = NutricionMeal.foods[i]
    val e = eOutBack(inP)
    val a = clamp01(inP * 1.9f)
    val m = l.Margin
    val w = l.Width - 2f * m
    withTransform({ translate(0f, (1f - e) * 16f) }) {
        rr(m, y, w, l.RowH, 14f, Color.White.a(0.055f * a))
        rrLine(m, y, w, l.RowH, 14f, Color.White.a(0.08f * a), 1f)

        // Dibujo del alimento: aparece con un pequeño rebote un instante después que la tarjeta.
        val iconPop = eOutBack(clamp01((inP - 0.18f) / 0.82f))
        rr(m + 9f, y + 9f, 36f, 36f, 11f, Color.White.a(0.085f * a))
        drawFoodIcon(food.id, m + 27f, y + 27f, 28f * iconPop, a)

        // Nombre y cantidad estimada
        nTextMid(kit, NT.RowName, food.name, m + 56f, y + 17f, NC.ink.a(a))
        nTextMid(kit, NT.RowQty, food.amount, m + 56f, y + 33f, NC.muted.a(clamp01((inP - 0.25f) / 0.6f)))

        // kcal que ruedan hasta su valor
        val kc = clamp01((inP - 0.1f) / 0.5f)
        nTextMid(kit, NT.RowKcal, s.rowKcal[i].toString(), m + w - 12f, y + 19f, NC.ink.a(kc), 1f)
        nTextMid(kit, NT.RowUnit, "kcal", m + w - 12f, y + 35f, NC.muted.a(kc), 1f)

        // Minibarras de proteína, hidratos y grasas
        for (j in 0..2) {
            val bx = m + 56f + 58f * j
            val color = NC.rings[j + 1]
            val fill = s.rowBars[i * 3 + j]
            nTextMid(kit, NT.BarLetter, MacroLetters[j], bx, y + 45.7f, color.a(a))
            rr(bx + 9f, y + 44f, 44f, 3.4f, 1.7f, color.a(0.16f * a))
            if (fill > 0.003f) rr(bx + 9f, y + 44f, maxOf(44f * fill, 3.4f), 3.4f, 1.7f, color.a(a))
        }
    }
}

// ── totales y botón

private fun DrawScope.drawTotals(s: NutricionSheetFrame, kit: NutricionKit, y: Float) {
    val a = clamp01(s.rowIn[0] * 2f)
    if (a <= 0f) return
    val l = NutricionLayout
    val mid = y + l.TotalH / 2f
    // Un filete encima: es el pie de la lista, un total que se va sumando con cada alimento.
    drawLine(Color.White.a(0.08f * a), Offset(l.Margin, y - 6f), Offset(l.Width - l.Margin, y - 6f), 1f)
    val values = intArrayOf(s.totalProtein, s.totalCarbs, s.totalFat)
    var x = l.Margin
    for (j in 0..2) {
        val text = " ${values[j]} g"
        nTextMid(kit, NT.Total, MacroLetters[j], x, mid, NC.rings[j + 1].a(a))
        val lw = kit.width(NT.Total, MacroLetters[j])
        nTextMid(kit, NT.Total, text, x + lw, mid, NC.ink.a(0.82f * a))
        x += lw + kit.width(NT.Total, text) + 12f
    }
    nTextMid(kit, NT.RowUnit, "kcal", l.Width - l.Margin, mid + 3f, NC.muted.a(a), 1f)
    nTextMid(kit, NT.TotalKcal, s.totalKcal.toString(), l.Width - l.Margin - kit.width(NT.RowUnit, "kcal") - 3f, mid, NC.ink.a(a), 1f)
}

private fun DrawScope.drawCta(s: NutricionSheetFrame, kit: NutricionKit, y: Float) {
    val k = s.ctaIn
    if (k <= 0f) return
    val l = NutricionLayout
    val a = clamp01(k * 1.6f)
    val rise = (1f - eOutCubic(k)) * 18f
    val press = s.ctaPress
    val sc = 1f - 0.03f * press
    val cy = y + rise + l.CtaH / 2f
    withTransform({ scale(sc, sc, Offset(l.Width / 2f, cy)) }) {
        rr(l.Margin, y + rise, l.Width - 2f * l.Margin, l.CtaH, 15f, mix(NC.ink, Color(0xFFCFCBC3), press).a(a))
        nTextMid(kit, NT.Cta, "Guardar comida", l.Width / 2f, cy, NC.onInk.a(a), 0.5f)
    }
}

// ── teclado

private const val KbMargin = 5f
private const val KbGap = 4.5f
private const val KbKeyH = 34f
private const val KbPitch = 40f
private val KbRows = arrayOf("1234567890", "qwertyuiop", "asdfghjklñ", "zxcvbnm")

private val ShiftPath by lazy { PathParser().parsePathString("M0-5.2L-5.4 0H-2.2V5H2.2V0H5.4Z").toPath() }
private val BackPath by lazy { PathParser().parsePathString("M-6.5 0L-2.8-5H6.5V5H-2.8Z").toPath() }

private fun keyOf(c: Char): Char = when (c) {
    'é', 'É' -> 'e'
    else -> c.lowercaseChar()
}

internal fun DrawScope.drawNutricionKeyboard(s: NutricionSheetFrame, kit: NutricionKit) {
    val kb = s.keyboard
    if (kb <= 0.001f) return
    val l = NutricionLayout
    val top = l.KeyboardTop + (1f - eOutCubic(kb)) * (l.Height - l.KeyboardTop + 16f)

    drawRect(NC.keyboard, Offset(0f, top), Size(l.Width, l.Height - top + 40f))
    drawLine(Color.White.a(0.06f), Offset(0f, top), Offset(l.Width, top), 1f)

    fun glow(c: Char): Float {
        var g = 0f
        for (i in 0..3) if (s.keyGlow[i] > 0f && keyOf(s.keyChars[i]) == c) g = max(g, s.keyGlow[i])
        return g
    }

    val kw = (l.Width - 2f * KbMargin - 9f * KbGap) / 10f
    var y = top + 10f

    // Cifras, y las tres filas de letras
    for (r in 0..2) {
        val letters = KbRows[r]
        for (j in letters.indices) {
            val c = letters[j]
            drawKey(kit, c.toString(), KbMargin + j * (kw + KbGap), y, kw, glow(c), NC.key)
        }
        y += KbPitch
    }

    // Mayúsculas, letras, borrar
    val specialW = 36f
    val row3 = KbRows[3]
    val rowW = specialW * 2f + KbGap * 2f + row3.length * kw + (row3.length - 1) * KbGap
    var x = (l.Width - rowW) / 2f
    drawKey(kit, "", x, y, specialW, 0f, NC.keySpecial)
    drawPathIcon(ShiftPath, x + specialW / 2f, y + KbKeyH / 2f, NC.ink.a(0.85f))
    x += specialW + KbGap
    for (c in row3) {
        drawKey(kit, c.toString(), x, y, kw, glow(c), NC.key)
        x += kw + KbGap
    }
    drawKey(kit, "", x, y, specialW, 0f, NC.keySpecial)
    drawPathIcon(BackPath, x + specialW / 2f, y + KbKeyH / 2f, NC.ink.a(0.85f), outline = true)
    y += KbPitch

    // ?123 · coma · espacio · punto · intro
    val bw = l.Width - 2f * KbMargin - specialW * 2f - kw * 2f - KbGap * 4f
    x = KbMargin
    drawKey(kit, "?123", x, y, specialW, 0f, NC.keySpecial, NT.KeySmall)
    x += specialW + KbGap
    drawKey(kit, ",", x, y, kw, glow(','), NC.keySpecial)
    x += kw + KbGap
    drawKey(kit, "Español", x, y, bw, glow(' '), NC.key, NT.KeySmall)
    x += bw + KbGap
    drawKey(kit, ".", x, y, kw, 0f, NC.keySpecial)
    x += kw + KbGap
    val enter = s.enterGlow
    drawKey(kit, "", x, y, specialW, 0f, mix(NC.kcal.a(0.92f), Color(0xFF8CCBFA), enter), null)
    drawCheck(x + specialW / 2f, y + KbKeyH / 2f, 11f, NC.onInk, 2f)
}

private fun DrawScope.drawKey(kit: NutricionKit, label: String, x: Float, y: Float, w: Float, glow: Float, base: Color, st: NT? = NT.Key) {
    val fill = if (glow > 0.003f) mix(base, NC.keyLit, glow) else base
    rr(x, y, w, KbKeyH, 7f, fill)
    if (label.isNotEmpty() && st != null) {
        val color = if (st == NT.KeySmall) NC.ink.a(0.7f) else NC.ink
        nTextMid(kit, st, label, x + w / 2f, y + KbKeyH / 2f, color, 0.5f)
    }
}

private fun DrawScope.drawPathIcon(path: Path, cx: Float, cy: Float, color: Color, outline: Boolean = false) {
    withTransform({ translate(cx, cy) }) {
        if (outline) {
            drawPath(path, color, style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color, Offset(-0.5f, -2.2f), Offset(3.4f, 2.2f), 1.3f, StrokeCap.Round)
            drawLine(color, Offset(3.4f, -2.2f), Offset(-0.5f, 2.2f), 1.3f, StrokeCap.Round)
        } else {
            drawPath(path, color)
        }
    }
}
