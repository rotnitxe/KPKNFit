import SwiftUI

// ─── PostSessionSheetView ─────────────────────────────────────────────────────

struct PostSessionSheetView: View {
    let questionnaire: PendingQuestionnaire
    let onDismiss: () -> Void
    let onSave: (PostSessionFeedback) -> Void

    @State private var cnsRecovery: Double = 7
    @State private var muscleFeedback: [String: MuscleFeedbackState] = [:]
    @State private var unresolvedDiscomfortIds: Set<String> = []

    init(
        questionnaire: PendingQuestionnaire,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (PostSessionFeedback) -> Void
    ) {
        self.questionnaire = questionnaire
        self.onDismiss = onDismiss
        self.onSave = onSave

        var initialFeedback: [String: MuscleFeedbackState] = [:]
        for muscle in questionnaire.muscleGroups {
            initialFeedback[muscle] = MuscleFeedbackState()
        }
        _muscleFeedback = State(initialValue: initialFeedback)
        _unresolvedDiscomfortIds = State(initialValue: Set(questionnaire.stillPresentDiscomfortIds))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("¿Cómo te recuperaste?")
                            .font(.title2)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                        Text(questionnaire.sessionName)
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

                // System recovery
                VStack(spacing: 4) {
                    HStack {
                        Text("Sistema / frescura general")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.white)
                        Spacer()
                        Text("\(Int(cnsRecovery)) / 10")
                            .font(.footnote)
                            .foregroundColor(Color(hex: 0x3B82F6))
                    }
                    Slider(value: $cnsRecovery, in: 1...10, step: 1)
                        .tint(Color(hex: 0x3B82F6))
                    HStack {
                        Text("Sin energía")
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                        Spacer()
                        Text("Totalmente recuperado")
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }

                // Per-muscle feedback
                if !questionnaire.muscleGroups.isEmpty {
                    Text("Retroalimentación muscular")
                        .font(.subheadline)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    ForEach(questionnaire.muscleGroups, id: \.self) { muscle in
                        MuscleFeedbackCard(
                            muscleName: muscle,
                            state: muscleFeedback[muscle] ?? MuscleFeedbackState(),
                            onChange: { muscleFeedback[muscle] = $0 }
                        )
                    }
                }

                // Discomfort follow-up
                if !questionnaire.stillPresentDiscomfortIds.isEmpty {
                    Text("¿Sigues sintiendo estas molestias?")
                        .font(.subheadline)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    VStack(spacing: 8) {
                        ForEach(questionnaire.stillPresentDiscomfortIds, id: \.self) { discomfortId in
                            let label = discomfortLabel(id: discomfortId)
                            let isSelected = unresolvedDiscomfortIds.contains(discomfortId)
                            Button(action: {
                                if isSelected {
                                    unresolvedDiscomfortIds.remove(discomfortId)
                                } else {
                                    unresolvedDiscomfortIds.insert(discomfortId)
                                }
                            }) {
                                HStack {
                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.caption)
                                    }
                                    Text(label)
                                        .font(.body)
                                        .fontWeight(.semibold)
                                    Spacer()
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity)
                                .background(
                                    isSelected
                                        ? Color(hex: 0xEF4444).opacity(0.25)
                                        : Color.white.opacity(0.05)
                                )
                                .foregroundColor(isSelected ? Color(hex: 0xEF4444) : .white)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }
                    }
                }

                Spacer(minLength: 4)

                // Save button
                Button(action: {
                    let fb = PostSessionFeedback(
                        logId: questionnaire.logId,
                        date: Self.todayString(),
                        cnsRecovery: Int(cnsRecovery),
                        muscleFeedback: muscleFeedback.mapValues { state in
                            MuscleFeedbackEntry(
                                doms: state.doms,
                                jointPain: state.jointPain,
                                strengthCapacity: state.strengthCapacity
                            )
                        },
                        unresolvedDiscomfortIds: Array(unresolvedDiscomfortIds)
                    )
                    onSave(fb)
                    onDismiss()
                }) {
                    Text("Guardar feedback")
                        .font(.subheadline)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                        .padding(.vertical, 4)
                        .frame(maxWidth: .infinity)
                }

                Spacer(minLength: 8)
            }
            .padding(.horizontal, 24)
            .padding(.top, 16)
        }
        .background(AppColors.bgDeepBlack)
    }

    static func todayString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }
}

// ─── MuscleFeedbackState ──────────────────────────────────────────────────────

struct MuscleFeedbackState {
    var doms: Int = 1
    var jointPain: Bool = false
    var strengthCapacity: Int = 7
}

// ─── MuscleFeedbackCard ───────────────────────────────────────────────────────

private struct MuscleFeedbackCard: View {
    let muscleName: String
    let state: MuscleFeedbackState
    let onChange: (MuscleFeedbackState) -> Void

    var body: some View {
        VStack(spacing: 10) {
            Text(muscleName)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)

            // DOMS selector (1-5)
            HStack {
                Text("Agujetas")
                    .font(.caption)
                    .foregroundColor(.white)
                Spacer()
                HStack(spacing: 4) {
                    ForEach(1...5, id: \.self) { level in
                        Button(action: {
                            var s = state; s.doms = level; onChange(s)
                        }) {
                            Text("\(level)")
                                .font(.caption2)
                                .fontWeight(.medium)
                                .foregroundColor(state.doms == level ? .white : AppColors.textSecondary)
                                .frame(width: 28, height: 28)
                                .background(state.doms == level ? Color.white.opacity(0.2) : Color.white.opacity(0.05))
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                    }
                }
            }

            // Strength capacity slider
            HStack {
                Text("Fuerza percibida")
                    .font(.caption)
                    .foregroundColor(.white)
                Spacer()
                Text("\(state.strengthCapacity)/10")
                    .font(.caption)
                    .foregroundColor(Color(hex: 0x3B82F6))
            }
            Slider(
                value: Binding(
                    get: { Double(state.strengthCapacity) },
                    set: { var s = state; s.strengthCapacity = Int($0); onChange(s) }
                ),
                in: 1...10,
                step: 1
            )
            .tint(Color(hex: 0x3B82F6))

            // Joint pain toggle
            HStack {
                Text("Dolor articular")
                    .font(.caption)
                    .foregroundColor(.white)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { state.jointPain },
                    set: { var s = state; s.jointPain = $0; onChange(s) }
                ))
                .tint(Color(hex: 0x3B82F6))
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
