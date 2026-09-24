package com.example.kpkn.screens.onboarding.design

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TICKS_PER_UNIT = 10
private val tickSlotWidth = 5.5.dp
private val ruleItemHeight = 72.dp

/**
 * Regla horizontal de peso de las referencias: cifra grande centrada sobre una
 * escala graduada con cursor vertical verde y la zona derecha sombreada.
 *
 * Todo número entero es una marca grande y rotulada; las mitades son etiquetas
 * menores y las décimas, marcas cortas. Deslizar ajusta de décima en décima.
 * **La posición inicial de la regla no es una respuesta**: `onValueChange` solo
 * se emite después de una interacción real del usuario.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizardWeightRule(
    unit: WizardMassUnit,
    valueKg: Double?,
    onValueChange: (Double) -> Unit,
) {
    val reducedMotion = wizardReducedMotion()
    val isLb = unit == WizardMassUnit.LB
    val range = remember(unit) { WizardWeightScale.displayRange(unit) }
    val firstStep = remember(unit) { kotlin.math.round(range.start * TICKS_PER_UNIT).toInt() }
    val lastStep = remember(unit) { kotlin.math.round(range.endInclusive * TICKS_PER_UNIT).toInt() }
    val stepCount = lastStep - firstStep + 1

    val initialIndex = remember(unit, valueKg) {
        val anchor = WizardWeightScale.toDisplay(valueKg ?: WizardWeightScale.MIN_KG * 3.5, unit)
        (kotlin.math.round(WizardWeightScale.clampDisplay(anchor, unit) * TICKS_PER_UNIT).toInt() - firstStep)
            .coerceIn(0, stepCount - 1)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }

    val centerIndex by remember(state, stepCount, initialIndex) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) initialIndex else {
                val center = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index?.coerceIn(0, stepCount - 1)
                    ?: initialIndex
            }
        }
    }
    val currentDisplay = (firstStep + centerIndex) / TICKS_PER_UNIT.toDouble()
    val currentKg = WizardWeightScale.toKg(currentDisplay, unit)

    fun settle() {
        if (touched) onValueChange(currentKg)
    }

    val jumpTo: (Int) -> Unit = { target ->
        touched = true
        scope.launch {
            if (reducedMotion) state.scrollToItem(target) else state.animateScrollToItem(target)
            settle()
        }
    }

    LaunchedEffect(unit, valueKg) {
        programmaticScroll = true
        try {
            state.scrollToItem(initialIndex)
            withFrameNanos { }
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex }
                ?: return@LaunchedEffect
            val viewportCenter = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
            val delta = item.offset - (viewportCenter - item.size / 2)
            if (delta != 0) state.scroll { scrollBy(delta.toFloat()) }
        } finally {
            touched = false
            programmaticScroll = false
        }
    }

    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        if (state.isScrollInProgress && !programmaticScroll) touched = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Regla de peso en ${if (isLb) "libras" else "kilogramos"}"
                stateDescription =
                    "${WizardWeightScale.format(currentDisplay)} ${unit.code}. Desliza para ajustar de décima en décima"
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir 0,1 ${unit.code}") {
                        jumpTo((centerIndex - 1).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction("Aumentar 0,1 ${unit.code}") {
                        jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)); true
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "${WizardWeightScale.format(currentDisplay)} ${unit.code}",
                style = WizardTypography.measure,
                color = if (touched) WizardColors.text else WizardColors.textMuted,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(ruleItemHeight)) {
            val horizontalPadding = maxWidth / 2
            Box(Modifier.fillMaxWidth().height(ruleItemHeight)) {
                // Zona derecha sombreada de la referencia, sin degradar el fondo del wizard.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ruleItemHeight)
                        .background(
                            Brush.horizontalGradient(
                                0.5f to androidx.compose.ui.graphics.Color.Transparent,
                                1f to WizardColors.ruleTint,
                            ),
                        ),
                )
                LazyRow(
                    state = state,
                    flingBehavior = fling,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
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
                                    .offset(y = (-32).dp)
                                    .width(if (isInteger) 2.5.dp else 1.5.dp)
                                    .height(if (isInteger) 22.dp else if (isHalf) 14.dp else 7.dp)
                                    .background(
                                        if (isInteger) WizardColors.text
                                        else WizardColors.textMuted.copy(alpha = if (isHalf) .9f else .55f),
                                        RoundedCornerShape(1.dp),
                                    ),
                            )
                            val label = WizardWeightScale.format(value)
                            if (isInteger) {
                                Text(
                                    text = label,
                                    color = WizardColors.text,
                                    fontSize = if (isLb) 11.sp else 13.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .requiredWidth(if (isLb) 24.dp else 18.dp)
                                        .clickable { jumpTo(index) }
                                        .padding(vertical = 6.dp),
                                )
                            } else if (isHalf) {
                                Text(
                                    text = label,
                                    color = WizardColors.textMuted,
                                    fontSize = if (isLb) 7.5.sp else 9.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .requiredWidth(if (isLb) 26.dp else 22.dp)
                                        .clickable { jumpTo(index) }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                // Cursor central verde de la referencia.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .width(2.5.dp)
                        .height(34.dp)
                        .background(WizardColors.ruleCursor, RoundedCornerShape(1.dp)),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { jumpTo((centerIndex - 1).coerceAtLeast(0)) }) {
                Text("−0,1", color = WizardColors.textMuted)
            }
            TextButton(onClick = { jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)) }) {
                Text("+0,1", color = WizardColors.textMuted)
            }
        }
        if (!touched && valueKg == null) {
            Text(
                text = "Desliza la regla para declarar tu peso. La posición inicial no es una respuesta.",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}
