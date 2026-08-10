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
                onResult(CatalogResult.from(request, listOf(info)))
            },
            onMultiSelect = { infos ->
                selected = infos.map { it.id }
                onResult(CatalogResult.from(request, infos))
                infos.map { it.id }
            },
            onSelectionChange = { infos -> selected = infos.map { it.id } },
            onOpenExerciseDetail = onOpenExerciseDetail,
            onDismiss = {
                onResult(request.copy().let { CatalogResult(it.requestId, canceled = true) })
            },
            opaqueSurface = true,
            initialCatalogDefinitionId = request.targetExerciseId?.let { targetId ->
                catalog.firstOrNull { it.id == targetId }?.catalogDefinitionId
            },
            initialCatalogConfigurationId = request.targetExerciseId?.let { targetId ->
                catalog.firstOrNull { it.id == targetId }?.catalogConfigurationId
            },
        )
    }
}
