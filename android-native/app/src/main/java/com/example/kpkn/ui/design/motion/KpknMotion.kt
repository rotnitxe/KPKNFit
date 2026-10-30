package com.example.kpkn.ui.design.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/** Una curva de movimiento: cualquier [Easing] de Compose (los «muelles» del lienzo son curvas bezier con rebasado, no `spring()`). */
typealias KpknEasing = Easing

/**
 * Duración o retardo del catálogo, en milisegundos. Los escalones con nombre están en [KpknMotion] (`D160`, `D240`, `R600`…); el
 * catálogo agrupa duraciones parecidas (±12 %), así que una fila del lienzo puede usar el valor exacto con `KpknDuration(420)`.
 */
@JvmInline
value class KpknDuration(val ms: Int) {
    val seconds: Float get() = ms / 1000f

    override fun toString(): String = "$ms ms"
}

/** Curva del catálogo: nombre, texto CSS original, función y las duraciones con las que más se usa (para la hoja de calibración). */
class KpknCurve(
    val name: String,
    val css: String,
    val easing: KpknEasing,
    val description: String,
    val typicalMs: List<Int>,
)

/** Escalón de duración o de retardo del catálogo. [exact] = no se agrupó con otro (no es de la escala redonda). */
class KpknScaleStep(val name: String, val duration: KpknDuration, val exact: Boolean)

/**
 * Catálogo de movimiento del lienzo «Pulido v1» (`paridad/CIMIENTOS_GRUPOS.md`): 14 curvas, 23 duraciones y 11 retardos, una sola vez.
 * Las pantallas usan estos nombres y no escriben curvas ni milisegundos sueltos.
 *
 * ```
 * val spec = kpknTween<Float>(KpknMotion.D240, KpknMotion.Estandar)          // transición de 240 ms
 * val e = KpknMotion.Resorte.transform(0.5f)                                 // la curva, evaluada
 * val curva = KpknMotion.curves.first { it.name == "trazo" }                 // el catálogo, por nombre
 * ```
 */
object KpknMotion {
    // ---- Las 14 curvas, con las coordenadas EXACTAS del lienzo (las palabras clave de CSS, con su bezier real) ----

    /** `cubic-bezier(.2,.8,.2,1)`: la de casi todo; sale rápido y se asienta despacio. */
    val Estandar: KpknEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** `ease` = `cubic-bezier(.25,.1,.25,1)`: color, fondo y opacidad. */
    val Suave: KpknEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** `linear`: barras, giros continuos y barridos. */
    val Lineal: KpknEasing = LinearEasing

    /** `cubic-bezier(.3,.7,.2,1)`: dibujo de líneas y anillos (stroke-dashoffset). */
    val Trazo: KpknEasing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)

    /** `ease-out` = `cubic-bezier(0,0,.58,1)`: pulsaciones y retornos cortos. */
    val Salida: KpknEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** `cubic-bezier(.3,1.5,.5,1)`: muelle del editor; rebasa un 15 % y vuelve. */
    val Resorte: KpknEasing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

    /** `ease-in-out` = `cubic-bezier(.42,0,.58,1)`: giros y trazos que van y vienen. */
    val Vaiven: KpknEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    /** `steps(1)`: todo o nada; vale 0 hasta el final del intervalo y 1 en él (parpadeo del cursor). */
    val Parpadeo: KpknEasing = Easing { fraction -> if (fraction >= 1f) 1f else 0f }

    /** `cubic-bezier(.5,0,.9,.5)`: salidas del editor; arranca lento y acelera. */
    val Cae: KpknEasing = CubicBezierEasing(0.5f, 0f, 0.9f, 0.5f)

    /** `cubic-bezier(.4,0,1,1)` (y `ease-in`, que se unió a ella): cierre de la hoja de la ruta y del velo. */
    val Acelera: KpknEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** `cubic-bezier(.4,0,.2,1)`: estándar de Material (entrada y salida de los fondos). */
    val Material: KpknEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** `cubic-bezier(.3,1.6,.5,1)`: muelle de suplementos; rebasa un 20 %. */
    val ResorteFuerte: KpknEasing = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)

    /** `cubic-bezier(.5,0,.2,1)`: trazado de la ruta del cardio. */
    val Ruta: KpknEasing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f)

    /** `cubic-bezier(.22,1.12,.36,1)`: apertura de la hoja de la ruta; rebasa un 5 %. */
    val ResorteSuave: KpknEasing = CubicBezierEasing(0.22f, 1.12f, 0.36f, 1f)

    // ---- Palabras clave de CSS que no son presets (por si una fila las escribe tal cual) ----

    /** `ease-in` de CSS (`cubic-bezier(.42,0,1,1)`); el catálogo la agrupó con [Acelera] (distancia 0,02). */
    val EaseIn: KpknEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

    /** Salida cúbica `1 − (1 − t)³` del contador `[data-count]` (la hace JavaScript en el lienzo, no una curva CSS). */
    val SalidaCubica: KpknEasing = Easing { x ->
        val inv = 1f - x
        1f - inv * inv * inv
    }

    // ---- Las 23 duraciones (ms) ----
    val D80 = KpknDuration(80)
    val D90 = KpknDuration(90)
    val D120 = KpknDuration(120)
    val D140 = KpknDuration(140)
    val D160 = KpknDuration(160)
    val D200 = KpknDuration(200)
    val D240 = KpknDuration(240)
    val D280 = KpknDuration(280)
    val D320 = KpknDuration(320)
    val D400 = KpknDuration(400)
    val D460 = KpknDuration(460)
    val D520 = KpknDuration(520)
    val D600 = KpknDuration(600)
    val D700 = KpknDuration(700)
    val D800 = KpknDuration(800)
    val D1000 = KpknDuration(1000)
    val D1200 = KpknDuration(1200)
    val D1300 = KpknDuration(1300)
    val D1400 = KpknDuration(1400)
    val D1600 = KpknDuration(1600)
    val D2400 = KpknDuration(2400)
    val D6000 = KpknDuration(6000)
    val D15000 = KpknDuration(15000)

    // ---- Los 11 retardos (ms) ----
    val R60 = KpknDuration(60)
    val R90 = KpknDuration(90)
    val R120 = KpknDuration(120)
    val R160 = KpknDuration(160)
    val R240 = KpknDuration(240)
    val R600 = KpknDuration(600)
    val R700 = KpknDuration(700)
    val R1000 = KpknDuration(1000)
    val R1250 = KpknDuration(1250)
    val R1300 = KpknDuration(1300)
    val R1600 = KpknDuration(1600)

    /** Las 14 curvas con su nombre, texto CSS, descripción y las duraciones con las que más se usan, en el orden del catálogo. */
    val curves: List<KpknCurve> = listOf(
        KpknCurve("estandar", "cubic-bezier(0.2, 0.8, 0.2, 1)", Estandar, "La de casi todo: sale rápido y se asienta despacio.", listOf(200, 240)),
        KpknCurve("suave", "ease", Suave, "Estándar del navegador para color, fondo y opacidad.", listOf(200, 160)),
        KpknCurve("lineal", "linear", Lineal, "Barras de progreso, giros continuos y barridos.", listOf(6000, 800)),
        KpknCurve("trazo", "cubic-bezier(0.3, 0.7, 0.2, 1)", Trazo, "Dibujo de líneas y anillos (stroke-dashoffset).", listOf(1600, 1400)),
        KpknCurve("salida", "ease-out", Salida, "Pulsaciones y retornos cortos.", listOf(1000, 200)),
        KpknCurve("resorte", "cubic-bezier(0.3, 1.5, 0.5, 1)", Resorte, "Muelle del editor: rebasa un 15 % y vuelve.", listOf(320, 160)),
        KpknCurve("vaiven", "ease-in-out", Vaiven, "Giros y trazos que van y vienen.", listOf(2400, 700)),
        KpknCurve("parpadeo", "steps(1)", Parpadeo, "Parpadeo del cursor (todo o nada).", listOf(1000)),
        KpknCurve("cae", "cubic-bezier(0.5, 0, 0.9, 0.5)", Cae, "Salidas del editor: arranca lento y acelera.", listOf(200, 240)),
        KpknCurve("acelera", "cubic-bezier(0.4, 0, 1, 1)", Acelera, "Cierre de la hoja de la ruta y del velo.", listOf(240, 200)),
        KpknCurve("material", "cubic-bezier(0.4, 0, 0.2, 1)", Material, "Estándar de Material (entrada y salida de los fondos).", listOf(320)),
        KpknCurve("resorte-fuerte", "cubic-bezier(0.3, 1.6, 0.5, 1)", ResorteFuerte, "Muelle de suplementos: rebasa un 20 %.", listOf(600, 700)),
        KpknCurve("ruta", "cubic-bezier(0.5, 0, 0.2, 1)", Ruta, "Trazado de la ruta del cardio.", listOf(1000)),
        KpknCurve("resorte-suave", "cubic-bezier(0.22, 1.12, 0.36, 1)", ResorteSuave, "Apertura de la hoja de la ruta: rebasa un 5 %.", listOf(400)),
    )

    /** Las 23 duraciones del catálogo (escala redonda y exactas), en milisegundos crecientes. */
    val durations: List<KpknScaleStep> = listOf(
        KpknScaleStep("80", D80, true),
        KpknScaleStep("90", D90, false),
        KpknScaleStep("120", D120, false),
        KpknScaleStep("140", D140, true),
        KpknScaleStep("160", D160, false),
        KpknScaleStep("200", D200, false),
        KpknScaleStep("240", D240, false),
        KpknScaleStep("280", D280, false),
        KpknScaleStep("320", D320, false),
        KpknScaleStep("400", D400, false),
        KpknScaleStep("460", D460, true),
        KpknScaleStep("520", D520, false),
        KpknScaleStep("600", D600, true),
        KpknScaleStep("700", D700, false),
        KpknScaleStep("800", D800, true),
        KpknScaleStep("1000", D1000, false),
        KpknScaleStep("1200", D1200, true),
        KpknScaleStep("1300", D1300, true),
        KpknScaleStep("1400", D1400, true),
        KpknScaleStep("1600", D1600, false),
        KpknScaleStep("2400", D2400, false),
        KpknScaleStep("6000", D6000, true),
        KpknScaleStep("15000", D15000, true),
    )

    /** Los 11 retardos del catálogo. */
    val delays: List<KpknScaleStep> = listOf(
        KpknScaleStep("r60", R60, true),
        KpknScaleStep("r90", R90, false),
        KpknScaleStep("r120", R120, false),
        KpknScaleStep("r160", R160, false),
        KpknScaleStep("r240", R240, false),
        KpknScaleStep("r600", R600, true),
        KpknScaleStep("r700", R700, false),
        KpknScaleStep("r1000", R1000, false),
        KpknScaleStep("r1250", R1250, true),
        KpknScaleStep("r1300", R1300, true),
        KpknScaleStep("r1600", R1600, false),
    )

    /** La curva del catálogo por nombre (`"estandar"`, `"resorte-fuerte"`…). */
    fun curve(name: String): KpknCurve = curves.first { it.name == name }
}

/**
 * Transición de Compose con una duración y una curva del catálogo (equivale a `transition: <prop> <duración> <curva> <retardo>` de CSS).
 *
 * ```
 * val alpha by animateFloatAsState(if (visible) 1f else 0f, kpknTween(KpknMotion.D200, KpknMotion.Suave))
 * ```
 */
fun <T> kpknTween(
    duration: KpknDuration,
    easing: KpknEasing = KpknMotion.Estandar,
    delayMillis: Int = 0,
): TweenSpec<T> = tween(durationMillis = duration.ms, delayMillis = delayMillis, easing = easing)

/** Los tres muelles del lienzo (curvas con rebasado, no `spring()`): [Normal] rebasa 15 %, [Fuerte] 20 % y [Suave] 5 %. */
enum class KpknSpring(val easing: KpknEasing) {
    Normal(KpknMotion.Resorte),
    Fuerte(KpknMotion.ResorteFuerte),
    Suave(KpknMotion.ResorteSuave),
}

/**
 * Equivalente de un «muelle» del lienzo: un `tween` con la curva con rebasado correspondiente.
 *
 * ```
 * val scale by animateFloatAsState(if (hot) 1f else 0.86f, kpknSpringTween(KpknMotion.D320))
 * ```
 */
fun <T> kpknSpringTween(
    duration: KpknDuration,
    spring: KpknSpring = KpknSpring.Normal,
    delayMillis: Int = 0,
): TweenSpec<T> = kpknTween(duration, spring.easing, delayMillis)
