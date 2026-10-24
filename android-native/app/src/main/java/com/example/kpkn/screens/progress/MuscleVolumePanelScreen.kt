package com.example.kpkn.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.training.MuscleVolumeWindow
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.auge.rememberAugeViewModel
import com.example.kpkn.screens.home.homeBodyValues
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.progress.design.MuscleVolumeBodyModule
import com.example.kpkn.screens.progress.design.MuscleVolumeControls
import com.example.kpkn.screens.progress.design.MuscleVolumeEmpty
import com.example.kpkn.screens.progress.design.MuscleVolumeListModule
import com.example.kpkn.screens.progress.design.MuscleVolumeUnassignedNote
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import kotlinx.coroutines.flow.map

/** Lo que la pantalla puede pedir al ViewModel; con valores por defecto vacíos para pintarla con datos de ejemplo. */
data class MuscleVolumePanelActions(
    val onBack: () -> Unit = {},
    val onWindow: (MuscleVolumeWindow) -> Unit = {},
    val onAverage: (Boolean) -> Unit = {},
    val onMode: (VolumeCountingMode) -> Unit = {},
    val onAdjust: (Boolean) -> Unit = {},
    val onView: (MuscleVolumeView) -> Unit = {},
    val onMetric: (BodyMetric) -> Unit = {},
    val onSide: (BodySide) -> Unit = {},
    val onToggleMuscle: (String) -> Unit = {},
    val onSelectRegion: (String) -> Unit = {},
) {
    companion object {
        fun from(viewModel: MuscleVolumePanelViewModel, onBack: () -> Unit): MuscleVolumePanelActions = MuscleVolumePanelActions(
            onBack = onBack,
            onWindow = viewModel::selectWindow,
            onAverage = viewModel::selectAverage,
            onMode = viewModel::selectMode,
            onAdjust = viewModel::selectAdjustByIntensity,
            onView = viewModel::selectView,
            onMetric = viewModel::selectMetric,
            onSide = viewModel::selectSide,
            onToggleMuscle = viewModel::toggleMuscle,
            onSelectRegion = viewModel::selectRegion,
        )
    }
}

/**
 * Ruta del panel desde la navegación. [programId] (o null) viene del enlace de la pestaña Volumen del programa; sin él cuentan los
 * registros de todos los programas. La recuperación sale del mismo AUGE que alimenta Inicio.
 */
@Composable
fun MuscleVolumePanelRoute(
    programId: String?,
    onBack: () -> Unit,
) {
    val auge = rememberAugeViewModel()
    val viewModel: MuscleVolumePanelViewModel = viewModel(key = "muscle-volume-panel/${programId.orEmpty()}") {
        MuscleVolumePanelViewModel(
            programId = programId,
            recovery = auge.perMuscle.map { perMuscle -> homeBodyValues(perMuscle) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MuscleVolumePanelScreen(state, MuscleVolumePanelActions.from(viewModel, onBack))
}

/** Pantalla completa: fila superior de «Atrás» y el contenido desplazable. */
@Composable
fun MuscleVolumePanelScreen(
    state: MuscleVolumePanelUiState,
    actions: MuscleVolumePanelActions = MuscleVolumePanelActions(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().testTag("muscle-volume-screen")) {
        KpknTopRow(onBack = actions.onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MuscleVolumePanelContent(state, actions, Modifier.padding(horizontal = 16.dp))
            // La barra inferior flota sobre el contenido: aire para que el final de la página no quede debajo.
            Spacer(Modifier.height(120.dp))
        }
    }
}

/**
 * El contenido sin desplazamiento ni insets (lo pinta tal cual la hoja de render): cabecera, vista, ajustes y la lista o el cuerpo.
 * Estados: cargando (esqueleto), sin datos, con datos y con registros sin asignar (línea discreta con el recuento).
 */
@Composable
fun MuscleVolumePanelContent(
    state: MuscleVolumePanelUiState,
    actions: MuscleVolumePanelActions,
    modifier: Modifier = Modifier,
) {
    val panel = state.panel
    Column(modifier = modifier.testTag("muscle-volume-panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            Text("Volumen por músculo", style = KpknType.display, color = WizardColors.text)
            Text(
                text = "Series registradas, no recuperación ni carga.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            if (state.scopeName != null) {
                Text(
                    text = "Solo el programa ${state.scopeName}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag("muscle-volume-scope"),
                )
            }
        }
        KpknSegmentedControl(
            options = listOf("Lista", "Cuerpo"),
            selectedIndex = state.view.ordinal,
            onSelect = { actions.onView(MuscleVolumeView.entries[it]) },
            size = KpknSegmentedSize.Regular,
            modifier = Modifier.testTag("muscle-volume-view"),
        )
        // La recuperación no usa la ventana ni el conteo de series: sus ajustes no se muestran para no mezclar las dos escalas.
        val showControls = !(state.view == MuscleVolumeView.BODY && state.metric == BodyMetric.RECOVERY)
        if (showControls) {
            MuscleVolumeControls(
                request = state.request,
                onWindow = actions.onWindow,
                onAverage = actions.onAverage,
                onMode = actions.onMode,
                onAdjust = actions.onAdjust,
                modifier = Modifier.kpknEntrance("progreso-volumen", 0),
            )
        }
        when {
            state.loading && panel == null -> KpknSkeletonModule(lines = 4)
            state.view == MuscleVolumeView.BODY -> MuscleVolumeBodyModule(
                state = state,
                actions = actions,
                modifier = Modifier.kpknEntrance("progreso-volumen", 1),
            )
            panel == null -> KpknSkeletonModule(lines = 4)
            !panel.hasData -> MuscleVolumeEmpty(days = panel.request.window.days)
            else -> MuscleVolumeListModule(
                panel = panel,
                zones = state.zones,
                expandedMuscle = state.selectedMuscle,
                onToggleMuscle = actions.onToggleMuscle,
                modifier = Modifier.kpknEntrance("progreso-volumen", 1),
            )
        }
        if (panel != null) MuscleVolumeUnassignedNote(panel.unassignedCount)
    }
}
