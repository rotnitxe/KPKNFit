package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.MuscleSuggestions
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.MuscleSymbolGrid

/** Marca de prueba del «Omitir» de los músculos. */
internal const val MUSCLES_SKIP_TAG = "setup-muscles-skip"

/**
 * PRIORITIES · «¿Qué músculos priorizas?»: la cuadrícula de los doce músculos dibujados, hasta 5; omitir es válido.
 *
 * Lee la bolsa de orden (`MuscleSymbols.symbolsOf(draft.trainingOptions.orderPriorities)`) y escribe SOLO con
 * `vm.toggleMuscle(symbol)` y `vm.clearMuscles()`. Las sugerencias del perfil (`MuscleSuggestions`) llegan precargadas
 * mientras la persona no toca el paso: la cuadrícula las rotula «Sugerido» hasta que se toca cada una y nunca se confirman
 * solas. Si el paso ya estaba declarado al entrar (se vuelve a editar), no hay nada que sugerir: lo elegido es suyo.
 *
 * «Omitir», arriba a la derecha y en texto discreto, deja el paso sin músculos (también sin las sugerencias) y lo confirma:
 * es una respuesta válida.
 */
@Composable
internal fun EntrenoMusclesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val selected = MuscleSymbols.symbolsOf(draft.trainingOptions.orderPriorities)
    // Se decide al entrar: tocar un músculo declara el paso y las sugerencias aún sin tocar deben seguir rotuladas.
    val suggested = remember(draft.goalProfile) {
        if (SetupStepId.PRIORITIES in draft.declaredSteps) emptySet() else MuscleSuggestions.forProfile(draft.goalProfile)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        MusclesSkip(
            onSkip = {
                vm.clearMuscles()
                vm.submitCurrentStep(SetupStepId.PRIORITIES)
            },
        )
        MuscleSymbolGrid(
            selected = selected,
            suggested = suggested,
            maxSelected = MuscleSymbols.MAX_SELECTION,
            onToggle = vm::toggleMuscle,
        )
    }
}

/** «Omitir»: un texto discreto alineado a la derecha, con un objetivo táctil de 48 dp y sin caja ni resplandor. */
@Composable
private fun MusclesSkip(onSkip: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .testTag(MUSCLES_SKIP_TAG)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Omitir los músculos",
                    onClick = onSkip,
                )
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Omitir", style = WizardTypography.controlLabel, color = WizardColors.textMuted)
        }
    }
}
