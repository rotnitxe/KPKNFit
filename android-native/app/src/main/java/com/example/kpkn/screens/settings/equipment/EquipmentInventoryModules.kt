package com.example.kpkn.screens.settings.equipment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.equipment.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*

@Composable
internal fun EquipmentInventorySummary(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions) {
    val summary = state.summary
    KpknModule(Modifier.testTag("equipment-summary").kpknEntrance("equipment", 0)) {
        KpknModuleHeader("Cargas con barra", action = {
            if (!state.loading && state.loadError == null) KpknTextAction("Plantillas", { actions.open(EquipmentSection.TEMPLATES, null) })
        })
        when {
            state.loading -> Text("Leyendo tu material…", style = KpknType.body, color = WizardColors.textMuted)
            state.loadError != null -> Text("Material no disponible", style = KpknType.title, color = WizardColors.text)
            state.summaryLoading -> Text("Comprobando cargas…", style = KpknType.body, color = WizardColors.textMuted)
            state.errors.isNotEmpty() -> Text("Revisa tu material", style = KpknType.title, color = WizardColors.text)
            summary.minKg != null && summary.maxKg != null -> {
                val from = EquipmentInventoryEditor.format(summary.minKg)
                val to = EquipmentInventoryEditor.format(summary.maxKg)
                Text(if (from == to) "$from kg" else "$from – $to kg", style = KpknType.metricL, color = WizardColors.text)
                val step = summary.uniformStepKg
                Text(when {
                    from == to -> "Solo la barra, sin discos disponibles."
                    step != null -> "En saltos de ${EquipmentInventoryEditor.format(step)} kg en total."
                    else -> "Los saltos dependen de la combinación de discos."
                }, style = KpknType.body, color = WizardColors.textMuted)
                if (summary.boundedPreview) Text("Vista comprobada hasta $to kg; no es un tope de tu material.", style = KpknType.caption, color = WizardColors.textMuted)
            }
            else -> {
                Text("Sin barra declarada", style = KpknType.title, color = WizardColors.text)
                Text("Añade tu material para resolver las cargas.", style = KpknType.body, color = WizardColors.textMuted)
            }
        }
        if (!state.loading) {
            Text("${state.draft.dumbbells.count { it.pairAvailable }} pares · ${state.draft.kettlebells.size} kettlebells · ${state.draft.machines.size} máquinas",
                style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(top = 10.dp))
        }
        summary.warnings.forEach { Text(it, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
internal fun EquipmentTemplateChoices(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions) {
    Text("Rellenan el formulario. Ajusta lo que necesites.", style = KpknType.caption, color = WizardColors.textMuted)
    EquipmentInventoryTemplate.entries.forEach { template ->
        EquipmentTemplateRow(template.label, !state.loading && !state.saving && state.loadError == null) { actions.template(template) }
    }
}

@Composable
internal fun EquipmentNotice(text: String, tag: String = "equipment-notice") {
    KpknModule(Modifier.testTag(tag), accent = WizardColors.danger) { EquipmentError(text) }
}

@Composable
internal fun EquipmentError(text: String) {
    Text(text, style = KpknType.caption, color = WizardColors.danger)
}

@Composable
internal fun EquipmentTemplateRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
        Icon(KpknLineIcons.ChevronRight, "Usar $label", tint = WizardColors.textMuted, modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EquipmentBarbellModule(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions) {
    KpknModule(Modifier.kpknEntrance("equipment", 2)) {
        KpknModuleHeader("Barra", action = {
            KpknTextAction("Sin barra", { actions.edit(state.draft.copy(barbellWeightKg = "")) })
        })
        EquipmentField("Peso de la barra (kg)", state.draft.barbellWeightKg,
            { actions.edit(state.draft.copy(barbellWeightKg = it)) }, state.errors["barbell"], "equipment-barbell", !state.saving)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("20", "15", "10").forEach { weight ->
                KpknChip("$weight kg", EquipmentInventoryEditor.number(state.draft.barbellWeightKg) == weight.toDouble(),
                    { actions.edit(state.draft.copy(barbellWeightKg = weight)) }, enabled = !state.saving)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EquipmentStockModule(state: EquipmentInventoryUiState, actions: EquipmentInventoryActions,
    section: EquipmentSection, title: String, description: String) {
    val draft = state.draft
    val labels = when (section) {
        EquipmentSection.PLATES -> draft.plates.map { "${it.weightKg.ifBlank { "—" }} kg · ${if (it.unlimited) "sin límite" else it.countPerSide} por lado" }
        EquipmentSection.DUMBBELLS -> draft.dumbbells.map { "${it.weightKg.ifBlank { "—" }} kg${if (it.pairAvailable) " × 2" else " · sin pareja"}" }
        EquipmentSection.KETTLEBELLS -> draft.kettlebells.map { "${it.ifBlank { "—" }} kg" }
        EquipmentSection.MACHINES -> draft.machines.map { "${it.name.ifBlank { "Sin nombre" }} · ${it.minLoadKg} – ${it.maxLoadKg.ifBlank { "—" }} kg" }
        else -> emptyList()
    }
    KpknModule(Modifier.testTag("equipment-${section.name.lowercase()}").kpknEntrance("equipment", 3 + section.ordinal)) {
        KpknModuleHeader(title, action = { KpknTextAction("Añadir", { actions.open(section, null) }, leadingIcon = KpknLineIcons.Plus) })
        Text(description, style = KpknType.caption, color = WizardColors.textMuted)
        if (section == EquipmentSection.DUMBBELLS) KpknTextAction("Añadir un rango", { actions.open(EquipmentSection.DUMBBELL_RANGE, null) })
        if (labels.isEmpty()) Text("Sin declarar", style = KpknType.body, color = WizardColors.textFaint, modifier = Modifier.padding(top = 14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            labels.forEachIndexed { index, label ->
                val path = section.name.lowercase()
                val hasError = state.errors.keys.any { it.startsWith("$path.$index.") }
                Text(
                    text = if (hasError) "$label · Revisar" else label,
                    style = KpknType.caption,
                    color = if (hasError) WizardColors.danger else WizardColors.text,
                    modifier = Modifier.heightIn(min = 48.dp).background(KpknSurface.inset, KpknShape.pill)
                        .clickable(enabled = !state.saving, role = Role.Button) { actions.open(section, index) }
                        .semantics { contentDescription = "Editar $title: $label" }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
        }
    }
}
