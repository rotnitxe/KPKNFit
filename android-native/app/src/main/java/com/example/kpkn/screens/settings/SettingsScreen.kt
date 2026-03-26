package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// ─── ViewModel ───────────────────────────────────────────────────────────────

class SettingsViewModel : ViewModel() {
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
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ─── Perfil ──────────────────────────────────────────────────────
            SettingsSection("Perfil") {
                SettingsTextField(
                    label = "Nombre de usuario",
                    value = settings.username,
                    onValueChange = { viewModel.update { s -> s.copy(username = it) } },
                )

                SettingsDropdown(
                    label = "Tipo de atleta",
                    options = AthleteType.entries.map { it.name },
                    selected = settings.athleteType.name,
                    onSelect = { name ->
                        val t = AthleteType.entries.firstOrNull { it.name == name } ?: AthleteType.ENTHUSIAST
                        viewModel.update { s -> s.copy(athleteType = t) }
                    },
                )

                SettingsDropdown(
                    label = "Género",
                    options = listOf("MALE", "FEMALE", "OTHER"),
                    selected = settings.userVitals.gender?.name ?: "No especificado",
                    onSelect = { name ->
                        val g = Gender.entries.firstOrNull { it.name == name }
                        viewModel.update { s -> s.copy(userVitals = s.userVitals.copy(gender = g)) }
                    },
                )

                SettingsNumberField(
                    label = "Peso (kg)",
                    value = settings.userVitals.weight?.toString() ?: "",
                    onValueChange = { raw ->
                        val d = raw.toDoubleOrNull()
                        viewModel.update { s -> s.copy(userVitals = s.userVitals.copy(weight = d)) }
                    },
                )

                SettingsNumberField(
                    label = "Altura (cm)",
                    value = settings.userVitals.height?.toString() ?: "",
                    onValueChange = { raw ->
                        val d = raw.toDoubleOrNull()
                        viewModel.update { s -> s.copy(userVitals = s.userVitals.copy(height = d)) }
                    },
                )
            }

            // ─── Nutrición ───────────────────────────────────────────────────
            SettingsSection("Nutrición") {
                SettingsDropdown(
                    label = "Objetivo calórico",
                    options = CalorieGoalObjective.entries.map { it.name },
                    selected = settings.calorieGoalObjective.name,
                    onSelect = { name ->
                        val obj = CalorieGoalObjective.entries.firstOrNull { it.name == name } ?: CalorieGoalObjective.MAINTENANCE
                        viewModel.update { s -> s.copy(calorieGoalObjective = obj) }
                    },
                )

                SettingsNumberField(
                    label = "Calorías diarias",
                    value = settings.dailyCalorieGoal?.toString() ?: "",
                    onValueChange = { raw ->
                        val i = raw.toIntOrNull()
                        viewModel.update { s -> s.copy(dailyCalorieGoal = i) }
                    },
                )

                SettingsNumberField(
                    label = "Proteínas diarias (g)",
                    value = settings.dailyProteinGoal?.toString() ?: "",
                    onValueChange = { raw ->
                        val i = raw.toIntOrNull()
                        viewModel.update { s -> s.copy(dailyProteinGoal = i) }
                    },
                )

                SettingsNumberField(
                    label = "Carbohidratos diarios (g)",
                    value = settings.dailyCarbGoal?.toString() ?: "",
                    onValueChange = { raw ->
                        val i = raw.toIntOrNull()
                        viewModel.update { s -> s.copy(dailyCarbGoal = i) }
                    },
                )

                SettingsNumberField(
                    label = "Grasas diarias (g)",
                    value = settings.dailyFatGoal?.toString() ?: "",
                    onValueChange = { raw ->
                        val i = raw.toIntOrNull()
                        viewModel.update { s -> s.copy(dailyFatGoal = i) }
                    },
                )
            }

            // ─── Entrenamiento ────────────────────────────────────────────────
            SettingsSection("Entrenamiento") {
                SettingsDropdown(
                    label = "Unidad de peso",
                    options = WeightUnit.entries.map { it.name },
                    selected = settings.weightUnit.name,
                    onSelect = { name ->
                        val u = WeightUnit.entries.firstOrNull { it.name == name } ?: WeightUnit.KG
                        viewModel.update { s -> s.copy(weightUnit = u) }
                    },
                )

                SettingsDropdown(
                    label = "Métrica de intensidad",
                    options = IntensityMetric.entries.map { it.name },
                    selected = settings.intensityMetric.name,
                    onSelect = { name ->
                        val m = IntensityMetric.entries.firstOrNull { it.name == name } ?: IntensityMetric.RIR
                        viewModel.update { s -> s.copy(intensityMetric = m) }
                    },
                )

                SettingsDropdown(
                    label = "Fórmula 1RM",
                    options = OneRMFormula.entries.map { it.name },
                    selected = settings.oneRMFormula.name,
                    onSelect = { name ->
                        val f = OneRMFormula.entries.firstOrNull { it.name == name } ?: OneRMFormula.BRZYCKI
                        viewModel.update { s -> s.copy(oneRMFormula = f) }
                    },
                )

                SettingsNumberField(
                    label = "Peso barra por defecto (kg)",
                    value = settings.barbellWeight.toString(),
                    onValueChange = { raw ->
                        val d = raw.toDoubleOrNull() ?: 20.0
                        viewModel.update { s -> s.copy(barbellWeight = d) }
                    },
                )

                SettingsNumberField(
                    label = "Descanso por defecto (segundos)",
                    value = settings.restTimerDefaultSeconds.toString(),
                    onValueChange = { raw ->
                        val i = raw.toIntOrNull() ?: 90
                        viewModel.update { s -> s.copy(restTimerDefaultSeconds = i) }
                    },
                )

                SettingsSwitch(
                    label = "Iniciar descanso automáticamente",
                    checked = settings.restTimerAutoStart,
                    onCheckedChange = { viewModel.update { s -> s.copy(restTimerAutoStart = it) } },
                )

                SettingsSwitch(
                    label = "Mostrar PRs durante el entrenamiento",
                    checked = settings.showPRsInWorkout,
                    onCheckedChange = { viewModel.update { s -> s.copy(showPRsInWorkout = it) } },
                )
            }

            // ─── AUGE / Algoritmo ─────────────────────────────────────────────
            SettingsSection("AUGE — Algoritmo") {
                SettingsSwitch(
                    label = "Seguimiento de nutrición",
                    checked = settings.algorithmSettings.augeEnableNutritionTracking,
                    onCheckedChange = { v ->
                        viewModel.update { s -> s.copy(algorithmSettings = s.algorithmSettings.copy(augeEnableNutritionTracking = v)) }
                    },
                )

                SettingsSwitch(
                    label = "Seguimiento de sueño",
                    checked = settings.algorithmSettings.augeEnableSleepTracking,
                    onCheckedChange = { v ->
                        viewModel.update { s -> s.copy(algorithmSettings = s.algorithmSettings.copy(augeEnableSleepTracking = v)) }
                    },
                )
            }

            // ─── UI / Apariencia ──────────────────────────────────────────────
            SettingsSection("Apariencia") {
                SettingsSwitch(
                    label = "Animaciones",
                    checked = settings.enableAnimations,
                    onCheckedChange = { viewModel.update { s -> s.copy(enableAnimations = it) } },
                )

                SettingsSwitch(
                    label = "Vibración háptica",
                    checked = settings.hapticFeedbackEnabled,
                    onCheckedChange = { viewModel.update { s -> s.copy(hapticFeedbackEnabled = it) } },
                )

                SettingsSwitch(
                    label = "Sonidos",
                    checked = settings.soundsEnabled,
                    onCheckedChange = { viewModel.update { s -> s.copy(soundsEnabled = it) } },
                )

                // Theme selector
                SettingsDropdown(
                    label = "Tema",
                    options = AppThemeMode.entries.map { it.name },
                    selected = themeMode.name,
                    onSelect = { name ->
                        val t = AppThemeMode.entries.firstOrNull { it.name == name } ?: AppThemeMode.DARK
                        onThemeChange(t)
                    },
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
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

// ─── Setting Row Helpers ──────────────────────────────────────────────────────

@Composable
private fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
private fun SettingsNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(8.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

@Composable
private fun SettingsDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
