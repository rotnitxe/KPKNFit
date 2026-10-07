package com.example.kpkn.screens.onboarding.welcome

import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.eInOut
import com.example.kpkn.screens.onboarding.design.eOutBack
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Escena de Nutrición · la lógica de QUÉ SE VE EN CADA INSTANTE.
 *
 * Todo aquí es una función pura de `t` (segundos desde el inicio del bucle): sin corrutinas ni estado. El
 * composable (WelcomeNutricionScene) solo dibuja el [NutricionFrame] que sale de [nutricionFrameAt], y las
 * pruebas (WelcomeNutricionSceneTest) comprueban esta lógica sin tocar la pantalla.
 *
 * Guion (≈ 14 s, ver [NutricionBeats]):
 *   1 · 0,3–1,6 s   El dedo toca «Registrar comida» y sube la hoja con un muelle suave.
 *   2 · 1,7–5,2 s   «Descríbela con tus palabras»: la frase se teclea sola, con ritmo humano.
 *   3 · 5,3–8,0 s   «KPKN lo entiende»: un destello recorre el texto, la frase se convierte en chips y los
 *                   alimentos entran uno a uno con su cantidad, sus kcal y sus macros.
 *   4 · 8,0–9,6 s   «Y tu día se actualiza»: se guarda, la hoja baja y los anillos barren hasta su valor.
 *   5 · 9,6–13,3 s  Respiro con el resultado (los anillos se presentan uno a uno) y fundido de vuelta.
 *
 * Con movimiento reducido se muestra t = 70 % del periodo (≈ 9,8 s): la comida ya entendida, los anillos
 * actualizados y la comida marcada como registrada.
 */

// ─────────────────────────────────────────────────────────────── los datos de la comida de ejemplo

internal enum class NutricionFoodId { HUEVOS, PALTA, PAN, CAFE }

/** Un alimento de la comida de ejemplo: lo que KPKN «entiende» de un fragmento de la frase. */
internal class NutricionFood(
    val id: NutricionFoodId,
    /** Fragmento de la frase escrita del que sale el alimento (tal cual está en [NutricionMeal.Sentence]). */
    val phrase: String,
    val name: String,
    /** Etiqueta del chip: nombre normalizado y cantidad («huevos revueltos» + «×2»). */
    val chipName: String,
    val chipQty: String,
    /** Cantidad estimada, como la ve la persona. */
    val amount: String,
    val kcal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
) {
    /** Energía que sale de los macros (4 · P + 4 · H + 9 · G): la fila es coherente si se parece a [kcal]. */
    val kcalFromMacros: Int get() = 4 * protein + 4 * carbs + 9 * fat
}

internal object NutricionMeal {
    const val Name = "Desayuno"
    const val Sentence = "2 huevos revueltos, media palta y 2 tostadas de pan integral con un café con leche"

    /** Metas del día de ejemplo. */
    const val GoalKcal = 2300
    const val GoalProtein = 150
    const val GoalCarbs = 260
    const val GoalFat = 70

    val foods: List<NutricionFood> = listOf(
        NutricionFood(NutricionFoodId.HUEVOS, "2 huevos revueltos", "Huevos revueltos", "huevos revueltos", "×2", "2 unidades · ≈ 100 g", 182, 13, 1, 14),
        NutricionFood(NutricionFoodId.PALTA, "media palta", "Palta", "palta", "½", "½ unidad · ≈ 70 g", 112, 1, 4, 10),
        NutricionFood(NutricionFoodId.PAN, "2 tostadas de pan integral", "Pan integral", "pan integral", "×2", "2 tostadas · ≈ 60 g", 150, 7, 28, 1),
        NutricionFood(NutricionFoodId.CAFE, "un café con leche", "Café con leche", "café con leche", "", "1 taza · ≈ 200 ml", 60, 6, 7, 1),
    )

    val totalKcal: Int = foods.sumOf { it.kcal }
    val totalProtein: Int = foods.sumOf { it.protein }
    val totalCarbs: Int = foods.sumOf { it.carbs }
    val totalFat: Int = foods.sumOf { it.fat }

    /** Dónde está cada alimento dentro de la frase escrita: [inicio, fin). */
    val spans: List<IntRange> = foods.map { food ->
        val start = Sentence.indexOf(food.phrase)
        start until start + food.phrase.length
    }
}

// ─────────────────────────────────────────────────────────────── el guion: instantes de cada momento

internal object NutricionBeats {
    // Entrada y salida del bucle: la pantalla emerge del fondo y, al final, vuelve a él.
    const val RevealStart = 0.04f
    const val RevealEnd = 0.44f
    const val DipStart = 13.35f
    const val DipEnd = 13.95f

    // 1 · Abre la hoja
    const val Caption1 = 0.50f
    const val Finger1In = 0.30f
    const val Touch1 = 0.80f
    const val Release1 = 0.93f
    const val SheetUp0 = 0.93f
    const val KbUp0 = 1.15f
    const val KbUp1 = 1.60f
    const val CaretOn = 1.55f

    // 2 · Escribe
    const val TypeStart = 1.70f
    const val TypeEnd = 4.95f
    const val EnterPress = 5.18f

    // 3 · Entiende
    const val Caption2 = 5.25f
    const val KbDown0 = 5.25f
    const val KbDown1 = 5.70f
    const val Expand0 = 5.25f
    const val Expand1 = 5.80f
    const val Shimmer0 = 5.35f
    const val Shimmer1 = 6.10f
    const val Chips0 = 5.80f
    const val ChipStagger = 0.12f
    const val ChipFlight = 0.46f
    const val Rows0 = 6.40f
    const val RowStagger = 0.30f
    const val RowRoll = 0.55f
    const val Cta0 = 7.70f
    const val Cta1 = 8.05f

    // 4 · Guarda y el día se actualiza
    const val Finger2In = 7.55f
    const val Touch2 = 8.12f
    const val Release2 = 8.24f
    const val Close0 = 8.24f
    const val Close1 = 8.74f
    const val Caption3 = 8.42f
    const val Rings0 = 8.47f
    const val RingStagger = 0.07f
    const val RingSweep = 0.85f
    const val Num0 = 8.47f
    const val Num1 = 9.47f
    const val Card0 = 8.62f
    const val Card1 = 9.22f
    const val Badge0 = 9.22f
    const val Badge1 = 9.57f

    // 5 · Respiro: los cuatro anillos se presentan, uno a uno
    const val Tour0 = 10.00f
    const val TourStep = 0.55f
}

/** Instante del cuadro fijo con movimiento reducido (el 70 % del periodo). */
internal val WelcomeNutricionStillT: Float get() = WelcomeNutricionPeriod * 0.7f

// ─────────────────────────────────────────────────────────────── la geometría compartida (lienzo 300 × 620)

internal object NutricionLayout {
    const val Width = 300f
    const val Height = 620f
    const val Margin = 20f

    // Pantalla de inicio
    const val RingCx = 150f
    const val RingCy = 204f
    const val RingOuter = 88f
    const val RingStroke = 9f
    const val RingGap = 3f

    // Botón «Registrar comida» (abajo a la derecha, como en la app)
    const val FabW = 158f
    const val FabH = 40f
    const val FabRight = 16f
    const val FabTop = 556f
    const val FabCx = Width - FabRight - FabW / 2f
    const val FabCy = FabTop + FabH / 2f

    // Hoja: posiciones del borde superior
    const val SheetHidden = 640f
    const val SheetTyping = 196f
    const val SheetFull = 116f

    // Hoja: medidas relativas a su borde superior
    const val FieldTop = 58f
    const val FieldH = 80f

    /** Aire sobre la primera fila de chips dentro del campo (los chips ocupan el sitio de la frase). */
    const val ChipsInset = 12f
    const val ChipH = 24f
    const val ChipRowGap = 8f
    const val RowsGap = 12f
    const val RowH = 54f
    const val RowGap = 6f
    const val RowsBlockH = RowH * 4f + RowGap * 3f
    const val TotalGap = 12f
    const val TotalH = 18f
    const val CtaGap = 8f
    const val CtaH = 46f

    /** Centro del botón «Guardar comida» con la hoja abierta del todo. */
    const val CtaCy = SheetFull + FieldTop + FieldH + RowsGap + RowsBlockH + TotalGap + TotalH + CtaGap + CtaH / 2f

    /** Teclado: borde superior con la hoja en modo escritura. */
    const val KeyboardTop = 388f
}

// ─────────────────────────────────────────────────────────────── muelle suave

/**
 * Respuesta de un muelle amortiguado de 0 a 1 sin velocidad inicial: sube y se asienta con un sobreimpulso
 * pequeño (con zeta 0,78 ≈ 2 %). `dt` en segundos desde que arranca. Es el «muelle suave» de la hoja y de los
 * chips; la marca ya usa otros en las celebraciones, más vivos.
 */
internal fun nutricionSpring(dt: Float, zeta: Float = 0.78f, omega: Float = 11f): Float {
    if (dt <= 0f) return 0f
    val damped = omega * sqrt(1f - zeta * zeta)
    val decay = zeta * omega
    return 1f - exp(-decay * dt) * (cos(damped * dt) + decay / damped * sin(damped * dt))
}

/** Posición del borde superior de la hoja: sube (muelle), se amplía al entender, y baja al guardar. */
internal fun nutricionSheetTop(t: Float): Float {
    val b = NutricionBeats
    val l = NutricionLayout
    val rising = lerpF(l.SheetHidden, l.SheetTyping, nutricionSpring(t - b.SheetUp0))
    val expanded = lerpF(rising, l.SheetFull, eInOut(seg(t, b.Expand0, b.Expand1)))
    return if (t >= b.Close0) lerpF(l.SheetFull, l.SheetHidden, eInOut(seg(t, b.Close0, b.Close1))) else expanded
}

// ─────────────────────────────────────────────────────────────── el tecleo

internal object NutricionTyping {
    /** Cuánto dura el brillo de una tecla pulsada, en segundos. */
    const val KeyGlowSecs = 0.16f

    /** Instante (s) en que se teclea cada carácter de la frase: ritmo humano, de [NutricionBeats.TypeStart] a TypeEnd. */
    val times: FloatArray = buildTimes()

    private fun buildTimes(): FloatArray {
        val text = NutricionMeal.Sentence
        val weights = FloatArray(text.length) { weight(text, it) }
        var total = 0f
        for (w in weights) total += w
        val duration = NutricionBeats.TypeEnd - NutricionBeats.TypeStart
        var acc = 0f
        return FloatArray(text.length) { i ->
            acc += weights[i]
            NutricionBeats.TypeStart + duration * (acc / total)
        }
    }

    /** Lo que tarda cada tecla (relativo): la primera, tras una coma y las cifras, más lentas; una pizca de temblor. */
    private fun weight(text: String, i: Int): Float {
        val prev = if (i > 0) text[i - 1] else ' '
        var w = 1f
        if (prev == ' ') w += 0.28f
        if (prev == ',') w += 1.0f
        if (text[i].isDigit()) w += 0.55f
        if (i == 0) w += 2.4f
        val h = ((i * 2654435761L) ushr 9).toInt() and 0xFF
        return w * (1f + (h / 255f - 0.5f) * 0.28f)
    }

    /** Cuántos caracteres de la frase llevan tecleados en [t]. */
    fun countAt(t: Float): Int {
        var n = 0
        while (n < times.size && times[n] <= t) n++
        return n
    }
}

// ─────────────────────────────────────────────────────────────── el cuadro

/** El dedo: dónde está, cuánto se ve, cuánto aprieta y cómo va la onda del toque (0 = sin onda). */
internal class NutricionTouch(val x: Float, val y: Float, val alpha: Float, val press: Float, val ripple: Float)

/** El rótulo del paso: el actual, el anterior (que sale) y cuánto ha entrado el actual. */
internal class NutricionCaption(val step: Int, val previous: Int, val mix: Float)

/** La pantalla de Nutrición: anillos, cifras y la tarjeta de la comida. */
internal class NutricionHomeFrame(
    /** Fracción de cada anillo de 0 a 1: kcal, proteína, hidratos, grasas. */
    val rings: FloatArray,
    val kcal: Int,
    val kcalLeft: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    /** Cuánto se ve el resumen de macros (de tenue a pleno al actualizarse). */
    val summaryAlpha: Float,
    /** De «sin registrar» (0) a comida registrada con sus alimentos (1). */
    val cardGrow: Float,
    /** Entrada de cada alimento en la tarjeta (con rebote). */
    val cardFoods: FloatArray,
    /** Sello de «registrada» (con rebote). */
    val badge: Float,
    /** Presentación de cada anillo en el respiro: 0 → 1 → 0. */
    val tour: FloatArray,
    val fabPress: Float,
)

/** La hoja de registro: qué hay y en qué punto está cada cosa. */
internal class NutricionSheetFrame(
    val top: Float,
    val scrim: Float,
    val keyboard: Float,
    val typed: Int,
    val caret: Float,
    val focus: Float,
    val keyChars: CharArray,
    val keyGlow: FloatArray,
    val enterGlow: Float,
    val underline: FloatArray,
    val ignite: FloatArray,
    val shimmer: Float,
    /** Cuánto se ve la frase escrita: se desvanece mientras sus palabras se convierten en chips. */
    val textAlpha: Float,
    /** Vuelo de cada chip desde su palabra hasta su sitio, de 0 a 1. */
    val chips: FloatArray,
    val rowIn: FloatArray,
    val rowRoll: FloatArray,
    val rowKcal: IntArray,
    /** Tres minibarras por fila (proteína, hidratos, grasas), de 0 a 1 cada una. */
    val rowBars: FloatArray,
    val totalKcal: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFat: Int,
    val ctaIn: Float,
    val ctaPress: Float,
)

internal class NutricionFrame(
    val t: Float,
    /** 0 = pantalla a la vista; 1 = cubierta por el fondo (fundido de entrada y de salida del bucle). */
    val dip: Float,
    val caption: NutricionCaption,
    val touch: NutricionTouch,
    val home: NutricionHomeFrame,
    val sheet: NutricionSheetFrame,
    /** Filas cuya cifra ya llegó a su valor. */
    val filasResueltas: Int,
) {
    /** La parte de la frase que lleva tecleada la persona. */
    val textoTecleado: String get() = NutricionMeal.Sentence.substring(0, sheet.typed)

    /** Cuántos chips ya salieron de la frase (en vuelo o ya puestos). */
    val chipsVisibles: Int get() = sheet.chips.count { it > 0f }

    /** El número del centro del anillo. */
    val kcalMostradas: Int get() = home.kcal

    /** Fracción de cada anillo: kcal, proteína, hidratos y grasas. */
    val anillos: FloatArray get() = home.rings

    /** La hoja está a la vista (aunque sea en parte). */
    val hojaVisible: Boolean get() = sheet.top < NutricionLayout.Height
}

// ── dedo

private class Gesture(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val driftX: Float, val driftY: Float,
    val tIn: Float, val tTouch: Float, val tRelease: Float,
)

private val RegisterGesture = Gesture(
    272f, 648f, NutricionLayout.FabCx, NutricionLayout.FabCy, 12f, 26f,
    NutricionBeats.Finger1In, NutricionBeats.Touch1, NutricionBeats.Release1,
)
private val SaveGesture = Gesture(
    238f, 652f, 150f, NutricionLayout.CtaCy, 16f, 28f,
    NutricionBeats.Finger2In, NutricionBeats.Touch2, NutricionBeats.Release2,
)

private fun touchAt(t: Float, g: Gesture): NutricionTouch {
    val approach = eInOut(seg(t, g.tIn, g.tTouch))
    val drift = eOutCubic(seg(t, g.tRelease, g.tRelease + 0.45f))
    // Un pequeño arco: el dedo no viaja en línea recta.
    val arc = sin(PI.toFloat() * approach) * 12f
    val x = lerpF(g.x0, g.x1, approach) + arc + g.driftX * drift
    val y = lerpF(g.y0, g.y1, approach) + g.driftY * drift
    val alpha = seg(t, g.tIn, g.tIn + 0.16f) * (1f - seg(t, g.tRelease + 0.05f, g.tRelease + 0.40f))
    val press = seg(t, g.tTouch, g.tTouch + 0.07f) * (1f - seg(t, g.tRelease, g.tRelease + 0.08f))
    val ripple = if (t < g.tTouch) 0f else seg(t, g.tTouch, g.tTouch + 0.55f)
    return NutricionTouch(x, y, alpha, press, ripple)
}

// ── rótulo del paso

private const val CaptionFade = 0.35f

internal fun nutricionCaptionAt(t: Float): NutricionCaption {
    val b = NutricionBeats
    return when {
        t < b.Caption1 -> NutricionCaption(0, 0, 1f)
        t < b.Caption2 -> NutricionCaption(1, 0, seg(t, b.Caption1, b.Caption1 + CaptionFade))
        t < b.Caption3 -> NutricionCaption(2, 1, seg(t, b.Caption2, b.Caption2 + CaptionFade))
        else -> NutricionCaption(3, 2, seg(t, b.Caption3, b.Caption3 + CaptionFade))
    }
}

/** Parpadeo suave del cursor: encendido algo más de la mitad del ciclo, con fundidos cortos. */
private fun blink(sinceOn: Float): Float {
    val p = (sinceOn / 0.9f).let { it - kotlin.math.floor(it) }
    return clamp01(p / 0.08f) * clamp01((0.58f - p) / 0.08f)
}

private fun bell(u: Float): Float = sin(PI.toFloat() * u)

/** Entrada y salida suaves (sin picos de velocidad) para los fundidos. */
private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

// ── el cuadro completo

internal fun nutricionFrameAt(rawT: Float): NutricionFrame {
    val t = if (rawT.isFinite()) rawT.mod(WelcomeNutricionPeriod) else 0f
    val b = NutricionBeats
    val meal = NutricionMeal
    val foods = meal.foods

    // Fundido de entrada y de salida: en t = 0 y en t = periodo la pantalla está del todo cubierta, así que el
    // salto del final al principio del bucle no se ve.
    val dip = maxOf(1f - smooth(seg(t, b.RevealStart, b.RevealEnd)), smooth(seg(t, b.DipStart, b.DipEnd)))

    // Dedo
    val registerTouch = touchAt(t, RegisterGesture)
    val saveTouch = touchAt(t, SaveGesture)
    val touch = if (t >= b.Finger2In) saveTouch else registerTouch

    // ── hoja
    val top = nutricionSheetTop(t)
    val scrim = clamp01((NutricionLayout.SheetHidden - top) / (NutricionLayout.SheetHidden - NutricionLayout.SheetTyping))
    val keyboard = eOutCubic(seg(t, b.KbUp0, b.KbUp1)) * (1f - eInOut(seg(t, b.KbDown0, b.KbDown1)))

    val times = NutricionTyping.times
    val typed = NutricionTyping.countAt(t)
    val keyChars = CharArray(4)
    val keyGlow = FloatArray(4)
    if (keyboard > 0.01f) {
        var slot = 0
        var i = typed - 1
        while (i >= 0 && slot < 4) {
            val age = t - times[i]
            if (age > NutricionTyping.KeyGlowSecs) break
            val k = 1f - age / NutricionTyping.KeyGlowSecs
            keyChars[slot] = NutricionMeal.Sentence[i]
            keyGlow[slot] = k * k
            slot++
            i--
        }
    }
    val enterGlow = bell(seg(t, b.EnterPress, b.EnterPress + 0.20f))

    val typingActive = t >= b.TypeStart - 0.05f && t <= times.last() + 0.25f
    val caretOn = when {
        t < b.CaretOn -> 0f
        typingActive -> 1f
        else -> blink(t - b.CaretOn)
    }
    val caret = caretOn * (1f - seg(t, b.Expand0, b.Expand0 + 0.2f))
    val focus = seg(t, b.CaretOn - 0.1f, b.CaretOn + 0.15f) * (1f - seg(t, b.Expand0, b.Expand0 + 0.3f))

    val underline = FloatArray(4)
    val ignite = FloatArray(4)
    for (k in 0..3) {
        val endTime = times[meal.spans[k].last]
        val chipStart = b.Chips0 + b.ChipStagger * k
        underline[k] = eOutCubic(seg(t, endTime, endTime + 0.30f)) * (1f - seg(t, chipStart + 0.05f, chipStart + 0.30f))
        ignite[k] = seg(t, b.Shimmer0 + 0.05f + 0.14f * k, b.Shimmer0 + 0.30f + 0.14f * k)
    }
    val shimmer = seg(t, b.Shimmer0, b.Shimmer1)
    val textAlpha = 1f - eInOut(seg(t, b.Chips0 - 0.05f, b.Chips0 + 0.40f))
    val chips = FloatArray(4) { k ->
        seg(t, b.Chips0 + b.ChipStagger * k, b.Chips0 + b.ChipStagger * k + b.ChipFlight)
    }

    val rowIn = FloatArray(4)
    val rowRoll = FloatArray(4)
    val rowKcal = IntArray(4)
    val rowBars = FloatArray(12)
    var totalKcal = 0
    var totalProtein = 0
    var totalCarbs = 0
    var totalFat = 0
    var resolved = 0
    for (i in 0..3) {
        val food = foods[i]
        val start = b.Rows0 + b.RowStagger * i
        val roll = seg(t, start + 0.08f, start + 0.08f + b.RowRoll)
        val e = eOutCubic(roll)
        rowIn[i] = seg(t, start, start + 0.42f)
        rowRoll[i] = roll
        rowKcal[i] = (food.kcal * e).roundToInt()
        rowBars[i * 3] = eOutCubic(seg(t, start + 0.16f, start + 0.66f)) * barFill(food.protein)
        rowBars[i * 3 + 1] = eOutCubic(seg(t, start + 0.22f, start + 0.72f)) * barFill(food.carbs)
        rowBars[i * 3 + 2] = eOutCubic(seg(t, start + 0.28f, start + 0.78f)) * barFill(food.fat)
        totalKcal += rowKcal[i]
        totalProtein += (food.protein * e).roundToInt()
        totalCarbs += (food.carbs * e).roundToInt()
        totalFat += (food.fat * e).roundToInt()
        if (roll >= 1f) resolved++
    }

    val sheet = NutricionSheetFrame(
        top = top,
        scrim = scrim,
        keyboard = keyboard,
        typed = typed,
        caret = caret,
        focus = focus,
        keyChars = keyChars,
        keyGlow = keyGlow,
        enterGlow = enterGlow,
        underline = underline,
        ignite = ignite,
        shimmer = shimmer,
        textAlpha = textAlpha,
        chips = chips,
        rowIn = rowIn,
        rowRoll = rowRoll,
        rowKcal = rowKcal,
        rowBars = rowBars,
        totalKcal = totalKcal,
        totalProtein = totalProtein,
        totalCarbs = totalCarbs,
        totalFat = totalFat,
        ctaIn = seg(t, b.Cta0, b.Cta1),
        ctaPress = saveTouch.press,
    )

    // ── pantalla de Nutrición
    val ringProgress = FloatArray(4) { i ->
        eOutCubic(seg(t, b.Rings0 + b.RingStagger * i, b.Rings0 + b.RingStagger * i + b.RingSweep))
    }
    val rings = floatArrayOf(
        meal.totalKcal / NutricionMeal.GoalKcal.toFloat() * ringProgress[0],
        meal.totalProtein / NutricionMeal.GoalProtein.toFloat() * ringProgress[1],
        meal.totalCarbs / NutricionMeal.GoalCarbs.toFloat() * ringProgress[2],
        meal.totalFat / NutricionMeal.GoalFat.toFloat() * ringProgress[3],
    )
    val kcal = (meal.totalKcal * eOutCubic(seg(t, b.Num0, b.Num1))).roundToInt()
    val home = NutricionHomeFrame(
        rings = rings,
        kcal = kcal,
        kcalLeft = NutricionMeal.GoalKcal - kcal,
        protein = (meal.totalProtein * ringProgress[1]).roundToInt(),
        carbs = (meal.totalCarbs * ringProgress[2]).roundToInt(),
        fat = (meal.totalFat * ringProgress[3]).roundToInt(),
        summaryAlpha = lerpF(0.4f, 1f, seg(t, b.Rings0, b.Rings0 + 0.4f)),
        cardGrow = eInOut(seg(t, b.Card0, b.Card1)),
        cardFoods = FloatArray(4) { k -> eOutBack(seg(t, b.Card0 + 0.22f + 0.07f * k, b.Card0 + 0.60f + 0.07f * k)) },
        badge = eOutBack(seg(t, b.Badge0, b.Badge1)),
        tour = FloatArray(4) { i -> bell(seg(t, b.Tour0 + b.TourStep * i, b.Tour0 + b.TourStep * (i + 1))) },
        fabPress = registerTouch.press,
    )

    return NutricionFrame(
        t = t,
        dip = dip,
        caption = nutricionCaptionAt(t),
        touch = touch,
        home = home,
        sheet = sheet,
        filasResueltas = resolved,
    )
}

/** Largo de una minibarra de macro respecto al tope de la escala (30 g), con un mínimo para que 1 g se vea. */
private fun barFill(grams: Int): Float = if (grams <= 0) 0f else maxOf(grams / 30f, 0.07f).coerceAtMost(1f)
