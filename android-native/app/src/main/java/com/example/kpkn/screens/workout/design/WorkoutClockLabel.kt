package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/**
 * Cronómetro de la fila superior (LS2a, §2 punto 1): la hora de la sesión en `metricM` tabular y, debajo y solo si hay [pace], el
 * ritmo en `caption` con [paceColor]. Sin [pace] no hay segunda línea. Toda la zona (48 dp como mínimo) abre el ajuste de tiempo por
 * [onClick]. Sin estado.
 */
@Composable
fun WorkoutClockLabel(
    clock: String,
    pace: String?,
    paceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val description = if (pace != null) "Tiempo de sesión $clock. $pace" else "Tiempo de sesión $clock"
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Ajustar el tiempo de la sesión",
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = clock,
            style = KpknType.metricM,
            color = WizardColors.text,
            maxLines = 1,
        )
        if (pace != null) {
            // Hasta dos líneas: en 130 % el ritmo se parte entre palabras antes que cortarse (KPKN_UI_V2 §5).
            Text(
                text = pace,
                style = KpknType.caption,
                color = paceColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
