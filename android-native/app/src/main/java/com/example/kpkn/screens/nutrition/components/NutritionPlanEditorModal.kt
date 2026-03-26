package com.example.kpkn.screens.nutrition.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*

// ─── Constants ───────────────────────────────────────────────────────────────

private val ACTIVITY_FACTORS = mapOf(1 to 1.2, 2 to 1.375, 3 to 1.55, 4 to 1.725, 5 to 1.9)

private val KCAL_PER_GRAM = mapOf("protein" to 4, "carbs" to 4, "fat" to 9)

private data class EditorState(
    val goal: CalorieGoal = CalorieGoal.MAINTAIN,
    val connectAuge: Boolean = true,
    val height: String = "170",
    val weight: String = "70",
    val age: String = "30",
    val gender: Gender = Gender.MALE,
    val bodyFat: String = "",
    val muscleMass: String = "",
    val activityLevel: Int = 3,
    val dietaryPreference: String = "omnivore",
    val formula: FormulaType = FormulaType.MIFFLIN,
    val weeklyChangeKg: Double = 0.5,
    val healthMultiplier: Double = 1.0,
    val proteinG: String = "150",
    val carbsG: String = "250",
    val fatsG: String = "70",
)

// ─── Composable ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionPlanEditorModal(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSave: (NutritionPlan) -> Unit,
    currentSettings: Settings = Settings(),
) {
    if (!isOpen) return

    var state by remember {
        mutableStateOf(EditorState(
            goal = when (currentSettings.calorieGoalObjective) {
                CalorieGoalObjective.DEFICIT -> CalorieGoal.LOSE
                CalorieGoalObjective.SURPLUS -> CalorieGoal.GAIN
                else -> CalorieGoal.MAINTAIN
            },
            height = currentSettings.userVitals.height?.toInt()?.toString() ?: "170",
            weight = currentSettings.userVitals.weight?.toInt()?.toString() ?: "70",
            age = currentSettings.age?.toString() ?: "30",
            gender = currentSettings.userVitals.gender ?: Gender.MALE,
            bodyFat = currentSettings.userVitals.bodyFatPercentage?.let { "%.1f".format(it) } ?: "",
            muscleMass = currentSettings.userVitals.muscleMassPercentage?.let { "%.1f".format(it) } ?: "",
            proteinG = currentSettings.dailyProteinGoal?.toString() ?: "150",
            carbsG = currentSettings.dailyCarbGoal?.toString() ?: "250",
            fatsG = currentSettings.dailyFatGoal?.toString() ?: "70",
        ))
    }

    // Derived calculations
    val weightD = state.weight.toDoubleOrNull() ?: 0.0
    val heightD = state.height.toDoubleOrNull() ?: 0.0
    val ageI = state.age.toIntOrNull() ?: 0
    val bodyFatD = state.bodyFat.toDoubleOrNull()
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
    val activityFactor = getActivityFactor(calorieConfig)
    val tdee = bmr?.let { kotlin.math.round(it * activityFactor).toInt() }
    val dietMultiplier = when (state.dietaryPreference) {
        "vegan" -> 1.15
        "vegetarian" -> 1.08
        else -> 1.0
    }
    val caloriesFromMacros = kotlin.math.round(
        proteinD * (KCAL_PER_GRAM["protein"] ?: 4) * dietMultiplier +
        carbsD * (KCAL_PER_GRAM["carbs"] ?: 4) +
        fatsD * (KCAL_PER_GRAM["fat"] ?: 9)
    )
    val weeklyTrendKg = if (tdee != null && tdee > 0) {
        ((caloriesFromMacros - tdee) * 7) / 7700.0
    } else null

    val ffmi = if (weightD > 0 && heightD > 0 && bodyFatD != null) {
        val lbm = weightD * (1 - bodyFatD / 100)
        lbm / ((heightD / 100) * (heightD / 100))
    } else null

    // ─── Sections state ──────────────────────────────────────────────────────
    var showBodyData by remember { mutableStateOf(true) }
    var showBioImpedance by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(true) }
    var showDiet by remember { mutableStateOf(false) }
    var showFormula by remember { mutableStateOf(false) }
    var showCalorieAdj by remember { mutableStateOf(false) }
    var showMacros by remember { mutableStateOf(true) }
    var showSummary by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item {
                Text(
                    text = "Editor de Plan de Alimentación",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            }

            // ── Goal Selector ────────────────────────────────────────────────
            item {
                SectionHeader("Objetivo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalOptionChip("Definición", state.goal == CalorieGoal.LOSE) {
                        state = state.copy(goal = CalorieGoal.LOSE)
                    }
                    GoalOptionChip("Mantención", state.goal == CalorieGoal.MAINTAIN) {
                        state = state.copy(goal = CalorieGoal.MAINTAIN)
                    }
                    GoalOptionChip("Superávit", state.goal == CalorieGoal.GAIN) {
                        state = state.copy(goal = CalorieGoal.GAIN)
                    }
                }
            }

            // ── Body Data ────────────────────────────────────────────────────
            item {
                SectionToggle("Datos corporales", showBodyData) { showBodyData = !showBodyData }
            }
            if (showBodyData) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Estatura (cm)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.height,
                                onValueChange = { state = state.copy(height = it.filter { c -> c.isDigit() }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Peso (kg)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.weight,
                                onValueChange = { state = state.copy(weight = it.filter { c -> c.isDigit() || c == '.' }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Edad", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.age,
                                onValueChange = { state = state.copy(age = it.filter { c -> c.isDigit() }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Género", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                GenderOptionChip("Hombre", state.gender == Gender.MALE) { state = state.copy(gender = Gender.MALE) }
                                GenderOptionChip("Mujer", state.gender == Gender.FEMALE) { state = state.copy(gender = Gender.FEMALE) }
                                GenderOptionChip("Otro", state.gender == Gender.OTHER) { state = state.copy(gender = Gender.OTHER) }
                            }
                        }
                    }
                }
            }

            // ── Bioimpedance ─────────────────────────────────────────────────
            item {
                SectionToggle("Bioimpedancia", showBioImpedance) { showBioImpedance = !showBioImpedance }
            }
            if (showBioImpedance) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("% Grasa", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.bodyFat,
                                onValueChange = { state = state.copy(bodyFat = it.filter { c -> c.isDigit() || c == '.' }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("% Músculo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.muscleMass,
                                onValueChange = { state = state.copy(muscleMass = it.filter { c -> c.isDigit() || c == '.' }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    if (ffmi != null && ffmi > 0) {
                        Text(
                            text = "FFMI: %.1f".format(ffmi),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ── Activity Level ───────────────────────────────────────────────
            item {
                SectionToggle("Nivel de actividad", showActivity) { showActivity = !showActivity }
            }
            if (showActivity) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActivityOptionChip("Sedentario", 1, state.activityLevel) { state = state.copy(activityLevel = it) }
                        ActivityOptionChip("Ligero", 2, state.activityLevel) { state = state.copy(activityLevel = it) }
                        ActivityOptionChip("Moderado", 3, state.activityLevel) { state = state.copy(activityLevel = it) }
                        ActivityOptionChip("Activo", 4, state.activityLevel) { state = state.copy(activityLevel = it) }
                        ActivityOptionChip("Muy activo", 5, state.activityLevel) { state = state.copy(activityLevel = it) }
                    }
                }
            }

            // ── Diet Preference ──────────────────────────────────────────────
            item {
                SectionToggle("Preferencia dietética", showDiet) { showDiet = !showDiet }
            }
            if (showDiet) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DietOptionChip("Omnívoro", "omnivore", state.dietaryPreference) { state = state.copy(dietaryPreference = it) }
                        DietOptionChip("Vegetariano", "vegetarian", state.dietaryPreference) { state = state.copy(dietaryPreference = it) }
                        DietOptionChip("Vegano", "vegan", state.dietaryPreference) { state = state.copy(dietaryPreference = it) }
                    }
                    if (state.dietaryPreference != "omnivore") {
                        Text(
                            text = "Multiplicador proteína: %.2fx".format(dietMultiplier),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── BMR Formula ──────────────────────────────────────────────────
            item {
                SectionToggle("Fórmula TMB", showFormula) { showFormula = !showFormula }
            }
            if (showFormula) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FormulaOptionChip("Mifflin-St Jeor", FormulaType.MIFFLIN, state.formula) { state = state.copy(formula = it) }
                        FormulaOptionChip("Harris-Benedict", FormulaType.HARRIS, state.formula) { state = state.copy(formula = it) }
                        FormulaOptionChip("Katch-McArdle", FormulaType.KATCH, state.formula) { state = state.copy(formula = it) }
                    }
                }
            }

            // ── Calorie Adjustments ──────────────────────────────────────────
            item {
                SectionToggle("Ajustes calóricos", showCalorieAdj) { showCalorieAdj = !showCalorieAdj }
            }
            if (showCalorieAdj) {
                item {
                    if (state.goal == CalorieGoal.LOSE || state.goal == CalorieGoal.GAIN) {
                        Text("Cambio semanal (kg)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0).forEach { v ->
                                val selected = state.weeklyChangeKg == v
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.clickable { state = state.copy(weeklyChangeKg = v) },
                                ) {
                                    Text(
                                        text = "$v",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text("Multiplicador salud (0.5–1.5)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Slider(
                        value = state.healthMultiplier.toFloat(),
                        onValueChange = { state = state.copy(healthMultiplier = it.toDouble()) },
                        valueRange = 0.5f..1.5f,
                        steps = 19,
                    )
                    Text(
                        text = "%.2f".format(state.healthMultiplier),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Macros ───────────────────────────────────────────────────────
            item {
                SectionToggle("Macros (g/día)", showMacros) { showMacros = !showMacros }
            }
            if (showMacros) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Proteínas", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.proteinG,
                                onValueChange = { state = state.copy(proteinG = it.filter { c -> c.isDigit() }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Carbohidratos", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.carbsG,
                                onValueChange = { state = state.copy(carbsG = it.filter { c -> c.isDigit() }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Grasas", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = state.fatsG,
                                onValueChange = { state = state.copy(fatsG = it.filter { c -> c.isDigit() }) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    Text(
                        text = "%d kcal/día (P×4 + C×4 + G×9)".format(caloriesFromMacros),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ── Summary ──────────────────────────────────────────────────────
            item {
                SectionToggle("Resumen calculado", showSummary) { showSummary = !showSummary }
            }
            if (showSummary) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SummaryRow("TMB", if (bmr != null) "${kotlin.math.round(bmr)} kcal" else "—")
                            SummaryRow("TDEE", if (tdee != null) "$tdee kcal" else "—")
                            SummaryRow(
                                "Tendencia",
                                if (weeklyTrendKg != null) {
                                    val sign = if (weeklyTrendKg >= 0) "+" else ""
                                    "$sign%.2f kg/sem".format(weeklyTrendKg)
                                } else "—"
                            )
                            SummaryRow("Calorías desde macros", "$caloriesFromMacros kcal")
                        }
                    }
                }
            }

            // ── Risk Flags ───────────────────────────────────────────────────
            item {
                val riskInput = RiskInput(
                    settings = nutritionInput,
                    calorieTarget = caloriesFromMacros.toInt(),
                    goalMetric = when (state.goal) {
                        CalorieGoal.LOSE -> GoalMetric.WEIGHT
                        CalorieGoal.GAIN -> GoalMetric.WEIGHT
                        else -> GoalMetric.WEIGHT
                    },
                    goalValue = weightD,
                    weeklyChangeKg = state.weeklyChangeKg,
                )
                val flags = buildNutritionRiskFlags(riskInput)
                if (flags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        flags.forEach { flag ->
                            val bgColor = when (flag.severity) {
                                RiskSeverity.DANGER -> Color(0xFFB3261E).copy(alpha = 0.15f)
                                RiskSeverity.WARNING -> Color(0xFFE6A200).copy(alpha = 0.15f)
                                RiskSeverity.INFO -> MaterialTheme.colorScheme.surfaceContainer
                            }
                            val iconColor = when (flag.severity) {
                                RiskSeverity.DANGER -> Color(0xFFB3261E)
                                RiskSeverity.WARNING -> Color(0xFFE6A200)
                                RiskSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bgColor),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        if (flag.hardStop) Icons.Default.Block else Icons.Default.Warning,
                                        null,
                                        tint = iconColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = flag.message,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Save Button ──────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val plan = NutritionPlan(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "Mi Plan",
                            goalType = when (state.goal) {
                                CalorieGoal.LOSE -> GoalMetric.WEIGHT
                                CalorieGoal.GAIN -> GoalMetric.WEIGHT
                                else -> GoalMetric.WEIGHT
                            },
                            goalValue = weightD,
                            calorieTarget = caloriesFromMacros.toInt(),
                            proteinGoal = (proteinD * dietMultiplier).toInt(),
                            carbGoal = carbsD.toInt(),
                            fatGoal = fatsD.toInt(),
                            isActive = true,
                            createdAt = java.time.Instant.now().toString(),
                            weeklyChangeKg = state.weeklyChangeKg,
                        )
                        onSave(plan)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GUARDAR PLAN", fontWeight = FontWeight.Black)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (0.1f).sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionToggle(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun GoalOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GenderOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityOptionChip(label: String, level: Int, selectedLevel: Int, onSelect: (Int) -> Unit) {
    val selected = level == selectedLevel
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable { onSelect(level) },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DietOptionChip(label: String, id: String, selectedId: String, onSelect: (String) -> Unit) {
    val selected = id == selectedId
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable { onSelect(id) },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onTertiary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FormulaOptionChip(label: String, id: FormulaType, selectedId: FormulaType, onSelect: (FormulaType) -> Unit) {
    val selected = id == selectedId
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFE6A200).copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6A200).copy(alpha = 0.5f)) else null,
        modifier = Modifier.clickable { onSelect(id) },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Color(0xFFE6A200) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
