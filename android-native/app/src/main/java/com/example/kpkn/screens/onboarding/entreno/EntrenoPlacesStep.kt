package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel

/**
 * EQUIPMENT · «¿Dónde entrenas?»: uno o varios lugares (gimnasio, en casa, en espacios públicos).
 *
 * Punto de enganche del control animado (`PlaceSymbolRow`): sustituir el cuerpo de este archivo. Lee
 * `state.draft.trainingPlaces` y escribe SOLO con `vm.togglePlace(place)` (siempre queda al menos un lugar). El
 * material, el entorno y la disponibilidad se derivan en el reductor; con dos o más lugares cada día de entreno podrá
 * tener el suyo (paso de los días).
 */
@Composable
internal fun EntrenoPlacesStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val selected = state.draft.trainingPlaces
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TrainingPlace.entries.forEach { place ->
            EntrenoOptionRow(
                label = place.label,
                selected = place in selected,
                onClick = { vm.togglePlace(place) },
                tag = "entreno-place-${place.name.lowercase()}",
            )
        }
        when {
            selected.isEmpty() -> EntrenoCaption("Elige al menos un lugar.")
            selected.size >= 2 -> EntrenoCaption("Después podrás elegir dónde entrenas cada día.")
        }
    }
}
