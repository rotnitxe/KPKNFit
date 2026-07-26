package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
    onOpenNutritionOverlay: () -> Unit = {},
    onNutritionAnchorPositionChanged: (Float) -> Unit = {},
) {
    val cards by viewModel.cardsState.collectAsState()

    Column(modifier.fillMaxWidth()) {
        SectionHeader("Progreso físico y alimentación", Modifier.padding(horizontal = 24.dp))
        MacroProgressBars(cards, onOpenNutritionOverlay, Modifier.padding(horizontal = 24.dp), onNutritionAnchorPositionChanged)

        Spacer(Modifier.height(12.dp))

        BiometryCardsCarousel(cards, onNavigateToCard)

    }
}

// ─── Macro Progress Bars ────────────────────────────────────────────────────

@Composable
private fun MacroProgressBars(
    state: HomeCardsState,
    onOpenOverlay: () -> Unit = {},

    modifier: Modifier = Modifier,
    onAnchorPositionChanged: (Float) -> Unit = {},
) {
    val nutrition = state.nutrition
    val calorieProgress = (nutrition.calories.toFloat() / state.calorieGoal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val macros = listOf(
        MacroItem("Proteínas", nutrition.protein.toInt(), state.proteinGoal, "g", Color(0xFFE89A8F)),
        MacroItem("Carbohidratos", nutrition.carbs.toInt(), state.carbGoal, "g", Color(0xFFD7AE63)),
        MacroItem("Grasas", nutrition.fats.toInt(), state.fatGoal, "g", Color(0xFF9A86C8)),
    )

    Card(
        onClick = onOpenOverlay,
        modifier = modifier.fillMaxWidth().onGloballyPositioned { onAnchorPositionChanged(it.positionInRoot().y) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181E2C)),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "REGISTRO DE HOY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.62f),
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            nutrition.calories.toInt().toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            " kcal",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                Text(
                    "${state.calorieGoal} kcal meta",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8FB7B8),
                )
            }
            LinearProgressIndicator(
                progress = { calorieProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = Color(0xFF4FA3A5),
                trackColor = Color.White.copy(alpha = 0.08f),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                macros.forEach { macro ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            macro.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                        )
                        Text(
                            "${macro.current}/${macro.goal}${macro.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = macro.color,
                            maxLines = 1,
                        )
                        LinearProgressIndicator(
                            progress = { (macro.current.toFloat() / macro.goal.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                            color = macro.color,
                            trackColor = Color.White.copy(alpha = 0.08f),
                        )
                    }
                }
            }
        }
    }
}

private data class MacroItem(
    val label: String,
    val current: Int,
    val goal: Int,
    val unit: String,
    val color: Color,
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
    val accent = when (data.navTarget) {
        "body-progress" -> Color(0xFF4FA3A5)
        "ffmi" -> Color(0xFF9A86C8)
        "imc" -> Color(0xFFD7AE63)
        "fat" -> Color(0xFFC96B5C)
        else -> Color(0xFF72A67B)
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(104.dp)
            .semantics { contentDescription = "${data.title} ${data.value} ${data.unit}. Tocar para ver progreso corporal" },
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.13f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                data.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.64f),
                letterSpacing = 0.8.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
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
                        color = accent,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                    )
                }
            }
            if (data.unit != "%" && data.unit != "kg" && data.unit.isNotEmpty()) {
                Text(
                    data.unit,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                )
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
            Modifier.fillMaxSize().padding(10.dp),
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
