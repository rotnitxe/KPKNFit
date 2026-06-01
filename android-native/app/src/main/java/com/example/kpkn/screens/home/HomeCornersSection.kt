package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.components.icons.PowerlifterCornerIcon
import com.example.kpkn.ui.components.icons.WikiIcon

// ─── Rincones Section ───────────────────────────────────────────────────────
// Parity with PWA "Rincones" section: Powerlifter Corner + WikiLab.

@Composable
fun HomeCornersSection(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        SectionHeader("Rincones")

        Spacer(Modifier.height(4.dp))

        // Powerlifter Corner
        CornerCard(
            title = "Powerlifter Corner",
            subtitle = "Federaciones, historial y competiciones.",
            icon = {
                PowerlifterCornerIcon(
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    size = 32.dp,
                )
            },
            onClick = { onNavigate("powerlifter-corner") },
        )

        Spacer(Modifier.height(12.dp))

        // Enciclopedia
        CornerCard(
            title = "Enciclopedia",
            subtitle = "Ciencia del entrenamiento y biomecánica.",
            icon = {
                WikiIcon(tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            },
            onClick = { onNavigate("wiki-home") },
        )
    }
}

// ─── Corner Card ────────────────────────────────────────────────────────────

@Composable
private fun CornerCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon container
            Box(
                Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Box(
                        Modifier.size(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                }
            }

            // Text content
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}
