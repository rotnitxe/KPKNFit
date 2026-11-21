package com.example.kpkn.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.example.kpkn.data.db.BackupTableManifest
import com.example.kpkn.data.db.DatabaseBackupHelper
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.LearnedResolutionEntity
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.data.preferences.PreferenceFiles
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.data.repository.SharedExerciseLoadReferenceRepository
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.data.secure.LegacyAiCredentialCleanup
import com.example.kpkn.data.settings.LearnedResolutionBackup
import com.example.kpkn.data.settings.SettingsExportPayload
import com.example.kpkn.data.settings.SettingsJsonBackup
import com.example.kpkn.data.work.TechniqueAnalysisScheduler
import com.example.kpkn.services.diagnostics.KpknDiagnosticStorage
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.workout.WorkoutReminderManager
import com.example.kpkn.ui.locale.LocaleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_BACKUP_JSON_BYTES = 64L * 1024L * 1024L

class SettingsViewModel : ViewModel() {
    private val programRepository = ProgramRepository.getInstance()
    private val nutritionRepository = NutritionRepository.getInstance()
    private var appContext: Context? = null
    private val storageOperationBusy = AtomicBoolean(false)
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

    /** Vacía los recorridos vistos: cada pantalla vuelve a mostrar su recorrido la próxima vez. */
    fun resetTours() {
        programRepository.updateSettings { current -> current.copy(tourSeen = emptySet()) }
    }

    fun exportData(context: Context, destination: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        launchStorageOperation(context, "Error desconocido", onError) { appContext ->
            flushRepositoriesBeforeStorageOp(appContext)
            val exportJson = PersistenceWriteCoordinator.mutex.withLock {
                com.example.kpkn.data.compatibility.LegacyTrainingBackup.export(
                    dbJson.encodeToString(buildExportPayload(appContext)),
                )
            }
            openTruncatedOutputStream(appContext, destination).use { output ->
                output.bufferedWriter(Charsets.UTF_8).use { it.write(exportJson) }
            }
            onSuccess
        }
    }

    private suspend fun buildExportPayload(context: Context): SettingsExportPayload {
        val appContext = context.applicationContext
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
            pantryItems = nutritionRepository.getPantryItemsForBackup(),
            mealTemplates = nutritionRepository.getMealTemplatesForBackup(),
            customFoods = nutritionRepository.getCustomFoodsForBackup(),
            learnedResolutions = db.learnedResolutionDao().getAll().map(::toBackup),
            foodCatalogMeta = nutritionRepository.getFoodCatalogMetaForBackup(),
            bodyObservations = bodyRepository.observations.value,
            bodyGoals = bodyRepository.goals.value,
            measurementSchedule = bodyRepository.measurementSchedule.value,
            calibrationProfile = NutritionCalibrationRepository.getInstance(appContext).get(),
            dailyGoalSnapshots = nutritionRepository.getDailyGoalSnapshots(),
            wellbeingLogs = emptyList(),
            sleepLogs = emptyList(),
            sleepLogsExtended = emptyList(),
            postSessionFeedback = emptyList(),
            sessionTemplates = SessionTemplateRepository.getInstance(appContext).userTemplates.value,
            customExercises = CustomExerciseRepository.customExercises.value,
            db = db,
        )
    }

    fun importBackupJson(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        launchStorageOperation(context, "Error al importar el JSON de respaldo", onError) { appContext ->
            val jsonString = readBoundedUtf8Text(appContext, uri)
            val payload = dbJson.decodeFromString<SettingsExportPayload>(
                com.example.kpkn.data.compatibility.LegacyTrainingBackup.normalize(jsonString),
            )
            val db = KpknDatabase.getInstance(appContext)
            flushRepositoriesBeforeStorageOp(appContext)
            withContext(NonCancellable) {
                var rollbackApplied = false
                try {
                    PersistenceWriteCoordinator.mutex.withLock {
                        val rollbackName = DatabaseBackupHelper.createSnapshot(appContext)
                        val rollbackFile = File(appContext.filesDir, "snapshots/$rollbackName")
                        try {
                            SettingsJsonBackup.importPayload(
                                context = appContext,
                                payload = payload,
                                db = db,
                                nutritionRepository = nutritionRepository,
                                onMeasurementSchedule = { schedule ->
                                    BodyProgressRepository.getInstance(appContext).updateMeasurementSchedule(schedule)
                                },
                            )
                            DatabaseBackupHelper.deleteSnapshot(rollbackFile)
                        } catch (error: Throwable) {
                            if (rollbackFile.exists()) {
                                runCatching {
                                    DatabaseBackupHelper.restoreSnapshot(
                                        appContext,
                                        rollbackFile,
                                        createSafetySnapshot = false,
                                    )
                                    DatabaseBackupHelper.deleteSnapshot(rollbackFile)
                                    rollbackApplied = true
                                }.onFailure { rollbackError ->
                                    error.addSuppressed(rollbackError)
                                }
                            }
                            throw error
                        }
                    }
                } finally {
                    if (rollbackApplied) {
                        runCatching { refreshAllRepositoriesAndAwait(appContext, db, reconcileVideoAnalysis = true) }
                    }
                }
                refreshAllRepositoriesAndAwait(appContext, db, reconcileVideoAnalysis = true)
            }
            onSuccess
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
        launchStorageOperation(context, "Error al borrar datos", onError) { appContext ->
            runCatching { WorkManager.getInstance(appContext).cancelAllWorkByTag(TechniqueAnalysisScheduler.TAG_ALL) }
            val workoutReminder = WorkoutReminderManager(appContext)
            val nutritionReminder = NutritionNotificationManager(appContext)
            workoutReminder.cancelWorkoutReminder()
            workoutReminder.cancelSleepReminder()
            nutritionReminder.cancelMealReminders()
            nutritionReminder.cancelMeasurementReminder()
            runCatching { CompetitionRepository.getInstance().cancelAllReminders() }

            flushRepositoriesBeforeStorageOp(appContext)
            val db = KpknDatabase.getInstance(appContext)
            withContext(NonCancellable) {
                PersistenceWriteCoordinator.mutex.withLock {
                    db.withTransaction {
                        val sqlite = db.openHelper.writableDatabase
                        BackupTableManifest.deleteRows(
                            sqlite,
                            BackupTableManifest.nonRegenerableTablesInDeleteOrder(),
                        )
                        sqlite.execSQL(
                            "UPDATE global_foods SET usageCount = 0, lastUsedAt = NULL WHERE usageCount <> 0 OR lastUsedAt IS NOT NULL",
                        )
                        db.settingsDao().upsert(Settings().toEntity())
                    }
                    BodyProgressRepository.getInstance(appContext).clearAllDataAndAwait()
                    LegacyAiCredentialCleanup.clear(appContext)
                    KpknDiagnosticStorage.clear(appContext)

                    appContext.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE)
                        .edit()
                        .remove("favorite_food_ids")
                        .remove("food_query_learning_v2")
                        .commit()
                    listOf(
                        "nutrition_utensils",
                        PreferenceFiles.PROGRAM_SNAPSHOTS,
                        PreferenceFiles.VARIANT_PREFERENCES,
                        PreferenceFiles.MONTHLY_SUMMARY,
                        "workout_prefs",
                        "workout_live_experience",
                        "kpkn_permission_ui_prefs",
                        "aj_permissions",
                        "workout_voice_first_use_v2",
                    ).forEach { prefName ->
                        appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
                    }

                    ProfilePhotoStore.delete(appContext)
                    listOf(
                        File(appContext.filesDir, "workout_media"),
                        File(appContext.filesDir, "workout_media_pending"),
                        File(appContext.filesDir, "workout_photos"),
                        File(appContext.filesDir, "exercise_user_media"),
                        File(appContext.filesDir, "analysis_requests"),
                        File(appContext.filesDir, "analysis_publications"),
                        File(appContext.filesDir, "snapshots"),
                        File(appContext.filesDir, "quarantine"),
                        File(appContext.filesDir, "kpkn_logs"),
                        File(appContext.filesDir, "nutrition_telemetry"),
                        File(appContext.cacheDir, "exports"),
                        File(appContext.filesDir, "import-source-evidence"),
                        File(appContext.cacheDir, "import_photos"),
                    ).forEach { dir ->
                        runCatching { dir.deleteRecursively() }
                    }

                    appContext.filesDir.listFiles { file -> file.name.startsWith("imported-program-editor-v1.json") }
                        ?.forEach { file -> file.delete() }

                    SettingsJsonBackup.ensureRegenerableAssetsIfEmpty(appContext, db) { meta ->
                        nutritionRepository.restoreFoodCatalogMeta(
                            NutritionRepository.FoodCatalogMeta(
                                version = meta.version,
                                checksum = meta.checksum,
                                importedAt = meta.importedAt,
                            ),
                        )
                    }
                }
                refreshAllRepositoriesAndAwait(appContext, db, reconcileVideoAnalysis = false)
            }
            onSuccess
        }
    }

    fun createSnapshot(context: Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        launchStorageOperation(context, "Error al crear snapshot", onError) { appContext ->
            flushRepositoriesBeforeStorageOp(appContext)
            val filename = PersistenceWriteCoordinator.mutex.withLock {
                DatabaseBackupHelper.createSnapshot(appContext)
            }
            return@launchStorageOperation { onSuccess(filename) }
        }
    }

    fun restoreSnapshot(context: Context, file: File, onSuccess: () -> Unit, onError: (String) -> Unit) {
        launchStorageOperation(context, "Error al restaurar el snapshot", onError) { appContext ->
            val restoreContext = currentCoroutineContext()
            val db = KpknDatabase.getInstance(appContext)
            flushRepositoriesBeforeStorageOp(appContext)
            PersistenceWriteCoordinator.mutex.withLock {
                DatabaseBackupHelper.restoreSnapshot(appContext, file) { !restoreContext.isActive }
            }
            refreshAllRepositoriesAndAwait(appContext, db, reconcileVideoAnalysis = true)
            onSuccess
        }
    }

    fun exportWorkoutMediaZip(
        context: Context,
        destination: Uri,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit,
    ) {
        launchStorageOperation(context, "Error desconocido", onError) { appContext ->
            val count = openTruncatedOutputStream(appContext, destination).use { output ->
                com.example.kpkn.data.media.WorkoutMediaZipExporter.writeZip(appContext.filesDir, output)
            }
            return@launchStorageOperation { onSuccess(count) }
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
        launchStorageOperation(context, "Error desconocido", onError) { appContext ->
            flushRepositoriesBeforeStorageOp(appContext)
            PersistenceWriteCoordinator.mutex.withLock {
                val snapshotName = DatabaseBackupHelper.createSnapshot(appContext)
                val snapshotFile = File(appContext.filesDir, "snapshots/$snapshotName")
                try {
                    openTruncatedOutputStream(appContext, destination).use { output ->
                        snapshotFile.inputStream().use { input -> input.copyTo(output) }
                    }
                } finally {
                    DatabaseBackupHelper.deleteSnapshot(snapshotFile)
                }
            }
            onSuccess
        }
    }

    fun importDatabaseSnapshot(
        context: Context,
        uri: Uri,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        launchStorageOperation(context, "Error al importar snapshot", onError) { appContext ->
            val tempImport = File(appContext.cacheDir, "import_snapshot_${System.currentTimeMillis()}.db")
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    tempImport.outputStream().use { output -> input.copyTo(output) }
                } ?: error("No se pudo leer el archivo")
                val db = KpknDatabase.getInstance(appContext)
                flushRepositoriesBeforeStorageOp(appContext)
                withContext(NonCancellable) {
                    PersistenceWriteCoordinator.mutex.withLock {
                        // restoreSnapshot validates integrity before creating one safety snapshot and replacing rows atomically.
                        DatabaseBackupHelper.restoreSnapshot(appContext, tempImport)
                    }
                    SettingsJsonBackup.ensureRegenerableAssetsIfEmpty(appContext, db) { meta ->
                        nutritionRepository.restoreFoodCatalogMeta(
                            NutritionRepository.FoodCatalogMeta(
                                version = meta.version,
                                checksum = meta.checksum,
                                importedAt = meta.importedAt,
                            ),
                        )
                    }
                    refreshAllRepositoriesAndAwait(appContext, db, reconcileVideoAnalysis = true)
                }
            } finally {
                tempImport.delete()
            }
            onSuccess
        }
    }

    private fun launchStorageOperation(
        context: Context,
        fallbackErrorMessage: String,
        onError: (String) -> Unit,
        block: suspend (appContext: Context) -> (() -> Unit),
    ) {
        if (!storageOperationBusy.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                onError("Ya hay una operación de almacenamiento en curso.")
            }
            return
        }
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            var mainCallback: (() -> Unit)? = null
            var failureMessage: String? = null
            try {
                mainCallback = block(appContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failureMessage = error.message ?: fallbackErrorMessage
            } finally {
                storageOperationBusy.set(false)
            }
            if (failureMessage != null) {
                withContext(Dispatchers.Main) { onError(failureMessage) }
            } else {
                mainCallback?.let { cb -> withContext(Dispatchers.Main) { cb() } }
            }
        }
    }

    private suspend fun flushRepositoriesBeforeStorageOp(appContext: Context) {
        programRepository.flushPendingWrites()
        BodyProgressRepository.getInstance(appContext).awaitPersistence()
        runCatching { CompetitionRepository.getInstance().awaitPendingWrites() }
        runCatching { SharedExerciseLoadReferenceRepository.getInstance(appContext).awaitPendingWrites() }
    }

    private suspend fun refreshAllRepositoriesAndAwait(
        appContext: Context,
        db: KpknDatabase,
        reconcileVideoAnalysis: Boolean,
    ) {
        programRepository.refreshDataAndAwait()
        nutritionRepository.refreshDataAndAwait(appContext)
        BodyProgressRepository.getInstance(appContext).refreshFromStorage()
        runCatching { CompetitionRepository.getInstance().refreshFromStorage() }
        SessionTemplateRepository.getInstance(appContext).refreshFromStorage()
        CustomExerciseRepository.refreshFromStorage()
        SharedExerciseLoadReferenceRepository.getInstance(appContext).resetFromStorage()
        WikiLabRepository.refreshFromStorage(appContext, db)
        if (reconcileVideoAnalysis) {
            runCatching { TechniqueAnalysisScheduler.reconcile(appContext) }
        }
    }

    private fun openTruncatedOutputStream(context: Context, uri: Uri): OutputStream {
        val resolver = context.contentResolver
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri)
        } catch (_: UnsupportedOperationException) {
            resolver.openOutputStream(uri)
        }
        return stream ?: error("No se pudo abrir el archivo de destino")
    }

    private fun readBoundedUtf8Text(
        context: Context,
        uri: Uri,
        maxBytes: Long = MAX_BACKUP_JSON_BYTES,
    ): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo leer el archivo")
        return input.use { stream ->
            val buffer = ByteArray(16_384)
            val output = ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException("El archivo supera el tamaño máximo permitido (64 MB).")
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }
}
