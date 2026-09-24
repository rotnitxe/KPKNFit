package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatMessage

@Composable
fun WizChatMessageBubble(message: WizChatMessage, onEdit: ((WizChatMessage) -> Unit)? = null) {
    // Historical answers size to their text; only the active question owns controls.
    val fromUser = message.fromUser
    val accent = WizChatTokens.stageAccent(message.stage)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Box(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = if (fromUser) 3.dp else 4.dp),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        androidx.compose.material3.Surface(
            color = if (fromUser) WizChatTokens.userBubble else accent,
            shape = if (fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 352.dp).then(
                if (fromUser && onEdit != null && message.questionId != null)
                    Modifier.semantics { contentDescription = "${message.text}. Tocar para cambiar la respuesta" }
                        .combinedClickable(onClick = { onEdit(message) }, onLongClick = { onEdit(message) })
                else Modifier
            ),
        ) {
            if (fromUser) {
                Text(message.text, color = WizChatTokens.text,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            } else {
                Text(message.text, color = Color(0xFF061725),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            }
        }
    }
    }
}
