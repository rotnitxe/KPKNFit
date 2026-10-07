package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * PRIORITIES · «¿Qué músculos quieres mejorar más?»: hasta 5 músculos; omitir es válido.
 *
 * Punto de enganche del control animado (`MuscleSymbolGrid`): sustituir el cuerpo de este archivo. Lee la bolsa de orden
 * (`MuscleSymbols.symbolsOf(draft.trainingOptions.orderPriorities)`) y escribe SOLO con `vm.toggleMuscle(symbol)` y
 * `vm.clearMuscles()` («Omitir»). Las sugerencias del perfil (`MuscleSuggestions`) llegan precargadas mientras la persona
 * no toca el paso (`SetupStepId.PRIORITIES !in draft.declaredSteps`): se rotulan «Sugerido» y nunca se confirman solas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntrenoMusclesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val selected = MuscleSymbols.symbolsOf(draft.trainingOptions.orderPriorities)
    val suggested = selected.isNotEmpty() && SetupStepId.PRIORITIES !in draft.declaredSteps
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // «Omitir» arriba a la derecha, discreto: un texto, no un botón.
        TextButton(
            onClick = { vm.clearMuscles() },
            modifier = Modifier.align(Alignment.End).testTag("entreno-muscles-skip"),
        ) {
            Text("Omitir", style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MuscleSymbol.entries.forEach { symbol ->
                EntrenoChip(
                    label = symbol.label,
                    selected = symbol in selected,
                    onClick = { vm.toggleMuscle(symbol) },
                    multi = true,
                    tag = "entreno-muscle-${symbol.name}",
                )
            }
        }
        when {
            suggested -> EntrenoCaption("Sugerido")
            selected.size >= MuscleSymbols.MAX_SELECTION -> EntrenoCaption("Máximo ${MuscleSymbols.MAX_SELECTION} músculos.")
        }
    }
}
