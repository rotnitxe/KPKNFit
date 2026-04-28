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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.R
import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.AppTheme
import com.example.kpkn.data.models.HapticIntensity
import com.example.kpkn.ui.locale.LocaleManager
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
                title = { Text(stringResource(R.string.screen_settings_general_title), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            item { SettingsSectionHeader(stringResource(R.string.screen_settings_general_section_appearance)) }
            item {
                SettingsSectionCard {
                    SettingsDropdownItem(
                        title = stringResource(R.string.screen_settings_general_theme),
                        description = stringResource(R.string.screen_settings_general_theme_desc),
                        options = AppTheme.entries,
                        selected = settings.appTheme,
                        onSelect = { value -> viewModel.update { it.copy(appTheme = value) } },
                        optionLabel = ::appThemeLabel,
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.screen_settings_general_animations),
                        description = stringResource(R.string.screen_settings_general_animations_desc),
                        checked = settings.enableAnimations,
                        onCheckedChange = { value -> viewModel.update { it.copy(enableAnimations = value) } },
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.screen_settings_general_reduced_motion),
                        description = stringResource(R.string.screen_settings_general_reduced_motion_desc),
                        checked = settings.reducedMotionMode,
                        onCheckedChange = { value -> viewModel.update { it.copy(reducedMotionMode = value) } },
                    )
                }
            }

            item { SettingsSectionHeader(stringResource(R.string.screen_settings_general_section_sensorial)) }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = stringResource(R.string.screen_settings_general_haptic),
                        description = stringResource(R.string.screen_settings_general_haptic_desc),
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(hapticFeedbackEnabled = value) } },
                    )
                    SettingsConditionalItem(visible = settings.hapticFeedbackEnabled) {
                        SettingsDropdownItem(
                            title = stringResource(R.string.screen_settings_general_haptic_intensity),
                            description = stringResource(R.string.screen_settings_general_haptic_intensity_desc),
                            options = HapticIntensity.entries,
                            selected = settings.hapticIntensity,
                            onSelect = { value -> viewModel.update { it.copy(hapticIntensity = value) } },
                            optionLabel = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        )
                    }
                    SettingsSwitchItem(
                        title = stringResource(R.string.screen_settings_general_sounds),
                        description = stringResource(R.string.screen_settings_general_sounds_desc),
                        checked = settings.soundsEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(soundsEnabled = value) } },
                    )
                }
            }

            item { SettingsSectionHeader(stringResource(R.string.screen_settings_general_section_ai)) }
            item {
                SettingsSectionCard {
                    SettingsDropdownItem(
                        title = stringResource(R.string.screen_settings_general_ai_provider),
                        description = stringResource(R.string.screen_settings_general_ai_provider_desc),
                        options = ApiProvider.entries,
                        selected = settings.apiProvider,
                        onSelect = { value -> viewModel.update { it.copy(apiProvider = value) } },
                        optionLabel = { it.name },
                    )
                    SettingsTextFieldItem(
                        label = stringResource(R.string.screen_settings_general_api_key_gemini),
                        value = settings.apiKeys.gemini.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(apiKeys = it.apiKeys.copy(gemini = value.ifBlank { null })) }
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    SettingsTextFieldItem(
                        label = stringResource(R.string.screen_settings_general_api_key_gpt),
                        value = settings.apiKeys.gpt.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(apiKeys = it.apiKeys.copy(gpt = value.ifBlank { null })) }
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    SettingsTextFieldItem(
                        label = stringResource(R.string.screen_settings_general_api_key_deepseek),
                        value = settings.apiKeys.deepseek.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(apiKeys = it.apiKeys.copy(deepseek = value.ifBlank { null })) }
                        },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    SettingsSliderItem(
                        title = stringResource(R.string.screen_settings_general_ai_temperature),
                        value = settings.aiTemperature.toFloat(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(aiTemperature = (value * 10).toInt() / 10.0) }
                        },
                        valueRange = 0f..1f,
                        steps = 9,
                        valueLabel = { "%.1f".format(it) },
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.screen_settings_general_ai_fallback),
                        description = stringResource(R.string.screen_settings_general_ai_fallback_desc),
                        checked = settings.aiFallbackEnabled,
                        onCheckedChange = { value -> viewModel.update { it.copy(aiFallbackEnabled = value) } },
                    )
                    SettingsTextFieldItem(
                        label = stringResource(R.string.screen_settings_general_ai_max_tokens),
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

            item { SettingsSectionHeader(stringResource(R.string.screen_settings_general_section_language)) }
            item {
                SettingsSectionCard {
                    val languageOptions = listOf(
                        LocaleManager.LANGUAGE_SYSTEM,
                        "es",
                        "en",
                    )
                    val languageLabels = mapOf(
                        LocaleManager.LANGUAGE_SYSTEM to stringResource(R.string.screen_settings_general_language_system),
                        "es" to stringResource(R.string.screen_settings_general_language_es),
                        "en" to stringResource(R.string.screen_settings_general_language_en),
                    )
                    SettingsDropdownItem(
                        title = stringResource(R.string.screen_settings_general_language),
                        description = stringResource(R.string.screen_settings_general_language_desc),
                        options = languageOptions,
                        selected = settings.appLanguage,
                        onSelect = { lang -> viewModel.update { it.copy(appLanguage = lang) } },
                        optionLabel = { code -> languageLabels[code] ?: code },
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
