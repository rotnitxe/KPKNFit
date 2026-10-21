package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** La parte visible de una figura tras el recorte del scroll: sin área visible, su reloj no corre. */
@Stable
internal class FeedbackAnimationVisibility {
    var visible by mutableStateOf(false)
        private set

    val modifier: Modifier = Modifier.onGloballyPositioned { coordinates ->
        visible = !coordinates.boundsInWindow().isEmpty
    }
}

@Composable
internal fun rememberFeedbackAnimationVisibility(): FeedbackAnimationVisibility = remember { FeedbackAnimationVisibility() }
