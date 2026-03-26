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
import kotlinx.coroutines.flow.stateIn

// ─── ViewModel ───────────────────────────────────────────────────────────────

class ProfileViewModel : ViewModel() {
    private val repository = ProgramRepository.getInstance()

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings())

    fun update(transform: (Settings) -> Settings) {
        repository.updateSettings(transform)
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel { ProfileViewModel() },
) {
    val settings by viewModel.settings.collectAsState()
    var editMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(Icons.Default.Edit, contentDescription = if (editMode) "Guardar" else "Editar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val initials = settings.username
                    .split(" ")
                    .take(2)
                    .map { it.firstOrNull()?.uppercaseChar() ?: ' ' }
                    .joinToString("")
                    .ifEmpty { "U" }
                Text(
                    initials,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                settings.username.ifBlank { "Usuario" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )

            // Athlete type badge
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    settings.athleteType.label(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Key Stats Row
            val weight = settings.userVitals.weight
            val height = settings.userVitals.height
            val age = settings.userVitals.age
            val bmi: Double? = if (weight != null && height != null && height > 0) {
                val h = height / 100.0
                (weight / (h * h) * 10).toLong() / 10.0
            } else null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip("Peso", weight?.let { "${it.toInt()} kg" } ?: "—")
                StatChip("Altura", height?.let { "${it.toInt()} cm" } ?: "—")
                StatChip("Edad", age?.let { "$it años" } ?: "—")
                StatChip("IMC", bmi?.let { "$it" } ?: "—")
            }

            Spacer(Modifier.height(24.dp))

            // Body Composition
            val bodyFat = settings.userVitals.bodyFatPercentage
            val muscle = settings.userVitals.muscleMassPercentage
            if (bodyFat != null || muscle != null) {
                ProfileSection("Composición Corporal") {
                    if (bodyFat != null) {
                        ProfileStatRow("Grasa corporal", "${bodyFat.toInt()}%")
                    }
                    if (muscle != null) {
                        ProfileStatRow("Masa muscular", "${muscle.toInt()}%")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Gender / Activity
            ProfileSection("Datos personales") {
                ProfileStatRow("Género", settings.userVitals.gender?.label() ?: "No especificado")
                ProfileStatRow("Objetivo calórico", settings.calorieGoalObjective.label())
                ProfileStatRow("Unidad de peso", settings.weightUnit.name)
                ProfileStatRow("Intensidad", settings.intensityMetric.name)
            }

            Spacer(Modifier.height(16.dp))

            // Macros
            val cal = settings.dailyCalorieGoal
            val prot = settings.dailyProteinGoal
            val carbs = settings.dailyCarbGoal
            val fat = settings.dailyFatGoal
            if (cal != null || prot != null) {
                ProfileSection("Objetivos Nutricionales") {
                    cal?.let { ProfileStatRow("Calorías", "$it kcal") }
                    prot?.let { ProfileStatRow("Proteínas", "${it}g") }
                    carbs?.let { ProfileStatRow("Carbohidratos", "${it}g") }
                    fat?.let { ProfileStatRow("Grasas", "${it}g") }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (editMode) {
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Edición rápida",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Para editar tu perfil completo, ve a Ajustes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, value: String) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun ProfileStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

// ─── Extension labels ─────────────────────────────────────────────────────────

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
