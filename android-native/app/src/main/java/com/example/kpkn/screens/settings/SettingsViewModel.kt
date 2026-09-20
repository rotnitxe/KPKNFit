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
import com.example.kpkn.data.db.LearnedResolutionEntity
import com.example.kpkn.data.db.toMealTemplate
import com.example.kpkn.data.db.toPantryItem
import com.example.kpkn.data.settings.LearnedResolutionBackup
import com.example.kpkn.data.settings.SettingsExportPayload
import com.example.kpkn.data.settings.SettingsJsonBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.secure.LegacyAiCredentialCleanup
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import com.example.kpkn.services.workout.WorkoutReminderManager
import com.example.kpkn.ui.locale.LocaleManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SettingsViewModel : ViewModel() {
    private val programRepository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()
    private var appContext: Context? = null
    val settings: StateFlow<Settings> = programRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setContext(context: Context) {
        val appContext = context.applicationContext
        this.appContext = appContext
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
        appContext?.let(LegacyAiCredentialCleanup::clear)
        programRepository.updateSettings { Settings() }
    }

    fun resetOnboarding() {
        programRepository.updateSettings { current ->
            current.copy(
                hasSeenWelcome = false,
                hasSeenHomeTour = false,
                hasSeenWorkoutTagEducation = false,
                hasChosenVoiceCaptureMode = false,
            )
        }
    }

    /** Legacy share entry point kept for old callers; the new Settings UI uses SAF below. */
    fun exportData(context: Context) {
        viewModelScope.launch {
            runCatching {
                val payload = buildExportPayload(context)
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

    fun exportData(context: Context, destination: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val exportJson = dbJson.encodeToString(buildExportPayload(context))
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(exportJson) }
                } ?: error("No se pudo abrir el archivo de destino")
            }.onSuccess {
                withContext(Dispatchers.Main) { onSuccess() }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onError(error.message ?: "Error desconocido") }
            }
        }
    }

    private suspend fun buildExportPayload(context: Context): SettingsExportPayload {
        val appContext = context.applicationContext
        val augeRepository = AugeRepository.getInstance(appContext)
        val bodyRepository = BodyProgressRepository.getInstance(appContext)
        bodyRepository.awaitReady()
        val db = KpknDatabase.getInstance(appContext)
        val profilePhotoJpegBase64 = ProfilePhotoStore.readBase64(
            appContext,
            programRepository.settings.value.profilePicture,
        )
        return SettingsJsonBackup.buildPayload(
            context = appContext,
            settings = programRepository.settings.value,
            profilePhotoJpegBase64 = profilePhotoJpegBase64,
            programs = programRepository.programs.value,
            workoutLogs = programRepository.history.value,
            activeProgramState = programRepository.activeProgramState.value,
            ongoingWorkout = programRepository.ongoingWorkout.value,
            nutritionLogs = nutritionRepository.nutritionLogs.value,
            nutritionPlans = nutritionRepository.nutritionPlans.value,
            activeNutritionPlanId = nutritionRepository.activeNutritionPlanId.value,
            pantryItems = db.nutritionDao().getAllPantryItems().map { it.toPantryItem() },
            mealTemplates = db.nutritionDao().getAllTemplates().map { it.toMealTemplate() },
            customFoods = nutritionRepository.getCustomFoodsForBackup(),
            learnedResolutions = db.learnedResolutionDao().getAll().map(::toBackup),
            foodCatalogMeta = nutritionRepository.getFoodCatalogMetaForBackup(),
            bodyObservations = bodyRepository.observations.value,
            bodyGoals = bodyRepository.goals.value,
            measurementSchedule = bodyRepository.measurementSchedule.value,
            calibrationProfile = NutritionCalibrationRepository.getInstance(appContext).get(),
            dailyGoalSnapshots = nutritionRepository.getDailyGoalSnapshots(),
            wellbeingLogs = augeRepository.getWellbeingLogs(),
            sleepLogs = augeRepository.getLastNSleepLogs(30),
            sleepLogsExtended = augeRepository.getAllSleepLogsExtended(),
            postSessionFeedback = augeRepository.getPostSessionFeedbacks(),
            adaptiveCache = augeRepository.getAdaptiveCache(),
            sessionTemplates = SessionTemplateRepository.getInstance(appContext).userTemplates.value,
            customExercises = CustomExerciseRepository.customExercises.value,
            db = db,
        )
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
                val rollbackName = DatabaseBackupHelper.createSnapshot(context)
                val rollbackFile = File(context.filesDir, "snapshots/$rollbackName")
                try {
                    SettingsJsonBackup.importPayload(
                        context = context,
                        payload = payload,
                        db = db,
                        nutritionRepository = nutritionRepository,
                        onMeasurementSchedule = { schedule ->
                            val bodyRepository = BodyProgressRepository.getInstance(context.applicationContext)
                            bodyRepository.refreshFromStorage()
                            bodyRepository.updateMeasurementSchedule(schedule)
                            val calibrationRepository = NutritionCalibrationRepository.getInstance(context.applicationContext)
                            if (payload.calibrationProfile != null) {
                                calibrationRepository.save(payload.calibrationProfile)
                            } else if (payload.schemaVersion >= SettingsJsonBackup.EXPORT_SCHEMA_VERSION && payload.includesCalibrationSection) {
                                calibrationRepository.clear()
                            } else if (payload.schemaVersion < SettingsJsonBackup.EXPORT_SCHEMA_VERSION && payload.calibrationProfile == null) {
                                calibrationRepository.clear()
                            }
                        },
                    )
                    DatabaseBackupHelper.deleteSnapshot(rollbackFile)
                } catch (error: Throwable) {
                    if (rollbackFile.exists()) {
                        DatabaseBackupHelper.restoreSnapshot(context, rollbackFile)
                        programRepository.refreshData()
                        nutritionRepository.refreshData(context)
                        SessionTemplateRepository.getInstance(context.applicationContext).refreshFromStorage()
                        CustomExerciseRepository.refreshFromStorage()
                        DatabaseBackupHelper.deleteSnapshot(rollbackFile)
                    }
                    throw error
                }

                programRepository.refreshData()
                nutritionRepository.refreshData(context)
                SessionTemplateRepository.getInstance(context.applicationContext).refreshFromStorage()
                CustomExerciseRepository.refreshFromStorage()
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

    private fun toBackup(entity: LearnedResolutionEntity): LearnedResolutionBackup =
        LearnedResolutionBackup(
            id = entity.id,
            queryKey = entity.queryKey,
            foodId = entity.foodId,
            portionGrams = entity.portionGrams,
            cookingMethod = entity.cookingMethod,
            count = entity.count,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt,
            syncedAt = entity.syncedAt,
            weightBasis = entity.weightBasis,
            portionMinGrams = entity.portionMinGrams,
            portionMaxGrams = entity.portionMaxGrams,
            preparation = entity.preparation,
            oilProfile = entity.oilProfile,
            confidence = entity.confidence,
            lastConfirmedAt = entity.lastConfirmedAt,
        )

    fun clearAllAppData(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val db = KpknDatabase.getInstance(context)
                db.clearAllTables()
                LegacyAiCredentialCleanup.clear(context)

                context.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE).edit().clear().commit()
                BodyProgressRepository.getInstance(context.applicationContext).clearAllDataAndAwait()
                ProfilePhotoStore.delete(context.applicationContext)
                KpknDiagnosticStorage.clear(context.applicationContext)
                File(context.applicationContext.filesDir, "kpkn_logs").deleteRecursively()
                File(context.applicationContext.filesDir, "nutrition_telemetry").deleteRecursively()

                programRepository.updateSettings { Settings() }
                programRepository.refreshData()
                nutritionRepository.refreshData(context)
                SessionTemplateRepository.getInstance(context.applicationContext).refreshFromStorage()
                CustomExerciseRepository.refreshFromStorage()

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
                SessionTemplateRepository.getInstance(context.applicationContext).refreshFromStorage()
                CustomExerciseRepository.refreshFromStorage()
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

    fun exportWorkoutMediaZip(
        context: Context,
        destination: Uri,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    com.example.kpkn.data.media.WorkoutMediaZipExporter.writeZip(context.filesDir, output)
                } ?: error("No se pudo abrir el archivo de destino")
            }.onSuccess { count ->
                withContext(Dispatchers.Main) { onSuccess(count) }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onError(error.message ?: "Error desconocido") }
            }
        }
    }

    fun getSnapshots(context: Context): List<File> {
        return DatabaseBackupHelper.listSnapshots(context)
    }

    fun deleteSnapshot(file: File): Boolean {
        return DatabaseBackupHelper.deleteSnapshot(file)
    }

    fun exportDatabaseSnapshot(
        context: Context,
        destination: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snapshotName = DatabaseBackupHelper.createSnapshot(context)
                val snapshotFile = File(context.filesDir, "snapshots/$snapshotName")
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    snapshotFile.inputStream().use { input -> input.copyTo(output) }
                } ?: error("No se pudo abrir el archivo de destino")
                DatabaseBackupHelper.deleteSnapshot(snapshotFile)
            }.onSuccess {
                withContext(Dispatchers.Main) { onSuccess() }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onError(error.message ?: "Error desconocido") }
            }
        }
    }

    fun importDatabaseSnapshot(
        context: Context,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val rollbackName = DatabaseBackupHelper.createSnapshot(context)
                val rollbackFile = File(context.filesDir, "snapshots/$rollbackName")
                val tempImport = File(context.cacheDir, "import_snapshot_${System.currentTimeMillis()}.db")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempImport.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("No se pudo leer el archivo")
                    DatabaseBackupHelper.restoreSnapshot(context, tempImport)
                    programRepository.refreshData()
                    nutritionRepository.refreshData(context)
                    SessionTemplateRepository.getInstance(context.applicationContext).refreshFromStorage()
                    CustomExerciseRepository.refreshFromStorage()
                    DatabaseBackupHelper.deleteSnapshot(rollbackFile)
                } catch (error: Throwable) {
                    if (rollbackFile.exists()) {
                        DatabaseBackupHelper.restoreSnapshot(context, rollbackFile)
                        programRepository.refreshData()
                        nutritionRepository.refreshData(context)
                        SessionTemplateRepository.getInstance(context.applicationContext).refreshFromStorage()
                        CustomExerciseRepository.refreshFromStorage()
                        DatabaseBackupHelper.deleteSnapshot(rollbackFile)
                    }
                    throw error
                } finally {
                    tempImport.delete()
                }
            }.onSuccess {
                withContext(Dispatchers.Main) { onSuccess() }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onError(error.message ?: "Error al importar snapshot") }
            }
        }
    }
}
