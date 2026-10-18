package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.CarouselValueTone
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs

/**
 * Fila de cifras con scroll y ajuste al centro (reps y RIR de la serie). Cada cifra va en un círculo `inset` de 48 dp; la elegida, en
 * tinta con la cifra en negro. Las demás, en `textMuted`. El gesto y el ajuste al centro son los de siempre (LR1a).
 */
@Composable
fun WorkoutHorizontalWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    roomier: Boolean = false,
    isError: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    centerGlowColor: Color? = null,
    showSemanticGlow: Boolean = centerGlowColor != null,
    centeredIndexHolder: MutableIntState? = null,
) {
    if (items.isEmpty()) return

    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val scope = rememberCoroutineScope()
    // Celda de 52 dp y círculo de 48 dp: entre cifra y cifra queda aire.
    val circleSize = 36.dp
    val controlHeight = if (roomier) 52.dp else 48.dp
    val density = LocalDensity.current

    val centerIndex by remember(items, listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf safeIndex
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.width / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: safeIndex
        }
    }

    SideEffect {
        centeredIndexHolder?.intValue = centerIndex
    }

    var programmaticScrolls by remember { mutableIntStateOf(0) }
    var emitAfterUserScroll by remember { mutableStateOf(false) }

    LaunchedEffect(safeIndex, items.size) {
        if (items.isEmpty() || listState.isScrollInProgress) return@LaunchedEffect
        val target = safeIndex.coerceIn(0, items.lastIndex)
        if (listState.firstVisibleItemIndex != target || listState.firstVisibleItemScrollOffset != 0) {
            programmaticScrolls += 1
            try {
                listState.scrollToItem(target)
            } finally {
                programmaticScrolls -= 1
            }
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.isScrollInProgress to programmaticScrolls }
            .collect { (scrolling, programmatic) ->
                if (scrolling) {
                    if (programmatic == 0) emitAfterUserScroll = true
                    return@collect
                }
                if (!emitAfterUserScroll) return@collect
                emitAfterUserScroll = false
                val centered = centerIndex
                if (centered in items.indices && centered != selectedIndex) {
                    onSelectedIndexChange(centered)
                }
            }
    }

    Box(modifier = modifier.height(controlHeight)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                // Capa fuera de pantalla: la máscara de alfa solo afecta a las cifras (LR1a).
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeWidth = with(density) { 24.dp.toPx() }
                    // Las orillas se desvanecen por alfa (DstIn): la cifra parcial se funde en vez de cortarse (LR1a).
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = 0f,
                            endX = fadeWidth,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val cellWidth = maxWidth / 5f
            val sidePadding = (maxWidth - cellWidth).coerceAtLeast(0.dp) / 2

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = sidePadding),
                verticalAlignment = Alignment.CenterVertically,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                userScrollEnabled = enabled,
            ) {
                itemsIndexed(items) { index, label ->
                    val isCentered = index == centerIndex
                    val fontSize = when {
                        label.length >= 7 -> 11.sp
                        label.length >= 5 -> 13.sp
                        isCentered -> 19.sp
                        else -> 15.sp
                    }
                    // La cifra elegida va en tinta, sin anillo de tono: la selección no lleva acento (LR1a).
                    val fill = when {
                        isCentered && isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        isCentered -> WizardColors.text
                        else -> KpknSurface.inset
                    }
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .clickable(enabled = enabled && index != centerIndex) {
                                scope.launch {
                                    programmaticScrolls += 1
                                    try {
                                        listState.animateScrollToItem(index)
                                        onSelectedIndexChange(index)
                                    } finally {
                                        programmaticScrolls -= 1
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isCentered) 45.dp else circleSize)
                                .background(fill, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = KpknType.bodyStrong.copy(
                                    fontFamily = com.example.kpkn.screens.onboarding.design.WizardFonts.display,
                                    fontSize = fontSize,
                                    fontWeight = if (isCentered) FontWeight.Bold else FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    fontFeatureSettings = "'tnum' 1",
                                ),
                                color = when {
                                    isError && isCentered -> MaterialTheme.colorScheme.error
                                    isCentered -> WizardColors.ctaContent
                                    else -> WizardColors.textMuted
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun carouselGlowForTone(tone: CarouselValueTone, accent: Color): Color? = when (tone) {
    CarouselValueTone.OnPlan -> WorkoutUiTokens.carouselGlowOnPlan(accent)
    CarouselValueTone.BelowPlan -> WorkoutUiTokens.carouselGlowBelowPlan(accent)
    CarouselValueTone.AbovePlan -> WorkoutUiTokens.carouselGlowAbovePlan(accent)
    CarouselValueTone.Neutral -> null
}
