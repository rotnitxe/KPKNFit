package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknShape
import dev.chrisbanes.haze.HazeState

/**
 * Navigation dock with one measured occlusion value. The safe-area inset and visual bottom margin stay distinct
 * modifier steps; the reported size includes both plus the bar itself.
 */
@Composable
internal fun MeasuredKpknBottomDock(
    hazeState: HazeState,
    onMeasuredHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    safeAreaInsets: WindowInsets = WindowInsets.navigationBars,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasuredHeightChanged(it.height) }
            .windowInsetsPadding(safeAreaInsets)
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .kpknGlass(hazeState, KpknShape.island),
        content = content,
    )
}
