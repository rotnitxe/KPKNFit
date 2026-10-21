package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.SplitWeekOption
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Semanas del programa como círculos (P5): los mismos de la fila de semanas de Program Detail, de 40 dp dentro de una zona táctil de
 * 48 dp. Cada círculo es una casilla que se marca o se desmarca. La elegida se rellena de tinta; la semana actual lleva anillo de tinta.
 * Sustituye al selector de chips (`WeekMultiSelector`). Sin estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitWeekCircles(
    weeks: List<SplitWeekOption>,
    selectedWeekIds: List<String>,
    currentWeekId: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weeks.forEachIndexed { index, week ->
            SplitWeekCircle(
                number = index + 1,
                selected = week.id in selectedWeekIds,
                current = week.id == currentWeekId,
                onClick = { onToggle(week.id) },
            )
        }
    }
}

/** Un círculo de semana: su número en `metricM`, con lectura accesible de si está elegido y de si es la actual. */
@Composable
private fun SplitWeekCircle(
    number: Int,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val fill = if (selected) WizardColors.text else KpknSurface.inset
    val ink = if (selected) WizardColors.ctaContent else WizardColors.textMuted
    val label = if (current) "Semana $number, en curso" else "Semana $number"
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .semantics {
                contentDescription = label
                stateDescription = if (selected) "Elegida" else "No elegida"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(fill, CircleShape)
                .then(if (current && !selected) Modifier.border(2.dp, WizardColors.text, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "$number", style = KpknType.metricM, color = ink, maxLines = 1)
        }
    }
}
