package com.example.kpkn.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
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
import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.CalorieGoalObjective
import com.example.kpkn.screens.settings.components.SettingsActionItem
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSegmentedButtonItem
import com.example.kpkn.screens.settings.components.SettingsSliderItem
import com.example.kpkn.screens.settings.components.SettingsSwitchItem
import com.example.kpkn.screens.settings.components.SettingsTextFieldItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNutritionScreen(
    onBack: () -> Unit,
    onOpenPlanOverlay: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutricion", fontWeight = FontWeight.Black) },
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
            item { SettingsSectionHeader("Objetivos") }
            item {
                SettingsSectionCard {
                    SettingsSegmentedButtonItem(
                        title = "Objetivo calorico",
                        options = CalorieGoalObjective.entries,
                        selected = settings.calorieGoalObjective,
                        onSelect = { value -> viewModel.update { it.copy(calorieGoalObjective = value) } },
                        optionLabel = ::calorieObjectiveLabel,
                    )
                    SettingsTextFieldItem(
                        label = "Calorias diarias",
                        value = settings.dailyCalorieGoal?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyCalorieGoal = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            item { SettingsSectionHeader("Macronutrientes") }
            item {
                SettingsSectionCard {
                    SettingsTextFieldItem(
                        label = "Proteinas diarias (g)",
                        value = settings.dailyProteinGoal?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyProteinGoal = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Carbohidratos diarios (g)",
                        value = settings.dailyCarbGoal?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyCarbGoal = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Grasas diarias (g)",
                        value = settings.dailyFatGoal?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyFatGoal = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsSwitchItem(
                        title = "Mostrar sobrepaso",
                        description = "Permite que los porcentajes superen 100% cuando pasas tus metas",
                        checked = settings.nutritionShowOverages,
                        onCheckedChange = { value -> viewModel.update { it.copy(nutritionShowOverages = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Micronutrientes") }
            item {
                SettingsSectionCard {
                    SettingsTextFieldItem(
                        label = "Meta de fibra (g)",
                        value = settings.dailyFiberGoal?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyFiberGoal = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Limite de azucar (g)",
                        value = settings.dailySugarLimit?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailySugarLimit = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Limite de sodio (mg)",
                        value = settings.dailySodiumLimitMg?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailySodiumLimitMg = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Meta de potasio (mg)",
                        value = settings.dailyPotassiumGoalMg?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyPotassiumGoalMg = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Meta de hidratacion (ml)",
                        value = settings.dailyHydrationGoalMl?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(dailyHydrationGoalMl = value.filter(Char::isDigit).toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            item { SettingsSectionHeader("Sueno") }
            item {
                SettingsSectionCard {
                    SettingsSliderItem(
                        title = "Meta de sueno",
                        value = settings.sleepTargetHours.toFloat(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(sleepTargetHours = (value * 2).toInt() / 2.0) }
                        },
                        valueRange = 4f..12f,
                        steps = 15,
                        valueLabel = { "%.1f h".format(it) },
                    )
                    SettingsSwitchItem(
                        title = "Sueno inteligente",
                        description = "Permite ajustes adaptativos alrededor del descanso",
                        checked = settings.smartSleepEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(smartSleepEnabled = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Herramientas") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Editar plan nutricional",
                        description = "Abre el panel unificado para recalcular objetivos y macros",
                        icon = Icons.Default.Restaurant,
                        onClick = onOpenPlanOverlay,
                    )
                }
            }

            item { SettingsSectionHeader("IA Avanzada") }
            item {
                SettingsSectionCard {
                    SettingsSegmentedButtonItem(
                        title = "Proveedor API",
                        options = ApiProvider.entries,
                        selected = settings.apiProvider,
                        onSelect = { value -> viewModel.update { it.copy(apiProvider = value) } },
                        optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    )
                    when (settings.apiProvider) {
                        ApiProvider.GEMINI -> SettingsTextFieldItem(
                            label = "API Key Gemini",
                            value = settings.apiKeys.gemini.orEmpty(),
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(apiKeys = it.apiKeys.copy(gemini = value.takeIf { it.isNotBlank() }))
                                }
                            },
                            keyboardType = KeyboardType.Password,
                        )
                        ApiProvider.GPT -> SettingsTextFieldItem(
                            label = "API Key OpenAI",
                            value = settings.apiKeys.gpt.orEmpty(),
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(apiKeys = it.apiKeys.copy(gpt = value.takeIf { it.isNotBlank() }))
                                }
                            },
                            keyboardType = KeyboardType.Password,
                        )
                        ApiProvider.DEEPSEEK -> SettingsTextFieldItem(
                            label = "API Key DeepSeek",
                            value = settings.apiKeys.deepseek.orEmpty(),
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(apiKeys = it.apiKeys.copy(deepseek = value.takeIf { it.isNotBlank() }))
                                }
                            },
                            keyboardType = KeyboardType.Password,
                        )
                    }
                }
            }
        }
    }
}

private fun calorieObjectiveLabel(value: CalorieGoalObjective): String = when (value) {
    CalorieGoalObjective.DEFICIT -> "Deficit"
    CalorieGoalObjective.MAINTENANCE -> "Mantener"
    CalorieGoalObjective.SURPLUS -> "Superavit"
}
