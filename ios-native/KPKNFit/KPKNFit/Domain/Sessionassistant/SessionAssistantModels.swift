import Foundation

struct SessionAssistantInput {
    let allExercisesInSession: [Exercise]
    let weekSessions: [Session]
    let currentSessionId: String
    let program: Program?
    let settings: Settings
    let workoutLogs: [WorkoutLog]
    let exerciseIndex: [String: ExerciseMuscleInfo]
    let ruleLimits: SessionEditorRuleLimits
    let mesoIndex: Int
    let programId: String
    let targetDurationMinutes: Int?
    let customDrain: PredictedDrain?
    let customTemplateDrains: [String: PredictedDrain]

    init(
        allExercisesInSession: [Exercise],
        weekSessions: [Session],
        currentSessionId: String,
        program: Program?,
        settings: Settings,
        workoutLogs: [WorkoutLog],
        exerciseIndex: [String: ExerciseMuscleInfo],
        ruleLimits: SessionEditorRuleLimits,
        mesoIndex: Int,
        programId: String,
        targetDurationMinutes: Int? = nil,
        customDrain: PredictedDrain? = nil,
        customTemplateDrains: [String: PredictedDrain] = [:]
    ) {
        self.allExercisesInSession = allExercisesInSession
        self.weekSessions = weekSessions
        self.currentSessionId = currentSessionId
        self.program = program
        self.settings = settings
        self.workoutLogs = workoutLogs
        self.exerciseIndex = exerciseIndex
        self.ruleLimits = ruleLimits
        self.mesoIndex = mesoIndex
        self.programId = programId
        self.targetDurationMinutes = targetDurationMinutes
        self.customDrain = customDrain
        self.customTemplateDrains = customTemplateDrains
    }
}

enum Verdict {
    case optimal, warning, fatiguing, critical
}

enum RiskType {
    case volume, failure, cns, spine, joint, pattern
}

enum RiskSeverity {
    case info, warning, blocking
}

struct SessionRisk {
    let id: String
    let type: RiskType
    let severity: RiskSeverity
    let muscle: String?
    let exerciseId: String?
    let exerciseName: String?
    let title: String
    let message: String
    let action: String

    init(
        id: String,
        type: RiskType,
        severity: RiskSeverity,
        muscle: String? = nil,
        exerciseId: String? = nil,
        exerciseName: String? = nil,
        title: String,
        message: String,
        action: String
    ) {
        self.id = id
        self.type = type
        self.severity = severity
        self.muscle = muscle
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.title = title
        self.message = message
        self.action = action
    }
}

enum AssistantActionType {
    case reduceSet, lowerRpe, removeFailure, addGhostExercise, applyTemplate, keep, blockAdd, reduceRestTime, convertToSuperset, convertToDropset
}

struct AssistantSuggestion {
    let id: String
    let type: AssistantActionType
    let title: String
    let message: String
    let muscle: String?
    let exerciseId: String?
    let exerciseName: String?
    let priority: Int
    let canAutoApply: Bool

    init(
        id: String,
        type: AssistantActionType,
        title: String,
        message: String,
        muscle: String? = nil,
        exerciseId: String? = nil,
        exerciseName: String? = nil,
        priority: Int = 0,
        canAutoApply: Bool = false
    ) {
        self.id = id
        self.type = type
        self.title = title
        self.message = message
        self.muscle = muscle
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.priority = priority
        self.canAutoApply = canAutoApply
    }
}

struct GhostExerciseCard {
    let cardId: String
    let exerciseDbId: String
    let name: String
    let motivo: String
    let sets: Int
    let reps: Int
    let rpe: Double
    let restSeconds: Int
    let impactoVolumen: String
    let impactoDrenaje: String
    let impactoColumna: String
    let compatibleConSplit: Bool
}

struct TemplatePreview {
    let template: SessionTemplate
    let modoRecomendado: SessionTemplateApplyMode
    let volumenPorMusculo: [String: Double]
    let drenajeEstimado: PredictedDrain
    let advertencias: [String]
    let duracionEstimada: Int
}

enum SessionTemplateApplyMode {
    case replace, append
}

struct VolumeThreshold {
    let mev: Double
    let mav: Double
    let mrv: Double
}

struct SessionAssistantReport {
    let veredicto: Verdict
    let scoreEstimado: Int
    let riesgos: [SessionRisk]
    let ajustes: [AssistantSuggestion]
    let oportunidades: [AssistantSuggestion]
    let tarjetasFantasma: [GhostExerciseCard]
    let plantillasCompatibles: [TemplatePreview]
    let volumenPorMusculo: [String: Double]
    let umbralesPorMusculo: [String: VolumeThreshold]
    let drenajeEstimado: PredictedDrain
    let duracionEstimada: Int
    let resumenTexto: String
    let totalRestSeconds: Int
    let estimatedWorkSeconds: Int

    init(
        veredicto: Verdict,
        scoreEstimado: Int,
        riesgos: [SessionRisk],
        ajustes: [AssistantSuggestion],
        oportunidades: [AssistantSuggestion],
        tarjetasFantasma: [GhostExerciseCard],
        plantillasCompatibles: [TemplatePreview],
        volumenPorMusculo: [String: Double],
        umbralesPorMusculo: [String: VolumeThreshold],
        drenajeEstimado: PredictedDrain,
        duracionEstimada: Int,
        resumenTexto: String,
        totalRestSeconds: Int = 0,
        estimatedWorkSeconds: Int = 0
    ) {
        self.veredicto = veredicto
        self.scoreEstimado = scoreEstimado
        self.riesgos = riesgos
        self.ajustes = ajustes
        self.oportunidades = oportunidades
        self.tarjetasFantasma = tarjetasFantasma
        self.plantillasCompatibles = plantillasCompatibles
        self.volumenPorMusculo = volumenPorMusculo
        self.umbralesPorMusculo = umbralesPorMusculo
        self.drenajeEstimado = drenajeEstimado
        self.duracionEstimada = duracionEstimada
        self.resumenTexto = resumenTexto
        self.totalRestSeconds = totalRestSeconds
        self.estimatedWorkSeconds = estimatedWorkSeconds
    }
}

struct SessionEditorRuleLimits {
    let maxRPE: Double
    let maxExercisesPerMuscle: Int
    let maxVolumePerMuscleSession: Double
    let maxVolumePerMuscleWeekly: Double
    let maxSamePatternPerSession: Int
    let rigidLimits: Bool

    init(
        maxRPE: Double = 10.0,
        maxExercisesPerMuscle: Int = 6,
        maxVolumePerMuscleSession: Double = 12.0,
        maxVolumePerMuscleWeekly: Double = 24.0,
        maxSamePatternPerSession: Int = 4,
        rigidLimits: Bool = false
    ) {
        self.maxRPE = maxRPE
        self.maxExercisesPerMuscle = maxExercisesPerMuscle
        self.maxVolumePerMuscleSession = maxVolumePerMuscleSession
        self.maxVolumePerMuscleWeekly = maxVolumePerMuscleWeekly
        self.maxSamePatternPerSession = maxSamePatternPerSession
        self.rigidLimits = rigidLimits
    }
}

class MuscularVolumeAccumulator {
    var flat: Double = 0.0
    var effective: Double = 0.0
    var fail: Double = 0.0
}

class MuscleRoleBreakdown {
    var primary: Double = 0.0
    var secondary: Double = 0.0
    var stabilizer: Double = 0.0
    var neutralizer: Double = 0.0

    var stabilizerShare: Double {
        let total = primary + secondary + stabilizer + neutralizer
        return total > 0 ? stabilizer / total : 0.0
    }

    var secondaryShare: Double {
        let total = primary + secondary + stabilizer + neutralizer
        return total > 0 ? secondary / total : 0.0
    }
}

class MuscleRecommendationContext {
    var usesPercent: Bool = false
    var usesRir: Bool = false
    var usesFailure: Bool = false
}
