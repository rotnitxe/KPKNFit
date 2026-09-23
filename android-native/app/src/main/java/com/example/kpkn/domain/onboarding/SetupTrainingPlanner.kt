package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY

data class SetupTrainingPlannerInput(
    val reference: TrainingReference?,
    val frequency: Int?,
    val equipment: Set<String>,
    val level: CatalogLevel,
    val focus: TrainingFocus,
    val protocolOnly: Boolean = false,
    val mixedTraining: Boolean = false,
)

/**
 * One adapter for candidate choice; actual materialization remains in the
 * existing engines. Candidates are selected by their real discipline metadata,
 * never by their provenance: a template or protocol is not strength by default.
 */
object SetupTrainingPlanner {
    fun candidates(input: SetupTrainingPlannerInput): List<CatalogEntry> {
        val entries = PersonalizedPlanCatalog.entries().filter { it.publication == PublicationState.PUBLISHED }
        return entries
            .filter { !input.protocolOnly || it.source == CatalogSource.PROTOCOL }
            .filter { it.supportedFrequencies.contains(input.frequency ?: it.supportedFrequencies.first) }
            .filter { it.supportedFocuses.contains(input.focus) || it.source == CatalogSource.PROTOCOL }
            // Native exercise compatibility is decided by SimpleCyclePersonalizer.
            // Fixed recipes cannot substitute missing required equipment.
            .filter { entry -> entry.source == CatalogSource.NATIVE || "general_gym" in input.equipment ||
                entry.requiredEquipment.all { it in input.equipment } }
            // A strength + cardio goal only qualifies plans that schedule cardio;
            // the chosen reference then orders them instead of hiding them.
            .filter { entry -> !input.mixedTraining || entry.schedulesCardio }
            .filter { entry -> input.mixedTraining || input.reference == null || input.reference in entry.references }
            .sortedWith(compareBy<CatalogEntry>(
                { if (input.reference != null && input.reference in it.references) 0 else 1 },
                { if (it.level == input.level) 0 else 1 },
                { it.requiredEquipment.size },
                { it.id },
            ))
    }

    fun fixedRecipeIds(): Set<String> = PROTOCOL_LIBRARY.map { it.id }.toSet()
}
