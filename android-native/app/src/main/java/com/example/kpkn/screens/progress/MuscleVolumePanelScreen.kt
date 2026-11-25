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

/** Lo que la pantalla puede pedir al ViewModel; con valores por defecto vacíos para pintarla con datos de ejemplo. */
data class MuscleVolumePanelActions(
    val onBack: () -> Unit = {},
    val onWindow: (MuscleVolumeWindow) -> Unit = {},
    val onAverage: (Boolean) -> Unit = {},
    val onMode: (VolumeCountingMode) -> Unit = {},
    val onAdjust: (Boolean) -> Unit = {},
    val onView: (MuscleVolumeView) -> Unit = {},
    val onSide: (BodySide) -> Unit = {},
    val onToggleMuscle: (String) -> Unit = {},
    val onSelectRegion: (String) -> Unit = {},
    val onMetricMode: (MusclePanelMetricMode) -> Unit = {},
) {
    companion object {
        fun from(viewModel: MuscleVolumePanelViewModel, onBack: () -> Unit): MuscleVolumePanelActions = MuscleVolumePanelActions(
            onBack = onBack,
            onWindow = viewModel::selectWindow,
            onAverage = viewModel::selectAverage,
            onMode = viewModel::selectMode,
            onAdjust = viewModel::selectAdjustByIntensity,
            onView = viewModel::selectView,
            onSide = viewModel::selectSide,
            onToggleMuscle = viewModel::toggleMuscle,
            onSelectRegion = viewModel::selectRegion,
            onMetricMode = viewModel::selectMetricMode,
        )
    }
}

/**
 * Ruta del panel desde la navegación. [programId] (o null) viene del enlace de la pestaña Volumen del programa; sin él cuentan los
 * registros de todos los programas.
 */
@Composable
fun MuscleVolumePanelRoute(
    programId: String?,
    onBack: () -> Unit,
) {
    val viewModel: MuscleVolumePanelViewModel = viewModel(key = "muscle-volume-panel/${programId.orEmpty()}") {
        MuscleVolumePanelViewModel(
            programId = programId,
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
            Text(
                text = if (state.metricMode == MusclePanelMetricMode.INTENSITY) "Intensidad media por músculo" else "Volumen por músculo",
                style = KpknType.display,
                color = WizardColors.text,
            )
            Text(
                text = if (state.metricMode == MusclePanelMetricMode.INTENSITY) {
                    "RIR global / RPE ponderado (1,0 · 0,5 · 0,3) y factores limitantes."
                } else {
                    "Series registradas, no recuperación ni carga."
                },
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
            options = listOf("Volumen", "Intensidad"),
            selectedIndex = state.metricMode.ordinal,
            onSelect = { actions.onMetricMode(MusclePanelMetricMode.entries[it]) },
            size = KpknSegmentedSize.Compact,
            modifier = Modifier.testTag("muscle-panel-section-toggle"),
        )
        KpknSegmentedControl(
            options = listOf("Lista", "Cuerpo"),
            selectedIndex = state.view.ordinal,
            onSelect = { actions.onView(MuscleVolumeView.entries[it]) },
            size = KpknSegmentedSize.Regular,
            modifier = Modifier.testTag("muscle-volume-view"),
        )
        run {
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
            state.metricMode == MusclePanelMetricMode.INTENSITY -> MuscleIntensityPanelContent(
                state = state,
                actions = actions,
                modifier = Modifier.kpknEntrance("progreso-intensidad", 1),
            )
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
        if (panel != null && state.metricMode == MusclePanelMetricMode.VOLUME) {
            MuscleVolumeUnassignedNote(panel.unassignedCount)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MuscleIntensityPanelContent(
    state: MuscleVolumePanelUiState,
    actions: MuscleVolumePanelActions,
    modifier: Modifier = Modifier,
) {
    val summary = state.intensitySummary
    if (summary == null || (!summary.hasIntensityData && summary.limitingFactorTallies.isEmpty())) {
        MuscleVolumeEmpty(days = state.request.window.days)
        return
    }
    Column(modifier = modifier.testTag("muscle-intensity-panel"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.top3Muscles.isNotEmpty()) {
            com.example.kpkn.ui.design.KpknModule {
                com.example.kpkn.ui.design.KpknModuleHeader("Top 3 intensidad media")
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    summary.top3Muscles.forEachIndexed { idx, row ->
                        androidx.compose.foundation.layout.Column(
                            modifier = androidx.compose.ui.Modifier
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("#${idx + 1} ${row.muscleName}", style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                            Text(
                                "RPE ${com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.formatDecimal1(row.weightedRpeMean)}",
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                            Text(
                                "RIR ${com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.formatDecimal1(row.weightedRirMean)}" +
                                    if (row.failureSetCount > 0) " · Fallo ×${row.failureSetCount}" else "",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                    }
                }
            }
        }

        if (state.view == MuscleVolumeView.BODY) {
            val paints = androidx.compose.runtime.remember(summary) {
                com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.paints(summary)
            }
            val descriptions = androidx.compose.runtime.remember(summary) {
                com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.regionDescriptions(summary)
            }
            val descSummary = androidx.compose.runtime.remember(summary) {
                com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.summaryDescription(summary)
            }
            com.example.kpkn.ui.design.KpknModule(modifier = Modifier.testTag("muscle-intensity-body-module")) {
                com.example.kpkn.ui.design.KpknModuleHeader("Mapa de calor muscular")
                Spacer(Modifier.height(8.dp))
                KpknSegmentedControl(
                    options = listOf("Frente", "Espalda"),
                    selectedIndex = state.side.ordinal,
                    onSelect = { actions.onSide(BodySide.entries[it]) },
                    size = KpknSegmentedSize.Compact,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally),
                ) {
                    com.example.kpkn.screens.progress.design.MuscleVolumeBodyMap(
                        side = state.side,
                        paints = paints,
                        selected = state.selectedMuscle,
                        onSelect = actions.onSelectRegion,
                        summary = descSummary,
                        regionDescriptions = descriptions,
                    )
                }
                Spacer(Modifier.height(10.dp))
                state.selectedMuscle?.let { selectedName ->
                    val row = summary.muscles.firstOrNull { it.muscleName == selectedName }
                    Text(
                        text = if (row != null) {
                            "$selectedName · RPE ${com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.formatDecimal1(row.weightedRpeMean)} · RIR ${com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.formatDecimal1(row.weightedRirMean)} (${row.ratedSetCount} series, ${row.failureSetCount} al fallo)"
                        } else {
                            "$selectedName · Sin series con RIR/RPE en esta ventana."
                        },
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        } else {
            com.example.kpkn.ui.design.KpknModule(modifier = Modifier.testTag("muscle-intensity-list-module")) {
                com.example.kpkn.ui.design.KpknModuleHeader("Intensidad por grupo muscular")
                Spacer(Modifier.height(8.dp))
                summary.muscles.forEach { row ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.muscleName, style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
                            Text(
                                "RPE ${com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.formatDecimal1(row.weightedRpeMean)} · RIR ${com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel.formatDecimal1(row.weightedRirMean)}" +
                                    if (row.failureSetCount > 0) " · Fallo ×${row.failureSetCount}" else "",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                        if (row.topExercises.isNotEmpty()) {
                            Text(
                                row.topExercises.joinToString(" · "),
                                style = KpknType.caption,
                                color = WizardColors.textFaint,
                            )
                        }
                    }
                }
            }
        }

        if (summary.limitingFactorTallies.isNotEmpty()) {
            com.example.kpkn.ui.design.KpknModule(modifier = Modifier.testTag("muscle-intensity-bottlenecks")) {
                com.example.kpkn.ui.design.KpknModuleHeader("Factores limitantes reportados")
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    summary.limitingFactorTallies.forEach { tally ->
                        val note = if (tally.linkedMuscleNames.isNotEmpty()) " → ${tally.linkedMuscleNames.joinToString(", ")}" else ""
                        com.example.kpkn.ui.design.KpknChip(
                            text = "${tally.label} (×${tally.count})$note",
                            selected = tally.autoLinkedCoreCount > 0,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
