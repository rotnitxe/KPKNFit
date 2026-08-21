package com.example.kpkn.screens.nutrition.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.MetabolicProfile
import com.example.kpkn.data.models.NutritionGoal
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.domain.nutrition.CalorieGoal
import com.example.kpkn.domain.nutrition.CalorieGoalConfig
import com.example.kpkn.domain.nutrition.FormulaType
import com.example.kpkn.domain.nutrition.NutritionInput
import com.example.kpkn.domain.nutrition.RiskInput
import com.example.kpkn.domain.nutrition.RiskSeverity
import com.example.kpkn.domain.nutrition.buildNutritionRiskFlags
import com.example.kpkn.domain.nutrition.calculateBMR
import com.example.kpkn.domain.nutrition.calculateTDEE
import com.example.kpkn.domain.nutrition.caloriesFromMacros
import com.example.kpkn.domain.nutrition.estimatePlanEndDate
import com.example.kpkn.domain.nutrition.recommendPlanMacros
import com.example.kpkn.domain.nutrition.weeklyChangeFromCalories
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.KpknGlassDialog

private val OVERLAY_BG = Color(0xE60A0C10)
private val PANEL_BG = Color(0xF214171C)
private val PANEL_ALT = Color(0xFF1B2027)
private val PANEL_STROKE = Color.White.copy(alpha = 0.10f)
private val PANEL_MUTED = Color(0xFFB4BBC5)
private val ACCENT_GREEN = Color(0xFF2ECC71)

private val PROTEIN_COLOR = Color(0xFFEF5350)
private val CARBS_COLOR = Color(0xFF7E57C2)
private val FATS_COLOR = Color(0xFF26A69A)
private val CALORIES_COLOR = Color(0xFF42A5F5)

private enum class EditorStep {
    WELCOME,
    BODY_DATA,
    OBJECTIVE,
    MACROS,
    SUMMARY,
    EDIT_PLAN
}

private data class EditorState(
    val goal: CalorieGoal,
    val goalMetric: GoalMetric,
    val goalValue: String,
    val height: String,
    val weight: String,
    val age: String,
    val gender: Gender,                           // identidad; también influye en mínimos de grasa y umbrales de riesgo
    val metabolicProfile: MetabolicProfile,       // perfil hormonal, alimenta la fórmula de TMB
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
    val calorieTarget: String,
    val weightUnit: WeightUnit,
    val lastMacroTouched: String,
    val targetBodyFat: String,
    val targetMuscle: String,
)

private const val KG_TO_LB = 2.20462262185
private const val LB_TO_KG = 0.45359237

private fun formatWeightField(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** Convert displayed weight/goal when toggling kg ↔ lb without distorting the underlying mass. */
private fun withWeightUnit(state: EditorState, newUnit: WeightUnit): EditorState {
    if (state.weightUnit == newUnit) return state
    val weight = state.weight.toDoubleOrNull()
    val goal = state.goalValue.toDoubleOrNull()
    val convertedWeight = weight?.let {
        if (newUnit == WeightUnit.LBS) it * KG_TO_LB else it * LB_TO_KG
    }
    val convertedGoal = if (state.goalMetric == GoalMetric.WEIGHT) {
        goal?.let { if (newUnit == WeightUnit.LBS) it * KG_TO_LB else it * LB_TO_KG }
    } else null
    return state.copy(
        weightUnit = newUnit,
        weight = convertedWeight?.let(::formatWeightField) ?: state.weight,
        goalValue = convertedGoal?.let(::formatWeightField) ?: state.goalValue,
    )
}

@Composable
fun NutritionPlanEditorModal(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSave: (NutritionPlan) -> Unit,
    currentSettings: Settings = Settings(),
    activePlan: NutritionPlan? = null,
    onDeletePlan: ((String) -> Unit)? = null,
) {
    if (!isOpen) return

    val initialState = remember(isOpen, activePlan?.id, currentSettings) {
        buildInitialState(currentSettings, activePlan)
    }
    var state by remember(isOpen, activePlan?.id, currentSettings) { mutableStateOf(initialState) }
    var currentStep by remember(isOpen, activePlan?.id) {
        mutableStateOf(if (activePlan != null) EditorStep.EDIT_PLAN else EditorStep.WELCOME)
    }
    val coroutineScope = rememberCoroutineScope()

    var heightError by remember { mutableStateOf(false) }
    var weightError by remember { mutableStateOf(false) }
    var ageError by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }
    var showStep1ValidationError by remember { mutableStateOf(false) }
    var macrosValidationError by remember { mutableStateOf(false) }
    var calorieTargetError by remember { mutableStateOf(false) }
    // Tracks last goal to reset macros when goal changes in MACROS step
    var lastSyncedGoal by remember { mutableStateOf<CalorieGoal?>(null) }

    var showAdvanced by remember(isOpen, activePlan?.id) { mutableStateOf(activePlan != null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showBodyDataEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isDirty = state != initialState

    val weightKg = if (state.weightUnit == WeightUnit.LBS) {
        (state.weight.toDoubleOrNull() ?: 0.0) * LB_TO_KG
    } else {
        state.weight.toDoubleOrNull() ?: 0.0
    }
    val heightD = state.height.toDoubleOrNull() ?: 0.0
    val ageI = state.age.toIntOrNull() ?: 0
    val bodyFatD = state.bodyFat.toDoubleOrNull()
    val muscleD = state.muscleMass.toDoubleOrNull()
    val goalValueD = state.goalValue.toDoubleOrNull() ?: fallbackGoalValue(state.goalMetric, weightKg, bodyFatD, muscleD)
    val proteinD = state.proteinG.toDoubleOrNull() ?: 0.0
    val carbsD = state.carbsG.toDoubleOrNull() ?: 0.0
    val fatsD = state.fatsG.toDoubleOrNull() ?: 0.0

    val nutritionInput = NutritionInput(
        weightKg = weightKg,
        heightCm = heightD,
        age = ageI,
        gender = state.gender,
        metabolicProfile = state.metabolicProfile,
        bodyFatPercentage = bodyFatD,
    )
    val effectiveFormula = when {
        bodyFatD != null && bodyFatD > 0 && state.formula == FormulaType.MIFFLIN -> FormulaType.KATCH
        else -> state.formula
    }
    val calorieConfig = CalorieGoalConfig(
        formula = effectiveFormula,
        activityLevel = state.activityLevel,
        goal = state.goal,
        weeklyChangeKg = state.weeklyChangeKg,
        healthMultiplier = state.healthMultiplier,
    )
    val bmr = calculateBMR(nutritionInput, calorieConfig)
    val tdee = calculateTDEE(nutritionInput, calorieConfig)

    val recommended = recommendPlanMacros(nutritionInput, calorieConfig, state.dietaryPreference)
    val proteinMultiplier = recommended?.dietProteinMultiplier ?: when (state.dietaryPreference) {
        "vegan" -> 1.15
        "vegetarian" -> 1.08
        else -> 1.0
    }
    val proteinPerKg = recommended?.proteinPerKg ?: when (state.goal) {
        CalorieGoal.LOSE -> 2.3
        CalorieGoal.MAINTAIN -> 1.8
        CalorieGoal.GAIN -> 2.0
    }
    val autoProtein = recommended?.proteinG
        ?: kotlin.math.round(weightKg * proteinPerKg * proteinMultiplier).toInt().coerceAtLeast(40)
    val autoFatsMin = recommended?.let {
        kotlin.math.max(
            kotlin.math.round(weightKg * it.fatPerKgMin).toInt(),
            if (state.gender == Gender.FEMALE) 50 else 45,
        )
    } ?: run {
        val fatPerKgMin = if (state.gender == Gender.FEMALE) 1.0 else 0.7
        kotlin.math.max(
            kotlin.math.round(weightKg * fatPerKgMin).toInt(),
            if (state.gender == Gender.FEMALE) 50 else 45,
        )
    }
    val autoFats = recommended?.fatsG ?: autoFatsMin
    val autoCarbs = recommended?.carbsG ?: 40
    val recommendedCalories = recommended?.calories ?: (tdee ?: 2000)

    val proteinGoal = proteinD.roundToInt()
    val macroCaloriesFromMacros = caloriesFromMacros(
        proteinG = proteinGoal,
        carbsG = carbsD.roundToInt(),
        fatsG = fatsD.roundToInt(),
    )
    // The explicit target is the source of truth. Macro sliders update it, but
    // the summary and persistence must never silently replace it with a new sum.
    val macroCalories = state.calorieTarget.toIntOrNull()?.takeIf { it > 0 }
        ?: macroCaloriesFromMacros
    val weeklyTrendKg = tdee?.takeIf { it > 0 }?.let { weeklyChangeFromCalories(macroCalories, it) }

    val weeklyRateStatus = when {
        weeklyTrendKg == null -> null
        state.goalMetric == GoalMetric.BODY_FAT -> {
            val weeklyPct = if (weightKg > 0) kotlin.math.abs(weeklyTrendKg) / weightKg * 100.0 else 0.0
            when {
                weeklyPct > 1.0 -> "danger"
                weeklyPct > 0.5 -> "warning"
                else -> "safe"
            }
        }
        else -> when {
            kotlin.math.abs(weeklyTrendKg) > 1.5 -> "danger"
            kotlin.math.abs(weeklyTrendKg) > 1.0 -> "warning"
            else -> "safe"
        }
    }

    val goalValueKg = if (state.goalMetric == GoalMetric.WEIGHT && state.weightUnit == WeightUnit.LBS) {
        goalValueD * LB_TO_KG
    } else {
        goalValueD
    }

    val effectiveWeeklyChangeKg = weeklyTrendKg?.let { kotlin.math.abs(it) } ?: state.weeklyChangeKg
    val riskFlags = remember(
        state.goal,
        state.goalMetric,
        state.goalValue,
        effectiveWeeklyChangeKg,
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
                calorieTarget = state.calorieTarget.toIntOrNull() ?: macroCalories,
                goalMetric = state.goalMetric,
                goalValue = goalValueKg,
                weeklyChangeKg = effectiveWeeklyChangeKg,
                calorieGoal = state.goal,
            )
        )
    }
    val blocksSave = riskFlags.any { it.hardStop }
    val formulaLabel = when (effectiveFormula) {
        FormulaType.KATCH -> "Katch–McArdle"
        FormulaType.HARRIS -> "Harris–Benedict"
        FormulaType.MIFFLIN -> "Mifflin–St Jeor"
    }

    fun syncMacrosFromCalories(newKcal: Int) {
        val totalFrom = proteinD * 4 + carbsD * 4 + fatsD * 9
        if (totalFrom <= 0) {
            val p = kotlin.math.round(newKcal * 0.30 / 4).toInt()
            val f = kotlin.math.round(newKcal * 0.30 / 9).toInt()
            val c = kotlin.math.round((newKcal - p * 4 - f * 9) / 4.0).toInt().coerceAtLeast(40)
            state = state.copy(
                proteinG = p.toString(),
                fatsG = f.toString(),
                carbsG = c.toString(),
                calorieTarget = newKcal.toString(),
                lastMacroTouched = "calories",
            )
        } else {
            val pRatio = (proteinD * 4) / totalFrom
            val cRatio = (carbsD * 4) / totalFrom
            val fRatio = (fatsD * 9) / totalFrom
            val newP = kotlin.math.round(newKcal * pRatio / 4).toInt().coerceAtLeast(20)
            val newF = kotlin.math.round(newKcal * fRatio / 9).toInt().coerceAtLeast(10)
            val newC = kotlin.math.round((newKcal - newP * 4 - newF * 9) / 4.0).toInt().coerceAtLeast(40)
            state = state.copy(
                proteinG = newP.toString(),
                fatsG = newF.toString(),
                carbsG = newC.toString(),
                calorieTarget = newKcal.toString(),
                lastMacroTouched = "calories",
            )
        }
    }

    fun updateMacros(protein: Int = proteinGoal, carbs: Int = carbsD.roundToInt(), fats: Int = fatsD.roundToInt(), touched: String) {
        state = state.copy(
            proteinG = protein.toString(),
            carbsG = carbs.toString(),
            fatsG = fats.toString(),
            calorieTarget = caloriesFromMacros(protein, carbs, fats).toString(),
            lastMacroTouched = touched,
        )
    }

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    // Resetear macros automáticamente cuando el usuario cambia el objetivo (LOSE/GAIN/MAINTAIN)
    // Esto garantiza que las calorías se actualicen al instante en el Paso 3.
    androidx.compose.runtime.LaunchedEffect(state.goal) {
        if (currentStep == EditorStep.MACROS && lastSyncedGoal != null && lastSyncedGoal != state.goal) {
            val macros = recommendPlanMacros(nutritionInput, calorieConfig.copy(goal = state.goal), state.dietaryPreference)
            if (macros != null) {
                state = state.copy(
                    proteinG = macros.proteinG.toString(),
                    fatsG = macros.fatsG.toString(),
                    carbsG = macros.carbsG.toString(),
                    calorieTarget = macros.calories.toString(),
                    lastMacroTouched = "",
                )
            }
        }
        lastSyncedGoal = state.goal
    }

    BackHandler(enabled = true) {
        if (currentStep == EditorStep.EDIT_PLAN) {
            requestDismiss()
        } else if (currentStep != EditorStep.WELCOME) {
            currentStep = when (currentStep) {
                EditorStep.BODY_DATA -> EditorStep.WELCOME
                EditorStep.OBJECTIVE -> EditorStep.BODY_DATA
                EditorStep.MACROS -> EditorStep.OBJECTIVE
                EditorStep.SUMMARY -> EditorStep.MACROS
                else -> EditorStep.WELCOME
            }
        } else {
            requestDismiss()
        }
    }

    if (showDiscardConfirm) {
        KpknAlertDialog(
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

    if (showDeleteConfirm) {
        KpknAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar plan actual?") },
            text = { Text("Esta acción eliminará de forma permanente tu plan de alimentación y todas tus metas configuradas. Tendrás que crear un plan desde cero. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        activePlan?.id?.let { planId ->
                            onDeletePlan?.invoke(planId)
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) { Text("Eliminar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    KpknGlassDialog(
        onDismissRequest = { requestDismiss() },
        dismissOnScrimClick = false,
        dismissOnBackPress = false,
        shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
        modifier = Modifier.fillMaxHeight(0.94f),
        maxWidth = 720.dp,
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (activePlan == null) "Crear plan de alimentaci\u00F3n" else "Editar plan de alimentaci\u00F3n",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when (currentStep) {
                                EditorStep.WELCOME -> "Paso 0: Bienvenida"
                                EditorStep.BODY_DATA -> "Paso 1: Tus datos, actividad y dieta"
                                EditorStep.OBJECTIVE -> "Paso 2: Define tu objetivo"
                                EditorStep.MACROS -> "Paso 3: Ajusta tus macros y ritmo"
                                EditorStep.SUMMARY -> "Paso 4: Resumen final"
                                EditorStep.EDIT_PLAN -> "Edición rápida de tu plan"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = PANEL_MUTED,
                        )
                    }
                    IconButton(onClick = ::requestDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                if (currentStep != EditorStep.EDIT_PLAN) {
                    Spacer(Modifier.height(14.dp))
                    StepIndicator(currentStep = currentStep)
                }
                Spacer(Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (currentStep) {
                        EditorStep.WELCOME -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = ACCENT_GREEN,
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    "¡Crea tu Plan de Alimentación!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Esta herramienta te guiará paso a paso para estructurar un plan de alimentación adaptado a tu metabolismo, nivel de actividad física diaria y metas de composición corporal.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = PANEL_ALT)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFFF8F00),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                "Aviso importante",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFFF8F00)
                                            )
                                        }
                                        Text(
                                            "Este asistente provee sugerencias y estimaciones basadas en ecuaciones científicas de gasto energético. Sin embargo, no reemplaza la evaluación o el diagnóstico personalizado de un nutricionista o médico profesional.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PANEL_MUTED
                                        )
                                    }
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1229))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = Color(0xFFCE93D8),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                "Salud mental y alimentación",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFCE93D8)
                                            )
                                        }
                                        Text(
                                            "Si tienes o has tenido una relación difícil con la comida, restricciones extremas, episodios de atracones o pensamientos intrusivos sobre tu cuerpo o peso, te recomendamos consultar con un profesional de salud mental antes de usar esta herramienta. Tu bienestar integral es lo primero.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PANEL_MUTED
                                        )
                                    }
                                }
                            }
                        }
                        EditorStep.OBJECTIVE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Objetivo del plan")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GoalChipNoBorder("Definición", state.goal == CalorieGoal.LOSE, Modifier.weight(1f)) { state = state.copy(goal = CalorieGoal.LOSE, goalMetric = GoalMetric.WEIGHT) }
                                    Spacer(Modifier.width(8.dp))
                                    GoalChipNoBorder("Mantención", state.goal == CalorieGoal.MAINTAIN, Modifier.weight(1f)) { state = state.copy(goal = CalorieGoal.MAINTAIN, goalMetric = GoalMetric.WEIGHT) }
                                    Spacer(Modifier.width(8.dp))
                                    GoalChipNoBorder("Superávit", state.goal == CalorieGoal.GAIN, Modifier.weight(1f)) { state = state.copy(goal = CalorieGoal.GAIN, goalMetric = GoalMetric.WEIGHT) }
                                }

                                val goalDescription = when (state.goal) {
                                    CalorieGoal.LOSE -> "Consiste en un déficit calórico moderado para oxidar grasa corporal reteniendo la mayor cantidad de masa muscular posible."
                                    CalorieGoal.MAINTAIN -> "Consiste en consumir las calorías de mantenimiento para recomposición corporal (perder grasa y ganar músculo al mismo tiempo) o consolidar el peso."
                                    CalorieGoal.GAIN -> "Consiste en un superávit calórico controlado para maximizar la síntesis proteica y el desarrollo de masa muscular y fuerza."
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = PANEL_ALT)
                                ) {
                                    Text(
                                        text = goalDescription,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PANEL_MUTED,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }

                                SectionTitle("Métricas del objetivo")
                                val weightLabel = if (state.weightUnit == WeightUnit.LBS) "lb" else "kg"
                                LabeledField(
                                    label = "Meta de peso ($weightLabel) *",
                                    value = state.goalValue,
                                    onValueChange = {
                                        state = state.copy(goalValue = sanitizeDecimal(it))
                                        showStep1ValidationError = false
                                    },
                                    keyboardType = KeyboardType.Decimal,
                                    placeholderText = "70"
                                )

                                LabeledField(
                                    label = "Meta de grasa corporal (%)",
                                    value = state.targetBodyFat,
                                    onValueChange = { state = state.copy(targetBodyFat = sanitizeDecimal(it)) },
                                    keyboardType = KeyboardType.Decimal,
                                    placeholderText = "15.0",
                                    optional = true
                                )

                                LabeledField(
                                    label = "Meta de masa muscular (%)",
                                    value = state.targetMuscle,
                                    onValueChange = { state = state.copy(targetMuscle = sanitizeDecimal(it)) },
                                    keyboardType = KeyboardType.Decimal,
                                    placeholderText = "40.0",
                                    optional = true
                                )

                                if (showStep1ValidationError) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Por favor ingresa un peso objetivo válido (*)",
                                        color = Color(0xFFE53935),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        EditorStep.BODY_DATA -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Datos corporales esenciales")
                                val wLabel = if (state.weightUnit == WeightUnit.LBS) "lb" else "kg"

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GoalChipNoBorder("kg", state.weightUnit == WeightUnit.KG, Modifier.widthIn(max = 50.dp)) {
                                        state = withWeightUnit(state, WeightUnit.KG)
                                    }
                                    GoalChipNoBorder("lb", state.weightUnit == WeightUnit.LBS, Modifier.widthIn(max = 50.dp)) {
                                        state = withWeightUnit(state, WeightUnit.LBS)
                                    }
                                }

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
                                            label = "Peso actual ($wLabel) *",
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
                                            label = "% m\u00FAsculo",
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
                                                "¿Cómo te identificas? *",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                            Text(
                                                "Usado para mínimos de grasa y alertas de riesgo.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PANEL_MUTED,
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                GoalChipNoBorder("Hombre", state.gender == Gender.MALE, Modifier.weight(1f)) { state = state.copy(gender = Gender.MALE) }
                                                GoalChipNoBorder("Mujer", state.gender == Gender.FEMALE, Modifier.weight(1f)) { state = state.copy(gender = Gender.FEMALE) }
                                                GoalChipNoBorder("Otro", state.gender == Gender.OTHER, Modifier.weight(1f)) { state = state.copy(gender = Gender.OTHER) }
                                            }
                                        }
                                    },
                                )

                                // Segunda pregunta: perfil hormonal para el cálculo de calorías
                                Spacer(Modifier.height(4.dp))
                                Column {
                                    Text(
                                        "¿Qué hormonas predominan más en tu cuerpo hoy? *",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Text(
                                        "Usado para calcular tus calorías con mayor precisión.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PANEL_MUTED,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        GoalChipNoBorder(
                                            "💪 Testosterona",
                                            state.metabolicProfile == MetabolicProfile.TESTOSTERONE,
                                            Modifier.weight(1f)
                                        ) { state = state.copy(metabolicProfile = MetabolicProfile.TESTOSTERONE) }
                                        GoalChipNoBorder(
                                            "🌸 Estrógenos",
                                            state.metabolicProfile == MetabolicProfile.ESTROGEN,
                                            Modifier.weight(1f)
                                        ) { state = state.copy(metabolicProfile = MetabolicProfile.ESTROGEN) }
                                        GoalChipNoBorder(
                                            "≈ Mixto",
                                            state.metabolicProfile == MetabolicProfile.MIXED,
                                            Modifier.weight(1f)
                                        ) { state = state.copy(metabolicProfile = MetabolicProfile.MIXED) }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                SectionTitle("Nivel de actividad")
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ActivityChip("Sedentario", 1, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                        ActivityChip("Ligero", 2, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                        ActivityChip("Moderado", 3, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ActivityChip("Activo", 4, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                        ActivityChip("Muy activo", 5, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                SectionTitle("Preferencia alimentaria")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    GoalChipNoBorder("Omn\u00EDvoro", state.dietaryPreference == "omnivore", Modifier.weight(1f)) { state = state.copy(dietaryPreference = "omnivore") }
                                    GoalChipNoBorder("Vegetariano", state.dietaryPreference == "vegetarian", Modifier.weight(1f)) { state = state.copy(dietaryPreference = "vegetarian") }
                                    GoalChipNoBorder("Vegano", state.dietaryPreference == "vegan", Modifier.weight(1f)) { state = state.copy(dietaryPreference = "vegan") }
                                }

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

                        EditorStep.MACROS -> {
                            val isCustomPace = state.lastMacroTouched.isNotEmpty()
                            
                            fun setPace(pace: Double) {
                                val macros = recommendPlanMacros(
                                    nutritionInput,
                                    calorieConfig.copy(weeklyChangeKg = pace, goal = state.goal),
                                    state.dietaryPreference,
                                )
                                if (macros != null) {
                                    state = state.copy(
                                        weeklyChangeKg = pace,
                                        proteinG = macros.proteinG.toString(),
                                        fatsG = macros.fatsG.toString(),
                                        carbsG = macros.carbsG.toString(),
                                        calorieTarget = macros.calories.toString(),
                                        lastMacroTouched = "",
                                    )
                                } else {
                                    state = state.copy(weeklyChangeKg = pace, lastMacroTouched = "")
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier.size(130.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MacroBabushkaRings(
                                            caloriesPct = if (tdee != null && tdee > 0) (macroCalories.toFloat() / tdee).coerceIn(0f, 1.5f) else 0.85f,
                                            proteinPct = if (autoProtein > 0) (proteinGoal.toFloat() / autoProtein).coerceIn(0f, 1.5f) else 0.72f,
                                            carbsPct = if (autoCarbs > 0) (carbsD.toFloat() / autoCarbs).coerceIn(0f, 1.5f) else 0.58f,
                                            fatsPct = if (autoFats > 0) (fatsD.toFloat() / autoFats).coerceIn(0f, 1.5f) else 0.48f,
                                        )
                                        // Removed overlapping center text as per request
                                    }

                                    Spacer(Modifier.width(18.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val goalColor = when (state.goal) {
                                            CalorieGoal.LOSE -> Color(0xFFEF5350)
                                            CalorieGoal.GAIN -> Color(0xFF2ECC71)
                                            CalorieGoal.MAINTAIN -> Color(0xFF42A5F5)
                                        }
                                        Surface(
                                            color = goalColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = when (state.goal) {
                                                    CalorieGoal.LOSE -> "D\u00C9FICIT"
                                                    CalorieGoal.GAIN -> "SUPER\u00C1VIT"
                                                    CalorieGoal.MAINTAIN -> "MANTENIMIENTO"
                                                },
                                                color = goalColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "Presupuesto",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PANEL_MUTED
                                        )
                                        Text(
                                            text = "$macroCalories kcal",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        tdee?.let { t ->
                                            val diff = macroCalories - t
                                            val text = when {
                                                diff < 0 -> "${kotlin.math.abs(diff)} kcal menos del gasto"
                                                diff > 0 -> "+${diff} kcal de super\u00E1vit"
                                                else -> "Equilibrado con tu gasto"
                                            }
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PANEL_MUTED,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                SectionTitle("Ajuste de macronutrientes")
                                SectionTitle("Presupuesto calórico")
                                LabeledField(
                                    label = "Calorías diarias (kcal) *",
                                    value = state.calorieTarget,
                                    onValueChange = { rawValue ->
                                        val sanitized = sanitizeInt(rawValue)
                                        val entered = sanitized.toIntOrNull()
                                        calorieTargetError = entered == null || entered <= 0
                                        state = state.copy(
                                            calorieTarget = sanitized,
                                            lastMacroTouched = "calories",
                                        )
                                        // Avoid reshaping macros while the user is
                                        // still typing a multi-digit value.
                                        if (entered != null && entered >= 800) {
                                            syncMacrosFromCalories(entered)
                                        }
                                    },
                                    keyboardType = KeyboardType.Number,
                                    placeholderText = recommendedCalories.toString(),
                                    error = calorieTargetError,
                                )
                                Text(
                                    "Recomendación actual: $recommendedCalories kcal. Si cambias un macro, el presupuesto se actualizará con esos gramos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PANEL_MUTED,
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MacroSliderRow("Proteína", proteinGoal, "g", PROTEIN_COLOR, (autoProtein - 60).coerceAtLeast(40)..(autoProtein + 60).coerceAtLeast(80)) { p ->
                                        updateMacros(protein = p, touched = "proteinG")
                                    }
                                    MacroSliderRow("Carbohidratos", carbsD.roundToInt(), "g", CARBS_COLOR, (autoCarbs - 100).coerceAtLeast(60)..(autoCarbs + 100).coerceAtLeast(120)) { c ->
                                        updateMacros(carbs = c, touched = "carbsG")
                                    }
                                    MacroSliderRow("Grasas", fatsD.roundToInt(), "g", FATS_COLOR, (autoFats - 30).coerceAtLeast(20)..(autoFats + 30).coerceAtLeast(40)) { f ->
                                        updateMacros(fats = f, touched = "fatsG")
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                SectionTitle("Ritmo de progreso")

                                when (state.goal) {
                                    CalorieGoal.LOSE -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val isLento = !isCustomPace && state.weeklyChangeKg == 0.3
                                            val isRec = !isCustomPace && state.weeklyChangeKg == 0.5
                                            val isRapido = !isCustomPace && state.weeklyChangeKg == 0.8
                                            
                                            GoalChipNoBorder("Lento", isLento, Modifier.weight(1f)) { setPace(0.3) }
                                            GoalChipNoBorder("Recomendado", isRec, Modifier.weight(1f)) { setPace(0.5) }
                                            GoalChipNoBorder("R\u00E1pido", isRapido, Modifier.weight(1f)) { setPace(0.8) }
                                            if (isCustomPace) {
                                                GoalChipNoBorder("Personalizado", true, Modifier.weight(1.2f)) {}
                                            }
                                        }
                                    }
                                    CalorieGoal.GAIN -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val isLento = !isCustomPace && state.weeklyChangeKg == 0.2
                                            val isRec = !isCustomPace && state.weeklyChangeKg == 0.4
                                            val isRapido = !isCustomPace && state.weeklyChangeKg == 0.6
                                            
                                            GoalChipNoBorder("Lento", isLento, Modifier.weight(1f)) { setPace(0.2) }
                                            GoalChipNoBorder("Recomendado", isRec, Modifier.weight(1f)) { setPace(0.4) }
                                            GoalChipNoBorder("R\u00E1pido", isRapido, Modifier.weight(1f)) { setPace(0.6) }
                                            if (isCustomPace) {
                                                GoalChipNoBorder("Personalizado", true, Modifier.weight(1.2f)) {}
                                            }
                                        }
                                    }
                                    CalorieGoal.MAINTAIN -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            GoalChipNoBorder("Mantenimiento", !isCustomPace, Modifier.weight(1f)) { setPace(0.0) }
                                            if (isCustomPace) {
                                                GoalChipNoBorder("Personalizado", true, Modifier.weight(1f)) {}
                                            }
                                        }
                                    }
                                }

                                val delta = macroCalories - (tdee ?: 2000)
                                var alertText: String? = null
                                var alertColor = Color(0xFFFF8F00)

                                when {
                                    state.goal == CalorieGoal.LOSE && delta >= 0 -> {
                                        alertText = "Tus calor\u00EDas superan tu gasto diario. No lograr\u00E1s definirte con este ajuste."
                                        alertColor = Color(0xFFE53935)
                                    }
                                    state.goal == CalorieGoal.GAIN && delta <= 0 -> {
                                        alertText = "Tus calor\u00EDas son menores a tu gasto. No lograr\u00E1s ganar volumen."
                                        alertColor = Color(0xFFE53935)
                                    }
                                    isCustomPace -> {
                                        val trend = weeklyTrendKg ?: 0.0
                                        val absTrend = kotlin.math.abs(trend)
                                        if (state.goal == CalorieGoal.LOSE) {
                                            when {
                                                absTrend > 1.0 -> {
                                                    alertText = "Ritmo muy agresivo. Puede ser insostenible a largo plazo."
                                                    alertColor = Color(0xFFFF8F00)
                                                }
                                                absTrend < 0.15 -> {
                                                    alertText = "Ritmo muy lento. Ver\u00E1s cambios m\u00EDnimos."
                                                    alertColor = Color(0xFF42A5F5)
                                                }
                                            }
                                        } else if (state.goal == CalorieGoal.GAIN) {
                                            when {
                                                absTrend > 0.6 -> {
                                                    alertText = "Ritmo de ganancia muy agresivo. Ganar\u00E1s grasa excesiva."
                                                    alertColor = Color(0xFFFF8F00)
                                                }
                                                absTrend < 0.10 -> {
                                                    alertText = "Ritmo de ganancia muy lento. Considera comer un poco m\u00E1s."
                                                    alertColor = Color(0xFF42A5F5)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (alertText != null) {
                                    Spacer(Modifier.height(4.dp))
                                    WeeklyRateAlert(
                                        title = "Aviso del plan",
                                        message = alertText!!,
                                        color = alertColor
                                    )
                                }

                                if (macrosValidationError) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Los valores de proteína, carbohidratos y grasas deben ser mayores a 0.",
                                        color = Color(0xFFE53935),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        EditorStep.SUMMARY -> {
                            val weeksNeeded: Double? = when (state.goalMetric) {
                                GoalMetric.WEIGHT -> {
                                    val currentWeightInUnit = if (state.weightUnit == WeightUnit.LBS) {
                                        weightKg * KG_TO_LB
                                    } else {
                                        weightKg
                                    }
                                    val diff = kotlin.math.abs(currentWeightInUnit - goalValueD)
                                    val weeklyChangeInUnit = if (state.weightUnit == WeightUnit.LBS) {
                                        (weeklyTrendKg?.let { kotlin.math.abs(it) * KG_TO_LB }) ?: 0.0
                                    } else {
                                        (weeklyTrendKg?.let { kotlin.math.abs(it) }) ?: 0.0
                                    }
                                    if (weeklyChangeInUnit > 0.01) diff / weeklyChangeInUnit else null
                                }
                                GoalMetric.BODY_FAT -> {
                                    val currentFat = bodyFatD ?: 0.0
                                    if (currentFat > 0.0) {
                                        val diff = kotlin.math.abs(currentFat - goalValueD)
                                        val weeklyPct = if (weightKg > 0 && weeklyTrendKg != null) {
                                            kotlin.math.abs(weeklyTrendKg / weightKg * 100.0)
                                        } else 0.0
                                        if (weeklyPct > 0.01) diff / weeklyPct else null
                                    } else null
                                }
                                else -> null
                            }

                            val dateText = if (weeksNeeded != null && weeksNeeded in 0.1..104.0) {
                                val localDate = java.time.LocalDate.now().plusDays(kotlin.math.round(weeksNeeded * 7).toLong())
                                val formatter = java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", java.util.Locale("es", "ES"))
                                localDate.format(formatter)
                            } else null

                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SectionTitle("Resultado del plan")
                                HeroSummaryCard(
                                    calorieGoal = state.goal,
                                    goalMetric = state.goalMetric,
                                    goalValue = goalValueD,
                                    macroCalories = macroCalories,
                                    proteinGoal = proteinGoal,
                                    carbsGoal = carbsD.roundToInt(),
                                    fatsGoal = fatsD.roundToInt(),
                                    weightUnit = state.weightUnit,
                                )

                                if (dateText != null && weeksNeeded != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                "Fecha meta estimada",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = PANEL_MUTED,
                                                maxLines = 1
                                            )
                                            Text(
                                                dateText,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = ACCENT_GREEN,
                                                maxLines = 1
                                            )
                                            Text(
                                                "Meta alcanzable en aprox. ${weeksNeeded.roundToInt()} semanas.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PANEL_MUTED
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                SectionTitle("Ritmo semanal estimado")

                                val trendColor = when (weeklyRateStatus) {
                                    "danger" -> Color(0xFFE53935)
                                    "warning" -> Color(0xFFFF8F00)
                                    else -> Color(0xFF43A047)
                                }
                                val trendBg = trendColor.copy(alpha = 0.10f)

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = trendBg),
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("Cambio estimado", style = MaterialTheme.typography.labelMedium, color = trendColor, fontWeight = FontWeight.Bold)
                                            val displayTrend = if (state.weightUnit == WeightUnit.LBS) {
                                                weeklyTrendKg?.let { it * KG_TO_LB }
                                            } else {
                                                weeklyTrendKg
                                            }
                                            val trendUnit = if (state.weightUnit == WeightUnit.LBS) "lb/semana" else "kg/semana"
                                            Text(
                                                displayTrend?.let { val prefix = if (it >= 0) "+" else ""; "$prefix${"%.2f".format(it)} $trendUnit" } ?: "\u2014",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = trendColor,
                                            )
                                        }
                                        if (state.goalMetric == GoalMetric.BODY_FAT && weightKg > 0 && weeklyTrendKg != null) {
                                            val weeklyPct = (weeklyTrendKg / weightKg * 100.0)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text("% grasa estimado", style = MaterialTheme.typography.labelMedium, color = trendColor.copy(alpha = 0.8f))
                                                Text(
                                                    "${if (weeklyPct >= 0) "+" else ""}${"%.2f".format(weeklyPct)} %/semana",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = trendColor,
                                                )
                                            }
                                        }
                                        tdee?.let { t ->
                                            val delta = macroCalories - t
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    if (delta < 0) "D\u00E9ficit diario" else if (delta > 0) "Super\u00E1vit diario" else "Balance diario",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = PANEL_MUTED,
                                                )
                                                Text(
                                                    "${if (delta >= 0) "+" else ""}${delta} kcal",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = trendColor,
                                                )
                                            }
                                        }
                                    }
                                }

                                when (weeklyRateStatus) {
                                    "danger" -> WeeklyRateAlert(
                                        "Alerta de ritmo extremo",
                                        if (state.goalMetric == GoalMetric.BODY_FAT) {
                                            "El cambio de % grasa por semana es demasiado agresivo. Riesgo de p\u00E9rdida muscular o estr\u00E9s metab\u00F3lico."
                                        } else {
                                            "El cambio de peso semanal es muy alto (> 1.5 kg/sem). Esto no es seguro ni sostenible. Ajusta tus macros o el ritmo de cambio."
                                        },
                                        Color(0xFFE53935),
                                    )
                                    "warning" -> WeeklyRateAlert(
                                        "Ritmo agresivo",
                                        if (state.goalMetric == GoalMetric.BODY_FAT) {
                                            "Tu ritmo de cambio de % grasa es elevado. Considera un enfoque m\u00E1s gradual para preservar masa muscular."
                                        } else {
                                            "Est\u00E1s por encima del ritmo seguro de cambio de peso (> 1 kg/sem). Considera ser m\u00E1s conservador."
                                        },
                                        Color(0xFFFF8F00),
                                    )
                                    else -> {}
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SummaryMiniCard(
                                        title = "Calor\u00EDas totales",
                                        value = "$macroCalories kcal",
                                        detail = tdee?.let { "Gasto: $it kcal" } ?: "Completa tus datos",
                                        icon = Icons.Default.Restaurant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    SummaryMiniCard(
                                        title = "TMB estimado",
                                        value = bmr?.let { "${it.roundToInt()} kcal" } ?: "\u2014",
                                        detail = formulaLabel,
                                        icon = Icons.Default.FitnessCenter,
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
                        EditorStep.EDIT_PLAN -> {
                            val isCustomPace = state.lastMacroTouched.isNotEmpty()
                            
                            fun setPace(pace: Double) {
                                val macros = recommendPlanMacros(
                                    nutritionInput,
                                    calorieConfig.copy(weeklyChangeKg = pace, goal = state.goal),
                                    state.dietaryPreference,
                                )
                                if (macros != null) {
                                    state = state.copy(
                                        weeklyChangeKg = pace,
                                        proteinG = macros.proteinG.toString(),
                                        fatsG = macros.fatsG.toString(),
                                        carbsG = macros.carbsG.toString(),
                                        calorieTarget = macros.calories.toString(),
                                        lastMacroTouched = "",
                                    )
                                } else {
                                    state = state.copy(weeklyChangeKg = pace, lastMacroTouched = "")
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // 1. Objetivo y Métricas
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
                                    border = BorderStroke(1.dp, PANEL_STROKE)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SectionTitle("Objetivo de alimentación")
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            GoalChipNoBorder("Definición", state.goal == CalorieGoal.LOSE, Modifier.weight(1f)) { state = state.copy(goal = CalorieGoal.LOSE, goalMetric = GoalMetric.WEIGHT) }
                                            Spacer(Modifier.width(8.dp))
                                            GoalChipNoBorder("Mantención", state.goal == CalorieGoal.MAINTAIN, Modifier.weight(1f)) { state = state.copy(goal = CalorieGoal.MAINTAIN, goalMetric = GoalMetric.WEIGHT) }
                                            Spacer(Modifier.width(8.dp))
                                            GoalChipNoBorder("Superávit", state.goal == CalorieGoal.GAIN, Modifier.weight(1f)) { state = state.copy(goal = CalorieGoal.GAIN, goalMetric = GoalMetric.WEIGHT) }
                                        }

                                        val goalDescription = when (state.goal) {
                                            CalorieGoal.LOSE -> "Déficit moderado para oxidar grasa reteniendo masa muscular."
                                            CalorieGoal.MAINTAIN -> "Calorías de mantenimiento para recomposición o consolidación."
                                            CalorieGoal.GAIN -> "Superávit calórico controlado para maximizar síntesis de fuerza/músculo."
                                        }
                                        Text(
                                            text = goalDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PANEL_MUTED
                                        )

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 4.dp))

                                        SectionTitle("Métricas del objetivo")
                                        val weightLabel = if (state.weightUnit == WeightUnit.LBS) "lb" else "kg"
                                        LabeledField(
                                            label = "Meta de peso ($weightLabel) *",
                                            value = state.goalValue,
                                            onValueChange = {
                                                state = state.copy(goalValue = sanitizeDecimal(it))
                                                showStep1ValidationError = false
                                            },
                                            keyboardType = KeyboardType.Decimal,
                                            placeholderText = "70"
                                        )

                                        TwoFieldRow(
                                            first = {
                                                LabeledField(
                                                    label = "Meta de grasa (%)",
                                                    value = state.targetBodyFat,
                                                    onValueChange = { state = state.copy(targetBodyFat = sanitizeDecimal(it)) },
                                                    keyboardType = KeyboardType.Decimal,
                                                    placeholderText = "15.0",
                                                    optional = true
                                                )
                                            },
                                            second = {
                                                LabeledField(
                                                    label = "Meta de músculo (%)",
                                                    value = state.targetMuscle,
                                                    onValueChange = { state = state.copy(targetMuscle = sanitizeDecimal(it)) },
                                                    keyboardType = KeyboardType.Decimal,
                                                    placeholderText = "40.0",
                                                    optional = true
                                                )
                                            }
                                        )

                                        if (showStep1ValidationError) {
                                            Text(
                                                "Por favor ingresa un peso objetivo válido (*)",
                                                color = Color(0xFFE53935),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // 2. Presupuesto, Macros y sliders
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
                                    border = BorderStroke(1.dp, PANEL_STROKE)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.size(110.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                MacroBabushkaRings(
                                                    caloriesPct = if (tdee != null && tdee > 0) (macroCalories.toFloat() / tdee).coerceIn(0f, 1.5f) else 0.85f,
                                                    proteinPct = if (autoProtein > 0) (proteinGoal.toFloat() / autoProtein).coerceIn(0f, 1.5f) else 0.72f,
                                                    carbsPct = if (autoCarbs > 0) (carbsD.toFloat() / autoCarbs).coerceIn(0f, 1.5f) else 0.58f,
                                                    fatsPct = if (autoFats > 0) (fatsD.toFloat() / autoFats).coerceIn(0f, 1.5f) else 0.48f,
                                                )
                                            }
                                            Spacer(Modifier.width(14.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("Presupuesto diario", style = MaterialTheme.typography.bodySmall, color = PANEL_MUTED)
                                                Text("$macroCalories kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                                                tdee?.let { t ->
                                                    val diff = macroCalories - t
                                                    val text = when {
                                                        diff < 0 -> "${kotlin.math.abs(diff)} kcal de déficit"
                                                        diff > 0 -> "+${diff} kcal de superávit"
                                                        else -> "Equilibrado con tu gasto"
                                                    }
                                                    Text(text, style = MaterialTheme.typography.bodySmall, color = PANEL_MUTED)
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                                        SectionTitle("Ajustar macronutrientes")
                                        SectionTitle("Presupuesto calórico")
                                        LabeledField(
                                            label = "Calorías diarias (kcal) *",
                                            value = state.calorieTarget,
                                            onValueChange = { rawValue ->
                                                val sanitized = sanitizeInt(rawValue)
                                                val entered = sanitized.toIntOrNull()
                                                calorieTargetError = entered == null || entered <= 0
                                                state = state.copy(
                                                    calorieTarget = sanitized,
                                                    lastMacroTouched = "calories",
                                                )
                                                // Avoid reshaping macros while the user is
                                                // still typing a multi-digit value.
                                                if (entered != null && entered >= 800) {
                                                    syncMacrosFromCalories(entered)
                                                }
                                            },
                                            keyboardType = KeyboardType.Number,
                                            placeholderText = recommendedCalories.toString(),
                                            error = calorieTargetError,
                                        )
                                        Text(
                                            "Recomendación actual: $recommendedCalories kcal. Si cambias un macro, el presupuesto se actualizará con esos gramos.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PANEL_MUTED,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            MacroSliderRow("Proteína", proteinGoal, "g", PROTEIN_COLOR, (autoProtein - 60).coerceAtLeast(40)..(autoProtein + 60).coerceAtLeast(80)) { p ->
                                                updateMacros(protein = p, touched = "proteinG")
                                            }
                                            MacroSliderRow("Carbohidratos", carbsD.roundToInt(), "g", CARBS_COLOR, (autoCarbs - 100).coerceAtLeast(60)..(autoCarbs + 100).coerceAtLeast(120)) { c ->
                                                updateMacros(carbs = c, touched = "carbsG")
                                            }
                                            MacroSliderRow("Grasas", fatsD.roundToInt(), "g", FATS_COLOR, (autoFats - 30).coerceAtLeast(20)..(autoFats + 30).coerceAtLeast(40)) { f ->
                                                updateMacros(fats = f, touched = "fatsG")
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                                        SectionTitle("Ritmo de progreso")
                                        when (state.goal) {
                                            CalorieGoal.LOSE -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    val isLento = !isCustomPace && state.weeklyChangeKg == 0.3
                                                    val isRec = !isCustomPace && state.weeklyChangeKg == 0.5
                                                    val isRapido = !isCustomPace && state.weeklyChangeKg == 0.8
                                                    GoalChipNoBorder("Lento", isLento, Modifier.weight(1f)) { setPace(0.3) }
                                                    GoalChipNoBorder("Recomendado", isRec, Modifier.weight(1f)) { setPace(0.5) }
                                                    GoalChipNoBorder("Rápido", isRapido, Modifier.weight(1f)) { setPace(0.8) }
                                                    if (isCustomPace) GoalChipNoBorder("Personalizado", true, Modifier.weight(1.2f)) {}
                                                }
                                            }
                                            CalorieGoal.GAIN -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    val isLento = !isCustomPace && state.weeklyChangeKg == 0.2
                                                    val isRec = !isCustomPace && state.weeklyChangeKg == 0.4
                                                    val isRapido = !isCustomPace && state.weeklyChangeKg == 0.6
                                                    GoalChipNoBorder("Lento", isLento, Modifier.weight(1f)) { setPace(0.2) }
                                                    GoalChipNoBorder("Recomendado", isRec, Modifier.weight(1f)) { setPace(0.4) }
                                                    GoalChipNoBorder("Rápido", isRapido, Modifier.weight(1f)) { setPace(0.6) }
                                                    if (isCustomPace) GoalChipNoBorder("Personalizado", true, Modifier.weight(1.2f)) {}
                                                }
                                            }
                                            CalorieGoal.MAINTAIN -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    GoalChipNoBorder("Mantenimiento", !isCustomPace, Modifier.weight(1f)) { setPace(0.0) }
                                                    if (isCustomPace) GoalChipNoBorder("Personalizado", true, Modifier.weight(1f)) {}
                                                }
                                            }
                                        }

                                        val delta = macroCalories - (tdee ?: 2000)
                                        var alertText: String? = null
                                        var alertColor = Color(0xFFFF8F00)
                                        when {
                                            state.goal == CalorieGoal.LOSE && delta >= 0 -> {
                                                alertText = "Tus calorías superan tu gasto diario. No lograrás definirte con este ajuste."
                                                alertColor = Color(0xFFE53935)
                                            }
                                            state.goal == CalorieGoal.GAIN && delta <= 0 -> {
                                                alertText = "Tus calorías son menores a tu gasto. No lograrás ganar volumen."
                                                alertColor = Color(0xFFE53935)
                                            }
                                            isCustomPace -> {
                                                val trend = weeklyTrendKg ?: 0.0
                                                val absTrend = kotlin.math.abs(trend)
                                                if (state.goal == CalorieGoal.LOSE) {
                                                    if (absTrend > 1.0) { alertText = "Ritmo muy agresivo. Puede ser insostenible."; alertColor = Color(0xFFFF8F00) }
                                                    else if (absTrend < 0.15) { alertText = "Ritmo muy lento. Verás cambios mínimos."; alertColor = Color(0xFF42A5F5) }
                                                } else if (state.goal == CalorieGoal.GAIN) {
                                                    if (absTrend > 0.6) { alertText = "Ritmo muy agresivo. Ganarás grasa excesiva."; alertColor = Color(0xFFFF8F00) }
                                                    else if (absTrend < 0.10) { alertText = "Ritmo muy lento. Considera comer más."; alertColor = Color(0xFF42A5F5) }
                                                }
                                            }
                                        }

                                        if (alertText != null) {
                                            WeeklyRateAlert(title = "Aviso del plan", message = alertText, color = alertColor)
                                        }

                                        if (macrosValidationError) {
                                            Text(
                                                "Los valores de proteína, carbohidratos y grasas deben ser mayores a 0.",
                                                color = Color(0xFFE53935),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // 3. Datos Metabólicos y Fisiología (Colapsable)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
                                    border = BorderStroke(1.dp, PANEL_STROKE)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SectionTitle("Datos corporales y actividad")
                                            IconButton(onClick = { showBodyDataEdit = !showBodyDataEdit }) {
                                                Icon(
                                                    imageVector = if (showBodyDataEdit) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = "Expandir sección",
                                                    tint = Color.White
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = showBodyDataEdit) {
                                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                                val wLabel = if (state.weightUnit == WeightUnit.LBS) "lb" else "kg"
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    GoalChipNoBorder("kg", state.weightUnit == WeightUnit.KG, Modifier.widthIn(max = 50.dp)) {
                                                        state = withWeightUnit(state, WeightUnit.KG)
                                                    }
                                                    GoalChipNoBorder("lb", state.weightUnit == WeightUnit.LBS, Modifier.widthIn(max = 50.dp)) {
                                                        state = withWeightUnit(state, WeightUnit.LBS)
                                                    }
                                                }

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
                                                            label = "Peso actual ($wLabel) *",
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
                                                    }
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
                                                    }
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
                                                                "¿Cómo te identificas? *",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White,
                                                            )
                                                            Text(
                                                                "Usado para mínimos de grasa y alertas de riesgo.",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = PANEL_MUTED,
                                                            )
                                                            Spacer(Modifier.height(8.dp))
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                GoalChipNoBorder("Hombre", state.gender == Gender.MALE, Modifier.weight(1f)) { state = state.copy(gender = Gender.MALE) }
                                                                GoalChipNoBorder("Mujer", state.gender == Gender.FEMALE, Modifier.weight(1f)) { state = state.copy(gender = Gender.FEMALE) }
                                                                GoalChipNoBorder("Otro", state.gender == Gender.OTHER, Modifier.weight(1f)) { state = state.copy(gender = Gender.OTHER) }
                                                            }
                                                        }
                                                    }
                                                )

                                                Column {
                                                    Text(
                                                        "¿Qué hormonas predominan más en tu cuerpo hoy? *",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                    )
                                                    Text(
                                                        "Usado para calcular tus calorías con mayor precisión.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PANEL_MUTED,
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        GoalChipNoBorder("💪 Testosterona", state.metabolicProfile == MetabolicProfile.TESTOSTERONE, Modifier.weight(1f)) { state = state.copy(metabolicProfile = MetabolicProfile.TESTOSTERONE) }
                                                        GoalChipNoBorder("🌸 Estrógenos", state.metabolicProfile == MetabolicProfile.ESTROGEN, Modifier.weight(1f)) { state = state.copy(metabolicProfile = MetabolicProfile.ESTROGEN) }
                                                        GoalChipNoBorder("≈ Mixto", state.metabolicProfile == MetabolicProfile.MIXED, Modifier.weight(1f)) { state = state.copy(metabolicProfile = MetabolicProfile.MIXED) }
                                                    }
                                                }

                                                Spacer(Modifier.height(4.dp))
                                                SectionTitle("Nivel de actividad")
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        ActivityChip("Sedentario", 1, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                                        ActivityChip("Ligero", 2, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                                        ActivityChip("Moderado", 3, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                                    }
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        ActivityChip("Activo", 4, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                                        ActivityChip("Muy activo", 5, state.activityLevel, Modifier.weight(1f)) { state = state.copy(activityLevel = it) }
                                                    }
                                                }

                                                Spacer(Modifier.height(4.dp))
                                                SectionTitle("Preferencia alimentaria")
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    GoalChipNoBorder("Omnívoro", state.dietaryPreference == "omnivore", Modifier.weight(1f)) { state = state.copy(dietaryPreference = "omnivore") }
                                                    GoalChipNoBorder("Vegetariano", state.dietaryPreference == "vegetarian", Modifier.weight(1f)) { state = state.copy(dietaryPreference = "vegetarian") }
                                                    GoalChipNoBorder("Vegano", state.dietaryPreference == "vegan", Modifier.weight(1f)) { state = state.copy(dietaryPreference = "vegan") }
                                                }

                                                if (showValidationError) {
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
                                    }
                                }

                                // 4. Alertas de Riesgo
                                if (riskFlags.isNotEmpty()) {
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
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                                                Text(flag.message, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }

                                // Botón para eliminar el plan y comenzar de cero
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE53935).copy(alpha = 0.12f),
                                        contentColor = Color(0xFFE53935)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.35f))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Eliminar plan y empezar de cero", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentStep == EditorStep.WELCOME || currentStep == EditorStep.EDIT_PLAN) {
                        TextButton(
                            onClick = ::requestDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.dp, Color.Transparent),
                        ) {
                            Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                currentStep = when (currentStep) {
                                    EditorStep.BODY_DATA -> EditorStep.WELCOME
                                    EditorStep.OBJECTIVE -> EditorStep.BODY_DATA
                                    EditorStep.MACROS -> EditorStep.OBJECTIVE
                                    EditorStep.SUMMARY -> EditorStep.MACROS
                                    else -> EditorStep.WELCOME
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.dp, Color.Transparent),
                        ) {
                            Text("Atr\u00E1s", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }

                    if (currentStep == EditorStep.SUMMARY || currentStep == EditorStep.EDIT_PLAN) {
                        Button(
                            onClick = {
                                // En modo edición unificada, validamos todos los campos clave primero
                                if (currentStep == EditorStep.EDIT_PLAN) {
                                    val hVal = state.height.toDoubleOrNull()
                                    val wVal = state.weight.toDoubleOrNull()
                                    val aVal = state.age.toIntOrNull()

                                    if (hVal == null || hVal <= 0.0) heightError = true
                                    if (wVal == null || wVal <= 0.0) weightError = true
                                    if (aVal == null || aVal <= 0) ageError = true

                                    val gVal = state.goalValue.toDoubleOrNull()
                                    if (gVal == null || gVal <= 0.0) showStep1ValidationError = true

                                    val pVal = state.proteinG.toDoubleOrNull()
                                    val cVal = state.carbsG.toDoubleOrNull()
                                    val fVal = state.fatsG.toDoubleOrNull()
                                    if (pVal == null || pVal <= 0 || cVal == null || cVal <= 0 || fVal == null || fVal <= 0) {
                                        macrosValidationError = true
                                    } else {
                                        macrosValidationError = false
                                    }
                                }

                                val kcalVal = state.calorieTarget.toIntOrNull()
                                if (kcalVal == null || kcalVal <= 0) calorieTargetError = true

                                if (blocksSave) return@Button

                                if (!heightError && !weightError && !ageError && !showStep1ValidationError && !macrosValidationError && !calorieTargetError) {
                                    coroutineScope.launch {
                                        com.example.kpkn.data.repository.ProgramRepository.getInstance().updateSettings { s ->
                                            s.copy(
                                                age = ageI.takeIf { it > 0 } ?: s.age,
                                                weightUnit = state.weightUnit,
                                                nutritionActivityLevel = state.activityLevel,
                                                nutritionDietaryPreference = state.dietaryPreference,
                                                userVitals = s.userVitals.copy(
                                                    height = heightD.takeIf { it > 0.0 } ?: s.userVitals.height,
                                                    weight = weightKg.takeIf { it > 0.0 } ?: s.userVitals.weight,
                                                    gender = state.gender,
                                                    bodyFatPercentage = bodyFatD ?: s.userVitals.bodyFatPercentage,
                                                    muscleMassPercentage = muscleD ?: s.userVitals.muscleMassPercentage,
                                                    metabolicProfile = state.metabolicProfile,
                                                )
                                            )
                                        }
                                    }
                                    val finalGoalValue = if (state.goalMetric == GoalMetric.WEIGHT && state.weightUnit == WeightUnit.LBS) {
                                        goalValueD * LB_TO_KG
                                    } else {
                                        goalValueD
                                    }
                                    val finalWeeklyChange = weeklyTrendKg?.let { kotlin.math.abs(it) } ?: state.weeklyChangeKg
                                    val startVal = activePlan?.startValue
                                        ?: fallbackGoalValue(GoalMetric.WEIGHT, weightKg, bodyFatD, muscleD)
                                    val eta = estimatePlanEndDate(
                                        currentValue = if (state.goalMetric == GoalMetric.WEIGHT) weightKg else goalValueKg,
                                        goalValue = finalGoalValue,
                                        weeklyChangeKg = finalWeeklyChange,
                                    )

                                    onSave(
                                        NutritionPlan(
                                            id = activePlan?.id ?: UUID.randomUUID().toString(),
                                            name = activePlan?.name ?: "Plan de alimentación",
                                            goalType = state.goalMetric,
                                            goalValue = finalGoalValue,
                                            calorieTarget = state.calorieTarget.toIntOrNull() ?: macroCalories,
                                            proteinGoal = proteinGoal,
                                            carbGoal = carbsD.roundToInt(),
                                            fatGoal = fatsD.roundToInt(),
                                            isActive = true,
                                            createdAt = activePlan?.createdAt ?: Instant.now().toString(),
                                            primaryGoal = NutritionGoal(
                                                metric = state.goalMetric,
                                                value = finalGoalValue,
                                                label = goalMetricLabel(state.goalMetric),
                                                unit = goalMetricUnit(state.goalMetric, state.weightUnit),
                                            ),
                                            estimatedEndDate = eta,
                                            weeklyChangeKg = finalWeeklyChange,
                                            startValue = startVal,
                                            targetBodyFat = state.targetBodyFat.toDoubleOrNull(),
                                            targetMuscle = state.targetMuscle.toDoubleOrNull(),
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.2f).height(52.dp),
                            enabled = !blocksSave,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.dp, Color.Transparent),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ACCENT_GREEN,
                                contentColor = Color.White,
                                disabledContainerColor = Color.Gray.copy(alpha = 0.4f),
                                disabledContentColor = Color.White.copy(alpha = 0.6f),
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (blocksSave) "Corrige alertas críticas" else "Guardar cambios",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                when (currentStep) {
                                    EditorStep.WELCOME -> {
                                        currentStep = EditorStep.BODY_DATA
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
                                            currentStep = EditorStep.OBJECTIVE
                                        }
                                    }
                                    EditorStep.OBJECTIVE -> {
                                        val gVal = state.goalValue.toDoubleOrNull()
                                        if (gVal != null && gVal > 0.0) {
                                            state = state.copy(
                                                proteinG = autoProtein.toString(),
                                                carbsG = autoCarbs.toString(),
                                                fatsG = autoFats.toString(),
                                                calorieTarget = recommendedCalories.toString(),
                                                lastMacroTouched = "",
                                            )
                                            currentStep = EditorStep.MACROS
                                        } else {
                                            showStep1ValidationError = true
                                        }
                                    }
                                    EditorStep.MACROS -> {
                                        val pVal = state.proteinG.toDoubleOrNull()
                                        val cVal = state.carbsG.toDoubleOrNull()
                                        val fVal = state.fatsG.toDoubleOrNull()
                                        val kcalVal = state.calorieTarget.toIntOrNull()
                                         if (pVal != null && pVal > 0 && cVal != null && cVal > 0 && fVal != null && fVal > 0 && kcalVal != null && kcalVal > 0) {
                                            macrosValidationError = false
                                            calorieTargetError = false
                                            currentStep = EditorStep.SUMMARY
                                        } else {
                                            macrosValidationError = true
                                            if (kcalVal == null || kcalVal <= 0) calorieTargetError = true
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.dp, Color.Transparent),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Siguiente", fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
    }
}

@Composable
private fun MacroBabushkaRings(
    caloriesPct: Float,
    proteinPct: Float,
    carbsPct: Float,
    fatsPct: Float,
) {
    val animSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
    val aCal by animateFloatAsState(caloriesPct.coerceIn(0f, 1.2f), animSpec, label = "cal")
    val aPro by animateFloatAsState(proteinPct.coerceIn(0f, 1.2f), animSpec, label = "pro")
    val aCar by animateFloatAsState(carbsPct.coerceIn(0f, 1.2f), animSpec, label = "car")
    val aFat by animateFloatAsState(fatsPct.coerceIn(0f, 1.2f), animSpec, label = "fat")

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

@Composable
private fun MacroSliderRow(
    label: String,
    value: Int,
    unit: String,
    color: Color,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("$value $unit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(kotlin.math.round(it).toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.15f),
            ),
        )
    }
}

@Composable
private fun WeeklyRateAlert(
    title: String,
    message: String,
    color: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = color)
                Text(message, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: EditorStep, modifier: Modifier = Modifier) {
    val steps = EditorStep.entries.filter { it != EditorStep.EDIT_PLAN }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    .size(12.dp)
                    .clip(CircleShape)
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
    val goalMetric = activePlan?.typedBodyGoal?.metric ?: activePlan?.goalType ?: GoalMetric.WEIGHT
    val fallbackGoal = fallbackGoalValue(goalMetric, vitals.weight ?: 0.0, vitals.bodyFatPercentage, vitals.muscleMassPercentage)
    val wUnit = currentSettings.weightUnit

    val storedWeightKg = vitals.weight ?: 0.0
    val displayWeight = if (wUnit == WeightUnit.LBS && storedWeightKg > 0) {
        formatGoalFieldValue(storedWeightKg * KG_TO_LB)
    } else {
        vitals.weight?.let(::formatGoalFieldValue).orEmpty()
    }

    val displayGoalVal = if (goalMetric == GoalMetric.WEIGHT && wUnit == WeightUnit.LBS) {
        (activePlan?.goalValue ?: fallbackGoal) * KG_TO_LB
    } else {
        activePlan?.goalValue ?: fallbackGoal
    }

    return EditorState(
        goal = goal,
        goalMetric = goalMetric,
        goalValue = formatGoalFieldValue(displayGoalVal),
        height = vitals.height?.let(::formatGoalFieldValue).orEmpty(),
        weight = displayWeight,
        age = currentSettings.age?.toString().orEmpty(),
        gender = vitals.gender ?: Gender.MALE,
        bodyFat = vitals.bodyFatPercentage?.let(::formatGoalFieldValue).orEmpty(),
        muscleMass = vitals.muscleMassPercentage?.let(::formatGoalFieldValue).orEmpty(),
        activityLevel = currentSettings.nutritionActivityLevel.coerceIn(1, 5),
        dietaryPreference = currentSettings.nutritionDietaryPreference.ifBlank { "omnivore" },
        formula = FormulaType.MIFFLIN,
        weeklyChangeKg = activePlan?.weeklyChangeKg ?: 0.5,
        healthMultiplier = 1.0,
        proteinG = (activePlan?.proteinGoal ?: currentSettings.dailyProteinGoal ?: 150).toString(),
        carbsG = (activePlan?.carbGoal ?: currentSettings.dailyCarbGoal ?: 220).toString(),
        fatsG = (activePlan?.fatGoal ?: currentSettings.dailyFatGoal ?: 70).toString(),
        calorieTarget = activePlan?.calorieTarget?.takeIf { it > 0 }?.toString().orEmpty(),
        weightUnit = wUnit,
        lastMacroTouched = "",
        targetBodyFat = activePlan?.targetBodyFat?.let(::formatGoalFieldValue).orEmpty(),
        targetMuscle = activePlan?.targetMuscle?.let(::formatGoalFieldValue).orEmpty(),
        metabolicProfile = vitals.metabolicProfile ?: when (vitals.gender) {
            Gender.FEMALE -> MetabolicProfile.ESTROGEN
            Gender.MALE -> MetabolicProfile.TESTOSTERONE
            else -> MetabolicProfile.MIXED
        },
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
    weightUnit: WeightUnit = WeightUnit.KG,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PANEL_ALT),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                when (calorieGoal) {
                    CalorieGoal.LOSE -> "Definición"
                    CalorieGoal.MAINTAIN -> "Mantención"
                    CalorieGoal.GAIN -> "Superávit"
                },
                style = MaterialTheme.typography.labelLarge,
                color = PANEL_MUTED,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${macroCalories} kcal",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Objetivo: ${formatGoalValue(goalMetric, goalValue, weightUnit)}",
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

@Composable
private fun HeroMetricPill(label: String, value: String) {
    val shortLabel = when (label.lowercase()) {
        "prote\u00EDnas" -> "P"
        "carbohidratos" -> "C"
        "grasas" -> "G"
        else -> label.firstOrNull()?.toString()?.uppercase() ?: ""
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp).background(Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(shortLabel, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun GoalChipNoBorder(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) Color.White else Color(0xFF2A2A2A),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActivityChip(
    label: String,
    level: Int,
    selectedLevel: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    GoalChipNoBorder(label, selectedLevel == level, modifier) { onSelect(level) }
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
    val focusBorderColor = if (error) Color(0xFFE53935) else PANEL_STROKE
    val borderStrokeColor = if (error) Color(0xFFE53935) else Color.White.copy(alpha = 0.08f)
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
            if (optional) {
                Text("Opcional", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
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
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
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
private fun TwoFieldRow(first: @Composable () -> Unit, second: @Composable () -> Unit) {
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
        // Do not manufacture a body value (70 kg/15%/40%) when the user has
        // no measurement. The canonical six-step wizard treats zero as an
        // incomplete field and asks for an explicit value.
        GoalMetric.WEIGHT -> weight.takeIf { it > 0.0 } ?: 0.0
        GoalMetric.BODY_FAT -> bodyFat?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        GoalMetric.MUSCLE_MASS -> muscle?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    }
}

private fun goalMetricLabel(metric: GoalMetric): String {
    return when (metric) {
        GoalMetric.WEIGHT -> "Peso corporal"
        GoalMetric.BODY_FAT -> "Grasa corporal"
        GoalMetric.MUSCLE_MASS -> "Masa muscular"
    }
}

private fun goalMetricUnit(metric: GoalMetric, weightUnit: WeightUnit = WeightUnit.KG): String {
    return when (metric) {
        GoalMetric.WEIGHT -> if (weightUnit == WeightUnit.LBS) "lb" else "kg"
        GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
    }
}

private fun formatGoalValue(metric: GoalMetric, value: Double, weightUnit: WeightUnit = WeightUnit.KG): String = when (metric) {
    GoalMetric.WEIGHT -> {
        val unitStr = if (weightUnit == WeightUnit.LBS) "lb" else "kg"
        "${formatGoalFieldValue(value)} $unitStr"
    }
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "${formatGoalFieldValue(value)}%"
}
