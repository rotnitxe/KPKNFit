import SwiftUI

struct MinimalMuscleSlider: View {
    let muscleLabel: String
    let value: Int
    let onValueChange: (Int) -> Void

    @State private var barWidth: CGFloat = 0

    var body: some View {
        let clamped = min(max(value, 0), 100)
        let accent: Color = {
            if clamped >= 80 { return Color(red: 0.29, green: 0.87, blue: 0.50) }
            if clamped >= 55 { return Color(red: 0.64, green: 0.64, blue: 0.64) }
            return Color(red: 0.32, green: 0.32, blue: 0.32)
        }()

        VStack(spacing: 2) {
            HStack {
                Text(muscleLabel)
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.65))
                Spacer()
                Text("\(clamped)%")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.55))
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color(.systemGray4).opacity(0.25))
                        .frame(height: 4)
                    RoundedRectangle(cornerRadius: 2)
                        .fill(accent)
                        .frame(width: geo.size.width * CGFloat(clamped) / 100.0, height: 4)
                }
                .frame(height: 32)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { gesture in
                            let newValue = Int((gesture.location.x / max(geo.size.width, 1)) * 100)
                            onValueChange(min(max(newValue, 0), 100))
                        }
                )
                .onAppear { barWidth = geo.size.width }
                .onChange(of: geo.size.width) { barWidth = $0 }
            }
            .frame(height: 32)
        }
    }
}
