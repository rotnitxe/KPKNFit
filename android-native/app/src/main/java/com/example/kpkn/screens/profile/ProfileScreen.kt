package com.example.kpkn.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProfileViewModel : ViewModel() {
    private val repository = ProgramRepository.getInstance()

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings())

    val performance: StateFlow<ProfilePerformanceUiState> = combine(
        repository.programs,
        repository.history,
        repository.settings,
    ) { programs, history, settings ->
        val starredNames = programs.flatMap { program ->
            program.macrocycles.flatMap { macro ->
                macro.blocks.flatMap { block ->
                    block.mesocycles.flatMap { meso ->
                        meso.weeks.flatMap { week ->
                            week.sessions.flatMap { session ->
                                (session.parts.flatMap { it.exercises }.ifEmpty { session.exercises })
                                    .filter { it.isStarTarget }
                                    .map { it.name.trim().lowercase() }
                            }
                        }
                    }
                }
            }
        }.filter { it.isNotBlank() }.distinct()
        val bestStarred = history.flatMap { it.completedExercises }
            .filter { it.exerciseName.trim().lowercase() in starredNames }
            .flatMap { exercise -> exercise.sets.map { set -> exercise.exerciseName to estimated1Rm(set.weight, set.reps) } }
            .maxByOrNull { it.second }
        fun bestFor(vararg names: String): Double = history.flatMap { it.completedExercises }
            .filter { exercise -> names.any { exercise.exerciseName.contains(it, ignoreCase = true) } }
            .flatMap { exercise -> exercise.sets.map { estimated1Rm(it.weight, it.reps) } }
            .maxOrNull() ?: 0.0
        val total = bestFor("sentadilla", "squat") + bestFor("press banca", "bench press") + bestFor("peso muerto", "deadlift")
        ProfilePerformanceUiState(
            starredTargets = starredNames.size,
            completedSessions = history.size,
            relativeStrength = settings.userVitals.weight?.takeIf { it > 0.0 }?.let { total / it } ?: 0.0,
            bestStarredName = bestStarred?.first,
            bestStarredRm = bestStarred?.second ?: 0.0,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ProfilePerformanceUiState())

    fun update(transform: (Settings) -> Settings) = repository.updateSettings(transform)
}

data class ProfilePerformanceUiState(
    val starredTargets: Int = 0,
    val completedSessions: Int = 0,
    val relativeStrength: Double = 0.0,
    val bestStarredName: String? = null,
    val bestStarredRm: Double = 0.0,
)

private fun estimated1Rm(weight: Double, reps: Int): Double =
    if (weight > 0.0 && reps > 0) weight * (36.0 / (37.0 - reps.coerceAtMost(36))) else 0.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel { ProfileViewModel() },
) {
    val settings by viewModel.settings.collectAsState()
    val performance by viewModel.performance.collectAsState()
    var editMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } },
                actions = { IconButton(onClick = { editMode = !editMode }) { Icon(Icons.Default.Edit, contentDescription = if (editMode) "Guardar" else "Editar") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            ProfileHero(settings, performance)
            Spacer(Modifier.height(24.dp))

            val weight = settings.userVitals.weight
            val height = settings.userVitals.height
            val age = settings.userVitals.age
            val bmi = if (weight != null && height != null && height > 0) (weight / ((height / 100.0) * (height / 100.0)) * 10).toLong() / 10.0 else null

            Text("DATOS CORPORALES", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatChip("Peso", weight?.let { "${it.toInt()} kg" } ?: "—")
                StatChip("Altura", height?.let { "${it.toInt()} cm" } ?: "—")
                StatChip("Edad", age?.let { "$it años" } ?: "—")
                StatChip("IMC", bmi?.toString() ?: "—")
            }
            Spacer(Modifier.height(24.dp))

            val bodyFat = settings.userVitals.bodyFatPercentage
            val muscle = settings.userVitals.muscleMassPercentage
            if (bodyFat != null || muscle != null) {
                ProfileSection("Composición corporal") {
                    bodyFat?.let { ProfileStatRow("Grasa corporal", "${it.toInt()}%") }
                    muscle?.let { ProfileStatRow("Masa muscular", "${it.toInt()}%") }
                }
                Spacer(Modifier.height(16.dp))
            }

            ProfileSection("Datos personales") {
                ProfileStatRow("Género", settings.userVitals.gender?.label() ?: "No especificado")
                ProfileStatRow("Objetivo calórico", settings.calorieGoalObjective.label())
                ProfileStatRow("Unidad de peso", settings.weightUnit.name)
                ProfileStatRow("Intensidad", settings.intensityMetric.name)
            }
            Spacer(Modifier.height(16.dp))

            val cal = settings.dailyCalorieGoal
            val prot = settings.dailyProteinGoal
            val carbs = settings.dailyCarbGoal
            val fat = settings.dailyFatGoal
            if (cal != null || prot != null) {
                ProfileSection("Objetivos nutricionales") {
                    cal?.let { ProfileStatRow("Calorías", "$it kcal") }
                    prot?.let { ProfileStatRow("Proteínas", "${it}g") }
                    carbs?.let { ProfileStatRow("Carbohidratos", "${it}g") }
                    fat?.let { ProfileStatRow("Grasas", "${it}g") }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (editMode) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Edición rápida", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text("Para editar tu perfil completo, ve a Ajustes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ProfileHero(settings: Settings, performance: ProfilePerformanceUiState) {
    val initials = settings.username.split(" ").take(2).map { it.firstOrNull()?.uppercaseChar() ?: ' ' }.joinToString("").ifEmpty { "U" }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF102A35), Color(0xFF253659), Color(0xFF171B2A))))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(74.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text(initials, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.username.ifBlank { "Usuario" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.13f)) {
                        Text(settings.athleteType.label(), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.88f))
                    }
                }
            }
            Text("PERFIL DE RENDIMIENTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, color = Color(0xFF9ED5D4))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMetric("OBJETIVOS", performance.starredTargets.toString())
                HeroMetric("SESIONES", performance.completedSessions.toString())
                HeroMetric("FUERZA REL.", if (performance.relativeStrength > 0.0) String.format("%.2f×", performance.relativeStrength) else "—")
            }
            val best = performance.bestStarredName
            if (best != null) Text("Mejor marca destacada · $best  ${performance.bestStarredRm.toInt()} kg estimados", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f), maxLines = 1)
            else Text("Marca ejercicios con estrella para ver tus PR aquí.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.68f))
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.62f))
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth(), content = content) }
}

@Composable
private fun ProfileStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

private fun AthleteType.label() = when (this) {
    AthleteType.ENTHUSIAST -> "Entusiasta"
    AthleteType.POWERLIFTER -> "Powerlifter"
    AthleteType.BODYBUILDER -> "Bodybuilder"
    AthleteType.POWERBUILDER -> "Powerbuilder"
    AthleteType.ZERCHER_LIFTER -> "Zercher Lifter"
    AthleteType.HYBRID -> "Atleta Híbrido"
    AthleteType.WEIGHTLIFTER -> "Halterófilo"
    AthleteType.CALISTHENICS -> "Calistenia"
}

private fun Gender.label() = when (this) {
    Gender.MALE -> "Masculino"
    Gender.FEMALE -> "Femenino"
    Gender.OTHER -> "Otro"
}

private fun com.example.kpkn.data.models.CalorieGoalObjective.label() = when (this) {
    com.example.kpkn.data.models.CalorieGoalObjective.DEFICIT -> "Déficit"
    com.example.kpkn.data.models.CalorieGoalObjective.MAINTENANCE -> "Mantenimiento"
    com.example.kpkn.data.models.CalorieGoalObjective.SURPLUS -> "Superávit"
}
