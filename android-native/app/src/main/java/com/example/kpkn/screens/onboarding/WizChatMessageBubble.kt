package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.WizChatMessage

@Composable
fun WizChatMessageBubble(message: WizChatMessage, onEdit: ((WizChatMessage) -> Unit)? = null) {
    // Historical answers size to their text; only the active question owns controls.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Box(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = if (message.fromUser) 3.dp else 4.dp),
        contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        androidx.compose.material3.Surface(
            color = if (message.fromUser) WizChatTokens.userBubble else WizChatTokens.botBubble,
            shape = if (message.fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 352.dp).then(
                if (message.fromUser && onEdit != null && message.questionId != null)
                    Modifier.semantics { contentDescription = "${message.text}. Tocar para cambiar la respuesta" }
                        .combinedClickable(onClick = { onEdit(message) }, onLongClick = { onEdit(message) })
                else Modifier
            ),
        ) {
            if (!message.fromUser) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(start = 10.dp)
                            .width(3.dp)
                            .height(28.dp)
                            .background(WizChatTokens.stageAccent(message.stage), RoundedCornerShape(2.dp)),
                    )
                    Text(message.text, color = WizChatTokens.text,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(start = 9.dp, end = 15.dp, top = 11.dp, bottom = 11.dp))
                }
            } else {
                Text(message.text, color = WizChatTokens.text,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp))
            }
        }
    }
    }
}
