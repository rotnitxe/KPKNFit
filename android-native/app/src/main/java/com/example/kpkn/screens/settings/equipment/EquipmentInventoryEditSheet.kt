package com.example.kpkn.screens.settings.equipment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.equipment.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.*

@Composable
internal fun EquipmentField(label: String, value: String, onChange: (String) -> Unit,
    error: String? = null, tag: String, enabled: Boolean = true, numeric: Boolean = true) {
    KpknTextField(
        value = value, onValueChange = onChange, label = label, error = error, enabled = enabled,
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) { contentDescription = label },
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable
internal fun EquipmentInventoryEditSheet(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions) {
    val target = state.editing ?: return
    KpknSheet(onDismissRequest = actions.close) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when (target.section) {
                EquipmentSection.PLATES -> "Disco"
                EquipmentSection.DUMBBELLS -> "Mancuernas"
                EquipmentSection.KETTLEBELLS -> "Kettlebell"
                EquipmentSection.MACHINES -> "Máquina"
                EquipmentSection.DUMBBELL_RANGE -> "Rango de mancuernas"
                EquipmentSection.TEMPLATES -> "Puntos de partida"
            }, style = KpknType.title, color = WizardColors.text)
            when (target.section) {
                EquipmentSection.PLATES -> EquipmentPlateFields(state, actions, target.index)
                EquipmentSection.DUMBBELLS -> EquipmentDumbbellFields(state, actions, target.index)
                EquipmentSection.KETTLEBELLS -> EquipmentKettlebellFields(state, actions, target.index)
                EquipmentSection.MACHINES -> EquipmentMachineFields(state, actions, target.index)
                EquipmentSection.TEMPLATES -> EquipmentTemplateChoices(state, actions)
                EquipmentSection.DUMBBELL_RANGE -> {
                    EquipmentField("Desde (kg por unidad)", state.range.fromKg, { actions.range(state.range.copy(fromKg = it)) }, tag = "equipment-range-from")
                    EquipmentField("Hasta (kg por unidad)", state.range.toKg, { actions.range(state.range.copy(toKg = it)) }, tag = "equipment-range-to")
                    EquipmentField("Salto (kg)", state.range.stepKg, { actions.range(state.range.copy(stepKg = it)) }, tag = "equipment-range-step")
                    state.rangeError?.let { EquipmentError(it) }
                    Text("Añade parejas completas y conserva los pesos que ya declaraste.", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            if (target.section == EquipmentSection.DUMBBELL_RANGE) {
                KpknPrimaryPill("Añadir rango", actions.addRange, Modifier.fillMaxWidth())
                KpknTextAction("Cerrar", actions.close)
            } else if (target.section == EquipmentSection.TEMPLATES) {
                KpknTextAction("Cerrar", actions.close)
            } else {
                KpknPrimaryPill("Listo", actions.close, Modifier.fillMaxWidth())
                KpknTextAction("Quitar del material", actions.remove, color = WizardColors.danger)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentPlateFields(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions, index: Int) {
    val row = state.draft.plates.getOrNull(index) ?: return
    fun change(value: PlateDraft) = actions.edit(state.draft.copy(plates = state.draft.plates.mapIndexed { i, old -> if (i == index) value else old }))
    EquipmentField("Peso de cada disco (kg)", row.weightKg, { change(row.copy(weightKg = it)) }, state.errors["plates.$index.weight"], "equipment-plate-weight")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("1,25", "2,5", "5", "10", "15", "20", "25").forEach { weight ->
            KpknChip("$weight kg", EquipmentInventoryEditor.number(row.weightKg) == EquipmentInventoryEditor.number(weight), { change(row.copy(weightKg = weight)) })
        }
    }
    KpknCheckRow(row.unlimited, "Sin límite de cantidad", { change(row.copy(unlimited = !row.unlimited)) })
    if (!row.unlimited) EquipmentField("Cantidad por lado", row.countPerSide, { change(row.copy(countPerSide = it)) },
        state.errors["plates.$index.count"], "equipment-plate-count")
    Text("2 por lado significa 4 discos en total de este peso.", style = KpknType.caption, color = WizardColors.textMuted)
}

@Composable
private fun EquipmentDumbbellFields(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions, index: Int) {
    val row = state.draft.dumbbells.getOrNull(index) ?: return
    fun change(value: DumbbellDraft) = actions.edit(state.draft.copy(dumbbells = state.draft.dumbbells.mapIndexed { i, old -> if (i == index) value else old }))
    EquipmentField("Peso por mancuerna (kg)", row.weightKg, { change(row.copy(weightKg = it)) }, state.errors["dumbbells.$index.weight"], "equipment-dumbbell-weight")
    KpknCheckRow(row.pairAvailable, "Pareja completa disponible", { change(row.copy(pairAvailable = !row.pairAvailable)) },
        subtitle = "Dos mancuernas del mismo peso.")
}

@Composable
private fun EquipmentKettlebellFields(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions, index: Int) {
    val row = state.draft.kettlebells.getOrNull(index) ?: return
    EquipmentField("Peso (kg)", row, { value -> actions.edit(state.draft.copy(kettlebells = state.draft.kettlebells.mapIndexed { i, old -> if (i == index) value else old })) },
        state.errors["kettlebells.$index.weight"], "equipment-kettlebell-weight")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentMachineFields(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions, index: Int) {
    val row = state.draft.machines.getOrNull(index) ?: return
    val path = "machines.$index"
    fun change(value: MachineDraft) = actions.edit(state.draft.copy(machines = state.draft.machines.mapIndexed { i, old -> if (i == index) value else old }))
    EquipmentField("Nombre", row.name, { change(row.copy(name = it)) }, state.errors["$path.name"], "equipment-machine-name", numeric = false)
    Text("Tipo de equipo", style = KpknType.caption, color = WizardColors.textMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EquipmentInventoryEditor.machineKinds.forEach { (kind, label) ->
            KpknChip(label, row.equipmentKind == kind, { change(row.copy(equipmentKind = kind, configurationId = null)) })
        }
    }
    state.errors["$path.kind"]?.let { EquipmentError(it) }
    EquipmentField("Carga mínima (kg)", row.minLoadKg, { change(row.copy(minLoadKg = it)) }, state.errors["$path.min"], "equipment-machine-min")
    EquipmentField("Carga máxima (kg) · obligatoria", row.maxLoadKg, { change(row.copy(maxLoadKg = it)) }, state.errors["$path.max"], "equipment-machine-max")
    EquipmentField("Incremento (kg)", row.incrementKg, { change(row.copy(incrementKg = it)) }, state.errors["$path.increment"], "equipment-machine-increment")
    EquipmentField("Carga base (kg)", row.baseLoadKg, { change(row.copy(baseLoadKg = it)) }, state.errors["$path.base"], "equipment-machine-base")
    Text("Configuración del catálogo · opcional", style = KpknType.bodyStrong, color = WizardColors.text)
    val selected = state.configurations.firstOrNull { it.id == row.configurationId }
    if (row.configurationId != null) {
        Text(selected?.name ?: "Configuración guardada", style = KpknType.body, color = WizardColors.text)
        KpknTextAction("Quitar configuración", { change(row.copy(configurationId = null)) })
    } else {
        Text(if (row.equipmentKind == "machine") "Elige la configuración real para que esta máquina pueda resolver cargas de ese ejercicio."
            else "Sin configuración se usa como estación de este tipo.", style = KpknType.caption, color = WizardColors.textMuted)
    }
    state.errors["$path.configuration"]?.let { EquipmentError(it) }
    EquipmentField("Buscar configuración", state.configurationQuery, actions.configurationQuery, tag = "equipment-configuration-query", numeric = false)
    val matches = state.configurations.filter { it.equipmentKind == row.equipmentKind && it.name.contains(state.configurationQuery.trim(), ignoreCase = true) }
    if (state.configurationQuery.isNotBlank()) {
        if (matches.isEmpty()) Text("Sin coincidencias para este tipo.", style = KpknType.caption, color = WizardColors.textMuted)
        matches.take(8).forEach { configuration ->
            EquipmentTemplateRow(configuration.name, true) {
                change(row.copy(configurationId = configuration.id))
                actions.configurationQuery("")
            }
        }
        if (matches.size > 8) Text("Escribe más para afinar la búsqueda.", style = KpknType.caption, color = WizardColors.textMuted)
    }
}
