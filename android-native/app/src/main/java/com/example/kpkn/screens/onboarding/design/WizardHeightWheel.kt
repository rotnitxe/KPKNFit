package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val wheelItemHeight = 56.dp
private val wheelVisibleItems = 5

/**
 * Rueda vertical de altura de las referencias: el ítem central queda destacado
 * entre dos líneas guía y los vecinos se atenúan sin perder contraste.
 *
 * Como en la regla de peso, la posición inicial no es una respuesta: el valor
 * canónico (cm) solo se emite tras una interacción real. Alternar entre cm y
 * pies/pulgadas reexpresa el mismo valor sin acumular error de conversión.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizardHeightWheel(
    unit: WizardHeightUnit,
    cm: Int?,
    onValueChange: (Int) -> Unit,
) {
    val reducedMotion = wizardReducedMotion()
    val stepCount = remember(unit) { WizardHeightScale.stepCount(unit) }
    val labels = remember(unit) { WizardHeightScale.labels(unit) }
    val initialIndex = remember(unit, cm) {
        WizardHeightScale.nearestStepIndex(cm ?: (WizardHeightScale.MIN_CM + WizardHeightScale.MAX_CM) / 2, unit)
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
                val center = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.height / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index?.coerceIn(0, stepCount - 1)
                    ?: initialIndex
            }
        }
    }
    val currentCm = WizardHeightScale.cmForStepIndex(centerIndex, unit)

    fun settle() {
        if (touched) onValueChange(currentCm)
    }

    val jumpTo: (Int) -> Unit = { target ->
        touched = true
        scope.launch {
            if (reducedMotion) state.scrollToItem(target) else state.animateScrollToItem(target)
            settle()
        }
    }

    LaunchedEffect(unit, cm) {
        programmaticScroll = true
        try {
            state.scrollToItem(initialIndex)
            withFrameNanos { }
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex }
                ?: return@LaunchedEffect
            val viewportCenter = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.height / 2
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
                contentDescription = "Rueda de altura en ${unit.label.lowercase()}"
                stateDescription = "${WizardHeightScale.format(currentCm, unit)}. Desliza para ajustar"
                role = Role.Button
                onClick("Confirmar altura") { if (touched) onValueChange(currentCm); true }
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir altura") {
                        jumpTo((centerIndex - 1).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction("Aumentar altura") {
                        jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)); true
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(wheelItemHeight * wheelVisibleItems)) {
            LazyColumn(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(vertical = wheelItemHeight * ((wheelVisibleItems - 1) / 2)),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().height(wheelItemHeight * wheelVisibleItems),
            ) {
                itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                    val center = index == centerIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(wheelItemHeight)
                            .clickable { jumpTo(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = if (center) WizardTypography.wheelValue else WizardTypography.wheelValueNeighbour,
                            color = when {
                                center && (touched || cm != null) -> WizardColors.text
                                center -> WizardColors.textMuted
                                else -> WizardColors.textMuted.copy(alpha = .62f)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            // Dos líneas guía finas que enmarcan el ítem central.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -(wheelItemHeight / 2))
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WizardColors.cardBorder),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = wheelItemHeight / 2)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WizardColors.cardBorder),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { jumpTo((centerIndex - 1).coerceAtLeast(0)) }) {
                Text("−1", color = WizardColors.textMuted)
            }
            TextButton(onClick = { jumpTo((centerIndex + 1).coerceAtMost(stepCount - 1)) }) {
                Text("+1", color = WizardColors.textMuted)
            }
        }
        if (!touched && cm == null) {
            Text(
                text = "Desliza la rueda para declarar tu altura. La posición inicial no es una respuesta.",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}
