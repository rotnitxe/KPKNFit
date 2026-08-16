package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.MacroGoals
import com.example.kpkn.domain.nutrition.computeDailyTotals
import com.example.kpkn.domain.nutrition.deriveMacroGoals

// ═══════════════════════════════════════════════════════════════════════
// COLORS
// ═══════════════════════════════════════════════════════════════════════

private val TEAL = Color(0xFF009688)
private val PROTEIN_COLOR = Color(0xFFEF5350)
private val CARBS_COLOR = Color(0xFF7E57C2)
private val FATS_COLOR = Color(0xFF26A69A)

private val MEAL_LABELS = mapOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.DINNER to "Cena",
    MealType.SNACK to "Snack",
)

private val MEAL_ICONS = mapOf(
    MealType.BREAKFAST to Icons.Default.FreeBreakfast,
    MealType.LUNCH to Icons.Default.LunchDining,
    MealType.DINNER to Icons.Default.DinnerDining,
    MealType.SNACK to Icons.Default.Fastfood,
)

// ═══════════════════════════════════════════════════════════════════════
// DATA
// ═══════════════════════════════════════════════════════════════════════

private data class DaySummary(
    val date: String,
    val dateLabel: String,
    val logs: List<NutritionLog>,
    val totals: DailyMacroTotals,
)

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealHistoryScreen(
    onBack: () -> Unit,
) {
    val allLogs by NutritionRepository.getInstance().nutritionLogs.collectAsState()
    val settings by com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.collectAsState()
    val plans by NutritionRepository.getInstance().nutritionPlans.collectAsState()
    val activePlanId by NutritionRepository.getInstance().activeNutritionPlanId.collectAsState()
    val activePlan = remember(plans, activePlanId) {
        activePlanId?.let { id -> plans.find { it.id == id } }
    }
    val goals = deriveMacroGoals(settings, activePlan)

    // Group by day, sorted descending
    val daySummaries = remember(allLogs) {
        allLogs
            .filter { it.status != NutritionStatus.PLANNED }
            .groupBy { it.date.take(10) }
            .map { (date, logs) ->
                val dateLabel = try {
                    java.time.LocalDate.parse(date)
                        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale.getDefault()))
                        .replaceFirstChar { it.uppercase() }
                } catch (_: Exception) { date }
                DaySummary(
                    date = date,
                    dateLabel = dateLabel,
                    logs = logs.sortedBy { it.mealType.ordinal },
                    totals = computeDailyTotals(logs),
                )
            }
            .sortedByDescending { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Comidas", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
            )
        },
    ) { padding ->
        if (daySummaries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sin registros aún",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Los registros de comida aparecerán aquí",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                // Stats summary
                item {
                    HistoryStatsHeader(
                        totalDays = daySummaries.size,
                        totalLogs = allLogs.count { it.status != NutritionStatus.PLANNED },
                        avgCalories = if (daySummaries.isNotEmpty())
                            daySummaries.map { it.totals.calories }.average() else 0.0,
                        goals = goals,
                    )
                }

                // Day groups
                daySummaries.forEach { day ->
                    item(key = "header_${day.date}") {
                        DayHeader(day = day, goals = goals)
                    }
                    items(day.logs, key = { it.id }) { log ->
                        HistoryLogEntry(log = log)
                    }
                    item(key = "spacer_${day.date}") {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// STATS HEADER
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryStatsHeader(
    totalDays: Int,
    totalLogs: Int,
    avgCalories: Double,
    goals: MacroGoals,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(TEAL.copy(alpha = 0.10f), Color.Transparent)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatBubble("Días", "$totalDays", Color(0xFF42A5F5))
            StatBubble("Registros", "$totalLogs", TEAL)
            StatBubble("Prom. kcal", "${kotlin.math.round(avgCalories).toInt()}",
                if (avgCalories > goals.calorieGoal * 1.1) Color(0xFFE53935) else Color(0xFF66BB6A))
        }
    }
}

@Composable
private fun StatBubble(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// DAY HEADER
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun DayHeader(day: DaySummary, goals: MacroGoals) {
    val calPct = if (goals.calorieGoal > 0) day.totals.calories / goals.calorieGoal else 0.0
    val overGoal = calPct > 1.05

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Date circle
            Surface(
                shape = CircleShape,
                color = if (overGoal) Color(0xFFE53935).copy(alpha = 0.12f) else TEAL.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val dayNum = try {
                        java.time.LocalDate.parse(day.date).dayOfMonth.toString()
                    } catch (_: Exception) { "?" }
                    Text(
                        dayNum,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (overGoal) Color(0xFFE53935) else TEAL,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    day.dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${kotlin.math.round(day.totals.calories).toInt()} kcal · P${kotlin.math.round(day.totals.protein).toInt()} C${kotlin.math.round(day.totals.carbs).toInt()} G${kotlin.math.round(day.totals.fats).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (overGoal) Color(0xFFE53935).copy(alpha = 0.10f) else TEAL.copy(alpha = 0.10f),
            ) {
                Text(
                    "${(calPct * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (overGoal) Color(0xFFE53935) else TEAL,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LOG ENTRY
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryLogEntry(log: NutritionLog) {
    val icon = MEAL_ICONS[log.mealType] ?: Icons.Default.Restaurant
    val label = MEAL_LABELS[log.mealType] ?: log.mealType.name
    val foodNames = log.foods.joinToString(", ") { it.foodName }.ifEmpty { "Comida registrada" }
    val cal = log.foods.sumOf { it.calories }
    val pro = log.foods.sumOf { it.protein }
    val car = log.foods.sumOf { it.carbs }
    val fat = log.foods.sumOf { it.fats }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = TEAL, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TEAL,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        foodNames,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    "${kotlin.math.round(cal).toInt()} kcal · P${kotlin.math.round(pro).toInt()} C${kotlin.math.round(car).toInt()} G${kotlin.math.round(fat).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
