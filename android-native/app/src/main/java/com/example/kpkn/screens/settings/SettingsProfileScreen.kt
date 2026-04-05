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
import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.Gender
import com.example.kpkn.screens.settings.components.SettingsDropdownItem
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsTextFieldItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsProfileScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val settings by viewModel.settings.collectAsState()
    val weightUnitLabel = settings.weightUnit.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil personal", fontWeight = FontWeight.Black) },
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
            item { SettingsSectionHeader("Identidad") }
            item {
                SettingsSectionCard {
                    SettingsTextFieldItem(
                        label = "Nombre de usuario",
                        value = settings.username,
                        onValueChange = { value -> viewModel.update { it.copy(username = value) } },
                    )
                    SettingsDropdownItem(
                        title = "Tipo de atleta",
                        description = "Ajusta tono y contexto en diferentes experiencias",
                        options = AthleteType.entries,
                        selected = settings.athleteType,
                        onSelect = { value -> viewModel.update { it.copy(athleteType = value) } },
                        optionLabel = ::athleteTypeLabel,
                    )
                    SettingsTextFieldItem(
                        label = "Nombre del gimnasio",
                        value = settings.gymName.orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { it.copy(gymName = value.ifBlank { null }) }
                        },
                        placeholder = "Opcional",
                    )
                }
            }

            item { SettingsSectionHeader("Medidas corporales") }
            item {
                SettingsSectionCard {
                    SettingsDropdownItem(
                        title = "Genero",
                        description = "Se usa en algunos calculos y sugerencias",
                        options = Gender.entries,
                        selected = settings.userVitals.gender ?: Gender.OTHER,
                        onSelect = { value ->
                            viewModel.update { current ->
                                current.copy(userVitals = current.userVitals.copy(gender = value))
                            }
                        },
                        optionLabel = ::genderLabel,
                    )
                    SettingsTextFieldItem(
                        label = "Edad",
                        value = settings.userVitals.age?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { current ->
                                val parsed = value.filter(Char::isDigit).toIntOrNull()
                                current.copy(
                                    age = parsed,
                                    userVitals = current.userVitals.copy(age = parsed),
                                )
                            }
                        },
                        keyboardType = KeyboardType.Number,
                    )
                    SettingsTextFieldItem(
                        label = "Peso ($weightUnitLabel)",
                        value = settings.userVitals.weight?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { current ->
                                current.copy(userVitals = current.userVitals.copy(weight = value.toDoubleOrNull()))
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                    SettingsTextFieldItem(
                        label = "Altura (cm)",
                        value = settings.userVitals.height?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { current ->
                                current.copy(userVitals = current.userVitals.copy(height = value.toDoubleOrNull()))
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }

            item { SettingsSectionHeader("Composicion corporal") }
            item {
                SettingsSectionCard {
                    SettingsTextFieldItem(
                        label = "Grasa corporal (%)",
                        value = settings.userVitals.bodyFatPercentage?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { current ->
                                current.copy(
                                    userVitals = current.userVitals.copy(bodyFatPercentage = value.toDoubleOrNull()),
                                )
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                    SettingsTextFieldItem(
                        label = "Masa muscular (%)",
                        value = settings.userVitals.muscleMassPercentage?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { current ->
                                current.copy(
                                    userVitals = current.userVitals.copy(muscleMassPercentage = value.toDoubleOrNull()),
                                )
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                    SettingsTextFieldItem(
                        label = "Peso objetivo ($weightUnitLabel)",
                        value = settings.userVitals.targetWeight?.toString().orEmpty(),
                        onValueChange = { value ->
                            viewModel.update { current ->
                                current.copy(userVitals = current.userVitals.copy(targetWeight = value.toDoubleOrNull()))
                            }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }
        }
    }
}

private fun athleteTypeLabel(type: AthleteType): String = when (type) {
    AthleteType.ENTHUSIAST -> "Enthusiast"
    AthleteType.POWERLIFTER -> "Powerlifter"
    AthleteType.BODYBUILDER -> "Bodybuilder"
    AthleteType.POWERBUILDER -> "Powerbuilder"
    AthleteType.ZERCHER_LIFTER -> "Zercher Lifter"
    AthleteType.HYBRID -> "Hybrid"
    AthleteType.WEIGHTLIFTER -> "Weightlifter"
    AthleteType.CALISTHENICS -> "Calisthenics"
}

private fun genderLabel(gender: Gender): String = when (gender) {
    Gender.MALE -> "Masculino"
    Gender.FEMALE -> "Femenino"
    Gender.OTHER -> "Otro"
}
