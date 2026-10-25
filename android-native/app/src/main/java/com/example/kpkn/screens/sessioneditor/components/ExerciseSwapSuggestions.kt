package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.onboarding.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.*

/** El mismo reparto por lugar del alta; no se deduce material de un aparato que aparezca en el plan. */
internal fun liveSwapAvailability(session: Session?, program: Program?, declared: EquipmentAvailability?): EquipmentAvailability? {
    declared ?: return null
    val place = TrainingPlace.entries.firstOrNull { it.name == session?.placeId } ?: return declared
    val sessions = program?.let { p -> p.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions } +
        p.loops.flatMap { it.sessions } + p.events.flatMap { it.sessions } }.orEmpty()
    val places = sessions.mapNotNull { s -> TrainingPlace.entries.firstOrNull { it.name == s.placeId } }.toSet()
    return if (places.size < 2) declared else PlaceMaterial.byPlace(declared,places)[place] ?: declared
}
internal fun persistedSwapAvoided(program: Program?): Set<AvoidGroup> = program?.avoidedExerciseGroups.orEmpty().mapNotNull { name -> AvoidGroup.entries.firstOrNull { it.name == name } }.toSet()
internal fun replacementSuggestions(exercise: Exercise?, availability: EquipmentAvailability?, avoided: Set<AvoidGroup>, catalog: ExerciseCatalogV2,
    query: String = ""): List<SwapOption> {
    if (exercise?.catalogConfigurationId.isNullOrBlank() || query.isNotBlank()) return emptyList()
    return if(availability == null) ExerciseSwapOptions.optionsForUnknownEquipment(requireNotNull(exercise),catalog,avoided,6)
        else ExerciseSwapOptions.optionsFor(requireNotNull(exercise),availability,catalog,avoided,6)
}
@Composable
internal fun ExerciseSwapSuggestions(options: List<SwapOption>, selectedConfigurationId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if(options.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag("exercise-swap-suggestions"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Sugeridos",style=KpknType.bodyStrong,color=WizardColors.text)
        options.forEach { option ->
            KpknModule(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("swap-suggestion-${option.configId}"),onClick={onSelect(option.configId)}) {
                Text(option.displayName,style=KpknType.bodyStrong,color=WizardColors.text)
                if(option.variant.isNotBlank()) Text(option.variant,style=KpknType.caption,color=WizardColors.textMuted)
                Text((if(option.kind == SwapKind.EQUIVALENT) "Equivalente" else "Mismo patrón") + if(option.configId == selectedConfigurationId) " · Elegido" else "",
                    style=KpknType.caption,color=WizardColors.textMuted)
            }
        }
    }
}
