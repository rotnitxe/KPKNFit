package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Fila del checklist de calentamiento y movilidad (KPKN LS5b): 48 dp como mínimo, con el [PrepCheckCircle], el [title] en `bodyStrong`
 * y, debajo, la [caption] (la dosis) en `caption`. Tocar el círculo o el texto marca o desmarca la fila, sin ripple; [trailing] (el peso)
 * va fuera de esa zona táctil, a la derecha. Sin estado.
 */
@Composable
fun PrepChecklistRow(
    title: String,
    state: PrepCheckState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val done = state == PrepCheckState.DONE
    val titleColor = when (state) {
        PrepCheckState.CURRENT -> WizardColors.text
        PrepCheckState.PENDING -> WizardColors.textMuted
        PrepCheckState.DONE -> WizardColors.textFaint
    }
    val stateText = when (state) {
        PrepCheckState.CURRENT -> "Actual"
        PrepCheckState.PENDING -> "Pendiente"
        PrepCheckState.DONE -> "Hecho"
    }
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .toggleable(
                    value = done,
                    role = Role.Checkbox,
                    interactionSource = interaction,
                    indication = null,
                    onValueChange = { onToggle() },
                )
                .semantics { stateDescription = stateText },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrepCheckCircle(state = state)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = KpknType.bodyStrong,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = KpknType.caption,
                        color = if (done) WizardColors.textFaint else WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
