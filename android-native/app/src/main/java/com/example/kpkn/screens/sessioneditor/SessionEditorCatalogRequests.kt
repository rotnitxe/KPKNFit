package com.example.kpkn.screens.sessioneditor

import java.util.UUID

/** All catalog entries and retries derive context from the variant being edited. */
internal fun SessionEditorUiState.catalogLaunchRequest(requestId: String = UUID.randomUUID().toString()): CatalogLaunchRequest {
    val active = activeVariantSession ?: session
    val target = pickerTargetExerciseId?.let { id -> active?.allExercises()?.firstOrNull { it.id == id } }
    val replacing = pickerTargetExerciseId != null
    return CatalogLaunchRequest(
        requestId = requestId,
        origin = if (replacing) CatalogLaunchOrigin.REPLACEMENT else CatalogLaunchOrigin.SESSION_EDITOR,
        selectionMode = if (replacing) CatalogSelectionMode.REPLACEMENT else CatalogSelectionMode.MULTIPLE,
        targetExerciseId = pickerTargetExerciseId,
        targetCatalogDefinitionId = target?.catalogDefinitionId,
        targetCatalogConfigurationId = target?.catalogConfigurationId ?: target?.exerciseDbId,
        targetGroupName = pickerTargetPartId?.let { id -> active?.parts?.firstOrNull { it.id == id }?.name },
        selectedExerciseIds = selectedExercisesIds.toList(),
        initialQuery = if (replacing) "" else searchQuery,
        targetExerciseName = target?.name,
        targetPlanSummary = target?.let { sessionExerciseListDose(it, includeRest = false) },
    )
}
