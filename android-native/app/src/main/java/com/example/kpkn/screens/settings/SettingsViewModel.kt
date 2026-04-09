package com.example.kpkn.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
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

    val settings: StateFlow<Settings> = programRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun update(transform: (Settings) -> Settings) {
        programRepository.updateSettings(transform)
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
                    settings = programRepository.settings.value,
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
                val exportFile = File(context.cacheDir, fileName)
                exportFile.writeText(exportJson)
                shareExportFile(context, exportFile)
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "No se pudo exportar: ${error.message ?: "error desconocido"}",
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
            Intent.createChooser(intent, "Exportar datos de KPKN").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Toast.makeText(context, "Datos preparados para exportar", Toast.LENGTH_SHORT).show()
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
