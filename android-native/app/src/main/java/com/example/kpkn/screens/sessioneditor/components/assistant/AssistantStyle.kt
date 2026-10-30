package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import kotlin.math.min

/**
 * Piel del asistente de sesión (lienzo «Pulido KPKN v1», paquete `as`, `as.css`): colores, tipografía y medidas del lienzo en dp
 * (1 px del lienzo = 1 dp), sin sombras ni vidrio ni movimiento (eso es de la pasada B/C: cada sitio lleva su marca
 * `// pulido:<ID>`).
 */
internal object AsColors {
    val sheet = Color(0xFF1B1B1D)
    val module = Color(0xFF121214)
    val cell = Color(0xFF242427)
    val inset = Color(0xFF0E0E10)
    val segOn = Color(0xFF2A2A2D)
    val menu = Color(0xFF2A2A2D)
    val decision = Color(0xFF222225)
    val toast = Color(0xFF2C2C30)
    val card = Color(0xFF0B0B0C)
    val pressed = Color(0xFF161618)

    val ink = Color(0xFFF2EEE6)
    val muted = Color(0xFFC4BFB6)
    val faint = Color(0xFF8E8980)
    val dim = Color(0xFF5F5C57)
    val energy = Color(0xFFF7CF73)
    val done = Color(0xFF43D18C)
    val danger = Color(0xFFFF9B92)
    val cardio = Color(0xFF8FB2FF)
    val onInk = Color(0xFF111111)

    /** Blanco con el alfa de un `#ffffffNN` del lienzo. */
    fun white(alpha: Int): Color = Color.White.copy(alpha = alpha / 255f)

    /** Un color del lienzo con el alfa de su sufijo hexadecimal (`#43d18c1a` = `tint(done, 0x1a)`). */
    fun tint(base: Color, alpha: Int): Color = base.copy(alpha = alpha / 255f)

    val line = white(0x10)
    val scrim = Color.Black.copy(alpha = 0xb0 / 255f)
}

internal val AsInter: FontFamily = WizardFonts.body
internal val AsSyne: FontFamily = WizardFonts.display

/**
 * Tamaño de letra del lienzo: `calc(Npx*min(var(--fs),cap))`. [cap] frena la escala de letra (el 130 % de las pruebas); sin tope,
 * `calc(Npx*var(--fs))`.
 */
@Composable
internal fun asSp(size: Float, cap: Float? = null): TextUnit {
    val fs = LocalDensity.current.fontScale
    if (cap == null || fs <= cap) return size.sp
    return (size * cap / fs).sp
}

/** Un texto con los parámetros del CSS del lienzo: `font: weight size/line-height family`, `letter-spacing` en px. */
@Composable
internal fun AsText(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = AsColors.ink,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = AsInter,
    cap: Float? = null,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    tabular: Boolean = false,
    uppercase: Boolean = false,
    /** `white-space: nowrap` del lienzo: una línea que no se parte. */
    noWrap: Boolean = false,
) {
    val fs = LocalDensity.current.fontScale
    val effective = if (cap == null) fs else min(fs, cap)
    // Una línea con `line-height` fijo mide justo eso (la caja de línea del CSS), sea cual sea la altura natural de la fuente.
    val fixedHeight = if (lineHeight != null && (maxLines == 1 || noWrap)) with(LocalDensity.current) { (size * effective / fs * lineHeight).sp.toDp() } else null
    Text(
        text = if (uppercase) text.uppercase() else text,
        modifier = if (fixedHeight != null) modifier.height(fixedHeight) else modifier,
        color = color,
        style = TextStyle(
            fontFamily = family,
            fontWeight = weight,
            fontSize = asSp(size, cap),
            lineHeight = if (lineHeight != null) (size * effective / fs * lineHeight).sp else TextUnit.Unspecified,
            // CSS reparte el interlineado a partes iguales por encima y por debajo (half-leading) y no recorta nada.
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            letterSpacing = if (letterSpacing != null) (letterSpacing / fs).sp else TextUnit.Unspecified,
            textAlign = align ?: TextAlign.Unspecified,
            fontFeatureSettings = if (tabular) "'tnum' 1, 'lnum' 1" else null,
        ),
        maxLines = if (noWrap) 1 else maxLines,
        softWrap = !noWrap,
        overflow = overflow,
    )
}

/** Cifras de Syne del lienzo: `font: 800 Npx/1 Syne`, números tabulares y de la altura de las mayúsculas. */
@Composable
internal fun AsFigure(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = AsColors.ink,
    weight: FontWeight = FontWeight.ExtraBold,
    cap: Float? = null,
    letterSpacing: Float? = null,
    maxLines: Int = 1,
) = AsText(
    text = text,
    size = size,
    modifier = modifier,
    color = color,
    weight = weight,
    family = AsSyne,
    cap = cap,
    lineHeight = 1f,
    letterSpacing = letterSpacing,
    tabular = true,
    maxLines = maxLines,
)

// ── Iconos del lienzo (retícula de 24, trazo de 1,75; los mismos trazados que `P` en `artifact.html`) ──────────────────────

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun svg(name: String, paths: List<String>, dots: List<String> = emptyList()): ImageVector {
    val ink = SolidColor(Color.Black)
    val builder = ImageVector.Builder(name = "As.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    for (d in paths) {
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = ink,
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    for (d in dots) builder.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = ink)
    return builder.build()
}

internal object AsIcons {
    val Back by lazy { svg("back", listOf("M15 5l-7 7 7 7")) }
    val Forward by lazy { svg("fwd", listOf("M9 5l7 7-7 7")) }
    val Down by lazy { svg("down", listOf("M5 9l7 7 7-7")) }
    val Check by lazy { svg("check", listOf("M5 12.5l4.5 4.5L19 7.5")) }
    val Close by lazy { svg("close", listOf("M6 6l12 12M18 6L6 18")) }
    val Plus by lazy { svg("plus", listOf("M12 5v14M5 12h14")) }
    val Minus by lazy { svg("asMinus", listOf("M6 12h12")) }
    val Search by lazy { svg("search", listOf(circle(11f, 11f, 6.5f), "M16 16l4 4")) }
    val Filter by lazy { svg("filter", listOf("M4 6h16M7 12h10M10 18h4")) }
    val More by lazy { svg("more", emptyList(), listOf(circle(5f, 12f, 1.3f), circle(12f, 12f, 1.3f), circle(19f, 12f, 1.3f))) }
    val Edit by lazy { svg("edit", listOf("M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4")) }
    val Trash by lazy { svg("trash", listOf("M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13")) }
    val Info by lazy { svg("info", listOf(circle(12f, 12f, 8.5f), "M12 11v5M12 8v.01")) }
    val Book by lazy { svg("book", listOf("M5 4.5h10a4 4 0 014 4V20H9a4 4 0 01-4-4z", "M5 16a4 4 0 014-4h10")) }
    val Layers by lazy { svg("asLayers", listOf("M12 4l8.5 4.5L12 13 3.5 8.5z", "M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5")) }
    val Spark by lazy { svg("spark", listOf("M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z")) }
    val Train by lazy { svg("train", listOf("M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12")) }
    val Undo by lazy { svg("asUndo", listOf("M4 9h10a5.5 5.5 0 010 11H8", "M8 5L4 9l4 4")) }
    val Lock by lazy { svg("asLock", listOf("M8 10.5h8a3 3 0 013 3v4a3 3 0 01-3 3H8a3 3 0 01-3-3v-4a3 3 0 013-3z", "M8 10.5V8a4 4 0 018 0v2.5")) }
    val Archive by lazy { svg("asArchive", listOf("M5 4.5h14a1.5 1.5 0 011.5 1.5v1.5A1.5 1.5 0 0119 9H5a1.5 1.5 0 01-1.5-1.5V6A1.5 1.5 0 015 4.5z", "M5 9v8a3 3 0 003 3h8a3 3 0 003-3V9M10 13h4")) }
    val Pulse by lazy { svg("asPulse", listOf("M3 12h4l2-5 4 10 2-5h6")) }
    val Warn by lazy { svg("asWarn", listOf("M12 4l9 15.5H3z", "M12 10v4.5M12 17.2v.01")) }
    val Save by lazy { svg("asSave", listOf("M5 4.5h11l3 3V19a1.5 1.5 0 01-1.5 1.5h-12A1.5 1.5 0 014 19V6a1.5 1.5 0 011-1.5z", "M8 4.5v4h7v-4M8 20v-6h8v6")) }
}

/** Un icono del lienzo a [size] dp, teñido con [tint]. */
@Composable
internal fun AsIcon(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Image(imageVector = icon, contentDescription = null, colorFilter = filter, modifier = modifier.size(size))
}


/**
 * Una fila de columnas `fr` de la rejilla del lienzo (`grid-template-columns: 1fr 1fr 1.25fr`): cada columna recibe su parte, pero
 * no baja de su contenido mínimo (como `minmax(auto, 1fr)`); las que no caben se quedan en su mínimo y el resto se reparte lo que
 * sobra. Todas las celdas miden lo mismo de alto (para que los separadores lleguen de arriba abajo).
 */
@Composable
internal fun AsFrRow(weights: List<Float>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val total = constraints.maxWidth
        val n = measurables.size
        val mins = measurables.map { it.minIntrinsicWidth(constraints.maxHeight) }
        val fixed = BooleanArray(n)
        val widths = IntArray(n)
        var changed = true
        while (changed) {
            changed = false
            val free = total - (0 until n).filter { fixed[it] }.sumOf { widths[it] }
            val sum = (0 until n).filter { !fixed[it] }.sumOf { weights[it].toDouble() }
            if (sum <= 0.0) break
            val fr = free / sum
            val offender = (0 until n).firstOrNull { !fixed[it] && mins[it] > weights[it] * fr }
            if (offender != null) {
                fixed[offender] = true
                widths[offender] = mins[offender]
                changed = true
            }
        }
        val free = total - (0 until n).filter { fixed[it] }.sumOf { widths[it] }
        val sum = (0 until n).filter { !fixed[it] }.sumOf { weights[it].toDouble() }
        for (i in 0 until n) if (!fixed[i]) widths[i] = if (sum > 0.0) (free * weights[i] / sum).toInt() else 0
        val height = measurables.mapIndexed { i, m -> m.maxIntrinsicHeight(widths[i]) }.maxOrNull() ?: 0
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
        layout(total, height) {
            var x = 0
            placeables.forEach { it.placeRelative(x, 0); x += it.width }
        }
    }
}
