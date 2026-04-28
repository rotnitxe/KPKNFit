package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════
// COLORS
// ═══════════════════════════════════════════════════════════════════════

private val TEAL = Color(0xFF009688)
private val WEIGHT_COLOR = Color(0xFF42A5F5)
private val BODYFAT_COLOR = Color(0xFFEF5350)
private val MUSCLE_COLOR = Color(0xFF66BB6A)
private val FFMI_COLOR = Color(0xFF7E57C2)

// ═══════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════

private data class MeasurementEntry(
    val date: String,
    val weight: Double?,
    val bodyFat: Double?,
    val muscleMass: Double?,
)

private data class FfmiCategory(
    val label: String,
    val range: ClosedFloatingPointRange<Double>,
    val color: Color,
)

// Visual scale range: 14 - 30. Categories sized proportionally for the gauge.
private val FFMI_SCALE_MIN = 14.0
private val FFMI_SCALE_MAX = 30.0

private val FFMI_CATEGORIES = listOf(
    FfmiCategory("Bajo", 0.0..17.9, Color(0xFF90CAF9)),
    FfmiCategory("Promedio", 18.0..19.9, Color(0xFF66BB6A)),
    FfmiCategory("Avanzado", 20.0..22.4, Color(0xFFFFA726)),
    FfmiCategory("Élite", 22.5..24.9, FFMI_COLOR),
    FfmiCategory("Excepcional", 25.0..30.0, Color(0xFFE53935)),
)

// For gauge rendering: clamp categories to visual range
private data class GaugeSegment(val label: String, val start: Double, val end: Double, val color: Color)
private val FFMI_GAUGE_SEGMENTS = listOf(
    GaugeSegment("Bajo", FFMI_SCALE_MIN, 18.0, Color(0xFF90CAF9)),
    GaugeSegment("Promedio", 18.0, 20.0, Color(0xFF66BB6A)),
    GaugeSegment("Avanzado", 20.0, 22.5, Color(0xFFFFA726)),
    GaugeSegment("Élite", 22.5, 25.0, FFMI_COLOR),
    GaugeSegment("Excepcional", 25.0, FFMI_SCALE_MAX, Color(0xFFE53935)),
)

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyProgressScreen(
    onBack: () -> Unit,
) {
    val nutritionRepo = NutritionRepository.getInstance()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val vitals = settings.userVitals
    val plans by nutritionRepo.nutritionPlans.collectAsState()
    val activePlan = plans.lastOrNull { it.isActive } ?: plans.lastOrNull()
    val bodyMeasurements by nutritionRepo.bodyMeasurements.collectAsState()
    val measurementSchedule by nutritionRepo.measurementSchedule.collectAsState()

    var showAddMeasurement by remember { mutableStateOf(false) }

    // Compute derived body metrics
    val weight = vitals.weight
    val height = vitals.height
    val bodyFat = vitals.bodyFatPercentage
    val muscle = vitals.muscleMassPercentage
    val goalType = activePlan?.goalType ?: GoalMetric.WEIGHT
    // Target value interpreted by goalType: kg for WEIGHT, % for BODY_FAT/MUSCLE_MASS
    val targetWeight = if (goalType == GoalMetric.WEIGHT) vitals.targetWeight ?: activePlan?.goalValue else null
    val targetBodyFat = if (goalType == GoalMetric.BODY_FAT) activePlan?.goalValue else null
    val targetMuscle = if (goalType == GoalMetric.MUSCLE_MASS) activePlan?.goalValue else null

    val bmi = if (weight != null && height != null && height > 0)
        weight / ((height / 100) * (height / 100)) else null
    val ffmi = if (weight != null && height != null && bodyFat != null && height > 0)
        (weight * (1 - bodyFat / 100)) / ((height / 100) * (height / 100)) else null
    val lbm = if (weight != null && bodyFat != null)
        weight * (1 - bodyFat / 100) else null
    val fatMass = if (weight != null && bodyFat != null)
        weight * (bodyFat / 100) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progreso Físico", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddMeasurement = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Registrar medición") },
                containerColor = TEAL,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            // ── Hero with current stats ─────────────────────────────────
            item {
                ProgressHero(
                    weight = weight,
                    bodyFat = bodyFat,
                    muscle = muscle,
                    bmi = bmi,
                    ffmi = ffmi,
                )
            }

            // ── Goal Progress ───────────────────────────────────────────
            when (goalType) {
                GoalMetric.WEIGHT -> if (targetWeight != null && weight != null) {
                    item {
                        GoalProgressCard(
                            currentValue = weight,
                            targetValue = targetWeight,
                            goalType = GoalMetric.WEIGHT,
                            plan = activePlan,
                            currentWeight = weight,
                        )
                    }
                }
                GoalMetric.BODY_FAT -> if (targetBodyFat != null && bodyFat != null) {
                    item {
                        GoalProgressCard(
                            currentValue = bodyFat,
                            targetValue = targetBodyFat,
                            goalType = GoalMetric.BODY_FAT,
                            plan = activePlan,
                            currentWeight = weight,
                        )
                    }
                }
                GoalMetric.MUSCLE_MASS -> if (targetMuscle != null && muscle != null) {
                    item {
                        GoalProgressCard(
                            currentValue = muscle,
                            targetValue = targetMuscle,
                            goalType = GoalMetric.MUSCLE_MASS,
                            plan = activePlan,
                            currentWeight = weight,
                        )
                    }
                }
            }

            // ── FFMI Gauge ──────────────────────────────────────────────
            if (ffmi != null) {
                item {
                    FfmiGaugeCard(ffmi = ffmi, gender = vitals.gender)
                }
            }

            // ── Body Composition Breakdown ──────────────────────────────
            if (lbm != null && fatMass != null && weight != null) {
                item {
                    CompositionBreakdownCard(
                        weight = weight,
                        lbm = lbm,
                        fatMass = fatMass,
                    )
                }
            }

            // ── KPI Grid ────────────────────────────────────────────────
            item {
                KpiGrid(
                    weight = weight,
                    height = height,
                    bodyFat = bodyFat,
                    muscle = muscle,
                    bmi = bmi,
                    ffmi = ffmi,
                    lbm = lbm,
                )
            }

            // ── BMI Category ────────────────────────────────────────────
            if (bmi != null) {
                item {
                    BmiCategoryCard(bmi = bmi)
                }
            }

            // ── Tips ────────────────────────────────────────────────────
            item {
                TipsCard(bodyFat = bodyFat, ffmi = ffmi, bmi = bmi)
            }

            // ── Programar próxima medición ───────────────────────────────
            item {
                MeasurementScheduleCard(
                    schedule = measurementSchedule,
                    onUpdate = nutritionRepo::updateMeasurementSchedule,
                )
            }

            // ── Historial de mediciones ──────────────────────────────────
            item {
                Text(
                    "HISTORIAL DE MEDIDAS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (bodyMeasurements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.MonitorWeight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Sin mediciones registradas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Usa el botón + para registrar tu primera medición",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(bodyMeasurements.sortedByDescending { it.date }) { entry ->
                    MeasurementEntryCard(
                        entry = entry,
                        onDelete = { nutritionRepo.deleteBodyMeasurement(entry.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    // ── Add Measurement Sheet ────────────────────────────────────────────────
    if (showAddMeasurement) {
        AddMeasurementSheet(
            onDismiss = { showAddMeasurement = false },
            onSave = { entry ->
                nutritionRepo.addBodyMeasurement(entry)
                // Actualizar vitals con el peso más reciente si se proveyó
                if (entry.weight != null) {
                    ProgramRepository.getInstance().let { repo ->
                        repo.updateSettings { s ->
                            s.copy(
                                userVitals = s.userVitals.copy(
                                    weight = entry.weight,
                                    bodyFatPercentage = entry.bodyFat ?: s.userVitals.bodyFatPercentage,
                                    muscleMassPercentage = entry.muscleMass ?: s.userVitals.muscleMassPercentage,
                                ),
                            )
                        }
                    }
                }
                showAddMeasurement = false
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HERO — Big stats at top
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ProgressHero(
    weight: Double?,
    bodyFat: Double?,
    muscle: Double?,
    bmi: Double?,
    ffmi: Double?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(TEAL.copy(alpha = 0.10f), Color.Transparent)
                )
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = TEAL, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Estado actual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                HeroMetric("Peso", weight?.let { "${r1(it)} kg" } ?: "—", WEIGHT_COLOR)
                HeroMetric("% Grasa", bodyFat?.let { "${r1(it)}%" } ?: "—", BODYFAT_COLOR)
                HeroMetric("% Músculo", muscle?.let { "${r1(it)}%" } ?: "—", MUSCLE_COLOR)
                HeroMetric("FFMI", ffmi?.let { "${r1(it)}" } ?: "—", FFMI_COLOR)
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = color.copy(alpha = 0.10f),
            modifier = Modifier.size(width = 68.dp, height = 52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// GOAL PROGRESS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun GoalProgressCard(
    currentValue: Double,
    targetValue: Double,
    goalType: GoalMetric = GoalMetric.WEIGHT,
    plan: NutritionPlan?,
    currentWeight: Double? = null, // needed for body fat % → kg rate conversion
) {
    // Direction: for MUSCLE goal, "losing" means moving away from target
    val isDecreasing = when (goalType) {
        GoalMetric.MUSCLE_MASS -> targetValue < currentValue // shouldn't happen normally
        else -> targetValue < currentValue
    }
    val totalDelta = kotlin.math.abs(currentValue - targetValue)

    val accentColor = when (goalType) {
        GoalMetric.WEIGHT -> WEIGHT_COLOR
        GoalMetric.BODY_FAT -> BODYFAT_COLOR
        GoalMetric.MUSCLE_MASS -> MUSCLE_COLOR
    }
    val titleLabel = when (goalType) {
        GoalMetric.WEIGHT -> "META DE PESO"
        GoalMetric.BODY_FAT -> "META DE % GRASA"
        GoalMetric.MUSCLE_MASS -> "META DE % MÚSCULO"
    }
    val unit = when (goalType) {
        GoalMetric.WEIGHT -> "kg"
        else -> "%"
    }
    val deltaLabel = when (goalType) {
        GoalMetric.WEIGHT -> if (isDecreasing) "Perder ${r1(totalDelta)} kg" else "Ganar ${r1(totalDelta)} kg"
        GoalMetric.BODY_FAT -> if (isDecreasing) "Reducir ${r1(totalDelta)}% grasa" else "Aumentar ${r1(totalDelta)}% grasa"
        GoalMetric.MUSCLE_MASS -> if (!isDecreasing) "Ganar ${r1(totalDelta)}% músculo" else "Reducir ${r1(totalDelta)}% músculo"
    }

    val weeklyRateKg = plan?.weeklyChangeKg ?: 0.5
    // For body fat / muscle goals, express weekly rate in % units per week
    val weeklyRateInUnit = when (goalType) {
        GoalMetric.WEIGHT -> weeklyRateKg
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> {
            val wt = currentWeight ?: 70.0
            if (wt > 0) weeklyRateKg / wt * 100.0 else weeklyRateKg
        }
    }
    val weeklyRateLabel = when (goalType) {
        GoalMetric.WEIGHT -> "${r1(weeklyRateKg)} kg/sem"
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "${r1(weeklyRateInUnit)} %/sem"
    }
    val weeksToGoal = if (weeklyRateInUnit > 0) (totalDelta / weeklyRateInUnit) else 0.0

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isDecreasing) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                    null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    titleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Actual", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${r1(currentValue)} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        deltaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Meta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${r1(targetValue)} $unit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = accentColor)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoChip("Ritmo", weeklyRateLabel, Modifier.weight(1f))
                InfoChip("Estimado", "${kotlin.math.round(weeksToGoal).toInt()} sem", Modifier.weight(1f))
                InfoChip("Δ kcal/día", "${kotlin.math.round(weeklyRateKg * 7700 / 7).toInt()}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// FFMI GAUGE
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun FfmiGaugeCard(ffmi: Double, gender: Gender?) {
    val category = FFMI_CATEGORIES.find { ffmi in it.range } ?: FFMI_CATEGORIES.last()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "ÍNDICE DE MASA LIBRE DE GRASA (FFMI)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Value + category
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${r1(ffmi)}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = category.color,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = category.color.copy(alpha = 0.12f),
                ) {
                    Text(
                        category.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = category.color,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Scale bar
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp))) {
                val totalRange = FFMI_SCALE_MAX - FFMI_SCALE_MIN
                var x = 0f
                FFMI_GAUGE_SEGMENTS.forEach { seg ->
                    val segWidth = ((seg.end - seg.start) / totalRange * size.width).toFloat()
                    drawRect(seg.color.copy(alpha = 0.5f), Offset(x, 0f), Size(segWidth, size.height))
                    x += segWidth
                }
                // Marker
                val markerX = ((ffmi.coerceIn(FFMI_SCALE_MIN, FFMI_SCALE_MAX) - FFMI_SCALE_MIN) / totalRange * size.width).toFloat()
                drawCircle(Color.White, 8.dp.toPx(), Offset(markerX, size.height / 2))
                drawCircle(category.color, 6.dp.toPx(), Offset(markerX, size.height / 2))
            }

            Spacer(Modifier.height(8.dp))

            // Category labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FFMI_GAUGE_SEGMENTS.forEach { seg ->
                    val isCurrent = seg.label == category.label
                    Text(
                        seg.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = if (isCurrent) seg.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "El FFMI normaliza la masa muscular por altura. Un valor >25 sin sustancias es excepcional.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BODY COMPOSITION BREAKDOWN
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CompositionBreakdownCard(
    weight: Double,
    lbm: Double,
    fatMass: Double,
) {
    val lbmPct = lbm / weight
    val fatPct = fatMass / weight

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "COMPOSICIÓN CORPORAL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Stacked bar
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))) {
                val lbmW = (size.width * lbmPct).toFloat()
                drawRect(MUSCLE_COLOR, Offset.Zero, Size(lbmW, size.height))
                drawRect(BODYFAT_COLOR, Offset(lbmW, 0f), Size(size.width - lbmW, size.height))
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CompositionItem("Masa magra", "${r1(lbm)} kg", "${(lbmPct * 100).toInt()}%", MUSCLE_COLOR)
                CompositionItem("Masa grasa", "${r1(fatMass)} kg", "${(fatPct * 100).toInt()}%", BODYFAT_COLOR)
                CompositionItem("Total", "${r1(weight)} kg", "100%", WEIGHT_COLOR)
            }
        }
    }
}

@Composable
private fun CompositionItem(label: String, value: String, pct: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(pct, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// KPI GRID
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun KpiGrid(
    weight: Double?,
    height: Double?,
    bodyFat: Double?,
    muscle: Double?,
    bmi: Double?,
    ffmi: Double?,
    lbm: Double?,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "TODAS LAS MÉTRICAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            val metrics = listOf(
                Triple("Peso", weight?.let { "${r1(it)} kg" } ?: "—", WEIGHT_COLOR),
                Triple("Altura", height?.let { "${it.toInt()} cm" } ?: "—", Color(0xFF78909C)),
                Triple("% Grasa", bodyFat?.let { "${r1(it)}%" } ?: "—", BODYFAT_COLOR),
                Triple("% Músculo", muscle?.let { "${r1(it)}%" } ?: "—", MUSCLE_COLOR),
                Triple("IMC", bmi?.let { "${r1(it)}" } ?: "—", WEIGHT_COLOR),
                Triple("FFMI", ffmi?.let { "${r1(it)}" } ?: "—", FFMI_COLOR),
                Triple("Masa magra", lbm?.let { "${r1(it)} kg" } ?: "—", MUSCLE_COLOR),
                Triple("Masa grasa", if (weight != null && bodyFat != null) "${r1(weight * bodyFat / 100)} kg" else "—", BODYFAT_COLOR),
            )

            // 2-column grid
            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (label, value, color) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = color.copy(alpha = 0.06f),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        value,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    // Fill empty slot if odd
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BMI CATEGORY
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BmiCategoryCard(bmi: Double) {
    data class BmiCat(val label: String, val range: ClosedFloatingPointRange<Double>, val color: Color)
    val cats = listOf(
        BmiCat("Bajo peso", 0.0..18.49, Color(0xFF90CAF9)),
        BmiCat("Normal", 18.5..24.99, Color(0xFF66BB6A)),
        BmiCat("Sobrepeso", 25.0..29.99, Color(0xFFFFA726)),
        BmiCat("Obesidad I", 30.0..34.99, Color(0xFFEF5350)),
        BmiCat("Obesidad II+", 35.0..60.0, Color(0xFFB71C1C)),
    )
    val current = cats.find { bmi in it.range } ?: cats.last()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "ÍNDICE DE MASA CORPORAL (IMC)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${r1(bmi)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = current.color,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = current.color.copy(alpha = 0.12f),
                ) {
                    Text(
                        current.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = current.color,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "⚠️ El IMC no distingue masa muscular de grasa. Si entrenas con pesas, el FFMI es una métrica más precisa.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TIPS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun TipsCard(bodyFat: Double?, ffmi: Double?, bmi: Double?) {
    val tips = mutableListOf<String>()

    if (bodyFat != null) {
        when {
            bodyFat < 10 -> tips += "Tu % de grasa es muy bajo. Mantener niveles < 10% es difícil a largo plazo y puede afectar hormonas."
            bodyFat in 10.0..15.0 -> tips += "Excelente nivel de grasa corporal para rendimiento y estética."
            bodyFat in 15.0..20.0 -> tips += "Buen rango de grasa corporal. Ideal para fases de volumen controlado."
            bodyFat > 25 -> tips += "Considera una fase de déficit para mejorar salud metabólica y composición corporal."
        }
    }
    if (ffmi != null) {
        when {
            ffmi < 18 -> tips += "Tu FFMI sugiere potencial de crecimiento muscular significativo. Prioriza entrenamiento progresivo."
            ffmi in 20.0..23.0 -> tips += "Muy buen desarrollo muscular. Estás en rango avanzado."
            ffmi > 25 -> tips += "FFMI excepcional. Estás en el tope del potencial muscular natural."
        }
    }

    if (tips.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TEAL.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = TEAL, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "OBSERVACIONES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = TEAL,
                )
            }
            Spacer(Modifier.height(8.dp))
            tips.forEach { tip ->
                Text(
                    "• $tip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MEASUREMENT SCHEDULE CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MeasurementScheduleCard(
    schedule: MeasurementSchedule,
    onUpdate: (MeasurementSchedule) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TEAL.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = TEAL, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Programar mediciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onUpdate(schedule.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = TEAL, checkedTrackColor = TEAL.copy(alpha = 0.3f)),
                )
            }

            if (schedule.enabled) {
                Spacer(Modifier.height(12.dp))

                // Interval selector
                Text("Frecuencia", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "Semanal", 14 to "Quincenal", 30 to "Mensual").forEach { (days, label) ->
                        val sel = schedule.intervalDays == days
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) TEAL else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { onUpdate(schedule.copy(intervalDays = days)) },
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (sel) FontWeight.Black else FontWeight.SemiBold,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (schedule.nextDate != null) {
                    Spacer(Modifier.height(10.dp))
                    val label = try {
                        java.time.LocalDate.parse(schedule.nextDate)
                            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale.getDefault()))
                            .replaceFirstChar { it.uppercase() }
                    } catch (_: Exception) { schedule.nextDate }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = TEAL, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Próxima: $label", style = MaterialTheme.typography.labelMedium, color = TEAL, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val next = java.time.LocalDate.now().plusDays(schedule.intervalDays.toLong()).toString()
                        onUpdate(schedule.copy(nextDate = next))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Event, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Programar próxima medición", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MEASUREMENT ENTRY CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MeasurementEntryCard(
    entry: BodyMeasurementEntry,
    onDelete: () -> Unit,
) {
    val dateLabel = try {
        java.time.LocalDate.parse(entry.date)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault()))
    } catch (_: Exception) { entry.date }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = TEAL, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dateLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            // Main metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                entry.weight?.let { MeasurementChip("Peso", "${r1(it)} kg", WEIGHT_COLOR) }
                entry.bodyFat?.let { MeasurementChip("% Grasa", "${r1(it)}%", BODYFAT_COLOR) }
                entry.muscleMass?.let { MeasurementChip("% Músculo", "${r1(it)}%", MUSCLE_COLOR) }
            }
            // Circumferences
            val hasCirfs = listOf(entry.waistCm, entry.hipCm, entry.chestCm, entry.armCm, entry.thighCm).any { it != null }
            if (hasCirfs) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    entry.waistCm?.let { MeasurementChip("Cintura", "${r1(it)} cm", Color(0xFF78909C)) }
                    entry.hipCm?.let { MeasurementChip("Cadera", "${r1(it)} cm", Color(0xFF78909C)) }
                    entry.chestCm?.let { MeasurementChip("Pecho", "${r1(it)} cm", Color(0xFF78909C)) }
                    entry.armCm?.let { MeasurementChip("Brazo", "${r1(it)} cm", Color(0xFF78909C)) }
                    entry.thighCm?.let { MeasurementChip("Muslo", "${r1(it)} cm", Color(0xFF78909C)) }
                }
            }
            if (entry.notes != null) {
                Spacer(Modifier.height(6.dp))
                Text(entry.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MeasurementChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ═══════════════════════════════════════════════════════════════════════
// ADD MEASUREMENT SHEET
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMeasurementSheet(
    onDismiss: () -> Unit,
    onSave: (BodyMeasurementEntry) -> Unit,
) {
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var muscleMass by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var neck by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Registrar Medición", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM yyyy", java.util.Locale.getDefault())
                    ).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { MeasurementSectionLabel("Peso y composición") }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeasurementField("Peso (kg)", weight, { weight = it }, Modifier.weight(1f))
                    MeasurementField("% Grasa", bodyFat, { bodyFat = it }, Modifier.weight(1f))
                    MeasurementField("% Músculo", muscleMass, { muscleMass = it }, Modifier.weight(1f))
                }
            }

            item { MeasurementSectionLabel("Circunferencias (cm) — opcional") }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeasurementField("Cintura", waist, { waist = it }, Modifier.weight(1f))
                    MeasurementField("Cadera", hip, { hip = it }, Modifier.weight(1f))
                    MeasurementField("Pecho", chest, { chest = it }, Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeasurementField("Brazo", arm, { arm = it }, Modifier.weight(1f))
                    MeasurementField("Muslo", thigh, { thigh = it }, Modifier.weight(1f))
                    MeasurementField("Cuello", neck, { neck = it }, Modifier.weight(1f))
                }
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notas (opcional)") },
                    maxLines = 2,
                )
            }

            item {
                val hasData = weight.isNotBlank() || bodyFat.isNotBlank() || waist.isNotBlank()
                Button(
                    onClick = {
                        val entry = BodyMeasurementEntry(
                            id = UUID.randomUUID().toString(),
                            date = java.time.LocalDate.now().toString(),
                            weight = weight.replace(",", ".").toDoubleOrNull(),
                            bodyFat = bodyFat.replace(",", ".").toDoubleOrNull(),
                            muscleMass = muscleMass.replace(",", ".").toDoubleOrNull(),
                            waistCm = waist.replace(",", ".").toDoubleOrNull(),
                            hipCm = hip.replace(",", ".").toDoubleOrNull(),
                            chestCm = chest.replace(",", ".").toDoubleOrNull(),
                            armCm = arm.replace(",", ".").toDoubleOrNull(),
                            thighCm = thigh.replace(",", ".").toDoubleOrNull(),
                            neckCm = neck.replace(",", ".").toDoubleOrNull(),
                            notes = notes.ifBlank { null },
                        )
                        onSave(entry)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = hasData,
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GUARDAR MEDICIÓN", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MeasurementSectionLabel(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

// ═══════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════

private fun r1(v: Double): String {
    return (kotlin.math.round(v * 10) / 10.0).toString()
}
