package com.example.kpkn.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogSearchExerciseId
import com.example.kpkn.data.exercises.resolveExerciseId
import com.example.kpkn.data.models.IntensityMetric
import com.example.kpkn.data.models.VoiceCaptureMode
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
import com.example.kpkn.screens.workout.components.VoiceCaptureModeDialog
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
    var showVoiceModeExplainer by remember { mutableStateOf(false) }
    var customPhraseText by remember { mutableStateOf("") }
    var customPhraseKind by remember { mutableStateOf("RPE") }
    var customPhraseValue by remember { mutableStateOf("") }
    var customPhraseError by remember { mutableStateOf<String?>(null) }

    fun addCustomIntensityPhrase() {
        val phrase = customPhraseText.trim()
        if (phrase.length < 4) {
            customPhraseError = "La frase debe tener al menos 4 caracteres."
            return
        }
        val normalized = java.text.Normalizer.normalize(
            phrase.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD,
        ).replace("\\p{Mn}+".toRegex(), "")
        val duplicated = settings.voiceCustomIntensityPhrases.any {
            java.text.Normalizer.normalize(
                it.phrase.lowercase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD,
            ).replace("\\p{Mn}+".toRegex(), "") == normalized
        }
        if (duplicated) {
            customPhraseError = "Esa frase ya existe."
            return
        }
        val kind = when (customPhraseKind) {
            "RIR" -> "RIR"
            "%RM" -> "PERCENT_RM"
            "Al fallo" -> "FALLO"
            else -> "RPE"
        }
        val value: Double? = if (kind == "FALLO") {
            null
        } else {
            customPhraseValue.replace(',', '.').toDoubleOrNull()?.let { v ->
                when (kind) {
                    "RPE" -> v.coerceIn(1.0, 10.0)
                    "RIR" -> v.coerceIn(0.0, 10.0)
                    else -> v.coerceIn(1.0, 100.0)
                }
            } ?: run {
                customPhraseError = "Indicá un número válido para la intensidad."
                return
            }
        }
        viewModel.update { current ->
            current.copy(
                voiceCustomIntensityPhrases = current.voiceCustomIntensityPhrases +
                    com.example.kpkn.data.models.CustomIntensityPhrase(
                        phrase = phrase,
                        kind = kind,
                        value = value,
                    ),
            )
        }
        customPhraseText = ""
        customPhraseValue = ""
        customPhraseError = null
    }
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
                        title = "Modo de captura de voz",
                        options = VoiceCaptureMode.entries,
                        selected = settings.voiceCaptureMode,
                        onSelect = { value ->
                            viewModel.update {
                                it.copy(voiceCaptureMode = value, hasChosenVoiceCaptureMode = true)
                            }
                        },
                        optionLabel = {
                            when (it) {
                                VoiceCaptureMode.HANDS_FREE -> "Manos libres"
                                VoiceCaptureMode.MUSIC -> "Música"
                            }
                        },
                    )
                    SettingsActionItem(
                        title = "Ver explicación de los modos",
                        description = "Recuerda qué hace cada modo con tu música y tu micrófono.",
                        icon = Icons.Default.Info,
                        onClick = { showVoiceModeExplainer = true },
                    )
                    SettingsSwitchItem(
                        title = "Sugerir carga por serie",
                        description = "Anuncia la carga recomendada al empezar cada serie; di \"sugerencia aplicada\" para usarla.",
                        checked = settings.voiceAutoSuggestLoads,
                        onCheckedChange = { value -> viewModel.update { it.copy(voiceAutoSuggestLoads = value) } },
                    )
                    SettingsSwitchItem(
                        title = "Cancelar eco en Modo Música (experimental)",
                        description = "Usa cancelación de eco en el mic del teléfono. Puede degradar la música; validado en campo.",
                        checked = settings.voiceMusicAec,
                        onCheckedChange = { value -> viewModel.update { it.copy(voiceMusicAec = value) } },
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

            item { SettingsSectionHeader("Frases de intensidad personalizadas") }
            item {
                SettingsSectionCard {
                    if (settings.voiceCustomIntensityPhrases.isEmpty()) {
                        SettingsInfoRow(
                            title = "Sin frases todavía",
                            value = "Agrega frases como \"rompiendo la barra\" para que la voz las entienda como intensidad (RPE, RIR, % o al fallo).",
                        )
                    } else {
                        settings.voiceCustomIntensityPhrases.forEachIndexed { index, phrase ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "\"${phrase.phrase}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        phraseLabel(phrase),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    viewModel.update { current ->
                                        current.copy(
                                            voiceCustomIntensityPhrases = current.voiceCustomIntensityPhrases
                                                .filterIndexed { i, _ -> i != index },
                                        )
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar frase",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (index < settings.voiceCustomIntensityPhrases.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customPhraseText,
                            onValueChange = {
                                customPhraseText = it
                                customPhraseError = null
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Nueva frase", fontSize = 12.sp) },
                            singleLine = true,
                        )
                        SettingsDropdownItem(
                            title = "",
                            options = listOf("RPE", "RIR", "%RM", "Al fallo"),
                            selected = customPhraseKind,
                            onSelect = { customPhraseKind = it },
                            optionLabel = { it },
                        )
                        if (customPhraseKind != "Al fallo") {
                            OutlinedTextField(
                                value = customPhraseValue,
                                onValueChange = { customPhraseValue = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                modifier = Modifier.width(56.dp),
                                placeholder = { Text("9", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                        IconButton(onClick = { addCustomIntensityPhrase() }) {
                            Icon(Icons.Default.Add, contentDescription = "Agregar frase")
                        }
                    }
                    customPhraseError?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
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
                        val label = catalogExerciseIndex()[exerciseId]?.name ?: exerciseId
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

    if (showVoiceModeExplainer) {
        VoiceCaptureModeDialog(
            onChosen = { mode ->
                showVoiceModeExplainer = false
                viewModel.update {
                    it.copy(voiceCaptureMode = mode, hasChosenVoiceCaptureMode = true)
                }
            },
            onDismissRequest = { showVoiceModeExplainer = false },
        )
    }
}

private fun phraseLabel(phrase: com.example.kpkn.data.models.CustomIntensityPhrase): String =
    when (phrase.kind.uppercase()) {
        "FALLO" -> "Al fallo"
        "RPE" -> "RPE ${phrase.value?.toInt() ?: "?"}"
        "RIR" -> "RIR ${phrase.value?.toInt() ?: "?"}"
        "PERCENT_RM" -> "${phrase.value?.toInt() ?: "?"}% de RM"
        else -> phrase.kind
    }

private fun resolveVoiceAliasExerciseId(query: String): String? {
    val raw = query.trim()
    if (raw.isBlank()) return null
    val normalized = normalizeAliasQuery(raw)
    resolveExerciseId(raw)?.let { return it }
    catalogExerciseIndex()[normalized]?.id?.let { return it }
    return catalogSearchExerciseId(raw)
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
