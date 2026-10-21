package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextAction

/**
 * Las dos acciones de día de la reutilización (N4), discretas, bajo la línea del estado del día: «Repetir ayer» (o «Repetir el día
 * anterior») y «Copiar día a…». [canCopyDay] es false mientras el día no tiene comidas registradas que copiar. Sin estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReuseDayActions(
    repeatLabel: String,
    canCopyDay: Boolean,
    onRepeat: () -> Unit,
    onCopyDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        KpknTextAction(
            text = repeatLabel,
            onClick = onRepeat,
            modifier = Modifier.testTag("reuse-repeat"),
            leadingIcon = KpknLineIcons.Repeat,
            color = WizardColors.textMuted,
        )
        if (canCopyDay) {
            KpknTextAction(
                text = "Copiar día a…",
                onClick = onCopyDay,
                modifier = Modifier.testTag("reuse-copy-day"),
                leadingIcon = KpknLineIcons.Calendar,
                color = WizardColors.textMuted,
            )
        }
    }
}
