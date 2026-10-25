package com.example.kpkn.screens.settings.equipment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.equipment.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.*

data class EquipmentInventoryActions(
    val edit: (EquipmentInventoryDraft) -> Unit = {},
    val template: (EquipmentInventoryTemplate) -> Unit = {},
    val open: (EquipmentSection, Int?) -> Unit = { _, _ -> },
    val close: () -> Unit = {},
    val remove: () -> Unit = {},
    val range: (DumbbellRangeDraft) -> Unit = {},
    val addRange: () -> Unit = {},
    val configurationQuery: (String) -> Unit = {},
    val save: () -> Unit = {},
    val exit: () -> Unit = {},
    val keepEditing: () -> Unit = {},
    val discard: () -> Unit = {},
)

@Composable
fun EquipmentInventoryRoute(
    onBack: () -> Unit,
    viewModel: EquipmentInventoryViewModel = viewModel { EquipmentInventoryViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.exitRequested) { if (state.exitRequested) onBack() }
    BackHandler { viewModel.requestExit() }
    EquipmentInventoryScreen(state, EquipmentInventoryActions(
        viewModel::edit, viewModel::useTemplate, viewModel::openEditor, viewModel::closeEditor,
        viewModel::removeEditingRow, viewModel::editRange, viewModel::addRange, viewModel::configurationQuery,
        viewModel::save, viewModel::requestExit, viewModel::keepEditing, viewModel::discard,
    ))
}

/** Pantalla real sin estado; las hojas editan el mismo borrador que se guarda al final. */
@Composable
fun EquipmentInventoryScreen(
    state: EquipmentInventoryUiState,
    actions: EquipmentInventoryActions = EquipmentInventoryActions(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().imePadding()) {
        KpknTopRow(onBack = actions.exit) {
            Text("AJUSTES", style = KpknType.eyebrow, color = WizardColors.textMuted)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            EquipmentInventoryForm(state, actions, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(24.dp))
        }
        EquipmentInventoryFooter(state, actions, Modifier.padding(horizontal = 16.dp).navigationBarsPadding())
    }
    if (state.editing != null) EquipmentInventoryEditSheet(state, actions)
    if (state.showDiscardDialog) {
        KpknAlertDialog(
            onDismissRequest = actions.keepEditing,
            title = { Text("¿Descartar los cambios?") },
            text = { Text("Tu material guardado se conserva. Los cambios de este formulario se perderán.") },
            confirmButton = { KpknDangerPill("Descartar", actions.discard, tag = "equipment-confirm-discard") },
            dismissButton = { KpknTextAction("Seguir editando", actions.keepEditing) },
        )
    }
}

@Composable
fun EquipmentInventoryForm(
    state: EquipmentInventoryUiState,
    actions: EquipmentInventoryActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Text("Mi material", style = KpknType.display, color = WizardColors.text)
            Text("Cargas reales para tus entrenamientos.", style = KpknType.body, color = WizardColors.textMuted)
        }
        EquipmentInventorySummary(state, actions)
        state.loadError?.let { EquipmentNotice(it) }
        state.saveError?.let { EquipmentNotice(it) }
        if (state.errors.isNotEmpty()) EquipmentNotice("Revisa los campos marcados antes de guardar.", "equipment-validation")
        if (!state.loading && state.loadError == null) {
            EquipmentBarbellModule(state, actions)
            EquipmentStockModule(state, actions, EquipmentSection.PLATES, "Discos", "Cantidad por lado; una unidad equivale a dos discos.")
            EquipmentStockModule(state, actions, EquipmentSection.DUMBBELLS, "Mancuernas", "Peso por unidad; declara si tienes la pareja completa.")
            EquipmentStockModule(state, actions, EquipmentSection.KETTLEBELLS, "Kettlebells", "Pesos individuales disponibles.")
            EquipmentStockModule(state, actions, EquipmentSection.MACHINES, "Máquinas", "Rangos reales de cada máquina o estación.")
            KpknModule(Modifier.kpknEntrance("equipment", 7)) {
                KpknModuleHeader("Material auxiliar")
                (EquipmentInventoryEditor.supportKinds + state.draft.supportEquipment.filterNot { it in EquipmentInventoryEditor.supportKinds }
                    .associateWith { "Material no reconocido: $it" }).forEach { (id, label) ->
                    KpknCheckRow(
                        checked = id in state.draft.supportEquipment,
                        title = label,
                        enabled = !state.saving,
                        onClick = { actions.edit(state.draft.copy(supportEquipment = if (id in state.draft.supportEquipment)
                            state.draft.supportEquipment - id else state.draft.supportEquipment + id)) },
                    )
                }
                state.errors["support"]?.let { EquipmentError(it) }
            }
            KpknModule(Modifier.kpknEntrance("equipment", 8)) {
                KpknModuleHeader("Puntos de partida")
                EquipmentTemplateChoices(state, actions)
            }
        }
    }
}

@Composable
fun EquipmentInventoryFooter(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KpknPrimaryPill(
            text = if (state.saving) "Guardando…" else "Guardar",
            onClick = actions.save,
            modifier = Modifier.fillMaxWidth(),
            tag = "equipment-save",
            enabled = !state.loading && !state.saving && state.loadError == null && state.errors.isEmpty(),
        )
        KpknTextAction("Descartar", actions.exit, Modifier.testTag("equipment-discard").fillMaxWidth())
    }
}
