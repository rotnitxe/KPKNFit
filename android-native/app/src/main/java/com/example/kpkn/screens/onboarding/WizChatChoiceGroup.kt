package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback

/**
 * Options always flow horizontally (rows wrap), so short choices sit side by
 * side instead of stacking one full-width row each. Chips are glass: when a
 * haze source is available they blur what scrolls behind them, otherwise they
 * fall back to the canonical near-opaque mica.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizChatChoiceGroup(
    options: List<String>,
    selected: Set<String> = emptySet(),
    multi: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    compact: Boolean = false,
    onSelect: (String) -> Unit,
) {
    val haze = LocalHazeState.current
    val chipShape: Shape = WizChatTokens.optionShape
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option in selected
            val border = if (isSelected) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f)
            Row(
                modifier = Modifier
                    .kpknGlassOrFallback(
                        haze,
                        chipShape,
                        withBorder = false,
                        additionalScrim = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent,
                    )
                    .border(1.dp, border, chipShape)
                    .heightIn(min = 42.dp)
                    .clickable(
                        role = if (multi) Role.Checkbox else Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .semantics { this.selected = isSelected; this.role = if (multi) Role.Checkbox else Role.RadioButton }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option,
                    color = WizChatTokens.text,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
                if (isSelected) Text(
                    "✓",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
    }
}
