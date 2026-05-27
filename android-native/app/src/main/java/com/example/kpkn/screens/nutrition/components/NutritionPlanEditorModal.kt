package com.example.kpkn.screens.nutrition.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionGoal
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.nutrition.CalorieGoal
import com.example.kpkn.domain.nutrition.CalorieGoalConfig
import com.example.kpkn.domain.nutrition.FormulaType
import com.example.kpkn.domain.nutrition.NutritionInput
import com.example.kpkn.domain.nutrition.RiskInput
import com.example.kpkn.domain.nutrition.RiskSeverity
import com.example.kpkn.domain.nutrition.buildNutritionRiskFlags
import com.example.kpkn.domain.nutrition.calculateBMR
import com.example.kpkn.domain.nutrition.getActivityFactor
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

private val OVERLAY_BG = Color(0xE60A0C10)
private val PANEL_BG = Color(0xF214171C)
private val PANEL_ALT = Color(0xFF1B2027)
private val PANEL_STROKE = Color.White.copy(alpha = 0.10f)
private val PANEL_MUTED = Color(0xFFB4BBC5)
private val ACCENT_GREEN = Color(0xFF2ECC71) // Curated modern emerald green

private enum class EditorStep {
    OBJECTIVE,
    BODY_DATA,
    ACTIVITY,
    MACROS,
    SUMMARY
}

private data class EditorState(
    val goal: CalorieGoal,
    val goalMetric: GoalMetric,
    val goalValue: String,
    val height: String,
    val weight: String,
    val age: String,
    val gender: Gender,
    val bodyFat: String,
    val muscleMass: String,
    val activityLevel: Int,
    val dietaryPreference: String,
    val formula: FormulaType,
    val weeklyChangeKg: Double,
    val healthMultiplier: Double,
    val proteinG: String,
    val carbsG: String,
    val fatsG: String,
)

@Composable
fun NutritionPlanEditorModal(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSave: (NutritionPlan) -> Unit,
    currentSettings: Settings = Settings(),
    activePlan: NutritionPlan? = null,
) {
    if (!isOpen) return

    val initialState = remember(isOpen, activePlan?.id, currentSettings) {
        buildInitialState(currentSettings, activePlan)
    }
    var state by remember(isOpen, activePlan?.id, currentSettings) { mutableStateOf(initialState) }
    var currentStep by remember(isOpen, activePlan?.id) { mutableStateOf(EditorStep.OBJECTIVE) }
    val coroutineScope = rememberCoroutineScope()
    
    // Validation states
    var heightError by remember { mutableStateOf(false) }
    var weightError by remember { mutableStateOf(false) }
    var ageError by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }

    var showAdvanced by remember(isOpen, activePlan?.id) { mutableStateOf(activePlan != null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val isDirty = state != initialState

    val weightD = state.weight.toDoubleOrNull() ?: 0.0
    val heightD = state.height.toDoubleOrNull() ?: 0.0
    val ageI = state.age.toIntOrNull() ?: 0
    val bodyFatD = state.bodyFat.toDoubleOrNull()
    val muscleD = state.muscleMass.toDoubleOrNull()
    val goalValueD = state.goalValue.toDoubleOrNull() ?: fallbackGoalValue(state.goalMetric, weightD, bodyFatD, muscleD)
    val proteinD = state.proteinG.toDoubleOrNull() ?: 0.0
    val carbsD = state.carbsG.toDoubleOrNull() ?: 0.0
    val fatsD = state.fatsG.toDoubleOrNull() ?: 0.0

    val nutritionInput = NutritionInput(
        weightKg = weightD,
        heightCm = heightD,
        age = ageI,
        gender = state.gender,
        bodyFatPercentage = bodyFatD,
    )
    val calorieConfig = CalorieGoalConfig(
        formula = state.formula,
        activityLevel = state.activityLevel,
        goal = state.goal,
        weeklyChangeKg = state.weeklyChangeKg,
        healthMultiplier = state.healthMultiplier,
    )
    val bmr = calculateBMR(nutritionInput, calorieConfig)
    val tdee = bmr?.let { (it * getActivityFactor(calorieConfig)).roundToInt() }
    val proteinMultiplier = when (state.dietaryPreference) {
        "vegan" -> 1.15
        "vegetarian" -> 1.08
        else -> 1.0
    }
    val proteinGoal = (proteinD * proteinMultiplier).roundToInt()
    val macroCalories = (proteinGoal * 4) + carbsD.roundToInt() * 4 + fatsD.roundToInt() * 9
    val weeklyTrendKg = if (tdee != null && tdee > 0) ((macroCalories - tdee) * 7) / 7700.0 else null
    val riskFlags = remember(
        state.goal,
        state.goalMetric,
        state.goalValue,
        state.weeklyChangeKg,
        state.weight,
        state.height,
        state.age,
        state.gender,
        state.bodyFat,
        macroCalories,
    ) {
        buildNutritionRiskFlags(
            RiskInput(
                settings = nutritionInput,
                calorieTarget = macroCalories,
                goalMetric = state.goalMetric,
                goalValue = goalValueD,
                weeklyChangeKg = state.weeklyChangeKg,
            )
        )
    }

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    BackHandler(enabled = true) {
        if (currentStep != EditorStep.OBJECTIVE) {
            currentStep = when (currentStep) {
                EditorStep.BODY_DATA -> EditorStep.OBJECTIVE
                EditorStep.ACTIVITY -> EditorStep.BODY_DATA
                EditorStep.MACROS -> EditorStep.ACTIVITY
                EditorStep.SUMMARY -> EditorStep.MACROS
                else -> EditorStep.OBJECTIVE
            }
        } else {
            requestDismiss()
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Descartar cambios") },
            text = { Text("Hay cambios sin guardar en tu plan nutricional.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                ) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Seguir editando") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OVERLAY_BG)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(28.dp),
            color = PANEL_BG,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                // FIXED HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (activePlan == null) "Crear plan de alimentación" else "Editar plan de alimentación",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (currentStep) {
                                EditorStep.OBJECTIVE -> "Paso 1: Define tu objetivo principal"
                                EditorStep.BODY_DATA -> "Paso 2: Datos corporales obligatorios y opcionales"
                                EditorStep.ACTIVITY -> "Paso 3: Nivel de actividad y dieta"
                                EditorStep.MACROS -> "Paso 4: Ajusta tus macronutrientes"
                                EditorStep.SUMMARY -> "Paso 5: Resumen final de tu plan"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = PANEL_MUTED,
                        )
                    }
                    IconButton(onClick = ::requestDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
                
                Spacer(Modifier.height(14.dp))
                StepIndicator(currentStep = currentStep)
                Spacer(Modifier.height(18.dp))

                // SCROLLABLE CONTENT AREA
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (currentStep) {
                        EditorStep.OBJECTIVE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Objetivo del plan")
                                ChipRow {
                                    GoalChip(
                                        label = "Definición",
                                        selected = state.goal == CalorieGoal.LOSE,
                                        onClick = { state = state.copy(goal = CalorieGoal.LOSE) }
                                    )
                                    GoalChip(
                                        label = "Mantención",
                                        selected = state.goal == CalorieGoal.MAINTAIN,
                                        onClick = { state = state.copy(goal = CalorieGoal.MAINTAIN) }
                                    )
                                    GoalChip(
                                        label = "Superávit",
                                        selected = state.goal == CalorieGoal.GAIN,
                                        onClick = { state = state.copy(goal = CalorieGoal.GAIN) }
                                    )
                                }
                                
                                SectionTitle("Métrica del objetivo")
                                ChipRow {
                                    GoalChip(
                                        label = "Peso",
                                        selected = state.goalMetric == GoalMetric.WEIGHT,
                                        onClick = { state = state.copy(goalMetric = GoalMetric.WEIGHT) }
                                    )
                                    GoalChip(
                                        label = "% grasa",
                                        selected = state.goalMetric == GoalMetric.BODY_FAT,
                                        onClick = { state = state.copy(goalMetric = GoalMetric.BODY_FAT) }
                                    )
                                    GoalChip(
                                        label = "% músculo",
                                        selected = state.goalMetric == GoalMetric.MUSCLE_MASS,
                                        onClick = { state = state.copy(goalMetric = GoalMetric.MUSCLE_MASS) }
                                    )
                                }
                                
                                LabeledField(
                                    label = when (state.goalMetric) {
                                        GoalMetric.WEIGHT -> "Meta de peso (kg) *"
                                        GoalMetric.BODY_FAT -> "Meta de grasa corporal (%) *"
                                        GoalMetric.MUSCLE_MASS -> "Meta de masa muscular (%) *"
                                    },
                                    value = state.goalValue,
                                    onValueChange = { state = state.copy(goalValue = sanitizeDecimal(it)) },
                                    keyboardType = KeyboardType.Decimal,
                                    placeholderText = when (state.goalMetric) {
                                        GoalMetric.WEIGHT -> "70"
                                        GoalMetric.BODY_FAT -> "15.0"
                                        GoalMetric.MUSCLE_MASS -> "40.0"
                                    }
                                )
                            }
                        }
                        
                        EditorStep.BODY_DATA -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Datos corporales esenciales")
                                TwoFieldRow(
                                    first = {
                                        LabeledField(
                                            label = "Estatura (cm) *",
                                            value = state.height,
                                            onValueChange = { 
                                                state = state.copy(height = sanitizeInt(it))
                                                heightError = false
                                                showValidationError = false
                                            },
                                            keyboardType = KeyboardType.Number,
                                            placeholderText = "170",
                                            error = heightError
                                        )
                                    },
                                    second = {
                                        LabeledField(
                                            label = "Peso actual (kg) *",
                                            value = state.weight,
                                            onValueChange = { 
                                                state = state.copy(weight = sanitizeDecimal(it))
                                                weightError = false
                                                showValidationError = false
                                            },
                                            keyboardType = KeyboardType.Decimal,
                                            placeholderText = "70",
                                            error = weightError
                                        )
                                    },
                                )
                                TwoFieldRow(
                                    first = {
                                        LabeledField(
                                            label = "Edad *",
                                            value = state.age,
                                            onValueChange = { 
                                                state = state.copy(age = sanitizeInt(it))
                                                ageError = false
                                                showValidationError = false
                                            },
                                            keyboardType = KeyboardType.Number,
                                            placeholderText = "30",
                                            error = ageError
                                        )
                                    },
                                    second = {
                                        LabeledField(
                                            label = "% grasa",
                                            value = state.bodyFat,
                                            onValueChange = { state = state.copy(bodyFat = sanitizeDecimal(it)) },
                                            keyboardType = KeyboardType.Decimal,
                                            placeholderText = "15.0",
                                            optional = true
                                        )
                                    },
                                )
                                TwoFieldRow(
                                    first = {
                                        LabeledField(
                                            label = "% músculo",
                                            value = state.muscleMass,
                                            onValueChange = { state = state.copy(muscleMass = sanitizeDecimal(it)) },
                                            keyboardType = KeyboardType.Decimal,
                                            placeholderText = "40.0",
                                            optional = true
                                        )
                                    },
                                    second = {
                                        Column {
                                            Text(
                                                "Sexo biológico *",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                GoalChip(
                                                    label = "Hombre",
                                                    selected = state.gender == Gender.MALE,
                                                    onClick = { state = state.copy(gender = Gender.MALE) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                GoalChip(
                                                    label = "Mujer",
                                                    selected = state.gender == Gender.FEMALE,
                                                    onClick = { state = state.copy(gender = Gender.FEMALE) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                GoalChip(
                                                    label = "Otro",
                                                    selected = state.gender == Gender.OTHER,
                                                    onClick = { state = state.copy(gender = Gender.OTHER) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    },
                                )
                                
                                if (showValidationError) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Por favor completa todos los campos obligatorios (*)",
                                        color = Color(0xFFE53935),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        EditorStep.ACTIVITY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Nivel de actividad física")
                                ChipRow {
                                    ActivityChip("Sedentario", 1, state.activityLevel) { state = state.copy(activityLevel = it) }
                                    ActivityChip("Ligero", 2, state.activityLevel) { state = state.copy(activityLevel = it) }
                                    ActivityChip("Moderado", 3, state.activityLevel) { state = state.copy(activityLevel = it) }
                                    ActivityChip("Activo", 4, state.activityLevel) { state = state.copy(activityLevel = it) }
                                    ActivityChip("Muy activo", 5, state.activityLevel) { state = state.copy(activityLevel = it) }
                                }
                                
                                SectionTitle("Preferencia alimentaria")
                                ChipRow {
                                    GoalChip("Omnívoro", state.dietaryPreference == "omnivore") { state = state.copy(dietaryPreference = "omnivore") }
                                    GoalChip("Vegetariano", state.dietaryPreference == "vegetarian") { state = state.copy(dietaryPreference = "vegetarian") }
                                    GoalChip("Vegano", state.dietaryPreference == "vegan") { state = state.copy(dietaryPreference = "vegan") }
                                }
                            }
                        }

                        EditorStep.MACROS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Distribución de macronutrientes")
                                TwoFieldRow(
                                    first = {
                                        LabeledField("Proteína (g) *", state.proteinG, { state = state.copy(proteinG = sanitizeInt(it)) }, KeyboardType.Number)
                                    },
                                    second = {
                                        LabeledField("Carbohidratos (g) *", state.carbsG, { state = state.copy(carbsG = sanitizeInt(it)) }, KeyboardType.Number)
                                    },
                                )
                                TwoFieldRow(
                                    first = {
                                        LabeledField("Grasas (g) *", state.fatsG, { state = state.copy(fatsG = sanitizeInt(it)) }, KeyboardType.Number)
                                    },
                                    second = {
                                        SummaryMiniCard(
                                            title = "Objetivo elegido",
                                            value = formatGoalValue(state.goalMetric, goalValueD),
                                            detail = goalMetricLabel(state.goalMetric),
                                            icon = Icons.Default.Check,
                                        )
                                    },
                                )
                                
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    onClick = { showAdvanced = !showAdvanced },
                                    color = PANEL_ALT,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text("Ajustes avanzados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("Fórmula, ritmo semanal y multiplicadores.", style = MaterialTheme.typography.bodySmall, color = PANEL_MUTED)
                                        }
                                        Icon(
                                            if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color.White,
                                        )
                                    }
                                }

                                AnimatedVisibility(showAdvanced) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Spacer(Modifier.height(4.dp))
                                        SectionTitle("Fórmula")
                                        ChipRow {
                                            GoalChip("Mifflin", state.formula == FormulaType.MIFFLIN) { state = state.copy(formula = FormulaType.MIFFLIN) }
                                            GoalChip("Harris", state.formula == FormulaType.HARRIS) { state = state.copy(formula = FormulaType.HARRIS) }
                                            GoalChip("Katch", state.formula == FormulaType.KATCH) { state = state.copy(formula = FormulaType.KATCH) }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        if (state.goal != CalorieGoal.MAINTAIN) {
                                            Text("Cambio semanal deseado: ${state.weeklyChangeKg} kg", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Slider(
                                                value = state.weeklyChangeKg.toFloat(),
                                                onValueChange = { state = state.copy(weeklyChangeKg = ((it * 4).roundToInt() / 4f).toDouble()) },
                                                valueRange = 0.25f..2f,
                                                steps = 6,
                                            )
                                        }
                                        Text("Multiplicador de salud: %.2f".format(state.healthMultiplier), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Slider(
                                            value = state.healthMultiplier.toFloat(),
                                            onValueChange = { state = state.copy(healthMultiplier = ((it * 20).roundToInt() / 20f).toDouble()) },
                                            valueRange = 0.5f..1.5f,
                                            steps = 19,
                                        )
                                    }
                                }
                            }
                        }

                        EditorStep.SUMMARY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Resultado del cálculo")
                                HeroSummaryCard(
                                    calorieGoal = state.goal,
                                    goalMetric = state.goalMetric,
                                    goalValue = goalValueD,
                                    macroCalories = macroCalories,
                                    proteinGoal = proteinGoal,
                                    carbsGoal = carbsD.roundToInt(),
                                    fatsGoal = fatsD.roundToInt(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SummaryMiniCard(
                                        title = "Ritmo estimado",
                                        value = weeklyTrendKg?.let { signedKgPerWeek(it) } ?: "—",
                                        detail = "Según tus macros actuales",
                                        icon = Icons.Default.FitnessCenter,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SummaryMiniCard(
                                        title = "Calorías totales",
                                        value = "$macroCalories kcal",
                                        detail = tdee?.let { "Gasto estimado: $it kcal" } ?: "Completa tus datos",
                                        icon = Icons.Default.Restaurant,
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                if (riskFlags.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    SectionTitle("Alertas de riesgo")
                                    riskFlags.forEach { flag ->
                                        val tone = when (flag.severity) {
                                            RiskSeverity.DANGER -> Color(0xFF5E2323)
                                            RiskSeverity.WARNING -> Color(0xFF5D4A1B)
                                            RiskSeverity.INFO -> PANEL_ALT
                                        }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = tone),
                                            shape = RoundedCornerShape(16.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                                                Text(flag.message, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // FIXED FOOTER
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentStep == EditorStep.OBJECTIVE) {
                        TextButton(
                            onClick = ::requestDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                currentStep = when (currentStep) {
                                    EditorStep.BODY_DATA -> EditorStep.OBJECTIVE
                                    EditorStep.ACTIVITY -> EditorStep.BODY_DATA
                                    EditorStep.MACROS -> EditorStep.ACTIVITY
                                    EditorStep.SUMMARY -> EditorStep.MACROS
                                    else -> EditorStep.OBJECTIVE
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Atrás", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentStep == EditorStep.SUMMARY) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    com.example.kpkn.data.repository.ProgramRepository.getInstance().updateSettings { s ->
                                        s.copy(
                                            age = ageI.takeIf { it > 0 } ?: s.age,
                                            userVitals = s.userVitals.copy(
                                                height = heightD.takeIf { it > 0.0 } ?: s.userVitals.height,
                                                weight = weightD.takeIf { it > 0.0 } ?: s.userVitals.weight,
                                                gender = state.gender,
                                                bodyFatPercentage = bodyFatD ?: s.userVitals.bodyFatPercentage,
                                                muscleMassPercentage = muscleD ?: s.userVitals.muscleMassPercentage,
                                            )
                                        )
                                    }
                                }
                                onSave(
                                    NutritionPlan(
                                        id = activePlan?.id ?: UUID.randomUUID().toString(),
                                        name = activePlan?.name ?: "Plan de alimentación",
                                        goalType = state.goalMetric,
                                        goalValue = goalValueD,
                                        calorieTarget = macroCalories,
                                        proteinGoal = proteinGoal,
                                        carbGoal = carbsD.roundToInt(),
                                        fatGoal = fatsD.roundToInt(),
                                        isActive = true,
                                        createdAt = activePlan?.createdAt ?: Instant.now().toString(),
                                        primaryGoal = NutritionGoal(
                                            metric = state.goalMetric,
                                            value = goalValueD,
                                            label = goalMetricLabel(state.goalMetric),
                                            unit = goalMetricUnit(state.goalMetric),
                                        ),
                                        estimatedEndDate = activePlan?.estimatedEndDate,
                                        weeklyChangeKg = state.weeklyChangeKg,
                                        startValue = activePlan?.startValue ?: fallbackGoalValue(GoalMetric.WEIGHT, weightD, bodyFatD, muscleD),
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ACCENT_GREEN,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(if (activePlan == null) Icons.Default.Add else Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (activePlan == null) "Crear plan" else "Guardar cambios", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                when (currentStep) {
                                    EditorStep.OBJECTIVE -> {
                                        val gVal = state.goalValue.toDoubleOrNull()
                                        if (gVal != null && gVal > 0.0) {
                                            currentStep = EditorStep.BODY_DATA
                                        }
                                    }
                                    EditorStep.BODY_DATA -> {
                                        val hVal = state.height.toDoubleOrNull()
                                        val wVal = state.weight.toDoubleOrNull()
                                        val aVal = state.age.toIntOrNull()

                                        if (hVal == null || hVal <= 0.0) heightError = true
                                        if (wVal == null || wVal <= 0.0) weightError = true
                                        if (aVal == null || aVal <= 0) ageError = true

                                        if (heightError || weightError || ageError) {
                                            showValidationError = true
                                        } else {
                                            currentStep = EditorStep.ACTIVITY
                                        }
                                    }
                                    EditorStep.ACTIVITY -> {
                                        currentStep = EditorStep.MACROS
                                    }
                                    EditorStep.MACROS -> {
                                        val pVal = state.proteinG.toDoubleOrNull()
                                        val cVal = state.carbsG.toDoubleOrNull()
                                        val fVal = state.fatsG.toDoubleOrNull()

                                        if (pVal != null && pVal > 0 && cVal != null && cVal > 0 && fVal != null && fVal > 0) {
                                            currentStep = EditorStep.SUMMARY
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Siguiente", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: EditorStep,
    modifier: Modifier = Modifier
) {
    val steps = EditorStep.values()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isCurrent = step == currentStep
            val isCompleted = step.ordinal < currentStep.ordinal
            
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (isCompleted || isCurrent) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.15f)
                        )
                )
            }
            
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 24.dp else 12.dp, 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.primary
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
            )
        }
    }
}

private fun buildInitialState(currentSettings: Settings, activePlan: NutritionPlan?): EditorState {
    val goal = when {
        activePlan?.calorieTarget != null && currentSettings.dailyCalorieGoal != null -> {
            when {
                activePlan.calorieTarget < currentSettings.dailyCalorieGoal!! -> CalorieGoal.LOSE
                activePlan.calorieTarget > currentSettings.dailyCalorieGoal!! -> CalorieGoal.GAIN
                else -> CalorieGoal.MAINTAIN
            }
        }
        else -> when (currentSettings.calorieGoalObjective.name) {
            "DEFICIT" -> CalorieGoal.LOSE
            "SURPLUS" -> CalorieGoal.GAIN
            else -> CalorieGoal.MAINTAIN
        }
    }
    val vitals = currentSettings.userVitals
    val goalMetric = activePlan?.goalType ?: GoalMetric.WEIGHT
    val fallbackGoal = fallbackGoalValue(goalMetric, vitals.weight ?: 0.0, vitals.bodyFatPercentage, vitals.muscleMassPercentage)
    return EditorState(
        goal = goal,
        goalMetric = goalMetric,
        goalValue = formatGoalFieldValue(activePlan?.goalValue ?: fallbackGoal),
        height = vitals.height?.let(::formatGoalFieldValue).orEmpty(),
        weight = vitals.weight?.let(::formatGoalFieldValue).orEmpty(),
        age = currentSettings.age?.toString().orEmpty(),
        gender = vitals.gender ?: Gender.MALE,
        bodyFat = vitals.bodyFatPercentage?.let(::formatGoalFieldValue).orEmpty(),
        muscleMass = vitals.muscleMassPercentage?.let(::formatGoalFieldValue).orEmpty(),
        activityLevel = 3,
        dietaryPreference = "omnivore",
        formula = FormulaType.MIFFLIN,
        weeklyChangeKg = activePlan?.weeklyChangeKg ?: 0.5,
        healthMultiplier = 1.0,
        proteinG = (activePlan?.proteinGoal ?: currentSettings.dailyProteinGoal ?: 150).toString(),
        carbsG = (activePlan?.carbGoal ?: currentSettings.dailyCarbGoal ?: 220).toString(),
        fatsG = (activePlan?.fatGoal ?: currentSettings.dailyFatGoal ?: 70).toString(),
    )
}

@Composable
private fun HeroSummaryCard(
    calorieGoal: CalorieGoal,
    goalMetric: GoalMetric,
    goalValue: Double,
    macroCalories: Int,
    proteinGoal: Int,
    carbsGoal: Int,
    fatsGoal: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                    )
                )
                .padding(18.dp),
        ) {
            Column {
                Text(
                    when (calorieGoal) {
                        CalorieGoal.LOSE -> "Definición"
                        CalorieGoal.MAINTAIN -> "Mantención"
                        CalorieGoal.GAIN -> "Superávit"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = PANEL_MUTED,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${macroCalories} kcal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Objetivo: ${formatGoalValue(goalMetric, goalValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PANEL_MUTED,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroMetricPill("Proteínas", "${proteinGoal} g")
                    HeroMetricPill("Carbohidratos", "${carbsGoal} g")
                    HeroMetricPill("Grasas", "${fatsGoal} g")
                }
            }
        }
    }
}

@Composable
private fun HeroMetricPill(label: String, value: String) {
    val shortLabel = when (label.lowercase()) {
        "proteínas" -> "P"
        "carbohidratos" -> "C"
        "grasas" -> "G"
        else -> label.firstOrNull()?.toString()?.uppercase() ?: ""
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.06f),
        modifier = Modifier.border(1.dp, PANEL_STROKE, RoundedCornerShape(999.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    shortLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = Color.White,
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun GoalChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) Color.White else Color(0xFF2A2A2A),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = if (selected) Color.White else Color(0xFF333333),
            shape = RoundedCornerShape(12.dp)
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActivityChip(label: String, level: Int, selectedLevel: Int, onSelect: (Int) -> Unit) {
    GoalChip(label, selectedLevel == level) { onSelect(level) }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    placeholderText: String = "",
    error: Boolean = false,
    optional: Boolean = false,
) {
    val focusBorderColor = if (error) Color(0xFFE53935) else MaterialTheme.colorScheme.primary
    val borderStrokeColor = if (error) Color(0xFFE53935) else Color(0xFF333333)
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (optional) {
                Text(
                    "Opcional",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholderText.isNotEmpty()) {
                { Text(placeholderText, color = Color.White.copy(alpha = 0.3f)) }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .border(
                    width = 1.dp,
                    color = if (isFocused) focusBorderColor else borderStrokeColor,
                    shape = RoundedCornerShape(14.dp)
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2A2A2A),
                unfocusedContainerColor = Color(0xFF2A2A2A),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
            )
        )
    }
}

@Composable
private fun TwoFieldRow(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) { first() }
        Column(modifier = Modifier.weight(1f)) { second() }
    }
}

@Composable
private fun SummaryMiniCard(
    title: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = PANEL_MUTED, style = MaterialTheme.typography.labelMedium)
            Text(value, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = PANEL_MUTED, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun sanitizeInt(input: String): String = input.filter(Char::isDigit)

private fun sanitizeDecimal(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered else {
        filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}

private fun formatGoalFieldValue(value: Double): String {
    return if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(value)
}

private fun fallbackGoalValue(metric: GoalMetric, weight: Double, bodyFat: Double?, muscle: Double?): Double {
    return when (metric) {
        GoalMetric.WEIGHT -> if (weight > 0) weight else 70.0
        GoalMetric.BODY_FAT -> bodyFat ?: 15.0
        GoalMetric.MUSCLE_MASS -> muscle ?: 40.0
    }
}

private fun goalMetricLabel(metric: GoalMetric): String {
    return when (metric) {
        GoalMetric.WEIGHT -> "Peso corporal"
        GoalMetric.BODY_FAT -> "Grasa corporal"
        GoalMetric.MUSCLE_MASS -> "Masa muscular"
    }
}

private fun goalMetricUnit(metric: GoalMetric): String {
    return when (metric) {
        GoalMetric.WEIGHT -> "kg"
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
    }
}

private fun formatGoalValue(metric: GoalMetric, value: Double): String = when (metric) {
    GoalMetric.WEIGHT -> "${formatGoalFieldValue(value)} kg"
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "${formatGoalFieldValue(value)}%"
}

private fun signedKgPerWeek(value: Double): String {
    val prefix = if (value > 0) "+" else ""
    return "$prefix${"%.2f".format(value)} kg/sem"
}
