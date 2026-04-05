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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.AppTheme
import com.example.kpkn.data.models.HapticIntensity
import com.example.kpkn.screens.settings.components.SettingsConditionalItem
import com.example.kpkn.screens.settings.components.SettingsDropdownItem
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSliderItem
import com.example.kpkn.screens.settings.components.SettingsSwitchItem
import com.example.kpkn.screens.settings.components.SettingsTextFieldItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("General", fontWeight = FontWeight.Black) },
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
            item { SettingsSectionHeader("Apariencia") }
            item {
                SettingsSectionCard {
                    SettingsDropdownItem(
                        title = "Tema de la app",
                        description = "Prepara el terreno para futuros modos visuales nativos",
                        options = AppTheme.entries,
                        selected = settings.appTheme,
                        onSelect = { value -> viewModel.update { it.copy(appTheme = value) } },
                        optionLabel = ::appThemeLabel,
                    )
                    SettingsSwitchItem(
                        title = "Animaciones",
                        description = "Activa las transiciones y microinteracciones de la interfaz",
                        checked = settings.enableAnimations,
                        onCheckedChange = { value -> viewModel.update { it.copy(enableAnimations = value) } },
                    )
                    SettingsSwitchItem(
                        title = "Modo ahorro rendimiento",
                        description = "Reduce movimiento y efectos pesados para equipos mas justos",
                        checked = settings.reducedMotionMode,
                        onCheckedChange = { value -> viewModel.update { it.copy(reducedMotionMode = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Sensorial") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Vibracion haptica",
                        description = "Feedback tactil en acciones clave de la app",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(hapticFeedbackEnabled = value) } },
                    )
                    SettingsConditionalItem(visible = settings.hapticFeedbackEnabled) {
                        SettingsDropdownItem(
                            title = "Intensidad haptica",
                            description = "Ajusta la fuerza del feedback",
                            options = HapticIntensity.entries,
                            selected = settings.hapticIntensity,
                            onSelect = { value -> viewModel.update { it.copy(hapticIntensity = value) } },
                            optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        )
                    }
                    SettingsSwitchItem(
                        title = "Sonidos",
                        description = "Incluye avisos sonoros y celebraciones in-app",
                        checked = settings.soundsEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(soundsEnabled = value) } },
                    )
                }
            }

            item { SettingsSectionHeader("Inteligencia artificial") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "IA local para alimentos",
                        description = "Prioriza el modelo on-device para parsear comidas",
                        checked = settings.localAiFoodEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(localAiFoodEnabled = value) } },
                    )
                    SettingsDropdownItem(
                        title = "Proveedor IA",
                        description = "Proveedor principal para funciones asistidas",
                        options = ApiProvider.entries,
                        selected = settings.apiProvider,
                        onSelect = { value -> viewModel.update { it.copy(apiProvider = value) } },
                        optionLabel = { it.name },
                    )
                    SettingsTextFieldItem(
                        label = "Clave API Gemini",
                        value = settings.apiKeys.gemini.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(apiKeys = it.apiKeys.copy(gemini = value.ifBlank { null })) }
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    SettingsTextFieldItem(
                        label = "Clave API GPT",
                        value = settings.apiKeys.gpt.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(apiKeys = it.apiKeys.copy(gpt = value.ifBlank { null })) }
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    SettingsTextFieldItem(
                        label = "Clave API DeepSeek",
                        value = settings.apiKeys.deepseek.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(apiKeys = it.apiKeys.copy(deepseek = value.ifBlank { null })) }
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    SettingsSliderItem(
                        title = "Temperatura IA",
                        value = settings.aiTemperature.toFloat(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(aiTemperature = (value * 10).toInt() / 10.0) }
                        },
                        valueRange = 0f..1f,
                        steps = 9,
                        valueLabel = { "%.1f".format(it) },
                    )
                    SettingsSwitchItem(
                        title = "Usar fallback IA",
                        description = "Si falla el proveedor principal, intenta el siguiente",
                        checked = settings.aiFallbackEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(aiFallbackEnabled = value) } },
                    )
                    SettingsTextFieldItem(
                        label = "Tokens maximos",
                        value = settings.aiMaxTokens.toString(),
                        onValueChange = { value ->
                            value.filter(Char::isDigit).toIntOrNull()?.let { parsed ->
                                viewModel.update { it.copy(aiMaxTokens = parsed.coerceIn(64, 4096)) }
                            }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                }
            }
        }
    }
}

private fun appThemeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.DEFAULT -> "Default"
    AppTheme.DARK -> "Dark"
    AppTheme.DEEP_BLACK -> "Deep Black"
    AppTheme.VOLT -> "Volt"
    AppTheme.LIGHT -> "Light"
}
