import Foundation

struct PerformanceRangeResult {
    let ermMin: Double
    let ermMax: Double
    let ermRms: Double
    let ewmaErm: Double
    let isCurrentInRange: Bool
}

enum PerformanceRangeCalculator {

    static let defaultWindowWeeks = 8
    private static let outlierStddevThreshold = 2.0

    static let optimismCapPct = 0.07   // +7% ceiling
    static let pessimismFloorPct = 0.10 // -10% floor

    /// Applies bandwidth correction to sessionErm relative to previousEwma.
    static func clampSessionErm(sessionErm: Double, previousEwma: Double) -> Double {
        if previousEwma <= 0.0 { return sessionErm }
        let upper = previousEwma * (1.0 + optimismCapPct)
        let lower = previousEwma * (1.0 - pessimismFloorPct)
        return max(lower, min(sessionErm, upper))
    }

    /// Computes the new EWMA eRM with bandwidth capping.
    static func computeEwmaErm(sessionErm: Double, previousEwma: Double) -> Double {
        let clamped = clampSessionErm(sessionErm: sessionErm, previousEwma: previousEwma)
        if previousEwma <= 0.0 {
            return sessionErm
        } else {
            return clamped * 0.3 + previousEwma * 0.7
        }
    }

    static func computeRange(
        snapshots: [Double],
        currentErm: Double,
        previousEwma: Double = 0.0
    ) -> PerformanceRangeResult {
        let newEwma = computeEwmaErm(sessionErm: currentErm, previousEwma: previousEwma)

        if snapshots.count < 3 {
            return PerformanceRangeResult(
                ermMin: currentErm * 0.9,
                ermMax: currentErm * 1.1,
                ermRms: currentErm,
                ewmaErm: newEwma,
                isCurrentInRange: true
            )
        }

        let mean = snapshots.reduce(0, +) / Double(snapshots.count)
        let variance = snapshots.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Double(snapshots.count)
        let stddev = sqrt(variance)

        let filtered: [Double]
        if stddev > 0.0 {
            filtered = snapshots.filter { abs($0 - mean) <= outlierStddevThreshold * stddev }
        } else {
            filtered = snapshots
        }
        let safeFiltered = filtered.isEmpty ? snapshots : filtered

        let rms = sqrt(safeFiltered.map { $0 * $0 }.reduce(0, +) / Double(safeFiltered.count))

        let size = safeFiltered.count
        let weights = (0..<size).map { i in exp(-0.1 * Double(size - 1 - i)) }
        let weightSum = weights.reduce(0, +)
        let weightedRms: Double
        if weightSum > 0.0 {
            weightedRms = (0..<size).reduce(0.0) { $0 + safeFiltered[$1] * weights[$1] } / weightSum
        } else {
            weightedRms = rms
        }

        let ermMin = safeFiltered.min() ?? (currentErm * 0.9)
        let ermMax = safeFiltered.max() ?? (currentErm * 1.1)

        return PerformanceRangeResult(
            ermMin: ermMin,
            ermMax: ermMax,
            ermRms: weightedRms,
            ewmaErm: newEwma,
            isCurrentInRange: currentErm >= ermMin && currentErm <= ermMax
        )
    }
}
