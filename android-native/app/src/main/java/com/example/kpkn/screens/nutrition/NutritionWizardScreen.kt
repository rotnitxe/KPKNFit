package com.example.kpkn.screens.nutrition

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import java.util.Locale

private val WizardGraphite = Color(0xFF151719)
private val WizardYellow = Color(0xFFF4D35E)
private val WizardTeal = Color(0xFF58C7C1)

@Composable
fun NutritionWizardScreen(
    mode: String = "create",
    planId: String? = null,
    viewModel: NutritionWizardViewModel = viewModel(),
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(mode, planId) { viewModel.initialize(mode, planId) }
    BackHandler {
        if (state.isDirty) showDiscard = true else onCancel()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardGraphite)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (state.isDirty) showDiscard = true else onCancel() }) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Plan nutricional", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.stepIndex + 1} de ${NutritionWizardStep.entries.size}",
                        color = WizardTeal,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    state.step.name.lowercase(Locale.getDefault()).replace('_', ' ').replaceFirstChar { it.uppercase() },
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.08f))) {
                Box(
                    Modifier
                        .fillMaxWidth((state.stepIndex + 1) / NutritionWizardStep.entries.size.toFloat())
                        .height(3.dp)
                        .background(WizardYellow),
                )
            }

            AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                label = "nutritionWizardStep",
            ) { step ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Text(
                            when (step) {
                                NutritionWizardStep.GOAL -> "¿Qué quieres conseguir?"
                                NutritionWizardStep.DATA -> "Datos y elegibilidad"
                                NutritionWizardStep.ACTIVITY -> "Actividad cotidiana"
                                NutritionWizardStep.PACE -> "Ritmo e incertidumbre"
                                NutritionWizardStep.STRATEGY -> "Estrategia de macros"
                                NutritionWizardStep.REVIEW -> "Revisa antes de guardar"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    when (step) {
                        NutritionWizardStep.GOAL -> goalStep(state, viewModel)
                        NutritionWizardStep.DATA -> dataStep(state, viewModel)
                        NutritionWizardStep.ACTIVITY -> activityStep(state, viewModel)
                        NutritionWizardStep.PACE -> paceStep(state, viewModel)
                        NutritionWizardStep.STRATEGY -> strategyStep(state, viewModel)
                        NutritionWizardStep.REVIEW -> reviewStep(state)
                    }
                    if (state.errors.isNotEmpty()) {
                        item {
                            Surface(
                                color = Color(0xFF5A2323),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    state.errors.values.forEach { error ->
                                        Text(error, color = Color(0xFFFFD5D5), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.canGoBack) {
                    TextButton(onClick = viewModel::back, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Default.ArrowBack, null)
                        Spacer(Modifier.size(6.dp))
                        Text("Atrás", color = Color.White)
                    }
                }
                Button(
                    onClick = {
                        if (state.step == NutritionWizardStep.REVIEW) {
                            if (viewModel.save() != null) onDone()
                        } else viewModel.next()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WizardYellow,
                        contentColor = WizardGraphite,
                    ),
                    enabled = state.canContinue,
                ) {
                    if (state.step == NutritionWizardStep.REVIEW) Icon(Icons.Default.Save, null)
                    Text(if (state.step == NutritionWizardStep.REVIEW) "Guardar plan" else "Continuar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("El borrador se conservará mientras esta pantalla siga abierta, pero cerrar ahora descarta sus cambios.") },
            confirmButton = {
                TextButton(onClick = { viewModel.markDiscarded(); showDiscard = false; onCancel() }) { Text("Descartar") }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Seguir editando") } },
        )
    }
}
private fun androidx.compose.foundation.lazy.LazyListScope.goalStep(
    state: NutritionWizardUiState,
    vm: NutritionWizardViewModel,
) {
    item { Text("Dirección", color = Color.White.copy(alpha = 0.75f)) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PlanDirection.DEFICIT to "Perder",
                PlanDirection.MAINTENANCE to "Mantener",
                PlanDirection.SURPLUS to "Ganar",
                PlanDirection.PROFESSIONAL to "Objetivo profesional",
            ).forEach { (direction, label) ->
                FilterChip(
                    selected = state.draft.direction == direction,
                    onClick = { vm.updateDirection(direction) },
                    label = { Text(label) },
                )
            }
        }
    }
    item { Text("Métrica corporal opcional", color = Color.White.copy(alpha = 0.75f)) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(GoalMetric.WEIGHT to "Peso", GoalMetric.BODY_FAT to "% grasa", GoalMetric.MUSCLE_MASS to "% músculo").forEach { (metric, label) ->
                FilterChip(state.draft.goalMetric == metric, { vm.updateGoalMetric(metric) }, label = { Text(label) })
            }
        }
    }
    item {
        WizardField(
            value = state.draft.targetValueText,
            onValueChange = vm::updateTargetValue,
            label = if (state.draft.direction == PlanDirection.MAINTENANCE) "Meta corporal (opcional; no se crea en mantención)" else "Meta corporal (opcional)",
            keyboardType = KeyboardType.Decimal,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dataStep(
    state: NutritionWizardUiState,
    vm: NutritionWizardViewModel,
) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.draft.weightUnit == "kg", { vm.updateWeightUnit("kg") }, label = { Text("kg") })
            FilterChip(state.draft.weightUnit == "lb", { vm.updateWeightUnit("lb") }, label = { Text("lb") })
        }
    }
    item { WizardField(state.draft.ageText, vm::updateAge, "Edad (años)", KeyboardType.Number) }
    item { WizardField(state.draft.heightText, vm::updateHeight, "Altura (cm)", KeyboardType.Decimal) }
    item { WizardField(state.draft.weightText, vm::updateWeight, "Peso (${state.draft.weightUnit})", KeyboardType.Decimal) }
    item { Text("Sexo usado únicamente por la ecuación EER", color = Color.White.copy(alpha = 0.75f)) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.draft.equationSex == EerSex.FEMALE, { vm.updateEquationSex(EerSex.FEMALE) }, label = { Text("Femenino") })
            FilterChip(state.draft.equationSex == EerSex.MALE, { vm.updateEquationSex(EerSex.MALE) }, label = { Text("Masculino") })
        }
    }
    item {
        AssistChip(
            onClick = { vm.updateMedicalRestriction(!state.draft.medicalRestriction) },
            label = { Text(if (state.draft.medicalRestriction) "Condición médica: objetivo manual" else "Tengo una condición relevante") },
            leadingIcon = { Icon(Icons.Default.Info, null) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.activityStep(
    state: NutritionWizardUiState,
    vm: NutritionWizardViewModel,
) {
    item { Text("Elige la categoría que ya incluye tu entrenamiento habitual; no se sumará dos veces.", color = Color.White.copy(alpha = 0.75f)) }
    items(EerActivity.entries) { activity ->
        FilterChip(
            selected = state.draft.activity == activity,
            onClick = { vm.updateActivity(activity) },
            label = { Text(activity.name.lowercase(Locale.getDefault()).replace('_', ' ').replaceFirstChar { it.uppercase() }) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.paceStep(
    state: NutritionWizardUiState,
    vm: NutritionWizardViewModel,
) {
    val recommendation = state.recommendation
    item { Text("La sugerencia es un punto de partida. La app mostrará un rango y calibrará con datos reales.", color = Color.White.copy(alpha = 0.75f)) }
    item { Text("EER: ${recommendation?.eerKcal?.toInt() ?: "—"} kcal/día", color = WizardTeal, fontWeight = FontWeight.Bold) }
    item { Text("Objetivo inicial: ${recommendation?.calorieTargetKcal ?: "manual"} kcal/día", color = Color.White) }
    item { Text("Rango de revisión: ±150 kcal · estado: ${recommendation?.projectionStatus ?: "sin calcular"}", color = Color.White.copy(alpha = 0.75f)) }
    item { WizardField(state.draft.manualCalorieTargetText, vm::updateManualCalories, "Calorías manuales (opcional)", KeyboardType.Number) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.strategyStep(
    state: NutritionWizardUiState,
    vm: NutritionWizardViewModel,
) {
    val macros = state.recommendation?.macros
    item { Text("Base equilibrada: proteína 1,6 g/kg (2,0 en déficit contextual), grasa 25%, carbohidratos remanentes y fibra 14 g/1000 kcal.", color = Color.White.copy(alpha = 0.75f)) }
    item { Text("Proteína ${macros?.proteinG?.toInt() ?: "—"} g · Carbohidratos ${macros?.carbsG?.toInt() ?: "—"} g · Grasas ${macros?.fatG?.toInt() ?: "—"} g · Fibra ${macros?.fiberG?.toInt() ?: "—"} g", color = Color.White) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.draft.higherProteinInDeficit, { vm.updateHigherProtein(!state.draft.higherProteinInDeficit) }, label = { Text("Proteína superior en déficit") })
        }
    }
    item {
        WizardField(state.draft.manualProteinText, { value -> vm.updateManualMacros(value, state.draft.manualCarbsText, state.draft.manualFatText) }, "Proteína manual (opcional)", KeyboardType.Decimal)
    }
    item {
        WizardField(state.draft.manualCarbsText, { value -> vm.updateManualMacros(state.draft.manualProteinText, value, state.draft.manualFatText) }, "Carbohidratos manuales (opcional)", KeyboardType.Decimal)
    }
    item {
        WizardField(state.draft.manualFatText, { value -> vm.updateManualMacros(state.draft.manualProteinText, state.draft.manualCarbsText, value) }, "Grasas manuales (opcional)", KeyboardType.Decimal)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reviewStep(state: NutritionWizardUiState) {
    item {
        Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dirección: ${state.draft.direction?.name ?: "—"}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Datos: ${state.draft.ageText} años · ${state.draft.heightText} cm · ${state.draft.weightText} ${state.draft.weightUnit}", color = Color.White)
                Text("Fórmula: EER 2023 · sexo ${state.draft.equationSex?.name ?: "sin seleccionar"}", color = WizardTeal)
                Text("Supuestos: ${state.recommendation?.snapshot?.assumptions?.joinToString(" ") ?: "—"}", color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun WizardField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
