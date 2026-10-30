package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.workout.RepsCarouselMath
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.drawKpknGradient
import com.example.kpkn.ui.design.depth.drawKpknShadow
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknPose
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknKeyframes
import com.example.kpkn.ui.design.motion.kpknTween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val CarouselInk = Color(0xFFF2EEE6)
private val CarouselDark = Color(0xFF111111)
private val CarouselMute = Color(0xFF8E8980)
private val CarouselFailHot = Color(0xFFFF9B92)
private val CarouselPeach = Color(0xFFF49A6E)
private val RailGradient = KpknGradient.RellenoHorizontalCrema2.spec()

/** `@keyframes lv-snap` (rebote del bloque central al encajar): 0,93 → 1,045 al 55 % → 1, 240 ms con la curva `estandar`. pulido:lv-A03 */
private val LensSnap = KpknAnimation(
    keyframes = kpknKeyframes("lv-snap", "@keyframes lv-snap { 0% { transform: scale(0.93); } 55% { transform: scale(1.045); } 100% { transform: scale(1); } }") {
        at(0f) { scale(0.93f) }
        at(0.55f) { scale(1.045f) }
        at(1f) { scale(1f) }
    },
    duration = KpknMotion.D240,
    easing = KpknMotion.Estandar,
)

/** `cubic-bezier(.33,1,.68,1)` = `1 - (1 - t)^3`: la curva del deslizamiento del carrusel del lienzo (`lvCarousel`). */
private val CarouselGlide = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * Pulido v1 · 13 · el carrusel de repeticiones de la tarjeta (`.lv-car` del lienzo). Una sola banda de 46 dp con las cifras en Inter
 * (lining + tabulares: todas a la misma altura), el bloque crema de 56 × 41 en el centro y los vecinos cada vez más pequeños y tenues.
 * El valor se queda siempre en el centro (imán al soltar), tocar un vecino lo centra y cada cifra que se cruza vibra.
 * El rango del plan es un riel de 3 dp bajo las cifras (las de dentro en crema y las de fuera atenuadas); un plan fijo es una marca corta
 * y un AMRAP, un riel que sale del mínimo hacia la derecha.
 *
 * Se dibuja en un solo lienzo (sin una composición por cifra): [value] es el valor externo y [onValueChange] se llama con cada cifra que
 * pasa por el centro, también durante el arrastre.
 */
@Composable
internal fun RepsCarousel(
    value: Int,
    onValueChange: (Int) -> Unit,
    planMin: Int?,
    planMax: Int?,
    amrap: Boolean,
    failed: Boolean,
    lastValue: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    belowPlanColor: Color = CarouselPeach,
    hapticsEnabled: Boolean = true,
    reducedMotion: Boolean = false,
) {
    val last = lastValue.coerceAtLeast(1)
    val shown = if (failed) 0 else value.coerceIn(0, last)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val fontScale = density.fontScale
    // Con letra grande la cifra se capa a 1,15× (el bloque central no crece): sp que dan esos píxeles.
    val digitSp = 32f * min(fontScale, RepsCarouselMath.MAX_FONT_SCALE) / fontScale
    val digitStyle = remember(digitSp) {
        TextStyle(
            fontFamily = WizardFonts.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = digitSp.sp,
            lineHeight = digitSp.sp,
            letterSpacing = (-0.5).sp,
            fontFeatureSettings = "'tnum' 1, 'lnum' 1",
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
    val layouts = remember(digitStyle, measurer) { HashMap<Int, TextLayoutResult>() }

    var pos by remember { mutableFloatStateOf(shown.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var animToken by remember { mutableIntStateOf(0) }
    var animating by remember { mutableStateOf(false) }
    var glideJob by remember { mutableStateOf<Job?>(null) }
    val latestChange by rememberUpdatedState(onValueChange)
    val latestLast by rememberUpdatedState(last)
    val cellPx = with(density) { RepsCarouselMath.CELL_DP.dp.toPx() }
    // Último valor que este carrusel entregó (o recibió) del exterior: lo que está en pantalla y lo que sabe el resto de la tarjeta.
    var lastEmitted by remember { mutableIntStateOf(shown) }
    // Escala del bloque central: el tic de cada cifra (0,965 durante 90 ms, `lv-car-lens.tk`) y el rebote al encajar (`lv-snap`).
    val tick = remember { Animatable(1f) }
    var snapScale by remember { mutableFloatStateOf(1f) }
    var snapKey by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val railAlpha by animateKpknFloatAsState(if (planMin != null && !failed) 1f else 0f, KpknMotion.D200, KpknMotion.Suave)

    // pulido:lv-J04 lv-T01 lv-X04 (cada cifra que se cruza: tic de escala del bloque central, 90 ms)
    /** Mueve el carrusel por un gesto de la persona: cada cifra nueva que pasa por el centro es un valor y una vibración corta. */
    fun setPos(next: Float) {
        pos = next
        val v = next.roundToInt().coerceIn(0, latestLast)
        if (v != lastEmitted) {
            lastEmitted = v
            latestChange(v)
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            // pulido:lv-T01 lv-X04 lv-J04: `transition: transform .09s ease` + clase `tk` 90 ms
            if (!reducedMotion) scope.launch {
                tick.animateTo(.965f, kpknTween<Float>(KpknMotion.D90, KpknMotion.Suave))
                tick.animateTo(1f, kpknTween<Float>(KpknMotion.D90, KpknMotion.Suave))
            }
        }
    }

    // pulido:lv-J05 lv-J06 (el deslizamiento es `1 - (1 - t)^3`; en el lienzo, por rAF)
    fun glide(target: Int, durationMs: Int) {
        glideJob?.cancel()
        val to = target.coerceIn(0, latestLast).toFloat()
        if (reducedMotion || durationMs <= 0 || abs(to - pos) < 0.001f) {
            animating = false
            setPos(to)
            snapKey++
            return
        }
        val token = ++animToken
        animating = true
        glideJob = scope.launch {
            try {
                animate(pos, to, animationSpec = tween(durationMs, easing = CarouselGlide)) { v, _ -> setPos(v) }
                setPos(to)
                snapKey++
            } finally {
                if (token == animToken) animating = false
            }
        }
    }

    // Un cambio venido de fuera (voz, vídeo, la hoja de detalle, «Serie fallida») recoloca el carrusel sin vibrar ni avisar.
    LaunchedEffect(shown, animating, dragging) {
        if (!dragging && !animating && lastEmitted != shown) {
            lastEmitted = shown
            pos = shown.toFloat()
        }
    }

    LaunchedEffect(snapKey) {
        if (snapKey == 0 || reducedMotion) return@LaunchedEffect
        val pose = KpknPose()
        animate(0f, LensSnap.periodMs, animationSpec = tween(LensSnap.duration.ms, easing = LinearEasing)) { t, _ ->
            LensSnap.poseAt(t, pose)
            snapScale = pose.scaleX
        }
        snapScale = 1f
    }

    val range = if (failed) null else planMin
    val gestures = if (enabled && !failed) {
        Modifier
            .pointerInput(last, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    glideJob?.cancel()
                    animating = false
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val startPos = pos
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop != null) {
                        dragging = true
                        horizontalDrag(slop.id) { change ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val raw = startPos - (change.position.x - down.position.x) / cellPx
                            // Más allá del primer y del último valor el carrusel se resiste (35 %).
                            setPos(
                                when {
                                    raw < 0f -> raw * .35f
                                    raw > last -> last + (raw - last) * .35f
                                    else -> raw
                                },
                            )
                        }
                        dragging = false
                        val velocity = -tracker.calculateVelocity().x / cellPx // cifras por segundo
                        val projected = (pos + (velocity * .23f).coerceIn(-6f, 6f)).roundToInt().coerceIn(0, last)
                        glide(projected, (180 + 90 * abs(projected - pos)).roundToInt().coerceIn(200, 520))
                    }
                }
            }
            .pointerInput(last, cellPx) {
                detectTapGestures(onTap = { at -> glide((pos + (at.x - size.width / 2f) / cellPx).roundToInt(), 200) })
            }
    } else Modifier

    // La rueda horizontal del ratón (`wheel` del lienzo): mueve el valor y, 120 ms después de parar, lo centra. pulido:lv-J07
    val wheel = if (enabled && !failed) Modifier.pointerInput(last, cellPx) {
        var settle: Job? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                if (kotlin.math.abs(delta.x) <= kotlin.math.abs(delta.y)) continue
                event.changes.forEach { it.consume() }
                glideJob?.cancel()
                animating = false
                setPos((pos + delta.x * .93f).coerceIn(0f, last.toFloat()))
                settle?.cancel()
                settle = scope.launch { delay(120); glide(pos.roundToInt(), 160) }
            }
        }
    } else Modifier

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled && !failed)
            // pulido:lv-S05 lv-J07 (anillo de foco; la rueda horizontal solo existe con ratón: sin equivalente táctil)
            .testTag("live-card-reps")
            .semantics {
                contentDescription = "Repeticiones"
                stateDescription = "$shown repeticiones"
                progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..last.toFloat(), (last - 1).coerceAtLeast(0))
                if (enabled && !failed) setProgress { target -> glide(target.roundToInt(), 160); true }
            }
            .then(gestures)
            .then(wheel),
    ) {
    val widthPx = with(density) { maxWidth.toPx() }
    Canvas(
        Modifier
            .matchParentSize()
            // Los bordes se funden (máscara de alfa 0 → 15 % → 85 % → 0): la cifra parcial se desvanece en vez de cortarse.
            // pulido:lv-X01
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent, .15f to Color.Black, .85f to Color.Black, 1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val dp = this.density
        val c = size.width / 2f
        // Línea base (`.lv-car-base`: top 44, 1 dp, blanco al 6 %).
        drawRect(Color(0x10FFFFFF), Offset(0f, 44f * dp), Size(size.width, 1f * dp))
        // Riel del rango del plan (`.lv-car-rail`: top 43, 3 dp, `relleno-horizontal-crema-2`: crema al 75 % → 100 %; opacidad .2 s `suave`).
        // pulido:lv-G01 lv-T02 lv-J03 lv-X05
        if (range != null && railAlpha > 0f) {
            val span = RepsCarouselMath.railSpan(range, planMax, amrap, pos, last)
            val x0 = c + span.startDp * dp
            val w = span.widthDp * dp
            val r = 2f * dp
            val path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        x0, 43f * dp, x0 + w, 46f * dp,
                        topLeftCornerRadius = CornerRadius(r), bottomLeftCornerRadius = CornerRadius(r),
                        topRightCornerRadius = if (amrap) CornerRadius.Zero else CornerRadius(r),
                        bottomRightCornerRadius = if (amrap) CornerRadius.Zero else CornerRadius(r),
                    ),
                )
            }
            clipPath(path) { drawKpknGradient(RailGradient, Offset(x0, 43f * dp), Size(w, 3f * dp), alpha = railAlpha) }
        }
        // El bloque central: 56 × 41 a 1 dp del borde superior, radio 15, con el tic de cada cifra y el rebote al encajar.
        // pulido:lv-A03 lv-T01 lv-X04 lv-S02 lv-S03 lv-S04
        val lensTopLeft = Offset(c - 28f * dp, 1f * dp)
        val lensSize = Size(56f * dp, 41f * dp)
        val lensRadius = CornerRadius(15f * dp)
        val lensShape = RoundedCornerShape(15.dp)
        val below = RepsCarouselMath.belowPlan(shown, planMin, amrap, failed)
        val lensScale = tick.value * snapScale
        withTransform({ scale(lensScale, lensScale, pivot = lensTopLeft + Offset(lensSize.width / 2f, lensSize.height / 2f)) }) {
            if (failed) {
                drawRoundRect(Color(0x26FF9B92), lensTopLeft, lensSize, lensRadius)
                drawKpknShadow(KpknShadow.InteriorPeligro1.spec, lensShape, lensTopLeft, lensSize)
            } else {
                // Por debajo del plan, `0 2px 0` en el acento de la sesión (`caida-6-16-capas-2` con ese color).
                val spec = if (below) KpknShadow.Caida616Capas2.spec.copyLayer(2) { it.copy(color = belowPlanColor) } else KpknShadow.Caida616Capas1.spec
                drawKpknShadow(spec, lensShape, lensTopLeft, lensSize)
                drawRoundRect(CarouselInk, lensTopLeft, lensSize, lensRadius)
            }
        }
        // Anillo de foco (`.lv-car:focus-visible`: `0 0 0 2px #f2eee655`, radio 14). pulido:lv-S05
        if (focused) drawKpknShadow(KpknShadow.AnilloFoco.spec, RoundedCornerShape(14.dp), Offset.Zero, size)
        // pulido:lv-J01 lv-J02 lv-X02 lv-X03 (traslación, escala, opacidad y color de cada cifra; `will-change` no tiene equivalente: el dibujo ya es una capa)
        // Las cifras: Inter, lining + tabulares, a la altura del centro del bloque (21 dp).
        val cy = 21f * dp
        val firstIndex = (pos - RepsCarouselMath.VISIBLE_DISTANCE).toInt().coerceAtLeast(0)
        val lastIndex = (pos + RepsCarouselMath.VISIBLE_DISTANCE).toInt().coerceAtMost(last)
        for (i in firstIndex..lastIndex) {
            val distance = i - pos
            val ad = abs(distance)
            if (ad > RepsCarouselMath.VISIBLE_DISTANCE) continue
            val inRange = RepsCarouselMath.isInRange(i, range, if (amrap) null else planMax)
            val dimmed = range != null && !inRange && !failed
            var alpha = RepsCarouselMath.alpha(ad)
            if (dimmed) alpha = RepsCarouselMath.outOfRangeAlpha(ad, alpha)
            val color = when {
                failed -> if (ad < .5f) CarouselFailHot else CarouselMute
                ad < .62f -> lerp(CarouselDark, CarouselInk, RepsCarouselMath.lensBlend(ad))
                inRange || range == null -> CarouselInk
                else -> CarouselMute
            }
            val sizeFactor = RepsCarouselMath.scale(ad)
            val layout = layouts.getOrPut(i) { measurer.measure(i.toString(), digitStyle) }
            val x = c + distance * cellPx
            withTransform({ scale(sizeFactor, sizeFactor, pivot = Offset(x, cy)) }) {
                drawText(layout, color = color, topLeft = Offset(x - layout.size.width / 2f, cy - layout.size.height / 2f), alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
    // Las cifras que se ven, como nodos de accesibilidad (texto + «tocar para elegirla»): el lienzo las dibuja pero no las puede decir.
    val centerIndex by remember { derivedStateOf { pos.roundToInt().coerceIn(0, last) } }
    for (i in (centerIndex - 3).coerceAtLeast(0)..(centerIndex + 3).coerceAtMost(last)) {
        key(i) {
            Box(
                Modifier.size(width = RepsCarouselMath.CELL_DP.dp, height = 42.dp)
                    .offset { IntOffset((widthPx / 2f + (i - pos) * cellPx - cellPx / 2f).roundToInt(), 0) }
                    .semantics {
                        text = AnnotatedString(i.toString())
                        contentDescription = "$i repeticiones"
                        role = Role.Button
                        this.selected = i == shown
                        if (enabled && !failed) onClick { glide(i, 200); true }
                    },
            )
        }
    }
    }
}
