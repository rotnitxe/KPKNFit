package com.example.kpkn.screens.technique.hub

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.technique.hub.design.TechniqueExerciseRow
import com.example.kpkn.screens.technique.hub.design.TechniqueSettingsModule
import com.example.kpkn.screens.technique.importer.VideoImportSheet
import com.example.kpkn.screens.technique.importer.VideoImportViewModel
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

@Composable
fun TechniqueHubRoute(
    savedStateHandle: SavedStateHandle,
    onBack: () -> Unit,
    onOpenTrends: (String) -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onOpenCatalog: (CatalogLaunchRequest) -> Unit,
    catalogResult: CatalogResult? = null,
    onCatalogResultConsumed: () -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as Application
    val hub: TechniqueHubViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TechniqueHubViewModel(application) as T
    })
    val importer: VideoImportViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoImportViewModel(application, savedStateHandle) as T
    })
    val state by hub.uiState.collectAsStateWithLifecycle()
    val importState by importer.uiState.collectAsStateWithLifecycle()
    var customPlate by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> importer.selectVideo(uri) }
    val pickVideo: () -> Unit = {
        importer.pickerLaunched()
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        catch (_: Exception) { importer.selectVideo(null) }
    }
    LaunchedEffect(importState.isOpen, importState.uri, importState.pickerInFlight) {
        if (importState.isOpen && importState.uri == null && !importState.pickerInFlight) pickVideo()
    }
    LaunchedEffect(catalogResult) {
        catalogResult?.let { importer.applyCatalogResult(it); onCatalogResultConsumed() }
    }
    LaunchedEffect(importState.importedMediaId) {
        importState.importedMediaId?.let { id -> onOpenAnalysis(id); importer.analysisOpened() }
    }
    BackHandler(enabled = importState.isOpen) { importer.dismiss() }
    TechniqueHubScreen(
        state = state,
        onBack = onBack,
        onImport = importer::open,
        onOpenTrends = onOpenTrends,
        onAnalyzePrevious = hub::analyzePrevious,
        onAnalyzeVideos = hub::setAnalyzeVideos,
        onPrefer60Fps = hub::setPrefer60Fps,
        onPlateDiameterMm = hub::setPlateDiameterMm,
        onMaxRecordSeconds = hub::setMaxRecordSeconds,
        showCustomPlate = customPlate,
        onShowCustomPlate = { customPlate = it },
    )
    if (importState.isOpen && !importState.pickerInFlight) {
        VideoImportSheet(
            state = importState,
            recentExercises = state.exercises,
            onPickVideo = pickVideo,
            onChooseRecent = importer::chooseRecent,
            onOpenCatalog = { onOpenCatalog(importer.beginCatalogSelection()) },
            onWeightChange = importer::setWeight,
            onAnalyze = importer::analyze,
            onDismiss = importer::dismiss,
        )
    }
}

/** Hub sin repositorios: navegación, vídeos pendientes y ajustes reales de técnica. */
@Composable
fun TechniqueHubScreen(
    state: TechniqueHubUiState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onOpenTrends: (String) -> Unit,
    onAnalyzePrevious: () -> Unit,
    onAnalyzeVideos: (Boolean) -> Unit,
    onPrefer60Fps: (Boolean) -> Unit,
    onPlateDiameterMm: (Int) -> Unit,
    onMaxRecordSeconds: (Int) -> Unit,
    showCustomPlate: Boolean = false,
    onShowCustomPlate: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().navigationBarsPadding().testTag("technique-hub-screen")) {
        KpknTopRow(onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Técnica en vídeo", style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
            KpknPrimaryPill(
                text = if (LocalDensity.current.fontScale > 1.1f) "Analizar de la galería" else "Analizar un vídeo de la galería",
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Analizar un vídeo de la galería" },
                tag = "technique-hub-import",
            )
            if (state.isLoading) {
                Text("Cargando tus vídeos…", style = KpknType.body, color = WizardColors.textMuted)
            } else if (state.exercises.isEmpty()) {
                KpknEmptyState(
                    icon = KpknLineIcons.Play,
                    title = "Tu técnica empieza aquí",
                    body = "Graba una serie desde la sesión o analiza un vídeo de tu galería",
                )
            } else {
                KpknModule(modifier = Modifier.kpknEntrance("technique-hub", 0)) {
                    KpknModuleHeader(label = "Tus ejercicios")
                    state.exercises.forEach { item -> TechniqueExerciseRow(item, onClick = { onOpenTrends(item.exerciseKey) }) }
                }
            }
            if (state.pendingMediaIds.isNotEmpty()) {
                KpknTextAction(
                    text = if (state.isEnqueuing) "Añadiendo vídeos a la cola…" else "Analizar vídeos anteriores (${state.pendingMediaIds.size})",
                    onClick = { if (!state.isEnqueuing) onAnalyzePrevious() },
                    modifier = Modifier.fillMaxWidth().testTag("technique-hub-pending"),
                )
            }
            state.message?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
            TechniqueSettingsModule(
                state.settings, showCustomPlate, onShowCustomPlate, onAnalyzeVideos, onPrefer60Fps, onPlateDiameterMm, onMaxRecordSeconds,
                modifier = Modifier.kpknEntrance("technique-hub", 1),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
