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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val heightTickSlot = 14.dp
private val heightRuleItemHeight = 110.dp
private val heightRuleTrackHeight = 90.dp

/**
 * Regla horizontal de altura. El canónico sigue siendo cm. La posición inicial
 * no es una respuesta: `onValueChange` solo se emite tras un gesto o al tocar
 * el valor central. Arrastrar no avanza el wizard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizardHeightRule(
    unit: WizardHeightUnit,
    cm: Int?,
    onValueChange: (Int) -> Unit,
    onConfirm: (() -> Unit)? = null,
) {
    val reducedMotion = wizardReducedMotion()
    val stepCount = remember(unit) { WizardHeightScale.stepCount(unit) }
    val labelEvery = if (unit == WizardHeightUnit.CM) 5 else 6
    val initialIndex = remember(unit) {
        WizardHeightScale.nearestStepIndex(
            cm ?: (WizardHeightScale.MIN_CM + WizardHeightScale.MAX_CM) / 2,
            unit,
        ).coerceIn(0, stepCount - 1)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }
    var programmaticScrolls by remember { mutableStateOf(0) }
    var lastEmittedCm by remember { mutableStateOf<Int?>(null) }
    val onValueChangeState = rememberUpdatedState(onValueChange)

    fun centeredIndexNow(): Int {
        val layoutInfo = state.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return initialIndex
        val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        return visible.minByOrNull { abs((it.offset + it.size / 2) - center) }
            ?.index?.coerceIn(0, stepCount - 1)
            ?: initialIndex
    }

    fun emitCandidate(value: Int) {
        if (lastEmittedCm != value) {
            lastEmittedCm = value
            onValueChangeState.value(value)
        }
    }

    val centerIndex by remember(state, stepCount, initialIndex) {
        derivedStateOf { centeredIndexNow() }
    }
    val currentCm = WizardHeightScale.cmForStepIndex(centerIndex, unit)

    fun confirm() {
        touched = true
        emitCandidate(currentCm)
        onConfirm?.invoke()
    }

    fun settle() {
        if (touched) emitCandidate(WizardHeightScale.cmForStepIndex(centeredIndexNow(), unit))
    }

    val jumpTo: (Int) -> Unit = { target ->
        touched = true
        scope.launch {
            programmaticScrolls += 1
            try {
                if (reducedMotion) state.scrollToItem(target) else state.animateScrollToItem(target)
            } finally {
                programmaticScrolls -= 1
            }
            settle()
        }
    }

    LaunchedEffect(unit) {
        programmaticScrolls += 1
        try {
            state.scrollToItem(initialIndex)
            withFrameNanos { }
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex }
                ?: return@LaunchedEffect
            val viewportCenter = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
            val delta = item.offset - (viewportCenter - item.size / 2)
            if (delta != 0) state.scroll { scrollBy(delta.toFloat()) }
        } finally {
            touched = false
            programmaticScrolls -= 1
        }
    }

    LaunchedEffect(state, unit) {
        var userScrollActive = false
        snapshotFlow { Triple(state.isScrollInProgress, programmaticScrolls, centerIndex) }
            .collect { (scrolling, programmatic, candidateIndex) ->
                if (scrolling) {
                    if (programmatic == 0) {
                        touched = true
                        userScrollActive = true
                    }
                    return@collect
                }
                if (!userScrollActive) return@collect
                userScrollActive = false
                if (programmatic == 0) {
                    emitCandidate(WizardHeightScale.cmForStepIndex(candidateIndex, unit))
                }
            }
    }

    val unitSpoken = if (unit == WizardHeightUnit.CM) "centímetros" else "pies y pulgadas"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Regla de altura en $unitSpoken"
                stateDescription = "${WizardHeightScale.format(currentCm, unit)}. Desliza para ajustar"
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir altura") {
                        jumpTo((centerIndex - 1).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction("Aumentar altura") {
                        jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)); true
                    },
                )
                onClick("Confirmar altura") { confirm(); true }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = WizardHeightScale.format(currentCm, unit) +
                    if (unit == WizardHeightUnit.CM) " cm" else "",
                style = WizardTypography.measure,
                color = if (touched || cm != null) WizardColors.text else WizardColors.textMuted,
                modifier = Modifier
                    .clickable { confirm() }
                    .testTag("setup-height-rule-value")
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth(0.90f)
                .align(Alignment.CenterHorizontally)
                .height(heightRuleItemHeight),
        ) {
            val horizontalPadding = maxWidth / 2
            Box(Modifier.fillMaxWidth().height(heightRuleItemHeight)) {
                LazyRow(
                    state = state,
                    flingBehavior = fling,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(heightRuleTrackHeight),
                ) {
                    items(stepCount) { index ->
                        val stepCm = WizardHeightScale.cmForStepIndex(index, unit)
                        val labeled = index % labelEvery == 0
                        Box(Modifier.width(heightTickSlot).height(heightRuleItemHeight)) {
                            if (labeled) {
                                Text(
                                    text = if (unit == WizardHeightUnit.CM) {
                                        WizardHeightScale.formatCm(stepCm)
                                    } else {
                                        "${WizardHeightScale.feet(stepCm)}′"
                                    },
                                    color = WizardColors.textMuted.copy(alpha = .72f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .requiredWidth(36.dp)
                                        .clickable { jumpTo(index) }
                                        .padding(top = 2.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .width(1.5.dp)
                                    .height(if (labeled) 50.dp else 32.dp)
                                    .background(
                                        WizardColors.textMuted.copy(alpha = .55f),
                                        RoundedCornerShape(1.dp),
                                    ),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to WizardColors.background,
                                .16f to androidx.compose.ui.graphics.Color.Transparent,
                                .84f to androidx.compose.ui.graphics.Color.Transparent,
                                1f to WizardColors.background,
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 5.dp)
                        .width(2.dp)
                        .height(95.dp)
                        .background(WizardColors.ruleCursor, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}
