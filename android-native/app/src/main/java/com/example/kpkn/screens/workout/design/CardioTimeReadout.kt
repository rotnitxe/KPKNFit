package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Tiempo del cardio (KPKN LS5b): la cifra en `metricXL` tabular, centrada, y debajo «Transcurrido» o «Restante» en `caption`. Su
 * semántica lee las dos cosas juntas. Sin estado.
 */
@Composable
fun CardioTimeReadout(
    clock: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$caption $clock" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = clock,
            style = KpknType.metricXL,
            color = WizardColors.text,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = caption,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
