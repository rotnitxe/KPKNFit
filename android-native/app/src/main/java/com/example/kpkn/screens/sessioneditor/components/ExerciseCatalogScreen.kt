package com.example.kpkn.screens.sessioneditor.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.screens.sessioneditor.CatalogCommitAction
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import com.example.kpkn.data.exercises.catalogv2.ApprovedAssetExerciseCatalogRepositoryV2

/**
 * Opaque, page-level host for the shared catalog. It deliberately owns no session model:
 * the caller receives only IDs through [CatalogResult].
 */
@Composable
internal fun ExerciseCatalogScreen(
    request: CatalogLaunchRequest,
    onResult: (CatalogResult) -> Unit,
    onBack: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val repository = remember(context) { ApprovedAssetExerciseCatalogRepositoryV2(context) }
    LaunchedEffect(repository) {
        repository.load()
    }
    val customExercises by com.example.kpkn.data.repository.CustomExerciseRepository.customExercises
        .collectAsStateWithLifecycle()
    val catalog = remember(customExercises) {
        (customExercises + exerciseCatalogSnapshot()).distinctBy { it.id.lowercase() }
    }
    var query by rememberSaveable(request.requestId) { mutableStateOf(request.initialQuery) }
    var selected by rememberSaveable(request.requestId) {
        mutableStateOf(request.selectedExerciseIds)
    }

    val editingExisting = request.selectionMode == CatalogSelectionMode.SINGLE ||
        request.selectionMode == CatalogSelectionMode.REPLACEMENT

    // Reemplazar (LV5, H1): la página arranca en el catálogo con el ejercicio actual de contexto, no en su ficha. El nombre y la dosis
    // del plan vienen en el pedido; si no hay nombre (el editor no lo manda), se saca del catálogo por su identidad.
    val replacement = remember(request, catalog) {
        val replacing = request.selectionMode == CatalogSelectionMode.REPLACEMENT ||
            request.origin == CatalogLaunchOrigin.REPLACEMENT
        if (!replacing) {
            null
        } else {
            val fromCatalog = request.targetCatalogConfigurationId
                ?.let { id ->
                    catalog.firstOrNull {
                        it.catalogConfigurationId.equals(id, ignoreCase = true) || it.id.equals(id, ignoreCase = true)
                    }?.name
                }
                ?: request.targetCatalogDefinitionId
                    ?.let { id -> catalog.firstOrNull { it.catalogDefinitionId == id }?.name }
            CatalogReplacementContext(
                exerciseName = request.targetExerciseName?.takeIf { it.isNotBlank() } ?: fromCatalog,
                planSummary = request.targetPlanSummary?.takeIf { it.isNotBlank() },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        ExercisePickerV2Catalog(
            repository = repository,
            query = query,
            onSearch = { query = it },
            editingExisting = editingExisting,
            selectedExercisesIds = selected.toSet(),
            onSelect = { info ->
                selected = listOf(info.id)
                onResult(CatalogResult.success(request, listOf(info), commitAction = CatalogCommitAction.ADD))
            },
            onMultiSelect = { infos ->
                selected = infos.map { it.id }
                onResult(CatalogResult.success(request, infos, commitAction = CatalogCommitAction.ADD))
                infos.map { it.id }
            },
            onSelectionChange = { infos -> selected = infos.map { it.id } },
            onOpenExerciseDetail = onOpenExerciseDetail,
            onCreateSupersetConfigured = { infos, config ->
                selected = infos.map { it.id }
                onResult(CatalogResult.success(request, infos, commitAction = CatalogCommitAction.CREATE_SUPERSET, supersetConfig = config))
            },
            isSupersetAddMode = request.origin == CatalogLaunchOrigin.SUPERSET || request.selectionMode == CatalogSelectionMode.SUPERSET,
            onDismiss = {
                onResult(CatalogResult.cancel(request))
            },
            // The page callback already writes the result and pops the route.
            // Calling onDismiss after it would overwrite that result as canceled.
            dismissAfterMultiSelect = false,
            opaqueSurface = true,
            initialCatalogDefinitionId = request.targetCatalogDefinitionId
                ?: request.targetExerciseId?.let { targetId ->
                    catalog.firstOrNull { it.id.equals(targetId, ignoreCase = true) }?.catalogDefinitionId
                        ?: catalog.firstOrNull { it.catalogDefinitionId == targetId }?.catalogDefinitionId
                        ?: catalog.firstOrNull { it.catalogConfigurationId.equals(targetId, ignoreCase = true) }?.catalogDefinitionId
                },
            initialCatalogConfigurationId = request.targetCatalogConfigurationId
                ?: request.targetExerciseId?.let { targetId ->
                    catalog.firstOrNull { it.id.equals(targetId, ignoreCase = true) }?.catalogConfigurationId
                        ?: catalog.firstOrNull { it.catalogConfigurationId.equals(targetId, ignoreCase = true) }?.catalogConfigurationId
                },
            targetGroupName = request.targetGroupName,
            replacement = replacement,
        )
    }
}
