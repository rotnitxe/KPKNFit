package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.sessions.SessionTemplate

data class SessionAssistantInput(
    val allExercisesInSession: List<com.example.kpkn.data.models.Exercise>,
    val weekSessions: List<com.example.kpkn.data.models.Session>,
    val currentSessionId: String,
    val program: com.example.kpkn.data.models.Program?,
    val settings: com.example.kpkn.data.models.Settings,
    val workoutLogs: List<com.example.kpkn.data.models.WorkoutLog>,
    val exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    val ruleLimits: SessionEditorRuleLimits,
    val mesoIndex: Int,
    val programId: String,
    val targetDurationMinutes: Int? = null,
    val customDrain: PredictedDrain? = null,
    val customTemplateDrains: Map<String, PredictedDrain> = emptyMap(),
)

enum class Verdict { OPTIMAL, WARNING, FATIGUING, CRITICAL }

enum class RiskType { VOLUME, FAILURE, CNS, SPINE, JOINT, PATTERN }
enum class RiskSeverity { INFO, WARNING, BLOCKING }

data class SessionRisk(
    val id: String,
    val type: RiskType,
    val severity: RiskSeverity,
    val muscle: String? = null,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val title: String,
    val message: String,
    val action: String,
)

enum class AssistantActionType {
    REDUCE_SET,
    LOWER_RPE,
    REMOVE_FAILURE,
    ADD_GHOST_EXERCISE,
    APPLY_TEMPLATE,
    KEEP,
    BLOCK_ADD,
    REDUCE_REST_TIME,
    CONVERT_TO_SUPERSET,
    CONVERT_TO_DROPSET,
}

data class AssistantSuggestion(
    val id: String,
    val type: AssistantActionType,
    val title: String,
    val message: String,
    val muscle: String? = null,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val priority: Int = 0,
    val canAutoApply: Boolean = false,
)

data class GhostExerciseCard(
    val cardId: String,
    val exerciseDbId: String,
    val name: String,
    val motivo: String,
    val sets: Int,
    val reps: Int,
    val rpe: Double,
    val restSeconds: Int,
    val impactoVolumen: String,
    val impactoDrenaje: String,
    val impactoColumna: String,
    val compatibleConSplit: Boolean,
)

data class TemplatePreview(
    val template: SessionTemplate,
    val modoRecomendado: SessionTemplateApplyMode,
    val volumenPorMusculo: Map<String, Double>,
    val drenajeEstimado: PredictedDrain,
    val advertencias: List<String>,
    val duracionEstimada: Int,
)

enum class SessionTemplateApplyMode { REPLACE, APPEND }

data class VolumeThreshold(
    val mev: Double,
    val mav: Double,
    val mrv: Double,
)

data class SessionAssistantReport(
    val veredicto: Verdict,
    val scoreEstimado: Int,
    val riesgos: List<SessionRisk>,
    val ajustes: List<AssistantSuggestion>,
    val oportunidades: List<AssistantSuggestion>,
    val tarjetasFantasma: List<GhostExerciseCard>,
    val plantillasCompatibles: List<TemplatePreview>,
    val volumenPorMusculo: Map<String, Double>,
    val umbralesPorMusculo: Map<String, VolumeThreshold>,
    val drenajeEstimado: PredictedDrain,
    val duracionEstimada: Int,
    val resumenTexto: String,
    val totalRestSeconds: Int = 0,
    val estimatedWorkSeconds: Int = 0,
)

data class SessionEditorRuleLimits(
    val maxRPE: Double = 10.0,
    val maxExercisesPerMuscle: Int = 6,
    val maxVolumePerMuscleSession: Double = 12.0,
    val maxVolumePerMuscleWeekly: Double = 24.0,
    val maxSamePatternPerSession: Int = 4,
    val rigidLimits: Boolean = false,
)

data class MuscularVolumeAccumulator(
    var flat: Double = 0.0,
    var effective: Double = 0.0,
    var fail: Double = 0.0,
)

data class MuscleRoleBreakdown(
    var primary: Double = 0.0,
    var secondary: Double = 0.0,
    var stabilizer: Double = 0.0,
    var neutralizer: Double = 0.0,
) {
    val stabilizerShare: Double get() {
        val total = primary + secondary + stabilizer + neutralizer
        return if (total > 0) stabilizer / total else 0.0
    }
    val secondaryShare: Double get() {
        val total = primary + secondary + stabilizer + neutralizer
        return if (total > 0) secondary / total else 0.0
    }
}

data class MuscleRecommendationContext(
    var usesPercent: Boolean = false,
    var usesRir: Boolean = false,
    var usesFailure: Boolean = false,
)
