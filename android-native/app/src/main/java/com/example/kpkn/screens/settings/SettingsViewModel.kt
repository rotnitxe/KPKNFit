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
import com.example.kpkn.data.db.LearnedResolutionEntity
import com.example.kpkn.data.db.toMealTemplate
import com.example.kpkn.data.db.toPantryItem
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.kpkn.data.models.ApiKeys
import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.secure.LegacyAiCredentialCleanup
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import com.example.kpkn.services.workout.WorkoutReminderManager
import com.example.kpkn.ui.locale.LocaleManager
import com.example.kpkn.domain.body.validateBodyValue
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
        return SettingsExportPayload(
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            // Never carry a legacy provider URI in a portable backup. The token
            // is emitted only when the private JPEG was actually read.
            settings = programRepository.settings.value.copy(
                apiKeys = ApiKeys(),
                profilePicture = ProfilePhotoStore.STORAGE_TOKEN.takeIf { profilePhotoJpegBase64 != null },
            ),
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
                require(payload.schemaVersion in 1..EXPORT_SCHEMA_VERSION) {
                    "Formato de exportación no compatible: ${payload.schemaVersion}"
                }
                val db = KpknDatabase.getInstance(context)
                if (payload.profilePhotoJpegBase64 == null) {
                    ProfilePhotoStore.delete(context.applicationContext)
                }
                val restoredPhotoToken = payload.profilePhotoJpegBase64?.let {
                    ProfilePhotoStore.saveBase64(context.applicationContext, it)
                }

                db.withTransaction {
                    // Import is a restore, not a merge: stale rows must not survive a backup restore.
                    db.clearAllTables()
                    db.settingsDao().upsert(
                        payload.settings.copy(
                            apiProvider = ApiProvider.LOCAL,
                            apiKeys = ApiKeys(),
                            profilePicture = restoredPhotoToken,
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
                    payload.customFoods.forEach { db.nutritionDao().upsertCustomFood(it.toEntity()) }
                    payload.learnedResolutions.forEach { db.learnedResolutionDao().upsert(it.toEntity()) }
                    payload.bodyObservations
                        .filter { validateBodyValue(it.metric, it.valueSi).valid }
                        .forEach { db.bodyProgressDao().upsertObservation(it.toEntity()) }
                    payload.bodyGoals
                        .filter { it.targetValueSi.isFinite() }
                        .forEach { db.bodyProgressDao().upsertGoal(it.toEntity()) }
                    payload.dailyGoalSnapshots.forEach {
                        db.nutritionDao().insertDailyGoalSnapshot(it.toEntity())
                    }
                }

                val bodyRepository = BodyProgressRepository.getInstance(context.applicationContext)
                bodyRepository.refreshFromStorage()
                bodyRepository.updateMeasurementSchedule(payload.measurementSchedule ?: MeasurementSchedule())
                val calibrationRepository = NutritionCalibrationRepository.getInstance(context.applicationContext)
                if (payload.calibrationProfile != null) calibrationRepository.save(payload.calibrationProfile) else calibrationRepository.clear()
                if (payload.foodCatalogMeta != null) {
                    nutritionRepository.restoreFoodCatalogMeta(payload.foodCatalogMeta)
                } else {
                    context.applicationContext.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE).edit().clear().commit()
                }

                val augeRepository = AugeRepository.getInstance(context.applicationContext)
                augeRepository.importBackupSlice(
                    wellbeingLogs = payload.wellbeingLogs,
                    sleepLogs = payload.sleepLogs,
                    sleepLogsExtended = payload.sleepLogsExtended,
                    postSessionFeedback = payload.postSessionFeedback,
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
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val settings: Settings,
    val profilePhotoJpegBase64: String? = null,
    val programs: List<com.example.kpkn.data.models.Program>,
    val workoutLogs: List<com.example.kpkn.data.models.WorkoutLog>,
    val activeProgramState: com.example.kpkn.data.models.ActiveProgramState?,
    val ongoingWorkout: com.example.kpkn.data.models.OngoingWorkoutState?,
    val nutritionLogs: List<com.example.kpkn.data.models.NutritionLog>,
    val nutritionPlans: List<com.example.kpkn.data.models.NutritionPlan>,
    val activeNutritionPlanId: String?,
    val pantryItems: List<com.example.kpkn.data.models.PantryItem>,
    val mealTemplates: List<com.example.kpkn.data.models.MealTemplate>,
    val customFoods: List<com.example.kpkn.data.models.FoodItem> = emptyList(),
    val learnedResolutions: List<LearnedResolutionBackup> = emptyList(),
    val foodCatalogMeta: NutritionRepository.FoodCatalogMeta? = null,
    val bodyObservations: List<com.example.kpkn.data.models.BodyObservation> = emptyList(),
    val bodyGoals: List<com.example.kpkn.data.models.BodyGoal> = emptyList(),
    val measurementSchedule: com.example.kpkn.data.models.MeasurementSchedule? = null,
    val calibrationProfile: NutritionCalibrationProfile? = null,
    val dailyGoalSnapshots: List<com.example.kpkn.data.models.DailyGoalSnapshot> = emptyList(),
    val wellbeingLogs: List<com.example.kpkn.data.models.DailyWellbeingLog>,
    val sleepLogs: List<com.example.kpkn.data.models.SleepLog>,
    val sleepLogsExtended: List<com.example.kpkn.data.models.SleepLogExtended> = emptyList(),
    val postSessionFeedback: List<com.example.kpkn.data.models.PostSessionFeedback>,
    val adaptiveCache: com.example.kpkn.data.models.AugeAdaptiveCache? = null,
)

@Serializable
private data class LearnedResolutionBackup(
    val id: String,
    val queryKey: String,
    val foodId: String,
    val portionGrams: Double? = null,
    val cookingMethod: String? = null,
    val count: Int = 1,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = 0L,
    val syncedAt: Long? = null,
    val weightBasis: String? = null,
    val portionMinGrams: Double? = null,
    val portionMaxGrams: Double? = null,
    val preparation: String? = null,
    val oilProfile: String? = null,
    val confidence: Double = 1.0,
    val lastConfirmedAt: Long = 0L,
) {
    fun toEntity() = LearnedResolutionEntity(
        id = id,
        queryKey = queryKey,
        foodId = foodId,
        portionGrams = portionGrams,
        cookingMethod = cookingMethod,
        count = count,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        syncedAt = syncedAt,
        weightBasis = weightBasis,
        portionMinGrams = portionMinGrams,
        portionMaxGrams = portionMaxGrams,
        preparation = preparation,
        oilProfile = oilProfile,
        confidence = confidence,
        lastConfirmedAt = lastConfirmedAt,
    )
}

private const val EXPORT_SCHEMA_VERSION = 3
