package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.IntensityMetric
import com.example.kpkn.data.models.OneRMFormula
import com.example.kpkn.data.models.VolumeSystem
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.models.WorkoutLoggerMode
import com.example.kpkn.screens.settings.components.SettingsDropdownItem
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSegmentedButtonItem
import com.example.kpkn.screens.settings.components.SettingsSliderItem
import com.example.kpkn.screens.settings.components.SettingsSwitchItem
import com.example.kpkn.screens.settings.components.SettingsTextFieldItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTrainingScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()
    val weightUnitLabel = settings.weightUnit.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entrenamiento", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { SettingsSectionHeader("Unidades") }
            item {
                SettingsSectionCard {
                    SettingsSegmentedButtonItem(
                        title = "Unidad de peso",
                        options = WeightUnit.entries,
                        selected = settings.weightUnit,
                        onSelect = { value -> viewModel.update { it.copy(weightUnit = value) } },
                        optionLabel = { it.name },
                    )
                    SettingsSegmentedButtonItem(
                        title = "Metrica de intensidad",
                        options = IntensityMetric.entries,
                        selected = settings.intensityMetric,
                        onSelect = { value -> viewModel.update { it.copy(intensityMetric = value) } },
                        optionLabel = { it.name },
                    )
                    SettingsDropdownItem(
                        title = "Formula 1RM",
                        options = OneRMFormula.entries,
                        selected = settings.oneRMFormula,
                        onSelect = { value -> viewModel.update { it.copy(oneRMFormula = value) } },
                        optionLabel = { it.name },
                    )
                    SettingsTextFieldItem(
                        label = "Peso barra por defecto ($weightUnitLabel)",
                        value = settings.barbellWeight.toString(),
                        onValueChange = { value ->
                            value.toDoubleOrNull()?.let { parsed ->
                                viewModel.update { it.copy(barbellWeight = parsed) }
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }

            item { SettingsSectionHeader("Temporizador") }
            item {
                SettingsSectionCard {
                    SettingsSliderItem(
                        title = "Descanso por defecto",
                        value = settings.restTimerDefaultSeconds.toFloat(),
                        onValueChange = { value ->
                            val snapped = ((value.toInt() - 30) / 15) * 15 + 30
                            viewModel.update { it.copy(restTimerDefaultSeconds = snapped.coerceIn(30, 300)) }
                        },
                        valueRange = 30f..300f,
                        steps = 17,
                        valueLabel = { "${it.toInt()} s" },
                    )
                    SettingsSwitchItem(
                        title = "Iniciar descanso automatico",
                        description = "Dispara el timer apenas registras una serie",
                        checked = settings.restTimerAutoStart,
                        onCheckedChange = { value -> viewModel.update { it.copy(restTimerAutoStart = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Experiencia en sesion") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Mostrar PRs en entrenamiento",
                        description = "Resalta records personales dentro de la sesion",
                        checked = settings.showPRsInWorkout,
                        onCheckedChange = { value -> viewModel.update { it.copy(showPRsInWorkout = value) } },
                    )
                    SettingsSegmentedButtonItem(
                        title = "Modo del logger",
                        options = WorkoutLoggerMode.entries,
                        selected = settings.workoutLoggerMode,
                        onSelect = { value -> viewModel.update { it.copy(workoutLoggerMode = value) } },
                        optionLabel = {
                            when (it) {
                                WorkoutLoggerMode.PRO -> "Pro"
                                WorkoutLoggerMode.SIMPLE -> "Simple"
                            }
                        },
                    )
                    SettingsSwitchItem(
                        title = "Vista compacta de sesion",
                        description = "Reduce densidad visual de las cards",
                        checked = settings.sessionCompactView,
                        onCheckedChange = { value -> viewModel.update { it.copy(sessionCompactView = value) } },
                    )
                    SettingsSwitchItem(
                        title = "Avanzar campos automaticamente",
                        description = "Mueve el foco al siguiente input tras registrar",
                        checked = settings.sessionAutoAdvanceFields,
                        onCheckedChange = { value -> viewModel.update { it.copy(sessionAutoAdvanceFields = value) } },
                    )
                    SettingsSwitchItem(
                        title = "Prompt ahorro de tiempo",
                        description = "Sugiere saltar pasos innecesarios al terminar",
                        checked = settings.showTimeSaverPrompt,
                        onCheckedChange = { value -> viewModel.update { it.copy(showTimeSaverPrompt = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Volumen") }
            item {
                SettingsSectionCard {
                    SettingsDropdownItem(
                        title = "Sistema de volumen por defecto",
                        description = "El typo interno KPNK se mantiene por compatibilidad",
                        options = VolumeSystem.entries,
                        selected = settings.defaultVolumeSystem,
                        onSelect = { value -> viewModel.update { it.copy(defaultVolumeSystem = value) } },
                        optionLabel = ::volumeSystemLabel,
                    )
                }
            }
        }
    }
}

private fun volumeSystemLabel(value: VolumeSystem): String = when (value) {
    VolumeSystem.KPNK -> "KPKN (personalizado)"
    VolumeSystem.ISRAETEL -> "Israetel (generico)"
    VolumeSystem.MANUAL -> "Manual"
}
