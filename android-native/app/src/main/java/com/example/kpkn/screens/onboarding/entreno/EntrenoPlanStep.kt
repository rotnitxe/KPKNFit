package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.TrainingPlanStep

/**
 * PLAN · «Tu programa a medida» (perfil general) / «Elige tu programa» (disciplina): el programa del perfil elegido.
 *
 * De momento delega en el control actual de candidatos (`TrainingPlanStep`: tarjetas de los programas viables con su
 * explicación, avisos de rechazo con reparación de un toque y «lo armaré más adelante»). Punto de enganche del
 * revelado animado: sustituir el cuerpo de este archivo. Lee `state.planCandidates` / `state.draft.selectedCatalogId` y
 * escribe SOLO con `vm.selectPlan(id)` y `vm.deferProgramUntilLater()`; el título cambia con el perfil en
 * `wizardPageCopy`.
 */
@Composable
internal fun EntrenoPlanStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    TrainingPlanStep(state = state, vm = vm)
}
