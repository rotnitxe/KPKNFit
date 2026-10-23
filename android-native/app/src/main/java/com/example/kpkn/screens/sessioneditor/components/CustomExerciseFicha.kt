package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.ExerciseMuscleInfo

/** La referencia del propio no es su identidad: historial y medios siguen ligados únicamente a su UUID. */
internal fun customExerciseDetailModel(info: ExerciseMuscleInfo, selected: Boolean = false) = CatalogDetailModel(
    name = info.name, caption = info.equipment, description = info.description,
    imageVariants = emptyList(), selectedImplementation = null, axes = emptyList(),
    configurationLine = info.catalogVariantChips.joinToString(" · ").takeIf { it.isNotBlank() },
    configurationIncomplete = false, muscleInfo = info, joints = emptyList(), patternId = info.movementPattern,
    isSelected = selected, canSelect = true, setupCues = info.setupCues.orEmpty(), executionCues = info.executionCues.orEmpty(),
    identityKeys = setOf(info.id), context = ExerciseFichaContext.CATALOG,
)
