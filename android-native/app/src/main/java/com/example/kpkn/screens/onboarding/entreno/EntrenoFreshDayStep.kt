package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.FreshDayRow

/**
 * FRESH_DAY · «¿Qué día llegas con más energía?»: los siete días en fila y uno encendido. Ahí cae la sesión más fuerte
 * y, mientras no se mueva el inicio de semana, es el primer día de la semana.
 *
 * Lee `state.draft.freshestDay` y escribe SOLO con `vm.setFreshDay(day)` (1 = lunes … 7 = domingo). La nota «También
 * será el primer día de tu semana.» la trae la propia fila al elegir un día.
 */
@Composable
internal fun EntrenoFreshDayStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    FreshDayRow(selectedDay = state.draft.freshestDay, onSelect = vm::setFreshDay)
}
