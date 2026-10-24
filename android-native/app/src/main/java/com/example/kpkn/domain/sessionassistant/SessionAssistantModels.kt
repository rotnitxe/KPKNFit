package com.example.kpkn.domain.sessionassistant

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
    val supersetGroups: List<com.example.kpkn.data.models.SupersetGroup> = emptyList(),
    val sessionWarmup: List<com.example.kpkn.data.models.WarmupExercise> = emptyList(),
    /**
     * El borrador activo completo (partes, grupos, calentamiento). Con él, el reloj de las reglas es el MISMO que el del editor
     * ([com.example.kpkn.domain.training.SessionDurationEstimator]) y las acciones se simulan sobre la estructura real. Null =
     * se arma una sesión provisional con [allExercisesInSession] (pruebas y llamadas sueltas).
     */
    val session: com.example.kpkn.data.models.Session? = null,
    /** Descansos con que el editor crea una superserie (Reglas): la simulación de «unir en superserie» usa los mismos. */
    val supersetBetweenRestSeconds: Int = 60,
    val supersetRoundRestSeconds: Int = 120,
    /** Ahora, en milisegundos: el historial reciente y los cruces se miden contra este instante. */
    val nowMs: Long = System.currentTimeMillis(),
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
    COMPOSITE,
}

/** Acción atómica que el usuario puede aceptar o rechazar dentro de una sugerencia. */
sealed class AssistantDetailAction {
    data class LowerRpe(
        val exerciseId: String? = null,
        val amount: Double = 0.5,
    ) : AssistantDetailAction()

    data class ReduceSet(
        val exerciseId: String? = null,
        val muscle: String? = null,
    ) : AssistantDetailAction()

    /** [exerciseIds] vacío = todos los ejercicios (acción antigua); con ids, solo esos (los elegibles tras la verificación). */
    data class ReduceRest(
        val seconds: Int = 15,
        val exerciseIds: List<String> = emptyList(),
        /** Descanso que se supone a un ejercicio sin descanso propio: el mismo que usó la simulación al verificar la acción. */
        val defaultRestSeconds: Int = 90,
    ) : AssistantDetailAction()

    data class ConvertToSuperset(
        val exerciseId: String,
    ) : AssistantDetailAction()

    data class ConvertToDropSet(
        val exerciseId: String,
    ) : AssistantDetailAction()

    data object RemoveFailure : AssistantDetailAction()
}

/** Efecto verificado de una acción sobre un borrador simulado: lo que de verdad cambia, antes de ofrecerla. */
data class AssistantActionEffect(
    /** Cambio de la duración con el reloj común: negativo = ahorra. */
    val deltaSeconds: Int,
    /** Cambio en el número de series de fuerza. */
    val deltaSets: Int,
    /** Cambio del pico de coste estimado (puntos): negativo = baja. Null si no se midió. */
    val deltaPeakCost: Double? = null,
)

data class AssistantSuggestionDetail(
    val id: String,
    val label: String,
    val action: AssistantDetailAction,
    val defaultAccepted: Boolean = true,
    /** Efecto ya comprobado sobre un borrador; null en las acciones antiguas sin verificar. */
    val effect: AssistantActionEffect? = null,
)

/** Etiqueta de cada aviso: sin severidades de lesión, solo qué es y de quién es la restricción. */
enum class AssistantNoticeKind(val label: String) {
    INFO("Información"),
    OPTIONAL_ADJUSTMENT("Ajuste opcional"),
    USER_LIMIT("Límite que elegiste"),
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
    val details: List<AssistantSuggestionDetail> = emptyList(),
    val kind: AssistantNoticeKind = AssistantNoticeKind.OPTIONAL_ADJUSTMENT,
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
    val compatibleConSplit: Boolean,
)

data class TemplatePreview(
    val template: SessionTemplate,
    val modoRecomendado: SessionTemplateApplyMode,
    val volumenPorMusculo: Map<String, Double>,
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
    val veredicto: Verdict?,
    val scoreEstimado: Int?,
    val riesgos: List<SessionRisk>,
    val ajustes: List<AssistantSuggestion>,
    val oportunidades: List<AssistantSuggestion>,
    val tarjetasFantasma: List<GhostExerciseCard>,
    val plantillasCompatibles: List<TemplatePreview>,
    val volumenPorMusculo: Map<String, Double>,
    val umbralesPorMusculo: Map<String, VolumeThreshold>,
    val duracionEstimada: Int,
    val resumenTexto: String,
    val totalRestSeconds: Int = 0,
    val estimatedWorkSeconds: Int = 0,
    /**
     * Notas informativas (sin acción): un coste alto sin historial comparable, un exceso de tiempo pequeño o un cruce bajo entre
     * sesiones previstas. Junto con [ajustes] suman como mucho tres avisos.
     */
    val notas: List<String> = emptyList(),
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
