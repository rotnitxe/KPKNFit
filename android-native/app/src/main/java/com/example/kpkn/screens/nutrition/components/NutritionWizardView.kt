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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import java.time.Instant
import java.util.UUID

// ─── Constants ───────────────────────────────────────────────────────────────

private const val TOTAL_STEPS = 5

private val STEP_LABELS = listOf(
    "Meta",
    "Datos",
    "Composición",
    "Actividad",
    "Resumen",
)

private val METRIC_LABELS = mapOf(
    GoalMetric.WEIGHT to "Peso (kg)",
    GoalMetric.BODY_FAT to "% Grasa",
    GoalMetric.MUSCLE_MASS to "% Músculo",
)

private val DIRECTION_LABELS = mapOf(
    "lose" to Triple("Definición", "Reduciremos grasa de forma segura", CalorieGoal.LOSE),
    "maintain" to Triple("Mantención", "Mantendremos tu composición corporal", CalorieGoal.MAINTAIN),
    "gain" to Triple("Volumen limpio", "Subiremos masa muscular con control", CalorieGoal.GAIN),
)

private val DIET_OPTIONS = listOf(
    "omnivore" to "Omnívoro",
    "vegetarian" to "Vegetariano",
    "vegan" to "Vegano",
)

private val ACTIVITY_OPTIONS = listOf(
    1 to "Muy baja",
    2 to "Ligera",
    3 to "Moderada",
    4 to "Alta",
    5 to "Muy alta",
)

private val METABOLIC_OPTIONS = listOf(
    "Diabetes", "Resistencia a la insulina", "Hipotiroidismo",
    "Hipertiroidismo", "Síndrome metabólico",
)

// ─── Composable ──────────────────────────────────────────────────────────────

@Composable
fun NutritionWizardView(
    onComplete: (NutritionPlan) -> Unit,
    onSkip: () -> Unit,
    currentSettings: Settings = Settings(),
) {
    // ─── State ────────────────────────────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }

    // Step 0: Goal
    var primaryMetric by remember { mutableStateOf(GoalMetric.WEIGHT) }
    var primaryValue by remember { mutableStateOf(currentSettings.userVitals.weight?.toInt()?.toString() ?: "70") }
    var direction by remember { mutableStateOf("maintain") }

    // Step 1: Body data
    var age by remember { mutableStateOf(currentSettings.userVitals.age?.toString() ?: "30") }
    var height by remember { mutableStateOf(currentSettings.userVitals.height?.toInt()?.toString() ?: "170") }
    var weight by remember { mutableStateOf(currentSettings.userVitals.weight?.toInt()?.toString() ?: "70") }
    var gender by remember { mutableStateOf(currentSettings.userVitals.gender ?: Gender.MALE) }

    // Step 2: Body composition
    var bodyFat by remember { mutableStateOf(currentSettings.userVitals.bodyFatPercentage?.let { "%.1f".format(it) } ?: "") }
    var muscleMass by remember { mutableStateOf(currentSettings.userVitals.muscleMassPercentage?.let { "%.1f".format(it) } ?: "") }
    var bodyFatQuality by remember { mutableStateOf("estimated") }
    var muscleMassQuality by remember { mutableStateOf("estimated") }
    var compositionConfirmed by remember { mutableStateOf(false) }

    // Step 3: Activity & diet
    var activityLevel by remember { mutableIntStateOf(3) }
    var dietaryPreference by remember { mutableStateOf("omnivore") }
    var weeklyChangeKg by remember { mutableStateOf(0.5) }
    var metabolicConditions by remember { mutableStateOf(emptyList<String>()) }

    // Step 4: Summary
    var manualCalories by remember { mutableStateOf("") }
    var manualProtein by remember { mutableStateOf("") }
    var manualCarbs by remember { mutableStateOf("") }
    var manualFats by remember { mutableStateOf("") }
    var useManualOverrides by remember { mutableStateOf(false) }

    // ─── Derived ──────────────────────────────────────────────────────────────

    val weightD = weight.toDoubleOrNull() ?: 70.0
    val heightD = height.toDoubleOrNull() ?: 170.0
    val ageI = age.toIntOrNull() ?: 30
    val bodyFatD = bodyFat.toDoubleOrNull()
    val muscleMassD = muscleMass.toDoubleOrNull()
    val primaryValD = primaryValue.toDoubleOrNull() ?: 0.0

    val nutritionInput = NutritionInput(
        weightKg = weightD,
        heightCm = heightD,
        age = ageI,
        gender = gender,
        bodyFatPercentage = bodyFatD,
    )

    val calorieConfig = CalorieGoalConfig(
        formula = if (bodyFatD != null) FormulaType.KATCH else FormulaType.MIFFLIN,
        activityLevel = activityLevel,
        goal = DIRECTION_LABELS[direction]?.third ?: CalorieGoal.MAINTAIN,
        weeklyChangeKg = weeklyChangeKg,
    )

    val bmr = calculateBMR(nutritionInput, calorieConfig)
    val tdee = bmr?.let { kotlin.math.round(it * getActivityFactor(calorieConfig)).toInt() }
    val targetCalories = calculateDailyCalorieGoal(nutritionInput, calorieConfig)

    val dietMultiplier = when (dietaryPreference) {
        "vegan" -> 1.15
        "vegetarian" -> 1.08
        else -> 1.0
    }

    val recommendedProtein = kotlin.math.round(weightD * 2.0 * dietMultiplier).toInt()
    val recommendedFats = kotlin.math.max(45, kotlin.math.round(weightD * 0.75).toInt())
    val recommendedCarbs = kotlin.math.max(40, kotlin.math.round((targetCalories - recommendedProtein * 4 - recommendedFats * 9) / 4.0).toInt())

    val weeklyTrendKg = if (direction == "maintain") 0.0
    else if (direction == "lose") -weeklyChangeKg
    else weeklyChangeKg

    val ffmi = if (weightD > 0 && heightD > 0 && bodyFatD != null && bodyFatD > 0) {
        val lbm = weightD * (1 - bodyFatD / 100)
        (lbm / ((heightD / 100) * (heightD / 100)) * 10).toLong() / 10.0
    } else null

    // Risk flags
    val riskInput = RiskInput(
        settings = nutritionInput,
        calorieTarget = targetCalories,
        goalMetric = primaryMetric,
        goalValue = primaryValD,
        weeklyChangeKg = kotlin.math.abs(weeklyTrendKg),
    )
    val riskFlags = buildNutritionRiskFlags(riskInput)
    val hasHardStop = riskFlags.any { it.hardStop }

    // Estimated end date
    val estimatedEndDate = if (direction == "maintain") null
    else {
        val currentVal = weightD
        val deltaPerWeek = kotlin.math.abs(weeklyTrendKg)
        if (deltaPerWeek > 0) {
            val weeks = kotlin.math.abs(primaryValD - currentVal) / deltaPerWeek
            java.time.LocalDate.now().plusDays((weeks * 7).toLong().coerceAtMost(365)).toString()
        } else null
    }

    // Step validity
    val stepValid = when (step) {
        0 -> primaryValD > 0
        1 -> ageI > 0 && heightD > 0 && weightD > 0
        2 -> bodyFatD != null && muscleMassD != null && (compositionConfirmed || bodyFatD > 0)
        3 -> activityLevel in 1..5
        4 -> targetCalories > 0 && !hasHardStop
        else -> false
    }

    // ─── Layout ───────────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f))
                .padding(16.dp),
        ) {
            Text(
                text = "Configurar plan de alimentación",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.18f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Asistente de plan nutricional",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (0 until TOTAL_STEPS).forEach { i ->
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        i < step -> MaterialTheme.colorScheme.primary
                                        i == step -> Color(0xFF006A6A).copy(alpha = 0.5f)
                                        else -> Color(0xFF000000).copy(alpha = 0.12f)
                                    }
                                ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = STEP_LABELS[i],
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (0.16f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Content ──────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Step 0: Goal ──────────────────────────────────────────────────
            if (step == 0) {
                item {
                    Section("Meta principal") {
                        METRIC_LABELS.forEach { (metric, label) ->
                            val selected = primaryMetric == metric
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { primaryMetric = metric },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) Color(0xFF006A6A).copy(alpha = 0.1f) else Color.Transparent,
                                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF006A6A).copy(alpha = 0.4f))
                                else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(label, fontWeight = FontWeight.ExtraBold)
                                    Text(
                                        "Objetivo en ${if (metric == GoalMetric.WEIGHT) "kg" else "%"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Objetivo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        OutlinedTextField(
                            value = primaryValue,
                            onValueChange = { primaryValue = it.filter { c -> c.isDigit() || c == '.' } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                item {
                    Section("Dirección del plan") {
                        DIRECTION_LABELS.forEach { (dir, triple) ->
                            val (title, subtitle, _) = triple
                            val selected = direction == dir
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { direction = dir },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) Color(0xFF006A6A).copy(alpha = 0.1f) else Color.Transparent,
                                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF006A6A).copy(alpha = 0.4f))
                                else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(title, fontWeight = FontWeight.ExtraBold)
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // ── Step 1: Body Data ─────────────────────────────────────────────
            if (step == 1) {
                item {
                    Section("Datos base obligatorios") {
                        LabeledInput("Edad", age) { age = it.filter { c -> c.isDigit() } }
                        LabeledInput("Estatura (cm)", height) { height = it.filter { c -> c.isDigit() } }
                        LabeledInput("Peso (kg)", weight) { weight = it.filter { c -> c.isDigit() || c == '.' } }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Sexo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GenderChip("Hombre", gender == Gender.MALE) { gender = Gender.MALE }
                            GenderChip("Mujer", gender == Gender.FEMALE) { gender = Gender.FEMALE }
                            GenderChip("Otro", gender == Gender.OTHER) { gender = Gender.OTHER }
                        }
                    }
                }
            }

            // ── Step 2: Body Composition ───────────────────────────────────────
            if (step == 2) {
                item {
                    Section("Composición corporal") {
                        Text("% Grasa corporal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        OutlinedTextField(
                            value = bodyFat,
                            onValueChange = {
                                bodyFat = it.filter { c -> c.isDigit() || c == '.' }
                                compositionConfirmed = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QualityChip("Medido", bodyFatQuality == "measured") { bodyFatQuality = "measured" }
                            QualityChip("Estimado", bodyFatQuality == "estimated") { bodyFatQuality = "estimated" }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("% Masa muscular", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        OutlinedTextField(
                            value = muscleMass,
                            onValueChange = {
                                muscleMass = it.filter { c -> c.isDigit() || c == '.' }
                                compositionConfirmed = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QualityChip("Medido", muscleMassQuality == "measured") { muscleMassQuality = "measured" }
                            QualityChip("Estimado", muscleMassQuality == "estimated") { muscleMassQuality = "estimated" }
                        }

                        if (ffmi != null && ffmi > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "FFMI: %.1f".format(ffmi),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (!compositionConfirmed && bodyFat.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF7D5700).copy(alpha = 0.1f)),
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Te sugerimos una estimación inicial. Revísala y confírmala antes de continuar.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { compositionConfirmed = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7D5700).copy(alpha = 0.2f)),
                                    ) {
                                        Text("CONFIRMAR ESTIMACIÓN", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Step 3: Activity & Diet ────────────────────────────────────────
            if (step == 3) {
                item {
                    Section("Nivel de actividad") {
                        ACTIVITY_OPTIONS.forEach { (level, label) ->
                            val selected = activityLevel == level
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activityLevel = level },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) Color(0xFF006A6A).copy(alpha = 0.1f) else Color.Transparent,
                                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF006A6A).copy(alpha = 0.4f))
                                else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(12.dp),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (0.12f).sp,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                item {
                    Section("Preferencia alimentaria") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DIET_OPTIONS.forEach { (id, label) ->
                                val selected = dietaryPreference == id
                                Surface(
                                    modifier = Modifier.clickable { dietaryPreference = id },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onTertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Section("Ritmo de cambio semanal") {
                        Text("Cambio semanal (kg)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Slider(
                            value = weeklyChangeKg.toFloat(),
                            onValueChange = { weeklyChangeKg = it.toDouble() },
                            valueRange = 0.1f..2.0f,
                            steps = 37,
                        )
                        Text(
                            text = "%.2f kg/sem".format(weeklyChangeKg),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Section("Condiciones metabólicas (opcional)") {
                        METABOLIC_OPTIONS.forEach { condition ->
                            val active = condition in metabolicConditions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        metabolicConditions = if (active) metabolicConditions - condition
                                        else metabolicConditions + condition
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = active,
                                    onCheckedChange = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(condition, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Step 4: Summary ────────────────────────────────────────────────
            if (step == 4) {
                item {
                    Section("Resumen final") {
                        SummaryCard("Calorías objetivo", "$targetCalories kcal")
                        Spacer(modifier = Modifier.height(8.dp))
                        SummaryCard("Macros diarios", "P ${recommendedProtein}g · C ${recommendedCarbs}g · G ${recommendedFats}g")
                        Spacer(modifier = Modifier.height(8.dp))
                        if (bmr != null) SummaryCard("TMB", "${kotlin.math.round(bmr)} kcal")
                        if (tdee != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SummaryCard("TDEE", "$tdee kcal")
                        }
                        if (estimatedEndDate != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SummaryCard("Fecha estimada", estimatedEndDate)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = useManualOverrides, onCheckedChange = { useManualOverrides = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajustar manualmente", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }

                        if (useManualOverrides) {
                            LabeledInput("Calorías", manualCalories) { manualCalories = it.filter { c -> c.isDigit() } }
                            LabeledInput("Proteína (g)", manualProtein) { manualProtein = it.filter { c -> c.isDigit() } }
                            LabeledInput("Carbohidratos (g)", manualCarbs) { manualCarbs = it.filter { c -> c.isDigit() } }
                            LabeledInput("Grasas (g)", manualFats) { manualFats = it.filter { c -> c.isDigit() } }
                        }
                    }
                }

                // Risk flags
                if (riskFlags.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            riskFlags.forEach { flag ->
                                val bgColor = when (flag.severity) {
                                    RiskSeverity.DANGER -> Color(0xFFB3261E).copy(alpha = 0.15f)
                                    RiskSeverity.WARNING -> Color(0xFFE6A200).copy(alpha = 0.15f)
                                    RiskSeverity.INFO -> MaterialTheme.colorScheme.surfaceContainer
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
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        // ── Footer ─────────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("ATRÁS", fontWeight = FontWeight.Black)
                    }
                } else {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Explorar sin plan")
                    }
                }

                Button(
                    onClick = {
                        if (step < TOTAL_STEPS - 1) {
                            step++
                        } else {
                            // Build final plan
                            val finalCalories = manualCalories.toIntOrNull() ?: targetCalories
                            val finalProtein = manualProtein.toIntOrNull() ?: recommendedProtein
                            val finalCarbs = manualCarbs.toIntOrNull() ?: recommendedCarbs
                            val finalFats = manualFats.toIntOrNull() ?: recommendedFats

                            val plan = NutritionPlan(
                                id = UUID.randomUUID().toString(),
                                name = "${METRIC_LABELS[primaryMetric]} ${primaryValue}",
                                goalType = primaryMetric,
                                goalValue = primaryValD,
                                calorieTarget = finalCalories,
                                proteinGoal = finalProtein,
                                carbGoal = finalCarbs,
                                fatGoal = finalFats,
                                isActive = true,
                                createdAt = Instant.now().toString(),
                                primaryGoal = NutritionGoal(
                                    metric = primaryMetric,
                                    value = primaryValD,
                                    label = METRIC_LABELS[primaryMetric] ?: "",
                                    unit = if (primaryMetric == GoalMetric.WEIGHT) "kg" else "%",
                                    priority = "primary",
                                ),
                                estimatedEndDate = estimatedEndDate,
                                weeklyChangeKg = weeklyChangeKg,
                            )
                            onComplete(plan)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = stepValid,
                ) {
                    Text(
                        if (step < TOTAL_STEPS - 1) "SIGUIENTE" else "CREAR PLAN",
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.16f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LabeledInput(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.14f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun GenderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF7D5700).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Color(0xFF6B4F00) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
