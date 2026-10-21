package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType

/**
 * Programa sin bloques (P1): la pestaña Semana no tiene nada que enseñar, así que la pantalla da salida con un módulo y la acción
 * principal «Añadir el primer bloque», que lleva a Estructura, donde se crea.
 */
@Composable
internal fun ProgramEmptyStructure(onAddFirstBlock: () -> Unit, modifier: Modifier = Modifier) {
    KpknModule(modifier = modifier.padding(horizontal = 16.dp)) {
        KpknModuleHeader(label = "Estructura")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Aún no hay bloques. Un bloque agrupa semanas con un mismo objetivo.",
            style = KpknType.body,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        KpknPrimaryPill(
            text = "Añadir el primer bloque",
            onClick = onAddFirstBlock,
            modifier = Modifier.fillMaxWidth(),
            tag = "program_empty_add_block",
        )
    }
}
