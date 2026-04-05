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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.settings.components.SettingsConditionalItem
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSwitchItem
import com.example.kpkn.screens.settings.components.SettingsTimePickerItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNotificationsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones", fontWeight = FontWeight.Black) },
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
            item { SettingsSectionHeader("Entrenamiento") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Recordatorio de entrenamiento",
                        description = "Guarda la preferencia horaria para futuras notificaciones",
                        checked = settings.workoutReminderEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(workoutReminderEnabled = value) } },
                    )
                    SettingsConditionalItem(visible = settings.workoutReminderEnabled) {
                        SettingsTimePickerItem(
                            title = "Hora del recordatorio",
                            value = settings.workoutReminderTime,
                            onValueChange = { value -> viewModel.update { it.copy(workoutReminderTime = value) } },
                        )
                    }
                }
            }

            item { SettingsSectionHeader("Comidas") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Recordatorio de comidas",
                        description = "Activa horarios para las comidas principales",
                        checked = settings.mealReminderEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(mealReminderEnabled = value) } },
                    )
                    SettingsConditionalItem(visible = settings.mealReminderEnabled) {
                        SettingsTimePickerItem(
                            title = "Desayuno",
                            value = settings.mealReminderBreakfast,
                            onValueChange = { value -> viewModel.update { it.copy(mealReminderBreakfast = value) } },
                        )
                    }
                    SettingsConditionalItem(visible = settings.mealReminderEnabled) {
                        SettingsTimePickerItem(
                            title = "Almuerzo",
                            value = settings.mealReminderLunch,
                            onValueChange = { value -> viewModel.update { it.copy(mealReminderLunch = value) } },
                        )
                    }
                    SettingsConditionalItem(visible = settings.mealReminderEnabled) {
                        SettingsTimePickerItem(
                            title = "Cena",
                            value = settings.mealReminderDinner,
                            onValueChange = { value -> viewModel.update { it.copy(mealReminderDinner = value) } },
                        )
                    }
                }
            }

            item { SettingsSectionHeader("Sueno") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Recordatorio de sueno",
                        description = "Guarda el horario recomendado para ir cerrando el dia",
                        checked = settings.sleepReminderEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(sleepReminderEnabled = value) } },
                    )
                    SettingsConditionalItem(visible = settings.sleepReminderEnabled) {
                        SettingsTimePickerItem(
                            title = "Hora recordatorio",
                            value = settings.sleepReminderTime,
                            onValueChange = { value -> viewModel.update { it.copy(sleepReminderTime = value) } },
                        )
                    }
                }
            }
        }
    }
}
