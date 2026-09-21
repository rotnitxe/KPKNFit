package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WizChatAnswerDock(
    content: @Composable () -> Unit,
    accent: Color,
    onSkip: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "Continuar",
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Omitir", color = WizChatTokens.muted) }
            if (onAction != null) {
                IconButton(onClick = onAction, modifier = Modifier.size(52.dp)) {
                    Icon(if (actionLabel == "Activar configuración") Icons.Default.Check else Icons.Default.Send, actionLabel, tint = accent)
                }
            }
        }
    }
}
