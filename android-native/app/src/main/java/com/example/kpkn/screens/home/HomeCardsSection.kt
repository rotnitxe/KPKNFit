package com.example.kpkn.screens.home

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.SectionHeader

private val HomeCardDark = Color(0xFF1C1C1E)
private val HomeCardDarkAlt = Color(0xFF242426)

@Composable
fun HomeCardsSection(
    viewModel: HomeViewModel,
    onNavigateToCard: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddMeal: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Progreso físico y alimentación", Modifier.padding(horizontal = 24.dp))
        MacroProgressBars(viewModel, onAddMeal, Modifier.padding(horizontal = 24.dp))

        Spacer(Modifier.height(12.dp))

        BiometryCardsCarousel(viewModel, onNavigateToCard)

        Spacer(Modifier.height(18.dp))

        SectionHeader("Tus ejercicios", Modifier.padding(horizontal = 24.dp))

        ExerciseMetricCards(viewModel, onNavigateToCard)
    }
}

// ─── Macro Progress Bars ────────────────────────────────────────────────────

@Composable
private fun MacroProgressBars(viewModel: HomeViewModel, onAddMeal: () -> Unit = {}, modifier: Modifier = Modifier) {
    val calGoal by viewModel.dailyCalorieGoal.collectAsState()
    val protGoal by viewModel.dailyProteinGoal.collectAsState()
    val carbGoal by viewModel.dailyCarbGoal.collectAsState()
    val fatGoal by viewModel.dailyFatGoal.collectAsState()
    val nutritionToday by viewModel.todayNutritionTotals.collectAsState()

    val macros = listOf(
        MacroItem("Cal", nutritionToday.calories.toInt(), calGoal, Color(0xFF60A5FA)),
        MacroItem("Prot", nutritionToday.protein.toInt(), protGoal, Color(0xFFF87171)),
        MacroItem("Carb", nutritionToday.carbs.toInt(), carbGoal, Color(0xFFFBBF24)),
        MacroItem("Fat", nutritionToday.fats.toInt(), fatGoal, Color(0xFFA78BFA)),
    )

    Card(
        onClick = onAddMeal,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardDark),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "REGISTRO DE HOY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.48f),
                letterSpacing = 1.6.sp,
            )
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
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            "${m.current}/${m.goal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.46f),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (m.current.toFloat() / m.goal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = m.color,
                        trackColor = Color.White.copy(alpha = 0.08f),
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
        modifier = Modifier.size(width = 118.dp, height = 124.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardDarkAlt),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                data.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.48f),
                letterSpacing = 1.2.sp,
            )
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        data.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    if (data.unit == "%" || data.unit == "kg") {
                        Text(
                            data.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.44f),
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                        )
                    }
                }
                if (data.unit != "%" && data.unit != "kg" && data.unit.isNotEmpty()) {
                    Text(
                        data.unit,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
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
    val historyCount by viewModel.historyCount.collectAsState()
    val strengthData = viewModel.getRelativeStrengthData()
    val ipfGlPoints = viewModel.getIpfGlPoints()

    val cards = listOf(
        ExerciseCardData("Metas 1RM", "$starCount", "Pendientes", "star-targets"),
        ExerciseCardData("Fuerza Relativa", "${String.format("%.2f", strengthData.relativeStrength)}x", "Total: ${String.format("%.0f", strengthData.totalKg)}kg", "relative-strength"),
        ExerciseCardData("Historiales", "$historyCount", "Sesiones registradas", "history"),
        ExerciseCardData("IPF GL", if (ipfGlPoints > 0.0) String.format("%.0f", ipfGlPoints) else "--", "Puntos", "ipf-gl"),
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
        modifier = Modifier.size(width = 156.dp, height = 106.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardDarkAlt),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                data.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Column {
                Text(
                    data.mainValue,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Text(
                    data.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.48f),
                )
            }
        }
    }
}
