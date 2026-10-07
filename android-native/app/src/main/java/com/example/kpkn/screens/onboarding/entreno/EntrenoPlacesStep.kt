package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.PlaceSymbolRow

/**
 * EQUIPMENT · «¿Dónde entrenas?»: las tres escenas dibujadas (gimnasio, en casa, en espacios públicos), de las que se
 * marca una, dos o las tres.
 *
 * Lee `state.draft.trainingPlaces` y escribe SOLO con `vm.togglePlace(place)` (siempre queda al menos un lugar). El
 * material, el entorno y la disponibilidad se derivan en el reductor; con dos o más lugares cada día de entreno podrá
 * tener el suyo (paso de los días), y así lo dice la nota de abajo.
 */
@Composable
internal fun EntrenoPlacesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val selected = state.draft.trainingPlaces
    Column(modifier = Modifier.fillMaxWidth()) {
        PlaceSymbolRow(selected = selected, onToggle = vm::togglePlace)
        EntrenoStepNote(placesNote(selected.size))
    }
}

/** La nota bajo los lugares: la invitación a elegir uno o, con varios, lo que podrán hacer los días; con uno, ninguna. */
internal fun placesNote(chosen: Int): String? = when {
    chosen <= 0 -> "Elige al menos un lugar."
    chosen >= 2 -> "Después podrás elegir dónde entrenas cada día."
    else -> null
}
