package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.SectionHeader
import com.example.kpkn.ui.theme.HomeCardSurface
import com.example.kpkn.ui.theme.HomeCardSurfaceAlt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics


@Composable
fun HomeCardsSection(
    viewModel: HomeViewModel,
    onNavigateToCard: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddMeal: () -> Unit = {},
) {
    val cards by viewModel.cardsState.collectAsState()

    Column(modifier.fillMaxWidth()) {
        SectionHeader("Progreso físico y alimentación", Modifier.padding(horizontal = 24.dp))
        MacroProgressBars(cards, onAddMeal, Modifier.padding(horizontal = 24.dp))

        Spacer(Modifier.height(12.dp))

        BiometryCardsCarousel(cards, onNavigateToCard)

        Spacer(Modifier.height(18.dp))

        SectionHeader("Tus ejercicios", Modifier.padding(horizontal = 24.dp))

        ExerciseMetricCards(cards, onNavigateToCard)
    }
}

// ─── Macro Progress Bars ────────────────────────────────────────────────────

@Composable
private fun MacroProgressBars(state: HomeCardsState, onAddMeal: () -> Unit = {}, modifier: Modifier = Modifier) {
    val nutritionToday = state.nutrition

    val macros = listOf(
        MacroItem("Cal", nutritionToday.calories.toInt(), state.calorieGoal, Color(0xFF60A5FA)),
        MacroItem("Prot", nutritionToday.protein.toInt(), state.proteinGoal, Color(0xFFF87171)),
        MacroItem("Carb", nutritionToday.carbs.toInt(), state.carbGoal, Color(0xFFFBBF24)),
        MacroItem("Fat", nutritionToday.fats.toInt(), state.fatGoal, Color(0xFFA78BFA)),
    )

    Card(
        onClick = onAddMeal,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardSurface),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "REGISTRO DE HOY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.68f),
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
                            color = Color.White.copy(alpha = 0.68f),
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
    state: HomeCardsState,
    onNavigateToCard: (String) -> Unit,
) {
    val weightText: String = state.weight?.let { String.format("%.1f", it) } ?: "--"
    val ffmiText: String = state.ffmi?.let { String.format("%.1f", it) } ?: "--"
    val imcText: String = state.imc?.let { String.format("%.1f", it) } ?: "--"
    val fatText: String = state.bodyFat?.let { String.format("%.1f", it) } ?: "--"
    val muscleText: String = state.musclePct?.let { String.format("%.1f", it) } ?: "--"

    val cards = listOf(
        BiometryCardData("Peso", weightText, "kg", "body-progress"),
        BiometryCardData("FFMI", ffmiText, state.ffmiInterpretation ?: "S/D", "ffmi"),
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
        modifier = Modifier
            .size(width = 118.dp, height = 124.dp)
            .semantics {
                contentDescription = "${data.title} ${data.value} ${data.unit}. Tocar para ver progreso corporal"
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardSurfaceAlt),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                data.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.68f),
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
                            color = Color.White.copy(alpha = 0.68f),
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
    state: HomeCardsState,
    onNavigateToCard: (String) -> Unit,
) {
    val cards = listOf(
        ExerciseCardData("Metas estrella", "${state.starTargetsCount}", "Configuradas", "star-targets"),
        ExerciseCardData("Fuerza Relativa", "${String.format("%.2f", state.relativeStrength)}x", "Total: ${String.format("%.0f", state.totalKg)}kg", "relative-strength"),
        ExerciseCardData("Historiales", "${state.historyCount}", "Sesiones registradas", "history"),
        ExerciseCardData("IPF GL", if (state.ipfGlPoints > 0.0) String.format("%.0f", state.ipfGlPoints) else "--", "Puntos", "ipf-gl"),
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
        modifier = Modifier
            .size(width = 156.dp, height = 106.dp)
            .semantics {
                contentDescription = "${data.title} ${data.mainValue} ${data.subtitle}"
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HomeCardSurfaceAlt),
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
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
        }
    }
}
