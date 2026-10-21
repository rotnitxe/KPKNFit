package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.workout.components.LocalLivePagerWorkingSetVisualHeightPx
import com.example.kpkn.screens.workout.components.publishLivePagerWorkingSetVisualHeight

/** Original G2 outer frame, narrowed by the user's later 24 dp gutter amendment. */
internal const val LiveCardWidthToHeight = 312f / 425f
private val LocalLiveSessionCardHeight = staticCompositionLocalOf { 0.dp }
internal val LocalLiveSessionCompactLayout = staticCompositionLocalOf { false }

/** The narrator takes the remainder; the card and the integrated line keep their approved dimensions. */
internal fun liveSessionRelatorBandHeight(viewportHeightDp: Float, headerHeightDp: Float, cardHeightDp: Float, bottomReservedDp: Float): Float =
    (viewportHeightDp - headerHeightDp - cardHeightDp - bottomReservedDp - 12f).coerceAtLeast(0f)

@Composable
internal fun liveSessionCardHeight(): Dp {
    val supplied = LocalLiveSessionCardHeight.current
    if (supplied > 0.dp) return supplied
    val configuration = LocalConfiguration.current
    // Width survives IME resize; keyboard and narrator never change the frame's proportions.
    return remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp - 48).coerceIn(240, 358).dp / LiveCardWidthToHeight
    }
}

/** A single measured frame for recording, preparation, rest and both reverse faces. */
@Composable
internal fun LiveSessionCardFrame(content: @Composable BoxScope.() -> Unit) {
    val measuredHeight = LocalLivePagerWorkingSetVisualHeightPx.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 358.dp).fillMaxWidth()) {
            val frameHeight = maxWidth / LiveCardWidthToHeight
            Box(
                Modifier.fillMaxWidth().height(frameHeight).testTag("live-card-frame")
                    .onSizeChanged { publishLivePagerWorkingSetVisualHeight(measuredHeight, it.height) },
            ) {
                CompositionLocalProvider(LocalLiveSessionCardHeight provides frameHeight) { content() }
            }
        }
    }
}
