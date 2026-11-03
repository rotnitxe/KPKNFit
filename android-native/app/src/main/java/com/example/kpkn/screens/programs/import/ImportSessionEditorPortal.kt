package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.ImportedProgramEditorStore
import com.example.kpkn.screens.sessioneditor.SessionEditorScreen
import com.example.kpkn.screens.sessioneditor.SessionEditorViewModel
import com.example.kpkn.screens.sessioneditor.flushProgramAutoSaveAndAwait
import com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogScreen
import com.example.kpkn.ui.components.KpknPortal
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private class ImportedEditorOwner(
    private val application: Application,
    override val viewModelStore: ViewModelStore,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    override val defaultViewModelCreationExtras: CreationExtras = MutableCreationExtras().apply {
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = application
    }
}

/** El editor y el catálogo habituales, alojados sobre la revisión de un borrador importado. */
@Composable
internal fun ImportSessionEditorPortal(
    store: ImportedProgramEditorStore,
    programId: String,
    sessionId: String,
    weekId: String,
    macroIndex: Int,
    mesoIndex: Int,
    dayOfWeek: Int?,
    onClose: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val owner = remember(store, application) { ImportedEditorOwner(application, store.editorViewModels) }
    val editor: SessionEditorViewModel = viewModel(
        viewModelStoreOwner = owner,
        key = "import-editor:${store.instanceKey}:$sessionId",
        factory = SessionEditorViewModel.factory(
            programId, sessionId, weekId, macroIndex, mesoIndex, dayOfWeek, store,
        ),
    )
    val scope = rememberCoroutineScope()
    var closing by remember(store) { mutableStateOf(false) }
    fun closeAfterSave() {
        if (closing) return
        closing = true
        scope.launch {
            val saved = try {
                editor.flushProgramAutoSaveAndAwait() && !editor.currentUiState.hasUnsavedChanges &&
                    editor.saveDraftForExitAndAwait()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
            if (!saved) {
                closing = false
                editor.updateUi { it.copy(snackbarMessage = "No se pudo guardar el borrador. Vuelve a intentarlo.") }
                return@launch
            }
            store.editorViewModels.clear()
            onClose()
        }
    }
    // El contrato serializado conserva el requestId durante una recreación de la actividad.
    var requestJson by rememberSaveable(programId, sessionId) { mutableStateOf<String?>(null) }
    var resultJson by rememberSaveable(programId, sessionId) { mutableStateOf<String?>(null) }
    val request = requestJson?.let { runCatching { Json.decodeFromString(CatalogLaunchRequest.serializer(), it) }.getOrNull() }
    val result = resultJson?.let { runCatching { Json.decodeFromString(CatalogResult.serializer(), it) }.getOrNull() }

    KpknPortal {
        Box(Modifier.fillMaxSize()) {
            // SessionEditorScreen normally owns a navigation destination with an opaque
            // background. As a portal it sits above the importer, so provide that same
            // full-screen surface and consume taps in the uncovered areas.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .clearAndSetSemantics {},
            )
            SessionEditorScreen(
                programId = programId,
                sessionId = sessionId,
                draftWeekId = weekId,
                draftMacroIndex = macroIndex,
                draftMesoIndex = mesoIndex,
                draftDayOfWeek = dayOfWeek,
                importedProgramStore = store,
                onBack = ::closeAfterSave,
                onSavedAndExit = ::closeAfterSave,
                onOpenCatalog = { requestJson = Json.encodeToString(CatalogLaunchRequest.serializer(), it) },
                catalogResult = result,
                onCatalogResultConsumed = { resultJson = null },
                viewModel = editor,
            )
            request?.let { active ->
                ExerciseCatalogScreen(
                    request = active,
                    onResult = {
                        resultJson = Json.encodeToString(CatalogResult.serializer(), it)
                        requestJson = null
                    },
                    onBack = {
                        resultJson = Json.encodeToString(CatalogResult.serializer(), CatalogResult.cancel(active))
                        requestJson = null
                    },
                    onOpenExerciseDetail = {},
                )
            }
        }
    }
}
