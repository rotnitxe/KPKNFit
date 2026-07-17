import SwiftUI

// ─── PostExerciseSheetView ────────────────────────────────────────────────────

struct PostExerciseSheetView: View {
    let exerciseName: String
    let onDismiss: () -> Void
    let onSave: (PostExerciseResult) -> Void

    @State private var technicalQuality: Double = 8
    @State private var mood: Double = 3
    @State private var selectedTags: Set<String> = []
    @State private var showAdvanced = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Feedback del ejercicio")
                            .font(.title2)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                        Text(exerciseName)
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                    }
                    Spacer()
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }

                Divider().background(Color.white.opacity(0.15))

                // Technical quality
                LabeledSlider(
                    label: "Calidad técnica",
                    value: $technicalQuality,
                    range: 6...10,
                    lowLabel: "Deficiente",
                    highLabel: "Perfecta"
                )

                // Advanced toggle
                Button(action: { withAnimation { showAdvanced.toggle() } }) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Opciones avanzadas")
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            Text("Estado anímico y molestias")
                                .font(.caption2)
                                .foregroundColor(AppColors.textSecondary)
                        }
                        Spacer()
                        Text(showAdvanced ? "Ocultar" : "Mostrar")
                            .font(.footnote)
                            .foregroundColor(Color(hex: 0x3B82F6))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                if showAdvanced {
                    LabeledSlider(
                        label: "Estado anímico",
                        value: $mood,
                        range: 1...5,
                        lowLabel: "Bajo",
                        highLabel: "Excelente"
                    )

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Molestias (opcional)")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.white)
                        FlowLayout(spacing: 8) {
                            ForEach(DISCOMFORT_TAGS, id: \.self) { tag in
                                let isSelected = selectedTags.contains(tag)
                                Button(action: {
                                    if isSelected { selectedTags.remove(tag) }
                                    else { selectedTags.insert(tag) }
                                }) {
                                    Text(tag)
                                        .font(.caption)
                                        .foregroundColor(isSelected ? .white : AppColors.textSecondary)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(isSelected ? Color.white.opacity(0.2) : Color.white.opacity(0.05))
                                        .clipShape(Capsule())
                                }
                            }
                        }
                    }
                }

                Spacer(minLength: 4)

                // Buttons
                HStack(spacing: 12) {
                    Button(action: onDismiss) {
                        Text("Saltar")
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.white.opacity(0.08))
                            .clipShape(Capsule())
                    }
                    Button(action: {
                        onSave(PostExerciseResult(
                            technicalQuality: Int(technicalQuality),
                            mood: Int(mood),
                            discomforts: Array(selectedTags)
                        ))
                        onDismiss()
                    }) {
                        Text("Guardar")
                            .font(.subheadline)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color(hex: 0x3B82F6))
                            .clipShape(Capsule())
                    }
                }

                Spacer(minLength: 8)
            }
            .padding(.horizontal, 24)
            .padding(.top, 16)
        }
        .background(AppColors.bgDeepBlack)
    }
}

// ─── LabeledSlider ────────────────────────────────────────────────────────────

private struct LabeledSlider: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let lowLabel: String
    let highLabel: String

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(label)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                Spacer()
                Text("\(Int(value))")
                    .font(.footnote)
                    .foregroundColor(Color(hex: 0x3B82F6))
            }
            Slider(value: $value, in: range, step: 1)
                .tint(Color(hex: 0x3B82F6))
            HStack {
                Text(lowLabel)
                    .font(.caption)
                    .foregroundColor(AppColors.textSecondary)
                Spacer()
                Text(highLabel)
                    .font(.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }
}

// ─── FlowLayout ───────────────────────────────────────────────────────────────

private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (positions: [CGPoint], size: CGSize) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
            totalHeight = y + rowHeight
        }

        return (positions, CGSize(width: maxWidth, height: totalHeight))
    }
}

// ─── DISCOMFORT_TAGS ──────────────────────────────────────────────────────────

private let DISCOMFORT_TAGS: [String] = [
    "Dolor lumbar", "Dolor cervical", "Dolor de rodilla",
    "Dolor de hombro", "Dolor de codo", "Dolor de muñeca",
    "Fatiga muscular", "Mareo", "Sin molestias",
]
