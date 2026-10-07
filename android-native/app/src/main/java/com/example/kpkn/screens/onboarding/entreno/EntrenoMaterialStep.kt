package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.selectedEquipmentSymbols

/**
 * AVAILABILITY · «¿Con qué material entrenas?»: un símbolo por implemento, con «Solo peso corporal» exclusivo.
 *
 * Punto de enganche del control animado (`EquipmentSymbolGrid`): sustituir el cuerpo de este archivo. Qué símbolos se
 * ofrecen sale de los lugares elegidos (`EquipmentSymbols.symbolsFor`); lo marcado, de la disponibilidad declarada
 * (`selectedEquipmentSymbols()`: en gimnasio todo lo habitual ya viene marcado). Escribe SOLO con
 * `vm.toggleEquipmentSymbol(symbol)`. Una selección vacía es «solo peso corporal» y es válida.
 */
@Composable
internal fun EntrenoMaterialStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val places = state.draft.trainingPlaces
    val offered = EquipmentSymbols.symbolsFor(places)
    val selected = state.draft.selectedEquipmentSymbols()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (offered.isEmpty()) {
            EntrenoCaption("Elige primero dónde entrenas.")
            return@Column
        }
        offered.forEach { symbol ->
            val bodyweight = symbol == EquipmentSymbolId.BODYWEIGHT_ONLY
            EntrenoOptionRow(
                label = symbol.label,
                selected = symbol in selected,
                onClick = { vm.toggleEquipmentSymbol(symbol) },
                supporting = if (bodyweight) "Entrenas con tu cuerpo." else null,
                multi = !bodyweight,
                tag = "entreno-symbol-${symbol.name}",
            )
        }
        EntrenoCaption(
            if (TrainingPlace.GYM in places) {
                "En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar."
            } else {
                "Marca solo lo que tienes a mano."
            },
        )
    }
}
