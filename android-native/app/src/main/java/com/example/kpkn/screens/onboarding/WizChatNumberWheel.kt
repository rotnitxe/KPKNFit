package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WizChatNumberWheel(
    values: List<Double>,
    selected: Double?,
    unit: String,
    accent: Color,
    onSettled: (Double) -> Unit,
    onCenterTap: (Double) -> Unit = onSettled,
) {
    if (values.isEmpty()) return
    val initialIndex = remember(values, selected) {
        selected?.let { target -> values.indices.minByOrNull { index -> abs(values[index] - target) } } ?: values.size / 2
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var touched by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    val centerIndex by remember(state, values) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) initialIndex else {
                val center = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index?.coerceIn(values.indices) ?: initialIndex
            }
        }
    }
    val touchExploration = LocalView.current.context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    LaunchedEffect(values, selected) {
        withFrameNanos { }
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex } ?: return@LaunchedEffect
        val viewportCenter = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
        val targetOffset = viewportCenter - item.size / 2
        val delta = item.offset - targetOffset
        if (delta != 0) {
            programmaticScroll = true
            try { state.scroll { scrollBy(delta.toFloat()) } } finally { programmaticScroll = false }
        }
    }
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        if (state.isScrollInProgress && !programmaticScroll) {
            touched = true
            lastInteraction = System.currentTimeMillis()
        }
    }
    LaunchedEffect(state.isScrollInProgress, touched, lastInteraction) {
        if (!touched || touchExploration || state.isScrollInProgress) return@LaunchedEffect
        val started = lastInteraction
        delay(900)
        if (started == lastInteraction && !state.isScrollInProgress && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            onSettled(values[centerIndex])
        }
    }
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .semantics {
                contentDescription = "Seleccionar $unit"
                stateDescription = "${formatWheelValue(values[centerIndex])} $unit"
                role = Role.Button
                onClick("Confirmar valor") { onCenterTap(values[centerIndex]); true }
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir valor") {
                        val target = (centerIndex - 1).coerceAtLeast(0)
                        scope.launch { state.animateScrollToItem(target); onSettled(values[target]) }
                        true
                    },
                    CustomAccessibilityAction("Aumentar valor") {
                        val target = (centerIndex + 1).coerceAtMost(values.lastIndex)
                        scope.launch { state.animateScrollToItem(target); onSettled(values[target]) }
                        true
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(values, key = { _, value -> value.toString() }) { index, value ->
            val center = centerIndex == index
            Box(
                modifier = Modifier
                    .height(70.dp)
                    .padding(horizontal = 18.dp)
                    .clickableWithoutRipple {
                        touched = true
                        lastInteraction = System.currentTimeMillis()
                        onCenterTap(value)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatWheelValue(value),
                    color = if (center) accent else WizChatTokens.muted,
                    fontSize = if (center) 32.sp else 21.sp,
                    fontWeight = if (center) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    Text(unit, color = WizChatTokens.muted, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

private fun formatWheelValue(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
