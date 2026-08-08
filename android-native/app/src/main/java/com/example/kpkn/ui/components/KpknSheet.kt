package com.example.kpkn.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Canonical KPKN Liquid Glass bottom sheet.
 *
 * Height is **content-proportional** (wrap), capped by [maxHeightFraction] so tall content
 * can still scroll without covering the whole screen by default.
 *
 * When [stableHeightFraction] is set, the sheet uses that fraction of the screen as a fixed
 * height so tab switches (RULES / ASSISTANT) do not resize the panel.
 */
@Composable
fun KpknSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    showDragHandle: Boolean = true,
    /** Adds top safe-area padding for edge-to-edge sheets whose content reaches the status bar. */
    safeTopInset: Boolean = false,
    /**
     * Maximum fraction of the screen the sheet may occupy. Does NOT force-fill —
     * short content stays short.
     */
    maxHeightFraction: Float = 0.92f,
    /** Alias treated as [maxHeightFraction] only (never forces full-height fill). */
    heightFraction: Float? = null,
    /**
     * When non-null, sheet height is fixed to this fraction of the screen (capped by max),
     * so swapping tab content does not change the sheet size.
     */
    stableHeightFraction: Float? = null,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    /** Optional black veil drawn over the blur for a scoped contrast adjustment. */
    additionalGlassScrim: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rootHaze = LocalHazeState.current
    val cap = (heightFraction ?: maxHeightFraction).coerceIn(0.35f, 1f)
    KpknPortal {
        KpknSheetBody(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            dismissible = dismissible,
            showDragHandle = showDragHandle,
            safeTopInset = safeTopInset,
            maxHeightFraction = cap,
            stableHeightFraction = stableHeightFraction?.coerceIn(0.35f, cap),
            hazeState = rootHaze,
            additionalGlassScrim = additionalGlassScrim,
            content = content,
        )
    }
}

@Composable
private fun KpknSheetBody(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    dismissible: Boolean,
    showDragHandle: Boolean,
    safeTopInset: Boolean,
    maxHeightFraction: Float,
    stableHeightFraction: Float?,
    hazeState: HazeState?,
    additionalGlassScrim: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = RoundedCornerShape(
        topStart = KpknGlass.SheetCornerRadius,
        topEnd = KpknGlass.SheetCornerRadius,
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelInteraction = remember { MutableInteractionSource() }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val settleAnim = remember { Animatable(0f) }
    val enterOffset = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        enterOffset.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    fun dismissAnimated() {
        if (!dismissible) return
        scope.launch {
            enterOffset.animateTo(1f, animationSpec = tween(durationMillis = 180))
            onDismissRequest()
        }
    }

    BackHandler(enabled = dismissible) { dismissAnimated() }
    BackHandler(enabled = !dismissible) { /* Non-dismissible sheets consume Back. */ }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(350f),
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val sheetCapPx = maxHeightPx * maxHeightFraction
        val sheetCapDp = with(density) { sheetCapPx.toDp() }
        val stableHeightDp = stableHeightFraction?.let { frac ->
            with(density) { (maxHeightPx * frac).toDp() }
        }
        val dismissThresholdPx = with(density) {
            minOf(150.dp.toPx(), sheetCapPx * 0.25f)
        }
        val dragProgress = (dragOffsetPx / sheetCapPx).coerceIn(0f, 1f)
        val totalOffsetPx = dragOffsetPx + (enterOffset.value * sheetCapPx)

        fun settle() {
            if (!dismissible) {
                scope.launch {
                    settleAnim.snapTo(dragOffsetPx)
                    settleAnim.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { dragOffsetPx = value }
                }
                return
            }
            scope.launch {
                settleAnim.snapTo(dragOffsetPx)
                if (dragOffsetPx >= dismissThresholdPx) {
                    settleAnim.animateTo(sheetCapPx, animationSpec = tween(durationMillis = 220)) {
                        dragOffsetPx = value
                    }
                    onDismissRequest()
                } else {
                    settleAnim.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { dragOffsetPx = value }
                }
            }
        }

        val nestedScrollConnection = remember(dismissible, sheetCapPx, dismissThresholdPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!dismissible) return Offset.Zero
                    if (available.y < 0f && dragOffsetPx > 0f) {
                        val newOffset = (dragOffsetPx + available.y).coerceAtLeast(0f)
                        val consumed = newOffset - dragOffsetPx
                        dragOffsetPx = newOffset
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!dismissible) return Offset.Zero
                    if (available.y > 0f) {
                        dragOffsetPx += available.y
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (!dismissible) return Velocity.Zero
                    settle()
                    return Velocity.Zero
                }
            }
        }

        fun dragBy(delta: Float) {
            if (!dismissible) return
            dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
        }

        val dragModifier = if (dismissible) {
            Modifier.pointerInput(sheetCapPx, dismissThresholdPx) {
                detectVerticalDragGestures(
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragBy(dragAmount)
                    },
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - enterOffset.value).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.72f * (1f - dragProgress)))
                .then(
                    if (dismissible) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dismissAnimated() },
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        Box(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (stableHeightDp != null) Modifier.height(stableHeightDp)
                    else Modifier.wrapContentHeight().heightIn(max = sheetCapDp),
                )
                .offset { IntOffset(0, totalOffsetPx.roundToInt()) }
                .kpknGlassOrFallback(
                    hazeState = hazeState,
                    shape = sheetShape,
                    additionalScrim = additionalGlassScrim,
                )
                .clickable(
                    interactionSource = panelInteraction,
                    indication = null,
                    onClick = {},
                ),
        ) {
            KpknSheetContentTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (stableHeightDp != null) Modifier.fillMaxSize()
                            else Modifier.wrapContentHeight().heightIn(max = sheetCapDp),
                        )
                        .then(if (safeTopInset) Modifier.statusBarsPadding() else Modifier)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .nestedScroll(nestedScrollConnection)
                        .then(if (dismissible) dragModifier else Modifier),
                ) {
                    if (showDragHandle) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .then(if (dismissible) dragModifier else Modifier)
                                .semantics {
                                    contentDescription = if (dismissible) {
                                        "Arrastra hacia abajo para cerrar"
                                    } else {
                                        "Hoja"
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 42.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(KpknSheetTokens.Handle),
                            )
                        }
                    }
                    content()
                }
            }
        }
    }
}
