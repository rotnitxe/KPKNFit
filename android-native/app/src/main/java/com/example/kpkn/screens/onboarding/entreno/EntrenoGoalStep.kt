package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingGoalRequirements
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.GoalProfileList

/**
 * GOAL · «¿Cuál es tu objetivo?»: tres perfiles generales («Generales») y siete disciplinas («Disciplinas»), estas
 * condicionadas al material (`TrainingGoalRequirements`), cada una con su símbolo animado.
 *
 * Lee `state.draft.goalProfile` y, por perfil, la razón del bloqueo (`TrainingGoalRequirements.missingText`: una línea,
 * sin culpar); escribe SOLO con `vm.setGoalProfile(profile)`. Un perfil bloqueado no se elige: tocarlo ofrece lo único
 * que lo desbloquea, «Cambiar mi material», que vuelve al paso del material (`vm.editStep(AVAILABILITY)`; al continuar
 * desde ahí la persona regresa a este paso). La validación del paso impide confirmar un perfil incompatible aunque el
 * borrador ya lo traiga (el material pudo cambiar después).
 */
@Composable
internal fun EntrenoGoalStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val availability = draft.trainingOptions.availability
    val blocked = remember(availability) { goalBlockedReasons(availability) }
    GoalProfileList(
        selected = draft.goalProfile,
        blockedReasons = blocked,
        onSelect = vm::setGoalProfile,
        onBlockedTap = { vm.editStep(SetupStepId.AVAILABILITY) },
    )
}

/** Perfil → lo que le falta al material en una línea; un perfil sin entrada está disponible (los generales siempre). */
internal fun goalBlockedReasons(availability: EquipmentAvailability?): Map<TrainingGoalProfile, String> =
    TrainingGoalProfile.entries
        .mapNotNull { profile -> TrainingGoalRequirements.missingText(profile, availability)?.let { profile to it } }
        .toMap()
