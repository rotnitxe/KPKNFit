package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.EffortScale
import com.example.kpkn.screens.workout.effortCenterPx
import com.example.kpkn.screens.workout.effortIndexAtX
import com.example.kpkn.screens.workout.effortSpokenDescription
import com.example.kpkn.screens.workout.effortThumbFraction
import com.example.kpkn.screens.workout.effortValueLabel
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.roundToInt

private val RangeInk = Color(0xFFF2EEE6)
private val RangeMuted = Color(0xFFB9B5AE)
private val RangeFaint = Color(0xFF92908A)
private val RangeFill = Color(0xFFE6E1D5)
private val RangePeach = Color(0xFFF49A6E)
private val RangeThumb = 25.dp
private val RangeInset = 12.5.dp

/**
 * `.effort-block` de la tarjeta del prototipo 02: «ESFUERZO» con el valor («Sin marcar ⌄» abre la escala completa), el `input[type=range]`
 * (pista de 9 con el tramo hecho en crema, la marca del plan y el pulgar crema con aro oscuro, melocotón desde el 80 %) y la leyenda
 * «RIR 10 · RIR 2 · plan · Fallo», donde «Fallo» es un botón. Reutiliza la escala y los gestos del deslizador de siempre.
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
    onClear: (() -> Unit)? = null,
) {
    val items = scale.items
    if (items.isEmpty()) return
    val count = items.size
    val chosen = selectedIndex?.coerceIn(0, count - 1)
    val selected = chosen?.let { items[it] }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val select by rememberUpdatedState(onSelect)
    var sheetOpen by remember { mutableStateOf(false) }
    val failure = items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }

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
                Text(
                    selected?.let { effortValueLabel(it) } ?: "Sin marcar",
                    style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp,
                        fontWeight = if (selected == null) FontWeight.Normal else FontWeight.SemiBold, fontFeatureSettings = "'tnum' 1"),
                    color = if (selected == null) RangeFaint else RangeInk,
                )
                Icon(KpknLineIcons.ChevronDown, null, Modifier.size(11.dp), tint = if (selected == null) RangeFaint else RangeInk)
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(44.dp).testTag(EffortSliderTag)
                .semantics {
                    contentDescription = effortSpokenDescription(selected)
                    stateDescription = selected?.let { effortValueLabel(it) } ?: "Sin marcar"
                    progressBarRangeInfo = ProgressBarRangeInfo((chosen ?: 0).toFloat(), 0f..(count - 1).toFloat(), (count - 2).coerceAtLeast(0))
                    if (enabled) setProgress { target -> select(target.roundToInt().coerceIn(0, count - 1)); true }
                }
                .pointerInput(count, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat()
                        val inset = RangeInset.toPx()
                        var last = effortIndexAtX(down.position.x, width, inset, count)
                        down.consume()
                        select(last)
                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        drag(down.id) { change ->
                            change.consume()
                            val index = effortIndexAtX(change.position.x, width, inset, count)
                            if (index != last) {
                                last = index
                                select(index)
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
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(9.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x07FFFFFF))) {
                if (chosen != null) {
                    Box(Modifier.fillMaxHeight().width(with(density) { centerPx.toDp() }).clip(RoundedCornerShape(6.dp)).background(RangeFill))
                }
            }
            scale.planIndex?.let { plan ->
                val planPx = effortCenterPx(plan, count, widthPx, insetPx)
                Box(Modifier.align(Alignment.CenterStart).offset { IntOffset(planPx.roundToInt(), 0) }.size(1.dp, 23.dp)
                    .background(Color(0x42FFFFFF)).testTag("effort-plan-mark"))
            }
            val hot = chosen != null && fraction >= .8f
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((centerPx - RangeThumb.toPx() / 2f).roundToInt(), 0) }
                    .size(RangeThumb)
                    .border(1.dp, Color(0x15FFFFFF), CircleShape)
                    .padding(1.dp)
                    .background(if (hot) RangePeach else RangeInk, CircleShape)
                    .border(4.dp, Color(0xFF1D1D1D), CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val legend = TextStyle(fontFamily = WizardFonts.body, fontSize = 9.sp, lineHeight = 17.sp)
            Text(effortValueLabel(items.first()), style = legend, color = RangeFaint)
            Spacer(Modifier.weight(1f))
            scale.planIndex?.let { plan -> Text("${effortValueLabel(items[plan])} · plan", style = legend, color = RangeMuted) }
            Spacer(Modifier.weight(1f))
            if (failure != null) {
                Text("Fallo", Modifier.clickable(enabled = enabled, role = Role.Button) { select(failure) }.testTag("live-effort-failure"),
                    style = legend, color = RangeFaint)
            }
        }
    }
    if (sheetOpen) {
        EffortScaleSheet(scale = scale, selectedIndex = chosen, onSelect = { select(it) }, onClear = onClear, onDismiss = { sheetOpen = false })
    }
}
