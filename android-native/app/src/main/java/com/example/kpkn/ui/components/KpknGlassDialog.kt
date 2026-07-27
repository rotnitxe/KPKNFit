package com.example.kpkn.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState

/**
 * Centered in-composition glass dialog (via [KpknPortal]).
 * Use for custom dialog bodies that previously lived in a Material [androidx.compose.ui.window.Dialog].
 */
@Composable
fun KpknGlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
    dismissOnScrimClick: Boolean = true,
    dismissOnBackPress: Boolean = true,
    maxWidth: Dp = 520.dp,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    val rootHaze = LocalHazeState.current
    KpknPortal {
        val panelInteraction = remember { MutableInteractionSource() }
        BackHandler(enabled = dismissOnBackPress, onBack = onDismissRequest)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(360f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .then(
                        if (dismissOnScrimClick) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismissRequest,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )

            Box(
                modifier = modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .kpknGlassOrFallback(rootHaze, shape)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                KpknSheetContentTheme {
                    content()
                }
            }
        }
    }
}
