package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType

/**
 * Invitación a calibrar el volumen (P4): un aviso teñido de energía que explica para qué sirve la calibración y ofrece «Calibrar ahora».
 */
@Composable
fun ProgramVolumeCalibrationInvite(
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
    /** Una línea de estado junto al botón, p. ej. «Sin calibrar: las zonas aparecen al calibrar»; null si no hace falta. */
    note: String? = null,
) {
    KpknModule(modifier = modifier, accent = SymbolPalette.energia) {
        Text(
            text = "Calibra tu volumen",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Para decirte si un músculo está quedándose corto, en rango ideal o pasado de volumen, primero necesitamos tu calibración personalizada.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        note?.let { text ->
            Text(
                text = text,
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
        }
        KpknPrimaryPill(
            text = "Calibrar ahora",
            onClick = onCalibrate,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
