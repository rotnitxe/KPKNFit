package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso «Revisa la estructura temporal» (P3a): un módulo teñido de `danger`, con el título en `bodyStrong` y hasta tres mensajes del
 * motor en `caption`. Sin [messages] no se pinta nada: la pantalla lo llama solo si hay avisos.
 */
@Composable
internal fun ProgramStructureNotice(
    messages: List<String>,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier, accent = WizardColors.danger) {
        Text(
            text = "Revisa la estructura temporal",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        messages.take(3).forEach { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}
