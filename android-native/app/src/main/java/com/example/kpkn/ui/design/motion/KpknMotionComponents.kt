package com.example.kpkn.ui.design.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknEntranceMemory
import java.util.Locale
import kotlin.math.pow

// ------------------------------------------------------------------------------------------------------------------------------
// Reloj y pose
// ------------------------------------------------------------------------------------------------------------------------------

/**
 * Reloj de una animación: milisegundos desde el primer cuadro tras componer (lo mueve el reloj de cuadros de Compose, así que se
 * puede parar en las pruebas con `mainClock.autoAdvance = false`). Para una animación finita se detiene en [endMs]; para un bucle,
 * [loopMs] = periodo y se enrolla pasadas dos vueltas, con el mismo estado de fase, para no perder precisión en sesiones largas.
 *
 * ```
 * val clock = rememberKpknClockMs(endMs = anim.endMs)
 * Modifier.kpknPose(anim, { clock.value })
 * ```
 */
@Composable
fun rememberKpknClockMs(
    running: Boolean = true,
    key: Any? = null,
    endMs: Float = Float.POSITIVE_INFINITY,
    loopStartMs: Float = 0f,
    loopMs: Float = 0f,
): State<Float> {
    val time = remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key, running, endMs, loopMs) {
        if (!running) return@LaunchedEffect
        val start = androidx.compose.runtime.withFrameNanos { it }
        time.floatValue = 0f
        while (time.floatValue < endMs) {
            androidx.compose.runtime.withFrameNanos { now ->
                val elapsed = (now - start) / 1_000_000.0
                val wrapped = if (loopMs > 0f && elapsed >= loopStartMs + 2.0 * loopMs) {
                    loopStartMs + loopMs + ((elapsed - loopStartMs - loopMs) % (2.0 * loopMs))
                } else {
                    elapsed
                }
                time.floatValue = wrapped.toFloat()
            }
        }
    }
    return time
}

/**
 * Valores de una animación leídos del reloj. [pose] se llama dentro de bloques de dibujo (`graphicsLayer`, `drawBehind`, `Canvas`): así
 * cada cuadro invalida solo el dibujo y la pantalla no se recompone.
 */
@Stable
class KpknPoseState internal constructor(
    private val animation: KpknAnimation,
    private val clock: State<Float>,
    private val reducedMotion: Boolean,
) {
    private val scratch = KpknPose()

    /** Estado de la animación en el instante actual (el objeto se reutiliza: léelo y suéltalo en el mismo bloque). */
    fun pose(): KpknPose {
        animation.poseAt(clock.value, scratch, reducedMotion)
        return scratch
    }
}

/**
 * Lee una animación con el reloj de cuadros: devuelve un [KpknPoseState] para dibujar con `drawBehind`/`Canvas` las propiedades que no
 * son de capa (anchos, sombras, color, trazo…). Con movimiento reducido da el estado final.
 *
 * ```
 * val trazo = rememberKpknPose(KpknFamilies.DibujoTrazo.demo)
 * Canvas(Modifier.size(100.dp, 40.dp)) { drawPath(path, color, style = Stroke(3.5f), alpha = 1f - 0f * trazo.pose().alpha) }
 * ```
 */
@Composable
fun rememberKpknPose(animation: KpknAnimation, key: Any? = null): KpknPoseState {
    val reduced = wizardReducedMotion()
    val clock = rememberKpknClockMs(
        running = !reduced,
        key = key ?: animation,
        endMs = animation.endMs,
        loopStartMs = animation.delay.ms.toFloat(),
        loopMs = if (animation.isInfinite) animation.periodMs else 0f,
    )
    return remember(animation, clock, reduced) { KpknPoseState(animation, clock, reduced) }
}

/**
 * Pulso o latido infinito con su periodo exacto (el de la animación del lienzo: `latido` 1100 ms, `pulso` 1000 ms, `pulso-anillo`
 * 1200 ms…). Con movimiento reducido no late (estado base).
 *
 * ```
 * val latido = rememberKpknPulse(KpknFamilies.Latido.demo)
 * Box(Modifier.kpknPulse(latido))        // opacidad y escala del latido, sin recomponer
 * ```
 */
@Composable
fun rememberKpknPulse(animation: KpknAnimation = KpknFamilies.Latido.demo, key: Any? = null): KpknPoseState {
    require(animation.isInfinite) { "rememberKpknPulse pide una animación infinita; para una sola vez usa rememberKpknPose" }
    return rememberKpknPose(animation, key)
}

/** Aplica a una capa (`graphicsLayer`) la opacidad, la traslación, la escala y el giro de un [KpknPoseState]. */
fun Modifier.kpknPulse(state: KpknPoseState, transformOrigin: TransformOrigin = TransformOrigin.Center): Modifier = composed {
    val density = LocalDensity.current.density
    val origin = transformOrigin
    graphicsLayer {
        val pose = state.pose()
        applyPose(pose, density, origin)
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyPose(
    pose: KpknPose,
    density: Float,
    origin: TransformOrigin,
) {
    transformOrigin = origin
    alpha = pose.alpha
    translationX = pose.translateXDp * density + pose.translateXFraction * size.width
    translationY = pose.translateYDp * density + pose.translateYFraction * size.height
    scaleX = pose.scaleX
    scaleY = pose.scaleY
    rotationZ = pose.rotationDeg
}

/**
 * Aplica una animación a una capa en el instante que da [timeMs] (se lee al dibujar). Es la pieza común de [kpknAnimate] y de la hoja de
 * calibración: la misma animación con el reloj de cuadros o con un instante fijado a mano. Orden de la capa: giro y escala sobre el
 * [transformOrigin] y, después, la traslación (como `translate() rotate() scale()` de CSS). [compositingStrategy] = `Auto` agrupa el
 * elemento al bajar su opacidad (como `opacity` de CSS); con `ModulateAlpha` cada trazo se atenúa solo, sin recorte de capa (formas simples).
 *
 * ```
 * var t by remember { mutableFloatStateOf(120f) }
 * Box(Modifier.kpknPose(KpknFamilies.Rebote.demo, { t }))
 * ```
 */
fun Modifier.kpknPose(
    animation: KpknAnimation,
    timeMs: () -> Float,
    reducedMotion: Boolean = false,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    compositingStrategy: CompositingStrategy = CompositingStrategy.Auto,
): Modifier = composed {
    val density = LocalDensity.current.density
    val scratch = remember { KpknPose() }
    val origin = transformOrigin
    val strategy = compositingStrategy
    graphicsLayer {
        animation.poseAt(timeMs(), scratch, reducedMotion)
        applyPose(scratch, density, origin)
        this.compositingStrategy = strategy
    }
}

/**
 * Reproduce una animación del lienzo (semántica de `animation` de CSS: retardo, curva por intervalo, repeticiones, dirección y relleno)
 * sobre un elemento, con el reloj de cuadros. Con movimiento reducido da el estado final. Reinicia al cambiar [restartKey].
 *
 * ```
 * Text("Listo", Modifier.kpknAnimate(KpknFamilies.EntradaSube.demo))
 * Box(Modifier.kpknAnimate(KpknFamilies.Rebote.demo, restartKey = count))
 * Box(Modifier.kpknAnimate(KpknFamilies.CreceX.demo, transformOrigin = TransformOrigin(0f, 0.5f)))
 * ```
 */
fun Modifier.kpknAnimate(
    animation: KpknAnimation,
    enabled: Boolean = true,
    restartKey: Any? = null,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    compositingStrategy: CompositingStrategy = CompositingStrategy.Auto,
): Modifier = composed {
    if (!enabled) {
        Modifier
    } else {
        val reduced = wizardReducedMotion()
        val clock = rememberKpknClockMs(
            running = !reduced,
            key = restartKey ?: animation,
            endMs = animation.endMs,
            loopStartMs = animation.delay.ms.toFloat(),
            loopMs = if (animation.isInfinite) animation.periodMs else 0f,
        )
        Modifier.kpknPose(animation, { clock.value }, reduced, transformOrigin, compositingStrategy)
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Transiciones (`transition`: del valor actual al nuevo, con duración, curva y retardo)
// ------------------------------------------------------------------------------------------------------------------------------

/**
 * `transition: <propiedad> <duración> <curva> <retardo>` para un número: al cambiar [target] sigue desde el valor que tenga ahora. Con
 * movimiento reducido salta al valor nuevo.
 *
 * ```
 * val alpha by animateKpknFloatAsState(if (visible) 1f else 0f, KpknMotion.D200, KpknMotion.Suave)
 * ```
 */
@Composable
fun animateKpknFloatAsState(
    target: Float,
    duration: KpknDuration = KpknMotion.D200,
    easing: KpknEasing = KpknMotion.Suave,
    delayMillis: Int = 0,
    label: String = "kpknFloat",
): State<Float> {
    val reduced = wizardReducedMotion()
    return animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap<Float>() else kpknTween<Float>(duration, easing, delayMillis),
        label = label,
    )
}

/**
 * `transition` para un color: se mezcla como CSS (sRGB con alfa premultiplicado). Al cambiar [target] sigue desde el color que se vea ahora.
 *
 * ```
 * val fondo by animateKpknColorAsState(if (on) Color(0xFFF2EEE6) else Color(0x14FFFFFF), KpknMotion.D160)
 * ```
 */
@Composable
fun animateKpknColorAsState(
    target: Color,
    duration: KpknDuration = KpknMotion.D200,
    easing: KpknEasing = KpknMotion.Suave,
    delayMillis: Int = 0,
): State<Color> {
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(1f) }
    val from = remember { mutableStateOf(target) }
    val to = remember { mutableStateOf(target) }
    val shown = remember { mutableStateOf(target) }
    LaunchedEffect(target, reduced) {
        if (target == to.value) return@LaunchedEffect
        from.value = shown.value
        to.value = target
        if (reduced) {
            progress.snapTo(1f)
            shown.value = target
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, kpknTween<Float>(duration, easing, delayMillis)) {
                shown.value = kpknCssColorLerp(from.value, to.value, value)
            }
            shown.value = to.value
        }
    }
    return shown
}

// ------------------------------------------------------------------------------------------------------------------------------
// Entradas y salidas estándar (AnimatedVisibility)
// ------------------------------------------------------------------------------------------------------------------------------

/** Entradas estándar del lienzo, con el desplazamiento y la duración exactos de su familia de `@keyframes`. */
enum class KpknEnterKind(val familyName: String) {
    /** `entrada-sube`: 14 dp hacia arriba, 420 ms, `estandar`. */
    Sube("entrada-sube"),

    /** `entrada-baja`: 8 dp hacia abajo, 240 ms. */
    Baja("entrada-baja"),

    /** `entrada-derecha`: 10 dp desde la derecha, 220 ms. */
    Derecha("entrada-derecha"),

    /** `entrada-izquierda`: 24 dp desde la izquierda, 300 ms. */
    Izquierda("entrada-izquierda"),

    /** `fundido-entrada`: solo opacidad, 200 ms, `suave`. */
    Fundido("fundido-entrada"),

    /** `hoja-sube`: desde el borde inferior (100 % del alto), 420 ms. */
    Hoja("hoja-sube"),
}

/** Salidas estándar del lienzo. */
enum class KpknExitKind(val familyName: String) {
    /** `fundido-salida`: opacidad a 0 en 200 ms, `suave`. */
    Fundido("fundido-salida"),

    /** `hoja-baja`: baja el 100 % del alto en 240 ms con la curva `cae`. */
    Hoja("hoja-baja"),
}

/**
 * Entrada estándar para `AnimatedVisibility`/`AnimatedContent`: fundido + el desplazamiento de la familia del lienzo, con su curva.
 *
 * ```
 * AnimatedVisibility(visible, enter = kpknEnter(), exit = kpknExit()) { Tarjeta() }
 * AnimatedVisibility(visible, enter = kpknEnter(KpknEnterKind.Hoja)) { Hoja() }
 * ```
 */
@Composable
fun kpknEnter(kind: KpknEnterKind = KpknEnterKind.Sube, duration: KpknDuration? = null, delayMillis: Int = 0): EnterTransition {
    val density = LocalDensity.current
    val family = KpknFamilies.byName(kind.familyName)
    val ms = duration ?: family.demo.duration
    val easing = family.demo.easing
    val fade = fadeIn(kpknTween(ms, easing, delayMillis), initialAlpha = 0f)
    return when (kind) {
        KpknEnterKind.Sube -> fade + slideInVertically(kpknTween(ms, easing, delayMillis)) { with(density) { 14.dp.roundToPx() } }
        KpknEnterKind.Baja -> fade + slideInVertically(kpknTween(ms, easing, delayMillis)) { with(density) { (-8).dp.roundToPx() } }
        KpknEnterKind.Derecha -> fade + slideInHorizontally(kpknTween(ms, easing, delayMillis)) { with(density) { 10.dp.roundToPx() } }
        KpknEnterKind.Izquierda -> fade + slideInHorizontally(kpknTween(ms, easing, delayMillis)) { with(density) { (-24).dp.roundToPx() } }
        KpknEnterKind.Fundido -> fade
        KpknEnterKind.Hoja -> slideInVertically(kpknTween(ms, easing, delayMillis)) { it }
    }
}

/**
 * Salida estándar: fundido de 200 ms o bajada de la hoja (`cae`, 240 ms).
 *
 * ```
 * AnimatedVisibility(visible, exit = kpknExit(KpknExitKind.Fundido)) { Aviso() }
 * ```
 */
@Composable
fun kpknExit(kind: KpknExitKind = KpknExitKind.Fundido, duration: KpknDuration? = null, delayMillis: Int = 0): ExitTransition {
    val family = KpknFamilies.byName(kind.familyName)
    val ms = duration ?: family.demo.duration
    val easing = family.demo.easing
    return when (kind) {
        KpknExitKind.Fundido -> fadeOut(kpknTween(ms, easing, delayMillis))
        KpknExitKind.Hoja -> slideOutVertically(kpknTween(ms, easing, delayMillis)) { it }
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Pulsación
// ------------------------------------------------------------------------------------------------------------------------------

/** Pulsaciones del lienzo: escala y fondo al apretar (`:active`) y duración de entrada y de vuelta. */
enum class KpknPressStyle(val scale: Float, val pressIn: KpknDuration, val release: KpknDuration, val easing: KpknEasing) {
    /** Botón principal (`.lv2-rec`): 0,97 en 90 ms al apretar, vuelve en 160 ms con `estandar`. */
    Primary(0.97f, KpknMotion.D90, KpknMotion.D160, KpknMotion.Estandar),

    /** Botón de vidrio o píldora (`.sp-take`, `.sp-hq`): 0,96 en 160 ms de ida y vuelta con la curva por defecto (`ease`). */
    Soft(0.96f, KpknMotion.D160, KpknMotion.D160, KpknMotion.Suave),
}

/**
 * Escala (y opacidad) de pulsación, igual que `:active { transform: scale(.97) }` con su `transition`: la duración de entrada es la
 * de la regla `:active` y la de vuelta la del estado normal. Se anima en la capa: no recompone.
 *
 * ```
 * val source = remember { MutableInteractionSource() }
 * Box(Modifier.kpknPress(source).clickable(source, null) { onTap() })
 * ```
 */
fun Modifier.kpknPress(
    interactionSource: InteractionSource,
    style: KpknPressStyle = KpknPressStyle.Primary,
    pressedScale: Float = style.scale,
    pressedAlpha: Float = 1f,
): Modifier = composed {
    val reduced = wizardReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val progress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reduced) snap<Float>() else kpknTween<Float>(if (pressed) style.pressIn else style.release, style.easing),
        label = "kpknPress",
    )
    graphicsLayer {
        val p = progress.value
        val s = 1f + (pressedScale - 1f) * p
        scaleX = s
        scaleY = s
        alpha = 1f + (pressedAlpha - 1f) * p
    }
}

// ------------------------------------------------------------------------------------------------------------------------------
// Cuenta de cifras
// ------------------------------------------------------------------------------------------------------------------------------

/** Duración de la cuenta del contador `[data-count]` del lienzo. */
const val KPKN_COUNT_UP_MS: Int = 1100

/** Valor de la cuenta tras [elapsedMs]: `objetivo × (1 − (1 − k)³)` con `k = elapsed / 1100` (el `animateIn` del lienzo). */
fun kpknCountValue(target: Double, elapsedMs: Float, durationMs: Int = KPKN_COUNT_UP_MS): Double {
    val k = if (durationMs <= 0) 1.0 else (elapsedMs / durationMs.toDouble()).coerceIn(0.0, 1.0)
    return target * (1.0 - (1.0 - k).pow(3))
}

/** Formato del lienzo: `toFixed(dec)` con coma decimal y punto de millar (`18.420`, `82,5`). */
fun kpknFormatCount(value: Double, decimals: Int = 0): String {
    val fixed = String.format(Locale.ROOT, "%.${decimals}f", value)
    val dot = fixed.indexOf('.')
    val integer = if (dot >= 0) fixed.substring(0, dot) else fixed
    val fraction = if (dot >= 0) fixed.substring(dot + 1) else ""
    val negative = integer.startsWith("-")
    val digits = if (negative) integer.substring(1) else integer
    val grouped = StringBuilder()
    digits.forEachIndexed { index, ch ->
        if (index > 0 && (digits.length - index) % 3 == 0) grouped.append('.')
        grouped.append(ch)
    }
    return (if (negative) "-" else "") + grouped + (if (fraction.isNotEmpty()) ",$fraction" else "")
}

/**
 * Cifra que cuenta de 0 a [target] en 1100 ms con salida cúbica `1 − (1 − t)³`, como el `[data-count]` del lienzo. Con [once] la cuenta
 * solo ocurre la primera vez que el proceso ve esa clave; con movimiento reducido muestra el valor final. [timeMs] fija el instante a mano
 * (hojas de calibración y pruebas).
 *
 * ```
 * KpknCountUp(1247.0, style = KpknType.metricXL)                           // 1.247
 * KpknCountUp(82.5, decimals = 1, suffix = " kg", once = "peso")           // 82,5 kg
 * ```
 */
@Composable
fun KpknCountUp(
    target: Double,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String = "",
    style: TextStyle = TextStyle.Default,
    once: String? = null,
    timeMs: (() -> Float)? = null,
) {
    val reduced = wizardReducedMotion()
    val counting = remember(once) { once?.let { KpknEntranceMemory.firstTime("cuenta/$it", 0) } ?: true } && !reduced
    val clock = rememberKpknClockMs(running = counting && timeMs == null, key = target, endMs = KPKN_COUNT_UP_MS.toFloat())
    val elapsed = when {
        !counting -> KPKN_COUNT_UP_MS.toFloat()
        timeMs != null -> timeMs()
        else -> clock.value
    }
    BasicText(
        text = kpknFormatCount(kpknCountValue(target, elapsed), decimals) + suffix,
        modifier = modifier,
        style = style,
    )
}
