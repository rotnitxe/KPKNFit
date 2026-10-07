package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.theme.Syne

/*
 * Escena de Nutrición · herramientas de dibujo compartidas: colores, estilos de texto con caché, dibujos de
 * alimentos y piezas sueltas (dedo, barra de estado). Todo se dibuja en el lienzo lógico de 300 × 620 (la escena
 * escala el Canvas una sola vez), y el texto se mide con densidad 1 para que 1 sp = 1 unidad del lienzo.
 */

/** Colores de la escena de Nutrición: los de [WelcomeScenePalette] más los de la hoja y el teclado. */
internal object NC {
    val screen = WelcomeScenePalette.screen
    val ink = WelcomeScenePalette.ink
    val muted = WelcomeScenePalette.muted
    val ok = WelcomeScenePalette.ok
    val kcal = WelcomeScenePalette.calorias
    val protein = WelcomeScenePalette.proteina
    val carbs = WelcomeScenePalette.hidratos
    val fat = WelcomeScenePalette.grasas

    /** Los cuatro anillos, de fuera hacia dentro: calorías, proteína, hidratos, grasas. */
    val rings = arrayOf(kcal, protein, carbs, fat)

    val sheet = Color(0xFF17181B)
    val onInk = Color(0xFF0B0B0B)
    val keyboard = Color(0xFF1D1E21)
    val key = Color(0xFF2E3034)
    val keySpecial = Color(0xFF25272A)
    val keyLit = Color(0xFF5A5E67)
}

private fun body(size: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.sp,
)

private fun display(size: Float, spacing: Float = 0f) = TextStyle(
    fontFamily = Syne,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    letterSpacing = spacing.sp,
)

/** Estilos de texto de la escena (tamaños en unidades del lienzo). Syne solo en títulos y cifras grandes. */
internal enum class NT(val size: Float, val style: TextStyle) {
    Status(11f, body(11f, FontWeight.SemiBold)),
    Hoy(10.5f, body(10.5f, FontWeight.Medium)),
    Title(22f, display(22f, -0.22f)),
    Tag(8f, body(8f, FontWeight.SemiBold, 0.9f)),
    Caption(11.5f, body(11.5f, FontWeight.SemiBold)),
    RingNum(24f, display(24f, -0.3f)),
    RingUnit(10f, body(10f, FontWeight.Medium)),
    Goal(10.5f, body(10.5f, FontWeight.Medium)),
    Legend(11.5f, body(11.5f, FontWeight.SemiBold)),
    LegendVal(11.5f, body(11.5f, FontWeight.Medium)),
    Pill(10.5f, body(10.5f, FontWeight.SemiBold)),
    Section(10.5f, body(10.5f, FontWeight.SemiBold, 0.4f)),
    CardTitle(13f, body(13f, FontWeight.SemiBold)),
    CardMeta(10.5f, body(10.5f, FontWeight.Normal)),
    CardKcal(15f, display(15f)),
    MiniName(10f, body(10f, FontWeight.Medium)),
    MiniKcal(10f, body(10f, FontWeight.SemiBold)),
    Fab(12.5f, body(12.5f, FontWeight.SemiBold)),
    SheetTitle(16f, display(16f, -0.15f)),
    Field(13.5f, body(13.5f, FontWeight.Normal).copy(lineHeight = 20.sp)),
    Chip(11f, body(11f, FontWeight.Medium)),
    ChipQty(11f, body(11f, FontWeight.SemiBold)),
    RowName(13f, body(13f, FontWeight.SemiBold)),
    RowQty(11f, body(11f, FontWeight.Normal)),
    RowKcal(17f, display(17f)),
    RowUnit(9f, body(9f, FontWeight.Medium)),
    BarLetter(8.5f, body(8.5f, FontWeight.SemiBold)),
    Total(10.5f, body(10.5f, FontWeight.Medium)),
    TotalKcal(17f, display(17f)),
    Cta(14.5f, body(14.5f, FontWeight.SemiBold)),
    Key(14f, body(14f, FontWeight.Medium)),
    KeySmall(10f, body(10f, FontWeight.Medium)),
}

/**
 * Mide y guarda en caché los textos de la escena: las cifras que ruedan son muchas cadenas distintas, y medirlas
 * en cada cuadro pesaría. La densidad es 1 a propósito: los tamaños de [NT] son unidades del lienzo lógico.
 */
internal class NutricionKit(resolver: FontFamily.Resolver) {
    private val measurer = TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr, 0)
    private val cache = Array(NT.entries.size) { HashMap<String, TextLayoutResult>() }

    fun layout(st: NT, text: String): TextLayoutResult =
        cache[st.ordinal].getOrPut(text) { measurer.measure(text, st.style) }

    /** Texto en varias líneas (ancho máximo en unidades del lienzo) o en una con puntos suspensivos. */
    fun block(st: NT, text: String, maxWidth: Float, maxLines: Int = Int.MAX_VALUE): TextLayoutResult =
        measurer.measure(
            text = text,
            style = st.style,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = maxWidth.toInt()),
        )

    fun width(st: NT, text: String): Float = layout(st, text).size.width.toFloat()
}

// ── texto

/** Dibuja [s] con su línea base en [baseline]; `anchor` 0 = x es el borde izquierdo, 0,5 = el centro, 1 = el derecho. */
internal fun DrawScope.nText(kit: NutricionKit, st: NT, s: String, x: Float, baseline: Float, color: Color, anchor: Float = 0f) {
    if (color.alpha <= 0.003f || s.isEmpty()) return
    val l = kit.layout(st, s)
    drawText(l, color = color, topLeft = Offset(x - l.size.width * anchor, baseline - l.firstBaseline))
}

/** Como [nText] pero centrando las mayúsculas en la vertical [yMid]. */
internal fun DrawScope.nTextMid(kit: NutricionKit, st: NT, s: String, x: Float, yMid: Float, color: Color, anchor: Float = 0f) =
    nText(kit, st, s, x, yMid + st.size * 0.36f, color, anchor)

// ── formas

internal fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color) {
    if (color.alpha <= 0.003f || w <= 0f || h <= 0f) return
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(minOf(r, h / 2f, w / 2f)))
}

internal fun DrawScope.rrLine(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, width: Float = 1f) {
    if (color.alpha <= 0.003f || w <= 0f || h <= 0f) return
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(minOf(r, h / 2f, w / 2f)), style = Stroke(width))
}

internal fun Color.a(alpha: Float): Color = copy(alpha = (this.alpha * alpha).coerceIn(0f, 1f))

private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

// ── dibujos de los alimentos (cuadrícula centrada en 0, ≈ ±10)

private class IconPart(val path: Path, val color: Color, val stroke: Float = 0f)

private val EggParts by lazy {
    listOf(
        IconPart(parse("M-7-2c0-5 5-7 8-5 3-2 8 0 7 5 3 3 0 8-4 8-2 2-6 2-8 0-4 0-6-4-3-8z"), Color(0xFFF7F3EA)),
        IconPart(parse("M-2.9 0a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0-6.8 0"), Color(0xFFF5B82E)),
    )
}
private val EggShade by lazy { IconPart(parse("M-7-2c0-5 5-7 8-5 3-2 8 0 7 5 3 3 0 8-4 8-2 2-6 2-8 0-4 0-6-4-3-8z"), Color(0xFFD9D2C0)) }

private val AvocadoParts by lazy {
    listOf(
        IconPart(parse("M0-9.5c3.6 0 4.6 4.2 5.7 7.2 1.5 4.2 2 9.3-5.7 9.3s-7.2-5.1-5.7-9.3c1-3 2.1-7.2 5.7-7.2z"), Color(0xFF5E9E4A)),
        IconPart(parse("M0-6.6c2.5 0 3.1 3.1 3.9 5.3 1 3.1 1.3 6.5-3.9 6.5s-4.9-3.4-3.9-6.5C-3.1-3.5-2.5-6.6 0-6.6z"), Color(0xFFD7E8A2)),
        IconPart(parse("M-2.9 1.6a2.9 2.9 0 1 0 5.8 0a2.9 2.9 0 1 0-5.8 0"), Color(0xFF8A5A3B)),
    )
}

private val ToastParts by lazy {
    listOf(
        IconPart(
            parse("M-8-1.4C-10.3-2.6-10.1-7-6-8.3-3-9.3 3-9.3 6-8.3 10.1-7 10.3-2.6 8-1.4L8 6.7C8 8.1 7 9.1 5.6 9.1L-5.6 9.1C-7 9.1-8 8.1-8 6.7Z"),
            Color(0xFFB9783A),
        ),
        IconPart(
            parse("M-6.3-2.5C-7.7-3.5-7.5-6.3-4.8-7.1-2.6-7.8 2.6-7.8 4.8-7.1 7.5-6.3 7.7-3.5 6.3-2.5L6.3 6.3C6.3 7 5.8 7.5 5.1 7.5L-5.1 7.5C-5.8 7.5-6.3 7-6.3 6.3Z"),
            Color(0xFFE2B87A),
        ),
        IconPart(parse("M-3.6-1.2a.7 .7 0 1 0 1.4 0a.7 .7 0 1 0-1.4 0M1.4 0a.7 .7 0 1 0 1.4 0a.7 .7 0 1 0-1.4 0M-1 3.8a.7 .7 0 1 0 1.4 0a.7 .7 0 1 0-1.4 0M3.4 4.4a.7 .7 0 1 0 1.4 0a.7 .7 0 1 0-1.4 0M-4.4 4.6a.7 .7 0 1 0 1.4 0a.7 .7 0 1 0-1.4 0"), Color(0xFF8C5A2B)),
    )
}

private val CoffeeParts by lazy {
    listOf(
        IconPart(parse("M5.6-1.2H7.6C10.4-1.2 10.4 4.8 7.2 4.8H5.2"), Color(0xFFF2EEE6), 1.7f),
        IconPart(parse("M-7.6-2.2H5.6V3.2C5.6 7 3 9.2-1 9.2S-7.6 7-7.6 3.2Z"), Color(0xFFF2EEE6)),
        IconPart(parse("M-7.6-2.2a6.6 2 0 1 0 13.2 0a6.6 2 0 1 0-13.2 0"), Color(0xFFB07A4F)),
        IconPart(parse("M-9.2 10.6H8.4"), Color(0xFF9A958D), 1.5f),
        IconPart(parse("M-3.2-4.6C-4.6-6.4-1.8-7.2-3.2-9.2M1.2-4.6C-.2-6.4 2.6-7.2 1.2-9.2"), Color(0xFFF2EEE6).copy(alpha = 0.55f), 1.2f),
    )
}

private fun DrawScope.drawParts(parts: List<IconPart>, alpha: Float) {
    for (part in parts) {
        val c = part.color.a(alpha)
        if (part.stroke > 0f) {
            drawPath(part.path, c, style = Stroke(part.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawPath(part.path, c)
        }
    }
}

/** Dibujo de un alimento centrado en ([cx], [cy]) dentro de un cuadrado de lado [size]. */
internal fun DrawScope.drawFoodIcon(id: NutricionFoodId, cx: Float, cy: Float, size: Float, alpha: Float = 1f) {
    if (alpha <= 0.003f || size <= 0f) return
    val s = size / 24f
    withTransform({ translate(cx, cy); scale(s, s, Offset.Zero) }) {
        when (id) {
            NutricionFoodId.HUEVOS -> {
                // Dos huevos: el de atrás asoma arriba a la derecha.
                withTransform({ translate(3.6f, -3.4f); scale(.82f, .82f, Offset.Zero); rotate(10f, Offset.Zero) }) {
                    drawPath(EggShade.path, EggShade.color.a(alpha))
                    drawParts(EggParts, alpha * .9f)
                }
                withTransform({ translate(-2.6f, 2.6f); rotate(-6f, Offset.Zero) }) { drawParts(EggParts, alpha) }
            }
            NutricionFoodId.PALTA -> withTransform({ rotate(-14f, Offset.Zero) }) { drawParts(AvocadoParts, alpha) }
            NutricionFoodId.PAN -> {
                withTransform({ translate(3f, -3f); rotate(8f, Offset.Zero) }) { drawParts(ToastParts, alpha * .55f) }
                withTransform({ translate(-2f, 2f); rotate(-4f, Offset.Zero) }) { drawParts(ToastParts, alpha) }
            }
            NutricionFoodId.CAFE -> drawParts(CoffeeParts, alpha)
        }
    }
}

// ── piezas sueltas

private val CheckPath by lazy { parse("M-4.2 .4L-1.3 3.4L4.4-3") }
private val SparkPath by lazy { parse("M0-5L1.2-1.2 5 0 1.2 1.2 0 5-1.2 1.2-5 0-1.2-1.2Z") }

/** Una marca de «hecho» de trazo redondeado centrada en ([cx], [cy]); `size` es el lado de su caja. */
internal fun DrawScope.drawCheck(cx: Float, cy: Float, size: Float, color: Color, width: Float = 1.8f) {
    val s = size / 10f
    withTransform({ translate(cx, cy); scale(s, s, Offset.Zero) }) {
        drawPath(CheckPath, color, style = Stroke(width / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Destello de cuatro puntas centrado en ([cx], [cy]) de radio [r]. */
internal fun DrawScope.drawSpark(cx: Float, cy: Float, r: Float, color: Color) {
    if (r <= 0f || color.alpha <= 0.003f) return
    val s = r / 5f
    withTransform({ translate(cx, cy); scale(s, s, Offset.Zero) }) { drawPath(SparkPath, color) }
}
