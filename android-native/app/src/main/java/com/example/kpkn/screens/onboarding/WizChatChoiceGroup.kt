package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@Composable
fun WizChatChoiceGroup(
    options: List<String>,
    selected: Set<String> = emptySet(),
    multi: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val isSelected = option in selected
            Surface(
                color = if (isSelected) accent.copy(alpha = WizChatTokens.selectedAlpha) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                shape = WizChatTokens.optionShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(role = if (multi) Role.Checkbox else Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { this.selected = isSelected; this.role = if (multi) Role.Checkbox else Role.RadioButton },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, color = WizChatTokens.text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    if (isSelected) Text("✓", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
