import Foundation

struct PlateResult {
    let platesPerSide: [Double]
    let achievedWeight: Double
    let targetWeight: Double
    let isExact: Bool
}

enum PlateCalculator {
    private static let minPlateWeight = 0.25

    static func calculatePlates(
        targetWeight: Double,
        barbellWeight: Double,
        availablePlates: [Double]
    ) -> PlateResult {
        if targetWeight <= barbellWeight {
            return PlateResult(
                platesPerSide: [],
                achievedWeight: barbellWeight,
                targetWeight: targetWeight,
                isExact: abs(targetWeight - barbellWeight) < 0.01
            )
        }

        let weightPerSide = (targetWeight - barbellWeight) / 2.0
        let sortedPlates = availablePlates.sorted(by: >)
        var platesPerSide: [Double] = []
        var remaining = weightPerSide

        for plate in sortedPlates {
            while remaining >= plate - 0.001 {
                platesPerSide.append(plate)
                remaining -= plate
            }
        }

        let achievedPerSide = platesPerSide.reduce(0, +)
        let achievedWeight = barbellWeight + (achievedPerSide * 2)
        let isExact = abs(achievedWeight - targetWeight) < 0.01

        return PlateResult(
            platesPerSide: platesPerSide,
            achievedWeight: achievedWeight,
            targetWeight: targetWeight,
            isExact: isExact
        )
    }
}
