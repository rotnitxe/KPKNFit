package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.programdetail.ProgramSeasonState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

@Composable
internal fun ProgramSeasonModule(state: ProgramSeasonState) {
    if (state.advice.isEmpty()) return
    KpknModule(Modifier.fillMaxWidth(),accent = if (state.advice.any { it.severity == 2 }) SymbolPalette.energia else null) {
        Text("Temporada",style = KpknType.bodyStrong,color = WizardColors.text)
        if (state.input.maintenanceKcal != null && state.input.calorieTarget != null) Text(
            "Objetivo nutricional: ${state.input.calorieTarget} kcal · mantenimiento de referencia del plan: ${state.input.maintenanceKcal} kcal.",
            style = KpknType.caption,color = WizardColors.textMuted)
        state.advice.forEach { advice ->
            Spacer(Modifier.height(10.dp))
            Text(advice.title,style = KpknType.bodyStrong,color = WizardColors.text)
            Text(advice.detail,style = KpknType.body,color = WizardColors.text)
        }
    }
}
