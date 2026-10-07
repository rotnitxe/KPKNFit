package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.ui.theme.Syne
import kotlin.math.max

/*
 * Herramientas de dibujo de la escena de Entreno: colores, estilos de texto con su caché, iconos y el «pincel»
 * ([EntrenoPen]) que dibuja en unidades del lienzo lógico (300 × 620). Nada de esto decide QUÉ se ve: eso lo
 * hace [entrenoFrameAt]; aquí solo se pinta.
 */

/** Colores de la escena: la paleta del kit más los neutros de las hojas. */
internal object ECol {
    val screen = WelcomeScenePalette.screen
    val sheet = Color(0xFF151618)
    val sheetHi = Color(0xFF1A1B1E)
    val panel = WelcomeScenePalette.panel
    val line = WelcomeScenePalette.line
    val ink = WelcomeScenePalette.ink
    val muted = WelcomeScenePalette.muted
    val ok = WelcomeScenePalette.ok
    val gold = WelcomeScenePalette.energia
    val rest = WelcomeScenePalette.columna
    val onInk = Color(0xFF0B0B0B)
    val onOk = Color(0xFF08130D)
    val dock = Color(0xFF121820)
    val white = Color.White
    val black = Color.Black
}

/** Estilos de texto de la escena. Syne (marca) solo para títulos y números grandes; Inter para el resto. */
internal enum class EFont(family: FontFamily, weight: FontWeight, val size: Float, tracking: Float = 0f) {
    Hero(Syne, FontWeight.ExtraBold, 30f, -0.4f),
    Name(Syne, FontWeight.ExtraBold, 18f, -0.2f),
    Big24(Syne, FontWeight.ExtraBold, 24f, -0.2f),
    Num17(Syne, FontWeight.ExtraBold, 17f),
    Title17(Syne, FontWeight.ExtraBold, 17f, -0.1f),
    Title16(Syne, FontWeight.ExtraBold, 16f),
    Title15(Syne, FontWeight.ExtraBold, 15f, -0.1f),
    Title14(Syne, FontWeight.ExtraBold, 14f, -0.1f),
    B15(WizardFonts.body, FontWeight.SemiBold, 15f),
    B14(WizardFonts.body, FontWeight.SemiBold, 14f),
    B13(WizardFonts.body, FontWeight.SemiBold, 13f),
    B12(WizardFonts.body, FontWeight.SemiBold, 12f),
    B115(WizardFonts.body, FontWeight.SemiBold, 11.5f),
    B105(WizardFonts.body, FontWeight.SemiBold, 10.5f),
    B10(WizardFonts.body, FontWeight.SemiBold, 10f),
    M15(WizardFonts.body, FontWeight.Medium, 15f),
    M13(WizardFonts.body, FontWeight.Medium, 13f),
    M12(WizardFonts.body, FontWeight.Medium, 12f),
    M115(WizardFonts.body, FontWeight.Medium, 11.5f),
    M11(WizardFonts.body, FontWeight.Medium, 11f),
    M105(WizardFonts.body, FontWeight.Medium, 10.5f),
    M10(WizardFonts.body, FontWeight.Medium, 10f),
    Cap9(WizardFonts.body, FontWeight.SemiBold, 9f, 1.2f),
    ;

    val style: TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = tracking.sp,
    )
}

private fun parsePath(d: String): Path = PathParser().parsePathString(d).toPath()

/** Iconos sobre una cuadrícula de 24 (los de trazo se dibujan con [EntrenoPen.icon], los macizos con [EntrenoPen.iconFill]). */
internal class EntrenoIcons {
    val plus = parsePath("M12 5v14M5 12h14")
    val check = parsePath("M5.5 12.5l4.2 4.2L18.5 7.6")
    val chevR = parsePath("M9.5 5.5L16 12l-6.5 6.5")
    val search = parsePath("M10.5 4.2a6.3 6.3 0 1 0 0 12.6 6.3 6.3 0 0 0 0-12.6zM15.2 15.2L20 20")
    val barbell = parsePath("M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11")
    val play = parsePath("M8.2 5.4v13.2l10.6-6.6z")
    val ffwd = parsePath("M4 6l8 6-8 6zM12 6l8 6-8 6z")
    val skipTri = parsePath("M5.5 5.6l9.5 6.4-9.5 6.4z")
    val skipBar = parsePath("M18.2 5.8v12.4")
    val star = parsePath("M12 3.2l2.6 5.4 5.9.8-4.3 4.1 1.1 5.9L12 16.6l-5.3 2.8 1.1-5.9-4.3-4.1 5.9-.8z")
    val sparkle = parsePath("M12 3l1.9 6.1L20 11l-6.1 1.9L12 19l-1.9-6.1L4 11l6.1-1.9z")
    val home = parsePath("M4 11.2L12 4l8 7.2V19a1.5 1.5 0 0 1-1.5 1.5H15v-5.2H9v5.2H5.5A1.5 1.5 0 0 1 4 19z")
    val apple = parsePath(
        "M12 8.1C10.5 6.8 7.7 6.7 6.1 8.6 3.8 11.2 5 17.5 7.7 20 9 21.2 10.4 20.2 12 20.2 13.6 20.2 15 21.2 16.3 20 " +
            "19 17.5 20.2 11.2 17.9 8.6 16.3 6.7 13.5 6.8 12 8.1Z",
    )
    val appleStem = parsePath("M12 7.5L13.3 4.2")
    val appleLeaf = parsePath("M14.2 5.2C15.5 3.4 18.1 3.2 19.5 4.1 18.5 5.9 16.1 6.9 14.2 5.2Z")
    val arm = parsePath(
        "M12.409 13.017A5 5 0 0 1 22 15c0 3.866-4 7-9 7-4.077 0-8.153-.82-10.371-2.462-.426-.316-.631-.832-.62-1.362" +
            "C2.118 12.723 2.627 2 10 2a3 3 0 0 1 3 3 2 2 0 0 1-2 2c-1.105 0-1.64-.444-2-1 Z",
    )
    val armLine1 = parsePath("M15 14a5 5 0 0 0-7.584 2")
    val armLine2 = parsePath("M9.964 6.825C8.019 7.977 9.5 13 8 15")
}

internal object EAlign {
    const val START = 0
    const val CENTER = 1
    const val END = 2
}

/**
 * Lo que vive de un cuadro al siguiente: el medidor de texto, la caché de textos ya medidos, los iconos y un
 * trazo de trabajo. [density] es la densidad con la que se miden los textos (sin la escala de fuente del
 * sistema: la escena es una ilustración con tamaño fijo).
 */
internal class EntrenoKit(resolver: FontFamily.Resolver, val density: Float) {
    private val measurer = TextMeasurer(resolver, Density(density, 1f), LayoutDirection.Ltr, 8)
    private val caches = Array(EFont.values().size) { HashMap<String, TextLayoutResult>(24) }
    private val charStrings = HashMap<Char, String>()

    val icons = EntrenoIcons()
    val scratch = Path()
    val dash: PathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)

    fun layout(s: String, st: EFont): TextLayoutResult =
        caches[st.ordinal].getOrPut(s) {
            measurer.measure(text = s, style = st.style, overflow = TextOverflow.Visible, softWrap = false, maxLines = 1)
        }

    fun charString(c: Char): String = charStrings.getOrPut(c) { c.toString() }

    /** Mide de antemano los textos fijos y los dígitos para que la primera vuelta no tenga tirones. */
    fun warm() {
        val digits = "0123456789,:"
        for (st in listOf(EFont.Num17, EFont.Big24, EFont.B13, EFont.B115, EFont.B15, EFont.B105)) for (ch in digits) layout(charString(ch), st)
        for (n in 1..EntrenoNombre.length) layout(EntrenoNombre.substring(0, n), EFont.Name)
        val statics = listOf(
            "Entreno" to EFont.Hero, "Semana 3 · jueves" to EFont.M12, "Nueva sesión" to EFont.Title15, "Elige ejercicios y empieza" to EFont.M11,
            "TUS SESIONES" to EFont.Cap9, "Inicio" to EFont.M10, "Nutrición" to EFont.M10, "Cuerpo" to EFont.M10,
            "Press banca" to EFont.Title17, "Remo con barra" to EFont.B14, "Pecho y espalda" to EFont.Title15,
            "Añadir ejercicio" to EFont.B13, "Empezar sesión" to EFont.B15, "Saltar" to EFont.B12, "¡Récord!" to EFont.B10,
        )
        for ((s, st) in statics) layout(s, st)
        for (item in EntrenoGeo.catalog) {
            layout(item.name, EFont.B13)
            layout(item.detail, EFont.M105)
        }
    }
}

/**
 * Pincel de la escena: dibuja en unidades del lienzo lógico sobre el [DrawScope] ya escalado. Lleva una opacidad
 * de grupo ([ga]) que multiplica a la de cada primitiva, para desvanecer elementos compuestos sin capas.
 */
internal class EntrenoPen(val ds: DrawScope, val kit: EntrenoKit) {
    private val d = kit.density

    /** Opacidad de grupo vigente. */
    var ga = 1f

    private fun c(color: Color, alpha: Float): Color {
        val a = color.alpha * alpha * ga
        return color.copy(alpha = if (a < 0f) 0f else if (a > 1f) 1f else a)
    }

    private fun hidden(alpha: Float, color: Color = Color.White): Boolean = alpha * ga * color.alpha <= 0.004f

    inline fun group(a: Float, block: () -> Unit) {
        val old = ga
        ga = old * (if (a < 0f) 0f else if (a > 1f) 1f else a)
        if (ga > 0.003f) block()
        ga = old
    }

    // ---------------------------------------------------------------- formas
    fun fill(r: EntrenoRect, rad: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        ds.drawRoundRect(c(color, alpha), Offset(r.l, r.t), Size(r.w, r.h), CornerRadius(rad, rad))
    }

    fun fillBrush(r: EntrenoRect, rad: Float, brush: Brush, alpha: Float = 1f) {
        if (hidden(alpha)) return
        ds.drawRoundRect(brush, Offset(r.l, r.t), Size(r.w, r.h), CornerRadius(rad, rad), alpha = alpha * ga)
    }

    /** Filete interior de ancho [w] (queda dentro del rectángulo). */
    fun border(r: EntrenoRect, rad: Float, w: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        val h = w / 2f
        val rr = if (rad - h < 0f) 0f else rad - h
        ds.drawRoundRect(c(color, alpha), Offset(r.l + h, r.t + h), Size(r.w - w, r.h - w), CornerRadius(rr, rr), style = Stroke(w))
    }

    fun dashed(r: EntrenoRect, rad: Float, w: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        val h = w / 2f
        val rr = if (rad - h < 0f) 0f else rad - h
        ds.drawRoundRect(
            c(color, alpha), Offset(r.l + h, r.t + h), Size(r.w - w, r.h - w), CornerRadius(rr, rr),
            style = Stroke(width = w, pathEffect = kit.dash),
        )
    }

    fun circle(cx: Float, cy: Float, rad: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color) || rad <= 0f) return
        ds.drawCircle(c(color, alpha), rad, Offset(cx, cy))
    }

    fun ring(cx: Float, cy: Float, rad: Float, w: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color) || rad <= 0f || w <= 0f) return
        ds.drawCircle(c(color, alpha), rad, Offset(cx, cy), style = Stroke(w))
    }

    fun arc(cx: Float, cy: Float, rad: Float, w: Float, start: Float, sweep: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color) || sweep <= 0.01f) return
        ds.drawArc(
            c(color, alpha), start, sweep, false, Offset(cx - rad, cy - rad), Size(rad * 2f, rad * 2f),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        ds.drawLine(c(color, alpha), Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
    }

    fun icon(p: Path, cx: Float, cy: Float, size: Float, color: Color, sw: Float = 2f, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        val k = size / 24f
        ds.withTransform({ translate(cx - size / 2f, cy - size / 2f); scale(k, k, Offset.Zero) }) {
            drawPath(p, c(color, alpha), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    fun iconFill(p: Path, cx: Float, cy: Float, size: Float, color: Color, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        val k = size / 24f
        ds.withTransform({ translate(cx - size / 2f, cy - size / 2f); scale(k, k, Offset.Zero) }) {
            drawPath(p, c(color, alpha))
        }
    }

    /** Relleno macizo de un icono con un trazo redondeado del mismo color (suaviza las esquinas de triángulos y casas). */
    fun iconSolid(p: Path, cx: Float, cy: Float, size: Float, color: Color, round: Float = 1.6f, alpha: Float = 1f) {
        if (hidden(alpha, color)) return
        val k = size / 24f
        ds.withTransform({ translate(cx - size / 2f, cy - size / 2f); scale(k, k, Offset.Zero) }) {
            val col = c(color, alpha)
            drawPath(p, col)
            drawPath(p, col, style = Stroke(round, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    fun fillScreen(color: Color, alpha: Float = 1f, top: Float = 0f) {
        if (hidden(alpha, color)) return
        ds.drawRect(c(color, alpha), Offset(0f, top), Size(EntrenoGeo.W, EntrenoGeo.H - top))
    }

    /** Hoja con las esquinas de arriba redondeadas, sombra hacia arriba y un filete de luz. */
    fun sheet(y: Float, rad: Float, color: Color) {
        if (y >= EntrenoGeo.H) return
        val sh = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f * ga)),
            startY = y - 34f,
            endY = y + 2f,
        )
        ds.drawRect(sh, Offset(0f, y - 34f), Size(EntrenoGeo.W, 36f))
        val p = kit.scratch
        p.reset()
        p.addRoundRect(
            RoundRect(
                0f, y, EntrenoGeo.W, EntrenoGeo.H + 90f,
                topLeftCornerRadius = CornerRadius(rad, rad),
                topRightCornerRadius = CornerRadius(rad, rad),
            ),
        )
        ds.drawPath(p, c(color, 1f))
        ds.drawPath(p, c(Color.White, 0.08f), style = Stroke(1f))
    }

    /** Sombra suave bajo una tarjeta: capas translúcidas que se abren hacia abajo. */
    fun shadow(r: EntrenoRect, rad: Float, depth: Float, strength: Float = 1f) {
        if (hidden(strength)) return
        for (i in 1..4) {
            val k = i / 4f
            val e = depth * k
            val rr = EntrenoRect(r.l - e * 0.7f, r.t + depth * 0.35f - e * 0.2f, r.r + e * 0.7f, r.b + e + depth * 0.35f)
            fill(rr, rad + e * 0.7f, Color.Black, strength * 0.16f * (1.1f - k))
        }
    }

    // ---------------------------------------------------------------- transformaciones
    fun shifted(dx: Float, dy: Float, block: () -> Unit) {
        ds.withTransform({ translate(dx, dy) }) { block() }
    }

    fun scaled(s: Float, px: Float, py: Float, block: () -> Unit) {
        if (s <= 0.001f) return
        ds.withTransform({ scale(s, s, Offset(px, py)) }) { block() }
    }

    fun rotated(deg: Float, px: Float, py: Float, block: () -> Unit) {
        ds.withTransform({ rotate(deg, Offset(px, py)) }) { block() }
    }

    fun clipRect(r: EntrenoRect, block: () -> Unit) {
        ds.withTransform({ clipRect(r.l, r.t, r.r, r.b) }) { block() }
    }

    fun clipRound(r: EntrenoRect, rad: Float, block: () -> Unit) {
        val p = kit.scratch
        p.reset()
        p.addRoundRect(RoundRect(r.l, r.t, r.r, r.b, CornerRadius(rad, rad)))
        ds.withTransform({ clipPath(p) }) { block() }
    }

    // ---------------------------------------------------------------- texto
    fun width(s: String, st: EFont): Float = if (s.isEmpty()) 0f else kit.layout(s, st).size.width / d

    /** Texto con la línea base en [base]; [align] decide si [x] es el inicio, el centro o el final. */
    fun text(s: String, st: EFont, x: Float, base: Float, color: Color, align: Int = EAlign.START, alpha: Float = 1f) {
        if (s.isEmpty() || hidden(alpha, color)) return
        val l = kit.layout(s, st)
        val w = l.size.width / d
        val left = when (align) {
            EAlign.CENTER -> x - w / 2f
            EAlign.END -> x - w
            else -> x
        }
        val top = base - l.firstBaseline / d
        val col = c(color, alpha)
        ds.withTransform({ translate(left, top); scale(1f / d, 1f / d, Offset.Zero) }) {
            drawText(l, col)
        }
    }

    /**
     * Número que rueda dígito a dígito (odómetro). Con [tabular] cada dígito ocupa el ancho del «0» (relojes y
     * contadores que no deben bailar). Alineado por la derecha cuando [align] es [EAlign.END].
     */
    fun roll(r: EntrenoRoll, st: EFont, x: Float, base: Float, color: Color, align: Int = EAlign.START, alpha: Float = 1f, tabular: Boolean = false) {
        if (r.done && !tabular) {
            text(r.to, st, x, base, color, align, alpha)
            return
        }
        val settled = r.done
        val n = max(r.from.length, r.to.length)
        if (n == 0) return
        val a = r.from.padStart(n)
        val b = r.to.padStart(n)
        val zero = if (tabular) width("0", st) else 0f
        val cw = FloatArray(n)
        var total = 0f
        for (i in 0 until n) {
            val ca = a[i]
            val cb = b[i]
            val w = if (tabular && (ca.isDigit() || cb.isDigit())) zero else max(charWidth(ca, st), charWidth(cb, st))
            cw[i] = w
            total += w
        }
        var cx = when (align) {
            EAlign.CENTER -> x - total / 2f
            EAlign.END -> x - total
            else -> x
        }
        val fs = st.size
        val p = if (settled) 1f else r.p
        for (i in 0 until n) {
            val ca = a[i]
            val cb = b[i]
            val k = n - 1 - i
            val pi = eOutCubic(seg(p, 0.09f * k, 0.09f * k + 0.7f))
            val mid = cx + cw[i] / 2f
            if (ca == cb || settled) {
                if (cb != ' ') text(kit.charString(cb), st, mid, base, color, EAlign.CENTER, alpha)
            } else {
                clipRect(EntrenoRect(cx - 1f, base - fs * 1.0f, cx + cw[i] + 1f, base + fs * 0.3f)) {
                    if (ca != ' ') text(kit.charString(ca), st, mid, base - pi * fs * 0.8f, color, EAlign.CENTER, alpha * (1f - pi))
                    if (cb != ' ') text(kit.charString(cb), st, mid, base + (1f - pi) * fs * 0.8f, color, EAlign.CENTER, alpha * pi)
                }
            }
            cx += cw[i]
        }
    }

    private fun charWidth(ch: Char, st: EFont): Float = if (ch == ' ') 0f else width(kit.charString(ch), st)
}
