package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.onboarding.CardioChoice
import com.example.kpkn.domain.onboarding.CardioChoices
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupCheckCard
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.cardioChoice
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.trainingAt

/**
 * CARDIO_TYPE · «¿Qué cardio quieres incluir?»: las respuestas que el material de la persona permite, no una lista fija.
 *
 * Qué se ofrece sale de [CardioChoices.optionsFor], que lee el equipo de cada lugar con el mismo aparato que usa el
 * generador al armar los días: caminar y correr al aire libre siempre; la bicicleta al aire libre solo si la persona marca
 * «Tengo bicicleta» (la casilla del pie, que escribe la llave `outdoor_bike` con `vm.setOutdoorBike`); y, con el símbolo
 * «Cardio» en algún lugar, la cinta, la bicicleta estática, la elíptica, el remo en máquina y «Lo que haya». Escribe con
 * `vm.setStepChoice(CARDIO_TYPE, valor)` (el valor es el nombre de [CardioChoice]).
 *
 * Una respuesta que el material de ahora ya no permite (se quitó «Cardio», se quitó la bicicleta) no se pinta entre las
 * opciones ni se cambia sola: la nota dice qué falta y el paso no deja continuar hasta elegir otra.
 */
@Composable
internal fun EntrenoCardioTypeStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val places = draft.trainingPlaces
    val availability = draft.trainingOptions.availability
    val options = remember(places, availability) { CardioChoices.optionsFor(places, availability) }
    val current = draft.cardioChoice()
    val hasBike = SetupApparatusPanel.hasBike(availability)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            options.forEach { option ->
                WizardChoiceCard(
                    title = option.label,
                    subtitle = option.hint,
                    selected = option == current,
                    // Re-pulsar la elegida no la deselecciona (selección única).
                    onClick = { if (option != current) vm.setStepChoice(SetupStepId.CARDIO_TYPE, option.name) },
                )
            }
            SetupCheckCard(
                title = BIKE_TITLE,
                subtitle = BIKE_HINT,
                checked = hasBike,
                onClick = { vm.setOutdoorBike(!hasBike) },
            )
        }
        EntrenoStepNote(cardioNote(current, places, availability))
    }
}

internal const val BIKE_TITLE = "Tengo bicicleta"
internal const val BIKE_HINT = "Para salir a pedalear al aire libre."

/**
 * La nota del paso (COPY · Cardio). Si lo elegido ya no está disponible, dice qué falta; si es un aparato y algún lugar
 * elegido no lo tiene, dice dónde sí está y que en los demás se hará otro cardio. «Lo que haya» no necesita nota: elige
 * según el material de cada día. Sin nada que decir, ninguna (la nota no ocupa sitio).
 */
internal fun cardioNote(current: CardioChoice?, places: Set<TrainingPlace>, availability: EquipmentAvailability?): String? {
    val choice = current ?: return null
    if (!CardioChoices.isOffered(choice, places, availability)) return CardioChoices.unavailableReason(choice)
    if (!choice.isMachine) return null
    val without = CardioChoices.placesWithoutMachines(places, availability)
    if (without.isEmpty()) return null
    val with = TrainingPlace.entries.filter { it in places && it !in without }
    return "Ese aparato solo está ${joinPlaces(with)}. ${joinPlaces(without).replaceFirstChar { it.uppercaseChar() }}, haremos otro cardio."
}

/** «en el gimnasio», «en el gimnasio y en casa», «en el gimnasio, en casa y en espacios públicos». */
private fun joinPlaces(places: List<TrainingPlace>): String {
    val items = places.map { it.trainingAt() }
    return if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " y " + items.last()
}
