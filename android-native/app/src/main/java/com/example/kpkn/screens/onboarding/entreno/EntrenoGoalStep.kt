package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.onboarding.TrainingGoalRequirements
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * GOAL · «¿Cuál es tu objetivo?»: tres perfiles generales («Generales») y siete disciplinas («Disciplinas»), estas
 * condicionadas al material (`TrainingGoalRequirements`).
 *
 * Punto de enganche del control animado: sustituir el cuerpo de este archivo. Lee `state.draft.goalProfile` y, por
 * perfil, `TrainingGoalRequirements.isCompatible(profile, availability)` / `.missingText(...)` (la razón del bloqueo en
 * una línea, sin culpar); escribe SOLO con `vm.setGoalProfile(profile)`. Un perfil bloqueado no se elige: la acción es
 * «Cambiar mi material» (`vm.editStep(SetupStepId.AVAILABILITY)`). La validación del paso impide confirmar un perfil
 * incompatible aunque el borrador ya lo traiga (el material pudo cambiar después).
 */
@Composable
internal fun EntrenoGoalStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val availability = draft.trainingOptions.availability
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EntrenoSectionLabel("Generales")
        TrainingGoalProfile.general.forEach { profile ->
            GoalProfileRow(profile, draft.goalProfile == profile, availability, vm)
        }
        EntrenoSectionLabel("Disciplinas", modifier = Modifier.padding(top = 16.dp))
        EntrenoCaption("Dependen de tu material")
        TrainingGoalProfile.specific.forEach { profile ->
            GoalProfileRow(profile, draft.goalProfile == profile, availability, vm)
        }
        val anyBlocked = TrainingGoalProfile.specific.any { !TrainingGoalRequirements.isCompatible(it, availability) }
        if (anyBlocked || draft.goalProfile?.let { !TrainingGoalRequirements.isCompatible(it, availability) } == true) {
            TextButton(
                onClick = { vm.editStep(SetupStepId.AVAILABILITY) },
                modifier = Modifier.testTag("entreno-goal-change-material"),
            ) {
                Text("Cambiar mi material", color = WizardColors.text, style = WizardTypography.cardTitle)
            }
        }
    }
}

@Composable
private fun GoalProfileRow(
    profile: TrainingGoalProfile,
    selected: Boolean,
    availability: com.example.kpkn.data.models.EquipmentAvailability?,
    vm: SetupWizardViewModel,
) {
    val missing = TrainingGoalRequirements.missingText(profile, availability)
    EntrenoOptionRow(
        label = profile.label,
        selected = selected,
        // Un perfil bloqueado no se elige; el aviso dice qué le falta al material.
        enabled = missing == null,
        onClick = { vm.setGoalProfile(profile) },
        supporting = missing ?: profile.tagline,
        multi = false,
        tag = "entreno-goal-${profile.name.lowercase()}",
    )
}

/** Rótulo de un tramo de la lista («Generales», «Disciplinas»). */
@Composable
internal fun EntrenoSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = WizardTypography.controlLabel,
        color = WizardColors.text,
        modifier = modifier.fillMaxWidth(),
    )
}
