import Foundation

public struct RestRecoveryStatus {
    public let recoveryFraction: Double
    public let recoveryPercent: Int
    public let difficultyTier: Int
    public let isReady: Bool
}

public enum WorkoutRestRecoveryModel {
    private static let READY_THRESHOLD = 0.75

    public static func fromLastSet(
        elapsedSeconds: Int,
        completedSet: CompletedSet?,
        advanced: SetAdvancedFeedback? = nil
    ) -> RestRecoveryStatus {
        let difficulty = estimateDifficultyTier(completedSet: completedSet, advanced: advanced)
        return calculate(elapsedSeconds: elapsedSeconds, difficultyTier: difficulty)
    }

    public static func calculate(elapsedSeconds: Int, difficultyTier: Int) -> RestRecoveryStatus {
        let safeDifficulty = max(1, min(difficultyTier, 5))
        let demand = 18.0 - (Double(5 - safeDifficulty) * 3.0)
        let minutes = max(0.0, Double(max(0, elapsedSeconds)) / 60.0)
        let recovery = ((21.0 - (demand * exp(pow(minutes, 0.8) * -0.7))) / 21.0).clamped(to: 0.0...1.05)
        let percent = max(0, min(Int((recovery * 100.0).rounded()), 100))
        return RestRecoveryStatus(
            recoveryFraction: recovery,
            recoveryPercent: percent,
            difficultyTier: safeDifficulty,
            isReady: recovery >= READY_THRESHOLD
        )
    }

    private static func estimateDifficultyTier(
        completedSet: CompletedSet?,
        advanced: SetAdvancedFeedback?
    ) -> Int {
        var score = 0
        if advanced?.isFailedSet == true ||
            advanced?.reachedFailure == true ||
            completedSet?.isFailure == true ||
            completedSet?.isFailedSet == true {
            score = 5
        } else {
            let effectiveRpe = resolveEffectiveRpe(completedSet: completedSet, advanced: advanced)
            if effectiveRpe >= 9.5 {
                score = 5
            } else if effectiveRpe >= 8.8 {
                score = 4
            } else if effectiveRpe >= 7.8 {
                score = 3
            } else if effectiveRpe >= 6.8 {
                score = 2
            } else {
                score = 1
            }
        }

        var techniqueLoad = 0
        if advanced?.dropSets.isEmpty == false || completedSet?.dropSets.isEmpty == false {
            techniqueLoad += 1
        }
        if advanced?.restPauses.isEmpty == false || completedSet?.restPauses.isEmpty == false {
            techniqueLoad += 1
        }
        if advanced?.isPartial == true || completedSet?.isPartial == true {
            techniqueLoad += 1
        }
        score += techniqueLoad

        return max(1, min(score, 5))
    }

    private static func resolveEffectiveRpe(
        completedSet: CompletedSet?,
        advanced: SetAdvancedFeedback?
    ) -> Double {
        let advancedFromMode: Double?
        if advanced?.actualIntensityMode == .RIR {
            let rir = advanced?.rir.map(Double.init) ?? advanced?.actualIntensityValue
            advancedFromMode = rir.map { 10.0 - $0 }
        } else {
            advancedFromMode = advanced?.actualIntensityValue
        }
        
        if let advancedFromMode = advancedFromMode {
            return max(1.0, min(advancedFromMode, 10.0))
        }

        let setFromMode: Double?
        if completedSet?.actualIntensityMode == .RIR {
            let rir = completedSet?.rir.map(Double.init) ?? completedSet?.actualIntensityValue
            setFromMode = rir.map { 10.0 - $0 }
        } else {
            setFromMode = completedSet?.actualIntensityValue
        }

        if let setFromMode = setFromMode {
            return max(1.0, min(setFromMode, 10.0))
        }

        if let rpe = completedSet?.rpe {
            return max(1.0, min(rpe, 10.0))
        }
        
        if let rir = advanced?.rir {
            return max(1.0, min(10.0 - Double(rir), 10.0))
        }

        return 7.5
    }
}
