package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.InitialRecoveryAxialExposure
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.models.VolumeCalibrationProfile
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.screens.nutrition.NutritionWizardDraft
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class SetupWizardMode { FULL, TRAINING_ONLY, NUTRITION_ONLY, RINGS_ONLY, RESUME }

@Serializable
enum class SetupWizardChapter { PROFILE, VOLUME, TRAINING, WEEK, NUTRITION, RINGS, REVIEW }

@Serializable
enum class SetupModuleChoice { TRAINING, TRAINING_AND_NUTRITION }

@Serializable
enum class SetupProgramRoute { CUSTOMIZABLE, PROTOCOL, LATER }

@Serializable
enum class SetupRecentTrainingState { NOT_ANSWERED, NO, YES, UNKNOWN }

@Serializable
data class SetupVolumeAnswers(
    val style: TrainingStyle? = null,
    val technique: Int? = null,
    val consistency: Int? = null,
    val strength: Int? = null,
    val mobility: Int? = null,
    val responseState: CalibrationResponseState = CalibrationResponseState.UNKNOWN,
)

@Serializable
enum class SetupExperience(val label: String) { NEW("Estoy empezando"), RETURNING("Estoy volviendo"), INTERMEDIATE("Ya entreno con constancia"), ADVANCED("Tengo experiencia") }

@Serializable
enum class SetupTrainingPath(val label: String) { PERSONALIZE("Personaliza un plan"), FROM_SCRATCH("Crea desde cero") }

@Serializable
enum class SetupGoal(val label: String) { STRENGTH("Fuerza"), MUSCLE("Músculo"), HEALTH("Salud y condición"), MIXED("Fuerza + cardio") }

@Serializable
enum class SetupFocus(val label: String) { FULL_BODY("Todo el cuerpo"), GLUTES("Glúteos"), LEGS("Piernas"), BACK("Espalda"), CHEST("Pecho"), SHOULDERS("Hombros"), ARMS("Brazos") }

@Serializable
enum class SetupEquipment(val label: String) { NONE("Sin material"), BANDS("Bandas"), DUMBBELLS("Mancuernas"), MACHINE("Máquinas"), CABLE("Polea"), BARBELL("Barra"), PULL_UP("Barra de dominadas"), GYM("Gimnasio completo"), SUPPORT("Apoyo estable"), BALL("Balón"), SMITH("Máquina Smith") }

@Serializable
data class SetupExerciseDraft(
    val id: String,
    val exercise: Exercise,
    val info: ExerciseMuscleInfo? = null,
) {
    val name: String get() = exercise.name
    val sets: Int? get() = exercise.sets.size.takeIf { it > 0 }
    val reps: Int? get() = exercise.sets.firstOrNull()?.targetReps
}

@Serializable
data class SetupSessionDraft(
    val weekday: Int,
    val title: String,
    val exercises: List<SetupExerciseDraft> = emptyList(),
) {
    fun toSession(commitId: String): Session = Session(
        id = "$commitId-session-$weekday",
        name = title,
        exercises = exercises.map { it.exercise },
        dayOfWeek = weekday,
        assignedDays = listOf(weekday),
    )
}

@Serializable
data class SetupRingsAnswers(
    val recentTraining: Boolean? = null,
    val recencyDays: Int? = null,
    val sessionsLastSevenDays: Int? = null,
    val activityType: InitialRecoveryActivityType? = null,
    val intensity: Int? = null,
    val zones: List<String> = emptyList(),
    val muscleFeeling: Int? = null,
    val energy: Int? = null,
    val structureFeeling: Int? = null,
    val capturedAtMs: Long? = null,
    val recentTrainingState: SetupRecentTrainingState = SetupRecentTrainingState.NOT_ANSWERED,
    val lastSessionRecencyDays: Int? = null,
    val intensityLevel: InitialRecoveryIntensity? = null,
    val muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
    val recentMuscles: Set<String> = emptySet(),
    val axialExposure: InitialRecoveryAxialExposure = InitialRecoveryAxialExposure(),
    val discomfortIds: List<String> = emptyList(),
    val activityTypeState: com.example.kpkn.data.models.InitialRecoveryResponseState = com.example.kpkn.data.models.InitialRecoveryResponseState.UNKNOWN,
)

typealias InitialRingsAnswers = SetupRingsAnswers

@Serializable
data class SetupWizardDraft(
    val draftId: String = "",
    val commitId: String = "",
    val revision: Int = 1,
    val chapter: SetupWizardChapter = SetupWizardChapter.PROFILE,
    val name: String = "",
    val moduleChoice: SetupModuleChoice = SetupModuleChoice.TRAINING_AND_NUTRITION,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val birthDateIso: String? = null,
    val experience: SetupExperience? = null,
    val trainingPath: SetupTrainingPath? = null,
    val programRoute: SetupProgramRoute = SetupProgramRoute.CUSTOMIZABLE,
    val goal: SetupGoal? = null,
    val focus: SetupFocus = SetupFocus.FULL_BODY,
    val daysPerWeek: Int? = null,
    val selectedWeekdays: Set<Int> = emptySet(),
    val minutesPerSession: Int? = null,
    val equipment: Set<SetupEquipment> = emptySet(),
    val priorityMuscles: Set<String> = emptySet(),
    val lowerEmphasisMuscles: Set<String> = emptySet(),
    val selectedSplitId: String? = null,
    val customSplitPattern: List<String> = emptyList(),
    val customSplitName: String? = null,
    val selectedCatalogId: String? = null,
    val sessions: List<SetupSessionDraft> = emptyList(),
    val includeTraining: Boolean = true,
    val includeNutrition: Boolean = true,
    val activateProgram: Boolean = true,
    val activateNutrition: Boolean = true,
    val confirmActivation: Boolean = false,
    val ringsAnswers: SetupRingsAnswers? = null,
    val nutritionMode: String = "create",
    val nutritionPlanId: String? = null,
    val nutritionDraft: NutritionWizardDraft? = null,
    val nutritionStepIndex: Int = 0,
    val nutritionDraftJson: String? = null,
    val catalogRevision: String? = null,
    val volumeRecommendations: List<VolumeRecommendation> = emptyList(),
    val athleteProfileScore: AthleteProfileScore? = null,
    val powerliftingProfile: PowerliftingProfile? = null,
    val volumeAnswers: SetupVolumeAnswers = SetupVolumeAnswers(),
    val volumeCalibrationProfile: VolumeCalibrationProfile? = null,
    val manualMuscleOverrides: Map<String, Int> = emptyMap(),
    val manualEnergyOverride: Int? = null,
    val manualStructureOverride: Int? = null,
)

data class SetupWizardState(
    val draft: SetupWizardDraft,
    val mode: SetupWizardMode = SetupWizardMode.FULL,
    val dirty: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isCommitting: Boolean = false,
    val receiptId: String? = null,
    val programPreview: Program? = null,
    val previewReport: PersonalizationReport? = null,
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null,
    val requiresActivationConfirmation: Boolean = false,
) {
    val showNutritionPreview: Boolean get() = nutritionDraft != null
    val nutritionDraft: NutritionWizardDraft? get() = draft.nutritionDraft
}

typealias SetupWizardUiState = SetupWizardState

data class SetupPreview(val program: Program?, val report: PersonalizationReport?)

object SetupWizardValidation {
    fun validate(draft: SetupWizardDraft, chapter: SetupWizardChapter): Map<String, String> = buildMap {
        when (chapter) {
            SetupWizardChapter.PROFILE -> {
                if (draft.ageYears == null && draft.birthDateIso == null) put("age", "Añade tu edad o fecha de nacimiento")
                draft.ageYears?.let { if (it !in 13..100) put("age", "La edad debe estar entre 13 y 100 años") }
                draft.birthDateIso?.let { value ->
                    val date = runCatching { LocalDate.parse(value) }.getOrNull()
                    if (date == null || date.isAfter(LocalDate.now())) put("birthDate", "Usa una fecha válida")
                    else if (date.plusYears(13).isAfter(LocalDate.now())) put("birthDate", "Debes tener al menos 13 años")
                }
                if (draft.experience == null) put("experience", "Elige tu experiencia")
            }
            SetupWizardChapter.TRAINING -> {
                if (!draft.includeTraining) return@buildMap
                if (draft.programRoute == SetupProgramRoute.LATER) return@buildMap
                if (draft.trainingPath == null && draft.programRoute == SetupProgramRoute.CUSTOMIZABLE) put("path", "Elige cómo quieres empezar")
                if (draft.goal == null) put("goal", "Elige un objetivo")
            }
            SetupWizardChapter.VOLUME -> {
                val answers = draft.volumeAnswers
                if (answers.style == null) put("volumeStyle", "Elige el estilo de referencia")
                if (answers.technique == null) put("volumeTechnique", "Indica tu técnica actual")
                if (answers.consistency == null) put("volumeConsistency", "Indica tu consistencia actual")
                if (answers.strength == null) put("volumeStrength", "Indica tu fuerza actual")
                if (answers.mobility == null) put("volumeMobility", "Indica tu movilidad actual")
            }
            SetupWizardChapter.WEEK -> {
                if (!draft.includeTraining) return@buildMap
                if (draft.programRoute == SetupProgramRoute.LATER) return@buildMap
                if (draft.daysPerWeek == null) put("days", "Elige los días que quieres entrenar")
                if (draft.minutesPerSession == null) put("minutes", "Indica el tiempo disponible")
                if (draft.equipment.isEmpty()) put("equipment", "Elige al menos un perfil de equipo")
                if (draft.selectedWeekdays.size != draft.daysPerWeek || draft.selectedWeekdays.any { it !in 1..7 }) put("week", "Selecciona ${draft.daysPerWeek ?: 0} días en tu semana")
                if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
                    val selected = draft.sessions.filter { it.weekday in draft.selectedWeekdays }
                    if (selected.size != draft.selectedWeekdays.size || selected.any { it.exercises.isEmpty() }) put("sessions", "Completa todas las sesiones que programaste")
                }
            }
            SetupWizardChapter.NUTRITION -> Unit
            SetupWizardChapter.RINGS, SetupWizardChapter.REVIEW -> Unit
        }
    }

    fun validateDate(value: String?, today: LocalDate = LocalDate.now()): Boolean {
        val date = runCatching { value?.let(LocalDate::parse) }.getOrNull() ?: return false
        return !date.isAfter(today) && !date.plusYears(13).isAfter(today)
    }
}
