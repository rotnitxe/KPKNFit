package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kpkn.screens.body.design.BODY_REMINDER_PRESET_DAYS
import com.example.kpkn.screens.body.design.BodyCompositionModule
import com.example.kpkn.screens.body.design.BodyFatModule
import com.example.kpkn.screens.body.design.BodyGoalDraft
import com.example.kpkn.screens.body.design.BodyGoalModule
import com.example.kpkn.screens.body.design.BodyGoalRemoveDialog
import com.example.kpkn.screens.body.design.BodyGoalSheet
import com.example.kpkn.screens.body.design.BodyInfoSheet
import com.example.kpkn.screens.body.design.BodyInfoTopic
import com.example.kpkn.screens.body.design.BodyIsland
import com.example.kpkn.screens.body.design.BodyPageHeader
import com.example.kpkn.screens.body.design.BodyReminderModule
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.body.design.bodyIslandChangeText
import com.example.kpkn.screens.body.design.bodyInfoFor
import com.example.kpkn.screens.body.design.buildBodyChartPoints
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.ui.design.rememberIslandVisible
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
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
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.calculations.calculateFFMI
import com.example.kpkn.domain.body.BmiCategory
import com.example.kpkn.domain.body.BodyGoalTrack
import com.example.kpkn.domain.body.BodyWeightSummary
import com.example.kpkn.domain.body.bmiCategory
import com.example.kpkn.domain.body.roundBmi1
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════
// COLORS
// ═══════════════════════════════════════════════════════════════════════

private val TEAL = Color(0xFFCED4DC)
private val WEIGHT_COLOR = Color(0xFFF3F4F6)
private val BODYFAT_COLOR = Color(0xFFC9CED6)
private val MUSCLE_COLOR = Color(0xFF939AA4)
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

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyProgressScreen(
    onCreatePlan: () -> Unit = {},
    onAddHeight: () -> Unit = {},
    onEditNutritionPlan: (String) -> Unit = {},
) {
    val bodyViewModel: BodyProgressViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val vitals = settings.userVitals
    // Recorrido de Cuerpo: se lanza al montar la pantalla (no hay señal de carga en el ViewModel). Visto = no arranca.
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(Unit) { tourHost?.startTour(TourIds.BODY, TourScripts.body) }
    val bodyMeasurements by bodyViewModel.legacyEntries.collectAsState()
    val bodyUiState by bodyViewModel.uiState.collectAsState()
    val schedule by bodyViewModel.measurementSchedule.collectAsState()

    var showAddMeasurement by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<BodyMeasurementEntry?>(null) }
    // Hojas y diálogos de los módulos de Cuerpo: objetivo propio, quitarlo, explicación de una cifra y la hora del recordatorio.
    var goalDraft by remember { mutableStateOf<BodyGoalDraft?>(null) }
    var goalRemoveConfirm by remember { mutableStateOf(false) }
    var infoTopic by remember { mutableStateOf<BodyInfoTopic?>(null) }
    var reminderOther by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val contextualBottomBarClearance = 110.dp
    val listState = rememberLazyListState()
    val islandVisible = rememberIslandVisible(listState, afterItemIndex = 0)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val today = LocalDate.now()

    // Cifras de Cuerpo: el resumen del peso y la meta llegan ya calculados desde el ViewModel.
    val summary = bodyUiState.weightSummary
    val goal = bodyUiState.goal
    // Peso actual = último pesaje válido, esté o no en el rango. La composición sale de una sola sesión.
    val weight = summary.current
    val composition = bodyUiState.latestComposition
    val height = vitals.height
    val bodyFat = composition?.bodyFatPercent
        ?: bodyUiState.latestByMetric[BodyMetric.BODY_FAT_PERCENT]?.valueSi
    val muscle = composition?.muscleMassPercent
        ?: bodyUiState.latestByMetric[BodyMetric.MUSCLE_MASS_PERCENT]?.valueSi
    val targetWeight = goal?.takeIf { it.metric == GoalMetric.WEIGHT }?.targetValueSi
    val targetBodyFat = goal?.takeIf { it.metric == GoalMetric.BODY_FAT }?.targetValueSi
    // Objetivo propio (el que edita la hoja) y plan activo: con plan, la meta se edita en el editor del plan.
    val ownGoal = bodyUiState.goals.filter { it.linkedPlanId == null }.maxByOrNull { it.updatedAtEpochMs }
    val activePlanId = bodyUiState.activePlanId
    val weightUnit = if (settings.weightUnit == WeightUnit.LBS) WizardMassUnit.LB else WizardMassUnit.KG

    val bmi = bodyUiState.bmi
    // FFMI, masa magra y masa grasa solo con la composición de una misma sesión.
    val compositionBodyFat = composition?.bodyFatPercent
    val ffmiResult = if (composition != null && height != null && compositionBodyFat != null) {
        calculateFFMI(heightCm = height, weightKg = composition.weightKg, bodyFatPercent = compositionBodyFat)
    } else {
        null
    }
    val ffmi = ffmiResult?.normalizedFfmi
    val lbm = ffmiResult?.leanBodyMass
    val fatMass = if (composition != null && compositionBodyFat != null) {
        composition.weightKg * (compositionBodyFat / 100)
    } else {
        null
    }

    // Series de la gráfica: el ViewModel calcula cada serie y su tendencia una vez; aquí solo se unen por día.
    val weightPoints = remember(bodyUiState.weightSeries, bodyUiState.weightTrend) {
        buildBodyChartPoints(bodyUiState.weightSeries, bodyUiState.weightTrend)
    }
    val bodyFatPoints = remember(bodyUiState.bodyFatSeries, bodyUiState.bodyFatTrend) {
        buildBodyChartPoints(bodyUiState.bodyFatSeries, bodyUiState.bodyFatTrend)
    }
    val islandTitle = summary.current?.let { "${formatDecimalEs(it, 1)} kg" } ?: "Cuerpo"
    val islandSubtitle = bodyIslandChangeText(bodyUiState.weightSummary30Days)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KpknSurface.canvas),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            // Cabecera: es el primer elemento; cuando sale de la vista, aparece la isla.
            item {
                BodyPageHeader(
                    topInset = topInset,
                    onAdd = {
                        editingEntry = null
                        showAddMeasurement = true
                    },
                    addModifier = Modifier.tourAnchor(TourAnchors.BODY_PESAJES),
                )
            }

            if (!bodyUiState.loaded) {
                item { KpknSkeletonModule(lines = 3, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                item { KpknSkeletonModule(lines = 2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            } else {
                item {
                    BodyWeightModule(
                        summary = summary,
                        goalKg = targetWeight,
                        points = weightPoints,
                        range = bodyUiState.range,
                        today = today,
                        onRangeChange = bodyViewModel::selectRange,
                        onAdd = {
                            editingEntry = null
                            showAddMeasurement = true
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .kpknEntrance("body", 0)
                            .tourAnchor(TourAnchors.BODY_TENDENCIA),
                    )
                }
                if (bodyFat != null) {
                    item {
                        BodyFatModule(
                            current = bodyFat,
                            start = bodyUiState.bodyFatSeries.firstOrNull(),
                            points = bodyFatPoints,
                            goalPercent = targetBodyFat,
                            progressPercent = if (goal?.metric == GoalMetric.BODY_FAT) bodyUiState.goalTrack?.progressPercent else null,
                            eta = if (goal?.metric == GoalMetric.BODY_FAT) bodyUiState.goalTrack?.eta else null,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .kpknEntrance("body", 1),
                        )
                    }
                }

                // Composición, objetivo y recordatorio (BODY_V1 §2.4 a §2.6).
                item {
                    BodyCompositionModule(
                        weightKg = weight,
                        leanKg = lbm,
                        fatKg = fatMass,
                        bodyFatPercent = compositionBodyFat,
                        musclePercent = muscle,
                        ffmi = ffmi,
                        bmi = bmi,
                        hasHeight = height != null,
                        onAddHeight = onAddHeight,
                        onInfo = { topic -> infoTopic = topic },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .kpknEntrance("body", 2),
                    )
                }
                item {
                    BodyGoalModule(
                        goal = goal,
                        weightUnit = weightUnit,
                        activePlan = activePlanId != null,
                        onEditInPlan = { activePlanId?.let(onEditNutritionPlan) },
                        onEditOwn = {
                            goalDraft = if (goal != null) {
                                BodyGoalDraft(
                                    metric = goal.metric,
                                    target = goal.targetValueSi,
                                    existingId = ownGoal?.id,
                                    createdAtEpochMs = goal.createdAtEpochMs,
                                )
                            } else {
                                BodyGoalDraft(metric = GoalMetric.WEIGHT)
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .kpknEntrance("body", 3),
                    )
                }
                item {
                    BodyReminderModule(
                        schedule = schedule,
                        customInterval = reminderOther || schedule.intervalDays !in BODY_REMINDER_PRESET_DAYS,
                        onCustomIntervalChange = { reminderOther = it },
                        onUpdate = bodyViewModel::updateSchedule,
                        onEditTime = { showReminderTime = true },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .kpknEntrance("body", 4),
                    )
                }
            }

            item {
                MeasurementSectionLabel("Historial de medidas")
            }

            if (bodyMeasurements.isEmpty() && bodyUiState.loaded) {
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
                                    val result = snackbarHostState.showKpknSnackbar(
                                        message = "Medición eliminada",
                                        type = SnackbarType.SUCCESS,
                                        actionLabel = "Deshacer",
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        bodyViewModel.addLegacyMeasurement(entry)
                                    }
                                } else {
                                    snackbarHostState.showKpknSnackbar(
                                        message = "Medición eliminada",
                                        type = SnackbarType.SUCCESS,
                                    )
                                }
                            }
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
            }
        }

        // Isla superior de Cuerpo: aparece cuando la cabecera sale de la vista.
        BodyIsland(
            visible = islandVisible,
            title = islandTitle,
            subtitle = islandSubtitle,
            onAdd = {
                editingEntry = null
                showAddMeasurement = true
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contextualBottomBarClearance + 64.dp),
        ) { data ->
            KpknSnackbar(data)
        }

        // Hojas y diálogos de Cuerpo: objetivo propio, quitarlo, explicación de una cifra y la hora del recordatorio.
        goalDraft?.let { draft ->
            BodyGoalSheet(
                draft = draft,
                weightUnit = weightUnit,
                canRemove = ownGoal != null,
                onDraftChange = { next -> goalDraft = next },
                onSave = { newGoal ->
                    bodyViewModel.upsertGoal(newGoal)
                    // Un solo objetivo propio: los demás se retiran para que no vuelvan al quitar este.
                    bodyUiState.goals
                        .filter { it.linkedPlanId == null && it.id != newGoal.id }
                        .forEach { bodyViewModel.deleteGoal(it.id) }
                    goalDraft = null
                    snackbarScope.launch {
                        snackbarHostState.showKpknSnackbar(message = "Objetivo guardado", type = SnackbarType.SUCCESS)
                    }
                },
                onRemove = {
                    goalDraft = null
                    goalRemoveConfirm = true
                },
                onDismiss = { goalDraft = null },
            )
        }
        if (goalRemoveConfirm) {
            BodyGoalRemoveDialog(
                onConfirm = {
                    bodyUiState.goals.filter { it.linkedPlanId == null }.forEach { bodyViewModel.deleteGoal(it.id) }
                    goalRemoveConfirm = false
                    snackbarScope.launch {
                        snackbarHostState.showKpknSnackbar(message = "Objetivo quitado", type = SnackbarType.SUCCESS)
                    }
                },
                onDismiss = { goalRemoveConfirm = false },
            )
        }
        infoTopic?.let { topic ->
            BodyInfoSheet(
                info = bodyInfoFor(topic, ffmi = ffmi, bodyFatPercent = compositionBodyFat),
                onDismiss = { infoTopic = null },
            )
        }
        if (showReminderTime) {
            KpknNativeTimePickerDialog(
                title = "Hora del recordatorio",
                initialHour = schedule.reminderHour,
                initialMinute = schedule.reminderMinute,
                onConfirm = { hour, minute ->
                    bodyViewModel.updateSchedule(schedule.copy(reminderHour = hour, reminderMinute = minute))
                    showReminderTime = false
                },
                onDismiss = { showReminderTime = false },
            )
        }
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
    weightSummary: BodyWeightSummary,
    bodyFat: Double?,
    bodyFatWeeklyRate: Double?,
    goalTrack: BodyGoalTrack?,
    goalMetric: GoalMetric?,
    loaded: Boolean,
    muscle: Double?,
    bmi: Double?,
    ffmi: Double?,
    targetWeight: Double?,
    targetBodyFat: Double?,
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
    // Cambio desde el primer punto del rango, con su fecha. El peso lo da el resumen del ViewModel.
    val startValue = if (selectedMetric == BodyHeroMetric.WEIGHT) {
        weightSummary.rangeStart
    } else {
        points.firstOrNull()?.value
    }
    val startDate = if (selectedMetric == BodyHeroMetric.WEIGHT) {
        weightSummary.rangeStartDate
    } else {
        points.firstOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val deltaValue = when {
        selectedMetric == BodyHeroMetric.WEIGHT -> weightSummary.change
        currentValue != null && startValue != null -> currentValue - startValue
        else -> null
    }
    val rangeStartLabel = startDate?.let { "desde el ${formatDayMonthEs(it)}" } ?: ""
    // El avance hacia la meta solo se muestra para la métrica de la meta activa.
    val progressPct: Int? = when {
        selectedMetric == BodyHeroMetric.WEIGHT && goalMetric == GoalMetric.WEIGHT -> weightSummary.progressPercent
        selectedMetric == BodyHeroMetric.BODY_FAT && goalMetric == GoalMetric.BODY_FAT -> goalTrack?.progressPercent
        else -> null
    }
    val latestDateLabel = when {
        !loaded -> ""
        selectedMetric == BodyHeroMetric.WEIGHT -> weightSummary.currentDate?.toString()?.let(::formatHeroDate) ?: "Sin registros"
        else -> points.lastOrNull()?.date?.let(::formatHeroDate) ?: "Sin registros"
    }
    val rateLabel = when (selectedMetric) {
        BodyHeroMetric.WEIGHT -> weightSummary.measuredWeeklyRate?.let { "${formatSignedDecimalEs(it, 1)} kg/sem" }
        BodyHeroMetric.BODY_FAT -> bodyFatWeeklyRate?.let { "${formatSignedDecimalEs(it, 1)} puntos/sem" }
    } ?: "Sin ritmo medido aún"
    val counterpartLabel = if (selectedMetric == BodyHeroMetric.WEIGHT) "% grasa" else "Peso"
    val counterpartValue = if (selectedMetric == BodyHeroMetric.WEIGHT) {
        bodyFat?.let { "${r1(it)}%" } ?: "—"
    } else {
        weight?.let { "${r1(it)} kg" } ?: "—"
    }
    // Sin ritmo medido del peso, la línea dice cuántas semanas de pesajes faltan.
    val rateHint = if (selectedMetric == BodyHeroMetric.WEIGHT && weightSummary.measuredWeeklyRate == null) {
        "Hacen falta 2 semanas de pesajes para medir tu ritmo"
    } else {
        counterpartLabel + " " + counterpartValue
    }
    val deltaLabel = when {
        deltaValue == null -> "Sin tendencia"
        deltaValue == 0.0 -> "Sin cambio"
        else -> "${formatSignedDecimalEs(deltaValue, 1)} $metricUnit"
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
                        secondary = rangeStartLabel,
                        color = BODY_NEUTRAL_MUTED,
                    )
                    BodyHeroDetailRow(
                        label = "Ritmo",
                        primary = rateLabel,
                        secondary = rateHint,
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
            // Sin avance medido no hay relleno: la barra queda vacía y no se inventa un porcentaje.
            progress?.let { fraction ->
                drawRoundRect(
                    color = color,
                    size = Size(fraction.coerceIn(0f, 1f) * size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
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
    var weight by remember(entryKey) { mutableStateOf(initialEntry?.weight?.let(::fieldText).orEmpty()) }
    var bodyFat by remember(entryKey) { mutableStateOf(initialEntry?.bodyFat?.let(::fieldText).orEmpty()) }
    var muscleMass by remember(entryKey) { mutableStateOf(initialEntry?.muscleMass?.let(::fieldText).orEmpty()) }
    var waist by remember(entryKey) { mutableStateOf(initialEntry?.waistCm?.let(::fieldText).orEmpty()) }
    var hip by remember(entryKey) { mutableStateOf(initialEntry?.hipCm?.let(::fieldText).orEmpty()) }
    var chest by remember(entryKey) { mutableStateOf(initialEntry?.chestCm?.let(::fieldText).orEmpty()) }
    var arm by remember(entryKey) { mutableStateOf(initialEntry?.armCm?.let(::fieldText).orEmpty()) }
    var thigh by remember(entryKey) { mutableStateOf(initialEntry?.thighCm?.let(::fieldText).orEmpty()) }
    var neck by remember(entryKey) { mutableStateOf(initialEntry?.neckCm?.let(::fieldText).orEmpty()) }
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

/** Cifra con coma decimal y un decimal, como el resto de la app: 64,0. */
private fun r1(v: Double): String = formatDecimalEs(v, 1)

/** Texto de un campo de medida sin redondear: 64,25 se edita como 64,25. */
private fun fieldText(v: Double): String = v.toString().replace('.', ',')

/** «12 de agosto», en español aunque el teléfono esté en otro idioma. */
private fun formatDayMonthEs(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es")))

private fun bodyDateEpochDay(raw: String): Long? =
    runCatching { java.time.LocalDate.parse(raw).toEpochDay() }.getOrNull()
