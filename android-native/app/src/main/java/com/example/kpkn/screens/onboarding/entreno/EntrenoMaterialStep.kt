package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.entreno.EquipmentSymbolGrid
import com.example.kpkn.screens.onboarding.selectedEquipmentSymbols
import com.example.kpkn.screens.onboarding.design.entreno.EntrenoWarmupEffect

/**
 * AVAILABILITY · «¿Con qué material entrenas?»: un símbolo dibujado por implemento, con «Solo peso corporal» exclusivo.
 *
 * Qué símbolos se ofrecen sale de los lugares elegidos (`EquipmentSymbols.symbolsFor`); lo marcado, de la disponibilidad
 * declarada (`selectedEquipmentSymbols()`: en gimnasio todo lo habitual ya viene marcado). Escribe SOLO con
 * `vm.toggleEquipmentSymbol(symbol)`, que es quien hace exclusivo el cuerpo solo. Una selección vacía es «solo peso
 * corporal» y es válida.
 */
@Composable
internal fun EntrenoMaterialStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val places = state.draft.trainingPlaces
    // Si el bloque se abrió en este paso (se reabre un borrador), también se preparan los dibujos de los siguientes.
    EntrenoWarmupEffect()
    val offered = EquipmentSymbols.symbolsFor(places)
    val selected = state.draft.selectedEquipmentSymbols()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (offered.isEmpty()) {
            EntrenoStepNote("Elige primero dónde entrenas.")
            return@Column
        }
        EquipmentSymbolGrid(symbols = offered, selected = selected, onToggle = vm::toggleEquipmentSymbol)
        EntrenoStepNote(materialNote(places, selected))
    }
}

/**
 * El pie del material (COPY): con «solo peso corporal» elegido, que se entrena con el cuerpo; con gimnasio, que ya viene
 * lo habitual marcado; sin gimnasio, que se marque solo lo que se tiene a mano.
 */
internal fun materialNote(places: Set<TrainingPlace>, selected: Set<EquipmentSymbolId>): String = when {
    EquipmentSymbolId.BODYWEIGHT_ONLY in selected -> "Entrenas con tu cuerpo."
    TrainingPlace.GYM in places -> "En el gimnasio ya contamos con lo habitual. Desmarca lo que no quieras usar."
    else -> "Marca solo lo que tienes a mano."
}
