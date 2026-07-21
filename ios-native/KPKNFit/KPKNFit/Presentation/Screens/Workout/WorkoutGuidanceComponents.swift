import SwiftUI

func activeAutoRegulationForSet(
    autoRegulation: SetAutoRegulation?,
    exerciseId: String,
    setIdx: Int
) -> SetAutoRegulation? {
    guard let reg = autoRegulation, reg.exerciseId == exerciseId, reg.nextSetIdx == setIdx else { return nil }
    return reg
}

func shouldOfferSuggestedLoad(
    currentWeightText: String,
    suggestion: WeightSuggestion?
) -> Bool {
    guard let suggested = suggestion?.suggestedWeight, suggested > 0.0 else { return false }
    return currentWeightText != toTrimmedNumberString(suggested)
}

func buildAutoRegulationHeadline(regulation: SetAutoRegulation) -> String {
    let deltaPercent = Int(((regulation.adjustmentFactor - 1.0) * 100.0).rounded())
    if abs(deltaPercent) <= 1 { return "Ajuste fino para esta serie" }
    if deltaPercent > 0 { return "Sube +\(deltaPercent)% para esta serie" }
    return "Baja \(deltaPercent)% para esta serie"
}

func coachSeverityLabel(_ severity: CoachSeverity) -> String {
    switch severity {
    case .info: return "Info"
    case .warning: return "Atención"
    case .danger: return "Crítico"
    case .success: return "Óptimo"
    }
}

func coachActionLabel(_ action: CoachAction) -> String {
    switch action {
    case .reduceIntensity: return "Baja intensidad"
    case .skipExercise: return "Salta ejercicio"
    case .extendRest: return "Más descanso"
    case .stayTheCourse: return "Mantener rumbo"
    }
}

struct WorkoutLiveGuidanceCard: View {
    let weightSuggestion: WeightSuggestion?
    let autoRegulation: SetAutoRegulation?
    let coachMessage: CoachMessage?
    let currentWeightText: String
    let onApplySuggestedLoad: (Double) -> Void

    var body: some View {
        if weightSuggestion == nil && autoRegulation == nil && coachMessage == nil {
            EmptyView()
        } else {
            let accent = coachAccentColor(severity: coachMessage?.severity)
            VStack(spacing: 10) {
                HStack {
                    Image(systemName: "sparkles")
                        .foregroundColor(accent)
                    Text("Ajuste en vivo")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Spacer()
                    if let msg = coachMessage {
                        Text(coachSeverityLabel(msg.severity))
                            .font(.caption)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(accent.opacity(0.2))
                            .cornerRadius(12)
                    }
                }
                if let suggestion = weightSuggestion {
                    VStack(spacing: 8) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Carga sugerida")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Text("\(toTrimmedNumberString(suggestion.suggestedWeight)) kg")
                                    .font(.title2)
                                    .fontWeight(.black)
                                    .foregroundColor(.white)
                            }
                            Spacer()
                            if shouldOfferSuggestedLoad(currentWeightText: currentWeightText, suggestion: suggestion) {
                                Button("Usar") { onApplySuggestedLoad(suggestion.suggestedWeight) }
                                    .buttonStyle(.borderedProminent)
                                    .tint(accent)
                            }
                        }
                        Text(suggestion.reason)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(12)
                    .background(Color(.systemGray6).opacity(0.3))
                    .cornerRadius(16)
                }
                if let reg = autoRegulation {
                    HStack(spacing: 8) {
                        Image(systemName: "chart.line.uptrend.xyaw")
                            .foregroundColor(accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(buildAutoRegulationHeadline(regulation: reg))
                                .font(.subheadline)
                                .fontWeight(.semibold)
                                .foregroundColor(.white)
                            Text(reg.reason)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(12)
                    .background(accent.opacity(0.08))
                    .cornerRadius(16)
                }
                if let msg = coachMessage {
                    VStack(spacing: 8) {
                        HStack(spacing: 8) {
                            Text(coachSeverityLabel(msg.severity))
                                .font(.caption)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(accent.opacity(0.2))
                                .cornerRadius(12)
                            if let action = msg.action {
                                Text(coachActionLabel(action))
                                    .font(.caption)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 4)
                                    .background(Color(.systemGray6).opacity(0.3))
                                    .cornerRadius(12)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Text(msg.title)
                            .font(.subheadline)
                            .foregroundColor(accent)
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(msg.body)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(12)
                    .background(Color(.systemGray6).opacity(0.3))
                    .cornerRadius(16)
                }
            }
            .padding(14)
            .background(accent.opacity(0.12))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(accent.opacity(0.24), lineWidth: 1))
            .cornerRadius(20)
        }
    }

    private func coachAccentColor(severity: CoachSeverity?) -> Color {
        switch severity {
        case .success: return .green
        case .warning: return .orange
        case .danger: return .red
        case .info, nil: return .blue
        }
    }
}
