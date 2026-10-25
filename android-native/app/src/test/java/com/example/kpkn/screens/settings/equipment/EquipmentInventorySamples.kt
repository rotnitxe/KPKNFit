package com.example.kpkn.screens.settings.equipment

import com.example.kpkn.domain.equipment.EquipmentInventoryDraft
import com.example.kpkn.domain.equipment.EquipmentInventoryEditor
import com.example.kpkn.domain.equipment.EquipmentInventoryTemplate
import com.example.kpkn.domain.equipment.EquipmentLoadSummary

object EquipmentInventorySamples {
    fun state(name: String): EquipmentInventoryUiState {
        val commercial = EquipmentInventoryEditor.template(EquipmentInventoryTemplate.COMMERCIAL)
        val draft = when (name) {
            "commercial" -> commercial
            "error" -> commercial.copy(barbellWeightKg = "-5")
            else -> EquipmentInventoryDraft()
        }
        val validation = EquipmentInventoryEditor.validate(draft)
        return EquipmentInventoryUiState(loading = false, draft = draft, dirty = name != "empty",
            errors = validation.errors, summary = validation.inventory?.let(EquipmentInventoryEditor::summarize) ?: EquipmentLoadSummary())
    }
}
