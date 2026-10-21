package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.kpkn.screens.body.design.BodyRecordDraft
import com.example.kpkn.screens.body.design.BodyRecordSheet
import com.example.kpkn.screens.body.design.bodyHistoryItems
import com.example.kpkn.screens.body.design.bodyRecordFrom
import com.example.kpkn.screens.body.design.bodyRecordNew
import com.example.kpkn.screens.body.design.bodyRecordSessionId
import com.example.kpkn.screens.body.design.epochMsFor
import com.example.kpkn.screens.body.design.metricValues
import com.example.kpkn.screens.body.design.BodyPageHeader
import com.example.kpkn.screens.body.design.BodyReminderModule
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.body.design.bodyIslandChangeText
import com.example.kpkn.screens.body.design.bodyWeightText
import com.example.kpkn.screens.body.design.bodyInfoFor
import com.example.kpkn.screens.body.design.buildBodyChartPoints
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknStatusVeil
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.ui.design.rememberIslandVisible
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.calculations.calculateFFMI
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.KpknSnackbar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyProgressScreen(
    onAddHeight: () -> Unit = {},
    onEditNutritionPlan: (String) -> Unit = {},
) {
    val bodyViewModel: BodyProgressViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val settings by ProgramRepository.getInstance().settings.collectAsState()
    val vitals = settings.userVitals
    // Recorrido de Cuerpo: se lanza al montar la pantalla (no hay señal de carga en el ViewModel). Visto = no arranca.
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(Unit) { tourHost?.startTour(TourIds.BODY, TourScripts.body) }
    val history by bodyViewModel.history.collectAsState()
    val bodyUiState by bodyViewModel.uiState.collectAsState()
    val schedule by bodyViewModel.measurementSchedule.collectAsState()

    // Hojas y diálogos de los módulos de Cuerpo: objetivo propio, quitarlo, explicación de una cifra y la hora del recordatorio.
    var goalDraft by remember { mutableStateOf<BodyGoalDraft?>(null) }
    var goalRemoveConfirm by remember { mutableStateOf(false) }
    var infoTopic by remember { mutableStateOf<BodyInfoTopic?>(null) }
    var reminderOther by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    // Hoja de registro: lo que se escribe y la medición de origen (null si es nueva). El contador rehace la hoja al abrirla.
    var recordDraft by remember { mutableStateOf<BodyRecordDraft?>(null) }
    var recordSource by remember { mutableStateOf<BodyHistoryRow?>(null) }
    var recordKey by remember { mutableStateOf(0) }
    val zone = ZoneId.systemDefault()
    // Una sola unidad de peso en la página: la de Ajustes. Los pesos se guardan en kg; solo cambia lo que se muestra.
    val weightUnit = if (settings.weightUnit == WeightUnit.LBS) WizardMassUnit.LB else WizardMassUnit.KG
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
    val islandTitle = summary.current?.let { bodyWeightText(it, weightUnit) } ?: "Cuerpo"
    val islandSubtitle = bodyIslandChangeText(bodyUiState.weightSummary30Days, weightUnit)

    // Abrir la hoja de registro: una medición nueva a la hora actual, o una existente para verla o editarla.
    fun openNewRecord() {
        recordSource = null
        recordDraft = bodyRecordNew(LocalDateTime.now(zone))
        recordKey += 1
    }
    fun openRecord(row: BodyHistoryRow) {
        recordSource = row
        recordDraft = bodyRecordFrom(row)
        recordKey += 1
    }

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
                    onAdd = { openNewRecord() },
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
                        weightUnit = weightUnit,
                        onRangeChange = bodyViewModel::selectRange,
                        onAdd = { openNewRecord() },
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
                        weightUnit = weightUnit,
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

            // Historial por meses: una etiqueta por mes y un módulo con una fila por medición (CU3).
            if (bodyUiState.loaded) {
                bodyHistoryItems(
                    rows = history,
                    weightUnit = weightUnit,
                    onOpen = { row -> openRecord(row) },
                    onAdd = { openNewRecord() },
                )
            }

            item { Spacer(Modifier.height(100.dp)) }
            }
        }

        // Velo bajo la barra de estado: por encima de la lista y por debajo de la isla superior.
        KpknStatusVeil(modifier = Modifier.align(Alignment.TopCenter))

        // Isla superior de Cuerpo: aparece cuando la cabecera sale de la vista.
        BodyIsland(
            visible = islandVisible,
            title = islandTitle,
            subtitle = islandSubtitle,
            onAdd = { openNewRecord() },
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

    // ── Hoja de registro de Cuerpo (CU3) ─────────────────────────────────────
    recordDraft?.let { draft ->
        key(recordKey) {
            BodyRecordSheet(
                draft = draft,
                onDraftChange = { recordDraft = it },
                source = recordSource,
                unit = weightUnit,
                anchorKg = summary.current,
                now = LocalDateTime.now(zone),
                onSave = {
                    val source = recordSource
                    bodyViewModel.saveBodyRecord(
                        sessionId = bodyRecordSessionId(source) { UUID.randomUUID().toString() },
                        timestampEpochMs = draft.epochMsFor(source, zone),
                        values = draft.metricValues(),
                        replacing = source?.observations.orEmpty(),
                    )
                    recordDraft = null
                    recordSource = null
                },
                onDelete = {
                    val removed = recordSource?.observations.orEmpty()
                    recordDraft = null
                    recordSource = null
                    if (removed.isNotEmpty()) {
                        bodyViewModel.removeObservations(removed)
                        snackbarScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Medición eliminada",
                                actionLabel = "Deshacer",
                                withDismissAction = true,
                            )
                            // Deshacer vuelve a guardar las mismas observaciones: mismo id, misma hora y mismo origen.
                            if (result == SnackbarResult.ActionPerformed) bodyViewModel.restoreObservations(removed)
                        }
                    }
                },
                onDismiss = {
                    recordDraft = null
                    recordSource = null
                },
            )
        }
    }
}
