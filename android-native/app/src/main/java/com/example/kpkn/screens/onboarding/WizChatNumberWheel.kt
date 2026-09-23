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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val initialIndex = remember(values, selected, unit) {
        // A viewport anchor is not an answer. It must never become a suggested weight.
        val anchor = selected ?: when (unit) { "kg" -> 70.0; "lb" -> 155.0; "cm" -> 170.0; "años" -> 30.0; "min" -> 60.0; else -> values.first() }
        values.indices.minByOrNull { index -> abs(values[index] - anchor) } ?: 0
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val centerIndex by remember(state, values) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) initialIndex else {
                val center = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index?.coerceIn(values.indices) ?: initialIndex
            }
        }
    }
    LaunchedEffect(values, selected) {
        programmaticScroll = true
        try {
            state.scrollToItem(initialIndex)
            withFrameNanos { }
            val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == initialIndex } ?: return@LaunchedEffect
            val viewportCenter = state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportSize.width / 2
            val targetOffset = viewportCenter - item.size / 2
            val delta = item.offset - targetOffset
            if (delta != 0) state.scroll { scrollBy(delta.toFloat()) }
        } finally {
            touched = false
            programmaticScroll = false
        }
    }
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        if (state.isScrollInProgress && !programmaticScroll) {
            touched = true
        }
    }
    // Sliding only moves the candidate. A tap sends the value actually touched,
    // on the first attempt, wherever it sits in the viewport.
    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(horizontal = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .semantics {
                contentDescription = "Seleccionar $unit"
                stateDescription = if (selected == null && !touched) "Sin seleccionar. Desliza y confirma el valor central" else "${formatWheelValue(values[centerIndex])} $unit. Toca para confirmar"
                role = Role.Button
                onClick("Confirmar valor central") { onCenterTap(values[centerIndex]); true }
                customActions = listOf(
                    CustomAccessibilityAction("Disminuir valor") {
                        val target = (centerIndex - 1).coerceAtLeast(0)
                        scope.launch { touched = true; state.animateScrollToItem(target) }
                        true
                    },
                    CustomAccessibilityAction("Aumentar valor") {
                        val target = (centerIndex + 1).coerceAtMost(values.lastIndex)
                        scope.launch { touched = true; state.animateScrollToItem(target) }
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
                    .height(62.dp)
                    .padding(horizontal = 10.dp)
                    .clickable {
                        touched = true
                        onCenterTap(value)
                        if (index != centerIndex) scope.launch { state.animateScrollToItem(index) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatWheelValue(value),
                    color = if (center && (touched || selected != null)) accent else WizChatTokens.muted,
                    fontSize = if (center) 32.sp else 21.sp,
                    fontWeight = if (center) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun formatWheelValue(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
