package com.example.kpkn.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.DatabaseBackupHelper
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.NutritionActiveStateEntity
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.kpkn.data.models.ApiKeys
import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.secure.DeepSeekCredentialStore
import com.example.kpkn.data.secure.DeepSeekSettingsMigration
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.diagnostics.ReportEnrichmentScheduler
import com.example.kpkn.services.workout.WorkoutReminderManager
import com.example.kpkn.ui.locale.LocaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SettingsViewModel : ViewModel() {
    private val programRepository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()
    private var appContext: Context? = null
    private val _deepSeekKey = MutableStateFlow<String?>(null)
    val deepSeekKey: StateFlow<String?> = _deepSeekKey.asStateFlow()

    val settings: StateFlow<Settings> = programRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setContext(context: Context) {
        val appContext = context.applicationContext
        this.appContext = appContext
        loadDeepSeekKey(appContext)
    }

    fun loadDeepSeekKey(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            DeepSeekSettingsMigration.migrate(context)
            _deepSeekKey.value = DeepSeekCredentialStore.read(context)
        }
    }

    fun saveDeepSeekKey(context: Context, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = value.trim()
            if (trimmed.isBlank()) DeepSeekCredentialStore.clear(context) else DeepSeekCredentialStore.write(context, trimmed)
            programRepository.updateSettings {
                it.copy(apiProvider = ApiProvider.DEEPSEEK, apiKeys = ApiKeys())
            }
            _deepSeekKey.value = trimmed.takeIf { it.isNotBlank() }
            ReportEnrichmentScheduler.resumePending(context)
        }
    }

    fun update(transform: (Settings) -> Settings) {
        val oldSettings = programRepository.settings.value
        val newSettings = transform(oldSettings)
        programRepository.updateSettings { newSettings }

        // Handle reminder scheduling changes
        appContext?.let { ctx ->
            handleReminderChanges(ctx, oldSettings, newSettings)
            // Handle language changes
            if (oldSettings.appLanguage != newSettings.appLanguage) {
                LocaleManager.applyAndPersist(ctx, newSettings.appLanguage)
            }
        }
    }

    private fun handleReminderChanges(context: Context, oldSettings: Settings, newSettings: Settings) {
        val workoutReminder = WorkoutReminderManager(context)
        val nutritionReminder = NutritionNotificationManager(context)

        // Workout reminder
        if (oldSettings.workoutReminderEnabled != newSettings.workoutReminderEnabled) {
            if (newSettings.workoutReminderEnabled) {
                workoutReminder.scheduleWorkoutReminder(newSettings.workoutReminderTime)
            } else {
                workoutReminder.cancelWorkoutReminder()
            }
        } else if (newSettings.workoutReminderEnabled && oldSettings.workoutReminderTime != newSettings.workoutReminderTime) {
            // Time changed
            workoutReminder.cancelWorkoutReminder()
            workoutReminder.scheduleWorkoutReminder(newSettings.workoutReminderTime)
        }

        // Sleep reminder
        if (oldSettings.sleepReminderEnabled != newSettings.sleepReminderEnabled) {
            if (newSettings.sleepReminderEnabled) {
                workoutReminder.scheduleSleepReminder(newSettings.sleepReminderTime)
            } else {
                workoutReminder.cancelSleepReminder()
            }
        } else if (newSettings.sleepReminderEnabled && oldSettings.sleepReminderTime != newSettings.sleepReminderTime) {
            // Time changed
            workoutReminder.cancelSleepReminder()
            workoutReminder.scheduleSleepReminder(newSettings.sleepReminderTime)
        }

        // Meal reminders
        if (oldSettings.mealReminderEnabled != newSettings.mealReminderEnabled) {
            if (newSettings.mealReminderEnabled) {
                nutritionReminder.scheduleMealReminders(
                    newSettings.mealReminderBreakfast,
                    newSettings.mealReminderLunch,
                    newSettings.mealReminderDinner,
                )
            } else {
                nutritionReminder.cancelMealReminders()
            }
        } else if (newSettings.mealReminderEnabled) {
            // Check if any meal time changed
            val breakfastChanged = oldSettings.mealReminderBreakfast != newSettings.mealReminderBreakfast
            val lunchChanged = oldSettings.mealReminderLunch != newSettings.mealReminderLunch
            val dinnerChanged = oldSettings.mealReminderDinner != newSettings.mealReminderDinner

            if (breakfastChanged || lunchChanged || dinnerChanged) {
                nutritionReminder.cancelMealReminders()
                nutritionReminder.scheduleMealReminders(
                    newSettings.mealReminderBreakfast,
                    newSettings.mealReminderLunch,
                    newSettings.mealReminderDinner,
                )
            }
        }
    }

    fun resetSettings() {
        appContext?.let(DeepSeekCredentialStore::clear)
        programRepository.updateSettings { Settings() }
        _deepSeekKey.value = null
    }

    fun resetOnboarding() {
        programRepository.updateSettings { current ->
            current.copy(
                hasSeenWelcome = false,
                hasSeenHomeTour = false,
                hasChosenVoiceCaptureMode = false,
            )
        }
    }

    fun exportData(context: Context) {
        viewModelScope.launch {
            runCatching {
                val augeRepository = AugeRepository.getInstance(context.applicationContext)
                val payload = SettingsExportPayload(
                    exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    settings = programRepository.settings.value.copy(apiKeys = ApiKeys()),
                    programs = programRepository.programs.value,
                    workoutLogs = programRepository.history.value,
                    activeProgramState = programRepository.activeProgramState.value,
                    ongoingWorkout = programRepository.ongoingWorkout.value,
                    nutritionLogs = nutritionRepository.nutritionLogs.value,
                    nutritionPlans = nutritionRepository.nutritionPlans.value,
                    activeNutritionPlanId = nutritionRepository.activeNutritionPlanId.value,
                    pantryItems = emptyList(),
                    mealTemplates = emptyList(),
                    wellbeingLogs = augeRepository.getWellbeingLogs(),
                    sleepLogs = augeRepository.getLastNSleepLogs(30),
                    sleepLogsExtended = augeRepository.getAllSleepLogsExtended(),
                    postSessionFeedback = augeRepository.getPostSessionFeedbacks(),
                    pendingQuestionnaire = augeRepository.getPendingQuestionnaire(),
                    adaptiveCache = augeRepository.getAdaptiveCache(),
                )
                val exportJson = dbJson.encodeToString(payload)
                val fileName = "kpkn-export-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json"
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val exportFile = File(exportDir, fileName)
                exportFile.writeText(exportJson)
                shareExportFile(context, exportFile)
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    context.getString(com.example.kpkn.R.string.msg_export_failed, error.message ?: context.getString(com.example.kpkn.R.string.common_error_unknown)),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun shareExportFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(com.example.kpkn.R.string.title_export_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Toast.makeText(context, context.getString(com.example.kpkn.R.string.msg_export_ready), Toast.LENGTH_SHORT).show()
    }

    fun importBackupJson(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("No se pudo leer el archivo")

                val payload = dbJson.decodeFromString<SettingsExportPayload>(jsonString)
                val db = KpknDatabase.getInstance(context)

                db.withTransaction {
                    db.clearAllTables()
                    db.settingsDao().upsert(
                        payload.settings.copy(
                            apiProvider = ApiProvider.DEEPSEEK,
                            apiKeys = ApiKeys(),
                        ).toEntity(),
                    )
                    payload.programs.forEach { db.programDao().upsert(it.toEntity()) }
                    payload.workoutLogs.forEach { db.workoutLogDao().insert(it.toEntity()) }
                    payload.activeProgramState?.let { db.stateDao().upsertActiveProgram(it.toEntity()) }
                    payload.ongoingWorkout?.let { db.stateDao().upsertOngoingWorkout(it.toEntity()) }
                    payload.nutritionLogs.forEach { db.nutritionDao().upsertLog(it.toEntity()) }
                    payload.nutritionPlans.forEach { db.nutritionDao().upsertPlan(it.toEntity()) }
                    payload.activeNutritionPlanId?.let {
                        db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = it))
                    }
                    payload.pantryItems.forEach { db.nutritionDao().upsertPantryItem(it.toEntity()) }
                    payload.mealTemplates.forEach { db.nutritionDao().upsertTemplate(it.toEntity()) }
                }

                val augeRepository = AugeRepository.getInstance(context.applicationContext)
                augeRepository.importBackupSlice(
                    wellbeingLogs = payload.wellbeingLogs,
                    sleepLogs = payload.sleepLogs,
                    sleepLogsExtended = payload.sleepLogsExtended,
                    postSessionFeedback = payload.postSessionFeedback,
                    pendingQuestionnaire = payload.pendingQuestionnaire,
                    adaptiveCache = payload.adaptiveCache,
                )

                programRepository.refreshData()
                nutritionRepository.refreshData(context)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onError(error.message ?: "Error al importar el JSON de respaldo")
                }
            }
        }
    }

    fun clearAllAppData(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val db = KpknDatabase.getInstance(context)
                db.clearAllTables()
                DeepSeekCredentialStore.clear(context)
                _deepSeekKey.value = null

                context.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE).edit().clear().commit()
                context.getSharedPreferences("measurements_prefs", Context.MODE_PRIVATE).edit().clear().commit()

                programRepository.updateSettings { Settings() }
                programRepository.refreshData()
                nutritionRepository.refreshData(context)

                val workoutReminder = WorkoutReminderManager(context)
                val nutritionReminder = NutritionNotificationManager(context)
                workoutReminder.cancelWorkoutReminder()
                workoutReminder.cancelSleepReminder()
                nutritionReminder.cancelMealReminders()
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onError(error.message ?: "Error al borrar datos")
                }
            }
        }
    }

    fun createSnapshot(context: Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                DatabaseBackupHelper.createSnapshot(context)
            }.onSuccess { filename ->
                withContext(Dispatchers.Main) {
                    onSuccess(filename)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onError(error.message ?: "Error al crear snapshot")
                }
            }
        }
    }

    fun restoreSnapshot(context: Context, file: File, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                DatabaseBackupHelper.restoreSnapshot(context, file)
                programRepository.refreshData()
                nutritionRepository.refreshData(context)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onError(error.message ?: "Error al restaurar el snapshot")
                }
            }
        }
    }

    fun getSnapshots(context: Context): List<File> {
        return DatabaseBackupHelper.listSnapshots(context)
    }

    fun deleteSnapshot(file: File): Boolean {
        return DatabaseBackupHelper.deleteSnapshot(file)
    }
}

@Serializable
private data class SettingsExportPayload(
    val exportedAt: String,
    val settings: Settings,
    val programs: List<com.example.kpkn.data.models.Program>,
    val workoutLogs: List<com.example.kpkn.data.models.WorkoutLog>,
    val activeProgramState: com.example.kpkn.data.models.ActiveProgramState?,
    val ongoingWorkout: com.example.kpkn.data.models.OngoingWorkoutState?,
    val nutritionLogs: List<com.example.kpkn.data.models.NutritionLog>,
    val nutritionPlans: List<com.example.kpkn.data.models.NutritionPlan>,
    val activeNutritionPlanId: String?,
    val pantryItems: List<com.example.kpkn.data.models.PantryItem>,
    val mealTemplates: List<com.example.kpkn.data.models.MealTemplate>,
    val wellbeingLogs: List<com.example.kpkn.data.models.DailyWellbeingLog>,
    val sleepLogs: List<com.example.kpkn.data.models.SleepLog>,
    val sleepLogsExtended: List<com.example.kpkn.data.models.SleepLogExtended> = emptyList(),
    val postSessionFeedback: List<com.example.kpkn.data.models.PostSessionFeedback>,
    val pendingQuestionnaire: com.example.kpkn.data.models.PendingQuestionnaire?,
    val adaptiveCache: com.example.kpkn.data.models.AugeAdaptiveCache? = null,
)
