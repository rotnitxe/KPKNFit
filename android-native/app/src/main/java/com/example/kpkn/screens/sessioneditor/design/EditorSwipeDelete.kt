package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EdRemoval
import com.example.kpkn.screens.sessioneditor.components.EdRowSurface
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.screens.sessioneditor.frame.EdListIcons
import com.example.kpkn.screens.sessioneditor.frame.EdMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * La fila deslizada y su píldora de borrar (`.ed-swwrap`, sin estado): la fila se desplaza [rowOffsetPx] (≤ 0) sobre su fondo `module`
 * y detrás, pegada al borde derecho a 8 dp, crece la píldora ([pillWidthPx]); con [armed] se enciende en rosa y enseña «Eliminar». El aviso
 * «Ejercicio eliminado · Deshacer» ([notice]) cubre el hueco de la fila con [noticeAlpha]. Es lo que dibujan las hojas de render de las
 * escenas `ed-borrar-*`; el gesto y las animaciones los pone [EdSwipeDeleteRow].
 */
@Composable
internal fun EdSwipeSurface(
    rowOffsetPx: () -> Float,
    armed: Boolean,
    pillWidthPx: () -> Float,
    pillAlpha: () -> Float,
    popScale: () -> Float,
    noticeAlpha: () -> Float,
    notice: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    row: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 7.dp, bottom = 7.dp, end = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            EdDeletePill(widthPx = pillWidthPx, armed = armed, alphaProvider = pillAlpha, popScale = popScale)
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = rowOffsetPx() }
                // La fila lleva fondo opaco solo mientras se desliza: así la píldora no se ve a través y, en reposo, el módulo conserva sus esquinas.
                .drawBehind { if (rowOffsetPx() != 0f) drawRect(EdRowSurface) },
        ) {
            row()
        }
        if (notice != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = noticeAlpha() }
                    .background(EdRowSurface),
            ) {
                notice()
            }
        }
    }
}

/**
 * `.ed-delpill`: la píldora de borrar. Radio 22, de 7 dp bajo el borde de arriba a 7 sobre el de abajo. Apagada, #3a1e1c con la papelera en
 * `danger`; encendida (`armed`), `danger` con #2a0d0a, la papelera al 122 % (0,18 s, resorte) y la palabra «Eliminar» que crece en 0,2 s. Al
 * encenderse, un pequeño rebote de escala (`ed-pop`, 0,26 s).
 */
@Composable
private fun EdDeletePill(widthPx: () -> Float, armed: Boolean, alphaProvider: () -> Float, popScale: () -> Float) {
    val iconScale by animateFloatAsState(if (armed) 1.22f else 1f, tween(180, easing = EdMotion.Spring), label = "edPillIcon")
    val content = if (armed) Color(0xFF2A0D0A) else EdColors.danger
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val w = widthPx().toInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                layout(w, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                this.alpha = alphaProvider()
                scaleX = popScale()
                scaleY = popScale()
            }
            .clip(RoundedCornerShape(22.dp))
            .background(if (armed) EdColors.danger else Color(0xFF3A1E1C)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // pulido:transicion ed-T12 .ed-delpill svg 0,18 s --ed-spring · ed-X03 scale(1.22) al encenderse
            Icon(
                imageVector = EdListIcons.Trash,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            // pulido:transicion ed-T11 .ed-delpill .lb opacidad 0,16 s ease · ancho 0,2 s --ed-ease
            AnimatedVisibility(
                visible = armed,
                enter = expandHorizontally(tween(200, easing = EdMotion.Standard)) + fadeIn(tween(160, easing = EdMotion.Ease)),
                exit = shrinkHorizontally(tween(200, easing = EdMotion.Standard)) + fadeOut(tween(160, easing = EdMotion.Ease)),
            ) {
                Text(
                    text = "Eliminar",
                    style = edText(14f, FontWeight.SemiBold, content, lineHeight = 17.5f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * Borrar una fila deslizándola a la izquierda (Pulido v1 · EDITOR · A2): la fila sigue al dedo, detrás crece la píldora de borrar, al pasar
 * 104 dp se enciende (tic de vibración) y más allá la fila ofrece resistencia. Al soltar pasado el umbral (o con velocidad), la fila sale entera
 * en 220 ms mientras la píldora rellena el hueco y, 230 ms después, el aviso con «Deshacer» ocupa ese mismo hueco; si no, la fila vuelve con
 * un rebote de 340 ms. Quien manda el borrado es [removal] (el estado de la lista): al confirmarse, la fila hace la salida; al deshacerse, vuelve.
 *
 * @param content la fila; recibe el modificador que escucha el gesto (va en todo menos el asa, que tiene el suyo).
 */
@Composable
internal fun EdSwipeDeleteRow(
    removal: EdRemoval,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (swipe: Modifier) -> Unit,
) {
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reduced = wizardReducedMotion()
    val latestOnCommit by rememberUpdatedState(onCommit)
    var widthPx by remember { mutableIntStateOf(0) }
    val rowX = remember { Animatable(0f) }
    val pillOut = remember { Animatable(0f) }
    val pillAlphaOut = remember { Animatable(1f) }
    val noticeAlpha = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    var noticeVisible by remember { mutableStateOf(false) }
    var armedByDrag by remember { mutableStateOf(false) }
    var committedLocal by remember { mutableStateOf(false) }
    var sawPending by remember { mutableStateOf(removal.pending) }
    LaunchedEffect(removal.pending) {
        if (removal.pending) {
            sawPending = true
        } else if (sawPending) {
            sawPending = false
            committedLocal = false
        }
    }
    val gone = removal.pending || committedLocal
    val startedGone = remember { gone }
    var settledOnce by remember { mutableStateOf(false) }

    // La salida y la vuelta de la fila siguen al estado del borrado, no al gesto: así también valen los otros caminos (acción de TalkBack, hoja de acciones).
    LaunchedEffect(gone, widthPx) {
        if (widthPx <= 0) return@LaunchedEffect
        val d = localDensity.density
        val outX = -(widthPx + 8f * d)
        val fill = max(0f, widthPx - 16f * d)
        if (gone) {
            if (startedGone && !settledOnce) {
                rowX.snapTo(outX)
                pillOut.snapTo(fill)
                pillAlphaOut.snapTo(0f)
                noticeAlpha.snapTo(1f)
                noticeVisible = true
            } else {
                // pulido:transicion ed-T21 .ed-row.out 0,22 s «cae» (ed-J18) · ed-J19 la píldora rellena el hueco 0,22 s --ed-ease
                val millis = if (reduced) 1 else EdSwipe.OutMillis
                launch { rowX.animateTo(outX, tween(millis, easing = EdMotion.Falls)) }
                launch { pillOut.animateTo(fill, tween(millis, easing = EdMotion.Standard)) }
                delay(EdSwipe.NoticeDelayMillis)
                // pulido:animacion ed-A17 .ed-undo fundido de entrada 0,22 s --ed-ease (ed-J20, ed-J21)
                pillAlphaOut.snapTo(0f)
                noticeVisible = true
                noticeAlpha.snapTo(0f)
                noticeAlpha.animateTo(1f, tween(if (reduced) 1 else 220, easing = EdMotion.Standard))
            }
            settledOnce = true
        } else if (settledOnce || rowX.value < -1f || noticeVisible) {
            // Deshacer: la fila vuelve deslizándose con rebote (ed-T22 .ed-row.snap 0,34 s --ed-spring, ed-J24, ed-J25).
            noticeVisible = false
            noticeAlpha.snapTo(0f)
            pillOut.snapTo(0f)
            pillAlphaOut.snapTo(1f)
            rowX.animateTo(0f, tween(if (reduced) 1 else EdSwipe.SnapMillis, easing = EdMotion.Spring))
        }
    }

    // pulido:animacion ed-A03 .ed-delpill.pop ed-pop 0,26 s --ed-spring (escala .96 → 1,04 → 1 al encenderse)
    LaunchedEffect(armedByDrag) {
        if (armedByDrag && !reduced) {
            pop.snapTo(0.96f)
            pop.animateTo(1.04f, tween(130, easing = EdMotion.Spring))
            pop.animateTo(1f, tween(130, easing = EdMotion.Spring))
        }
    }

    val swipe = remember(enabled, gone, localDensity.density) {
        Modifier.pointerInput(enabled, gone) {
            if (!enabled || gone) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val d = density
                var locked = false
                var lastX = down.position.x
                var lastT = down.uptimeMillis
                var velocity = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val dxDp = (change.position.x - down.position.x) / d
                    val dyDp = (change.position.y - down.position.y) / d
                    if (change.changedToUpIgnoreConsumed()) {
                        if (locked) {
                            change.consume()
                            armedByDrag = false
                            if (edShouldCommit(edReveal(dxDp), velocity)) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                committedLocal = true
                                latestOnCommit()
                            } else {
                                // pulido:transicion ed-T22 .ed-row.snap 0,34 s --ed-spring (ed-J26)
                                scope.launch { rowX.animateTo(0f, tween(if (reduced) 1 else EdSwipe.SnapMillis, easing = EdMotion.Spring)) }
                            }
                        }
                        break
                    }
                    if (!locked) {
                        if (change.isConsumed) break
                        val decision = edSwipeDecision(dxDp, dyDp)
                        if (decision == EdSwipeDecision.Scroll) break
                        if (decision == EdSwipeDecision.Undecided) continue
                        locked = true
                    }
                    change.consume()
                    val now = change.uptimeMillis
                    velocity = abs(change.position.x - lastX) / d / max(1L, now - lastT).toFloat()
                    lastX = change.position.x
                    lastT = now
                    val reveal = edReveal(dxDp)
                    val nowArmed = edArmed(reveal)
                    if (nowArmed != armedByDrag) {
                        armedByDrag = nowArmed
                        // pulido:vibracion ed-J14 tic al pasar el umbral de 104
                        if (nowArmed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    scope.launch { rowX.snapTo(-reveal * d) }
                }
            }
        }
    }

    EdSwipeSurface(
        rowOffsetPx = { rowX.value },
        armed = gone || armedByDrag,
        pillWidthPx = {
            if (gone) pillOut.value else max(0f, -rowX.value - EdSwipe.PillInsetDp * localDensity.density)
        },
        pillAlpha = {
            if (gone) pillAlphaOut.value else if (-rowX.value / localDensity.density > EdSwipe.PillVisibleAfterDp) 1f else 0f
        },
        popScale = { pop.value },
        noticeAlpha = { noticeAlpha.value },
        notice = if (noticeVisible) {
            {
                SessionExerciseUndoNotice(
                    onUndo = removal.onUndo,
                    label = removal.label,
                    // La cuenta acaba cuando el borrado se confirma: el aviso aparece 230 ms después de pedirlo.
                    windowMillis = SESSION_UNDO_WINDOW_MS - EdSwipe.NoticeDelayMillis.toInt(),
                    startKey = Unit,
                )
            }
        } else {
            null
        },
        modifier = modifier
            .onSizeChanged { widthPx = it.width }
            .semantics {
                stateDescription = if (gone) "Eliminado" else "Desliza a la izquierda para eliminar"
                customActions = listOf(
                    CustomAccessibilityAction("Eliminar ejercicio") {
                        if (!gone) {
                            committedLocal = true
                            latestOnCommit()
                        }
                        true
                    },
                )
            },
    ) {
        content(swipe)
    }
}
