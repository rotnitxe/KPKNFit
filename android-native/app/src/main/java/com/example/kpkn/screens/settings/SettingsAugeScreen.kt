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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSliderItem
import com.example.kpkn.screens.settings.components.SettingsSwitchItem
import com.example.kpkn.screens.settings.components.SettingsTextFieldItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAugeScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()
    val algorithm = settings.algorithmSettings
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis RINGS (AUGE)", fontWeight = FontWeight.Black) },
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
            item { SettingsSectionHeader("Seguimiento") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Seguimiento de nutricion",
                        description = "AUGE considera la nutricion para la recuperacion",
                        checked = algorithm.augeEnableNutritionTracking,
                        onCheckedChange = { value ->
                            viewModel.update {
                                it.copy(algorithmSettings = it.algorithmSettings.copy(augeEnableNutritionTracking = value))
                            }
                        },
                    )
                    SettingsSwitchItem(
                        title = "Seguimiento de sueno",
                        description = "Incluye descanso en calculos de fatiga y readiness",
                        checked = algorithm.augeEnableSleepTracking,
                        onCheckedChange = { value ->
                            viewModel.update {
                                it.copy(algorithmSettings = it.algorithmSettings.copy(augeEnableSleepTracking = value))
                            }
                        },
                    )
                    SettingsSwitchItem(
                        title = "Alertas durante sesion",
                        description = "Muestra avisos AUGE dentro del editor y workout",
                        checked = algorithm.augeShowAlertsInSession,
                        onCheckedChange = { value ->
                            viewModel.update {
                                it.copy(algorithmSettings = it.algorithmSettings.copy(augeShowAlertsInSession = value))
                            }
                        },
                    )
                    SettingsSwitchItem(
                        title = "Auto-deload sugerido",
                        description = "Permite sugerencias preventivas cuando la fatiga se dispara",
                        checked = algorithm.augeAutoDeload,
                        onCheckedChange = { value ->
                            viewModel.update {
                                it.copy(algorithmSettings = it.algorithmSettings.copy(augeAutoDeload = value))
                            }
                        },
                    )
                }
            }

            item { SettingsSectionHeader("Sensibilidad") }
            item {
                SettingsSectionCard {
                    SettingsSliderItem(
                        title = "Umbral de readiness",
                        value = algorithm.augeReadinessThreshold.toFloat(),
                        onValueChange = { value ->
                            viewModel.update {
                                it.copy(
                                    algorithmSettings = it.algorithmSettings.copy(
                                        augeReadinessThreshold = value.toInt().coerceIn(20, 90),
                                    ),
                                )
                            }
                        },
                        valueRange = 20f..90f,
                        valueLabel = { it.toInt().toString() },
                    )
                    SettingsSliderItem(
                        title = "Sensibilidad de fatiga",
                        value = algorithm.augeFatigueSensitivity.toFloat(),
                        onValueChange = { value ->
                            viewModel.update {
                                it.copy(
                                    algorithmSettings = it.algorithmSettings.copy(
                                        augeFatigueSensitivity = (value * 10).toInt() / 10.0,
                                    ),
                                )
                            }
                        },
                        valueRange = 0.5f..2f,
                        steps = 14,
                        valueLabel = { "%.1f".format(it) },
                    )
                    SettingsSliderItem(
                        title = "Sensibilidad de recuperacion",
                        value = algorithm.augeRecoverySensitivity.toFloat(),
                        onValueChange = { value ->
                            viewModel.update {
                                it.copy(
                                    algorithmSettings = it.algorithmSettings.copy(
                                        augeRecoverySensitivity = (value * 10).toInt() / 10.0,
                                    ),
                                )
                            }
                        },
                        valueRange = 0.5f..2f,
                        steps = 14,
                        valueLabel = { "%.1f".format(it) },
                    )
                }
            }

            item { SettingsSectionHeader("Avanzado") }
            item {
                SettingsSectionCard {
                    Text(
                        text = "Parametros avanzados del algoritmo. Modifica solo si sabes lo que haces.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(if (showAdvanced) "Ocultar parametros" else "Mostrar parametros")
                    }
                }
            }
            if (showAdvanced) {
                item {
                    SettingsSectionCard {
                        SettingsTextFieldItem(
                            label = "Tasa decaimiento 1RM",
                            value = algorithm.oneRMDecayRate.toString(),
                            onValueChange = { value ->
                                value.toDoubleOrNull()?.let { parsed ->
                                    viewModel.update {
                                        it.copy(
                                            algorithmSettings = it.algorithmSettings.copy(oneRMDecayRate = parsed),
                                        )
                                    }
                                }
                            },
                            keyboardType = KeyboardType.Decimal,
                        )
                        SettingsTextFieldItem(
                            label = "Factor fatiga por fallo",
                            value = algorithm.failureFatigueFactor.toString(),
                            onValueChange = { value ->
                                value.toDoubleOrNull()?.let { parsed ->
                                    viewModel.update {
                                        it.copy(
                                            algorithmSettings = it.algorithmSettings.copy(failureFatigueFactor = parsed),
                                        )
                                    }
                                }
                            },
                            keyboardType = KeyboardType.Decimal,
                        )
                        SettingsSliderItem(
                            title = "Multiplicador vol. piernas",
                            value = algorithm.legVolumeMultiplier.toFloat(),
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(
                                        algorithmSettings = it.algorithmSettings.copy(
                                            legVolumeMultiplier = (value * 10).toInt() / 10.0,
                                        ),
                                    )
                                }
                            },
                            valueRange = 0.5f..2f,
                            steps = 14,
                            valueLabel = { "%.1f".format(it) },
                        )
                        SettingsSliderItem(
                            title = "Multiplicador vol. torso",
                            value = algorithm.torsoVolumeMultiplier.toFloat(),
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(
                                        algorithmSettings = it.algorithmSettings.copy(
                                            torsoVolumeMultiplier = (value * 10).toInt() / 10.0,
                                        ),
                                    )
                                }
                            },
                            valueRange = 0.5f..2f,
                            steps = 14,
                            valueLabel = { "%.1f".format(it) },
                        )
                        SettingsSliderItem(
                            title = "Factor sinergista",
                            value = algorithm.synergistFactor.toFloat(),
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(
                                        algorithmSettings = it.algorithmSettings.copy(
                                            synergistFactor = (value * 20).toInt() / 20.0,
                                        ),
                                    )
                                }
                            },
                            valueRange = 0f..1f,
                            steps = 19,
                            valueLabel = { "%.2f".format(it) },
                        )
                    }
                }
            }
        }
    }
}
