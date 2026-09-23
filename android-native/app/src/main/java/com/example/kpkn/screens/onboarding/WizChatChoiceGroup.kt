package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    val optionsContent: @Composable () -> Unit = {
        options.forEach { option ->
            val isSelected = option in selected
            Surface(
                color = if (isSelected) WizChatTokens.optionSelected else WizChatTokens.option,
                shape = WizChatTokens.optionShape,
                modifier = Modifier
                    .then(if (compact) Modifier else Modifier.fillMaxWidth())
                    .heightIn(min = 48.dp)
                    .clickable(role = if (multi) Role.Checkbox else Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { this.selected = isSelected; this.role = if (multi) Role.Checkbox else Role.RadioButton },
            ) {
                Row(Modifier.then(if (compact) Modifier else Modifier.fillMaxWidth()).padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, color = WizChatTokens.text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.then(if (compact) Modifier else Modifier.weight(1f)))
                    if (isSelected) Text("✓", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (compact) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) { optionsContent() }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) { optionsContent() }
    }
}
