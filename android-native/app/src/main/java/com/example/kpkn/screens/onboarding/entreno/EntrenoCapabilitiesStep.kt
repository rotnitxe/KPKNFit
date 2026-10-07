package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.capabilitySkills

/**
 * CAPABILITIES · «¿Qué ejercicios ya te salen?»: el nivel (Aún no · Algunas · Varias) de cada ejercicio de peso corporal
 * que se ofrece con el material (dominadas solo con barra de dominadas; fondos con paralelas o banco).
 *
 * Punto de enganche del control animado (`CapabilitySymbols`): sustituir el cuerpo de este archivo. Qué ejercicios se
 * ofrecen sale de `draft.capabilitySkills()`; los niveles, de `draft.capabilities`; escribe SOLO con
 * `vm.setCapability(skill, level)`. El motor elige con ellos la variante de flexión, dominada, fondo o sentadilla a una
 * pierna, así que cada ejercicio ofrecido necesita su nivel para confirmar el paso.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntrenoCapabilitiesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val skills = state.draft.capabilitySkills()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        skills.forEach { skill ->
            val level = state.draft.capabilities[skill]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EntrenoSectionLabel(skill.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CapabilityLevel.entries.forEach { option ->
                        EntrenoChip(
                            label = option.label,
                            selected = level == option,
                            onClick = { vm.setCapability(skill, option) },
                            description = "${skill.label}: ${option.label}",
                            tag = "entreno-capability-${skill.name}-${option.name}",
                        )
                    }
                }
            }
        }
        EntrenoCaption("Sin presión: siempre podrás cambiarlo.")
    }
}
