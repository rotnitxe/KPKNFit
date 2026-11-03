package com.example.kpkn.screens.programs.importer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogScreen
import com.example.kpkn.ui.components.KpknPortal

/** Identidad del catálogo elegido; un resultado sin configuración solo es válido si es un ejercicio personal real. */
internal fun importCatalogIdentity(request: CatalogLaunchRequest, result: CatalogResult): String? {
    if (result.canceled || !result.isValidFor(request)) return null
    return result.selectedConfigurationIds.singleOrNull()?.takeIf { it.isNotBlank() }
        ?: result.selectedExerciseIds.singleOrNull()?.takeIf { it.startsWith("custom:", ignoreCase = true) }
}

/** El mismo catálogo y creador que usa el editor de sesiones, iniciado con el nombre original del documento. */
@Composable
internal fun ImportSharedCatalog(rawName: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    val request = remember(rawName) {
        CatalogLaunchRequest(
            origin = CatalogLaunchOrigin.SESSION_EDITOR,
            selectionMode = CatalogSelectionMode.SINGLE,
            initialQuery = rawName,
        )
    }
    KpknPortal {
        ExerciseCatalogScreen(
            request = request,
            onResult = { result ->
                importCatalogIdentity(request, result)?.let(onSelect)
                onClose()
            },
            onBack = onClose,
            onOpenExerciseDetail = {},
        )
    }
}
