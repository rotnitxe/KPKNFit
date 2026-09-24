package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.repository.NutritionPlanCommitCoordinator
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorMode
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.nutrition.provenanceOf

/**
 * Editor nutricional DIRECTO: secciones editables en cualquier orden, sin
 * pasos, sin botones «Siguiente» y sin secuencia guiada. Es la única puerta de
 * creación/edición del plan tras el onboarding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionPlanEditorScreen(
    planId: String?,
    pendingDraftId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: NutritionPlanEditorViewModel = rememberEditorViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(planId, pendingDraftId) { viewModel.initialize(planId, pendingDraftId) }
    LaunchedEffect(state.isSaved) { if (state.isSaved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    Text(
                        if (state.draft.planId == null) "Nuevo plan nutricional" else "Editar plan nutricional",
                        fontWeight = FontWeight.Black,
                    )
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && !state.isLoading,
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() / 2 + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EditorSectionCard("Dirección y meta") {
                    DirectionSection(state, viewModel)
                }
            }
            item {
                EditorSectionCard("Datos de cálculo") {
                    CalculationDataSection(state, viewModel)
                }
            }
            item {
                EditorSectionCard("Calorías y macros") {
                    EnergySection(state, viewModel)
                }
            }
            item {
                EditorSectionCard("Reparto semanal") {
                    WeeklyDistributionSection(state, viewModel)
                }
            }
            item {
                EditorSectionCard("Procedencia") {
                    ProvenanceSection(state, viewModel)
                }
            }
            item {
                EditorSectionCard("Estado") {
                    ModeSection(state, viewModel)
                }
            }
            state.errors.values.forEach { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberEditorViewModel(): NutritionPlanEditorViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel {
        val nutritionRepository = NutritionRepository.getInstance()
        val programRepository = ProgramRepository.getInstance()
        val factory = com.example.kpkn.data.onboarding.persistenceFactory(context)
        NutritionPlanEditorViewModel(
            nutritionRepository = nutritionRepository,
            programRepository = programRepository,
            commits = NutritionPlanCommitCoordinator(
                db = factory.database,
                nutritionRepository = nutritionRepository,
                programRepository = programRepository,
            ),
            drafts = factory.drafts,
        )
    }
}

@Composable
private fun EditorSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun DirectionSection(state: NutritionPlanEditorUiState, viewModel: NutritionPlanEditorViewModel) {
    Text("Dirección", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PlanDirection.DEFICIT to "Déficit",
            PlanDirection.MAINTENANCE to "Mantenimiento",
            PlanDirection.SURPLUS to "Superávit",
            PlanDirection.PROFESSIONAL to "Profesional",
        ).forEach { (direction, label) ->
            FilterChip(
                selected = state.draft.direction == direction,
                onClick = { viewModel.setDirection(direction) },
                label = { Text(label) },
            )
        }
    }
    Text("Meta", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            GoalMetric.WEIGHT to "Peso",
            GoalMetric.BODY_FAT to "% grasa",
            GoalMetric.MUSCLE_MASS to "% músculo",
        ).forEach { (metric, label) ->
            FilterChip(
                selected = state.draft.goalMetric == metric,
                onClick = { viewModel.setGoalMetric(metric) },
                label = { Text(label) },
            )
        }
    }
    OutlinedTextField(
        value = state.draft.targetValueText,
        onValueChange = { viewModel.setTargetValue(it) },
        label = { Text("Valor objetivo (opcional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CalculationDataSection(state: NutritionPlanEditorUiState, viewModel: NutritionPlanEditorViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.draft.ageText,
            onValueChange = { viewModel.setAge(it) },
            label = { Text("Edad") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.draft.heightText,
            onValueChange = { viewModel.setHeight(it) },
            label = { Text("Altura (cm)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.draft.weightText,
            onValueChange = { viewModel.setWeight(it) },
            label = { Text("Peso (${state.draft.weightUnit})") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Column(Modifier.weight(1f)) {
            Text("Sexo de la ecuación", style = MaterialTheme.typography.labelMedium)
            Row {
                EerSex.entries.forEach { sex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.draft.equationSex == sex,
                            onClick = { viewModel.setEquationSex(sex) },
                        )
                        Text(if (sex == EerSex.MALE) "Hombre" else "Mujer", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    Text("Actividad", style = MaterialTheme.typography.labelMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EerActivity.entries.forEach { activity ->
            FilterChip(
                selected = state.draft.activity == activity,
                onClick = { viewModel.setActivity(activity) },
                label = { Text(activityLabel(activity), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
    SwitchRow("No puedo confirmar mi elegibilidad", state.draft.eligibilityUnknown) {
        viewModel.setEligibilityUnknown(it)
    }
    SwitchRow("Condición médica", state.draft.medicalRestriction) {
        viewModel.setMedicalRestriction(it)
    }
    SwitchRow("Embarazo", state.draft.pregnant) { viewModel.setPregnant(it) }
    SwitchRow("Lactancia", state.draft.lactating) { viewModel.setLactating(it) }
}

@Composable
private fun EnergySection(state: NutritionPlanEditorUiState, viewModel: NutritionPlanEditorViewModel) {
    val base = state.base ?: NutritionEditorBase(0, 0, 0, 0)
    OutlinedTextField(
        value = base.caloriesKcal.toString(),
        onValueChange = { text ->
            parseLocalizedNumber(text)?.roundToIntSafe()?.let { viewModel.setCalories(it) }
        },
        label = { Text("Calorías (kcal)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroField("Proteína (g)", base.proteinG, Modifier.weight(1f)) { viewModel.setMacro(protein = it) }
        MacroField("Carbohidratos (g)", base.carbsG, Modifier.weight(1f)) { viewModel.setMacro(carbs = it) }
        MacroField("Grasas (g)", base.fatG, Modifier.weight(1f)) { viewModel.setMacro(fat = it) }
    }
    Text(
        "Total energético mostrado: ${base.atwaterEnergyKcal} kcal",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Base revisada que se guardará tal cual: ${base.caloriesKcal} kcal · P ${base.proteinG} g · C ${base.carbsG} g · G ${base.fatG} g",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MacroField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onValue: (Double) -> Unit,
) {
    OutlinedTextField(
        // Un 0 es un cero manual legítimo y se muestra tal cual.
        value = value.toString(),
        onValueChange = { text ->
            parseLocalizedNumber(text)?.takeIf { it >= 0 }?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun WeeklyDistributionSection(
    state: NutritionPlanEditorUiState,
    viewModel: NutritionPlanEditorViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.draft.weeklyDistribution == NutritionWeeklyDistributionMode.VARIABLE,
            onClick = { viewModel.setWeeklyDistribution(NutritionWeeklyDistributionMode.VARIABLE) },
            label = { Text("Según gasto previsto") },
        )
        FilterChip(
            selected = state.draft.weeklyDistribution == NutritionWeeklyDistributionMode.UNIFORM,
            onClick = { viewModel.setWeeklyDistribution(NutritionWeeklyDistributionMode.UNIFORM) },
            label = { Text("Uniforme") },
        )
    }
    if (state.weeklyTargets.isEmpty()) {
        Text(
            "Sin base revisada aún no hay reparto que mostrar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        state.weeklyTargets.forEach { day ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.date.toString(), style = MaterialTheme.typography.bodySmall)
                Text(
                    "${day.calorieTargetKcal} kcal · P ${day.proteinG} · C ${day.carbsG} · G ${day.fatG}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProvenanceSection(state: NutritionPlanEditorUiState, viewModel: NutritionPlanEditorViewModel) {
    listOf(
        Triple(NutritionEditorProvenance.AUTOMATIC, "Recomendación automática", "Calculada con la ecuación EER."),
        Triple(NutritionEditorProvenance.SELF_DEFINED, "Objetivos propios", "Valores introducidos por ti."),
        Triple(NutritionEditorProvenance.PROFESSIONAL, "Pauta de tercero", "Definida por un profesional."),
    ).forEach { (provenance, title, subtitle) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = provenanceOf(state.draft) == provenance,
                onClick = { viewModel.setProvenance(provenance) },
            )
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeSection(state: NutritionPlanEditorUiState, viewModel: NutritionPlanEditorViewModel) {
    listOf(
        Triple(
            NutritionPlanEditorMode.ACTIVE_PLAN,
            "Plan activo",
            "Con metas numéricas para medir y avisar.",
        ),
        Triple(
            NutritionPlanEditorMode.TRACKING_ONLY,
            "Solo registro",
            "Registras comidas sin metas: no hay porcentajes ni avisos de «te falta». Los planes anteriores no se borran.",
        ),
    ).forEach { (mode, title, subtitle) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = state.draft.mode == mode,
                onClick = { viewModel.setMode(mode) },
            )
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()

private fun activityLabel(activity: EerActivity): String = when (activity) {
    EerActivity.INACTIVE -> "Inactivo"
    EerActivity.LOW_ACTIVE -> "Poco activo"
    EerActivity.ACTIVE -> "Activo"
    EerActivity.VERY_ACTIVE -> "Muy activo"
}
