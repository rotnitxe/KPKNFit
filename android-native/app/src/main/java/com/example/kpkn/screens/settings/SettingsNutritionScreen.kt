package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.HealthAndSafety
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.settings.components.SettingsActionItem
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSliderItem
import com.example.kpkn.screens.settings.components.SettingsSwitchItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNutritionScreen(
    onBack: () -> Unit,
    onOpenPlanOverlay: () -> Unit,
    onOpenCalibration: () -> Unit = {},
    showHealthConnect: Boolean = false,
    onOpenHealthConnect: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrición", fontWeight = FontWeight.Black) },
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
            item { SettingsSectionHeader("Visualización") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Mostrar sobrepaso",
                        description = "Permite que los porcentajes superen 100% cuando pasas tus metas.",
                        checked = settings.nutritionShowOverages,
                        onCheckedChange = { value -> viewModel.update { it.copy(nutritionShowOverages = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Sueño") }
            item {
                SettingsSectionCard {
                    SettingsSliderItem(
                        title = "Meta de sueño",
                        value = settings.sleepTargetHours.toFloat(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(sleepTargetHours = (value * 2).toInt() / 2.0) }
                        },
                        valueRange = 4f..12f,
                        steps = 15,
                        valueLabel = { "%.1f h".format(it) },
                    )
                    SettingsSwitchItem(
                        title = "Sueño inteligente",
                        description = "Permite ajustes adaptativos alrededor del descanso.",
                        checked = settings.smartSleepEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(smartSleepEnabled = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Herramientas de Plan") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Configurar plan nutricional",
                        description = "Abre el wizard de 6 pasos para calcular objetivos con EER 2023 y guardarlos como un plan.",
                        icon = Icons.Default.Restaurant,
                        onClick = onOpenPlanOverlay,
                    )
                    SettingsActionItem(
                        title = "Calibrar con datos reales",
                        description = "Revisa una muestra de 14–21 días antes de aplicar ajustes de ±150 kcal.",
                        icon = Icons.Default.Timeline,
                        onClick = onOpenCalibration,
                    )
                    if (showHealthConnect) {
                        SettingsActionItem(
                            title = "Health Connect",
                            description = "Importa peso y composición corporal con fecha, fuente e identificador deduplicable.",
                            icon = Icons.Default.HealthAndSafety,
                            onClick = onOpenHealthConnect,
                        )
                    }
                }
            }
        }
    }
}
