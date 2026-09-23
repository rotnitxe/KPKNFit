package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pure scale for the graduated weight rule: internal persistence is always kg,
 * display may be kg or lb, and every displayed value keeps one-decimal precision.
 */
object WizChatWeightScale {
    const val STEP = 0.1
    const val LB_TO_KG = 0.45359237

    fun toKg(display: Double, unit: String): Double = if (unit == "lb") display * LB_TO_KG else display

    fun toDisplay(kg: Double, unit: String): Double = if (unit == "lb") kg / LB_TO_KG else kg

    fun snap(display: Double): Double = Math.round(display * 10.0) / 10.0

    fun displayRange(unit: String): ClosedFloatingPointRange<Double> {
        val low = Math.ceil(toDisplay(20.0, unit) * 10.0) / 10.0
        val high = Math.floor(toDisplay(500.0, unit) * 10.0) / 10.0
        return low..high
    }

    fun clampDisplay(display: Double, unit: String): Double {
        val range = displayRange(unit)
        return snap(display).coerceIn(range.start, range.endInclusive)
    }

    fun format(display: Double): String =
        if (display % 1.0 == 0.0) display.toInt().toString() else "%.1f".format(display).replace('.', ',')
}

private const val TICKS_PER_UNIT = 10
private val tickSlotWidth = 5.5.dp
private val ruleItemHeight = 66.dp

/**
 * Compact graduated weight rule: whole units are main numbers, halves are
 * secondary labels and tenths are small marks only. Sliding adjusts the
 * candidate at 0.1 precision; tapping a visible number sends it right away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizChatWeightRule(
    unit: String,
    selected: Double?,
    accent: Color,
    onConfirm: (Double) -> Unit,
) {
    val reducedMotion = wizChatReducedMotion()
    val isLb = unit == "lb"
    val range = remember(unit) { WizChatWeightScale.displayRange(unit) }
    val firstStep = remember(unit) { Math.round(range.start * TICKS_PER_UNIT).toInt() }
    val lastStep = remember(unit) { Math.round(range.endInclusive * TICKS_PER_UNIT).toInt() }
    val stepCount = lastStep - firstStep + 1
    val initialIndex = remember(unit, selected) {
        val anchor = selected ?: if (isLb) 155.0 else 70.0
        (Math.round(WizChatWeightScale.clampDisplay(anchor, unit) * TICKS_PER_UNIT).toInt() - firstStep)
            .coerceIn(0, stepCount - 1)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    val centerIndex by remember(state, stepCount, initialIndex) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) initialIndex else {
                val center = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index?.coerceIn(0, stepCount - 1) ?: initialIndex
            }
        }
    }
    val current = (firstStep + centerIndex) / TICKS_PER_UNIT.toDouble()
    val jumpTo: (Int) -> Unit = { target ->
        scope.launch { if (reducedMotion) state.scrollToItem(target) else state.animateScrollToItem(target) }
    }
    LaunchedEffect(unit, selected) {
        state.scrollToItem(initialIndex)
        withFrameNanos { }
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex } ?: return@LaunchedEffect
        val viewportCenter = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
        val delta = item.offset - (viewportCenter - item.size / 2)
        if (delta != 0) state.scroll { scrollBy(delta.toFloat()) }
    }
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = "Regla de peso en ${if (isLb) "libras" else "kilogramos"}"
        stateDescription = "${WizChatWeightScale.format(current)} $unit. Desliza para ajustar de décima en décima; toca un número para elegirlo"
        customActions = listOf(
            CustomAccessibilityAction("Disminuir 0,1 $unit") {
                jumpTo((centerIndex - 1).coerceAtLeast(0))
                true
            },
            CustomAccessibilityAction("Aumentar 0,1 $unit") {
                jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1))
                true
            },
            CustomAccessibilityAction("Confirmar ${WizChatWeightScale.format(current)} $unit") {
                onConfirm(current)
                true
            },
        )
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "${WizChatWeightScale.format(current)} $unit",
                color = accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { onConfirm(current) }
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(ruleItemHeight)) {
            LazyRow(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(horizontal = maxWidth / 2),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().height(ruleItemHeight),
            ) {
                items(stepCount) { index ->
                    val step = firstStep + index
                    val value = step / TICKS_PER_UNIT.toDouble()
                    val isInteger = step % TICKS_PER_UNIT == 0
                    val isHalf = step % (TICKS_PER_UNIT / 2) == 0
                    Box(
                        Modifier.width(tickSlotWidth).height(ruleItemHeight),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .offset(y = (-30).dp)
                                .width(if (isInteger) 2.5.dp else 1.5.dp)
                                .height(if (isInteger) 22.dp else if (isHalf) 14.dp else 7.dp)
                                .background(
                                    if (isInteger) WizChatTokens.text else WizChatTokens.muted.copy(alpha = if (isHalf) .9f else .55f),
                                    RoundedCornerShape(1.dp),
                                ),
                        )
                        val label = WizChatWeightScale.format(value)
                        if (isInteger) {
                            Text(
                                text = label,
                                color = WizChatTokens.text,
                                fontSize = if (isLb) 11.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .requiredWidth(if (isLb) 24.dp else 18.dp)
                                    .clickable { onConfirm(value) }
                                    .padding(vertical = 6.dp),
                            )
                        } else if (isHalf) {
                            Text(
                                text = label,
                                color = WizChatTokens.muted,
                                fontSize = if (isLb) 7.5.sp else 9.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .requiredWidth(if (isLb) 26.dp else 22.dp)
                                    .clickable { onConfirm(value) }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .height(32.dp)
                    .background(accent.copy(alpha = .85f), RoundedCornerShape(1.dp)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { jumpTo((centerIndex - 1).coerceAtLeast(0)) }) {
                Text("−0,1", color = WizChatTokens.muted)
            }
            TextButton(onClick = { jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)) }) {
                Text("+0,1", color = WizChatTokens.muted)
            }
        }
    }
}
