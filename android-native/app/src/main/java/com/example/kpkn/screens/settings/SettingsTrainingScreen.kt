package com.example.kpkn.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.exercises.resolveExerciseId
import com.example.kpkn.data.models.IntensityMetric
import com.example.kpkn.data.models.VoiceInputMode
import com.example.kpkn.data.models.VoiceNoiseProfile
import com.example.kpkn.data.models.VoiceVerbosity
import com.example.kpkn.data.models.VolumeSystem
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.data.models.WorkoutLoggerMode
import com.example.kpkn.screens.settings.components.SettingsActionItem
import com.example.kpkn.screens.settings.components.SettingsDropdownItem
import com.example.kpkn.screens.settings.components.SettingsInfoRow
import com.example.kpkn.screens.settings.components.SettingsSectionCard
import com.example.kpkn.screens.settings.components.SettingsSectionHeader
import com.example.kpkn.screens.settings.components.SettingsSegmentedButtonItem
import com.example.kpkn.screens.settings.components.SettingsSliderItem
import com.example.kpkn.screens.settings.components.SettingsSwitchItem
import com.example.kpkn.screens.settings.components.SettingsTextFieldItem
import com.example.kpkn.services.workout.WorkoutVoiceDiagnosticStorage
import java.text.Normalizer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTrainingScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val weightUnitLabel = settings.weightUnit.name
    var aliasNickname by remember { mutableStateOf("") }
    var aliasExerciseQuery by remember { mutableStateOf("") }
    var aliasError by remember { mutableStateOf<String?>(null) }
    var voiceDiagnosticsFolder by remember {
        mutableStateOf(WorkoutVoiceDiagnosticStorage.configuredLabel(context))
    }
    val voiceDiagnosticsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        WorkoutVoiceDiagnosticStorage.configure(context, uri)
            .onSuccess { label ->
                voiceDiagnosticsFolder = label
                Toast.makeText(context, "JSONL de voz se guardarán en $label", Toast.LENGTH_LONG).show()
            }
            .onFailure { error ->
                Toast.makeText(
                    context,
                    "No se pudo usar la carpeta: ${error.message ?: "error desconocido"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

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

                    SettingsSegmentedButtonItem(
                        title = "Verbosidad de voz",
                        options = VoiceVerbosity.entries,
                        selected = settings.voiceVerbosity,
                        onSelect = { value -> viewModel.update { it.copy(voiceVerbosity = value) } },
                        optionLabel = {
                            when (it) {
                                VoiceVerbosity.COMPLETE -> "Completa"
                                VoiceVerbosity.ESSENTIAL -> "Esencial"
                                VoiceVerbosity.SILENT -> "Silencio"
                            }
                        },
                    )
                    SettingsSegmentedButtonItem(
                        title = "Perfil de micrófono",
                        options = VoiceNoiseProfile.entries,
                        selected = settings.voiceNoiseProfile,
                        onSelect = { value -> viewModel.update { it.copy(voiceNoiseProfile = value) } },
                        optionLabel = {
                            when (it) {
                                VoiceNoiseProfile.GYM -> "Gimnasio"
                                VoiceNoiseProfile.QUIET -> "Silencio"
                            }
                        },
                    )
                    SettingsSegmentedButtonItem(
                        title = "Modo de micrófono",
                        options = VoiceInputMode.entries,
                        selected = settings.voiceInputMode,
                        onSelect = { value -> viewModel.update { it.copy(voiceInputMode = value) } },
                        optionLabel = {
                            when (it) {
                                VoiceInputMode.CONTINUOUS -> "Continuo"
                                VoiceInputMode.PUSH_TO_TALK -> "Mantener"
                            }
                        },
                    )
                    SettingsSliderItem(
                        title = "Velocidad de voz TTS",
                        value = settings.ttsSpeechRate,
                        onValueChange = { value ->
                            val snapped = (Math.round(value * 20.0) / 20.0).toFloat().coerceIn(0.8f, 1.2f)
                            viewModel.update { it.copy(ttsSpeechRate = snapped) }
                        },
                        valueRange = 0.8f..1.2f,
                        steps = 7,
                        valueLabel = { String.format("%.2f×", it) },
                    )
                }
            }

            item { SettingsSectionHeader("Diagnósticos de voz") }
            item {
                SettingsSectionCard {
                    SettingsActionItem(
                        title = "Carpeta de JSONL automáticos",
                        description = voiceDiagnosticsFolder?.let { folder ->
                            "Activa: cada sesión con voz guarda sus diagnósticos en $folder"
                        } ?: "Toca aquí para elegir una carpeta. Se crea un JSONL incremental al activar la voz.",
                        icon = Icons.Default.Folder,
                        onClick = { voiceDiagnosticsFolderLauncher.launch(null) },
                    )
                    if (voiceDiagnosticsFolder != null) {
                        SettingsActionItem(
                            title = "Desvincular carpeta",
                            description = "Deja de copiar fuera de la app los próximos diagnósticos.",
                            icon = Icons.Default.Delete,
                            destructive = true,
                            onClick = {
                                WorkoutVoiceDiagnosticStorage.clear(context)
                                voiceDiagnosticsFolder = null
                                Toast.makeText(context, "Carpeta desvinculada", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            item { SettingsSectionHeader("Apodos de voz") }
            item {
                SettingsSectionCard {
                    SettingsTextFieldItem(
                        label = "Apodo (cómo lo dices)",
                        value = aliasNickname,
                        onValueChange = {
                            aliasNickname = it
                            aliasError = null
                        },
                        placeholder = "ej. press banca",
                    )
                    SettingsTextFieldItem(
                        label = "Ejercicio (nombre o id)",
                        value = aliasExerciseQuery,
                        onValueChange = {
                            aliasExerciseQuery = it
                            aliasError = null
                        },
                        placeholder = "ej. Press banca o tren_superior_...",
                    )
                    if (aliasError != null) {
                        SettingsInfoRow(title = "Error", value = aliasError!!)
                    }
                    SettingsActionItem(
                        title = "Añadir apodo",
                        description = "Mapea tu apodo al ejercicio del catálogo",
                        onClick = {
                            val nick = aliasNickname.trim()
                            if (nick.isBlank()) {
                                aliasError = "Escribe un apodo"
                                return@SettingsActionItem
                            }
                            val resolvedId = resolveVoiceAliasExerciseId(aliasExerciseQuery)
                            if (resolvedId == null) {
                                aliasError = "No encontré ese ejercicio"
                                return@SettingsActionItem
                            }
                            viewModel.update {
                                it.copy(
                                    voiceExerciseAliases = it.voiceExerciseAliases +
                                        (nick.lowercase(Locale.ROOT) to resolvedId),
                                )
                            }
                            aliasNickname = ""
                            aliasExerciseQuery = ""
                            aliasError = null
                        },
                    )
                    settings.voiceExerciseAliases.entries.sortedBy { it.key }.forEach { (nick, exerciseId) ->
                        val label = EXERCISE_DATABASE_BY_ID[exerciseId]?.name ?: exerciseId
                        SettingsActionItem(
                            title = "\"$nick\" → $label",
                            description = exerciseId,
                            icon = Icons.Default.Delete,
                            destructive = true,
                            onClick = {
                                viewModel.update {
                                    it.copy(voiceExerciseAliases = it.voiceExerciseAliases - nick)
                                }
                            },
                        )
                    }
                }
            }

            item { SettingsSectionHeader("Experiencia en sesion") }
            item {
                SettingsSectionCard {
                    SettingsSwitchItem(
                        title = "Mostrar PRs en entrenamiento",
                        description = "Resalta records y anuncia rango eRM al entrar (voz completa)",
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

private fun resolveVoiceAliasExerciseId(query: String): String? {
    val raw = query.trim()
    if (raw.isBlank()) return null
    val normalized = normalizeAliasQuery(raw)
    resolveExerciseId(raw)?.let { return it }
    EXERCISE_DATABASE_BY_ID[normalized]?.id?.let { return it }
    EXERCISE_DATABASE.firstOrNull { normalizeAliasQuery(it.name) == normalized }?.id?.let { return it }
    EXERCISE_DATABASE.firstOrNull {
        val name = normalizeAliasQuery(it.name)
        name.contains(normalized) || normalized.contains(name)
    }?.id?.let { return it }
    return null
}

private fun normalizeAliasQuery(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{Mn}+"), "").trim()
}

private fun volumeSystemLabel(value: VolumeSystem): String = when (value) {
    VolumeSystem.KPNK -> "KPKN (personalizado)"
    VolumeSystem.ISRAETEL -> "Israetel (generico)"
    VolumeSystem.MANUAL -> "Manual"
}
