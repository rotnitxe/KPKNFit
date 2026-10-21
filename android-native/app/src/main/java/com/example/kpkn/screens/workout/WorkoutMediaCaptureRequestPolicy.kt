package com.example.kpkn.screens.workout

import com.example.kpkn.data.media.WorkoutMediaSides
import com.example.kpkn.data.models.Exercise

/** El diario inicial y la entrega final deben identificar la misma configuración y lateralidad. */
internal fun WorkoutMediaCaptureRequest.withExerciseCaptureContext(exercise: Exercise?): WorkoutMediaCaptureRequest = copy(
    catalogDefinitionId = exercise?.catalogDefinitionId ?: catalogDefinitionId,
    catalogConfigurationId = exercise?.catalogConfigurationId ?: catalogConfigurationId,
    side = WorkoutMediaSides.normalize(side, exercise),
)
