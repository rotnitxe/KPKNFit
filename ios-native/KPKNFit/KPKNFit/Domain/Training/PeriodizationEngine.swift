import Foundation

/// Paridad mínima de `PeriodizationEngine.kt` para progresión por bloque.
public enum PeriodizationEngine {
    public struct SetPrescription {
        public let sets: Int
        public let reps: Int
        public let percentageRM: Double
        public let rpe: Double
    }

    private static func volumeGoalMultiplier(_ goal: MesocycleGoal) -> Double {
        switch goal {
        case .ACCUMULATION: return 1.15
        case .INTENSIFICATION: return 1.0
        case .REALIZATION: return 0.8
        case .DELOAD: return 0.55
        case .CUSTOM: return 1.0
        }
    }

    public static func scaleSets(baseSets: Int, goal: MesocycleGoal, volumeModifier: Double?) -> Int {
        let multiplier = volumeGoalMultiplier(goal) * (volumeModifier ?? 1.0)
        let scaled = Int((Double(baseSets) * multiplier).rounded())
        return max(1, min(scaled, max(baseSets * 3, 1)))
    }

    public static func percentageForWeek(
        intensityMin: Int,
        intensityMax: Int,
        weekNumber: Int,
        totalWeeksInBlock: Int
    ) -> Double {
        if totalWeeksInBlock <= 1 { return Double(intensityMin + intensityMax) / 2.0 }
        let progress = min(1.0, max(0.0, Double(weekNumber - 1) / Double(totalWeeksInBlock - 1)))
        return Double(intensityMin) + Double(intensityMax - intensityMin) * progress
    }

    public static func prescriptionFor(
        goal: MesocycleGoal,
        baseSets: Int,
        baseReps: Int,
        volumeModifier: Double?,
        intensityMin: Int,
        intensityMax: Int,
        weekNumber: Int,
        totalWeeksInBlock: Int
    ) -> SetPrescription {
        let reps: Int = {
            switch goal {
            case .ACCUMULATION: return min(20, max(1, baseReps + 2))
            case .INTENSIFICATION: return min(20, max(1, baseReps))
            case .REALIZATION: return min(20, max(1, baseReps - 2))
            case .DELOAD: return min(20, max(1, baseReps - 1))
            case .CUSTOM: return min(20, max(1, baseReps))
            }
        }()
        let rpe: Double = {
            switch goal {
            case .ACCUMULATION: return 6.5
            case .INTENSIFICATION: return 8.0
            case .REALIZATION: return 9.0
            case .DELOAD: return 5.5
            case .CUSTOM: return 7.5
            }
        }()
        return SetPrescription(
            sets: scaleSets(baseSets: baseSets, goal: goal, volumeModifier: volumeModifier),
            reps: reps,
            percentageRM: percentageForWeek(
                intensityMin: intensityMin,
                intensityMax: intensityMax,
                weekNumber: weekNumber,
                totalWeeksInBlock: totalWeeksInBlock
            ),
            rpe: rpe
        )
    }
}
