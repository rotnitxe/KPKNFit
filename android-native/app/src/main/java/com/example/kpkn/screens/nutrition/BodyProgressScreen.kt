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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.calculations.calculateFFMI
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.body.goalProgressPercent
import com.example.kpkn.domain.body.dailyMedianSeries
import com.example.kpkn.domain.body.ewmaTrend
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.ui.components.KpknSheet
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════
// COLORS
// ═══════════════════════════════════════════════════════════════════════

private val TEAL = Color(0xFFCED4DC)
private val WEIGHT_COLOR = Color(0xFFF3F4F6)
private val BODYFAT_COLOR = Color(0xFFC9CED6)
private val MUSCLE_COLOR = Color(0xFF939AA4)
private val FFMI_COLOR = Color(0xFF6F7782)
private val BODY_NEUTRAL_ACCENT = Color(0xFFE8EBEF)
private val BODY_NEUTRAL_MUTED = Color(0xFFB7BEC8)
private val BODY_NEUTRAL_DARK = Color(0xFF5D6570)

// ═══════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════

private data class MeasurementEntry(
    val date: String,
    val weight: Double?,
    val bodyFat: Double?,
    val muscleMass: Double?,
)

private enum class BodyHeroMetric {
    WEIGHT,
    BODY_FAT,
}

private data class BodyMetricPoint(
    val date: String,
    val value: Double,
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
    onCreatePlan: () -> Unit = {},
) {
    val nutritionRepo = NutritionRepository.getInstance()
    val bodyViewModel: BodyProgressViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val vitals = settings.userVitals
    val plans by nutritionRepo.nutritionPlans.collectAsState()
    val activePlanId by nutritionRepo.activeNutritionPlanId.collectAsState()
    val activePlan = plans.find { it.id == activePlanId }
    val bodyMeasurements by bodyViewModel.legacyEntries.collectAsState()
    val bodyUiState by bodyViewModel.uiState.collectAsState()
    val schedule by bodyViewModel.measurementSchedule.collectAsState()

    var showAddMeasurement by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<BodyMeasurementEntry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var heroMetric by rememberSaveable { mutableStateOf(BodyHeroMetric.WEIGHT) }
    val contextualBottomBarClearance = 110.dp

    // Compute derived body metrics
    val composition = bodyUiState.latestComposition
    val weight = composition?.weightKg ?: bodyUiState.latestByMetric[BodyMetric.WEIGHT]?.valueSi
    val height = vitals.height
    // Composition KPIs must be from a compatible session/timestamp; do not
    // combine an old scale weight with a newer body-fat reading.
    val bodyFat = composition?.bodyFatPercent
        ?: bodyUiState.latestByMetric[BodyMetric.BODY_FAT_PERCENT]?.valueSi
    val muscle = composition?.muscleMassPercent
        ?: bodyUiState.latestByMetric[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
    val independentGoal = bodyUiState.goals.firstOrNull()
    val independentGoalMetric = independentGoal?.metric?.let {
        when (it) {
            BodyMetric.WEIGHT -> GoalMetric.WEIGHT
            BodyMetric.BODY_FAT_PERCENT -> GoalMetric.BODY_FAT
            BodyMetric.MUSCLE_MASS_PERCENT -> GoalMetric.MUSCLE_MASS
            else -> null
        }
    }
    val goalType = activePlan?.typedBodyGoal?.metric
        ?: activePlan?.goalType
        ?: independentGoalMetric
        ?: GoalMetric.WEIGHT
    // Target value interpreted by goalType: kg for WEIGHT, % for BODY_FAT/MUSCLE_MASS.
    // Manual goals remain visible even when no nutrition plan is active.
    val typedTarget = activePlan?.typedBodyGoal?.targetValueSi
        ?: activePlan?.goalValue?.takeIf { it > 0.0 }
        ?: independentGoal?.targetValueSi
    val targetWeight = typedTarget?.takeIf { goalType == GoalMetric.WEIGHT }
    val targetBodyFat = typedTarget?.takeIf { goalType == GoalMetric.BODY_FAT }
    val targetMuscle = typedTarget?.takeIf { goalType == GoalMetric.MUSCLE_MASS }

    val bmi = bodyUiState.bmi
    val ffmiResult = if (composition != null && weight != null && height != null && bodyFat != null) {
        calculateFFMI(heightCm = height, weightKg = weight, bodyFatPercent = bodyFat)
    } else {
        null
    }
    val ffmi = ffmiResult?.normalizedFfmi
    val lbm = ffmiResult?.leanBodyMass
    val fatMass = if (weight != null && bodyFat != null)
        weight * (bodyFat / 100) else null

    val sortedMeasurements = remember(bodyMeasurements) {
        bodyMeasurements.sortedBy { it.date }
    }
    val chartObservations = remember(bodyUiState.observations, bodyUiState.range) {
        val now = System.currentTimeMillis()
        val rangeDays = bodyUiState.range.days
        bodyUiState.observations.filter { observation ->
            val age = now - observation.timestampEpochMs
            rangeDays == null || (age >= 0L && age <= rangeDays * 86_400_000L)
        }
    }
    val weightSeries = remember(chartObservations) {
        dailyMedianSeries(chartObservations.filter { it.metric == BodyMetric.WEIGHT }, ZoneId.systemDefault())
            .map { BodyMetricPoint(it.date.toString(), it.value) }
    }
    val bodyFatSeries = remember(chartObservations) {
        dailyMedianSeries(chartObservations.filter { it.metric == BodyMetric.BODY_FAT_PERCENT }, ZoneId.systemDefault())
            .map { BodyMetricPoint(it.date.toString(), it.value) }
    }
    val weightTrendSeries = remember(weightSeries) { smoothBodySeries(weightSeries) }
    val bodyFatTrendSeries = remember(bodyFatSeries) { smoothBodySeries(bodyFatSeries) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item {
                ProgressHero(
                    selectedMetric = heroMetric,
                    onMetricChange = { heroMetric = it },
                    weightSeries = weightSeries,
                    bodyFatSeries = bodyFatSeries,
                    weight = weight,
                    bodyFat = bodyFat,
                    muscle = muscle,
                    bmi = bmi,
                    ffmi = ffmi,
                    targetWeight = targetWeight,
                    targetBodyFat = targetBodyFat,
                    plan = activePlan,
                )
            }

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

            item {
                MeasurementSectionLabel("Métricas y biometría")
            }

            if (ffmi != null) {
                item {
                    FfmiGaugeCard(ffmi = ffmi, gender = vitals.gender)
                }
            }

            if (lbm != null && fatMass != null && weight != null) {
                item {
                    CompositionBreakdownCard(
                        weight = weight,
                        lbm = lbm,
                        fatMass = fatMass,
                    )
                }
            }

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

            if (bmi != null) {
                item {
                    BmiCategoryCard(bmi = bmi)
                }
            }

            item {
                TipsCard(bodyFat = bodyFat, ffmi = ffmi, bmi = bmi)
            }

            item {
                MeasurementScheduleCard(
                    schedule = schedule,
                    onUpdate = bodyViewModel::updateSchedule,
                )
            }

            item {
                MeasurementSectionLabel("Historial de medidas")
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(
                        onClick = {
                            editingEntry = null
                            showAddMeasurement = true
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Registrar medidas", fontWeight = FontWeight.Black)
                    }
                }
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
                            TextButton(onClick = onCreatePlan) {
                                Text("Crear plan (opcional)")
                            }
                        }
                    }
                }
            } else {
                items(bodyMeasurements.sortedByDescending { it.date }) { entry ->
                    val manualEntry = !entry.id.startsWith("health_connect:") && !entry.id.startsWith("instant:")
                    MeasurementEntryCard(
                        entry = entry,
                        onEdit = if (manualEntry) ({ editingEntry = entry }) else null,
                        onDelete = {
                            bodyViewModel.deleteLegacyMeasurement(entry.id)
                            snackbarScope.launch {
                                if (manualEntry) {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Medición eliminada",
                                        actionLabel = "Deshacer",
                                        withDismissAction = true,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        bodyViewModel.addLegacyMeasurement(entry)
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Medición eliminada")
                                }
                            }
                        },
                    )
                }
            }

            item {
                MeasurementSectionLabel("Gráficos")
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BodyChartRange.entries.forEach { chartRange ->
                        FilterChip(
                            selected = bodyUiState.range == chartRange,
                            onClick = { bodyViewModel.selectRange(chartRange) },
                            label = {
                                Text(
                                    when (chartRange) {
                                        BodyChartRange.ONE_MONTH -> "1M"
                                        BodyChartRange.THREE_MONTHS -> "3M"
                                        BodyChartRange.SIX_MONTHS -> "6M"
                                        BodyChartRange.ONE_YEAR -> "1A"
                                        BodyChartRange.ALL -> "Todo"
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                BodyMetricTrendChartCard(
                    title = "Peso corporal",
                    subtitle = "Evolución según tus registros",
                    points = weightSeries,
                    trend = weightTrendSeries,
                    unit = "kg",
                    color = WEIGHT_COLOR,
                    targetValue = targetWeight,
                    emptyMessage = "Registra peso para ver su evolución en el tiempo.",
                )
            }

            item {
                BodyMetricTrendChartCard(
                    title = "% de grasa",
                    subtitle = "Cambio relativo en composición",
                    points = bodyFatSeries,
                    trend = bodyFatTrendSeries,
                    unit = "%",
                    color = BODYFAT_COLOR,
                    targetValue = targetBodyFat,
                    emptyMessage = "Agrega mediciones de grasa corporal para activar este gráfico.",
                )
            }

            item { Spacer(Modifier.height(100.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editingEntry = null
                showAddMeasurement = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contextualBottomBarClearance),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Registrar medición") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contextualBottomBarClearance + 64.dp),
        )
    }

    // ── Add Measurement Sheet ────────────────────────────────────────────────
    if (showAddMeasurement || editingEntry != null) {
        AddMeasurementSheet(
            initialEntry = editingEntry,
            onDismiss = {
                showAddMeasurement = false
                editingEntry = null
            },
            onSave = { entry ->
                val original = editingEntry
                if (original == null) {
                    bodyViewModel.addLegacyMeasurement(entry)
                } else {
                    bodyViewModel.replaceLegacyMeasurement(original.id, entry)
                }
                showAddMeasurement = false
                editingEntry = null
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HERO — Big stats at top
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ProgressHero(
    selectedMetric: BodyHeroMetric,
    onMetricChange: (BodyHeroMetric) -> Unit,
    weightSeries: List<BodyMetricPoint>,
    bodyFatSeries: List<BodyMetricPoint>,
    weight: Double?,
    bodyFat: Double?,
    muscle: Double?,
    bmi: Double?,
    ffmi: Double?,
    targetWeight: Double?,
    targetBodyFat: Double?,
    plan: NutritionPlan?,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val points = if (selectedMetric == BodyHeroMetric.WEIGHT) weightSeries else bodyFatSeries
    val accentColor = if (selectedMetric == BodyHeroMetric.WEIGHT) BODY_NEUTRAL_ACCENT else BODY_NEUTRAL_MUTED
    val metricLabel = if (selectedMetric == BodyHeroMetric.WEIGHT) "Peso corporal" else "% de grasa"
    val metricUnit = if (selectedMetric == BodyHeroMetric.WEIGHT) "kg" else "%"
    val currentValue = when (selectedMetric) {
        BodyHeroMetric.WEIGHT -> weight
        BodyHeroMetric.BODY_FAT -> bodyFat
    }
    val targetValue = when (selectedMetric) {
        BodyHeroMetric.WEIGHT -> targetWeight
        BodyHeroMetric.BODY_FAT -> targetBodyFat
    }
    val startValue = points.firstOrNull()?.value
    val deltaValue = if (currentValue != null && startValue != null) currentValue - startValue else null
    val progressPct = calculateGoalProgress(startValue, currentValue, targetValue)
    val latestDateLabel = points.lastOrNull()?.date?.let(::formatHeroDate) ?: "Sin registros"
    val weeklyRateLabel = when {
        plan == null -> "Sin ritmo"
        selectedMetric == BodyHeroMetric.WEIGHT -> "${r1(plan.weeklyChangeKg)} kg/sem"
        weight != null && weight > 0 -> "${r1(plan.weeklyChangeKg / weight * 100)} %/sem"
        else -> "Sin ritmo"
    }
    val counterpartLabel = if (selectedMetric == BodyHeroMetric.WEIGHT) "% grasa" else "Peso"
    val counterpartValue = if (selectedMetric == BodyHeroMetric.WEIGHT) {
        bodyFat?.let { "${r1(it)}%" } ?: "—"
    } else {
        weight?.let { "${r1(it)} kg" } ?: "—"
    }
    val deltaLabel = when {
        deltaValue == null -> "Sin tendencia"
        deltaValue == 0.0 -> "Sin cambio"
        else -> {
            val direction = if (deltaValue > 0) "+" else ""
            "$direction${r1(deltaValue)} $metricUnit"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        BODY_NEUTRAL_MUTED.copy(alpha = 0.08f),
                        Color.Transparent,
                    )
                )
            )
            .padding(start = 20.dp, end = 20.dp, top = topInset + 18.dp, bottom = 20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        latestDateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Cuerpo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroTogglePill(
                        label = "Peso",
                        selected = selectedMetric == BodyHeroMetric.WEIGHT,
                        activeColor = BODY_NEUTRAL_ACCENT,
                        onClick = { onMetricChange(BodyHeroMetric.WEIGHT) },
                    )
                    HeroTogglePill(
                        label = "% grasa",
                        selected = selectedMetric == BodyHeroMetric.BODY_FAT,
                        activeColor = BODY_NEUTRAL_MUTED,
                        onClick = { onMetricChange(BodyHeroMetric.BODY_FAT) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .width(140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BodyTrendSparkline(
                        points = points,
                        color = accentColor,
                        targetValue = targetValue,
                        unit = metricUnit,
                        modifier = Modifier.size(140.dp),
                        compact = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        currentValue?.let { "${r1(it)}" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                    Text(
                        "/ ${targetValue?.let { r1(it) } ?: "—"} $metricUnit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BodyHeroDetailRow(
                        label = metricLabel,
                        primary = currentValue?.let { "${r1(it)} $metricUnit" } ?: "Sin datos",
                        secondary = targetValue?.let { "Meta ${r1(it)} $metricUnit" } ?: "Sin meta",
                        color = accentColor,
                        progress = progressPct?.div(100f),
                    )
                    BodyHeroDetailRow(
                        label = "Cambio",
                        primary = deltaLabel,
                        secondary = "Desde el primer registro",
                        color = BODY_NEUTRAL_MUTED,
                    )
                    BodyHeroDetailRow(
                        label = "Ritmo",
                        primary = weeklyRateLabel,
                        secondary = counterpartLabel + " " + counterpartValue,
                        color = BODY_NEUTRAL_DARK,
                    )

                    Spacer(Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = (if ((progressPct ?: 0) >= 50) BODY_NEUTRAL_ACCENT else BODY_NEUTRAL_MUTED).copy(alpha = 0.10f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if ((progressPct ?: 0) >= 50) Icons.Default.CheckCircle else Icons.Default.Timeline,
                                null,
                                tint = if ((progressPct ?: 0) >= 50) BODY_NEUTRAL_ACCENT else BODY_NEUTRAL_MUTED,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (progressPct != null) "$progressPct% hacia tu meta · FFMI ${ffmi?.let(::r1) ?: "—"}"
                                else "FFMI ${ffmi?.let(::r1) ?: "—"} · % músculo ${muscle?.let { "${r1(it)}%" } ?: "—"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if ((progressPct ?: 0) >= 50) BODY_NEUTRAL_ACCENT else BODY_NEUTRAL_MUTED,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = color.copy(alpha = 0.10f),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
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

@Composable
private fun HeroTogglePill(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroInsightChip(label: String, value: String, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.10f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = accentColor,
            )
        }
    }
}

@Composable
private fun BodyHeroDetailRow(
    label: String,
    primary: String,
    secondary: String,
    color: Color,
    progress: Float? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            secondary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 14.dp, top = 1.dp),
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            drawRoundRect(
                color = color.copy(alpha = 0.12f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
            val fill = ((progress ?: 0.45f).coerceIn(0.12f, 1f)) * size.width
            drawRoundRect(
                color = color,
                size = Size(fill, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun BodyTrendSparkline(
    points: List<BodyMetricPoint>,
    color: Color,
    targetValue: Double?,
    unit: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val minValue = minOf(points.minOfOrNull { it.value } ?: targetValue ?: 0.0, targetValue ?: Double.MAX_VALUE)
        .takeIf { it != Double.MAX_VALUE } ?: 0.0
    val maxValue = maxOf(points.maxOfOrNull { it.value } ?: targetValue ?: 1.0, targetValue ?: Double.MIN_VALUE)
        .takeIf { it != Double.MIN_VALUE } ?: 1.0
    val valueRange = (maxValue - minValue).takeIf { it > 0 } ?: 1.0

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = color.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Avance en el tiempo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (points.isNotEmpty()) "${points.size} registro(s)" else "Sin historial",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 92.dp else 120.dp),
            ) {
                if (points.isEmpty()) return@Canvas
                val width = size.width
                val height = size.height
                val horizontalPadding = 8.dp.toPx()
                val usableWidth = (width - horizontalPadding * 2).coerceAtLeast(1f)
                val pointDates = points.mapIndexed { index, point ->
                    bodyDateEpochDay(point.date) ?: index.toLong()
                }
                val minDate = pointDates.minOrNull() ?: 0L
                val maxDate = pointDates.maxOrNull() ?: minDate
                val dateSpan = (maxDate - minDate).coerceAtLeast(1L)

                fun pointOffset(index: Int, value: Double): Offset {
                    val date = pointDates.getOrElse(index) { minDate + index }
                    val dateFraction = if (maxDate == minDate) 0.5f
                    else ((date - minDate).toDouble() / dateSpan.toDouble()).toFloat()
                    val x = horizontalPadding + usableWidth * dateFraction.coerceIn(0f, 1f)
                    val normalized = ((value - minValue) / valueRange).toFloat()
                    val y = height - normalized * (height - 12.dp.toPx()) - 6.dp.toPx()
                    return Offset(x, y)
                }

                if (targetValue != null) {
                    val targetNormalized = ((targetValue - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                    val targetY = height - targetNormalized * (height - 12.dp.toPx()) - 6.dp.toPx()
                    drawLine(
                        color = color.copy(alpha = 0.35f),
                        start = Offset(0f, targetY),
                        end = Offset(width, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }

                points.forEachIndexed { index, point ->
                    if (index == 0) return@forEachIndexed
                    drawLine(
                        color = color,
                        start = pointOffset(index - 1, points[index - 1].value),
                        end = pointOffset(index, point.value),
                        strokeWidth = 3.dp.toPx(),
                    )
                }

                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = pointOffset(index, point.value),
                    )
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = pointOffset(index, point.value),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (points.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatShortDate(points.first().date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        points.last().let { "${r1(it.value)} $unit" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyMetricTrendChartCard(
    title: String,
    subtitle: String,
    points: List<BodyMetricPoint>,
    trend: List<BodyMetricPoint> = emptyList(),
    unit: String,
    color: Color,
    targetValue: Double? = null,
    emptyMessage: String,
) {
    val minValue = minOf(points.minOfOrNull { it.value } ?: targetValue ?: 0.0, targetValue ?: Double.MAX_VALUE)
        .takeIf { it != Double.MAX_VALUE } ?: 0.0
    val maxValue = maxOf(points.maxOfOrNull { it.value } ?: targetValue ?: 1.0, targetValue ?: Double.MIN_VALUE)
        .takeIf { it != Double.MIN_VALUE } ?: 1.0
    val valueRange = (maxValue - minValue).takeIf { it > 0 } ?: 1.0
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (points.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = color.copy(alpha = 0.06f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = color.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            emptyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp),
                ) {
                    val width = size.width
                    val height = size.height
                    val leftPadding = 10.dp.toPx()
                    val rightPadding = 10.dp.toPx()
                    val topPadding = 10.dp.toPx()
                    val bottomPadding = 18.dp.toPx()
                    val usableWidth = (width - leftPadding - rightPadding).coerceAtLeast(1f)
                    val usableHeight = (height - topPadding - bottomPadding).coerceAtLeast(1f)
                    val allSeries = points + trend
                    val seriesDates = allSeries.mapIndexed { index, point ->
                        bodyDateEpochDay(point.date) ?: index.toLong()
                    }
                    val minDate = seriesDates.minOrNull() ?: 0L
                    val maxDate = seriesDates.maxOrNull() ?: minDate
                    val dateSpan = (maxDate - minDate).coerceAtLeast(1L)

                    fun pointOffset(point: BodyMetricPoint, fallbackIndex: Int): Offset {
                        val normalized = ((point.value - minValue) / valueRange).toFloat()
                        val date = bodyDateEpochDay(point.date) ?: (minDate + fallbackIndex)
                        val dateFraction = if (maxDate == minDate) 0.5f
                        else ((date - minDate).toDouble() / dateSpan.toDouble()).toFloat()
                        val x = leftPadding + usableWidth * dateFraction.coerceIn(0f, 1f)
                        val y = topPadding + usableHeight - usableHeight * normalized
                        return Offset(x, y)
                    }

                    repeat(3) { gridIndex ->
                        val y = topPadding + usableHeight * (gridIndex / 2f)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPadding, y),
                            end = Offset(width - rightPadding, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    if (targetValue != null) {
                        val targetNormalized = ((targetValue - minValue) / valueRange).toFloat().coerceIn(0f, 1f)
                        val targetY = topPadding + usableHeight - usableHeight * targetNormalized
                        drawLine(
                            color = color.copy(alpha = 0.35f),
                            start = Offset(leftPadding, targetY),
                            end = Offset(width - rightPadding, targetY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        )
                    }

                    fun drawSeries(series: List<BodyMetricPoint>, strokeWidth: Float, seriesColor: Color, dashed: Boolean = false) {
                        series.forEachIndexed { index, point ->
                            if (index == 0) return@forEachIndexed
                            val effect = if (dashed) {
                                androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 7f))
                            } else null
                            drawLine(
                                color = seriesColor,
                                start = pointOffset(series[index - 1], index - 1),
                                end = pointOffset(point, index),
                                strokeWidth = strokeWidth,
                                pathEffect = effect,
                            )
                        }
                    }

                    drawSeries(trend, 2.dp.toPx(), color.copy(alpha = 0.72f), dashed = true)
                    drawSeries(points, 3.dp.toPx(), color)
                    points.forEachIndexed { index, point ->
                        drawCircle(Color.White, 5.dp.toPx(), pointOffset(point, index))
                        drawCircle(color, 3.dp.toPx(), pointOffset(point, index))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                    Text(
                        points.firstOrNull()?.date?.let(::formatShortDate) ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        points.lastOrNull()?.let { "${r1(it.value)} $unit" } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
                Text(
                    "Puntos diarios · tendencia EWMA 7 días · línea discontinua = tendencia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

    val weeklyRateKg = plan?.weeklyChangeKg ?: 0.0
    // Composition plans persist percentage points/week; never convert through
    // a guessed 70 kg body weight or label it as kg/week.
    val weeklyRateInUnit = when (goalType) {
        GoalMetric.WEIGHT -> weeklyRateKg.takeIf { it > 0.0 } ?: 0.0
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> weeklyRateKg
    }
    val weeklyRateLabel = when (goalType) {
        GoalMetric.WEIGHT -> weeklyRateInUnit?.let { "${r1(it)} kg/sem" } ?: "Calibrando"
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> if (plan?.weeklyChangeUnit == "percentage-points/week" && weeklyRateInUnit > 0.0) "${r1(weeklyRateInUnit)} puntos/sem" else "Calibrando"
    }

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
                InfoChip("Proyección", "Sin fecha", Modifier.weight(1f))
                InfoChip("Estado", "Calibrando", Modifier.weight(1f))
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
    onEdit: (() -> Unit)?,
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
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
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
    initialEntry: BodyMeasurementEntry? = null,
    onDismiss: () -> Unit,
    onSave: (BodyMeasurementEntry) -> Unit,
) {
    val entryKey = initialEntry?.id ?: "new"
    val displayDate = runCatching {
        java.time.LocalDate.parse(initialEntry?.date ?: java.time.LocalDate.now().toString())
    }.getOrDefault(java.time.LocalDate.now())
    var weight by remember(entryKey) { mutableStateOf(initialEntry?.weight?.let(::r1).orEmpty()) }
    var bodyFat by remember(entryKey) { mutableStateOf(initialEntry?.bodyFat?.let(::r1).orEmpty()) }
    var muscleMass by remember(entryKey) { mutableStateOf(initialEntry?.muscleMass?.let(::r1).orEmpty()) }
    var waist by remember(entryKey) { mutableStateOf(initialEntry?.waistCm?.let(::r1).orEmpty()) }
    var hip by remember(entryKey) { mutableStateOf(initialEntry?.hipCm?.let(::r1).orEmpty()) }
    var chest by remember(entryKey) { mutableStateOf(initialEntry?.chestCm?.let(::r1).orEmpty()) }
    var arm by remember(entryKey) { mutableStateOf(initialEntry?.armCm?.let(::r1).orEmpty()) }
    var thigh by remember(entryKey) { mutableStateOf(initialEntry?.thighCm?.let(::r1).orEmpty()) }
    var neck by remember(entryKey) { mutableStateOf(initialEntry?.neckCm?.let(::r1).orEmpty()) }
    var notes by remember(entryKey) { mutableStateOf(initialEntry?.notes.orEmpty()) }

    KpknSheet(
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    if (initialEntry == null) "Registrar Medición" else "Editar Medición",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    displayDate.format(
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
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notas (opcional)") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1C1C1C),
                        unfocusedContainerColor = Color(0xFF1C1C1C),
                        disabledContainerColor = Color(0xFF121212),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            item {
                val hasData = listOf(weight, bodyFat, muscleMass, waist, hip, chest, arm, thigh, neck)
                    .any { it.isNotBlank() }
                val parsed = listOfNotNull(
                    weight.takeIf { it.isNotBlank() }?.let { BodyMetric.WEIGHT to parseLocalizedNumber(it) },
                    bodyFat.takeIf { it.isNotBlank() }?.let { BodyMetric.BODY_FAT_PERCENT to parseLocalizedNumber(it) },
                    muscleMass.takeIf { it.isNotBlank() }?.let { BodyMetric.MUSCLE_MASS_PERCENT to parseLocalizedNumber(it) },
                    waist.takeIf { it.isNotBlank() }?.let { BodyMetric.WAIST to parseLocalizedNumber(it) },
                    hip.takeIf { it.isNotBlank() }?.let { BodyMetric.HIP to parseLocalizedNumber(it) },
                    chest.takeIf { it.isNotBlank() }?.let { BodyMetric.CHEST to parseLocalizedNumber(it) },
                    arm.takeIf { it.isNotBlank() }?.let { BodyMetric.ARM to parseLocalizedNumber(it) },
                    thigh.takeIf { it.isNotBlank() }?.let { BodyMetric.THIGH to parseLocalizedNumber(it) },
                    neck.takeIf { it.isNotBlank() }?.let { BodyMetric.NECK to parseLocalizedNumber(it) },
                )
                val invalid = parsed.any { (metric, value) -> value == null || !validateBodyValue(metric, value).valid }
                if (invalid) {
                    Text("Revisa los valores: deben ser finitos y estar dentro de rangos válidos.", color = Color(0xFFFF9E9E), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = {
                        val entry = BodyMeasurementEntry(
                            id = initialEntry?.id ?: UUID.randomUUID().toString(),
                            date = initialEntry?.date ?: java.time.LocalDate.now().toString(),
                            weight = parseLocalizedNumber(weight),
                            bodyFat = parseLocalizedNumber(bodyFat),
                            muscleMass = parseLocalizedNumber(muscleMass),
                            waistCm = parseLocalizedNumber(waist),
                            hipCm = parseLocalizedNumber(hip),
                            chestCm = parseLocalizedNumber(chest),
                            armCm = parseLocalizedNumber(arm),
                            thighCm = parseLocalizedNumber(thigh),
                            neckCm = parseLocalizedNumber(neck),
                            notes = notes.ifBlank { null },
                        )
                        onSave(entry)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = hasData && !invalid,
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
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF1C1C1C),
            unfocusedContainerColor = Color(0xFF1C1C1C),
            disabledContainerColor = Color(0xFF121212),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

// ═══════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════

private fun calculateGoalProgress(
    startValue: Double?,
    currentValue: Double?,
    targetValue: Double?,
): Int? {
    if (startValue == null || currentValue == null || targetValue == null) return null
    return goalProgressPercent(startValue, currentValue, targetValue)
}

private fun formatShortDate(raw: String): String {
    return try {
        java.time.LocalDate.parse(raw)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    } catch (_: Exception) {
        raw
    }
}

private fun formatHeroDate(raw: String): String {
    return try {
        java.time.LocalDate.parse(raw)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", java.util.Locale.getDefault()))
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        raw
    }
}

private fun r1(v: Double): String {
    return (kotlin.math.round(v * 10) / 10.0).toString()
}

private fun bodyDateEpochDay(raw: String): Long? =
    runCatching { java.time.LocalDate.parse(raw).toEpochDay() }.getOrNull()

private fun smoothBodySeries(points: List<BodyMetricPoint>): List<BodyMetricPoint> =
    ewmaTrend(
        points.mapNotNull { point ->
            runCatching {
                com.example.kpkn.domain.body.BodyMetricPoint(
                    date = java.time.LocalDate.parse(point.date),
                    value = point.value,
                    sourceObservationIds = emptyList(),
                )
            }.getOrNull()
        },
    ).map { point -> BodyMetricPoint(point.date.toString(), point.value) }
