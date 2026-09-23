package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Presentation-only carousel: one main card and a peek of the next. Sliding
 * never selects; only the explicit button sends the chosen plan id.
 */
@Composable
fun WizChatPlanCarousel(
    candidates: List<SetupPlanCandidate>,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth * 0.84f
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(candidates, key = { it.id }) { candidate ->
                WizChatPlanCard(candidate, accent, cardWidth, onSelect = { onSelect(candidate.id) })
            }
        }
    }
}

@Composable
private fun WizChatPlanCard(
    candidate: SetupPlanCandidate,
    accent: Color,
    cardWidth: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit,
) {
    var showDetails by remember(candidate.id) { mutableStateOf(false) }
    Surface(
        color = Color.White.copy(alpha = .08f),
        shape = WizChatTokens.optionShape,
        modifier = Modifier
            .width(cardWidth)
            .heightIn(min = 240.dp, max = 340.dp)
            .semantics { contentDescription = "Plan ${candidate.title}" },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(candidate.title, color = WizChatTokens.text, fontWeight = FontWeight.Bold)
            Text(candidate.subtitle, color = accent)
            Text(candidate.description, color = WizChatTokens.muted)
            candidate.reasons.forEach { reason ->
                Text("✓ $reason", color = WizChatTokens.text)
            }
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF061725)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Elegir este plan")
            }
            if (!candidate.details.isNullOrBlank()) {
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Ocultar detalles" else "Ver detalles", color = accent)
                }
                if (showDetails) Text(candidate.details, color = WizChatTokens.muted)
            }
        }
    }
}
