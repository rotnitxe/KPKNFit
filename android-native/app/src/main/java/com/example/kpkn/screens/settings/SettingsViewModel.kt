package com.example.kpkn.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.ApiKeys
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.services.workout.WorkoutReminderManager
import com.example.kpkn.ui.locale.LocaleManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        appContext = context.applicationContext
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
        programRepository.updateSettings { Settings() }
    }

    fun resetOnboarding() {
        programRepository.updateSettings { current ->
            current.copy(
                hasSeenWelcome = false,
                hasSeenHomeTour = false,
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
                    postSessionFeedback = augeRepository.getPostSessionFeedbacks(),
                    pendingQuestionnaire = augeRepository.getPendingQuestionnaire(),
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
    val postSessionFeedback: List<com.example.kpkn.data.models.PostSessionFeedback>,
    val pendingQuestionnaire: com.example.kpkn.data.models.PendingQuestionnaire?,
)
