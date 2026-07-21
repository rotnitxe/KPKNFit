import Foundation

struct WorkoutAdaptiveRest {
    private static let minRest = 45
    private static let maxRest = 360
    private static let minFactor = 0.75
    private static let maxFactor = 2.10

    enum ExerciseType {
        case compound
        case isolation
        case unknown
    }

    struct AdaptiveRestContext {
        let advanced: SetAdvancedFeedback
        let setDrain: SetDrain?
        let effectiveRpe: Double?
        let sessionProgress: Double?
        let exerciseType: ExerciseType
        let isSuperset: Bool
        let rom: Int?
        let sessionPaceFactor: Double?

        init(
            advanced: SetAdvancedFeedback,
            setDrain: SetDrain? = nil,
            effectiveRpe: Double? = nil,
            sessionProgress: Double? = nil,
            exerciseType: ExerciseType = .unknown,
            isSuperset: Bool = false,
            rom: Int? = nil,
            sessionPaceFactor: Double? = nil
        ) {
            self.advanced = advanced
            self.setDrain = setDrain
            self.effectiveRpe = effectiveRpe
            self.sessionProgress = sessionProgress
            self.exerciseType = exerciseType
            self.isSuperset = isSuperset
            self.rom = rom
            self.sessionPaceFactor = sessionPaceFactor
        }
    }

    static func compute(baseRestSeconds: Int, advanced: SetAdvancedFeedback) -> Int {
        return compute(baseRestSeconds: baseRestSeconds, context: AdaptiveRestContext(advanced: advanced))
    }

    static func compute(baseRestSeconds: Int, context: AdaptiveRestContext) -> Int {
        var factor = techniqueFactor(advanced: context.advanced)
        factor *= augeDrainFactor(setDrain: context.setDrain)
        factor *= rpeFactor(effectiveRpe: context.effectiveRpe)
        factor *= sessionProgressFactor(sessionProgress: context.sessionProgress)
        factor *= exerciseTypeFactor(type: context.exerciseType, isSuperset: context.isSuperset)
        factor *= romFactor(rom: context.rom)
        if let pace = context.sessionPaceFactor { factor *= pace }
        let boundedFactor = factor.clamped(to: minFactor...maxFactor)
        return Int((Double(baseRestSeconds) * boundedFactor).clamped(to: Double(minRest)...Double(maxRest)))
    }

    private static func techniqueFactor(advanced: SetAdvancedFeedback) -> Double {
        var factor = 1.0
        if advanced.reachedFailure { factor *= 1.30 }
        if advanced.isFailedSet { factor *= 1.20 }
        if advanced.isPartial { factor *= 1.15 }
        if !advanced.restPauses.isEmpty { factor *= 1.20 }
        if !advanced.dropSets.isEmpty { factor *= 1.10 }
        return factor
    }

    private static func augeDrainFactor(setDrain: SetDrain?) -> Double {
        guard let drain = setDrain else { return 1.0 }
        let weighted = drain.cnsDrainPct * 0.45 + drain.muscularDrainPct * 0.25 + drain.spinalDrainPct * 0.30
        switch weighted {
        case 8.0...: return 1.35
        case 6.0..<8.0: return 1.27
        case 4.0..<6.0: return 1.20
        case 2.5..<4.0: return 1.12
        case 1.5..<2.5: return 1.06
        default: return 1.0
        }
    }

    private static func rpeFactor(effectiveRpe: Double?) -> Double {
        guard let rpe = effectiveRpe else { return 1.0 }
        switch rpe {
        case 10.0...: return 1.16
        case 9.5..<10.0: return 1.12
        case 8.8..<9.5: return 1.08
        case 7.5..<8.8: return 1.03
        case ..<6.5: return 0.95
        default: return 1.0
        }
    }

    private static func sessionProgressFactor(sessionProgress: Double?) -> Double {
        guard let progress = sessionProgress else { return 1.0 }
        switch progress {
        case 0.85...: return 1.12
        case 0.66..<0.85: return 1.08
        case 0.40..<0.66: return 1.04
        case ..<0.21: return 0.96
        default: return 1.0
        }
    }

    private static func exerciseTypeFactor(type: ExerciseType, isSuperset: Bool) -> Double {
        let base: Double = {
            switch type {
            case .compound: return 1.08
            case .isolation: return 0.96
            case .unknown: return 1.0
            }
        }()
        return isSuperset ? base * 1.06 : base
    }

    private static func romFactor(rom: Int?) -> Double {
        guard let r = rom else { return 1.0 }
        switch r {
        case ..<50: return 0.92
        case ..<60: return 0.95
        case ..<80: return 1.0
        default: return 1.02
        }
    }

    static func computeSessionPaceFactor(
        elapsedMs: Int64,
        targetMinutes: Int?,
        completedSets: Int,
        totalSets: Int
    ) -> Double? {
        guard let targetMinutes = targetMinutes, totalSets > 0, completedSets > 0 else { return nil }
        let elapsedMin = Double(elapsedMs) / 60000.0
        let expectedMin = Double(targetMinutes) * (Double(completedSets) / Double(totalSets))
        guard expectedMin > 0 else { return nil }
        let ratio = elapsedMin / expectedMin
        switch ratio {
        case ..<0.70: return 0.88
        case ..<0.85: return 0.93
        case ..<1.15: return 1.0
        case ..<1.30: return 1.07
        default: return 1.14
        }
    }
}
