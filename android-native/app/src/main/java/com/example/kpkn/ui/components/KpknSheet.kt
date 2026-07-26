package com.example.kpkn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/**
 * Canonical KPKN Liquid Glass [ModalBottomSheet].
 *
 * ModalBottomSheet is hosted in a separate window, so Haze cannot sample MainActivity's source.
 * It therefore uses the canonical cross-window frosted fallback instead of a dead hazeEffect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KpknSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    dismissible: Boolean = true,
    showDragHandle: Boolean = true,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = LocalHazeState.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = KpknGlass.SheetCornerRadius,
        topEnd = KpknGlass.SheetCornerRadius,
    )
    val defaultState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (dismissible) true else target != SheetValue.Hidden
        },
    )
    val effectiveState = sheetState ?: defaultState

    ModalBottomSheet(
        onDismissRequest = if (dismissible) onDismissRequest else ({}),
        sheetState = effectiveState,
        modifier = modifier,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .kpknWindowGlass(shape),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                if (showDragHandle && dismissible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 6.dp)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                    )
                }
                content()
            }
        }
    }
}
