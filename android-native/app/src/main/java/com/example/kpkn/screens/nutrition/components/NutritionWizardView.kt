package com.example.kpkn.screens.nutrition.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import java.time.Instant
import java.util.UUID

// ═══════════════════════════════════════════════════════════════════════
// NUTRITION WIZARD — Full-screen onboarding + plan creation
// ═══════════════════════════════════════════════════════════════════════

private const val TOTAL_DATA_STEPS = 5

private val STEP_LABELS = listOf("Meta", "Datos", "Composición", "Actividad", "Resumen")

private val METRIC_OPTIONS = listOf(
    Triple(GoalMetric.WEIGHT, "Peso corporal", "Trabaja hacia tu peso ideal en kg"),
    Triple(GoalMetric.BODY_FAT, "Porcentaje de grasa", "Reduce o controla tu % de grasa"),
    Triple(GoalMetric.MUSCLE_MASS, "Masa muscular", "Aumenta tu % de masa muscular"),
)

private val DIRECTION_OPTIONS = listOf(
    Triple("lose", "Definición", "Reducir grasa de forma progresiva y segura, conservando masa muscular"),
    Triple("maintain", "Mantención", "Mantener tu composición actual con las calorías justas"),
    Triple("gain", "Volumen limpio", "Ganar masa muscular con un superávit calórico controlado"),
)

private val DIRECTION_ICONS = mapOf(
    "lose" to Icons.Default.TrendingDown,
    "maintain" to Icons.Default.Balance,
    "gain" to Icons.Default.TrendingUp,
)

private val DIRECTION_COLORS = mapOf(
    "lose" to Color(0xFFE53935),
    "maintain" to Color(0xFF1E88E5),
    "gain" to Color(0xFF43A047),
)

private val DIET_OPTIONS = listOf(
    Triple("omnivore", "Omnívoro", "Incluye todo tipo de alimentos"),
    Triple("vegetarian", "Vegetariano", "Sin carne, con lácteos y huevos"),
    Triple("vegan", "Vegano", "100% basado en plantas"),
)

private val ACTIVITY_LEVELS = listOf(
    Triple(1, "Sedentario", "Trabajo de escritorio, poco movimiento"),
    Triple(2, "Ligera", "Caminas frecuentemente, actividad ligera"),
    Triple(3, "Moderada", "Entrenas 3-4 veces/semana"),
    Triple(4, "Alta", "Entrenas 5-6 veces/semana con intensidad"),
    Triple(5, "Muy alta", "Atleta, entrenamientos diarios intensos"),
)

private val METABOLIC_OPTIONS = listOf(
    "Diabetes", "Resistencia a la insulina", "Hipotiroidismo",
    "Hipertiroidismo", "Síndrome metabólico",
)

// ═══════════════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun NutritionWizardView(
    onComplete: (NutritionPlan) -> Unit,
    onSkip: () -> Unit,
    currentSettings: Settings = Settings(),
) {
    // -1 = welcome screen, 0..4 = data steps
    var step by remember { mutableIntStateOf(-1) }

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
    var weeklyChangeKg by remember { mutableStateOf(0.45) }
    var metabolicConditions by remember { mutableStateOf(emptyList<String>()) }

    // Step 4: Summary
    var manualCalories by remember { mutableStateOf("") }
    var manualProtein by remember { mutableStateOf("") }
    var manualCarbs by remember { mutableStateOf("") }
    var manualFats by remember { mutableStateOf("") }
    var useManualOverrides by remember { mutableStateOf(false) }

    // ─── Derived ─────────────────────────────────────────────────────────
    val weightD = weight.toDoubleOrNull() ?: 70.0
    val heightD = height.toDoubleOrNull() ?: 170.0
    val ageI = age.toIntOrNull() ?: 30
    val bodyFatD = bodyFat.toDoubleOrNull()
    val muscleMassD = muscleMass.toDoubleOrNull()
    val primaryValD = primaryValue.toDoubleOrNull() ?: 0.0

    val nutritionInput = NutritionInput(
        weightKg = weightD, heightCm = heightD, age = ageI,
        gender = gender, bodyFatPercentage = bodyFatD,
    )
    val goalDirection = when (direction) {
        "lose" -> CalorieGoal.LOSE; "gain" -> CalorieGoal.GAIN; else -> CalorieGoal.MAINTAIN
    }
    val calorieConfig = CalorieGoalConfig(
        formula = if (bodyFatD != null) FormulaType.KATCH else FormulaType.MIFFLIN,
        activityLevel = activityLevel,
        goal = goalDirection,
        weeklyChangeKg = weeklyChangeKg,
    )
    val bmr = calculateBMR(nutritionInput, calorieConfig)
    val tdee = bmr?.let { kotlin.math.round(it * getActivityFactor(calorieConfig)).toInt() }
    val targetCalories = calculateDailyCalorieGoal(nutritionInput, calorieConfig)

    val dietMultiplier = when (dietaryPreference) {
        "vegan" -> 1.15; "vegetarian" -> 1.08; else -> 1.0
    }
    val recommendedProtein = kotlin.math.round(weightD * 2.0 * dietMultiplier).toInt()
    val recommendedFats = kotlin.math.max(45, kotlin.math.round(weightD * 0.75).toInt())
    val recommendedCarbs = kotlin.math.max(40, kotlin.math.round((targetCalories - recommendedProtein * 4 - recommendedFats * 9) / 4.0).toInt())

    val weeklyTrendKg = when (direction) {
        "lose" -> -weeklyChangeKg; "gain" -> weeklyChangeKg; else -> 0.0
    }

    val ffmi = if (weightD > 0 && heightD > 0 && bodyFatD != null && bodyFatD > 0) {
        val lbm = weightD * (1 - bodyFatD / 100)
        (lbm / ((heightD / 100) * (heightD / 100)) * 10).toLong() / 10.0
    } else null

    val riskInput = RiskInput(
        settings = nutritionInput, calorieTarget = targetCalories,
        goalMetric = primaryMetric, goalValue = primaryValD,
        weeklyChangeKg = kotlin.math.abs(weeklyTrendKg),
    )
    val riskFlags = buildNutritionRiskFlags(riskInput)
    val hasHardStop = riskFlags.any { it.hardStop }

    val estimatedEndDate = if (direction == "maintain") null else {
        val deltaPerWeek = kotlin.math.abs(weeklyTrendKg)
        if (deltaPerWeek > 0) {
            val weeks = kotlin.math.abs(primaryValD - weightD) / deltaPerWeek
            java.time.LocalDate.now().plusDays((weeks * 7).toLong().coerceAtMost(365)).toString()
        } else null
    }

    val stepValid = when (step) {
        -1 -> true  // welcome always valid
        0 -> primaryValD > 0
        1 -> ageI in 10..100 && heightD in 120.0..250.0 && weightD in 30.0..300.0
        2 -> compositionConfirmed || (bodyFatD != null && bodyFatD > 0 && muscleMassD != null && muscleMassD > 0)
        3 -> activityLevel in 1..5
        4 -> targetCalories > 0 && !hasHardStop
        else -> false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LAYOUT
    // ═══════════════════════════════════════════════════════════════════════

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── WELCOME SCREEN ──────────────────────────────────────────
            if (step == -1) {
                WelcomeScreen(
                    onStart = { step = 0 },
                    onSkip = onSkip,
                )
                return@Column
            }

            // ─── STEP HEADER ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                Color.Transparent,
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (step > 0) step-- else step = -1 }) {
                        Icon(Icons.Default.ArrowBack, "Atrás", modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Paso ${step + 1} de $TOTAL_DATA_STEPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            STEP_LABELS[step],
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    // Step dots
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (0 until TOTAL_DATA_STEPS).forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == step) 24.dp else 8.dp, 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            i < step -> MaterialTheme.colorScheme.primary
                                            i == step -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                            )
                        }
                    }
                }
            }

            // ─── STEP CONTENT ────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            ) {
                when (step) {
                    0 -> {
                        // ─── STEP 0: Goal ────────────────────────────────
                        item {
                            WizardSection(
                                "¿Qué quieres lograr?",
                                "Elige la métrica principal que guiará tu plan nutricional.",
                                Icons.Default.Flag,
                            ) {
                                METRIC_OPTIONS.forEach { (metric, title, desc) ->
                                    SelectableCard(
                                        title = title,
                                        subtitle = desc,
                                        selected = primaryMetric == metric,
                                        onClick = { primaryMetric = metric },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }

                                Spacer(Modifier.height(16.dp))

                                Text("¿Cuánto quieres alcanzar?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = primaryValue,
                                    onValueChange = { primaryValue = it.filter { c -> c.isDigit() || c == '.' } },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(if (primaryMetric == GoalMetric.WEIGHT) "Peso objetivo (kg)" else "% objetivo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(14.dp),
                                )
                            }
                        }

                        item {
                            WizardSection(
                                "Dirección del plan",
                                "Define si buscas perder, mantener o ganar.",
                                Icons.Default.SwapVert,
                            ) {
                                DIRECTION_OPTIONS.forEach { (dir, title, desc) ->
                                    val color = DIRECTION_COLORS[dir] ?: Color.Gray
                                    val icon = DIRECTION_ICONS[dir] ?: Icons.Default.ArrowForward
                                    SelectableCard(
                                        title = title,
                                        subtitle = desc,
                                        selected = direction == dir,
                                        onClick = { direction = dir },
                                        leadingIcon = icon,
                                        accentColor = color,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }

                    1 -> {
                        // ─── STEP 1: Body data ───────────────────────────
                        item {
                            WizardSection(
                                "Tus datos corporales",
                                "Estos datos son esenciales para calcular tu metabolismo basal y gasto calórico.",
                                Icons.Default.Person,
                            ) {
                                WizardInput("Edad", age, "años", "10 – 100") { age = it.filter { c -> c.isDigit() } }
                                WizardInput("Estatura", height, "cm", "120 – 250") { height = it.filter { c -> c.isDigit() } }
                                WizardInput("Peso actual", weight, "kg", "30 – 300") { weight = it.filter { c -> c.isDigit() || c == '.' } }

                                Spacer(Modifier.height(12.dp))
                                Text("Sexo biológico", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Necesario para el cálculo hormonal del metabolismo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GenderOption("Hombre", Icons.Default.Male, gender == Gender.MALE) { gender = Gender.MALE }
                                    GenderOption("Mujer", Icons.Default.Female, gender == Gender.FEMALE) { gender = Gender.FEMALE }
                                    GenderOption("Otro", Icons.Default.Person, gender == Gender.OTHER) { gender = Gender.OTHER }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ─── STEP 2: Body composition ────────────────────
                        item {
                            WizardSection(
                                "Composición corporal",
                                "Si no tienes datos exactos, puedes dejarlo en blanco y te estimaremos valores iniciales.",
                                Icons.Default.MonitorWeight,
                            ) {
                                WizardInput("% Grasa corporal", bodyFat, "%", "4 – 55") {
                                    bodyFat = it.filter { c -> c.isDigit() || c == '.' }
                                    compositionConfirmed = true
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QualityTag("Medido", bodyFatQuality == "measured") { bodyFatQuality = "measured" }
                                    QualityTag("Estimado", bodyFatQuality == "estimated") { bodyFatQuality = "estimated" }
                                }

                                Spacer(Modifier.height(12.dp))

                                WizardInput("% Masa muscular", muscleMass, "%", "20 – 60") {
                                    muscleMass = it.filter { c -> c.isDigit() || c == '.' }
                                    compositionConfirmed = true
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QualityTag("Medido", muscleMassQuality == "measured") { muscleMassQuality = "measured" }
                                    QualityTag("Estimado", muscleMassQuality == "estimated") { muscleMassQuality = "estimated" }
                                }

                                if (ffmi != null && ffmi > 0) {
                                    Spacer(Modifier.height(16.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text("FFMI estimado", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                                Text("%.1f".format(ffmi), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }

                                if (!compositionConfirmed) {
                                    Spacer(Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFF8F00).copy(alpha = 0.08f),
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                if (bodyFat.isEmpty() && muscleMass.isEmpty())
                                                    "Si no conoces estos datos puedes omitirlos. Usaremos estimaciones seguras."
                                                else
                                                    "Revisa los valores ingresados y confirma para continuar.",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    compositionConfirmed = true
                                                    // Auto-fill defaults if empty
                                                    if (bodyFat.isEmpty()) bodyFat = if (gender == Gender.FEMALE) "25.0" else "18.0"
                                                    if (muscleMass.isEmpty()) muscleMass = if (gender == Gender.FEMALE) "32.0" else "40.0"
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                                            ) {
                                                Text(
                                                    if (bodyFat.isEmpty() && muscleMass.isEmpty()) "OMITIR Y ESTIMAR" else "CONFIRMAR VALORES",
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // ─── STEP 3: Activity & diet ─────────────────────
                        item {
                            WizardSection(
                                "Nivel de actividad física",
                                "¿Cuánto te mueves en tu día a día y cuánto entrenas?",
                                Icons.Default.DirectionsRun,
                            ) {
                                ACTIVITY_LEVELS.forEach { (level, title, desc) ->
                                    SelectableCard(
                                        title = title,
                                        subtitle = desc,
                                        selected = activityLevel == level,
                                        onClick = { activityLevel = level },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }

                        item {
                            WizardSection(
                                "Preferencia alimentaria",
                                "Tu estilo de alimentación afecta la distribución de macronutrientes.",
                                Icons.Default.Restaurant,
                            ) {
                                DIET_OPTIONS.forEach { (id, title, desc) ->
                                    SelectableCard(
                                        title = title,
                                        subtitle = desc,
                                        selected = dietaryPreference == id,
                                        onClick = { dietaryPreference = id },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }

                        item {
                            WizardSection(
                                "Ritmo de cambio semanal",
                                "Un ritmo seguro está entre 0.25 – 0.75 kg/semana para la mayoría.",
                                Icons.Default.Speed,
                            ) {
                                Text(
                                    "${"%.2f".format(weeklyChangeKg)} kg / semana",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = when {
                                        weeklyChangeKg <= 0.5 -> Color(0xFF43A047)
                                        weeklyChangeKg <= 1.0 -> Color(0xFFFF8F00)
                                        else -> Color(0xFFE53935)
                                    },
                                )
                                Slider(
                                    value = weeklyChangeKg.toFloat(),
                                    onValueChange = { weeklyChangeKg = it.toDouble() },
                                    valueRange = 0.1f..2.0f,
                                    steps = 37,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Lento", style = MaterialTheme.typography.labelSmall, color = Color(0xFF43A047))
                                    Text("Agresivo", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935))
                                }
                            }
                        }

                        item {
                            WizardSection(
                                "Condiciones metabólicas",
                                "Opcional. Informar condiciones ayuda a ajustar las recomendaciones de seguridad.",
                                Icons.Default.HealthAndSafety,
                            ) {
                                METABOLIC_OPTIONS.forEach { condition ->
                                    val active = condition in metabolicConditions
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                metabolicConditions = if (active) metabolicConditions - condition
                                                else metabolicConditions + condition
                                            }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = active, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(condition, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // ─── STEP 4: Summary ─────────────────────────────
                        item {
                            WizardSection(
                                "Tu plan calculado",
                                "Basado en tus datos, tu metabolismo y tus objetivos.",
                                Icons.Default.Calculate,
                            ) {
                                // Main KPI cards
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    SummaryKpi("Calorías", "$targetCalories", "kcal", Color(0xFF49454F), Modifier.weight(1f))
                                    SummaryKpi("Proteína", "$recommendedProtein", "g", Color(0xFFB3261E), Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    SummaryKpi("Carbohidratos", "$recommendedCarbs", "g", Color(0xFF6750A4), Modifier.weight(1f))
                                    SummaryKpi("Grasas", "$recommendedFats", "g", Color(0xFF006A6A), Modifier.weight(1f))
                                }

                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Spacer(Modifier.height(12.dp))

                                // Technical info
                                if (bmr != null) {
                                    InfoRow("TMB (Metabolismo Basal)", "${kotlin.math.round(bmr)} kcal")
                                }
                                if (tdee != null) {
                                    InfoRow("TDEE (Gasto Total)", "$tdee kcal")
                                }
                                val deficit = if (tdee != null) targetCalories - tdee else null
                                if (deficit != null && deficit != 0) {
                                    InfoRow(
                                        if (deficit < 0) "Déficit calórico" else "Superávit calórico",
                                        "${kotlin.math.abs(deficit)} kcal/día",
                                    )
                                }
                                if (estimatedEndDate != null) {
                                    InfoRow("Fecha estimada", try {
                                        java.time.LocalDate.parse(estimatedEndDate)
                                            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("es")))
                                    } catch (_: Exception) { estimatedEndDate })
                                }

                                Spacer(Modifier.height(16.dp))

                                // Manual overrides
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { useManualOverrides = !useManualOverrides },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text("Ajustar macros manualmente", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Icon(
                                            if (useManualOverrides) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            null, modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                if (useManualOverrides) {
                                    Spacer(Modifier.height(12.dp))
                                    WizardInput("Calorías", manualCalories, "kcal") { manualCalories = it.filter { c -> c.isDigit() } }
                                    WizardInput("Proteína", manualProtein, "g") { manualProtein = it.filter { c -> c.isDigit() } }
                                    WizardInput("Carbohidratos", manualCarbs, "g") { manualCarbs = it.filter { c -> c.isDigit() } }
                                    WizardInput("Grasas", manualFats, "g") { manualFats = it.filter { c -> c.isDigit() } }
                                }
                            }
                        }

                        // Risk flags
                        if (riskFlags.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    riskFlags.forEach { flag ->
                                        val (bg, icon, iconTint) = when (flag.severity) {
                                            RiskSeverity.DANGER -> Triple(Color(0xFFB3261E).copy(alpha = 0.12f), Icons.Default.Block, Color(0xFFB3261E))
                                            RiskSeverity.WARNING -> Triple(Color(0xFFFF8F00).copy(alpha = 0.12f), Icons.Default.Warning, Color(0xFFFF8F00))
                                            RiskSeverity.INFO -> Triple(MaterialTheme.colorScheme.surfaceContainer, Icons.Default.Info, MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Surface(shape = RoundedCornerShape(12.dp), color = bg) {
                                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(icon, null, modifier = Modifier.size(18.dp), tint = iconTint)
                                                Spacer(Modifier.width(10.dp))
                                                Text(flag.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── FOOTER ──────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ATRÁS", fontWeight = FontWeight.Black)
                        }
                    } else {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("Explorar sin plan", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            if (step < TOTAL_DATA_STEPS - 1) {
                                step++
                            } else {
                                val finalCalories = manualCalories.toIntOrNull() ?: targetCalories
                                val finalProtein = manualProtein.toIntOrNull() ?: recommendedProtein
                                val finalCarbs = manualCarbs.toIntOrNull() ?: recommendedCarbs
                                val finalFats = manualFats.toIntOrNull() ?: recommendedFats

                                val plan = NutritionPlan(
                                    id = UUID.randomUUID().toString(),
                                    name = "${when (primaryMetric) { GoalMetric.WEIGHT -> "Peso"; GoalMetric.BODY_FAT -> "Grasa"; GoalMetric.MUSCLE_MASS -> "Músculo" }} $primaryValue",
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
                                        label = when (primaryMetric) { GoalMetric.WEIGHT -> "Peso (kg)"; GoalMetric.BODY_FAT -> "% Grasa"; GoalMetric.MUSCLE_MASS -> "% Músculo" },
                                        unit = if (primaryMetric == GoalMetric.WEIGHT) "kg" else "%",
                                        priority = "primary",
                                    ),
                                    estimatedEndDate = estimatedEndDate,
                                    weeklyChangeKg = weeklyChangeKg,
                                )
                                onComplete(plan)
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = stepValid,
                    ) {
                        Text(
                            if (step < TOTAL_DATA_STEPS - 1) "SIGUIENTE" else "CREAR PLAN",
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (step < TOTAL_DATA_STEPS - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                            null, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// WELCOME SCREEN
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF006A6A).copy(alpha = 0.06f),
                        MaterialTheme.colorScheme.surface,
                        Color(0xFF006A6A).copy(alpha = 0.03f),
                    )
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero icon
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = Color(0xFF006A6A).copy(alpha = 0.1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Restaurant,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF006A6A),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Nutrición KPKN",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Tu plan nutricional personalizado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF006A6A),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Vamos a calcular tus necesidades calóricas y de macronutrientes basándonos en tu cuerpo, tus objetivos y tu nivel de actividad.\n\nEl proceso toma menos de 2 minutos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(16.dp))

            // Feature list
            val features = listOf(
                Icons.Default.Calculate to "Cálculo de calorías y macros basado en ciencia",
                Icons.Default.TrendingUp to "Seguimiento de progreso con gráficos",
                Icons.Default.Restaurant to "Registro de comidas rápido e inteligente",
                Icons.Default.Insights to "Métricas corporales: FFMI, IMC, % grasa",
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                features.forEach { (icon, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF006A6A).copy(alpha = 0.08f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color(0xFF006A6A))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006A6A)),
            ) {
                Text("EMPEZAR", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null, tint = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onSkip) {
                Text(
                    "Explorar sin plan por ahora",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// REUSABLE WIZARD COMPONENTS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun WizardSection(
    title: String,
    description: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val containerColor = if (selected) accentColor.copy(alpha = 0.08f) else Color.Transparent
    val borderColor = if (selected) accentColor.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, modifier = Modifier.size(20.dp), tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(22.dp), tint = accentColor)
            }
        }
    }
}

@Composable
private fun WizardInput(
    label: String,
    value: String,
    unit: String,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            suffix = { Text(unit, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }} else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun GenderOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualityTag(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFFF8F00).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Color(0xFF6B4F00) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryKpi(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = color)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = color)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
