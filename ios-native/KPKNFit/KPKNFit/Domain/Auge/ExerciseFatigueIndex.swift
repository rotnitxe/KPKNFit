import Foundation

struct ExerciseFatigueUserIndex {
    let muscle: Int
    let snc: Int
    let spinal: Int
    let overall: Int
}

enum ExerciseFatigueIndex {
    private static let maxEfc = 5.0
    private static let maxCnc = 5.0
    private static let maxSsc = 2.0

    private static let defaultEfc = 2.5
    private static let defaultCnc = 2.5
    private static let defaultSsc = 0.5

    private static let wMuscle = 3.5
    private static let wSnc = 3.0
    private static let wSpinal = 4.0

    static func fromIntrinsic(
        efc: Double?,
        cnc: Double?,
        ssc: Double?
    ) -> ExerciseFatigueUserIndex {
        let muscle = scaleToTen(Swift.max(1, Swift.min(efc ?? defaultEfc, maxEfc)), maxValue: maxEfc)
        let snc = scaleToTen(Swift.max(1, Swift.min(cnc ?? defaultCnc, maxCnc)), maxValue: maxCnc)
        let spinal = scaleToTen(Swift.max(0, Swift.min(ssc ?? defaultSsc, maxSsc)), maxValue: maxSsc)

        let weightedOverall = (
            (Double(muscle) * wMuscle) +
            (Double(snc) * wSnc) +
            (Double(spinal) * wSpinal)
        ) / (wMuscle + wSnc + wSpinal)

        return ExerciseFatigueUserIndex(
            muscle: muscle,
            snc: snc,
            spinal: spinal,
            overall: Swift.max(1, Swift.min(Int(weightedOverall.rounded()), 10))
        )
    }

    private static func scaleToTen(_ value: Double, maxValue: Double) -> Int {
        if maxValue <= 0.0 { return 1 }
        return Swift.max(1, Swift.min(Int(((value / maxValue) * 10.0).rounded()), 10))
    }
}
