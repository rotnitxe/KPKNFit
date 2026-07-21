import SwiftUI

struct SetAdjustmentOverlay: View {
    let exercise: Exercise
    let currentSet: ExerciseSet
    let setIndex: Int
    let exerciseReadiness: ExerciseReadiness
    let weightSuggestion: WeightSuggestion?
    let averageErm: Double?
    let bodyWeight: Double?
    let loadMode: LoadModeV2
    let onDismiss: () -> Void
    let onApply: (SetAdjustmentSuggestion) -> Void

    @State private var severitySlider: Float = 0.5

    var body: some View {
        let plannedWeight: Double = {
            if loadMode == .bodyweight { return 0 }
            if let w = currentSet.weight, w > 0 { return w }
            if let sug = weightSuggestion?.suggestedWeight { return sug }
            if let erm = averageErm, let reps = currentSet.targetReps, reps > 0 {
                return erm * (1.0278 - 0.0278 * Double(reps))
            }
            return 0
        }()

        let adjustment = calculateSetAdjustment(plannedWeight: plannedWeight, readiness: exerciseReadiness, severitySlider: Double(severitySlider), averageErm: averageErm, loadMode: loadMode, bodyWeight: bodyWeight)

        let readinessColor: Color = {
            if exerciseReadiness.overallScore >= 75 { return Color(red: 0.30, green: 0.78, blue: 0.31) }
            if exerciseReadiness.overallScore >= 50 { return Color(red: 1.0, green: 0.76, blue: 0.03) }
            return Color(red: 1.0, green: 0.32, blue: 0.32)
        }()
        let readinessLabel = readinessLabel(for: exerciseReadiness.overallScore)

        let plannedModeText: String = {
            switch loadMode {
            case .lastre: return "Con Lastre"
            case .bodyweight: return "Peso Corporal"
            case .assisted: return "Asistido"
            default: return "Carga Normal"
            }
        }()
        let plannedWeightText: String = loadMode == .bodyweight ? "BW" : "\(plannedWeight.toTrimmedString())kg"

        let suggestedModeText: String = {
            switch adjustment.suggestedLoadMode {
            case .lastre: return "Con Lastre"
            case .bodyweight: return "Peso Corporal"
            case .assisted: return "Asistido"
            default: return "Carga Normal"
            }
        }()
        let suggestedWeightText: String = adjustment.suggestedLoadMode == .bodyweight ? "BW" : "\(adjustment.suggestedWeight.toTrimmedString())kg"

        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Ajuste por estado actual")
                        .font(.system(size: 17, weight: .bold))
                    Text("\(exercise.name) · Serie \(setIndex + 1)/\(exercise.sets.count)")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                }
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .foregroundColor(.white.opacity(0.6))
                }
            }

            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(readinessColor.opacity(0.25))
                        .frame(width: 40, height: 40)
                    Text("\(exerciseReadiness.overallScore)%")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(readinessColor)
                }
                VStack(alignment: .leading) {
                    Text("Preparación: \(readinessLabel)")
                        .font(.system(size: 14, weight: .bold))
                    Text("Músc. \(exerciseReadiness.muscularComponent)% · SNC \(exerciseReadiness.cnsComponent)%" +
                         (exerciseReadiness.spinalWeight > 0.01 ? " · Col. \(exerciseReadiness.spinalComponent)%" : ""))
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.6))
                }
                Spacer()
            }
            .padding(16)
            .background(Color(.systemGray5).opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 16))

            HStack(spacing: 12) {
                VStack(spacing: 4) {
                    Text(plannedModeText.uppercased())
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white.opacity(0.5))
                    Text(plannedWeightText)
                        .font(.system(size: 22, weight: .black))
                }
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(Color(.systemGray5))
                .clipShape(RoundedRectangle(cornerRadius: 14))

                Text("→")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(readinessColor)

                VStack(spacing: 4) {
                    Text(suggestedModeText.uppercased())
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(readinessColor)
                    Text(suggestedWeightText)
                        .font(.system(size: 22, weight: .black))
                        .foregroundColor(readinessColor)
                    Text(loadMode == .assisted
                         ? "+\(Int(adjustment.suggestedWeight - plannedWeight))kg asist."
                         : "−\(Int(adjustment.reductionPercent * 100))%")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundColor(readinessColor.opacity(0.8))
                }
                .frame(maxWidth: .infinity)
                .padding(14)
                .background(readinessColor.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }

            if let erm = averageErm, erm > 0 {
                HStack(spacing: 6) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.4))
                    Text("eRM promedio: ~\(erm.toTrimmedString())kg")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.5))
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Intensidad del ajuste")
                    .font(.system(size: 14, weight: .semibold))
                Slider(value: $severitySlider, in: 0...1)
                    .tint(readinessColor)
                HStack {
                    Text("Conservador")
                        .font(.system(size: 11))
                        .foregroundColor(severitySlider <= 0.33 ? readinessColor : .white.opacity(0.4))
                    Spacer()
                    Text("Equilibrado")
                        .font(.system(size: 11))
                        .foregroundColor(severitySlider > 0.33 && severitySlider <= 0.66 ? readinessColor : .white.opacity(0.4))
                    Spacer()
                    Text("Agresivo")
                        .font(.system(size: 11))
                        .foregroundColor(severitySlider > 0.66 ? readinessColor : .white.opacity(0.4))
                }
            }

            HStack(spacing: 8) {
                Image(systemName: "info.circle")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.39, green: 0.71, blue: 0.96))
                Text("El ajuste aplica solo para esta serie y sesión. Tu plan no se modifica permanentemente.")
                    .font(.system(size: 11))
                    .foregroundColor(Color(red: 0.56, green: 0.79, blue: 0.98))
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(red: 0.106, green: 0.165, blue: 0.227))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            HStack(spacing: 12) {
                Button(action: onDismiss) {
                    Text("Cancelar")
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                Button(action: { onApply(adjustment) }) {
                    let text: String = {
                        switch adjustment.suggestedLoadMode {
                        case .bodyweight: return "Aplicar Peso Corporal"
                        case .assisted: return "Aplicar +\(adjustment.suggestedWeight.toTrimmedString())kg Asist."
                        default: return "Aplicar \(adjustment.suggestedWeight.toTrimmedString())kg"
                        }
                    }()
                    Text(text)
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(readinessColor)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 32)
    }
}

private func readinessLabel(for score: Int) -> String {
    if score >= 75 { return "Alta" }
    if score >= 50 { return "Media" }
    return "Baja"
}
