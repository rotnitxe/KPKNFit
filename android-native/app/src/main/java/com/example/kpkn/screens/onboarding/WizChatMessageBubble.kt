package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatMessage

@Composable
fun WizChatMessageBubble(message: WizChatMessage, onEdit: ((WizChatMessage) -> Unit)? = null) {
    val tint = message.stage.accent()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalAlignment = if (message.fromUser) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start,
    ) {
        androidx.compose.material3.Surface(
            color = if (message.fromUser) tint.copy(alpha = WizChatTokens.selectedAlpha) else Color.White.copy(alpha = WizChatTokens.bubbleAlpha),
            shape = WizChatTokens.bubbleShape,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(if (message.fromUser) 0.84f else 0.92f),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(message.text, color = WizChatTokens.text, fontWeight = if (message.fromUser) FontWeight.SemiBold else FontWeight.Normal)
                if (message.fromUser && onEdit != null && message.questionId != null) {
                    androidx.compose.material3.TextButton(onClick = { onEdit(message) }, modifier = Modifier.padding(top = 2.dp)) {
                        Text("Cambiar", color = tint)
                    }
                }
            }
        }
    }
}
