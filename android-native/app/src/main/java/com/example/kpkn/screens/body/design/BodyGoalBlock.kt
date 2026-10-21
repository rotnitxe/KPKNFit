package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknType

/**
 * Objetivo de una medida (BODY_V1 §2.2): la barra fina de avance desde el punto de partida hasta la meta (si hay avance medido),
 * «Te faltan 4,5 kg» y el detalle «Meta: 70 kg · ≈ marzo de 2027». Sin estado.
 */
@Composable
fun BodyGoalBlock(
    headline: String,
    detail: String,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (progress != null) {
            KpknProgressBar(progress = progress, height = 6.dp)
            Spacer(Modifier.height(12.dp))
        }
        Text(text = headline, style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(text = detail, style = KpknType.caption, color = WizardColors.textMuted)
    }
}
