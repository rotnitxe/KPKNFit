package com.example.kpkn.screens.programdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GradientTheme(
    val id: String,
    val name: String,
    val colors: List<Color>,
)

val GRADIENT_THEMES = listOf(
    GradientTheme("purple", "Púrpura", listOf(Color(0xFF6750A4), Color(0xFFD0BCFF), Color(0xFFFEF7FF))),
    GradientTheme("sunset", "Cálido", listOf(Color(0xFFFF6B35), Color(0xFFFFB347), Color(0xFFFFF3E0))),
    GradientTheme("ocean", "Frío", listOf(Color(0xFF006994), Color(0xFF4FC3F7), Color(0xFFE1F5FE))),
    GradientTheme("forest", "Bosque", listOf(Color(0xFF2E5D4B), Color(0xFF66BB6A), Color(0xFFE8F5E9))),
    GradientTheme("midnight", "Oscuro", listOf(Color(0xFF1A1A2E), Color(0xFF4A4A6A), Color(0xFF2D2D44))),
    GradientTheme("rose", "Rosa", listOf(Color(0xFFC2185B), Color(0xFFF48FB1), Color(0xFFFCE4EC))),
)

data class FocusOption(val value: String, val label: String)

val FOCUS_OPTIONS = listOf(
    FocusOption("powerlifting", "Powerlifting"),
    FocusOption("powerbuilding", "Powerbuilding"),
    FocusOption("hypertrophy", "Hipertrofia"),
)

@Composable
fun CompactHeroBanner(
    programName: String,
    programDescription: String?,
    isActive: Boolean,
    isPaused: Boolean,
    currentWeekIndex: Int,
    totalWeeks: Int,
    totalAdherence: Int,
    focusMode: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartPause: () -> Unit,
    onFocusChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTheme by remember { mutableStateOf(GRADIENT_THEMES[0]) }
    var showFocusSelector by remember { mutableStateOf(false) }
    var showThemeSelector by remember { mutableStateOf(false) }

    val statusLabel = when {
        isActive -> "Activo"
        isPaused -> "Pausado"
        else -> "Borrador"
    }
    val statusColor = when {
        isActive -> Color(0xFF10B981)
        isPaused -> Color(0xFFF59E0B)
        else -> Color.White.copy(alpha = 0.5f)
    }
    val adherenceValue = if (totalWeeks > 0) totalAdherence else 0
    val isDark = selectedTheme.id == "midnight"
    val labelColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(selectedTheme.colors)),
        ) {
            // Overlay oscuro sutil
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.1f)))

            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                // ─── Nav buttons row ──────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(onClick = { showThemeSelector = !showThemeSelector }) {
                            Text("🎨", fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                statusLabel.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        CircleIconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Editar", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ─── Title + Play/Pause ───────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            programName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2,
                        )
                        if (!programDescription.isNullOrBlank()) {
                            Text(
                                programDescription,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 2,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onStartPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isActive && !isPaused) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(3.dp, 14.dp).background(Color.Black, RoundedCornerShape(1.dp)))
                                Box(Modifier.size(3.dp, 14.dp).background(Color.Black, RoundedCornerShape(1.dp)))
                            }
                        } else {
                            Icon(Icons.Default.PlayArrow, "Iniciar", tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ─── KPIs ─────────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.25f))
                        .padding(top = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Semana
                    KpiColumn("Semana", "${currentWeekIndex + 1} / $totalWeeks", labelColor)
                    // Adherencia
                    KpiColumn("Adherencia", "$adherenceValue%", labelColor)
                    // Enfoque (dropdown)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Enfoque",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = labelColor,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { showFocusSelector = !showFocusSelector }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    FOCUS_OPTIONS.find { it.value == focusMode }?.label ?: focusMode,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
            }
        }

        // ─── Focus Selector Dropdown ──────────────────────────────────────
        if (showFocusSelector) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { showFocusSelector = false },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
            ) {
                Column {
                    Text(
                        "Enfoque",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    FOCUS_OPTIONS.forEach { option ->
                        val isSelected = focusMode == option.value
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) Color.Black else Color.Transparent)
                                .clickable {
                                    onFocusChange(option.value)
                                    showFocusSelector = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                option.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        // ─── Theme Selector Dropdown ──────────────────────────────────────
        if (showThemeSelector) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
            ) {
                Column {
                    Text(
                        "Tema",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GRADIENT_THEMES.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.2f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(theme.colors))
                                    .then(
                                        if (selectedTheme.id == theme.id) Modifier.border(
                                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .clickable { selectedTheme = theme; showThemeSelector = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    theme.name,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun KpiColumn(label: String, value: String, labelColor: Color) {
    Column {
        Text(
            label,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = labelColor,
        )
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
    }
}
