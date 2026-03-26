package com.example.kpkn.screens.nutrition

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.screens.nutrition.components.NutritionPlanEditorModal
import com.example.kpkn.screens.nutrition.components.NutritionWizardView

// ─── Constants ───────────────────────────────────────────────────────────────

private val PROTEIN_COLOR = Color(0xFFB3261E)
private val CARBS_COLOR = Color(0xFF6750A4)
private val FATS_COLOR = Color(0xFF006A6A)
private val CALORIES_COLOR = Color(0xFF49454F)

private val MEAL_LABELS = mapOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.DINNER to "Cena",
    MealType.SNACK to "Snack",
)

@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel { NutritionViewModel() },
) {
    val dailyTotals by viewModel.dailyTotals.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val macroRingPct by viewModel.macroRingPct.collectAsState()
    val mealGroups by viewModel.mealGroups.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val showWizard by viewModel.showWizard.collectAsState()
    val bodyKpis by viewModel.bodyKpis.collectAsState()
    val progressPct by viewModel.progressPct.collectAsState()
    val foodDatabase by viewModel.foodDatabase.collectAsState()

    var showFoodLogger by remember { mutableStateOf(false) }
    var showPlanEditor by remember { mutableStateOf(false) }

    if (showWizard || activePlan == null) {
        NutritionWizardView(
            onComplete = { plan ->
                viewModel.createPlan(plan)
                viewModel.setShowWizard(false)
            },
            onSkip = { viewModel.setShowWizard(false) },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Hero Header ─────────────────────────────────────────────────────
        item {
            NutritionHeroHeader(
                macroRingPct = macroRingPct,
                goals = goals,
                selectedDate = selectedDate,
                progressPct = progressPct,
                hasActivePlan = activePlan != null,
                onCreatePlan = { viewModel.setShowWizard(true) },
                onEditPlan = { showPlanEditor = true },
            )
        }

        // ── Date Selector ───────────────────────────────────────────────────
        item {
            DateSelector(
                selectedDate = selectedDate,
                onDateChange = { viewModel.setSelectedDate(it) },
            )
        }

        // ── Add Food Button ─────────────────────────────────────────────────
        item {
            AddFoodButton(onClick = { showFoodLogger = true })
        }

        // ── Meal Groups ─────────────────────────────────────────────────────
        val mealOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
        items(mealOrder) { mealType ->
            val group = mealGroups.find { it.mealType == mealType }
            MealGroupCard(
                mealType = mealType,
                group = group,
                onDelete = { viewModel.deleteLog(it) },
            )
        }

        // ── Body KPIs ──────────────────────────────────────────────────────
        item {
            BodyKpiSection(kpis = bodyKpis)
        }

        // ── Bottom Spacer ──────────────────────────────────────────────────
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // ── Food Logger Drawer ───────────────────────────────────────────────────
    FoodLoggerDrawer(
        isOpen = showFoodLogger,
        onDismiss = { showFoodLogger = false },
        onSave = { log ->
            viewModel.addLog(log)
            showFoodLogger = false
        },
        foodDatabase = foodDatabase,
        initialDate = selectedDate,
        initialMealType = MealType.LUNCH,
    )

    // ── Plan Editor Modal ────────────────────────────────────────────────────
    NutritionPlanEditorModal(
        isOpen = showPlanEditor,
        onDismiss = { showPlanEditor = false },
        onSave = { plan ->
            viewModel.createPlan(plan)
            showPlanEditor = false
        },
        currentSettings = com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.value,
    )
}

// ─── Hero Header ─────────────────────────────────────────────────────────────

@Composable
private fun NutritionHeroHeader(
    macroRingPct: MacroRingPct,
    goals: MacroGoals,
    selectedDate: String,
    progressPct: Int,
    hasActivePlan: Boolean,
    onCreatePlan: () -> Unit,
    onEditPlan: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = try {
                            java.time.LocalDate.parse(selectedDate)
                                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale("es")))
                        } catch (e: Exception) { selectedDate },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (0.05f).sp,
                    )
                    Text(
                        text = "Tu Progreso",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (!hasActivePlan) {
                    Button(
                        onClick = onCreatePlan,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("CREAR PLAN")
                    }
                } else {
                    IconButton(onClick = onEditPlan) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Macro Rings
                MacroRingStack(
                    caloriesPct = macroRingPct.calories.coerceIn(0.0, 1.2),
                    proteinPct = macroRingPct.protein.coerceIn(0.0, 1.2),
                    carbsPct = macroRingPct.carbs.coerceIn(0.0, 1.2),
                    fatsPct = macroRingPct.fats.coerceIn(0.0, 1.2),
                    size = 120,
                )

                // Stats
                Column(modifier = Modifier.weight(1f)) {
                    StatRow("Calorías", "${macroRingPct.calories * goals.calorieGoal}", "kcal", CALORIES_COLOR)
                    StatRow("Proteína", "${macroRingPct.protein * goals.proteinGoal}", "g", PROTEIN_COLOR)
                    StatRow("Carbohidratos", "${macroRingPct.carbs * goals.carbGoal}", "g", CARBS_COLOR)
                    StatRow("Grasas", "${macroRingPct.fats * goals.fatGoal}", "g", FATS_COLOR)
                }
            }
        }
    }
}

// ─── Macro Ring Stack ────────────────────────────────────────────────────────

@Composable
private fun MacroRingStack(
    caloriesPct: Double,
    proteinPct: Double,
    carbsPct: Double,
    fatsPct: Double,
    size: Int = 120,
) {
    val sizeDp = size.dp
    val stroke = (size * 0.065f).coerceAtLeast(12f).dp
    val spacing = (size * 0.012f).coerceAtLeast(2f).dp

    Box(
        modifier = Modifier.size(sizeDp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = stroke.toPx()
            val spacingPx = spacing.toPx()
            val cx = size.dp.toPx() / 2
            val cy = size.dp.toPx() / 2

            val outerR = (size.dp.toPx() / 2) - strokeWidth / 2
            val midOuterR = outerR - strokeWidth - spacingPx
            val midInnerR = midOuterR - strokeWidth - spacingPx
            val innerR = midInnerR - strokeWidth - spacingPx

            val drawRing = { radius: Float, pct: Double, color: Color ->
                val arcSize = radius * 2
                drawArc(
                    color = color.copy(alpha = 0.15f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = (360f * pct.coerceIn(0.0, 1.0)).toFloat(),
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }

            drawRing(outerR, caloriesPct, CALORIES_COLOR)
            drawRing(midOuterR, proteinPct, PROTEIN_COLOR)
            drawRing(midInnerR, carbsPct, CARBS_COLOR)
            drawRing(innerR, fatsPct, FATS_COLOR)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Macros",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Stat Row ────────────────────────────────────────────────────────────────

@Composable
private fun StatRow(label: String, value: String, unit: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Date Selector ───────────────────────────────────────────────────────────

@Composable
private fun DateSelector(
    selectedDate: String,
    onDateChange: (String) -> Unit,
) {
    val today = remember { java.time.LocalDate.now() }
    val dates = remember { (-3..3).map { today.plusDays(it.toLong()).toString() } }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates) { date ->
            val isSelected = date == selectedDate
            val isToday = date == today.toString()
            val label = try {
                val d = java.time.LocalDate.parse(date)
                val dayName = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale("es")))
                val dayNum = d.dayOfMonth.toString()
                "$dayName $dayNum"
            } catch (e: Exception) { date }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onDateChange(date) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isToday) {
                        Text(
                            text = "Hoy",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// ─── Add Food Button ─────────────────────────────────────────────────────────

@Composable
private fun AddFoodButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "REGISTRO RÁPIDO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Describe tu comida",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Escribe con libertad: '200g pollo con arroz'",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Meal Group Card ─────────────────────────────────────────────────────────

@Composable
private fun MealGroupCard(
    mealType: MealType,
    group: MealGroup?,
    onDelete: (String) -> Unit,
) {
    val label = MEAL_LABELS[mealType] ?: mealType.name
    val logs = group?.logs ?: emptyList()
    val totals = group?.totals ?: DailyMacroTotals()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (0.08f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (logs.isNotEmpty()) {
                        Text(
                            text = "${kotlin.math.round(totals.calories)} kcal · P ${kotlin.math.round(totals.protein)}g · C ${kotlin.math.round(totals.carbs)}g · G ${kotlin.math.round(totals.fats)}g",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = "Sin registros por ahora",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (logs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                    ) {
                        Text(
                            text = "${logs.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                logs.forEach { log ->
                    LogEntry(log = log, onDelete = onDelete)
                    if (log != logs.last()) Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

// ─── Log Entry ───────────────────────────────────────────────────────────────

@Composable
private fun LogEntry(log: NutritionLog, onDelete: (String) -> Unit) {
    val foodNames = log.foods.joinToString(", ") { it.foodName }.ifEmpty { "Comida registrada" }
    val logTotals = DailyMacroTotals(
        calories = log.foods.sumOf { it.calories },
        protein = log.foods.sumOf { it.protein },
        carbs = log.foods.sumOf { it.carbs },
        fats = log.foods.sumOf { it.fats },
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = foodNames,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${kotlin.math.round(logTotals.calories)} kcal · P ${kotlin.math.round(logTotals.protein)}g · C ${kotlin.math.round(logTotals.carbs)}g · G ${kotlin.math.round(logTotals.fats)}g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (log.notes != null) {
                Text(
                    text = "Nota: ${log.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onDelete(log.id) },
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("Eliminar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─── Body KPI Section ────────────────────────────────────────────────────────

@Composable
private fun BodyKpiSection(kpis: List<NutritionViewModel.BodyKpi>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "MÉTRICAS CORPORALES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(kpis) { kpi ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = kpi.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.06f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = kpi.value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
    }
}
