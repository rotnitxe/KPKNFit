import Foundation

/// Paridad de `BlockProgressionEngine.kt` — progresión semanal dentro de un bloque.
public enum BlockProgressionEngine {
    public struct PrescriptionDiff {
        public let weekFromIndex: Int
        public let weekToIndex: Int
        public let percentageDelta: Double
        public let setsDelta: Int
        public let rpeDelta: Double
        public let summary: String
    }

    public struct ApplyResult {
        public let block: Block
        public let diffs: [PrescriptionDiff]
    }

    public static func intensityRange(for goal: BlockGoal?) -> (Int, Int) {
        switch goal {
        case .ACCUMULATION, .DENSITY: return (60, 75)
        case .INTENSIFICATION: return (75, 88)
        case .SPECIFICITY: return (80, 92)
        case .REALIZATION, .PEAK: return (85, 98)
        case .DELOAD, .TAPER: return (40, 65)
        case .CUSTOM, .none: return (65, 85)
        }
    }

    public static func mesocycleGoal(from blockGoal: BlockGoal?) -> MesocycleGoal {
        switch blockGoal {
        case .ACCUMULATION, .DENSITY: return .ACCUMULATION
        case .INTENSIFICATION: return .INTENSIFICATION
        case .SPECIFICITY: return .REALIZATION
        case .REALIZATION, .PEAK: return .REALIZATION
        case .DELOAD, .TAPER: return .DELOAD
        case .CUSTOM, .none: return .CUSTOM
        }
    }

    public static func previewDiff(block: Block, weekFrom: Int, weekTo: Int) -> PrescriptionDiff? {
        let total = block.mesocycles.first?.weeks.count ?? 0
        guard total >= 1, weekFrom >= 1, weekTo >= 1, weekFrom <= total, weekTo <= total else { return nil }
        let range = intensityRange(for: block.goal)
        let goal = mesocycleGoal(from: block.goal)
        let fromRx = PeriodizationEngine.prescriptionFor(
            goal: goal, baseSets: 3, baseReps: 8, volumeModifier: nil,
            intensityMin: range.0, intensityMax: range.1,
            weekNumber: weekFrom, totalWeeksInBlock: total
        )
        let toRx = PeriodizationEngine.prescriptionFor(
            goal: goal, baseSets: 3, baseReps: 8, volumeModifier: nil,
            intensityMin: range.0, intensityMax: range.1,
            weekNumber: weekTo, totalWeeksInBlock: total
        )
        return PrescriptionDiff(
            weekFromIndex: weekFrom,
            weekToIndex: weekTo,
            percentageDelta: toRx.percentageRM - fromRx.percentageRM,
            setsDelta: toRx.sets - fromRx.sets,
            rpeDelta: toRx.rpe - fromRx.rpe,
            summary: "Semana \(weekFrom)→\(weekTo): %RM \(String(format: "%.1f", fromRx.percentageRM))→\(String(format: "%.1f", toRx.percentageRM))"
        )
    }

    /// Aplica índices de progresión y deja diffs semana N→N+1 (MVP: no reescribe sets en iOS aún).
    public static func applyProgression(block: Block, scheme: BlockProgressionScheme? = nil) -> ApplyResult {
        let resolved = scheme ?? block.progressionScheme ?? .PERCENT_RM
        guard resolved != .NONE, let meso = block.mesocycles.first, !meso.weeks.isEmpty else {
            return ApplyResult(block: block, diffs: [])
        }
        let total = meso.weeks.count
        let range = intensityRange(for: block.goal)
        let goal = mesocycleGoal(from: block.goal)
        var diffs: [PrescriptionDiff] = []
        if total >= 2 {
            for i in 1..<total {
                let fromRx = PeriodizationEngine.prescriptionFor(
                    goal: goal, baseSets: 3, baseReps: 8, volumeModifier: nil,
                    intensityMin: range.0, intensityMax: range.1,
                    weekNumber: i, totalWeeksInBlock: total
                )
                let toRx = PeriodizationEngine.prescriptionFor(
                    goal: goal, baseSets: 3, baseReps: 8, volumeModifier: nil,
                    intensityMin: range.0, intensityMax: range.1,
                    weekNumber: i + 1, totalWeeksInBlock: total
                )
                diffs.append(
                    PrescriptionDiff(
                        weekFromIndex: i,
                        weekToIndex: i + 1,
                        percentageDelta: toRx.percentageRM - fromRx.percentageRM,
                        setsDelta: toRx.sets - fromRx.sets,
                        rpeDelta: toRx.rpe - fromRx.rpe,
                        summary: "Semana \(i)→\(i + 1)"
                    )
                )
            }
        }
        let weeks = meso.weeks.enumerated().map { idx, week in
            ProgramWeek(
                id: week.id,
                name: week.name,
                description: week.description,
                sessions: week.sessions,
                variant: week.variant,
                isLoopWeek: week.isLoopWeek,
                loopId: week.loopId,
                startDate: week.startDate,
                endDate: week.endDate,
                trainingDayDates: week.trainingDayDates,
                progressionIndex: week.progressionIndex ?? (idx + 1)
            )
        }
        let updatedMeso = Mesocycle(
            id: meso.id,
            name: meso.name,
            goal: goal,
            customGoal: meso.customGoal,
            weeks: weeks
        )
        let updated = Block(
            id: block.id,
            name: block.name,
            description: block.description,
            mesocycles: [updatedMeso] + Array(block.mesocycles.dropFirst()),
            goal: block.goal,
            progressionScheme: resolved
        )
        return ApplyResult(block: updated, diffs: diffs)
    }
}
