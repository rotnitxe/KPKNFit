package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY

data class SetupTrainingPlannerInput(
    val goal: String?,
    val frequency: Int?,
    val equipment: Set<String>,
    val level: CatalogLevel,
    val focus: TrainingFocus,
)

/** One adapter for candidate choice; actual materialization remains in the existing engines. */
object SetupTrainingPlanner {
    fun candidates(input: SetupTrainingPlannerInput): List<CatalogEntry> {
        val goal = input.goal?.trim()?.lowercase()
        val entries = PersonalizedPlanCatalog.entries().filter { it.publication == PublicationState.PUBLISHED }
        val strength = goal == "strength" || goal == "fuerza" || goal == "powerlifting"
        val mixed = goal?.contains("cardio") == true
        return entries
            .filter { it.supportedFrequencies.contains(input.frequency ?: it.supportedFrequencies.first) }
            .filter { it.supportedFocuses.contains(input.focus) || it.source == CatalogSource.PROTOCOL }
            .filter { entry -> input.equipment.isEmpty() || entry.requiredEquipment.any { it in input.equipment } || entry.requiredEquipment.contains("general_gym") && "general_gym" in input.equipment }
            .filter { entry -> if (strength) entry.source != CatalogSource.NATIVE || entry.sourceId == "strength-cardio" else true }
            .filter { entry -> if (mixed) entry.source == CatalogSource.NATIVE && entry.sourceId == "strength-cardio" else true }
            .sortedWith(compareBy<CatalogEntry>({ if (strength && it.source == CatalogSource.TEMPLATE) 0 else if (mixed && it.sourceId == "strength-cardio") 0 else 1 }, { if (it.level == input.level) 0 else 1 }, { it.requiredEquipment.size }, { it.id }))
            .take(3)
    }

    fun fixedRecipeIds(): Set<String> = PROTOCOL_LIBRARY.map { it.id }.toSet()
}
