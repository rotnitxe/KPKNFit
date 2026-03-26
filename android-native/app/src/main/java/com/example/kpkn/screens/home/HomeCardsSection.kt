package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.components.icons.NutritionIcon

// ─── Home Cards Section ─────────────────────────────────────────────────────
// Parity with PWA HomeCardsSection: macros, biometry, exercise metric cards.

@Composable
fun HomeCardsSection(
    viewModel: HomeViewModel,
    onNavigateToCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // ─── Progreso físico y alimentación ──────────────────────────────────
        SectionHeader("Progreso físico y alimentación", Modifier.padding(horizontal = 24.dp))

        // Macro bars
        MacroProgressBars(viewModel, Modifier.padding(horizontal = 24.dp))

        Spacer(Modifier.height(16.dp))

        // Biometry cards carousel
        BiometryCardsCarousel(viewModel, onNavigateToCard)

        Spacer(Modifier.height(24.dp))

        // ─── Tus ejercicios ─────────────────────────────────────────────────
        SectionHeader("Tus ejercicios", Modifier.padding(horizontal = 24.dp))

        ExerciseMetricCards(viewModel, onNavigateToCard)
    }
}

// ─── Macro Progress Bars ────────────────────────────────────────────────────

@Composable
private fun MacroProgressBars(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val calGoal by viewModel.dailyCalorieGoal.collectAsState()
    val protGoal by viewModel.dailyProteinGoal.collectAsState()
    val carbGoal by viewModel.dailyCarbGoal.collectAsState()
    val fatGoal by viewModel.dailyFatGoal.collectAsState()

    // TODO: Replace with real nutrition log data when available.
    // For now, show goals as reference values.
    val macros = listOf(
        MacroItem("Cal", 0, calGoal, MaterialTheme.colorScheme.primary),
        MacroItem("Prot", 0, protGoal, MaterialTheme.colorScheme.error),
        MacroItem("Carb", 0, carbGoal, MaterialTheme.colorScheme.tertiary),
        MacroItem("Fat", 0, fatGoal, MaterialTheme.colorScheme.secondary),
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            macros.forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            m.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            "${m.current}/${m.goal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (m.current.toFloat() / m.goal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = m.color,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    )
                }
            }
        }
    }
}

private data class MacroItem(
    val label: String,
    val current: Int,
    val goal: Int,
    val color: androidx.compose.ui.graphics.Color,
)

// ─── Biometry Cards Carousel ────────────────────────────────────────────────

@Composable
private fun BiometryCardsCarousel(
    viewModel: HomeViewModel,
    onNavigateToCard: (String) -> Unit,
) {
    val lastWeight by viewModel.lastWeight.collectAsState()
    val lastBodyFat by viewModel.lastBodyFat.collectAsState()
    val lastMusclePct by viewModel.lastMusclePct.collectAsState()
    val height by viewModel.heightCm.collectAsState()

    val ffmiValue: Double? = remember(lastWeight, lastBodyFat, height) {
        if (lastWeight != null && lastBodyFat != null) {
            viewModel.computeNormalizedFfmi(lastWeight!!, height, lastBodyFat!!)
        } else null
    }
    val ffmiInterpretation: String? = remember(lastWeight, lastBodyFat, height) {
        if (lastWeight != null && lastBodyFat != null) {
            viewModel.computeFfmiInterpretation(lastWeight!!, height, lastBodyFat!!)
        } else null
    }

    val weightText: String = lastWeight?.let { String.format("%.1f", it) } ?: "--"
    val ffmiText: String = ffmiValue?.let { String.format("%.1f", it) } ?: "--"
    val imcText: String = lastWeight?.let { w ->
        viewModel.computeImc(w, height)?.let { String.format("%.1f", it) }
    } ?: "--"
    val fatText: String = lastBodyFat?.let { String.format("%.1f", it) } ?: "--"
    val muscleText: String = lastMusclePct?.let { String.format("%.1f", it) } ?: "--"

    val cards = listOf(
        BiometryCardData("Peso", weightText, "kg", "body-progress"),
        BiometryCardData("FFMI", ffmiText, ffmiInterpretation ?: "S/D", "ffmi"),
        BiometryCardData("IMC", imcText, "", "imc"),
        BiometryCardData("% Grasa", fatText, "%", "fat"),
        BiometryCardData("% Músculo", muscleText, "%", "muscle"),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards) { card ->
            BiometryCard(card, onClick = { onNavigateToCard(card.navTarget) })
        }
    }
}

private data class BiometryCardData(
    val title: String,
    val value: String,
    val unit: String,
    val navTarget: String,
)

@Composable
private fun BiometryCard(data: BiometryCardData, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 140.dp, height = 160.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                data.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                letterSpacing = 2.sp,
            )
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        data.value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                    if (data.unit == "%" || data.unit == "kg") {
                        Text(
                            data.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                        )
                    }
                }
                // Subtitle for interpretation (e.g., FFMI level)
                if (data.unit != "%" && data.unit != "kg" && data.unit.isNotEmpty()) {
                    Text(
                        data.unit,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

// ─── Exercise Metric Cards ──────────────────────────────────────────────────

@Composable
private fun ExerciseMetricCards(
    viewModel: HomeViewModel,
    onNavigateToCard: (String) -> Unit,
) {
    val starCount by viewModel.starTargetsCount.collectAsState()
    val strengthData = viewModel.getRelativeStrengthData()

    val cards = listOf(
        ExerciseCardData("Metas 1RM", "$starCount", "Pendientes", "star-targets"),
        ExerciseCardData("Fuerza Relativa", "${String.format("%.2f", strengthData.relativeStrength)}x", "Total: ${String.format("%.0f", strengthData.totalKg)}kg", "relative-strength"),
        ExerciseCardData("Historiales", "--", "Explorar todo", "history"),
        ExerciseCardData("IPF GL", "--", "Puntos", "ipf-gl"),
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards) { card ->
            ExerciseCard(card, onClick = { onNavigateToCard(card.navTarget) })
        }
    }
}

private data class ExerciseCardData(
    val title: String,
    val mainValue: String,
    val subtitle: String,
    val navTarget: String,
)

@Composable
private fun ExerciseCard(data: ExerciseCardData, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 180.dp, height = 130.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                data.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
            )
            Column {
                Text(
                    data.mainValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Text(
                    data.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}
