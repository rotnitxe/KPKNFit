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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Línea de tiempo PURA de la escena de Entreno: dado `t` (segundos desde el inicio del bucle) devuelve QUÉ se ve
 * (texto tecleado, filas que existen, desplazamiento de la lista, serie marcada, posición del dedo…). No dibuja
 * nada ni usa reloj: el composable solo pinta el [EntrenoFrame] resultante. Así cada instante se puede probar.
 *
 * Guion (≈ 16 s):
 *   0 – 1,1   Entreno: el dedo llega a «Nueva sesión» y la toca.
 *   1,1 – 3,1 La hoja del editor sube; se teclea «Pecho y espalda».
 *   3,1 – 6,5 «Añadir ejercicio»: la lista se desliza con inercia; se eligen Press banca y Remo con barra; los
 *             contadores (ejercicios · series · min) ruedan.
 *   6,5 – 7,2 «Empezar sesión»: la hoja baja y aparece la sesión en vivo con el cronómetro corriendo.
 *   7,2 – 11  Series: 60 × 8 ✓ (descanso en time-lapse que se salta), 62,5 × 8 ✓ (¡Récord!), 62,5 × 7 ✓.
 *   11 – 16   Respiro con el estado final (con movimiento reducido se congela en 0,7 × periodo ≈ 11,2 s) y fundido.
 */

/** Tiempos (s) de cada hito. Se encadenan unos con otros: mover uno desplaza todo lo que sigue. */
internal object EntrenoT {
    // ---- Inicio
    const val veilIn = 0.42f
    const val fingerIn = 0.30f
    const val tapHome = 1.06f

    // ---- Hoja del editor
    const val sheetRise = tapHome + 0.06f
    const val focusIn = sheetRise + 0.52f
    const val typeStart = focusIn + 0.12f
    const val typeDur = 0.95f
    const val typeEnd = typeStart + typeDur
    const val tapAdd = typeEnd + 0.36f

    // ---- Selector de ejercicios
    const val pickerRise = tapAdd + 0.06f
    const val flingDown = pickerRise + 0.42f
    const val flingUp = flingDown + 0.28f
    const val inertia = 0.85f
    const val tapPress = flingUp + 0.64f
    const val tapRemo = tapPress + 0.40f
    const val tapAnadir = tapRemo + 0.44f
    const val pickerClose = tapAnadir + 0.06f
    const val pickerCloseDur = 0.34f

    // ---- Filas del editor y salida
    const val row1 = pickerClose + 0.18f
    const val row2 = row1 + 0.16f
    const val ctaOn = row2 + 0.16f
    const val tapEmpezar = ctaOn + 0.34f
    const val exitStart = tapEmpezar + 0.07f
    const val exitDur = 0.55f

    // ---- Sesión en vivo
    const val liveStart = exitStart + 0.20f
    const val tapRow1 = tapEmpezar + 0.74f
    const val roll1 = tapRow1 + 0.06f
    const val tapCheck1 = tapRow1 + 0.62f
    const val dock1In = tapCheck1 + 0.26f
    const val lapseStart = dock1In + 0.36f
    const val lapseEnd = lapseStart + 0.55f
    const val tapSaltar = lapseEnd + 0.18f
    const val dock1Out = tapSaltar + 0.04f
    const val dock1OutDur = 0.34f
    const val act2 = tapSaltar + 0.14f
    const val roll2 = tapSaltar + 0.18f
    const val tapCheck2 = tapSaltar + 0.74f
    const val act3 = tapCheck2 + 0.14f
    const val roll3 = tapCheck2 + 0.18f
    const val tapCheck3 = tapCheck2 + 0.70f
    const val dock3In = tapCheck3 + 0.24f
    const val act4 = tapCheck3 + 0.35f
    const val coachIn = tapCheck3 + 1.10f

    /** Segundos de descanso que se «saltan» en el time-lapse y duración total del descanso. */
    const val lapseSeconds = 76f
    const val restTotal = 90

    /** Fundido final del bucle. */
    const val veilOut = 0.5f
}

/** Un toque del dedo: cuándo, dónde y qué control debe estar bajo él. */
internal class EntrenoTap(val id: String, val t: Float, val x: Float, val y: Float, val target: EntrenoRect)

private val tapHomeCard = EntrenoTap("nueva-sesion", EntrenoT.tapHome, EntrenoGeo.homeTapX, EntrenoGeo.homeTapY, EntrenoGeo.homeCard)
private val tapAddButton = EntrenoGeo.addButtonAbs(0f).let { EntrenoTap("anadir-ejercicio", EntrenoT.tapAdd, it.cx, it.cy, it) }
private val tapPressRow = EntrenoGeo.catRowRect(EntrenoGeo.idxPressBanca, EntrenoGeo.pickerScrollEnd)
    .let { EntrenoTap("press-banca", EntrenoT.tapPress, 196f, it.cy, it) }
private val tapRemoRow = EntrenoGeo.catRowRect(EntrenoGeo.idxRemoConBarra, EntrenoGeo.pickerScrollEnd)
    .let { EntrenoTap("remo-con-barra", EntrenoT.tapRemo, 196f, it.cy, it) }
private val tapAnadirCta = EntrenoTap("anadir-2", EntrenoT.tapAnadir, EntrenoGeo.cta.cx, EntrenoGeo.cta.cy, EntrenoGeo.cta)
private val tapEmpezarCta = EntrenoTap("empezar-sesion", EntrenoT.tapEmpezar, EntrenoGeo.cta.cx, EntrenoGeo.cta.cy, EntrenoGeo.cta)
private val tapSerie1 = EntrenoGeo.liveRow(0).let { EntrenoTap("serie-1", EntrenoT.tapRow1, EntrenoGeo.weightX + 4f, it.cy, it) }
private val tapVisto1 = EntrenoGeo.checkRect(0).let { EntrenoTap("visto-1", EntrenoT.tapCheck1, it.cx, it.cy, it) }
private val tapSaltarBtn = EntrenoTap("saltar", EntrenoT.tapSaltar, EntrenoGeo.saltar.cx, EntrenoGeo.saltar.cy, EntrenoGeo.saltar)
private val tapVisto2 = EntrenoGeo.checkRect(1).let { EntrenoTap("visto-2", EntrenoT.tapCheck2, it.cx, it.cy, it) }
private val tapVisto3 = EntrenoGeo.checkRect(2).let { EntrenoTap("visto-3", EntrenoT.tapCheck3, it.cx, it.cy, it) }

/** Todos los toques del guion, en orden. */
internal val EntrenoTaps: List<EntrenoTap> = listOf(
    tapHomeCard, tapAddButton, tapPressRow, tapRemoRow, tapAnadirCta, tapEmpezarCta,
    tapSerie1, tapVisto1, tapSaltarBtn, tapVisto2, tapVisto3,
)

// ======================================================================================== datos de un cuadro

internal enum class EntrenoEtapa { INICIO, EDITOR, SELECTOR, SESION }

internal enum class EntrenoEstadoSerie { PENDIENTE, ACTIVA, HECHA }

/**
 * Un número que rueda de [from] a [to] con progreso [p] (0 = se ve [from], 1 = se ve [to]). Las cadenas se
 * alinean por la derecha, dígito a dígito (odómetro); [from] vacío = el número entra desde cero.
 */
internal data class EntrenoRoll(val from: String, val to: String, val p: Float) {
    val done: Boolean get() = p >= 0.999f || from == to

    /** Lo que se lee cuando el giro ya terminó (o no empezó). */
    val shown: String get() = if (p >= 0.5f) to else from
}

/** El dedo: posición, opacidad, cuánto está hundido y progreso de la onda del último toque (-1 = sin onda). */
internal data class EntrenoDedo(val x: Float, val y: Float, val alpha: Float, val press: Float, val onda: Float)

internal data class EntrenoSerie(
    val estado: EntrenoEstadoSerie,
    /** 0..1: la fila entra en pantalla. */
    val enter: Float,
    /** 0..1: resalte de «serie activa». */
    val activa: Float,
    /** 0..1: pulso al tocar la fila (rellenar valores). */
    val touch: Float,
    val peso: EntrenoRoll,
    val reps: EntrenoRoll,
    /** 0..1: transición a verde. */
    val hecha: Float,
    /** Escala del botón «visto» (hundido al tocar, pop al confirmar). */
    val checkScale: Float,
    /** Anillo de confirmación: progreso 0..1, -1 = sin anillo. */
    val onda: Float,
    /** Insignia «¡Récord!»: 0 = sin insignia; pasa de 1 al entrar (rebote). */
    val record: Float,
)

internal data class EntrenoDescanso(
    /** 0 = fuera de pantalla, 1 = a la vista (con rebote). */
    val visible: Float,
    /** Tras qué serie se descansa (1 o 3). */
    val serie: Int,
    val restante: Int,
    /** Fracción del anillo que queda (1 = lleno). */
    val fraccion: Float,
    /** 0..1: intensidad del avance rápido (time-lapse). */
    val lapso: Float,
    val saltarPress: Float,
)

internal data class EntrenoFilaEditor(val enter: Float, val chip1: Float, val chip2: Float, val chip3: Float)

internal data class EntrenoEditor(
    /** `y` del borde superior de la hoja (640 = escondida). */
    val hojaY: Float,
    /** Opacidad del telón oscuro sobre la pantalla de debajo. */
    val telon: Float,
    val foco: Float,
    val nombre: String,
    val cursor: Boolean,
    val fila1: EntrenoFilaEditor,
    val fila2: EntrenoFilaEditor,
    /** Número continuo de filas (0..2): coloca el botón «Añadir ejercicio». */
    val filas: Float,
    /** Opacidad del marcador «aún no hay ejercicios». */
    val vacio: Float,
    val ejercicios: EntrenoRoll,
    val series: EntrenoRoll,
    val minutos: EntrenoRoll,
    val addPress: Float,
    /** 0..1: «Empezar sesión» pasa de desactivado a activo. */
    val ctaOn: Float,
    /** Onda de aviso al activarse el botón (-1 = ninguna). */
    val ctaPulse: Float,
    val ctaPress: Float,
)

internal data class EntrenoSelector(
    val y: Float,
    val telon: Float,
    val scroll: Float,
    /** Marca de «elegido» de Press banca y Remo con barra (0..1, con rebote). */
    val sel1: Float,
    val sel2: Float,
    /** Entrada del botón «Añadir n ejercicios». */
    val cta: Float,
    val ctaCount: EntrenoRoll,
    val ctaPress: Float,
)

internal data class EntrenoSesion(
    /** Opacidad de la pantalla de sesión como capa base. */
    val baseAlpha: Float,
    /** Progreso lineal 0..1 de la entrada del contenido (el dibujo escalona por elemento). */
    val enter: Float,
    val crono: EntrenoRoll,
    val hechas: EntrenoRoll,
    /** Segmentos llenos de la barra de progreso (continuo, 0..8). */
    val progreso: Float,
    val series: List<EntrenoSerie>,
    val descanso: EntrenoDescanso,
    val coach: Float,
)

internal data class EntrenoFrame(
    val t: Float,
    val etapa: EntrenoEtapa,
    /** Velo de fundido del bucle (1 = pantalla vacía). */
    val velo: Float,
    /** Pulso lento 0..1 para los detalles que respiran. */
    val latido: Float,
    val tarjetaPress: Float,
    val tarjetaOnda: Float,
    val homeAlpha: Float,
    val editor: EntrenoEditor,
    val selector: EntrenoSelector,
    val sesion: EntrenoSesion,
    val dedo: EntrenoDedo,
)

// ======================================================================================== utilidades

private const val TAU = 6.2831855f

/** Muelle suave de 0 a 1: sube, rebasa un poco y se asienta (subamortiguado). Con `dt ≤ 0` vale 0. */
internal fun entrenoSettle(dt: Float, omega: Float = 16f, zeta: Float = 0.78f): Float {
    if (dt <= 0f) return 0f
    val d = if (dt > 4f) 4f else dt
    val wd = omega * sqrt(1f - zeta * zeta)
    val decay = exp(-zeta * omega * d)
    return 1f - decay * (cos(wd * d) + (zeta * omega / wd) * sin(wd * d))
}

/** Envolvente de un toque en [tapT]: se hunde justo antes y se suelta justo después (0..1). */
internal fun entrenoTapEnv(t: Float, tapT: Float): Float {
    val dt = t - tapT
    return when {
        dt < -0.08f || dt > 0.20f -> 0f
        dt < 0f -> seg(dt, -0.08f, 0f)
        else -> 1f - seg(dt, 0.05f, 0.20f)
    }
}

/**
 * Número que rueda por escalones: `values[0]` hasta `starts[0]`; luego pasa a `values[1]` rodando durante [dur]
 * segundos, y así sucesivamente. `values.size == starts.size + 1`.
 */
internal fun entrenoRollSteps(t: Float, starts: FloatArray, values: IntArray, dur: Float): EntrenoRoll {
    var k = 0
    while (k < starts.size && t >= starts[k]) k++
    if (k == 0) {
        val s = values[0].toString()
        return EntrenoRoll(s, s, 1f)
    }
    val p = clamp01((t - starts[k - 1]) / dur)
    return EntrenoRoll(values[k - 1].toString(), values[k].toString(), p)
}

/** Formato mm:ss del cronómetro. */
internal fun entrenoClock(seconds: Int): String {
    val s = if (seconds < 0) 0 else seconds
    val m = s / 60
    val r = s % 60
    return (if (m < 10) "0$m" else "$m") + ":" + (if (r < 10) "0$r" else "$r")
}

/** Formato m:ss del descanso (1:30). */
internal fun entrenoRestClock(seconds: Int): String {
    val s = if (seconds < 0) 0 else seconds
    val r = s % 60
    return "${s / 60}:" + (if (r < 10) "0$r" else "$r")
}

// ======================================================================================== nombre tecleado

internal const val EntrenoNombre = "Pecho y espalda"

/** Pausa relativa ANTES de cada carácter (los espacios y los cambios de palabra dudan un poco más). */
private val TypeWeights = floatArrayOf(1.0f, .85f, .95f, 1.1f, .8f, 1.7f, .9f, 1.7f, 1.0f, .85f, .8f, .9f, 1.1f, .85f, .9f)

/** Instante en que aparece cada carácter del nombre. */
private val CharTimes: FloatArray = FloatArray(EntrenoNombre.length).also { out ->
    val sum = TypeWeights.sum()
    var acc = 0f
    for (i in out.indices) {
        acc += TypeWeights[i]
        out[i] = EntrenoT.typeStart + EntrenoT.typeDur * acc / sum
    }
}

/** Texto del nombre que ya está tecleado en el instante [t] (siempre un prefijo de [EntrenoNombre]). */
internal fun entrenoTyped(t: Float): String {
    var n = 0
    while (n < CharTimes.size && t >= CharTimes[n]) n++
    return EntrenoNombre.substring(0, n)
}

// ======================================================================================== desplazamiento

private const val DragDistance = 270f
private const val InertiaK = 5.65f

/** Desplazamiento de la lista del selector: sigue al dedo mientras arrastra y luego sigue por inercia. */
internal fun entrenoScroll(t: Float): Float = when {
    t <= EntrenoT.flingDown -> 0f
    t < EntrenoT.flingUp -> DragDistance * ((t - EntrenoT.flingDown) / (EntrenoT.flingUp - EntrenoT.flingDown)).pow(1.35f)
    else -> {
        val u = clamp01((t - EntrenoT.flingUp) / EntrenoT.inertia)
        val f = (1f - exp(-InertiaK * u)) / (1f - exp(-InertiaK))
        DragDistance + (EntrenoGeo.pickerScrollEnd - DragDistance) * f
    }
}

// ======================================================================================== el dedo

private const val LIN = 0
private const val IO = 1
private const val OUT = 2
private const val POW = 3

private class EntrenoWp(val t: Float, val x: Float, val y: Float, val ease: Int)

private fun easeBy(kind: Int, u: Float): Float = when (kind) {
    IO -> eInOut(u)
    OUT -> eOutCubic(u)
    POW -> u.pow(1.35f)
    else -> u
}

private val FingerPath: Array<EntrenoWp> by lazy {
    val tp = EntrenoT
    arrayOf(
        EntrenoWp(tp.fingerIn, 238f, 330f, OUT),
        EntrenoWp(tp.tapHome - 0.06f, tapHomeCard.x, tapHomeCard.y, OUT),
        EntrenoWp(tp.tapHome + 0.26f, tapHomeCard.x + 2f, tapHomeCard.y + 2f, LIN),
        EntrenoWp(tp.tapHome + 0.58f, 214f, 220f, IO),
        EntrenoWp(tp.tapAdd - 0.52f, 240f, 440f, IO),
        EntrenoWp(tp.tapAdd - 0.06f, tapAddButton.x + 2f, tapAddButton.y + 2f, OUT),
        EntrenoWp(tp.tapAdd + 0.08f, tapAddButton.x, tapAddButton.y, LIN),
        EntrenoWp(tp.flingDown - 0.06f, 212f, 566f, IO),
        EntrenoWp(tp.flingDown, 212f, 562f, LIN),
        EntrenoWp(tp.flingUp, 206f, 562f - DragDistance, POW),
        EntrenoWp(tp.tapPress - 0.10f, 200f, tapPressRow.y + 2f, OUT),
        EntrenoWp(tp.tapPress + 0.08f, tapPressRow.x, tapPressRow.y, LIN),
        EntrenoWp(tp.tapRemo - 0.06f, tapRemoRow.x + 2f, tapRemoRow.y, IO),
        EntrenoWp(tp.tapRemo + 0.10f, tapRemoRow.x, tapRemoRow.y, LIN),
        EntrenoWp(tp.tapAnadir - 0.06f, tapAnadirCta.x, tapAnadirCta.y, IO),
        EntrenoWp(tp.tapEmpezar + 0.20f, tapEmpezarCta.x, tapEmpezarCta.y, LIN),
        EntrenoWp(tp.tapRow1 - 0.38f, 70f, 330f, IO),
        EntrenoWp(tp.tapRow1 - 0.08f, tapSerie1.x + 2f, tapSerie1.y + 2f, OUT),
        EntrenoWp(tp.tapRow1 + 0.10f, tapSerie1.x, tapSerie1.y, LIN),
        EntrenoWp(tp.tapCheck1 - 0.14f, tapVisto1.x - 2f, tapVisto1.y, IO),
        EntrenoWp(tp.tapCheck1 + 0.10f, tapVisto1.x, tapVisto1.y, LIN),
        EntrenoWp(tp.tapCheck1 + 0.36f, tapVisto1.x, tapVisto1.y, LIN),
        EntrenoWp(tp.tapSaltar - 0.40f, 232f, 520f, IO),
        EntrenoWp(tp.tapSaltar - 0.06f, tapSaltarBtn.x + 2f, tapSaltarBtn.y + 2f, OUT),
        EntrenoWp(tp.tapSaltar + 0.10f, tapSaltarBtn.x, tapSaltarBtn.y, LIN),
        EntrenoWp(tp.tapCheck2 - 0.14f, tapVisto2.x - 2f, tapVisto2.y, IO),
        EntrenoWp(tp.tapCheck2 + 0.10f, tapVisto2.x, tapVisto2.y, LIN),
        EntrenoWp(tp.tapCheck3 - 0.14f, tapVisto3.x - 2f, tapVisto3.y, IO),
        EntrenoWp(tp.tapCheck3 + 0.12f, tapVisto3.x, tapVisto3.y, LIN),
        EntrenoWp(tp.tapCheck3 + 0.55f, 246f, 412f, IO),
    )
}

/** Tiempos de cada punto de paso del dedo (para comprobar que son crecientes). */
internal fun entrenoFingerWaypointTimes(): FloatArray = FloatArray(FingerPath.size) { FingerPath[it].t }

/** Ventanas de visibilidad del dedo: [empieza a verse, ya se ve del todo, empieza a irse, ya no se ve]. */
private val FingerWindows: Array<FloatArray> by lazy {
    val tp = EntrenoT
    arrayOf(
        floatArrayOf(tp.fingerIn, tp.fingerIn + 0.28f, tp.tapHome + 0.24f, tp.tapHome + 0.56f),
        floatArrayOf(tp.tapAdd - 0.52f, tp.tapAdd - 0.26f, tp.tapEmpezar + 0.10f, tp.tapEmpezar + 0.34f),
        floatArrayOf(tp.tapRow1 - 0.36f, tp.tapRow1 - 0.12f, tp.tapCheck1 + 0.14f, tp.tapCheck1 + 0.38f),
        floatArrayOf(tp.tapSaltar - 0.40f, tp.tapSaltar - 0.18f, tp.tapCheck3 + 0.20f, tp.tapCheck3 + 0.50f),
    )
}

private fun fingerAlpha(t: Float): Float {
    var a = 0f
    for (w in FingerWindows) a = max(a, seg(t, w[0], w[1]) * (1f - seg(t, w[2], w[3])))
    return a
}

private fun fingerPress(t: Float): Float {
    var best = 0f
    for (tap in EntrenoTaps) best = max(best, entrenoTapEnv(t, tap.t))
    val hold = seg(t, EntrenoT.flingDown - 0.06f, EntrenoT.flingDown) * (1f - seg(t, EntrenoT.flingUp, EntrenoT.flingUp + 0.10f))
    return max(best, hold)
}

private fun fingerRipple(t: Float): Float {
    var out = -1f
    for (tap in EntrenoTaps) {
        val dt = t - tap.t
        if (dt >= 0f && dt < 0.55f) out = dt / 0.55f
    }
    return out
}

private fun dedoAt(t: Float): EntrenoDedo {
    val p = FingerPath
    var x = p[0].x
    var y = p[0].y
    if (t >= p[p.size - 1].t) {
        x = p[p.size - 1].x
        y = p[p.size - 1].y
    } else if (t > p[0].t) {
        var i = 0
        while (i < p.size - 2 && t > p[i + 1].t) i++
        val a = p[i]
        val b = p[i + 1]
        val u = easeBy(b.ease, seg(t, a.t, b.t))
        x = lerpF(a.x, b.x, u)
        y = lerpF(a.y, b.y, u)
    }
    return EntrenoDedo(x, y, fingerAlpha(t), fingerPress(t), fingerRipple(t))
}

// ======================================================================================== editor

private fun filaEditor(t: Float, at: Float): EntrenoFilaEditor = EntrenoFilaEditor(
    enter = entrenoSettle(t - at, 18f, 0.68f),
    chip1 = eOutBack(seg(t, at + 0.18f, at + 0.40f)),
    chip2 = eOutBack(seg(t, at + 0.25f, at + 0.47f)),
    chip3 = eOutBack(seg(t, at + 0.32f, at + 0.54f)),
)

/** Hueco que ocupa una fila que entra: se abre ANTES de que la fila se vea, para que «Añadir ejercicio» nunca la pise. */
private fun rowSpace(enter: Float): Float = eOutCubic(clamp01(enter * 2.2f))

private fun editorAt(t: Float): EntrenoEditor {
    val tp = EntrenoT
    val rise = entrenoSettle(t - tp.sheetRise, 15f, 0.8f)
    val exit = eInOut(seg(t, tp.exitStart, tp.exitStart + tp.exitDur))
    val y = lerpF(EntrenoGeo.sheetHidden, EntrenoGeo.sheetRest, rise) + exit * (EntrenoGeo.sheetHidden - EntrenoGeo.sheetRest)
    val telon = 0.62f * eOutCubic(seg(t, tp.sheetRise, tp.sheetRise + 0.4f)) *
        (1f - eInOut(seg(t, tp.exitStart + 0.12f, tp.exitStart + tp.exitDur)))
    val foco = eOutCubic(seg(t, tp.focusIn, tp.focusIn + 0.2f)) * (1f - seg(t, tp.tapAdd, tp.tapAdd + 0.2f))
    val typing = t >= tp.typeStart - 0.15f && t <= tp.typeEnd + 0.30f
    val blinkOn = ((max(0f, t - tp.focusIn) / 0.55f).toInt() % 2) == 0
    val f1 = filaEditor(t, tp.row1)
    val f2 = filaEditor(t, tp.row2)
    val filas = rowSpace(f1.enter) + rowSpace(f2.enter)
    val starts = floatArrayOf(tp.row1 + 0.12f)
    return EntrenoEditor(
        hojaY = y,
        telon = telon,
        foco = foco,
        nombre = entrenoTyped(t),
        cursor = foco > 0.5f && (typing || blinkOn),
        fila1 = f1,
        fila2 = f2,
        filas = filas,
        vacio = 1f - seg(t, tp.row1 - 0.02f, tp.row1 + 0.18f),
        ejercicios = entrenoRollSteps(t, starts, intArrayOf(0, 2), 0.42f),
        series = entrenoRollSteps(t, starts, intArrayOf(0, 8), 0.46f),
        minutos = entrenoRollSteps(t, starts, intArrayOf(0, 50), 0.52f),
        addPress = entrenoTapEnv(t, tp.tapAdd),
        ctaOn = eOutCubic(seg(t, tp.ctaOn, tp.ctaOn + 0.30f)),
        ctaPulse = (t - tp.ctaOn).let { dt -> if (dt in 0f..0.6f) dt / 0.6f else -1f },
        ctaPress = entrenoTapEnv(t, tp.tapEmpezar),
    )
}

// ======================================================================================== selector

private fun selectorAt(t: Float): EntrenoSelector {
    val tp = EntrenoT
    val rise = entrenoSettle(t - tp.pickerRise, 16f, 0.8f)
    val close = eInOut(seg(t, tp.pickerClose, tp.pickerClose + tp.pickerCloseDur))
    val y = lerpF(EntrenoGeo.sheetHidden, EntrenoGeo.pickerRest, rise) + close * (EntrenoGeo.sheetHidden - EntrenoGeo.pickerRest)
    val telon = 0.58f * eOutCubic(seg(t, tp.pickerRise, tp.pickerRise + 0.35f)) *
        (1f - eInOut(seg(t, tp.pickerClose + 0.05f, tp.pickerClose + tp.pickerCloseDur)))
    return EntrenoSelector(
        y = y,
        telon = telon,
        scroll = entrenoScroll(t),
        sel1 = eOutBack(seg(t, tp.tapPress, tp.tapPress + 0.30f)),
        sel2 = eOutBack(seg(t, tp.tapRemo, tp.tapRemo + 0.30f)),
        cta = eOutBack(seg(t, tp.tapPress + 0.10f, tp.tapPress + 0.42f)),
        ctaCount = entrenoRollSteps(t, floatArrayOf(tp.tapRemo + 0.04f), intArrayOf(1, 2), 0.30f),
        ctaPress = entrenoTapEnv(t, tp.tapAnadir),
    )
}

// ======================================================================================== sesión en vivo

private val CheckAt by lazy { floatArrayOf(EntrenoT.tapCheck1, EntrenoT.tapCheck2, EntrenoT.tapCheck3) }
private val ActivateAt by lazy { floatArrayOf(EntrenoT.tapRow1, EntrenoT.act2, EntrenoT.act3, EntrenoT.act4) }
private val RollAt by lazy { floatArrayOf(EntrenoT.roll1, EntrenoT.roll2, EntrenoT.roll3) }
private val PesoText = arrayOf("60", "62,5", "62,5")
private val RepsText = arrayOf("8", "8", "7")

private val NoRoll = EntrenoRoll("", "", 1f)

/** Duración del giro de los números de una serie al rellenarse. */
private const val ROLL_DUR = 0.36f

private fun serieAt(i: Int, t: Float): EntrenoSerie {
    val tp = EntrenoT
    val hasCheck = i < 3
    val chk = if (hasCheck) CheckAt[i] else Float.MAX_VALUE
    val act = ActivateAt[i]
    val enter = eOutCubic(seg(t, tp.liveStart + 0.10f + 0.06f * i, tp.liveStart + 0.55f + 0.06f * i))
    val activa = seg(t, act, act + 0.22f) * (if (hasCheck) 1f - seg(t, chk, chk + 0.18f) else 1f)
    val dt = t - chk
    val hecha = if (hasCheck) eOutCubic(seg(t, chk, chk + 0.30f)) else 0f
    val pop = if (hasCheck && dt >= 0f) 0.6f * exp(-9f * dt) * sin(20f * dt) else 0f
    val pressDown = if (hasCheck) entrenoTapEnv(t, chk) else 0f
    val estado = when {
        hasCheck && t >= chk -> EntrenoEstadoSerie.HECHA
        activa > 0.5f -> EntrenoEstadoSerie.ACTIVA
        else -> EntrenoEstadoSerie.PENDIENTE
    }
    return EntrenoSerie(
        estado = estado,
        enter = enter,
        activa = activa,
        touch = if (i == 0) entrenoTapEnv(t, tp.tapRow1) else 0f,
        peso = if (hasCheck) EntrenoRoll("", PesoText[i], seg(t, RollAt[i], RollAt[i] + ROLL_DUR)) else NoRoll,
        reps = if (hasCheck) EntrenoRoll("", RepsText[i], seg(t, RollAt[i] + 0.06f, RollAt[i] + 0.06f + ROLL_DUR)) else NoRoll,
        hecha = hecha,
        checkScale = 1f - 0.14f * pressDown + pop,
        onda = if (hasCheck && dt >= 0f && dt < 0.6f) dt / 0.6f else -1f,
        record = if (i == 1) eOutBack(seg(t, chk + 0.16f, chk + 0.58f)) else 0f,
    )
}

private fun lapseProgress(t: Float): Float = eInOut(seg(t, EntrenoT.lapseStart, EntrenoT.lapseEnd))

/**
 * Desfase del reloj de la sesión (14 s y fracción): hace que el cuadro congelado del movimiento reducido
 * (0,7 × periodo) caiga siempre a mitad de un segundo, nunca mientras rueda un dígito del cronómetro.
 */
private val SessionBase: Float by lazy {
    val raw = (WelcomeEntrenoPeriod * 0.7f - EntrenoT.liveStart) + EntrenoT.lapseSeconds
    14f + (((0.55f - raw) % 1f) + 1f) % 1f
}

/** Segundos de sesión transcurridos: arranca en ≈ 14 s al aparecer la sesión y avanza a velocidad real, salvo el salto del time-lapse. */
internal fun entrenoSessionSeconds(t: Float): Float =
    SessionBase + max(0f, t - EntrenoT.liveStart) + EntrenoT.lapseSeconds * lapseProgress(t)

private fun descansoAt(t: Float): EntrenoDescanso {
    val tp = EntrenoT
    val first = t < tp.dock1Out + tp.dock1OutDur
    return if (first) {
        val vis = entrenoSettle(t - tp.dock1In, 16f, 0.78f) * (1f - eInOut(seg(t, tp.dock1Out, tp.dock1Out + tp.dock1OutDur)))
        val elapsed = max(0f, t - tp.dock1In) + tp.lapseSeconds * lapseProgress(t)
        val left = (tp.restTotal - elapsed).coerceIn(0f, tp.restTotal.toFloat())
        EntrenoDescanso(
            visible = vis,
            serie = 1,
            restante = left.toInt(),
            fraccion = left / tp.restTotal,
            lapso = sin(PI.toFloat() * seg(t, tp.lapseStart - 0.05f, tp.lapseEnd + 0.05f)),
            saltarPress = entrenoTapEnv(t, tp.tapSaltar),
        )
    } else {
        val left = (tp.restTotal - max(0f, t - tp.dock3In)).coerceIn(0f, tp.restTotal.toFloat())
        EntrenoDescanso(
            visible = entrenoSettle(t - tp.dock3In, 16f, 0.78f),
            serie = 3,
            restante = left.toInt(),
            fraccion = left / tp.restTotal,
            lapso = 0f,
            saltarPress = 0f,
        )
    }
}

private fun sesionAt(t: Float): EntrenoSesion {
    val tp = EntrenoT
    val sessionSeconds = entrenoSessionSeconds(t)
    val sec = floor(sessionSeconds).toInt()
    val frac = sessionSeconds - sec
    val lapso = sin(PI.toFloat() * seg(t, tp.lapseStart - 0.05f, tp.lapseEnd + 0.05f))
    val cronoP = if (lapso > 0.05f || frac > 0.22f) 1f else frac / 0.22f
    val starts = floatArrayOf(tp.tapCheck1 + 0.10f, tp.tapCheck2 + 0.10f, tp.tapCheck3 + 0.10f)
    var progreso = 0f
    for (c in CheckAt) progreso += eOutCubic(seg(t, c + 0.06f, c + 0.42f))
    return EntrenoSesion(
        baseAlpha = seg(t, tp.exitStart, tp.exitStart + 0.12f),
        enter = seg(t, tp.liveStart - 0.10f, tp.liveStart + 0.70f),
        crono = EntrenoRoll(entrenoClock(sec - 1), entrenoClock(sec), cronoP),
        hechas = entrenoRollSteps(t, starts, intArrayOf(0, 1, 2, 3), 0.36f),
        progreso = progreso,
        series = List(4) { serieAt(it, t) },
        descanso = descansoAt(t),
        coach = eOutCubic(seg(t, tp.coachIn, tp.coachIn + 0.45f)),
    )
}

// ======================================================================================== cuadro completo

private fun etapaAt(t: Float): EntrenoEtapa = when {
    t < EntrenoT.sheetRise -> EntrenoEtapa.INICIO
    t < EntrenoT.pickerRise -> EntrenoEtapa.EDITOR
    t < EntrenoT.pickerClose + EntrenoT.pickerCloseDur -> EntrenoEtapa.SELECTOR
    t < EntrenoT.exitStart -> EntrenoEtapa.EDITOR
    else -> EntrenoEtapa.SESION
}

/** Velo de fundido: cubre el arranque y el final del bucle para que el salto de vuelta al inicio no se note. */
internal fun entrenoVeil(t: Float): Float = max(
    1f - eOutCubic(seg(t, 0f, EntrenoT.veilIn)),
    eInOut(seg(t, WelcomeEntrenoPeriod - EntrenoT.veilOut, WelcomeEntrenoPeriod)),
)

/** Qué se ve en el instante [tIn] (segundos desde el inicio del bucle). Función pura y barata de evaluar por cuadro. */
internal fun entrenoFrameAt(tIn: Float): EntrenoFrame {
    val t = tIn.coerceIn(0f, WelcomeEntrenoPeriod)
    return EntrenoFrame(
        t = t,
        etapa = etapaAt(t),
        velo = entrenoVeil(t),
        latido = 0.5f + 0.5f * sin(TAU * t / 1.8f),
        tarjetaPress = entrenoTapEnv(t, EntrenoT.tapHome),
        tarjetaOnda = (t - EntrenoT.tapHome).let { dt -> if (dt in 0f..0.55f) dt / 0.55f else -1f },
        homeAlpha = 1f - seg(t, EntrenoT.exitStart, EntrenoT.exitStart + 0.12f),
        editor = editorAt(t),
        selector = selectorAt(t),
        sesion = sesionAt(t),
        dedo = dedoAt(t),
    )
}

/** Entrada escalonada de los elementos de la pantalla de inicio (índice del elemento, 0..7). */
internal fun entrenoHomeEnter(t: Float, index: Int): Float =
    eOutCubic(seg(t, 0.04f + 0.055f * index, 0.04f + 0.055f * index + 0.42f))
