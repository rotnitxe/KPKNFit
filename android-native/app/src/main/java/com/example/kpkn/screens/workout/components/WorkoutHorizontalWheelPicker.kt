package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.workout.CarouselValueTone
import kotlin.math.abs

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
    val itemWidth = if (roomier) 52.dp else 48.dp
    val controlHeight = if (roomier) 60.dp else 56.dp
    val density = LocalDensity.current
    val trackColor = WorkoutUiTokens.setInnerColor().copy(alpha = 0.92f)
    val cellColor = WorkoutUiTokens.setInnerHighestColor()
    val fadeColor = trackColor

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

    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else -> trackColor
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.height(controlHeight),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .drawWithContent {
                    drawContent()
                    val fadeWidth = with(density) { 24.dp.toPx() }
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(fadeColor, Color.Transparent),
                            startX = 0f,
                            endX = fadeWidth,
                        ),
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, fadeColor),
                            startX = size.width - fadeWidth,
                            endX = size.width,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val sidePadding = (maxWidth - itemWidth).coerceAtLeast(0.dp) / 2
            val cellCornerPx = with(density) { 14.dp.toPx() }
            val fillColor = if (showSemanticGlow && centerGlowColor != null) {
                centerGlowColor
            } else if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            } else {
                cellColor
            }
            val selectedTextColor = when {
                isError -> MaterialTheme.colorScheme.error
                centerGlowColor != null && showSemanticGlow -> Color(
                    red = centerGlowColor.red,
                    green = centerGlowColor.green,
                    blue = centerGlowColor.blue,
                    alpha = 1f,
                )
                else -> MaterialTheme.colorScheme.onSurface
            }

            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .drawBehind {
                        drawRoundRect(
                            color = fillColor,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(cellCornerPx),
                        )
                    },
            )

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = sidePadding),
                verticalAlignment = Alignment.CenterVertically,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                userScrollEnabled = enabled,
            ) {
                itemsIndexed(items) { index, label ->
                    val distance = abs(index - centerIndex)
                    val alpha = when (distance) {
                        0 -> 1f
                        1 -> 0.52f
                        else -> 0.38f
                    }
                    val fontSize = when {
                        label.length >= 7 -> 11.sp
                        label.length >= 5 -> 13.sp
                        index == centerIndex -> if (roomier) 22.sp else 20.sp
                        else -> if (roomier) 16.sp else 15.sp
                    }
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = fontSize,
                                fontWeight = if (index == centerIndex) FontWeight.Black else FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = when {
                                    isError && index == centerIndex -> MaterialTheme.colorScheme.error
                                    index == centerIndex -> selectedTextColor
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun carouselGlowForTone(tone: CarouselValueTone): Color? = when (tone) {
    CarouselValueTone.OnPlan -> WorkoutUiTokens.carouselGlowGreen()
    CarouselValueTone.BelowPlan -> WorkoutUiTokens.carouselGlowRed()
    CarouselValueTone.AbovePlan -> WorkoutUiTokens.carouselGlowBlue()
    CarouselValueTone.Neutral -> null
}
