import Foundation

struct CoachMessage {
    let key: String
    let title: String
    let body: String
    let severity: CoachSeverity
    let action: CoachAction?
}

enum CoachSeverity { case info, warning, danger, success }
enum CoachAction { case reduceIntensity, skipExercise, extendRest, stayTheCourse }

struct WorkoutCoachMessages {
    enum DrainLevel { case xs, low, average, high }
    enum ReadinessLevel { case xs, low, average, high }
    private enum SessionPhase { case early, mid, late }

    private struct CoachKey: Hashable {
        let drain: DrainLevel
        let readiness: ReadinessLevel
        let sessionProgress: SessionPhase
    }

    private struct CoachMessageComponents {
        let severity: CoachSeverity
        let title: String
        let body: String
        let action: CoachAction?
    }

    private static let coachMessages: [CoachKey: CoachMessage] = {
        var map = [CoachKey: CoachMessage]()
        for drain in [DrainLevel.xs, .low, .average, .high] {
            for readiness in [ReadinessLevel.xs, .low, .average, .high] {
                for phase in [SessionPhase.early, .mid, .late] {
                    let resolved = resolve(drain: drain, readiness: readiness, phase: phase)
                    map[CoachKey(drain: drain, readiness: readiness, sessionProgress: phase)] = CoachMessage(
                        key: "\(drain)_\(readiness)_\(phase)",
                        title: resolved.title,
                        body: resolved.body,
                        severity: resolved.severity,
                        action: resolved.action
                    )
                }
            }
        }
        return map
    }()

    private static func resolve(drain: DrainLevel, readiness: ReadinessLevel, phase: SessionPhase) -> CoachMessageComponents {
        let isHighDrain = drain == .high || drain == .average
        let isLowReadiness = readiness == .xs || readiness == .low
        let isLateSession = phase == .late

        switch (isHighDrain, isLowReadiness, isLateSession, drain, readiness, phase) {
        case (true, true, true, _, _, _):
            return CoachMessageComponents(
                severity: .danger,
                title: "Cuidado: fatiga acumulada + baja disponibilidad",
                body: "Has acumulado fatiga significativa y tu readiness está baja. Considera terminar ejercicios principales aquí y guardar energía.",
                action: .reduceIntensity
            )
        case (true, true, false, _, _, _):
            return CoachMessageComponents(
                severity: .warning,
                title: "Fatiga en aumento, readiness limitada",
                body: "El sistema nervioso está trabajando fuerte y la recuperación de hoy no es óptima. Prioriza las series más importantes.",
                action: .reduceIntensity
            )
        case (_, _, _, .high, .average, .late):
            return CoachMessageComponents(
                severity: .warning,
                title: "Cerca del límite — serie final de esta fase",
                body: "Estás drenado pero tu cuerpo responde. Si la próxima serie se siente pesada, no insistas. Termina fuerte pero seguro.",
                action: .reduceIntensity
            )
        case (_, _, _, .high, .high, .late):
            return CoachMessageComponents(
                severity: .info,
                title: "Drenado pero listo",
                body: "Tu batería está baja pero tu readiness es alta. Recuperas bien. La última serie cuenta.",
                action: .stayTheCourse
            )
        case (_, _, _, .high, .high, _):
            return CoachMessageComponents(
                severity: .info,
                title: "Buen ritmo de trabajo",
                body: "Estás drenando pero tu recuperación es excelente. Sigue con el plan, tu cuerpo responde bien.",
                action: .stayTheCourse
            )
        case (_, _, _, .average, .high, .late):
            return CoachMessageComponents(
                severity: .success,
                title: "Excelente sesión",
                body: "Drenado moderado con readiness alta. Estás navegando la sesión con eficiencia. ¡A la carga!",
                action: .stayTheCourse
            )
        case (_, _, _, .average, .low, _):
            return CoachMessageComponents(
                severity: .warning,
                title: "Recuperación limitada detectada",
                body: "Tu cuerpo no está al 100% hoy. Ajusta la intensidad si algo no se siente bien.",
                action: .reduceIntensity
            )
        case (false, true, _, .low, _, _):
            return CoachMessageComponents(
                severity: .info,
                title: "Sesión ligera — oportunidad",
                body: "Baja fatiga y readiness moderada. Ideal para trabajar técnica o accesibilidad. ¡Aprovecha!",
                action: .stayTheCourse
            )
        case (false, false, _, .low, .high, _):
            return CoachMessageComponents(
                severity: .success,
                title: "Día óptimo para max effort",
                body: "Baja fatiga y máxima disponibilidad. Ideal para series pesadas o probar RMs.",
                action: .stayTheCourse
            )
        case (false, false, _, .xs, .high, _):
            return CoachMessageComponents(
                severity: .success,
                title: "¡Energía al máximo!",
                body: "Sesión nueva, cuerpo fresco. Perfecto para series pesadas o explorar nuevos límites.",
                action: .stayTheCourse
            )
        case (false, true, _, .xs, .xs, _):
            return CoachMessageComponents(
                severity: .warning,
                title: "Disponibilidad reducida",
                body: "Readiness muy baja. Si es posible, considera reprogramar sesiones pesadas para otro día.",
                action: .skipExercise
            )
        case (true, false, _, .high, .average, _):
            return CoachMessageComponents(
                severity: .info,
                title: "Mitad de sesión — evalúa cómo vas",
                body: "Has drenado bastante. Si aún te quedan series pesadas, considera reducir un 5-10% en las últimas.",
                action: .extendRest
            )
        default:
            return CoachMessageComponents(
                severity: .info,
                title: "Seguimiento en tiempo real",
                body: "La sesión ajusta el descanso según tu fatiga registrada.",
                action: nil
            )
        }
    }

    static func getMessage(
        weightedDrainPct: Double,
        readinessScore: Int?,
        sessionProgress: Double
    ) -> CoachMessage {
        let drainLevel: DrainLevel = {
            switch weightedDrainPct {
            case 8.0...: return .high
            case 4.0..<8.0: return .average
            case 1.5..<4.0: return .low
            default: return .xs
            }
        }()
        let readinessLevel: ReadinessLevel = {
            guard let score = readinessScore else { return .high }
            switch score {
            case 80...100: return .high
            case 60..<80: return .average
            case 40..<60: return .low
            default: return .xs
            }
        }()
        let phase: SessionPhase = {
            switch sessionProgress {
            case ..<0.33: return .early
            case 0.33..<0.66: return .mid
            default: return .late
            }
        }()
        return coachMessages[CoachKey(drain: drainLevel, readiness: readinessLevel, sessionProgress: phase)]
            ?? coachMessages[CoachKey(drain: .low, readiness: .average, sessionProgress: .mid)]!
    }

    static func getReadinessScore(neural: Int?, spinal: Int?, muscular: Int?) -> Int? {
        let values = [neural, spinal, muscular].compactMap { $0 }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / values.count
    }
}
