package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknSheetSpec
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknCssColorLerp
import com.example.kpkn.ui.design.motion.kpknPose
import com.example.kpkn.ui.design.motion.kpknKeyframes
import com.example.kpkn.ui.design.motion.KpknPose
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/*
 * MOVIMIENTO del asistente de sesión (pulido, paquete `as`, pasada B/C). Todo sale de `ui/design/motion`: las familias del catálogo con la
 * duración EXACTA de la fila del lienzo (`paridad/as.md`), las curvas del catálogo (`ease` = `suave`, `var(--as-ease)` = `estandar`) y los
 * fotogramas de `KpknFamilies`. Aquí solo hay nombres de fila y milisegundos; ninguna curva ni fotograma suelto en las pantallas.
 */
internal object AsAnim {
    /** `ease` de CSS (el valor por defecto de `transition`/`animation`). */
    val Ease: Easing = KpknMotion.Suave

    /** `var(--as-ease)` = cubic-bezier(.2,.8,.2,1). */
    val Std: Easing = KpknMotion.Estandar

    /**
     * `@keyframes asIn` evaluado con `--dx:0` (todas las filas del asistente salvo `.as-pane.on`, que usa el desplazamiento por defecto de
     * 10 px): solo el fundido, sin desplazamiento horizontal.
     */
    private val asInDx0 = kpknKeyframes(
        "asIn",
        "@keyframes asIn { 0% { opacity: 0; transform: translateX(var(--dx,10px)); } 100% { opacity: 1; transform: none; } } con --dx:0",
    ) {
        at(0f) { alpha(0f); translateX(0f) }
        at(1f) { alpha(1f); transformNone() }
    }

    private fun inDx0(ms: Int) = KpknAnimation(asInDx0, KpknDuration(ms), KpknMotion.Estandar, fill = KpknFill.Both)

    /** as-A06 `.as-fold.open .as-fb2` · 0,22 s. */
    val FoldBodyIn = inDx0(220)

    /** as-A07 `.as-fp.open` · 0,22 s. */
    val FilterPanelIn = inDx0(220)

    /** as-A09 `.as-menu.open` · 0,18 s. */
    val MenuIn = inDx0(180)

    /** as-A10 `.as-mrow.open .as-mdet` · 0,22 s. */
    val MuscleDetailIn = inDx0(220)

    /** as-A16 `.as-root[data-mall="1"] .as-un` · 0,22 s. */
    val UnworkedIn = inDx0(220)

    /** as-A17 `.as-sgw.applied .as-done.ap` · 0,26 s. */
    val FoldedOutcomeIn = inDx0(260)

    /** as-A01 `.as-ap.done .after` · 0,3 s. */
    val ApplyAfterIn = inDx0(300)

    /** as-A11 `.as-pane.on` · asIn 0,26 s con el desplazamiento por defecto (10 px desde la derecha). */
    val PaneIn = KpknFamilies.EntradaDerecha.demo.copy(duration = KpknDuration(260))

    /** as-A02 `.as-ap.open` y as-A04 `.as-dec.open` · asScrim 0,2 s ease. */
    val OverlayFade = KpknFamilies.FundidoEntrada.demo.copy(duration = KpknDuration(200))

    /** as-A03 `.as-dec > div` · asRise 0,36 s. */
    val DecisionRise = KpknFamilies.HojaSube.demo.copy(duration = KpknDuration(360))

    /** as-A19 `.as-toast.open` · asRise 0,3 s. */
    val ToastRise = KpknFamilies.HojaSube.demo.copy(duration = KpknDuration(300))

    /** as-A08 `.as-mbar i` · asGrow 0,7 s desde la izquierda (`transform-origin:left`), relleno `both`. */
    val BarGrow = KpknFamilies.CreceX.demo.copy(duration = KpknDuration(700), easing = KpknMotion.Estandar, fill = KpknFill.Both)

    /** as-A12 `.as-rg .sp` · asSpin 1,1 s lineal, infinito. */
    val Spin = KpknFamilies.Giro.demo.copy(duration = KpknDuration(1100))

    /** as-A18 `.as-sk-l i` · asSh 1,4 s lineal, infinito (la familia es exactamente esa). */
    val Shimmer = KpknFamilies.BrilloBarrido.demo

    /** as-A05 `.as-flash` · asFlash 0,6 s ease, sin relleno (la familia `destello`). */
    val Flash = KpknFamilies.Destello.demo

    /** `--mus`: color de músculo del lienzo (el punto de partida del destello). */
    val Mus = Color(0xFFF49A6E)

    /** Origen de las barras que crecen (`transform-origin:left`). */
    val Left = TransformOrigin(0f, 0.5f)
}

/**
 * Hoja del asistente (`KpknSheet`): sombra as-S05 y tiempos del lienzo: velo as-A14/as-T09 (0,22 s ease), hoja as-A15 (0,42 s), contenido as-A13
 * (0,3 s con 0,16 s de retardo, 10 px), altura as-T13 (0,32 s), cierre as-X06 (baja el 102 % en 0,42 s).
 */
internal val AsSheetSpec = KpknSheetSpec(
    shadow = KpknShadow.Hoja.spec,
    scrimInMs = 220,
    scrimEasing = AsAnim.Ease,
    sheetInMs = 420,
    sheetEasing = AsAnim.Std,
    contentDelayMs = 160,
    contentInMs = 300,
    contentEasing = AsAnim.Std,
    contentRise = 10.dp,
    outMs = 420,
    outEasing = AsAnim.Std,
    outTravel = 1.02f,
    scrimOutMs = 220,
    heightMs = 320,
    heightEasing = AsAnim.Std,
)

/**
 * Instante fijo (ms desde el inicio) para las hojas de pruebas: con un valor, TODAS las animaciones del asistente se dibujan en ese instante
 * (reloj parado) en lugar de seguir el reloj de cuadros. En la app es siempre null.
 */
internal val LocalAsFrozenMs = androidx.compose.runtime.staticCompositionLocalOf<Float?> { null }

/** Reproduce [animation] cuando el elemento entra en la composición (o cuando cambia [key]); con movimiento reducido, el estado final. */
internal fun Modifier.asEnter(animation: KpknAnimation, origin: TransformOrigin = TransformOrigin.Center, key: Any? = null): Modifier = composed {
    val frozen = LocalAsFrozenMs.current
    if (frozen != null) Modifier.kpknPose(animation, { frozen }, false, origin) else Modifier.kpknAnimate(animation, restartKey = key, transformOrigin = origin)
}

/** `transition: background/color/border-color <ms> ease`: el color sigue al nuevo valor con la curva `ease`. */
@Composable
internal fun asColorState(target: Color, ms: Int = 200, easing: Easing = AsAnim.Ease): State<Color> =
    animateKpknColorAsState(target, KpknDuration(ms), easing)

/** `transition: transform/opacity/width <ms> <curva>` para un número. */
@Composable
internal fun asFloatState(target: Float, ms: Int, easing: Easing = AsAnim.Std): State<Float> =
    animateKpknFloatAsState(target, KpknDuration(ms), easing)

/** Giro de un chevron a [degrees] leído al dibujar (no recompone en cada cuadro). */
internal fun Modifier.asRotation(degrees: State<Float>): Modifier = graphicsLayer { rotationZ = degrees.value }

/**
 * as-A05 `.as-flash` (asFlash 0,6 s ease): cuando [value] cambia, el color parte del de músculo (`--mus`) y vuelve a [base]. El primer valor no
 * parpadea (el lienzo solo añade la clase al cambiar). Con movimiento reducido, siempre [base].
 */
@Composable
internal fun asFlashColor(value: Any?, base: Color): Color {
    var flashes by remember { mutableIntStateOf(0) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(value) {
        if (first[0]) first[0] = false else flashes += 1
    }
    val frozen = LocalAsFrozenMs.current
    val reduced = wizardReducedMotion()
    val clock = rememberKpknClockMs(running = flashes > 0 && !reduced, key = flashes, endMs = AsAnim.Flash.endMs)
    val scratch = remember { KpknPose() }
    if (frozen != null) {
        AsAnim.Flash.poseAt(frozen, scratch)
        return if (scratch.active) kpknCssColorLerp(AsAnim.Mus, base, scratch[KpknChannel.ColorMix]) else base
    }
    if (flashes == 0 || reduced) return base
    AsAnim.Flash.poseAt(clock.value, scratch)
    return if (scratch.active) kpknCssColorLerp(AsAnim.Mus, base, scratch[KpknChannel.ColorMix]) else base
}

/**
 * Máscara de degradado del lienzo (`mask-image`): un `DstIn` sobre una capa propia. [bottomFade] = `linear-gradient(#000 calc(100% - Npx), transparent)`
 * (as-X07 `.as-sc`: 26). El catálogo de profundidad no tiene máscaras (no son fondos), así que esta es la única definición del asistente.
 */
internal fun Modifier.asBottomMask(bottomFade: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = bottomFade.toPx().coerceAtMost(size.height)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
            size = androidx.compose.ui.geometry.Size(size.width, fade),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

/**
 * as-X02 `.as-chips`: `linear-gradient(90deg, transparent, #000 [startFade], #000 calc(100% - [endFade]), transparent)` — la fila de chips se funde en
 * sus dos orillas (20 y 28). Va ANTES de `horizontalScroll`, como la del editor: el fundido cae sobre la ventana, no sobre el contenido entero.
 */
internal fun Modifier.asEdgeMask(startFade: androidx.compose.ui.unit.Dp, endFade: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val s = startFade.toPx().coerceAtMost(size.width / 2f)
        val e = endFade.toPx().coerceAtMost(size.width / 2f)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = s),
            size = androidx.compose.ui.geometry.Size(s, size.height),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - e, endX = size.width),
            topLeft = androidx.compose.ui.geometry.Offset(size.width - e, 0f),
            size = androidx.compose.ui.geometry.Size(e, size.height),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
