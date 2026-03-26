package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.components.icons.CaupolicanIcon

// ─── Programs Section ───────────────────────────────────────────────────────
// Parity with PWA "Tus Programas" horizontal scroll.

@Composable
fun HomeProgramsSection(
    programs: List<Program>,
    onProgramClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Tus Programas", Modifier.padding(horizontal = 24.dp))

        if (programs.isEmpty()) {
            EmptyProgramsCard(Modifier.padding(horizontal = 24.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(programs, key = { it.id }) { program ->
                    ProgramCard(
                        program = program,
                        onClick = { onProgramClick(program.id) },
                    )
                }
            }
        }
    }
}

// ─── Program Card ───────────────────────────────────────────────────────────

@Composable
private fun ProgramCard(program: Program, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 176.dp, height = 112.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CaupolicanIcon(
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                size = 40.dp,
            )
        }
    }
    // Program name below card
    Text(
        program.name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp).width(176.dp),
        maxLines = 1,
    )
}

// ─── Empty State ────────────────────────────────────────────────────────────

@Composable
private fun EmptyProgramsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Crea tu primer programa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}
