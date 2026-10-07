package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.capabilitySkills
import com.example.kpkn.screens.onboarding.design.entreno.CapabilitySymbols

/**
 * CAPABILITIES · «¿Qué ejercicios ya te salen?»: una figura de palitos por ejercicio de peso corporal que se ofrece con el
 * material (dominadas solo con barra de dominadas; fondos con paralelas o banco), que hace el movimiento con el ritmo de su
 * nivel (Aún no · Algunas · Varias); tocar el símbolo avanza al siguiente nivel y tocar un segmento lo fija.
 *
 * Qué ejercicios se ofrecen sale de `draft.capabilitySkills()` (lo decide `CapabilityRules`); los niveles, de
 * `draft.capabilities`; escribe SOLO con `vm.setCapability(skill, level)`. El motor elige con ellos la variante de flexión,
 * dominada, fondo o sentadilla a una pierna, así que cada ejercicio ofrecido necesita su nivel para confirmar el paso (sin
 * entrada en el mapa = sin responder, y el check sigue apagado).
 */
@Composable
internal fun EntrenoCapabilitiesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    Column(modifier = Modifier.fillMaxWidth()) {
        CapabilitySymbols(skills = draft.capabilitySkills(), levels = draft.capabilities, onLevel = vm::setCapability)
        EntrenoStepNote("Sin presión: siempre podrás cambiarlo.")
    }
}
