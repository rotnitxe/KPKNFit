package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** El mismo gesto de la barra abre o pliega el cockpit; no activa ningún modo de edición. */
internal fun Modifier.cockpitVerticalGesture(mode: RoadmapMode, onModeChange: (RoadmapMode) -> Unit): Modifier =
    pointerInput(mode, onModeChange) {
        var distance = 0f
        detectVerticalDragGestures(
            onDragStart = { distance = 0f },
            onVerticalDrag = { change, amount -> distance += amount; change.consume() },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                val threshold = 36.dp.toPx()
                when {
                    distance <= -threshold && mode == RoadmapMode.COMPACT -> onModeChange(RoadmapMode.EXPANDED)
                    distance >= threshold && mode == RoadmapMode.EXPANDED -> onModeChange(RoadmapMode.COMPACT)
                }
                distance = 0f
            },
        )
    }
