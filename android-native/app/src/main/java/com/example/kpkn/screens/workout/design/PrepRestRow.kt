package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Descanso entre aproximaciones (KPKN LS5b): «Descanso 01:30» en `bodyStrong`, «Saltar descanso» como acción de texto y, debajo, la
 * barra de lo que queda. Sin estado: [remainingSeconds] y [totalSeconds] los calcula quien la monta.
 */
@Composable
fun PrepRestRow(
    remainingSeconds: Int,
    totalSeconds: Int,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Descanso ${prepClock(remainingSeconds)}",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
            )
            KpknTextAction(text = "Saltar descanso", onClick = onSkip)
        }
        KpknProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            height = 4.dp,
        )
    }
}
