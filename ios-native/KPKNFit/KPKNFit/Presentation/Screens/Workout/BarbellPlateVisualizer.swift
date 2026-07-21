import SwiftUI

private let plateColors: [Double: Color] = [
    25.0: Color(red: 0.827, green: 0.184, blue: 0.184),
    20.0: Color(red: 0.098, green: 0.463, blue: 0.824),
    15.0: Color(red: 1.0, green: 0.757, blue: 0.027),
    10.0: Color(red: 0.220, green: 0.557, blue: 0.235),
    5.0: .white,
    2.5: Color(red: 0.620, green: 0.620, blue: 0.620),
    1.25: Color(red: 0.459, green: 0.459, blue: 0.459),
    0.5: Color(red: 0.741, green: 0.741, blue: 0.741),
    0.25: Color(red: 0.620, green: 0.620, blue: 0.620),
]

private func plateColor(_ weight: Double) -> Color {
    plateColors.first(where: { $0.key == weight })?.value ?? Color(red: 0.376, green: 0.490, blue: 0.545)
}

private func plateHeight(_ weight: Double, maxHeight: CGFloat) -> CGFloat {
    switch weight {
    case 25.0...: return maxHeight
    case 20.0..<25.0: return maxHeight * 0.92
    case 15.0..<20.0: return maxHeight * 0.82
    case 10.0..<15.0: return maxHeight * 0.72
    case 5.0..<10.0: return maxHeight * 0.58
    case 2.5..<5.0: return maxHeight * 0.44
    case 1.25..<2.5: return maxHeight * 0.34
    default: return maxHeight * 0.28
    }
}

struct BarbellPlateVisualizer: View {
    let targetWeight: Double
    let barbellWeight: Double
    let availablePlates: [Double]

    var body: some View {
        let result = calculatePlates(targetWeight, barbellWeight: barbellWeight, availablePlates: availablePlates)

        VStack(spacing: 8) {
            HStack {
                Text("Barra + platos")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.secondary)
                Spacer()
                Text(result.isExact
                     ? "\(result.achievedWeight.toTrimmedString()) kg"
                     : "\(result.achievedWeight.toTrimmedString()) kg (objetivo \(result.targetWeight.toTrimmedString()) kg)")
                    .font(.system(size: 11))
                    .foregroundColor(result.isExact ? .blue : .red)
            }

            BarbellPlateCanvas(result: result)
                .frame(height: 72)

            if !result.platesPerSide.isEmpty {
                Text("Por lado: \(formatPlatesList(result.platesPerSide))")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct BarbellPlateCanvas: View {
    let result: PlateResult

    var body: some View {
        Canvas { context, size in
            let centerX = size.width / 2
            let centerY = size.height / 2
            let barHeight: CGFloat = 14
            let sleeveWidth = size.width * 0.20
            let barWidth = size.width * 0.60
            let plateStartX = centerX - barWidth / 2 + 8
            let plateEndX = centerX + barWidth / 2 - 8
            let collarWidth: CGFloat = 10

            // Bar
            context.fill(Path(roundedRect: CGRect(x: centerX - barWidth / 2, y: centerY - barHeight / 2, width: barWidth, height: barHeight), cornerRadius: barHeight / 2), with: .color(Color(red: 0.471, green: 0.569, blue: 0.604)))

            // Collars
            context.fill(Path(roundedRect: CGRect(x: centerX - barWidth / 2, y: centerY - barHeight / 2 - 2, width: collarWidth, height: barHeight + 4), cornerRadius: 3), with: .color(Color(red: 0.329, green: 0.431, blue: 0.478)))

            context.fill(Path(roundedRect: CGRect(x: centerX + barWidth / 2 - collarWidth, y: centerY - barHeight / 2 - 2, width: collarWidth, height: barHeight + 4), cornerRadius: 3), with: .color(Color(red: 0.329, green: 0.431, blue: 0.478)))

            // Sleeves
            context.fill(Path(roundedRect: CGRect(x: centerX - barWidth / 2 - sleeveWidth + 4, y: centerY - 6, width: sleeveWidth, height: 12), cornerRadius: 4), with: .color(Color(red: 0.565, green: 0.643, blue: 0.682)))
            context.fill(Path(roundedRect: CGRect(x: centerX + barWidth / 2 - 4, y: centerY - 6, width: sleeveWidth, height: 12), cornerRadius: 4), with: .color(Color(red: 0.565, green: 0.643, blue: 0.682)))

            // Plates left
            drawPlates(ctx: &context, plates: result.platesPerSide, startX: plateStartX, centerY: centerY, maxPlateHeight: size.height * 0.90, direction: -1)
            // Plates right
            drawPlates(ctx: &context, plates: result.platesPerSide, startX: plateEndX, centerY: centerY, maxPlateHeight: size.height * 0.90, direction: 1)
        }
    }

    private func drawPlates(ctx: inout GraphicsContext, plates: [Double], startX: CGFloat, centerY: CGFloat, maxPlateHeight: CGFloat, direction: Int) {
        var currentX = startX
        let plateThickness: CGFloat = 10
        let gap: CGFloat = 1.5

        for plate in plates {
            let height = plateHeight(plate, maxHeight: maxPlateHeight)
            let color = plateColor(plate)
            let x = direction < 0 ? currentX - plateThickness : currentX
            let rect = CGRect(x: x, y: centerY - height / 2, width: plateThickness, height: height)
            ctx.fill(Path(roundedRect: rect, cornerRadius: 3), with: .color(color))
            if height > 20 {
                ctx.fill(Path(roundedRect: rect, cornerRadius: 3), with: .color(.black.opacity(0.15)))
            }
            currentX += (plateThickness + gap) * CGFloat(direction)
        }
    }
}

private func formatPlatesList(_ plates: [Double]) -> String {
    guard !plates.isEmpty else { return "ninguno" }
    let grouped = Dictionary(grouping: plates) { $0 }.mapValues { $0.count }
    return grouped.sorted { $0.key > $1.key }
        .map { weight, count in count > 1 ? "\(count)×\(weight.toTrimmedString())" : weight.toTrimmedString() }
        .joined(separator: ", ")
}

private func calculatePlates(_ targetWeight: Double, barbellWeight: Double, availablePlates: [Double]) -> PlateResult {
    var sortedPlates = availablePlates.sorted(by: >)
    let remainingPerSide = (targetWeight - barbellWeight) / 2
    guard remainingPerSide > 0 else {
        return PlateResult(achievedWeight: barbellWeight, targetWeight: targetWeight, platesPerSide: [], isExact: remainingPerSide == 0)
    }

    var plates: [Double] = []
    var remaining = remainingPerSide
    for plate in sortedPlates {
        while remaining >= plate {
            plates.append(plate)
            remaining -= plate
        }
    }
    let achieved = barbellWeight + plates.reduce(0, +) * 2
    return PlateResult(achievedWeight: achieved, targetWeight: targetWeight, platesPerSide: plates, isExact: abs(achieved - targetWeight) < 0.01)
}

private struct PlateResult {
    let achievedWeight: Double
    let targetWeight: Double
    let platesPerSide: [Double]
    let isExact: Bool
}

private extension Double {
    func toTrimmedString() -> String {
        self == floor(self) ? String(Int(self)) : String(format: "%.1f", self)
    }
}
