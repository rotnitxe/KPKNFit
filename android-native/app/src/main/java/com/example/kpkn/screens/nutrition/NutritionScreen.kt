package com.example.kpkn.screens.nutrition

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
private val PROTEIN_COLOR = Color(0xFFEF5350)
private val CARBS_COLOR = Color(0xFF7E57C2)
private val FATS_COLOR = Color(0xFF26A69A)
private val CALORIES_COLOR = Color(0xFF42A5F5)
private val TEAL = Color(0xFF009688)

private val MEAL_ICONS = mapOf(
    MealType.BREAKFAST to Icons.Default.FreeBreakfast,
    MealType.LUNCH to Icons.Default.LunchDining,
    MealType.DINNER to Icons.Default.DinnerDining,
    MealType.SNACK to Icons.Default.Fastfood,
)
private val MEAL_LABELS = mapOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.DINNER to "Cena",
    MealType.SNACK to "Snack",
)

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel { NutritionViewModel() },
    onNavigateToBodyProgress: (() -> Unit)? = null,
    onNavigateToMealHistory: (() -> Unit)? = null,
    onNavigateToWizard: (mode: String, planId: String?) -> Unit = { _, _ -> },
) {
    val dailyTotals by viewModel.dailyTotals.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val macroRingPct by viewModel.macroRingPct.collectAsState()
    val mealGroups by viewModel.mealGroups.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val foodDatabase by viewModel.foodDatabase.collectAsState()
    val trendData by viewModel.trendData.collectAsState()
    val historySeries by viewModel.historySeries.collectAsState()
    val sharedDescription by viewModel.pendingSharedDescription.collectAsState()
    val sharedTab by viewModel.pendingSharedTab.collectAsState()
    val foodLoggerOpenRequest by viewModel.foodLoggerOpenRequest.collectAsState()
    val dailyEnergyBalance by viewModel.dailyEnergyBalance.collectAsState()
    val nutritionRepo = remember { com.example.kpkn.data.repository.NutritionRepository.getInstance() }

    var showFoodLogger by remember { mutableStateOf(false) }
    var showPlanRequiredDialog by remember { mutableStateOf(false) }
    var selectedMealForLogger by remember { mutableStateOf(MealType.LUNCH) }
    var foodLoggerInitialDescription by remember { mutableStateOf<String?>(sharedDescription) }
    var foodLoggerInitialTab by remember { mutableIntStateOf(sharedTab.coerceIn(0, 1)) }
    
    val nutritionHazeState = remember { HazeState() }

    LaunchedEffect(sharedDescription) {
        if (!sharedDescription.isNullOrBlank()) {
            foodLoggerInitialDescription = sharedDescription
            foodLoggerInitialTab = sharedTab.coerceIn(0, 1)
            showFoodLogger = true
        }
    }

    LaunchedEffect(foodLoggerOpenRequest) {
        val request = foodLoggerOpenRequest ?: return@LaunchedEffect
        foodLoggerInitialDescription = request.description
        foodLoggerInitialTab = request.tab
        showFoodLogger = true
        viewModel.consumeFoodLoggerOpenRequest()
    }

    LaunchedEffect(activePlan?.id, activePlan?.calorieTarget, activePlan?.proteinGoal, activePlan?.carbGoal, activePlan?.fatGoal) {
        if (activePlan != null) {
            viewModel.syncActivePlanGoalsToSettings()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .hazeSource(state = nutritionHazeState),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item {
                    NutritionHeroHeader(
                        macroRingPct = macroRingPct,
                        dailyTotals = dailyTotals,
                        goals = goals,
                        selectedDate = selectedDate,
                        onEditPlan = { onNavigateToWizard("edit", activePlan?.id) },
                        onCreatePlan = { onNavigateToWizard("create", null) },
                        hasActivePlan = activePlan != null,
                    )
                }

                item {
                    DailyEnergyBalanceCard(balance = dailyEnergyBalance)
                }

                if (dailyTotals.calories > 0) {
                    item {
                        MacroBarsSection(dailyTotals = dailyTotals)
                    }
                }

                item {
                    DateSelector(
                        selectedDate = selectedDate,
                        onDateChange = { viewModel.setSelectedDate(it) },
                    )
                }

                item {
                    QuickAddBar(
                        onMealTypeClick = { meal ->
                            selectedMealForLogger = meal
                            foodLoggerInitialDescription = null
                            foodLoggerInitialTab = 0
                            viewModel.requestFoodLoggerOpen(tab = 0)
                        },
                    )
                }

                val mealOrder = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
                items(mealOrder) { mealType ->
                    val group = mealGroups.find { it.mealType == mealType }
                    MealGroupCard(
                        mealType = mealType,
                        group = group,
                        onDelete = { viewModel.deleteLog(it) },
                        onAddFood = {
                            selectedMealForLogger = mealType
                            foodLoggerInitialDescription = null
                            foodLoggerInitialTab = 0
                            viewModel.requestFoodLoggerOpen(tab = 0)
                        },
                    )
                }

                if (onNavigateToMealHistory != null) {
                    item {
                        TextButton(
                            onClick = onNavigateToMealHistory,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ver historial de comidas", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (trendData.isNotEmpty()) {
                    item {
                        CalorieTrendChart(
                            trendData = trendData,
                            calorieGoal = goals.calorieGoal,
                        )
                    }
                }
                if (historySeries.points.isNotEmpty()) {
                    item { NutritionHistoryCoverageCard(historySeries) }
                }
            }
        }

        val pillShape = RoundedCornerShape(999.dp)
        val yellowGlassStyle = remember {
            HazeStyle(
                blurRadius = KpknGlass.BlurRadius,
                tint = HazeTint(Color(0xFFFFD600).copy(alpha = 0.14f)),
                backgroundColor = Color.Black.copy(alpha = 0.45f),
                noiseFactor = KpknGlass.NoiseFactor,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 106.dp)
                .clip(pillShape)
                .hazeEffect(
                    state = nutritionHazeState,
                    style = yellowGlassStyle,
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFD600).copy(alpha = 0.45f),
                    shape = pillShape,
                )
                .clickable {
                    if (activePlan == null) {
                        showPlanRequiredDialog = true
                    } else {
                        selectedMealForLogger = MealType.LUNCH
                        foodLoggerInitialDescription = null
                        foodLoggerInitialTab = 0
                        viewModel.requestFoodLoggerOpen(tab = 0)
                    }
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFFD600).copy(alpha = 0.22f),
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Registrar comida",
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Registrar comida",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFFFFBEB),
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }

    // ── Food Logger Drawer ───────────────────────────────────────────────────
    FoodLoggerDrawer(
        nutritionRepo = nutritionRepo,
        isOpen = showFoodLogger,
        onDismiss = {
            showFoodLogger = false
            viewModel.consumeSharedDescription()
            foodLoggerInitialDescription = null
            foodLoggerInitialTab = 0
        },
        onSave = { log ->
            viewModel.addLog(log)
            showFoodLogger = false
            viewModel.consumeSharedDescription()
            foodLoggerInitialDescription = null
            foodLoggerInitialTab = 0
        },
        foodDatabase = foodDatabase,
        initialDate = selectedDate,
        initialMealType = selectedMealForLogger,
        initialDescription = foodLoggerInitialDescription,
        initialTab = foodLoggerInitialTab,
    )

    if (showPlanRequiredDialog) {
        KpknAlertDialog(
            onDismissRequest = { showPlanRequiredDialog = false },
            title = { Text("Crea un plan de alimentación") },
            text = { Text("Primero necesitas crear un plan de alimentación para registrar comidas o mediciones.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlanRequiredDialog = false
                        onNavigateToWizard("create", null)
                    },
                ) { Text("Crear plan") }
            },
            dismissButton = {
                TextButton(onClick = { showPlanRequiredDialog = false }) { Text("Cerrar") }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HERO HEADER — Central ring + macro summary
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun NutritionHeroHeader(
    macroRingPct: MacroRingPct,
    dailyTotals: DailyMacroTotals,
    goals: MacroGoals,
    selectedDate: String,
    onEditPlan: () -> Unit,
    onCreatePlan: () -> Unit,
    hasActivePlan: Boolean,
) {
    val dateLabel = try {
        java.time.LocalDate.parse(selectedDate)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale.getDefault()))
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { selectedDate }

    val calRemaining = goals.calorieGoal - dailyTotals.calories.toInt()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        TEAL.copy(alpha = 0.10f),
                        Color.Transparent,
                    )
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = topInset + 16.dp, bottom = 16.dp),
    ) {
        Column {
            // Top row: date + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Nutrición",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (hasActivePlan) {
                    IconButton(onClick = if (hasActivePlan) onEditPlan else onCreatePlan, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (hasActivePlan) Icons.Default.Edit else Icons.Default.Add,
                            if (hasActivePlan) "Editar plan" else "Crear plan",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Central ring + surrounding stats
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!hasActivePlan) Modifier.blur(10.dp) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(130.dp)) {
                            AnimatedMacroRing(
                                caloriesPct = if (hasActivePlan) macroRingPct.calories else 0.72,
                                proteinPct = if (hasActivePlan) macroRingPct.protein else 0.64,
                                carbsPct = if (hasActivePlan) macroRingPct.carbs else 0.58,
                                fatsPct = if (hasActivePlan) macroRingPct.fats else 0.48,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (hasActivePlan) "${dailyTotals.calories.toInt()}" else "Plan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (hasActivePlan) CALORIES_COLOR else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (hasActivePlan) "/ ${goals.calorieGoal} kcal" else "Personalizado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MacroDetailRow(
                            label = "Proteína",
                            current = if (hasActivePlan) dailyTotals.protein else 126.0,
                            goal = if (hasActivePlan) goals.proteinGoal else 160,
                            unit = "g",
                            color = if (hasActivePlan) PROTEIN_COLOR else MaterialTheme.colorScheme.onSurface,
                        )
                        MacroDetailRow(
                            label = "Carbohidratos",
                            current = if (hasActivePlan) dailyTotals.carbs else 180.0,
                            goal = if (hasActivePlan) goals.carbGoal else 230,
                            unit = "g",
                            color = if (hasActivePlan) CARBS_COLOR else MaterialTheme.colorScheme.onSurface,
                        )
                        MacroDetailRow(
                            label = "Grasas",
                            current = if (hasActivePlan) dailyTotals.fats else 52.0,
                            goal = if (hasActivePlan) goals.fatGoal else 70,
                            unit = "g",
                            color = if (hasActivePlan) FATS_COLOR else MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(Modifier.height(4.dp))

                        val remainColor = when {
                            !hasActivePlan -> MaterialTheme.colorScheme.onSurface
                            calRemaining >= 0 -> TEAL
                            else -> Color(0xFFE53935)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = remainColor.copy(alpha = if (hasActivePlan) 0.10f else 0.08f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    when {
                                        !hasActivePlan -> Icons.Default.AutoAwesome
                                        calRemaining >= 0 -> Icons.Default.CheckCircle
                                        else -> Icons.Default.Warning
                                    },
                                    null,
                                    tint = remainColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (hasActivePlan) {
                                        if (calRemaining >= 0) "$calRemaining kcal restantes"
                                        else "${-calRemaining} kcal de más"
                                    } else {
                                        "Define objetivos, macros y ritmo"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = remainColor,
                                )
                            }
                        }
                    }
                }

                if (!hasActivePlan) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Sin plan activo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Crea un plan simple y ajustable sin salir de nutrición.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onCreatePlan,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Crear plan de alimentación")
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MACRO DETAIL ROW — label + progress bar + value
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MacroDetailRow(
    label: String,
    current: Double,
    goal: Int,
    unit: String,
    color: Color,
    margin: Double? = null,
) {
    val pct = if (goal > 0) (current / goal).coerceIn(0.0, 1.2) else 0.0
    val trackColor = if (color == MaterialTheme.colorScheme.onSurface) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    } else {
        color.copy(alpha = 0.12f)
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${current.toInt()} / $goal $unit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (pct > 1.0 && color != MaterialTheme.colorScheme.onSurface) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (margin != null && margin > 0.0) {
            Text(
                text = "+${formatSignedMargin(margin)} / -${formatSignedMargin(margin)} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(start = 14.dp, top = 1.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        // Progress bar
        Canvas(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        ) {
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            val filledWidth = (size.width * pct.coerceAtMost(1.0)).toFloat()
            if (filledWidth > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(filledWidth, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

private fun formatSignedMargin(value: Double): String {
    return if (value >= 10.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// ANIMATED MACRO RING
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun AnimatedMacroRing(
    caloriesPct: Double,
    proteinPct: Double,
    carbsPct: Double,
    fatsPct: Double,
) {
    val animSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
    val aCal by animateFloatAsState(caloriesPct.coerceIn(0.0, 1.2).toFloat(), animSpec, label = "cal")
    val aPro by animateFloatAsState(proteinPct.coerceIn(0.0, 1.2).toFloat(), animSpec, label = "pro")
    val aCar by animateFloatAsState(carbsPct.coerceIn(0.0, 1.2).toFloat(), animSpec, label = "car")
    val aFat by animateFloatAsState(fatsPct.coerceIn(0.0, 1.2).toFloat(), animSpec, label = "fat")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeW = 10.dp.toPx()
        val gap = 3.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        val outerR = (size.minDimension / 2) - strokeW / 2
        val r2 = outerR - strokeW - gap
        val r3 = r2 - strokeW - gap
        val r4 = r3 - strokeW - gap

        fun ring(radius: Float, pct: Float, color: Color) {
            val d = radius * 2
            drawArc(color.copy(alpha = 0.10f), 0f, 360f, false,
                Offset(cx - radius, cy - radius), Size(d, d),
                style = Stroke(strokeW, cap = StrokeCap.Round))
            if (pct > 0f) {
                drawArc(color, -90f, (360f * pct.coerceAtMost(1f)), false,
                    Offset(cx - radius, cy - radius), Size(d, d),
                    style = Stroke(strokeW, cap = StrokeCap.Round))
            }
        }

        ring(outerR, aCal, CALORIES_COLOR)
        ring(r2, aPro, PROTEIN_COLOR)
        ring(r3, aCar, CARBS_COLOR)
        ring(r4, aFat, FATS_COLOR)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// DATE SELECTOR
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun DateSelector(
    selectedDate: String,
    onDateChange: (String) -> Unit,
) {
    val today = remember { java.time.LocalDate.now() }
    val dates = remember { (-3..3).map { today.plusDays(it.toLong()).toString() } }

    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(dates) { date ->
            val isSelected = date == selectedDate
            val isToday = date == today.toString()
            val d = try { java.time.LocalDate.parse(date) } catch (_: Exception) { today }
            val dayName = d.format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.getDefault()))
            val dayNum = d.dayOfMonth.toString()

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onDateChange(date) },
                shape = RoundedCornerShape(14.dp),
                color = when {
                    isSelected -> TEAL
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                tonalElevation = if (isSelected) 0.dp else 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        dayName.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        dayNum,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isToday) {
                        Box(
                            Modifier.size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else TEAL)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// QUICK ADD BAR
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun QuickAddBar(onMealTypeClick: (MealType) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TEAL.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, TEAL.copy(alpha = 0.20f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "REGISTRO RÁPIDO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = TEAL,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealType.entries.forEach { meal ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onMealTypeClick(meal) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E26),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                MEAL_ICONS[meal] ?: Icons.Default.Add,
                                null,
                                tint = TEAL,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                MEAL_LABELS[meal] ?: "",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MACRO BARS SECTION
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MacroBarsSection(dailyTotals: DailyMacroTotals) {
    val totalCal = dailyTotals.protein * 4 + dailyTotals.carbs * 4 + dailyTotals.fats * 9
    val protPct = if (totalCal > 0) (dailyTotals.protein * 4 / totalCal) else 0.0
    val carbPct = if (totalCal > 0) (dailyTotals.carbs * 4 / totalCal) else 0.0
    val fatPct = if (totalCal > 0) (dailyTotals.fats * 9 / totalCal) else 0.0

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "DISTRIBUCIÓN CALÓRICA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // Stacked bar
            if (totalCal > 0) {
                Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    drawRoundRect(Color.Gray.copy(alpha = 0.08f), cornerRadius = CornerRadius(7.dp.toPx()))
                    var x = 0f
                    val segments = listOf(protPct to PROTEIN_COLOR, carbPct to CARBS_COLOR, fatPct to FATS_COLOR)
                    segments.forEach { (pct, color) ->
                        val w = (size.width * pct).toFloat()
                        if (w > 0f) {
                            drawRect(color, Offset(x, 0f), Size(w, size.height))
                            x += w
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyEnergyBalanceCard(balance: DailyEnergyBalance) {
    val statusColor = when (balance.status) {
        DailyEnergyStatus.DEFICIT -> Color(0xFFEF5350)
        DailyEnergyStatus.MAINTENANCE -> Color(0xFF22C55E)
        DailyEnergyStatus.SURPLUS -> Color(0xFF7E57C2)
    }
    val statusLabel = when (balance.status) {
        DailyEnergyStatus.DEFICIT -> "Déficit"
        DailyEnergyStatus.MAINTENANCE -> "Mantención"
        DailyEnergyStatus.SURPLUS -> "Superávit"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.20f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Consumido", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${balance.consumedKcal}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Entreno", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("-${balance.trainingBurnKcal}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ingesta", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${balance.consumedKcal}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
            }

            val maxForBar = maxOf(balance.targetKcal * 2, balance.consumedKcal.coerceAtLeast(1))
            val netFraction = if (maxForBar > 0) balance.consumedKcal.toFloat() / maxForBar.toFloat() else 0f
            val targetFraction = if (maxForBar > 0) balance.targetKcal.toFloat() / maxForBar.toFloat() else 0f

            Canvas(
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            ) {
                drawRoundRect(Color.Gray.copy(alpha = 0.12f), cornerRadius = CornerRadius(5.dp.toPx()))

                val netW = (size.width * netFraction).coerceAtMost(size.width)
                if (netW > 0f) {
                    drawRoundRect(statusColor.copy(alpha = 0.7f), topLeft = Offset.Zero, size = Size(netW, size.height), cornerRadius = CornerRadius(5.dp.toPx()))
                }

                val targetX = (size.width * targetFraction).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                drawLine(Color.White, Offset(targetX, 0f), Offset(targetX, size.height), strokeWidth = 3.dp.toPx())
                drawLine(Color(0xFF333333), Offset(targetX, 0f), Offset(targetX, size.height), strokeWidth = 1.5.dp.toPx())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Meta: ${balance.targetKcal} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                    )
                    Text(
                        " · ${if (balance.deltaFromTarget >= 0) "+" else ""}${balance.deltaFromTarget} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DistributionLabel(label: String, pct: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            "$label $pct",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MEAL GROUP CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MealGroupCard(
    mealType: MealType,
    group: MealGroup?,
    onDelete: (String) -> Unit,
    onAddFood: () -> Unit,
) {
    val label = MEAL_LABELS[mealType] ?: mealType.name
    val icon = MEAL_ICONS[mealType] ?: Icons.Default.Restaurant
    val logs = group?.logs ?: emptyList()
    val totals = group?.totals ?: DailyMacroTotals()
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TEAL.copy(alpha = 0.10f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = TEAL, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (logs.isNotEmpty()) {
                            Text(
                                "${kotlin.math.round(totals.calories).toInt()} kcal · P ${kotlin.math.round(totals.protein).toInt()}g · C ${kotlin.math.round(totals.carbs).toInt()}g · G ${kotlin.math.round(totals.fats).toInt()}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Sin registros",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = TEAL.copy(alpha = 0.12f),
                        ) {
                            Text(
                                "${logs.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = TEAL,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = onAddFood, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add, "Agregar", tint = TEAL, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Entries
            AnimatedVisibility(visible = expanded && logs.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    logs.forEach { log ->
                        LogEntry(log = log, onDelete = onDelete)
                        if (log != logs.last()) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LOG ENTRY
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun LogEntry(log: NutritionLog, onDelete: (String) -> Unit) {
    val foodNames = log.foods.joinToString(", ") { it.foodName }.ifEmpty { "Comida registrada" }
    val cal = log.foods.sumOf { it.calories }
    val pro = log.foods.sumOf { it.protein }
    val car = log.foods.sumOf { it.carbs }
    val fat = log.foods.sumOf { it.fats }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    foodNames,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${kotlin.math.round(cal).toInt()} kcal · P${kotlin.math.round(pro).toInt()} C${kotlin.math.round(car).toInt()} G${kotlin.math.round(fat).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (log.notes != null) {
                    Text(
                        log.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { onDelete(log.id) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CALORIE TREND CHART
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CalorieTrendChart(
    trendData: List<TrendPoint>,
    calorieGoal: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "TENDENCIA DE CALORÍAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Últimos ${trendData.size} días",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))

            val maxCal = maxOf(
                trendData.filter { it.hasData }.maxOfOrNull { it.calories } ?: 0.0,
                calorieGoal.toDouble(),
            ) * 1.15

            val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

            val barCount = trendData.size

            Canvas(
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                if (trendData.isEmpty() || maxCal <= 0) return@Canvas

                val w = size.width
                val h = size.height
                val slotWidth = w / barCount
                val barPadding = slotWidth * 0.15f
                val barWidth = slotWidth - barPadding * 2

                // Goal line
                val goalY = h - (calorieGoal / maxCal * h).toFloat()
                drawLine(
                    color = surfaceVariant.copy(alpha = 0.3f),
                    start = Offset(0f, goalY),
                    end = Offset(w, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8f, 6f)
                    ),
                )

                trendData.forEachIndexed { i, point ->
                    val slotX = i * slotWidth
                    val x = slotX + barPadding
                    if (!point.hasData) {
                        drawLine(
                            color = surfaceVariant.copy(alpha = 0.35f),
                            start = androidx.compose.ui.geometry.Offset(x + barWidth / 2f, h - 3f),
                            end = androidx.compose.ui.geometry.Offset(x + barWidth / 2f, h - 14f),
                            strokeWidth = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                        )
                        return@forEachIndexed
                    }
                    val barH = (point.calories / maxCal * h).toFloat().coerceAtLeast(4f)
                    val y = h - barH
                    val overGoal = point.calories > calorieGoal
                    val barColor = if (overGoal) Color(0xFFE53935).copy(alpha = 0.7f) else TEAL.copy(alpha = 0.7f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }

            // Day labels — each gets equal slot to align with bars
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                trendData.forEach { point ->
                    val dayLabel = try {
                        java.time.LocalDate.parse(point.date)
                            .format(java.time.format.DateTimeFormatter.ofPattern("E", java.util.Locale.getDefault()))
                            .take(2)
                    } catch (_: Exception) { "?" }
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionHistoryCoverageCard(series: NutritionHistorySeries) {
    val uncertain = series.points.filter { point ->
        point.intakeCaloriesMin != null && point.intakeCaloriesMax != null &&
            point.intakeCaloriesMin != point.intakeCaloriesMax
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("HISTÓRICO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("Cobertura: ${series.coverage.label}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (uncertain.isEmpty()) "Sin rangos inciertos en este periodo."
                else "Los días con alimentos inciertos conservan su banda mínima–máxima.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            series.averageCaloriesOnRegisteredDays?.let { average ->
                Text("Promedio registrado: ${kotlin.math.round(average).toInt()} kcal", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BODY KPI SECTION
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BodyKpiSection(
    kpis: List<NutritionViewModel.BodyKpi>,
    onSeeMore: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MÉTRICAS CORPORALES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onSeeMore != null) {
                    TextButton(onClick = onSeeMore) {
                        Text("Ver más", style = MaterialTheme.typography.labelSmall, color = TEAL)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                kpis.forEach { kpi ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                kpi.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                kpi.value,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
    }
}
