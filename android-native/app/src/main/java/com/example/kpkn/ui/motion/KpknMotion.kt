package com.example.kpkn.ui.motion

/*
 * KPKN · Animaciones del logo (Jetpack Compose). Port 1:1 de kpkn-motion.html.
 *
 * Presentación:  APILAR, RESPIRAR, PULSO, CARGA, DEFORMAR
 * Cargando:      APILANDO, OLA, GIRANDO            (bucle infinito)
 * Momentos:      META_CUMPLIDA, NUEVO_RECORD, RACHA, RECUPERADO, SIN_CONEXION
 *
 * Uso:
 *   // Splash / intro, una vez:
 *   KpknMotion(KpknAnim.APILAR, loop = false, onFinished = { navegarAlHome() })
 *   // Cargando:
 *   KpknMotion(KpknAnim.OLA, Modifier.size(220.dp), background = Color.Transparent)
 *   // Momento con dato real:
 *   KpknMotion(KpknAnim.RACHA, value = diasDeRacha, loop = false, onFinished = { cerrar() })
 *
 * Requisitos: Compose UI 1.5+. Usa la FontFamily `Syne` de ui/theme/KpknType.kt.
 */

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.theme.Syne
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class KpknAnim(val dur: Float, val loop: Boolean = false) {
    // Presentación
    APILAR(3.6f), RESPIRAR(4.2f), PULSO(3.8f), CARGA(4.0f), DEFORMAR(4.2f),
    // Cargando (bucles sin corte)
    APILANDO(2.4f, true), OLA(1.6f, true), GIRANDO(1.2f, true),
    // Momentos
    META_CUMPLIDA(3.2f), NUEVO_RECORD(3.4f), RACHA(3.6f), RECUPERADO(4.0f), SIN_CONEXION(3.4f),
}

private const val HOLD = 1.8f
private const val FADE = .45f

/**
 * @param loop      true = se repite (con fundido entre ciclos); false = se reproduce una vez y queda en el cuadro final.
 *                  Los bucles de carga ignoran este valor y siempre se repiten.
 * @param value     RACHA: días (por defecto 7) · RECUPERADO: porcentaje final (por defecto 100).
 * @param text      reemplaza el texto (por ejemplo "LOADING" o "GOAL!"); null = texto por defecto.
 * @param onFinished se llama una vez al terminar (solo si no hace bucle).
 */
@Composable
fun KpknMotion(
    anim: KpknAnim,
    modifier: Modifier = Modifier.fillMaxSize(),
    background: Color = Color.Black,
    loop: Boolean = true,
    value: Int? = null,
    text: String? = null,
    onFinished: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val finished by rememberUpdatedState(onFinished)
    var t by remember(anim) { mutableFloatStateOf(if (reduceMotion && !anim.loop) anim.dur else 0f) }
    val repeat = loop || anim.loop

    LaunchedEffect(anim, repeat, reduceMotion) {
        if (reduceMotion) { if (!anim.loop) finished?.invoke(); return@LaunchedEffect }
        var start = withFrameNanos { it }
        var called = false
        while (isActive) {
            withFrameNanos { now ->
                val e = (now - start) / 1_000_000_000f
                if (anim.loop) { t = e; return@withFrameNanos }
                val total = anim.dur + HOLD + FADE
                if (repeat) {
                    if (e >= total) { start = now; t = 0f } else t = e
                } else {
                    t = min(e, anim.dur)
                    if (!called && e >= anim.dur + HOLD) { called = true; finished?.invoke() }
                }
            }
        }
    }

    val fr = frame(anim, if (anim.loop) t else min(t, anim.dur), value)
    val fade = if (!anim.loop && repeat && t > anim.dur + HOLD) ((t - anim.dur - HOLD) / FADE).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(modifier.background(background), contentAlignment = Alignment.Center) {
        val w = maxWidth; val h = maxHeight
        val density = LocalDensity.current
        val fontPx = with(density) { min(h.toPx() * .11f, w.toPx() * .13f) } * fr.size
        val maxWordPx = with(density) { w.toPx() } * .86f
        var naturalW by remember(anim, text) { mutableFloatStateOf(0f) }
        val fit = if (naturalW > maxWordPx && naturalW > 0f) maxWordPx / naturalW else 1f

        Column(
            Modifier.graphicsLayer { alpha = 1f - fade },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val logoW = minOf(w * .46f, h * .52f)
            Canvas(Modifier.width(logoW).aspectRatio(100f / 66f)) {
                val k = size.width / 100f
                withTransform({ scale(k, k, Offset.Zero); translate(0f, -14f) }) { drawFrame(fr) }
            }
            Spacer(Modifier.height(h * .024f))
            val fontSp = with(density) { fontPx.toSp() }
            val gapDp = with(density) { (fr.ls * fontPx).coerceAtLeast(0f).toDp() }
            Row(
                Modifier
                    .onSizeChanged { naturalW = it.width.toFloat() }
                    .graphicsLayer { scaleX = fit; scaleY = fit }
                    .drawWithContent {
                        val c = fr.clip
                        if (c == null) drawContent() else clipRect(right = size.width * c) { this@drawWithContent.drawContent() }
                    },
                horizontalArrangement = Arrangement.spacedBy(gapDp),
            ) {
                val word = text ?: fr.text
                word.forEachIndexed { i, ch ->
                    val l = fr.letters.getOrNull(i) ?: Letter()
                    Text(
                        (l.ch ?: ch).toString().let { if (it == " ") " " else it },
                        style = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = fontSp, color = fr.wordCol ?: fr.col),
                        modifier = Modifier.graphicsLayer {
                            alpha = l.o.coerceIn(0f, 1f)
                            translationY = l.y * fontPx
                            scaleX = l.sc; scaleY = l.sc
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- estado de un cuadro
private data class DiscS(
    val s: Float = 1f, val q: Float = 0f, val x: Float = 0f, val y: Float = 0f,
    val f: Float = 1f, val ix: Float = 0f, val a: Float = 1f, val col: Color? = null,
)
private data class Fx(val rx: Float, val ry: Float, val cy: Float, val o: Float, val w: Float)
private data class Letter(val y: Float = 0f, val o: Float = 1f, val sc: Float = 1f, val ch: Char? = null)
private class Frame(
    val discs: List<DiscS?>, val col: Color, val text: String = "KPKN", val letters: List<Letter> = emptyList(),
    val ls: Float = -.01f, val size: Float = 1f, val clip: Float? = null, val wordCol: Color? = null, val fx: List<Fx> = emptyList(),
)

// ---------------------------------------------------------------- colores y utilidades
private val WHITE = Color(0xFFF2EEE6); private val WARM = Color(0xFFFFD6BA); private val COOL = Color(0xFFCDDCFF)
private val PURE = Color(0xFFFFFFFF); private val GOLD = Color(0xFFF5C86E); private val DIM = Color(0xFF464A54)
private val T_COL = Color(0xFF8FB2FF); private val T_MUS = Color(0xFFF49A6E); private val T_ENE = Color(0xFFF7CF73)
private const val TAU = 6.2831855f
private const val PIf = 3.1415927f
private const val GLYPHS = "KPNXA0∆≡"

private fun c01(v: Float) = v.coerceIn(0f, 1f)
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun seg(t: Float, a: Float, b: Float) = c01((t - a) / (b - a))
private fun eOutCubic(t: Float) = 1f - (1f - t).pow(3)
private fun eInOut(t: Float) = if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun eOutBack(t: Float): Float { val c1 = 1.9f; val c3 = c1 + 1f; return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2) }
private fun eInQuad(t: Float) = t * t
private fun spring(dt: Float, k: Float = 7f, w: Float = 22f) = if (dt < 0f) 0f else exp(-k * dt) * cos(w * dt)
private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, c01(t))
private fun per(text: String, fn: (Int) -> Letter) = text.indices.map(fn)

// ---------------------------------------------------------------- geometría de la Torre (4º disco solo para récord)
private class Geo(val cy: Float, val rx: Float, val ry: Float, val off: Float, val dx: Float, val dy: Float)
private val BASE = listOf(
    Geo(61f, 38f, 12f, 2f, 5f, 3.5f), Geo(46f, 28f, 9f, 1.5f, 4f, 2.5f),
    Geo(33f, 18f, 6.5f, 1f, 3.5f, 2.2f), Geo(21.6f, 11f, 4.2f, .6f, 2.6f, 1.5f),
)
private val FLAT_CY = floatArrayOf(54f, 60f, 66f, 70f) // los Anillos vistos de frente

private fun discPath(i: Int, p: DiscS): Path {
    val b = BASE[i]
    val cx = 50f + p.x
    val cy = lerpF(FLAT_CY[i], b.cy, p.f) + p.y
    val rx = b.rx * p.s * (1f + p.q)
    val ry = lerpF(b.rx * p.s, b.ry * p.s, p.f) * (1f - p.q)
    val irx = max(.1f, rx - b.dx * p.s)
    val iry = max(.1f, lerpF(irx, ry * (b.ry - b.dy) / b.ry, p.f))
    val icy = cy - b.off * p.s * p.f
    val icx = cx + p.ix * b.dx * p.s * .8f
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
        addOval(Rect(icx - irx, icy - iry, icx + irx, icy + iry))
    }
}

private fun DrawScope.drawFrame(fr: Frame) {
    fr.fx.forEach { e ->
        if (e.o > 0f) drawOval(fr.col, Offset(50f - e.rx, e.cy - e.ry), Size(e.rx * 2, e.ry * 2), alpha = c01(e.o), style = Stroke(e.w))
    }
    fr.discs.forEachIndexed { i, d ->
        if (d != null && d.a > 0f) drawPath(discPath(i, d), d.col ?: fr.col, alpha = c01(d.a))
    }
}

// ---------------------------------------------------------------- las 13 animaciones
private fun frame(a: KpknAnim, t: Float, value: Int?): Frame = when (a) {
    KpknAnim.APILAR -> {
        val discs = (0..2).map { i ->
            val t0 = .2f + .42f * i; val land = t0 + .42f
            var q = .22f * spring(t - land, 6f, 20f)
            for (j in i + 1..2) q += .07f * spring(t - (.62f + .42f * j), 7f, 22f)
            DiscS(y = (1f - eInQuad(seg(t, t0, land))) * -90f, q = q, a = if (t < t0) 0f else 1f)
        }
        val flash = max(0f, (0..2).maxOf { i -> spring(t - (.62f + .42f * i), 5f, 0f) })
        Frame(discs, mix(WHITE, WARM, flash * .55f),
            letters = per("KPKN") { i -> val k = eOutCubic(seg(t, 1.75f + .08f * i, 2.3f + .08f * i)); Letter(y = (1f - k) * .7f, o = k) })
    }
    KpknAnim.RESPIRAR -> {
        val f = eInOut(seg(t, .9f, 2.3f))
        val discs = (0..2).map { i ->
            val k = eOutCubic(seg(t, .05f + .12f * i, .75f + .12f * i))
            DiscS(f = f, s = lerpF(.7f, 1f, k) * (1f + .025f * sin(t * TAU / 1.8f + i * .7f)), a = k)
        }
        val k = eOutCubic(seg(t, 2.1f, 3.3f))
        Frame(discs, mix(COOL, WHITE, eInOut(seg(t, .4f, 3f))), letters = per("KPKN") { Letter(o = k) }, ls = lerpF(.7f, -.01f, k))
    }
    KpknAnim.PULSO -> {
        val beats = floatArrayOf(1.35f, 1.95f)
        val discs = (0..2).map { i ->
            var s = max(0f, eOutBack(seg(t, .15f + .22f * i, .65f + .22f * i)))
            for (b in beats) { val d = t - b - .07f * i; if (d > 0f) s += .12f * exp(-6f * d) * sin(min(d * 14f, PIf)) }
            DiscS(s = s, a = if (t < .15f + .22f * i) 0f else 1f)
        }
        var glow = 0f; for (b in beats) { val d = t - b; if (d > 0f) glow = max(glow, exp(-4f * d)) }
        Frame(discs, mix(WHITE, WARM, glow * .7f),
            letters = per("KPKN") { i -> val k = seg(t, 1.35f + .17f * i, 1.6f + .17f * i); Letter(o = k, sc = lerpF(1.5f, 1f, eOutCubic(k))) })
    }
    KpknAnim.CARGA -> {
        val z = eInOut(seg(t, 0f, 1.1f)); val k1 = seg(t, 1f, 1.65f); val k2 = seg(t, 1.25f, 1.9f)
        Frame(listOf(
            DiscS(s = lerpF(3.4f, 1f, z), y = lerpF(-14f, 0f, z)),
            DiscS(s = lerpF(.55f, 1f, eOutBack(k1)), y = lerpF(15f, 0f, eOutBack(k1)), a = if (k1 > 0f) 1f else 0f),
            DiscS(s = lerpF(.45f, 1f, eOutBack(k2)), y = lerpF(28f, 0f, eOutBack(k2)), a = if (k2 > 0f) 1f else 0f),
        ), mix(PURE, WHITE, z), clip = eInOut(seg(t, 1.95f, 2.75f)))
    }
    KpknAnim.DEFORMAR -> {
        val amp = 1f - eInOut(seg(t, .4f, 2.5f)); val settle = spring(t - 2.5f, 6f, 18f)
        val discs = (0..2).map { i ->
            DiscS(q = amp * .32f * sin(t * 7.5f + i * 1.3f) + .06f * settle, y = amp * 4f * sin(t * 5f + i * 2.1f),
                x = amp * 3f * cos(t * 4.2f + i), f = lerpF(1f, .55f + .45f * sin(t * 3f + i), amp), a = eOutCubic(seg(t, 0f, .4f)))
        }
        val h = sin(t * 2.4f)
        Frame(discs, if (h > 0f) mix(WHITE, WARM, h * amp * .8f) else mix(WHITE, COOL, -h * amp * .8f),
            letters = per("KPKN") { i ->
                Letter(o = seg(t, 1.5f, 1.9f), ch = if (t < 2.4f + .18f * i) GLYPHS[floor(t * 18f + i * 3).toInt() % GLYPHS.length] else null)
            })
    }
    KpknAnim.APILANDO -> {
        val p = t % 2.4f
        val discs = (0..2).map { i ->
            val tin = .1f + .28f * i; val tout = 1.55f + .16f * (2 - i)
            val kin = eOutBack(seg(p, tin, tin + .35f)); val kout = eInOut(seg(p, tout, tout + .35f))
            DiscS(y = (1f - kin) * -14f - kout * 10f, a = c01(seg(p, tin, tin + .15f) - kout), q = .12f * spring(p - tin - .35f, 8f, 24f))
        }
        Frame(discs, WHITE, "CARGANDO", per("CARGANDO") { i -> Letter(o = .45f + .55f * max(0f, sin(p / 2.4f * TAU * 2f - i * .5f))) }, ls = .35f, size = .42f)
    }
    KpknAnim.OLA -> {
        val p = t % 1.6f
        val discs = (0..2).map { i -> val w = max(0f, sin(p / 1.6f * TAU - i * .9f)); DiscS(s = 1f + .05f * w, a = .3f + .7f * w, y = -2f * w) }
        Frame(discs, WHITE, "CARGANDO", per("CARGANDO") { Letter(o = .7f) }, ls = .35f, size = .42f)
    }
    KpknAnim.GIRANDO -> {
        val p = t % 1.2f
        val discs = (0..2).map { i -> DiscS(ix = sin(p / 1.2f * TAU + i * 1.1f), q = .03f * cos(p / 1.2f * TAU + i * 1.1f)) }
        Frame(discs, WHITE, "SINCRONIZANDO", per("SINCRONIZANDO") { Letter(o = .75f) }, ls = .3f, size = .36f)
    }
    KpknAnim.META_CUMPLIDA -> {
        val crouch = eInOut(seg(t, 0f, .3f)) * (1f - seg(t, .3f, .45f))
        val up = eOutCubic(seg(t, .35f, .85f)); val down = eInQuad(seg(t, .85f, 1.25f)); val land = spring(t - 1.25f, 6f, 22f)
        val discs = (0..2).map { i -> DiscS(y = -(up - down) * (16f + 2f * i), q = .14f * crouch + .2f * land * (1f - i * .25f)) }
        val fx = (0..2).map { k ->
            val d = seg(t, 1.25f + .13f * k, 2.15f + .13f * k)
            Fx(lerpF(38f, 78f, eOutCubic(d)), lerpF(12f, 26f, eOutCubic(d)), 61f, if (d > 0f && d < 1f) 1f - d else 0f, lerpF(2.2f, .6f, d))
        }
        val glow = max(0f, spring(t - 1.25f, 2.5f, 0f))
        Frame(discs, mix(WHITE, GOLD, glow * .9f), "META CUMPLIDA",
            per("META CUMPLIDA") { i -> val k = eOutBack(seg(t, 1.45f + .035f * i, 1.85f + .035f * i)); Letter(y = (1f - k) * .6f, o = c01(k)) },
            size = .5f, fx = fx)
    }
    KpknAnim.NUEVO_RECORD -> {
        val make = eInOut(seg(t, .1f, .5f)); val land = 1f; val impact = spring(t - land, 6f, 22f)
        val discs = (0..2).map { i -> DiscS(y = make * 5f, q = .1f * impact * (1f - i * .2f)) } +
            DiscS(y = 5f + (1f - eInQuad(seg(t, .55f, land))) * -70f, a = if (t < .55f) 0f else 1f, q = .25f * impact,
                col = mix(PURE, GOLD, seg(t, land, land + .4f)))
        Frame(discs, WHITE, "NUEVO RÉCORD",
            per("NUEVO RÉCORD") { i -> val k = eOutCubic(seg(t, 1.35f + .04f * i, 1.85f + .04f * i)); Letter(y = (1f - k) * .6f, o = k) },
            size = .5f, wordCol = mix(WHITE, GOLD, seg(t, 1.6f, 2.4f)))
    }
    KpknAnim.RACHA -> {
        val target = (value ?: 7).coerceAtLeast(1)
        val cols = listOf(T_COL, T_MUS, T_ENE)
        val discs = (0..2).map { i -> val k = seg(t, .4f + .5f * i, .8f + .5f * i); DiscS(col = mix(DIM, cols[i], k), s = 1f + .08f * sin(PIf * k)) }
        val n = (1 + floor(seg(t, .3f, 2f) * (target - 1 + .999f)).toInt()).coerceIn(1, target)
        val pop = spring(t - 2f, 5f, 14f)
        val txt = "$n " + if (n == 1) "DÍA" else "DÍAS"
        Frame(discs, WHITE, txt, per(txt) { Letter(sc = 1f + .12f * max(0f, pop)) }, size = .62f)
    }
    KpknAnim.RECUPERADO -> {
        val target = (value ?: 100).coerceIn(0, 100)
        val discs = (0..2).map { i ->
            val k = eInOut(seg(t, .2f + .7f * i, 1.2f + .7f * i))
            DiscS(col = mix(DIM, mix(COOL, WHITE, k), k), s = 1f + .02f * sin(t * TAU / 2.2f + i))
        }
        val pct = (eInOut(seg(t, .2f, 2.6f)) * target).roundToInt()
        val txt = "$pct% RECUPERADO"
        Frame(discs, WHITE, txt, per(txt) { Letter(o = .4f + .6f * seg(t, 0f, .6f)) }, size = .46f, wordCol = mix(COOL, WHITE, seg(t, 2.4f, 3f)))
    }
    KpknAnim.SIN_CONEXION -> {
        val wob = sin(t * 9f) * exp(-1.2f * max(0f, t - .3f)) * seg(t, .1f, .3f)
        val slide = sin(PIf * seg(t, .5f, 1.6f)); val back = spring(t - 1.6f, 5f, 18f)
        val discs = (0..2).map { i ->
            DiscS(x = wob * (1f + i * 1.6f) + if (i == 2) slide * 14f + back * 2f else 0f,
                y = if (i == 2) slide * 3f else 0f, q = if (i == 2) .1f * back else 0f)
        }
        Frame(discs, WHITE, "SIN CONEXIÓN", per("SIN CONEXIÓN") { Letter(o = seg(t, .4f, .9f)) },
            ls = .12f, size = .46f, wordCol = Color(0xFFC8C4BE))
    }
}

// ---------------------------------------------------------------- pantalla de prueba
/** Galería para probar todas. Bórrala cuando esté integrado. */
@Composable
fun KpknMotionGallery() {
    var i by remember { mutableIntStateOf(0) }
    val all = KpknAnim.values()
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        KpknMotion(all[i], Modifier.weight(1f).fillMaxSize())
        Row(
            Modifier.weight(.12f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(onClick = { i = (i - 1 + all.size) % all.size }) { Text("← Anterior", color = WHITE) }
            Text(all[i].name, color = WHITE, fontFamily = Syne)
            androidx.compose.material3.TextButton(onClick = { i = (i + 1) % all.size }) { Text("Siguiente →", color = WHITE) }
        }
    }
}
