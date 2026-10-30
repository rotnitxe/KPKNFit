package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.EffortScale
import com.example.kpkn.screens.workout.effortCenterPx
import com.example.kpkn.screens.workout.effortFireLevel
import com.example.kpkn.screens.workout.effortIndexAtX
import com.example.kpkn.screens.workout.effortSpokenDescription
import com.example.kpkn.screens.workout.effortThumbFraction
import com.example.kpkn.screens.workout.effortValueLabel
import com.example.kpkn.screens.workout.effortWord
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.roundToInt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import com.example.kpkn.domain.workout.EffortHeat
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.KpknShadowLayer
import com.example.kpkn.ui.design.depth.KpknShadowSpec
import com.example.kpkn.ui.design.depth.drawKpknShadow
import com.example.kpkn.ui.design.depth.kpknRing
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.kpknAnimate
import androidx.compose.runtime.withFrameNanos

private val RangeInk = Color(0xFFF2EEE6)
private val RangeMuted = Color(0xFFB9B5AE)
private val RangeFaint = Color(0xFF92908A)
private val RangeFill = Color(0xFFE6E1D5)
private val RangePeach = Color(0xFFF49A6E)
private val RangeAmber = Color(0xFFF7CF73)
private val RangeHeat = Color(0xFFFFF1C8)
private val RangeThumb = 26.dp
private val RangeInset = 12.5.dp

/**
 * Pulido v1 · 13 · `.lv-effb` del lienzo: «ESFUERZO» con el valor y su palabra («RPE 8 · exigente ⌄», «Sin marcar ⌄» abre la escala completa),
 * la banda táctil de 44 dp (pista de 10 con el tramo hecho en crema, la marca del plan de 2 × 24 y el pulgar de 26 con aro oscuro) y la
 * leyenda: el mínimo a la izquierda, «Fallo» (un botón) a la derecha y el plan bajo su marca, que cede ante «Fallo» sin descartarse.
 * Con el esfuerzo en calor (nivel 3 o más) el valor se vuelve melocotón y en el fallo, ámbar. Reutiliza la escala y los niveles del motor
 * (`effortFireLevel`). El calor visual (brasas, destello, halo, degradado del relleno) es del lienzo `.lv-fx`:
 * pulido:lv-J08 lv-J09 lv-J10 lv-J11 lv-J12 lv-J13.
 */
@Composable
internal fun LiveEffortRange(
    scale: EffortScale,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    /** Falso (tarjeta fuera de pantalla, fotograma fijado): sin bucle de cuadros, como con movimiento reducido. */
    animate: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    val items = scale.items
    if (items.isEmpty()) return
    val count = items.size
    val chosen = selectedIndex?.coerceIn(0, count - 1)
    val selected = chosen?.let { items[it] }
    val level = selected?.let { effortFireLevel(it) } ?: 0f
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val select by rememberUpdatedState(onSelect)
    var sheetOpen by remember { mutableStateOf(false) }
    val failure = items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }
    val still = wizardReducedMotion() || !animate
    val fx = remember(still) { LiveEffortFx(still) }
    var popKey by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    // Lo que la persona elige: el calor sube con el nivel, el pulgar rebota y, al llegar al fallo (o a RPE 9,5), hay destello. pulido:lv-A05
    fun pick(index: Int) {
        val previous = if (chosen != null) level else 0f
        val next = items.getOrNull(index)?.let { effortFireLevel(it) } ?: 0f
        if (index != chosen) {
            popKey++
            if (next >= 5f && previous < 5f) fx.arrive(full = true) else if (next >= 3.5f && previous < 3.5f) fx.arrive(full = false)
        }
        select(index)
    }

    Column(modifier.fillMaxWidth().padding(top = if (compact) 2.dp else 6.dp).testTag("live-effort-range")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ESFUERZO", style = TextStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp), color = RangeMuted)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.heightIn(min = 22.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Elegir el esfuerzo realizado") { sheetOpen = true }
                    .testTag("live-effort-value"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val valueStyle = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
                if (selected == null) {
                    Text("Sin marcar", style = valueStyle, color = RangeFaint)
                } else {
                    // Valor en tinta (melocotón desde el nivel 3 y ámbar en el fallo) y, a su lado, la palabra del motor: «· exigente».
                    Text(effortValueLabel(selected), style = valueStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = when { level >= 5f -> RangeAmber; level >= 3f -> RangePeach; else -> RangeInk })
                    effortWord(selected)?.let { word ->
                        Text("· $word", Modifier.testTag("live-effort-word"), style = valueStyle, color = RangeMuted)
                    }
                }
                Icon(KpknLineIcons.ChevronDown, null, Modifier.size(11.dp), tint = if (selected == null) RangeFaint else RangeInk)
            }
        }
        // pulido:lv-S08 (anillo de foco) lv-X06 (pulgar con will-change)
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(44.dp).testTag(EffortSliderTag)
                .onFocusChanged { focused = it.isFocused }
                .focusable(enabled)
                .semantics {
                    contentDescription = effortSpokenDescription(selected)
                    stateDescription = selected?.let { effortValueLabel(it) } ?: "Sin marcar"
                    progressBarRangeInfo = ProgressBarRangeInfo((chosen ?: 0).toFloat(), 0f..(count - 1).toFloat(), (count - 2).coerceAtLeast(0))
                    if (enabled) setProgress { target -> pick(target.roundToInt().coerceIn(0, count - 1)); true }
                }
                .pointerInput(count, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val inset = RangeInset.toPx()
                        var last = effortIndexAtX(down.position.x, width, inset, count)
                        down.consume()
                        pick(last)
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        drag(down.id) { change ->
                            change.consume()
                            val index = effortIndexAtX(change.position.x, width, inset, count)
                            if (index != last) {
                                last = index
                                pick(index)
                                if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                },
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val insetPx = with(density) { RangeInset.toPx() }
            val fraction = effortThumbFraction(chosen ?: 0, count)
            val centerPx = insetPx + fraction * (widthPx - 2f * insetPx)
            val centerDp = with(density) { centerPx.toDp().value }
            // El calor sigue el nivel elegido y se precalienta una vez al aparecer (110 cuadros, para que las brasas ya existan). pulido:lv-J13
            remember(fx) {
                fx.setTarget(if (chosen != null) level else 0f, immediate = true)
                if (chosen != null) fx.prewarm(centerDp)
                true
            }
            LaunchedEffect(level, chosen, still) { fx.setTarget(if (chosen != null) level else 0f) }
            val currentCenter by rememberUpdatedState(centerDp)
            // El bucle de cuadros (`requestAnimationFrame` del lienzo): corre mientras haya calor, brasas o destello. pulido:lv-J12
            LaunchedEffect(fx, level, chosen, popKey) {
                var last = withFrameNanos { it }
                while (fx.animating) {
                    withFrameNanos { now ->
                        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
                        last = now
                        fx.step(dt, currentCenter)
                    }
                }
            }
            // Pista: top 17, 10 dp, blanco al 5 %.
            Box(Modifier.align(Alignment.TopStart).offset(y = 17.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0x0CFFFFFF)))
            // El tramo hecho (hasta el centro del pulgar): crema y, con calor, degradado crema, tono medio y extremo caliente con su resplandor
            // (`fill.style.background` y `fill.style.boxShadow` del lienzo). pulido:lv-J08 lv-J09 lv-J10
            if (chosen != null) {
                Box(
                    Modifier.align(Alignment.TopStart).offset(y = 17.dp).width(with(density) { centerPx.toDp() }).height(10.dp)
                        .drawBehind {
                            fx.frame
                            val fire = EffortHeat.fireAt(fx.level)
                            val shape = RoundedCornerShape(6.dp)
                            val radius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                            if (fire == null) {
                                drawRoundRect(RangeFill, cornerRadius = radius)
                            } else {
                                val from = ((1f - fire.tail) * 100f).coerceAtLeast(0f)
                                val mid = (from + (100f - from) * .55f) / 100f
                                val brush = Brush.horizontalGradient(
                                    0f to RangeFill, (from / 100f) to RangeFill,
                                    mid to Color(0xFF000000.toInt() or fire.midRgb), 1f to Color(0xFF000000.toInt() or fire.tipRgb),
                                )
                                drawKpknShadow(KpknShadowSpec(listOf(KpknShadowLayer(blur = fire.blurDp.dp, color = Color(244, 154, 110, (fire.alpha * 255).toInt())))), shape)
                                drawRoundRect(brush, cornerRadius = radius)
                            }
                        },
                )
            }
            // La marca del plan: 2 x 24 dp a 10 dp del borde superior.
            scale.planIndex?.let { plan ->
                val planPx = effortCenterPx(plan, count, widthPx, insetPx)
                Box(Modifier.align(Alignment.TopStart)
                    .offset { IntOffset((planPx - 1.dp.toPx()).roundToInt(), 10.dp.roundToPx()) }
                    .size(width = 2.dp, height = 24.dp)
                    .background(Color(0x47FFFFFF), RoundedCornerShape(1.dp))
                    .testTag("effort-plan-mark"))
            }
            // `.lv-fx`: el lienzo de calor, 14 dp mas ancho a cada lado y de la altura de la banda (nada se sale de ella).
            if (chosen != null) Canvas(Modifier.align(Alignment.TopStart).offset(x = (-14).dp).requiredSize(maxWidth + 28.dp, 44.dp)) {
                fx.frame
                translate(14.dp.toPx(), 0f) { fx.draw(this, currentCenter) }
            }
            // Pulgar de 26 dp a 9 dp del borde superior: aro oscuro de 5 dp y centro crema que se calienta con el nivel (crema, melocoton,
            // amarillo claro); con calor lleva un halo `0 0 (6+L*3,2)px` del melocoton. Rebota al elegir (`lv-pop`). pulido:lv-S09 lv-J11 lv-A05 lv-X06
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset { IntOffset((centerPx - RangeThumb.toPx() / 2f).roundToInt(), 9.dp.roundToPx()) }
                    .size(RangeThumb)
                    .kpknAnimate(KpknFamilies.Rebote.demo, enabled = popKey > 0, restartKey = popKey)
                    .drawBehind {
                        fx.frame
                        val l = fx.level
                        val fire = EffortHeat.fireAt(l)
                        val spec = if (fire != null) {
                            val hot = EffortHeat.hot(l, true)
                            val base = KpknShadow.BordeExterior.spec
                            KpknShadowSpec(base.layers + KpknShadowLayer(blur = (6f + l * 3.2f).dp, color = Color(244, 154, 110, ((.2f + hot * .55f) * 255).toInt())))
                        } else KpknShadow.BordeExterior.spec
                        drawKpknShadow(spec, CircleShape)
                        drawCircle(Color(0xFF1D1D1D), size.minDimension / 2f)
                        val inner = if (chosen == null) RangeInk
                        else lerp(RangeInk, if (l >= 5f) RangeHeat else RangePeach, EffortHeat.thumbMix(l))
                        drawCircle(inner, size.minDimension / 2f - 5.dp.toPx())
                    },
            )
            // Anillo de foco (`.lv-eff:focus-visible`: `0 0 0 2px #f2eee655`, radio 12). pulido:lv-S08
            if (focused) Box(Modifier.matchParentSize().kpknRing(KpknShadow.AnilloFoco, RoundedCornerShape(12.dp)))
        }
        // Leyenda de 16 dp: el mínimo, el plan bajo su marca y «Fallo».
        val legend = TextStyle(fontFamily = WizardFonts.body, fontSize = 9.sp, lineHeight = 16.sp, fontFeatureSettings = "'tnum' 1, 'lnum' 1")
        // Un plan «a fallo» no lleva leyenda propia: «Fallo · plan» ya es el botón de la derecha.
        val planIndex = scale.planIndex?.takeIf { it != failure }
        val failureIsPlan = failure != null && scale.planIndex == failure
        EffortLegend(
            planCenterFraction = planIndex?.let { effortThumbFraction(it, count) },
            insetDp = RangeInset,
            hasFailure = failure != null,
            modifier = Modifier.fillMaxWidth().height(16.dp),
        ) {
            Text(effortValueLabel(items.first()), style = legend, color = RangeFaint, maxLines = 1, softWrap = false)
            if (planIndex != null) {
                Text("${effortValueLabel(items[planIndex])} · plan", style = legend, color = RangeMuted, maxLines = 1, softWrap = false)
            }
            if (failure != null) {
                Text(if (failureIsPlan) "Fallo · plan" else "Fallo",
                    Modifier.clickable(enabled = enabled, role = Role.Button) { pick(failure) }.testTag("live-effort-failure"),
                    style = legend, color = if (failureIsPlan) RangeMuted else RangeFaint, maxLines = 1, softWrap = false)
            }
        }
    }
    if (sheetOpen) {
        EffortScaleSheet(scale = scale, selectedIndex = chosen, onSelect = { select(it) }, onClear = onClear, onDismiss = { sheetOpen = false })
    }
}

/**
 * `.lv-leg`: el mínimo pegado a la izquierda y «Fallo» a la derecha; el plan centrado bajo su marca pero sin pisar a ninguno de los dos
 * (a 8 dp como mínimo): si la marca está muy cerca del fallo el plan se queda a su izquierda en vez de desaparecer.
 * Hijos, en orden: mínimo, plan (si [planCenterFraction] no es nulo) y fallo (si [hasFailure]).
 */
@Composable
private fun EffortLegend(
    planCenterFraction: Float?,
    insetDp: Dp,
    hasFailure: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val first = placeables[0]
        var next = 1
        val plan = if (planCenterFraction != null) placeables.getOrNull(next++) else null
        val fail = if (hasFailure) placeables.getOrNull(next) else null
        val width = constraints.maxWidth
        val height = constraints.maxHeight.coerceAtLeast(first.height)
        val gap = 8.dp.roundToPx()
        layout(width, height) {
            first.placeRelative(0, (height - first.height) / 2)
            fail?.placeRelative(width - fail.width, (height - fail.height) / 2)
            if (plan != null && planCenterFraction != null) {
                val inset = insetDp.roundToPx()
                val center = inset + planCenterFraction * (width - 2 * inset)
                val minX = first.width + gap
                val maxX = width - (fail?.width ?: 0) - gap - plan.width
                val x = (center - plan.width / 2f).roundToInt().coerceIn(minX, maxOf(minX, maxX))
                plan.placeRelative(x, (height - plan.height) / 2)
            }
        }
    }
}
